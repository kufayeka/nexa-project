# Nexa Framework 🚀

[![Java Version](https://img.shields.io/badge/Java-25-orange.svg)](https://jdk.java.net/)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Architecture](https://img.shields.io/badge/Architecture-Domain--Driven-blue.svg)]()
[![DI](https://img.shields.io/badge/Dependency%20Injection-Pure%20DI-red.svg)]()

Nexa adalah **industrial automation low-code platform** modern berperforma tinggi yang dibangun di atas Java. Nexa dirancang untuk menduplikasi perilaku eksekusi visual flow ala **Node-RED**, tetapi diimplementasikan sebagai runtime Java tingkat produksi (*production‑grade*) yang tangguh, modular, mudah dipelihara, dan hemat memori.

---

## 🌟 Fitur Utama

* **Concurrency Ringan (Virtual Threads)**: Setiap jalur aliran data downstream dieksekusi secara asinkronus dan paralel menggunakan Java Virtual Threads, memungkinkan penanganan jutaan transaksi pesan tanpa membebani thread carrier OS.
* **Pure Dependency Injection (Pure DI)**: Mengeliminasi overhead pemindaian classpath dan startup lag dengan perakitan dependensi manual bertipe aman (*compile‑time checked*) melalui pola *Composition Root*.
* **Domain‑Driven Design (DDD)**: Struktur paket diorganisasikan secara ketat berdasarkan domain fungsional bisnis (Workspace, Deployment, Execution, Scheduler, Statistics, Scripting) demi mencegah kode spaghetti.
* **Isolasi State Pesan (Deep Copy)**: Transformasi data yang terjadi di cabang rute yang berbeda terisolasi secara aman menggunakan algoritma deep‑copy berbasis *Switch Pattern Matching* Java 25.
* **Built‑in Nexa DSL Engine**: Memiliki compiler dan runtime terintegrasi untuk mengeksekusi skrip transformasi data performa tinggi dengan fitur null‑safety (`?.`, `??`) dan integrasi Java host extensions.
* **Lock‑free Statistics**: Pencatatan metrik operasional secara konkuren menggunakan `LongAdder` untuk meminimalkan contention antar thread worker.

---

## 📂 Struktur Proyek (Setelah Refactor Multi-Module Gradle)

```
nexa-framework/
├── .agents/                 # Panduan kustomisasi aturan agen AI
├── nexa-api/                # Modul publik API yang di-import oleh plugin eksternal
│   └── src/main/java/nexa/framework/runtime/api/  # (e.g. NexaPlugin, RuntimeMessage)
├── nexa-core/               # Core Engine (FlowCompiler, DefaultRuntimeEngine, domain)
│   └── src/main/java/nexa/framework/runtime/domain/
│       ├── workspace/       # Manajemen JSON & model definition
│       ├── deployment/      # Validasi topologi & compiler graf
│       ├── execution/       # Orkestrasi pesan & virtual threads
│       ├── scheduler/       # Penjadwal pemicu input node
│       └── statistics/      # Akumulator metrik flow
├── nexa-script-engine/      # Modul implementasi scripting DSL (Nexa DSL compiler/runtime)
│   └── src/main/resources/META-INF/services/      # Registrasi SPI nexa.framework.runtime.domain.scripting.api.ScriptEngine
└── nexa-cli/                # Command-line Runner standalone (NexaStandaloneRunner, App)
    └── src/main/java/nexa/framework/
        ├── NexaStandaloneRunner.java
        └── App.java
```

**Perubahan penting**:
- **Pemisahan CLI Runner (`nexa-cli`)**: Logika bootstrap program dan parsing argument CLI terpisah penuh dari Core Engine.
- **Pemisahan Scripting Engine (`nexa-script-engine`)**: Core Engine kini tidak terikat mati ke bahasa skrip *Nexa DSL*. Compiler/Interpreter DSL dipisah ke modul independen dan terdaftar dinamis via Java `ServiceLoader` (SPI).
- **Isolasi Plugin (`nexa-api`)**: Semua plugin eksternal (termasuk MQTT Plugin) hanya bergantung compile-time ke `nexa-api` tanpa bocoran class implementasi dari core engine.

---

## 📖 Dokumentasi Lanjutan

Untuk informasi detail tentang API runtime, lihat modul **`nexa-api`** di repository ini atau baca file Javadoc pada paket `nexa.framework.runtime.api`.

---

## 📜 Lisensi

Proyek ini dilisensikan di bawah lisensi internal Kufayeka Industrial Automation.

## 🛠️ Quick Start & Panduan Operasional (Untuk Pemula)

### 1. Konsep Penting Gradle & Multi-Module
Nexa saat ini menggunakan struktur **Multi-Project Gradle**. Artinya, terdapat satu project induk (`nexa-framework`) yang membawahi beberapa sub-project (modul) independen.

Ketika Anda menjalankan Gradle task di folder root `nexa-framework`, Gradle akan mengeksekusi task tersebut untuk **seluruh modul** yang terdaftar di `settings.gradle.kts` secara otomatis.

---

### 2. Apa itu `shadowJar`?
Secara default, task `jar` bawaan Java hanya membungkus kode program yang Anda tulis sendiri tanpa mengikutsertakan library eksternal (pihak ketiga) yang di-import. Jika dijalankan, Java akan melempar error `ClassNotFoundException` karena library tambahannya tidak ada.

**`shadowJar` (atau sering disebut Fat JAR / Uber JAR)** adalah task khusus dari Shadow Plugin yang bertugas untuk:
1. Mengompilasi kode program Anda.
2. Mengambil seluruh library pihak ketiga yang dibutuhkan (contoh: Jackson untuk parse JSON, Paho untuk MQTT).
3. Membundel (menggabungkan) semuanya ke dalam **satu file JAR tunggal**.

Dengan **Fat JAR**, Anda bisa menjalankan aplikasi di server mana saja hanya dengan satu file JAR tersebut menggunakan perintah `java -jar nama-file.jar` tanpa perlu menginstal library tambahan secara manual.

---

### 3. Membangun Proyek (Build All Modules)
Untuk membangun (compile dan bundling) seluruh modul sekaligus, jalankan perintah berikut di terminal/PowerShell pada direktori `d:\DEV\kufayeka\nexa-project\nexa-framework`:

```powershell
./gradlew.bat shadowJar
```

#### Dimana Hasil Build Masing-Masing Modul?
Ya! Perintah di atas akan secara otomatis membuild JAR pada masing-masing folder modulnya sendiri di dalam sub-folder `build/libs/`:

| Modul (Sub-project) | Lokasi File JAR Hasil Build | Jenis JAR | Kegunaan |
|---|---|---|---|
| **`nexa-api`** | `nexa-api/build/libs/nexa-api.jar` | Plain JAR | Berisi interface & kontrak publik. Hanya di-import oleh pengembang plugin (tidak dijalankan langsung). |
| **`nexa-core`** | `nexa-core/build/libs/nexa-core.jar` | Plain JAR | Core engine pemroses flow. Dipanggil oleh modul CLI. |
| **`nexa-script-engine`** | `nexa-script-engine/build/libs/nexa-script-engine.jar` | Plain JAR | Implementasi compiler/interpreter Nexa DSL. |
| **`nexa-cli`** | `nexa-cli/build/libs/nexa-cli.jar` | **Fat JAR** (Shadow) | **Runner Utama Standalone**. Ini adalah file JAR yang Anda jalankan untuk memutar engine Nexa via Command Line. |
| **`nexa-mqtt-plugin`** | `nexa-mqtt-plugin/build/libs/nexa-mqtt-plugin.jar` | **Fat JAR** (Shadow) | Plugin MQTT yang berisi node input/sink. Dimuat secara dinamis oleh Runner CLI. |

---

### 4. Cara Membangun & Menggunakan Plugin (Untuk Pemula)

Jika Anda ingin membuat atau mengompilasi plugin (contoh: `nexa-mqtt-plugin`):

#### A. Cara Mengompilasi Plugin Saja:
Jika Anda hanya mengubah kode di dalam folder `nexa-mqtt-plugin` dan tidak ingin mem-build ulang modul core lainnya, Anda bisa menjalankan task spesifik untuk modul tersebut:

```powershell
# Jalankan perintah ini di folder root nexa-framework
./gradlew.bat :nexa-mqtt-plugin:shadowJar
```
Perintah ini hanya akan mengompilasi dan menghasilkan file JAR plugin di folder `nexa-framework/nexa-mqtt-plugin/build/libs/nexa-mqtt-plugin.jar`.

#### B. Cara Menjalankan Plugin di Lingkungan Test (`nexa-test`):
Plugin di Nexa bersifat **Pluggable (Dinamis)**. Anda tidak perlu menyatukan kode plugin ke dalam core engine. Cukup ikuti langkah berikut:

1. Build plugin dengan perintah `./gradlew.bat :nexa-mqtt-plugin:shadowJar`.
2. Salin file JAR hasil build (`nexa-mqtt-plugin.jar`) ke folder `nexa-test/plugins/`.
3. Jalankan aplikasi runner (`nexa-core.jar` atau `nexa-cli.jar`) di folder `nexa-test`.
4. Runner akan mendeteksi file JAR tersebut di folder `plugins/` secara otomatis saat startup dan meregistrasikannya ke sistem.

---

### 5. Menjalankan Unit & Integration Test
Untuk menjalankan seluruh suite pengujian otomatis di seluruh modul:

```powershell
./gradlew.bat test
```
*Hasil laporan pengujian (HTML report) akan dibuat otomatis pada folder `nexa-core/build/reports/tests/test/index.html`.*

---

### 6. Cara Menjalankan Aplikasi Utama (Running CLI)

#### Struktur Folder untuk Menjalankan App:
Untuk menjalankan aplikasi secara mandiri (misal pada folder `nexa-test`), susunlah file JAR hasil build Anda seperti ini:

```
workspace-folder/
│
├── nexa-core.jar                  # JAR Runner Utama (salinan dari nexa-cli/build/libs/nexa-cli.jar)
│
├── workspaces/
│   └── workspace-main.json        # File JSON yang mendefinisikan graf/topologi flow
│
└── plugins/                       # Folder opsional untuk menaruh plugin eksternal
    └── nexa-mqtt-plugin.jar       # Biner plugin MQTT yang Anda salin ke sini
```

#### Jalankan Menggunakan Java Runtime:
Buka terminal pada folder tersebut, lalu jalankan:

```powershell
java -jar nexa-core.jar
```

* **Default Workspace**: Jika Anda tidak menyertakan argumen path file JSON, runner secara otomatis akan memuat file `workspaces/workspace-main.json`.
* **Dynamic Plugin Loading**: Saat aplikasi dijalankan, runner secara dinamis akan memindai folder `plugins/` yang berada sejajar dengannya, mendeteksi semua file `.jar` di dalamnya, dan memuat class plugin eksternal secara otomatis menggunakan `ServiceLoader`.
* **Dynamic Scripting Loader**: Script engine untuk executor node (seperti Nexa DSL compiler) akan di-load secara dinamis saat runtime mendeteksi tipe script yang sesuai pada workspace JSON.

---

## 📖 Dokumentasi Lanjutan

* **[Spesifikasi Lengkap Nexa DSL V1](README-NEXA-DSL.md)**: Panduan syntax dan logika scripting dalam executor node.
* **[Panduan Membuat Plugin Nexa Framework](README-NEXA-PLUGIN.md)**: Dokumentasi API untuk menulis source, sink, atau function plugin baru menggunakan modul `nexa-api`.

---

## 📜 Lisensi
Proyek ini dilisensikan di bawah lisensi internal Kufayeka Industrial Automation.

