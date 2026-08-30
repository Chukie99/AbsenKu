"""
create_exe.py — build AbsenKu Desktop .exe via PyInstaller.

Run:  python create_exe.py

Produces: dist/AbsenKu Desktop.exe (single-file, headless icon)
"""
import os, sys, subprocess

HERE = os.path.dirname(os.path.abspath(__file__))

ENTRY = os.path.join(HERE, "src", "main.py")
ICON = os.path.join(HERE, "assets", "logo.png")

SPEC = f"""
# -*- mode: python ; coding: utf-8 -*-

block_cipher = None

a = Analysis(
    [r"{ENTRY}"],
    pathex=[r"{os.path.join(HERE, 'src')}", r"{os.path.join(HERE, 'assets')}"],
    binaries=[],
    datas=[
        (r"{os.path.join(HERE, 'assets')}", "assets/"),
    ],
    hiddenimports=[
        "ttkbootstrap",
        "reportlab",
        "openpyxl",
        "barcode",
        "qrcode",
        "apscheduler",
        "flask",
        "PIL",
    ],
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    cipher=block_cipher,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="AbsenKu Desktop",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
"""

# write spec file
spec_path = os.path.join(HERE, "absenku_desktop.spec")
with open(spec_path, "w") as f:
    f.write(SPEC)

# also write a one-line build.bat for convenience
bat = os.path.join(HERE, "build_exe.bat")
with open(bat, "w") as f:
    f.write("@echo off\npyinstaller --noconfirm " + spec_path + "\npause\n")

print(f"Spec written: {spec_path}")
print(f"Run: pyinstaller --noconfirm {spec_path}")

if __name__ == "__main__":
    # try to build if PyInstaller is installed
    try:
        subprocess.run([sys.executable, "-m", "PyInstaller", "--noconfirm", spec_path], cwd=HERE, check=True)
        print("BUILD SUCCESS → dist/AbsenKu Desktop.exe")
    except Exception as e:
        print("Build skipped/failed:", e)
        print("Install deps first: pip install -r requirements.txt")
