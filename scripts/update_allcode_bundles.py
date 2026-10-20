#!/usr/bin/env python3
"""
update_allcode_bundles.py
Syncs all source code files from allcode/ directly into
docs/bundles/linktomac_complete_android_source_code.md and
docs/bundles/linktomac_complete_macos_source_code.md.
"""

import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

EXT_TO_LANG = {
    ".kt": "kotlin",
    ".kts": "kotlin",
    ".swift": "swift",
    ".xml": "xml",
    ".plist": "xml",
    ".properties": "properties",
    ".sh": "bash",
}

ANDROID_FILES = [
    "allcode/LinkToMac-Android/settings.gradle.kts",
    "allcode/LinkToMac-Android/gradle.properties",
    "allcode/LinkToMac-Android/gradle/wrapper/gradle-wrapper.properties",
    "allcode/LinkToMac-Android/build.gradle.kts",
    "allcode/LinkToMac-Android/app/build.gradle.kts",
    "allcode/LinkToMac-Android/app/src/main/AndroidManifest.xml",
    "allcode/LinkToMac-Android/app/src/main/res/values/strings.xml",
    "allcode/LinkToMac-Android/app/src/main/res/xml/accessibility_service_config.xml",
    "allcode/LinkToMac-Android/app/src/main/res/xml/link_accessibility_config.xml",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/ProtocolModels.kt",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkTouchAccessibilityService.kt",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkConnectionService.kt",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkNotificationService.kt",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/SmsContactsCallBridge.kt",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/FilePhotosBridge.kt",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/ScreenCameraStreamer.kt",
    "allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/MainActivity.kt",
]

MACOS_FILES = [
    "allcode/LinkToMac-macOS/Package.swift",
    "allcode/LinkToMac-macOS/Info.plist",
    "allcode/LinkToMac-macOS/build_mac_app.sh",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/ProtocolModels.swift",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/LocalState.swift",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/ConnectionManager.swift",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/ADBBridge.swift",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/ClipboardSyncManager.swift",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/MainDashboardView.swift",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/FeatureViews.swift",
    "allcode/LinkToMac-macOS/Sources/LinkToMac/LinkToMacApp.swift",
]

def generate_markdown(title: str, file_list: list[str]) -> str:
    lines = [
        f"# {title}",
        "",
        f"Every file below belongs inside the codebase. Run `python3 scripts/stitch_allcode.py` to extract them automatically.",
        "",
        "---",
        ""
    ]
    for rel_path in file_list:
        p = REPO_ROOT / rel_path
        if not p.exists():
            print(f"[WARN] File not found: {rel_path}")
            continue
        ext = p.suffix.lower()
        lang = EXT_TO_LANG.get(ext, "text")
        content = p.read_text(encoding="utf-8")
        lines.append(f"### FILE: {rel_path}")
        lines.append(f"```{lang}")
        lines.append(content)
        lines.append("```")
        lines.append("")
        lines.append("---")
        lines.append("")
    return "\n".join(lines)

def main():
    bundles_dir = REPO_ROOT / "docs" / "bundles"
    bundles_dir.mkdir(parents=True, exist_ok=True)

    android_md = generate_markdown(
        "LinkToMac — Complete Android Source Code (`allcode/LinkToMac-Android`)",
        ANDROID_FILES
    )
    android_target = bundles_dir / "linktomac_complete_android_source_code.md"
    android_target.write_text(android_md, encoding="utf-8")
    print(f"[OK] Updated {android_target.relative_to(REPO_ROOT)} ({len(ANDROID_FILES)} files)")

    macos_md = generate_markdown(
        "LinkToMac — Complete macOS Source Code (`allcode/LinkToMac-macOS`)",
        MACOS_FILES
    )
    macos_target = bundles_dir / "linktomac_complete_macos_source_code.md"
    macos_target.write_text(macos_md, encoding="utf-8")
    print(f"[OK] Updated {macos_target.relative_to(REPO_ROOT)} ({len(MACOS_FILES)} files)")

if __name__ == "__main__":
    main()
