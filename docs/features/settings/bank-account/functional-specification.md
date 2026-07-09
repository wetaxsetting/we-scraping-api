# Bank Account — Functional Specification (Backend)

The implemented behaviour of the Settings → Bank Account backend. JEX behaviour reference (read-only):
the live `comm_0183_*` module (see [source-code-index.md](source-code-index.md)).

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | List | GET | `/api/v1/settings/bank-account` | `comm_0183_001_r001` (`IDO.ACCT_MSTR_R012`) | No |
| 2 | Update one row | PATCH | `/api/v1/settings/bank-account/{bankCd}/{acctNo}` | `comm_0183_003_u001_act.jsp` | Yes |
| 3 | Bulk register | POST | `/api/v1/settings/bank-account/register` | `comm_0218_003_c001` + `select_bank_accounts_popup` | Yes (per inserted row) |
| 4 | Permission roster | GET | `/api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions?type=` | `comm_0183_002_r001` (`IDO.ACCT_AUTH_INFO_R004`) | No |
| 5 | Permission replace | PUT | `/api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions` | `comm_0183_002_u001` | Yes (1 event, granted/revoked counts) |
| 6 | Audit logs | GET | `/api/v1/settings/bank-account/{bankCd}/{acctNo}/audit-logs` | `comm_0183_004_r001` | No |

**CRUD operations that do NOT exist on this surface** (explicit):
- **No create-from-scratch** — accounts arrive only via `register` (scrape Add-chain). There is no
  `POST /bank-account` for a hand-typed single account.
- **No delete / de-register** — there is no `DELETE /{bankCd}/{acctNo}`. Account de-registration is out
  of scope; the list scopes to `ACTV_YN='Y'` but the screen never flips `ACTV_YN`.
- **No single-row detail GET** — the list row already carries every maintenance field; the frontend
  seeds its edit dialog from the listed row, not a detail fetch.
- **No download / export** — there is no XLSX export endpoint for Bank Account (unlike Account Inquiry).
- **No upload / import** — `register` is a JSON bulk insert from the scrape, not an Excel import.

---

## 2. Field contract

### 2.1 List row (output) — `BankAccountRow`

| Wire field | Type | DB column | Notes |
|---|---|---|---|
| `bank_cd` | string | `BANK_CD` | bank code (PK locator) |
| `bank_name` | string | (resolved) | `BankCdResolver` short name; never null |
| `acct_no` | string | `ACCT_NO` | account number |
| `currency` | string\|null | `CCY_CD` | VND / USD / KRW |
| `nickname` | string\|null | `ACCT_NICK_NM` | |
| `description` | string\|null | `CONT` | |
| `account_type_cd` | string\|null | `ACCT_TYPE_CD` | 02..08 (`AcctTypeCd`) |
| `maturity_date` | string\|null | `EXPI_DT` | ISO `yyyy-MM-dd`; null for current accounts |
| `vas_acct_seq_no` | number\|null | `VAS_ACCT_SEQ_NO` | VAS FK |
| `vas_acct_cd` / `vas_acct_nm` | string\|null | `VAS_ACCT_CD` / `VAS_ACCT_NM` | denormalised VAS code / name |
| `vas_account_bank` | string\|null | (composed) | `"VAS_ACCT_NM(CCY)"` compact label |
| `prn_seq` | number\|null | `PRN_SEQ` | print order (default sort) |
| `bal` / `real_amt` | string\|null | `BAL` / `REAL_AMT` | balance / available |
| `last_modified_by` | string\|null | `MOD_ID` (→ `USER_NM`) | audit actor |
| `last_modified_at` | string\|null | `MOD_DTM` | `yyyyMMddHHmmss` |
| `view_permission_label` | string\|null | derived (`ACCT_AUTH_INFO` 'V') | `"X"` / `"X + N more"`; null = none |
| `withdrawal_permission_label` | string\|null | derived (`ACCT_AUTH_INFO` 'W') | same shape |

### 2.2 Update request (`BankAccountUpdateRequest`)

| Field | Required | Limit | Rule |
|---|---|---|---|
| `nickname` | no | ≤20 | `@Size(20)`; null = unchanged |
| `description` | no | ≤200 | `@Size(200)`; null = unchanged |
| `account_type_cd` | no | ≤2 | `@Size(2)`; `AcctTypeCd.fromCode` must resolve → `WBE_BANK_ACCOUNT_INVALID_TYPE` |
| `maturity_date` | no | ≤10 | `@Size(10)`; ISO → VARCHAR(8); empty = clear; bad length → `WBE_BANK_ACCOUNT_INVALID_DATE` |
| `vas_acct_seq_no` | no | — | null = unchanged; `<=0` = clear; positive = set after FK validation |
| `vas_acct_cd` / `vas_acct_nm` | no | ≤100 / ≤200 | echoes — service overwrites with the authoritative DB values |

**All-null body** → `WBE_VALIDATION`.

### 2.3 Register request (`BankAccountRegisterRequest`)

| Field | Required | Limit |
|---|---|---|
| `bank_cd` | yes | `@NotBlank @Size(20)` |
| `items[]` | yes | `@NotEmpty` |
| `items[].acct_no` | yes | `@NotBlank @Size(100)` |
| `items[].currency` | yes | `@NotBlank @Size(3)` |
| `items[].account_type_cd` | yes | `@NotBlank @Size(2)` (defaults to `02` Current when blank in service) |
| `items[].nickname` | no | `@Size(20)` |
| `items[].description` | no | `@Size(200)` |
| `items[].acct_nm` | no | `@Size(100)` |

Duplicate `(bank_cd, acct_no)` rows are **silently skipped** (legacy re-scrape safety) and counted in
`skipped`.

### 2.4 Permission roster (output) — `BankAccountPermissionResponse`

`{ kind, account_label, members[], total_selected }`. Each member:
`{ user_id, user_nm, user_type, phone_no, is_selected, is_locked, lock_reason }`.

### 2.5 Permission replace request (`BankAccountPermissionUpdateRequest`)

| Field | Required | Rule |
|---|---|---|
| `kind` | yes | `@NotBlank @Pattern(VIEW\|WITHDRAWAL)` |
| `user_ids` | yes | `@NotNull` (empty array = revoke-all for the kind) |

### 2.6 Audit row (output) — `BankAccountAuditRow`

`{ audit_logs_seq_no, audit_logs_actv, audit_logs_actv_dtl, reg_id, reg_nm, reg_dtm }`.

---

## 3. Business rules

### 3.1 List scope

Active accounts only — `findByBizKeyAndActvYn(bizKey, "Y", sort)`. Legacy `comm_0183_001_r001` /
`IDO.ACCT_MSTR_R012` filters `am.actv_yn = 'Y'`; the refactor follows legacy (G11, business decision
2026-06-16). Default ORDER BY is `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC`; `NULL prn_seq` sorts last
(PostgreSQL ASC NULLS LAST).

### 3.2 Sort whitelist (`BankAccountSortField`)

`FIELD_ID` → `AcctMstr` property: `BANK→bankCd`, `ACCOUNT_NICKNAME→acctNickNm`, `DESCRIPTION→cont`,
`ACCOUNT_TYPE→acctTypeCd`, `MATURITY_DATE→expiDt`, `VAS_CODE→vasAcctCd`, `VAS_NAME→vasAcctNm`.
Unknown / blank `sortField` falls back to the default ORDER BY (lenient `parse`, never echoes user input
into SQL). Columns intentionally **not** sortable (their seed carries `SORT_YN='N'`): `CHECKBOX`,
`VIEW_PERMISSION` / `WITHDRAWAL_PERMISSION` (derived aggregates), `AUDIT_LOGS` (derived).

### 3.3 Update

Single `ACCT_MSTR` row patch; every field optional, all-null → `WBE_VALIDATION`. Account-type rejected
unless in the legacy 02..08 set. Maturity ISO ↔ VARCHAR(8); empty clears (saving → current). VAS mapping
FK-validated against `BUSI_VAS_ACCT_INFM` (`DEL_YN='N'` + `STTS='A'`, current tenant) so a stale client
cannot stamp an arbitrary `seqNo`.

### 3.4 Register

Bulk INSERT of scraped accounts (`HAND_REG_YN='N'`, `ACTV_YN='Y'`). PK `(BIZ_KEY, BANK_CD, ACCT_NO)` is
written verbatim — no key generation. Duplicate skip is silent.

### 3.5 Permissions — replace semantics + guards

- Client sends the **full desired user-id set** for one `kind`; service diffs into grant / revoke and
  writes a single audit event.
- **Soft-revoke** (`USE_YN='N'`) keeps audit history; re-granting flips `'N'→'Y'`.
- **Admins are excluded from the roster** (legacy `IDO.ACCT_AUTH_INFO_R004:80` `ADMIN_YN != 'Y'`) — they
  are implicitly authorized and never counted as explicit grantees (mirrored in `countActiveByAccount` /
  `findFirstActiveUserName`'s `NOT EXISTS BUSI_ADMN_INFM` clause).
- **Self-revoke blocked** for a non-admin caller currently granted → `WBE_CANNOT_REVOKE_SELF`.
- **Withdrawal-bank restriction** — a `WITHDRAWAL` grant on a bank outside the 5-bank
  `WithdrawalBankPolicy` whitelist → `WBE0452` (legacy `comm_0183_002_u001` `grpBankCd`; business
  decision 2026-06-16 = keep). A pure revoke-all (empty want set) is still allowed.
- **Tenant validation** — every want-id must be an active `USER_BUSI_CONN_INFM` link → else
  `WBE_USER_NOT_IN_TENANT`.
- **`FULL_ACCESS` ('F')** is in the DB enum / `BankAccountAuthType` but rejected on these endpoints
  (`WBE_VALIDATION`) — the dialog is View / Withdrawal only.
- **No-op saves** (no grant, no revoke) skip the audit write.

### 3.6 Audit-logs popup

Reads `AUDIT_LOGS_INFM` for `(BIZ_KEY, REF_NO IN ('ACCT_MSTR','ACCT_AUTH_INFO'), REF_NO1=bankCd,
REF_NO2=acctNo, WSVC_ID IN ('bank-account','comm_0183_001'), LANG_CD)`, latest-first. Reads both the
slug and the legacy page id for shared-DB coexistence; empty history returns an empty list (never an
error). `LANG_CD` defaults to EN when the context carries none.

---

## 4. Authorization

- Controller class-level: `@PreAuthorize("@menuAuthz.canAccess('settings-core-bank-account')")` — every
  endpoint (read and write) requires the `settings-core-bank-account` menu grant resolved from
  `USER_MENU_AUTH_INFM` (admins bypass the per-user ∩ but not the product scope). F-3 server-side
  menu-authorization model.
- Missing / invalid JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`.

---

## 5. Audit-log expectations

Every Update / Register / permission-change writes a row to `AUDIT_LOGS_INFM` via the shared
`AuditLogService`:

| Column | Update | Register | Permission |
|---|---|---|---|
| `WSVC_ID` | `bank-account` | `bank-account` | `bank-account` |
| `REF_NO` | `ACCT_MSTR` | `ACCT_MSTR` | `ACCT_AUTH_INFO` |
| `REF_NO1` | `bankCd` | `bankCd` | `bankCd` |
| `REF_NO2` | `acctNo` | `acctNo` | `acctNo` |
| activity key | `audit.bank_account.activity` | same | same |
| detail key | `audit.bank_account.update.detail` | `audit.bank_account.create.detail` | `audit.bank_account.permission.{view,withdrawal}.detail` (args `granted`, `revoked`) |

Reads write **no** audit row. Append runs `@Transactional(REQUIRES_NEW)` so an audit failure doesn't
roll back the business transaction.

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBE_VALIDATION` | 200/400 | all-null update body; unknown permission `kind` / `FULL_ACCESS`; bean validation |
| `WBE_BANK_ACCOUNT_NOT_FOUND` | 200 | account not in tenant (update / permissions) |
| `WBE_BANK_ACCOUNT_INVALID_TYPE` | 200 | account-type code outside 02..08 |
| `WBE_BANK_ACCOUNT_INVALID_DATE` | 200 | maturity date not `yyyy-MM-dd`-shaped |
| `WBE_BANK_ACCOUNT_INVALID_VAS_ACCT` | 200 | VAS FK missing / inactive / wrong tenant |
| `WBE_USER_NOT_IN_TENANT` | 200 | permission want-id has no active tenant link |
| `WBE_CANNOT_REVOKE_SELF` | 200 | non-admin caller tried to revoke their own access |
| `WBE0452` | 200 | Withdrawal grant on a non-whitelisted bank |
| `WBE1009` / `WBE1003` / `WBE9999` | 401 / 403 / 500 | JWT missing-invalid / unauthorized / unexpected (shared) |

Full request/response shapes: [interface-specification.md](interface-specification.md).
