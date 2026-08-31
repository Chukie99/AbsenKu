"""
ui/ranking.py — Dedicated ranking screen with two modes:
  1. Ranking by Nilai Akademik (avg of all mapel)
  2. Ranking by Poin Disiplin (net positive - negative)
Supports filters per-kelas, semester, and tahun_ajaran. Includes
visual podium (top 3 medals), sortable table, and export to PDF/Excel.
"""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(here))

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import pdf_generator, excel_exporter
from datetime import datetime

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
    "input_bg": "#F1F5F9",
}

MEDALS = [
    {"icon": "🥇", "color": C["gold"], "bg": C["gold_bg"], "label": "Juara 1"},
    {"icon": "🥈", "color": C["silver"], "bg": C["silver_bg"], "label": "Juara 2"},
    {"icon": "🥉", "color": C["bronze"], "bg": C["bronze_bg"], "label": "Juara 3"},
]


class Ranking:
    def __init__(self, parent):
        self.parent = parent
        self._mode = "nilai"  # "nilai" or "poin"
        self.tree = None
        self._ranking_data = []
        self._top_podium = None

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # ── Header ──
        hdr = tk.Frame(p, bg=C["bg"])
        hdr.pack(fill="x", padx=20, pady=(16, 0))
        tk.Label(hdr, text="🏆  Ranking Siswa", font=("Segoe UI", 18, "bold"),
                fg=C["text"], bg=C["bg"]).pack(anchor="w")
        tk.Label(hdr, text="Lihat peringkat siswa berdasarkan akademik atau poin disiplin",
                font=("Segoe UI", 10), fg=C["text2"], bg=C["bg"]).pack(anchor="w")

        # ── Mode tabs (Nilai | Poin) ──
        mode_frame = tk.Frame(p, bg=C["bg"])
        mode_frame.pack(fill="x", padx=20, pady=(12, 0))
        self._mode_btns = {}
        for key, label in [("nilai", "📚 Ranking Nilai Akademik"),
                          ("poin", "🎯 Ranking Poin Disiplin")]:
            btn = tk.Button(mode_frame, text=label,
                          font=("Segoe UI", 10, "bold"),
                          relief="flat", padx=16, pady=6, cursor="hand2",
                          command=lambda k=key: self._switch_mode(k))
            btn.pack(side="left", padx=(0, 4))
            self._mode_btns[key] = btn

        # Separator
        sep = tk.Frame(p, bg=C["border"], height=1)
        sep.pack(fill="x", padx=20, pady=(8, 0))

        # ── Filter bar ──
        filter_bar = tk.Frame(p, bg=C["card"], relief="flat",
                             highlightbackground=C["border"], highlightthickness=1)
        filter_bar.pack(fill="x", padx=20, pady=(8, 8))

        filt_inner = tk.Frame(filter_bar, bg=C["card"])
        filt_inner.pack(fill="x", padx=16, pady=10)

        # Kelas filter
        tk.Label(filt_inner, text="Kelas:", font=("Segoe UI", 10),
                fg=C["text"], bg=C["card"]).pack(side="left", padx=(0, 4))
        self.kelas_list = db.kelas_all()
        self.kelas_options = ["Semua"] + [k["nama"] for k in self.kelas_list]
        self.kelas_var = tk.StringVar(value="Semua")
        ttk.Combobox(filt_inner, textvariable=self.kelas_var,
                    values=self.kelas_options, state="readonly",
                    width=16, font=("Segoe UI", 10)).pack(side="left", padx=(0, 16))
        self.kelas_var.trace_add("write", lambda *a: self._load())

        # Mode-specific filters
        self.semester_lbl = tk.Label(filt_inner, text="Semester:",
                                    font=("Segoe UI", 10),
                                    fg=C["text"], bg=C["card"])
        self.semester_var = tk.StringVar(value="Semua")
        self.semester_cb = ttk.Combobox(filt_inner, textvariable=self.semester_var,
                                       values=["Semua", "1", "2"],
                                       state="readonly", width=8,
                                       font=("Segoe UI", 10))
        self.semester_var.trace_add("write", lambda *a: self._load())

        self.ta_lbl = tk.Label(filt_inner, text="T.A.:",
                              font=("Segoe UI", 10),
                              fg=C["text"], bg=C["card"])
        self.ta_var = tk.StringVar(value="Semua")
        # Get distinct tahun_ajaran from DB
        ta_rows = db.q("SELECT DISTINCT tahun_ajaran FROM nilai WHERE tahun_ajaran IS NOT NULL AND tahun_ajaran != '' ORDER BY tahun_ajaran DESC")
        ta_options = ["Semua"] + [r["tahun_ajaran"] for r in ta_rows]
        self.ta_cb = ttk.Combobox(filt_inner, textvariable=self.ta_var,
                                 values=ta_options, state="readonly", width=12,
                                 font=("Segoe UI", 10))
        self.ta_var.trace_add("write", lambda *a: self._load())

        # Refresh button
        tk.Button(filt_inner, text="🔄 Refresh", font=("Segoe UI", 10, "bold"),
                 bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=4, command=self._load).pack(side="right", padx=4)

        # Export button
        tk.Button(filt_inner, text="📊 Export Excel", font=("Segoe UI", 10),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=10, pady=4, command=self._export_excel).pack(side="right", padx=4)
        tk.Button(filt_inner, text="📄 Export PDF", font=("Segoe UI", 10),
                 bg=C["red"], fg="white", relief="flat", cursor="hand2",
                 padx=10, pady=4, command=self._export_pdf).pack(side="right", padx=4)

        # ── Content area ──
        self._content = tk.Frame(p, bg=C["bg"])
        self._content.pack(fill="both", expand=True, padx=20, pady=(0, 16))

        self._switch_mode(self._mode)

    def _switch_mode(self, mode):
        self._mode = mode
        for key, btn in self._mode_btns.items():
            if key == mode:
                btn.config(bg=C["tab_active"], fg="white",
                          activebackground=C["tab_active"])
            else:
                btn.config(bg=C["tab_inactive"], fg=C["text"],
                          activebackground=C["tab_inactive"])

        # Show/hide mode-specific filters
        if mode == "nilai":
            self.semester_lbl.pack(side="left", padx=(16, 4))
            self.semester_cb.pack(side="left", padx=(0, 8))
            self.ta_lbl.pack(side="left", padx=(8, 4))
            self.ta_cb.pack(side="left", padx=(0, 8))
        else:
            self.semester_lbl.pack_forget()
            self.semester_cb.pack_forget()
            self.ta_lbl.pack_forget()
            self.ta_cb.pack_forget()

        self._load()

    def _load(self):
        for c in self._content.winfo_children():
            c.destroy()

        # Build filter args
        kelas_name = self.kelas_var.get()
        kelas_id = None
        if kelas_name != "Semua":
            for k in self.kelas_list:
                if k["nama"] == kelas_name:
                    kelas_id = k["id"]
                    break

        if self._mode == "nilai":
            semester = self.semester_var.get()
            ta = self.ta_var.get()
            semester_arg = None if semester == "Semua" else semester
            ta_arg = None if ta == "Semua" else ta
            rows = db.ranking_nilai_by_kelas(
                kelas_id=kelas_id, semester=semester_arg, ta=ta_arg)
            data = [{
                "id": r["id"],
                "nis": r["nis"],
                "nama": r["nama"],
                "kelas": r["kelas_nama"],
                "score": r["rata_rata"] or 0,
                "extra": r["jumlah_mapel"],
            } for r in rows]
            score_label = "Rata-rata"
            score_format = lambda v: f"{v:.1f}"
            higher_better = True
        else:
            rows = db.poin_summary_by_kelas(kelas_id=kelas_id)
            data = [{
                "id": r["id"],
                "nis": r["nis"],
                "nama": r["nama"],
                "kelas": r["kelas_nama"],
                "score": r["poin_net"] or 0,
                "extra": f"+{r['poin_positif']} / -{r['poin_negatif']}",
            } for r in rows]
            score_label = "Poin Bersih"
            score_format = lambda v: f"{v:+d}"
            higher_better = True

        if not data:
            empty = tk.Frame(self._content, bg=C["card"], relief="flat",
                           highlightbackground=C["border"], highlightthickness=1)
            empty.pack(fill="both", expand=True)
            tk.Label(empty, text="📊 Belum ada data ranking",
                    font=("Segoe UI", 14), fg=C["text2"], bg=C["card"]).pack(expand=True, pady=(60, 4))
            tk.Label(empty, text="Tambahkan data nilai/poin disiplin terlebih dahulu",
                    font=("Segoe UI", 10), fg=C["text2"], bg=C["card"]).pack(pady=(0, 60))
            self._ranking_data = []
            return

        # Sort descending
        data.sort(key=lambda x: x["score"], reverse=True)
        self._ranking_data = data

        # ── Stats summary ──
        summary_frame = tk.Frame(self._content, bg=C["bg"])
        summary_frame.pack(fill="x", pady=(0, 8))

        total = len(data)
        avg_score = sum(d["score"] for d in data) / total if total else 0
        top1 = data[0] if data else None
        top3_avg = sum(d["score"] for d in data[:3]) / 3 if len(data) >= 3 else 0

        stats = [
            (f"Total Siswa", str(total), C["accent"], C["accent_light"]),
            (f"Rata-rata {score_label}", score_format(avg_score), C["green"], C["green_light"]),
            ("Peringkat 1", top1["nama"] if top1 else "-", C["gold"], C["gold_bg"]),
            ("Rata-rata Top 3", score_format(top3_avg), C["orange"], C["orange_light"]),
        ]
        for label, val, color, bg_c in stats:
            card = tk.Frame(summary_frame, bg=bg_c, relief="flat",
                          highlightbackground=C["border"], highlightthickness=1)
            card.pack(side="left", padx=4, expand=True, fill="x")
            tk.Label(card, text=val, font=("Segoe UI", 12, "bold"),
                    fg=color, bg=bg_c, wraplength=140).pack(pady=(8, 0))
            tk.Label(card, text=label, font=("Segoe UI", 9),
                    fg=C["text2"], bg=bg_c).pack(pady=(0, 8))

        # ── Top 3 podium ──
        if len(data) >= 3:
            self._build_podium(data[:3], score_label, score_format)

        # ── Full ranking table ──
        self._build_table(data, score_label, score_format, higher_better)

    def _build_podium(self, top3, score_label, score_format):
        """Build top-3 podium display."""
        podium_frame = tk.Frame(self._content, bg=C["bg"])
        podium_frame.pack(fill="x", pady=(0, 8))

        # Reorder to: 2nd, 1st, 3rd (visual layout)
        order = [(1, top3[1]), (0, top3[0]), (2, top3[2])]
        podiums = []
        for idx, (orig_idx, student) in enumerate(order):
            medal = MEDALS[orig_idx]
            pod = tk.Frame(podium_frame, bg=medal["bg"], relief="flat",
                         highlightbackground=C["border"], highlightthickness=1)
            pod.pack(side="left", expand=True, fill="both", padx=4)
            podiums.append(pod)

            inner = tk.Frame(pod, bg=medal["bg"])
            inner.pack(fill="both", expand=True, padx=12, pady=12)

            # Trophy icon
            tk.Label(inner, text=medal["icon"], font=("Segoe UI", 36),
                    bg=medal["bg"]).pack()
            # Rank
            tk.Label(inner, text=medal["label"], font=("Segoe UI", 11, "bold"),
                    fg=medal["color"], bg=medal["bg"]).pack()
            # Name
            name = student["nama"]
            if len(name) > 20:
                name = name[:18] + "…"
            tk.Label(inner, text=name, font=("Segoe UI", 12, "bold"),
                    fg=C["text"], bg=medal["bg"], wraplength=160).pack(pady=2)
            # Class
            tk.Label(inner, text=student["kelas"] or "-",
                    font=("Segoe UI", 9), fg=C["text2"], bg=medal["bg"]).pack()
            # Score
            tk.Label(inner, text=score_format(student["score"]),
                    font=("Segoe UI", 22, "bold"),
                    fg=medal["color"], bg=medal["bg"]).pack(pady=(4, 0))
            # Extra info
            tk.Label(inner, text=f"{score_label}: {score_format(student['score'])}",
                    font=("Segoe UI", 8), fg=C["text2"], bg=medal["bg"]).pack()

        # Make podium #1 taller (visual hierarchy)
        podiums[1].configure(height=10)  # doesn't strictly work in tk, but helps

    def _build_table(self, data, score_label, score_format, higher_better):
        """Build the full ranking table."""
        list_frame = tk.Frame(self._content, bg=C["card"], relief="flat",
                             highlightbackground=C["border"], highlightthickness=1)
        list_frame.pack(fill="both", expand=True)

        tk.Label(list_frame, text=f"📋 Tabel Ranking Lengkap ({len(data)} siswa)",
                font=("Segoe UI", 12, "bold"), fg=C["text"],
                bg=C["card"]).pack(anchor="w", padx=16, pady=(12, 4))

        # Scrollable
        table_container = tk.Frame(list_frame, bg=C["card"])
        table_container.pack(fill="both", expand=True, padx=16, pady=(0, 12))

        cols = ("rank", "medal", "nama", "nis", "kelas", "score", "extra")
        self.tree = ttk.Treeview(table_container, columns=cols,
                                show="headings", height=14)
        headers = [
            ("rank", "Rank", 60),
            ("medal", "", 50),
            ("nama", "Nama Siswa", 240),
            ("nis", "NIS", 100),
            ("kelas", "Kelas", 130),
            ("score", score_label, 110),
            ("extra", "Info" if self._mode == "nilai" else "Pos / Neg", 130),
        ]
        for col, txt, w in headers:
            self.tree.heading(col, text=txt, anchor="w")
            self.tree.column(col, width=w, minwidth=60, anchor="w")

        sb = ttk.Scrollbar(table_container, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=sb.set)
        self.tree.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

        # Color tags
        for i, medal in enumerate(MEDALS):
            self.tree.tag_configure(f"medal_{i}", background=medal["bg"])

        for i, d in enumerate(data):
            rank = i + 1
            medal_text = MEDALS[i]["icon"] if i < 3 else ""
            tag = f"medal_{i}" if i < 3 else ("alt" if rank % 2 == 0 else "")
            if rank % 2 == 0 and i >= 3:
                self.tree.tag_configure("alt", background="#FAFBFC")
            self.tree.insert("", "end", values=(
                rank, medal_text, d["nama"], d["nis"], d["kelas"] or "-",
                score_format(d["score"]), d["extra"] or "-",
            ), tags=(tag,) if tag else ())

        # Bind double-click to view student detail
        self.tree.bind("<Double-1>", self._on_row_double)

    def _on_row_double(self, event):
        sel = self.tree.selection()
        if not sel:
            return
        item = self.tree.item(sel[0])
        # Find the matching siswa_id from the values (3rd col is nama, 4th is nis)
        nis = item["values"][3]
        rows = db.q("SELECT id FROM siswa WHERE nis=?", (nis,))
        if not rows:
            return
        siswa_id = rows[0]["id"]
        try:
            mod = __import__("ui.siswa_detail", fromlist=["Siswa_detail"])
            cls = getattr(mod, "Siswa_detail")
            for c in self.parent.winfo_children():
                c.destroy()
            cls(self.parent, siswa_id=siswa_id).build()
        except Exception as e:
            messagebox.showerror("Error", f"Gagal buka detail: {e}")

    def _export_excel(self):
        if not self._ranking_data:
            messagebox.showwarning("Info", "Tidak ada data untuk di-export")
            return
        path = filedialog.asksaveasfilename(
            defaultextension=".xlsx",
            initialfile=f"ranking_{self._mode}.xlsx")
        if not path:
            return
        try:
            wb = __import__("openpyxl").Workbook()
            ws = wb.active
            ws.title = "Ranking"
            score_label = "Rata-rata" if self._mode == "nilai" else "Poin Bersih"
            headers = ["Rank", "NIS", "Nama", "Kelas", score_label, "Info"]
            for c, h in enumerate(headers, 1):
                cell = ws.cell(1, c, h)
                cell.font = __import__("openpyxl").styles.Font(
                    bold=True, color="FFFFFF")
                cell.fill = __import__("openpyxl").styles.PatternFill(
                    "solid", fgColor="1A73E8")

            for i, d in enumerate(self._ranking_data, 1):
                r = i + 1
                ws.cell(r, 1, i)
                ws.cell(r, 2, d["nis"])
                ws.cell(r, 3, d["nama"])
                ws.cell(r, 4, d["kelas"] or "-")
                if self._mode == "nilai":
                    ws.cell(r, 5, round(d["score"], 1))
                else:
                    ws.cell(r, 5, d["score"])
                ws.cell(r, 6, str(d["extra"] or "-"))

            for col in ws.columns:
                ws.column_dimensions[col[0].column_letter].width = 18
            wb.save(path)
            messagebox.showinfo("Sukses", f"Export ranking ke:\n{path}")
        except Exception as e:
            messagebox.showerror("Error", f"Gagal export:\n{e}")

    def _export_pdf(self):
        if not self._ranking_data:
            messagebox.showwarning("Info", "Tidak ada data untuk di-export")
            return
        path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            initialfile=f"ranking_{self._mode}.pdf")
        if not path:
            return
        try:
            score_label = "Rata-rata" if self._mode == "nilai" else "Poin Bersih"
            headers = ["Rank", "NIS", "Nama", "Kelas", score_label, "Info"]
            data = []
            for i, d in enumerate(self._ranking_data, 1):
                row = {
                    "rank": str(i),
                    "nis": d["nis"],
                    "nama": d["nama"],
                    "kelas": d["kelas"] or "-",
                    "skor": f"{d['score']:.1f}" if self._mode == "nilai" else f"{d['score']:+d}",
                    "info": str(d["extra"] or "-"),
                }
                data.append(row)
            title = "Ranking Nilai Akademik" if self._mode == "nilai" else "Ranking Poin Disiplin"
            pdf_generator.generate_store_report_pdf(
                path, "", data, headers,
                f"{title} - AbsenKu")
            messagebox.showinfo("Sukses", f"Export ranking ke:\n{path}")
        except Exception as e:
            messagebox.showerror("Error", f"Gagal export:\n{e}")
