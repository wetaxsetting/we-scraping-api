# Account Inquiry (VND) — Migration Guide (JEX → Spring)

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

JEX service → Spring artifact mapping. Companion (cross-feature): [references/migration/service-mapping.md](../../../references/migration/service-mapping.md).
The refactor is a clean Spring rewrite informed by JEX behaviour — **not** a port (no `JexData`,
no `IDODynamic`, no `_act.jsp` envelope mimicry).

---

## 1. Screen / route

| JEX | Refactor |
|---|---|
| `fnsh_0001_001.act` (`fnsh_0001_001_view.jsp`) | Route `/banking/account-inquiry` → `AccountInquiryPage.tsx` |
| WSVC id `fnsh_0001_001` (menu artifact) | Slug `banking-account-inquiry-vnd` (route + View Setting `WEB_SVC_ID` + log/audit tag) |

Legacy WSVC ids survive only as file:line citations and migration prose — never in Java/REST/URI/i18n.

---

## 2. Service → controller/endpoint

| JEX service | Behaviour | Refactor endpoint | Handler |
|---|---|---|---|
| `fnsh_0001_001_r001` | Read account list + totals | `GET /api/v1/banking/accounts` | `AccountController.listAccounts` → `AccountService.list` |
| `fnsh_0001_001_r002` | Recent tx for one account | `GET .../accounts/{acctSeqNo}/transactions` | `AccountController.listTransactions` |
| `fnsh_0001_001_r003` | All accounts + creds (scrape prep) | (internal) credential resolve inside `BankTransactionSyncService` | — |
| `fnsh_0001_001_c001` | Ingest NXiSAS LOGIN/BALANCE/TRANSACTION results → update `ACCT_MSTR` + `ACCT_TRNS_HSTR` | `POST .../accounts/transactions-sync` (+ challenge resume) | `AccountController.syncTransactions` → `BankTransactionSyncService.start` |
| (toolbar Excel) | Export visible rows | `POST .../accounts/export` | `AccountController.exportAccounts` → `AccountInquiryVndExportProvider` |
| (no JEX twin) | Read past real-time calls | `GET /api/v1/banking/sync-history` | `SyncHistoryController` |

---

## 3. IDO query → repository

| JEX IDO | Refactor query |
|---|---|
| `IDO.ACCT_MSTR_R001` + `FnshHelper.fnsh_0001_001_r001_dynamic0` (BIZ_KEY, CCY_CD, ACCT_TYPE_CD IN, BANK_CD, SRCH_WD, permitted-account dynamic) | `AcctMstrRepository.findFiltered(bizKey, ccyEq, ccyNot, acctTypeCdIn, bankCd, keywordPattern, authUserId, pageable)` (JPQL, `(:p IS NULL OR …)` optional predicates) |
| `IDO.ACCT_MSTR_R002` (count, SUM(BAL), SUM(REAL_AMT)) | `AcctMstrRepository.aggregateByCcy(...)` (GROUP BY CCY_CD, same WHERE) |
| (bank dropdown distinct) | `AcctMstrRepository.findDistinctBankCds(...)` (not narrowed by bankCd/keyword) |
| `IDO.ACCT_MSTR_U002` (LOGIN/BALANCE write-back) | `AcctMstrRepository.applyOwnTransferBalanceRefresh(...)` (conditional COALESCE UPSERT) + `markScrapeFailure(...)` |
| `SortHelper.getSortDynamic("fnsh_0001_001_r001")` (default `prn_seq:asc`) | `AccountSortField` whitelist + `DEFAULT_SORT = Sort.by(asc("prnSeq"), asc("bankCd"), asc("acctNo"))` |
| `QueryHelper.getPermittedBankAccountDynamic` | `ACCT_AUTH_INFO` `EXISTS` predicate (`AUTH_TYPE='V'`, `USE_YN='Y'`) for non-admins |
| `wb_format_acct_nick_nm` / `wb_get_bank_nm` (PL/pgSQL) | Same functions ported verbatim (V44); `AcctMstr.formattedAcctNickNm` `@Formula`; keyword `LIKE` matches the formatted value |

---

## 4. Behavioural translation notes

- **Envelope:** JEX `COMMON_HEAD`/`REC`/`REC_TOT` → `ApiResponse<AccountListResponse>` (`accounts` + scalar
  totals + `totals_by_ccy`). No legacy envelope mimicry.
- **Naming:** UPPER_SNAKE wire keys → lowercase snake_case (global Jackson strategy). Java DTO fields camelCase.
- **ML codes → error codes:** JEX `P*`/`W*` ML codes → `WBExxxx`/`WBIxxxx` in `messages.properties` (see
  [interface-specification.md §5](interface-specification.md#5-error-codes)). Legacy codes are not reused verbatim.
- **Sync ingest:** JEX parsed `JSON_STR` (a `List<List<IsasThread>>` posted from the browser after the NXiSAS
  applet ran). The refactor orchestrates LOGIN/BALANCE/TRANSACTION server-side via `BankTransactionSyncService`
  and the Coocon smart-gateway, returning a state machine (`COMPLETED`/`*_REQUIRED`/`ERROR`) instead of a
  fire-and-forget POST. Challenge handling (OTP/CAPTCHA/QnA) is explicit refactor surface JEX did inside the applet.
- **Last-update source:** JEX bound the grid cell to `ACCT_BAL_LST_INQ_DTM`; the refactor ships that value on
  the wire field `acct_bal_upd_dtm` and sorts `LAST_UPDATE` by `acctBalLstInqDtm` (HR-1) to keep cell + sort aligned.
- **Scope guard:** JEX touched several tables not in the PROD active list; the refactor implements only
  active-list tables (`ACCT_MSTR`, `ACCT_AUTH_INFO`, `EXT_TRAN_CALL_HIS`, `BANK_CERT_INFO`, `BANK_INFM`,
  `BANK_INFM_DETL`, `ACCT_TRNS_HSTR`).

---

## 5. Out-of-scope / deferred (PO-tracked)

- FCY *frontend* client server-paging migration (backend already supports it via the shared service).
- Print endpoint (frontend ships browser-print helper).
- A handful of legacy `REC` fields (`NXISAS_ACCT_TYPE`, `CERT_INFO_*`, `BANK_NM2`, `ACCT_CUST_TYPE`,
  `VAS_ACCT_*`, `HAND_REG_YN` filter, date-range filter) held pending PO triage — see
  [refactor-gap-analysis.md](refactor-gap-analysis.md).
