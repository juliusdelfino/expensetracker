# Database Migration Guidelines

## Strategy

We use **Flyway** for all schema migrations. JPA's `ddl-auto` must be set to `validate` (never `update` or `create`) in all environments except local development.

## Migration File Conventions

### Location

```
src/main/resources/db/migration/
```

### Naming

```
V<version>__<description>.sql
```

- **Version**: Sequential integer or timestamp-based (`V1__`, `V2__`, … or `V20260529120000__`).
- **Description**: Lowercase with underscores, describes the change.
- Examples:
  - `V1__create_expenses_table.sql`
  - `V2__add_exchange_rate_column.sql`
  - `V3__create_stores_table.sql`

### Repeatable Migrations (rare)

```
R__<description>.sql
```

Use only for views, functions, or seed data that can be safely re-applied.

## Writing Migrations

### Rules

1. **One logical change per migration file.** Don't mix unrelated DDL.
2. **Migrations are immutable.** Never edit a migration that has been applied. Create a new one instead.
3. **Always include rollback comments.** Add a comment block at the top showing how to reverse the change (even if Flyway doesn't auto-rollback):
   ```sql
   -- Rollback: ALTER TABLE expenses DROP COLUMN exchange_rate;
   ```
4. **Use explicit column types.** Don't rely on defaults — specify `VARCHAR(255)`, `NUMERIC(12,2)`, etc.
5. **Add indexes explicitly** for foreign keys and frequently-queried columns.
6. **Non-nullable columns on existing tables** must have a `DEFAULT` or be backfilled in the same migration.
7. **No data-destructive operations** without team review (dropping columns/tables).

### Flyway Configuration (application.yml)

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
```

## Environment Strategy

| Environment | `ddl-auto` | Flyway enabled |
|-------------|-----------|----------------|
| Local dev | `validate` | ✅ |
| Test (CI) | `validate` | ✅ (on embedded PG) |
| Staging | `validate` | ✅ |
| Production | `validate` | ✅ |

## Transition Plan

The project currently uses JPA auto-DDL. To transition to Flyway:

1. Generate a baseline migration (`V1__baseline.sql`) from the current production schema using `pg_dump --schema-only`.
2. Add Flyway dependency to `pom.xml`.
3. Set `baseline-on-migrate: true` so existing databases are baselined at V1.
4. Set `spring.jpa.hibernate.ddl-auto=validate`.
5. All future schema changes go through new migration files.

## Testing Migrations

- Integration tests use Embedded PostgreSQL — Flyway runs automatically.
- Add a dedicated test that verifies all migrations apply cleanly on a fresh database.

