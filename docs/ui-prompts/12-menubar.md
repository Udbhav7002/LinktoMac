# Task 12 — Menu Bar Apps Polish

> Prerequisite: Tasks 01–11 done. This task touches two small menu-bar surfaces.

## Context

There are two menu bar implementations:

1. **SwiftUI `MenuBarExtra`** (inside `LinkToMacApp.swift`) — **already restyled in Task 02** with
   `StatusDot`, hairlines, semantic danger "Quit". This task only *verifies* it.
2. **Python rumps app** (`orchestrator/linktomac/menubar_app.py`) — titled with a 🔗 emoji, menu
   items prefixed with 📱🔍🌐 emoji, no empty state. Restyle: plain text title, clean labels,
   a quiet empty state — zero backend changes.

---

## Part 1 — Verify the SwiftUI `MenuBarExtra` (from Task 02)

Confirm in `allcode/LinkToMac-macOS/Sources/LinkToMac/LinkToMacApp.swift`:

- [ ] Label is a single `iphone.radiowaves.left.and.right` / `iphone` SF Symbol (no battery text in
      the bar itself — battery lives in the popover).
- [ ] Popover: status dot + device headline, battery/notifications caption, hairlines between groups,
      clipboard `Toggle`, media buttons, `Quit` in `LinkTheme.danger`.
- [ ] `.menuBarExtraStyle(.window)` so the popover renders as a matte window.
- [ ] No emoji, no green.

## Part 2 — `orchestrator/linktomac/menubar_app.py` (replace ONLY `_run_menubar`)

The rest of the file (`main`, `_run_window_fallback`, the `Api` class, all backend calls) stays
byte-identical.

```python
def _run_menubar():
    """Quiet Premium rumps menu bar implementation."""
    import rumps
    import json
    import threading
    from linktomac.backend import LinktoMacBackend

    backend = LinktoMacBackend()

    class LinktoMacApp(rumps.App):
        def __init__(self):
            super().__init__("LinktoMac", quit_button=None)
            self.backend = backend
            self._refresh_menu()
            self.backend.set_callback(self._on_devices_updated)
            self.backend.start_discovery()

        def _refresh_menu(self):
            items = []
            paired = self.backend.get_paired_devices()

            if paired:
                for dev in paired:
                    name = dev.get("name", dev.get("ip", "Device"))
                    ip = dev.get("ip", "")
                    item = rumps.MenuItem(f"Mirror {name}",
                                          callback=lambda _, i=ip: self._mirror(i))
                    items.append(item)
            else:
                items.append(rumps.MenuItem("No paired devices", callback=None))

            items.append(rumps.separator)
            items.append(rumps.MenuItem("Discover Devices", callback=self._discover))
            items.append(rumps.MenuItem("Remote Session…", callback=self._remote))
            items.append(rumps.separator)
            items.append(rumps.MenuItem("Quit LinktoMac", callback=rumps.quit_application))
            self.menu = items

        def _on_devices_updated(self, devices):
            self._refresh_menu()

        def _mirror(self, ip):
            success, err = self.backend.launch_mirror(ip)
            if not success:
                rumps.alert(f"Mirror failed: {err}")

        def _discover(self, _):
            rumps.notification("LinktoMac", "Scanning…", "Looking for Android devices on your network.")
            self.backend.start_discovery()

        def _remote(self, _):
            result = self.backend.start_remote_session()
            if result.get("success"):
                rumps.alert(f"Remote code:\n{result['code']}")
            else:
                rumps.alert(f"Failed: {result.get('error')}")

    LinktoMacApp().run()
```

Changes from the original, itemized (so reviewers can diff quickly):

| Before | After | Why |
|---|---|---|
| `super().__init__("🔗", …)` | `super().__init__("LinktoMac", …)` | No emoji as icons |
| `📱 Mirror: {name}` | `Mirror {name}` | Clean label |
| `🔍 Discover Devices` | `Discover Devices` | Clean label |
| `🌐 Remote Session…` | `Remote Session…` | Clean label |
| (nothing when empty) | `No paired devices` disabled row | Quiet empty state |

Everything else — `launch_mirror`, `start_discovery`, `start_remote_session`, notifications,
alerts, callbacks — is untouched.

---

## Acceptance Checklist

- [ ] `python3 -c "import ast; ast.parse(open('orchestrator/linktomac/menubar_app.py').read())"` passes.
- [ ] Menu bar title is plain "LinktoMac" text; zero emoji anywhere in the file.
- [ ] Empty state shows "No paired devices"; paired devices list rebuilds on discovery callback
      (same `_on_devices_updated` → `_refresh_menu` flow).
- [ ] Mirror / Discover / Remote Session / Quit all call the same backend methods as before.
- [ ] SwiftUI `MenuBarExtra` checklist (Part 1) verified in the running macOS app.
