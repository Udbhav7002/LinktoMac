# Task 06 — macOS Files & Photos Workspaces

> Prerequisite: Tasks 01–05 done. **Create a new file** `FilesPhotosView.swift` in
> `allcode/LinkToMac-macOS/Sources/LinkToMac/`. Contains `FilesWorkspaceView` and
> `PhotosWorkspaceView` (both names referenced by the shell).

## Context

**Files** is an `HSplitView`: storage browser (left, min 420pt: send button, quick bookmarks, path
bar, file list, drag-and-drop) + transfers queue (right, 260–320pt). **Photos** is an adaptive grid.
Today: green "Send File from Mac", yellow-folder/blue-doc icon pair, green drop overlay, green
progress tints. Redesign: monochrome file icons, quiet bookmark chips, **accent-blue** drop target
(1.5pt hairline stroke, no glow), accent-tinted progress, hover-reveal "Save" on photo cards.

Behavior contract (unchanged):
- Send button → `pickMacFilesToSend()` (NSOpenPanel, multi-select) → `conn.sendFileToAndroid(fileURL:destinationFolder: conn.currentDirectoryPath)`.
- Bookmarks and rows navigate via `conn.requestDirectoryListing(path:)`; parent via `chevron.left`.
- Row "Download" → `conn.pullRemoteFileToMac(remotePath:fileName:revealInFinder: true)`.
- `.onDrop(of: [.fileURL], isTargeted:)` → `handleDroppedMacFiles(providers:)` (unchanged helper).
- Photos: refresh → `conn.requestPhotosList()`; save → `pullRemoteFileToMac`.

---

## File — `allcode/LinkToMac-macOS/Sources/LinkToMac/FilesPhotosView.swift` (create)

```swift
import SwiftUI
import UniformTypeIdentifiers

// MARK: - Files browser & transfers workspace
struct FilesWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared
    @LocalState private var isDropTargeted: Bool = false

    let quickBookmarks: [(String, String)] = [
        ("Downloads", "/sdcard/Download"),
        ("Camera", "/sdcard/DCIM/Camera"),
        ("Pictures", "/sdcard/Pictures"),
        ("Movies", "/sdcard/Movies"),
        ("Documents", "/sdcard/Documents"),
        ("Storage", "/sdcard")
    ]

    var body: some View {
        HSplitView {
            storagePane
            transfersPane
        }
        .background(LinkTheme.canvas)
    }

    // MARK: Left — Android storage
    private var storagePane: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: LS.s) {
                Text("Android storage")
                    .font(.headline)
                    .foregroundStyle(LinkTheme.textPrimary)
                Spacer()
                Button("Send files") { pickMacFilesToSend() }
                    .buttonStyle(.primary)
                Button {
                    conn.requestDirectoryListing(path: conn.currentDirectoryPath)
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.icon)
                .help("Refresh this folder")
            }
            .padding(.horizontal, LS.l)
            .padding(.top, LS.m)
            .padding(.bottom, LS.s)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: LS.s) {
                    ForEach(quickBookmarks, id: \.1) { name, path in
                        Button(name) {
                            conn.requestDirectoryListing(path: path)
                        }
                        .buttonStyle(.quiet)
                        .controlSize(.small)
                    }
                }
                .padding(.horizontal, LS.l)
                .padding(.bottom, LS.s)
            }

            HStack(spacing: LS.s) {
                Button {
                    let parent = (conn.currentDirectoryPath as NSString).deletingLastPathComponent
                    if !parent.isEmpty { conn.requestDirectoryListing(path: parent) }
                } label: {
                    Image(systemName: "chevron.left")
                }
                .buttonStyle(.icon)
                .help("Go to parent folder")
                Text(conn.currentDirectoryPath)
                    .font(.caption.monospaced() as Font)
                    .foregroundStyle(LinkTheme.textSecondary)
                    .textSelection(.enabled)
                    .lineLimit(1)
                    .truncationMode(.head)
                Spacer()
            }
            .padding(.horizontal, LS.l)
            .padding(.bottom, LS.s)

            List(conn.remoteFiles) { file in
                FileRow(file: file)
            }
            .listStyle(.plain)
        }
        .frame(minWidth: 420)
        .onDrop(of: [.fileURL], isTargeted: $isDropTargeted) { providers in
            handleDroppedMacFiles(providers: providers)
            return true
        }
        .overlay {
            if isDropTargeted {
                dropOverlay
            }
        }
    }

    /// Quiet drop target: hairline accent stroke + faint tint. No glow, no heavy border.
    private var dropOverlay: some View {
        ZStack {
            RoundedRectangle(cornerRadius: LR.card)
                .fill(LinkTheme.accent.opacity(0.06))
            RoundedRectangle(cornerRadius: LR.card)
                .strokeBorder(LinkTheme.accent, lineWidth: 1.5)
            Label("Drop files to send to \(conn.currentDirectoryPath)",
                  systemImage: "arrow.down.doc")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(LinkTheme.accent)
                .padding(.horizontal, LS.l)
                .background(LinkTheme.surface)
                .clipShape(Capsule())
        }
        .padding(LS.s)
    }

    // MARK: Right — transfers
    private var transfersPane: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Transfers")
                .font(.headline)
                .foregroundStyle(LinkTheme.textPrimary)
                .padding(.horizontal, LS.m)
                .padding(.top, LS.m)
                .padding(.bottom, LS.s)

            if conn.transfers.isEmpty {
                EmptyStateView(
                    icon: "arrow.left.arrow.right",
                    title: "No transfers yet",
                    message: "Drag files from Finder onto the storage pane to send them to Android."
                )
            } else {
                ScrollView {
                    LazyVStack(spacing: LS.s) {
                        ForEach(conn.transfers) { item in
                            TransferRow(item: item)
                        }
                    }
                    .padding(.horizontal, LS.m)
                    .padding(.bottom, LS.m)
                }
            }
        }
        .frame(minWidth: 260, maxWidth: 320)
        .background(LinkTheme.surface)
        .overlay(
            Rectangle().fill(LinkTheme.hairline).frame(width: 1)
        )
    }

    // MARK: Helpers (behavior preserved from original)
    private func pickMacFilesToSend() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        if panel.runModal() == .OK {
            for url in panel.urls {
                conn.sendFileToAndroid(fileURL: url, destinationFolder: conn.currentDirectoryPath)
            }
        }
    }

    private func handleDroppedMacFiles(providers: [NSItemProvider]) {
        for provider in providers {
            provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, _ in
                if let data = item as? Data,
                   let url = URL(dataRepresentation: data, relativeTo: nil) {
                    Task { @MainActor in
                        ConnectionManager.shared.sendFileToAndroid(
                            fileURL: url,
                            destinationFolder: ConnectionManager.shared.currentDirectoryPath
                        )
                    }
                }
            }
        }
    }
}

// MARK: - File row: monochrome icons (no yellow folder / blue doc pair)
struct FileRow: View {
    let file: RemoteFileItem
    @ObservedObject private var conn = ConnectionManager.shared

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: file.isDirectory ? "folder" : "doc")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(file.isDirectory ? LinkTheme.accent : LinkTheme.textTertiary)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 1) {
                Text(file.name)
                    .font(.subheadline)
                    .foregroundStyle(LinkTheme.textPrimary)
                    .lineLimit(1)
                if !file.isDirectory {
                    Text(ByteCountFormatter.string(fromByteCount: file.sizeBytes, countStyle: .file))
                        .font(.caption2)
                        .foregroundStyle(LinkTheme.textTertiary)
                }
            }

            Spacer(minLength: LS.s)

            if file.isDirectory {
                Button("Open") {
                    conn.requestDirectoryListing(path: file.path)
                }
                .buttonStyle(.quiet)
                .controlSize(.small)
            } else {
                Button("Download") {
                    conn.pullRemoteFileToMac(remotePath: file.path, fileName: file.name, revealInFinder: true)
                }
                .buttonStyle(.quiet)
                .controlSize(.small)
                .help("Download to ~/Downloads/LinkToMac and reveal in Finder")
            }
        }
        .padding(.vertical, 3)
    }
}

// MARK: - Transfer row: accent progress, neutral transport chip
struct TransferRow: View {
    let item: ActiveTransferItem

    var body: some View {
        VStack(alignment: .leading, spacing: LS.xs) {
            HStack(spacing: LS.s) {
                Image(systemName: directionIcon)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(LinkTheme.textSecondary)
                Text(item.fileName)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(LinkTheme.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.middle)
                Spacer(minLength: LS.s)
                Text(item.transport)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(LinkTheme.textSecondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(LinkTheme.surfaceAlt)
                    .clipShape(Capsule())
            }
            ProgressView(value: item.progress)
                .tint(LinkTheme.accent)
            HStack {
                Text(item.direction)
                Spacer()
                Text(item.isCompleted ? "Completed" : "\(Int(item.progress * 100))%")
                    .foregroundStyle(item.isCompleted ? LinkTheme.success : LinkTheme.textSecondary)
            }
            .font(.caption2)
            .foregroundStyle(LinkTheme.textTertiary)
        }
        .padding(LS.m)
        .background(LinkTheme.surfaceAlt.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: LR.control))
    }

    private var directionIcon: String {
        item.direction.contains("Sending") ? "arrow.up" : "arrow.down"
    }
}

// MARK: - Photos workspace
struct PhotosWorkspaceView: View {
    @ObservedObject var conn = ConnectionManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader(title: "Photos",
                          subtitle: "\(conn.remotePhotos.count) recent items from your Android gallery") {
                Button {
                    conn.requestPhotosList()
                } label: {
                    Label("Refresh", systemImage: "arrow.clockwise")
                }
                .buttonStyle(.quiet)
            }

            if conn.remotePhotos.isEmpty {
                EmptyStateView(
                    icon: "photo",
                    title: "No photos loaded",
                    message: "Load your recent Android photos to browse and save them to Finder.",
                    actionTitle: "Load recent photos"
                ) {
                    conn.requestPhotosList()
                }
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: LS.m)],
                              spacing: LS.m) {
                        ForEach(conn.remotePhotos) { photo in
                            PhotoCard(photo: photo)
                        }
                    }
                    .padding(.horizontal, LS.xl)
                    .padding(.bottom, LS.xl)
                }
            }
        }
        .background(LinkTheme.canvas)
    }
}

// MARK: - Photo card: hover-reveal save action
struct PhotoCard: View {
    let photo: RemotePhotoItem
    @ObservedObject private var conn = ConnectionManager.shared
    @LocalState private var isHovering: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: LS.xs) {
            ZStack {
                RoundedRectangle(cornerRadius: LR.control)
                    .fill(LinkTheme.surfaceAlt)
                    .frame(height: 120)

                if let b64 = photo.thumbnailBase64,
                   let data = Data(base64Encoded: b64),
                   let img = NSImage(data: data) {
                    Image(nsImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 120)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: LR.control))
                } else {
                    Image(systemName: photo.isVideo ? "video" : "photo")
                        .font(.title2)
                        .foregroundStyle(LinkTheme.textTertiary)
                }

                if isHovering {
                    Button {
                        conn.pullRemoteFileToMac(remotePath: photo.path, fileName: photo.name, revealInFinder: true)
                    } label: {
                        Label("Save", systemImage: "arrow.down.circle")
                    }
                    .buttonStyle(.primary)
                    .controlSize(.small)
                    .padding(6)
                    .background(LinkTheme.surface)
                    .clipShape(Capsule())
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 1)  // overlay affordance only
                }
            }
            .onHover { isHovering = $0 }

            Text(photo.name)
                .font(.caption)
                .foregroundStyle(LinkTheme.textSecondary)
                .lineLimit(1)
        }
        .padding(LS.s)
        .background(LinkTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: LR.card))
        .overlay(
            RoundedRectangle(cornerRadius: LR.card)
                .strokeBorder(LinkTheme.hairline, lineWidth: 1)
        )
    }
}
```

---

## Acceptance Checklist

- [ ] Zero `Color.yellow` / `Color.blue` icon pairs; file icons are monochrome (accent folder / tertiary doc).
- [ ] Drop overlay: 1.5pt accent stroke + 6% tint — no `lineWidth: 3`, no green.
- [ ] Progress bars tinted accent; "Completed" is the only success-green text.
- [ ] Drag-and-drop from Finder still streams to the dropped-in folder; NSOpenPanel multi-send works.
- [ ] Photo cards reveal "Save" on hover (light + dark), thumbnail decode path unchanged.
- [ ] Transfers pane separated by a 1px hairline, not a heavy divider; transport chips are neutral.
- [ ] Bookmarks navigate to the same six paths as before (labels simplified: "Camera", "Storage").
