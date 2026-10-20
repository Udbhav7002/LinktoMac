import argparse
import sys
import time
import socket
import logging
import os
import subprocess
import traceback
from pathlib import Path

from . import config
from .adb import AdbClient, AdbError
from .backend import resolve_mirror_client_path

def global_exception_handler(exc_type, exc_value, exc_traceback):
    crash_log = os.path.expanduser("~/Desktop/LinktoMac_Crash.txt")
    with open(crash_log, "a") as f:
        f.write("Uncaught Exception in CLI:\n")
        traceback.print_exception(exc_type, exc_value, exc_traceback, file=f)
    sys.__excepthook__(exc_type, exc_value, exc_traceback)

sys.excepthook = global_exception_handler

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

def wait_for_server(timeout=5.0):
    """Wait for the Android server to start. We can't probe the port because
    the server only accepts one connection (which would be consumed by the probe).
    Instead, we just give it a reasonable amount of time to start up."""
    time.sleep(timeout)

def main():
    parser = argparse.ArgumentParser(description="LinktoMac Orchestrator")
    parser.add_argument("--serial", help="Device serial number")
    parser.add_argument("--wireless", help="IP address and port of the wireless device (e.g. 192.168.1.100:5555)")
    parser.add_argument("--bitrate", type=int, default=config.DEFAULT_BITRATE, help="Video bitrate")
    parser.add_argument("--fps", type=int, default=config.DEFAULT_FPS, help="Max frame rate")
    parser.add_argument("--width", type=int, default=config.DEFAULT_WIDTH, help="Screen width")
    parser.add_argument("--height", type=int, default=config.DEFAULT_HEIGHT, help="Screen height")
    parser.add_argument("--server-jar", default=None, help="(Deprecated) Path to legacy server.jar")
    
    try:
        client_default = str(resolve_mirror_client_path())
    except Exception:
        client_default = str(config.BASE_DIR / "native-client/build/mirror_client")

    parser.add_argument("--client-bin", default=client_default, help="Path to mirror_client binary")
    args = parser.parse_args()

    adb = None
    try:
        if args.wireless:
            target_host = args.wireless.split(":")[0]
            logger.info(f"Connecting to wireless Android app at {target_host}:{config.DEFAULT_VIDEO_PORT}...")
            # Optionally attempt to auto-enable accessibility if ADB over Wi-Fi is active
            try:
                AdbClient.connect(args.wireless, timeout=1.5)
                adb = AdbClient(args.wireless)
                adb.enable_accessibility()
                logger.info("Auto-enabled accessibility via wireless ADB.")
            except Exception:
                logger.debug("Wireless ADB connection unavailable; proceeding with direct Wi-Fi mirror.")
        else:
            adb = AdbClient(args.serial)
            devices = adb.get_devices()
            if not devices:
                logger.error("No ADB devices found. Please connect via USB.")
                sys.exit(1)

            if args.serial and args.serial not in devices:
                if f"{args.serial}:5555" in devices:
                    args.serial = f"{args.serial}:5555"
                    adb = AdbClient(args.serial)
                else:
                    logger.error(f"Device {args.serial} not found in connected devices.")
                    sys.exit(1)

            logger.info("Setting up USB port forwarding to Android app on port 27189...")
            adb.forward(config.DEFAULT_VIDEO_PORT, config.DEFAULT_VIDEO_PORT)
            adb.forward(config.DEFAULT_CONTROL_PORT, config.DEFAULT_CONTROL_PORT)
            adb.enable_accessibility()
            target_host = "127.0.0.1"

        logger.info("Launching native mirror client...")
        client_path = Path(args.client_bin).resolve() if args.client_bin else None

        if not client_path or not client_path.exists():
            try:
                client_path = resolve_mirror_client_path()
            except FileNotFoundError:
                logger.error(f"Mirror client binary not found at {args.client_bin}")
                sys.exit(1)
        logger.info(f"Starting mirror client: {client_path}")
        try:
            subprocess.run([
                str(client_path),
                "--connect", target_host,
                "--video-port", str(config.DEFAULT_VIDEO_PORT)
            ], check=True)
        except KeyboardInterrupt:
            logger.info("Shutting down...")
        except subprocess.CalledProcessError as e:
            logger.error(f"Client exited with error: {e}")

    except AdbError as e:
        logger.error(f"ADB Error: {e}")
    finally:
        logger.info("Cleaning up...")
        if adb and not args.wireless:
            try:
                adb.remove_forward(config.DEFAULT_VIDEO_PORT)
                adb.remove_forward(config.DEFAULT_CONTROL_PORT)
            except Exception:
                pass

if __name__ == "__main__":
    main()
