"""
Poin Disiplin — Pelanggaran & Prestasi Siswa
AbsenKu v2.2.0
"""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(here))

import tkinter as tk
from tkinter import ttk, messagebox, StringVar, IntVar

import db_manager as db

# ── Color palette ──
C = {"bg": "#F8F9FA", "text": "#1E293B", "text2": "#64748B", "accent": "#2563EB",
     "success": "#28A745", "danger": "#DC3545"}


class Poin_disiplin:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # Header
        hdr = tk.Frame(p, bg=C["bg"])
        hdr.pack(fill="x", padx=20, pady=(16, 0))
        tk.Label(hdr, text="🎯 Poin Disiplin", font=("Segoe UI", 18, "bold"),
                 fg=C["text"], bg=C["bg"]).pack(anchor="w")
        tk.Label(hdr, text="Pelanggaran & Prestasi Siswa", font=("Segoe UI", 10),
                 fg=C["text2"], bg=C["bg"]).pack(anchor="w")

        # Controls
        ctrl = tk.Frame(p, bg=C["bg"])
        ctrl.pack(fill="x", padx=20, pady=(8, 4))
        tk.Button(ctrl, text="➕ Tambah Poin", bg=C["accent"], fg="white",
                  relief="flat", padx=10, pady=4, command=self._show_add_dialog).pack(side="left")
        tk.Button(ctrl, text="🔄 Refresh", bg="#64748B", fg="white",
                  relief="flat", padx=10, pady=4, command=self.load).pack(side="left", padx=4)

        # Filter by kelas
        tk.Label(ctrl, text="Kelas:", font=("Segoe UI", 10), fg=C["text"],
                 bg=C["bg"]).pack(side="left", padx=(12, 4))
        self.filter_var = StringVar(value="Semua")
        kelas_names = ["Semua"] + [k["nama"] for k in db.kelas_all()]
        ttk.Combobox(ctrl, textvariable=self.filter_var, values=kelas_names,
                     state="readonly", width=16).pack(side="left")
        self.filter_var.trace_add("write", lambda *a: self.load())

        # Stats cards
        self.stats_frame = tk.Frame(p, bg=C["bg"])
        self.stats_frame.pack(fill="x", padx=20, pady=(4, 8))

        # Table
        tree_frame = tk.Frame(p, bg=C["bg"])
        tree_frame.pack(fill="both", expand=True, padx=20, pady=(0, 16))

        cols = ("id", "siswa", "tanggal", "kategori", "poin", "keterangan", "diberikan_oleh")
        self.tree = ttk.Treeview(tree_frame, columns=cols, show="headings", height=18)
        headers = {"id": ("ID", 0), "siswa": ("Siswa", 180), "tanggal": ("Tanggal", 90),
                   "kategori": ("Kategori", 90), "poin": ("Poin", 60),
                   "keterangan": ("Keterangan", 200), "diberikan_oleh": ("Diberikan Oleh", 120)}
        for c in cols:
            self.tree.heading(c, text=headers[c][0])
            self.tree.column(c, width=headers[c][1], minwidth=20)
        self.tree.column("id", width=0, stretch=False)

        scroll = ttk.Scrollbar(tree_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll.set)
        self.tree.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        self.load()

    def load(self):
        if not self.tree:
            return
        self.tree.delete(*self.tree.get_children())
        kelas_filter = self.filter_var.get() if hasattr(self, 'filter_var') else "Semua"

        query = """
            SELECT pd.id, s.nama, pd.tanggal, pd.kategori, pd.poin, pd.keterangan, pd.diberikan_oleh
            FROM poin_disiplin pd
            JOIN siswa s ON pd.siswa_id = s.id
        """
        params = ()
        if kelas_filter and kelas_filter != "Semua":
            query += " JOIN kelas k ON s.kelas_id = k.id WHERE k.nama = ?"
            params = (kelas_filter,)
        query += " ORDER BY pd.tanggal DESC, pd.id DESC"

        rows = db.fetch_all(query, params) if params else db.fetch_all(query)
        total = 0
        neg = 0
        pos = 0
        for r in rows:
            poin = r["poin"] if isinstance(r, dict) else r[4]
            total += poin
            if poin < 0:
                neg += abs(poin)
            else:
                pos += poin
            vals = [r["id"], r["siswa"], r["tanggal"], r["kategori"], r["poin"], r["keterangan"], r["diberikan_oleh"]] if isinstance(r, dict) else list(r)
            tag = "neg" if (r["kategori"] if isinstance(r, dict) else r[3]) == "Negatif" else "pos"
            self.tree.insert("", "end", values=vals, tags=(tag,))

        self.tree.tag_configure("neg", foreground=C["danger"])
        self.tree.tag_configure("pos", foreground=C["success"])

        # Stats
        for w in self.stats_frame.winfo_children():
            w.destroy()
        for label, val, color in [("Total Poin", total, C["accent"]), ("Negatif", neg, C["danger"]), ("Positif", pos, C["success"]), ("Entri", len(rows), C["text2"])]:
            card = tk.Frame(self.stats_frame, bg="white", relief="solid", bd=1)
            card.pack(side="left", padx=5, fill="x", expand=True)
            tk.Label(card, text=label, font=("Segoe UI", 9), fg=C["text2"], bg="white").pack(pady=(8, 2))
            tk.Label(card, text=str(val), font=("Segoe UI", 16, "bold"), fg=color, bg="white").pack(pady=(0, 8))

    def _show_add_dialog(self):
        dlg = tk.Toplevel(self.parent)
        dlg.title("Tambah Poin Disiplin")
        dlg.geometry("450x420")
        dlg.resizable(False, False)

        tk.Label(dlg, text="Tambah Poin Disiplin", font=("Segoe UI", 14, "bold"),
                 fg=C["text"]).pack(pady=(15, 10))

        frm = tk.Frame(dlg, bg="white")
        frm.pack(fill="both", expand=True, padx=15, pady=10)

        # Siswa
        tk.Label(frm, text="Siswa:", font=("Segoe UI", 10), bg="white").pack(anchor="w")
        siswa_rows = db.fetch_all("""
            SELECT s.id, s.nama || ' (' || k.nama || ')' as label
            FROM siswa s JOIN kelas k ON s.kelas_id = k.id
            WHERE s.is_active = 1 ORDER BY s.nama
        """)
        siswa_list = [r["label"] for r in siswa_rows] if siswa_rows else []
        siswa_map = {r["label"]: r["id"] for r in siswa_rows} if siswa_rows else {}
        siswa_var = StringVar()
        ttk.Combobox(frm, textvariable=siswa_var, values=siswa_list, state="readonly", width=40).pack(fill="x", pady=(0, 8))

        # Kategori
        tk.Label(frm, text="Kategori:", font=("Segoe UI", 10), bg="white").pack(anchor="w")
        kategori_var = StringVar(value="Negatif")
        kat_frame = tk.Frame(frm, bg="white")
        kat_frame.pack(fill="x", pady=(0, 8))
        tk.Radiobutton(kat_frame, text="⛔ Negatif (-)", variable=kategori_var, value="Negatif", bg="white").pack(side="left", padx=(0, 15))
        tk.Radiobutton(kat_frame, text="🏆 Positif (+)", variable=kategori_var, value="Positif", bg="white").pack(side="left")

        # Poin
        tk.Label(frm, text="Poin:", font=("Segoe UI", 10), bg="white").pack(anchor="w")
        poin_var = IntVar(value=-5)
        tk.Spinbox(frm, from_=-100, to=100, textvariable=poin_var, width=10, font=("Segoe UI", 12)).pack(anchor="w", pady=(0, 8))

        # Keterangan
        tk.Label(frm, text="Keterangan:", font=("Segoe UI", 10), bg="white").pack(anchor="w")
        ket_var = StringVar()
        tk.Entry(frm, textvariable=ket_var, width=40).pack(fill="x", pady=(0, 8))

        # Diberikan oleh
        tk.Label(frm, text="Diberikan oleh:", font=("Segoe UI", 10), bg="white").pack(anchor="w")
        oleh_var = StringVar()
        tk.Entry(frm, textvariable=oleh_var, width=40).pack(fill="x", pady=(0, 12))

        def save():
            siswa_id = siswa_map.get(siswa_var.get())
            if not siswa_id:
                messagebox.showwarning("Warning", "Pilih siswa!")
                return
            poin = poin_var.get()
            kategori = kategori_var.get()
            if kategori == "Negatif" and poin > 0:
                poin = -poin
            elif kategori == "Positif" and poin < 0:
                poin = abs(poin)

            from datetime import date
            db.exec_one("""
                INSERT INTO poin_disiplin (siswa_id, tanggal, kategori, poin, keterangan, diberikan_oleh)
                VALUES (?, ?, ?, ?, ?, ?)
            """, (siswa_id, date.today().isoformat(), kategori, poin,
                  ket_var.get() or None, oleh_var.get() or None))
            dlg.destroy()
            self.load()

        btn_frame = tk.Frame(frm, bg="white")
        btn_frame.pack(fill="x")
        tk.Button(btn_frame, text="Simpan", bg=C["success"], fg="white", relief="flat",
                  padx=12, pady=4, command=save).pack(side="right", padx=4)
        tk.Button(btn_frame, text="Batal", bg="#64748B", fg="white", relief="flat",
                  padx=12, pady=4, command=dlg.destroy).pack(side="right")
