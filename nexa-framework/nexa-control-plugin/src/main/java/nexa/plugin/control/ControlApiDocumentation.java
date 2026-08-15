package nexa.plugin.control;

import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import nexa.framework.runtime.api.model.RuntimeMessage;

final class ControlApiDocumentation {
    private ControlApiDocumentation() {
    }

    @OpenApi(path = "/api/workspace/load", methods = HttpMethod.POST, summary = "Load a workspace", operationId = "loadWorkspace", tags = {
            "Workspace" }, requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = String.class), required = true), responses = @OpenApiResponse(status = "200", description = "Workspace loaded"))
    static void loadWorkspace() {
    }

    @OpenApi(path = "/api/workspace/unload", methods = HttpMethod.POST, summary = "Unload a workspace", operationId = "unloadWorkspace", tags = {
            "Workspace" }, queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Workspace identifier"), responses = @OpenApiResponse(status = "200", description = "Workspace unloaded"))
    static void unloadWorkspace() {
    }

    @OpenApi(path = "/api/workspace/enable", methods = HttpMethod.POST, summary = "Enable a workspace", operationId = "enableWorkspace", tags = {
            "Workspace" }, queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Workspace identifier"), responses = @OpenApiResponse(status = "200", description = "Workspace enabled"))
    static void enableWorkspace() {
    }

    @OpenApi(path = "/api/workspace/disable", methods = HttpMethod.POST, summary = "Disable a workspace", operationId = "disableWorkspace", tags = {
            "Workspace" }, queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Workspace identifier"), responses = @OpenApiResponse(status = "200", description = "Workspace disabled"))
    static void disableWorkspace() {
    }

    @OpenApi(path = "/api/workspace/list", methods = HttpMethod.GET, summary = "List workspaces", operationId = "listWorkspaces", tags = {
            "Workspace" }, responses = @OpenApiResponse(status = "200", description = "Workspace information list", content = @OpenApiContent(from = Object[].class)))
    static void listWorkspaces() {
    }

    @OpenApi(path = "/api/workspace/{id}", methods = HttpMethod.GET, summary = "Get workspace information", operationId = "getWorkspaceInfo", tags = {
            "Workspace" }, pathParams = @OpenApiParam(name = "id", required = true, description = "Workspace identifier"), responses = @OpenApiResponse(status = "200", description = "Workspace information", content = @OpenApiContent(from = Object.class)))
    static void getWorkspaceInfo() {
    }

    @OpenApi(path = "/api/workspace/{id}/data", methods = HttpMethod.GET, summary = "Get raw workspace JSON", operationId = "getWorkspaceData", tags = {
            "Workspace" }, pathParams = @OpenApiParam(name = "id", required = true, description = "Workspace identifier"), responses = {
                    @OpenApiResponse(status = "200", description = "Raw workspace JSON", content = @OpenApiContent(from = String.class)),
                    @OpenApiResponse(status = "404", description = "Workspace not found") })
    static void getWorkspaceData() {
    }

    @OpenApi(path = "/api/workspace/validate", methods = HttpMethod.POST, summary = "Validate a workspace definition", operationId = "validateWorkspace", tags = {
            "Workspace" }, requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = String.class), required = true), responses = @OpenApiResponse(status = "200", description = "Validation result", content = @OpenApiContent(from = Object.class)))
    static void validateWorkspace() {
    }

    @OpenApi(path = "/api/workspace/validate-script", methods = HttpMethod.POST, summary = "Validate a node script", operationId = "validateNodeScript", tags = {
            "Workspace" }, queryParams = @OpenApiParam(name = "language", required = true, description = "Script language"), requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = String.class), required = true), responses = @OpenApiResponse(status = "200", description = "Script validation result", content = @OpenApiContent(from = Object.class)))
    static void validateNodeScript() {
    }

    @OpenApi(path = "/api/node/enable", methods = HttpMethod.POST, summary = "Enable a node", operationId = "enableNode", tags = {
            "Node" }, queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Node enabled"))
    static void enableNode() {
    }

    @OpenApi(path = "/api/node/disable", methods = HttpMethod.POST, summary = "Disable a node", operationId = "disableNode", tags = {
            "Node" }, queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Node disabled"))
    static void disableNode() {
    }

    @OpenApi(path = "/api/node/{id}", methods = HttpMethod.GET, summary = "Get node information", operationId = "getNodeInfo", tags = {
            "Node" }, pathParams = @OpenApiParam(name = "id", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Node information", content = @OpenApiContent(from = Object.class)))
    static void getNodeInfo() {
    }

    @OpenApi(path = "/api/node/breakpoint/add", methods = HttpMethod.POST, summary = "Add a breakpoint to a node", operationId = "addNodeBreakpoint", tags = {
            "Node Debugging" }, queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Breakpoint added"))
    static void addBreakpoint() {
    }

    @OpenApi(path = "/api/node/breakpoint/remove", methods = HttpMethod.POST, summary = "Remove a node breakpoint", operationId = "removeNodeBreakpoint", tags = {
            "Node Debugging" }, queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Breakpoint removed"))
    static void removeBreakpoint() {
    }

    @OpenApi(path = "/api/node/breakpoint/resume", methods = HttpMethod.POST, summary = "Resume a paused node", operationId = "resumeNode", tags = {
            "Node Debugging" }, queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Node resumed"))
    static void resumeNode() {
    }

    @OpenApi(path = "/api/node/breakpoint/step", methods = HttpMethod.POST, summary = "Execute one node step", operationId = "stepNode", tags = {
            "Node Debugging" }, queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Node stepped"))
    static void stepNode() {
    }

    @OpenApi(path = "/api/node/breakpoint/message/{id}", methods = HttpMethod.GET, summary = "Get a paused message", operationId = "getPausedMessage", tags = {
            "Node Debugging" }, pathParams = @OpenApiParam(name = "id", required = true, description = "Paused message identifier"), responses = @OpenApiResponse(status = "200", description = "Paused runtime message", content = @OpenApiContent(from = RuntimeMessage.class)))
    static void getPausedMessage() {
    }

    @OpenApi(path = "/api/connection/enable", methods = HttpMethod.POST, summary = "Enable a connection", operationId = "enableConnection", tags = {
            "Connection" }, queryParams = @OpenApiParam(name = "connectionId", required = true, description = "Connection UUID"), responses = @OpenApiResponse(status = "200", description = "Connection enabled"))
    static void enableConnection() {
    }

    @OpenApi(path = "/api/connection/disable", methods = HttpMethod.POST, summary = "Disable a connection", operationId = "disableConnection", tags = {
            "Connection" }, queryParams = @OpenApiParam(name = "connectionId", required = true, description = "Connection UUID"), responses = @OpenApiResponse(status = "200", description = "Connection disabled"))
    static void disableConnection() {
    }

    @OpenApi(path = "/api/connection/inject", methods = HttpMethod.POST, summary = "Inject a runtime message into a connection", operationId = "injectMessage", tags = {
            "Connection" }, queryParams = @OpenApiParam(name = "connectionId", required = true, description = "Connection UUID"), requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RuntimeMessage.class), required = true), responses = @OpenApiResponse(status = "200", description = "Message injected"))
    static void injectMessage() {
    }

    @OpenApi(path = "/api/runtime/status", methods = HttpMethod.GET, summary = "Get runtime system status", operationId = "getRuntimeStatus", tags = {
            "Runtime" }, responses = @OpenApiResponse(status = "200", description = "Runtime system status", content = @OpenApiContent(from = Object.class)))
    static void getRuntimeStatus() {
    }

    @OpenApi(path = "/api/runtime/shutdown", methods = HttpMethod.POST, summary = "Shutdown the Nexa runtime", operationId = "shutdownRuntime", tags = {
            "Runtime" }, responses = @OpenApiResponse(status = "200", description = "Shutdown triggered"))
    static void shutdownRuntime() {
    }

    @OpenApi(path = "/api/runtime/gc", methods = HttpMethod.POST, summary = "Trigger garbage collection", operationId = "triggerGarbageCollection", tags = {
            "Runtime" }, responses = @OpenApiResponse(status = "200", description = "Garbage collection triggered"))
    static void triggerGarbageCollection() {
    }

    @OpenApi(path = "/api/runtime/reload-plugins", methods = HttpMethod.POST, summary = "Reload runtime plugins", operationId = "reloadPlugins", tags = {
            "Runtime" }, responses = @OpenApiResponse(status = "200", description = "Plugins reloaded"))
    static void reloadPlugins() {
    }

    @OpenApi(path = "/api/runtime/metrics/reset/workspace", methods = HttpMethod.POST, summary = "Reset workspace metrics", operationId = "resetWorkspaceMetrics", tags = {
            "Runtime Monitoring" }, queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Workspace identifier"), responses = @OpenApiResponse(status = "200", description = "Workspace metrics reset"))
    static void resetWorkspaceMetrics() {
    }

    @OpenApi(path = "/api/runtime/metrics/reset/node", methods = HttpMethod.POST, summary = "Reset node metrics", operationId = "resetNodeMetrics", tags = {
            "Runtime Monitoring" }, queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Node identifier"), responses = @OpenApiResponse(status = "200", description = "Node metrics reset"))
    static void resetNodeMetrics() {
    }
}
