# AI Usage — Implementation Plan

## Goal
Add user-level AI settings, monthly AI usage quotas, admin management, and per-user provider/model selection without breaking the current chat and OCR flows.

This plan is tailored to the current codebase:
- Backend: Spring Boot + JPA + session-based auth
- Frontend: static SPA in `src/main/resources/static/js/*.js`
- Current AI wiring:
  - Chat uses Spring AI `ChatClient` in `ChatService`
  - OCR uses direct HTTP calls in `OcrService`
  - Provider selection is currently application-wide via Spring profiles in `AiConfig`, `application-ollama.yml`, and `application-openai.yml`
- Current auth/role state:
  - `User` has no role field yet
  - `SessionAuthenticationFilter` always grants `ROLE_USER`
  - `/api/auth/me` does not expose role or AI settings
- Current UI state:
  - Profile page is rendered in `static/js/auth.js`
  - No admin route/page exists yet
  - Chat and scan UIs do not show usage/quota status

---

## Scope

### User entity
Add:
- `role` — enum: `USER`, `ADMIN`
- `ai_model` — nullable; when null, fall back to the default model from app configuration

### AI usage entity
Add a monthly usage table/entity with:
- `type` — enum: `OCR`, `CHAT`
- `user_id`
- `month_year`
- `usage_count`
- `quota`

### UI changes
- Profile page: allow the user to override the AI model used for their account
- Admin page: create a dedicated page for admin users
- Header/status bar: display monthly chat and OCR usage vs quota
- Enforce quota in UX:
  - disable receipt scan when OCR quota is exceeded
  - disable chat send when chat quota is exceeded and show a warning

### Platform changes
- Support both Ollama and OpenAI simultaneously for different users

---

## Design Decisions

### 1 — Keep `ai_model` nullable and use config fallback
**Decision:** `users.ai_model` remains nullable. `null` means: use the configured default model.

Why:
- Matches the requested data model.
- Makes rollout safe for existing users.
- Allows global default changes without touching every row.

Behavior:
- `null` → use default configured model for the relevant feature
- non-null → use that model for the user

---

### 2 — Use a config-backed model catalog rather than adding `ai_provider` to `users`
**Decision:** infer provider from the selected model via configuration, instead of adding a new `ai_provider` column.

Why:
- The request explicitly asks for `ai_model`, not `ai_provider`.
- A model registry can map each supported model to a provider (`OLLAMA` or `OPENAI`).
- This is enough to support different providers for different users as long as each model name is unique in config.

Example concept:
```yaml
app:
  ai:
    models:
      - id: gemma4:latest
        provider: OLLAMA
        supports-chat: true
        supports-ocr: true
      - id: qwen3.5-397b-a17b
        provider: OPENAI
        supports-chat: true
        supports-ocr: true
    defaults:
      chat-model: gemma4:latest
      ocr-model: gemma4:latest
```

Notes:
- If product requirements later need provider-specific user credentials or the same model id across multiple providers, add `ai_provider` in a future phase.
- For now, the model catalog keeps the schema smaller.

---

### 3 — Track usage monthly by `(user, type, month)`
**Decision:** store one row per user, per usage type, per month.

Recommended schema shape:
```sql
CREATE TABLE ai_usage (
    id           BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    type         VARCHAR(20) NOT NULL,      -- OCR | CHAT
    month_year   VARCHAR(7) NOT NULL,       -- yyyy-MM
    usage_count  INTEGER NOT NULL,
    quota        INTEGER NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    UNIQUE (user_id, type, month_year)
);
```

Why:
- Easy to query for the current month.
- Natural monthly reset without background jobs.
- `quota` is stored as a snapshot so historical reports remain correct even if defaults change later.

---

### 4 — Count usage at the service boundary, not only in controllers
**Decision:** enforce quota and increment usage inside services used by the actual AI execution path.

Why:
- Prevents bypassing checks if a future controller or background job reuses the service.
- Keeps the quota logic close to the real AI call.

Recommended enforcement points:
- `ChatService.processUserMessage(...)` → check/increment `CHAT`
- `ExpenseController` or `ExpenseService` before scan creation, plus `OcrService` before outbound OCR request → check/increment `OCR`

Preferred rule:
- Check quota before the external AI call
- Increment usage only when the request is accepted for processing
- Decide explicitly whether retries count toward quota; recommended initial behavior: **one accepted user action = one quota unit**

---

### 5 — Introduce admin-only APIs and UI from the start
**Decision:** admin capabilities should not be mixed into the profile page.

Why:
- Keeps normal user experience simple.
- Makes access control easier.
- Leaves room for broader admin tooling later.

---

## Proposed Domain Model

### `User`
Add:
- `role: UserRole`
- `aiModel: String`

Recommended enum:
```java
public enum UserRole {
    USER,
    ADMIN
}
```

Rules:
- Existing users default to `USER`
- Registration creates `USER`
- `ADMIN` is assigned only via DB migration, bootstrap config, or admin tooling

---

### `AiUsage`
New entity fields:
- `id`
- `userId`
- `type`
- `monthYear`
- `usageCount`
- `quota`
- `createdAt`
- `updatedAt`

Recommended enum:
```java
public enum AiUsageType {
    OCR,
    CHAT
}
```

Optional but recommended later:
- `lastUsedAt`
- `provider`
- `model`

Those are not required for the first version because the user request only needs quota tracking and status display.

---

## Config Model

### New config block
Add an application config section for:
- available models
- provider per model
- default model(s)
- monthly quotas

Suggested structure:
```yaml
app:
  ai:
    defaults:
      chat-model: gemma4:latest
      ocr-model: gemma4:latest
    quotas:
      chat-monthly: 200
      ocr-monthly: 100
    models:
      - id: gemma4:latest
        label: Gemma 4 (Ollama)
        provider: OLLAMA
        supports-chat: true
        supports-ocr: true
      - id: qwen3.5-397b-a17b
        label: Qwen 3.5 (OpenAI)
        provider: OPENAI
        supports-chat: true
        supports-ocr: true
```

Why this matters:
- The frontend can fetch a safe, filtered list of allowed models.
- The backend can resolve provider + model centrally.
- Different users can use different providers without switching Spring profiles.

---

## Major Architectural Change Needed

## Current limitation
Right now provider selection is effectively global:
- `AiConfig` marks one `ChatModel` bean as primary based on Spring profile
- OCR provider/config is chosen from app config / profile-wide settings

That setup does **not** support:
- user A on Ollama
- user B on OpenAI
in the same running application instance.

## Required target state
Refactor AI selection so both providers are registered at once and chosen per request.

### Chat target design
Introduce a resolver/factory, for example:
- `AiModelCatalog`
- `ChatModelResolver`
- `UserAiSettingsService`

Flow:
1. Resolve current user's effective model:
   - user override from `users.ai_model`, else app default
2. Look up model metadata from config
3. Resolve provider-specific chat client/model
4. Execute the request with that provider

### OCR target design
Do the same for OCR:
- `OcrModelResolver`
- provider-specific request builder/client selected from the resolved model metadata

This avoids tying OCR/chat behavior to global Spring profiles.

---

## Phases

---

### Phase 1 — Data Model & Security Foundation

**Goal:** add the minimum persistence and auth primitives needed for roles and AI settings.

#### 1.1 User schema changes
Update `users` with:
- `role` `VARCHAR(20) NOT NULL DEFAULT 'USER'`
- `ai_model` `VARCHAR(...) NULL`

#### 1.2 New AI usage table
Create `ai_usage` table with unique key on:
- `(user_id, type, month_year)`

#### 1.3 Java model layer
Create/update:
- `model/UserRole.java` — new enum
- `model/AiUsage.java` — new entity
- `model/AiUsageType.java` — new enum
- `repository/AiUsageRepository.java`

Repository methods should include:
- `findByUserIdAndTypeAndMonthYear(...)`
- `findByUserIdAndMonthYear(...)`
- aggregate/report queries for admin usage pages

#### 1.4 Auth propagation
Update security/auth wiring so role is no longer hardcoded:
- `SessionAuthenticationFilter` must load the user role from DB/session context
- `SecurityConfig.userDetailsService()` must emit `ROLE_USER` or `ROLE_ADMIN` from persisted role
- `AuthController /api/auth/me` must return role and AI settings

#### 1.5 DTO changes
Extend:
- `UserProfileResponse`
- `UpdateProfileRequest`
- login/me response payloads if needed by the UI

#### 1.6 Bootstrap/admin seeding
Define how the first admin user is created.

Recommended options:
- one-time SQL/manual DB update in non-prod
- `app.admin.bootstrap-username` config for local/dev only

#### 1.7 Tests
- migration test or startup validation for new columns/table
- auth tests verifying admin role is propagated
- `/api/auth/me` response test includes `role` and `aiModel`

**Exit criteria:**
- Existing users can still log in
- all users have a role
- backend exposes user role and AI model override safely

---

### Phase 2 — AI Model Catalog & Per-User Provider Selection

**Goal:** support both Ollama and OpenAI in the same running app, selected per user.

#### 2.1 Introduce config-backed model catalog
Create config classes such as:
- `AiProperties`
- `AiModelDefinition`
- `AiProviderType`

Responsibilities:
- expose allowed models to backend services
- provide defaults for chat and OCR
- declare provider per model

#### 2.2 Register both providers concurrently
Refactor `AiConfig` so the app can create both provider clients/models without relying on mutually exclusive Spring profiles.

Target result:
- Ollama chat support available at runtime
- OpenAI chat support available at runtime
- OCR provider implementations available at runtime

#### 2.3 Add model resolution services
Create services like:
- `UserAiSettingsService`
- `ChatModelResolver`
- `OcrModelResolver`

Rules:
- if `user.aiModel` is null → use configured default
- if `user.aiModel` is non-null but not in catalog → reject update or fall back with warning
- only expose models allowed for the relevant feature

#### 2.4 Refactor chat execution path
Update `ChatService` so it no longer depends on one globally primary chat model.

Instead:
- resolve the effective model for the user
- choose the correct provider-specific client/model
- send the prompt with that client

#### 2.5 Refactor OCR execution path
Update `OcrService` so it resolves model/provider per user or per expense owner before calling the external API.

Important:
- queued OCR jobs must persist enough context to resolve the same user's effective model later
- avoid relying on whichever Spring profile was active at startup

#### 2.6 Public API for model options
Add an authenticated endpoint, for example:
- `GET /api/user/ai/models`

Returns:
- allowed model ids/labels
- which models support `CHAT`, `OCR`, or both
- defaults currently configured

#### 2.7 Tests
- resolver tests for null override vs explicit override
- chat path tests for one user on Ollama and another on OpenAI
- OCR path tests for provider selection
- invalid model update tests

**Exit criteria:**
- two users can use different providers/models in the same deployment
- model selection is deterministic and config-driven

---

### Phase 3 — Usage Tracking & Quota Enforcement Backend

**Goal:** centrally measure usage and prevent over-quota AI actions.

#### 3.1 Create `AiUsageService`
Core methods:
```java
AiUsageStatus getCurrentStatus(long userId);
AiUsageStatus getStatus(long userId, YearMonth month);
QuotaCheckResult checkQuota(long userId, AiUsageType type);
AiUsage consume(long userId, AiUsageType type);
AiUsage ensureCurrentMonthRow(long userId, AiUsageType type);
```

Recommended DTOs:
- `AiUsageStatus`
- `AiUsageLineDto`
- `QuotaCheckResult`

#### 3.2 Quota source of truth
Monthly quota should come from config unless overridden later by admin tooling.

Initial behavior:
- for a new month, create row with quota snapshot from config
- usage starts at 0

Suggested config:
```yaml
app:
  ai:
    quotas:
      chat-monthly: 200
      ocr-monthly: 100
```

#### 3.3 Enforce chat quota
Before sending to LLM in `ChatService`:
- load/check `CHAT` quota
- if exceeded, return a domain error/exception that the controller maps cleanly
- if allowed, increment usage for the accepted message

Suggested API behavior when exceeded:
- HTTP `429 Too Many Requests` or `403 Forbidden`
- JSON body with machine-readable code such as `AI_CHAT_QUOTA_EXCEEDED`

#### 3.4 Enforce OCR quota
Before scan processing starts:
- check `OCR` quota
- block creation of new scan requests if exceeded
- for batch uploads, define behavior explicitly

Recommended batch rule:
- reject the entire batch if remaining OCR quota is less than number of files
- return clear error showing remaining quota

Alternative:
- partially accept only remaining files

For predictability, the first option is better.

#### 3.5 Decide retry/cancel semantics
Document whether these count toward quota:
- failed LLM/OCR provider call
- retry after transient error
- invalid user input

Recommended initial policy:
- accepted chat message counts once
- accepted scan file counts once
- internal retries do not count extra

#### 3.6 Usage status endpoints
Add authenticated endpoints such as:
- `GET /api/user/ai/usage/current`
- `GET /api/user/ai/status`

Return:
- effective model
- chat usage/quota for current month
- OCR usage/quota for current month
- boolean flags such as `chatAllowed`, `ocrAllowed`

#### 3.7 Tests
- row creation on first use of month
- usage increment race/concurrency tests
- quota reached / just-under-limit / over-limit cases
- batch scan behavior tests
- controller response tests for exceeded quota

**Exit criteria:**
- backend always knows current monthly usage
- backend blocks over-quota chat and OCR consistently

---

### Phase 4 — Profile Page & User-Facing Status UI

**Goal:** let users see their limits and select an allowed model.

#### 4.1 Extend `/api/auth/me`
Return additional fields needed by the SPA:
- `role`
- `aiModel`
- maybe `effectiveAiModel` if you want UI to show fallback clearly

#### 4.2 Profile page changes
Update `static/js/auth.js` profile form to include:
- AI model dropdown/select
- helper text such as:
  - "Leave empty to use default model"
  - or explicit default label in the dropdown

Recommended UX:
- first option: `Default (Gemma 4 / Ollama)`
- other options: allowed models from `/api/user/ai/models`

Validation:
- user may only pick from models exposed by the backend

#### 4.3 Status bar / header widget
Add a compact usage display in the existing navigation/header area.

Suggested display:
- `Chat: 34 / 200`
- `OCR: 8 / 100`

Possible placements:
1. **Header / navbar** — best for persistent visibility
2. Profile page only — too hidden
3. Desktop header + mobile compact card — recommended

Recommended implementation:
- desktop: small pill badges in `index.html` navbar
- mobile: compact status strip above or below the tab bar/home hero

#### 4.4 Shared frontend state
Add a client-side loader that fetches current AI status once after auth check and refreshes after:
- sending chat
- scanning receipt(s)
- updating profile AI model

#### 4.5 Disable chat when quota exceeded
Update `static/js/chat.js`:
- show warning banner/message in the chat panel
- disable textarea and send button when chat is blocked
- preserve ability to read chat history

Suggested copy:
- "Your monthly chat quota has been reached. Chat will be available again next month."

#### 4.6 Disable scan when quota exceeded
Update `static/js/expense-new.js` and mobile scan flow:
- disable upload zone
- disable camera button
- show remaining OCR quota if available

Suggested copy:
- "Your monthly OCR quota has been reached. Receipt scanning is temporarily unavailable."

#### 4.7 Tests
- frontend/manual QA for desktop and mobile layouts
- profile update flow reflects selected model
- quota banner visibility
- disabled state after hitting the limit

**Exit criteria:**
- users can see usage and model selection
- UI prevents further AI actions when quota is exhausted

---

### Phase 5 — Admin APIs & Admin Page

**Goal:** provide operational visibility and control for admins.

#### 5.1 Role-based access control
Add method security with admin-only checks, for example:
- `@PreAuthorize("hasRole('ADMIN')")`

Apply to all admin endpoints.

#### 5.2 Admin endpoints
Recommended initial admin API surface:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/admin/users` | List users with role, ai model, usage summary |
| `GET` | `/api/admin/users/{id}` | View one user |
| `PATCH` | `/api/admin/users/{id}/role` | Promote/demote user |
| `PATCH` | `/api/admin/users/{id}/ai-model` | Override/reset model |
| `GET` | `/api/admin/ai/usage` | Monthly usage overview |
| `GET` | `/api/admin/ai/usage/users/{id}` | Per-user usage detail |

Optional later:
- update user-specific quotas
- reset current month usage
- export usage as CSV

#### 5.3 Admin page route
Add new SPA route, for example:
- `#/admin`

Update:
- `router.js`
- navbar/drawer visibility logic

Only show the admin nav item when `currentUser.role === 'ADMIN'`.

#### 5.4 Admin page contents
Recommended first version:
- user table with:
  - username
  - role
  - selected AI model
  - effective provider
  - chat usage/quota this month
  - OCR usage/quota this month
- quick filters:
  - role
  - quota exceeded
  - model/provider

#### 5.5 Guardrails
- admins should not be able to remove their own last-admin access accidentally
- audit significant admin actions via structured logs

#### 5.6 Tests
- non-admin access denied
- admin page nav hidden for regular users
- admin role update flow
- list/report endpoints return correct usage aggregates

**Exit criteria:**
- admin users can view and manage AI settings operationally
- non-admin users cannot access admin features

---

### Phase 6 — Hardening, Reporting & Cleanup

**Goal:** make the feature production-ready and maintainable.

#### 6.1 Concurrency safety
Protect usage increments against race conditions.

Options:
- optimistic locking on `AiUsage`
- transactional upsert/update query
- database-level increment statement

Recommended:
- atomic update in repository/service transaction

#### 6.2 Error model consistency
Standardize backend error codes for:
- `AI_CHAT_QUOTA_EXCEEDED`
- `AI_OCR_QUOTA_EXCEEDED`
- `AI_MODEL_NOT_ALLOWED`
- `ADMIN_ONLY`

#### 6.3 Observability
Add logs/metrics for:
- per-user/provider/model usage events
- quota denials
- provider selection decisions
- latency and error rates by provider

#### 6.4 Monthly reporting helpers
Add admin summaries such as:
- total chat calls this month
- total OCR calls this month
- users over 80% quota
- users exceeded quota
- usage split by provider/model

#### 6.5 Documentation
Update or add:
- admin usage guide
- config reference for models/quotas
- testing notes for mixed-provider environments
- migration notes for existing users

#### 6.6 Tests
- mixed provider integration tests
- quota overflow edge cases
- UI regression for mobile/desktop
- security tests for admin-only APIs

**Exit criteria:**
- quota tracking is robust under load
- admins can operate the feature confidently
- behavior is documented and observable

---

## Suggested API Summary

### User-facing
| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/auth/me` | Required | Return role + AI settings |
| `PUT` | `/api/user/profile` | Required | Update profile including AI model override |
| `GET` | `/api/user/ai/models` | Required | List allowed models |
| `GET` | `/api/user/ai/status` | Required | Current model + monthly usage/quota |

### Existing APIs affected
| Method | Path | Change |
|---|---|---|
| `POST` | `/api/chat` | Enforce chat quota before processing |
| `POST` | `/api/expenses/scan` | Enforce OCR quota before accepting scan |
| `POST` | `/api/expenses/scan/batch` | Enforce OCR quota against batch size |

### Admin-facing
| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/admin/users` | Admin | List users and AI settings |
| `PATCH` | `/api/admin/users/{id}/role` | Admin | Update role |
| `PATCH` | `/api/admin/users/{id}/ai-model` | Admin | Update/reset AI model |
| `GET` | `/api/admin/ai/usage` | Admin | Monthly usage overview |
| `GET` | `/api/admin/ai/usage/users/{id}` | Admin | Per-user usage details |

---

## Suggested Files to Create / Modify

### Backend
| File | Action |
|---|---|
| `model/User.java` | add `role`, `aiModel` |
| `model/UserRole.java` | new |
| `model/AiUsage.java` | new |
| `model/AiUsageType.java` | new |
| `repository/AiUsageRepository.java` | new |
| `config/AiProperties.java` | new |
| `config/AiConfig.java` | refactor to support both providers concurrently |
| `config/SecurityConfig.java` | role-aware authorities |
| `config/SessionAuthenticationFilter.java` | populate persisted role |
| `controller/AuthController.java` | expose role + AI settings in `/me` |
| `controller/UserController.java` | accept AI model update |
| `controller/ChatController.java` | return quota errors cleanly |
| `controller/ExpenseController.java` | reject over-quota scan requests |
| `service/ChatService.java` | provider/model resolution + quota enforcement |
| `service/OcrService.java` | provider/model resolution + quota enforcement |
| `service/AiUsageService.java` | new |
| `service/UserAiSettingsService.java` | new |
| `service/ChatModelResolver.java` | new |
| `service/OcrModelResolver.java` | new |
| `dto/auth/UserProfileResponse.java` | add role + ai model fields |
| `dto/auth/UpdateProfileRequest.java` | add ai model field |

### Frontend
| File | Action |
|---|---|
| `static/index.html` | add header/status placeholders and admin nav entry |
| `static/js/router.js` | add admin route, load AI status after auth |
| `static/js/auth.js` | extend profile page with AI model selector |
| `static/js/chat.js` | show quota warning and disable send |
| `static/js/expense-new.js` | disable scan UI when OCR quota exceeded |
| `static/js/utils.js` | shared fetch/state helpers for AI status |
| `static/js/admin.js` | new admin page renderer |
| `static/css/navbar.css` | style usage badges/status strip |
| `static/css/base.css` or new CSS file | admin page styling |

### Config / docs
| File | Action |
|---|---|
| `application.yml` | add AI model catalog + quota defaults |
| `application-ollama.yml` | align with multi-provider runtime design |
| `application-openai.yml` | align with multi-provider runtime design |
| `docs/architecture.md` | update AI provider selection notes |
| `docs/testing.md` | add quota/provider test cases |

---

## Recommended Rollout Order

If this is implemented incrementally, use this order:

1. **Phase 1** — schema + roles + `/me` payload
2. **Phase 2** — per-user model/provider resolution
3. **Phase 3** — backend quota tracking/enforcement
4. **Phase 4** — profile page + status bar + feature disabling
5. **Phase 5** — admin page and admin APIs
6. **Phase 6** — hardening and reporting

This order minimizes risk because:
- data model is in place first
- provider routing is solved before the UI exposes it
- quotas are enforced in backend before the UI relies on them

---

## Open Questions to Resolve Before Implementation

1. **Should one `ai_model` apply to both chat and OCR, or should they be split later?**
   - This plan assumes one override for both.
   - If product wants separate chat/OCR choices, add `chat_ai_model` and `ocr_ai_model` later.

2. **Should admins have unlimited quota?**
   - Recommended initial answer: no, use explicit quotas unless configured otherwise.

3. **Should failed provider calls consume quota?**
   - Recommended initial answer: accepted user action counts once; internal retries do not.

4. **Should quotas be global defaults only, or per-user configurable by admin?**
   - This plan starts with global defaults and quota snapshots in `ai_usage`.
   - Per-user override can be a later enhancement.

5. **What should happen when a selected model is removed from config?**
   - Recommended initial answer: fall back to default and surface a warning in logs/admin UI.

---

## Summary

This work is best split into six phases:
1. data model + roles
2. per-user provider/model resolution
3. usage tracking + quota enforcement
4. profile/status UI
5. admin tools
6. hardening/reporting

The most important technical change is replacing the current profile-wide AI provider selection with a runtime resolver that can choose Ollama or OpenAI per user. Once that exists, quota tracking and UI controls become straightforward additions on top.

