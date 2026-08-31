"""
ui/siswa_detail.py — Comprehensive student detail view.

Shows full profile: biodata, attendance summary, nilai per mapel, poin disiplin,
quick actions (print rapor, edit, view QR). Linked from siswa list via double-click.
"""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(here))

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import pdf_generator, barcode_generator
from datetime import datetime

# ── Color palette (consistent with other screens) ──
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
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "input_bg": "#F1F5F9",
}


def center_window(win, w, h):
    """Center a Toplevel window on its parent."""
    win.update_idletasks()
    px = win.master.winfo_rootx() if win.master else 0
    py = win.master.winfo_rooty() if win.master else 0
    pw = win.master.winfo_width() if win.master else win.winfo_screenwidth()
    ph = win.master.winfo_height() if win.master else win.winfo_screenheight()
    x = px + (pw - w) // 2
    y = py + (ph - h) // 2
    win.geometry(f"{w}x{h}+{x}+{y}")


class Siswa_detail:
    """Detailed student profile view. Constructor accepts an optional siswa_id;
    if omitted, shows a search/select dialog to choose a student.
    """

    def __init__(self, parent, siswa_id=None):
        self.parent = parent
        self.siswa_id = siswa_id
        self.tree_att = None
        self.tree_nil = None
        self.tree_poin = None
        self._photo_img = None  # prevent GC

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # If no siswa_id, prompt user to pick one
        if not self.siswa_id:
            self._build_picker()
            return

        record = db.siswa_get(self.siswa_id)
        if not record:
            tk.Label(p, text="❌ Siswa tidak ditemukan",
                    font=("Segoe UI", 14, "bold"),
                    fg=C["red"], bg=C["bg"]).pack(pady=40)
            return

        # ── Header bar ──
        hdr = tk.Frame(p, bg=C["accent"], height=70)
        hdr.pack(fill="x")
        hdr.pack_propagate(False)

        hdr_inner = tk.Frame(hdr, bg=C["accent"])
        hdr_inner.pack(fill="both", expand=True, padx=20, pady=10)

        tk.Label(hdr_inner, text="👤  Detail Siswa",
                font=("Segoe UI", 16, "bold"), fg="white",
                bg=C["accent"]).pack(side="left")

        btn_hdr = tk.Frame(hdr_inner, bg=C["accent"])
        btn_hdr.pack(side="right")
        tk.Button(btn_hdr, text="← Kembali", font=("Segoe UI", 9, "bold"),
                 bg="white", fg=C["accent"], relief="flat", cursor="hand2",
                 padx=10, pady=4, command=self._back_to_list).pack(side="left", padx=2)
        tk.Button(btn_hdr, text="🖨️ Cetak Rapor", font=("Segoe UI", 9, "bold"),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=10, pady=4,
                 command=lambda: self._cetak_rapor(record)).pack(side="left", padx=2)
        tk.Button(btn_hdr, text="🏷️ Cetak Name Tag", font=("Segoe UI", 9, "bold"),
                 bg=C["orange"], fg="white", relief="flat", cursor="hand2",
                 padx=10, pady=4,
                 command=lambda: self._cetak_name_tag(record)).pack(side="left", padx=2)

        # ── Scrollable body ──
        body_outer = tk.Frame(p, bg=C["bg"])
        body_outer.pack(fill="both", expand=True)

        canvas = tk.Canvas(body_outer, bg=C["bg"], highlightthickness=0)
        scroll = ttk.Scrollbar(body_outer, orient="vertical", command=canvas.yview)
        body = tk.Frame(canvas, bg=C["bg"])

        body.bind("<Configure>",
                 lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0, 0), window=body, anchor="nw")
        canvas.configure(yscrollcommand=scroll.set)

        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        # ── Row 1: Profile + Photo ──
        row1 = tk.Frame(body, bg=C["bg"])
        row1.pack(fill="x", padx=20, pady=(16, 8))

        # Profile card (left)
        prof_card = tk.Frame(row1, bg=C["card"], relief="flat",
                            highlightbackground=C["border"], highlightthickness=1)
        prof_card.pack(side="left", fill="both", expand=True, padx=(0, 8))

        prof_inner = tk.Frame(prof_card, bg=C["card"])
        prof_inner.pack(fill="both", expand=True, padx=20, pady=16)

        tk.Label(prof_inner, text="📋 Biodata Siswa",
                font=("Segoe UI", 13, "bold"), fg=C["text"],
                bg=C["card"]).pack(anchor="w", pady=(0, 12))

        # Kelas name lookup
        kelas_nama = "-"
        if record.get("kelas_id"):
            krows = db.q("SELECT nama FROM kelas WHERE id=?", (record["kelas_id"],))
            if krows:
                kelas_nama = krows[0]["nama"]

        biodata = [
            ("NIS", record.get("nis", "-")),
            ("Nama Lengkap", record.get("nama", "-")),
            ("Kelas", kelas_nama),
            ("Tempat, Tgl Lahir", record.get("tanggal_lahir") or "-"),
            ("Alamat", record.get("alamat") or "-"),
            ("No HP Orang Tua", record.get("no_hp_ortu") or "-"),
            ("Status", "Aktif" if record.get("is_active") else "Non-aktif"),
        ]

        for label, val in biodata:
            row = tk.Frame(prof_inner, bg=C["card"])
            row.pack(fill="x", pady=2)
            tk.Label(row, text=label, font=("Segoe UI", 10),
                    fg=C["text2"], bg=C["card"], width=18, anchor="w").pack(side="left")
            tk.Label(row, text=str(val), font=("Segoe UI", 10, "bold"),
                    fg=C["text"], bg=C["card"], anchor="w", wraplength=380,
                    justify="left").pack(side="left", fill="x", expand=True)

        # Photo / QR card (right)
        photo_card = tk.Frame(row1, bg=C["card"], relief="flat",
                             highlightbackground=C["border"], highlightthickness=1,
                             width=240)
        photo_card.pack(side="left", fill="y", padx=(8, 0))
        photo_card.pack_propagate(False)

        photo_inner = tk.Frame(photo_card, bg=C["card"])
        photo_inner.pack(fill="both", expand=True, padx=16, pady=16)

        tk.Label(photo_inner, text="📷 Foto / QR",
                font=("Segoe UI", 11, "bold"), fg=C["text"],
                bg=C["card"]).pack(anchor="w", pady=(0, 8))

        # Try to load photo
        photo_lbl = tk.Label(photo_inner, bg=C["input_bg"], text="📷\nTidak ada\nfoto",
                            font=("Segoe UI", 11), fg=C["text2"],
                            width=20, height=8, relief="groove")
        photo_lbl.pack(pady=(0, 8), fill="x")

        foto_path = record.get("foto")
        if foto_path and os.path.exists(foto_path):
            try:
                from PIL import Image, ImageTk
                img = Image.open(foto_path)
                img.thumbnail((200, 200))
                self._photo_img = ImageTk.PhotoImage(img)
                photo_lbl.config(image=self._photo_img, text="", width=200, height=200)
            except Exception:
                pass

        # QR code
        qr_lbl = tk.Label(photo_inner, bg=C["input_bg"], text="QR\nbelum\ndibuat",
                         font=("Segoe UI", 9), fg=C["text2"],
                         width=20, height=6, relief="groove")
        qr_lbl.pack(pady=(0, 8), fill="x")

        qr_path = record.get("qr_code")
        if qr_path and os.path.exists(qr_path):
            try:
                from PIL import Image, ImageTk
                img = Image.open(qr_path)
                img = img.resize((150, 150), Image.LANCZOS)
                self._qr_img = ImageTk.PhotoImage(img)
                qr_lbl.config(image=self._qr_img, text="", width=150, height=150)
            except Exception:
                pass

        tk.Button(photo_inner, text="🔄 Generate QR",
                 font=("Segoe UI", 9, "bold"), bg=C["accent"], fg="white",
                 relief="flat", cursor="hand2", padx=8, pady=4,
                 command=lambda: self._generate_qr(record, qr_lbl)).pack(fill="x")

        # ── Row 2: Stats summary cards ──
        stats_row = tk.Frame(body, bg=C["bg"])
        stats_row.pack(fill="x", padx=20, pady=(0, 8))

        # Compute stats
        absen_rows = db.absensi_by_siswa(self.siswa_id)
        total_absen = len(absen_rows)
        count_hadir = sum(1 for a in absen_rows if a.get("status") == "Hadir")
        count_izin = sum(1 for a in absen_rows if a.get("status") == "Izin")
        count_sakit = sum(1 for a in absen_rows if a.get("status") == "Sakit")
        count_alfa = sum(1 for a in absen_rows if a.get("status") == "Alfa")
        persen_hadir = (count_hadir / total_absen * 100) if total_absen else 0

        # Nilai stats
        nilai_rows = db.nilai_by_siswa(self.siswa_id)
        if nilai_rows:
            all_n = [n["nilai"] for n in nilai_rows if n.get("nilai") is not None]
            avg_nilai = sum(all_n) / len(all_n) if all_n else 0
            max_nilai = max(all_n) if all_n else 0
            min_nilai = min(all_n) if all_n else 0
        else:
            avg_nilai = max_nilai = min_nilai = 0

        # Poin stats
        poin_rows = db.poin_by_siswa(self.siswa_id)
        poin_pos = sum(p["poin"] for p in poin_rows if p.get("kategori") == "Positif")
        poin_neg = sum(p["poin"] for p in poin_rows if p.get("kategori") == "Negatif")
        poin_net = poin_pos - poin_neg

        for label, val, color, bg_c in [
            ("Total Absensi", str(total_absen), C["accent"], C["accent_light"]),
            ("% Kehadiran", f"{persen_hadir:.1f}%", C["green"], C["green_light"]),
            ("Rata-rata Nilai", f"{avg_nilai:.1f}", C["orange"], C["orange_light"]),
            ("Poin Bersih", f"{poin_net:+d}", C["gold"], C["accent_light"]),
        ]:
            card = tk.Frame(stats_row, bg=bg_c, relief="flat",
                          highlightbackground=C["border"], highlightthickness=1)
            card.pack(side="left", expand=True, fill="x", padx=4)
            tk.Label(card, text=val, font=("Segoe UI", 18, "bold"),
                    fg=color, bg=bg_c).pack(pady=(10, 0))
            tk.Label(card, text=label, font=("Segoe UI", 9),
                    fg=C["text2"], bg=bg_c).pack(pady=(0, 10))

        # ── Row 3: Two-column layout (Absensi | Nilai) ──
        row3 = tk.Frame(body, bg=C["bg"])
        row3.pack(fill="both", expand=True, padx=20, pady=(0, 8))

        # Absensi card
        att_card = tk.Frame(row3, bg=C["card"], relief="flat",
                          highlightbackground=C["border"], highlightthickness=1)
        att_card.pack(side="left", fill="both", expand=True, padx=(0, 4))

        att_inner = tk.Frame(att_card, bg=C["card"])
        att_inner.pack(fill="both", expand=True, padx=12, pady=12)

        att_hdr = tk.Frame(att_inner, bg=C["card"])
        att_hdr.pack(fill="x", pady=(0, 6))
        tk.Label(att_hdr, text="✅ Riwayat Absensi",
                font=("Segoe UI", 12, "bold"), fg=C["text"],
                bg=C["card"]).pack(side="left")

        # Filter by month
        self.att_filter = tk.StringVar(value="Semua")
        ttk.Combobox(att_hdr, textvariable=self.att_filter,
                    values=["Semua", "Bulan Ini", "Hadir", "Izin", "Sakit", "Alfa"],
                    state="readonly", width=12).pack(side="right")
        self.att_filter.trace_add("write", lambda *a: self._load_attendance())

        # Mini stats
        att_stats = tk.Frame(att_inner, bg=C["card"])
        att_stats.pack(fill="x", pady=(0, 6))
        for label, val, color in [
            ("H", count_hadir, C["green"]),
            ("I", count_izin, C["accent"]),
            ("S", count_sakit, C["orange"]),
            ("A", count_alfa, C["red"]),
        ]:
            tk.Label(att_stats, text=f"{label}: {val}", font=("Segoe UI", 9, "bold"),
                    fg=color, bg=C["card"]).pack(side="left", padx=4)

        # Tree
        tree_frame = tk.Frame(att_inner, bg=C["card"])
        tree_frame.pack(fill="both", expand=True)
        self.tree_att = ttk.Treeview(tree_frame,
                                    columns=("tgl", "masuk", "keluar", "status"),
                                    show="headings", height=10)
        for col, txt, w in [("tgl", "Tanggal", 90), ("masuk", "Masuk", 80),
                            ("keluar", "Keluar", 80), ("status", "Status", 80)]:
            self.tree_att.heading(col, text=txt, anchor="w")
            self.tree_att.column(col, width=w, minwidth=60)

        sb = ttk.Scrollbar(tree_frame, orient="vertical", command=self.tree_att.yview)
        self.tree_att.configure(yscrollcommand=sb.set)
        self.tree_att.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

        # Color tags for status
        self.tree_att.tag_configure("Hadir", foreground=C["green"])
        self.tree_att.tag_configure("Izin", foreground=C["accent"])
        self.tree_att.tag_configure("Sakit", foreground=C["orange"])
        self.tree_att.tag_configure("Alfa", foreground=C["red"])

        self._load_attendance()

        # Nilai card
        nil_card = tk.Frame(row3, bg=C["card"], relief="flat",
                          highlightbackground=C["border"], highlightthickness=1)
        nil_card.pack(side="left", fill="both", expand=True, padx=(4, 0))

        nil_inner = tk.Frame(nil_card, bg=C["card"])
        nil_inner.pack(fill="both", expand=True, padx=12, pady=12)

        nil_hdr = tk.Frame(nil_inner, bg=C["card"])
        nil_hdr.pack(fill="x", pady=(0, 6))
        tk.Label(nil_hdr, text="📝 Daftar Nilai",
                font=("Segoe UI", 12, "bold"), fg=C["text"],
                bg=C["card"]).pack(side="left")
        tk.Label(nil_hdr, text=f"Rata-rata: {avg_nilai:.1f} | Tertinggi: {max_nilai:.0f} | Terendah: {min_nilai:.0f}",
                font=("Segoe UI", 9), fg=C["text2"], bg=C["card"]).pack(side="right")

        tree_frame2 = tk.Frame(nil_inner, bg=C["card"])
        tree_frame2.pack(fill="both", expand=True)
        self.tree_nil = ttk.Treeview(tree_frame2,
                                    columns=("mapel", "nilai", "sem", "ta"),
                                    show="headings", height=10)
        for col, txt, w in [("mapel", "Mata Pelajaran", 160), ("nilai", "Nilai", 70),
                            ("sem", "Sem", 60), ("ta", "T.A.", 100)]:
            self.tree_nil.heading(col, text=txt, anchor="w")
            self.tree_nil.column(col, width=w, minwidth=60)

        sb2 = ttk.Scrollbar(tree_frame2, orient="vertical", command=self.tree_nil.yview)
        self.tree_nil.configure(yscrollcommand=sb2.set)
        self.tree_nil.pack(side="left", fill="both", expand=True)
        sb2.pack(side="right", fill="y")

        # Color tags for nilai
        self.tree_nil.tag_configure("tinggi", foreground=C["green"])
        self.tree_nil.tag_configure("sedang", foreground=C["orange"])
        self.tree_nil.tag_configure("rendah", foreground=C["red"])

        mw = {m["id"]: m for m in db.mapel_all()}
        for n in nilai_rows:
            v = n.get("nilai") or 0
            tag = "tinggi" if v >= 80 else ("sedang" if v >= 60 else "rendah")
            self.tree_nil.insert("", "end", values=(
                mw.get(n["mapel_id"], {}).get("nama", "-"),
                f"{v:.1f}" if v else "-",
                n.get("semester") or "-",
                n.get("tahun_ajaran") or "-",
            ), tags=(tag,))

        # ── Row 4: Poin Disiplin ──
        poin_card = tk.Frame(body, bg=C["card"], relief="flat",
                            highlightbackground=C["border"], highlightthickness=1)
        poin_card.pack(fill="x", padx=20, pady=(0, 16))

        poin_inner = tk.Frame(poin_card, bg=C["card"])
        poin_inner.pack(fill="both", expand=True, padx=12, pady=12)

        poin_hdr = tk.Frame(poin_inner, bg=C["card"])
        poin_hdr.pack(fill="x", pady=(0, 6))
        tk.Label(poin_hdr, text="🎯 Poin Disiplin",
                font=("Segoe UI", 12, "bold"), fg=C["text"],
                bg=C["card"]).pack(side="left")
        tk.Label(poin_hdr,
                text=f"Positif: +{poin_pos} | Negatif: -{poin_neg} | Bersih: {poin_net:+d}",
                font=("Segoe UI", 9), fg=C["text2"], bg=C["card"]).pack(side="right")

        tree_frame3 = tk.Frame(poin_inner, bg=C["card"])
        tree_frame3.pack(fill="both", expand=True)
        self.tree_poin = ttk.Treeview(tree_frame3,
                                     columns=("tgl", "kategori", "poin", "ket", "oleh"),
                                     show="headings", height=6)
        for col, txt, w in [("tgl", "Tanggal", 90), ("kategori", "Kategori", 80),
                            ("poin", "Poin", 60), ("ket", "Keterangan", 220),
                            ("oleh", "Oleh", 110)]:
            self.tree_poin.heading(col, text=txt, anchor="w")
            self.tree_poin.column(col, width=w, minwidth=60)

        sb3 = ttk.Scrollbar(tree_frame3, orient="vertical", command=self.tree_poin.yview)
        self.tree_poin.configure(yscrollcommand=sb3.set)
        self.tree_poin.pack(side="left", fill="both", expand=True)
        sb3.pack(side="right", fill="y")

        self.tree_poin.tag_configure("Positif", foreground=C["green"])
        self.tree_poin.tag_configure("Negatif", foreground=C["red"])

        for p in poin_rows:
            self.tree_poin.insert("", "end", values=(
                p.get("tanggal") or "-",
                p.get("kategori") or "-",
                f"{p.get('poin', 0):+d}",
                p.get("keterangan") or "-",
                p.get("diberikan_oleh") or "-",
            ), tags=(p.get("kategori") or "",))

    def _load_attendance(self):
        if not self.tree_att:
            return
        for i in self.tree_att.get_children():
            self.tree_att.delete(i)
        filter_val = self.att_filter.get()
        rows = db.absensi_by_siswa(self.siswa_id)
        for r in rows:
            status = r.get("status") or "-"
            if filter_val == "Bulan Ini":
                # current month-year
                bulan_ini = datetime.now().strftime("%Y-%m")
                if not (r.get("tanggal") or "").startswith(bulan_ini):
                    continue
            elif filter_val in ("Hadir", "Izin", "Sakit", "Alfa"):
                if status != filter_val:
                    continue
            self.tree_att.insert("", "end", values=(
                r.get("tanggal") or "-",
                r.get("waktu_masuk") or "-",
                r.get("waktu_keluar") or "-",
                status,
            ), tags=(status,))

    def _build_picker(self):
        """Show a student picker if no ID provided."""
        p = self.parent
        # Header
        tk.Label(p, text="👤 Pilih Siswa",
                font=("Segoe UI", 18, "bold"), fg=C["text"],
                bg=C["bg"]).pack(anchor="w", padx=20, pady=(16, 0))
        tk.Label(p, text="Double-click pada siswa untuk melihat detail",
                font=("Segoe UI", 10), fg=C["text2"],
                bg=C["bg"]).pack(anchor="w", padx=20, pady=(0, 8))

        # Search bar
        search = tk.Frame(p, bg=C["bg"])
        search.pack(fill="x", padx=20, pady=(0, 8))
        tk.Label(search, text="🔍 Cari:", font=("Segoe UI", 10),
                fg=C["text"], bg=C["bg"]).pack(side="left")
        self.search_var = tk.StringVar()
        e = tk.Entry(search, textvariable=self.search_var, font=("Segoe UI", 11),
                    bg=C["input_bg"], relief="flat",
                    highlightthickness=1, highlightbackground=C["border"],
                    highlightcolor=C["accent"])
        e.pack(side="left", fill="x", expand=True, padx=8, ipady=4)
        self.search_var.trace_add("write", lambda *a: self._load_picker_list())

        # Table
        table_frame = tk.Frame(p, bg=C["card"], relief="flat",
                              highlightbackground=C["border"], highlightthickness=1)
        table_frame.pack(fill="both", expand=True, padx=20, pady=(0, 16))

        self.tree = ttk.Treeview(table_frame,
                                columns=("nis", "nama", "kelas"),
                                show="headings", height=18)
        for col, txt, w in [("nis", "NIS", 120), ("nama", "Nama", 280),
                            ("kelas", "Kelas", 140)]:
            self.tree.heading(col, text=txt, anchor="w")
            self.tree.column(col, width=w, minwidth=80)

        sb = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=sb.set)
        self.tree.pack(side="left", fill="both", expand=True, padx=8, pady=8)
        sb.pack(side="right", fill="y", pady=8)

        self.tree.bind("<Double-1>", self._on_pick)
        self._load_picker_list()

    def _load_picker_list(self):
        if not hasattr(self, "tree") or not self.tree:
            return
        for i in self.tree.get_children():
            self.tree.delete(i)
        keyword = (self.search_var.get() if hasattr(self, "search_var") else "").lower()
        kw = {k["id"]: k["nama"] for k in db.kelas_all()}
        for row in db.siswa_all():
            if keyword and keyword not in (row["nama"] or "").lower() \
                       and keyword not in (row["nis"] or "").lower():
                continue
            self.tree.insert("", "end", iid=str(row["id"]),
                           values=(row["nis"], row["nama"], kw.get(row["kelas_id"], "-")))

    def _on_pick(self, event):
        sel = self.tree.selection()
        if not sel:
            return
        self.siswa_id = int(sel[0])
        self.build()

    def _back_to_list(self):
        # Switch back to siswa list
        try:
            mod = __import__("ui.siswa", fromlist=["Siswa"])
            cls = getattr(mod, "Siswa")
            for c in self.parent.winfo_children():
                c.destroy()
            cls(self.parent).build()
        except Exception as e:
            messagebox.showerror("Error", f"Gagal kembali: {e}")

    def _generate_qr(self, record, qr_lbl):
        """Generate QR for student."""
        import json
        from utils.barcode_generator import generate_qr
        _DESKTOP = os.path.dirname(os.path.dirname(here))
        _QR_DIR = os.path.join(_DESKTOP, "assets", "qr")
        os.makedirs(_QR_DIR, exist_ok=True)
        qr_data = json.dumps({
            "siswa_id": record["id"],
            "nis": record["nis"],
            "nama": record["nama"],
        }, ensure_ascii=False)
        qr_path = os.path.join(_QR_DIR, f"{record['nis']}.png")
        try:
            generate_qr(qr_data, qr_path)
            db.siswa_update_qr(record["id"], qr_path)
            from PIL import Image, ImageTk
            img = Image.open(qr_path).resize((150, 150), Image.LANCZOS)
            self._qr_img = ImageTk.PhotoImage(img)
            qr_lbl.config(image=self._qr_img, text="", width=150, height=150)
            messagebox.showinfo("QR Code", f"QR tersimpan:\n{qr_path}")
        except Exception as e:
            messagebox.showerror("Error QR", f"Gagal generate QR:\n{e}")

    def _cetak_rapor(self, record):
        path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            initialfile=f"rapor_{record['nis']}.pdf")
        if not path:
            return
        try:
            nilai = db.q(
                "SELECT m.nama AS mapel, n.nilai, n.semester, n.tahun_ajaran "
                "FROM nilai n JOIN mapel m ON n.mapel_id=m.id "
                "WHERE siswa_id=? ORDER BY m.nama",
                (record["id"],))
            absen = db.absensi_by_siswa(record["id"])
            pdf_generator.generate_rapor_pdf(path, record, nilai, absen)
            messagebox.showinfo("Sukses", f"Rapor tersimpan:\n{path}")
        except Exception as e:
            messagebox.showerror("Error", f"Gagal cetak rapor:\n{e}")

    def _cetak_name_tag(self, record):
        path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            initialfile=f"nametag_{record['nis']}.pdf")
        if not path:
            return
        try:
            barcode_path = path.replace(".pdf", f"_{record['nis']}.png")
            barcode_generator.generate_code128(record["nis"], barcode_path)
            kelas_nama = "-"
            if record.get("kelas_id"):
                krows = db.q("SELECT nama FROM kelas WHERE id=?",
                            (record["kelas_id"],))
                if krows:
                    kelas_nama = krows[0]["nama"]
            cards = [{
                "nama": record["nama"],
                "nis": record["nis"],
                "kelas": kelas_nama,
                "foto": record.get("foto"),
                "barcode": barcode_path,
            }]
            pdf_generator.generate_name_tag_pdf(path, cards)
            messagebox.showinfo("Sukses", f"Name tag tersimpan:\n{path}")
        except Exception as e:
            messagebox.showerror("Error", f"Gagal cetak name tag:\n{e}")
