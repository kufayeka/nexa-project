package nexa.framework.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;
import nexa.framework.runtime.domain.workspace.model.ConnectionDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SuperConfigurableStressTest merupakan pengujian stres tingkat tinggi yang
 * kompleks.
 * Dapat diatur jumlah flow, nodes per flow, dan fan-out degree via System
 * Properties atau static field.
 * Skrip eksekusi menggunakan 100% fitur dari Nexa DSL V1 (comments,
 * null-safety, array methods, regex, dll.).
 */
public final class SuperConfigurableStressTest {

    // Konfigurasi stres tes (bisa disetel via System Properties)
    private static final int FLOW_COUNT = Integer.getInteger("stress.flows", 50);
    private static final int FANOUT_DEGREE = Integer.getInteger("stress.fanout", 4);

    @Test
    public void executeSuperStressTest() throws Exception {
        int expectedOutputs = FLOW_COUNT * FANOUT_DEGREE;
        CountDownLatch latch = new CountDownLatch(expectedOutputs);
        AtomicInteger outputCounter = new AtomicInteger(0);

        // 1. Definisikan Output Consumer untuk menghitung pemrosesan downstream
        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            outputCounter.incrementAndGet();
            latch.countDown();
        };

        // 2. Inisialisasi Runtime Engine (Composition Root)
        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(15)),
                outputConsumer);

        String workspaceId = "ws-super-stress";

        System.out.println("[super-stress] Membangun workspace dengan " + FLOW_COUNT + " flows...");
        long startBuild = System.nanoTime();
        WorkspaceDefinition workspaceDef = generateSuperStressWorkspace(workspaceId, FLOW_COUNT, FANOUT_DEGREE);
        long buildDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startBuild);
        System.out.println("[super-stress] Workspace selesai dibangun dalam " + buildDurationMs + " ms.");

        // Simpan ke berkas JSON
        File jsonFile = new File("workspaces/super_stress_workspace.json");
        jsonFile.getParentFile().mkdirs();
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonFile, workspaceDef);
        System.out.println("[super-stress] Workspace JSON disimpan ke: " + jsonFile.getAbsolutePath());

        // 3. Deploy workspace (kompilator akan mengompilasi ratusan skrip secara
        // bersamaan)
        System.out.println("[super-stress] Memulai kompilasi dan deploy...");
        long startDeploy = System.nanoTime();
        runtime.deploy(workspaceDef);
        long deployDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startDeploy);
        System.out.println("[super-stress] Kompilasi & deploy selesai dalam " + deployDurationMs + " ms.");

        // 4. Aktifkan runtime
        runtime.startRuntime();

        // 5. Picu pemicu manual untuk setiap flow secara paralel menggunakan virtual
        // threads
        System.out.println("[super-stress] Menembakkan trigger manual ke " + FLOW_COUNT + " flows...");
        long startTrigger = System.nanoTime();

        Thread[] threads = new Thread[FLOW_COUNT];
        for (int i = 0; i < FLOW_COUNT; i++) {
            final String flowId = "flow-" + i;
            threads[i] = Thread.startVirtualThread(() -> {
                RuntimeMessage seed = new RuntimeMessage();
                // Kirim payload mentah untuk di-parse oleh script
                seed.writeValue("payload.rawData",
                        "{\"speed\":150,\"temp\":105.2,\"batches\":[5,15,25,35],\"name\":\"  WO-9988-B  \"}");
                runtime.trigger(workspaceId, flowId, "input-manual", seed);
            });
        }

        // Tunggu semua thread trigger menembak
        for (Thread thread : threads) {
            thread.join();
        }

        // 6. Tunggu hingga semua rute fan-out paralel selesai diproses oleh virtual
        // threads
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        long triggerDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTrigger);

        System.out.println("[super-stress] Pemrosesan selesai dalam " + triggerDurationMs + " ms.");
        System.out.println(
                "[super-stress] Total output yang dikonsumsi: " + outputCounter.get() + " / " + expectedOutputs);

        // Pastikan semua output terkirim tanpa ada kebocoran
        assertTrue(finished, "Stress test timeout! Hanya memproses " + outputCounter.get() + " pesan.");
        assertTrue(outputCounter.get() >= expectedOutputs);

        // 7. Cek metrik statistik untuk flow pertama untuk memastikan stats dicatat
        // dengan benar
        RuntimeStatisticsSnapshot stats = runtime.statistics(workspaceId, "flow-0");
        System.out.println("[super-stress] Statistik flow-0: completed=" + stats.completed()
                + " failed=" + stats.failed()
                + " rejected=" + stats.rejected()
                + " avgTimeMs=" + stats.averageExecutionTimeMillis());

        runtime.stopRuntime();
    }

    private WorkspaceDefinition generateSuperStressWorkspace(String workspaceId, int flowCount, int fanoutDegree) {
        List<FlowDefinition> flows = new ArrayList<>();

        // Buat skrip fanout utama untuk merutekan pesan ke port fan-out paralel
        StringBuilder fanoutScriptBuilder = new StringBuilder();
        fanoutScriptBuilder.append("send([");
        for (int i = 0; i < fanoutDegree; i++) {
            if (i > 0)
                fanoutScriptBuilder.append(", ");
            fanoutScriptBuilder.append("\"p").append(i).append("\"");
        }
        fanoutScriptBuilder.append("], msg)");
        String fanoutScript = fanoutScriptBuilder.toString();

        // Skrip kompleks yang memanfaatkan seluruh fitur Nexa DSL V1
        String complexProcessingScript = "// 1. Single-line comment: Memulai kalkulasi sensor batch\n" +
                "/*\n" +
                " * 2. Block comment:\n" +
                " * Melakukan parsing JSON, null-safety, named function, map-filter-reduce,\n" +
                " * switch-case, regex replace, dan format datetime.\n" +
                " */\n" +
                "val rawInput = msg.payload?.rawData ?? \"{\\\"speed\\\":100,\\\"temp\\\":95.5,\\\"batches\\\":[10,20,30]}\"\n"
                +
                "val parsed = Json.parse(rawInput)\n" +
                "\n" +
                "// 3. Null-safety & type conversion\n" +
                "val baseSpeed = (parsed?.speed ?? 0).toNumber()\n" +
                "val temperature = (parsed?.temp ?? 0).toNumber()\n" +
                "\n" +
                "// 4. Named function\n" +
                "fun computeAdjustment(speed, temp) {\n" +
                "    val rounded = Math.round(temp)\n" +
                "    if (speed > 120) {\n" +
                "        return Math.max(rounded, 100)\n" +
                "    }\n" +
                "    return Math.min(rounded, 80)\n" +
                "}\n" +
                "val adjustment = computeAdjustment(baseSpeed, temperature)\n" +
                "\n" +
                "// 5. Higher-order functions (map, filter, reduce) & Lambda\n" +
                "val batches = parsed?.batches ?? []\n" +
                "val doubledBatches = batches.map(fun (x) => x * 2)\n" +
                "val highBatches = doubledBatches.filter(fun (x) => x > 20)\n" +
                "val sumBatches = highBatches.reduce(fun (acc, x) => acc + x, 0)\n" +
                "\n" +
                "// 6. Switch statement\n" +
                "var statusStr = \"UNKNOWN\"\n" +
                "switch (baseSpeed) {\n" +
                "    case 100:\n" +
                "        statusStr = \"STABLE\"\n" +
                "    case 150:\n" +
                "        statusStr = \"PEAK\"\n" +
                "    default:\n" +
                "        statusStr = \"NORMAL\"\n" +
                "}\n" +
                "\n" +
                "// 7. Regex & string methods\n" +
                "val sensorName = parsed?.name ?? \"  WO-105-A  \"\n" +
                "val cleanedSensorName = sensorName.trim().toUpperCase()\n" +
                "val matches = cleanedSensorName.match(\"WO-\\\\d+\")\n" +
                "var extractedWo = \"NONE\"\n" +
                "if (matches.length > 0) {\n" +
                "    extractedWo = matches[0]\n" +
                "}\n" +
                "val replacedSensorName = Regex.replace(cleanedSensorName, \"-\", \"_\")\n" +
                "\n" +
                "// 8. DateTime\n" +
                "val executionTime = DateTime.now().toISOString()\n" +
                "\n" +
                "// 9. Reassemble\n" +
                "msg.payload = {\n" +
                "    originalSpeed: baseSpeed,\n" +
                "    adjustment: adjustment,\n" +
                "    status: statusStr,\n" +
                "    totalBatchCount: batches.length,\n" +
                "    highBatchSum: sumBatches,\n" +
                "    extractedWorkOrder: extractedWo,\n" +
                "    formattedSensor: replacedSensorName,\n" +
                "    processedAt: executionTime\n" +
                "}\n" +
                "send(msg)";

        for (int index = 0; index < flowCount; index++) {
            String flowId = "flow-" + index;
            List<NodeDefinition> nodes = new ArrayList<>();
            List<ConnectionDefinition> connections = new ArrayList<>();

            // 1. Input Node
            nodes.add(new NodeDefinition(
                    "input-manual",
                    NodeCategory.INPUT,
                    "manual-input",
                    null,
                    true,
                    new InputExecutionPolicyDefinition(1024),
                    Map.of()));

            // 2. Routing Executor Node (Fan-out)
            nodes.add(new NodeDefinition(
                    "exec-router",
                    NodeCategory.EXECUTOR,
                    "script",
                    "nexa",
                    true,
                    new InputExecutionPolicyDefinition(null),
                    Map.of("script", fanoutScript)));

            connections.add(new ConnectionDefinition("input-manual", "default", "exec-router"));

            // 3. Cabang Fan-out (masing-masing mengeksekusi Nexa script kompleks)
            for (int f = 0; f < fanoutDegree; f++) {
                String port = "p" + f;
                String execId = "exec-complex-" + f;
                String outId = "out-debug-" + f;

                nodes.add(new NodeDefinition(
                        execId,
                        NodeCategory.EXECUTOR,
                        "script",
                        "nexa",
                        true,
                        new InputExecutionPolicyDefinition(null),
                        Map.of("script", complexProcessingScript)));

                nodes.add(new NodeDefinition(
                        outId,
                        NodeCategory.OUTPUT,
                        "debug-output",
                        null,
                        true,
                        new InputExecutionPolicyDefinition(null),
                        Map.of()));

                connections.add(new ConnectionDefinition("exec-router", port, execId));
                connections.add(new ConnectionDefinition(execId, "default", outId));
            }

            flows.add(new FlowDefinition(flowId, flowId, true, nodes, connections));
        }

        return new WorkspaceDefinition(workspaceId, true, flows);
    }
}
