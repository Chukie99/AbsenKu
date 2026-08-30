"""
ui/siswa.py — CRUD table with soft-delete, import CSV (with validation), export.
Centered modals, consistent styling.
"""
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import db_manager as db
import csv, os


C = {
    "bg": "#F8F9FA",
    "card": "#FFFFFF",
    "accent": "#2563EB",
    "green": "#16A34A",
    "orange": "#EA580C",
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "input_bg": "#F1F5F9",
    "row_hover": "#F1F5F9",
    "row_alt": "#FAFBFC",
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


class Siswa:
    def __init__(self, parent):
        self.parent = parent
        self.tree = None

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # Header
        tk.Label(p, text="Data Siswa", font=("Segoe UI", 16, "bold"),
                 fg=C["text"], bg=C["bg"]).pack(anchor="w", padx=20, pady=(16, 0))

        # Action bar
        top = tk.Frame(p, bg=C["bg"])
        top.pack(fill="x", padx=20, pady=8)

        for text, color, cmd in [
            ("+ Tambah Siswa", C["accent"], self.add),
            ("Import CSV", C["green"], self.do_import),
            ("Export CSV", C["orange"], self.do_export),
        ]:
            tk.Button(top, text=text, font=("Segoe UI", 10, "bold"),
                     bg=color, fg="white", relief="flat", cursor="hand2",
                     padx=12, pady=6, command=cmd).pack(side="left", padx=(0, 6))

        # Table
        table_frame = tk.Frame(p, bg=C["card"], relief="flat",
                              highlightbackground=C["border"], highlightthickness=1)
        table_frame.pack(fill="both", expand=True, padx=20, pady=(0, 16))

        self.tree = ttk.Treeview(table_frame,
                                columns=("nis", "nama", "kelas", "no_hp", "tgl_lahir"),
                                show="headings", height=18)
        for col, txt, w in [("nis", "NIS", 100), ("nama", "Nama", 220),
                            ("kelas", "Kelas", 100), ("no_hp", "No HP Ortu", 140),
                            ("tgl_lahir", "Tgl Lahir", 110)]:
            self.tree.heading(col, text=txt, anchor="w")
            self.tree.column(col, width=w, minwidth=80)

        # Style treeview
        style = ttk.Style()
        style.configure("Treeview", font=("Segoe UI", 10), rowheight=32)
        style.configure("Treeview.Heading", font=("Segoe UI", 10, "bold"))

        scrollbar = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        self.tree.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

        self.tree.bind("<Double-1>", self.edit)
        self.load()

    def load(self):
        for i in self.tree.get_children():
            self.tree.delete(i)
        for row in db.siswa_all():
            kelas_nama = "-"
            rows = db.q("SELECT nama FROM kelas WHERE id=?", (row["kelas_id"],))
            if rows:
                kelas_nama = rows[0]["nama"]
            self.tree.insert("", "end", iid=str(row["id"]),
                           values=(row["nis"], row["nama"], kelas_nama,
                                  row["no_hp_ortu"] or "-", row["tanggal_lahir"] or "-"))

    def add(self):
        SiswaForm(self.parent, on_save=self._after_save).build()

    def edit(self, event):
        sel = self.tree.selection()
        if not sel:
            return
        record_id = int(sel[0])
        rec = db.siswa_get(record_id)
        if rec:
            SiswaForm(self.parent, record=rec, on_save=self._after_save).build()

    def _after_save(self):
        self.load()

    def do_import(self):
        path = filedialog.askopenfilename(filetypes=[("CSV", "*.csv")])
        if not path:
            return
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
                kr = db.q("SELECT id FROM kelas WHERE nama=?", (kelas_nama,))
                if not kr:
                    errors.append(f"Baris {i}: kelas '{kelas_nama}' tidak ditemukan")
                    continue
                seen_nis.add(nis)
                rows.append((nis, nama, kr[0]["id"], r.get("Alamat", ""),
                            r.get("No HP Ortu", ""), r.get("Tanggal Lahir", "")))
        if errors:
            msg = "\n".join(errors[:20])
            if not messagebox.askyesno("Error Import",
                                       f"{len(errors)} error ditemukan:\n{msg}\n\n"
                                       f"Lanjutkan {len(rows)} baris valid?"):
                return
        if not messagebox.askyesno("Konfirmasi", f"Import {len(rows)} siswa?"):
            return
        try:
            with db.tx() as conn:
                for nr in rows:
                    conn.execute("INSERT OR IGNORE INTO siswa "
                               "(nis, nama, kelas_id, alamat, no_hp_ortu, tanggal_lahir) "
                               "VALUES (?,?,?,?,?,?)", nr)
                conn.commit()
            messagebox.showinfo("Sukses", f"Import {len(rows)} siswa selesai")
            self.load()
        except Exception as e:
            messagebox.showerror("Error", f"Gagal import: {e}")

    def do_export(self):
        path = filedialog.asksaveasfilename(defaultextension=".csv",
                                            initialfile="siswa.csv")
        if not path:
            return
        try:
            with open(path, "w", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                w.writerow(["NIS", "Nama", "Kelas ID", "Alamat", "No HP Ortu", "Tanggal Lahir"])
                for row in db.siswa_all():
                    w.writerow([row["nis"], row["nama"], row["kelas_id"],
                              row["alamat"], row["no_hp_ortu"], row["tanggal_lahir"]])
            messagebox.showinfo("Sukses", f"Export ke {path}")
        except Exception as e:
            messagebox.showerror("Error", str(e))


class SiswaForm:
    def __init__(self, parent, record=None, on_save=None):
        self.parent = parent
        self.record = record
        self.on_save = on_save

    def build(self):
        top = tk.Toplevel(self.parent.winfo_toplevel())
        top.title("Edit Siswa" if self.record else "Tambah Siswa")
        center_window(top, 420, 480)
        top.configure(bg=C["card"])
        top.resizable(False, False)

        # Header
        hdr = tk.Frame(top, bg=C["accent"])
        hdr.pack(fill="x")
        title = "Edit Data Siswa" if self.record else "Tambah Siswa Baru"
        tk.Label(hdr, text=title, font=("Segoe UI", 12, "bold"),
                 fg="white", bg=C["accent"]).pack(padx=16, pady=10, anchor="w")

        frm = tk.Frame(top, bg=C["card"])
        frm.pack(fill="both", expand=True, padx=16, pady=12)

        def make_field(label_text, default=""):
            tk.Label(frm, text=label_text, font=("Segoe UI", 9),
                     fg=C["text2"], bg=C["card"]).pack(anchor="w", pady=(8, 0))
            e = tk.Entry(frm, font=("Segoe UI", 11), bg=C["input_bg"],
                        relief="flat", highlightthickness=1,
                        highlightbackground=C["border"], highlightcolor=C["accent"])
            e.pack(fill="x", ipady=4, pady=(2, 0))
            e.insert(0, default or "")
            return e

        self.nis_e = make_field("NIS *", self.record["nis"] if self.record else "")
        self.nama_e = make_field("Nama Lengkap *", self.record["nama"] if self.record else "")

        # Kelas dropdown instead of raw ID input
        tk.Label(frm, text="Kelas", font=("Segoe UI", 9),
                 fg=C["text2"], bg=C["card"]).pack(anchor="w", pady=(8, 0))
        kelas_list = db.kelas_all()
        kelas_names = [k["nama"] for k in kelas_list]
        self.kelas_map = {k["nama"]: k["id"] for k in kelas_list}
        self.kelas_cb = ttk.Combobox(frm, values=kelas_names, state="readonly",
                                     font=("Segoe UI", 10))
        self.kelas_cb.pack(fill="x", pady=(2, 0))
        if self.record and self.record.get("kelas_id"):
            for name, kid in self.kelas_map.items():
                if kid == self.record["kelas_id"]:
                    self.kelas_cb.set(name)
                    break

        self.alamat_e = make_field("Alamat", self.record["alamat"] if self.record else "")
        self.hp_e = make_field("No HP Orang Tua", self.record["no_hp_ortu"] if self.record else "")
        self.tgl_e = make_field("Tanggal Lahir (yyyy-mm-dd)",
                                self.record["tanggal_lahir"] if self.record else "")

        # Buttons
        btn_frame = tk.Frame(frm, bg=C["card"])
        btn_frame.pack(fill="x", pady=(16, 0))

        tk.Button(btn_frame, text="Batal", font=("Segoe UI", 10),
                 bg=C["border"], fg=C["text"], relief="flat", cursor="hand2",
                 padx=12, pady=6, command=top.destroy).pack(side="left")
        tk.Button(btn_frame, text="Simpan", font=("Segoe UI", 10, "bold"),
                 bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=6, command=lambda: self._save(top)).pack(side="right")

    def _save(self, top):
        nis = self.nis_e.get().strip()
        nama = self.nama_e.get().strip()
        if not nis or not nama:
            messagebox.showwarning("Validasi", "NIS & Nama wajib diisi")
            return
        kelas_nama = self.kelas_cb.get()
        kid = self.kelas_map.get(kelas_nama, 0)
        if self.record:
            db.siswa_update(self.record["id"], nis, nama, kid,
                           self.record.get("foto", ""), self.alamat_e.get(),
                           self.hp_e.get(), self.tgl_e.get())
        else:
            db.siswa_insert(nis, nama, kid, None, self.alamat_e.get(),
                           self.hp_e.get(), self.tgl_e.get())
        if self.on_save:
            self.on_save()
        top.destroy()
