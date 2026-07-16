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
    parser.add_argument("--server-jar", default=str(config.BASE_DIR / "android-server/server.jar"), help="Path to server.jar")
    
    if getattr(sys, 'frozen', False):
        # Inside a Mac app bundle, --add-binary puts secondary binaries in Contents/Frameworks
        if sys.platform == 'darwin':
            client_default = str(Path(sys.executable).parent.parent / "Frameworks" / "mirror_client")
        else:
            # On other platforms, it's usually in the same dir as sys.executable
            client_default = str(Path(sys.executable).parent / "mirror_client")
    else:
        client_default = str(config.BASE_DIR / "native-client/build/mirror_client")
        
    parser.add_argument("--client-bin", default=client_default, help="Path to mirror_client binary")
    args = parser.parse_args()

    try:
        try:
            if args.wireless:
                logger.info(f"Attempting wireless connection to {args.wireless}...")
                AdbClient.connect(args.wireless)
                args.serial = args.wireless
        except AdbError as e:
            logger.warning(f"Wireless ADB connection failed: {e}. Falling back to USB connection if available.")
            args.serial = None # Force it to use USB fallback

        adb = AdbClient(args.serial)
        
        devices = adb.get_devices()
        if not devices:
            logger.error("No ADB devices found. Please connect via USB.")
            sys.exit(1)
            
        if not args.serial and args.wireless:
            # We fell back to USB, but the user requested wireless mirroring
            # Automatically enable tcpip 5555 on the USB device so wireless works next time or even now!
            logger.info("USB device found. Automatically enabling wireless debugging on port 5555...")
            subprocess.run(["adb", "-s", devices[0], "tcpip", "5555"], check=False)
            import time
            time.sleep(2) # Give adbd a moment to restart on the phone
            try:
                AdbClient.connect(args.wireless)
                args.serial = args.wireless
                adb = AdbClient(args.serial)
                logger.info("Successfully switched to wireless mode!")
            except AdbError:
                logger.warning("Still couldn't connect wirelessly after tcpip. Will proceed with USB mirroring.")
                args.serial = devices[0]
                adb = AdbClient(args.serial)
        else:
            if args.serial and args.serial not in devices:
                if f"{args.serial}:5555" in devices:
                    args.serial = f"{args.serial}:5555"
                    adb = AdbClient(args.serial)
                else:
                    logger.error(f"Device {args.serial} not found in connected devices.")
                    sys.exit(1)
        
        logger.info("Pushing server to device...")
        jar_path = Path(args.server_jar).resolve()
        if not jar_path.exists():
            logger.error(f"Server jar not found at {jar_path}")
            sys.exit(1)
        
        adb.push(str(jar_path), config.SERVER_REMOTE_PATH)

        logger.info("Setting up port forwarding...")
        adb.forward(config.DEFAULT_VIDEO_PORT, config.DEFAULT_VIDEO_PORT)
        adb.forward(config.DEFAULT_CONTROL_PORT, config.DEFAULT_CONTROL_PORT)

        logger.info("Starting Android server...")
        try:
            adb._run(["shell", "pkill", "-f", "com.linktomac.Server"], check=False)
        except Exception:
            pass
            
        server_cmd = [
            "CLASSPATH=" + config.SERVER_REMOTE_PATH,
            "app_process",
            "/",
            config.SERVER_MAIN_CLASS,
            str(args.width),
            str(args.height),
            str(args.bitrate),
            str(args.fps)
        ]
        server_process = adb.shell_bg(*server_cmd)

        logger.info("Waiting for server to start...")
        wait_for_server(3.0)
            
        logger.info("Launching native client...")
        client_path = Path(args.client_bin).resolve()
        
        if not client_path.exists():
            logger.error(f"Mirror client binary not found at {client_path}")
            server_process.kill()
            sys.exit(1)
        logger.info(f"Starting mirror client: {client_path}")
        try:
            subprocess.run([
                str(client_path),
                "--connect", "127.0.0.1",
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
        if 'server_process' in locals() and server_process.poll() is None:
            server_process.kill()
        
        # We don't remove forward automatically if we didn't add it, but it's safe to clear
        try:
            adb.remove_forward(config.DEFAULT_VIDEO_PORT)
            adb.remove_forward(config.DEFAULT_CONTROL_PORT)
        except:
            pass

if __name__ == "__main__":
    main()
