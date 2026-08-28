"""
ui/laporan.py — laporan absen, nilai, rekap gabungan → PDF/Excel.
"""
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import excel_exporter, pdf_generator
from datetime import datetime


class Laporan:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for c in p.winfo_children(): c.destroy()
        ttk.Label(p, text="Laporan & Export", font=("Helvetica", 14, "bold")).pack(pady=12)

        btns = tk.Frame(p); btns.pack(pady=8)
        tk.Button(btns, text="Laporan Absensi (Hari Ini) → PDF", bg="#1A73E8", fg="white", command=self.absen_pdf, relief="flat", padx=10).pack(fill="x", pady=4)
        tk.Button(btns, text="Laporan Absensi (Hari Ini) → Excel", bg="#34A853", fg="white", command=self.absen_xlsx, relief="flat", padx=10).pack(fill="x", pady=4)
        tk.Button(btns, text="Laporan Nilai → Excel", bg="#FBBC04", fg="black", command=self.nilai_xlsx, relief="flat", padx=10).pack(fill="x", pady=4)
        tk.Button(btns, text="Rapor Siswa → PDF", bg="#D93025", fg="white", command=self.rapor_pdf, relief="flat", padx=10).pack(fill="x", pady=4)

        ttk.Label(p, text="Export riwayat ke: /home atau pilih folder", font=("Helvetica", 9), foreground="#5F6368").pack(pady=12)

    def _today(self):
        return datetime.now().strftime("%Y-%m-%d")

    def absen_pdf(self):
        tgl = self._today()
        rows = db.absensi_by_date(tgl)
        path = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"absen_{tgl}.pdf")
        if not path: return
        pdf_generator.export_absensi_pdf(path, rows, tgl)
        messagebox.showinfo("Sukses", f"Export ke {path}")

    def absen_xlsx(self):
        rows = db.absensi_by_date(self._today())
        path = filedialog.asksaveasfilename(defaultextension=".xlsx", initialfile="absen.xlsx")
        if not path: return
        excel_exporter.export_absensi_xlsx(path, rows)
        messagebox.showinfo("Sukses", f"Export ke {path}")

    def nilai_xlsx(self):
        rows = db.q("SELECT s.nama,s.nis,m.nama AS mapel_nama,n.* FROM nilai n JOIN siswa s ON n.siswa_id=s.id JOIN mapel m ON n.mapel_id=m.id ORDER BY s.nama")
        path = filedialog.asksaveasfilename(defaultextension=".xlsx", initialfile="nilai.xlsx")
        if not path: return
        excel_exporter.export_nilai_xlsx(path, rows)
        messagebox.showinfo("Sukses", f"Export ke {path}")

    def rapor_pdf(self):
        nis = tk.simpledialog.askstring("Rapor", "Masukkan NIS:")
        if not nis: return
        s = db.siswa_get_by_nis(nis)
        if not s:
            messagebox.showerror("Error", "Siswa tidak ditemukan"); return
        path = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"rapor_{nis}.pdf")
        if not path: return
        nilar = db.q("SELECT m.nama AS mapel, n.nilai, n.semester FROM nilai n JOIN mapel m ON n.mapel_id=m.id WHERE siswa_id=?", (s["id"],))
        absen = db.absensi_by_date(self._today())
        pdf_generator.generate_rapor_pdf(path, s, nilar, absen)
        messagebox.showinfo("Sukses", f"Export ke {path}")
