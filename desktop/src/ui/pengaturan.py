"""
ui/pengaturan.py — school data, sync config (token management), backup schedule.
"""
import tkinter as tk
from tkinter import ttk, messagebox
import db_manager as db
from utils import backup_scheduler, pairing_manager


class Pengaturan:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for c in p.winfo_children(): c.destroy()
        ttk.Label(p, text="Pengaturan", font=("Helvetica", 14, "bold")).pack(pady=8)

        ttk.Label(p, text="Nama Sekolah").pack(anchor="w", padx=12)
        sekolah = tk.Entry(p, width=40); sekolah.pack(fill="x", padx=12, pady=2)
        sekolah.insert(0, db.get_setting("store_name") or "")

        ttk.Label(p, text="Nama Guru").pack(anchor="w", padx=12, pady=(8,0))
        guru = tk.Entry(p, width=40); guru.pack(fill="x", padx=12, pady=2)
        guru.insert(0, db.get_setting("teacher_name") or "")

        ttk.Label(p, text="Tahun Ajaran").pack(anchor="w", padx=12, pady=(8,0))
        ta = tk.Entry(p, width=40); ta.pack(fill="x", padx=12, pady=2)
        ta.insert(0, db.get_setting("year") or "2025/2026")

        tk.Button(p, text="Simpan Pengaturan", bg="#1A73E8", fg="white", command=lambda: self.save(sekolah.get(),guru.get(),ta.get()), relief="flat", padx=10, pady=5).pack(pady=12)

        # backup section
        ttk.Label(p, text="Backup Database", font=("Helvetica", 11, "bold")).pack(anchor="w", padx=12, pady=(16,4))
        tk.Button(p, text="Backup Sekarang", bg="#34A853", fg="white", command=self.backup_now, relief="flat", padx=10, pady=5).pack(pady=4)
        tk.Button(p, text="Restore...", bg="#FBBC04", fg="black", command=self.restore_now, relief="flat", padx=10, pady=5).pack(pady=4)

        ttk.Label(p, text="Pairing & Sync", font=("Helvetica", 11, "bold")).pack(anchor="w", padx=12, pady=(16,4))
        tk.Button(p, text="Generate 6-Digit PIN (untuk HP)", bg="#1A73E8", fg="white", command=self.gen_pin, relief="flat", padx=10, pady=5).pack(pady=4)
        pin_lbl = ttk.Label(p, text="", font=("Helvetica", 14, "bold"), foreground="#1557B0")
        pin_lbl.pack(pady=6)
        self.pin_lbl = pin_lbl

        ttk.Label(p, text="Perangkat Terpasang", font=("Helvetica", 10, "bold")).pack(anchor="w", padx=12, pady=(8,2))
        lb = tk.Listbox(p, height=5)
        lb.pack(fill="x", padx=12, pady=4)
        self.dev_list = lb
        self.refresh_devices()

    def save(self, sekolah, guru, ta):
        db.put_setting("store_name", sekolah)
        db.put_setting("teacher_name", guru)
        db.put_setting("year", ta)
        messagebox.showinfo("Sukses", "Pengaturan disimpan")

    def gen_pin(self):
        pin = pairing_manager.issue_pin()
        self.pin_lbl.config(text=pin)
        messagebox.showinfo("PIN", f"PIN: {pin}\nBerlaku 5 menit. Berikan ke HP untuk input di aplikasi.")

    def backup_now(self):
        path = backup_scheduler.backup_once()
        messagebox.showinfo("Backup", f"Backup berhasil:\n{path}")

    def restore_now(self):
        from tkinter import filedialog
        path = filedialog.askopenfilename(filetypes=[("SQLite DB", "*.db")])
        if path and backup_scheduler.restore_once(path):
            messagebox.showinfo("Restore", "Restore berhasil. Restart aplikasi.")
        else:
            messagebox.showerror("Error", "Checksum gagal / file tidak valid")

    def refresh_devices(self):
        self.dev_list.delete(0, tk.END)
        for d in pairing_manager.list_paired():
            self.dev_list.insert(tk.END, f"{d['device_name']} ({d['device_id'][:8]})")
