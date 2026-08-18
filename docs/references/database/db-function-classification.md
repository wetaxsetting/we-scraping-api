# DB Function Classification & Migration Priority — wecms-api


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](flyway-migration-baseline.md) §5.

**Scope:** wecms-api/ (Needs Confirmation) (Spring Boot 3.5.13, Java 21, Flyway, single tenant `VN_VNM0004_000001`).
**Status:** Plan only — no code changes, no Flyway migrations, no DB writes.
**Companion plan:** task-plan-common-backend-functional-seahorse.md _(removed — historical `planning/` folder deleted by project decision)_ — bootstrap & `wb_get_next_val` strategy, Phase 1/2/3 framing reused below.

---

## Context

The legacy WeCMS PostgreSQL schema `wecmsvndev` ships **~165 PL/pgSQL functions**. The seahorse plan already covers a tiny subset (`wb_get_next_val`, `hash`, `encrypt`) for the bootstrap path. To plan the rest of the JEX → Spring refactor we need a single inventory that says — for **every** function — whether it stays in the database, gets wrapped behind a Spring repository, or gets reimplemented in Java, plus a concrete priority and recommended Spring component name.

This document is that inventory. It feeds two downstream decisions:

1. **What can `wecms-api` ignore today?** Triggers and reject-functions stay invisible behind table writes; the refactor never imports them.
2. **What needs a Spring façade now vs. later?** Per-domain refactor order is gated by which function groups each module touches, so this plan ranks the groups by priority and risk.

The classification pattern follows the user's brief: **LOG/TRIGGER**, **SYSTEM (`wb_*`)**, **REPORT (`rept_*`/`trco_*`)**, **UTILITY (`fn_*` + helpers)**, **API-like (`comm_*`/`mbmr_*`/`admn_*`)**.

---

## 1. Function Inventory Summary

| Group | Pattern | Count | Default strategy | Default priority |
|---|---|---:|---|---|
| 1. LOG / TRIGGER | `*_log`, `*_logtrigger`, `reject_*`, `sync_*` | **62** | **KEEP in DB** — never call from Spring | LOW |
| 2. SYSTEM business | `wb_get_*`, `wb_format_*`, `wb_init_*`, `wb_insert_*`, `wb_pcsn_*` | **59** | Phase 1 wrap → Phase 2 service → Phase 3 Java | mixed |
| 3. REPORT | `rept_*`, `trco_*`, `get_cashflow_report`, `get_chart_acct_hierarchy`, `fn_get_haengsung_*` | **33** | KEEP in DB; call via native query from `…ReportRepository` | MEDIUM |
| 4. UTILITY | `fn_*`, `kr_to_vn_time`, `now_to_char`, `remove_accents` | **9** | Classify simple → Java; complex → wrap | mixed |
| 5. API-like procedures | `comm_*`, `mbmr_*`, `admn_*` | **5** | Migrate to Spring service when owning module is refactored | MEDIUM |
| **TOTAL** | | **~168** | | |

(Counts include overloads counted separately. `trco_0004_002_r001_rec_backup` and the two `migarate_*` / `fn_migrate_*` one-shots are inventoried but flagged as **drop candidates**.)

---

## 2. Group 1 — LOG / TRIGGER Functions (KEEP in DB)

**Common shape:** zero arguments, return type `trigger`, written in plpgsql; fired by `BEFORE`/`AFTER` `INSERT`/`UPDATE`/`DELETE` row triggers on the matching base table. Each one writes a history/audit row to a `*_HIS` shadow table or rejects the operation outright.

**Strategy:** **Do not migrate.** The Spring service writes to the base table; the DB trigger writes the history row. This preserves the existing audit trail with zero Spring changes and survives parallel writes from any remaining JEX path. They become invisible to the application layer.

**Recommended Spring component:** *None.* Verify the trigger still fires in `…RepositoryIT` (insert one row, assert the corresponding `*_HIS` row appears) — that is the only Spring-side cost.

| Sub-pattern | Examples (representative) | Count | Risk note |
|---|---|---:|---|
| Audit/history triggers | `acct_mstr_log`, `acct_trns_hstr_log`, `busi_infm_log`, `user_infm_log`, `empl_infm_log`, `trco_infm_log`, `saly_book_mstr_log`, `tb_pay_file_mst_log`, `push_device_mstr_log` | 55 | **Low.** Idempotent on row writes. Risk only if the JPA entity skips a column the trigger expects. Mitigation: `ddl-auto=validate` already catches schema drift; verify trigger DDL is preserved when the local DB is rebuilt from V1–V10 + V5 snapshot. |
| Reject triggers | `reject_not_existing_bank_cd`, `reject_not_existing_dp_acct_bank_cd`, `reject_not_existing_issu_bank_cd`, `reject_not_existing_loan_acct_bank_cd`, `reject_not_existing_rcv_acct_bank_cd` | 5 | **Low.** Surface as `DataIntegrityViolationException`; let `GlobalExceptionHandler` map to a `BusinessException("WBExxxx")` once a real flow hits one. |
| Sync triggers | `sync_acct_trns_hstr_to_rept` | 1 | **Medium.** Cross-table denormalization into report tables — if the report table schema diverges in the refactor, this trigger silently fails or duplicates. Audit before the report module ships. |
| Special-cased name | `trco_rspb_tax_agent_logtrigger` (only `*_logtrigger` suffix, behaves the same) | 1 | **Low.** Same as audit triggers. |

**Phase fit:** Phase 1 — no change. Phase 2 — keep. Phase 3 — keep (no plan to retire DB-side audit during R&D).

---

## 3. Group 2 — SYSTEM / Business Functions (`wb_*`)

The `wb_*` family is the largest and most heterogeneous. Split into four sub-groups by behavioural shape — strategy and priority differ per sub-group.

### 3.1 `wb_get_*` — Lookup / status / formatting (52 functions)

**Common shape:** small inputs (codes, IDs, language flag), single-value output, no side effects. Functionally equivalent to a SELECT-with-CASE wrapped in a function — used inline from JEX IDOs to denormalize codes into display names.

**Strategy:**
- **Phase 1:** call via `LegacyFunctionRepository.scalar(...)` from the new domain repositories so JEX IDO behaviour is preserved bit-for-bit.
- **Phase 2:** wrap each cluster in a per-domain `…LookupService` inside `domain/{module}/service/`.
- **Phase 3:** when the related code-table data has been mirrored into JPA, replace the native call with a Java cache-backed lookup and drop the SQL function.

**Risk note:** Many of these embed translation logic (`p_lang_code`) that the Spring side already owns through `MessageResolver` + `LangCd`. Reusing `MessageResolver` instead of duplicating the lang switch removes ~40 % of the `wb_get_*` code paths in Phase 3.

| Cluster | Example functions | Spring component (Phase 2) | Priority |
|---|---|---|---|
| Bank-name lookups | `wb_get_bank_nm` (1-arg + 2-arg), `wb_get_bank_name_any_code`, `wb_get_bank_format`, `wb_get_napas_bank_nm` (1-arg + 3-arg), `wb_get_napas_bank_nm_or_citad_bank_nm`, `wb_get_napas_bank_nm_or_citad_bank_nm_v2`, `wb_get_napas_or_citad_bank_nm`, `wb_get_napas_or_citad_bank_nm_v2` | `domain/banking/service/BankLookupService` | **HIGH** — every banking screen depends on bank-name resolution; the `*_v2` overloads suggest historical correctness fixes that Java should consolidate |
| Account / chart-of-accounts | `wb_get_acct_type_nm`, `wb_get_acct_result_stts`, `wb_get_chart_acct_level`, `wb_get_chart_acct_level_text`, `wb_get_chart_acct_lv_nm`, `wb_get_chart_acct_nm`, `wb_get_erp_acct_nm`, `wb_get_vas_acct_nm` | `domain/accounting/service/ChartOfAccountsService` (+ `AccountTypeLookupService`) | **HIGH** |
| Approval / status names | `wb_get_appr_stts`, `wb_get_expt_resol_mstr_doc_nm`, `wb_get_user_dv_nm`, `wb_pcsn_stts_nm`, `wb_get_operat_stts` (`SETOF text`), `wb_get_prch_stts_nm`, `wb_get_purchase_status_to_code`, `wb_get_trco_oprt_stts` | `domain/approval/service/ApprovalStatusService` + `domain/purchase/service/PurchaseStatusService` | **MEDIUM** |
| Trading-company (TRCO) | `wb_get_trco_nm` (2-arg + 3-arg), `wb_get_normalized_trco_nick_nm` | `domain/trco/service/TradingCompanyLookupService` | **MEDIUM** |
| User / admin / security | `wb_get_admin_yn`, `wb_get_user_nm` (1-arg + 2-arg) | `domain/auth/service/AuthorizationService` (admin check), `domain/auth/service/UserLookupService` | **HIGH** — `wb_get_admin_yn` is security-relevant; replace with a Java `@PreAuthorize` rule before any non-LOCAL deploy |
| E-invoice / tax | `wb_get_efiling_tax_type`, `wb_get_einv_orig`, `wb_get_einv_stts`, `wb_get_invoice_status`, `wb_get_tax_rate`, `wb_get_wht_nm` | `domain/einvoice/service/EInvoiceLookupService`, `domain/tax/service/WithholdingTaxService` | **MEDIUM** |
| Memo / annotation | `wb_get_memo_color`, `wb_get_memo_cont` (each takes 9 ref-no params!) | `domain/memo/service/MemoLookupService` | **LOW** — wide-arity APIs are awkward to expose; defer until a feature actually consumes them |
| Project / usage / project-name | `wb_get_prjt_nm`, `wb_get_usag_nm`, `wb_get_usag_nm_new` | `domain/project/service/ProjectLookupService`, `domain/code/service/UsageCodeLookupService` | **MEDIUM** |
| Salary / payroll computations | `wb_get_net_salary`, `wb_get_totl_salary`, `wb_get_tot_pay_amt` | `domain/salary/service/SalaryComputationService` | **LOW** — payroll module not yet in refactor scope; keep as native call until the module lands |
| Currency / amount formatting | `wb_get_amt` | `common/format/AmountFormatter` (or extend `MessageResolver`) | **MEDIUM** — pure function, easy Java replacement |
| Translation / normalization | `wb_get_translate_text`, `wb_get_normalized_rmrk` | `common/i18n/TranslationService`, `common/util/RemarkNormalizer` | **LOW** — `wb_get_translate_text` overlaps with `MessageResolver`; consolidate, do not duplicate |
| Composite/JSON-returning | `wb_get_aut_cls_acct_trns_rmrk_infm` (returns `jsonb`), `wb_get_bank_cd_usage_cd` (returns `varchar[]`), `wb_get_non_coll_cond_daily_sumr_data` (returns `TABLE`) | `domain/banking/service/AutoClassificationService`, `domain/banking/service/BankUsageResolver`, `domain/report/service/NonCollectionDailySummaryService` | **MEDIUM** — keep as native query in Phase 1 (JSONB/array/TABLE marshalling is fiddly); reimplement in Phase 3 |
| Sequence generation | `wb_get_next_val` (~40 internal branches) | Already covered: `LegacyFunctionRepository` → `BusinessKeyGeneratorService` / `EmployeeNumberGeneratorService` (per seahorse plan) | **HIGH** — already on Phase 1 critical path |

**Risk note (group-wide):** A handful of `*_v2` overloads coexist with their pre-`v2` siblings — e.g. `wb_get_napas_or_citad_bank_nm_v2` and `wb_get_napas_or_citad_bank_nm`. **Action:** before wrapping, run `pg_get_functiondef` on each pair, decide which is canonical, and stop calling the dead one from Spring (do not delete the SQL function in Phase 1; just route Spring traffic to the survivor and mark the loser `@Deprecated` in the wrapper).

### 3.2 `wb_format_*` — Formatting (1 function)

| Function | Strategy | Spring component | Priority | Risk |
|---|---|---|---|---|
| `wb_format_acct_nick_nm(acct_nick_nm, bank_cd, acct_no, hand_reg_yn)` | Phase 2 — reimplement in Java; the rule (truncate / prefix / fall back) is small and unit-testable | `domain/banking/util/AccountNicknameFormatter` | **MEDIUM** | **Low** — pure function, no I/O. Watch for trailing-space conventions. |

### 3.3 `wb_init_*` — First-login / first-tenant default-data seeders (3 functions)

**Important correction:** These are **not** the bootstrap row-creators for `BUSI_INFM` / `EMPL_INFM` / `USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_ADMN_INFM`. The 5-table chain stays in `CompanyBootstrapService` / `UserBootstrapService` (per seahorse plan §4–§5). The `wb_init_*` family seeds **default config rows** that the legacy login/feature paths expect once the company already exists. Each is idempotent (uses `WHERE NOT EXISTS`).

Verified via `pg_get_functiondef` on the JEX DEV DB:

- `wb_init_busi_data` reads `tax_id FROM busi_infm WHERE biz_key = p_biz_key` (i.e. `BUSI_INFM` must already exist), then copies `SYS_ENV_SET_INFM` → `BUSI_ENV_SET_INFM`, `SYS_USAG_SET_INFM` (joined to `USAG_INFM`) → `BUSI_USAG_SET_INFM`, `USAG_INFM_LANG_DETL` → `BUSI_USAG_SET_INFM_LANG_DETL`, default trading-company groups (`TRCO_GRP_BSC_SYS` → `TRCO_GRP_BSC_CSTM`), and partner basic links (`PGRO_BSAC_LNKN_INFM`).
- `wb_init_user_data` copies `SYS_SCR_OPTION` → `USER_SCR_OPTION` filtered by `p_lgn_chnl` (`PC` excludes `WABOOKS_MREC_R004`; `MOB` keeps only `WABOOKS_MREC_R004`). The `WHERE NOT EXISTS` is keyed on `(biz_key, web_svc_id)` only — so it seeds **per tenant**, not per user.
- `wb_init_erp_usag_data` (definition not yet inspected) follows the same shape from its name: seeds default ERP usage codes per tenant.

| Function | What it actually does | Strategy | Spring component | Priority | Risk |
|---|---|---|---|---|---|
| `wb_init_busi_data(biz_key, reg_id, reg_dtm)` | Seeds default tenant config (env settings, usage codes + lang labels, default trading-company groups, partner basic links) **after** `BUSI_INFM` exists. Idempotent. | **Phase 1 — keep as DB function.** `CompanyBootstrapService.bootstrap(...)` calls it via `LegacyFunctionRepository.call("wb_init_busi_data", bizKey, regId, regDtm)` **after** the 5-table chain commits. Phase 2/3 — re-examine; only port to Java once the `SYS_*`/`BUSI_*_INFM` tables are mirrored in JPA. | `domain/tenant/service/TenantDefaultsSeederService` (new package) — wraps the call; not part of the 5-table TX | **MEDIUM** | **Medium** — required for any tenant to have working env/usage settings. Today the seahorse plan does not invoke it; add the call after `CompanyBootstrapService` saves `BUSI_INFM`. Idempotency makes the call safe on re-runs. |
| `wb_init_user_data(lgn_chnl, biz_key, reg_id, reg_dtm)` | First-login default-screen-option seeder per `(biz_key, channel)`. Idempotent. **Not** a user-creation function. | **Phase 1 — keep as DB function.** Call only when the legacy login flow requires it (i.e. when a screen reads `USER_SCR_OPTION` and finds nothing). The current Sprint 0 login does not touch `USER_SCR_OPTION`, so leave un-wired until the first screen-option-consuming feature lands. Phase 2/3 — analyze the function definition before moving to Java. | `domain/preferences/service/UserScreenOptionSeederService` (new) — invoked from `AuthService.login` **only** when first-login-per-tenant is detected, or eagerly at the end of `CompanyBootstrapService` (TBD when the consuming screen lands) | **MEDIUM** | **Low–Medium** — idempotent; failure to run only causes empty per-user screen options, no data corruption. Decision deferred to whenever a screen actually reads from `USER_SCR_OPTION`. |
| `wb_init_erp_usag_data(biz_key, reg_id, reg_dtm)` | Seeds default ERP usage codes per tenant (presumed; verify via `pg_get_functiondef` before wiring). | **Phase 1 — keep as DB function.** Wire into `TenantDefaultsSeederService` next to `wb_init_busi_data` when the ERP/accounting module starts being consumed. Phase 2/3 — port to Java alongside the ERP/usage JPA model. | `domain/tenant/service/TenantDefaultsSeederService` (same as above) | **LOW** | **Low** — only matters when the accounting module ships; defer until then. |

**Phase fit:** Phase 1 = wrap, do not migrate. Phase 2 = analyze each function definition individually; small enough that a single sprint can port them. Phase 3 = drop SQL functions once their default rows are seeded by Flyway or Spring.

### 3.4 `wb_insert_*` — Pre-aggregated report writers (2 functions)

| Function | Strategy | Spring component | Priority | Risk |
|---|---|---|---|---|
| `wb_insert_daily_fincl_rept_daily_cash_sumr_data(biz_key, req_date, req_date2)` | Phase 1 — call via `…ReportRepository.refreshDailyCashSummary(...)` native query; Phase 3 — reimplement as a Spring `@Scheduled` job once we own the report flow | `domain/report/service/DailyFinancialReportService` | **MEDIUM** | **Medium** — these write into report tables; double-execution = double rows. Idempotency is the responsibility of the function (verify before invoking from Spring). |
| `wb_insert_daily_fincl_rept_daily_sumr_detl_data(biz_key, req_date, req_date2)` | Same | Same | **MEDIUM** | Same |

---

## 4. Group 3 — REPORT Functions (33 functions)

**Common shape:** named `{module}_{screen-id}_{rec…}`, takes `(biz_key, date-range, …)`, returns `TABLE(...)`. Direct Spring rewrite is high-risk (every column ordering + numeric rollup is locked in by the legacy UI).

**Strategy:**
- **Phase 1 + 2:** Keep in DB. Each report becomes a `@Repository` method using `@Query(value = "SELECT * FROM {fn}(...)", nativeQuery = true)` returning a JPA-friendly DTO projection (Spring Data interface projection or `Tuple → record` mapper).
- **Phase 3:** Migrate one report at a time (driven by the screen refactor), starting with the smallest grain. Rewrite as `JPQL` + `CriteriaBuilder` only when there is a clear correctness reason — otherwise keep delegating to the function for stability.

**Risk note (group-wide):** The wide TABLE return types (often 20+ columns) make schema drift the dominant risk. Mitigation: a single `…ReportRowProjection` interface per function pinned to the function signature, regenerated from `pg_get_function_result(oid)` whenever the underlying function changes. Add a smoke test per report that asserts column count + first-row shape.

| Cluster | Functions | Spring component | Priority | Risk |
|---|---|---|---|---|
| Account-balance summary | `rept_0015_001_r001_rec1`, `rept_0015_001_r001_rec3`, `rept_0900_001_rec1..rec4` | `domain/report/repository/AccountSummaryReportRepository` → `AccountSummaryReportService` | **MEDIUM** | Medium — wide column lists; column order is API |
| Sales / purchase by trading company | `rept_0200_001_rec1`, `rept_0200_001_rec2` | `domain/report/repository/SalesPurchaseByTrcoReportRepository` | **MEDIUM** | Medium |
| Invoice/items detail (sales) | `rept_0300_001_osmc`, `rept_0300_001_rec1`, `rept_0300_001_rec2` | `domain/report/repository/SalesItemReportRepository` | **MEDIUM** | Medium |
| Invoice/items detail (purchase) | `rept_0400_001_osmc`, `rept_0400_001_rec1`, `rept_0400_001_rec2` | `domain/report/repository/PurchaseItemReportRepository` | **MEDIUM** | Medium |
| Receivable / payable aging | `rept_0500_001_rec1`, `rept_0600_001_rec1` | `domain/report/repository/ReceivablePayableReportRepository` | **MEDIUM** | Medium |
| Cost / profit by usage | `rept_0700_001_osmc`, `rept_0700_001_osmc_rec1..rec3`, `rept_0700_001_rec1..rec3` | `domain/report/repository/UsageCostReportRepository` | **LOW** | Low (read-only); defer until usage module ships |
| TRCO transaction & balance | `trco_0004_002_r001_bal`, `trco_0004_002_r001_rec`, `trco_0004_002_r001_tot` | `domain/trco/repository/TrcoBalanceReportRepository` | **MEDIUM** | Medium |
| TRCO group dashboard | `trco_0101_001_r001_rec`, `trco_0101_001_r002_rec` | `domain/trco/repository/TrcoGroupReportRepository` | **MEDIUM** | Medium |
| Cashflow (flagship dashboard) | `get_cashflow_report` (8-arg, 23-col output, optional bank filter) | `domain/report/repository/CashflowReportRepository` → `CashflowReportService` | **HIGH** | **High** — large signature + optional `text` bank filter (CSV) makes it the most likely to break under JPA marshalling; add `@Query` integration test before exposing |
| Chart-of-accounts hierarchy | `get_chart_acct_hierarchy` | `domain/accounting/service/ChartOfAccountsService.getHierarchy` | **HIGH** | Medium — recursion lives in plpgsql; native call is safest until we own the chart-of-accounts JPA model |
| Tenant-specific (Haengsung) | `fn_get_haengsung_daily_financial_report` | `domain/report/repository/HaengsungDailyFinancialReportRepository` (annotated `@Profile("tenant-haengsung")` if/when multi-tenant) | **MEDIUM** | **Medium** — reference deployment; do not generalize prematurely. Treat as a per-tenant override, not a default |
| Deprecated / drop candidate | `trco_0004_002_r001_rec_backup` | *None* | **LOW** | Confirm no remaining callers in JEX IDOs; flag for deletion in a Phase 3 cleanup migration |

---

## 5. Group 4 — UTILITY Functions (9 functions)

**Strategy split:** trivial pure functions → Java in Phase 2; complex/data-touching → keep as native call.

| Function | Behaviour | Strategy | Spring component | Priority | Risk |
|---|---|---|---|---|---|
| `fn_cleanup_rmrk(rmrk)` | Strip noise from a remark string | **Java** (Phase 2) — pure regex | `common/util/RemarkSanitizer` | **MEDIUM** | Low |
| `fn_regexp_escape(text)` | SQL-language wrapper escaping regex meta-chars | **Drop from Spring side** — use `java.util.regex.Pattern.quote(...)` | *None* | **LOW** | Low |
| `fn_random_suffix(trsc_dt)` | Append random suffix to a date | **Java** (Phase 2) — pure | `common/util/TransactionIdSuffixGenerator` | **LOW** | Low |
| `fn_gen_full_id(bank_cd, trsc_dt, trsc_tm, trsc_amt, inout_dv, rmrk, rmrk2)` | Composite ID generation | **Phase 1 wrap, Phase 3 Java** — algorithm needs to be pinned by sample data first | `domain/banking/util/BankTransactionIdGenerator` | **MEDIUM** | **Medium** — collision risk if rewritten naively |
| `fn_create_views_option(user_id, biz_key, tabname)` | Writes into per-user view-preferences table | **Phase 2 service** — reimplement as JPA `UPSERT` | `domain/preferences/service/UserViewPreferencesService.create` | **LOW** | Medium — has side effects; not a pure utility |
| `fn_read_views_option(user_id, biz_key, tabname)` | Read view preferences | **Phase 1 wrap; Phase 2 JPA query** | `domain/preferences/service/UserViewPreferencesService.read` | **LOW** | Low |
| `kr_to_vn_time(kr_time_str)` | Korea → Vietnam timezone conversion | **Java** (Phase 2) — `ZoneId` + `DateTimeFormatter`; trivial | `common/util/KoreaToVietnamTimeConverter` | **LOW** | Low |
| `now_to_char(num)` | Format `now()` to a fixed character pattern | **Java** (Phase 2) — replace with injected `Clock` + `DateTimeFormatter` | `common/util/TimestampFormatter` (or use `Clock` directly) | **LOW** | Low |
| `remove_accents(input_text)` | Strip diacritics (Vietnamese normalisation) | **Java** (Phase 2) — `Normalizer.normalize(s, NFD).replaceAll("\\p{M}","")` | `common/util/TextNormalizer` | **LOW** | Low — verify it matches the legacy mapping for Vietnamese-specific chars (đ → d) |

**One-shot data-migration helpers (do NOT migrate, drop after legacy retired):**

| Function | Strategy | Risk |
|---|---|---|
| `fn_migrate_bank_trns_ids` | One-shot migration; never call from Spring; drop in Phase 3 cleanup | Low |
| `migarate_trco_nick_nm(trco_nm, ccy_cd)` | Same; spelled "migarate" (legacy typo) | Low — useful only as a historical reference |

---

## 6. Group 5 — API-like DB Procedures (5 functions)

These are not lookups — they implement a feature in PL/pgSQL. Each maps onto a single Spring service method when the owning module is refactored.

| Function | Behaviour | Strategy | Spring component | Priority | Risk |
|---|---|---|---|---|---|
| `comm_0100_002_r001_rec(biz_key, user_id)` | Returns dashboard groups + counts | Phase 1 — native `@Query`; Phase 2 — service-orchestrated JPQL once `domain/dashboard` lands | `domain/dashboard/service/DashboardGroupCountService` | **MEDIUM** | Low — read-only, small result set |
| `comm_0900_001(biz_key, doc_id)` | `void` — likely a document-cleanup / cascade-delete | Phase 1 wrap; Phase 2 reimplement after auditing what tables it touches | `domain/document/service/DocumentCleanupService` | **HIGH** | **High** — unknown side effects; **must** read `pg_get_functiondef` and write a regression test before any Spring caller invokes it |
| `mbmr_0200_001(biz_key)` | `void` — member module init / reset | Phase 2 — fold into `domain/member/service/MemberBootstrapService` | `domain/member/service/MemberBootstrapService` | **MEDIUM** | Medium — bootstrap-class function; verify idempotency |
| `admn_get_bill_cnt(ntnl_cd, bill_item_cd, bill_ym, biz_key)` | Billing count for admin dashboard | Phase 1 wrap; Phase 2 service when `domain/admin` lands | `domain/admin/service/BillCountService` | **LOW** | Low |
| `admn_get_bill_prdt_type(prdt_type)` | Billing product type lookup | Phase 1 wrap; Phase 3 Java via static map | `domain/admin/service/BillProductTypeService` | **LOW** | Low |

---

## 7. Cross-Cutting Strategy

### 7.1 Phase alignment with the seahorse plan

| Phase | Group 1 (triggers) | Group 2 (`wb_*`) | Group 3 (reports) | Group 4 (utility) | Group 5 (API-like) |
|---|---|---|---|---|---|
| **Phase 1 — Skeleton** | Untouched | `wb_get_next_val` wrapped via `LegacyFunctionRepository`; rest of `wb_get_*` left as native calls inside the Spring repo of whichever module gets refactored next | All reports keep their native-query pattern; no Java rewrite | Wrap any utility that the bootstrap path touches; rest untouched | Wrap on first need only |
| **Phase 2 — Reimplementation** | Untouched | Per-cluster `…LookupService` services land in their domain packages; pure formatters move to Java; `wb_init_*` analyzed (definition-by-definition) and ported to `TenantDefaultsSeederService` / `UserScreenOptionSeederService` only when the consuming module ships; `wb_insert_*` becomes `@Scheduled` jobs | Largest reports (cashflow, chart-of-accounts hierarchy) get DTO projections + integration tests | Pure utilities all move to Java | `comm_0900_001` audited & wrapped or rewritten |
| **Phase 3 — Retirement** | Still untouched (no plan to move audit to Spring during R&D) | Drop `wb_get_*` SQL functions whose Spring replacement is verified; mark `LegacyFunctionRepository` methods `@Deprecated` as branches retire | Per-screen rewrites where the JPA model is rich enough | Drop the SQL utility functions whose Spring replacement is verified | Drop the SQL procedures |

### 7.2 Single funnel — `LegacyFunctionRepository`

Every native call to a surviving DB function goes through `com.kosign.wecms.common.legacy.LegacyFunctionRepository` (defined in the seahorse plan, §5.1). For this plan, extend its surface to:

```java
String  scalar(String fn, Object... args);                       // wb_get_*_nm and friends
<T> T   scalar(String fn, Class<T> type, Object... args);        // typed scalar (bigint, numeric)
<T> List<T> rows(String fn, Class<T> rowType, Object... args);   // rept_*, trco_*, comm_0100_002
void    call(String fn, Object... args);                         // wb_init_*, wb_insert_*, comm_0900_001, mbmr_0200_001
```

Rationale: a single grep target (`grep -r "legacyFunctions\." src/main`) is the migration tracker. **Do not** sprinkle `@Query(nativeQuery=true)` callers across repositories ad-hoc — every call routes through this façade until Phase 3 retirement makes it unnecessary.

### 7.3 What NOT to do

- **Don't preemptively rewrite triggers.** They are out of scope; the JEX schema's history tables depend on them.
- **Don't create a "FunctionService" god-class** that exposes every `wb_get_*` as a method. Each cluster lands in its own domain package — see the table in §3.1 for the boundaries.
- **Don't mix `MessageSource`/`LangCd` with `wb_get_translate_text`.** Pick `MessageSource`. Translation belongs in Spring i18n; the SQL function is a fossil.
- **Don't keep `*_v2` and pre-`v2` overloads alive in Java.** Pick the canonical one when the cluster lands in Phase 2; the loser stays in the DB but no Spring caller uses it.
- **Don't migrate one-shot `migarate_*`/`fn_migrate_*` helpers.** They are historical migration scripts, not runtime code.

### 7.4 Drop candidates (track for Phase 3 cleanup migration)

| Function | Reason |
|---|---|
| `trco_0004_002_r001_rec_backup` | Name contains `_backup` — superseded by `trco_0004_002_r001_rec` |
| `fn_migrate_bank_trns_ids` | One-shot migration |
| `migarate_trco_nick_nm` | One-shot migration (legacy typo "migarate") |
| Pre-`v2` overloads after `_v2` adoption | `wb_get_napas_or_citad_bank_nm`, `wb_get_napas_bank_nm_or_citad_bank_nm` (only after the `*_v2` siblings are wired up) |

These are **not** dropped in Phase 1 or Phase 2 — only flagged. Drop happens in a single Phase 3 cleanup Flyway migration.

---

## 8. Priority Summary (sorted)

### HIGH — touch in the next refactor sprint

| Item | Reason | Group |
|---|---|---|
| `wb_get_next_val` wrap | On the bootstrap critical path; already in seahorse plan | 2 |
| Company bootstrap chain (`BUSI_INFM` → `EMPL_INFM` → `USER_INFM` → `USER_BUSI_CONN_INFM` → `BUSI_ADMN_INFM`) | Already in seahorse plan; **does not** use any `wb_init_*` function — those are downstream defaults seeders | n/a (plain JPA inserts) |
| `wb_get_admin_yn` | Security-relevant authorization check | 2 |
| Bank-name lookups (`wb_get_bank_nm`, `wb_get_napas_*`, `*_v2` cluster) | Used by every banking screen | 2 |
| Chart-of-accounts cluster (`wb_get_chart_acct_*`, `get_chart_acct_hierarchy`) | Used by every accounting screen | 2 + 3 |
| `get_cashflow_report` | Flagship dashboard; hardest report to marshal | 3 |
| `comm_0900_001` | `void`-returning, unknown side effects — audit before any Spring caller invokes it | 5 |

### MEDIUM — wrap in Phase 1, reimplement when owning module is refactored

`wb_init_busi_data` (call after company creation; idempotent defaults seeder, not a row creator), `wb_init_user_data` (call only when a screen actually consumes `USER_SCR_OPTION`), `wb_format_acct_nick_nm`, `wb_get_amt`, `wb_get_acct_*`, approval/status clusters, TRCO lookups, e-invoice / tax lookups, project / usage lookups, JSON/array/TABLE-returning composite functions, all `rept_*` and `trco_*` reports, `wb_insert_daily_fincl_*`, `comm_0100_002_r001_rec`, `mbmr_0200_001`, `fn_cleanup_rmrk`, `fn_gen_full_id`.

### LOW — defer; minimal user impact during R&D

Salary computations (`wb_get_net_salary`, `wb_get_totl_salary`, `wb_get_tot_pay_amt`), memo lookups (`wb_get_memo_*`), translation/normalization helpers, view-preferences (`fn_*_views_option`), timezone/format utilities (`kr_to_vn_time`, `now_to_char`, `remove_accents`), admin-billing helpers (`admn_*`), Haengsung tenant-specific report, `rept_0700_*` cost reports, `fn_random_suffix`, `fn_regexp_escape`, `wb_init_erp_usag_data` (only matters when accounting module ships).

---

## 9. Verification

The plan itself is verified by sampling — pick one function per group and confirm:

1. **Trigger sample** — read `pg_get_functiondef('busi_infm_log'::regprocedure)` via the `postgres` MCP; confirm it writes to `BUSI_INFM_HIS` and matches the `BaseEntity` audit columns. Verify by inserting one row in `BUSI_INFM` from a test and asserting the `*_HIS` row appears.
2. **`wb_get_*` sample** — read `pg_get_functiondef('wb_get_admin_yn'::regprocedure)`; confirm it is a single-`SELECT` lookup with no side effects and no permission elevation.
2a. **`wb_init_*` already verified** (2026-04-27): `wb_init_busi_data` reads `tax_id` from existing `BUSI_INFM` then seeds `BUSI_ENV_SET_INFM`/`BUSI_USAG_SET_INFM`/`BUSI_USAG_SET_INFM_LANG_DETL`/`TRCO_GRP_BSC_CSTM`/`PGRO_BSAC_LNKN_INFM`. `wb_init_user_data` seeds `USER_SCR_OPTION` from `SYS_SCR_OPTION` filtered by login channel. Neither creates the bootstrap chain. Re-verify before porting to Java in Phase 2/3.
3. **Report sample** — read `pg_get_functiondef('rept_0900_001_rec1'::regprocedure)`; confirm column count matches `pg_get_function_result(oid)`; build a `RowProjection` for it and a smoke test.
4. **Utility sample** — read `pg_get_functiondef('remove_accents'::regprocedure)`; confirm `Normalizer.NFD` + diacritic strip is sufficient and check the Vietnamese-specific `đ → d` mapping.
5. **API-like sample** — read `pg_get_functiondef('comm_0900_001'::regprocedure)`; identify every table it writes to **before** any Spring caller invokes it.

Each sample queries metadata only — no DML, no `EXPLAIN ANALYZE`, no `DO $$` (per [.claude/rules/database.md](../../../../.claude/rules/database.md)).

---

## 10. Final Recommendation

Treat the 168 functions as a five-bucket portfolio rather than one big problem:

1. **Triggers stay invisible** — don't migrate, don't worry about them. They're 37 % of the function count and 0 % of the Spring effort.
2. **`wb_get_next_val`, the 5-table company-bootstrap chain, `wb_get_admin_yn`, bank-name lookups, chart-of-accounts, and `comm_0900_001`** are the only HIGH-priority items. The `wb_init_*` family is **not** part of bootstrap — it is downstream defaults seeding (idempotent), wired into `TenantDefaultsSeederService` / `UserScreenOptionSeederService` only when the consuming module ships. Everything else is paced by which JEX module gets refactored next.
3. **Reports stay native** until the JPA model is rich enough to express them in JPQL — for R&D, that is **never** for most of them. Wrap, project, test.
4. **One funnel** — `LegacyFunctionRepository` — so retirement in Phase 3 is a `grep`.
5. **One-shot migrations and `*_backup` are flagged for drop**, not migrated.

This plan classifies the inventory and assigns a recommended Spring component name to every group. It does **not** ship code; the next concrete deliverable is the seahorse plan's Phase 1 (bootstrap skeleton). All other groups join the Spring layer one module at a time, in priority order, behind `LegacyFunctionRepository` until each is verified and a per-domain service or Java rewrite takes over.
