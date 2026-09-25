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

## AI Usage / Admin Regression Checklist

- Validate quota exhaustion responses include machine-readable codes:
  - `AI_CHAT_QUOTA_EXCEEDED`
  - `AI_OCR_QUOTA_EXCEEDED`
  - `AI_MODEL_NOT_ALLOWED`
  - `ADMIN_ONLY`
- Verify concurrent quota consumption does not over-increment monthly usage rows.
- Verify admin usage overview returns provider/model breakdowns and near-quota counts.
- Verify admin-only routes remain forbidden for regular users.
- When adding observability, assert counters/timers on critical AI flows where practical.

## Account Management Regression Checklist

### Expense Trash (`UserTrashService`, `UserDataController`)

- `GET /api/user/trash/expenses` returns only the current user's soft-deleted expenses (never another user's).
- `POST /api/user/trash/expenses/{id}/restore` restores the expense; returns 400 for active expenses, 403 for another user's.
- `DELETE /api/user/trash/expenses/{id}` permanently deletes; returns 400 for active expenses, 403 for another user's.
- `POST /api/user/trash/expenses/purge` bulk deletes: all when `expenseIds` is empty/null; only selected IDs otherwise.
- Bulk purge with a mix of own and foreign IDs returns 403 and does not delete anything.
- After purge, expense rows and linked items must be absent from the DB.

### Account Export (`UserExportService`, `UserDataController`)

- `GET /api/user/export` requires authentication; returns 401 for anonymous requests.
- Response `Content-Type` is `application/zip`; `Content-Disposition` contains `account-export-`.
- ZIP contains `account.json`, `expenses.json`, `expenses.csv`, `metadata.json` entries.
- Only active (non-deleted) expenses are included in the export.
- An account with zero expenses still exports cleanly (valid ZIP, empty arrays in JSON).

### Account Deletion (`UserDeletionService`, `UserDataController`)

- `POST /api/user/delete-account` requires authentication; returns 401 for anonymous requests.
- Wrong password returns 400 with message `"Incorrect password"`.
- Missing or wrong confirmation phrase returns 400 with `"Confirmation phrase must be exactly: DELETE"`.
- Successful deletion removes user, all expenses, expense items, stores, and AI usage rows from the DB.
- Session is invalidated immediately after successful deletion (subsequent authenticated requests on the same session return 401).
- One user cannot delete another user's account (each request operates only on the authenticated user).
