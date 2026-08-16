# Nexa Compiled Runtime Rebuild

## Goal

Build Nexa as a compiled automation runtime with predictable low latency, strong type safety, safe plugin boundaries, and a deployment model that executes compiled JVM bytecode rather than interpreting Nexa source at runtime.

## Target execution model

```text
Nexa source / workspace configuration
        |
        v
Lexer -> Parser -> Typed AST -> Semantic analysis
        |
        v
      Nexa IR
        |
        v
    IR optimizer
        |
        v
   JVM bytecode backend
        |
        v
Compiled workspace artifact (.jar/container of .class files)
        |
        v
      JVM HotSpot
        |
        v
 native machine code (JIT)
```

The JAR is a deployment artifact. JVM class bytecode is the execution representation.

## Phase 0 - Architecture and contracts

- Define compiler/runtime/plugin boundaries.
- Separate control plane from the hot data plane.
- Define immutable compiled workspace artifacts.
- Define host capabilities so plugins remain implementation-isolated.
- Define deployment validation and atomic workspace replacement.
- Establish that runtime never parses or interprets Nexa source during normal execution.

## Phase 1 - Nexa language and type system

Canonical scalar types:

- BOOLEAN
- INT8 / INT16 / INT32 / INT64
- UINT8 / UINT16 / UINT32 / UINT64
- FLOAT32 / FLOAT64
- STRING
- ARRAY<T>
- OBJECT
- user-defined structural object types

Required language features:

- typed `let` declarations
- typed function signatures
- structural object types
- generic arrays
- field access
- loops
- conditionals
- arithmetic and boolean operators
- explicit/defined numeric conversion rules
- `self`, `oldValue`, `newValue`, timestamp and quality execution context
- compile-time validation of tag names, types and host capability signatures

## Phase 2 - Typed Nexa IR

The IR is the stable compiler boundary between the language frontend and execution backend.

The initial IR should represent typed operations such as:

- constants
- local load/store
- tag load/store
- self load/store
- field load/store
- array operations
- arithmetic/comparison
- branches/loops
- function calls
- host capability calls
- return

IR must preserve static type information and avoid Object-based operations on primitive hot paths.

## Phase 3 - JVM bytecode compiler

- Generate JVM `.class` bytes directly; do not generate Java source.
- Keep generated code deterministic and inspectable.
- Map primitive Nexa operations to JVM primitive instructions.
- Keep host/plugin calls behind stable runtime interfaces.
- Add bytecode verification tests.

## Phase 4 - Compiled workspace artifact

A workspace is compiled before deployment and packaged as an immutable artifact.

Artifact should contain:

- workspace identity/version
- compiler version
- target/runtime compatibility
- compiled class bytes
- symbol/type metadata
- dependency metadata
- diagnostics/build information

Deployment flow:

```text
source/config -> compile -> validate -> package -> stage -> atomic activate
```

A failed compilation must never replace the currently active workspace.

## Phase 5 - Asset TagStore and dependency engine

Hot-path tag storage must be typed and slot-based rather than `Map<String, Object>` lookups.

The asset compiler resolves tag references to stable slot metadata at compile time where possible.

Dependency graph propagation should execute only affected tags instead of scanning every tag on every cycle.

## Phase 6 - Runtime scheduler

Execution lanes:

- high-speed deterministic lane for short non-blocking calculations
- normal automation lane
- background/low-priority lane
- event-driven lane

High-speed scripts must not perform blocking I/O or unbounded host calls.

The scheduler must track execution deadlines, failures and missed cycles.

## Phase 7 - Plugin Host API

Plugins remain independent modules. Nexa interacts with them through registered host capabilities/signatures rather than depending on plugin implementations.

Examples:

```text
mqtt.publish(STRING, OBJECT) -> VOID
control.read(STRING) -> typed value
control.write(STRING, typed value) -> VOID
asset.read(STRING) -> typed value
asset.write(STRING, typed value) -> VOID
```

The compiler validates capability names and signatures before deployment.

## Phase 8 - JIT/runtime optimization

Measure before optimizing. Focus on:

- allocation rate
- boxing
- tag lookup overhead
- dependency propagation
- scheduler overhead
- host-call overhead
- JIT warmup
- GC pauses

Avoid custom VM machinery unless benchmarks demonstrate a concrete JVM limitation.

## Phase 9 - Hardcore benchmark

Measure end-to-end latency and throughput for:

- 1k / 10k / 50k tags
- 100ms / 50ms / 20ms cycles
- primitive calculations
- multi-tag calculations
- dependency fan-out/fan-in
- JSON/object-heavy workloads
- mixed workloads

Report p50, p95, p99 and p99.9 latency, CPU, allocation, GC pauses, missed deadlines and throughput.

## Phase 10 - Production hardening

- execution budgets
- fault isolation
- watchdogs
- deployment rollback
- artifact compatibility checks
- security restrictions on generated code
- diagnostics/observability
- deterministic lifecycle behavior
- backward-compatible plugin contracts
