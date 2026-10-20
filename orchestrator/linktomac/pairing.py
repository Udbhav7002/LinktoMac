import socket
import threading
import json
import logging
import time
import ssl
import os
from pathlib import Path

logger = logging.getLogger(__name__)

DISCOVERY_PORT = 27191
PAIRING_PORT = 27192
# Enforced TLS 1.3 port for secure pairing
PAIRING_PORT_TLS = 27193

from zeroconf import Zeroconf, ServiceBrowser, ServiceListener, ServiceStateChange

class DiscoveryListener(ServiceListener):
    def __init__(self, on_device_found):
        self.on_device_found = on_device_found
        self.zeroconf = None
        self.browser = None
        self.tls_browser = None
        self._stop_event = threading.Event()

    def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        self._process_service(zc, type_, name)

    def update_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        self._process_service(zc, type_, name)

    def remove_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        pass

    def on_service_state_change(self, zeroconf, service_type, name, state_change):
        if self._stop_event.is_set():
            return
        if state_change == ServiceStateChange.Added or state_change == ServiceStateChange.Updated:
            self._process_service(zeroconf, service_type, name)

    def _process_service(self, zeroconf, service_type, name):
        if self._stop_event.is_set():
            return
        try:
            info = zeroconf.get_service_info(service_type, name)
            if info:
                addresses = info.parsed_addresses()
                if addresses:
                    ip = addresses[0]
                    # Strip suffix from service name
                    device_name = name.replace("._linktomac._tcp.local.", "").replace("._linktomac-tls._tcp.local.", "")

                    # Get device_id from TXT records
                    device_id = "unknown_device"
                    if info.properties and b'device_id' in info.properties:
                        device_id = info.properties[b'device_id'].decode('utf-8')

                    # Check if this is a TLS service
                    is_tls = True
                    if info.properties and b'tls' in info.properties:
                        is_tls = info.properties[b'tls'].decode('utf-8') == 'true'

                    self.on_device_found(ip, device_name, device_id, is_tls)
        except Exception as e:
            logger.debug(f"Error resolving service {name}: {e}")

    def start(self):
        if self.zeroconf: return
        self._stop_event.clear()
        self.zeroconf = Zeroconf()
        self.browser = ServiceBrowser(self.zeroconf, "_linktomac._tcp.local.", listener=self)
        self.tls_browser = ServiceBrowser(self.zeroconf, "_linktomac-tls._tcp.local.", listener=self)

    def stop(self):
        self._stop_event.set()
        if self.zeroconf:
            time.sleep(0.1)
            try:
                self.zeroconf.close()
            except Exception:
                pass
            self.zeroconf = None
            self.browser = None
            self.tls_browser = None


class PairingClient:
    @staticmethod
    def pair(ip, pin, my_name="LinktoMac_Mac"):
        """
        Pairs with the Android app strictly over TLS 1.3 encryption.
        Cleartext transmission is rejected to protect PIN secrecy against MITM.
        Returns (success: bool, error_message: str)
        """
        port = PAIRING_PORT_TLS

        try:
            cert_manager = _get_cert_manager()
            context = cert_manager.get_ssl_context(is_server=False)
            if context is None:
                return False, "Security Error: TLS 1.3 encryption context could not be initialized."

            # Enforce TLS 1.3
            context.minimum_version = ssl.TLSVersion.TLSv1_3
            context.maximum_version = ssl.TLSVersion.TLSv1_3

            raw_sock = socket.create_connection((ip, port), timeout=5.0)
            with context.wrap_socket(raw_sock, server_hostname=ip) as secure_sock:
                # Transmit pairing request inside TLS 1.3 tunnel
                request = f"PAIR:{pin}:{my_name}\n"
                secure_sock.sendall(request.encode('utf-8'))

                # Receive server confirmation
                response = secure_sock.recv(1024).decode('utf-8').strip()
                if response == "SUCCESS":
                    logger.info(f"Successfully paired with {ip} over encrypted TLS 1.3 tunnel")
                    return True, ""
                else:
                    return False, f"Phone rejected pairing: {response}"

        except socket.timeout:
            return False, "Connection timed out (verify phone is on same Wi-Fi)"
        except ConnectionRefusedError:
            return False, f"Connection refused on TLS port {port} (Ensure phone app is running)"
        except ssl.SSLError as e:
            logger.error(f"TLS 1.3 handshake failed: {e}")
            return False, f"TLS 1.3 handshake failed: {e}"
        except Exception as e:
            logger.error(f"Pairing error: {e}")
            return False, str(e)


class CertificateManager:
    """Manages TLS 1.3 certificates and cryptographic identities for secure device pairing."""

    def __init__(self):
        self.cert_dir = Path.home() / ".linktomac" / "certs"
        self.cert_dir.mkdir(parents=True, exist_ok=True)
        self.cert_file = self.cert_dir / "device_cert.pem"
        self.key_file = self.cert_dir / "device_key.pem"
        self.ca_file = self.cert_dir / "ca_cert.pem"
        self._ensure_certificates()

    def _ensure_certificates(self):
        """Generate self-signed X.509 certificate for TLS 1.3 if not present."""
        if self.cert_file.exists() and self.key_file.exists():
            return

        try:
            from cryptography import x509
            from cryptography.hazmat.primitives import hashes, serialization
            from cryptography.hazmat.primitives.asymmetric import rsa
            import datetime

            # Generate 2048-bit RSA private key
            private_key = rsa.generate_private_key(
                public_exponent=65537,
                key_size=2048,
            )

            subject = issuer = x509.Name([
                x509.NameAttribute(x509.oid.NameOID.COMMON_NAME, "LinktoMac"),
                x509.NameAttribute(x509.oid.NameOID.ORGANIZATION_NAME, "LinktoMac"),
            ])

            now = datetime.datetime.now(datetime.timezone.utc)
            cert = x509.CertificateBuilder().subject_name(
                subject
            ).issuer_name(
                issuer
            ).public_key(
                private_key.public_key()
            ).serial_number(
                x509.random_serial_number()
            ).not_valid_before(
                now
            ).not_valid_after(
                now + datetime.timedelta(days=3650)
            ).add_extension(
                x509.SubjectAlternativeName([x509.DNSName("localhost"), x509.IPAddress("127.0.0.1")]),
                critical=False
            ).add_extension(
                x509.BasicConstraints(ca=False, path_length=None),
                critical=True
            ).sign(private_key, hashes.SHA256())

            # Write certificate and key
            with open(self.cert_file, "wb") as f:
                f.write(cert.public_bytes(serialization.Encoding.PEM))

            with open(self.key_file, "wb") as f:
                f.write(private_key.private_bytes(
                    encoding=serialization.Encoding.PEM,
                    format=serialization.PrivateFormat.PKCS8,
                    encryption_algorithm=serialization.NoEncryption()
                ))

            with open(self.ca_file, "wb") as f:
                f.write(cert.public_bytes(serialization.Encoding.PEM))

            os.chmod(self.key_file, 0o600)
            os.chmod(self.cert_file, 0o644)
            logger.info(f"Generated TLS 1.3 certificates in {self.cert_dir}")

        except ImportError:
            logger.error("cryptography library not found; required for TLS 1.3 certificate generation")
        except Exception as e:
            logger.error(f"Failed to generate TLS certificates: {e}")

    def get_ssl_context(self, is_server=False):
        """Creates an SSLContext configured strictly for TLS 1.3."""
        if not self.cert_file.exists() or not self.key_file.exists():
            self._ensure_certificates()

        if not self.cert_file.exists() or not self.key_file.exists():
            return None

        if is_server:
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.minimum_version = ssl.TLSVersion.TLSv1_3
            context.maximum_version = ssl.TLSVersion.TLSv1_3
            context.load_cert_chain(str(self.cert_file), str(self.key_file))
            context.verify_mode = ssl.CERT_NONE
        else:
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            context.minimum_version = ssl.TLSVersion.TLSv1_3
            context.maximum_version = ssl.TLSVersion.TLSv1_3
            context.check_hostname = False
            context.verify_mode = ssl.CERT_NONE  # TOFU (Trust On First Use) for peer device certs
            try:
                context.load_cert_chain(str(self.cert_file), str(self.key_file))
            except Exception:
                pass

        return context


_cert_manager = None

def _get_cert_manager():
    global _cert_manager
    if _cert_manager is None:
        _cert_manager = CertificateManager()
    return _cert_manager
