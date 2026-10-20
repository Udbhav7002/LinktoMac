#!/usr/bin/env python3
"""
Adversarial Stress Test: Handshake Integrity for LinktoMac Native Client
Tests that:
1. 16-byte corrupted / legacy packets are strictly rejected.
2. The client does NOT perform a 16-byte fallback read or desynchronize.
3. Invalid magic numbers (0x00000000, 0xDEADBEEF, 0xFFFFFFFF) are rejected.
4. Out-of-bounds dimensions (<=0 or >7680) are rejected.
5. Partial / truncated handshakes (1, 4, 15, 19 bytes) do not crash the client.
6. Server mode handles repeated corrupt handshakes without crashing or socket exhaustion.
7. Valid 20-byte handshake succeeds and client sends 0x05 sync request.
"""

import os
import socket
import struct
import subprocess
import sys
import threading
import time

CLIENT_BIN = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../native-client/build/mirror_client"))
HANDSHAKE_MAGIC = 0x4C494E4B  # "LINK"

def pack_handshake(magic, version, width, height, flags=0):
    return struct.pack(">IIIII", magic, version, width, height, flags)

class HandshakeTester:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.results = []

    def log(self, name, success, details=""):
        status = "PASS" if success else "FAIL"
        if success:
            self.passed += 1
        else:
            self.failed += 1
        msg = f"[{status}] {name}"
        if details:
            msg += f" - {details}"
        print(msg)
        self.results.append((name, success, details))

    def run_client_against_mock_server(self, payload, close_after_send=True, timeout=2.0):
        """Starts a mock TCP server, sends payload to connecting client, returns (returncode, stdout, stderr, client_sent_data)."""
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind(("127.0.0.1", 0))
        server_sock.listen(1)
        port = server_sock.getsockname()[1]

        received_from_client = bytearray()
        server_error = None

        def server_thread():
            nonlocal server_error
            try:
                server_sock.settimeout(timeout + 1.0)
                conn, _ = server_sock.accept()
                conn.settimeout(timeout)
                if payload:
                    conn.sendall(payload)
                if close_after_send:
                    conn.close()
                else:
                    # Keep connection open and read any client response
                    try:
                        data = conn.recv(1024)
                        if data:
                            received_from_client.extend(data)
                    except socket.timeout:
                        pass
                    conn.close()
            except Exception as e:
                server_error = e
            finally:
                server_sock.close()

        th = threading.Thread(target=server_thread)
        th.daemon = True
        th.start()

        cmd = [CLIENT_BIN, "--connect", "127.0.0.1", "--video-port", str(port)]
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        try:
            stdout, stderr = proc.communicate(timeout=timeout)
            ret = proc.returncode
        except subprocess.TimeoutExpired:
            proc.kill()
            stdout, stderr = proc.communicate()
            ret = -9  # timed out

        th.join(timeout=1.0)
        return ret, stdout, stderr, bytes(received_from_client)

    def test_16_byte_rejection(self):
        """Verify 16-byte packet is strictly rejected with no fallback or crash."""
        # Old 16-byte v1 handshake: magic, version, width, height (missing flags)
        payload = struct.pack(">IIII", HANDSHAKE_MAGIC, 1, 1080, 1920)
        assert len(payload) == 16

        ret, stdout, stderr, _ = self.run_client_against_mock_server(payload, close_after_send=True)
        # Client must fail cleanly with non-zero exit code
        rejected = (ret != 0)
        has_handshake_error = ("Failed to read 20-byte handshake" in stderr or "Failed to read" in stderr)
        no_segfault = (ret != -11 and ret != 139) # SIGSEGV
        success = rejected and has_handshake_error and no_segfault
        self.log("16-byte packet rejection (EOF after 16 bytes)", success,
                 f"ret={ret}, err={stderr.strip()}")

    def test_16_byte_with_delayed_garbage(self):
        """Verify client does NOT read an additional 16-byte fallback chunk if only 16 bytes sent initially."""
        payload = struct.pack(">IIII", HANDSHAKE_MAGIC, 1, 1080, 1920)

        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind(("127.0.0.1", 0))
        server_sock.listen(1)
        port = server_sock.getsockname()[1]

        def slow_server():
            try:
                server_sock.settimeout(2.0)
                conn, _ = server_sock.accept()
                conn.sendall(payload) # send only 16 bytes
                time.sleep(0.3)
                # Now close without sending the remaining 4 bytes
                conn.close()
            except:
                pass
            finally:
                server_sock.close()

        th = threading.Thread(target=slow_server)
        th.daemon = True
        th.start()

        proc = subprocess.Popen([CLIENT_BIN, "--connect", "127.0.0.1", "--video-port", str(port)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        stdout, stderr = proc.communicate(timeout=2.0)
        th.join()

        success = (proc.returncode != 0) and ("Failed to read 20-byte handshake" in stderr)
        self.log("16-byte incomplete stream rejected on EOF", success,
                 f"ret={proc.returncode}, err={stderr.strip()}")

    def test_invalid_magic_numbers(self):
        """Verify invalid magic numbers fail immediately."""
        bad_magics = [
            (0x00000000, "all zeros"),
            (0xDEADBEEF, "deadbeef"),
            (0x4C494E00, "truncated link"),
            (0xFFFFFFFF, "all ones"),
            (0x53435250, "scrcpy magic SCRP")
        ]
        for magic, desc in bad_magics:
            payload = pack_handshake(magic, 2, 1080, 1920, 0)
            ret, stdout, stderr, _ = self.run_client_against_mock_server(payload, close_after_send=True)
            success = (ret != 0) and ("Invalid magic number" in stderr)
            self.log(f"Invalid magic {hex(magic)} ({desc})", success,
                     f"ret={ret}, err={stderr.strip()}")

    def test_invalid_dimensions(self):
        """Verify boundary and out-of-bounds dimensions are rejected."""
        test_cases = [
            (0, 1920, "zero width"),
            (1080, 0, "zero height"),
            (-1, 1920, "negative width (uint32 underflow)"),
            (1080, -5, "negative height"),
            (7681, 1080, "width > 7680"),
            (1080, 10000, "height > 7680"),
            (0x7FFFFFFF, 1920, "int32 max width")
        ]
        for w, h, desc in test_cases:
            # Struct pack as uint32
            w_u32 = w & 0xFFFFFFFF
            h_u32 = h & 0xFFFFFFFF
            payload = pack_handshake(HANDSHAKE_MAGIC, 2, w_u32, h_u32, 0)
            ret, stdout, stderr, _ = self.run_client_against_mock_server(payload, close_after_send=True)
            success = (ret != 0) and ("Invalid dimensions" in stderr)
            self.log(f"Dimension boundary: {desc}", success,
                     f"ret={ret}, err={stderr.strip()}")

    def test_truncated_packets(self):
        """Verify partial packets (1, 4, 15, 19 bytes) do not crash client."""
        for size in [1, 4, 15, 19]:
            payload = b"X" * size
            ret, stdout, stderr, _ = self.run_client_against_mock_server(payload, close_after_send=True)
            no_crash = (ret != -11 and ret != 139)
            success = (ret != 0) and no_crash
            self.log(f"Truncated packet ({size} bytes) clean error handling", success,
                     f"ret={ret}, err={stderr.strip()}")

    def test_server_mode_corrupt_handshake_resilience(self):
        """Verify server mode rejects corrupt handshakes and continues listening without crash."""
        server_port = 28190
        # Start client in server/listen mode
        cmd = [CLIENT_BIN, "--video-port", str(server_port)]
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        # Allow server to bind and listen
        time.sleep(0.5)

        corrupt_payloads = [
            b"X" * 16, # 16 bytes
            b"X" * 4,  # 4 bytes
            pack_handshake(0xDEADBEEF, 2, 1080, 1920, 0), # bad magic
            pack_handshake(HANDSHAKE_MAGIC, 2, 0, 0, 0), # zero dimensions
            pack_handshake(HANDSHAKE_MAGIC, 2, 9000, 9000, 0), # oversize
        ]

        try:
            for idx, p in enumerate(corrupt_payloads):
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.connect(("127.0.0.1", server_port))
                s.sendall(p)
                s.close()
                time.sleep(0.1)

            # Check if process is still alive (not crashed!)
            alive = (proc.poll() is None)
            self.log("Server mode resilience under 5 corrupt handshakes", alive,
                     f"Process still alive: {alive}")
        finally:
            proc.terminate()
            stdout, stderr = proc.communicate(timeout=2.0)

    def test_valid_handshake_and_keyframe_request(self):
        """Verify a valid 20-byte handshake initiates connection and client sends 0x05 sync request."""
        payload = pack_handshake(HANDSHAKE_MAGIC, 2, 1080, 1920, 0)
        ret, stdout, stderr, client_data = self.run_client_against_mock_server(
            payload, close_after_send=False, timeout=1.5
        )
        # Client should have sent at least 1 byte: EVENT_TYPE_SYNC_REQUEST (0x05)
        sent_sync = (len(client_data) > 0 and client_data[0] == 0x05)
        self.log("Valid 20-byte handshake emits 0x05 keyframe sync request", sent_sync,
                 f"client_data={client_data.hex() if client_data else 'none'}")

    def run_all(self):
        print("=== Running Handshake Integrity Empirical Stress Tests ===")
        self.test_16_byte_rejection()
        self.test_16_byte_with_delayed_garbage()
        self.test_invalid_magic_numbers()
        self.test_invalid_dimensions()
        self.test_truncated_packets()
        self.test_server_mode_corrupt_handshake_resilience()
        self.test_valid_handshake_and_keyframe_request()
        print(f"\nHandshake Tests: {self.passed} passed, {self.failed} failed.")
        return self.failed == 0

if __name__ == "__main__":
    tester = HandshakeTester()
    ok = tester.run_all()
    sys.exit(0 if ok else 1)
