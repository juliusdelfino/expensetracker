# Internationalization Implementation Plan (English + German)

## Checklist
- [ ] Inventory all user-facing text in frontend, backend responses, reports, and share views
- [ ] Introduce i18n foundations in backend and frontend (locale resolution + translation runtime)
- [ ] Migrate modules in phases with stable translation keys and fallback strategy
- [ ] Add locale-aware tests and rollout safeguards
- [ ] Ensure adding a third language is primarily configuration + translation data

## 1) Objectives and Non-goals

### Objectives
- Add full user-facing i18n support for English (`en`) and German (`de`).
- Cover SPA UI text, API/user-facing error messages, reports/charts labels, and share/public pages.
- Keep existing API behavior compatible while introducing locale-aware responses.
- Make adding a new language later mostly data/config work.

### Non-goals
- Do not translate internal operator logs/stack traces.
- Do not translate user-generated content (expense notes, user-entered category labels, chat history).
- Do not attempt to translate third-party map tile labels controlled by providers.
- Do not replatform frontend architecture.

## 2) Current-State Areas to Scan

### Frontend text hotspots
- `src/main/resources/static/index.html`
- `src/main/resources/static/js/auth.js`
- `src/main/resources/static/js/router.js`
- `src/main/resources/static/js/dashboard-widgets.js`
- `src/main/resources/static/js/expenses.js`
- `src/main/resources/static/js/expense-detail.js`
- `src/main/resources/static/js/expense-dialogs.js`
- `src/main/resources/static/js/reports.js`
- `src/main/resources/static/js/chat.js`
- `src/main/resources/static/js/admin.js`
- `src/main/resources/static/js/utils.js`

### Backend text hotspots
- `src/main/java/com/delfino/expensetracker/controller/*.java`
- `src/main/java/com/delfino/expensetracker/service/*.java`
- `src/main/java/com/delfino/expensetracker/exception/*.java`
- `src/main/java/com/delfino/expensetracker/config/*.java`

### Report/share/country-related hotspots
- `src/main/java/com/delfino/expensetracker/service/ReportService.java`
- `src/main/java/com/delfino/expensetracker/service/ReportPdfService.java`
- `src/main/java/com/delfino/expensetracker/service/ReportInsightService.java`
- `src/main/java/com/delfino/expensetracker/controller/ShareController.java`
- `src/main/resources/country-mapping.yml`

## 3) Recommended Architecture

### Frontend i18n architecture
- Add a single translation wrapper (for example `t(key, params)`) in `utils.js`.
- Use namespaced locale JSON files under:
  - `src/main/resources/static/locales/en/*.json`
  - `src/main/resources/static/locales/de/*.json`
- Re-render current route/view on language switch.
- Avoid hardcoded strings in feature modules after migration.

### Backend i18n architecture
- Add Spring message bundles:
  - `src/main/resources/messages_en.properties`
  - `src/main/resources/messages_de.properties`
- Centralize locale resolution with clear fallback to `en`.
- Return stable machine-readable error `code` and localized user-facing message text.

### API contract guidance
- Keep existing fields to avoid breaking clients.
- Add/standardize message metadata for frontend rendering consistency (`messageKey`, optional args).
- Include `Content-Language` response header where practical.

## 4) Library Recommendations

### Frontend
- `i18next`
- `i18next-http-backend`
- `i18next-browser-languagedetector`
- Optional: `i18next-icu` (if advanced pluralization/gender rules are required)

### Backend
- Spring `MessageSource`
- `AcceptHeaderLocaleResolver` or a custom resolver to include user profile preference
- Hibernate Validator message bundle integration for validation errors

## 5) Translatable Asset Inventory

### Texts/UI copy
- Navigation labels, buttons, dialogs, tooltips, placeholders, toasts, empty states, onboarding/help copy.

### Constants and domain labels
- User-facing labels for enums and statuses (e.g., expense status/type labels shown in UI).
- Report grouping labels and filter labels.

### Errors and validation
- Backend exceptions and error messages.
- Bean validation messages.
- Auth/account/profile-related API messages.

### Reports/charts
- Chart titles, axis labels, legend items, series names, export labels, PDF section titles.

### Maps and geocoding
- Map UI controls/popups and geocoding helper text.
- Request language for geocoding endpoints should follow active locale.

### Images and visual assets
- Any image with embedded text (replace with locale-specific variants or remove baked-in text).
- OCR-related overlay/status text in receipt views.

### Share/public pages
- OpenGraph/share titles/descriptions and public page static text.

### Notifications/templates
- Future-facing placeholder for email/notification templates so localization can be added without redesign.

## 6) Key Strategy and Conventions

- Use dot-separated keys: `module.section.element.state`.
- Keep keys semantic and stable (do not use English sentence text as keys).
- Maintain `en` as canonical source locale.
- Separate reusable common keys (for example `common.actions.save`) from feature-specific keys.
- Add CI checks for missing keys and unused keys.

## 7) Locale Negotiation and Fallback

Priority order:
1. Explicit user profile language preference
2. Optional URL/query override (debug/support)
3. `Accept-Language`
4. Default `en`

Fallback chain examples:
- `de-DE -> de -> en`
- `en-US -> en`

## 7.1) Formatting Standards (Must Be Locale-Aware)

- Date-only: use locale date formatting (example: short month/day/year per active locale).
- Date-time: use locale formatting with date + time (hour/minute, locale-sensitive 12h/24h behavior).
- Month labels (reports/charts): use locale month names (`Jan 2026` vs `Jan. 2026` / localized full month names where space allows).
- Currency: use locale-aware currency formatting (`Intl.NumberFormat` in frontend, locale-aware formatting in backend exports where rendered text is user-facing).
- Number/grouping: use locale thousands and decimal separators for user-visible aggregates.
- Non-goal exception remains: internal logs/audit internals may remain English and non-localized.

## 8) Phased Implementation Plan

## Phase 0 - Discovery and design
- Build translation inventory from all hotspots.
- Confirm key namespace taxonomy and glossary (expense/receipt/report terms).
- Mark non-translatable data classes.

Exit criteria:
- Inventory complete and reviewed.
- Naming convention approved.

## Phase 1 - Backend foundations
- Add message bundles and locale resolver.
- Add user language preference field in profile model/DTO/API.
- Refactor global exception handling to code + localized message.

Exit criteria:
- Backend returns locale-aware messages for `en` and `de`.
- Fallback to `en` verified.

## Phase 2 - Frontend foundations
- Initialize i18n runtime and locale file loading.
- Add language switcher (profile/settings).
- Create base namespace JSON files for `en` and `de`.

Exit criteria:
- Language can be switched at runtime.
- Core shell/navigation text is localized.

## Phase 3 - Feature migration
- Migrate modules: auth, dashboard, expenses, expense detail/dialogs, reports, chat, admin, legal/help pages.
- Replace hardcoded literals and apply locale-aware formatting wrappers.

Exit criteria:
- No hardcoded user-facing English text in migrated modules.

## Phase 4 - Reports/maps/share hardening
- Localize report/PDF labels and insights.
- Localize share/public page boilerplate.
- Ensure geocoding request language and map-adjacent UI use active locale.

Exit criteria:
- Reports and share flows match selected locale.

## Phase 5 - Testing and rollout
- Update brittle tests asserting fixed English strings.
- Add integration tests for `en`, `de`, and fallback behavior.
- Roll out behind feature flag and monitor missing keys/fallback rates.

Exit criteria:
- Regression suite passes.
- Rollout metrics are healthy.

## 9) Testing Strategy

### Unit tests
- Translation helper wrappers and formatting helpers.
- Locale resolver and message resolution logic.

### Integration tests
- API responses under different locales (`Accept-Language`, profile locale).
- Validation/error payload localization.

### End-to-end tests
- Login/profile language change, expense CRUD, dashboard, reports, share flow in both locales.
- UI checks for untranslated literals in critical views.

## 10) Rollout and Operational Considerations

- Add `app.i18n.enabled` feature toggle.
- Start with internal/canary users, then gradual rollout.
- Track metrics:
  - missing translation keys
  - fallback usage
  - locale distribution
  - i18n initialization failures
- Add runbook section in docs for translation updates and rollback.

## 11) Definition of Done

- English and German are selectable and persisted per user.
- Core user journeys are fully localized.
- API errors preserve stable codes and localized user-facing messages.
- Reports/charts/share pages reflect selected locale.
- Fallback behavior works and is tested.
- Adding a new language requires only new locale files, message bundle, and locale config registration.

## 12) How to Add a New Language Later

1. Add locale code to supported locale configuration.
2. Create frontend locale namespaces under `static/locales/<new-locale>/`.
3. Add backend bundle `messages_<new-locale>.properties`.
4. Run key completeness checks and locale tests.
5. Validate date/number/currency formatting for the new locale.
6. Update translator glossary for domain terms.

## Suggested Ownership Split
- Backend i18n foundation: backend team
- Frontend i18n runtime + module migration: frontend team
- Glossary/translation QA: product + QA
- Rollout/monitoring: platform/ops


