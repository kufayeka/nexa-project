# Nexa Workspace Compilation (Phase 1)

Phase 1 establishes the control-plane/runtime boundary for compiled workspaces.

## Workspaces

- **Flow Workspace** contains flows, nodes, connections and resources.
- **Asset Workspace** contains assets, attributes, templates and calculation scripts.

Both are compiled before runtime start. Plugins expose symbols through `WorkspaceCompilationContext`; the compiler does not depend on MQTT, Control or Asset Manager implementations.

## Compiled artifacts

`CompiledFlowWorkspace` and `CompiledAssetWorkspace` are immutable runtime artifacts. Each artifact carries compiled scripts, resolved symbols and a `NexaBytecodeProgram` slot reserved for the bytecode backend.

The Phase-1 bytecode model is deliberately small and explicit. It is the contract for the next optimization phase; the existing Nexa compiler remains the source-to-compiled-script implementation until the bytecode lowering pass lands.

## Runtime rule

Source parsing belongs to compilation/deployment. Runtime code should consume compiled artifacts. JIT/native execution is a later phase and is not required to change plugin boundaries.
