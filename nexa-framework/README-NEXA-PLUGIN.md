# ⚙️ Nexa Framework: Panduan Pengembangan & Instalasi Plugin

Selamat datang di Panduan Resmi Pengembangan Plugin Nexa Framework. Dokumen ini ditujukan bagi para engineer dan kontributor luar yang ingin memperluas konektivitas serta fungsionalitas runtime Nexa tanpa menyentuh atau melakukan kompilasi ulang pada *core engine* master[cite: 5].

---

## 1. Core Capabilities & Plugin Architecture

Sistem plugin Nexa dibangun di atas infrastruktur **Java Service Provider Interface (SPI)** dan berjalan di atas pool **Virtual Threads**[cite: 5]. Hal ini memberikan jaminan performa tinggi (I/O non-blocking) sekaligus menjaga isolasi eksekusi[cite: 5].

### Kategori Komponen Plugin
Setiap plugin wajib mengimplementasikan interface spesifik yang disediakan oleh `nexa-api.jar`[cite: 5, 6]:
1. **`NexaResourcePlugin` (Infrastruktur):** Digunakan untuk mengelola koneksi berat seperti *Connection Pool Database* (HikariCP) atau *Shared MQTT Client Management*[cite: 5].
2. **`NexaSourcePlugin` (INPUT Node):** Bertindak sebagai hulu data (*Ingress/Source*) yang menangkap data eksternal lalu melemparkannya ke downstream pipeline Nexa via `emitter`[cite: 5, 6].
3. **`NexaFunctionPlugin` (EXECUTOR Node):** Pemrosesan tengah dinamis bertipe native biner Java untuk manipulasi data berkecepatan tinggi[cite: 5, 6].
4. **`NexaSinkPlugin` (OUTPUT Node):** Bertindak sebagai hilir data (*Egress/Sink*) yang bertugas melempar payload akhir Nexa ke sistem luar[cite: 5, 6].

### Daur Hidup Plugin (Lifecycle)
Core Nexa mengontrol penuh status plugin melalui 3 fase mutlak[cite: 5]:
*   **`onInit`:** Fase inisialisasi di mana konfigurasi JSON dibaca dan di-map ke memori internal plugin[cite: 5]. Di fase ini, node juga dapat meminjam koneksi global dari `NexaPluginContext`[cite: 5].
*   **`onStart`:** Fase aktivasi fungsional jaringan (seperti membuka soket TCP, melakukan *connect* ke broker, atau memulai *subscription* topik)[cite: 5].
*   **`onStop`:** Fase pembersihan (*cleanup*). Dipanggil secara otomatis saat workspace di-*undeploy* untuk membebaskan memori dan menutup koneksi yang menggantung[cite: 5].

---

## 🛠️ Langkah Demi Langkah: Membuat Project Plugin Nexa
### Langkah 1: Buat Direktori Project Baru
Buat folder terpisah di luar repositori utama Nexa kamu (misalnya bernama nexa-mqtt-plugin). susun folder mengikuti standar konvensi berikut:

```Plaintext
nexa-mqtt-plugin/
├── build.gradle.kts           # File konfigurasi build Gradle Plugin
├── src/main/java/nexa/plugin/mqtt/
│   ├── manager/
│   │   └── MqttBrokerManager.java     # Logika reusable connection pool
│   └── node/
│       ├── MqttSharedInputPlugin.java # Node INPUT (Source)
│       └── MqttSharedSinkPlugin.java  # Node OUTPUT (Sink)
└── src/main/resources/META-INF/services/
    └── nexa.framework.runtime.api.plugin.NexaPlugin   # Manifest SPI (Pendaftaran)
```

### Langkah 2: Konfigurasi build.gradle.kts Plugin
Ini adalah bagian paling krusial. Kamu harus memasukkan nexa-api.jar menggunakan compileOnly.

Kenapa compileOnly? Agar saat plugin di-compile menjadi JAR, kode dari nexa-api tidak ikut dibundel masuk ke dalam JAR plugin. Jika ikut dibundel, JVM akan mendeteksi ada 2 kelas RuntimeMessage yang berbeda (satu di core, satu di plugin) dan akan melempar error fatal ClassCastException.

```kotlin
plugins {
    java
    // Menggunakan Shadow Plugin untuk membundel dependensi pihak ketiga milik plugin (misal: Paho MQTT)
    id("com.gradleup.shadow") version "9.2.0" 
}

repositories {
    mavenCentral()
}

dependencies {
    // 1. Ambil nexa-api.jar sebagai kontrak COMPILE ONLY (Wajib!)
    // Sesuaikan path-nya ke lokasi nexa-api.jar hasil build kamu sebelumnya
    compileOnly(files("../nexa-framework/nexa-api/build/libs/nexa-api.jar"))

    // 2. Dependensi pihak ketiga yang murni dibutuhkan oleh plugin wajib menggunakan 'implementation'
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25) // Sesuaikan dengan versi Java Nexa (Java 25)
    }
}

// Konfigurasi output nama file JAR plugin
tasks.shadowJar {
    archiveBaseName.set("nexa-mqtt-plugin")
    archiveClassifier.set("") 
    archiveVersion.set("")
}
```
> ⚠️ **PENTING (ANTI THREAD-PINNING):** Karena runtime Nexa berjalan di atas Virtual Threads, plugin **DILARANG** menggunakan kata kunci `synchronized`. Gunakan `ReentrantLock` dan `ConcurrentHashMap` agar eksekusi tidak mengunci Carrier Thread OS.
> 
> 

---

## 3. Implementasi Kode Sumber (Java Native)

### 3.1. `manager/MqttBrokerManager.java` (Shared Connection Manager)

```java
package nexa.plugin.mqtt.manager;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class MqttBrokerManager {
    private static final ConcurrentHashMap<String, MqttClient> clientPool = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    public static MqttClient getOrCreateClient(String brokerUrl, int keepAlive) throws Exception {
        MqttClient client = clientPool.get(brokerUrl);
        
        if (client == null || !client.isConnected()) {
            lock.lock();
            try {
                client = clientPool.get(brokerUrl);
                if (client == null || !client.isConnected()) {
                    String clientId = "Nexa-Shared-" + MqttClient.generateClientId();
                    client = new MqttClient(brokerUrl, clientId);
                    
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setKeepAliveInterval(keepAlive);
                    options.setCleanSession(true);
                    options.setAutomaticReconnect(true);
                    
                    client.connect(options);
                    clientPool.put(brokerUrl, client);
                    System.out.println("[MQTT Pool] TCP Connection established to: " + brokerUrl);
                }
            } finally {
                lock.unlock();
            }
        }
        return client;
    }

    public static void removeClient(String brokerUrl) {
        lock.lock();
        try {
            MqttClient client = clientPool.remove(brokerUrl);
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
            }
        } catch (Exception ignored) {
        } finally {
            lock.unlock();
        }
    }
}

```

### 3.2. `node/MqttSharedInputPlugin.java` (Node INPUT / Inbound)

```java
package nexa.plugin.mqtt.node;

import nexa.framework.runtime.api.plugin.NexaSourcePlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.plugin.mqtt.manager.MqttBrokerManager;
import org.eclipse.paho.client.mqttv3.MqttClient;
import java.util.Map;
import java.util.function.Consumer;

public final class MqttSharedInputPlugin implements NexaSourcePlugin {
    private Consumer<RuntimeMessage> emitter;
    private MqttClient mqttClient;
    private String brokerUrl;
    private String topic;
    private int keepAlive;

    @Override
    public String getPluginType() {
        return "mqtt-shared-input"; 
    }

    @Override
    public void setEmitter(Consumer<RuntimeMessage> emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
        this.brokerUrl = (String) config.getOrDefault("brokerUrl", "tcp://localhost:1883");
        this.topic = (String) config.getOrDefault("topic", "sensor/data");
        this.keepAlive = ((Number) config.getOrDefault("keepAlive", 60)).intValue();
    }

    @Override
    public void onStart() throws Exception {
        this.mqttClient = MqttBrokerManager.getOrCreateClient(this.brokerUrl, this.keepAlive);
        this.mqttClient.subscribe(this.topic, (receivedTopic, mqttMessage) -> {
            RuntimeMessage nexaMsg = new RuntimeMessage();[cite: 6]
            nexaMsg.writeValue("payload.rawData", new String(mqttMessage.getPayload()));
            nexaMsg.writeValue("payload.topic", receivedTopic);
            
            if (this.emitter != null) {
                this.emitter.accept(nexaMsg); 
            }
        });
    }

    @Override
    public void onStop() {
        try {
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                this.mqttClient.unsubscribe(this.topic);
            }
        } catch (Exception ignored) {}
    }
}

```

### 3.3. `node/MqttSharedSinkPlugin.java` (Node OUTPUT / Outbound)

```java
package nexa.plugin.mqtt.node;

import nexa.framework.runtime.api.plugin.NexaSinkPlugin;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.plugin.mqtt.manager.MqttBrokerManager;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.util.Map;

public final class MqttSharedSinkPlugin implements NexaSinkPlugin {
    private MqttClient mqttClient;
    private String brokerUrl;
    private String topic;
    private int keepAlive;

    @Override
    public String getPluginType() {
        return "mqtt-shared-sink";
    }

    @Override
    public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
        this.brokerUrl = (String) config.getOrDefault("brokerUrl", "tcp://localhost:1883");
        this.topic = (String) config.getOrDefault("topic", "sensor/processed");
        this.keepAlive = ((Number) config.getOrDefault("keepAlive", 60)).intValue();
    }

    @Override
    public void onStart() throws Exception {
        this.mqttClient = MqttBrokerManager.getOrCreateClient(this.brokerUrl, this.keepAlive);
    }

    @Override
    public void consume(RuntimeMessage msg) {
        try {
            Object rawPayload = msg.readRawValue("payload");
            if (rawPayload == null) return;

            MqttMessage mqttMessage = new MqttMessage(rawPayload.toString().getBytes());
            mqttMessage.setQos(1);
            this.mqttClient.publish(this.topic, mqttMessage);
        } catch (Exception e) {
            System.err.println("[MQTT Sink Error] Gagal mempublikasikan data: " + e.getMessage());
        }
    }

    @Override
    public void onStop() {}
}

```

---

## 4. Registrasi Manifes SPI

Buka folder `src/main/resources/META-INF/services/` lalu buat sebuah berkas teks tanpa ekstensi bernama:
`nexa.framework.runtime.api.plugin.NexaPlugin`

Isi dokumen tersebut dengan mendaftarkan kelas *fully-qualified name* dari komponen di atas:

```text
nexa.plugin.mqtt.node.MqttSharedInputPlugin
nexa.plugin.mqtt.node.MqttSharedSinkPlugin

```

Kompilasi project ini menjadi fat-JAR menggunakan perintah:

* **Maven:** `mvn clean package`
* **Gradle:** `./gradlew jar` atau `./gradlew shadowJar`

---

## 🚀 5. Prosedur Instalasi di Direktori Production Nexa

Untuk memasang dan menjalankan plugin ini di lingkungan produksi tanpa mengubah binary utama Nexa, ikuti langkah-langkah berikut:

### Langkah 5.1: Tata Letak Folder Server Production

Pastikan struktur folder di server produksi Anda tertata sebagai berikut:

```text
/opt/nexa-runtime/
├── nexa-core.jar              # Binary Utama Runtime Engine Nexa[cite: 5]
├── nexa-api.jar               # Pustaka Kontrak API Nexa[cite: 5]
└── plugins/                   # Folder Khusus Plugin Eksternal[cite: 5]
    └── nexa-mqtt-plugin.jar   # Hasil kompilasi JAR plugin Anda[cite: 5]

```

### Langkah 5.2: Perintah Eksekusi Classpath (Run Command)

Gunakan argumen Classpath Java (`-cp`) untuk memuat seluruh library di folder `/plugins` secara dinamis ke dalam classloader Nexa saat startup:

* **Linux / Ubuntu Server:**
```bash
java -cp "nexa-core.jar:plugins/*" nexa.framework.NexaStandaloneRunner

```


* **Windows Server (PowerShell):**
```powershell
java -cp "nexa-core.jar;plugins/*" nexa.framework.NexaStandaloneRunner

```



---

## 🗂️ 6. Contoh Implementasi JSON Workspace

Setelah runtime aktif, Web Editor dapat menyusun konfigurasi JSON berikut untuk menguji interaksi *Shared Connection*:

```json
{
  "id": "workspace-mqtt-demo",
  "enabled": true,
  "resources": [
    {
      "id": "mqtt-pool-pabrik",
      "type": "mqtt-broker-pool",
      "config": {
        "brokerUrl": "tcp://broker:1883",
        "keepAlive": 60
      }
    }
  ],
  "flows": [
    {
      "id": "mqtt-flow",
      "name": "MQTT Production Pipeline",
      "enabled": true,
      "nodes": [
        {
          "id": "mqtt-in",
          "category": "INPUT",
          "type": "mqtt-shared-input",
          "config": {
            "brokerUrl": "tcp://broker:1883",
            "topic": "sensor/data"
          }
        },
        {
          "id": "process-data-tengah",
          "category": "EXECUTOR",
          "type": "script",
          "language": "nexa",
          "config": {
            "script": "val raw = msg.readRawValue(\"payload.rawData\").toString(); msg.writeValue(\"payload.processedData\", raw.toUpperCase()); send(msg);"
          }
        },
        {
          "id": "mqtt-out",
          "category": "OUTPUT",
          "type": "mqtt-shared-sink",
          "config": {
            "brokerUrl": "tcp://broker:1883",
            "topic": "sensor/processed"
          }
        }
      ],
      "connections": [
        {
          "sourceNodeId": "mqtt-in",
          "sourcePort": "default",
          "targetNodeId": "process-data-tengah"
        },
        {
          "sourceNodeId": "process-data-tengah",
          "sourcePort": "default",
          "targetNodeId": "mqtt-out"
        }
      ]
    }
  ]
}

```

```

```