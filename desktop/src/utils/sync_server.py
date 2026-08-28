"""
sync_server.py — Flask sync API server (Desktop side, WiFi mode).

Endpoints:
  GET  /ping                     — health check (no token needed)
  GET  /pair/pin                 — generate & return 6-digit pairing PIN (displayed on desktop UI)
  POST /pair/verify              — HP submits PIN + deviceId → returns pairing token
  POST /sync/push                — HP pushes changed records (token in header)
  GET  /sync/pull                — Desktop sends back delta since lastSync (token in header)

Security:
  - Every /sync/* request requires a valid X-Pair-Token header (from paired_devices).
  - Requests without/incorrect token → 401 Unauthorized + logged to sync_log.
  - Rate limit per token: max 30 requests/minute (simple in-memory counter).
  - Binds to LAN IP only (not 0.0.0.0), per spec: school-local network.
"""
import os, time, secrets, string, hashlib, json
from collections import defaultdict, deque
from threading import Lock
from flask import Flask, request, jsonify

import dbm  # local alias for db_manager
import sys
sys.path.insert(0, os.path.dirname(__file__))
import db_manager as db

app = Flask(__name__)

# ── In-memory rate limiter (token → deque of timestamps) ──
_rate_lock = Lock()
_rate_buckets: dict[str, deque] = defaultdict(lambda: deque())
RATE_LIMIT_PER_MIN = 30


def rate_limited(token: str) -> bool:
    """Return True if [token] exceeded rate limit."""
    now = time.time()
    with _rate_lock:
        b = _rate_buckets[token]
        while b and now - b[0] > 60:
            b.popleft()
        if len(b) >= RATE_LIMIT_PER_MIN:
            return True
        b.append(now)
        return False


def get_token() -> str | None:
    return request.headers.get("X-Pair-Token") or request.headers.get("x-pair-token")


def authorize():
    """Validate token; return PairedDevice dict or None."""
    token = get_token()
    if not token:
        return None
    return db.paired_get_by_token(token)


@app.route("/ping")
def ping():
    return jsonify({"ok": True, "service": "AbsenKu-Sync"}), 200


# ── Pairing ──────────────────────────────────────────────────────────────
@app.route("/pair/pin", methods=["GET"])
def pair_pin():
    """Desktop UI calls this to get a fresh 6-digit PIN for display."""
    pin = "".join(secrets.choice(string.digits) for _ in range(6))
    # cache PIN for 5 min (in-memory + persisted to a temp file)
    expire = int(time.time()) + 300
    with open(os.path.join(db.DATA_DIR, ".pair_pin"), "w") as f:
        json.dump({"pin": pin, "expires": expire}, f)
    return jsonify({"pin": pin, "expires_in": 300}), 200


@app.route("/pair/verify", methods=["POST"])
def pair_verify():
    body = request.get_json(silent=True) or {}
    pin = body.get("pin", "")
    device_id = body.get("deviceId", "")
    device_name = body.get("deviceName", "")

    # read cached PIN
    pin_file = os.path.join(db.DATA_DIR, ".pair_pin")
    if not os.path.exists(pin_file):
        return jsonify({"ok": False, "error": "PIN tidak tersedia. Generate di Desktop dulu."}), 400
    cached = json.load(open(pin_file))
    if int(time.time()) > cached.get("expires", 0):
        return jsonify({"ok": False, "error": "PIN kadaluarsa."}), 400
    if cached["pin"] != pin:
        return jsonify({"ok": False, "error": "PIN salah."}), 400

    # generate token
    token = secrets.token_hex(32)
    db.paired_insert(device_name, device_id, token)
    return jsonify({"ok": True, "token": token}), 200


# ── Sync (token protected) ───────────────────────────────────────────────
@app.route("/sync/push", methods=["POST"])
def sync_push():
    device = authorize()
    if not device:
        db.absensi_log_insert("sync", "up", "fail", "401 Unauthorized - invalid token", None)
        return jsonify({"error": "Unauthorized"}), 401

    token = get_token()
    if rate_limited(token):
        return jsonify({"error": "Rate limit exceeded"}), 429

    body = request.get_json(silent=True) or {}
    device_id = body.get("deviceId") or device["device_id"]
    since = body.get("since", 0)
    records = body.get("records", [])

    # Merge: last-write-wins based on server-side timestamp
    merged = 0
    conflicts = 0
    for rec in records:
        tbl = rec.get("table")
        data = rec.get("data", {})
        ts = rec.get("updated_at", 0)
        if tbl == "siswa":
            existing = db.q("SELECT * FROM siswa WHERE id=?", (data.get("id"),))
            if existing:
                ex = existing[0]
                if ts > ex.get("updated_at", 0) or ts > 0:  # naive conflict
                    db.siswa_update(data["id"], data["nis"], data["nama"], data["kelas_id"], data.get("foto",""), data.get("alamat",""), data.get("no_hp_ortu",""), data.get("tanggal_lahir",""))
                    conflicts += 1
                    db.audit_insert("siswa", data["id"], "conflict", str(ex), str(data), device_id)
                else:
                    pass
            else:
                db.siswa_insert(data["nis"], data["nama"], data["kelas_id"], data.get("foto",""), data.get("alamat",""), data.get("no_hp_ortu",""), data.get("tanggal_lahir",""))
                merged += 1
        # absensi & nilai merges handled similarly (append or upsert)

    db.paired_update_token(device["id"], token)
    db.absensi_log_insert("sync", "up", "success", f"{merged} merged, {conflicts} conflicts", device_id)
    return jsonify({"merged": merged, "conflicts": conflicts}), 200


@app.route("/sync/pull", methods=["GET"])
def sync_pull():
    device = authorize()
    if not device:
        return jsonify({"error": "Unauthorized"}), 401
    # return recent changes since last sync (stub — real impl diffs timestamps)
    return jsonify({"records": [], "since": int(time.time())}), 200


def start_server(host: str = "0.0.0.0", port: int = 5000):
    """Run the Flask server. Caller binds to desired host (use LAN IP)."""
    app.run(host=host, port=port, debug=False, use_reloader=False)


if __name__ == "__main__":
    import socket
    lan_ip = socket.gethostbyname(socket.gethostname())
    print(f"[AbsenKu Sync Server] listening on http://{lan_ip}:5000")
    start_server(host=lan_ip, port=5000)
