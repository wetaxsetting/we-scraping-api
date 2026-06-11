# Smart Organizer — Functional Specification (Backend)

The *what* of the refactored Smart Organizer backend: scope, eligibility rules, filters, sort
whitelist, the grid/classification fields the API returns, confirm semantics, and permissions. The
*how* is in [business-flow.md](business-flow.md). Functional source of truth: JEX
`fnsh_0200_001` / `fnsh_0200_002`
([docs/jex/features/smart-organizer/functional-specification.md](../../../../../docs/jex/features/smart-organizer/functional-specification.md)).

---

## 1. Scope & access

- **Tenant-scoped** by `BIZ_KEY` (from `TenantContext`) on every query.
- **Account-scoped (R11):** `bank_cd` + `acct_no` are **required** on `/eligible` and `/confirm`.
  Non-admin callers must hold a `V` (View) grant for that account in `ACCT_AUTH_INFO`
  (`AccountAccessGuard.assertCanView`); admins bypass.
- **Permission:** `@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')`
  on both endpoints (Smart Organizer is launched from Transaction History, so it inherits that
  screen's menu authority — VND **or** FCY).

## 2. Eligibility rules (which transactions appear)

A transaction is listed iff **all** hold:

1. Belongs to the current tenant and the selected account (`BIZ_KEY` + `BANK_CD` + `ACCT_NO`).
2. `TRSC_DT` falls in `[from_dt, to_dt]` (inclusive, `yyyyMMdd`).
3. The account's `ACCT_TYPE_CD ∈ ('03','04','05','06')` (current/savings VAS types).
4. **Not already confirmed** — no row exists in `ACCT_TRNS_RCV_PAY_INFM` for `(BIZ_KEY, ACCT_SEQ_NO)`.
5. **Matches a rule** — at least one `RMRK_ORG_INFM` rule matches under the R01 logic; the winning rule
   (lowest `DISP_ORD`) supplies the classification proposal.
6. *(optional)* the remark contains the `q` keyword (`ILIKE '%q%'`).

## 3. Filters / search parameters

| Param | Wire | Required | Notes |
|---|---|---|---|
| Bank code | `bank_cd` | ✅ | Account scope (R11) |
| Account number | `acct_no` | ✅ | Account scope (R11) |
| From date | `from_dt` | ✅ | `yyyyMMdd` |
| To date | `to_dt` | ✅ | `yyyyMMdd` |
| Keyword | `q` | — | Substring `ILIKE` on `RMRK` |
| Sort field | `sort_field` | — | Whitelist (§4); unknown → default |
| Sort direction | `sort_dir` | — | `ASC`/`DESC`; default `DESC` |
| Page | `page` | — | 1-based, default `1` |
| Page size | `size` | — | Default `15`, clamped ≤ 100 |

## 4. Sort whitelist (`SmartOrganizerSortField`)

Only these keys are accepted; anything else falls back to `TRSC_DT` DESC. This prevents SQL injection
through the sort parameter.

| Wire key | SQL expression |
|---|---|
| `TRSC_DT` *(default)* | `h.trsc_dt, h.trsc_tm` |
| `ACCT_NO` | `h.acct_no` |
| `IN_AMT` | `CASE WHEN h.inout_dv = '1' THEN h.trsc_amt ELSE 0 END` |
| `OUT_AMT` | `CASE WHEN h.inout_dv = '2' THEN h.trsc_amt ELSE 0 END` |
| `RMRK` | `h.rmrk` |
| `CHART_ACCT_CD` | `r.chart_acct_cd` |
| `USE_USAG_CD` | `r.use_usag_cd` |

## 5. Eligibility grid fields (`EligibleTransactionRow`)

Each row is split into a **Bank Transaction Data** block (read from `ACCT_TRNS_HSTR`) and a
**Confirmation / classification** block (read from the matched rule + resolved names).

| Field | Source | Notes |
|---|---|---|
| `acctSeqNo` | transaction PK | Used as the confirm key |
| `bankCd` / `bankNm` | code / `BankCdResolver` | R07 |
| `acctNo` / `acctNickNm` | account / `ACCT_MSTR` | R07 |
| `trscDt` / `trscTm` | transaction | `yyyyMMdd` / `HHmmss` |
| `inoutDv` | transaction | `'1'`=in (RCV), `'2'`=out (PAY) |
| `inAmt` / `outAmt` | derived from `INOUT_DV` | R06 |
| `bal` | transaction | running balance, R06/R07 |
| `ccyCd` | transaction | drives client amount format, R06 |
| `rmrk` | transaction | matched against rule patterns |
| `matchedRmrkSeqNo` | matched rule | which rule fired |
| `chartAcctCd` / `chartAcctSeqNo` / `chartAcctNm` / `chartAcctLevelNm` | rule + resolver | reporting account; breadcrumb reserved/leaf-only (R07) |
| `useUsagCd` / `dispUsagNm` | rule + resolver | purpose code + label (R07) |
| `vasAcctSeqNo` / `dispVasAcctNm` | rule + resolver | VAS surrogate + label (R07) |
| `trcoKey` / `dispTrcoNm` | rule + resolver | contact key + label (R07) |

## 6. Confirmation semantics (`/confirm`)

- Body carries the scoped account (`bank_cd`, `acct_no`) + a non-empty `rows[]`; each row carries the
  `acct_seq_no` plus the (possibly user-edited) classification (`use_usag_cd`, `vas_acct_seq_no`,
  `trco_key`, `chart_acct_cd`, `chart_acct_seq_no`).
- All classification fields are **optional** but, when present, must reference existing tenant records
  (R03 existence checks — §"Validation").
- Each confirmed transaction is written once to `ACCT_TRNS_RCV_PAY_INFM` with `CNFM_YN='Y'` and the
  denormalized VAS/contact values (R05). It then drops out of the eligibility list.
- Response: `confirmed_count` (number of rows persisted). The operation is transactional — any failing
  row aborts the batch.

## 7. Organizer Settings rules (`/rules`)

A rule (`RMRK_ORG_INFM`) is the unit of matching:

| Field | Required | Constraint |
|---|---|---|
| `rmrk` | ✅ | ≤ 500 chars — the pattern text |
| `match_type` | ✅ | `C` (contains) or `E` (exact) |
| `rcv_pay_dv` | ✅ | `RCV` or `PAY` — direction |
| `disp_ord` | — | priority; auto-assigned `MAX+1` when null |
| `chart_acct_cd` / `chart_acct_seq_no` | — | reporting account proposal |
| `use_usag_cd` | — | purpose proposal |
| `vas_acct_seq_no` | — | VAS proposal |
| `trco_key` | — | contact proposal |

Rules are listed ordered by `DISP_ORD, RMRK_SEQ_NO`; `reorder` rewrites `DISP_ORD` from a deduplicated
list. This table has **no** `MOD_*` columns (legacy parity) — only `REG_ID`/`REG_DTM`.

## 8. Validation & error cases

| Condition | Error code | HTTP |
|---|---|---|
| Missing/blank tenant | `WBE1009` | 400 |
| Missing `bank_cd`/`acct_no` (scope) | client error (bean validation `@NotBlank`) | 400 |
| Caller lacks `V` grant for the account | `AccessDeniedException` | 403 |
| Transaction not in tenant / wrong account | `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND` | 404 |
| Transaction already confirmed | `WBE_SMART_ORGANIZER_ALREADY_CONFIRMED` | 409 |
| Purpose code not found | `WBE_SMART_ORGANIZER_USAGE_NOT_FOUND` | 404 |
| VAS account not found / deleted | `WBE_SMART_ORGANIZER_VAS_NOT_FOUND` | 404 |
| Contact not found | `WBE_SMART_ORGANIZER_CONTACT_NOT_FOUND` | 404 |
| Sequence allocation failed | `WBE9999` | 500 |
| Rule not found (update/delete/reorder) | `WBE_SMART_ORGANIZER_RULE_NOT_FOUND` | 404 |
| Duplicate rule id in reorder payload | `WBE_SMART_ORGANIZER_RULE_DUPLICATE_REORDER` | 400 |

> **R03 note:** legacy additionally enforced a direction check (`USAG_GRP_CD` `4`→IN, `6`→OUT). Because
> `BUSI_USAG_SET_INFM.USAG_GRP_CD` is unpopulated in the refactor DB, the refactor degrades that to a
> presence check and relies on the rule's stored `RCV_PAY_DV` for direction. See
> [refactor-gap-analysis.md](refactor-gap-analysis.md) §R03.

Full request/response shapes: [interface-specification.md](interface-specification.md).
Tables & column mapping: [database-mapping.md](database-mapping.md).
