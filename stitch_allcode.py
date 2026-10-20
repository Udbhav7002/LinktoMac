#!/usr/bin/env python3
"""
stitch_allcode.py
Automatically extracts all source files from ALLCODE_MACOS_COMPLETE.md and
ALLCODE_ANDROID_COMPLETE.md into the ./allcode/ directory.
"""
import os
import re
import stat

MARKDOWN_FILES = [
    "ALLCODE_MACOS_COMPLETE.md",
    "ALLCODE_ANDROID_COMPLETE.md",
]

# Fallbacks if symlinks are not used
ALT_NAMES = {
    "ALLCODE_MACOS_COMPLETE.md": "linktomac_complete_macos_source_code.md",
    "ALLCODE_ANDROID_COMPLETE.md": "linktomac_complete_android_source_code.md",
}

FILE_PATTERN = re.compile(
    r"### FILE:\s*([^\n\r]+)\r?\n```[a-zA-Z0-9_-]*\r?\n(.*?)\r?\n```",
    re.DOTALL
)

def extract_from_markdown(md_path: str):
    target = md_path
    if not os.path.exists(target):
        alt = ALT_NAMES.get(md_path)
        if alt and os.path.exists(alt):
            target = alt
        else:
            print(f"[WARN] {md_path} not found in current directory.")
            return 0

    with open(target, "r", encoding="utf-8") as f:
        content = f.read()

    matches = FILE_PATTERN.findall(content)
    count = 0
    for rel_path, code in matches:
        rel_path = rel_path.strip()
        full_path = os.path.join(".", rel_path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w", encoding="utf-8") as out:
            out.write(code + "\n")
        if full_path.endswith(".sh"):
            st = os.stat(full_path)
            os.chmod(full_path, st.st_mode | stat.S_IEXEC)
        print(f"[OK] Created: {full_path}")
        count += 1
    return count

def main():
    total = 0
    for md in MARKDOWN_FILES:
        total += extract_from_markdown(md)
    print(f"\nDone! Extracted {total} files into ./allcode/")

    # Ensure Android project has wrapper and local.properties if available
    android_dir = os.path.join(".", "allcode", "LinkToMac-Android")
    if os.path.isdir(android_dir):
        wrapper_sh = os.path.join(android_dir, "gradlew")
        if not os.path.exists(wrapper_sh):
            src_gw = os.path.join("android-app", "gradlew")
            src_gr = os.path.join("android-app", "gradle")
            if os.path.exists(src_gw):
                import shutil
                shutil.copy2(src_gw, wrapper_sh)
                os.chmod(wrapper_sh, 0o755)
            if os.path.exists(src_gr):
                import shutil
                shutil.copytree(src_gr, os.path.join(android_dir, "gradle"), dirs_exist_ok=True)
        local_prop = os.path.join(android_dir, "local.properties")
        if not os.path.exists(local_prop):
            sdk_cand = os.path.expanduser("~/Library/Android/sdk")
            if os.path.isdir(sdk_cand):
                with open(local_prop, "w") as f:
                    f.write(f"sdk.dir={sdk_cand}\n")

    print("Next steps:")
    print("  1. Build & Run macOS App: cd allcode/LinkToMac-macOS && ./build_mac_app.sh")
    print("  2. Build Android App:     cd allcode/LinkToMac-Android && ./gradlew assembleDebug")

if __name__ == "__main__":
    main()
