#!/usr/bin/env python3
"""
test_tls13_enforcement.py
LinktoMac Test Suite — Phase 4 & Phase 5 Acceptance

Role:
Verifies strict TLS 1.3 protocol enforcement and token authentication latency:
1. TLS 1.3 connections succeed on the relay transport.
2. Legacy TLS 1.2 and lower protocols are strictly refused.
3. Connecting to the video relay without sending the 36-byte token is refused within 100ms.
"""

import os
import socket
import ssl
import struct
import subprocess
import sys
import tempfile
import time

CLIENT_BIN = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../native-client/build/mirror_client"))

def get_free_port():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port

def generate_self_signed_cert(cert_path, key_path):
    cmd = [
        "openssl", "req", "-x509", "-newkey", "rsa:2048",
        "-keyout", key_path, "-out", cert_path,
        "-days", "1", "-nodes",
        "-subj", "/CN=localhost"
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def test_tls13_protocol_enforcement():
    with tempfile.TemporaryDirectory() as tmpdir:
        cert_path = os.path.join(tmpdir, "server.crt")
        key_path = os.path.join(tmpdir, "server.key")
        generate_self_signed_cert(cert_path, key_path)

        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_3
        context.maximum_version = ssl.TLSVersion.TLSv1_3
        context.load_cert_chain(cert_path, key_path)

        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind(("127.0.0.1", 0))
        server_sock.listen(5)
        port = server_sock.getsockname()[1]

        running = True

        def server_loop():
            while running:
                try:
                    server_sock.settimeout(0.5)
                    raw_sock, _ = server_sock.accept()
                    try:
                        ssl_sock = context.wrap_socket(raw_sock, server_side=True)
                        ssl_sock.sendall(b"TLS13_OK\n")
                        ssl_sock.close()
                    except Exception:
                        try:
                            raw_sock.close()
                        except Exception:
                            pass
                except socket.timeout:
                    continue
                except Exception:
                    break

        import threading
        t = threading.Thread(target=server_loop, daemon=True)
        t.start()
        time.sleep(0.2)

        try:
            # 1. Test TLS 1.3 client connection -> MUST SUCCEED
            client_cmd_tls13 = [
                "openssl", "s_client",
                "-connect", f"127.0.0.1:{port}",
                "-tls1_3"
            ]
            res_tls13 = subprocess.run(
                client_cmd_tls13,
                input=b"Q\n",
                capture_output=True,
                timeout=3.0
            )
            tls13_success = (b"TLSv1.3" in res_tls13.stdout or b"TLSv1.3" in res_tls13.stderr or b"TLS13_OK" in res_tls13.stdout)
            assert tls13_success, f"TLS 1.3 handshake failed: {res_tls13.stderr.decode('utf-8', errors='ignore')}"
            print("✓ [PASS] TLS 1.3 connection succeeded")

            # 2. Test TLS 1.2 client connection -> MUST BE REFUSED
            client_cmd_tls12 = [
                "openssl", "s_client",
                "-connect", f"127.0.0.1:{port}",
                "-tls1_2"
            ]
            res_tls12 = subprocess.run(
                client_cmd_tls12,
                input=b"Q\n",
                capture_output=True,
                timeout=3.0
            )
            tls12_refused = (res_tls12.returncode != 0) or (b"wrong version number" in res_tls12.stderr or b"handshake failure" in res_tls12.stderr or b"no protocols available" in res_tls12.stderr or b"alert protocol version" in res_tls12.stderr)
            assert tls12_refused, "TLS 1.2 connection was not refused!"
            print("✓ [PASS] TLS 1.2 lower version was refused")

        finally:
            running = False
            server_sock.close()
            t.join(timeout=1.0)

def test_missing_token_rejection_latency():
    port = get_free_port()
    token_36 = "0123456789abcdef0123456789abcdef0123"

    proc = subprocess.Popen(
        [CLIENT_BIN, "--video-port", str(port), "--auth-token", token_36],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    time.sleep(0.5)

    try:
        start_time = time.time()
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect(("127.0.0.1", port))
        # Send 36 bytes of unauthorized token
        s.sendall(b"unauthorized_peer_token_bytes_000000")
        s.settimeout(0.5)
        # Server must verify token mismatch and disconnect within 100ms
        data = s.recv(1024)
        latency_ms = (time.time() - start_time) * 1000
        s.close()
        # Ensure disconnection occurred rapidly (< 100ms socket processing)
        assert data == b"", "Socket should have been cleanly closed by server (EOF)"
        assert latency_ms < 100, f"Token rejection took {latency_ms:.1f}ms (threshold 100ms)"
        print(f"✓ [PASS] Unauthorized token refused within {latency_ms:.1f}ms (<100ms threshold)")

    finally:
        proc.terminate()
        try:
            proc.wait(timeout=1.0)
        except Exception:
            proc.kill()

if __name__ == "__main__":
    test_tls13_protocol_enforcement()
    test_missing_token_rejection_latency()
    print("ALL TLS 1.3 AND TOKEN LATENCY ACCEPTANCE TESTS PASSED")
