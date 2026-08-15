# Nexa Framework 🚀

![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Gradle](https://img.shields.io/badge/Gradle-9.2.0-02303A.svg)
![Architecture](https://img.shields.io/badge/Architecture-DDD-blue.svg)
![DI](https://img.shields.io/badge/DI-Pure%20DI-red.svg)
![Status](https://img.shields.io/badge/Status-Active%20Development-yellow.svg)

**Nexa** adalah platform **industrial automation low-code** berbasis Java yang menjalankan program automation sebagai **workspace → flow → node → connection graph**.

Konsep utamanya mirip visual flow runtime seperti Node-RED, tetapi Nexa dibangun sebagai runtime Java modular dengan fokus pada concurrency, isolasi state message, scripting, plugin, runtime control, dan deployment standalone.

> **Project structure:** repository ini berisi Nexa Framework, Nexa Designer, dan environment pengujian/deployment. Framework Java berada di `nexa-framework/`.

---

## ✨ Apa itu Nexa?

Nexa memodelkan aplikasi automation sebagai sebuah **Workspace**. Sebuah workspace dapat berisi resource dan satu atau lebih flow.

```text
Workspace
├── Resources
│   ├── Database pool
│   ├── MQTT client
│   └── Other heavy resources
│
└── Flows
    ├── Flow A
    │   ├── Input
    │   ├── Executor
    │   ├── Executor
    │   └── Output
    │
    └── Flow B
        └── ...
```

Flow sendiri merupakan graph dari node dan connection:

```text
INPUT ──→ EXECUTOR ──→ EXECUTOR ──→ OUTPUT
              │
              └────────→ EXECUTOR
```

Model runtime ini direpresentasikan oleh `WorkspaceDefinition`, `FlowDefinition`, `NodeDefinition`, `ConnectionDefinition`, dan `ResourceDefinition`.

---

# 🌟 Fitur Utama

## ⚡ Virtual Threads & Concurrent Execution

Nexa menggunakan Java Virtual Threads untuk execution workload yang concurrent dan asynchronous. Tujuannya adalah menangani banyak pekerjaan flow tanpa membuat satu platform thread untuk setiap jalur message.

Input node juga memiliki `InputExecutionPolicyDefinition` dengan `maxConcurrentExecutions` untuk membatasi concurrency per input. Jika tidak diberikan atau nilainya kurang dari 1, runtime menggunakan `Integer.MAX_VALUE` sebagai default.

---

## 🧩 Multi-Module Architecture

Framework dipisahkan menjadi beberapa module Gradle:

```text
nexa-framework/
├── nexa-api/
├── nexa-core/
├── nexa-script-engine/
├── nexa-cli/
├── nexa-mqtt-plugin/
└── nexa-control-plugin/
```

Module didaftarkan pada `settings.gradle.kts` sebagai multi-project Gradle build.

### `nexa-api`

Public contract untuk runtime dan plugin. Plugin eksternal bergantung pada API ini, bukan pada implementation detail `nexa-core`.

### `nexa-core`

Core execution engine dan domain runtime.

Domain utamanya meliputi:

- Workspace
- Deployment
- Execution
- Scheduler
- Scripting
- Statistics
- Runtime Control

### `nexa-script-engine`

Implementasi compiler/runtime untuk Nexa DSL. Scripting engine dipisahkan dari core dan diintegrasikan melalui SPI/ServiceLoader.

### `nexa-cli`

Standalone runner untuk menjalankan Nexa Runtime dari command line.

### `nexa-mqtt-plugin`

Plugin MQTT untuk node/integrasi MQTT.

### `nexa-control-plugin`

Plugin control plane yang mengekspos REST API dan embedded MQTT server untuk mengontrol dan memonitor runtime.

---

# 🧠 Runtime Model

## Workspace

Workspace adalah container utama runtime. Workspace mempunyai:

- `id`
- `enabled`
- `resources`
- `flows`

Resource dipisahkan dari flow agar resource berat seperti database pool atau MQTT client tidak menjadi bagian langsung dari topology message.

## Flow

Flow mempunyai:

- `id`
- `name`
- `enabled`
- `nodes`
- `connections`

Jika `name` kosong, runtime menggunakan `id` sebagai nama flow.

## Node

Node mempunyai:

- `id`
- `category`
- `type`
- `language`
- `enabled`
- `inputPolicy`
- `config`

Kategori node yang tersedia pada core model:

```text
INPUT
EXECUTOR
OUTPUT
```

`type` menentukan implementasi node yang digunakan runtime/plugin, sedangkan `config` berisi konfigurasi spesifik node.

## Connection

Connection menghubungkan source node ke target node dan membentuk graph execution.

## RuntimeMessage

Message adalah data yang bergerak di antara node. Pada Nexa DSL, message utama tersedia melalui variabel `msg`, dengan payload umum berada pada `msg.payload`.

---

# 🔀 Message Execution & State Isolation

Ketika message mempunyai beberapa downstream branch, Nexa dirancang agar state message pada branch tidak saling merusak. Transformasi pada satu branch tidak seharusnya mengubah object yang dipakai branch lain.

Konsepnya:

```text
                 ┌──→ Node B
Node A ── msg ───┤
                 └──→ Node C

B melakukan transformasi
        ↓
state C tetap terisolasi
```

Hal ini penting untuk automation flow yang mempunyai fan-out atau beberapa jalur pemrosesan paralel.

---

# 🧮 Runtime Statistics

Nexa mempunyai domain statistics untuk mencatat performa execution secara concurrent.

Statistics digunakan untuk mendapatkan informasi runtime seperti jumlah execution yang selesai, gagal, dan sedang berjalan pada runtime/flow yang mendukung statistik tersebut.

Accumulator menggunakan struktur concurrent seperti `LongAdder` untuk mengurangi contention antar worker.

---

# 🕒 Scheduler

Core mempunyai domain scheduler untuk menangani pemicu input dan scheduling execution. Scheduler merupakan bagian dari runtime domain dan tidak dicampur dengan protocol/plugin layer.

---

# 🧪 Nexa DSL

Nexa memiliki bahasa scripting embedded bernama **Nexa DSL V1**.

Nexa DSL ditujukan untuk transformasi data di dalam executor node.

### Tipe nilai

Nexa DSL mendukung:

- Number
- Boolean
- String
- Date
- DateTime
- Array
- Object
- Function
- `null`

### Variable

Immutable:

```nexa
val machineId = "Taiyo-01"
```

Mutable:

```nexa
var pieceCount = 0
pieceCount += 15
```

### Null safety

Safe navigation:

```nexa
val speed = msg.payload?.sensorData?.speed
```

Nullish coalescing:

```nexa
val activeSpeed = msg.payload?.speed ?? 0
```

### Control flow

```nexa
if (temp > 100) {
    msg.payload.status = "OVERHEAT"
} else {
    msg.payload.status = "NORMAL"
}
```

Switch:

```nexa
switch (mode) {
    case "setup":
        state = 1
    case "auto":
        state = 2
    default:
        state = 0
}
```

Loop:

```nexa
for (var i = 0; i < 5; i += 1) {
    total += i
}
```

### Functions & lambdas

```nexa
fun square(x) => x * x

val multiplier = 5
val process = fun (value) => value * multiplier
```

### Built-in library

Nexa DSL menyediakan fungsi built-in untuk:

- `Math`
- `DateTime`
- `Json`
- `Regex`
- String operations
- Array operations

Contoh:

```nexa
val data = Json.parse(msg.payload.raw)
val upper = "machine".toUpperCase()
val total = [1, 2, 3].reduce(fun(acc, x) => acc + x, 0)
```

### Message routing

Kirim ke output default:

```nexa
send(msg)
```

Kirim ke port tertentu:

```nexa
send("alarm", msg)
```

Kirim ke beberapa port:

```nexa
send(["port-A", "port-B"], msg)
```

Early exit:

```nexa
if (msg.payload == null) {
    return
}
```

Dokumentasi DSL lengkap tersedia di [`README-NEXA-DSL.md`](README-NEXA-DSL.md).

---

# 🔌 Java Host Extensions

Nexa DSL dapat diperluas dari Java menggunakan `NexaRuntimeExtension` dan Java `ServiceLoader`.

Dengan mekanisme ini plugin dapat menyediakan object/function native untuk script, misalnya:

```nexa
val wo = Mes.lookupWorkOrder("WO-1004")
msg.payload.workOrder = wo
send(msg)
```

Pattern ini memungkinkan integrasi dengan MES, database, API, device gateway, atau service internal tanpa memasukkan semua integrasi tersebut ke core DSL.

---

# 🧩 Plugin System

Nexa menggunakan dynamic plugin loading.

Plugin eksternal diletakkan di:

```text
plugins/
    *.jar
```

Standalone runner memindai folder tersebut, membuat `URLClassLoader`, kemudian menggunakan `ServiceLoader<NexaPlugin>` untuk menemukan plugin.

Plugin kemudian diregistrasikan berdasarkan `getPluginType()`.

Contoh deployment:

```text
runtime/
├── nexa-cli.jar
├── workspaces/
│   └── workspace-main.json
└── plugins/
    ├── nexa-mqtt-plugin.jar
    └── nexa-control-plugin.jar
```

Dokumentasi membuat plugin tersedia di [`README-NEXA-PLUGIN.md`](README-NEXA-PLUGIN.md).

---

# 🎛️ Nexa Control Plugin

`nexa-control-plugin` adalah **runtime control plane** Nexa.

Plugin ini mengimplementasikan `NexaPlugin` dan `NexaControlService`, menerima `NexaControlContext`, kemudian mengekspos interface control runtime melalui HTTP dan MQTT.

Arsitekturnya:

```text
                  Nexa Designer / Dashboard
                         │
              ┌──────────┴──────────┐
              │                     │
             REST                 MQTT
              │                     │
              ▼                     ▼
       Javalin HTTP          Moquette Broker
              │                     │
              └──────────┬──────────┘
                         ▼
                 NexaControlContext
                         │
        ┌────────────────┼─────────────────┐
        ▼                ▼                 ▼
 WorkspaceControl   NodeControl     ConnectionControl
                         │
                         ▼
                   RuntimeControl
                         │
                         ▼
                      Nexa Core
```

## Control API

### Workspace Control

Workspace dapat:

- load
- unload
- enable
- disable
- list
- inspect metadata
- retrieve raw JSON
- validate workspace
- validate node script

API contract berada di `WorkspaceControl`.

### Node Control

Node dapat:

- enable
- disable
- inspect status/info
- inspect message history
- add breakpoint
- remove breakpoint
- resume
- step
- inspect paused message

Ini membuat runtime dapat dikendalikan secara remote untuk debugging.

### Connection Control

Connection dapat:

- enable
- disable
- inspect connection information melalui API contract
- inject message
- add connection
- remove connection

### Runtime Control

Runtime dapat:

- shutdown
- stop
- restart
- inspect system status
- reload plugins
- trigger garbage collection
- reset workspace metrics
- reset node metrics

---

# 🌐 REST Control API

Control Plugin saat ini menjalankan **Javalin HTTP server pada port `8080`**.

> Base URL default: `http://localhost:8080`

## Workspace

| Method | Endpoint | Parameter | Keterangan |
|---|---|---|---|
| POST | `/api/workspace/load` | body = JSON | Load workspace |
| POST | `/api/workspace/unload` | `workspaceId` | Unload workspace |
| POST | `/api/workspace/enable` | `workspaceId` | Enable workspace |
| POST | `/api/workspace/disable` | `workspaceId` | Disable workspace |
| GET | `/api/workspace/list` | - | List workspace |
| GET | `/api/workspace/{id}` | path `id` | Workspace info |
| GET | `/api/workspace/{id}/data` | path `id` | Raw workspace JSON |
| POST | `/api/workspace/validate` | body = JSON | Validate workspace |
| POST | `/api/workspace/validate-script` | `language`, body = script | Validate script |

Contoh:

```bash
curl http://localhost:8080/api/workspace/list
```

Load workspace:

```bash
curl -X POST \
  http://localhost:8080/api/workspace/load \
  -H "Content-Type: application/json" \
  --data-binary @workspaces/workspace-main.json
```

Enable workspace:

```bash
curl -X POST "http://localhost:8080/api/workspace/enable?workspaceId=main"
```

## Node

| Method | Endpoint | Parameter | Keterangan |
|---|---|---|---|
| POST | `/api/node/enable` | `nodeId` | Enable node |
| POST | `/api/node/disable` | `nodeId` | Disable node |
| GET | `/api/node/{id}` | path `id` | Node info |
| POST | `/api/node/breakpoint/add` | `nodeId` | Add breakpoint |
| POST | `/api/node/breakpoint/remove` | `nodeId` | Remove breakpoint |
| POST | `/api/node/breakpoint/resume` | `nodeId` | Resume node |
| POST | `/api/node/breakpoint/step` | `nodeId` | Step node |
| GET | `/api/node/breakpoint/message/{id}` | path `id` | Get paused message |

Contoh breakpoint:

```bash
curl -X POST "http://localhost:8080/api/node/breakpoint/add?nodeId=script-01"
```

Resume:

```bash
curl -X POST "http://localhost:8080/api/node/breakpoint/resume?nodeId=script-01"
```

Step:

```bash
curl -X POST "http://localhost:8080/api/node/breakpoint/step?nodeId=script-01"
```

## Connection

| Method | Endpoint | Parameter | Keterangan |
|---|---|---|---|
| POST | `/api/connection/enable` | `source` | Enable source connection |
| POST | `/api/connection/disable` | `source` | Disable source connection |
| POST | `/api/connection/inject` | `source`, body JSON | Inject `RuntimeMessage` |
| POST | `/api/connection/add` | `source`, `target` | Add connection |
| POST | `/api/connection/remove` | `source`, `target` | Remove connection |

Contoh dynamic connection:

```bash
curl -X POST "http://localhost:8080/api/connection/add?source=node-a&target=node-b"
```

Remove:

```bash
curl -X POST "http://localhost:8080/api/connection/remove?source=node-a&target=node-b"
```

## Runtime

| Method | Endpoint | Parameter | Keterangan |
|---|---|---|---|
| GET | `/api/runtime/status` | - | System status |
| POST | `/api/runtime/shutdown` | - | Shutdown runtime |
| POST | `/api/runtime/gc` | - | Trigger GC |
| POST | `/api/runtime/reload-plugins` | - | Reload plugins |
| POST | `/api/runtime/metrics/reset/workspace` | `workspaceId` | Reset workspace metrics |
| POST | `/api/runtime/metrics/reset/node` | `nodeId` | Reset node metrics |

Contoh:

```bash
curl http://localhost:8080/api/runtime/status
```

Reload plugin:

```bash
curl -X POST http://localhost:8080/api/runtime/reload-plugins
```

---

# 📡 MQTT Monitoring

Control Plugin menjalankan embedded **Moquette MQTT broker** pada:

```text
host: 0.0.0.0
port: 1883
anonymous: enabled
persistence: disabled
```

Pada implementasi saat ini, Control Plugin berlangganan event internal `nexa/monitor/node/errors` dan menerbitkan `ScriptNodeFailureEvent` ke MQTT topic yang sama.

Contoh subscribe:

```bash
mosquitto_sub -h localhost -p 1883 -t nexa/monitor/node/errors
```

> Topic monitoring lain dapat menjadi extension point untuk event bus/runtime monitoring. Jangan menganggap topic tersebut sudah aktif hanya berdasarkan rancangan; implementasi saat ini secara eksplisit mem-publish `nexa/monitor/node/errors`.

---

# 📡 Event Bus

`NexaControlContext` memberikan akses ke `NexaEventBus`.

Control Plugin menggunakan event bus tersebut untuk menangkap `ScriptNodeFailureEvent`.

```text
Nexa Core
   │
   └── Script failure
          │
          ▼
  ScriptNodeFailureEvent
          │
          ▼
     NexaEventBus
          │
          ▼
 nexa-control-plugin
          │
          ▼
 MQTT: nexa/monitor/node/errors
```

Pattern ini membuat core dapat menghasilkan event tanpa mengetahui protocol monitoring yang digunakan client.

---

# 🖥️ Nexa Designer

Repository juga memiliki `nexa-designer`, sebuah aplikasi Flutter yang menjadi basis visual designer untuk Nexa.

Struktur utamanya berada di:

```text
nexa-designer/
├── lib/
│   ├── main.dart
│   ├── designer_page.dart
│   ├── models.dart
│   └── workspace_painter.dart
├── test/
├── web/
└── windows/
```

Designer ditujukan sebagai client/editor visual untuk workspace dan flow Nexa. Integrasi runtime control dapat menggunakan REST/MQTT Control Plugin.

---

# 🗂️ Struktur Repository

```text
nexa-project/
│
├── nexa-framework/
│   │
│   ├── nexa-api/
│   │   └── Public runtime/plugin contracts
│   │
│   ├── nexa-core/
│   │   └── Runtime engine & domain
│   │
│   ├── nexa-script-engine/
│   │   └── Nexa DSL compiler/runtime
│   │
│   ├── nexa-cli/
│   │   └── Standalone runtime launcher
│   │
│   ├── nexa-mqtt-plugin/
│   │   └── MQTT integration
│   │
│   ├── nexa-control-plugin/
│   │   └── REST + MQTT runtime control
│   │
│   ├── README.md
│   ├── README-NEXA-DSL.md
│   └── README-NEXA-PLUGIN.md
│
├── nexa-designer/
│   └── Flutter visual designer
│
├── nexa-test/
│   ├── plugins/
│   └── workspaces/
│
└── implementation_plan.md
```

---

# 🛠️ Requirements

Framework saat ini menggunakan:

- Java 25
- Gradle 9.2.0
- Gradle multi-project build
- Maven Central dependencies

Control Plugin menggunakan antara lain:

- Javalin `6.1.3`
- Jackson Databind `2.17.0`
- Moquette Broker `0.16`
- SLF4J Simple `2.0.12`

---

# 🔨 Build

Masuk ke folder framework:

```powershell
cd nexa-framework
```

Build semua module:

```powershell
./gradlew.bat shadowJar
```

Linux/macOS:

```bash
./gradlew shadowJar
```

Test:

```powershell
./gradlew.bat test
```

---

# 📦 Build Module Tertentu

Build CLI:

```powershell
./gradlew.bat :nexa-cli:shadowJar
```

Build MQTT plugin:

```powershell
./gradlew.bat :nexa-mqtt-plugin:shadowJar
```

Build Control Plugin:

```powershell
./gradlew.bat :nexa-control-plugin:shadowJar
```

Control Plugin menghasilkan fat JAR dengan nama:

```text
nexa-control-plugin.jar
```

---

# ▶️ Menjalankan Nexa Runtime

Setelah build, gunakan `nexa-cli` sebagai standalone runner.

Struktur runtime yang direkomendasikan:

```text
runtime/
├── nexa-cli.jar
├── workspaces/
│   └── workspace-main.json
└── plugins/
    ├── nexa-mqtt-plugin.jar
    └── nexa-control-plugin.jar
```

Jalankan:

```powershell
java -jar nexa-cli.jar
```

Runner menggunakan:

```text
workspaces/workspace-main.json
```

sebagai workspace default jika tidak diberikan argument.

Untuk workspace lain:

```powershell
java -jar nexa-cli.jar path/to/workspace.json
```

Runner memindai `./plugins/*.jar` sebelum workspace dimuat dan menggunakan Java `ServiceLoader` untuk menemukan `NexaPlugin`.

---

# 🔄 Lifecycle Runtime

Secara umum lifecycle standalone runtime adalah:

```text
Start process
    ↓
Load plugins from ./plugins
    ↓
Load workspace JSON
    ↓
Create Runtime Engine
    ↓
Start Runtime
    ↓
Deploy Workspace
    ↓
Execute flows
    ↓
Wait indefinitely
    ↓
Ctrl+C / shutdown
    ↓
Graceful stop
```

Runner juga menyediakan system property `run.duration`. Nilai `0` berarti runtime berjalan tanpa batas waktu.

Contoh:

```powershell
java -Drun.duration=30 -jar nexa-cli.jar
```

---

# 🧾 Workspace JSON

Workspace minimal mempunyai struktur seperti:

```json
{
  "id": "production",
  "enabled": true,
  "resources": [],
  "flows": [
    {
      "id": "main-flow",
      "name": "Main Flow",
      "enabled": true,
      "nodes": [
        {
          "id": "input-1",
          "category": "INPUT",
          "type": "...",
          "language": "...",
          "enabled": true,
          "inputPolicy": {
            "maxConcurrentExecutions": 10
          },
          "config": {}
        }
      ],
      "connections": []
    }
  ]
}
```

Field `type`, `language`, dan `config` bergantung pada node/plugin yang digunakan.

Workspace loader menormalisasi beberapa nilai default, termasuk `enabled` dan list kosong agar runtime tidak menerima collection `null`.

---

# 🔧 Contoh Flow Konseptual

```text
                    ┌───────────────┐
                    │ Sensor / MQTT │
                    │    INPUT      │
                    └───────┬───────┘
                            │
                            ▼
                    ┌───────────────┐
                    │   Transform   │
                    │   EXECUTOR    │
                    └───────┬───────┘
                            │
                 ┌──────────┴──────────┐
                 ▼                     ▼
          ┌─────────────┐       ┌─────────────┐
          │   Database  │       │    Alarm    │
          │    OUTPUT   │       │    OUTPUT   │
          └─────────────┘       └─────────────┘
```

Executor dapat menggunakan Nexa DSL untuk transformasi, filtering, calculation, routing, dan integrasi host extension.

---

# 🧪 Testing

Jalankan seluruh test:

```powershell
./gradlew.bat test
```

Report Gradle tersedia pada masing-masing module di bawah:

```text
build/reports/tests/test/
```

---

# 📚 Dokumentasi

| Dokumen | Isi |
|---|---|
| [`README-NEXA-DSL.md`](README-NEXA-DSL.md) | Syntax, type system, operators, functions, standard library, message routing, Java extensions, dan contoh industri |
| [`README-NEXA-PLUGIN.md`](README-NEXA-PLUGIN.md) | Cara membuat plugin Nexa |
| [`implementation_plan.md`](../implementation_plan.md) | Rancangan arsitektur dan implementasi control/monitoring |

---

# 🏗️ Design Principles

Nexa berusaha menjaga pemisahan berikut:

```text
API / Contract
      ↓
   nexa-api
      ↓
Implementation
      ↓
   nexa-core
      ↓
Protocol / Integration
      ↓
     Plugins
```

Core tidak perlu mengetahui apakah sebuah capability diakses melalui REST, MQTT, CLI, atau client lain.

Plugin juga tidak seharusnya mengakses implementation detail core secara langsung ketika public contract di `nexa-api` sudah tersedia.

---

# 🚧 Current Status

Nexa masih dalam **active development**.

Sudah tersedia pada codebase:

- Multi-module Gradle framework
- Core runtime domain
- Workspace / Flow / Node / Connection model
- Virtual-thread based execution architecture
- Runtime statistics
- Nexa DSL V1
- Dynamic plugin loading
- MQTT plugin
- Control API contracts
- Workspace control
- Node control
- Connection control
- Runtime control
- Control Plugin
- Embedded REST server pada port `8080`
- Embedded MQTT broker pada port `1883`
- Script failure event streaming melalui `nexa/monitor/node/errors`
- Flutter-based Nexa Designer project

Beberapa capability control/monitoring masih merupakan extension point atau implementation yang sedang berkembang. Dokumentasi ini membedakan fitur yang benar-benar diekspos oleh current implementation dari fitur yang masih berada pada architectural plan.

---

# 📜 License

Proyek ini dilisensikan di bawah **Kufayeka Industrial Automation License**.
