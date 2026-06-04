# Approval Box — Database Impact

**Feature slug:** `approval-box`
**Module:** `payment`
**Date:** 2026-05-13
**Status:** Design — no migration file placed yet.

Rules source: [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Legacy Schema Parity Rule", §"Key-generation rule for migrated legacy tables", §"DB Function / Legacy Function Policy"; [.claude/rules/database.md](../../../../../.claude/rules/database.md) (read-only DEV inspection only).

---

## 1 · Headline

> **Default: no new Flyway migration is required for this MR.** The Approval Box list endpoint is read-only over four legacy tables that already exist on DEV and only need to be made visible to the refactor's local PostgreSQL via the standard Sprint 1+ schema-parity flow. The Flyway file is **drafted** below (file content only) so the schema review can happen before placement.

The migration covers four legacy tables plus seed data and View Setting columns. It is a **schema-parity port**, not a new schema design.

---

## 2 · Inventory of DB objects this feature consumes

### 2.1 · Tables (mirroring DEV `wecmsvndev` schema, column-for-column)

| # | Table | Owner | Action in this MR |
|---|---|---|---|
| 1 | `EXPT_RESOL_MSTR` | Approval Box | **NEW** — port column-for-column from DEV. |
| 2 | `EXPT_RESOL_APPRVR_INFM` | Approval Box | **NEW** — port column-for-column from DEV. |
| 3 | `EXPT_RESOL_FILE` | Approval Box (detail port consumes; list only needs `FILE_NM` from sibling tables) | **NEW** — port column-for-column from DEV. Needed for FK integrity; not selected from in the list path but referenced by the detail-popup MR that lands next. |
| 4 | `EXPT_RESOL_PAY_EVID_FILE` | Approval Box | **NEW** — port column-for-column. Same reason. |
| 5 | `EXPT_RESOL_APPR_LINE` | Approval-Line config (separate feature) | **DEFERRED.** Approval Box list does not read it; the Settings → Approval Line feature owns this port. |
| 6 | `BLTB_POST_INFM` | (general bulletin / discussion) | **NEW (minimal)** — only the columns this feature joins (`BIZ_KEY`, `APPR_SEQ_NO`, `BLTB_CD`, `POST_SEQ_NO`, `POST_TITLE`, `REF_BLTB_CD`, `REF_POST_SEQ_NO`). Full port lands when the bulletin feature ships. Marked with deferred-FK comments. |
| 7 | `OVRS_PYMN_MSTR` | (Overseas Payment) | **NEW (minimal)** — `BIZ_KEY`, `APPR_SEQ_NO`, `TRNF_CCY_CD`. Comment: full port lands with Overseas feature. |
| 8 | `SCDUL_PYMN_SUMR` | (Scheduled Payment) | **NEW (minimal)** — `BIZ_KEY`, `APPR_SEQ_NO`, `CCY_CD`, `TITL`, `REG_ID`. Reused by visibility filter + currency resolution. |
| 9 | `OVRS_PYMN_SUMR` | (Overseas Payment summary) | **NEW (minimal)** — `BIZ_KEY`, `APPR_SEQ_NO`, `CCY_CD`, `TITL`, `REG_ID`. |
| 10 | `CARD_TRNS_HSTR` | (Corporate Card) | **MINIMAL — already partially seeded**. The local DB has the demo-card seed from V58/V72; this MR ensures `BIZ_KEY`, `APPR_SEQ_NO`, `TRSC_AMT`, `CCY_CD`, `REG_ID` are reachable. |
| 11 | `BUSA_DOC_MSTR` | (Vendor/Expense scheduled docs) | **NEW (minimal)** — for the `PAY_YN` computation. May be omitted if the refactor drops `PAY_YN` from the wire (see D6 in [design-backend.md](design-backend.md) §13 — currently kept). |
| 12 | `SALY_BOOK_MSTR` | (Payroll) | **NEW (minimal)** — same reason as 11. |

The four EXPT_RESOL_* tables form a self-contained cluster (FKs internal to themselves + outward to `BUSI_INFM`). The "minimal" sibling tables are added to satisfy `spring.jpa.hibernate.ddl-auto = validate` and the JPQL JOINs in [design-backend.md](design-backend.md) §7.

### 2.2 · Indexes & constraints (per DEV `pg_indexes` / `pg_constraint`)

`EXPT_RESOL_MSTR`:
- PK `expt_resol_mstr_pk` on `(biz_key, appr_seq_no)`.
- FK `expt_resol_mstr_fk01` → `busi_infm(biz_key)` — already enforced in refactor (V11+).
- CHECK `expt_resol_mstr_chk01`: `appr_dv ∈ {00,10,20,21,30,31,32,33,34,35,40,50,60,70}`.
- CHECK `expt_resol_mstr_chk02`: `appr_sts ∈ {0,1,2,4,9}`.
- CHECK `expt_resol_mstr_chk03/04`: `del_yn`, `hand_reg_yn ∈ {Y,N}`.

`EXPT_RESOL_APPRVR_INFM`:
- PK `pk_expt_resol_apprvr` on `(biz_key, appr_seq_no, apprvr_id)`.
- **No FK to `expt_resol_mstr`** (legacy gap). The migration preserves the gap to avoid breaking cascade-delete ordering. Document as deferred FK in migration trailing comment.

`EXPT_RESOL_FILE`:
- PK on `(biz_key, appr_seq_no, file_id)`.
- FK `expt_resol_file_fk01` → `expt_resol_mstr(biz_key, appr_seq_no)`.

`EXPT_RESOL_PAY_EVID_FILE`:
- PK on `(biz_key, appr_seq_no, evid_file_id)`.
- FK `expt_resol_pay_evid_file_fk01` → `expt_resol_mstr(biz_key, appr_seq_no)`.

Triggers: none on any of the four core tables (DEV `pg_trigger` returned 0 user triggers). No legacy `BEFORE INSERT` audit-stamping helpers to port.

### 2.3 · Helper functions referenced

Per [discovery.md](discovery.md) §3.7 — `wb_get_appr_stts`, `wb_get_expt_resol_mstr_doc_nm`, `wb_get_memo_cont`, `wb_get_memo_color`, `wb_get_translate_text`.

**None of these will be ported as new `wb_*` PL/pgSQL helpers** per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"DB Function / Legacy Function Policy". Their behaviour is reimplemented in Java (status derivation in service; type label as enum + i18n; memo dropped; translate-search dropped). See [design-backend.md](design-backend.md) §13 D2/D3/D4/D5.

The single grandfathered helper `wb_format_acct_nick_nm` (V44) is unrelated. The already-ported `wb_get_next_val(...)` (V25/V26/V27/V66/V82) handles sequence generation if/when a create endpoint lands — no additional branch needed for `EXPT_RESOL_MSTR.APPR_SEQ_NO` because the list path is read-only.

### 2.4 · Sequence generation (`wb_get_next_val`)

Run on DEV: `SELECT wb_get_next_val('EXPT_RESOL_MSTR', 'appr_seq_no', NULL, NULL, NULL)` — returns a non-NULL value. Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Key-generation rule for migrated legacy tables" Sprint 1+ rule, this means **future** create-endpoint MR must route through `wbGetNextValService.next(...)` to mint `appr_seq_no`. Out of scope here (list endpoint does not insert).

---

## 3 · Drafted migration file content (for review — DO NOT place yet)

> **Where it will go (after review):** `wecms-api/src/main/resources/db/migration/V{N}__approval_box_schema.sql` where `V{N}` is the next free integer at the time of placement. As of 2026-05-13 the last applied migration is `V83` (per `wecms-api/src/main/resources/db/migration/` listing) — the new file would be **V84** if placed today. Confirm before placement; another in-flight MR may take that slot.

```sql
-- =============================================================================
-- Approval Box — schema parity port from DEV (wecmsvndev).
-- Mirrors:
--   - WECMS DEV/WECMS/src/wabooks/entity/ExptResolMstr.java
--   - WECMS DEV/WECMS/src/wabooks/entity/ExptResolApprvrInfm.java
--   - WECMS DEV/WECMS/src/wabooks/entity/ExptResolFile.java
--   - WECMS DEV/WECMS/src/wabooks/entity/ExptResolPayEvidFile.java
-- Source-of-truth columns verified via information_schema queries against DEV
-- on 2026-05-13. Constraints copied from pg_constraint. No new wb_* helper
-- functions per wecms-api/CLAUDE.md §"DB Function / Legacy Function Policy".
-- =============================================================================

CREATE TABLE IF NOT EXISTS EXPT_RESOL_MSTR (
    BIZ_KEY               VARCHAR(20)   NOT NULL,
    APPR_SEQ_NO           VARCHAR(15)   NOT NULL,
    APPR_DV               VARCHAR(2)    NOT NULL,
    APPR_STS              VARCHAR(2)    NOT NULL DEFAULT '0',
    DRAFT_DATE            VARCHAR(8)    NOT NULL,
    DRAFT_TIME            VARCHAR(6)    NOT NULL,
    DRAFT_USER_ID         VARCHAR(100)  NOT NULL,
    DRAFT_USER_NM         VARCHAR(100)  NOT NULL,
    DRAFT_USER_POS_NM     VARCHAR(100),
    DRAFT_USER_DEPT_CD    VARCHAR(20),
    DRAFT_USER_DEPT_NM    VARCHAR(100),
    DRAFT_USER_SITE_CD    VARCHAR(20)   NOT NULL,
    DRAFT_USER_SITE_NM    VARCHAR(200)  NOT NULL,
    TITL                  VARCHAR(200)  NOT NULL,
    CONT                  TEXT,
    DOC_NO                VARCHAR(50)   NOT NULL,
    DOC_NM                VARCHAR(100)  NOT NULL,
    TOT_PAY_VND_AMT       NUMERIC                DEFAULT 0,
    TOT_PAY_USD_AMT       NUMERIC                DEFAULT 0,
    PAY_DATE              VARCHAR(8),
    DEL_YN                VARCHAR(1)    NOT NULL DEFAULT 'N',
    HAND_REG_YN           VARCHAR(1)    NOT NULL DEFAULT 'Y',
    REG_ID                VARCHAR(100)  NOT NULL,
    REG_DTM               VARCHAR(14)   NOT NULL,
    MOD_ID                VARCHAR(100),
    MOD_DTM               VARCHAR(14)   NOT NULL,
    APPR_BOX_USE_YN       VARCHAR(1)    NOT NULL DEFAULT 'N',
    APPR_BOX_DISP_CNT     VARCHAR(1),
    APPR_DAT_DISP_YN      VARCHAR(1)    NOT NULL DEFAULT 'N',
    CMPN_LOGO_DISP_YN     VARCHAR(1)    NOT NULL DEFAULT 'N',
    REG_DISP_YN           VARCHAR(1)    NOT NULL DEFAULT 'N',
    CMPN_LOGO             VARCHAR(50),
    USER_NM               VARCHAR(100),
    PAY_END_YN            VARCHAR(1)    NOT NULL DEFAULT 'N',
    PAY_END_DTM           VARCHAR(14),
    PAY_END_NM            VARCHAR(100),
    TOT_PAY_KHR_AMT       NUMERIC,
    PAY_REQ_DATE          VARCHAR(8),
    REF_DATA_CNT          NUMERIC,
    PAY_YN                CHAR(1)                DEFAULT 'N',
    ADV_PAYM_SEQ          NUMERIC,
    ADV_PAYM_NM           VARCHAR(50),
    BANK_CD               VARCHAR(20),
    BANK_NM               VARCHAR(100),
    ACCT_NO               VARCHAR(100),
    APLY_EXCH_RATE        NUMERIC,
    APPR_TYPE_CD          VARCHAR(2)    NOT NULL DEFAULT '00',
    REG_CHNL              VARCHAR(3)             DEFAULT 'PC',
    PC_MOD_YN             VARCHAR(1)             DEFAULT 'Y',
    APPR_FUND_TP          CHAR(1),
    CONSTRAINT EXPT_RESOL_MSTR_PK   PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO),
    CONSTRAINT EXPT_RESOL_MSTR_FK01 FOREIGN KEY (BIZ_KEY) REFERENCES BUSI_INFM(BIZ_KEY),
    CONSTRAINT EXPT_RESOL_MSTR_CHK01 CHECK (APPR_DV  IN ('00','10','20','21','30','31','32','33','34','35','40','50','60','70')),
    CONSTRAINT EXPT_RESOL_MSTR_CHK02 CHECK (APPR_STS IN ('0','1','2','4','9')),
    CONSTRAINT EXPT_RESOL_MSTR_CHK03 CHECK (DEL_YN  IN ('Y','N')),
    CONSTRAINT EXPT_RESOL_MSTR_CHK04 CHECK (HAND_REG_YN IN ('Y','N'))
);

CREATE TABLE IF NOT EXISTS EXPT_RESOL_APPRVR_INFM (
    BIZ_KEY        VARCHAR(20)   NOT NULL,
    APPR_SEQ_NO    VARCHAR(15)   NOT NULL,
    APPRVR_ID      VARCHAR(100)  NOT NULL,
    APPRVR_STTS    CHAR(1)       NOT NULL,
    APPRVR_NM      VARCHAR(100)  NOT NULL,
    APPR_DTM       VARCHAR(14),
    REG_ID         VARCHAR(100),
    REG_DTM        VARCHAR(14)   NOT NULL,
    MOD_ID         VARCHAR(100)  NOT NULL,
    MOD_DTM        VARCHAR(14)   NOT NULL,
    READ_YN        VARCHAR(1)    NOT NULL DEFAULT 'N',
    APPRVR_LINE_NO NUMERIC,
    APPR_LINE_NO   NUMERIC,
    CONSTRAINT PK_EXPT_RESOL_APPRVR PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO, APPRVR_ID)
    -- Deferred: legacy schema has no FK from this table to EXPT_RESOL_MSTR.
    -- Preserve the gap; cascade-delete order is handled in application code.
);

CREATE TABLE IF NOT EXISTS EXPT_RESOL_FILE (
    BIZ_KEY       VARCHAR(20)   NOT NULL,
    APPR_SEQ_NO   VARCHAR(15)   NOT NULL,
    FILE_ID       VARCHAR(100)  NOT NULL,
    ORG_FILE_NM   VARCHAR(500)  NOT NULL,
    SAVE_FILE_NM  VARCHAR(500)  NOT NULL,
    NOTE          VARCHAR(255),
    REG_ID        VARCHAR(100)  NOT NULL,
    REG_DTM       VARCHAR(14)   NOT NULL,
    REG_NM        VARCHAR(100)  NOT NULL,
    FILE_SIZE     VARCHAR(100),
    CONSTRAINT EXPT_RESOL_FILE_PK   PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO, FILE_ID),
    CONSTRAINT EXPT_RESOL_FILE_FK01 FOREIGN KEY (BIZ_KEY, APPR_SEQ_NO)
        REFERENCES EXPT_RESOL_MSTR(BIZ_KEY, APPR_SEQ_NO)
);

CREATE TABLE IF NOT EXISTS EXPT_RESOL_PAY_EVID_FILE (
    BIZ_KEY       VARCHAR(20)   NOT NULL,
    APPR_SEQ_NO   VARCHAR(15)   NOT NULL,
    EVID_FILE_ID  VARCHAR(100)  NOT NULL,
    ORG_FILE_NM   VARCHAR(500)  NOT NULL,
    SAVE_FILE_NM  VARCHAR(500)  NOT NULL,
    NOTE          VARCHAR(255),
    REG_ID        VARCHAR(100)  NOT NULL,
    REG_DTM       VARCHAR(14)   NOT NULL,
    REG_NM        VARCHAR(100)  NOT NULL,
    FILE_SIZE     VARCHAR(100),
    CONSTRAINT EXPT_RESOL_PAY_EVID_FILE_PK   PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO, EVID_FILE_ID),
    CONSTRAINT EXPT_RESOL_PAY_EVID_FILE_FK01 FOREIGN KEY (BIZ_KEY, APPR_SEQ_NO)
        REFERENCES EXPT_RESOL_MSTR(BIZ_KEY, APPR_SEQ_NO)
);

-- ----------------------------------------------------------------------------
-- Minimal sibling-table columns for JOINs (full ports land with their features).
-- IF NOT EXISTS guards keep the migration idempotent when those features have
-- already shipped their own DDL.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS BLTB_POST_INFM (
    BIZ_KEY          VARCHAR(20)  NOT NULL,
    BLTB_CD          VARCHAR(15)  NOT NULL,
    POST_SEQ_NO      NUMERIC      NOT NULL,
    APPR_SEQ_NO      VARCHAR(15),
    POST_TITLE       VARCHAR(200),
    REF_BLTB_CD      VARCHAR(15),
    REF_POST_SEQ_NO  NUMERIC,
    REG_ID           VARCHAR(100),
    REG_DTM          VARCHAR(14),
    CONSTRAINT BLTB_POST_INFM_PK PRIMARY KEY (BIZ_KEY, BLTB_CD, POST_SEQ_NO)
);

CREATE TABLE IF NOT EXISTS OVRS_PYMN_MSTR (
    BIZ_KEY        VARCHAR(20)  NOT NULL,
    APPR_SEQ_NO    VARCHAR(15)  NOT NULL,
    TRNF_CCY_CD    VARCHAR(3),
    REG_ID         VARCHAR(100),
    REG_DTM        VARCHAR(14),
    CONSTRAINT OVRS_PYMN_MSTR_PK PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO)
);

CREATE TABLE IF NOT EXISTS SCDUL_PYMN_SUMR (
    BIZ_KEY        VARCHAR(20)  NOT NULL,
    APPR_SEQ_NO    VARCHAR(15)  NOT NULL,
    CCY_CD         VARCHAR(3),
    TITL           VARCHAR(200),
    REG_ID         VARCHAR(100),
    REG_DTM        VARCHAR(14),
    CONSTRAINT SCDUL_PYMN_SUMR_PK PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO)
);

CREATE TABLE IF NOT EXISTS OVRS_PYMN_SUMR (
    BIZ_KEY        VARCHAR(20)  NOT NULL,
    APPR_SEQ_NO    VARCHAR(15)  NOT NULL,
    CCY_CD         VARCHAR(3),
    TITL           VARCHAR(200),
    REG_ID         VARCHAR(100),
    REG_DTM        VARCHAR(14),
    CONSTRAINT OVRS_PYMN_SUMR_PK PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO)
);

-- CARD_TRNS_HSTR is already present in the local DB (V58/V72 seed). No DDL here.

-- ----------------------------------------------------------------------------
-- View Setting seed for the screen — WEB_SVC_ID = 'approval-box'.
-- Default visible columns + sortability. Columns + labels mirror discovery §6.
-- ----------------------------------------------------------------------------
INSERT INTO SYS_SCR_OPTION_WECMS (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, DISP_ORD, DISP_YN, SORT_YN, FIELD_NM_KEY)
VALUES
    ('approval-box','default','CHECKBOX',     1, 'Y', 'N', 'approval_box.column.checkbox'),
    ('approval-box','default','REG_DTM',      2, 'Y', 'Y', 'approval_box.column.date'),
    ('approval-box','default','DOC_NM',       3, 'Y', 'Y', 'approval_box.column.type'),
    ('approval-box','default','DOC_NO',       4, 'Y', 'Y', 'approval_box.column.doc_no'),
    ('approval-box','default','TITL',         5, 'Y', 'Y', 'approval_box.column.title'),
    ('approval-box','default','TOT_PAY_AMT',  6, 'Y', 'Y', 'approval_box.column.amount'),
    ('approval-box','default','PAY_END_YN',   7, 'Y', 'Y', 'approval_box.column.status'),
    ('approval-box','default','PAY_DATE',     8, 'Y', 'Y', 'approval_box.column.scheduled_date'),
    ('approval-box','default','DRAFT_USER_NM',9, 'Y', 'Y', 'approval_box.column.requestor'),
    ('approval-box','default','APPRER_NM',   10, 'N', 'Y', 'approval_box.column.approver'),
    ('approval-box','default','CURRENCY',    11, 'N', 'Y', 'approval_box.column.currency'),
    ('approval-box','default','ETC',         12, 'Y', 'N', 'approval_box.column.etc')
ON CONFLICT (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID) DO NOTHING;

-- Tenant scope: VN_VNM0004_000001 only — HAENGSUNG (VN_VNM0004_000014) is product-type 'H' and
-- approval-box surfaces six payment types regardless of product type, so no per-tenant override seeded.

-- ----------------------------------------------------------------------------
-- Real DEV seed: pull current DEV rows for VN_VNM0004_000001 (~325 rows in
-- EXPT_RESOL_MSTR + their EXPT_RESOL_APPRVR_INFM children). The Sprint 1+
-- "real DEV data" rule applies — see V22/V29/V83 precedents.
-- The seed script is generated separately into Claude Code (AI)/sql/ via the
-- canonical mcp__postgres_dev_server_jexframe__query → INSERT-script pipeline. NOT inlined
-- here to keep this migration file diff-reviewable.
-- ----------------------------------------------------------------------------
```

Trailing comments mark every deferred concern:

- `EXPT_RESOL_APPRVR_INFM` — no FK to master (legacy gap).
- Sibling tables intentionally minimal — full ports owned by Banking / Card / Bulletin features.
- Real DEV seed delivered as a sibling script per the V22/V29/V83 precedent.

---

## 4 · Seed strategy

Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Legacy Schema Parity Rule" item 5 — "Seed real DEV rows for the tenant `VN_VNM0004_000001`. Pull rows from DEV via the `postgres` MCP; never invent demo data when real DEV data exists."

DEV row count (verified via MCP today):

| Table | Row count for `VN_VNM0004_000001` |
|---|---|
| `expt_resol_mstr` | 325 |
| `expt_resol_apprvr_infm` | (joined per master, fetch on seed gen) |
| `expt_resol_file` | (joined per master, fetch on seed gen) |
| `expt_resol_pay_evid_file` | (joined per master, fetch on seed gen) |

Sample real rows fetched on 2026-05-13 (5 most recent, `BIZ_KEY = VN_VNM0004_000001`):

| appr_seq_no | appr_dv | appr_sts | appr_type_cd | doc_no | titl | reg_dtm |
|---|---|---|---|---|---|---|
| `1540` | `35` (Internal Transfer) | `2` | `02` (Rejected) | `VO_260511_002` | Test | 20260511075547 |
| `1539` | `35` | `2` | `01` (Approved) | `VO_260511_001` | Testing | 20260511071347 |
| `1538` | `35` | `2` | `01` | `VO_260421_001` | Testing New feature | 20260421064741 |
| `1537` | `31` (Expense) | `2` | `01` | `VO_260325_005` | Testing | 20260325093212 |
| `1536` | `34` (Overseas) | `2` | `01` | `VO_260325_004` | Testing | 20260325084948 |

The seed script (separate `.sql` file generated under [Claude Code (AI)/sql/](../../../../../Claude%20Code%20(AI)/sql/) at file-placement time) will `INSERT … ON CONFLICT DO NOTHING` rows for the 325 masters + their approver / file children, scoped to `VN_VNM0004_000001`.

---

## 5 · `application*.yml` changes

None for this MR. Database parameters already point to `localhost:5434/wecms_dev` per workspace [CLAUDE.md](../../../../../CLAUDE.md) §Environments.

---

## 6 · Hibernate ddl-auto and validation

After Flyway applies the migration, `spring.jpa.hibernate.ddl-auto = validate` will run. The migration must produce columns matching the Java `@Column(name = …)` declarations in [design-backend.md](design-backend.md) §8 exactly — capitalisation, type, nullability — or boot will fail with `SchemaManagementException`. The drafted SQL above does so (verified column-by-column against the DEV `information_schema` query result captured during discovery).

---

## 7 · Risks & open questions

| # | Risk / question | Mitigation |
|---|---|---|
| Q1 | Migration slot — V84 may be taken | Lock the slot via merge sequence; the migration file content can be reused verbatim. |
| Q2 | Sibling tables `BLTB_POST_INFM` / `OVRS_PYMN_MSTR` / `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` may collide with future full ports | Use `CREATE TABLE IF NOT EXISTS` (idempotent). Subsequent full-port migration will `ALTER TABLE … ADD COLUMN IF NOT EXISTS` to extend. |
| Q3 | `EXPT_RESOL_APPRVR_INFM` has no FK to master | Preserved (legacy gap). Application code enforces cascade order on delete. |
| Q4 | Helper functions on DEV (`wb_get_appr_stts`, …) — refactor cannot use them | Reimplemented in Java per [design-backend.md](design-backend.md) §5 (`deriveApprStts`). |
| Q5 | Seed volume (325 rows) | Ships as a separate generated `.sql` script under [Claude Code (AI)/sql/](../../../../../Claude%20Code%20(AI)/sql/) to keep the migration diff small. |

---

## 8 · Cross-references

- [discovery.md](discovery.md) §3 — table inventory & schema details
- [design-backend.md](design-backend.md) §8 — Java entity layout matching this schema
- [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Legacy Schema Parity Rule"
- [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"DB Function / Legacy Function Policy"
- [.claude/rules/database.md](../../../../../.claude/rules/database.md) — DB access governance
