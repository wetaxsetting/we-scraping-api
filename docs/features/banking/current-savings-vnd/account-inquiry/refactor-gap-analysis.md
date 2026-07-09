# Account Inquiry (VND) — Refactor Gap Analysis (Final Parity Review)

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

Refactor-side parity review (JEX `fnsh_0001_001` ↔ Spring/Next.js). The JEX-side audit lives at
[docs/jex/features/account-inquiry-vnd/final-parity-audit.md](../../../../../../docs/jex/features/account-inquiry-vnd/final-parity-audit.md)
and [refactor-gap-analysis.md](../../../../../../docs/jex/features/account-inquiry-vnd/refactor-gap-analysis.md) — this doc is the wecms-api view and stays consistent with it.

Status legend: **MATCH** = behaviourally equivalent · **PARTIAL** = implemented with a documented,
PO-tracked deviation · **MISSING** = not implemented.

---

## 1. Parity matrix

| Area | Legacy (`fnsh_0001_001`) | Refactor | Status |
|---|---|---|---|
| Search — currency scope | `CCY_CD` + `ACCT_TYPE_CD IN` filter | `AccountService.resolveFilter` (VND → `02,03,05` + `CCY_CD='VND'`; loan `01` excluded) | **MATCH** |
| Search — bank | `BANK_CD` input | `bankCd` exact predicate | **MATCH** |
| Search — keyword | `SRCH_WD` ILIKE formatted nickname | `keyword` `LIKE … ESCAPE '!'` on `wb_format_acct_nick_nm` | **MATCH** |
| Account grid | bank, acct no, type, nickname, balance, available, last update, results | `AccountRow` (all columns + cert/manual flags) | **MATCH** |
| Sorting | `SortHelper` default `prn_seq:asc`; sortable headers | `AccountSortField` whitelist + `DEFAULT_SORT prnSeq,bankCd,acctNo` | **MATCH** |
| Pagination | `PAGE_NO` / `PAGE_PER_CNT` | server-side `page`/`size` (10/20/50/100, max 100) | **MATCH** |
| Update Balance | NXiSAS BALANCE job → `ACCT_MSTR_U002` | sync orchestrator → `applyOwnTransferBalanceRefresh`/`markScrapeFailure` | **MATCH** |
| Account Sync | LOGIN job → `LST_LGN_*` | `BankTransactionSyncService` LOGIN → `LST_LGN_STTS/_MSG/_SUC_DTM` | **MATCH** |
| Transaction Sync | TRANSACTION job → `ACCT_TRNS_HSTR` + `ACCT_HIS_*` | sync TRANSACTION job → `ACCT_TRNS_HSTR` + `ACCT_HIS_*` + `EXT_TRAN_CALL_HIS` | **MATCH** |
| Last Update display | account-type-aware (FD-only → `ACCT_BAL_LST_INQ_DTM`, else `ACCT_HIS_LST_INQ_DT`) + status-branch logic (>72h stale glyph) | wire `acct_bal_upd_dtm` ← `AccountService.resolveLastUpdate(...)` (FCY-A02) + frontend Success/Failed/datetime/stale | **MATCH** |
| Status display | `00000000`/`42110000` success, else error msg | raw `acct_bal_inq_stts` shipped; frontend maps to Success/Failed | **MATCH** |
| Detail view | `fnsh_0001_001_r002` recent tx | `GET .../{acctSeqNo}/transactions*` (Transaction History sibling) | **MATCH** |
| Permissions | admin-vs-user permitted accounts (`QueryHelper`) | `ACCT_AUTH_INFO` `EXISTS` (non-admin) + `@menuAuthz` | **MATCH** |
| Error handling | ML codes + popup | `WBExxxx` + standard envelope + `trace_id` | **MATCH** |
| Totals | `REC_TOT` (count, SUM BAL, SUM REAL_AMT) | `aggregateByCcy` (same WHERE) + scalar totals | **MATCH** |
| View Setting / column config | `comm_0108_001` popover | global View Setting (`banking-account-inquiry-vnd`) | **MATCH** |
| Excel export | toolbar Excel | shared `common/export` (`AccountInquiryVndExportProvider`) | **MATCH** |
| Sync history | (read of `EXT_TRAN_CALL_HIS`) | `GET /api/v1/banking/sync-history` | **MATCH** |
| Print | JEX print path (`printAllTable`) | frontend browser-print helper; no backend endpoint | **PARTIAL** (by design) |
| FCY twin server-paging | n/a | backend supports it; FCY *frontend* client still uncontrolled | **PARTIAL** (FCY page, not VND) |
| Extra legacy `REC` fields | `NXISAS_ACCT_TYPE`, `CERT_INFO_*`, `BANK_NM2`, `ACCT_CUST_TYPE`, `VAS_ACCT_*`, `HAND_REG_YN`/date-range filters | not exposed (held pending PO triage) | **PARTIAL** (deferred) |

---

## 2. Special verification areas

### Last Update logic — verified
The displayed source is resolved account-type-aware by `AccountService.resolveLastUpdate(...)` (FCY-A02,
PO decision 2026-06-06 — supersedes HR-1): online accounts display `ACCT_HIS_LST_INQ_DT`; fixed-deposit-only
banks (`ACCT_TYPE_CD='02'` other than Sacombank `11704004`) display `ACCT_BAL_LST_INQ_DTM`. The four
legacy display branches (Success+Datetime / Failed+message / history-fail message / No-Data) are
reproduced; `42110000` is correctly treated as success. Sort key `LAST_UPDATE` maps to the single
property `acctHisLstInqDt` (FCY-A02), matching legacy's server-side single-column ORDER BY. **MATCH.**

### Success / Failed display — verified
On failure the balance is **not** zeroed — last known `BAL`/`REAL_AMT` is retained and the row is
flagged; `*_LST_INQ_DTM` is not stamped (so a failed refresh doesn't masquerade as a fresh one). **MATCH.**

### Sync status behaviour — verified
The sync state machine (`COMPLETED`/`OTP_REQUIRED`/`CAPTCHA_REQUIRED`/`QNA_REQUIRED`/`ERROR`) covers the
challenge flows JEX handled inside the browser applet. Failure stamping runs in a `REQUIRES_NEW` tx so
the status survives an outer rollback. **MATCH.**

### Account balance refresh logic — verified
`applyOwnTransferBalanceRefresh` uses `COALESCE(:new, existing)` for `BAL`/`REAL_AMT`/`CORC_BF_BAL`/`ACCT_BAL_UPD_DTM`
so a status-only refresh preserves balances, and `CORC_BF_BAL` captures the prior balance exactly as
JEX `c001` did. **MATCH.**

---

## 3. Verdict

```
Refactor Status: 100% Complete   (in-scope Account Inquiry (VND) surface)
```

**Supporting evidence:** every in-scope area in §1 is MATCH; the three PARTIAL rows are (a) Print —
deliberately reframed as a frontend browser-print helper, (b) the FCY *frontend* client's paging mode —
a separate page, backend already supports server paging, and (c) extra legacy `REC` fields explicitly
held pending PO triage. None affects the VND list, update-balance, sync, last-update, status, detail,
View Setting, or export behaviour. SIT/UAT verification completed (see [test-scenarios.md](test-scenarios.md)).

## 4. Open questions (PO confirmation)

These remain documented open items from the original spec ([current-savings-vnd/account-inquiry.md](../account-inquiry/legacy-spec.md) TODO list) — none block the feature:

- `HAND_REG_YN` / `ACCT_CUST_TYPE` / date-range filters — confirm whether the VND screen needs them.
- `VAS_ACCOUNT` column source field (`VAS_ACCT_NM`) — surface only if business confirms it must be visible (currently `USE_YN/DISP_YN='N'`).
- Confirm production `ACCT_BAL_INQ_STTS` success-code set beyond `00000000`/`42110000`.
- Bank dropdown server-side derivation vs. current distinct-from-rows (misses banks with zero VND accounts).
- Print strategy — keep the frontend browser-print helper or port the JEX print path.

> Per the doc skill, these OQ items are **not** closed here — they stay open pending PO confirmation.
