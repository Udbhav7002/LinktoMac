# Task 11 — Web Orchestrator UI Redesign

> Prerequisite: Task 01 (token reference). **Replace** `orchestrator/ui/style.css` and
> `orchestrator/ui/index.html`; apply a small, explicitly-scoped patch to `orchestrator/ui/script.js`.

## Context

The web orchestrator runs in a fixed **400×600 pywebview** window: header, login screen, dashboard
(tabs: local devices / remote relay), status banner, cable banner, device lists, footer disconnect,
session-recovery overlay. Today it is dark-only, blue-accented, with emoji icons (⚡), inline styles,
and duplicate Google SVGs.

Redesign goals: same "Quiet Premium" tokens (light + dark via `data-theme`), matte cards + hairlines,
one accent, inline SVG icons (no emoji), no inline `style` attributes, fixed 400×600 layout preserved.

**Hard constraint — `script.js` hooks.** Every `id`, every class used by `script.js`, and the
`hidden`/`active` state classes MUST survive exactly: `#login-screen`, `#dashboard-screen`,
`#btn-google-login`, `#btn-google-login-remote`, `#user-avatar`, `#user-email`, `#btn-signout`,
`#tab-local`, `#tab-remote`, `#view-local`, `#view-remote`, `#status-message`, `#status-text`,
`#status-retry-btn`, `#cable-banner`, `#cable-device-name`, `#btn-cable-mirror`,
`#discovered-count`, `#discovered-list`, `#recent-section`, `#paired-count`, `#paired-list`,
`#remote-unauth`, `#remote-auth`, `#remote-count`, `#remote-device-list`, `#btn-disconnect`,
`#session-recovery-hud`, `#recovery-message`, `#recovery-attempt`, `#btn-recovery-retry`,
`#btn-recovery-cancel`, plus dynamic classes: `.device-item`, `.device-info`, `.device-icon`,
`.device-details`, `.device-name`, `.status-badge .badge-local|.badge-remote|.badge-cable`,
`.device-actions`, `.btn-connect-device`, `.btn-label`, `.spinner`, `.spinner-sm`, `.empty-state`.
The Google "G" logo SVG stays multi-colored — it is a brand mark, not decoration.

---

## File 1 — `orchestrator/ui/style.css` (replace entirely)

```css
/* ============================================================
   LinktoMac — Quiet Premium (matches LinkTheme.swift / .kt)
   Matte · minimal · no gradients · no neon · light + dark
   ============================================================ */

:root {
  --bg:             #F5F5F7;
  --surface:        #FFFFFF;
  --surface-alt:    #F2F2F7;
  --text-primary:   #1D1D1F;
  --text-secondary: #6E6E73;
  --text-tertiary:  #AEAEB2;
  --hairline:       rgba(0, 0, 0, 0.08);
  --separator:      rgba(0, 0, 0, 0.06);
  --accent:         #0071E3;
  --accent-quiet:   rgba(0, 113, 227, 0.10);
  --on-accent:      #FFFFFF;
  --success:        #34C759;
  --warning:        #FF9500;
  --danger:         #FF3B30;
  --danger-quiet:   rgba(255, 59, 48, 0.10);
  --radius-control: 8px;
  --radius-card:    12px;
  --radius-overlay: 16px;
  --font: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, sans-serif;
  --mono: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
  color-scheme: light;
}

[data-theme="dark"] {
  --bg:             #1C1C1E;
  --surface:        #2C2C2E;
  --surface-alt:    #38383A;
  --text-primary:   #F5F5F7;
  --text-secondary: #98989D;
  --text-tertiary:  #636366;
  --hairline:       rgba(255, 255, 255, 0.10);
  --separator:      rgba(255, 255, 255, 0.08);
  --accent:         #0A84FF;
  --accent-quiet:   rgba(10, 132, 255, 0.14);
  --success:        #30D158;
  --warning:        #FF9F0A;
  --danger:         #FF453A;
  --danger-quiet:   rgba(255, 69, 58, 0.14);
  color-scheme: dark;
}

* { box-sizing: border-box; margin: 0; padding: 0; }

html, body {
  height: 100%;
  font-family: var(--font);
  font-size: 13px;
  line-height: 1.45;
  color: var(--text-primary);
  background: var(--bg);
  -webkit-font-smoothing: antialiased;
}

/* ---------- Layout shell (400×600 pywebview window) ---------- */
body { display: flex; flex-direction: column; overflow: hidden; }

.app-header {
  flex: none;
  padding: 12px 16px;
  border-bottom: 1px solid var(--hairline);
  background: var(--surface);
}

.logo { display: flex; align-items: center; gap: 8px; }
.logo svg { width: 16px; height: 16px; color: var(--accent); }
.logo h1 { font-size: 14px; font-weight: 600; letter-spacing: -0.01em; }

.screen { display: none; flex: 1; flex-direction: column; min-height: 0; }
.screen.active { display: flex; }
.hidden { display: none !important; }

/* ---------- Login ---------- */
#login-screen { justify-content: center; }

.login-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 32px 24px;
  gap: 12px;
}

.hero-icon {
  width: 44px; height: 44px;
  color: var(--text-tertiary);
}

.login-center h2 { font-size: 17px; font-weight: 600; }
.login-center p {
  font-size: 13px;
  color: var(--text-secondary);
  max-width: 280px;
}

/* ---------- Buttons ---------- */
button {
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  border-radius: var(--radius-control);
  color: var(--text-primary);
  background: transparent;
  transition: background 0.15s ease, color 0.15s ease, opacity 0.15s ease;
}

button:active { opacity: 0.8; }
button:disabled { opacity: 0.4; cursor: default; }

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  min-height: 30px;
  background: var(--accent);
  color: var(--on-accent);
  font-weight: 600;
}
.btn-primary:hover { opacity: 0.9; }

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  min-height: 30px;
  background: var(--surface-alt);
  color: var(--text-primary);
}
.btn-secondary:hover { background: var(--hairline); }

.btn-danger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  min-height: 30px;
  background: var(--danger);
  color: #fff;
  font-weight: 600;
}
.btn-danger:hover { opacity: 0.9; }

.btn-text { color: var(--accent); padding: 4px 6px; }
.btn-text:hover { background: var(--accent-quiet); }

.btn-sm { padding: 5px 10px; min-height: 26px; font-size: 12px; }
.btn-xs { padding: 3px 8px; min-height: 22px; font-size: 11px; }

.google-btn {
  background: var(--surface);
  color: var(--text-primary);
  border: 1px solid var(--hairline);
  font-weight: 600;
}
.google-btn:hover { background: var(--surface-alt); }

/* ---------- Profile bar ---------- */
.user-profile-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--separator);
}

.user-info { display: flex; align-items: center; gap: 8px; min-width: 0; }

.avatar {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--on-accent);
  font-size: 11px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
  flex: none;
}

#user-email {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ---------- Tabs ---------- */
.tab-bar {
  display: flex;
  gap: 4px;
  padding: 10px 16px 0;
}

.tab-btn {
  flex: 1;
  padding: 6px 8px;
  border-radius: var(--radius-control);
  font-size: 12px;
  color: var(--text-secondary);
  background: transparent;
}
.tab-btn:hover { background: var(--surface-alt); }
.tab-btn.active {
  background: var(--accent-quiet);
  color: var(--accent);
  font-weight: 600;
}

/* ---------- Content ---------- */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.tab-view { display: flex; flex-direction: column; gap: 12px; }

/* Status banner (success / error messages) */
.status-message {
  padding: 10px 12px;
  border-radius: var(--radius-control);
  background: var(--surface-alt);
  font-size: 12px;
}
.status-message.error { color: var(--danger); }
.status-message.success { color: var(--success); }

.status-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

/* ---------- Cable banner (USB priority card) ---------- */
.cable-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-card);
}

.cable-card-content { display: flex; align-items: center; gap: 10px; min-width: 0; }

.cable-icon {
  width: 32px; height: 32px;
  flex: none;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-control);
  background: var(--accent-quiet);
  color: var(--accent);
}
.cable-icon svg { width: 16px; height: 16px; }

.cable-info { min-width: 0; }
.cable-title {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ---------- Badges & status chips ---------- */
.badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--surface-alt);
  border-radius: 999px;
  padding: 1px 8px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
  padding: 1px 8px;
  margin-top: 2px;
}
.status-badge::before {
  content: "";
  width: 6px; height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.badge-cable, .badge-local { color: var(--success); background: transparent; padding-left: 0; }
.badge-remote { color: var(--accent); background: transparent; padding-left: 0; }

/* ---------- Device sections ---------- */
.device-section {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--separator);
}

.section-header h2 {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.section-header h2 svg { color: var(--text-tertiary); }

.device-list { display: flex; flex-direction: column; }

.device-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--separator);
}
.device-item:last-child { border-bottom: none; }
.device-item:hover { background: var(--surface-alt); }

.device-info { display: flex; align-items: center; gap: 10px; min-width: 0; }

.device-icon {
  width: 32px; height: 32px;
  flex: none;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-control);
  background: var(--surface-alt);
  color: var(--text-secondary);
}
.device-icon svg { width: 16px; height: 16px; }

.device-details { min-width: 0; }
.device-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.device-actions { flex: none; }
.btn-label { display: inline-flex; }

.empty-state {
  padding: 18px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
}

/* ---------- Remote relay card ---------- */
.remote-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 10px;
  padding: 20px 16px;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-card);
}
.remote-card .hero-icon { width: 36px; height: 36px; }
.remote-card h3 { font-size: 14px; font-weight: 600; }
.remote-card p {
  font-size: 12px;
  color: var(--text-secondary);
  max-width: 300px;
}

/* ---------- Footer ---------- */
.app-footer {
  flex: none;
  padding: 10px 16px;
  border-top: 1px solid var(--hairline);
  background: var(--surface);
}
.action-buttons { display: flex; }
.action-buttons .btn-danger { width: 100%; justify-content: center; }

/* ---------- Recovery HUD overlay ---------- */
.overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);   /* scrim only — no blur (matte rule) */
}

.recovery-card {
  width: 320px;
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-overlay);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);   /* the one allowed shadow: floating overlay */
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recovery-header { display: flex; align-items: center; gap: 8px; }
.recovery-header h3 { font-size: 15px; font-weight: 600; }

/* Pulsing dot = active retry feedback (functional motion, opacity only) */
.pulse-indicator {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--warning);
  animation: pulse 1.6s ease-in-out infinite;
}
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }

.recovery-card p {
  font-size: 12px;
  color: var(--text-secondary);
}

.recovery-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

/* ---------- Spinners (functional loading feedback) ---------- */
.spinner {
  width: 16px; height: 16px;
  border: 2px solid var(--hairline);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.spinner-sm { width: 12px; height: 12px; border-width: 1.5px; }
@keyframes spin { to { transform: rotate(360deg); } }

.recovery-actions { display: flex; gap: 8px; }
.recovery-actions button { flex: 1; justify-content: center; }

/* ---------- Scrollbar (quiet) ---------- */
.content::-webkit-scrollbar { width: 8px; }
.content::-webkit-scrollbar-thumb {
  background: var(--hairline);
  border-radius: 4px;
}
.content::-webkit-scrollbar-track { background: transparent; }
```

---

## File 2 — `orchestrator/ui/index.html` (replace entirely)

Every `id`/class/hook from the original is preserved. Changes: emoji → SVG, inline styles removed,
static "●" bullets removed (CSS `::before` dot replaces them), Google SVG kept once per button
(it is a brand mark).

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LinktoMac</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="app-header">
        <div class="logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
            </svg>
            <h1>LinktoMac</h1>
        </div>
    </header>

    <!-- STATE 1: GOOGLE LOGIN SCREEN -->
    <div id="login-screen" class="screen hidden">
        <div class="login-center">
            <svg class="hero-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
                <line x1="12" y1="18" x2="12.01" y2="18"></line>
            </svg>
            <h2>Link your Android Phone</h2>
            <p>Control your phone with your Mac's mouse and keyboard, with zero latency.</p>

            <button id="btn-google-login" class="btn-primary google-btn login-button-wide">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                </svg>
                Sign in with Google
            </button>
        </div>
    </div>

    <!-- STATE 2: ZERO-CONFIG DASHBOARD (CONNECTION HUB) -->
    <div id="dashboard-screen" class="screen active">
        <div class="user-profile-bar">
            <div class="user-info">
                <div class="avatar" id="user-avatar">L</div>
                <span id="user-email">Local Mode</span>
            </div>
            <button id="btn-signout" class="btn-text btn-sm hidden">Sign Out</button>
        </div>

        <!-- Tab Bar: Local vs Remote -->
        <div class="tab-bar">
            <button id="tab-local" class="tab-btn active">Local · Wi-Fi &amp; USB</button>
            <button id="tab-remote" class="tab-btn">Remote · Cloud</button>
        </div>

        <div class="content">
            <!-- Dynamic status / error banner -->
            <div id="status-message" class="status-message hidden">
                <div class="status-content">
                    <span id="status-text"></span>
                    <button id="status-retry-btn" class="btn-xs btn-primary hidden">Retry</button>
                </div>
            </div>

            <!-- TAB 1: LOCAL DEVICES (DEFAULT) -->
            <div id="view-local" class="tab-view">
                <!-- USB cable priority banner -->
                <div id="cable-banner" class="cable-card hidden">
                    <div class="cable-card-content">
                        <div class="cable-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                            </svg>
                        </div>
                        <div class="cable-info">
                            <div class="cable-title" id="cable-device-name">USB Cable Connected</div>
                            <div class="status-badge badge-cable">Ready · Fastest 60 FPS</div>
                        </div>
                    </div>
                    <button id="btn-cable-mirror" class="btn-primary btn-sm">Mirror Now</button>
                </div>

                <!-- Discovered local Wi-Fi devices -->
                <div class="device-section">
                    <div class="section-header">
                        <h2>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
                                <line x1="12" y1="18" x2="12.01" y2="18"></line>
                            </svg>
                            Your Devices
                        </h2>
                        <span id="discovered-count" class="badge">0</span>
                    </div>
                    <div id="discovered-list" class="device-list">
                        <div class="empty-state">Waiting for your Android phone to link...</div>
                    </div>
                </div>

                <!-- Recent linked devices -->
                <div class="device-section" id="recent-section">
                    <div class="section-header">
                        <h2>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <circle cx="12" cy="12" r="10"></circle>
                                <polyline points="12 6 12 12 16 14"></polyline>
                            </svg>
                            Recent Devices
                        </h2>
                        <span id="paired-count" class="badge">0</span>
                    </div>
                    <div id="paired-list" class="device-list">
                        <div class="empty-state">No recent devices</div>
                    </div>
                </div>
            </div>

            <!-- TAB 2: REMOTE CLOUD RELAY (OPTIONAL) -->
            <div id="view-remote" class="tab-view hidden">
                <div id="remote-unauth" class="remote-card">
                    <svg class="hero-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path>
                    </svg>
                    <h3>Remote Internet Relay</h3>
                    <p>Sign in with your Google Account on both Mac and Android to mirror seamlessly when away from your home Wi-Fi network.</p>
                    <button id="btn-google-login-remote" class="btn-primary google-btn">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                        </svg>
                        Sign in with Google
                    </button>
                </div>
                <div id="remote-auth" class="device-section hidden">
                    <div class="section-header">
                        <h2>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"></path>
                            </svg>
                            Cloud Linked Devices
                        </h2>
                        <span id="remote-count" class="badge">0</span>
                    </div>
                    <div id="remote-device-list" class="device-list">
                        <div class="empty-state">Waiting for phone cloud sync...</div>
                    </div>
                </div>
            </div>
        </div>

        <div class="app-footer">
            <div class="action-buttons">
                <button id="btn-disconnect" class="btn-danger">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                        <line x1="9" y1="9" x2="15" y2="15"></line>
                        <line x1="15" y1="9" x2="9" y2="15"></line>
                    </svg>
                    Disconnect Active Stream
                </button>
            </div>
        </div>
    </div>

    <!-- SESSION RECOVERY HUD OVERLAY -->
    <div id="session-recovery-hud" class="overlay hidden">
        <div class="recovery-card">
            <div class="recovery-header">
                <div class="pulse-indicator"></div>
                <h3>Reconnecting Stream</h3>
            </div>
            <p id="recovery-message">Connection interrupted. Resuming session...</p>
            <div class="recovery-progress">
                <div class="spinner"></div>
                <span id="recovery-attempt">Attempt 1 / 5</span>
            </div>
            <div class="recovery-actions">
                <button id="btn-recovery-retry" class="btn-primary btn-sm">Retry Now</button>
                <button id="btn-recovery-cancel" class="btn-secondary btn-sm">Cancel</button>
            </div>
        </div>
    </div>

    <!-- Firebase SDKs -->
    <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-app-compat.js"></script>
    <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-auth-compat.js"></script>
    <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-firestore-compat.js"></script>

    <script src="script.js"></script>
</body>
</html>
```

Add this small rule (append to `style.css`) to replace the removed inline widths:

```css
/* Inline-style replacements */
.login-button-wide { width: 100%; justify-content: center; }
```

---

## File 3 — `orchestrator/ui/script.js` (SCOPED PATCH — do not refactor anything else)

Apply exactly these changes and nothing else:

**1. Theme bootstrapping — insert at the very top of the file (line 1):**

```js
// --- Quiet Premium theme bootstrap (presentation only) ---
(function () {
    var root = document.documentElement;
    function applyTheme() {
        root.setAttribute("data-theme",
            window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    }
    applyTheme();
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", applyTheme);
})();
```

**2. Cosmetic string patches (JS-rendered markup only — logic untouched):**

- In `renderDeviceCard`, replace
  `<div class="status-badge ${badgeClass}">● ${escapeHtml(badgeText)}</div>`
  with
  `<div class="status-badge ${badgeClass}">${escapeHtml(badgeText)}</div>`
  (the CSS `::before` dot renders the indicator).
- If any other template string contains a literal `●` or an emoji glyph, delete that character —
  nothing else in those strings changes.

**Everything else in `script.js` — tab switching, `window.updateUsbDevice`, `showStatus`,
`setButtonLoading`, `attemptSessionRecovery`, Firestore listeners, `escapeHtml`, Google auth —
stays byte-identical.**

---

## Acceptance Checklist

- [ ] All IDs/classes above still exist (diff the new HTML against the hook list — zero missing).
- [ ] Window is still a fixed 400×600-friendly column layout; no horizontal scroll.
- [ ] No emoji anywhere in HTML or JS templates; no `style="…"` attributes in the HTML.
- [ ] No `backdrop-filter`, no gradients, no `#4ADE80`; the only shadow is on `.recovery-card`.
- [ ] Light AND dark both render (toggle macOS appearance — pywebview follows the system webview).
- [ ] Tab switching, cable banner (`window.updateUsbDevice`), status banner auto-hide, recovery HUD
      (spinner + attempt counter + retry/cancel) all still work — verified in the running app.
- [ ] Google sign-in buttons render with the brand G (multi-color allowed) on a matte surface.
