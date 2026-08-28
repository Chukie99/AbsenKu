"""
ui/siswa.py — CRUD table with soft-delete, import CSV (with validation), export.
"""
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import db_manager as db
import csv, os


class Siswa:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        top = tk.Frame(p)
        top.pack(fill="x", pady=6)
        tk.Button(top, text="Tambah", bg="#1A73E8", fg="white", command=self.add, relief="flat", padx=10, pady=4).pack(side="left")
        tk.Button(top, text="Import CSV", bg="#34A853", fg="white", command=self.do_import, relief="flat", padx=10, pady=4).pack(side="left", padx=4)
        tk.Button(top, text="Export CSV", bg="#FBBC04", fg="black", command=self.do_export, relief="flat", padx=10, pady=4).pack(side="left", padx=4)

        self.tree = ttk.Treeview(p, columns=("nis", "nama", "kelas", "no_hp", "tgl_lahir"), show="headings", height=18)
        for col, txt, w in [("nis","NIS",100), ("nama","Nama",200), ("kelas","Kelas",100), ("no_hp","No HP Ortu",140), ("tgl_lahir","Tgl Lahir",100)]:
            self.tree.heading(col, text=txt)
            self.tree.column(col, width=w)
        self.tree.pack(fill="both", expand=True)
        self.tree.bind("<ButtonRelease-1>", self.on_select)

        self.load()

    def load(self):
        for i in self.tree.get_children():
            self.tree.delete(i)
        for row in db.siswa_all():
            kelas_nama = "-"; 
            rows = db.q("SELECT nama FROM kelas WHERE id=?", (row["kelas_id"],))
            if rows: kelas_nama = rows[0]["nama"]
            self.tree.insert("", "end", iid=row["id"], values=(row["nis"], row["nama"], kelas_nama, row["no_hp_ortu"] or "-", row["tanggal_lahir"] or "-"))

    def add(self):
        form = SiswaForm(self.parent, on_save=self._after_save)
        form.build()

    def edit(self, event):
        sel = self.tree.selection()
        if not sel: return
        record_id = int(sel[0])
        rows = db.siswa_all()
        rec = next((r for r in rows if r["id"] == record_id), None)
        if rec:
            form = SiswaForm(self.parent, record=rec, on_save=self._after_save)
            form.build()

    def on_select(self, event):
        sel = self.tree.selection()
        # double-click edit
        self.edit(event)

    def _after_save(self):
        self.load()

    def do_import(self):
        path = filedialog.askopenfilename(filetypes=[("CSV", "*.csv")])
        if not path: return
        errors = []
        rows = []
        seen_nis = set()
        with open(path, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for i, r in enumerate(reader, 2):
                nis = (r.get("NIS") or "").strip()
                nama = (r.get("Nama") or "").strip()
                kelas_nama = (r.get("Kelas") or "").strip()
                if not nis or not nama:
                    errors.append(f"Baris {i}: NIS/Nama kosong")
                    continue
                if nis in seen_nis:
                    errors.append(f"Baris {i}: NIS duplikat {nis}")
                    continue
                # resolve kelas
                kr = db.q("SELECT id FROM kelas WHERE nama=?", (kelas_nama,))
                if not kr:
                    errors.append(f"Baris {i}: kelas '{kelas_nama}' tidak ditemukan")
                    continue
                seen_nis.add(nis)
                rows.append((nis, nama, kr[0]["id"], r.get("Alamat",""), r.get("No HP Ortu",""), r.get("Tanggal Lahir","")))
        if errors:
            msg = "\n".join(errors[:20])
            if messagebox.askyesno("Error Import", f"{len(errors)} error ditemukan:\n{msg}\n\nLanjutkan {len(rows)} baris valid?"):
                pass
            else:
                return
        # preview
        if not messagebox.askyesno("Konfirmasi", f"Import {len(rows)} siswa?"):
            return
        try:
            with db.tx() as conn:
                for nr in rows:
                    conn.execute("INSERT OR IGNORE INTO siswa (nis, nama, kelas_id, alamat, no_hp_ortu, tanggal_lahir) VALUES (?,?,?,?,?,?)", nr)
                conn.commit()
            messagebox.showinfo("Sukses", f"Import {len(rows)} siswa selesai")
            self.load()
        except Exception as e:
            messagebox.showerror("Error", f"Gagal import: {e}")

    def do_export(self):
        path = filedialog.asksaveasfilename(defaultextension=".csv", initialfile="siswa.csv")
        if not path: return
        try:
            with open(path, "w", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                w.writerow(["NIS", "Nama", "Kelas ID", "Alamat", "No HP Ortu", "Tanggal Lahir"])
                for row in db.siswa_all():
                    w.writerow([row["nis"], row["nama"], row["kelas_id"], row["alamat"], row["no_hp_ortu"], row["tanggal_lahir"]])
            messagebox.showinfo("Sukses", f"Export ke {path}")
        except Exception as e:
            messagebox.showerror("Error", str(e))


class SiswaForm:
    def __init__(self, parent, record=None, on_save=None):
        self.parent = parent
        self.record = record
        self.on_save = on_save

    def build(self):
        top = tk.Toplevel(self.parent.winfo_toplevel() if hasattr(self.parent, "winfo_toplevel") else self.parent)
        top.title("Edit Siswa" if self.record else "Tambah Siswa")
        top.geometry("400x440")
        frm = tk.Frame(top, padx=16, pady=16)
        frm.pack(fill="both", expand=True)

        tk.Label(frm, text="NIS").pack(anchor="w")
        nis_e = tk.Entry(frm, width=40)
        nis_e.pack(fill="x"); nis_e.insert(0, self.record["nis"] if self.record else "")

        tk.Label(frm, text="Nama").pack(anchor="w", pady=(8,0))
        nama_e = tk.Entry(frm, width=40)
        nama_e.pack(fill="x"); nama_e.insert(0, self.record["nama"] if self.record else "")

        tk.Label(frm, text="Kelas ID (angka)").pack(anchor="w", pady=(8,0))
        kelas_e = tk.Entry(frm, width=40)
        kelas_e.pack(fill="x"); kelas_e.insert(0, str(self.record["kelas_id"]) if self.record else "")

        tk.Label(frm, text="Alamat").pack(anchor="w", pady=(8,0))
        alamat_e = tk.Entry(frm, width=40)
        alamat_e.pack(fill="x"); alamat_e.insert(0, self.record["alamat"] or "" if self.record else "")

        tk.Label(frm, text="No HP Orang Tua").pack(anchor="w", pady=(8,0))
        hp_e = tk.Entry(frm, width=40)
        hp_e.pack(fill="x"); hp_e.insert(0, self.record["no_hp_ortu"] or "" if self.record else "")

        tk.Label(frm, text="Tanggal Lahir (yyyy-mm-dd)").pack(anchor="w", pady=(8,0))
        tgl_e = tk.Entry(frm, width=40)
        tgl_e.pack(fill="x"); tgl_e.insert(0, self.record["tanggal_lahir"] or "" if self.record else "")

        def save():
            nis = nis_e.get().strip()
            nama = nama_e.get().strip()
            if not nis or not nama:
                messagebox.showwarning("Validasi", "NIS & Nama wajib diisi"); return
            try:
                kid = int(kelas_e.get())
            except ValueError:
                kid = 0
            if self.record:
                db.siswa_update(self.record["id"], nis, nama, kid, self.record.get("foto",""), alamat_e.get(), hp_e.get(), tgl_e.get())
            else:
                db.siswa_insert(nis, nama, kid, None, alamat_e.get(), hp_e.get(), tgl_e.get())
            if self.on_save: self.on_save()
            top.destroy()

        tk.Button(frm, text="Simpan", bg="#1A73E8", fg="white", command=save, relief="flat", padx=12, pady=5).pack(pady=16)
