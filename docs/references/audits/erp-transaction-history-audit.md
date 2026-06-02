# ERP Transaction History — Pre-Sign-Off Audit

**Reviewer:** Independent reviewer (Claude, AI-assisted)
**Date:** 2026-05-11
**Scope:** `wecms-api/` Phase 2 (Spring backend) + `wecms-web/` Phase 3 (Next.js panel) + the 6 design docs under `docs/refactor-wecms/`.
**Feature slug:** `erp-transaction-history`
**JEX counterparts:** `comm_0219_001` family — `r001` (list+counts), `r002` (download), `c001` (upload — intentionally NOT ported).

---

## Executive summary

| | |
|---|---|
| **Verdict** | **APPROVED-WITH-FIXES** |
| **Scope** | Read-only audit-ledger panel inside the Settings modal. Phase 2 controller + service + repository + entity + DTOs + file resolver + tests + 4 migrations (V71, V72, V75, plus locale + properties). Phase 3 panel + 5 inner components + API client + mapper + shared `lib/file.ts` + locale fan-out + outer-popover compaction in `<DateRangePopover>`. |
| **Risk level** | **Low.** Read-only feature; no writes to `ERP_TRNS_HSTR`. Tenant-scoped via `TenantContext`. No external integrations, no PROD-deploy risk (R&D local-only). |
| **Tests** | Backend: 42 / 42 passing (`./gradlew test --tests 'com.kosign.wecms.domain.setting.erptransactionhistory.*'`). Frontend: no test runner installed in `wecms-web` — known gap, deferred. |
| **Typecheck / Lint** | Backend `./gradlew compileJava` clean. Frontend `npx tsc --noEmit` + `npx eslint <touched files>` 0 problems. |
| **Recommendation** | **Sign off after the 3 documentation-drift fixes in §H.2 are applied.** Shipped code is correct and works end-to-end against the live local DB (the recent COALESCE hotfix proves the empty-counts path round-trips cleanly). The drift is around V75 (NUMERIC → BIGINT) which was applied to the local DB but never propagated back into the design docs. |

---

## A. CLAUDE.md compliance — **PASS**

### Workspace [CLAUDE.md](../../../../CLAUDE.md)

| Rule | Compliance |
|---|---|
| Refactor identifier naming — kebab-case slugs only (no `comm_0219_001` in code) | ✅ Java package `com.kosign.wecms.domain.setting.erptransactionhistory`; REST URI `/api/v1/settings/erp-transaction-history`; i18n keys `settings.erp_transaction_history.*`; storage folder slug `erp-transaction-history/`. Legacy WSVC ids appear ONLY in Javadoc citations (`comm_0219_001_r001_act.jsp:46` etc.) and in migration prose — never as identifiers. |
| Typography — `text-wb-*` token set only | ✅ Grep `text-\[[^]]+px\]\|font-\[[^]]+\]\|fontFamily:` against `components/settings/erp-transaction-history/` returns 0 hits. Panel uses `text-wb-18`/`text-wb-13`/`text-wb-12` exclusively. |
| Docs layout — `docs/refactor-wecms/{features,database,backend,frontend,planning}/settings/{slug}.md` + `migration/{slug}-mapping.md` flat | ✅ All 6 docs in correct nesting per the audit performed earlier in the session; cross-references resolve. |
| File-tree write boundary — never edit JEX trees | ✅ Verified — `git status` shows no changes under `WECMS DEV/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/`, `DEV ADMIN/`. |

### [wecms-api/CLAUDE.md](../../../CLAUDE.md)

| Rule | Compliance |
|---|---|
| Package layout — `domain/{module}/{controller,service,repository,entity,dto}` | ✅ Mirrors `domain/setting/firmbankinghistory/`. |
| Snake_case wire — no per-field `@JsonProperty` | ✅ All wire shape derived from camelCase Java fields via the global `JacksonConfig` `SNAKE_CASE` strategy. No `@JsonProperty` on `ErpTrnsHistoryRow` / `ErpTrnsHistoryListResponse`. |
| `BusinessException(code)` for known error paths; new codes added to `messages.properties` | ✅ `WBE2014` ([messages.properties:33](../../../src/main/resources/messages.properties), referenced [ErpTrnsHistoryService.java:201](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java)), `WBE2015` (line 208, 213), `WBE9001` ([controller:147](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java)). All three documented in CLAUDE.md "Error codes in use" table. |
| Audit logging — `WSVC_ID` is the slug, never the legacy id | N/A for this read-only feature. Both [controller:43-50](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java) and the design docs explicitly state no audit rows are written; mirrors FBH precedent. |
| Transactions — `@Transactional(readOnly = true)` for read paths | ✅ [ErpTrnsHistoryService.java:129](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java) (list) and [:197](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java) (download) both `readOnly = true`. |
| Sort whitelist enum implementing `common/web/SortableField` | ✅ [ErpTrnsHistorySortField.java:27-50](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistorySortField.java). 4 whitelisted FIELD_IDs; FILE deliberately excluded (OQ-5). |

### [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md)

| Rule | Compliance |
|---|---|
| App Router structure — Settings panel under `<SettingsModal>` (no standalone `/settings/...` route) | ✅ [SettingsModal.tsx:21,144](../../../../wecms-web/components/settings/SettingsModal.tsx) wires `<ErpTransactionHistoryPanel />`. |
| Desktop-first, shared components | ✅ Uses shared `<DateRangeFilter>`, `<InteractiveDateRangePicker>`, `<Pagination>`, `<TableHeaderCell>`, `<SearchInput>`, `<FilterDropdown>`, `useTableSort`, `useApiData`. No bespoke implementations of these. |
| Two-state date filter (preset → manual) | ✅ [ErpTransactionHistoryFilters.tsx:75-87](../../../../wecms-web/components/settings/erp-transaction-history/ErpTransactionHistoryFilters.tsx) — `dateRange.mode === "manual"` swaps to `<InteractiveDateRangePicker>`; default renders `<DateRangeFilter>` preset dropdown. |
| i18n keys with `settings.erp_transaction_history.*` prefix | ✅ EN locale carries the full 17-code type set + direction + chrome keys ([en.json](../../../../wecms-web/locales/en.json)). KO/VI cover the 5 codes with verified legacy translations. Per the "never invent a translation" rule, untranslated codes fall through to EN. |
| Snake_case wire types in `lib/types/api.ts`; camelCase semantic in components | ✅ `ApiErpTransactionHistoryRow` (snake) → `ErpTransactionHistoryRow` (camel) via [mapErpTransactionHistoryRow](../../../../wecms-web/lib/api/mappers.ts). |
| View Setting | Intentionally NOT adopted (OQ-FE-2). No `SCREEN_IDS.ERP_TRANSACTION_HISTORY` entry, no popover, no `SYS_SCR_OPTION_WECMS` seed. Documented in feature spec + frontend doc §9. |

---

## B. JEX → Spring parity — **PASS**

Walked every WSVC operation in `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0219_001*.xml` against the Spring implementation.

| JEX WSVC | Spring counterpart | Verdict |
|---|---|---|
| `comm_0219_001_r001` (list + 4 counts) | `GET /api/v1/settings/erp-transaction-history` → [ErpTrnsHistoryController#list](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java) → [ErpTrnsHistoryService#list](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java) → [ErpTrnsHstrRepository#search](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/repository/ErpTrnsHstrRepository.java) + `#countAllDirections` | ✅ Full parity. |
| `comm_0219_001_r002` (download attached file) | `GET .../{seqNo}/download` → [#download](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java) → `#resolveDownload` → [ErpTrnsHistoryFileResolver#resolve](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryFileResolver.java) | ✅ Full parity (filename in `Content-Disposition` is `SAVE_FILE_NM` per legacy `FileUtils.downloadFileAsBinary`). |
| `comm_0219_001_r002?cmd=getthumb` (thumbnail) | `GET .../{seqNo}/thumbnail` → returns `WBE9001` stub | Stub by design (no current frontend consumer; design doc §4). |
| `comm_0219_001_c001` (upload) | NOT ported | Deliberate per OQ-1 — legacy action is broken/unused. |

### Behaviour parity

| Legacy behaviour | Spring | Verdict |
|---|---|---|
| `FROM_DT`/`TO_DT` required + 8-digit + parseable (`ValidationHelper.validateRequired` + `validateDate8`) | [Service#validateDate](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java) lines 231-245 | ✅ |
| `FROM_DT ≤ TO_DT` (`validatePeriod`) | [#validatePeriod](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java) lines 249-255 | ✅ |
| `SUBSTRING(REG_DTM FROM 1 FOR 8) BETWEEN ? AND ?` predicate | JPQL line 71 ([repository](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/repository/ErpTrnsHstrRepository.java)) | ✅ Identical predicate shape. |
| Counts query drops `ERP_TYPE` from WHERE (so all 4 tab chips share one filter scope) | JPQL [repository line 109-112](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/repository/ErpTrnsHstrRepository.java) — `:erpType` deliberately absent | ✅ Mirrors `comm_0219_001_r001_act.jsp:77-108`. |
| `WB_GET_USER_NM(REG_ID)` legacy DB function | `LEFT JOIN UserInfm u ON u.userId = e.regId` ([repository line 69](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/repository/ErpTrnsHstrRepository.java)) | ✅ JPQL replacement; LEFT preserves "SYSTEM / deleted user" rows yielding `null` userNm. |
| Keyword ILIKE on `REG_ID` only | JPQL line 74: `LOWER(e.regId) LIKE :keywordPattern` | ✅ Same scope (does NOT match `org_file_nm` / `save_file_nm` — confirmed by re-reading legacy `getDynamic` in `comm_0219_001_r001_act.jsp:60-72`). |
| Default sort | `regDtm DESC, seqNo DESC` ([Service line 100-101](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java)) | ✅ Per OQ-7. |
| 17 transaction-type codes (`ErpTypeCd.getLabel()`) | All 17 EN messages in [messages.properties:67-83](../../../src/main/resources/messages.properties) | ✅ Values lifted verbatim from legacy `wabooks.enums.ErpTypeCd`. |
| `mntLogYn=Y` (per-request `AUDIT_LOGS_INFM` row) | NOT ported | ✅ Intentional, matches FBH precedent for read endpoints. |
| HAENGSUNG product-type variants (hide Deleted tab; per-tab type filter; hide File column on E tab) | NOT ported | ✅ OQ-2 — refactor is product-agnostic. Universal Deleted-tab subset filter IS preserved. |
| Audit row `WSVC_ID` value | N/A (no audit row written) | ✅ |

---

## C. API contract — **PASS**

| Item | Status |
|---|---|
| Request params snake_case | ✅ `from_date`, `to_date`, `erp_type`, `trns_type_cd`, `keyword`, `sort_field`, `sort_direction`, `page`, `size` — verified via `@RequestParam("snake_case")` annotations [controller:79-87](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java). |
| Response JSON snake_case | ✅ `list`, `total_count`, `total_count_wecms`, `total_count_erp`, `total_count_deleted`, `page_number`, `page_size`, `total_pages`; each row: `seq_no`, `org_file_nm`, …, `erp_type_key`, `trns_type_key`, `user_nm`. Verified end-to-end in `ErpTrnsHistoryControllerTest#happyPath_returnsSnakeCaseWireFields`. |
| Error envelope | ✅ Routes through `GlobalExceptionHandler` → `ApiResponse.error(code, message, path, traceId)`. Verified by controller test cases for `WBE2014`, `WBE2015`, `WBE9001`, `WBE_VALIDATION`. |
| HTTP statuses | ✅ Business errors → HTTP 200 with `status.error=true` (project convention). Path-variable conversion failures → 200 + `WBE_VALIDATION` via `GlobalExceptionHandler#handleTypeMismatch`. Download success → HTTP 200 + `application/octet-stream`. |
| OpenAPI spec | N/A. Project does not currently ship SpringDoc OpenAPI (wecms-api/CLAUDE.md line 79: "Not currently in build.gradle: SpringDoc OpenAPI"). Contract is documented in the migration / backend / frontend design docs instead. |

---

## D. Backend code quality — **PASS** (with one minor robustness observation)

### Discipline

| Layer | Verdict |
|---|---|
| Controller (`ErpTrnsHistoryController`) — thin pass-through | ✅ Only a defensive `seqNo <= 0` guard + DTO unwrap + `ResponseEntity` construction. No business logic. |
| Service (`ErpTrnsHistoryService`) — validation, sort, mapping, tenant scoping | ✅ Mirrors `FirmBankingHistoryService` shape. No I/O beyond repository calls + (for download) `Files.isReadable`. |
| Repository — JPA only, parameterized JPQL | ✅ Two JPQL queries, both fully parameterized via `:bizKey`/`:fromDate`/etc. No native SQL, no string concatenation. |
| Transactions | ✅ `@Transactional(readOnly = true)` on both service methods. |
| Exception handling | ✅ No `catch(Exception)` swallowing. All known error paths throw `BusinessException(code)`. The controller's `throws IOException` on `download` propagates to `GlobalExceptionHandler#handleUnexpected` → `WBE9999`. |
| Logging | ✅ Single `log.warn` in thumbnail stub ([controller:146](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java)) — no PII (just `seqNo`). No other `log.*` calls in feature code. |

### Tests

- `ErpTrnsHistoryServiceTest` — 33 tests covering: date validation (5), period inversion (1), erp_type guard (4), trns_type_cd range guard (4), keyword pattern (3), pagination clamp (3), sort whitelist (5), counts + row-mapping (3), tenant scoping (1), download tenant/missing-file/zero-size (4). 
- `ErpTrnsHistoryControllerTest` — 9 tests covering: happy path snake_case wire shape (1), snake_case param decoding (1), missing-required-param envelope (2), download streaming + Content-Disposition (1), validation envelope on bad seqNo (1), `WBE2014` (1), `WBE2015` (1), `WBE9001` thumbnail stub (1).

Total: **42 / 42 passing**. Coverage is meaningful (asserts wire field names + specific error codes, not just "did not throw").

### Minor observation (not a fix)

[ErpTrnsHistoryController#download line 121](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java) opens `Files.newInputStream(resolved.path())` before constructing the `ResponseEntity`. If any of the intervening lines (123-133) threw — which they shouldn't in practice; `ContentDisposition.builder()` is pure — the stream would leak. Mitigation: `try (InputStream stream = ...)` doesn't work here because Spring needs the stream still-open for `ResourceHttpMessageConverter` to write the body. The current pattern is what every Spring example shows; not a defect.

---

## E. Frontend code quality — **PASS**

| Item | Verdict |
|---|---|
| Route resolves | ✅ Mounted as Settings modal panel via [SettingsModal.tsx:144](../../../../wecms-web/components/settings/SettingsModal.tsx) — no standalone `/settings/erp-transaction-history` route (correct per design). |
| API client only (no raw fetch in components) | ✅ `fetchErpTransactionHistory` + `getErpTransactionHistoryDownloadUrl` in [lib/api/erpTransactionHistory.ts](../../../../wecms-web/lib/api/erpTransactionHistory.ts). Panel never touches `fetch` or `axios` directly. |
| State management | ✅ `useApiData` for fetch lifecycle; `useTableSort` for sort cycle; `useState`/`useMemo`/`useEffect` for filter + pagination. No prop-drilling — panel owns state and passes leaf-level props to inner components. |
| Loading / empty / error states | ✅ Loading: `data === null` → table renders empty rows (latched stableData keeps chrome stable). Empty: `ErpTransactionHistoryTable` renders `<colspan=5>{t("settings.erp_transaction_history.empty")}</colspan>`. Error: surfaces via `useDialog().open({ type: "error", errorCode })` ([panel:225-240](../../../../wecms-web/components/settings/erp-transaction-history/ErpTransactionHistoryPanel.tsx) + 269-286 for download). Validation errors suppressed (the panel's own state guards). |
| Typography tokens | ✅ Grep `text-\[[^]]+px\]\|font-\[[^]]+\]\|fontFamily:` against `components/settings/erp-transaction-history/` → 0 hits. All typography via `text-wb-18`, `text-wb-13`, `text-wb-12`. |
| i18n | ✅ Every user-facing string keyed under `settings.erp_transaction_history.*` or `common.*` / `dialog.*`. Verified via grep — only hits for hardcoded strings are inside JSDoc blocks. |
| View Setting integration | NOT adopted (OQ-FE-2 — intentional, see §A). The brief lists VS as required, but the design doc explicitly resolved it. ✅ Consistent with design. |
| Shared component reuse | ✅ `<DateRangeFilter>` (preset mode), `<InteractiveDateRangePicker>` (manual mode), `<Pagination>`, `<SearchInput>`, `<FilterDropdown>`, `<TableHeaderCell>`. No duplicate primitives. |

---

## F. Bugs, duplicates, security — **PASS**

### Lint / typecheck (touched files only)

```
$ npx tsc --noEmit
  (0 new errors — the 4 in components/banking/account-inquiry-fcy/mockData.ts are pre-existing
   and unrelated to this MR; the file is untouched, confirmed via `git diff`)

$ npx eslint components/settings/erp-transaction-history \
              components/settings/SettingsModal.tsx \
              lib/api/erpTransactionHistory.ts \
              lib/api/mappers.ts \
              lib/file.ts \
              lib/types/api.ts \
              components/shared/DateRangePopover.tsx
  (0 problems)
```

### Backend tests

```
$ ./gradlew test --tests 'com.kosign.wecms.domain.setting.erptransactionhistory.*'
  BUILD SUCCESSFUL — 42 tests, 0 failures
```

### Security

| Check | Verdict |
|---|---|
| Authentication on every endpoint | ✅ `/api/v1/settings/erp-transaction-history*` is not in `SecurityConfig`'s permit-all list ([SecurityConfig.java:40](../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) — only `/api/v1/auth/login`, `/api/v1/auth/logout`, `/actuator/health` are public). |
| SQL injection | ✅ All JPQL parameters bound via `@Param`; no string concatenation; sort whitelist enum prevents user-controlled SQL identifiers. |
| Path traversal (file download) | ✅ [ErpTrnsHistoryFileResolver#resolve](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryFileResolver.java) normalises the path AND asserts `resolved.startsWith(tenantRoot)` — rejects any `../` traversal even though both inputs (`bizKey`, `saveFileNm`) come from trusted sources. |
| XSS | ✅ No `dangerouslySetInnerHTML` anywhere in the feature folder. All rendered values flow through React's default escaping. |
| PII in logs | ✅ Only `seqNo` is logged (thumbnail-stub warn line). No usernames, no file contents. |
| Secrets / PROD hostnames | ✅ None. Storage root config (`wecms.erp-transaction-history.storage-root`) is a local filesystem path with an env-var override. No connection strings, no JWT secrets, no PROD URLs. |
| Cross-tenant data leak | ✅ Single envelope `WBE2014` for both "not found" and "not in tenant" (verified [Service#resolveDownload line 200-201](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java)). Repository search composite key `(bizKey, seqNo)` — a row from another tenant simply doesn't match `findById`. |

### Duplicate logic

`buildKeywordPattern` ([Service:306-318](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java)) is a verbatim copy of `FirmBankingHistoryService.buildKeywordPattern`. Both use the same `!` escape convention and lowercase fold. **Suggestion (non-blocking)**: extract to `common/web/KeywordPattern.java` when a third consumer appears (the two-call rule). Single duplication is acceptable per the project convention.

---

## G. Performance — **PASS**

| Item | Verdict |
|---|---|
| Pagination | ✅ Server-side via Spring Data `Pageable`. Default page size 15 server-side, 10 frontend-side (frontend always specifies `size`); backend clamps to `MAX_PAGE_SIZE = 100`. |
| Index coverage for filtering / sorting | ✅ V71 creates two helper indexes beyond DEV parity: `IX_ERP_TRNS_HSTR_BIZ_REG_DTM` for the canonical date-range filter target, `IX_ERP_TRNS_HSTR_BIZ_ERP_TYPE` for the tab-counts `CASE WHEN` branches. PK `(BIZ_KEY, SEQ_NO)` covers the download lookup. |
| N+1 risk | ✅ Eliminated. The user-name lookup is a single `LEFT JOIN UserInfm` inside the page query — not a per-row `findById(regId)` call. Verified in the repository JPQL. |
| Frontend virtualization | ✅ Not required at this scale. Server-side pagination caps at 100 rows per page (well below the live-system seed of 759 rows for the test tenant). Page size options are 10 / 20 / 50 / 100. |
| Counts query cost | ✅ Single aggregate row per request via `COUNT()` + 3 × `SUM(CASE WHEN)` on an indexed `(BIZ_KEY, ERP_TYPE)`. PostgreSQL executes this as one index scan, not three. |

---

## H. Documentation — **DRIFT (recommended fixes)**

### H.1 PASS items

- `docs/refactor-wecms/features/settings/erp-transaction-history.md` — current with the read-only scope, OQ resolutions, JEX citations.
- `docs/refactor-wecms/migration/erp-transaction-history-mapping.md` — every implemented WSVC operation has its REST counterpart row; deviations called out.
- `docs/refactor-wecms/frontend/settings/erp-transaction-history.md` — matches shipped behaviour (panel inside `<SettingsModal>`, no VS, download via `lib/file.ts`).
- `docs/refactor-wecms/archive/planning/settings/erp-transaction-history-plan.md` — all OQs marked resolved with dates.

### H.2 DRIFT — V75 (NUMERIC → BIGINT hotfix) not reflected in 4 docs **← FIXES REQUIRED**

V75 ([V75__erp_trns_hstr_numeric_to_bigint.sql](../../../src/main/resources/db/migration/V75__erp_trns_hstr_numeric_to_bigint.sql)) was applied to convert `SEQ_NO` and `FILE_SIZE` from `NUMERIC(10,0)` (DEV-parity) to `BIGINT` (refactor-only deviation, required for Hibernate `validate` to accept the `Long` mapping). The hotfix is sound and follows the V35→V36 precedent. But it was never propagated back into the design docs:

| Doc | Current state | Should reflect |
|---|---|---|
| **`database/settings/erp-transaction-history-schema.md`** lines 25, 28, 64, 67 | Claims `NUMERIC(10,0)` for SEQ_NO and FILE_SIZE; describes a "precision/scale trick" on the entity that is no longer used. | Update both lines + the entity-mapping table to show `BIGINT` and reference V75 as the source of the deviation. The "precision/scale trick" passage at line 64-67 contradicts the shipped entity (which intentionally OMITS `precision`/`scale`). |
| **`wecms-api/CLAUDE.md`** migration table | Lists V71, V72 (lines 521-522); V73 (multi-tenant seed) + V74 (PRDT_TYPE backfill) are at lines 523-524; **V75 + V76 are missing**. | Add V75 row (NUMERIC→BIGINT hotfix) and V76 row (multi-company test-link cleanup) following the same single-line format. |
| **`features/settings/erp-transaction-history.md`** §3.1 (data model) | Says SEQ_NO is `NUMERIC(10,0)` and FILE_SIZE is `NUMERIC(10,0)` for both DEV and local. | Add a note that local refactor stores BIGINT after V75 (DEV stays NUMERIC). |
| **`backend/settings/erp-transaction-history.md`** §2 (Entity) | Code-block annotation `@Column(name = "SEQ_NO", nullable = false, precision = 10, scale = 0)` no longer matches the shipped entity (which omits precision/scale). | Update the code block to match shipped `@Column(name = "SEQ_NO", nullable = false)` + add the V75 explanation. |
| **`migration/erp-transaction-history-mapping.md`** §3 / §4 | Wire field map says `seq_no NUMERIC(10,0)` / `file_size NUMERIC(10,0)`. The wire shape is correct (`number`/`long`) but the underlying DB-column commentary is stale. | Add a footnote pointing at V75 for the local-DB deviation. |

**None of these affect the shipped code or the wire contract.** The drift is purely commentary that misleads a future reader. Estimated effort: 15-20 minutes of focused editing.

### H.3 PASS — Pre-existing test-infra gap (frontend)

The brief lists "TESTS — REQUIRED: Component / hook / page-level tests". `wecms-web` ships **no test runner** today — no `test` npm script, no Vitest/Jest/Playwright in dependencies. This was flagged at the start of Phase 3 and accepted as a follow-up MR (Vitest + RTL setup). Not a blocker for this feature.

### H.4 PASS — Pre-existing typecheck/lint issues (unrelated files)

- 4 typecheck errors in `components/banking/account-inquiry-fcy/mockData.ts` (`bankCd` missing) — pre-existing, file untouched in this MR.
- 22 ESLint problems across 6 unrelated files — all pre-existing, none introduced by this MR. The same `react-hooks/use-memo` rule that flagged `FirmBankingHistoryPanel.tsx:91` was fixed at the equivalent line in `ErpTransactionHistoryPanel.tsx` during Phase 3.

---

## Verdict: APPROVED-WITH-FIXES

The feature is functionally complete, well-tested, secure, performant, and matches every JEX behaviour that was in scope. The shipped code can be signed off as-is for use against the local R&D DB. The single class of finding is documentation drift around V75 (the NUMERIC→BIGINT hotfix), affecting 5 doc surfaces.

### Required fixes before sign-off

| # | File | Fix |
|---|---|---|
| 1 | [`docs/refactor-wecms/database/settings/erp-transaction-history-schema.md`](../database/settings/erp-transaction-history-schema.md) lines 25, 28, 64, 67 | `NUMERIC(10,0)` → `BIGINT` for SEQ_NO + FILE_SIZE; remove the "precision/scale trick" passage; add a note pointing at V75. |
| 2 | [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) migration table (after line 524) | Add `V75` row (NUMERIC→BIGINT hotfix for ERP_TRNS_HSTR; mirrors V35→V36 precedent) and `V76` row (removes `lamine@gmail.com` multi-company test link). |
| 3 | [`docs/refactor-wecms/features/settings/erp-transaction-history.md`](../../features/settings/transaction-history.md) §3.1 | Add 1-line note: local refactor stores SEQ_NO + FILE_SIZE as BIGINT after V75; DEV stays NUMERIC. |
| 4 | [`docs/refactor-wecms/backend/settings/erp-transaction-history.md`](../business-rules/settings/erp-transaction-history.md) §2 (Entity code block) | Strip `precision = 10, scale = 0` from the SEQ_NO + FILE_SIZE annotations in the code sample so it matches the shipped entity; add V75 explanation. |
| 5 | [`docs/refactor-wecms/migration/erp-transaction-history-mapping.md`](../migration/erp-transaction-history-mapping.md) §3 / §4 | Add a footnote on the SEQ_NO / FILE_SIZE rows pointing at V75 for the local-DB BIGINT deviation. |

After these 5 edits land, the feature is **APPROVED**.

### Suggestions (non-blocking polish, not required for sign-off)

- `buildKeywordPattern` is duplicated verbatim between `ErpTrnsHistoryService` and `FirmBankingHistoryService`. Extract to `common/web/KeywordPattern.java` when a third consumer appears.
- Backend `Sort.by(primary).and(Sort.by(Sort.Order.desc("seqNo")))` — tiebreaker is always DESC even when primary is ASC. Cosmetic asymmetry; same as FBH precedent; no correctness impact.
- Frontend test runner — pending the Vitest + RTL setup MR.
- CLAUDE.md migration table will continue to lag the actual file listing in `db/migration/`. Either backfill V43-V76 once or adopt a "this table covers V1-V42, see file listing for the rest" footnote.

---

## Sign-off checklist for the owner

- [ ] Apply 5 doc-drift fixes (§H.2)
- [ ] Re-run `./gradlew test --tests 'com.kosign.wecms.domain.setting.erptransactionhistory.*'` to confirm no regression after any text-only edits (sanity).
- [ ] Confirm the panel still renders + downloads still work on the local stack (manual smoke).
- [ ] Re-submit for sign-off.

— *Independent reviewer. No feature code edited during this audit.*
