import os
import subprocess
import logging

# Ensure standard adb locations are in PATH for Mac windowed app sandbox
os.environ["PATH"] += os.pathsep + "/opt/homebrew/bin" + os.pathsep + "/usr/local/bin" + os.pathsep + os.path.expanduser("~/Library/Android/sdk/platform-tools")

logger = logging.getLogger(__name__)

class AdbError(Exception):
    pass

class AdbClient:
    def __init__(self, serial=None):
        self.serial = serial

    def _run(self, args, check=True, capture_output=True):
        cmd = ["adb"]
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
        
    @staticmethod
    def connect(ip):
        # adb connect <ip> returns immediately, but might print "connected to" or "failed to connect"
        try:
            result = subprocess.run(["adb", "connect", ip], capture_output=True, text=True, check=False)
        except FileNotFoundError:
            raise AdbError("adb not found in PATH")
        if "failed" in result.stdout.lower() or "cannot connect" in result.stdout.lower() or "connection refused" in result.stdout.lower():
            raise AdbError(f"Failed to connect to {ip}: {result.stdout}")
        logger.info(f"Connected to {ip}")

    def shell_bg(self, *args):
        """Runs an adb shell command in the background with args as list (no shell injection)."""
        cmd = ["adb"]
        if self.serial:
            cmd.extend(["-s", self.serial])
        cmd.extend(["shell"] + list(args))
        
        logger.debug(f"Starting bg: {' '.join(cmd)}")
        return subprocess.Popen(cmd, text=True)

    @staticmethod
    def get_devices():
        try:
            result = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=False)
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