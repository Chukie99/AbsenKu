"""
tests/test_pairing_token.py — 6-digit PIN generation, token verification, expiry.

Validates pairing_manager logic used by sync_server.py & bluetooth_server.py.
"""
import os, sys, time, json

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "src"))

import db_manager
from utils import pairing_manager


def test_pin_is_six_digits():
    """Generated PIN must be exactly 6 numeric chars."""
    pin = pairing_manager.generate_pin()
    assert len(pin) == 6, f"PIN length {len(pin)}, expected 6"
    assert pin.isdigit(), f"PIN '{pin}' is not all digits"
    print(f"PASS: 6-digit PIN generated = '{pin}'")


def test_pin_issuable_and_verified(tmp_path, monkeypatch):
    """issue_pin → verify_and_pair round-trip must produce a token.

    NOTE: issue_pin() reads/writes pairing_manager.PIN_FILE which in turn
    uses db.DATA_DIR — so we must monkeypatch *before* calling issue_pin.
    """
    test_db = str(tmp_path / "test_pairing.db")
    monkeypatch.setattr(db_manager, "DATA_DIR", str(tmp_path), raising=True)
    monkeypatch.setattr(db_manager, "DB_PATH", test_db)
    db_manager.init_db(test_db)

    pin = pairing_manager.issue_pin()
    token = pairing_manager.verify_and_pair(pin, device_id=f"DEV_TEST_{int(time.time()*1000)}", device_name="TestHP")
    assert token is not None, "verify_and_pair returned None — pairing failed"
    assert len(token) >= 16
    stored = db_manager.paired_get_by_token(token)
    assert stored is not None, "token not in paired_devices"
    assert stored["device_id"].startswith("DEV_TEST_"), f"unexpected device_id: {stored['device_id']}"
    print(f"PASS: PIN '{pin}' verified → token issued & stored for DEV001")


def test_bad_pin_rejected(tmp_path, monkeypatch):
    """Wrong PIN must return None."""
    test_db = str(tmp_path / "test_bad.db")
    monkeypatch.setattr(db_manager, "DATA_DIR", str(tmp_path), raising=True)
    monkeypatch.setattr(db_manager, "DB_PATH", test_db)
    db_manager.init_db(test_db)

    pairing_manager.issue_pin()
    token = pairing_manager.verify_and_pair("000000", device_id="DEV002")
    assert token is None
    print("PASS: wrong PIN rejected")


def test_pin_expires(tmp_path, monkeypatch):
    """Expired PIN must not verify."""
    test_db = str(tmp_path / "test_exp.db")
    monkeypatch.setattr(db_manager, "DATA_DIR", str(tmp_path), raising=True)
    monkeypatch.setattr(db_manager, "DB_PATH", test_db)
    db_manager.init_db(test_db)

    pin = pairing_manager.issue_pin()
    # force-expire the cached pin file
    pin_file = os.path.join(str(tmp_path), ".pair_pin")
    old = json.load(open(pin_file))
    old["expires"] = int(time.time()) - 1
    with open(pin_file, "w") as f:
        json.dump(old, f)
    token = pairing_manager.verify_and_pair(pin, device_id="DEV003")
    assert token is None, "expired PIN should be rejected"
    print("PASS: expired PIN rejected")
