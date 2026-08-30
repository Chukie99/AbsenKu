"""
ui/pengaturan.py — Settings with tabbed layout.
Tabs: Umum | Backup | Pairing | Tentang
"""
import tkinter as tk
from tkinter import ttk, messagebox
import db_manager as db
from utils import backup_scheduler, pairing_manager


C = {
    "bg": "#F8F9FA",
    "card": "#FFFFFF",
    "accent": "#2563EB",
    "accent_light": "#EFF6FF",
    "green": "#16A34A",
    "orange": "#EA580C",
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "input_bg": "#F1F5F9",
}


class Pengaturan:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # Header
        tk.Label(p, text="Pengaturan", font=("Segoe UI", 16, "bold"),
                 fg=C["text"], bg=C["bg"]).pack(anchor="w", padx=20, pady=(16, 0))

        # Tab notebook
        style = ttk.Style()
        style.configure("TNotebook", background=C["bg"], borderwidth=0)
        style.configure("TNotebook.Tab", font=("Segoe UI", 10), padding=[16, 6])

        notebook = ttk.Notebook(p)
        notebook.pack(fill="both", expand=True, padx=20, pady=12)

        # ── Tab: Umum ──
        tab_umum = tk.Frame(notebook, bg=C["card"])
        notebook.add(tab_umum, text="  Umum  ")
        self._build_umum(tab_umum)

        # ── Tab: Backup ──
        tab_backup = tk.Frame(notebook, bg=C["card"])
        notebook.add(tab_backup, text="  Backup  ")
        self._build_backup(tab_backup)

        # ── Tab: Pairing ──
        tab_pairing = tk.Frame(notebook, bg=C["card"])
        notebook.add(tab_pairing, text="  Pairing  ")
        self._build_pairing(tab_pairing)

        # ── Tab: Tentang ──
        tab_tentang = tk.Frame(notebook, bg=C["card"])
        notebook.add(tab_tentang, text="  Tentang  ")
        self._build_tentang(tab_tentang)

    def _make_entry(self, parent, label_text, default=""):
        frame = tk.Frame(parent, bg=C["card"])
        frame.pack(fill="x", padx=20, pady=(8, 0))
        tk.Label(frame, text=label_text, font=("Segoe UI", 10),
                 fg=C["text2"], bg=C["card"]).pack(anchor="w")
        entry = tk.Entry(frame, font=("Segoe UI", 11), bg=C["input_bg"],
                        relief="flat", highlightthickness=1,
                        highlightbackground=C["border"], highlightcolor=C["accent"])
        entry.pack(fill="x", ipady=6, pady=(4, 0))
        entry.insert(0, default)
        return entry

    def _make_button(self, parent, text, color, command):
        btn = tk.Button(parent, text=text, font=("Segoe UI", 10, "bold"),
                       bg=color, fg="white", relief="flat",
                       activebackground=color, cursor="hand2",
                       padx=16, pady=8, command=command)
        return btn

    def _build_umum(self, parent):
        tk.Label(parent, text="Data Sekolah", font=("Segoe UI", 12, "bold"),
                 fg=C["text"], bg=C["card"]).pack(anchor="w", padx=20, pady=(16, 8))

        self.sekolah = self._make_entry(parent, "Nama Sekolah",
                                         db.get_setting("store_name") or "")
        self.guru = self._make_entry(parent, "Nama Guru",
                                      db.get_setting("teacher_name") or "")
        self.ta = self._make_entry(parent, "Tahun Ajaran",
                                    db.get_setting("year") or "2025/2026")

        btn_frame = tk.Frame(parent, bg=C["card"])
        btn_frame.pack(fill="x", padx=20, pady=16)
        self._make_button(btn_frame, "Simpan Pengaturan", C["accent"],
                         lambda: self._save(self.sekolah.get(), self.guru.get(), self.ta.get())).pack(side="left")

    def _build_backup(self, parent):
        tk.Label(parent, text="Backup & Restore Database", font=("Segoe UI", 12, "bold"),
                 fg=C["text"], bg=C["card"]).pack(anchor="w", padx=20, pady=(16, 8))

        info = tk.Label(parent, text="Backup otomatis berjalan setiap jam 22:00.\n"
                       "Data disimpan di folder: desktop/data/backups/",
                       font=("Segoe UI", 9), fg=C["text2"], bg=C["card"],
                       justify="left")
        info.pack(anchor="w", padx=20, pady=(0, 12))

        btn_frame = tk.Frame(parent, bg=C["card"])
        btn_frame.pack(fill="x", padx=20)
        self._make_button(btn_frame, "Backup Sekarang", C["green"],
                         self.backup_now).pack(side="left", padx=(0, 8))
        self._make_button(btn_frame, "Restore...", C["orange"],
                         self.restore_now).pack(side="left")

    def _build_pairing(self, parent):
        tk.Label(parent, text="Pairing & Sinkronisasi", font=("Segoe UI", 12, "bold"),
                 fg=C["text"], bg=C["card"]).pack(anchor="w", padx=20, pady=(16, 8))

        tk.Label(parent, text="Generate PIN untuk menghubungkan HP ke Desktop.",
                 font=("Segoe UI", 9), fg=C["text2"], bg=C["card"]).pack(anchor="w", padx=20)

        btn_frame = tk.Frame(parent, bg=C["card"])
        btn_frame.pack(fill="x", padx=20, pady=8)
        self._make_button(btn_frame, "Generate 6-Digit PIN", C["accent"],
                         self.gen_pin).pack(side="left")

        self.pin_lbl = tk.Label(parent, text="", font=("Consolas", 18, "bold"),
                                fg=C["accent"], bg=C["card"])
        self.pin_lbl.pack(pady=8)

        tk.Label(parent, text="Perangkat Terpasang", font=("Segoe UI", 10, "bold"),
                 fg=C["text"], bg=C["card"]).pack(anchor="w", padx=20, pady=(12, 4))

        list_frame = tk.Frame(parent, bg=C["card"])
        list_frame.pack(fill="both", expand=True, padx=20, pady=(0, 12))

        self.dev_list = tk.Listbox(list_frame, font=("Segoe UI", 10),
                                   relief="flat", highlightthickness=1,
                                   highlightbackground=C["border"],
                                   selectbackground=C["accent_light"],
                                   selectforeground=C["text"])
        self.dev_list.pack(fill="both", expand=True)
        self.refresh_devices()

    def _build_tentang(self, parent):
        tk.Label(parent, text="AbsenKu v2.0", font=("Segoe UI", 14, "bold"),
                 fg=C["text"], bg=C["card"]).pack(pady=(24, 4))
        tk.Label(parent, text="Sistem Absensi & Nilai Siswa Offline",
                 font=("Segoe UI", 10), fg=C["text2"], bg=C["card"]).pack()
        tk.Label(parent, text="100% Offline — Database SQLite Lokal",
                 font=("Segoe UI", 9), fg=C["text2"], bg=C["card"]).pack(pady=(4, 16))

        # Separator
        tk.Frame(parent, bg=C["border"], height=1).pack(fill="x", padx=20)

        info_frame = tk.Frame(parent, bg=C["card"])
        info_frame.pack(fill="x", padx=20, pady=12)
        for label, val in [("Platform", "Android + Desktop"),
                          ("Database", "SQLite (offline)"),
                          ("Sync", "Bluetooth / WiFi LAN"),
                          ("Developer", "Sopian (082261407123)")]:
            row = tk.Frame(info_frame, bg=C["card"])
            row.pack(fill="x", pady=2)
            tk.Label(row, text=label + ":", font=("Segoe UI", 9, "bold"),
                     fg=C["text2"], bg=C["card"], width=12, anchor="w").pack(side="left")
            tk.Label(row, text=val, font=("Segoe UI", 9),
                     fg=C["text"], bg=C["card"]).pack(side="left")

    def _save(self, sekolah, guru, ta):
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
            self.dev_list.insert(tk.END, f"{d['device_name']} ({d['device_id'][:8]}...)")
