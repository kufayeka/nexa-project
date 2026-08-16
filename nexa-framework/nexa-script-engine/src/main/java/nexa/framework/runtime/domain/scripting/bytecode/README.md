# Nexa Bytecode

Phase 2 introduces the first executable Nexa bytecode pipeline:

```text
Nexa source -> tokenizer -> parser/AST -> bytecode -> NexaBytecodeVm
```

The AST is used only during compilation. The resulting `NexaBytecodeProgram` contains immutable instructions/constants and can be executed without source parsing.

`NexaBytecodeExecutionContext` is the plugin boundary. Asset Manager, MQTT, Control, and other plugins remain host implementations and are exposed to bytecode through named host calls rather than direct compiler dependencies.

This phase intentionally does not replace the existing `NexaRuntime` integration yet. The next integration step is to make `NexaCompiledScript` hold the bytecode artifact and route runtime bindings (including `self`, asset slots, message context, and host functions) into the VM. After that, workspace compilers can persist the executable bytecode artifact directly.
