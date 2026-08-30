"""
ui/jadwal_pelajaran.py — Jadwal Pelajaran grid per Kelas.
Uses existing db_manager functions (db.q, db.jadwal_all, etc.).
Style matches existing screens (tkinter + optional ttkbootstrap).
"""
import tkinter as tk
from tkinter import ttk, messagebox, StringVar
import db_manager as db

HARI = ["Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"]

C = {
    "bg": "#F8F9FA",
    "card": "#FFFFFF",
    "accent": "#2563EB",
    "accent_light": "#EFF6FF",
    "green": "#16A34A",
    "green_light": "#F0FDF4",
    "red": "#DC2626",
    "text": "#1E293B",
    "text2": "#64748B",
    "border": "#E2E8F0",
}


class Jadwal_pelajaran:
    def __init__(self, parent):
        self.parent = parent

    def build(self):
        p = self.parent
        for c in p.winfo_children():
            c.destroy()

        # ── Header ──
        hdr = tk.Frame(p, bg=C["bg"])
        hdr.pack(fill="x", padx=20, pady=(16, 0))
        tk.Label(hdr, text="📅 Jadwal Pelajaran", font=("Segoe UI", 18, "bold"),
                 fg=C["text"], bg=C["bg"]).pack(anchor="w")

        # ── Controls bar ──
        ctrl = tk.Frame(p, bg=C["bg"])
        ctrl.pack(fill="x", padx=20, pady=(8, 4))

        tk.Label(ctrl, text="Kelas:", font=("Segoe UI", 10), fg=C["text"],
                 bg=C["bg"]).pack(side="left")
        self.kelas_var = StringVar()
        self.kelas_rows = db.kelas_all()
        self.kelas_map = {r["nama"]: r["id"] for r in self.kelas_rows}
        self.kelas_combo = ttk.Combobox(ctrl, textvariable=self.kelas_var,
                                         values=list(self.kelas_map.keys()),
                                         state="readonly", width=18)
        self.kelas_combo.pack(side="left", padx=(4, 12))
        if self.kelas_rows:
            self.kelas_var.set(self.kelas_rows[0]["nama"])
        self.kelas_combo.bind("<<ComboboxSelected>>", lambda e: self._load_grid())

        tk.Button(ctrl, text="➕ Tambah Jadwal", bg=C["accent"], fg="white",
                  relief="flat", padx=10, pady=4, command=self._show_add_dialog).pack(side="right")
        tk.Button(ctrl, text="🔄 Refresh", bg="#64748B", fg="white",
                  relief="flat", padx=10, pady=4, command=self._load_grid).pack(side="right", padx=4)

        # ── Scrollable grid area ──
        canvas_frame = tk.Frame(p, bg=C["bg"])
        canvas_frame.pack(fill="both", expand=True, padx=20, pady=(4, 16))

        self.canvas = tk.Canvas(canvas_frame, bg=C["bg"], highlightthickness=0)
        vscroll = ttk.Scrollbar(canvas_frame, orient="vertical", command=self.canvas.yview)
        self.grid_frame = tk.Frame(self.canvas, bg=C["bg"])

        self.grid_frame.bind("<Configure>", lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")))
        self.canvas.create_window((0, 0), window=self.grid_frame, anchor="nw")
        self.canvas.configure(yscrollcommand=vscroll.set)

        self.canvas.pack(side="left", fill="both", expand=True)
        vscroll.pack(side="right", fill="y")

        # Bind mousewheel
        def _on_mousewheel(event):
            self.canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")
        self.canvas.bind_all("<MouseWheel>", _on_mousewheel)

        self._load_grid()

    def _load_grid(self):
        for w in self.grid_frame.winfo_children():
            w.destroy()

        kelas_nama = self.kelas_var.get()
        if not kelas_nama:
            return
        kelas_id = self.kelas_map.get(kelas_nama)
        if not kelas_id:
            return

        # Fetch jadwal for this kelas
        jadwal = db.jadwal_by_kelas(kelas_id)
        jadwal_map = {}
        for j in jadwal:
            key = (j["hari"], j.get("jam_mulai", ""))
            jadwal_map[key] = j

        # Fetch mapel names
        mapel_list = db.mapel_all()
        mapel_names = {m["id"]: m["nama"] for m in mapel_list}

        # Header row
        tk.Label(self.grid_frame, text="Hari / Jam", font=("Segoe UI", 10, "bold"),
                 fg="white", bg=C["accent"], relief="flat", padx=8, pady=6,
                 width=12, anchor="center").grid(row=0, column=0, padx=1, pady=1, sticky="nsew")

        for j, hari in enumerate(HARI):
            tk.Label(self.grid_frame, text=hari, font=("Segoe UI", 10, "bold"),
                     fg="white", bg=C["accent"], relief="flat", padx=8, pady=6,
                     anchor="center").grid(row=0, column=j + 1, padx=1, pady=1, sticky="nsew")

        # Group jadwal by hari
        by_hari = {}
        for j in jadwal:
            h = j["hari"]
            by_hari.setdefault(h, []).append(j)

        # Show entries per day
        for i, hari in enumerate(HARI):
            entries = by_hari.get(hari, [])
            if not entries:
                cell = tk.Label(self.grid_frame, text="—", font=("Segoe UI", 9),
                                fg=C["text2"], bg="#F1F5F9", relief="solid", bd=1,
                                anchor="center", padx=4, pady=4)
                cell.grid(row=i + 1, column=0, padx=1, pady=1, sticky="nsew")
                for j in range(len(HARI)):
                    tk.Label(self.grid_frame, text="—", font=("Segoe UI", 9),
                             fg=C["text2"], bg="#F1F5F9", relief="solid", bd=1,
                             anchor="center", padx=4, pady=4).grid(
                        row=i + 1, column=j + 1, padx=1, pady=1, sticky="nsew")
            else:
                # Time slot label
                times = [f"{e['jam_mulai']}-{e['jam_selesai']}" for e in entries]
                tk.Label(self.grid_frame, text="\n".join(times),
                         font=("Segoe UI", 8), fg=C["text"], bg="#E0F2FE",
                         relief="solid", bd=1, anchor="center", padx=4, pady=2).grid(
                    row=i + 1, column=0, padx=1, pady=1, sticky="nsew")

                # Mapel entries for this day
                mapel_texts = []
                for e in entries:
                    mn = mapel_names.get(e["mapel_id"], "?")
                    guru = e.get("guru", "") or ""
                    line = mn + (f"\n({guru})" if guru else "")
                    mapel_texts.append(line)

                cell = tk.Label(self.grid_frame, text="\n".join(mapel_texts) if mapel_texts else "—",
                                font=("Segoe UI", 9), fg=C["text"], bg=C["green_light"],
                                relief="solid", bd=1, anchor="center", padx=4, pady=4, wraplength=120)
                cell.grid(row=i + 1, column=1, padx=1, pady=1, sticky="nsew", columnspan=len(HARI))

        # Column weights
        self.grid_frame.grid_columnconfigure(0, weight=1)
        for j in range(len(HARI)):
            self.grid_frame.grid_columnconfigure(j + 1, weight=2)
        for i in range(len(HARI)):
            self.grid_frame.grid_rowconfigure(i + 1, weight=1)

    def _show_add_dialog(self):
        top = tk.Toplevel(self.parent)
        top.title("Tambah Jadwal Pelajaran")
        top.geometry("400x350")
        top.resizable(False, False)

        frm = tk.Frame(top, padx=20, pady=20)
        frm.pack(fill="both", expand=True)

        tk.Label(frm, text="Tambah Jadwal Pelajaran", font=("Segoe UI", 13, "bold")).pack(anchor="w")

        # Kelas
        tk.Label(frm, text="Kelas:", font=("Segoe UI", 10)).pack(anchor="w", pady=(10, 2))
        kelas_var = StringVar()
        ttk.Combobox(frm, textvariable=kelas_var,
                     values=list(self.kelas_map.keys()), state="readonly", width=30).pack(fill="x")

        # Mapel
        tk.Label(frm, text="Mata Pelajaran:", font=("Segoe UI", 10)).pack(anchor="w", pady=(8, 2))
        mapel_list = db.mapel_all()
        mapel_map = {m["nama"]: m["id"] for m in mapel_list}
        mapel_var = StringVar()
        ttk.Combobox(frm, textvariable=mapel_var,
                     values=list(mapel_map.keys()), state="readonly", width=30).pack(fill="x")

        # Hari
        tk.Label(frm, text="Hari:", font=("Segoe UI", 10)).pack(anchor="w", pady=(8, 2))
        hari_var = StringVar(value="Senin")
        ttk.Combobox(frm, textvariable=hari_var, values=HARI,
                     state="readonly", width=30).pack(fill="x")

        # Jam
        tk.Label(frm, text="Jam Mulai (HH:MM):", font=("Segoe UI", 10)).pack(anchor="w", pady=(8, 2))
        mulai_entry = tk.Entry(frm, width=33)
        mulai_entry.insert(0, "07:00")
        mulai_entry.pack(fill="x")

        tk.Label(frm, text="Jam Selesai (HH:MM):", font=("Segoe UI", 10)).pack(anchor="w", pady=(8, 2))
        selesai_entry = tk.Entry(frm, width=33)
        selesai_entry.insert(0, "08:00")
        selesai_entry.pack(fill="x")

        # Guru
        tk.Label(frm, text="Guru:", font=("Segoe UI", 10)).pack(anchor="w", pady=(8, 2))
        guru_entry = tk.Entry(frm, width=33)
        guru_entry.pack(fill="x")

        def save():
            kelas_nama = kelas_var.get()
            mapel_nama = mapel_var.get()
            if not kelas_nama or not mapel_nama:
                messagebox.showwarning("Validasi", "Pilih kelas dan mata pelajaran!")
                return
            kelas_id = self.kelas_map.get(kelas_nama)
            mapel_id = mapel_map.get(mapel_nama)
            hari = hari_var.get()
            mulai = mulai_entry.get().strip()
            selesai = selesai_entry.get().strip()
            guru = guru_entry.get().strip()
            if not mulai or not selesai:
                messagebox.showwarning("Validasi", "Jam mulai dan selesai harus diisi!")
                return
            db.jadwal_insert(kelas_id, mapel_id, hari, mulai, selesai, guru or None)
            self._load_grid()
            top.destroy()

        tk.Button(frm, text="💾 Simpan", bg=C["accent"], fg="white",
                  relief="flat", padx=12, pady=6, command=save).pack(pady=(14, 0))
