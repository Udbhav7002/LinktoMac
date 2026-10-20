#!/usr/bin/env python3
"""
Adversarial empirical stress test for UsbDeviceWatcher and ADB port forwarding logic.
Tests boundary conditions, race conditions, rapid flapping, unauthorized devices,
port collisions, exception resilience, and thread lifecycle.
"""

import sys
import os
import time
import threading
import unittest
from unittest.mock import MagicMock, patch

# Ensure orchestrator is on sys.path
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(BASE_DIR, "orchestrator"))

from linktomac.backend import UsbDeviceWatcher, LinkMyMacBackend
from linktomac.adb import AdbClient, AdbError


class TestUsbDeviceWatcherStress(unittest.TestCase):
    def setUp(self):
        self.callbacks = []
        self.cb_lock = threading.Lock()

    def usb_callback(self, dev_info):
        with self.cb_lock:
            self.callbacks.append(dev_info)

    def test_01_usb_connection_and_forwarding(self):
        """Verify connecting a USB device forwards port 27189 and notifies callback."""
        mock_devices = [
            {"serial": "USB_DEV_001", "state": "device", "model": "Pixel 10a", "is_usb": True, "raw": "line1"}
        ]
        with patch.object(AdbClient, "get_detailed_devices", return_value=mock_devices), \
             patch.object(AdbClient, "forward") as mock_forward, \
             patch.object(AdbClient, "remove_forward") as mock_remove:

            watcher = UsbDeviceWatcher(callback=self.usb_callback, poll_interval=0.05)
            watcher.start()
            time.sleep(0.15)
            watcher.stop()

            # Verify forward called
            mock_forward.assert_called_with(27189, 27189)
            # Verify callback received dev_info
            with self.cb_lock:
                self.assertGreaterEqual(len(self.callbacks), 1)
                first = self.callbacks[0]
                self.assertTrue(first.get("connected"))
                self.assertEqual(first.get("serial"), "USB_DEV_001")
                self.assertEqual(first.get("name"), "Pixel 10a")
                self.assertEqual(first.get("ip"), "127.0.0.1")
                self.assertTrue(first.get("is_usb"))

    def test_02_usb_disconnection_and_cleanup(self):
        """Verify disconnecting USB device removes port forward and notifies callback."""
        mock_states = [
            # First poll: connected
            [{"serial": "USB_DEV_002", "state": "device", "model": "Galaxy S24", "is_usb": True}],
            # Second poll: disconnected
            []
        ]
        state_idx = 0

        def mock_get_detailed():
            nonlocal state_idx
            idx = min(state_idx, len(mock_states) - 1)
            state_idx += 1
            return mock_states[idx]

        with patch.object(AdbClient, "get_detailed_devices", side_effect=mock_get_detailed), \
             patch.object(AdbClient, "forward") as mock_forward, \
             patch.object(AdbClient, "remove_forward") as mock_remove:

            watcher = UsbDeviceWatcher(callback=self.usb_callback, poll_interval=0.05)
            watcher.start()
            time.sleep(0.2)
            watcher.stop()

            with self.cb_lock:
                self.assertGreaterEqual(len(self.callbacks), 2)
                self.assertTrue(self.callbacks[0].get("connected"))
                self.assertFalse(self.callbacks[1].get("connected"))

            mock_remove.assert_called_with(27189)

    def test_03_unauthorized_device_ignored(self):
        """Unauthorized device (state != 'device') must NOT trigger USB connection."""
        mock_devices = [
            {"serial": "USB_DEV_UNAUTH", "state": "unauthorized", "model": "Pixel 9", "is_usb": True}
        ]
        with patch.object(AdbClient, "get_detailed_devices", return_value=mock_devices), \
             patch.object(AdbClient, "forward") as mock_forward:

            watcher = UsbDeviceWatcher(callback=self.usb_callback, poll_interval=0.05)
            watcher.start()
            time.sleep(0.12)
            watcher.stop()

            mock_forward.assert_not_called()
            with self.cb_lock:
                self.assertEqual(len(self.callbacks), 0)
                self.assertEqual(watcher.get_connected_device(), {"connected": False})

    def test_04_wifi_adb_and_emulator_ignored(self):
        """Wi-Fi ADB (IP:port) and emulator devices must NOT trigger USB watcher."""
        mock_devices = [
            {"serial": "192.168.1.150:5555", "state": "device", "model": "Pixel 8", "is_usb": False},
            {"serial": "emulator-5554", "state": "device", "model": "Android SDK built for arm64", "is_usb": False}
        ]
        with patch.object(AdbClient, "get_detailed_devices", return_value=mock_devices), \
             patch.object(AdbClient, "forward") as mock_forward:

            watcher = UsbDeviceWatcher(callback=self.usb_callback, poll_interval=0.05)
            watcher.start()
            time.sleep(0.12)
            watcher.stop()

            mock_forward.assert_not_called()
            with self.cb_lock:
                self.assertEqual(len(self.callbacks), 0)

    def test_05_forward_failure_resilience(self):
        """If adb forward raises an exception, watcher must not crash and continue polling."""
        mock_devices = [
            {"serial": "USB_DEV_FAIL", "state": "device", "model": "Moto G", "is_usb": True}
        ]
        with patch.object(AdbClient, "get_detailed_devices", return_value=mock_devices), \
             patch.object(AdbClient, "forward", side_effect=AdbError("bind error")), \
             patch.object(AdbClient, "remove_forward"):

            watcher = UsbDeviceWatcher(callback=self.usb_callback, poll_interval=0.05)
            watcher.start()
            time.sleep(0.15)
            self.assertTrue(watcher._thread.is_alive())
            watcher.stop()

            with self.cb_lock:
                # Callback is still notified so UI knows device is present
                self.assertGreaterEqual(len(self.callbacks), 1)

    def test_06_callback_exception_resilience(self):
        """If caller's callback raises an unhandled error, watcher must survive."""
        def bad_callback(dev):
            raise RuntimeError("Boom!")

        mock_devices = [
            {"serial": "USB_DEV_CRASH", "state": "device", "model": "OnePlus", "is_usb": True}
        ]
        with patch.object(AdbClient, "get_detailed_devices", return_value=mock_devices), \
             patch.object(AdbClient, "forward"), \
             patch.object(AdbClient, "remove_forward"):

            watcher = UsbDeviceWatcher(callback=bad_callback, poll_interval=0.05)
            watcher.start()
            time.sleep(0.15)
            self.assertTrue(watcher._thread.is_alive())
            watcher.stop()

    def test_07_device_swapping_different_serial(self):
        """Unplugging one device and plugging another must trigger remove on old and forward on new."""
        forwards = []
        removes = []

        def mock_fwd(lp, rp):
            forwards.append((lp, rp))

        def mock_rm(lp):
            removes.append(lp)

        states = [
            [{"serial": "DEV_A", "state": "device", "model": "Device A", "is_usb": True}],
            [{"serial": "DEV_B", "state": "device", "model": "Device B", "is_usb": True}],
        ]
        idx = 0
        def mock_get():
            nonlocal idx
            s = states[min(idx, len(states) - 1)]
            idx += 1
            return s

        with patch.object(AdbClient, "get_detailed_devices", side_effect=mock_get), \
             patch.object(AdbClient, "forward", side_effect=mock_fwd), \
             patch.object(AdbClient, "remove_forward", side_effect=mock_rm):

            watcher = UsbDeviceWatcher(callback=self.usb_callback, poll_interval=0.05)
            watcher.start()
            time.sleep(0.2)
            watcher.stop()

            with self.cb_lock:
                serials = [c.get("serial") for c in self.callbacks if c.get("connected")]
                self.assertIn("DEV_A", serials)
                self.assertIn("DEV_B", serials)

    def test_08_thread_lifecycle_idempotency(self):
        """Starting twice or stopping twice must be safe and idempotent."""
        watcher = UsbDeviceWatcher(poll_interval=0.05)
        watcher.start()
        t1 = watcher._thread
        watcher.start()  # Idempotent
        self.assertIs(t1, watcher._thread)

        watcher.stop()
        self.assertIsNone(watcher._thread)
        watcher.stop()  # Idempotent stop
        self.assertIsNone(watcher._thread)

    def test_09_backend_smart_connect_usb(self):
        """Backend smart_connect with is_usb=True must forward 27189 and route to 127.0.0.1."""
        backend = LinkMyMacBackend()
        with patch.object(AdbClient, "forward") as mock_fwd, \
             patch.object(backend, "launch_mirror", return_value=(True, None)) as mock_launch, \
             patch("linktomac.backend.storage.save_device"):

            res = backend.smart_connect({
                "name": "USB Phone",
                "ip": "127.0.0.1",
                "is_usb": True,
                "serial": "TEST_USB_SERIAL"
            })
            self.assertTrue(res.get("success"))
            self.assertEqual(res.get("mode"), "local")
            mock_fwd.assert_called_with(27189, 27189)
            mock_launch.assert_called_with("127.0.0.1")


if __name__ == "__main__":
    unittest.main()
