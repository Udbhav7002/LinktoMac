/**
 * Empirical Stress Test: Multi-Tenant Cloud Isolation
 * Tests Objective 1:
 * - Verify that no read or write to `accounts/latest` exists in `orchestrator/ui/script.js` or UI files.
 * - Stress-test concurrent user sign-ins and verify accounts remain strictly isolated to `accounts/{cleanEmail}`.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

const REPO_ROOT = path.resolve(__dirname, '../..');
const SCRIPT_PATH = path.join(REPO_ROOT, 'orchestrator/ui/script.js');
const INDEX_PATH = path.join(REPO_ROOT, 'orchestrator/ui/index.html');
const CSS_PATH = path.join(REPO_ROOT, 'orchestrator/ui/style.css');

console.log("=== Running Multi-Tenant Isolation Tests ===");

// ----------------------------------------------------
// Step 1: Static Code Audit
// ----------------------------------------------------
console.log("\n[Test 1.1] Verifying 0 occurrences of 'accounts/latest' across all UI files...");
const uiFiles = [SCRIPT_PATH, INDEX_PATH, CSS_PATH];
for (const file of uiFiles) {
    const content = fs.readFileSync(file, 'utf8');
    const hasLatest = content.includes('accounts/latest') || content.includes('accounts/"latest"') || content.includes("accounts/'latest'");
    assert.strictEqual(hasLatest, false, `Forbidden 'accounts/latest' found in ${file}`);
    console.log(`  ✓ ${path.basename(file)}: Clean (0 occurrences)`);
}

// Check entire orchestrator/ directory for any stray 'accounts/latest'
function scanDir(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            scanDir(fullPath);
        } else if (entry.isFile() && (entry.name.endsWith('.js') || entry.name.endsWith('.py') || entry.name.endsWith('.html'))) {
            const c = fs.readFileSync(fullPath, 'utf8');
            assert.strictEqual(c.includes('accounts/latest'), false, `Found accounts/latest in ${fullPath}`);
        }
    }
}
scanDir(path.join(REPO_ROOT, 'orchestrator'));
console.log("  ✓ Entire orchestrator/ directory: Clean of accounts/latest");

// ----------------------------------------------------
// Step 2: Runtime Isolation Harness
// ----------------------------------------------------
console.log("\n[Test 1.2] Dynamic Mock Firestore Multi-Tenant Isolation Stress Test...");

// Mock Firestore Store
class MockFirestore {
    constructor() {
        this.store = new Map(); // path -> data
        this.accessLog = [];
    }

    collection(colName) {
        const self = this;
        return {
            doc(docId) {
                const docPath = `${colName}/${docId}`;
                return {
                    path: docPath,
                    set(data, options) {
                        self.accessLog.push({ type: 'write', path: docPath, data });
                        const existing = self.store.get(docPath) || {};
                        const merged = (options && options.merge) ? { ...existing, ...data } : data;
                        self.store.set(docPath, merged);
                        return Promise.resolve();
                    },
                    get() {
                        self.accessLog.push({ type: 'read', path: docPath });
                        const exists = self.store.has(docPath);
                        return Promise.resolve({
                            exists,
                            data: () => self.store.get(docPath)
                        });
                    },
                    collection(subColName) {
                        return {
                            doc(subDocId) {
                                const subDocPath = `${docPath}/${subColName}/${subDocId}`;
                                return {
                                    path: subDocPath,
                                    set(data, options) {
                                        self.accessLog.push({ type: 'write', path: subDocPath, data });
                                        const existing = self.store.get(subDocPath) || {};
                                        const merged = (options && options.merge) ? { ...existing, ...data } : data;
                                        self.store.set(subDocPath, merged);
                                        return Promise.resolve();
                                    },
                                    get() {
                                        self.accessLog.push({ type: 'read', path: subDocPath });
                                        const exists = self.store.has(subDocPath);
                                        return Promise.resolve({
                                            exists,
                                            data: () => self.store.get(subDocPath)
                                        });
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

// Setup VM sandbox with script.js context
function createSandbox() {
    const mockDb = new MockFirestore();
    const localStorageStore = {};

    const elements = {};
    function getOrCreateElement(id) {
        if (!elements[id]) {
            elements[id] = {
                id,
                innerText: '',
                classList: {
                    add: () => {},
                    remove: () => {},
                    contains: () => false
                },
                setAttribute: () => {},
                getAttribute: () => null,
                addEventListener: () => {},
                querySelector: () => ({ classList: { add: () => {}, remove: () => {} } }),
                querySelectorAll: () => []
            };
        }
        return elements[id];
    }

    const sandbox = {
        window: {
            addEventListener: () => {}
        },
        document: {
            getElementById: getOrCreateElement,
            createElement: () => ({ appendChild: () => {}, setAttribute: () => {} })
        },
        localStorage: {
            getItem: (k) => localStorageStore[k] || null,
            setItem: (k, v) => { localStorageStore[k] = v; },
            removeItem: (k) => { delete localStorageStore[k]; }
        },
        firebase: {
            initializeApp: () => {},
            firestore: Object.assign(() => mockDb, {
                FieldValue: {
                    serverTimestamp: () => "MOCK_SERVER_TIMESTAMP"
                }
            })
        },
        console: console,
        setTimeout: setTimeout,
        clearTimeout: clearTimeout,
        setInterval: () => {},
        clearInterval: () => {}
    };
    sandbox.window.addEventListener = () => {};
    sandbox.addEventListener = () => {};

    const scriptCode = fs.readFileSync(SCRIPT_PATH, 'utf8');
    const context = vm.createContext(sandbox);
    vm.runInContext(scriptCode, context);

    return { sandbox, mockDb };
}

// Run concurrent user sign-ins test
async function testConcurrentSignIns() {
    const { sandbox, mockDb } = createSandbox();
    const { publishAccountForPhoneSync, enterDashboard } = sandbox;

    const tenants = [
        { email: "Alice.Smith@Domain.COM", uid: "uid_alice_001", expectedDoc: "accounts/alice.smith@domain.com" },
        { email: "  Bob.Jones@Corp.ORG  ", uid: "uid_bob_002", expectedDoc: "accounts/bob.jones@corp.org" },
        { email: "charlie@gmail.com", uid: "uid_charlie_003", expectedDoc: "accounts/charlie@gmail.com" },
        { email: "DAISY+test@sub.example.co.uk", uid: "uid_daisy_004", expectedDoc: "accounts/daisy+test@sub.example.co.uk" },
        { email: "eve@attacker.evil", uid: "uid_eve_005", expectedDoc: "accounts/eve@attacker.evil" }
    ];

    console.log(`  Spawning 100 interleaved sign-in events across ${tenants.length} distinct tenants...`);
    const promises = [];
    for (let i = 0; i < 100; i++) {
        const tenant = tenants[i % tenants.length];
        const userObj = {
            email: tenant.email,
            uid: tenant.uid,
            displayName: `User ${tenant.uid}`
        };
        // Introduce small jitter
        const p = new Promise(resolve => {
            setTimeout(() => {
                publishAccountForPhoneSync(userObj);
                resolve();
            }, Math.floor(Math.random() * 10));
        });
        promises.push(p);
    }

    await Promise.all(promises);

    // Assertions
    console.log("  Auditing mock Firestore state...");

    // 1. Verify 'accounts/latest' was NEVER accessed
    const latestAccess = mockDb.accessLog.filter(log => log.path.includes("latest"));
    assert.strictEqual(latestAccess.length, 0, `VIOLATION: 'accounts/latest' was accessed ${latestAccess.length} times!`);
    console.log("  ✓ accounts/latest access count is strictly 0");

    // 2. Verify all tenants have strictly separate documents
    for (const tenant of tenants) {
        const docData = mockDb.store.get(tenant.expectedDoc);
        assert.ok(docData, `Missing expected document for ${tenant.expectedDoc}`);
        assert.strictEqual(docData.uid, tenant.uid, `Tenant UID mismatch in ${tenant.expectedDoc}`);
        assert.strictEqual(docData.email, tenant.email.toLowerCase().trim(), `Tenant cleanEmail mismatch in ${tenant.expectedDoc}`);
        assert.strictEqual(docData.updatedAt, "MOCK_SERVER_TIMESTAMP");
    }
    console.log(`  ✓ All ${tenants.length} tenants are strictly isolated in accounts/{cleanEmail}`);

    // 3. Verify total number of accounts documents in store is exactly tenants.length
    const accountKeys = Array.from(mockDb.store.keys()).filter(k => k.startsWith("accounts/"));
    assert.strictEqual(accountKeys.length, tenants.length, `Expected ${tenants.length} account docs, got ${accountKeys.length}`);
    console.log(`  ✓ Exactly ${accountKeys.length} isolated documents exist in accounts/`);

    // 4. Verify boundary conditions for publishAccountForPhoneSync
    console.log("  Testing boundary conditions for publishAccountForPhoneSync (null user, missing email, missing uid)...");
    const preCount = mockDb.accessLog.length;
    publishAccountForPhoneSync(null);
    publishAccountForPhoneSync({});
    publishAccountForPhoneSync({ email: "only_email@test.com" });
    publishAccountForPhoneSync({ uid: "only_uid" });
    publishAccountForPhoneSync({ email: "", uid: "uid_empty_email" });
    assert.strictEqual(mockDb.accessLog.length, preCount, "Invalid user inputs must be ignored without writing to Firestore");
    console.log("  ✓ Malformed / incomplete user objects safely rejected");
}

testConcurrentSignIns().then(() => {
    console.log("\n>>> ALL MULTI-TENANT ISOLATION TESTS PASSED SUCCESSFULLY! <<<\n");
    process.exit(0);
}).catch(err => {
    console.error("\n❌ MULTI-TENANT ISOLATION TEST FAILED:", err);
    process.exit(1);
});
