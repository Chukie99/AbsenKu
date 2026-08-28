"""
ui/absensi.py — lihat absen per siswa / per kelas, edit status, export.
Edit status tercatat ke audit_log.
"""
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db, csv, datetime


class Absensi:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None

    def build(self):
        p = self.parent
        for c in p.winfo_children(): c.destroy()
        today = datetime.date.today().isoformat()

        # filter header
        tf = tk.Frame(p); tf.pack(fill="x", pady=6)
        tk.Label(tf, text="Tanggal (yyyy-mm-dd):").pack(side="left")
        self.date_e = tk.Entry(tf, width=12); self.date_e.pack(side="left", padx=4)
        self.date_e.insert(0, today)
        tk.Button(tf, text="Tampil", bg="#1A73E8", fg="white", command=self.load, relief="flat", padx=8).pack(side="left", padx=4)
        tk.Button(tf, text="Export CSV", bg="#FBBC04", fg="black", command=self.export, relief="flat", padx=8).pack(side="right")

        self.tree = ttk.Treeview(p, columns=("tgl","masuk","keluar","status","siswa","mapel"), show="headings", height=18)
        for col, txt, w in [("tgl","Tgl",90),("masuk","Masuk",80),("keluar","Keluar",80),("status","Status",90),("siswa","Siswa",160),("mapel","Mapel",90)]:
            self.tree.heading(col, text=txt); self.tree.column(col, width=w)
        self.tree.pack(fill="both", expand=True)
        self.tree.bind("<ButtonRelease-1>", self.edit_status)

    def load(self):
        for i in self.tree.get_children(): self.tree.delete(i)
        tgl = self.date_e.get()
        siswas = {s["id"]: s for s in db.siswa_all()}
        for a in db.q("SELECT * FROM absensi WHERE tanggal=? ORDER BY siswa_id", (tgl,)):
            s = siswas.get(a["siswa_id"], {})
            self.tree.insert("", "end", iid=a["id"], values=(a["tanggal"], a["waktu_masuk"], a["waktu_keluar"], a["status"], s.get("nama","-"), a["mapel_id"]))

    def edit_status(self, event):
        sel = self.tree.selection()
        if not sel: return
        rid = int(sel[0])
        rec = db.q("SELECT * FROM absensi WHERE id=?", (rid,))
        if not rec: return
        rec = rec[0]
        new = tk.simpledialog.askstring("Edit Status", "Status baru (Hadir/Izin/Sakit/Alfy):", initialvalue=rec["status"])
        if new and new in ("Hadir","Izin","Sakit","Alfa"):
            old = rec["status"]
            if old != new:
                db.absensi_update(rid, new, rec["waktu_keluar"])
                db.audit_insert("absensi", rid, "status", old, new, "desktop-edit")
            self.load()

    def export(self):
        tgl = self.date_e.get()
        rows = db.absensi_by_date(tgl)
        path = filedialog.asksaveasfilename(defaultextension=".csv", initialfile=f"absen_{tgl}.csv")
        if not path: return
        with open(path, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f); w.writerow(["Tanggal","Waktu Masuk","Waktu Keluar","Status","Siswa ID","Mapel ID"])
            for r in rows:
                w.writerow([r["tanggal"], r["waktu_masuk"], r["waktu_keluar"], r["status"], r["siswa_id"], r["mapel_id"]])
        messagebox.showinfo("Sukses", f"Export ke {path}")
