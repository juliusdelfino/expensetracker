# Coding Standards

## Java

### General

- **Java version**: 21. Use modern features (records, pattern matching, sealed classes, text blocks) where appropriate.
- **No Lombok**. Use IDE generation or records for boilerplate reduction.
- **Max line length**: 120 characters.
- **Indentation**: 4 spaces, no tabs.

### Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Class | PascalCase | `ExpenseService` |
| Method/variable | camelCase | `calculateTotal` |
| Constant | UPPER_SNAKE | `MAX_UPLOAD_SIZE` |
| Package | lowercase | `com.delfino.expensetracker.service` |
| DTO | Suffix with `Request`/`Response`/`Dto` | `ExpenseResponse` |
| REST Controller | Suffix with `Controller` | `ExpenseController` |

### Classes & Methods

- Prefer small, focused methods (≤ 30 lines).
- One public class per file.
- Use `final` for local variables that are not reassigned.
- Avoid `null` returns — use `Optional` for query methods, throw exceptions for invalid states.
- Use Bean Validation annotations (`@NotNull`, `@Size`, etc.) on DTOs.

### Exception Handling

- Define application-specific exceptions (e.g., `ResourceNotFoundException`).
- Use `@RestControllerAdvice` for global exception mapping.
- Never catch `Exception` or `Throwable` broadly without re-throwing or logging.

### Logging

- Use SLF4J (`org.slf4j.Logger`) — Log4j2 is the implementation.
- Use parameterized messages: `log.info("Processed expense {}", id)`.
- Never log sensitive data (passwords, tokens, PII).

### Dependencies & Injection

- Use constructor injection (no `@Autowired` on fields).
- Keep Spring dependencies out of `businesslogic` and `util` packages.

## JavaScript (Frontend)

- ES6+ syntax. No TypeScript, no build tooling.
- `const` by default, `let` when reassignment is needed, never `var`.
- Functions: prefer `async/await` over raw Promises.
- Use `===` and `!==` exclusively.
- Escape user-supplied data before inserting into DOM (use the `esc()` helper).
- Prefix private/internal module variables with `_`.
- Max line length: 120 characters.

## SQL / JPA

- Entity class names match the domain concept in singular form (`Expense`, `Store`).
- Table names are lowercase snake_case and pluralized (`expenses`, `stores`).
- Use `@Column(name = "...")` explicitly for clarity.
- Avoid `CascadeType.ALL` — be explicit about cascades.
- Prefer JPQL/HQL; use native queries only when JPQL is insufficient.

## Git Conventions

- Branch naming: `feature/<short-desc>`, `bugfix/<short-desc>`, `hotfix/<short-desc>`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):
  - `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `perf:`
- Keep commits atomic — one logical change per commit.

