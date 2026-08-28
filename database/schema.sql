-- AbsenKu Database Schema
-- Run at app first-launch to create all tables.
-- Same schema used on Android (Room) & Desktop (sqlite3).

CREATE TABLE IF NOT EXISTS siswa (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nis TEXT UNIQUE NOT NULL,
    nama TEXT NOT NULL,
    kelas_id INTEGER,
    foto TEXT,
    alamat TEXT,
    no_hp_ortu TEXT,
    tanggal_lahir TEXT,
    is_active INTEGER DEFAULT 1,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (kelas_id) REFERENCES kelas(id)
);

CREATE TABLE IF NOT EXISTS kelas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nama TEXT NOT NULL,
    wali_kelas TEXT,
    tahun_ajaran TEXT,
    is_active INTEGER DEFAULT 1,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    FOREIGN KEY (mapel_id) REFERENCES mapel(id)
);

CREATE TABLE IF NOT EXISTS nilai (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    siswa_id INTEGER,
    mapel_id INTEGER,
    nilai TEXT,
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
