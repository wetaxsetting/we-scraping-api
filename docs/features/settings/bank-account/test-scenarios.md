# Bank Account — Test Scenarios (Backend)

SIT/UAT scenarios per operation. The feature is **list + update + register + permissions + audit-logs**;
the table below also states explicitly which CRUD ops do NOT exist. Frontend scenarios:
[wecms-web/docs/features/settings/bank-account/test-scenarios.md](../../../../../wecms-web/docs/features/settings/bank-account/test-scenarios.md).

> Backend JUnit coverage exists for this feature in `src/test/` (3 classes, 11 `@Test` methods — see
> §3) — the scenarios below are the broader SIT/UAT acceptance set; the existing classes cover the
> highest-value guards (roster admin-exclusion, withdrawal `WBE0452`, bank-name resolution, active-only
> list, permission labels, audit-logs).

---

## 1. SIT / UAT scenarios

### List

| # | Scenario | Expected |
|---|---|---|
| L1 | List with no sort | active accounts (`ACTV_YN='Y'`) ordered `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC`; inactive accounts absent |
| L2 | Sort by `BANK` ASC | rows ordered by `BANK_CD ASC` then the default tiebreak |
| L3 | Sort by `BALANCE`… | (no `BALANCE` field on this whitelist — Bank Account sorts `BANK`/`ACCOUNT_NICKNAME`/`DESCRIPTION`/`ACCOUNT_TYPE`/`MATURITY_DATE`/`VAS_CODE`/`VAS_NAME`; an unknown field falls back to default ORDER BY) |
| L4 | Unknown / blank `sortField` | falls back to default ORDER BY (no error, no SQL injection) |
| L5 | Bank name resolution | `bank_name` shows the short bank name (`BankCdResolver`), never the raw code when a name exists |
| L6 | Modifier name | `last_modified_by` shows the user display name (`USER_NM`), not the raw `MOD_ID` / e-mail |
| L7 | Permission labels | `view_permission_label` / `withdrawal_permission_label` show `"X"` (one grantee) or `"X + N more"`; null when no active grantee; admins not counted |
| L8 | Maturity formatting | saving/fixed accounts show ISO `yyyy-MM-dd`; current accounts show null |

### Update

| # | Scenario | Expected |
|---|---|---|
| U1 | Patch nickname only | 200; row updated; audit row written (`update.detail`) |
| U2 | All-null body | `WBE_VALIDATION` |
| U3 | Unknown `account_type_cd` (e.g. `99`) | `WBE_BANK_ACCOUNT_INVALID_TYPE` |
| U4 | `maturity_date` = bad length (`2025-9-3`→ strips to `2025-9-3`≠8) | `WBE_BANK_ACCOUNT_INVALID_DATE` |
| U5 | `maturity_date` = `""` (empty) | clears `EXPI_DT` (saving → current) |
| U6 | Set VAS to a valid active row | 200; `VAS_ACCT_*` stamped from `BUSI_VAS_ACCT_INFM` (authoritative cd/nm, ignoring the wire echo) |
| U7 | Set VAS to a missing / inactive / cross-tenant seqNo | `WBE_BANK_ACCOUNT_INVALID_VAS_ACCT` |
| U8 | `vas_acct_seq_no = 0` | clears all three VAS fields |
| U9 | Account not in tenant | `WBE_BANK_ACCOUNT_NOT_FOUND` |

### Register

| # | Scenario | Expected |
|---|---|---|
| R1 | Register two new accounts | 200; `{ inserted: 2, skipped: 0 }`; rows `ACTV_YN='Y'`, `HAND_REG_YN='N'`; audit row per insert |
| R2 | Register an account that already exists | silently skipped (`skipped` incremented) |
| R3 | Item with blank `account_type_cd` | defaults to `02` (Current) and inserts |
| R4 | Item with unknown `account_type_cd` | `WBE_BANK_ACCOUNT_INVALID_TYPE` |
| R5 | Empty `items[]` | `WBE_VALIDATION` |
| R6 | Blank `bank_cd` / `acct_no` / `currency` | `WBE_VALIDATION` |

### Permissions — roster (GET)

| # | Scenario | Expected |
|---|---|---|
| P1 | `type=VIEW` | every active non-admin tenant user, `is_selected` reflecting active `AUTH_TYPE='V'` rows; `total_selected` correct |
| P2 | `type=WITHDRAWAL` | same shape for `AUTH_TYPE='W'` |
| P3 | `type=FULL_ACCESS` (or garbage) | `WBE_VALIDATION` |
| P4 | Admins | never appear in `members` (excluded) |
| P5 | Non-admin caller, self currently granted | own row `is_locked=true, lock_reason="SELF"` |
| P6 | Account not in tenant | `WBE_BANK_ACCOUNT_NOT_FOUND` |
| P7 | `account_label` | `"<BANK_CD> / <ACCT_NO> (<CCY>)"` |

### Permissions — replace (PUT)

| # | Scenario | Expected |
|---|---|---|
| W1 | Grant View to two users | 200; new `AUTH_TYPE='V'` rows `USE_YN='Y'`; one audit event (granted 2, revoked 0) |
| W2 | Remove a user from the set | that user's row soft-revoked (`USE_YN='N'`); audit event (revoked 1) |
| W3 | Re-grant a previously revoked user | `USE_YN` flips `'N'→'Y'` (no duplicate row) |
| W4 | No-op save (same set) | 200; no audit row written |
| W5 | Withdrawal grant on a non-whitelisted bank | `WBE0452` |
| W6 | Withdrawal **revoke-all** (empty `user_ids`) on a non-whitelisted bank | allowed (clears stale rows) |
| W7 | want-id not in the tenant | `WBE_USER_NOT_IN_TENANT` |
| W8 | Non-admin caller revokes themselves (currently granted) | `WBE_CANNOT_REVOKE_SELF` |
| W9 | `kind=FULL_ACCESS` | `WBE_VALIDATION` |
| W10 | Account not in tenant | `WBE_BANK_ACCOUNT_NOT_FOUND` |

### Audit logs (GET)

| # | Scenario | Expected |
|---|---|---|
| AL1 | Account with edit + permission history | rows latest-first; metadata (`ACCT_MSTR`) + permission (`ACCT_AUTH_INFO`) entries both present |
| AL2 | Empty history | `{ items: [] }` (never an error) |
| AL3 | Locale | activity / detail strings in the caller's locale; defaults to EN when context has none |
| AL4 | Shared-DB coexistence | rows written by live JEX (`WSVC_ID='comm_0183_001'`) appear alongside refactor (`'bank-account'`) rows |
| AL5 | Actor | `reg_nm` (display name) shown, fallback `reg_id`, never blank |

### Authorization

| # | Scenario | Expected |
|---|---|---|
| A1 | User with `settings-core-bank-account` | all operations succeed |
| A2 | Authenticated user without the grant | 403 `WBE1003` |
| A3 | No / invalid JWT | 401 `WBE1009` |

### Operations that do NOT exist (negative coverage)

| # | Scenario | Expected |
|---|---|---|
| N1 | `POST /api/v1/settings/bank-account` (create one account) | no such handler → 405 / 404 (use `/register`) |
| N2 | `DELETE /api/v1/settings/bank-account/{bankCd}/{acctNo}` | no such handler — no de-register surface |
| N3 | `GET /api/v1/settings/bank-account/{bankCd}/{acctNo}` (single detail) | no such handler — list row carries all fields |
| N4 | `POST …/export` (XLSX) | no such handler — Bank Account has no data export |
| N5 | `POST …/excel/preview` or `/excel/import` | no such handler — `register` is JSON, not Excel import |

---

## 2. Error-handling matrix

| # | Scenario | Expected |
|---|---|---|
| E1 | Bean-validation failure (over-length nickname, blank register item) | `WBE_VALIDATION` envelope, lowercase snake_case |
| E2 | Missing tenant context | `WBE9999` |
| E3 | Business error | HTTP 200, `status.error=true`, `status.code` set |

---

## 3. Test-coverage notes

Three backend JUnit classes exist for this feature under
`src/test/java/com/kosign/wecms/domain/setting/bankaccount/service/` (11 `@Test` methods total):

| Class | `@Test` count | Covers |
|---|---|---|
| `SettingsBankAccountServiceBankNameTest` | 7 | bank-name resolution (short name); active-only list query (never the unfiltered finder); permission label 0 / 1 / N grantees; audit-logs row mapping (tenant + account scope); audit-logs empty history |
| `BankAccountPermissionServiceRosterTest` | 2 | roster excludes admins (VIEW kind); roster excludes admins (WITHDRAWAL kind) |
| `BankAccountPermissionServiceWithdrawalWhitelistTest` | 2 | withdrawal on non-whitelisted bank → `WBE0452`; withdrawal on whitelisted bank passes the guard |

Recommended remaining follow-up (not yet covered):

- `SettingsBankAccountService` update path — validation (`WBE_VALIDATION` all-null, `_INVALID_TYPE`,
  `_INVALID_DATE`, `_INVALID_VAS_ACCT`) + register dup-skip + audit append.
- `BankAccountPermissionService.replace` — the grant/revoke diff, soft-revoke flip, self-lock
  (`WBE_CANNOT_REVOKE_SELF`), `WBE_USER_NOT_IN_TENANT`, no-op-skips-audit, and the `FULL_ACCESS`-rejection
  branch.
- A `SettingsBankAccountControllerTest` asserting the lowercase snake_case wire shape + the
  `BusinessException → status.code` envelope mapping.
