"""
ui/batch_print.py — Batch print screen.

Print multiple items in one go:
  - Batch name tags (per kelas or all students) — PDF
  - Batch rapor (per kelas) — PDF
  - Batch QR code (per kelas) — ZIP of PNGs
  - Batch name tag v2 with progress and direct OS print

Uses utils/pdf_generator.py and utils/barcode_generator.py.
"""
import os, sys
import zipfile
import tempfile
import subprocess
import platform
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(here))

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import pdf_generator, barcode_generator
from PIL import Image
import json

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
    "purple": "#7C3AED",
    "purple_light": "#F5F3FF",
    "gold": "#F59E0B",
    "gold_light": "#FFFBEB",
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "input_bg": "#F1F5F9",
    "tab_active": "#2563EB",
    "tab_inactive": "#E2E8F0",
}

_HERE = os.path.dirname(os.path.abspath(__file__))
_DESKTOP = os.path.dirname(os.path.dirname(_HERE))
_QR_DIR = os.path.join(_DESKTOP, "assets", "qr")
_BC_DIR = os.path.join(_DESKTOP, "assets", "barcode")
os.makedirs(_QR_DIR, exist_ok=True)
os.makedirs(_BC_DIR, exist_ok=True)


class Batch_print:
    def __init__(self, parent):
        self.parent = parent
        self._mode = "nametag"
        self._selected_ids = set()  # siswa_id set
        self.tree_preview = None
        self.preview_data = []

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # ── Header ──
        hdr = tk.Frame(p, bg=C["purple"], height=70)
        hdr.pack(fill="x")
        hdr.pack_propagate(False)
        tk.Label(hdr, text="🖨️  Batch Print",
                font=("Segoe UI", 18, "bold"), fg="white",
                bg=C["purple"]).pack(anchor="w", padx=20, pady=14)

        # ── Mode tabs ──
        mode_frame = tk.Frame(p, bg=C["bg"])
        mode_frame.pack(fill="x", padx=20, pady=(12, 0))
        self._mode_btns = {}
        for key, label in [
            ("nametag", "🏷️  Name Tag / ID Card"),
            ("rapor", "📝  Rapor Siswa"),
            ("qrcode", "🔲  QR Code"),
            ("barcode", "📊  Barcode"),
        ]:
            btn = tk.Button(mode_frame, text=label,
                          font=("Segoe UI", 10, "bold"),
                          relief="flat", padx=14, pady=6, cursor="hand2",
                          command=lambda k=key: self._switch_mode(k))
            btn.pack(side="left", padx=(0, 4))
            self._mode_btns[key] = btn

        sep = tk.Frame(p, bg=C["border"], height=1)
        sep.pack(fill="x", padx=20, pady=(8, 0))

        # ── Body (split: left filters, right preview) ──
        body = tk.Frame(p, bg=C["bg"])
        body.pack(fill="both", expand=True, padx=20, pady=(8, 8))

        body.columnconfigure(0, weight=1)
        body.columnconfigure(1, weight=2)
        body.rowconfigure(0, weight=1)

        # LEFT: Filter & actions
        left = tk.Frame(body, bg=C["card"], relief="flat",
                       highlightbackground=C["border"], highlightthickness=1)
        left.grid(row=0, column=0, sticky="nsew", padx=(0, 4))

        self._left_inner = tk.Frame(left, bg=C["card"])
        self._left_inner.pack(fill="both", expand=True, padx=16, pady=16)

        # RIGHT: Preview list
        right = tk.Frame(body, bg=C["card"], relief="flat",
                        highlightbackground=C["border"], highlightthickness=1)
        right.grid(row=0, column=1, sticky="nsew", padx=(4, 0))

        right_inner = tk.Frame(right, bg=C["card"])
        right_inner.pack(fill="both", expand=True, padx=16, pady=16)

        tk.Label(right_inner, text="📋 Preview Siswa Terpilih",
                font=("Segoe UI", 12, "bold"),
                fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(0, 8))

        # Build the right-side preview list (Treeview)
        self._build_preview_tree(right_inner)

        self._switch_mode(self._mode)

    def _switch_mode(self, mode):
        self._mode = mode
        for key, btn in self._mode_btns.items():
            if key == mode:
                btn.config(bg=C["tab_active"], fg="white",
                          activebackground=C["tab_active"])
            else:
                btn.config(bg=C["tab_inactive"], fg=C["text"],
                          activebackground=C["tab_inactive"])

        # Rebuild left panel
        for c in self._left_inner.winfo_children():
            c.destroy()

        # Mode-specific content
        if mode == "nametag":
            self._build_nametag_panel()
        elif mode == "rapor":
            self._build_rapor_panel()
        elif mode == "qrcode":
            self._build_qrcode_panel()
        elif mode == "barcode":
            self._build_barcode_panel()

    def _build_preview_tree(self, parent):
        """Build the right-side preview tree."""
        table_frame = tk.Frame(parent, bg=C["card"])
        table_frame.pack(fill="both", expand=True)

        cols = ("select", "nis", "nama", "kelas")
        self.tree_preview = ttk.Treeview(table_frame, columns=cols,
                                        show="headings", height=14)
        for col, txt, w in [
            ("select", "✓", 36),
            ("nis", "NIS", 100),
            ("nama", "Nama", 200),
            ("kelas", "Kelas", 100),
        ]:
            self.tree_preview.heading(col, text=txt, anchor="w")
            self.tree_preview.column(col, width=w, minwidth=60, anchor="w")

        sb = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree_preview.yview)
        self.tree_preview.configure(yscrollcommand=sb.set)
        self.tree_preview.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

        self.tree_preview.tag_configure("selected", background="#EFF6FF")

    def _refresh_preview(self):
        """Refresh preview tree from selected siswa ids."""
        if not self.tree_preview:
            return
        for i in self.tree_preview.get_children():
            self.tree_preview.delete(i)

        self.preview_data = []
        kw = {k["id"]: k["nama"] for k in db.kelas_all()}
        for sid in self._selected_ids:
            s = db.siswa_get(sid)
            if not s:
                continue
            self.preview_data.append(s)
            self.tree_preview.insert("", "end", iid=str(sid),
                                    values=("✓", s["nis"], s["nama"],
                                           kw.get(s.get("kelas_id"), "-")),
                                    tags=("selected",))

        # Update action label
        for w in self._left_inner.winfo_children():
            pass  # we'll update count label via _update_count

    def _update_count(self):
        if hasattr(self, "_count_lbl") and self._count_lbl.winfo_exists():
            self._count_lbl.config(
                text=f"{len(self._selected_ids)} siswa dipilih")

    def _select_all_in_kelas(self, kelas_id):
        for s in db.siswa_by_kelas(kelas_id):
            self._selected_ids.add(s["id"])
        self._refresh_preview()
        self._update_count()

    def _clear_selection(self):
        self._selected_ids.clear()
        self._refresh_preview()
        self._update_count()

    # ── Panel: Name Tag ──
    def _build_nametag_panel(self):
        inner = self._left_inner

        tk.Label(inner, text="🏷️ Cetak Name Tag / ID Card",
                font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(0, 12))

        # Filter by kelas
        tk.Label(inner, text="Pilih Siswa per Kelas:",
                font=("Segoe UI", 10, "bold"),
                fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(8, 4))

        kelas_list = db.kelas_all()
        if not kelas_list:
            tk.Label(inner, text="Belum ada data kelas",
                    font=("Segoe UI", 10), fg=C["red"], bg=C["card"]).pack(pady=8)
            return

        # Quick selection buttons per kelas
        sel_frame = tk.Frame(inner, bg=C["card"])
        sel_frame.pack(fill="x", pady=4)

        for k in kelas_list:
            row = tk.Frame(sel_frame, bg=C["card"])
            row.pack(fill="x", pady=2)
            tk.Label(row, text=k["nama"], font=("Segoe UI", 10),
                    fg=C["text"], bg=C["card"], width=18, anchor="w").pack(side="left")
            tk.Button(row, text="Pilih Semua", font=("Segoe UI", 9),
                     bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                     padx=8, pady=2,
                     command=lambda kid=k["id"]: self._select_all_in_kelas(kid)).pack(side="right", padx=2)

        # Select all button
        ctrl_row = tk.Frame(inner, bg=C["card"])
        ctrl_row.pack(fill="x", pady=(8, 4))
        tk.Button(ctrl_row, text="Pilih Semua Siswa", font=("Segoe UI", 9, "bold"),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3,
                 command=self._select_all).pack(side="left", padx=2)
        tk.Button(ctrl_row, text="Hapus Pilihan", font=("Segoe UI", 9),
                 bg=C["text2"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3,
                 command=self._clear_selection).pack(side="left", padx=4)

        self._count_lbl = tk.Label(inner, text="0 siswa dipilih",
                                  font=("Segoe UI", 10, "bold"),
                                  fg=C["accent"], bg=C["card"])
        self._count_lbl.pack(anchor="w", pady=(4, 12))

        # Action buttons
        sep = tk.Frame(inner, bg=C["border"], height=1)
        sep.pack(fill="x", pady=8)

        tk.Label(inner, text="Generate PDF:",
                font=("Segoe UI", 10, "bold"),
                fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(4, 6))

        tk.Button(inner, text="🖨️  Generate & Print Name Tags",
                 font=("Segoe UI", 11, "bold"),
                 bg=C["purple"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=8, fill="x",
                 command=self._do_batch_nametag).pack(pady=2, fill="x")
        tk.Button(inner, text="👁️  Generate PDF Saja",
                 font=("Segoe UI", 10),
                 bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=6, fill="x",
                 command=self._do_batch_nametag_only_pdf).pack(pady=2, fill="x")

    def _select_all(self):
        for s in db.siswa_all():
            self._selected_ids.add(s["id"])
        self._refresh_preview()
        self._update_count()

    def _do_batch_nametag(self):
        if not self._selected_ids:
            messagebox.showwarning("Validasi", "Pilih siswa terlebih dahulu!")
            return
        path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            initialfile=f"nametag_batch_{len(self._selected_ids)}.pdf")
        if not path:
            return
        try:
            cards = self._build_nametag_cards()
            pdf_generator.generate_name_tag_pdf(path, cards)
            messagebox.showinfo("Sukses",
                f"Berhasil generate {len(cards)} name tag:\n{path}\n\n"
                f"Untuk print: buka file PDF lalu tekan Ctrl+P",
                parent=self.parent)
        except Exception as e:
            messagebox.showerror("Error", f"Gagal generate name tag:\n{e}")

    def _do_batch_nametag_only_pdf(self):
        """Same as above but explicitly just for PDF."""
        self._do_batch_nametag()

    def _build_nametag_cards(self):
        kw = {k["id"]: k["nama"] for k in db.kelas_all()}
        cards = []
        for sid in self._selected_ids:
            s = db.siswa_get(sid)
            if not s:
                continue
            bc_path = os.path.join(_BC_DIR, f"{s['nis']}.png")
            try:
                barcode_generator.generate_code128(s["nis"], bc_path)
            except Exception:
                bc_path = None
            cards.append({
                "nama": s["nama"],
                "nis": s["nis"],
                "kelas": kw.get(s.get("kelas_id"), "-"),
                "foto": s.get("foto"),
                "barcode": bc_path if bc_path and os.path.exists(bc_path) else None,
            })
        return cards

    # ── Panel: Rapor ──
    def _build_rapor_panel(self):
        inner = self._left_inner

        tk.Label(inner, text="📝 Cetak Rapor Massal",
                font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(0, 12))

        tk.Label(inner, text="Pilih siswa per kelas untuk cetak rapor sekaligus:",
                font=("Segoe UI", 9), fg=C["text2"],
                bg=C["card"], wraplength=240, justify="left").pack(anchor="w", pady=(0, 8))

        kelas_list = db.kelas_all()
        if not kelas_list:
            tk.Label(inner, text="Belum ada data kelas",
                    font=("Segoe UI", 10), fg=C["red"], bg=C["card"]).pack(pady=8)
            return

        sel_frame = tk.Frame(inner, bg=C["card"])
        sel_frame.pack(fill="x", pady=4)

        for k in kelas_list:
            row = tk.Frame(sel_frame, bg=C["card"])
            row.pack(fill="x", pady=2)
            tk.Label(row, text=k["nama"], font=("Segoe UI", 10),
                    fg=C["text"], bg=C["card"], width=18, anchor="w").pack(side="left")
            tk.Button(row, text="Pilih Semua", font=("Segoe UI", 9),
                     bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                     padx=8, pady=2,
                     command=lambda kid=k["id"]: self._select_all_in_kelas(kid)).pack(side="right", padx=2)

        ctrl_row = tk.Frame(inner, bg=C["card"])
        ctrl_row.pack(fill="x", pady=(8, 4))
        tk.Button(ctrl_row, text="Pilih Semua", font=("Segoe UI", 9, "bold"),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3, command=self._select_all).pack(side="left", padx=2)
        tk.Button(ctrl_row, text="Hapus", font=("Segoe UI", 9),
                 bg=C["text2"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3, command=self._clear_selection).pack(side="left", padx=4)

        self._count_lbl = tk.Label(inner, text="0 siswa dipilih",
                                  font=("Segoe UI", 10, "bold"),
                                  fg=C["accent"], bg=C["card"])
        self._count_lbl.pack(anchor="w", pady=(4, 12))

        sep = tk.Frame(inner, bg=C["border"], height=1)
        sep.pack(fill="x", pady=8)

        tk.Button(inner, text="📄  Generate Rapor PDF (Semua)",
                 font=("Segoe UI", 11, "bold"),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=8, fill="x",
                 command=self._do_batch_rapor).pack(pady=2, fill="x")

    def _do_batch_rapor(self):
        if not self._selected_ids:
            messagebox.showwarning("Validasi", "Pilih siswa terlebih dahulu!")
            return
        path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            initialfile=f"rapor_batch_{len(self._selected_ids)}.pdf")
        if not path:
            return
        try:
            from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
            from reportlab.lib.pagesizes import A4
            from reportlab.lib.units import mm
            from reportlab.lib import colors
            from reportlab.lib.styles import getSampleStyleSheet

            doc = SimpleDocTemplate(path, pagesize=A4,
                                   leftMargin=20, rightMargin=20,
                                   topMargin=20, bottomMargin=20)
            story = []
            st = getSampleStyleSheet()

            kw = {k["id"]: k["nama"] for k in db.kelas_all()}

            for idx, sid in enumerate(self._selected_ids):
                s = db.siswa_get(sid)
                if not s:
                    continue

                # Rapor content
                story.append(Paragraph(f"Rapor Siswa - {s['nama']}", st["Heading1"]))
                story.append(Spacer(1, 8))
                story.append(Paragraph(f"NIS: {s['nis']}", st["BodyText"]))
                story.append(Paragraph(f"Kelas: {kw.get(s.get('kelas_id'), '-')}",
                                     st["BodyText"]))
                story.append(Paragraph(f"Tgl Lahir: {s.get('tanggal_lahir') or '-'}",
                                     st["BodyText"]))
                story.append(Spacer(1, 12))

                # Nilai
                story.append(Paragraph("<b>Nilai per Mata Pelajaran</b>",
                                     st["BodyText"]))
                nilai = db.q(
                    "SELECT m.nama AS mapel, n.nilai, n.semester "
                    "FROM nilai n JOIN mapel m ON n.mapel_id=m.id "
                    "WHERE siswa_id=? ORDER BY m.nama", (sid,))
                if nilai:
                    tdata = [["Mata Pelajaran", "Nilai", "Semester"]]
                    for n in nilai:
                        tdata.append([n["mapel"], f"{n['nilai']:.1f}" if n["nilai"] else "-",
                                    n.get("semester") or "-"])
                    t = Table(tdata, colWidths=[80*mm, 25*mm, 25*mm])
                    t.setStyle(TableStyle([
                        ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
                        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1A73E8")),
                        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                    ]))
                    story.append(t)
                else:
                    story.append(Paragraph("<i>Belum ada data nilai</i>",
                                         st["BodyText"]))
                story.append(Spacer(1, 12))

                # Absensi summary
                absen = db.absensi_by_siswa(sid)
                if absen:
                    count = {"Hadir": 0, "Izin": 0, "Sakit": 0, "Alfa": 0}
                    for a in absen:
                        if a.get("status") in count:
                            count[a["status"]] += 1
                    story.append(Paragraph("<b>Ringkasan Kehadiran</b>",
                                         st["BodyText"]))
                    story.append(Paragraph(
                        f"Hadir: {count['Hadir']} | Izin: {count['Izin']} | "
                        f"Sakit: {count['Sakit']} | Alfa: {count['Alfa']}",
                        st["BodyText"]))
                else:
                    story.append(Paragraph("<i>Belum ada data absensi</i>",
                                         st["BodyText"]))

                # Page break between students
                if idx < len(self._selected_ids) - 1:
                    story.append(PageBreak())

            doc.build(story)
            messagebox.showinfo("Sukses",
                f"Berhasil generate {len(self._selected_ids)} rapor:\n{path}")
        except Exception as e:
            messagebox.showerror("Error", f"Gagal generate rapor:\n{e}")

    # ── Panel: QR Code ──
    def _build_qrcode_panel(self):
        inner = self._left_inner

        tk.Label(inner, text="🔲 Generate QR Code Massal",
                font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(0, 12))

        tk.Label(inner, text="QR berisi: siswa_id, NIS, nama, kelas. "
                "Hasil di-pack ke ZIP berisi file PNG.",
                font=("Segoe UI", 9), fg=C["text2"],
                bg=C["card"], wraplength=240, justify="left").pack(anchor="w", pady=(0, 8))

        kelas_list = db.kelas_all()
        if not kelas_list:
            tk.Label(inner, text="Belum ada data kelas",
                    font=("Segoe UI", 10), fg=C["red"], bg=C["card"]).pack(pady=8)
            return

        sel_frame = tk.Frame(inner, bg=C["card"])
        sel_frame.pack(fill="x", pady=4)

        for k in kelas_list:
            row = tk.Frame(sel_frame, bg=C["card"])
            row.pack(fill="x", pady=2)
            tk.Label(row, text=k["nama"], font=("Segoe UI", 10),
                    fg=C["text"], bg=C["card"], width=18, anchor="w").pack(side="left")
            tk.Button(row, text="Pilih Semua", font=("Segoe UI", 9),
                     bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                     padx=8, pady=2,
                     command=lambda kid=k["id"]: self._select_all_in_kelas(kid)).pack(side="right", padx=2)

        ctrl_row = tk.Frame(inner, bg=C["card"])
        ctrl_row.pack(fill="x", pady=(8, 4))
        tk.Button(ctrl_row, text="Pilih Semua", font=("Segoe UI", 9, "bold"),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3, command=self._select_all).pack(side="left", padx=2)
        tk.Button(ctrl_row, text="Hapus", font=("Segoe UI", 9),
                 bg=C["text2"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3, command=self._clear_selection).pack(side="left", padx=4)

        self._count_lbl = tk.Label(inner, text="0 siswa dipilih",
                                  font=("Segoe UI", 10, "bold"),
                                  fg=C["accent"], bg=C["card"])
        self._count_lbl.pack(anchor="w", pady=(4, 12))

        sep = tk.Frame(inner, bg=C["border"], height=1)
        sep.pack(fill="x", pady=8)

        tk.Button(inner, text="📦  Generate QR → ZIP",
                 font=("Segoe UI", 11, "bold"),
                 bg=C["orange"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=8, fill="x",
                 command=self._do_batch_qr).pack(pady=2, fill="x")

    def _do_batch_qr(self):
        if not self._selected_ids:
            messagebox.showwarning("Validasi", "Pilih siswa terlebih dahulu!")
            return
        path = filedialog.asksaveasfilename(
            defaultextension=".zip",
            initialfile=f"qrcodes_{len(self._selected_ids)}.zip")
        if not path:
            return

        # Progress dialog
        prog = tk.Toplevel(self.parent)
        prog.title("Generate QR")
        prog.geometry("420x150")
        prog.configure(bg=C["card"])
        prog.resizable(False, False)
        prog.grab_set()

        tk.Label(prog, text="Membuat QR Code...",
                font=("Segoe UI", 11, "bold"),
                fg=C["text"], bg=C["card"]).pack(pady=(20, 8))
        progress = ttk.Progressbar(prog, length=380, mode="determinate")
        progress.pack(pady=8, padx=20)
        status_lbl = tk.Label(prog, text="0 / 0",
                            font=("Segoe UI", 9),
                            fg=C["text2"], bg=C["card"])
        status_lbl.pack(pady=4)

        kw = {k["id"]: k["nama"] for k in db.kelas_all()}
        total = len(self._selected_ids)
        progress["maximum"] = total

        try:
            with tempfile.TemporaryDirectory() as tmpdir:
                # Generate QRs
                for i, sid in enumerate(self._selected_ids, 1):
                    s = db.siswa_get(sid)
                    if not s:
                        continue
                    qr_data = json.dumps({
                        "siswa_id": s["id"],
                        "nis": s["nis"],
                        "nama": s["nama"],
                        "kelas": kw.get(s.get("kelas_id"), "-"),
                    }, ensure_ascii=False)
                    qr_filename = os.path.join(tmpdir, f"{s['nis']}.png")
                    try:
                        barcode_generator.generate_qr(qr_data, qr_filename)
                        db.siswa_update_qr(s["id"], qr_filename)
                    except Exception as ex:
                        print(f"QR error for {s['nis']}: {ex}")

                    progress["value"] = i
                    status_lbl.config(text=f"{i} / {total}")
                    prog.update()

                # Create ZIP
                status_lbl.config(text="Membuat ZIP...")
                prog.update()
                with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
                    for fname in os.listdir(tmpdir):
                        fpath = os.path.join(tmpdir, fname)
                        zf.write(fpath, arcname=fname)

            tk.Label(prog, text=f"✓ Selesai! {total} QR code di-ZIP",
                    font=("Segoe UI", 10, "bold"),
                    fg=C["green"], bg=C["card"]).pack(pady=4)
            tk.Button(prog, text="Tutup", font=("Segoe UI", 10, "bold"),
                     bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                     padx=20, pady=6,
                     command=lambda: [prog.destroy(),
                                     messagebox.showinfo("Sukses",
                                         f"{total} QR code tersimpan:\n{path}",
                                         parent=self.parent)]).pack(pady=8)
        except Exception as e:
            prog.destroy()
            messagebox.showerror("Error", f"Gagal generate QR:\n{e}")

    # ── Panel: Barcode ──
    def _build_barcode_panel(self):
        inner = self._left_inner

        tk.Label(inner, text="📊 Generate Barcode Code128",
                font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(anchor="w", pady=(0, 12))

        tk.Label(inner, text="Generate barcode Code128 dari NIS siswa. "
                "Hasil dikemas dalam ZIP berisi file PNG.",
                font=("Segoe UI", 9), fg=C["text2"],
                bg=C["card"], wraplength=240, justify="left").pack(anchor="w", pady=(0, 8))

        kelas_list = db.kelas_all()
        if not kelas_list:
            tk.Label(inner, text="Belum ada data kelas",
                    font=("Segoe UI", 10), fg=C["red"], bg=C["card"]).pack(pady=8)
            return

        sel_frame = tk.Frame(inner, bg=C["card"])
        sel_frame.pack(fill="x", pady=4)

        for k in kelas_list:
            row = tk.Frame(sel_frame, bg=C["card"])
            row.pack(fill="x", pady=2)
            tk.Label(row, text=k["nama"], font=("Segoe UI", 10),
                    fg=C["text"], bg=C["card"], width=18, anchor="w").pack(side="left")
            tk.Button(row, text="Pilih Semua", font=("Segoe UI", 9),
                     bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                     padx=8, pady=2,
                     command=lambda kid=k["id"]: self._select_all_in_kelas(kid)).pack(side="right", padx=2)

        ctrl_row = tk.Frame(inner, bg=C["card"])
        ctrl_row.pack(fill="x", pady=(8, 4))
        tk.Button(ctrl_row, text="Pilih Semua", font=("Segoe UI", 9, "bold"),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3, command=self._select_all).pack(side="left", padx=2)
        tk.Button(ctrl_row, text="Hapus", font=("Segoe UI", 9),
                 bg=C["text2"], fg="white", relief="flat", cursor="hand2",
                 padx=8, pady=3, command=self._clear_selection).pack(side="left", padx=4)

        self._count_lbl = tk.Label(inner, text="0 siswa dipilih",
                                  font=("Segoe UI", 10, "bold"),
                                  fg=C["accent"], bg=C["card"])
        self._count_lbl.pack(anchor="w", pady=(4, 12))

        sep = tk.Frame(inner, bg=C["border"], height=1)
        sep.pack(fill="x", pady=8)

        tk.Button(inner, text="📦  Generate Barcode → ZIP",
                 font=("Segoe UI", 11, "bold"),
                 bg=C["red"], fg="white", relief="flat", cursor="hand2",
                 padx=12, pady=8, fill="x",
                 command=self._do_batch_barcode).pack(pady=2, fill="x")

    def _do_batch_barcode(self):
        if not self._selected_ids:
            messagebox.showwarning("Validasi", "Pilih siswa terlebih dahulu!")
            return
        path = filedialog.asksaveasfilename(
            defaultextension=".zip",
            initialfile=f"barcodes_{len(self._selected_ids)}.zip")
        if not path:
            return

        prog = tk.Toplevel(self.parent)
        prog.title("Generate Barcode")
        prog.geometry("420x150")
        prog.configure(bg=C["card"])
        prog.resizable(False, False)
        prog.grab_set()

        tk.Label(prog, text="Membuat Barcode...",
                font=("Segoe UI", 11, "bold"),
                fg=C["text"], bg=C["card"]).pack(pady=(20, 8))
        progress = ttk.Progressbar(prog, length=380, mode="determinate")
        progress.pack(pady=8, padx=20)
        status_lbl = tk.Label(prog, text="0 / 0",
                            font=("Segoe UI", 9),
                            fg=C["text2"], bg=C["card"])
        status_lbl.pack(pady=4)

        total = len(self._selected_ids)
        progress["maximum"] = total

        try:
            with tempfile.TemporaryDirectory() as tmpdir:
                for i, sid in enumerate(self._selected_ids, 1):
                    s = db.siswa_get(sid)
                    if not s:
                        continue
                    bc_filename = os.path.join(tmpdir, f"{s['nis']}.png")
                    try:
                        barcode_generator.generate_code128(s["nis"], bc_filename)
                    except Exception as ex:
                        print(f"BC error for {s['nis']}: {ex}")

                    progress["value"] = i
                    status_lbl.config(text=f"{i} / {total}")
                    prog.update()

                status_lbl.config(text="Membuat ZIP...")
                prog.update()
                with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
                    for fname in os.listdir(tmpdir):
                        fpath = os.path.join(tmpdir, fname)
                        zf.write(fpath, arcname=fname)

            tk.Label(prog, text=f"✓ Selesai! {total} barcode di-ZIP",
                    font=("Segoe UI", 10, "bold"),
                    fg=C["green"], bg=C["card"]).pack(pady=4)
            tk.Button(prog, text="Tutup", font=("Segoe UI", 10, "bold"),
                     bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                     padx=20, pady=6,
                     command=lambda: [prog.destroy(),
                                     messagebox.showinfo("Sukses",
                                         f"{total} barcode tersimpan:\n{path}",
                                         parent=self.parent)]).pack(pady=8)
        except Exception as e:
            prog.destroy()
            messagebox.showerror("Error", f"Gagal generate barcode:\n{e}")
