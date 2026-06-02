# Purpose Settings — Database schema + Flyway plan

**Refactor slug:** `purpose-settings`
**Sibling docs:** [feature spec](../../../features/settings/purpose.md) · [JEX → Spring mapping](../../migration/purpose-settings.md) · [backend design](../../business-rules/settings/purpose-settings.md) · [frontend design](../../../../../wecms-web/docs/features/settings/purpose.md) · [task plan](../../../planning/settings/purpose-settings-task-plan.md).
**Live DB verified:** JEX DEV `wecmsvndev.busi_usag_set_infm` via the `postgres` MCP (2026-05-12). Local refactor DB target: `localhost:5434/wecms_dev` per memory `reference_local_db_setup.md`.

This document is the DDL design contract for Phase 2. **No DDL is executed in Phase 1.** Per [.claude/rules/database.md](../../../../../.claude/rules/database.md): the `postgres` MCP is read-only DEV-only — no DDL/DML, no PROD. Phase 2 lands the schema via Flyway migrations on the local refactor DB.

---

## 1. Existing JEX schema (reference only — read-only)

Source: JEX DEV via `postgres` MCP, 2026-05-12. Schema name `wecmsvndev` (production), table `busi_usag_set_infm`.

### 1.1 Columns

Verified column list (15 columns, ordinal order preserved):

| # | Column | Data type | Length | Null | Default | Notes |
|---|---|---|---|---|---|---|
| 1 | `biz_key` | `varchar` | 20 | NOT NULL | — | PK part 1 — tenant identifier |
| 2 | `use_usag_cd` | `varchar` | 20 | NOT NULL | — | PK part 2 — system purpose code (10-char zero-padded in practice; column is 20 to accommodate any legacy bucket prefix) |
| 3 | `use_yn` | `varchar` | 1 | NOT NULL | `'Y'` | Active flag |
| 4 | `disp_yn` | `varchar` | 1 | NOT NULL | `'Y'` | Display flag (controls payment-dropdown visibility) |
| 5 | `disp_ord` | `numeric` | — | NULL | — | Per-tenant sort order |
| 6 | `mod_id` | `varchar` | 100 | NULL | — | Last editor login id |
| 7 | `mod_dtm` | `varchar` | 14 | NULL | — | `yyyyMMddHHmmss` UTC |
| 8 | `usag_grp_cd` | `varchar` | 3 | NULL | — | Legacy bucket selector (always NULL in refactor scope) |
| 9 | `rept_dv` | `varchar` | 2 | NULL | — | Legacy report division (unused by refactor) |
| 10 | `rept_grp` | `varchar` | 2 | NULL | — | Legacy report group (unused) |
| 11 | `usag_type` | `character` (CHAR) | 2 | NULL | — | Legacy usage type (unused) |
| 12 | `hand_use_usag_cd` | `varchar` | 20 | NULL | — | **User-visible code** — required at the application layer for refactor rows |
| 13 | `parent_use_usag_cd` | `varchar` | 20 | NULL | — | Self-reference to a level-1 purpose's `use_usag_cd` |
| 14 | `use_usag_nm` | `varchar` | 255 | NULL | — | **Display name** — required at the application layer for refactor rows |
| 15 | `use_usag_cont` | `varchar` | 500 | NULL | — | Description (nullable) |

### 1.2 Constraints + indexes

```
PK: busi_usag_set_infm_pk PRIMARY KEY (biz_key, use_usag_cd)
INDEX: busi_usag_set_infm_pk (BTREE on biz_key, use_usag_cd) — same as PK
```

No FK, no CHECK, no UNIQUE beyond the PK. No additional indexes on JEX DEV.

### 1.3 Row volume

DEV row count (estimated via `pg_class.reltuples`): **~116,170** rows across all tenants. For tenant `VN_VNM0004_000001`:
- Active rows with `usag_grp_cd IS NULL AND use_yn = 'Y'` (refactor scope): **5** rows on DEV today (small set — see [feature spec §1.4](../../../features/settings/purpose.md) for the sample).
- The bulk of the 116k rows are legacy `usag_grp_cd IS NOT NULL` rows (the old per-group hierarchy) — out of refactor scope.

### 1.4 Sample data (refactor scope: `usag_grp_cd IS NULL AND use_yn = 'Y'`)

```
biz_key            | use_usag_cd | hand_use_usag_cd | parent_use_usag_cd | use_usag_nm | use_usag_cont    | disp_yn | disp_ord
-------------------+-------------+------------------+--------------------+-------------+------------------+---------+----------
VN_VNM0004_000001  | 0000000001  | 9999             | NULL               | VIP         | VIP payment      | N       | 186
VN_VNM0004_000001  | 0000000005  | 9991             | 0000000001         | VIP2        | VIP payment2     | Y       | 190
VN_VNM0004_000001  | 0000000006  | 8888             | NULL               | VIP8        | VIP payment8     | Y       | 191
VN_VNM0004_000001  | 0000000007  | P01              | NULL               | Deposit     | Deposit purpose  | Y       | 192
VN_VNM0004_000001  | 0000000008  | P1.1             | 0000000007         | Sale        | Sale business    | Y       | 193
```

Two parent-child pairs visible: `0000000001 → 0000000005` and `0000000007 → 0000000008`. Confirms the two-level hierarchy in production data. `use_usag_cd` is zero-padded to 10 digits.

---

## 2. Target schema (local refactor DB)

The refactor's local DB at `localhost:5434/wecms_dev` (per `reference_local_db_setup.md`) inherits the JEX schema column-for-column per the Legacy Schema Parity Rule ([wecms-api/CLAUDE.md](../../../../CLAUDE.md)). The Flyway migration V80 below reproduces the DEV schema verbatim.

### 2.1 `BUSI_USAG_SET_INFM` — local schema

Identical to DEV §1.1. Column names UPPER_SNAKE per the legacy-schema-parity rule (Postgres folds to lowercase by default, but the entity `@Column(name = "UPPER_SNAKE")` annotations match the legacy convention).

### 2.2 `V_PURPOSE_IN_USE` — new read-only view

New view that the backend `LEFT JOIN`s against to derive the `used_in_transactions` wire field in one query (see [backend §3.1](../../business-rules/settings/purpose-settings.md)) AND to drive the recursive delete pre-check (OQ-5 decided 2026-05-12 — block delete when the deleted row OR any of its active children is referenced).

```sql
CREATE OR REPLACE VIEW v_purpose_in_use AS
SELECT DISTINCT biz_key, use_usag_cd
FROM (
  -- (a) An active level-1 purpose has at least one active child referencing it.
  --     Lands in V81 — covers the legacy `hasActiveChildren` semantics + the
  --     "parent is in use because its child is in use" branch of OQ-5.
  SELECT biz_key, parent_use_usag_cd AS use_usag_cd
  FROM busi_usag_set_infm
  WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y'

  -- (b) An active banking-transaction-history / payment / transfer row
  --     references the purpose. UNION branches added per consumer table as
  --     the consumer entities land in the refactor. Each branch follows the
  --     shape:
  --
  --     UNION ALL
  --     SELECT biz_key, use_usag_cd FROM <consumer_table>
  --     WHERE use_usag_cd IS NOT NULL
  --       AND <consumer_table active filter — e.g. status NOT IN ('CANCELLED','DELETED')>
  --
  --     Candidate consumer tables (Phase 2 owner enumerates exhaustively):
  --     - PYMN_INFM / PYMN_DTL                — Vendor / Expense / Payroll payment masters + details
  --     - TRNS_HSTR_INFM                      — Banking transaction history (the literal
  --                                             "banking transaction history" reference in
  --                                             Sonny's Figma comment)
  --     - SCDUL_TRNF_INFM / SCDUL_TRNF_DTL    — Internal Transfer schedule
  --     - SCDUL_PYMN_INFM / SCDUL_PYMN_DTL    — Vendor / Expense / Payroll scheduled payments
  --     - OVRS_PYMN_INFM                      — Overseas payment requests
  --     - EXPS_INFM                           — Expense ledger
  --
  --     Exact column name in each consumer table TBD — JEX uses `use_usag_cd`
  --     verbatim everywhere, so the column name is presumed stable. Phase 2
  --     owner verifies each consumer's column name + active-row filter
  --     before extending the view.
) t;
```

The view is a **read-only projection** — refactor never `INSERT`s into it. Flyway-managed via V81 for branch (a) and a successor migration (V?? — picked at the time the first consumer table lands in the refactor) for each branch (b) addition.

**Delete pre-check semantics — recursive (OQ-5 + OQ-8):** the service-layer `delete(...)` call (see [backend §3.1](../../business-rules/settings/purpose-settings.md)) looks up `(biz_key, useUsagCd)` PLUS `(biz_key, every active child's useUsagCd)` in this view. If ANY of those codes appears in the view's branch-(b) rows, the delete fails with 409 + `WBE1199` (per OQ-8 — same wire code as the legacy "has active children" case; one localized message covers both branches). The service runs branch-(a) check first via `repo.hasActiveChildren(...)`, then the recursive branch-(b) check via `v_purpose_in_use`. Both throw the same `BusinessException("WBE1199")`.

OQ-DB-1: should `v_purpose_in_use` be a `MATERIALIZED VIEW` for performance? Decision: **no** — small data, the view runs once per list call, the DISTINCT cost is negligible. Re-evaluate when row counts exceed 10k per tenant OR when branch (b) adds joins against high-cardinality consumer tables.

### 2.3 Demo seed (Phase 3)

A small idempotent seed for tenant `VN_VNM0004_000001` so the local panel renders against real data instead of `mockPurposeTree`. Landing as Flyway V82 once the Phase 2 entity + repo are in place.

```sql
-- V82: idempotent — only seeds when the tenant has zero refactor-scope rows.
INSERT INTO busi_usag_set_infm (biz_key, use_usag_cd, hand_use_usag_cd, use_usag_nm,
                                 use_usag_cont, parent_use_usag_cd, use_yn, disp_yn,
                                 disp_ord, mod_id, mod_dtm)
SELECT 'VN_VNM0004_000001', '0000000001', 'BD01', 'Business Development', 'Top-level marker', NULL,
       'Y', 'Y', 1, 'system@wecms.vn', '20260501000000'
WHERE NOT EXISTS (
  SELECT 1 FROM busi_usag_set_infm
  WHERE biz_key = 'VN_VNM0004_000001'
    AND usag_grp_cd IS NULL AND use_yn = 'Y'
);
-- … plus 3-4 more rows, level-2 children under BD01, mixed disp_yn / parent.
```

The seed is Phase 3 — Phase 2 ships V80 + V81 only.

---

## 3. Flyway migration plan

Next free slot is **V80** (last known: V79 — `vas_account_view_setting_seed.sql`).

### V80a — `wb_get_next_val_function.sql` (documented exception — see below)

Install the legacy `wb_get_next_val(...)` PL/pgSQL function on the local refactor DB. Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) the refactor's policy is "no legacy PL/pgSQL helpers" — **this migration is a documented exception** authorized by Sonny (OQ-3 decision, 2026-05-12). The function is needed by the Purpose Settings create path to generate the 10-digit zero-padded `use_usag_cd` codes in the `0000000001..0999999999` range (avoiding the legacy group-prefixed ranges `1xxx / 3xxx / 4xxx / 6xxx / 91xx`).

```sql
-- V80a: ported legacy DB function wb_get_next_val(...) — full body copied
-- verbatim from JEX DEV (wecmsvndev schema). Documented exception to the
-- refactor's no-legacy-PL/pgSQL-helpers policy — approved by Sonny per
-- OQ-3 decision (2026-05-12).
--
-- Extraction source: `pg_get_functiondef(...)` against DEV, 2026-05-12.
-- Phase 2 owner extracts the full body (74k chars) via the postgres MCP
-- and pastes it verbatim into this migration; do NOT hand-edit branches.
--
-- The function carries logic for many tables — refactor only consumes
-- the `busi_usag_set_infm.use_usag_cd` branch today. Keep the full
-- function body for fidelity; do not prune branches for unrelated tables.
--
-- Signature (verified via postgres MCP):
--   wb_get_next_val(
--       tablename varchar, fieldname varchar,
--       param1 varchar DEFAULT NULL,  -- biz_key for busi_usag_set_infm
--       param2 varchar DEFAULT NULL,  -- usag_grp_cd (empty '' = refactor scope)
--       param3..6 varchar DEFAULT NULL
--   ) RETURNS text

CREATE OR REPLACE FUNCTION wb_get_next_val(
    tablename varchar, fieldname varchar,
    param1 varchar DEFAULT NULL, param2 varchar DEFAULT NULL,
    param3 varchar DEFAULT NULL, param4 varchar DEFAULT NULL,
    param5 varchar DEFAULT NULL, param6 varchar DEFAULT NULL
) RETURNS text LANGUAGE plpgsql AS $$
DECLARE
    newSeqNo varchar;
BEGIN
    -- Phase 2: replace this body with the full DEV extraction (CASE chain
    -- across every table the function knows about). The Purpose-relevant
    -- branch confirmed against DEV (2026-05-12) is:
    --
    --   when lower($1) = 'busi_usag_set_infm' and lower($2) = 'use_usag_cd'
    --        and coalesce(trim($3), '') <> ''
    --        and coalesce(trim($4), '') = '' then
    --       newSeqNo := (
    --           select coalesce(lpad((max(use_usag_cd)::numeric + 1)::varchar, 10, '0'),
    --                           '0000000001')
    --           from busi_usag_set_infm
    --           where biz_key = $3
    --             and use_usag_cd ~ '^0[0-9]{9}$'
    --       );
    --
    -- Phase 2 owner pastes the full extracted body (single CASE expression
    -- with one WHEN clause per supported (table, field) pair) here.

    RETURN newSeqNo;
END
$$;
```

**Concurrency note:** the function carries no advisory lock / `FOR UPDATE`. Refactor service wraps the call + the immediate `INSERT` in one transaction; on PK-violation `DataIntegrityViolationException` (the rare concurrent-create race), the service retries once. Documented in [backend §8](../../business-rules/settings/purpose-settings.md).

**Schema-name caveat:** the DEV function lives in schema `wecmsvndev`; the local refactor DB uses the default `public` schema. Phase 2 owner verifies the function is created under `public` (or whatever schema the local refactor DB pins as default) and that the JPA repository's native query (`SELECT wb_get_next_val(...)`) resolves to it without an explicit schema prefix.

### V80 — `busi_usag_set_infm_schema.sql`

```sql
-- V80: BUSI_USAG_SET_INFM — refactor scope for Settings > Purpose.
--
-- Mirrors JEX DEV schema exactly (verified via `postgres` MCP, 2026-05-12).
-- Local-only R&D — no DEV/UAT/PROD impact. Refactor scope is rows where
-- USAG_GRP_CD IS NULL (the level-2 hierarchy bucket, per the legacy
-- comm_0187_001_r001_act.jsp:50 comment).

CREATE TABLE IF NOT EXISTS busi_usag_set_infm (
    biz_key             VARCHAR(20)  NOT NULL,
    use_usag_cd         VARCHAR(20)  NOT NULL,
    use_yn              VARCHAR(1)   NOT NULL DEFAULT 'Y',
    disp_yn             VARCHAR(1)   NOT NULL DEFAULT 'Y',
    disp_ord            NUMERIC,
    mod_id              VARCHAR(100),
    mod_dtm             VARCHAR(14),
    usag_grp_cd         VARCHAR(3),
    rept_dv             VARCHAR(2),
    rept_grp            VARCHAR(2),
    usag_type           CHAR(2),
    hand_use_usag_cd    VARCHAR(20),
    parent_use_usag_cd  VARCHAR(20),
    use_usag_nm         VARCHAR(255),
    use_usag_cont       VARCHAR(500),
    CONSTRAINT busi_usag_set_infm_pk PRIMARY KEY (biz_key, use_usag_cd)
);
```

### V81 — `busi_usag_set_infm_purpose_in_use_view.sql`

```sql
-- V81: V_PURPOSE_IN_USE — derives the "used_in_transactions" flag for Purpose
-- Settings list response. Backend LEFT JOINs against this view in one query.
--
-- Initial version covers branch (a) only — active parent referenced by an
-- active child. Branch (b) — payment / transfer / expense references —
-- lands in a follow-up migration when the consumer entities arrive
-- (feature spec OQ-5).

CREATE OR REPLACE VIEW v_purpose_in_use AS
SELECT DISTINCT biz_key, use_usag_cd
FROM (
  SELECT biz_key, parent_use_usag_cd AS use_usag_cd
  FROM busi_usag_set_infm
  WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y'
) t;
```

### V82 — `busi_usag_set_infm_demo_seed.sql` (Phase 3)

Idempotent INSERT block seeding ~6 purposes for `VN_VNM0004_000001` (2 level-1 + 4 level-2 children) so the modal renders against live data. Lands with Phase 3 frontend wiring.

### Indexing (deferred — Phase 2.5)

The DEV schema has no secondary indexes; the refactor follows suit at landing time. Add these only if profiling shows a hot scan:

| Candidate index | Use case |
|---|---|
| `(biz_key, use_yn, usag_grp_cd, disp_ord)` partial WHERE `usag_grp_cd IS NULL` | List query — main hot path |
| `(biz_key, hand_use_usag_cd) WHERE usag_grp_cd IS NULL AND use_yn = 'Y'` UNIQUE | OQ-7 hardening — DB-level dup guard |
| `(biz_key, use_usag_nm) WHERE usag_grp_cd IS NULL AND use_yn = 'Y'` UNIQUE | OQ-7 hardening — DB-level dup guard |
| `(biz_key, parent_use_usag_cd) WHERE use_yn = 'Y'` | `hasActiveChildren` query + view branch (a) |

Phase 2 ships none of these by default. Phase 2.5 owner profiles the list endpoint with the seed + a synthetic 1k-row test set and adds the indexes if the query plan calls for them.

---

## 4. Constraints

### 4.1 PK

`PRIMARY KEY (biz_key, use_usag_cd)` — already present in V80.

### 4.2 NOT NULL

Matches DEV:
- `biz_key`, `use_usag_cd`, `use_yn`, `disp_yn` — all NOT NULL with defaults where applicable.
- All other columns nullable (matches DEV).

The application layer enforces "required" semantics for `hand_use_usag_cd` and `use_usag_nm` even though the columns are nullable in the DB — this preserves the legacy schema and matches the legacy JSP validation flow ([comm_0187_001_c001_act.jsp:32-33](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_c001_act.jsp)). Tightening these to NOT NULL is out of scope — would break the legacy data import path.

### 4.3 CHECK constraints

None on DEV. The refactor does NOT add any — service-layer Bean Validation covers the same ground (length, enum-vocabulary for `use_yn` / `disp_yn`, parent-active validation).

### 4.4 FK constraints

None on DEV. The refactor does NOT add `parent_use_usag_cd` FK to `(biz_key, use_usag_cd)` because:
- The legacy data set has rows with `parent_use_usag_cd` referencing non-existent / soft-deleted parents (verified via the DEV row sample); adding a FK would fail on import.
- Service-layer validation (`PurposeSettingsService.validateParent(...)`) covers the same ground.

OQ-DB-2: revisit after seed data lands — if the refactor's local DB only carries clean refactor-written rows, a deferred-validation FK (`INITIALLY DEFERRED`) could land in Phase 2.5.

### 4.5 UNIQUE

None beyond the PK. Service-layer enforces `(biz_key, hand_use_usag_cd, USAG_GRP_CD IS NULL, use_yn='Y')` uniqueness on create and `(biz_key, use_usag_nm, USAG_GRP_CD IS NULL, use_yn='Y')` uniqueness on create + update.

Optional Phase 2.5 hardening — partial unique index (OQ-7 in the feature spec):

```sql
-- Optional V?? — DB-level race-condition guard
CREATE UNIQUE INDEX busi_usag_set_infm_hand_uq
    ON busi_usag_set_infm (biz_key, hand_use_usag_cd)
    WHERE usag_grp_cd IS NULL AND use_yn = 'Y';

CREATE UNIQUE INDEX busi_usag_set_infm_name_uq
    ON busi_usag_set_infm (biz_key, use_usag_nm)
    WHERE usag_grp_cd IS NULL AND use_yn = 'Y';
```

Off by default. Add only if a concurrent-create race ever materializes in testing.

---

## 5. Audit log column types

`AUDIT_LOGS_INFM` (verified via `postgres` MCP, 2026-05-12) — read-only reference; the refactor reuses the existing shared `AuditLogService`:

```
biz_key             varchar(20)
audit_logs_seq_no   numeric
wsvc_id             varchar(20)
lang_cd             varchar(3)
ref_no              varchar(50)
ref_no1             varchar(50)
ref_no2             varchar(50)
ref_no3             varchar(50)
ref_no4             varchar(50)
ref_no5             varchar(50)
audit_logs_actv_dtl text
reg_id              varchar(100)
reg_nm              varchar(100)
reg_dtm             varchar(14)
audit_logs_actv     varchar(50)
```

Per the 2026-05-09 cutover, `WSVC_ID` for Purpose Settings rows is `'purpose-settings'` (≤ 20 chars; fits). `REF_NO` is `'BUSI_USAG_SET_INFM'` (20 chars; fits). `REF_NO1` is `use_usag_cd` (10 chars; fits). `REF_NO2` is `hand_use_usag_cd` (≤ 20 chars; fits). No schema change needed.

If the audit table needs new columns (it doesn't for this feature), it would land via a separate migration — `firm-banking-settings`' V63 covers the existing schema baseline.

---

## 6. Row volume estimates

| Tenant scope | Estimated active rows (refactor scope) | Notes |
|---|---|---|
| `VN_VNM0004_000001` (DEV reference tenant) | 5 today; grows to 20-50 once Phase 3 seed lands | Tiny |
| Single tenant in production | 50-200 typical | Per `dictionary_ko.properties` examples |
| Max realistic | 1,000 | Outlier — large multi-division corporate |

Performance is not a concern. The list endpoint returns the full list (no pagination per [backend OQ-Backend-4](../../business-rules/settings/purpose-settings.md)). The `v_purpose_in_use` JOIN runs against a tiny denormalized projection.

---

## 7. Migration ordering + dependencies

```
V80 (busi_usag_set_infm_schema)
  └─ requires: nothing (independent baseline)
V81 (busi_usag_set_infm_purpose_in_use_view)
  └─ requires: V80
V82 (busi_usag_set_infm_demo_seed) — Phase 3
  └─ requires: V80, V81; idempotent (NOT EXISTS guard)
```

Phase 2.5 hardening (partial unique indexes, optional consumer FK) lands as separate V?? migrations once profiling justifies them.

---

## 8. Verification queries (read-only — runnable via `postgres` MCP after migration)

After V80 applies on the local refactor DB:

```sql
-- 1. Confirm columns + nullability
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns
WHERE lower(table_name) = 'busi_usag_set_infm'
ORDER BY ordinal_position;

-- 2. Confirm PK + indexes
SELECT indexname, indexdef FROM pg_indexes WHERE lower(tablename) = 'busi_usag_set_infm';

-- 3. Confirm constraints
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid = 'busi_usag_set_infm'::regclass;
```

After V81:

```sql
-- 4. Confirm view exists + returns expected rows
SELECT table_name FROM information_schema.views
WHERE lower(table_name) = 'v_purpose_in_use';
SELECT * FROM v_purpose_in_use LIMIT 10;
```

After V82 (Phase 3):

```sql
-- 5. Confirm demo seed for refactor scope
SELECT biz_key, use_usag_cd, hand_use_usag_cd, use_usag_nm, parent_use_usag_cd, disp_yn, disp_ord
FROM busi_usag_set_infm
WHERE biz_key = 'VN_VNM0004_000001'
  AND usag_grp_cd IS NULL AND use_yn = 'Y'
ORDER BY disp_ord;
```

All read-only — runnable from Claude Code via the `postgres` MCP without violating [.claude/rules/database.md](../../../../../.claude/rules/database.md).

---

## 9. Open questions (DB-specific)

### OQ-DB-1 — Materialized view for `v_purpose_in_use`?

Default: regular `VIEW`, not `MATERIALIZED VIEW`. Re-evaluate if row counts grow > 10k per tenant.

### OQ-DB-2 — `parent_use_usag_cd` FK (deferred-validation)

Default: no FK; service-layer validation suffices. Revisit in Phase 2.5 if data quality issues surface.

### OQ-DB-3 — Partial unique indexes for `hand_use_usag_cd` / `use_usag_nm`

Default: off. Add only if a race-condition duplicate materializes in testing. See OQ-7 in the feature spec.

### OQ-DB-4 — `use_usag_cd` generation strategy — **DECIDED 2026-05-12**

**Resolution:** Port the legacy `wb_get_next_val(...)` PL/pgSQL function — documented exception to the no-legacy-helpers policy. Refactor service calls `wb_get_next_val('busi_usag_set_infm', 'use_usag_cd', biz_key, '')` for PK generation. See V80a above for the migration plan, [feature spec §11 OQ-3](../../../features/settings/purpose.md) for the function body and concurrency-retry guard, and [backend §3.1](../../business-rules/settings/purpose-settings.md) for the call site.

Open follow-up (Phase 2): verify whether the function carries a `disp_ord` branch for `usag_grp_cd = ''`. If yes, swap the in-service `findMaxDispOrd(...)+1` for a sibling `wb_get_next_val('busi_usag_set_infm', 'disp_ord', biz_key, '')` call. If no, in-service computation stays — same semantics, no DB-function surface.

### OQ-DB-5 — `numeric` for `disp_ord`

DEV stores `disp_ord` as `numeric` (variable-precision). The refactor entity maps it to `Integer` (JPA). Verify Hibernate's `numeric → Integer` mapping works for the small positive integers we store, and bump the schema to `INTEGER` only if Hibernate complains. **Phase 2 verifies during repository tests.**

---

## 10. References

- [.claude/rules/database.md](../../../../../.claude/rules/database.md) — DB access rules (read-only MCP).
- [wecms-api/CLAUDE.md](../../../../CLAUDE.md) — Legacy Schema Parity Rule, Flyway slot rule, Data Migration Rule.
- [docs/refactor-wecms/database/settings/firm-banking-settings-schema.md](firm-banking-settings-schema.md) — closest schema precedent (Settings panel + audit-log table integration).
- [docs/refactor-wecms/database/manual-tenant-setup.md](../manual-tenant-setup.md) — local DB setup (Phase 2 owner ensures the local DB exists before applying V80).
- Memory `reference_local_db_setup.md` — `localhost:5434/wecms_dev`, user `postgres`.
