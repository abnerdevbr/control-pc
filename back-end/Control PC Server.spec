# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['Control PC Server.py'],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=['os', 'pyautogui', 'socket', 'threading', 'json', 'ctypes', 'ctypes.wintypes'],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=['tkinter'],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='Control PC Server',
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
    version='C:/Users/Usuario/Programacao/Control PC/back-end/version_info.txt',
    icon=['mousekeyboard.ico'],
)
