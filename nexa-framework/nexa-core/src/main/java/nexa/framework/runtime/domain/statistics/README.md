# Statistics Domain

Domain ini bertanggung jawab untuk mencatat metrik kinerja flow secara asinkronus (konkuren) dan bebas kunci (lock-free) demi meminimalkan overhead performa pada jalur kritis eksekusi pesan.

## Paket & Komponen Utama

* **`api/`**:
  * `StatisticsService`: Kontrak luar domain untuk pembuatan instansi statistika flow baru.
* **`controller/`**:
  * `DefaultStatisticsService`: Pengontrol implementasi default dari StatisticsService.
* **`service/`**:
  * `FlowStatistics`: Kelas akumulator data statistik menggunakan `LongAdder` Java untuk menghindari contention antar thread virtual paralel.
* **`model/`**:
  * `RuntimeStatisticsSnapshot`: Representasi data snapshot statistik flow yang immutable.

## Panduan Ekspansi & Refactoring (SOP)

### Menambahkan Metrik Baru (Misalnya: Jumlah Throughput Pesan Per Detik)
1. Buka berkas `FlowStatistics.java` di sub-paket `service/`.
2. Tambahkan variabel instansi `LongAdder` baru (misalnya `messageCount`).
3. Tambahkan metode publik untuk menginkremen nilai akumulator tersebut.
4. Perbarui record `RuntimeStatisticsSnapshot.java` di sub-paket `model/` untuk menyertakan bidang data baru ini.
5. Perbarui metode `snapshot()` pada `FlowStatistics.java` untuk mengembalikan snaphot baru tersebut dengan data terakumulasi terbaru.
