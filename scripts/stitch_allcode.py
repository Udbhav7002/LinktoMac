#!/usr/bin/env python3
"""
stitch_allcode.py
Automatically extracts all source files from docs/bundles/ manifests into the ./allcode/ directory.
"""
import os
import re
import stat
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

MARKDOWN_FILES = [
    REPO_ROOT / "docs" / "bundles" / "linktomac_complete_macos_source_code.md",
    REPO_ROOT / "docs" / "bundles" / "linktomac_complete_android_source_code.md",
]

FILE_PATTERN = re.compile(
    r"### FILE:\s*([^\n\r]+)\r?\n```[a-zA-Z0-9_-]*\r?\n(.*?)\r?\n```",
    re.DOTALL
)

def extract_from_markdown(target: Path):
    if not target.exists():
        print(f"[WARN] {target} not found.")
        return 0

    content = target.read_text(encoding="utf-8")
    matches = FILE_PATTERN.findall(content)
    count = 0
    for rel_path, code in matches:
        rel_path = rel_path.strip()
        full_path = REPO_ROOT / rel_path
        full_path.parent.mkdir(parents=True, exist_ok=True)
        full_path.write_text(code.rstrip() + "\n", encoding="utf-8")
        if str(full_path).endswith(".sh"):
            st = os.stat(full_path)
            os.chmod(full_path, st.st_mode | stat.S_IEXEC)
        print(f"[OK] Created: {rel_path}")
        count += 1
    return count

def main():
    total = 0
    for md_file in MARKDOWN_FILES:
        total += extract_from_markdown(md_file)

    print(f"\nDone! Extracted {total} files into ./allcode/")
    print("Next steps:")
    print("  1. Build & Run macOS App: cd allcode/LinkToMac-macOS && ./build_mac_app.sh")
    print("  2. Build Android App:     cd allcode/LinkToMac-Android && ./gradlew assembleDebug")

if __name__ == "__main__":
    main()
