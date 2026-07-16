"""Configuration constants for LinktoMac"""

import os
import sys
from pathlib import Path

# Resolve base directory (handles PyInstaller bundle)
if getattr(sys, 'frozen', False):
    # PyInstaller --onefile: sys._MEIPASS is the temp extraction dir
    if hasattr(sys, '_MEIPASS'):
        BASE_DIR = Path(sys._MEIPASS)
    # PyInstaller --onedir (.app bundle): sys.executable is Contents/MacOS/LinktoMac
    # Resources are in Contents/Resources
    else:
        exe_dir = Path(sys.executable).parent
        if exe_dir.name == "MacOS" and exe_dir.parent.name == "Contents":
            BASE_DIR = exe_dir.parent / "Resources"
        else:
            BASE_DIR = exe_dir
else:
    BASE_DIR = Path(__file__).parent.parent.parent

DEFAULT_VIDEO_PORT = 27189
DEFAULT_CONTROL_PORT = 27190

SERVER_JAR_NAME = "server.jar"
SERVER_REMOTE_PATH = "/data/local/tmp/linktomac-server.jar"
SERVER_MAIN_CLASS = "com.linktomac.Server"

DEFAULT_BITRATE = 4000000
DEFAULT_FPS = 60
DEFAULT_WIDTH = 720
DEFAULT_HEIGHT = 1600
