"""
ui/kelas.py — CRUD kelas with soft-delete.
"""
import tkinter as tk
from tkinter import ttk, messagebox
import db_manager as db


class Kelas:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None

    def build(self):
        p = self.parent
        for c in p.winfo_children(): c.destroy()
        top = tk.Frame(p); top.pack(fill="x", pady=6)
        tk.Button(top, text="Tambah", bg="#1A73E8", fg="white", command=self.add, relief="flat", padx=10, pady=4).pack(side="left")

        self.tree = ttk.Treeview(p, columns=("nama","wali","ta"), show="headings", height=20)
        for col, txt, w in [("nama","Nama",180),("wali","Wali Kelas",160),("ta","T.A.",120)]:
            self.tree.heading(col, text=txt); self.tree.column(col, width=w)
        self.tree.pack(fill="both", expand=True)
        self.load()

    def load(self):
        for i in self.tree.get_children(): self.tree.delete(i)
        for row in db.kelas_all():
            self.tree.insert("", "end", iid=row["id"], values=(row["nama"], row["wali_kelas"] or "-", row["tahun_ajaran"] or "-"))

    def add(self):
        top = tk.Toplevel(self.parent)
        top.title("Tambah Kelas"); top.geometry("340x180")
        frm = tk.Frame(top, padx=16, pady=16); frm.pack(fill="both", expand=True)
        tk.Label(frm, text="Nama Kelas").pack(anchor="w")
        nama = tk.Entry(frm, width=36); nama.pack(fill="x", pady=2)
        tk.Label(frm, text="Wali Kelas").pack(anchor="w", pady=(8,0))
        wali = tk.Entry(frm, width=36); wali.pack(fill="x", pady=2)
        tk.Label(frm, text="Tahun Ajaran").pack(anchor="w", pady=(8,0))
        ta = tk.Entry(frm, width=36); ta.pack(fill="x", pady=2)

        def save():
            if not nama.get() or not wali.get():
                messagebox.showwarning("Validasi", "Nama & Wali Kelas wajib"); return
            db.kelas_insert(nama.get(), wali.get(), ta.get())
            self.load(); top.destroy()
        tk.Button(frm, text="Simpan", bg="#1A73E8", fg="white", command=save, relief="flat", padx=12).pack(pady=14)
