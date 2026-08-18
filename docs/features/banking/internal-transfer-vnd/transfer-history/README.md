# Transfer History — Backend (wecms-api)

> **Module:** Banking › Internal Transfer › Transfer History
> **Refactor slug:** `transfer-history`
> **Legacy JEX:** `trns_0003_001` / `trns_0003_001_r001`
> **Status:** ✅ **Implemented — full-stack, legacy parity verified** (backend + frontend landed 2026-05-20)
> **System:** Refactor (Spring Boot 3.5.13)

This folder is the **primary documentation home** for the Transfer History backend feature. It supersedes the scattered notes previously filed under `own-account-transfer/` and `references/`; those older docs are preserved and cross-linked below for migration/audit traceability (per the workspace "Existing Documentation Handling" rule — nothing was deleted).

---

## 1. What this feature is

Transfer History is a **read-only inquiry list** of NAPAS firm-banking transfer transactions executed by the tenant. The user picks a date range and an optional success/failed filter, and the screen returns a paged, sortable grid of transfers with per-currency totals.

It mirrors the legacy JEX screen `trns_0003_001` (view `trns_0003_001_view.jsp`, read service `trns_0003_001_r001`), reimplemented as a clean Spring Boot query endpoint over the `NAPAS_REMIT_TRNS_HSTR` table.

### What it is NOT (scope boundary — read this first)

The Transfer History feature is a **single read endpoint**. It does **not** include:

- ❌ Vendor / expense **matching**
- ❌ Match **candidate search**
- ❌ **Unmatch** / unmatch-all
- ❌ A per-row **detail** API or detail route
- ❌ **Export** (XLSX) or print
- ❌ Any **write** / mutation endpoint
- ❌ Audit-log writes

Those capabilities belong to a **different feature** — Banking › Transaction History **bank-matching** (Smart Organizer, legacy `comm_0196_*`), which is wired into `AccountController` under `/api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match*` and operates over `ACCT_TRNS_HSTR` / `SCDUL_PYMN_SUMR(_DETL)` / `BUSI_MATCH_LOG`. See [refactor-gap-analysis.md](refactor-gap-analysis.md) §"Scope boundary" for the full explanation of why those areas are **N/A** for Transfer History, not "missing".

---

## 2. Endpoint at a glance

| Property | Value |
|---|---|
| URL | `GET /api/v1/banking/internal-transfer/transfer-history` |
| Auth | JWT + **`@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-history')")`** (menu-authorization gated, like other banking controllers). No ADMIN-only role gate — consistent with legacy `NORM_USER_ONLY_YN=Y` (excludes WTA users only). |
| Tenant scope | `TenantContext.currentBizKey()` — `biz_key` never accepted on the wire |
| Audit log | **None** (read-only; OQ-4) |
| Response | `ApiResponse<TransferHistoryListResponse>` (lowercase snake_case envelope) |
| Wire shape | snake_case (global Jackson `SNAKE_CASE`) |

The deprecated Sprint-1 interim endpoint `GET /api/v1/banking/transfers/history` (`TransferController.history()` + `TransferHistoryService` + UPPER_SNAKE `TransferHistoryResponse` / `TransferRow`) was **deleted 2026-06-07** — it had no frontend consumer (the live UI uses the new spec-aligned path). `TransferController` now exposes only `/transfers/own-accounts`. See [interface-specification.md](interface-specification.md) §5.

---

## 3. Documentation map (this folder)

| File | Contents |
|---|---|
| [README.md](README.md) | This overview + scope boundary + cross-links |
| [business-flow.md](business-flow.md) | End-to-end flow, status semantics, totals, tenant + locale rules |
| [functional-specification.md](functional-specification.md) | Request params, validation, sort whitelist, error codes, page-size rules |
| [database-mapping.md](database-mapping.md) | `NAPAS_REMIT_TRNS_HSTR` schema, legacy column → entity → DTO → UI column chain |
| [interface-specification.md](interface-specification.md) | Controller signature, request/response DTOs, JSON examples, deprecated interim endpoint |
| [source-code-index.md](source-code-index.md) | Every backend + legacy file with paths/line numbers |
| [migration-guide.md](migration-guide.md) | JEX → Spring artifact-by-artifact mapping |
| [test-scenarios.md](test-scenarios.md) | Unit/controller test inventory + SIT/UAT scenarios |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity table (MATCH/PARTIAL/MISSING/N-A), scope boundary, open questions |

---

## 4. Cross-links (preserved existing docs — do not delete)

| Doc | Path | Role |
|---|---|---|
| Legacy JEX feature package (canonical legacy spec) | [docs/jex/features/transfer-history/](../../../../../../docs/jex/features/transfer-history/) | 10-file legacy analysis — primary source of truth for legacy behaviour |
| JEX → Spring migration mapping | [../../../references/migration/transfer-history.md](../../../../references/migration/transfer-history.md) | Service-by-service field mapping |
| Parity audit (2026-05-20) | [../../../references/audits/transfer-history-audit-2026-05-20.md](../../../../audits/historical/transfer-history-audit-2026-05-20.md) | Original parity audit |
| Archived Sprint-1 plan | ../../../archive/planning/phase-1/transfer-history-plan.md _(removed — historical `archive/` folder deleted by project decision)_ | Historical planning |
| Frontend page-design doc (existing) | [../../../../../wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/transfer-history.md](../../../../../../wecms-web/docs/features/banking/internal-transfer-vnd/transfer-history/README.md) | Frontend design (misfiled under own-account-transfer; new canonical home is `wecms-web/docs/features/banking/internal-transfer-vnd/transfer-history/`) |
| Deprecated legacy mirror | `docs/refactor-wecms/...transfer-history...` | Read-only, superseded 2026-06-01 |

---

## 5. Key implementation facts

- **Status semantics:** `trns_stts = "00000000"` ⇒ Success; anything else ⇒ Failed. The wire filter `trns_stts=99999999` is a sentinel meaning "failed" (resolves to a `<> '00000000'` predicate, null-safe). Verbatim engine `trns_stts` / `trns_msg` ride on each row.
- **UTC → display zone:** `REG_DTM` is a `VARCHAR(14)` UTC `yyyyMMddHHmmss` string. The service normalizes it to `Asia/Ho_Chi_Minh` (UTC+7) for display (`trns_dt` / `trns_tm`) but **filters on the raw UTC string** (legacy parity).
- **Totals:** backend computes VND + USD amount/count totals server-side (`aggregateTotals` JPQL). The frontend renders the VND row only today.
- **Bank names:** out-bank via `BankCdResolver`; in-bank via the ported legacy function `wb_get_napas_bank_nm_or_citad_bank_nm`, keyed on `IN_NAPAS_BANK_CODE`.
- **Helper index — DROPPED (no current migration).** The base table ships as [`V69__napas_remit_trns_hstr.sql`](../../../../../src/main/resources/db/migration/V69__napas_remit_trns_hstr.sql) with **PK only** (verified: zero `CREATE INDEX`), matching DEV. *Historical only:* `V196__transfer_history_helper_index.sql` added `ix_napas_remit_trns_hstr_biz_regdtm` on `(BIZ_KEY, substr(REG_DTM,1,8) DESC, SEQ_NO DESC)`; it was removed by the [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md) along with every other helper-index migration and has **no current equivalent**. Re-propose as a manual DBA script if the date-range query needs it.
