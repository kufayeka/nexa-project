package nexa.framework.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;
import nexa.framework.runtime.domain.workspace.model.ConnectionDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimedStressExecutorTest melakukan simulasi timed inject dari beberapa input
 * node sekaligus
 * dengan interval hingga milidetik, menyebar (fan-out) ke beberapa node
 * kompleks,
 * lalu menyatu kembali (fan-in) ke satu node output debug tunggal.
 */
public final class TimedStressExecutorTest {

        private static final int TIMED_INPUT_COUNT = Integer.getInteger("timed.inputs", 4);
        private static final String INTERVAL_MS = System.getProperty("timed.interval", "50ms");
        private static final int FANOUT_DEGREE = Integer.getInteger("timed.fanout", 3);
        private static final int TEST_DURATION_SEC = Integer.getInteger("timed.duration", 4);

        @Test
        public void executeTimedTest() throws Exception {
                AtomicInteger outputCounter = new AtomicInteger(0);

                // 1. Output Consumer (Fan-in akhir ke debug-output)
                OutputConsumer outputConsumer = (context, nodeId, message) -> {
                        outputCounter.incrementAndGet();
                };

                // 2. Setup Runtime Engine
                RuntimeEngine runtime = new DefaultRuntimeEngine(
                                new RuntimeConfiguration(Duration.ofSeconds(15)),
                                outputConsumer);

                String workspaceId = "ws-timed-stress";
                String flowId = "flow-timed-stress";

                System.out.println("[timed-stress] Membangun workspace...");
                WorkspaceDefinition workspaceDef = generateTimedStressWorkspace(
                                workspaceId, flowId, TIMED_INPUT_COUNT, INTERVAL_MS, FANOUT_DEGREE);

                // Save ke berkas JSON agar bisa dipakai oleh standalone runner
                File jsonFile = new File("workspaces/timed_stress_workspace.json");
                jsonFile.getParentFile().mkdirs();
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonFile, workspaceDef);
                System.out.println("[timed-stress] Workspace JSON disimpan ke: " + jsonFile.getAbsolutePath());

                // 3. Deploy dan Jalankan
                System.out.println("[timed-stress] Memulai deploy & compile...");
                runtime.deploy(workspaceDef);
                System.out.println("[timed-stress] Booting runtime engine...");
                runtime.startRuntime();

                // 4. Biarkan runtime berjalan untuk durasi waktu yang dikonfigurasi
                System.out.println("[timed-stress] Menjalankan timed inject selama " + TEST_DURATION_SEC + " detik...");
                TimeUnit.SECONDS.sleep(TEST_DURATION_SEC);

                // 5. Hentikan runtime
                System.out.println("[timed-stress] Menghentikan runtime...");
                runtime.stopRuntime();

                // 6. Tampilkan statistik & verifikasi
                RuntimeStatisticsSnapshot stats = runtime.statistics(workspaceId, flowId);
                int totalProcessed = outputCounter.get();
                System.out.println("[timed-stress] Pengujian Selesai.");
                System.out.println("[timed-stress] Total Output Terproses (Fan-in): " + totalProcessed);
                System.out.println("[timed-stress] Statistik flow: completed=" + stats.completed()
                                + " failed=" + stats.failed()
                                + " rejected=" + stats.rejected()
                                + " running=" + stats.running()
                                + " avgMs=" + stats.averageExecutionTimeMillis());

                // Pastikan tidak ada kegagalan error skrip
                assertTrue(stats.failed() == 0, "Ada skrip yang melempar error runtime!");
                assertTrue(totalProcessed > 0, "Pesan tidak terkirim atau tidak terproses!");
        }

        private WorkspaceDefinition generateTimedStressWorkspace(
                        String workspaceId,
                        String flowId,
                        int timedInputCount,
                        String intervalValue,
                        int fanoutDegree) {

                List<NodeDefinition> nodes = new ArrayList<>();
                List<ConnectionDefinition> connections = new ArrayList<>();

                // Skrip kompleks yang mencakup 100% fitur Nexa DSL
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

                // 1. Output Node Tunggal (Fan-in)
                String finalOutId = "out-debug";
                nodes.add(new NodeDefinition(
                                finalOutId,
                                NodeCategory.OUTPUT,
                                "debug-output",
                                null,
                                true,
                                new InputExecutionPolicyDefinition(null),
                                Map.of()));

                // 2. Loop pembuatan multiple timed-trigger inputs
                for (int i = 0; i < timedInputCount; i++) {
                        String inputId = "input-timed-" + i;
                        String routerId = "exec-router-" + i;

                        // Buat Timed Trigger Input Node
                        nodes.add(new NodeDefinition(
                                        inputId,
                                        NodeCategory.INPUT,
                                        "timed-trigger",
                                        null,
                                        true,
                                        new InputExecutionPolicyDefinition(1024),
                                        Map.of(
                                                        "interval", intervalValue,
                                                        "payload", Map.of(
                                                                        "rawData",
                                                                        "{\"speed\":150,\"temp\":105.2,\"batches\":[5,15,25,35],\"name\":\"  WO-9988-B  \"}"))));

                        // Buat Router Node untuk melakukan Fan-out
                        StringBuilder fanoutScriptBuilder = new StringBuilder();
                        fanoutScriptBuilder.append("send([");
                        for (int f = 0; f < fanoutDegree; f++) {
                                if (f > 0)
                                        fanoutScriptBuilder.append(", ");
                                fanoutScriptBuilder.append("\"p").append(f).append("\"");
                        }
                        fanoutScriptBuilder.append("], msg)");
                        String fanoutScript = fanoutScriptBuilder.toString();

                        nodes.add(new NodeDefinition(
                                        routerId,
                                        NodeCategory.EXECUTOR,
                                        "script",
                                        "nexa",
                                        true,
                                        new InputExecutionPolicyDefinition(null),
                                        Map.of("script", fanoutScript)));

                        // Hubungkan input ke router
                        connections.add(new ConnectionDefinition(inputId, "default", routerId));

                        // Buat cabang fan-out paralel untuk router ini
                        for (int f = 0; f < fanoutDegree; f++) {
                                String port = "p" + f;
                                String execId = "exec-complex-" + i + "-" + f;
                                String secondStageExecId = "exec-second-" + i + "-" + f;

                                // Stage 1: Complex script
                                nodes.add(new NodeDefinition(
                                                execId,
                                                NodeCategory.EXECUTOR,
                                                "script",
                                                "nexa",
                                                true,
                                                new InputExecutionPolicyDefinition(null),
                                                Map.of("script", complexProcessingScript)));

                                // Stage 2: Melakukan pengolahan lanjutan sederhana
                                nodes.add(new NodeDefinition(
                                                secondStageExecId,
                                                NodeCategory.EXECUTOR,
                                                "script",
                                                "nexa",
                                                true,
                                                new InputExecutionPolicyDefinition(null),
                                                Map.of("script",
                                                                "msg.payload.stage2Processed = true\n" +
                                                                                "send(\"default\", msg)")));

                                // Hubungkan router -> Stage 1 -> Stage 2 -> Fan-in debug output
                                connections.add(new ConnectionDefinition(routerId, port, execId));
                                connections.add(new ConnectionDefinition(execId, "default", secondStageExecId));
                                connections.add(new ConnectionDefinition(secondStageExecId, "default", finalOutId));
                        }
                }

                FlowDefinition flow = new FlowDefinition(flowId, flowId, true, nodes, connections);
                return new WorkspaceDefinition(workspaceId, true, List.of(flow));
        }
}
