# Overseas Payment — Database Mapping


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

> Entity ↔ table ↔ column map. Hub: [README.md](README.md). All tables present in the legacy table inventory; the refactor mirrors the JEX DDL (no rename/re-type). Verified against the `OvrsPymn*` entities on 2026-06-23.

> **Correct table names.** The per-line detail table/entity is **`OVRS_PYMN_SUMR_DETL`** (`OvrsPymnSumrDetl`). The earlier `OVRS_PYMN_DTLS` name was wrong and is retired. (The repository class is named `OvrsPymnDtlsRepository` but maps the `OvrsPymnSumrDetl` entity → `OVRS_PYMN_SUMR_DETL`.)

## Tables

| Entity | Table | Role |
|---|---|---|
| [`OvrsPymnSumr`](../../../../src/main/java/com/kosign/wecms/domain/payment/entity/OvrsPymnSumr.java) | `OVRS_PYMN_SUMR` | Scheduled-batch summary (Excel upload, **E**-mode) |
| [`OvrsPymnSumrDetl`](../../../../src/main/java/com/kosign/wecms/domain/payment/entity/OvrsPymnSumrDetl.java) | `OVRS_PYMN_SUMR_DETL` | Batch per-line detail (beneficiary, FX, amount, reporting account) |
| [`OvrsPymnMstr`](../../../../src/main/java/com/kosign/wecms/domain/payment/entity/OvrsPymnMstr.java) | `OVRS_PYMN_MSTR` | Per-payment master (**M**-mode, manual/individual) |
| `ExptResolMstr` (approval-box domain) | `EXPT_RESOL_MSTR` | Approval master — `APPR_TYPE_CD` is the authoritative approval state |
| `ExptResolApprvrInfm` | `EXPT_RESOL_APPRVR_INFM` | Approver chain |
| `ExptResolDetl` / `ExptResolFile` | `EXPT_RESOL_DETL` / `EXPT_RESOL_FILE` | Resolution detail + approval attachments |
| Dispatch entities | `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA` | Written on dispatch (File path); read for result counts |

## OVRS_PYMN_SUMR (`OvrsPymnSumr`)

- **PK** (`OvrsPymnSumrId`): `BIZ_KEY` (varchar 20) + `OVRS_PYMN_NO` (varchar 14).
- **No `APPR_STTS` column** — approval status derived from `EXPT_RESOL_MSTR.APPR_TYPE_CD` at read time.

| Column | Type | Notes |
|---|---|---|
| `OVRS_PYMN_TYPE_CD` | char(2) | E/M batch type |
| `TITL` | varchar(100) | Batch / approval title |
| `CCY_CD` | varchar(3) | Currency |
| `TOTAL_TRAN_AMT` | numeric(20,6) | Total transfer amount |
| `TOT_TRNS_CNT` | numeric(10,0) | Transaction count |
| `APPR_SEQ_NO` | varchar(15) | nullable; set on request-approval; cleared on retract/draft |
| `OUT_BANK_CD` / `WDRL_ACT_NO` / `WDRL_CCY_CD` | varchar | Withdrawal account (back-stamped at request-approval) |
| `TRNF_YN` | char(1) | `Y` transferred / `N` not — set on dispatch |
| `TRNF_DT` | varchar(14) | yyyyMMddHHmmss — set on dispatch |
| `PAY_TRAN_DT` / `PAY_FILE_SEQ_NO` | varchar | Dispatch file-batch shadow keys |
| `ORG_FILE_NM` / `SAVE_FILE_NM` / `FILE_SIZE` | varchar | Uploaded Excel metadata |
| `REG_DTM`/`REG_ID`/`MOD_DTM`/`MOD_ID` | varchar | Audit columns (legacy 14-char datetime strings) |

## OVRS_PYMN_SUMR_DETL (`OvrsPymnSumrDetl`)

- **PK** (`OvrsPymnSumrDetlId`): `BIZ_KEY` + `OVRS_PYMN_NO` + `OVRS_PYMN_DETL_NO` (varchar 15).
- **FK**: `(BIZ_KEY, OVRS_PYMN_NO)` → `OVRS_PYMN_SUMR`.

| Column | Type | Notes |
|---|---|---|
| `TRNF_AMT` | numeric(20,6) | Per-line amount |
| `TRNF_CCY_CD` | varchar(3) | Per-line currency |
| `BNFC_NM` / `BNFC_BANK_NAME` / `BNFC_BANK_CD` / `BNFC_ACCT_NO` / `BNFC_BANK_BIC` / `BNFC_ADDR` | varchar | Beneficiary fields |
| `FEE_PAYER_CD` | char(1) | B/O/S |
| `TRCO_KEY` / `HAND_TRCO_KEY` | varchar(20) | Trade-company keys |
| `MEMO` | varchar(500) | Memo/purpose |
| `CHART_ACCT_CD` / `CHART_ACCT_SEQ_NO` | varchar | Reporting account (pair) — nullable |
| `PAY_TRAN_DT` / `PAY_TRAN_SEQ` | varchar | Dispatch shadow keys (TB_PAY_FILE_DATA link) |
| `REG_*` / `MOD_*` | varchar | Audit columns |

## OVRS_PYMN_MSTR (`OvrsPymnMstr`)

- **PK** (`OvrsPymnMstrId`): `BIZ_KEY` + `OVRS_PYMN_NO` (varchar 14).
- M-mode single-remittance master; no detail child. Header withdrawal = debit-account fields.

| Column | Type | Notes |
|---|---|---|
| `DBT_ACCT_NO` / `DBT_ACCT_CD` / `DBT_CCY_CD` | varchar | Debit (withdrawal) account — used as M-mode header |
| `TRNF_CCY_CD` / `TRNF_AMT` | varchar / numeric | Remittance currency + amount |
| `TOTAL_TRAN_AMT` / `TOT_TRNS_CNT` | numeric | Totals |
| `FEE_PAYER_CD` | char(1) | |
| `BNFC_*` (`BNFC_NM`/`BNFC_BANK_CD`/`BNFC_ACCT_NO`/`BNFC_BANK_NAME`/`BNFC_BANK_BIC`/`BNFC_ADDR`) | varchar | Beneficiary |
| `MEMO` | varchar(500) | |
| `APPR_SEQ_NO` | varchar(15) | nullable |
| `CHART_ACCT_CD` / `CHART_ACCT_SEQ_NO` | varchar | Reporting account (Cash-Flow report filter) |
| `TRNF_YN` | char(1) | |
| `REG_*` / `MOD_*` | varchar | Audit columns |

## EXPT_RESOL_MSTR (approval master — read/written here)

Key columns the overseas flow touches: `APPR_SEQ_NO` (PK with `BIZ_KEY`), `APPR_DV='34'` (Overseas), `APPR_STS='2'` at request, `APPR_TYPE_CD` (`00` pending / `01` approved / `02` rejected / `03` in-progress — the **derived-status source**), `DOC_NO` (`VO_YYMMDD_NNN`), `TITL`, `PAY_DATE`, `TOT_PAY_{KHR,USD,VND}_AMT` (currency-split totals), display flags, `DEL_YN`.

## Key generation

| Key | Generator | Funnel |
|---|---|---|
| `OVRS_PYMN_NO` | `OverseasPaymentKeyGenerator.nextOvrsPymnNo` | `wb_get_next_val` (per-tenant MAX+1 over `OVRS_PYMN_MSTR ∪ OVRS_PYMN_SUMR`) |
| `APPR_SEQ_NO` | `LegacyFunctionRepository.nextApprSeqNo` | global `sq_appr_seq_no` sequence (shared across Payment modules) |
| `DOC_NO` | `DocNoGenerator.nextDocNo` | `wb_get_next_val('expt_resol_mstr','doc_no',bizKey)` → `VO_YYMMDD_NNN` |

Per the funnel rule (`project_wb_get_next_val_funnel.md`); no Hibernate identity/sequence generators. One-shot race retry on `DataIntegrityViolationException` at the create/request-approval site.

## Migrations

No new tables — all four are legacy. View Setting seeds for the four overseas screen IDs land in `SYS_SCR_OPTION_WECMS` (manual DBA SQL, `ON CONFLICT DO NOTHING`); export provider screenId seed `payment-overseas-payment-result-detail` (V161). No DDL changes to the `OVRS_PYMN_*` tables.

## Cross-links
- [interface-specification.md](interface-specification.md) · [business-flow.md](business-flow.md) · [source-code-index.md](source-code-index.md)
