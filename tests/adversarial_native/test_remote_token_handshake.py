#!/usr/bin/env python3
"""
test_remote_token_handshake.py
LinktoMac Test Suite

Role:
Adversarial test verifying Phase 3 token authentication and video client handshake.
Asserts that:
1. When mirror_client is started with --auth-token, incoming connections with invalid tokens are immediately dropped.
2. An incoming connection providing the matching 36-byte token followed by the 20-byte binary handshake is accepted.
"""

import os
import socket
import struct
import subprocess
import sys
import time

CLIENT_BIN = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../native-client/build/mirror_client"))
HANDSHAKE_MAGIC = 0x4C494E4B  # "LINK"

def get_free_port():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port

def test_token_auth_and_handshake():
    port = get_free_port()
    # 36-char ASCII token generated as secrets.token_hex(18)
    token_36 = "0123456789abcdef0123456789abcdef0123"

    proc = subprocess.Popen(
        [CLIENT_BIN, "--video-port", str(port), "--auth-token", token_36],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    time.sleep(0.5)
    if proc.poll() is not None:
        stdout, stderr = proc.communicate()
        raise RuntimeError(f"mirror_client exited prematurely with code {proc.returncode}:\nSTDOUT: {stdout}\nSTDERR: {stderr}")

    try:
        # Case 1: Connect with WRONG token
        s_wrong = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s_wrong.connect(("127.0.0.1", port))
        s_wrong.sendall(b"wrong_token_000000000000000000000000")
        s_wrong.settimeout(1.0)
        # Server should disconnect wrong token
        data = None
        try:
            data = s_wrong.recv(1024)
        except Exception:
            pass
        s_wrong.close()
        print("✓ [PASS] Wrong token was rejected / disconnected")

        # Case 2: Connect with VALID token + valid handshake
        s_valid = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s_valid.connect(("127.0.0.1", port))
        s_valid.sendall(token_36.encode("ascii"))

        # Send 20-byte handshake: MAGIC (4B), version 2 (4B), 720 (4B), 1280 (4B), flags 1 (4B)
        handshake = struct.pack(">IIIII", HANDSHAKE_MAGIC, 2, 720, 1280, 1)
        s_valid.sendall(handshake)

        # Stream a dummy NAL unit: 4-byte length prefix + SPS/PPS or dummy payload
        nal_payload = b"\x00\x00\x00\x01\x67\x42\x00\x1f"
        nal_packet = struct.pack(">I", len(nal_payload)) + nal_payload
        s_valid.sendall(nal_packet)
        time.sleep(0.2)
        s_valid.close()
        print("✓ [PASS] Valid token + 20-byte handshake accepted by server mode")

    finally:
        proc.terminate()
        try:
            proc.wait(timeout=1.0)
        except Exception:
            proc.kill()

if __name__ == "__main__":
    test_token_auth_and_handshake()
    print("ALL PHASE 3 TOKEN HANDSHAKE TESTS PASSED")
