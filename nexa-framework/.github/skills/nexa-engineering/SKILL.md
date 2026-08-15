---
name: nexa-engineering
description: Engineering principles and coding standards for Nexa Framework. Apply these rules whenever generating, reviewing, or refactoring code.
---

# Nexa Engineering Principles

Nexa is an industrial automation low code platform built with Java.
Nexa aims to replicate the execution behavior of Node-RED while being implemented as a modern, maintainable, production-grade Java runtime.

Always generate production-grade code.

The implementation may evolve over time.

Do not assume a specific architecture, implementation detail, framework, runtime model, or technology unless it already exists in the project.

Focus on engineering quality rather than implementation patterns.

---

# Engineering Mindset

Write code as if another engineer will debug it at 03:00 AM on a production industrial system.

Every design decision should make debugging easier.

Optimize for humans reading the code.

Code is read far more often than it is written.

---

# Primary Goals

Always prioritize:

1. Readability
2. Maintainability
3. Explicitness
4. Predictability
5. Simplicity

Readable code is more valuable than clever code.

---

# Coding Style

Write code like an experienced software engineer.

Avoid code that feels AI-generated.

Prefer explicit logic over clever tricks.

Prefer understandable code over short code.

Avoid unnecessary abstraction.

Avoid hidden behavior.

Avoid magical code.

---

# Readability

Business flow should be understandable from top to bottom.

A developer should not constantly jump across files just to understand one process.

Readable business flow is more important than maximum reusability.

---

# SOLID

Apply SOLID pragmatically.

Do not introduce interfaces, inheritance, factories, strategies, or abstractions unless they solve an existing problem.

Do not design for hypothetical future requirements.

---

# Clean Architecture

Apply Clean Architecture pragmatically.

Keep dependencies pointing toward the business core.

Avoid unnecessary layers.

Every layer must have a clear responsibility.

---

# Separation of Concerns

Each class should have one clear responsibility.

Each package should represent one domain.

Do not mix business logic with infrastructure concerns.

---

# Module Boundary

Expose only stable contracts.

Hide implementation details whenever practical.

Reduce coupling between modules.

---

# KISS

Choose the simplest solution that satisfies the current requirement.

Avoid unnecessary complexity.

---

# YAGNI

Implement only what is required today.

Do not implement future features prematurely.

---

# DRY

Do not pursue DRY aggressively.

Small duplicate code (around five lines or less) is acceptable if it improves readability.

Avoid introducing abstractions solely to remove small duplication.

---

# Method Extraction

Do not extract methods simply to:

- move one to three lines
- reduce tiny duplication
- satisfy theoretical clean code rules

Extract methods only when they:

- improve readability
- represent a meaningful business action
- are genuinely reused

---

# Method Naming

Method names should clearly express business intent.

Prefer names that describe what the code does from a business perspective.

Avoid vague names.

Avoid unnecessary abbreviations.

---

# Class Design

Keep classes cohesive.

Avoid God Objects.

Avoid large utility classes containing unrelated logic.

AVOID GOD SERVICE! limit code maksmimum +-400 lines per service. diatas itu split menjadi beberapa service yang lebih kecil dan fokus pada satu tanggung jawab per service.

---

# Typing

Prefer explicit types.

Avoid Object when a concrete type is known.

Use generics only when they improve type safety and readability.

Avoid unnecessary wildcard generics.

AVOID GOD SERVICE! limit code maksmimum +-400 lines per service. diatas itu split menjadi beberapa service yang lebih kecil dan fokus pada satu tanggung jawab per service.
---

# Ternary

Use ternary operators only for simple expressions.

Never use nested ternary operators.

Use if statements whenever readability improves.

---

# Comments

Comments should explain:

- business rules
- engineering decisions
- architectural constraints
- why a decision exists

Do not explain obvious Java syntax.

Do not comment what the code already expresses clearly.

Write comments in Indonesian.

Explain WHY, not WHAT.

---

# Dead Code

Do not generate:

- unused methods
- unused classes
- placeholder implementations
- empty TODOs
- speculative helper methods

Every piece of code should have a clear purpose.

---

# Error Handling

Never ignore exceptions.

Never swallow exceptions silently.

Fail explicitly.

Provide meaningful exception messages.

Error handling should simplify debugging.

---

# Logging

Log meaningful events.

Avoid excessive logging.

Logs should help diagnose production issues.

---

# Thread Safety

Assume concurrent execution is possible.

Avoid shared mutable state whenever practical.

Do not rely on undefined execution order.

Choose thread-safe designs when appropriate.

---

# Performance

Avoid premature optimization.

Optimize algorithms and architecture before micro-optimizations.

Never sacrifice readability for insignificant performance gains.

---

# Refactoring

Refactor only when readability or maintainability improves.

Do not refactor merely to reduce line count.

Do not introduce abstraction without clear value.

---

# Testing

Write code that is easy to test.

Reduce hidden dependencies.

Favor deterministic behavior.

Avoid unnecessary side effects.

---

# AI Behavior

If multiple valid implementations exist:

Choose the solution that is:

- easiest to understand
- easiest to debug
- easiest to maintain
- least surprising to future developers

Do not optimize for fewer lines of code.

Produce code that looks naturally written by an experienced engineer rather than generated by AI.