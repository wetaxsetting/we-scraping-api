# Add-on Service > Operation Status Inquiry — Database Design

> **Consolidated feature docs:** see [../../../features/addon-service/operation-status-inquiry/](../../../features/addon-service/operation-status-inquiry/) — especially [database-mapping.md](../../../features/addon-service/operation-status-inquiry/database-mapping.md) for the implemented entity ↔ schema mapping. This file remains the deep DB-design reference.

**Module:** Add-on Service · `domain/operation/`
**Refactor screen ID (proposed):** `addon-operation-status`
**JEX counterpart:** `aosv_0001_001_r001`
**Feature spec:** [../../features/addon-service/operation-status-inquiry.md](../../../features/addon-service/operation-status-inquiry.md)
**Implementation plan:** ../../planning/addon/operation-status-inquiry-plan.md _(removed — historical `planning/` folder deleted by project decision)_
**Status:** Schema is in place (V16); seed in V17. Phase 4 deliverables = View Setting seed migration + (possibly) a column-parity corrective migration after the DEV audit.

JEX is **read-only reference**.

---

## Legacy tables (from JEX)

The legacy live system uses a single table for the listing read path:

| Table | Role | Tenant scope | PK |
|---|---|---|---|
| `OPRT_STTS_INFO` | Per-counterparty (per-MST) snapshot of the GDT operating-status response. Read by `aosv_0001_001_r001` (paged + count); written by `aosv_0001_001_u001` (manual edit) and the NXISAS / OSMC GDT-pull path. | `BIZ_KEY` | `(BIZ_KEY, TAXCODE)` composite |

Companion table created in V16 but not yet read/written by any feature:

| Table | Role | Tenant scope | PK |
|---|---|---|---|
| `EXT_TRAN_CALL_HIS` | External-call audit log. Per legacy, **NOT** `BIZ_KEY`-scoped at the column level — `BIZ_KEY` is nullable and tenant filtering happens service-side. Owned by the future GDT-pull bridge. | (column nullable; service enforces) | `(SVC_ID, REF_DATE, SEQ_NO)` composite |

There is **no separate "company master" table** for this feature. `OPRT_STTS_INFO` is itself the master of (BIZ_KEY, TAXCODE) pairs known to WeCMS — it is populated by the GDT pull, not by a counterparty-onboarding flow. The richer counterparty record (`TRCO_INFM`, `TRCO_ACCT_INFO`, `TRCO_OVRS_INFM`) is in the [PROD Active Table List](../../../../CLAUDE.md) but is owned by other features (counterparty registry / payment / banking) and is NOT joined on this listing — see [§ Relationships](#relationships) below.

### Verified legacy IDO output (per [`IDO.OPRT_STTS_INFO_R001.xml`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml))

```sql
SELECT BIZ_KEY, TAXCODE, COMPANYNAME, COMPANYSTATUS, OFFICEADD,
       SEARCHDATETIME, REPRESENTATIVEFIRSTNAME,
       REG_ID, REG_DTM, MOD_ID, MOD_DTM
FROM OPRT_STTS_INFO
WHERE 1=1
??     -- IDODynamic placeholder: BIZ_KEY (always) + COMPANYSTATUS ILIKE (optional)
       --                       + (taxCode ILIKE OR companyname ILIKE) (optional)
```

11 columns total — every column on the table is exposed. Audit columns (`REG_*`, `MOD_*`) live on the IDO output but are dropped at the WSVC contract (legacy WSVC `<out>` exposes only the 6 user-visible fields).

---

## Proposed refactor tables / entities

**No new tables.** The refactor reads `OPRT_STTS_INFO` column-for-column from the legacy schema. `EXT_TRAN_CALL_HIS` is created in V16 but not consumed yet.

### Local-Flyway state (current)

| Migration | What landed |
|---|---|
| [V16](../../../../src/main/resources/db/migration/V16__sprint1_operation_status_schema.sql) | Created `OPRT_STTS_INFO` (Sprint 1 column shape) + `EXT_TRAN_CALL_HIS` (audit log) + index `ix_oprt_stts_biz_dtm ON (BIZ_KEY, SEARCHDATETIME)`. |
| [V17](../../../../src/main/resources/db/migration/V17__sprint1_seed_data.sql) | Seeded 5 demo rows under `BIZ_KEY = VN_VNM0004_000001`: `0102345678 / CUSTOMER ABC LTD / "đang hoạt động" / 20251125…`, `0103456789 / CUSTOMER XYZ / "tạm ngừng" / 20251124…`, plus 3 active rows. |

### Phase 3 deliverables

| # | Task | Migration |
|---|---|---|
| 1 | Seed `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-operation-status'`, `DYM_AREA_ID = 'default'`. Columns: `CHECKBOX` (DISP_ORD=0, FIX_YN='Y', USE_YN='Y'), `COMPANY` (DISP_ORD=1), `ADDRESS` (DISP_ORD=2), `OPERATION_STATUS` (DISP_ORD=3), `LAST_UPDATE` (DISP_ORD=4), `REPRESENTATIVEFIRSTNAME` (DISP_ORD=5, USE_YN='N'). Use Figma WB365 widths in `SIZE`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. | New: `V{N}__addon_operation_status_screen_options.sql` (next free slot at write time) |
| 2 | **Column-for-column DEV parity audit** (run before Phase 4 closes). Query `wecmsvndev.OPRT_STTS_INFO` `information_schema.columns`. If DEV carries any column not in V16 (or differs in length / nullability), ship `V{N+1}__addon_operation_status_schema_parity.sql` with `ALTER TABLE ADD COLUMN IF NOT EXISTS` (mirrors the V23 corrective-migration pattern). | Possibly: `V{N+1}__addon_operation_status_schema_parity.sql` |
| 3 | **Demo seed coverage check** — V17 ships only 5 rows. If sparse for stakeholder demos (especially the status-filter dropdown), add `V{N+2}__addon_operation_status_demo_seed.sql` with up to 30 real DEV rows (per the [Data Migration Rule](../../../../CLAUDE.md) — copy real DEV rows, never fabricate). | Possibly: `V{N+2}__addon_operation_status_demo_seed.sql` |

**No schema changes are blocking** the Phase 2 backend work. Phase 4 work is purely additive (View Setting seed) and post-audit corrective.

---

## Key columns

The task brief uses generic column names (`COMPANY_ID`, `COMPANY_NAME`, `BUSINESS_NO`, `ADDRESS`, `OPERATION_STATUS`, `LAST_UPDATE`). The legacy table maps as follows — **legacy column names are the source of truth**:

| Brief name | Legacy column | Type (V16 / DEV target) | Nullable | Notes |
|---|---|---|---|---|
| `COMPANY_ID` | (no synthetic id) — **`(BIZ_KEY, TAXCODE)` is the natural key** | — | — | Do NOT introduce a surrogate `COMPANY_ID` column; the legacy table has no such column and the URL identifier on the proposed `PUT` endpoint is `{taxcode}`. |
| `COMPANY_NAME` | `COMPANYNAME` | VARCHAR(100) (V16) — **possible parity gap**: legacy WSVC declares 255 (per `WSVC.aosv_0001_001_r001.xml`); confirm DEV table column length in audit | nullable | Free-text from the GDT response. |
| `BUSINESS_NO` | `TAXCODE` | VARCHAR(100) NOT NULL | NOT NULL | Vietnamese MST (mã số thuế). PK part 2. Numeric in practice; column type is VARCHAR for legacy reasons (some MSTs carry hyphenated branch suffixes). |
| `ADDRESS` | `OFFICEADD` | VARCHAR(255) | nullable | Registered office address (free-text from GDT). |
| `OPERATION_STATUS` | `COMPANYSTATUS` | VARCHAR(255) | nullable | **GDT-supplied free-form Vietnamese text** — see [§ Status column definition](#status-column-definition). |
| `LAST_UPDATE` | `SEARCHDATETIME` | VARCHAR(14) NOT NULL | NOT NULL | `YYYYMMDDhhmmss` — see [§ Date / time column handling](#date--time-column-handling). |
| (additional, on legacy) | `REPRESENTATIVEFIRSTNAME` | VARCHAR(255) NOT NULL DEFAULT '' (V16) | NOT NULL | Legal representative name. Not surfaced on the static UI today; refactor wire response can opt in via the View Setting seed (`USE_YN='N'`). |
| audit | `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM` | from `BaseEntity` | mixed | TIMESTAMPTZ on local vs VARCHAR(14) on DEV — known refactor-only deviation per the BaseEntity convention. Documented in [wecms-api/CLAUDE.md](../../../../CLAUDE.md) Schema Parity Rule. |
| PK | `BIZ_KEY` | VARCHAR(20) NOT NULL (V16) — V24 BIZ_KEY-length rule | NOT NULL | PK part 1; tenant scope. |

### Verified V16 schema (canonical)

```sql
CREATE TABLE IF NOT EXISTS OPRT_STTS_INFO (
    BIZ_KEY                 VARCHAR(20)  NOT NULL,
    TAXCODE                 VARCHAR(100) NOT NULL,
    COMPANYNAME             VARCHAR(100),
    COMPANYSTATUS           VARCHAR(255),
    OFFICEADD               VARCHAR(255),
    SEARCHDATETIME          VARCHAR(14)  NOT NULL,
    REPRESENTATIVEFIRSTNAME VARCHAR(255) NOT NULL DEFAULT '',
    REG_ID                  VARCHAR(100) NOT NULL,
    REG_DTM                 VARCHAR(14)  NOT NULL,
    MOD_ID                  VARCHAR(100) NOT NULL,
    MOD_DTM                 VARCHAR(14),
    CONSTRAINT pk_oprt_stts_info PRIMARY KEY (BIZ_KEY, TAXCODE)
);

CREATE INDEX IF NOT EXISTS ix_oprt_stts_biz_dtm
    ON OPRT_STTS_INFO (BIZ_KEY, SEARCHDATETIME);
```

### Suspected parity gap

`COMPANYNAME` is `VARCHAR(100)` in V16 but the legacy WSVC declares it as length 255. Verify in the DEV audit (Phase 3 task #2). If DEV is `VARCHAR(255)`, ship the corrective `ALTER COLUMN COMPANYNAME TYPE VARCHAR(255)`.

`MOD_DTM` is local-only (V16 uses VARCHAR(14)); legacy IDO `R001` exposes it. Acceptable per the BaseEntity convention — local audit columns deliberately diverge from DEV's VARCHAR(14) in shape but are populated by the JPA `@PrePersist` / `@PreUpdate` lifecycle.

---

## Status column definition

`COMPANYSTATUS` is **NOT a controlled enum** at the database level. It is a `VARCHAR(255)` column populated with whatever string the GDT lookup endpoint returned on the last successful poll. There is **no CHECK constraint**, **no foreign key to a status code table**, and **no DB-level localisation** (the `wb_get_operat_stts(value)` PL/pgSQL function exists in DEV but is commented out in legacy itself — see [`aosv_0001_001_r001_act.jsp:48-49`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp)).

### Stored vs derived

- **Stored:** the raw Vietnamese string. The DB is the only source of truth; nothing else writes / overwrites this column except the GDT-pull bridge.
- **Derived:** the canonical 3-state code (`active_certificate_issued` / `suspended` / `not_registered`) is computed on-the-fly **server-side** by [`OperationStatusService.resolveStatusCd(...)`](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java). It is NOT persisted — adding it to the table would invite drift between storage and display logic.

### Observed values (from local seed V17 + legacy TS legend)

| Sample value | Canonical key | Frequency |
|---|---|---|
| `NNT đang hoạt động (đã được cấp GCN ĐKT)` | `active_certificate_issued` | High (most active counterparties) |
| `NNT tạm ngừng hoạt động kinh doanh` | `suspended` | Low |
| `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST` | `suspended` | Compliance-flag — legacy TS legend captures this |
| (~9 other Vietnamese strings observed in legacy TS legend) | `not_registered` (catch-all) | Long tail |
| `null` / empty | `not_registered` | Theoretical (column is nullable) |

The legacy TypeScript ([`aosv_0001_001.ts`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts)) carries 12 distinct status strings → short EN labels. The refactor's 3-state collapse loses information; preserve the raw string as a tooltip / sub-line on the frontend (Phase 4).

### Filter contract

- Wire `companyStatus` query param: **raw Vietnamese substring**, never a canonical key.
- Backend predicate: `companystatus ILIKE '%' || ? || '%'`.
- The 3-state canonical key is **frontend display only** (and `STATUS_CD` on the response).

---

## Date / time column handling

| Column | Type | Format | Source |
|---|---|---|---|
| `SEARCHDATETIME` | VARCHAR(14) NOT NULL | `YYYYMMDDhhmmss` (UTC, no separator) | Wall-clock timestamp from the last GDT poll, written by the legacy NXISAS bridge. Refactor reads verbatim; no interpretation server-side. |
| `REG_DTM` | (local: TIMESTAMPTZ via BaseEntity; DEV: VARCHAR(14)) | varies | Audit. JPA `@PrePersist` writes the local clock. |
| `MOD_DTM` | (local: TIMESTAMPTZ; DEV: VARCHAR(14)) | varies | Audit. JPA `@PreUpdate` writes the local clock. |

### Wire shape

`SEARCHDATETIME` (renamed `LAST_UPDATE` in the current refactor wire — possibly reverting in Phase 4 task #6) is returned **verbatim** as a string — backend does NOT convert to ISO-8601 / TIMESTAMPTZ. The frontend formats via [`lib/datetime.ts`](../../../../../wecms-web/lib/datetime.ts) for display. Do NOT switch wire types — column is read column-for-column from DEV per the [Schema Parity Rule](../../../../CLAUDE.md).

### Sort semantics

`ORDER BY SEARCHDATETIME DESC` is the legacy default. Because the column is a fixed-width `YYYYMMDDhhmmss` string, lexical sort = chronological sort — no `CAST` needed.

Phase 4 sort whitelist (`OperationStatusSortField`):

| Wire `FIELD_ID` | Entity property | DB column | Notes |
|---|---|---|---|
| `COMPANY` | `companyname` | `COMPANYNAME` | Locale-naive — Phase 4 may want to fold accents (Vietnamese `Đ`/`đ`); skip unless PO asks. |
| `OPERATION_STATUS` | `companystatus` | `COMPANYSTATUS` | Sorts by stored string — different logical states with the same prefix (`NNT đang …`) cluster together. Acceptable for Phase 4. |
| `LAST_UPDATE` | `searchdatetime` | `SEARCHDATETIME` | Lexical sort = chronological. |

Default ORDER BY: `SEARCHDATETIME DESC, TAXCODE ASC` (secondary key keeps pagination deterministic across rows that share a poll timestamp).

---

## Tenant filtering

`BIZ_KEY` is the **mandatory baseline filter** on every query. Sourced from `TenantContext.currentBizKey()` (populated by `JwtAuthenticationFilter` from the validated JWT). Wildcard filters rejected by `TenantProperties` regex.

### `OPRT_STTS_INFO` PK ordering

`(BIZ_KEY, TAXCODE)` — `BIZ_KEY` first means PostgreSQL's PK btree index also serves single-tenant range scans on `TAXCODE`. The companion index `ix_oprt_stts_biz_dtm` on `(BIZ_KEY, SEARCHDATETIME)` covers the default ORDER BY.

### `EXT_TRAN_CALL_HIS` deliberately not BIZ_KEY-scoped at the column level

`BIZ_KEY` is **nullable** in `EXT_TRAN_CALL_HIS` — legacy parity. Some external-call rows are tenant-agnostic (e.g. system-level health checks). Tenant filtering happens **service-side** when the audit log eventually reads via `WHERE BIZ_KEY = ? OR BIZ_KEY IS NULL` (deferred — no consumer yet).

---

## Index strategy (search + pagination)

### Today (V16)

| Index | Columns | Cardinality | Use |
|---|---|---|---|
| `pk_oprt_stts_info` | `(BIZ_KEY, TAXCODE)` | High | PK; serves point lookups + tenant range scans |
| `ix_oprt_stts_biz_dtm` | `(BIZ_KEY, SEARCHDATETIME)` | High (timestamp) | Default ORDER BY (`SEARCHDATETIME DESC` within tenant) — paginated reads |

### Phase 4 — additional indexes (only if DEV audit recommends)

| Candidate | Columns | When to add |
|---|---|---|
| `ix_oprt_stts_biz_companyname_lower` | `(BIZ_KEY, lower(COMPANYNAME) text_pattern_ops)` | Only if keyword search performance degrades on real data — `ILIKE '%X%'` cannot use a regular btree. PostgreSQL `pg_trgm` GIN index is the right tool, but it's heavyweight. Skip in Phase 4 unless EXPLAIN ANALYZE shows seq scans on >10k rows. |
| `ix_oprt_stts_biz_taxcode_lower` | `(BIZ_KEY, TAXCODE)` (already covered by PK) | PK already covers; no additional index needed. |
| `ix_oprt_stts_biz_companystatus` | `(BIZ_KEY, COMPANYSTATUS)` | Only if status-filter selectivity is low (i.e. most rows match the filter). For a 3-state collapse on a small per-tenant table, almost certainly not worth it. |

**Recommendation:** ship Phase 4 with the V16 index set. Add a `pg_trgm` index only after profiling against real DEV-size data (currently `OPRT_STTS_INFO` is small; `EXT_TRAN_CALL_HIS` is the volume table at 24K+ rows in DEV).

### Pagination cost

With ≤ 1000 rows per tenant (typical), `OFFSET / LIMIT` is fine. No keyset-pagination optimisation needed in Phase 4. If a tenant ever exceeds ~10K counterparties on this table, switch to keyset pagination on `(SEARCHDATETIME DESC, TAXCODE ASC)` — the existing index covers it.

---

## Relationships

### `OPRT_STTS_INFO` is a leaf table for the listing read path

- **No FK to a counterparty registry** at the DB level. Conceptually `(BIZ_KEY, TAXCODE)` could join to `TRCO_INFM` (counterparty master in the [PROD Active Table List](../../../../CLAUDE.md)), but legacy does not declare this FK and the IDO does not perform the join. **Refactor preserves this** — counterparty-master enrichment is a separate feature.
- **No FK to `BUSI_INFM`** at the column level. `BIZ_KEY` is the tenant scope but is not declared as an FK to `BUSI_INFM.BIZ_KEY` on this table per legacy. Other tables in the local Flyway state declare this FK; this table does not. Refactor preserves the legacy stance.

### Cross-feature read consumers

The `COMPANYSTATUS` value is consumed by **three other features** that join `OPRT_STTS_INFO` indirectly via `TRCO_TAX_ID`:

| Feature | How it consumes | Doc |
|---|---|---|
| Sales e-Invoices | Buyer cell shows the GDT status as a free-form badge / text | [`addon/sales-e-invoices-db.md`](sales-e-invoices-db.md) |
| Expenses e-Invoices | Supplier cell shows the GDT status — primary compliance signal | [`addon/expenses-e-invoices-db.md`](expenses-e-invoices-db.md) |
| (future) Bank-matching dashboard | Suspended-counterparty alerts | (separate spec, not yet authored) |

A schema or canonical-key change here regresses all three. Coordinate via the View Setting `STATUS_CD` field — Sales / Expenses Buyer cells should read `STATUS_CD` for tinting, never the raw string.

### `EXT_TRAN_CALL_HIS` deferred relationship

When the GDT bridge eventually lands, every Inquire / Update call writes one `EXT_TRAN_CALL_HIS` row per polled MST. The audit row's `TAXCODE` column links back to `OPRT_STTS_INFO` logically, but no FK is declared (legacy parity — `EXT_TRAN_CALL_HIS` is BIZ_KEY-nullable and intentionally loose).

---

## Data migration

### Local seed (today)

V17 seeds 5 rows under `BIZ_KEY = VN_VNM0004_000001`:

| TAXCODE | COMPANYNAME | COMPANYSTATUS | SEARCHDATETIME |
|---|---|---|---|
| `0102345678` | CUSTOMER ABC LTD | "NNT đang hoạt động (đã được cấp GCN ĐKT)" | `20251125094000` |
| `0103456789` | CUSTOMER XYZ | "NNT tạm ngừng hoạt động kinh doanh" | `20251124104500` |
| `0104567890` | SUPPLIER DELTA | "đang hoạt động" variant | `20251123091200` |
| `0105678901` | EXCHANGE PARTNER | "đang hoạt động" variant | `20251122152300` |
| `0106789012` | OFFICE LANDLORD | "đang hoạt động" variant | `20251121133900` |

Idempotent via `INSERT … ON CONFLICT DO NOTHING`.

### Phase 3 demo seed (if needed)

Per the [Data Migration Rule](../../../../CLAUDE.md): max 30 rows, only `BIZ_KEY = VN_VNM0004_000001`, **real DEV rows only — never fabricated**. Run the audit query against `wecmsvndev.OPRT_STTS_INFO` ordered by `SEARCHDATETIME DESC`, take the latest 30, copy verbatim. Idempotent + ordered FK-respectful (no FK chain on this table — single-table seed).

---

## Open DB questions

These are deliberately listed as DB-side questions; cross-link to the planning doc for the broader feature decisions.

| # | Question | Answer (today) | Owner |
|---|---|---|---|
| 1 | **Is status stored or derived?** | **Stored.** `COMPANYSTATUS` is a `VARCHAR(255)` column populated by the GDT-pull bridge. The canonical 3-state code (`STATUS_CD`) is **derived server-side** in `OperationStatusService.resolveStatusCd(...)` and never persisted — adding it would invite drift. Decision: keep stored as raw string, derive canonical at read time. | Sonny (resolved) |
| 2 | **Is history required?** | **Not for the listing page.** Today every Inquire / Update overwrites the row in-place; there is no history of prior `COMPANYSTATUS` values. Legacy created `EXT_TRAN_CALL_HIS` for per-call audit (created in V16; not yet read), which captures *whether the poll happened* but not *the prior status*. If product wants a "status changed from X to Y on date Z" view, that would need a new history table (`OPRT_STTS_INFO_HIS` — naming convention from sibling features) or a CDC stream. **Refactor R&D scope: deferred** — no PO ask. | PO (deferred — confirmed against project_refactoring_scope.md (memory: project_refactoring_scope)) |
| 3 | **`COMPANYNAME` length parity** | V16 declares `VARCHAR(100)`; legacy WSVC declares 255. Confirm DEV column length in the Phase 3 audit and ship corrective `ALTER COLUMN` if needed. | Arthur (Phase 3 audit) |
| 4 | **Index coverage for keyword search** | `ILIKE '%X%'` cannot use a btree index. Today's table is small enough that seq scan is acceptable. Phase 4 should profile against real DEV-size data (~hundreds to low-thousands of rows per tenant) before adding `pg_trgm`. | Arthur (post-Phase-4 profiling) |
| 5 | **`EXT_TRAN_CALL_HIS` consumer pattern** | Created in V16, not yet read/written. When the GDT bridge lands, the service writes one row per polled MST. The table is high-volume on DEV (24K+ rows audit-sampled into V29). Refactor service-side reads must always include `WHERE WEB_SVC_ID = 'addon-operation-status' AND (BIZ_KEY = ? OR BIZ_KEY IS NULL)` for tenant scope. | (deferred until GDT bridge port) |
| 6 | **Surrogate `COMPANY_ID` column** | The task brief mentions `COMPANY_ID`. **Do NOT introduce one** — the legacy table has no surrogate; the natural key `(BIZ_KEY, TAXCODE)` is sufficient and the proposed `PUT` endpoint identifier is `{taxcode}`. | (resolved) |
| 7 | **Should `STATUS_CD` be persisted as a generated column?** | Tempting (`STATUS_CD VARCHAR(50) GENERATED ALWAYS AS (...) STORED`), but PostgreSQL's `GENERATED` cannot reference a Java function and the regex logic must stay co-located with the canonical-key map in `OperationStatusService`. **Decision: do NOT add a `STATUS_CD` column.** Derive at read time. | Sonny (resolved) |
| 8 | **`REPRESENTATIVEFIRSTNAME` exposure on the wire** | Legacy WSVC includes it; refactor `OperationStatusRow` doesn't surface it. Phase 4 decides; if surfaced, register the column with `USE_YN='N'` in the View Setting seed so opt-in is seed-only. | Arthur (Phase 4) |

---

## Cross-references

- Feature spec: [../../features/addon-service/operation-status-inquiry.md](../../../features/addon-service/operation-status-inquiry.md)
- Implementation plan: ../../planning/addon/operation-status-inquiry-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- Frontend page design: [../../frontend/addon/operation-status-inquiry-page.md](../../../../../wecms-web/docs/features/addon-service/operation-status-inquiry.md)
- Backend API: [../../backend/addon/operation-status-inquiry-api.md](../../api-spec/addon/operation-status-inquiry-api.md)
- Service mapping: [../../migration/service-mapping.md](../../migration/service-mapping.md) → "Add-on Service > Operation Status Inquiry"
- Sibling DB docs (same `OPRT_STTS_INFO` consumers): [`addon/sales-e-invoices-db.md`](sales-e-invoices-db.md), [`addon/expenses-e-invoices-db.md`](expenses-e-invoices-db.md)
- Schema-parity / migration policy: [wecms-api/CLAUDE.md](../../../../CLAUDE.md) — Legacy Schema Parity Rule, DB Function Policy, PROD Active Table List, Data Migration Rule
- Legacy IDO (read): [`WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml)
- Legacy IDO (count): `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001_CNT.xml`
- Legacy IDO (write — out of scope): `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_C001.xml`, `_U001.xml`, `_D001.xml`
