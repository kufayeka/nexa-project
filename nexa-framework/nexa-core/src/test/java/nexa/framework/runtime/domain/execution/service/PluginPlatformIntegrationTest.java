package nexa.framework.runtime.domain.execution.service;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.plugin.NexaPluginContext;
import nexa.framework.runtime.api.plugin.NexaResourcePlugin;
import nexa.framework.runtime.api.plugin.NexaSourcePlugin;
import nexa.framework.runtime.api.plugin.NexaFunctionPlugin;
import nexa.framework.runtime.api.plugin.NexaSinkPlugin;
import nexa.framework.runtime.domain.workspace.model.ConnectionDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;
import nexa.framework.runtime.domain.workspace.model.ResourceDefinition;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.scripting.registry.PluginRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PluginPlatformIntegrationTest {

    @Test
    public void testPluginPlatformLifecycleAndExecution() throws Exception {
        // 1. Daftarkan meta class plugin ke registry
        PluginRegistry.registerMeta("dummy-resource", DummyResourcePlugin.class);
        PluginRegistry.registerMeta("dummy-source", DummySourcePlugin.class);
        PluginRegistry.registerMeta("dummy-function", DummyFunctionPlugin.class);
        PluginRegistry.registerMeta("dummy-sink", DummySinkPlugin.class);

        // State verifikasi
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger sinkCallCount = new AtomicInteger(0);
        AtomicBoolean resourceInitialized = new AtomicBoolean(false);
        AtomicBoolean resourceStarted = new AtomicBoolean(false);

        // Suntikkan verifikator statis ke dummy class (untuk simplifikasi pengujian)
        DummyResourcePlugin.onInitCallback = () -> resourceInitialized.set(true);
        DummyResourcePlugin.onStartCallback = () -> resourceStarted.set(true);

        DummySinkPlugin.consumeCallback = msg -> {
            sinkCallCount.incrementAndGet();
            assertEquals("hello-transformed", msg.readRawValue("payload.status"));
            assertEquals("dummy-native-client-pool", msg.readRawValue("payload.resourceClient"));
            latch.countDown();
        };

        // 2. Buat objek WorkspaceDefinition baru dengan Resources
        ResourceDefinition resDef = new ResourceDefinition("res-db", "dummy-resource", Map.of("jdbcUrl", "jdbc:dummy"));

        NodeDefinition sourceNode = new NodeDefinition(
                "node-source",
                NodeCategory.INPUT,
                "dummy-source",
                null,
                true,
                new InputExecutionPolicyDefinition(null),
                Map.of("messagePayload", "hello"));

        NodeDefinition functionNode = new NodeDefinition(
                "node-function",
                NodeCategory.EXECUTOR,
                "dummy-function",
                null,
                true,
                new InputExecutionPolicyDefinition(null),
                Map.of("suffix", "-transformed", "resourceRef", "res-db"));

        NodeDefinition sinkNode = new NodeDefinition(
                "node-sink",
                NodeCategory.OUTPUT,
                "dummy-sink",
                null,
                true,
                new InputExecutionPolicyDefinition(null),
                Map.of());

        List<ConnectionDefinition> connections = List.of(
                new ConnectionDefinition(
                        UUID.randomUUID().toString(), "node-source", "default", "node-function"),

                new ConnectionDefinition(
                        UUID.randomUUID().toString(), "node-function", "default", "node-sink"));

        FlowDefinition flow = new FlowDefinition("flow-1", "flow-1", true, List.of(sourceNode, functionNode, sinkNode),
                connections);
        WorkspaceDefinition wsDef = new WorkspaceDefinition("ws-plugin-test", true, List.of(resDef), List.of(flow));

        // 3. Setup Engine
        OutputConsumer outputConsumer = (context, nodeId, message) -> {
        };
        RuntimeEngine runtime = new DefaultRuntimeEngine(new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        // 4. Deploy dan Start
        runtime.deploy(wsDef);

        assertTrue(resourceInitialized.get(), "Resource plugin should be initialized on deploy!");
        assertTrue(resourceStarted.get(), "Resource plugin should be started on deploy/onStart!");

        runtime.startRuntime();

        // 5. Tunggu pesan diproses melalui pipeline plugin eksternal
        boolean complete = latch.await(3, TimeUnit.SECONDS);
        assertTrue(complete, "Message did not flow through the plugin execution pipeline in time!");
        assertEquals(1, sinkCallCount.get(), "Sink plugin should have been called exactly once!");

        // 6. Stop & Undeploy
        runtime.stopRuntime();
        runtime.undeploy("ws-plugin-test");

        // Bersihkan callback
        DummyResourcePlugin.onInitCallback = null;
        DummyResourcePlugin.onStartCallback = null;
        DummySinkPlugin.consumeCallback = null;
    }

    // --- Mock Plugin Classes ---

    public static class DummyResourcePlugin implements NexaResourcePlugin {
        static Runnable onInitCallback;
        static Runnable onStartCallback;

        @Override
        public String getPluginType() {
            return "dummy-resource";
        }

        @Override
        public Object getNativeClient() {
            return "dummy-native-client-pool";
        }

        @Override
        public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
            if (onInitCallback != null)
                onInitCallback.run();
        }

        @Override
        public void onStart() throws Exception {
            if (onStartCallback != null)
                onStartCallback.run();
        }

        @Override
        public void onStop() {
        }
    }

    public static class DummySourcePlugin implements NexaSourcePlugin {
        private Consumer<RuntimeMessage> emitter;
        private String payloadText;

        @Override
        public String getPluginType() {
            return "dummy-source";
        }

        @Override
        public void setEmitter(Consumer<RuntimeMessage> emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
            this.payloadText = (String) config.getOrDefault("messagePayload", "default-payload");
        }

        @Override
        public void onStart() throws Exception {
            if (emitter != null) {
                RuntimeMessage msg = new RuntimeMessage();
                msg.writeValue("payload.data", payloadText);
                emitter.accept(msg);
            }
        }

        @Override
        public void onStop() {
        }
    }

    public static class DummyFunctionPlugin implements NexaFunctionPlugin {
        private String suffix;
        private String dbRef;
        private NexaPluginContext context;

        @Override
        public String getPluginType() {
            return "dummy-function";
        }

        @Override
        public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
            this.suffix = (String) config.getOrDefault("suffix", "");
            this.dbRef = (String) config.get("resourceRef");
            this.context = context;
        }

        @Override
        public void onStart() throws Exception {
        }

        @Override
        public RuntimeMessage process(RuntimeMessage msg) {
            String data = msg.readValue("payload.data", String.class);
            String transformed = data + suffix;
            msg.writeValue("payload.status", transformed);

            // Cari shared resource db
            if (dbRef != null && context != null) {
                Object client = context.getSharedResource(dbRef);
                msg.writeValue("payload.resourceClient", client);
            }

            return msg;
        }

        @Override
        public void onStop() {
        }
    }

    public static class DummySinkPlugin implements NexaSinkPlugin {
        static Consumer<RuntimeMessage> consumeCallback;

        @Override
        public String getPluginType() {
            return "dummy-sink";
        }

        @Override
        public void onInit(String targetId, Map<String, Object> config, NexaPluginContext context) throws Exception {
        }

        @Override
        public void onStart() throws Exception {
        }

        @Override
        public void consume(RuntimeMessage msg) {
            if (consumeCallback != null) {
                consumeCallback.accept(msg);
            }
        }

        @Override
        public void onStop() {
        }
    }
}
