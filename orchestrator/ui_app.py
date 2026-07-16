import webview
import sys
import os
import threading
import traceback
from pathlib import Path
from linktomac.backend import LinktoMacBackend
from linktomac import config

def setup_logging():
    log_dir = os.path.expanduser("~/.linktomax")
    os.makedirs(log_dir, exist_ok=True)
    log_file = os.path.join(log_dir, "app.log")
    
    import logging
    logging.basicConfig(
        level=logging.DEBUG,
        format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
        handlers=[
            logging.FileHandler(log_file),
            logging.StreamHandler(sys.stdout)
        ]
    )
    logging.getLogger(__name__).info("--- LinktoMac UI App Started ---")

def global_exception_handler(exc_type, exc_value, exc_traceback):
    import logging
    logger = logging.getLogger(__name__)
    logger.error("Uncaught Exception:", exc_info=(exc_type, exc_value, exc_traceback))
    crash_log = os.path.expanduser("~/Desktop/LinktoMac_Crash.txt")
    with open(crash_log, "a") as f:
        f.write("Uncaught Exception:\n")
        traceback.print_exception(exc_type, exc_value, exc_traceback, file=f)
    sys.__excepthook__(exc_type, exc_value, exc_traceback)

sys.excepthook = global_exception_handler

UI_DIR = config.BASE_DIR / "orchestrator" / "ui"

class Api:
    def __init__(self):
        self.backend = LinktoMacBackend()
        self.window = None
        self._js_queue = []
        self._js_lock = threading.Lock()

    def set_window(self, window):
        self.window = window
        # Update UI whenever backend discovers a new device
        self.backend.set_callback(self._on_devices_updated)
        self.backend.start_discovery()

    def _on_devices_updated(self, devices):
        if self.window:
            import json
            try:
                self.window.evaluate_js(f"window.updateDiscoveredDevices({json.dumps(devices)})")
            except Exception as e:
                pass

    def _queue_js(self, js_code):
        """Thread-safe JS evaluation - queues JS to be executed on main thread."""
        with self._js_lock:
            self._js_queue.append(js_code)
        # Trigger webview to process queue on next iteration
        if self.window:
            # webview doesn't have direct thread marshaling, but we can use evaluate_js
            # from the main thread. We'll process queue in a timer.
            pass

    def _process_js_queue(self):
        """Call this periodically from main thread to execute queued JS."""
        with self._js_lock:
            while self._js_queue:
                js = self._js_queue.pop(0)
                try:
                    self.window.evaluate_js(js)
                except Exception as e:
                    print(f"JS eval error: {e}")

    # Functions exposed to JS:
    def close_window(self):
        self.stop_remote_session()
        if self.window:
            self.window.destroy()
        os._exit(0)

    def minimize_window(self):
        if self.window:
            self.window.minimize()
    
    def start_remote_session(self):
        import logging
        logger = logging.getLogger(__name__)
        logger.info("User clicked 'Start Session' from UI")
        # Run in background to avoid freezing UI
        import threading
        def _launch():
            result = self.backend.start_remote_session()
            if self.window:
                import json
                self.window.evaluate_js(f"window.onRemoteSessionStarted({json.dumps(result)})")
        threading.Thread(target=_launch, daemon=True).start()
        return True

    def stop_remote_session(self):
        import logging
        logger = logging.getLogger(__name__)
        logger.info("User clicked 'Stop Session' or closed window")
        self.backend.stop_remote_session()
        return True

def main():
    setup_logging()
    
    if "--cli" in sys.argv:
        # Route to the CLI orchestrator instead of launching a UI
        from linktomac import cli
        # Remove --cli from argv so argparse doesn't get confused
        sys.argv.remove("--cli")
        cli.main()
        return

    if not UI_DIR.exists():
        UI_DIR.mkdir()
        
    api = Api()
    
    # Create the native Mac window
    window = webview.create_window(
        'LinktoMac', 
        url=str(UI_DIR / "index.html"),
        js_api=api,
        width=400, 
        height=600,
        resizable=False,
        frameless=False, 
        transparent=False,
        easy_drag=False # MUST BE FALSE. True causes horrific IPC jitter when dragging on macOS.
    )
    
    api.set_window(window)
    
    # Process JS queue periodically on main thread
    def _process_js():
        api._process_js_queue()
        if api.window:
            api.window.evaluate_js("setTimeout(window.processJsQueue || (() => {}), 0)")
    
    # Start the webview loop (blocks until window is closed)
    webview.start()

if __name__ == '__main__':
    main()
