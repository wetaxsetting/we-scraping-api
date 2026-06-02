# Settings > Firm Banking History — DB Design

**Refactor screen ID:** `firm-banking-history`
**Feature spec:** [../../features/settings/firm-banking-history.md](../../../features/settings/firm-banking-history.md)
**Migration mapping:** [../../migration/firm-banking-history-mapping.md](../../migration/firm-banking-history-mapping.md)
**JEX reference:** `IDO.TB_FIRM_TRAN_R002.xml` (multi-leg UNION) — read-only

> No DDL is executed in Phase 1. The `postgres_dev` MCP is read-only. This file specifies the migrations Phase 2 will author and apply.

---

## 1. Source tables (legacy DEV — schema captured 2026-05-08)

Schema captured live from `wecmsvndev` via the read-only `postgres_dev` MCP. Refactor mirrors **column-for-column**, with the documented intentional divergences from [docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md § 3a–3b](../../audits/db-parity-audit-2026-04-30.md) (CHAR→VARCHAR, varchar(14)→TIMESTAMPTZ kept as varchar(14) for the legacy `REG_DTM` columns to keep behaviour parity).

### 1.1 `TB_FIRM_TRAN` — Firm Banking gateway log (Korean firm-banking leg)

PK: `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` — `pk_tb_firm_tran` UNIQUE btree on DEV.
DEV row count: **10 097** (2026-05-08).

| Column | Type | NN | Used by screen | Notes |
|---|---|---|---|---|
| `TRAN_DT` | VARCHAR(8) | Y | yes (PK + composes `TARN_DTM`) | `YYYYMMDD` — KR-side |
| `BIZ_KEY` | VARCHAR(20) | Y | yes (tenant) | |
| `OUT_BANK_CD` | VARCHAR(15) | Y | yes (`DEP_BANK_CD`) | Withdrawal bank code |
| `TRAN_SEQ` | VARCHAR(10) | Y | yes (`SEQ_NO`) | |
| `BANK_PRTN_ID` | VARCHAR(20) | Y | — | |
| `TRAN_TP` | CHAR(3) | Y | yes (`SVC_NM`, `JOB`) | Values observed in DEV: `201`, `202`, `203` — Inquiry Recipient / Transfer Payment / Inquiry Transfer Result |
| `TRAN_TM` | CHAR(6) | Y | yes (composes `TARN_DTM` via `kr_to_vn_time(TRAN_DT‖TRAN_TM)`) | KR-side, HHMMSS |
| `VAN_ORG_CD` | VARCHAR(20) | Y | yes (`VAN_ORG_CD`) | |
| `RS_CD` | VARCHAR(4) | — | yes (`RSLT_CD`, `RSLT_MSG`) | Frontend resolves message via DICTIONARY_VAN |
| `RS_MSG` | VARCHAR(200) | — | — | Free-text response message; legacy IDO ignores it for `RSLT_MSG` (uses `RS_CD` directly) |
| `WDRL_ACT_NO` | VARCHAR(100) | — | — | |
| `TAR_BANK_CD` | VARCHAR(15) | — | — | |
| `FINC_MDA_DIV` | VARCHAR(1) | — | — | |
| `FINC_MDA_NO` | VARCHAR(100) | — | — | |
| `FINC_MDA_OWNER_NM` | VARCHAR(150) | — | — | |
| `ANT_DIV_CD` | CHAR(5) | — | — | |
| `TRSC_CCY_CD` | CHAR(3) | — | — | |
| `TRAN_AMT` | NUMERIC(23,2) | — | — | |
| `TRAN_USAG` | VARCHAR(200) | — | — | |
| `BAL_AMT` | NUMERIC(23,2) | — | — | |
| `BAL_AMT_SIGN` | CHAR(1) | — | — | |
| `RQ_DTTM` | VARCHAR(14) | — | — | |
| `RS_DTTM` | VARCHAR(14) | — | — | |
| `PROC_STAT` | CHAR(1) | — | — | |
| `ORG_TRAN_DT` | VARCHAR(8) | — | — | |
| `ORG_TRAN_SEQ` | VARCHAR(50) | — | — | |
| `ORG_PROC_TM` | VARCHAR(6) | — | — | |
| `ORG_RESP_CD` | VARCHAR(4) | — | — | |
| `ORG_RESP_MSG` | VARCHAR(150) | — | — | |
| `WDRL_ACCT_RMRK` | VARCHAR(150) | — | — | |
| `TAR_ACCT_RMRK` | VARCHAR(150) | — | — | |
| `WITHD_CCY_CD` | VARCHAR(3) | — | — | |
| `DEP_CCY_CD` | VARCHAR(3) | — | — | |
| `APL_EXC_RATE` | NUMERIC(23,2) | — | — | |
| `WITHD_AMT` | NUMERIC(23,2) | — | — | |
| `DEP_AMT` | NUMERIC(23,2) | — | — | |

Per the [legacy schema parity rule](../../../../CLAUDE.md), entity `TbFirmTran` mirrors **all 30 columns** even though only 8 are read by this screen — same precedent as `BusiInfm`'s 45-column entity.

### 1.2 `NAPAS_REMIT_TRNS_HSTR` — NAPAS funds-transfer audit (already in V13)

PK: `(BIZ_KEY, SEQ_NO)` — `napas_remit_trns_hstr_pk`.
DEV row count: **290**.
Columns (already mirrored by V13 per [audit § 1](../../audits/db-parity-audit-2026-04-30.md#1-full-local-table-list-23-tables)).

Used by this screen: `BIZ_KEY`, `SEQ_NO`, `OUT_BANK_CODE`, `REG_DTM`, `TRNS_STTS`, `TRNS_MSG`.

### 1.3 `NXSIAS_MODULE_TRAN_LOG_HIS` — NXISAS / GDT bridge audit log

PK on DEV: **none observed** (verified — no rows in `pg_indexes`). This is unusual; see [open question 5](../../../features/settings/firm-banking-history.md#11-open-questions--blockers).
DEV row count: **77 250**.

| Column | Type | NN | Used by screen |
|---|---|---|---|
| `UUID` | VARCHAR(32) | — | yes (`SEQ_NO`) — primary correlation id with NXISAS |
| `REG_DTM` | VARCHAR(14) | — | yes (`TARN_DTM`) — VN local time at insert |
| `NXISAS_CLASS` | VARCHAR(100) | — | — |
| `NXISAS_MODULE` | VARCHAR(100) | — | yes (`DEP_BANK_CD`) |
| `NXISAS_JOB` | VARCHAR(100) | — | yes (`JOB`, `SVC_TYPE='B'` discriminator via `BUSSINESS_REGISTRATION_*`) |
| `NXISAS_TYPE` | VARCHAR(100) | — | yes (`SVC_TYPE='G'` discriminator via `E-INVOICE_*_INQUIRY`) |
| `NXISAS_ERR_CODE` | VARCHAR(8) | — | yes (`RSLT_CD`) |
| `NXISAS_ERR_CONT` | VARCHAR(1000) | — | yes (`RSLT_MSG`) |
| `REQ_DATA` | VARCHAR(1000) | — | yes (`REQ_DATA`) — the e-Invoice symbol/no JSON or business-reg `search` JSON the popover decodes |
| `BIZ_KEY` | VARCHAR(20) | — | yes (tenant filter) |
| `REG_ID` | VARCHAR(100) | — | — |

V59 will declare a composite PK `(BIZ_KEY, REG_DTM, UUID)` — the natural choice. Confirm against any DEV-side write trigger before applying.

### 1.4 `EXT_TRAN_CALL_HIS` — generic external-call audit (already in V16)

PK: `(SVC_ID, REF_DATE, SEQ_NO)`.
DEV row count: **24 019** (global; not tenant-scoped — `BIZ_KEY` is nullable).

Used by this screen — only the rows where `SVC_ID ∈ {BALANCE_INQUIRY, REAL_TIME_ONLINE_INQUIRY, CARD_INQUIRY}`. See [feature spec § 2 Scope](../../../features/settings/firm-banking-history.md#2-scope) for the per-`SVC_ID` `SVC_NM` mapping.

The other 27 `SVC_ID` values observed in DEV (`BANK_*`, `BANKING_CREDENTIAL`) are used by **other** scraping flows and are NOT shown on this screen — consistent with `IDO.TB_FIRM_TRAN_R002` filter (`WHERE ETH.SVC_ID IN ('BALANCE_INQUIRY', 'REAL_TIME_ONLINE_INQUIRY','CARD_INQUIRY')`).

---

## 2. Refactor view: `V_FIRM_BANKING_HISTORY`

The five-leg `UNION ALL` from `IDO.TB_FIRM_TRAN_R002` is ported verbatim to a Postgres view. The view's column list mirrors the IDO `<out>` exactly — see [feature spec § 6.2](../../../features/settings/firm-banking-history.md#62-read-only-view-v_firm_banking_history).

### 2.1 View definition (sketch — final SQL written in V60)

```sql
CREATE VIEW V_FIRM_BANKING_HISTORY AS
SELECT
    kr_to_vn_time(TFT.TRAN_DT || TFT.TRAN_TM)                 AS TARN_DTM,
    'F'                                                        AS SVC_TYPE,
    TFT.TRAN_TP                                                AS SVC_NM,
    TFT.TRAN_SEQ                                               AS SEQ_NO,
    wb_get_napas_bank_nm(TFT.OUT_BANK_CD) || ' - ' || TFT.OUT_BANK_CD  AS DEP_BANK_CD,
    TFT.RS_CD                                                  AS RSLT_CD,
    TFT.RS_CD                                                  AS RSLT_MSG,
    TFT.TRAN_TP                                                AS JOB,
    NULL::VARCHAR(1000)                                        AS REQ_DATA,
    TFT.BIZ_KEY,
    TFT.VAN_ORG_CD
FROM TB_FIRM_TRAN TFT

UNION ALL

SELECT NMTLH.REG_DTM,
       'G',
       CASE WHEN NMTLH.NXISAS_TYPE = 'E-INVOICE_SALES_INQUIRY'
            THEN 'Sale E-Invoice Inquiry'
            ELSE 'Expense E-Invoice Inquiry' END,
       NMTLH.UUID,
       NMTLH.NXISAS_MODULE,
       NMTLH.NXISAS_ERR_CODE,
       NMTLH.NXISAS_ERR_CONT,
       NMTLH.NXISAS_JOB,
       NMTLH.REQ_DATA,
       NMTLH.BIZ_KEY,
       NULL
FROM NXSIAS_MODULE_TRAN_LOG_HIS NMTLH
WHERE NMTLH.NXISAS_TYPE IN ('E-INVOICE_SALES_INQUIRY','E-INVOICE_EXPENSE_INQUIRY')

UNION ALL

SELECT NMTLH.REG_DTM,
       'B',
       'Vietnam Business Inquiry',
       NMTLH.UUID,
       NMTLH.NXISAS_MODULE,
       NMTLH.NXISAS_ERR_CODE,
       NMTLH.NXISAS_ERR_CONT,
       NMTLH.NXISAS_JOB,
       NMTLH.REQ_DATA,
       NMTLH.BIZ_KEY,
       NULL
FROM NXSIAS_MODULE_TRAN_LOG_HIS NMTLH
WHERE NMTLH.NXISAS_JOB IN ('BUSSINESS_REGISTRATION_DETAIL','BUSSINESS_REGISTRATION_LIST')

UNION ALL

SELECT NRTH.REG_DTM,
       'S',
       'Funds Transfer (Scraping)',
       NRTH.SEQ_NO::VARCHAR,
       wb_get_bank_nm(NRTH.OUT_BANK_CODE,'s') || ' - ' || NRTH.OUT_BANK_CODE,
       NRTH.TRNS_STTS,
       NRTH.TRNS_MSG,
       'TRANSFER',
       NULL,
       NRTH.BIZ_KEY,
       NULL
FROM NAPAS_REMIT_TRNS_HSTR NRTH

UNION ALL

SELECT ETH.REG_DTM,
       'S',
       CASE ETH.SVC_ID
            WHEN 'BALANCE_INQUIRY'          THEN 'Scraping Balance'
            WHEN 'REAL_TIME_ONLINE_INQUIRY' THEN 'Scraping Transaction'
            WHEN 'CARD_INQUIRY'             THEN 'Scraping Card Transaction'
       END,
       ETH.SEQ_NO::VARCHAR,
       wb_get_bank_nm(ETH.BANK_CD,'s') || ' - ' || ETH.BANK_CD,
       ETH.RSLT_CD,
       ETH.ERR_CONT,
       CASE ETH.SVC_ID
            WHEN 'BALANCE_INQUIRY'          THEN 'BALANCE'
            WHEN 'REAL_TIME_ONLINE_INQUIRY' THEN 'TRANSACTION'
            WHEN 'CARD_INQUIRY'             THEN 'CARD_USING'
       END,
       NULL,
       ETH.BIZ_KEY,
       NULL
FROM EXT_TRAN_CALL_HIS ETH
WHERE ETH.SVC_ID IN ('BALANCE_INQUIRY','REAL_TIME_ONLINE_INQUIRY','CARD_INQUIRY');
```

This is **byte-for-byte parity** with `IDO.TB_FIRM_TRAN_R002.xml` modulo:

- The `WHERE BIZ_KEY = :bizKey` predicate moves from inside each leg to the outer SELECT in the repository call (Postgres pushes it down via `UNION ALL` predicate pushdown — verify in EXPLAIN).
- The column list is rewritten in canonical order matching the WSVC `subRule`.
- Aliasing typos in JEX (`SVN_NM` for two of five legs) are corrected to `SVC_NM`. The IDO XML's `<out>` already labels them `SVC_NM` — the inline column alias is cosmetic; the resultset column name is the IDO's `<out>` id.

### 2.2 Why a view (not JPQL `UNION` or five repositories)

See [feature spec § 6.4](../../../features/settings/firm-banking-history.md#64-why-a-view-not-jpql-union).

---

## 3. Indexes

### 3.1 Existing source-table indexes (DEV — captured 2026-05-08)

| Table | Index | Definition |
|---|---|---|
| `TB_FIRM_TRAN` | `pk_tb_firm_tran` | UNIQUE `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` |
| `NAPAS_REMIT_TRNS_HSTR` | `napas_remit_trns_hstr_pk` | UNIQUE `(BIZ_KEY, SEQ_NO)` |
| `EXT_TRAN_CALL_HIS` | `ext_tran_call_his_pk` | UNIQUE `(SVC_ID, REF_DATE, SEQ_NO)` |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | none | none |

### 3.2 New indexes proposed (Phase 2 — only if EXPLAIN warrants)

The view is queried with `WHERE BIZ_KEY = ? AND TARN_DTM BETWEEN ? AND ? [AND SVC_TYPE = ?] [AND SEQ_NO ILIKE ?]`. Each leg's plan should hit an index that covers `(BIZ_KEY, REG_DTM)` or equivalent.

| Table | Proposed index | Rationale |
|---|---|---|
| `TB_FIRM_TRAN` | `ix_tb_firm_tran_biz_dt (BIZ_KEY, TRAN_DT)` | Date-range filter — current PK leads with `TRAN_DT` so `WHERE BIZ_KEY=? AND TRAN_DT BETWEEN ?` doesn't use the PK index efficiently. Verify in Phase 2; only add if EXPLAIN shows a sequential scan. |
| `NAPAS_REMIT_TRNS_HSTR` | `ix_napas_remit_trns_hstr_biz_dtm (BIZ_KEY, REG_DTM)` | Same — current PK leads with `BIZ_KEY` but doesn't include `REG_DTM`. |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | `ix_nxsias_biz_dtm (BIZ_KEY, REG_DTM)` + `ix_nxsias_type (NXISAS_TYPE)` partial index for the GDT/Business legs | No PK exists today; the indexes ARE the access path. |
| `EXT_TRAN_CALL_HIS` | `ix_ext_tran_biz_dtm (BIZ_KEY, REG_DTM, SVC_ID)` partial `WHERE SVC_ID IN (...)` | Filtered subset; partial index keeps it tight. |

**Decision rule (per [audit doc](../../audits/db-parity-audit-2026-04-30.md)):** add only after EXPLAIN ANALYZE on representative data shows it's needed. R&D scale (≤30 days, single tenant) probably does not need them; they become essential at production scale.

---

## 4. Constraints, defaults, triggers

| Concern | Status |
|---|---|
| BIZ_KEY tenant isolation | Enforced at query time via repository predicate; see [audit § 4](../../audits/db-parity-audit-2026-04-30.md#4-constraint-gaps). No DB-level enforcement (matches DEV). |
| Audit columns (`REG_ID/REG_DTM/MOD_ID/MOD_DTM`) | Read-only screen — no writes, no `BaseEntity` extension. The view's `REG_DTM` columns come from the source legs unchanged. |
| Triggers | None on this read path. The deferred audit-log triggers from V24 ([audit § 5](../../audits/db-parity-audit-2026-04-30.md#5-trigger-parity)) are unchanged; this screen has no write surface so triggers do not fire from refactor activity. |
| `PROC_STAT` / `RS_CD` / `TRNS_STTS` value space | No CHECK constraints in DEV; refactor mirrors. Validation lives in the **frontend's** localized result-code map, not the DB. |
| `SVC_TYPE` enum (`F`/`S`/`G`/`B`) | View is the only place these literals exist — the constraint is the view definition itself. |

---

## 5. Planned Flyway migrations

> Migration numbers reserved provisionally. Final number = next free V-number at MR time.

| Migration | Filename | Purpose | Effects |
|---|---|---|---|
| V59 | `V59__firm_banking_history_source_tables.sql` | Add the two missing source tables: `TB_FIRM_TRAN`, `NXSIAS_MODULE_TRAN_LOG_HIS`. Mirror DEV column-for-column with the documented intentional CHAR→VARCHAR / NUMERIC→NUMERIC(N,M) divergences. | DDL (CREATE TABLE x2) |
| V60 | `V60__firm_banking_history_view_and_fns.sql` | Port `kr_to_vn_time(varchar)` and `wb_get_napas_bank_nm` (1-arg + 3-arg overloads) from `wecmsvndev.pg_proc`. Create `V_FIRM_BANKING_HISTORY` view. | DDL (CREATE FUNCTION x2 + CREATE VIEW) |
| V61 | `V61__firm_banking_history_view_setting_seed.sql` | Seed `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'firm-banking-history'`, `DYM_AREA_ID = 'default'` with the 7 FIELD_IDs (incl. `ETC` admin-pinned). `SIZE` from Figma. | DML (INSERT seed rows) |
| V62 | `V62__firm_banking_history_demo_seed.sql` | Optional — copy a representative slice from DEV (≤30 rows per source leg) for the configured tenant per the [Data Migration Rule](../../../../CLAUDE.md). Idempotent via `INSERT … ON CONFLICT DO NOTHING`. | DML (seed) |

**Order matters.** V59 must precede V60 (the view references the new tables). V60 must precede V61 (View Setting seed depends on the View Setting tables, which already exist from V30 — so no new dependency, but the screen needs to "exist" conceptually). V62 is independent of V61.

**Do not run any of these in Phase 1.** The `postgres_dev` MCP is read-only; the local DB is owned by Sonny's developer setup.

---

## 6. Java entity layer (Phase 2 sketch)

```java
// FirmBankingHistoryView.java
@Entity
@Immutable
@Table(name = "V_FIRM_BANKING_HISTORY")
public class FirmBankingHistoryView {

    @Id
    @AttributeOverrides({
        @AttributeOverride(name = "bizKey",   @Column(name = "BIZ_KEY")),
        @AttributeOverride(name = "tarnDtm",  @Column(name = "TARN_DTM")),
        @AttributeOverride(name = "svcType",  @Column(name = "SVC_TYPE")),
        @AttributeOverride(name = "seqNo",    @Column(name = "SEQ_NO"))
    })
    private FirmBankingHistoryViewId id;          // surrogate composite key sufficient for uniqueness

    @Column(name = "SVC_NM",      length = 50)   private String svcNm;
    @Column(name = "DEP_BANK_CD", length = 200)  private String depBankCd;
    @Column(name = "RSLT_CD",     length = 20)   private String rsltCd;
    @Column(name = "RSLT_MSG",    length = 2000) private String rsltMsg;
    @Column(name = "JOB",         length = 5000) private String job;
    @Column(name = "REQ_DATA",    length = 1000) private String reqData;
    @Column(name = "VAN_ORG_CD",  length = 20)   private String vanOrgCd;

    // BIZ_KEY is part of the @IdClass; tarnDtm + svcType + seqNo together are unique-enough
    // for views (rows are derived; if the underlying source has duplicates, they stay).
}
```

`@Immutable` (Hibernate) flags the entity as read-only — Hibernate will not flush state changes. Combined with the view being un-updatable in Postgres, this is a defence-in-depth read-only contract.

The PK choice (`BIZ_KEY + TARN_DTM + SVC_TYPE + SEQ_NO`) is **best-effort** — views don't have a real PK. If two legs ever produce the same tuple (extremely unlikely given the per-leg `SVC_TYPE` discriminator), Hibernate would deduplicate them in the first-level cache. Phase 2 should sanity-check by running a `GROUP BY` over the view to confirm no duplicates.

---

## 7. Cross-references

- Feature spec: [../../features/settings/firm-banking-history.md](../../../features/settings/firm-banking-history.md)
- Migration mapping: [../../migration/firm-banking-history-mapping.md](../../migration/firm-banking-history-mapping.md)
- Parity audit checklist: [../../audits/firm-banking-history-parity-checklist.md](../../audits/firm-banking-history-parity-checklist.md)
- Cross-feature DB mapping: [../db-mapping.md](../table-mapping/db-mapping.md) (add a row when V59–V62 land)
- DB parity audit (precedent for divergences): [../../audits/db-parity-audit-2026-04-30.md](../../audits/db-parity-audit-2026-04-30.md)
- DB function strategy: [../../architecture/db-function-strategy.md](../../../architecture/database/db-function-strategy.md)
- Workspace DB rules: [/.claude/rules/database.md](../../../../../.claude/rules/database.md)
