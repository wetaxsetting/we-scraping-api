# Contacts — Database Schema Design

| Field | Value |
|---|---|
| Refactor slug | `contacts` |
| Local DB | PostgreSQL `localhost:5434/wecms_dev` (R&D only) |
| Migrations | Flyway — `wecms-api/src/main/resources/db/migration/` |
| Functional spec | [`docs/refactor-wecms/features/contacts.md`](../../features/settings/contacts.md) |
| Backend design | [`docs/refactor-wecms/backend/contacts-design.md`](../business-rules/contacts-design.md) |
| Migration mapping | [`docs/refactor-wecms/migration/contacts-mapping.md`](../migration/contacts-mapping.md) |

---

## 1. Tables touched

All in the PROD Active Table List (cross-checked against [`wecms-api/CLAUDE.md` § PROD DB Table Scope](../../../CLAUDE.md)):

| Table | Role | Read | Write |
|---|---|---|---|
| `TRCO_INFM` | Master row per contact | ✓ | ✓ |
| `TRCO_ADDR_INFM` | Addresses (current + previous) | ✓ | ✓ |
| `TRCO_ACCT_INFO` | Bank accounts (Local) | ✓ | ✓ |
| `TRCO_OVRS_INFM` | Overseas beneficiaries | ✓ | ✓ |
| `PGRO_BSAC_LNKN_INFM` | Group membership rows (seeded with `B`/`S` at create) | — | ✓ |
| `ACCT_TRNS_RCV_PAY_INFM` | In-use guard before soft-delete | ✓ | — |
| `SYS_SCR_OPTION_WECMS` | View Setting system seed | ✓ | ✓ (one-time seed) |
| `USER_SCR_OPTION_WECMS` | View Setting per-user override | ✓ | ✓ (lazy-init via existing flow) |

Excluded tables (NOT in PROD Active Table List):

| Table | Why excluded |
|---|---|
| `TRCO_INFO_FILE` | File attachments out of scope |
| `TRCO_RSPB_INFM` | Responsible persons out of scope |
| `MEMO_INFM` | Memo search out of scope — search dropped from refactor list query |
| `TAX_AGENT_INFM` | Tax-agent feature out of scope |
| `TRCO_MDPH` | Mirror-history table — refactor uses central `AUDIT_LOGS_INFM` via aspect |

## 2. Legacy schema reference (DEV authority)

Per [`wecms-api/CLAUDE.md` § Legacy Schema Parity Rule](../../../CLAUDE.md), the local schema must match DEV column-for-column. Before writing the migration, the author runs:

```sql
-- Run against DEV via `postgres` MCP (read-only, metadata only).
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns WHERE table_name = 'TRCO_INFM' ORDER BY ordinal_position;
-- Repeat for TRCO_ADDR_INFM, TRCO_ACCT_INFO, TRCO_OVRS_INFM, PGRO_BSAC_LNKN_INFM.

SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid = 'TRCO_INFM'::regclass;
-- Repeat for each table — captures PK / UK / CHECK / FK.

SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'TRCO_INFM';
-- Repeat for each table.
```

The result is pasted into the migration header as a "DEV snapshot" comment so future reviewers can spot drift. The DEV column count for each table is recorded; the local migration must match it.

**Until the audit happens at MR time, the column shape below is a design-stage *minimum* — the real migration adds every DEV column. The Sprint subset rule applies to JPA entities, not to the DB schema.**

## 3. Tables — design-stage shape (parity-extended at MR time)

> Sizes / types / defaults below derive from the JEX entity files cited in [`docs/refactor-wecms/migration/contacts-mapping.md` § 2](../migration/contacts-mapping.md) (`TrcoInfm.java`, `TrcoAddrInfm.java`, `TrcoAcctInfo.java`, `TrcoOvrsInfm.java`). They are confirmed at MR time against DEV `information_schema.columns`. Audit columns ship as TIMESTAMPTZ locally (deviation from DEV VARCHAR(14)) — same documented deviation already accepted in `SysScrOptionWecms` per the V35 migration header.

### 3.1 `TRCO_INFM`

```sql
CREATE TABLE IF NOT EXISTS TRCO_INFM (
    BIZ_KEY                VARCHAR(20)  NOT NULL,
    TRCO_KEY               VARCHAR(20)  NOT NULL,
    HAND_TRCO_KEY          VARCHAR(20),
    TRCO_CMNM              VARCHAR(200) NOT NULL,
    TRCO_CMENM             VARCHAR(200) NOT NULL,
    TRCO_TAX_ID            VARCHAR(40),
    TRCO_NICK_NM           VARCHAR(100),
    TRCO_RPPR_NM           VARCHAR(200),
    TRCO_RPPR_ENM          VARCHAR(200),
    TRCO_TYPE              VARCHAR(1)   NOT NULL,                    -- 'L' or 'O'
    TRCO_STTS              VARCHAR(1),                                -- null = active, 'C' = soft-deleted
    TRCO_INQ_YN            VARCHAR(1)   NOT NULL DEFAULT 'Y',
    TRSC_CCY_CD            VARCHAR(3),
    TRCO_BSUN_POST_ADRS    VARCHAR(200),
    -- … remaining DEV columns added by parity audit
    REG_ID                 VARCHAR(100),
    REG_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),       -- local deviation
    MOD_ID                 VARCHAR(100),
    MOD_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT trco_infm_pk PRIMARY KEY (BIZ_KEY, TRCO_KEY)
);
CREATE INDEX IF NOT EXISTS ix_trco_infm_biz_handkey ON TRCO_INFM (BIZ_KEY, HAND_TRCO_KEY);
CREATE INDEX IF NOT EXISTS ix_trco_infm_biz_taxid   ON TRCO_INFM (BIZ_KEY, TRCO_TAX_ID);
CREATE INDEX IF NOT EXISTS ix_trco_infm_biz_stts_modkey ON TRCO_INFM (BIZ_KEY, TRCO_STTS, MOD_DTM DESC, TRCO_KEY DESC);
```

The two `ix_trco_infm_biz_*` indexes back the duplicate checks; the third backs the default ORDER BY `mod_dtm DESC`. Extra indexes beyond DEV are explicitly allowed per the Legacy Schema Parity Rule.

### 3.2 `TRCO_ADDR_INFM`

```sql
CREATE TABLE IF NOT EXISTS TRCO_ADDR_INFM (
    BIZ_KEY                VARCHAR(20)  NOT NULL,
    TRCO_KEY               VARCHAR(20)  NOT NULL,
    TRCO_ADDR_KEY          VARCHAR(20)  NOT NULL,
    TRCO_BSUN_POST_ADRS    VARCHAR(200),
    DISP_ORD               INTEGER,
    ISSU_DT                DATE,
    DUE_DT                 DATE,
    -- … remaining DEV columns
    REG_ID                 VARCHAR(100),
    REG_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_ID                 VARCHAR(100),
    MOD_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT trco_addr_infm_pk PRIMARY KEY (BIZ_KEY, TRCO_KEY, TRCO_ADDR_KEY)
);
CREATE INDEX IF NOT EXISTS ix_trco_addr_infm_biz_trco ON TRCO_ADDR_INFM (BIZ_KEY, TRCO_KEY);
```

### 3.3 `TRCO_ACCT_INFO`

```sql
CREATE TABLE IF NOT EXISTS TRCO_ACCT_INFO (
    BIZ_KEY                VARCHAR(20)  NOT NULL,
    TRCO_KEY               VARCHAR(20)  NOT NULL,
    TRCO_ACCT_KEY          VARCHAR(20)  NOT NULL,
    BANK_NM                VARCHAR(2000),
    BANK_CD                VARCHAR(20),
    ACCT_NO                VARCHAR(100),
    NAPAS_BANK_CD          VARCHAR(10),
    NAPAS_BANK_YN          VARCHAR(1),
    CITAD_BANK_CD          VARCHAR(200),
    ACCT_CCY_CD            VARCHAR(3),
    FINC_MDA_OWNER_NM      VARCHAR(150),
    OWAC_NM                VARCHAR(50),
    OWAC_ADDR              VARCHAR(500),
    TRCO_NM                VARCHAR(200),
    TRCO_ENM               VARCHAR(200),
    DISP_ORD               INTEGER,
    -- … remaining DEV columns
    REG_ID                 VARCHAR(100),
    REG_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_ID                 VARCHAR(100),
    MOD_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT trco_acct_info_pk PRIMARY KEY (BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY)
);
CREATE INDEX IF NOT EXISTS ix_trco_acct_info_biz_trco ON TRCO_ACCT_INFO (BIZ_KEY, TRCO_KEY);
CREATE INDEX IF NOT EXISTS ix_trco_acct_info_napas    ON TRCO_ACCT_INFO (BIZ_KEY, NAPAS_BANK_CD)
    WHERE NAPAS_BANK_CD IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_trco_acct_info_citad    ON TRCO_ACCT_INFO (BIZ_KEY, CITAD_BANK_CD)
    WHERE CITAD_BANK_CD IS NOT NULL;
```

### 3.4 `TRCO_OVRS_INFM`

```sql
CREATE TABLE IF NOT EXISTS TRCO_OVRS_INFM (
    BIZ_KEY                VARCHAR(20)  NOT NULL,
    TRCO_KEY               VARCHAR(20)  NOT NULL,
    TRCO_OVRS_KEY          VARCHAR(20)  NOT NULL,
    BENE_NAME              VARCHAR(200),
    BENE_ACCT_NO           VARCHAR(100),
    BIC_CODE               VARCHAR(50),
    BENE_BANK_NAME         VARCHAR(200),
    BENE_BANK_CODE         VARCHAR(20),
    BENE_ADDR              VARCHAR(500),
    -- … remaining DEV columns
    REG_ID                 VARCHAR(100),
    REG_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_ID                 VARCHAR(100),
    MOD_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT trco_ovrs_infm_pk PRIMARY KEY (BIZ_KEY, TRCO_KEY, TRCO_OVRS_KEY)
);
CREATE INDEX IF NOT EXISTS ix_trco_ovrs_infm_biz_trco ON TRCO_OVRS_INFM (BIZ_KEY, TRCO_KEY);
```

### 3.5 `PGRO_BSAC_LNKN_INFM`

```sql
CREATE TABLE IF NOT EXISTS PGRO_BSAC_LNKN_INFM (
    BIZ_KEY                VARCHAR(20)  NOT NULL,
    GRP_CD                 VARCHAR(20)  NOT NULL,
    USER_ID                VARCHAR(100) NOT NULL,
    TRCO_KEY               VARCHAR(20)  NOT NULL,
    TAX_ID                 VARCHAR(40),
    -- … remaining DEV columns
    REG_ID                 VARCHAR(100),
    REG_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    MOD_ID                 VARCHAR(100),
    MOD_DTM                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pgro_bsac_lnkn_infm_pk PRIMARY KEY (BIZ_KEY, GRP_CD, USER_ID, TRCO_KEY)
);
CREATE INDEX IF NOT EXISTS ix_pgro_bsac_lnkn_infm_biz_trco ON PGRO_BSAC_LNKN_INFM (BIZ_KEY, TRCO_KEY);
```

The actual PK column-set comes from DEV — the design above is best-effort from the entity reference; **must be verified before the migration lands**.

### 3.6 Foreign keys

Following [`wecms-api/CLAUDE.md` § Legacy Schema Parity Rule — Constraints](../../../CLAUDE.md), every DEV `*_fk*` carried over is added; FKs to out-of-scope tables (e.g. `TRCO_MDPH`) are documented as **deferred** in the migration trailer.

```sql
ALTER TABLE TRCO_ADDR_INFM
    ADD CONSTRAINT trco_addr_infm_fk01
    FOREIGN KEY (BIZ_KEY, TRCO_KEY) REFERENCES TRCO_INFM(BIZ_KEY, TRCO_KEY)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE TRCO_ACCT_INFO
    ADD CONSTRAINT trco_acct_info_fk01
    FOREIGN KEY (BIZ_KEY, TRCO_KEY) REFERENCES TRCO_INFM(BIZ_KEY, TRCO_KEY)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE TRCO_OVRS_INFM
    ADD CONSTRAINT trco_ovrs_infm_fk01
    FOREIGN KEY (BIZ_KEY, TRCO_KEY) REFERENCES TRCO_INFM(BIZ_KEY, TRCO_KEY)
    DEFERRABLE INITIALLY DEFERRED;
```

DEV may also carry FKs from `PGRO_BSAC_LNKN_INFM` → `TRCO_INFM` and audit-log triggers; capture during the parity audit.

### 3.7 Triggers

Per the rule "Triggers + audit `_log` functions" in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md), every legacy BEFORE / AFTER trigger on these tables is **deferred** to the sprint that ports the dependent history tables (`TRCO_MDPH` etc.). The migration trailer block documents:

```
-- DEFERRED TRIGGERS (DEV-only, out of refactor scope):
--   TRG_TRCO_INFM_BEFORE_UPDATE       (audit fan-out to TRCO_MDPH)
--   TRG_TRCO_INFM_AFTER_INSERT        (audit fan-out)
--   TRG_TRCO_ADDR_INFM_AFTER_UPDATE   (audit)
--   TRG_TRCO_ACCT_INFO_AFTER_UPDATE   (audit)
--   TRG_TRCO_OVRS_INFM_AFTER_UPDATE   (audit)
```

The refactor's central `AUDIT_LOGS_INFM` aspect handles the audit need at the application layer.

## 4. Flyway migration outline

Assuming current head is V74 (the last migration documented in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) at time of writing), the Contacts MR adds:

### V{n}__contacts_schema.sql

```sql
-- =====================================================================
-- Contacts schema (TRCO_INFM + TRCO_ADDR_INFM + TRCO_ACCT_INFO
--                  + TRCO_OVRS_INFM + PGRO_BSAC_LNKN_INFM).
--
-- DEV column counts (recorded at audit time):
--   TRCO_INFM            : XX columns
--   TRCO_ADDR_INFM       : XX columns
--   TRCO_ACCT_INFO       : XX columns
--   TRCO_OVRS_INFM       : XX columns
--   PGRO_BSAC_LNKN_INFM  : XX columns
-- Local migration column counts MUST match — verified by reviewer.
-- =====================================================================

CREATE TABLE IF NOT EXISTS TRCO_INFM ( ... );
CREATE TABLE IF NOT EXISTS TRCO_ADDR_INFM ( ... );
CREATE TABLE IF NOT EXISTS TRCO_ACCT_INFO ( ... );
CREATE TABLE IF NOT EXISTS TRCO_OVRS_INFM ( ... );
CREATE TABLE IF NOT EXISTS PGRO_BSAC_LNKN_INFM ( ... );

-- Sequence for TrcoKeyGeneratorService (Option A — pick at MR time per OQ-4)
CREATE SEQUENCE IF NOT EXISTS sq_trco_key START WITH 1000000 INCREMENT BY 1;

-- Indexes (PK + duplicate-check + sort)
CREATE INDEX IF NOT EXISTS ... ;

-- Foreign keys (deferrable, to TRCO_INFM only — TRCO_MDPH FKs deferred)
ALTER TABLE TRCO_ADDR_INFM ADD CONSTRAINT trco_addr_infm_fk01 ... ;
ALTER TABLE TRCO_ACCT_INFO ADD CONSTRAINT trco_acct_info_fk01 ... ;
ALTER TABLE TRCO_OVRS_INFM ADD CONSTRAINT trco_ovrs_infm_fk01 ... ;

-- DEFERRED (documented in trailer):
--   TRG_TRCO_*_*  triggers (audit fan-out)
--   FKs to TRCO_MDPH (out of scope)
```

### V{n+1}__contacts_seed.sql

```sql
-- =====================================================================
-- Contacts demo seed — ≤ 30 most recent rows per table for tenant
-- VN_VNM0004_000001 (per Data Migration Rule).
-- =====================================================================

INSERT INTO TRCO_INFM (BIZ_KEY, TRCO_KEY, HAND_TRCO_KEY, TRCO_CMNM, TRCO_CMENM, ...)
VALUES
    ('VN_VNM0004_000001', '...', ...),
    ...
ON CONFLICT (BIZ_KEY, TRCO_KEY) DO NOTHING;

INSERT INTO TRCO_ADDR_INFM ( ... ) VALUES ( ... ) ON CONFLICT DO NOTHING;
INSERT INTO TRCO_ACCT_INFO ( ... ) VALUES ( ... ) ON CONFLICT DO NOTHING;
INSERT INTO TRCO_OVRS_INFM ( ... ) VALUES ( ... ) ON CONFLICT DO NOTHING;
INSERT INTO PGRO_BSAC_LNKN_INFM ( ... ) VALUES ( ... ) ON CONFLICT DO NOTHING;
```

Source rows are read-only from DEV via the `postgres` MCP (per [`.claude/rules/database.md`](../../../../.claude/rules/database.md)) using a `SELECT … LIMIT 30 ORDER BY MOD_DTM DESC` per table.

### V{n+2}__contacts_view_setting_seed.sql

```sql
-- Seed View Setting columns for the Contacts screen.

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, FIX_YN, SORT_YN, SIZE, DISP_YN, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('contacts', 'default', 'CHECKBOX',            'CHECKBOX',           0, 'Y', 'Y', 'N', '38',  'Y', 'SEED', now(), 'SEED', now()),
    ('contacts', 'default', 'HAND_TRCO_KEY',       'Contact Code',       1, 'Y', 'Y', 'Y', '160', 'Y', 'SEED', now(), 'SEED', now()),
    ('contacts', 'default', 'TRCO_CMNM',           'Company',            2, 'Y', 'N', 'Y', '260', 'Y', 'SEED', now(), 'SEED', now()),
    ('contacts', 'default', 'TRCO_TYPE',           'Contact Location',   3, 'Y', 'N', 'Y', '140', 'Y', 'SEED', now(), 'SEED', now()),
    ('contacts', 'default', 'TRCO_TAX_ID',         'Tax ID',             4, 'Y', 'N', 'Y', '160', 'Y', 'SEED', now(), 'SEED', now()),
    ('contacts', 'default', 'BANK_NM',             'Bank',               5, 'Y', 'N', 'N', '240', 'Y', 'SEED', now(), 'SEED', now()),
    ('contacts', 'default', 'TRCO_BSUN_POST_ADRS', 'Address',            6, 'Y', 'N', 'N', '320', 'Y', 'SEED', now(), 'SEED', now()),
    ('contacts', 'default', 'ETC',                 'ETC',               99, 'Y', 'Y', 'N', '50',  'Y', 'SEED', now(), 'SEED', now())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
```

Notes on the seed:

- `WEB_SVC_ID = 'contacts'` is the **refactor slug** — semantic kebab-case per the View Setting `WEB_SVC_ID` convention in [`wecms-api/CLAUDE.md` § Screen Option (View Setting)](../../../CLAUDE.md).
- `CHECKBOX` (`DISP_ORD = 0`) anchors the row-selection column with `FIX_YN='Y'`. `ETC` (`DISP_ORD = 99`) anchors the action column (the ⋯ menu) with `FIX_YN='Y'`. Per the special-FIELD_ID rule, both are admin-controlled.
- `BANK_NM` and `TRCO_BSUN_POST_ADRS` ship `SORT_YN='N'` — they are sub-table columns NOT in `ContactSortField`. Per the View-Setting + whitelist agreement rule, if a column has no enum constant, `SORT_YN` MUST be `'N'`.
- `SIZE` values are placeholders — the Figma frame (when registered) is the authority. The seed lands the rounded approximations from the legacy view JSP colgroup ([comm_0184_001_view.jsp:139-148](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp)) and the next stabilisation pass realigns to Figma.

## 5. Data Migration Rule compliance

Per [`wecms-api/CLAUDE.md` § Data Migration Rule](../../../CLAUDE.md):

- **Tenant pin**: `BIZ_KEY = 'VN_VNM0004_000001'` only.
- **Row cap**: ≤ 30 rows per table. For Contacts the seed is the 30 most recently modified `TRCO_INFM` rows for the tenant + the matching child rows from `TRCO_ADDR_INFM`, `TRCO_ACCT_INFO`, `TRCO_OVRS_INFM` (parent-first order so FKs don't violate).
- **Idempotency**: every `INSERT ... ON CONFLICT DO NOTHING`.
- **Other tenants**: never seeded.
- **`jex_*` / backups / `_iXXXXX`**: never seeded.

Pre-migration parity checklist (paste into MR description):

```
- [ ] Queried DEV `information_schema.columns` for every table touched
- [ ] Local migration column count == DEV column count
- [ ] Every legacy NOT-NULL column has the legacy DEFAULT or is in the seed
- [ ] Legacy DEFAULTs preserved verbatim
- [ ] PK + key indexes carried over
- [ ] PRIMARY KEY column order matches DEV
- [ ] UNIQUE constraints from *_uk* series carried over
- [ ] CHECK constraints from *_chk* series carried over
- [ ] FOREIGN KEYs carried over OR documented as deferred
- [ ] BIZ_KEY column type matches DEV (VARCHAR(20) — NOT 32)
- [ ] Triggers either ported or documented as deferred
- [ ] wb_get_next_val branches verified — Contacts intentionally adds NO new branch (Java-side generator)
- [ ] Data migration: BIZ_KEY pin + ≤30 rows / table + ON CONFLICT DO NOTHING
```

## 6. Key generation — `TRCO_KEY`

Per the rule in [`wecms-api/CLAUDE.md` § "DB Function / Legacy Function Policy"](../../../CLAUDE.md) — the refactor does **not** add a `wb_get_next_val('trco_infm','trco_key',…)` branch. Key generation happens in Java (`TrcoKeyGeneratorService`).

Two options to pick from at MR time per OQ-4:

- **Option A — plain monotonic sequence** (`sq_trco_key` created in the schema migration).
- **Option B — composite `{biz_key}_{date}_{seq}`** — requires per-tenant sequences (created on first use).

If DEV's `wb_get_next_val('trco_infm','trco_key', …)` returns a composite-looking string, pick Option B; otherwise Option A. Audit query (read-only):

```sql
SELECT wecmsvndev.wb_get_next_val('trco_infm', 'trco_key', NULL, NULL, NULL);
```

Record the result in the migration header so future readers know why the choice was made.

## 7. Bank-name resolution

The legacy IDO calls `wb_get_napas_bank_nm_or_citad_bank_nm(NAPAS_OR_CITAD_CD, LANG_CD)` ([IDO.TRCO_INFM_R016.xml:63](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016.xml)).

Refactor replaces this with the existing `BankCdResolver` ([`wecms-api/CLAUDE.md` § Reference data](../../../CLAUDE.md)) which reads from `BANK_INFM` (V20 seed). Per OQ-7, the parity audit confirms `BANK_INFM` carries both NAPAS and CITAD codes — if not, the same MR extends the registry seed.

No new DB function is added. No new lookup table is added. No hard-coded `Map<String, String>` for bank names anywhere.

## 8. Cross-table impacts

- `ACCT_TRNS_RCV_PAY_INFM` — read-only `existsByBizKeyAndTrcoKey(...)` for the in-use guard. The table is already in scope (V21 schema); the Contacts MR adds **only** the derived method on the existing repository — no schema change.
- `AUDIT_LOGS_INFM` — written by the existing global aspect with `WSVC_ID = 'contacts'`. No schema change.

## 9. Local-only constraints

- All changes apply to the local R&D Postgres at `localhost:5434/wecms_dev`.
- Per [`.claude/rules/database.md`](../../../../.claude/rules/database.md) — Claude Code does not run DDL/DML against DEV/UAT/PROD. The migration runs only when the developer runs `./gradlew bootRun` (Flyway auto-migrates on startup) or `./gradlew flywayMigrate`.
- No data is copied back to DEV / UAT / PROD ever.

## 10. Out-of-scope schema work

| Item | Reason |
|---|---|
| `TRCO_INFO_FILE` | Out of PROD active list |
| `TRCO_RSPB_INFM` | Out of PROD active list |
| `MEMO_INFM` | Out of PROD active list |
| `TAX_AGENT_INFM` | Out of PROD active list |
| `TRCO_MDPH` (history twin) | Audit moved to central `AUDIT_LOGS_INFM` aspect |
| Legacy triggers `TRG_TRCO_*_*` | Audit fan-out moved to application layer |
| `wb_get_next_val('trco_infm','trco_key', …)` branch | Java-side generator instead |
| `wb_get_napas_bank_nm_or_citad_bank_nm` | Java-side `BankCdResolver` instead |
| `wb_get_translate_text(...)` (used in legacy list search) | Refactor searches raw column text |
