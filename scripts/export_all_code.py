#!/usr/bin/env python3
"""
Exports the complete, clean codebase of LinktoMac into all_code.md.
Categorizes files, generates Table of Contents with Markdown anchors,
and excludes git metadata, build artifacts, binaries, and deleted obsolete modules.
"""

import os
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
    "export_all_code.py",
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

def generate_all_code():
    files = collect_files()
    categorized = categorize_files(files)
    total_files = len(files)

    lines = []
    lines.append("# LinktoMac — Complete Codebase\n")
    lines.append(f"This document consolidates all {total_files} source and configuration files of the LinktoMac project in a clean, structured format.\n")
    lines.append("---\n")
    lines.append("## Table of Contents\n")

    for title, cat_files in categorized:
        lines.append(f"### {title}\n")
        for f in cat_files:
            anchor = make_anchor(f)
            lines.append(f"- [`{f}`](#{anchor})")
        lines.append("")

    lines.append("---\n")

    for title, cat_files in categorized:
        lines.append(f"## {title}\n")
        for f in cat_files:
            full_path = REPO_ROOT / f
            anchor = make_anchor(f)
            lang = get_highlight_lang(full_path)

            lines.append(f'<a id="{anchor}"></a>')
            lines.append(f"### `{f}`\n")

            try:
                content = full_path.read_text(encoding="utf-8", errors="replace")
            except Exception as e:
                content = f"// Error reading file: {e}"

            # Ensure backticks inside markdown files don't prematurely close code block
            fence = "```"
            if "```" in content:
                fence = "````"

            lines.append(f"{fence}{lang}")
            lines.append(content.rstrip())
            lines.append(f"{fence}\n")

    out_file = REPO_ROOT / "docs" / "bundles" / "all_code.md"
    out_file.parent.mkdir(parents=True, exist_ok=True)
    out_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Successfully exported {total_files} files to {out_file} ({out_file.stat().st_size:,} bytes)")

if __name__ == "__main__":
    generate_all_code()
