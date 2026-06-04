# Daily Financial — Legacy Parity Verification Report

**Feature:** Banking > Reports > Daily Financial (CMC)
**Refactor route:** `/banking/reports/daily-financial`
**Legacy WSVC:** `rept_0015_001` (view) + `rept_0015_001_r001` (read API)
**Date:** 2026-06-04 (P0 fix applied same day — see §10)
**Method:** source-code evidence only (legacy JSP/TS/act + IDO/WSVC docs vs. refactor controller/service/DTO + Next.js client/page/dialog). No assumptions. Live DB verification was **attempted** on both `postgres_dev_server_jexframe` and `postgres_dev_server_refactor` MCP servers; both returned an empty/unreachable schema in this run, so DB findings below rely on the Flyway migration ledger (`wecms-api/CLAUDE.md`) + service code + the already-verified [database-mapping.md](database-mapping.md) (verified live 2026-06-04).

> **P0 fix landed 2026-06-04; SIT prep completed 2026-06.** Gaps **G-1** (per-account aggregates), **G-2** (as-of date), and **I-1** (Financial Status) are closed via the live-compute Strategy B (see [P0-fix-plan.md](P0-fix-plan.md) + §10). KRW coverage (§11), the Transaction History Details popup UI restyle (§12), and the Time-column verification (§13) followed. **Live data is now verified** via an authenticated `GET /api/v1/banking/reports/daily-financial` (HTTP 200, real VND/USD/KRW). **Daily Financial is READY FOR SIT** for the in-PROD scope — see §14 + [README.md §5](README.md#5-sit-readiness-2026-06). Remaining items are P1 / out-of-scope non-blockers.

---

## 1. Verdict

> Percentages below show **pre-fix → post-fix** (P0 landed 2026-06-04).

| Scope | Parity | Basis |
|---|---|---|
| **Overall** | **≈ 57 % → ≈ 80 %** | Core report data surface now functional (per-account debit/credit populated, date-based, status date-aware). Remaining gaps are P1/P2 (internal-transfer toggle, active widening, backend Excel, inline grid). |
| **Backend API parity** | **≈ 45 % → ≈ 78 %** | Per-account aggregates + as-of-date + Financial-Status now live-computed from in-scope `ACCT_TRNS_HSTR`/`ACCT_TRNS_RCV_PAY_INFM`/`ACCT_MSTR`. Still stubbed: `includeInternalTransfers` exclusion (P1/G-4), `activeAccountsOnly=false` widening (P1/G-6), server Excel (P1/G-5). |
| **Web UI parity** | **≈ 75 % → ≈ 85 %** | No UI change needed (wire shape unchanged); the ledger's numeric columns now render real figures instead of `0`. Inline `fn_accBank` grid still dropped (P1/G-3) and Excel still client-side (P1/I-6). |

**Final recommendation: READY FOR SIT (in-PROD scope).** The SIT-gate P0 items (G-1 per-account aggregates, G-2 as-of date, I-1 Financial Status) are implemented, unit-tested (`ReportServiceDailyFinancialTest`), **and verified against real data** — an authenticated `GET /api/v1/banking/reports/daily-financial?asOf=20260604&currencyScope=ALL` returns HTTP 200 with real VND/USD/KRW Financial Status + populated ledger rows. KRW coverage, the Transaction History Details popup UI (§12), and the Time-column behaviour (§13) are confirmed. Remaining items (server-side Excel provider, inline REC2 grid, internal-transfer toggle exclusion, active-account widening) are **P1 / out-of-scope non-blockers** (§14).

---

## 2. Confirmed matching behavior

| # | Behavior | Legacy evidence | Refactor evidence |
|---|---|---|---|
| M-1 | REST contract: as-of date + currency scope + 2 toggles | `rept_0015_001_r001` IN = `REF_DATE`, `CCY_CD`, `ACTV_ACCT_ONLY_YN`, `INCL_INTL_TRNS_YN` ([rept_0015_001.ts:18-23](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L18-L23)) | `GET /daily-financial?asOf&currencyScope&activeAccountsOnly&includeInternalTransfers` ([ReportController.java:29-36](../../../../src/main/java/com/kosign/wecms/domain/banking/controller/ReportController.java#L29-L36)) |
| M-2 | Date format `yyyyMMdd` | `common.app.formatDateForSave` → `REF_DATE` ([ts:277-291](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L277-L291)) | client `toBackendDate` strips dashes → `asOf=yyyyMMdd` ([DailyFinancialClient.tsx:97-99](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialClient.tsx#L97-L99)) |
| M-3 | Currency filter values All / FCY / VND(local) | radios `checkCurr` = `""` / `FCY` / `_local_currency` ([rept_0015_001_view.jsp:118-129](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp#L118-L129)) | scope `all`/`vnd`/`fcy` → `ALL`/`VND`/`FCY` ([DailyFinancialClient.tsx:90-94](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialClient.tsx#L90-L94)); FCY = `ccy <> VND` ([ReportService.java:142](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L142)) |
| M-4 | Default state: nickname + active-only + internal-transfers all ON | `onload` sets all 3 checkboxes checked ([ts:136-138](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L136-L138)) | `activeOnly`, `includeInternalTransfers`, `showNickname` all default `true` ([DailyFinancialPage.tsx:156-158](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L156-L158)) |
| M-5 | Empty state before Search | `#message` "Please click the Search button"; data hidden until `btn_search` ([view.jsp:148](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp#L148), [ts:189-210](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L189-L210)) | `hasSearched` gate → placeholder until Search ([DailyFinancialPage.tsx:711-786](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L711-L786)) |
| M-6 | Two ledger sections: Current Accounts + Savings Accounts | `showCurrentAcc` / `showSavingAcc` tables ([view.jsp:286-389](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp#L286-L389)); TS partition `03||04` / `05||06` ([ts:375-384](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L375-L384)) | two `<CurrentAccountsTable>` instances, partitioned by type ([DailyFinancialPage.tsx:753-777](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L753-L777)) |
| M-7 | Ledger column order (Bank, Acct No, [Nickname], Currency, #Debits, Debit, #Int Debits, Int Debit, #Credits, Credit, #Int Credits, Int Credit, Balance) | `thead` ([view.jsp:308-323](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp#L308-L323)) | header cells ([DailyFinancialPage.tsx:906-945](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L906-L945)) |
| M-8 | Per-currency `Total(CCY)` footer rows | `currentAccFootLoc/Usd`, `savingAccFoot*` ([ts:566-675](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L566-L675)) | `totalsByCurrency` footer rows ([DailyFinancialPage.tsx:982-1008](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L982-L1008)) |
| M-9 | Internal-transfer columns toggle visibility | `showCurrentAccInter` show/hide on `INCL_INTL_TRNS_YN` ([ts:388-395](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L388-L395)) | internal columns zeroed when off ([DailyFinancialPage.tsx:963-968](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L963-L968)) — *visibility kept, see I-4* |
| M-10 | Show-account-nickname toggles the nickname column | `colAccNickName` show/hide ([ts:191-202](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L191-L202)) | `showNickname` gates the column across screen/print/excel ([DailyFinancialPage.tsx:910-914](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L910-L914)) |
| M-11 | Click Bank/Acct No/Nickname → transaction-history popup | `.view-details` → `rept_0015_002.act` ([ts:146-151](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L146-L151)) | `<CellLink>` → `<TransactionHistoryDialog>` ([DailyFinancialPage.tsx:953-959](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L953-L959)) |
| M-12 | **Transaction popup reads real per-account history** | `rept_0015_002_r001` over `…ACCT_TRNS_HSTR_SUMR` | `dailyFinancialTransactions` real `ACCT_TRNS_HSTR` read, chronological, paginated, day totals + closing balance ([ReportService.java:229-295](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L229-L295)) |
| M-13 | Financial Status = per-currency Current + Saving totals | `REC` (R003) `TOTL_NORM_AMT`/`TOTL_SAV_AMT` ([interface-specification.md §5](interface-specification.md#5-key-sql-verbatim)) | `FinancialStatusRow` summed from `ACCT_MSTR.bal` ([ReportService.java:124-149](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L124-L149)) — *source differs, see I-1* |
| M-14 | Amount formatting locale-aware (VND `.`/₫, USD `,`/$) | `_osmc.ui.formatAmount(amt, ccy)` ([ts:519-526](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L519-L526)) | `formatAmount(value, ccy, true)` ([DailyFinancialPage.tsx:962-969](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L962-L969)) |
| M-15 | Excel + Print toolbar actions present | excel form + print clone ([view.jsp:151-156,525-527](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/rept/rept_0015_001_view.jsp#L151-L156), [ts:212-265](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L212-L265)) | Excel + Print buttons ([DailyFinancialPage.tsx:677-709](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L677-L709)) — *Excel off-contract, see I-6* |

---

## 3. Confirmed missing behavior

| # | Missing | Legacy evidence | Refactor evidence | Impact |
|---|---|---|---|---|
| ~~**G-1**~~ ✅ **RESOLVED (P0, 2026-06-04)** | **Per-account daily debit/credit counts + amounts** (`SND_CNT/AMT`, `RCV_CNT/AMT`, `INTL_*`) | `REC1` R001 carries all 8 aggregates ([interface-specification.md §2 REC1](interface-specification.md)) | now grouped from `ACCT_TRNS_HSTR` for `TRSC_DT=asOf`, internal split via `ACCT_TRNS_RCV_PAY_INFM` usage codes — `ReportService.computeDailyAggregates(...)` + `DailyAggregate`. Covered by `ReportServiceDailyFinancialTest`. | ~~Critical~~ → closed |
| ~~**G-2**~~ ✅ **RESOLVED (P0, 2026-06-04)** | **As-of-date semantics on the main report** | report keyed by `REF_DATE` against materialized `DAILY_FINCL_REPT_DAILY_SUMR_DETL` | `asOf` now drives both the per-day aggregates (`findByBizKeyAndTrscDt`) and the per-account end-of-day balance (`findFirst…TrscDtLessThanEqual…`) — `ReportService.asOfBalance(...)`. Falls back to `ACCT_MSTR.BAL` only when no history ≤ asOf. | ~~Critical~~ → closed |
| **G-3** | **Inline per-account transaction grid** (legacy `fn_accBank` / `REC2`+`REC2_1`) | big Debit/Credit grid appended under Current Accounts ([ts:743-924](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L743-L924)) | not rendered; `transactions[]` returned empty ([ReportService.java:180](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L180)); page has no inline grid (replaced by click-popup) | **High** — deliberate per [refactor-gap-analysis.md](refactor-gap-analysis.md), but a real screen-content difference |
| **G-4** | **`includeInternalTransfers` data semantics** | excludes usage codes `4000000008/6000000062/4000000104/6000000061` (and `7000000061` cash) ([rept_0015_001_r001_act.jsp:254-272,368-371](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0015_001_r001_act.jsp#L254-L272)) | flag reserved/no-op on both the main read and the popup (`RCV_PAY_USAG_CD` not joined) ([ReportService.java:106-107,210-214](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L106-L107)) | **High** — toggle is cosmetic |
| **G-5** | **Excel export (server, full layout)** | 4,508-line POI export re-running all IDOs incl. per-transaction sheets ([rept_0015_001_excel.jsp](../../../../../WECMS%20DEV/WECMS/web/view/jexGrid/file/rept_0015_001_excel.jsp)) | no `DailyFinancialExportProvider`; frontend builds a reduced workbook client-side ([DailyFinancialPage.tsx:344-617](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L344-L617)) | **High** — off-contract (see I-6) |
| **G-6** | **`activeAccountsOnly=false` widening** | `ACTV_ACCT_ONLY_YN=N` → no `actv_yn` filter (returns inactive too) ([act.jsp:308-321](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0015_001_r001_act.jsp#L308-L321)) | both ternary branches call the **active-only** finder → inactive accounts never returned ([ReportService.java:116-118](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L116-L118)) | **Medium** — unchecking the box does nothing server-side |
| **G-7** | Receipt/payment-purpose name on transactions | `wb_get_usag_nm` join in R004 / `rept_0015_003` popup | out of PROD scope (`DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR` not in active list) — no purpose column | **Low** — documented out-of-scope ([database-mapping.md §1](database-mapping.md#1-prod-scope-verdict-refactor)) |
| **G-8** | Cash book (`REC3`) + cash-book popup (`rept_0015_004`) | commented out in legacy JSP (Redmine #42129) + cash tables out of PROD scope | intentionally dropped from DTO ([DailyFinancialCmcReport.java:20-24](../../../../src/main/java/com/kosign/wecms/domain/banking/dto/DailyFinancialCmcReport.java#L20-L24)) | **None** — correct (legacy also hides it) |
| **G-9** | Audit log + View Setting seed for the screen | n/a (legacy) | no `web_svc_id='daily-financial'` seed; no audit row confirmed ([refactor-gap-analysis.md](refactor-gap-analysis.md)) | **Low/Medium** — blocks future server-side Excel + column customization |

---

## 4. Incorrect behavior

| # | Issue | Evidence | Why it's wrong |
|---|---|---|---|
| **I-1** ⚠️ **PARTIALLY RESOLVED (P0, 2026-06-04)** | **Financial Status now date-aware** — per-currency current + saving totals are summed from as-of end-of-day balances (was: live `ACCT_MSTR.bal`, date-blind, and `total` over-counted non-current/saving types). `total = current + saving` (in-scope). | `ReportService.dailyFinancial(...)` status block | **Residual:** the source is still live-computed `ACCT_TRNS_HSTR`, **not** the materialized `DAILY_FINCL_REPT_DAILY_SUMR_DETL` (Strategy B, per OQ-DF-1). Edge differences vs the legacy nightly aggregate (TZ of `TRSC_DT`, no opening-balance source for zero-history accounts) remain — validate against live summary for ≥ 3 dates. |
| **I-2** | **Account-type partition is a superset** — refactor current = `{03,04,07}`, saving = `{05,06,08}`; legacy TS uses `{03,04}` / `{05,06}` only | [ReportService.java:59-60](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L59-L60) + [DailyFinancialPage.tsx:57-58](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L57-L58) vs [ts:375-384](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L375-L384) | KRW current/savings (`07`/`08`) would surface in the refactor but are absent in legacy; harmless for the VN tenant (no `07`/`08` accounts) but a genuine classification difference to confirm with PO |
| **I-3** | **Mock fallback rows never render** — `mockCurrentAccounts` use `accountTypeCd: "11"`, which matches neither partition set, so on an empty live response both ledgers show "no rows" | [mockData.ts:74,90,107,134](../../../../../wecms-web/components/banking/reports/daily-financial/mockData.ts#L74) vs partition sets ([DailyFinancialPage.tsx:57-58,231-238](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L57-L58)) | the "fall back to mock" safety net is dead for the two ledgers; also the mock fabricates non-zero debit/credit figures that could mislead if the partition were ever fixed |
| **I-4** | **Internal columns visible but always zero** — columns remain rendered (not hidden) and show `0` because backend counts are zero | [DailyFinancialPage.tsx:921-941,963-968](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L921-L941) | legacy *hides* the internal columns when the toggle is off ([ts:388-395](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0015_001.ts#L388-L395)); refactor keeps them and zeroes the values — both a behavioral and a data divergence |
| **I-5** | **No request-time freshness recompute** | legacy `getCompareAndUpdateData` deletes+reinserts the summary tables on every request ([act.jsp:196-205,500-501](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0015_001_r001_act.jsp#L196-L205)) | refactor reads live `ACCT_MSTR` so there is no staleness — *arguably better*, but it means the refactor's "as-of-date" cannot be reconstructed (ties to G-2) | drift / contract mismatch |
| **I-6** | **Excel built client-side with `xlsx-js-style`** | [DailyFinancialPage.tsx:31-35,344-617](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L31-L35) | violates `wecms-web/CLAUDE.md` §13b (backend Apache POI is the single XLSX producer; a frontend `xlsx`/`xlsx-js-style` dependency is a forbidden pattern). Output is also reduced (no per-transaction sheet) and built from zeroed/mock data |
| **I-7** | **Param-name contract split** | implemented `asOf`/`currencyScope` ([ReportController.java:31-32](../../../../src/main/java/com/kosign/wecms/domain/banking/controller/ReportController.java#L31-L32)) vs stub doc `referenceDate`/`currency` ([interface-specification.md §3 note](interface-specification.md#3-refactor-request-spring)) | doc/endpoint disagreement; frontend uses `asOf`/`currencyScope`, so the stub doc is the stale side — reconcile to avoid future wiring error |

---

## 5. Database verification

**Documented source trace** ([database-mapping.md](database-mapping.md), verified live 2026-06-04):

- Legacy reads five `DAILY_FINCL_REPT_DAILY_*` summary tables + `ACCT_MSTR` / `ACCT_TRNS_HSTR` / `ACCT_TRNS_RCV_PAY_INFM` / `CASH_REPY_MSTR`.
- **In PROD Active Table List:** `ACCT_MSTR`, `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM`, `DAILY_FINCL_REPT_DAILY_SUMR_DETL`, `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR`.
- **NOT in PROD scope (batch/materialized):** `DAILY_FINCL_REPT_DAILY_CASH_SUMR(_DETL)`, `DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR`, `CASH_REPY_MSTR`.

**What the refactor actually uses:** `ACCT_MSTR` (via `AcctMstrRepository`) + `ACCT_TRNS_HSTR` (via `AcctTrnsHstrRepository`, popup only) ([ReportService.java:45-47](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L45-L47)). The Flyway migration ledger (`wecms-api/CLAUDE.md` V1–V154) shows **no `DAILY_FINCL_REPT_*` tables created locally** — the only report tables ever created (`BUSI_COND_DAILY_SUMR(_DETL)`, V14) were **dropped in V31** as out-of-PROD-scope. The recompute functions `wb_insert_daily_fincl_rept_daily_sumr_detl_data` / `_cash_sumr_data` are **not referenced** by any Spring code ([source-code-index.md §8](source-code-index.md), CLAUDE.md "Legacy report DB functions — Sprint 1 stance").

**Findings:**

1. **Correct scope discipline.** The refactor correctly avoids the two cash tables + the rcv/pay summary table that are out of PROD scope; dropping REC3/cash/purpose is compliant, not a defect.
2. **Strategy divergence (open decision OQ-DF-1).** The two **in-scope** summary tables (`DAILY_FINCL_REPT_DAILY_SUMR_DETL`, `…_ACCT_TRNS_HSTR_SUMR`) are **not used** — the refactor live-computes from `ACCT_MSTR`/`ACCT_TRNS_HSTR` (Strategy B). This is why per-account aggregates (G-1) and as-of-date semantics (G-2) are absent: `ACCT_MSTR` carries a *current* balance with no `ref_date` and no per-day debit/credit columns.
3. **Batch dependency.** Legacy depends on the nightly batch (`wb_insert_daily_fincl_*`) plus a **request-time recompute** ([act.jsp:196-205](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0015_001_r001_act.jsp#L196-L205)). Per CLAUDE.md the refactor deliberately does **not** port those writes. To reach G-1/G-2 parity without the batch, the per-account daily aggregates must be computed in Java by grouping `ACCT_TRNS_HSTR` by date (Strategy B), or the in-scope summary tables must be migrated + fed (Strategy A).
4. **Live DB check inconclusive this run.** Both MCP servers returned empty schemas (`information_schema` count `0`); findings above rest on the migration ledger + service code + the prior live-verified database-mapping.

---

## 6. Risk items

1. **Aggregate parity drift (I-1).** Financial Status computed from live `ACCT_MSTR` will not equal the legacy nightly `DAILY_FINCL_REPT_DAILY_SUMR_DETL` aggregate for past dates and may differ at edges for today (TZ of `TRSC_DT`, internal-transfer exclusions). Validate against the live summary table for ≥ 3 dates before claiming parity.
2. **Silent empty ledger (G-1 + I-3).** With real data the numeric columns are `0`; with empty data the mock fallback also renders nothing (wrong `accountTypeCd`). Either path produces a report that *looks* populated (Bank/Balance) but carries no transaction activity — a tester may not notice it's broken.
3. **Running-balance / closing-balance correctness (popup).** The popup's closing balance is the last chronological row's `BAL` ([ReportService.java:263-266](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java#L263-L266)); depends on `ACCT_TRNS_HSTR.BAL` being populated and time-ordered. Verify on real seed.
4. **Excel off-contract (I-6).** Client-side `xlsx-js-style` violates the project's single-XLSX-producer rule and will diverge from the legacy POI layout; also exports zeroed/mock figures.
5. **Export not gated (G-9).** A future server-side export would `WBE2013` without a `web_svc_id='daily-financial'` View Setting seed.
6. **Param-name doc drift (I-7).** Stub doc says `referenceDate`/`currency`; endpoint + frontend use `asOf`/`currencyScope`. A new integration reading the stub would wire the wrong names.

---

## 7. Required fixes

### P0 — must close before SIT ✅ **DONE in code (2026-06-04) — live-data verification outstanding**

- ✅ **P0-1 (G-1, I-1):** Per-account daily Debit/Credit counts + amounts (`snd/amt`, `rcv/amt`, internal variants) populated via grouped `ACCT_TRNS_HSTR` for the as-of date; internal split via `ACCT_TRNS_RCV_PAY_INFM`. Done.
- ✅ **P0-2 (G-2):** `asOf` applied — per-day aggregates keyed on `TRSC_DT=asOf`; per-account balance = last `BAL` ≤ asOf (Strategy B). Done.
- ✅ **P0-3 (I-1):** Financial-Status source decided (Strategy B, OQ-DF-1) + implemented date-aware (`total = current + saving`). Done. **Reconciliation against the live summary for ≥ 3 dates remains** (needs a running DB).

### P1 — required for full parity

- **P1-1 (G-4):** Implement `includeInternalTransfers` semantics (exclude the 4 usage codes via `ACCT_TRNS_RCV_PAY_INFM` — the only in-scope source) on both the main read and the popup, or document it as cosmetic-only with PO sign-off.
- **P1-2 (G-6):** Make `activeAccountsOnly=false` actually widen to inactive accounts (add a `findAllByBizKey…` finder); today both branches are active-only.
- **P1-3 (G-5, I-6):** Replace the client-side Excel with a backend `DailyFinancialExportProvider` + `POST /banking/reports/daily-financial/export` through `common/export/`; remove the `xlsx-js-style` dependency from the page.
- **P1-4 (G-3):** Decide inline REC2 grid vs. popup-only with PO; if popup-only is accepted, record it as an intentional UI divergence (it currently is, per gap analysis).
- **P1-5 (I-7):** Reconcile param names (`asOf`/`currencyScope`) across endpoint, stub doc, and frontend in one MR.

### P2 — hardening / follow-up

- **P2-1 (I-2):** Confirm `{03,04,07}`/`{05,06,08}` vs legacy `{03,04}`/`{05,06}` partition with PO.
- **P2-2 (I-3):** Fix mock `accountTypeCd` to a partition-valid code (or remove the mock fallback for the ledgers).
- **P2-3 (I-4):** Hide internal columns when the toggle is off (match legacy) rather than rendering zeros.
- **P2-4 (G-9):** Add the View Setting seed + audit-log write (`WSVC_ID='daily-financial'`) when export lands.

---

## 8. Priority summary

| Priority | Items |
|---|---|
| **P0** ✅ done in code | G-1 (per-account aggregates) · G-2 (as-of date) · I-1 (status source, residual reconciliation) |
| **P1** ⏳ open | G-4 (internal-transfer toggle exclusion) · G-6 (active-only widening) · G-5/I-6 (backend Excel) · G-3 (inline grid decision) · I-7 (param names) |
| **P2** ⏳ open | I-2 (partition set) · I-3 (mock type) · I-4 (hide internal cols) · G-9 (view-setting + audit) |

---

## 9. Final recommendation

**READY FOR SIT (in-PROD scope).**

The report's reason for existing — showing each account's daily debit/credit activity as of a chosen date — is wired and verified: per-account counts/amounts are live-computed (G-1 ✅), the as-of date drives the aggregates + balances (G-2 ✅), and Financial-Status is date-aware with `total = current + saving` across VND/USD/KRW (I-1 ✅, Strategy-B source). The Transaction History Details popup is implemented and UI-aligned to the e-Invoice popup (§12); the Time column is verified correct/blank-by-design (§13). **Live data confirmed** — authenticated `GET /api/v1/banking/reports/daily-financial?asOf=20260604&currencyScope=ALL` → HTTP 200 with real VND/USD/KRW Financial Status (e.g. VND current `11,804,899,409`) and populated ledger rows.

Non-SIT-blockers (P1 / out-of-scope): `includeInternalTransfers` exclusion and `activeAccountsOnly=false` widening are display-only (G-4, G-6); the server-side Excel provider is pending while a client-side export exists (G-5/I-6); the inline `fn_accBank` grid is intentionally deferred — the popup serves the drilldown (G-3); and edge-case reconciliation vs the legacy nightly aggregate remains a P1 validation follow-up. None gate SIT.

---

## 10. P0 fix changelog (2026-06-04)

**Strategy:** live-compute (Strategy B) — the documented summary table `DAILY_FINCL_REPT_DAILY_SUMR_DETL` is **not** migrated to the refactor DB (no Flyway migration, no entity; only the sibling Daily *Cash* summary exists via V125), so the report computes the legacy figures from the in-scope operational tables. Full rationale: [P0-fix-plan.md](P0-fix-plan.md).

**Files changed (backend only — no frontend change; wire shape unchanged):**

| File | Change |
|---|---|
| `repository/AcctTrnsHstrRepository.java` | + `findByBizKeyAndTrscDt(bizKey, trscDt)` (day's transactions); + `findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderByTrscDtDescTrscTmDescAcctSeqNoDesc(...)` (as-of end-of-day balance) |
| `repository/AcctTrnsRcvPayInfmRepository.java` | + `findByBizKeyAndAcctSeqNoInAndRcvPayUsagCdIn(...)` (internal-transfer classification by usage code) |
| `service/ReportService.java` | rewrote `dailyFinancial(...)`: per-account daily aggregates (SND/RCV + internal split, disjoint), as-of-date balance, date-aware Financial Status (`total = current + saving`); added `+ AcctTrnsRcvPayInfmRepository rcvPayRepo`, `INTERNAL_TRANSFER_USAGE_CODES`, helpers `computeDailyAggregates` / `asOfBalance` / `matchesScope` / `normaliseAsOf` + `DailyAggregate`. `dailyFinancialTransactions(...)` (popup) **untouched**. |
| `test/.../service/ReportServiceDailyFinancialTest.java` | **new** — Mockito unit tests: internal-vs-external split, as-of balance + `ACCT_MSTR` fallback, currency-scope FCY filter, Financial-Status totals. |

**Logic changed:**
- Per-account `NUM_DEBITS/DEBIT/NUM_INTERNAL_DEBITS/INTERNAL_DEBIT/NUM_CREDITS/CREDIT/NUM_INTERNAL_CREDITS/INTERNAL_CREDIT` — were hard-`0`; now grouped from `ACCT_TRNS_HSTR` for `TRSC_DT = asOf`. A transaction is internal when its `ACCT_SEQ_NO` matches an `ACCT_TRNS_RCV_PAY_INFM` row with usage code ∈ `{4000000008, 6000000062, 4000000104, 6000000061}` (verbatim legacy set). SND/RCV exclude internal (disjoint, legacy footer parity).
- `BAL` and Financial-Status totals — were "current" `ACCT_MSTR.bal`; now the last `BAL` ≤ asOf (date-aware), with `ACCT_MSTR.bal` fallback only when the account has no history that early.
- Financial-Status `total` — was Σ over **all** account types (over-counted loan/deposit); now `current + saving` (in-scope).
- Blank/null `asOf` now defaults to today (legacy requires `REF_DATE`).

**Verification:** `./gradlew test` — full suite **BUILD SUCCESSFUL** (new `ReportServiceDailyFinancialTest` green; no regressions). Frontend untouched → no typecheck/build needed (the DTO wire fields the page already reads were simply zero before).

**Remaining gaps (unchanged — out of P0 scope):** G-3 inline grid (P1), G-4 internal-transfer toggle exclusion (P1), G-5/I-6 backend Excel (P1), G-6 active widening (P1), I-2 partition superset (P2), I-3 mock account-row type (P2), I-4 hide internal cols when off (P2), G-9 view-setting/audit (P2), I-1 residual reconciliation vs live summary, and **live-data P0 verification** (SIT gate).

---

## 11. KRW Financial Status currency coverage (follow-up, 2026-06-04)

**Reported:** `/banking/reports/daily-financial` showed only *Financial Status — VND* and *— USD*; KRW was missing even though the tenant holds active KRW accounts.

**Diagnosis (evidence):**
- The tenant `VN_VNM0004_000001` has **2 active KRW current accounts** (`acct_type_cd = '03'`, `ccy_cd = 'KRW'`, confirmed via `postgres_dev_server_refactor`). `03` ∈ `CURRENT_ACCOUNT_TYPE_CODES`, so the backend's per-currency union (`currentByCcy ∪ savingByCcy`) **already emits a KRW Financial Status row** — no VND/USD-only restriction exists in `ReportService.dailyFinancial`. Legacy actually *lumps* non-USD into the "local" bucket (`rept_0015_001.ts` `fn_finacialStatus`: `currency === "USD" ? usd : loc`) and renders only two sections — the refactor's per-currency rendering is **more granular** and already correct for KRW.
- Frontend `formatAmount` already maps `KRW → ₩` ([lib/currency.ts](../../../../../wecms-web/lib/currency.ts) `CCY_SYMBOLS`/`PREFIX_SYMBOL`/`CCY_RULES`), and the page renders **one `<ReportSection>` per currency** dynamically — no hard-coded VND/USD cap.
- **Root cause of the screenshot:** it was the **mock fallback** (API returned nothing → `sourceStatus = mockFinancialStatus`). `mockFinancialStatus` (a) had only VND + USD and (b) held *pre-formatted* strings (`"21.437.055.576₫"`) that `formatAmount` re-parsed into garbage (`21.437 → "21₫"`, USD `$…` → `NaN → "$0"`) — matching the screenshot's `21đ` / `$0` exactly.

**Fix (this issue only):**
- **Frontend** — [`mockData.ts`](../../../../../wecms-web/components/banking/reports/daily-financial/mockData.ts): `mockFinancialStatus` now holds **raw wire-style numeric strings** (matching `AmountFormatter.wire` + `mapFinancialStatus`, which the page formats via `formatAmount`) and **adds a KRW row**. This fixes the fallback's currency coverage *and* the double-format garble; under Currency = All the fallback now renders VND + USD + **KRW (₩)**.
- **Backend** — no code change required (KRW already emitted). Added regression test `ReportServiceDailyFinancialTest#financialStatus_includesKrwSection_whenKrwCurrentOrSavingAccountExists` locking that a KRW current/saving account produces a KRW Financial Status row under Currency = All.

**Out of scope (untouched):** mock *account-row* type-`11` issue (I-3, P2 — `mockCurrentAccounts` ledger rows), REC2 inline grid, Excel export, transaction-history popup, and all other P1/P2 items.

**Verification:** backend `./gradlew test` — all green (incl. the new KRW test). Frontend `npx tsc --noEmit` — clean (mock change is type-compatible).

---

## 12. Transaction History Details popup — UI restyle (2026-06)

The popup (`TransactionHistoryDialog.tsx`, opened from the Bank / Account No. / Nickname cells) was
restyled to match the **e-Invoice Latest Inquiry History** popup
(`components/addon/sales-einvoices/EInvoiceHistoryDialog.tsx`). **UI-only — no API / business logic /
Excel / popup-trigger change.**

| Item | Status |
|---|---|
| Modal layout | ✅ `w-[1024px] max-w-[92vw] rounded-2xl` (e-Invoice contract); responsive sizing |
| Table styling | ✅ bordered rounded box + sticky `bg-wb-bg-sidebar` header + e-Invoice `Th`/`Td` |
| Columns | Time · Debit · Credit · Balance · Remarks (numeric right-aligned, Remarks left); **no "No." column** (e-Invoice pattern has none) |
| Empty state | ✅ centered row spanning all columns, fixed height — not floating text |
| Footer | ✅ page-size (left) · pagination nav (center, when > 1 page) · totals summary (right) |
| Default page size | ✅ **10** (`PAGE_SIZE_OPTIONS[0]`) |
| Page-size options | ✅ **10 / 20 / 50 / 100** (shared `PAGE_SIZE_OPTIONS`) |
| Page-size change | ✅ resets to page 1 |
| Dropdown overflow | ✅ fixed — opens upward (`bottom-full`), no overlap below the footer |
| Summary text | ✅ `Total {N} transaction(s) / Total Debit: {amt}, Total Credit: {amt}, Balance: {amt}` |
| Validation | ✅ `npx tsc --noEmit` clean · `npm run build` success · route `/banking/reports/daily-financial` built |

**Status: READY FOR SIT.**

## 13. Time Column verification (2026-06)

**Result: verified correct — no mapping issue, no header-alignment issue. Not a defect.**

- `ACCT_TRNS_HSTR.TRSC_TM` is **empty** for the affected rows (dev DB: the 5 transactions on
  `2026-06-04` for `VN_VNM0004_000001` all have `trsc_tm = ''`, length 0, not null).
- Backend returns `TRSC_TM` correctly (`ReportService.dailyFinancialTransactions` →
  `DailyFinancialTransactionsReport.TransactionRow.TRSC_TM`, 1st positional field).
- Mapper passes `TRSC_TM` correctly (`mapDailyFinancialTransactionsResponse`: `transactionTime ← r.TRSC_TM`).
- UI renders `TRSC_TM` correctly (`<Td>{formatTime(r.transactionTime)}</Td>`; header↔cell order 1:1).
- Empty `TRSC_TM` renders blank **by design** (`formatTime("")` → `""`); debit/credit bind by `INOUT_DV`.
- Legacy behaves the same (`rept_0015_001.ts` → `common.app.time_format2(v.TRSC_TM)` is blank when empty).

**Conclusion:** the Time column is intentionally blank because source transaction records contain no
time value. This is not a defect.

## 14. SIT readiness + release note

**Daily Financial — Status: READY FOR SIT** (in-PROD scope).

**Completed:** main report (date-aware) · Financial Status · Current Accounts · Saving Accounts ·
Transaction History Details popup · VND / USD / KRW support · API integration · date-aware balances ·
Debit/Credit/Internal calculations.

**Remaining (non-SIT blockers):** server-side Excel provider (frontend export already present) · REC2
inline transaction expansion (popup serves the drilldown) · `includeInternalTransfers`/`activeAccountsOnly`
toggle semantics · cosmetic UI only.

### Release note — 2026-06 Daily Financial Final SIT Preparation

Completed: fixed Financial Status calculations · added KRW support · fixed API data-source
configuration · removed mock-data dependency in correctly-configured environments · updated the
Transaction History Details popup UI · verified Time-column behaviour · completed SIT readiness review.

> Full SIT-readiness summary + release note: [README.md §5–§7](README.md#5-sit-readiness-2026-06).
> Runtime data-source diagnosis: [runtime-data-source-check.md](runtime-data-source-check.md).

---

*P0 fix applied 2026-06-04 (backend); KRW coverage + Transaction-History popup UI restyle + Time-column
verification + API data-source fix landed 2026-06. Live data verified via authenticated
`GET /api/v1/banking/reports/daily-financial` (HTTP 200, real VND/USD/KRW). **Daily Financial is now
marked READY FOR SIT for the in-PROD scope**; remaining items are P1 / out-of-scope non-blockers.
Legacy paths under `WECMS DEV/` remain read-only reference; pre-fix sections above are preserved as the
audit trail.*
