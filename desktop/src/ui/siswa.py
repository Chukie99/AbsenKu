"""
ui/siswa.py — CRUD table with soft-delete, QR code generation, import CSV (with validation & progress), export.
Centered modals, consistent styling.
"""
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import db_manager as db
import csv, os, json

# QR generation via existing barcode_generator utils
from utils.barcode_generator import generate_qr

# ── Paths ──
_HERE = os.path.dirname(os.path.abspath(__file__))
_DESKTOP = os.path.dirname(os.path.dirname(_HERE))
_QR_DIR = os.path.join(_DESKTOP, "assets", "qr")
os.makedirs(_QR_DIR, exist_ok=True)

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
        """Import siswa from CSV with progress dialog and auto-create kelas."""
        path = filedialog.askopenfilename(
            title="Pilih File CSV",
            filetypes=[("CSV Files", "*.csv"), ("All Files", "*.*")])
        if not path:
            return

        # Show progress dialog
        prog = tk.Toplevel(self.parent.winfo_toplevel())
        prog.title("Import CSV")
        center_window(prog, 420, 180)
        prog.configure(bg=C["card"])
        prog.resizable(False, False)
        prog.grab_set()

        tk.Label(prog, text="Mengimpor data siswa...", font=("Segoe UI", 11, "bold"),
                 fg=C["text"], bg=C["card"]).pack(pady=(20, 8))
        status_lbl = tk.Label(prog, text="Membaca file...", font=("Segoe UI", 9),
                              fg=C["text2"], bg=C["card"])
        status_lbl.pack(pady=4)
        progress = ttk.Progressbar(prog, length=360, mode="determinate")
        progress.pack(pady=8, padx=20)
        count_lbl = tk.Label(prog, text="", font=("Segoe UI", 9),
                             fg=C["text2"], bg=C["card"])
        count_lbl.pack(pady=4)

        # ── Read and validate CSV ──
        imported = 0
        skipped = 0
        errors = []
        kelas_cache = {}  # nama -> id

        def _get_or_create_kelas(nama):
            if not nama:
                return None
            if nama in kelas_cache:
                return kelas_cache[nama]
            rows = db.q("SELECT id FROM kelas WHERE nama=?", (nama,))
            if rows:
                kelas_cache[nama] = rows[0]["id"]
                return rows[0]["id"]
            # Auto-create kelas
            kid = db.kelas_insert(nama, "", "")
            kelas_cache[nama] = kid
            return kid

        try:
            with open(path, newline="", encoding="utf-8-sig") as f:
                reader = csv.DictReader(f)
                # Normalize header names (strip whitespace, lowercase)
                if reader.fieldnames:
                    reader.fieldnames = [h.strip().lower() for h in reader.fieldnames]

                # Map flexible header names
                HEADER_MAP = {
                    "nis": "nis", "nisn": "nis", "no_induk": "nis",
                    "nama": "nama", "nama_lengkap": "nama", "nama_siswa": "nama",
                    "kelas": "kelas", "kelas_nama": "kelas", "nama_kelas": "kelas", "class": "kelas",
                    "alamat": "alamat", "address": "alamat",
                    "no_hp_ortu": "no_hp_ortu", "no_hp": "no_hp_ortu", "hp_ortu": "no_hp_ortu",
                    "telepon_ortu": "no_hp_ortu",
                    "tanggal_lahir": "tanggal_lahir", "tgl_lahir": "tanggal_lahir", "dob": "tanggal_lahir",
                }

                def _map_col(h):
                    return HEADER_MAP.get(h, h)

                all_rows = []
                seen_nis = set()
                for i, r in enumerate(reader, 2):
                    # Remap keys
                    mapped = {}
                    for k, v in r.items():
                        mapped[_map_col(k)] = (v or "").strip()

                    nis = mapped.get("nis", "")
                    nama = mapped.get("nama", "")
                    kelas_nama = mapped.get("kelas", "")
                    alamat = mapped.get("alamat", "")
                    no_hp = mapped.get("no_hp_ortu", "")
                    tgl = mapped.get("tanggal_lahir", "")

                    if not nis or not nama:
                        errors.append(f"Baris {i}: NIS/Nama kosong, dilewati")
                        skipped += 1
                        continue
                    if nis in seen_nis:
                        errors.append(f"Baris {i}: NIS duplikat '{nis}', dilewati")
                        skipped += 1
                        continue
                    # Check if NIS already exists in DB
                    existing = db.q("SELECT id FROM siswa WHERE nis=? AND is_active=1", (nis,))
                    if existing:
                        errors.append(f"Baris {i}: NIS '{nis}' sudah ada di database, dilewati")
                        skipped += 1
                        continue

                    seen_nis.add(nis)
                    all_rows.append((nis, nama, kelas_nama, alamat, no_hp, tgl))

                total = len(all_rows)
                status_lbl.config(text=f"Memproses {total} baris...")
                prog.update()

                # ── Insert rows with progress ──
                if total > 0:
                    with db.tx() as conn:
                        for idx, (nis, nama, kelas_nama, alamat, no_hp, tgl) in enumerate(all_rows):
                            kid = _get_or_create_kelas(kelas_nama)
                            try:
                                conn.execute(
                                    "INSERT OR IGNORE INTO siswa "
                                    "(nis, nama, kelas_id, alamat, no_hp_ortu, tanggal_lahir) "
                                    "VALUES (?,?,?,?,?,?)",
                                    (nis, nama, kid, alamat, no_hp, tgl))
                                imported += 1
                            except Exception as e:
                                errors.append(f"NIS '{nis}': {e}")
                                skipped += 1
                            # Update progress
                            pct = ((idx + 1) / total) * 100
                            progress["value"] = pct
                            count_lbl.config(text=f"{idx+1} / {total} baris")
                            prog.update()

            # ── Report ──
            status_lbl.config(text="Selesai!")
            count_lbl.config(
                text=f"✓ {imported} diimpor  ⚠ {skipped} dilewati  ✗ {len(errors)} error")
            prog.update()

            # Show detail in a closeable dialog
            tk.Button(prog, text="Tutup", font=("Segoe UI", 10, "bold"),
                     bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                     padx=16, pady=6, command=prog.destroy).pack(pady=12)

            if imported > 0:
                self.load()

            # Show errors if any
            if errors:
                detail_win = tk.Toplevel(prog)
                detail_win.title("Detail Import")
                center_window(detail_win, 500, 320)
                detail_win.configure(bg=C["card"])
                tk.Label(detail_win, text="Detail:", font=("Segoe UI", 10, "bold"),
                         fg=C["text"], bg=C["card"]).pack(anchor="w", padx=12, pady=(8, 4))
                txt = tk.Text(detail_win, font=("Consolas", 9), wrap="word",
                              bg=C["input_bg"], relief="flat", padx=8, pady=8)
                txt.pack(fill="both", expand=True, padx=12, pady=(0, 8))
                for err in errors:
                    txt.insert("end", f"• {err}\n")
                txt.config(state="disabled")

        except Exception as e:
            prog.destroy()
            messagebox.showerror("Error Import", f"Gagal membaca file:\n{e}")

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
        self._qr_photo = None  # prevent GC of PhotoImage

    def build(self):
        top = tk.Toplevel(self.parent.winfo_toplevel())
        top.title("Edit Siswa" if self.record else "Tambah Siswa")
        center_window(top, 460, 560)
        top.configure(bg=C["card"])
        top.resizable(False, False)

        # Header
        hdr = tk.Frame(top, bg=C["accent"])
        hdr.pack(fill="x")
        title = "Edit Data Siswa" if self.record else "Tambah Siswa Baru"
        tk.Label(hdr, text=title, font=("Segoe UI", 12, "bold"),
                 fg="white", bg=C["accent"]).pack(padx=16, pady=10, anchor="w")

        # Scrollable frame for the form
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

        # ── QR Code Section (only when editing existing record) ──
        if self.record:
            qr_frame = tk.Frame(frm, bg=C["card"])
            qr_frame.pack(fill="x", pady=(12, 0))

            tk.Label(qr_frame, text="QR Code", font=("Segoe UI", 9, "bold"),
                     fg=C["text"], bg=C["card"]).pack(anchor="w")

            qr_row = tk.Frame(qr_frame, bg=C["card"])
            qr_row.pack(fill="x", pady=(4, 0))

            # QR image placeholder
            self.qr_lbl = tk.Label(qr_row, bg=C["input_bg"], text="Belum ada QR",
                                    font=("Segoe UI", 9), fg=C["text2"],
                                    width=18, height=10, relief="groove")
            self.qr_lbl.pack(side="left", padx=(0, 12))

            # Show existing QR if available
            qr_path = self.record.get("qr_code", "")
            if qr_path and os.path.exists(qr_path):
                self._show_qr_image(qr_path)
            elif qr_path and not os.path.exists(qr_path):
                self.qr_lbl.config(text="File QR tidak\nditemukan")

            # Buttons column
            qr_btn_frame = tk.Frame(qr_row, bg=C["card"])
            qr_btn_frame.pack(side="left", fill="y")

            tk.Button(qr_btn_frame, text="🔄 Generate QR",
                     font=("Segoe UI", 9, "bold"), bg=C["accent"], fg="white",
                     relief="flat", cursor="hand2", padx=10, pady=4,
                     command=lambda: self._generate_qr(top)).pack(anchor="w", pady=(0, 4))

            tk.Button(qr_btn_frame, text="💾 Simpan & Generate",
                     font=("Segoe UI", 9), bg=C["green"], fg="white",
                     relief="flat", cursor="hand2", padx=10, pady=4,
                     command=lambda: self._save_and_generate_qr(top)).pack(anchor="w", pady=(0, 4))

            # QR info
            qr_info = tk.Label(qr_btn_frame,
                              text="QR berisi: ID, NIS, Nama, Kelas",
                              font=("Segoe UI", 8), fg=C["text2"], bg=C["card"])
            qr_info.pack(anchor="w")

        # Buttons
        btn_frame = tk.Frame(frm, bg=C["card"])
        btn_frame.pack(fill="x", pady=(16, 0))

        tk.Button(btn_frame, text="Batal", font=("Segoe UI", 10),
                 bg=C["border"], fg=C["text"], relief="flat", cursor="hand2",
                 padx=12, pady=6, command=top.destroy).pack(side="left")
        tk.Button(btn_frame, text="Simpan", font=("Segoe UI", 10, "bold"),
                 bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=6, command=lambda: self._save(top)).pack(side="right")

    def _generate_qr(self, top):
        """Generate QR code for the current student record."""
        if not self.record:
            return
        nis = self.record["nis"]
        kelas_nama = self.kelas_cb.get() or "-"
        qr_data = json.dumps({
            "siswa_id": self.record["id"],
            "nis": nis,
            "nama": self.record["nama"],
            "kelas": kelas_nama,
        }, ensure_ascii=False)

        qr_path = os.path.join(_QR_DIR, f"{nis}.png")
        try:
            generate_qr(qr_data, qr_path)
            # Save path to DB
            db.siswa_update_qr(self.record["id"], qr_path)
            self._show_qr_image(qr_path)
            messagebox.showinfo("QR Code", f"QR tersimpan:\n{qr_path}", parent=top)
        except Exception as e:
            messagebox.showerror("Error QR", f"Gagal generate QR:\n{e}", parent=top)

    def _save_and_generate_qr(self, top):
        """Save current form data first, then generate QR."""
        # Save the record first
        if not self.record:
            return
        nis = self.nis_e.get().strip()
        nama = self.nama_e.get().strip()
        if not nis or not nama:
            messagebox.showwarning("Validasi", "NIS & Nama wajib diisi", parent=top)
            return
        kelas_nama = self.kelas_cb.get()
        kid = self.kelas_map.get(kelas_nama, 0)
        db.siswa_update(self.record["id"], nis, nama, kid,
                       self.record.get("foto", ""), self.alamat_e.get(),
                       self.hp_e.get(), self.tgl_e.get())
        # Refresh record
        self.record = db.siswa_get(self.record["id"])
        # Generate QR
        self._generate_qr(top)

    def _show_qr_image(self, path):
        """Display QR image in the label."""
        try:
            from PIL import Image, ImageTk
            img = Image.open(path)
            img = img.resize((150, 150), Image.LANCZOS)
            self._qr_photo = ImageTk.PhotoImage(img)
            self.qr_lbl.config(image=self._qr_photo, text="", width=150, height=150)
        except Exception:
            self.qr_lbl.config(text="Gagal load\ngambar QR")

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
