# 🏷️ Nexa Asset Manager Plugin

Modul plugin `nexa-asset-manager` untuk **Nexa Framework** bertindak sebagai motor pengelola tag/attribute terdistribusi berbasis aset hierarkis, template blueprint, dan mesin kalkulasi script dinamis (Nexa DSL) dengan dukungan I/O non-blocking berbasis Java **Virtual Threads**[cite: 5].

---

## 🚀 Fitur Utama

1. **Hierarchy Tree & Blueprint Templates**: Mendefinisikan blueprint template aset (misal: `PumpTemplate`), parameter substitusi (`${param}`), serta pohon hierarki aset tak terbatas (misal: `/SiteA/Line1/Pump1`).
2. **Thread-Safe VTQ (Value, Timestamp, Quality)**: Setiap attribute menyimpan nilai saat ini (*value*), nilai sebelumnya (*oldValue*), timestamp pembaruan, dan status kualitas data (*quality*), diamankan menggunakan `ReentrantLock` sehingga ramah thread virtual OS.
3. **Execution Triggers**:
    *   **`INTERVAL`**: Perhitungan terjadwal otomatis (misal: `1s`, `500ms`) menggunakan Virtual Threads scheduler.
    *   **`ON_CHANGE`**: Pendeteksian dependensi otomatis sewaktu script membaca tag lain via `assetManager.read()`. Saat tag hulu berubah, seluruh anak dependensi di bawahnya otomatis di-recalculating.
    *   **`ON_WRITE`**: Interceptor & validator data masukan yang memproses nilai baru (`self.newValue`) sebelum disimpan ke atribut.
4. **Cycle Prevention**: Sistem proteksi rekursif berbasis `ThreadLocal` stack untuk memecah loop melingkar tak berujung (misal: `A -> B -> A`).

---

## 🛠️ Cara Integrasi & Konfigurasi

### 1. Daftarkan Resource Plugin
Masukkan plugin tipe `asset-manager` ke dalam bagian `resources` pada file workspace utama Anda (misalnya `workspace-main.json`):

```json
{
  "id": "workspace-modbus-to-mqtt",
  "resources": [
    {
      "id": "asset-manager-resource",
      "type": "asset-manager",
      "enabled": true,
      "config": {
        "configFile": "workspaces/workspace-assets-2.json"
      }
    }
  ]
}
```

### 2. Definisikan Aset Workspace (`workspace-assets.json`)
Buat file konfigurasi aset terpisah (seperti `workspaces/workspace-assets-2.json`):

```json
{
  "id": "workspace-assets-2",
  "templates": [
    {
      "name": "PumpTemplate",
      "attributes": [
        {
          "name": "flowRate",
          "dataType": "FLOAT32",
          "value": 0.0
        },
        {
          "name": "inletPressure",
          "dataType": "FLOAT32",
          "value": 1.2
        },
        {
          "name": "outletPressure",
          "dataType": "FLOAT32",
          "value": 4.5
        },
        {
          "name": "efficiency",
          "dataType": "FLOAT32",
          "value": 0.0,
          "calculationConfig": {
            "triggerType": "ON_CHANGE",
            "script": "return (assetManager.read(\"../outletPressure\") - assetManager.read(\"../inletPressure\")) * assetManager.read(\"../flowRate\") * 0.85;"
          }
        },
        {
          "name": "totalRunHours",
          "dataType": "FLOAT64",
          "value": 100.0,
          "calculationConfig": {
            "triggerType": "INTERVAL",
            "intervalExpr": "1s",
            "script": "return self.value + 0.01;"
          }
        },
        {
          "name": "status",
          "dataType": "STRING",
          "value": "STOPPED",
          "calculationConfig": {
            "triggerType": "ON_WRITE",
            "script": "return self.newValue.toUpperCase();"
          }
        }
      ]
    }
  ],
  "assets": [
    {
      "name": "SiteB",
      "children": [
        {
          "name": "LineA",
          "children": [
            {
              "name": "Pump1",
              "template": "PumpTemplate",
              "parameters": {},
              "attributes": [
                {
                  "name": "location",
                  "dataType": "STRING",
                  "value": "Bay-4"
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## 📝 Scripting API (Nexa DSL)

### Global Object: `assetManager`
Tersedia baik di dalam script kalkulasi atribut maupun di node script aliran workspace.
*   `assetManager.read("absolute_or_relative_path")`: Membaca nilai dari atribut (misal: `assetManager.read("../temperature")`).
*   `assetManager.readVTQ("path")`: Membalikan peta berisi metadata `value`, `oldValue`, `timestamp`, dan `quality`.
*   `assetManager.write("path", value)`: Menulis nilai baru ke atribut. *(Hanya diperbolehkan dari flow node executor. Menulis secara langsung di dalam script perhitungan kalkulasi atribut dilarang keras untuk menjaga konsistensi state)*.

### Context Object: `self`
Hanya tersedia di dalam script kalkulasi milik atribut bersangkutan:
*   `self.value`: Nilai atribut saat ini.
*   `self.oldValue`: Nilai atribut sebelum perubahan.
*   `self.newValue`: Nilai masukan yang baru masuk *(hanya untuk trigger tipe ON_WRITE)*.
*   `self.timestamp`: Epoch millisecond saat ini.
*   `self.quality`: String kualitas data (misal: `"GOOD"`).

---

## 🎛️ Native Flow Nodes

Selain menggunakan `assetManager` melalui script Nexa DSL, Anda juga dapat menggunakan node native bawaan di dalam diagram alir (flows) workspace utama:

### 1. Node `asset-write` (Pencatatan Aset)
Menulis nilai dari payload pesan ke dalam atribut Asset Manager.
*   `type`: `asset-write`
*   `category`: `EXECUTOR`
*   `config`:
    *   `attributePath`: Path absolut ke target atribut (misal: `/SiteB/LineA/Pump1/flowRate`).
    *   `valueSource`: Path JSON asal nilai di dalam payload pesan (Default: `payload.value`).

**Contoh Konfigurasi Node:**
```json
{
  "id": "asset-write-flowRate",
  "category": "EXECUTOR",
  "type": "asset-write",
  "enabled": true,
  "config": {
    "attributePath": "/SiteB/LineA/Pump1/flowRate",
    "valueSource": "payload.value"
  }
}
```

### 2. Node `asset-read` (Pembacaan Aset)
Membaca nilai dari atribut Asset Manager dan menyimpannya ke dalam payload pesan.
*   `type`: `asset-read`
*   `category`: `EXECUTOR`
*   `config`:
    *   `attributePath`: Path absolut ke target atribut (misal: `/SiteB/LineA/Pump1/efficiency`).
    *   `outputField`: Path JSON tujuan tempat nilai disimpan di dalam payload pesan (Default: `payload.value`).

**Contoh Konfigurasi Node:**
```json
{
  "id": "asset-read-efficiency",
  "category": "EXECUTOR",
  "type": "asset-read",
  "enabled": true,
  "config": {
    "attributePath": "/SiteB/LineA/Pump1/efficiency",
    "outputField": "payload.efficiency"
  }
}
```

---

## 📦 Cara Build & Deploy
1. Masuk ke root directory project `nexa-framework`.
2. Jalankan perintah kompilasi shadow jar:
   ```powershell
   .\gradlew.bat :nexa-asset-manager:shadowJar
   ```
3. Salin file JAR hasil build yang terletak di:
   `nexa-asset-manager/build/libs/nexa-asset-manager.jar`
4. Pindahkan ke direktori `plugins` dari project runtime test Anda (misal `nexa-test/plugins/`).
