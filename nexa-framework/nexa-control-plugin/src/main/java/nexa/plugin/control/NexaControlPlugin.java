package nexa.plugin.control;

import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.*;
import nexa.framework.runtime.api.control.*;
import nexa.framework.runtime.api.control.events.ScriptNodeFailureEvent;
import nexa.framework.runtime.api.plugin.NexaPlugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import nexa.framework.runtime.api.model.RuntimeMessage;

import java.util.Properties;

public class NexaControlPlugin implements NexaPlugin, NexaControlService {
    private Javalin app;
    private Server mqttBroker;
    private final ObjectMapper mapper;

    public NexaControlPlugin() {
        this.mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(RuntimeMessage.class, new com.fasterxml.jackson.databind.JsonSerializer<RuntimeMessage>() {
            @Override
            public void serialize(RuntimeMessage value, com.fasterxml.jackson.core.JsonGenerator gen,
                    com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                gen.writeObject(value.values());
            }
        });
        this.mapper.registerModule(module);
    }

    @Override
    public String getPluginType() {
        return "control-plugin";
    }

    @Override
    public void start(NexaControlContext context) {
        try {
            mqttBroker = new Server();
            Properties config = new Properties();
            config.put("port", "1883");
            config.put("host", "0.0.0.0");
            config.put("allow_anonymous", "true");
            config.put("persistence_enabled", "false");
            mqttBroker.startServer(new MemoryConfig(config));
        } catch (Exception e) {
            e.printStackTrace();
        }

        context.getEventBus().subscribe("nexa/monitor/node/errors", ScriptNodeFailureEvent.class, event -> {
            try {
                byte[] payload = mapper.writeValueAsBytes(event);
                MqttPublishMessage message = MqttMessageBuilders.publish()
                        .topicName("nexa/monitor/node/errors")
                        .retained(false)
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .payload(Unpooled.copiedBuffer(payload))
                        .build();
                mqttBroker.internalPublish(message, "SERVER");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        app = Javalin.create(config -> {
            config.registerPlugin(new OpenApiPlugin(pluginConfig -> {
                pluginConfig.withDefinitionConfiguration((version, definition) -> {
                    definition.withInfo(info -> {
                        info.setTitle("Nexa Control API");
                        info.setVersion("1.0.0");
                        info.setDescription("REST control and monitoring API for the Nexa Runtime.");
                    });
                });
            }));
            config.registerPlugin(new SwaggerPlugin());
        }).start(8080);

        // --- Workspace Control ---
        app.post("/api/workspace/load", ctx -> {
            context.getWorkspaceControl().loadWorkspace(ctx.body());
            ctx.status(200).result("Workspace loaded");
        });

        app.post("/api/workspace/unload", ctx -> {
            String workspaceId = ctx.queryParam("workspaceId");
            context.getWorkspaceControl().unloadWorkspace(workspaceId);
            ctx.status(200).result("Workspace unloaded: " + workspaceId);
        });

        app.post("/api/workspace/enable", ctx -> {
            String workspaceId = ctx.queryParam("workspaceId");
            context.getWorkspaceControl().enableWorkspace(workspaceId);
            ctx.status(200).result("Workspace enabled: " + workspaceId);
        });

        app.post("/api/workspace/disable", ctx -> {
            String workspaceId = ctx.queryParam("workspaceId");
            context.getWorkspaceControl().disableWorkspace(workspaceId);
            ctx.status(200).result("Workspace disabled: " + workspaceId);
        });

        app.get("/api/workspace/list", ctx -> {
            ctx.json(context.getWorkspaceControl().getWorkspacesInfo());
        });

        app.get("/api/workspace/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ctx.json(context.getWorkspaceControl().getWorkspaceInfo(id));
        });

        app.get("/api/workspace/{id}/data", ctx -> {
            String id = ctx.pathParam("id");
            String data = context.getWorkspaceControl().getWorkspaceData(id);
            if (data == null) {
                ctx.status(404).result("Workspace not found");
            } else {
                ctx.status(200).result(data);
            }
        });

        app.post("/api/workspace/validate", ctx -> {
            ctx.json(context.getWorkspaceControl().validateWorkspace(ctx.body()));
        });

        app.post("/api/workspace/validate-script", ctx -> {
            String language = ctx.queryParam("language");
            String script = ctx.body();
            ctx.json(context.getWorkspaceControl().validateNodeScript(language, script));
        });

        // --- Node Control ---
        app.post("/api/node/enable", ctx -> {
            String nodeId = ctx.queryParam("nodeId");
            context.getNodeControl().enableNode(nodeId);
            ctx.status(200).result("Node enabled: " + nodeId);
        });

        app.post("/api/node/disable", ctx -> {
            String nodeId = ctx.queryParam("nodeId");
            context.getNodeControl().disableNode(nodeId);
            ctx.status(200).result("Node disabled: " + nodeId);
        });

        app.get("/api/node/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ctx.json(context.getNodeControl().getNodeInfo(id));
        });

        app.post("/api/node/breakpoint/add", ctx -> {
            String nodeId = ctx.queryParam("nodeId");
            context.getNodeControl().addBreakpoint(nodeId);
            ctx.status(200).result("Breakpoint added: " + nodeId);
        });

        app.post("/api/node/breakpoint/remove", ctx -> {
            String nodeId = ctx.queryParam("nodeId");
            context.getNodeControl().removeBreakpoint(nodeId);
            ctx.status(200).result("Breakpoint removed: " + nodeId);
        });

        app.post("/api/node/breakpoint/resume", ctx -> {
            String nodeId = ctx.queryParam("nodeId");
            context.getNodeControl().resumeNode(nodeId);
            ctx.status(200).result("Node resumed: " + nodeId);
        });

        app.post("/api/node/breakpoint/step", ctx -> {
            String nodeId = ctx.queryParam("nodeId");
            context.getNodeControl().stepNode(nodeId);
            ctx.status(200).result("Node stepped: " + nodeId);
        });

        app.get("/api/node/breakpoint/message/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ctx.json(context.getNodeControl().getPausedMessage(id));
        });

        // --- Connection Control ---
        app.post("/api/connection/enable", ctx -> {
            String connectionId = ctx.queryParam("connectionId");
            context.getConnectionControl().enableConnection(connectionId);
            ctx.status(200).result("Connection enabled: " + connectionId);
        });

        app.post("/api/connection/disable", ctx -> {
            String connectionId = ctx.queryParam("connectionId");
            context.getConnectionControl().disableConnection(connectionId);
            ctx.status(200).result("Connection disabled: " + connectionId);
        });

        app.post("/api/connection/inject", ctx -> {
            String connectionId = ctx.queryParam("connectionId");
            RuntimeMessage msg = mapper.readValue(ctx.body(), RuntimeMessage.class);

            context.getConnectionControl().injectMessageIntoConnection(connectionId, msg);

            ctx.status(200).result("Message injected into connection: " + connectionId);
        });

        // UNsED!
        // app.post("/api/connection/add", ctx -> {
        // String source = ctx.queryParam("source");
        // String target = ctx.queryParam("target");
        // context.getConnectionControl().addConnection(source, target);
        // ctx.status(200).result("Connection added: " + source + " -> " + target);
        // });

        // UNsED!
        // app.post("/api/connection/remove", ctx -> {
        // String connectionId = ctx.queryParam("connectionId");
        // context.getConnectionControl().removeConnection(connectionId);
        // ctx.status(200).result(
        // "Connection removed: " + connectionId);
        // });

        // --- Runtime Control & Monitoring ---
        app.get("/api/runtime/status", ctx -> {
            ctx.json(context.getRuntimeControl().getSystemStatus());
        });

        app.post("/api/runtime/shutdown", ctx -> {
            ctx.status(200).result("Shutdown triggered");
            context.getRuntimeControl().shutdown();
        });

        app.post("/api/runtime/gc", ctx -> {
            context.getRuntimeControl().triggerGarbageCollection();
            ctx.status(200).result("Garbage collection triggered");
        });

        app.post("/api/runtime/reload-plugins", ctx -> {
            context.getRuntimeControl().reloadPlugins();
            ctx.status(200).result("Plugins reloaded");
        });

        app.post("/api/runtime/metrics/reset/workspace", ctx -> {
            String workspaceId = ctx.queryParam("workspaceId");
            context.getRuntimeControl().resetWorkspaceMetrics(workspaceId);
            ctx.status(200).result("Workspace metrics reset: " + workspaceId);
        });

        app.post("/api/runtime/metrics/reset/node", ctx -> {
            String nodeId = ctx.queryParam("nodeId");
            context.getRuntimeControl().resetNodeMetrics(nodeId);
            ctx.status(200).result("Node metrics reset: " + nodeId);
        });
    }

    @Override
    public void stop() {
        if (app != null)
            app.stop();
        if (mqttBroker != null)
            mqttBroker.stopServer();
    }
}
