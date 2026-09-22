# Exam Browser (Android Native)

Aplikasi browser ujian sekolah Android native yang ringan, stabil, aman, dan anti-curang. Dirancang khusus untuk lingkungan ujian berbasis web (CBT / Computer Based Test).

---

## 📱 Ringkasan Aplikasi

- **Application Name**: Exam Browser
- **Package Name / Application ID**: `com.muhipo.exambrowser`
- **Target SDK**: Android 15 (API 35)
- **Minimum SDK**: Android 8.0 Oreo (API 26)
- **Bahasa Pemrograman**: Kotlin 2.0
- **Build System**: Gradle 9.7 + Android Gradle Plugin (AGP) 8.8
- **Ukuran APK**: ~6.1 MB (Release) / ~7.5 MB (Debug)
- **Status Kompilasi**: ✅ **100% BUILD SUCCESSFUL**

---

## 🌟 Fitur Utama

1. **Clean & Modern Interface**:
   - Tampilan awal minimalis dengan logo perisai keamanan (*Shield & Checkmark*).
   - Tombol utama besar: **SCAN QR / BARCODE**.
   - Tombol manual: **ENTER URL MANUALLY**.
   - Akses rahasia ke **Administrator Settings** dengan mengetuk (tap) logo sebanyak 5 kali dalam 3 detik.
2. **Kamera QR / Barcode Scanner Cepat & Mandiri**:
   - Menggunakan CameraX + ZXing core (~500 KB, pure Java).
   - 100% bekerja offline tanpa dependensi Google Play Services.
   - Mendukung format: QR Code, Code 128, Code 39, EAN-13, EAN-8, UPC-A, UPC-E, Data Matrix, PDF417, ITF.
   - Deteksi URL otomatis (hanya mengizinkan HTTP/HTTPS). Barcode non-URL menampilkan notifikasi *"Invalid exam code."*.
   - Kamera langsung ditutup otomatis setelah scan berhasil.
   - Dilengkapi tombol toggle lampu senter (*torch*).
3. **Pemeriksaan Manual URL**:
   - Dialog minimalis untuk memasukkan URL ujian secara manual jika kamera bermasalah.
   - Validasi ketat format HTTP / HTTPS.
4. **WebView Ujian Terisolasi & Teroptimasi**:
   - Hardware acceleration aktif.
   - JavaScript, DOM Storage, dan Cookie aktif.
   - Navigasi tetap berada di dalam WebView (tidak membuka browser eksternal Chrome).
   - Intent eksternal (`market://`, `intent://`, `tel:`) otomatis diblokir.
   - Indikator loading progress bar di bagian atas.
   - Layar error *"Unable to load exam."* dengan tombol **RETRY**.
   - Layar offline *"NO INTERNET CONNECTION"* otomatis mendeteksi koneksi jaringan dengan tombol **RETRY**.
5. **Whitelist Domain Dinamis**:
   - Domain dari QR code / URL manual otomatis ditambahkan ke whitelist sesi.
   - Akses ke domain di luar whitelist akan diblokir dengan notifikasi: *"This page is outside the exam environment."*.
   - Administrator dapat menambah atau menghapus domain whitelist dari menu Pengaturan Admin.
6. **Immersive Fullscreen (Mode Ujian Penuh)**:
   - Status bar dan Navigation bar disembunyikan sepenuhnya (*sticky immersive*).
   - Layar selalu menyala (`FLAG_KEEP_SCREEN_ON`).
   - Tidak ada toolbar atau address bar browser.
7. **Perlindungan Anti-Curang (Security & Anti-Cheat)**:
   - **Screenshot & Screen Recording**: Diblokir penuh menggunakan `FLAG_SECURE` resmi Android (layar hitam pada perekam layar dan daftar recent apps).
   - **Copy / Paste & Long Press**: Dinonaktifkan pada WebView statis menggunakan CSS injection dan gesture blocker, namun keyboard dan input tetap berfungsi normal untuk soal essay / teks siswa.
   - **Back Button**: Dinonaktifkan selama ujian aktif (dapat diaktifkan oleh admin jika diinginkan).
   - **Download Blocker**: Pengunduhan file diblokir secara default.
8. **Lock Task Mode (Kiosk Mode Android Resmi)**:
   - Menggunakan API resmi `ActivityManager` dan `DevicePolicyManager` (`startLockTask()`, `stopLockTask()`).
   - Mendukung mode **Device Owner** untuk penguncian 100% tanpa tombol unpin.
   - Jika belum berstatus Device Owner, aplikasi tetap mengaktifkan Screen Pinning dan memberikan informasi konfigurasi kepada administrator.
9. **Exam Session Persistence**:
   - Sesi ujian aktif disimpan di penyimpanan lokal.
   - Jika HP siswa di-restart saat ujian aktif, aplikasi otomatis melanjutkan (*auto-resume*) ke halaman ujian ketika dibuka kembali.
10. **Finish Exam**:
    - Tombol **FINISH EXAM** dengan dialog konfirmasi (*"Are you sure you want to finish this examination?"*).
    - Menghapus sesi, membersihkan cache & history WebView, menghentikan lock task, dan kembali ke halaman awal.
11. **Mode Administrator Terproteksi**:
    - Dibuka dengan mengetuk logo 5 kali.
    - Dilindungi PIN (Default: `123456`).
    - PIN disimpan dengan hash SHA-256 + cryptographic salt (bukan plaintext).
    - Pengaturan: Tambah/hapus domain whitelist, toggle Kiosk mode, toggle Screenshot protection, toggle tombol Back WebView, reset sesi aktif, ganti PIN admin.
12. **Deep Link**:
    - Mendukung skema deep link: `exam://start?url=https%3A%2F%2Fexam.school.sch.id`

---

## 📂 Struktur Project

```
Eam/
├── build.gradle.kts                     # Root build configuration
├── settings.gradle.kts                  # Gradle repository & module settings
├── gradle.properties                    # JVM memory & AndroidX settings
├── local.properties                     # Lokasi Android SDK
├── gradlew & gradlew.bat                # Gradle wrapper executable
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties        # Gradle 9.7.1
└── app/
    ├── build.gradle.kts                 # Dependencies & Build Types
    ├── proguard-rules.pro               # ProGuard / R8 rules
    └── src/
        └── main/
            ├── AndroidManifest.xml      # Activities, Permissions, Receiver
            ├── java/com/muhipo/exambrowser/
            │   ├── MainActivity.kt      # Home screen, manual URL, admin gesture
            │   ├── scanner/
            │   │   ├── ScannerActivity.kt     # CameraX + ZXing barcode reader
            │   │   └── ViewfinderView.kt      # Custom laser & bracket overlay
            │   ├── exam/
            │   │   ├── ExamActivity.kt        # Fullscreen secure WebView
            │   │   ├── ExamWebClient.kt       # Whitelist filter & error handler
            │   │   └── ExamWebChromeClient.kt # Progress & WebRTC permissions
            │   ├── admin/
            │   │   ├── AdminActivity.kt       # Admin control panel
            │   │   └── WhitelistAdapter.kt    # RecyclerView whitelist
            │   ├── kiosk/
            │   │   └── KioskManager.kt        # LockTaskMode & DeviceOwner API
            │   ├── security/
            │   │   ├── SecurityManager.kt     # FLAG_SECURE & anti-copy
            │   │   └── CryptoUtils.kt         # SHA-256 + salt PIN hasher
            │   ├── receiver/
            │   │   └── ExamDeviceAdminReceiver.kt # Android Device Admin
            │   └── utils/
            │       ├── PreferenceManager.kt   # Local session & preferences
            │       ├── NetworkUtils.kt        # Real-time connectivity monitor
            │       └── UrlValidator.kt        # HTTP/HTTPS & domain parser
            └── res/
                ├── layout/
                │   ├── activity_main.xml
                │   ├── activity_scanner.xml
                │   ├── activity_exam.xml
                │   ├── activity_admin.xml
                │   ├── dialog_manual_url.xml
                │   ├── dialog_admin_pin.xml
                │   ├── dialog_finish_exam.xml
                │   ├── dialog_add_domain.xml
                │   ├── dialog_change_pin.xml
                │   └── item_whitelist_domain.xml
                ├── drawable/
                │   ├── ic_exam_shield.xml     # Vector Logo (Shield & Checkmark)
                │   ├── ic_qr_scan.xml
                │   ├── ic_settings.xml
                │   ├── ic_flash_on.xml
                │   ├── ic_flash_off.xml
                │   ├── ic_delete.xml
                │   ├── ic_arrow_back.xml
                │   ├── ic_warning.xml
                │   ├── ic_wifi_off.xml
                │   ├── ic_refresh.xml
                │   ├── ic_lock.xml
                │   ├── bg_button_primary.xml
                │   ├── bg_button_secondary.xml
                │   ├── bg_card.xml
                │   ├── bg_badge.xml
                │   └── bg_edit_text.xml
                ├── values/
                │   ├── colors.xml
                │   ├── strings.xml
                │   └── themes.xml
                └── xml/
                    ├── device_admin.xml       # Kebijakan Device Admin
                    └── network_security_config.xml
```

---

## 🛠️ Cara Membuka Project di Android Studio

1. Buka **Android Studio**.
2. Pilih menu **File** → **Open...**.
3. Navigasikan ke folder:
   ```
   c:\Users\LENOVO\Documents\Eam
   ```
4. Klik **OK**. Android Studio akan menyinkronkan dependensi Gradle secara otomatis.

---

## 🔨 Cara Build APK

Proyek ini telah dilengkapi dengan wrapper Gradle siap pakai. Jalankan perintah berikut di PowerShell atau Command Prompt pada direktori project:

### 1. Build Debug APK:
```powershell
.\gradlew.bat assembleDebug
```
File APK yang dihasilkan berlokasi di:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Build Release APK:
```powershell
.\gradlew.bat assembleRelease
```
File APK yang dihasilkan berlokasi di:
```
app/build/outputs/apk/release/app-release.apk
```

---

## 📲 Cara Install APK ke Perangkat

### Metode 1: Melalui ADB (Android Debug Bridge)
Hubungkan HP ke komputer dengan kabel USB dan aktifkan *USB Debugging*, lalu jalankan:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Metode 2: Salin Manual ke HP
Kirim file `app-debug.apk` atau `app-release.apk` ke HP via USB/WhatsApp/Drive, lalu buka file APK di HP dan pilih **Install**.

---

## 🔒 Konfigurasi Device Owner & Kiosk Lock Task Mode

### Perbedaan Android Biasa vs Dedicated Kiosk Device:
- **Android Biasa (Standar / Non-Device Owner)**:
  Aplikasi memanggil `startLockTask()`. Sistem Android akan menampilkan dialog *Screen Pinning*. Siswa tidak bisa menekan tombol Home atau Recent Apps secara normal. Namun, sistem Android standar menyediakan gesture bawaan (menekan tombol Back + Recent secara bersamaan) untuk keluar dari pin.
- **Dedicated Kiosk Device (Device Owner)**:
  Aplikasi didaftarkan sebagai Device Owner melalui MDM atau ADB. Saat ujian dimulai, `startLockTask()` mengunci perangkat secara total: tombol Home, Recent Apps, Notifikasi, dan Power Menu dinonaktifkan tanpa dialog konfirmasi dan **tidak dapat dibatalkan oleh siswa**.

### Cara Mengaktifkan Device Owner melalui ADB:
1. Pastikan di HP **belum ada akun Google / akun email yang tersinkron** (pada Android murni, Device Owner harus diset sebelum ada akun atau pada perangkat baru/factory reset, atau hapus sementara akun Google di *Settings → Accounts*).
2. Hubungkan HP ke PC via USB dengan *USB Debugging* aktif.
3. Jalankan perintah ADB berikut:
   ```bash
   adb shell dpm set-device-owner com.muhipo.exambrowser/.receiver.ExamDeviceAdminReceiver
   ```
4. Jika berhasil, terminal akan menampilkan output:
   `Success: Device owner set to package com.muhipo.exambrowser`
5. Buka Exam Browser, masuk ke menu Admin Settings, dan status akan menunjukkan:
   `Device Owner: Active (Full Kiosk Enabled)`.

---

## 🔑 Mode Administrator & Konfigurasi

1. **Cara Masuk ke Mode Admin**:
   - Buka halaman utama aplikasi Exam Browser.
   - Ketuk (tap) logo perisai di bagian tengah sebanyak **5 kali** berturut-turut.
   - Masukkan PIN Admin.
   - **Default PIN**: `123456`
2. **Konfigurasi Domain Whitelist**:
   - Tekan tombol **+ ADD** di dalam Admin Settings.
   - Masukkan nama host server ujian (contoh: `exam.sekolah.sch.id` atau `192.168.1.100`).
   - Tekan **ADD DOMAIN**.
3. **Mengubah PIN Admin**:
   - Tekan tombol **Change Admin PIN** di bagian bawah.
   - Masukkan PIN lama dan PIN baru (minimal 4 digit), lalu tekan **UPDATE PIN**.

---

## 🧪 Testing Checklist

Gunakan daftar periksa berikut saat menguji aplikasi:

| No | Pengujian | Hasil yang Diharapkan |
|---|---|---|
| 1 | **Install Aplikasi** | APK terinstall dengan sukses, icon perisai Exam Browser muncul di launcher. |
| 2 | **Buka Aplikasi** | Halaman utama tampil bersih dengan judul EXAM BROWSER dan 2 tombol utama. |
| 3 | **Permission Kamera** | Menekan "SCAN QR / BARCODE" meminta izin kamera secara runtime. |
| 4 | **Scan QR Valid** | Kamera membaca QR dengan URL HTTP/HTTPS, bergetar halus, kamera tertutup otomatis, dan halaman ujian langsung terbuka. |
| 5 | **Scan Barcode Non-URL** | Membaca barcode non-URL (misal angka/teks acak) menampilkan peringatan *"Invalid exam code."* dan tidak crash. |
| 6 | **Input URL Manual** | Tombol "ENTER URL MANUALLY" memunculkan dialog input URL dengan validasi format. |
| 7 | **Immersive Fullscreen** | Status bar dan Navigation bar hilang seketika saat masuk ke halaman ujian. |
| 8 | **Back Button Lock** | Menekan tombol Back perangkat saat ujian aktif menampilkan pesan *"Back navigation is disabled during examination."* dan tidak keluar dari ujian. |
| 9 | **Screenshot Protection** | Menekan tombol Screenshot menghasilkan notifikasi sistem *"Can't take screenshot due to security policy"* dan preview recent apps berwarna hitam. |
| 10 | **Domain Whitelist** | Mencoba navigasi ke website lain (misal Google / Facebook) diblokir dengan notifikasi *"This page is outside the exam environment."*. |
| 11 | **Offline Detection** | Saat koneksi internet terputus, muncul layar penuh *"NO INTERNET CONNECTION"* dengan tombol RETRY. |
| 12 | **Finish Exam** | Menekan tombol "FINISH EXAM" menampilkan dialog konfirmasi. Saat konfirmasi dipilih, sesi dihapus dan kembali ke halaman awal. |
| 13 | **Auto-Resume Sesi** | Jika aplikasi ditutup paksa saat ujian aktif dan dibuka kembali, aplikasi otomatis kembali ke halaman ujian. |
| 14 | **Admin Mode (5 Tap)** | Mengetuk logo 5 kali memunculkan modal input PIN, menerima default PIN `123456`, dan membuka Admin Settings. |
