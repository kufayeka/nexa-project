# Deployment Domain

Domain ini bertanggung jawab untuk memvalidasi struktur topologi graf flow, mendeteksi kesalahan konfigurasi (seperti siklus tak terbatas atau node tak terjangkau), serta mengompilasi model mentah menjadi struktur graf flow executable (`CompiledWorkspace`).

## Paket & Komponen Utama

* **`api/`**:
  * `DeploymentService`: Antarmuka luar domain untuk memicu kompilasi workspace dan invalidasi skrip.
* **`controller/`**:
  * `DefaultDeploymentService`: Implementasi pengontrol publik yang mendelegasikan tugas ke compiler internal.
* **`service/`**:
  * `FlowCompiler`: Kelas inti kompilasi yang merakit node, mengompilasi skrip logika, dan membuat struktur routing downstream (`CompiledFlow`).
  * `FlowValidator`: Validator topologi graf untuk memastikan flow tidak memiliki dependensi sirkular (loop) tertutup dan mendeteksi node yang terisolasi.
* **`model/`**:
  * `CompiledWorkspace`, `CompiledFlow`, `CompiledNode`: Representasi hasil kompilasi graf yang siap dieksekusi di runtime engine.
* **`exception/`**:
  * `ValidationException`: Exception yang dilemparkan jika struktur topologi atau skrip tidak valid.

## Panduan Ekspansi & Refactoring (SOP)

### Menambahkan Aturan Validasi Topologi Baru
1. Buka berkas `FlowValidator.java` di sub-paket `service/`.
2. Tulis metode validasi baru (misalnya memeriksa batasan jumlah koneksi pada port tertentu).
3. Jika validasi gagal, lempar `ValidationException` dengan pesan kesalahan yang deskriptif untuk membantu *debugging* di sisi pengguna.
4. Pastikan unit test kompilasi ditambahkan untuk menguji skenario kegagalan validasi tersebut.
