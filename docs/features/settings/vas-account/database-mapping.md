# Settings > VAS Account — Database Mapping (Backend)

Tables touched, the legacy-column → entity-field → DTO-field → UI-field chain, and the Flyway
migrations. Companion design doc (preserved): [references/database/vas-account.md](../../../references/database/vas-account.md).
JEX source of truth: [docs/jex/features/vas-account/database-mapping.md](../../../../../docs/jex/features/vas-account/database-mapping.md).

---

## 1. Core tables

| Table | Role | PK |
|---|---|---|
| `BUSI_VAS_ACCT_INFM` | The VAS account list — the **only** table this feature owns | `(BIZ_KEY, VAS_ACCT_SEQ_NO)` |

There are **no hierarchy / parent-child / import-staging tables**. The feature is a single flat
table. Downstream tables (`ACCT_MSTR`, `ACCT_TRNS_RCV_PAY_INFM`) reference it by `VAS_ACCT_SEQ_NO` /
`VAS_ACCT_CD` but are owned by Banking, not by this feature.

> **Verified against the live refactor DB** (`busi_vas_acct_infm`, 13 columns): PK
> `pk_busi_vas_acct_infm (biz_key, vas_acct_seq_no)`; two helper indexes
> `ix_busi_vas_acct_infm_biz_cd_del (biz_key, vas_acct_cd, del_yn)` and
> `ix_busi_vas_acct_infm_biz_del_stts (biz_key, del_yn, stts)`; **no FK, no UNIQUE other than the PK,
> no CHECK constraint, and no parent / display-order / level / depth column.** Code uniqueness within
> a tenant is enforced in the service layer (`existsByBizKeyAndVasAcctCdAndDelYn`), not by a DB UNIQUE.

## 2. `BUSI_VAS_ACCT_INFM` — entity ↔ column mapping

Entity: `BusiVasAcctInfm` (`@Table(name="BUSI_VAS_ACCT_INFM")`, `@IdClass(BusiVasAcctInfmId.class)`).
Does **not** extend `BaseEntity` — audit columns are legacy `VARCHAR(14)` strings, not
`OffsetDateTime`.

| Legacy column | Type (DEV / local) | Entity field | Notes |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) NN | `bizKey` (`@Id`) | tenant; never on the wire |
| `VAS_ACCT_SEQ_NO` | BIGINT NN (DEV NUMERIC(10,0) — V77 deviation, entity maps `Long`) | `vasAcctSeqNo` (`@Id`) | per-tenant MAX+1 surrogate |
| `VAS_ACCT_CD` | VARCHAR(15) NN | `vasAcctCd` | tenant-unique while `DEL_YN='N'` (service-enforced) |
| `VAS_ACCT_NM` | VARCHAR(50) NULL | `vasAcctNm` | |
| `VAS_ACCT_ENM` | VARCHAR(50) NULL | `vasAcctEnm` | English name (optional) |
| `VAS_ACCT_TYPE` | CHAR(1) NN | `vasAcctType` (`@JdbcTypeCode(CHAR)`) | `0` Dual / `1` Credit / `2` Debit |
| `STTS` | CHAR(1) NN | `stts` (`@JdbcTypeCode(CHAR)`) | `A` Active / `I` Inactive |
| `CONT` | VARCHAR(200) NULL | `cont` | notes |
| `DEL_YN` | CHAR(1) NN DEFAULT `'N'` | `delYn` (`@JdbcTypeCode(CHAR)`) | `N` live / `Y` deleted |
| `REG_ID` | VARCHAR(100) NN | `regId` (`updatable=false`) | |
| `REG_DTM` | VARCHAR(14) NN | `regDtm` (`updatable=false`) | `yyyyMMddHHmmss` UTC |
| `MOD_ID` | VARCHAR(100) NN | `modId` | |
| `MOD_DTM` | VARCHAR(14) NN | `modDtm` | `yyyyMMddHHmmss` UTC |

## 3. Full chain — legacy column → entity → DTO (wire) → UI field

| Concept | Legacy column | Entity field | DTO field (wire, snake_case) | UI field (frontend in-memory) |
|---|---|---|---|---|
| VAS Account Sequence No | `VAS_ACCT_SEQ_NO` | `vasAcctSeqNo: Long` | `vas_acct_seq_no: string` (list/detail) · `number` (lookup) | `seqNo` / `id` |
| VAS Account Code | `VAS_ACCT_CD` | `vasAcctCd` | `vas_acct_cd` | `vasCode` |
| VAS Account Name | `VAS_ACCT_NM` | `vasAcctNm` | `vas_acct_nm` | `accountName` |
| English Name | `VAS_ACCT_ENM` | `vasAcctEnm` | `vas_acct_enm` | `englishName` |
| Type | `VAS_ACCT_TYPE` | `vasAcctType` | `vas_acct_type` (+ `vas_acct_type_key`) | `type` / `typeKey` |
| Status | `STTS` | `stts` | `stts` (+ `stts_key`) | `status` / `statusKey` |
| Notes | `CONT` | `cont` | `cont` | `notes` |
| Parent VAS Account | — *(no column)* | — | — | derived client-side by code prefix (`buildTree`); not persisted |
| Display Order | — *(no column)* | — | — | none (rows ordered by code) |
| Created Date | `REG_DTM` | `regDtm` | (server-only) | — |
| Updated By / Date | `MOD_ID` / `MOD_DTM` | `modId` / `modDtm` | `mod_id` / `mod_dtm` | `modifiedBy` / `modifiedAt` |

> **"Parent VAS Account" and "Display Order" rows have no backing column** — they are listed here only
> to make explicit that the legacy table does not carry them. The frontend's two-level parent/child
> rendering is computed from `vas_acct_cd` string prefixes at render time and is never written back.

## 4. `BusiVasAcctInfmId` (composite key)

`bizKey: String`, `vasAcctSeqNo: Long`; `@AllArgsConstructor @EqualsAndHashCode implements Serializable`.

## 5. Migrations

| Version | Change |
|---|---|
| **V77** | `CREATE TABLE BUSI_VAS_ACCT_INFM` — column-for-column with DEV; PK `PK_BUSI_VAS_ACCT_INFM`; helper indexes `IX_..._BIZ_CD_DEL` + `IX_..._BIZ_DEL_STTS`; no UNIQUE / CHECK / FK / trigger (DEV parity). `VAS_ACCT_SEQ_NO` materialised as BIGINT (entity `Long`) — documented deviation from DEV's NUMERIC(10,0). |
| **V78** | Seed — 5 real DEV rows for `VN_VNM0004_000001` (seqNos 132/149/150/163/164). |
| **V79** | View Setting seed — `web_svc_id='vas-account'`, `dym_area_id='default'`, columns `VAS_ACCT_CD`/`VAS_ACCT_NM`/`VAS_ACCT_TYPE` (hidden)/`STTS`/`ETC`. `CHECKBOX` intentionally not seeded (no bulk delete — DN-8). |

## 6. Cross-feature reference (read-only)

| Table | Relationship |
|---|---|
| `ACCT_MSTR.VAS_ACCT_SEQ_NO` | bank-account master references a VAS account (no cascade on soft-delete) |
| `ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_SEQ_NO` / `.VAS_ACCT_CD` / `.VAS_ACCT_NM` | receipt/pay rows tagged with a VAS account by SmartOrganizer / TransactionService |

These are documented in [references/database/table-ownership.md](../../../references/database/table-ownership.md)
and [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md).
