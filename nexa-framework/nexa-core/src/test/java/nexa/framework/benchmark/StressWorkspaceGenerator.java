package nexa.framework.benchmark;

import nexa.framework.runtime.domain.workspace.model.ConnectionDefinition;
import nexa.framework.runtime.domain.workspace.model.FlowDefinition;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import nexa.framework.runtime.domain.workspace.model.NodeDefinition;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StressWorkspaceGenerator {

        private StressWorkspaceGenerator() {
        }

        public static WorkspaceDefinition parallelManualWorkspace(String workspaceId, String flowId, int branches) {
                List<NodeDefinition> nodes = new ArrayList<>();
                List<ConnectionDefinition> connections = new ArrayList<>();

                nodes.add(new NodeDefinition(
                                "input-manual",
                                NodeCategory.INPUT,
                                "manual-input",
                                null,
                                true,
                                null,
                                Map.of()));

                String fanoutScript = fanoutScript(branches);
                nodes.add(new NodeDefinition(
                                "exec-fanout",
                                NodeCategory.EXECUTOR,
                                "script",
                                "nexa",
                                true,
                                new InputExecutionPolicyDefinition(null),
                                Map.of("script", fanoutScript)));

                connections.add(new ConnectionDefinition(
                                UUID.randomUUID().toString(), "input-manual", "default", "exec-fanout"));

                for (int index = 0; index < branches; index++) {
                        String port = "p" + index;
                        String execId = "exec-" + index;
                        String outId = "out-" + index;

                        Map<String, Object> execConfig = new LinkedHashMap<>();
                        execConfig.put("script",
                                        "msg.payload = { branch: \"" + port
                                                        + "\" }\nsend(\"default\", msg)");

                        nodes.add(new NodeDefinition(
                                        execId,
                                        NodeCategory.EXECUTOR,
                                        "script",
                                        "nexa",
                                        true,
                                        new InputExecutionPolicyDefinition(null),
                                        execConfig));

                        nodes.add(new NodeDefinition(
                                        outId,
                                        NodeCategory.OUTPUT,
                                        "debug-output",
                                        null,
                                        true,
                                        new InputExecutionPolicyDefinition(null),
                                        Map.of()));

                        connections.add(new ConnectionDefinition(
                                        UUID.randomUUID().toString(), "exec-fanout", port, execId));
                        connections.add(new ConnectionDefinition(
                                        UUID.randomUUID().toString(), execId, "default", outId));
                }

                FlowDefinition flow = new FlowDefinition(flowId, flowId, true, nodes, connections);
                return new WorkspaceDefinition(workspaceId, true, List.of(flow));
        }

        public static WorkspaceDefinition timedWorkspace(String workspaceId, String flowId, String intervalValue) {
                List<NodeDefinition> nodes = List.of(
                                new NodeDefinition(
                                                "input-timed",
                                                NodeCategory.INPUT,
                                                "timed-trigger",
                                                null,
                                                true,
                                                new InputExecutionPolicyDefinition(64),
                                                Map.of(
                                                                "interval", intervalValue,
                                                                "payload", Map.of("source", "stress-timer"))),
                                new NodeDefinition(
                                                "exec-main",
                                                NodeCategory.EXECUTOR,
                                                "script",
                                                "nexa",
                                                true,
                                                new InputExecutionPolicyDefinition(null),
                                                Map.of("script",
                                                                "val current = msg.payload?.count ?? 0\n"
                                                                                + "val next = current + 1\n"
                                                                                + "msg.payload.count = next\n"
                                                                                + "send(\"default\", msg)")),
                                new NodeDefinition(
                                                "out-main",
                                                NodeCategory.OUTPUT,
                                                "debug-output",
                                                null,
                                                true,
                                                new InputExecutionPolicyDefinition(null),
                                                Map.of()));

                List<ConnectionDefinition> connections = List.of(
                                new ConnectionDefinition(
                                                UUID.randomUUID().toString(), "input-timed", "default", "exec-main"),
                                new ConnectionDefinition(
                                                UUID.randomUUID().toString(), "exec-main", "default", "out-main"));

                FlowDefinition flow = new FlowDefinition(flowId, flowId, true, nodes, connections);
                return new WorkspaceDefinition(workspaceId, true, List.of(flow));
        }

        public static WorkspaceDefinition largeWorkspaceForIncrementalDeploy(String workspaceId, int flowCount,
                        int changedFlowIndex) {
                List<FlowDefinition> flows = new ArrayList<>();
                for (int index = 0; index < flowCount; index++) {
                        flows.add(singlePathFlow("flow-" + index, index == changedFlowIndex));
                }
                return new WorkspaceDefinition(workspaceId, true, flows);
        }

        private static FlowDefinition singlePathFlow(String flowId, boolean changed) {
                String inputId = flowId + "-input";
                String execId = flowId + "-exec";
                String outId = flowId + "-out";

                String script = changed
                                ? "msg.payload = { version: 2 }\nsend(\"default\", msg)"
                                : "msg.payload = { version: 1 }\nsend(\"default\", msg)";

                List<NodeDefinition> nodes = List.of(
                                new NodeDefinition(
                                                inputId,
                                                NodeCategory.INPUT,
                                                "manual-input",
                                                null,
                                                true,
                                                new InputExecutionPolicyDefinition(1024),
                                                Map.of()),
                                new NodeDefinition(
                                                execId,
                                                NodeCategory.EXECUTOR,
                                                "script",
                                                "nexa",
                                                true,
                                                new InputExecutionPolicyDefinition(null),
                                                Map.of("script", script)),
                                new NodeDefinition(
                                                outId,
                                                NodeCategory.OUTPUT,
                                                "debug-output",
                                                null,
                                                true,
                                                new InputExecutionPolicyDefinition(null),
                                                Map.of()));

                List<ConnectionDefinition> connections = List.of(
                                new ConnectionDefinition(
                                                UUID.randomUUID().toString(), inputId, "default", execId),
                                new ConnectionDefinition(
                                                UUID.randomUUID().toString(), execId, "default", outId));

                return new FlowDefinition(flowId, flowId, true, nodes, connections);
        }

        private static String fanoutScript(int branches) {
                StringBuilder builder = new StringBuilder();
                builder.append("send([");

                for (int index = 0; index < branches; index++) {
                        if (index > 0) {
                                builder.append(", ");
                        }
                        builder.append("\"").append("p").append(index).append("\"");
                }

                builder.append("], msg)");
                return builder.toString();
        }
}
