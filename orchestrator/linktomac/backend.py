import logging
import threading
import subprocess
import time
import requests
import json
import os
import sys
import tempfile
import signal
import select
from . import storage
from . import pairing
from . import config

logger = logging.getLogger(__name__)

class LinktoMacBackend:
    def __init__(self):
        self.listener = None
        # Track running mirrors
        self.running_mirrors = {}
        self._mirrors_lock = threading.Lock()
        self.discovered_devices = {}
        self.mirror_log_file = None

    def set_callback(self, cb):
        """Callback gets triggered when the discovered devices list changes"""
        self.on_devices_updated_cb = cb

    def start_discovery(self):
        """Starts listening for UDP broadcasts from the phone app"""
        def on_found(ip, name, device_id):
            updated = False
            if ip not in self.discovered_devices:
                updated = True
            
            self.discovered_devices[ip] = {
                "ip": ip,
                "name": name,
                "device_id": device_id,
                "latency": 0 # Unknown initially
            }
            
            if updated and self.on_devices_updated_cb:
                self.on_devices_updated_cb(list(self.discovered_devices.values()))

        self.listener = pairing.DiscoveryListener(on_found)
        self.listener.start()
        logger.info("mDNS Discovery started")

    def stop_discovery(self):
        if self.listener:
            self.listener.stop()
            self.listener = None

    def _get_adb_path(self):
        import os
        paths = [
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb",
            os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
            "adb"
        ]
        for p in paths:
            if os.path.exists(p) or p == "adb":
                try:
                    subprocess.check_output([p, "version"], stdin=subprocess.DEVNULL, stderr=subprocess.STDOUT)
                    return p
                except Exception:
                    pass
        return "adb"

    def _get_ip_via_adb(self):
        adb_path = self._get_adb_path()
        try:
            output = subprocess.check_output([adb_path, "shell", "ip", "route"], stdin=subprocess.DEVNULL, stderr=subprocess.STDOUT).decode('utf-8')
            for line in output.split('\n'):
                if 'src' in line and ('wlan' in line or 'wlan0' in line):
                    parts = line.split('src')
                    if len(parts) > 1:
                        ip = parts[1].strip().split(' ')[0]
                        return ip
        except Exception as e:
            logger.error(f"ADB IP resolution failed: {e}")
        return None

    def pair_device(self, ip, pin, pc_name="My Mac"):
        """
        Pairs with the device using the PIN. 
        Returns (success: bool, error: str)
        """
        success = False
        err = ""
        used_usb_fallback = False
        
        if ip:
            # Try Wi-Fi first
            success, err = pairing.PairingClient.pair(ip, pin, my_name=pc_name)
            
        if not success:
            logger.info(f"Wi-Fi pairing failed ({err}), attempting true USB fallback via ADB...")
            adb_path = self._get_adb_path()
            try:
                # Forward the port securely over the USB cable explicitly (-d means only USB devices)
                subprocess.check_output([adb_path, "-d", "forward", "tcp:27192", "tcp:27192"], stdin=subprocess.DEVNULL, stderr=subprocess.STDOUT)
                
                # Pair via localhost which is now forwarded directly to the phone
                success, err = pairing.PairingClient.pair("127.0.0.1", pin, my_name=pc_name)
                
                if success:
                    used_usb_fallback = True
                    # If we used USB, we might not have the phone's Wi-Fi IP. Try to get it anyway for the mirror, 
                    # or just rely on ADB for the mirror too.
                    ip = self._get_ip_via_adb() or "127.0.0.1"
                
                # Remove the forward so it doesn't leak
                subprocess.check_output([adb_path, "-d", "forward", "--remove", "tcp:27192"], stdin=subprocess.DEVNULL, stderr=subprocess.STDOUT)
            except Exception as e:
                err = f"USB fallback also failed: {e}"

        if success:
            device = self.discovered_devices.get(ip)
            if device and not used_usb_fallback:
                storage.save_device(ip, device["name"], device["device_id"])
            else:
                # Try to grab name via adb
                try:
                    adb_path = self._get_adb_path()
                    name = subprocess.check_output([adb_path, "shell", "getprop", "ro.product.model"], stdin=subprocess.DEVNULL, stderr=subprocess.STDOUT).decode('utf-8').strip()
                except Exception:
                    name = "USB Wireless Device"
                storage.save_device(ip, name, f"usb_{ip}")
        return success, err

    def get_paired_devices(self):
        """Returns a list of saved devices"""
        return storage.get_devices()

    def remove_device(self, device_id):
        storage.remove_device(device_id)

    def launch_mirror(self, ip):
        """
        Launches the mirror orchestrator using the given IP
        """
        import sys
        import os
        logger.info(f"Launching mirror for {ip}...")
        # If frozen (PyInstaller), sys.executable is our bundled app. 
        # We pass --cli so ui_app.py knows to route to cli.py instead of opening a window.
        if ip == "127.0.0.1":
            # Native USB mode
            if getattr(sys, 'frozen', False):
                cmd = [sys.executable, "--cli"]
            else:
                cmd = [sys.executable, "-m", "linktomac.cli"]
        else:
            # Wireless mode
            if getattr(sys, 'frozen', False):
                cmd = [sys.executable, "--cli", "--wireless", ip]
            else:
                cmd = [sys.executable, "-m", "linktomac.cli", "--wireless", ip]
        
        try:
            # Use setsid to create a new process group so we can kill the entire tree
            # (the CLI process AND the mirror_client grandchild) with one signal.
            proc = subprocess.Popen(
                cmd,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                preexec_fn=os.setsid
            )
            with self._mirrors_lock:
                self.running_mirrors[ip] = proc
            # Don't block — mirror runs in background process group
            # Caller can stop via stop_remote_session()
            return True, ""
        except Exception as e:
            with self._mirrors_lock:
                self.running_mirrors.pop(ip, None)
            return False, str(e)

    def start_remote_session(self):
        import os
        import subprocess
        import time
        import threading
        import select

        logger.info("Starting pinggy tunnel...")
        pinggy_proc = None
        mirror_proc = None
        mirror_log_file = None
        try:
            # We use Pinggy via SSH instead of ngrok to avoid credit card/account requirements!
            cmd = ["ssh", "-tt", "-p", "443", "-R0:localhost:27189", "tcp@a.pinggy.io", "-o", "StrictHostKeyChecking=no"]
            
            pinggy_proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                preexec_fn=os.setsid
            )
            self.running_mirrors["ngrok"] = pinggy_proc
            
            public_url = None
            output_lines = []
            
            def reader_thread():
                try:
                    for line in iter(pinggy_proc.stdout.readline, ''):
                        if not line: break
                        logger.debug(f"Pinggy out: {repr(line)}")
                        output_lines.append(line)
                except Exception:
                    pass
                    
            t = threading.Thread(target=reader_thread, daemon=True)
            t.start()

            start_time = time.time()
            while time.time() - start_time < 30:
                for line in list(output_lines): # copy list to iterate safely
                    if "tcp://" in line:
                        public_url = line.strip()
                        idx = public_url.find("tcp://")
                        if idx != -1:
                            public_url = public_url[idx:].replace("tcp://", "")
                            public_url = public_url.split('\x1b')[0].strip()
                        break
                
                if public_url:
                    break
                    
                if pinggy_proc.poll() is not None:
                    break
                time.sleep(0.5)
            
            if not public_url:
                logger.error("Pinggy failed to establish a tunnel within 10 seconds.")
                self._kill_pg(pinggy_proc)
                self.running_mirrors.pop("ngrok", None)
                return {"success": False, "error": "Pinggy failed to establish a tunnel."}
            
            logger.info(f"Pinggy tunnel successfully established: {public_url}")
            
            # Now start the C++ mirror client (which will bind and listen on 27189)
            import sys
            client_path = config.BASE_DIR / "native-client/build/mirror_client"
            if getattr(sys, 'frozen', False):
                if sys.platform == 'darwin':
                    from pathlib import Path
                    client_path = Path(sys.executable).parent.parent / "Frameworks" / "mirror_client"
                else:
                    from pathlib import Path
                    client_path = Path(sys.executable).parent / "mirror_client"

            cmd = [str(client_path), "--video-port", "27189"]
            log_path = os.path.join(tempfile.gettempdir(), "LinktoMac_Mirror.log")
            mirror_log_file = open(log_path, "w")
            
            mirror_proc = subprocess.Popen(
                cmd,
                stdin=subprocess.DEVNULL,
                stdout=mirror_log_file,
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid
            )
            with self._mirrors_lock:
                self.running_mirrors["mirror"] = mirror_proc
            
            # Store log file for cleanup
            self.mirror_log_file = mirror_log_file
            
            return {"success": True, "code": public_url}
            
        except Exception as e:
            logger.error(f"start_remote_session failed: {e}")
            # Cleanup on error
            if mirror_proc:
                self._kill_pg(mirror_proc)
            if pinggy_proc:
                self._kill_pg(pinggy_proc)
            if mirror_log_file:
                mirror_log_file.close()
            with self._mirrors_lock:
                self.running_mirrors.pop("ngrok", None)
                self.running_mirrors.pop("mirror", None)
            return {"success": False, "error": str(e)}
    
    def _kill_pg(self, proc):
        if proc and proc.poll() is None:
            try:
                os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
            except:
                pass
    
    def stop_remote_session(self):
        import signal
        with self._mirrors_lock:
            for key in ["ngrok", "mirror"]:
                proc = self.running_mirrors.get(key)
                if proc:
                    self._kill_pg(proc)
                self.running_mirrors.pop(key, None)
        # Close log file
        if self.mirror_log_file:
            try:
                self.mirror_log_file.close()
            except:
                pass
            self.mirror_log_file = None
        return True
