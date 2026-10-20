#!/usr/bin/env node
/**
 * Empirical Adversarial Test for Milestone 2:
 * UI DOM Elements, Obsolete Elements Removal, and Offline Robustness.
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

const REPO_ROOT = path.resolve(__dirname, '../..');
const HTML_PATH = path.join(REPO_ROOT, 'orchestrator/ui/index.html');
const JS_PATH = path.join(REPO_ROOT, 'orchestrator/ui/script.js');
const CSS_PATH = path.join(REPO_ROOT, 'orchestrator/ui/style.css');

console.log('===================================================================');
console.log('   M2 EMPIRICAL TEST: UI DOM CONTRACTS & OFFLINE ROBUSTNESS        ');
console.log('===================================================================');

const htmlContent = fs.readFileSync(HTML_PATH, 'utf8');
const jsContent = fs.readFileSync(JS_PATH, 'utf8');
const cssContent = fs.readFileSync(CSS_PATH, 'utf8');

// -----------------------------------------------------------------------------
// SECTION 1: DOM Elements and Skill Compliance Checks
// -----------------------------------------------------------------------------
console.log('\n>>> [SECTION 1] Checking HTML structure and skill compliance...');

// 1.1 Verify Obsolete Elements are REMOVED per update-connection-ui skill
const forbiddenPatterns = [
    '#manual-ip-input',
    'manual-ip-input',
    '#btn-manual-connect',
    'btn-manual-connect',
    '#remote-code-container',
    'remote-code-container',
    'Connect by Device IP'
];

for (const pattern of forbiddenPatterns) {
    assert.strictEqual(
        htmlContent.includes(pattern),
        false,
        `Violation: Obsolete pattern "${pattern}" must be removed from index.html per update-connection-ui skill`
    );
}
console.log('  ✓ Obsolete manual IP and remote tunnel elements are completely removed.');

// 1.2 Verify Required Milestone 2 Elements in index.html
const requiredIds = [
    'dashboard-screen',
    'login-screen',
    'tab-local',
    'tab-remote',
    'view-local',
    'view-remote',
    'cable-banner',
    'cable-device-name',
    'btn-cable-mirror',
    'discovered-list',
    'discovered-count',
    'paired-list',
    'paired-count',
    'remote-unauth',
    'remote-auth',
    'remote-device-list',
    'remote-count',
    'btn-google-login',
    'btn-google-login-remote',
    'btn-disconnect',
    'status-message',
    'status-text',
    'user-email',
    'user-avatar',
    'btn-signout'
];

for (const id of requiredIds) {
    const idRegex = new RegExp(`id=["']${id}["']`);
    assert(idRegex.test(htmlContent), `Missing required DOM element id: "${id}" in index.html`);
}
console.log(`  ✓ All ${requiredIds.length} required element IDs exist in index.html.`);

// 1.3 Verify Default Screen Visibility in HTML
assert(
    /<div id="dashboard-screen"[^>]*class="[^"]*screen active[^"]*"/i.test(htmlContent),
    'dashboard-screen must be active by default in index.html'
);
assert(
    /<div id="login-screen"[^>]*class="[^"]*screen hidden[^"]*"/i.test(htmlContent),
    'login-screen must be hidden by default in index.html'
);
console.log('  ✓ Zero-Config default: dashboard-screen is active, login-screen is hidden.');

// 1.4 Verify CSS Classes per update-connection-ui skill
assert(cssContent.includes('.cable-card'), 'Missing .cable-card in style.css');
assert(cssContent.includes('.badge-cable'), 'Missing .badge-cable in style.css');
assert(cssContent.includes('.badge-local'), 'Missing .badge-local in style.css');
assert(cssContent.includes('.badge-remote'), 'Missing .badge-remote in style.css');
console.log('  ✓ CSS classes (.cable-card, .badge-cable, .badge-local, .badge-remote) are defined.');


// -----------------------------------------------------------------------------
// SECTION 2: script.js DOM Reference Audit
// -----------------------------------------------------------------------------
console.log('\n>>> [SECTION 2] Auditing script.js getElementById calls against index.html...');

const getElementMatches = jsContent.matchAll(/document\.getElementById\(["']([^"']+)["']\)/g);
const referencedIds = new Set();
for (const match of getElementMatches) {
    referencedIds.add(match[1]);
}

for (const refId of referencedIds) {
    const idRegex = new RegExp(`id=["']${refId}["']`);
    assert(
        idRegex.test(htmlContent),
        `Dangling DOM ID in script.js: document.getElementById("${refId}") does NOT exist in index.html!`
    );
}
console.log(`  ✓ All ${referencedIds.size} element IDs referenced in script.js exist in index.html without dangling references.`);


// -----------------------------------------------------------------------------
// SECTION 3: Offline Execution & UsbDeviceWatcher Simulation in DOM VM
// -----------------------------------------------------------------------------
console.log('\n>>> [SECTION 3] Stress-testing offline execution (Firebase CDN down)...');

// Build a mock DOM tree representing index.html
class MockElement {
    constructor(id, tagName = 'div', classes = []) {
        this.id = id;
        this.tagName = tagName.toUpperCase();
        this.classList = {
            _classes: new Set(classes),
            add: (c) => this.classList._classes.add(c),
            remove: (c) => this.classList._classes.delete(c),
            contains: (c) => this.classList._classes.has(c)
        };
        this.children = [];
        this.listeners = {};
        this.innerText = '';
        this.innerHTML = '';
        this.disabled = false;
        this.style = {};
    }

    addEventListener(event, fn) {
        if (!this.listeners[event]) this.listeners[event] = [];
        this.listeners[event].push(fn);
    }

    click() {
        if (this.listeners['click']) {
            this.listeners['click'].forEach(fn => fn({ target: this }));
        }
        if (this.onclick) {
            this.onclick({ target: this });
        }
    }

    querySelector(selector) {
        // Simple mock selector
        if (selector.startsWith('.')) {
            const cls = selector.slice(1);
            for (const ch of this.children) {
                if (ch.classList.contains(cls)) return ch;
                const found = ch.querySelector(selector);
                if (found) return found;
            }
        }
        return null;
    }

    querySelectorAll(selector) {
        return [];
    }
}

const domElements = {};
for (const id of requiredIds) {
    const isHidden = (id === 'login-screen' || id === 'cable-banner' || id === 'view-remote' || id === 'btn-disconnect' || id === 'btn-signout');
    const classes = [];
    if (isHidden) classes.push('hidden');
    if (id === 'dashboard-screen' || id === 'tab-local') classes.push('active');
    domElements[id] = new MockElement(id, 'div', classes);
}

const mockDocument = {
    getElementById: (id) => domElements[id] || null,
    querySelector: (sel) => null,
    querySelectorAll: (sel) => []
};

const mockLocalStorage = {
    _data: {},
    getItem: (k) => mockLocalStorage._data[k] || null,
    setItem: (k, v) => { mockLocalStorage._data[k] = String(v); },
    removeItem: (k) => { delete mockLocalStorage._data[k]; }
};

let lastConnectedDevice = null;
const mockWindow = {
    document: mockDocument,
    localStorage: mockLocalStorage,
    setInterval: () => 1,
    setTimeout: (fn, ms) => { fn(); return 1; },
    clearTimeout: () => {},
    addEventListener: () => {},
    console: console,
    pywebview: {
        api: {
            get_discovered_devices: () => Promise.resolve([]),
            get_paired_devices: () => Promise.resolve([]),
            get_usb_device: () => Promise.resolve(null),
            connect_device: (dev) => {
                lastConnectedDevice = dev;
                return Promise.resolve({ success: true, mode: "local" });
            }
        }
    }
};

const context = vm.createContext({
    window: mockWindow,
    document: mockDocument,
    localStorage: mockLocalStorage,
    console: console,
    setTimeout: mockWindow.setTimeout,
    clearTimeout: mockWindow.clearTimeout,
    setInterval: mockWindow.setInterval,
    // Note: firebase is deliberately UNDEFINED to simulate offline CDN failure
    firebase: undefined
});

// Execute script.js in offline context
try {
    vm.runInContext(jsContent, context);
    console.log('  ✓ script.js executed successfully with firebase=undefined (0 unhandled exceptions).');
} catch (err) {
    console.error('FATAL: script.js crashed during offline execution:', err);
    process.exit(1);
}

// Verify initial state after offline load
const dashScreen = domElements['dashboard-screen'];
const logScreen = domElements['login-screen'];
assert(dashScreen.classList.contains('active'), 'dashboard-screen must be active after load');
assert(logScreen.classList.contains('hidden'), 'login-screen must be hidden after load');
console.log('  ✓ Initial state confirmed: dashboard active, login hidden.');

// Test window.updateUsbDevice
console.log('\n>>> [SECTION 4] Testing window.updateUsbDevice adversarial inputs...');
const cableBanner = domElements['cable-banner'];
const cableName = domElements['cable-device-name'];

// 4.1 USB Connected
mockWindow.updateUsbDevice({
    connected: true,
    serial: 'USB_PIXEL_10',
    name: 'Google Pixel 10 Pro',
    ip: '127.0.0.1',
    is_usb: true
});
assert.strictEqual(cableBanner.classList.contains('hidden'), false, 'cable-banner must be visible when USB device connected');
assert.strictEqual(cableName.innerText, 'Google Pixel 10 Pro', 'cable-device-name must update');
console.log('  ✓ Connected USB event unhides cable-banner and sets model name.');

// 4.2 Click "Mirror Now" button on cable card
const btnCable = domElements['btn-cable-mirror'];
btnCable.click();
assert(lastConnectedDevice !== null, 'Clicking btn-cable-mirror must trigger connectDevice');
assert.strictEqual(lastConnectedDevice.ip, '127.0.0.1', 'Cable mirror must route to 127.0.0.1');
assert.strictEqual(lastConnectedDevice.is_usb, true, 'Cable mirror must flag is_usb=true');
console.log('  ✓ btn-cable-mirror click successfully routes 1-click mirror to 127.0.0.1 (is_usb: true).');

// 4.3 USB Disconnected
mockWindow.updateUsbDevice({ connected: false });
assert.strictEqual(cableBanner.classList.contains('hidden'), true, 'cable-banner must be hidden when USB disconnected');
console.log('  ✓ Disconnect event hides cable-banner.');

// 4.4 Adversarial null / undefined / empty payload
mockWindow.updateUsbDevice(null);
assert.strictEqual(cableBanner.classList.contains('hidden'), true, 'null payload safely handled');

mockWindow.updateUsbDevice(undefined);
assert.strictEqual(cableBanner.classList.contains('hidden'), true, 'undefined payload safely handled');

mockWindow.updateUsbDevice({});
assert.strictEqual(cableBanner.classList.contains('hidden'), true, 'empty object payload safely handled');
console.log('  ✓ Adversarial inputs (null, undefined, {}) to updateUsbDevice safely handled.');

// 4.5 Tab Switching Logic
console.log('\n>>> [SECTION 5] Testing Tab Switching (Local vs Remote Relay)...');
const tabLocal = domElements['tab-local'];
const tabRemote = domElements['tab-remote'];
const viewLocal = domElements['view-local'];
const viewRemote = domElements['view-remote'];

// Switch to Remote
tabRemote.click();
assert(tabRemote.classList.contains('active'), 'tabRemote should be active');
assert(!tabLocal.classList.contains('active'), 'tabLocal should not be active');
assert(!viewRemote.classList.contains('hidden'), 'viewRemote should be visible');
assert(viewLocal.classList.contains('hidden'), 'viewLocal should be hidden');
console.log('  ✓ Switched to Remote Relay tab successfully.');

// Switch back to Local
tabLocal.click();
assert(tabLocal.classList.contains('active'), 'tabLocal should be active');
assert(!tabRemote.classList.contains('active'), 'tabRemote should not be active');
assert(!viewLocal.classList.contains('hidden'), 'viewLocal should be visible');
assert(viewRemote.classList.contains('hidden'), 'viewRemote should be hidden');
console.log('  ✓ Switched back to Local Devices tab successfully.');

console.log('\n===================================================================');
console.log('   ALL UI DOM & OFFLINE ADVERSARIAL TESTS PASSED!                  ');
console.log('===================================================================');
