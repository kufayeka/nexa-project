# Scheduler Domain

Domain ini bertanggung jawab untuk menangani pemicu/input dari luar (seperti scheduler berkala atau trigger manual) yang memulai jalannya suatu aliran data (flow).

## Paket & Komponen Utama

* **`api/`**:
  * `InputNodeHandler`: Antarmuka yang wajib diimplementasikan oleh setiap handler tipe input node baru.
  * `InputNodeActivationPort`: Gerbang interface callback agar handler dapat berinteraksi kembali dengan runtime scheduler dan memicu eksekusi downstream.
* **`registry/`**:
  * `InputNodeHandlerRegistry`: Registri internal untuk mencatat dan mencari handler pemicu input yang didukung berdasarkan tipe string JSON node.
* **`service/`**:
  * `InputActivationService`: Pengendali aktivasi yang mengimplementasikan `InputActivator` (dari domain execution) untuk mengontrol siklus hidup penjadwalan input secara asinkronus.
  * `TimedTriggerInputNodeHandler`: Handler bawaan untuk pemicu berkala berdasarkan konfigurasi interval.
  * `ManualInputNodeHandler`: Handler bawaan untuk pemicu manual lewat pemanggilan API eksternal.
* **`model/`**:
  * `InputNodeRuntimeState`: Objek penyimpan state eksekusi konkuren dan referensi pembatalan penjadwalan aktif.
* **`helpers/`**:
  * `DurationParser`: Parser untuk mengubah representasi waktu string (seperti "100ms", "5s", "2m") menjadi durasi Java.

## Panduan Ekspansi & Refactoring (SOP)

### Menambahkan Tipe Input Node Baru
1. Buat kelas handler baru di sub-paket `service/` yang mengimplementasikan antarmuka `InputNodeHandler`.
2. Implementasikan metode `nodeType()` untuk mengembalikan string pengenal node (misal: `"sensor-polling"`).
3. Implementasikan metode `activate(...)` untuk mengatur penjadwalan/polling. Gunakan metode penjadwalan bawaan atau gunakan thread pool runtime via `InputNodeActivationPort`.
4. Daftarkan kelas handler baru Anda ke dalam instansiasi registri di constructor [SchedulerModule.java](file:///d:/DEV/kufayeka/nexa-framework/app/src/main/java/nexa/framework/runtime/domain/scheduler/SchedulerModule.java).
5. Tulis unit test di folder test untuk memverifikasi fungsionalitas pemicu baru tersebut.
