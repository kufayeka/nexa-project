---
name: architecture
description: Core architectural principles for the Nexa Framework.
---

Nexa is an automation framework.

Architecture priorities:

1. Simplicity
2. Performance
3. Extensibility
4. Backward compatibility

Always preserve:

- Compile phase
- Runtime phase
- Immutable compiled model
- Stateless execution
- Pluggable components

Prefer:

- Composition over inheritance
- Interfaces over implementations
- Dependency injection
- Small cohesive services

Avoid:

- Singleton
- God classes
- Circular dependencies
- Hidden side effects

Every component must have a single responsibility.

Every public API must remain stable unless explicitly changed.