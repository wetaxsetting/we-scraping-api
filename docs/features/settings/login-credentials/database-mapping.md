# Login Credentials — Database Mapping (Backend)

Full legacy-column → entity-field → DTO-field → UI-field mapping, taken from the real entity code.
Legacy DDL is the schema source of truth; the refactor mirrors it column-for-column (Legacy Schema
Parity Rule). All four tables are in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md): `bank_cert_info`,
`gdt_cert_info`, `acct_auth_info`, `audit_logs_infm` (+ `bank_infm_detl` for the read-only picker).

---

## 1. Bank credentials — `BANK_CERT_INFO`

Entity: [BankCertInfo.java](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/entity/BankCertInfo.java)
(`@Table("BANK_CERT_INFO")`, `@IdClass(BankCertInfoId.class)`). Owned by the **banking**
`bankcredentials` package and **reused** by this slice — no second entity. The entity does **not**
extend `BaseEntity` (legacy audit columns are VARCHAR strings, not `OffsetDateTime`).

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | Tenant key; first predicate of every query |
| 2 | `SEQ_NO` | NUMERIC(10,0) | `seqNo` (`BigDecimal`) | ✅ | Per-tenant MAX+1 via `wb_get_next_val` |
| 3 | `BANK_CD` | VARCHAR(20) | `bankCd` | | NAPAS/CITAD bank code |
| 4 | `CERT_INFO_DV` | VARCHAR(1) | `certInfoDv` | | discriminator; refactor always writes `'1'` (all live rows are `'1'`) |
| 5 | `CERT_INFO_CON` | VARCHAR(2000) | `certInfoCon` | | login id / username — **plaintext** (legacy stores plain) |
| 6 | `CERT_INFO_DETL_CON` | VARCHAR(2000) | `certInfoDetlCon` | | **password ciphertext** — legacy pgcrypto AES-256-CBC base64 (see crypto note) |
| 7 | `CERT_INFO_DETL_CON2` | VARCHAR(2000) | `certInfoDetlCon2` | | per-bank extra / Corporate ID — stored **plain** |
| 8 | `CERT_INFO_DETL_CON3` | VARCHAR(2000) | `certInfoDetlCon3` | | login-type / sub-type label — stored **plain** |
| 9 | `ACCT_LIST_INQ_STTS` | VARCHAR(20) | `acctListInqStts` | | last scrape outcome code |
| 10 | `ACCT_LIST_INQ_MSG` | VARCHAR(255) | `acctListInqMsg` | | last scrape outcome message |
| 11 | `LST_ACCT_LIST_INQ_DT` | VARCHAR(8) | `lstAcctListInqDt` | | last scrape date `yyyyMMdd` |
| 12 | `NXISAS_GB_SEQ_NO` | NUMERIC(10,0) | `nxisasGbSeqNo` | | sub-type discriminator (radio value); defaults `0` (Corporate) |
| 13 | `REG_ID` | VARCHAR(100) | `regId` | | creator login id |
| 14 | `REG_DTM` | VARCHAR(14) | `regDtm` | | `yyyyMMddHHmmss` UTC |
| 15 | `MOD_ID` | VARCHAR(100) | `modId` | | last modifier login id |
| 16 | `MOD_DTM` | VARCHAR(14) | `modDtm` | | `yyyyMMddHHmmss` UTC |

> **Crypto note.** `CERT_INFO_DETL_CON` holds the password ciphertext produced by
> [BankCertCryptoService](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java):
> **AES-256-CBC, all-zero IV, PKCS5, base64**, key = raw `SHA-256("wabooksvn")`. Byte-identical to
> legacy `public.encrypt()`/`public.decrypt()`. The entity Javadoc still says "AES-GCM" — **stale**
> (see [refactor-gap-analysis.md](refactor-gap-analysis.md)); the live service is the
> legacy-compatible cipher.

Find-or-create dedup key (legacy `fnsh_0101_001_c001:166-194`):
`(BIZ_KEY, BANK_CD, CERT_INFO_CON, NXISAS_GB_SEQ_NO)` via
`findFirstByBizKeyAndBankCdAndCertInfoConAndNxisasGbSeqNoOrderBySeqNoAsc`. No DB unique constraint
(DEV parity) — uniqueness is a service-layer find-or-create + one-shot race retry.

---

## 2. GDT credentials — `GDT_CERT_INFO`

Entity: [GdtCertInfo.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/entity/GdtCertInfo.java)
(`@Table("GDT_CERT_INFO")`, `@IdClass(GdtCertInfoId.class)`). The only entity **owned** by this
package. No `BaseEntity` (same precedent as `BankCertInfo`).

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | Tenant key |
| 2 | `SEQ_NO` | NUMERIC(10,0) | `seqNo` (`BigDecimal`) | ✅ | Per-tenant MAX+1 via `wb_get_next_val` |
| 3 | `CERT_INFO_DV` | VARCHAR(1) | `certInfoDv` | | discriminator; refactor writes `'1'` (all live rows `'1'`) |
| 4 | `CERT_INFO_CON` | VARCHAR(2000) | `certInfoCon` | | GDT login id — **plaintext** |
| 5 | `CERT_INFO_DETL_CON` | VARCHAR(2000) | `certInfoDetlCon` | | **password ciphertext** — same legacy cipher as bank |
| 6 | `CERT_INFO_DETL_CON2` | VARCHAR(2000) | `certInfoDetlCon2` | | secondary slot (`extra`), legacy parity; stored plain |
| 7 | `REG_ID` | VARCHAR(100) | `regId` | | creator login id |
| 8 | `REG_DTM` | VARCHAR(14) | `regDtm` | | `yyyyMMddHHmmss` |
| 9 | `MOD_ID` | VARCHAR(100) | `modId` | | last modifier |
| 10 | `MOD_DTM` | VARCHAR(14) | `modDtm` | | `yyyyMMddHHmmss` |

One row per company (WeCMS allows exactly one GDT credential per tenant — enforced in
`createGdt` via `WBE_LOGIN_CREDENTIAL_GDT_EXISTS`).

---

## 3. Account-permission fan-out — `ACCT_AUTH_INFO` (atomic Save only)

Written by [AccountRegistrationService.fanOutAccountAuth](../../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountRegistrationService.java)
when the atomic Save (`/bank/register`) inserts a **new** `ACCT_MSTR` row. Entity:
[AcctAuthInfo.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfo.java)
(bank-account package; reused, not re-implemented).

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | Tenant key |
| 2 | `USER_ID` | VARCHAR(100) | `userId` | ✅ | one row per active tenant user |
| 3 | `BANK_CD` | VARCHAR(20) | `bankCd` | ✅ | account's bank |
| 4 | `ACCT_NO` | VARCHAR(100) | `acctNo` | ✅ | account number |
| 5 | `AUTH_TYPE` | CHAR(1) (`@JdbcTypeCode(CHAR)`) | `authType` | ✅ | `'V'` VIEW · `'W'` WITHDRAWAL (W only on the 5 withdrawal banks) |
| 6 | `USE_YN` | CHAR(1) (`@JdbcTypeCode(CHAR)`) | `useYn` | | `'Y'` for the registrant, `'N'` for other users |
| 7 | `REG_ID` | VARCHAR(100) | `regId` | | registrant login id |
| 8 | `REG_DTM` | VARCHAR(14) | `regDtm` | | `yyyyMMddHHmmss` |
| 9 | `MOD_ID` | VARCHAR(100) | `modId` | | registrant login id |
| 10 | `MOD_DTM` | VARCHAR(14) | `modDtm` | | `yyyyMMddHHmmss` |

Fan-out logic (legacy `fnsh_0101_001_c001:267-297`): on insert, every active
(`USER_BUSI_CONN_INFM.DEL_YN='N'`) tenant user gets a VIEW row (`USE_YN='Y'` for the registrant,
`'N'` for the rest) so requester + approver share the account; WITHDRAWAL rows are seeded only for the
five withdrawal-capable banks ([WithdrawalBankPolicy](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/WithdrawalBankPolicy.java)).
`ACCT_AUTH_INFO` is **not** re-fanned on an account UPDATE (legacy parity).

`ACCT_MSTR` itself (the registered account row) is the bank-account / banking feature's table — see
that feature's database-mapping for its full column set.

---

## 4. Audit history (read-only) — `AUDIT_LOGS_INFM`

The audit-logs popup reads `AUDIT_LOGS_INFM` via
[AuditLogEntryRepository](../../../../src/main/java/com/kosign/wecms/common/auditlog/repository/AuditLogEntryRepository.java)
(`findByBizKeyAndRefNoAndRefNo1AndWsvcIdInAndLangCdAndRefNo2IsNullOrderByRegDtmDesc`). The columns
projected onto [LoginCredentialAuditRow](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/dto/LoginCredentialAuditRow.java):

| Audit column (entity getter) | DTO field | UI use |
|---|---|---|
| `AUDIT_LOGS_SEQ_NO` (`getAuditLogsSeqNo`) | `audit_logs_seq_no` | row key |
| `AUDIT_LOGS_ACTV` (`getAuditLogsActv`) | `audit_logs_actv` | activity (localized) |
| `AUDIT_LOGS_ACTV_DTL` (`getAuditLogsActvDtl`) | `audit_logs_actv_dtl` | detail (localized) |
| `REG_ID` (`getRegId`) | `reg_id` | actor login id |
| `REG_NM` (`getRegNm`) | `reg_nm` | actor display name |
| `REG_DTM` (`getRegDtm`) | `reg_dtm` | timestamp |

Filter: `BIZ_KEY = bizKey` AND `REF_NO ∈ {BANK_CERT_INFO | GDT_CERT_INFO}` AND `REF_NO1 = seqNo` AND
`REF_NO2 IS NULL` AND `LANG_CD = current` AND `WSVC_ID IN ('login-credentials','comm_0218_001','comm_0218_004')`.
The legacy-id inclusion is a **read-only** coexistence union; writes use the slug only.

---

## 5. Bank picker / form-info source — `BANK_INFM_DETL` (read-only)

Not modelled as a JPA entity — a flexible key-value table (one row per `(BANK_CD, BANK_DETL_INFO_CD)`),
read via `NamedParameterJdbcTemplate`:

| Repository | `BANK_DETL_INFO_CD` values read | Projects |
|---|---|---|
| [BankPickerRepository](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/repository/BankPickerRepository.java) | `9`='Y' (scraping-capable), `10`=name, `11`=img; excludes `BANK_CD='11740099'` | `bank_cd · bank_nm · bank_img` |
| [BankFormInfoRepository](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/repository/BankFormInfoRepository.java) | `25`=seq_nm, `12`=logo_url, `13`=id_label, `16`=password_label, `19`=confirm_label, `22`=extra_label (grouped by `SEQ_NO`) | one `BankFormInfoSubType` per sub-type |

---

## 6. Legacy → Refactor → DTO → UI mapping (list / reveal)

| Legacy column | Entity field | List DTO field | UI label |
|---|---|---|---|
| `BANK_CERT_INFO.SEQ_NO` | `seqNo` | `LoginCredentialAccount.seq_no` | (opaque row key) |
| `BANK_CERT_INFO.BANK_CD` | `bankCd` | `LoginCredentialGroup.bank_cd` → `institution` (bank name lookup) | Institution / Bank |
| `BANK_CERT_INFO.CERT_INFO_CON` | `certInfoCon` | `LoginCredentialAccount.login_id` | Login ID |
| `BANK_CERT_INFO.CERT_INFO_DETL_CON` | `certInfoDetlCon` | *(never on wire — only via `RevealResponse.password`)* | (masked •••) |
| `BANK_CERT_INFO.CERT_INFO_DETL_CON3` | `certInfoDetlCon3` | `LoginCredentialAccount.login_type` | sub-type label (disambiguates same login id) |
| `BANK_CERT_INFO.NXISAS_GB_SEQ_NO` | `nxisasGbSeqNo` | `LoginCredentialAccount.nxisas_gb_seq_no` | (sub-type discriminator) |
| `BANK_CERT_INFO.MOD_ID` / `MOD_DTM` (latest in group) | `modId` / `modDtm` | `LoginCredentialGroup.modified_by` / `modified_at` | Modified By / Modified At |
| `ACCT_MSTR.{BANK_CD,ACCT_NO,ACCT_NICK_NM}` (linked) | — | `LoginCredentialBankAccount.{bank_cd,acct_no,acct_nick_nm}` | Bank Account column (+N badge) |
| `GDT_CERT_INFO.SEQ_NO` | `seqNo` | `LoginCredentialAccount.seq_no` (GDT group) | (opaque row key) |
| `GDT_CERT_INFO.CERT_INFO_CON` | `certInfoCon` | `LoginCredentialAccount.login_id` (GDT) | GDT login id |
| — (literal) | — | `LoginCredentialGroup.institution = "GDT"`, `type = "GDT"`, `bank_cd = null` | "GDT" row |

---

## 7. Relationships & scope

- **Tenant isolation:** `BIZ_KEY` is the leading PK column on every table; cross-tenant access cannot
  succeed by accident.
- **Credential ↔ account link:** `ACCT_MSTR.BANK_CERT_SEQ` references the credential `SEQ_NO`, but the
  list query resolves linked accounts **per `(BIZ_KEY, BANK_CD)`** (legacy `R005` parity) because the
  `BANK_CERT_SEQ` FK goes stale across re-registration; per-bank resolution sidesteps the staleness.
- **No new tables.** Everything is a legacy inventory table; the GDT entity is the only refactor-owned
  mapping in this package, and it maps a legacy table (`GDT_CERT_INFO`) column-for-column. SEQ_NO
  branches for `bank_cert_info` (V140 #23) and `gdt_cert_info` (V140 #26) live in the `wb_get_next_val`
  funnel.
