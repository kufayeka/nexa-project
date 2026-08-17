package nexa.framework.runtime.domain.deployment.model;

import nexa.framework.runtime.api.NexaCompiledNode;
import nexa.framework.runtime.api.NexaExecutionContext;
import nexa.framework.runtime.api.model.RuntimeMessage;
import nexa.framework.runtime.domain.workspace.model.InputExecutionPolicyDefinition;
import nexa.framework.runtime.domain.workspace.model.NodeCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledFlowTest {

    @Test
    void nodeEnableToggleMustPreserveAotExecutable() {
        NexaCompiledNode executable = new NexaCompiledNode() {
            @Override
            public void execute(RuntimeMessage msg, NexaExecutionContext context) {
                context.send(msg);
            }
        };

        CompiledNode node = new CompiledNode(
                "script",
                NodeCategory.EXECUTOR,
                "nexa-script",
                true,
                new InputExecutionPolicyDefinition(1),
                Map.of(),
                "nexa",
                executable);

        CompiledFlow flow = new CompiledFlow(
                "main",
                "main",
                true,
                Map.of("script", node),
                Map.of("script", Map.of("default", List.of())),
                Map.of());

        flow.setNodeEnabled("script", false);

        CompiledNode updated = flow.node("script");
        assertTrue(!updated.enabled());
        assertSame(executable, updated.executableNode());
    }
}
