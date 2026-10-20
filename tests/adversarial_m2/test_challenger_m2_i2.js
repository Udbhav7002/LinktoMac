/**
 * Challenger Stress Test for Milestone M2 Iteration 2
 * Objective 1: In-flight recovery cancellation race condition
 * Objective 2: Endpoint parsing stress test for parseRelayEndpoint(res)
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

const REPO_ROOT = path.resolve(__dirname, '../..');
const SCRIPT_PATH = path.join(REPO_ROOT, 'orchestrator/ui/script.js');

console.log("===================================================================");
console.log("   CHALLENGER STRESS TESTS: M2 ITERATION 2 ADVERSARIAL VALIDATION  ");
console.log("===================================================================");

// ----------------------------------------------------------------------
// Harness Factory
// ----------------------------------------------------------------------
class MockFirestore {
    constructor() {
        this.store = new Map();
        this.writes = [];
    }
    collection(colName) {
        const self = this;
        return {
            doc(docId) {
                const docPath = `${colName}/${docId}`;
                return {
                    path: docPath,
                    set(data, options) {
                        self.writes.push({
                            timestamp: Date.now(),
                            path: docPath,
                            data: JSON.parse(JSON.stringify(data)),
                            options
                        });
                        const existing = self.store.get(docPath) || {};
                        const merged = (options && options.merge) ? { ...existing, ...data } : data;
                        self.store.set(docPath, merged);
                        return Promise.resolve();
                    },
                    collection(subColName) {
                        return {
                            doc(subDocId) {
                                const subDocPath = `${docPath}/${subColName}/${subDocId}`;
                                return {
                                    path: subDocPath,
                                    set(data, options) {
                                        self.writes.push({
                                            timestamp: Date.now(),
                                            path: subDocPath,
                                            data: JSON.parse(JSON.stringify(data)),
                                            options
                                        });
                                        const existing = self.store.get(subDocPath) || {};
                                        const merged = (options && options.merge) ? { ...existing, ...data } : data;
                                        self.store.set(subDocPath, merged);
                                        return Promise.resolve();
                                    },
                                    onSnapshot() {
                                        return () => {};
                                    }
                                };
                            }
                        };
                    }
                };
            }
        };
    }
}

function createHarness() {
    const mockDb = new MockFirestore();
    const localStorageStore = {
        "linktomac_user": JSON.stringify({ uid: "challenger_uid_42", email: "challenger@test.org" })
    };

    const elements = {};
    function getOrCreateElement(id) {
        if (!elements[id]) {
            elements[id] = {
                id,
                innerText: '',
                className: '',
                classList: {
                    _classes: new Set(),
                    add(c) { this._classes.add(c); },
                    remove(c) { this._classes.delete(c); },
                    contains(c) { return this._classes.has(c); }
                },
                setAttribute() {},
                getAttribute() { return null; },
                addEventListener(evt, h) { this[`on${evt}`] = h; },
                querySelector() { return { classList: { add() {}, remove() {} }, dataset: {} }; },
                querySelectorAll() { return []; },
                onclick: null
            };
        }
        return elements[id];
    }

    let mockApi = {
        connect_device: () => Promise.resolve({ success: true, mode: "local" }),
        stop_mirror: () => Promise.resolve({ success: true }),
        stop_remote_session: () => Promise.resolve({ success: true })
    };

    const sandbox = {
        window: {
            addEventListener() {},
            pywebview: { api: mockApi }
        },
        document: {
            getElementById: getOrCreateElement,
            createElement: () => ({ appendChild() {}, setAttribute() {} })
        },
        localStorage: {
            getItem: (k) => localStorageStore[k] || null,
            setItem: (k, v) => { localStorageStore[k] = v; },
            removeItem: (k) => { delete localStorageStore[k]; }
        },
        firebase: {
            initializeApp() {},
            firestore: Object.assign(() => mockDb, {
                FieldValue: {
                    serverTimestamp: () => "MOCK_TIMESTAMP"
                }
            })
        },
        console: {
            log: () => {},
            error: () => {},
            warn: () => {}
        },
        setTimeout: setTimeout,
        clearTimeout: clearTimeout,
        setInterval: () => {},
        clearInterval: () => {}
    };
    sandbox.window.pywebview = { api: mockApi };

    const scriptCode = fs.readFileSync(SCRIPT_PATH, 'utf8');
    const context = vm.createContext(sandbox);
    vm.runInContext(scriptCode, context);

    return {
        sandbox,
        context,
        mockDb,
        elements,
        getActiveSession() {
            return vm.runInContext("activeSession", context);
        },
        getRecoveryCancelled() {
            return vm.runInContext("recoveryCancelled", context);
        },
        getRecoveryToken() {
            return vm.runInContext("recoveryToken", context);
        },
        getReconnectTimer() {
            return vm.runInContext("reconnectTimer", context);
        },
        setApi(api) {
            sandbox.window.pywebview.api = api;
        }
    };
}

// ======================================================================
// PART 1: Endpoint Parsing Stress Tests (parseRelayEndpoint)
// ======================================================================
async function runEndpointParsingTests() {
    console.log("\n>>> [SECTION 1] Testing parseRelayEndpoint(res) directly from script.js...");

    const { sandbox } = createHarness();
    const parse = sandbox.parseRelayEndpoint;
    assert.strictEqual(typeof parse, "function", "parseRelayEndpoint must be exported to global sandbox scope");

    const testCases = [
        // IPv4 standard
        {
            name: "IPv4 standard tunnel address",
            input: { session_code: "a.pinggy.io:44321" },
            expectedHost: "a.pinggy.io",
            expectedPort: 44321
        },
        {
            name: "IPv4 dotted quad with port",
            input: { session_code: "192.168.1.100:27189" },
            expectedHost: "192.168.1.100",
            expectedPort: 27189
        },
        {
            name: "IPv4 padded with whitespace",
            input: { session_code: "   a.pinggy.io   :   44321   " },
            expectedHost: "a.pinggy.io",
            expectedPort: 44321
        },

        // Bracketed IPv6
        {
            name: "Bracketed IPv6 standard",
            input: { session_code: "[2001:db8::1]:44321" },
            expectedHost: "2001:db8::1",
            expectedPort: 44321
        },
        {
            name: "Bracketed IPv6 full 8-group",
            input: { session_code: "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8080" },
            expectedHost: "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            expectedPort: 8080
        },
        {
            name: "Bracketed IPv6 loopback [::1]:27189",
            input: { session_code: "[::1]:27189" },
            expectedHost: "::1",
            expectedPort: 27189
        },
        {
            name: "Bracketed IPv6 padded with whitespace",
            input: { session_code: "  [2001:db8::1]  :  44321  " },
            expectedHost: "2001:db8::1",
            expectedPort: 44321
        },
        {
            name: "Bracketed IPv6 with missing port",
            input: { session_code: "[2001:db8::1]" },
            expectedHost: "2001:db8::1",
            expectedPort: 0
        },
        {
            name: "Bracketed IPv6 with trailing colon but no port",
            input: { session_code: "[2001:db8::1]:" },
            expectedHost: "2001:db8::1",
            expectedPort: 0
        },
        {
            name: "Bracketed IPv6 with non-numeric port",
            input: { session_code: "[2001:db8::1]:nonnum" },
            expectedHost: "2001:db8::1",
            expectedPort: 0
        },

        // Unbracketed IPv6
        {
            name: "Unbracketed IPv6 with port (2001:db8::1:44321)",
            input: { session_code: "2001:db8::1:44321" },
            expectedHost: "2001:db8::1",
            expectedPort: 44321
        },
        {
            name: "Unbracketed IPv6 full 8-group with port",
            input: { session_code: "2001:0db8:85a3:0000:0000:8a2e:0370:7334:44321" },
            expectedHost: "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            expectedPort: 44321
        },
        {
            name: "Unbracketed IPv6 loopback (::1:27189)",
            input: { session_code: "::1:27189" },
            expectedHost: "::1",
            expectedPort: 27189
        },

        // Malformed / Boundary inputs
        {
            name: "Missing port entirely (no colon)",
            input: { session_code: "a.pinggy.io" },
            expectedHost: "",
            expectedPort: 0
        },
        {
            name: "Missing port (trailing colon)",
            input: { session_code: "a.pinggy.io:" },
            expectedHost: "a.pinggy.io",
            expectedPort: 0
        },
        {
            name: "Missing host (leading colon)",
            input: { session_code: ":44321" },
            expectedHost: "",
            expectedPort: 44321
        },
        {
            name: "Non-numeric port",
            input: { session_code: "a.pinggy.io:invalid_port" },
            expectedHost: "a.pinggy.io",
            expectedPort: 0
        },
        {
            name: "Unclosed bracket with port",
            input: { session_code: "[:44321" },
            expectedHost: "[",
            expectedPort: 44321
        },
        {
            name: "Empty string session_code",
            input: { session_code: "" },
            expectedHost: "",
            expectedPort: 0
        },
        {
            name: "Null input",
            input: null,
            expectedHost: "",
            expectedPort: 0
        },
        {
            name: "Undefined input",
            input: undefined,
            expectedHost: "",
            expectedPort: 0
        },
        {
            name: "Empty object input",
            input: {},
            expectedHost: "",
            expectedPort: 0
        },
        {
            name: "Non-string session_code (number)",
            input: { session_code: 8080 },
            expectedHost: "",
            expectedPort: 0
        },

        // Explicit host & port fields (override or primary)
        {
            name: "Explicit host & port provided by backend",
            input: { host: "relay.linktomac.net", port: 9000, session_code: "ignore:123" },
            expectedHost: "relay.linktomac.net",
            expectedPort: 9000
        },
        {
            name: "Explicit host bracketed IPv6",
            input: { host: "[2001:db8::1]", port: 44321 },
            expectedHost: "2001:db8::1",
            expectedPort: 44321
        },
        {
            name: "Explicit host string port",
            input: { host: "10.0.0.1", port: "5555" },
            expectedHost: "10.0.0.1",
            expectedPort: 5555
        },
        {
            name: "Explicit host non-numeric port",
            input: { host: "10.0.0.1", port: "bad" },
            expectedHost: "10.0.0.1",
            expectedPort: 0
        }
    ];

    let passed = 0;
    for (const tc of testCases) {
        const result = parse(tc.input);
        assert.strictEqual(
            result.relayHost,
            tc.expectedHost,
            `FAIL [${tc.name}]: expected host "${tc.expectedHost}", got "${result.relayHost}"`
        );
        assert.strictEqual(
            result.relayPort,
            tc.expectedPort,
            `FAIL [${tc.name}]: expected port ${tc.expectedPort}, got ${result.relayPort}`
        );
        console.log(`  ✓ Passed: ${tc.name} -> host="${result.relayHost}", port=${result.relayPort}`);
        passed++;
    }
    console.log(`\n  All ${passed} endpoint parsing test cases PASSED!`);
}

// ======================================================================
// PART 2: In-Flight Recovery Cancellation Race Stress Tests
// ======================================================================
async function runCancellationRaceTests() {
    console.log("\n>>> [SECTION 2] Stress-testing in-flight recovery cancellation race condition...");
    const sessionDocPath = "users/challenger_uid_42/sessions/current";

    // ------------------------------------------------------------------
    // Race Scenario 2.1: Late-resolving success promise AFTER cancel
    // ------------------------------------------------------------------
    console.log("\n  [Scenario 2.1] Late-resolving success promise after user clicks cancel...");
    {
        const { sandbox, mockDb, elements, setApi, getActiveSession } = createHarness();

        let resolvePromise;
        const pendingPromise = new Promise(resolve => {
            resolvePromise = resolve;
        });

        let stopMirrorCalled = false;
        let stopRemoteCalled = false;

        setApi({
            connect_device: () => pendingPromise,
            stop_mirror: () => { stopMirrorCalled = true; return Promise.resolve(); },
            stop_remote_session: () => { stopRemoteCalled = true; return Promise.resolve(); }
        });

        // Step 1: Initiate recovery
        sandbox.attemptSessionRecovery("Pixel 10a");

        // Verify recovery HUD is shown
        const hud = elements["session-recovery-hud"];
        assert.ok(hud && !hud.classList.contains("hidden"), "Recovery HUD must be visible");

        // Step 2: Cancel recovery while connect_device is in-flight
        const cancelBtn = elements["btn-recovery-cancel"];
        assert.ok(cancelBtn && cancelBtn.onclick, "Cancel button must exist with onclick handler");
        cancelBtn.onclick();

        // Step 3: Verify state immediately post-cancellation
        assert.strictEqual(getActiveSession(), null, "activeSession must be null immediately upon cancel");
        assert.strictEqual(hud.classList.contains("hidden"), true, "HUD must be hidden upon cancel");
        assert.strictEqual(stopMirrorCalled, true, "stop_mirror() must be invoked on cancel");
        assert.strictEqual(stopRemoteCalled, true, "stop_remote_session() must be invoked on cancel");

        const sessionAfterCancel = mockDb.store.get(sessionDocPath);
        assert.ok(sessionAfterCancel, "Firestore session doc must exist after cancel");
        assert.strictEqual(sessionAfterCancel.active, false, "Firestore session must be active: false");
        assert.strictEqual(sessionAfterCancel.host, null, "Firestore session host must be null");
        assert.strictEqual(sessionAfterCancel.port, 0, "Firestore session port must be 0");

        const writesCountAtCancel = mockDb.writes.length;

        // Step 4: Late-resolving connect_device promise resolves successfully with remote mode
        resolvePromise({
            success: true,
            mode: "remote",
            host: "late.pinggy.io",
            port: 44321,
            session_code: "late.pinggy.io:44321"
        });

        // Wait for promise microtasks and macrotasks
        await new Promise(r => setTimeout(r, 60));

        // Step 5: VERIFY NO STATE RESURRECTION
        assert.strictEqual(
            getActiveSession(),
            null,
            "CRITICAL: activeSession was resurrected by late promise! Expected null."
        );

        const sessionAfterLateResolve = mockDb.store.get(sessionDocPath);
        assert.strictEqual(
            sessionAfterLateResolve.active,
            false,
            "CRITICAL: Firestore was overwritten by late promise! Expected active: false."
        );
        assert.strictEqual(sessionAfterLateResolve.host, null);
        assert.strictEqual(sessionAfterLateResolve.port, 0);

        // Verify no extra writes were made after cancel
        assert.strictEqual(
            mockDb.writes.length,
            writesCountAtCancel,
            `CRITICAL: ${mockDb.writes.length - writesCountAtCancel} spurious write(s) occurred after cancellation!`
        );

        console.log("  ✓ Late success promise dropped safely: activeSession is null, Firestore remains active: false, 0 spurious writes.");
    }

    // ------------------------------------------------------------------
    // Race Scenario 2.2: Late-rejecting promise AFTER cancel
    // ------------------------------------------------------------------
    console.log("\n  [Scenario 2.2] Late-rejecting promise (network error / crash) after user clicks cancel...");
    {
        const { sandbox, mockDb, elements, setApi, getActiveSession, getReconnectTimer } = createHarness();

        let rejectPromise;
        const pendingPromise = new Promise((_, reject) => {
            rejectPromise = reject;
        });

        setApi({
            connect_device: () => pendingPromise,
            stop_mirror: () => Promise.resolve(),
            stop_remote_session: () => Promise.resolve()
        });

        sandbox.attemptSessionRecovery("Pixel 10a");

        const cancelBtn = elements["btn-recovery-cancel"];
        cancelBtn.onclick();

        assert.strictEqual(getActiveSession(), null);
        const writesCountAtCancel = mockDb.writes.length;

        // Reject promise late
        rejectPromise(new Error("Peer connection reset by peer"));
        await new Promise(r => setTimeout(r, 60));

        // Verify no retry timer scheduled and no state resurrection
        assert.strictEqual(getActiveSession(), null);
        assert.strictEqual(getReconnectTimer(), null, "reconnectTimer must not be rescheduled after cancel");
        assert.strictEqual(mockDb.writes.length, writesCountAtCancel, "No spurious writes on late rejection");

        console.log("  ✓ Late rejection dropped safely: activeSession is null, no reconnectTimer rescheduled.");
    }

    // ------------------------------------------------------------------
    // Race Scenario 2.3: Rapid Cancel & New Connection Interleaving
    // ------------------------------------------------------------------
    console.log("\n  [Scenario 2.3] Cancel recovery -> Start new connection -> Old promise resolves late...");
    {
        const { sandbox, mockDb, elements, setApi, getActiveSession } = createHarness();

        let resolveOldPromise;
        const oldPendingPromise = new Promise(resolve => {
            resolveOldPromise = resolve;
        });

        setApi({
            connect_device: (arg) => {
                if (arg && arg.name === "Old Phone") return oldPendingPromise;
                return Promise.resolve({
                    success: true,
                    mode: "local",
                    name: "New Phone"
                });
            },
            stop_mirror: () => Promise.resolve(),
            stop_remote_session: () => Promise.resolve()
        });

        // 1. Start recovery for "Old Phone"
        sandbox.attemptSessionRecovery("Old Phone");

        // 2. Cancel recovery for "Old Phone"
        const cancelBtn = elements["btn-recovery-cancel"];
        cancelBtn.onclick();
        assert.strictEqual(getActiveSession(), null);

        // 3. Start a new connection for "New Phone"
        const newDeviceData = { name: "New Phone", ip: "192.168.1.200", uid: "challenger_uid_42" };
        sandbox.window.connectDevice(newDeviceData);
        await new Promise(r => setTimeout(r, 40));

        const active1 = getActiveSession();
        assert.ok(active1, "New session must be active");
        assert.strictEqual(active1.name, "New Phone");
        assert.strictEqual(active1.mode, "local");

        // 4. Old recovery promise resolves late with "Old Phone" remote session
        resolveOldPromise({
            success: true,
            mode: "remote",
            name: "Old Phone",
            host: "old.pinggy.io",
            port: 33333
        });
        await new Promise(r => setTimeout(r, 60));

        // 5. Verify the new connection was NOT corrupted or hijacked by the old promise!
        const active2 = getActiveSession();
        assert.strictEqual(
            active2.name,
            "New Phone",
            "CRITICAL: Old promise hijacked activeSession from new connection!"
        );
        assert.strictEqual(active2.mode, "local");

        const currentDoc = mockDb.store.get(sessionDocPath);
        assert.strictEqual(currentDoc.active, true);
        assert.strictEqual(currentDoc.mode, "local");
        assert.strictEqual(currentDoc.host, "local");

        console.log("  ✓ Monotonic recoveryToken prevented old recovery promise from hijacking new active session.");
    }
}

// ======================================================================
// MAIN RUNNER
// ======================================================================
async function main() {
    await runEndpointParsingTests();
    await runCancellationRaceTests();
    console.log("\n===================================================================");
    console.log("   ALL CHALLENGER EMPIRICAL STRESS TESTS PASSED SUCCESSFULLY!      ");
    console.log("===================================================================\n");
}

main().catch(err => {
    console.error("\n❌ CHALLENGER STRESS TEST FAILED:", err);
    process.exit(1);
});
