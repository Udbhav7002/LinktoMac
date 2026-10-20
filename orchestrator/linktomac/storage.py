import json
import os
import fcntl
from pathlib import Path
import logging
import tempfile

logger = logging.getLogger(__name__)

CONFIG_DIR = Path.home() / ".linktomac"
DEVICES_FILE = CONFIG_DIR / "devices.json"

def init_storage():
    if not CONFIG_DIR.exists():
        CONFIG_DIR.mkdir(parents=True, mode=0o700)
    if not DEVICES_FILE.exists():
        with open(DEVICES_FILE, "w") as f:
            json.dump([], f)

def get_devices():
    init_storage()
    try:
        with open(DEVICES_FILE, "r") as f:
            fcntl.flock(f.fileno(), fcntl.LOCK_SH)
            try:
                devices = json.load(f)
                # Filter out loopback or mock addresses
                return [d for d in devices if isinstance(d, dict) and d.get("ip") not in ("127.0.0.1", "localhost", "0.0.0.0")]
            finally:
                fcntl.flock(f.fileno(), fcntl.LOCK_UN)
    except Exception as e:
        logger.error(f"Failed to read devices: {e}")
        return []

def save_device(ip, name, device_id):
    devices = get_devices()
    
    # Check if already exists, update ip/name if so
    for d in devices:
        if d.get("device_id") == device_id:
            d["ip"] = ip
            d["name"] = name
            _write_devices(devices)
            return

    devices.append({
        "device_id": device_id,
        "name": name,
        "ip": ip
    })
    _write_devices(devices)

def remove_device(device_id):
    devices = get_devices()
    devices = [d for d in devices if d.get("device_id") != device_id]
    _write_devices(devices)

def _write_devices(devices):
    init_storage()
    # Atomic write: write to temp file, then rename
    with tempfile.NamedTemporaryFile(
        mode='w', dir=CONFIG_DIR, prefix='devices_', suffix='.tmp', delete=False
    ) as f:
        try:
            fcntl.flock(f.fileno(), fcntl.LOCK_EX)
            json.dump(devices, f, indent=4)
            f.flush()
            os.fsync(f.fileno())
        finally:
            fcntl.flock(f.fileno(), fcntl.LOCK_UN)
    os.replace(f.name, DEVICES_FILE)
    os.chmod(DEVICES_FILE, 0o600)

SETTINGS_FILE = CONFIG_DIR / "settings.json"

def get_settings():
    init_storage()
    if not SETTINGS_FILE.exists():
        # Default settings
        return {"mode": "quality"}
    try:
        with open(SETTINGS_FILE, "r") as f:
            fcntl.flock(f.fileno(), fcntl.LOCK_SH)
            try:
                return json.load(f)
            finally:
                fcntl.flock(f.fileno(), fcntl.LOCK_UN)
    except Exception:
        return {"mode": "quality"}

def save_settings(settings):
    init_storage()
    with tempfile.NamedTemporaryFile(
        mode='w', dir=CONFIG_DIR, prefix='settings_', suffix='.tmp', delete=False
    ) as f:
        try:
            fcntl.flock(f.fileno(), fcntl.LOCK_EX)
            json.dump(settings, f, indent=4)
            f.flush()
            os.fsync(f.fileno())
        finally:
            fcntl.flock(f.fileno(), fcntl.LOCK_UN)
    os.replace(f.name, SETTINGS_FILE)
    os.chmod(SETTINGS_FILE, 0o600)
