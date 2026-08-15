package nexa.framework.runtime.domain.execution.api;

import nexa.framework.runtime.domain.execution.model.WorkspaceRuntime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InputActivator adalah antarmuka inversi dependensi (DIP) untuk mengaktifkan
 * dan menonaktifkan input node dari domain execution tanpa membuat cyclic dependency ke domain scheduler.
 */
public interface InputActivator {

    void activateWorkspaceInputs(WorkspaceRuntime workspaceRuntime, AtomicBoolean runtimeStarted);

    void activateInputNode(WorkspaceRuntime workspaceRuntime, String flowId, String nodeId, AtomicBoolean runtimeStarted);

    void stopWorkspaceRuntime(WorkspaceRuntime workspaceRuntime);
}
