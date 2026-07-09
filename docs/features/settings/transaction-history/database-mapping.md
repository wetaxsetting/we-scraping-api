# ERP Transaction History — Database Mapping (Backend)

The full `ERP_TRNS_HSTR` legacy-column → entity-field → DTO-field → UI-field mapping, taken from the
real entity code. Legacy DB reference: the JEX `ERP_TRNS_HSTR` schema documented in the preserved flat
spec [../transaction-history.md §3](../transaction-history.md). Table baseline:
[references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)
(`erp_trns_hstr`).

---

## 1. Core table — `ERP_TRNS_HSTR`

Entity: [ErpTrnsHstr.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/entity/ErpTrnsHstr.java)
(`@Table("ERP_TRNS_HSTR")`, `@IdClass(ErpTrnsHstrId.class)`). 9 columns, PK `(BIZ_KEY, SEQ_NO)`. Live
migration: [`V54__erp_trns_hstr.sql`](../../../../src/main/resources/db/migration/V54__erp_trns_hstr.sql).
The entity does **not** extend `BaseEntity` — the legacy table has only `REG_ID` / `REG_DTM`
(no `MOD_*`, no `DEL_YN`).

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | `VARCHAR(20)` | `bizKey` | ✅ | Tenant key; first predicate of every query; never on the wire |
| 2 | `SEQ_NO` | `NUMERIC(10,0)` | `seqNo` (`@JdbcTypeCode(NUMERIC)`, Java `Long`) | ✅ | Per-tenant monotonic sequence; doubles as the download row id |
| 3 | `ORG_FILE_NM` | `VARCHAR(500)` | `orgFileNm` | | Original filename supplied at write time |
| 4 | `SAVE_FILE_NM` | `VARCHAR(500)` | `saveFileNm` | | NAS / storage-root filename; used as the `Content-Disposition` name |
| 5 | `FILE_SIZE` | `NUMERIC(10,0)` | `fileSize` (`@JdbcTypeCode(NUMERIC)`, Java `Long`) | | Bytes; `0` for batch rows with no physical file (download → `WBE2015`) |
| 6 | `ERP_TYPE` | `CHAR(1)` | `erpType` (`@JdbcTypeCode(CHAR)`) | | Direction: `W` WeCMS→ERP · `E` ERP→WeCMS · `D` deleted |
| 7 | `TRNS_TYPE_CD` | `CHAR(2)` | `trnsTypeCd` (`@JdbcTypeCode(CHAR)`) | | Transaction type `"01".."17"` |
| 8 | `REG_ID` | `VARCHAR(100)` | `regId` | | Login id of the triggering user (or `SYSTEM` for batch) |
| 9 | `REG_DTM` | `VARCHAR(14)` | `regDtm` | | UTC `yyyyMMddHHmmss`; filter target for `SUBSTRING(REG_DTM,1,8) BETWEEN ? AND ?` |

`CHAR(1)` / `CHAR(2)` columns are mapped `String` + `@JdbcTypeCode(SqlTypes.CHAR)` so Hibernate
`validate` accepts the `bpchar` columns against the live JEX schema (same precedent as
`SysScrOptionWecms.sortYn`). The `NUMERIC(10,0)` columns are mapped `Long` + `@JdbcTypeCode(NUMERIC)`.

No FK / CHECK / UNIQUE constraints, no triggers (DEV parity — the only index is the PK). The two helper
performance indexes the `wecms-api/CLAUDE.md` narrative attributes to "V71"
(`IX_*_BIZ_REG_DTM`, `IX_*_BIZ_ERP_TYPE`) are part of the documented intent; verify their presence in
`V54__erp_trns_hstr.sql` before relying on them (the live `V54` body transcribed in this doc shows only
`pk_erp_trns_hstr`).

---

## 2. `USER_NM` resolution — LEFT JOIN, not `WB_GET_USER_NM`

The legacy IDO resolved the row's user display name with the DB function `WB_GET_USER_NM(REG_ID)`. The
refactor replaces it with a JPQL `LEFT JOIN UserInfm u ON u.userId = e.regId` inside
[ErpTrnsHstrRepository.search](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/repository/ErpTrnsHstrRepository.java):

```jpql
SELECT new …ErpTrnsHistoryProjection(
    e.seqNo, e.orgFileNm, e.saveFileNm, e.fileSize, e.erpType, e.trnsTypeCd,
    e.regDtm, e.regId, u.userNm
)
FROM ErpTrnsHstr e
LEFT JOIN UserInfm u ON u.userId = e.regId
WHERE …
```

- **LEFT** is intentional — `REG_ID = 'SYSTEM'` (batch writes) and removed users have no matching
  `USER_INFM` row; `userNm` comes back `null` and the frontend falls back to `reg_id` (legacy parity).
- No formal JPA relationship is declared (`ERP_TRNS_HSTR` and `USER_INFM` share no FK — legacy schema
  parity forbids adding one); the HQL `ON` clause joins on the bare columns.

---

## 3. SEQ_NO generation — `wb_get_next_val` funnel (writer only)

The read/download/export paths never generate a `SEQ_NO`. The inert
[ErpTrnsHistoryWriter](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryWriter.java)
(no live caller yet) allocates one through the single legacy-function funnel:

```
LegacyFunctionRepository.nextErpTrnsSeqNo(bizKey)   →   wb_get_next_val('erp_trns_hstr','seq_no', bizKey, …)
```

Per-tenant `MAX+1`, verbatim from the live JEX function (V140 branch #28 per the centralization audit);
the writer wraps the call + INSERT in one transaction and retries once on `DataIntegrityViolationException`.

---

## 4. Legacy → Refactor → DTO → UI mapping

| Legacy column | Entity field | List DTO field (`ErpTrnsHistoryRow`) | UI label / use |
|---|---|---|---|
| `SEQ_NO` | `seqNo` | `seq_no` | — (row id / download key; not a visible column) |
| `REG_DTM` | `regDtm` | `reg_dtm` | **Date** (two-line date + time, formatted FE-side) |
| `ERP_TYPE` | `erpType` | `erp_type` | **Type** column — rendered via `erp_type_key` |
| — (derived) | — | `erp_type_key` | i18n key `settings.erp_transaction_history.direction.*` → `t(key)` |
| `TRNS_TYPE_CD` | `trnsTypeCd` | `trns_type_cd` | **Transaction Type** — rendered via `trns_type_key` |
| — (derived) | — | `trns_type_key` | i18n key `settings.erp_transaction_history.type.NN` → `t(key)` |
| `ORG_FILE_NM` | `orgFileNm` | `org_file_nm` | **File** (download link label; suggested download name) |
| `SAVE_FILE_NM` | `saveFileNm` | `save_file_nm` | — (forensic parity; the actual `Content-Disposition` name on download) |
| `FILE_SIZE` | `fileSize` | `file_size` | gates the File-cell link (FE hides it when `0`); export `FILE_SIZE` column |
| `REG_ID` | `regId` | `reg_id` | **User** fallback when `user_nm` is null; keyword filter target |
| — (LEFT JOIN `UserInfm.userNm`) | — (projection only) | `user_nm` | **User** display name (replaces `WB_GET_USER_NM`) |
| `BIZ_KEY` | `bizKey` | (not on wire) | — (tenant scope only) |

The frontend column set (Figma 134:16546): **Date** → `REG_DTM`; **Type** → `ERP_TYPE`;
**Transaction Type** → `TRNS_TYPE_CD`; **File** → `ORG_FILE_NM` (+ `FILE_SIZE` gate); **User** →
`USER_NM` (fallback `REG_ID`). There is no visible Save-file-name or seq-no column.

---

## 5. `ERP_TYPE` (direction) + `TRNS_TYPE_CD` (transaction type) code sets

`ERP_TYPE` (3 values, `ErpTrnsHistoryService.ALLOWED_ERP_TYPES = {W,E,D}`):

| Code | Meaning | Wire i18n key |
|---|---|---|
| `W` | WeCMS → ERP (export / batch row) | `settings.erp_transaction_history.direction.wecms_to_erp` |
| `E` | ERP → WeCMS (uploaded ERP file) | `settings.erp_transaction_history.direction.erp_to_wecms` |
| `D` | Deleted (soft-delete of an `E` row) | `settings.erp_transaction_history.direction.deleted` |

`TRNS_TYPE_CD` (17 codes `"01".."17"`; service validates the numeric range, resolves the key as
`settings.erp_transaction_history.type.NN`). Full label table: [../transaction-history.md §3.3](../transaction-history.md).
The legacy HAENGSUNG-only gating (code `15` Exchange Rate shown only for `prdtType='H'`) is **not**
ported — every code is valid for every tenant (OQ-2). The only universal per-tab restriction preserved
is the Deleted-tab dropdown subset (`02`, `03`, `12`, `13`, `16`), enforced **frontend-side** only.

---

## 6. Relationships & scope

- **No FK relationships.** `ERP_TRNS_HSTR` stands alone; `USER_NM` is resolved by a JOIN, not a mapped
  association.
- **Tenant isolation:** `BIZ_KEY` is the leading PK column; cross-tenant access cannot succeed by
  accident, and the download path re-checks the row's `biz_key` against `TenantContext`.
- **In-scope tables:** `ERP_TRNS_HSTR` only (+ a read-only LEFT JOIN to `USER_INFM`). No
  `BUSA_DOC_*` / `TB_TRNS_*` staging tables are entities of this feature.
