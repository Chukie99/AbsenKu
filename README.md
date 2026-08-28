# AbsenKu v2.0 — Sistem Absensi & Nilai Siswa Offline

> **AbsenKu** adalah sistem manajemen absensi dan nilai siswa berbasis **barcode/QR Code** untuk sekolah.  
> Terdiri dari **2 aplikasi**: Aplikasi Android (APK) untuk scan absen & input nilai, dan Aplikasi Desktop (Python/Tkinter) untuk manajemen data, cetak name tag, laporan, dan sinkronisasi.  
> Sistem **100% OFFLINE** — database lokal SQLite, sync via Bluetooth/WiFi (LAN sekolah), **bukan internet**.

---

## 📱 Demo Fitur
- **Scan barcode QR name tag siswa** → absen otomatis hadir
- **Input nilai** per mata pelajarui, laporan rapor PDF, backup otomatis harian, soft-delete siswa/kelas, audit log perubahan nilai, pairing token antar device

## 🔧 Teknologi
| Platform | Stack |
|---|---|
| Android | Kotlin, Jetpack Compose (M3), Room, Hilt, CameraX+ML Kit, Coil |
| Desktop | Python, Tkinter, SQLite, Flask, ReportLab, openpyxl, APScheduler |
| Build | GitHub Actions (APK), PyInstaller (.exe) |

## 🔄 Sinkronisasi (Offline, LAN-only)
1. **Pairing wajib sekali**: Desktop generate 6-digit PIN → HP input → device tersimpan dengan token acak.
2. **WiFi**: HP kirim/rekap data via REST API (Flask), request wajib bawa header token → tanpa token = 401.
3. **Bluetooth**: kirim/terima file CSV/JSON yang ditandatangani token.
4. **Manual**: Export CSV → transfer via WhatsApp/USB → Import.
5. **Konflik**: timestamp terbaru menang, semua konflik tercatat di `sync_log`.

## 📦 Cara Build

### Android (APK via GitHub Actions)
```bash
git clone https://github.com/Chukie99/AbsenKu.git
cd Absk/AbsenKu/android && git tag v2.0.0 && git push origin v2.0.0
# GitHub Actions auto-build → download di Releases
```

### Desktop (EXE via PyInstaller)
```bash
pip install -r AbsenKu/desktop/requirements.txt
python AbsenKu/desktop/create_exe.py
# output: dist/absenku-desktop.exe
```

## 🔓 Aktivasi (Anti Bajak)
1. APK otomatis generate Device ID (`SHA256(ANDROID_ID + "AbsenKuSalt2025")` → 8 karakter).  
2. Kirim Device ID ke **WA 082261407123**.  
3. Admin buka [`admin/serial-generator.html`](admin/serial-generator.html), input Device ID → dapat Serial.  
4. Input Serial di HP → aplikasi aktif permanen.  
5. Desktop **gratis**, tidak perlu aktivasi.

## 🛡️ Privasi Data Anak Indonesia
AbsenKu menyimpan semua data siswa secara lokal (foto, alamat, no HP orang tua, tanggal lahir) — tidak ada yang dikirim ke internet.  
Lihat [docs/PRIVACY.md](docs/PRIVACY.md) untuk rekomendasi persetujuan orang tua sesuai **UU PDP No. 27/2022**.

## 📄 Lisensi
MIT — lihat [`LICENSE`](LICENSE). Bisa dipaketin & dijual per Sekolah/Perangkat.

---
*Dikembangkan untuk UMKM sekolah — siap jual, 100% offline.*
