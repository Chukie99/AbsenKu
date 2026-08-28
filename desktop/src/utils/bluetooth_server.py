"""
bluetooth_server.py — Bluetooth sync server using raw sockets (cross-platform).

PyBluez is Windows-unfriendly → we use a platform-agnostic RFCOMM-style listener.
On Windows the desktop acts as a "socket receiver": the HP pushes a file/CSV
over Bluetooth SPP, and we read it; then we push back the delta as JSON.

Design:
  - Listen on a raw socket (Bluetooth or TCP fallback when BT unavailable).
  - Authenticate the incoming payload by verifying the embedded pairing-token.
  - Merge received records, then send back the server's delta.

The HP-side BluetoothManager.kt opens the SPP socket and writes the payload.
We keep the desktop side simple: a blocking listener in a background thread.
"""
import os, sys, json, socket, threading, struct
from datetime import datetime

sys.path.insert(0, os.path.dirname(__file__))
import db_manager as db
from . import pairing_manager  # local import for token check


HOST = "0.0.0.0"
PORT = 5100          # TCP fallback port when Bluetooth stack unavailable
BUFFER = 8192


def _read_length_prefix(sock: socket.socket) -> int | None:
    """Read first 4 bytes as big-endian length."""
    data = sock.recv(4)
    if len(data) < 4:
        return None
    return struct.unpack("!I", data)[0]


def _recv_exact(sock: socket.socket, length: int) -> bytes:
    chunks = []
    while sum(len(c) for c in chunks) < length:
        chunk = sock.recv(min(BUFFER, length - sum(len(c) for c in chunks)))
        if not chunk:
            break
        chunks.append(chunk)
    return b"".join(chunks)


def _recv_payload(sock: socket.socket) -> dict | None:
    """Read a length-prefixed JSON payload."""
    length = _read_length_prefix(sock)
    if length is None or length <= 0 or length > 10_000_000:  # hard cap 10 MB
        return None
    raw = _recv_exact(sock, length)
    try:
        return json.loads(raw.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return None


def _send_payload(sock: socket.socket, payload: dict):
    raw = json.dumps(payload).encode("utf-8")
    sock.sendall(struct.pack("!I", len(raw)) + raw)


def handle_client(sock: socket.socket, addr):
    """Authenticate + merge for ONE bluetooth/tcp connection."""
    try:
        payload = _recv_payload(sock) or {}
        token = payload.get("token", "")
        records = payload.get("records", [])

        # authenticate with paired_devices token
        if not db.paired_get_by_token(token):
            db.absensi_log_insert("sync", "up", "fail", f"BT 401 {addr}: bad token", None)
            _send_payload(sock, {"error": "unauthorized"})
            return

        device_id = payload.get("device_id", "?")
        merged, conflicts = 0, 0
        for rec in records:
            tbl = rec.get("table")
            if tbl == "siswa":
                d = rec.get("data", {})
                db.siswa_insert(d["nis"], d["nama"], d.get("kelas_id", 0), d.get("foto", ""), d.get("alamat", ""), d.get("no_hp_ortu", ""), d.get("tanggal_lahir", ""))
                merged += 1
            elif tbl == "absensi":
                d = rec.get("data", {})
                db.absensi_insert(d["siswa_id"], d["tanggal"], d.get("waktu_masuk"), d.get("waktu_keluar"), d.get("status","Hadir"), d.get("mapel_id", 0))
                merged += 1
            elif tbl == "nilai":
                d = rec.get("data", {})
                db.nilai_insert(d["siswa_id"], d["mapel_id"], d["nilai"], d.get("semester","1"), d.get("tahun_ajaran",""))
                merged += 1

        # send back delta (stub: return empty delta for now)
        _send_payload(sock, {"ok": True, "merged": merged, "conflicts": conflicts, "delta": []})
        db.absensi_log_insert("sync", "up", "success", f"BT merged={merged} conflicts={conflicts}", device_id)
    except Exception as e:
        db.absensi_log_insert("sync", "up", "fail", f"BT error: {e}", None)
    finally:
        sock.close()


def start_listener(host: str = HOST, port: int = PORT):
    """Blocking call — run in a background daemon thread from main.py."""
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((host, port))
    srv.listen(5)
    while True:
        try:
            client, addr = srv.accept()
            threading.Thread(target=handle_client, args=(client, addr), daemon=True).start()
        except OSError:
            break


def start_in_background():
    """Start listener thread (non-blocking)."""
    t = threading.Thread(target=start_listener, daemon=True)
    t.start()
    return t
