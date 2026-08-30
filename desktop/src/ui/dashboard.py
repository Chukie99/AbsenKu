"""
ui/dashboard.py — Dashboard screen with stats cards + bar chart.
Clean, minimal design — not AI-template looking.
"""
import tkinter as tk
from tkinter import ttk
import db_manager as db
import datetime


# ── Color palette (warm, not corporate) ──
C = {
    "bg": "#F8F9FA",
    "card": "#FFFFFF",
    "accent": "#2563EB",
    "accent_light": "#EFF6FF",
    "green": "#16A34A",
    "green_light": "#F0FDF4",
    "red": "#DC2626",
    "red_light": "#FEF2F2",
    "orange": "#EA580C",
    "orange_light": "#FFF7ED",
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "bar": "#2563EB",
    "bar_empty": "#E2E8F0",
}


class Dashboard:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for child in p.winfo_children():
            child.destroy()

        # ── Header ──
        hdr = tk.Frame(p, bg=C["bg"])
        hdr.pack(fill="x", padx=20, pady=(16, 0))
        tk.Label(hdr, text="Dashboard", font=("Segoe UI", 18, "bold"),
                 fg=C["text"], bg=C["bg"]).pack(anchor="w")
        today_str = datetime.date.today().strftime("%A, %d %B %Y")
        tk.Label(hdr, text=today_str, font=("Segoe UI", 10),
                 fg=C["text2"], bg=C["bg"]).pack(anchor="w")

        # ── Stats cards ──
        today = datetime.date.today().isoformat()
        all_siswa = db.siswa_all()
        all_kelas = db.kelas_all()
        today_absen = db.absensi_by_date(today)
        hadir = sum(1 for a in today_absen if a["status"] == "Hadir")
        alfa = sum(1 for a in today_absen if a["status"] == "Alfa")
        izin = sum(1 for a in today_absen if a["status"] == "Izin")
        sakit = sum(1 for a in today_absen if a["status"] == "Sakit")

        cards_frame = tk.Frame(p, bg=C["bg"])
        cards_frame.pack(fill="x", padx=20, pady=12)

        stats = [
            ("Total Siswa", str(len(all_siswa)), C["accent"], C["accent_light"]),
            ("Total Kelas", str(len(all_kelas)), C["green"], C["green_light"]),
            ("Hadir Hari Ini", str(hadir), C["green"], C["green_light"]),
            ("Alfa Hari Ini", str(alfa), C["red"], C["red_light"]),
        ]

        for i, (label, val, color, bg_color) in enumerate(stats):
            card = tk.Frame(cards_frame, bg=bg_color, relief="flat",
                           highlightbackground=C["border"], highlightthickness=1)
            card.grid(row=0, column=i, padx=6, pady=4, sticky="nsew")
            cards_frame.grid_columnconfigure(i, weight=1)

            inner = tk.Frame(card, bg=bg_color)
            inner.pack(padx=16, pady=12, anchor="w")

            # Colored left bar
            bar = tk.Frame(inner, bg=color, width=4, height=40)
            bar.pack(side="left", padx=(0, 12))
            bar.pack_propagate(False)

            tk.Label(inner, text=val, font=("Segoe UI", 24, "bold"),
                     fg=color, bg=bg_color).pack(anchor="w")
            tk.Label(inner, text=label, font=("Segoe UI", 9),
                     fg=C["text2"], bg=bg_color).pack(anchor="w")

        # ── Attendance summary ──
        sum_frame = tk.Frame(p, bg=C["bg"])
        sum_frame.pack(fill="x", padx=20, pady=(4, 8))

        for label, val, color in [("Hadir", hadir, C["green"]),
                                   ("Izin", izin, C["orange"]),
                                   ("Sakit", sakit, C["accent"]),
                                   ("Alfa", alfa, C["red"])]:
            sf = tk.Frame(sum_frame, bg=C["card"], relief="flat",
                         highlightbackground=C["border"], highlightthickness=1)
            sf.pack(side="left", padx=4, expand=True, fill="x")
            tk.Label(sf, text=str(val), font=("Segoe UI", 14, "bold"),
                     fg=color, bg=C["card"]).pack(pady=(8, 0))
            tk.Label(sf, text=label, font=("Segoe UI", 9),
                     fg=C["text2"], bg=C["card"]).pack(pady=(0, 8))

        # ── Bar chart — 7 day attendance ──
        chart_frame = tk.Frame(p, bg=C["card"], relief="flat",
                              highlightbackground=C["border"], highlightthickness=1)
        chart_frame.pack(fill="both", expand=True, padx=20, pady=(0, 16))

        tk.Label(chart_frame, text="Kehadiran 7 Hari Terakhir",
                 font=("Segoe UI", 11, "bold"), fg=C["text"],
                 bg=C["card"]).pack(anchor="w", padx=16, pady=(12, 4))

        canvas = tk.Canvas(chart_frame, height=160, bg=C["card"],
                          highlightthickness=0)
        canvas.pack(fill="both", expand=True, padx=16, pady=(0, 12))

        # Draw after layout
        def draw_bars(event=None):
            canvas.delete("all")
            w = canvas.winfo_width()
            if w < 100:
                return

            cal = datetime.date.today()
            weekly = []
            day_labels = []
            for i in range(6, -1, -1):
                d = cal - datetime.timedelta(days=i)
                day_labels.append(d.strftime("%a"))
                arr = db.absensi_by_date(d.isoformat())
                weekly.append(sum(1 for a in arr if a["status"] == "Hadir"))

            maxv = max(weekly) if weekly else 1
            maxv = max(maxv, 1)

            chart_h = 120
            bar_gap = 8
            bar_w = (w - bar_gap * (len(weekly) + 1)) / len(weekly)

            for idx, val in enumerate(weekly):
                bh = int((val / maxv) * chart_h) if maxv else 0
                x = bar_gap + idx * (bar_w + bar_gap)

                # Bar
                canvas.create_rectangle(x, chart_h - bh + 10, x + bar_w, chart_h + 10,
                                       fill=C["bar"], outline="", width=0)
                # Value on top
                canvas.create_text(x + bar_w / 2, chart_h - bh + 2,
                                  text=str(val), font=("Segoe UI", 8),
                                  fill=C["text2"])
                # Day label
                canvas.create_text(x + bar_w / 2, chart_h + 24,
                                  text=day_labels[idx], font=("Segoe UI", 8),
                                  fill=C["text2"])

        canvas.bind("<Configure>", draw_bars)
