---
slug: transfer-history
jex_wsvc_id: trns_0003_001_r001  # citation only — never used as a refactor runtime identifier
owner: Sonny (Ran Sokny)
status: planning
last_updated: 2026-05-20
---

# Transfer History — Implementation Plan

Step-by-step plan for porting **Banking > Internal Transfer > Transfer History**
to wecms-api (Spring Boot 3.5.13) + wecms-web (Next.js 16) under the refactor
R&D scope. Companion to
[docs/refactor-wecms/features/banking/transfer-history.md](../../../features/banking/own-account-transfer/transfer-history.md)
(spec) and
[docs/refactor-wecms/migration/transfer-history.md](../../../references/migration/transfer-history.md)
(mapping).

Read-only scope — this is a list endpoint over an already-ported table.

---

## 0. Pre-conditions (must be true before starting Task BE-1)

- [ ] Open questions §10 OQ-1, OQ-2, OQ-3, OQ-4, OQ-5, OQ-6, OQ-7, OQ-8 in the
      feature spec are resolved with Mr. Jung (PO). At minimum **OQ-1**
      (`in_bank_nm` resolution) and **OQ-6** (page-size whitelist) must be
      resolved before any code lands.
- [ ] Local DB has `NAPAS_REMIT_TRNS_HSTR` (it should — V13 / V18 / V19 / V23
      / V24 / V28 / V29 already shipped). Confirm with `./gradlew flywayInfo`
      and a `\d napas_remit_trns_hstr` showing 21 columns.
- [ ] Local DB has at least ~25 seed rows for tenant `VN_VNM0004_000001` (per
      the Sprint 1 import precedent). If empty, add a seed migration as Task
      BE-2.
- [ ] `JAVA_HOME` set to JDK 21 (per memory
      `project_local_java_toolchain` (Needs Confirmation)).
- [ ] VPN reachable to shared dev DB (per memory
      `reference_local_db_setup` (Needs Confirmation)).

---

## 1. Task list

Task IDs follow the convention used in the prior planning docs (`BE-N` for
backend, `FE-N` for frontend, `E2E-N` for integration). Estimates are in
**developer-days**; the team baseline is 8 dev days + 2 test days per memory
`project_team_roster` (Needs Confirmation).

### Phase A — Backend (sequential)

| Task | Description | Reuses (do NOT recreate) | Est. | Depends on |
|---|---|---|---|---|
| **BE-1** | Generate `NapasRemitTrnsHstr` JPA entity + `NapasRemitTrnsHstrId` composite key in `domain/banking/transferhistory/entity/`. 21 columns mapped 1:1, `@Table(name = "NAPAS_REMIT_TRNS_HSTR")`, extends `BaseEntity`, `@JdbcTypeCode(SqlTypes.CHAR)` on `TRANS_TYPE` / `CCY_CD` / `FEE_DFR_SBJ` / `TRNS_STLM_YN` (CHAR columns, same precedent as V38 `sort_yn`). | `BaseEntity` (audit columns), `LangCd` enum, project Lombok config | 0.25 d | OQ-1 resolved |
| **BE-2** | (Optional) Add Flyway seed migration `V{N}__transfer_history_seed.sql` importing ~25 most-recent rows from DEV via `INSERT … ON CONFLICT DO NOTHING`. Skip if local DB already has rows from V22 / V29. | Idempotent migration pattern from V22 / V72 / V87 | 0.25 d | BE-1; access to DEV via `postgres_dev_server_jexframe` MCP (read-only) |
| **BE-3** | `NapasRemitTrnsHstrRepository extends JpaRepository<NapasRemitTrnsHstr, NapasRemitTrnsHstrId>` — `findAll(Specification, Pageable)` + one `@Query` for the totals projection (mirrors `NAPAS_REMIT_TRNS_HSTR_R003_CNT`). Tenant-scoped — `BIZ_KEY` constraint per [wecms-api § Repositories](../../../../CLAUDE.md). | Spring Data JPA, existing `TenantContext.currentBizKey()` | 0.5 d | BE-1 |
| **BE-4** | DTOs as Java **records** under `domain/banking/transferhistory/dto/`: `TransferHistoryListRequest` (validated query params), `TransferHistoryRow`, `TransferHistoryTotals`, `TransferHistoryListResponse`. **Field names camelCase; no `@JsonProperty` unless wire diverges from snake-cased field.** | Global `JacksonConfig` SNAKE_CASE strategy, existing `@Pattern` / `@Min` validators | 0.5 d | — |
| **BE-5** | `TransferHistorySortField` enum (one constant per allowed `data-sort` value: `TRNS_DT`, `OUT_BANK_NM`, `IN_BANK_NM`, `TRSC_AMT`, `RMRK`) per the "Common table sort" rule + project-wide whitelist precedent. | `ContactSortField` / `ApprovalLineSortField` precedent | 0.25 d | BE-4 |
| **BE-6** | `TransferHistoryService` — orchestrate validation, BIZ_KEY scoping via `TenantContext`, batched bank-name + user-name resolution, period-cap guard, sort whitelist, paged repository call + totals call, projection to `TransferHistoryRow`. Per OQ-1: `BankCdResolver` for `out_bank_nm`; `LegacyFunctionRepository.resolveNapasOrCitadBankName` (V86) for `in_bank_nm` unless PO overrides. | `BankCdResolver`, `LegacyFunctionRepository`, `TenantContext`, `UserInfmRepository` | 1.0 d | BE-3, BE-4, BE-5 |
| **BE-7** | `TransferHistoryController` — single `@GetMapping("/api/v1/banking/internal-transfer/transfer-history")`. Thin controller (parse → service → wrap in `ApiResponse.ok`). | `ApiResponse`, `GlobalExceptionHandler` | 0.25 d | BE-6 |
| **BE-8** | i18n + error codes in `messages.properties`: `WBE_TRANSFER_HISTORY_INVALID_PERIOD`, `WBE_TRANSFER_HISTORY_PERIOD_TOO_LONG` (if OQ-2 specifies a cap). | `MessageResolver`, existing message bundle | 0.1 d | BE-6 |
| **BE-9** | Unit + slice tests — `TransferHistoryServiceTest` (sort whitelist, period guard, BIZ_KEY scoping), `TransferHistoryControllerTest` (`@WebMvcTest`, validation cases, success envelope, error envelope, 401 path). | JUnit 5, `spring-security-test`, existing test scaffolding | 1.0 d | BE-7 |
| **BE-10** | (Optional, per OQ-3) Helper index migration `V{N}__transfer_history_idx.sql` — `CREATE INDEX IF NOT EXISTS ix_napas_remit_trns_hstr_biz_regdtm ON napas_remit_trns_hstr (biz_key, substr(reg_dtm, 1, 8) DESC, seq_no DESC)`. | Idempotent migration pattern | 0.1 d | BE-3 (baseline measurement) |

**Phase A subtotal:** ~4.0 dev days (with BE-2 + BE-10 deferred until needed).

### Phase B — Frontend (depends on the backend slice landing)

| Task | Description | Reuses | Est. | Depends on |
|---|---|---|---|---|
| **FE-1** | Flip `ApiTransferRow` / `ApiTransferHistoryResponse` in [`wecms-web/lib/types/api.ts`](../../../../../wecms-web/lib/types/api.ts) from UPPER_SNAKE holdover to lowercase snake_case wire shape (per [wecms-web § Holdovers](../../../../../wecms-web/CLAUDE.md)). | Existing `ApiResponse<T>` envelope | 0.25 d | Phase A complete |
| **FE-2** | Add wire-to-in-memory mapper `toTransferHistoryRow(...)` + `toTransferHistoryResponse(...)` in [`lib/api/mappers.ts`](../../../../../wecms-web/lib/api/mappers.ts). In-memory shape camelCase semantic — `TransferHistoryRow` interface declared in `components/banking/internal-transfer/types.ts`. | Existing mapper file | 0.25 d | FE-1 |
| **FE-3** | Hook `useTransferHistory({ fromDt, toDt, status, sortField, sortDirection, pageNo, pageSize })` in `lib/banking/useTransferHistory.ts` — Axios call via the existing `api` client, returns `{ rows, totals, isLoading, error }`. Error path uses `useDialog({ type: "error" })` per [wecms-web § Dialog variant](../../../../../wecms-web/CLAUDE.md). | Existing `api` (lib/axios.ts), `useDialog()`, `lib/i18n` | 0.5 d | FE-2 |
| **FE-4** | `TransferHistoryPage.tsx` composing existing `<PageHeader>` + `<DateRangePicker>` + status radios + `<TransferHistoryTable>` + `<TransferHistoryTableColumns>` + `<Pagination>` + footer row. **No new abstractions.** | Shared shell, `<StatusBadge>`, `<PageHeader>`, `<PageActionButtons>`, `<SearchInput>` (if PO wants keyword), `<Pagination>`, `<DateRangePicker>` (scaffold exists, no real picker yet — uses the same shell as other banking pages) | 1.0 d | FE-3 |
| **FE-5** | i18n entries in [`locales/en.json`](../../../../../wecms-web/locales/en.json) / `ko.json` / `vi.json` under `banking.transfer_history.*` (15 keys per spec §9.1). | Existing in-house i18n provider | 0.25 d | FE-4 |
| **FE-6** | Route: `app/(dashboard)/banking/internal-transfer/transfer-history/page.tsx` (thin server component, sets `metadata`, renders `<TransferHistoryPage />`). | `<AppShell>` via `(dashboard)/layout.tsx`, `HOME_ROUTE` constant in `lib/routes.ts` if a redirect is needed | 0.1 d | FE-4 |
| **FE-7** | Sidebar entry — flip the existing placeholder `internal-transfer/transfer-history` link in [`Sidebar.tsx`](../../../../../wecms-web/components/layout/Sidebar.tsx) from `href=undefined` to a real route. | Existing sidebar config | 0.1 d | FE-6 |
| **FE-8** | Tests (Vitest + React Testing Library, when the project test config supports them — otherwise smoke test only): mapper round-trip, hook happy path, error dialog opens on 500. | Existing test setup if present | 0.5 d | FE-3 |

**Phase B subtotal:** ~3.0 dev days.

### Phase C — End-to-end + sign-off

| Task | Description | Est. | Depends on |
|---|---|---|---|
| **E2E-1** | Manual smoke run by Mr. Youvita (AI-assisted tester): empty range, success-only filter, failed-only filter, single-day range, paging across ≥ 2 pages, status badge colours, error dialog on bad token. Compare side-by-side with live JEX `https://dev.wecms.vn/trns_0003_001.act` for parity. | 1.0 test day | Phase A + B complete |
| **E2E-2** | Verify wire shape via `curl` — `GET /api/v1/banking/internal-transfer/transfer-history?from_dt=YYYYMMDD&to_dt=YYYYMMDD` returns the snake_case envelope; trace_id grepable in logs. | 0.25 test day | Phase A complete |
| **E2E-3** | Backend MR + frontend MR open as a paired set on GitLab (workspace full-stack ownership rule). Reviewer checklist: no UPPER_SNAKE `@JsonProperty` left; sort whitelist enforced; no JEX trees touched; no `coocon.exe` / `ProcessBuilder` reference anywhere in the Java side. | 0.25 d | Phase B complete |
| **E2E-4** | Update [docs/refactor-wecms/features/banking/transfer-history.md](../../../features/banking/own-account-transfer/transfer-history.md) `status:` frontmatter to `working`; update [wecms-api/CLAUDE.md § Feature Status](../../../../CLAUDE.md) and [wecms-web/CLAUDE.md § Feature Status](../../../../../wecms-web/CLAUDE.md) with one row each. | 0.25 d | E2E-1 passes |

**Phase C subtotal:** ~1.75 days (≈1.25 dev + 1 test).

---

## 2. Total effort

| Phase | Dev | Test |
|---|---|---|
| A — Backend | ~4.0 d | — |
| B — Frontend | ~3.0 d | — |
| C — E2E + sign-off | ~0.75 d | ~1.25 d |
| **Total** | **~7.75 dev days** | **~1.25 test days** |

Fits inside the standard 8 dev + 2 test envelope per memory
`project_team_roster` (Needs Confirmation),
with margin.

---

## 3. Sequencing rule (do NOT parallelise)

Per [wecms-api § Scope Guard](../../../../CLAUDE.md), this is a backend
+ frontend joint MR pair. Sequence:

```
OQ resolved → Phase A (BE-1 … BE-9) → Phase B (FE-1 … FE-8) → Phase C
                                ↑
                       FE-1 cannot start until the
                       Spring DTO wire shape is final
```

The static-UI scaffolding (`TransferHistoryTable.tsx`,
`TransferHistoryTableColumns.tsx`) already exists with mock data — but per the
[wecms-web § Holdovers](../../../../../wecms-web/CLAUDE.md) rule, **do not
pre-migrate** the wire types or mappers until the backend lands.

---

## 4. Reusable pieces — confirmed inventory

The plan introduces **no new abstractions**. Every cross-feature helper below
already exists; using anything else needs a justification in the MR.

**Backend (`wecms-api/`):**

- `BaseEntity` (audit columns)
- `ApiResponse<T>` + `CommonHead` (envelope)
- `BusinessException` + `GlobalExceptionHandler` + `MessageResolver`
- `TenantContext.currentBizKey()` / `currentUserId()` / `currentLangCd()`
- `JwtAuthenticationFilter` + `JwtAuthenticationEntryPoint`
- `BankCdResolver` (BANK_INFM lookup)
- `LegacyFunctionRepository.resolveNapasOrCitadBankName(code, langCd)` (V86)
- `UserInfmRepository` (for `WB_GET_USER_NM` replacement)
- Sort-whitelist enum precedent (`ContactSortField`, `ApprovalLineSortField`,
  `ChartOfAccountsSortField`)
- Bean Validation, `LangCd`, global SNAKE_CASE Jackson strategy
- `TraceIdFilter` + MDC `traceId`

**Frontend (`wecms-web/`):**

- `<AppShell>`, `<Header>`, `<Sidebar>`, `<SidebarMenuItem>`, `<ProfileMenu>`
- `<PageHeader>`, `<PageActionButtons>`
- `<DateRangePicker>` (UI-only scaffold — same as Account Inquiry today)
- `<SearchInput>` (only if PO wants keyword filtering — feature spec §10 OQ-?)
- `<Pagination>`
- `<TransferHistoryTable>` + `<TransferHistoryTableColumns>` (already
  scaffolded with mock data)
- `<StatusBadge>` (`wb-status-*` tokens)
- `useDialog()` (`type: "error"` for API/network failures per the
  wecms-web rule)
- `api` Axios instance + JWT interceptor
- `lib/api/mappers.ts` (extend, don't duplicate)
- In-house i18n (`useTranslation()`)
- Workspace typography tokens (`text-page-title`, `text-table-header`,
  `text-table-cell`, `text-base`, etc.)
- `cn(...)` from `lib/utils.ts`

**Do NOT introduce:**

- A new "transfer-history-only" date picker, table primitive, status badge
  variant, dialog variant, mapper file, response envelope, or icon set.
- A second i18n library, second styling library, second HTTP client.
- A new `wb_get_next_val` branch (read-only — Sprint 1 audit explicitly
  excludes `napas_remit_trns_hstr.seq_no`).
- A `coocon.exe` invocation, `ProcessBuilder`, or any subprocess from Spring.

---

## 5. Definition of done

- [ ] `GET /api/v1/banking/internal-transfer/transfer-history` returns the
      snake_case envelope with the right rows + totals for the seeded tenant.
- [ ] Date validation, sort whitelist, page-size whitelist all enforced and
      tested.
- [ ] `in_bank_nm` + `out_bank_nm` + `user_nm` resolution path matches the
      OQ-1 decision (no N+1).
- [ ] Frontend renders the page, paging, status filter, sort, footer summary,
      error dialog — visually parity-checked against the live JEX page.
- [ ] No `@JsonProperty("UPPER_SNAKE")` introduced; no `COMMON_HEAD` / `REC`
      envelope re-introduced.
- [ ] No file written under `WECMS DEV/`, `SOURCE_CODE_ADMIN/`,
      `SOURCE_CODED_OSMC/`, or `DEV ADMIN/`.
- [ ] `./gradlew build test` green; `npm run lint && npm run type-check &&
      npm run build` green.
- [ ] Feature spec frontmatter updated to `status: working`.
- [ ] `CLAUDE.md` feature-status tables (api + web) updated with one row each.

---

## 6. Out of scope

- The Coocon iSAS-NX engine — its contract is in the feature spec §3 for
  context. Ported by the separate **Internal Transfer Register** MR. Spring
  never invokes the engine.
- `NAPAS_REMIT_TRNS_HSTR_C001` / `_U001` IDOs and `SeqHelper` Java equivalent
  — out of scope (Register / Settlement MRs).
- Excel export endpoint (OQ-7).
- Detail-row dialog or `GET /…/{seq_no}` endpoint.
- DEV / UAT / PROD deployment.
- Any change to the JEX trees.
