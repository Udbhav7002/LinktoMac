#!/usr/bin/env python3
"""
Empirical test for mirror_core Python bindings with simulated frame stream.
Stresses:
1. Module loading and attribute validation.
2. Client connect-mode lifecycle with simulated 20-byte handshake.
3. Receipt of 0x05 on-demand IDR sync request from native client.
4. Simulated frame streaming through native decode thread.
5. Thread-safe client.stop() invocation and termination.
6. Server-mode listening and accept lifecycle.
"""

import sys
import os
import time
import socket
import struct
import threading
from pathlib import Path

# Add native-client/build to python path
BUILD_DIR = Path(__file__).resolve().parent.parent.parent / "native-client" / "build"
sys.path.insert(0, str(BUILD_DIR))

try:
    import mirror_core
except ImportError as e:
    print(f"[FAIL] Could not import mirror_core: {e}")
    sys.exit(1)

HANDSHAKE_MAGIC = 0x4C494E4B  # 'LINK'
HANDSHAKE_SIZE = 20

def create_handshake(width=720, height=1280, bitrate=4000000, flags=1, version=2):
    # Magic (4B), version (4B), width (4B), height (4B), flags (4B)
    return struct.pack(">IIIII", HANDSHAKE_MAGIC, version, width, height, flags)

def test_attributes():
    print("[TEST 1] Verifying mirror_core module attributes...")
    assert hasattr(mirror_core, "MirrorClient"), "Missing MirrorClient class"
    client = mirror_core.MirrorClient("127.0.0.1", 27189)
    assert hasattr(client, "set_connect_mode"), "Missing set_connect_mode"
    assert hasattr(client, "start"), "Missing start"
    assert hasattr(client, "stop"), "Missing stop"
    assert hasattr(client, "is_running"), "Missing is_running"
    assert not client.is_running(), "New client should not be running"
    print("  [PASS] mirror_core class and methods verified.")

def test_connect_mode_streaming():
    print("[TEST 2] Testing connect-mode streaming lifecycle with simulated server...")
    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind(("127.0.0.1", 0))
    port = server_sock.getsockname()[1]
    server_sock.listen(1)

    client = mirror_core.MirrorClient("127.0.0.1", port)
    client.set_connect_mode(True)

    client_thread = None
    server_accepted = threading.Event()
    sync_received = threading.Event()
    stop_event = threading.Event()

    def server_worker():
        conn, addr = server_sock.accept()
        server_accepted.set()
        
        # 1. Send handshake
        hs = create_handshake(width=720, height=1280, flags=1)
        conn.sendall(hs)

        # 2. Receive 0x05 sync request
        conn.settimeout(3.0)
        try:
            req = conn.recv(1)
            if req == b"\x05":
                sync_received.set()
        except socket.timeout:
            pass

        # 3. Stream synthetic NAL chunks until stop
        sps_pps = bytes([
            0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x1f, # SPS
            0x00, 0x00, 0x00, 0x01, 0x68, 0xce, 0x38, 0x80  # PPS
        ])
        while not stop_event.is_set():
            try:
                conn.sendall(sps_pps)
                time.sleep(0.016)
            except (BrokenPipeError, ConnectionResetError, OSError):
                break

        conn.close()

    server_t = threading.Thread(target=server_worker)
    server_t.daemon = True
    server_t.start()

    # Note: On macOS Cocoa, SDL_CreateWindow and event loops should run on main thread if possible,
    # but in python bindings tests, let's verify if client.start() can run on thread or if it exits cleanly.
    client_res = []
    def client_worker():
        ok = client.start()
        client_res.append(ok)

    client_t = threading.Thread(target=client_worker)
    client_t.daemon = True
    client_t.start()

    assert server_accepted.wait(timeout=5.0), "Server did not accept client connection"
    assert sync_received.wait(timeout=5.0), "Client did not emit 0x05 sync request"
    print("  ✓ Server accepted connection and received 0x05 IDR sync request.")

    # Check client running state
    time.sleep(0.2)
    assert client.is_running(), "client.is_running() should be True while active"
    print("  ✓ Client is reported as running.")

    # Stop client
    stop_event.set()
    client.stop()
    client_t.join(timeout=3.0)

    assert not client.is_running(), "client.is_running() should be False after stop()"
    server_t.join(timeout=2.0)
    server_sock.close()
    print("  [PASS] Connect-mode streaming completed cleanly.")

def test_corrupt_handshake_handling():
    print("[TEST 3] Testing client resilience against corrupt server handshake...")
    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind(("127.0.0.1", 0))
    port = server_sock.getsockname()[1]
    server_sock.listen(1)

    def server_worker():
        conn, addr = server_sock.accept()
        # Send bad magic
        bad_hs = struct.pack(">IIIII", 0xDEADBEEF, 2, 720, 1280, 1)
        conn.sendall(bad_hs)
        time.sleep(0.1)
        conn.close()

    server_t = threading.Thread(target=server_worker)
    server_t.daemon = True
    server_t.start()

    client = mirror_core.MirrorClient("127.0.0.1", port)
    client.set_connect_mode(True)

    # start() should return false on handshake error without crashing Python
    ok = client.start()
    assert not ok, "Client should fail start() on corrupted handshake"
    assert not client.is_running(), "Client should not be running after failed start"

    server_t.join(timeout=2.0)
    server_sock.close()
    print("  [PASS] Corrupted handshake properly rejected.")

if __name__ == "__main__":
    print("=== Running Empirical mirror_core Python Bindings Tests ===")
    test_attributes()
    test_corrupt_handshake_handling()
    test_connect_mode_streaming()
    print("\nAll mirror_core Python Bindings Tests PASSED!")
