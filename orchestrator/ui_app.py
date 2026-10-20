import html
import json
import logging
import os
import secrets
from pathlib import Path
import sys
import threading
import time
import urllib.parse
import webbrowser
import socketserver
from http.server import BaseHTTPRequestHandler, HTTPServer
import webview

# Ensure all request threads from ThreadingMixIn (including bottle/pywebview) are daemon threads
socketserver.ThreadingMixIn.daemon_threads = True

# Paths
BASE_DIR = Path(__file__).resolve().parent
UI_DIR = BASE_DIR / "ui"

# Ensure orchestrator package is accessible
sys.path.insert(0, str(BASE_DIR))
from linktomac.backend import LinktoMacBackend

logger = logging.getLogger(__name__)


class ReusableHTTPServer(HTTPServer):
    allow_reuse_address = True


class Api:
    def __init__(self):
        self.window = None
        self._server_running = False
        self._auth_server = None
        self.backend = LinktoMacBackend()
        self.backend.set_mirror_exit_callback(self._on_mirror_exit)

    def _on_mirror_exit(self, exit_code, target_name):
        if self.window:
            try:
                js_code = f"if (window.onMirrorProcessExit) {{ window.onMirrorProcessExit({exit_code}, {json.dumps(target_name)}); }}"
                self.window.evaluate_js(js_code)
            except Exception as e:
                logger.debug(f"Error evaluating JS for mirror exit: {e}")

    def set_window(self, window):
        self.window = window

    def google_login(self, timeout=120.0):
        print("🔗 Starting Google Login via system browser...")

        if self._server_running:
            print("Server already running, ignoring click...")
            return None

        result_data = {"uid": None, "email": None, "error": None}
        self._server_running = True
        csrf_state = secrets.token_urlsafe(32)

        class LoginHandler(BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                pass  # Suppress HTTP access logs

            def do_GET(self):
                if self.path.startswith("/callback"):
                    query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
                    state = query.get("state", [None])[0]
                    expected_state = getattr(self.server, "csrf_state", None)

                    # Strict CSRF state token validation: must be present and match expected state
                    if not expected_state or state != expected_state:
                        self.send_response(403)
                        self.send_header("Content-type", "text/html; charset=utf-8")
                        self.end_headers()
                        self.wfile.write(b"<!DOCTYPE html><html><body><h2>403 Forbidden</h2><p>Invalid or missing CSRF state token.</p></body></html>")
                        return

                    if "error" in query:
                        error_msg = query.get("error", ["Unknown error"])[0]
                        self.server.result_data["error"] = error_msg
                        self.send_response(200)
                        self.send_header("Content-type", "text/html; charset=utf-8")
                        self.end_headers()
                        escaped_err = html.escape(error_msg)
                        error_html = f"""<!DOCTYPE html>
<html>
<head><title>LinktoMac - Login Failed</title></head>
<body style="font-family: -apple-system, BlinkMacSystemFont, sans-serif; text-align: center; padding: 50px; background: #121212; color: #ff453a;">
    <h2>Login Failed</h2>
    <p>{escaped_err}</p>
    <p style="color: #aaa; margin-top: 20px;">You can close this tab and return to the LinktoMac app.</p>
    <script>setTimeout(() => window.close(), 3000);</script>
</body>
</html>"""
                        self.wfile.write(error_html.encode("utf-8"))
                    else:
                        self.server.result_data["uid"] = query.get("uid", [""])[0]
                        self.server.result_data["email"] = query.get("email", [""])[0]
                        self.send_response(200)
                        self.send_header("Content-type", "text/html; charset=utf-8")
                        self.end_headers()
                        success_html = """<!DOCTYPE html>
<html>
<head><title>LinktoMac - Login Successful</title></head>
<body style="font-family: -apple-system, BlinkMacSystemFont, sans-serif; text-align: center; padding: 50px; background: #121212; color: white;">
    <h2>Success!</h2>
    <p>You can close this tab and return to the LinktoMac app.</p>
    <script>setTimeout(() => window.close(), 2000);</script>
</body>
</html>"""
                        self.wfile.write(success_html.encode("utf-8"))

                    # Shutdown server in daemon thread after small delay so browser response finishes
                    def _delayed_shutdown(srv):
                        time.sleep(1.0)
                        try:
                            srv.shutdown()
                        except Exception:
                            pass
                    threading.Thread(target=_delayed_shutdown, args=(self.server,), daemon=True).start()

                elif self.path == "/login":
                    self.send_response(200)
                    self.send_header("Content-type", "text/html; charset=utf-8")
                    self.end_headers()

                    csrf_token = getattr(self.server, "csrf_state", "")
                    login_page = f"""<!DOCTYPE html>
<html>
<head>
    <title>LinktoMac Login</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-app-compat.js"></script>
    <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-auth-compat.js"></script>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; text-align: center; padding: 40px 20px; background: #121212; color: white; }}
        .card {{ max-width: 360px; margin: 40px auto; padding: 32px 24px; background: #1e1e1e; border-radius: 14px; border: 1px solid #333; box-shadow: 0 8px 32px rgba(0,0,0,0.4); }}
        .btn {{ padding: 12px 24px; font-size: 15px; font-weight: 500; cursor: pointer; border-radius: 8px; border: none; background: white; color: black; margin-top: 20px; display: inline-flex; align-items: center; justify-content: center; gap: 10px; width: 100%; box-sizing: border-box; transition: opacity 0.2s; }}
        .btn:hover {{ opacity: 0.9; }}
        #status {{ margin-top: 16px; font-size: 13px; color: #aaa; min-height: 20px; }}
        a {{ color: #888; font-size: 12px; text-decoration: none; display: inline-block; margin-top: 16px; }}
        a:hover {{ color: #ccc; }}
    </style>
</head>
<body>
    <div class="card">
        <h2 style="margin: 0 0 8px 0; font-size: 20px;">LinktoMac</h2>
        <p style="margin: 0; color: #888; font-size: 13px;">Sign in with Google to link your Android device</p>
        <button id="btn-login" class="btn" onclick="triggerLogin()">
            <svg width="18" height="18" viewBox="0 0 24 24"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg>
            Sign in with Google
        </button>
        <div id="status"></div>
        <div><a href="/callback?error=Cancelled&state={csrf_token}">Cancel and return to app</a></div>
    </div>
    <script>
        const firebaseConfig = {{ apiKey: "AIzaSyCr6T3Np-dI8lax8R2PfGDO57ZTPr0Eb2g", authDomain: "linktomac.firebaseapp.com", projectId: "linktomac" }};
        const csrfToken = "{csrf_token}";
        let auth, provider;
        try {{
            firebase.initializeApp(firebaseConfig);
            auth = firebase.auth();
            provider = new firebase.auth.GoogleAuthProvider();
        }} catch (e) {{
            console.error("Firebase init error", e);
        }}

        function triggerLogin() {{
            if (!auth) {{
                window.location.href = "/callback?error=" + encodeURIComponent("Firebase failed to load.") + "&state=" + encodeURIComponent(csrfToken);
                return;
            }}
            document.getElementById("status").innerText = "Signing in...";
            auth.signInWithPopup(provider).then((result) => {{
                document.getElementById("status").innerText = "Success! Redirecting...";
                window.location.href = "/callback?uid=" + encodeURIComponent(result.user.uid) + "&email=" + encodeURIComponent(result.user.email) + "&state=" + encodeURIComponent(csrfToken);
            }}).catch((err) => {{
                console.error("Auth error", err);
                const statusEl = document.getElementById("status");
                if (err.code === 'auth/popup-blocked') {{
                    statusEl.innerText = "Popup was blocked. Please click the button above to sign in.";
                }} else if (err.code === 'auth/popup-closed-by-user') {{
                    statusEl.innerText = "Sign in was closed. Click the button above to try again.";
                }} else {{
                    statusEl.innerText = err.message || "Sign in failed. Click to try again.";
                }}
            }});
        }}
    </script>
</body>
</html>"""
                    self.wfile.write(login_page.encode("utf-8"))
                else:
                    self.send_response(404)
                    self.end_headers()

        # Find a free port starting from 54321
        port = 54321
        server = None
        for p in range(54321, 54331):
            try:
                server = ReusableHTTPServer(('127.0.0.1', p), LoginHandler)
                port = p
                break
            except OSError:
                continue

        if not server:
            self._server_running = False
            print("❌ Failed to bind local server")
            return None

        server.result_data = result_data
        server.csrf_state = csrf_state
        self._auth_server = server

        # Open the system browser (Chrome/Safari) using localhost (Firebase authorized domain)
        if not os.environ.get("LINKTOMAC_NO_BROWSER") and not os.environ.get("TESTING"):
            webbrowser.open(f"http://localhost:{port}/login")

        timeout_timer = None
        if timeout and timeout > 0:
            def _handle_timeout():
                logger.warning(f"Google login timed out after {timeout}s")
                try:
                    server.shutdown()
                except Exception:
                    pass

            timeout_timer = threading.Timer(timeout, _handle_timeout)
            timeout_timer.daemon = True
            timeout_timer.start()

        try:
            # Block until callback is hit, server is shut down, or timeout expires
            server.serve_forever()
        finally:
            if timeout_timer:
                timeout_timer.cancel()
            try:
                server.server_close()
            except Exception:
                pass
            self._auth_server = None
            self._server_running = False

        if result_data.get("uid"):
            print(f"✅ Login successful for {result_data['email']}")
            try:
                auth_file = Path.home() / ".linktomac" / "auth.json"
                auth_file.parent.mkdir(parents=True, exist_ok=True)
                with open(auth_file, "w") as f:
                    json.dump({"uid": result_data["uid"], "email": result_data["email"]}, f)
            except Exception as e:
                logger.debug(f"Failed to save auth session: {e}")
            return result_data
        elif result_data.get("error"):
            print(f"❌ Login failed: {result_data['error']}")
            return {"error": result_data["error"]}
        return None

    def get_saved_user(self):
        auth_file = Path.home() / ".linktomac" / "auth.json"
        if auth_file.exists():
            try:
                with open(auth_file, "r") as f:
                    return json.load(f)
            except Exception:
                pass
        return None

    def sign_out(self):
        auth_file = Path.home() / ".linktomac" / "auth.json"
        if auth_file.exists():
            try:
                auth_file.unlink()
            except Exception:
                pass
        return True

    def connect_device(self, device_data):
        return self.backend.smart_connect(device_data)

    def connect_to_ip(self, ip):
        if not ip or ip == "undefined":
            return {"success": False, "error": "Invalid target"}
        return self.backend.smart_connect({"ip": ip})

    def start_remote_session(self):
        print("🔗 Starting remote session...")
        return self.backend.start_remote_session()

    def stop_remote_session(self):
        print("🛑 Stopping remote session...")
        return self.backend.stop_remote_session()

    def stop_mirror(self, ip=None):
        print(f"🛑 Stopping mirror for {ip or 'all'}...")
        return self.backend.stop_mirror(ip)

    def get_paired_devices(self):
        return self.backend.get_paired_devices()

    def get_discovered_devices(self):
        return self.backend.get_discovered_devices()

    def get_usb_device(self):
        return self.backend.get_connected_usb_device()

    def start_discovery(self):
        def _on_devices_updated(devices):
            if self.window:
                try:
                    js_code = f"if (window.updateDiscoveredDevices) {{ window.updateDiscoveredDevices({json.dumps(devices)}); }}"
                    self.window.evaluate_js(js_code)
                except Exception as e:
                    logger.debug(f"Error evaluating JS for device update: {e}")

        def _on_usb_changed(usb_dev):
            if self.window:
                try:
                    js_code = f"if (window.updateUsbDevice) {{ window.updateUsbDevice({json.dumps(usb_dev)}); }}"
                    self.window.evaluate_js(js_code)
                except Exception as e:
                    logger.debug(f"Error evaluating JS for usb update: {e}")

        self.backend.set_callback(_on_devices_updated)
        self.backend.set_usb_callback(_on_usb_changed)
        self.backend.start_discovery()
        return True

    def cleanup(self, *args, **kwargs):
        """Cleanup method for shutting down the auth server and all background tasks."""
        if self._auth_server:
            server = self._auth_server
            self._auth_server = None
            try:
                t = threading.Thread(target=server.shutdown, daemon=True)
                t.start()
                t.join(timeout=1.5)
                server.server_close()
            except Exception as e:
                logger.debug(f"Error during auth server cleanup: {e}")

        self._server_running = False
        self.window = None

        try:
            self.backend.stop_discovery()
        except Exception:
            pass

        try:
            self.backend.stop_remote_session()
        except Exception:
            pass

        try:
            self.backend.stop_mirror()
        except Exception:
            pass


def main():
    if "--cli" in sys.argv:
        from linktomac import cli
        sys.argv.remove("--cli")
        cli.main()
        return

    api = Api()
    is_headless = os.environ.get("LINKTOMAC_HEADLESS") or os.environ.get("TESTING")
    window = webview.create_window(
        'LinktoMac',
        url=str(UI_DIR / "index.html"),
        js_api=api,
        width=400,
        height=600,
        resizable=False,
        frameless=False,
        hidden=bool(is_headless),
        focus=not bool(is_headless)
    )
    api.window = window
    api.start_discovery()
    window.events.closing += api.cleanup
    window.events.closed += api.cleanup
    webview.start(http_server=True)


if __name__ == '__main__':
    main()
