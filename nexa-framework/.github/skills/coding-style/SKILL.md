---
name: coding-style
description: Java coding conventions used throughout Nexa.
---

General:

- Use Java 25 features whenever appropriate.
- Prefer final variables.
- Prefer records for immutable interfaces/DTOs.
- Prefer enums over String constants.
- Prefer Optional only as return values.

Naming:

- Classes: PascalCase
- Methods: camelCase
- Constants: UPPER_SNAKE_CASE
- Packages: lowercase

Method Rules:

- Keep methods under 50 lines whenever practical.
- One level of abstraction per method.
- Early return preferred.
- Avoid nested if statements.

Classes:

- One primary responsibility.
- Constructor injection only.
- Avoid static mutable state.

Collections:

- Prefer List over Collection unless abstraction is required.
- Prefer Map.copyOf() and List.copyOf().
- Return immutable collections.

Exceptions:

- Never swallow exceptions.
- Throw domain-specific exceptions.
- Wrap external exceptions.

Logging:

- Use parameterized logging.
- Never concatenate log messages.

Comments:

- Explain WHY.
- Do not explain WHAT.

Formatting:

- Maximum line length: 120
- Blank line between logical blocks.
- No wildcard imports.