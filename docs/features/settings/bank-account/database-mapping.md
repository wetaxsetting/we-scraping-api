# Bank Account — Database Mapping (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

The feature-folder view + the full column → entity → DTO → UI mapping. Two tables: `ACCT_MSTR` (master,
**owned by `domain/banking`**) and `ACCT_AUTH_INFO` (permission rows, owned by this feature). Legacy DB
reference: the live JEX `ACCT_MSTR` / `ACCT_AUTH_INFO` schema (inspect via the `postgres_dev_server_jexframe`
MCP read-only).

> Both tables are in the [legacy-table-inventory](../../../references/database/legacy-table-inventory.md)
> (`ACCT_MSTR`, `ACCT_AUTH_INFO`) — no off-inventory tables are introduced by this feature.

---

## 1. Permission table — `ACCT_AUTH_INFO`

Entity: [AcctAuthInfo.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfo.java)
(`@Table("ACCT_AUTH_INFO")`, `@IdClass(AcctAuthInfoId.class)`). Schema:
[V1__acct_auth_info.sql](../../../../src/main/resources/db/migration/V1__acct_auth_info.sql). The entity
does **not** extend `BaseEntity` — audit columns are legacy `VARCHAR(14)` strings (`yyyyMMddHHmmss`), not
the refactor's `TIMESTAMPTZ` pattern.

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | Tenant key; first predicate of every query |
| 2 | `BANK_CD` | VARCHAR(20) | `bankCd` | ✅ | Bank code (locator into `ACCT_MSTR`) |
| 3 | `ACCT_NO` | VARCHAR(100) | `acctNo` | ✅ | Account number (locator into `ACCT_MSTR`) |
| 4 | `USER_ID` | VARCHAR(100) | `userId` | ✅ | Grantee login id |
| 5 | `AUTH_TYPE` | CHAR(1) (`@JdbcTypeCode(CHAR)`) | `authType` | ✅ | `'V'` view / `'W'` withdraw / `'F'` full-access; default `'V'` |
| 6 | `USE_YN` | CHAR(1) (`@JdbcTypeCode(CHAR)`) | `useYn` | | `'Y'` active / `'N'` soft-revoked; default `'N'` |
| 7 | `REG_ID` | VARCHAR(100) | `regId` | | Grantor login id |
| 8 | `REG_DTM` | VARCHAR(14) | `regDtm` | | `yyyyMMddHHmmss` UTC |
| 9 | `MOD_ID` | VARCHAR(100) | `modId` | | Last modifier login id |
| 10 | `MOD_DTM` | VARCHAR(14) | `modDtm` | | `yyyyMMddHHmmss` UTC |

**PK** `(BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, AUTH_TYPE)` — `AUTH_TYPE` is part of the key, so the same
user can hold a VIEW row **and** a WITHDRAW row for one account independently.
**FK** `acct_auth_info_fk01 (BIZ_KEY, BANK_CD, ACCT_NO) → ACCT_MSTR(BIZ_KEY, BANK_CD, ACCT_NO)`.
Soft-revoke (`USE_YN='N'`) never deletes the row, preserving audit history (legacy `comm_0220_*` revoke
parity).

> **Stale Javadoc:** the entity comments reference "V245" / "V127" as the creating migration — the live
> migration is `V1__acct_auth_info.sql` after the 2026-06-09 renumber. Behaviour is unchanged.

### Composite PK class

[AcctAuthInfoId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfoId.java)
— `{ bizKey, userId, bankCd, acctNo, authType }` (mirrors the legacy `AcctAuthInfoPk`).

---

## 2. Master table — `ACCT_MSTR` (read + maintenance fields)

Entity: [AcctMstr.java](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java)
(`@Table("ACCT_MSTR")`, owned by `domain/banking`; V13 schema + V23 column-parity). This feature reads
the master row and writes only the maintenance subset below. Columns the feature touches:

| DB column | Type | JPA field | Bank Account use |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | `bizKey` | tenant key (PK) |
| `BANK_CD` | VARCHAR(20) | `bankCd` | bank code (PK); resolved to display name via `BankCdResolver` |
| `ACCT_NO` | VARCHAR(100) | `acctNo` | account number (PK) |
| `CCY_CD` | VARCHAR(3) | `ccyCd` | currency (VND / USD / KRW) |
| `ACCT_NICK_NM` | VARCHAR | `acctNickNm` | nickname — **editable** |
| `CONT` | VARCHAR | `cont` | description — **editable** |
| `ACCT_TYPE_CD` | VARCHAR(2) | `acctTypeCd` | account type 02..08 (`AcctTypeCd` enum) — **editable** |
| `EXPI_DT` | VARCHAR(8) | `expiDt` | maturity `yyyyMMdd` — **editable** (cleared for current accounts) |
| `VAS_ACCT_SEQ_NO` | NUMERIC(10,0) | `vasAcctSeqNo` | VAS FK into `BUSI_VAS_ACCT_INFM` — **editable** (FK-validated) |
| `VAS_ACCT_CD` | VARCHAR(50) | `vasAcctCd` | denormalised VAS code echo — **editable** (stamped from DB) |
| `VAS_ACCT_NM` | VARCHAR(50) | `vasAcctNm` | denormalised VAS name echo — **editable** (stamped from DB) |
| `PRN_SEQ` | NUMERIC(2,0) | `prnSeq` | print order — default-sort key (read-only here) |
| `BAL` / `REAL_AMT` | NUMERIC(20,6) | `bal` / `realAmt` | balance / available — echoed read-only |
| `ACTV_YN` | VARCHAR(1) | `actvYn` | active flag — list scopes to `'Y'`; `register` writes `'Y'` |
| `HAND_REG_YN` | VARCHAR(1) | `handRegYn` | manual-vs-scrape flag — `register` writes `'N'` (scraped) |
| `MOD_ID` / `MOD_DTM` | VARCHAR | `modId` / `modDtm` | last-modified actor / time (audit cell) |
| `REG_ID` / `REG_DTM` | VARCHAR | `regId` / `regDtm` | created actor / time (`register` writes) |

**On the wire `EXPI_DT` is reformatted** `yyyyMMdd` ↔ ISO `yyyy-MM-dd` (`SettingsBankAccountService.toRow`
+ `update`). The DB column stays `VARCHAR(8)`.

---

## 3. Supporting tables (read-only / write-event)

| Table | Used for | Entity / repo |
|---|---|---|
| `BUSI_VAS_ACCT_INFM` | VAS FK validation on update (`STTS='A'`, `DEL_YN='N'`, current tenant) | `BusiVasAcctInfm` / `BusiVasAcctInfmRepository` (owned by `domain/setting/vasaccount`) |
| `USER_INFM` | modifier display names (`MOD_ID → USER_NM`, legacy `WB_GET_USER_NM`); roster names | `UserInfm` / `UserInfmRepository` |
| `BUSI_ADMN_INFM` | admin set (excluded from the permission roster; legacy `ADMIN_YN != 'Y'`) | `BusiAdmnInfmRepository` |
| `USER_BUSI_CONN_INFM` | active tenant-user roster + want-id tenant validation | `UserBusiConnInfmRepository` |
| `AUDIT_LOGS_INFM` | write-event rows (shared `AuditLogService`) + popup read (`AuditLogEntryRepository`) | shared `common/auditlog` |

No `wb_get_next_val` branch is needed — `ACCT_MSTR` register writes the legacy composite PK verbatim
from the scrape, and `ACCT_AUTH_INFO` rows are keyed on the natural composite key.

---

## 4. Legacy → Refactor → DTO → UI mapping

### 4.1 List row (`BankAccountRow`)

| Legacy column | Entity field | List DTO field (`BankAccountRow`) | UI label (frontend) |
|---|---|---|---|
| `BANK_CD` | `bankCd` | `bank_cd` | (logo + addresses per-row endpoints) |
| (resolved) | — | `bank_name` | Bank (name) |
| `ACCT_NO` | `acctNo` | `acct_no` | Account No |
| `CCY_CD` | `ccyCd` | `currency` | Currency |
| `ACCT_NICK_NM` | `acctNickNm` | `nickname` | Nickname |
| `CONT` | `cont` | `description` | Description |
| `ACCT_TYPE_CD` | `acctTypeCd` | `account_type_cd` | Account Type (→ current / saving / fixed) |
| `EXPI_DT` (yyyyMMdd) | `expiDt` | `maturity_date` (ISO yyyy-MM-dd) | Maturity Date |
| `VAS_ACCT_SEQ_NO` | `vasAcctSeqNo` | `vas_acct_seq_no` | (picker seed) |
| `VAS_ACCT_CD` | `vasAcctCd` | `vas_acct_cd` | VAS Code |
| `VAS_ACCT_NM` | `vasAcctNm` | `vas_acct_nm` | VAS Account Name |
| (composed `NM(CCY)`) | — | `vas_account_bank` | VAS (compact one-line) |
| `PRN_SEQ` | `prnSeq` | `prn_seq` | (default sort) |
| `BAL` / `REAL_AMT` | `bal` / `realAmt` | `bal` / `real_amt` | Balance / Available |
| `MOD_ID` (→ USER_NM) | `modId` | `last_modified_by` | Audit Logs (actor) |
| `MOD_DTM` | `modDtm` | `last_modified_at` | Audit Logs (date) |
| (derived `ACCT_AUTH_INFO` 'V') | — | `view_permission_label` | View Permission ("X + N more") |
| (derived `ACCT_AUTH_INFO` 'W') | — | `withdrawal_permission_label` | Withdrawal Permission |

### 4.2 Update request (`BankAccountUpdateRequest`)

| Wire field | DB column | Rule |
|---|---|---|
| `nickname` | `ACCT_NICK_NM` | `@Size(20)`; null = unchanged |
| `description` | `CONT` | `@Size(200)`; null = unchanged |
| `account_type_cd` | `ACCT_TYPE_CD` | `@Size(2)`; `AcctTypeCd.fromCode` must resolve |
| `maturity_date` | `EXPI_DT` | `@Size(10)`; ISO → strip `-` → VARCHAR(8); empty = clear |
| `vas_acct_seq_no` | `VAS_ACCT_SEQ_NO` | null = unchanged; `<=0` = clear; positive = set after FK validation |
| `vas_acct_cd` / `vas_acct_nm` | `VAS_ACCT_CD` / `VAS_ACCT_NM` | echoes — service stamps the authoritative values from `BUSI_VAS_ACCT_INFM` |

### 4.3 Permission member (`BankAccountPermissionMember`)

| Wire field | Source |
|---|---|
| `user_id` / `user_nm` | `USER_INFM` (via tenant roster projection) |
| `user_type` | `USER` (admins are excluded from the roster) |
| `phone_no` | `USER_INFM.CLPH_NO`, masked (`•••••1234`) |
| `is_selected` | active `ACCT_AUTH_INFO` row for `(account, kind, user)` |
| `is_locked` / `lock_reason` | `SELF` when caller-self-selected and non-admin |

### 4.4 Audit row (`BankAccountAuditRow`)

| Wire field | DB column |
|---|---|
| `audit_logs_seq_no` | `AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO` |
| `audit_logs_actv` | `AUDIT_LOGS_INFM.AUDIT_LOGS_ACTV` (pre-localized) |
| `audit_logs_actv_dtl` | `AUDIT_LOGS_INFM.AUDIT_LOGS_ACTV_DTL` (pre-localized) |
| `reg_id` / `reg_nm` | `AUDIT_LOGS_INFM.REG_ID` / `REG_NM` (display name stamped at write time) |
| `reg_dtm` | `AUDIT_LOGS_INFM.REG_DTM` (`yyyyMMddHHmmss`; FE splits into date / time) |

---

## 5. Relationships & scope

- **`ACCT_AUTH_INFO → ACCT_MSTR`** FK on `(BIZ_KEY, BANK_CD, ACCT_NO)` — a permission row cannot exist
  without its account.
- **Tenant isolation:** `BIZ_KEY` is the leading PK column on both tables; cross-tenant access cannot
  succeed by accident.
- **In-scope tables:** `ACCT_MSTR`, `ACCT_AUTH_INFO`, `BUSI_VAS_ACCT_INFM`, `USER_INFM`,
  `BUSI_ADMN_INFM`, `USER_BUSI_CONN_INFM`, `AUDIT_LOGS_INFM` — all in the legacy inventory. No new tables.
