package nexa.framework;

import nexa.framework.runtime.api.OutputConsumer;
import nexa.framework.runtime.api.RuntimeConfiguration;
import nexa.framework.runtime.api.RuntimeEngine;
import nexa.framework.runtime.domain.deployment.exception.ValidationException;
import nexa.framework.runtime.domain.execution.service.DefaultRuntimeEngine;
import nexa.framework.runtime.domain.workspace.service.WorkspaceJsonLoader;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.statistics.model.RuntimeStatisticsSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @Test
    void branchExecutionMustIsolateMessageState() throws Exception {
        List<Map<String, Object>> outputs = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            outputs.add(message.deepCopy().values());
            latch.countDown();
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        String workspaceJson = """
                {
                    "id": "ws-branch",
                    "enabled": true,
                    "flows": [
                        {
                            "id": "flow-branch",
                            "enabled": true,
                            "nodes": [
                                {
                                    "id": "input-1",
                                    "category": "INPUT",
                                    "type": "manual-input",
                                    "enabled": true,
                                    "inputPolicy": { "maxConcurrentExecutions": 10 }
                                },
                                {
                                    "id": "fanout",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "msg.payload = { value: 1 }\\nsend([\\"success\\", \\"audit\\"], msg)"
                                    }
                                },
                                {
                                    "id": "success-node",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "val value = msg.payload.value\\nmsg.payload.branch = \\"success\\"\\nmsg.payload.value = value + 1\\nsend(\\"default\\", msg)"
                                    }
                                },
                                {
                                    "id": "audit-node",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "val value = msg.payload.value\\nmsg.payload.branch = \\"audit\\"\\nmsg.payload.value = value + 2\\nsend(\\"default\\", msg)"
                                    }
                                },
                                {
                                    "id": "sink-1",
                                    "category": "OUTPUT",
                                    "type": "debug-output",
                                    "enabled": true
                                }
                            ],
                            "connections": [
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "fanout" },
                                { "sourceNodeId": "fanout", "sourcePort": "success", "targetNodeId": "success-node" },
                                { "sourceNodeId": "fanout", "sourcePort": "audit", "targetNodeId": "audit-node" },
                                { "sourceNodeId": "success-node", "sourcePort": "default", "targetNodeId": "sink-1" },
                                { "sourceNodeId": "audit-node", "sourcePort": "default", "targetNodeId": "sink-1" }
                            ]
                        }
                    ]
                }
                """;

        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        runtime.deploy(loader.fromJson(workspaceJson));
        runtime.startRuntime();

        runtime.trigger("ws-branch", "flow-branch", "input-1", new RuntimeMessage());

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(2, outputs.size());

        boolean hasSuccess = outputs.stream().anyMatch(entry -> {
            Map<String, Object> payload = payloadMap(entry);
            return "success".equals(payload.get("branch")) && ((Number) payload.get("value")).intValue() == 2;
        });

        boolean hasAudit = outputs.stream().anyMatch(entry -> {
            Map<String, Object> payload = payloadMap(entry);
            return "audit".equals(payload.get("branch")) && ((Number) payload.get("value")).intValue() == 3;
        });

        assertTrue(hasSuccess);
        assertTrue(hasAudit);

        runtime.stopRuntime();
    }

    @Test
    void objectLiteralAssignmentMustBuildNestedMessageState() throws Exception {
        List<Map<String, Object>> outputs = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            outputs.add(message.deepCopy().values());
            latch.countDown();
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        String workspaceJson = """
                {
                    "id": "ws-autocreate",
                    "enabled": true,
                    "flows": [
                        {
                            "id": "flow-autocreate",
                            "enabled": true,
                            "nodes": [
                                {
                                    "id": "input-1",
                                    "category": "INPUT",
                                    "type": "manual-input",
                                    "enabled": true,
                                    "inputPolicy": { "maxConcurrentExecutions": 10 }
                                },
                                {
                                    "id": "executor-1",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "msg.payload = { timestamp: 123 }\\nmsg.user = { profile: { name: \\"Yeka\\" } }\\nsend(\\"default\\", msg)"
                                    }
                                },
                                {
                                    "id": "sink-1",
                                    "category": "OUTPUT",
                                    "type": "debug-output",
                                    "enabled": true
                                }
                            ],
                            "connections": [
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "executor-1" },
                                { "sourceNodeId": "executor-1", "sourcePort": "default", "targetNodeId": "sink-1" }
                            ]
                        }
                    ]
                }
                """;

        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        runtime.deploy(loader.fromJson(workspaceJson));
        runtime.startRuntime();

        runtime.trigger("ws-autocreate", "flow-autocreate", "input-1", new RuntimeMessage());

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(1, outputs.size());

        Map<String, Object> entry = outputs.getFirst();
        Map<String, Object> payload = payloadMap(entry);
        Map<String, Object> user = objectMap(entry.get("user"), "user");
        Map<String, Object> profile = objectMap(user.get("profile"), "user.profile");

        assertEquals(123, ((Number) payload.get("timestamp")).intValue());
        assertEquals("Yeka", profile.get("name"));

        runtime.stopRuntime();
    }

    @Test
    void nexaExecutorMustSupportExpressionsAndNestedObjectWrites() throws Exception {
        List<Map<String, Object>> outputs = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            outputs.add(message.deepCopy().values());
            latch.countDown();
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        String workspaceJson = """
                {
                    "id": "ws-nexa-script",
                    "enabled": true,
                    "flows": [
                        {
                            "id": "flow-nexa-script",
                            "enabled": true,
                            "nodes": [
                                {
                                    "id": "input-1",
                                    "category": "INPUT",
                                    "type": "manual-input",
                                    "enabled": true,
                                    "inputPolicy": { "maxConcurrentExecutions": 10 }
                                },
                                {
                                    "id": "executor-1",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "val rpm = msg.payload.rpm\\nval counter = (msg.payload.counter ?? 0) + 1\\nmsg.payload.power = rpm * 3\\nmsg.payload.counter = counter\\nmsg.payload.user = { name: \\"Yeka\\" }\\nsend(msg)"
                                    }
                                },
                                {
                                    "id": "sink-1",
                                    "category": "OUTPUT",
                                    "type": "debug-output",
                                    "enabled": true
                                }
                            ],
                            "connections": [
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "executor-1" },
                                { "sourceNodeId": "executor-1", "sourcePort": "default", "targetNodeId": "sink-1" }
                            ]
                        }
                    ]
                }
                """;

        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        runtime.deploy(loader.fromJson(workspaceJson));
        runtime.startRuntime();

        runtime.trigger("ws-nexa-script", "flow-nexa-script", "input-1",
                new RuntimeMessage(Map.of("payload", Map.of("rpm", 1500))));

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(1, outputs.size());

        Map<String, Object> first = outputs.getFirst();
        Map<String, Object> firstPayload = payloadMap(first);
        Map<String, Object> firstUser = objectMap(firstPayload.get("user"), "payload.user");
        assertEquals(1500, ((Number) firstPayload.get("rpm")).intValue());
        assertEquals(4500, ((Number) firstPayload.get("power")).intValue());
        assertEquals(1, ((Number) firstPayload.get("counter")).intValue());
        assertEquals("Yeka", firstUser.get("name"));

        runtime.stopRuntime();
    }

    @Test
    void multipleNexaExecutorsMustRunInOneFlow() throws Exception {
        List<Map<String, Object>> outputs = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            outputs.add(message.deepCopy().values());
            latch.countDown();
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        String workspaceJson = """
                {
                    "id": "ws-multi-nexa",
                    "enabled": true,
                    "flows": [
                        {
                            "id": "flow-multi-nexa",
                            "enabled": true,
                            "nodes": [
                                {
                                    "id": "input-1",
                                    "category": "INPUT",
                                    "type": "manual-input",
                                    "enabled": true,
                                    "inputPolicy": { "maxConcurrentExecutions": 10 }
                                },
                                {
                                    "id": "executor-a",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "msg.payload = { branch: \\"nexa-a\\" }\\nsend(msg)"
                                    }
                                },
                                {
                                    "id": "executor-b",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "msg.payload = { branch: \\"nexa-b\\" }\\nsend(msg)"
                                    }
                                },
                                {
                                    "id": "sink-a",
                                    "category": "OUTPUT",
                                    "type": "debug-output",
                                    "enabled": true
                                },
                                {
                                    "id": "sink-b",
                                    "category": "OUTPUT",
                                    "type": "debug-output",
                                    "enabled": true
                                }
                            ],
                            "connections": [
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "executor-a" },
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "executor-b" },
                                { "sourceNodeId": "executor-a", "sourcePort": "default", "targetNodeId": "sink-a" },
                                { "sourceNodeId": "executor-b", "sourcePort": "default", "targetNodeId": "sink-b" }
                            ]
                        }
                    ]
                }
                """;

        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        runtime.deploy(loader.fromJson(workspaceJson));
        runtime.startRuntime();

        runtime.trigger("ws-multi-nexa", "flow-multi-nexa", "input-1", new RuntimeMessage());

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(2, outputs.size());

        boolean hasExecutorA = outputs.stream().anyMatch(entry -> {
            Map<String, Object> payload = payloadMap(entry);
            return "nexa-a".equals(payload.get("branch"));
        });

        boolean hasExecutorB = outputs.stream().anyMatch(entry -> {
            Map<String, Object> payload = payloadMap(entry);
            return "nexa-b".equals(payload.get("branch"));
        });

        assertTrue(hasExecutorA);
        assertTrue(hasExecutorB);

        runtime.stopRuntime();
    }

    @Test
    void inputPolicyMustRejectWhenConcurrentLimitReached() throws Exception {
        CountDownLatch firstExecutionArrived = new CountDownLatch(1);
        CountDownLatch releaseOutput = new CountDownLatch(1);

        OutputConsumer outputConsumer = (context, nodeId, message) -> {
            firstExecutionArrived.countDown();
            try {
                releaseOutput.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                outputConsumer);

        String workspaceJson = """
                {
                    "id": "ws-policy",
                    "enabled": true,
                    "flows": [
                        {
                            "id": "flow-policy",
                            "enabled": true,
                            "nodes": [
                                {
                                    "id": "input-1",
                                    "category": "INPUT",
                                    "type": "manual-input",
                                    "enabled": true,
                                    "inputPolicy": { "maxConcurrentExecutions": 1 }
                                },
                                {
                                    "id": "executor-1",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "send(\\"default\\", msg)"
                                    }
                                },
                                {
                                    "id": "sink-1",
                                    "category": "OUTPUT",
                                    "type": "debug-output",
                                    "enabled": true
                                }
                            ],
                            "connections": [
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "executor-1" },
                                { "sourceNodeId": "executor-1", "sourcePort": "default", "targetNodeId": "sink-1" }
                            ]
                        }
                    ]
                }
                """;

        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        runtime.deploy(loader.fromJson(workspaceJson));
        runtime.startRuntime();

        runtime.trigger("ws-policy", "flow-policy", "input-1", new RuntimeMessage());
        assertTrue(firstExecutionArrived.await(2, TimeUnit.SECONDS));

        runtime.trigger("ws-policy", "flow-policy", "input-1", new RuntimeMessage());

        RuntimeStatisticsSnapshot stats = runtime.statistics("ws-policy", "flow-policy");
        assertTrue(stats.rejected() >= 1);

        releaseOutput.countDown();
        runtime.stopRuntime();
    }

    @Test
    void invalidNexaMustFailDuringDeploy() {
        RuntimeEngine runtime = new DefaultRuntimeEngine(
                new RuntimeConfiguration(Duration.ofSeconds(5)),
                (context, nodeId, message) -> {
                });

        String workspaceJson = """
                {
                    "id": "ws-invalid-nexa",
                    "enabled": true,
                    "flows": [
                        {
                            "id": "flow-invalid-nexa",
                            "enabled": true,
                            "nodes": [
                                {
                                    "id": "input-1",
                                    "category": "INPUT",
                                    "type": "timed-trigger",
                                    "enabled": true,
                                    "inputPolicy": { "maxConcurrentExecutions": 1 },
                                    "config": { "interval": "1s" }
                                },
                                {
                                    "id": "exec-1",
                                    "category": "EXECUTOR",
                                    "type": "script",
                                    "language": "nexa",
                                    "enabled": true,
                                    "config": {
                                        "script": "val broken ="
                                    }
                                }
                            ],
                            "connections": [
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "exec-1" }
                            ]
                        }
                    ]
                }
                """;

        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();

        assertThrows(ValidationException.class,
                () -> runtime.deploy(loader.fromJson(workspaceJson)));
    }

    private Map<String, Object> payloadMap(Map<String, Object> entry) {
        Object payloadRaw = entry.get("payload");
        if (!(payloadRaw instanceof Map<?, ?> payloadMap)) {
            throw new IllegalStateException("payload must be object");
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> mapEntry : payloadMap.entrySet()) {
            payload.put(String.valueOf(mapEntry.getKey()), mapEntry.getValue());
        }

        return payload;
    }

    private Map<String, Object> objectMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> objectMap)) {
            throw new IllegalStateException(path + " must be object");
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : objectMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        return result;
    }
}

