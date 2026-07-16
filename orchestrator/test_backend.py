import time
from linktomac.backend import LinktoMacBackend

def on_devices_found(devices):
    print("\n--- Discovered Devices ---")
    for d in devices:
        print(f"[{d['device_id']}] {d['name']} @ {d['ip']}")
    print("--------------------------\n")

if __name__ == "__main__":
    backend = LinktoMacBackend()
    backend.set_callback(on_devices_found)
    
    print("Saved devices:")
    for d in backend.get_paired_devices():
        print(f"  - {d['name']} ({d['ip']})")

    print("\nStarting discovery...")
    backend.start_discovery()
    
    try:
        time.sleep(2) # Wait for broadcast
        
        devices = list(backend.discovered_devices.values())
        if devices:
            ip = devices[0]['ip']
            print(f"\nAttempting to pair with {ip} using PIN '1234'...")
            success, err = backend.pair_device(ip, "1234")
            if success:
                print("Pairing successful!")
                print("Updated saved devices:")
                for d in backend.get_paired_devices():
                    print(f"  - {d['name']} ({d['ip']})")
            else:
                print(f"Pairing failed: {err}")
        else:
            print("No devices discovered.")
            
        time.sleep(1)
    finally:
        backend.stop_discovery()
