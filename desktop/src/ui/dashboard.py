"""
ui/dashboard.py — Dashboard screen with stats cards + simple matplotlib-style bar.
Tkinter native (no matplotlib dep — draw bars manually on Canvas).
"""
import tkinter as tk
from tkinter import ttk
import db_manager as db


class Dashboard:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for child in p.winfo_children():
            child.destroy()

        header = ttk.Label(p, text="AbsenKu Dashboard", font=("Helvetica", 16, "bold"))
        header.pack(pady=12)

        stats = ttk.Frame(p)
        stats.pack(fill="x", pady=8)

        # stats cards
        counts = {
            "Total Siswa": 0, "Total Kelas": 0,
            "Hadir Hari Ini": 0, "Alfa Hari Ini": 0,
        }
        import datetime
        today = datetime.date.today().isoformat()
        all_siswa = db.siswa_all()
        counts["Total Siswa"] = len(all_siswa)
        counts["Total Kelas"] = len(db.kelas_all())
        today_absen = db.absensi_by_date(today)
        counts["Hadir Hari Ini"] = sum(1 for a in today_absen if a["status"] == "Hadir")
        counts["Alfa Hari Ini"] = sum(1 for a in today_absen if a["status"] == "Alfa")

        for i, (label, val) in enumerate(counts.items()):
            card = tk.Frame(stats, relief="ridge", borderwidth=1, bg="#E8F0FE")
            card.grid(row=0, column=i, padx=8, pady=8, sticky="nsew")
            stats.grid_columnconfigure(i, weight=1)
            ttk.Label(card, text=str(val), font=("Helvetica", 22, "bold"), foreground="#1A73E8", background="#E8F0FE").pack(pady=4)
            ttk.Label(card, text=label, font=("Helvetica", 9), foreground="#1A1A1A", background="#E8F0FE").pack()

        # Simple bar chart — attendance per day this week (Canvas)
        ttk.Label(p, text="Kehadiran 7 Hari Terakhir (Hadir)", font=("Helvetica", 11, "bold")).pack(anchor="w", pady=(16, 4))
        canvas = tk.Canvas(p, height=140, bg="white", highlightthickness=0)
        canvas.pack(fill="x", pady=8)

        import datetime as dt
        cal = dt.date.today()
        weekly = []
        labels = []
        for i in range(6, -1, -1):
            d = cal - dt.timedelta(days=i)
            labels.append(d.strftime("%a"))
            arr = db.absensi_by_date(d.isoformat())
            weekly.append(sum(1 for a in arr if a["status"] == "Hadir"))
        maxv = max(weekly) if weekly else 1
        maxv = max(maxv, 1)
        w = 300
        max_h = 100
        canvas.config(width=w)
        bar_w = w / (len(weekly) * 2)
        for idx, val in enumerate(weekly):
            bh = int((val / maxv) * max_h) if maxv else 0
            x = idx * (w / len(weekly)) + bar_w / 2
            canvas.create_rectangle(x, max_h - bh, x + bar_w, max_h, fill="#1A73E8", width=0)
            canvas.create_text(x + bar_w / 2, max_h + 14, text=labels[idx], font=("Helvetica", 7))
            canvas.create_text(x + bar_w / 2, max_h - bh - 6, text=str(val), font=("Helvetica", 8), fill="#5F6368")
