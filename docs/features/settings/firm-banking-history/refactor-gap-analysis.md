# Firm Banking History — Refactor Gap Analysis (Final Parity Review)

Final parity review of the refactored Firm Banking History against the legacy JEX `comm_0221_001` /
`comm_0221_001_r001`. Parent: [README.md](README.md).
Backed by the JEX-side audit set: [refactor-gap-analysis](../../../../../docs/jex/features/firm-banking-history/refactor-gap-analysis.md) ·
[refactor-verification-report (2026-06-05)](../../../../../docs/jex/features/firm-banking-history/refactor-verification-report.md) ·
[parity checklist](../../../references/audits/firm-banking-history-parity-checklist.md) ·
[independent review (2026-05-08)](../../../references/audits/firm-banking-history-review-2026-05-08.md).

**Review date:** 2026-06-05. **Legend:** MATCH = behaviourally equivalent · PARTIAL = equivalent
with a documented intentional deviation · MISSING = legacy behaviour not reproduced.

---

## 1. Parity matrix

| Area | Legacy (JEX) | Refactor | Status |
|---|---|---|---|
| **Search (date range)** | `FROM_DT`/`TO_DT` required `YYYYMMDD`, `left(TARN_DTM,8) between` | `from_date`/`to_date` required `YYYYMMDD`, `SUBSTRING(tarn_dtm,1,8) BETWEEN` | **MATCH** |
| **Date filters / default range** | last 30 days (`loadinitializeDate(-30)`) | `defaultDateRange()` last 30 days | **MATCH** |
| **Service-type filter** | `SVC_TYPE` optional (F/S/G/B), no back-end enforcement | `svc_type` optional, explicit `{F,S,G,B}` validation → `WBE_VALIDATION` | **MATCH** (stricter, safe) |
| **Keyword search** | `SRCH_WD` → `SEQ_NO ilike '%'||?||'%'` | `keyword` → `LOWER(seq_no) LIKE %kw% ESCAPE '!'` (meta-chars escaped) | **MATCH** (hardened) |
| **Pagination** | `PAGE_NO` (1-based) + `PAGE_PER_CNT`, default 15 | `page` (0-based) + `size`, backend default 15, max 100; frontend sends 10 | **PARTIAL** — see GAP-1 |
| **Sorting** | free `paramSort` via `SortHelper` | whitelisted enum (6 FIELD_IDs); unknown → default | **MATCH** (hardened) |
| **Default sort** | `TARN_DTM DESC` | `TARN_DTM DESC, SEQ_NO DESC` (deterministic tiebreaker) | **MATCH** |
| **Grid columns** | 7 cols (date/time, svc type, svc name, seq no, bank, result, action) | identical 6 data cols + View Details | **MATCH** |
| **Detail view** | `fnDetails` per-leg, no extra call | client-side popover per-leg, no extra call | **MATCH** |
| **Service-name mapping** | frontend `SERVICE_NAME_MAP` | server `FirmBankingServiceNameResolver` + `svc_name_key` | **MATCH** (ownership moved server-side) |
| **Status / result message** | F via `DICTIONARY_VAN`; S/G/B via `resultMap` (18 codes) | F via ported `dictionaryVan`; S/G/B via 18-code `scrapingResultCodes` | **MATCH** |
| **Status pill (success/failed)** | success = `00000000`/`0000` | same; blank/null → failed | **MATCH** (GAP-2 fixed 2026-06-05) |
| **Product-type H gate** | hide G/B options when `prdtTypes === "H"` | frontend hides G/B options for product H | **MATCH** (GAP-3 fixed) |
| **Permissions** | login required, `NORM_USER_ONLY_YN=Y`, `WTA_USER_ALLOW_YN=N` | `@PreAuthorize("hasAnyRole('ADMIN','USER') and @menuAuthz.canAccess('settings-firm-banking-history')")` (no WTA users exist in refactor; menu-authz term is the F-3 per-menu gate) | **MATCH** (GAP-4, faithful mapping) |
| **Tenant isolation** | `BIZ_KEY = loginUser.getBizKey()` per leg | `BIZ_KEY = TenantContext.currentBizKey()` per leg | **MATCH** |
| **Timezone** | `kr_to_vn_time` (F) + `DateTimeUtils.normalizeResult` per login user | `kr_to_vn_time` (F, called by the `@Subselect`) + frontend `formatLegacyDtmInDisplayTz` (Asia/Ho_Chi_Minh) | **MATCH** (display moved to frontend) |
| **Error handling** | `WBW0001`/`WBE0001`/`WBE1000`/`WBE9999` | consolidated to `WBE_VALIDATION` / `WBE_DB` / `WBE9999` + `trace_id` | **MATCH** (codes modernized) |
| **Bank-name resolution** | `wb_get_napas_bank_nm` (F) / `wb_get_bank_nm` (S) | same DB functions, called by the `@Subselect` (resolved against the JEX DB) | **PARTIAL** — see GAP-6 |
| **Read maintenance log** | WSVC `mntLogYn=Y` (logs the read) | not written | **MISSING** — see GAP-5 |
| **Excel export** | none | `POST /export` (refactor-only addition) | **N/A** (enhancement, no legacy baseline) |
| **Print** | none | none | **MATCH** |
| **Write / inquire actions** | none (read-only) | none (read-only) | **MATCH** |

---

## 2. Open / residual items

| # | Gap | Severity | Status | Detail |
|---|---|---|---|---|
| GAP-1 | Default page size 10 (frontend) vs 15 (legacy/backend) | Low | Intentional | Frontend canonical page size is 10 (matches Account Inquiry precedent); backend default remains 15 and accepts whatever `size` the frontend sends. Not a data-correctness issue. |
| GAP-2 | Blank/null `rslt_cd` status | — | ✅ Fixed 2026-06-05 | Now maps to `failed` in `mapFirmBankingHistoryRow`. |
| GAP-3 | Product-type H hides G/B filter options | — | ✅ Fixed | `FirmBankingHistoryFilters.tsx` gates G/B when product type `H`. |
| GAP-4 | Normal-user-only / WTA-user block | — | ✅ Fixed 2026-06-05 | `@PreAuthorize("hasAnyRole('ADMIN','USER') and @menuAuthz.canAccess('settings-firm-banking-history')")`; refactor has no WTA/agent user type, so the faithful role mapping is "any authenticated normal company role", further gated by the F-3 per-menu `@menuAuthz` term. |
| GAP-5 | Read-side maintenance-log write (`mntLogYn=Y`) | Low | Open | The refactor does not write an audit/maintenance row on read. Legacy logs the inquiry. Low impact for a read-only screen; revisit if read-audit becomes a compliance requirement. |
| GAP-6 | Bank-name COALESCE / null divergence | Low (cosmetic) | Open | Edge-case display difference when a bank code has no name match. Cosmetic only — value still renders the code. |

No blocker- or major-severity gaps remain. No data-corruption or tenant-leakage risk was found in
any review.

---

## 3. Status

> **Refactor Status: Functionally complete — legacy parity verified (2026-06-05).**

Every behavioural area is **MATCH** except:
- **GAP-1** (page-size default) — an intentional frontend choice, not a parity defect.
- **GAP-6** (bank-name null edge case) — cosmetic.
- **GAP-5** (read maintenance-log) — the single genuine MISSING item; low impact for a read-only audit screen.

Plus one refactor **enhancement** beyond legacy: XLSX export.

### Supporting evidence

- **Backend:** controller (2 endpoints, class-level `@PreAuthorize` role + `@menuAuthz.canAccess('settings-firm-banking-history')`), service (validation + paging + tenant
  scope), single JPQL over the `@Subselect`-backed `FirmBankingHistoryView` (5-leg union port of
  `IDO.TB_FIRM_TRAN_R002`; no DB view), DTO with `svc_type_key`/`svc_name_key`. Tests:
  `FirmBankingHistoryServiceTest`,
  `FirmBankingServiceNameResolverTest`, `FirmBankingHistoryControllerTest`,
  `FirmBankingHistoryExportProviderTest` — all green at last run.
- **Frontend:** panel + filters + table + detail popover wired to the live endpoint (no mock
  fallback), VAN + scraping result-code i18n bundles ported, service-name rendered from `svc_name_key`.
- **DB:** the five-leg union is a Hibernate `@Subselect` (no `V_FIRM_BANKING_HISTORY` view object);
  its output projection is the 11-column shape matching the legacy IDO `<out>`.
- **Verification:** [refactor-verification-report (2026-06-05)](../../../../../docs/jex/features/firm-banking-history/refactor-verification-report.md) concludes a faithful functional port.

### Recommendation

Close GAP-1 and GAP-6 as **accepted intentional/cosmetic deviations** (PO sign-off). Track GAP-5
(read maintenance-log) as a low-priority follow-up only if read-audit becomes a requirement. No code
blockers remain for declaring Firm Banking History done.
