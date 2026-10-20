# Task 01 — Design Tokens & Shared Components

> Read `00-MASTER-PROMPT.md` first. This task creates the token layer for **all three platforms**.
> Nothing else in the pack compiles without these files. Do this task completely before Tasks 02–12.

## Context

Today every color, radius, and spacing value is hardcoded inline, with three competing greens
(`Color.green`, `0xFF4ADE80`, `#30D158`) and a blue web accent. This task introduces **one source of
truth**: `LinkTheme` on macOS, `LinkTheme` on Android, CSS custom properties on web — all carrying the
same "Quiet Premium" values from the master palette table.

**Create these files:**

1. `allcode/LinkToMac-macOS/Sources/LinkToMac/LinkTheme.swift` — colors, spacing, radii, type helpers
2. `allcode/LinkToMac-macOS/Sources/LinkToMac/LinkComponents.swift` — shared SwiftUI components
3. `allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkTheme.kt` — Compose tokens
4. `orchestrator/ui/` — token CSS block goes at the top of the new `style.css` (Task 11 uses it; it is
   reproduced here as the canonical reference)

Do NOT delete or modify any existing file in this task. Only add these new ones.

---

## File 1 — `LinkTheme.swift` (macOS tokens)

```swift
import SwiftUI

// MARK: - LinktoMac Design Tokens — "Quiet Premium"
// Matte, minimal, no gradients, no neon. One brand accent (blue) + semantic status colors.
// This is the ONLY file allowed to define colors. Views must reference LinkTheme.* / LS.* / LR.*.

extension Color {
    /// Dynamic color that resolves per system appearance (macOS 13+).
    static func adaptive(light: NSColor, dark: NSColor) -> Color {
        Color(nsColor: NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(from: [
                .darkAqua, .vibrantDark,
                .accessibilityHighContrastDarkAqua, .accessibilityHighContrastVibrantDark
            ]) != nil
            return isDark ? dark : light
        })
    }
}

enum LinkTheme {

    // MARK: Brand
    static let accent = Color.adaptive(
        light: NSColor(red: 0x00/255, green: 0x71/255, blue: 0xE3/255, alpha: 1),
        dark:  NSColor(red: 0x0A/255, green: 0x84/255, blue: 0xFF/255, alpha: 1)
    )

    // MARK: Semantic status (ONLY for status meaning — never decoration)
    static let success = Color.adaptive(
        light: NSColor(red: 0x34/255, green: 0xC7/255, blue: 0x59/255, alpha: 1),
        dark:  NSColor(red: 0x30/255, green: 0xD1/255, blue: 0x58/255, alpha: 1)
    )
    static let warning = Color.adaptive(
        light: NSColor(red: 0xFF/255, green: 0x95/255, blue: 0x00/255, alpha: 1),
        dark:  NSColor(red: 0xFF/255, green: 0x9F/255, blue: 0x0A/255, alpha: 1)
    )
    static let danger = Color.adaptive(
        light: NSColor(red: 0xFF/255, green: 0x3B/255, blue: 0x30/255, alpha: 1),
        dark:  NSColor(red: 0xFF/255, green: 0x45/255, blue: 0x3A/255, alpha: 1)
    )

    // MARK: Surfaces
    static let canvas     = Color.adaptive(light: NSColor(hex: 0xF5F5F7), dark: NSColor(hex: 0x1C1C1E))
    static let surface    = Color.adaptive(light: NSColor(hex: 0xFFFFFF), dark: NSColor(hex: 0x2C2C2E))
    static let surfaceAlt = Color.adaptive(light: NSColor(hex: 0xF2F2F7), dark: NSColor(hex: 0x38383A))

    // MARK: Text
    static let textPrimary   = Color.adaptive(light: NSColor(hex: 0x1D1D1F), dark: NSColor(hex: 0xF5F5F7))
    static let textSecondary = Color.adaptive(light: NSColor(hex: 0x6E6E73), dark: NSColor(hex: 0x98989D))
    static let textTertiary  = Color.adaptive(light: NSColor(hex: 0xAEAEB2), dark: NSColor(hex: 0x636366))

    // MARK: Hairlines & separators (matte borders instead of shadows)
    static let hairline = Color.adaptive(
        light: NSColor.black.withAlphaComponent(0.08),
        dark:  NSColor.white.withAlphaComponent(0.10)
    )
    static let separator = Color.adaptive(
        light: NSColor.black.withAlphaComponent(0.06),
        dark:  NSColor.white.withAlphaComponent(0.08)
    )
}

extension NSColor {
    convenience init(hex: UInt32) {
        self.init(
            red:   CGFloat((hex >> 16) & 0xFF) / 255.0,
            green: CGFloat((hex >>  8) & 0xFF) / 255.0,
            blue:  CGFloat((hex      ) & 0xFF) / 255.0,
            alpha: 1
        )
    }
}

// MARK: - Spacing (4pt grid)
enum LS {
    static let xs:  CGFloat = 4
    static let s:   CGFloat = 8
    static let m:   CGFloat = 12
    static let l:   CGFloat = 16
    static let xl:  CGFloat = 20
    static let xxl: CGFloat = 24
    static let xxxl: CGFloat = 32
}

// MARK: - Corner radii
enum LR {
    static let control: CGFloat = 8
    static let card:    CGFloat = 12
    static let overlay: CGFloat = 16
}

// MARK: - Muted avatar palette (deterministic, works in both themes)
enum LinkAvatar {
    static let palette: [Color] = [
        Color.adaptive(light: NSColor(hex: 0x7D8A9C), dark: NSColor(hex: 0x8E9AAB)),
        Color.adaptive(light: NSColor(hex: 0x8FA07A), dark: NSColor(hex: 0x9BAC86)),
        Color.adaptive(light: NSColor(hex: 0xB5977C), dark: NSColor(hex: 0xC2A68C)),
        Color.adaptive(light: NSColor(hex: 0x9C7D9C), dark: NSColor(hex: 0xAC8DAC)),
        Color.adaptive(light: NSColor(hex: 0x7C9C93), dark: NSColor(hex: 0x8AAFA6))
    ]

    static func color(for name: String) -> Color {
        var hash = 5381
        for scalar in name.unicodeScalars { hash = (hash << 5) &+ hash &+ Int(scalar.value) }
        return palette[abs(hash) % palette.count]
    }
}
```

---

## File 2 — `LinkComponents.swift` (macOS shared components)

```swift
import SwiftUI

// MARK: - Card surface: matte fill + 1px hairline. NO shadow, NO border, NO GroupBox.
struct LinkCardModifier: ViewModifier {
    var padding: CGFloat = LS.l
    var corner: CGFloat = LR.card

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(LinkTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: corner))
            .overlay(
                RoundedRectangle(cornerRadius: corner)
                    .strokeBorder(LinkTheme.hairline, lineWidth: 1)
            )
    }
}

extension View {
    func linkCard(padding: CGFloat = LS.l, corner: CGFloat = LR.card) -> some View {
        modifier(LinkCardModifier(padding: padding, corner: corner))
    }
}

// MARK: - Buttons

/// Solid accent button (primary action). White label, matte fill, opacity press feedback.
struct PrimaryButtonStyle: ButtonStyle {
    var tint: Color = LinkTheme.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Color.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .frame(minHeight: 28)
            .background(tint)
            .clipShape(RoundedRectangle(cornerRadius: LR.control))
            .opacity(configuration.isPressed ? 0.85 : 1.0)
    }
}

/// Tinted quiet button (secondary action). Accent label on accent@10% fill.
struct QuietButtonStyle: ButtonStyle {
    var tint: Color = LinkTheme.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.medium))
            .foregroundStyle(tint)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(tint.opacity(0.10))
            .clipShape(RoundedRectangle(cornerRadius: LR.control))
            .opacity(configuration.isPressed ? 0.8 : 1.0)
    }
}

/// Small square icon button for toolbars/lists.
struct IconButtonStyle: ButtonStyle {
    var tint: Color = LinkTheme.textSecondary

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.medium))
            .foregroundStyle(tint)
            .frame(width: 28, height: 28)
            .background(LinkTheme.surfaceAlt.opacity(configuration.isPressed ? 1.0 : 0.0))
            .clipShape(RoundedRectangle(cornerRadius: LR.control))
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.7 : 1.0)
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
    static var dangerPrimary: PrimaryButtonStyle { PrimaryButtonStyle(tint: LinkTheme.danger) }
}
extension ButtonStyle where Self == QuietButtonStyle {
    static var quiet: QuietButtonStyle { QuietButtonStyle() }
    static var quietDanger: QuietButtonStyle { QuietButtonStyle(tint: LinkTheme.danger) }
    static var quietWarning: QuietButtonStyle { QuietButtonStyle(tint: LinkTheme.warning) }
}
extension ButtonStyle where Self == IconButtonStyle {
    static var icon: IconButtonStyle { IconButtonStyle() }
}

// MARK: - Filled text field (replaces .roundedBorder everywhere)
struct LinkTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<_Label>) -> some View {
        configuration
            .font(.subheadline)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(LinkTheme.surfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: LR.control))
            .overlay(
                RoundedRectangle(cornerRadius: LR.control)
                    .strokeBorder(LinkTheme.hairline, lineWidth: 1)
            )
    }
}

// MARK: - Semantic status dot
enum LinkStatus { case connected, waiting, disconnected }

struct StatusDot: View {
    let state: LinkStatus
    var size: CGFloat = 7

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
    }

    private var color: Color {
        switch state {
        case .connected:   return LinkTheme.success
        case .waiting:     return LinkTheme.warning
        case .disconnected: return LinkTheme.textTertiary
        }
    }
}

// MARK: - Neutral capsule badge (replaces green count badges)
struct CountBadge: View {
    let count: Int

    var body: some View {
        Text("\(count)")
            .font(.caption2.weight(.bold))
            .foregroundStyle(LinkTheme.textSecondary)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(LinkTheme.surfaceAlt)
            .clipShape(Capsule())
    }
}

// MARK: - Deterministic muted initials avatar
struct AvatarView: View {
    let name: String
    var size: CGFloat = 32

    var body: some View {
        ZStack {
            Circle().fill(LinkAvatar.color(for: name))
            Text(String(name.prefix(1)).uppercased())
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(Color.white)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

// MARK: - Empty state (quiet, tertiary) with optional action button
struct EmptyStateView: View {
    let icon: String
    let title: String
    let message: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: LS.m) {
            Image(systemName: icon)
                .font(.system(size: 36, weight: .light))
                .foregroundStyle(LinkTheme.textTertiary)
            Text(title)
                .font(.headline)
                .foregroundStyle(LinkTheme.textSecondary)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(LinkTheme.textTertiary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 380)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.primary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Section header: title + trailing actions, consistent padding
struct SectionHeader<Trailing: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: LS.m) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.title2.bold()).foregroundStyle(LinkTheme.textPrimary)
                if let subtitle {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(LinkTheme.textSecondary)
                }
            }
            Spacer()
            trailing()
        }
        .padding(.horizontal, LS.xl)
        .padding(.top, LS.xl)
        .padding(.bottom, LS.s)
    }
}

// MARK: - Hairline divider
struct HairlineDivider: View {
    var body: some View {
        Rectangle()
            .fill(LinkTheme.separator)
            .frame(height: 1)
    }
}

// MARK: - Small monospaced value line (IP, port, token, paths)
struct MonoValue: View {
    let label: String
    let value: String
    var prominent: Bool = false

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.caption)
                .foregroundStyle(LinkTheme.textSecondary)
            Spacer(minLength: 12)
            Text(value)
                .font(.system(.subheadline, design: .monospaced))
                .foregroundStyle(prominent ? LinkTheme.accent : LinkTheme.textPrimary)
                .bold(prominent)
                .textSelection(.enabled)
        }
    }
}
```

---

## File 3 — `LinkTheme.kt` (Android Compose tokens)

```kotlin
package com.linktomac.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// LinktoMac Design Tokens — "Quiet Premium" (mirrors LinkTheme.swift / web CSS)
private val BlueLight  = Color(0xFF0071E3)
private val BlueDark   = Color(0xFF0A84FF)
private val GreenLight = Color(0xFF34C759)
private val GreenDark  = Color(0xFF30D158)
private val AmberLight = Color(0xFFFF9500)
private val AmberDark  = Color(0xFFFF9F0A)
private val RedLight   = Color(0xFFFF3B30)
private val RedDark    = Color(0xFFFF453A)

private val LightScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    secondary = Color(0xFF6E6E73),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFD1D1D6),
    error = RedLight
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    secondary = Color(0xFF98989D),
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF38383A),
    onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0xFF48484A),
    error = RedDark
)

// Semantic helpers resolved at composition time.
data class LinkStatusColors(
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val textTertiary: Color,
    val hairline: Color,
    val surfaceAlt: Color
)

@Composable
fun linkStatusColors(): LinkStatusColors {
    val dark = isSystemInDarkTheme()
    return LinkStatusColors(
        accent = MaterialTheme.colorScheme.primary,
        success = if (dark) GreenDark else GreenLight,
        warning = if (dark) AmberDark else AmberLight,
        danger = if (dark) RedDark else RedLight,
        textTertiary = if (dark) Color(0xFF636366) else Color(0xFFAEAEB2),
        hairline = if (dark) Color(0x1AFFFFFF) else Color(0x14000000),
        surfaceAlt = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun LinkToMacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content
    )
}

// Spacing (4dp grid) and radii — single source of truth for Compose UI.
object LS {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp
    val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp
}

object LR {
    val control = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(16.dp)
    val overlay = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(percent = 50)
}

// Type ramp (sp / weight) — use these instead of ad-hoc values.
object LT {
    val hero = 20.sp to 600
    val section = 16.sp to 600
    val body = 14.sp to 400
    val caption = 12.sp to 400
}
```

---

## File 4 — Web CSS token block (canonical reference)

Paste at the top of the new `orchestrator/ui/style.css` (full stylesheet arrives in Task 11):

```css
:root {
  /* Quiet Premium tokens — matches LinkTheme.swift / LinkTheme.kt */
  --bg:            #F5F5F7;
  --surface:       #FFFFFF;
  --surface-alt:   #F2F2F7;
  --text-primary:  #1D1D1F;
  --text-secondary:#6E6E73;
  --text-tertiary: #AEAEB2;
  --hairline:      rgba(0, 0, 0, 0.08);
  --separator:     rgba(0, 0, 0, 0.06);
  --accent:        #0071E3;
  --accent-quiet:  rgba(0, 113, 227, 0.10);
  --on-accent:     #FFFFFF;
  --success:       #34C759;
  --warning:       #FF9500;
  --danger:        #FF3B30;
  --radius-control: 8px;
  --radius-card:    12px;
  --radius-overlay: 16px;
  --font: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, sans-serif;
  --mono: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
}

[data-theme="dark"] {
  --bg:            #1C1C1E;
  --surface:       #2C2C2E;
  --surface-alt:   #38383A;
  --text-primary:  #F5F5F7;
  --text-secondary:#98989D;
  --text-tertiary: #636366;
  --hairline:      rgba(255, 255, 255, 0.10);
  --separator:     rgba(255, 255, 255, 0.08);
  --accent:        #0A84FF;
  --accent-quiet:  rgba(10, 132, 255, 0.14);
  --success:       #30D158;
  --warning:       #FF9F0A;
  --danger:        #FF453A;
}
```

---

## Acceptance Checklist

- [ ] `swift build` succeeds with both new files added to the target (they live inside
      `Sources/LinkToMac`, so SPM picks them up automatically).
- [ ] `LinkTheme.swift` contains ZERO `Color.green` / `.tint(.green)` references.
- [ ] Every color in all four files matches the master palette table in `00-MASTER-PROMPT.md`.
- [ ] No gradients (`LinearGradient` etc.) anywhere in the new files.
- [ ] `LinkComponents.swift` compiles standalone (only imports SwiftUI; no references to
      `ConnectionManager` — components are pure presentation).
- [ ] Android file has no new Gradle dependencies.
