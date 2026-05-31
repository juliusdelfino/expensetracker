# Architecture

## Overview

Expense Tracker is a monolithic Spring Boot 3.5 application running on Java 21.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21, Spring Boot 3.5 |
| Web | Spring MVC, vanilla JavaScript (SPA-style) |
| Security | Spring Security (session-based) |
| Persistence | Spring Data JPA, PostgreSQL |
| AI | Spring AI (Ollama + OpenAI) |
| Logging | Log4j2 (YAML config) |
| Build | Maven, Assembly plugin for deployment packaging |
| Testing | JUnit 5, WireMock, Embedded PostgreSQL (Zonky) |

## Package Structure

```
com.delfino.expensetracker
├── config/          # Spring configuration & beans
├── controller/      # REST controllers (@RestController)
├── dto/             # Request/response DTOs
├── model/           # JPA entities
├── repository/      # Spring Data JPA repositories
├── service/         # Business/service layer
├── businesslogic/   # Domain logic (non-Spring)
├── migration/       # Data migration utilities
└── util/            # Shared utilities
```

## Layered Architecture

```
Controller → Service → Repository → Database
                ↘ BusinessLogic ↗
```

### Rules

1. **Controllers** handle HTTP concerns only (validation, serialization, status codes). No business logic.
2. **Services** orchestrate business operations, manage transactions, and call repositories.
3. **Repositories** are Spring Data JPA interfaces — no custom SQL unless strictly necessary.
4. **DTOs** are used at controller boundaries. Entities must never be exposed directly in API responses.
5. **BusinessLogic** contains pure domain logic with no Spring dependencies.

## Frontend

- Static resources served from `src/main/resources/static/`.
- Vanilla JavaScript — no build step, no framework.
- Communicates with backend exclusively via REST API (`/api/**`).

### Internationalisation (i18n)

- Use **[i18next](https://www.i18next.com/)** (browser bundle, no build tooling needed).
- Translation files: `static/locales/{lang}.json` (e.g. `en.json`, `de.json`).
- All user-visible strings must go through `i18next.t('key')` — no hardcoded UI text.
- Translations are loaded at app init; language switching triggers a re-render of the current view.
- **Do not migrate to React solely for i18n** — vanilla JS + i18next covers all requirements without a build pipeline.

## Data Storage

- **PostgreSQL** is the primary database.
- Receipt files stored on the local filesystem (`data/receipts/`).

## External Integrations

- **Nominatim** (OpenStreetMap) for geocoding/store address lookup.
- **Ollama / OpenAI** via Spring AI for receipt OCR/parsing.
- **MCP Server** exposed via WebMVC SSE transport.


