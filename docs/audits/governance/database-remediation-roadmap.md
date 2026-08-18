# Database Audit Remediation Roadmap

**System:** Refactor (wecms-api)
**Date:** 2026-06-24
**Source of truth:** Database Consistency Audit (this session — entities, repositories, SQL, PostgreSQL functions, naming; cross-checked against live JEX DB `wecmsvndev` and refactor DB `wecmsdevdb` via MCP).
**Status:** Planning document only. No application code is modified by this roadmap.

---

## Governing principle

The refactor DB (`wecmsdevdb`) is a subset-copy of the live JEX DB (`wecmsvndev`), and the app runs against the **live JEX DB in all environments** (`project_uat_prod_live_jex_db_target.md`). The index, PK, and function profiles **match JEX exactly** for every table sampled. Therefore every finding falls into one of three ownership buckets, and only the first is freely actionable:

| Bucket | Meaning | Action policy |
|---|---|---|
| **Refactor-owned** | Java/DTO/wiring inside `wecms-api`; does not alter the legacy DB structure | Action under normal "change only what's broken" rule (`feedback_preserve_existing_code.md`) |
| **Legacy-inherited** | Schema / index / function characteristics that mirror JEX | Flag-only; changing them is structural drift forbidden by CLAUDE.md §1/§6 without PO approval |
| **PO-gated** | Touches API+WEB contract parity, legacy-parity behavior, or legacy DB structure | Requires Mr. Jung (PO) decision before any edit |

> **Migration-safety reminder:** Per CLAUDE.md §6, when two valid approaches exist, choose the one that keeps the refactor closest to legacy. Nothing in Phase A or B alters the legacy DB schema, columns, keys, or data. Phase C items are explicitly escalations, not implementations.

---

## Ownership classification (summary)

### Refactor-owned (Phases A & B — actionable)
1. `BankAccountPermissionService` `findAll()` + in-Java tenant filter (correctness)
2. HIGH N+1 per-element reads (4 sites)
3. MEDIUM per-row `save()` loops (write amplification)
4. 4 unused repositories
5. DEV-only DB trigger `busi_infm_init_data_tg` (DEV drift vs live-JEX target)
6. camelCase export DTOs on the wire (`ExcelColumnSpec`, `ExcelExportRequest`)
7. `Nxsias*` misspelled class triplet
8. Field-vs-column name divergence (verify-first; may be intentional contract)

### Legacy-inherited (flag-only — do NOT change without PO)
- Single-index tables / near-total absence of secondary indexes (matches JEX)
- 4 PK-less tables: `busa_doc_rcv_pay_his`, `busi_env_set_mdtr`, `nxsias_module_tran_log_his`, `pgro_bsac_lnkn_his` (PK-less in JEX too)
- Redundant index `idx_trco_infm` (duplicates `trco_infm` PK — exists in JEX)
- 5 DB functions unused by Java but possibly called internally by other DB functions
- 52 inventory tables with no entity/repository (coverage gap; "Unused ≠ removable")

### PO-gated (Phase C — escalate, do not implement)
- Duplicate payment-query consolidation across `OvrsPymnSumr` / `ScdulPymnSumr` (and the 4-table chartAcct scan)
- Any legacy DB schema/index change (add PK, add secondary index, drop redundant index)

---

# Phase A — Safe Improvements

Refactor-owned. No API contract or DB schema change. Highest value, lowest risk.

## A1. Tenant-scope correction — `BankAccountPermissionService.findAll()` + in-Java filter

| Field | Detail |
|---|---|
| **Title** | Replace whole-table `findAll()` + in-Java `bizKey` filter with a tenant-scoped query |
| **Severity** | **High** (cross-tenant data exposure shape, not just performance) |
| **Effort** | Small (~½ day incl. test) |
| **Affected files** | `domain/setting/bankaccount/.../BankAccountPermissionService.java:98`, `:194`; `BusiAdmnInfmRepository` (add `findAllByBizKey`) |
| **API impact** | None — response shape unchanged; only the data set is correctly scoped to the caller's tenant |
| **WEB impact** | None — same payload; corrected to exclude other tenants' admins |
| **DB impact** | None structural. Query changes from full-table scan to `WHERE biz_key = ?` (uses `biz_key`-leading PK; no new index needed) |
| **Risk if unchanged** | Every tenant's `BUSI_ADMN_INFM` rows are loaded across all tenants before filtering — multi-tenant leak shape + full-table scan that worsens with tenant count |
| **Recommended approach** | Add `List<BusiAdmnInfm> findAllByBizKey(String bizKey)` to the repository; replace both `findAll().forEach(... if bizKey.equals ...)` sites. Mirrors the already-completed fix recorded at `BusiInfmRepository.java:17` / `ExchangeRateBatchService.java:495` — these two sites are survivors of that same anti-pattern. |
| **Rollback** | Revert the two call sites and remove the new repository method; behavior returns to prior (broader) scan. No data migration involved. |

## A2. N+1 reduction — HIGH per-element reads

| Field | Detail |
|---|---|
| **Title** | Convert per-element `findById` / `findBy…` loops to batch `findAllById` / `…In(:list)` |
| **Severity** | **High** (latency scales linearly with input list size) |
| **Effort** | Medium (~1–1.5 days for all 4 sites incl. tests) |
| **Affected files** | `domain/payment/overseas/.../OverseasPaymentService.java:790` (per `ovrsPymnNos()`); `domain/banking/.../BankTransactionSyncService.java:180` (per account); `domain/einvoice/.../EInvoiceScrapeService.java:150` (per scraped invoice); `domain/setting/bankaccount/.../SettingsBankAccountService.java:143` (~4 `AcctAuthInfo` calls/row) |
| **API impact** | None — request/response contracts unchanged; internal query strategy only |
| **WEB impact** | None |
| **DB impact** | None structural. Fewer round-trips (N → 1 via `IN`/`findAllById`). PK predicates already supported. |
| **Risk if unchanged** | On large user-supplied lists / scrape payloads, one DB round-trip per element causes latency spikes and connection pressure |
| **Recommended approach** | Use existing batch patterns as templates — the `OvrsPymnSumr`/`ScdulPymnSumr` `…IN :apprSeqNos` repository methods are already the batch replacement for exactly this. For `SettingsBankAccountService:143`, batch-resolve the V+W permission labels once for the page rather than 4 calls per row. Preserve result ordering where the UI relies on it. |
| **Rollback** | Revert each site independently to the per-element loop; the batch methods can remain (additive, harmless). |

## A3. N+1 / write-amplification — MEDIUM per-row `save()` loops

| Field | Detail |
|---|---|
| **Title** | Switch per-row `save()` loops to `saveAll()` / JDBC batch where upload volume warrants |
| **Severity** | **Medium** (acceptable at current scale; degrades on large uploads) |
| **Effort** | Medium (~1 day; gated by whether JDBC batching is enabled) |
| **Affected files** | `VendorPaymentService.java:489`, `ExpensePaymentService.java:486`, `InternalTransferService.java:554`, `CardTransactionsService.java:829` & `:1183`, `TransactionService.java:490`/`:559`/`:631` |
| **API impact** | None |
| **WEB impact** | None |
| **DB impact** | None structural. Reduces statement count via batched inserts/updates. Key generation must still funnel through `wb_get_next_val` (`project_wb_get_next_val_funnel.md`) — do not replace per-row key gen with identity columns. |
| **Risk if unchanged** | Large batch payments/card-history uploads issue one `INSERT`/`UPDATE` per row; slow but correct |
| **Recommended approach** | Replace `for (...) repo.save(x)` with `repo.saveAll(list)` only where rows are independent. Verify `spring.jpa.properties.hibernate.jdbc.batch_size` is set before claiming a win. Leave bounded loops (e.g. `ApprovalBoxService.java:478`, capped by `MAX_BULK_SIZE` with per-row savepoints) untouched — that per-row isolation is legacy-faithful by design. |
| **Rollback** | Revert to per-row `save()`; functionally identical. |

## A4. Unused repository review

| Field | Detail |
|---|---|
| **Title** | Review & remove (or wire) 4 repositories with zero references in `src` |
| **Severity** | **Low** (dead code) |
| **Effort** | Small (~½ day, mostly verification) |
| **Affected files** | `domain/banking/repository/DailyCashReptDailySumrDetlRepository.java`; `domain/auth/repository/EmplInfmRepository.java`; `domain/payment/dispatch/repository/TbBatchScheduleInfoRepository.java`; `domain/payment/dispatch/repository/TbBatchScheduleDtlRepository.java` |
| **API impact** | None |
| **WEB impact** | None |
| **DB impact** | None — repositories are removed/added in code only; their inventory tables stay in the baseline ("Unused ≠ removable") |
| **Risk if unchanged** | Minor: dead interfaces obscure which tables are actually live; low maintenance cost |
| **Recommended approach** | **Verify before deleting.** `EmplInfmRepository` likely stays as-is because `EMPL_INFM` rows are created via the manual DBA SQL path (consistent with "account creation is manual SQL only") — keep it if a near-term feature needs it, otherwise remove. The two `TbBatchSchedule*` repos relate to the batch module (`project_batch_refactor_real_impl.md`) which is flag-gated/inert — confirm with the batch owner whether they are pending wiring before removal. |
| **Rollback** | Restore the deleted interface file(s) from VCS; no schema or data effect. |

## A5. DEV-only DB drift reconciliation — `busi_infm_init_data_tg`

| Field | Detail |
|---|---|
| **Title** | Reconcile the DEV-only trigger function `busi_infm_init_data_tg` with the live-JEX target |
| **Severity** | **Medium** (DEV env diverges from the real UAT/PROD target) |
| **Effort** | Small (investigation + decision) |
| **Affected files** | None in application code. DB object: trigger function `busi_infm_init_data_tg` present in `wecmsdevdb`, **absent** in `wecmsvndev` (JEX). |
| **API impact** | None |
| **WEB impact** | None |
| **DB impact** | DEV-only. Because UAT/PROD run against the live JEX DB, this trigger will not exist there — DEV behavior (e.g. tenant init on `busi_infm` insert) can mask a gap that only appears in the real target. |
| **Risk if unchanged** | Code that implicitly relies on the DEV trigger may break in UAT/PROD where the trigger is absent; or, conversely, the trigger duplicates logic the app already performs |
| **Recommended approach** | Determine origin: (a) if it replicates legacy init logic the app now owns in Java (`wb_init_busi_data` is already called via `LegacyFunctionRepository`), drop it from DEV so DEV matches the target; (b) if it is genuinely required, raise as a JEX DDL change with the PO/DBA — do not let it live only in DEV. Document the decision in `docs/database/`. |
| **Rollback** | Trigger is DB-side and DBA-managed; recreate from the captured definition if removed in error. No app code rollback needed. |

---

# Phase B — Contract Changes

Refactor-owned but touches the API wire format and/or public identifiers. Per CLAUDE.md §4, API and WEB are refactored together — every item below names the paired WEB change.

## B1. DTO naming consistency — camelCase export DTOs → snake_case

| Field | Detail |
|---|---|
| **Title** | Bring export DTO wire names into the lowercase snake_case convention |
| **Severity** | **Medium** (violates the global wire-format rule `project_api_naming_lowercase.md`) |
| **Effort** | Small–Medium (coordinated API+WEB change + test) |
| **Affected files** | `common/export/ExcelColumnSpec.java:41`/`:44`/`:46` (`@JsonProperty("fieldId"/"ccyCd"/"widthChars")`); `common/export/ExcelExportRequest.java:72`/`:80`/`:88`/`:93` (`"screenId"/"dymAreaId"/"fileName"/"subHeader"`) |
| **API impact** | **Breaking wire change:** `fieldId→field_id`, `ccyCd→ccy_cd`, `widthChars→width_chars`, `screenId→screen_id`, `dymAreaId→dym_area_id`, `fileName→file_name`, `subHeader→sub_header`. These are WeCMS-internal export bodies (not vendor) so the snake_case rule applies. |
| **WEB impact** | **Required matching change** — every `wecms-web` caller posting these export request bodies must send the new snake_case keys. Identify all View-Setting / export-trigger call sites before flipping. |
| **DB impact** | None — these DTOs do not map to DB columns |
| **Risk if unchanged** | The only non-vendor camelCase-on-the-wire cluster in the codebase; inconsistent contract surface that contradicts the CTO directive and complicates future client generation |
| **Recommended approach** | Remove the explicit `@JsonProperty` overrides so the global `PropertyNamingStrategies.SNAKE_CASE` (`config/JacksonConfig.java:25`) applies, **or** set them explicitly to the snake_case value. Land the API and WEB changes in the same MR; note the paired change in the MR description. Do **not** touch the EFS/Coocon/NXiSAS vendor DTOs (`dispatch/van/`, `dispatch/callback/`, `/nxisas/gateway/`) — vendor casing is mandatory (`feedback_vendor_dto_casing.md`). |
| **Rollback** | Re-add the `@JsonProperty` camelCase values on the API side and revert the WEB keys together; contract returns to current state. |

## B2. Nxsias/Nxisas naming cleanup

| Field | Detail |
|---|---|
| **Title** | Fix transposed `Nxsias*` class identifiers to `Nxisas*` |
| **Severity** | **Medium** (grep/maintenance hazard; inconsistent with package + 75 files) |
| **Effort** | Small (mechanical rename + recompile) |
| **Affected files** | `domain/banking/nxisas/audit/entity/NxsiasModuleTranLogHis.java`, its `…HisId.java`, and `domain/banking/nxisas/audit/NxsiasModuleTranLogHisRepository.java` |
| **API impact** | None — class names are internal; no REST path or DTO field references `Nxsias` |
| **WEB impact** | None |
| **DB impact** | **None — do NOT rename the table.** The legacy table is genuinely spelled `nxsias_module_tran_log_his` in the JEX DDL; the `@Table(name=...)` must keep mirroring that misspelling. Only the **Java class/identifier** spelling changes. |
| **Risk if unchanged** | Developers searching `Nxisas` miss this audit triplet; future code may introduce a second (correct) spelling and fragment the audit logger |
| **Recommended approach** | Rename the 3 classes `Nxsias*`→`Nxisas*` and update all references (IDE-safe rename). Keep `@Table(name = "NXSIAS_MODULE_TRAN_LOG_HIS")` and the `@Synchronize` list in `FirmBankingHistoryView` exactly as-is. Add a one-line comment noting the table name's legacy misspelling is intentional. |
| **Rollback** | Revert the rename commit; no schema/data effect. |

## B3. Field-vs-column name divergence (verify-first)

| Field | Detail |
|---|---|
| **Title** | Decide whether divergent Java field names should align to their legacy columns |
| **Severity** | **Low–Medium** (readability vs. an existing — possibly intentional — wire contract) |
| **Effort** | Medium (investigation-heavy; per-field decision + paired WEB change if renamed) |
| **Affected files** | `setting/users/entity/UserMenuAuthInfm.java:58` (`WEB_SVC_ID`→`menuAuthCd`); `payment/entity/OvrsPymnSumrDetl.java:56`/`:62`/`:71`/`:91` (`TRNF_CCY_CD→ccyCd`, `BNFC_BANK_NAME→bnfcBankNm`, `BNFC_BANK_BIC→bicCd`, `CHART_ACCT_CD→rprtAcctCd`); `banking/entity/BankExchRateInfm.java:95`/`:126` (`EXCH_RATE_REG_DT→exchRateRegDtm`, `REG_ID→regUser`); `dispatch/entity/TbPayFileData.java:81` & `internaltransfer/entity/ScdulPymnSumrDetl.java:82` (`WDRL_ACCT_RMRK→wdrlActRmrk`) |
| **API impact** | **Potentially breaking.** The wire name derives from the Java field via Jackson SNAKE_CASE — e.g. field `ccyCd` ⇒ wire `ccy_cd` (not `trnf_ccy_cd`). Renaming a field to match its column changes the wire key. |
| **WEB impact** | Paired change required for any field actually renamed (consumers reading the affected keys). |
| **DB impact** | None — columns stay as legacy DDL; only Java field identifiers (and thus wire keys) would change. |
| **Risk if unchanged** | Low. Maintainers cross-referencing the JEX DDL may be confused by the field/column mismatch, but behavior is correct. |
| **Recommended approach** | **Do not bulk-rename.** Treat as documentation-first: confirm whether each divergent field name is a deliberate DTO contract (the wire key may already be relied upon by WEB) or accidental. Only align the ones that are clearly accidental AND have a coordinated WEB change. Default to leaving them and documenting the mapping. |
| **Rollback** | Per-field revert; pair with WEB revert for any renamed field. |

---

# Phase C — PO Decision Required

Do **not** implement. These items either increase/alter legacy-parity behavior or touch the legacy DB structure. Each needs an explicit PO (Mr. Jung) decision first.

## C1. Duplicate payment-query consolidation

| Field | Detail |
|---|---|
| **Title** | Consolidate mirrored queries across `OvrsPymnSumr` / `ScdulPymnSumr` (and the 4-table chartAcct scan) |
| **Severity** | **Low** (drift risk between modules, not a defect) |
| **Effort** | Medium–Large (design a shared projection + regression-test both modules) |
| **Affected files** | `OvrsPymnSumrRepository.java:37-41`/`:51-55`/`:69-76`; `ScdulPymnSumrRepository.java:459-460`/`:477-478`/`:495-502`; chartAcct scan: `AcctTrnsHstrRepository.java:115-120`, `CardTrnsHstrRepository.java:268-273`, `OvrsPymnMstrRepository.java:45-50`, `ScdulPymnSumrDetlRepository.java:146-162`; link/unlink pairs: `AcctTrnsHstrRepository.java:83-91`/`:239-249`/`:324-334`, `ScdulPymnSumrDetlRepository.java:85-95`/`:109-119`; `ExptResolMstrRepository.java:82-88` vs `:99-100` |
| **API impact** | Potentially — if a shared projection changes a response shape; must be assessed per cluster |
| **WEB impact** | Potentially — paired with any contract change |
| **DB impact** | None structural; query text only |
| **Risk if unchanged** | The two payment modules can drift apart over time (one fixed, the other not). But the duplication is **deliberate legacy-parity mirroring** — consolidation trades parity-safety for DRY. |
| **Recommended approach** | **Escalate, do not edit.** Present the clusters to the PO. If approved, extract a shared `@Query` projection/interface and apply to both modules in one MR with parity tests against JEX for each module. Because this touches both API and WEB surfaces per CLAUDE.md §4, the PR must note the paired changes. Also confirm whether `ExptResolMstrRepository.java:82-88` and `:99-100` are genuinely both needed (one may be redundant). |
| **Rollback** | Revert to the per-module duplicate queries (the safe legacy-parity state). |

## C2. Legacy DB schema / index discussions

| Field | Detail |
|---|---|
| **Title** | Decide on PK-less tables, redundant index, and absent secondary indexes |
| **Severity** | **Low now / grows in PROD** (DEV data volumes are tiny; live JEX PROD tables are large) |
| **Effort** | N/A until approved (then manual-DBA-DDL path) |
| **Affected DB objects** | PK-less: `busa_doc_rcv_pay_his`, `busi_env_set_mdtr`, `nxsias_module_tran_log_his` (565 rows), `pgro_bsac_lnkn_his`. Redundant: `idx_trco_infm` (duplicates `trco_infm` PK on `trco_key`). General: near-total absence of secondary/tuning indexes (one PK per table). |
| **API impact** | None |
| **WEB impact** | None |
| **DB impact** | **This IS a legacy DB schema change — the highest-sensitivity bucket.** All of these characteristics mirror JEX exactly; changing them is structural drift from legacy. |
| **Risk if unchanged** | Queries filtering on non-PK columns (status, date ranges, `user_id` where not key-leading) lack supporting indexes; on large PROD tables this is slow. Conversely, adding indexes/PKs diverges from JEX and could affect live-JEX coexistence and data migration. |
| **Recommended approach** | **Escalate to PO + DBA.** Do not add a PK, add a secondary index, or drop `idx_trco_infm` unilaterally. If an index is justified by a measured slow query, deliver it as a **manual DBA-run SQL script** under `Claude Code (AI)/sql/` (per CLAUDE.md §2 — Flyway is disabled, `ddl-auto: validate`), documenting run order and the legacy-coexistence impact. Note the stray `flyway_schema_history` (1 row) in the refactor DB despite Flyway being disabled — confirm it is inert. |
| **Rollback** | Any approved index is `DROP INDEX` reversible; a PK addition is harder — assess migration impact before approving. Default state = leave matching JEX. |

---

## Legacy-inherited items — explicitly NOT scheduled

These are recorded for awareness only. They mirror JEX and must not be changed without a PO decision (most are covered by C2 above):

- **5 DB functions unused by Java** — `wb_get_efiling_tax_type`, `wb_get_napas_bank_nm_or_citad_bank_nm_v2`, `wb_get_prjt_nm`, `wb_insert_daily_fincl_rept_daily_cash_sumr_data`, `rept_0015_001_r001_rec3`. May be invoked internally by other DB functions; they exist in JEX. **No action** — do not drop.
- **52 inventory tables with no entity/repository** — refactor coverage gap, not dead tables. "Unused ≠ removable" (CLAUDE.md §1). Port on demand as features require; never drop.
- **Single-index table pattern** — inherited from JEX; see C2.

---

## Suggested execution order

1. **A1** (tenant-scope correctness) — do first; it is a correctness issue, not just perf.
2. **A2** (HIGH N+1 batching).
3. **A5** (DEV trigger reconciliation) — clears a DEV/PROD-target divergence cheaply.
4. **A4** (unused repository review) and **A3** (per-row save loops) — low risk, do when convenient.
5. **B2** (Nxsias rename) — mechanical, no contract risk.
6. **B1** (export DTO snake_case) — coordinated API+WEB MR.
7. **B3** (field divergence) — verify-first; likely document-only.
8. **C1**, **C2** — PO review sessions; no edits until decided.

Phases A and B are refactor-owned and safe under `feedback_preserve_existing_code.md` (change only what is broken, never adjacent completed items). Phase C and all legacy-inherited items require a PO decision before any change because they affect legacy structural parity and JEX → refactor data-migration safety.
