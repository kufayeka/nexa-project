## Role

You are contributing to Nexa Framework.

Nexa is an industrial automation runtime built with Java 25.

Nexa aims to replicate the execution behavior of Node-RED while being implemented as a modern, maintainable, production-grade Java runtime.

Do not copy Node-RED's implementation details.

Understand its behavior and design an implementation that fits Java and Nexa's engineering principles.

Always generate production-grade code.

---

## Primary Goal

Prioritize:

- Readability
- Maintainability
- Explicitness
- Predictability

Code will be maintained for years by multiple developers.

Always optimize for humans reading the code.

Never optimize for writing fewer lines.

---

## Coding Style

Write code that looks like it was written by an experienced software engineer.

Avoid code that feels AI-generated.

Prefer explicit logic over clever tricks.

Avoid unnecessary abstractions.

Avoid creating helper methods unless they improve readability.

AVOID GOD SERVICE! limit code maksmimum +-400 lines per service. diatas itu split menjadi beberapa service yang lebih kecil dan fokus pada satu tanggung jawab per service.

Keep classes cohesive.

Avoid God Objects.

Avoid large utility classes containing unrelated logic.

---

## Business Flow

Business flow must be readable from top to bottom.

Developers should understand the behavior without constantly jumping between files.

Readable flow is more important than maximum reusability.

---

## Method Extraction

Do NOT extract methods only to:

- move 1–3 lines
- reduce duplicate code
- satisfy theoretical clean code rules

Extract methods only when it improves readability or represents a clear business action.

---

## Duplicate Code

Do not aggressively remove small duplicate code.

If duplicated code is fewer than approximately five lines and remains easy to read, leave it.

Readability is more important than DRY.

---

## Naming

Method names must describe business intent.

Prefer:

executeFlow()

instead of

run()

Prefer:

loadFlowDefinition()

instead of

load()

Avoid abbreviations unless widely accepted.

---

## Ternary

Use ternary only for simple expressions.

Never use nested ternary.

Use if statements instead.

---

## Typing

Prefer explicit types.

Avoid Object when a concrete type exists.

Use Generic only when it improves type safety.

---

## Comments

Write comments only for:

- business rules
- design decisions
- runtime constraints

Do not explain obvious Java syntax.

All comments must be written in Indonesian.

---

## Dead Code

Never generate unused code.

Do not create placeholder methods.

Do not leave TODO implementations unless requested.

---

## Error Handling

Fail explicitly.

Never silently swallow exceptions.

Never ignore exceptions.

---

## Production

Every generated code is assumed to be production code.

Avoid shortcuts made only for examples.

---

## AI Behavior

If multiple implementations are possible:

Choose the one that is:

- easiest to debug
- easiest to maintain
- easiest to understand