# AbsenKu v2.1.0 — Sistem Absensi & Nilai Siswa Offline

> **AbsenKu** adalah sistem manajemen absensi dan nilai siswa berbasis **barcode/QR Code** untuk sekolah.  
> Terdiri dari **2 aplikasi**: Aplikasi Android (APK) untuk scan absen & input nilai, dan Aplikasi Desktop (Python/Tkinter) untuk manajemen data, cetak name tag, laporan, dan sinkronisasi.  
> Sistem **100% OFFLINE** — database lokal SQLite, sync via Bluetooth/WiFi (LAN sekolah), **bukan internet**.

---

## 📥 Download

| Platform | Link | Size |
|----------|------|------|
| **Android (APK)** | [AbsenKu-v2.1.0.apk](https://github.com/Chukie99/AbsenKu/releases/download/v2.1.0/AbsenKu-v2.1.0.apk) | ~34 MB |
| **Desktop (EXE)** | [AbsenKu Desktop v2.1.0.exe](https://github.com/Chukie99/AbsenKu/releases/download/v2.1.0/AbsenKu+Desktop+v2.1.0.exe) | ~36 MB |
| **Serial Generator** | [serial-generator.html](https://github.com/Chukie99/AbsenKu/releases/download/v2.1.0/serial-generator.html) | ~12 KB |

---

## 🔓 Aktivasi APK (Anti Bajak)

### Cara Kerja
1. **User beli APK** → install → buka aplikasi
2. Aplikasi menampilkan **Device ID** (unik per HP)
3. **User WhatsApp** ke admin: `082261407123`
   - Kirim Device ID mereka
   - Contoh: *"Halo Admin, Device ID saya: A7K3P9Q2"*
4. **Admin buka** `serial-generator.html` → input Device ID → generate serial
5. **Kirim serial** ke user via WhatsApp
6. **User input serial** di aplikasi → aplikasi terbuka permanen

### Penting
- ✅ Serial **terikat 1 device** — ganti HP = perlu serial baru
- ✅ Serial **permanen** — selama gak dihapus, bisa re-aktivasi setelah format HP
- ✅ **Desktop gratis** — tidak perlu aktivasi
- ✅ WhatsApp admin: **[082261407123](https://wa.me/6282261407123)**

---

## 📱 Fitur Android
- **Scan barcode/QR** name tag siswa → absen otomatis hadir
- **Input nilai** per mata pelajaran
- **Laporan rapor** PDF
- **Backup otomatis** harian
- **Soft-delete** siswa/kelas
- **Audit log** perubahan nilai
- **Pairing token** antar device (Desktop ↔ HP)
- **Sync offline** via Bluetooth/WiFi LAN

## 🖥️ Fitur Desktop
- **Manajemen data** siswa, kelas, mata pelajaran
- **Cetak name tag** barcode/QR
- **Laporan** rapor PDF, export Excel
- **Backup/restore** database
- **Pairing** dengan HP via PIN 6 digit
- **Sync** via Bluetooth/WiFi/CSV

---

## 🔄 Sinkronisasi (Offline, LAN-only)
1. **Pairing wajib sekali**: Desktop generate 6-digit PIN → HP input → device tersimpan dengan token acak.
2. **WiFi**: HP kirim/rekap data via REST API (Flask), request wajib bawa header token → tanpa token = 401.
3. **Bluetooth**: kirim/terima file CSV/JSON yang ditandatangani token.
4. **Manual**: Export CSV → transfer via WhatsApp/USB → Import.
5. **Konflik**: timestamp terbaru menang, semua konflik tercatat di `sync_log`.

---

## 📦 Cara Build

### Android (APK via GitHub Actions)
```bash
git clone https://github.com/Chukie99/AbsenKu.git
cd AbsenKu/android
# Auto-build via GitHub Actions
git tag v2.1.0 && git push origin v2.1.0
```

### Desktop (EXE via PyInstaller)
```bash
cd AbsenKu/desktop
pip install -r requirements.txt
python create_exe.py
# output: dist/AbsenKu Desktop.exe
```

---

## 🔧 Teknologi
| Platform | Stack |
|----------|-------|
| Android | Kotlin, Jetpack Compose (M3), Room, Hilt, CameraX+ML Kit, Coil |
| Desktop | Python, Tkinter, SQLite, Flask, ReportLab, openpyxl, APScheduler |
| Build | GitHub Actions (APK), PyInstaller (.exe) |

---

## 🛡️ Privasi Data Anak Indonesia
AbsenKu menyimpan semua data siswa secara lokal (foto, alamat, no HP orang tua, tanggal lahir) — tidak ada yang dikirim ke internet.  
Lihat [docs/PRIVACY.md](docs/PRIVACY.md) untuk rekomendasi persetujuan orang tua sesuai **UU PDP No. 27/2022**.

---

## 📞 Kontak
- **WhatsApp Admin**: [082261407123](https://wa.me/6282261407123)
- **GitHub**: [Chukie99/AbsenKu](https://github.com/Chukie99/AbsenKu)

---

## 📄 Lisensi
MIT — lihat [`LICENSE`](LICENSE). Bisa dipaketin & dijual per Sekolah/Perangkat.

---
*Dikembangkan untuk UMKM sekolah — siap jual, 100% offline.*
