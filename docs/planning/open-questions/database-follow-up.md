# Database Follow-Up — ERD Needs-Confirmation Items

**Raised-on:** 2026-06-01 (ERD Phase 2) · **Owner:** Sonny / Arthur · **Status:** 6 open (OQ-DB-1 … OQ-DB-6)

Converts the six **Needs Confirmation** items surfaced by the ERD audit
([`../../references/database/erd/full-project-erd.md`](../../references/database/erd/full-project-erd.md) § "Needs Confirmation")
into tracked open questions. Each is a **schema-correctness decision** for a maintainer to verify against
DEV (read-only via the `postgres` MCP) — none is a broken link. All detail from the ERD audit is preserved.

> **Do not change any migration to resolve these.** Flyway migrations are additive-only and never edited once
> applied. A confirmed fix ships as a **new** corrective migration. Until a decision is recorded here, the ERDs
> describe the schema as-built.

---

## OQ-DB-1 — `NXSIAS_MODULE_TRAN_LOG_HIS` dual definition (PK conflict)

- **Decision needed:** confirm the intended primary-key shape for `NXSIAS_MODULE_TRAN_LOG_HIS` and consolidate the two CREATE statements.
- **Conflict:** the table is created by **two** migrations, both `CREATE TABLE IF NOT EXISTS`:
  - **V59** (`firm_banking_history_source_tables`) — PK `(BIZ_KEY, REG_DTM, UUID)`, plus indexes `ix_nxsias_biz_type`, `ix_nxsias_biz_job`.
  - **V101** (`nxsias_module_tran_log_his`) — PK `(UUID, REG_DTM)`, plus `NXSIAS_MODULE_TRAN_LOG_HIS_BIZ_REG_IX`, `NXSIAS_MODULE_TRAN_LOG_HIS_JOB_IX`.
- **As-built behaviour:** on a fresh DB, **V59 runs first and wins** (lower version); V101's CREATE is a no-op. So the live PK is `(BIZ_KEY, REG_DTM, UUID)`. The JPA entity (`NxsiasModuleTranLogHisRepository`) must match the V59 shape.
- **Action:** verify the DEV PK shape, confirm `(BIZ_KEY, REG_DTM, UUID)` is intended, and document V101's CREATE as redundant (or ship a corrective note). Reconcile the index sets if both are wanted.
- **References:** [erd/settings-erd.md](../../references/database/erd/settings-erd.md) · [erd/diagrams/settings-erd.mmd](../../references/database/erd/diagrams/settings-erd.mmd) · migrations `V59`, `V101`.

## OQ-DB-2 — `BANK_EXCH_RATE_INFM.BIZ_KEY` column width

- **Decision needed:** is `BIZ_KEY VARCHAR(50)` on `BANK_EXCH_RATE_INFM` intentional, or migration drift from the project-wide `VARCHAR(20)`?
- **Detail:** every other tenant-scoped table uses `BIZ_KEY VARCHAR(20)` (V24 explicitly narrowed `BUSI_INFM.BIZ_KEY` from 32→20). `BANK_EXCH_RATE_INFM` (V127) declares `VARCHAR(50)`. A wider key column is harmless for storage but breaks the "BIZ_KEY type matches DEV (VARCHAR(20))" parity checklist item.
- **Action:** query DEV `information_schema.columns` for `bank_exch_rate_infm.biz_key`; if DEV is `VARCHAR(20)`, ship a corrective `ALTER COLUMN … TYPE VARCHAR(20)`. If DEV is genuinely `VARCHAR(50)`, record the exception.
- **References:** [erd/banking-erd.md](../../references/database/erd/banking-erd.md) · migration `V127`.

## OQ-DB-3 — `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN.SCDUL_PYMN_NO` width mismatch

- **RESOLVED / MOOT (2026-06-10):** the bridge table was **removed from the refactor** (unused by Spring; entity deleted; dropped via `docs/database/manual-cleanup/drop_pay_target_conn_bridge_20260610.sql`). The width question no longer applies on the refactor side. (DEV still owns the table; no refactor action.)
- **Decision needed:** ~~should the bridge table's `SCDUL_PYMN_NO` widen to match its logical parent?~~ (superseded by removal)
- **Detail:** `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` (V188) declares `SCDUL_PYMN_NO VARCHAR(10)`, but the parent `SCDUL_PYMN_SUMR.SCDUL_PYMN_NO` (V131) is `VARCHAR(20)`. The schema-parity note flags this — the bridge should normally inherit the parent width. A 10-char column truncates a 20-char key.
- **Action:** confirm whether V188's narrower width is intentional DEV parity (the DEV column may be `VARCHAR(10)`) or a refactor bug; if a bug, ship a corrective widen. The relationship is **logical — no FK**, so there is no constraint-level guard today.
- **References:** [erd/payment-erd.md](../../references/database/erd/payment-erd.md) · [payment/firm-banking-dispatch-relationships.md](../../references/database/payment/firm-banking-dispatch-relationships.md) · migrations `V131`, `V188`.

## OQ-DB-4 — `TB_FIRM_FILE_DATA` primary key omits `BIZ_KEY`

- **Decision needed:** confirm the gateway-side file-data table intentionally drops `BIZ_KEY` from its PK, and that this does not open a multi-tenant isolation gap.
- **Detail:** `TB_FIRM_FILE_DATA` (V189) has PK `(TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)` — `BIZ_KEY` is present as a **non-PK** column. This is a documented DEV design (see firm-banking-dispatch-relationships §2.5), but it is unusual: every other tenant-scoped table includes `BIZ_KEY` in the PK. Sibling `TB_FIRM_FILE_RSLT` keeps `BIZ_KEY` in its PK.
- **Action:** confirm DEV's PK matches, and verify the dispatch read/write paths always filter `BIZ_KEY` explicitly so two tenants can't collide on `(TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)`. Note: this table currently has an entity but **no repository** (write-path not yet ported — see [database-coverage-matrix.md](../../references/database/database-coverage-matrix.md)).
- **References:** [erd/payment-erd.md](../../references/database/erd/payment-erd.md) · [payment/firm-banking-dispatch-relationships.md](../../references/database/payment/firm-banking-dispatch-relationships.md) · migration `V189`.

## OQ-DB-5 — `BUSA_DOC_RCV_PAY_HIS.ACCT_SEQ_NO` reference unverified

- **Decision needed:** does `BUSA_DOC_RCV_PAY_HIS.ACCT_SEQ_NO` logically reference `ACCT_TRNS_HSTR.ACCT_SEQ_NO`, or is it an independent locator?
- **Detail:** `ACCT_SEQ_NO` appears on `BUSA_DOC_RCV_PAY_HIS` but was added/locator-noted around V21 rather than in the V13 CREATE. The ERD currently models a **logical** link `ACCT_TRNS_HSTR → BUSA_DOC_RCV_PAY_HIS` via `ACCT_SEQ_NO`, but the audit could not confirm it is the same sequence space.
- **Action:** trace the JEX write-path / the refactor `BusaDocRcvPayHisRepository` usage to confirm whether `ACCT_SEQ_NO` joins to `ACCT_TRNS_HSTR`. If it does not, downgrade the relationship in [erd/banking-erd.md](../../references/database/erd/banking-erd.md) to "independent column".
- **References:** [erd/banking-erd.md](../../references/database/erd/banking-erd.md) · migrations `V13`, `V21`.

## OQ-DB-6 — `AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO` numeric precision

- **Decision needed:** confirm the precision/scale bound of `AUDIT_LOGS_SEQ_NO`.
- **Detail:** migration V63 declares the column as `NUMERIC` (unbounded as documented), while the entity maps it as `BigDecimal`. The audit flagged ambiguity over whether it should be `NUMERIC(n,0)` with an explicit bound (matching DEV) and whether the `BigDecimal` mapping is correct vs a `Long`.
- **Action:** query DEV for the exact `numeric_precision` / `numeric_scale`; if DEV bounds it (e.g. `NUMERIC(15,0)`), ship a corrective `ALTER` and confirm the entity type. If it must stay `BigDecimal`, record why (precision exceeds `Long`).
- **References:** [erd/common-erd.md](../../references/database/erd/common-erd.md) · [erd/full-project-erd.md](../../references/database/erd/full-project-erd.md) · migration `V63`.

---

## Status

| OQ | Topic | Severity | Status |
|---|---|---|---|
| OQ-DB-1 | `NXSIAS_MODULE_TRAN_LOG_HIS` dual CREATE / PK conflict | medium (resolved at runtime by version order; tidy-up) | Open |
| OQ-DB-2 | `BANK_EXCH_RATE_INFM.BIZ_KEY` width 50 vs 20 | low (parity) | Open |
| OQ-DB-3 | dispatch-bridge `SCDUL_PYMN_NO` width 10 vs 20 | medium (truncation risk) | Open |
| OQ-DB-4 | `TB_FIRM_FILE_DATA` PK omits `BIZ_KEY` | medium (tenant isolation) | Open |
| OQ-DB-5 | `BUSA_DOC_RCV_PAY_HIS.ACCT_SEQ_NO` link | low (ERD accuracy) | Open |
| OQ-DB-6 | `AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO` precision | low (parity) | Open |

## Related

- ERD set: [`../../references/database/erd/README.md`](../../references/database/erd/README.md) (Needs-Confirmation source: [full-project-erd.md](../../references/database/erd/full-project-erd.md))
- Coverage matrix: [`../../references/database/database-coverage-matrix.md`](../../references/database/database-coverage-matrix.md) · Table ownership: [`../../references/database/table-ownership.md`](../../references/database/table-ownership.md)
- Sibling open-questions: [documentation-follow-up.md](documentation-follow-up.md) · [open-questions README](README.md)
- Schema-parity rule: `wecms-api/CLAUDE.md` § "Legacy Schema Parity Rule"
