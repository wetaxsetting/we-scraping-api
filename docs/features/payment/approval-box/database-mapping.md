# Approval Box — Database Mapping (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Tables, columns, keys, indexes, Flyway migrations, and domain code values for `approval-box`. Authoritative source: the JPA entities + the Flyway DDL, cross-checked against the live DEV `wecmsvndev` schema.

---

## 1. Tables

| Table | Role | Used by Approval Box list/action path |
|---|---|---|
| `EXPT_RESOL_MSTR` | Approval document master (one per submitted batch) | **Read + write** — list base, status, type, doc no/title, amount columns; `APPR_TYPE_CD` + mod cols written on approve/reject; full row on fan-out; deleted on withdraw |
| `EXPT_RESOL_APPRVR_INFM` | Approver chain (one row per approver) | **Read + write** — chain read + per-row approver counts; `APPRVR_STTS`/`APPR_DTM` written on approve/reject; `W→S` cascade; inserted on fan-out; deleted/rebuilt on withdraw/change-line |
| `SCDUL_PYMN_SUMR` | Scheduled-payment summary (Internal/Vendor/Expense/Payroll) | **Read** — currency (`CCY_CD`), file name (`ORG_FILE_NM`), uploader visibility (`REG_ID`, G3); **write** = `clearApprSeqNo` on withdraw only. No `APPR_STTS` write-back (column absent in legacy). |
| `OVRS_PYMN_SUMR` | Overseas-payment summary (`APPR_DV='34'`) | **Read** — currency, file name, uploader visibility; **write** = `clearApprSeqNo` on withdraw only (same as SCDUL). |
| `CARD_TRNS_HSTR` | Corporate-card transaction ledger (`APPR_DV='33'`) | **Read only** — currency (`CCY_CD`), **card amount** (`SUM(TRSC_AMT)`, G5), uploader visibility (`REG_ID`, G3). The Approval Box never writes it (Card module owns it). |
| `APPR_LINE_INFM_DETL` | Approval-line template approvers | **Read** (change-approval-line rebuild) |
| `AUDIT_LOGS_INFM` | Audit ledger | **Write** (`WSVC_ID = "approval-box"`, one row per locale) |

Other legacy `EXPT_RESOL_*` family tables — **not read by the refactor list path**:

- `EXPT_RESOL_DETL` (per-line transaction detail), `EXPT_RESOL_APPR_LINE` (display snapshot of the line at submission), `EXPT_RESOL_FILE` (requester uploads), `EXPT_RESOL_PAY_EVID_FILE` (payment-evidence uploads): mapped as entities for the fan-out / detail-popup paths but the **main list** does not join them. The displayed file-name column comes from `ORG_FILE_NM` on the summary tables (parity fix — legacy `IDO.EXPT_RESOL_MSTR_R003.xml:146` read `COALESCE(SCDUL.TITL, OVRS.TITL)`, but the refactor moved the file name to `ORG_FILE_NM`; `TITL` now holds the typed title).
- `BLTB_POST_INFM` (bulletin post): legacy `IDO.EXPT_RESOL_MSTR_R003.xml` LEFT-JOINs it for the Title column (`POST_TITLE`) and a `ref_bltb_cd`/`ref_post_seq_no` filter. The refactor **drops both** — it renders `EXPT_RESOL_MSTR.TITL` directly for the Title column (the WeCMS payment kinds carry no bulletin post; the on-screen values match `TITL`). Out-of-scope table for this feature.

---

## 2. `EXPT_RESOL_MSTR` — master (PK `(BIZ_KEY, APPR_SEQ_NO)`)

Entity: `entity/ExptResolMstr.java` (`@IdClass(ExptResolMstrId.class)`). The DEV table has 47 columns; the entity maps the ~37 the feature reads/writes (allowed per the schema-vs-entity subset rule). Key columns:

| Column | Type (DEV) | Null | Mapped | Meaning |
|---|---|---|---|---|
| `BIZ_KEY` | varchar(20) | NO (PK1) | ✓ | Tenant |
| `APPR_SEQ_NO` | varchar(15) | NO (PK2) | ✓ | Per-tenant approval doc sequence |
| `APPR_DV` | varchar(2) | NO | ✓ | Payment-type discriminator (`21/31/32/33/34/35` in scope) |
| `APPR_STS` | varchar(2) | NO `'0'` | ✓ | Internal legacy status (**not** the box status; live = `'2'`) |
| `APPR_TYPE_CD` | varchar(2) | NO `'00'` | ✓ | **Tab discriminator** `00/01/02/03` |
| `DRAFT_DATE` | varchar(8) | NO | ✓ | Draft date `YYYYMMDD` — the date filter column |
| `DRAFT_TIME` | varchar(6) | NO | ✓ | Draft time |
| `DRAFT_USER_ID` / `_NM` | varchar(100) | NO | ✓ | Requester |
| `DRAFT_USER_SITE_CD` / `_NM` | varchar(20)/(200) | NO | ✓ | Drafter company |
| `TITL` | varchar(200) | NO | ✓ | Title / subject |
| `CONT` | text | YES | ✓ | Approval content |
| `DOC_NO` | varchar(50) | NO | ✓ | Document number `VO_YYMMDD_NNN` |
| `DOC_NM` | varchar(100) | NO | ✓ | = `ApprDv.slug()` |
| `TOT_PAY_KHR_AMT` | numeric(20,6) | YES | ✓ | KHR total — holds the live amount for VN tenants |
| `TOT_PAY_USD_AMT` | numeric(20,6) | YES `0` | ✓ | USD total |
| `TOT_PAY_VND_AMT` | numeric(20,6) | YES `0` | ✓ | VND total |
| `PAY_DATE` | varchar(8) | YES | ✓ | Scheduled pay date |
| `PAY_END_YN` | varchar(1) | NO `'N'` | ✓ | Payment-complete flag |
| `DEL_YN` | varchar(1) | NO `'N'` | ✓ | Soft-delete |
| `REG_ID` / `REG_DTM` | varchar(100)/(14) | NO | ✓ | Creator / create datetime (`REG_ID` = requester, used by the visibility predicate) |
| `MOD_ID` / `MOD_DTM` | varchar(100)/(14) | — | ✓ | Modifier / modify datetime |
| `APPR_FUND_TP` | char(1) | YES | ✓ (`@JdbcTypeCode(CHAR)`) | Approval fund type |
| print flags (`HAND_REG_YN`, `APPR_BOX_USE_YN`, `APPR_DAT_DISP_YN`, `CMPN_LOGO_DISP_YN`, `REG_DISP_YN`) | varchar(1) | NO | ✓ | Print-form flags set by fan-out |

Unmapped DEV columns (by design): `DRAFT_USER_POS_NM`, `DRAFT_USER_DEPT_CD/_NM`, `APPR_BOX_DISP_CNT`, `CMPN_LOGO`, `PAY_END_DTM/_NM`, `PAY_REQ_DATE`, `REF_DATA_CNT`, `PAY_YN`, `ADV_PAYM_SEQ/_NM`, `BANK_CD/_NM`, `ACCT_NO`, `APLY_EXCH_RATE`, `REG_CHNL`, `PC_MOD_YN`.

Constraints (DEV): PK `expt_resol_mstr_pk`; FK `expt_resol_mstr_fk01 (BIZ_KEY) → BUSI_INFM(BIZ_KEY)`; CHK01 `APPR_DV ∈ {00,10,20,21,30,31,32,33,34,35,40,50,60,70}`; CHK02 `APPR_STS ∈ {0,1,2,4,9}`; CHK03 `DEL_YN ∈ {Y,N}`; CHK04 `HAND_REG_YN ∈ {Y,N}`.

---

## 3. `EXPT_RESOL_APPRVR_INFM` — approver chain (PK `(BIZ_KEY, APPR_SEQ_NO, APPRVR_ID)`)

Entity: `entity/ExptResolApprvrInfm.java` (`@IdClass(ExptResolApprvrInfmId.class)`). 13 columns:

| Column | Type (DEV) | Null | Meaning |
|---|---|---|---|
| `BIZ_KEY` | varchar(20) | NO (PK1) | Tenant |
| `APPR_SEQ_NO` | varchar(15) | NO (PK2) | Joins master |
| `APPRVR_ID` | varchar(100) | NO (PK3) | Approver user id |
| `APPRVR_STTS` | char(1) | NO | `P/A/R/W/S` (`@JdbcTypeCode(CHAR)`) |
| `APPRVR_NM` | varchar(100) | NO | Approver name |
| `APPR_DTM` | varchar(14) | YES | Action timestamp |
| `READ_YN` | varchar(1) | NO `'N'` | Read flag |
| `APPRVR_LINE_NO` | numeric(15,0) | YES | Approver order 1..N |
| `APPR_LINE_NO` | numeric(15,0) | YES | Approval-line template ref (no DB FK) |
| `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` | varchar(100)/(14) | mixed | Audit |

Constraints (DEV): PK `pk_expt_resol_apprvr` only — **no FK to the master** (legacy parity; existence enforced in app code), no CHECK constraints.

---

## 4. Repository query map

| Repository method | Query | Used by |
|---|---|---|
| `ExptResolMstrRepository.search(...)` | JPQL constructor projection → `ApprovalBoxProjection`; filters bizKey/delYn/apprDv-IN/draftDate-BETWEEN/apprTypeCd/keyword + visibility; two correlated COUNT subqueries | list |
| `ExptResolMstrRepository.countTabs(...)` | JPQL constructor projection → `ApprovalBoxTabCounts`; same scope minus apprTypeCd (identical 5-branch visibility); four `SUM(CASE…)` | tab counts |
| `ExptResolMstrRepository.findAmountBasisForTotals(...)` | JPQL `Object[]` `(apprSeqNo, apprDv, totPayKhrAmt, totPayUsdAmt, totPayVndAmt)`; same WHERE as `countTabs` (tab-unfiltered, full visibility), unpaged | footer per-currency totals (G1) |
| `ExptResolApprvrInfmRepository.findChain(bizKey, apprSeqNo)` | JPQL ORDER BY `apprvrLineNo ASC` | approvers + row mapping |
| `ExptResolApprvrInfmRepository.countByStatus(...)` | JPQL COUNT by status | approve/reject recount |
| `ExptResolApprvrInfmRepository.stopDownstream(...)` | `@Modifying` UPDATE `W→S` | reject |
| `ExptResolApprvrInfmRepository.deleteByBizKeyAndApprSeqNo(...)` | `@Modifying` DELETE chain | withdraw / change-line |
| `ScdulPymnSumrRepository` / `OvrsPymnSumrRepository` `findCcyCdBy…`, `findOrgFileNmBy…`, `clearApprSeqNo` | currency/file batch reads + withdraw clear (no `APPR_STTS` write-back — column absent) | list mapping + withdraw |
| `CardTrnsHstrRepository.findCcyCdByBizKeyAndApprSeqNos(...)` / `sumTrscAmtByBizKeyAndApprSeqNos(...)` | card currency + `SUM(TRSC_AMT)` batch reads (`APPR_DV='33'`) | list mapping + footer (G5) |

---

## 5. Flyway migrations

| Version | Summary |
|---|---|
| **V55** `expt_resol_appr_line.sql` | Creates `EXPT_RESOL_APPR_LINE` (line snapshot at submission). |
| **V56** `expt_resol_apprvr_infm.sql` | Creates `EXPT_RESOL_APPRVR_INFM` (approver chain — 13 cols, PK `(BIZ_KEY, APPR_SEQ_NO, APPRVR_ID)`, no FK to master). |
| **V57** `expt_resol_detl.sql` | Creates `EXPT_RESOL_DETL` (per-line transaction detail — read by `ApprovalDetailService.transactions`). |
| **V58** `expt_resol_file.sql` | Creates `EXPT_RESOL_FILE` (requester uploads — read by `ApprovalDetailService.attachments`, written by `ApprovalAttachmentWriter`). |
| **V59** `expt_resol_mstr.sql` | Creates `EXPT_RESOL_MSTR` (master — PK `(BIZ_KEY, APPR_SEQ_NO)`, FK→BUSI_INFM, CHECKs on `APPR_DV`/`APPR_STS`/`DEL_YN`/`HAND_REG_YN`). |
| **V60** `expt_resol_pay_evid_file.sql` | Creates `EXPT_RESOL_PAY_EVID_FILE` (payment-evidence uploads — second attachment source). |
| **V140** `wb_get_next_val.sql` | Funnel function — branch #1 `EXPT_RESOL_MSTR.APPR_SEQ_NO` → global `sq_appr_seq_no` sequence; branch #39 `EXPT_RESOL_MSTR.DOC_NO` (env-set-driven `VO_yyMMdd_NNN`). `sq_appr_seq_no` created in **V148**. |
| **V156** `sys_scr_option_wecms.sql` | Manual DBA-run View Setting seed (NOT Flyway-auto-applied, per `project_sys_scr_option_seed_strategy.md`). Seeds 11 user columns for `web_svc_id='approval-box'` / `dym_area_id='default'`: `REG_DTM`("Date") · `DOC_NM`("Type") · `DOC_NO`("Document No.") · `TITL`("Title") · `FILE_NM`("File Name") · `CURRENCY`("Transfer Currency") · `TOT_PAY_AMT`("Transfer Amount") · `PAY_END_YN`("Approval Status") · `PAY_DATE`("Scheduled Date") · `DRAFT_USER_NM`("Requestor") · `APPRER_NM`("Approver"). Canonical 10-column schema (no `FIX_YN`). All 11 `SORT_YN='Y'`. **NO CHECKBOX/ETC special rows seeded for approval-box** — the live page renders no row-selection column. `ON CONFLICT DO NOTHING`. |

> **No demo-seed or data-fix migration for Approval Box.** Earlier revisions of this doc cited V109/V137/V207/V216/V213/V194/V211 — those version numbers do **not** exist in the current `db/migration/` tree (corrected against the live filenames 2026-06-23). The schema lives in the per-table V55–V60 files above; the View Setting columns are seeded once in V156 (manual DBA SQL); there is no Approval Box demo-seed migration and no overseas approver-status backfill migration. (The seed strategy keeps legacy rows untouched for live-JEX coexistence — see `project_sys_scr_option_seed_strategy.md`.)

---

## 6. Domain code values (confirmed on DEV)

- `APPR_DV` present in DEV data: all 14 CHECK codes; Approval Box filters to `{21,31,32,33,34,35}`.
- `APPR_TYPE_CD`: `00`, `01`, `02`, `03` (the four tabs).
- `APPR_STS`: only `'2'` in live data (internal; not the box status).
- `APPRVR_STTS`: `P`, `A`, `R`, `W`, `S` — all five states present.
- `PAY_END_YN`, `DEL_YN`, `READ_YN`: `Y`/`N`.

DEV row volume: `EXPT_RESOL_MSTR` ≈ 995 rows total, ~329 for tenant `VN_VNM0004_000001`.

---

## 7. View Setting seed (`SYS_SCR_OPTION_WECMS`, `WEB_SVC_ID='approval-box'`, area `default`)

11 user columns seeded by **V156** (the canonical 10-column schema; no `FIX_YN`). Widths + headers verbatim from the seed:

`REG_DTM`("Date", 90px) · `DOC_NM`("Type", 170px) · `DOC_NO`("Document No.", 135px) · `TITL`("Title", 220px) · `FILE_NM`("File Name", 260px) · `CURRENCY`("Transfer Currency", 150px) · `TOT_PAY_AMT`("Transfer Amount", 150px) · `PAY_END_YN`("Approval Status", 160px) · `PAY_DATE`("Scheduled Date", 145px) · `DRAFT_USER_NM`("Requestor", 180px) · `APPRER_NM`("Approver", 180px). `disp_ord` 1..11.

**No `CHECKBOX` (disp_ord 0) and no `ETC` (disp_ord 99) special rows are seeded for `approval-box`** — the live Approval Box table has no row-selection column and no trailing action column (bulk-select was removed 2026-05-17; `ApprovalTable` renders neither). This matches the frontend `APPROVAL_FALLBACK_COLUMNS` (11 columns, no CHECKBOX/ETC).

All 11 rows carry `SORT_YN='Y'` in the seed. However, the backend whitelist `ApprovalBoxSortField` only resolves `REG_DTM`, `DOC_NM`, `DOC_NO`, `TITL`, `TOT_PAY_AMT`, `PAY_END_YN`, `PAY_DATE`, `DRAFT_USER_NM` (+ `APPR_TYPE_CD`) — `CURRENCY`, `FILE_NM`, and `APPRER_NM` have **no constant** and fall through to the default ORDER BY when sent. This is a `SORT_YN='Y'` ↔ whitelist disagreement for those three columns (the seed-vs-whitelist agreement rule wants `SORT_YN='N'` on columns the whitelist doesn't cover); flagged, not yet reconciled. The View-Setting `FIELD_ID`s `TOT_PAY_AMT`, `CURRENCY`, `APPRER_NM`, `FILE_NM` are presentation ids, not DB columns.

---

## 8. Schema-vs-DEV notes

- The five numeric columns are `numeric(20,6)`/`(10,0)` on DEV but bare `NUMERIC` in V109 (un-pinned precision/scale — the one substantive parity gap; Hibernate `validate` tolerates it, entities carry no `precision`/`scale`).
- Helper indexes are local-only additions (allowed).
- No FK on the approver table — intentional, consistent across entity / migration / DEV.
