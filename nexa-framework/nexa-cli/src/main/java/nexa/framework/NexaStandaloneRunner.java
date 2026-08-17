package nexa.framework;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.workspace.service.WorkspaceJsonLoader;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public final class NexaStandaloneRunner {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("        Nexa Runtime Standalone Runner           ");
        System.out.println("=================================================");

        // Fallback to workspace-main.json
        File baseDir = new File(".").getAbsoluteFile();
        String pathStr = args.length > 0 ? args[0] : "workspaces/workspace-main.json";
        File file = new File(pathStr);
        if (!file.isAbsolute()) {
            file = new File(baseDir, pathStr);
        }

        if (!file.exists()) {
            System.err.println("[Error] Berkas JSON Workspace tidak ditemukan di: " + file.getAbsolutePath());
            System.err.println(
                    "[Solusi] Pastikan folder 'workspaces/' dan file 'workspace-main.json' sudah ditaruh sejajar dengan nexa-core.jar");
            System.exit(1);
            return;
        }

        Path jsonPath = file.toPath();
        System.out.println("[standalone] Membaca konfigurasi produksi dari: " + jsonPath.toAbsolutePath());

        // Parse Workspace Definition
        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        WorkspaceDefinition workspaceDef = loader.fromFile(jsonPath);
        System.out.println("[standalone] Workspace '" + workspaceDef.id() + "' berhasil dimuat ke memori.");

        // Setup Output Consumer to Console
        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            System.out.println(String.format("[%s][DEBUG][%s] message: %s",
                    Instant.now().toString(), nodeId, message.values()));
        };

        // Initialize Core Engine
        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(15)),
                outputConsumer);

        // Deploy Workspace Flow Topology
        System.out.println("[standalone] Menghidupkan pipeline runtime Nexa Engine...");
        runtime.startRuntime();
        System.out.println("[standalone] Mentransformasikan graf biner dan mendesentralisasikan resource...");
        runtime.deploy(workspaceDef);

        int runDuration = Integer.getInteger("run.duration", 0);
        System.out.println("[standalone] Core Engine Aktif. Tekan Ctrl+C untuk menghentikan aplikasi.");
        if (runDuration > 0) {
            System.out.println("[standalone] Aliran data akan otomatis dihentikan setelah " + runDuration + " detik.");
        } else {
            System.out.println(
                    "[standalone] Aliran data dikonfigurasi berjalan tanpa batas waktu (Indefinitely Continuous).");
        }

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[standalone] Sinyal interupsi diterima. Mematikan seluruh subsistem pipeline...");
            runtime.stopRuntime();
            System.out.println("[standalone] Runtime dimatikan secara bersih (graceful). Server dihentikan.");
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[standalone] Main thread terinterupsi, mematikan runner...");
            Thread.currentThread().interrupt();
        }
    }
}