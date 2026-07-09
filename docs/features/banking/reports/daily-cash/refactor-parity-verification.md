# Daily Cash — Refactor Parity Verification

> **⚠️ Data-source corrected 2026-06-13 (noted 2026-06-23).** The live read path calls the legacy DB
> functions **`rept_0900_001_rec1..rec4`** directly via `DailyCashReportFunctionRepository`
> (`DailyCashReportService.java:24-75` — the documented set-returning exception, same stance as Cash Flow
> and Daily Financial). The prior **Java live-recompute** (`DailyCashRecomputeRepository` /
> `CurrencyAccumulator`) is **bypassed / no longer wired** and retained only as a cleanup candidate. Any
> "live recompute over `ACCT_MSTR` + `ACCT_TRNS_HSTR`" wording below is the **superseded pre-pivot** record
> (audit history preserved).

**Feature:** Banking > Reports > Daily Cash (`rept_0009_001`)
**Refactor route:** `/banking/reports/daily-cash`
**Date:** 2026-06-03 · **Re-verified 2026-06-04 (OQ-1 resolved against DEV DB).** · **Post-fix review 2026-06-04.**
**Scope:** Full parity check — legacy JEX (source of truth) vs. refactor (Spring Boot + Next.js).
**Verdict (post-fix 2026-06-04):** 🟡 **PARTIAL PASS** — the two CRITICAL correctness bugs are **fixed**; remaining gaps are non-critical/deferred (client-side export, data-source divergence, cosmetics). See §"Post-fix status" below and [fix-plan.md](./fix-plan.md).
**Original verdict (2026-06-03):** ❌ FAIL — two confirmed correctness bugs (account-type bucketing on both tiers). Retained below as the historical record.

> ### Post-fix status (2026-06-04)
> Implemented since the original review (all verified: backend `DailyCashReportServiceTest` 4/4 green; FE `tsc`/`eslint`/`build` clean):
> - **§1.2 backend bucketing — FIXED.** `DailyCashReportService` buckets via `AcctTypeCd.fromCode(...)`: demand=`03,04`, savings=`05,06`, term=`02`, loan=`01`, cash=`0`, `07/08`+unknown ignored. Fabricated `11/12/13/21` deleted. DB-verified equal to legacy `rept_0900_001_rec2` (§1.5).
> - **§1.3 frontend split — FIXED.** Current=`03/04`, Savings=`05/06`; `01/02/07/08` excluded from both tables.
> - **§2.8 summary — REWORKED to legacy shape.** Per-currency block `"Daily Cash Status — <CCY> Total : <amount>"` + 2-column Current/Saving table (Cash/Time/Available columns removed), styled like Daily Financial. Always renders after Search; falls back to zero VND/USD/KRW rows when the API summary is empty. Ordered VND→USD→KRW→others.
> - **§2.14 Cash table — REMOVED** from the UI (legacy VN view has it commented out).
> - On-screen **summary** amounts now formatted via `formatAmount` (incl. symbol); **detail-table** cells remain raw (Issue 3, still open).
> **Still open (non-critical):** client-side Excel export vs. backend POI (§4 / Gap #2), live-recompute vs snapshot (§3 / Gap #3, OQ), per-tenant local currency (Gap #6), last-collection timestamp (§2.3 / Gap #5), unwired checkbox column (§2.12), bespoke print path (§5), detail-cell amount formatting (§2.13).
> Section rows below (§2/§3/§4/§5) reflect the **original** 2026-06-03 state unless annotated; treat this banner as authoritative where they differ.

> **2026-06-04 update.** The `postgres` MCP (JEX DEV) was connected this pass, so the two items that were previously unverifiable are now **confirmed against live data**:
> 1. **Distinct `ACCT_TYPE_CD` in `DAILY_CASH_REPT_DAILY_SUMR_DETL`** = `01,02,03,04,05,06,07` only — **never `11/12/13/21`, and `08` is absent** for this DB. This is direct proof the backend's `11/12/13/21` constants match zero rows.
> 2. **`rept_0900_001_rec2` / `rec4` function bodies** (the OQ-1 blocker) were read via `pg_get_functiondef`. The summary-bucket mapping is now **fully resolved** — see §1.5. This unblocks Issue 1 in the fix plan.

> Legacy is read-only reference per workspace rules. All legacy paths are under `WECMS DEV/`; refactor paths under `wecms-api/` and `wecms-web/`. Companion index: [source-code-index.md](./source-code-index.md).

---

## 0. Live request path (what actually runs)

There are **two parallel Daily Cash implementations** in the refactor. The page under test uses only the first:

```
DailyCashClient.tsx
  → lib/api/banking.fetchDailyCashReport({ asOfDate, currency })
  → GET /api/v1/banking/reports/daily-cash-report           ← slug endpoint (LIVE)
  → DailyCashReportController.search(asOfDate, currency)
  → DailyCashReportService.search(...)
  → DailyCashReportFunctionRepository                       ← calls legacy DB functions directly (2026-06-13)
  → fn rept_0900_001_rec1..4(...)                           ← rec1/rec2 active; rec3/rec4 data-blocked
```

> **Active source = direct `rept_0900_001_rec1..4` calls (2026-06-13).** Earlier revisions of this doc
> showed the snapshot read (`DailyCashReptDailySumrDetlRepository.findRows` over
> `DAILY_CASH_REPT_DAILY_SUMR_DETL`) and then a Java live-recompute (`DailyCashRecomputeRepository.findAccountsAsOf`
> over `ACCT_MSTR` + `ACCT_TRNS_HSTR`) as the live path — **both are superseded**. The snapshot repo is
> dormant and `DailyCashRecomputeRepository` is `@Deprecated(forRemoval=true)`. The service now calls the
> legacy DB functions directly via `DailyCashReportFunctionRepository`.

The older `GET /api/v1/banking/reports/daily-cash` (`ReportController` / `ReportService.dailyCash` / `DailyCashReport` DTO, reading `ACCT_MSTR`) is **NOT** used by this page — it is the V31-era stub. The frontend's `lib/api/banking.fetchDailyCash` wraps that old endpoint but is **not called** by `DailyCashClient`.

> ⚠️ **Stale doc note.** `wecms-web/CLAUDE.md` "Known contract mismatches" says *"Daily Cash: backend ACCOUNTS is a flat list with no Current/Savings discriminator … 'Savings Accounts' stays on the mock seed."* That describes the **old** `/daily-cash` path. The **live** `/daily-cash-report` path **does** ship `acct_type_cd` per account row, so the discriminator exists — the bug (below) is that the frontend compares it against the wrong code. The CLAUDE.md note should be updated when the fix lands.

Legacy serves the page through `rept_0009_001_view_osmc.jsp` (VN-active view) + `rept_0009_001_osmc.ts`, calling two services: `rept_0009_001_r001` (report data) and `rept_0100_001_r001` (last-aggregation timestamp).

---

## 1. CRITICAL — Account-type code mapping

This is the headline finding. The prompt asked specifically whether the refactor uses wrong codes like `11/12/13/21`. **It does — confirmed on both backend and frontend, in two independent bugs.**

### 1.1 Legacy ground truth — `AcctTypeCd` (verified)

`WECMS DEV/WECMS/src/wabooks/enums/AcctTypeCd.java:7-14` + labels `:41-48`:

| Code | Constant | Korean label | Meaning | Daily-Cash section |
|---|---|---|---|---|
| `01` | `LOAN` | 대출금 | Loan | (Loan table — dead/commented in VN view) |
| `02` | `DEPOSIT` | 정기예적금 | Fixed / time deposit | (Time Deposit table — dead/commented) |
| `03` | `CURRENT_KHR` | 입출식예금 | Current / demand deposit (local) | **Current Accounts** |
| `04` | `CURRENT_USD` | 외화입출식예금 | Current / demand deposit (FCY) | **Current Accounts** |
| `05` | `SAVINGS_KHR` | 보통예금 | Savings / passbook (local) | **Savings Accounts** |
| `06` | `SAVINGS_USD` | 외화보통예금 | Savings / passbook (FCY) | **Savings Accounts** |
| `07` | `CURRENT_KRW` | Current Accounts | Current account (KRW) | Current (KRW tenants) |
| `08` | `SAVINGS_KRW` | Saving Accounts | Savings account (KRW) | Savings (KRW tenants) |

The **detail-table section split is verified from the live legacy controller**, not inferred:
- Current Accounts table renders rows where `ACCT_TYPE_CD == "03" || "04"` — `rept_0009_001_osmc.ts:622`.
- Savings Accounts table renders rows where `ACCT_TYPE_CD == "05" || "06"` — `rept_0009_001_osmc.ts:748`.

> 📝 **Correction to the prompt's assumption.** The prompt listed `07 = Other`, `08 = Other`. The enum says `07 = Current (KRW)`, `08 = Savings (KRW)` — there is **no "Other"** code. This does not change the bug conclusions but is recorded so the fix uses the real mapping.

The refactor's own mirror enum `wecms-api/.../common/enums/AcctTypeCd.java:50-83` correctly uses `01–08`. **Neither the live backend service nor the frontend uses this enum** — that is the root of both bugs.

### 1.2 Backend bug — invented codes `01/11/12/13/21` — ✅ FIXED 2026-06-04

`DailyCashReportService.java:36-40`:

```java
private static final String TYPE_CASH    = "01"; // physical cash
private static final String TYPE_DEMAND  = "11"; // current / demand deposit
private static final String TYPE_SAVINGS = "12"; // savings / passbook
private static final String TYPE_TERM    = "13"; // term deposit
private static final String TYPE_LOAN    = "21";
```

Bucketing switch `DailyCashReportService.java:137-144` (exact `.equals` on trimmed `ACCT_TYPE_CD`):

```java
switch (type) {
    case TYPE_CASH    -> cash    = cash.add(bal);    // "01"
    case TYPE_DEMAND  -> demand  = demand.add(bal);  // "11"
    case TYPE_SAVINGS -> savings = savings.add(bal); // "12"
    case TYPE_TERM    -> term    = term.add(bal);    // "13"
    case TYPE_LOAN    -> loan    = loan.add(bal);    // "21"
    default           -> { /* unrecognised — totals untouched */ }
}
```

**Why it's wrong:** `DAILY_CASH_REPT_DAILY_SUMR_DETL.ACCT_TYPE_CD` is a `CHAR(2)` column holding legacy codes `01–08` (entity `DailyCashReptDailySumrDetl.java:39-42`; enum confirms the domain). **Verified on DEV 2026-06-04** — `SELECT acct_type_cd, count(*) … GROUP BY acct_type_cd` returns `01`(2 311) · `02`(16 951) · `03`(74 882) · `04`(19 611) · `05`(29 330) · `06`(16 136) · `07`(19). No `11/12/13/21`, no `08`. Against real data:
- `01` rows are bucketed as **cash**, but `01` = **LOAN** in the enum → loan balances mislabeled as cash, and the `loan` bucket (which expects `21`) is **always empty**.
- `02`–`08` (every real demand/savings/term/current account) fall through to `default` → **dropped from every summary bucket**.
- Codes `11/12/13/21` have **no source** in either the legacy or refactor `AcctTypeCd` enum, in the IDO XMLs, or in any migration. They appear to be a fabricated guess.

**Net effect:** `currency_summary` (`total_cash / total_demand_deposit / total_savings / total_term_deposit / total_loan`) and `currency_flow` (whose `balance = cash+demand+term+savings+loan`) are effectively **zero / wrong** for all real accounts.

### 1.3 Frontend bug — savings split on `"03"` — ✅ FIXED 2026-06-04

`DailyCashClient.tsx:132-134`:

```ts
function isSavingType(acctTypeCd: string | undefined | null): boolean {
    return (acctTypeCd ?? "").trim() === "03";
}
```

Used at `:83` (current = `!isSavingType`), `:87` (savings = `isSavingType`), and `:147` (rollup). The in-code comment at `:77-80` wrongly asserts *"03 = saving"*.

**Why it's wrong vs. legacy ground truth (`rept_0009_001_osmc.ts:622,748`):**
- Legacy `03` = **Current Account (VND)** → this code misfiles every current-VND account into the **Savings Accounts** table.
- Legacy savings = `05` / `06` → these fall into the `else` branch and land in the **Current Accounts** table.
- The split is effectively **inverted** for the most common (VND) accounts.

Correct frontend rule: **Current = `03` || `04`; Savings = `05` || `06`.**

### 1.4 Account-type verdict

| Layer | Expected (legacy) | Refactor actual | Status |
|---|---|---|---|
| Detail Current section | `03`,`04` | FE: everything ≠ `03` | ❌ wrong |
| Detail Savings section | `05`,`06` | FE: only `03` | ❌ wrong (inverted) |
| Summary loan bucket | `01` | BE: `21` (never matches) | ❌ wrong |
| Summary term/fixed bucket | `02` | BE: `13` (never matches) | ❌ wrong |
| Summary demand/current bucket | `03`,`04`(,`07`) | BE: `11` (never matches) | ❌ wrong |
| Summary savings bucket | `05`,`06`(,`08`) | BE: `12` (never matches) | ❌ wrong |
| Summary cash bucket | (from cash source, see OQ-1) | BE: `01` (= legacy LOAN) | ❌ wrong |

### 1.5 DB-verified summary-bucket mapping (OQ-1 — RESOLVED 2026-06-04)

The legacy summary (`REC`) is produced by PostgreSQL function `rept_0900_001_rec2`, read via `pg_get_functiondef` on DEV. Its aggregation is unambiguous:

```sql
-- rept_0900_001_rec2(...) — aggregates over rept_0900_001_rec1 (account detail)
sum(case when acct_type_cd in ('03','04') then bal else 0 end)  AS totl_norm_amt   -- Current / demand
sum(case when acct_type_cd in ('05','06') then bal else 0 end)  AS totl_sav_amt    -- Savings
sum(case when acct_type_cd =  '02'        then bal else 0 end)  AS totl_acct_amt   -- Time deposit (정기예적금)
sum(case when acct_type_cd =  '01'        then bal else 0 end)  AS totl_loan_amt   -- Loan
```

`TOTL_CASH` comes from a **completely separate** function pair `rept_0900_001_rec4 → rec3`, which sums daily balances from table **`cash_repy_mstr`** (cash repayment master) per currency — it is **NOT** derived from any `ACCT_TYPE_CD` in the detail table.

**Authoritative bucket mapping (use this for the fix):**

| Summary DTO field | Legacy bucket | `ACCT_TYPE_CD` | Source |
|---|---|---|---|
| `total_demand_deposit` | `TOTL_NORM_AMT` | `03`, `04` | `rec2` |
| `total_savings` | `TOTL_SAV_AMT` | `05`, `06` | `rec2` |
| `total_term_deposit` | `TOTL_ACCT_AMT` | `02` | `rec2` |
| `total_loan` | `TOTL_LOAN_AMT` | `01` | `rec2` |
| `total_cash` | `TOTL_CASH` | **none** — from `cash_repy_mstr` (rec3/rec4) | separate source |

**Two consequences for the refactor:**

1. **`07` and `08` are in NO summary bucket.** `rec2` only sums `01–06`. The refactor must NOT fold `07`→demand / `08`→savings into summary totals if strict parity is the goal (this refines the earlier fix-plan suggestion). `07` exists in DEV data (19 rows) and is silently excluded from every legacy summary total — and from both detail tables (`osmc.ts` only renders `03/04` and `05/06`).
2. **`total_cash` cannot be sourced from the detail table at all** — and `cash_repy_mstr` / `DAILY_CASH_REPT_DAILY_CASH_SUMR_DETL` are **not in the refactor PROD active-table list**. So a faithful `total_cash` is out of refactor scope today; it should be `0` (legacy VN view hides the Cash column anyway — §2.8), **never** the sum of `01` (loan) rows.

**Detail (`REC1`) split — also DB-confirmed.** `rept_0900_001_rec1` selects from `acct_mstr` (active accounts; loans `01` only when `expi_dt >= req_date`). The two rendered tables filter it as Current = `03/04`, Savings = `05/06` (matches `osmc.ts:622,748`). **As of 2026-06-13** the refactor calls `rept_0900_001_rec1` directly (via `DailyCashReportFunctionRepository`) — same column shape, same codes. (Earlier revisions read the batch-persisted snapshot, then Java-recomputed; both superseded.)

---

## 2. UI behavior parity

Legend: ✅ match · ⚠️ minor divergence · ❌ bug · ❔ open question.

| # | Aspect | Legacy (evidence) | Refactor (evidence) | Status |
|---|---|---|---|---|
| 2.1 | Initial screen (pre-Search) | No auto-search; shows "Please click Search" (`rept_0009_001_view_osmc.jsp:176`, `onload` only loads date + last-agg ts, `_osmc.ts:112-116`) | No auto-fetch; centered placeholder `report.daily_cash.empty_message` gated on `hasSearched=false` (`DailyCashClient.tsx:37,56-60`; `DailyCashPage.tsx:329-339`) | ✅ |
| 2.2 | Date default | Today (`$(".date").datepicker("setDate", new Date())`, `_osmc.ts:162`); single date; **input disabled** in VN view (`view_osmc.jsp:147`) | Today, ISO `YYYY-MM-DD` (`DailyCashClient.tsx:44-46`); single date; editable `<DateField>` | ⚠️ legacy date field is disabled; refactor lets the user pick. Display format both `DD-MM-YYYY`. Confirm whether the refactor should lock the date (OQ-4). |
| 2.3 | Last-aggregation timestamp | `fn_last_date()` calls `rept_0100_001_r001` (BAT_ID `REPT_0900_001`) → "영업집계 최종일시 : …" (`_osmc.ts:918-937`) | **Not implemented** — no `rept_0100_001` equivalent fetched/displayed | ❌ missing (OQ-5) |
| 2.4 | Currency radios | name `checkCurr`: All (`value=""`, checked) / `FCY` / `VND` (`view_osmc.jsp:156-167`) | `CurrencyScopeToggle`: `all` (default) / `vnd` / `fcy` → wire `ALL`/`VND`/`FCY` (`types.ts:1`; `DailyCashClient.tsx:36,161-165`) | ✅ semantics match (labels via i18n keys) |
| 2.5 | Search behavior | Click `.btn_search_tb` → `fn_listData` → one sync call to `rept_0009_001_r001` (`_osmc.ts:119-126,204-231`) | Search button bumps `searchKey` → `fetchDailyCashReport` (`DailyCashClient.tsx:91-94,48-52`) | ✅ |
| 2.6 | Scope change after search | Re-runs query | `handleScopeChange` re-runs only after first search (`DailyCashClient.tsx:96-101`) | ✅ |
| 2.7 | Empty result | Per-section "no data" rows (`_osmc.ts:266-269,686-687,785-786`) | `AccountLedger` no-rows row; summary section hidden when empty (`DailyCashPage.tsx:300,556-565`) | ✅ behavior equivalent |
| 2.8 | Cash summary section | Per-currency totals; visible columns = Current (보통예금) + Savings only; Cash/Time/Loan columns hard-hidden (`view_osmc.jsp:220-294`) | `SummaryTable` 5 cols: Cash · Current · Savings · TimeDeposit · Available (`DailyCashPage.tsx:375-389`) | ⚠️ refactor shows Cash/Time columns legacy hides; **and values are wrong per §1.2**. `available = total_demand_deposit` only (`DailyCashClient.tsx:180`) — legacy grand total summed NORM+SAV+CASH+ACCT+LOAN (`_osmc.ts:518-540`). Different semantics (OQ-6). |
| 2.9 | Current Accounts section | 8 cols: Bank · Acct No · Currency · No.Debits · Debit · No.Credits · Credit · Balance; rows `03`/`04` (`_osmc.ts:663-672,622`) | `AccountLedger`: 9 cols (checkbox + same 8); rows = `!isSavingType` (`DailyCashPage.tsx:483-569`) | ❌ membership wrong (§1.3); + extra checkbox col (2.12) |
| 2.10 | Savings Accounts section | 8 cols (same shape); rows `05`/`06` (`_osmc.ts:769-778,748`) | `AccountLedger` 9 cols; rows = `isSavingType` = `03` only (`DailyCashClient.tsx:87`) | ❌ membership wrong/inverted (§1.3) |
| 2.11 | Table sorting | No client-side sort; the savings header `<a>` tags are inert (`_osmc.ts` has no sort handler) | No client-side sort | ✅ |
| 2.12 | Checkbox column | **None** anywhere — no `type=checkbox` in view, no select-all | `AccountLedger` renders a header + per-row `<Checkbox>` (`DailyCashPage.tsx:514-516,543-545`), **unwired** (no `checked`/`onChange`) | ❌ extra non-legacy column; also non-functional. Remove (or hide) for parity (OQ-7). |
| 2.13 | Number formatting | Currency-aware `_osmc.ui.formatAmount(v, ccy)`; counts via `Math.trunc`/`fn_formatcnt` (`_osmc.ts:274,667,954-959`). VND `#,###`, USD `#,##0.00` | On-screen ledger/summary cells render **raw strings** (`toAccountRow`/`toSummaryRow` `String(...)`, `DailyCashClient.tsx:174-201`); `formatAmount` used only in section title + export/print | ❌ on-screen amounts unformatted (no thousand separators / decimals). Legacy formats every cell. |
| 2.14 | Cash (physical) section | Commented-out/dead in VN view (Redmine #42096, `view_osmc.jsp:297-335`) | `HoldingsTable` always renders but `holdings=undefined` → always empty (`DailyCashClient.tsx:106`) | ⚠️ both effectively empty; harmless but the dead table renders an empty 8-col grid |
| 2.15 | currency_flow consumption | REC2 cash detail drives the (dead) cash table | `currency_flow` typed but **never consumed** by the client | ⚠️ dead wire field; no UI impact |

---

## 3. API / data parity

### 3.1 Request parameters

| Item | Legacy `rept_0009_001_r001` | Refactor `/daily-cash-report` | Status |
|---|---|---|---|
| Date param | `REQ_DATE`, `yyyyMMdd`, required, `validateDate8` (`rept_0009_001_r001_act.jsp:52-57`) | `asOfDate`, `yyyy-MM-dd`, `@NotBlank @Pattern` (`DailyCashReportController.java:39-41`); service strips `-` → `yyyyMMdd` (`DailyCashReportService.java:117-120`) | ✅ equivalent (format converted server-side) |
| Currency param | `CCY_CD`: blank=All, `"FCY"`=`ccy_cd<>'VND'`, else exact (`_act.jsp:107-131`) | `currency`: parsed by `DailyCashCurrencyFilter` → ALL/FCY/VND/USD/KHR; `exact()` + `exclude("VND")` (`DailyCashCurrencyFilter.java`) | ✅ core semantics match. ⚠️ refactor only matches the 5 enum currencies exactly; any other code silently degrades to ALL (legacy would treat e.g. `EUR` as exact). Low risk. |
| `INQ_DV` | Declared, used only by the dead re-aggregate path | Not implemented | ✅ correctly omitted (OQ-2 already noted in feature doc) |
| Tenant scope | `biz_key` always bound (`_act.jsp:112-113`) | `TenantContext.currentBizKey()` (`DailyCashReportService.java:52`) | ✅ |

### 3.2 Response structure / REC / REC1 / REC2 mapping

Legacy assembles three records (`_act.jsp:213-215`): `REC` (per-currency totals from IDO R005 + `TOTL_CASH` merged from R007), `REC1` (account detail from R004), `REC2` (cash flow from R006). The prompt's mapping (R004→REC1, R005→REC, R006→REC2, R007→REC.TOTL_CASH) is **confirmed**.

| Legacy record | Purpose | Refactor counterpart | Status |
|---|---|---|---|
| `REC` (`CCY_CD`, `TOTL_NORM_AMT`, `TOTL_SAV_AMT`, `TOTL_ACCT_AMT`, `TOTL_LOAN_AMT`, `TOTL_CASH`) | Per-currency summary | `currency_summary[]` (`currency_cd`, `total_cash`, `total_demand_deposit`, `total_term_deposit`, `total_loan`, `total_savings`) | ⚠️ shape maps cleanly (NORM↔demand, SAV↔savings, ACCT↔term, LOAN↔loan, CASH↔cash) **but values wrong per §1.2**. `TOTL_CASH` source confirmed (§1.5): legacy = `rec4`→`rec3` over `cash_repy_mstr` (out of refactor active-table scope); refactor = sum of `01`-coded detail rows = legacy **LOAN**. Refactor `total_cash` should be `0`, not loan-sourced. |
| `REC1` (16 fields incl. `BANK_NM`, `ACCT_NO`, `BAL`, `SND_CNT/AMT`, `RCV_CNT/AMT`, `EXPI_DT`, `AGMT_AMT`, `LOAN_NM`, `LST_COLL_DATE`) | Account detail | `accounts[]` (`AccountRow`, 16 fields) | ✅ field-complete; bank name resolved via `BankCdResolver` (`DailyCashReportService.java:74`) instead of IDO join — acceptable |
| `REC2` (`CCY_CD`, `BEF_DATE_BAL`, `BAL`, `SND_CNT/AMT`, `RCV_CNT/AMT`) | Cash flow per currency | `currency_flow[]` (`CurrencyFlow`) | ⚠️ shape present but: `before_date_balance` is **computed** (`closing - inflow + outflow`, `DailyCashReportService.java:99`) rather than read from `BEF_DATE_BAL`; values depend on the broken buckets; and the field is **never consumed by the FE**. |

### 3.3 Other data concerns

| Item | Legacy | Refactor | Status |
|---|---|---|---|
| Currency ordering | REC2 IDO `ORDER BY CASE WHEN CCY_CD='VND' THEN 1 ELSE 2 END` (VND-first, `IDO…R006.xml:38-39`); REC1 `ccy_cd desc` (`_act.jsp:66`) | (historical, superseded path) `findRows` `ORDER BY d.ccyCd ASC, …`; `LinkedHashMap` preserves first-seen | ⚠️ finding recorded against the superseded `findRows` path; re-verify ordering now that the active path calls `rept_0900_001_rec1..4` directly (the function carries the legacy VND-first ORDER BY). (OQ-3) |
| Server-side number formatting | None (raw strings) (`_act.jsp:200`) | None (raw `BigDecimal`) | ✅ both delegate to client |
| Empty data | Empty REC/REC1/REC2, no error (`_act.jsp:187-217`) | Empty lists, no error, audit still written (`DailyCashReportService.java:89-113`) | ✅ |
| `reqDate2` | Always == `reqDate` (single-day; vestigial second param) | Single `refDate` | ✅ |
| Audit logging | (Read action — no `AUDIT_LOGS_INFM` write observed in `_act.jsp`) | Writes audit row `wsvcId="daily-cash-report"` on every search (`DailyCashReportService.java:105-111`) | ⚠️ refactor adds audit (acceptable refactor convention) — but note **double-write on export** (§4). |
| Local currency | Dynamic `WABOOKSOSMCCurrencyUtil.getLocalCurrencyInfm()` (`_act.jsp:166`) | Hardcoded `LOCAL_CCY="VND"` (`DailyCashReportService.java:44`) | ⚠️ acceptable for VN R&D; tracked as OQ-3 (per-tenant base ccy) in feature doc |

---

## 4. Export parity

| Item | Legacy | Refactor | Status |
|---|---|---|---|
| Mechanism | **Server-side** Apache POI. Form POST of REC/REC1/REC2 JSON → `rept_0009_001_excel_osmc_vn.jsp` → XSSFWorkbook streamed back via hidden iframe (`view_osmc.jsp:86-90`; `_osmc.ts:895-917`; excel jsp `:1077-1617`) | **Client-side** `xlsx-js-style` workbook built in-browser, `xlsx.writeFile(...)` (`lib/dailyCashExport.ts:25,203-355`) | ❌ **violates** `wecms-web/CLAUDE.md §13b** (backend POI is the single XLSX producer; frontend `xlsx*` imports + `xlsx.writeFile` forbidden). A backend `DailyCashReportExportProvider` (`screenId="daily-cash-report"`) **exists** but the FE doesn't call it. (OQ-8) |
| Excel icon click | `download_excel` → submit hidden form (`_osmc.ts:129-131`) | `handleExportExcel` → `exportDailyCashExcel(...)` (client) (`DailyCashPage.tsx:155-177,242`) | ❌ wired to client path |
| File name | `{DAILY_CASH_REPORT}_{yyyyMMddHHmm}.xlsx` (excel jsp `:1616`) | `Daily Cash_{YYYYMMDDhhmm}.xlsx` (`DailyCashPage.tsx:175`; `dailyCashExport.ts:458-468`) | ⚠️ similar pattern; title string differs |
| Section order / layout | Title → last-update → as-of/ccy → grand total → summary (Current + Savings only) → Current Accounts table → Savings Accounts table; Cash/Time/Loan commented out (excel jsp `:1107-1418`) | Title → meta → per-currency summary band (Current/Savings) → Current Accounts → Savings Accounts (if rows) (`dailyCashExport.ts:217-310`) | ⚠️ broadly matches; **but rows are mis-bucketed (§1.3)** so the wrong accounts land in each section. Last-update line missing (no rept_0100). |
| Merged cells | Title A1:J2; per-row acct-no/balance/total merges (excel jsp `:393-416`) | Title A:J; mini-table + ledger merges (`dailyCashExport.ts:219,260-261,393-394,446`) | ⚠️ comparable |
| Amount format | USD `#,##0.00`, VND `#,###` (excel jsp `:301-311`) | USD/EUR decimal else integer (`dailyCashExport.ts:169-172`) | ✅ equivalent |
| Row limit | None (excel jsp `:835-976`) | None | ✅ |
| Audit logging on export | **None** in the excel jsp (only a session download-status flag) | Client path = **none** (cannot — never hits backend). Backend provider would write `audit.daily_cash_report.export.detail` if used. | ⚠️ client path produces no audit row. If routed to backend provider, note it writes **two** audit rows (search + provider) per export — verify intended. |
| Filters passed to export | Same `REQ_DATE`/`CCY_CD` as the read call, plus the full payload | Nothing sent to a server — export renders in-memory rows + pre-built labels only (`dailyCashExport.ts:155-177`) | ❌ no date/currency reaches a backend; export is a pure client render |

---

## 5. Print parity

| Item | Legacy | Refactor | Status |
|---|---|---|---|
| Mechanism | Clone `.form-wrap`, strip toolbar/title/search, `_v2common.app.callPrintThis(...)` (printThis-based) (`_osmc.ts:134-149`) | Hidden `<iframe>` + self-contained A4-landscape HTML → `iframe.contentWindow.print()` (`dailyCashExport.ts:504-549,551-672`) | ⚠️ both print a filtered/standalone DOM (not raw `window.print`). Different implementation, equivalent intent. |
| Shared helper | Framework helper | Bespoke `printDailyCashReport` — **not** the shared `lib/print.ts printReport` mandated by `wecms-web/CLAUDE.md §6d` (reuses only `escapeHtml`) | ⚠️ divergence from shared-print rule; lower priority than the bucketing bugs |
| Content | Summary + Current + Savings sections | Same sections, with the **mis-bucketed rows** (§1.3) | ❌ inherits the split bug |

---

## 6. Open Questions

| ID | Question | Why it's open |
|---|---|---|
| OQ-1 | ~~Exact `ACCT_TYPE_CD → NORM/SAV/ACCT/LOAN/CASH` summary-bucket mapping~~ | ✅ **RESOLVED 2026-06-04** (see §1.5). `rept_0900_001_rec2`/`rec4` read from DEV via `pg_get_functiondef`. Mapping: demand=`03,04`, savings=`05,06`, term=`02`, loan=`01`; `07/08` excluded from all summary buckets; `total_cash` is from `cash_repy_mstr` (out of refactor active-table scope) and must be `0`, never loan-sourced. No remaining ambiguity — Issue 1 in the fix plan is no longer blocked. |
| OQ-2 | Should the refactor display the last-aggregation timestamp (`rept_0100_001`)? | Legacy shows "영업집계 최종일시"; refactor has no equivalent endpoint. Needs PO decision on whether to port. |
| OQ-3 | Currency ordering — should the refactor be VND-first? | Legacy REC2 is VND-first; refactor is alphabetical ASC. Confirm desired order for the summary/flow lists and detail tables. |
| OQ-4 | Should the as-of date field be editable? | Legacy VN view disables it (today only). Refactor allows picking any date. Confirm intended behavior. |
| OQ-5 | Should Cash/Time-Deposit/Loan columns/sections be shown? | Legacy hides/comments them out in the VN view; refactor renders a Cash summary column and an (empty) Holdings table. |
| OQ-6 | `available` semantics in the summary | Refactor sets `available = total_demand_deposit`; legacy grand total summed all buckets. Define the correct "available" formula. |
| OQ-7 | Checkbox column on the ledger tables | Legacy has none; refactor renders an unwired checkbox column. Remove for parity, or is row-selection a deliberate new feature? |
| OQ-8 | Move Excel export to the backend POI provider? | A `DailyCashReportExportProvider` already exists server-side. Confirm cutover from the client-side `xlsx-js-style` path (which violates §13b) to the shared `useExcelExport` + backend POI path. |

---

## 7. Final decision

**Original (2026-06-03): FAIL.** The page renders and the request/response shapes broadly line up, but the **account-type bucketing is wrong on both the backend (summary totals) and the frontend (Current/Savings split)** — the core data the report exists to show is incorrect for real accounts. Secondary issues (client-side export vs. backend POI, missing last-aggregation timestamp, non-VND-first ordering, extra checkbox column, unformatted on-screen amounts) compound the gap.

**Updated (2026-06-04): PARTIAL PASS.** Both correctness-critical bugs (§1.2 backend, §1.3 frontend) are **fixed and verified** (DB-equal to legacy `rec2`; unit-tested; build/lint clean), and the summary block now matches the legacy/Daily-Financial shape with a zero VND/USD/KRW fallback. The remaining items are all **non-critical or PO-gated**: client-side export vs. backend POI (§4), live-recompute vs. snapshot data source (§3), per-tenant local currency, last-aggregation timestamp, unwired checkbox column, bespoke print path, and detail-cell amount formatting. Daily Cash can proceed to the non-critical parity cleanup backlog. See the "Post-fix status" banner at the top.

**Must-fix before parity sign-off:** §1.2 (backend codes) and §1.3 (frontend split) — see [fix-plan.md](./fix-plan.md). The summary-bucket mapping (OQ-1) is now **resolved against the DB** (§1.5), so Issue 1 is unblocked: demand=`03,04`, savings=`05,06`, term=`02`, loan=`01`, cash=`0` (out of scope), `07/08` excluded.
