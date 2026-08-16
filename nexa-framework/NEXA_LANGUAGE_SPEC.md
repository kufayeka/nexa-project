# Nexa Language — Workstream 1

## Primitive types

`BOOLEAN`, `INT8`, `INT16`, `INT32`, `INT64`, `UINT8`, `UINT16`, `UINT32`, `UINT64`, `FLOAT32`, `FLOAT64`, `STRING`, `OBJECT`.

## Generic/container types

`ARRAY<T>` supports nested element types, including `ARRAY<OBJECT>` and `ARRAY<{ ... }>`.

## Structural types

```nexa
type ProductionOrder = {
    id: STRING,
    machine: { id: STRING, speed: INT32 },
    materials: ARRAY<{ code: STRING, quantity: FLOAT64, unit: STRING }>
};
```

## Variables

Declarations are explicit:

```nexa
let speed: INT32 = 100;
let enabled: BOOLEAN = true;
let name: STRING = "motor";
let values: ARRAY<FLOAT64> = [1.0, 2.0];
```

## Expressions

Arithmetic, comparison, boolean operators, field access, indexing, object/array literals, calls, unary operators, assignment and return are part of the frontend grammar.

## Dynamic OBJECT

`OBJECT` is the intentional dynamic JSON/object escape hatch. A typed object value can be assigned to `OBJECT`, and dynamic field/index access is permitted. Primitive types do not implicitly convert through `OBJECT`.

## Numeric safety

Implicit numeric conversion is widening-only. Numeric literals are range checked against their declared destination type, so `UINT8 = 256` and `INT8 = 128` are compile-time errors.

## Compilation contract

Workstream 1 ends at a validated, typed AST. Runtime execution must not interpret this AST. Workstream 2 consumes the typed representation and lowers it into Nexa IR.
