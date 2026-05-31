# API Versioning

## Current State

All REST endpoints live under `/api/` with no explicit version prefix. This document defines the strategy for when breaking changes become necessary.

## Versioning Strategy: URI Path Prefix

```
/api/v1/expenses
/api/v2/expenses
```

### Why URI-based

- Simple, visible, easy to route and document.
- Compatible with static frontend (no header manipulation needed).
- Works well with reverse proxies and API gateways.

## Rules

1. **Non-breaking changes do NOT require a new version:**
   - Adding optional fields to responses.
   - Adding new endpoints.
   - Adding optional query parameters.
   - Relaxing validation (accepting more input).

2. **Breaking changes REQUIRE a new version:**
   - Removing or renaming fields in responses.
   - Changing field types.
   - Removing endpoints.
   - Changing URL structure.
   - Tightening validation (rejecting previously valid input).

3. **Support at most 2 concurrent versions.** Deprecate the old version with a timeline (minimum 3 months).

4. **Deprecation signaling:**
   - Add `Deprecation` header to old-version responses.
   - Log warnings when deprecated endpoints are called.
   - Document sunset date in changelog.

## Endpoint Conventions

| Aspect | Convention |
|--------|-----------|
| Base path | `/api/v{n}/` |
| Resource naming | Plural nouns (`expenses`, `stores`, `items`) |
| HTTP methods | `GET` (read), `POST` (create), `PUT` (full update), `PATCH` (partial), `DELETE` |
| Nested resources | `/api/v1/expenses/{id}/items` |
| Filtering | Query params: `?category=food&from=2026-01-01` |
| Pagination | `?page=0&size=20` (Spring default) |
| Sorting | `?sort=date,desc` |

## Response Format

```json
{
  "data": { ... },
  "message": "optional human-readable message",
  "timestamp": "2026-05-29T12:00:00Z"
}
```

Error responses:

```json
{
  "error": "NOT_FOUND",
  "message": "Expense not found",
  "timestamp": "2026-05-29T12:00:00Z",
  "path": "/api/v1/expenses/999"
}
```

## Migration Path

When ready to introduce versioning:

1. Add `/api/v1/` prefix to all existing endpoints.
2. Keep `/api/` as an alias for the latest version (or redirect).
3. Update frontend API calls to use `/api/v1/`.

