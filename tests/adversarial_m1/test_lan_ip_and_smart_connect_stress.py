#!/usr/bin/env python3
"""
Empirical Adversarial Stress Suite for Milestone 1:
1. get_mac_local_ip() behavior, format, edge cases, socket fallbacks, and concurrency.
2. smart_connect() payload schema, direct vs remote modes, error paths, and cancellation.
"""

import os
import sys
import socket
import unittest
from unittest.mock import patch, MagicMock
import concurrent.futures
import re

# Ensure orchestrator is in python path
repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
orchestrator_dir = os.path.join(repo_root, "orchestrator")
if orchestrator_dir not in sys.path:
    sys.path.insert(0, orchestrator_dir)

from linktomac.backend import LinkMyMacBackend

class TestLanIpAndSmartConnectStress(unittest.TestCase):

    def setUp(self):
        self.backend = LinkMyMacBackend()

    def tearDown(self):
        self.backend.stop_mirror()
        self.backend.stop_remote_session()

    # =========================================================================
    # 1. get_mac_local_ip() STRESS & FALLBACK TESTS
    # =========================================================================

    def test_get_mac_local_ip_returns_valid_ipv4(self):
        """Verify get_mac_local_ip returns a valid IPv4 string under normal conditions."""
        ip = self.backend.get_mac_local_ip()
        self.assertIsInstance(ip, str)
        self.assertTrue(len(ip) >= 7)
        parts = ip.split(".")
        self.assertEqual(len(parts), 4, f"IP '{ip}' does not have 4 octets")
        for p in parts:
            val = int(p)
            self.assertTrue(0 <= val <= 255, f"Octet '{p}' out of range in IP '{ip}'")

    def test_get_mac_local_ip_target_variations(self):
        """Test with various target IPs including loopback, broadcast, LAN, and empty."""
        # Empty target should resolve against default routing (8.8.8.8)
        ip_empty = self.backend.get_mac_local_ip("")
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_empty))

        # Localhost / loopback should route against 8.8.8.8 default
        ip_loopback = self.backend.get_mac_local_ip("127.0.0.1")
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_loopback))

        ip_zero = self.backend.get_mac_local_ip("0.0.0.0")
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_zero))

        ip_host = self.backend.get_mac_local_ip("localhost")
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_host))

        # Normal LAN target
        ip_lan = self.backend.get_mac_local_ip("192.168.1.100")
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_lan))

    def test_get_mac_local_ip_socket_failure_fallback_to_hostname(self):
        """Simulate UDP socket connection failure (e.g. no network interfaces up) and verify fallback."""
        with patch("socket.socket") as mock_socket_ctor:
            mock_s = MagicMock()
            mock_s.connect.side_effect = OSError("Network is unreachable")
            mock_socket_ctor.return_value = mock_s

            with patch("socket.gethostname", return_value="test-mac.local"):
                with patch("socket.gethostbyname", return_value="192.168.50.25"):
                    ip = self.backend.get_mac_local_ip("10.0.0.2")
                    self.assertEqual(ip, "192.168.50.25")

    def test_get_mac_local_ip_complete_failure_fallback_to_loopback(self):
        """Simulate catastrophic network failure where both socket and hostname resolution fail."""
        with patch("socket.socket") as mock_socket_ctor:
            mock_s = MagicMock()
            mock_s.connect.side_effect = OSError("Network down")
            mock_socket_ctor.return_value = mock_s

            with patch("socket.gethostname", side_effect=Exception("DNS failure")):
                ip = self.backend.get_mac_local_ip("10.0.0.2")
                self.assertEqual(ip, "127.0.0.1")

    def test_get_mac_local_ip_concurrent_stress(self):
        """Stress get_mac_local_ip under 50 concurrent threads."""
        targets = ["", "127.0.0.1", "192.168.1.1", "10.0.0.1", "172.16.0.1"] * 10
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
            futures = [pool.submit(self.backend.get_mac_local_ip, t) for t in targets]
            results = [f.result(timeout=5) for f in futures]

        self.assertEqual(len(results), 50)
        for res in results:
            self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", res), f"Invalid IP returned: {res}")

    # =========================================================================
    # 2. smart_connect() PAYLOAD & FLOW VERIFICATION
    # =========================================================================

    def test_smart_connect_local_direct_payload_format(self):
        """Verify smart_connect local direct mode payload matches schema."""
        test_ip = "127.0.0.1"
        with patch.object(self.backend, "launch_mirror", return_value=(True, None)):
            res = self.backend.smart_connect({"ip": test_ip, "name": "Pixel 7 Pro", "device_id": "test_dev_1"})

            self.assertIsInstance(res, dict)
            self.assertTrue(res.get("success"), "Expected success=True")
            self.assertEqual(res.get("mode"), "local")
            self.assertEqual(res.get("name"), "Pixel 7 Pro")
            self.assertIn("mac_ip", res)
            self.assertTrue(isinstance(res["mac_ip"], str) and len(res["mac_ip"]) > 0)
            self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", res["mac_ip"]))

    def test_smart_connect_accepts_raw_string_ip(self):
        """Verify smart_connect correctly handles device_data as a plain string IP."""
        with patch.object(self.backend, "launch_mirror", return_value=(True, None)):
            res = self.backend.smart_connect("127.0.0.1")
            self.assertTrue(res.get("success"))
            self.assertEqual(res.get("mode"), "local")
            self.assertEqual(res.get("name"), "Android Device")
            self.assertIn("mac_ip", res)

    def test_smart_connect_invalid_or_none_device_data(self):
        """Verify smart_connect handles None or invalid types gracefully."""
        with patch.object(self.backend, "start_remote_session", return_value={"success": False}):
            # None device data
            res_none = self.backend.smart_connect(None)
            self.assertFalse(res_none.get("success"))

            # Number device data
            res_int = self.backend.smart_connect(12345)
            self.assertFalse(res_int.get("success"))

            # Empty dict
            res_empty = self.backend.smart_connect({})
            self.assertFalse(res_empty.get("success"))

    def test_smart_connect_direct_launch_failure_falls_back_to_remote(self):
        """When local launch_mirror fails, smart_connect must fall back to remote relay."""
        with patch.object(self.backend, "launch_mirror", return_value=(False, "Connection refused")):
            with patch.object(self.backend, "start_remote_session", return_value={
                "success": True,
                "code": "connect.pinggy.io:44321"
            }):
                res = self.backend.smart_connect({"ip": "127.0.0.1", "name": "Test Phone"})
                self.assertTrue(res.get("success"))
                self.assertEqual(res.get("mode"), "remote")
                self.assertEqual(res.get("host"), "connect.pinggy.io")
                self.assertEqual(res.get("port"), 44321)
                self.assertEqual(res.get("session_code"), "connect.pinggy.io:44321")

    def test_smart_connect_remote_session_with_invalid_code_format(self):
        """Test fallback when remote session returns malformed code string (missing port)."""
        with patch.object(self.backend, "launch_mirror", return_value=(False, "Failed")):
            with patch.object(self.backend, "start_remote_session", return_value={
                "success": True,
                "code": "invalid_code_no_colon"
            }):
                res = self.backend.smart_connect({"ip": "127.0.0.1", "name": "Test Phone"})
                self.assertTrue(res.get("success"))
                self.assertEqual(res.get("mode"), "remote")
                self.assertEqual(res.get("host"), "")
                self.assertEqual(res.get("port"), 0)

    def test_smart_connect_cancellation(self):
        """Verify smart_connect honors _cancel_remote event flag after local mirror failure."""
        def cancel_during_mirror(ip):
            self.backend._cancel_remote.set()
            return (False, "Process terminated")

        with patch.object(self.backend, "launch_mirror", side_effect=cancel_during_mirror):
            with patch.object(self.backend, "start_remote_session") as mock_remote:
                res = self.backend.smart_connect({"ip": "127.0.0.1", "name": "Phone"})
                self.assertFalse(res.get("success"))
                self.assertIn("cancelled", res.get("error", "").lower())
                mock_remote.assert_not_called()

    def test_get_mac_local_ip_ipv6_and_malformed_inputs(self):
        """Test get_mac_local_ip with IPv6, None, and garbage inputs."""
        # IPv6 address
        ip_v6 = self.backend.get_mac_local_ip("2001:4860:4860::8888")
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_v6))

        # None input
        ip_none = self.backend.get_mac_local_ip(None)
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_none))

        # Malformed strings
        ip_garbage = self.backend.get_mac_local_ip("not_an_ip_%#@!_999.999")
        self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", ip_garbage))

    def test_smart_connect_storage_exception_resilience(self):
        """Ensure storage.save_device throwing an exception does not break smart_connect."""
        with patch.object(self.backend, "launch_mirror", return_value=(True, None)):
            with patch("linktomac.storage.save_device", side_effect=Exception("Disk full")):
                res = self.backend.smart_connect({"ip": "127.0.0.1", "name": "Phone"})
                self.assertTrue(res.get("success"))
                self.assertEqual(res.get("mode"), "local")
                self.assertIn("mac_ip", res)

    def test_smart_connect_concurrent_calls(self):
        """Ensure thread safety when multiple smart_connect calls are triggered concurrently."""
        with patch.object(self.backend, "launch_mirror", return_value=(True, None)):
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
                futures = [
                    pool.submit(self.backend.smart_connect, {"ip": "127.0.0.1", "name": f"Phone_{i}"})
                    for i in range(20)
                ]
                results = [f.result(timeout=5) for f in futures]

            self.assertEqual(len(results), 20)
            for res in results:
                self.assertTrue(res.get("success"))
                self.assertEqual(res.get("mode"), "local")
                self.assertTrue(re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", res.get("mac_ip", "")))


if __name__ == "__main__":
    unittest.main()
