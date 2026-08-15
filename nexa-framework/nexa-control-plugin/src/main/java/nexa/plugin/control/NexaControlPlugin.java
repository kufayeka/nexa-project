package nexa.plugin.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import nexa.framework.runtime.api.control.NexaControlContext;
import nexa.framework.runtime.api.control.NexaControlService;
import nexa.framework.runtime.api.control.events.ScriptNodeFailureEvent;
import nexa.framework.runtime.api.control.model.ConnectionInfo;
import nexa.framework.runtime.api.control.model.NodeInfo;
import nexa.framework.runtime.api.control.model.NodeMessageHistory;
import nexa.framework.runtime.api.control.model.SystemStatus;
import nexa.framework.runtime.api.control.model.WorkspaceMetaInfo;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.api.plugin.NexaPlugin;
import nexa.plugin.control.dto.ActionResponse;
import nexa.plugin.control.dto.ErrorResponse;
import nexa.plugin.control.dto.WorkspaceListResponse;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Nexa's machine-facing REST control plane.
 *
 * <p>The API is deliberately JSON-only and backed by compile-time OpenAPI
 * annotations in {@link ControlApiDocumentation}. The same contract can be
 * consumed by Swagger UI, OpenAPI Generator, Orval, Dart generators, and Java
 * client generators.
 */
public class NexaControlPlugin implements NexaPlugin, NexaControlService {
    private static final int HTTP_PORT = 8080;
    private static final int MQTT_PORT = 1883;
    private static final String API_VERSION = "1.0.0";
    private static final String NODE_ERROR_TOPIC = "nexa/monitor/node/errors";

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
        startMqttBroker();
        subscribeNodeFailures(context);
        startHttpApi(context);
    }

    private void startMqttBroker() {
        try {
            mqttBroker = new Server();
            Properties config = new Properties();
            config.put("port", Integer.toString(MQTT_PORT));
            config.put("host", "0.0.0.0");
            config.put("allow_anonymous", "true");
            config.put("persistence_enabled", "false");
            mqttBroker.startServer(new MemoryConfig(config));
        } catch (Exception e) {
            System.err.println("[CONTROL] Failed to start embedded MQTT broker");
            e.printStackTrace();
        }
    }

    private void subscribeNodeFailures(NexaControlContext context) {
        context.getEventBus().subscribe(NODE_ERROR_TOPIC, ScriptNodeFailureEvent.class, event -> {
            try {
                byte[] payload = mapper.writeValueAsBytes(event);
                MqttPublishMessage message = MqttMessageBuilders.publish()
                        .topicName(NODE_ERROR_TOPIC)
                        .retained(false)
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .payload(Unpooled.copiedBuffer(payload))
                        .build();
                if (mqttBroker != null) {
                    mqttBroker.internalPublish(message, "SERVER");
                }
            } catch (Exception e) {
                System.err.println("[CONTROL] Failed to publish node failure event");
                e.printStackTrace();
            }
        });
    }

    private void startHttpApi(NexaControlContext context) {
        app = Javalin.create(config -> {
            config.registerPlugin(new OpenApiPlugin(pluginConfig -> {
                pluginConfig.withDefinitionConfiguration((version, definition) -> {
                    definition.withInfo(info -> {
                        info.setTitle("Nexa Control API");
                        info.setVersion(API_VERSION);
                        info.setDescription("Machine-facing REST control plane for Nexa Runtime. "
                                + "Use REST for lifecycle/control operations and MQTT for runtime event streaming. "
                                + "The generated OpenAPI contract is intended for strongly typed client generation.");
                    });
                });
            }));
            config.registerPlugin(new SwaggerPlugin());
        }).start(HTTP_PORT);

        app.exception(Exception.class, (exception, ctx) -> handleException(exception, ctx));

        registerWorkspaceRoutes(context);
        registerNodeRoutes(context);
        registerConnectionRoutes(context);
        registerRuntimeRoutes(context);
    }

    private void registerWorkspaceRoutes(NexaControlContext context) {
        app.post("/api/workspace/load", ctx -> {
            context.getWorkspaceControl().loadWorkspace(ctx.body());
            ctx.json(new ActionResponse(true, "Workspace loaded and deployed"));
        });

        app.post("/api/workspace/unload", ctx -> {
            String workspaceId = requiredQueryParam(ctx, "workspaceId");
            context.getWorkspaceControl().unloadWorkspace(workspaceId);
            ctx.json(new ActionResponse(true, "Workspace unloaded: " + workspaceId));
        });

        app.post("/api/workspace/enable", ctx -> {
            String workspaceId = requiredQueryParam(ctx, "workspaceId");
            context.getWorkspaceControl().enableWorkspace(workspaceId);
            ctx.json(new ActionResponse(true, "Workspace enabled: " + workspaceId));
        });

        app.post("/api/workspace/disable", ctx -> {
            String workspaceId = requiredQueryParam(ctx, "workspaceId");
            context.getWorkspaceControl().disableWorkspace(workspaceId);
            ctx.json(new ActionResponse(true, "Workspace disabled: " + workspaceId));
        });

        app.get("/api/workspace/list", ctx -> {
            List<WorkspaceMetaInfo> workspaces = context.getWorkspaceControl().getWorkspacesInfo();
            ctx.json(new WorkspaceListResponse(workspaces));
        });

        app.get("/api/workspace/{id}", ctx -> {
            String id = requiredPathParam(ctx, "id");
            WorkspaceMetaInfo info = context.getWorkspaceControl().getWorkspaceInfo(id);
            if (info == null) {
                throw new ResourceNotFoundException("Workspace not found: " + id);
            }
            ctx.json(info);
        });

        app.get("/api/workspace/{id}/data", ctx -> {
            String id = requiredPathParam(ctx, "id");
            String data = context.getWorkspaceControl().getWorkspaceData(id);
            if (data == null) {
                throw new ResourceNotFoundException("Workspace data not found: " + id);
            }
            ctx.contentType("application/json").result(data);
        });

        app.post("/api/workspace/validate", ctx ->
                ctx.json(context.getWorkspaceControl().validateWorkspace(ctx.body())));

        app.post("/api/workspace/validate-script", ctx -> {
            String language = requiredQueryParam(ctx, "language");
            String script = ctx.body();
            if (script == null || script.isBlank()) {
                throw new IllegalArgumentException("Request body script must not be blank");
            }
            ctx.json(context.getWorkspaceControl().validateNodeScript(language, script));
        });
    }

    private void registerNodeRoutes(NexaControlContext context) {
        app.post("/api/node/enable", ctx -> {
            String nodeId = requiredQueryParam(ctx, "nodeId");
            context.getNodeControl().enableNode(nodeId);
            ctx.json(new ActionResponse(true, "Node enabled: " + nodeId));
        });

        app.post("/api/node/disable", ctx -> {
            String nodeId = requiredQueryParam(ctx, "nodeId");
            context.getNodeControl().disableNode(nodeId);
            ctx.json(new ActionResponse(true, "Node disabled: " + nodeId));
        });

        app.get("/api/node/{id}", ctx -> {
            String id = requiredPathParam(ctx, "id");
            NodeInfo info = context.getNodeControl().getNodeInfo(id);
            if (info == null) {
                throw new ResourceNotFoundException("Node not found: " + id);
            }
            ctx.json(info);
        });

        app.get("/api/node/{id}/messages", ctx -> {
            String id = requiredPathParam(ctx, "id");
            NodeMessageHistory history = context.getNodeControl().getNodeMessages(id);
            if (history == null) {
                throw new ResourceNotFoundException("Node message history not found: " + id);
            }
            ctx.json(history);
        });

        app.post("/api/node/breakpoint/add", ctx -> {
            String nodeId = requiredQueryParam(ctx, "nodeId");
            context.getNodeControl().addBreakpoint(nodeId);
            ctx.json(new ActionResponse(true, "Breakpoint added: " + nodeId));
        });

        app.post("/api/node/breakpoint/remove", ctx -> {
            String nodeId = requiredQueryParam(ctx, "nodeId");
            context.getNodeControl().removeBreakpoint(nodeId);
            ctx.json(new ActionResponse(true, "Breakpoint removed: " + nodeId));
        });

        app.post("/api/node/breakpoint/resume", ctx -> {
            String nodeId = requiredQueryParam(ctx, "nodeId");
            context.getNodeControl().resumeNode(nodeId);
            ctx.json(new ActionResponse(true, "Node resumed: " + nodeId));
        });

        app.post("/api/node/breakpoint/step", ctx -> {
            String nodeId = requiredQueryParam(ctx, "nodeId");
            context.getNodeControl().stepNode(nodeId);
            ctx.json(new ActionResponse(true, "Node stepped: " + nodeId));
        });

        app.get("/api/node/breakpoint/message/{id}", ctx -> {
            String id = requiredPathParam(ctx, "id");
            RuntimeMessage message = context.getNodeControl().getPausedMessage(id);
            if (message == null) {
                throw new ResourceNotFoundException("No paused message found for node: " + id);
            }
            ctx.json(message.values());
        });
    }

    private void registerConnectionRoutes(NexaControlContext context) {
        app.get("/api/connection/{id}", ctx -> {
            String id = requiredPathParam(ctx, "id");
            ConnectionInfo info = context.getConnectionControl().getConnectionInfo(id);
            if (info == null) {
                throw new ResourceNotFoundException("Connection not found: " + id);
            }
            ctx.json(info);
        });

        app.post("/api/connection/enable", ctx -> {
            String connectionId = requiredQueryParam(ctx, "connectionId");
            context.getConnectionControl().enableConnection(connectionId);
            ctx.json(new ActionResponse(true, "Connection enabled: " + connectionId));
        });

        app.post("/api/connection/disable", ctx -> {
            String connectionId = requiredQueryParam(ctx, "connectionId");
            context.getConnectionControl().disableConnection(connectionId);
            ctx.json(new ActionResponse(true, "Connection disabled: " + connectionId));
        });

        app.post("/api/connection/inject", ctx -> {
            String connectionId = requiredQueryParam(ctx, "connectionId");
            Map<String, Object> data = mapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});
            if (data == null) {
                throw new IllegalArgumentException("Request body must be a JSON object");
            }

            RuntimeMessage message = new RuntimeMessage(data);
            context.getConnectionControl().injectMessageIntoConnection(connectionId, message);
            ctx.json(new ActionResponse(true, "Message injected into connection: " + connectionId));
        });
    }

    private void registerRuntimeRoutes(NexaControlContext context) {
        app.get("/api/runtime/status", ctx -> {
            SystemStatus status = context.getRuntimeControl().getSystemStatus();
            ctx.json(status);
        });

        app.post("/api/runtime/shutdown", ctx -> {
            ctx.json(new ActionResponse(true, "Shutdown triggered"));
            context.getRuntimeControl().shutdown();
        });

        app.post("/api/runtime/gc", ctx -> {
            context.getRuntimeControl().triggerGarbageCollection();
            ctx.json(new ActionResponse(true, "Garbage collection triggered"));
        });

        app.post("/api/runtime/reload-plugins", ctx -> {
            context.getRuntimeControl().reloadPlugins();
            ctx.json(new ActionResponse(true, "Plugins reload requested"));
        });

        app.post("/api/runtime/metrics/reset/workspace", ctx -> {
            String workspaceId = requiredQueryParam(ctx, "workspaceId");
            context.getRuntimeControl().resetWorkspaceMetrics(workspaceId);
            ctx.json(new ActionResponse(true, "Workspace metrics reset: " + workspaceId));
        });

        app.post("/api/runtime/metrics/reset/node", ctx -> {
            String nodeId = requiredQueryParam(ctx, "nodeId");
            context.getRuntimeControl().resetNodeMetrics(nodeId);
            ctx.json(new ActionResponse(true, "Node metrics reset: " + nodeId));
        });
    }

    private void handleException(Exception exception, io.javalin.http.Context ctx) {
        int status;
        String code;
        if (exception instanceof ResourceNotFoundException) {
            status = 404;
            code = "NOT_FOUND";
        } else if (exception instanceof IllegalArgumentException
                || exception instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            status = 400;
            code = "BAD_REQUEST";
        } else {
            status = 500;
            code = "INTERNAL_ERROR";
        }

        System.err.println("[CONTROL API] " + status + " " + ctx.method() + " " + ctx.path()
                + " - " + exception.getMessage());
        if (status >= 500) {
            exception.printStackTrace();
        }

        ctx.status(status).json(new ErrorResponse(
                code,
                exception.getMessage() == null ? code : exception.getMessage(),
                ctx.path()));
    }

    private static String requiredQueryParam(io.javalin.http.Context ctx, String name) {
        String value = ctx.queryParam(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " query parameter is required");
        }
        return value;
    }

    private static String requiredPathParam(io.javalin.http.Context ctx, String name) {
        String value = ctx.pathParam(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " path parameter is required");
        }
        return value;
    }

    @Override
    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
        if (mqttBroker != null) {
            mqttBroker.stopServer();
            mqttBroker = null;
        }
    }

    private static final class ResourceNotFoundException extends RuntimeException {
        private ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
