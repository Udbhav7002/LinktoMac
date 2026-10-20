import os
import sys
import re
import subprocess
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# ADB binary resolution — bundle-first, then well-known paths, then PATH
# ---------------------------------------------------------------------------
_adb_path_cache = None


def _find_adb_path() -> str:
    """Return the path to the adb binary.

    Resolution order:
      1. Frozen app bundle → Contents/Resources/platform-tools/adb
      2. Well-known install locations (Homebrew, usr/local, Android SDK)
      3. Plain ``adb`` (rely on system PATH)
    The result is cached so the lookup only runs once.
    """
    global _adb_path_cache
    if _adb_path_cache is not None:
        return _adb_path_cache

    candidates: list[Path] = []

    # 1. PyInstaller frozen bundle
    if getattr(sys, "frozen", False):
        bundle_root = Path(sys.executable).parent.parent  # Contents/
        candidates.append(bundle_root / "MacOS" / "platform-tools" / "adb")
        candidates.append(bundle_root / "Resources" / "platform-tools" / "adb")
        candidates.append(bundle_root / "Frameworks" / "platform-tools" / "adb")
        if hasattr(sys, "_MEIPASS"):
            candidates.append(Path(sys._MEIPASS) / "platform-tools" / "adb")

    # 2. Well-known locations
    candidates.extend([
        Path("/opt/homebrew/bin/adb"),
        Path("/usr/local/bin/adb"),
        Path.home() / "Library/Android/sdk/platform-tools/adb",
    ])

    # 3. Plain "adb" on PATH (last resort)
    candidates.append(Path("adb"))

    for path in candidates:
        try:
            subprocess.run(
                [str(path), "version"],
                capture_output=True,
                text=True,
                check=False,
            )
            if path.exists() or path.name == "adb":
                _adb_path_cache = str(path)
                logger.info("Using adb: %s", _adb_path_cache)
                return _adb_path_cache
        except OSError:
            continue

    _adb_path_cache = "adb"
    logger.warning("adb not found anywhere — falling back to 'adb'")
    return _adb_path_cache


# ---------------------------------------------------------------------------
# Exceptions
# ---------------------------------------------------------------------------
class AdbError(Exception):
    pass


# ---------------------------------------------------------------------------
# ADB client
# ---------------------------------------------------------------------------
class AdbClient:
    def __init__(self, serial=None):
        if not serial:
            try:
                detailed = self.get_detailed_devices()
                usb_devs = [d for d in detailed if d.get("state") == "device" and d.get("is_usb")]
                if usb_devs:
                    serial = usb_devs[0]["serial"]
                elif len(detailed) == 1 and detailed[0].get("state") == "device":
                    serial = detailed[0]["serial"]
            except Exception:
                pass
        self.serial = serial

    def _run(self, args, check=True, capture_output=True):
        cmd = [_find_adb_path()]
        if self.serial:
            cmd.extend(["-s", self.serial])
        cmd.extend(args)

        logger.debug(f"Running: {' '.join(cmd)}")

        try:
            result = subprocess.run(cmd, check=check, capture_output=capture_output, text=True)
            return result
        except subprocess.CalledProcessError as e:
            logger.error(f"ADB command failed: {e.stderr}")
            raise AdbError(f"ADB error: {e.stderr}") from e
        except FileNotFoundError:
            logger.error("adb not found in PATH")
            raise AdbError("adb not found in PATH") from None

    def push(self, local, remote):
        self._run(["push", local, remote])

    def forward(self, local_port, remote_port):
        self._run(["forward", f"tcp:{local_port}", f"tcp:{remote_port}"])

    def remove_forward(self, local_port):
        self._run(["forward", "--remove", f"tcp:{local_port}"], check=False)

    def enable_accessibility(self):
        """Automatically enable LinktoMac accessibility service and PROJECT_MEDIA permission via ADB."""
        try:
            cmd = (
                "settings put secure enabled_accessibility_services "
                "com.linktomac.app/com.linktomac.app.LinktoMacAccessibilityService && "
                "settings put secure accessibility_enabled 1 && "
                "appops set com.linktomac.app PROJECT_MEDIA allow"
            )
            self._run(["shell", cmd], check=False)
            logger.info("Auto-enabled LinktoMac accessibility service and PROJECT_MEDIA via ADB")
        except Exception as e:
            logger.debug(f"Could not auto-enable accessibility: {e}")

    @staticmethod
    def connect(ip, timeout=3.0):
        adb = _find_adb_path()
        try:
            result = subprocess.run([adb, "connect", ip], capture_output=True, text=True, check=False, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise AdbError(f"Connection to {ip} timed out after {timeout}s")
        except FileNotFoundError:
            raise AdbError("adb not found in PATH")
        if "failed" in result.stdout.lower() or "cannot connect" in result.stdout.lower() or "connection refused" in result.stdout.lower():
            raise AdbError(f"Failed to connect to {ip}: {result.stdout}")
        logger.info(f"Connected to {ip}")

    def shell_bg(self, *args):
        """Runs an adb shell command in the background with args as list (no shell injection)."""
        cmd = [_find_adb_path()]
        if self.serial:
            cmd.extend(["-s", self.serial])
        cmd.extend(["shell"] + list(args))

        logger.debug(f"Starting bg: {' '.join(cmd)}")
        return subprocess.Popen(cmd, text=True)

    @staticmethod
    def get_devices():
        adb = _find_adb_path()
        try:
            result = subprocess.run([adb, "devices"], capture_output=True, text=True, check=False)
        except FileNotFoundError:
            logger.error("adb not found in PATH")
            return []
        if result.returncode != 0:
            logger.error(f"adb devices failed: {result.stderr}")
            return []
        devices = []
        for line in result.stdout.splitlines()[1:]:
            parts = line.split()
            if len(parts) == 2 and parts[1] == "device":
                devices.append(parts[0])
        return devices

    @staticmethod
    def get_detailed_devices():
        """
        Runs `adb devices -l` and parses device attributes:
        serial, state, model, is_usb, line.
        Returns a list of dicts.
        """
        adb = _find_adb_path()
        try:
            result = subprocess.run([adb, "devices", "-l"], capture_output=True, text=True, check=False)
        except FileNotFoundError:
            logger.error("adb not found in PATH")
            return []
        if result.returncode != 0:
            logger.error(f"adb devices -l failed: {result.stderr}")
            return []

        devices = []
        for line in result.stdout.splitlines()[1:]:
            line_str = line.strip()
            if not line_str:
                continue
            parts = line_str.split()
            if len(parts) < 2:
                continue
            serial = parts[0]
            state = parts[1]

            model_match = re.search(r"model:([^\s]+)", line_str)
            if model_match:
                model = model_match.group(1).replace("_", " ")
            else:
                model = serial

            is_usb = ("usb:" in line_str) or (":" not in serial and not serial.startswith("emulator-"))

            devices.append({
                "serial": serial,
                "state": state,
                "model": model,
                "is_usb": is_usb,
                "raw": line_str
            })
        return devices

