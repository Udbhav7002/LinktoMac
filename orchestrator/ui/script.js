const firebaseConfig = {
    apiKey: "AIzaSyCr6T3Np-dI8lax8R2PfGDO57ZTPr0Eb2g",
    authDomain: "linktomac.firebaseapp.com",
    projectId: "linktomac"
};

let db = null;
if (typeof firebase !== 'undefined') {
    try {
        firebase.initializeApp(firebaseConfig);
        db = firebase.firestore();
    } catch (e) {
        console.error("Firebase initialization error:", e);
    }
}

function getFirestoreTimestamp() {
    try {
        if (typeof firebase !== 'undefined' && firebase && firebase.firestore && firebase.firestore.FieldValue) {
            return firebase.firestore.FieldValue.serverTimestamp();
        }
    } catch (e) {}
    return new Date().toISOString();
}

let currentUsbDevice = null;
let unsubscribeFirestore = null;
let firestoreHasDevice = false;

window.updateUsbDevice = function(dev) {
    const banner = document.getElementById("cable-banner");
    const nameEl = document.getElementById("cable-device-name");
    if (!banner) return;

    if (dev && dev.connected) {
        currentUsbDevice = dev;
        if (nameEl) {
            nameEl.innerText = dev.name || "USB Device Connected";
        }
        banner.classList.remove("hidden");
    } else {
        currentUsbDevice = null;
        banner.classList.add("hidden");
    }
};

function getCurrentUid() {
    try {
        const saved = localStorage.getItem("linktomac_user");
        if (saved) {
            const user = JSON.parse(saved);
            return (user && user.uid && user.uid !== "local_user") ? user.uid : null;
        }
    } catch (e) {}
    return null;
}

let activeSession = null;
let reconnectTimer = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;
let recoveryCancelled = false;
let recoveryToken = 0;

function escapeHtml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function setButtonLoading(button, isLoading, loadingText = "") {
    if (!button) return;
    button.disabled = isLoading;
    const label = button.querySelector(".btn-label");
    const spinner = button.querySelector(".spinner");

    if (isLoading) {
        if (label && loadingText) label.dataset.prevText = label.innerText;
        if (label && loadingText) label.innerText = loadingText;
        if (spinner) spinner.classList.remove("hidden");
    } else {
        if (label && label.dataset.prevText) {
            label.innerText = label.dataset.prevText;
            delete label.dataset.prevText;
        }
        if (spinner) spinner.classList.add("hidden");
    }
}

function showStatus(message, isError = false, onRetry = null) {
    const el = document.getElementById("status-message");
    const textEl = document.getElementById("status-text");
    const retryBtn = document.getElementById("status-retry-btn");
    if (!el || !textEl) return;

    if (!message) {
        el.classList.add("hidden");
        textEl.innerText = "";
        if (retryBtn) retryBtn.classList.add("hidden");
        return;
    }

    textEl.innerText = message;
    el.className = "status-message " + (isError ? "error" : "success");
    el.classList.remove("hidden");

    if (retryBtn) {
        if (isError && onRetry) {
            retryBtn.classList.remove("hidden");
            retryBtn.onclick = () => {
                showStatus("", false);
                onRetry();
            };
        } else {
            retryBtn.classList.add("hidden");
        }
    }

    if (!isError) {
        setTimeout(() => {
            el.classList.add("hidden");
        }, 5000);
    }
}

function setDisconnectVisible(visible) {
    const btn = document.getElementById("btn-disconnect");
    if (btn) {
        if (visible) btn.classList.remove("hidden");
        else btn.classList.add("hidden");
    }
}

function parseRelayEndpoint(res) {
    let relayHost = (res && res.host) ? String(res.host).trim() : "";
    let relayPort = (res && res.port) ? (parseInt(res.port, 10) || 0) : 0;

    if (relayHost.startsWith("[") && relayHost.endsWith("]")) {
        relayHost = relayHost.slice(1, -1).trim();
    }

    if (!relayHost && res && res.session_code && typeof res.session_code === "string" && res.session_code.includes(":")) {
        const rawCode = res.session_code.trim();
        if (rawCode.startsWith("[")) {
            const closingBracket = rawCode.indexOf("]");
            if (closingBracket !== -1) {
                relayHost = rawCode.slice(1, closingBracket).trim();
                const afterBracket = rawCode.slice(closingBracket + 1).trim();
                if (afterBracket.startsWith(":")) {
                    relayPort = parseInt(afterBracket.slice(1).trim(), 10) || 0;
                } else {
                    relayPort = 0;
                }
            } else {
                const lastColon = rawCode.lastIndexOf(":");
                relayHost = rawCode.slice(0, lastColon).trim();
                relayPort = parseInt(rawCode.slice(lastColon + 1).trim(), 10) || 0;
            }
        } else {
            const lastColon = rawCode.lastIndexOf(":");
            relayHost = rawCode.slice(0, lastColon).trim();
            relayPort = parseInt(rawCode.slice(lastColon + 1).trim(), 10) || 0;
        }
    }

    return { relayHost, relayPort };
}

function showSessionRecovery(deviceName, attempt = 1, maxAttempts = MAX_RECONNECT_ATTEMPTS) {
    const hud = document.getElementById("session-recovery-hud");
    const msg = document.getElementById("recovery-message");
    const attemptSpan = document.getElementById("recovery-attempt");
    const retryBtn = document.getElementById("btn-recovery-retry");
    const cancelBtn = document.getElementById("btn-recovery-cancel");

    if (!hud) return;
    hud.classList.remove("hidden");

    if (msg) msg.innerText = `Reconnecting to ${deviceName || "your phone"}...`;
    if (attemptSpan) attemptSpan.innerText = `Attempt ${attempt} / ${maxAttempts}`;

    if (retryBtn) {
        retryBtn.onclick = () => {
            recoveryCancelled = false;
            if (reconnectTimer) clearTimeout(reconnectTimer);
            attemptSessionRecovery(deviceName);
        };
    }

    if (cancelBtn) {
        cancelBtn.onclick = () => {
            recoveryCancelled = true;
            recoveryToken++;
            hideSessionRecovery();
            if (reconnectTimer) clearTimeout(reconnectTimer);
            reconnectTimer = null;
            reconnectAttempts = 0;
            const uid = (activeSession && activeSession.uid) || getCurrentUid();
            activeSession = null;
            setDisconnectVisible(false);
            showStatus("Reconnection cancelled.", false);
            if (window.pywebview && window.pywebview.api) {
                if (typeof window.pywebview.api.stop_mirror === "function") {
                    try { window.pywebview.api.stop_mirror().catch(() => {}); } catch (e) {}
                }
                if (typeof window.pywebview.api.stop_remote_session === "function") {
                    try { window.pywebview.api.stop_remote_session().catch(() => {}); } catch (e) {}
                }
            }
            if (db && uid) {
                db.collection("users").doc(uid).collection("sessions").doc("current").set({
                    active: false,
                    host: null,
                    port: 0,
                    updatedAt: getFirestoreTimestamp()
                }, { merge: true }).catch(() => {});
            }
        };
    }
}

function hideSessionRecovery() {
    const hud = document.getElementById("session-recovery-hud");
    if (hud) hud.classList.add("hidden");
}

function attemptSessionRecovery(deviceName) {
    if (reconnectAttempts === 0) {
        recoveryCancelled = false;
    }
    if (recoveryCancelled) return;
    const currentToken = ++recoveryToken;

    reconnectAttempts++;
    showSessionRecovery(deviceName, reconnectAttempts, MAX_RECONNECT_ATTEMPTS);

    if (!window.pywebview || !window.pywebview.api) {
        hideSessionRecovery();
        showStatus("Backend connection lost.", true);
        return;
    }

    const target = activeSession || { name: deviceName };
    const uid = (activeSession && activeSession.uid) || (target && target.uid) || getCurrentUid();

    try {
        window.pywebview.api.connect_device(target).then((res) => {
            if (recoveryCancelled || currentToken !== recoveryToken) return;
            if (res && res.error) {
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    const backoffDelay = Math.min(1000 * Math.pow(1.8, reconnectAttempts), 10000);
                    reconnectTimer = setTimeout(() => attemptSessionRecovery(deviceName), backoffDelay);
                } else {
                    hideSessionRecovery();
                    activeSession = null;
                    setDisconnectVisible(false);
                    showStatus(`Connection lost to ${deviceName}. Tap Mirror to reconnect.`, true, () => {
                        recoveryCancelled = false;
                        reconnectAttempts = 0;
                        attemptSessionRecovery(deviceName);
                    });
                    if (db && uid) {
                        db.collection("users").doc(uid).collection("sessions").doc("current").set({
                            active: false,
                            host: null,
                            port: 0,
                            updatedAt: getFirestoreTimestamp()
                        }, { merge: true }).catch(() => {});
                    }
                }
            } else {
                hideSessionRecovery();
                reconnectAttempts = 0;
                const modeText = (res && res.mode === "local") ? "Local" : "Remote";

                activeSession = {
                    name: deviceName,
                    mode: (res && res.mode) ? res.mode : "direct",
                    uid: uid,
                    ip: (activeSession && activeSession.ip) || (target && target.ip) || ""
                };

                if (db && uid) {
                    if (res && res.mode === "remote") {
                        const { relayHost, relayPort } = parseRelayEndpoint(res);
                        db.collection("users").doc(uid).collection("sessions").doc("current").set({
                            active: true,
                            mode: "remote",
                            host: relayHost,
                            port: relayPort,
                            updatedAt: getFirestoreTimestamp()
                        }, { merge: true }).catch((err) => {
                            console.error("Session recovery remote Firestore update failed:", err);
                        });
                    } else if (res && res.mode === "local") {
                        const macIp = (res && res.mac_ip) ? res.mac_ip : "local";
                        db.collection("users").doc(uid).collection("sessions").doc("current").set({
                            active: true,
                            mode: "local",
                            host: macIp,
                            macIp: macIp,
                            port: 27189,
                            updatedAt: getFirestoreTimestamp()
                        }, { merge: true }).catch((err) => {
                            console.error("Session recovery local Firestore update failed:", err);
                        });
                    }
                }

                showStatus(`Connected to ${deviceName} (${modeText}).`, false);
                setDisconnectVisible(true);
            }
        }).catch(err => {
            if (recoveryCancelled || currentToken !== recoveryToken) return;
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                const backoffDelay = Math.min(1000 * Math.pow(1.8, reconnectAttempts), 10000);
                reconnectTimer = setTimeout(() => attemptSessionRecovery(deviceName), backoffDelay);
            } else {
                hideSessionRecovery();
                activeSession = null;
                setDisconnectVisible(false);
                showStatus(`Connection lost: ${err}`, true);
                if (db && uid) {
                    db.collection("users").doc(uid).collection("sessions").doc("current").set({
                        active: false,
                        host: null,
                        port: 0,
                        updatedAt: getFirestoreTimestamp()
                    }, { merge: true }).catch(() => {});
                }
            }
        });
    } catch (e) {
        if (recoveryCancelled || currentToken !== recoveryToken) return;
        hideSessionRecovery();
        activeSession = null;
        setDisconnectVisible(false);
        showStatus(`Connection recovery error: ${e}`, true);
        if (db && uid) {
            db.collection("users").doc(uid).collection("sessions").doc("current").set({
                active: false,
                host: null,
                port: 0,
                updatedAt: getFirestoreTimestamp()
            }, { merge: true }).catch(() => {});
        }
    }
}

window.onMirrorProcessExit = function(exitCode, deviceName) {
    if (activeSession) {
        const name = (activeSession && activeSession.name) ? activeSession.name : (deviceName || "Phone");
        const uid = (activeSession && activeSession.uid) || getCurrentUid();
        if (exitCode !== 0) {
            recoveryCancelled = false;
            attemptSessionRecovery(name);
        } else {
            hideSessionRecovery();
            activeSession = null;
            setDisconnectVisible(false);
            showStatus("Session ended.", false);
            if (db && uid) {
                db.collection("users").doc(uid).collection("sessions").doc("current").set({
                    active: false,
                    host: null,
                    port: 0,
                    updatedAt: getFirestoreTimestamp()
                }, { merge: true }).catch(() => {});
            }
        }
    }
};

function publishAccountForPhoneSync(user) {
    if (!db || !user || !user.uid || !user.email) return;
    const cleanEmail = user.email.toLowerCase().trim();
    const payload = {
        uid: user.uid,
        email: cleanEmail,
        updatedAt: getFirestoreTimestamp()
    };
    db.collection("accounts").doc(cleanEmail).set(payload, { merge: true }).catch(() => {});
}

function initLocalDashboard() {
    const emailEl = document.getElementById("user-email");
    const avatarEl = document.getElementById("user-avatar");
    const signoutBtn = document.getElementById("btn-signout");
    const remoteUnauth = document.getElementById("remote-unauth");
    const remoteAuth = document.getElementById("remote-auth");

    if (emailEl) emailEl.innerText = "Local Mode";
    if (avatarEl) avatarEl.innerText = "L";
    if (signoutBtn) signoutBtn.classList.add("hidden");
    if (remoteUnauth) remoteUnauth.classList.remove("hidden");
    if (remoteAuth) remoteAuth.classList.add("hidden");

    const loginScreen = document.getElementById("login-screen");
    const dashboardScreen = document.getElementById("dashboard-screen");
    if (loginScreen) {
        loginScreen.classList.remove("active");
        loginScreen.classList.add("hidden");
    }
    if (dashboardScreen) {
        dashboardScreen.classList.remove("hidden");
        dashboardScreen.classList.add("active");
    }

    loadDiscoveredDevices();
    loadPairedDevices();
}

function enterDashboard(user) {
    if (!user) return;
    try {
        localStorage.setItem("linktomac_user", JSON.stringify(user));
    } catch (e) {}

    publishAccountForPhoneSync(user);

    const emailEl = document.getElementById("user-email");
    const avatarEl = document.getElementById("user-avatar");
    const signoutBtn = document.getElementById("btn-signout");
    const remoteUnauth = document.getElementById("remote-unauth");
    const remoteAuth = document.getElementById("remote-auth");

    const email = user.email || "Google Account";
    if (emailEl) emailEl.innerText = email;
    if (avatarEl) avatarEl.innerText = email.charAt(0).toUpperCase();
    if (signoutBtn) {
        signoutBtn.innerText = "Sign Out";
        signoutBtn.classList.remove("hidden");
        signoutBtn.onclick = () => {
            try { localStorage.removeItem("linktomac_user"); } catch (e) {}
            if (unsubscribeFirestore) {
                try { unsubscribeFirestore(); } catch (e) {}
                unsubscribeFirestore = null;
            }
            initLocalDashboard();
            showStatus("Signed out. Switched to Local Mode.", false);
        };
    }
    if (remoteUnauth) remoteUnauth.classList.add("hidden");
    if (remoteAuth) remoteAuth.classList.remove("hidden");

    const loginScreen = document.getElementById("login-screen");
    const dashboardScreen = document.getElementById("dashboard-screen");
    if (loginScreen) {
        loginScreen.classList.remove("active");
        loginScreen.classList.add("hidden");
    }
    if (dashboardScreen) {
        dashboardScreen.classList.remove("hidden");
        dashboardScreen.classList.add("active");
    }

    if (user.uid) {
        listenForAndroidDevice(user.uid);
    }
    loadDiscoveredDevices();
    loadPairedDevices();
}

function showLoginScreen() {
    const loginScreen = document.getElementById("login-screen");
    const dashboardScreen = document.getElementById("dashboard-screen");
    if (loginScreen) {
        loginScreen.classList.remove("hidden");
        loginScreen.classList.add("active");
    }
    if (dashboardScreen) {
        dashboardScreen.classList.remove("active");
        dashboardScreen.classList.add("hidden");
    }
}

try {
    const saved = localStorage.getItem("linktomac_user");
    if (saved) {
        const user = JSON.parse(saved);
        if (user && user.uid && user.uid !== "local_user") {
            enterDashboard(user);
        } else {
            initLocalDashboard();
        }
    } else {
        initLocalDashboard();
    }
} catch (e) {
    initLocalDashboard();
}

// Tab Switching (Local vs Remote Relay)
const tabLocal = document.getElementById("tab-local");
const tabRemote = document.getElementById("tab-remote");
const viewLocal = document.getElementById("view-local");
const viewRemote = document.getElementById("view-remote");

if (tabLocal && tabRemote && viewLocal && viewRemote) {
    tabLocal.addEventListener("click", () => {
        tabLocal.classList.add("active");
        tabRemote.classList.remove("active");
        viewLocal.classList.remove("hidden");
        viewRemote.classList.add("hidden");
    });

    tabRemote.addEventListener("click", () => {
        tabRemote.classList.add("active");
        tabLocal.classList.remove("active");
        viewRemote.classList.remove("hidden");
        viewLocal.classList.add("hidden");
    });
}

// USB Cable Mirror Button
const btnCableMirror = document.getElementById("btn-cable-mirror");
if (btnCableMirror) {
    btnCableMirror.addEventListener("click", () => {
        const dev = currentUsbDevice || { name: "USB Android Device", ip: "127.0.0.1", is_usb: true };
        window.connectDevice({
            name: dev.name || "USB Android Device",
            ip: "127.0.0.1",
            is_usb: true
        }, btnCableMirror);
    });
}

// Google Sign In Handler (shared for both login-screen and remote tab)
function handleGoogleLogin(btn) {
    if (!window.pywebview || !window.pywebview.api) {
        showStatus("Starting LinktoMac backend...", false);
        return;
    }

    if (btn) {
        btn.disabled = true;
        btn.style.opacity = "0.7";
        btn.innerText = "Opening Browser...";
    }

    window.pywebview.api.google_login().then((result) => {
        if (result && result.uid) {
            enterDashboard({ uid: result.uid, email: result.email });
            showStatus(`Signed in as ${result.email}`, false);
        } else if (result && result.error) {
            showStatus(`Sign in failed: ${result.error}`, true);
        }
    }).catch(err => {
        showStatus(`Login error: ${err}`, true);
    }).finally(() => {
        if (btn) {
            btn.disabled = false;
            btn.style.opacity = "1";
            btn.innerHTML = `
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                </svg>
                Sign in with Google
            `;
        }
    });
}

const btnGoogleLogin = document.getElementById("btn-google-login");
if (btnGoogleLogin) {
    btnGoogleLogin.addEventListener("click", () => handleGoogleLogin(btnGoogleLogin));
}

const btnGoogleLoginRemote = document.getElementById("btn-google-login-remote");
if (btnGoogleLoginRemote) {
    btnGoogleLoginRemote.addEventListener("click", () => handleGoogleLogin(btnGoogleLoginRemote));
}

function renderDeviceCard(list, countEl, deviceName, ip, uid, badgeText) {
    const safeName = escapeHtml(deviceName);
    if (countEl) countEl.innerText = "1";
    const badgeClass = (badgeText && (badgeText.includes("Cloud") || badgeText.includes("Remote"))) ? "badge-remote" : "badge-local";
    list.innerHTML = `
        <div class="device-item">
            <div class="device-info">
                <div class="device-icon">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
                        <line x1="12" y1="18" x2="12.01" y2="18"></line>
                    </svg>
                </div>
                <div class="device-details">
                    <div class="device-name">${safeName}</div>
                    <div class="status-badge ${badgeClass}">● ${escapeHtml(badgeText)}</div>
                </div>
            </div>
            <div class="device-actions">
                <button class="btn-primary btn-sm btn-connect-device">
                    <span class="btn-label">Mirror</span>
                    <span class="spinner spinner-sm hidden"></span>
                </button>
            </div>
        </div>
    `;

    const connectBtn = list.querySelector(".btn-connect-device");
    if (connectBtn) {
        connectBtn.addEventListener("click", () => {
            window.connectDevice({
                name: deviceName,
                ip: ip || "",
                uid: uid
            }, connectBtn);
        });
    }
}

function listenForAndroidDevice(uid) {
    const list = document.getElementById("remote-device-list") || document.getElementById("discovered-list");
    const countEl = document.getElementById("remote-count") || document.getElementById("discovered-count");
    if (!list || !db) return;

    unsubscribeFirestore = db.collection("users").doc(uid).collection("devices").doc("android")
        .onSnapshot((doc) => {
            if (doc.exists) {
                const data = doc.data() || {};
                const deviceName = data.deviceName || "Android Phone";
                const ip = data.ip || data.localIp || "";
                firestoreHasDevice = true;
                renderDeviceCard(list, countEl, deviceName, ip, uid, "Ready (Cloud)");
            } else {
                firestoreHasDevice = false;
                if (countEl) countEl.innerText = "0";
                list.innerHTML = '<div class="empty-state">Waiting for phone cloud sync...</div>';
            }
        }, (error) => {
            console.error("Firestore sync error:", error);
        });
}

window.connectDevice = function(deviceData, sourceBtn = null) {
    recoveryCancelled = false;
    if (!deviceData) return;
    const deviceName = deviceData.name || "Phone";
    const targetUid = deviceData.uid || getCurrentUid();

    if (!window.pywebview || !window.pywebview.api) {
        if (sourceBtn) setButtonLoading(sourceBtn, false);
        showStatus("Connecting to LinktoMac service...", true, () => window.connectDevice(deviceData, sourceBtn));
        return;
    }

    if (sourceBtn) setButtonLoading(sourceBtn, true, "Linking...");
    showStatus(`Connecting to ${deviceName}...`, false);

    try {
        const apiFunc = window.pywebview.api.connect_device || window.pywebview.api.connect_to_ip;
        const arg = window.pywebview.api.connect_device ? deviceData : (deviceData.ip || "");

        apiFunc(arg).then((res) => {
            if (res && res.error) {
                showStatus(`Connection failed: ${res.error}`, true, () => window.connectDevice(deviceData, sourceBtn));
                if (db && targetUid) {
                    db.collection("users").doc(targetUid).collection("sessions").doc("current").set({
                        active: false,
                        host: null,
                        port: 0,
                        updatedAt: getFirestoreTimestamp()
                    }, { merge: true }).catch(() => {});
                }
            } else {
                activeSession = {
                    name: deviceName,
                    mode: (res && res.mode) ? res.mode : "direct",
                    uid: targetUid,
                    ip: deviceData.ip || ""
                };
                reconnectAttempts = 0;
                const modeLabel = (res && res.mode === "local") ? "Local" : "Remote";

                if (db && targetUid) {
                    if (res && res.mode === "local") {
                        const macIp = (res && res.mac_ip) ? res.mac_ip : "local";
                        db.collection("users").doc(targetUid).collection("sessions").doc("current").set({
                            active: true,
                            mode: "local",
                            host: macIp,
                            macIp: macIp,
                            port: 27189,
                            updatedAt: getFirestoreTimestamp()
                        }, { merge: true }).catch((err) => {
                            console.error("Firestore local session update failed:", err);
                        });
                    } else if (res && res.mode === "remote") {
                        const { relayHost, relayPort } = parseRelayEndpoint(res);
                        db.collection("users").doc(targetUid).collection("sessions").doc("current").set({
                            active: true,
                            mode: "remote",
                            host: relayHost,
                            port: relayPort,
                            updatedAt: getFirestoreTimestamp()
                        }, { merge: true }).catch((err) => {
                            console.error("Firestore remote session update failed:", err);
                        });
                    }
                }

                showStatus(`Connected to ${deviceName} (${modeLabel})!`, false);
                setDisconnectVisible(true);
            }
        }).catch(err => {
            showStatus(`Could not connect to ${deviceName}: ${err}`, true, () => window.connectDevice(deviceData, sourceBtn));
            if (db && targetUid) {
                db.collection("users").doc(targetUid).collection("sessions").doc("current").set({
                    active: false,
                    host: null,
                    port: 0,
                    updatedAt: getFirestoreTimestamp()
                }, { merge: true }).catch(() => {});
            }
        }).finally(() => {
            if (sourceBtn) setButtonLoading(sourceBtn, false);
        });
    } catch (err) {
        if (sourceBtn) setButtonLoading(sourceBtn, false);
        showStatus(`Connection initiation failed: ${err}`, true);
        if (db && targetUid) {
            db.collection("users").doc(targetUid).collection("sessions").doc("current").set({
                active: false,
                host: null,
                port: 0,
                updatedAt: getFirestoreTimestamp()
            }, { merge: true }).catch(() => {});
        }
    }
};

const btnDisconnect = document.getElementById("btn-disconnect");
if (btnDisconnect) {
    btnDisconnect.addEventListener("click", () => {
        if (!window.pywebview || !window.pywebview.api) return;
        btnDisconnect.disabled = true;
        const uid = (activeSession && activeSession.uid) || getCurrentUid();
        window.pywebview.api.stop_mirror().then(() => {
            showStatus("Stream disconnected.", false);
            setDisconnectVisible(false);
            activeSession = null;
            if (db && uid) {
                db.collection("users").doc(uid).collection("sessions").doc("current").set({
                    active: false,
                    host: null,
                    port: 0,
                    updatedAt: getFirestoreTimestamp()
                }, { merge: true }).catch(() => {});
            }
        }).finally(() => {
            btnDisconnect.disabled = false;
        });
    });
}

function loadPairedDevices() {
    if (!window.pywebview || !window.pywebview.api || !window.pywebview.api.get_paired_devices) return;
    const pairedList = document.getElementById("paired-list");
    const pairedCount = document.getElementById("paired-count");
    if (!pairedList) return;

    window.pywebview.api.get_paired_devices().then((devices) => {
        if (devices && devices.length > 0) {
            if (pairedCount) pairedCount.innerText = String(devices.length);
            pairedList.innerHTML = devices.map(d => {
                const name = escapeHtml(d.name || "Known Device");
                return `
                    <div class="device-item">
                        <div class="device-info">
                            <div class="device-icon">
                                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                    <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
                                    <line x1="12" y1="18" x2="12.01" y2="18"></line>
                                </svg>
                            </div>
                            <div class="device-details">
                                <div class="device-name">${name}</div>
                                <div class="status-badge badge-local">● Linked</div>
                            </div>
                        </div>
                        <div class="device-actions">
                            <button class="btn-primary btn-sm btn-connect-paired" data-dev='${escapeHtml(JSON.stringify(d))}'>
                                <span class="btn-label">Mirror</span>
                                <span class="spinner spinner-sm hidden"></span>
                            </button>
                        </div>
                    </div>
                `;
            }).join("");

            pairedList.querySelectorAll(".btn-connect-paired").forEach(b => {
                b.addEventListener("click", () => {
                    try {
                        const dev = JSON.parse(b.getAttribute("data-dev"));
                        window.connectDevice(dev, b);
                    } catch (e) {}
                });
            });
        } else {
            if (pairedCount) pairedCount.innerText = "0";
            pairedList.innerHTML = '<div class="empty-state">No recent devices</div>';
        }
    }).catch(() => {});
}

window.updateDiscoveredDevices = function(devices) {
    const list = document.getElementById("discovered-list");
    const countEl = document.getElementById("discovered-count");
    if (!list) return;

    const wirelessDevices = devices || [];
    if (wirelessDevices.length > 0) {
        if (countEl) countEl.innerText = String(wirelessDevices.length);
        list.innerHTML = wirelessDevices.map(d => {
            const name = escapeHtml(d.name || "Android Phone");
            return `
                <div class="device-item">
                    <div class="device-info">
                        <div class="device-icon">
                            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
                                <line x1="12" y1="18" x2="12.01" y2="18"></line>
                            </svg>
                        </div>
                        <div class="device-details">
                            <div class="device-name">${name}</div>
                            <div class="status-badge badge-local">● Ready (Local)</div>
                        </div>
                    </div>
                    <div class="device-actions">
                        <button class="btn-primary btn-sm btn-connect-discovered" data-dev='${escapeHtml(JSON.stringify(d))}'>
                            <span class="btn-label">Mirror</span>
                            <span class="spinner spinner-sm hidden"></span>
                        </button>
                    </div>
                </div>
            `;
        }).join("");

        list.querySelectorAll(".btn-connect-discovered").forEach(b => {
            b.addEventListener("click", () => {
                try {
                    const dev = JSON.parse(b.getAttribute("data-dev"));
                    window.connectDevice(dev, b);
                } catch (e) {}
            });
        });
    } else {
        if (countEl) countEl.innerText = "0";
        list.innerHTML = '<div class="empty-state">Open LinktoMac on your phone to link automatically.</div>';
    }
};

function loadDiscoveredDevices() {
    if (!window.pywebview || !window.pywebview.api || !window.pywebview.api.get_discovered_devices) return;
    window.pywebview.api.get_discovered_devices().then((devices) => {
        window.updateDiscoveredDevices(devices);
    }).catch(() => {});
}

window.addEventListener('pywebviewready', () => {
    if (!getCurrentUid() && window.pywebview && window.pywebview.api && window.pywebview.api.get_saved_user) {
        window.pywebview.api.get_saved_user().then((user) => {
            if (user && user.uid) {
                enterDashboard(user);
            }
        }).catch(() => {});
    }
    if (window.pywebview && window.pywebview.api && window.pywebview.api.get_usb_device) {
        window.pywebview.api.get_usb_device().then((usbDev) => {
            if (usbDev) window.updateUsbDevice(usbDev);
        }).catch(() => {});
    }
    loadDiscoveredDevices();
    loadPairedDevices();
});

setInterval(() => {
    loadDiscoveredDevices();
    if (window.pywebview && window.pywebview.api && window.pywebview.api.get_usb_device) {
        window.pywebview.api.get_usb_device().then((usbDev) => {
            if (usbDev) window.updateUsbDevice(usbDev);
        }).catch(() => {});
    }
}, 2500);
