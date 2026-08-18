# Feature Migration Playbook — JEX → Spring Boot + Next.js


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../database/flyway-migration-baseline.md) §5.

This is the **procedural** guide for porting one feature from the legacy JEX system to the Spring Boot + Next.js refactor. The **rules** that govern each step live in three CLAUDE.md files and in the existing per-folder READMEs — this playbook is the ordering and the links, not the rules themselves.

**When to use:** every time you start porting a feature (login, account-inquiry, approval-box, firm-banking-settings, …). Walk the steps in order. Do not skip discovery.

**Companion rule sources** (read at the corresponding step — do not duplicate their content here):

| Tree | CLAUDE.md | Governs |
|---|---|---|
| Workspace root | [/CLAUDE.md](../../../CLAUDE.md) | Cross-tree boundary, typography, doc structure, slug naming |
| Spring Boot API | [wecms-api/CLAUDE.md](../../../CLAUDE.md) | Wire shape, envelope, error codes, entity / DTO / repo / service rules, Flyway, sort whitelist, View Setting backend, Excel export, enums, tenant context |
| Next.js Web | [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) | Three-layer naming, mapper boundary, dialog rules, shell composition, View Setting popover, screenId registry, locale files |

---

## 0 · Before you start

- **Confirm scope with the PO** (Mr. Jung) for any feature with non-trivial UX or business-rule ambiguity. The team roster lives in memory `project_team_roster.md`.
- **Confirm tree placement.** You will edit only `wecms-api/` and `wecms-web/` plus `docs/refactor-wecms/`. The JEX trees (`WECMS DEV/`, `SOURCE_CODE_ADMIN/`) are **read-only reference**. Never write there during a refactor port.
- **Refactor is local-only R&D.** No DEV / UAT / PROD deploy. No bank integration. No coexistence with the live JEX system.

---

## 1 · Pick the feature and assign its slug

Every feature has **one kebab-case slug** that is used identically on both sides:

```
docs/jex/features/<slug>.md                ↔  docs/refactor-wecms/features/<module>/<slug>.md
docs/refactor-wecms/migration/<slug>-mapping.md
docs/refactor-wecms/database/<module>/<slug>-schema.md
com.kosign.wecms.domain.<module>.<slug-as-camel>     (Java package)
/api/v1/<module>/<slug>                              (Spring REST URI)
app/(dashboard)/<module>/<slug>/                     (Next.js route)
components/<module>/<slug>/                          (frontend feature folder)
SCREEN_IDS.<UPPER_SNAKE_OF_SLUG>                     (lib/viewSettingScreens.ts)
WEB_SVC_ID seed value = "<module>-<slug>"            (SYS_SCR_OPTION_WECMS)
```

**Rules on slug choice** (root CLAUDE.md §"Refactor identifier naming"):

- Use the feature's domain name, not its legacy WSVC id. `firm-banking-settings` ✅, not `comm_0220_001` ❌.
- The slug must have ≥ 2 kebab segments (the backend `@Pattern` validates `^[a-z][a-z0-9]*(-[a-z0-9]+)+$`).
- Multi-currency / multi-area pages get a suffix: `account-inquiry-vnd`, `account-inquiry-fcy`.

**Existing slugs already taken** — `account-inquiry-vnd`, `account-inquiry-fcy`, `approval-box`, `firm-banking-settings`, `firm-banking-history`, `erp-transaction-history`, `purpose-settings`, `vas-account`, `contacts`.

---

## 2 · Discovery — read the JEX source of truth

The JEX side is the **functional spec**. Read it; do not copy its patterns. Skim in this order, and quote file:line pointers into your design notes:

1. **The action JSP** — `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/<module>/<wsvc>_act.jsp`. This is the controller equivalent — validation, transaction boundary, IDO calls, error codes.
2. **The WSVC XML** — under `WECMS_ETC/xml/service/WSVC/WSVC.<wsvc>.xml`. Defines the wire input/output field names + lengths.
3. **The IDO XML(s) referenced by the WSVC** — `WECMS_ETC/xml/service/IDO/IDO.<TABLE>_R00N.xml`. SQL essence (parameterised SELECT/INSERT/UPDATE/DELETE).
4. **The DAO** — `WECMS DEV/WECMS/src/wabooks/dao/<Table>DAO.java`. Wraps the IDO calls. Methods like `findByPk`, `findByDynamic`, `create`, `update`, `upsert`, `deleteByPk`.
5. **The Entity** — `WECMS DEV/WECMS/src/wabooks/entity/<Table>.java`. `@Table(name = "UPPER_SNAKE")` mapping; full legacy column set.
6. **Any helper** — `WECMS DEV/WECMS/src/wabooks/helper/<Module>Helper.java`. Reusable business logic across action JSPs.
7. **JEX docs** — `docs/jex/features/<slug>.md`, plus relevant `docs/jex/business-rules/` and `docs/jex/screen-flows/`.

**Read the database side too:**

- Use the `postgres` MCP (DEV, read-only — never PROD) to query `information_schema.columns` for every table the JSP touches. The local Flyway schema must mirror DEV **column-for-column** (see api CLAUDE.md §"Legacy Schema Parity Rule"). All MCP DB rules are in [.claude/rules/database.md](../../../../.claude/rules/database.md).
- Check `wecmsvndev.wb_get_next_val('<table>', '<field>', NULL, NULL, NULL)` for legacy key generation. If non-NULL, follow the Sprint 1+ rule (api CLAUDE.md §"Key-generation rule for migrated legacy tables").

Output of Step 2 is reading notes — file:line citations you'll embed in the design docs in Step 3.

---

## 3 · Write the design docs *before* writing code

> **Docs-first is now mandatory repo-wide (2026-08-17), not just for a feature port.** In addition to
> the design docs below, a **dated implementation record** — `descriptive-name-YYYY-MM-DD.md` at
> `Status: IN PROGRESS` — is created **before** any application code is modified, and is completed in
> place afterwards (status → `COMPLETE` / `CLOSED` / `PARTIAL` / `BLOCKED`). Authoritative rule +
> templates: [../../../CLAUDE.md](../../../CLAUDE.md) §"Docs-First Implementation Workflow
> (MANDATORY)". This step and that rule agree — docs precede code — so nothing here is relaxed.

Create three files under `docs/refactor-wecms/` in this order. Each links to the others.

| Order | File | Owner-folder README |
|---|---|---|
| 3.1 | `features/<module>/<slug>.md` — feature spec spine (status, scope, endpoints, page route, DB tables, JEX traceability) | [features/README.md](../../features/README.md) |
| 3.2 | `migration/<slug>-mapping.md` — JEX→Spring side-by-side: WSVC → controller method, IDO → repo query, field-by-field DTO mapping. **Use the existing precedent:** [migration/firm-banking-settings-mapping.md](firm-banking-settings-mapping.md) is the canonical example. | [migration/README.md](README.md) |
| 3.3 | `database/<module>/<slug>-schema.md` — DDL intent: every table touched, column-for-column parity with DEV, indexes, constraints, deferred FKs/triggers, seed strategy | [database/README.md](../database/README.md) |

Optional companions when scope is non-trivial:

- `backend/<module>/<slug>-design.md` — controller/service/repo internals if the mapping doc is not enough (see [backend design notes → business-rules/README.md](../business-rules/README.md))
- `frontend/<module>/<slug>-design.md` — page layout, hooks, state shape (see [frontend design notes → wecms-web/docs/README.md](../../../../wecms-web/docs/README.md))

**Naming hard rule** (root CLAUDE.md §"Documentation Structure Rules"): the `<slug>` must be identical to the JEX counterpart `docs/jex/features/<slug>.md`. Same name on both sides — never rename.

---

## 4 · Ship the Flyway migrations

Database changes always land first — `spring.jpa.hibernate.ddl-auto = validate` means the entity won't load if the schema doesn't match.

1. **One migration file per logical change** under `wecms-api/src/main/resources/db/migration/V{N}__<description>.sql`. Find the next free `V{N}`; never edit an applied migration.
2. **Mirror DEV column-for-column.** Run the pre-migration checklist from api CLAUDE.md §"Legacy Schema Parity Rule". Copy every column with the legacy NOT-NULL / DEFAULT / length / precision.
3. **Carry over PK + every `*_uk*` UNIQUE + every `*_chk*` CHECK + applicable FKs.** Defer FKs only to tables not yet ported, and document the deferral in the migration's trailing comment.
4. **Idempotent SQL** — `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, `INSERT ... ON CONFLICT DO NOTHING`.
5. **Seed real DEV rows** for the tenant `VN_VNM0004_000001` (and `VN_VNM0004_000014` HAENGSUNG for product-type `H` features). Pull rows from DEV via the `postgres` MCP; never invent demo data when real DEV data exists.
6. **No new `wb_get_next_val` branches**, no new `CREATE FUNCTION`, no `@Procedure` / stored-procedure call from feature code (api CLAUDE.md §"DB Function / Legacy Function Policy"). The only exception is `wb_format_acct_nick_nm` already ported in V44 — talk to Sonny before adding another.
7. **If the feature is a list page**, seed its View Setting columns in the same migration (api CLAUDE.md §"Screen Option (View Setting)"). `WEB_SVC_ID = '<module>-<slug>'`, `DYM_AREA_ID = 'default'` for single-area pages.

Verify migrations apply cleanly:

```bash
cd wecms-api
./gradlew bootRun        # Flyway applies on boot; Hibernate validates schema
```

---

## 5 · Implement the backend (`wecms-api`)

Package layout — create `com.kosign.wecms.domain.<module>.<slug-as-camel>/` with five sub-packages mirroring the existing `domain/auth/` (api CLAUDE.md §"Architecture Rules"):

```
controller/<Slug>Controller.java          @RestController @RequestMapping("/api/v1/<module>/<slug>")
service/<Slug>Service.java                @Service @Transactional on mutating methods
repository/<Slug>Repository.java          extends JpaRepository<Entity, Id>
entity/<Slug>.java                        @Table(name = "<UPPER_SNAKE>") extends BaseEntity
dto/<Slug>Row.java + <Slug>Request.java   Java records, camelCase fields
```

Reference implementation to copy structure from (do not copy code mechanically):

- [domain/banking/](../../../src/main/java/com/kosign/wecms/domain/banking/) — list with filter / sort / paging
- [domain/setting/erptransactionhistory/](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/) — list + audit + file download
- [domain/setting/firmbankingsettings/](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/) — CRUD with audit-log writes

**Per-layer checklist** (each item links to a section of api CLAUDE.md):

- **Controller** thin — request parsing, `@Valid @RequestBody`, `TenantContext.currentBizKey()` not read here (service does that). Return `ApiResponse<T>`. §"Coding Rules → Controllers".
- **Service** — `@Transactional`; tenant-scope from `TenantContext`; throw `BusinessException("WBExxxx")` for known error paths. §"Coding Rules → Services".
- **Repository** — prefer derived method names over `@Query`. Every tenant-scoped query carries `bizKey`. §"Coding Rules → Repositories".
- **Entity** — `@Column(name = "UPPER_SNAKE")`, extends `BaseEntity` for audit columns, `@IdClass` for composite PK. §"Coding Rules → Entities".
- **DTO** — Java records, camelCase Java fields → lowercase snake_case wire via the global `SNAKE_CASE` Jackson strategy. No `@JsonProperty("UPPER_SNAKE")`. §"API Naming & Serialization Standards".
- **Error codes** — add `WBExxxx` to `src/main/resources/messages.properties` BEFORE throwing. §"When you add a new error code".
- **Sort whitelist** if the list endpoint exposes sortable columns — write a per-domain enum implementing `SortableField`; mirror View Setting `FIELD_ID`. §"Common table sort".
- **Excel export** if the list page has the toolbar Excel icon — add a `@Component` implementing `ExportRowProvider`; reuse the list service's filter+sort path. §"Excel export".
- **Audit log** if the write touches a feature that needs the `AUDIT_LOGS_INFM` ledger — go through `AuditLogService.append(...)` with the feature slug as the `WSVC_ID`. Never open-code the three-language insert.
- **Enums** — never bulk-port. Add a `common/enums/<Name>.java` only when a real consumer lands. §"Domain enums — `common/enums/`".

Build + run + smoke test:

```bash
cd wecms-api
./gradlew build
./gradlew bootRun                       # listens on localhost:8090 (default)
# In another shell:
curl http://localhost:8090/api/v1/<module>/<slug> -H "Authorization: Bearer <jwt>"
```

---

## 6 · Implement the frontend (`wecms-web`)

Naming follows the **three-layer rule** (web CLAUDE.md §"API Naming & Serialization Standards"):

| Layer | Convention | File |
|---|---|---|
| Wire JSON | lowercase `snake_case` (matches the backend wire) | every HTTP call |
| Frontend wire types | `snake_case` property names matching wire | [lib/types/api.ts](../../../../wecms-web/lib/types/api.ts) |
| Frontend in-memory types | `camelCase` semantic full words | everything outside `lib/types/api.ts` |

**Translation happens at the boundary, exactly once** in [lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts) or [lib/auth/client.ts](../../../../wecms-web/lib/auth/client.ts). Components, hooks, storage helpers MUST NOT read `response.data.user_id` directly — extend a mapper first.

**Per-layer checklist:**

1. **Add wire types** to `lib/types/api.ts` — one `Api<Slug>Row` interface + one `Api<Slug>ListResponse` envelope shape. Properties lowercase snake_case.
2. **Add in-memory types + mapper** to `lib/api/mappers.ts` — `map<Slug>Row(api: Api<Slug>Row): <Slug>Row`. Reference: existing `mapAccountRow` in [mappers.ts](../../../../wecms-web/lib/api/mappers.ts).
3. **Create the route** under `app/(dashboard)/<module>/<slug>/page.tsx` (thin) → composes a feature component. Multi-currency variant uses sub-routes (`account-inquiry/vnd`).
4. **Build the feature component** under `components/<module>/<slug>/` — start by checking the four approved UI baselines (web CLAUDE.md §"UI Implementation Baseline"). Pick the closest of: Account Inquiry (standard list), Approval Box (tabbed), Sales e-Invoices (multi-currency totals), Purpose modal (modal baseline). Reuse `<AppShell>` + `<Header>` + `<Sidebar>` + `<PageHeader>` + `<Pagination>` + `<SearchInput>` from `components/shared/` and `components/layout/`.
5. **Register the screen** in [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts) — add a constant to `SCREEN_IDS` whose value equals the backend `WEB_SVC_ID` seed (`<module>-<slug>`). The popover and Excel-export wiring read from this registry.
6. **Locale entries** in `locales/en.json`, `locales/ko.json`, `locales/vi.json` — feature key prefix matches the slug (`account_inquiry.title`, `firm_banking_settings.column.bank_nm`). Never add entries to `km.json` (web/api CLAUDE.md — KH locale deferred).
7. **Dialog variants** for any popup — pick the correct `type` (`error` / `warning` / `confirmation` / `success`) per web CLAUDE.md §"Dialog variant". The variant drives icon + title; API/network failures are ALWAYS `error`, never `warning`.
8. **Sort cycle** if the table is sortable — frontend sends `sortField` (View Setting `FIELD_ID`) + `sortDirection` (`ASC` / `DESC`); cycle is `default → ASC → DESC → default`. Special FIELD_IDs (`CHECKBOX`, `ETC`) are never sortable.

Run + browser-test:

```bash
cd wecms-web
npm install                      # if dependencies changed
npm run lint                     # ESLint flat config
npm run dev                      # http://localhost:3000
# Open the new route in a browser; exercise filter / sort / paging / export / dialog flows.
```

UI testing is **not optional** for any UI change — see workspace CLAUDE.md "For UI or frontend changes, start the dev server and use the feature in a browser before reporting the task as complete."

---

## 7 · Cross-tree consistency check

Before declaring the feature ported, verify these alignments:

| Check | Backend | Frontend |
|---|---|---|
| **Slug** | `/api/v1/<module>/<slug>`, package, audit `WSVC_ID` | Route, component folder, `SCREEN_IDS` key |
| **Wire field names** | DTO record fields (camelCase) → snake_case via Jackson | `Api<Slug>Row` properties (snake_case) match exactly |
| **In-memory shape** | n/a | Mapper output uses camelCase semantic full words |
| **Error codes** | `WBExxxx` in `messages.properties` + thrown via `BusinessException` | Error dialogs surface `code` + `traceId` |
| **View Setting screenId** | `SYS_SCR_OPTION_WECMS.WEB_SVC_ID = '<module>-<slug>'` | `SCREEN_IDS.<UPPER>` equals the seed value |
| **Sortable columns** | Whitelist enum constants match View Setting `FIELD_ID` and have `SORT_YN='Y'` | Frontend sort UI sends those exact `FIELD_ID` values |
| **Tenant scope** | Every query carries `BIZ_KEY` from `TenantContext` | n/a (token-bound) |

If the feature is exportable, also verify the export endpoint registers an `ExportRowProvider` and the frontend Excel button posts to `/api/v1/<module>/<resource>/export` with the same `screenId`.

---

## 8 · Commit, push, document

> **"Document" here means *finalize*, not *begin*.** The dated implementation record and the design
> docs already exist from §3 — this step completes them (verified root cause, actual implementation,
> files changed, impacts, test results, limitations), flips the record's `Status:` off `IN PROGRESS`,
> and updates the permanent feature docs where the authoritative behaviour changed. Nothing here
> authorizes writing the first documentation after the code.

1. **GitLab branch convention** — `feature/<slug>` on both `wecms-api` and `wecms-web`. Push both; cross-link MRs in the descriptions.
2. **Pre-merge checklist** in the MR description — paste the api CLAUDE.md pre-migration checklist if Flyway changes are part of the MR.
3. **Tag the feature spec** at `docs/refactor-wecms/features/<module>/<slug>.md` with `Status: Implemented` once the backend + frontend are merged.
4. **Bug reports** discovered during the port go to `docs/bug-reports/` tagged with `System: JEX` (if the bug exists in the legacy system you observed) or `System: Refactor` (if introduced in the new code).
5. **Update the refactor README** if the feature added a new module not previously listed.

---

## 9 · Stop conditions (when to escalate, not push through)

Pause and ask Sonny or the PO before:

- **Schema drift** discovered during Step 4 that touches more than the feature's own tables — could affect other ports already in flight.
- **A new `wb_*` DB function** appears unavoidable (api CLAUDE.md §"DB Function / Legacy Function Policy" — only `wb_format_acct_nick_nm` is currently grandfathered).
- **A behavioural delta** from JEX that is not just a naming / envelope change — record it in §"Behavior delta" of the migration mapping with the reason.
- **A new wire envelope** (anything other than `{ status, data }` for success / `{ status, timestamp, path, trace_id }` for error) — forbidden by the CTO directive.
- **A second UI / icon / form library** or a second font stack — forbidden by web CLAUDE.md.

---

## 10 · Per-feature template summary (paste into your kickoff note)

```markdown
# Feature: <slug>

- JEX WSVC ids: comm_xxxx_yyy, comm_xxxx_zzz
- JEX docs: docs/jex/features/<slug>.md
- Refactor module: <module>
- Refactor slug: <slug>
- REST URI: /api/v1/<module>/<slug>
- Frontend route: /<module>/<slug>
- View Setting WEB_SVC_ID: <module>-<slug>
- Tables touched: <TABLE_A>, <TABLE_B>
- Tenants: VN_VNM0004_000001 [, VN_VNM0004_000014 if HAENGSUNG-specific]
- Sprint: <N>
- Author: <name>

## Discovery citations
- JSP: WECMS DEV/.../action/jex/wabooks/<module>/<wsvc>_act.jsp:<L>-<L>
- WSVC: WECMS_ETC/xml/service/WSVC/WSVC.<wsvc>.xml
- IDOs: IDO.<TABLE>_R00N.xml
- DAO:  WECMS DEV/WECMS/src/wabooks/dao/<Table>DAO.java
- Entity: WECMS DEV/WECMS/src/wabooks/entity/<Table>.java

## Design doc paths (to create)
- features/<module>/<slug>.md
- migration/<slug>-mapping.md
- database/<module>/<slug>-schema.md

## Behavior deltas from JEX
- (list any intentional differences with reason)
```

---

## Reference index

| Topic | Doc |
|---|---|
| Workspace boundaries, typography, slug rule | [/CLAUDE.md](../../../CLAUDE.md) |
| Backend rules (full) | [wecms-api/CLAUDE.md](../../../CLAUDE.md) |
| Frontend rules (full) | [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) |
| DB access governance (MCP) | [/.claude/rules/database.md](../../../../.claude/rules/database.md) |
| Refactor doc structure | [docs/refactor-wecms/README.md](README.md) |
| Migration mapping format (canonical example) | [migration/firm-banking-settings-mapping.md](firm-banking-settings-mapping.md) |
| Feature spec format | [features/README.md](../../features/README.md) |
| Schema design notes format | [database/README.md](../database/README.md) |
| Backend design notes format | [backend design notes → business-rules/README.md](../business-rules/README.md) |
| Frontend design notes format | [frontend design notes → wecms-web/docs/README.md](../../../../wecms-web/docs/README.md) |
| JEX functional specs (read-only) | [docs/jex/features/](../../../../docs/jex/features/) |
| Bug reports (cross-system) | docs/bug-reports/ _(removed — historical `archive/` folder deleted by project decision)_ |
