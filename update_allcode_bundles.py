#!/usr/bin/env python3
"""
update_allcode_bundles.py
Syncs all source code files from allcode/ directly into
linktomac_complete_android_source_code.md (ALLCODE_ANDROID_COMPLETE.md) and
linktomac_complete_macos_source_code.md (ALLCODE_MACOS_COMPLETE.md).
"""

import os
from pathlib import Path

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
        f"Every file below belongs inside the codebase. Run `python3 stitch_allcode.py` from `LINKTOMAC_MASTER_PLAN.md` to extract them automatically.",
        "",
        "---",
        ""
    ]
    for rel_path in file_list:
        p = Path(rel_path)
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
    android_md = generate_markdown(
        "LinkToMac — Complete Android Source Code (`allcode/LinkToMac-Android`)",
        ANDROID_FILES
    )
    Path("linktomac_complete_android_source_code.md").write_text(android_md, encoding="utf-8")
    print(f"[OK] Updated linktomac_complete_android_source_code.md ({len(ANDROID_FILES)} files)")

    macos_md = generate_markdown(
        "LinkToMac — Complete macOS Source Code (`allcode/LinkToMac-macOS`)",
        MACOS_FILES
    )
    Path("linktomac_complete_macos_source_code.md").write_text(macos_md, encoding="utf-8")
    print(f"[OK] Updated linktomac_complete_macos_source_code.md ({len(MACOS_FILES)} files)")

if __name__ == "__main__":
    main()
