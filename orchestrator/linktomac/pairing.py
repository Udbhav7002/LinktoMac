import socket
import threading
import json
import logging
import time

logger = logging.getLogger(__name__)

DISCOVERY_PORT = 27191
PAIRING_PORT = 27192

from zeroconf import Zeroconf, ServiceBrowser, ServiceStateChange

class DiscoveryListener:
    def __init__(self, on_device_found):
        self.on_device_found = on_device_found
        self.zeroconf = None
        self.browser = None
        self._stop_event = threading.Event()

    def on_service_state_change(self, zeroconf, service_type, name, state_change):
        if self._stop_event.is_set():
            return
        if state_change == ServiceStateChange.Added or state_change == ServiceStateChange.Updated:
            info = zeroconf.get_service_info(service_type, name)
            if info:
                addresses = info.parsed_addresses()
                if addresses:
                    ip = addresses[0]
                    # The service name is "DeviceName._linktomac._tcp.local."
                    # We can strip the suffix
                    device_name = name.replace("._linktomac._tcp.local.", "")
                    
                    # Get device_id from TXT records
                    device_id = "unknown_device"
                    if info.properties and b'device_id' in info.properties:
                        device_id = info.properties[b'device_id'].decode('utf-8')
                        
                    self.on_device_found(ip, device_name, device_id)

    def start(self):
        if self.zeroconf: return
        self._stop_event.clear()
        self.zeroconf = Zeroconf()
        self.browser = ServiceBrowser(self.zeroconf, "_linktomac._tcp.local.", handlers=[self.on_service_state_change])

    def stop(self):
        self._stop_event.set()
        if self.zeroconf:
            # Wait briefly for any in-flight callbacks to complete
            time.sleep(0.1)
            self.zeroconf.close()
            self.zeroconf = None
            self.browser = None

class PairingClient:
    @staticmethod
    def pair(ip, pin, my_name="LinktoMac_PC"):
        """
        Attempts to pair with the Android app by sending the PIN over TCP.
        Returns (success: bool, error_message: str)
        """
        try:
            with socket.create_connection((ip, PAIRING_PORT), timeout=5.0) as sock:
                # Send pairing request: PAIR:<PIN>:<PC_NAME>
                request = f"PAIR:{pin}:{my_name}\n"
                sock.sendall(request.encode('utf-8'))
                
                # Wait for response
                response = sock.recv(1024).decode('utf-8').strip()
                if response == "SUCCESS":
                    return True, ""
                else:
                    return False, f"Phone rejected pairing: {response}"
        except socket.timeout:
            return False, "Connection timed out"
        except ConnectionRefusedError:
            return False, "Connection refused (Is the phone app open?)"
        except Exception as e:
            return False, str(e)
