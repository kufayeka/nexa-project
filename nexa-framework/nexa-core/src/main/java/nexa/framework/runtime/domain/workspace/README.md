# Workspace Domain

Domain ini bertanggung jawab untuk memuat, mem-parsing, dan merepresentasikan struktur data Workspace, Flow, dan Node mentah yang didefinisikan dari file JSON.

## Paket & Komponen Utama

* **`api/`**: 
  * `WorkspaceService`: Kontrak antarmuka publik untuk memuat data workspace dari file/teks.
* **`service/`**:
  * `WorkspaceJsonLoader`: Implementasi concrete `WorkspaceService` menggunakan Jackson library untuk parsing JSON.
* **`model/`**:
  * `WorkspaceDefinition`, `FlowDefinition`, `NodeDefinition`, `ConnectionDefinition`, `InputExecutionPolicyDefinition`: Java Records immutable yang merepresentasikan model graf alur data mentah.
  * `NodeCategory`: Enum kategori node (`INPUT`, `EXECUTOR`, `OUTPUT`).

## Panduan Ekspansi & Refactoring (SOP)

### Menambahkan Properti/Atribut Baru pada JSON
1. Tambahkan bidang baru pada Record yang bersangkutan di sub-paket `model/`.
2. Gunakan anotasi Jackson `@JsonProperty("<nama_field_json>")` jika nama variabel berbeda dengan kunci di JSON.
3. **WAJIB** definisikan default value pada *canonical constructor* atau *compact constructor* record tersebut. Jangan biarkan field bernilai `null` agar domain lain aman dari `NullPointerException`.
4. Jika properti baru memerlukan validasi topologi, tambahkan aturan validasinya di domain `deployment`.
