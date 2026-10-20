#!/usr/bin/env python3
"""
Empirical Adversarial Stress Test: ABR Telemetry & Bitrate Adaptation (M4)
Tests wire protocol serialization, TCP transport under high throughput,
and mathematical stability under extreme RTT and loss inputs.
"""

import math
import socket
import struct
import sys
import threading
import time

OPCODE_NETWORK_FEEDBACK = 0x07
MIN_BITRATE = 2_000_000   # 2 Mbps
MAX_BITRATE = 24_000_000  # 24 Mbps


def pack_feedback(loss_rate: float, rtt_ms: float) -> bytes:
    """Wire format: 1 byte opcode (0x07), 4 bytes float loss, 4 bytes float rtt (Big-Endian)."""
    return struct.pack("!Bff", OPCODE_NETWORK_FEEDBACK, loss_rate, rtt_ms)


def unpack_feedback(data: bytes):
    assert len(data) == 9, f"Invalid packet length: {len(data)}"
    opcode, loss, rtt = struct.unpack("!Bff", data)
    assert opcode == OPCODE_NETWORK_FEEDBACK
    return loss, rtt


def calculate_adapted_bitrate(current_bitrate: int, loss_rate: float, rtt_ms: float) -> int:
    """Exact replica of Kotlin ScreenEncoder.calculateAdaptedBitrate."""
    if math.isnan(loss_rate) or math.isnan(rtt_ms):
        return current_bitrate

    if loss_rate > 0.10 or rtt_ms > 300.0:
        return max(MIN_BITRATE, int(current_bitrate * 0.70))
    elif loss_rate > 0.05 or rtt_ms > 150.0:
        return max(MIN_BITRATE, int(current_bitrate * 0.85))
    elif loss_rate < 0.01 and rtt_ms < 50.0:
        return min(MAX_BITRATE, int(current_bitrate * 1.10))
    else:
        return current_bitrate


def run_wire_serialization_tests():
    print("\n--- 1. Wire Format Serialization & Precision ---")
    test_cases = [
        (0.0, 15.5),
        (0.005, 45.2),
        (0.125, 320.0),
        (0.999, 1500.0),
        (1.0, 5000.0),
    ]
    for loss, rtt in test_cases:
        wire = pack_feedback(loss, rtt)
        assert len(wire) == 9
        unpacked_loss, unpacked_rtt = unpack_feedback(wire)
        assert abs(unpacked_loss - loss) < 1e-5, f"Loss mismatch: {unpacked_loss} vs {loss}"
        assert abs(unpacked_rtt - rtt) < 1e-4, f"RTT mismatch: {unpacked_rtt} vs {rtt}"
        print(f"  ✓ [PASS] Serialized loss={loss}, rtt={rtt}ms -> 9 bytes: {wire.hex()}")


def run_socket_stress():
    print("\n--- 2. High-Frequency TCP Socket Stress (5,000 packets) ---")
    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind(("127.0.0.1", 0))
    server_sock.listen(1)
    port = server_sock.getsockname()[1]

    received_count = 0
    server_done = threading.Event()

    def server_worker():
        nonlocal received_count
        conn, _ = server_sock.accept()
        buf = bytearray()
        try:
            while True:
                chunk = conn.recv(4096)
                if not chunk:
                    break
                buf.extend(chunk)
                while len(buf) >= 9:
                    opcode = buf[0]
                    if opcode == OPCODE_NETWORK_FEEDBACK:
                        loss, rtt = struct.unpack("!ff", buf[1:9])
                        received_count += 1
                        del buf[:9]
                    else:
                        raise ValueError(f"Desynced opcode: {opcode}")
        finally:
            conn.close()
            server_sock.close()
            server_done.set()

    th = threading.Thread(target=server_worker, daemon=True)
    th.start()

    client_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_sock.connect(("127.0.0.1", port))

    # Send 5,000 back-to-back feedback packets
    total_packets = 5000
    batch = b"".join(pack_feedback(i / 10000.0, 20.0 + (i % 300)) for i in range(total_packets))
    client_sock.sendall(batch)
    client_sock.close()

    server_done.wait(timeout=5.0)
    assert received_count == total_packets, f"Received {received_count}/{total_packets}"
    print(f"  ✓ [PASS] Streamed and parsed {received_count}/{total_packets} back-to-back packets with zero desync.")


def run_mathematical_stress():
    print("\n--- 3. Mathematical Adaptation & Boundary Stress ---")

    # NaN / Inf stability
    print("  Testing NaN / Infinity immunity...")
    initial_bitrate = 8_000_000
    assert calculate_adapted_bitrate(initial_bitrate, float("nan"), 20.0) == initial_bitrate
    assert calculate_adapted_bitrate(initial_bitrate, 0.05, float("nan")) == initial_bitrate
    assert calculate_adapted_bitrate(initial_bitrate, float("nan"), float("nan")) == initial_bitrate
    print("  ✓ [PASS] NaN inputs strictly preserve current bitrate without crashing.")

    # Positive Infinity
    inf_bitrate = calculate_adapted_bitrate(initial_bitrate, float("inf"), 20.0)
    assert inf_bitrate == int(initial_bitrate * 0.70)
    rtt_inf_bitrate = calculate_adapted_bitrate(initial_bitrate, 0.0, float("inf"))
    assert rtt_inf_bitrate == int(initial_bitrate * 0.70)
    print("  ✓ [PASS] +Infinity loss/RTT triggers aggressive 30% reduction safely.")

    # Negative values
    print("  Testing negative loss and negative RTT handling...")
    neg_loss_res = calculate_adapted_bitrate(initial_bitrate, -1.0, 20.0)
    # loss < 0.01 and rtt < 50 triggers probe upward
    assert neg_loss_res == int(initial_bitrate * 1.10)
    print("  ✓ [PASS] Negative inputs handled predictably without math exceptions.")

    # Extreme Clamping: Underflow
    print("  Testing lower bound clamp (MIN_BITRATE = 2 Mbps)...")
    bitrate = 2_100_000
    for _ in range(20):
        bitrate = calculate_adapted_bitrate(bitrate, 0.50, 500.0)
        assert bitrate >= MIN_BITRATE, f"Bitrate underflowed: {bitrate}"
    assert bitrate == MIN_BITRATE
    print(f"  ✓ [PASS] Bitrate strictly clamped at MIN_BITRATE ({MIN_BITRATE / 1e6:.1f} Mbps) under extreme loss.")

    # Extreme Clamping: Overflow
    print("  Testing upper bound clamp (MAX_BITRATE = 24 Mbps)...")
    bitrate = 23_000_000
    for _ in range(20):
        bitrate = calculate_adapted_bitrate(bitrate, 0.001, 10.0)
        assert bitrate <= MAX_BITRATE, f"Bitrate overflowed: {bitrate}"
    assert bitrate == MAX_BITRATE
    print(f"  ✓ [PASS] Bitrate strictly clamped at MAX_BITRATE ({MAX_BITRATE / 1e6:.1f} Mbps) under optimal conditions.")

    # Step-down trajectory verification
    print("  Verifying 8 Mbps -> 2 Mbps congestion convergence...")
    b = 8_000_000
    trajectory = [b]
    while b > MIN_BITRATE:
        b = calculate_adapted_bitrate(b, 0.15, 400.0)
        trajectory.append(b)
    assert len(trajectory) == 5, f"Unexpected trajectory length: {trajectory}"
    assert trajectory == [8000000, 5600000, 3919999, 2743999, 2000000], f"Trajectory mismatch: {trajectory}"
    print(f"  ✓ [PASS] Exact step-down convergence verified: {trajectory}")


def run_all():
    print("===================================================================")
    print("  M4 EMPIRICAL CHALLENGER: ABR TELEMETRY ADVERSARIAL STRESS TEST   ")
    print("===================================================================")
    run_wire_serialization_tests()
    run_socket_stress()
    run_mathematical_stress()
    print("\n===================================================================")
    print("  ALL ABR TELEMETRY ADVERSARIAL STRESS TESTS PASSED (100% PASS)    ")
    print("===================================================================")


if __name__ == "__main__":
    run_all()
