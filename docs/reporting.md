# Reporting — Implementation Plan

## Goal
Add a persistent reporting feature that lets users generate, view, re-open, and export expense reports.

Each report should contain:
- a list of expenses
- aggregate figures
- charts
- insights
- filters/grouping metadata so the report can be reproduced later
- PDF export

This plan is tailored to the current codebase:
- Backend: Spring Boot 3.5 + Spring MVC + Spring Data JPA
- Frontend: static SPA in `src/main/resources/static/js/*.js`
- Existing analytics surface:
  - dashboard data is already produced by `DashboardController`
  - dashboard trip/discovery cards already group expenses by place + month
  - charts are rendered client-side with Chart.js in `dashboard-widgets.js`
- Existing expense filtering surface:
  - `ExpenseController` already supports `search`, `startDate`, `endDate`, `category`, and `country`
  - `DashboardController` already supports richer filters including `storeName` and keyword search
- Existing chat surface:
  - chat runs through `ChatService`
  - Spring AI tools already exist in `service/mcp/*`
- Existing export surface:
  - expenses can already be exported as JSON/CSV via `GET /api/expenses/export`
  - PDFBox already exists in `pom.xml`, but there is no report PDF rendering pipeline yet

---

## Scope

### User-facing capabilities
- Generate a report from filtered expenses
- Group reports by:
  - store location
  - category
  - keyword
  - date range
- View saved reports on a dedicated Reports page
- Open a report from dashboard trip cards via **View Report**
- Generate a report from chat
- Export a report as PDF

### Data persistence
Create a new reports table with at least these columns:
- `id`
- `title`
- `description`
- `expense_ids`
- `chart_definitions`

Recommended additional columns for a usable production feature:
- `user_id`
- `group_by`
- `filter_snapshot`
- `insights`
- `created_at`
- `updated_at`

---

## Design Decisions

### 1 — Persist reports instead of generating them only on demand
**Decision:** a report should be saved and reopenable later.

Why:
- The user explicitly asked for a page containing generated reports.
- Trip cards need stable deep links.
- Chat-triggered reports should create an object the UI can open later.
- PDF export becomes much simpler when there is a durable report record.

Behavior:
- User generates a report from filters or chat.
- Backend persists the report definition and the list of included expense IDs.
- UI navigates to that report detail page.

---

### 2 — Store report membership as a snapshot of expense IDs
**Decision:** persist the exact included expense IDs at generation time.

Why:
- A saved report should not silently change when the user later edits filters or data.
- PDF export should reflect the report the user actually generated.
- This matches the requested schema: “list of expense ids”.

Recommended rule:
- `expense_ids` is a snapshot of included expense primary keys at generation time.
- Aggregates/charts are generated from that snapshot.
- A future enhancement can add “refresh report” to re-run the original filters.

---

### 3 — Keep chart configuration in data, not hardcoded per page
**Decision:** persist chart definitions with each report.

Why:
- The requirement explicitly asks for `list of chart definitions`.
- Reports may need different chart sets depending on grouping.
- PDF export can render the same chart selections as the web page.

Suggested chart definition shape:
```json
[
  {
    "id": "spend-by-category",
    "type": "DOUGHNUT",
    "title": "Spend by Category",
    "metric": "TOTAL_AMOUNT",
    "groupBy": "CATEGORY",
    "limit": 10,
    "sort": "DESC"
  },
  {
    "id": "daily-spending",
    "type": "LINE",
    "title": "Daily Spending",
    "metric": "TOTAL_AMOUNT",
    "groupBy": "DAY"
  }
]
```

---

### 4 — Start with rule-based insights, keep AI-generated insights optional
**Decision:** first version insights should be deterministic and derived from data.

Examples:
- top category
- top store/location
- highest single expense
- average spend per day
- number of active days in the report
- biggest increase/decrease period

Why:
- Predictable and testable
- No extra AI cost required for the core reporting feature
- Easier to export to PDF reliably

Optional later:
- add AI-written narrative summaries on top of deterministic insights
- expose “Explain this report” in chat

---

### 5 — Reuse existing filter semantics from dashboard/expenses
**Decision:** report generation should reuse the current filtering model instead of inventing a separate one.

Baseline filters to support from day one:
- `startDate`
- `endDate`
- `category`
- `country`
- `storeName`
- `search` keyword
- `groupBy`

Why:
- `DashboardController` and dashboard widgets already implement similar concepts.
- Users can generate reports from the same filters they already understand.
- Less duplication between dashboard, expenses page, and reports.

---

### 6 — Add a dedicated report aggregation service instead of expanding `DashboardController`
**Decision:** create a dedicated reporting service layer.

Why:
- Dashboard responses are optimized for interactive widgets, not persisted report generation.
- Reporting needs extra concepts: report persistence, chart definitions, PDF export, title/description generation, report detail DTOs.
- Keeps controller responsibilities clean.

Suggested services:
- `ReportService`
- `ReportQueryService`
- `ReportAggregationService`
- `ReportPdfService`
- `ReportInsightService`

---

## Proposed Domain Model

### `Report`
Minimum fields:
- `id`
- `userId`
- `title`
- `description`
- `expenseIds`
- `chartDefinitions`
- `createdAt`
- `updatedAt`

Recommended additional fields:
- `groupBy`
- `startDate`
- `endDate`
- `filterSnapshot`
- `insights`

Suggested entity shape:
```java
public class Report {
    Long id;
    Long userId;
    String title;
    String description;
    List<Long> expenseIds;
    List<ChartDefinition> chartDefinitions;
    String groupBy;
    String filterSnapshot;
    String insights;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

### `ReportGroupBy`
Recommended enum:
```java
public enum ReportGroupBy {
    CATEGORY,
    STORE_LOCATION,
    KEYWORD
}
```

### `ChartDefinition`
Recommended fields:
- `id`
- `type`
- `title`
- `metric`
- `groupBy`
- `limit`
- `sort`

Recommended enums:
```java
public enum ReportChartType {
    BAR,
    LINE,
    DOUGHNUT,
    TABLE
}

public enum ReportMetric {
    TOTAL_AMOUNT,
    EXPENSE_COUNT,
    AVERAGE_AMOUNT
}
```

---

## Data Model / Storage

## Required table columns
Per requirement, the `reports` table should include:
- `id`
- `title`
- `description`
- `list of expense ids`
- `list of chart definitions`

## Recommended PostgreSQL shape
Because the app already uses PostgreSQL, the simplest practical representation is:
- `expense_ids` as `jsonb` or `bigint[]`
- `chart_definitions` as `jsonb`

Suggested first version schema:
```sql
CREATE TABLE reports (
    id                 BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id            BIGINT NOT NULL REFERENCES users(id),
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    expense_ids        JSONB NOT NULL,
    chart_definitions  JSONB NOT NULL,
    group_by           VARCHAR(50) NOT NULL,
    filter_snapshot    JSONB,
    insights           JSONB,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL
);
```

Why this is a good fit:
- respects the requested “list” columns
- works well with JPA using converters or text/JSON handling
- keeps first implementation simpler than introducing join tables for report-expense and report-chart

Future option:
- normalize to `report_expenses` and `report_charts` tables if report complexity grows

### Migration note
The docs mention Flyway, but the current runtime still uses `spring.jpa.hibernate.ddl-auto=update` and a separate SQLite-to-Postgres migration runner.
Before implementation, decide whether report schema changes will be introduced via:
- Flyway migrations, or
- the current JPA-driven schema approach

Pick one path consistently for the reporting feature.

---

## Report Content Model

Each generated report should expose four sections.

### 1 — Expense list
Includes the selected expense snapshot with enough detail for drill-down:
- expense ID
- url ID
- transaction date
- amount
- currency
- amount in base currency
- category
- store name/location
- notes/tags

### 2 — Aggregate figures
Recommended first version:
- total spend
- expense count
- average expense amount
- min / max expense
- top category
- top location/store
- active days count
- covered date range

### 3 — Charts
Recommended default chart packs by report type:

#### Category report
- spend by category
- daily spending timeline
- top stores within selected expenses

#### Store location report
- spend by location
- daily/weekly spend timeline
- category mix within that location set

#### Keyword report
- spend over time
- category breakdown
- matching locations/stores

### 4 — Insights
Recommended deterministic first version:
- “Most spending happened in X.”
- “Largest expense was Y on DATE.”
- “Average daily spend was Z.”
- “Top category accounted for N% of the total.”

---

## Proposed API Surface

### User-facing APIs
| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/reports` | Generate and persist a new report from filters/grouping |
| `GET` | `/api/reports` | List saved reports for the user |
| `GET` | `/api/reports/{id}` | Get one report detail with aggregates, charts, insights, expenses |
| `DELETE` | `/api/reports/{id}` | Delete a saved report |
| `GET` | `/api/reports/{id}/pdf` | Export a report as PDF |
| `POST` | `/api/reports/preview` | Optional preview without saving |

### Existing APIs affected
| Method | Path | Change |
|---|---|---|
| `GET` | `/api/dashboard` | optionally include report link metadata for discovery/trip cards |
| `POST` | `/api/chat` | allow chat flow to trigger report generation |
| `GET` | `/api/expenses` | reuse existing filters as source input for report creation |

---

## Backend Responsibilities

### `ReportService`
Core responsibilities:
- validate request filters/grouping
- resolve matching expenses for the user
- create title/description
- create default chart definitions
- persist the report

### `ReportQueryService`
Responsibilities:
- list report summaries
- fetch report detail by user
- map stored report entities into DTOs

### `ReportAggregationService`
Responsibilities:
- load expenses from `expense_ids`
- compute aggregate figures
- compute grouped series for charts
- reuse existing amount/base currency rules from `Expense`

### `ReportInsightService`
Responsibilities:
- produce deterministic textual insights from aggregate data
- optionally add AI narrative generation in a later phase

### `ReportPdfService`
Responsibilities:
- render a report into printable HTML or direct PDF output
- embed figures/tables/charts
- stream PDF download response

---

## Frontend Responsibilities

### Reports page
Add a dedicated route such as:
- `#/reports`
- `#/reports/:id`

Capabilities:
- show saved reports list
- create a new report from current filters
- open a report detail view
- delete/export a report

### Report generation UX
Entry points:
1. Reports page “Generate report”
2. Expenses page from current list filters
3. Dashboard trip card “View Report”
4. Chat-generated reports

Recommended first version generation form:
- title (optional auto-generated default)
- description (optional)
- date range
- category filter
- country filter
- store filter
- keyword filter
- group by selector

### Report detail UX
Sections:
- report header with title, description, created date
- aggregate cards
- charts
- insight cards
- expense table/list
- actions: Export PDF, Delete, View matching expenses

---

## Chat Integration Design

## Current state
Chat already supports Spring AI tool callbacks via services in `service/mcp/*`.

## Recommended target state
Introduce a new tool/service specifically for reporting, for example:
- `ReportToolService`

Suggested tool methods:
- `generateExpenseReport(groupBy, startDate, endDate, keyword, category, country, storeName)`
- `listReports(limit)`
- `getReportSummary(reportId)`

Recommended chat behavior:
- When the user says “create a report for my Japan trip” or “generate a category report for groceries last quarter”, the LLM calls the report tool.
- The tool persists the report and returns:
  - report ID
  - title
  - number of included expenses
- The chat reply should include a UI-friendly link/button target to open the report.

Important:
- Keep report generation as a tool-backed operation, not only prompt-parsing in `ChatService`.
- This makes it deterministic, secure, and testable.

---

## Dashboard Trip Card Integration

## Current state
Discovery/trip cards are produced in `DashboardController.buildDiscoveryCards(...)` and rendered in `dashboard-widgets.js`.

## Required change
Each discovery card should expose a **View Report** action.

Recommended first version behavior:
- if a matching saved trip report already exists, open it
- otherwise generate a report using the card's country/city/month filters and navigate to it

Suggested filter mapping from a trip card:
- `country`
- optional `city`
- `yearMonth` translated to `startDate` and `endDate`
- grouping default: `STORE_LOCATION`

Why:
- discovery cards already behave like report seeds
- this gives immediate value without inventing a new dashboard object

---

## PDF Export Strategy

## Requirement
Export a report as PDF.

## Recommended implementation path
Use server-side PDF generation from HTML rather than building PDFs manually with PDFBox primitives.

Recommended approaches:
1. **Preferred:** add an HTML-to-PDF library such as OpenHTMLToPDF for layouted report exports
2. **Fallback:** use PDFBox directly for a simpler text/table-first export

Why HTML-to-PDF is preferable:
- easier styling
- better support for tables, headings, and pagination
- easier future branding

Suggested first version PDF contents:
- title + description
- generated timestamp
- filter summary
- aggregate figures
- insight bullets
- expense table
- optional chart images if available

Chart export options:
- phase 1 PDF can skip chart images and include tables only
- later phase can send chart image snapshots from frontend or render SVG/server-side charts

---

## Phases

---

### Phase 1 — Report Data Model & Backend Skeleton

**Goal:** introduce persistent report storage and basic CRUD APIs.

#### 1.1 Add report schema
Create a new `reports` table/entity with:
- required columns from the task
- `user_id`
- timestamps
- optional `group_by`, `filter_snapshot`, `insights`

#### 1.2 Add model/repository layer
Create:
- `model/Report.java`
- `model/ReportGroupBy.java`
- `dto/report/*`
- `repository/ReportRepository.java`

#### 1.3 Add controller/service skeleton
Create:
- `controller/ReportController.java`
- `service/ReportService.java`
- `service/ReportQueryService.java`

#### 1.4 Basic endpoints
Implement:
- `POST /api/reports`
- `GET /api/reports`
- `GET /api/reports/{id}`
- `DELETE /api/reports/{id}`

#### 1.5 Tests
- repository persistence tests
- auth/ownership tests
- controller response shape tests

**Exit criteria:**
- user can create, list, open, and delete a saved report record
- report belongs only to its owner

---

### Phase 2 — Report Generation Engine

**Goal:** generate meaningful report content from filtered expenses.

#### 2.1 Reuse expense filtering rules
Create a shared filter DTO/service so report generation can use:
- `startDate`
- `endDate`
- `category`
- `country`
- `storeName`
- `search`

#### 2.2 Implement grouping modes
Support:
- `CATEGORY`
- `STORE_LOCATION`
- `KEYWORD`

#### 2.3 Build aggregate calculations
Create `ReportAggregationService` to compute:
- totals
- counts
- averages
- top dimensions
- time-series values

#### 2.4 Build chart definition defaults
Generate a default chart pack based on grouping mode.

#### 2.5 Add deterministic insights
Create `ReportInsightService` for textual insights.

#### 2.6 Report detail DTO
Return a hydrated response containing:
- persisted metadata
- aggregate figures
- chart-ready data
- insights
- expense list

#### 2.7 Tests
- grouping tests
- filter combination tests
- aggregate correctness tests
- empty result behavior tests

**Exit criteria:**
- generated report contains correct expense list, aggregates, charts config, and insights

---

### Phase 3 — Reports Page & Manual Generation UX

**Goal:** let users generate and browse reports in the SPA.

#### 3.1 Add Reports route
Update:
- `static/js/router.js`
- navbar/drawer links in `static/index.html`

#### 3.2 Add reports list page
Create `static/js/reports.js` to render:
- saved reports table/cards
- create-report button
- delete/export actions

#### 3.3 Add report detail page
Render:
- header
- aggregates
- charts
- insights
- expense list

#### 3.4 Add generation form/modal
Allow generation using any filter + grouping combination.

#### 3.5 Optionally add generation from Expenses page
Add a “Generate Report” action using current list filters.

#### 3.6 Styling
Add CSS for:
- reports list
- report detail
- PDF-friendly print layout if reused

#### 3.7 Tests / QA
- route rendering tests where applicable
- manual desktop/mobile QA
- empty-state and error-state QA

**Exit criteria:**
- user can generate and view reports entirely from the UI

---

### Phase 4 — Dashboard Trip Cards → View Report

**Goal:** make discovery/trip cards a report entry point.

#### 4.1 Extend discovery card payload or client mapping
Add enough metadata to derive report filters from a trip card.

#### 4.2 Add `View Report` action in discovery card UI
Update `dashboard-widgets.js` so each card shows a clear CTA.

#### 4.3 Implement navigation behavior
Recommended first version:
- click creates a report using the card filters
- redirect to `#/reports/{id}`

Optional optimization later:
- deduplicate and reopen an existing matching report

#### 4.4 Tests
- controller mapping tests for trip filter generation
- frontend/manual QA for card CTA behavior

**Exit criteria:**
- trip cards can open a relevant generated report

---

### Phase 5 — Chat-Driven Report Generation

**Goal:** allow users to create reports from chat.

#### 5.1 Add reporting tool service
Create `service/mcp/ReportToolService.java`.

#### 5.2 Register report generation tools
Add tools such as:
- generate report
- list reports
- summarize report

#### 5.3 Update chat prompt/tool usage expectations
Extend chatbot guidance so report requests trigger tools rather than vague prose.

#### 5.4 Return report link metadata to frontend
Add a way for chat responses to carry report references, similar to expense cards.

Possible DTO changes:
- extend `ChatResponse`
- add `ReportCard` DTO

#### 5.5 Update `static/js/chat.js`
Render report cards/links inside bot messages.

#### 5.6 Tests
- tool service tests
- chat controller/service tests for report references
- prompt/tool regression tests

**Exit criteria:**
- a user can ask chat to generate a report and open it from the reply

---

### Phase 6 — PDF Export

**Goal:** export saved reports as PDF.

#### 6.1 Add PDF rendering service
Create `ReportPdfService`.

#### 6.2 Implement export endpoint
Add:
- `GET /api/reports/{id}/pdf`

#### 6.3 Render printable report structure
Include:
- title / description
- date generated
- filters summary
- aggregate figures
- insights
- expense table
- optionally chart images

#### 6.4 Decide chart export strategy
Options:
- no charts in first PDF version
- chart data tables instead of images
- frontend-generated chart images uploaded or passed for rendering
- server-generated SVG/PNG later

#### 6.5 Tests
- controller content type / headers
- PDF generation smoke test
- ownership/access tests

**Exit criteria:**
- user can export any saved report as a PDF download

---

### Phase 7 — Hardening & Enhancements

**Goal:** make reporting robust and scalable.

#### 7.1 Performance improvements
- avoid N+1 lookups for store/item enrichment
- cache repeated aggregations if needed
- paginate long expense lists in report detail UI

#### 7.2 Validation and guardrails
- max number of expenses per report
- max chart count per report
- reject invalid filter/group combinations

#### 7.3 Refresh/rebuild support
Optional later:
- “Refresh report from original filters”
- “Duplicate report with new date range”

#### 7.4 AI narrative insights
Optional later:
- explain trends in natural language
- compare this report with a previous period

#### 7.5 Documentation
Update:
- `docs/architecture.md`
- `docs/testing.md`
- API docs if maintained separately

**Exit criteria:**
- reporting is maintainable, performant, and documented

---

## Suggested Files to Create / Modify

### Backend
| File | Action |
|---|---|
| `model/Report.java` | new |
| `model/ReportGroupBy.java` | new |
| `repository/ReportRepository.java` | new |
| `dto/report/*` | new DTOs |
| `service/ReportService.java` | new |
| `service/ReportQueryService.java` | new |
| `service/ReportAggregationService.java` | new |
| `service/ReportInsightService.java` | new |
| `service/ReportPdfService.java` | new |
| `controller/ReportController.java` | new |
| `controller/DashboardController.java` | extend discovery-card/report metadata if needed |
| `service/ChatService.java` | support report link metadata in responses |
| `service/mcp/ReportToolService.java` | new |
| `dto/chat/ChatResponse.java` | extend for report references |

### Frontend
| File | Action |
|---|---|
| `static/index.html` | add Reports nav entry |
| `static/js/router.js` | add `#/reports` routes |
| `static/js/reports.js` | new |
| `static/js/dashboard-widgets.js` | add View Report on discovery cards |
| `static/js/chat.js` | render report links/cards |
| `static/js/expenses.js` | optional “Generate Report” from current filters |
| `static/css/base.css` or new report CSS | report page styling |

### Config / dependencies
| File | Action |
|---|---|
| `pom.xml` | add HTML-to-PDF dependency if chosen |
| schema migration location or JPA model | add reports persistence |

---

## Recommended Rollout Order

1. **Phase 1** — report entity + CRUD skeleton
2. **Phase 2** — report generation engine
3. **Phase 3** — reports page and manual generation
4. **Phase 4** — dashboard trip card integration
5. **Phase 5** — chat-driven report generation
6. **Phase 6** — PDF export
7. **Phase 7** — hardening and enhancements

Why this order works:
- persistence comes first
- generation correctness is solved before UI richness
- dashboard/chat integrations are layered on top of a stable reporting backend
- PDF export comes after report detail shape is stable

---

## Open Questions

1. **Should reports be immutable snapshots, or refreshable views?**
   - Recommended first answer: immutable snapshot, with refresh later.

2. **Should a report store computed aggregates/insights, or recompute them from `expense_ids` on read?**
   - Recommended first answer: recompute on read, store only report definition + IDs.

3. **Should keyword grouping mean one report filtered by keyword, or grouping buckets by multiple matching keywords?**
   - Recommended first answer: one keyword filter per report.

4. **Should trip-card reports be auto-created and persisted on click, or previewed first?**
   - Recommended first answer: create on click and navigate to saved report.

5. **Do charts need to appear as images in PDF from day one?**
   - Recommended first answer: no; ship table/text-first PDF, then add chart rendering.

6. **Should report titles/descriptions be auto-generated or user-editable?**
   - Recommended first answer: auto-generate defaults, allow manual override.

---

## Summary

This reporting feature fits naturally into the current app if implemented in phases:
1. persist report records
2. generate report content from filtered expenses
3. add a dedicated Reports page
4. wire dashboard trip cards to reports
5. add chat-triggered generation
6. add PDF export
7. harden and refine

The key architectural choice is to make reports persisted snapshots built from existing expense filters, while reusing the current dashboard aggregation concepts, Chart.js-based frontend rendering, and Spring AI tool integration for chat-driven workflows.

