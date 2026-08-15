package nexa.framework;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.api.plugin.NexaPlugin;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.workspace.service.WorkspaceJsonLoader;
import nexa.framework.runtime.domain.scripting.registry.PluginRegistry;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

public final class NexaStandaloneRunner {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("        Nexa Runtime Standalone Runner           ");
        System.out.println("=================================================");

        // 1. Pemuatan Subsistem Plugin Dinamis dari folder ./plugins
        loadDynamicPlugins();

        // 2. BERSIHKAN FALLBACK STRESS TEST: Arahkan default ke
        // workspaces/workspace-main.json
        File baseDir = new File(".").getAbsoluteFile();
        String pathStr = args.length > 0 ? args[0] : "workspaces/workspace-main.json";
        File file = new File(pathStr);
        if (!file.isAbsolute()) {
            file = new File(baseDir, pathStr);
        }

        // Jalankan pemeriksaan eksistensi file JSON Workspace utama
        if (!file.exists()) {
            System.err.println("[Error] Berkas JSON Workspace tidak ditemukan di: " + file.getAbsolutePath());
            System.err.println(
                    "[Solusi] Pastikan folder 'workspaces/' dan file 'workspace-main.json' sudah ditaruh sejajar dengan nexa-core.jar");
            System.exit(1);
            return;
        }

        Path jsonPath = file.toPath();
        System.out.println("[standalone] Membaca konfigurasi produksi dari: " + jsonPath.toAbsolutePath());

        // 3. Parsing objek JSON Workspace Definition
        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        WorkspaceDefinition workspaceDef = loader.fromFile(jsonPath);
        System.out.println("[standalone] Workspace '" + workspaceDef.id() + "' berhasil dimuat ke memori.");

        // 4. Setup Log Interseptor Output ke Console log
        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            System.out.println(String.format("[%s][DEBUG][%s] message: %s",
                    Instant.now().toString(), nodeId, message.values()));
        };

        // 5. Inisialisasi Engine Core
        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(15)),
                outputConsumer);

        // 6. Deploy Rantai Topologi Node & Hidupkan Aliran Data
        System.out.println("[standalone] Mentransformasikan graf biner dan mendesentralisasikan resource...");
        runtime.deploy(workspaceDef);
        System.out.println("[standalone] Menghidupkan pipeline runtime Nexa Engine...");
        runtime.startRuntime();

        // Di level produksi, set run.duration default ke 0 agar engine berjalan abadi
        // (indefinitely)
        int runDuration = Integer.getInteger("run.duration", 0);
        System.out.println("[standalone] Core Engine Aktif. Tekan Ctrl+C untuk menghentikan aplikasi.");
        if (runDuration > 0) {
            System.out.println("[standalone] Aliran data akan otomatis dihentikan setelah " + runDuration + " detik.");
        } else {
            System.out.println(
                    "[standalone] Aliran data dikonfigurasi berjalan tanpa batas waktu (Indefinitely Continuous).");
        }

        // Shutdown hook bersih untuk mengamankan port jaringan dan state memori
        // database
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[standalone] Sinyal interupsi diterima. Mematikan seluruh subsistem pipeline...");
            runtime.stopRuntime();
            System.out.println("[standalone] Runtime dimatikan secara bersih (graceful). Server dihentikan.");
        }));

        // long startTime = System.currentTimeMillis();
        // long endTime = startTime + (runDuration * 1000L);

        // try {
        // // Loop monitoring performa TPS dan status antrean aliran data
        // while (runDuration <= 0 || System.currentTimeMillis() < endTime) {
        // TimeUnit.SECONDS.sleep(5); // Cukup cek metrik statistik setiap 5 detik agar
        // hemat CPU
        // if (workspaceDef.flows() != null && !workspaceDef.flows().isEmpty()) {
        // String sampleFlowId = workspaceDef.flows().getFirst().id();
        // RuntimeStatisticsSnapshot stats = runtime.statistics(workspaceDef.id(),
        // sampleFlowId);
        // System.out.println(String.format("[MONITOR METRIC][%s] Sukses: %d | Gagal: %d
        // | Aktif Konkuren: %d",
        // sampleFlowId, stats.completed(), stats.failed(), stats.running()));
        // }
        // }
        // } catch (InterruptedException e) {
        // Thread.currentThread().interrupt();
        // }

        // TAHAN MAIN THREAD SECARA PASIF (0% CPU Usage untuk perulangan)
        try {
            // Perintah ini menyuruh Main Thread untuk menunggu sampai aplikasi dimatikan
            // (Ctrl+C).
            // Tidak ada perulangan, tidak ada sleep 5 detik, tidak ada print log STATS ke
            // CMD.
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[standalone] Main thread terinterupsi, mematikan runner...");
            Thread.currentThread().interrupt();
        }

    }

    public static void loadDynamicPlugins() {
        try {
            File baseDir = new File(".").getAbsoluteFile();
            File pluginDir = new File(baseDir, "plugins");
            if (!pluginDir.exists()) {
                pluginDir.mkdirs();
            }

            File[] jarFiles = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles == null || jarFiles.length == 0) {
                System.out.println(
                        "[Nexa Dynamic Loader] Berjalan dalam mode Native Fallback (0 JAR plugin eksternal terdeteksi).");
                return;
            }

            URL[] urls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                urls[i] = jarFiles[i].toURI().toURL();
            }

            URLClassLoader classLoader = new URLClassLoader(urls, NexaStandaloneRunner.class.getClassLoader());
            ServiceLoader<NexaPlugin> serviceLoader = ServiceLoader.load(NexaPlugin.class, classLoader);

            for (NexaPlugin plugin : serviceLoader) {
                PluginRegistry.registerMeta(plugin.getPluginType(), plugin.getClass());
                System.out.println(
                        "[Nexa Dynamic Loader] Berhasil meregistrasikan biner plugin: " + plugin.getPluginType());
            }
        } catch (Exception e) {
            System.err.println("[Nexa Dynamic Loader][Critical Error] Gagal memuat folder plugin: " + e.getMessage());
        }
    }
}