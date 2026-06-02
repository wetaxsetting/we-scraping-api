# Firm Banking Settings — Database Schema (Refactor)

**Companion to:** [docs/refactor-wecms/features/settings/firm-banking-settings.md](../../../features/settings/firm-banking-settings.md), [docs/refactor-wecms/migration/firm-banking-settings-mapping.md](../../migration/firm-banking-settings-mapping.md)
**Phase:** 1 — design only. No DDL executed in this phase.
**Source-of-truth:** JEX DEV — `wecmsvndev` schema, captured 2026-05-09 via the `postgres` MCP (read-only, per [.claude/rules/database.md](../../../../../.claude/rules/database.md)).

The Legacy Schema Parity Rule (`wecms-api/CLAUDE.md`) requires the local Flyway schema to mirror DEV column-for-column. The DDL below is extracted from `information_schema.columns` and `pg_constraint` — paste-ready for Flyway with names normalized to UPPER_SNAKE.

---

## 1. Tables touched

| Table | Role | Migration ownership |
|---|---|---|
| `TB_FIRM_CONF` | Owning table — primary CRUD target | This feature |
| `TB_FIRM_BANK_CODE` | Bank code registry — lookup + JOIN for `bank_nm`; existence check (V10) | This feature **if** not already provisioned by a sibling feature; otherwise verify schema parity. |
| `NAPAS_BANK_INFO` | Napas bank registry — existence check (V9) | Owned by `domain/banking` (already partially ported) |
| `ACCT_MSTR` | Tenant account ledger — list JOIN + existence check (V11) | Owned by `domain/banking` (`V13` Sprint 1 banking schema, `V23` parity) |
| `AUDIT_LOGS_INFM` | Append-only audit log | Shared — see feature §11.1 |
| `V_FIRM_BANKING_SETTING` (NEW VIEW) | List read-only join | This feature |

---

## 2. `TB_FIRM_CONF` — column-for-column DDL

Extracted from JEX DEV `information_schema.columns` on 2026-05-09. **Column count: 14.** Primary key: `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)`.

| # | Column | Type | Length / Precision | Nullable | Default | Notes |
|---|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | `VARCHAR` | 20 | NOT NULL | — | Tenant key. PK. |
| 2 | `OUT_BANK_CD` | `VARCHAR` | 15 | NOT NULL | — | NAPAS bank code. PK. |
| 3 | `BANK_PRTN_ID` | `VARCHAR` | 20 | NOT NULL | — | Partner ID issued by Shinhan / Woori. |
| 4 | `VAN_ORG_CD` | `VARCHAR` | 20 | NOT NULL | — | VAN organization code. |
| 5 | `ORG_STAT` | `VARCHAR` | 1 | NOT NULL | `'0'` | `0..3` per `wabooks.enums.OrgStat` (WAITING / AVAILABLE / SUSPENDED / TERMINATED). DEV default is `'0'` (WAITING); JEX DAO override is `'1'` (AVAILABLE) — see feature V17 / OQ. |
| 6 | `WDRL_ACT_NO` | `VARCHAR` | 100 | NOT NULL | — | Tenant company account number. PK. |
| 7 | `FILE_TYPE` | `CHAR` | 3 | NOT NULL | — | Server-derived from `OUT_BANK_CD` (`SHB`/`WRB`). |
| 8 | `BAT_TRAN_BRANCH_DIV` | `CHAR` | 1 | NOT NULL | `'Y'` | Y/N. |
| 9 | `BAT_TRAN_BRANCH_LIMIT` | `NUMERIC` | (7,0) | NOT NULL | `2000` | Per-batch transfer limit. |
| 10 | `REG_DTTM` | `VARCHAR` | 14 | NOT NULL | — | yyyyMMddHHmmss UTC. |
| 11 | `START_DTTM` | `VARCHAR` | 14 | NULL | — | Same format. |
| 12 | `END_DTTM` | `VARCHAR` | 14 | NULL | — | Same format. |
| 13 | `MOD_DTTM` | `VARCHAR` | 14 | NULL | — | Same format. |
| 14 | `LAST_EDTR` | `VARCHAR` | 50 | NULL | — | User name (not user_id). |

**Constraints — extracted via `pg_constraint`:**

| `conname` | `contype` | Definition |
|---|---|---|
| `pk_tb_firm_conf` | PRIMARY KEY | `PRIMARY KEY (biz_key, out_bank_cd, wdrl_act_no)` |

**Indexes — extracted via `pg_indexes`:**

| Name | Definition |
|---|---|
| `pk_tb_firm_conf` | `CREATE UNIQUE INDEX pk_tb_firm_conf ON wecmsvndev.tb_firm_conf USING btree (biz_key, out_bank_cd, wdrl_act_no)` |

DEV has **no other indexes** on this table — workloads are PK-driven (the list service filters only by `BIZ_KEY` and the JOIN partner is `ACCT_MSTR`).

**Refactor parity stance:**
- Mirror DEV exactly — column names, types, lengths, nullability, defaults, PK.
- Honor the `BAT_TRAN_BRANCH_LIMIT` `NUMERIC(7,0)` cap (max value 9 999 999) — do **not** widen unless DEV moves first.
- The `FILE_TYPE CHAR(3)` is a fixed-width legacy column. Hibernate's `String` mapping reads `bpchar` correctly when annotated `@JdbcTypeCode(SqlTypes.CHAR)` — apply it on the entity to avoid a `validate` failure (precedent: `SysScrOptionWecms.sortYn` in V38).
- The `BAT_TRAN_BRANCH_DIV CHAR(1)` likewise — annotate `@JdbcTypeCode(SqlTypes.CHAR)` on the entity field.

**Encryption — none.** `wdrl_act_no` stays plain text in the schema (legacy parity, OQ-4 resolved 2026-05-09). No feature-local encryption, no `@Convert` on the entity column. API-level masking is the v1 protection (feature §9). Future encryption is a project-wide migration covering `ACCT_MSTR.acct_no`, `TB_FIRM_CONF.wdrl_act_no`, `USER_BUSI_CONN_INFM`, etc. in lockstep — not a per-feature task.

---

## 3. `V_FIRM_BANKING_SETTING` — list read view

Replicates `IDO.TB_FIRM_CONF_R004.xml` (LEFT JOIN to `ACCT_MSTR` for `CCY_CD`/`BAL`/`EXPI_DT` + JOIN to `TB_FIRM_BANK_CODE` for `BANK_NM`). New view, owned by this feature.

```sql
CREATE OR REPLACE VIEW V_FIRM_BANKING_SETTING AS
SELECT
    TFC.BIZ_KEY,
    TFC.OUT_BANK_CD,
    COALESCE(TFBC.BANK_NM, TFBC.BANK_ENG_NM) AS BANK_NM,
    AM.CCY_CD,
    TFC.BANK_PRTN_ID,
    TFC.VAN_ORG_CD,
    TFC.ORG_STAT,
    TFC.WDRL_ACT_NO,
    TFC.FILE_TYPE,
    TFC.BAT_TRAN_BRANCH_DIV,
    TFC.BAT_TRAN_BRANCH_LIMIT,
    TFC.REG_DTTM,
    TFC.START_DTTM,
    TFC.END_DTTM,
    TFC.MOD_DTTM,
    TFC.LAST_EDTR,
    AM.BAL,
    AM.EXPI_DT
FROM TB_FIRM_CONF TFC
LEFT JOIN ACCT_MSTR AM
    ON  TFC.BIZ_KEY = AM.BIZ_KEY
    AND TFC.WDRL_ACT_NO = AM.ACCT_NO
LEFT JOIN TB_FIRM_BANK_CODE TFBC
    ON  TFBC.ANT_DIV_CD = 'NAPAS'
    AND TFBC.BANK_CD = TFC.OUT_BANK_CD;
```

Notes:
- `LEFT JOIN ACCT_MSTR` — same as JEX. A firm-conf row without a matching `ACCT_MSTR` row still appears (with null `BAL`, `EXPI_DT`, `CCY_CD`).
- `LEFT JOIN TB_FIRM_BANK_CODE` — adds `BANK_NM` for the list display. JEX's `R004` IDO doesn't include `BANK_NM` (the WSVC `r001` outputs it from elsewhere — likely a frontend-side enrichment). Folding it into the view simplifies the refactor.
- Filter `ANT_DIV_CD = 'NAPAS'` — only NAPAS bank codes are valid for `OUT_BANK_CD` in this flow.

`@Immutable` JPA entity binds to this view. The list controller calls `repo.findAllByBizKey(bizKey)` — JEX has no other list filter today.

---

## 4. `TB_FIRM_BANK_CODE` — lookup table

Confirmed in JEX DEV (column count: 14, PK: `(ant_div_cd, bank_cd)`). The refactor only needs `BANK_NM`, `BANK_ENG_NM`, and existence check by `(ANT_DIV_CD, BANK_CD)` — the JPA entity may map a Sprint subset of columns, but the **Flyway schema must be column-for-column with DEV** (Legacy Schema Parity Rule).

Verify whether the table is already provisioned locally by a sibling feature before adding it here. If it is, this feature owns no new schema for `TB_FIRM_BANK_CODE` — only the JPA entity.

---

## 5. `AUDIT_LOGS_INFM` — append-only audit log

Owned by the **shared audit-log feature** (to be authored in tandem — see feature §11.1). The firm-banking-settings feature consumes the table via `AuditLogService.append(...)`; it does not own the schema.

If the table does not yet exist in the local Flyway head when this feature lands, the audit-log feature must ship first.

---

## 6. Planned Flyway migrations

Numbered tentatively from the current Flyway head (`V42`). Final numbers assigned at implementation time, monotonic, no gaps.

| Version (tentative) | Filename | Contents |
|---|---|---|
| `V{N}` | `V{N}__firm_banking_settings_source_tables.sql` | `CREATE TABLE TB_FIRM_CONF` (column-for-column from §2) + `CREATE TABLE TB_FIRM_BANK_CODE` (if not already present locally) + indexes + PKs. Idempotent via `CREATE TABLE IF NOT EXISTS`. |
| `V{N+1}` | `V{N+1}__firm_banking_settings_view.sql` | `CREATE OR REPLACE VIEW V_FIRM_BANKING_SETTING` per §3. Idempotent by definition. |
| `V{N+2}` | `V{N+2}__firm_banking_settings_demo_seed.sql` | `INSERT … ON CONFLICT DO NOTHING` for tenant `VN_VNM0004_000001`: 1 Shinhan binding (`out_bank_cd=970424`, `file_type='SHB'`) + 1 Woori binding (`out_bank_cd=970457`, `file_type='WRB'`) + matching `TB_FIRM_BANK_CODE` rows where missing. Includes corresponding `ACCT_MSTR` rows so the LEFT JOIN populates `CCY_CD`/`BAL`. |

**Pre-migration checklist (paste into the MR description, per `wecms-api/CLAUDE.md` Legacy Schema Parity Rule):**

```
- [ ] Queried DEV `information_schema.columns` for TB_FIRM_CONF (column count: 14) ✅ 2026-05-09
- [ ] Local migration column count == DEV column count
- [ ] Every legacy NOT-NULL column has the legacy DEFAULT or is in the seed
- [ ] Legacy DEFAULTs preserved verbatim (ORG_STAT='0', BAT_TRAN_BRANCH_DIV='Y', BAT_TRAN_BRANCH_LIMIT=2000)
- [ ] PK + key indexes carried over (pk_tb_firm_conf only)
- [ ] If applied locally already, mismatch fixed by a NEW migration (additive only)
```

---

## 7. JPA entity — Sprint subset declaration

The Flyway schema mirrors DEV exactly (14 columns); the JPA entity may map only the columns the feature reads/writes. Document the subset in the entity Javadoc:

```java
/**
 * TB_FIRM_CONF — firm-banking outbound channel binding.
 *
 * Sprint subset: maps every legacy column (the table is small enough that there is
 * no value in dropping any). All 14 columns; column-for-column with JEX DEV.
 *
 * Refactor-only deviations from DEV: none — schema is exact-parity.
 *
 * Encryption-at-rest: none (DEV parity). See
 * docs/refactor-wecms/features/settings/firm-banking-settings.md §7.4 / OQ-4.
 */
```

---

## 8. Hibernate `validate` traps

Pre-flight the entity against DEV's column types — these are known categories that have bitten earlier features (V37/V38/V39/V40):

| DEV type | Hibernate `validate` trap | Mitigation |
|---|---|---|
| `CHAR(3)` (`FILE_TYPE`), `CHAR(1)` (`BAT_TRAN_BRANCH_DIV`) | Hibernate maps Java `String` to `varchar`; `validate` rejects `bpchar`. | Annotate the field `@JdbcTypeCode(SqlTypes.CHAR)`. (Precedent: V38 `SysScrOptionWecms.sortYn`.) |
| `NUMERIC(7,0)` (`BAT_TRAN_BRANCH_LIMIT`) | Map to Java `Integer` (max 9 999 999 fits) — using `Long` triggers BIGINT mismatch (precedent: V35→V36 `disp_ord` revert). | Map to `Integer`. |
| `VARCHAR(14)` (`REG_DTTM` etc.) | OK as `String` — keep raw legacy format, no joda/Instant conversion at the entity level. The JEX entity converts via `DateTime14Serializer`; the refactor does **not** — wire emits the 14-digit string verbatim (mapping doc §4.5). |

---

## 9. Out-of-scope schema

Items the feature does **not** touch in v1:

- `ENV_SET_INFM` — owned by the (out-of-scope) Auto Transfer / Auto Match toggle feature (feature §10).
- `NAPAS_BANK_INFO` — pre-existing under `domain/banking`. Feature consumes via repo, no schema change.
- `ACCT_MSTR` — pre-existing (V13 + V23). No schema change.
- Encryption columns — not introduced (feature §7.4 / OQ-4 resolved 2026-05-09 — plain text, project-wide encryption is a separate future migration).

---

## 10. Verification queries (post-migration sanity)

After applying the planned migrations, the developer runs these read-only queries against the local DB to confirm parity:

```sql
-- 1. Confirm TB_FIRM_CONF exists with 14 columns
SELECT count(*)
FROM information_schema.columns
WHERE table_name = 'tb_firm_conf';
-- expected: 14

-- 2. Confirm column types match DEV (run per column; spot-check)
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'tb_firm_conf'
ORDER BY ordinal_position;

-- 3. Confirm PK
SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'tb_firm_conf'::regclass AND contype = 'p';

-- 4. Confirm view returns at least the seeded rows
SELECT count(*)
FROM v_firm_banking_setting
WHERE biz_key = 'VN_VNM0004_000001';
-- expected: 2 (post-seed)
```
