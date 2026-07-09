# Smart Organizer — Database Mapping (Backend)

Tables touched by the refactored Smart Organizer, and the legacy-column → entity-field → DTO-field →
UI-field chain. Schema is not redrawn here — see the
[banking ERD](../../../references/database/erd/banking-erd.md). Legacy column truth:
[docs/jex/features/smart-organizer/database-mapping.md](../../../../../docs/jex/features/smart-organizer/database-mapping.md).

---

## 1. Tables & roles

| Table | Role | Access | PK |
|---|---|---|---|
| `RMRK_ORG_INFM` | **Rule table** (Organizer Settings) | read + write | `(BIZ_KEY, RMRK_SEQ_NO)` |
| `ACCT_TRNS_HSTR` | **Transaction source** | read only | `(BIZ_KEY, ACCT_SEQ_NO)` |
| `ACCT_TRNS_RCV_PAY_INFM` | **Confirmation target** | read + write | `(BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO)` |
| `ACCT_MSTR` | account nickname + currency + type | read only | `(BIZ_KEY, BANK_CD, ACCT_NO)` |
| `CHART_ACCT_INFM` (entity `ChartOfAccount`) | reporting-account names | read only | `(BIZ_KEY, CHART_ACCT_SEQ_NO)` |
| `BUSI_USAG_SET_INFM` | purpose names | read only | `(BIZ_KEY, USE_USAG_CD)` |
| `BUSI_VAS_ACCT_INFM` | VAS account code + name | read only | `(BIZ_KEY, VAS_ACCT_SEQ_NO)` |
| `TRCO_INFM` | contact (customer/vendor) names | read only | `(BIZ_KEY, TRCO_KEY)` |
| `ACCT_AUTH_INFO` | per-user account authority (R11) | read only | — |

Relationship: `ACCT_TRNS_HSTR` is matched against `RMRK_ORG_INFM` (no FK — value match on `RMRK` +
direction), and confirmed rows fan out 1→N into `ACCT_TRNS_RCV_PAY_INFM` keyed by `ACCT_SEQ_NO`. The
master tables are dimension lookups resolved at read time (R07) and denormalized at write time (R05).

## 2. Schema sources (Flyway)

| Migration | Adds |
|---|---|
| `V172__smart_organizer_rmrk_org_infm_schema.sql` | `RMRK_ORG_INFM` rule table (refactor of legacy `V160`) |
| `V173__wb_get_next_val_rmrk_org_infm.sql` | `wb_get_next_val` branch for `rmrk_org_infm.rmrk_seq_no` |
| `V174__smart_organizer_view_setting_seed.sql` | View Setting seed `WEB_SVC_ID='smart-organizer'` |

`ACCT_TRNS_HSTR` and `ACCT_TRNS_RCV_PAY_INFM` are owned by the Transaction History migrations — see
[transaction-history-vnd/database-mapping.md](../current-savings-vnd/transaction-history/database-mapping.md).

## 3. Rule table (`RMRK_ORG_INFM`) column map

| Legacy column | Entity `RmrkOrgInfm` | DTO `OrganizerRuleRow` / `…SaveRequest` | UI field |
|---|---|---|---|
| `BIZ_KEY` (PK) | `bizKey` | — (tenant context) | — |
| `RMRK_SEQ_NO` (PK) | `rmrkSeqNo` | `rmrk_seq_no` | (row id) |
| `RCV_PAY_DV` | `rcvPayDv` | `rcv_pay_dv` | Receive/Pay toggle |
| `RMRK` | `rmrk` | `rmrk` | Remark |
| `MATCH_TYPE` | `matchType` (`@JdbcTypeCode CHAR`) | `match_type` | Match Type (`C`/`E`) |
| `DISP_ORD` | `dispOrd` | `disp_ord` | row order (drag) |
| `CHART_ACCT_CD` | `chartAcctCd` | `chart_acct_cd` | Reporting Account |
| `CHART_ACCT_SEQ_NO` | `chartAcctSeqNo` | `chart_acct_seq_no` | Reporting Account (surrogate) |
| `USE_USAG_CD` | `useUsagCd` | `use_usag_cd` | Purpose |
| `VAS_ACCT_SEQ_NO` | `vasAcctSeqNo` | `vas_acct_seq_no` | VAS Account |
| `TRCO_KEY` | `trcoKey` | `trco_key` | Contact |
| `REG_ID` / `REG_DTM` | `regId` / `regDtm` | `reg_id` / `reg_dtm` | (audit) |

> No `MOD_ID`/`MOD_DTM` on this table — legacy parity (V172). Updates preserve `REG_*`.

## 4. Eligibility row column map (read — `ACCT_TRNS_HSTR` + matched rule)

The user-requested core fields, traced end-to-end:

| Concept | Legacy column | Entity / source | DTO `EligibleTransactionRow` | UI field |
|---|---|---|---|---|
| Transaction ID | `ACCT_SEQ_NO` | `AcctTrnsHstr.acctSeqNo` | `acctSeqNo` | (confirm key) |
| Account Number | `ACCT_NO` | `AcctTrnsHstr.acctNo` | `acctNo` | Account No. |
| Currency | `CCY_CD` | `AcctTrnsHstr.ccyCd` | `ccyCd` | (amount format) |
| Amount (in) | `TRSC_AMT` where `INOUT_DV='1'` | derived | `inAmt` | Deposit |
| Amount (out) | `TRSC_AMT` where `INOUT_DV='2'` | derived | `outAmt` | Withdrawal |
| Receive/Pay type | `INOUT_DV` | `AcctTrnsHstr.inoutDv` | `inoutDv` | (direction) |
| Balance | `BAL` | `AcctTrnsHstr.bal` | `bal` | Balance |
| Remark | `RMRK` | `AcctTrnsHstr.rmrk` | `rmrk` | Remarks |
| VAS Account Code | rule `VAS_ACCT_SEQ_NO` → `BUSI_VAS_ACCT_INFM.VAS_ACCT_CD` | resolver | `vasAcctSeqNo` + `dispVasAcctNm` | VAS Account |
| VAS Account Name | `BUSI_VAS_ACCT_INFM.VAS_ACCT_NM` | resolver | `dispVasAcctNm` | VAS Account |
| Reporting Account | rule `CHART_ACCT_CD` → `CHART_ACCT_INFM` | resolver | `chartAcctCd` + `chartAcctNm` | Reporting Account |
| Purpose | rule `USE_USAG_CD` → `BUSI_USAG_SET_INFM` | resolver | `useUsagCd` + `dispUsagNm` | Purpose |
| Contact Name | rule `TRCO_KEY` → `TRCO_INFM` | resolver | `trcoKey` + `dispTrcoNm` | Contact |
| Transaction Date | `TRSC_DT` (+`TRSC_TM`) | `AcctTrnsHstr.trscDt` | `trscDt`/`trscTm` | Date |

## 5. Confirmation write column map (`ACCT_TRNS_RCV_PAY_INFM`) — R05

| Column | Entity `AcctTrnsRcvPayInfm` | Value at confirm |
|---|---|---|
| `BIZ_KEY` (PK) | `bizKey` | tenant |
| `ACCT_SEQ_NO` (PK) | `acctSeqNo` | source transaction |
| `RCV_PAY_SEQ_NO` (PK) | `rcvPaySeqNo` | `wb_get_next_val(...)` |
| `BANK_CD` / `ACCT_NO` / `TRSC_DT` | denormalized locators | from source row |
| `RCV_PAY_AMT` | `rcvPayAmt` | source `TRSC_AMT` |
| `RCV_PAY_USAG_CD` | `rcvPayUsagCd` | request `use_usag_cd` |
| `RCV_PAY_TRCO_KEY` | `rcvPayTrcoKey` | request `trco_key` |
| **`RCV_PAY_TRCO_NM`** | `rcvPayTrcoNm` | resolved `TRCO_CMNM` (R05) |
| **`RCV_PAY_TRCO_ENM`** | `rcvPayTrcoEnm` | resolved `TRCO_CMENM` (R05) |
| `VAS_ACCT_SEQ_NO` | `vasAcctSeqNo` | request surrogate |
| **`VAS_ACCT_CD`** | `vasAcctCd` | resolved real code (R05 — not the surrogate) |
| **`VAS_ACCT_NM`** | `vasAcctNm` | resolved name (R05) |
| `CNFM_YN` | `cnfmYn` | `'Y'` |
| `CNFM_BY_USER_ID`/`_NM`/`CNFM_DTM` | … | current user + now |
| `REG_*` / `MOD_*` | … | current user + now |

## 6. Match / Confirmation status (derived)

- **Match status** is implicit: a row appears in `/eligible` only if a rule matched (`matchedRmrkSeqNo`
  is non-null). Unmatched transactions never reach the list.
- **Confirmation status** is the existence of a `CNFM_YN='Y'` row in `ACCT_TRNS_RCV_PAY_INFM` for the
  transaction — `existsByBizKeyAndAcctSeqNoAndCnfmYn(...)`. Confirmed = excluded from `/eligible`;
  Transaction History reads the same flag to badge a line "confirmed".

See [business-flow.md](business-flow.md) for the runtime sequence and
[interface-specification.md](interface-specification.md) for wire shapes.
