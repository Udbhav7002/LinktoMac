# LINKTOMAC UI MASTER PROMPT — "Quiet Premium" Redesign

> **You are the implementation agent.** This file is your contract. Read it fully before writing any code.
> Your job: restyle every LinktoMac surface (macOS app, Android app, web orchestrator, menu bar) to one
> unified, minimal, matte design system — **without changing any behavior, protocol, or data flow**.

---

## 1. Mission

LinktoMac already *works*: pairing, SMS, notifications, files, photos, clipboard, mirroring, and call
control all function over USB / local Wi-Fi. What it lacks is a **unified premium look**. Today the app
ships with stock controls, three different greens, ad-hoc radii, and zero design tokens.

Your mission: make every surface feel like it was designed by one careful hand — **Apple-native,
minimal, matte, and quiet** — so the app *looks* rich precisely because it is restrained.

## 2. The Client's Design Brief (verbatim — treat as law)

> *"keep it very minimal and simple to use, it need to give a rich look. and i need the colours to be
> matt and no gradients or neon colours very simple and minimal"*

Derived hard rules:

1. **Matte only.** Solid opaque fills. No gradients, no glassmorphism blur, no glows, no neon.
2. **Minimal & simple to use.** Fewer visual elements per screen, clear hierarchy, generous whitespace.
3. **Rich look through restraint.** Hairline borders, consistent radii, one accent color, quiet type ramp.
4. **Apple-native direction.** HIG-compliant on macOS (system fonts, SF Symbols, native controls),
   Material 3 on Android with the same token values, web follows the same palette.
5. **Light + dark themes**, driven by system appearance, via semantic tokens.
6. **Minimal motion.** Functional feedback only (state fades, HUD entrance, progress). Nothing decorative.

## 3. Design System — "Quiet Premium"

### 3.1 Brand & Semantic Palette

One brand accent (**blue**) + three semantic status colors + neutral surfaces. All matte solids.

| Token | Light | Dark | Usage |
|---|---|---|---|
| `accent` | `#0071E3` | `#0A84FF` | Primary actions, selected states, outgoing chat bubble, links, QR accents |
| `accentQuiet` | accent @ 10% opacity | accent @ 14% | Tinted button fills, selected chips, drop-target fill |
| `success` | `#34C759` | `#30D158` | Connected dot ONLY, transfer complete, permission granted |
| `warning` | `#FF9500` | `#FF9F0A` | Waiting / disconnected dot, ring phone |
| `danger` | `#FF3B30` | `#FF453A` | Decline/end call, stop streams, disconnect |
| `canvas` | `#F5F5F7` | `#1C1C1E` | Window / page background |
| `surface` | `#FFFFFF` | `#2C2C2E` | Cards, elevated panels |
| `surfaceAlt` | `#F2F2F7` | `#38383A` | Inputs, wells, list hover, secondary chips |
| `textPrimary` | `#1D1D1F` | `#F5F5F7` | Headlines, body |
| `textSecondary` | `#6E6E73` | `#98989D` | Subtitles, captions |
| `textTertiary` | `#AEAEB2` | `#636366` | Placeholders, empty states |
| `hairline` | `rgba(0,0,0,0.08)` | `rgba(255,255,255,0.10)` | 1px card & divider borders |
| `videoWell` | `#000000` | `#000000` | Mirror/camera video canvas only |

**Status colors are semantic only.** Never use green as decoration. The old "green everywhere"
accent is retired. Blue is the brand.

### 3.2 Typography (system fonts only — never install fonts)

| Style | macOS (SF Pro) | Android (Roboto) | Web |
|---|---|---|---|
| Hero | `.title2.bold()` (~22pt) | 20sp / 600 | 20px / 600 |
| Section | `.headline` (~13pt semibold) | 16sp / 600 | 16px / 600 |
| Body | `.body` / `.subheadline` | 14sp / 400 | 14px / 400 |
| Caption | `.caption` / `.caption2` | 12sp / 400 | 12px / 400 |
| Mono | `.system(.body, design: .monospaced)` | `FontFamily.Monospace` | `ui-monospace` |

Mono is reserved for: IP addresses, ports, pairing tokens, file paths, clipboard content.

### 3.3 Spacing, Radii, Elevation

- **Spacing — 4pt grid:** 4, 8, 12, 16, 20, 24, 32. Card padding default 16–20. Screen padding 24.
- **Radii:** controls 8, cards 12, overlays 16. Capsules for pills/badges. Never mix.
- **Elevation = hairline borders, not shadows.** Cards are `surface` fill + 1px `hairline` stroke.
  Shadows are allowed ONLY on true floating overlays (Incoming Call HUD): black 15%, radius 24, y-offset 8.
- No `GroupBox` chrome; use the token `LinkCard` component instead.

### 3.4 Motion (minimal, functional)

Allowed, exactly these:

| Interaction | Spec |
|---|---|
| Section / state change | `easeInOut(duration: 0.15)` fade — macOS `.animation(_, value:)` |
| Incoming Call HUD | `spring(response: 0.28, dampingFraction: 0.9)` slide-from-top + opacity (exists today — keep) |
| Button press | opacity 0.85 (via custom ButtonStyle) |
| Progress | Native `ProgressView` / determinate bars, no animation tricks |

**Forbidden:** matchedGeometryEffect chains, pulsing dots, animated gradients, glow effects,
scale bounce, parallax, skeletons, confetti. If it isn't in the table, don't animate it.

### 3.5 Iconography

- **macOS:** SF Symbols, **regular (non-`.fill`) weight** wherever a base glyph exists:
  `message`, `bell`, `folder`, `photo`, `doc.on.clipboard`, `video`, `qrcode`, `link`.
  Monochrome (`textSecondary`/`accent`) — never multi-colored icon pairs like yellow folder + blue doc;
  use tinted-neutral instead.
- **Android:** Material Symbols **Outlined** style only.
- **Web:** inline SVG, 16px, `stroke: 1.5`, currentColor. **Emoji are banned as icons** (no ⚡🔗📱🔍🌐).

### 3.6 Component Inventory (build once, reuse everywhere)

| Component | Purpose | Defined in |
|---|---|---|
| `LinkTheme` / tokens | All colors, spacing, radii | Task 01 |
| `LinkCard` modifier | Matte surface + hairline | Task 01 |
| `PrimaryButtonStyle` | Solid accent button, white label | Task 01 |
| `QuietButtonStyle` | Tinted (accent @10%) button | Task 01 |
| `LinkTextFieldStyle` | Filled `surfaceAlt` input, hairline | Task 01 |
| `StatusDot` + `StatusPill` | Semantic connection state | Task 01 / 02 |
| `AvatarView` | Deterministic muted initials avatar | Task 01 |
| `CountBadge` | Neutral capsule badge | Task 01 |
| `EmptyStateView` | Icon + title + message | Task 01 |
| `SectionHeader` | Title + trailing actions | Task 01 |
| `TransferRow` | Progress row (accent tint) | Task 06 |
| `PermissionRow` | Granted ✓ / FIX state row (Android) | Task 10 |
| `CallHUD` | Floating overlay card | Task 09 |

## 4. Architecture Guardrails — READ TWICE

This is a **UI-layer-only redesign**. The following are strictly off-limits (no edits, no renames):

- `ConnectionManager.swift`, `ClipboardSyncManager.swift`, `ADBBridge.swift`, `ProtocolModels.swift`,
  `LocalState.swift` (macOS)
- `LinkConnectionService.kt`, `LinkNotificationService.kt`, `LinkTouchAccessibilityService.kt`,
  `ScreenCameraStreamer.kt`, `SmsContactsCallBridge.kt`, `FilePhotosBridge.kt`, `ProtocolModels.kt`
  (Android)
- The wire protocol (frame types 0x01–0x04, JSON envelope keys, ports, Bonjour `_linktomac._tcp`)
- `orchestrator/linktomac/*.py` backend logic, Firestore paths, `window.pywebview.api.*` calls
- All build configs (`Package.swift`, `build.gradle.kts`, `Info.plist`, manifests) — **no new dependencies,
  no new packages, no new fonts.** System fonts and stock SF Symbols / Material Symbols only.

You MAY create new UI-only files (tokens, components, restyled views) and fully replace the bodies of
view files listed in the tasks. All observable behavior (bindings, service calls, gesture handling,
drag & drop, QR generation, share intents) must remain identical.

## 5. Agent Workflow

Work tasks **in order, one at a time**. Each task file is self-contained: context → full code →
acceptance checklist.

| Phase | Task | Deliverable |
|---|---|---|
| 0 | `00-MASTER-PROMPT.md` (this file) | Shared contract |
| 1 | `01-design-tokens.md` | Token + component files on all platforms |
| 2 | `02-macos-app-shell.md` | App entry + sidebar shell |
| 3 | `03`–`09` macOS screens (parallelizable after 02) | All 8 workspaces + HUD |
| 4 | `10-android-app.md` | Android Compose redesign |
| 5 | `11-web-orchestrator.md` | Web UI redesign |
| 6 | `12-menubar.md` | Menu bar polish |

**After each task:** run its acceptance checklist, fix failures, then proceed. Do not batch tasks.
Do not "improve" behavior while styling. When a task's code conflicts with reality (API drift),
adapt the code to the real API — never invent properties.

## 6. Global Acceptance Criteria (apply to every task)

- [ ] Zero gradients, zero blur effects, zero glow/shadow (except the Call HUD shadow), zero neon.
- [ ] No hardcoded colors outside the token files (search for `Color.green`, `0xFF4ADE80`, `#30D158`
      inline usages — all must be gone from view code).
- [ ] No emoji used as icons anywhere.
- [ ] Light AND dark mode both correct (toggle system appearance and verify every screen).
- [ ] All fonts are system fonts; all icons are SF Symbols / Material Symbols / inline SVG.
- [ ] Every control keeps its original action wired to the original manager/service method.
- [ ] Contrast: body text ≥ 4.5:1 against its background in both themes.
- [ ] VoiceOver/accessibility labels preserved or improved (`.help()` tooltips kept on macOS).
- [ ] Builds: `swift build` (macOS target), `./gradlew assembleDebug` (Android), page loads (web).

## 7. Anti-Patterns (instant rejection)

1. `LinearGradient`, `AngularGradient`, `.shadow` on cards, `blur(radius:)`, glow strokes.
2. Neon or Tailwind greens (`#4ADE80`), Apple greens as *brand* color, per-screen accent colors.
3. Baked-in copy like "01/02/03", "v1.61" in UI strings (remove numbering; version lives in one constant).
4. Emoji icons, duplicated inline SVGs, decorative animations.
5. Restyling that touches protocol/manager files or changes any button's functional target.
6. New dependencies (SwiftPM packages, Gradle libs, npm packages, webfonts/CDNs).

## 8. Repo File Map (where code lands)

```
allcode/LinkToMac-macOS/Sources/LinkToMac/
├── LinkTheme.swift            ← NEW (Task 01)
├── LinkComponents.swift       ← NEW (Task 01)
├── LinkToMacApp.swift         ← REPLACE (Task 02)
├── MainDashboardView.swift    ← REPLACE (Task 02 + 03 + 09)
├── FeatureViews.swift         ← REPLACE (Tasks 04–09)
allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/
├── LinkTheme.kt               ← NEW (Task 10)
├── MainActivity.kt            ← REPLACE (Task 10)
orchestrator/ui/
├── style.css                  ← REPLACE (Task 11)
├── index.html                 ← REPLACE (Task 11)
└── script.js                  ← PATCH only (Task 11; API logic untouched)
```

## 9. Verification Commands

```bash
# macOS
cd allcode/LinkToMac-macOS && swift build
./build_mac_app.sh          # full app bundle

# Android
cd allcode/LinkToMac-Android && ./gradlew assembleDebug

# Web (open directly or via pywebview)
python3 -c "import webbrowser; webbrowser.open('file://' + '$PWD/orchestrator/ui/index.html')"
```

Manual QA script per surface: toggle light/dark → open every section → click every primary action →
confirm behavior unchanged → screenshot → compare against anti-pattern list.
