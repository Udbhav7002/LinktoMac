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
