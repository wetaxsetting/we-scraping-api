# Card Transactions — Feature Spec (Refactor)

| Field | Value |
|---|---|
| Slug | `card-transactions` |
| Module | `payment` (Payments domain) |
| Refactor REST root | `/api/v1/payment/card-transactions` |
| Refactor frontend route | `/payment/card-transactions` |
| View Setting `WEB_SVC_ID` | `card-transactions` |
| Audit `AUDIT_LOGS_INFM.WSVC_ID` | `card-transactions` (legacy JEX writes NONE — see §10) |
| Status | **Design phase — docs only** (Playbook step 3 of 10; Flyway / backend / frontend NOT started) |
| Sprint | TBD with PO (Mr. Jung) |
| Author | Sonny (lead) — drafted with Claude Code |
| Date | 2026-05-18 |

> **Identifier-naming rule (workspace [CLAUDE.md](../../../CLAUDE.md) §"Cross-Tree Rules")** — the kebab-case slug `card-transactions` is the canonical identifier across Next.js routes, Spring REST URIs, Java packages/classes/DTOs, frontend feature folders, i18n key prefixes, log/MDC tags, View Setting `screenId`, and the audit `AUDIT_LOGS_INFM.WSVC_ID` column. The legacy JEX WSVC id `card_0001_001` appears in this document **only** in (1) JEX file:line citations as source-of-truth pointers and (2) migration-traceability prose.

---

## 1 · Business purpose

Card Transactions is the **Payment-module ledger of every card transaction scraped from the bank** for the tenant's registered corporate cards. Each row is one transaction posted by the issuer (POS purchase, ATM withdrawal, refund, etc.); the page lets the finance team:

1. **See every card movement in one place** — multi-card, multi-company, multi-period — with running totals (overall, approval-requested, approval-not-requested).
2. **Categorise** each transaction against the company's Chart of Accounts (`CHART_ACCT_INFM`) so it carries to the daily-financial reports.
3. **Annotate** with a one-line description (`CONT`) and (optionally) a `CARD_TRNS_USAGE_CODE` (Purpose code).
4. **Link** the transaction to one or more expense-resolution documents (`EXPT_RESOL_MSTR`) via the JEX bulletin-board hand-off — the row's badge then flips Linked ↔ Unlinked.
5. **Drill into the linked approval** — bulletin post, approver list, approval status — without leaving the page.
6. **Memo + attached evidence files** via the side-memo panel (`wb_get_memo_cont` + `wb_get_memo_color` per row).

Scraping itself is **out of scope for the refactor** — the JEX comment at [card_0001_001.ts:218](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L218) explicitly notes "card transactions … are scraped automatically." The refactor inherits the populated `CARD_TRNS_HSTR` rows and exposes the read + classify + link-status surface. The scraper batch stays on the JEX side under the same coexistence carve-out the rest of the refactor R&D follows.

## 2 · Actors & permissions

| Actor | Read | Update reporting account | Update description / purpose | Link to expense resolution |
|---|---|---|---|---|
| Tenant user (any `BUSI_ADMN_INFM` role) | Yes | Yes | Yes | Yes (creates the bulletin post → expense resolution per existing JEX flow) |
| External / non-tenant user | No (tenant filter) | — | — | — |

Legacy JEX permissions are **WSVC-level only** — every authenticated user with the menu mounted can hit every endpoint. The refactor follows that model (no `@PreAuthorize` admin gate today; same precedent as `approval-line` / `chart-of-accounts`). If a per-role gate is wanted later it lands as an OQ for the PO (see §11).

The menu is currently mounted for **PRDT_TYPE F / H / M** — confirmed via the DEV query `SELECT prdt_type FROM menu_auth_admn_sys WHERE web_svc_id='card_0001_001'` (returned 3 rows: `F`, `H`, `M`). HAENGSUNG (`H`) uses a wider table-2 layout (per [card_0001_001.ts:19-29](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L19-L29) — `_DYNAMIC_TABLE = 'table2'`). The refactor treats table-2 as a per-product View Setting variant (`DYM_AREA_ID = 'table2'`); same V32-style seed strategy that Account Inquiry uses for its VND / FCY twins.

## 3 · Approval line

The Card Transactions screen does **not own** an approval line. The "request approval" affordance posts the selected rows to the legacy **Expense Resolution** flow (`EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM` + `BLTB_POST_INFM`) — that feature has its own approval-line machinery and will port under its own slug. For Card Transactions the refactor renders the **resulting** `APPR_DOC_NO` / `APPR_DV` / approver-status badges (read-only) so the screen reflects approval state without owning the approval flow itself.

In Sprint 0 of `card-transactions` the link / approval-create write-paths are **deferred**; the refactor delivers the read pane and the two single-field patch endpoints (description, reporting account). See §11 OQ-1.

## 4 · Screen behaviour

Reference frame: legacy production page <https://www.wecms.vn/card_0001_001.act>. Approved refactor UI baseline (per [wecms-web/CLAUDE.md §"UI Implementation Baseline"](../../../../wecms-web/CLAUDE.md)): closest of the four approved references is **Banking > Account Inquiry (VND)** — list page with header, filter row, table, paginator + dashboard hero cards above the table.

### 4.1 Page chrome

- **Title** — `text-page-title` (18 / 500), translation key `card_transactions.title`. Legacy ML id `card_0001_001_m001` ([card_0001_001_view.jsp:251](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp#L251)).
- **Toolbar (top-right)** — Dashboard toggle, View Setting popover (list-icon), Print, Excel export, "Manage Cards" link, "Update" primary action. Toolbar pattern reuses `<PageActionButtons>` + the View Setting popover from the shared library; the "Manage Cards" + "Update" buttons are feature-scoped.
- **Dashboard hero cards** (legacy `loadDataDashboardViewCallback` at [card_0001_001.ts:1349](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L1349)) — three KPI tiles above the table:
  - **TOTAL** — `TOTL_CNT` rows · `TOTL_AMT` ₫
  - **APPROVAL NOT REQUESTED** — `TOTL_APPR_NOT_REQ_CNT` · `TOTL_APPR_NOT_REQ_AMT` ₫
  - **APPROVAL REQUESTED** — `TOTL_APPR_REQ_CNT` · `TOTL_APPR_REQ_AMT` ₫
  - Toggleable via the dashboard icon; user preference persisted via the existing global "settings dash off/on" mechanism (`common.app.getSettingsDashOffOn`).

### 4.2 Filter row

| Control | Bound input | Behaviour | Legacy ref |
|---|---|---|---|
| Date range (preset dropdown: Last 30 / 90 / 180 days · custom) | `FROM_DT`, `TO_DT` (yyyyMMdd) | **Required** per [card_0001_001_r001_act.jsp:34-39](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L34-L39). Default 30 days back. | `card_0001_001_in01..in04` |
| All Card Companies (multi-select bank) | `REC_IN[].BANK_CD` | Backend `WHERE CTH.bank_cd IN (...)` — built per [card_0001_001_r001_act.jsp:72-86](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L72-L86) | `card_0001_001_m013` |
| All Cards (multi-select card no.) | `REC_IN_2[].CARD_NO` | Backend `WHERE CTH.card_no IN (...)` — built per [card_0001_001_r001_act.jsp:88-102](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L88-L102) | `card_0001_001_m014` |
| Keyword search | `SRCH_WD` | `ILIKE` against `CARD_HLDR_NM` / `CARD_NM` / `CARD_NICK_NM` / `MERCH_NM` per [card_0001_001_r001_act.jsp:104-116](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L104-L116) | `card_0001_001_m015` |
| Link status badges (All / Linked / Unlinked) | `LINK_YN` ∈ `""`/`"Y"`/`"N"` | Backend filters via `T.REC_1 IS NOT NULL / IS NULL` in the outer wrapper of `CARD_MSTR_R002` — [card_0001_001_r001_act.jsp:134-151](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L134-L151) | `card_0001_001_j002..j004` |

All filter `onChange` handlers reset to page 1 via `resetPage()` per [wecms-web/CLAUDE.md §5 Event Function Rule](../../../../wecms-web/CLAUDE.md).

### 4.3 Pagination + sort

- Server-side pagination — `PAGE_NO` + `PAGE_PER_CNT`. Default page size 15 per legacy view ([card_0001_001_view.jsp:496-504](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp#L496-L504); options `15 / 20 / 50 / 100`).
- Sort: server-side, header-click cycle (`default → ASC → DESC → default`) per [wecms-api/CLAUDE.md §"Common table sort"](../../../CLAUDE.md). Whitelist FIELD_IDs (see §6 column spec) — `TRSC_DT`, `CARD_NO`, `MERCH_NM`, `CARD_NICK_NM`, `CARD_HLDR_NM`, `CCY_CD`, `TRSC_AMT`, `CHART_ACCT_NM`, `STTS`, `CARD_TRNS_USAGE_NM`, `CONT`. Default ORDER BY: `TRSC_DT DESC, TRSC_TM DESC, CARD_SEQ_NO DESC` (matches legacy `SortHelper.getSortDynamic` baseline — see [card_0001_001_r001_act.jsp:200](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L200)).
- Special FIELD_IDs `CHECKBOX_1` / `CHECKBOX_2` / `MEMO` / `MEMO_2` are admin-controlled — `SORT_YN='Y'` for CHECKBOX (legacy parity with the seed inspected on DEV) but never wired into the whitelist enum because the column is row-selection, not data. Backend `ScreenOptionService.SPECIAL_FIELD_IDS` silently locks them.

### 4.4 Row detail / drill-in

Clicking the **APPR_DOC_NO** badge opens a side popup ("Receipt Details" — legacy `popUpLayerReceiptDetail`) summarising the linked expense resolution. The refactor renders this as a stacked feature dialog (per [wecms-web/CLAUDE.md §"Modal stacking convention"](../../../../wecms-web/CLAUDE.md) — `stackLevel={1}` because the page itself is not stacked).

Clicking the **side-memo icon** in the last column (`MEMO`) opens the side-memo panel — same primitive used by other Payment screens; reuses the legacy `wb_get_memo_cont` / `wb_get_memo_color` data. Side-memo is **read-only in Sprint 0** of `card-transactions` (write-path = the Memo feature's own slug, ports separately).

### 4.5 Export

Excel export reuses the shared pipeline per [wecms-api/CLAUDE.md §"Excel export"](../../../CLAUDE.md). A new `CardTransactionsExportProvider` (`screenId = card-transactions`) lands in the same MR that ships the list endpoint. Allowed FIELD_IDs = the View Setting set minus `CHECKBOX_1/2` + `MEMO/MEMO_2`. Subheader = `<count> transaction(s) · Total: <TOTL_AMT> ₫ · Approval Requested: <TOTL_APPR_REQ_AMT> ₫ · Approval Not Requested: <TOTL_APPR_NOT_REQ_AMT> ₫` (backend-computed from the existing `CARD_TRNS_COUNT_R001` IDO).

Per the Excel-export contract, **frontend never ships rows** and **CSV is not sanctioned** (web CLAUDE.md §13b).

### 4.6 Empty / error states

- No rows: render the `not-found` `<tfoot>` row legacy uses ([card_0001_001_view.jsp:454-455](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp#L454-L455)) — refactor uses the shared empty-state primitive.
- API/network failure: `useDialog().open({ type: "error", … })` carrying `code` + `traceId` from the response envelope per [wecms-web/CLAUDE.md §"Dialog variant"](../../../../wecms-web/CLAUDE.md).
- Validation failure (missing `FROM_DT` / `TO_DT` / period overflow): `WBE_VALIDATION` from backend; frontend renders error dialog the same way.

## 5 · Endpoints (planned)

| Method | Refactor URI | Refactor purpose | Legacy WSVC (read-only ref) |
|---|---|---|---|
| GET | `/api/v1/payment/card-transactions` | Paged list + counts + totals (single envelope replaces legacy R001 + counts) | `card_0001_001_r001` |
| GET | `/api/v1/payment/card-transactions/cards` | Distinct active cards for the dropdown filter (`CARD_MSTR_R003`) | `card_0001_001_r002` |
| PATCH | `/api/v1/payment/card-transactions/{cardSeqNo}` | Partial patch — `description` OR `card_trns_usage_code` (legacy single-field U001) | `card_0001_001_u001` |
| PUT | `/api/v1/payment/card-transactions/{cardSeqNo}/reporting-account` | Update reporting account (`chart_acct_cd` + `chart_acct_seq_no`) | `card_0001_001_u002` |
| POST | `/api/v1/payment/card-transactions/export` | XLSX export (shared pipeline) | (none — JEX exported client-side) |

`/api/v1/payment/card-transactions/cards` is split out (rather than merged into the list response) so the dropdown can be cached client-side and survives filter changes without a re-fetch.

## 6 · Field-by-field column spec (View Setting seed)

DEV `SYS_SCR_OPTION_WECMS` carries **two area variants** for `card_0001_001`: `table1` (16 columns — default; PRDT_TYPE `F` / `M`) and `table2` (14 columns — HAENGSUNG; PRDT_TYPE `H`). The refactor seeds both under `web_svc_id='card-transactions'` with matching `dym_area_id` values.

### 6.1 `dym_area_id = table1` (default — F / M)

| Order | FIELD_ID | Header (en) | Wire type | Default | Notes |
|---|---|---|---|---|---|
| 0 | `CHECKBOX_1` | (—) | special | `USE_YN=Y, SORT_YN=Y, FIX_YN=Y` | Row-selection; locked by `SPECIAL_FIELD_IDS` |
| 1 | `TRSC_DT` | Date/Time | `string yyyyMMdd` | visible | Cell renders `TRSC_DT` + small grey `TRSC_TM` line ([card_0001_001.ts:1048-1056](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L1048-L1056)) |
| 2 | `CARD_NO` | Card Number | `string` | visible | Cell renders `CARD_NM` chip + `CARD_NO` (masked PAN) |
| 3 | `MERCH_NM` | Merchant | `string` | visible | |
| 4 | `CARD_NICK_NM` | Card Nickname | `string` | visible | |
| 5 | `CARD_HLDR_NM` | Cardholder | `string` | visible | |
| 6 | `CCY_CD` | Currency Code | `string(3)` | visible | |
| 7 | `TRSC_AMT` | Amount | `BigDecimal(20,6)` | visible | Right-aligned, formatted via `AmountFormatter.forDisplay` |
| 8 | `CHART_ACCT_NM` | Reporting Account | `string` (composed) | visible | Composite — `CHART_ACCT_NM` + `CHART_ACCT_LEVEL_NM` (small grey). Click → "Select Account" popover → PATCH `/reporting-account`. |
| 9 | `STTS` | Status | `string(1)` | visible | Localised via `wb_get_prch_stts_nm(prch_stts, LANG_CD)`; ported into Java via the shared `MessageResolver` against `payment.card_transactions.status.*` keys |
| 10 | `CARD_TRNS_USAGE_NM` | Purpose | `string` | visible | Resolved via `wb_get_usag_nm_new` in legacy — refactor reads `USAG_INFM_LANG_DETL` directly (active list — see §10) |
| 11 | `DISP_DOC_ID` | Link Reference | `string` | visible | First linked `BUSA_DOC_MSTR.DISP_DOC_ID` (REC_1[0]); empty when unlinked |
| 12 | `CONT` | Description | `string(200)` | visible | Inline-editable → PATCH `field_id=CONT, field_val=...` |
| 13 | `APPR_DOC_NO` | Approval Ref. | `string` | visible | Resolved from `EXPT_RESOL_MSTR.DOC_NO`; click opens approval drill-in popup |
| 100 | `MEMO` | Memo | special | `USE_YN=Y, SORT_YN=N, FIX_YN=Y` | Side-memo trigger; never sortable |

### 6.2 `dym_area_id = table2` (HAENGSUNG — H)

Same set with `_2` suffix on FIELD_IDs (`CHECKBOX_2`, `TRSC_DT_2`, …); two columns removed (`CARD_TRNS_USAGE_NM_2` and `CHART_ACCT_NM_2` are not present in DEV — confirmed in the DEV seed dump). The refactor seeds the 14 rows verbatim; the API contract stays the same — the per-product variation is purely a view-time visibility decision the View Setting popover already supports.

### 6.3 Wire field shapes (lowercase snake_case, per the global Jackson strategy)

```jsonc
// One row in the `data.rows` array
{
  "card_seq_no":            "156",
  "bank_cd":                "11704020",
  "bank_nm":                "VietinBank",
  "card_no":                "407465******5559",
  "card_nm":                null,
  "card_nick_nm":           "VTB(5559)",
  "card_hldr_nm":           "LE MINH",
  "merch_nm":               "GOLDEN LOTUS - TVL",
  "ccy_cd":                 "VND",
  "trsc_amt":               "799200.000000",
  "trsc_dt":                "20251019",
  "trsc_tm":                "114140",
  "card_trns_usage_code":   "1007010",
  "card_trns_usage_nm":     "Marketing / Promotion",
  "chart_acct_seq_no":      "100",
  "chart_acct_cd":          "100",
  "chart_acct_nm":          "Marketing Expense",
  "chart_acct_nm_en":       "Marketing Expense",
  "chart_acct_level_nm":    "Expenses > Operating > Marketing",
  "cont":                   "Lunch with client",
  "appr_seq_no":            "1390",
  "appr_doc_no":            "ER-2025-00123",
  "appr_dv":                "01",
  "post_seq_no":            123,
  "post_reg_id":            "lamine@gmail.com",
  "stts":                   "1",
  "stts_key":               "payment.card_transactions.status.1",
  "linked_docs": [
    {
      "doc_id":      "BD-…",
      "disp_doc_id": "INV-9001",
      "hand_doc_id": null,
      "doc_type":    "I",
      "einv_yn":     "Y",
      "totl_amt":    "799200.000000"
    }
  ],
  "approvers": [
    { "apprvr_id": "u01", "apprvr_nm": "An Pham",    "apprvr_stts": "A" },
    { "apprvr_id": "u02", "apprvr_nm": "Sonny Ran",  "apprvr_stts": "P" }
  ]
}
```

Wire keys are lowercase snake_case per [wecms-api/CLAUDE.md §"API Naming & Serialization Standards"](../../../CLAUDE.md). Stable identifier values stay UPPERCASE (`field_id="CHECKBOX_1"`, `stts="1"`, `ccy_cd="VND"`).

## 7 · State machine (status field)

`CARD_TRNS_HSTR.PRCH_STTS` is a single-character code with localised label (legacy `wb_get_prch_stts_nm`). The refactor preserves the code on the wire (`stts`) and ships a sibling i18n key (`stts_key`) under the `payment.card_transactions.status.*` namespace so frontend renders via `t(stts_key)`. The label set will be confirmed in the OQ phase — Sprint 0 ships only the codes observed in DEV data (`'1'` + null) and leaves the namespace open. See OQ-3.

`appr_dv` (Expense Resolution document type — drawn from `EXPT_RESOL_MSTR.APPR_DV`) and `apprvr_stts` (per-approver state from `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS`) are surfaced verbatim; the per-row Linked / Unlinked badge derives from `linked_docs.length > 0`.

## 8 · DB tables (active list confirmed)

All five tables are in the **PROD Active Table List** ([wecms-api/CLAUDE.md §"PROD DB Table Scope for Refactoring"](../../../CLAUDE.md)):

| Table | Role | Active list? |
|---|---|---|
| `CARD_TRNS_HSTR` | Ledger — one row per scraped card transaction | ✅ |
| `CARD_MSTR` | Card registry (per `(BIZ_KEY, BANK_CD, CARD_NO)`) | ✅ |
| `CHART_ACCT_INFM` | Reporting-account dictionary | ✅ (already ported under `chart-of-accounts`) |
| `EXPT_RESOL_MSTR` | Expense resolution master | ✅ |
| `EXPT_RESOL_APPRVR_INFM` | Per-approver state on a resolution | ✅ |
| `BUSA_DOC_MSTR` + `BUSA_DOC_RCV_PAY_HIS` | E-invoice / business-doc registry (for linked documents) | ✅ |
| `BLTB_POST_INFM` | Bulletin post owning the resolution conversation | ✅ |
| `USAG_INFM` + `USAG_INFM_LANG_DETL` | Purpose-code dictionary | ✅ |
| `BANK_INFM` | Bank-code → display-name lookup | ✅ |

Full column-for-column schema notes live in [docs/refactor-wecms/database/card-transactions.md](../../references/database/card-transactions.md).

## 9 · Tenant scope

Tenant is `TenantContext.currentBizKey()` (from the signed JWT). Every read / write path constrains on `BIZ_KEY` per [wecms-api/CLAUDE.md §"Coding Rules → Repositories"](../../../CLAUDE.md). The frontend never sends `biz_key` outside of login — the request body / query string cannot override the bound tenant.

R&D seed uses tenant `VN_VNM0004_000001` (DEV row count: `CARD_TRNS_HSTR` ≈ 268, `CARD_MSTR` ≈ 14 across all tenants; the 30-row-per-table Data Migration Rule applies).

## 10 · Audit log

Legacy `card_0001_001*` writes **zero rows** to `AUDIT_LOGS_INFM` (confirmed: `SELECT count(*) FROM audit_logs_infm WHERE wsvc_id LIKE 'card_0001_001%'` returned 0 on DEV). The refactor follows the **approval-line / chart-of-accounts** precedent for write-paths that mutate user-visible state: ship the audit hook (one `AuditLogService.append(...)` call per successful write) under the slug `card-transactions`, even though legacy doesn't. This is an additive divergence consistent with the refactor's documented audit philosophy ([wecms-api/CLAUDE.md §"Refactor identifier naming"](../../../CLAUDE.md)) and matches what `firm-banking-settings` / `chart-of-accounts` ship today. Track as OQ-2 — surface to the PO before the backend MR lands; default proposal is **enable audit for the 2 write endpoints (`PATCH` description / purpose; `PUT` reporting account)**.

## 11 · Open questions for PO (Mr. Jung)

These are **deliberately unresolved**. Resolve before the backend MR (Playbook step 5).

| ID | Question | Default the refactor will pick if unanswered |
|---|---|---|
| OQ-1 | **Approval-request write-path ownership.** The "RequestApproval" button on the page creates / updates an `EXPT_RESOL_MSTR` row. Is that path owned by `card-transactions` (becomes one more endpoint here) or by the future `expense-resolution` slug (this page just reads the result)? | Defer write-path to `expense-resolution` slug. `card-transactions` ships read-only join to it. |
| OQ-2 | **Audit log enablement.** Legacy JEX writes no `AUDIT_LOGS_INFM` rows for `card_0001_001`. Refactor proposes turning audit ON for the 2 write endpoints (description/purpose patch; reporting-account update). Confirm? | Enable audit with `WSVC_ID='card-transactions'`. |
| OQ-3 | **`PRCH_STTS` value set.** DEV data carries only `'1'` and NULL. What's the full legal value set and the EN/KO/VI labels for each? Needed before `payment.card_transactions.status.*` locale entries land. | Ship only the codes observed in DEV; refuse unknown codes with a degraded-label fallback (frontend renders the raw code). |
| OQ-4 | **Reporting-account "Select Account" tree** — is the picker the same `chart-of-accounts/tree` projection the existing Chart of Accounts feature ships? | Reuse `GET /api/v1/settings/chart-of-accounts/tree`. |
| OQ-5 | **HAENGSUNG `table2` divergence** — is it acceptable to ship `table2` as `dym_area_id='table2'` (per legacy column-set), or should the refactor consolidate to a single `default` area and drop the H-only variant? | Keep both areas; mirrors legacy + lets the View Setting popover do its job per product. |
| OQ-6 | **Side-memo write-path** — legacy renders memos via `wb_get_memo_cont` / `wb_get_memo_color`. Does the refactor ship the memo CRUD here, or does it port under a separate `memo` slug? | Read-only in `card-transactions`. Write-path belongs to the future `memo` slug. |
| OQ-7 | **Linked-documents (`REC_1` / `linked_docs`) e-invoice surface** — should the row carry the full e-invoice doc shape or just the `DISP_DOC_ID` + minimal badge? Legacy ships the full subset. | Ship the full subset (legacy parity). |
| OQ-8 | **Print preset** — legacy `setUpDataForPrint` builds a per-page HTML print template. Adopt the shared `lib/print.ts printReport({...})` helper? | Adopt the shared helper. |
| OQ-9 | **5-failure cap on `description` / `purpose` patches** — should we cap how often the same user can rewrite a row's text (DOS guard) or is this open? | No cap (legacy parity). |
| OQ-10 | **Scraper coexistence** — confirmed scraping stays on the JEX side. Any local seed strategy beyond the 30-row migration rule (e.g., a generator for local dev to keep the page populated)? | None. Use the 30-row real-DEV seed; local devs can SQL-append. |

## 12 · Cross-references

- JEX functional doc (read-only reference): _no `docs/jex/features/card-transactions.md` exists yet_ — flagged for parallel creation by the JEX team.
- Migration mapping: [docs/refactor-wecms/migration/card-transactions.md](../../references/migration/card-transactions.md)
- DB schema notes: [docs/refactor-wecms/database/card-transactions.md](../../references/database/card-transactions.md)
- Backend design: [docs/refactor-wecms/backend/card-transactions.md](../../references/business-rules/card-transactions.md)
- Frontend design: [docs/refactor-wecms/frontend/card-transactions.md](../../../../wecms-web/docs/features/payment/card-transactions.md)
- Parity checklist: [docs/refactor-wecms/audits/card-transactions-parity-checklist.md](../../references/audits/card-transactions-parity-checklist.md)
