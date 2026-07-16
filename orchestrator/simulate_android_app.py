import socket
import json
import time
import threading

DISCOVERY_PORT = 27191
PAIRING_PORT = 27192
PIN = "1234"

def broadcast_loop():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    
    payload = json.dumps({
        "device_id": "test_pixel_123",
        "name": "Simulated Pixel Phone"
    }).encode('utf-8')
    
    print("Starting UDP broadcast on port", DISCOVERY_PORT)
    while True:
        try:
            sock.sendto(payload, ('<broadcast>', DISCOVERY_PORT))
        except OSError:
            # On some systems <broadcast> doesn't work out of the box, try 255.255.255.255
            sock.sendto(payload, ('255.255.255.255', DISCOVERY_PORT))
        time.sleep(2)

def pairing_server():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(('0.0.0.0', PAIRING_PORT))
    sock.listen(1)
    
    print(f"Starting TCP pairing server on port {PAIRING_PORT}. Expected PIN: {PIN}")
    while True:
        conn, addr = sock.accept()
        with conn:
            data = conn.recv(1024).decode('utf-8').strip()
            print(f"Received pairing request from {addr[0]}: {data}")
            
            parts = data.split(':')
            if len(parts) >= 3 and parts[0] == "PAIR" and parts[1] == PIN:
                print("PIN accepted. Sending SUCCESS.")
                conn.sendall(b"SUCCESS\n")
            else:
                print("PIN rejected.")
                conn.sendall(b"INVALID_PIN\n")

if __name__ == "__main__":
    t1 = threading.Thread(target=broadcast_loop, daemon=True)
    t2 = threading.Thread(target=pairing_server, daemon=True)
    t1.start()
    t2.start()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Shutting down simulated Android app")
