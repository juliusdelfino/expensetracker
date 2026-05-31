# Testing Standards

## Test Pyramid

```
         /  E2E  \          (few — browser/full-stack)
        / Integration \     (moderate — Spring context, DB)
       /    Unit Tests   \  (many — fast, isolated)
```

## Frameworks & Tools

| Tool | Purpose |
|------|---------|
| JUnit 5 | Test framework |
| Mockito | Mocking |
| Spring Boot Test | Integration testing with context |
| Embedded PostgreSQL (Zonky) | Real DB for integration tests |
| WireMock | Stub external HTTP (OCR, Nominatim, AI) |
| Spring Security Test | Auth-related testing |

## Conventions

### File & Class Naming

- Test class: `<ClassUnderTest>Test` (unit) or `<ClassUnderTest>IT` (integration).
- Location mirrors source: `src/test/java/com/delfino/expensetracker/service/ExpenseServiceTest.java`.

### Method Naming

```java
@Test
void shouldReturnExpenseWhenIdExists() { ... }

@Test
void shouldThrowNotFoundWhenExpenseDoesNotExist() { ... }
```

Use `should<Expected>When<Condition>` pattern.

### Structure (AAA)

```java
// Arrange
var expense = new Expense(...);

// Act
var result = service.findById(expense.getId());

// Assert
assertThat(result).isPresent();
```

## Unit Tests

- Test service and business logic classes in isolation.
- Mock all dependencies (repositories, external clients).
- No Spring context (`@ExtendWith(MockitoExtension.class)`).
- Must be fast (< 100ms each).

## Integration Tests

- Use `@SpringBootTest` with embedded PostgreSQL.
- Test full request lifecycle (controller → service → DB).
- Use `@Transactional` for automatic rollback or `@DirtiesContext` sparingly.
- WireMock for external API stubs.

## What to Test

| Layer | What to verify |
|-------|---------------|
| Controller | Status codes, response shape, validation errors, auth |
| Service | Business rules, edge cases, exception handling |
| Repository | Custom queries, pagination, projections |
| BusinessLogic | Pure logic — all branches and edge cases |

## Coverage Goals

- **New code**: ≥ 80% line coverage.
- **Critical paths** (payments, auth, data mutation): 100% branch coverage.
- Coverage is a guide, not a target — meaningful assertions matter more.

## Test Data

- Use builder patterns or factory methods for test entities.
- Avoid shared mutable test state.
- Each test must be independent and idempotent.

## Running Tests

```bash
mvn test                  # Unit tests only (Surefire)
mvn verify                # Unit + integration tests (Failsafe, if configured)
```

