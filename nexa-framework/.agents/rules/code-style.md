---
trigger: always_on
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
- Tulis komentar dalam Bahasa Indonesia untuk instance, class, method, helper, dan komponen utama.
- Pada bagian kode yang krusial, tulis komentar penjelas alur kerja kode secara runtut.

Formatting:

- Maximum line length: 120
- Blank line between logical blocks.
- No wildcard imports.

Pure Dependency Injection & Coupling Rules:

- Dilarang keras menggunakan framework/anotasi DI otomatis (seperti `@Autowired`, `@Component`, `@Inject`).
- Semua perakitan ketergantungan wajib dilakukan secara eksplisit menggunakan Constructor Injection biasa di Composition Root (`DefaultRuntimeEngine`).
- Dilarang keras membuat import melingkar (cyclic dependency) antar-domain. Jika domain A membutuhkan domain B, domain A harus merujuk ke antarmuka di `nexa.framework.runtime.domain.b.api.*`.
- Untuk DTO berbasis Java Record, gunakan konstruktor kanonik manual atau compact constructor untuk membersihkan dan menormalisasi input null agar aman terhadap NullPointerException.