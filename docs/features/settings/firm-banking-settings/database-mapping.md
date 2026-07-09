# Firm Banking Settings — Database Mapping (Backend)

Feature-folder view + the full column → entity → DTO → UI mapping, read from the real entity code.
Authoritative table baseline:
[references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)
(`TB_FIRM_CONF`, `TB_FIRM_BANK_CODE`, `ACCT_MSTR`, `AUDIT_LOGS_INFM` are all in the inventory).

---

## 1. Core table — `TB_FIRM_CONF`

Entity: [TbFirmConf.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmConf.java)
(`@Table("TB_FIRM_CONF")`, `@IdClass(TbFirmConfId.class)`). 14 columns. Does **not** extend
`BaseEntity` — the legacy table uses its own `*_DTTM` / `LAST_EDTR` audit columns (no `REG_ID`/`REG_DTM`),
so the service stamps `regDttm` / `modDttm` / `lastEdtr` explicitly.

| # | DB column | Type | JPA field | PK | `@JdbcTypeCode` | Refactor use |
|---|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | — | Tenant key; first predicate of every query |
| 2 | `OUT_BANK_CD` | VARCHAR(15) | `outBankCd` | ✅ | — | NAPAS bank code (Shinhan/Vietcombank/Woori) |
| 3 | `WDRL_ACT_NO` | VARCHAR(100) | `wdrlActNo` | ✅ | — | Withdrawal account no; **plain-text** (OQ-4); cleartext on the wire (list + detail), display masking is frontend-only |
| 4 | `BANK_PRTN_ID` | VARCHAR(20) | `bankPrtnId` | | — | Bank-issued partner id |
| 5 | `VAN_ORG_CD` | VARCHAR(20) | `vanOrgCd` | | — | VAN organisation code |
| 6 | `ORG_STAT` | VARCHAR(1) | `orgStat` | | — | `0` WAITING / `1` AVAILABLE / `2` SUSPENDED / `3` TERMINATED |
| 7 | `FILE_TYPE` | VARCHAR(3) | `fileType` | | `CHAR` | Server-derived on Create (SHB/VCB/WRB); `bpchar(3)` on disk |
| 8 | `BAT_TRAN_BRANCH_DIV` | VARCHAR(1) | `batTranBranchDiv` | | `CHAR` | Default `'Y'` on Create (V15) |
| 9 | `BAT_TRAN_BRANCH_LIMIT` | NUMERIC(7,0) | `batTranBranchLimit` (`BigDecimal`) | | — | Default `2000` on Create (V16) |
| 10 | `REG_DTTM` | VARCHAR(14) | `regDttm` | | — | `yyyyMMddHHmmss` UTC, set on Create |
| 11 | `START_DTTM` | VARCHAR(14) | `startDttm` | | — | nullable |
| 12 | `END_DTTM` | VARCHAR(14) | `endDttm` | | — | nullable |
| 13 | `MOD_DTTM` | VARCHAR(14) | `modDttm` | | — | `yyyyMMddHHmmss` UTC, set on Update |
| 14 | `LAST_EDTR` | VARCHAR(20) | `lastEdtr` | | — | Last editor display name (not id) |

Composite PK: [TbFirmConfId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmConfId.java)
`{ bizKey, outBankCd, wdrlActNo }` — order matches the legacy DB PK.

`FILE_TYPE` / `BAT_TRAN_BRANCH_DIV` are `bpchar` on DEV; the entity maps `String` + `@JdbcTypeCode(CHAR)`
so Hibernate `validate` accepts the columns (same precedent as the `sort_yn` CHAR mapping in View Setting).

---

## 2. Supporting table — `TB_FIRM_BANK_CODE` (read-only dictionary)

Entity: [TbFirmBankCode.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmBankCode.java)
(`@Table("TB_FIRM_BANK_CODE")`, `@IdClass(TbFirmBankCodeId.class)`). Global NAPAS/CITAD bank registry —
**no tenant scoping** (no `BIZ_KEY`). Used by Create's referential guard and by the bank-picker /
Internal-Transfer parser lookups.

| DB column | JPA field | PK | `@JdbcTypeCode` | Notes |
|---|---|---|---|---|
| `BANK_CD` | `bankCd` | ✅ | — | NAPAS / CITAD code |
| `ANT_DIV_CD` | `antDivCd` | ✅ | `CHAR` | `'NAPAS'` / `'CITAD'` discriminator |
| `CITAD_BANK_CD` | `citadBankCd` | | `CHAR` | Companion CITAD code on NAPAS rows |
| `BANK_NM` | `bankNm` | | — | Localized (Vietnamese) bank name |
| `BANK_ENG_NM` | `bankEngNm` | | — | English bank name |
| `ENG_ABRVAT_NM` | `engAbrvatNm` | | — | English abbreviation |
| `BANK_STAS` | `bankStas` | | `CHAR` | `'Y'`/`'N'` active flag |
| `BANK_BRANCH_DIV` | `bankBranchDiv` | | — | `'1'` headquarters / blank branch |

Composite PK: [TbFirmBankCodeId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmBankCodeId.java)
`{ antDivCd, bankCd }` — a `BANK_CD` recurs across divisions.

---

## 3. List view — `@Subselect` (`FirmBankingSettingView`, replaces `V_FIRM_BANKING_SETTING`)

The legacy refactor design called for a DB view `V_FIRM_BANKING_SETTING`; per memory
`project_v_firm_banking_history_subselect.md` it is **not created as a view object** — instead a
read-only Hibernate `@Subselect` entity projects over the legacy base tables. The entity skips
schema-validate and is `@Immutable` + `@Synchronize({"TB_FIRM_CONF","ACCT_MSTR","TB_FIRM_BANK_CODE"})`.

Entity: [FirmBankingSettingView.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSettingView.java)
(`@IdClass(TbFirmConfId.class)`, same composite key).

```sql
-- @Subselect (verbatim)
SELECT
    TFC.BIZ_KEY                                 AS BIZ_KEY,
    TFC.OUT_BANK_CD                             AS OUT_BANK_CD,
    COALESCE(TFBC.BANK_NM, TFBC.BANK_ENG_NM)    AS BANK_NM,
    AM.CCY_CD                                   AS CCY_CD,
    TFC.BANK_PRTN_ID                            AS BANK_PRTN_ID,
    TFC.VAN_ORG_CD                              AS VAN_ORG_CD,
    TFC.ORG_STAT                                AS ORG_STAT,
    TFC.WDRL_ACT_NO                             AS WDRL_ACT_NO,
    TFC.FILE_TYPE                               AS FILE_TYPE,
    TFC.BAT_TRAN_BRANCH_DIV                     AS BAT_TRAN_BRANCH_DIV,
    TFC.BAT_TRAN_BRANCH_LIMIT                   AS BAT_TRAN_BRANCH_LIMIT,
    TFC.REG_DTTM                                AS REG_DTTM,
    TFC.START_DTTM                              AS START_DTTM,
    TFC.END_DTTM                                AS END_DTTM,
    TFC.MOD_DTTM                                AS MOD_DTTM,
    TFC.LAST_EDTR                               AS LAST_EDTR,
    AM.BAL                                      AS BAL,
    AM.EXPI_DT                                  AS EXPI_DT
FROM TB_FIRM_CONF TFC
LEFT JOIN ACCT_MSTR AM
    ON  TFC.BIZ_KEY     = AM.BIZ_KEY
    AND TFC.WDRL_ACT_NO = AM.ACCT_NO
LEFT JOIN TB_FIRM_BANK_CODE TFBC
    ON  TFBC.ANT_DIV_CD = 'NAPAS'
    AND TFBC.BANK_CD    = TFC.OUT_BANK_CD
```

The 18 projected columns map 1:1 to the entity fields (PK three not-null; `BANK_NM` `VARCHAR(300)`,
`CCY_CD` `VARCHAR(3)`, `BAL` `NUMERIC(23,6)`, `EXPI_DT` `VARCHAR(8)` all from the joins, nullable).

---

## 4. Legacy → Entity → DTO → UI mapping

The list DTO `FirmBankingSettingRow` (18 fields) is built from the `@Subselect` view; the detail DTO
`FirmBankingSettingDetail` (14 fields) is built from the base `TbFirmConf` entity.

| Legacy column | Entity field | List DTO (`Row`) | Detail DTO | Create/Update | UI label |
|---|---|---|---|---|---|
| `OUT_BANK_CD` | `outBankCd` | `out_bank_cd` | `out_bank_cd` | `out_bank_cd` (create / path) | Bank (logo via NAPAS code) |
| `WDRL_ACT_NO` | `wdrlActNo` | `wdrl_act_no` (**cleartext**; FE masks display) | `wdrl_act_no` (**cleartext**) | `wdrl_act_no` (create / path) | Withdrawal Account |
| `BANK_PRTN_ID` | `bankPrtnId` | `bank_prtn_id` | `bank_prtn_id` | `bank_prtn_id` | Partner ID |
| `VAN_ORG_CD` | `vanOrgCd` | `van_org_cd` | `van_org_cd` | `van_org_cd` | VAN Code |
| `ORG_STAT` | `orgStat` | `org_stat` | `org_stat` | `org_stat` | Firm Banking Status |
| `FILE_TYPE` | `fileType` | `file_type` | `file_type` | (server-derived) | — (internal) |
| `BAT_TRAN_BRANCH_DIV` | `batTranBranchDiv` | `bat_tran_branch_div` | `bat_tran_branch_div` | (server default `Y`) | — |
| `BAT_TRAN_BRANCH_LIMIT` | `batTranBranchLimit` | `bat_tran_branch_limit` | `bat_tran_branch_limit` | (server default `2000`) | — |
| `REG_DTTM` | `regDttm` | `reg_dttm` | `reg_dttm` | (server) | — |
| `MOD_DTTM` | `modDttm` | `mod_dttm` | `mod_dttm` | (server) | "Last updated" (with `LAST_EDTR`) |
| `LAST_EDTR` | `lastEdtr` | `last_edtr` | `last_edtr` | (server) | "Last updated" actor |
| `START_DTTM` / `END_DTTM` | `startDttm`/`endDttm` | `start_dttm`/`end_dttm` | `start_dttm`/`end_dttm` | — | — |
| `BANK_NM` (view) | `bankNm` (view) | `bank_nm` | — | — | Bank name (display) |
| `CCY_CD` (`ACCT_MSTR`) | `ccyCd` (view) | `ccy_cd` | — | — | currency suffix `(VND)`/`(USD)` |
| `BAL` (`ACCT_MSTR`) | `bal` (view) | `bal` | — | — | (reserved; balance) |
| `EXPI_DT` (`ACCT_MSTR`) | `expiDt` (view) | `expi_dt` | — | — | (reserved; expiry) |

**Masking:** the backend ships `wdrl_act_no` **cleartext on both list and detail** — it is the PK the
frontend addresses for Delete / Update / Detail / Audit, and a server-side mask would break those writes
(the `@Pattern` guard rejects `*`). The displayed masking (e.g. `1009****0384`) is produced **frontend-side**
by `maskAccountNumber(...)` inside `mapFirmBankingSettingRow`, which keeps the cleartext PK alongside the
masked display string (OQ-4).

---

## 5. Audit table — `AUDIT_LOGS_INFM` (Create / Update only)

Written via the shared `AuditLogService.append(...)`. No new table; the shared audit writer owns the
`audit_logs_seq_no` key generation (funnelled through `wb_get_next_val`). Read filter for the
detail-audit endpoint:

| Column | Value |
|---|---|
| `WSVC_ID` | `firm-banking-settings` (slug — OQ-6 override 2026-05-09; never `comm_0220_001` for new writes) |
| `REF_NO` | `TB_FIRM_CONF` |
| `REF_NO1` | `out_bank_cd` |
| `REF_NO2` | `wdrl_act_no` |
| `LANG_CD` | per-locale row (read filters by `TenantContext.currentLangCd()`) |
| activity / detail | `audit.firm_banking_settings.activity` · `audit.firm_banking_settings.{create,update}.detail` |

---

## 6. Relationships & scope

- **Self-contained PK:** `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)` — client-supplied on Create, no
  `wb_get_next_val` allocation.
- **Tenant isolation:** `BIZ_KEY` is the leading PK column; cross-tenant access cannot succeed by accident
  (detail/update/delete against another tenant's row surface as `WBE2020`).
- **In-scope tables:** `TB_FIRM_CONF` (CRUD), `TB_FIRM_BANK_CODE` (read-only guard/dictionary),
  `ACCT_MSTR` (read-only join for currency/balance), `AUDIT_LOGS_INFM` (shared writer). All four are in
  the [legacy-table-inventory](../../../references/database/legacy-table-inventory.md).
- **`V_FIRM_BANKING_SETTING`** is **not** a DB object — replaced by the `@Subselect` entity (§3).
