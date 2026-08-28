"""
tests/conftest.py — add src/ to sys.path for import resolution.
"""
import os, sys
_SRC = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src")
if _SRC not in sys.path:
    sys.path.insert(0, _SRC)
