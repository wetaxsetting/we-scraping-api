# CLAUDE.md — wecms-api

Living AI/developer guide for the Spring Boot backend of the WeCMS JEX → Spring/Next.js refactor. Update this file whenever the real code moves.

Workspace-level rules (cross-tree, write boundary, typography source of truth, BIZ_KEY rule) live in the root [CLAUDE.md](../CLAUDE.md). The Next.js frontend this app serves is in [wecms-web/](../wecms-web/). JEX functional spec is read-only in [WECMS DEV/CLAUDE.md](../WECMS%20DEV/CLAUDE.md).

**Porting a feature?** The end-to-end procedural workflow (discovery → design docs → Flyway → backend → frontend → verify) is at [docs/refactor-wecms/MIGRATION_PLAYBOOK.md](../docs/refactor-wecms/MIGRATION_PLAYBOOK.md). Steps 4–5 of the playbook are the backend slice; the rules each step links to live in *this* file.

---

## Purpose

Back-end API for the WeCMS refactor R&D. Serves the Next.js client, reads the legacy JEX schema shape (column-for-column), and enforces single-tenant data isolation.

Status: **R&D proof-of-concept**, local-only, GitLab-only. No DEV / UAT / PROD deploy, no bank integration, no coexistence code with the live JEX system.

## Scope Guard — Backend Changes Only on Explicit Request

This tree is **NOT** modified during static-UI / frontend tasks. The frontend repo (`wecms-web/`) is the default editable tree; this repo is touched only when the user explicitly asks for a backend change.

**Triggers that authorise editing this tree:**

- The user names a backend artifact (controller, service, repository, entity, DTO, Flyway migration, `application*.yml`).
- The user asks for a new endpoint, request/response shape change, schema change, or business rule change.
- The user explicitly says "backend" / "API" / "Spring" / "endpoint" / "DB" / "schema" / "migration".
- A frontend task cannot proceed without a corresponding API contract — in which case Claude must ask the user before editing this tree (full-stack ownership rule), not assume.

**Do NOT, when working on a frontend task:**

- Add / remove / modify Java source under `src/main/java/`.
- Add / modify Flyway migrations under `src/main/resources/db/migration/`.
- Touch `application*.yml`, `build.gradle`, or `messages*.properties`.
- "Pre-emptively" wire an endpoint a frontend mock could already cover.
- Modify the local DB schema, even via the `postgres` MCP — schema changes go through Flyway, and Flyway migrations are a backend artifact.

**Static-UI mock data is owned by the frontend** (`wecms-web/components/{feature}/.../mockData.ts`). Do not generate "matching mock data" on the backend side, and do not propose moving frontend mocks into this repo. The frontend ships static-UI panels with no backend dependency until a real endpoint is asked for.

When in doubt, ask the user. The cost of pausing to confirm is low; the cost of an unscheduled migration or a half-wired endpoint is high.

## Documentation Structure Rules

All refactor backend documentation lives under this project's own [`docs/`](docs/) tree (relocated 2026-06-01 out of the deprecated workspace-level `docs/refactor-wecms/`). Never write refactor docs under `docs/jex/` — that tree is **read-only** functional reference for the live JEX system.

| Doc type | Path |
|---|---|
| Backend API contracts (controllers, DTOs, validation, errors) | [docs/api-spec/](docs/api-spec/) |
| Service / domain design (services, repositories, entities, security, settings) | [docs/business-rules/](docs/business-rules/) |
| JEX → Spring service mapping (which WSVC/IDO maps to which controller/service, field-by-field) | [docs/migration/](docs/migration/) |
| DB schema design + Flyway migration notes | [docs/database/](docs/database/) |
| DB parity audits (refactor vs JEX) | [docs/audits/](docs/audits/) |
| Refactor feature specs (kebab-case must match the JEX counterpart) | [docs/features/](docs/features/) |
| Sprint / task plans | [docs/planning/](docs/planning/) |
| System architecture (cross-feature) | [docs/architecture/](docs/architecture/) |
| Refactor-side test plans | [docs/testing/](docs/testing/) |
| Bug reports (tag `System: Refactor`) | [../docs/bug-reports/](../docs/bug-reports/) (shared, workspace root) |

> The deprecated [`docs/refactor-wecms/`](../docs/refactor-wecms/) tree is retained read-only; deep links elsewhere in this file still point there and remain valid.

**JEX is read-only reference.** Read JEX entity, DAO, IDO XML, WSVC XML, and `_act.jsp` (under `WECMS DEV/`) plus the canonical specs under [docs/jex/](../docs/jex/) as the functional spec for any service being translated. **Never implement JEX patterns directly** — the refactor is a clean Spring Boot rewrite informed by JEX behavior, not a port. No `JexData`, no `IDODynamic`, no `_act.jsp`-style logic, no `getInput()/getOutput()` envelope mimicry inside Java code.

**Naming traceability:** when a feature exists on both sides, the JEX spec at `docs/jex/features/<slug>.md` and the refactor spec at `wecms-api/docs/features/<slug>.md` must share the same kebab-case `<slug>`.

## Project Overview

- **What works today:** login (`POST /api/v1/auth/login`) with two-step multi-company resolution (auto-pick for single-link users; company chooser response for users with ≥ 2 active links), me (`GET /api/v1/auth/me`), logout stub (`POST /api/v1/auth/logout`), change-password (`POST /api/v1/auth/change-password`), JWT-protected catch-all for all other endpoints, Flyway-managed schema, **verify-only** password check against the legacy `public.hash()` SHA-256 credential (no password write-back — coexistence with the live JEX system).
- **Implemented module:** `domain/auth/` only. Every other JEX module (`pymn`, `mbmr`, `comm`, `admn`, …) is unimplemented.
- **Operational model: one user = one company account.** This is the **primary supported business flow** for current WeCMS operation. A `USER_ID` is expected to belong to exactly one active `BIZ_KEY` via `USER_BUSI_CONN_INFM`. Login resolves that single link, issues a JWT scoped to it, and the user lands on the dashboard directly with **no chooser screen**. Legacy WeCMS and current WeCMS operation do **not** treat one user spanning multiple companies as a normal production case.
- **Multi-company linkage is exceptional / test-demo only.** The refactor's per-user `userBusiRepo.findAllByUserIdAndDelYn(...)` + chooser response (`LoginResponse.chooser(...)` + `CompanyOption[]` + `<CompanyChooser>` UI) are retained as a **safe generic fallback** so a user who happens to have two or more active links won't error out — but that fallback is **not** an endorsement of the multi-company pattern as a documented business requirement. Treat the chooser branch as an exception path; do not redesign domain logic around it. If/when a real future business rule introduces multi-company users, this clause should be replaced with explicit requirements.
- **Tenant resolution mechanics:** tenant is resolved per user at login from `USER_BUSI_CONN_INFM`; JWT carries the selected `bizKey` + `authGrCd` + `prdtType` claims; `TenantContext` reads them per request. Local seed ships two R&D tenants today: `VN_VNM0004_000001` (V2 + V5 DEV snapshot — `CÔNG TY TNHH WEBCASH VIỆT NAM`, `PRDT_TYPE = 'F'`) and `VN_VNM0004_000014` (V73 seed — `HAENGSUNG ELECTRONICS`, `PRDT_TYPE = 'H'`). V73 originally added a guarded two-company link for `lamine@gmail.com` so the chooser UI could be exercised locally; **V76 removes that test link** so even on the seeded DB the operational model is one-user-one-company. `wecms.tenant.biz-key` is now a **dormant** config slot — retained only so `TenantProperties` stays bindable and so `CompanyBootstrapService.assertConsistent(...)` has a comparison target; the login path does not route through it.
- **No Spring auto-bootstrap.** Production / UAT / local startup never creates company, employee, user, link, or admin rows. The previous `BootstrapRunner` auto-invocation has been removed. Operational practice is **DBA-driven manual SQL** following the legacy setup pattern (see `Claude Code (AI)/sql/setup_uat_account_*.sql`): generate `BIZ_KEY` via `wb_get_next_val('BUSI_INFM','biz_key',NTNL_CD,CHNL_ID)`, insert `BUSI_INFM`, generate `EMPL_NO` via `wb_get_next_val('EMPL_INFM','empl_no',BIZ_KEY)`, then insert `EMPL_INFM` → `USER_INFM` → `BUSI_ADMN_INFM` → `USER_BUSI_CONN_INFM`. **That legacy script targets the legacy JEX schema** (`encrypt(...)` + `hash(...)` DB functions present, audit columns as `varchar(14)`) and is meant for DEV / UAT / PROD. It is **not** a directly executable migration against the refactor's local DB, which uses native PostgreSQL `TIMESTAMPTZ` / `DATE` audit columns and does not install `encrypt()` / `hash()` (per the "DB Function / Legacy Function Policy" rule — refactor does not add new legacy PL/pgSQL helpers). Local R&D account creation goes through Flyway seeds, not the manual script. **Canonical operational reference:** [docs/refactor-wecms/database/manual-tenant-setup.md](../docs/refactor-wecms/database/manual-tenant-setup.md) — full step-by-step SQL flow, both legacy-UAT and refactor-LOCAL variants, verification queries, and known limitations.
- **Bootstrap services removed (2026-06-10).** The inert `domain/auth/bootstrap/` package (`CompanyBootstrapService`, `UserBootstrapService`, `BusinessKeyGeneratorService`, `EmployeeNumberGeneratorService`, `CompanyBootstrapCommand`, `FirstAdminCommand`, `BootstrapResult`, `DataSeeder`) plus the BCrypt-only `PasswordHashService` were deleted — they had no production invoker and the "future Phase 2 admin API" never materialised. Account creation is **manual SQL only** (see the "No Spring auto-bootstrap" bullet). `TenantEnvironmentSettingsBootstrapService` was kept (it is the env-set seeding leg used by the Environment-Settings feature, not account creation). If a Phase 2 admin API is ever built, design it fresh — do not resurrect the deleted skeleton. See [docs/audits/login-legacy-compatibility-2026-06-10.md](docs/audits/login-legacy-compatibility-2026-06-10.md).
- **Authority model:** `BUSI_ADMN_INFM` is the per-(BIZ_KEY, USER_ID) authority source — presence means ADMIN of that company, absence means USER. Legacy `USER_BUSI_CONN_INFM` carries no `AUTH_GR_CD` column (confirmed on DEV 2026-05-11), so the admin-master table is authoritative. The same user can be ADMIN at one company and USER at another. JWT carries `authGrCd`; `JwtAuthenticationFilter` populates Spring Security authorities (`ROLE_ADMIN` / `ROLE_USER`). `@PreAuthorize` gating is **live (F-3 complete, 2026-06-06)**: every business `/api/v1/**` endpoint across Banking / Payment / Add-on / Settings is gated by the `@menuAuthz` menu-authorization guard (30 of 43 controllers; the other 13 are documented allow-list exceptions), with existing role gates (`hasRole('ADMIN')`, `@userMgmtAccess`) stacked, not weakened. A Phase-F coverage test (`EndpointAuthorizationCoverageTest`) fails the build on any un-annotated, non-allow-listed handler. See [docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md](../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).
- **Product type:** `BUSI_INFM.PRDT_TYPE` (one-character legacy code) is stamped into the JWT at login finalize and surfaced via `TenantContext.currentPrdtType()`. The refactor recognises three letters explicitly today — `F` (WeCMS default, used by `VN_VNM0004_000001`), `M` (WeCMS Lumens), and `H` (WeCMS HAENGSUNG Electronics, used by `VN_VNM0004_000014`) — via `common/enums/PrdtType`. The remaining 11 legacy letters (`W` / `R` / `B` / `E` / `U` / `L` / `S` / `P` / `C` / `D` / `T`) are carried verbatim on the wire and in the JWT but intentionally not modelled — a constant lands in `PrdtType` only when a real consumer for that letter arrives. Per-product behavioural gates (HAENGSUNG payment-table-2 layout, account masking for bank `11704020`, hidden expense file column, daily-financial-HS report gating; WeCMS Default e-Invoice gate; Lumens readonly overrides) are **deferred** and port with each feature MR. Infrastructure only this phase.
- **Deploy target:** developer laptop only. Runs against a local PostgreSQL instance owned by the developer.

## Security & SIT Readiness Status

> Concise status only — full detail lives in the closure docs (linked below); do not duplicate them here.

- **Spring security review:** deep review done 2026-06-24 (adversarial, scored 6.0/10 initially). Findings-of-record + remediation banner in [docs/security/spring-security-deep-review.md](../docs/security/spring-security-deep-review.md).
- **Remediation:** **F-01–F-07 + F-10 closed and verified** (JWT secret fail-fast/blocklist, SVG rejection, `jti` revocation + real logout, security headers/CSP/HSTS, committed-secret cleanup, SIT logging hygiene, reveal audit, brute-force lockout + rate limiting). Compile green; 6 security-guarding unit test classes pass (31 tests, 0 failures). Documentation/comments aligned (no stale BCrypt-write / AES-GCM-active / SVG-allowed / logout-no-op claims remain).
- **UAT readiness:** **Conditionally Ready** — provided host env secrets are set (fail-fast otherwise), dispatch stays OFF (default), and the historically-committed Coocon keys are rotated.
- **PROD blockers:** (1) secret rotation (Ops); (2) F-09 callback hardening before any dispatch enablement; (3) action-level authz (R-02); (4) multi-instance durability decision for the in-memory revocation/rate-limit stores. Accepted legacy constraints: F-08 (AES-256-CBC cert cipher) + F-11 (unsalted SHA-256 passwords) — kept for JEX coexistence.
- **Closure docs:** [docs/security/README.md](docs/security/README.md) (index) → closure report, remaining-risk register, ops rotation handoff, required-environment-secrets; SIT run-book at [docs/deployment/sit-security-deployment-checklist.md](docs/deployment/sit-security-deployment-checklist.md).

## Tech Stack

| Layer | Choice |
|---|---|
| Runtime | Java 21 (Eclipse Temurin, Gradle toolchain) |
| Framework | Spring Boot 3.5.13 |
| Build | Gradle (Groovy DSL) — `./gradlew build`, `./gradlew bootRun`, `./gradlew test` |
| Web | spring-boot-starter-web |
| Persistence | spring-boot-starter-data-jpa + Hibernate, PostgreSQL driver |
| Migrations | **Flyway** (`flyway-core` + `flyway-database-postgresql`), `src/main/resources/db/migration/` |
| Security | spring-boot-starter-security + **jjwt 0.12.6** (HS256/HS512) |
| Validation | spring-boot-starter-validation + Jakarta Bean Validation |
| Boilerplate | Lombok (compile-only + annotation processor) |
| Dev UX | spring-boot-devtools |
| Tests | JUnit 5 + spring-boot-starter-test + spring-security-test |
| Excel | **Apache POI 5.x** (`poi-ooxml`) — used by `common/export/ExcelWorkbookWriter` (SXSSF streaming) for the shared XLSX export feature. Frontend never carries an XLSX library; backend is the single producer. |

**Not currently in `build.gradle`** (do not assume they are): SpringDoc OpenAPI, Testcontainers. Add them in the sprint where the first feature actually needs them.

**Entry point:** `com.kosign.wecms.WecmsApiMainApplication`
**Group / artifact / version:** `com.kosign.wecms` / `wecms-api` / `0.0.1-SNAPSHOT`
**VCS:** Git — `kosign-wecms/wecms-api` on GitLab.

## API Naming & Serialization Standards

> **CTO directive (2026-05-08):** legacy JEX naming is retired. Wire shape is lowercase snake_case; envelope is `status` + `data`; every error carries a traceable id. Do not introduce, copy, or restore legacy patterns.

### Wire JSON

- **Property names: lowercase snake_case.** `user_id`, `user_nm`, `access_token`, `bank_cd`, `field_id`, `total_count`, `banks_available`, `disp_ord`. No UPPERCASE, no camelCase, no kebab-case.
- **Envelope: `{ status, data, timestamp?, path?, trace_id? }`.** Successes ship `{ status, data }` only; errors add `timestamp`, `path`, `trace_id` via `@JsonInclude(NON_NULL)`. Never `COMMON_HEAD` / `REC` — that envelope is retired.

```json
// success
{ "status": { "error": false, "code": "WBI0000", "message": "Success" }, "data": { ... } }

// error
{
  "status":    { "error": true, "code": "WBE1009", "message": "ID or password does not match." },
  "timestamp": "2026-05-08T14:46:49Z",
  "path":      "/api/v1/auth/login",
  "trace_id":  "20260508144649PT06592776769567DEVL"
}
```

### Java fields → wire mapping

- Java DTO fields stay **camelCase** (`expiresIn`, `accountNo`, `userNm`).
- Conversion to snake_case is enforced **globally** by [`JacksonConfig`](src/main/java/com/kosign/wecms/config/JacksonConfig.java) which sets `PropertyNamingStrategies.SNAKE_CASE` on the auto-configured `ObjectMapper`. No per-DTO `@JsonNaming`. No per-field `@JsonProperty` "for safety" — the strategy already does the right thing.
- `@JsonProperty` is reserved for the rare wire name that diverges from the snake-cased Java field. The single canonical example: `LoginResponse.accessToken` carries `@JsonProperty("access_token")` because OAuth-style takes precedence over strict `access_token` snake. Adding any new `@JsonProperty` in code review requires a written justification in the MR.
- DTOs use Java **records**. Validation lives on record components (`@NotBlank`, `@Size`, `@Pattern`).
- `Map<String, Object>` shouldn't escape `service/` into a controller response — return a typed DTO. The export pipeline's intentional `Map<String,Object>` rows (FIELD_ID-keyed, in `common/export/`) are workbook-internal and never serialized as JSON.

### Forbidden (caught at review)

- `@JsonProperty("USER_ID")` / `@JsonProperty("ACCESS_TOKEN")` / any `@JsonProperty` whose value is UPPERCASE.
- `Map.of("CODE", ..., "MESSAGE", ...)` returned from a controller — use `BusinessException` + the standard envelope.
- A new `CommonHead` / `ApiResponse` factory that emits anything other than the `{ status, data, timestamp?, path?, trace_id? }` shape.
- Re-introducing a `COMMON_HEAD` / `REC` envelope (retired).
- Custom serializers / deserializers added to "match legacy JEX" — there is no JEX compatibility surface in this repo.

### Error handling — every error envelope MUST carry

| Field | Source | Notes |
|---|---|---|
| `status.error` | `true` | Boolean flag — frontend's primary success/failure signal |
| `status.code` | `WBExxxx` / `WBIxxxx` | Add to [messages.properties](src/main/resources/messages.properties) before throwing |
| `status.message` | localized | Resolved via `MessageResolver` from the user's `Accept-Language` |
| `timestamp` | `Instant.now()` | ISO-8601, UTC |
| `path` | `req.getRequestURI()` | The endpoint that failed |
| `trace_id` | [`TraceId`](src/main/java/com/kosign/wecms/common/exception/TraceId.java) via MDC | JEX-style id grepable in server logs and the `X-Trace-Id` response header |

**Never:**
- Return a generic `UNKNOWN` code with no context. Use `WBE9999` from the catch-all handler — it always logs the full stack trace at `error` level alongside the request-scoped `traceId`.
- Swallow an exception in a controller / service. If you really need to recover, log at `warn` with the original exception as the cause.
- Return a stack trace or internal hostname in `status.message`. The response body is user-facing.
- Return HTTP 200 for an unexpected server fault. Business outcomes (BusinessException) → 200; client mistakes → 4xx; server faults → 5xx. Full table in "Exception → response mapping" below.

### Refactoring rules — when porting a JEX module

1. **Strip every `@JsonProperty("UPPER_SNAKE")`.** Java field name + global SNAKE_CASE strategy is enough. Add `@JsonProperty` back only when the wire name truly diverges from the Java field.
2. **Replace any `COMMON_HEAD` / `REC`-style structure** with `ApiResponse.ok(data)` / `ApiResponse.error(code, message, path, traceId)`. Don't define a parallel envelope.
3. **Replace `Map<String, Object>` controller returns** with a typed Java record DTO.
4. **Translate JEX ML codes (`P00001` etc.) into `WBExxxx` / `WBIxxxx`.** Don't reuse the JEX code verbatim — add the new code to `messages.properties` and reference the legacy code in the migration doc.
5. **Match the new feature's entity / DAO / IDO XML / WSVC XML / `_act.jsp` against the PROD Active Table List** (under "PROD DB Table Scope for Refactoring") before writing any Flyway. If a referenced table isn't in the active list, it doesn't get an entity.
6. **Update the matching frontend wire type + mappers in the same MR.** This is the full-stack ownership rule from the workspace root [CLAUDE.md](../CLAUDE.md). The frontend repo's wire types in [lib/types/api.ts](../wecms-web/lib/types/api.ts) must flip to lowercase the moment your DTO does.

### Exceptions (non-rules) — DB schema + JPA `@Column`

The **database schema** stays UPPER_SNAKE forever — column names like `BIZ_KEY`, `CMPN_NM`, `BUSI_INFM` mirror the legacy JEX schema column-for-column so the data copies cleanly (see "Legacy Schema Parity Rule" below). JPA `@Column(name = "UPPER_SNAKE")` annotations on entities are correct and stay. The naming convention above governs **JSON wire shape only**, not SQL.

### Exceptions (non-rules) — external Coocon/NXiSAS gateway DTOs

The lowercase snake_case wire rule governs **WeCMS internal** API/Web contracts only. The Coocon/NXiSAS smart-gateway DTOs under [`domain/banking/nxisas/gateway/dto/`](src/main/java/com/kosign/wecms/domain/banking/nxisas/gateway/dto/) (`NxisasApiIn`/`NxisasApiOut`/`NxisasCommon`/`NxisasResult`/`NxisasThread`/`NxisasOutput`/`NxisasQnaJobInput`/`NxisasLoginJobResult`) bind an **external vendor integration contract**, not the WeCMS REST wire. Their `@JsonProperty` values **must match the Coocon/NXiSAS documentation verbatim** — including the vendor's irregular spellings (`SAS_SessionID`, `Auth_key`, `Tr_seq`, `Result_mg`, `Dsnc`, `CallBackfunc`, `API_SEQ`, `Module`/`Class`, …). These were verified against the vendor docs on 2026-06-07.

- **Do NOT** migrate these to lowercase snake_case, rename them, or otherwise change vendor request/response casing.
- **Do NOT** edit the vendor documents or `.exe` files. Vendor docs (read-only, for verification) live at `C:\WABOOKS PROJECT\WABOOKS_NEW_DOC\etc\COOCON API` — open via `unzip -p "<file>" word/document.xml` (PowerShell is deny-listed in this env).
- When grepping for stray UPPER_SNAKE `@JsonProperty`, **exclude `/nxisas/gateway/`** — a hit there is expected and correct, not a defect.

Final convention: **WeCMS API/Web contract = lowercase snake_case; external Coocon/NXiSAS gateway contract = vendor-defined casing.**

### Refactor identifier naming — slugs, not WSVC ids

Legacy JEX WSVC / menu ids (`comm_0220_001`, `fnsh_0001_001`, `pymn_0010_002`, …) are JSP-architecture artifacts. **In Spring code, use kebab-case slug identifiers — never legacy WSVC ids.** Cross-tree summary: workspace [CLAUDE.md](../CLAUDE.md) §"Cross-Tree Rules → Refactor work".

| Surface | Slug-based (correct) | Legacy WSVC id (forbidden in this surface) |
|---|---|---|
| REST URI | `/api/v1/settings/firm-banking-settings/...` | ❌ never `/api/v1/comm_0220_001/...` |
| Java package | `com.kosign.wecms.domain.setting.firmbankingsettings` | ❌ never `com.kosign.wecms.domain.comm.comm_0220` |
| Class names | `FirmBankingSettingsController`, `FirmBankingSettingsService`, `FirmBankingSetting`, `FirmBankingSettingId` | ❌ never `Comm0220Service`, `TbFirmConfController` |
| DTO records | `FirmBankingSettingRow`, `FirmBankingSettingCreateRequest` | ❌ never `Comm0220R001Response` |
| i18n message keys | `audit.firm_banking_settings.*`, `settings.firm_banking_settings.*` | ❌ never `audit.comm_0220_001.*` |
| Log / MDC tags | `firm-banking-settings` | ❌ never `comm_0220_001` |
| `AUDIT_LOGS_INFM.WSVC_ID` | refactor route slug, e.g. `firm-banking-settings` (override 2026-05-09) | ❌ never `comm_0220_001` for new writes — V67 back-fills any local rows that still carry the legacy value |
| View Setting `WEB_SVC_ID` | refactor route-based slug from the central registry, e.g. `banking-account-inquiry-vnd` (see [wecms-web/lib/viewSettingScreens.ts](../wecms-web/lib/viewSettingScreens.ts)) | ❌ never `fnsh_0001_001` (legacy registry retired in V32) |

Legacy WSVC ids are preserved **only** in two buckets going forward:

1. **JEX file:line citations** in Javadoc / migration prose / audit notes — e.g. `// Mirrors comm_0220_002_c001_act.jsp:84-88` is fine.
2. **Migration traceability prose** in `docs/refactor-wecms/migration/<slug>-mapping.md` — narrative paragraphs explaining "this refactor service replaces JEX `comm_0220_001`."

> **Audit-DB-constant bucket retired 2026-05-09.** Earlier revisions reserved a third bucket for `AUDIT_LOGS_INFM.WSVC_ID` to remain `'comm_0220_001'` so the legacy JEX detail-audit query at `comm_0220_003_r001_act.jsp:74-86` could find refactor-written rows. That decision was overridden — refactor-written audit rows now use the route slug. JEX queries the local refactor DB nowhere (refactor is local-only R&D), so the cross-system compatibility motivation evaporated. A future feature with genuinely irreducible cross-system needs would need a fresh PO decision before reviving this bucket. See [docs/refactor-wecms/features/settings/firm-banking-settings.md §13 OQ-6](../docs/refactor-wecms/features/settings/firm-banking-settings.md) and the V67 back-fill migration.

If you find yourself reaching for a legacy WSVC id in any new Java field name, REST URI segment, package, class, i18n key, log tag, **or DB column** (including `AUDIT_LOGS_INFM.WSVC_ID`), **stop** — that's a bug. Use the slug.

The matching frontend rules (Next.js routes, View Setting `screenId`s) live in [wecms-web/CLAUDE.md](../wecms-web/CLAUDE.md) §"Routing" + §13.



## Architecture Rules

Lightweight domain-driven packaging — **NOT** full tactical DDD. No aggregates, value objects, or domain events unless a specific feature genuinely requires them.

```
com.kosign.wecms
├── WecmsApiMainApplication.java
├── config/
│   ├── CorsConfig.java              — CorsFilter bean; reads wecms.cors.allowed-origins
│   ├── JwtProperties.java           — @ConfigurationProperties("wecms.jwt")
│   ├── SecurityConfig.java          — SecurityFilterChain + PasswordEncoder bean
│   └── TenantProperties.java        — @ConfigurationProperties("wecms.tenant"); rejects % wildcard
├── common/
│   ├── entity/BaseEntity.java       — REG_ID/REG_DTM/MOD_ID/MOD_DTM audit columns (@PrePersist/@PreUpdate)
│   ├── enums/LangCd.java            — enum {EN, KO, KM, VI}; fromAcceptLanguage(), fromCodeOrDefault()
│   ├── exception/
│   │   ├── BusinessException.java   — extends RuntimeException; holds code + Object[] args
│   │   └── GlobalExceptionHandler.java — @RestControllerAdvice; maps BusinessException,
│   │                                     MethodArgumentNotValidException, generic Exception
│   ├── i18n/MessageResolver.java    — MessageSource wrapper; resolves WBE/WBI codes via LocaleContextHolder
│   ├── response/
│   │   ├── ApiResponse.java         — `status` + `data` envelope (lowercase snake_case); static ok(T) / ok(code,T) / error(code,message)
│   │   └── CommonHead.java          — ERROR bool, CODE, MESSAGE; factories for success/error
│   ├── security/
│   │   ├── JwtTokenProvider.java    — HS256/HS512 token issue/parse; fail-fast on missing/short secret
│   │   ├── JwtAuthenticationFilter.java — OncePerRequestFilter; reads Bearer header;
│   │   │                                  sets UsernamePasswordAuthenticationToken;
│   │   │                                  populates TenantContext (userId, bizKey, langCd)
│   │   ├── WecmsPasswordEncoder.java    — coexistence: legacy SHA-256 (public.hash()) write, SHA-256 read (BCrypt tolerated for old dev rows), never upgrades
│   │   └── LegacyHashPasswordEncoder.java — replicates public.hash(txt): SHA-256 → base64
│   └── tenant/TenantContext.java    — ThreadLocal<Principal> holder; current(), currentUserId(),
│                                      currentBizKey(), currentLangCd() static accessors
└── domain/
    └── auth/                        — the only domain module implemented
        ├── controller/AuthController — @RequestMapping("/api/v1/auth")
        ├── dto/
        │   ├── LoginRequest.java    — record; user_id, user_pw; @NotBlank @Size; compact() trims
        │   ├── LoginResponse.java   — record; access_token, token_type, expires_in, user_id, user_nm,
        │   │                          biz_key, lang_cd, cmpn_nm
        │   └── MeResponse.java      — record; user_id, user_nm, biz_key, lang_cd, cmpn_nm (no token)
        ├── entity/
        │   ├── BusiInfm.java        — BUSI_INFM table; PK = BIZ_KEY; 40+ columns; extends BaseEntity
        │   ├── UserInfm.java        — USER_INFM table; PK = USER_ID; isActive/isLocked/isDeleted helpers
        │   ├── UserBusiConnInfm.java — USER_BUSI_CONN_INFM; composite PK (USER_ID, BIZ_KEY)
        │   ├── UserBusiConnInfmId.java — Serializable composite key
        │   ├── UserLoginHis.java    — USER_LOGIN_HIS; composite PK (USER_ID, REG_DTM); no BaseEntity
        │   └── UserLoginHisId.java  — Serializable composite key
        ├── repository/
        │   ├── UserInfmRepository.java           — JpaRepository<UserInfm, String>
        │   ├── BusiInfmRepository.java            — JpaRepository<BusiInfm, String>
        │   ├── UserBusiConnInfmRepository.java    — + findByUserIdAndBizKeyAndDelYn()
        │   └── UserLoginHisRepository.java        — JpaRepository<UserLoginHis, UserLoginHisId>
        └── service/
            ├── AuthService.java          — login(req, ipAddr, langCd) + me()
            └── LoginHistoryService.java  — record(userId, ipAddr, rsltCd, rsltCont) @REQUIRES_NEW
```

**Package rules:**
- New feature code → new `domain/{jex-module-code}/` package (e.g. `pymn`, `mbmr`, `comm`, `admn`).
- Do not promote helpers to `common/` until more than one domain consumes them.
- Within a domain: **controller → service → repository → entity + dto**. No cross-layer shortcuts.
- No controller-to-controller calls. No cross-domain repository access.
- Business logic lives in `service/`. Entities are JPA mapping + trivial boolean helpers, not rich domain objects.

## Endpoints

| Endpoint | Method | Auth | Returns |
|---|---|---|---|
| `/api/v1/auth/login` | POST | Public | `ApiResponse<LoginResponse>` |
| `/api/v1/auth/logout` | POST | Public | `ApiResponse<Void>` (stub — client clears token) |
| `/api/v1/auth/me` | GET | Protected (JWT) | `ApiResponse<MeResponse>` |
| `/api/v1/navigation` | GET | Protected (JWT) | `ApiResponse<NavigationResponse>` — resolved per-product menu tree for the session (`Product Tree ∩ grants` for a normal user, full `Product Tree` for an admin). Product type read from the JWT (`TenantContext.currentPrdtType()`), never a client param. |
| `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | GET | Protected | `ApiResponse<ScreenOptionListResponse>` (merged sys + user for one area) |
| `/api/v1/screen-options/{webSvcId}/areas` | GET | Protected | `ApiResponse<ScreenAreaListResponse>` — distinct DYM_AREA_IDs + column count per area |
| `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | PUT | Protected | save user overrides for one area → returns refreshed merged list |
| `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | DELETE | Protected | reset user overrides for one area → returns system defaults |
| `/api/v1/banking/accounts/export` | POST | Protected | streams `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (XLSX) for Banking > Account Inquiry — body is the shared `ExcelExportRequest` shape (see common/export rule below) |
| `/actuator/health` | GET | Public | Spring Actuator |
| Everything else | any | Protected | 401 if no valid JWT (via `JwtAuthenticationEntryPoint`) |

## REST API Design Conventions (Canonical)

> **Source:** consolidated from [docs/audits/rest-api-design-consistency-audit.md](../docs/audits/rest-api-design-consistency-audit.md) (2026-06-24, score 7.5/10). These are the **canonical rules for every NEW endpoint.** Existing endpoints that diverge are **grandfathered** — they are not rewritten in unrelated MRs; they migrate behind a future `/api/v2` boundary with paired frontend changes. The "Known divergences" notes below exist so a new developer is not misled into thinking the whole surface already conforms. **When this section and an older scattered rule disagree, this section wins for new code.**

### 1. Pagination — `page` + `size`, zero-based

- List endpoints paginate with exactly two query params: **`page`** (zero-based, default `0`) and **`size`** (integer, default `20`, clamped to a per-resource `MAX_PAGE_SIZE`).
- `size` is an **`int`**, never a string. Do not introduce `page_no`, `page_per_cnt`, `pageNo`, `pageNum`, `offset`, `perPage`, or a one-based `page`.
- The unpaged filtered total ships as **`total_count` (`long`)** on the list DTO (see §7). A future `common/web/PagedResponse<T>` will standardize `{ items, total_count, page, size, total_pages }`.
- **Known divergences (grandfathered):** `ReportController` `/daily-financial/transactions` and `SmartOrganizerController` use a one-based `page`; `TransferHistoryController` uses `page_no` + `page_per_cnt` (string). New endpoints must not copy these.

### 2. Response envelope — `ApiResponse<T>` on every JSON endpoint

- Every JSON endpoint returns **`ApiResponse<T>`** = `{ status: { error, code, message }, data }`; errors add `timestamp`, `path`, `trace_id`. Success → `ApiResponse.ok(data)`; failure → throw `BusinessException("WBExxxx")` (never hand-roll a body).
- Wire is lowercase snake_case (global Jackson `SNAKE_CASE`). Do not define a second envelope shape.
- Verified 2026-06-24: 304 of 315 handlers return `ApiResponse<T>` — the envelope is effectively universal. This consistency is what makes a single shared frontend `unwrap()` safe to extract (frontend action-plan **H-2**).

### 3. Binary download / export — the documented exception to §2

- File **download** and Excel **export** endpoints return **`ResponseEntity<InputStreamResource>` / `ResponseEntity<Resource>` / a streamed body**, **not** `ApiResponse<T>`. This is the **only** sanctioned envelope exception (11 endpoints as of 2026-06-24).
- Such endpoints set `Content-Type: application/octet-stream` (or the XLSX MIME for exports) and an **RFC 5987** `Content-Disposition: attachment; filename*=UTF-8''…` so Korean/Khmer/Vietnamese filenames round-trip.
- **Frontend contract:** these endpoints are consumed via `lib/file.ts` / `lib/excel.ts` (blob handling) and **must never be routed through the shared `unwrap()`** — `unwrap()` is for JSON `ApiResponse<T>` only.
- See §9 for the security rules these endpoints must follow.

### 4. Error handling contract — single funnel; `BusinessException` → HTTP 200

- `GlobalExceptionHandler` (`@RestControllerAdvice`) is the single funnel. Every error carries `code` + localized `message` + `path` + `trace_id` (also in the `X-Trace-Id` header). Never return a stack trace, raw exception text, or internal hostname in `status.message`.
- **`BusinessException` returns HTTP 200 by design** — a documented business outcome. The frontend reads `status.error`, **not** the HTTP code, for known business results. This is intentional and surprises REST-trained newcomers, so it is stated explicitly here.
- Status mapping (canonical): `BusinessException` → **200**; bean/`@Valid`/`@Validated`/missing-param/type-mismatch validation → **200** (`WBE_VALIDATION`); `IllegalArgumentException` / malformed JSON → **400**; method not allowed → **405**; unsupported media type → **415**; no handler → **404**; `AuthenticationException` → **401**; `AccessDeniedException` → **403** (`WBE1003`); `DataAccessException` → **500** (`WBE_DB`); `TimeoutException` → **504**; any other → **500** (`WBE9999`, logged with stack + traceId). JWT 401 is handled by `JwtAuthenticationEntryPoint` (fires before the advice).
- New error categories add a handler row here + a `WBE`/`WBI` key in `messages.properties` — never a parallel envelope.

### 5. Query-parameter naming — lowercase snake_case

- All query params are **lowercase snake_case**: `sort_field`, `sort_direction`, `page`, `size`, `from_dt`, `to_dt`, `search`, `bank_cd`, `acct_no`, `as_of`. No camelCase, no kebab-case.
- Sorting uses `sort_field` (a View Setting `FIELD_ID`) + `sort_direction` (`ASC`/`DESC`), resolved through the per-domain whitelist enum (never concatenated into SQL); unknown values fall back to the default ORDER BY.
- **Known divergences (grandfathered):** several existing endpoints ship camelCase params — `sortField`/`sortDirection` (most list controllers), and `asOf`/`bankCd`/`acctNo`/`currencyScope`/`activeAccountsOnly` (`ReportController`). The older common-sort reference snippet elsewhere in this file shows `@RequestParam("sortField")` — that camelCase form is **legacy**; the canonical form for new endpoints is `sort_field`. `TransferHistoryController` already uses the canonical snake_case.

### 6. HTTP method conventions

- **GET** = reads only, never state-changing. Filtered reads are GET with query params (do **not** model a filtered read as POST).
- **POST** = create, or a genuine domain **action/command** (`/approve`, `/reject`, `/dispatch`, `/request-approval`, `/bulk-delete`). Action sub-resources are acceptable for a banking/payment domain — they are not CRUD and should not be forced into it.
- **PUT** = full replacement of a resource. **PATCH** = partial update. Pick one per operation and keep it consistent across modules — e.g. "set approval line" is **`PUT /{id}/approval-line`** (the majority form).
- **DELETE** = delete / clear a sub-resource. Idempotent toggles use a paired `PUT`+`DELETE` on the sub-resource (e.g. `.../confirm`).
- **Known divergences (grandfathered):** `OperationStatusController` exposes `POST /operation-status/inquire` (read-as-POST) and `POST /operation-status/update` (update-as-POST); `OverseasPaymentController` uses `PATCH /{id}/approval-line` (vs PUT in 5 peers) and `GET /result/export` (vs POST elsewhere). New endpoints follow the canonical rules above.

### 7. DTO naming & shape conventions

- Java **records**, camelCase fields, auto-serialized to snake_case by the global Jackson strategy. `@JsonProperty` only when the wire name genuinely diverges (the one canonical case is `access_token`).
- **Suffix vocabulary (canonical — keep predictable):**
  - `…Request` — request body (e.g. `LoginRequest`, `ContactCreateRequest`).
  - `…Response` — single-object response (e.g. `MeResponse`).
  - `…Row` — one row in a list payload (e.g. `AccountRow`).
  - `…ListResponse` — a list payload `{ rows/items, total_count, … }` (e.g. `ContactListResponse`).
  - `…Detail` — a detail/expanded view when it differs from `…Response`.
  - Reserve `…Command` for internal service inputs, not wire DTOs.
- Verified 2026-06-24: suffix usage is already consistent (`Request` 96 · `Row` 78 · `Response` 65 · `ListResponse` 48). Avoid mixing `…Result` with `…Response` for the same concept.
- Vendor/external DTOs (`domain/banking/nxisas/gateway/`) keep **vendor casing** verbatim — they are not the WeCMS wire and are exempt from the snake_case rule.

### 8. Authorization — canonical pattern + two pre-deploy gates

- Every business `/api/v1/**` endpoint is gated by `@PreAuthorize("@menuAuthz.canAccess('<route-slug>')")` (stack `hasRole(...)` / `@userMgmtAccess` on top, never weaker). The only unannotated handlers are the documented allow-list (auth/public, navigation, screen-options, country, sync-history, blocked-chars, and the dispatch callbacks).
- **Pre-deploy gate R-02 — action-level authorization:** per-action authority (Request-approval / Delete / Dispatch — "R/D/T") is **not yet enforced backend or frontend** (the frontend uses a static `{R:true,D:true,T:true}` stub). Before any UAT/PROD deploy, define the per-action authority source and add server-side `@PreAuthorize` for state-changing actions. The frontend flag is UX only and is never the boundary.
- **Pre-deploy gate R-03 — dispatch-callback protection:** the callback controllers under `/api/v1/payment/dispatch/callbacks` (`BulkExecutionResultController`, `BulkReceptionResultController`) are **unauthenticated**. Acceptable for local R&D; before any non-local deploy they must be restricted by network / mTLS / shared-secret. Document why they are public when you do.
- Neither gate blocks local R&D, but **both block UAT/PROD** and must be closed at cutover.

### 9. File download/export security (applies to §3 endpoints)

- Resolve the physical path under a tenant-scoped root and **reject traversal**: normalize, then verify `resolved.startsWith(tenantRoot)` (reference: `ErpTrnsHistoryFileResolver.resolve(...)`). Never build a path directly from a client-supplied filename without this guard.
- Enforce tenant scope (`TenantContext.currentBizKey()`) on every download/export — never cross tenants.
- **Known gap (R-08, MEDIUM):** the traversal guard is currently a per-feature pattern, not a shared primitive — a future download handler could omit it. New download endpoints must replicate the guard (or route through a shared `common/file` helper if/when one is extracted).

## Coding Rules

**Controllers**
- Return `ApiResponse<T>`. Success: `ApiResponse.ok(rec)`. Error: throw `BusinessException("WBExxxx")`.
- Keep controllers thin: parse request, resolve incidental data (client IP, `Accept-Language` → `LANG_CD`), delegate to service, wrap result.
- DTOs use Java **records** with **camelCase Java fields** that auto-serialize as **lowercase snake_case** via the global Jackson naming strategy ([`JacksonConfig`](src/main/java/com/kosign/wecms/config/JacksonConfig.java)). Per-field `@JsonProperty` is only added when the wire name diverges from the snake-cased Java field (e.g. `accessToken` → `access_token`). Validation on record components (`@NotBlank`, `@Size`, `@Pattern`).

**Services**
- Annotate mutating methods with `@Transactional`. Audit writes that must survive outer rollback use `@Transactional(propagation = REQUIRES_NEW)`.
- Throw `BusinessException(CODE, args…)` for known error paths. Code resolves via `messages*.properties`.
- Never leak raw Hibernate / JDBC exceptions — let the global handler map unexpected ones to `WBE9999`.

**Repositories**
- Spring Data JPA. Prefer derived method names over `@Query` until a native/JPQL query is actually needed.
- A repository method returning rows from a tenant-scoped table **must** constrain on `BIZ_KEY`. Never pass a `%` wildcard `BIZ_KEY`.

**Entities**
- Extend `BaseEntity` for legacy tables so audit columns are inherited.
- `@Table(name = "UPPER_SNAKE")` and `@Column(name = "UPPER_SNAKE")` — mirror legacy JEX schema column-for-column.
- `@IdClass` for composite keys.
- No JPA cascade on cross-aggregate relations unless there is a concrete need.

**Config / properties**
- Typed `@ConfigurationProperties` records, not scattered `@Value`. Enabled via `@EnableConfigurationProperties` on `SecurityConfig`.
- Every sensitive value has an env var (`WECMS_DB_PASSWORD`, `WECMS_JWT_SECRET`). Fail-fast in the constructor if a required value is missing.

**Lombok**
- `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j` — trivial only. No `@Data` on entities. No `@Builder` on DTOs.

**Style**
- One class per file.
- Javadoc on public service methods that implement a JEX behaviour — cite the JEX path being ported (e.g. `SzonHelper.logn_2001_001_r001`).
- Comments explain WHY. Never comment WHAT.

## Feature Status

| Module | Endpoints | Status |
|---|---|---|
| `domain/auth` | `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` | Working |
| `domain/navigation` | `GET /api/v1/navigation` | **Implemented full-stack (Phases 1–5 + F-3 complete, 2026-06-06).** Product-tree-driven navigation: the server selects the menu tree from the session's `prdt_type` (`F→WE_CMSVN`, `M→WE_CMSVN_LUMENS`, `H→WE_CMSVN_HAENGSUNG`, read from `TenantContext.currentPrdtType()` — never a client param) and returns the **resolved visible tree** — `Product Tree ∩ USER_MENU_AUTH_INFM grants` for a normal user, the full `Product Tree` for an admin (`authGrCd=ADMIN` bypasses the per-user ∩ only, never the product scope). Reuses `MenuAuthorizationService.grantedMenuCds(...)` unchanged for the ∩ term. Parents collapse when all children are filtered out; a grant for a `permission_cd` outside the tenant's product tree is inert (no node to render). New tables `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` (V242 schema, V243 seed from the JexAdmin export — F=41, H=33, M=41 nodes). `permission_cd` values are the verified live kebab menuAuth codes (see [permission-code-normalization-verification.md](../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md)); **Daily Financial split** — F/M carry `banking-reports-daily-financial` (`rept_0015_001`), H carries `banking-reports-daily-financial-hs` (`rept_0017_001`). `NavigationServiceTest` 8/8 green (F/M/H admin + non-admin, incl. out-of-tree-grant inert, no-grants empty, and the HS Daily-Financial split). **Frontend wired (Phases 2–5):** all four `wecms-web` nav surfaces — Sidebar, Top Nav, mobile MENU switcher, and Menu Entitlement — consume this endpoint via the shared `useNavigation` hook (hardcoded menu arrays removed; only node-id-keyed presentation registries for label/icon remain). Menu Entitlement is generated from the navigation tree (`entitlementTree.ts`); the former `mockMenuEntitlements.ts` was removed in Phase 5. **Server-side enforcement (F-3):** the same `permission_cd`s gate every business endpoint via `@menuAuthz` (see the Authority-model bullet + [endpoint-authorization-matrix.md](../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md)). Design: [backend-navigation-implementation-design.md](../docs/jex/features/menu-product-type-management/backend-navigation-implementation-design.md). |
| `domain/setting` | `GET / PUT / DELETE /api/v1/screen-options/{webSvcId}/{dymAreaId}` + `GET .../areas` (View Setting) | Working — **global / common** WeCMS feature, screen-agnostic. Endpoints accept any `WEB_SVC_ID` + `DYM_AREA_ID` and are reused by every list page across the product (Banking, Payment, Add-on, Settings, future modules). `WEB_SVC_ID` follows the semantic kebab-case convention (`banking-account-inquiry-vnd`, etc.) per [docs/jex/features/view-setting.md §1.1](../docs/jex/features/view-setting.md). First seeded screen: `banking-account-inquiry-vnd` / `default` (V32). New screens add their seed rows in their own feature migration — no controller / service / DTO changes required. |
| `common/export` + `domain/banking/export` | `POST /api/v1/banking/accounts/export` (XLSX) | Working — **global / common** WeCMS feature. Shared `ExcelExportService` + `ExportableScreenRegistry` + `ExcelWorkbookWriter` (Apache POI SXSSF) consumed by per-resource controller endpoints. First wired screen: `banking-account-inquiry-vnd` (Account Inquiry VND) via `AccountInquiryExportProvider`, which reuses `AccountService.list` so export and list share one query path. Backend computes the count + totals subheader. Adding a new screen = one new `ExportRowProvider` class + one `@PostMapping` per the resource's existing controller. See the **Excel export — `common/export/`** rule below. |
| `domain/setting/erptransactionhistory` | `GET /api/v1/settings/erp-transaction-history` (list + 4 tab counts), `GET .../{seqNo}/download` (binary), `GET .../{seqNo}/thumbnail` (stub → `WBE9001`) | Working — read-only audit ledger of files exchanged between WeCMS and the customer's ERP. Mirrors legacy `comm_0219_001_r001` + `comm_0219_001_r002`. Tenant-scoped via `TenantContext.currentBizKey()`; sort whitelist `ErpTrnsHistorySortField` (REG_DTM / ERP_TYPE / TRNS_TYPE_CD / REG_USER); LEFT JOIN `UserInfm` replaces legacy `WB_GET_USER_NM`. Download streams from `wecms.erp-transaction-history.storage-root` (default `C:/WECMS PROJECT/File Data`). View Setting **NOT adopted** (OQ-FE-2 — legacy JEX panel has no toolbar list-icon button). Per-feature i18n keys (`settings.erp_transaction_history.direction.*` + `.type.*`) emitted on the wire so frontend renders via `t(key)`. Upload write-path intentionally NOT ported (OQ-1) — each future Payment feature writes its own audit row. |
| `domain/setting/contacts` | `GET /api/v1/settings/contacts` (list), `GET /{trcoKey}` (detail), `POST /` (create), `PUT /{trcoKey}` (update), `DELETE /{trcoKey}` (single soft-delete), `POST /bulk-delete` (bulk soft-delete) | Working — mirrors legacy `comm_0184_001` + `comm_0184_002_c001` + `comm_0184_005_u001` + `comm_0184_001_u001`. CRUD over `TRCO_INFM` + `TRCO_ADDR_INFM` + `TRCO_ACCT_INFO` + `TRCO_OVRS_INFM` + `PGRO_BSAC_LNKN_INFM`. Tenant-scoped via `TenantContext.currentBizKey()`; sort whitelist `ContactSortField` (HAND_TRCO_KEY / TRCO_CMNM / TRCO_TYPE / TRCO_TAX_ID); paged + filtered list with `search` + `trco_type`. **TRCO_KEY allocation** routes through `LegacyFunctionRepository.nextTrcoKey` (V113 `wb_get_next_val` branch + `sq_trco_key`, OQ-4); one-shot retry on `DataIntegrityViolationException`. **Bank-name resolution** routes through `LegacyFunctionRepository.resolveNapasOrCitadBankName` (V114 ported function over `tb_firm_bank_code`, OQ-7) — `BankCdResolver` NOT consulted in this flow. **WBE1190 in-use guard** runs on single delete, bulk delete, and the edit-time `trco_stts → 'C'` transition (OQ-2) via `AcctTrnsRcvPayInfmRepository.existsByBizKeyAndRcvPayTrcoKey`. **OQ-3** keeps `TRCO_INQ_YN` mapped + defaults to `'Y'` on create (service-internal; never on the wire). **OQ-5** no Trash UI — list query hard-codes `coalesce(trco_stts,'') <> 'C'`. **OQ-6** audit slug is `contacts`. **OQ-8** address current/previous discriminator is `disp_ord` ∈ {1, 2}, NOT `trco_addr_key`. **View Setting** adopted via V154 seed (re-applied V187; `web_svc_id = 'contacts'`, 8 columns including CHECKBOX + ETC specials). |
| `domain/setting/approvalline` | `GET /api/v1/settings/approval-line` (list + name search + sort), `GET /{apprLineNo}` (detail), `POST /` (create), `PUT /{apprLineNo}` (update + diff-sync), `DELETE /{apprLineNo}` (hard delete) | Working — mirrors legacy `comm_0222_001_r001` + `comm_0222_002_c001` + `comm_0222_003_r001` + `comm_0222_003_u001` + `comm_0222_001_d001` (refactor slug `approval-line`). Master + child CRUD over `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL` (V90). Tenant-scoped via `TenantContext.currentBizKey()`; sort whitelist `ApprovalLineSortField` (REG_DTM / APPR_LINE_NM / MOD_DTM) with default `REG_DTM DESC, APPR_LINE_NO DESC`. No server-side pagination (OQ-3, 2026-05-13 — load-all matches legacy). **APPR_LINE_NO allocation** routes through `LegacyFunctionRepository.nextApprLineNo` (V91 `wb_get_next_val` branch, OQ-7). **5-approver cap** pinned in `ApprovalLineConstants.MAX_APPROVERS` + DB column `NUMERIC(1,0)` + bean validation + service guard → `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`. **Diff-sync on Update** mirrors legacy `comm_0222_003_u001_act.jsp:104-194` — UPDATE rows in `existing ∩ incoming`, INSERT net-new, DELETE net-removed, all in one tx. **`apprvr_disp_ord` re-normalised 1..N on both Create AND Update** (OQ-4 proposed 2026-05-13; stricter than legacy Create which trusts client order). **Defensive guard:** every `apprvr_id` in the request is verified against `USER_BUSI_CONN_INFM.findAllByUserIdInAndBizKeyAndDelYn` → `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` (legacy has no such guard). **OQ-5** no `@PreAuthorize` admin gate — follows live JEX behaviour (any normal user). **OQ-6** no audit-log writes (legacy parity — JEX `comm_0222_*` writes no `AUDIT_LOGS_INFM` rows either; deliberate divergence from `firm-banking-settings` / `contacts`). **`user_img_url`** resolved per-approver via `USER_BUSI_CONN_INFM.USER_IMG`; format is slug-based `/api/v1/users/{apprvrId}/image` (endpoint itself out of scope, OQ-1) — null when no image. **View Setting NOT adopted** — legacy panel has no toolbar list-icon button (same precedent as `erp-transaction-history`). |
| `domain/setting/chartofaccounts` | `GET /api/v1/settings/chart-of-accounts` (list + search + sort), `GET /tree` (parent-picker projection), `GET /{chart_acct_seq_no}` (detail), `POST /` (create / revive), `PUT /{chart_acct_seq_no}` (update), `DELETE /{chart_acct_seq_no}` (soft delete) | Working — mirrors legacy `comm_0223_001_r001` / `_r002` / `_c001` / `_u001` / `_d001` (refactor slug `chart-of-accounts`). Full CRUD over `CHART_ACCT_INFM` (V94, 15 cols). Tenant-scoped via `TenantContext.currentBizKey()`; sort whitelist `ChartOfAccountsSortField` (CHART_ACCT_CD / CHART_ACCT_NM_EN / STTS / MOD_DTM) with default `CHART_ACCT_CD ASC` (legacy R001 parity). No server-side pagination (load-all matches legacy). **CHART_ACCT_SEQ_NO allocation** routes through `LegacyFunctionRepository.nextChartAcctSeqNo` (V95 `wb_get_next_val` branch). Per-tenant MAX+1; one-shot retry on `DataIntegrityViolationException`. **Restricted roots (ACC-01 / ACC-02 / ACC-03)** seeded for every tenant in V96; blocked from edit / delete via `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` (legacy `WBE1178` parity). **4-level hierarchy cap** enforced at write time by `ChartOfAccountsValidator.assertDepthCap` (refactor addition — legacy bakes the cap into the R002 read query only). **Cycle + self-parent guards** added at write time (refactor additions — legacy DEV's seq 104 is self-referencing; the validator catches new writes, grandfathered data is rendered with `chart_acct_level=null`). **Revive-on-create** for soft-deleted rows with the same code preserves the original `chart_acct_seq_no`; **archive-rename-on-delete** (`prefix(0..4) + last5(seq_no) + 'D'`) preserves the UK `(BIZ_KEY, CHART_ACCT_CD, DEL_YN='Y')` on collision. **Child re-parent on delete** folds legacy `comm_0223_001_u002` parent-null patch in via one `@Modifying @Query` (`reparentActiveChildren`). **`ChartOfAccountsTreeBuilder`** moves the legacy 100-line R002 CASE projection to Java — backend ships `chart_acct_level` / `has_children` / `restricted` / `breadcrumb` on every row. **OQ-1** (USER role gating) — pending PO; default ships without `@PreAuthorize`. **OQ-3** (legacy `u001:237-256` parent-cleanup branch) — DROPPED (refactor recommendation; cleanup looks like leftover debug instrumentation, pending PO confirmation per memory `project_chart_of_accounts_oq_pending`). **Audit-log** enabled: `AUDIT_LOGS_INFM.WSVC_ID = "chart-of-accounts"` (slug, not `comm_0223_001`); matches firm-banking-settings / contacts precedent. **View Setting NOT adopted** — legacy panel has no toolbar list-icon button (same precedent as approval-line / erp-transaction-history); slug reserved in frontend `SCREEN_IDS` for future popover wiring + log/audit consistency. |
| `domain/setting/firmbankingsettings` | `…/settings/firm-banking-settings` (list/detail/CRUD/audit) | **Backend implemented** (+ unit tests). Frontend not verified this pass. |
| `domain/setting/firmbankinghistory` | `…/settings/firm-banking-history` (list + `/export`) | **Backend implemented** (+ unit tests, XLSX export `FirmBankingHistoryExportProvider`). Frontend not verified this pass. |
| `domain/setting/bankaccount` | `…/settings/bank-account` | **Backend implemented** (2 services). Frontend not verified this pass. |
| `domain/setting/users` | `…/settings/users` (check-id, GET/PUT/DELETE `/{user_id}`, menu-entitlement GET/PUT, image up/get/del) | **Backend implemented** (3 services). Menu-entitlement persistence via `USER_MENU_AUTH_INFM` (V126). Frontend not verified this pass. |
| `domain/setting/logincredentials` | `…/login-credentials` (bank/GDT CRUD, `/reveal`, `/audit-logs`) — note the wire base path is `/api/v1/login-credentials` | **Implemented** — backend + frontend both wired (GDT create/update, audit-logs, reveal all call live endpoints; verified in `wecms-web` 2026-06-02, no mock fallback). |
| `domain/setting/preferences` | `…/settings/preferences` | **Backend implemented**. Frontend not verified this pass. |
| `domain/setting/environmentsettings` | `…/settings/environment-settings` | **Backend implemented** (+ tests). Frontend not verified this pass. |
| `domain/setting/purposesettings` | `…/settings/purpose-settings` (list, CRUD, `/order`, excel preview/import) | **Backend implemented** (+ tests). Frontend not verified this pass. |
| `domain/setting/vasaccount` | `…/settings/vas-account` (CRUD, `/lookup`, excel preview/import, `/export`) | **Implemented** — backend (+ unit + integration tests) and frontend present (`wecms-web/lib/api/vasAccount.ts` + `components/settings/vas-account/`). |
| `domain/banking` (`AccountController`) | `…/banking/accounts` (+ `/export`, `/{acctSeqNo}/transactions`) | **Implemented (Account Inquiry VND)** full-stack; FCY = multi-currency enhancement; Transaction-History frontend partial. |
| `domain/banking` reports (`ReportController`, `DailyCashReportController`, `CashFlowReportController`) | `…/banking/reports/{daily-financial,daily-financial-hs,daily-cash-report,cash-flow-report}` (+ `/export`) | **All four report pages now call the legacy DB functions directly (2026-06-14).** **Cash Flow** — the earlier Java reimplementation of `get_cashflow_report` is **bypassed**; the service calls the function directly (helpers `wb_get_chart_acct_level` + `wb_get_chart_acct_level_text` added to the refactor DB); remaining = numeric live verification blocked by missing `chart_acct_infm`/classified-payment data. **Daily Cash** — Java recompute bypassed; calls `rept_0900_001_rec1..4` directly (rec1/rec2 active; rec3/rec4 need `CASH_REPY_MSTR` data). **Daily Financial HS** — hardcoded stub removed; calls `fn_get_haengsung_daily_financial_report` (`REPT_TRNS_HSTR` + fn added to baseline); remaining = bank/account multi-select UI, seed rows, live HTTP smoke. **Daily Financial CMC** — Java live-compute bypassed; reads `rept_0015_001_r001_rec1` directly via `DailyFinancialCmcFunctionRepository` (07/08 KEPT by PO direction; DF-3 `activeAccountsOnly` + DF-4 `includeInternalTransfers` fixed; no `WB_INSERT_*` write-on-read, no summary-table read); remaining = numeric live verification data-blocked in R&D, `rec3`/cash + inline transactions out of scope. Frontend wired, no mock fallback. See [docs/audits/main-financial-pages-sit-readiness-action-plan.md](../docs/audits/main-financial-pages-sit-readiness-action-plan.md) + the per-feature docs. |
| `domain/banking` (`ExchangeRateController`) | `…/banking/exchange-rates` | **Backend implemented**. Frontend not verified this pass. |
| `domain/banking/ownaccounttransfer` | `…/banking/own-account-transfer/*` (9 endpoints) | **Implemented** — backend (+ tests) + frontend page landed; composer credential-collection deferred (OQ-FE-1). |
| `domain/banking/transferhistory` | `…/banking/internal-transfer/transfer-history` | **Implemented** — backend (+ tests) + frontend page landed. |
| `domain/banking/bankcredentials` | `…/banking/bank-credentials` | **Backend implemented** (2 services). Frontend not verified this pass. |
| `domain/banking/nxisas` | `BankScrapingController` (NXISAS scraping client) | **Backend implemented** (scraping spike). Frontend not verified this pass. |
| `domain/banking/smartorganizer` | `…/smart-organizer` + organizer-rule endpoints | **Implemented** — backend full-stack (2 controllers + 2 services + V172–V174) + frontend route `/banking/smart-organizer`. The earlier "discovery scaffold" label is **superseded** (2026-06-23) — it survives only in the preserved `smart-organizer/legacy-spec-{vnd,fcy}.md`. SO-2 (rule drag-reorder save UI) deferred. |
| `domain/banking/synchistory` | `SyncHistoryController` | **Backend implemented**. Frontend not verified this pass. |
| `domain/payment/approvalbox` | `…/payment/approval-box` | **Backend implemented** (+ controller test). Frontend not verified this pass. |
| `domain/payment/cardtransactions` | `…/payment/card-transactions` | **Backend implemented** (3 services + tests). Frontend not verified this pass. |
| `domain/payment/internaltransfer` | `InternalTransferController` + `TransferResultController` | **Backend implemented** (3 services + tests). Frontend not verified this pass. |
| `domain/payment` (`OverseasPaymentController`) | overseas-payment | **Backend implemented**. Frontend not verified this pass. |
| `domain/payment/expensepayment` | expense-payment | **Backend implemented** (2 services). Frontend not verified this pass. |
| `domain/payment/payrollpayment` | payroll-payment | **Backend implemented** (2 services). Frontend not verified this pass. |
| `domain/payment/vendorpayment` | vendor-payment | **Backend implemented** (2 services). Frontend not verified this pass. |
| `domain/payment/validation` | `BlockedCharsRuleController` (blocked-chars-rule) | **Backend implemented** (supporting). Frontend not verified this pass. |
| `domain/einvoice` | `…/addon/einvoices` (list + `/export`; sales & expenses share the controller) | **Implemented** — backend (+ tests) + frontend wired (server-side keyword/page/size/sort + XLSX export; verified in `wecms-web` 2026-06-02). Caveats: invoice-**status** filter still client-side (backend IR-4 gap). `EInvoiceService.resolveDocType` maps `SALES`/`I`→`'I'`, `EXPENSE`/`BILL`→`'B'`, else `WBE2002` ([`EInvoiceService.java:205-217`](src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java#L205-L217)) — the earlier "EXPENSE→`'E'` open code bug" note was **stale** (corrected 2026-06-23). |
| `domain/operation` | `…/operation-status` (`/inquire`, `/update`, `/export`) | **Implemented** — backend + frontend wired (Inquire, Update, Export all call live endpoints; verified in `wecms-web` 2026-06-02). Save/persist is **live** — `POST /api/v1/addon/operation-status` (`create`, [`OperationStatusController.java:100-103`](src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java#L100-L103)) persists with a `(BIZ_KEY,TAXCODE)` dup guard; the earlier "Save is a stub" note was **stale** (corrected 2026-06-23). |
| `domain/common/country` | `…/countries` (reference data) | **Backend implemented**. |
| `domain/payment/notification` | `…/notifications` (`GET /`, `GET /unread-count`, `PUT /{id}/read`, `PUT /read-all`) | **Implemented** — header-bell popup backed by legacy `BLTB_*` tables (5 entities); ports `comm_0192_007`; **BLTB branch only** (General `TYPE='G'` not ported). No `@menuAuthz` (global header surface; allow-listed). Producer leg `ApprovalNotificationService`. Frontend: header bell + slide-over. The earlier refactor-only `domain/notification/` + `NOTIFICATION` table (V238) was **removed 2026-06-10** (DEAD LEGACY). |
| `common/auditlog` | shared `AUDIT_LOGS_INFM` writer | **Backend implemented** (+ tests). Internal — no standalone endpoint. |
| `domain/auth` (`UserController`) | user lookup / image (auth-adjacent) | **Backend present** — endpoint scope not re-verified this pass (**Unknown**). |
| `domain/member`; dashboard; refresh tokens | — | **Not implemented.** No controller/service on disk. (Server-side **logout revocation** is now implemented — F-03, `TokenRevocationService` — and **5-failure auto-lock** is implemented — F-10, `LoginLockoutService`; both moved out of this row. Notifications **is** now implemented — see the `domain/payment/notification` row. Post-login menu / permission resolution is **Phase 1 implemented** — see the `domain/navigation` row.) |

> **Status legend (added 2026-06-02 — evidence-based reconciliation):** *Implemented* = backend **and** frontend both evidenced on disk. *Backend implemented* = controller + service (often + tests/migrations) present; the matching Next.js frontend was **not verified in this pass** — do not read as full-stack-complete. *Partial* = backend itself is incomplete/stub in parts. *Unknown* = code present but scope not re-verified. *Not implemented* = no controller/service exists. This table was rebuilt from the actual `domain/**/controller/` + `service/` + `src/test/` tree on 2026-06-02; the prior "Everything else — Not started" line was inaccurate (42 controllers exist). "Backend implemented" rows are conservative — confirm the frontend before declaring a feature done.
>
> **2026-06-23 addendum (frontend reality):** the "Frontend not verified this pass" caveat reflects the **2026-06-02** snapshot only. The Next.js app now ships **43 dashboard routes** — full Banking (Account Inquiry VND/FCY, Transaction History VND/FCY + `[accountId]`, Transfer Own Accounts, Transfer History, Smart Organizer, 4 Reports incl. `daily-financial-hs`), all 5 Payment modules (scheduled + result list + `[id]` detail) + Card Transactions + Exchange Rate Inquiry, and Add-on ×3 — plus the Settings panels under `<SettingsModal>`. For per-module frontend state consult the `wecms-web` Feature Status table + the feature docs; do not treat the 2026-06-02 caveats as current.
>
> **Frontend verification (2026-06-02):** `login-credentials`, `einvoice` (sales/expenses), and `operation-status` were checked against `wecms-web` and found **frontend-wired** (calling live endpoints, no mock fallback) — more complete than their feature docs claimed. Residual gaps only: e-invoice invoice-status server-side filter (IR-4), operation-status inquire-dialog Save persist endpoint, and the `resolveDocType EXPENSE→'E'` backend code bug. Details in the content-validation report under [docs/archive/reports/](docs/archive/reports/).

Deferred inside `auth` (still genuinely open): password-change-history reset, `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` post-auth gates, refresh tokens, and a durable/multi-instance revocation + rate-limit store. **No longer deferred — now implemented:** 5-failure auto-lock (F-10), JWT revocation / server-side logout (F-03), and post-login menu resolution (F-3).

## Auth Rules

### Login flow (`AuthService.login`)

Two-step multi-company flow. The `LoginRequest` carries an optional `biz_key`; when omitted, the backend may either auto-pick (single-link user) or return a chooser response (multi-link user).

1. Load `USER_INFM` by `USER_ID`. Missing → `WBE1009` + FAIL row in `USER_LOGIN_HIS`.
2. Flag gates, in order: `DEL_YN=Y` → `WBE1128`, `ACTV_YN=N` → `WBE1010`, `LOCK_YN=Y` → `WBE1011`.
3. `passwordEncoder.matches(rawPassword, storedHash)`. Mismatch → `WBE1009`.
4. **Verify-only — no write-back.** `USER_INFM.USER_PW` is never re-encoded or saved during login. The legacy `public.hash()` SHA-256 value stays intact so the live JEX system can authenticate the same credential (coexistence rule, 2026-06-10). The former transparent SHA-256 → BCrypt upgrade was removed.
5. Query **all** active links: `userBusiRepo.findAllByUserIdAndDelYn(userId, "N")`. Zero rows → `WBE1009` (user not provisioned at any company).
6. **Resolve the tenant.** Three branches:
   - **One link.** Auto-pick the row. If the request supplied a `biz_key` that doesn't match → `WBE1009`.
   - **Multiple links, no `biz_key` in request.** Build the chooser response (`LoginResponse.chooser(...)`): one `CompanyOption(biz_key, cmpn_nm, auth_gr_cd)` per active link, sorted by company name; per-option authority resolved from `BUSI_ADMN_INFM`. Return immediately — **no JWT issued, no `LST_LGN_DATE` write, no SUCCESS audit row**.
   - **Multiple links, `biz_key` in request.** Find the link whose `bizKey` matches. Unknown / non-matching → `WBE1009`.
7. Update `LST_LGN_DATE` on the resolved link.
8. Write a SUCCESS `USER_LOGIN_HIS` row.
9. Look up `BUSI_INFM` for the resolved company — single query, used for both the `CMPN_NM` display name and the verbatim `PRDT_TYPE` letter.
10. Resolve authority for the chosen company: `busiAdmnRepo.existsByBizKeyAndUserId(bizKey, userId)` → `"ADMIN"` if present, `"USER"` otherwise.
11. Issue JWT (`access_token`, `token_type`, `expires_in`) carrying claims `{ sub: userId, userNm, bizKey, langCd, authGrCd, prdtType? }` (the `prdtType` claim is omitted when the company row carries no value — never silently defaulted to `"F"`) and return `LoginResponse.authenticated(...)` with the standard tenant fields + `auth_gr_cd` + `prdt_type`.

The chooser response (multi-link user, no `biz_key` in request) stamps the per-company `prdt_type` into each `CompanyOption` so the frontend can display the product label next to each option. The top-level `prdt_type` is null on the chooser shape — product type only binds once a JWT is issued.

### Me flow (`AuthService.me`)

1. Read `TenantContext.current()` — populated by `JwtAuthenticationFilter` from the validated JWT.
2. Verify user still eligible (not deleted / inactive / locked in `USER_INFM`).
3. Verify the link to the **token-bound** tenant still exists (`USER_BUSI_CONN_INFM` where `DEL_YN='N'`).
4. Fetch `BUSI_INFM.CMPN_NM`.
5. Return `MeResponse(USER_ID, USER_NM, BIZ_KEY, LANG_CD, CMPN_NM, AUTH_GR_CD, PRDT_TYPE)` — `authGrCd` and `prdtType` echoed from the JWT claim (signed; not re-queried).

### JWT (`JwtTokenProvider`)
- Secret: `wecms.jwt.secret` (env var `WECMS_JWT_SECRET`). Minimum 32 bytes → HS256; ≥ 64 bytes → HS512 (preferred). Fail-fast on missing/too-short secret.
- Claims: `sub = USER_ID`, `userNm`, `bizKey`, `langCd`, `authGrCd` (`"ADMIN"` or `"USER"` — stamped at login finalize from `BUSI_ADMN_INFM`), `prdtType` (verbatim `BUSI_INFM.PRDT_TYPE` letter — omitted when the row carries no value). Lifetime from `wecms.jwt.expires-in-seconds` (default 3600 s).
- **Stateless transport, with server-side revocation (F-03):** no refresh token, but there **is** a revocation store — `TokenRevocationService` holds a `jti` denylist (logout) + a per-user not-before cutoff (admin lock/delete/demote/password-reset), consulted by `JwtAuthenticationFilter` on every request. The store is **in-memory** (lost on restart, not shared across instances — a durable/multi-instance store is the one open item here). Authority / product-type changes (rows modified in `BUSI_ADMN_INFM` or `BUSI_INFM.PRDT_TYPE`) otherwise take effect at the next login — the in-flight JWT's claims are signed and immutable.

### JWT filter (`JwtAuthenticationFilter`)
- Runs before `UsernamePasswordAuthenticationFilter`. Reads `Authorization: Bearer <token>`, parses, and:
  - Sets `UsernamePasswordAuthenticationToken` with `principal = userId` and `authorities = [ROLE_ADMIN | ROLE_USER]` derived from the `authGrCd` claim. These authorities are **enforced** — `@EnableMethodSecurity` is active and business endpoints are gated by `@PreAuthorize` / `@menuAuthz` (F-3), so the role is a live access-control input, not advisory.
  - Rejects **revoked** tokens (F-03): a logged-out `jti` on the denylist, or a token issued before the user's revocation cutoff, is treated as invalid → 401 (via `TokenRevocationService`).
  - Populates `TenantContext` with `(userId, userNm, bizKey, langCd, authGrCd, prdtType)` for the duration of the request. `prdtType` is the verbatim DB letter and may be null when the issuing tenant row had no value.
- Invalid token → clear context, let `JwtAuthenticationEntryPoint` return **401** with the standard `ApiResponse` envelope (`status.code = WBE1009`). The frontend's Axios response interceptor watches for 401 to surface the global session-expired re-auth dialog.
- **Never trust frontend-sent `biz_key` or `prdt_type` outside the login endpoint.** Domain services read the active company and product type exclusively from `TenantContext.currentBizKey()` / `TenantContext.currentPrdtType()` (sourced from the signed JWT). A request body or query parameter cannot override the tenant or product type a JWT is bound to. Frontend-side `productType` in `StoredUser` is for UI display + gating only — never a security boundary.

### Product-type rule

`BUSI_INFM.PRDT_TYPE` is a one-character legacy code that flags the product variant a tenant runs (WeCMS Default, WeCMS Lumens, HAENGSUNG Electronics, plus the 11 deferred WABOOKS variants — see "Project Overview"). The refactor's `common/enums/PrdtType` enum lists only the values that have active consumers in the refactor today: `F`, `M`, `H`. Other letters round-trip verbatim through the wire / JWT / `TenantContext` but `PrdtType.fromCode(...)` returns `Optional.empty()` for them so gates degrade safely.

**Rules for new code that branches on product type:**

- **Read from `TenantContext.currentPrdtType()`** — never from a request body, query parameter, header, or the frontend's `StoredUser.productType`. The signed JWT is the only authoritative source.
- **Use `PrdtType.fromCode(...)` to resolve the enum** — never compare to literal `"F"` / `"M"` / `"H"` strings. Typos and unknown letters fail closed instead of silently activating the wrong gate.
- **Null is a real value.** A tenant row with `PRDT_TYPE = NULL` ships null through the entire pipeline (no default substitution). Gates that need a value must either fall back explicitly or refuse the request with a documented error code.
- **Adding a constant requires a real consumer.** Don't pre-populate the enum with the 11 deferred WABOOKS letters speculatively; add them in the MR that ports their first behavioural gate.

**Deferred per-product behaviours (port with each feature MR, not infrastructure-level):**

| Product | Deferred behaviour | Lands with |
|---|---|---|
| `F` | e-Invoice feature gate | Add-on e-Invoice MR |
| `M` | Readonly-field overrides | The first form whose readonly rules differ for Lumens |
| `H` | Payment table-2 layout, account masking for bank `11704020`, hidden expense file column, daily-financial-HS report gating | Each ports with its module (Payment, Banking Transfer, Add-on Expense, Reports) |

### Security filter chain (`SecurityConfig`)
- CSRF disabled, CORS enabled via `CorsConfig` bean, session `STATELESS`.
- Public: `/api/v1/auth/login`, `/api/v1/auth/logout`, `/actuator/health`. **Everything else is authenticated.**

### Passwords (`WecmsPasswordEncoder` + `LegacyHashPasswordEncoder`)
- **Coexistence rule (2026-06-10): legacy-compatible SHA-256 on every write path; verify-only on login; no password write-back; no migration.** The Spring app runs alongside the live JEX system against the same DB, so both must authenticate the same `USER_INFM` row.
- Write → legacy JEX `public.hash()` format: `Base64(SHA-256(utf8(raw)))`, 44 chars (NOT BCrypt). This is the single write path for all three flows: Settings → Users create/reset, change-password, and any future write. `USER_PW` is `VARCHAR(100)` so it fits.
- Read → `matches()` verifies against the SHA-256 hash; BCrypt (`$2a$`/`$2b$`/`$2y$`) is **also** accepted purely as tolerance for any local/dev rows a prior build's (now-removed) upgrade may have written. Live DEV/UAT/PROD rows are 100% SHA-256.
- `upgradeEncoding()` always returns `false` — login never mutates `USER_INFM.USER_PW`. Writing BCrypt would lock the user out of legacy JEX (which computes SHA-256 and compares), so it is forbidden.

## i18n Rules

- Spring `MessageSource` (basename `messages`, UTF-8). Files: `messages.properties` (EN), `messages_en.properties`. Add `messages_ko.properties`, `messages_vi.properties`, `messages_km.properties` as real translations arrive.
- Keys are WBE/WBI codes (e.g. `WBE1009=ID or password does not match.`). Resolve via `MessageResolver.resolve(code, args…)`.
- Locale resolved at controller: `Accept-Language: ko` → `KO`, `vi` → `VI`, `km` → `KM`, default `EN`. Embedded in the JWT claim so downstream code reads it from `TenantContext` instead of re-parsing the header.
- Use the `LangCd` enum (`common/enums/LangCd.java`) for Accept-Language resolution — `LangCd.fromAcceptLanguage(header)` and `LangCd.fromCodeOrDefault(code)`.
- **Do NOT** reuse JEX ML code IDs verbatim — translate to `WBExxxx`/`WBIxxxx` and record in `messages*.properties`.

## API / DB Rules

### Response envelope — all endpoints

```json
// Success
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data":   { ... }
}

// Error
{
  "status":    { "error": true, "code": "WBE1009", "message": "ID or password does not match." },
  "timestamp": "2026-05-08T14:46:49Z",
  "path":      "/api/v1/auth/login",
  "trace_id":  "20260508144649PT06592776769567DEVL"
}
```

- **Wire convention: lowercase snake_case** — globally enforced by [`JacksonConfig`](src/main/java/com/kosign/wecms/config/JacksonConfig.java) which sets `PropertyNamingStrategies.SNAKE_CASE` on the auto-configured `ObjectMapper`. Java DTO fields stay camelCase (`expiresIn`, `accountNo`) and Jackson converts to snake (`expires_in`, `account_no`) on serialize / deserialize.
- Per-field `@JsonProperty` is reserved for the rare wire name that diverges from the Java field — e.g. `LoginResponse.accessToken` annotated `@JsonProperty("access_token")` (OAuth-style) rather than the strict snake `access_token`. Don't add `@JsonProperty` "for safety" when the strategy already produces the correct name.
- Success: `ApiResponse.ok(data)` → `error=false`, `code=WBI0000`. `@JsonInclude(NON_NULL)` drops `data` (and the `timestamp` / `path` / `trace_id` tracing trio) when null — successes stay a clean `{ status, data }`.
- Error: every error response carries `timestamp` (ISO-8601), `path` (the request URI), and `trace_id` alongside `status`. The trace id format is JEX-style — `yyyyMMddHHmmss + "PT" + 14 random digits + "DEVL"` — generated by [`TraceIdFilter`](src/main/java/com/kosign/wecms/common/exception/TraceIdFilter.java) at the head of the filter chain, placed in SLF4J `MDC` under key `traceId`, and echoed back in the `X-Trace-Id` response header. The same id appears in every log line for the request via the `%X{traceId}` MDC pattern in [`application.yml`](src/main/resources/application.yml). A user reading the id from the error dialog can quote it back to support; ops can grep it directly in server logs.
- **UPPER_SNAKE DTO holdovers fully retired 2026-06-07.** The Transaction History, Daily Cash, Daily Financial (CMC/HS/transactions), and Bank-Matching DTOs under `domain/banking/dto/` were migrated (UPPER `@JsonProperty` removed → lowercase snake_case via the global strategy). The last holdover — the `@Deprecated` legacy `TransferRow` / `TransferHistoryResponse` + `TransferHistoryService` + the `GET /api/v1/banking/transfers/history` endpoint — was **deleted** (superseded by the lowercase `/internal-transfer/transfer-history`). **No WeCMS DTO carries an UPPER `@JsonProperty` anymore**; the only `@JsonProperty` UPPER/PascalCase left in the tree is the external Coocon/NXiSAS gateway binding (`nxisas/gateway/`), which is a vendor contract, not the WeCMS wire. Full record: [docs/audits/field-casing-parity-audit-2026-06-06.md](docs/audits/field-casing-parity-audit-2026-06-06.md). Do not add new UPPER `@JsonProperty`.

### Exception → response mapping

[`GlobalExceptionHandler`](src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) is the single funnel. Every handler attaches `path` + `traceId` via `ApiResponse.error(code, message, path, traceId)`, logs with the request-scoped MDC id, and picks a category-appropriate HTTP status:

| Exception | HTTP | Code | Notes |
|---|---|---|---|
| `BusinessException` | **200** | `ex.getCode()` | Documented business outcome — frontend reads `status.error`, not the HTTP code. Logged at `warn`. |
| `MethodArgumentNotValidException` | 200 | `WBE_VALIDATION` | `@Valid @RequestBody` failure |
| `ConstraintViolationException` | 200 | `WBE_VALIDATION` | `@Validated` on path / param |
| `MissingServletRequestParameterException` | 200 | `WBE_VALIDATION` | required `@RequestParam` omitted |
| `MethodArgumentTypeMismatchException` | 200 | `WBE_VALIDATION` | param present but wrong type |
| `IllegalArgumentException` | 400 | `WBE_VALIDATION` | bad input that bypassed bean validation |
| `HttpMessageNotReadableException` | 400 | `WBE_BAD_REQUEST` | malformed JSON body |
| `HttpRequestMethodNotSupportedException` | 405 | `WBE_METHOD_NOT_ALLOWED` | wrong HTTP method |
| `HttpMediaTypeNotSupportedException` | 415 | `WBE_UNSUPPORTED_MEDIA_TYPE` | wrong / missing `Content-Type` |
| `NoResourceFoundException` | 404 | `WBE_NOT_FOUND` | no handler matched the URI |
| `AuthenticationException` | 401 | `WBE1009` | Spring Security pre-auth fail (separate from JWT 401 path which lives in `JwtAuthenticationEntryPoint`) |
| `AccessDeniedException` | 403 | `WBE1003` | authenticated but unauthorised |
| `DataAccessException` | 500 | `WBE_DB` | persistence layer fault — logged at `error` with stack |
| `TimeoutException` | 504 | `WBE_TIMEOUT` | async / external timeout |
| `Exception` (catch-all) | 500 | `WBE9999` | NPE and any other unmapped runtime — logged at `error` with stack |

JWT 401 stays handled by [`JwtAuthenticationEntryPoint`](src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java) (the entry point fires before the controller chain, so `@RestControllerAdvice` doesn't see it). It uses the same envelope shape and reads the request-scoped `traceId` from MDC so the JSON body and the log line correlate.

### When you add a new error code
1. Add the WBE/WBI key + English message to `messages.properties` (and `messages_en.properties`; future locales follow).
2. Throw `new BusinessException("WBExxxx")` — never a raw `String` message. Optional `Object[] args` interpolate via `MessageResolver`.
3. If the failure is a brand-new category (auth / DB / external system / etc.) and `BusinessException` doesn't fit, add the handler in `GlobalExceptionHandler` and the exception → code row to the table above. Don't introduce a parallel error envelope.

### Error codes in use

| Code | Meaning |
|---|---|
| WBI0000 | Success |
| WBE1009 | ID or password does not match (also: user not in configured tenant) |
| WBE1010 | Account not activated |
| WBE1011 | Account locked |
| WBE1128 | Account deleted |
| WBE2010 | Excel export not configured for screen (unknown `screenId` in `ExcelExportRequest`) |
| WBE2011 | Excel export exceeds the 50,000-row cap |
| WBE2012 | Excel export failed (unexpected runtime error before bytes flushed) |
| WBE2013 | Excel export request has no exportable columns after intersection with View Setting / allow-list |
| WBE2014 | ERP Transaction History row not found in tenant (single envelope merges "not found" + "wrong tenant") |
| WBE2015 | ERP Transaction History file is unavailable on the configured storage root (or `FILE_SIZE = 0` batch row) |
| WBE9001 | Endpoint is not yet implemented (current consumer: ERP Transaction History thumbnail stub) |
| WBE_APPROVAL_LINE_NOT_FOUND | Approval Line row not found in tenant (single envelope merges "not found" + "wrong tenant") |
| WBE_APPROVAL_LINE_TOO_MANY_APPROVERS | Approval line cannot have more than 5 approvers (legacy `WBW1164` parity) |
| WBE_APPROVAL_LINE_DUP_APPROVER | Same `apprvr_id` repeated in the `approvers` request array |
| WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT | `apprvr_id` has no active `USER_BUSI_CONN_INFM` link for the current tenant |
| WBE_APPROVAL_LINE_EMPTY_APPROVERS | `approvers` request array is empty / missing |
| WBE_APPROVAL_LINE_NAME_BLANK | `appr_line_nm` missing / whitespace-only |
| WBE_CHART_OF_ACCOUNTS_NOT_FOUND | Chart of accounts row not found in tenant (single envelope merges "not found" + "wrong tenant") |
| WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE | Duplicate active `CHART_ACCT_CD` in the tenant (Create dup-check or Update rename collision) |
| WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND | `parent_chart_acct_cd` references a row that doesn't exist (or is soft-deleted) in the tenant |
| WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE | Tried to edit / delete a restricted root (`ACC-01` / `ACC-02` / `ACC-03`), or rename a row TO a restricted code |
| WBE_CHART_OF_ACCOUNTS_CYCLE | Parent change on Update would create a cycle in the hierarchy (refactor addition — legacy DEV data has self-references, refactor blocks at write) |
| WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED | Create / Update would push a leaf beyond the 4-level depth cap (refactor addition) |
| WBE_CHART_OF_ACCOUNTS_SELF_PARENT | `parent_chart_acct_cd` equals the row's own `chart_acct_cd` (refactor addition) |
| WBE_VALIDATION | Bean-validation failure |
| WBE9999 | Unexpected server error |

Add new codes to `messages.properties` first, then throw `new BusinessException("WBExxxx")`. Never return a raw string message without a code.

### Database (LOCAL)

- **Canonical local target: the shared dev DB `172.168.5.172:5432/wecmsdevdb`, user `wecmsdev_owner`** (VPN-required), configured in `application-local.yml`. Password via env `WECMS_DB_PASSWORD` or the R&D default in that file (gitignored — **not** in a fresh clone; obtain it per [docs/INFRASTRUCTURE_ACCESS.md](../docs/INFRASTRUCTURE_ACCESS.md)). A developer-local `localhost:5434/wecms_dev` (user `postgres`) is a **documented fallback only**.
- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto = validate` — never `update` or `create`. Schema changes always land as a new `V{N}__{description}.sql` in `src/main/resources/db/migration/`.
- Current migrations:

  | Version | Summary |
  |---|---|
  | V1 | Auth schema: BUSI_INFM, USER_INFM, USER_BUSI_CONN_INFM, USER_LOGIN_HIS, indexes, constraints |
  | V2 | Seed: tenant VN_VNM0004_000001 + testuser (active) + lockeduser (LOCK_YN=Y), password = `__BCRYPT_PENDING__` sentinel |
  | V3 | Normalize YN columns CHAR(1) → VARCHAR(1) |
  | V5 | JEX DEV snapshot (2026-04-22): 218 BUSI_INFM + 318 USER_INFM + 382 USER_BUSI_CONN_INFM rows, all USER_PW = sentinel |
  | V6 | Scope data to single tenant |
  | V7 | Align schema to legacy JEX exact structure |
  | V8 | Add BUSI_INFM.DEL_YN |
  | V9 | Normalize BUSI_INFM char columns |
  | V10 | Add legacyuser with SHA-256 base64 hash for testing the upgrade path |
  | V11 | Phase 1 bootstrap: EMPL_INFM + BUSI_ADMN_INFM tables, `sq_biz_key` sequence, `wb_get_next_val` legacy PL/pgSQL function (BIZ_KEY + EMPL_NO branches only). Idempotent. |
  | V12 | Re-align EMPL_INFM date columns to legacy VARCHAR(8) shape. |
  | V13 | Sprint 1 banking schema: ACCT_MSTR, ACCT_TRNS_HSTR, ACCT_TRNS_RCV_PAY_INFM, BUSA_DOC_RCV_PAY_HIS, ACCT_TRNS_RCV_PAY_EVID_FILE, NAPAS_REMIT_TRNS_HSTR. |
  | V14 | Sprint 1 reports schema: BUSI_COND_DAILY_SUMR(_DETL), SELL_HIS_DAILY_SUMR(_DETL), BAT_CALL_HIS. |
  | V15 | Sprint 1 e-invoice schema: BUSA_DOC_MSTR (Sprint 1 column subset only — ~16 of 100 legacy columns), EINV_FEE_DETL, EINV_TAX_DETL. |
  | V16 | Sprint 1 operation status schema: OPRT_STTS_INFO, EXT_TRAN_CALL_HIS. |
  | V17 | Sprint 1 seed data — pinned to `BIZ_KEY = VN_VNM0004_000001`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. Also widens `EINV_INVOICESYMBOL` to VARCHAR(50) for static-UI compat. |
  | V18 | Normalise CHAR → VARCHAR on Sprint 1 banking/report/e-invoice columns so Hibernate `validate` accepts them (mirrors V3 / V9 pattern). |
  | V19 | Follow-up to V18 — convert `ACCT_TRNS_RCV_PAY_INFM.CONF_YN` CHAR(1) → VARCHAR(1) (missed in V18). |
  | V20 | Sprint 1 audit: add `BANK_INFM` table (Vietnamese bank registry, 29 rows seeded from real DEV) so bank-name lookup goes through the DB instead of a static map. |
  | V21 | Sprint 1 audit: align `ACCT_TRNS_RCV_PAY_INFM` to legacy column names — rename `TRCO_*`/`USAG_CD`/`AMT`/`CONF_*` → `RCV_PAY_TRCO_*`/`RCV_PAY_USAG_CD`/`RCV_PAY_AMT`/`CNFM_*`; add legacy locator columns BANK_CD/ACCT_NO/TRSC_DT. |
  | V22 | Sprint 1 audit: replace V17 fake demo seed with real wecmsvndev rows (~25 latest per table) for tenant `VN_VNM0004_000001`. Tables empty in DEV keep V17 stub: `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `ACCT_TRNS_RCV_PAY_EVID_FILE`. |
  | V23 | Sprint 1 schema-parity corrective migration: add 144 missing legacy columns across 6 tables (`ACCT_MSTR` +30, `ACCT_TRNS_HSTR` +10, `ACCT_TRNS_RCV_PAY_INFM` +17, `BUSA_DOC_RCV_PAY_HIS` +10, `ACCT_TRNS_RCV_PAY_EVID_FILE` +7, **`BUSA_DOC_MSTR` +80**). Purely additive — `ALTER TABLE ADD COLUMN IF NOT EXISTS`. Brings local schema column-for-column equivalent to wecmsvndev. |
  | V24 | Sprint 1 schema-parity corrective migration round 2 (constraints): align BUSI_INFM.BIZ_KEY VARCHAR(32)→(20); realign 5 PRIMARY KEYs to legacy shape (`bank_infm`, `bat_call_his`, `acct_trns_rcv_pay_evid_file`, `sell_his_daily_sumr(_detl)`); add `busa_doc_mstr_uk01` UNIQUE; add 11 legacy CHECK constraints; add 5 legacy FOREIGN KEYs (the rest deferred — depend on `cash_repy_mstr`/`mrec_pic_store` not yet migrated, or audit-log triggers). |
  | V25 | ~~Full port of DEV `wb_get_next_val` (all 164 WHEN branches)~~ — **superseded by V26/V27**. Applied and immediately corrected; do not reference as canonical state. |
  | V26 | Corrective (intermediate): trims `wb_get_next_val` to 2 branches; drops 11 unnecessary sequences V25 created. **Superseded by V27** which adds Sprint 1 migrated-table branches. |
  | V27 | Final corrective for `wb_get_next_val`: Sprint 0 branches (2) + Sprint 1 migrated-table branches that have a DEV entry (11). Total 13 branches. Sprint 1 tables with no DEV branch documented inline. |
  | V28 | Sprint 1 final parity audit (2026-04-30) — schema fixes: add `empl_infm._empl_nm`, add `user_login_his.reg_uid` (and rebuild PK to (user_id, reg_dtm, reg_uid)); widen NUMERIC to `(20,6)` for amounts and `(N)` for seq columns across 12 tables; widen VARCHAR(50)→(100) for `user_id`/`reg_id`/`mod_id` across auth tables and `rcv_pay_trco_nm` 200→250; add `acct_trns_hstr_uk01`, `busi_infm_uk01` UNIQUE; add `busi_infm_chk01` CHECK; realign `busi_cond_daily_sumr_detl` PK to DEV column order. All additive/safe — see [docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md](../docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md). |
  | V29 | Sprint 1 real-DEV seed round 2: 226 real rows imported into 5 previously-empty tables — `bat_call_his` (84), `busa_doc_rcv_pay_his` (26), `einv_fee_detl` (16), `einv_tax_detl` (50 of 669 — sampled most recent), `ext_tran_call_his` (50 of 24K — sampled). Idempotent via `INSERT … ON CONFLICT DO NOTHING`. |
  | V30 | Sprint 1 stabilization: `SYS_SCR_OPTION_WECMS` + `USER_SCR_OPTION_WECMS` (View Setting). Seeds 6 default columns for `fnsh_0001_001` (BANK · ACCOUNT_NICKNAME · BALANCE · AVAILABLE_BALANCE · VAS_ACCOUNT (hidden) · LAST_UPDATE). Idempotent via `CREATE TABLE IF NOT EXISTS` + `INSERT … ON CONFLICT DO NOTHING`. |
  | V31 | PROD Active Table Scope cleanup (2026-05-01) — drops 6 local tables that are NOT in the PROD active list defined below: `busi_cond_daily_sumr(_detl)`, `sell_his_daily_sumr(_detl)`, `bat_call_his`, `acct_trns_rcv_pay_evid_file`. These were carried over from JEX DEV but PROD does not populate them. Java side: deleted `BusiCondDailySumr` entity / id / repository; `ReportService.dailyCash()` per-currency summary now stubs (account ledger still reads `ACCT_MSTR`). Sprint 2 will wire the real PROD report sources (`DAILY_CASH_REPT_DAILY_SUMR_DETL`, `DAILY_FINCL_REPT_DAILY_SUMR_DETL`). |
  | V32 | View Setting semantic screen-id cutover (2026-05-01) — wipes the V30 `fnsh_0001_001` rows from both `SYS_SCR_OPTION_WECMS` and `USER_SCR_OPTION_WECMS`, reseeds Banking > Account Inquiry (VND) under semantic `WEB_SVC_ID = 'banking-account-inquiry-vnd'` with `DYM_AREA_ID = 'default'` on every row, then `ALTER COLUMN DYM_AREA_ID SET NOT NULL` on both tables (after backfilling any nulls). Idempotent. Java side (same MR): added `dymAreaId` mapping to `SysScrOptionWecms`/`UserScrOptionWecms` entities; updated repos/service/controller for the new path shape (`{webSvcId}/{dymAreaId}` plus `{webSvcId}/areas`); added `@Pattern` validation guards; added `ConstraintViolationException` handler in `GlobalExceptionHandler`. R&D scope — no preservation of local user-saved overrides. See [docs/jex/features/view-setting.md](../docs/jex/features/view-setting.md). |
  | V33 | View Setting special FIELD_ID seed (2026-05-02) — adds `CHECKBOX` (DISP_ORD=0, FIX_YN='Y') under `banking-account-inquiry-vnd` / `default` to anchor the row-selection column convention. Special FIELD_IDs (`CHECKBOX`, `ETC`) are admin-controlled — frontend popover hides them, backend `ScreenOptionService.SPECIAL_FIELD_IDS` locks `USE_YN='Y'` + sys-pinned `DISP_ORD` on save. ETC NOT seeded — Account Inquiry has no action column. |
  | V34 | View Setting schema parity + SIZE seed (2026-05-02) — aligns `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` to DEV: `SIZE` INTEGER → VARCHAR(10), `DISP_YN` SET NOT NULL, ADDs `SORT_YN CHAR(1) NOT NULL DEFAULT 'Y'` + `HTML_CONT TEXT` on sys (DEV doesn't have these on user_scr — local mirrors). Backfills SIZE for `banking-account-inquiry-vnd` using **Figma WB365 widths** (the layout authority — disagrees with legacy DEV in 5 of 7 columns). Re-asserts two row-value drifts that accumulated post-V32 (`BANK.fix_yn='Y'`, `VAS_ACCOUNT.use_yn='N'/disp_yn='Y'`). Clears USER table for the scope so lazy-init re-copies. Documented refactor-only deviations (kept): `field_nm` width 200/nullable, `disp_ord` INTEGER, `fix_yn` column, `reg_dtm`/`mod_dtm` TIMESTAMPTZ. SIZE is **schema-aligned + value-seeded but still not consumed end-to-end** — Java entity / DTO / TS type unchanged. **Superseded for the data layer by V35** which reverses V32's semantic-id decision and reseeds with legacy DEV ids. |
  | V35 | View Setting schema-only DEV parity (2026-05-02) — tightens column types/nullability without touching row contents. `field_nm` VARCHAR(200) NULL → VARCHAR(100) NOT NULL on both tables; `disp_ord` INTEGER NOT NULL DEFAULT 0 → NUMERIC(10,0) NOT NULL on both tables (DROP DEFAULT first); `size` SET NOT NULL on sys (V34 already populated every row using Figma WB365 widths). Existing `web_svc_id = 'banking-account-inquiry-vnd'` rows from V32–V34 stay in place — V35 does NOT seed legacy `fnsh_0001_001` (refactor screenId convention is semantic route-based per `wecms-web/lib/viewSettingScreens.ts`, NOT legacy JEX ids). Refactor-only deviations kept: `fix_yn` column (column-locking, see [docs/jex/features/view-setting.md §1.8](../docs/jex/features/view-setting.md)) and `BaseEntity` audit columns (`reg_dtm`/`mod_dtm` TIMESTAMPTZ vs DEV's VARCHAR(14)) — both intentionally non-DEV. `user_scr_option_wecms.size` stays NULLABLE (deferred — `ScreenOptionService.initUserRowsFromSys` now copies sys.size into user.size, but legacy local user rows have NULL; flip to NOT NULL in a follow-up after one round of dev/QA confirms coverage). Java side same MR: `SysScrOptionWecms` adds `size`/`sortYn`/`htmlCont`/`dispYn`; `ScreenOptionItem` DTO exposes SIZE/SORT_YN/HTML_CONT/DISP_YN; `initUserRowsFromSys` copies `size`/`dispYn`. **`disp_ord` NUMERIC(10,0) immediately reverted by V36** (see below) — Hibernate's schema validator rejected NUMERIC against the Java `Long`/`Integer` mapping. Idempotent. |
  | V36 | View Setting `disp_ord` revert to INTEGER (2026-05-02) — hotfix for V35: Hibernate `validate` failed because Java `Long` maps to BIGINT but V35 set the column to NUMERIC. Reverts `disp_ord` NUMERIC(10,0) → INTEGER NN DEFAULT 0 on both tables and restores the V34 stance ("matches the JPA `Integer dispOrd` field type"). Entity reverted to `Integer dispOrd` in the same MR. Permanent refactor-only deviation from DEV's NUMERIC(10,0) — documented in entity Javadoc + V36 header. Idempotent (`ALTER COLUMN TYPE` is a no-op when already INTEGER). |
  | V37 | View Setting `sort_yn` CHAR(1)→VARCHAR(1) on sys (2026-05-02) — interim hotfix for the second Hibernate validate failure post-V35 (`found bpchar, expecting char(1) (Types#VARCHAR)` for the new `String sortYn` field). **Wrong direction — superseded by V38** which reverses the change in favour of DEV parity (DEV is `bpchar(1)`). V37 is left in flyway_schema_history as-applied; V38 reverses the column type and the entity gets `@JdbcTypeCode(SqlTypes.CHAR)` so DB-side parity is preserved. |
  | V38 | View Setting `sort_yn` revert to CHAR(1) on sys (2026-05-02) — reverses V37, restoring DEV-parity `bpchar(1) NOT NULL DEFAULT 'Y'` on `sys_scr_option_wecms.sort_yn`. Java side same MR: `SysScrOptionWecms.sortYn` annotated `@JdbcTypeCode(SqlTypes.CHAR)` so Hibernate maps `String` → CHAR JDBC type and `validate` accepts the bpchar column. **Direction locked**: when DEV uses `char(1)`, prefer the entity-side annotation over a CHAR→VARCHAR migration — keeps local DB strictly DEV-parity. Idempotent. |
  | V39 | Add lowercase `size` column to screen-option tables (2026-05-02) — V30/V34 created `"SIZE"` as a quoted uppercase identifier; Hibernate 6 generates lowercase unquoted `size` in SQL, causing `ERROR: column ssow1_0.size does not exist`. Intermediate fix: added a new `size varchar(20)` column (backfilled to `'auto'`), leaving `"SIZE"` intact. **Superseded by V40** which corrects the approach. |
  | V40 | Fix SIZE column — drop V39 duplicate, rename legacy `"SIZE"` → `size` (2026-05-02) — V39's `size` column had wrong `'auto'` values; the real Figma pixel values (`38px`, `240px`, `280px`, …) lived in the legacy `"SIZE"` column. V40: drops the V39 `size` column, renames `"SIZE"` to `size`, widens both tables from varchar(10) → varchar(20). Sys: NOT NULL re-asserted (0 nulls confirmed). User: stays NULLABLE (lazy-init'd by `initUserRowsFromSys`). Entity `length` annotation updated to 20 on both `SysScrOptionWecms` and `UserScrOptionWecms`. |
  | V41 | View Setting — turn off `SORT_YN` for `VAS_ACCOUNT` on `banking-account-inquiry-vnd` (2026-05-02) — flips `SYS_SCR_OPTION_WECMS.SORT_YN='N'` and clears `USER_SCR_OPTION_WECMS` for the screen so the lazy-init step re-copies the corrected sys default. Aligns the seed with the backend `AccountSortField` whitelist (no `VAS_ACCOUNT` constant — column has no backing entity field yet) per the new "Common table sort" rule that `SORT_YN='Y'` columns MUST have a whitelist entry. Idempotent. When `BUSI_VAS_ACCT_INFM` lands in a later sprint, the same migration that adds the enum constant flips this back to `'Y'`. |
  | V42 | View Setting — set `DISP_YN='N'` for `VAS_ACCOUNT` on `banking-account-inquiry-vnd` (2026-05-02) — hides `VAS_ACCOUNT` from the View Setting popup. `DISP_YN='N'` mirrors the legacy `IDO.USER_SCR_OPTION_WECMS_R002.xml` filter (`WHERE ... AND USR.DISP_YN = 'Y'`): the popup query returns only rows where `DISP_YN='Y'`. Frontend `ViewSettingPopover.tsx` enforces the same rule client-side (added in the same MR). `VAS_ACCOUNT` already has `USE_YN='N'` (hidden from table by default, V34); `DISP_YN='N'` prevents users from "un-hiding" a column that still renders `"—"`. Clears `USER_SCR_OPTION_WECMS` for the scope so lazy-init re-copies the corrected flag. When `BUSI_VAS_ACCT_INFM` is ported, flip both `DISP_YN` and `USE_YN` back to `'Y'` in the same migration. Idempotent. |
  | V43–V70 | Settings module groundwork (audit logs, firm-banking sources/view/seed, environment settings, etc.). See individual migration files in `src/main/resources/db/migration/` — not transcribed into this table on a per-row basis. |
  | V71 | ERP Transaction History schema (2026-05-11) — `CREATE TABLE IF NOT EXISTS ERP_TRNS_HSTR` column-for-column with JEX DEV (`biz_key VARCHAR(20)`, `seq_no NUMERIC(10,0)`, `org_file_nm` / `save_file_nm` VARCHAR(500), `file_size NUMERIC(10,0)`, `erp_type CHAR(1)`, `trns_type_cd CHAR(2)`, `reg_id VARCHAR(100)`, `reg_dtm VARCHAR(14)`, PK `(BIZ_KEY, SEQ_NO)`). Adds two helper performance indexes beyond DEV (`IX_*_BIZ_REG_DTM`, `IX_*_BIZ_ERP_TYPE`) — extra indexes allowed by the Legacy Schema Parity Rule. Idempotent. First consumer: `domain/setting/erptransactionhistory`. |
  | V72 | ERP Transaction History demo seed (2026-05-11) — 30 rows for tenant `VN_VNM0004_000001` from real JEX DEV (top 10 per direction by `REG_DTM DESC, SEQ_NO DESC`). All seeded rows have `FILE_SIZE > 0`; physical files are NOT seeded under `wecms.erp-transaction-history.storage-root` — the download endpoint surfaces `WBE2015` for any row whose file is missing locally, mirroring production behaviour. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. |
  | V73 | Multi-tenant seed (2026-05-11) — adds second R&D tenant `VN_VNM0004_000014` (HAENGSUNG ELECTRONICS, `PRDT_TYPE = 'H'`) from real JEX DEV, plus 6 DEV-imported HAENGSUNG users, the `BUSI_ADMN_INFM` row for `wecmshaengsung@gmail.com`, and three `WHERE EXISTS`-guarded multi-company links (`rndadmin` / `testuser` light up on bootstrap-only DBs — no-op on DEV-imported DBs; `lamine@gmail.com` lights up on DEV-imported DBs so the chooser is exercisable there). All inserts idempotent via `ON CONFLICT DO NOTHING` + the `WHERE EXISTS` guard prevents FK violations against `USER_INFM` for any user that isn't present on the local DB. |
  | V74 | Backfill `BUSI_INFM.PRDT_TYPE` for `VN_VNM0004_000001` (2026-05-11) — V2's INSERT omitted the column so the row was `NULL` locally; DEV stores `'F'`. Idempotent — WHERE clause excludes rows that already carry a non-NULL value. After V74 the two local R&D tenants align with the refactor's recognised set: 000001 → `'F'`, 000014 → `'H'`. |
  | V75–V83 | (Various stabilisation migrations: ERP transaction history numeric type fix, multi-company link cleanup, VAS Account schema + seed + View Setting, Purpose Settings schema + view + `wb_get_next_val` branch, JEX DEV init-data alignment. See per-file headers under `src/main/resources/db/migration/`.) |
  | V112 *(drafted as V84; renumbered on commit — the file's internal `-- V84` header comment is stale)* | Settings > Contacts schema (2026-05-13) — `TRCO_INFM` (47 cols, PK on `TRCO_KEY` alone — global sequence), `TRCO_ADDR_INFM` (11), `TRCO_ACCT_INFO` (20), `TRCO_OVRS_INFM` (13), `PGRO_BSAC_LNKN_INFM` (6). All five tables in the PROD Active Table List. Column-for-column parity with DEV verified via `postgres_dev_server_jexframe` MCP; PKs / UK (`trco_infm_uk01` on `(BIZ_KEY, TRCO_NICK_NM)`, `pgro_bsac_lnkn_infm_uk01` on `(BIZ_KEY, GRP_CD, TRCO_KEY)`) / CHECK (`trco_infm_chk01` on `TRSC_CCY_CD IN ('VND','USD')`) / FK (`trco_acct_info_fk01`, `pgro_bsac_lnkn_infm_fk01/fk02`) carried over verbatim. Helper indexes added beyond DEV: `ix_trco_infm_biz_handkey`, `ix_trco_infm_biz_taxid`, `ix_trco_infm_biz_moddtm`, `ix_trco_addr_infm_biz_trco`, `ix_trco_acct_info_biz_trco`, `ix_trco_ovrs_infm_biz_trco`. Idempotent. |
  | V113 *(drafted as V85; file header stale)* | Settings > Contacts: `wb_get_next_val` extension (2026-05-13, OQ-4) — adds the `trco_infm.trco_key` branch (`SELECT nextval('sq_trco_key')`) ported VERBATIM from DEV; creates the local `sq_trco_key` sequence starting at 1,000,000 (well above the V115 seed ceiling). Branch count: 13 → **14**. Documented in "Surviving legacy functions" below with the retirement plan. Consumed by `LegacyFunctionRepository.nextTrcoKey` → `ContactsService.create`. |
  | V114 *(drafted as V86; file header stale)* | Settings > Contacts: port `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang_cd)` (2026-05-13, OQ-7) — function body copied VERBATIM from DEV. Reads from `tb_firm_bank_code` (V59) — no new schema dependency. Returns the EN abbreviation for `lang_cd='en'`, the long name for `lang_cd in ('vi','vn')`; returns NULL for `'ko'` / `'km'` (no branch on DEV). Documented in "Surviving legacy functions" below. Consumed by `LegacyFunctionRepository.resolveNapasOrCitadBankName` → `ContactsService.list/getOne` (request-scoped batching, one funnel call per unique `(code, lang_cd)` pair). `BankCdResolver` NOT used in this flow per OQ-7. |
  | V115 *(drafted as V87; file header stale)* | Settings > Contacts demo seed (2026-05-13) — 30 master rows + 30 addresses + 28 bank accounts + 9 overseas beneficiaries + 30 PGRO group-links (`USER_ID='ALL'`, `GRP_CD ∈ {B,S}`) pulled from real JEX DEV for tenant `VN_VNM0004_000001`. TRCO_KEY values preserved verbatim from DEV (9212..9322) to keep FK chains consistent; newly-created contacts get values from `sq_trco_key` starting at 1,000,000 — no collision risk. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. |
  | V154 *(drafted as V88; re-applied as V187 after a Flyway collision — both file headers are stale)* | Settings > Contacts View Setting seed (2026-05-13) — registers `web_svc_id='contacts'`/`dym_area_id='default'` with 8 columns: CHECKBOX · HAND_TRCO_KEY · TRCO_CMNM · TRCO_TYPE · TRCO_TAX_ID · BANK_NM · TRCO_BSUN_POST_ADRS · ETC. `BANK_NM` and `TRCO_BSUN_POST_ADRS` ship `SORT_YN='N'` because they project from sub-tables (no `ContactSortField` constants for them); the View Setting + whitelist agreement rule is preserved. Idempotent. |
  | V117 *(drafted as V89; file header stale)* | Settings > Contacts hotfix (2026-05-13) — converts `TRCO_ADDR_INFM.DISP_ORD` and `TRCO_ACCT_INFO.DISP_ORD` from `NUMERIC(10,0)` to `INTEGER` (Hibernate `validate` vs JPA `Integer` mapping; same precedent as V35 → V36). `USING DISP_ORD::integer`. Idempotent. |
  | V139 / V140 *(drafted as V98 / V99; file headers stale)* | Settings > Contacts attachments — `TRCO_INFO_FILE` schema (V139) + the `trco_info_file.file_id` `wb_get_next_val` branch (V140). PROD active-list scope widened 2026-05-13 (PO call). Consumed by `ContactsFileService` + `LegacyFunctionRepository.nextTrcoInfoFileFileId`. |  | V90 | Settings > Approval Line schema (2026-05-13) — `APPR_LINE_INFM` (7 cols, PK on `(BIZ_KEY, APPR_LINE_NO)`) + `APPR_LINE_INFM_DETL` (11 cols, PK on `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)`). Both tables in the PROD Active Table List. Column-for-column parity with DEV verified via `postgres_dev_server_jexframe` MCP. `APPRVR_DISP_ORD NUMERIC(1,0)` pins the 5-approver cap at the DB level (legacy `WBW1164` parity). Helper index added beyond DEV: `ix_appr_line_infm_biz_reg_dtm` (`BIZ_KEY, REG_DTM DESC, APPR_LINE_NO DESC`) materialises the default list sort. No FK constraints (DEV doesn't have them either; cascading delete is service-layer via `@Modifying` DELETE-by-FK on the detail repo). Idempotent. |
  | V91 | Settings > Approval Line: `wb_get_next_val` extension (2026-05-13, OQ-7) — adds the `appr_line_infm.appr_line_no` branch ported VERBATIM from DEV (`SELECT COALESCE(MAX(appr_line_no::numeric), 0) + 1 FROM appr_line_infm WHERE biz_key = $3`). Per-tenant MAX+1. Branch count: 14 → **15**. Documented in "Surviving legacy functions" below with the retirement plan. Consumed by `LegacyFunctionRepository.nextApprLineNo` → `ApprovalLineService.create`. |
  | V92 | Settings > Approval Line hotfix (2026-05-13) — converts `APPR_LINE_INFM.APPR_LINE_NO` + `APPR_LINE_INFM_DETL.APPR_LINE_NO` from `NUMERIC(15,0)` to `BIGINT`, and `APPR_LINE_INFM_DETL.APPRVR_DISP_ORD` from `NUMERIC(1,0)` to `INTEGER`. Same precedent as V35 → V36 (`SysScrOptionWecms.dispOrd`) and V89 (Contacts `disp_ord`) — Hibernate 6's `ddl-auto: validate` rejects NUMERIC against the Java `Long` / `Integer` mappings. `USING ...::bigint|integer` makes the lossless cast explicit. Safe — DEV's 15-digit cap fits inside BIGINT; the `NUMERIC(1,0)` defence-in-depth cap on `APPRVR_DISP_ORD` is replaced by the service-layer guard (`ApprovalLineConstants.MAX_APPROVERS=5` + bean validation + `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`). Idempotent — DO blocks check `data_type` before altering. |
  | V94 | Settings > Chart of Accounts schema (2026-05-13) — `CREATE TABLE IF NOT EXISTS CHART_ACCT_INFM` column-for-column with JEX DEV (15 cols, PK `(BIZ_KEY, CHART_ACCT_SEQ_NO)`, UK `uk_chart_acct_cd` on `(BIZ_KEY, CHART_ACCT_CD, DEL_YN)` — the 3-column UK is what allows the revive-on-create / archive-rename-on-delete behaviour). Column-for-column parity with DEV verified via `postgres_dev_server_jexframe` MCP. `CHART_ACCT_SEQ_NO` stays `VARCHAR(14)` (DEV parity); entity maps as `String` end-to-end — no AttributeConverter needed. `STTS` / `DEL_YN` / `CAL_RULE` are `CHAR(1)` on DEV; entity uses `@JdbcTypeCode(SqlTypes.CHAR)` so `validate` accepts the bpchar columns (same precedent as V38 `sort_yn`). Helper indexes added beyond DEV: `ix_chart_acct_infm_biz_parent` (partial `WHERE del_yn='N'`) powers child re-parent + has_children group-count; `ix_chart_acct_infm_biz_moddtm` supports optional MOD_DTM sort. No CHECK / FK on DEV → none locally (parity). Idempotent. First consumer: `domain/setting/chartofaccounts`. |
  | V95 | Settings > Chart of Accounts: `wb_get_next_val` extension (2026-05-13) — adds the `chart_acct_infm.chart_acct_seq_no` branch ported VERBATIM from DEV (`SELECT COALESCE(MAX(chart_acct_seq_no::numeric), 0) + 1 FROM chart_acct_infm WHERE biz_key = $3`). Per-tenant MAX+1, same shape as V91 `appr_line_no`. Branch count: 15 → **16**. Documented in "Surviving legacy functions" below with the retirement plan. Consumed by `LegacyFunctionRepository.nextChartAcctSeqNo` → `ChartOfAccountsService.create`. |
  | V96 | Settings > Chart of Accounts: restricted-roots seed (2026-05-13) — seeds `ACC-01` (REVENUE / 수입), `ACC-02` (EXPENSES / 지출), `ACC-03` (INVESTMENT / 투자) for every active tenant in `BUSI_INFM`. These three codes are blocked from edit / delete by `ChartOfAccountsService` via `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` (legacy mirror: `WBE1178` in `comm_0223_001_u001` / `d001`). Resolves the planning OQ on root-row provisioning — Flyway-time seed, not on-demand auto-creation. `chart_acct_seq_no` allocated via the V95 branch so values stay tenant-disjoint. `reg_id` / `mod_id` = `biz_key` (legacy convention — DEV rows for tenant 000001 seq 70/74/80 carry the same pattern). Idempotent via `ON CONFLICT (BIZ_KEY, CHART_ACCT_CD, DEL_YN) DO NOTHING`. |
  | V97 | Settings > Chart of Accounts: demo seed for `VN_VNM0004_000001` (2026-05-13) — 9 real rows pulled from JEX DEV, preserving DEV `CHART_ACCT_SEQ_NO` values (90..112). Excludes the V96-seeded restricted roots. Two grandfathered shapes preserved verbatim: seq 104 carries a self-reference (`CHART_ACCT_CD='2001001'`, `PARENT_CHART_ACCT_CD='2001001'`), seq 112 carries an orphan parent (`'Code009'` missing from snapshot). `ChartOfAccountsTreeBuilder` surfaces both with `chart_acct_level = null`; the cycle / self-parent / depth guards in `ChartOfAccountsValidator` apply only to new writes (legacy data grandfathered). Idempotent. |
  | V153 | Settings > Firm Banking History: View Setting seed (2026-05-15, export gate) — seeds `SYS_SCR_OPTION_WECMS` with 7 columns (`REG_DTM`, `SVC_TYPE`, `SVC_NM`, `SEQ_NO`, `DEP_BANK_CD`, `RS_CD`, `RS_MSG`) under `web_svc_id = 'firm-banking-history'` / `dym_area_id = 'default'`. Every row `FIX_YN='Y'` (no popover); `SORT_YN='Y'` only where `FirmBankingHistorySortField` covers it (REG_DTM/SVC_TYPE/SVC_NM/SEQ_NO). Unlocks `FirmBankingHistoryExportProvider` — without the seed `ExcelExportService` would drop every column with `WBE2013`. Idempotent. |
  | V154 | Settings > ERP Transaction History: View Setting seed (2026-05-15, export gate) — seeds `SYS_SCR_OPTION_WECMS` with 7 columns (`REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `ORG_FILE_NM`, `FILE_SIZE`, `REG_USER`, `REG_ID`) under `web_svc_id = 'erp-transaction-history'` / `dym_area_id = 'default'`. Same shape + rationale as V153. `SORT_YN='Y'` only where `ErpTrnsHistorySortField` covers it (REG_DTM/ERP_TYPE/TRNS_TYPE_CD/REG_USER). Idempotent. |
  | V155–V241 | *(Historical pre-renumber range — superseded by the 2026-06-09 consolidation that archived the old higher-numbered migrations into `migration_archive_20260609/` and renumbered the live set to V1–V155.)* Various feature + stabilisation migrations across the Settings / Banking / Payment / Add-on modules. See per-file headers under `src/main/resources/db/migration/`. |
  | **V140 (live)** *(was labelled "V155" here)* | **⚠️ Number corrected 2026-06-23:** on the current disk the live `wb_get_next_val` function is **`V140__wb_get_next_val.sql`** — `V155` on disk is `mrec_pic_store`. The "V155 centralization" described in this row was renumbered to **V140** during the 2026-06-09 consolidation; the pre-renumber file is under `src/main/resources/db/migration_archive_20260609/`. Original (historical) text follows. **`wb_get_next_val` centralization (2026-06-09).** `CREATE OR REPLACE FUNCTION wb_get_next_val` superseding the prior V140 — curated **28 branches** (10 kept + 2 restored + 16 added), 8-param signature unchanged; plus raise-only `setval` floors for the four global sequences (`sq_appr_seq_no`/`firm_seq_no`/`firm_file_seq_no`/`sq_actv_seq`, already created in V143/V144/V147/V148). Highest *renumbered* `wb_get_next_val` migration — **NOT the highest migration overall**: later feature migrations continue past it (current max = **V248**; 158 `V*.sql` files — e.g. V156–V159 Settings/Add-on route-slug seeds, V172–V174 Smart Organizer, V196 Transfer History, V237 Card-Txn special fields, V242/V243 Navigation). See `db/migration/` per-file headers. See "`wb_get_next_val` centralization — FINAL STATUS" under DB Function / Legacy Function Policy + [docs/audits/wb-get-next-val-centralization-2026-06-09.md](docs/audits/wb-get-next-val-centralization-2026-06-09.md). |
  | V137 / V138 / V139 *(was "V242")* | **⚠️ Number corrected 2026-06-23:** the live navigation-catalogue tables are **V137** (`product_menu`), **V138** (`menu_item`), **V139** (`menu_item_translation`). The pre-renumber `V242__navigation_menu_catalogue.sql` is archived under `migration_archive_20260609/`. Original (historical) text follows. **Navigation menu catalogue (Phase 1 — refactor-owned product-tree tables).** Creates `MENU_ITEM` (product-independent node structure: slug PK, `PARENT_ID`, `MENU_DEP`, `ROUTE`, `ICON`, `SHOW_YN`, `PERMISSION_CD`, `LEGACY_MNU_ID`, `LEGACY_WSVC_ID`), `PRODUCT_MENU` (per-product membership + `DISP_ORD`; PK `(PRODUCT_TYPE, MENU_ITEM_ID)` — replaces the legacy per-product catalogue role of `MENU_AUTH_ADMN_SYS`), `MENU_ITEM_TRANSLATION` (per-product per-language label; PK `(PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)` — keyed by product because M's VI/KO labels diverge from F for the same node). These are **new refactor infrastructure tables** (not legacy JEX tables, not in the PROD Active Table List) that replace the lost JexAdmin / Jex Studio MenuManager trees (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`); seeded by Flyway and never mutated at runtime, so no audit columns. Idempotent (`CREATE TABLE / INDEX IF NOT EXISTS`). First consumer: `domain/navigation` (`GET /api/v1/navigation`). |
  | seed *(was "V243" — archived, not in live set)* | **⚠️ Number corrected 2026-06-23:** the navigation seed shipped as the pre-renumber `V243__navigation_menu_seed.sql`, now archived under `migration_archive_20260609/`. The live `V137`–`V139` files contain **no** `INSERT` — the seed was **not** carried into the renumbered `V*` set; confirm its live application path. Original (historical) text follows. **Navigation menu catalogue seed (F / M / H product trees).** Seeds `MENU_ITEM` (42 rows = 41 F-nodes + 1 HS Daily Financial), `PRODUCT_MENU` (115 rows: F=41, H=33, M=41) and `MENU_ITEM_TRANSLATION` (345 rows = 115 × EN/KO/VI) from the JexAdmin export at [docs/jex/features/menu-product-type-management/export/wecms-menu-export.json](../docs/jex/features/menu-product-type-management/export/wecms-menu-export.json). `permission_cd` values are the **verified live `menuAuth.ts` kebab codes** (cross-checked against `USER_MENU_AUTH_INFM` in [permission-code-normalization-verification.md](../docs/jex/features/menu-product-type-management/permission-code-normalization-verification.md) §1). **Daily Financial split:** F/M carry `banking-reports-daily-financial` (`rept_0015_001`), H carries `banking-reports-daily-financial-hs` (`rept_0017_001`). M is structurally identical to F (same slugs/routes/`permission_cd`s) and differs only in VI/KO translation rows. Idempotent (`INSERT … ON CONFLICT DO NOTHING`). Generated by `tmp/gen_nav_seed.js`. |

- Table/column naming: `UPPER_SNAKE`, FK constraints `FK_<child>_<parent>`. Mirror legacy JEX so data copies cleanly.

### Database (DEV — JEX live system)

- Read-only via the `postgres` MCP. Full deny list in [.claude/rules/database.md](../.claude/rules/database.md) — no INSERT / UPDATE / DELETE / DDL / EXPLAIN ANALYZE / DO $$…$$ / CALL.
- One-way flow: **DEV → SQL script / Flyway → LOCAL**. Never write back to DEV / UAT / PROD.

### Legacy Schema Parity Rule

When porting a legacy `wecmsvndev` table into Flyway, the **local DB schema must mirror DEV column-for-column**. This is non-negotiable — partial schemas are a future-bug factory.

**Do**

- For every migrated legacy table the local schema must match DEV exactly: column name, data type, length / precision / scale, nullability, primary key, important indexes, and DEFAULT values where the legacy DDL defines one.
- Before writing the migration, query DEV's `information_schema.columns` (read-only via `postgres` MCP) for the table; copy every column. Record the column count from DEV in the migration header so a future reviewer can spot drift.
- Add `CREATE INDEX IF NOT EXISTS` for the legacy indexes that real workloads use (date-range / tenant queries on history tables especially).
- If a parity gap is discovered after a migration is already applied, ship a corrective additive migration (`ALTER TABLE ADD COLUMN IF NOT EXISTS`). The V23 migration is the working precedent.
- Continue Flyway numbering correctly — never edit an applied migration.

**Don't**

- Don't create a 12-column simplified table just because the static UI reads 12 fields. If DEV has 100 columns, local has 100 columns. The "subset" concept applies to the JPA entity, not the DB schema.
- Don't drop legacy columns from DEV when bringing them into Flyway — even if you don't think Sprint 1 needs them. Future ETL or Sprint 2+ features will.
- Don't introduce ad-hoc surrogate columns (`SEQ_NO` of your own design, `FILE_PATH` instead of `SAVE_FILE_NM`, etc.) when the legacy table already encodes the same concept under its own name.
- Don't skip the DEFAULT clause for legacy NOT-NULL columns — Hibernate / `INSERT … SELECT` paths will hit constraint violations when the column lands in production.

**The schema vs. entity vs. DTO split**

- **DB schema** — must match DEV exactly. No subsets. (This rule.)
- **JPA entity** — may map only the fields Sprint 1 reads/writes. Hibernate `validate` ignores DB columns the entity doesn't declare. Document this in the entity Javadoc with a "Sprint 1 column subset; extend when …" note.
- **DTO** — exposes only the wire fields the API contract needs. Independent of both.
- **Seed data** — may insert only the columns the demo flow needs, provided the unselected columns are nullable or have a DEFAULT. Don't INSERT NULL into a NOT-NULL legacy column.

**Pre-migration checklist (paste into the MR description)**

```
- [ ] Queried DEV `information_schema.columns` for every table touched
- [ ] Local migration column count == DEV column count
- [ ] Every legacy NOT-NULL column has the legacy DEFAULT or is in the seed
- [ ] Legacy DEFAULTs preserved verbatim (e.g. PROV_STTS = 'OP', REG_CHNL = 'PC')
- [ ] PK + key indexes carried over
- [ ] If applied locally already, mismatch fixed by a NEW migration (additive only)
```

**Constraints, indexes, and triggers — also part of parity**

The "column-for-column" rule above extends to constraints and physical structures:

- **PRIMARY KEY**: must match DEV's PK column set + ordering (PostgreSQL respects PK column order for the underlying btree index). If a local PK was declared with the wrong column set, drop and recreate it via the corrective migration.
- **UNIQUE**: every legacy `*_uk*` UNIQUE constraint must be carried over. Multi-NULL columns are deduped via PostgreSQL's "NULLs distinct" default — no special handling needed.
- **CHECK**: every legacy `*_chk*` constraint must be carried over. Verify the local seed already passes the constraint *before* adding it (a `DELETE` of violating stub rows in the same migration is acceptable for fake demo data, never for real DEV-imported data).
- **FOREIGN KEY**: add the FK only if the target table exists locally with matching column types. If the target table is out of Sprint scope, document the deferred FK in the migration's trailing comment block; defer to the sprint that ports the dependency.
- **Triggers + audit `_log` functions**: legacy DEV uses BEFORE INSERT/UPDATE triggers (FK-like checks) and AFTER triggers (audit logging into `*_log_h` history tables). Sprint 1 endpoints are read-only, so missing triggers don't break Sprint 1 traffic. Defer trigger creation to the sprint that ports the dependent functions / history tables. Document the deferred triggers explicitly.
- **Indexes beyond PK/UK**: extra performance indexes (e.g. local `ix_*` on date+tenant) are allowed even when DEV has none — extra indexes don't hurt parity.

**Pre-migration parity checklist (extends the column checklist above)**

```
- [ ] PRIMARY KEY columns + order match DEV
- [ ] UNIQUE constraints from `*_uk*` series carried over
- [ ] CHECK constraints from `*_chk*` series carried over (with seed cleanup if needed)
- [ ] FOREIGN KEYs carried over OR documented as deferred with target table
- [ ] BIZ_KEY column type matches DEV (VARCHAR(20) — NOT 32)
- [ ] Triggers either ported or documented as deferred
- [ ] wb_get_next_val branches verified (Sprint 1 confirmed: returns NULL for all 17 tables — no dependency)
```

**Sprint 1 schema-parity audit (2026-04-29)**

Initial Sprint 1 migrations (V13 / V15) created subset tables. The audit caught the gap and shipped two corrective migrations:
- **V23**: column-level parity — added 144 missing legacy columns across 6 tables (most critically, BUSA_DOC_MSTR went from 20 to 100 columns).
- **V24**: constraint-level parity — aligned BUSI_INFM.BIZ_KEY VARCHAR(32)→(20), realigned 5 PRIMARY KEYs, added 1 UNIQUE + 11 CHECK + 5 FOREIGN KEY constraints.

Local schema now matches DEV column-for-column AND constraint-for-constraint for all 17 Sprint 1 tables, except for the deferred items documented in V24's trailer (FKs to `cash_repy_mstr` / `mrec_pic_store`; all 28 audit-log + sync triggers) which await Sprint 2+ dependency ports. JPA entities continue to map only the Sprint 1 column subset — that's the right scope for them.

**Final Sprint 1 parity audit (2026-04-30)** — full audit across all 23 local tables (auth + Sprint 1) against DEV. Two more corrective migrations shipped:
- **V28**: schema fixes — added missing DEV columns (`_empl_nm`, `reg_uid`); widened NUMERIC precision to match DEV (`(20,6)` for amounts, `(N)` for seq numbers, 30+ columns); widened VARCHAR(50)→(100) for `user_id`/`reg_id`/`mod_id`; added `acct_trns_hstr_uk01`, `busi_infm_uk01` UNIQUE; added `busi_infm_chk01` CHECK; realigned `busi_cond_daily_sumr_detl` PK and `user_login_his` PK to DEV shape.
- **V29**: real-DEV seed round 2 — 226 rows imported into 5 previously-empty tables (`bat_call_his`, `busa_doc_rcv_pay_his`, `einv_fee_detl`, `einv_tax_detl`, `ext_tran_call_his`).

Full report at [docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md](../docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md).

**Audit rule (added to this file):** **Run a full local-vs-DEV parity audit at the end of every sprint** before declaring the schema "done". Compare table-by-table: columns, types, lengths, defaults, PK/UK/CHECK/FK, indexes, triggers, row counts. Ship corrective additive migrations as gaps are found. Never edit applied migrations; never auto-delete local-only columns/tables; never blindly add DEV-only tables.

### DB Function / Legacy Function Policy

The refactor is moving away from legacy JEX-style PL/pgSQL helpers in favour of Java service logic. New code must follow these rules:

**Do**

- Prefer Java/service logic, Spring Data JPA repositories, and standard PostgreSQL identity / `SERIAL` / `IDENTITY` / native `SEQUENCE` behaviour for ID generation and value resolution.
- For legacy column shapes that already encode a generated value pattern (e.g. `BIZ_KEY = NTNL_CD_CHNL_ID_NNNNNN`), implement the generator in a `@Service` and write the value from Java — do not delegate to a stored function.
- Document any *unavoidable* DB-function dependency in this section: which migration created it, which service consumes it, and the Sprint that retires it.
- Before adding a new migration, check the existing `src/main/resources/db/migration/` listing in §"Database (LOCAL)" above and continue numbering correctly. Sprint 1 migrations were V13–V19 because V11 + V12 were already taken by Phase 1 bootstrap.
- Make new migrations idempotent where possible:
  - `CREATE TABLE IF NOT EXISTS`
  - `CREATE INDEX IF NOT EXISTS`
  - `ALTER TABLE … ADD COLUMN IF NOT EXISTS`
  - `INSERT … ON CONFLICT DO NOTHING` for seed rows
- For schema fix-ups (e.g. CHAR → VARCHAR alignment), `ALTER COLUMN … TYPE` with the same target type is a no-op — safe to re-run.

**Don't**

- Don't introduce new legacy DB-function dependencies in refactored Spring Boot code (banking, payment, member, e-invoice, operation, settings, …). No new `wb_get_next_val(...)`, no new ad-hoc PL/pgSQL helpers, no `CREATE FUNCTION` in feature migrations.
- Don't call legacy DB functions directly from Sprint feature code (controllers, services, repositories, schedulers). If you genuinely need one, it goes through a single funnel — see "Surviving legacy functions" below — never via a scattered `@Query(nativeQuery = true)` per repository.
- Don't introduce JPA `@Procedure` / `StoredProcedureQuery` / `@NamedNativeQuery` for stored procedures in Sprint 2+ feature work.
- Don't add `SERIAL` / `BIGSERIAL` / `IDENTITY` columns when the legacy table doesn't have one and the value can be assembled in Java (most JEX tables encode meaning in their PK — keep the meaning in Java).
- Don't modify a Flyway migration that has already been applied to a developer's local DB. If a fix is needed, ship a new migration (the V18 / V19 hotfix pair is the working precedent).
- **For `wb_get_next_val`, maintain branches only for locally migrated tables with a DEV branch.** The rule: when a table is migrated to local Flyway AND DEV's `wb_get_next_val` has a branch for it, that branch belongs in local. Do not port branches for tables not yet migrated locally. Do not port the full DEV function blindly. For Sprint 2+ tables, the branch is added in the same migration sprint that ports the table — confirmed with the lead before writing.

**`wb_get_next_val` centralization — FINAL STATUS (2026-06-09)**

The legacy-key generation surface was consolidated and centralized. The authoritative function is the live **`V140__wb_get_next_val.sql`** on disk (it was authored as `V155__wb_get_next_val_centralize.sql` and **renumbered to V140** during the 2026-06-09 consolidation — verify by `ls src/main/resources/db/migration/`), a `CREATE OR REPLACE` (8-param signature unchanged) carrying the **curated 28 branches** actually invoked by Spring (the 10 kept from V140 + 2 regressed branches restored — `audit_logs_infm.audit_logs_seq_no`, `busi_usag_set_infm.use_usag_cd` — + 16 added for tables whose Spring code previously used Java-side `MAX+1`). Status: **all in-scope business key generation now routes through `LegacyFunctionRepository` → `wb_get_next_val`; compile + affected tests pass.** Full record: [docs/audits/wb-get-next-val-centralization-2026-06-09.md](docs/audits/wb-get-next-val-centralization-2026-06-09.md).

**Funnel architecture rule (non-negotiable):**

```text
Spring services → LegacyFunctionRepository → wb_get_next_val() → JEX-compatible key generation
```

- **Exactly one executable SQL call to `wb_get_next_val` may exist**, and its only executor is [`common/legacy/LegacyFunctionRepository`](src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java) (`em.createNativeQuery("SELECT wb_get_next_val(...)")`). Verified 2026-06-09: it is the sole executable call site.
- **No repository or service may bypass the funnel.** No `@Query(nativeQuery=true)` and no `em.createNativeQuery(...)` calling `wb_get_next_val` outside `LegacyFunctionRepository`. (The former bypasses — `AuditLogSeqNoRepository` native `@Query`, deleted; `PurposeSettingsRepository.nextUseUsagCd` native `@Query`, removed — are gone.)
- **Do not add direct `wb_get_next_val` calls outside `LegacyFunctionRepository`.** New key allocation = add a branch to the live `wb_get_next_val` function (**`V140__wb_get_next_val.sql`** on the current disk — the "V155" cited elsewhere in this file is the pre-2026-06-09-renumber number) verbatim from the live JEX body + a named helper on the funnel + call the helper.
- **Remaining Java-side generators are documented exceptions only** (below) — never expand them.

**Documented exceptions (Java-side key generation kept by design — do NOT convert):**

1. **`EXPT_RESOL_MSTR.doc_no`** (`DocNoGenerator`) — JEX branch is env-set-driven (`BUSI/SYS_ENV_SET_INFM` prefix + date format); PO-declined port; `doc_no` has no unique constraint (legacy accepts the race).
2. **`MEMO_INFM.seq_no`** — `MEMO_INFM` is NOT in the PROD Active Table List (rule 6).
3. **Display-order / UI-ordering fields** — `rmrk_org_infm.disp_ord` (`OrganizerRuleService`), `busi_usag_set_infm.disp_ord` (`PurposeSettingsService`; the PK `use_usag_cd` IS funneled).
4. **Round-probe fields** — `bank_exch_rate_infm.seq_no` Woori round (`ExchangeRateBatchService.findHighestSeqNo`); a read for round computation, not a row-key allocator (the row PK `exch_seq_no` IS funneled).
5. **Date-prefixed composite payment keys** — `scdul_pymn_no` / `ovrs_pymn_no` and the child detail numbering (`scdul_pymn_detl_no`), via `InternalTransferKeyGenerator` / `OverseasPaymentKeyGenerator` + `*_KEY_SEQ` counter tables. Different format than JEX's plain-int `wb_get_next_val`; collision-free (disjoint numeric range). **Open Decision 2** below.

**Open decisions (recorded, not actioned — see [docs/planning/open-questions/wb-get-next-val-open-decisions.md](docs/planning/open-questions/wb-get-next-val-open-decisions.md)):**

- **OD-1 — Contacts `trco_acct_key`:** `ContactsService` accepts `trco_acct_key` from the client request instead of generating it via `wb_get_next_val` (the e-Invoice path to the same table IS funneled). Recorded as a create-vs-update key-contract review. **Do not change now.**
- **OD-2 — Date-prefixed payment keys:** the exception #5 architecture (`scdul_pymn_no` / `ovrs_pymn_no` / detail numbering). Recorded as a PO/architecture decision. **Do not change now.**

**Surviving legacy functions (kept by exception, not by default)**

> **Updated 2026-06-09:** the row below is **historical**. The live function on disk is **`V140__wb_get_next_val.sql`** with **28 branches** (authored as V155, renumbered to V140 in the 2026-06-09 consolidation; see "centralization — FINAL STATUS" above + the audit doc). The per-branch retirement plans still apply.

| Function / sequence | Created by | Consumed by | Why kept | Retire when |
|---|---|---|---|---|
| `wb_get_next_val(table, field, p1..p6)` — **28 branches** (V155, 2026-06-09; supersedes the historical 13) | V11 (2-branch stub) → V25/V26/V27 → V140 (consolidation, 10) → **V155** (centralized, 28) | `common.legacy.LegacyFunctionRepository` (the funnel). The former `domain.auth.bootstrap.*` consumers (`BusinessKeyGeneratorService` / `EmployeeNumberGeneratorService` / `CompanyBootstrapService`) were **deleted 2026-06-10** — the `BIZ_KEY` + `EMPL_NO` branches now have no Spring caller; the legacy **DBA manual setup SQL** still calls the same function directly against DEV / UAT / PROD | Branches exactly cover the locally migrated table set that has a DEV branch. Sprint 0: company key + employee number. Sprint 1: 11 tables with DEV branches (see §"Key-generation rule" below for list). Sprint 2+ tables: branch added with the table's migration sprint. | When Phase 3 retires the bootstrap path. Sprint 1 branches retire when each feature gets a Java-side generator. |
| `sq_biz_key` sequence | V11 | `wb_get_next_val('busi_infm','biz_key',…)` (via the BUSI_INFM branch) | Keeps legacy parity for the company-key generator. | Same retirement window as `wb_get_next_val`. |
| `wb_get_next_val('trco_infm','trco_key',…)` branch | V113 (Contacts MR, 2026-05-13; drafted as V85) | `LegacyFunctionRepository.nextTrcoKey` → `ContactsService.create` | OQ-4 (2026-05-12): align with legacy `SeqHelper.getNextVal` so refactor-created contacts inter-operate with live JEX inquiry/search paths whose usage is not fully verified. Branch body copied VERBATIM from DEV — `SELECT nextval('sq_trco_key')`. | Drop the branch (and `sq_trco_key`) and replace the funnel call with a Java generator once the legacy WeCMS / WABOOKS systems no longer call `wb_get_next_val` for `trco_infm`. No fixed deadline — re-evaluate at coexistence-cutover time. |
| `sq_trco_key` sequence | V113 (drafted as V85) | `wb_get_next_val('trco_infm','trco_key',…)` (Contacts) | OQ-4 alignment with DEV's global TRCO_KEY namespace. Local sequence starts at 1,000,000 — well above DEV's current ~9300 ceiling so newly-created contacts never collide with the V115 seed. | Same retirement window as the `wb_get_next_val` branch above. |
| `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang_cd)` | V114 (Contacts MR, 2026-05-13; drafted as V86) | `LegacyFunctionRepository.resolveNapasOrCitadBankName` → `ContactsService.list / getOne` | OQ-7 (2026-05-12): parity with the legacy `TRCO_INFM_R016` IDO at `IDO.TRCO_INFM_R016.xml:63`. `BANK_INFM` coverage of the NAPAS + CITAD code space (and the function's KH-fallback / language-variant rules) is unverified for this flow; using the function avoids hidden parity drift. `BANK_INFM` seed NOT extended. Body copied VERBATIM from DEV; reads `tb_firm_bank_code` (V59). | Once the NAPAS + CITAD code space is reconciled in `BANK_INFM` (both code dimensions, all four language columns), drop the function port and replace the funnel call with a `BankCdResolver` call. No fixed deadline — re-evaluate when a future feature MR audits `BANK_INFM` parity. |
| `wb_get_next_val('appr_line_infm','appr_line_no',…)` branch | V91 (Approval Line MR, 2026-05-13) | `LegacyFunctionRepository.nextApprLineNo` → `ApprovalLineService.create` | OQ-7 (2026-05-13): align with legacy `SeqHelper.getNextVal(util, idoCon, ApprLineInfm.class, "appr_line_no")` and stay consistent with the established Contacts (V113) refactor pattern. Branch body copied VERBATIM from DEV — `SELECT COALESCE(MAX(appr_line_no::numeric), 0) + 1 FROM appr_line_infm WHERE biz_key = $3`. Per-tenant MAX+1, not a sequence. | Drop the branch and replace the funnel call with a Java-side per-tenant generator once the legacy WeCMS / WABOOKS systems no longer call `wb_get_next_val` for `appr_line_infm`. No fixed deadline. |
| `wb_get_next_val('chart_acct_infm','chart_acct_seq_no',…)` branch | V95 (Chart of Accounts MR, 2026-05-13) | `LegacyFunctionRepository.nextChartAcctSeqNo` → `ChartOfAccountsService.create` | Align with legacy `SeqHelper.getNextVal(util, idoCon, ChartAcctInfm.class, "chart_acct_seq_no")` at the JEX DAO layer. Stays consistent with the established `appr_line_infm` (V91) refactor pattern — both use per-tenant MAX+1, not a sequence. Branch body copied VERBATIM from DEV — `SELECT COALESCE(MAX(chart_acct_seq_no::numeric), 0) + 1 FROM chart_acct_infm WHERE biz_key = $3`. Concurrency: service wraps the call + INSERT in one `@Transactional`; PK conflict surfaces as `DataIntegrityViolationException` and `ChartOfAccountsService.create` retries once before surfacing `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE`. | Drop the branch and replace the funnel call with a Java-side per-tenant generator once the legacy WeCMS / WABOOKS systems no longer call `wb_get_next_val` for `chart_acct_infm`. No fixed deadline. |

**Key-generation rule for migrated legacy tables**

Before adding a new migrated legacy table, the Spring Boot author MUST audit `wb_get_next_val` for that table:

```sql
SELECT wecmsvndev.wb_get_next_val('<lower_table>', '<lower_field>', NULL, NULL, NULL);
```

If the call returns non-NULL, that field uses legacy generated keys/codes (`{prefix}_{date}_{seq}` patterns, monotonic sequences, etc.).

- **If the Sprint feature is read-only / imports DEV rows**: keep the real DEV PK values verbatim in the seed migration (`INSERT … ON CONFLICT DO NOTHING` with the DEV value as the conflict target). Do NOT generate new IDs. Do NOT add a local `wb_get_next_val` branch.
- **If the Sprint feature creates new rows**: implement the generator in a Java `@Service` (preferred — see "DB Function / Legacy Function Policy" rule above). Only add a local `wb_get_next_val` branch as a temporary compatibility bridge if a hard legacy interop deadline forces it; document the branch and its retirement plan in this section.

**Sprint 1 key-generation audit (2026-04-29)**: 11 of 17 Sprint 1 tables have DEV `wb_get_next_val` branches (`acct_mstr.prn_seq`, `acct_trns_hstr.acct_seq_no`, `acct_trns_rcv_pay_evid_file.file_id`, `acct_trns_rcv_pay_infm.rcv_pay_seq_no`, `busa_doc_mstr.doc_id`, `busa_doc_rcv_pay_his.rcv_pay_his_seq_no`, `busi_cond_daily_sumr_detl.dtl_seq_no`, `einv_fee_detl.disp_ord`, `einv_tax_detl.disp_ord`, `ext_tran_call_his.seq_no`, `napas_remit_trns_hstr.seq_no`). Sprint 1 endpoints are entirely read-only — backend never inserts into these tables. Seed migrations (V17, V22) use real DEV PK values. **No new `wb_get_next_val` branches added; no Java generators added.** Sprint 2+ writes that create new rows in any of these tables MUST implement a Java service-side generator (not a new local branch).

If a future feature genuinely needs a new legacy-style function, document it in this table *before* writing the migration and call out the retirement plan in the same MR. No undocumented function may land in `src/main/resources/db/migration/`.

**Sprint 1 audit (2026-04-29)**

Verified: no Sprint 1 banking / e-invoice / operation code calls `wb_get_next_val` or any other legacy DB helper. All Sprint 1 IDs are either composite legacy PKs (no surrogate) or strings the service composes in Java. The only `CREATE FUNCTION` in `db/migration/` is V11's bootstrap function above.

### Reference data — single sources of truth

Reference data must be looked up from its canonical table, never duplicated as a static map in code.

| Reference | Source table | Loader / consumer | Notes |
|---|---|---|---|
| Bank code → display name (`BANK_CD` → `RPRS_BANK_NM`) | `BANK_INFM` (V20) | `BankInfmRepository` → `BankCdResolver` | Cache warmed once at startup; `BankCdResolver` falls back to a per-request DB lookup on cache miss. **Never** hardcode bank-code → bank-name mappings in services or DTOs. |
| Account-type code → label (`ACCT_TYPE_CD`) | (no dedicated table in legacy) | `BankCdResolver.resolveAcctTypeKey` → `AcctTypeCd` enum (`common/enums/`); `BankCdResolver.resolveAcctTypeNm` is a legacy English-only fallback. | Wire ships **both** `acct_type_nm` (legacy English label, kept one MR cycle) and `acct_type_key` (i18n key under `bank.acct_type.*`). Frontend renders via `t(acct_type_key)` and falls back to `acct_type_nm` when the key is null. |

If a future feature needs a new reference table, add an entity + repository, seed it from real DEV data via Flyway, and route lookups through the repository. Don't hardcode the table's contents into a `Map.of(...)` in a `@Component`.

### Domain enums — `common/enums/`

> **Modernized refactor enum policy (2026-05-08).** Stable legacy domain
> codes (`*_CD`, `*_DV`, `*_TP`, `*_STS`, `*_YN`) live as Spring enums
> under `com.kosign.wecms.common.enums.*`. Every enum carries a stable
> `code()` accessor (the verbatim legacy DB value) and an optional
> `i18nKey()` accessor. The refactor never ports JEX's `GenericEnum`
> interface, the `getLabel()` method, the `@JsonValue` /
> `@JsonCreator` annotations, or any localized label inside the enum
> body — labels live in `wecms-web/locales/{en,ko,vi}.json` and
> are resolved via `t(key)` on the frontend.

**Standard enum shape (every domain enum follows it):**

```java
public enum InoutDv {
    IN("1", "tx.inout.in"),
    OUT("2", "tx.inout.out"),
    ETC("0", null);

    private final String code;
    private final String i18nKey;

    InoutDv(String code, String i18nKey) { this.code = code; this.i18nKey = i18nKey; }

    public String code()    { return code; }
    public String i18nKey() { return i18nKey; }   // null when no verified translation exists yet

    public static Optional<InoutDv> fromCode(String raw) { … }
}
```

**Rules (non-negotiable):**

- **No `EnumType.ORDINAL`.** Enums are either non-persisted (today's pattern — DB columns stay `VARCHAR(N)` holding the legacy code, Java reads them as `String` and uses `Enum.fromCode(...)` at the service layer) or, when persistence mapping becomes necessary, mapped via a JPA `@Converter` calling `code()` / `fromCode()`. Never `@Enumerated(EnumType.STRING)` on a column whose DB value is a legacy code (the constant *name* and the *code* deliberately differ — e.g. `IN("1")`).
- **DB values preserved exactly.** The `code` field is the verbatim legacy string. New constants extend the legacy code set; renames are forbidden (renaming an enum constant changes nothing on the wire because `code()` is the source of truth).
- **No localized labels in the enum.** `i18nKey()` returns a stable key under a feature-scoped namespace (`bank.acct_type.*`, `tx.inout.*`, `fb_history.service.*`, `settings.firm_banking_history.service_type.*`). Frontend resolves the localized string. Setting `i18nKey()` to `null` is the explicit "translation not yet verified" signal — frontend renders the raw code or a wire-side fallback label and never blank.
- **No `@JsonValue` / `@JsonCreator`.** Enums never escape onto the wire — the API contract carries the raw legacy code string PLUS a sibling `*_key` field (e.g. `acct_type_cd` + `acct_type_key`). Jackson serialises the enum's `String` projection, not the enum itself.
- **No bulk-port.** Each enum lands in the MR that gets a real consumer. Single-use enums stay where they are; the second consumer is the trigger to extract.
- **Adding a new code:** one enum constant + one `bank.<feature>.<code>` locale entry per supported locale (`en` + `ko` + `vi`; never `km`). When the verified translation isn't available, omit the locale entry — frontend's missing-key fallback handles the gap.

**Enum index (current consumers — keep in sync as enums land):**

| Enum | Legacy source | DB column(s) | Consumers | i18n namespace |
|---|---|---|---|---|
| [`LangCd`](src/main/java/com/kosign/wecms/common/enums/LangCd.java) | `wabooks/enums/LangCd.java` | `BUSI_INFM.LANG_CD`, JWT `langCd` claim | `AuthService`, `AuthController`, `MessageResolver` | (fixed enum — no key set) |
| [`YesOrNo`](src/main/java/com/kosign/wecms/common/enums/YesOrNo.java) | `wabooks/enums/YesOrNo.java` | every `*_YN` column | helper for service-layer `Y`/`N` checks | (none — boolean projection) |
| [`TranTp`](src/main/java/com/kosign/wecms/common/enums/TranTp.java) | `wabooks/enums/TranTp.java` (codes 201–204) | `TB_FIRM_TRAN.TRAN_TP` | `FirmBankingServiceNameResolver.resolveF` | `fb_history.service.*` |
| [`InoutDv`](src/main/java/com/kosign/wecms/common/enums/InoutDv.java) | `wabooks/enums/InoutDv.java` (1, 2, 0) | `ACCT_TRNS_HSTR.INOUT_DV` | `TransactionService` (totals + filter + row mapping) | `tx.inout.*` (key registered, locale entries deferred) |
| [`AcctTypeCd`](src/main/java/com/kosign/wecms/common/enums/AcctTypeCd.java) | `wabooks/enums/AcctTypeCd.java` (01–08) | `ACCT_MSTR.ACCT_TYPE_CD` | `BankCdResolver.resolveAcctTypeKey`, `AccountService` filter sets | `bank.acct_type.*` (EN + KO verified) |
| [`FirmBankingSvcType`](src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) | (no JEX twin — V60 view discriminator) | `V_FIRM_BANKING_HISTORY.SVC_TYPE` | `FirmBankingHistoryService.toRow` | `settings.firm_banking_history.service_type.*` (EN + KO + VI verified) |
| [`PrdtType`](src/main/java/com/kosign/wecms/common/enums/PrdtType.java) | `wabooks/enums/PrdtType.java` (14 legacy letters; refactor models only `F`, `M`, `H`) | `BUSI_INFM.PRDT_TYPE`, JWT `prdtType` claim | `AuthService.login` (stamp), `AuthService.me` (echo) | `auth.prdt_type.*` (EN + KO + VI verified for F/M/H) |

**Deferred enums** — port each when its module migrates, not before:

- `BusaDocType` (e-Invoice doc type — `EInvoiceService.resolveDocType` is single-consumer; two-call rule).
- `ApprSts`, `ProcStat`, `ProcStatF` — payment processing not yet migrated to Spring.
- `ProvStts` — e-Invoice provisioning status; consumer keeps `provStts` raw until `ProvStts.fromCode` has a real call site.
- `BalAmtSign` — no consumer.
- ~190 other legacy enums — bulk-port forbidden; port when real consumer lands.

**BLOCKERS** (intentionally not lifted to enums):

- **e-Invoice status** — backend currently exposes `einv_invoicestatus` as a localized string from the legacy `wb_get_invoice_status(value, LANG_CD)` DB function (IR-4 deferred). Until the backend port replaces the localized string with a coded value, the frontend regex in `lib/api/einvoiceStatus.ts` stays.
- **`FBH_SUCCESS_CODES` per-leg policy** — F-leg uses 8-char VAN codes, NAPAS uses 4-char codes, NXISAS sends free-text. The legacy `RsCd` enum only covers 3 of the 4 variants. Lifting this to a single Spring resolver requires an explicit per-leg policy decision; until then, the frontend `Set` in `mappers.ts:562` stays.

**Unresolved translations** (do NOT invent — add when verified):

- `tx.inout.in` / `tx.inout.out` / `tx.inout.etc` — no locale file entries yet. Add when the Transaction History module resurfaces the labels server-side.
- `bank.acct_type.*` — `vi.json` entries deliberately omitted. Vietnamese translations for the 7 ACCT_TYPE_CD codes (02, 03, 04, 05, 06, 07, 08) are not in any verified project source. Frontend falls back to `acct_type_nm` (English) under VI locale until a verified translation lands.

### Screen Option (View Setting) — `domain/setting/`

View Setting is a **global / common WeCMS main feature**, not a Sprint 1 deliverable and not a Banking-specific endpoint. Every list page in the entire product (Banking, Payment, Add-on, Settings, every future module) reuses the same controller, service, DTOs, and tables — keyed only by `WEB_SVC_ID`. Adding a new page to View Setting is a Flyway-seed-only change on the backend; no new controller, no new service method, no new DTO. The popover (Figma 100:13382 / legacy `comm_0108_001`) is the canonical reference. Backend module is `domain/setting/`; tables are `SYS_SCR_OPTION_WECMS` (system defaults, PK `WEB_SVC_ID + FIELD_ID`) and `USER_SCR_OPTION_WECMS` (per-user overrides, PK `BIZ_KEY + USER_ID + WEB_SVC_ID + FIELD_ID`). The base schema landed in V30 alongside Sprint 1's first consumer; the schema itself is screen-agnostic and stays as-is — only seed rows are added per page.

**Endpoints** (`domain/setting/controller/ScreenOptionController.java`):

| Method | Path | Returns | Legacy |
|---|---|---|---|
| GET    | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (merged for one area; lazy-INIT on first call per scope) | `comm_0108_001_r001_osmc` (lazy-INSERT mirrored — see [docs/jex/features/view-setting.md §1.6](../docs/jex/features/view-setting.md)) |
| GET    | `/api/v1/screen-options/{webSvcId}/areas`       | `ScreenAreaListResponse` (distinct areas + counts) | `SYS_SCR_OPTION_WECMS_R002` |
| PUT    | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (refreshed) | `comm_0108_001_u002_osmc` |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (re-init'd from sys) | `comm_0108_001_u001_osmc` (DELETE-then-INSERT-from-sys; user table always populated post-reset — see [docs/jex/features/view-setting.md §1.7](../docs/jex/features/view-setting.md)) |

**Rules (non-negotiable):**

- Every new list page that adopts View Setting **MUST** seed its column set into `SYS_SCR_OPTION_WECMS` in the same Flyway migration that ships the page. The seed is the source of truth — frontend `FALLBACK_COLUMNS` is a paint-time stand-in, never a runtime authority.
- `WEB_SVC_ID` is the legacy JEX page id (snake-case, e.g. `fnsh_0001_001`). Do **not** invent new ids; reuse the JEX one so the frontend stays in lockstep with the legacy docs.
- `FIELD_ID` is uppercase and stable across releases (e.g. `BANK`, `ACCOUNT_NICKNAME`). Renaming a `FIELD_ID` orphans every saved user override — treat the FIELD_ID set as a public contract.
- Locked columns (a column the user cannot hide / reorder, e.g. Account Inquiry's `BANK`) are **not** a DB concern. The live JEX `SYS_SCR_OPTION_WECMS` has no `FIX_YN` column, and the refactor **removed** `FIX_YN` from the entity / DTO / service / wire (2026-06-11) so `ddl-auto=validate` passes against the unmodified 10-column JEX table. Fixed columns are declared **statically in the frontend View Setting registry** (`lockedFieldIds` passed to `<ViewSettingPopover>`), never via a DB flag. The structural `CHECKBOX` / `ETC` columns are still locked server-side via `ScreenOptionService.SPECIAL_FIELD_IDS`, independent of any DB column. Do **not** re-add a `FIX_YN` column or any other column beyond the legacy 10.
- Save uses **delete-then-insert** for the (BIZ_KEY, USER_ID, WEB_SVC_ID) scope — mirrors `comm_0108_001_u002` and is atomic via `@Transactional`. The DTO carries the FULL merged column list (including USE_YN='N' rows) so order survives across save.
- Reset deletes user rows for the scope and **immediately re-inits from sys** in the same transaction (legacy parity with `comm_0108_001_r001` lazy-init + `u001` delete). The user table is always populated post-reset; the response is built from the freshly-init'd rows directly. See [docs/jex/features/view-setting.md §1.7](../docs/jex/features/view-setting.md).
- Load (GET) does **lazy-INSERT on first call** per `(BIZ_KEY, USER_ID, WEB_SVC_ID, DYM_AREA_ID)` scope: if the user has zero rows for the scope, every sys row is copied into `USER_SCR_OPTION_WECMS` (FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, DYM_AREA_ID, REG_ID/MOD_ID = current user) before the response is built. Subsequent loads skip the init branch. Mirrors legacy `comm_0108_001_r001`. See [docs/jex/features/view-setting.md §1.6](../docs/jex/features/view-setting.md).
- Unknown FIELD_IDs in a save payload (frontend bundle drift) are silently dropped — never throw. The popover's GET response on success will reconcile the user with the canonical column set.
- New columns belong to the page's **own** Flyway migration. Don't sneak `INSERT` rows into V30 — that file is closed (it covers only the base schema + the first consumer `fnsh_0001_001`). Each future page adds its rows in the migration that ships the page, with a comment like `-- Seed: <page name> column set`. The migration file numbering is whatever fits the next free slot in `db/migration/`; the seed has nothing to do with V30 and is not blocked by it.
- Adopting a new page on the backend is **seed-only**. No new controller, no new service method, no new DTO, no entity change. The endpoints `GET / PUT / DELETE /api/v1/screen-options/{webSvcId}/{dymAreaId}` already accept any `(WEB_SVC_ID, DYM_AREA_ID)` pair — they were designed product-wide, not for one Sprint. If a refactor is tempted to fork `ScreenOptionController` per module ("BankingScreenOptionController", "PaymentScreenOptionController", etc.), the refactor is wrong — extend the seed, not the API surface.
- **`WEB_SVC_ID` value convention is semantic route-based kebab-case** — `<module>-<page>[-<type>]` (e.g. `banking-account-inquiry-vnd`). The refactor frontend's central registry at [`wecms-web/lib/viewSettingScreens.ts`](../wecms-web/lib/viewSettingScreens.ts) is the single source of truth — the API and the DB both key against the same string the registry exports. Backend `@Pattern` enforces the format (`screenId` requires ≥ 2 kebab segments via `^[a-z][a-z0-9]*(-[a-z0-9]+)+$`; `dymAreaId` accepts a single segment so `default` passes). The regex deliberately rejects legacy JEX ids (`fnsh_0001_001`, `fnap_0001_001`, etc. — underscores not allowed) so a stale frontend cannot orphan rows under the wrong namespace. Duplicated tokens (`banking-banking-account-inquiry-vnd`, `banking-account-inquiry-account-inquiry-vnd`) and module-less ids (`account-inquiry-vnd`) pass the regex but fail review — reject in the MR. Bad path input returns `WBE_VALIDATION` via the standard envelope; unknown screenId values (regex passes but no `SYS_SCR_OPTION_WECMS` row) return `WBE2001` from `ScreenOptionService`.
- **`DYM_AREA_ID` is required** in every API call and every DB row. Single-area pages use `default`; multi-area pages use meaningful kebab-case names (`summary`, `detail`, `current-accounts`). V32 backfills any nulls and locks both columns to `NOT NULL`. See [docs/jex/features/view-setting.md §1.5](../docs/jex/features/view-setting.md) for the full naming rules.
- **Special FIELD_IDs — `CHECKBOX` (`DISP_ORD=0`) and `ETC` (`DISP_ORD=99`)** — ride through the View Setting data so column order / Save / Reset preserve them, but they are NEVER editable by the end user. The popover hides them; the backend locks them. `ScreenOptionService.SPECIAL_FIELD_IDS` is the authoritative set on the API side: on Save, rows whose `FIELD_ID` is in that set always persist with `USE_YN='Y'` and `DISP_ORD` taken verbatim from the system seed, regardless of the payload — a stale or buggy frontend cannot relax the rule. For non-special columns, Save trusts the frontend's `DISP_ORD` because the popover renumbers densely starting at 1 after every reorder. Pages seed `CHECKBOX` only when their table renders the row-selection column (Account Inquiry VND in V33); do NOT bulk-seed `ETC` across pages that do not have an action column.

**Pages currently configured:**

| WEB_SVC_ID | Page | Migration | Columns |
|---|---|---|---|
| `banking-account-inquiry-vnd` (DYM_AREA_ID `default`) | Banking > Account Inquiry (VND) | V30 (initial seed) → V32 (cutover to semantic id + DYM_AREA_ID NOT NULL) → V33 (CHECKBOX special row) → V34 (SIZE values) → V35 (schema parity tightening — column types/NN, no row changes) | CHECKBOX · BANK · ACCOUNT_NICKNAME · BALANCE · AVAILABLE_BALANCE · VAS_ACCOUNT (hidden default) · LAST_UPDATE |

Adding the FCY twin (`fnsh_0005_001`), Approval Box, Transaction History, e-Invoices, Operation Status, Payment list pages, Settings tables, and every future list-page-with-list-icon happens in those features' own seed migrations — never as a sweeping "seed all pages" migration, and never as a backend code change. Keep the seed adjacent to the feature that owns it. The list above grows over time as adoption rolls out; the controller / service / DTOs do not.

### Common table sort — `common/web/` whitelist + per-domain enum

Table-header sorting uses the same `FIELD_ID` keys as View Setting and shares one common API contract across every list endpoint. The frontend cycle (`default → ASC → DESC → default` — see [wecms-web/CLAUDE.md §13a](../wecms-web/CLAUDE.md)) sends two query params — `sortField` (View Setting `FIELD_ID`) and `sortDirection` (`ASC` / `DESC`) — and the backend resolves them through a per-domain whitelist enum, never as a raw SQL string.

**Common primitives — [`common/web/`](src/main/java/com/kosign/wecms/common/web/):**

- `SortDirection` — enum `{ASC, DESC}` with a lenient `parse(String)` that returns `null` on blanks / unknown values so the service falls back to the default ORDER BY.
- `SortableField` — interface every per-domain whitelist enum implements; exposes a single `entityProperty()` accessor returning the JPA entity property name (e.g. `"bal"`, `"acctNickNm"`).

**Per-domain whitelist enum** (one per list endpoint):

- Lives next to the service that consumes it (e.g. `domain/banking/service/AccountSortField.java`).
- Constant names mirror the View Setting `FIELD_ID` (uppercase semantic key — `BANK`, `BALANCE`, `LAST_UPDATE`).
- Each constant maps to the matching JPA property on the entity — the only place wire input is translated into a sort column.
- Lenient `parse(String)` returns `null` for unknown / blank wire values so the service falls back to the default ORDER BY (no exception, no echo of user input into a query).

**Rules (non-negotiable):**

- **Never concatenate a raw `sortField` into a JPQL / native query.** Every sortable column passes through the per-domain whitelist enum — the wire string is matched against `valueOf(...)` and either resolves to an enum constant or returns `null`. There is no third option, and no SQL string ever contains the user-supplied value.
- **Use Spring Data `Sort` built from the enum's `entityProperty()`.** `Sort.by(Sort.Order.asc(field.entityProperty()))` — the property is JPA-validated against the entity metamodel. A typo in the enum mapping fails fast at query time rather than reaching the database.
- **The default ORDER BY mirrors the legacy listing whenever the legacy sort key is mappable.** When `sortField` / `sortDirection` are absent or unknown, the service returns rows in the legacy default order. For Account Inquiry the legacy default is `prn_seq:asc` (legacy [`SortHelper.java#86`](../WECMS%20DEV/WECMS/src/wabooks/helper/SortHelper.java)) — `DEFAULT_SORT = Sort.by(asc("prnSeq"), asc("bankCd"), asc("acctNo"))` matches it, with `bankCd, acctNo` as deterministic tiebreakers (and a fallback for rows where `prnSeq` is NULL — V22 seed predates the V23 schema column, so unassigned rows naturally sort last under PostgreSQL's default ASC NULLS-LAST). When the legacy sort key is **not** safely mappable (entity field missing from the Sprint subset, NUMERIC overflow risk, value-coverage gap that would change observable order), keep the existing default but document the deferral inline with a `// TODO:` referencing the legacy column and the blocking constraint — never silently diverge.
- **Always append the default order as a stable secondary key** when the user requested a primary sort: `Sort.by(primary).and(DEFAULT_SORT)`. Equal values on the user-chosen column then keep a deterministic order across requests, which matters for pagination and snapshot tests.
- **Special FIELD_IDs are NEVER in the whitelist.** `CHECKBOX` and `ETC` are admin-controlled rows that ride through View Setting for column ordering — they are not sortable on either side. Frontend filters them via `isSpecialViewSettingField(...)`; backend simply does not register them in the enum, so a malicious / stale client request resolves to default ORDER BY.
- **Seed `SORT_YN` and the whitelist enum must agree.** Every column with `SORT_YN='Y'` in `SYS_SCR_OPTION_WECMS` MUST have a constant in the matching whitelist enum (`AccountSortField` etc.). When a column has no backing entity field yet, seed it with `SORT_YN='N'` — never lean on the silent `parse()`-returns-null fallback to mask UI-visible-but-inert sort glyphs. The defensive fallback exists for stale clients, not for permanently misconfigured columns. Reference: V41 flips `VAS_ACCOUNT.SORT_YN` to `'N'` precisely because the column is in the View Setting registry but not (yet) in `AccountSortField` — when its source data lands, the same migration that adds the enum constant flips `SORT_YN` back to `'Y'`.
- **Public service signature carries the optional sort.** Add an overload that accepts `(String sortField, String sortDirection)` (or refactor the existing call site) so the controller forwards the wire values verbatim. Never expose the enum across the controller boundary — parsing happens in the service.
- **Controllers stay thin.** `@RequestParam(value = "sortField", required = false)` + `@RequestParam(value = "sortDirection", required = false)` — pass the raw strings through. No regex validation, no `@Pattern` on these two params (the enum-`parse(...)` step in the service is the validator).
- **Adopting a new endpoint** is purely additive: write a new whitelist enum, add the two query params to the controller, build the `Sort` in the service, and call the repository's `Sort`-accepting variant (`findBy...(args, Sort sort)`). No changes to `common/web/`, no new controller / DTO / migration on the View Setting side.

**Reference implementation:**

- Common: [`common/web/SortDirection.java`](src/main/java/com/kosign/wecms/common/web/SortDirection.java), [`common/web/SortableField.java`](src/main/java/com/kosign/wecms/common/web/SortableField.java).
- Banking domain: [`AccountSortField`](src/main/java/com/kosign/wecms/domain/banking/service/AccountSortField.java) — whitelist for Account Inquiry VND/FCY (`BANK` → `bankCd`, `ACCOUNT_NICKNAME` → `acctNickNm`, `BALANCE` → `bal`, `AVAILABLE_BALANCE` → `realAmt`, `LAST_UPDATE` → `acctBalLstInqDtm`). **HR-1 (2026-06-06):** `LAST_UPDATE` maps to `acctBalLstInqDtm` (the column the sync stamps + the legacy fnsh_0001_001 grid binds), not `acctBalUpdDtm` — the displayed cell and its sort key stay aligned. The wire field is still `acct_bal_upd_dtm` (frontend contract unchanged); `AccountService.toRow` sources its value from `ACCT_BAL_LST_INQ_DTM`.
- Service application: [`AccountService.list(currency, sortField, sortDirection)`](src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java) — parses both wire values, builds the `Sort`, falls back to `DEFAULT_SORT = Sort.by(asc("prnSeq"), asc("bankCd"), asc("acctNo"))` on null inputs (legacy `fnsh_0001_001` / `SortHelper#86` parity; `prnSeq` is the V23 nullable `NUMERIC(2,0)` print-order column on `AcctMstr`).
- Controller: [`AccountController#listAccounts`](src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java) — three query params (`currency`, `sortField`, `sortDirection`).
- Repository: [`AcctMstrRepository#findByBizKeyAndActvYn(...)`](src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java) and the two filtered variants — each takes a `Sort` parameter alongside the legacy `OrderBy*` finder methods (kept for `AccountService#ownAccounts()`).

**Forbidden patterns (caught at review):**

- `@Query(value = "... ORDER BY " + sortField, nativeQuery = true)` — concatenating user input into SQL.
- `Sort.by(sortField)` (passing the wire string directly to `Sort.by`) — bypasses the whitelist; even though Spring Data validates against the metamodel, `sortField` is also the wire FIELD_ID, not the entity property name.
- Throwing `IllegalArgumentException` / `BusinessException` on an unknown `sortField` — degrade gracefully to the default ORDER BY, never surface a 4xx for a stale frontend bundle.
- Adding `sortField` / `sortDirection` to the path: `/accounts/{sortField}` — they are query params, never path segments.
- Forking `SortDirection` per domain (`AccountSortDirection`, `PaymentSortDirection`, …) — the enum is global and lives in `common/web/`.

**Endpoints with common-sort wired:**

| Endpoint | Whitelist enum | Sortable FIELD_IDs | Default ORDER BY |
|---|---|---|---|
| `GET /api/v1/banking/accounts` | `AccountSortField` | `BANK`, `ACCOUNT_NICKNAME`, `BALANCE`, `AVAILABLE_BALANCE`, `LAST_UPDATE` | `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (legacy `fnsh_0001_001` parity; NULL `PRN_SEQ` rows sort last via PostgreSQL's default ASC NULLS-LAST) |

Other list endpoints adopt the contract in their next stabilisation pass — one per-domain enum + controller params + service `Sort` build per migration. The common primitives in `common/web/` do not change.

### Legacy report DB functions — Sprint 1 stance

The 2026-04-29 audit identified three legacy PL/pgSQL helpers in `wecmsvndev` that back the daily-financial / daily-cash / cash-flow reports:

| Function | Type | Used by legacy | Sprint 1 stance |
|---|---|---|---|
| `wb_insert_daily_fincl_rept_daily_sumr_detl_data(p_biz_key, p_req_date, p_req_date2)` | `void` (writes) | BCS batch — refreshes Daily Financial CMC summary tables | **NOT called from Spring.** Sprint 1 reads pre-aggregated rows from `BUSI_COND_DAILY_SUMR(_DETL)` (or stub seed). The batch port lands in Sprint 2/3. |
| `wb_insert_daily_fincl_rept_daily_cash_sumr_data(p_biz_key, p_req_date, p_req_date2)` | `void` (writes) | BCS batch — refreshes Daily Cash summary tables | **NOT called from Spring.** Same reasoning. |
| `fn_get_haengsung_daily_financial_report(p_biz_key, p_as_of_date, p_account_filter)` | `TABLE(...)` (read-only set-returning function) | `rept_0017_001` HAENGSUNG ledger query | **CALLED (2026-06-14).** The Sprint-1 hardcoded stub was removed; `ReportService.dailyFinancialHs` now calls this function directly through the single funnel `DailyFinancialHsReportRepository` (native query, not scattered `@Query`). The function + its base table `REPT_TRNS_HSTR` were **added to the refactor DB baseline** (PO-approved off-inventory addition). Remaining: bank/account multi-select UI, seed `REPT_TRNS_HSTR` rows, live HTTP smoke. |

Per the policy above, the `wb_insert_daily_fincl_rept_*` batch-write helpers are still **not** called from Spring — and **Daily Financial CMC does not need them**: it reads the underlying read-only function `rept_0015_001_r001_rec1` directly (the function `wb_insert_*` materialises into the summary tables), so there is no write-on-read and the `DAILY_FINCL_REPT_*` summary tables are not read as the primary source. `fn_get_haengsung_daily_financial_report` **is now called** (HS report, 2026-06-14) via `DailyFinancialHsReportRepository` — the documented set-returning exception to the "no new DB-function dependencies" rule. **Cash Flow** likewise calls `get_cashflow_report` directly, **Daily Cash** calls `rept_0900_001_rec1..4`, and **Daily Financial CMC** calls `rept_0015_001_r001_rec1` (via `DailyFinancialCmcFunctionRepository`) — see the `domain/banking` reports row in the Feature Status table.

### Excel export — `common/export/` shared pipeline + per-screen `ExportRowProvider`

Excel / XLSX export is a **global / common WeCMS feature** keyed by the same View Setting `WEB_SVC_ID` registry that drives column visibility / order and table sort. Every list page that exposes the toolbar Excel icon (Banking, Payment, Add-on, Settings, every future module) routes through one shared service, one shared writer, one shared registry, and a thin per-resource controller endpoint. The first wired page is **Banking > Account Inquiry (VND)** at `POST /api/v1/banking/accounts/export`; other paginated pages adopt as part of their next stabilisation pass.

**Common primitives — [`common/export/`](src/main/java/com/kosign/wecms/common/export/):**

- [`ExcelExportRequest`](src/main/java/com/kosign/wecms/common/export/ExcelExportRequest.java) — wire body shared by every per-resource endpoint. Carries `screenId` + `dymAreaId` + `fileName` + `filters` + `sort` + `columns` + optional fallback `subHeader`. Validated via `@Pattern` on the screen / area ids; bad input → `WBE_VALIDATION` envelope.
- [`ExcelColumnSpec`](src/main/java/com/kosign/wecms/common/export/ExcelColumnSpec.java) — one column entry; `fieldId` + already-translated `header` + `format` + `ccyCd` + `align`. Frontend ships these in the order the user sees them on screen; backend re-intersects with the persisted View Setting visibility before emitting.
- [`ExcelColumnFormat`](src/main/java/com/kosign/wecms/common/export/ExcelColumnFormat.java) — `TEXT | AMOUNT | NUMBER | DATE | DATETIME`. Backend owns the actual cell rendering — single contract for amount / date formatting across every consumer.
- [`ExcelAlignment`](src/main/java/com/kosign/wecms/common/export/ExcelAlignment.java) — `LEFT | RIGHT | CENTER` with `AMOUNT` / `NUMBER` defaulting to `RIGHT` when unspecified.
- [`ExcelSortSpec`](src/main/java/com/kosign/wecms/common/export/ExcelSortSpec.java) — same wire contract as the list endpoints' common-sort (`field` = View Setting `FIELD_ID`, `direction` = `ASC` / `DESC`).
- [`ExcelWorkbookWriter`](src/main/java/com/kosign/wecms/common/export/ExcelWorkbookWriter.java) — Apache POI **SXSSF** (streaming) writer. Pooled `CellStyle`s (9 styles total — text / number / amount × LEFT / RIGHT / CENTER + title + subheader + header) so a 50k-row export stays bounded in heap. Title row → optional subheader row → blank → header row → data rows. Format coercion failures degrade to text — never throw.
- [`ExportableScreenRegistry`](src/main/java/com/kosign/wecms/common/export/ExportableScreenRegistry.java) — Spring auto-collects every `@Component implements ExportRowProvider`. Rejects duplicate screenIds at startup. `WBE2010` for unknown `screenId`.
- [`ExportRowProvider`](src/main/java/com/kosign/wecms/common/export/ExportRowProvider.java) — per-screen strategy interface. Returns `ExportFetch` (`rows` keyed by FIELD_ID + backend-computed `subHeader`).
- [`ExcelExportService`](src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) — orchestrator: resolve provider → intersect columns with allow-list AND persisted View Setting visibility (defence-in-depth) → `provider.fetch(...)` → cap rows at `MAX_ROWS = 50_000` → stream XLSX via the writer with RFC 5987 UTF-8 `Content-Disposition` filename.

**Per-resource endpoint pattern (CLAUDE.md decision 3 — never a generic dispatcher):**

```
POST /api/v1/{module}/{resource}/export
Content-Type: application/json
Body: ExcelExportRequest
→ HTTP 200, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
→ Content-Disposition: attachment; filename*=UTF-8''<percent-encoded>.xlsx
```

The controller is intentionally thin — one `@PostMapping` that takes `@Valid @RequestBody ExcelExportRequest` + `HttpServletResponse`, forwards to `excelExportService.exportTo(response, request)`, returns `void`. Auth is the same JWT path as the list endpoint (everything outside `/api/v1/auth/*` is `authenticated()` per [`SecurityConfig`](src/main/java/com/kosign/wecms/config/SecurityConfig.java)).

**Rules (non-negotiable):**

- **Never write Excel generation per page.** Every screen routes through `ExcelExportService`. The fix for a new column / filter / format is to extend the shared module first, then consume it — forking is not permitted.
- **Backend is the source of data.** The export endpoint re-runs the same filter+sort path the list endpoint uses (Account Inquiry's provider calls `AccountService.list(currency, sortField, sortDirection)`). Frontend never ships rows.
- **Backend is the authority on visibility.** The frontend's `columns[]` is a hint; the service intersects it with: (a) the provider's `allowedFieldIds()` allow-list and (b) the persisted View Setting (`USE_YN='Y'` from `ScreenOptionService.load(...)`). Special FIELD_IDs (`CHECKBOX`, `ETC`) are dropped by `ScreenOptionService.isSpecialFieldId(...)` — defence in depth alongside the frontend's own filter.
- **Column order comes from the request** — honours unsaved popover reorders (CLAUDE.md decision 4) — but only AFTER the visibility intersection. Hidden columns never leak even when the frontend asks for them.
- **All filtered+sorted rows, never paginated.** Bounded by `MAX_ROWS = 50_000` (CLAUDE.md decision 2). Exceeded → `WBE2011`.
- **i18n labels live on the frontend.** The wire `header` is already-translated text; the backend never re-resolves locale-aware strings for export columns.
- **Subheader is backend-computed** (CLAUDE.md decision 5). The provider returns the canonical count + totals string in `ExportFetch.subHeader`; the request's `subHeader` is informational fallback only.
- **Amount values in the workbook MUST go through the shared display formatter** — never the raw wire string and never an ad-hoc `String.format` numeric template. Specifically:
  - **Subheader / summary line** (e.g. `"20 account(s) · Balance: 19.886.030.287₫ · Available Balance: 8.417.734.439₫"`) — provider composes via [`AmountFormatter.forDisplay(value, ccyCd)`](src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java), which mirrors the frontend [`lib/currency.ts formatAmount`](../wecms-web/lib/currency.ts) 1:1 (vi-VN locale + `₫` suffix for VND, en-US + prefix for USD/EUR/KRW/JPY, vi-VN + no symbol for everything else). The on-screen summary card, the print popup, and the Excel subheader render the same number identically.
  - **Numeric data cells** (Balance, Available Balance, etc.) — `format: "amount"` on the wire `ExcelColumnSpec` triggers POI's `#,##0.##` data format inside the cell, applied automatically by [`ExcelWorkbookWriter`](src/main/java/com/kosign/wecms/common/export/ExcelWorkbookWriter.java). The provider hands the writer raw `BigDecimal` / wire-decimal strings — never a pre-formatted string with embedded thousand separators or symbol — so Excel can re-sum the column natively.
  - **Title cell** stays free-form (no amount formatting needed; mirrors the on-screen page title).
  - **Free-text amount fields embedded in any other cell** — same rule: route through `AmountFormatter.forDisplay` so locale + symbol stay consistent across every export consumer. No `String.format("%,d ₫", n)`, no `BigDecimal.toString()`, no per-page formatter.
- **In-sheet title vs. filename are two distinct strings.** The wire `title` field carries the on-screen page title (with natural spacing — `계좌 조회 (VND)` / `Account Inquiry (VND)`); the wire `fileName` carries the legacy filesystem-friendly form (no inner space + `_DDMMYYYY` suffix — `계좌조회(VND)_02052026`). [`ExcelExportService`](src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) prefers `title` for the row 0 merged cell; older callers that omit `title` fall back to `fileName` (legacy parity). Frontend pages source the two from separate i18n keys (`<screen>.title` for the on-screen header / in-sheet title, `<screen>.export_title` for the no-space filename base).
- **File name is caller-supplied** (e.g. `"Banking Account Inquiry (VND)"`); controller appends `.xlsx`. No timestamp by default — matches legacy. RFC 5987 `filename*=UTF-8''…` so non-ASCII (Korean / Khmer) round-trips.
- **Filter shape is per-screen.** `filters: Map<String, Object>` is opaque to the service; the provider decides which keys it understands. Account Inquiry's provider consumes `currency` (DB-side) plus `bankName` / `keyword` (post-fetch in-memory, mirroring the page's client-side filter). When the list endpoint grows real DB filters for those keys, drop the in-memory pass — the controller signature does not change.
- **Sort whitelist still enforces.** The provider forwards `sort.field` / `sort.direction` straight to the list service which validates against the per-domain enum (e.g. `AccountSortField`). Unknown FIELD_IDs fall back to default ORDER BY — never echo into a SQL string.
- **Adopting a new endpoint** is purely additive: write a new `ExportRowProvider` (one class, registers itself via `@Component`), add the `@PostMapping("/{resource}/export")` to the existing resource controller, wire the row map → FIELD_ID. No changes to `common/export/`, no new DTO, no new request shape.
- **Tenant scope** — `BIZ_KEY` filter is mandatory; the provider inherits the list service's tenant scoping (`TenantContext.currentBizKey()`). The export must NEVER cross tenants.
- **No `@Transactional` on the orchestrator.** `ExcelExportService.exportTo` deliberately runs without an outer transaction so the streaming write does not hold a DB transaction open. Each inner call (`ScreenOptionService.load`, `AccountService.list`) opens its own scope.

**Forbidden patterns (caught at review):**

- Writing to a `Workbook` outside `ExcelWorkbookWriter` — there is one writer; pages do not poke POI directly.
- Generic dispatcher endpoints like `POST /api/v1/exports/{screenId}` — every export endpoint is per-resource (CLAUDE.md decision 3).
- Adding a new XLSX library (EasyExcel, jxls) alongside POI — POI 5.x is the single producer.
- A frontend `xlsx` / `exceljs` dependency — backend produces the file; frontend posts JSON and saves the blob.
- Frontend-shipped row arrays for export — pages MUST send filter / sort / column-spec context, never the rendered rows.
- Concatenating `screenId` / `fieldId` / `sortField` into a SQL string — every wire identifier passes through the typed validators above.
- Amount values rendered with `String.format("%,d", n)` / `String.format("%,.2f ₫", n)` / `BigDecimal.toString()` / `value + " ₫"` — every monetary string in the workbook (subheader, free-text cells) MUST go through `AmountFormatter.forDisplay(...)` so locale + symbol stay consistent.
- Composing the in-sheet title from `request.fileName()` when `request.title()` is set — the writer always emits `title` first; do not collapse the two fields back into one.

**Reference implementation:**

- Controller: [`AccountController#exportAccounts`](src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java).
- Provider: [`AccountInquiryExportProvider`](src/main/java/com/kosign/wecms/domain/banking/export/AccountInquiryExportProvider.java) — registers `screenId = "banking-account-inquiry-vnd"`, allow-list `{BANK, ACCOUNT_NICKNAME, BALANCE, AVAILABLE_BALANCE, VAS_ACCOUNT, LAST_UPDATE}`, reuses `AccountService.list`, computes the `<count> account(s) · Balance: … · Available Balance: …` subheader server-side.

**Endpoints with Excel export wired:**

| Endpoint | Provider | Allowed FIELD_IDs | Subheader source |
|---|---|---|---|
| `POST /api/v1/banking/accounts/export` | `AccountInquiryExportProvider` (`screenId = banking-account-inquiry-vnd`) | `BANK`, `ACCOUNT_NICKNAME`, `BALANCE`, `AVAILABLE_BALANCE`, `VAS_ACCOUNT`, `LAST_UPDATE` | Backend (count + total balance + total available, computed from `AccountListResponse`) |
| `POST /api/v1/settings/firm-banking-history/export` | `FirmBankingHistoryExportProvider` (`screenId = firm-banking-history`) | `REG_DTM`, `SVC_TYPE`, `SVC_NM`, `SEQ_NO`, `DEP_BANK_CD`, `RS_CD`, `RS_MSG` | Backend (`<count> record(s)` — no aggregable amount; unified view carries audit metadata only). Reuses `FirmBankingHistoryService.listAll`. Refactor-only addition (no legacy JEX counterpart). |
| `POST /api/v1/settings/erp-transaction-history/export` | `ErpTrnsHistoryExportProvider` (`screenId = erp-transaction-history`) | `REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `ORG_FILE_NM`, `FILE_SIZE`, `REG_USER`, `REG_ID` | Backend (`<count> record(s)`). Reuses `ErpTrnsHistoryService.listAll`. Refactor-only addition (no legacy JEX counterpart). |

> **As of 2026-06-23 ~20 providers are registered** — the three rows above are illustrative, not the full list (that table was stale). `ExportableScreenRegistry` auto-collects every `@Component` implementing `ExportRowProvider` (26 provider files on disk; ~4 abstract bases unregistered). Additional wired screenIds: `banking-account-inquiry-fcy`, `banking-transaction-history`, `daily-cash-report`, `cash-flow-report`, `addon-sales-einvoices`, `addon-expenses-einvoices`, `addon-operation-status`, `approval-box`, `card-transactions`, `vas-account`, `settings-contacts-local`, `settings-contacts-overseas`, and the 5 payment-result screens. The registry is the source of truth — see [docs/audits/common-source-index.md](docs/audits/common-source-index.md) §3.

Other list pages adopt the contract one-at-a-time in their next stabilisation pass — one `ExportRowProvider` class + one new `@PostMapping` per migration. The common primitives in `common/export/` do not change.

## PROD DB Table Scope for Refactoring

> **Superseded as the canonical baseline (2026-06-11).** The **single authoritative table baseline** for the refactor is now [docs/references/database/legacy-table-inventory.md](docs/references/database/legacy-table-inventory.md) (141 tables) — see root [CLAUDE.md](../CLAUDE.md) §"Refactor DB & Legacy-Compatibility Rules → 1. Legacy table compatibility". This section is **retained for its derivation method, exclude rules, and the per-table Data Migration Rule**, not as the table list of record. **When this list and the inventory disagree, the inventory wins.** Do not treat the list below as the sole scope authority any longer; validate every table decision against the inventory first.

This section establishes how the original PROD table scope was **derived**. WeCMS was originally cloned from WABOOKS (source code + database), so the live PROD DB still contains many tables that are NOT actually used by WeCMS — legacy WABOOKS tables, backup snapshots, migration artifacts, and JEX framework internals. Legacy JEX code routinely references tables that PROD does not actually populate.

The scope below was derived by running this query **directly on PROD** and manually filtering the result:

```sql
SELECT
    schemaname,
    relname AS table_name,
    n_live_tup AS row_count
FROM pg_stat_user_tables
WHERE n_live_tup > 0
ORDER BY n_live_tup DESC;
```

### Source of truth

1. The **single source of truth** for the table baseline is [docs/references/database/legacy-table-inventory.md](docs/references/database/legacy-table-inventory.md). The PROD table list below documents how that baseline was originally derived from live PROD data — keep it for the exclude rules and the Data Migration Rule, but defer to the inventory when the two diverge.
2. Include only real tables that PROD populates with live business data.
3. **Do NOT rely on DEV DB** for scope decisions — DEV is sparse and includes test artifacts that do not reflect production usage. DEV remains the source of truth for *schema shape* (column-for-column parity per the rule above), but **NOT** for which tables exist in scope.
4. Always filter out non-business tables using the exclude rules below.

### Exclude rules (strict)

The following are excluded from the active list, regardless of what legacy JEX code references:

1. **JEX framework tables** — prefix `jex_*`
2. **Backup tables** — pattern `%_backup_YYYYMMDD`
3. **Generated / copy tables** — pattern `%_i[0-9]+`
4. **Any duplicated, test, or migration artifacts** that do not represent live business data

### Active table list (PROD-confirmed, FINAL)

Use ONLY these tables for refactoring. If a table is not in this list, do not create an entity, repository, or Flyway migration for it — even if legacy JEX code references it.

```
ACCT_AUTH_INFO
ACCT_MSTR
ACCT_TRNS_HSTR
ACCT_TRNS_RCV_PAY_INFM
API_TRAN_LOG_HIS
APPR_LINE_INFM
APPR_LINE_INFM_DETL
AUDIT_LOGS_INFM
BANK_CERT_INFO
BANK_EXCH_RATE_INFM
BANK_INFM
BANK_INFM_DETL
BASE_INFM
BLOCKED_CHARS_RULE
BLTB_COMNT_INFM
BLTB_NOTI_CONT_INFM
BLTB_NOTI_INFM
BLTB_POST_INFM
BLTB_POST_INFM_MDPH
BLTB_USER_NEW_POST_INFM
BUSA_DOC_DETL
BUSA_DOC_DETL_HIS
BUSA_DOC_MSTR
BUSA_DOC_MSTR_HIS
BUSA_DOC_RCV_PAY_HIS
BUSI_ADMN_ACTV_HIS
BUSI_ADMN_INFM
BUSI_ADMN_LOGIN_HIS
BUSI_ADMN_MDTR
BUSI_ENV_SET_INFM
BUSI_ENV_SET_MDTR
BUSI_INFM
BUSI_MATCH_LOG
BUSI_MDPH
BUSI_USAG_SET_INFM
BUSI_USAG_SET_INFM_LANG_DETL
BUSI_VAS_ACCT_INFM
CARD_MSTR
CARD_TRNS_HSTR
CARD_TRNS_RCV_PAY_INFM
CCY_INFM
CHART_ACCT_INFM
CHNL_INFM
CITAD_BANK_CD_LOOKUP
DAILY_CASH_REPT_DAILY_SUMR_DETL
DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR
DAILY_FINCL_REPT_DAILY_SUMR_DETL
EINV_FEE_DETL
EINV_FEE_DETL_HIS
EINV_TAX_DETL
EINV_TAX_DETL_HIS
EMPL_INFM
EMPL_MDPH
ERP_TRNS_HSTR
EXPT_RESOL_APPR_LINE
EXPT_RESOL_APPRVR_INFM
EXPT_RESOL_DETL
EXPT_RESOL_FILE
EXPT_RESOL_MSTR
EXPT_RESOL_PAY_EVID_FILE
EXT_TRAN_CALL_HIS
GDT_CERT_INFO
MENU_AUTH_ADMN_SYS
MSG_SND_HIS
NAPAS_BANK_INFO
NAPAS_BANK_REMIT_INFO
NAPAS_REMIT_TRNS_HSTR
NATN_INFM
NON_COLL_COND_DAILY_SUMR
NON_PAY_COND_DAILY_SUMR
NXSIAS_MODULE_TRAN_LOG_HIS
OPRT_STTS_INFO
OVRS_PYMN_MSTR
OVRS_PYMN_MSTR_HIS
OVRS_PYMN_SUMR
OVRS_PYMN_SUMR_DETL
OVRS_PYMN_SUMR_DETL_HIS
OVRS_PYMN_SUMR_HIS
PGRO_BSAC_LNKN_HIS
PGRO_BSAC_LNKN_INFM
PUSH_DEVICE_MSTR
PUSH_DEVICE_MSTR_HIS
RMRK_ORG_INFM
SCDUL_PYMN_SUMR
SCDUL_PYMN_SUMR_DETL
SCDUL_PYMN_SUMR_DETL_HIS
SCDUL_PYMN_SUMR_HIS
SYS_DOC_ID_GEN
SYS_DSHBRD_INFM
SYS_ENV_SET_INFM
SYS_SCR_OPTION_NEW
SYS_SCR_OPTION_WECMS
SYS_USAG_SET_INFM
TB_BATCH_SCHEDULE_DTL
TB_BATCH_SCHEDULE_INFO
TB_FIRM_BANK_CODE
TB_FIRM_BANK_CODE_CMS
TB_FIRM_BAT_API_TRAN
TB_FIRM_CONF
TB_FIRM_FILE_DATA
TB_FIRM_FILE_MST
TB_FIRM_FILE_RSLT
TB_FIRM_TRAN
TB_PAY_FILE_DATA
TB_PAY_FILE_DATA_HIS
TB_PAY_FILE_MST
TB_PAY_FILE_MST_HIS
TB_PAY_TARGET
TB_PAY_TARGET_HIS
TB_VAN_API_INFO
TRCO_ACCT_INFO
TRCO_ACTV_HSTR
TRCO_ADDR_INFM
TRCO_BUSI_INFM_SUMR
TRCO_INFO_FILE
TRCO_GRP_BSC_CSTM
TRCO_GRP_BSC_SYS
TRCO_INFM
TRCO_MDPH
TRCO_OVRS_INFM
USAG_INFM
USAG_INFM_LANG_DETL
USER_BLTB_CONN_INFM
USER_BUSI_CONN_INFM
USER_BUSI_CONN_INFM_HIS
USER_DSHBRD_INFM
USER_INFM
USER_LOGIN_HIS
USER_MDPH
USER_MENU_AUTH_INFM
USER_PW_CHG_HIS
USER_SCR_OPTION_NEW
USER_SCR_OPTION_WECMS
USER_TRCO_USE_HIS
USER_USAG_USE_HIS
```

### Data Migration Rule (MANDATORY)

This rule governs how data is copied from DEV → LOCAL for the active table set defined above. It is **non-negotiable** and applies to every Flyway seed migration in `src/main/resources/db/migration/`.

- **Only migrate data for** `biz_key = 'VN_VNM0004_000001'`.
- **Limit data volume** → **Maximum 30 rows per refactored active table.** When a table has more than 30 rows in DEV, take the most relevant / latest rows (typically by `REG_DTM DESC` or the table's natural ordering); when it has fewer, take all of them.
- **Do NOT migrate:**
  - Full table data
  - Other `biz_key` data (any `BIZ_KEY` value other than `VN_VNM0004_000001`)
  - `jex_*` tables
  - `*_backup_*` tables
  - `*_iXXXXX` tables
  - Tables not in the **PROD Active Table List** above
- **Maintain integrity:**
  - Insert parent tables first
  - Respect foreign-key relationships
  - Avoid orphan data (no child row whose parent key is absent locally)
- **Purpose:**
  - Keep the local DB lightweight
  - Ensure a consistent dataset across all developer machines
  - Avoid unnecessary data migration
  - Focus on refactored feature scope

Seed migrations MUST be idempotent (`INSERT … ON CONFLICT DO NOTHING`) and MUST be ordered so parents land before children in FK chains (e.g. `BUSI_INFM` → `USER_INFM` → `USER_BUSI_CONN_INFM` → `USER_LOGIN_HIS`).

### Refactoring enforcement rule (very important)

**Refactoring Enforcement Rule:** if legacy JEX uses multiple tables → **ONLY refactor tables that exist in the PROD Active Table List**. No exceptions.

When porting a legacy JEX endpoint:

- Read the legacy `_act.jsp`, IDO XML, WSVC XML, DAO, and entity to understand the behaviour.
- Cross-check **every** referenced table against the active list above.
- If legacy uses 5 tables but only 3 of them appear in the active list → **refactor ONLY those 3 tables**.

**Do NOT:**
- Blindly follow legacy structure when it touches out-of-scope tables.
- Create JPA entities, repositories, or Flyway migrations for tables not in the active list.
- Migrate unused WABOOKS tables just because legacy code mentions them.

If a legacy code path is genuinely impossible without an excluded table (e.g. a join target that PROD truly does populate via a path the audit missed), raise the discrepancy with the lead **before** widening this list — do not silently expand the scope.

This rule sits **above** the "Legacy Schema Parity Rule" (column-for-column DEV mirroring): parity governs *how* a table is migrated; this rule governs *whether* a table is migrated at all.

### Recommended PROD audit query

To re-derive this list (or refresh it when the scope is reviewed), run on PROD:

```sql
SELECT
    schemaname,
    relname AS table_name,
    n_live_tup AS row_count
FROM pg_stat_user_tables
WHERE n_live_tup > 0
ORDER BY n_live_tup DESC;
```

Then manually filter out:
- `jex_*`
- `%_backup_YYYYMMDD`
- `%_i[0-9]+`
- duplicated / test / migration artifacts

Update this section (and only this section) when the filtered list changes. Never refresh from DEV.

**Scope override 2026-05-13 — `TRCO_INFO_FILE` added.** The contacts Add / Edit popup attaches files (legacy `comm_0184_002_c001` + `comm_0184_005_*`). Earlier audit excluded the table because PROD usage was unverified; the PO call on 2026-05-13 confirmed the refactor must support attachments end-to-end. Active-list rule "raise the discrepancy before widening this list" honoured — the lead authorised the widening explicitly, and **V139 + V140** ship the schema + key generator (`TRCO_INFO_FILE` table + the `trco_info_file.file_id` `wb_get_next_val` branch; both files' internal headers are mislabeled V98/V99). Refactor folder slug is `contacts` (not the legacy `trco_0004_001_u002` WSVC id) per the identifier-naming rule.

## Application Profiles

| File | Purpose |
|---|---|
| `application.yml` | Base — JPA/Flyway wiring, messages basename, server port 8080, `wecms.jwt.*`, `wecms.cors.*`, `wecms.tenant.biz-key=VN_VNM0004_000001`. Default active profile `local`. |
| `application-local.yml` | Developer laptop — DB URL + password default, JWT dev secret default, verbose logging. |
| `application-dev.yml` | Reserved. Not used in Sprint 0. |
| `application-prod.yml` | Reserved. Not used in Sprint 0. |
| `application-test.yml` | Integration-test profile. |

## Build & Run

```bash
./gradlew build                                              # compile + unit tests
./gradlew bootRun --args='--spring.profiles.active=local'    # runs against the shared dev DB in application-local.yml (172.168.5.172)
./gradlew test                                               # tests only

./run-local.bat    # Windows wrapper — sets env vars + bootRun
./run-local.sh     # POSIX equivalent
```

Local seed users are provisioned via SQL (Flyway is disabled — `ddl-auto: validate` only). The startup `DataSeeder` was **removed 2026-06-10** along with the rest of the bootstrap package; the `__BCRYPT_PENDING__` sentinel pattern is retired. Seed passwords are written as the legacy `public.hash()` SHA-256 value directly.

Test users (when seeded):
- `testuser` / `Password!1` — active, unlocked
- `lockeduser` / `Password!1` — `LOCK_YN=Y` → returns `WBE1011`
- `legacyuser` / `Password!1` — stored as legacy SHA-256 base64 hash (V10). Login is **verify-only** — the hash is NOT upgraded (coexistence rule); SHA-256 stays in place.

## Do / Don't

**Do**
- Keep DTO Java field names in camelCase; the global Jackson SNAKE_CASE strategy converts to lowercase snake_case on the wire ([JacksonConfig](src/main/java/com/kosign/wecms/config/JacksonConfig.java)). Add `@JsonProperty` only when the wire name diverges from snake-cased Java (e.g. `accessToken` → `access_token`).
- Throw `BusinessException("WBExxxx")` with a matching `messages.properties` entry for every known error path.
- Add a Flyway migration for every schema change; ensure the JPA entity matches (`ddl-auto: validate` will fail startup otherwise).
- Constrain every tenant-scoped query on `BIZ_KEY = <tenantProperties.bizKey>` — never a literal string, never a wildcard.
- Put Next.js-visible shape changes into matching DTO/types on both sides in the same MR (full-stack ownership rule, root CLAUDE.md).
- Use `TenantContext` to read the current user's identity inside service methods — do not re-parse the JWT or re-query the user for every call.
- Use `LangCd` enum for all Accept-Language parsing — do not re-implement string matching.

**Don't**
- Don't hardcode passwords, JWT secrets, or DB credentials. Everything sensitive reads from env / `application-local.yml`.
- Don't hand-roll an alternative response shape — always go through `ApiResponse<T>` + `CommonHead`.
- Don't add business logic in entities, controllers, or filters. It belongs in `service/`.
- Don't use `ddl-auto: update` or `create`. Flyway is the only sanctioned schema channel.
- Don't widen the tenant filter to `LIKE 'VN_VNM0004_%'` in code — the property regex rejects wildcards for a reason.
- Don't add SpringDoc / Testcontainers / Redis / any other dependency "just in case". Add it when the first feature needs it.
- Don't cross the write boundary: JEX trees are read-only references.

## Known Issues / Inconsistencies

| Issue | Location | Status |
|---|---|---|
| In-memory revocation / rate-limit durability | `TokenRevocationService`, `RateLimiter` | **Open (by design for single-instance).** Both stores are in-memory — revocations and rate-limit counters are lost on restart and not shared across instances. Bounded (≤ access-token TTL after restart); a shared cache / PO-approved table is required before a multi-instance deploy. |
| 5-failure auto-lock | `LoginLockoutService`, `AuthService`, `USER_LOGIN_HIS` | **Implemented (F-10).** After 5 consecutive FAIL rows, `LoginLockoutService` sets `USER_INFM.LOCK_YN='Y'` (own `REQUIRES_NEW` tx), wired in `AuthService.login`. |
| Server-side logout / revocation | `AuthService.logout()`, `TokenRevocationService`, `JwtAuthenticationFilter` | **Implemented (F-03).** Logout revokes the token's `jti`; admin lock/delete/demote/password-reset revokes all the user's tokens; the filter rejects revoked/superseded tokens. (Durability caveat tracked in the row above.) |
| PW_CHG_REQ_SEQ_NO unused | `UserInfm.pwChgReqSeqNo` | Mapped but not read; password-expiry flow deferred |
| NTNL_CD/CHNL_TYPE/BUSI_STTS gates missing | `AuthService.login` | Post-auth gates deferred; not in Sprint 0 scope |
| No SpringDoc / Swagger | — | Deferred; add with first non-auth module |
| No Testcontainers | `AuthServiceTest` | Tests use Mockito; H2/in-memory deferred until second domain |
| `SYS_SCR_OPTION_WECMS.SIZE` type drift from DEV | [V30__sprint1_screen_option_tables.sql:25](src/main/resources/db/migration/V30__sprint1_screen_option_tables.sql#L25) | DEV is `VARCHAR` (e.g. `"250px"`); local is `INTEGER`. Deferred — bundle with the View Setting SIZE end-to-end feature (entity field + DTO + frontend `<col width>` consumption + Figma-aligned px backfill) so the type fix lands with the first real consumer. |

## Notes for future implementation

- **SpringDoc OpenAPI** — first non-auth module should add `org.springdoc:springdoc-openapi-starter-webmvc-ui` and annotate controllers with `@Operation`.
- **Logout / token revocation** — **implemented (F-03).** `AuthService.logout` revokes by `jti`; admin lock/delete/demote/password-reset revokes all of a user's tokens; `JwtAuthenticationFilter` enforces it. **Still open:** refresh tokens (not implemented), and a **durable / multi-instance** revocation store (the current `TokenRevocationService` is in-memory — lost on restart, per-node; a shared cache or a PO-approved table is the next step, see `docs/security/jwt-revocation.md`).
- **Auto-lock counter** — **implemented (F-10).** `LoginLockoutService.lockIfThresholdReached` sets `USER_INFM.LOCK_YN='Y'` after the latest 5 attempts are all failures (own `REQUIRES_NEW` tx); wired in `AuthService.login`.
- **Password expiry** — `USER_INFM.PW_CHG_REQ_SEQ_NO` is mapped but unused. Wire to a `/auth/change-password` flow before non-local deploy.
- **Menu / permission resolution** — **complete (Phases 1–5 + F-3, 2026-06-06).** `domain/navigation` (`GET /api/v1/navigation`; live catalogue migrations **V137–V139** — the pre-renumber `V242–V244` are archived; the catalogue **seed** is **not** in the renumbered live set, so a freshly-migrated DB may have an empty `MENU_ITEM` — verify per [docs/TROUBLESHOOTING.md](../docs/TROUBLESHOOTING.md) §4) serves the per-product menu tree, server-resolved to `Product Tree ∩ grants` (admin = full tree). All four `wecms-web` nav surfaces (Sidebar, Top Nav, mobile MENU, Menu Entitlement) are wired to the endpoint (hardcoded arrays removed); the Menu Entitlement universe + per-user grant write-path are implemented (`USER_MENU_AUTH_INFM`, generated via `entitlementTree.ts`); and server-side per-menu `@PreAuthorize` enforcement is live (F-3). See the `domain/navigation` row in the Feature Status table + [endpoint-authorization-matrix.md](../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).
- **Rate-limiting / brute-force protection** — **implemented (F-10).** In-memory fixed-window `RateLimiter` guards `POST /auth/login` (`login:<ip>`) and credential reveal. **Still open:** a **durable / distributed** limiter (the current one is in-memory — per-node, resets on restart) for any multi-instance deploy.
- **NTNL_CD / CHNL_TYPE / BUSI_STTS post-auth gates** — legacy JEX checks additional business flags after password validation; implement these in `AuthService.login` before production use.
- **View Setting SIZE feature** — when (and only when) admin-tunable column widths or drag-to-resize becomes a real product requirement, ship one migration + cross-stack change: `ALTER COLUMN "SIZE" TYPE VARCHAR(20)` to align with DEV, map the field on `SysScrOptionWecms` / `UserScrOptionWecms`, add `@JsonProperty("SIZE")` to `ScreenOptionItem`, surface `SIZE` in the TS `ApiScreenOptionItem`, consume it in the frontend `<col width>` rendering, and backfill the seed with **Figma WB365** values (NOT legacy DEV values — the two diverge in 5/7 columns). Until then SIZE is intentionally schema-only metadata; the layout source of truth is Figma → Tailwind tokens.
