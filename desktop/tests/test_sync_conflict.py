"""
tests/test_sync_conflict.py — merge logic: last-write-wins + conflict logged.
"""
import os, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "src"))

import db_manager as db


def test_conflict_last_write_wins(tmp_path):
    """Server should keep the record with the newest updated_at timestamp."""
    test_db = str(tmp_path / "test_conflict.db")
    db.init_db(test_db)

    db.exec_one("INSERT INTO kelas (id, nama) VALUES (1, 'X IPA')", db_path=test_db)
    db.exec_one("INSERT INTO siswa (id, nis, nama, kelas_id) VALUES (1, 'S001', 'Old Name', 1)", db_path=test_db)

    now_ts = int(time.time() * 1000)
    db.exec_one("UPDATE siswa SET nama='Alice', updated_at=? WHERE id=1", (now_ts,), db_path=test_db)

    row = db.q("SELECT * FROM siswa WHERE id=1", db_path=test_db)[0]
    assert row["nama"] == "Alice", f"expected Alice, got {row['nama']}"
    print("PASS: conflict last-write-wins -> Alice kept")


def test_conflict_logged_to_audit(tmp_path):
    """When a server-side conflict is resolved, it should log to audit_log."""
    test_db = str(tmp_path / "test_audit.db")
    db.init_db(test_db)
    db.exec_one("INSERT INTO kelas (id, nama) VALUES (1, 'X IPA')", db_path=test_db)
    db.exec_one("INSERT INTO siswa (id, nis, nama, kelas_id) VALUES (1,'S001','Alice',1)", db_path=test_db)
    db.exec_one("INSERT INTO audit_log (table_name, record_id, field_name, old_value, new_value, changed_by) VALUES ('siswa',1,'nama','Bob','Alice','device-B')",
                db_path=test_db)
    rows = db.q("SELECT * FROM audit_log WHERE record_id=1 AND table_name='siswa'", db_path=test_db)
    assert len(rows) == 1, f"expected 1 audit row, got {len(rows)}"
    assert rows[0]["old_value"] == "Bob"
    assert rows[0]["new_value"] == "Alice"
    print("PASS: conflict logged to audit_log (old=Bob, new=Alice)")
