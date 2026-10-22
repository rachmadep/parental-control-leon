# Parental Control untuk Leon (Android Pair App)

Sepasang aplikasi Android (Native Kotlin) yang dirancang untuk memantau dan mengontrol aktivitas tablet anak (**Leon**), khususnya penggunaan **YouTube Kids**.

Aplikasi ini terdiri dari dua modul:
1. **`child-app`**: Dipasang di tablet Android Leon. Berjalan sebagai *foreground service* yang memantau durasi aplikasi, mengunci layar, mengatur volume/brightness, dan menyediakan server Bluetooth (BLE) saat Wi-Fi dimatikan.
2. **`parent-app`**: Dipasang di HP orang tua. Berfungsi sebagai dashboard pemantau waktu layar YouTube Kids dan remote kontrol (Lock screen, Slider volume, Slider kecerahan, dan koneksi Bluetooth darurat).

---

## 🚀 Fitur Utama

- ⏱️ **Cek Aplikasi yang Dibuka & Durasinya**:
  - Menggunakan Android `UsageStatsManager` untuk membaca durasi menit harian.
  - Menyorot pemakaian **YouTube Kids** secara visual dengan progres bar harian.
- 📶 **Kontrol Koneksi & Solusi Wi-Fi Mati (Bluetooth Fallback)**:
  - **Saat Online**: Menggunakan **Firebase Realtime Database** untuk pengiriman perintah instan dari jarak jauh.
  - **Saat Wi-Fi Mati / Offline**: Tablet Leon menjalankan **BLE GATT Server**. HP Orang Tua dapat langsung terhubung via **Bluetooth Low Energy (BLE)** dalam jarak dekat untuk mengirimkan perintah kunci layar, atur volume, atau menyalakan koneksi kembali.
- 🔊 **Kontrol Volume & Kecerahan**:
  - Slider volume suara media (`STREAM_MUSIC`) langsung dari HP orang tua.
  - Slider kecerahan layar tablet (`Settings.System.SCREEN_BRIGHTNESS`).
- 🔒 **Lock Screen Ganda**:
  - **System Lock**: Menggunakan `DevicePolicyManager.lockNow()` untuk mematikan dan mengunci layar tablet.
  - **Kid-Friendly Lock Overlay**: Layar penuh ramah anak dengan pesan *"Waktunya Istirahat, Leon! 🌟"* yang dilengkapi verifikasi PIN Orang Tua (`1234`) jika ingin dibuka manual.

---

## 📁 Struktur Direktori Proyek

```
parental-control/
├── child-app/                       # Modul Tablet Leon
│   ├── src/main/
│   │   ├── AndroidManifest.xml      # Izin UsageStats, DeviceAdmin, Overlay, BLE
│   │   ├── res/
│   │   │   ├── xml/device_admin.xml # Konfigurasi Device Admin Policy
│   │   │   └── layout/              # UI Setup Checklist & Lock Overlay
│   │   └── java/com/parentalcontrol/child/
│   │       ├── manager/             # AppUsageManager, DeviceControlManager, BleServerManager
│   │       ├── service/             # ChildMonitoringService, ChildFcmService
│   │       ├── receiver/            # AdminReceiver, BootReceiver
│   │       └── ui/                  # MainActivity, LockOverlayActivity
├── parent-app/                      # Modul HP Orang Tua
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/parentalcontrol/parent/
│   │       ├── ble/                 # BleClientManager (Scanner & Transmitter)
│   │       ├── firebase/            # FirebaseSyncManager (Realtime listener & sender)
│   │       └── ui/                  # MainActivity & AppUsageAdapter
├── gradle/libs.versions.toml        # Version Catalog Gradle
├── build.gradle.kts                 # Root Gradle
└── settings.gradle.kts
```

---

## 🛠️ Langkah Menjalankan Proyek

### 1. Buka di Android Studio
Buka folder `parental-control` di **Android Studio** (Hedgehog / Iguana / Jellyfish atau versi terbaru). Android Studio akan otomatis mengunduh Gradle wrapper dan dependensi yang dibutuhkan.

### 2. Setup Firebase
1. Buat proyek baru di [Firebase Console](https://console.firebase.google.com/).
2. Aktifkan **Firebase Realtime Database** dalam mode test atau set rules:
   ```json
   {
     "rules": {
       "devices": {
         "$deviceId": {
           ".read": true,
           ".write": true
         }
       }
     }
   }
   ```
3. Daftarkan 2 aplikasi Android di Firebase Console:
   - Package 1: `com.parentalcontrol.child` -> Download `google-services.json` dan letakkan di dalam folder `child-app/`.
   - Package 2: `com.parentalcontrol.parent` -> Download `google-services.json` dan letakkan di dalam folder `parent-app/`.

### 3. Instalasi di Tablet Leon (`child-app`)
1. Pilih konfigurasi run `child-app` di Android Studio dan install ke tablet.
2. Buka aplikasi **Leon Parental (Child)** di tablet.
3. Ikuti **Checklist Izin** pada layar:
   - ✅ **Akses Penggunaan**: Izinkan akses data penggunaan agar aplikasi bisa membaca durasi YouTube Kids.
   - ✅ **Device Administrator**: Aktifkan agar HP orang tua bisa mengunci layar tablet.
   - ✅ **Tampilkan di Atas Aplikasi Lain (Overlay)**: Izinkan untuk menampilkan pesan "Waktunya Istirahat".
   - ✅ **Ubah Pengaturan Sistem**: Izinkan untuk mengatur slider kecerahan layar.
   - ✅ **Izin Bluetooth**: Izinkan agar BLE Server aktif menerima sinyal offline.
4. Klik tombol **"Mulai Layanan Proteksi"**.

### 4. Instalasi di HP Orang Tua (`parent-app`)
1. Pilih konfigurasi run `parent-app` dan install ke HP Anda.
2. Buka aplikasi **Leon Parental (Parent)**.
3. Anda akan langsung melihat:
   - Status perangkat tablet Leon (**Online** via Firebase).
   - Durasi penggunaan **YouTube Kids** hari ini beserta daftar aplikasi lainnya.
   - Tombol **Kunci Layar Sekarang**, **Buka Kunci**, serta slider Volume dan Brightness.
4. **Uji Coba Saat Wi-Fi Tablet Mati:**
   - Matikan Wi-Fi pada tablet Leon.
   - Di HP Orang Tua, klik tombol **"Hubungkan via Bluetooth (Offline)"**.
   - HP Anda akan mencari tablet Leon via BLE dan terhubung secara lokal.
   - Anda tetap bisa menekan tombol **Kunci Layar** atau mengatur **Volume/Brightness** meski tanpa internet!
