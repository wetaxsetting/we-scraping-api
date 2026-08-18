# Account Inquiry (FCY) — Functional Specification (Backend)

> The *what*. Scope, filters, columns, totals, sorting, pagination, permissions, export.
> Companion: [business-flow.md](business-flow.md) (the *how*) · [interface-specification.md](interface-specification.md) (the wire).
> Legacy source of truth: `fnsh_0005_001` — see [legacy investigation package](../../../../../../docs/features/account-inquiry-fcy/functional-specification.md).

## 1. Scope

Account Inquiry (FCY) is the foreign-currency sibling of [Account Inquiry (VND)](../account-inquiry/functional-specification.md).
It lists the current tenant's **non-VND** deposit accounts and lets an authorised user refresh them by
scraping the bank.

- **Tenant-scoped** — every query constrains `BIZ_KEY = TenantContext.currentBizKey()`
  (`AccountService.java:176`). Never a wildcard.
- **Active only** — `ACTV_YN = 'Y'`.
- **Currency** — `currency=FCY` resolves to `CCY_CD <> 'VND'`; a concrete `currency=USD|KRW|EUR|…`
  resolves to `CCY_CD = <CCY>` (FCY-A01, approved multi-currency).
- **Account types** — `FCY_TYPE_CODES = {02 DEPOSIT, 04 CURRENT_USD, 06 SAVINGS_USD, 07 CURRENT_KRW,
  08 SAVINGS_KRW}` (`AccountService.java:109-114`). Excludes `01` LOAN and the VND-only `03`/`05`.
  Legacy parity: legacy USD branch filters `('04','06','02')` (`FnshHelper.java:425-430`); the refactor
  adds the KRW types as part of the approved multi-currency modernization.

## 2. Filters

| Filter | Wire param | Backend predicate | Notes |
|---|---|---|---|
| Currency | `currency` | `FCY` → `ccy_cd <> 'VND'` + `FCY_TYPE_CODES`; concrete CCY → `ccy_cd = <CCY>` | `AccountService.resolveFilter` (`:444-474`). Default/blank → `FCY` on this screen. |
| Bank | `bankCd` | `a.bankCd = :bankCd` (legacy `BANK_CD`, e.g. `11704001`) | Exact match. Options come from `findDistinctBankCds` (scoped to tenant + currency, never narrowed by the active bank/keyword). |
| Keyword | `keyword` | `LOWER(wb_format_acct_nick_nm(...)) LIKE :pattern ESCAPE '!'` | Matches the **formatted** nickname (what the user sees), not the raw `ACCT_NICK_NM`. `%`,`_`,`!` escaped (`buildKeywordPattern`, `:489-501`). |

There are **no Search/Reset buttons** — filtering is event-driven (each filter change re-fetches).
The on-screen **date** chip does **not** reach the backend in the refactor (snapshot-only balances — see §8, OQ-FCY-2 PO Approved Deviation; legacy *did* use it for historical balance).

## 3. Account grid columns

Columns are driven by the View Setting registry (`SYS_SCR_OPTION_WECMS` seeded under
`banking-account-inquiry-fcy`). The default set mirrors VND:

| FIELD_ID | Label | Source | Sortable | Notes |
|---|---|---|---|---|
| `CHECKBOX` | (row select) | — | No | `DISP_ORD=0`, special, never hideable. |
| `BANK` | Bank | `bankNm` + `acctTypeNm` + `ccyCd` + `acctNo` | Yes | Composite cell; for FCY the currency label shows e.g. "USD • Current". |
| `ACCOUNT_NICKNAME` | Account Nickname | `acctNickNm` (formatted) | Yes | |
| `BALANCE` | Balance | `bal` | Yes | Right-aligned; click opens the balance popup. |
| `AVAILABLE_BALANCE` | Available Balance | `realAmt` | Yes | Right-aligned. |
| `VAS_ACCOUNT` | VAS Account | — | No | `USE_YN='N'` (hidden); renders `—`. Neither legacy nor refactor display it (FCY-A09, cosmetic). |
| `LAST_UPDATE` | Last Update | `acctBalUpdDtm` (value sourced from `ACCT_HIS_LST_INQ_DT` / FD-fallback) | Yes | See §6. |

## 4. Per-currency totals

The response carries `totals_by_ccy` — one `CurrencyTotal {ccy_cd, count, balance, available_bal}` per
distinct CCY in the filtered set (`AcctMstrRepository.aggregateByCcy`, `:235-272`). The aggregation runs
the **same WHERE chain** as the paginated list, so totals stay stable across page changes. The frontend
renders one summary tile per entry. Scalar `balance` / `available_bal` are cross-currency sums (only
meaningful for a single-CCY narrow; the FCY page reads `totals_by_ccy`).

Legacy parity: per-currency `REC_TOT` (IDO `ACCT_MSTR_R004_CNT`) + grand totals (`ACCT_MSTR_R001_CNT`),
one card per CCY. **MATCH** (refactor computes over the full filtered set).

## 5. Sorting

Server-side ORDER BY via the `AccountSortField` whitelist (shared with VND):

| FIELD_ID | Entity property |
|---|---|
| `BANK` | `bankCd` |
| `ACCOUNT_NICKNAME` | `acctNickNm` |
| `BALANCE` | `bal` |
| `AVAILABLE_BALANCE` | `realAmt` |
| `LAST_UPDATE` | `acctHisLstInqDt` (FCY-A02) |

- Wire: `sortField` (FIELD_ID) + `sortDirection` (`ASC`/`DESC`). Unknown/blank → default ORDER BY (no
  exception, no SQL string interpolation).
- **Default ORDER BY:** `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (`DEFAULT_SORT`, `AccountService.java:124-125`)
  — legacy `fnsh_0001_001` / `SortHelper#86` parity. NULL `PRN_SEQ` sorts last.
- A user-chosen primary sort is appended with the default as a stable secondary key.

## 6. Last Update display (FCY-A02 — CLOSED)

```
FCY-A02
Status: CLOSED
Decision: Implemented (legacy parity)
```

`AccountService.resolveLastUpdate(a)` decides the source column per account:

- **Online accounts** → `ACCT_HIS_LST_INQ_DT` (transaction-history last-inquiry date, stamped by the sync
  orchestrator on every scrape).
- **Fixed-deposit-only banks** (`ACCT_TYPE_CD = '02'` and `BANK_CD != '11704004'` Sacombank) →
  fall back to `ACCT_BAL_LST_INQ_DTM`.

This is byte-for-byte the legacy conditional at `fnsh_0005_001_api_osmc.ts:956-969`. The wire field name
stays `acct_bal_upd_dtm` (frontend grid contract unchanged); only the **value source** flipped on the
backend, and `AccountSortField.LAST_UPDATE` now maps to `acctHisLstInqDt`. Export inherits the corrected
value via the DTO.

**Display rules (rendered on the frontend):**

| Condition | Render |
|---|---|
| Has timestamp, inquiry status success | Humanized relative time ("40m ago", "1d ago") + full datetime tooltip |
| Inquiry status error (`acct_bal_inq_stts` non-success) | Red "Error" tag + humanized time if present |
| No timestamp | Blank cell |

Legacy additionally reddened the cell when the last update was **>72h stale** regardless of status
(`ts:791-834`) — the refactor reds only on inquiry-status error (**FCY-A05**, OPEN, deferred).

## 7. Account status

`acct_bal_inq_stts` carries the balance-inquiry result: `00000000` (success), `42110000` (no new
transactions — treated as success), or an error code with `acct_bal_inq_msg` as the tooltip. Drives the
row error indicator. `cert_conn_yn` (derived from `BANK_CERT_INFO`) flags whether the account's bank has
a live credential — disconnected/manual accounts render with a disabled checkbox + tooltip.

## 8. Date control — historical balance (OQ-FCY-2, PO Approved Deviation)

> **Correction:** an earlier revision called this a "FALSE POSITIVE." That was wrong and is retracted.
> Source re-verification proved **legacy** `fnsh_0005_001` performs real as-of-date historical-balance
> inquiry — FE sends `START_DATE` (`fnsh_0005_001_api_osmc.ts:606-615`),
> `FnshHelper.fnsh_0001_001_r001_readList:246` binds it, and `IDO.ACCT_MSTR_R004.xml:90-217` reconstructs
> `BAL`/`REAL_AMT` as of that date from `ACCT_TRNS_HSTR`, including the Vietcombank `11704004` special case.

The **refactor** intentionally renders the live `ACCT_MSTR.BAL` / `REAL_AMT` snapshot only:
`AccountController.listAccounts` declares no date param (`:94-101`); `AccountFilter` carries only
`currency, bankCd, keyword` (javadoc: "date range — gated on PO confirmation"); `findFiltered` /
`aggregateByCcy` have no date predicate. The date chip is retained because it scopes the per-account
balance/transaction popup (`AccountInquiryPage.tsx:1301`) — it does **not** re-base list balances.

**Decision — OQ-FCY-2 → CLOSED, PO Approved Deviation (Option B, 2026-06-06):** snapshot-only balances
accepted; full legacy parity (Option A — `START_DATE` wiring + `ACCT_TRNS_HSTR` reconstruction +
Vietcombank `11704004`) deferred to a post-SIT MR, gated on realistic seeded transaction history (R&D
seed ≤30 `ACCT_TRNS_HSTR` rows cannot validate as-of-date math). PO sign-off: Mr. Jung (pending formal
confirmation). See [refactor-gap-analysis.md](refactor-gap-analysis.md) and the
[parity audit §3](../../../../audits/legacy-parity/account-inquiry-fcy-parity-audit.md).

## 9. Permissions

| Concern | Rule |
|---|---|
| Login | JWT-protected (everything outside `/auth/*`). |
| Menu / page | `@PreAuthorize("@menuAuthz.canAccessAccountInquiry(#p0)")` — FCY (non-VND currency) resolves to `banking-fcy-update-inquire`; VND/blank → `banking-vnd-account-inquiry` (`MenuAuthorizationGuard.java:141-145`). |
| Per-account data scope | Non-admin restricted via `ACCT_AUTH_INFO` (`use_yn='Y'`, `auth_type='V'`); admin bypass (`AcctMstrRepository.java:197-204`). |
| Update / Export | `/transactions-sync` + `/accounts/export` gated `canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')`. **No frontend role gate** today (FCY-A06, OPEN — backend enforces). |

## 10. Excel export

`POST /api/v1/banking/accounts/export` with `screenId="banking-account-inquiry-fcy"` →
`AccountInquiryFcyExportProvider`:

- `defaultCurrency()` = `"FCY"`; reuses `AccountService.list(...)` (one query path for list + export).
- Allowed columns inherited from the base provider: `BANK, ACCOUNT_NICKNAME, BALANCE,
  AVAILABLE_BALANCE, VAS_ACCOUNT, LAST_UPDATE`.
- **Per-CCY subheader** — `composeSubHeader` emits one line per `totals_by_ccy` entry:
  `<count> account(s)  ·  Balance: <formatted>  ·  Available Balance: <formatted>`, each formatted via
  `AmountFormatter.forDisplay(value, ccyCd)`. A concrete-CCY narrow produces a single line (VND-style).
- Backend is the single XLSX producer (Apache POI SXSSF); amount cells get POI's `#,##0.##` format.

See [interface-specification.md §Export](interface-specification.md) and the workspace export rule in
`wecms-api/CLAUDE.md → "Excel export — common/export/"`.
