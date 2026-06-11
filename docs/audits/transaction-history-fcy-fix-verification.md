# Transaction History (FCY) — Fix Verification

> **System:** Refactor (`wecms-api` Spring Boot + `wecms-web` Next.js)
> **Date:** 2026-06-06 · **Scope:** SIT-readiness fixes for the parity audit's CONDITIONAL-GO blockers
> **Parent audit:** [transaction-history-fcy-parity-audit.md](transaction-history-fcy-parity-audit.md)
> **Build status:** backend `./gradlew compileJava` + `compileTestJava` BUILD SUCCESSFUL · frontend `npx tsc --noEmit` exit 0

---

## 1. Fix Summary

Five blockers from the parity audit are resolved. The refactor already shipped the screen full-stack; these fixes close the remaining list-grid defects and the two data-scope blockers.

| ID | Title | Approach |
|---|---|---|
| BUG-1 | "Confirmed by" column blank in the list grid | Populate `CONFIRMED_BY` + `CONFIRMED_DTM` in `toRow` from the confirmed `ACCT_TRNS_RCV_PAY_INFM` row (data was already loaded for the confirmed-state check). Frontend mapper already consumed these fields — no frontend change needed for BUG-1. |
| BUG-2 | Transaction ID / File Name / Matched date+by columns had no backing data | Add 4 fields to the list-row DTO and batch-resolve match metadata (`BUSI_MATCH_LOG` → `USER_INFM` for the name, → `SCDUL_PYMN_SUMR` for the file). Wire them through the frontend mapper + wire type; fill the export columns. |
| BUG-3 | Keyword placeholder over-promised vs backend search | **Option A** — widen the backend keyword to match the placeholder ("amount, contacts name, purpose") plus remarks / ref / transaction-id / VAS. Placeholder is now accurate; no frontend change. |
| #5 | Currency (`CCY_CD`) filter missing | Recover the account currency from `ACCT_MSTR` in `resolve()` and filter transaction rows to it in `fetchRows()` — covers list / summary / detail / export. |
| #29 | Per-account authorization missing | New `AccountAccessGuard` (admin bypass else active `ACCT_AUTH_INFO` View grant) enforced on every per-account path; unauthorized access → HTTP 403 `WBE1003`. |
| **R11** | Smart Organizer operated tenant-wide (PO reversed the earlier "accepted modernization" → **Critical SIT Blocker**) | Account-scope Smart Organizer to the selected account: backend filters `BIZ_KEY + BANK_CD + ACCT_NO` + `AccountAccessGuard`; frontend threads the selected account from Transaction History into the page + API calls. See §R11. |

**Decision recorded for BUG-3:** Option A (expand backend) was chosen over Option B (shrink the placeholder) so the refactor moves toward legacy keyword breadth rather than away from it. The backend now covers amount, contact name, purpose, VAS code, transaction id, remark, remark2, and bank ref no — a superset of the placeholder wording, so the placeholder is accurate without over-promising.

---

## 2. Source Files Modified

### Backend (`wecms-api`)

| File | Change |
|---|---|
| `src/main/java/.../domain/banking/service/AccountAccessGuard.java` | **New** — per-account View-permission guard (#29). Admin bypass; else require active `ACCT_AUTH_INFO AUTH_TYPE='V'`; throws `AccessDeniedException` → 403. |
| `src/main/java/.../domain/banking/dto/TransactionRow.java` | +4 fields: `BANK_TRNS_SHORT_ID`, `PAY_ORG_FILE_NM`, `MATCHED_AT`, `MATCHED_BY` (BUG-2). |
| `src/main/java/.../domain/banking/service/TransactionService.java` | `resolve()` adds account-currency lookup (#5) + `accountAccessGuard.assertCanView` (#29); `fetchRows()` currency filter (#5); `confirmedSeqs`→`confirmedRowsBySeq` map (BUG-1); `matchInfoBySeq()` + `MatchInfo` record + `parsePayTrnsId` (BUG-2); `matchesKeyword` widened with classification context (BUG-3); `toRow()` new signature populating confirmer + match fields; `Resolved` record gains `ccyCd`. New injected repos: `AcctMstrRepository`, `BusiMatchLogRepository`, `ScdulPymnSumrRepository`, `UserInfmRepository`, `AccountAccessGuard`. |
| `src/main/java/.../domain/banking/service/MatchService.java` | Inject `AccountAccessGuard`; `requireBankRow()` calls `assertCanView` (#29 on match-candidates/match/unmatch). |
| `src/main/java/.../domain/banking/repository/BusiMatchLogRepository.java` | +`findByBizKeyAndBankTrnsShortIdInAndLockedYn(...)` batch finder (BUG-2). |
| `src/main/java/.../domain/setting/bankaccount/repository/AcctAuthInfoRepository.java` | +`existsByBizKeyAndUserIdAndBankCdAndAcctNoAndAuthTypeAndUseYn(...)` (#29). |
| `src/main/java/.../domain/banking/export/TransactionHistoryExportProvider.java` | `toFieldMap` fills `TRSC_ID`, `FILE_NM`, `MTCH_DT`, `MTCH_BY`, `PURPOSE`, `CONTACT_NM`, `CONFIRMED_BY` from the now-populated row (BUG-1/BUG-2). |
| `src/main/java/.../domain/banking/smartorganizer/service/SmartOrganizerService.java` | **R11** — `listEligible(bankCd, acctNo, …)` + `confirm` now require + filter on `BIZ_KEY + BANK_CD + ACCT_NO`, call `accountAccessGuard.assertCanView`, and reject confirm rows whose bank row doesn't match the scoped account; `requireAccount` guard. |
| `src/main/java/.../domain/banking/smartorganizer/controller/SmartOrganizerController.java` | **R11** — `/eligible` gains required `bank_cd`/`acct_no` params; both `/eligible` + `/confirm` gain `@PreAuthorize(@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions'))` (also closes a prior menu-authz gap). |
| `src/main/java/.../domain/banking/smartorganizer/dto/ConfirmRequest.java` | **R11** — `+bankCd` + `+acctNo` (both `@NotBlank`) so confirm carries the selected-account scope. |

### Frontend (`wecms-web`)

| File | Change |
|---|---|
| `lib/types/api.ts` | `ApiTransactionRow` +4 wire fields (`BANK_TRNS_SHORT_ID`, `PAY_ORG_FILE_NM`, `MATCHED_AT`, `MATCHED_BY`). |
| `lib/api/mappers.ts` | `mapTransactionRow` maps the 4 fields → `transactionId` / `fileName` / `matchedDate` (formatted) / `matchedBy`. |
| `lib/api/smartOrganizer.ts` | **R11** — `fetchEligibleTransactions` requires `bankCd`/`acctNo` (sent as `bank_cd`/`acct_no`); `ApiSmartOrganizerConfirmRequest` +`bank_cd`/`acct_no`. |
| `components/banking/transaction-history/SmartOrganizerButton.tsx` | **R11** — `+account` prop; link is now `/banking/smart-organizer?account=<bankCd_acctNo>`. |
| `components/banking/transaction-history/TransactionHistoryActions.tsx` | **R11** — `+account` prop threaded to the button. |
| `components/banking/transaction-history/TransactionHistoryPage.tsx` | **R11** — passes `account={account.id}` to the actions toolbar. |
| `components/banking/smart-organizer/SmartOrganizerPage.tsx` | **R11** — reads `?account` (via `window.location`, repo prerender convention), parses `bankCd_acctNo`, passes it to `fetchEligibleTransactions`/`confirmEligibleTransactions`, and **skips the fetch + shows the empty state when no account is present** (never tenant-wide). |

> No other frontend change: the in-memory `TransactionRow` type already declared `transactionId`/`fileName`/`matchedDate`/`matchedBy`/`confirmedBy`, and `columnRegistry.tsx` already rendered them — they were simply never populated. BUG-1's `confirmedBy` path was already wired in the mapper (`api.CONFIRMED_BY` + `api.CONFIRMED_DTM`), so once the backend ships those values the grid shows them automatically.

---

## 3. Before vs After

### BUG-1 — Confirmed by
- **Before:** `toRow` passed `null, null` for `CONFIRMED_BY`/`CONFIRMED_DTM`; the grid's "Confirmed by" column was blank for every row even when confirmed.
- **After:** the confirmed `ACCT_TRNS_RCV_PAY_INFM` row is carried in `confirmedRowsBySeq` and `toRow` emits `cnfm.getCnfmByUserNm()` + `cnfm.getCnfmDtm()`. Grid + export now show the confirmer name and date; matches the detail popup.

### BUG-2 — Transaction ID / File Name / Matched date+by
- **Before:** `TransactionRow` had no `BANK_TRNS_SHORT_ID` / `PAY_ORG_FILE_NM` / `MATCHED_AT` / `MATCHED_BY`; the grid columns and export entries were declared "pending DTO extension" → rendered empty.
- **After:** `BANK_TRNS_SHORT_ID` ships on every row; for matched rows (`LINK_MATCH_YN='Y'`) `matchInfoBySeq` resolves the live `BUSI_MATCH_LOG` (by short id), the matched-by **name** via `USER_INFM`, and the payment **file name** via `SCDUL_PYMN_SUMR` (from the log's `PAY_TRNS_ID`). One batch query each — at most 4 extra queries per page, skipped entirely when no row is matched.

### BUG-3 — Keyword scope
- **Before:** backend matched only `RMRK` / `RMRK2` / `BANK_REF_NO`; placeholder advertised "amount, contacts name, purpose" → misleading.
- **After:** `matchesKeyword(row, classification, keyword)` ORs across remark / remark2 / ref / transaction-id / amount / contact name / purpose code / VAS code. To make contact/purpose searchable, `list()` and `summary()` now compute the rcv-pay classification **before** the keyword filter.

### #5 — Currency filter
- **Before:** the list query scoped by `bizKey + bankCd + acctNo [+ date]` only; FCY/VND could co-mingle if a bank+acct held >1 currency.
- **After:** `resolve()` reads the account's `CCY_CD` from `ACCT_MSTR`; `fetchRows()` keeps only rows in that currency. Single-currency accounts unaffected. When the account master has no row, the filter is skipped (screen still renders). Route-level note in §5 below.

### #29 — Per-account authorization
- **Before:** any user with the `banking-fcy-inquire-transactions` menu permission could read **any** account in their tenant by typing the URL.
- **After:** `AccountAccessGuard.assertCanView` runs on every per-account path. Non-admins must hold an active `ACCT_AUTH_INFO` View grant (`AUTH_TYPE='V'`, `USE_YN='Y'`) for the account; otherwise **403 `WBE1003`**. Admins bypass (legacy `!isAdmin()` parity). Policy is identical to the Account Inquiry list (`authUserId = currentIsAdmin() ? null : currentUserId()`), so the picker and the transaction screen agree.

### R11 — Smart Organizer account scope (final PO decision — reclassified Critical SIT Blocker)

- **PO decision:** the earlier classification of Smart Organizer's tenant-wide behaviour as *MODERNIZATION / Accepted Deviation* is **overruled**. Smart Organizer **must preserve legacy account scope** — operate only on the account selected in Transaction History, filtered by `BIZ_KEY + BANK_CD + ACCT_NO`, and **must not operate tenant-wide**.
- **Before:** `GET /smart-organizer/eligible` queried `WHERE h.biz_key = :bizKey` + date + account-type + not-confirmed + rule-match — i.e. **every account in the tenant**. The frontend reached the page via a context-free `<Link href="/banking/smart-organizer">` and sent no account. `confirm` accepted any `acct_seq_no` in the tenant. No `@PreAuthorize`.
- **After (legacy workflow restored):**
  - **Backend query scope** — `listEligible(bankCd, acctNo, …)` adds `AND h.bank_cd = :bankCd AND h.acct_no = :acctNo` to both the count and page queries; `requireAccount(...)` rejects a missing bank/account with `WBE_VALIDATION` (no silent tenant-wide fallback).
  - **Backend confirm scope** — `confirm` reads the scoped `bankCd`/`acctNo` from the request, authorizes the account, and rejects any row whose loaded `ACCT_TRNS_HSTR` doesn't match (`WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND`).
  - **Authorization** — `AccountAccessGuard.assertCanView(bizKey, bankCd, acctNo)` on both `listEligible` and `confirm`; plus `@PreAuthorize` menu-authz on both endpoints (closes a prior gap where the controller had none).
  - **Frontend account passing** — the Smart Organizer button carries `?account=<bankCd>_<acctNo>`; the page parses it and sends `bank_cd`/`acct_no` on `eligible` + `confirm`; with no account param it shows the empty state and never fetches.

**Remediation plan (PO-required steps — all done):**
1. ✅ **Pass `BANK_CD` + `ACCT_NO` from Transaction History to the Smart Organizer APIs** — `SmartOrganizerButton` → `?account=<bankCd_acctNo>`; `SmartOrganizerPage` → `bank_cd`/`acct_no` on both API calls.
2. ✅ **Backend filtering by `BIZ_KEY + BANK_CD + ACCT_NO`** — added to the `listEligible` count + page queries and enforced on `confirm` (per-row account match).
3. ✅ **Account authorization on the selected account** — `AccountAccessGuard.assertCanView` on `listEligible` + `confirm`, plus `@PreAuthorize` on both endpoints.
4. ✅ **Regression guard that other accounts' transactions are never returned** — the query account filter + the `confirm` per-row account check; SIT scenarios in §6 (R11) verify cross-account rows are absent and rejected. *(No DB-backed automated test runs here — the SIT scenarios are the required runtime regression check.)*

---

## 4. Validation Results

| Check | Result |
|---|---|
| `./gradlew compileJava` | **BUILD SUCCESSFUL** (incl. R11 Smart Organizer changes) |
| `./gradlew compileTestJava` | **BUILD SUCCESSFUL** (no test contracts broken by the signature changes; no test referenced the changed internals or `ConfirmRequest`/`SmartOrganizerService`) |
| `npx tsc --noEmit` (wecms-web) | **exit 0** (no type errors; new wire fields + mapper lines + R11 account threading type-check) |
| R11 prerender convention | Smart Organizer reads `?account` via `window.location` (not `useSearchParams`) — matches the repo's documented prerender-without-Suspense convention (`ApprovalBoxPage`). |
| Signature-change blast radius | `new TransactionRow(...)`, `confirmedSeqs`, `new Resolved(...)` appear only in the two edited service files — confirmed by grep across `src`. |
| Entity getter availability | `getCnfmByUserNm`/`getCnfmDtm` (AcctTrnsRcvPayInfm), `getMatchedAt`/`getMatchedBy`/`getPayTrnsId`/`getBankTrnsShortId` (BusiMatchLog), `getOrgFileNm` (ScdulPymnSumr), `getUserNm` (UserInfm) all pre-exist and are used elsewhere. |

**Not run:** DB-backed `@SpringBootTest` boot + end-to-end runtime checks — those require the local Postgres and are the SIT step (§5 lists the exact scenarios to run there). No runtime result is claimed here; compile + type-check are green.

---

## 5. Remaining PO Decisions

These are unchanged from the audit §10 and are **not** blockers for SIT — they are scope/parity choices for UAT and beyond:

1. **Currency route shape (follow-up to #5).** The fix recovers currency from `ACCT_MSTR` and filters by it, which is correct for the normal single-currency-per-account case. If a single `bankCd_acctNo` legitimately spans multiple currencies, the route (`/transaction-history-fcy/{bankCd}_{acctNo}`) cannot disambiguate them and would show all of that account's currencies. Decision: does any real account share `bankCd+acctNo` across currencies? If yes, the route must carry `ccyCd` (route + frontend change). Recommend: confirm against PROD data before UAT.
2. **List sorting (#9)** — still fixed `TRSC_DT DESC, TRSC_TM DESC`; no header sort whitelist on the transaction list. Port legacy sort or accept?
3. **Bank-specific rules (#23/#24)** — Vietcombank `11704004` balance recompute + bank `11704020` remark rule still absent (deferred product/bank gates). Port and when?
4. **Inline auto-classification (#18) + Memo side panel (#21)** — auto-classification exists in Smart Organizer; inline grid suggestion + per-transaction memo not ported. In scope for FCY or consolidated/dropped?
5. **Paging (#8)** — load-all + client pagination retained. Accept, or introduce DB paging for large accounts?

> **Resolved (was item 6): Smart Organizer scope.** The prior pass listed the Smart Organizer eligible-list tenant-wide scope as an open PO decision. The PO has now decided: **preserve legacy account scope** (`BIZ_KEY + BANK_CD + ACCT_NO`, never tenant-wide). This is implemented and closed as **R11** — see §R11. No longer an open decision.

---

## 6. Test Scenarios To Run at SIT (DB-backed)

1. **BUG-1:** confirm a transaction → the list grid "Confirmed by" column shows the confirmer name + date (not blank); matches the detail popup and the Excel export `CONFIRMED_BY` column.
2. **BUG-2:** with a matched withdrawal, verify the grid shows Transaction ID (always), and for matched rows File Name + Matched date + Matched by (the **name**, not the user id); confirm the same in the Excel export.
3. **BUG-3:** search by an amount substring, a contact name, a purpose code, a VAS code, and a transaction id — each returns the expected rows; the status-tab counts (`/summary`) honour the same keyword.
4. **#5:** open an FCY account whose `bankCd+acctNo` also has rows in another currency (if any exist) → only the account's currency rows appear; totals/counts match. VND and FCY routes never cross data.
5. **#29 (authorized):** as a non-admin **with** a View grant on the account → list/detail/export/match all succeed.
6. **#29 (denied):** as a non-admin **without** a grant, directly request `/api/v1/banking/accounts/{bankCd}_{acctNo}/transactions` (and `/summary`, `/{trnsSeqNo}`, `/export`, `/match-candidates`, POST `/match`, DELETE `/match`) → **403 `WBE1003`** on every path.
7. **#29 (admin):** as a tenant admin → all accounts accessible (bypass).
8. **Tenant isolation (regression):** a user from another `BIZ_KEY` → 403/empty, unchanged.
9. **Performance sanity:** a fully-matched page (every row matched) issues at most 4 extra batch queries for match enrichment (not N per row).
10. **R11 account scope:** open Smart Organizer from account A's Transaction History → the eligible list contains **only** account A's transactions (seed a rule-matched, unconfirmed row on account B and confirm it never appears). Repeat from account B → only B's rows.
11. **R11 confirm scope:** attempt `POST /smart-organizer/confirm` with `bank_cd/acct_no` of account A but a row `acct_seq_no` belonging to account B → rejected (`WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND`); a missing `bank_cd`/`acct_no` → `WBE_VALIDATION`.
12. **R11 authorization:** as a non-admin without a View grant on the selected account, `GET /smart-organizer/eligible` and `POST /smart-organizer/confirm` → **403 `WBE1003`**; admin → allowed.
13. **R11 no-account guard:** navigate to `/banking/smart-organizer` with no `?account` → the page shows the empty state and issues **no** eligible request (never tenant-wide).

---

## 7. SIT Recommendation

All five required fixes are implemented, compile clean on both trees, and the type contracts hold end-to-end. The remaining items are documented PO decisions, none of which block a controlled SIT. Run the §6 scenarios against the local/SIT DB to confirm runtime behaviour, with particular attention to the #29 denied-path 403s and the BUG-2 matched-row columns (which need seeded matched data to observe).

---

## FINAL VERDICT

**GO FOR SIT**

**Open Issues (non-blocking — PO decisions, see §5):**
- Currency route shape if any account spans currencies (follow-up to #5)
- List sorting (#9), bank-specific rules (#23/#24), inline auto-classification (#18) + memo (#21), paging strategy (#8)

**Closed (SIT/UAT blockers):** BUG-1 ✅ · BUG-2 ✅ · BUG-3 ✅ · CCY filter (#5) ✅ · Account authorization (#29) ✅ · **Smart Organizer account scope (R11) ✅ — final PO decision, legacy `BIZ_KEY+BANK_CD+ACCT_NO` scope restored**
