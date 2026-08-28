"""
conftest.py — pytest fixture: add src/ to sys.path so `import db_manager` works.

Place this in both desktop/ (parent) AND desktop/tests/ so tests can import
the db_manager + utils modules without install.
"""
import os, sys

# walk up to desktop/, then add src/ to path
_HERE = os.path.dirname(os.path.abspath(__file__))
_DESKTOP_ROOT = os.path.dirname(_HERE)  # either src dir parent or desktop/ itself
if _HERE.endswith("tests"):
    _SRC = os.path.join(_DESKTOP_ROOT, "src")
else:
    _SRC = _HERE  # when placed in desktop/, src is subdir
if os.path.isdir(_SRC) and _SRC not in sys.path:
    sys.path.insert(0, _SRC)
