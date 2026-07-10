# Bank Transaction Sync — "Debit Matched" Parity Gap

**System:** Refactor (wecms-api + wecms-web)
**Legacy screen:** `fnsh_0001_001` (Account Inquiry) → nxisas transaction-list scrape popup (`nxisas_tranList`)
**Refactor surface:** Bank Transaction Sync popup — [`components/banking/account-inquiry/TransactionSyncDialog.tsx`](../../../wecms-web/components/banking/account-inquiry/TransactionSyncDialog.tsx)
**Date:** 2026-07-09 (gap) · 2026-07-09 (implemented) · 2026-07-09 (strict-parity correction)
**Status:** ✅ **Implemented + strict legacy parity** (2026-07-09) — see §6 "Implementation record" + §7 "Strict-parity correction" below.

> **Payment-type parity (final):** Internal Transfer ✓ · Vendor ✓ · Expense ✓ · **Payroll = NO auto-match** (legacy R007 excludes it) · **Overseas ✓** (legacy R007 Branch 3, now ported). The first implementation wrongly auto-matched Payroll and omitted Overseas; both were corrected on PO instruction to follow legacy exactly — see §7.

> **Correction to the original gap analysis (§3):** the claim "backend has no
> auto-match service" was **wrong**. `AutoMatchService`
> ([domain/banking/service/AutoMatchService.java](../../src/main/java/com/kosign/wecms/domain/banking/service/AutoMatchService.java))
> already existed, was already wired into `BankTransactionSyncService`, already
> gated on env `00079`, already wrote `BUSI_MATCH_LOG` (`MATCH_TYPE_CD='A'`,
> `LOCKED_YN='Y'`) + `LINK_MATCH_YN` on both sides, and the response already
> carried a **batch-level** `auto_matched_count`. The genuine gap was
> **per-currency surfacing + the frontend row**. That is what this change added.

---

## 1. Summary

Legacy shows a third row in each per-currency block of the Bank Transaction Sync popup:

```
Credit          12 Transaction(s)      2.000.000.000₫   View
Debit           12 Transaction(s)      2.000.000.000₫   View
Debit Matched   0/12 Transaction(s)
```

The refactor renders **only** `Credit` and `Debit`. The **"Debit Matched" row is missing** — in both the UI and the API wire contract.

The row is the visible surface of a legacy **auto-match-on-scrape** feature: after a scrape saves debit transactions, the server tries to auto-link each new debit to a matching payment record and returns how many it matched (`AUTO_MATCHED_CNT`). It is **Debit-only** (there is no "Credit Matched") and is **gated by business-env setting `00079`**.

Fixing this is **not a label add** — it requires the backend auto-match pipeline, `BUSI_MATCH_LOG` writes, `LINK_MATCH_YN` updates on both bank and payment sides, and a new wire field. **Do not implement until the auto-match feature is confirmed in scope by the PO.**

---

## 2. Legacy behavior (source of truth)

### 2.1 Where it renders

| Concern | File | Lines |
|---|---|---|
| Hidden `tr.debit-matched-row` template (label `nxisas_tranList_m018` = "Debit Matched", cell `.debit-matched-cnt`) | `WECMS DEV/WECMS/web/view/jex/wabooks/nxisas/nxisas_tranList_view.jsp` | 144–153 |
| Credit / Debit total rows (`m012` / `m013`) | same JSP | 132–143 |
| Population logic | `WECMS_STATIC/web/js/jex/wabooks/nxisas/nxisas_tranList_osmc.js` | 528–534 |

```js
// nxisas_tranList_osmc.js:528
if (_debitCount > 0 && _nxisas_tranList._autoMatchEnv !== "N") {
    var _autoMatchedCnt = (!jexjs.empty(rData['AUTO_MATCHED_CNT']))
        ? parseInt(rData['AUTO_MATCHED_CNT']) : 0;
    $(...).find("... tr.debit-matched-row").show();
    $(...).find("... tr.debit-matched-row > td.debit-matched-cnt").html(
        _nxisas_tranList.ML.get("nxisas_tranList_j018",
            "#{MATCHED_CNT}/#{TOTAL_CNT} Transaction(s)",
            {"MATCHED_CNT": _autoMatchedCnt, "TOTAL_CNT": _debitCount}));
}
```

### 2.2 Display condition

- Shown **only after a scrape/sync completes** (filled in the AJAX success callback of `fnsh_0001_001_c001`).
- Shown **only when `_debitCount > 0`** (there were debit transactions this run).
- Shown **only when auto-match env `00079` is on** — client reads `_v2common.app.getEnvSetInfmRealTime("00079")` (`_autoMatchEnv !== "N"`); server independently re-checks `BUSI_ENV_SET_INFM` setting `00079 = 'Y'`.
- **Debit-only.** No "Credit Matched" row exists in the markup or the server logic.

### 2.3 The `{matched}/{total}` count

- **Denominator** (`12`) = `_debitCount`, the client-side **total** debit transactions for that currency/account block this run.
- **Numerator** (`0`) = `AUTO_MATCHED_CNT`, a **runtime counter** of debits auto-matched *in this run* (not a stored column).
- `0/12` is **normal, not an error** — it means none of the 12 debits met the strict "exactly one unique payment candidate" rule (debits already matched in a prior run are excluded from the candidate pool).

### 2.4 Data source & calculation

- **WSVC:** `fnsh_0001_001_c001` (the scrape-**save** action) → `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0001_001_c001_act.jsp` (auto-match block ~1852–2083). Returns `result.put("AUTO_MATCHED_CNT", …)` at ~2083.
- **Enable gate:** `BUSI_ENV_SET_INFM` setting `00079` (`ENV_SET_CONT = 'Y'`) via `BusiEnvSetInfmDAO` (~1874–1885).
- **Candidate query (IDO):** `OVRS_PYMN_SUMR_DETL_R007` — `WECMS_ETC/xml/service/IDO/IDO.OVRS_PYMN_SUMR_DETL_R007.xml`. UNIONs scheduled payments (`SCDUL_PYMN_SUMR_DETL D` + `SCDUL_PYMN_SUMR S`, branch `'S'`) and overseas payments (`OVRS_PYMN_SUMR_DETL D` + `OVRS_PYMN_SUMR S`, branch `'O'`), filtered by:
  - `S.WDRL_ACT_NO = :BANK_ACCT_NO` (same withdrawal account)
  - `COALESCE(D.LINK_MATCH_YN,'N') = 'N'` (not yet matched)
  - transfer succeeded (`S.TRNF_YN='Y'` + `TB_PAY_FILE_DATA.RS_CD='0000'` or `TB_PAY_TARGET.PROC_STAT='0'`)
  - exact amount, exact date, transfer time < banking time
  - `NOT EXISTS` a locked `BUSI_MATCH_LOG` row (`LOCKED_YN='Y'`)
- **Java post-filter (~1924–1937):** banking remark must *contain* the payment remark (`TRAN_USAG` for S-type, `MEMO` for O-type).
- **Match rule (~1946):** exactly **1** surviving candidate → match; `0` or `≥2` → skip.
- **On a match (~1966–2068):** upsert `BUSI_MATCH_LOG` (`MATCH_TYPE_CD='AUTO'`, `LOCKED_YN='Y'`), set bank-side `ACCT_TRNS_HSTR.LINK_MATCH_YN='Y'`, set payment-side `SCDUL_PYMN_SUMR_DETL` / `OVRS_PYMN_SUMR_DETL.LINK_MATCH_YN='Y'`, then `autoMatchedCount++`.

**Persistence surface:**

| Table.Column | Role |
|---|---|
| `ACCT_TRNS_HSTR.LINK_MATCH_YN` | Bank side — `'Y'` when matched |
| `SCDUL_PYMN_SUMR_DETL.LINK_MATCH_YN` | Domestic payment side |
| `OVRS_PYMN_SUMR_DETL.LINK_MATCH_YN` | Overseas payment side |
| `BUSI_MATCH_LOG` (`MATCH_TYPE_CD='AUTO'`, `LOCKED_YN='Y'`) | Audit log of the match |
| `BUSI_ENV_SET_INFM` (`00079`) | Auto-match enable flag |

### 2.5 Legacy caveats worth carrying forward

- Labels `nxisas_tranList_m018` ("Debit Matched") / `m016` ("Balance") are **not** in the sync ML dictionary `WECMS_SYNC/.../nxisas_tranList_view.json` — only `m012`/`m013` (Credit/Debit). `j018` has a hardcoded English fallback `"#{MATCHED_CNT}/#{TOTAL_CNT} Transaction(s)"` in the JS.
- The env flag is checked twice with different providers (client real-time vs server DAO). If they disagree the row can show while the server skips matching (or vice-versa) — the refactor should read a **single** authoritative source.

---

## 3. Refactor current state (the gap)

| Layer | Current | Gap |
|---|---|---|
| UI | [`TransactionSyncDialog.tsx`](../../../wecms-web/components/banking/account-inquiry/TransactionSyncDialog.tsx) per-currency block (~683–758) renders `Credit` + `Debit` `<TotalsRow>` only | No "Debit Matched" row |
| Wire | [`lib/types/api.ts`](../../../wecms-web/lib/types/api.ts) `ApiCurrencySummary` (682–690) / `ApiTransactionsSyncResponse` (629–674) carry `credit/debit_count/total`, `success/failed_count` | No `auto_matched_count` (or `debit_matched_count`) field |
| Backend | Sync is an **R&D stub** — only bumps `LST_LGN_SUC_DTM` + `ACCT_BAL_LST_INQ_DTM`; Credit/Debit totals render `0`. Per the dialog's own header comment, real numbers land only with the real NXISAS pipeline (out of current R&D scope) | No auto-match service, no `BUSI_MATCH_LOG` write, no `LINK_MATCH_YN` update |

**Conclusion:** the "Debit Matched" row cannot be added as a UI-only change — it depends on backend scrape + auto-match data that does not yet exist in the refactor.

---

## 4. Implementation plan (draft — do NOT implement until PO-confirmed)

> Prerequisite: the real scrape/save pipeline must be producing genuine Credit/Debit data first. Auto-match is meaningless on the current stub (which returns `0` counts). Sequence this **after** the real sync pipeline lands, or scope both together.

### 4.1 Backend — API response change (`wecms-api`)

- Extend the per-currency summary DTO (backend counterpart of `ApiCurrencySummary`) with one field: `auto_matched_count` (lowercase snake_case wire, per `project_api_naming_lowercase.md`). Optionally also `debit_total_count` if the denominator should be server-authoritative instead of the client's `_debitCount`.
- Populate it on `COMPLETED` from the auto-match service (§4.2). `null`/omit when auto-match env `00079` is off, so the FE can decide whether to render the row.
- Keep it **Debit-only** — no credit-matched field.
- Paired change: update `wecms-web` `ApiCurrencySummary` + the `handleResponse` mapper in the same MR (API/WEB coordination rule).

### 4.2 Backend — auto-match service / query parity (`wecms-api`)

- Port the `fnsh_0001_001_c001` auto-match block as a service invoked inside the scrape-save transaction (after new debit rows are persisted, before the response is built).
- **Candidate query:** reproduce IDO `OVRS_PYMN_SUMR_DETL_R007` as a repository query (native SQL or JPQL) — same UNION (scheduled `'S'` + overseas `'O'`), same predicates (`WDRL_ACT_NO`, `COALESCE(LINK_MATCH_YN,'N')='N'`, transfer-success, exact amount/date, transfer-time < banking-time, `NOT EXISTS` locked `BUSI_MATCH_LOG`).
- **Post-filter:** banking-remark-contains-payment-remark (`TRAN_USAG` / `MEMO`) — match legacy Java string containment exactly.
- **Match rule:** exactly-1-candidate → match; 0 or ≥2 → skip. Preserve verbatim.
- **On match:** insert `BUSI_MATCH_LOG` (`MATCH_TYPE_CD='AUTO'`, `LOCKED_YN='Y'`), set `ACCT_TRNS_HSTR.LINK_MATCH_YN='Y'` and the payment-side `LINK_MATCH_YN='Y'`; increment the per-currency counter.
- **Enable gate:** read env `00079` from `BUSI_ENV_SET_INFM` (single authoritative source; do not duplicate the client-side check as a second source of truth).
- **Inventory check:** verify `BUSI_MATCH_LOG`, `BUSI_ENV_SET_INFM`, `OVRS_PYMN_SUMR_DETL`, `SCDUL_PYMN_SUMR_DETL`, `ACCT_TRNS_HSTR` against [`legacy-table-inventory.md`](../references/database/legacy-table-inventory.md) before writing any entity/repository (per workspace DB rules). `BUSI_MATCH_LOG` is already used by the Smart Organizer refactor — reuse that entity/DAO if present, don't create a second one.

### 4.3 Frontend — UI display in `TransactionSyncDialog.tsx` (`wecms-web`)

- Add a `debitMatched` field to the `CurrencySummaryView` type and map it from `s.auto_matched_count` in `handleResponse`.
- In the per-currency block, render a **third row after Debit** (a `<TotalsRow>` variant or a lighter dedicated row — the legacy row has no amount and no `View` link, just a `{matched}/{total} Transaction(s)` count). Render it **only when** `completionTally` is set AND the backend indicated auto-match is enabled AND `debitCount > 0` — mirroring the legacy `_debitCount > 0 && _autoMatchEnv !== "N"` gate.
- New i18n keys in all three locale files (`en/ko/vi`), e.g. `account_inquiry.sync.col.debit_matched` ("Debit Matched") and a count string `account_inquiry.sync.debit_matched_count` = `"{matched}/{total} Transaction(s)"`. Use verified KO/VI translations or omit (EN fallback) per the domain-enum locale rule — never invent translations.
- No new amount column; Debit Matched has count only (matches legacy).

### 4.4 Required tests

- **Backend unit:** candidate-query predicate coverage (account match, `LINK_MATCH_YN='N'` only, transfer-success gate, exact amount/date, time ordering, locked-log exclusion); post-filter remark-contains; exactly-1 vs 0 vs ≥2 candidate outcomes; env `00079` off ⇒ no matching + count `null`.
- **Backend integration:** scrape-save with N debits ⇒ correct `auto_matched_count`; verify `BUSI_MATCH_LOG` row (`AUTO`/`LOCKED_YN='Y`), `ACCT_TRNS_HSTR.LINK_MATCH_YN='Y'`, and payment-side `LINK_MATCH_YN='Y'` all set; idempotency (a debit matched last run is excluded this run).
- **Parity:** same fixture through legacy `fnsh_0001_001_c001` vs the refactor service ⇒ identical `AUTO_MATCHED_CNT` and identical rows flipped.
- **Frontend:** row hidden pre-sync and when env off / `debitCount === 0`; shown post-sync with `{matched}/{total}`; per-currency isolation (VND group's count ≠ USD group's); i18n keys resolve in en/ko/vi.

### 4.5 Risks / PO confirmation items

1. **Is auto-match in refactor scope at all?** It writes to production-shaped tables (`BUSI_MATCH_LOG`, `LINK_MATCH_YN` on bank + payment). Confirm with **Mr. Jung** before any implementation.
2. **Env `00079` in the refactor** — confirm the setting exists / is seeded in the refactor DB and its authoritative source (single check, not client+server duplication).
3. **Data-migration safety** — auto-match mutates `LINK_MATCH_YN` on `ACCT_TRNS_HSTR` / `SCDUL_PYMN_SUMR_DETL` / `OVRS_PYMN_SUMR_DETL`; confirm no structural drift vs legacy and that it coexists with live-JEX matching (same tables the Smart Organizer manual match uses).
4. **Overlap with Smart Organizer** — the refactor already has manual bank↔payment matching (`BUSI_MATCH_LOG`, `comm_0196_001`). Confirm the auto-match reuses the same tables/entities and does not create a competing match path.
5. **Denominator definition** — confirm `{total}` should be the per-run debit count (legacy `_debitCount`) vs a server-side figure, so FE/BE agree on the fraction.
6. **Sequencing** — auto-match is only meaningful once the real scrape pipeline produces non-zero Credit/Debit data. Confirm whether to ship it with the scrape pipeline or as a follow-up.

---

## 6. Implementation record (2026-07-09)

**Reused (not duplicated):** `AutoMatchService`, `BusiMatchLog` entity, `BusiMatchLogRepository`, the domestic/payroll candidate queries — all pre-existing. No new match entity, repository, or match algorithm was created.

**Backend changes (per-currency surfacing + enabled flag):**
- `NxisasScrapeSession.CurrencyTally` — added `autoMatchedCount` field + `addAutoMatched(int)` + `autoMatchedCount()`.
- `BankTransactionSyncService.processOneAccount` — folds each account's `autoMatchNewDebits(...)` result into its currency tally.
- `BankTransactionSyncService.finalize` — computes `autoMatchService.isAutoMatchEnabled(bizKey)` and passes it + the per-currency counts to `completed(...)`.
- `AutoMatchService.isAutoMatchEnabled(bizKey)` — new public wrapper over the private `00079` gate check.
- `CurrencySummary` — added `int autoMatchedCount` (wire `auto_matched_count`).
- `TransactionsSyncResponse` — added `Boolean autoMatchEnabled` (wire `auto_match_enabled`, COMPLETED-only).

**Frontend changes:**
- `lib/types/api.ts` — `ApiCurrencySummary.auto_matched_count`, `ApiTransactionsSyncResponse.auto_match_enabled`.
- `TransactionSyncDialog.tsx` — `CurrencySummaryView.debitMatched`, `autoMatchEnabled` state, maps both wire fields, renders **"Debit Matched {matched}/{debit} Transaction(s)"** after the Debit row (no amount, no View link) gated on `completed && autoMatchEnabled && debitCount > 0`.
- `locales/en.json` — `account_inquiry.sync.col.debit_matched` + `account_inquiry.sync.debit_matched_count`.

**Tests:** existing `AutoMatchServiceTest` already covers env-off / 0 / 1 / multiple candidates (+ many edge cases). New `NxisasScrapeSessionAutoMatchTallyTest` covers the per-currency accumulator + `CurrencySummary` projection. Backend `compileJava`/`compileTestJava` + targeted tests pass; frontend `tsc --noEmit` + eslint pass.

**Divergences from the original §4 plan (intentional):**
1. `MATCH_TYPE_CD` is **`'A'`** (existing codebase + manual-matcher convention on the CHAR column), not the `'AUTO'` string the gap doc guessed. Kept `'A'` for parity with the manual matchers.
2. **Overseas (`OVRS_PYMN_SUMR_DETL`) auto-match remains deferred** — `AutoMatchService` is domestic + payroll only (documented pre-existing P1 scope, matching the manual side). Legacy matches overseas too; porting it is a separate scoped item.
3. **Locale: EN-only.** The entire sync popup has zero `account_inquiry.sync.*` keys in `ko.json`/`vi.json` today (EN fallback via `LocaleProvider`); the two new keys follow that established per-feature pattern rather than shipping isolated unverified KO/VI translations. Verified KO/VI for the whole popup is a follow-up.
4. **Enabled signalled by a boolean, not null-coalescing.** A per-tenant `auto_match_enabled` flag drives row visibility (env `00079` is per-tenant, not per-currency), so a `0` count cannot be misread as "disabled".

## 7. Strict-parity correction (2026-07-09)

A parity verification found the first implementation diverged from legacy on two payment types. Corrected to match `OVRS_PYMN_SUMR_DETL_R007` exactly:

**Removed Payroll auto-match.** Legacy R007 Branch 1 filters `SCDUL_PYMN_TYPE_CD <> '00'` and the act.jsp has no payroll code path → legacy does **not** auto-match payroll. Removed the payroll candidate loop and the master-level (`SCDUL_PYMN_SUMR.LINK_MATCH_YN`) write from `AutoMatchService`; deleted the now-orphaned `ScdulPymnSumrRepository.findPayrollAutoMatchCandidates` (replaced with a "do not re-add" note). The auto-match flow **never** writes `SCDUL_PYMN_SUMR` master now.

**Added Overseas auto-match.** Ported legacy R007 Branch 3: new account-scoped `OvrsPymnDtlsRepository.findOverseasAutoMatchCandidates` (mirrors the R006 overseas query + a `WDRL_ACT_NO` filter). Reuses the existing overseas match infrastructure — `DispatchResultResolver.resolveOverseas` (success), `AcctTrnsHstrRepository.markBankRowMatchedOverseas` (bank FK), `OvrsPymnDtlsRepository.markOverseasDetailMatched` (`OVRS_PYMN_SUMR_DETL.LINK_MATCH_YN='Y'`), and the same `BusiMatchLog` insert/re-lock idiom (`MATCH_TYPE_CD='A'`, `LOCKED_YN='Y'`, `PAY_TRNS_ID = OVRS_PYMN_NO + "_" + OVRS_PYMN_DETL_NO`). No new match entity/repository created.

**`AutoMatchService.matchOne`** now unions **domestic (01/02/03) + overseas** candidates and applies the exactly-1 guard across both (a 1-domestic-vs-1-overseas tie is ambiguous → skip). `writeMatch` branches on candidate type (`DOMESTIC` → SCDUL detail, `OVERSEAS` → OVRS detail); both write the **detail** table only, matching legacy `PAY_TYPE_CD` `'S'`/`'O'` at `fnsh_0001_001_c001_act.jsp:2041-2065`.

**No wire/UI change.** `auto_matched_count` / `auto_match_enabled` semantics and the "Debit Matched {matched}/{debit}" display are unchanged — only *which* debits count toward the numerator changed (now excludes payroll, includes overseas), matching legacy.

**Tests:** `AutoMatchServiceTest` — payroll case flipped to `payroll_isNotAutoMatched_legacyParity` (asserts no match, no master write); added `exactlyOneOverseasCandidate_...`, `overseasFailedResult_doesNotMatch`, `oneDomesticAndOneOverseasCandidate_areAmbiguous_soNoMatch`. Domestic / env-off / 0 / multiple / dup / relock cases unchanged and still pass.

## 5. References

- Refactor UI: [`wecms-web/components/banking/account-inquiry/TransactionSyncDialog.tsx`](../../../wecms-web/components/banking/account-inquiry/TransactionSyncDialog.tsx)
- Refactor wire: [`wecms-web/lib/types/api.ts`](../../../wecms-web/lib/types/api.ts) — `ApiCurrencySummary`, `ApiTransactionsSyncResponse`
- Legacy JSP: `WECMS DEV/WECMS/web/view/jex/wabooks/nxisas/nxisas_tranList_view.jsp:144-153`
- Legacy JS: `WECMS_STATIC/web/js/jex/wabooks/nxisas/nxisas_tranList_osmc.js:528-534`
- Legacy action: `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0001_001_c001_act.jsp:1852-2083`
- Legacy candidate IDO: `WECMS_ETC/xml/service/IDO/IDO.OVRS_PYMN_SUMR_DETL_R007.xml`
- Related refactor match feature: Smart Organizer (`comm_0196_001`) — reuses `BUSI_MATCH_LOG` / `LINK_MATCH_YN`
