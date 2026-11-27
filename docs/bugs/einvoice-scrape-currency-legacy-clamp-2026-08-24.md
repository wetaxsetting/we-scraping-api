# e-Invoice Scrape — Missing Legacy Currency Resolution (chk07 crash + empty currency bucket)

**Date:** 2026-08-24
**Type:** BUG FIX / PARITY FIX
**Current Work Classification:** BUG
**Status:** READY FOR DBA
**Repository:** wecms-api (+ paired data repair; no wecms-web change)
**Issue:** —
**Reported from:** SIT — `https://wecms.kosign.dev/addon/expenses-einvoices`

## Problem

Two symptoms, reported separately, that turned out to share one root cause.

1. **`WBE_DB` on Update.** `POST /api/v1/addon/einvoices/scrape-detail-results` returned
   HTTP 500 / `WBE_DB` and rolled back the entire 53-invoice batch.
   `trace_id = 20260824135803PT33011246736568DEVL`.
2. **Invoices with no currency.** The Expenses e-Invoices footer showed a
   `Total ()` bucket — `9 transaction(s) · 7.837.798 · tax 458.541` — alongside
   `Total (VND) 43` and `Total (USD) 1`.

## Current Behavior (before the fix)

**Symptom 1.** PostgreSQL rejected the master UPDATE:

```
ERROR: new row for relation "busa_doc_mstr" violates check constraint "busa_doc_mstr_chk07"
```

The offending invoice is `1K26TTT-22277-0309103635-018` — `einv_invoicesymbol = 'K26TTT'`,
`einv_testresults = '1'`, `einv_rate = 17.93`, seller `0309103635-018`
(NGÂN HÀNG SHINHAN — CHI NHÁNH ĐÔNG SÀI GÒN). The GDT engine reported
`currency_cd = "KRW"` and the service wrote it verbatim.

`busa_doc_mstr_chk07` — **legacy JEX DDL**, verified identical on the live JEX DEV DB
and on the refactor dev DB:

```sql
CHECK (trco_trsc_ccy_cd = ANY (ARRAY['VND','USD','999','998']))
```

**Why the stack trace pointed at the wrong frame.** The loop stamped `KRW` onto the
managed `BusaDocMstr` in iteration *N*; iteration *N+1* then called
`EInvoiceTrcoAutoRegistrar.writeActivityHistory` → `LegacyFunctionRepository.nextTrcoActvSeq`,
whose native query triggers a Hibernate **auto-flush** — which is where the dirty row
finally hit the DB. `nextTrcoActvSeq` is an innocent frame; the key generator is not at fault.

**Symptom 2.** All 9 rows in the footer bucket are **POS e-Invoices**
(`einv_testresults = '2'`; symbols `C26MGA`, `C26MCC`, `C26MAA`, `C26MHO`, `C26MYY`,
`C26MVV`, `C26MMC` ×2, `C26MDB`) with `TRCO_TRSC_CCY_CD IS NULL`.

## Expected Behavior

Match legacy JEX: the master carries `VND` for both cases.

## Verified Root Cause

`EInvoiceDetailScrapeService` replaced legacy's **three-branch currency resolution** with a
naive pass-through (`normalizeCcy` — mapped `VNĐ`→`VND` and truncated to 3 chars, nothing else).

Legacy clamps the engine currency **before any write** —
[`nxisas_einvoice_c002_act.jsp:239-260`](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c002_act.jsp):

| Invoice kind | Legacy rule |
|---|---|
| **K** (`einv_testResults='1'` OR `invoiceSymbol` starts `K`) | blank → (`totalAmt` has no `,` AND ≥ 1000) ? `VND` : `999`; non-blank and not USD/VND → **`999`** |
| **POS** (`'2'`) | blank → **`VND`** (ticket #38058) |
| **C** (other) | untouched |

then writes the master via `trcoInfm.taxInfo` at `:605-611` / `:414-419` — default `VND`,
overridden **only when the clamped value is not `CcyCd.NONE`**, so `999` never reaches the
master and collapses back to `VND`.

**One missing clamp, two branches, two symptoms:**

| | Engine response | Legacy insert | Refactor insert (before fix) |
|---|---|---|---|
| POS, no `currency` | blank — expected | `blank → VND` | wrote nothing → **NULL** |
| K invoice, `KRW` | `KRW` | `KRW → 999 → VND` | wrote `KRW` → **chk07 crash** |
| C invoice, `VND` | `VND` | `VND` | `VND` ✓ |

### The scrape response is NOT at fault

Explicitly ruled out — the defect is entirely in the write path.

- **POS detail responses genuinely omit `currency`.** The same engine `invoiceDetail`
  object populated `einv_title`, `einv_paymenttype`, `einv_totalamtinletter` and
  `trco_cmnm` on those rows (e.g. `1C26MCC-11574-0317254980` → title
  `HOÁ ĐƠN GIÁ TRỊ GIA TĂNG`, paytype `TM/CK`). The response arrived, decrypted and
  parsed correctly; only `currency` was absent.
- **No frontend key mismatch.** [`einvoiceScrape.ts:659`](../../../wecms-web/lib/nxisas/einvoiceScrape.ts)
  reads `str(d.currency ?? src.currency_cd)` — the same key legacy reads
  (`invoiceDetail.getString("currency")`, `c002:239`).
- **Structural, not intermittent.** 0 of 407 POS rows carry a currency across
  `20260101`–`20260820`, while 1,143 C invoices *do* hold VND through the same code path.
- **Legacy anticipated it.** The POS branch exists solely to compensate for the absent
  field (ticket #38058 in the source comment).

### Legacy data confirms the rule

| DB | scope | VND | USD | `998` | NULL |
|---|---|---|---|---|---|
| Live JEX DEV | all `busa_doc_mstr` | 23,407 | 508 | 1 | 9,902 |
| Live JEX DEV | POS (`einv_testresults='2'`) | **467** | 0 | 0 | 78 |
| Refactor dev DB | POS, tenant `VN_VNM0004_000001` | **0** | 0 | 0 | **407** |

Legacy K e-Invoices carrying a real FX rate store `VND` or `USD`, never the foreign code —
e.g. `1K24TCG-98262-…` rate `25464.00` → `USD`; `1K24TCG-98517-…` rate `1.00` → `VND`.

## Scope

- Restore the legacy K / POS / C currency resolution in the phase-2 detail scrape.
- Apply the same resolution in the phase-1 list upsert, which also wrote the raw code.
- Regression test pinning all three branches.
- Manual DBA backfill for the rows already written wrong.

## Out of Scope

- **`busa_doc_mstr_chk07` is not modified.** It is legacy JEX DDL and is correct. Widening
  it to admit `KRW`/`KHR` would diverge from the JEX schema and break forward
  data-migration safety.
- **No DB function change.** `wb_get_next_val` is not involved.
- `BUSA_DOC_DETL.CCY_CD` — legacy writes it at `c002:776` (the `999` value); the refactor
  never has. No constraint there, so it is a silent data gap, not an error. Separate record.
- Phase-1's speculative `inv.currencyCd` read — see Known Limitations.

## Implementation

| File | Change |
|---|---|
[`EInvoiceDetailScrapeService.java`](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailScrapeService.java) | New `resolveMasterCcy(...)` (row overload + 5-arg overload) reproducing the legacy K/POS/C clamp incl. the blank-currency `no-separator && ≥1000 → VND` heuristic (`looksLikeWholeVnd`), plus `CCY_VND/USD/ETC/NONE` constants. Call site at line 148 switched from `normalizeCcy` to `resolveMasterCcy`. `normalizeCcy` left untouched so `EInvoiceTrcoAutoRegistrar:485`'s USD test is unaffected. |
[`EInvoiceScrapeService.java`](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceScrapeService.java) | Line 217 routes `row.currencyCd()` through the same resolution before `blankToExisting`. Legacy `c001` writes no currency at all (`grep -c currency` → 0), so clamping is strictly closer to legacy than the previous raw write. |
[`EInvoiceDetailScrapeCurrencyTest.java`](../../src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailScrapeCurrencyTest.java) | New — 8 cases across K / POS / C, with the reported invoice as the first assertion. |

### Deliberate deviation from legacy (1)

On the **C branch** legacy leaves the currency raw, so a C invoice reporting `KRW` would hit
the identical constraint error *in JEX too*. Since the column cannot store it, the refactor
clamps that residual case to `998` (`CcyCd.ETC` — "Others") with a WARN rather than failing
the batch. `998` is a genuine legacy value (1 live row uses it). Reversible on request.

## API Impact

None — no endpoint, DTO, or wire-field change. Same request/response shape.

## UI Impact

Indirect, no `wecms-web` change. After the backfill the `Total ()` footer row disappears and
its 9 transactions fold into `Total (VND)` → `52 transaction(s) · 140.406.067`.

A residual `Total ()` bucket remains by design (see Known Limitations OQ-1).

## Database Impact

**MANUAL DBA SCRIPT** — DML only. No DDL, no schema change, no DB-function change.

Script: [`docs/sql/2026/backfill-einvoice-currency-2026-08-24.sql`](../sql/2026/backfill-einvoice-currency-2026-08-24.sql)

- **Affected table/column:** `BUSA_DOC_MSTR.TRCO_TRSC_CCY_CD`
- **Current state:** `NULL` on detail-enriched K/POS rows the refactor wrote before the fix.
- **Target state:** `'VND'` (the value legacy's resolution produces).
- **Rows (verified 2026-08-24 on `172.168.5.172/wecmsdevdb`):** **304** —
  300 POS (`einv_testresults='2'`) + 4 K (`'1'`), tenant `VN_VNM0004_000001` only,
  `issu_dt 20260101..20260820`. **0 of the 304 carries an FX rate**, so no genuinely
  foreign-currency invoice is in scope.
- **Deliberately excluded:** 107 POS + 38 K phase-1-only rows (never detail-scraped — legacy
  leaves those NULL too; the application stamps them on the next Update), and 24
  blank-currency C invoices (legacy stores NULL — see OQ-1).
- **Execution order:** application fix deployed first (otherwise the next scrape re-creates
  NULLs), then the script. Independent of any other pending script.
- **Rollback:** non-destructive (`NULL → 'VND'`, nullable column, nothing deleted) but **not
  self-reversing** — requires the `doc_id` list from pre-check 1.2. Script §4 carries the
  template. Re-running after a rollback is idempotent.
- **UAT / PROD:** requires a **fresh count and a separate PO decision**. Legacy-written rows
  on the live JEX DB also carry NULL (78 POS + 825 K on JEX DEV) — legacy's own gaps,
  predating #38058 or never detail-scraped. Those are out of scope; the `biz_key` filter is
  what keeps the script off them and must not be removed.

## Database Execution Status

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| LOCAL | No | — | NOT APPLICABLE | - | no affected rows locally |
| SIT (`172.168.5.172/wecmsdevdb`) | Yes | DBA TEAM | **COMPLETE** | 2026-08-25 | post-check 3.3 output supplied by the team; 304 rows updated (POS 300 + K 4). See "Post-backfill verification" below. |
| UAT | TBD | DBA TEAM | NOT STARTED | - | fresh count + PO decision required first |
| PROD | TBD | DBA TEAM | NOT STARTED | - | fresh count + PO decision required first |

## Post-backfill verification (2026-08-25)

Backfill executed by the team on the SIT/dev DB. Post-check 3.3 returned:

```
einv_testresults  ccy    count
' ' (C)           VND    1143      ' ' (C)   NULL   24     ' ' (C)  USD  1
'1' (K)           VND     172      '1' (K)   NULL   38     '1' (K)  USD  7
'2' (POS)         VND     300      '2' (POS) NULL  107
NULL              VND       2
```

POS `VND` went 0 → 300 and K `VND` 168 → 172: **304 rows updated, exactly as predicted.**
No value outside `VND | USD | 999 | 998` exists (post-check 3.2 clean).

### Residual NULLs are the intentional exclusions — 169 rows

UI after the backfill: Expenses `Total ()` = 168, Sales `Total ()` = 1 → **169**, reconciling
with 107 POS + 38 K + 24 C.

Re-verified 2026-08-25 against `BUSA_DOC_DETL` rather than the enrichment-field proxy used
when the script was authored — **all 169 rows have ZERO detail line items and zero enrichment
fields.** They were never detail-scraped, so they are missing the invoice title, payment type,
seller address and all product/tax lines, not only the currency. The proxy and the
authoritative test agree; the exclusion was correct. Writing `VND` there would invent a value
legacy does not hold either (legacy phase-1 `c001` writes no currency at all).

| doc_type | einv_testresults | NULL rows | detail lines | enriched |
|---|---|---|---|---|
| `B` | `' '` C | 23 | 0 | 0 |
| `B` | `'1'` K | 38 | 0 | 0 |
| `B` | `'2'` POS | 107 | 0 | 0 |
| `I` | `' '` C | 1 | 0 | 0 |

**Why they lack detail — collateral of this bug.** 153 of the 169 (90%) sit in
`20260302`–`20260331`, the exact range of the `WBE_DB` failure: that transaction rolled back,
discarding the whole batch's enrichment.

```
doc_type  month    rows   range
B         202603    153   20260302 – 20260331   <- the crashed batch
B         202606     13   20260623 – 20260630
B         202602      2   20260211 – 20260223
I         202502      1   20250228
```

### Remediation — re-scrape, not a second backfill

The correct repair is a **detail re-scrape** (recovers real data: currency *and* the missing
titles, payment types and line items). Do **not** extend the backfill.

Prerequisite: **the code fix must be deployed to SIT first.** As of 2026-08-25 no
`busa_doc_mstr` row has been modified since `20260824074822`, so the fix is not yet exercised;
the March range still contains the `KRW` invoice and would crash again.

| Screen | From | To | Rows recovered |
|---|---|---|---|
| Expenses e-Invoices | 2026-02-01 | 2026-03-31 | 155 |
| Expenses e-Invoices | 2026-06-01 | 2026-06-30 | 13 |
| Sales e-Invoices | 2025-02-01 | 2025-02-28 | 1 |

Expected end state: `Total ()` at or near zero. The March payload shows C invoices *do* carry
`currency_cd: "VND"`, so most of the 24 C rows should resolve too; anything left is OQ-1.

**Caveat:** the Feb-2025 Sales invoice is ~18 months old and may fall outside GDT's query
retention window. If the scrape returns no data, that row stays NULL and is not repairable
from the application side.

## Integration Impact

None. No change to the NXiSAS/GDT gateway contract, the decrypt path, or the frontend
scrape payload shape.

## Verification

### Automated Tests

- Test: `EInvoiceDetailScrapeCurrencyTest` (8 cases) — K foreign→VND (the reported invoice),
  K VND/USD passthrough, K blank-amount heuristic, POS blank→VND, POS passthrough,
  C blank→leave-existing, C passthrough, C residual→998.
- Command: `./gradlew test --tests "com.kosign.wecms.domain.einvoice.*"`
- Result: **PASS** — 112 tests, 0 failures, 0 errors across 10 e-invoice test classes.

### Test Scenarios

- Scenario: invoice `K26TTT-22277` (`einv_testresults='1'`, `currency_cd='KRW'`).
  Expected `VND` · Actual `VND` · **PASS** (unit).
- Scenario: POS invoice, blank `currency_cd`. Expected `VND` · Actual `VND` · **PASS** (unit).
- Scenario: SIT re-run of Update over `20260301`–`20260331` → batch completes, no `WBE_DB`.
  **NOT RUN** — requires a SIT deploy of the fix.

### Regression Review

- Areas checked: `normalizeCcy` left byte-identical, so
  `EInvoiceTrcoAutoRegistrar:485` (`"USD".equals(normalizeCcy(raw))`) is unaffected —
  covered by `EInvoiceTrcoAutoRegistrarTest` 18/18 PASS. `EInvoiceScrapeServiceTest`
  12/12 PASS covers the modified phase-1 line. Export/display paths read the column
  unchanged (`EInvoiceExportProviderTest` 17/17, `EInvoiceDetailWorkbookWriterTest` 8/8).
- Result: no regression found in the e-invoice module.

### Database Verification

- `chk07` definition confirmed identical on the live JEX DEV DB and the refactor dev DB.
- Legacy value distribution confirmed (tables above).
- Backfill predicate executed read-only against the refactor dev DB → 304 rows, 0 with an
  FX rate. **Backfill itself: NOT RUN — PENDING DBA.**

### Manual Verification

- Environment: SIT
- Steps: re-run Add-on Service > Expenses e-Invoices > Update; confirm no `WBE_DB`, then
  confirm the footer shows `Total (VND) 52` and no `Total ()`.
- Result: **NOT RUN** — blocked on the SIT deploy + the DBA backfill.

## Test Evidence

Test result: [e-Invoice Scrape Currency Clamp — Test Result](../testing/results/einvoice-scrape-currency-clamp-test-result-2026-08-24.md)

Final test result: PASS (unit) · Regression coverage: PASS · SIT verification: NOT RUN

## Result

Code fix complete and unit-verified; the data repair is prepared and awaiting the DBA.

```
Code: COMPLETE · Tests: PASS · DB script: PREPARED · SIT DB execution: PENDING
Overall: READY FOR DBA  (then READY FOR MANUAL COMMIT)
```

No commit, push, or deploy performed.

## Known Limitations / Follow-up

**OQ-1 — blank-currency C invoices store NULL (legacy defect, reproduced deliberately).**
At `c002:605-611` legacy sets the `VND` default and then immediately overwrites it:
`CcyCd.fromValue("")` returns `null` (no enum constant matches `""`), and `null != CcyCd.NONE`
is true, so the override fires with `null`. This is very likely unintended and explains the
9,902 NULL rows on JEX DEV. Reproduced faithfully rather than "fixed", because changing it
would write different values than JEX for the same invoice.
**Decision needed from the PO (Mr. Jung):** accept the NULL bucket, or default blank-currency
C invoices to `VND` in the refactor. 24 rows affected on the dev DB.

**OQ-2 — `totl_amt` is not converted when the currency label is folded.** Legacy relabels a
KRW invoice as `VND` but leaves `totl_amt` as reported (`76,676,400`) with the rate in
`einv_rate` (`17.93`), so the amount may render as ₫ when it is actually KRW. USD escapes
this because `USD` is an allowed code. Legacy behaviour reproduced exactly.
**Decision needed from the PO** — any conversion changes stored data and diverges from JEX.

**FU-1 — UI label for the NULL bucket.** `Total ()` would read better as
`Total (No Currency)`, matching `CcyCd.NONE.getLabel()` in
[`CcyCd.java`](../../../WECMS%20DEV/WECMS/src/wabooks/enums/CcyCd.java). `wecms-web` change,
not started.

**FU-2 — speculative phase-1 key.** [`einvoiceScrape.ts:553`](../../../wecms-web/lib/nxisas/einvoiceScrape.ts)
reads `str(inv.currencyCd)` from the engine's `invoiceList`. Legacy `c001` reads no currency
field at all, so there is no legacy authority that the key exists. Inert today (`str()` → `""`
→ `blankToExisting` keeps the existing value) and now clamped, but the unit-test fixtures
hardcode `currency_cd: "VND"` on list rows, which would mask the real engine never sending it.
Verify against the Coocon/NXiSAS spec or drop to match `c001`. **Not touched.**

**FU-3 — `BUSA_DOC_DETL.CCY_CD` never written.** Legacy sets it at `c002:776`. No constraint,
so no error — a silent data gap. Needs its own record.
