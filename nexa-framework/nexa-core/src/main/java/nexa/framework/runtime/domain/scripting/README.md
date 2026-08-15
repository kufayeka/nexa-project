# Scripting Domain

Domain ini bertanggung jawab sebagai mesin kompilator dan eksekutor skrip logika yang ditulis oleh pengguna untuk memproses data pesan di dalam node graf. Secara default, domain ini mendukung bahasa skrip bawaan Nexa DSL.

## Paket & Komponen Utama

* **`api/`**:
  * `ScriptEngine`, `ScriptCompiler`, `CompiledScript`: Antarmuka inti untuk mendaftarkan dan menjalankan mesin skrip baru.
  * `ScriptExecutionResult`, `ScriptExecutionControl`, `ScriptRuntimeApi`: Antarmuka jembatan interaksi runtime.
* **`model/`**:
  * `ScriptRuntimeContext`, `DefaultScriptExecutionResult`: Konteks data untuk eksekusi skrip terisolasi.
* **`registry/`**:
  * `ScriptEngineRegistry`: Registri pencarian mesin skrip terdaftar berdasarkan bahasa (misal: "nexa" atau "js").
* **`service/`**:
  * `NexaScriptEngine`, `NexaScriptCompiler`: Implementasi compiler dan engine bawaan untuk mengeksekusi Nexa DSL.
* **`internal/nexa/`**:
  * Lexer, AST Parser, dan Interpreter Evaluator (`NexaParser`, `NexaTokenizer`, `NexaRuntime`, dll.) yang disembunyikan sepenuhnya sebagai detail internal bahasa Nexa.

## Panduan Ekspansi & Refactoring (SOP)

### Menambahkan Dukungan Bahasa Skrip Baru (Misalnya: Python/JS)
1. Buat implementasi baru dari `ScriptEngine` dan `ScriptCompiler` di bawah paket `service/`.
2. Daftarkan engine baru tersebut dengan menambahkan kelas konkret tersebut ke dalam konfigurasi Service Loader di berkas:
   `app/src/main/resources/META-INF/services/nexa.framework.runtime.domain.scripting.api.ScriptEngine`.
3. Kompilasi ulang projek dan pastikan mesin skrip baru Anda secara otomatis terdeteksi oleh `ScriptingModule` saat inisialisasi boot.
