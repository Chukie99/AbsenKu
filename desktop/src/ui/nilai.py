"""
ui/nilai.py — input nilai per siswa per mapel, rekap rata-rata, audit_log on edit, export, Grafik.
"""
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import excel_exporter


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
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "tab_active": "#2563EB",
    "tab_inactive": "#E2E8F0",
}

# Bar chart colors (cycling)
CHART_COLORS = [
    "#2563EB",  # blue
    "#16A34A",  # green
    "#EA580C",  # orange
    "#9333EA",  # purple
    "#DC2626",  # red
    "#0891B2",  # cyan
    "#D97706",  # amber
    "#4F46E5",  # indigo
    "#059669",  # emerald
    "#DB2777",  # pink
]


class Nilai:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None
        self._tab = "list"  # current active tab

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # ── Header ──
        hdr = tk.Frame(p, bg=C["bg"])
        hdr.pack(fill="x", padx=20, pady=(16, 0))
        tk.Label(hdr, text="Manajemen Nilai", font=("Segoe UI", 18, "bold"),
                 fg=C["text"], bg=C["bg"]).pack(anchor="w")

        # ── Tab switcher ──
        tab_frame = tk.Frame(p, bg=C["bg"])
        tab_frame.pack(fill="x", padx=20, pady=(12, 0))

        self._tab_btns = {}
        for key, label in [("list", "📋  Daftar Nilai"), ("grafik", "📊  Grafik")]:
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

        self._switch_tab("list")

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

        if tab == "list":
            self._build_list_tab()
        elif tab == "grafik":
            self._build_grafik_tab()

    # ── List Tab (original functionality) ──
    def _build_list_tab(self):
        p = self._content

        # Action buttons
        tf = tk.Frame(p, bg=C["bg"])
        tf.pack(fill="x", pady=(0, 8))

        tk.Button(tf, text="➕  Input Nilai", bg="#1A73E8", fg="white",
                 command=self.input_nilai, relief="flat", padx=12, pady=6,
                 font=("Segoe UI", 10), cursor="hand2").pack(side="left")
        tk.Button(tf, text="📥  Export Excel", bg="#34A853", fg="white",
                 command=self.export_excel, relief="flat", padx=12, pady=6,
                 font=("Segoe UI", 10), cursor="hand2").pack(side="left", padx=6)

        # Treeview card
        card = tk.Frame(p, bg=C["card"], relief="flat",
                       highlightbackground=C["border"], highlightthickness=1)
        card.pack(fill="both", expand=True)

        self.tree = ttk.Treeview(card, columns=("siswa","mapel","nilai","sem","ta"),
                                show="headings", height=18)
        for col, txt, w in [("siswa","Siswa",200),("mapel","Mapel",160),
                             ("nilai","Nilai",80),("sem","Semester",80),("ta","T.A.",100)]:
            self.tree.heading(col, text=txt)
            self.tree.column(col, width=w)

        scroll = ttk.Scrollbar(card, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll.set)
        self.tree.pack(side="left", fill="both", expand=True, padx=(8,0), pady=8)
        scroll.pack(side="right", fill="y", pady=8, padx=(0,8))

        self.load()

    def load(self):
        if not self.tree:
            return
        for i in self.tree.get_children():
            self.tree.delete(i)
        sw = {s["id"]: s for s in db.siswa_all()}
        mw = {m["id"]: m for m in db.mapel_all()}
        for n in db.q("SELECT * FROM nilai ORDER BY created_at DESC"):
            self.tree.insert("", "end", iid=n["id"], values=(
                sw.get(n["siswa_id"],{}).get("nama","-"),
                mw.get(n["mapel_id"],{}).get("nama","-"),
                n["nilai"], n["semester"], n["tahun_ajaran"]))

    # ── Grafik Tab ──
    def _build_grafik_tab(self):
        p = self._content

        # Fetch chart data
        chart_data = self._compute_chart_data()

        if not chart_data:
            empty_card = tk.Frame(p, bg=C["card"], relief="flat",
                                 highlightbackground=C["border"], highlightthickness=1)
            empty_card.pack(fill="both", expand=True, pady=4)
            tk.Label(empty_card, text="📊 Belum ada data nilai untuk grafik",
                    font=("Segoe UI", 14), fg=C["text2"], bg=C["card"]).pack(expand=True)
            tk.Label(empty_card, text="Silakan input nilai terlebih dahulu.",
                    font=("Segoe UI", 10), fg=C["text2"], bg=C["card"]).pack(pady=(4, 20))
            return

        # ── Chart card ──
        chart_card = tk.Frame(p, bg=C["card"], relief="flat",
                             highlightbackground=C["border"], highlightthickness=1)
        chart_card.pack(fill="both", expand=True, pady=(0, 8))

        tk.Label(chart_card, text="📊 Rata-rata Nilai per Mata Pelajaran",
                font=("Segoe UI", 13, "bold"), fg=C["text"], bg=C["card"]).pack(
                    anchor="w", padx=16, pady=(12, 4))
        tk.Label(chart_card, text="Tinggi bar menunjukkan rata-rata nilai (skala 0-100)",
                font=("Segoe UI", 9), fg=C["text2"], bg=C["card"]).pack(
                    anchor="w", padx=16, pady=(0, 4))

        canvas = tk.Canvas(chart_card, bg=C["card"], highlightthickness=0)
        canvas.pack(fill="both", expand=True, padx=16, pady=(0, 8))

        def draw_bars(event=None):
            canvas.delete("all")
            w = canvas.winfo_width()
            if w < 150:
                return

            n_bars = len(chart_data)
            max_val = 100  # fixed scale 0-100
            chart_h = canvas.winfo_height() - 60  # leave room for labels

            bar_gap = 12
            total_gap = bar_gap * (n_bars + 1)
            bar_w = max((w - total_gap) / n_bars, 20)

            # Draw Y-axis guide lines
            for guide_val in [20, 40, 60, 80, 100]:
                y = chart_h - int((guide_val / max_val) * chart_h) + 10
                canvas.create_line(30, y, w - 10, y, fill=C["border"],
                                 dash=(3, 3), width=1)
                canvas.create_text(12, y, text=str(guide_val), anchor="e",
                                 font=("Segoe UI", 7), fill=C["text2"])

            # Draw bars
            for idx, item in enumerate(chart_data):
                avg = item["avg"]
                color = CHART_COLORS[idx % len(CHART_COLORS)]
                x = bar_gap + 20 + idx * (bar_w + bar_gap)  # +20 for y-axis labels

                # Clamp to canvas width
                if x + bar_w > w - 10:
                    break

                bh = int((avg / max_val) * chart_h) if max_val else 0

                # Bar (with rounded look via slightly inset rectangle)
                canvas.create_rectangle(x, chart_h - bh + 10, x + bar_w, chart_h + 10,
                                       fill=color, outline="", width=0)

                # Value on top of bar
                val_text = f"{avg:.1f}"
                canvas.create_text(x + bar_w / 2, chart_h - bh + 2,
                                  text=val_text, font=("Segoe UI", 9, "bold"),
                                  fill=C["text"])

                # Mapel name below (truncated if too long)
                name = item["mapel"]
                max_chars = max(int(bar_w / 6), 4)
                if len(name) > max_chars:
                    name = name[:max_chars - 1] + "…"
                canvas.create_text(x + bar_w / 2, chart_h + 24,
                                  text=name, font=("Segoe UI", 8),
                                  fill=C["text2"], anchor="n",
                                  width=bar_w)

                # Jumlah siswa count
                canvas.create_text(x + bar_w / 2, chart_h + 42,
                                  text=f"({item['count']} siswa)", font=("Segoe UI", 7),
                                  fill=C["text2"], anchor="n")

        canvas.bind("<Configure>", draw_bars)

        # ── Summary stats below chart ──
        summary_frame = tk.Frame(p, bg=C["bg"])
        summary_frame.pack(fill="x", pady=(0, 4))

        overall_avg = sum(d["avg"] * d["count"] for d in chart_data) / sum(d["count"] for d in chart_data) if chart_data else 0
        best = chart_data[0] if chart_data else None
        worst = chart_data[-1] if chart_data else None

        for label, val, color, bg_c in [
            ("Rata-rata Umum", f"{overall_avg:.1f}", C["accent"], C["accent_light"]),
            ("Mapel Tertinggi", f"{best['mapel']} ({best['avg']:.1f})" if best else "-", C["green"], C["green_light"]),
            ("Mapel Terendah", f"{worst['mapel']} ({worst['avg']:.1f})" if worst else "-", C["red"], C["red_light"]),
            ("Total Mapel", str(len(chart_data)), C["accent"], C["accent_light"]),
        ]:
            card = tk.Frame(summary_frame, bg=bg_c, relief="flat",
                           highlightbackground=C["border"], highlightthickness=1)
            card.pack(side="left", padx=4, expand=True, fill="x")
            tk.Label(card, text=val, font=("Segoe UI", 11, "bold"),
                     fg=color, bg=bg_c).pack(pady=(8, 0))
            tk.Label(card, text=label, font=("Segoe UI", 8),
                     fg=C["text2"], bg=bg_c).pack(pady=(0, 8))

    def _compute_chart_data(self):
        """Compute average nilai per mapel, sorted by average descending."""
        rows = db.q("""
            SELECT m.nama AS mapel, m.id AS mapel_id,
                   AVG(n.nilai) AS avg_nilai, COUNT(n.nilai) AS cnt
            FROM mapel m
            LEFT JOIN nilai n ON m.id = n.mapel_id
            WHERE n.id IS NOT NULL
            GROUP BY m.id, m.nama
            HAVING cnt > 0
            ORDER BY avg_nilai DESC
        """)
        return [{
            "mapel": r["mapel"],
            "mapel_id": r["mapel_id"],
            "avg": r["avg_nilai"],
            "count": r["cnt"],
        } for r in rows]

    # ── Original methods (unchanged) ──
    def input_nilai(self):
        top = tk.Toplevel(self.parent)
        top.title("Input Nilai")
        top.geometry("360x280")
        frm = tk.Frame(top, padx=16, pady=16)
        frm.pack(fill="both", expand=True)

        tk.Label(frm, text="Siswa ID").pack(anchor="w")
        sid = tk.Entry(frm, width=36)
        sid.pack(fill="x", pady=2)

        tk.Label(frm, text="Mapel ID").pack(anchor="w", pady=(8, 0))
        mid = tk.Entry(frm, width=36)
        mid.pack(fill="x", pady=2)

        tk.Label(frm, text="Nilai").pack(anchor="w", pady=(8, 0))
        nl = tk.Entry(frm, width=36)
        nl.pack(fill="x", pady=2)

        tk.Label(frm, text="Semester").pack(anchor="w", pady=(8, 0))
        sm = tk.Entry(frm, width=36)
        sm.pack(fill="x", pady=2)
        sm.insert(0, "1")

        tk.Label(frm, text="Tahun Ajaran").pack(anchor="w", pady=(8, 0))
        ta = tk.Entry(frm, width=36)
        ta.pack(fill="x", pady=2)
        ta.insert(0, "2025/2026")

        def save():
            try:
                s_id = int(sid.get())
                m_id = int(mid.get())
            except ValueError:
                messagebox.showwarning("Validasi", "ID harus angka")
                return
            db.nilai_insert(s_id, m_id, nl.get(), sm.get(), ta.get())
            self.load()
            # Also refresh grafik if visible
            if self._tab == "grafik":
                self._switch_tab("grafik")
            top.destroy()

        tk.Button(frm, text="Simpan", bg="#1A73E8", fg="white", command=save,
                 relief="flat", padx=12, pady=4).pack(pady=14)

    def export_excel(self):
        rows = db.q("SELECT s.nama,s.nis,m.nama AS mapel_nama,n.* FROM nilai n JOIN siswa s ON n.siswa_id=s.id JOIN mapel m ON n.mapel_id=m.id ORDER BY s.nama, m.nama")
        path = filedialog.asksaveasfilename(defaultextension=".xlsx", initialfile="nilai.xlsx")
        if not path:
            return
        excel_exporter.export_nilai_xlsx(path, rows)
        messagebox.showinfo("Sukses", f"Export ke {path}")
