# Receipt Storage & Expense Sharing — Implementation Plan

## Background & Current State

| Area | Current behaviour |
|---|---|
| Receipt upload | Saved to `data/receipts/<yyyyMMdd_HHmmss_SSS>_<userId>_<originalFilename>` (flat directory) |
| Attachment upload | Saved to `data/attachments/<expenseId>/<filename>` |
| Sharing | `urlId` (UUID) stored on `expenses`; `ShareController` serves an OG-meta redirect page at `/view/expenses/{urlId}`; no access control, no expiry, no revocation |
| Anonymous access | The SPA route `/#/expenses/{urlId}` requires an authenticated session — anonymous visitors who follow the share link cannot see the expense |
| Receipt serving | `GET /api/attachments/receipts/{filename}` — no authentication, flat path, filename leaks the original file name |

---

## Design Decisions

### 1 — Receipt file path layout
**Decision:** `data/receipts/<userId>/<yyyy_MM>/<UUID>.<ext>`

Rationale:
- Scopes files per user; makes per-user quotas and cleanup trivial.
- Monthly bucket prevents excessively large directories.
- UUID filename removes all correlation to the original filename, making files unguessable even if the directory were exposed.
- Extension is preserved (`.png`, `.jpg`, `.pdf`) for correct `Content-Type` detection.

### 2 — Sharing model: new `expense_shares` table
**Decision:** Introduce a dedicated `expense_shares` table rather than reusing columns on `expenses`.

Rationale:
- A user may revoke and re-create a share link without losing the `urlId` that already appears in bookmarks and OG previews.
- The share token (`shareToken`) is intentionally separate from `urlId` — `urlId` identifies the expense for the authenticated owner, while `shareToken` is the credential that grants temporary anonymous read access.
- Storing `expiresAt` and `revokedAt` on a child row is cleaner and allows future extensions (e.g. password-protected links, viewer analytics).

```sql
CREATE TABLE expense_shares (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    expense_id   INTEGER NOT NULL REFERENCES expenses(id),
    share_token  TEXT    NOT NULL UNIQUE,   -- random UUID, used in share URL
    created_at   DATETIME NOT NULL,
    expires_at   DATETIME NOT NULL,         -- default: created_at + 30 days
    revoked_at   DATETIME,                  -- NULL = still active
    created_by   INTEGER NOT NULL           -- userId of the person who created the link
);
```

### 3 — Anonymous access to receipts
**Decision:** Receipts are served via a **share-token–scoped endpoint** instead of the current open endpoint.

- Authenticated owner: `GET /api/attachments/receipts/{userId}/{yyyy_MM}/{uuid}.{ext}` (requires session).
- Anonymous viewer (via share link): `GET /api/share/{shareToken}/receipts/{uuid}.{ext}`.
  - The server validates `shareToken`, resolves the linked expense, and confirms that `{uuid}.{ext}` belongs to that expense before streaming the file.
- The filename stored on disk is the UUID, so possession of the URL is the only credential needed — there is no directory traversal risk and nothing to enumerate.
- The `AttachmentController` existing `/api/attachments/receipts/{filename}` endpoint must be **secured** (require authentication) or deprecated once migration is complete.

---

## Phases

---

### Phase 1 — Receipt Storage Reorganisation

**Goal:** Change where and how receipt files are stored on disk without breaking existing expenses.

#### 1.1 Backend — new upload path
- Update `ExpenseController.uploadReceipt` and `uploadReceiptBatch`:
  ```
  data/receipts/<userId>/<yyyy_MM>/<UUID>.<ext>
  ```
  Replace the current `getDateString() + "_" + userId + "_" + file.getOriginalFilename()` naming.
- Keep original file extension; strip everything else.
- Update `AttachmentController.getReceipt` to handle the new path pattern:
  ```
  GET /api/attachments/receipts/{userId}/{yyyy_MM}/{filename}
  ```
  Require the requesting user to match `{userId}` (or be an admin).

#### 1.2 Backend — data migration
- Add a `DbMigrationRunner` / `DataMigrationRunner` step (following existing pattern in `migration/DbMigrationRunner.java`) that:
  1. Selects all `expenses` where `image_path` matches the old flat pattern.
  2. Moves each file to the new directory structure.
  3. Updates `expenses.image_path` to the new relative path.
- Migration must be idempotent (skip rows already on the new path).

#### 1.3 Tests
- Unit-test the new path builder.
- Integration test: upload → stored in `<userId>/<yyyy_MM>/<UUID>.<ext>` → served back correctly.

---

### Phase 2 — Expense Sharing Infrastructure

**Goal:** Introduce the `expense_shares` table, share-link lifecycle API, and anonymous read access.

#### 2.1 DB migration
- Create `expense_shares` table (schema above).

#### 2.2 Model & Repository
- New `ExpenseShare` JPA entity mapping the table.
- `ExpenseShareRepository` with:
  - `findByShareTokenAndRevokedAtIsNull(String token)`
  - `findByExpenseIdAndRevokedAtIsNull(long expenseId)` — to check for an existing active link.

#### 2.3 Service — `ExpenseShareService`
```java
ExpenseShare createShare(long expenseId, long userId, Duration ttl);   // default TTL = 30 days
ExpenseShare revokeShare(long shareId, long userId);
Optional<ExpenseShare> resolveActiveShare(String shareToken);           // checks expiry + revocation
```

#### 2.4 API — `ExpenseShareController`
| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/expenses/{urlId}/share` | Required | Create (or return existing active) share link |
| `DELETE` | `/api/expenses/{urlId}/share` | Required | Revoke active share link |
| `GET` | `/api/expenses/{urlId}/share` | Required | Get current share status (token, expiry, revoked) |
| `GET` | `/api/share/{shareToken}/expense` | None | Return expense detail for anonymous viewer |

#### 2.5 Anonymous receipt endpoint
```
GET /api/share/{shareToken}/receipts/{filename}
```
- Validate `shareToken` → resolve `ExpenseShare` → verify `filename` is associated with that expense's `imagePath` or `attachments`.
- Stream the file if all checks pass; `403` otherwise.

#### 2.6 Security config
- Add `/api/share/**` and `/view/expenses/**` to the permit-all list in `SecurityConfig`.
- Keep `/api/attachments/**` authenticated (or add ownership check).

#### 2.7 Update `ShareController` OG page
- Continue to work for anonymous visitors (no session required — already permit-all).
- The redirect target changes to `/#/share/{shareToken}` so the SPA knows to render in anonymous mode.

---

### Phase 3 — UI: Share, Manage & Anonymous View

**Goal:** Surface sharing controls to the owner and render the expense correctly for anonymous visitors.

#### 3.1 Owner: share panel (expense detail screen)
- **Share button** → triggers `POST /api/expenses/{urlId}/share`.
- Share panel shows:
  - Copyable share URL (e.g. `https://app.example.com/view/expenses/{shareToken}` or `/#/share/{shareToken}`).
  - Expiry date/time ("Expires in 30 days — Jun 30, 2026").
  - **Revoke** button → calls `DELETE /api/expenses/{urlId}/share`.
- If no active share exists, show only the "Create share link" button.
- If a share exists, show the info card + revoke option.

#### 3.2 Anonymous view (`/#/share/{shareToken}`)
- New SPA route/page — no login required.
- Calls `GET /api/share/{shareToken}/expense` to load the expense detail.
- Displays: store name, date, amount, category, items list.
- Receipt images use the share-scoped URL: `GET /api/share/{shareToken}/receipts/{filename}`.
- Shows a "Sign in to track your own expenses" prompt / banner.
- If the token is expired or revoked: show a clear "This link has expired or been revoked." message.

#### 3.3 OG meta / `ShareController` update
- Ensure the OG preview still works for the share token URL.
- Either reuse the existing `ShareController` by accepting both `urlId` (owner) and `shareToken` (public) patterns, or add a second `@GetMapping`.

---

### Phase 4 — Hardening & Polish

**Goal:** Close security gaps and improve the user experience.

#### 4.1 Security
- Add an ownership check to `GET /api/attachments/receipts/**` — only the owning user may access their files (prevents cross-user enumeration once path is known).
- Rate-limit `GET /api/share/{shareToken}/**` to prevent token brute-forcing.
- Log share-link access (who viewed, when) in a `share_access_log` table or via structured logging.

#### 4.2 Configurable TTL
- Add `app.sharing.default-ttl-days=30` to `application.properties`.
- Owner may optionally choose a shorter TTL (7 days, 24 hours) via the share panel.

#### 4.3 Scheduled cleanup
- Nightly `@Scheduled` task to delete files in `data/receipts/` that no longer have a corresponding `expenses` row, and to hard-delete `expense_shares` rows that have been revoked or expired for more than 90 days.

#### 4.4 Existing `urlId` backward compatibility
- Keep `GET /view/expenses/{urlId}` working for the authenticated owner (redirects to `/#/expenses/{urlId}`).
- The new public share route is `GET /view/share/{shareToken}` (or reuse the same endpoint with token-type detection).

---

## Summary of New Endpoints

| # | Method | Path | Auth | Phase |
|---|---|---|---|---|
| 1 | `GET` | `/api/attachments/receipts/{userId}/{yyyy_MM}/{filename}` | Required (owner) | 1 |
| 2 | `POST` | `/api/expenses/{urlId}/share` | Required | 2 |
| 3 | `GET` | `/api/expenses/{urlId}/share` | Required | 2 |
| 4 | `DELETE` | `/api/expenses/{urlId}/share` | Required | 2 |
| 5 | `GET` | `/api/share/{shareToken}/expense` | None | 2 |
| 6 | `GET` | `/api/share/{shareToken}/receipts/{filename}` | None | 2 |

## Summary of DB Changes

| Table | Change | Phase |
|---|---|---|
| `expenses` | No structural change; `imagePath` values updated to new path format | 1 |
| `expense_shares` | New table | 2 |

## Files to Create / Modify (approximate)

| File | Action |
|---|---|
| `ExpenseController.java` | Modify upload path logic |
| `AttachmentController.java` | Add new path pattern + ownership check |
| `migration/DbMigrationRunner.java` | Add step to move old receipt files |
| `model/ExpenseShare.java` | New |
| `repository/ExpenseShareRepository.java` | New |
| `service/ExpenseShareService.java` | New |
| `controller/ExpenseShareController.java` | New |
| `controller/ShareController.java` | Update OG redirect URL |
| `config/SecurityConfig.java` | Permit `/api/share/**` |
| `application.properties` | Add `app.sharing.default-ttl-days` |
| SPA: `ShareView.vue` / `ShareRoute` | New anonymous view component |
| SPA: `ExpenseDetail.vue` | Add share panel / controls |

