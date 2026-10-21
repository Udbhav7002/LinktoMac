import logging
import threading
import subprocess
import time
import os
import sys
import shutil
import tempfile
import signal
import socket
from pathlib import Path
from . import storage
from . import pairing
from . import config

logger = logging.getLogger(__name__)


def resolve_mirror_client_path() -> Path:
    """
    Search candidates for the mirror_client binary in priority order:
      1. Path(sys.executable).parent / "mirror_client" (Contents/MacOS/mirror_client)
      2. Path(sys.executable).parent.parent / "Frameworks" / "mirror_client" (Contents/Frameworks/mirror_client)
      3. Path(sys.executable).parent.parent / "Resources" / "mirror_client"
      4. Path(sys._MEIPASS) / "mirror_client" (if frozen)
      5. config.BASE_DIR / "native-client/build/mirror_client"
      6. shutil.which("mirror_client")
    Candidate is accepted if it exists, is a file, and is executable.
    """
    candidates = []
    if getattr(sys, 'frozen', False):
        exe = Path(sys.executable)
        candidates.append(exe.parent / "mirror_client")
        candidates.append(exe.parent.parent / "Frameworks" / "mirror_client")
        candidates.append(exe.parent.parent / "Resources" / "mirror_client")
        if hasattr(sys, '_MEIPASS'):
            candidates.append(Path(sys._MEIPASS) / "mirror_client")

    candidates.append(config.BASE_DIR / "native-client/build/mirror_client")
    candidates.append(config.BASE_DIR / "mirror_client")

    for p in candidates:
        try:
            if p.is_file() and os.access(p, os.X_OK):
                return p
        except Exception:
            continue

    which_name = shutil.which("mirror_client")
    if which_name:
        p = Path(which_name)
        try:
            if p.is_file() and os.access(p, os.X_OK):
                return p
        except Exception:
            pass

    checked_str = [str(c) for c in candidates]
    raise FileNotFoundError(f"mirror_client binary not found. Checked: {checked_str}")


class UsbDeviceWatcher:
    """
    Background daemon thread polling `adb devices -l` for connected USB devices.
    When a physical USB device is connected:
      1. Automatically runs `adb forward tcp:27189 tcp:27189`.
      2. Invokes on_usb_changed({"connected": True, "serial": serial, "name": model_name, "ip": "127.0.0.1", "is_usb": True}).
    When the USB device is disconnected:
      1. Runs `adb forward --remove tcp:27189`.
      2. Invokes on_usb_changed({"connected": False}).
    """
    def __init__(self, callback=None, poll_interval=1.5):
        self.callback = callback
        self.poll_interval = poll_interval
        self._stop_event = threading.Event()
        self._thread = None
        self.current_device = None
        self._lock = threading.Lock()

    def start(self):
        if self._thread and self._thread.is_alive():
            return
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run, daemon=True, name="UsbDeviceWatcher")
        self._thread.start()

    def stop(self):
        self._stop_event.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2.0)
        self._thread = None
        if self.current_device:
            try:
                from .adb import AdbClient
                AdbClient(serial=self.current_device.get("serial")).remove_forward(27189)
            except Exception:
                pass
            with self._lock:
                self.current_device = None

    def get_connected_device(self):
        with self._lock:
            return dict(self.current_device) if self.current_device else {"connected": False}

    def _run(self):
        from .adb import AdbClient
        while not self._stop_event.is_set():
            try:
                devices = AdbClient.get_detailed_devices()
                usb_dev = None
                for d in devices:
                    if d.get("state") == "device" and d.get("is_usb"):
                        usb_dev = d
                        break

                if usb_dev:
                    serial = usb_dev["serial"]
                    model = usb_dev["model"]
                    prev_serial = self.current_device.get("serial") if self.current_device else None

                    if not self.current_device or prev_serial != serial:
                        try:
                            AdbClient(serial=serial).forward(27189, 27189)
                            logger.info(f"Auto-forwarded port 27189 for USB device {serial} ({model})")
                        except Exception as e:
                            logger.warning(f"Failed to forward port 27189 for {serial}: {e}")

                        dev_info = {
                            "connected": True,
                            "serial": serial,
                            "name": model,
                            "ip": "127.0.0.1",
                            "is_usb": True
                        }
                        with self._lock:
                            self.current_device = dev_info
                        if self.callback:
                            try:
                                self.callback(dev_info)
                            except Exception as e:
                                logger.debug(f"Error in UsbDeviceWatcher callback: {e}")
                else:
                    if self.current_device and self.current_device.get("connected"):
                        prev_serial = self.current_device.get("serial")
                        try:
                            AdbClient(serial=prev_serial).remove_forward(27189)
                            logger.info(f"Removed port forward 27189 after disconnect of {prev_serial}")
                        except Exception:
                            pass

                        dev_info = {"connected": False}
                        with self._lock:
                            self.current_device = None
                        if self.callback:
                            try:
                                self.callback(dev_info)
                            except Exception as e:
                                logger.debug(f"Error in UsbDeviceWatcher callback: {e}")
            except Exception as e:
                logger.debug(f"Exception in UsbDeviceWatcher loop: {e}")

            self._stop_event.wait(self.poll_interval)


class LinkMyMacBackend:
    def __init__(self):
        self.listener = None
        self.usb_watcher = None
        self.running_mirrors = {}
        self._mirrors_lock = threading.Lock()
        self.discovered_devices = {}
        self._devices_lock = threading.Lock()
        self.mirror_log_file = None
        self.on_devices_updated_cb = None
        self.on_usb_changed_cb = None
        self.on_mirror_exit_cb = None
        self._user_stopped = False
        self._cancel_remote = threading.Event()
        self.local_probe_timeout = 2.5

    def set_callback(self, cb):
        self.on_devices_updated_cb = cb

    def set_usb_callback(self, cb):
        self.on_usb_changed_cb = cb
        if self.usb_watcher:
            self.usb_watcher.callback = cb

    def get_connected_usb_device(self):
        if self.usb_watcher:
            return self.usb_watcher.get_connected_device()
        return {"connected": False}

    def set_mirror_exit_callback(self, cb):
        self.on_mirror_exit_cb = cb

    def _spawn_process_watcher(self, proc, target_name):
        def _watch():
            try:
                exit_code = proc.wait()
            except Exception:
                exit_code = -1
            with self._mirrors_lock:
                for k, v in list(self.running_mirrors.items()):
                    if v == proc:
                        self.running_mirrors.pop(k, None)
                        break
            if not self._user_stopped and self.on_mirror_exit_cb:
                try:
                    self.on_mirror_exit_cb(exit_code, target_name)
                except Exception as e:
                    logger.debug(f"Error in on_mirror_exit_cb: {e}")

        t = threading.Thread(target=_watch, daemon=True)
        t.start()

    def start_discovery(self):
        def on_found(ip, name, device_id, is_tls=False):
            with self._devices_lock:
                updated = False
                existing = self.discovered_devices.get(ip)
                if not existing or existing.get("name") != name or existing.get("is_tls") != is_tls:
                    updated = True

                self.discovered_devices[ip] = {
                    "ip": ip,
                    "name": name,
                    "device_id": device_id,
                    "is_tls": is_tls
                }
                devices_snapshot = list(self.discovered_devices.values())

            if updated and self.on_devices_updated_cb:
                self.on_devices_updated_cb(devices_snapshot)

        self.listener = pairing.DiscoveryListener(on_found)
        self.listener.start()

        def _on_usb_changed(usb_info):
            if self.on_usb_changed_cb:
                self.on_usb_changed_cb(usb_info)

        if not self.usb_watcher:
            self.usb_watcher = UsbDeviceWatcher(callback=_on_usb_changed)
        else:
            self.usb_watcher.callback = _on_usb_changed
        self.usb_watcher.start()
        logger.info("Discovery started")

    def stop_discovery(self):
        if self.usb_watcher:
            self.usb_watcher.stop()
            self.usb_watcher = None
        if self.listener:
            self.listener.stop()
            self.listener = None

    def get_discovered_devices(self):
        with self._devices_lock:
            return list(self.discovered_devices.values())

    def get_paired_devices(self):
        return storage.get_devices()

    def remove_device(self, device_id):
        storage.remove_device(device_id)

    def get_mac_local_ip(self, target_ip=""):
        s = None
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            dest = target_ip if (target_ip and target_ip not in ("127.0.0.1", "localhost", "0.0.0.0")) else "8.8.8.8"
            s.connect((dest, 80))
            ip = s.getsockname()[0]
            return str(ip)
        except Exception:
            try:
                return socket.gethostbyname(socket.gethostname())
            except Exception:
                return "127.0.0.1"
        finally:
            if s:
                try:
                    s.close()
                except Exception:
                    pass

    def _get_mac_local_subnets(self):
        subnets = set()
        s = None
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            local_ip = s.getsockname()[0]
            parts = local_ip.split(".")
            if len(parts) == 4:
                subnets.add(".".join(parts[:3]))
        except Exception:
            pass
        finally:
            if s:
                try:
                    s.close()
                except Exception:
                    pass
        return subnets

    def _is_same_subnet(self, ip: str) -> bool:
        try:
            parts = ip.split(".")
            if len(parts) != 4:
                return False
            prefix = ".".join(parts[:3])
            mac_subnets = self._get_mac_local_subnets()
            if not mac_subnets:
                return True
            return prefix in mac_subnets
        except Exception:
            return False

    def smart_connect(self, device_data, uid=None):
        """
        1. If on the same Wi-Fi subnet (or mDNS discovered), launch mirror_client directly.
        2. If on a different Wi-Fi network / 5G, or direct launch fails, seamlessly negotiate the Pinggy remote relay tunnel.
        """
        if isinstance(device_data, str):
            device_data = {"ip": device_data, "name": "Android Device"}
        elif not isinstance(device_data, dict):
            device_data = {}

        device_name = device_data.get("name") or "Android Device"
        ip = device_data.get("ip") or ""

        self.stop_remote_session()
        self.stop_mirror()
        self._cancel_remote.clear()

        if ip and ip != "0.0.0.0":
            with self._devices_lock:
                is_mdns_discovered = (ip in self.discovered_devices)

            same_subnet = (
                is_mdns_discovered
                or (ip in ("127.0.0.1", "localhost"))
                or device_data.get("is_usb")
                or (ip != "127.0.0.1" and self._is_same_subnet(ip))
            )
            if same_subnet:
                logger.info(f"Direct local subnet detected for {device_name} ({ip}). Launching mirror_client directly.")
                if device_data.get("is_usb") or ip in ("127.0.0.1", "localhost"):
                    try:
                        from .adb import AdbClient
                        serial = device_data.get("serial")
                        AdbClient(serial=serial).forward(27189, 27189)
                    except Exception as e:
                        logger.debug(f"Adb forward before USB launch: {e}")
                success, err = self.launch_mirror(ip)
                if success:
                    try:
                        storage.save_device(ip, device_name, device_data.get("device_id") or f"wifi_{ip}")
                    except Exception:
                        pass
                    mac_ip = self.get_mac_local_ip(ip)
                    return {"success": True, "mode": "local", "name": device_name, "mac_ip": mac_ip}
                if self._cancel_remote.is_set():
                    return {"success": False, "error": "Connection cancelled."}
                if device_data.get("is_usb"):
                    return {"success": False, "error": f"USB mirror launch failed: {err}"}
                logger.info(f"Direct mirror_client launch failed for {ip}: {err}. Falling back to remote relay tunnel...")

        if self._cancel_remote.is_set():
            return {"success": False, "error": "Connection cancelled."}

        logger.info(f"Local Wi-Fi unreachable for {device_name}. Seamlessly negotiating remote relay tunnel...")
        relay_res = self.start_remote_session()
        if relay_res.get("success"):
            if uid:
                self._publish_session_to_user(uid, relay_res)
            code = relay_res.get("code", "")
            host = ""
            port = 0
            if "video" in relay_res and isinstance(relay_res["video"], dict):
                host = relay_res["video"].get("host", "")
                port = relay_res["video"].get("port", 0)
            elif ":" in code:
                parts = code.split(":")
                host = parts[0].strip()
                try:
                    port = int(parts[1].strip())
                except ValueError:
                    port = 0
            return {
                "success": True,
                "mode": "remote",
                "name": device_name,
                "session_code": code,
                "host": host,
                "port": port,
                "video": relay_res.get("video"),
                "control": relay_res.get("control"),
                "token": relay_res.get("token"),
                "session_id": relay_res.get("session_id")
            }

        return {
            "success": False,
            "error": "Device unreachable. Please ensure LinktoMac is open on your Android phone and connected to internet."
        }

    def launch_mirror(self, ip):
        logger.info(f"Launching mirror client session for {ip}...")
        self.stop_mirror(ip)
        self._cancel_remote.clear()

        try:
            client_path = resolve_mirror_client_path()
        except FileNotFoundError as e:
            return False, str(e)

        cmd = [str(client_path), "--connect", str(ip), "--video-port", "27189"]
        repo_root = str(Path(__file__).resolve().parent.parent)
        env = dict(os.environ, PYTHONPATH=repo_root)

        try:
            self._user_stopped = False
            proc = subprocess.Popen(
                cmd,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                preexec_fn=os.setsid,
                cwd=repo_root,
                env=env
            )
            with self._mirrors_lock:
                self.running_mirrors[ip] = proc

            time.sleep(0.35)
            if proc.poll() is not None:
                err_code = proc.returncode
                with self._mirrors_lock:
                    self.running_mirrors.pop(ip, None)
                return False, f"Mirror client exited immediately with code {err_code}"

            self._spawn_process_watcher(proc, ip)
            return True, ""
        except Exception as e:
            with self._mirrors_lock:
                self.running_mirrors.pop(ip, None)
            return False, str(e)

    def start_remote_session(self):
        """
        Open TWO Pinggy reverse tunnels in a single SSH session:
          -R0:localhost:27189   (video)
          -R0:localhost:54921   (control)
        Parse both `tcp://host:port` lines from stdout, write both to Firestore,
        and launch mirror_client in listen mode.
        """
        import subprocess, threading, os, time, tempfile, secrets
        pinggy_proc = None
        mirror_proc = None
        mirror_log_file = None
        try:
            cmd = [
                "ssh", "-tt", "-p", "443",
                "-R0:localhost:27189",
                "-R0:localhost:54921",
                "tcp@a.pinggy.io",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "-o", "ServerAliveInterval=15"
            ]
            pinggy_proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                preexec_fn=os.setsid
            )
            with self._mirrors_lock:
                self.running_mirrors["ngrok"] = pinggy_proc

            video_url = None
            control_url = None
            output_lines = []

            def reader_thread():
                try:
                    for line in iter(pinggy_proc.stdout.readline, ''):
                        if not line:
                            break
                        output_lines.append(line)
                except Exception:
                    pass

            t = threading.Thread(target=reader_thread, daemon=True)
            t.start()

            start_time = time.time()
            while not self._cancel_remote.is_set() and (time.time() - start_time < 25):
                for line in list(output_lines):
                    parsed = self._extract_pinggy_url(line)
                    if parsed:
                        if video_url is None:
                            video_url = parsed
                        elif control_url is None and parsed != video_url:
                            control_url = parsed
                if video_url and control_url:
                    break
                if pinggy_proc.poll() is not None:
                    break
                time.sleep(0.2)

            if self._cancel_remote.is_set():
                if pinggy_proc:
                    self._kill_pg(pinggy_proc)
                with self._mirrors_lock:
                    self.running_mirrors.pop("ngrok", None)
                return {"success": False, "error": "Cancelled"}

            if not video_url or not control_url:
                if pinggy_proc:
                    self._kill_pg(pinggy_proc)
                with self._mirrors_lock:
                    self.running_mirrors.pop("ngrok", None)
                return {"success": False, "error": "Pinggy did not return both URLs"}

            # Generate session token + id
            session_token = secrets.token_hex(18)
            session_id = secrets.token_hex(8)

            # Launch local mirror_client in listen mode
            try:
                client_path = resolve_mirror_client_path()
            except FileNotFoundError as e:
                if pinggy_proc:
                    self._kill_pg(pinggy_proc)
                with self._mirrors_lock:
                    self.running_mirrors.pop("ngrok", None)
                return {"success": False, "error": str(e)}

            log_path = os.path.join(tempfile.gettempdir(), "LinktoMac_Mirror.log")
            mirror_log_file = open(log_path, "w")
            cmd = [str(client_path), "--video-port", "27189",
                   "--auth-token", session_token]

            if self._cancel_remote.is_set():
                if pinggy_proc:
                    self._kill_pg(pinggy_proc)
                with self._mirrors_lock:
                    self.running_mirrors.pop("ngrok", None)
                if mirror_log_file:
                    try:
                        mirror_log_file.close()
                    except Exception:
                        pass
                return {"success": False, "error": "Cancelled"}

            self._user_stopped = False
            mirror_proc = subprocess.Popen(
                cmd,
                stdin=subprocess.DEVNULL,
                stdout=mirror_log_file,
                stderr=subprocess.STDOUT,
                preexec_fn=os.setsid
            )
            with self._mirrors_lock:
                self.running_mirrors["mirror"] = mirror_proc

            time.sleep(0.4)
            if mirror_proc.poll() is not None:
                err_code = mirror_proc.returncode
                self._kill_pg(pinggy_proc)
                with self._mirrors_lock:
                    self.running_mirrors.pop("ngrok", None)
                    self.running_mirrors.pop("mirror", None)
                if mirror_log_file:
                    try:
                        mirror_log_file.close()
                    except Exception:
                        pass
                return {"success": False, "error": f"mirror_client exited with {err_code}"}

            self._spawn_process_watcher(mirror_proc, "remote")
            self.mirror_log_file = mirror_log_file

            # Publish to Firestore (Phase 1 will read this)
            self._publish_session_to_firestore(
                video_host=video_url["host"], video_port=video_url["port"],
                control_host=control_url["host"], control_port=control_url["port"],
                token=session_token, session_id=session_id,
            )

            return {
                "success": True,
                "code": f"{video_url['host']}:{video_url['port']}",
                "video": video_url, "control": control_url,
                "token": session_token, "session_id": session_id,
            }

        except Exception as e:
            if mirror_proc:
                self._kill_pg(mirror_proc)
            if pinggy_proc:
                self._kill_pg(pinggy_proc)
            if mirror_log_file:
                try:
                    mirror_log_file.close()
                except Exception:
                    pass
            with self._mirrors_lock:
                self.running_mirrors.pop("ngrok", None)
                self.running_mirrors.pop("mirror", None)
            return {"success": False, "error": str(e)}

    def _extract_pinggy_url(self, line):
        """Parse 'tcp://a.pinggy.io:44321' from a Pinggy log line."""
        idx = line.find("tcp://")
        if idx < 0:
            return None
        rest = line[idx:].replace("tcp://", "").split('\x1b')[0].strip().split()[0]
        if ":" not in rest:
            return None
        host, port_str = rest.rsplit(":", 1)
        try:
            return {"host": host, "port": int(port_str), "raw": rest}
        except ValueError:
            return None

    def _publish_session_to_firestore(self, video_host, video_port,
                                      control_host, control_port,
                                      token, session_id):
        """Write the relay endpoints + token to Firestore so Android can dial in."""
        try:
            import firebase_admin
            from firebase_admin import credentials, firestore
            from pathlib import Path
            if not firebase_admin._apps:
                cred_path = Path.home() / ".linktomac" / "firebase-creds.json"
                if cred_path.exists():
                    cred = credentials.Certificate(str(cred_path))
                    firebase_admin.initialize_app(cred)
                else:
                    return
            db = firestore.client()
            db.collection("sessions").document(session_id).set({
                "videoHost": video_host, "videoPort": video_port,
                "controlHost": control_host, "controlPort": control_port,
                "token": token, "active": True,
                "sessionId": session_id,
                "updatedAt": firestore.SERVER_TIMESTAMP,
            })
        except Exception as e:
            logger.warning(f"Firestore publish failed: {e}")

    def _publish_session_to_user(self, uid, relay_res):
        """Publish the active session to users/{uid}/sessions/current."""
        try:
            import firebase_admin
            from firebase_admin import credentials, firestore
            from pathlib import Path
            if not firebase_admin._apps:
                cred_path = Path.home() / ".linktomac" / "firebase-creds.json"
                if cred_path.exists():
                    cred = credentials.Certificate(str(cred_path))
                    firebase_admin.initialize_app(cred)
                else:
                    return
            db = firestore.client()
            video = relay_res.get("video", {})
            control = relay_res.get("control", {})
            db.collection("users").document(uid).collection("sessions").document("current").set({
                "videoHost": video.get("host", ""),
                "videoPort": video.get("port", 27189),
                "controlHost": control.get("host", ""),
                "controlPort": control.get("port", 54921),
                "token": relay_res.get("token", ""),
                "sessionId": relay_res.get("session_id", ""),
                "active": True,
                "updatedAt": firestore.SERVER_TIMESTAMP,
            })
        except Exception as e:
            logger.warning(f"Firestore publish to user {uid} failed: {e}")

    def _kill_pg(self, proc):
        if proc and proc.poll() is None:
            try:
                pgid = os.getpgid(proc.pid)
                os.killpg(pgid, signal.SIGTERM)
                try:
                    proc.wait(timeout=1.0)
                except Exception:
                    os.killpg(pgid, signal.SIGKILL)
                    proc.wait(timeout=1.0)
            except Exception:
                try:
                    proc.kill()
                    proc.wait(timeout=0.5)
                except Exception:
                    pass
            finally:
                for stream in [proc.stdout, proc.stderr, proc.stdin]:
                    if stream:
                        try:
                            stream.close()
                        except Exception:
                            pass

    def stop_remote_session(self):
        self._user_stopped = True
        self._cancel_remote.set()
        with self._mirrors_lock:
            for key in ["ngrok", "mirror"]:
                proc = self.running_mirrors.get(key)
                if proc:
                    self._kill_pg(proc)
                self.running_mirrors.pop(key, None)
        if self.mirror_log_file:
            try:
                self.mirror_log_file.close()
            except Exception:
                pass
            self.mirror_log_file = None
        return True

    def stop_mirror(self, ip=None):
        self._user_stopped = True
        self._cancel_remote.set()
        with self._mirrors_lock:
            if ip:
                proc = self.running_mirrors.get(ip)
                if proc:
                    self._kill_pg(proc)
                self.running_mirrors.pop(ip, None)
            else:
                for key, proc in list(self.running_mirrors.items()):
                    self._kill_pg(proc)
                self.running_mirrors.clear()
        if self.mirror_log_file:
            try:
                self.mirror_log_file.close()
            except Exception:
                pass
            self.mirror_log_file = None
        return True

    def launch_remote_mirror(self):
        """Start remote mirror session using Pinggy reverse tunnel and resolve_mirror_client_path()."""
        return self.start_remote_session()


LinktoMacBackend = LinkMyMacBackend
__all__ = ["LinkMyMacBackend", "LinktoMacBackend", "UsbDeviceWatcher", "resolve_mirror_client_path"]
