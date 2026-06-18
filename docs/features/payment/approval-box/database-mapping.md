# Approval Box — Database Mapping (Backend)

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
| **V109** `approval_box_schema.sql` | Creates `EXPT_RESOL_MSTR` (47 cols, PK + FK→BUSI_INFM + 4 CHECKs) and `EXPT_RESOL_APPRVR_INFM` (13 cols, PK, no FK). Adds helper indexes beyond DEV: `IX_EXPT_RESOL_MSTR_BIZ_DRAFT_DATE`, `IX_EXPT_RESOL_MSTR_BIZ_REG_ID`, `IX_EXPT_RESOL_APPRVR_INFM_BIZ_APPRVR_ID`. Seeds 12 View Setting rows for `WEB_SVC_ID='approval-box'`. (File header `-- V84` is stale.) |
| **V137** `approval_box_demo_seed.sql` | 21 master rows + 24 approver rows for `VN_VNM0004_000001`, spanning all six `APPR_DV` and all four `APPR_TYPE_CD` so every tab populates. Idempotent. (File header `-- V121` stale.) |
| **V207** `approval_box_view_setting_align_with_legacy.sql` | Aligns the View Setting column set to legacy `resl_0001_001` (11 user columns); adds `FILE_NM`, un-hides CURRENCY/APPRER_NM; clears user overrides for re-init. |
| **V216** `approval_box_view_setting_legacy_widths.sql` | Sets the 11 columns' `SIZE` to legacy PROD pixel widths; re-asserts `SORT_YN='Y'` on all 11. |
| **V213** `overseas_approver_stts_backfill.sql` | Data fix: backfills `APPRVR_STTS` (`P/W → A` or `R`) for Overseas batches finalised before `OverseasPaymentService.actOnApproval` was corrected. Confirms master `01`=approved/`02`=rejected ↔ approver `A`/`R`. |
| V194 | Data fix: corrects USD Overseas amounts on `EXPT_RESOL_MSTR`. |
| V211 | Creates `EXPT_RESOL_FILE` (deferred FK to master — not used by the list path). |

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

11 user columns after V207/V216, plus the two specials:
`CHECKBOX`(0) · `REG_DTM`(1, 120px) · `DOC_NM`(2, 170px) · `DOC_NO`(3, 135px) · `TITL`(4, 220px) · `FILE_NM`(5, 260px) · `CURRENCY`(6, 150px) · `TOT_PAY_AMT`(7, 150px) · `PAY_END_YN`(8, 160px) · `PAY_DATE`(9, 145px) · `DRAFT_USER_NM`(10, 180px) · `APPRER_NM`(11, 180px) · `ETC`(99).

`SORT_YN='Y'` rows must each have a `ApprovalBoxSortField` constant — `CURRENCY`, `FILE_NM`, `APPRER_NM`, `CHECKBOX`, `ETC` are non-sortable (no constant), the rest map per [functional-specification.md §4](functional-specification.md). The View-Setting `FIELD_ID`s `TOT_PAY_AMT`, `CURRENCY`, `APPRER_NM`, `FILE_NM` are presentation ids, not DB columns.

---

## 8. Schema-vs-DEV notes

- The five numeric columns are `numeric(20,6)`/`(10,0)` on DEV but bare `NUMERIC` in V109 (un-pinned precision/scale — the one substantive parity gap; Hibernate `validate` tolerates it, entities carry no `precision`/`scale`).
- Helper indexes are local-only additions (allowed).
- No FK on the approver table — intentional, consistent across entity / migration / DEV.
