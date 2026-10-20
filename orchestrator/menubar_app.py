#!/usr/bin/env python3
# orchestrator/menubar_app.py
# macOS menu bar app for LinktoMac using rumps

import sys
import os
import json
import threading
import subprocess
import logging
from pathlib import Path

# Ensure the orchestrator package is on the path
ORCHESTRATOR_DIR = Path(__file__).parent
sys.path.insert(0, str(ORCHESTRATOR_DIR))

import rumps
from linktomac.backend import LinktoMacBackend

# Configure logging
LOG_DIR = Path.home() / ".linktomax"
LOG_DIR.mkdir(exist_ok=True)
LOG_FILE = LOG_DIR / "app.log"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class LinktoMacMenuBarApp(rumps.App):
    """Menu bar application for LinktoMac"""

    def __init__(self):
        # App name shown in menu bar (we'll use an emoji icon)
        super().__init__("LinktoMac", quit_button=None)
        
        self.backend = LinktoMacBackend()
        self._discovered_devices = {}
        self._paired_devices_cache = []
        
        # Build initial menu
        self._build_menu()
        
        # Set up backend callback for device discovery
        self.backend.set_callback(self._on_devices_updated)
        self.backend.start_discovery()
        
        # Start mirror process monitor
        self._start_mirror_monitor()
        
        logger.info("LinktoMac menu bar app started")

    def _build_menu(self):
        """Build the menu structure"""
        self.menu.clear()
        
        # Mirroring sessions section
        self._mirror_menu = rumps.MenuItem("Mirroring Sessions")
        self.menu.add(self._mirror_menu)
        
        self.menu.add(rumps.separator)
        
        # Paired devices submenu
        self._paired_submenu = rumps.MenuItem("Paired Devices")
        self.menu.add(self._paired_submenu)
        
        self.menu.add(rumps.separator)
        
        # Actions
        self.menu.add(rumps.MenuItem(
            "Discover Devices", 
            callback=self._on_discover_clicked
        ))
        
        self.menu.add(rumps.MenuItem(
            "Remote Session...", 
            callback=self._on_remote_session_clicked
        ))
        
        self.menu.add(rumps.MenuItem(
            "Preferences...", 
            callback=self._on_preferences_clicked
        ))
        
        self.menu.add(rumps.separator)
        
        self.menu.add(rumps.MenuItem(
            "Quit LinktoMac", 
            callback=self._on_quit_clicked
        ))
        
        # Update paired devices in menu
        self._refresh_paired_devices_menu()

    def _on_devices_updated(self, devices):
        """Callback from backend when mDNS discovers new devices"""
        logger.info(f"Discovered {len(devices)} device(s)")
        self._discovered_devices = {d["ip"]: d for d in devices}
        self._update_mirror_menu()

    def _update_mirror_menu(self):
        """Update the mirroring sessions menu with discovered + paired devices"""
        self._mirror_menu.clear()
        
        # Add currently running mirrors first
        running_count = 0
        with self.backend._mirrors_lock:
            for ip, proc in self.backend.running_mirrors.items():
                if ip not in ("ngrok", "mirror") and proc.poll() is None:
                    device = self._discovered_devices.get(ip)
                    name = device["name"] if device else ip
                    item = rumps.MenuItem(f"{name} ({ip}) - Active")
                    item.set_callback(lambda _: None)  # Just show status
                    self._mirror_menu.add(item)
                    running_count += 1
        
        # Add discoverable devices as "Start Mirror" options
        for ip, device in self._discovered_devices.items():
            is_running = False
            with self.backend._mirrors_lock:
                if ip in self.backend.running_mirrors and self.backend.running_mirrors[ip].poll() is None:
                    is_running = True
            
            if not is_running:
                item = rumps.MenuItem(f"Mirror: {device['name']} ({ip})")
                item.set_callback(lambda _, ip=ip: self._start_mirror(ip))
                self._mirror_menu.add(item)
        
        if running_count == 0 and len(self._discovered_devices) == 0:
            item = rumps.MenuItem("No devices found on Wi-Fi")
            item.set_callback(lambda _: None)
            self._mirror_menu.add(item)

    def _refresh_paired_devices_menu(self):
        """Refresh the paired devices submenu"""
        self._paired_submenu.clear()
        
        devices = self.backend.get_paired_devices()
        self._paired_devices_cache = devices
        
        if not devices:
            item = rumps.MenuItem("No paired devices")
            item.set_callback(lambda _: None)
            self._paired_submenu.add(item)
        else:
            for device in devices:
                item = rumps.MenuItem(f"📱 {device['name']} ({device['ip']})")
                submenu = rumps.MenuItem("")
                submenu.add(rumps.MenuItem(
                    "Mirror", 
                    callback=lambda _, ip=device['ip']: self._start_mirror(ip)
                ))
                submenu.add(rumps.MenuItem(
                    "Remove", 
                    callback=lambda _, did=device['device_id']: self._remove_device(did)
                ))
                item.set_submenu(submenu)
                self._paired_submenu.add(item)

    def _start_mirror(self, ip):
        """Start mirroring for a device IP"""
        logger.info(f"Starting mirror for {ip}")
        rumps.notification("LinktoMac", f"Starting mirror for {ip}...", "")
        
        success, err = self.backend.launch_mirror(ip)
        if success:
            rumps.notification("LinktoMac", f"Mirroring started for {ip}", "")
            # The monitor will update the menu
        else:
            rumps.alert("Failed to start mirror", f"Error: {err}")
            logger.error(f"Mirror launch failed for {ip}: {err}")

    def _remove_device(self, device_id):
        """Remove a paired device"""
        self.backend.remove_device(device_id)
        rumps.notification("LinktoMac", "Device removed", "")
        self._refresh_paired_devices_menu()

    def _on_discover_clicked(self, _):
        """Trigger mDNS discovery"""
        logger.info("Manual discovery triggered")
        rumps.notification("LinktoMac", "Scanning for devices...", "")
        # Backend already runs discovery continuously; just refresh UI
        self._update_mirror_menu()

    def _on_remote_session_clicked(self, _):
        """Start a Pinggy remote tunnel"""
        logger.info("Starting remote session")
        
        def _launch():
            result = self.backend.start_remote_session()
            if result.get("success"):
                code = result.get("code", "unknown")
                rumps.alert(
                    "Remote Session Active",
                    f"Share this code with the Android app:\n\n{code}",
                    ok="Copy Code",
                    cancel="OK"
                )
                # Copy to clipboard if OK clicked
                import pyperclip
                pyperclip.copy(code)
            else:
                error = result.get("error", "Unknown error")
                rumps.alert("Failed to start remote session", f"Error: {error}")
        
        threading.Thread(target=_launch, daemon=True).start()
        rumps.notification("LinktoMac", "Starting remote tunnel...", "")

    def _on_preferences_clicked(self, _):
        """Show preferences dialog"""
        # Get ADB status
        from linktomac.adb import AdbClient
        adb_path = AdbClient._find_adb_path() if hasattr(AdbClient, '_find_adb_path') else "unknown"
        usb_devices = AdbClient.get_devices()
        
        msg = (
            f"LinktoMac Preferences\n\n"
            f"ADB Path: {adb_path}\n"
            f"USB Devices Connected: {len(usb_devices)}\n"
            f"Log File: {LOG_FILE}\n\n"
            f"Version: 1.0.0"
        )
        rumps.alert("Preferences", msg)

    def _on_quit_clicked(self, _):
        """Quit the app"""
        logger.info("Quitting LinktoMac")
        self.backend.stop_remote_session()
        # Stop all running mirrors
        with self.backend._mirrors_lock:
            import signal
            for ip, proc in self.backend.running_mirrors.items():
                if proc.poll() is None:
                    try:
                        os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
                    except Exception:
                        proc.terminate()
        rumps.quit_application()

    def _start_mirror_monitor(self):
        """Background thread to monitor mirror processes"""
        def _monitor():
            while True:
                import time
                time.sleep(2)
                if not self._mirror_menu:
                    continue
                dead_ips = []
                with self.backend._mirrors_lock:
                    for ip, proc in list(self.backend.running_mirrors.items()):
                        if ip in ("ngrok", "mirror"):
                            continue
                        if proc.poll() is not None:
                            dead_ips.append(ip)
                for ip in dead_ips:
                    logger.info(f"Mirror process for {ip} exited")
                    with self.backend._mirrors_lock:
                        self.backend.running_mirrors.pop(ip, None)
                if dead_ips:
                    # Update menu on main thread
                    rumps._send_to_main_thread(self._update_mirror_menu)
        
        threading.Thread(target=_monitor, daemon=True, name="mirror-monitor").start()


def main():
    app = LinktoMacMenuBarApp()
    app.run()


if __name__ == "__main__":
    main()
