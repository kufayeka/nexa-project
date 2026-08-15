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

## 🛠️ Quick Start & Panduan Operasional

### 1. Prasyarat System
* **Java Development Kit (JDK) 25** atau lebih baru.
* **Gradle build tool** (disediakan gradle wrapper bawaan).

---

### 2. Membangun Proyek (Build)
Untuk mengompilasi seluruh modul dan memaketkannya menjadi file JAR, jalankan perintah berikut di folder root proyek:

```powershell
./gradlew.bat shadowJar
```

#### Hasil Output Kompilasi & Penjelasannya:
Setelah proses build selesai, Anda akan mendapatkan file JAR pada masing-masing modul:

| Modul | Lokasi File JAR | Kegunaan |
|---|---|---|
| **`nexa-api`** | `nexa-api/build/libs/nexa-api.jar` | **Public API Contract**. Hanya berisi interface, model, dan lifecycle (`NexaPlugin`, `RuntimeMessage`, dll.). Digunakan sebagai dependency compile-only saat Anda mengembangkan plugin eksternal baru (seperti plugin MQTT). |
| **`nexa-core`** | `nexa-core/build/libs/nexa-core.jar` | **Core Runtime Engine**. Berisi logika utama pemrosesan flow, parser workspace JSON, virtual threads scheduler, dan statistics. |
| **`nexa-script-engine`** | `nexa-script-engine/build/libs/nexa-script-engine.jar` | **Scripting Engine (Nexa DSL)**. Menyediakan implementasi compiler dan interpreter bahasa Nexa DSL. |
| **`nexa-cli`** | `nexa-cli/build/libs/nexa-cli.jar` | **Standalone Runner CLI**. Merupakan **Fat JAR** runnable yang membungkus runner utama (`NexaStandaloneRunner`), core engine, dan dependensi lainnya. |

---

### 3. Menjalankan Unit & Integration Test
Untuk menjalankan seluruh suite pengujian otomatis (unit tests, validation, dan platform integration tests):

```powershell
./gradlew.bat test
```
*Hasil laporan pengujian (HTML report) akan dibuat otomatis pada folder `nexa-core/build/reports/tests/test/index.html`.*

---

### 4. Cara Menjalankan Aplikasi (Running standalone CLI)

#### Persyaratan File & JAR untuk Menjalankan App:
Untuk menjalankan aplikasi secara mandiri di production atau test env, Anda membutuhkan struktur folder seperti berikut:

```
workspace-folder/
│
├── nexa-cli.jar                   # JAR Runner Utama (diambil dari nexa-cli/build/libs/)
│
├── workspaces/
│   └── workspace-main.json        # File JSON yang mendefinisikan graf/topologi flow
│
└── plugins/                       # Folder opsional untuk menaruh plugin eksternal
    └── nexa-mqtt-plugin.jar       # Contoh biner plugin MQTT eksternal
```

#### Perintah untuk Menjalankan (Running command):
Jalankan file jar utama menggunakan java runtime:

```powershell
java -jar nexa-cli.jar [path_ke_file_workspace.json]
```

* **Default Workspace**: Jika Anda tidak menyertakan argumen path file JSON, runner secara otomatis akan memuat file `workspaces/workspace-main.json`.
* **Dynamic Plugin Loading**: Saat aplikasi dijalankan, `NexaStandaloneRunner` secara dinamis akan memindai folder `plugins/` yang berada sejajar dengannya, mendeteksi semua file `.jar` di dalamnya, dan memuat class plugin eksternal secara otomatis menggunakan `ServiceLoader`.
* **Dynamic Scripting Loader**: Script engine untuk executor node (seperti Nexa DSL compiler) akan di-load secara dinamis saat runtime mendeteksi tipe script yang sesuai pada workspace JSON.

#### Menjalankan Lewat Gradle (Development Mode):
Jika Anda masih dalam tahap pengembangan dan ingin langsung menjalankan runner tanpa memaketkan JAR:

```powershell
./gradlew :nexa-cli:runStandalone -PappArgs="workspaces/workspace-main.json"
```

---

## 📖 Dokumentasi Lanjutan

* **[Spesifikasi Lengkap Nexa DSL V1](README-NEXA-DSL.md)**: Panduan syntax dan logika scripting dalam executor node.
* **[Panduan Membuat Plugin Nexa Framework](README-NEXA-PLUGIN.md)**: Dokumentasi API untuk menulis source, sink, atau function plugin baru menggunakan modul `nexa-api`.

---

## 📜 Lisensi
Proyek ini dilisensikan di bawah lisensi internal Kufayeka Industrial Automation.
