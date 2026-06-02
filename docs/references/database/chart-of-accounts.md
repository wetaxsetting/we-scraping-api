# Chart of Accounts — Database Design

**Slug:** `chart-of-accounts`
**Tenant:** all tables tenant-scoped via `BIZ_KEY`.
**Related docs:** [feature spec](../../features/settings/chart-of-accounts.md) · [migration map](../migration/chart-of-accounts.md) · [backend](../business-rules/chart-of-accounts.md) · [frontend](../../../../wecms-web/docs/features/settings/chart-of-accounts.md) · [plan](../../planning/phase-1/chart-of-accounts-plan.md).

The refactor mirrors the JEX DEV schema column-for-column per the [Legacy Schema Parity Rule](../../../CLAUDE.md). DEV introspection was captured on 2026-05-13; this doc is the canonical reference for the Phase-1 Flyway migration.

---

## 1. Tables involved

Only one table is mutated by this feature. Downstream consumer tables (`ACCT_TRNS_HSTR`, `CARD_TRNS_HSTR`, financial-report views) reference `CHART_ACCT_INFM` by code but are out of scope for the Phase-1 port — they land with their owning module migrations.

### 1.1 `CHART_ACCT_INFM`

DEV definition (introspected from `wecmsvndev` 2026-05-13):

| # | Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | NO | — | Tenant FK to `BUSI_INFM.BIZ_KEY` (FK not declared in DEV — refactor follows DEV verbatim per parity rule; tenant isolation enforced in service via `TenantContext.currentBizKey()`). |
| 2 | `CHART_ACCT_SEQ_NO` | VARCHAR(14) | NO | — | Surrogate PK part. Per-tenant integer allocated by `wb_get_next_val('chart_acct_infm','chart_acct_seq_no', BIZ_KEY)`. Stored as VARCHAR(14); refactor entity maps to `Long`. |
| 3 | `CHART_ACCT_CD` | VARCHAR(20) | NO | — | User-supplied code. Trimmed, NBSP-sanitised. Subject to UK with `BIZ_KEY` + `DEL_YN`. |
| 4 | `CHART_ACCT_NM_EN` | VARCHAR(255) | NO | — | English name. Trimmed. |
| 5 | `CHART_ACCT_NM_VN` | VARCHAR(255) | YES | — | Vietnamese name. |
| 6 | `CHART_ACCT_NM_KO` | VARCHAR(255) | YES | — | Korean name. |
| 7 | `CHART_ACCT_TYPE` | VARCHAR(100) | YES | — | Free-form type (Asset / Liability / Revenue / Expense / Equity in concept). **Unused in DEV — all NULL**; kept on the schema for forward compat. |
| 8 | `PARENT_CHART_ACCT_CD` | VARCHAR(20) | YES | — | Parent's `CHART_ACCT_CD` within the same tenant. NULL ⇒ root. Not declared as FK in DEV. |
| 9 | `STTS` | CHAR(1) | NO | — | `A` Active / `I` Inactive. |
| 10 | `DEL_YN` | CHAR(1) | NO | `'N'::bpchar` | Soft-delete flag. |
| 11 | `REG_ID` | VARCHAR(100) | NO | — | Creator user id. |
| 12 | `REG_DTM` | VARCHAR(14) | NO | — | Creation timestamp `yyyyMMddHHmmss` (UTC). |
| 13 | `MOD_ID` | VARCHAR(100) | NO | — | Last modifier user id. |
| 14 | `MOD_DTM` | VARCHAR(14) | NO | — | Last modification timestamp `yyyyMMddHHmmss` (UTC). |
| 15 | `CAL_RULE` | CHAR(1) | YES | — | `P` (+) / `M` (-) sign-aggregation rule. |

**Audit-column shape note** (`REG_DTM` / `MOD_DTM` as VARCHAR(14)): DEV uses the legacy yyyyMMddHHmmss VARCHAR convention. The refactor entity extends `BaseEntity` which exposes `OffsetDateTime` accessors; a per-field JPA `@Convert` translates at the column boundary. This matches the established Sprint 1 pattern — do NOT migrate the local column to `TIMESTAMPTZ`, since the legacy `wabooks/commons/DateTimeUtils.WB_FORMATTER_DTM14` round-trips this exact shape and parity is non-negotiable.

### 1.2 Constraints

| Constraint | Definition | Purpose |
|---|---|---|
| `pk_chart_acct_infm` | `PRIMARY KEY (biz_key, chart_acct_seq_no)` | Composite PK. |
| `uk_chart_acct_cd` | `UNIQUE (biz_key, chart_acct_cd, del_yn)` | Per-tenant code uniqueness **including the soft-delete flag** — one active row + one soft-deleted row per code is the legal state. Drives the revive-on-create and rename-on-delete behaviour (see [feature spec §4.3](../../features/settings/chart-of-accounts.md#43-code-uniqueness--soft-delete-interplay)). |

**No CHECK constraints** declared in DEV. Refactor mirrors — STTS / DEL_YN / CAL_RULE value validation lives in the JPA enums + bean validation, not the DB. This matches existing Settings precedent (Contacts, Approval Line).

**No FOREIGN KEY constraints** declared in DEV — neither to `BUSI_INFM.BIZ_KEY` nor self-referential to `CHART_ACCT_INFM.CHART_ACCT_CD`. Refactor mirrors per the parity rule. Tenant isolation runs in the service layer; parent-existence validation runs in `ChartOfAccountsValidator`.

### 1.3 Indexes

| Index | Definition | Reason |
|---|---|---|
| `pk_chart_acct_infm` | `btree (biz_key, chart_acct_seq_no)` | PK btree. |
| `uk_chart_acct_cd` | `btree (biz_key, chart_acct_cd, del_yn)` | UK btree — also accelerates the dup-check `WHERE biz_key = ? AND chart_acct_cd = ? AND del_yn = ?`. |

**No additional indexes in DEV.** Refactor adds two helper indexes beyond DEV (allowed per [Legacy Schema Parity Rule "Indexes beyond PK/UK"](../../../CLAUDE.md)):

| Local-only helper index | Reason |
|---|---|
| `ix_chart_acct_infm_biz_parent` btree `(biz_key, parent_chart_acct_cd) WHERE del_yn = 'N'` | Powers the child-lookup query in `delete` (re-parent step) and the `has_children` group-count query in `list`. |
| `ix_chart_acct_infm_biz_moddtm` btree `(biz_key, mod_dtm DESC)` | Default secondary-sort path; matches the existing precedent on `appr_line_infm` (V90). |

---

## 2. Refactor Flyway migrations

Numbering continues the existing sequence — last applied is **V92** (per [wecms-api/CLAUDE.md](../../../CLAUDE.md) "Database (LOCAL)" table). The Chart of Accounts schema lands in **V93** + **V94** + **V95** + **V96** (numbering will shift if other migrations land first — confirm at MR-open time).

| Version | Title | Summary |
|---|---|---|
| **V93** | Settings > Chart of Accounts schema | `CREATE TABLE IF NOT EXISTS CHART_ACCT_INFM` column-for-column with DEV (15 columns, PK on `(BIZ_KEY, CHART_ACCT_SEQ_NO)`, UK on `(BIZ_KEY, CHART_ACCT_CD, DEL_YN)`, DEFAULT `'N'` on `DEL_YN`). Adds two helper btree indexes (`ix_chart_acct_infm_biz_parent` partial WHERE `del_yn='N'`, `ix_chart_acct_infm_biz_moddtm`). Idempotent. Column count parity with DEV recorded in the migration header (15). |
| **V94** | Settings > Chart of Accounts: `wb_get_next_val` extension (per-tenant MAX+1) | Adds the `chart_acct_infm.chart_acct_seq_no` branch ported VERBATIM from DEV. Body is the per-tenant MAX+1 pattern, same shape as V91 / `appr_line_no`: `SELECT COALESCE(MAX(chart_acct_seq_no::numeric), 0) + 1 FROM chart_acct_infm WHERE biz_key = $3`. Branch count: 15 → **16**. Documented in [wecms-api/CLAUDE.md "Surviving legacy functions"](../../../CLAUDE.md) on landing with the retirement plan. Consumed by `LegacyFunctionRepository.nextChartAcctSeqNo(bizKey)`. |
| **V95** | Settings > Chart of Accounts: restricted-roots seed (per tenant) | Idempotent seed of `ACC-01` (REVENUE), `ACC-02` (EXPENSES), `ACC-03` (INVESTMENT) for every tenant present in `BUSI_INFM` — `INSERT … ON CONFLICT DO NOTHING` keyed on `(biz_key, chart_acct_cd, del_yn)`. `STTS='A'`, `DEL_YN='N'`, `PARENT_CHART_ACCT_CD=NULL`, `REG_ID = biz_key`, `REG_DTM = '20260513000000'` (deterministic seed timestamp). Allocates `CHART_ACCT_SEQ_NO` via `wb_get_next_val('chart_acct_infm','chart_acct_seq_no', biz_key)` so values stay tenant-disjoint. Resolves OQ-4. |
| **V96** | Settings > Chart of Accounts: demo seed for `VN_VNM0004_000001` | 26 non-root rows pulled verbatim from real JEX DEV (top of `CHART_ACCT_INFM` for the tenant). Preserves DEV `CHART_ACCT_SEQ_NO` (70..112 range) so any frontend-cached references stay consistent. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. Excludes V95-seeded restricted roots (already present). |
| **V97** (optional, Phase 2) | Convert `CHART_ACCT_SEQ_NO` from VARCHAR(14) to BIGINT | Same precedent as V92 (`appr_line_no` NUMERIC → BIGINT). Refactor entity types `chartAcctSeqNo` as `Long`; Hibernate `validate` rejects VARCHAR for a `Long` field. The Phase-1 entity carries `@Convert(converter = StringToLongConverter.class)` to absorb the type mismatch; Phase 2 simplifies by aligning the DB column. Cast safely via `USING chart_acct_seq_no::bigint`. **Deferred until after Phase 1 ships and is stable** — schema-parity drift is documented in the entity Javadoc + V97 header (same convention as V35→V36→V40 SIZE column saga). |

### 2.1 V93 — `CHART_ACCT_INFM` schema (full)

```sql
-- V93__settings_chart_of_accounts_schema.sql
-- Column count parity with DEV: 15
-- DEV introspection: 2026-05-13 (postgres_dev MCP)
-- Refactor naming: kebab-case slug `chart-of-accounts` — no `comm_0223_001` anywhere.

CREATE TABLE IF NOT EXISTS CHART_ACCT_INFM (
    BIZ_KEY              VARCHAR(20)  NOT NULL,
    CHART_ACCT_SEQ_NO    VARCHAR(14)  NOT NULL,
    CHART_ACCT_CD        VARCHAR(20)  NOT NULL,
    CHART_ACCT_NM_EN     VARCHAR(255) NOT NULL,
    CHART_ACCT_NM_VN     VARCHAR(255),
    CHART_ACCT_NM_KO     VARCHAR(255),
    CHART_ACCT_TYPE      VARCHAR(100),
    PARENT_CHART_ACCT_CD VARCHAR(20),
    STTS                 CHAR(1)      NOT NULL,
    DEL_YN               CHAR(1)      NOT NULL DEFAULT 'N',
    REG_ID               VARCHAR(100) NOT NULL,
    REG_DTM              VARCHAR(14)  NOT NULL,
    MOD_ID               VARCHAR(100) NOT NULL,
    MOD_DTM              VARCHAR(14)  NOT NULL,
    CAL_RULE             CHAR(1),
    CONSTRAINT pk_chart_acct_infm PRIMARY KEY (BIZ_KEY, CHART_ACCT_SEQ_NO),
    CONSTRAINT uk_chart_acct_cd   UNIQUE      (BIZ_KEY, CHART_ACCT_CD, DEL_YN)
);

-- Helper indexes (beyond DEV — allowed per parity rule)
CREATE INDEX IF NOT EXISTS ix_chart_acct_infm_biz_parent
    ON CHART_ACCT_INFM (BIZ_KEY, PARENT_CHART_ACCT_CD)
    WHERE DEL_YN = 'N';
CREATE INDEX IF NOT EXISTS ix_chart_acct_infm_biz_moddtm
    ON CHART_ACCT_INFM (BIZ_KEY, MOD_DTM DESC);
```

### 2.2 V94 — `wb_get_next_val` extension

```sql
-- V94__settings_chart_of_accounts_wb_get_next_val.sql
-- Adds the chart_acct_infm.chart_acct_seq_no branch ported VERBATIM from DEV.
-- Pattern: per-tenant MAX+1 (same as appr_line_no V91, trco_key V85).
-- Branch count after: 16.
-- Consumed by: LegacyFunctionRepository.nextChartAcctSeqNo(bizKey).
-- Retire when: replaced by a Java service-side generator; no fixed deadline.

CREATE OR REPLACE FUNCTION wb_get_next_val(
    p_table TEXT, p_field TEXT,
    p1 TEXT, p2 TEXT, p3 TEXT, p4 TEXT, p5 TEXT, p6 TEXT
) RETURNS TEXT AS $$
DECLARE
    v_next TEXT;
BEGIN
    -- … existing 15 branches stay unchanged …

    IF p_table = 'chart_acct_infm' AND p_field = 'chart_acct_seq_no' THEN
        SELECT COALESCE(MAX(chart_acct_seq_no::numeric), 0) + 1
          INTO v_next
          FROM chart_acct_infm
         WHERE biz_key = p3;
        RETURN v_next;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
```

Body is copied verbatim from DEV (introspection 2026-05-13). `p3` is the `BIZ_KEY` per the established 3-arg convention shared with `appr_line_no` (V91).

### 2.3 V95 — Per-tenant restricted-roots seed

```sql
-- V95__settings_chart_of_accounts_seed_restricted_roots.sql
-- Seeds ACC-01 / ACC-02 / ACC-03 for every active tenant in BUSI_INFM.
-- Resolves OQ-4: provisioning-time seed (not on-demand auto-creation).
-- Idempotent — ON CONFLICT against uk_chart_acct_cd.

INSERT INTO CHART_ACCT_INFM (
    BIZ_KEY, CHART_ACCT_SEQ_NO, CHART_ACCT_CD, CHART_ACCT_NM_EN,
    CHART_ACCT_NM_VN, CHART_ACCT_NM_KO, CHART_ACCT_TYPE,
    PARENT_CHART_ACCT_CD, STTS, DEL_YN, REG_ID, REG_DTM, MOD_ID, MOD_DTM, CAL_RULE
)
SELECT
    b.BIZ_KEY,
    wb_get_next_val('chart_acct_infm','chart_acct_seq_no', NULL, NULL, b.BIZ_KEY, NULL, NULL, NULL) AS seq,
    s.code, s.name_en, s.name_vn, s.name_ko, NULL, NULL,
    'A', 'N', b.BIZ_KEY, '20260513000000', b.BIZ_KEY, '20260513000000', NULL
FROM BUSI_INFM b
CROSS JOIN (VALUES
    ('ACC-01','REVENUE','REVENUE','수입'),
    ('ACC-02','EXPENSES','EXPENSES','지출'),
    ('ACC-03','INVESTMENT','INVESTMENT','투자')
) AS s(code, name_en, name_vn, name_ko)
WHERE b.DEL_YN = 'N'
ON CONFLICT (BIZ_KEY, CHART_ACCT_CD, DEL_YN) DO NOTHING;
```

Restricted-root names come straight from the DEV `VN_VNM0004_000001` rows (seq 70 / 74 / 80) — see [feature spec §4.2](../../features/settings/chart-of-accounts.md#42-restricted-root-codes). Treats every active tenant uniformly so any future R&D tenant seeded in a `Vnn` migration auto-inherits the three roots.

### 2.4 V96 — Demo seed (tenant `VN_VNM0004_000001`)

Imports 26 real rows from JEX DEV. Preserves DEV `CHART_ACCT_SEQ_NO` (70..112 range) verbatim — same pattern as V87 (Contacts) preserving `TRCO_KEY`. The V94 `wb_get_next_val` branch produces values starting at 113 for tenant 000001 (verified live), so newly-created refactor rows never collide with V96 seed values. Idempotent via `INSERT … ON CONFLICT DO NOTHING`.

(Exact INSERT body is mechanical — generate the script from DEV via `pg_dump --data-only --inserts --table=chart_acct_infm` filtered to `WHERE biz_key = 'VN_VNM0004_000001'`. Trim the V95-seeded restricted roots from the dump before commit.)

---

## 3. Schema vs. entity vs. DTO split

Per [Legacy Schema Parity Rule](../../../CLAUDE.md), three layers carry different scopes:

| Layer | Scope |
|---|---|
| **DB schema** (V93) | All 15 columns from DEV, no subset. |
| **JPA entity** (`ChartOfAccount`) | Maps all 15 columns explicitly. `chartAcctType` is exposed but documented as "currently unused in DEV (all NULL); reserved for forward compat". |
| **Wire DTO** (`ChartOfAccountRow`) | Drops `BIZ_KEY` (tenant from JWT) and `DEL_YN` (filtered server-side). Adds 3 computed fields: `chart_acct_level`, `has_children`, `restricted`. |

---

## 4. Data-volume / index-impact notes

- DEV `chart_acct_infm` row count for tenant `VN_VNM0004_000001`: **41 total** (29 active, 12 soft-deleted) — see queries in [migration map §1](../migration/chart-of-accounts.md). Tenant `VN_VNM0004_000014` (HAENGSUNG): **0 rows** today; V95 will seed the 3 restricted roots on apply.
- Realistic tenant scale: a mid-size company tracking ~3 levels deep across Revenue / Expense / Investment carries 100–300 active rows. The "load-all" list pattern (no pagination) is safe at this scale; if a tenant ever crosses 1000 rows, the panel paginates — but Phase 1 doesn't plan for it.
- Both helper indexes are cheap: `ix_chart_acct_infm_biz_parent` is a partial index on a tiny table; `ix_chart_acct_infm_biz_moddtm` is a tenant-scoped btree. Disk impact < 100 KB per tenant at the realistic scale.
- The `uk_chart_acct_cd` UK is the busiest path — every Create hits it twice (active dup-check, deleted-dup revive check) and every Delete hits it once (archive-rename collision check). All point lookups using the leading `(BIZ_KEY, CHART_ACCT_CD)` prefix.

---

## 5. Migration parity checklist (pre-merge)

Required by [wecms-api/CLAUDE.md](../../../CLAUDE.md) — paste into the MR description:

```
- [ ] Queried DEV `information_schema.columns` for chart_acct_infm
- [ ] Local migration column count (15) == DEV column count (15)
- [ ] Every legacy NOT-NULL column has the legacy DEFAULT or is in the seed
      (DEL_YN default 'N' preserved; STTS / REG_ID / REG_DTM / MOD_ID / MOD_DTM
       set explicitly in the create flow; CHART_ACCT_NM_EN required from request)
- [ ] Legacy DEFAULTs preserved verbatim (DEL_YN = 'N'::bpchar — kept as DEFAULT 'N' on the CHAR(1) column)
- [ ] PK + key indexes carried over (pk_chart_acct_infm, uk_chart_acct_cd)
- [ ] PRIMARY KEY columns + order match DEV (BIZ_KEY, CHART_ACCT_SEQ_NO)
- [ ] UNIQUE constraints from *_uk* series carried over (uk_chart_acct_cd)
- [ ] CHECK constraints from *_chk* series carried over (none in DEV — refactor mirrors)
- [ ] FOREIGN KEYs carried over OR documented as deferred (none in DEV — refactor mirrors;
      tenant + parent existence enforced in service layer)
- [ ] BIZ_KEY column type matches DEV (VARCHAR(20) — NOT 32)
- [ ] Triggers either ported or documented as deferred (no triggers in DEV)
- [ ] wb_get_next_val branch verified (per-tenant MAX+1 via V94, same shape as V91 appr_line_no)
```

---

## 6. Open / deferred items

| # | Item | Notes |
|---|---|---|
| DB-1 | V97 `CHART_ACCT_SEQ_NO` VARCHAR(14) → BIGINT | Deferred to Phase 2. Phase 1 ships with a JPA `@Convert(converter = StringToLongConverter.class)` on the column — same precedent as the V35→V36 dispOrd saga. |
| DB-2 | FK `chart_acct_infm.biz_key → busi_infm.biz_key` | NOT declared in DEV; refactor mirrors. Adding it would require V95 to use `INSERT … FROM BUSI_INFM` (already does); revisit if a future audit finds orphaned rows. |
| DB-3 | Self-referential FK `parent_chart_acct_cd → chart_acct_cd` within same `biz_key` | NOT declared in DEV; would be tricky (cross-row UK, not PK). Refactor mirrors — parent existence is service-validated. |
| DB-4 | Audit-log write to `AUDIT_LOGS_INFM` | Schema side: `AUDIT_LOGS_INFM` already exists per Settings precedent. No schema change for this feature; `ChartOfAccountsAuditLogger` (Spring side) just writes rows. |
| DB-5 | Trigger-based audit (legacy `*_log_h` history table) | DEV has no `chart_acct_infm_log_h` and no associated trigger. Refactor's audit-log path uses the application-level `AUDIT_LOGS_INFM` row instead — matches the firm-banking-settings / contacts precedent. |
