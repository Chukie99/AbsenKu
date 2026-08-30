"""
db_manager.py — SQLite wrapper for AbsenKu Desktop (placed at src/ root for import resolution).

Provides: get_conn, tx, init_db, q, exec_one, exec_many + high-level
accessors (siswa, kelas, mapel, absensi, nilai, settings, paired, audit, sync_log).

All DB operations use sqlite3 transactions. Soft-delete-aware queries.
"""
import sqlite3, os
from contextlib import contextmanager
from typing import List

HERE = os.path.dirname(os.path.abspath(__file__))          # .../src
DESKTOP = os.path.dirname(HERE)                            # .../desktop
DATA_DIR = os.path.join(DESKTOP, "data")
DB_PATH = os.path.join(DATA_DIR, "absenku.db")
os.makedirs(DATA_DIR, exist_ok=True)

_SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS kelas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nama TEXT NOT NULL,
    wali_kelas TEXT,
    tahun_ajaran TEXT,
    is_active INTEGER DEFAULT 1,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS siswa (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nis TEXT UNIQUE NOT NULL,
    nama TEXT NOT NULL,
    kelas_id INTEGER,
    foto TEXT,
    alamat TEXT,
    no_hp_ortu TEXT,
    tanggal_lahir TEXT,
    qr_code TEXT,
    is_active INTEGER DEFAULT 1,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (kelas_id) REFERENCES kelas(id)
);
CREATE TABLE IF NOT EXISTS mapel (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nama TEXT NOT NULL,
    kode TEXT UNIQUE,
    jam_per_minggu INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS absensi (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    siswa_id INTEGER,
    tanggal TEXT NOT NULL,
    waktu_masuk TEXT,
    waktu_keluar TEXT,
    status TEXT CHECK(status IN ('Hadir','Izin','Sakit','Alfa')),
    mapel_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (siswa_id) REFERENCES siswa(id),
    FOREIGN KEY (mapel_id) REFERENCES mapel(id),
    UNIQUE(siswa_id, tanggal, mapel_id)
);
CREATE TABLE IF NOT EXISTS nilai (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    siswa_id INTEGER,
    mapel_id INTEGER,
    nilai REAL CHECK(nilai IS NULL OR (nilai >= 0 AND nilai <= 100)),
    semester TEXT,
    tahun_ajaran TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (siswa_id) REFERENCES siswa(id),
    FOREIGN KEY (mapel_id) REFERENCES mapel(id)
);
CREATE TABLE IF NOT EXISTS pengaturan (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    key TEXT UNIQUE NOT NULL,
    value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS aktivasi (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT UNIQUE NOT NULL,
    serial_number TEXT,
    status TEXT DEFAULT 'inactive',
    activated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS paired_devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_name TEXT,
    device_id TEXT UNIQUE NOT NULL,
    pairing_token TEXT UNIQUE NOT NULL,
    paired_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_sync_at TIMESTAMP,
    revoked INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name TEXT NOT NULL,
    record_id INTEGER NOT NULL,
    field_name TEXT,
    old_value TEXT,
    new_value TEXT,
    changed_by TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sync_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT,
    direction TEXT,
    status TEXT,
    message TEXT,
    device_id TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_siswa_kelas_id ON siswa(kelas_id);
CREATE INDEX IF NOT EXISTS idx_siswa_is_active ON siswa(is_active);
CREATE INDEX IF NOT EXISTS idx_absensi_siswa_id ON absensi(siswa_id);
CREATE INDEX IF NOT EXISTS idx_absensi_tanggal ON absensi(tanggal);
CREATE INDEX IF NOT EXISTS idx_absensi_mapel_id ON absensi(mapel_id);
CREATE INDEX IF NOT EXISTS idx_absensi_status ON absensi(status);
CREATE INDEX IF NOT EXISTS idx_absensi_siswa_tanggal ON absensi(siswa_id, tanggal);
CREATE INDEX IF NOT EXISTS idx_nilai_siswa_id ON nilai(siswa_id);
CREATE INDEX IF NOT EXISTS idx_nilai_mapel_id ON nilai(mapel_id);
CREATE INDEX IF NOT EXISTS idx_nilai_semester ON nilai(semester, tahun_ajaran);
CREATE INDEX IF NOT EXISTS idx_audit_table_name ON audit_log(table_name);
CREATE INDEX IF NOT EXISTS idx_audit_record_id ON audit_log(record_id);
CREATE INDEX IF NOT EXISTS idx_sync_log_device_id ON sync_log(device_id);
CREATE INDEX IF NOT EXISTS idx_paired_device_id ON paired_devices(device_id);
-- Triggers for auto-updating updated_at
CREATE TRIGGER IF NOT EXISTS trg_siswa_updated_at
AFTER UPDATE ON siswa
BEGIN
    UPDATE siswa SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
CREATE TRIGGER IF NOT EXISTS trg_absensi_updated_at
AFTER UPDATE ON absensi
BEGIN
    UPDATE absensi SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
CREATE TRIGGER IF NOT EXISTS trg_nilai_updated_at
AFTER UPDATE ON nilai
BEGIN
    UPDATE nilai SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
CREATE TRIGGER IF NOT EXISTS trg_pengaturan_updated_at
AFTER UPDATE ON pengaturan
BEGIN
    UPDATE pengaturan SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
CREATE TABLE IF NOT EXISTS jadwal_pelajaran (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kelas_id INTEGER NOT NULL,
    mapel_id INTEGER NOT NULL,
    hari TEXT NOT NULL CHECK(hari IN ('Senin','Selasa','Rabu','Kamis','Jumat','Sabtu')),
    jam_mulai TEXT NOT NULL,
    jam_selesai TEXT NOT NULL,
    guru TEXT,
    is_active INTEGER DEFAULT 1,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (kelas_id) REFERENCES kelas(id),
    FOREIGN KEY (mapel_id) REFERENCES mapel(id)
);
CREATE TABLE IF NOT EXISTS poin_disiplin (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    siswa_id INTEGER NOT NULL,
    tanggal TEXT NOT NULL,
    kategori TEXT CHECK(kategori IN ('Positif','Negatif')),
    poin INTEGER NOT NULL DEFAULT 0,
    keterangan TEXT,
    diberikan_oleh TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (siswa_id) REFERENCES siswa(id)
);
CREATE INDEX IF NOT EXISTS idx_jadwal_kelas ON jadwal_pelajaran(kelas_id);
CREATE INDEX IF NOT EXISTS idx_jadwal_mapel ON jadwal_pelajaran(mapel_id);
CREATE INDEX IF NOT EXISTS idx_jadwal_hari ON jadwal_pelajaran(hari);
CREATE INDEX IF NOT EXISTS idx_poin_siswa ON poin_disiplin(siswa_id);
CREATE INDEX IF NOT EXISTS idx_poin_tanggal ON poin_disiplin(tanggal);
CREATE INDEX IF NOT EXISTS idx_poin_kategori ON poin_disiplin(kategori);
CREATE TRIGGER IF NOT EXISTS trg_jadwal_updated_at
AFTER UPDATE ON jadwal_pelajaran
BEGIN
    UPDATE jadwal_pelajaran SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
CREATE TRIGGER IF NOT EXISTS trg_poin_updated_at
AFTER UPDATE ON poin_disiplin
BEGIN
    UPDATE poin_disiplin SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
"""


def get_conn(db_path: str = DB_PATH) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path, timeout=30, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


@contextmanager
def tx(db_path: str = DB_PATH):
    conn = get_conn(db_path)
    try:
        yield conn; conn.commit()
    except Exception:
        conn.rollback(); raise
    finally:
        conn.close()


def init_db(db_path: str = DB_PATH) -> None:
    with tx(db_path) as conn:
        conn.executescript(_SCHEMA_SQL); conn.commit()
    _migrate_add_qr_code(db_path)


def _migrate_add_qr_code(db_path: str = DB_PATH) -> None:
    """Add qr_code column to existing databases if missing."""
    conn = get_conn(db_path)
    try:
        cols = {row[1] for row in conn.execute("PRAGMA table_info(siswa)").fetchall()}
        if "qr_code" not in cols:
            conn.execute("ALTER TABLE siswa ADD COLUMN qr_code TEXT")
            conn.commit()
    except Exception:
        pass
    finally:
        conn.close()


def q(sql: str, params: tuple = (), db_path: str = DB_PATH) -> List[dict]:
    with get_conn(db_path) as conn:
        rows = conn.execute(sql, params).fetchall()
    return [dict(r) for r in rows]


def exec_one(sql: str, params: tuple = (), db_path: str = DB_PATH) -> int:
    with tx(db_path) as conn:
        cur = conn.execute(sql, params)
    return cur.lastrowid


def exec_many(sql: str, params_list, db_path: str = DB_PATH) -> int:
    with tx(db_path) as conn:
        cur = conn.executemany(sql, params_list)
    return cur.rowcount


# ── High-level accessors ──
def siswa_all(include_inactive=False):
    sql = "SELECT * FROM siswa"
    if not include_inactive: sql += " WHERE is_active=1 AND deleted_at IS NULL"
    return q(sql + " ORDER BY nama")

def siswa_by_kelas(kelas_id):
    return q("SELECT * FROM siswa WHERE kelas_id=? AND is_active=1 AND deleted_at IS NULL ORDER BY nama", (kelas_id,))

def siswa_get(id):
    rows = q("SELECT * FROM siswa WHERE id=?", (id,)); return rows[0] if rows else None

def siswa_get_by_nis(nis):
    rows = q("SELECT * FROM siswa WHERE nis=? AND is_active=1", (nis,)); return rows[0] if rows else None

def siswa_insert(nis, nama, kelas_id, foto, alamat, no_hp, tgl_lahir):
    return exec_one("INSERT INTO siswa (nis, nama, kelas_id, foto, alamat, no_hp_ortu, tanggal_lahir) VALUES (?,?,?,?,?,?,?)",
                    (nis, nama, kelas_id, foto, alamat, no_hp, tgl_lahir))

def siswa_update(id, nis, nama, kelas_id, foto, alamat, no_hp, tgl_lahir):
    exec_one("UPDATE siswa SET nis=?, nama=?, kelas_id=?, foto=?, alamat=?, no_hp_ortu=?, tanggal_lahir=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
             (nis, nama, kelas_id, foto, alamat, no_hp, tgl_lahir, id))

def siswa_soft_delete(id):
    exec_one("UPDATE siswa SET is_active=0, deleted_at=CURRENT_TIMESTAMP WHERE id=?", (id,))

def siswa_update_qr(id, qr_path):
    exec_one("UPDATE siswa SET qr_code=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", (qr_path, id))

def kelas_all():
    return q("SELECT * FROM kelas WHERE is_active=1 ORDER BY nama")

def kelas_insert(nama, wali, ta):
    return exec_one("INSERT INTO kelas (nama, wali_kelas, tahun_ajaran) VALUES (?,?,?)", (nama, wali, ta))

def kelas_update(id, nama, wali, ta):
    exec_one("UPDATE kelas SET nama=?, wali_kelas=?, tahun_ajaran=? WHERE id=?", (nama, wali, ta, id))

def kelas_soft_delete(id):
    exec_one("UPDATE kelas SET is_active=0, deleted_at=CURRENT_TIMESTAMP WHERE id=?", (id,))

def mapel_all():
    return q("SELECT * FROM mapel ORDER BY nama")

def mapel_insert(nama, kode, jam):
    return exec_one("INSERT INTO mapel (nama, kode, jam_per_minggu) VALUES (?,?,?)", (nama, kode, jam))

def mapel_update(id, nama, kode, jam):
    exec_one("UPDATE mapel SET nama=?, kode=?, jam_per_minggu=? WHERE id=?", (nama, kode, jam, id))

def absensi_by_date(tanggal):
    return q("SELECT * FROM absensi WHERE tanggal=? ORDER BY waktu_masuk", (tanggal,))

def absensi_by_siswa(siswa_id):
    return q("SELECT * FROM absensi WHERE siswa_id=? ORDER BY tanggal DESC, waktu_masuk DESC", (siswa_id,))

def absensi_insert(siswa_id, tanggal, waktu_masuk, waktu_keluar, status, mapel_id):
    return exec_one("INSERT INTO absensi (siswa_id, tanggal, waktu_masuk, waktu_keluar, status, mapel_id) VALUES (?,?,?,?,?,?)",
                    (siswa_id, tanggal, waktu_masuk, waktu_keluar, status, mapel_id))

def absensi_update(id, status, waktu_keluar):
    exec_one("UPDATE absensi SET status=?, waktu_keluar=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", (status, waktu_keluar, id))

def absensi_log_insert(type, direction, status, message, device_id):
    exec_one("INSERT INTO sync_log (type, direction, status, message, device_id) VALUES (?,?,?,?,?)", (type, direction, status, message, device_id))

def nilai_by_siswa(siswa_id):
    return q("SELECT * FROM nilai WHERE siswa_id=? ORDER BY created_at DESC", (siswa_id,))

def nilai_by_mapel(mapel_id):
    return q("SELECT * FROM nilai WHERE mapel_id=? ORDER BY created_at DESC", (mapel_id,))

def nilai_insert(siswa_id, mapel_id, nilai, semester, ta):
    return exec_one("INSERT INTO nilai (siswa_id, mapel_id, nilai, semester, tahun_ajaran) VALUES (?,?,?,?,?)",
                    (siswa_id, mapel_id, nilai, semester, ta))

def nilai_update(id, nilai_str, semester, ta):
    exec_one("UPDATE nilai SET nilai=?, semester=?, tahun_ajaran=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", (nilai_str, semester, ta, id))

def get_setting(key):
    rows = q("SELECT value FROM pengaturan WHERE `key`=?", (key,)); return rows[0]["value"] if rows else None

def put_setting(key, value):
    exec_one("INSERT OR REPLACE INTO pengaturan (`key`, value, updated_at) VALUES (?,?,CURRENT_TIMESTAMP)", (key, value))

def paired_all_active():
    return q("SELECT * FROM paired_devices WHERE revoked=0 ORDER BY paired_at DESC")

def paired_get_by_token(token):
    rows = q("SELECT * FROM paired_devices WHERE pairing_token=? AND revoked=0", (token,)); return rows[0] if rows else None

def paired_insert(device_name, device_id, token):
    return exec_one("INSERT INTO paired_devices (device_name, device_id, pairing_token) VALUES (?,?,?)", (device_name, device_id, token))

def paired_revoke(id):
    exec_one("UPDATE paired_devices SET revoked=1 WHERE id=?", (id,))

def paired_touch_sync(id):
    exec_one("UPDATE paired_devices SET last_sync_at=CURRENT_TIMESTAMP WHERE id=?", (id,))

def audit_insert(table_name, record_id, field, old, new, changed_by):
    exec_one("INSERT INTO audit_log (table_name, record_id, field_name, old_value, new_value, changed_by) VALUES (?,?,?,?,?,?)",
             (table_name, record_id, field, old, new, changed_by))

def audit_recent(limit=100):
    return q(f"SELECT * FROM audit_log ORDER BY changed_at DESC LIMIT {limit}")

def sync_log_recent(limit=200):
    return q(f"SELECT * FROM sync_log ORDER BY created_at DESC LIMIT {limit}")

# alias kept for sync_server / bluetooth_server which use absensi_log_insert
log_sync = absensi_log_insert


# ── Jadwal Pelajaran accessors ──
def jadwal_all():
    return q("SELECT j.*, k.nama AS kelas_nama, m.nama AS mapel_nama FROM jadwal_pelajaran j LEFT JOIN kelas k ON j.kelas_id=k.id LEFT JOIN mapel m ON j.mapel_id=m.id WHERE j.is_active=1 ORDER BY j.hari, j.jam_mulai")

def jadwal_by_kelas(kelas_id):
    return q("SELECT j.*, k.nama AS kelas_nama, m.nama AS mapel_nama FROM jadwal_pelajaran j LEFT JOIN kelas k ON j.kelas_id=k.id LEFT JOIN mapel m ON j.mapel_id=m.id WHERE j.kelas_id=? AND j.is_active=1 ORDER BY j.hari, j.jam_mulai", (kelas_id,))

def jadwal_insert(kelas_id, mapel_id, hari, jam_mulai, jam_selesai, guru):
    return exec_one("INSERT INTO jadwal_pelajaran (kelas_id, mapel_id, hari, jam_mulai, jam_selesai, guru) VALUES (?,?,?,?,?,?)",
                    (kelas_id, mapel_id, hari, jam_mulai, jam_selesai, guru))

def jadwal_update(id, kelas_id, mapel_id, hari, jam_mulai, jam_selesai, guru):
    exec_one("UPDATE jadwal_pelajaran SET kelas_id=?, mapel_id=?, hari=?, jam_mulai=?, jam_selesai=?, guru=? WHERE id=?",
             (kelas_id, mapel_id, hari, jam_mulai, jam_selesai, guru, id))

def jadwal_soft_delete(id):
    exec_one("UPDATE jadwal_pelajaran SET is_active=0, deleted_at=CURRENT_TIMESTAMP WHERE id=?", (id,))


# ── Poin Disiplin accessors ──
def poin_all():
    return q("SELECT p.*, s.nama AS siswa_nama FROM poin_disiplin p LEFT JOIN siswa s ON p.siswa_id=s.id ORDER BY p.tanggal DESC, p.created_at DESC")

def poin_by_siswa(siswa_id):
    return q("SELECT p.*, s.nama AS siswa_nama FROM poin_disiplin p LEFT JOIN siswa s ON p.siswa_id=s.id WHERE p.siswa_id=? ORDER BY p.tanggal DESC", (siswa_id,))

def poin_insert(siswa_id, tanggal, kategori, poin, keterangan, diberikan_oleh):
    return exec_one("INSERT INTO poin_disiplin (siswa_id, tanggal, kategori, poin, keterangan, diberikan_oleh) VALUES (?,?,?,?,?,?)",
                    (siswa_id, tanggal, kategori, poin, keterangan, diberikan_oleh))

def poin_update(id, tanggal, kategori, poin, keterangan, diberikan_oleh):
    exec_one("UPDATE poin_disiplin SET tanggal=?, kategori=?, poin=?, keterangan=?, diberikan_oleh=? WHERE id=?",
             (tanggal, kategori, poin, keterangan, diberikan_oleh, id))

def poin_delete(id):
    exec_one("DELETE FROM poin_disiplin WHERE id=?", (id,))

def poin_summary_by_kelas(kelas_id=None):
    """Return per-siswa net poin (positif - negatif) for ranking."""
    sql = """
        SELECT s.id, s.nama, s.nis, k.nama AS kelas_nama,
               SUM(CASE WHEN p.kategori='Positif' THEN p.poin ELSE 0 END) AS poin_positif,
               SUM(CASE WHEN p.kategori='Negatif' THEN p.poin ELSE 0 END) AS poin_negatif,
               SUM(CASE WHEN p.kategori='Positif' THEN p.poin ELSE -p.poin END) AS poin_net
        FROM poin_disiplin p
        JOIN siswa s ON p.siswa_id = s.id
        LEFT JOIN kelas k ON s.kelas_id = k.id
        WHERE s.is_active = 1 AND s.deleted_at IS NULL
    """
    params = ()
    if kelas_id:
        sql += " AND s.kelas_id = ?"
        params = (kelas_id,)
    sql += " GROUP BY s.id ORDER BY poin_net DESC"
    return q(sql, params)


def ranking_nilai_by_kelas(kelas_id=None, semester=None, ta=None):
    """Return per-siswa average nilai for ranking."""
    sql = """
        SELECT s.id, s.nama, s.nis, k.nama AS kelas_nama,
               AVG(n.nilai) AS rata_rata,
               COUNT(n.id) AS jumlah_mapel
        FROM nilai n
        JOIN siswa s ON n.siswa_id = s.id
        LEFT JOIN kelas k ON s.kelas_id = k.id
        WHERE s.is_active = 1 AND s.deleted_at IS NULL
    """
    params_list = []
    if kelas_id:
        sql += " AND s.kelas_id = ?"
        params_list.append(kelas_id)
    if semester:
        sql += " AND n.semester = ?"
        params_list.append(semester)
    if ta:
        sql += " AND n.tahun_ajaran = ?"
        params_list.append(ta)
    sql += " GROUP BY s.id ORDER BY rata_rata DESC"
    return q(sql, tuple(params_list))


if __name__ == "__main__":
    init_db()
    print(f"DB initialized at: {DB_PATH}")
