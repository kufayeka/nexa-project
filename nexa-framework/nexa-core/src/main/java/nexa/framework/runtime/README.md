# Nexa Runtime Architecture: Domain-Driven Design & Pure DI

Dokumen ini menjelaskan arsitektur inti Nexa Runtime, pengorganisasian kode berbasis domain (Domain-Driven Packaging), prinsip Pure Dependency Injection (DI), serta Standar Operasional Prosedur (SOP) untuk melakukan refactoring dan ekspansi sistem.

---

## 1. Arsitektur Domain-Driven & Pola Modul

Nexa Runtime membagi seluruh tanggung jawab fungsionalnya ke dalam **Domain Independen** di bawah paket `nexa.framework.runtime.domain.<nama_domain>`. Setiap domain bertindak sebagai modul yang mandiri, terenkapsulasi, dan hanya mengekspos kontrak stabil (antarmuka) ke domain lainnya.

### Daftar Domain yang Valid:
1. **`workspace`**: Memuat, mem-parsing, dan merepresentasikan struktur data Workspace/Flow/Node mentah (JSON parsing & model).
2. **`deployment`**: Mengompilasi model mentah menjadi struktur graf flow executable, melakukan validasi topologi, mendeteksi siklus, dan menangani deployment.
3. **`execution`**: Orkestrasi routing pesan asinkronus downstream, manajemen context eksekusi paralel (virtual threads), isolasi state (deep copy), dan pembatalan/timeout eksekusi.
4. **`scheduler`**: Menangani aktivasi input node (misalnya pemicu periodik atau manual), registri pemicu, dan penjadwalan interval.
5. **`statistics`**: Pencatatan metrik operasional flow secara asinkronus (durasi rata-rata, completed, failed, rejected, running).
6. **`scripting`**: Compiler & runtime untuk pengeksekusian script (Nexa DSL, JavaScript).

---

## 2. Standardisasi Struktur Domain (SOP Sub-paket)

Setiap paket domain **WAJIB** mengikuti struktur sub-paket standar berikut untuk menjaga Separation of Concerns (SoC) dan menyembunyikan detail implementasi:

```text
nexa.framework.runtime.domain.<nama_domain>/
├── api/             (Atau interfaces/: Berisi interface/kontrak publik yang boleh diimpor oleh domain lain)
├── controller/      (Pintu masuk utama domain, mengimplementasikan antarmuka di api/)
├── service/         (Logika bisnis internal, bersifat package-private - dilarang diekspos keluar domain)
├── model/           (Record data/DTO immutable spesifik domain)
├── registry/        (Optional: Tempat pendaftaran handler/plugin/extension)
├── helpers/         (Optional: Utilitas/parser spesifik domain - dilarang ada paket util global)
├── exception/       (Optional: Exception spesifik domain)
├── internal/        (Optional: Detail implementasi internal yang disembunyikan sepenuhnya dari sub-paket lain)
└── <DomainName>Module.java (Composition Root tingkat domain, merakit dependensi internal domain)
```

---

## 3. Mekanisme Pure Dependency Injection (Wiring)

Nexa **melarang keras** penggunaan framework DI otomatis (seperti Spring Boot `@Autowired`, `@Component`, atau Guice `@Inject`) untuk menghindari *magic behavior*, meningkatkan startup time, dan menjamin deteksi error sejak proses kompilasi (*compile-time safety*).

* **Wiring Pusat**: Perakitan seluruh modul dilakukan secara manual di konstruktor [DefaultRuntimeEngine.java](file:///d:/DEV/kufayeka/nexa-framework/app/src/main/java/nexa/framework/runtime/domain/execution/service/DefaultRuntimeEngine.java).
* **Dependency Inversion (DIP)**: Jika Domain A membutuhkan layanan dari Domain B, Domain A **hanya boleh mengimpor interface** yang berada di dalam paket `domain.b.api.*`. Domain A **DILARANG KERAS** mengimpor langsung kelas implementasi konkret (`controller/` atau `service/`) milik Domain B.
* **Memutus Circular Dependency**: Jika ada hubungan melingkar (misal Execution butuh Scheduler untuk mengaktifkan input, dan Scheduler butuh Execution untuk memicu jalannya data), perkenalkan sebuah Interface Inversi di paket `api/` dari domain yang dipanggil, lalu lakukan *injection* lewat constructor/setter di Composition Root.

---

## 4. SOP Rekayasa Kode (Refactor & Expand)

### SOP A: Menambah Fitur/Logika Baru ke Domain yang Ada
1. **Identifikasi Kebutuhan Ekspos**: Apakah fitur baru ini perlu dipanggil dari luar domain tersebut?
   * **Jika YA**: Tulis kontrak metodenya di interface dalam paket `api/`. Buat implementasinya di `controller/` atau delegasikan dari `controller/` ke internal `service/`.
   * **Jika TIDAK**: Tulis kodenya langsung di dalam paket `service/` atau `internal/` sebagai kelas dengan visibilitas *package-private* (tanpa keyword `public`).
2. **Normalisasi Data**: Jika fitur menerima parameter DTO baru, gunakan Java **Record** di sub-paket `model/` dan gunakan *compact constructor* untuk membersihkan parameter `null`.
3. **Instansiasi Internal**: Jika kelas baru membutuhkan dependensi internal domain, rakit instansiasinya di dalam berkas `<DomainName>Module.java`.

### SOP B: Membuat Domain Baru
1. Buat folder domain baru di bawah `nexa.framework.runtime.domain.<nama_domain>`.
2. Buat sub-paket standar: `api`, `controller`, `service`, `model`.
3. Buat berkas `<DomainName>Module.java` di root paket domain baru tersebut untuk merakit seluruh komponen internal domain.
4. Ekspos layanan utama domain baru melalui interface di sub-paket `api/`.
5. Daftarkan dan rakit modul baru di dalam Composition Root [DefaultRuntimeEngine.java](file:///d:/DEV/kufayeka/nexa-framework/app/src/main/java/nexa/framework/runtime/domain/execution/service/DefaultRuntimeEngine.java).

### SOP C: Melakukan Refactoring
1. **Aturan Panjang Kode**: Batasi panjang file kelas maksimal +- 400 baris. Jika melebihi batasan ini, pecah menjadi sub-layanan yang lebih fokus pada satu tanggung jawab tunggal (*Single Responsibility Principle*).
2. **Komentar**: Tulis komentar penjelas alur kerja menggunakan **Bahasa Indonesia** untuk setiap kelas utama, helper, metode kritis, dan alur asinkronus yang kompleks. Jelaskan *WHY* (alasan keputusan desain), bukan *WHAT* (kode sintaksis standar).
3. **Validasi & Pengujian**: Setiap kali melakukan perubahan logika bisnis, jalankan `.\gradlew.bat compileJava compileTestJava` dilanjutkan dengan `.\gradlew.bat test` untuk memverifikasi bahwa perubahan tidak memecahkan kompatibilitas.
