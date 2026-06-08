# Smart Organizer R11 — Final Verification Audit

> **System:** Refactor (`wecms-api` Spring Boot + `wecms-web` Next.js)
> **Date:** 2026-06-06 · **Type:** Independent re-verification from current source (prior reports NOT trusted)
> **PO decision under test:** Smart Organizer must operate **only** on the account selected in Transaction History — scope `BIZ_KEY + BANK_CD + ACCT_NO`, never tenant-wide.
> **Builds:** `./gradlew compileJava` ✅ · `./gradlew compileTestJava` ✅ · `npx tsc --noEmit` ✅ (exit 0). No application code modified during this audit.

---

## 1. Scope

Independent verification that the R11 remediation matches the final PO decision and legacy account scope. Every file below was re-read from current source (two files — `SmartOrganizerService.java`, `smartOrganizer.ts` — had been edited since the prior report; both re-read here). Conclusions cite `File / Line / Code / Reason`. Where a runtime behaviour cannot be observed without a live DB, it is flagged as a **SIT runtime check**, not asserted as proven.

Files inspected:
- Backend: `SmartOrganizerController`, `SmartOrganizerService`, `ConfirmRequest`, `AccountAccessGuard`, `AcctAuthInfoRepository`.
- Frontend: `SmartOrganizerButton`, `TransactionHistoryActions`, `TransactionHistoryPage`, `lib/api/smartOrganizer.ts`, `SmartOrganizerPage`.

---

## 2. Source Evidence (anchors)

| Concern | File | Line(s) |
|---|---|---|
| Eligible query account filter | `domain/banking/smartorganizer/service/SmartOrganizerService.java` | 129-132 |
| `requireAccount` (no tenant-wide fallback) | same | 114, 336, 436-440 |
| `assertCanView` on eligible + confirm | same | 119, 341 |
| Confirm per-row account match | same | 357-360 |
| Param binding (eligible) | same | 177-182, 211-216 |
| Controller required params + `@PreAuthorize` | `…/smartorganizer/controller/SmartOrganizerController.java` | 41-55, 59-62 |
| `ConfirmRequest.bankCd/acctNo @NotBlank` | `…/smartorganizer/dto/ConfirmRequest.java` | 36-39 |
| Guard impl (admin bypass / 403) | `domain/banking/service/AccountAccessGuard.java` | 47-59 |
| Auth existence query | `domain/setting/bankaccount/repository/AcctAuthInfoRepository.java` | 98 |
| FE button link carries account | `components/banking/transaction-history/SmartOrganizerButton.tsx` | 34 |
| FE actions threads account | `components/banking/transaction-history/TransactionHistoryActions.tsx` | 19, 34, 77 |
| FE page passes `account.id` | `components/banking/transaction-history/TransactionHistoryPage.tsx` | 351-355 |
| FE API client sends `bank_cd`/`acct_no` | `lib/api/smartOrganizer.ts` | 139-156, 89-93 |
| FE page parse + no-account guard + confirm scope | `components/banking/smart-organizer/SmartOrganizerPage.tsx` | 54-64, 92-103, 157, 171-172 |

---

## 3. Backend Verification

**B1 — `bank_cd` required.** ✅ PASS
- File: `SmartOrganizerController.java` · Line: 44 · Code: `@RequestParam("bank_cd")  String bankCd,` (no `required=false`, no default).
- File: `SmartOrganizerService.java` · Line: 114 → 436-440 · Code: `requireAccount(bankCd, acctNo);` → `if (bankCd == null || bankCd.isBlank() …) throw new BusinessException("WBE_VALIDATION");`
- Reason: missing `bank_cd` fails at the controller (Spring `MissingServletRequestParameterException` → `WBE_VALIDATION`) AND defensively in the service. Two layers; no path reaches the query without it.

**B2 — `acct_no` required.** ✅ PASS
- File: `SmartOrganizerController.java` · Line: 45 · Code: `@RequestParam("acct_no")  String acctNo,`
- File: `SmartOrganizerService.java` · Line: 437-439 · Code: `acctNo == null || acctNo.isBlank() … throw … "WBE_VALIDATION"`.
- Reason: same dual enforcement as B1.

**B3 — missing values rejected.** ✅ PASS
- File: `SmartOrganizerService.java` · Line: 436-440 · Code: `requireAccount(...)` throws `WBE_VALIDATION`. For confirm: Line 336 calls the same guard; `ConfirmRequest.java:38-39` `@NotBlank` rejects blank body fields with `WBE_VALIDATION` before the service runs.
- Reason: blank/missing account is a hard client error on every entry point.

**B4 — no tenant-wide fallback.** ✅ PASS
- File: `SmartOrganizerService.java` · Line: 113-119 · Code: `requireBizKey(); requireAccount(bankCd, acctNo); accountAccessGuard.assertCanView(...)` execute before any query is built; there is no branch that omits the account filter.
- File: `SmartOrganizerService.java` · Line: 129-132 · Code: the `WHERE` always appends `AND h.bank_cd = :bankCd AND h.acct_no = :acctNo`. There is no conditional that drops them.
- Reason: the account predicate is unconditional; the only "tenant-only" string (`h.biz_key = :bizKey`, line 129) is always accompanied by the bank/account predicates.

**B5 — eligible query filters BIZ_KEY + BANK_CD + ACCT_NO.** ✅ PASS
- File: `SmartOrganizerService.java` · Line: 129-132 · Code:
  ```
  where.append(" WHERE h.biz_key = :bizKey ");
  where.append("   AND h.bank_cd = :bankCd ");
  where.append("   AND h.acct_no = :acctNo ");
  ```
- Bound for both count + page: Line 177-179 (`countQ.setParameter("bizKey"/"bankCd"/"acctNo")`) and Line 211-213 (`q.setParameter(...)`).
- Reason: matches the PO-required SQL exactly on the outer `acct_trns_hstr h` query. The lateral `rmrk_org_infm` rule join (Line 166) is itself `r.biz_key = h.biz_key`, so the rule match never crosses tenant.

**B6 — confirm validates selected account + row ownership.** ✅ PASS
- File: `SmartOrganizerService.java` · Line: 333-341 · Code: `String bankCd = req.bankCd(); String acctNo = req.acctNo(); requireAccount(bankCd, acctNo); accountAccessGuard.assertCanView(bizKey, bankCd, acctNo);`
- File: `SmartOrganizerService.java` · Line: 357-360 · Code:
  ```
  if (!bankCd.equals(bank.getBankCd()) || !acctNo.equals(bank.getAcctNo())) {
      throw new BusinessException("WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND");
  }
  ```
- Reason: each row's `ACCT_TRNS_HSTR` is loaded by `(bizKey, acctSeqNo)` (Line 351-355) and then rejected unless its bank/account equals the scoped account — a request cannot confirm a transaction from another account.

**B7 — account authorization enforced.** ✅ PASS
- File: `SmartOrganizerService.java` · Line: 119 (eligible), 341 (confirm) · Code: `accountAccessGuard.assertCanView(bizKey, bankCd, acctNo);`
- File: `AccountAccessGuard.java` · Line: 47-59 · Code: admin bypass (`TenantContext.currentIsAdmin()`), else `authRepo.existsByBizKeyAndUserIdAndBankCdAndAcctNoAndAuthTypeAndUseYn(bizKey, userId, bankCd, acctNo, "V", "Y")`; `!permitted → throw new AccessDeniedException(...)`.
- File: `AcctAuthInfoRepository.java` · Line: 98 · Code: `boolean existsByBizKeyAndUserIdAndBankCdAndAcctNoAndAuthTypeAndUseYn(...)`.
- Reason: non-admin must hold an active `ACCT_AUTH_INFO` View grant for the account; `AccessDeniedException` → HTTP 403 `WBE1003` (per `GlobalExceptionHandler` mapping). Same policy as the Account Inquiry list (audit #29 parity).

**B8 — `@PreAuthorize` exists.** ✅ PASS
- File: `SmartOrganizerController.java` · Line: 42 (eligible) & 60 (confirm) · Code: `@PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")`.
- Reason: both endpoints are menu-authz gated (this also closed a prior gap where the controller had no annotation). Menu gate (reach the screen) + `AccountAccessGuard` (which account) are layered.

**B9 — no alternate endpoint bypasses scope.** ✅ PASS
- File: grep `listEligible` / `service.confirm(` across `src/main/java` · Only callers: `SmartOrganizerController.java:54` and `:62`.
- File: `SmartOrganizerService.java` — only two public query/write methods exist (`listEligible` Line 107, `confirm` Line 331); both are scoped. The sibling rule-CRUD controller (`/smart-organizer/rules`) operates on `RMRK_ORG_INFM` rules, not on transactions, so it cannot return tenant-wide bank rows.
- Reason: there is no second path that lists or confirms eligible transactions without the account scope.

---

## 4. Frontend Verification

**F1 — `BANK_CD` passed / F2 — `ACCT_NO` passed.** ✅ PASS
- File: `lib/api/smartOrganizer.ts` · Line: 139-156 · Code: `fetchEligibleTransactions({ bankCd, acctNo, … })` → `params: { bank_cd: params.bankCd, acct_no: params.acctNo, … }`. `bankCd`/`acctNo` are non-optional in the param type (Line 140-141).
- File: `SmartOrganizerPage.tsx` · Line: 101-103 · Code: `fetchEligibleTransactions({ bankCd: account.bankCd, acctNo: account.acctNo, … })`.
- Reason: both values are sent on the wire as `bank_cd`/`acct_no`.

**F3 — `account=<bankCd>_<acctNo>` propagated correctly.** ✅ PASS
- File: `TransactionHistoryPage.tsx` · Line: 351-355 · Code: `<TransactionHistoryActions … account={account.id} />` (`account.id` is the `acctSeqNo` surrogate = `bankCd_acctNo`).
- File: `TransactionHistoryActions.tsx` · Line: 34, 77 · Code: `account` prop → `<SmartOrganizerButton account={account} />`.
- File: `SmartOrganizerButton.tsx` · Line: 34 · Code: `href={`/banking/smart-organizer?account=${encodeURIComponent(account)}`}`.
- File: `SmartOrganizerPage.tsx` · Line: 54-64 · Code: reads `?account` via `window.location`, splits on the first `_` → `{ bankCd, acctNo }`.
- Reason: the selected account flows end-to-end from the Transaction History row context into the Smart Organizer query.

**F4 — Smart Organizer cannot load tenant-wide.** ✅ PASS
- File: `SmartOrganizerPage.tsx` · Line: 92-97 · Code: `if (!account) { setRows([]); setTotalCount(0); setLoading(false); return; }` — the fetch is never issued without an account.
- File: `lib/api/smartOrganizer.ts` · Line: 140-141 · Code: `bankCd`/`acctNo` are required by the type, so a call without them is a compile error.
- Reason: there is no front-end code path that calls `/eligible` without `bank_cd`/`acct_no`; even if there were, the backend (B1-B5) would reject it.

**F5 — missing account param ⇒ no fetch + empty state.** ✅ PASS
- File: `SmartOrganizerPage.tsx` · Line: 59-64 (`account` is `null` when `?account` absent/malformed) → Line 92-97 (early-return, no fetch) → with `rows=[]` the existing empty-state block renders.
- Reason: navigating directly to `/banking/smart-organizer` with no `?account` shows the empty state and issues no request.

**F6 — confirm API includes `bank_cd` + `acct_no`.** ✅ PASS
- File: `lib/api/smartOrganizer.ts` · Line: 89-93 · Code: `ApiSmartOrganizerConfirmRequest { bank_cd: string; acct_no: string; rows: [...] }`.
- File: `SmartOrganizerPage.tsx` · Line: 157, 171-172 · Code: `if (selected.size === 0 || !account) return;` then `confirmEligibleTransactions({ bank_cd: account.bankCd, acct_no: account.acctNo, rows: … })`.
- Reason: confirm carries the scoped account; with no account the save is blocked client-side and would be rejected server-side (B6).

---

## 5. Security Verification

**Case 1 — Account A selected ⇒ only Account A rows.** ✅ PASS (by construction)
- Evidence: B5 (`AND h.bank_cd = :bankCd AND h.acct_no = :acctNo`, bound from the selected account). A rule-matched, unconfirmed row on Account B cannot satisfy the WHERE.
- SIT runtime check: §"SIT scenarios" #1 (seed a matching row on B, confirm it never appears when A is selected).

**Case 2 — Account A selected, confirm a row from Account B ⇒ fails.** ✅ PASS
- Evidence: B6 (`SmartOrganizerService.java:357-360`) throws `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND` when the row's bank/account ≠ scoped account.
- Reason: a crafted confirm payload mixing accounts is rejected per row.

**Case 3 — user lacks account authorization ⇒ 403.** ✅ PASS
- Evidence: B7 — `AccountAccessGuard.assertCanView` throws `AccessDeniedException` for a non-admin without an active `'V'` grant; `GlobalExceptionHandler` maps `AccessDeniedException → 403 WBE1003`. Enforced on both `/eligible` (Line 119) and `/confirm` (Line 341). The `@PreAuthorize` menu gate (B8) is an additional 403 surface.
- SIT runtime check: §"SIT scenarios" #3.

**Case 4 — direct URL `/banking/smart-organizer` without account context ⇒ no tenant-wide data.** ✅ PASS
- Evidence (frontend): F5 — no fetch, empty state. Evidence (backend): B1-B4 — a call without `bank_cd`/`acct_no` is rejected with `WBE_VALIDATION`, never falling back to tenant-wide.
- Reason: both layers refuse to produce a tenant-wide listing.

> **Note on URL tampering:** `?account` is user-controlled, but supplying another account's slug only succeeds if the user is authorized for it (B7) and within their tenant (B5 + `TenantContext.currentBizKey()`). Tampering to an unauthorized account → 403; to an authorized account → already permitted. No privilege escalation.

---

## 6. Legacy Parity Verification

| | Scope |
|---|---|
| **Legacy** (PO-stated; `fnsh_0200_001_r001` / `ACCT_TRNS_HSTR_R028`) | `BIZ_KEY + BANK_CD + ACCT_NO` |
| **Refactor (current)** | `BIZ_KEY + BANK_CD + ACCT_NO` — `SmartOrganizerService.java:129-132` |

**Verdict: MATCH.**
- File: `SmartOrganizerService.java` · Line: 129-132 · Code: `WHERE h.biz_key = :bizKey AND h.bank_cd = :bankCd AND h.acct_no = :acctNo` · Reason: the refactor's outer eligibility query now constrains on the exact 3-column key the PO defined as legacy behaviour. The earlier tenant-wide-only scope is gone.
- Confirm path additionally enforces per-row account ownership (Line 357-360) — at least as strict as legacy.

**Verification limitation (honest):** this audit verifies the refactor against the **PO-stated** legacy scope (`BIZ_KEY+BANK_CD+ACCT_NO`), which the PO declared authoritative. I did **not** independently re-open the legacy `IDO.ACCT_TRNS_HSTR_R028.xml` / `fnsh_0200_001_r001_act.jsp` in this pass to re-derive that scope from JEX source — `UNVERIFIED - SOURCE NOT RE-READ` for the legacy artifact itself. The implementation matches the scope the PO mandated; if independent legacy-source confirmation of the 3-column key is required, that is a small follow-up.

---

## 7. Remaining Risks

1. **Stale Javadoc (doc-only, not functional).** `SmartOrganizerService.java:52-54` ("tenant-scoped on `ATH.BIZ_KEY`") and `SmartOrganizerController.java:36` ("for the current tenant") still describe the pre-R11 behaviour. The code is correct; the comments mislead a future reader. Recommend a doc cleanup (not blocking).
2. **No DB-backed automated test.** Security Cases 1-4 are verified by source reading + successful compile, not by a running test. There is no `SmartOrganizerServiceTest`/controller test exercising the account filter (grep: none). The §"SIT scenarios" are the required runtime regression check. Recommend adding a slice/integration test before this hardens.
3. **`EndpointAuthorizationCoverageTest` not executed here.** I added `@PreAuthorize` to both endpoints; the coverage test only fails on *un-annotated, non-allow-listed* handlers, so annotation is safe — but the full test suite (which boots Spring + needs the local DB) was not run in this audit (only `compileJava`/`compileTestJava`). Recommend running `./gradlew test` in the SIT environment to confirm the coverage test + menu-slug wiring.
4. **Legacy source not re-derived this pass** (see §6 limitation).
5. **Frontend `account` parse assumes `bankCd` has no `_`.** `SmartOrganizerPage.tsx:61` splits on the first `_`; bank codes are numeric (e.g. `11704004`) so this holds for real data, but it is a structural assumption shared with `TransactionService.resolve()` on the backend (consistent on both sides).

---

## 8. Final Verdict

All nine backend checks, all six frontend checks, and all four security cases **PASS** from current source. Both trees compile clean. The refactor scope now equals the PO-mandated legacy scope.

```
R11 Status:
CLOSED

Legacy Parity:
MATCH  (refactor scope = BIZ_KEY + BANK_CD + ACCT_NO, SmartOrganizerService.java:129-132;
        verified against the PO-stated legacy scope — legacy JEX artifact not re-derived this pass, §6)

SIT Recommendation:
GO

Reason:
- Smart Organizer is account-scoped on both endpoints: listEligible filters
  BIZ_KEY + BANK_CD + ACCT_NO (SmartOrganizerService.java:129-132) and confirm
  rejects rows outside the scoped account (:357-360); no tenant-wide fallback
  exists (requireAccount :436-440, unconditional WHERE).
- bank_cd / acct_no are required at the controller (:44-45) and re-checked in the
  service; missing values → WBE_VALIDATION.
- Per-account authorization is enforced via AccountAccessGuard (:119, :341 →
  AccessDeniedException → 403 WBE1003) and both endpoints carry @PreAuthorize (:42, :60).
- Frontend threads the selected account end-to-end and refuses to fetch without it
  (SmartOrganizerPage.tsx:92-97); confirm carries bank_cd/acct_no (:171-172).
- compileJava + compileTestJava + tsc --noEmit all green.

Conditions on the GO (non-blocking, recommended before hardening):
- Run ./gradlew test in SIT (boots Spring + DB) to exercise the endpoint-authorization
  coverage test and the menu-slug wiring.
- Execute the §SIT runtime scenarios (cross-account exclusion, confirm rejection, 403,
  no-account empty state) against seeded data.
- Tidy the two stale "tenant" Javadoc lines; optionally re-derive the legacy 3-column
  scope from IDO.ACCT_TRNS_HSTR_R028.xml for a fully independent parity citation.
```

### SIT scenarios (runtime regression — required)
1. Open Smart Organizer from Account A → eligible list contains only A's rows (seed a rule-matched unconfirmed row on Account B; confirm it never appears). Repeat from B.
2. `POST /smart-organizer/confirm` with A's `bank_cd/acct_no` but a row `acct_seq_no` from B → `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND`. Missing `bank_cd`/`acct_no` → `WBE_VALIDATION`.
3. Non-admin without a View grant on the selected account → `/eligible` and `/confirm` return 403 `WBE1003`; admin → allowed.
4. Navigate to `/banking/smart-organizer` with no `?account` → empty state, no `/eligible` request fired (network tab).
