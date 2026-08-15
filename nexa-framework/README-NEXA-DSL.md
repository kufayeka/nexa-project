# Nexa DSL V1: Referensi Bahasa & Studi Kasus Industri

Dokumen ini menjelaskan spesifikasi lengkap cara memprogram executor node menggunakan **Nexa DSL V1** (`language = "nexa"`). Nexa DSL adalah bahasa pemrograman scripting tersemat (*embedded*) ringan, cepat, dan aman, yang dirancang khusus untuk memproses transformasi aliran data (*data flow*) secara efisien di dalam Nexa Framework.

---

## 📖 Pendahuluan & Filosofi Runtime

Nexa DSL dirancang dengan tujuan utama:
* **Kecepatan Kompilasi & Runtime**: Dikompilasi langsung ke bentuk AST (Abstract Syntax Tree) berkinerja tinggi di JVM.
* **Isolasi Mutlak**: Skrip dieksekusi secara asinkronus dan aman di dalam thread virtual tanpa efek samping (*side effects*) ke flow lain.
* **Null-safety Tingkat Bahasa**: Menyediakan operator penanganan data kosong (`null`) untuk mengeliminasi resiko kegagalan runtime.

> [!NOTE]
> Nexa DSL V1 menggunakan model tipe dinamis (*dynamic runtime typing*). Tidak ada kata kunci `undefined`; properti objek atau indeks array yang tidak ditemukan akan secara otomatis mengembalikan nilai `null`.

---

## 🛠️ 1. Variabel & Model Nilai

Nexa DSL mendukung tipe data primitif dan kompleks berikut: `Number`, `Boolean`, `String`, `Date`, `DateTime`, `Array`, `Object`, `Function`, dan `null`.

### 1.1 Variabel Read-Only (`val`)
Variabel yang dideklarasikan dengan `val` bersifat immutable (tidak dapat di-assign ulang setelah diinisialisasi).
```nexa
val machineId = "Taiyo-01"
// machineId = "Taiyo-02" // ERROR!
```

### 1.2 Variabel Mutable (`var`)
Variabel yang dideklarasikan dengan `var` dapat diubah nilainya kapan saja.
```nexa
var pieceCount = 0
pieceCount += 15
```

### 1.3 Deklarasi Tanpa Initializer
Variabel `var` yang dideklarasikan tanpa nilai awal secara otomatis bernilai `null`.
```nexa
var operatorName
// operatorName bernilai nu---

## 💬 2. Komentar (Comments)

Nexa DSL mendukung penulisan komentar seperti Java, Kotlin, atau JavaScript untuk membantu dokumentasi kode flow Anda:

### 2.1 Single-Line Comment (`//`)
Mengabaikan semua karakter setelah tanda `//` hingga akhir baris.
```nexa
// Ini adalah komentar satu baris
val speed = 100 // Kecepatan default mesin
```

### 2.2 Multi-Line / Block Comment (`/* ... */`)
Mengabaikan semua teks di antara pembuka `/*` dan penutup `*/` (bisa beberapa baris).
```nexa
/*
 * Blok komentar ini digunakan untuk menjelaskan 
 * logika penyaringan sensor di bawah.
 */
val temp = msg.payload?.temperature
```

---

## 🛡️ 3. Operator Null-Safety

Penanganan data `null` sangat krusial dalam otomasi industri untuk mencegah seluruh sistem mogok akibat sensor yang mengirim data kosong.

### 3.1 Safe Navigation (`?.`)
Mengembalikan `null` secara aman jika objek di sebelah kiri bernilai `null` tanpa melemparkan error.
```nexa
val speed = msg.payload?.sensorData?.speed
```

### 3.2 Nullish Coalescing (`??`)
Mengembalikan nilai cadangan (sisi kanan) jika ekspresi sisi kiri mengevaluasi ke `null`.
```nexa
val activeSpeed = msg.payload?.speed ?? 0
```

---

## 🔄 4. Struktur Kontrol & Perulangan

### 4.1 Percabangan `if / else if / else`
Mengevaluasi ekspresi berdasarkan kondisi boolean.
```nexa
val temp = (msg.payload?.temperature ?? 0).toNumber()

if (temp > 100) {
    msg.payload.status = "OVERHEAT"
} else if (temp > 80) {
    msg.payload.status = "WARNING"
} else {
    msg.payload.status = "NORMAL"
}
```

### 4.2 Switch Statement (`switch`)
Mencocokkan nilai ekspresi ke dalam case. Berbeda dengan Java/C, switch di Nexa DSL **tidak memerlukan keyword `break`** dan **tidak memiliki perilaku fallthrough** (hanya mengeksekusi case pertama yang cocok).
```nexa
val mode = msg.payload?.mode ?? "manual"
var state = 0

switch (mode) {
    case "setup":
        state = 1
    case "auto":
        state = 2
    default:
        state = 0
}
```

### 4.3 For Perulangan Klasik (`for`)
Mendukung iterasi perulangan bertipe angka:
```nexa
var total = 0
for (var i = 0; i < 5; i += 1) {
    total += i
}
```

---

## ⚡ 5. Fungsi & Lambda (First-Class Citizens)

Fungsi di Nexa DSL adalah objek kelas utama (*first-class values*) yang dapat disimpan di variabel, dikirim sebagai argumen, atau dikembalikan dari fungsi lain.

### 5.1 Named Function
* **Bentuk Expression Body (Short-hand)**:
  ```nexa
  fun square(x) => x * x
  ```
* **Bentuk Block Body**:
  ```nexa
  fun calculateYield(good, total) {
      if (total == 0) {
          return 0
      }
      return (good / total) * 100
  }
  ```

### 5.2 Lambda & Closure
Fungsi anonim (lambda) dapat menangkap variabel dari cakupan (*scope*) luar:
```nexa
val multiplier = 5
val process = fun (val) => val * multiplier
```

---

## 📦 6. Pustaka Standar Bawaan (Built-in Standard Library)

### 6.1 Math
Menyediakan operasi matematika standar:
* `Math.abs(x)`, `Math.round(x)`, `Math.floor(x)`, `Math.ceil(x)`
* `Math.max(a, b, ...)`, `Math.min(a, b, ...)`
* `Math.random()` (mengembalikan nilai antara `0` dan `1`)
* `Math.sqrt(x)`, `Math.pow(a, b)`, `Math.log(x)`
* `Math.sin(x)`, `Math.cos(x)`

### 6.2 DateTime
Menyediakan fungsionalitas waktu:
* `DateTime.now()`: Mengembalikan objek waktu saat ini.
* `.toISOString()`: Mengonversi `Date` atau `DateTime` menjadi format string ISO-8601 UTC.
* `.toDate()`: Mengonversi string tanggal menjadi objek `Date`.

### 6.3 Json
* `Json.parse(text)`: Mengubah teks JSON menjadi Array atau Object Nexa.
* `Json.stringify(value)`: Mengubah objek/array menjadi teks string JSON.

### 6.4 Regex
* `Regex.match(text, pattern)`: Mengembalikan array hasil pencocokan.
* `Regex.replace(text, pattern, replacement)`: Mengganti substring menggunakan Java Regex.

---

## 📇 7. Manipulasi String & Array

Setiap instance String dan Array menyediakan metode bawaan yang kaya:

### 7.1 String Methods
| Metode | Deskripsi | Contoh |
| :--- | :--- | :--- |
| `length` | Properti panjang karakter string. | `"taiyo".length` (hasil: 5) |
| `trim()` | Menghapus spasi di awal/akhir string. | `"  ab ".trim()` (hasil: `"ab"`) |
| `toUpperCase()` | Mengubah ke huruf kapital semua. | `"ab".toUpperCase()` (hasil: `"AB"`) |
| `toLowerCase()` | Mengubah ke huruf kecil semua. | `"AB".toLowerCase()` (hasil: `"ab"`) |
| `startsWith(val)` | Memeriksa kecocokan awal string. | `"WO-1".startsWith("WO-")` (hasil: `true`) |
| `endsWith(val)` | Memeriksa kecocokan akhir string. | `"a.json".endsWith(".json")` (hasil: `true`) |
| `includes(val)` | Memeriksa keberadaan substring. | `"Production".includes("duct")` (hasil: `true`) |
| `split(sep)` | Memecah string menjadi Array String. | `"A/B".split("/")` (hasil: `["A", "B"]`) |
| `substring(s, e)` | Mengambil potongan indeks `s` hingga `e`. | `"Taiyo".substring(0, 3)` (hasil: `"Tai"`) |

* **String Interpolation**: String dinamis dapat ditulis dengan backtick (`` ` ``) menggunakan format `${expression}`:
  ```nexa
  val count = 10
  val msgText = `Total item diproses: ${count}`
  ```

### 7.2 Array Methods
| Metode | Deskripsi | Contoh |
| :--- | :--- | :--- |
| `length` | Properti jumlah elemen array. | `[1, 2].length` (hasil: 2) |
| `push(item)` | Menambah elemen ke akhir array. | `var a = [1]; a.push(2)` |
| `pop()` | Menghapus dan mengembalikan elemen terakhir. | `val last = arr.pop()` |
| `join(sep)` | Menggabungkan elemen menjadi satu string. | `[1, 2].join("-")` (hasil: `"1-2"`) |
| `map(fun)` | Mentransformasikan setiap elemen array. | `[1, 2].map(fun(x) => x*2)` |
| `filter(fun)` | Menyaring elemen berdasarkan fungsi filter. | `[1, 2].filter(fun(x) => x > 1)` |
| `reduce(fun, init)` | Mengurangi array menjadi nilai tunggal. | `[1, 2].reduce(fun(acc, x) => acc+x, 0)` |
| `forEach(fun)` | Menjalankan fungsi untuk setiap elemen. | `arr.forEach(fun(item) => trace(item))` |

---

## 📡 8. Interaksi dengan Runtime & Port

* **Pesan Utama (`msg`)**: Objek variabel global mutable yang bertindak sebagai input data ke node, dan output data ketika diteruskan. Payload data biasanya diletakkan pada properti `msg.payload`.
* **Mengirim Pesan (`send`)**: Pemicu untuk mengirimkan pesan ke node downstream.
  ```nexa
  send(msg) // Mengirim ke port default ("default")
  send("high-priority", msg) // Mengirim ke port spesifik
  send(["port-A", "port-B"], msg) // Mengirim salinan pesan ke banyak port sekaligus
  ```
* **Early Exit (`return`)**: Menghentikan eksekusi script saat itu juga.
  ```nexa
  if (msg.payload == null) {
      return // Keluar dari eksekusi node
  }
  ```

---

## 🔌 9. Integrasi Java (Host Extensions)

Nexa DSL dapat diekspansi secara dinamis menggunakan plugin kelas Java native melalui Service Loader:

### Langkah 1: Implementasikan `NexaRuntimeExtension` di Java
```java
package my.custom.plugin;

import nexa.framework.runtime.domain.scripting.api.ScriptRuntimeApi;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntimeExtension;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaHostObject;
import nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntime;

import java.util.List;
import java.util.Map;

public final class CustomMesPlugin implements NexaRuntimeExtension {
    @Override
    public Map<String, Object> globals() {
        return Map.of("Mes", new NexaHostObject() {
            // Definisikan method kustom yang bisa dipanggil dari script
            public Object lookupWorkOrder(List<Object> args, ScriptRuntimeApi api) {
                String woId = String.valueOf(args.getFirst());
                // Lakukan query database atau API di sini secara native
                return Map.of("id", woId, "status", "APPROVED", "targetQty", 500);
            }
        });
    }
}
```

### Langkah 2: Register di META-INF Resources
Buat file `META-INF/services/nexa.framework.runtime.domain.scripting.internal.nexa.NexaRuntimeExtension` berisi:
```text
my.custom.plugin.CustomMesPlugin
```

### Langkah 3: Panggil di Nexa DSL
```nexa
val woDetails = Mes.lookupWorkOrder("WO-1004")
msg.payload.wo = woDetails
send(msg)
```

---

## 🏭 10. Studi Kasus Skenario Industri (Real-world Use Cases)

### Kasus 1: Penyaringan Sensor Cacat & Alarm Fan-out
**Skenario**: Membaca sensor temperatur. Jika temperatur di atas batas kritis, picu port `"alarm"` dan hentikan eksekusi flow biasa. Jika normal, kirim ke port `"default"`.
```nexa
val temp = (msg.payload?.temperature ?? 0).toNumber()

if (temp >= 110) {
    msg.payload = {
        alert: true,
        level: "CRITICAL",
        value: temp,
        timestamp: DateTime.now().toISOString(),
        message: `System overheat detected! Temperature: ${temp} C`
    }
    send("alarm", msg)
    return // Batalkan pengiriman ke jalur reguler
}

// Jalur normal
msg.payload.status = "STABLE"
send(msg)
```

### Kasus 2: Kalkulasi Agregasi Yield & Efisiensi Mesin
**Skenario**: Menerima array data produksi batch, menyaring batch yang kosong, menghitung total yield produksi bagus, rata-rata, dan rasio efisiensi.
```nexa
val batches = msg.payload?.batches ?? []

// 1. Saring batch yang memiliki produksi
val activeBatches = batches.filter(fun (b) => b.totalCount > 0)

// 2. Hitung total yield bagus menggunakan reduce
val totalGood = activeBatches.reduce(fun (acc, b) => acc + (b.goodCount ?? 0), 0)
val totalProduced = activeBatches.reduce(fun (acc, b) => acc + (b.totalCount ?? 0), 0)

// 3. Kalkulasi rasio OEE
var yieldRatio = 0
if (totalProduced > 0) {
    yieldRatio = (totalGood / totalProduced) * 100
}

msg.payload = {
    batchProcessed: activeBatches.length,
    yieldGood: totalGood,
    yieldTotal: totalProduced,
    efficiencyPercent: Math.round(yieldRatio),
    timestamp: DateTime.now().toISOString()
}

send(msg)
```

### Kasus 3: Parsing Data Modbus String Menjadi JSON
**Skenario**: Menerima payload mentah dari sensor Modbus berupa string datar berpemisah koma: `"(Taiyo01,RUN,150,85.5)"`, parse isinya dan keluarkan bentuk data bertipe kuat.
```nexa
val rawPayload = msg.payload?.rawData ?? "(Unknown,OFF,0,0.0)"

// Bersihkan karakter pembuka kurung
val cleaned = rawPayload.replace("(", "").replace(")", "")
val parts = cleaned.split(",")

msg.payload = {
    machineId: parts[0].trim(),
    status: parts[1].trim().toUpperCase(),
    speed: parts[2].trim().toNumber(),
    efficiency: parts[3].trim().toNumber(),
    processedAt: DateTime.now().toISOString()
}

send(msg)
```

### Kasus 4: Parsing Kode WorkOrder Menggunakan Regex
**Skenario**: Membaca string deskripsi alur kerja dan mengekstrak kode WorkOrder berpola `WO-[angka]` menggunakan regex.
```nexa
val desc = msg.payload?.description ?? "No description available"
val matches = desc.match("WO-\\d+")

if (matches.length > 0) {
    msg.payload.workOrderId = matches[0]
    msg.payload.isValid = true
} else {
    msg.payload.workOrderId = null
    msg.payload.isValid = false
}

send(msg)
```
