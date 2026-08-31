"""
ui/pdf_generator.py — PDF report generation hub.

Unified screen to generate: attendance reports, rapor per siswa,
ranking report, and jadwal pelajaran PDF. Wraps utils/pdf_generator.py.
"""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(here))

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import db_manager as db
from utils import pdf_generator
from datetime import datetime

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
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
    "input_bg": "#F1F5F9",
}


class Pdf_generator:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # ── Header ──
        hdr = tk.Frame(p, bg=C["accent"], height=70)
        hdr.pack(fill="x")
        hdr.pack_propagate(False)
        tk.Label(hdr, text="📄  Generator PDF",
                font=("Segoe UI", 18, "bold"), fg="white",
                bg=C["accent"]).pack(anchor="w", padx=20, pady=14)

        # ── Content grid ──
        content = tk.Frame(p, bg=C["bg"])
        content.pack(fill="both", expand=True, padx=20, pady=20)

        # Title
        tk.Label(content, text="Pilih jenis laporan PDF yang ingin di-generate:",
                font=("Segoe UI", 12), fg=C["text2"],
                bg=C["bg"]).pack(anchor="w", pady=(0, 16))

        # Card grid — 2 columns
        grid = tk.Frame(content, bg=C["bg"])
        grid.pack(fill="both", expand=True)

        grid.columnconfigure(0, weight=1)
        grid.columnconfigure(1, weight=1)

        cards_data = [
            (0, 0, "📋", "Laporan Absensi", "Generate laporan absensi harian dalam format PDF",
             C["accent"], C["accent_light"], self._absensi_pdf),
            (0, 1, "📝", "Rapor Siswa", "Cetak rapor per siswa (nilai + ringkasan absensi)",
             C["green"], C["green_light"], self._rapor_pdf),
            (1, 0, "🏆", "Laporan Ranking", "Ranking siswa berdasarkan rata-rata nilai",
             C["orange"], C["orange_light"], self._ranking_pdf),
            (1, 1, "📅", "Jadwal Pelajaran", "Print jadwal pelajaran per kelas",
             C["purple"], C["purple_light"], self._jadwal_pdf),
            (2, 0, "📊", "Rekap Absensi Bulanan", "Rekap kehadiran siswa per bulan",
             C["red"], C["red_light"], self._rekap_bulanan_pdf),
            (2, 1, "🎯", "Rekap Poin Disiplin", "Laporan poin disiplin siswa",
             C["orange"], C["orange_light"], self._poin_pdf),
        ]

        for row, col, icon, title, desc, color, bg_c, cmd in cards_data:
            card = tk.Frame(grid, bg=bg_c, relief="flat",
                          highlightbackground=C["border"], highlightthickness=1,
                          cursor="hand2")
            card.grid(row=row, column=col, sticky="nsew", padx=8, pady=8)
            grid.rowconfigure(row, weight=1)

            inner = tk.Frame(card, bg=bg_c)
            inner.pack(fill="both", expand=True, padx=20, pady=20)

            # Icon + title row
            icon_lbl = tk.Label(inner, text=icon, font=("Segoe UI", 28),
                               fg=color, bg=bg_c)
            icon_lbl.pack(anchor="w")

            tk.Label(inner, text=title, font=("Segoe UI", 14, "bold"),
                    fg=C["text"], bg=bg_c).pack(anchor="w", pady=(4, 2))

            tk.Label(inner, text=desc, font=("Segoe UI", 10),
                    fg=C["text2"], bg=bg_c, wraplength=260, justify="left").pack(
                        anchor="w", pady=(0, 12))

            tk.Button(inner, text="Generate PDF →", font=("Segoe UI", 10, "bold"),
                     bg=color, fg="white", relief="flat", cursor="hand2",
                     padx=16, pady=6, command=cmd).pack(anchor="w")

    # ── Report generators ──

    def _absensi_pdf(self):
        """Generate today's attendance report PDF."""
        tgl = datetime.now().strftime("%Y-%m-%d")

        # Optional: prompt for date
        top = tk.Toplevel(self.parent)
        top.title("Laporan Absensi PDF")
        top.geometry("350x180")
        top.configure(bg=C["card"])
        top.resizable(False, False)
        top.grab_set()

        tk.Label(top, text="📅 Pilih Tanggal", font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(pady=(20, 12))

        tk.Label(top, text="Tanggal:", font=("Segoe UI", 10),
                fg=C["text2"], bg=C["card"]).pack(anchor="w", padx=20)
        date_e = tk.Entry(top, font=("Segoe UI", 11), bg=C["input_bg"],
                         relief="flat", highlightthickness=1,
                         highlightbackground=C["border"])
        date_e.pack(fill="x", padx=20, pady=(2, 8), ipady=4)
        date_e.insert(0, tgl)

        def generate():
            tgl_val = date_e.get().strip()
            path = filedialog.asksaveasfilename(
                defaultextension=".pdf",
                initialfile=f"absensi_{tgl_val}.pdf",
                parent=top)
            if not path:
                return
            try:
                rows = db.absensi_by_date(tgl_val)
                if not rows:
                    messagebox.showwarning("Info",
                        f"Tidak ada data absensi untuk tanggal {tgl_val}", parent=top)
                    return

                # Enrich rows with student names
                sw = {s["id"]: s for s in db.siswa_all()}
                mw = {m["id"]: m for m in db.mapel_all()}
                enriched = []
                for r in rows:
                    r["siswa_nama"] = sw.get(r.get("siswa_id"), {}).get("nama", "-")
                    r["mapel_nama"] = mw.get(r.get("mapel_id"), {}).get("nama", "-")
                    enriched.append(r)

                pdf_generator.export_absensi_pdf(path, enriched, tgl_val)
                messagebox.showinfo("Sukses",
                    f"PDF absensi tersimpan:\n{path}", parent=top)
                top.destroy()
            except Exception as e:
                messagebox.showerror("Error", f"Gagal generate PDF:\n{e}", parent=top)

        tk.Button(top, text="Generate PDF", font=("Segoe UI", 10, "bold"),
                 bg=C["accent"], fg="white", relief="flat", cursor="hand2",
                 padx=16, pady=6, command=generate).pack(pady=(4, 0))

    def _rapor_pdf(self):
        """Generate rapor PDF for a student by NIS."""
        top = tk.Toplevel(self.parent)
        top.title("Rapor Siswa PDF")
        top.geometry("380x200")
        top.configure(bg=C["card"])
        top.resizable(False, False)
        top.grab_set()

        tk.Label(top, text="📝 Cetak Rapor Siswa", font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(pady=(20, 12))

        tk.Label(top, text="Masukkan NIS siswa:", font=("Segoe UI", 10),
                fg=C["text2"], bg=C["card"]).pack(anchor="w", padx=20)
        nis_e = tk.Entry(top, font=("Segoe UI", 11), bg=C["input_bg"],
                        relief="flat", highlightthickness=1,
                        highlightbackground=C["border"])
        nis_e.pack(fill="x", padx=20, pady=(2, 4), ipady=4)

        # Siswa label
        info_lbl = tk.Label(top, text="", font=("Segoe UI", 9),
                           fg=C["text2"], bg=C["card"])
        info_lbl.pack(anchor="w", padx=20)

        def lookup(*a):
            nis = nis_e.get().strip()
            if nis:
                s = db.siswa_get_by_nis(nis)
                if s:
                    info_lbl.config(text=f"✓ {s['nama']}", fg=C["green"])
                else:
                    info_lbl.config(text="✗ Siswa tidak ditemukan", fg=C["red"])
            else:
                info_lbl.config(text="")

        nis_e.bind("<FocusOut>", lookup)
        nis_e.bind("<KeyRelease>", lookup)

        def generate():
            nis = nis_e.get().strip()
            if not nis:
                messagebox.showwarning("Validasi", "Masukkan NIS!", parent=top)
                return
            s = db.siswa_get_by_nis(nis)
            if not s:
                messagebox.showerror("Error", "Siswa tidak ditemukan!", parent=top)
                return
            path = filedialog.asksaveasfilename(
                defaultextension=".pdf",
                initialfile=f"rapor_{s['nama']}.pdf",
                parent=top)
            if not path:
                return
            try:
                nilai = db.q(
                    "SELECT m.nama AS mapel, n.nilai, n.semester "
                    "FROM nilai n JOIN mapel m ON n.mapel_id=m.id "
                    "WHERE siswa_id=? ORDER BY m.nama", (s["id"],))
                absen = db.absensi_by_siswa(s["id"])
                pdf_generator.generate_rapor_pdf(path, s, nilai, absen)
                messagebox.showinfo("Sukses",
                    f"Rapor tersimpan:\n{path}", parent=top)
                top.destroy()
            except Exception as e:
                messagebox.showerror("Error", f"Gagal generate rapor:\n{e}", parent=top)

        tk.Button(top, text="Generate Rapor PDF", font=("Segoe UI", 10, "bold"),
                 bg=C["green"], fg="white", relief="flat", cursor="hand2",
                 padx=16, pady=6, command=generate).pack(pady=(4, 0))

    def _ranking_pdf(self):
        """Generate ranking report PDF."""
        path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            initialfile="ranking_nilai.pdf")
        if not path:
            return
        try:
            rows = db.ranking_nilai_by_kelas()
            if not rows:
                messagebox.showwarning("Info", "Tidak ada data ranking")
                return
            # Build a generic PDF table
            headers = ["Rank", "NIS", "Nama", "Kelas", "Rata-rata", "Jml Mapel"]
            data = []
            for i, r in enumerate(rows, 1):
                data.append({
                    "rank": str(i),
                    "nis": r.get("nis", "-"),
                    "nama": r.get("nama", "-"),
                    "kelas": r.get("kelas_nama", "-"),
                    "rata_rata": f"{r.get('rata_rata', 0):.1f}",
                    "jml_mapel": str(r.get("jumlah_mapel", 0)),
                })
            pdf_generator.generate_store_report_pdf(
                path, "", data, headers,
                "Laporan Ranking Siswa - AbsenKu")
            messagebox.showinfo("Sukses", f"Ranking PDF tersimpan:\n{path}")
        except Exception as e:
            messagebox.showerror("Error", f"Gagal generate ranking PDF:\n{e}")

    def _jadwal_pdf(self):
        """Generate jadwal pelajaran PDF for a class."""
        top = tk.Toplevel(self.parent)
        top.title("Jadwal Pelajaran PDF")
        top.geometry("350x180")
        top.configure(bg=C["card"])
        top.resizable(False, False)
        top.grab_set()

        tk.Label(top, text="📅 Cetak Jadwal Pelajaran",
                font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(pady=(20, 12))

        tk.Label(top, text="Pilih Kelas:", font=("Segoe UI", 10),
                fg=C["text2"], bg=C["card"]).pack(anchor="w", padx=20)
        kelas_list = db.kelas_all()
        kelas_names = [k["nama"] for k in kelas_list]
        kelas_cb = ttk.Combobox(top, values=kelas_names, state="readonly",
                               font=("Segoe UI", 10))
        kelas_cb.pack(fill="x", padx=20, pady=(2, 12))
        if kelas_names:
            kelas_cb.set(kelas_names[0])

        def generate():
            kelas_nama = kelas_cb.get()
            if not kelas_nama:
                messagebox.showwarning("Validasi", "Pilih kelas!", parent=top)
                return
            kelas_obj = db.q("SELECT id FROM kelas WHERE nama=?", (kelas_nama,))
            if not kelas_obj:
                return
            kelas_id = kelas_obj[0]["id"]
            jadwal = db.jadwal_by_kelas(kelas_id)
            if not jadwal:
                messagebox.showwarning("Info",
                    f"Tidak ada jadwal untuk kelas {kelas_nama}", parent=top)
                return
            path = filedialog.asksaveasfilename(
                defaultextension=".pdf",
                initialfile=f"jadwal_{kelas_nama}.pdf",
                parent=top)
            if not path:
                return
            try:
                headers = ["Hari", "Mapel", "Jam", "Guru"]
                data = []
                for j in jadwal:
                    data.append({
                        "hari": j.get("hari", "-"),
                        "mapel": j.get("mapel_nama", "-"),
                        "jam": f"{j.get('jam_mulai', '?')} - {j.get('jam_selesai', '?')}",
                        "guru": j.get("guru") or "-",
                    })
                pdf_generator.generate_store_report_pdf(
                    path, "", data, headers,
                    f"Jadwal Pelajaran - {kelas_nama}")
                messagebox.showinfo("Sukses",
                    f"Jadwal PDF tersimpan:\n{path}", parent=top)
                top.destroy()
            except Exception as e:
                messagebox.showerror("Error",
                    f"Gagal generate jadwal PDF:\n{e}", parent=top)

        tk.Button(top, text="Generate PDF", font=("Segoe UI", 10, "bold"),
                 bg=C["purple"], fg="white", relief="flat", cursor="hand2",
                 padx=16, pady=6, command=generate).pack()

    def _rekap_bulanan_pdf(self):
        """Generate monthly attendance recap PDF."""
        top = tk.Toplevel(self.parent)
        top.title("Rekap Bulanan PDF")
        top.geometry("350x200")
        top.configure(bg=C["card"])
        top.resizable(False, False)
        top.grab_set()

        tk.Label(top, text="📊 Rekap Absensi Bulanan",
                font=("Segoe UI", 13, "bold"),
                fg=C["text"], bg=C["card"]).pack(pady=(20, 8))

        # Month/year selectors
        frm = tk.Frame(top, bg=C["card"])
        frm.pack(padx=20, fill="x")

        tk.Label(frm, text="Bulan:", font=("Segoe UI", 10),
                fg=C["text2"], bg=C["card"]).pack(side="left")
        bulan_var = tk.StringVar(value=datetime.now().strftime("%m"))
        ttk.Combobox(frm, textvariable=bulan_var, width=5, state="readonly",
                    values=[f"{i:02d}" for i in range(1, 13)]).pack(side="left", padx=(4, 12))

        tk.Label(frm, text="Tahun:", font=("Segoe UI", 10),
                fg=C["text2"], bg=C["card"]).pack(side="left")
        tahun_var = tk.StringVar(value=datetime.now().strftime("%Y"))
        ttk.Combobox(frm, textvariable=tahun_var, width=8, state="readonly",
                    values=[str(y) for y in range(2024, 2030)]).pack(side="left", padx=4)

        info_lbl = tk.Label(top, text="", font=("Segoe UI", 9),
                           fg=C["text2"], bg=C["card"])
        info_lbl.pack(padx=20, pady=4)

        def generate():
            bulan = bulan_var.get()
            tahun = tahun_var.get()
            prefix = f"{tahun}-{bulan}"

            # Gather all students and their attendance for the month
            all_siswa = db.siswa_all()
            all_absen = db.q(
                "SELECT * FROM absensi WHERE tanggal LIKE ?",
                (f"{prefix}%",))

            # Group by siswa_id
            att_by_siswa = {}
            for a in all_absen:
                sid = a["siswa_id"]
                if sid not in att_by_siswa:
                    att_by_siswa[sid] = {"Hadir": 0, "Izin": 0, "Sakit": 0, "Alfa": 0}
                st = a.get("status")
                if st in att_by_siswa[sid]:
                    att_by_siswa[sid][st] += 1

            headers = ["NIS", "Nama", "Kelas", "Hadir", "Izin", "Sakit", "Alfa"]
            data = []
            kw = {k["id"]: k["nama"] for k in db.kelas_all()}
            for s in all_siswa:
                att = att_by_siswa.get(s["id"], {"Hadir": 0, "Izin": 0, "Sakit": 0, "Alfa": 0})
                data.append({
                    "nis": s.get("nis", "-"),
                    "nama": s.get("nama", "-"),
                    "kelas": kw.get(s.get("kelas_id"), "-"),
                    "hadir": str(att["Hadir"]),
                    "izin": str(att["Izin"]),
                    "sakit": str(att["Sakit"]),
                    "alfa": str(att["Alfa"]),
                })

            if not data:
                messagebox.showwarning("Info",
                    "Tidak ada data siswa", parent=top)
                return

            info_lbl.config(text=f"Memproses {len(data)} siswa...")
            top.update()

            path = filedialog.asksaveasfilename(
                defaultextension=".pdf",
                initialfile=f"rekap_absen_{prefix}.pdf",
                parent=top)
            if not path:
                info_lbl.config(text="")
                return

            try:
                pdf_generator.generate_store_report_pdf(
                    path, "", data, headers,
                    f"Rekap Absensi Bulanan {prefix} - AbsenKu")
                messagebox.showinfo("Sukses",
                    f"Rekap bulanan tersimpan:\n{path}", parent=top)
                info_lbl.config(text=f"✓ Berhasil! {len(data)} siswa")
            except Exception as e:
                messagebox.showerror("Error",
                    f"Gagal generate PDF:\n{e}", parent=top)
                info_lbl.config(text="")

        tk.Button(top, text="Generate PDF", font=("Segoe UI", 10, "bold"),
                 bg=C["red"], fg="white", relief="flat", cursor="hand2",
                 padx=16, pady=6, command=generate).pack(pady=(4, 0))

    def _poin_pdf(self):
        """Generate discipline points report PDF."""
        path = filedialog.asksaveasfilename(
            defaultextension=".pdf",
            initialfile="poin_disiplin.pdf")
        if not path:
            return
        try:
            rows = db.poin_summary_by_kelas()
            if not rows:
                messagebox.showwarning("Info",
                    "Tidak ada data poin disiplin")
                return
            headers = ["Rank", "NIS", "Nama", "Kelas", "Poin +", "Poin -", "Bersih"]
            data = []
            for i, r in enumerate(rows, 1):
                data.append({
                    "rank": str(i),
                    "nis": r.get("nis", "-"),
                    "nama": r.get("nama", "-"),
                    "kelas": r.get("kelas_nama", "-"),
                    "poin_positif": f"+{r.get('poin_positif', 0)}",
                    "poin_negatif": f"-{r.get('poin_negatif', 0)}",
                    "bersih": f"{r.get('poin_net', 0):+d}",
                })
            pdf_generator.generate_store_report_pdf(
                path, "", data, headers,
                "Rekap Poin Disiplin Siswa - AbsenKu")
            messagebox.showinfo("Sukses", f"Poin disiplin PDF tersimpan:\n{path}")
        except Exception as e:
            messagebox.showerror("Error", f"Gagal generate PDF:\n{e}")
