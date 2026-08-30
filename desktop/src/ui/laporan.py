"""
ui/laporan.py — laporan absen, nilai, rekap gabungan → PDF/Excel + Ranking siswa.
"""
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import excel_exporter, pdf_generator
from datetime import datetime


# ── Color palette ──
C = {
    "bg": "#F8F9FA",
    "card": "#FFFFFF",
    "accent": "#2563EB",
    "accent_light": "#EFF6FF",
    "green": "#16A34A",
    "green_light": "#F0FDF4",
    "red": "#DC2626",
    "red_light": "#FEF2F2",
    "gold": "#F59E0B",
    "gold_bg": "#FFFBEB",
    "silver": "#9CA3AF",
    "silver_bg": "#F3F4F6",
    "bronze": "#D97706",
    "bronze_bg": "#FEF3C7",
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "tab_active": "#2563EB",
    "tab_inactive": "#E2E8F0",
}

# Medal config
MEDALS = [
    {"icon": "🥇", "color": C["gold"], "bg": C["gold_bg"], "label": "Emas"},
    {"icon": "🥈", "color": C["silver"], "bg": C["silver_bg"], "label": "Perak"},
    {"icon": "🥉", "color": C["bronze"], "bg": C["bronze_bg"], "label": "Perunggu"},
]


class Laporan:
    def __init__(self, parent):
        self.parent = parent
        self._tab = "export"  # current active tab

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # ── Header ──
        hdr = tk.Frame(p, bg=C["bg"])
        hdr.pack(fill="x", padx=20, pady=(16, 0))
        tk.Label(hdr, text="Laporan & Export", font=("Segoe UI", 18, "bold"),
                 fg=C["text"], bg=C["bg"]).pack(anchor="w")

        # ── Tab switcher ──
        tab_frame = tk.Frame(p, bg=C["bg"])
        tab_frame.pack(fill="x", padx=20, pady=(12, 0))

        self._tab_btns = {}
        for key, label in [("export", "📄  Export"), ("ranking", "🏆  Ranking")]:
            btn = tk.Button(tab_frame, text=label, font=("Segoe UI", 10, "bold"),
                          relief="flat", padx=16, pady=6, cursor="hand2",
                          command=lambda k=key: self._switch_tab(k))
            btn.pack(side="left", padx=(0, 4))
            self._tab_btns[key] = btn

        # Separator
        sep = tk.Frame(p, bg=C["border"], height=1)
        sep.pack(fill="x", padx=20, pady=(8, 0))

        # Content area
        self._content = tk.Frame(p, bg=C["bg"])
        self._content.pack(fill="both", expand=True, padx=20, pady=(8, 16))

        self._switch_tab("export")

    def _switch_tab(self, tab):
        self._tab = tab
        # Update button styles
        for key, btn in self._tab_btns.items():
            if key == tab:
                btn.config(bg=C["tab_active"], fg="white", activebackground=C["tab_active"])
            else:
                btn.config(bg=C["tab_inactive"], fg=C["text"], activebackground=C["tab_inactive"])

        # Clear content
        for c in self._content.winfo_children():
            c.destroy()

        if tab == "export":
            self._build_export_tab()
        elif tab == "ranking":
            self._build_ranking_tab()

    # ── Export Tab (original functionality) ──
    def _build_export_tab(self):
        p = self._content

        card = tk.Frame(p, bg=C["card"], relief="flat",
                       highlightbackground=C["border"], highlightthickness=1)
        card.pack(fill="x", pady=4)

        inner = tk.Frame(card, bg=C["card"])
        inner.pack(padx=20, pady=16, anchor="w")

        tk.Label(inner, text="Export Laporan", font=("Segoe UI", 13, "bold"),
                 fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(0, 12))

        btns = tk.Frame(inner, bg=C["card"])
        btns.pack(fill="x")

        export_items = [
            ("Laporan Absensi (Hari Ini) → PDF", "#1A73E8", "white", self.absen_pdf),
            ("Laporan Absensi (Hari Ini) → Excel", "#34A853", "white", self.absen_xlsx),
            ("Laporan Nilai → Excel", "#FBBC04", "black", self.nilai_xlsx),
            ("Rapor Siswa → PDF", "#D93025", "white", self.rapor_pdf),
        ]

        for text, bg, fg, cmd in export_items:
            tk.Button(btns, text=text, bg=bg, fg=fg, command=cmd,
                     relief="flat", padx=12, pady=6, font=("Segoe UI", 10),
                     cursor="hand2").pack(fill="x", pady=3)

        tk.Label(inner, text="Export riwayat ke: /home atau pilih folder",
                font=("Segoe UI", 9), fg=C["text2"], bg=C["card"]).pack(anchor="w", pady=(10, 0))

    # ── Ranking Tab ──
    def _build_ranking_tab(self):
        p = self._content

        # Fetch ranking data
        ranking_data = self._compute_ranking()

        if not ranking_data:
            empty_card = tk.Frame(p, bg=C["card"], relief="flat",
                                 highlightbackground=C["border"], highlightthickness=1)
            empty_card.pack(fill="both", expand=True, pady=4)
            tk.Label(empty_card, text="📊 Belum ada data nilai",
                    font=("Segoe UI", 14), fg=C["text2"], bg=C["card"]).pack(expand=True)
            tk.Label(empty_card, text="Silakan input nilai terlebih dahulu di menu Nilai.",
                    font=("Segoe UI", 10), fg=C["text2"], bg=C["card"]).pack(pady=(4, 20))
            return

        # ── Summary stats ──
        summary_frame = tk.Frame(p, bg=C["bg"])
        summary_frame.pack(fill="x", pady=(0, 8))

        total = len(ranking_data)
        avg_all = sum(r["avg"] for r in ranking_data) / total if total else 0

        for label, val, color, bg_c in [
            ("Total Siswa", str(total), C["accent"], C["accent_light"]),
            ("Rata-rata Umum", f"{avg_all:.1f}", C["green"], C["green_light"]),
            ("Peringkat 1", ranking_data[0]["nama"] if ranking_data else "-", C["gold"], C["gold_bg"]),
        ]:
            card = tk.Frame(summary_frame, bg=bg_c, relief="flat",
                           highlightbackground=C["border"], highlightthickness=1)
            card.pack(side="left", padx=4, expand=True, fill="x")
            tk.Label(card, text=val, font=("Segoe UI", 14, "bold"),
                     fg=color, bg=bg_c).pack(pady=(10, 0))
            tk.Label(card, text=label, font=("Segoe UI", 9),
                     fg=C["text2"], bg=bg_c).pack(pady=(0, 10))

        # ── Ranking list ──
        list_frame = tk.Frame(p, bg=C["card"], relief="flat",
                             highlightbackground=C["border"], highlightthickness=1)
        list_frame.pack(fill="both", expand=True)

        tk.Label(list_frame, text="🏆 Ranking Siswa (Berdasarkan Rata-rata Nilai)",
                font=("Segoe UI", 12, "bold"), fg=C["text"], bg=C["card"]).pack(
                    anchor="w", padx=16, pady=(12, 4))

        # Scrollable area
        canvas = tk.Canvas(list_frame, bg=C["card"], highlightthickness=0)
        scrollbar = ttk.Scrollbar(list_frame, orient="vertical", command=canvas.yview)
        scrollable = tk.Frame(canvas, bg=C["card"])

        scrollable.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0, 0), window=scrollable, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)

        canvas.pack(side="left", fill="both", expand=True, padx=(16, 0), pady=(0, 12))
        scrollbar.pack(side="right", fill="y", pady=(0, 12), padx=(0, 16))

        # Bind mousewheel
        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")
        canvas.bind_all("<MouseWheel>", _on_mousewheel)

        # Header row
        hdr = tk.Frame(scrollable, bg=C["border"])
        hdr.pack(fill="x", pady=(0, 2))
        for col, text, width in [("rank", "Rank", 60), ("medal", "", 50),
                                  ("nama", "Nama Siswa", 200), ("kelas", "Kelas", 120),
                                  ("avg", "Rata-rata", 100), ("total_nilai", "Jml Nilai", 80)]:
            lbl = tk.Label(hdr, text=text, font=("Segoe UI", 10, "bold"),
                          fg=C["text"], bg=C["border"], width=width, anchor="w",
                          padx=8, pady=6)
            lbl.pack(side="left")

        # Data rows
        for i, row in enumerate(ranking_data):
            rank = i + 1
            medal_info = MEDALS[i] if i < 3 else None
            bg_row = medal_info["bg"] if medal_info else (C["bg"] if rank % 2 == 0 else C["card"])

            row_frame = tk.Frame(scrollable, bg=bg_row)
            row_frame.pack(fill="x", pady=1)

            # Rank number
            rank_color = medal_info["color"] if medal_info else C["text2"]
            rank_font = ("Segoe UI", 11, "bold") if medal_info else ("Segoe UI", 11)
            tk.Label(row_frame, text=str(rank), font=rank_font, fg=rank_color,
                     bg=bg_row, width=6, anchor="w", padx=8).pack(side="left")

            # Medal icon
            medal_text = medal_info["icon"] if medal_info else ""
            tk.Label(row_frame, text=medal_text, font=("Segoe UI", 14),
                     bg=bg_row, width=3).pack(side="left")

            # Name (bold for top 3)
            name_font = ("Segoe UI", 10, "bold") if rank <= 3 else ("Segoe UI", 10)
            tk.Label(row_frame, text=row["nama"], font=name_font,
                     fg=C["text"], bg=bg_row, width=20, anchor="w", padx=8).pack(side="left")

            # Kelas
            tk.Label(row_frame, text=row["kelas"] or "-", font=("Segoe UI", 10),
                     fg=C["text2"], bg=bg_row, width=12, anchor="w", padx=8).pack(side="left")

            # Average (color-coded: green ≥ 80, yellow ≥ 60, red < 60)
            avg = row["avg"]
            if avg >= 80:
                avg_color = C["green"]
            elif avg >= 60:
                avg_color = "#D97706"
            else:
                avg_color = C["red"]
            tk.Label(row_frame, text=f"{avg:.1f}", font=("Segoe UI", 11, "bold"),
                     fg=avg_color, bg=bg_row, width=8, anchor="w", padx=8).pack(side="left")

            # Total nilai count
            tk.Label(row_frame, text=str(row["count"]), font=("Segoe UI", 10),
                     fg=C["text2"], bg=bg_row, width=8, anchor="w", padx=8).pack(side="left")

    def _compute_ranking(self):
        """Compute student ranking by average nilai."""
        rows = db.q("""
            SELECT s.id, s.nama, s.nis, k.nama AS kelas,
                   AVG(n.nilai) AS avg_nilai, COUNT(n.nilai) AS cnt
            FROM siswa s
            LEFT JOIN kelas k ON s.kelas_id = k.id
            LEFT JOIN nilai n ON s.id = n.siswa_id
            WHERE s.is_active = 1 AND s.deleted_at IS NULL
            GROUP BY s.id, s.nama, k.nama
            HAVING cnt > 0
            ORDER BY avg_nilai DESC, s.nama ASC
        """)
        return [{
            "id": r["id"],
            "nama": r["nama"],
            "nis": r["nis"],
            "kelas": r["kelas"],
            "avg": r["avg_nilai"],
            "count": r["cnt"],
        } for r in rows]

    # ── Original export methods (unchanged) ──
    def _today(self):
        return datetime.now().strftime("%Y-%m-%d")

    def absen_pdf(self):
        tgl = self._today()
        rows = db.absensi_by_date(tgl)
        path = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"absen_{tgl}.pdf")
        if not path: return
        pdf_generator.export_absensi_pdf(path, rows, tgl)
        messagebox.showinfo("Sukses", f"Export ke {path}")

    def absen_xlsx(self):
        rows = db.absensi_by_date(self._today())
        path = filedialog.asksaveasfilename(defaultextension=".xlsx", initialfile="absen.xlsx")
        if not path: return
        excel_exporter.export_absensi_xlsx(path, rows)
        messagebox.showinfo("Sukses", f"Export ke {path}")

    def nilai_xlsx(self):
        rows = db.q("SELECT s.nama,s.nis,m.nama AS mapel_nama,n.* FROM nilai n JOIN siswa s ON n.siswa_id=s.id JOIN mapel m ON n.mapel_id=m.id ORDER BY s.nama")
        path = filedialog.asksaveasfilename(defaultextension=".xlsx", initialfile="nilai.xlsx")
        if not path: return
        excel_exporter.export_nilai_xlsx(path, rows)
        messagebox.showinfo("Sukses", f"Export ke {path}")

    def rapor_pdf(self):
        nis = tk.simpledialog.askstring("Rapor", "Masukkan NIS:")
        if not nis: return
        s = db.siswa_get_by_nis(nis)
        if not s:
            messagebox.showerror("Error", "Siswa tidak ditemukan"); return
        path = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"rapor_{nis}.pdf")
        if not path: return
        nilar = db.q("SELECT m.nama AS mapel, n.nilai, n.semester FROM nilai n JOIN mapel m ON n.mapel_id=m.id WHERE siswa_id=?", (s["id"],))
        absen = db.absensi_by_date(self._today())
        pdf_generator.generate_rapor_pdf(path, s, nilar, absen)
        messagebox.showinfo("Sukses", f"Export ke {path}")
