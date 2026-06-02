# Settings > ERP Transaction History — DB Design

**Refactor screen ID:** `erp-transaction-history`
**Feature spec:** [../../features/settings/erp-transaction-history.md](../../../features/settings/transaction-history.md)
**Migration mapping:** [../../migration/erp-transaction-history-mapping.md](../../migration/erp-transaction-history-mapping.md)
**Backend design:** [../../backend/settings/erp-transaction-history.md](../../business-rules/settings/erp-transaction-history.md)
**JEX reference:** `IDO.ERP_TRNS_HSTR_R001.xml`, `IDO.ERP_TRNS_HSTR_R001_CNT.xml` — read-only

> Implemented in [V71](../../../../src/main/resources/db/migration/V71__erp_trns_hstr_schema.sql) + [V72](../../../../src/main/resources/db/migration/V72__erp_trns_hstr_demo_seed.sql). This file is the design-time spec — the migrations are the source of truth for the live local schema.

---

## 1. Source table (legacy DEV — schema captured 2026-05-11)

Schema captured live from `wecmsvndev.erp_trns_hstr` via the read-only `postgres_dev` MCP. Refactor mirrors **column-for-column** per the [legacy schema parity rule](../../../../CLAUDE.md). No documented deviations.

### 1.1 `ERP_TRNS_HSTR` — ERP file-exchange audit ledger

PK: `(BIZ_KEY, SEQ_NO)` — `PK_ERP_TRNS_HSTR` UNIQUE btree on DEV.
DEV row count: **759** for tenant `VN_VNM0004_000001` (2026-05-11). DEV-wide row count: **20 481**.

| Column | Type | NN | Used by screen | Notes |
|---|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | Y | yes (tenant scope) | Always part of WHERE clause; never exposed on the wire |
| `SEQ_NO` | NUMERIC(10,0) | Y | yes (PK; doubles as download row id) | Per-tenant monotonic sequence (legacy `wb_get_next_val('erp_trns_hstr', 'seq_no', biz_key)`) |
| `ORG_FILE_NM` | VARCHAR(500) | Y | yes (File column) | Original filename as supplied at write-time |
| `SAVE_FILE_NM` | VARCHAR(500) | Y | yes (download — `Content-Disposition` filename) | Filename on NAS / local storage root |
| `FILE_SIZE` | NUMERIC(10,0) | Y | yes (`> 0` → file link visible, `= 0` → empty cell) | Bytes; `0` for batch rows with no physical file behind them |
| `ERP_TYPE` | CHAR(1) | Y | yes (Type column + tab discriminator) | `W`/`E`/`D` — WeCMS→ERP / ERP→WeCMS / Deleted |
| `TRNS_TYPE_CD` | CHAR(2) | Y | yes (Transaction Type column) | `"01"`..`"17"` — see legacy `wabooks.enums.ErpTypeCd` for the 17-code set |
| `REG_ID` | VARCHAR(100) | Y | yes (User column + keyword search target) | Login id (or `SYSTEM` for batch writers) |
| `REG_DTM` | VARCHAR(14) | Y | yes (Date column + date-range filter target) | UTC `yyyyMMddHHmmss`; date filter via `SUBSTRING(REG_DTM, 1, 8) BETWEEN ? AND ?` |

No FK constraints, no CHECK constraints, no UNIQUE constraints beyond the PK. No triggers DEV-side (verified — `pg_trigger` query returned zero rows for `erp_trns_hstr::regclass`).

The refactor entity ([`ErpTrnsHstr.java`](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/entity/ErpTrnsHstr.java)) mirrors all 9 columns 1:1.

---

## 2. Local migrations

### 2.1 `V71__erp_trns_hstr_schema.sql` — table + helper indexes

Creates `ERP_TRNS_HSTR` with the column shape above. Adds two performance indexes beyond DEV parity (the legacy schema has only the PK index):

| Index | Columns | Rationale |
|---|---|---|
| `IX_ERP_TRNS_HSTR_BIZ_REG_DTM` | `(BIZ_KEY, REG_DTM DESC)` | Default sort + date-range filter target |
| `IX_ERP_TRNS_HSTR_BIZ_ERP_TYPE` | `(BIZ_KEY, ERP_TYPE)` | `CASE WHEN ERP_TYPE = ... THEN 1 ELSE 0 END` branches in the tab-counts query |

Extra performance indexes are allowed beyond DEV parity per the [legacy schema parity rule](../../../../CLAUDE.md). Idempotent (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`).

### 2.2 `V72__erp_trns_hstr_demo_seed.sql` — real-DEV seed

30 rows from real JEX DEV for tenant `VN_VNM0004_000001`. Sample selection: top 10 per direction (`W` / `E` / `D`) ordered by `REG_DTM DESC, SEQ_NO DESC`. Mix preserves real-world filename patterns (Account Inquiry exports, Vendor Payment uploads, Internal Transfer ingests, batch-generated rows, etc.). All seeded rows have `FILE_SIZE > 0` — fixture files are NOT committed under the storage root, so the download endpoint surfaces `WBE2015` for any seeded `seqNo` whose file hasn't been manually placed at `<storage-root>/<biz_key>/erp-transaction-history/<save_file_nm>`. Per the [data migration rule](../../../../CLAUDE.md) (≤30 rows per active table, idempotent via `INSERT … ON CONFLICT DO NOTHING`).

---

## 3. Entity ↔ schema mapping

| Entity field (Java) | Type | Column | DDL type | Notes |
|---|---|---|---|---|
| `bizKey` | `String` | `BIZ_KEY` | `VARCHAR(20) NOT NULL` | `@Id` part 1 |
| `seqNo` | `Long` | `SEQ_NO` | `NUMERIC(10,0) NOT NULL` | `@Id` part 2; `@Column(precision = 10, scale = 0)` so Hibernate `validate` binds NUMERIC rather than the default BIGINT for `Long` |
| `orgFileNm` | `String` | `ORG_FILE_NM` | `VARCHAR(500) NOT NULL` | — |
| `saveFileNm` | `String` | `SAVE_FILE_NM` | `VARCHAR(500) NOT NULL` | — |
| `fileSize` | `Long` | `FILE_SIZE` | `NUMERIC(10,0) NOT NULL` | Same precision/scale trick as `seqNo` |
| `erpType` | `String` | `ERP_TYPE` | `CHAR(1) NOT NULL` | `@JdbcTypeCode(SqlTypes.CHAR)` so Hibernate binds CHAR rather than VARCHAR (same convention as `SysScrOptionWecms.sortYn`) |
| `trnsTypeCd` | `String` | `TRNS_TYPE_CD` | `CHAR(2) NOT NULL` | Same CHAR binding |
| `regId` | `String` | `REG_ID` | `VARCHAR(100) NOT NULL` | — |
| `regDtm` | `String` | `REG_DTM` | `VARCHAR(14) NOT NULL` | Kept as `String yyyyMMddHHmmss` to mirror legacy `DateTimeUtils.utcNow()` output — never widened to `OffsetDateTime` because the date-range filter relies on `SUBSTRING(regDtm, 1, 8)` which depends on the string shape |

Composite-id class [`ErpTrnsHstrId`](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/entity/ErpTrnsHstrId.java) holds `bizKey` + `seqNo` as a Serializable — Hibernate IdClass convention.

---

## 4. Cross-table dependencies (read-only joins)

| Join | Purpose | Refactor implementation |
|---|---|---|
| `ERP_TRNS_HSTR.REG_ID = USER_INFM.USER_ID` | Resolve user display name | JPQL `LEFT JOIN UserInfm u ON u.userId = e.regId` in [`ErpTrnsHstrRepository#search`](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/repository/ErpTrnsHstrRepository.java). Replaces legacy `WB_GET_USER_NM(REG_ID)` function call (legacy [`IDO.ERP_TRNS_HSTR_R001.xml`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_R001.xml) line 34). LEFT variant intentional — `REG_ID = 'SYSTEM'` (batch writes) and deleted users yield `null`; frontend falls back to `reg_id`. No `BIZ_KEY` predicate on the join (mirrors legacy single-arg `WB_GET_USER_NM` behaviour which is also tenant-agnostic). |

No other joins. The legacy schema has no FK from `ERP_TRNS_HSTR` to anything else, and the refactor preserves that.

---

## 5. Write paths — out of scope for this feature

`ERP_TRNS_HSTR` is **read-only** from the refactor's `domain/setting/erptransactionhistory` module. Rows arrive in the table only via:

| Write path | Owner | Refactor status |
|---|---|---|
| User-initiated file upload (legacy `comm_0219_001_c001`) | Legacy JEX `_act.jsp` — empty / broken in practice | NOT ported (OQ-1) |
| Per-feature audit writes from Payment / Transfer / Account-Inquiry features | Each future feature's service | NOT ported yet (Sprint 2+) |
| ERP scheduled batch writers (`ERP_0100_001..004`) | Legacy `batch.jex.wabooks.erp` package | NOT ported (no scheduled batch infrastructure on the refactor side yet) |
| Manual refresh helper (`ErpDataRefreshHelper`) | Legacy `wabooks.helper` | NOT ported (Sprint 2+) |

Soft-delete (`ERP_TYPE='D'`) writes are driven from external features (Scheduled Payment "Cancel" flow). The refactor's read endpoints surface deleted rows under the Deleted tab without ever writing to them.

---

## 6. Storage-root layout (Phase 2 — download endpoint)

The physical-file storage root is a **separate concern** from the DB schema — the row carries `SAVE_FILE_NM` only; the resolver composes a filesystem path at download time. Documented in detail in [feature spec §9 OQ-3](../../../features/settings/transaction-history.md) and [backend design §10](../../business-rules/settings/erp-transaction-history.md). Summary:

```
<wecms.erp-transaction-history.storage-root>
  └── <BIZ_KEY>
      └── erp-transaction-history/
          └── <SAVE_FILE_NM>
```

Default `storage-root` on Windows: `C:/WECMS PROJECT/File Data`. Override via env var `WECMS_ERP_TRANSACTION_HISTORY_STORAGE_ROOT`. Path-traversal protection lives in [`ErpTrnsHistoryFileResolver`](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryFileResolver.java) — resolved paths are asserted to stay under the tenant subtree.

The slug `erp-transaction-history` (not the legacy WSVC id `comm_0219_001_c001`) is the folder name on the refactor side — deliberate divergence so future tooling can identify "refactor-written" file paths at a glance.

---

## 7. Changelog

- 2026-05-11 — v0.1 — initial schema spec (extracted from the original backend doc §2 during the docs-structure-alignment pass; matches the FB-History `database/settings/firm-banking-history-schema.md` precedent).
