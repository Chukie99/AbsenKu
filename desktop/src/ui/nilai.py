"""
ui/nilai.py — input nilai per siswa per mapel, rekap rata-rata, audit_log on edit, export.
"""
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import excel_exporter


class Nilai:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None

    def build(self):
        p = self.parent
        for c in p.winfo_children(): c.destroy()
        tf = tk.Frame(p); tf.pack(fill="x", pady=6)
        tk.Button(tf, text="Input Nilai", bg="#1A73E8", fg="white", command=self.input_nilai, relief="flat", padx=10, pady=4).pack(side="left")
        tk.Button(tf, text="Export Excel", bg="#34A853", fg="white", command=self.export_excel, relief="flat", padx=10, pady=4).pack(side="left", padx=4)

        self.tree = ttk.Treeview(p, columns=("siswa","mapel","nilai","sem","ta"), show="headings", height=18)
        for col, txt, w in [("siswa","Siswa",200),("mapel","Mapel",160),("nilai","Nilai",80),("sem","Semester",80),("ta","T.A.",100)]:
            self.tree.heading(col, text=txt); self.tree.column(col, width=w)
        self.tree.pack(fill="both", expand=True)
        self.load()

    def load(self):
        for i in self.tree.get_children(): self.tree.delete(i)
        sw = {s["id"]: s for s in db.siswa_all()}
        mw = {m["id"]: m for m in db.mapel_all()}
        for n in db.q("SELECT * FROM nilai ORDER BY created_at DESC"):
            self.tree.insert("", "end", iid=n["id"], values=(sw.get(n["siswa_id"],{}).get("nama","-"), mw.get(n["mapel_id"],{}).get("nama","-"), n["nilai"], n["semester"], n["tahun_ajaran"]))

    def input_nilai(self):
        top = tk.Toplevel(self.parent); top.title("Input Nilai"); top.geometry("360x240")
        frm = tk.Frame(top, padx=16, pady=16); frm.pack(fill="both", expand=True)
        tk.Label(frm, text="Siswa ID").pack(anchor="w"); sid = tk.Entry(frm, width=36); sid.pack(fill="x", pady=2)
        tk.Label(frm, text="Mapel ID").pack(anchor="w", pady=(8,0)); mid = tk.Entry(frm, width=36); mid.pack(fill="x", pady=2)
        tk.Label(frm, text="Nilai").pack(anchor="w", pady=(8,0)); nl = tk.Entry(frm, width=36); nl.pack(fill="x", pady=2)
        tk.Label(frm, text="Semester").pack(anchor="w", pady=(8,0)); sm = tk.Entry(frm, width=36); sm.pack(fill="x", pady=2); sm.insert(0,"1")
        tk.Label(frm, text="Tahun Ajaran").pack(anchor="w", pady=(8,0)); ta = tk.Entry(frm, width=36); ta.pack(fill="x", pady=2); ta.insert(0,"2025/2026")

        def save():
            try:
                s_id = int(sid.get()); m_id = int(mid.get())
            except ValueError:
                messagebox.showwarning("Validasi", "ID harus angka"); return
            db.nilai_insert(s_id, m_id, nl.get(), sm.get(), ta.get())
            self.load(); top.destroy()
        tk.Button(frm, text="Simpan", bg="#1A73E8", fg="white", command=save, relief="flat", padx=12).pack(pady=14)

    def export_excel(self):
        rows = db.q("SELECT s.nama,s.nis,m.nama AS mapel_nama,n.* FROM nilai n JOIN siswa s ON n.siswa_id=s.id JOIN mapel m ON n.mapel_id=m.id ORDER BY s.nama, m.nama")
        path = filedialog.asksaveasfilename(defaultextension=".xlsx", initialfile="nilai.xlsx")
        if not path: return
        excel_exporter.export_nilai_xlsx(path, rows)
        messagebox.showinfo("Sukses", f"Export ke {path}")
