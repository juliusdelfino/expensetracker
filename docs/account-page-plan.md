- `Receipt Trash`
- soft deletion applies at the expense level via `Expense.deleted`
# User Account Page — Improvement Plan

## Goal
- management of soft-deleted receipts/expenses from the User Account page
- permanent deletion of already soft-deleted receipts/expenses
- management of soft-deleted receipts/expenses from the User Account page
- permanent deletion of already soft-deleted receipts/expenses
- download/export of user account data
- deletion of the user account and associated user-owned data

This plan is tailored to the current codebase:
- Backend: Spring Boot 3.5 + Spring MVC + Spring Data JPA
- Frontend: static SPA in `src/main/resources/static/js/*.js`
- Current profile UI:
  - rendered by `renderProfile(app)` in `src/main/resources/static/js/auth.js`
  - there is no current permanent-delete flow for expenses/receipts
- Current account API surface:
  - `GET /api/auth/me`
  - `PUT /api/user/profile`
- Current expense deletion model:
  - expenses are soft-deleted via `Expense.deleted`
  - there is no current permanent-delete flow for expenses/receipts
  - there is no current permanent-delete flow for soft-deleted expenses
- Current export surface:
  - expenses can be exported as JSON/CSV via `GET /api/expenses/export`
- View and manage soft-deleted receipts/expenses from the User Account page
- Current file storage model:
  - scanned receipts are stored under `data/receipts/`
  - attachments are stored under `data/attachments/{expenseUrlId}/`

---

## Scope

### User-facing capabilities
- View and manage soft-deleted receipts/expenses from the User Account page
- View and manage soft-deleted expenses from the User Account page
- Permanently delete items already in trash
- Download account and user-owned data
- Delete the user account from within the application

### Backend capabilities
- Provide account-specific APIs for trash, export, and account deletion
### Important architectural constraint
Instead:
- Keep the feature consistent with the existing session-based auth model

---

## Current State Assessment

That means the User Account page trash area should conceptually be a **Receipt/Expense Trash** backed by deleted expenses.
- soft-deleted expenses,
- along with their scanned receipt file and attachments.
The app already has a basic account/profile page with:
- username display
- email and phone editing
- base currency, base city, and base country settings
- password update
- appearance/theme controls

The app also already supports:
- soft deletion of expenses
- export of expenses as JSON/CSV

The requested trash-management scope is specifically about **soft-deleted expenses**, which the app already supports today.
### Important architectural constraint
The current domain model does **not** appear to have a standalone "deleted receipt" entity.

Instead:
- a scanned receipt is attached to an `Expense` via `imagePath`
- attachments are stored separately and referenced from the expense
- soft deletion applies at the expense level via `Expense.deleted`

**Implication:**
For this feature, “soft-deleted receipts” should be implemented as:
- soft-deleted expenses,
- along with their scanned receipt file and attachments.

That means the User Account page trash area should conceptually be a **Receipt/Expense Trash** backed by deleted expenses.

---

## Design Decisions

### 1 — Split User Account into tabs
**Decision:** replace the current single long account page with tabbed sections.

Recommended tabs:
- `Expense Trash`
- `Appearance`
- `Receipt Trash`
- `Data & Export`
- `Danger Zone`

Why:
- `Profile`
### 2 — Manage deleted receipts through deleted expenses
**Decision:** use soft-deleted expenses as the backing store for deleted receipt management.

### 2 — Manage soft-deleted expenses directly
**Decision:** use the existing soft-deleted expense model as the backing store for trash management.
---


Why:
- The soft-delete flow is already implemented, so the User Account work can build on top of it rather than redefining deletion semantics.
- Each expense already owns the receipt file and attachments.
- It avoids inventing a separate receipt-trash system that would conflict with the existing architecture.

- It avoids inventing a separate receipt-trash system that would conflict with the existing architecture.
- The trash tab shows soft-deleted expenses belonging to the user.
- Each row/card can show:
  - category
  - amount
  - date
  - whether a scanned receipt exists
  - number of attachments
  - deleted timestamp if added later
- Actions:
  - restore
  - permanently delete

---

### 3 — Permanent deletion should only apply to already soft-deleted items
**Decision:** hard delete should only be allowed from trash.

Why:
- Safer UX
- Preserves the current soft-delete behavior as the default delete flow
- Reduces accidental data loss

Recommended rule:
- active expenses can be soft-deleted only
- only already-deleted expenses may be permanently purged

---

### 4 — Account export should include structured data and user files
**Decision:** provide a full account export as a downloadable ZIP archive.

Recommended contents:
- `account.json`
- `expenses.json`
- `expenses.csv`
- `receipts/`
- `attachments/`
- `metadata.json`

Why:
- The app stores both records and files
- ZIP is the simplest user-friendly delivery format
- It satisfies common portability expectations for account-data export

---

### 5 — Account deletion should be an explicit, confirmed destructive workflow
**Decision:** expose account deletion in a dedicated `Danger Zone` tab with strong confirmation requirements.

Recommended protections:
- require current password
- require a typed confirmation phrase such as `DELETE`
- show a summary of what will be removed
- invalidate the session immediately after success

Why:
- This is a high-risk operation
- It should be intentionally separated from routine profile edits
- It must be auditable and testable

---

### 6 — Start with immediate deletion workflows, then add async/retention options later
**Decision:** first version should favor simpler synchronous flows where practical.

Applies to:
- permanent deletion of trash items
- account export generation if data size is reasonable
- account deletion

Why:
- Simpler to implement and validate in the existing architecture
- Lower coordination cost than adding job tables/background processing immediately

Optional later:
- async export jobs
- scheduled account deletion
- auto-purge retention policy for trash

---

## Proposed User Account Information Architecture

### `Profile` tab
Purpose:
- manage user identity and preferences already supported by the profile API

Contents:
- username
- email
- phone number
- base currency
- base city
- base country
- password change

### `Appearance` tab
Purpose:
### `Receipt Trash` tab

Contents:
- theme selector
### `Expense Trash` tab

- manage soft-deleted expenses
Purpose:
- manage soft-deleted expenses/receipts

Contents:
- deleted expense list
- restore action
- permanent delete action
- bulk selection actions
- optional retention notice

### `Receipt Trash` tab
Purpose:
- provide account and data download controls

Contents:
- download full account export ZIP
- download expenses JSON
- download expenses CSV
- explanation of included data

### `Danger Zone` tab
Purpose:
- isolate destructive account-level actions

Contents:
- delete account CTA
- confirmation form
- irreversible action warnings

---

## Proposed API Surface

### Account summary / tabs support
| Method | Path | Purpose |
| `GET` | `/api/user/trash/summary` | return soft-deleted expense counts, file counts, and optional retention info |
| `GET` | `/api/user/trash/summary` | return deleted-expense counts, file counts, and optional retention info |
|---|---|---|

### Trash management
| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/user/trash/expenses` | list soft-deleted expenses for the current user |
| `DELETE` | `/api/user/trash/expenses/{expenseUrlId}` | permanently delete one soft-deleted expense and linked files |
| `POST` | `/api/user/trash/expenses/purge` | bulk purge selected or all soft-deleted expenses |
| `POST` | `/api/user/trash/expenses/{expenseUrlId}/restore` | optional account-scoped restore endpoint |

Note:
- The app already has `PATCH /api/expenses/{expenseUrlId}/restore`.
- You may either reuse that endpoint from the UI or add a User Account specific endpoint for consistency.

### Data export
| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/user/export` | download full account export ZIP |
| `GET` | `/api/user/export/expenses` | optional endpoint for account-page JSON/CSV exports |

Optional async version later:
| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/user/export` | start export job |
| `GET` | `/api/user/export/{jobId}` | get export job status |
| `GET` | `/api/user/export/{jobId}/download` | download generated export |

### Account deletion
| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/user/delete-account` | validate confirmation and permanently delete the account and user-owned data |

Alternative REST form:
| Method | Path | Purpose |
|---|---|---|
| `DELETE` | `/api/user` | delete current account |

Recommended request body:
```json
{
  "password": "current-password",
  "confirmation": "DELETE"
}
```

---

## Backend Responsibilities

### `UserController` or `UserDataController`
Responsibilities:
- expose account summary endpoints
- expose trash/export/delete-account endpoints
- validate request payloads
- delegate destructive actions to dedicated services

Recommendation:
- keep `PUT /api/user/profile` in `UserController`
- consider a new `UserDataController` for trash/export/deletion responsibilities to avoid one oversized controller

### `UserTrashService`
Responsibilities:
- list soft-deleted expenses for the user
- permanently delete soft-deleted expenses
- validate ownership
- validate that the expense is already soft-deleted
- delete linked receipt files and attachment files/directories

### `UserExportService`
Responsibilities:
- load account profile data
- load user expenses
- build JSON/CSV payloads
- package receipt files and attachments into ZIP output
- stream or return the export response

### `UserDeletionService`
Responsibilities:
- validate password and confirmation text
- collect all user-owned data
- delete linked files first or in a safe order
- purge expenses, items, stores, and account row
- invalidate session / logout current user
- log deletion event

---

## Data Model / Persistence Considerations

### Current model usage
Leverage current tables/entities:
- `User`
- `Expense`
- `ExpenseItem`
- `Store`
- `expense_attachments` collection table

### Recommended near-term additions
#### Add `deletedAt` to `Expense`
Suggested reason:
- better trash UX
- easier sort/filter in trash
- future auto-purge support
- clearer auditability

Suggested field:
```java
LocalDateTime deletedAt;
```

Behavior:
- set when soft-deleting an expense
- clear when restoring an expense

This is recommended but not strictly required for the first version.

### Hard-delete logic requirements
Permanent deletion of a deleted expense should remove:
- the `Expense` row
- related `ExpenseItem` rows
- `expense_attachments` entries
- scanned receipt file pointed to by `imagePath`
- attachments under `data/attachments/{expenseUrlId}/`
- any other future dependent data tied to the expense

### Account deletion requirements
Deleting a user account should remove:
- the `User` row
- all user-owned expenses
- all dependent items and attachment references
- all receipt files and attachment files
- user stores if no longer referenced elsewhere and user-scoped
- future user-owned entities such as reports if added later

---

## Export Content Model

### Full export ZIP contents
Recommended first version:
```text
account-export-YYYY-MM-DD.zip
  account.json
  expenses.json
  expenses.csv
  metadata.json
  receipts/
    ...files...
  attachments/
    <expense-url-id>/
      ...files...
```

### `account.json`
Should contain:
- username
- email
- phone number
- base currency
- base city
- base country
- createdAt
- updatedAt

### `expenses.json`
Should contain:
- export-safe view of user expenses
- preferably aligned with or derived from existing expense export DTOs
- no server-only implementation details that users do not need

### `metadata.json`
Should contain:
- export generation timestamp
- app version if available
- counts of exported expenses/files
- export format version

---

## Frontend Responsibilities

### Route design
Recommended route structure:
- `#/profile`
- `#/profile?tab=profile`
- `#/profile?tab=appearance`
- `#/profile?tab=trash`
- `#/profile?tab=data`
- `#/profile?tab=danger`

Alternative:
- `#/profile/profile`
- `#/profile/appearance`
- `#/profile/trash`
- `#/profile/data`
- `#/profile/danger`

For the current router, query-param tabs are likely the smaller change.

### User Account page UX
Replace the current single-page profile card layout with:
- tab header / segmented navigation
### Receipt Trash UX
- mobile-friendly stacked layout
### Expense Trash UX

### Receipt Trash UX
Recommended UI elements:
- trash summary card
- deleted item list or table
- restore button
- permanent delete button
- bulk select + bulk purge
- confirmation modal or double-confirm pattern

### Data & Export UX
Recommended UI elements:
- “Download full account archive” primary CTA
- tab deep-link support
- note describing included files and formats
- optional password re-entry if required by policy

### Danger Zone UX
Recommended UI elements:
- strong visual separation from normal tabs
- summary of data that will be removed
- password field
- typed confirmation field
- final destructive button

---

## Phases

---

### Phase 1 — Tabbed User Account Foundation

**Goal:** restructure the current User Account page into a scalable tabbed surface.

#### 1.1 Add tabbed account layout
- Receipt Trash
- Expense Trash
- Appearance
- Receipt Trash
- Data & Export
- Danger Zone

#### 1.2 Preserve existing profile update flow
Keep using:
- `GET /api/auth/me`
- `PUT /api/user/profile`

#### 1.3 Add lightweight summary endpoints
Add summary endpoints to support the new UI shell:
- account summary
- trash summary

#### 1.4 Add route/tab deep-linking
- Profile

#### 1.5 Tests / QA
- route rendering checks
- tab switching behavior
- mobile layout QA
- existing profile update flow regression checks

**Exit criteria:**
- User Account page is tabbed
- existing profile editing still works
### Phase 2 — Receipt Trash Management

### Phase 2 — Expense Trash Management

**Goal:** allow users to manage already-soft-deleted expenses from their account.

**Goal:** allow users to manage soft-deleted expenses/receipts from their account.

#### 2.1 Add deleted-expense list endpoint
Return the current user’s soft-deleted expenses.

#### 2.2 Add restore from account UI
Either:
- call the existing restore endpoint, or
---

#### 2.3 Add permanent delete for one item
**Goal:** allow users to manage soft-deleted expenses/receipts from their account.

#### 2.4 Add bulk purge
Allow selected/all trash items to be permanently deleted.

#### 2.5 File cleanup logic
Delete:
- scanned receipt file
- attachments directory and files
- any linked attachment references

#### 2.6 Tests
- only owner can view trash
- only owner can purge
- cannot purge active expense through trash API
- User Account page can list, restore, and purge deleted receipt/expense records
- restore still works correctly
- User Account page can list, restore, and purge soft-deleted expense records
**Exit criteria:**
- User Account page can list, restore, and purge deleted receipt/expense records

---

### Phase 3 — User Data Export

**Goal:** let users download their account and associated data from the User Account page.

#### 3.1 Add export service
Implement account export assembly logic.

#### 3.2 Add export endpoint


#### 3.3 Reuse existing expense export logic where practical
Leverage the existing JSON/CSV export patterns instead of duplicating logic unnecessarily.

#### 3.4 Add account-page export UI
Add clear download CTAs in the `Data & Export` tab.

#### 3.5 Tests
- authenticated-only access
- only current user data included
- ZIP contains expected files
- empty/attachment-free accounts still export cleanly

**Exit criteria:**
- user can download a full account archive including structured data and owned files

---

### Phase 4 — Account Deletion

**Goal:** let users delete their account and associated user-owned data in-app.

#### 4.1 Add delete-account request DTO
Include:
- current password
- typed confirmation phrase

#### 4.2 Add account deletion service
Implement ordered purge of:
- user expenses and dependent data
- files
- user-owned stores if applicable
- user account

#### 4.3 Invalidate session after deletion
User should be logged out immediately after successful deletion.

#### 4.4 Add `Danger Zone` UI
Provide warnings and a confirmation flow.

#### 4.5 Tests
- wrong password rejected
- missing confirmation rejected
- successful deletion removes account data
- session invalidated after delete
- one user cannot delete another user’s account

**Exit criteria:**
- user can permanently delete their account and is logged out immediately afterward

---

### Phase 5 — Hardening, Security, and Policy Alignment

**Goal:** make the feature safe, maintainable, and aligned with product/legal copy.

#### 5.1 Require stronger confirmation for destructive operations
Recommended for:
- full export
- hard delete from trash
- account deletion

#### 5.2 Logging and auditing
Log:
- export generation
- permanent purge of trash items
- account deletion events

#### 5.3 Review file access model
The current attachment serving endpoints are public-facing and should be reviewed for privacy/security alignment.

Potential follow-up:
- require ownership/authenticated access for private receipt files
- or introduce signed/share-token access only where intentional

#### 5.4 Update terms/privacy copy
Current UI text says users must email for full export/account deletion.
That should be updated once the in-app feature ships.

#### 5.5 Documentation updates
Update:
- `docs/security.md`
- `docs/testing.md`
- user-facing legal/help text if maintained in the SPA

**Exit criteria:**
- account-management functionality is secure, documented, and consistent with user-facing copy

---

## Suggested Files to Create / Modify

### Backend
| File | Action |
|---|---|
| `controller/UserController.java` | extend or keep profile-only |
| `controller/UserDataController.java` | recommended new controller for trash/export/delete |
| `service/UserTrashService.java` | new |
| `service/UserExportService.java` | new |
| `service/UserDeletionService.java` | new |
| `dto/user/*` | new DTOs for summary/export/delete requests |
| `repository/ExpenseRepository.java` | add deleted-expense queries / purge helpers |
| `repository/ExpenseItemRepository.java` | add hard-delete helpers if needed |
| `model/Expense.java` | optionally add `deletedAt` |

### Frontend
| File | Action |
|---|---|
| `static/js/auth.js` | refactor current profile page into tabbed account UI |
| `static/css/base.css` or new account CSS | tabs, expense-trash list, danger zone styling |
| `static/index.html` | optional nav label/entry refinements |
| `static/css/base.css` or new account CSS | tabs, trash list, danger zone styling |
| `static/js/utils.js` | update privacy/terms text after rollout |

### Tests
| File | Action |
|---|---|
| `src/test/java/com/delfino/expensetracker/controller/UserControllerTest.java` | extend |
| `static/css/base.css` or new account CSS | tabs, trash list, danger zone styling |
| `src/test/java/com/delfino/expensetracker/service/*` | add focused service tests |
2. **Phase 2** — receipt trash management

---

## Recommended Rollout Order
2. **Phase 2** — expense trash management
1. **Phase 1** — tabbed account shell
2. **Phase 2** — receipt trash management
3. **Phase 3** — full user data export
4. **Phase 4** — account deletion
5. **Phase 5** — security/policy hardening and copy updates

Why this order works:
- it improves the page structure first
- it introduces lower-risk account management before the most destructive workflow
- it gives users export capability before account deletion


---

## Open Questions

1. **Should trash items be auto-purged after N days?**
   - Recommended first answer: no automatic purge yet; add later after introducing `deletedAt`.

2. **Should full export require password re-entry?**
   - Recommended first answer: yes, especially if receipts/attachments are included.

3. **Should account deletion happen immediately or be scheduled with a grace period?**
   - Recommended first answer: immediate deletion for v1 unless product/legal policy requires a delay.
   - Recommended first answer: no; keep deletion behavior expense-centric to match the current model and the existing soft-deleted-expense workflow.
   - Recommended first answer: no; keep deletion behavior expense-centric to match the current model.
   - Recommended first answer: no; keep deletion behavior expense-centric to match the current model.

5. **Should attachment/receipt download endpoints remain public?**
   - Recommended first answer: review and likely tighten access for private account data.

---

## Summary

2. add an Expense Trash for soft-deleted expenses

2. add a Receipt Trash for soft-deleted expenses/receipts
3. add full user-data export
4. add in-app account deletion
The key architectural decision is to treat deleted receipts as deleted expenses with linked files, because that matches the existing persistence and file-storage model. That keeps the implementation practical, testable, and consistent with the rest of the application.
The key architectural decision is to build on the already-implemented soft-deleted expense model, while ensuring permanent deletion also cleans up linked receipt files and attachments. That keeps the implementation practical, testable, and consistent with the rest of the application.

The User Account page can be improved safely and incrementally by implementing the work in phases:

