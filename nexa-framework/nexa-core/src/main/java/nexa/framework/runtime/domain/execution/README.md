# Execution Domain

Domain ini bertanggung jawab untuk mengeksekusi dan mengalirkan pesan (`RuntimeMessage`) melalui graf flow, mengelola *virtual thread pool* untuk eksekusi asinkronus konkuren, mengisolasi mutable state lewat penyalinan mendalam (deep copy), serta menangani siklus hidup eksekusi global.

## Paket & Komponen Utama

* **`api/`**:
  * `ExecutionService`: Antarmuka luar domain untuk kontrol siklus hidup runtime, deployment, pengaktifan node, dan trigger manual.
  * `InputActivator`: Antarmuka inversi (DIP) agar domain execution dapat memerintahkan domain scheduler mengaktifkan/menonaktifkan pemicu input tanpa kopling melingkar.
* **`controller/`**:
  * `DefaultExecutionService`: Pengontrol utama yang mengimplementasikan `ExecutionService` dan mengelola state peta `workspaces` aktif.
* **`service/`**:
  * `RuntimeExecutionService`: Orkestrator eksekusi internal yang mengontrol virtual thread worker pool dan scheduler pool.
  * `NodeExecutor`: Pelaksana eksekusi downstream paralel yang menyusun penyerahan rute node dan mendistribusikan salinan pesan (deep copy) pada fan-out.
  * `ExecutionLifecycleManager`: Manajer siklus hidup yang mengontrol pendaftaran eksekusi aktif, pembatalan, pembersihan data (*cleanup*), dan pemicu timeout global.
* **`model/`**:
  * `WorkspaceRuntime`, `FlowRuntime`, `NodeRuntime`, `ActiveExecution`: Struktur data dinamis yang menyimpan state runtime.
  * `ExecutionContext`, `ExecutionStatus`: Konteks data transaksi eksekusi tunggal.
  * `RuntimeMessage`: Wrapper data payload pesan yang mengalir antar node.
* **`helpers/`**:
  * `DeepCopyUtil`: Utilitas penyalinan mendalam menggunakan switch pattern matching untuk menjamin isolasi data pesan antar cabang.

## Panduan Ekspansi & Refactoring (SOP)

### Menambahkan Logika Routing Downstream Baru
1. Modifikasi metode rute di dalam `NodeExecutor.java`.
2. Jika Anda membuat node cabang baru yang memerlukan fan-out, pastikan pesan disalin menggunakan `DeepCopyUtil.deepCopyValue(...)` sebelum dikirim ke thread target untuk mencegah *race condition* penulisan variabel.
3. Selalu pastikan metode pemicu eksekusi diakhiri dengan pemanggilan `completeTask` di dalam blok `finally` untuk memastikan sumber daya memori dilepas saat selesai.
