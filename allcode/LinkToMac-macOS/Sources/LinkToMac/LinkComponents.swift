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
