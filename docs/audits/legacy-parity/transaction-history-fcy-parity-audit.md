# Transaction History (FCY) Refactor Parity Audit

> **System:** Refactor (Spring Boot `wecms-api` + Next.js `wecms-web`) vs Legacy JEX `fnsh_0006_001`
> **Audit date:** 2026-06-06 · **Auditor:** Legacy parity audit (source-verified)
> **Legacy slug:** `transaction-history-fcy` · **Refactor route:** `/banking/transaction-history-fcy/{acctSeqNo}` where `acctSeqNo = {bankCd}_{acctNo}`
> **Doc location rationale:** refactor parity audits live under `wecms-api/docs/audits/` per [wecms-api/CLAUDE.md](../../../CLAUDE.md) ("DB parity audits (refactor vs JEX) → docs/audits/"). The prompt-suggested `docs/jex/...` location is rejected — `docs/jex/` is read-only JEX functional reference. This audit supersedes the field-level conclusions of the earlier draft [docs/jex/features/transaction-history-fcy/refactor-gap-analysis.md](../../../../docs/jex/features/transaction-history-fcy/refactor-gap-analysis.md) (corrections noted in §1).

---

## 0. Resolution Status (updated 2026-06-06)

The SIT-readiness fix pass landed. All five SIT/UAT blockers are **CLOSED**. Verification detail: [transaction-history-fcy-fix-verification.md](../historical/transaction-history-fcy-fix-verification.md).

| Item | Severity | Status | Fix (file:line) |
|---|---|---|---|
| **BUG-1** — "Confirmed by" blank in list grid | Medium | **CLOSED** | `TransactionService.toRow` now populates `CONFIRMED_BY`/`CONFIRMED_DTM` from the confirmed `ACCT_TRNS_RCV_PAY_INFM` row (`confirmedRowsBySeq` map) — `TransactionService.java:907-909` |
| **BUG-2** — Transaction ID / File Name / Matched date+by columns unbacked | Medium | **CLOSED** | `TransactionRow` gained `BANK_TRNS_SHORT_ID`/`PAY_ORG_FILE_NM`/`MATCHED_AT`/`MATCHED_BY`; `matchInfoBySeq` batch-loads `BUSI_MATCH_LOG` + `USER_INFM` + `SCDUL_PYMN_SUMR`; frontend `mapTransactionRow` + `ApiTransactionRow` wire them; export provider fills the columns |
| **BUG-3** — keyword placeholder over-promised | Low | **CLOSED (Option A)** | Backend `matchesKeyword` widened to amount + contact name + purpose + VAS + transaction-id (+ existing remark/remark2/ref) — `TransactionService.java` `matchesKeyword`. Placeholder now accurate; no frontend change |
| **#5** — currency (`CCY_CD`) filter missing | Medium (UAT) | **CLOSED** | `resolve()` recovers the account currency from `ACCT_MSTR`; `fetchRows()` filters rows to that currency — applies to list/summary/detail/export — `TransactionService.java` `resolve`/`fetchRows` |
| **#29** — per-account authorization missing | Medium (UAT) | **CLOSED** | New `AccountAccessGuard.assertCanView` (admin bypass else `ACCT_AUTH_INFO` `AUTH_TYPE='V'`) enforced in `TransactionService.resolve()` (list/detail/export/confirm/undo/classify/reporting) and `MatchService.requireBankRow()` (match-candidates/match/unmatch); throws 403 `WBE1003` |
| **R11** — Smart Organizer operates tenant-wide | **Critical (SIT Blocker)** | **CLOSED** | Final PO decision reverses the earlier "accepted modernization": Smart Organizer must preserve legacy account scope. `SmartOrganizerService.listEligible`/`confirm` now require + filter on `BIZ_KEY + BANK_CD + ACCT_NO`, apply `AccountAccessGuard`, and reject confirm rows from other accounts; controller endpoints gain the account params + `@PreAuthorize`; the frontend threads the selected account from Transaction History into the page + API calls |

> **R11 final PO decision (2026-06-06).** The earlier classification of Smart Organizer's tenant-wide scope as **MODERNIZATION / Accepted Deviation** is **overruled**. Smart Organizer **must preserve the legacy account scope** — it operates **only on the account currently selected in Transaction History**, filtered by `BIZ_KEY + BANK_CD + ACCT_NO`, and **must not operate tenant-wide**. Reclassified **GAP — Needs Developer Fix — Severity: Critical — SIT Blocker**, and now fixed (see [fix-verification §R11](../historical/transaction-history-fcy-fix-verification.md)).

**Verdict change:** §16 moves from **CONDITIONAL GO** to **GO FOR SIT** (all blockers incl. R11 closed).

---

## 1. Executive Summary

The Transaction History (FCY) screen **is implemented full-stack** in the refactor — and the implementation is **substantially more complete than the pre-existing `refactor-gap-analysis.md` claimed**. That gap-analysis draft (and a first-pass backend sub-agent) read only `TransactionService.java` and consequently reported several features as MISSING that are in fact present in sibling classes. This audit corrects those errors from source.

**Major corrections to the prior gap-analysis (all source-verified in §4):**

| Prior claim | Reality | Evidence |
|---|---|---|
| "No dedicated FCY feature; FCY would be a parameterisation" | **FCY route, page, menu authz slug, and i18n exist as a first-class scope** | `wecms-web/app/(dashboard)/banking/transaction-history-fcy/[accountId]/page.tsx`; menuAuth slug `banking-fcy-inquire-transactions` |
| "No `TransactionController` / endpoints" | **All 14 transaction endpoints exist on `AccountController`** (`/api/v1/banking/...`) | `AccountController.java:244-516` |
| #14 Payment match/unmatch **MISSING** | **PRESENT** — `MatchService` + 3 endpoints (`match-candidates`, `match`, `match` DELETE) | `AccountController.java:463-501`; `MatchService.java` |
| #12 Auto-classification (`RMRK_ORG_INFM`) **MISSING** | **PRESENT but relocated** to the Smart Organizer feature | `domain/banking/smartorganizer/entity/RmrkOrgInfm.java`, `SmartOrganizerService.java` |
| #20 authorization "per-account not verified, menu authz not confirmed" | **Per-endpoint `@PreAuthorize` menu authz is live with an FCY-specific slug** | `AccountController.java:245,262,...` |
| i18n KO/VI "UNVERIFIED" | **EN/KO/VI all carry the 66 `txn_history` keys** | `wecms-web/locales/{en,ko,vi}.json` |

**What is genuinely incomplete (the real gaps):**

1. **BUG — `CONFIRMED_BY` / `CONFIRMED_DTM` always null in the list grid.** `toRow` hard-codes both to `null` (`TransactionService.java:765-766`) even though the data exists and `detail()` returns it. The grid's "Confirmed by" column is therefore blank for every row in list view.
2. **MISSING — grid columns without backing list data.** `TransactionRow` has **no** `BANK_TRNS_SHORT_ID` (Transaction ID), `PAY_ORG_FILE_NM` (File Name), or `MATCHED_AT/MATCHED_BY` (Matched date/by) fields, yet the UI grid + export config declare those columns ("pending DTO extension"). They render empty.
3. **MISSING — currency (`CCY_CD`) filter.** The list query scopes by `bizKey + bankCd + acctNo [+ date]`, never `ccyCd` (`TransactionService.java:660-665`). Mitigated by single-currency-per-account scoping, but legacy does not assume that.
4. **MISSING / narrower — keyword scope.** Refactor matches only `RMRK/RMRK2/BANK_REF_NO` (`TransactionService.java:681-691`); legacy ORs across 8+ columns incl. amount, VAS, contact, transaction-id, memo. The frontend placeholder **over-promises** ("Search for amount, contacts name, purpose") relative to what the backend searches.
5. **MISSING — list-column sorting.** Fixed `TRSC_DT DESC, TRSC_TM DESC`; no sort whitelist for the transaction list (Smart Organizer has one, the transaction list does not).
6. **MISSING (deferred) — bank-specific rules.** Vietcombank `11704004` balance recompute and bank `11704020` remark rule are absent (consistent with the refactor-wide product/bank-gate deferral).
7. **MISSING — inline auto-classification display + memo side panel** in the transaction grid (auto-classification exists, but only inside Smart Organizer; memo is not ported to this screen).
8. **MODERNIZED — paging.** Load-all + in-memory filter + client-side pagination, vs legacy DB-paged contract.

**Strong-parity areas (confirmed MATCH):** date / in-out / confirm filters; deposit/withdraw/confirmed/unconfirmed totals; confirm/undo; purpose / VAS / contact classify+clear; reporting-account set+clear; payment match/unmatch; Smart Organizer; server-side Excel export; amount formatting (2 dp); tenant scoping; per-endpoint menu authorization with an FCY-specific permission code.

**Verdict: CONDITIONAL GO for SIT** — the screen is functionally wired end-to-end, but two list-grid defects (Confirmed-by blank; Transaction-ID/File-Name/Matched columns unbacked) and the keyword-placeholder mismatch are visible-to-tester regressions that should be fixed first. See §16.

---

## 2. Audit Scope

This audit covers the 15 areas in the audit prompt: route/menu/account selection, search conditions, API contract, DB query parity, sorting/pagination, confirmation logic, deposit/withdraw logic, keyword search, popups/write APIs, Smart Organizer, Excel download, scraping relationship, bank-specific rules, authorization/tenancy, and UI parity. Both trees were read from source:

- **Legacy (read-only reference):** `fnsh_0006_001_osmc_VN.ts`, `fnsh_0006_001_r001_act.jsp`, `FnshHelper.java`, `SortHelper.java`, `IDO.ACCT_TRNS_HSTR_R016(.|_CNT.)xml`, `WSVC.fnsh_0006_001_r001.xml`, plus the nine prepared docs under `docs/jex/features/transaction-history-fcy/`.
- **Refactor backend:** `domain/banking` (`AccountController`, `TransactionService`, `MatchService`, DTOs, `TransactionHistoryExportProvider`), `domain/banking/smartorganizer` (`SmartOrganizerController/Service`, `RmrkOrgInfm`), entities/repositories.
- **Refactor frontend:** `app/(dashboard)/banking/transaction-history-fcy/**`, the shared `TransactionHistory*` components + `TransactionHistoryClient`, `lib/api/banking.ts`, `locales/{en,ko,vi}.json`.

Out of scope: scraping/sync engine internals (NXiSAS/Coocon), and the live-system behaviour of features not surfaced by this screen.

---

## 3. Legacy Source Evidence

All file:line + verbatim code below were re-read from the live JEX tree.

**3.1 Currency + account-type filter** — `FnshHelper.java:654-656, 714-723`
```java
if (null != ccyCd) { dQuery.append("\n and ATH.ccy_cd = ? "); dParam.add(ccyCd.getValue()); }
...
} else if (ccyCd == CcyCd.USD) {
    dQuery.append("\n and AM.ACCT_TYPE_CD in (?,?,?)");
    dParam.add(AcctTypeCd.CURRENT_USD.getValue());
    dParam.add(AcctTypeCd.SAVINGS_USD.getValue());
    dParam.add(AcctTypeCd.DEPOSIT.getValue());
}
```

**3.2 Date / in-out / confirm filters** — `FnshHelper.java:739-746, 757-760`
```java
dQuery.append("\n and ATH.trsc_dt between ? and ? "); dParam.add(FROM_DT); dParam.add(TO_DT);
if (isNotBlank(INOUT_DV)) { dQuery.append("\n and ATH.inout_dv = ? "); dParam.add(INOUT_DV); }
if (isNotBlank(CNFM_YN)) { dQuery.append("\n and coalesce(ATRPI.CNFM_YN , 'N') = ? "); dParam.add(CNFM_YN); }
```

**3.3 Keyword OR-block (8 legs)** — `FnshHelper.java:669-707`: amount (deposit only) · `ATH.acct_no` · `wb_get_translate_text(ATH.rmrk)` (VN only) · `ATH.rmrk` · `ATRPI.VAS_ACCT_CD` · `ATRPI.VAS_ACCT_NM` · `ATRPI.RCV_PAY_TRCO_NM` · `ATH.BANK_TRNS_SHORT_ID` · memo `exists(...)`.

**3.4 Sorting** — `SortHelper.java:1980-2060`. Whitelist branch keyed on `fnsh_0006_001_r001`; default `ath.trsc_dt:desc` + a hand-reg/`trsc_tm`-or-`acct_seq_no` CASE tie-breaker; NULLS FIRST(asc)/LAST(desc).

**3.5 Totals** — `IDO.ACCT_TRNS_HSTR_R016_CNT.xml:24-30`: `TOT_CNT`, `TOT_IN_AMT` (`INOUT_DV='1'`), `TOT_OUT_AMT` (`INOUT_DV='2'`), `TOT_CNFM_CNT` (`CNFM_YN='Y'`), `TOT_UN_CNFM_CNT` (`='N' OR NULL`).

**3.6 Auto-classification (`AUT_CLS_*`)** — `IDO.ACCT_TRNS_HSTR_R016.xml:133-152` lateral `RMRK_ORG_INFM` best-match (`MATCH_TYPE='E'` exact / else `ILIKE`, `ORDER BY DISP_ORD LIMIT 1`); display names via `wb_get_*` at `:97-104`.

**3.7 Display-name resolution** — `IDO.ACCT_TRNS_HSTR_R016.xml:31,91,98,100,103,109`: `wb_get_bank_nm`, `wb_get_usag_nm_new`, `wb_get_trco_nm`, `wb_get_vas_acct_nm`, `wb_get_chart_acct_nm`.

**3.8 Vietcombank `11704004` balance recompute** — `IDO.ACCT_TRNS_HSTR_R016.xml:43-73`: when `ATH.BANK_CD='11704004'`, `COALESCE(ATH.BAL, AM.BAL − SUM(later txns))`; else `ATH.BAL`.

**3.9 Bank `11704020` remark rule (#37692)** — `fnsh_0006_001_r001_act.jsp:69-82`: `11704020` → withdrawal shows `RMRK2`, deposit shows `RMRK`; other banks merge `"{RMRK2} - {RMRK}"`.

**3.10 Amount scaling** — `fnsh_0006_001_r001_act.jsp:54-57`: `setScale(2, HALF_UP)` on `BAL/IN_AMT/OUT_AMT/TRSC_AMT`.

**3.11 Authorization** — `FnshHelper.java:659-660` (`ATH.biz_key = loginUser.getBizKey()`), `:764-767` (`!isAdmin()` → `QueryHelper.getPermittedBankAccountDynamic(...)` per-account scope). WSVC `<login>Y`, `NORM_USER_ONLY_YN=Y`.

**3.12 Payment-match columns** — `IDO.ACCT_TRNS_HSTR_R016.xml:154-182` lateral `BUSI_MATCH_LOG` (LOCKED_YN='Y') → `MATCHED_BY (UI.USER_NM)`, `MATCHED_AT (BL.MOD_DTM)`, file name from `SCDUL/OVRS_PYMN_SUMR`. Allow-listed banks `11704020/11704088/970457/970424` on withdrawals (`fnsh_0006_001_osmc_VN.ts:1219-1244`).

**3.13 WSVC contract** — `WSVC.fnsh_0006_001_r001.xml`: `<in>` = `SRCH_WD, FROM_DT, TO_DT, INOUT_DV, BANK_CD, ACCT_NO, PAGE_PER_CNT, PAGE_NO, CNFM_YN, CCY_CD`; `<out>` scalars `TOT_*` + `REC` (60 subfields incl. `BANK_TRNS_SHORT_ID`, `MATCHED_BY/AT`, `PAY_ORG_FILE_NM`, `RCPT_NM`, `AUT_CLS_*`, `CNFM_BY_USER_NM/CNFM_DTM`).

---

## 4. Refactor Source Evidence

**4.1 Controller (all endpoints exist)** — `AccountController.java` (`@RequestMapping("/api/v1/banking")`):

| Method + path (relative) | Service call | Line |
|---|---|---|
| `GET /accounts/{acctSeqNo}/transactions` | `transactionService.list(...)` | 244-253 |
| `GET /accounts/{acctSeqNo}/transactions/summary` | `transactionService.summary(...)` | 261-270 |
| `GET /accounts/{acctSeqNo}/transactions/{trnsSeqNo}` | `transactionService.detail(...)` | 278-283 |
| `PUT .../{trnsSeqNo}/confirm` | `confirm(...)` | 293-299 |
| `DELETE .../{trnsSeqNo}/confirm` | `undoConfirm(...)` | 303-308 |
| `PATCH/DELETE .../{trnsSeqNo}/reporting-account` | `updateReportingAccount/clear` | 327-355 |
| `PATCH/DELETE .../{trnsSeqNo}/purpose` | `updatePurpose/clearPurpose` | 369-391 |
| `PATCH/DELETE .../{trnsSeqNo}/vas-account` | `updateVasAccount/clear` | 402-422 |
| `PATCH/DELETE .../{trnsSeqNo}/contact` | `updateContact/clear` | 434-454 |
| `GET .../{trnsSeqNo}/match-candidates` | `matchService.listCandidates(...)` | 463-472 |
| `POST .../{trnsSeqNo}/match` | `matchService.create(...)` | 481-488 |
| `DELETE .../{trnsSeqNo}/match` | `matchService.undo(...)` | 495-501 |
| `POST /accounts/transactions/export` | `excelExportService.exportTo(...)` | 510-516 |

Every handler carries `@PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")`.

**4.2 List query path** — `TransactionService.java:94-138` (load `fetchRows` → in-memory type/keyword/confirm filter → totals → `toRow`). `fetchRows` (`:660-665`):
```java
return (fromDt blank || toDt blank)
   ? txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(bizKey, bankCd, acctNo)
   : txnRepo.findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(...);
```
No `ccyCd` argument anywhere → **no currency filter**.

**4.3 Filters** — `matchesTypeFilter` (`:667-672`, DEPOSIT/WITHDRAW→`InoutDv.IN/OUT.code()`), `matchesCnfmFilter` (`:674-679`), `matchesKeyword` (`:681-691`, RMRK/RMRK2/BankRefNo only).

**4.4 Totals/summary** — `TransactionService.java:144-174` → `TransactionSummaryResponse(totalCount, confirmedCount, unconfirmedCount, depositTotal, withdrawTotal)`.

**4.5 Row mapping (DTO + the two defects)** — `TransactionRow.java` fields: `ACCT_SEQ_NO, TRSC_DT, TRSC_TM, INOUT_DV, DEPOSIT_AMT, WITHDRAW_AMT, BAL, RMRK, CONF_YN, CONFIRMED_BY, CONFIRMED_DTM, LINK_MATCH_YN, CHART_ACCT_CD, CHART_ACCT_SEQ_NO, USE_USAG_CD, VAS_ACCT_CD, RCV_PAY_TRCO_KEY, RCV_PAY_TRCO_NM`. `toRow` (`TransactionService.java:750-775`) passes `null, null` for `CONFIRMED_BY/CONFIRMED_DTM` (lines 765-766). **No** `BANK_TRNS_SHORT_ID`, `PAY_ORG_FILE_NM`, `MATCHED_AT/BY` fields exist on the row.

**4.6 Detail** — `TransactionService.java:176-215`: `TransactionDetailResponse` **does** return `ccyCd`, both `rmrk`/`rmrk2` (unmerged), and `cnfmByUserId/Nm/Dtm`. So confirmed-by data is reachable per-row via detail, just not in the list payload.

**4.7 Write paths (MATCH)** — confirm/undo `:223-295` (writes `ACCT_TRNS_RCV_PAY_INFM.CNFM_*`); purpose `:394-455`; VAS `:465-524`; contact `:535-597`; reporting account `:314-379` (writes `ACCT_TRNS_HSTR.CHART_ACCT_*`). `RCV_PAY_SEQ_NO` allocated Java-side MAX+1 (documented MODERNIZED key-gen).

**4.8 Match (PRESENT)** — `MatchService.java` (`listCandidates/create/undo`); create "writes `BUSI_MATCH_LOG` + flips `LINK_MATCH_YN='Y'` on `ACCT_TRNS_HSTR` and `SCDUL_PYMN_SUMR_DETL`" (Javadoc `AccountController.java:476-479`, mirrors `comm_0196_001_c001`).

**4.9 Smart Organizer + auto-classification (PRESENT)** — `SmartOrganizerController.java` (`GET /smart-organizer/eligible`, `POST /smart-organizer/confirm`); `RmrkOrgInfm` entity + repository + `SmartOrganizerService`/`OrganizerRuleService` (V172–V174). Has its own sort whitelist (V162 seed).

**4.10 Excel export** — `TransactionHistoryExportProvider` allow-list `{TRSC_ID, TRSC_DTM, DEPOSIT_AMT, WITHDRAW_AMT, BAL, RMRK, FILE_NM, MTCH_DT, MTCH_BY, PURPOSE, CONTACT_NM, CONFIRMED_BY}` (several annotated `// pending DTO extension`); reuses `transactionService.list(...)`, filters applied, all rows (cap 50k). Server-side POI via shared `ExcelExportService` (`AccountController.java:510-516`).

**4.11 No bank-specific rules** — grep for `11704004`/`11704020`/`RMRK_ORG_INFM`/`AUT_CLS` in the transaction path: balance-recompute and remark-merge **absent** from `TransactionService`; `RmrkOrgInfm` only in `smartorganizer`.

**4.12 Frontend** — FCY route `app/(dashboard)/banking/transaction-history-fcy/[accountId]/page.tsx` → `<TransactionHistoryClient acctSeqNo={accountId} scope="FCY" />` (VND/FCY share the component, differ by `scope`). Default date range = last 30 days (`TransactionHistoryClient` `defaultDateRange`), ISO→`YYYYMMDD` at fetch. Status tabs map `Confirmed→"Y"`, `Unconfirmed→"N"`, `All→undefined`; counts from `summary`. Pagination client-side (`usePagination`, sizes 10/20/50/100), footer `"{shown} of {total}"`, empty-state `txn_history.empty_fcy` = "Transaction History (FCY) Not Found". Export via `POST /api/v1/banking/accounts/transactions/export`. API client functions in `lib/api/banking.ts` cover all 14 endpoints. i18n: 66 `txn_history.*` keys in each of en/ko/vi.

---

## 5. Feature-by-Feature Verdict Table

| # | Area | Legacy | Refactor | Verdict | Severity | Fix req. |
|---|---|---|---|---|---|---|
| 1 | FCY route / menu / account selection | session-scoped `bankCd/acctNo/ccyCd` | dedicated FCY route + `scope="FCY"` + FCY menu slug | **MATCH** | — | No |
| 2 | Date-range filter | `trsc_dt between` | `TrscDtBetween` finder | **MATCH** | — | No |
| 3 | In/Out filter | `inout_dv` 1/2 | `matchesTypeFilter` DEPOSIT/WITHDRAW | **MATCH** | — | No |
| 4 | Confirm filter | `coalesce(CNFM_YN,'N')` | `matchesCnfmFilter` | **MATCH** | — | No |
| 5 | Currency (`CCY_CD`) filter | `ATH.ccy_cd=?` + acct-type set | none | **MISSING** | Medium | Yes |
| 6 | Keyword scope | 8-leg OR | RMRK/RMRK2/BankRefNo | **MISSING (narrower)** | Medium | Yes |
| 7 | Keyword placeholder vs backend | n/a | placeholder over-promises | **BUG** | Low | Yes |
| 8 | Paging | DB-paged + `_CNT` | load-all + client paginate | **MODERNIZED** | Low | PO |
| 9 | List sorting | sort whitelist + default | fixed `TRSC_DT DESC` | **MISSING** | Low | PO |
| 10 | Totals (in/out + confirmed/unconfirmed) | `_CNT` IDO | `summary` | **MATCH** | — | No |
| 11 | Deposit/Withdraw/Balance display + 2dp | `setScale(2,HALF_UP)` | `AmountFormatter.wire` | **MATCH** | — | No |
| 12 | Confirm / Undo | `u002`/`u003` | `confirm`/`undoConfirm` | **MATCH** | — | No |
| 13 | Confirmed-by name/date in **list** | `CNFM_BY_USER_NM`+`CNFM_DTM` | hard-coded `null` in `toRow` | **BUG** | Medium | Yes |
| 14 | Classify purpose/VAS/contact (+clear) | `c001`/`u005` | update/clear trio | **MATCH** | — | No |
| 15 | Reporting account (+clear) | `u004`/`u006` | update/clear | **MATCH** | — | No |
| 16 | Transaction ID column (`BANK_TRNS_SHORT_ID`) | in `REC` | not on `TransactionRow` | **MISSING** | Medium | Yes |
| 17 | File Name + Matched date/by columns | lateral match join | not on `TransactionRow` | **MISSING** | Medium | Yes |
| 18 | Inline auto-classification (`AUT_CLS_*`) in grid | shown when no manual value | only via Smart Organizer | **MODERNIZED/MISSING** | Low | PO |
| 19 | Payment match / unmatch | `comm_0196_*` | `MatchService` + 3 endpoints | **MATCH** | — | No |
| 20 | Smart Organizer (feature present) | `fnsh_0200_*` | `SmartOrganizerController/Service` | **MATCH** | — | No |
| 20a (**R11**) | Smart Organizer **account scope** | per-account (selected account) | was tenant-wide → now `BIZ_KEY+BANK_CD+ACCT_NO` | **GAP → CLOSED** | **Critical / SIT Blocker** | Done (see §0) |
| 21 | Memo side panel | `comm_0105_001_*` | not ported to this screen | **MISSING** | Low | PO |
| 22 | Excel export | client→hidden-form POST | server-side POI provider | **MODERNIZED** | — | verify cols |
| 23 | Vietcombank `11704004` balance recompute | IDO CASE | absent | **MISSING (deferred)** | Medium | PO |
| 24 | Bank `11704020` remark rule | act.jsp merge | absent | **MISSING (deferred)** | Low | PO |
| 25 | Haengsung layout / saving filters | `_prdtTypes==="H"` table5 | deferred refactor-wide | **PO DECISION** | Low | PO |
| 26 | Amount rounding/format | 2dp HALF_UP | 2dp + strip trailing zeros | **MATCH (note)** | Low | verify |
| 27 | Tenant isolation (`BIZ_KEY`) | `ATH.biz_key=?` | `TenantContext.currentBizKey()` | **MATCH** | — | No |
| 28 | Menu authorization | WSVC `<login>Y`/norm-user | `@PreAuthorize` FCY slug | **MATCH** | — | No |
| 29 | Per-account data scope | `getPermittedBankAccountDynamic` | not implemented | **MISSING** | Medium | Yes |
| 30 | Scraping at query time | upstream/on-demand only | list path reads DB only | **MATCH** | — | No |
| 31 | i18n EN/KO/VI | ML codes | 66 keys × 3 locales | **MATCH** | — | No |

---

## 6. Confirmed Matches

Date / in-out / confirm filters (§4.3); deposit/withdraw + confirmed/unconfirmed totals (§4.4); amount 2-dp formatting; confirm/undo, purpose/VAS/contact classify+clear, reporting-account set+clear (§4.7); **payment match/unmatch** (§4.8); **Smart Organizer** incl. `RMRK_ORG_INFM` auto-classification (§4.9); **server-side Excel export** (§4.10); tenant `BIZ_KEY` scoping; **per-endpoint menu authorization with an FCY-specific permission code** (§4.1); query-time reads DB only (no scraping); EN/KO/VI i18n parity.

---

## 7. Missing Features

1. **Currency (`CCY_CD`) filter** (§4.2) — finding #5.
2. **Confirmed-by name/date in list** (§4.5) — finding #13 (also a bug — data exists).
3. **Transaction ID column data** (`BANK_TRNS_SHORT_ID`) — finding #16.
4. **File Name + Matched date/by column data** — finding #17.
5. **Wider keyword scope** (amount/VAS/contact/transaction-id/memo) — finding #6.
6. **List-column sorting** — finding #9.
7. **Inline auto-classification display in grid** — finding #18 (capability exists in Smart Organizer).
8. **Memo side panel** on this screen — finding #21.
9. **Bank-specific rules** (Vietcombank balance recompute, `11704020` remark) — findings #23/#24.
10. **Per-account data scoping** for non-admins — finding #29.

---

## 8. Bugs

- **BUG-1 (Medium) — "Confirmed by" column always blank in list.** `TransactionService.java:765-766` passes `null` for `CONFIRMED_BY`/`CONFIRMED_DTM`. Fix: populate from the same `ACCT_TRNS_RCV_PAY_INFM` row already loaded for `confirmedSeqs`/classification (the `confirm()` write stamps `CnfmByUserNm`/`CnfmDtm`, and `detail()` already returns them).
- **BUG-2 (Medium) — grid declares unbacked columns.** Frontend fallback columns + export allow-list include `TRSC_ID`, `FILE_NM`, `MTCH_DT`, `MTCH_BY` ("pending DTO extension"), but `TransactionRow` has no such fields → empty cells / empty export columns. Fix: extend `TransactionRow` (and `toRow`) with `BANK_TRNS_SHORT_ID` and the match-join projections, or hide the columns until backed.
- **BUG-3 (Low) — keyword placeholder over-promises.** Placeholder "Search for amount, contacts name, purpose" vs backend matching only RMRK/RMRK2/BankRefNo. Fix: either widen the backend keyword OR (preferred for parity) or change the placeholder to match reality.

---

## 9. Modernized Differences

- **Paging:** load-all + in-memory filter + client-side pagination (`TransactionService.java:94-138`, `usePagination`) vs legacy DB paging + `_CNT`. Acceptable for bounded per-account depth; diverges from the legacy paged contract and loads everything for large accounts.
- **Excel:** server-side Apache POI provider vs legacy hidden-form POST — cleaner; verify column parity (BUG-2).
- **Auto-classification:** relocated from the inline grid to the Smart Organizer feature.
- **Key-gen:** `RCV_PAY_SEQ_NO` via Java MAX+1 (documented refactor rule) vs `wb_get_next_val`.
- **Amount wire:** `AmountFormatter.wire` strips trailing zeros (`5000.00`→`5000`); legacy ships `5000.00`. Display formatting is client-side — verify the FCY 2-dp presentation renders identically.

---

## 10. PO Decisions Required

1. **Currency filter (#5):** require the list to filter by `CCY_CD` (+ FCY account-type set) as legacy does, or accept account-scoping as sufficient?
2. **Keyword breadth (#6) + sorting (#9):** match legacy breadth/sort, or accept reduced behaviour?
3. **Bank-specific rules (#23/#24):** port Vietcombank `11704004` balance recompute and bank `11704020` remark rule, or accept divergence (and on what timeline)?
4. **Inline auto-classification (#18) + Memo (#21):** in scope for the FCY screen, or intentionally consolidated into Smart Organizer / dropped?
5. **Haengsung gates (#25):** schedule with the product-type-gate MR.
6. **Paging (#8):** accept load-all, or introduce DB paging for large accounts?

---

## 11. Security / Authorization Findings

- **MATCH — menu authorization is live and FCY-aware.** Every transaction endpoint is gated by `@PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")` (`AccountController.java`). The Phase-F coverage test fails the build on any un-annotated handler. Direct-URL access by an unauthenticated user → 401 (JWT filter); by a user lacking the menu permission → 403.
- **MATCH — tenant isolation.** `resolve(acctSeqNo)` binds `bizKey` from `TenantContext.currentBizKey()` (signed JWT, never a request param); all repository finders constrain on `BIZ_KEY`.
- **MISSING — per-account data scope (#29).** Legacy adds `getPermittedBankAccountDynamic(...)` so a non-admin only sees *permitted* accounts. The refactor has no equivalent account-entitlement filter on this path: **a user who holds the `banking-fcy-inquire-transactions` menu permission can read transactions for *any* account in their own tenant by typing `/banking/transaction-history-fcy/{bankCd}_{acctNo}`.** This is tenant-safe but not account-scoped. **Action: confirm with PO whether per-account entitlement is required for the refactor; if yes, port the permitted-account filter.**

---

## 12. Database Query Parity

| Concern | Legacy | Refactor | Parity |
|---|---|---|---|
| Tenant scope | `ATH.biz_key=?` | `BIZ_KEY` on every finder | ✅ |
| Bank + account | `BANK_CD`/`ACCT_NO` | `bankCd`/`acctNo` | ✅ |
| Currency | `ATH.ccy_cd=?` + acct-type IN | none | ❌ (#5) |
| Date range | `trsc_dt between` | `TrscDtBetween` | ✅ |
| In/out | `inout_dv=?` | in-memory | ✅ (logic) |
| Confirm | `coalesce(CNFM_YN,'N')` | in-memory over `confirmedSeqs` | ✅ (logic) |
| Keyword | 8-leg OR (SQL) | 3-col in-memory | ❌ (#6) |
| Auto-class join (`RMRK_ORG_INFM`) | inline lateral | only in Smart Organizer | ❌ inline (#18) |
| Match join (`BUSI_MATCH_LOG`) | inline lateral → MATCHED_*/file | not in list query (own `MatchService`) | ❌ list columns (#17) |
| `wb_get_*` name resolution | server SQL | codes + client lookups | MODERNIZED (#22) |
| Ordering | sort whitelist + default tie-break | fixed `TRSC_DT DESC, TRSC_TM DESC` | ❌ (#9) |
| Pagination | DB page + `_CNT` | in-memory + client | MODERNIZED (#8) |
| Totals | `_CNT` IDO | `summary` in Java | ✅ |
| Vietcombank balance recompute | IDO CASE | absent | ❌ (#23) |

**FCY-specific risk:** because the refactor list query omits `CCY_CD`, any account row that mixes currencies would over-return. Legacy never assumes one-currency-per-account; the refactor implicitly does.

---

## 13. API Contract Parity

- **Request:** legacy `<in>` carries `CCY_CD` + `PAGE_NO`/`PAGE_PER_CNT`; refactor query params are `from/to/type/cnfmYn/keyword` only (no `ccyCd`, no page params). In/Out wire differs (`1`/`2` vs `DEPOSIT`/`WITHDRAW`) — handled on the frontend.
- **Response (list `TransactionRow`):** present — `ACCT_SEQ_NO, TRSC_DT, TRSC_TM, INOUT_DV, DEPOSIT_AMT, WITHDRAW_AMT, BAL, RMRK, CONF_YN, CONFIRMED_BY, CONFIRMED_DTM, LINK_MATCH_YN, CHART_ACCT_CD/SEQ_NO, USE_USAG_CD, VAS_ACCT_CD, RCV_PAY_TRCO_KEY/NM`. **Absent vs legacy `REC`:** `BANK_TRNS_SHORT_ID`, `PAY_ORG_FILE_NM`, `MATCHED_AT/MATCHED_BY`, `RCPT_NM`, `AUT_CLS_*`, `RMRK2` (merged-remark), and `CONFIRMED_BY/DTM` are present in the DTO but **always null** in list (BUG-1).
- **Detail (`TransactionDetailResponse`):** richer — returns `ccyCd`, `rmrk`+`rmrk2`, and `cnfmByUserId/Nm/Dtm`. Note the wire ships UPPER_SNAKE `@JsonProperty` on these DTOs (legacy-mirror exception still permitted for unmigrated Transactions screens per wecms-api/CLAUDE.md).
- **Envelope:** standard `{status,data}` (`ApiResponse`).

---

## 14. UI Parity

| UI element | Legacy | Refactor | Note |
|---|---|---|---|
| Title | `Transaction History > {nick} ({ccy})` | `txn_history.breadcrumb` `"Transaction History ({bankName} {ccy})"` | ✅ |
| Date range + calendar | datepicker, default range | last-30-days default | ✅ (default differs from legacy saved-range) |
| All/Deposit/Withdraw | radio | `TransactionTypeFilter` | ✅ |
| All/Confirmed/Unconfirmed + counts | counters | `TransactionStatusTabs` + `summary` counts | ✅ |
| Keyword + placeholder | "Remarks, VAS, Contact, Transaction ID" | "amount, contacts name, purpose" | ⚠ placeholder ≠ backend (BUG-3) |
| Grid columns | dynamic table1–5 | fixed 14-col registry | Transaction ID / File Name / Matched cols unbacked (BUG-2) |
| Confirmed-by | name + date | blank in list (BUG-1) | ❌ |
| Footer totals | `Total ({ccy})` in/out | summary deposit/withdraw | ✅ |
| Pagination | DB-paged 15/20/50/100 | client 10/20/50/100 | ⚠ page sizes differ (10 vs 15 base) |
| Empty state | "No transaction found." | "Transaction History (FCY) Not Found" + "0 of 0" | ✅ acceptable modernization |
| Excel button | hidden-form POST | server endpoint | ✅ |
| Smart Organizer button | bulk screen | dialog + `/smart-organizer` | ✅ |
| Row actions (confirm/undo, reporting/purpose/VAS/contact pickers) | popups | inline handlers | ✅ |

Default page size base differs (refactor 10, legacy 15) — minor; align if exact parity is wanted.

---

## 15. Test Scenarios To Run

1. **Confirmed-by visibility (BUG-1):** confirm a row → list view must show the confirmer name + date in the "Confirmed by" column (currently blank). Detail view should already show it.
2. **Unbacked columns (BUG-2):** with seeded data, verify Transaction ID / File Name / Matched date / Matched by columns are populated in grid **and** export; if not, hide or back them.
3. **Currency isolation (#5):** seed an account (or test data) and confirm only the FCY currency's rows appear; verify VND and FCY routes never cross data.
4. **Keyword parity (#6):** search by amount, VAS code, contact name, and transaction id — confirm whether results return (currently won't); reconcile against placeholder (BUG-3).
5. **Totals:** verify deposit/withdraw totals + confirmed/unconfirmed counts recompute correctly as date/type/keyword/status filters change.
6. **Match/unmatch (#19) + Smart Organizer (#20):** match an allow-listed withdrawal to a payment → `LINK_MATCH_YN='Y'`, `BUSI_MATCH_LOG` row written; undo reverses it.
7. **Per-account authorization (#29):** as a non-admin, directly request `/banking/transaction-history-fcy/{otherAccount}` within the tenant — confirm whether it should be blocked (legacy blocks).
8. **Tenant isolation:** confirm a user from another `BIZ_KEY` gets 403/empty for these endpoints.
9. **Excel:** download with active filters → columns/order match the on-screen grid and the View Setting visibility; amounts 2-dp, FCY formatting.
10. **Empty + paging:** zero-result range shows the FCY empty state; large account paginates client-side without error.
11. **Bank rules (#23/#24):** if a Vietcombank (`11704004`) FCY account is in scope, confirm balance values (currently raw `BAL`); bank `11704020` remark rendering.

---

## 16. Final Go / No-Go Decision

> **UPDATE 2026-06-06 — verdict raised to GO FOR SIT.** All five required fixes (BUG-1/2/3, #5, #29) are CLOSED and verified (compile + type-check green; manual scenarios in [the fix-verification doc](../historical/transaction-history-fcy-fix-verification.md)). The original CONDITIONAL-GO assessment below is retained for history.

**ORIGINAL VERDICT (pre-fix): CONDITIONAL GO (for SIT)**

**Reason:**
- The FCY screen is wired end-to-end — route, FCY-specific menu authorization, list/summary/detail, confirm/undo, full classification trio, reporting account, payment match/unmatch, Smart Organizer, and server-side Excel export are all present and source-verified. The earlier `refactor-gap-analysis.md` significantly **under-stated** completeness.
- Two list-grid defects (Confirmed-by blank; Transaction-ID/File-Name/Matched columns unbacked) and a keyword placeholder mismatch are tester-visible and should be fixed before SIT to avoid false "broken" reports.
- The remaining gaps (currency filter, keyword breadth, sorting, bank-specific rules, per-account scope) are parity deltas that are partly deliberate (deferrals/modernizations) and partly need a PO decision — none blocks a controlled SIT, but #5 and #29 must be resolved before UAT.

**Required fixes before SIT/UAT:**
1. **BUG-1** — populate `CONFIRMED_BY`/`CONFIRMED_DTM` in `TransactionService.toRow` (data already loaded). *(SIT)*
2. **BUG-2** — add `BANK_TRNS_SHORT_ID` + match-join projections (`PAY_ORG_FILE_NM`, `MATCHED_AT/BY`) to `TransactionRow`/`toRow`, or hide those columns + export entries until backed. *(SIT)*
3. **BUG-3** — align the keyword placeholder with actual backend search scope (or widen the backend). *(SIT)*
4. **#5 Currency filter** — add `CCY_CD` (+ account-type set) to the list/summary query path, pending PO confirmation. *(UAT)*
5. **#29 Per-account data scope** — port the permitted-account filter for non-admins, pending PO confirmation. *(UAT)*

**PO decisions:**
1. Currency filter required on the list query? (#5)
2. Keyword breadth + list sorting — match legacy or accept reduced? (#6, #9)
3. Bank-specific rules (Vietcombank balance recompute, `11704020` remark) — port or accept divergence, and when? (#23, #24)
4. Inline auto-classification display + memo side panel — in scope for FCY or consolidated/dropped? (#18, #21)
5. Per-account entitlement (#29) and Haengsung gates (#25) scheduling.
6. Paging strategy — accept load-all or introduce DB paging? (#8)

---

### Evidence caveat

All refactor findings are from the `wecms-api` / `wecms-web` trees as of 2026-06-06. Legacy citations are from the live JEX tree and the prepared `docs/jex/features/transaction-history-fcy/` docs (themselves source-traced). Where a behaviour could not be observed at runtime (e.g. column population requires seeded data), it is flagged in §15 as a test to run rather than asserted as confirmed.
