"""
pairing_manager.py — Desktop-side pairing orchestration.

Manages the 6-digit PIN shown on screen + verifies the HP's submission
+ persists the pairing token to paired_devices.

The Flask endpoint logic in sync_server.py mirrors these helpers for
the HTTP path; this module is the callable API for the Tkinter UI.
"""
import os, sys, json, time, secrets, string
import db_manager as db

PIN_TTL = 300  # 5 minutes


def pin_file():
    """Resolve the PIN cache path dynamically (so tests can monkeypatch DATA_DIR)."""
    return os.path.join(db.DATA_DIR, ".pair_pin")


def generate_pin(length: int = 6) -> str:
    """Generate a cryptographically-random 6-digit PIN."""
    digits = string.digits
    return "".join(secrets.choice(digits) for _ in range(length))


def current_pin() -> str | None:
    """Return the active PIN if valid (not expired), else None."""
    pf = pin_file()
    if not os.path.exists(pf):
        return None
    try:
        with open(pf) as f:
            data = json.load(f)
        if int(time.time()) > data.get("expires", 0):
            return None
        return data["pin"]
    except Exception:
        return None


def issue_pin() -> str:
    """Create a new 6-digit PIN, cache it (5-min TTL), return it."""
    pin = generate_pin()
    expire = int(time.time()) + PIN_TTL
    with open(pin_file(), "w") as f:
        json.dump({"pin": pin, "expires": expire}, f)
    return pin


def verify_and_pair(pin: str, device_id: str, device_name: str = "HP") -> str | None:
    """HP submits [pin] + device_id. If pin valid → token, else None."""
    active_pin = current_pin()
    if active_pin is None:
        return None
    if active_pin != pin:
        return None
    token = secrets.token_hex(32)
    db.paired_insert(device_name, device_id, token)
    return token


def list_paired() -> list[dict]:
    return db.paired_all_active()


def revoke_device(device_internal_id: int):
    db.paired_revoke(device_internal_id)


def revoke_all():
    for d in list_paired():
        db.paired_revoke(d["id"])


def device_id_matches(device_id: str) -> bool:
    """Check if a given device_id is already paired/trusted."""
    return db.paired_get_by_token(device_id) is not None or any(
        d.get("device_id") == device_id for d in list_paired()
    )
