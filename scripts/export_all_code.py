#!/usr/bin/env python3
"""
Exports the complete, clean codebase and project structure of LinktoMac into all_code.md.
Categorizes files, generates comprehensive architecture documentation, project directory tree,
Table of Contents with Markdown anchors, and includes 100% of the source code.
"""

import os
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# File extension to syntax highlight mapping
EXT_MAP = {
    ".py": "python",
    ".kt": "kotlin",
    ".kts": "kotlin",
    ".swift": "swift",
    ".plist": "xml",
    ".java": "java",
    ".cpp": "cpp",
    ".h": "cpp",
    ".hpp": "cpp",
    ".mm": "objectivec",
    ".m": "objectivec",
    ".xml": "xml",
    ".json": "json",
    ".html": "html",
    ".css": "css",
    ".js": "javascript",
    ".sh": "bash",
    ".gradle": "groovy",
    ".properties": "properties",
    ".md": "markdown",
    ".txt": "text",
    ".yml": "yaml",
    ".yaml": "yaml",
    ".ini": "ini",
    ".spec": "python",
}

# Directories to strictly exclude
EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    ".build",
    "build",
    ".system_generated",
    ".user_uploaded",
    ".agents",
    "__pycache__",
    "dist",
    "bin",
    "out",
    ".idea",
    ".vscode",
    ".claude",
    ".zcode",
    "android-client",  # Obsolete module permanently removed
}

# Binary / artifact files to exclude
EXCLUDED_EXTS = {
    ".jar",
    ".dex",
    ".apk",
    ".dmg",
    ".app",
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".ico",
    ".pdf",
    ".so",
    ".dylib",
    ".a",
    ".o",
    ".pyc",
    ".pyo",
    ".class",
    ".bin",
}

EXCLUDED_FILES = {
    "all_code.md",
    ".DS_Store",
    "gradle-wrapper.jar",
    "server.jar",
    "app-debug.apk",
    "local.properties",
}

CATEGORIES = [
    (
        "Standalone Native macOS App (Swift / SwiftUI)",
        lambda p: p.startswith("allcode/LinkToMac-macOS/")
    ),
    (
        "Standalone Android Companion App (Kotlin / Jetpack Compose)",
        lambda p: p.startswith("allcode/LinkToMac-Android/")
    ),
    (
        "macOS Native Client (C++ / Objective-C++ / Metal)",
        lambda p: p.startswith("native-client/")
    ),
    (
        "macOS Python Orchestrator & Web UI",
        lambda p: p.startswith("orchestrator/")
    ),
    (
        "Android Companion App (Kotlin / Legacy)",
        lambda p: p.startswith("android-app/")
    ),
    (
        "Android Headless Server (ADB Mode)",
        lambda p: p.startswith("android-server/")
    ),
    (
        "Root Configuration & Build Scripts",
        lambda p: not ("/" in p) or p.startswith(".github/")
    ),
    (
        "Scripts, Tests & Documentation",
        lambda p: p.startswith("scripts/") or p.startswith("tests/") or p.startswith("docs/")
    ),
]

DIR_ANNOTATIONS = {
    "allcode/LinkToMac-macOS": "Standalone Native macOS App (Swift / SwiftUI)",
    "allcode/LinkToMac-macOS/Sources/LinkToMac": "SwiftUI Views, Controllers & IPC bridges",
    "allcode/LinkToMac-Android": "Standalone Android Companion App (Kotlin / Jetpack Compose)",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android": "Compose UI, Screen Streamer & Accessibility",
    "native-client": "macOS Native Client (C++ / Objective-C++ / Metal)",
    "native-client/src": "Hardware VideoToolbox decoder, Metal renderer, and network pipeline",
    "orchestrator": "macOS Python Orchestrator & Web Dashboard",
    "orchestrator/linktomac": "FastAPI backend, ADB port tunneling, and pairing logic",
    "orchestrator/ui": "Web Dashboard UI (HTML5 / Vanilla JS / CSS)",
    "android-server": "Android Headless Server (ADB Daemon Mode)",
    "android-app": "Android Companion App (Legacy Kotlin Client)",
    "tests": "Adversarial & Multi-tier Stress Test Harnesses",
    "tests/adversarial_m1": "M1: LAN IP, Smart Connect & Handshake tests",
    "tests/adversarial_m2": "M2: DOM offline, USB watcher & multitenant isolation",
    "tests/adversarial_m3": "M3: Packaging and path integrity tests",
    "tests/adversarial_m4": "M4: ABR telemetry, scroll coalescing & fuzzer",
    "tests/adversarial_native": "Native: VideoToolbox decoder & Metal surface stress tests",
    "scripts": "Build, Release, Packaging & Code Stitcher tooling",
    "docs": "Architecture specifications, UI prompts & audit reports",
    "docs/ui-prompts": "UI Design System & component prompt definitions",
    "release": "Release checksums and installation instructions",
    "public": "Distribution downloads & landing portal",
    ".github/workflows": "GitHub Actions CI/CD workflows",
}

def make_anchor(rel_path: str) -> str:
    # Anchor id replaces '/', '.', and '_' to standard hyphen anchor
    return rel_path.replace("/", "-").replace(".", "-").replace("_", "-")

def get_highlight_lang(file_path: Path) -> str:
    name = file_path.name.lower()
    if name == "cmakelists.txt":
        return "cmake"
    if name == "makefile":
        return "makefile"
    if name == "gradlew" or name.endswith(".sh"):
        return "bash"
    if name.endswith(".plist"):
        return "xml"
    return EXT_MAP.get(file_path.suffix.lower(), "text")

def get_fence(content: str) -> str:
    max_b = 3
    runs = re.findall(r"`+", content)
    if runs:
        max_b = max(max_b, max(len(r) for r in runs) + 1)
    return "`" * max_b

def is_text_file(path: Path) -> bool:
    if path.suffix.lower() in EXCLUDED_EXTS:
        return False
    if path.name in EXCLUDED_FILES:
        return False
    try:
        with open(path, "rb") as f:
            chunk = f.read(1024)
            if b"\x00" in chunk:
                return False
        return True
    except Exception:
        return False

def collect_files():
    files = []
    for root, dirs, filenames in os.walk(REPO_ROOT):
        # Exclude directories in-place (allow .github for CI workflows)
        dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS and (not d.startswith(".") or d == ".github")]

        for fn in sorted(filenames):
            if fn in EXCLUDED_FILES:
                continue
            full_path = Path(root) / fn
            rel_path = full_path.relative_to(REPO_ROOT).as_posix()

            if any(part in EXCLUDED_DIRS for part in Path(rel_path).parts):
                continue

            if is_text_file(full_path):
                files.append(rel_path)

    return sorted(files)

def categorize_files(files):
    cat_buckets = {cat[0]: [] for cat in CATEGORIES}
    other_bucket = []

    for f in files:
        assigned = False
        for title, predicate in CATEGORIES:
            if predicate(f):
                cat_buckets[title].append(f)
                assigned = True
                break
        if not assigned:
            other_bucket.append(f)

    result = []
    for title, _ in CATEGORIES:
        if cat_buckets[title]:
            result.append((title, sorted(cat_buckets[title])))
    if other_bucket:
        result.append(("Other Project Files", sorted(other_bucket)))

    return result

def format_directory_tree(dir_path: Path, prefix="") -> list[str]:
    entries = sorted([p for p in dir_path.iterdir() if p.name not in EXCLUDED_DIRS and p.name not in EXCLUDED_FILES and not p.name.startswith(".DS_Store")])
    dirs = [e for e in entries if e.is_dir() and (not e.name.startswith(".") or e.name == ".github")]
    files = [e for e in entries if e.is_file() and is_text_file(e)]

    # Filter out directories that have no text files
    active_dirs = []
    for d in dirs:
        has_files = any(f.is_file() and is_text_file(f) for f in d.rglob("*") if not any(part in EXCLUDED_DIRS for part in f.parts))
        if has_files:
            active_dirs.append(d)

    lines = []
    items = active_dirs + files
    for i, item in enumerate(items):
        is_last = (i == len(items) - 1)
        connector = "└── " if is_last else "├── "
        child_prefix = "    " if is_last else "│   "
        rel = item.relative_to(REPO_ROOT).as_posix()

        annotation = f"  # {DIR_ANNOTATIONS[rel]}" if rel in DIR_ANNOTATIONS else ""
        if item.is_dir():
            lines.append(f"{prefix}{connector}{item.name}/{annotation}")
            lines.extend(format_directory_tree(item, prefix + child_prefix))
        else:
            lines.append(f"{prefix}{connector}{item.name}{annotation}")
    return lines

def generate_all_code():
    files = collect_files()
    categorized = categorize_files(files)
    total_files = len(files)

    total_lines = 0
    for f in files:
        try:
            total_lines += len((REPO_ROOT / f).read_text(encoding="utf-8", errors="replace").splitlines())
        except Exception:
            pass

    tree_lines = ["LinktoMac/"] + format_directory_tree(REPO_ROOT)
    tree_text = "\n".join(tree_lines)

    lines = []
    lines.append("# LinktoMac — Complete Codebase & Project Architecture\n")
    lines.append(f"> **Repository Summary**: `{total_files}` Source & Configuration Files | `{total_lines:,}` Total Lines of Code\n")
    lines.append("LinktoMac is a high-performance, low-latency screen mirroring and remote control ecosystem connecting Android devices with macOS. It features hardware-accelerated 60fps streaming, bi-directional clipboard sync, telephony/SMS management, notification bridging, and low-latency input event injection over USB (ADB) and local Wi-Fi networks.\n")
    lines.append("---\n")

    # Section 1: Architecture Overview
    lines.append("## 1. System Architecture & Component Design\n")
    lines.append("### High-Level Architecture Diagram\n")
    lines.append("```mermaid")
    lines.append("graph TD")
    lines.append("    subgraph Android[\"Android Device\"]")
    lines.append("        App[\"Standalone Android App<br/>(Kotlin / Compose)\"]")
    lines.append("        Svc[\"LinkConnectionService<br/>(MediaProjection / H.264)\"]")
    lines.append("        Acc[\"AccessibilityService<br/>(Touch & Key Injection)\"]")
    lines.append("        Notif[\"Notification / SMS Bridge\"]")
    lines.append("        Server[\"Headless Daemon<br/>(android-server.jar)\"]")
    lines.append("    end")
    lines.append("")
    lines.append("    subgraph Transport[\"Transport Layer\"]")
    lines.append("        USB[\"ADB Reverse Port Forwarding<br/>(localhost:7007 / 7008)\"]")
    lines.append("        LAN[\"LAN TCP/UDP Streaming<br/>(Wi-Fi Direct / Local Subnet)\"]")
    lines.append("        WS[\"WebSocket IPC & Telemetry<br/>(Control & Clipboard)\"]")
    lines.append("    end")
    lines.append("")
    lines.append("    subgraph Mac[\"macOS Host\"]")
    lines.append("        SwiftApp[\"Standalone Native macOS App<br/>(Swift / SwiftUI)\"]")
    lines.append("        NativeClient[\"macOS Native Client<br/>(C++ / Metal / VideoToolbox)\"]")
    lines.append("        Orchestrator[\"Python Orchestrator & Web UI<br/>(FastAPI / WebSockets / Rumps)\"]")
    lines.append("    end")
    lines.append("")
    lines.append("    App --> Svc")
    lines.append("    App --> Acc")
    lines.append("    App --> Notif")
    lines.append("    Svc --> LAN")
    lines.append("    Svc --> USB")
    lines.append("    Server --> USB")
    lines.append("    USB --> Orchestrator")
    lines.append("    LAN --> NativeClient")
    lines.append("    USB --> NativeClient")
    lines.append("    Orchestrator --> SwiftApp")
    lines.append("    NativeClient --> SwiftApp")
    lines.append("```\n")

    lines.append("### Subsystems & Technology Stack\n")
    lines.append("1. **Standalone Native macOS App (`allcode/LinkToMac-macOS`)**")
    lines.append("   - Pure Swift and SwiftUI desktop application optimized for macOS 13+ (Ventura, Sonoma, Sequoia).")
    lines.append("   - Features modular SwiftUI views: `MainDashboardView`, `OverviewView`, `MirrorCameraView`, `MessagesView`, `NotificationsView`, `FilesPhotosView`, `ClipboardView`, and `PairingCallHUDView`.")
    lines.append("   - Built-in `ADBBridge` and `ConnectionManager` manage process lifecycle and auto-discovery.\n")

    lines.append("2. **Standalone Android Companion App (`allcode/LinkToMac-Android`)**")
    lines.append("   - Modern Kotlin application built with Jetpack Compose following Material Design 3 tokens.")
    lines.append("   - `ScreenCameraStreamer` captures screen buffers via `MediaProjection` and hardware-encodes H.264 video with `MediaCodec`.")
    lines.append("   - `LinkTouchAccessibilityService` performs ultra-low-latency gesture, touch tap, and keypress injection.")
    lines.append("   - `LinkNotificationService` and `SmsContactsCallBridge` expose notification syncing, incoming call HUD, and SMS threads.\n")

    lines.append("3. **macOS Native Client (`native-client`)**")
    lines.append("   - High-performance C++ / Objective-C++ streaming engine built with Apple Metal and VideoToolbox.")
    lines.append("   - Zero-copy hardware video decoding via `VTDecompressionSession` (`decoder.cpp`).")
    lines.append("   - Metal texture shader pipeline rendering directly to `CAMetalLayer` (`renderer.mm`).")
    lines.append("   - Native macOS mouse and keyboard event capture mapped directly to binary stream packets (`input_handler.cpp`).")
    lines.append("   - Pybind11 integration (`python_bindings.cpp`) providing Python bindings for the orchestrator.\n")

    lines.append("4. **macOS Python Orchestrator & Web UI (`orchestrator`)**")
    lines.append("   - Lightweight FastAPI & WebSockets service (`backend.py`) orchestrating ADB reverse tunneling, pairing tokens, and LAN fallback.")
    lines.append("   - Native macOS Menu Bar utility (`menubar_app.py`) built with `rumps` for one-click streaming.")
    lines.append("   - Browser-based HTML5 dashboard (`orchestrator/ui/`) for responsive control without desktop app dependencies.\n")

    lines.append("5. **Android Headless Server (`android-server`)**")
    lines.append("   - Rootless ADB daemon executed via `app_process /system/bin com.linktomac.server.Server`.")
    lines.append("   - Uses hidden Android APIs (`SurfaceControl`, `InputManager`) to stream and control devices without requiring an APK to be installed.\n")

    lines.append("6. **Multi-Tier Stress & Adversarial Test Suites (`tests`)**")
    lines.append("   - Extensive adversarial validation suites: `adversarial_m1` (network & handshake), `adversarial_m2` (DOM recovery & USB watcher), `adversarial_m3` (packaging & binary path integrity), `adversarial_m4` (adaptive bitrate telemetry, gesture coalescing, protocol fuzzing), and `adversarial_native` (VideoToolbox decoder stress & rapid surface flip resilience).\n")

    lines.append("7. **Automation, Packaging & Deployment (`scripts`, `Makefile`, `.github`)**")
    lines.append("   - Automated build scripts (`make_release.sh`, `build_mac_app.sh`), code synchronizers (`export_all_code.py`, `update_allcode_bundles.py`, `stitch_allcode.py`), and CI/CD workflows for macOS and Android.\n")
    lines.append("---\n")

    # Section 2: Directory Structure
    lines.append("## 2. Project Directory Structure\n")
    lines.append("```text")
    lines.append(tree_text)
    lines.append("```\n")
    lines.append("---\n")

    # Section 3: Table of Contents
    lines.append("## 3. Complete Source Code Table of Contents\n")
    for title, cat_files in categorized:
        lines.append(f"### {title}\n")
        for f in cat_files:
            anchor = make_anchor(f)
            lines.append(f"- [`{f}`](#{anchor})")
        lines.append("")

    lines.append("---\n")

    # Section 4: File Contents
    lines.append("## 4. Source Code Manifest\n")
    for title, cat_files in categorized:
        lines.append(f"### Category: {title}\n")
        for f in cat_files:
            full_path = REPO_ROOT / f
            anchor = make_anchor(f)
            lang = get_highlight_lang(full_path)

            lines.append(f'<a id="{anchor}"></a>')
            lines.append(f"#### File: `{f}`\n")

            try:
                content = full_path.read_text(encoding="utf-8", errors="replace")
            except Exception as e:
                content = f"// Error reading file: {e}"

            fence = get_fence(content)

            lines.append(f"{fence}{lang}")
            lines.append(content.rstrip())
            lines.append(f"{fence}\n")

    output_text = "\n".join(lines) + "\n"

    # Save to root all_code.md
    root_out = REPO_ROOT / "all_code.md"
    root_out.write_text(output_text, encoding="utf-8")

    # Save to docs/bundles/all_code.md
    bundle_out = REPO_ROOT / "docs" / "bundles" / "all_code.md"
    bundle_out.parent.mkdir(parents=True, exist_ok=True)
    bundle_out.write_text(output_text, encoding="utf-8")

    print(f"Successfully generated all_code.md!")
    print(f"  - Root file:   {root_out} ({root_out.stat().st_size:,} bytes)")
    print(f"  - Bundle file: {bundle_out} ({bundle_out.stat().st_size:,} bytes)")
    print(f"  - Total files included: {total_files}")
    print(f"  - Total code lines:     {total_lines:,}")

if __name__ == "__main__":
    generate_all_code()
