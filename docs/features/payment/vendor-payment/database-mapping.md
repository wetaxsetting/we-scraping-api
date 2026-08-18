# Vendor Payment — Database Mapping (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Tables, columns, keys, key-generation, and migrations for `vendor-payment` (both sub-modules). Authoritative source: the JPA entities (shared with Internal Transfer) + the legacy JEX DDL, cross-checked against the live DEV `wecmsvndev` schema. All tables are in the [legacy-table-inventory](../../../../docs/references/database/legacy-table-inventory.md).

> **Shared tables.** Vendor Payment owns **no** tables of its own. It reuses Internal Transfer's `SCDUL_PYMN_SUMR` / `SCDUL_PYMN_SUMR_DETL` (partitioned by `SCDUL_PYMN_TYPE_CD = '01'`) and the Approval-Box `EXPT_RESOL_*` family. See [internal-transfer](../internal-transfer/) + [approval-box/database-mapping.md](../approval-box/database-mapping.md) for the full column-level mapping; this file documents the Vendor-specific facts.

---

## 1. Tables

| Table | Role | Vendor usage |
|---|---|---|
| `SCDUL_PYMN_SUMR` | Scheduled-payment summary (Internal/Vendor/Expense/Payroll share it) | **Read + write** — Vendor rows carry `SCDUL_PYMN_TYPE_CD='01'`; full row on create, `APPR_SEQ_NO` / `TRNF_YN` updated, `ERP_FILE_NUM` **not** used for Approval Ref |
| `SCDUL_PYMN_SUMR_DETL` | Per-line detail | **Read + write** — one row per Excel line; reporting-account + Banking-Match columns |
| `EXPT_RESOL_MSTR` | Approval document master | **Read + write** (via shared fan-out) — `APPR_DV='32'` (Vendor); carries `DOC_NO` (Approval Ref), `TITL` (Approval Title), `APPR_TYPE_CD`, `PAY_DATE` |
| `EXPT_RESOL_APPRVR_INFM` | Approver chain | **Read + write** (shared fan-out) — `APPRVR_STTS` per approver |
| `EXPT_RESOL_FILE` | Requester attachments | **Write** on request-approval (`ApprovalAttachmentWriter`) |
| `EXPT_RESOL_PAY_EVID_FILE` | Payment-evidence attachments | **Read** for the combined attachment list |
| `TB_PAY_*`, `TB_FIRM_*` | Firm-banking dispatch ledger | **Write** on dispatch (flag-gated inert by default) |
| `AUDIT_LOGS_INFM` | Audit ledger | **Write** — `WSVC_ID='vendor-payment'` |

---

## 2. Discriminator & the missing APPR_STTS column

- **Discriminator.** Every `SCDUL_PYMN_SUMR` / `SCDUL_PYMN_SUMR_DETL` read & write for Vendor carries `SCDUL_PYMN_TYPE_CD = '01'` (refactor enum `ScdulPymnTypeCd.VENDOR.code()`). Internal Transfer uses `'03'`, Expense `'02'`, Payroll `'00'`. This keeps the modules isolated on the shared physical table.
- **No `SCDUL_PYMN_SUMR.APPR_STTS` column.** The legacy JEX schema has none. Approval status is derived at read time via the legacy DB function `wb_get_appr_stts(...)` through `LegacyFunctionRepository` (`C` approved, `R` rejected — **not** `X`). Both the Scheduled-list `appr_type` filter and the rendered status come from this function over the `EXPT_RESOL_MSTR` join. Never add an `APPR_STTS` column or write-back. (Same rule as Internal Transfer / all payment modules — `project_scdul_pymn_sumr_appr_stts_remap.md`.)

---

## 3. Entity ↔ table ↔ column (Vendor-relevant)

| Entity (package) | Table | Notes |
|---|---|---|
| `internaltransfer/entity/ScdulPymnSumr` (+ `ScdulPymnSumrId`) | `SCDUL_PYMN_SUMR` | PK `(BIZ_KEY, SCDUL_PYMN_NO)`; `SCDUL_PYMN_NO` mapped `length=10` (not VARCHAR(20)); `APPR_SEQ_NO`, `TRNF_YN`, `ORG_FILE_NM`, `SAVE_FILE_NM`, `CCY_CD`, `SCDUL_PYMN_TYPE_CD` |
| `internaltransfer/entity/ScdulPymnSumrDetl` (+ `*Id`) | `SCDUL_PYMN_SUMR_DETL` | PK `(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)`; reporting-account + match columns; line order `SCDUL_PYMN_DETL_NO` |
| `payment/approvalbox/entity/ExptResolMstr` (+ `*Id`) | `EXPT_RESOL_MSTR` | `APPR_DV='32'`; `DOC_NO` = Approval Ref; `TITL` = Approval Title |
| `payment/approvalbox/entity/ExptResolApprvrInfm` (+ `*Id`) | `EXPT_RESOL_APPRVR_INFM` | approver chain |

Vendor declares no entity of its own — it imports these from the shared packages.

---

## 4. Key generation (all through the `wb_get_next_val` funnel / documented exceptions)

Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Next-value policy" + `project_wb_get_next_val_funnel.md`:

| Key | Generator | Rule |
|---|---|---|
| `SCDUL_PYMN_NO` | `InternalTransferKeyGenerator` | date-prefixed; funnels `wb_get_next_val` (OD-2 exception) |
| `SCDUL_PYMN_DETL_NO` | `LegacyFunctionRepository.nextScdulPymnDetlNo` seed, then Java increment | seeded once per batch then incremented |
| `EXPT_RESOL_MSTR.APPR_SEQ_NO` | `LegacyFunctionRepository.nextApprSeqNo` | global `sq_appr_seq_no` |
| `EXPT_RESOL_MSTR.DOC_NO` | `DocNoGenerator` | `VO_YYMMDD_NNN` (Java-side exception, env-set-driven) |

No new ID scheme, no Hibernate sequence generator, no DB identity column.

---

## 5. Migrations

- **Schema:** `SCDUL_PYMN_SUMR` / `SCDUL_PYMN_SUMR_DETL` and the `EXPT_RESOL_*` family are created by the Internal-Transfer / Approval-Box per-table Flyway DDL files (see those features' database-mapping docs). Vendor adds **no** schema migration.
- **Key-gen function:** `V155` (the consolidated `wb_get_next_val` funnel, 28 branches) + `V148` (`sq_appr_seq_no`).
- **View Setting seed:** consolidated into the single manual-DBA script **`V156__sys_scr_option_wecms.sql`** — **49 vendor-payment rows** across the 4 screen slugs (`payment-vendor-payment-scheduled`, `…-scheduled-detail`, `…-result`, `…-result-detail`). The per-feature `V141/V142/V150/V160` migrations referenced by the old stub **do not exist** — they were folded into V156 during the 2026-06-09 consolidation. V156 is **DBA-run, not Flyway-applied** (Flyway is disabled, `ddl-auto=validate`; `ON CONFLICT DO NOTHING`, legacy rows untouched — `project_sys_scr_option_seed_strategy.md`).

---

## 6. Storage (not a DB table)

Original Excel files are stored on the filesystem under `<root>/<biz_key>/vendor-payment/<save_file_nm>` (`VendorPaymentFileResolver` + `VendorPaymentStorageProperties` + `VendorPaymentConfig`), with a `resolveForRead` fallback to the legacy `internal-transfer/` folder for batches uploaded before the per-module split. The DB stores only `ORG_FILE_NM` / `SAVE_FILE_NM` on `SCDUL_PYMN_SUMR`.

---

## 7. View Setting columns (`SYS_SCR_OPTION_WECMS`, V156 seed)

| Screen slug (`WEB_SVC_ID`) | Columns |
|---|---|
| `payment-vendor-payment-scheduled` | Date/Time · File Name · No. of Transactions · Total Transfer Amount · Scheduled Date · Approval Status · Requestor · Approver · Approval Ref · Approval Title |
| `payment-vendor-payment-result` | Date/Time · File Name · No. of Transactions · Total Transfer Amount · Transfer Status · Approval Ref · Approval Title |
| `…-scheduled-detail` / `…-result-detail` | per-line detail column sets |

Canonical 10-column schema (no `FIX_YN` — removed per `project_sys_scr_option_fix_yn_keep_divergence.md`). Sort whitelists are `VendorPaymentSortField` (scheduled) / `VendorPaymentResultSortField` (result). **`APPRVR_NM` (Approver) carries `SORT_YN='Y'` but has no whitelist constant** → glyph renders but the backend falls through to the default ORDER BY (PARTIAL — see [refactor-gap-analysis.md](refactor-gap-analysis.md)).
