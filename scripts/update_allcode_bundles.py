#!/usr/bin/env python3
"""
update_allcode_bundles.py
Syncs all source code files from allcode/ directly into
docs/bundles/linktomac_complete_android_source_code.md and
docs/bundles/linktomac_complete_macos_source_code.md.
Dynamically discovers all files so no piece of code is ever missed.
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
    "": "bash",
}

EXCLUDED_NAMES = {
    "local.properties",
    ".DS_Store",
}

EXCLUDED_PARTS = {
    ".git",
    ".gradle",
    ".build",
    "build",
    "__pycache__",
    ".idea",
    ".vscode",
}

EXCLUDED_EXTS = {
    ".jar",
    ".class",
    ".dex",
    ".apk",
    ".app",
    ".png",
    ".jpg",
    ".jpeg",
    ".ico",
    ".dylib",
    ".so",
    ".a",
    ".o",
}

def discover_files(directory: str) -> list[str]:
    target_dir = REPO_ROOT / directory
    if not target_dir.exists():
        return []

    collected = []
    for f in target_dir.rglob("*"):
        if not f.is_file():
            continue
        if any(part in EXCLUDED_PARTS for part in f.parts):
            continue
        if f.name in EXCLUDED_NAMES or f.suffix.lower() in EXCLUDED_EXTS:
            continue
        rel = f.relative_to(REPO_ROOT).as_posix()
        collected.append(rel)

    def sort_key(path_str: str):
        # Sort priority: Root configs -> manifests / resources -> sources
        name = Path(path_str).name
        if name in ("Package.swift", "settings.gradle.kts", "build.gradle.kts"):
            return (0, path_str)
        if name.endswith((".properties", ".plist", ".xml")):
            return (1, path_str)
        if name.endswith((".swift", ".kt", ".kts")):
            return (2, path_str)
        return (3, path_str)

    return sorted(collected, key=sort_key)

def generate_markdown(title: str, file_list: list[str]) -> str:
    lines = [
        f"# {title}",
        "",
        "Every file below belongs inside the codebase. Run `python3 scripts/stitch_allcode.py` to extract them automatically.",
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
        content = p.read_text(encoding="utf-8", errors="replace")
        lines.append(f"### FILE: {rel_path}")
        lines.append(f"```{lang}")
        lines.append(content.rstrip())
        lines.append("```")
        lines.append("")
        lines.append("---")
        lines.append("")
    return "\n".join(lines)

def main():
    bundles_dir = REPO_ROOT / "docs" / "bundles"
    bundles_dir.mkdir(parents=True, exist_ok=True)

    android_files = discover_files("allcode/LinkToMac-Android")
    android_md = generate_markdown(
        "LinkToMac — Complete Android Source Code (`allcode/LinkToMac-Android`)",
        android_files
    )
    android_target = bundles_dir / "linktomac_complete_android_source_code.md"
    android_target.write_text(android_md, encoding="utf-8")
    print(f"[OK] Updated {android_target.relative_to(REPO_ROOT)} ({len(android_files)} files)")

    macos_files = discover_files("allcode/LinkToMac-macOS")
    macos_md = generate_markdown(
        "LinkToMac — Complete macOS Source Code (`allcode/LinkToMac-macOS`)",
        macos_files
    )
    macos_target = bundles_dir / "linktomac_complete_macos_source_code.md"
    macos_target.write_text(macos_md, encoding="utf-8")
    print(f"[OK] Updated {macos_target.relative_to(REPO_ROOT)} ({len(macos_files)} files)")

if __name__ == "__main__":
    main()
