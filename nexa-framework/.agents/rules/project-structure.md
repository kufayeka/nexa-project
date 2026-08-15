---
trigger: always_on
---

# Project Structure: Domain-Driven Packaging & SOP

Organisasikan paket kode berdasarkan **Domain**, bukan berdasarkan layer teknis.

## Daftar Domain yang Valid
Seluruh kode runtime utama berada di bawah paket `nexa.framework.runtime.domain.<nama_domain>`:

1. **`workspace`**: Representasi model workspace, flow, dan loader JSON.
2. **`deployment`**: Kompilasi graf flow executable, validasi topologi, dan deploy lifecycle.
3. **`execution`**: Orkestrasi routing pesan, thread pool virtual, context eksekusi, isolasi state, dan engine utama.
4. **`scheduler`**: Timed/manual trigger, polling sensors, dan scheduling task input node.
5. **`statistics`**: Perekaman metrik durasi, status running, completed, dan failed.
6. **`scripting`**: Compiler & runtime untuk pengeksekusian script (Nexa DSL, JS).

---

## Standardisasi SOP Sub-paket per Domain
Setiap paket domain wajib mematuhi pemisahan sub-paket internal berikut untuk menjaga prinsip Separation of Concerns:

* **`api/`** (atau **`interfaces/`**): Berisi antarmuka (interface) publik yang menjadi kontrak hubungan dengan domain lain.
* **`controller/`**: Entry point utama domain, mengimplementasikan antarmuka API, dan mengorkestrasi service internal.
* **`service/`**: Logika bisnis internal yang bersifat *package-private* (tidak boleh diekspos ke luar domain).
* **`model/`**: Record data/DTO yang immutable.
* **`registry/`**: Registri untuk dynamic handlers/plugins (jika ada).
* **`helpers/`** (atau **`utils/`**): Utilitas dan parser yang khusus digunakan di domain ini saja.
* **`exception/`**: Kelas exception khusus domain.
* **`internal/`**: Logika detail internal/AST parser yang tidak boleh diakses oleh kelas lain di luar domain.
* **`<DomainName>Module.java`**: Berada di root paket domain. Berperan sebagai container konfigurasi dan perakit dependency injection internal domain (*Composition Root* tingkat domain).

---

## Larangan Keras
* **DILARANG** membuat paket utilitas generik global seperti `common/`, `util/`, `helper/`, atau `misc/`. Semua utilitas harus spesifik diletakkan di sub-paket `helpers/` di bawah domain terkait (misal: `DeepCopyUtil` di execution, `DurationParser` di scheduler).
* **DILARANG** melakukan import langsung ke kelas implementasi (`service/` atau `controller/`) milik domain lain. Akses antar-domain **wajib** melalui antarmuka di sub-paket `api/`.

---

## Kewajiban Pemeliharaan Dokumentasi (README.md)
* Setiap Domain wajib memiliki berkas `README.md` di root direktorinya yang menjelaskan tugas, tanggung jawab, komponen utama, serta Standar Operasional Prosedur (SOP) ekspansi domain tersebut.
* **WAJIB** memperbarui atau membuat berkas `README.md` jika terjadi perubahan yang mengubah alur sistem, logika bisnis, atau pembagian tanggung jawab modul. Segala bentuk penyimpangan dokumentasi (documentation drift) dilarang keras.