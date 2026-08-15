package nexa.plugin.control;

import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import nexa.framework.runtime.api.control.model.ConnectionInfo;
import nexa.framework.runtime.api.control.model.NodeInfo;
import nexa.framework.runtime.api.control.model.NodeMessageHistory;
import nexa.framework.runtime.api.control.model.ScriptValidationResult;
import nexa.framework.runtime.api.control.model.SystemStatus;
import nexa.framework.runtime.api.control.model.ValidationResult;
import nexa.framework.runtime.api.control.model.WorkspaceMetaInfo;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.plugin.control.dto.ActionResponse;
import nexa.plugin.control.dto.ErrorResponse;
import nexa.plugin.control.dto.WorkspaceListResponse;

import java.util.Map;

/**
 * Compile-time OpenAPI contract for the Nexa Control REST API.
 *
 * <p>This class intentionally contains no HTTP implementation. Javalin's
 * annotation processor uses these annotations to generate the API contract,
 * including request/response schemas used by Swagger UI and client generators.
 */
final class ControlApiDocumentation {
    private ControlApiDocumentation() {}

    @OpenApi(path = "/api/workspace/load", methods = HttpMethod.POST,
            summary = "Load and deploy a workspace", operationId = "loadWorkspace", tags = {"Workspace"},
            requestBody = @OpenApiRequestBody(description = "Complete workspace definition to validate, compile, deploy, and make available to the runtime.", content = @OpenApiContent(from = WorkspaceDefinition.class), required = true),
            responses = {
                    @OpenApiResponse(status = "200", description = "Workspace successfully loaded and deployed.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "Workspace JSON is malformed or fails deployment validation.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Workspace deployment failed because a plugin or runtime component could not be initialized.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void loadWorkspace() {}

    @OpenApi(path = "/api/workspace/unload", methods = HttpMethod.POST,
            summary = "Undeploy a workspace", operationId = "unloadWorkspace", tags = {"Workspace"},
            queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Unique workspace identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Workspace undeployed.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "workspaceId is missing or invalid.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Workspace undeployment failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void unloadWorkspace() {}

    @OpenApi(path = "/api/workspace/enable", methods = HttpMethod.POST,
            summary = "Enable a workspace", operationId = "enableWorkspace", tags = {"Workspace"},
            queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Unique workspace identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Workspace enabled and its input activators started when the runtime is active.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "workspaceId is invalid or the workspace is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Workspace activation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void enableWorkspace() {}

    @OpenApi(path = "/api/workspace/disable", methods = HttpMethod.POST,
            summary = "Disable a workspace", operationId = "disableWorkspace", tags = {"Workspace"},
            queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Unique workspace identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Workspace disabled and its active input routes stopped.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "workspaceId is invalid or the workspace is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Workspace deactivation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void disableWorkspace() {}

    @OpenApi(path = "/api/workspace/list", methods = HttpMethod.GET,
            summary = "List deployed workspaces", operationId = "listWorkspaces", tags = {"Workspace"},
            responses = {
                    @OpenApiResponse(status = "200", description = "Current workspace metadata, including enabled state and flow/node counts.", content = @OpenApiContent(from = WorkspaceListResponse.class)),
                    @OpenApiResponse(status = "500", description = "Runtime state could not be read.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void listWorkspaces() {}

    @OpenApi(path = "/api/workspace/{id}", methods = HttpMethod.GET,
            summary = "Get workspace metadata", operationId = "getWorkspaceInfo", tags = {"Workspace"},
            pathParams = @OpenApiParam(name = "id", required = true, description = "Unique workspace identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Workspace metadata.", content = @OpenApiContent(from = WorkspaceMetaInfo.class)),
                    @OpenApiResponse(status = "404", description = "Workspace is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Runtime state could not be read.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void getWorkspaceInfo() {}

    @OpenApi(path = "/api/workspace/{id}/data", methods = HttpMethod.GET,
            summary = "Get the original workspace JSON", operationId = "getWorkspaceData", tags = {"Workspace"},
            pathParams = @OpenApiParam(name = "id", required = true, description = "Unique workspace identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "The exact workspace JSON submitted during load.", content = @OpenApiContent(from = String.class)),
                    @OpenApiResponse(status = "404", description = "Workspace data is not available.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Workspace data could not be read.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void getWorkspaceData() {}

    @OpenApi(path = "/api/workspace/validate", methods = HttpMethod.POST,
            summary = "Validate a workspace without deploying it", operationId = "validateWorkspace", tags = {"Workspace"},
            requestBody = @OpenApiRequestBody(description = "Workspace definition to validate. No runtime deployment is performed.", content = @OpenApiContent(from = WorkspaceDefinition.class), required = true),
            responses = {
                    @OpenApiResponse(status = "200", description = "Validation completed. The response contains validity, errors, and warnings.", content = @OpenApiContent(from = ValidationResult.class)),
                    @OpenApiResponse(status = "400", description = "The request body is not valid JSON.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Validation failed unexpectedly.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void validateWorkspace() {}

    @OpenApi(path = "/api/workspace/validate-script", methods = HttpMethod.POST,
            summary = "Validate node script source code", operationId = "validateNodeScript", tags = {"Workspace"},
            queryParams = @OpenApiParam(name = "language", required = true, description = "Registered scripting language identifier, for example `nexa`."),
            requestBody = @OpenApiRequestBody(description = "Script source code as UTF-8 text.", content = @OpenApiContent(from = String.class), required = true),
            responses = {
                    @OpenApiResponse(status = "200", description = "Script validation result.", content = @OpenApiContent(from = ScriptValidationResult.class)),
                    @OpenApiResponse(status = "400", description = "Language or script is missing/invalid.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Script validation failed unexpectedly.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void validateNodeScript() {}

    @OpenApi(path = "/api/node/enable", methods = HttpMethod.POST,
            summary = "Enable a node", operationId = "enableNode", tags = {"Node"},
            queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Node enabled.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "nodeId is invalid or node is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Node activation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void enableNode() {}

    @OpenApi(path = "/api/node/disable", methods = HttpMethod.POST,
            summary = "Disable a node", operationId = "disableNode", tags = {"Node"},
            queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Node disabled.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "nodeId is invalid or node is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Node deactivation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void disableNode() {}

    @OpenApi(path = "/api/node/{id}", methods = HttpMethod.GET,
            summary = "Get node runtime information", operationId = "getNodeInfo", tags = {"Node"},
            pathParams = @OpenApiParam(name = "id", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Node state and processing counters.", content = @OpenApiContent(from = NodeInfo.class)),
                    @OpenApiResponse(status = "404", description = "Node is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Node state could not be read.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void getNodeInfo() {}

    @OpenApi(path = "/api/node/{id}/messages", methods = HttpMethod.GET,
            summary = "Get node message history", operationId = "getNodeMessages", tags = {"Node"},
            pathParams = @OpenApiParam(name = "id", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Incoming and outgoing messages retained by the node runtime.", content = @OpenApiContent(from = NodeMessageHistory.class)),
                    @OpenApiResponse(status = "404", description = "Node is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Message history could not be read.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void getNodeMessages() {}

    @OpenApi(path = "/api/node/breakpoint/add", methods = HttpMethod.POST,
            summary = "Add a breakpoint to a node", operationId = "addNodeBreakpoint", tags = {"Node Debugging"},
            queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Breakpoint added.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "nodeId is invalid or node is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Breakpoint operation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void addBreakpoint() {}

    @OpenApi(path = "/api/node/breakpoint/remove", methods = HttpMethod.POST,
            summary = "Remove a node breakpoint", operationId = "removeNodeBreakpoint", tags = {"Node Debugging"},
            queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Breakpoint removed.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "nodeId is invalid or node is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Breakpoint operation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void removeBreakpoint() {}

    @OpenApi(path = "/api/node/breakpoint/resume", methods = HttpMethod.POST,
            summary = "Resume execution of a paused node", operationId = "resumeNode", tags = {"Node Debugging"},
            queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Paused node resumed.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "nodeId is invalid or node is not paused.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Resume operation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void resumeNode() {}

    @OpenApi(path = "/api/node/breakpoint/step", methods = HttpMethod.POST,
            summary = "Execute one step for a paused node", operationId = "stepNode", tags = {"Node Debugging"},
            queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "One execution step completed.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "nodeId is invalid or node is not paused.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Step operation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void stepNode() {}

    @OpenApi(path = "/api/node/breakpoint/message/{id}", methods = HttpMethod.GET,
            summary = "Get the message paused at a breakpoint", operationId = "getPausedMessage", tags = {"Node Debugging"},
            pathParams = @OpenApiParam(name = "id", required = true, description = "Node identifier whose paused message should be returned."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Paused runtime message. The message is a dynamic JSON object whose top-level keys are application-defined.", content = @OpenApiContent(from = Map.class)),
                    @OpenApiResponse(status = "404", description = "Node is not paused or no message is available.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Paused message could not be read.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void getPausedMessage() {}

    @OpenApi(path = "/api/connection/{id}", methods = HttpMethod.GET,
            summary = "Get connection runtime information", operationId = "getConnectionInfo", tags = {"Connection"},
            pathParams = @OpenApiParam(name = "id", required = true, description = "Unique connection identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Connection endpoints, enabled state, and injected message count.", content = @OpenApiContent(from = ConnectionInfo.class)),
                    @OpenApiResponse(status = "404", description = "Connection is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Connection state could not be read.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void getConnectionInfo() {}

    @OpenApi(path = "/api/connection/enable", methods = HttpMethod.POST,
            summary = "Enable a connection", operationId = "enableConnection", tags = {"Connection"},
            queryParams = @OpenApiParam(name = "connectionId", required = true, description = "Unique connection identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Connection enabled and routing refreshed.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "connectionId is invalid or connection is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Connection activation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void enableConnection() {}

    @OpenApi(path = "/api/connection/disable", methods = HttpMethod.POST,
            summary = "Disable a connection", operationId = "disableConnection", tags = {"Connection"},
            queryParams = @OpenApiParam(name = "connectionId", required = true, description = "Unique connection identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Connection disabled and routing refreshed.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "connectionId is invalid or connection is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Connection deactivation failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void disableConnection() {}

    @OpenApi(path = "/api/connection/inject", methods = HttpMethod.POST,
            summary = "Inject a runtime message into a connection", operationId = "injectMessage", tags = {"Connection"},
            queryParams = @OpenApiParam(name = "connectionId", required = true, description = "Unique connection identifier."),
            requestBody = @OpenApiRequestBody(description = "Dynamic runtime message. Every top-level JSON property becomes a message field; application-defined fields such as `topic` and `rawData` are allowed.", content = @OpenApiContent(from = Map.class), required = true),
            responses = {
                    @OpenApiResponse(status = "200", description = "Message accepted for execution through the selected connection.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "connectionId or message body is invalid.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "404", description = "Connection is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "409", description = "Workspace, flow, or connection is disabled; the message was not injected.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Message injection failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void injectMessage() {}

    @OpenApi(path = "/api/runtime/status", methods = HttpMethod.GET,
            summary = "Get runtime system status", operationId = "getRuntimeStatus", tags = {"Runtime"},
            responses = {
                    @OpenApiResponse(status = "200", description = "Current JVM/runtime resource status and uptime.", content = @OpenApiContent(from = SystemStatus.class)),
                    @OpenApiResponse(status = "500", description = "Runtime status could not be collected.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void getRuntimeStatus() {}

    @OpenApi(path = "/api/runtime/shutdown", methods = HttpMethod.POST,
            summary = "Request runtime shutdown", operationId = "shutdownRuntime", tags = {"Runtime"},
            responses = {
                    @OpenApiResponse(status = "200", description = "Shutdown sequence accepted. The runtime performs graceful shutdown asynchronously.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "500", description = "Shutdown could not be initiated.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void shutdownRuntime() {}

    @OpenApi(path = "/api/runtime/gc", methods = HttpMethod.POST,
            summary = "Request JVM garbage collection", operationId = "triggerGarbageCollection", tags = {"Runtime"},
            responses = {
                    @OpenApiResponse(status = "200", description = "Garbage collection request submitted to the JVM.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "500", description = "Garbage collection request failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void triggerGarbageCollection() {}

    @OpenApi(path = "/api/runtime/reload-plugins", methods = HttpMethod.POST,
            summary = "Reload runtime plugins", operationId = "reloadPlugins", tags = {"Runtime"},
            responses = {
                    @OpenApiResponse(status = "200", description = "Plugin reload requested.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "500", description = "Plugin reload failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void reloadPlugins() {}

    @OpenApi(path = "/api/runtime/metrics/reset/workspace", methods = HttpMethod.POST,
            summary = "Reset workspace metrics", operationId = "resetWorkspaceMetrics", tags = {"Runtime Monitoring"},
            queryParams = @OpenApiParam(name = "workspaceId", required = true, description = "Unique workspace identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Workspace metrics reset.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "workspaceId is invalid or workspace is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Metrics reset failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void resetWorkspaceMetrics() {}

    @OpenApi(path = "/api/runtime/metrics/reset/node", methods = HttpMethod.POST,
            summary = "Reset node metrics", operationId = "resetNodeMetrics", tags = {"Runtime Monitoring"},
            queryParams = @OpenApiParam(name = "nodeId", required = true, description = "Unique node identifier."),
            responses = {
                    @OpenApiResponse(status = "200", description = "Node metrics reset.", content = @OpenApiContent(from = ActionResponse.class)),
                    @OpenApiResponse(status = "400", description = "nodeId is invalid or node is not deployed.", content = @OpenApiContent(from = ErrorResponse.class)),
                    @OpenApiResponse(status = "500", description = "Metrics reset failed.", content = @OpenApiContent(from = ErrorResponse.class))
            })
    static void resetNodeMetrics() {}
}
