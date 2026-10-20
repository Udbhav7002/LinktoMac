import os
import sys
import unittest
import threading
import time
import urllib.request
import webbrowser

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from ui_app import Api, ReusableHTTPServer
from linktomac.backend import LinktoMacBackend, LinkMyMacBackend
from linktomac.pairing import DiscoveryListener


class TestUiAppLifecycle(unittest.TestCase):
    def setUp(self):
        # Prevent opening actual browser during unit tests
        self._orig_browser_open = webbrowser.open
        webbrowser.open = lambda url: None

    def tearDown(self):
        webbrowser.open = self._orig_browser_open

    def test_api_initialization(self):
        api = Api()
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)
        self.assertIsNotNone(api.backend)

    def test_reusable_server_flag(self):
        self.assertTrue(ReusableHTTPServer.allow_reuse_address)

    def test_oauth_success_callback(self):
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)
        self.assertTrue(api._server_running)
        self.assertIsNotNone(api._auth_server)

        port = api._auth_server.server_address[1]
        csrf_state = getattr(api._auth_server, "csrf_state", "")
        url = f"http://127.0.0.1:{port}/callback?uid=test_user_123&email=user%40example.com&state={csrf_state}"
        with urllib.request.urlopen(url, timeout=3) as resp:
            self.assertEqual(resp.status, 200)
            body = resp.read().decode("utf-8")
            self.assertIn("Success", body)

        t.join(timeout=2.0)
        self.assertFalse(t.is_alive())
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)

    def test_oauth_error_callback(self):
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)
        self.assertTrue(api._server_running)

        port = api._auth_server.server_address[1]
        csrf_state = getattr(api._auth_server, "csrf_state", "")
        url = f"http://127.0.0.1:{port}/callback?error=user_cancelled_auth&state={csrf_state}"
        with urllib.request.urlopen(url, timeout=3) as resp:
            self.assertEqual(resp.status, 200)
            body = resp.read().decode("utf-8")
            self.assertIn("Login Failed", body)

        t.join(timeout=2.0)
        self.assertFalse(t.is_alive())
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)

    def test_oauth_csrf_validation_rejects_missing_and_invalid_state(self):
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)
        try:
            self.assertTrue(api._server_running)
            port = api._auth_server.server_address[1]

            # 1. Missing state must be rejected with 403 Forbidden
            url_missing = f"http://127.0.0.1:{port}/callback?uid=evil_uid&email=evil%40example.com"
            import urllib.error
            with self.assertRaises(urllib.error.HTTPError) as ctx:
                urllib.request.urlopen(url_missing, timeout=3)
            self.assertEqual(ctx.exception.code, 403)

            # 2. Invalid state must be rejected with 403 Forbidden
            url_invalid = f"http://127.0.0.1:{port}/callback?uid=evil_uid&email=evil%40example.com&state=forged_state"
            with self.assertRaises(urllib.error.HTTPError) as ctx:
                urllib.request.urlopen(url_invalid, timeout=3)
            self.assertEqual(ctx.exception.code, 403)

            # Server should still be running and uncompromised
            self.assertTrue(api._server_running)
            self.assertIsNone(api._auth_server.result_data.get("uid"))
        finally:
            api.cleanup()
            t.join(timeout=2.0)

    def test_api_cleanup_shuts_down_server(self):
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)
        self.assertTrue(api._server_running)
        self.assertIsNotNone(api._auth_server)

        # Cleanup should cleanly unblock serve_forever
        api.cleanup()

        t.join(timeout=2.0)
        self.assertFalse(t.is_alive())
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)

    def test_connect_to_ip_validation(self):
        api = Api()
        res_none = api.connect_to_ip(None)
        self.assertFalse(res_none["success"])
        res_undef = api.connect_to_ip("undefined")
        self.assertFalse(res_undef["success"])
        res_empty = api.connect_to_ip("")
        self.assertFalse(res_empty["success"])

    def test_oauth_safety_timeout_exits_cleanly(self):
        api = Api()
        start = time.time()
        result = api.google_login(timeout=0.3)
        elapsed = time.time() - start
        self.assertIsNone(result)
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)
        self.assertLess(elapsed, 2.0)

    def test_api_discovery_methods(self):
        api = Api()
        try:
            # get_discovered_devices initially empty
            self.assertEqual(api.get_discovered_devices(), [])
            # simulate discovered device
            api.backend.discovered_devices["127.0.0.1"] = {
                "ip": "127.0.0.1", "name": "USB Device", "is_usb": True
            }
            devs = api.get_discovered_devices()
            self.assertEqual(len(devs), 1)
            self.assertEqual(devs[0]["name"], "USB Device")
        finally:
            api.cleanup()


class TestBackendAndPairing(unittest.TestCase):
    def test_backend_alias(self):
        self.assertIs(LinktoMacBackend, LinkMyMacBackend)

    def test_discovery_without_callback(self):
        """Verify discovery does not raise AttributeError if set_callback was not called."""
        backend = LinktoMacBackend()
        backend.start_discovery()
        try:
            backend.listener.on_device_found("10.0.0.42", "Phone", "dev1", False)
            self.assertIn("10.0.0.42", backend.discovered_devices)
        finally:
            backend.stop_discovery()

    def test_launch_mirror_sets_cwd_and_env(self):
        backend = LinktoMacBackend()
        from unittest.mock import patch
        with patch("subprocess.Popen") as mock_popen:
            mock_proc = mock_popen.return_value
            mock_proc.poll.return_value = None
            success, err = backend.launch_mirror("10.0.0.42")
            self.assertTrue(success)
            self.assertEqual(err, "")
            mock_popen.assert_called_once()
            _, kwargs = mock_popen.call_args
            self.assertIn("cwd", kwargs)
            self.assertIn("env", kwargs)
            self.assertIn("PYTHONPATH", kwargs["env"])

    def test_discovery_callback_with_tls(self):
        backend = LinktoMacBackend()
        discovered = []
        backend.set_callback(lambda devs: discovered.extend(devs))
        backend.start_discovery()
        try:
            # Simulate device announcement with 4 parameters
            backend.listener.on_device_found("192.168.1.100", "Pixel 8", "pixel_id", True)
            self.assertIn("192.168.1.100", backend.discovered_devices)
            dev = backend.discovered_devices["192.168.1.100"]
            self.assertEqual(dev["name"], "Pixel 8")
            self.assertTrue(dev["is_tls"])
        finally:
            backend.stop_discovery()

    def test_stop_mirror_clean(self):
        backend = LinktoMacBackend()
        self.assertTrue(backend.stop_mirror())


if __name__ == "__main__":
    unittest.main()
