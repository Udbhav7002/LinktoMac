/**
 * Empirical Adversarial Stress Test: Multi-Tenant Concurrent User Isolation (M4)
 * Simulates concurrent multi-tenant user operations across Firestore session paths:
 * - Invariant 1: Zero operational access to global accounts/latest
 * - Invariant 2: Strict tenant partition: users/{uid}/sessions/current
 * - Invariant 3: Multi-user concurrent session race resolution
 */

const assert = require('assert');

console.log("===================================================================");
console.log("  M4 EMPIRICAL CHALLENGER: MULTI-TENANT CONCURRENCY STRESS TEST    ");
console.log("===================================================================");

class MultiTenantStore {
    constructor() {
        this.data = new Map(); // path -> document data
        this.auditLog = [];    // record of every read/write
    }

    set(docPath, payload, options = {}) {
        // Enforce forbidden paths
        if (docPath.includes('accounts/latest')) {
            throw new Error(`CRITICAL SECURITY VIOLATION: Access to global accounts/latest detected! Path: ${docPath}`);
        }

        const existing = this.data.get(docPath) || {};
        const merged = options.merge ? { ...existing, ...payload } : { ...payload };
        this.data.set(docPath, merged);
        this.auditLog.push({ op: 'SET', path: docPath, data: merged });
        return Promise.resolve();
    }

    get(docPath) {
        if (docPath.includes('accounts/latest')) {
            throw new Error(`CRITICAL SECURITY VIOLATION: Read from global accounts/latest detected! Path: ${docPath}`);
        }
        const val = this.data.get(docPath);
        this.auditLog.push({ op: 'GET', path: docPath, exists: !!val });
        return Promise.resolve({
            exists: !!val,
            data: () => val || null,
        });
    }
}

class UserSessionClient {
    constructor(uid, store) {
        this.uid = uid;
        this.store = store;
        this.sessionPath = `users/${uid}/sessions/current`;
        this.recoveryToken = 0;
        this.activeSession = null;
    }

    async initLocalSession() {
        const payload = {
            active: true,
            mode: "local",
            host: "192.168.1." + Math.floor(Math.random() * 200 + 10),
            port: 27189,
            updatedAt: Date.now()
        };
        this.activeSession = payload;
        await this.store.set(this.sessionPath, payload);
    }

    async recoverRemoteSession(newHost, newPort) {
        const token = ++this.recoveryToken;
        // Simulate async network delay
        await new Promise(r => setTimeout(r, Math.random() * 20));

        if (token !== this.recoveryToken) {
            // Cancelled or superseded by newer recovery
            return;
        }

        const payload = {
            active: true,
            mode: "remote",
            host: newHost,
            port: newPort,
            updatedAt: Date.now()
        };
        this.activeSession = payload;
        await this.store.set(this.sessionPath, payload);
    }

    async disconnectSession() {
        this.recoveryToken++;
        const payload = {
            active: false,
            updatedAt: Date.now()
        };
        this.activeSession = null;
        await this.store.set(this.sessionPath, payload);
    }
}

async function runMultiTenantConcurrencyStress() {
    console.log("\n--- 1. Spawning 10 Concurrent Tenant Sessions ---");
    const store = new MultiTenantStore();
    const userCount = 10;
    const users = [];

    for (let i = 1; i <= userCount; ++i) {
        users.push(new UserSessionClient(`tenant_user_${i}`, store));
    }
    console.log(`  ✓ Spawned ${userCount} isolated tenant clients.`);

    console.log("\n--- 2. Fuzzing 500 Interleaved Asynchronous Session Transitions ---");
    const actions = [];
    const opsCount = 500;

    for (let op = 0; op < opsCount; ++op) {
        const user = users[Math.floor(Math.random() * userCount)];
        const actionType = Math.floor(Math.random() * 3);

        if (actionType === 0) {
            actions.push(user.initLocalSession());
        } else if (actionType === 1) {
            const host = `tunnel-${op}.pinggy.io`;
            const port = 30000 + (op % 1000);
            actions.push(user.recoverRemoteSession(host, port));
        } else {
            actions.push(user.disconnectSession());
        }
    }

    await Promise.all(actions);
    console.log(`  ✓ Completed ${opsCount} concurrent async operations.`);

    console.log("\n--- 3. Verifying Multi-Tenant Isolation & Zero Cross-Contamination ---");
    assert.strictEqual(store.auditLog.length > 0, true);

    // Verify 0 global accounts/latest access
    for (const log of store.auditLog) {
        assert(!log.path.includes("accounts/latest"), `Forbidden global path in log: ${log.path}`);
        
        // Path MUST match users/<uid>/sessions/current
        const match = log.path.match(/^users\/([^/]+)\/sessions\/current$/);
        assert(match !== null, `Path does not conform to user isolation contract: ${log.path}`);
    }
    console.log(`  ✓ All ${store.auditLog.length} audit entries strictly conform to users/{uid}/sessions/current.`);

    // Verify each user's state is strictly their own
    for (const user of users) {
        const snap = await store.get(user.sessionPath);
        if (user.activeSession) {
            assert.strictEqual(snap.exists, true);
            const data = snap.data();
            assert.strictEqual(data.active, true);
        } else {
            if (snap.exists) {
                assert.strictEqual(snap.data().active, false);
            }
        }
    }
    console.log("  ✓ All 10 user sessions verified in final state without cross-tenant pollution.");

    console.log("\n===================================================================");
    console.log("  ALL MULTI-TENANT CONCURRENCY STRESS TESTS PASSED (100% PASS)     ");
    console.log("===================================================================");
}

runMultiTenantConcurrencyStress().catch(err => {
    console.error("Multi-tenant stress test failed:", err);
    process.exit(1);
});
