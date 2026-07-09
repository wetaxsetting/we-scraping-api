# Account Inquiry (FCY) — Migration Guide (JEX → Spring Boot + Next.js)

> Legacy `fnsh_0005_001` (USD account list) → refactor. The refactor **already exists** and collapses
> VND + FCY onto one backend endpoint + one shared frontend component — mirroring how legacy
> `fnsh_0005_001` reuses the VND helper. Paired with [refactor-gap-analysis.md](refactor-gap-analysis.md).
> Full legacy investigation: [docs/features/account-inquiry-fcy/migration-guide.md](../../../../../../docs/features/account-inquiry-fcy/migration-guide.md).

## 0. Identifier mapping (slug, not WSVC id)

| Concern | Legacy | Refactor slug |
|---|---|---|
| Feature | `fnsh_0005_001` | `account-inquiry-fcy` |
| Next.js route | `fnsh_0005_001.act` | `/banking/account-inquiry-fcy` |
| View-Setting screenId / `WEB_SVC_ID` | `ACCOUNTS_INQUIRY_VND` (legacy `table2`) | `banking-account-inquiry-fcy` |
| Menu permission | (Jex Studio menu) | `banking-fcy-update-inquire` |

## 1. Architecture mapping

| Legacy layer | Refactor layer |
|---|---|
| `fnsh_0005_001_view_osmc.jsp` + `fnsh_0005_001_api_osmc.ts` | `app/(dashboard)/banking/account-inquiry-fcy/page.tsx` → `AccountInquiryFcyClient.tsx` → shared `components/shared/AccountInquiryPage.tsx` |
| `fnsh_0005_001_r001_act.jsp` → `FnshHelper.fnsh_0001_001_r001(…USD)` | `AccountController.listAccounts` → `AccountService.list` → `AcctMstrRepository.findFiltered` |
| IDO `ACCT_MSTR_R004` / `_R004_CNT` / `_R001_CNT` (raw SQL) | JPA entity `AcctMstr` + Spring Data JPQL (`findFiltered` + `aggregateByCcy`) |
| `fnsh_0005_001_r002` history popup → `ACCT_TRNS_HSTR_R010` | `BalancePopover.tsx` → `GET /banking/accounts/{acctSeqNo}/transactions` (`from=to=date`) |
| `nxisas_fnsh_r001` + `fnsh_0001_001_c001` scrape/save | `POST /api/v1/banking/accounts/transactions-sync` → `BankTransactionSyncService` |
| Excel via frontend `TO_EXCEL` | `POST /api/v1/banking/accounts/export` → `AccountInquiryFcyExportProvider` |
| `comm_0140_001_r001` bank dropdown | `banks_available` in the list response + `findDistinctBankCds` |

## 2. Per-feature migration

### 2.1 Account list
- **Legacy:** `ACCT_MSTR` filtered `ccy_cd='USD'`, `acct_type_cd IN ('04','06','02')`, `actv_yn='Y'`,
  `biz_key`, optional bank/keyword(nickname) (`FnshHelper.java:402-489`).
- **Refactor:** `GET /accounts?currency=FCY&…`; `resolveFilter` maps `FCY → ccy_cd <> 'VND'` +
  `FCY_TYPE_CODES {02,04,06,07,08}` (`AccountService.java:109-114, 450-453`).
- **Decision:** legacy USD-only → refactor multi-currency non-VND (gap **FCY-G01 / audit FCY-A01**).
  **CLOSED 2026-06-06 — PO Approved Deviation** (Mr. Jung). Keep multi-currency; no code change.

### 2.2 Totals & dashboard
- **Legacy:** per-currency `REC_TOT` (`ACCT_MSTR_R004_CNT`) + grand totals (`ACCT_MSTR_R001_CNT`).
- **Refactor:** `totals_by_ccy` (per-CCY `CurrencyTotal`) + scalar grand totals via `aggregateByCcy`
  over the full filtered set. One `SummaryTile` per CCY. **MATCH.**
- Open item **FCY-A04**: legacy applies a `PrdtType.H` (Haengsung) maturity/balance exclusion on the
  totals (`FnshHelper.java:505-509`); `aggregateByCcy` has no such clause. OPEN, deferred.

### 2.3 History popup
- **Legacy:** `fnsh_0005_001_r002` → `ACCT_TRNS_HSTR_R010`; floating popup, max 10 rows, "View Details"
  → `fnsh_0006_001`.
- **Refactor:** `BalancePopover` fetches `from=to=selectedDate` + "View Details" deep-link to FCY
  Transaction History. **Acceptable parity** (peek + deep-link), confirm single-date behaviour (FCY-G09).

### 2.4 Update Balance (scraping)
- **Legacy:** client opens `nxisas_tranList.act` → `nxisas_fnsh_r001` (Coocon gateway) →
  `fnsh_0001_001_c001` saves to `ACCT_MSTR`/`ACCT_TRNS_HSTR` + auto-match. NXiSAS engine bridges to
  Coocon. **No `.exe` on the active list path's `r003`** — the engine path is the dead variant.
- **Refactor:** `POST /transactions-sync` → `BankTransactionSyncService.start` (server-side Coocon
  LOGIN→TRANSACTION→BALANCE, SHA-256 dedup, OTP/QNA/CAPTCHA resume). **Functionally present.** Topology
  divergence (client-side engine → server-side RestClient) is **FCY-A03** — by design, not validated
  vs. live banks under R&D (OQ-FCY-3).

### 2.5 Permissions
- **Legacy:** non-admin restricted by `QueryHelper.getPermittedBankAccountDynamic` (EXISTS on the
  permitted-account table); `.scraping` UI hidden for non-admins.
- **Refactor:** list endpoint gated `@menuAuthz.canAccessAccountInquiry` → FCY = `banking-fcy-update-inquire`;
  per-user authority via `ACCT_AUTH_INFO`. **MATCH** on the backend (separate FCY/VND menu authority is
  an improvement). Frontend role gate not reproduced (FCY-A06, OPEN — backend enforces 403).

### 2.6 Excel export
- **Legacy:** 5 columns from the same `r001` data (frontend `TO_EXCEL`).
- **Refactor:** `AccountInquiryFcyExportProvider` (`screenId=banking-account-inquiry-fcy`,
  `defaultCurrency=FCY`, per-CCY subheader). **MATCH** (server-side export is an improvement).

### 2.7 View Setting
- **Legacy:** `dymAreaId='table2'`, screenId `ACCOUNTS_INQUIRY_VND`, columns from Jex Studio.
- **Refactor:** `useViewSetting(SCREEN_IDS.ACCOUNT_INQUIRY_FCY, "default")` with seeds `V43`/`V47`.
  **MATCH** (column-seed contents NOT VERIFIED at row level).

## 3. Field / wire-format mapping

| Legacy field (UPPER_SNAKE) | Refactor (lowercase snake_case) | Note |
|---|---|---|
| `BANK_CD`, `ACCT_NO`, `ACCT_NICK_NM`, `BAL`, `REAL_AMT`, `ACCT_TYPE_CD`, `CCY_CD`, `HAND_REG_YN` | `bank_cd`, `acct_no`, `acct_nick_nm`, `bal`, `real_amt`, `acct_type_cd`, `ccy_cd`, `hand_reg_yn` | global Jackson SNAKE_CASE |
| `ACCT_HIS_LST_INQ_DT` (online) / `ACCT_BAL_LST_INQ_DTM` (FD-only) | `acct_bal_upd_dtm` | value via `resolveLastUpdate` (FCY-A02); wire name retained |
| `CERT_INFO_*` (raw cert payload) | `cert_conn_yn` (boolean) | collapsed to a connected flag (security-positive) |
| `START_DATE` | (not sent) | legacy reconstructs as-of-date balance from `ACCT_TRNS_HSTR` (`ACCT_MSTR_R004.xml:90-217`, incl. VCB `11704004`); refactor ships snapshot-only — **OQ-FCY-2 PO Approved Deviation (Option B)**, full parity (Option A) deferred |
| `ACCT_CUST_TYPE`, `HAND_REG_YN` (as filter) | (absent) | KH-only / low priority (FCY-G03/G04) |

## 4. Migration risks / sequencing

1. **FCY-A01 currency scope** — RESOLVED (PO Approved Deviation). No further action.
2. **FCY-A02 Last Update** — RESOLVED (implemented legacy parity, unit-tested).
3. **OQ-FCY-2 (date control / historical balance)** — RESOLVED 2026-06-06, **Option B (PO Approved
   Deviation)**: refactor ships snapshot-only balances; legacy as-of-date reconstruction (incl. VCB
   `11704004`) is real but deferred to a post-SIT MR (Option A), gated on realistic transaction history.
   Date control retained for the per-account popup only. (Prior "FALSE POSITIVE" framing retracted.)
4. **Parity tests are mandatory** for the per-CCY balance/total math (same rule as cash-flow).
5. **Keyword scope** — legacy matches raw `ACCT_NICK_NM`; refactor matches the formatted nickname
   (`wb_format_acct_nick_nm`) — arguably better screen-parity; fix the misleading controller javadoc that
   claims ACCT_NO/ACCT_NM are searched (FCY-G05).

> See also the cross-feature [references/migration/service-mapping.md](../../../../references/migration/service-mapping.md).
