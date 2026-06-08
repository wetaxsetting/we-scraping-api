# Approval Box — Functional Specification (Backend)

Authoritative business rules for the `approval-box` feature. Code refs are to `com.kosign.wecms.domain.payment.approvalbox.*`.

---

## 1. Scope

Approval Box lists Payment approval documents for the current tenant. Only six payment types are surfaced (hardcoded in `ApprovalBoxService` / the `search` query):

| `APPR_DV` | Type | Slug (`ApprDv.slug()`) |
|---|---|---|
| `21` | Payroll | `payroll` |
| `31` | Expense | `expense` |
| `32` | Vendor | `vendor` |
| `33` | Corporate Card | `corporate_card` |
| `34` | Overseas | `overseas` |
| `35` | Internal Transfer | `internal_transfer` |

`DEL_YN = 'N'` rows only. The 14-code `APPR_DV` CHECK domain exists in the table, but Approval Box reads only this six-code subset.

---

## 2. Status model (two levels)

### Document status — `EXPT_RESOL_MSTR.APPR_TYPE_CD` (`ApprTypeCd`)
| Code | Meaning | Tab |
|---|---|---|
| `00` | Pending | Pending |
| `03` | In Progress | In Progress |
| `01` | Approved | Approved |
| `02` | Rejected | Rejected |

### Per-approver status — `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS` (`ApprvrStts`)
| Code | Meaning |
|---|---|
| `P` | Pending — this approver's turn |
| `A` | Approved |
| `R` | Rejected |
| `W` | Waiting — upstream not yet done |
| `S` | Stopped — a prior approver rejected |

### Wire status — `appr_stts` (derived)
`ApprovalBoxService.deriveApprStts(apprTypeCd)` (replaces legacy `wb_get_appr_stts`): `null→P`, `01→C`, `02→R`, `03→I`, default→`P`. The wire value is `P`/`I`/`C`/`R`. **`APPR_STS` (no `_TYPE_`) is NOT the box status** — it is an internal legacy column (every live DEV row is `'2'`); never derive status from it.

---

## 3. Search & filtering

| Filter | Wire param | Rule | SQL effect |
|---|---|---|---|
| Date range | `from_date`, `to_date` | **Required**, `YYYYMMDD`. Validated for length 8 + parseability; `from ≤ to`. Filters on `DRAFT_DATE` (not `REG_DTM`), inclusive. | `erm.draftDate BETWEEN :fromDate AND :toDate` |
| Keyword | `srch_wd` | Optional. Escapes `% _ !`, wraps `%…%`, lowercases. | `LOWER(docNm \| titl \| docNo) LIKE :keywordLike ESCAPE '!'`; null → omitted |
| Status tab | `appr_type_cd` | Optional, one of `00/01/02/03`. Null/blank = all tabs. Invalid → `WBE_VALIDATION`. | `(:apprTypeCd IS NULL OR erm.apprTypeCd = :apprTypeCd)` (list slice only) |
| Sort | `sort_field`, `sort_direction` | Optional; whitelisted (see §4). | `Pageable` Sort |
| Paging | `page`, `size` | `page` default 0 (zero-based), `size` default 15, clamped `[1..100]`. | `PageRequest.of(...)` |
| Tenant + visibility | (implicit) | From `TenantContext`. | see §6 |

**Tab counts** (`countTabs`) run the same scope as the list **but ignore `appr_type_cd`**, so the four chips always reflect one date/keyword/visibility combination simultaneously. Counts: `pending` (`00`), `inProgress` (`03`), `approved` (`01`), `rejected` (`02`). (The frontend's "All" tab = sum of the four; "Draft" is not a backend tab.)

---

## 4. Sort whitelist (`ApprovalBoxSortField`)

Wire `FIELD_ID` → entity property. Unknown/blank → default sort (no exception):

| `FIELD_ID` | Entity property |
|---|---|
| `REG_DTM` | `regDtm` |
| `DOC_NM` | `docNm` |
| `DOC_NO` | `docNo` |
| `TITL` | `titl` |
| `TOT_PAY_AMT` | `totPayKhrAmt` |
| `PAY_END_YN` | `payEndYn` |
| `PAY_DATE` | `payDate` |
| `DRAFT_USER_NM` | `draftUserNm` |
| `APPR_TYPE_CD` | `apprTypeCd` |

`CHECKBOX`, `ETC`, `APPRER_NM`, `CURRENCY` are deliberately **not** sortable. Default ORDER BY: `REG_DTM DESC, APPR_SEQ_NO DESC`; a user sort gets `APPR_SEQ_NO` appended as a stable secondary key. (Sort whitelist + `SORT_YN` seed agreement is documented in [database-mapping.md](database-mapping.md).)

---

## 5. Approve / Reject / Withdraw / Change-line rules

| Operation | Preconditions | Effect |
|---|---|---|
| **Approve** | master exists in tenant (`WBE2050`); not finalised `01/02` (`WBE2053`); caller is an assigned approver (`WBE2051`); caller's row is `P` (`WBE2052`) | caller `P→A`; if no `P`/`W` left → master `01` + source `C`; else master `03`, promote first `W→P`, source `I` |
| **Reject** | same as approve | caller `P→R`; all `W→S`; master `02`; source `X` |
| **Withdraw** | master Pending/In Progress (`00/03`) else `WBE_VALIDATION`; not found → `WBE_NOT_FOUND` | delete chain + master; null source `APPR_SEQ_NO`/`APPR_STTS` (back to Draft) |
| **Change line** | master Pending/In Progress; `appr_line_no` positive (`WBE_VALIDATION`); picked line has approvers | rebuild chain from template (line 1 `P`, rest `W`); master → `00` |
| **Bulk approve** | 1..100 ids (`WBE2054`) | per-id `approve`; aggregate `{successes, failures}` |

Approve/reject only ever act on the caller's own approver row — an approver cannot act out of turn or for someone else. Cross-tenant lookups surface as `WBE2050`/`WBE_NOT_FOUND` and never leak existence.

---

## 6. Authorization & tenant scope

- **Menu gate:** class-level `@PreAuthorize("@menuAuthz.canAccess('payment-approval-box')")` on all 8 endpoints (product-tree leaf validity → product gate → admin bypass → per-user `USER_MENU_AUTH_INFM` grant). Unauthorized → `AccessDeniedException` → **403 / `WBE1003`**; no JWT → **401 / `WBE1009`**.
- **Row visibility (in-query, defence beyond the menu gate):** admin sees every tenant row; a non-admin sees only rows they drafted (`regId = userId`) OR are an assigned approver of (`EXISTS` approver row). Enforced inside both `search` and `countTabs` JPQL. Mirrors legacy Redmine #42046.
- **Identity** comes only from `TenantContext` (signed JWT): `currentBizKey()`, `currentUserId()`, `currentIsAdmin()`. Never from request body/param.

---

## 7. Validation rules

- `from_date` / `to_date`: blank → `WBE_VALIDATION "<field>: is required"`; length ≠ 8 or unparseable → `WBE_VALIDATION "<field>: invalid date format (YYYYMMDD)"`.
- Period: `from > to` → `WBE_VALIDATION "from_date: must be on or before to_date"`.
- `appr_type_cd`: not in {00,01,02,03} → `WBE_VALIDATION "appr_type_cd: must be 00/01/02/03"`.
- `apprSeqNo` path: blank → `WBE_VALIDATION "appr_seq_no: is required"`.
- `ChangeApprovalLineRequest.apprLineNo`: `@NotNull` + `@Positive` → `WBE_VALIDATION`.
- `ApprovalBoxBulkApproveRequest.apprSeqNos`: `@NotNull @NotEmpty @Size(max=100)`; service also rejects >100 / empty → `WBE2054`.
- Missing required `@RequestParam` (`from_date`/`to_date`) → `WBE_VALIDATION` (HTTP 200 per the global handler).

---

## 8. Error codes

| Code | Meaning |
|---|---|
| `WBE2050` | Approval not found (missing master / cross-tenant) |
| `WBE2051` | Caller is not an approver for this approval |
| `WBE2052` | Approval already actioned by this approver (not in `P`) |
| `WBE2053` | Approval workflow already finalised (`01`/`02`) |
| `WBE2054` | Bulk approve requires 1..{max} approval ids |
| `WBE_VALIDATION` | Date / period / tab / body validation failure |
| `WBE_NOT_FOUND` | Reference not found (withdraw/change-line guard) |
| `WBE9999` | Null tenant/user context, or unexpected server error |
| `WBE1003` / `WBE1009` | Forbidden (menu) / Unauthenticated (no JWT) |

All `BusinessException`s return HTTP 200 with the standard `{ status, timestamp, path, trace_id }` envelope.

---

## 9. Empty / boundary behaviour

- Empty search → empty `list`, `total_count = 0`, `total_pages = 0`, tab counts all 0.
- No approver chain → `approvers` returns `[]`.
- `size = 0` on the wire → service substitutes default 15.
- Bulk approve all-fail → `successes = []`, `failures = [...]`, and no aggregate audit row is written.
- Export with no matching rows → an empty workbook (headers only).

---

## 10. Constants

`MAX_PAGE_SIZE = 100`, `DEFAULT_PAGE_SIZE = 15`, `MAX_BULK_SIZE = 100`, `MAX_EXPORT_ROWS = 50_000`, `AUDIT_WSVC_ID = "approval-box"`.

> **Page-size note:** the backend default is 15 (legacy parity). The frontend list hook requests `size = 10` by an intentional project decision (see [parity-fixes-2026-06.md](parity-fixes-2026-06.md)); the backend honours whatever size the client sends, clamped to `[1..100]`.
