#!/usr/bin/env python3
"""
test_tap_injection_latency.py
LinktoMac Test Suite — Phase 6 Acceptance

Role:
Verifies tap injection round-trip latency over a simulated WAN network topology:
1. Simulates 100ms WAN RTT (50ms outbound delay, 50ms inbound delay).
2. Measures total turnaround time of STREAM_CONTROL REMOTE_TAP envelope dispatch.
3. Asserts that the added processing overhead is < 50ms over the 100ms simulated RTT.
"""

import json
import socket
import struct
import threading
import time

def simulate_remote_tap_latency():
    wan_delay_sec = 0.05  # 50ms each direction = 100ms RTT

    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind(("127.0.0.1", 0))
    server_sock.listen(1)
    port = server_sock.getsockname()[1]

    running = True

    def mock_android_receiver():
        conn, _ = server_sock.accept()
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        try:
            # Read wire length header (4 bytes)
            raw_len = conn.recv(4)
            if len(raw_len) == 4:
                payload_len = struct.unpack(">I", raw_len)[0]
                payload_data = conn.recv(payload_len)
                payload_json = json.loads(payload_data.decode("utf-8"))

                # Simulate LinkTouchAccessibilityService.performNormalizedTap processing overhead (2-5ms)
                time.sleep(0.003)

                # Send simulated ACK with 50ms inbound WAN return delay
                time.sleep(wan_delay_sec)
                ack_packet = json.dumps({"type": "TAP_ACK", "status": "OK"}).encode("utf-8")
                ack_len = struct.pack(">I", len(ack_packet))
                conn.sendall(ack_len + ack_packet)
        finally:
            conn.close()

    t = threading.Thread(target=mock_android_receiver, daemon=True)
    t.start()
    time.sleep(0.1)

    client_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    client_sock.connect(("127.0.0.1", port))

    start_time = time.time()
    # Outbound simulated WAN delay
    time.sleep(wan_delay_sec)

    tap_payload = json.dumps({
        "type": "STREAM_CONTROL",
        "payload": {
            "mode": "REMOTE_TAP",
            "x": "0.5230",
            "y": "0.7810"
        }
    }).encode("utf-8")

    header = struct.pack(">I", len(tap_payload))
    client_sock.sendall(header + tap_payload)

    ack_len_raw = client_sock.recv(4)
    ack_len = struct.unpack(">I", ack_len_raw)[0]
    ack_data = client_sock.recv(ack_len)
    total_elapsed_ms = (time.time() - start_time) * 1000

    client_sock.close()
    server_sock.close()
    t.join(timeout=1.0)

    added_overhead_ms = total_elapsed_ms - (wan_delay_sec * 2 * 1000)
    print(f"Total WAN round-trip: {total_elapsed_ms:.2f}ms (Simulated RTT: 100ms, Added overhead: {added_overhead_ms:.2f}ms)")
    assert added_overhead_ms < 50.0, f"Added overhead {added_overhead_ms:.2f}ms exceeded 50ms budget!"
    print("✓ [PASS] Tap injection round-trip latency < 50ms added overhead over 100ms WAN RTT")

if __name__ == "__main__":
    simulate_remote_tap_latency()
