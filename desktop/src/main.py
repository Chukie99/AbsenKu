"""
main.py — Tkinter desktop app entry point.

- Blue-pastel minimal palette (uses ttkbootstrap if available, else native ttk).
- Sidebar navigation: Dashboard, Siswa, Kelas, Mapel, Nilai, Absensi,
  Cetak Name Tag, Laporan, Pengaturan.
- Initializes DB on start, starts backup scheduler, starts Bluetooth listener thread.
"""
import os, sys, threading

here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, here)

import db_manager as db
from utils import backup_scheduler
from utils import bluetooth_server
from utils import pairing_manager

# ── Try ttkbootstrap for modern theme; fall back to native ttk ──
try:
    import ttkbootstrap as ttk
    from ttkbootstrap.constants import *
    THEME = "flatly"
    HAS_BOOTSTRAP = True
except ImportError:
    import tkinter as ttk
    from tkinter import ttk as _ttk
    HAS_BOOTSTRAP = False


def make_root():
    if HAS_BOOTSTRAP:
        root = ttk.Window(themename=THEME)
        root.title("AbsenKu v2.0")
        root.geometry("1024x700")
    else:
        root = ttk.Tk()
        root.title("AbsenKu v2.0")
        root.geometry("1024x700")
        root.option_add("*TButton*highlightBackground", "#1A73E8")
        root.option_add("*TButton*highlightColor", "#1A73E8")
    return root


def main():
    # init DB
    db.init_db()

    # start background: backup scheduler + bluetooth listener
    scheduler = backup_scheduler.create_scheduler(hour=22, minute=0, retention=14)
    bluetooth_server.start_in_background()

    root = make_root()

    # content frame
    content = ttk.Frame(root)
    content.pack(side="right", fill="both", expand=True, padx=10, pady=10)

    # sidebar
    if HAS_BOOTSTRAP:
        sidebar = ttk.Frame(root, bootstyle="primary")
    else:
        sidebar = ttk.Frame(root, relief="ridge")
    sidebar.pack(side="left", fill="y", padx=5, pady=5)

    title = ttk.Label(sidebar, text="AbsenKu v2", font=("Helvetica", 14, "bold"),
                      **({"bootstyle": "inverse-primary"} if HAS_BOOTSTRAP else {"foreground": "#1A73E8"}))
    title.pack(pady=10)

    # menu buttons
    def show(name):
        for c in content.winfo_children():
            c.destroy()
        try:
            mod = __import__(f"ui.{name}", fromlist=[name.capitalize()])
            screen_cls = getattr(mod, name.capitalize())
            screen_cls(content).build()
        except Exception as e:
            err = ttk.Label(content, text=f"UI module '{name}' belum dibangun: {e}", foreground="#D93025")
            err.pack(pady=40)

    items = [
        ("Dashboard", "dashboard"),
        ("Siswa", "siswa"),
        ("Kelas", "kelas"),
        ("Mapel", "mapel"),
        ("Nilai", "nilai"),
        ("Absensi", "absensi"),
        ("Cetak Name Tag", "cetak_name_tag"),
        ("Laporan", "laporan"),
        ("Pengaturan", "pengaturan"),
    ]
    for label, key in items:
        btn = ttk.Button(sidebar, text=label,
                         command=lambda k=key: show(k),
                         **({"bootstyle": "secondary-outline", "takefocus": False} if HAS_BOOTSTRAP else {}))
        btn.pack(fill="x", padx=5, pady=2)

    show("dashboard")
    root.mainloop()
    scheduler.shutdown(wait=False)


if __name__ == "__main__":
    main()
