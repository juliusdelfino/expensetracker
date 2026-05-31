# SonarQube / Static Analysis Rules

## Quality Gate Criteria

| Metric | Threshold |
|--------|-----------|
| Code coverage (new code) | ≥ 80% |
| Duplicated lines (new code) | ≤ 3% |
| Maintainability rating | A |
| Reliability rating | A |
| Security rating | A |
| Security hotspots reviewed | 100% |

## Key Rules (Must Not Violate)

### Bugs

- No `NullPointerException` risks — use `Optional`, null checks, or `@NonNull`.
- No resource leaks (streams, connections must be closed in try-with-resources).
- No ignored exceptions (empty catch blocks).

### Vulnerabilities

- No hardcoded credentials or secrets.
- No SQL injection (use parameterized queries / JPA).
- No path traversal in file operations.
- No deserialization of untrusted data.

### Code Smells

- **Cognitive complexity** per method: ≤ 15.
- **Method length**: ≤ 50 lines (aim for ≤ 30).
- **Class length**: ≤ 500 lines.
- **Parameter count**: ≤ 5 per method (use a DTO/object if more are needed).
- No unused imports, variables, or private methods.
- No commented-out code in production source.

### Naming

- Boolean methods/variables should indicate state: `isActive`, `hasItems`, `canEdit`.
- Test methods should be descriptive: `shouldReturnNotFoundWhenExpenseDoesNotExist`.

## Suppression Policy

- Suppress only with `@SuppressWarnings` and a comment explaining why.
- Never suppress security or bug rules without team review.

## Recommended Plugins / Profiles

- SonarQube default Java profile + "Sonar way".
- Enable rules for Spring-specific issues (e.g., `@Transactional` misuse).
- Enable JavaScript/HTML analysis for `src/main/resources/static`.

