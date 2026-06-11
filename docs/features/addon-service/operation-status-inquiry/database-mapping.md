# Operation Status Inquiry — Database Mapping

Tables, columns, keys, indexes, and Flyway migrations backing the feature. Deep DB design notes (open questions, parity audit) live in [../../../references/database/addon/operation-status-inquiry-db.md](../../../references/database/addon/operation-status-inquiry-db.md); this doc maps the *implemented* entity ↔ schema.

---

## 1. Primary table — `OPRT_STTS_INFO`

Entity: [OprtSttsInfo.java](../../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfo.java) · composite PK class [OprtSttsInfoId.java](../../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfoId.java). One row per counterparty operating-status snapshot.

| Column | JPA property | Type | Nullable | Notes |
|---|---|---|---|---|
| `BIZ_KEY` | `bizKey` (@Id) | VARCHAR(20) | NOT NULL | PK part 1 — tenant scope |
| `TAXCODE` | `taxcode` (@Id) | VARCHAR(100) | NOT NULL | PK part 2 — Vietnamese tax code (MST) |
| `COMPANYNAME` | `companyname` | VARCHAR(100) | nullable | Company name from GDT |
| `COMPANYSTATUS` | `companystatus` | VARCHAR(255) | nullable | **Raw Vietnamese** GDT operating-status string |
| `OFFICEADD` | `officeadd` | VARCHAR(255) | nullable | Registered office address |
| `SEARCHDATETIME` | `searchdatetime` | VARCHAR(14) | NOT NULL | GDT poll timestamp, `yyyyMMddHHmmss` |
| `REPRESENTATIVEFIRSTNAME` | `representativefirstname` | VARCHAR(255) | NOT NULL | Legal representative; defaults to `''` when GDT returns blank |
| `REG_ID` | `regId` | VARCHAR(100) | NOT NULL | Audit — creator user id |
| `REG_DTM` | `regDtm` | VARCHAR(14) | NOT NULL | Audit — create timestamp (14-digit UTC) |
| `MOD_ID` | `modId` | VARCHAR(100) | NOT NULL | Audit — last modifier |
| `MOD_DTM` | `modDtm` | VARCHAR(14) | nullable | Audit — last modify timestamp |

- **PK:** `(BIZ_KEY, TAXCODE)` via `@IdClass(OprtSttsInfoId.class)`.
- **Index:** `ix_oprt_stts_biz_dtm` on `(BIZ_KEY, SEARCHDATETIME)` — supports the default `SEARCHDATETIME DESC` order + paginated tenant reads.
- Audit columns are written by the service's `stampAudit(...)` (not `BaseEntity` — this entity maps the legacy VARCHAR(14) audit shape directly).

> The entity maps the Sprint-1 column subset the feature reads/writes; the DB schema mirrors DEV column-for-column per the Legacy Schema Parity Rule.

---

## 2. Audit tables (written, not read by this page)

| Table | Entity / repository | Written by | Key columns used |
|---|---|---|---|
| `EXT_TRAN_CALL_HIS` | `ExtTranCallHis` / `ExtTranCallHisRepository` (`domain.banking.synchistory`) | `#inquire` (1/call), `#bulkUpdate` (1/batch) | `SVC_ID=BI09`, `REF_DATE` (yyyyMMdd), `SEQ_NO` (max-per-day+1), `WEB_SVC_ID=aosv_0001_001_r001`, `BIZ_KEY`, `USER_ID`, `TAXCODE`, `RSLT_CD`, `ERR_CONT`, `REG_DTM` |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | `NxsiasModuleTranLogHis` / `NxsiasModuleTranLogHisRepository` (`domain.banking.nxisas.audit`) | `#inquire` (1/call), `#bulkUpdate` (1/row) | `UUID`, `BIZ_KEY`, NXISAS `CLASS=COMMON` / `MODULE=GLOBAL/VNM/PUBLIC_MASOTHUE` / `JOB=BUSSINESS_REGISTRATION_LIST`, `ERR_CODE`, `ERR_CONT`, `REQ_DATA`, `REG_ID`, `REG_DTM` |

Both tables are in the PROD Active Table List. The legacy sentinels are pinned verbatim so coexistence on a shared DB keeps audit history aligned.

---

## 3. View Setting rows — `SYS_SCR_OPTION_WECMS`

Screen `WEB_SVC_ID = 'addon-operation-status'`, `DYM_AREA_ID = 'default'` (seeded V56, patched V58):

| FIELD_ID | DISP_ORD | USE_YN | FIX_YN | SORT_YN | SIZE |
|---|---|---|---|---|---|
| `CHECKBOX` | 0 | Y | Y | N | 66px |
| `COMPANY` | 1 | Y | N | Y | auto |
| `ADDRESS` | 2 | Y | N | Y (V58) | auto |
| `OPERATION_STATUS` | 3 | Y | N | Y | 339px |
| `LAST_UPDATE` | 4 | Y | N | Y | 220px |

`SORT_YN='Y'` columns each have a matching `OperationStatusSortField` constant (the common-sort agreement rule). The Excel export `allowedFieldIds()` = `{COMPANY, ADDRESS, OPERATION_STATUS, LAST_UPDATE}`.

---

## 4. Flyway migrations

| Version | Summary |
|---|---|
| V16 | `OPRT_STTS_INFO` + `EXT_TRAN_CALL_HIS` schema; index `ix_oprt_stts_biz_dtm`. |
| V17 | Sprint-1 seed (demo rows, tenant `VN_VNM0004_000001`); idempotent `ON CONFLICT DO NOTHING`. |
| V56 | View Setting seed for `addon-operation-status` (CHECKBOX · COMPANY · ADDRESS · OPERATION_STATUS · LAST_UPDATE). |
| V57 | Real-DEV demo seed for `OPRT_STTS_INFO`. |
| V58 | Patch — `SORT_YN='Y'` on `ADDRESS`. |

> Migration version numbers are authoritative as of this writing; confirm against `src/main/resources/db/migration/` before relying on a specific Vxx — file headers can lag (see the CLAUDE.md "drafted as Vxx" notes).

---

## 5. Status string → code (read-time, not stored)

`status_cd` is derived on read by `OperationStatusService.resolveStatusCd(...)` and never persisted — see [functional-specification.md §2](functional-specification.md#2-operating-status-definition). The DB only stores the raw Vietnamese `COMPANYSTATUS`.
