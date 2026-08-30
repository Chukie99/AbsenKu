"""
main.py — Tkinter desktop app entry point.

- Clean minimal palette (uses ttkbootstrap if available, else native ttk).
- Sidebar navigation with icons + labels.
- Initializes DB on start, starts backup scheduler, starts Bluetooth listener.
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
    import tkinter as tk
    from tkinter import ttk as _ttk
    HAS_BOOTSTRAP = False

# ── Color palette ──
C = {
    "sidebar_bg": "#1E293B",
    "sidebar_fg": "#CBD5E1",
    "sidebar_active": "#2563EB",
    "sidebar_hover": "#334155",
    "accent": "#2563EB",
    "bg": "#F8F9FA",
}


def make_root():
    if HAS_BOOTSTRAP:
        root = ttk.Window(themename=THEME)
    else:
        root = tk.Tk()
    root.title("AbsenKu v2.0")
    root.geometry("1024x700")
    root.minsize(800, 500)
    try:
        root.iconbitmap(default="")
    except Exception:
        pass
    return root


def main():
    # init DB
    db.init_db()

    # start background: backup scheduler + bluetooth listener
    scheduler = backup_scheduler.create_scheduler(hour=22, minute=0, retention=14)
    bluetooth_server.start_in_background()

    root = make_root()

    # ── Sidebar ──
    if HAS_BOOTSTRAP:
        sidebar = ttk.Frame(root, bootstyle="dark", width=200)
    else:
        sidebar = tk.Frame(root, bg=C["sidebar_bg"], width=200)
    sidebar.pack(side="left", fill="y")
    sidebar.pack_propagate(False)

    # Logo / title
    if HAS_BOOTSTRAP:
        logo_frame = ttk.Frame(sidebar, bootstyle="dark")
    else:
        logo_frame = tk.Frame(sidebar, bg=C["sidebar_bg"])
    logo_frame.pack(fill="x", pady=(16, 20), padx=12)

    if HAS_BOOTSTRAP:
        ttk.Label(logo_frame, text="📋 AbsenKu", font=("Helvetica", 14, "bold"),
                  bootstyle="inverse-primary").pack(anchor="w")
    else:
        tk.Label(logo_frame, text="📋 AbsenKu", font=("Segoe UI", 14, "bold"),
                fg="white", bg=C["sidebar_bg"]).pack(anchor="w")
        tk.Label(logo_frame, text="v2.0", font=("Segoe UI", 8),
                fg=C["sidebar_fg"], bg=C["sidebar_bg"]).pack(anchor="w")

    # ── Menu items with icons ──
    items = [
        ("📊  Dashboard", "dashboard"),
        ("👤  Siswa", "siswa"),
        ("🏫  Kelas", "kelas"),
        ("📚  Mapel", "mapel"),
        ("📝  Nilai", "nilai"),
        ("✅  Absensi", "absensi"),
        ("🏷️  Cetak Name Tag", "cetak_name_tag"),
        ("📈  Laporan", "laporan"),
        ("⚙️  Pengaturan", "pengaturan"),
    ]

    # Content frame
    if HAS_BOOTSTRAP:
        content = ttk.Frame(root)
    else:
        content = tk.Frame(root, bg=C["bg"])
    content.pack(side="right", fill="both", expand=True)

    # Active button tracking
    active_btn = [None]

    def show(name, btn_widget=None):
        for c in content.winfo_children():
            c.destroy()
        try:
            mod = __import__(f"ui.{name}", fromlist=[name.capitalize()])
            screen_cls = getattr(mod, name.capitalize())
            screen_cls(content).build()
        except Exception as e:
            if HAS_BOOTSTRAP:
                err = ttk.Label(content, text=f"Module '{name}' error: {e}",
                              foreground="#D93025")
            else:
                err = tk.Label(content, text=f"Module '{name}' error: {e}",
                             fg="#D93025", bg=C["bg"])
            err.pack(pady=40)

        # Update active state
        if active_btn[0] and not HAS_BOOTSTRAP:
            active_btn[0].config(bg=C["sidebar_bg"], fg=C["sidebar_fg"])
        if btn_widget and not HAS_BOOTSTRAP:
            btn_widget.config(bg=C["sidebar_active"], fg="white")
        active_btn[0] = btn_widget

    # Build sidebar buttons
    for label, key in items:
        if HAS_BOOTSTRAP:
            btn = ttk.Button(sidebar, text=label,
                           command=lambda k=key: show(k),
                           bootstyle="secondary-outline", takefocus=False)
        else:
            btn = tk.Button(sidebar, text=label, font=("Segoe UI", 10),
                          bg=C["sidebar_bg"], fg=C["sidebar_fg"],
                          activebackground=C["sidebar_hover"],
                          activeforeground="white",
                          relief="flat", anchor="w", padx=12, pady=8,
                          cursor="hand2",
                          command=lambda k=key, b=btn: show(k, b))
        btn.pack(fill="x", padx=8, pady=1)

    show("dashboard")
    root.mainloop()
    scheduler.shutdown(wait=False)


if __name__ == "__main__":
    main()
