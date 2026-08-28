"""
backup_scheduler.py — real scheduled backup via APScheduler.

- Daily backup at 22:00 (configurable in Settings).
- Each backup gets a SHA256 checksum sidecar file.
- Retention: keep N backups (default 14), auto-delete older.
- Manual "backup now" still available.
- Uses a background BlockingScheduler (non-GUI) process recommended;
  in Tkinter we use a BackgroundScheduler that ticks alongside mainloop.
"""
import os, sys, shutil, hashlib, sqlite3, time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
import db_manager as db

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

DATA_DIR = db.DATA_DIR
BACKUP_DIR = os.path.join(DATA_DIR, "backup")
os.makedirs(BACKUP_DIR, exist_ok=True)


def compute_sha256(path: str) -> str:
    """Return hex SHA-256 digest of [path]."""
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def verify_sha256(path: str, expected: str) -> bool:
    if not os.path.exists(path):
        return False
    return compute_sha256(path) == expected


def backup_once(retention: int = 14) -> str:
    """Create a timestamped backup + checksum. Returns backup file path."""
    os.makedirs(BACKUP_DIR, exist_ok=True)
    ts = datetime.now().strftime("%Y-%m-%d")
    backup_name = f"absenku_{ts}_{int(time.time())}.db"
    dst = os.path.join(BACKUP_DIR, backup_name)

    # Use SQLite .backup for a safe, atomic snapshot
    conn = sqlite3.connect(db.DB_PATH)
    bconn = sqlite3.connect(dst)
    with bconn:
        conn.backup(bconn)
    bconn.close()
    conn.close()

    # checkpoint file (for WAL)
    for ext in ["-wal", "-shm"]:
        src = db.DB_PATH + ext
        if os.path.exists(src):
            shutil.copy2(src, dst + ext)

    # write checksum sidecar
    sha = compute_sha256(dst)
    with open(dst + ".sha256", "w") as f:
        f.write(sha + "  " + os.path.basename(dst) + "\n")

    # retention: delete older than [retention] files
    cleanup_old(retention)
    db.absensi_log_insert("backup", "all", "success", f"backup -> {backup_name}", None)
    return dst


def restore_once(backup_file: str) -> bool:
    """Verify checksum then replace active DB. Restart required by caller."""
    if not backup_file.endswith(".db"):
        return False
    sha_file = backup_file + ".sha256"
    if not os.path.exists(sha_file):
        db.absensi_log_insert("backup", "all", "fail", "restore: checksum file missing", None)
        return False
    expected = open(sha_file).read().split()[0]
    if not verify_sha256(backup_file, expected):
        db.absensi_log_insert("backup", "all", "fail", "restore: checksum mismatch!", None)
        return False

    # swap DB
    for ext in ["", "-wal", "-shm"]:
        src = backup_file + ext
        dst = db.DB_PATH + ext
        if os.path.exists(src):
            if os.path.exists(dst):
                os.remove(dst)
            shutil.copy2(src, dst)
    db.absensi_log_insert("backup", "all", "success", f"restored from {os.path.basename(backup_file)}", None)
    return True


def cleanup_old(retention: int):
    """Keep only the [retention] most recent .db backups."""
    backups = sorted(
        [f for f in os.listdir(BACKUP_DIR) if f.endswith(".db")],
        key=lambda f: os.path.getmtime(os.path.join(BACKUP_DIR, f)),
        reverse=True,
    )
    for old in backups[retention:]:
        p = os.path.join(BACKUP_DIR, old)
        for ext in ["", ".sha256", "-wal", "-shm"]:
            try: os.remove(p + ext)
            except OSError: pass
        db.absensi_log_insert("backup", "all", "info", f"auto-deleted old backup {old}", None)


def list_backups() -> list[tuple[str, str, bool]]:
    """Return list of (path, date_str, checksum_ok)."""
    result = []
    for f in sorted(os.listdir(BACKUP_DIR)):
        if not f.endswith(".db"):
            continue
        path = os.path.join(BACKUP_DIR, f)
        ts = datetime.fromtimestamp(os.path.getmtime(path)).strftime("%Y-%m-%d %H:%M")
        sha_ok = verify_sha256(path, open(path + ".sha256").read().split()[0]) if os.path.exists(path + ".sha256") else False
        result.append((path, ts, sha_ok))
    return result


def create_scheduler(hour: int = 22, minute: int = 0, retention: int = 14) -> BackgroundScheduler:
    """Start background scheduler for daily backup + retention."""
    scheduler = BackgroundScheduler()
    trigger = CronTrigger(hour=hour, minute=minute)
    scheduler.add_job(
        func=backup_once,
        trigger=trigger,
        args=[retention],
        id="daily_backup",
        replace_existing=True,
    )
    scheduler.start()
    return scheduler


if __name__ == "__main__":
    db.init_db()
    path = backup_once()
    print("Backup created:", path)
