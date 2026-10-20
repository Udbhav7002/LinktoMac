"""
Adversarial empirical tests for Milestone 3 packaging and binary paths.
Tests resolve_mirror_client_path() under various simulated runtime environments,
verifies dist/LinktoMac.app, codesigning, dynamic library dependencies (no sdl3),
and DMG mount integrity.
"""

import os
import sys
import shutil
import stat
import tempfile
import unittest
import subprocess
import threading
from pathlib import Path
from unittest.mock import patch

# Ensure orchestrator is importable
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(REPO_ROOT / "orchestrator"))

from linktomac.backend import resolve_mirror_client_path, config
from linktomac import adb


class TestResolveMirrorClientPathAdversarial(unittest.TestCase):
    """Stress-test resolve_mirror_client_path under simulated environments."""

    def test_development_environment_resolution(self):
        """In standard dev mode, it should resolve the build directory binary."""
        with patch.object(sys, "frozen", False, create=True):
            resolved = resolve_mirror_client_path()
            self.assertTrue(resolved.is_file(), f"Resolved path must exist: {resolved}")
            self.assertTrue(os.access(resolved, os.X_OK), f"Resolved path must be executable: {resolved}")
            self.assertEqual(resolved, config.BASE_DIR / "native-client/build/mirror_client")

    def test_frozen_bundle_macos_resolution(self):
        """When frozen in a macOS bundle, it should prioritize Contents/MacOS/mirror_client."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir) / "LinktoMac.app" / "Contents"
            macos_dir = bundle / "MacOS"
            macos_dir.mkdir(parents=True)
            mock_exe = macos_dir / "LinktoMac"
            mock_exe.touch()
            mock_mirror = macos_dir / "mirror_client"
            mock_mirror.touch()
            mock_mirror.chmod(mock_mirror.stat().st_mode | stat.S_IXUSR)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)):
                resolved = resolve_mirror_client_path()
                self.assertEqual(resolved, mock_mirror)

    def test_frozen_bundle_frameworks_fallback(self):
        """When MacOS/mirror_client is missing, it falls back to Contents/Frameworks/mirror_client."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir) / "LinktoMac.app" / "Contents"
            macos_dir = bundle / "MacOS"
            macos_dir.mkdir(parents=True)
            mock_exe = macos_dir / "LinktoMac"
            mock_exe.touch()

            frameworks_dir = bundle / "Frameworks"
            frameworks_dir.mkdir(parents=True)
            mock_mirror = frameworks_dir / "mirror_client"
            mock_mirror.touch()
            mock_mirror.chmod(mock_mirror.stat().st_mode | stat.S_IXUSR)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)):
                resolved = resolve_mirror_client_path()
                self.assertEqual(resolved, mock_mirror)

    def test_frozen_bundle_resources_fallback(self):
        """When MacOS and Frameworks are missing, it falls back to Contents/Resources/mirror_client."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir) / "LinktoMac.app" / "Contents"
            macos_dir = bundle / "MacOS"
            macos_dir.mkdir(parents=True)
            mock_exe = macos_dir / "LinktoMac"
            mock_exe.touch()

            resources_dir = bundle / "Resources"
            resources_dir.mkdir(parents=True)
            mock_mirror = resources_dir / "mirror_client"
            mock_mirror.touch()
            mock_mirror.chmod(mock_mirror.stat().st_mode | stat.S_IXUSR)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)):
                resolved = resolve_mirror_client_path()
                self.assertEqual(resolved, mock_mirror)

    def test_frozen_bundle_meipass_fallback(self):
        """When bundle directories lack the binary, it falls back to sys._MEIPASS."""
        with tempfile.TemporaryDirectory() as tmpdir:
            macos_dir = Path(tmpdir) / "MacOS"
            macos_dir.mkdir(parents=True)
            mock_exe = macos_dir / "LinktoMac"
            mock_exe.touch()

            meipass_dir = Path(tmpdir) / "_MEIPASS"
            meipass_dir.mkdir(parents=True)
            mock_mirror = meipass_dir / "mirror_client"
            mock_mirror.touch()
            mock_mirror.chmod(mock_mirror.stat().st_mode | stat.S_IXUSR)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)), \
                 patch.object(sys, "_MEIPASS", str(meipass_dir), create=True):
                resolved = resolve_mirror_client_path()
                self.assertEqual(resolved, mock_mirror)

    def test_shutil_which_fallback(self):
        """When bundle and dev candidates do not exist, fall back to shutil.which."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bin_dir = Path(tmpdir) / "bin"
            bin_dir.mkdir()
            mock_mirror = bin_dir / "mirror_client"
            mock_mirror.touch()
            mock_mirror.chmod(mock_mirror.stat().st_mode | stat.S_IXUSR)

            with patch.object(sys, "frozen", False, create=True), \
                 patch.object(config, "BASE_DIR", Path(tmpdir) / "empty"), \
                 patch("shutil.which", return_value=str(mock_mirror)):
                resolved = resolve_mirror_client_path()
                self.assertEqual(resolved, mock_mirror)

    def test_missing_all_raises_filenotfound(self):
        """When no candidate exists, FileNotFoundError must be raised with checked paths list."""
        with tempfile.TemporaryDirectory() as tmpdir:
            with patch.object(sys, "frozen", False, create=True), \
                 patch.object(config, "BASE_DIR", Path(tmpdir) / "empty"), \
                 patch("shutil.which", return_value=None):
                with self.assertRaises(FileNotFoundError) as ctx:
                    resolve_mirror_client_path()
                self.assertIn("mirror_client binary not found", str(ctx.exception))
                self.assertIn("native-client/build/mirror_client", str(ctx.exception))

    def test_non_executable_skipped(self):
        """Candidates without execute permission (chmod -x) must be skipped."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir) / "LinktoMac.app" / "Contents"
            macos_dir = bundle / "MacOS"
            macos_dir.mkdir(parents=True)
            mock_exe = macos_dir / "LinktoMac"
            mock_exe.touch()

            # Non-executable mirror in MacOS
            non_exec_mirror = macos_dir / "mirror_client"
            non_exec_mirror.touch()
            non_exec_mirror.chmod(stat.S_IRUSR | stat.S_IWUSR)  # rw- only, no X

            # Executable mirror in Frameworks
            frameworks_dir = bundle / "Frameworks"
            frameworks_dir.mkdir(parents=True)
            exec_mirror = frameworks_dir / "mirror_client"
            exec_mirror.touch()
            exec_mirror.chmod(exec_mirror.stat().st_mode | stat.S_IXUSR)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)):
                resolved = resolve_mirror_client_path()
                self.assertEqual(resolved, exec_mirror)

    def test_directory_named_mirror_client_skipped(self):
        """A directory named 'mirror_client' must not be selected as the binary."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir) / "LinktoMac.app" / "Contents"
            macos_dir = bundle / "MacOS"
            macos_dir.mkdir(parents=True)
            mock_exe = macos_dir / "LinktoMac"
            mock_exe.touch()

            # Directory named mirror_client
            dir_mirror = macos_dir / "mirror_client"
            dir_mirror.mkdir()

            # Executable mirror in Frameworks
            frameworks_dir = bundle / "Frameworks"
            frameworks_dir.mkdir(parents=True)
            exec_mirror = frameworks_dir / "mirror_client"
            exec_mirror.touch()
            exec_mirror.chmod(exec_mirror.stat().st_mode | stat.S_IXUSR)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)):
                resolved = resolve_mirror_client_path()
                self.assertEqual(resolved, exec_mirror)

    def test_concurrent_resolution(self):
        """Concurrent calls to resolve_mirror_client_path must all succeed consistently."""
        results = []
        errors = []

        def worker():
            try:
                res = resolve_mirror_client_path()
                results.append(res)
            except Exception as e:
                errors.append(e)

        threads = [threading.Thread(target=worker) for _ in range(25)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        self.assertEqual(len(errors), 0, f"Concurrent resolution errors: {errors}")
        self.assertEqual(len(results), 25)
        # All results should be identical
        self.assertTrue(all(r == results[0] for r in results))


class TestPackagingArtifactsAdversarial(unittest.TestCase):
    """Empirical verification of packaged LinktoMac.app and LinktoMac.dmg."""

    APP_PATH = REPO_ROOT / "dist" / "LinktoMac.app"
    DMG_PATH = REPO_ROOT / "dist" / "LinktoMac.dmg"

    def test_app_bundle_structure(self):
        """LinktoMac.app must contain required binaries and frameworks."""
        self.assertTrue(self.APP_PATH.is_dir(), f"{self.APP_PATH} does not exist")
        
        macos_bin = self.APP_PATH / "Contents" / "MacOS" / "mirror_client"
        frameworks_bin = self.APP_PATH / "Contents" / "Frameworks" / "mirror_client"
        
        self.assertTrue(macos_bin.is_file(), f"Missing {macos_bin}")
        self.assertTrue(os.access(macos_bin, os.X_OK), f"{macos_bin} is not executable")
        
        self.assertTrue(frameworks_bin.is_file(), f"Missing {frameworks_bin}")
        self.assertTrue(os.access(frameworks_bin, os.X_OK), f"{frameworks_bin} is not executable")

        # Main executable
        main_bin = self.APP_PATH / "Contents" / "MacOS" / "LinktoMac"
        self.assertTrue(main_bin.is_file(), f"Missing {main_bin}")
        self.assertTrue(os.access(main_bin, os.X_OK), f"{main_bin} is not executable")

        # SDL2 dynamic library
        sdl2_frameworks = self.APP_PATH / "Contents" / "Frameworks" / "libSDL2-2.0.0.dylib"
        sdl2_macos = self.APP_PATH / "Contents" / "MacOS" / "libSDL2-2.0.0.dylib"
        self.assertTrue(sdl2_frameworks.is_file(), f"Missing {sdl2_frameworks}")
        self.assertTrue(sdl2_macos.is_file(), f"Missing {sdl2_macos}")

    def test_otool_no_sdl3_dependency(self):
        """otool -L on mirror_client must verify SDL2 and complete absence of SDL3."""
        for subpath in ["Contents/MacOS/mirror_client", "Contents/Frameworks/mirror_client"]:
            target = self.APP_PATH / subpath
            proc = subprocess.run(
                ["otool", "-L", str(target)],
                capture_output=True,
                text=True,
                check=True
            )
            output = proc.stdout
            self.assertIn("libSDL2", output, f"mirror_client at {subpath} must link against libSDL2")
            self.assertNotIn("sdl3", output.lower(), f"mirror_client at {subpath} unexpectedly linked against sdl3")
            self.assertNotIn("libsdl3", output.lower(), f"mirror_client at {subpath} unexpectedly linked against libsdl3")

    def test_codesign_bundle_verification(self):
        """codesign --verify --deep --strict must pass on LinktoMac.app."""
        proc = subprocess.run(
            ["codesign", "--verify", "--deep", "--strict", str(self.APP_PATH)],
            capture_output=True,
            text=True
        )
        self.assertEqual(
            proc.returncode, 0,
            f"codesign verification failed: stdout={proc.stdout}, stderr={proc.stderr}"
        )

    def test_dmg_mountability_and_contents(self):
        """LinktoMac.dmg must mount cleanly and contain LinktoMac.app."""
        self.assertTrue(self.DMG_PATH.is_file(), f"{self.DMG_PATH} does not exist")
        self.assertGreater(self.DMG_PATH.stat().st_size, 10_000_000, "DMG size should exceed 10MB")

        # Mount dmg
        mount_proc = subprocess.run(
            ["hdiutil", "attach", "-nobrowse", str(self.DMG_PATH)],
            capture_output=True,
            text=True,
            check=True
        )
        mount_output = mount_proc.stdout
        
        # Extract mount point
        mount_point = None
        for line in mount_output.splitlines():
            if "/Volumes/" in line:
                mount_point = line[line.index("/Volumes/"):].strip()
                break

        self.assertIsNotNone(mount_point, f"Failed to locate mount point from: {mount_output}")
        mount_path = Path(mount_point)

        try:
            app_in_dmg = mount_path / "LinktoMac.app"
            self.assertTrue(app_in_dmg.is_dir(), f"LinktoMac.app not found in mounted DMG: {mount_path}")
            # Verify internal binary in DMG
            dmg_mirror = app_in_dmg / "Contents" / "MacOS" / "mirror_client"
            self.assertTrue(dmg_mirror.is_file(), f"mirror_client missing from DMG app bundle: {dmg_mirror}")
        finally:
            # Always detach
            subprocess.run(["hdiutil", "detach", mount_point], capture_output=True, check=False)

    def test_zero_sdl3_references_across_scripts(self):
        """Verify 0 occurrences of 'sdl3' across all project scripts and build configs."""
        targets = [
            REPO_ROOT / "build_mac_app.sh",
            REPO_ROOT / "native-client",
            REPO_ROOT / "scripts",
            REPO_ROOT / "orchestrator",
            REPO_ROOT / "android-app",
            REPO_ROOT / "android-server",
        ]
        for target in targets:
            if not target.exists():
                continue
            proc = subprocess.run(
                ["grep", "-ri", "sdl3", str(target)],
                capture_output=True,
                text=True
            )
            self.assertEqual(
                proc.returncode, 1,
                f"Unexpected sdl3 reference found in {target}: {proc.stdout}"
            )


class TestAdbPathResolutionAdversarial(unittest.TestCase):
    """Stress-test ADB path resolution in frozen bundles and standard systems."""

    def setUp(self):
        adb._adb_path_cache = None

    def tearDown(self):
        adb._adb_path_cache = None

    def test_adb_frozen_bundle_macos_platform_tools(self):
        """In frozen bundle, Contents/MacOS/platform-tools/adb is prioritized."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir) / "LinktoMac.app" / "Contents"
            macos_tools = bundle / "MacOS" / "platform-tools"
            macos_tools.mkdir(parents=True)
            mock_adb = macos_tools / "adb"
            mock_adb.touch()
            mock_adb.chmod(mock_adb.stat().st_mode | stat.S_IXUSR)

            mock_exe = bundle / "MacOS" / "LinktoMac"
            mock_exe.touch()

            def fake_run(cmd, **kwargs):
                p = Path(cmd[0])
                if not p.is_absolute() and p.name == "adb":
                    return unittest.mock.MagicMock(returncode=0)
                if not p.exists():
                    raise FileNotFoundError(f"No such file: {p}")
                return unittest.mock.MagicMock(returncode=0)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)), \
                 patch("subprocess.run", side_effect=fake_run):
                resolved = adb._find_adb_path()
                self.assertEqual(resolved, str(mock_adb))

    def test_adb_frozen_bundle_resources_platform_tools(self):
        """When MacOS tools absent, Resources/platform-tools/adb is selected."""
        with tempfile.TemporaryDirectory() as tmpdir:
            bundle = Path(tmpdir) / "LinktoMac.app" / "Contents"
            (bundle / "MacOS").mkdir(parents=True)
            mock_exe = bundle / "MacOS" / "LinktoMac"
            mock_exe.touch()

            res_tools = bundle / "Resources" / "platform-tools"
            res_tools.mkdir(parents=True)
            mock_adb = res_tools / "adb"
            mock_adb.touch()
            mock_adb.chmod(mock_adb.stat().st_mode | stat.S_IXUSR)

            def fake_run(cmd, **kwargs):
                p = Path(cmd[0])
                if not p.is_absolute() and p.name == "adb":
                    return unittest.mock.MagicMock(returncode=0)
                if not p.exists():
                    raise FileNotFoundError(f"No such file: {p}")
                return unittest.mock.MagicMock(returncode=0)

            with patch.object(sys, "frozen", True, create=True), \
                 patch.object(sys, "executable", str(mock_exe)), \
                 patch("subprocess.run", side_effect=fake_run):
                resolved = adb._find_adb_path()
                self.assertEqual(resolved, str(mock_adb))

    def test_adb_system_path_fallback(self):
        """In dev mode with no fixed-path ADB, falls back to plain 'adb' on system PATH."""
        def fake_run(cmd, **kwargs):
            p = Path(cmd[0])
            # Only plain "adb" succeeds on PATH, fixed paths fail
            if not p.is_absolute() and str(p) == "adb":
                return unittest.mock.MagicMock(returncode=0)
            raise FileNotFoundError(f"No such file: {p}")

        with patch.object(sys, "frozen", False, create=True), \
             patch("subprocess.run", side_effect=fake_run):
            resolved = adb._find_adb_path()
            self.assertEqual(resolved, "adb")


if __name__ == "__main__":
    unittest.main()


