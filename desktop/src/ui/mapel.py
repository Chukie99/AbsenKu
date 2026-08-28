"""
ui/mapel.py — CRUD mata pelajaran.
"""
import tkinter as tk
from tkinter import ttk, messagebox
import db_manager as db
from utils import pairing_manager


class Mapel:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None

    def build(self):
        p = self.parent
        for c in p.winfo_children(): c.destroy()
        top = tk.Frame(p); top.pack(fill="x", pady=6)
        tk.Button(top, text="Tambah", bg="#1A73E8", fg="white", command=self.add, relief="flat", padx=10, pady=4).pack(side="left")

        self.tree = ttk.Treeview(p, columns=("nama","kode","jam"), show="headings", height=20)
        for col, txt, w in [("nama","Nama",240),("kode","Kode",120),("jam","Jam/Minggu",100)]:
            self.tree.heading(col, text=txt); self.tree.column(col, width=w)
        self.tree.pack(fill="both", expand=True)
        self.load()

    def load(self):
        for i in self.tree.get_children(): self.tree.delete(i)
        for row in db.mapel_all():
            self.tree.insert("", "end", iid=row["id"], values=(row["nama"], row["kode"], row["jam_per_minggu"]))

    def add(self):
        top = tk.Toplevel(self.parent)
        top.title("Tambah Mapel"); top.geometry("340x170")
        frm = tk.Frame(top, padx=16, pady=16); frm.pack(fill="both", expand=True)
        tk.Label(frm, text="Nama Mapel").pack(anchor="w")
        n = tk.Entry(frm, width=36); n.pack(fill="x", pady=2)
        tk.Label(frm, text="Kode").pack(anchor="w", pady=(8,0))
        k = tk.Entry(frm, width=36); k.pack(fill="x", pady=2)
        tk.Label(frm, text="Jam/Minggu").pack(anchor="w", pady=(8,0))
        j = tk.Entry(frm, width=36); j.pack(fill="x", pady=2)

        def save():
            if not n.get() or not k.get():
                messagebox.showwarning("Validasi", "Nama & Kode wajib"); return
            try: jam = int(j.get())
            except: jam = 0
            db.mapel_insert(n.get(), k.get(), jam)
            self.load(); top.destroy()
        tk.Button(frm, text="Simpan", bg="#1A73E8", fg="white", command=save, relief="flat", padx=12).pack(pady=14)
