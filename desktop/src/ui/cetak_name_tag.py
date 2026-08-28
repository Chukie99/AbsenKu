"""
ui/cetak_name_tag.py — generate + print name tags (54x86mm) with student photo + barcode.
"""
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
from utils import barcode_generator, image_compressor
from utils import pdf_generator
import db_manager as db
from PIL import Image


class Cetak_name_tag:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for c in p.winfo_children(): c.destroy()
        ttk.Label(p, text="Cetak Name Tag / ID Card", font=("Helvetica", 14, "bold")).pack(pady=12)

        tf = tk.Frame(p); tf.pack(fill="x", pady=6)
        tk.Label(tf, text="Masukkan NIS (pisahkan koma untuk banyak):").pack(side="left")
        self.nis_e = tk.Entry(tf, width=40); self.nis_e.pack(side="left", padx=6)
        tk.Button(tf, text="Cetak", bg="#1A73E8", fg="white", command=self.cetak, relief="flat", padx=10, pady=4).pack(side="left")

        tk.Label(p, text="Preview name tag (54mm x 86mm) — print to ID card printer atau PDF", font=("Helvetica", 10), fg="#5F6368").pack(pady=8)
        self.canvas = tk.Canvas(p, width=280, height=440, bg="white", highlightthickness=1)
        self.canvas.pack(pady=8)

    def cetak(self):
        nis_list = [n.strip() for n in self.nis_e.get().split(",") if n.strip()]
        if not nis_list:
            messagebox.showwarning("Validasi", "Masukkan setidaknya 1 NIS"); return
        out_path = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile="nametag.pdf")
        if not out_path: return
        cards = []
        for nis in nis_list:
            sw = db.siswa_get_by_nis(nis)
            if not sw:
                messagebox.showwarning("Peringatan", f"NIS {nis} tidak ditemukan, skip"); continue
            barcode_path = out_path.replace(".pdf", f"_{nis}.png")
            barcode_generator.generate_code128(nis, barcode_path)
            foto_path = sw.get("foto")
            cards.append({"nama": sw["nama"], "nis": nis, "kelas": sw.get("kelas_id","") and db.q("SELECT nama FROM kelas WHERE id=?",(sw["kelas_id"],))[0]["nama"], "foto": foto_path, "barcode": barcode_path})
        if cards:
            pdf_generator.generate_name_tag_pdf(out_path, cards)
            messagebox.showinfo("Sukses", f"Export {len(cards)} name tag ke {out_path}")
