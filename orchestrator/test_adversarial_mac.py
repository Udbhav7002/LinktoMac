"""
Empirical Adversarial Stress Test Suite for Mac Desktop Application (orchestrator)
Tests:
1. Non-interactive startup of orchestrator/ui_app.py
2. Pywebview window open/close lifecycle, rapid destroy, and thread leak detection
3. OAuth local HTTP server behavior (port binding, search, exhaustion, callbacks, shutdown, error handling)
4. Import integrity and existing test execution across orchestrator
"""

import html
import http.client
import os
import socket
import subprocess
import sys
import threading
import time
import unittest
import urllib.parse
import urllib.request
import webbrowser

# Add orchestrator to sys.path
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

from ui_app import Api, ReusableHTTPServer, UI_DIR
from linktomac.backend import LinktoMacBackend, LinkMyMacBackend
from linktomac.pairing import DiscoveryListener


class TestNonInteractiveStartup(unittest.TestCase):
    """Area 1: Verify non-interactive startup of orchestrator/ui_app.py without Python tracebacks."""

    def test_startup_gui_mode_subprocess(self):
        """Start ui_app.py in a background process, let it initialize for 2.5s, verify no tracebacks, terminate cleanly."""
        cmd = [sys.executable, os.path.join(BASE_DIR, "ui_app.py")]
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            cwd=os.path.dirname(BASE_DIR),
            env=dict(os.environ, LINKTOMAC_HEADLESS="1", TESTING="1", LINKTOMAC_NO_BROWSER="1")
        )
        try:
            time.sleep(2.5)
            poll = proc.poll()
            if poll is not None:
                stdout, stderr = proc.communicate()
                self.fail(f"ui_app.py exited prematurely with code {poll}.\nSTDOUT: {stdout}\nSTDERR: {stderr}")

            # Verify no errors written to stderr while starting
            proc.terminate()
            stdout, stderr = proc.communicate(timeout=3.0)
            self.assertEqual(stderr.strip(), "", f"Unexpected traceback on stderr: {stderr}")
        except subprocess.TimeoutExpired:
            proc.kill()
            stdout, stderr = proc.communicate()
            self.fail(f"ui_app.py did not terminate cleanly within timeout.\nSTDERR: {stderr}")

    def test_startup_cli_help(self):
        """Run ui_app.py with --cli --help and verify exit code 0 without traceback."""
        cmd = [sys.executable, os.path.join(BASE_DIR, "ui_app.py"), "--cli", "--help"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        self.assertEqual(res.returncode, 0, f"CLI help failed: {res.stderr}")
        self.assertIn("LinktoMac Orchestrator", res.stdout)
        self.assertNotIn("Traceback", res.stderr)


class TestPywebviewLifecycleAndThreadLeaks(unittest.TestCase):
    """Area 2: Stress test pywebview window open/close, checking for thread leaks or hanging processes."""

    def test_window_open_js_eval_and_clean_close(self):
        """Verify pywebview starts, evaluates JS, destroys cleanly, and leaves no non-daemon threads."""
        test_script = """
import os, sys, time, threading
import webview
sys.path.insert(0, sys.argv[1])
from ui_app import Api, UI_DIR

api = Api()
def on_shown(window):
    assert window is not None
    # Verify DOM title or location evaluation
    title = window.evaluate_js("document.title")
    time.sleep(0.3)
    window.destroy()

window = webview.create_window(
    'Stress Test Window',
    url=str(UI_DIR / "index.html"),
    js_api=api,
    width=400,
    height=600,
    hidden=True,
    focus=False
)
api.window = window
window.events.closing += api.cleanup
window.events.closed += api.cleanup

webview.start(on_shown, window, http_server=True)

# Post-exit thread verification: only MainThread or daemon threads should remain
for t in list(threading.enumerate()):
    if not t.daemon and t != threading.main_thread():
        t.join(timeout=1.0)
non_daemon = [t.name for t in threading.enumerate() if not t.daemon and t != threading.main_thread()]
if non_daemon:
    print(f"THREAD_LEAK: {non_daemon}")
    sys.stdout.flush()
    os._exit(2)
api.cleanup()
sys.stdout.flush()
sys.stderr.flush()
os._exit(0)
"""
        cmd = [sys.executable, "-c", test_script, BASE_DIR]
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        self.assertEqual(res.returncode, 0, f"Window lifecycle failed: {res.stderr}\nSTDOUT: {res.stdout}")
        self.assertNotIn("THREAD_LEAK", res.stdout)

    def test_window_close_with_inflight_oauth_server(self):
        """Verify window close while OAuth server is actively serving shuts down server and unblocks thread."""
        test_script = """
import os, sys, time, threading, webbrowser
import webview
sys.path.insert(0, sys.argv[1])
from ui_app import Api, UI_DIR

webbrowser.open = lambda url: None
api = Api()
login_thread = None

def on_shown(window):
    global login_thread
    login_thread = threading.Thread(target=api.google_login)
    login_thread.start()
    time.sleep(0.5)
    assert api._server_running is True, "Server should be running"
    # Destroy window while server is active
    window.destroy()

window = webview.create_window('Test OAuth Inflight', url=str(UI_DIR / "index.html"), js_api=api, hidden=True, focus=False)
api.window = window
window.events.closing += api.cleanup
window.events.closed += api.cleanup

webview.start(on_shown, window, http_server=True)

# Verify login thread joined
login_thread.join(timeout=2.0)
if login_thread.is_alive():
    print("LEAK: login thread did not terminate")
    sys.stdout.flush()
    os._exit(3)
if api._server_running:
    print("LEAK: server_running flag still True")
    sys.stdout.flush()
    os._exit(4)
api.cleanup()
sys.stdout.flush()
sys.stderr.flush()
os._exit(0)
"""
        cmd = [sys.executable, "-c", test_script, BASE_DIR]
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        self.assertEqual(res.returncode, 0, f"In-flight OAuth window close failed: {res.stderr}\nSTDOUT: {res.stdout}")

    def test_consecutive_window_lifecycle_stress(self):
        """Execute 3 rapid consecutive window launches in subprocesses to verify no persistent zombie processes or port hogs."""
        test_script = """
import os, sys, time
import webview
sys.path.insert(0, sys.argv[1])
from ui_app import Api, UI_DIR

api = Api()
def on_shown(window):
    time.sleep(0.2)
    window.destroy()

window = webview.create_window('Quick Window', url=str(UI_DIR / "index.html"), js_api=api, hidden=True, focus=False)
api.window = window
window.events.closing += api.cleanup
webview.start(on_shown, window, http_server=True)
api.cleanup()
sys.stdout.flush()
sys.stderr.flush()
os._exit(0)
"""
        for i in range(3):
            cmd = [sys.executable, "-c", test_script, BASE_DIR]
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            self.assertEqual(res.returncode, 0, f"Consecutive run #{i+1} failed: {res.stderr}")


class TestOAuthLocalHttpServerAdversarial(unittest.TestCase):
    """Area 3: Test OAuth local HTTP server behavior: port binding, simulated callbacks, thread shutdown, edge cases."""

    def setUp(self):
        self._orig_browser_open = webbrowser.open
        webbrowser.open = lambda url: None

    def tearDown(self):
        webbrowser.open = self._orig_browser_open
        time.sleep(0.05)

    def test_port_binding_and_default_login_route(self):
        """Verify server binds to 54321+ and responds to /login with 200 and Firebase HTML."""
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)
        try:
            self.assertTrue(api._server_running)
            port = api._auth_server.server_address[1]
            url = f"http://127.0.0.1:{port}/login"
            with urllib.request.urlopen(url, timeout=3) as resp:
                self.assertEqual(resp.status, 200)
                body = resp.read().decode("utf-8")
                self.assertIn("firebaseConfig", body)
                self.assertIn("AIzaSyCr6T3Np-dI8lax8R2PfGDO57ZTPr0Eb2g", body)
        finally:
            api.cleanup()
            t.join(timeout=2.0)

    def test_port_conflict_search(self):
        """Occupy ports 54321..54323 and verify Api successfully finds 54324."""
        dummy_sockets = []
        try:
            for p in (54321, 54322, 54323):
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                if hasattr(socket, "SO_REUSEPORT"):
                    try:
                        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
                    except OSError:
                        pass
                s.bind(('127.0.0.1', p))
                s.listen(1)
                dummy_sockets.append(s)

            api = Api()
            t = threading.Thread(target=api.google_login, daemon=True)
            t.start()
            time.sleep(0.3)
            self.assertTrue(api._server_running)
            self.assertEqual(api._auth_server.server_address[1], 54324)

            # Cleanly unblock with success callback
            csrf_state = getattr(api._auth_server, "csrf_state", "")
            url = f"http://127.0.0.1:54324/callback?uid=test_uid&email=test@example.com&state={csrf_state}"
            with urllib.request.urlopen(url, timeout=3) as resp:
                self.assertEqual(resp.status, 200)
            t.join(timeout=2.0)
            self.assertFalse(t.is_alive())
        finally:
            for s in dummy_sockets:
                s.close()

    def test_port_exhaustion_graceful_failure(self):
        """Occupy all ports 54321..54330. Verify google_login returns None gracefully without crash."""
        dummy_sockets = []
        try:
            for p in range(54321, 54331):
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                if hasattr(socket, "SO_REUSEPORT"):
                    try:
                        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
                    except OSError:
                        pass
                s.bind(('127.0.0.1', p))
                s.listen(1)
                dummy_sockets.append(s)

            api = Api()
            result = api.google_login()
            self.assertIsNone(result)
            self.assertFalse(api._server_running)
            self.assertIsNone(api._auth_server)
        finally:
            for s in dummy_sockets:
                s.close()

    def test_simulated_success_callback_lifecycle(self):
        """Simulate successful OAuth redirect, verifying 200 status, response body, and clean thread join."""
        api = Api()
        results = []
        t = threading.Thread(target=lambda: results.append(api.google_login()), daemon=True)
        t.start()
        time.sleep(0.3)
        self.assertTrue(api._server_running)
        port = api._auth_server.server_address[1]
        csrf_state = getattr(api._auth_server, "csrf_state", "")

        url = f"http://127.0.0.1:{port}/callback?uid=u12345&email=tester%40gmail.com&state={csrf_state}"
        with urllib.request.urlopen(url, timeout=3) as resp:
            self.assertEqual(resp.status, 200)
            content = resp.read().decode("utf-8")
            self.assertIn("Login Successful", content)

        t.join(timeout=2.0)
        self.assertFalse(t.is_alive(), "Server thread did not terminate after success callback")
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0], {"uid": "u12345", "email": "tester@gmail.com", "error": None})

    def test_simulated_error_callback_user_cancelled(self):
        """Simulate user cancelling OAuth: /callback?error=user_cancelled. Verify clean shutdown and error return."""
        api = Api()
        results = []
        t = threading.Thread(target=lambda: results.append(api.google_login()), daemon=True)
        t.start()
        time.sleep(0.3)
        self.assertTrue(api._server_running)
        port = api._auth_server.server_address[1]
        csrf_state = getattr(api._auth_server, "csrf_state", "")

        url = f"http://127.0.0.1:{port}/callback?error=user_cancelled&state={csrf_state}"
        with urllib.request.urlopen(url, timeout=3) as resp:
            self.assertEqual(resp.status, 200)
            content = resp.read().decode("utf-8")
            self.assertIn("Login Failed", content)
            self.assertIn("user_cancelled", content)

        t.join(timeout=2.0)
        self.assertFalse(t.is_alive(), "Server thread did not terminate after error callback")
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0], {"error": "user_cancelled"})

    def test_adversarial_xss_in_callback_error(self):
        """Send malicious XSS payload in error query parameter; verify HTML escaping and clean shutdown."""
        api = Api()
        results = []
        t = threading.Thread(target=lambda: results.append(api.google_login()), daemon=True)
        t.start()
        time.sleep(0.3)
        port = api._auth_server.server_address[1]
        csrf_state = getattr(api._auth_server, "csrf_state", "")

        xss_payload = "<script>alert('XSS')</script>&foo=<b>bar</b>"
        encoded_err = urllib.parse.quote(xss_payload)
        url = f"http://127.0.0.1:{port}/callback?error={encoded_err}&state={csrf_state}"
        with urllib.request.urlopen(url, timeout=3) as resp:
            self.assertEqual(resp.status, 200)
            content = resp.read().decode("utf-8")
            self.assertIn(html.escape(xss_payload), content)
            self.assertNotIn("<script>alert", content)

        t.join(timeout=2.0)
        self.assertFalse(t.is_alive())
        self.assertEqual(results[0], {"error": xss_payload})

    def test_callback_without_params(self):
        """Access /callback with valid CSRF state but no user/error payload; verify it shuts down cleanly and returns None."""
        api = Api()
        results = []
        t = threading.Thread(target=lambda: results.append(api.google_login()), daemon=True)
        t.start()
        time.sleep(0.3)
        port = api._auth_server.server_address[1]
        csrf_state = getattr(api._auth_server, "csrf_state", "")

        url = f"http://127.0.0.1:{port}/callback?state={csrf_state}"
        with urllib.request.urlopen(url, timeout=3) as resp:
            self.assertEqual(resp.status, 200)

        t.join(timeout=2.0)
        self.assertFalse(t.is_alive())
        # Neither uid nor error set, should return None
        self.assertIsNone(results[0])

    def test_adversarial_csrf_forgery_protection(self):
        """Verify that requests missing state token or with forged state token return 403 Forbidden."""
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)
        try:
            self.assertTrue(api._server_running)
            port = api._auth_server.server_address[1]

            # Request with no state token at all
            url_no_state = f"http://127.0.0.1:{port}/callback?uid=attacker&email=attacker%40evil.com"
            import urllib.error
            with self.assertRaises(urllib.error.HTTPError) as ctx1:
                urllib.request.urlopen(url_no_state, timeout=3)
            self.assertEqual(ctx1.exception.code, 403)

            # Request with mismatched state token
            url_bad_state = f"http://127.0.0.1:{port}/callback?uid=attacker&email=attacker%40evil.com&state=invalid_123"
            with self.assertRaises(urllib.error.HTTPError) as ctx2:
                urllib.request.urlopen(url_bad_state, timeout=3)
            self.assertEqual(ctx2.exception.code, 403)

            # Ensure login was not triggered
            self.assertIsNone(api._auth_server.result_data.get("uid"))
        finally:
            api.cleanup()
            t.join(timeout=2.0)

    def test_unknown_path_returns_404(self):
        """Access non-existent path on server, verify 404 and that server remains alive until callback/cleanup."""
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)
        port = api._auth_server.server_address[1]

        try:
            req = urllib.request.Request(f"http://127.0.0.1:{port}/nonexistent_path")
            urllib.request.urlopen(req)
            self.fail("Expected HTTP 404")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 404)

        # Server should still be active
        self.assertTrue(api._server_running)

        # Now clean up
        api.cleanup()
        t.join(timeout=2.0)
        self.assertFalse(t.is_alive())

    def test_concurrent_google_login_calls(self):
        """Call google_login while already running; second call should return None immediately."""
        api = Api()
        t = threading.Thread(target=api.google_login, daemon=True)
        t.start()
        time.sleep(0.3)

        # Second call
        second_res = api.google_login()
        self.assertIsNone(second_res)
        self.assertTrue(api._server_running)

        # Clean up
        api.cleanup()
        t.join(timeout=2.0)
        self.assertFalse(t.is_alive())

    def test_cleanup_idempotency(self):
        """Calling api.cleanup() multiple times consecutively must be safe and idempotent."""
        api = Api()
        for _ in range(5):
            api.cleanup()
        self.assertFalse(api._server_running)
        self.assertIsNone(api._auth_server)


class TestImportIntegrityAndExistingTests(unittest.TestCase):
    """Area 4: Verify import integrity across all orchestrator modules and existing test files."""

    def test_import_orchestrator_core_modules(self):
        """Verify linktomac and orchestrator core modules can be imported without errors."""
        modules = [
            'ui_app',
            'linktomac',
            'linktomac.config',
            'linktomac.storage',
            'linktomac.adb',
            'linktomac.backend',
            'linktomac.pairing',
            'linktomac.cli',
            'linktomac.menubar_app',
            'simulate_android_app',
        ]
        import importlib
        for mod in modules:
            m = importlib.import_module(mod)
            self.assertIsNotNone(m, f"Module {mod} imported as None")

    def test_menubar_app_top_level_status(self):
        """Check status of orchestrator/menubar_app.py which requires rumps."""
        import importlib
        try:
            import rumps
            m = importlib.import_module('menubar_app')
            self.assertIsNotNone(m)
        except ImportError as e:
            # Document finding: rumps is an optional/uninstalled dependency not in requirements.txt
            # Ensure linktomac.menubar_app (which has graceful fallback) is used instead
            import linktomac.menubar_app
            self.assertTrue(hasattr(linktomac.menubar_app, 'main'))

    def test_existing_test_ui_app(self):
        """Run existing orchestrator/test_ui_app.py suite."""
        cmd = [sys.executable, "-m", "unittest", "test_ui_app.py"]
        res = subprocess.run(cmd, capture_output=True, text=True, cwd=BASE_DIR)
        self.assertEqual(res.returncode, 0, f"test_ui_app.py failed: {res.stderr}\n{res.stdout}")
        self.assertIn("OK", res.stderr)

    def test_existing_test_backend(self):
        """Run existing orchestrator/test_backend.py suite."""
        cmd = [sys.executable, "-m", "unittest", "test_backend.py"]
        res = subprocess.run(cmd, capture_output=True, text=True, cwd=BASE_DIR)
        self.assertEqual(res.returncode, 0, f"test_backend.py failed: {res.stderr}\n{res.stdout}")
        self.assertIn("OK", res.stderr)

    def test_existing_test_domain(self):
        """Run root test_domain.py if present."""
        root_dir = os.path.dirname(BASE_DIR)
        test_domain_path = os.path.join(root_dir, "test_domain.py")
        if not os.path.exists(test_domain_path):
            self.skipTest("test_domain.py not present in workspace")
        cmd = [sys.executable, "test_domain.py"]
        res = subprocess.run(cmd, capture_output=True, text=True, cwd=root_dir)
        self.assertEqual(res.returncode, 0, f"test_domain.py failed: {res.stderr}\n{res.stdout}")
        self.assertIn("Hostname: 127.0.0.1", res.stdout)


if __name__ == "__main__":
    unittest.main()
