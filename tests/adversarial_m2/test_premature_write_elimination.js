/**
 * Empirical Stress Test: Premature Write Elimination
 * Tests Objective 3:
 * - Verify that `window.connectDevice` NEVER writes `mode: "local"` to Firestore
 *   before `smart_connect` resolves.
 * - Verify state sequence and write timing under local, remote fallback, and failure paths.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

const REPO_ROOT = path.resolve(__dirname, '../..');
const SCRIPT_PATH = path.join(REPO_ROOT, 'orchestrator/ui/script.js');

console.log("=== Running Premature Write Elimination Tests ===");

class MockFirestoreWithTimeline {
    constructor() {
        this.store = new Map();
        this.writeTimeline = []; // chronological log of writes with timestamps
    }

    collection(colName) {
        const self = this;
        return {
            doc(docId) {
                const docPath = `${colName}/${docId}`;
                return {
                    path: docPath,
                    set(data, options) {
                        const entry = {
                            time: Date.now(),
                            path: docPath,
                            data: JSON.parse(JSON.stringify(data)),
                            options
                        };
                        self.writeTimeline.push(entry);
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
                                        const entry = {
                                            time: Date.now(),
                                            path: subDocPath,
                                            data: JSON.parse(JSON.stringify(data)),
                                            options
                                        };
                                        self.writeTimeline.push(entry);
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
    const mockDb = new MockFirestoreWithTimeline();
    const localStorageStore = {
        "linktomac_user": JSON.stringify({ uid: "user_test_timing_123", email: "timing@example.com" })
    };

    const elements = {};
    function getOrCreateElement(id) {
        if (!elements[id]) {
            elements[id] = {
                id,
                innerText: '',
                className: '',
                classList: {
                    add() {},
                    remove() {},
                    contains() { return false; }
                },
                setAttribute() {},
                getAttribute() { return null; },
                addEventListener() {},
                querySelector() { return { classList: { add() {}, remove() {} }, dataset: {} }; },
                querySelectorAll() { return []; },
                onclick: null
            };
        }
        return elements[id];
    }

    let mockApi = {};

    const sandbox = {
        window: {
            addEventListener() {},
            pywebview: {
                api: mockApi
            }
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
                    serverTimestamp: () => "MOCK_SERVER_TIMESTAMP"
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

    return { sandbox, mockDb, elements, setMockApi: (api) => {
        sandbox.window.pywebview.api = api;
    }};
}

async function testTimingScenarios() {
    const sessionDocPath = "users/user_test_timing_123/sessions/current";

    // ----------------------------------------------------------------------
    // Scenario 1: Pending smart_connect probe (delayed resolution)
    // ----------------------------------------------------------------------
    console.log("\n[Test 3.1] Verifying ZERO writes to sessions/current while smart_connect is in-flight...");
    {
        const { sandbox, mockDb, setMockApi } = createHarness();

        let resolveBackend;
        const pendingPromise = new Promise((resolve) => {
            resolveBackend = resolve;
        });

        setMockApi({
            connect_device: () => pendingPromise
        });

        // Clear any pre-existing startup writes (like account sync on page load)
        mockDb.writeTimeline = [];

        // Trigger connectDevice
        const deviceData = { name: "Pixel 10a", ip: "192.168.1.100", uid: "user_test_timing_123" };
        sandbox.window.connectDevice(deviceData);

        // Verify writeTimeline while smart_connect is still in-flight
        const sessionWritesWhilePending = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(
            sessionWritesWhilePending.length,
            0,
            `PREMATURE WRITE DETECTED: ${sessionWritesWhilePending.length} session write(s) occurred before backend resolution! Details: ${JSON.stringify(sessionWritesWhilePending)}`
        );
        console.log("  ✓ While smart_connect is in-flight: 0 session writes occurred (No premature local write).");

        // Now resolve with local mode
        resolveBackend({ success: true, mode: "local", name: "Pixel 10a" });
        await new Promise(r => setTimeout(r, 20));

        // Now exactly 1 session write should have occurred, with mode: "local"
        const sessionWritesAfter = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(sessionWritesAfter.length, 1, "Expected exactly 1 write after local resolution");
        assert.strictEqual(sessionWritesAfter[0].data.mode, "local");
        assert.strictEqual(sessionWritesAfter[0].data.active, true);
        assert.strictEqual(sessionWritesAfter[0].path, sessionDocPath);
        console.log("  ✓ After smart_connect resolves 'local': Exactly 1 write occurred with mode: 'local'.");
    }

    // ----------------------------------------------------------------------
    // Scenario 2: Remote relay fallback path
    // ----------------------------------------------------------------------
    console.log("\n[Test 3.2] Verifying Remote Relay fallback NEVER writes mode: 'local'...");
    {
        const { sandbox, mockDb, setMockApi } = createHarness();

        let resolveBackend;
        const pendingPromise = new Promise((resolve) => {
            resolveBackend = resolve;
        });

        setMockApi({
            connect_device: () => pendingPromise
        });

        // Clear any pre-existing startup writes
        mockDb.writeTimeline = [];

        const deviceData = { name: "Remote Pixel", ip: "10.0.0.99", uid: "user_test_timing_123" };
        sandbox.window.connectDevice(deviceData);

        // Check before resolution
        const sessionWritesPending2 = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(sessionWritesPending2.length, 0, "No session writes while in-flight");

        // Resolve with remote mode
        resolveBackend({
            success: true,
            mode: "remote",
            name: "Remote Pixel",
            host: "a.pinggy.io",
            port: 39482,
            session_code: "a.pinggy.io:39482"
        });
        await new Promise(r => setTimeout(r, 20));

        // Verify that NO local write ever occurred in the history
        const localWrites = mockDb.writeTimeline.filter(w => w.data.mode === "local");
        assert.strictEqual(localWrites.length, 0, `VIOLATION: Found premature/erroneous local write during remote fallback!`);

        // Verify the only session write was the remote write
        const sessionWritesAfter2 = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(sessionWritesAfter2.length, 1);
        assert.strictEqual(sessionWritesAfter2[0].data.mode, "remote");
        assert.strictEqual(sessionWritesAfter2[0].data.host, "a.pinggy.io");
        assert.strictEqual(sessionWritesAfter2[0].data.port, 39482);
        assert.strictEqual(sessionWritesAfter2[0].data.active, true);
        console.log("  ✓ Remote relay resolution published 'remote' without any intermediate 'local' churn.");
    }

    // ----------------------------------------------------------------------
    // Scenario 3: Connection failure path
    // ----------------------------------------------------------------------
    console.log("\n[Test 3.3] Verifying Connection Failure path NEVER writes mode: 'local'...");
    {
        const { sandbox, mockDb, setMockApi } = createHarness();

        let resolveBackend;
        const pendingPromise = new Promise((resolve) => {
            resolveBackend = resolve;
        });

        setMockApi({
            connect_device: () => pendingPromise
        });

        // Clear any pre-existing startup writes
        mockDb.writeTimeline = [];

        const deviceData = { name: "Offline Phone", ip: "192.168.1.250", uid: "user_test_timing_123" };
        sandbox.window.connectDevice(deviceData);

        // Check before resolution
        const sessionWritesPending3 = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(sessionWritesPending3.length, 0);

        // Resolve with failure
        resolveBackend({
            success: false,
            error: "Device unreachable"
        });
        await new Promise(r => setTimeout(r, 20));

        // Verify that NO local write ever occurred
        const localWrites = mockDb.writeTimeline.filter(w => w.data.mode === "local");
        assert.strictEqual(localWrites.length, 0, "VIOLATION: Local write occurred on failed connection!");

        // Verify failure reset was written
        const sessionWritesAfter3 = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(sessionWritesAfter3.length, 1);
        assert.strictEqual(sessionWritesAfter3[0].data.active, false);
        assert.strictEqual(sessionWritesAfter3[0].data.host, null);
        assert.strictEqual(sessionWritesAfter3[0].data.port, 0);
        console.log("  ✓ Failed connection cleanly reset active: false without writing mode: 'local'.");
    }

    // ----------------------------------------------------------------------
    // Scenario 4: Promise Rejection (exception) path
    // ----------------------------------------------------------------------
    console.log("\n[Test 3.4] Verifying Promise Rejection path NEVER writes mode: 'local'...");
    {
        const { sandbox, mockDb, setMockApi } = createHarness();

        let rejectBackend;
        const pendingPromise = new Promise((_, reject) => {
            rejectBackend = reject;
        });

        setMockApi({
            connect_device: () => pendingPromise
        });

        // Clear any pre-existing startup writes
        mockDb.writeTimeline = [];

        const deviceData = { name: "Crash Phone", ip: "192.168.1.251", uid: "user_test_timing_123" };
        sandbox.window.connectDevice(deviceData);

        // Check before rejection
        const sessionWritesPending4 = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(sessionWritesPending4.length, 0);

        // Reject with exception
        rejectBackend(new Error("Subprocess terminated unexpectedly"));
        await new Promise(r => setTimeout(r, 20));

        // Verify that NO local write ever occurred
        const localWrites = mockDb.writeTimeline.filter(w => w.data.mode === "local");
        assert.strictEqual(localWrites.length, 0, "VIOLATION: Local write occurred on rejected connection!");

        const sessionWritesAfter4 = mockDb.writeTimeline.filter(w => w.path === sessionDocPath);
        assert.strictEqual(sessionWritesAfter4.length, 1);
        assert.strictEqual(sessionWritesAfter4[0].data.active, false);
        console.log("  ✓ Rejected connection cleanly reset active: false without writing mode: 'local'.");
    }
}

testTimingScenarios().then(() => {
    console.log("\n>>> ALL PREMATURE WRITE ELIMINATION TESTS PASSED! <<<\n");
    process.exit(0);
}).catch(err => {
    console.error("\n❌ PREMATURE WRITE ELIMINATION TEST FAILED:", err);
    process.exit(1);
});
