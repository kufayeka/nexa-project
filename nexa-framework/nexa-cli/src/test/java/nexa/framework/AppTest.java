package nexa.framework;

import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import nexa.framework.runtime.domain.workspace.service.WorkspaceJsonLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @Test
    void sanityModelTest() {
        String workspaceJson = """
                {
                    "id": "ws-sanity",
                    "enabled": true,
                    "flows": [
                        {
                            "id": "flow-sanity",
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
                                    "id": "sink-1",
                                    "category": "OUTPUT",
                                    "type": "debug-output",
                                    "enabled": true
                                }
                            ],
                            "connections": [
                                { "sourceNodeId": "input-1", "sourcePort": "default", "targetNodeId": "sink-1" }
                            ]
                        }
                    ]
                }
                """;

        WorkspaceJsonLoader loader = new WorkspaceJsonLoader();
        WorkspaceDefinition def = loader.fromJson(workspaceJson);
        assertNotNull(def);
        assertEquals("ws-sanity", def.id());
    }
}

