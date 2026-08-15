# Runtime API Deep Dive

Dokumen ini fokus ke kontrak API runtime yang dipakai oleh layer luar (editor, gateway, service orchestrator).

Referensi interface utama: [app/src/main/java/nexa/framework/runtime/api/RuntimeEngine.java](app/src/main/java/nexa/framework/runtime/api/RuntimeEngine.java)

## Tujuan Runtime API

- Menyediakan kontrol lifecycle runtime dan workspace.
- Menyediakan operasi deploy/undeploy yang aman.
- Menyediakan trigger manual untuk kebutuhan external system.
- Menyediakan statistik internal runtime.

## Metode RuntimeEngine

### startRuntime()

Mengaktifkan runtime dan menjalankan activation InputNode untuk workspace yang enable.

### stopRuntime()

Menghentikan activation input dan membatalkan execution yang masih aktif.

### deploy(workspaceDefinition)

Pipeline deploy:

1. Validate
2. Compile
3. Register
4. Ready

### undeploy(workspaceId)

Melepas workspace dari registry runtime dan menghentikan aktivitasnya.

### disable(workspaceId)

Menonaktifkan workspace tanpa menghapus definisinya.

### enable(workspaceId)

Mengaktifkan kembali workspace dan mengaktifkan input node jika runtime sudah berjalan.

### trigger(workspaceId, flowId, inputNodeId, message)

Trigger manual untuk InputNode tipe manual-input.

Catatan penting:

- Ini untuk external/manual trigger (misal visual editor, API endpoint, test harness).
- Ini bukan mekanisme utama timed-trigger.

### setNodeEnabled(workspaceId, flowId, nodeId, enabled)

Mengaktifkan/menonaktifkan node saat runtime hidup.

Jika node input dimatikan, scheduler input node dibersihkan.

### statistics(workspaceId, flowId)

Mengambil snapshot statistik flow.

## Runtime API Yang Perlu Dijaga Stabil

Kontrak berikut sebaiknya tetap stabil untuk kompatibilitas:

- Struktur ID workspace/flow/node
- Lifecycle method naming
- Trigger API signature
- Statistics snapshot semantics

Jika perlu breaking change, buat versi API baru atau adapter layer.

## Checklist Saat Menambah Fitur API

1. Tentukan apakah fitur masuk kontrak RuntimeEngine atau internal service.
2. Pastikan perubahan tidak mem-bypass validation/compile pipeline.
3. Pastikan cleanup tetap deterministik.
4. Tambah test untuk concurrency, timeout, dan failure isolation.
5. Perbarui dokumentasi ini.
