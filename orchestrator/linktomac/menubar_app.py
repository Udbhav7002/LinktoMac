"""
LinktoMac Menu Bar App
Stub — full implementation coming in Phase 2.
Falls back to the standard webview UI if rumps is not installed.
"""
import sys
import logging

logger = logging.getLogger(__name__)

def main():
    logger.info("Menu bar mode requested")
    try:
        import rumps
        _run_menubar()
    except ImportError:
        logger.warning("rumps not installed — falling back to standard window UI")
        _run_window_fallback()

def _run_window_fallback():
    """Launch the standard webview window when rumps isn't available."""
    import webview
    from pathlib import Path
    from linktomac.backend import LinktoMacBackend
    from linktomac import config

    UI_DIR = config.BASE_DIR / "orchestrator" / "ui"

    import threading, json
    backend = LinktoMacBackend()

    class Api:
        def __init__(self):
            self.window = None
            self.backend = backend
        def set_window(self, w):
            self.window = w
            self.backend.set_callback(self._on_devices)
            self.backend.start_discovery()
        def _on_devices(self, devices):
            if self.window:
                try:
                    self.window.evaluate_js(f"window.updateDiscoveredDevices({json.dumps(devices)})")
                except Exception:
                    pass
        def close_window(self):
            if self.window:
                self.window.destroy()
            import os; os._exit(0)
        def minimize_window(self):
            if self.window:
                self.window.minimize()
        def get_paired_devices(self):
            return self.backend.get_paired_devices()
        def remove_device(self, device_id):
            self.backend.remove_device(device_id); return True
        def launch_mirror(self, ip):
            s, e = self.backend.launch_mirror(ip)
            return {"success": s, "error": e}
        def pair_device(self, ip, pin):
            s, e = self.backend.pair_device(ip, pin)
            return {"success": s, "error": e}
        def launch_mirror_usb(self):
            from linktomac.adb import AdbClient
            if not AdbClient.get_devices():
                return {"success": False, "error": "No USB device connected."}
            s, e = self.backend.launch_mirror("127.0.0.1")
            return {"success": s, "error": e}
        def stop_mirror(self, ip=None):
            self.backend.stop_remote_session(); return True
        def start_remote_session(self):
            def _run():
                result = self.backend.start_remote_session()
                if self.window:
                    self.window.evaluate_js(f"window.onRemoteSessionStarted({json.dumps(result)})")
            threading.Thread(target=_run, daemon=True).start()
            return True
        def stop_remote_session(self):
            self.backend.stop_remote_session(); return True

    api = Api()
    window = webview.create_window(
        "LinktoMac", url=str(UI_DIR / "index.html"),
        js_api=api, width=400, height=600, resizable=False, frameless=False,
        transparent=False, easy_drag=False
    )
    api.set_window(window)
    webview.start()

def _run_menubar():
    """Quiet Premium rumps menu bar implementation."""
    import rumps
    import json
    import threading
    from linktomac.backend import LinktoMacBackend

    backend = LinktoMacBackend()

    class LinktoMacApp(rumps.App):
        def __init__(self):
            super().__init__("LinktoMac", quit_button=None)
            self.backend = backend
            self._refresh_menu()
            self.backend.set_callback(self._on_devices_updated)
            self.backend.start_discovery()

        def _refresh_menu(self):
            items = []
            paired = self.backend.get_paired_devices()

            if paired:
                for dev in paired:
                    name = dev.get("name", dev.get("ip", "Device"))
                    ip = dev.get("ip", "")
                    item = rumps.MenuItem(f"Mirror {name}",
                                          callback=lambda _, i=ip: self._mirror(i))
                    items.append(item)
            else:
                items.append(rumps.MenuItem("No paired devices", callback=None))

            items.append(rumps.separator)
            items.append(rumps.MenuItem("Discover Devices", callback=self._discover))
            items.append(rumps.MenuItem("Remote Session…", callback=self._remote))
            items.append(rumps.separator)
            items.append(rumps.MenuItem("Quit LinktoMac", callback=rumps.quit_application))
            self.menu = items

        def _on_devices_updated(self, devices):
            self._refresh_menu()

        def _mirror(self, ip):
            success, err = self.backend.launch_mirror(ip)
            if not success:
                rumps.alert(f"Mirror failed: {err}")

        def _discover(self, _):
            rumps.notification("LinktoMac", "Scanning…", "Looking for Android devices on your network.")
            self.backend.start_discovery()

        def _remote(self, _):
            result = self.backend.start_remote_session()
            if result.get("success"):
                rumps.alert(f"Remote code:\n{result['code']}")
            else:
                rumps.alert(f"Failed: {result.get('error')}")

    LinktoMacApp().run()
