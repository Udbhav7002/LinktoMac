import os
import sys
import time
import unittest
from unittest.mock import patch, MagicMock

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from linktomac.backend import LinktoMacBackend, LinkMyMacBackend
from linktomac.pairing import DiscoveryListener


class TestLinktoMacBackend(unittest.TestCase):
    def test_backend_alias(self):
        """Test that LinktoMacBackend is an alias for LinkMyMacBackend."""
        self.assertIs(LinktoMacBackend, LinkMyMacBackend)
        backend = LinktoMacBackend()
        self.assertIsNotNone(backend)

    def test_discovery_listener_signatures(self):
        """Test that DiscoveryListener implements Zeroconf ServiceListener methods."""
        events = []
        listener = DiscoveryListener(lambda ip, name, dev_id, tls: events.append((ip, name, dev_id, tls)))
        self.assertTrue(callable(getattr(listener, 'add_service', None)))
        self.assertTrue(callable(getattr(listener, 'remove_service', None)))
        self.assertTrue(callable(getattr(listener, 'update_service', None)))
        self.assertTrue(callable(getattr(listener, 'on_service_state_change', None)))

    def test_stop_mirror(self):
        """Test stop_mirror terminates running processes cleanly."""
        backend = LinktoMacBackend()
        self.assertTrue(backend.stop_mirror())

    def test_get_discovered_devices(self):
        """Verify get_discovered_devices returns snapshot of discovered wireless devices."""
        backend = LinktoMacBackend()
        backend.discovered_devices["192.168.1.100"] = {
            "ip": "192.168.1.100", "name": "Pixel 10a", "device_id": "wifi_192.168.1.100", "is_tls": False
        }
        devs = backend.get_discovered_devices()
        self.assertEqual(len(devs), 1)
        self.assertEqual(devs[0]["ip"], "192.168.1.100")
        self.assertEqual(devs[0]["name"], "Pixel 10a")

    def test_launch_mirror_terminates_previous_instance(self):
        """Verify launch_mirror terminates any existing active mirror on that IP before starting new one."""
        backend = LinktoMacBackend()

        with patch.object(backend, "stop_mirror") as mock_stop, \
             patch("subprocess.Popen") as mock_popen:
            mock_popen.return_value.poll.return_value = None
            success, err = backend.launch_mirror("192.168.1.100")
            self.assertTrue(success)
            mock_stop.assert_called_with("192.168.1.100")

    def test_smart_connect_local_mode_success(self):
        """Verify direct launch_mirror call when _is_same_subnet is True, without invoking connect_ex."""
        backend = LinktoMacBackend()
        with patch.object(backend, "_is_same_subnet", return_value=True), \
             patch.object(backend, "launch_mirror", return_value=(True, "")) as mock_launch, \
             patch("socket.socket") as mock_sock_cls, \
             patch("linktomac.storage.save_device"):
            mock_sock = MagicMock()
            mock_sock_cls.return_value = mock_sock

            res = backend.smart_connect({"ip": "192.168.1.100", "name": "Pixel 10a"})
            self.assertTrue(res["success"])
            self.assertEqual(res["mode"], "local")
            self.assertEqual(res["name"], "Pixel 10a")
            mock_launch.assert_called_with("192.168.1.100")
            mock_sock.connect_ex.assert_not_called()

    def test_smart_connect_fallback_to_remote_relay(self):
        """Verify fallback to start_remote_session when launch_mirror returns (False, ...)."""
        backend = LinktoMacBackend()
        with patch.object(backend, "_is_same_subnet", return_value=True), \
             patch.object(backend, "launch_mirror", return_value=(False, "Connection refused")) as mock_launch, \
             patch.object(backend, "start_remote_session", return_value={"success": True, "code": "a.pinggy.io:44321"}) as mock_remote:

            res = backend.smart_connect({"ip": "192.168.1.100", "name": "Pixel 10a"})
            self.assertTrue(res["success"])
            self.assertEqual(res["mode"], "remote")
            self.assertEqual(res["name"], "Pixel 10a")
            self.assertEqual(res["session_code"], "a.pinggy.io:44321")
            mock_launch.assert_called_with("192.168.1.100")
            mock_remote.assert_called_once()

    def test_smart_connect_both_fail_graceful_error(self):
        """Verify graceful error handling when both local launch and remote relay fail."""
        backend = LinktoMacBackend()
        with patch.object(backend, "_is_same_subnet", return_value=True), \
             patch.object(backend, "launch_mirror", return_value=(False, "Connection refused")) as mock_launch, \
             patch.object(backend, "start_remote_session", return_value={"success": False, "error": "Relay down"}) as mock_remote:

            res = backend.smart_connect({"ip": "192.168.1.100", "name": "Pixel 10a"})
            self.assertFalse(res["success"])
            self.assertIn("Device unreachable", res["error"])
            mock_launch.assert_called_with("192.168.1.100")
            mock_remote.assert_called_once()

    def test_smart_connect_bypasses_connect_ex_probe(self):
        """Verify smart_connect never performs raw TCP connect_ex probe on port 27189."""
        backend = LinktoMacBackend()
        with patch("socket.socket") as mock_sock_cls, \
             patch.object(backend, "_is_same_subnet", return_value=True), \
             patch.object(backend, "launch_mirror", return_value=(True, "")), \
             patch("linktomac.storage.save_device"):
            mock_sock = MagicMock()
            mock_sock_cls.return_value = mock_sock

            res = backend.smart_connect({"ip": "192.168.1.100", "name": "Pixel 10a"})
            self.assertTrue(res["success"])
            mock_sock.connect_ex.assert_not_called()

    def test_smart_connect_different_subnet_remote_direct(self):
        """Verify smart_connect on different subnet bypasses local launch and directly starts remote relay."""
        backend = LinktoMacBackend()
        with patch.object(backend, "_is_same_subnet", return_value=False), \
             patch.object(backend, "launch_mirror") as mock_launch, \
             patch.object(backend, "start_remote_session", return_value={"success": True, "code": "a.pinggy.io:44321"}) as mock_remote:

            res = backend.smart_connect({"ip": "10.200.1.5", "name": "Remote Pixel"})
            self.assertTrue(res["success"])
            self.assertEqual(res["mode"], "remote")
            mock_launch.assert_not_called()
            mock_remote.assert_called_once()

    def test_smart_connect_unmocked_launch_mirror_fallback_regression(self):
        """Verify smart_connect falls back to remote relay when unmocked launch_mirror fails.

        Ensures that launch_mirror executes stop_mirror, _cancel_remote does not poison the
        internal connection state, and smart_connect seamlessly falls back to start_remote_session.
        """
        backend = LinktoMacBackend()
        mock_proc = MagicMock()
        mock_proc.poll.return_value = 1
        mock_proc.returncode = 1

        with patch.object(backend, "_is_same_subnet", return_value=True), \
             patch.object(backend, "stop_mirror", wraps=backend.stop_mirror) as spy_stop, \
             patch("subprocess.Popen", return_value=mock_proc) as mock_popen, \
             patch("time.sleep"), \
             patch.object(backend, "start_remote_session", return_value={"success": True, "code": "a.pinggy.io:44321"}) as mock_remote:

            res = backend.smart_connect({"ip": "192.168.1.100", "name": "Pixel 10a"})
            self.assertTrue(res.get("success"))
            self.assertEqual(res.get("mode"), "remote")
            self.assertEqual(res.get("name"), "Pixel 10a")
            self.assertEqual(res.get("session_code"), "a.pinggy.io:44321")
            self.assertEqual(res.get("host"), "a.pinggy.io")
            self.assertEqual(res.get("port"), 44321)

            # Verify unmocked launch_mirror executed stop_mirror and attempted subprocess launch
            mock_popen.assert_called_once()
            spy_stop.assert_any_call("192.168.1.100")

            # Verify _cancel_remote did not poison the state
            self.assertFalse(backend._cancel_remote.is_set())

            # Verify fallback was seamlessly invoked
            mock_remote.assert_called_once()

    def test_smart_connect_loopback_local_mode(self):
        """Verify loopback IP (127.0.0.1 and localhost) connects via direct local launch (mode: 'local')."""
        backend = LinktoMacBackend()

        for host in ("127.0.0.1", "localhost"):
            with self.subTest(host=host):
                with patch.object(backend, "launch_mirror", return_value=(True, "")) as mock_launch, \
                     patch.object(backend, "start_remote_session") as mock_remote, \
                     patch("linktomac.storage.save_device"):

                    res = backend.smart_connect({"ip": host, "name": f"Device on {host}"})
                    self.assertTrue(res.get("success"))
                    self.assertEqual(res.get("mode"), "local")
                    self.assertEqual(res.get("name"), f"Device on {host}")
                    mock_launch.assert_called_once_with(host)
                    mock_remote.assert_not_called()

    def test_smart_connect_invalid_input_graceful_handling(self):
        """Verify smart_connect(None) and smart_connect("") gracefully handle invalid input without raising AttributeError."""
        backend = LinktoMacBackend()

        for invalid_input in (None, "", {}, {"ip": None}):
            with self.subTest(invalid_input=invalid_input):
                with patch.object(backend, "start_remote_session", return_value={"success": False, "error": "Device unreachable"}):
                    try:
                        res = backend.smart_connect(invalid_input)
                    except AttributeError as e:
                        self.fail(f"smart_connect({invalid_input!r}) raised AttributeError: {e}")

                    self.assertIsInstance(res, dict)
                    self.assertFalse(res.get("success"))
                    self.assertIn("error", res)

    def test_start_remote_session_cancelled_before_and_after_mirror_proc(self):
        """Verify start_remote_session cleans up and avoids orphaned processes if _cancel_remote is set."""
        backend = LinktoMacBackend()

        # Cancellation before mirror_proc
        backend._cancel_remote.set()
        mock_pinggy = MagicMock()
        mock_pinggy.poll.return_value = None
        mock_pinggy.stdout.readline.side_effect = ["tcp://a.pinggy.io:44321\n", ""]

        with patch("subprocess.Popen", return_value=mock_pinggy) as mock_popen, \
             patch.object(backend, "_kill_pg") as mock_kill:
            res = backend.start_remote_session()
            self.assertFalse(res["success"])
            self.assertIn("cancelled", res["error"].lower())
            mock_kill.assert_called_with(mock_pinggy)
            self.assertNotIn("ngrok", backend.running_mirrors)
            self.assertNotIn("mirror", backend.running_mirrors)

    def test_on_found_detects_property_updates(self):
        """Verify mDNS on_found callback fires when an existing device updates its name or TLS flag."""
        backend = LinktoMacBackend()
        updates = []
        backend.set_callback(lambda devs: updates.append(list(devs)))
        backend.start_discovery()
        try:
            # Initial discovery
            backend.listener.on_device_found("192.168.1.80", "Device V1", "dev_80", False)
            self.assertEqual(len(updates), 1)
            self.assertEqual(updates[0][0]["name"], "Device V1")

            # Updated announcement with new name
            backend.listener.on_device_found("192.168.1.80", "Device V2", "dev_80", True)
            self.assertEqual(len(updates), 2)
            self.assertEqual(updates[1][0]["name"], "Device V2")
            self.assertTrue(updates[1][0]["is_tls"])
        finally:
            backend.stop_discovery()

    def test_cli_wireless_mode_routes_directly_without_adb(self):
        """Verify cli.py in wireless mode routes mirror_client directly to <ip>:27189 without failing on absent ADB."""
        from linktomac import cli

        test_args = ["cli.py", "--wireless", "192.168.1.50"]
        with patch("sys.argv", test_args), \
             patch("pathlib.Path.exists", return_value=True), \
             patch("linktomac.adb.AdbClient.connect", side_effect=Exception("No ADB")), \
             patch("subprocess.run") as mock_run:
            cli.main()
            mock_run.assert_called_once()
            cmd_run = mock_run.call_args[0][0]
            self.assertIn("--connect", cmd_run)
            self.assertIn("192.168.1.50", cmd_run)
            self.assertIn("--video-port", cmd_run)
            self.assertIn("27189", cmd_run)


if __name__ == "__main__":
    unittest.main()
