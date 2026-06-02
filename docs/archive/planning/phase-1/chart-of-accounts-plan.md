# Chart of Accounts — Refactor Implementation Plan

**Slug:** `chart-of-accounts`
**Team:** Sonny (lead), Arthur (dev), Mr. Youvita (AI-assisted tester), Mr. Jung (PO) — per memory `project_team_roster.md`.
**Budget:** 8 developer-days + 2 test-days (refactor sprint norm).
**Status:** Planning only — no code written, no Flyway applied.
**Related docs:** [feature spec](../../../features/settings/chart-of-accounts.md) · [migration map](../../../references/migration/chart-of-accounts.md) · [backend](../../../references/business-rules/chart-of-accounts.md) · [frontend](../../../../../wecms-web/docs/features/settings/chart-of-accounts.md) · [database](../../../references/database/chart-of-accounts.md).

---

## 1. Scope summary

**In scope (Phase 1 — this MR family):**

- Spring Boot backend: list + tree + create + update + soft-delete endpoints.
- Per-tenant `wb_get_next_val('chart_acct_infm','chart_acct_seq_no')` branch (V94).
- Flyway schema (V93) + restricted-root seed for all tenants (V95) + demo seed for `VN_VNM0004_000001` (V96).
- Next.js Settings panel wiring the existing static UI (`ChartOfAccountsPanel`) to the real endpoints.
- Audit-log write per CRUD op (default ON; see OQ-Audit in [backend doc §7](../../../references/business-rules/chart-of-accounts.md#7-audit-logging)).
- i18n keys in EN (+ KO from legacy dictionary if available).

**Phase 2 — follow-up MR (out of Phase 1 scope):**

- Excel bulk import (`POST /api/v1/settings/chart-of-accounts/bulk-import`) — Phase 1 keeps the dialog inert per OQ-5.
- `CHART_ACCT_SEQ_NO` VARCHAR → BIGINT migration (V97).
- VI / KO translation completion across all i18n keys.

**Out of scope (defer to later sprints):**

- `fnsh_0200_001` Income Statement / Trial Balance report integration — ports with Reports module.
- `ACCT_TRNS_HSTR.CHART_ACCT_CD` FK validation + Banking-side write path — ports with Banking transaction module.
- `CARD_TRNS_HSTR` integration — ports with Card module (not on roadmap).
- Upload-history audit ledger (`comm_0223_002_r001` / `_r002`) — ports with `ERP_TRNS_HSTR`-style audit feature.

---

## 2. Task breakdown + effort estimates

10 days total (8 dev + 2 test). Days are calendar-style estimates assuming Sonny + Arthur work in parallel where dependencies allow.

### Phase 1 — backend (4.5 dev-days)

| # | Task | Owner | Effort | Dependency |
|---|---|---|---|---|
| BE-1 | Create `domain/setting/chartofaccounts/` package skeleton — entity, ID class, repository, empty service + controller stubs | Arthur | 0.5 d | — |
| BE-2 | Flyway V93 — `CHART_ACCT_INFM` schema (15 columns, PK, UK, 2 helper indexes); verify Hibernate `validate` passes against real DEV-mirrored columns | Arthur | 0.5 d | BE-1 |
| BE-3 | Flyway V94 — `wb_get_next_val` branch + `LegacyFunctionRepository.nextChartAcctSeqNo(bizKey)` | Arthur | 0.25 d | BE-2 |
| BE-4 | Flyway V95 — restricted-roots seed (all tenants × {ACC-01/02/03}) + verify on local DB | Arthur | 0.25 d | BE-3 |
| BE-5 | Flyway V96 — demo seed (`VN_VNM0004_000001`, 26 real rows preserving DEV `seq_no`) | Arthur | 0.5 d | BE-4 |
| BE-6 | `ChartOfAccountsService#list` + `#tree` + `#getOne` (read paths) + DTOs + controller GETs; integrate with TenantContext; sort whitelist enum; tree builder (level / has_children / breadcrumb) | Sonny | 1.0 d | BE-2 |
| BE-7 | `ChartOfAccountsService#create` — dup-check, revive-on-deleted, `nextChartAcctSeqNo`, one-shot retry on `DataIntegrityViolationException` | Sonny | 0.5 d | BE-6 |
| BE-8 | `ChartOfAccountsService#update` — restricted guard, dup-on-rename, parent existence + cycle + depth-cap + self-parent guards; explicitly NOT porting the legacy u001:237-256 cleanup branch (OQ-3) | Sonny | 0.5 d | BE-7 |
| BE-9 | `ChartOfAccountsService#delete` — restricted guard, child re-parent (single `@Modifying @Query`), archive-rename on UK collision, soft delete; fold legacy `comm_0223_001_u002` into the same tx | Sonny | 0.5 d | BE-8 |
| BE-10 | i18n message keys — register `WBE_CHART_OF_ACCOUNTS_*` codes in `messages.properties` + `messages_en.properties` | Sonny | 0.25 d | parallel with BE-7..9 |
| BE-11 | `ChartOfAccountsAuditLogger` — `@Transactional(REQUIRES_NEW)` writes to `AUDIT_LOGS_INFM` with slug `chart-of-accounts`; reuse Settings precedent | Arthur | 0.25 d | BE-9 |
| BE-12 | Backend unit + integration tests — JUnit 5 against an embedded Postgres / Testcontainers (or `@SpringBootTest` against local DB if Testcontainers not yet in scope); cover dup-revive, restricted-code block, child re-parent cascade, cycle rejection, depth-cap rejection | Sonny + Arthur | 0.75 d (shared) | BE-9, BE-10 |

**Backend subtotal:** 4.5 dev-days.

### Phase 1 — frontend (3 dev-days)

| # | Task | Owner | Effort | Dependency |
|---|---|---|---|---|
| FE-1 | Add wire types `ApiChartOfAccountRow` / `ApiChartOfAccountListResponse` / `ApiChartOfAccountTreeResponse` to `lib/api/settings.ts` (or `lib/types/api.ts`) — lowercase snake_case | Arthur | 0.25 d | BE-6 (contract finalised) |
| FE-2 | Add fetchers `fetchChartOfAccounts*`, `createChartOfAccount`, `updateChartOfAccount`, `deleteChartOfAccount` in `lib/api/settings.ts` | Arthur | 0.25 d | FE-1 |
| FE-3 | Extend `lib/api/mappers.ts` with `toChartOfAccountRow(api)` (snake → camel) | Arthur | 0.25 d | FE-1 |
| FE-4 | Wire `<ChartOfAccountsPanel>` state — replace `mockData` with `useApiData(fetchChartOfAccounts, …)`; thread filter + sort state; bump `refreshKey` on every mutation | Sonny | 0.75 d | FE-2, FE-3, BE-6 |
| FE-5 | Wire `<ChartOfAccountsTable>` inline edit / create row to `createChartOfAccount` / `updateChartOfAccount`; success / error feedback through `useDialog()`; reset `editingSeqNo` on commit | Sonny | 0.5 d | FE-4, BE-7, BE-8 |
| FE-6 | Wire delete confirmation flow (`useDialog({ type: "confirmation" })` → `deleteChartOfAccount` → success dialog → `refreshKey++`) | Sonny | 0.25 d | FE-4, BE-9 |
| FE-7 | Parent picker — fetch via `fetchChartOfAccountsTree()`; client-side disabling of self / descendants / level-4 nodes; refresh tree on every mutation | Arthur | 0.5 d | FE-2, BE-6 |
| FE-8 | i18n — add the `settings.chart_of_accounts.*` keys to `en.json` + carry-over to `ko.json` from legacy ML dictionary; defer `vi.json` if no verified translator | Arthur | 0.25 d | parallel |
| FE-9 | Sort wiring — register `useTableSort()` cycle on `CHART_ACCT_CD`, `CHART_ACCT_NM_EN`, `STTS`, `MOD_DTM`; verify backend whitelist agrees | Sonny | 0.25 d | FE-4, BE-6 |
| FE-10 | Restricted-code rendering — disable Edit / Delete buttons + tooltip for rows where `restricted === true`; render `chart_acct_nm_en` for the canonical EN label per [§13a-bis](../../../../../wecms-web/CLAUDE.md) | Arthur | 0.25 d | FE-4 |

**Frontend subtotal:** 3.0 dev-days.

### Phase 1 — testing (2 test-days)

| # | Task | Owner | Effort |
|---|---|---|---|
| QA-1 | Backend unit-test coverage review — confirm all 7 service paths (list, tree, getOne, create, update, delete, retry) + 6 error codes are exercised | Sonny + Arthur | 0.25 d (shared) |
| QA-2 | Local end-to-end: tenant `VN_VNM0004_000001` — create root, create level-2 child, edit name, change parent (valid), change parent (cycle → expect error), change parent (depth>4 → expect error), delete root (children promoted), delete leaf (clean), try delete ACC-01 (expect error), try edit ACC-01 (expect error), search by code, search by EN name, sort each column | Mr. Youvita | 1.0 d |
| QA-3 | Local end-to-end: tenant `VN_VNM0004_000014` (HAENGSUNG) — verify V95 seeded the 3 restricted roots; create a couple of nodes; confirm tenant isolation by switching to tenant 000001 and confirming the new HAENGSUNG nodes don't appear | Mr. Youvita | 0.25 d |
| QA-4 | Concurrent-create race — open two browser tabs as different users in the same tenant, click "+ Add A New Account" with identical `CHART_ACCT_CD` simultaneously; expect one success + one `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` | Mr. Youvita | 0.25 d |
| QA-5 | Soft-delete + revive — delete code `X-1`, then Create new code `X-1`; confirm the original row was revived (same `seq_no`) and the new request did NOT insert a new row | Mr. Youvita | 0.25 d |

**Testing subtotal:** 2.0 test-days.

### Phase 1 budget summary

| Bucket | Days |
|---|---|
| Backend (Sonny + Arthur) | 4.5 |
| Frontend (Sonny + Arthur) | 3.0 |
| Testing (Mr. Youvita + dev support) | 2.0 |
| **Total** | **9.5 / 10 budget** |

Half a day of float against the 8 dev + 2 test budget. Tight; risks below could eat into it.

---

## 3. Critical files (what gets created / touched)

### Backend (`wecms-api/`)
- NEW `src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/controller/ChartOfAccountsController.java`
- NEW `src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/service/{ChartOfAccountsService,ChartOfAccountsTreeBuilder,ChartOfAccountsValidator,ChartOfAccountsAuditLogger}.java`
- NEW `src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/repository/ChartOfAccountRepository.java`
- NEW `src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/entity/{ChartOfAccount,ChartOfAccountId}.java`
- NEW `src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/dto/{ChartOfAccountRow,ChartOfAccountTreeNode,ChartOfAccountListResponse,ChartOfAccountTreeResponse,ChartOfAccountCreateRequest,ChartOfAccountUpdateRequest}.java`
- NEW `src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/enums/{ChartOfAccountsSortField,CalRule}.java`
- EDIT `src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java` — add `nextChartAcctSeqNo(bizKey)`
- EDIT `src/main/resources/messages.properties` + `messages_en.properties` — add `WBE_CHART_OF_ACCOUNTS_*` keys
- NEW `src/main/resources/db/migration/V93__settings_chart_of_accounts_schema.sql`
- NEW `src/main/resources/db/migration/V94__settings_chart_of_accounts_wb_get_next_val.sql`
- NEW `src/main/resources/db/migration/V95__settings_chart_of_accounts_seed_restricted_roots.sql`
- NEW `src/main/resources/db/migration/V96__settings_chart_of_accounts_demo_seed.sql`
- EDIT `CLAUDE.md` — append entries to the Flyway migration table (V93–V96), the `wb_get_next_val` "Surviving legacy functions" table (chart_acct_infm branch), and the Feature Status table.

### Frontend (`wecms-web/`)
- EDIT `lib/api/settings.ts` (NEW if not present) — add `fetchChartOfAccounts*`, `createChartOfAccount`, `updateChartOfAccount`, `deleteChartOfAccount` + wire types.
- EDIT `lib/api/mappers.ts` — add `toChartOfAccountRow`.
- EDIT `lib/viewSettingScreens.ts` — add `SCREEN_IDS.SETTINGS_CHART_OF_ACCOUNTS = "chart-of-accounts"` (reserved slug; popover NOT rendered).
- EDIT `components/settings/chart-of-accounts/ChartOfAccountsPanel.tsx` — replace `mockData` with `useApiData`; thread mutation handlers + dialog wiring.
- EDIT `components/settings/chart-of-accounts/types.ts` — finalise the camelCase in-memory type.
- (NO EDIT to `ExcelUploadDialog.tsx` — Phase 2.)
- EDIT `locales/en.json` (+ `ko.json` if dictionary entries available; defer `vi.json`).

### Docs
- Already created in this MR: features / migration / backend / frontend / database / planning (this file).

---

## 4. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-1 | OQ-3 (`u001` cleanup branch) turns out to be intentional business logic, not debug instrumentation | Low | Medium — re-design needed mid-sprint | Lock OQ-3 with Mr. Jung **before** BE-8 starts. If it's intentional, port verbatim + add regression test (+ 0.5d). |
| R-2 | Concurrent create race produces unfixable `DataIntegrityViolationException` even after one-shot retry under high contention | Very low (DEV traffic; refactor is R&D-local) | Low | Document the second-failure → `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` envelope; QA-4 verifies the user gets a clean error message rather than a 500. |
| R-3 | Per-tenant `wb_get_next_val` branch returns a value lower than V96 demo seed's max (`112` for tenant 000001) — would cause an immediate UK collision on the first refactor-created row | Low | High — write path broken on day-one | The branch is per-tenant `MAX+1`, so it picks up from `113` automatically. V96 seed deliberately preserves DEV `seq_no` (70..112). Verify in BE-5 by calling the branch from `psql` against the seeded local DB and confirming `>= 113`. |
| R-4 | The 4-level depth cap is computed differently in legacy R002 (read-time CASE) vs. refactor (write-time validator). A row that snuck past as legal depth in JEX shows up as `chart_acct_level=null` in the refactor list | Medium (legacy data is messy — DEV has self-referential rows!) | Low | List endpoint still surfaces these rows with `chart_acct_level=null`; frontend renders them under a "Deprecated depth" footnote. Existing data is grandfathered; only new edits trip the validator. |
| R-5 | `CHART_ACCT_TYPE` is unused in DEV but `NOT NULL` ambiguity could surface if a future seed sets it | Very low | Low | Confirmed `NULL`-able from DEV introspection; no defensive logic needed. |
| R-6 | `<ExcelUploadDialog>` wrapper at [`components/settings/chart-of-accounts/ExcelUploadDialog.tsx`](../../../../../wecms-web/components/settings/chart-of-accounts/ExcelUploadDialog.tsx) was already wired against static mockData; the panel might still pass mock rows | Low | Low (UI-only) | FE-4 verifies the wrapper's `rows` prop is the mocked-template state (Phase-1 inert), not derived from the live list. The wrapper stays unchanged — Phase 2 wires `onUpload`. |
| R-7 | Audit-log volume — every CRUD writes an `AUDIT_LOGS_INFM` row. Chart of Accounts is low-traffic so this is fine, but cross-feature audit-log growth is a known watch-point | Very low | Very low | Confirmed in BE-11. Reuse the existing Settings audit precedent (firm-banking-settings / contacts already write); no schema impact. |
| R-8 | Java local toolchain — Sonny's `JAVA_HOME` is Java 11 per memory `project_local_java_toolchain.md`; Gradle resolution fails until manually set to JDK 21 | Certain | Medium (delays bring-up) | First task on day 1: Sonny sets `JAVA_HOME` to JDK 21 in the IDE + terminal. ~15 min, but document the workaround in the kickoff Slack so Arthur doesn't repeat it. |
| R-9 | Local DB cut-over to shared dev server `172.168.5.172:5432/wecmsdevdb` (per memory `reference_local_db_setup.md`) — VPN required; password lives in `.gitignored` files | Certain | Medium | Confirm VPN access for both Sonny + Arthur on day 1. Confirm both have an up-to-date `.gitignored` password file. Confirm Flyway can write a new migration to that shared DB without conflicting with other refactor work in flight. |
| R-10 | Mr. Jung returns OQ-1 with "USER can write too" (matching legacy), invalidating the `@PreAuthorize` plan | Low | Very low | Default plan ships WITHOUT the annotation. If OQ-1 lands ADMIN-only, add one annotation per write endpoint in a 15-min follow-up. |

---

## 5. Open questions & decisions needed from PO (Mr. Jung)

Reproduced here from the [feature spec §7](../../../features/settings/chart-of-accounts.md#7-open-questions-for-mr-jung-po) — every question MUST be resolved before the named blocking task starts.

| # | Question | Blocking task | Recommended default | Due |
|---|---|---|---|---|
| OQ-1 | USER role: read-only or full CRUD? | BE-12 (write endpoint role gates) | Read-only (consistent with VAS Account / Purpose) | Before day 5 (BE-12 starts) |
| OQ-2 | Add cycle / depth / self-parent guards to Update — confirm? | BE-8 | YES — legacy holes are bugs | Before day 4 (BE-8 starts) |
| OQ-3 | Drop the legacy `u001:237-256` parent-cleanup branch — confirm? | BE-8 | YES — drop it | Before day 4 (BE-8 starts) — **highest-priority OQ; risk R-1** |
| OQ-4 | Restricted roots seeded at Flyway-time (per tenant), not on-demand — confirm? | BE-4 (V95) | YES — V95 seeds all tenants | Before day 2 (BE-4 starts) |
| OQ-5 | Excel bulk upload — Phase 2 (Phase 1 inert)? | FE-(none — Phase 1 skips) | YES — defer | Before day 1 (so we plan the right amount of frontend work) |
| OQ-6 | View Setting popover — adopt for this panel, or skip? | FE-(none — Phase 1 skips popover) | SKIP — match Approval Line / Contacts precedent | Before day 1 |
| OQ-7 | Sort whitelist confirmed (`CHART_ACCT_CD`, `CHART_ACCT_NM_EN`, `STTS`, `MOD_DTM`)? | FE-9 (frontend sort wiring) | YES — defaults to `CHART_ACCT_CD ASC` | Before day 7 (FE-9 starts) |
| OQ-Audit | Write audit-log rows for every CRUD (new vs. legacy which writes none)? | BE-11 | YES — match firm-banking-settings / contacts | Before day 4 (BE-11 starts) |

Lock OQ-3, OQ-4, OQ-5, OQ-6 at kickoff (day 1). The remaining OQs can resolve as the matching task approaches.

---

## 6. Definition of done (Phase 1)

- [ ] All Phase 1 backend + frontend tasks complete; CI green.
- [ ] Hibernate `validate` passes against the new schema (no entity / column drift).
- [ ] All 5 OQs (1, 2, 3, 4, Audit) have a recorded decision in this doc (or in the feature spec OQ table); behaviour matches the decision.
- [ ] QA-1..5 verified on local DB (both seeded tenants); Mr. Youvita signs off.
- [ ] No new `@JsonProperty("UPPER_SNAKE")` on any new DTO field.
- [ ] No new shared frontend component added — the panel composes existing shells / hooks / dialogs.
- [ ] No new legacy DB function created (besides the V94 `wb_get_next_val` branch documented in [wecms-api/CLAUDE.md "Surviving legacy functions"](../../../../CLAUDE.md)).
- [ ] Audit-log writes produce one `AUDIT_LOGS_INFM` row per Create / Update / Delete; row carries the slug `chart-of-accounts` in `WSVC_ID` (NOT `comm_0223_001`).
- [ ] `CLAUDE.md` updates land in the same MR (Flyway table + Surviving legacy functions table + Feature Status table).

---

## 7. Post-Phase-1 follow-up backlog

Not part of this plan; tracked here so they don't get lost.

1. **Phase 2 — Excel bulk import** wiring (`POST .../bulk-import` + template download + per-row error reporting). 1.5 dev-days estimated.
2. **V97 — `CHART_ACCT_SEQ_NO` VARCHAR(14) → BIGINT** migration; entity drops the `@Convert(StringToLongConverter)`. 0.25 dev-day.
3. **VI / KO translation completion** for `settings.chart_of_accounts.*` keys. Out-of-MR translator pass.
4. **Reports module integration** — `fnsh_0200_001` ports with the Reports module sprint; will add a join read against `CHART_ACCT_INFM` but no schema changes here.
5. **Banking transaction write-side enforcement** — `ACCT_TRNS_HSTR.CHART_ACCT_CD` FK validation when banking module's write path ports. No schema change in this feature.
6. **View Setting popover adoption (if PO reverses OQ-6 later)** — add a `<SYS_SCR_OPTION_WECMS>` seed for `web_svc_id='chart-of-accounts'` and wire `<ViewSettingPopover>` in the panel. 0.5 dev-day.
