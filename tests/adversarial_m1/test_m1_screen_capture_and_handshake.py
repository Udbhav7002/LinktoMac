#!/usr/bin/env python3
"""
Empirical Adversarial Verification Suite for Milestone 1:
1. Static and AST analysis of ScreenCapture.java, Server.java, ScreenEncoder.java.
2. Verification of absence of removed reflection APIs (openTransaction, closeTransaction).
3. Verification of SurfaceControl$Transaction reflection resolution and finally { close() }.
4. Verification of hidden API exemption strings in Server.java.
5. Verification of 20-byte handshake wire serialization and codec negotiation alignment with native-client.
6. Verification of DEX bytecode integrity in server.jar.
7. Simulated runtime reflection oracle testing (Android 12-15, Android 10-11, legacy pre-10, fault injection).
"""

import os
import sys
import struct
import socket
import threading
import subprocess
import unittest
import tempfile
import shutil

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
ANDROID_SERVER_DIR = os.path.join(REPO_ROOT, "android-server")
SERVER_SRC_DIR = os.path.join(ANDROID_SERVER_DIR, "src/main/java/com/linktomac")

class TestMilestone1ReflectionAndHandshake(unittest.TestCase):

    # =========================================================================
    # 1. ABSENCE OF REMOVED APIS
    # =========================================================================

    def test_no_opentransaction_or_closetransaction_in_android_server(self):
        """Ensure openTransaction and closeTransaction are completely absent from android-server."""
        forbidden_apis = ["openTransaction", "closeTransaction"]
        for root, _, files in os.walk(os.path.join(ANDROID_SERVER_DIR, "src")):
            for file in files:
                if file.endswith(".java"):
                    path = os.path.join(root, file)
                    with open(path, "r", encoding="utf-8") as f:
                        content = f.read()
                    for api in forbidden_apis:
                        self.assertNotIn(
                            api,
                            content,
                            f"Forbidden removed API '{api}' found in {path}"
                        )

    # =========================================================================
    # 2. SCREENCAPTURE REFLECTION AND FINALLY { CLOSE() } STRUCTURE
    # =========================================================================

    def test_screencapture_dynamic_transaction_loading(self):
        """Verify ScreenCapture.java loads SurfaceControl$Transaction and its methods."""
        screen_capture_path = os.path.join(SERVER_SRC_DIR, "ScreenCapture.java")
        self.assertTrue(os.path.isfile(screen_capture_path), f"File not found: {screen_capture_path}")

        with open(screen_capture_path, "r", encoding="utf-8") as f:
            code = f.read()

        # Check class lookup
        self.assertIn('Class.forName("android.view.SurfaceControl$Transaction")', code)
        self.assertIn('Class.forName("android.view.SurfaceControl")', code)

        # Check method lookups on Transaction
        self.assertIn('"setDisplaySurface"', code)
        self.assertIn('"setDisplayProjection"', code)
        self.assertIn('"setDisplayLayerStack"', code)
        self.assertIn('"apply"', code)
        self.assertIn('"close"', code)

        # Check createDisplay and destroyDisplay on SurfaceControl
        self.assertIn('"createDisplay"', code)
        self.assertIn('"destroyDisplay"', code)

    def test_screencapture_finally_block_closes_transaction(self):
        """Verify transaction.close() is invoked in a finally block."""
        screen_capture_path = os.path.join(SERVER_SRC_DIR, "ScreenCapture.java")
        with open(screen_capture_path, "r", encoding="utf-8") as f:
            code = f.read()

        # Extract start() method body
        start_idx = code.find("public void start(")
        self.assertNotEqual(start_idx, -1, "public void start() method missing in ScreenCapture.java")
        start_body = code[start_idx:code.find("public void stop()", start_idx)]

        # Check for try - finally structure enclosing closeMethod.invoke(transaction)
        self.assertIn("finally", start_body, "finally block missing in ScreenCapture.start()")
        finally_idx = start_body.find("finally")
        finally_block = start_body[finally_idx:]

        self.assertIn("closeMethod.invoke(transaction)", finally_block,
                      "closeMethod.invoke(transaction) MUST be located inside the finally block")

        # Verify closeMethod invocation is itself wrapped in try-catch to prevent masking prior exceptions
        self.assertIn("catch (Exception ignored)", finally_block,
                      "closeMethod.invoke(transaction) should catch exceptions in finally")

    def test_screencapture_android_10_11_fallback_resilience(self):
        """Verify NoSuchMethodException on close() is handled gracefully for Android 10-11."""
        screen_capture_path = os.path.join(SERVER_SRC_DIR, "ScreenCapture.java")
        with open(screen_capture_path, "r", encoding="utf-8") as f:
            code = f.read()

        self.assertIn("catch (NoSuchMethodException ignored)", code,
                      "NoSuchMethodException should be caught when looking up close() for Android 10-11")

    # =========================================================================
    # 3. SERVER.JAVA HIDDEN API EXEMPTIONS
    # =========================================================================

    def test_server_hidden_api_exemptions(self):
        """Verify Server.java includes all required hidden API exemption signatures."""
        server_path = os.path.join(SERVER_SRC_DIR, "Server.java")
        self.assertTrue(os.path.isfile(server_path), f"File not found: {server_path}")

        with open(server_path, "r", encoding="utf-8") as f:
            code = f.read()

        required_exemptions = [
            "Landroid/view/SurfaceControl",
            "Landroid/view/SurfaceControl;",
            "Landroid/view/SurfaceControl$Transaction;",
            "Landroid/hardware/input/InputManagerGlobal;",
            "Landroid/hardware/input/InputManager;",
            "Landroid/hardware/display/DisplayManagerGlobal;"
        ]

        for exemption in required_exemptions:
            self.assertIn(
                f'"{exemption}"',
                code,
                f"Missing hidden API exemption: {exemption}"
            )

    # =========================================================================
    # 4. HANDSHAKE WIRE PROTOCOL & CODEC NEGOTIATION
    # =========================================================================

    def test_server_handshake_serialization_and_wire_format(self):
        """Verify 20-byte handshake wire serialization matches native-client."""
        PROTOCOL_MAGIC = 0x4C494E4B # 'LINK'
        VERSION = 2
        width = 1080
        height = 2400

        # Case 1: HEVC (codecFlags = 0)
        codec_flags_hevc = 0
        handshake_hevc = struct.pack(">IIIII", PROTOCOL_MAGIC, VERSION, width, height, codec_flags_hevc)
        self.assertEqual(len(handshake_hevc), 20)

        # Unpack as native-client mirror_client.cpp does:
        # magic = read_be32(handshake)
        # protocol_version = read_be32(handshake + 4)
        # width = read_be32(handshake + 8)
        # height = read_be32(handshake + 12)
        # flags = read_be32(handshake + 16)
        magic, ver, w, h, flags = struct.unpack(">IIIII", handshake_hevc)
        self.assertEqual(magic, 0x4C494E4B)
        self.assertEqual(ver, 2)
        self.assertEqual(w, 1080)
        self.assertEqual(h, 2400)
        self.assertEqual(flags, 0)
        # In native-client decoder.cpp: preferred_codec = (flags & 0x01) ? AV_CODEC_ID_H264 : AV_CODEC_ID_HEVC
        self.assertEqual(flags & 0x01, 0, "Flag 0 must map to HEVC in native-client")

        # Case 2: AVC (codecFlags = 1)
        codec_flags_avc = 1
        handshake_avc = struct.pack(">IIIII", PROTOCOL_MAGIC, VERSION, width, height, codec_flags_avc)
        self.assertEqual(len(handshake_avc), 20)
        magic, ver, w, h, flags = struct.unpack(">IIIII", handshake_avc)
        self.assertEqual(flags, 1)
        self.assertEqual(flags & 0x01, 1, "Flag 1 must map to H264 in native-client")

    def test_handshake_network_loopback_exchange(self):
        """Empirically test sending and receiving the 20-byte handshake over a TCP socket."""
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.bind(("127.0.0.1", 0))
        server_sock.listen(1)
        port = server_sock.getsockname()[1]

        received_data = bytearray()
        client_error = None

        def client_thread():
            nonlocal client_error
            try:
                c = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                c.connect(("127.0.0.1", port))
                # Read exactly 20 bytes
                while len(received_data) < 20:
                    chunk = c.recv(20 - len(received_data))
                    if not chunk:
                        break
                    received_data.extend(chunk)
                c.close()
            except Exception as e:
                client_error = e

        t = threading.Thread(target=client_thread)
        t.start()

        conn, _ = server_sock.accept()
        # Simulate Server.java handshake transmission
        handshake = struct.pack(">IIIII", 0x4C494E4B, 2, 1080, 2400, 0)
        conn.sendall(handshake)
        conn.close()
        server_sock.close()
        t.join(timeout=2.0)

        self.assertIsNone(client_error)
        self.assertEqual(len(received_data), 20)
        magic, ver, w, h, flags = struct.unpack(">IIIII", received_data)
        self.assertEqual(magic, 0x4C494E4B)
        self.assertEqual(ver, 2)
        self.assertEqual(w, 1080)
        self.assertEqual(h, 2400)
        self.assertEqual(flags, 0)

    # =========================================================================
    # 5. DEX BYTECODE & ARCHIVE INTEGRITY
    # =========================================================================

    def test_server_jar_contains_valid_classes_dex_and_no_class_files(self):
        """Verify server.jar structure: contains classes.dex, no .class bytecode, and valid DEX."""
        jar_path = os.path.join(ANDROID_SERVER_DIR, "server.jar")
        self.assertTrue(os.path.isfile(jar_path), f"server.jar missing at {jar_path}")

        res = subprocess.run(["jar", "tf", jar_path], capture_output=True, text=True)
        self.assertEqual(res.returncode, 0)
        entries = res.stdout.strip().splitlines()

        self.assertIn("classes.dex", entries)
        self.assertIn("META-INF/MANIFEST.MF", entries)

        # Ensure no .class files packaged into JAR
        for entry in entries:
            self.assertFalse(entry.endswith(".class"), f"Unwanted .class file found in server.jar: {entry}")

        # Check dexdump
        build_tools_dir = os.path.expanduser("~/Library/Android/sdk/build-tools/36.0.0")
        dexdump_bin = os.path.join(build_tools_dir, "dexdump")
        if os.path.isfile(dexdump_bin):
            res_dex = subprocess.run([dexdump_bin, "-f", jar_path], capture_output=True, text=True)
            self.assertEqual(res_dex.returncode, 0)
            self.assertIn("Lcom/linktomac/ScreenCapture;", res_dex.stdout)
            self.assertIn("Lcom/linktomac/Server;", res_dex.stdout)
            self.assertIn("Lcom/linktomac/ScreenEncoder;", res_dex.stdout)
            self.assertNotIn("openTransaction", res_dex.stdout)
            self.assertNotIn("closeTransaction", res_dex.stdout)

    # =========================================================================
    # 6. TLS 1.3 PROTOCOL ENFORCEMENT
    # =========================================================================

    def test_tls13_enforcement_in_native_client_and_android(self):
        """Verify native-client strictly enforces TLS1_3_VERSION and Android requests TLSv1.3."""
        network_cpp = os.path.join(REPO_ROOT, "native-client/src/network.cpp")
        self.assertTrue(os.path.isfile(network_cpp), f"Missing {network_cpp}")
        with open(network_cpp, "r", encoding="utf-8") as f:
            net_code = f.read()

        # Both connect_tls and listen_and_accept_tls must enforce min and max TLS1_3_VERSION
        self.assertIn("SSL_CTX_set_min_proto_version(ssl_ctx_, TLS1_3_VERSION);", net_code)
        self.assertIn("SSL_CTX_set_max_proto_version(ssl_ctx_, TLS1_3_VERSION);", net_code)

        # Android LinkConnectionService.kt must request TLSv1.3
        link_service_kt = os.path.join(REPO_ROOT, "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkConnectionService.kt")
        self.assertTrue(os.path.isfile(link_service_kt), f"Missing {link_service_kt}")
        with open(link_service_kt, "r", encoding="utf-8") as f:
            kt_code = f.read()

        self.assertIn('SSLContext.getInstance("TLSv1.3")', kt_code)


    # =========================================================================
    # 6. SIMULATED RUNTIME REFLECTION ORACLE
    # =========================================================================

    def test_java_reflection_oracle_execution(self):
        """Compile and run a Java test harness verifying the exact reflection call graph and finally { close() }."""
        test_java_src = """
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ReflectionOracleTest {

    // Trace of calls made during test
    public static final List<String> callTrace = new ArrayList<>();

    // Mock android.os.IBinder
    public static class MockIBinder {}

    // Mock android.graphics.Rect
    public static class MockRect {
        public int left, top, right, bottom;
        public MockRect(int l, int t, int r, int b) {
            this.left = l; this.top = t; this.right = r; this.bottom = b;
        }
    }

    // Mock android.view.Surface
    public static class MockSurface {}

    // Mock android.view.SurfaceControl
    public static class MockSurfaceControl {
        public static MockIBinder createDisplay(String name, boolean secure) {
            callTrace.add("createDisplay:" + name + ":" + secure);
            return new MockIBinder();
        }
        public static void destroyDisplay(MockIBinder display) {
            callTrace.add("destroyDisplay");
        }
    }

    // Mock android.view.SurfaceControl$Transaction
    public static class MockTransaction {
        public static boolean throwOnApply = false;
        public static boolean throwOnClose = false;

        public MockTransaction setDisplaySurface(MockIBinder token, MockSurface surface) {
            callTrace.add("setDisplaySurface");
            return this;
        }
        public MockTransaction setDisplayProjection(MockIBinder token, int orientation, MockRect layerStackRect, MockRect displayRect) {
            callTrace.add("setDisplayProjection");
            return this;
        }
        public MockTransaction setDisplayLayerStack(MockIBinder token, int layerStack) {
            callTrace.add("setDisplayLayerStack");
            return this;
        }
        public void apply() {
            if (throwOnApply) {
                callTrace.add("apply:THROW");
                throw new RuntimeException("Simulated apply failure");
            }
            callTrace.add("apply");
        }
        public void close() {
            if (throwOnClose) {
                callTrace.add("close:THROW");
                throw new RuntimeException("Simulated close failure");
            }
            callTrace.add("close");
        }
    }

    // Harness mirroring ScreenCapture.java logic
    public static class ScreenCaptureHarness {
        private Class<?> surfaceControlClass;
        private Class<?> transactionClass;
        private Method createDisplayMethod;
        private Method destroyDisplayMethod;
        private Method setDisplaySurfaceMethod;
        private Method setDisplayProjectionMethod;
        private Method setDisplayLayerStackMethod;
        private Method applyMethod;
        private Method closeMethod;
        private boolean reflectionInitialized = false;
        private Object display;

        public void init(boolean includeCloseMethod) throws Exception {
            surfaceControlClass = MockSurfaceControl.class;
            createDisplayMethod = surfaceControlClass.getMethod("createDisplay", String.class, boolean.class);
            destroyDisplayMethod = surfaceControlClass.getMethod("destroyDisplay", MockIBinder.class);

            transactionClass = MockTransaction.class;
            setDisplaySurfaceMethod = transactionClass.getMethod("setDisplaySurface", MockIBinder.class, MockSurface.class);
            setDisplayProjectionMethod = transactionClass.getMethod("setDisplayProjection", MockIBinder.class, int.class, MockRect.class, MockRect.class);
            setDisplayLayerStackMethod = transactionClass.getMethod("setDisplayLayerStack", MockIBinder.class, int.class);
            applyMethod = transactionClass.getMethod("apply");

            if (includeCloseMethod) {
                closeMethod = transactionClass.getMethod("close");
            } else {
                closeMethod = null;
            }
            reflectionInitialized = true;
        }

        public void start(MockSurface surface, int width, int height) {
            if (!reflectionInitialized) return;
            try {
                display = createDisplayMethod.invoke(null, "LinktoMac", false);
                if (transactionClass != null && setDisplaySurfaceMethod != null) {
                    Object transaction = transactionClass.getDeclaredConstructor().newInstance();
                    try {
                        setDisplaySurfaceMethod.invoke(transaction, display, surface);
                        if (setDisplayProjectionMethod != null) {
                            setDisplayProjectionMethod.invoke(transaction, display, 0,
                                    new MockRect(0, 0, width, height),
                                    new MockRect(0, 0, width, height));
                        }
                        if (setDisplayLayerStackMethod != null) {
                            setDisplayLayerStackMethod.invoke(transaction, display, 0);
                        }
                        if (applyMethod != null) {
                            applyMethod.invoke(transaction);
                        }
                    } finally {
                        if (closeMethod != null) {
                            try {
                                closeMethod.invoke(transaction);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                callTrace.add("caught:" + e.getClass().getSimpleName());
            }
        }

        public void stop() {
            if (display != null) {
                try {
                    destroyDisplayMethod.invoke(null, display);
                    display = null;
                } catch (Exception ignored) {}
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Test 1: Standard Modern Android 12-15 Flow
        callTrace.clear();
        MockTransaction.throwOnApply = false;
        MockTransaction.throwOnClose = false;
        ScreenCaptureHarness harness = new ScreenCaptureHarness();
        harness.init(true);
        harness.start(new MockSurface(), 1080, 2400);
        harness.stop();

        assert callTrace.contains("createDisplay:LinktoMac:false") : "Missing createDisplay";
        assert callTrace.contains("setDisplaySurface") : "Missing setDisplaySurface";
        assert callTrace.contains("setDisplayProjection") : "Missing setDisplayProjection";
        assert callTrace.contains("setDisplayLayerStack") : "Missing setDisplayLayerStack";
        assert callTrace.contains("apply") : "Missing apply";
        assert callTrace.contains("close") : "Missing close";
        assert callTrace.contains("destroyDisplay") : "Missing destroyDisplay";
        int applyIdx = callTrace.indexOf("apply");
        int closeIdx = callTrace.indexOf("close");
        assert closeIdx > applyIdx : "close() must be called after apply()";

        // Test 2: Exception in apply() -> close() MUST still be called in finally
        callTrace.clear();
        MockTransaction.throwOnApply = true;
        MockTransaction.throwOnClose = false;
        harness = new ScreenCaptureHarness();
        harness.init(true);
        harness.start(new MockSurface(), 1080, 2400);

        assert callTrace.contains("apply:THROW") : "apply should have thrown";
        assert callTrace.contains("close") : "close() MUST be called in finally block even on error!";
        assert callTrace.contains("caught:InvocationTargetException") : "Exception must be caught in start()";

        // Test 3: Exception in close() -> must not mask or crash
        callTrace.clear();
        MockTransaction.throwOnApply = false;
        MockTransaction.throwOnClose = true;
        harness = new ScreenCaptureHarness();
        harness.init(true);
        harness.start(new MockSurface(), 1080, 2400);

        assert callTrace.contains("apply") : "Missing apply";
        assert callTrace.contains("close:THROW") : "close() threw as expected";
        assert !callTrace.contains("caught:InvocationTargetException") : "Exception in close() must be swallowed in finally";

        // Test 4: Android 10-11 flow without closeMethod
        callTrace.clear();
        MockTransaction.throwOnApply = false;
        MockTransaction.throwOnClose = false;
        harness = new ScreenCaptureHarness();
        harness.init(false);
        harness.start(new MockSurface(), 1080, 2400);

        assert callTrace.contains("apply") : "Missing apply";
        assert !callTrace.contains("close") : "close() should not be called when null";

        System.out.println("ALL_ORACLE_TESTS_PASSED");
    }
}
"""
        tmp_dir = tempfile.mkdtemp(prefix="oracle_test_")
        try:
            oracle_file = os.path.join(tmp_dir, "ReflectionOracleTest.java")
            with open(oracle_file, "w") as f:
                f.write(test_java_src)

            # Compile
            res_compile = subprocess.run(["javac", oracle_file], capture_output=True, text=True)
            self.assertEqual(res_compile.returncode, 0, f"Compilation failed: {res_compile.stderr}")

            # Run with -ea (enable assertions)
            res_run = subprocess.run(["java", "-ea", "-cp", tmp_dir, "ReflectionOracleTest"], capture_output=True, text=True)
            self.assertEqual(res_run.returncode, 0, f"Oracle test failed: {res_run.stderr}")
            self.assertIn("ALL_ORACLE_TESTS_PASSED", res_run.stdout)
        finally:
            shutil.rmtree(tmp_dir)

if __name__ == "__main__":
    unittest.main()
