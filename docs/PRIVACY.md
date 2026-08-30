# Kebijakan Privasi Data & Pedoman Persetujuan Orang Tua  
## (UU PDP No. 27/2022) — AbsenKu v2.0

---

## 1. ✅ Design Offline-by-Design

**AbsenKu tidak pernah mengirim data ke internet.** Semua data siswa, absen, nilai, dan foto disimpan **secara lokal** di dalam perangkat (SQLite database + internal storage Android / folder lokal Desktop). Sinkronisasi antar-device hanya terjadi **di jaringan LAN sekolah saja** (Bluetooth/WiFi), **bukan melalui internet**.

### Data pribadi anak yang dikumpulkan
| Data | Tujuan | Penyimpanan |
|---|---|---|
| Foto 3:4 siswa (resize 800x1067, JPEG q80) | Name tag ID card | Internal storage `foto/` |
| Nama lengkap | Absen & nilai | SQLite tabel `siswa` |
| Nomor Induk Sekolah (NIS) | Identifikasi unik | SQLite tabel `siswa` |
| Kelas & Tanggal Lahir | Absensi & laporan | SQLite tabel `siswa` |
| Alamat, No HP Orang Tua | Kontak darurat / rapor | SQLite tabel `siswa` |

### Ancaman privasi & mitigasi
- **Backup harian** (.db + checksum SHA256): hanya disimpan di folder lokal PC guru, **bukan shared drive / cloud**.
- **Sync log & audit log**: semua log perubahan (siapa ubah nilai, kapan) tersimpan lokal — siap ditunjukkan sebagai bukti asal usul data tidak bocor ke pihak ketiga.

---

## 2. 📋 Rekomendasi Persetujuan Orang Tua (Formulir)

Sebelum memasukkan **data pribadi anak** (foto, alamat, no HP orang tua), sekolah wajib:

### a) Dapatkan persetujuan tertulis orang tua
```
Formulir Persetujuan Pengumpulan Data Pribadi Anak

Nama Siswa: _____________________  Kelas: ______

Dengan ini, orang tua/wali siswa di atas setuju agar sekolah mengumpulkan dan menyimpan
secara LOKAL (offline) data berikut untuk keperluan administrasi absensi & nilai:
□ Foto siswa
□ Alamat rumah
□ Nomor HP orang tua/wali

Data akan disimpan di perangkat sekolah (SQLite), Tidak pernah dikirim ke internet.
Hak akses: orang tua berhak melihat, memperbaiki, atau memintakan penghapusan data.

Tanda tangan orang tua/wali: _________________  Tanggal: ____/____/_____ 
```

### b) Prinsip Minimum Data
- Hanya kumpulkan data yang memang dibutuhkan untuk absensi & nilai.
- Foto **hanya wajib jika** sekolah memakai sistem scan barcode/QR pada name tag. Jika tidak dipakai, kolom `foto` bisa dikosongkan.

### c) Hak Akses Orang Tua
Orang tua berhak:
1. **Melihat** data anak (via fitur export laporan rapor di Desktop).
2. **Meminta koreksi** jika ada kesalahan (tercatat di `audit_log`).
3. **Meminta penghapusan** — hubungi TU Sekolah, data akan dihapus (soft delete aktif + hapus foto) dari database lokal.

---

## 3. 🗑️ Hapus Data Pribadi (Soft Delete)

Jika seorang siswa pindah / keluar:
1. **Soft-delete siswa**: `is_active = 0`, `deleted_at` terisi — histori absen/nilai tetap utuh.
2. **Hapus foto**: file `foto/.../{nis}.jpg` otomatis dihapus saat data dihapus.
3. **Audit trail**: aksi hapus tercatat di `audit_log` (table_name='siswa', record_id, old_value, changed_by).

---

## 4. 🛡️ Keamanan Teknis

- **File DB tidak di-encrypt** secara default — karena sistem offline lokal, risiko maksimal. Jika sekolah butuh enkripsi tambahan: gunakan path DB khusus & backup dilindungi permission folder.
- **Token pairing & PIN**: disimpan di `SharedPreferences` HP & database `paired_devices` — **bukan file teks biasa**, bukan di share ke pihak lain.
- **Server WiFi**: Flask API bind ke `127.0.0.1` atau subnet lokal saja, **bukan `0.0.0.0`** public internet. Rate limit 30 req/menit per token.

---

## 5. 📞 Kontak Data Protection Officer (DPO)
Jika ada pertanyaan terkait data pribadi anak, hubungi:  
**Email / WA**: 082261407123 (Sopian — Pengembang AbsenKu)  

---
*Dokumen ini hanya dibagikan kepada pengguna internal sekolah.*
