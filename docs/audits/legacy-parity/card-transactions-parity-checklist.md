# Card Transactions — Parity Checklist (Refactor vs JEX)

Sign-off gate before the `card-transactions` MR merges. Tick each row only after observing the behaviour in both systems (legacy JEX on `https://dev.wecms.vn/card_0001_001.act`, refactor on `http://localhost:3000/payment/card-transactions` + `localhost:8090/api/v1/payment/card-transactions`).

> **Identifier-naming reminder** — kebab-case slug `card-transactions` is the canonical identifier across every refactor surface. Legacy `card_0001_001*` ids are used in this checklist only as JEX traceability pointers.

Format per row: state the **observable behaviour**, then check (a) what legacy does, (b) what the refactor does, (c) tick when they match (or when the intentional delta is documented in §"Intentional deltas").

---

## 1 · Page chrome

- [ ] **Title.** Legacy renders the title via ML id `card_0001_001_m001` at the top-left ([_view.jsp:251](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp#L251)). Refactor renders `t("card_transactions.title")` via `<PageHeader>`. EN labels match.
- [ ] **Dashboard hero toggle (Dashboard icon)** — both systems toggle the three-card KPI strip on/off; preference persists across reloads.
- [ ] **View Setting popover** — both systems show the column-visibility / order popover; refactor wires `screenId='card-transactions'`. The popover hides special FIELD_IDs (`CHECKBOX_1/2`, `MEMO/MEMO_2`) in both systems.
- [ ] **Print button** — both systems render a print preview reflecting the current filter + sort snapshot.
- [ ] **Excel icon** — both systems download an XLSX of the current filter + sort snapshot. Refactor uses the shared `/export` endpoint per [wecms-api/CLAUDE.md §"Excel export"](../../../CLAUDE.md); legacy generates client-side.
- [ ] **Manage Cards button** — link target intent matches (opens card management modal/screen).
- [ ] **Update button** — primary action label + state (`disabled` when no cards present) matches; legacy ML id `card_0001_001_m003`.

## 2 · Filter row

- [ ] **Date range** — preset dropdown options match (Last 30 / Last 90 / Last 180 / Custom). Default is 30 days back from today on first visit.
- [ ] **Period validation** — `FROM_DT` ≤ `TO_DT`; span cap (legacy implicit, refactor `WBE_CARD_TRANSACTIONS_PERIOD_INVALID` ≤ 365 days — OQ-B1).
- [ ] **All Card Companies dropdown** — multi-select banks; Save / Cancel buttons; selection persists in-page until cleared.
- [ ] **All Cards dropdown** — multi-select cards; Save / Cancel; same behaviour.
- [ ] **Keyword search** — `ILIKE` against `CARD_HLDR_NM` / `CARD_NM` / `CARD_NICK_NM` / `MERCH_NM` ([_r001_act.jsp:104-116](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L104-L116)). Case-insensitive on both sides.
- [ ] **Link status pills** — All / Linked / Unlinked filter the table to `T.REC_1 IS NOT NULL/NULL` ([_r001_act.jsp:140-148](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L140-L148)) in legacy and to the equivalent `EXISTS` clause in refactor.
- [ ] **Filter `onChange` resets page to 1** — both systems.

## 3 · Table data

- [ ] **Default sort** — legacy default order from `SortHelper.getSortDynamic(util, "card_0001_001_r001")` matches refactor `Sort.by(trscDt DESC, trscTm DESC, cardSeqNo DESC)`.
- [ ] **Sort cycle** — header click cycles `default → ASC → DESC → default` in both systems (refactor enforces this through `<TableHeaderCell>`).
- [ ] **Sortable columns** — `TRSC_DT`, `CARD_NO`, `MERCH_NM`, `CARD_NICK_NM`, `CARD_HLDR_NM`, `CCY_CD`, `TRSC_AMT`, `STTS`, `CARD_TRNS_USAGE_NM`, `CHART_ACCT_NM`, `CONT`. **Intentional delta:** legacy seeds `DISP_DOC_ID` and `APPR_DOC_NO` with `SORT_YN='Y'`; refactor seeds them with `SORT_YN='N'` because they're projection-only columns ([database/card-transactions.md §5.1](../../references/database/card-transactions.md#51-table1-seed-default)).
- [ ] **Non-sortable columns** — `MEMO` / `MEMO_2` (always), plus refactor's `DISP_DOC_ID` / `APPR_DOC_NO` (delta above).
- [ ] **Pagination** — default page size 15; options `[15, 20, 50, 100]` ([_view.jsp:498-503](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp#L498-L503)).
- [ ] **`X of Y` counter** — both systems render the page indicator.
- [ ] **Tenant isolation** — switching tenant resets the row set to that tenant's rows only.
- [ ] **PRDT_TYPE H variant** — HAENGSUNG user sees the `table2` column set (no `CARD_TRNS_USAGE_NM` / `CHART_ACCT_NM`); F / M users see the full `table1` set.

## 4 · Row rendering

- [ ] **`TRSC_DT` cell** — two-line: `yyyy-MM-dd` on top, small grey `HH:mm:ss` underneath ([card_0001_001.ts:1048-1056](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L1048-L1056)). Refactor mirrors via `<DateTimeCell>`.
- [ ] **`CARD_NO` cell** — two-line: `CARD_NM` chip on top, masked PAN underneath. Refactor renders the same.
- [ ] **`TRSC_AMT` cell** — right-aligned, formatted via `AmountFormatter.forDisplay(value, ccyCd)` (backend) / `formatAmount(value, ccyCd)` (frontend) — vi-VN locale + `₫` suffix for VND.
- [ ] **`CHART_ACCT_NM` cell** — composite: `CHART_ACCT_NM` on top + small grey `CHART_ACCT_LEVEL_NM` underneath. Empty rows render the "Select Account" pill ([card_0001_001.ts:1097-1115](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L1097-L1115)).
- [ ] **`STTS` cell** — legacy renders `wb_get_prch_stts_nm(prch_stts, lang_cd)`; refactor renders `t(row.statusKey ?? "")` with fallback to the raw 1-character code.
- [ ] **`CARD_TRNS_USAGE_NM` cell** — legacy renders `wb_get_usag_nm_new(...)`; refactor renders the resolved name from `UsageCodeResolver`.
- [ ] **`DISP_DOC_ID` cell** — when `linked_docs[].length > 0`, renders the first `disp_doc_id` (+ count badge for the rest). Empty when unlinked.
- [ ] **`CONT` cell** — inline-editable in both systems; debounced PATCH; success toast.
- [ ] **`APPR_DOC_NO` cell** — when present, click opens the Receipt Details dialog. Refactor opens `<ReceiptDetailsDialog stackLevel={1}>`.
- [ ] **`CHECKBOX_1/2` column** — disabled when row has `APPR_SEQ_NO` (legacy `isDisabled`); refactor mirrors via the same conditional ([card_0001_001.ts:1031](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L1031)).
- [ ] **Row background** — legacy applies `ks-bg-yellow` to rows without `APPR_SEQ_NO`; refactor uses the equivalent `wb-status-pending-bg` token.
- [ ] **`MEMO` icon** — side-memo trigger present on both sides; opens the side-memo panel.

## 5 · Dashboard hero KPIs

- [ ] **TOTAL tile** — `TOTL_CNT` rows · `TOTL_AMT` ₫. Both systems compute via the same SQL aggregate (legacy `CARD_TRNS_COUNT_R001` IDO; refactor `findLedgerTotals`).
- [ ] **APPROVAL NOT REQUESTED tile** — `TOTL_APPR_NOT_REQ_CNT` · `TOTL_APPR_NOT_REQ_AMT` ₫.
- [ ] **APPROVAL REQUESTED tile** — `TOTL_APPR_REQ_CNT` · `TOTL_APPR_REQ_AMT` ₫.
- [ ] **Tile click filtering** — legacy supports click-to-filter via `data-type="ALL"/"PA"`. Refactor defaults to **no click action** (OQ-F4); confirm with PO before MR.

## 6 · Write paths

- [ ] **Description PATCH** — set the description via inline edit; legacy `card_0001_001_u001` with `FIELD_ID='CONT'`. Refactor `PATCH /api/v1/payment/card-transactions/{cardSeqNo}` with `{ "field": "description", "value": "…" }`.
- [ ] **Purpose PATCH** — set the purpose code via popover; legacy `card_0001_001_u001` with `FIELD_ID='CARD_TRNS_USAGE_CODE'`. Refactor `PATCH ...` with `{ "field": "purpose", "value": "…" }`. **Legacy bug:** `CardTrnsHstr.setCardTrnsUsageCd` writes to `this.apprSeqNo` ([CardTrnsHstr.java:184-187](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/CardTrnsHstr.java#L184-L187)) but emits the correct field via `IdoUtils.getDynamic`, so the DB write is correct — only the in-memory Java entity drifts (harmless). Refactor writes correctly to both; flag JEX bug separately. See OQ-C in migration doc.
- [ ] **Reporting account PUT** — Select Account dialog → pick row → save. Legacy `card_0001_001_u002`; refactor `PUT .../{cardSeqNo}/reporting-account`. Both update `(CHART_ACCT_SEQ_NO, CHART_ACCT_CD)` atomically.
- [ ] **Chart account validation** — selecting a soft-deleted chart account fails. Legacy throws `WbMessageCode.WBE0001`; refactor throws `WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID`. Behaviour parity: error dialog with code + message + traceId.
- [ ] **Audit log delta** — legacy writes **0 rows** to `AUDIT_LOGS_INFM`; refactor writes 1 row per successful PATCH / PUT with `WSVC_ID='card-transactions'`. **Intentional delta** (OQ-2; default = enable). See §"Intentional deltas".
- [ ] **`MOD_ID` / `MOD_DTM` update** — every PATCH / PUT bumps both columns to the current user / current UTC second (yyyyMMddHHmmss). Both systems.
- [ ] **Optimistic-lock behaviour** — neither system version-checks `MOD_DTM` before PATCH; last-write-wins (OQ-B4). Match.
- [ ] **Empty body PATCH** — both systems reject (legacy `WbMessageCode.WBE0001`; refactor `WBE_VALIDATION`).
- [ ] **PATCH on non-existent `cardSeqNo`** — legacy throws `WbMessageCode.WBE0001` from `ValidationHelper.validateCardTrnsHstr`; refactor throws `WBE_CARD_TRANSACTIONS_NOT_FOUND` (single envelope merging not-found + wrong-tenant; same precedent as `WBE_APPROVAL_LINE_NOT_FOUND`).

## 7 · Receipt-details drill-in

- [ ] **Trigger** — click `APPR_DOC_NO` cell in both systems opens the popup.
- [ ] **Content** — title (`Receipt Details`), bulletin post info, approver list (from `EXPT_RESOL_APPRVR_INFM`), document type (`APPR_DV`), approval status.
- [ ] **Per-row data exposure** — `POST_REG_ID` is rendered correctly for "I am the requester" identification ([card_0001_001.ts:219](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/card/card_0001_001.ts#L219)). Refactor surfaces `post_reg_id` on the wire.
- [ ] **Read-only** — neither system allows editing from this dialog.

## 8 · Excel export

- [ ] **Column intersection** — both systems honour the View Setting `USE_YN='Y'` set; refactor adds the backend allow-list intersection per [wecms-api/CLAUDE.md §"Excel export"](../../../CLAUDE.md) (defence-in-depth).
- [ ] **Special FIELD_IDs dropped** — `CHECKBOX_1/2` and `MEMO/MEMO_2` never make it into the workbook.
- [ ] **Row cap** — legacy is unbounded; refactor caps at 50 000 rows → `WBE2011`. Document the delta in §"Intentional deltas".
- [ ] **Filename** — legacy hardcodes; refactor uses caller-supplied via the `ExcelExportRequest.fileName` field; matches Account Inquiry convention.
- [ ] **Amount formatting** — both systems format VND amounts as `1.234.567 ₫` (vi-VN locale).
- [ ] **Subheader** — refactor injects backend-computed `<count> transaction(s) · Total: … · Approval Requested: … · Approval Not Requested: …`. Legacy injects equivalent prose; document any wording deltas in the MR description.
- [ ] **Tenant scope** — neither system leaks rows across tenants.

## 9 · Wire envelope

- [ ] **List response shape** — refactor uses `{ status: { error, code, message }, data: { page, page_size, total_count, totals, rows } }`. Legacy uses `{ COMMON_HEAD: { ERROR, CODE, MESSAGE }, REC: [...], TOTL_*: ... }`. Both shapes round-trip the same data; documented at [migration/card-transactions.md §3.2](../../references/migration/card-transactions.md#32-idocard_trns_count_r001xml--cardtrnshstrrepositoryfindledgertotals).
- [ ] **`TOTL_LINK_CNT` / `TOTL_UN_LINK_CNT` dropped** — legacy hardcodes both to `0` ([_r001_act.jsp:242-243](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L242-L243)); refactor doesn't propagate dead fields. Documented intentional delta.
- [ ] **Error envelope** — refactor always carries `timestamp` + `path` + `trace_id` on errors; legacy does not. Refactor improvement.
- [ ] **Field naming** — refactor wire is lowercase snake_case; legacy is `UPPER_SNAKE_CASE`. Identifier-naming rule — intentional.

## 10 · Localisation

- [ ] **EN / KO / VI** labels match for table headers, dashboard tile labels, filter labels, dialog titles. Confirm via the per-language ML registrations in Jex Studio (legacy) vs `messages_*.properties` (backend) + `locales/*.json` (frontend).
- [ ] **KM (Khmer)** — legacy may have entries; refactor omits per the established stance ([wecms-api/CLAUDE.md §"i18n Rules"](../../../CLAUDE.md)). Document the delta in the MR description.
- [ ] **`Accept-Language: ko`** — server returns `status.message` in Korean; both `stts_key`-driven labels and feature labels render in Korean on the frontend.
- [ ] **Currency suffix** — VND always shows `₫`; non-VND uses currency-prefix per `formatAmount(value, ccyCd)`.

## 11 · Tenant + product gating

- [ ] **PRDT_TYPE F user** — sees `table1` view; menu visible.
- [ ] **PRDT_TYPE M user** — sees `table1` view (legacy `MENU_AUTH_ADMN_SYS` row confirms); same behaviour.
- [ ] **PRDT_TYPE H user** — sees `table2` view (no `CARD_TRNS_USAGE_NM` / `CHART_ACCT_NM` columns); menu visible.
- [ ] **Other PRDT_TYPE letters** — menu hidden in both systems (no `MENU_AUTH_ADMN_SYS` row).
- [ ] **Multi-tenant user** — link selection at login affects the data set; both systems respect the JWT-bound (or session-bound, legacy) tenant.

## 12 · Performance / scale

- [ ] **15-row page renders ≤ 800 ms** end-to-end on a warm cache against ~300 ledger rows.
- [ ] **`findLedgerPage` uses `ix_card_trns_hstr_biz_trsc_dt`** — confirm via `EXPLAIN` (without `ANALYZE`, per the MCP rules).
- [ ] **`findLedgerTotals` reuses the same index** — confirm via `EXPLAIN`.
- [ ] **`cards` endpoint** — returns within 200 ms (≤ 14 rows tenant scope).
- [ ] **Excel export 1000 rows** — completes within 5 s end-to-end (controller → POI stream → blob).

## 13 · Security

- [ ] **No JWT → 401** — every endpoint outside the auth pair.
- [ ] **Cross-tenant attempt** — passing a `bank_cd` / `card_no` that exists in another tenant returns the same response as missing data (no information leak).
- [ ] **CSRF** — disabled in refactor (`STATELESS` session); legacy uses JEX-managed session — both behave the same against the test harness.
- [ ] **Trace id presence on errors** — refactor always includes `X-Trace-Id` header + `trace_id` in body; legacy does not (refactor improvement).
- [ ] **MCP DB access policy** — verifying via `EXPLAIN` (without `ANALYZE`) honours the read-only contract in [.claude/rules/database.md](../../../../.claude/rules/database.md).

## 14 · Logging & observability

- [ ] **Server logs** — every request emits one `info` line via SLF4J under `com.kosign.wecms.domain.payment.cardtransactions` carrying the `%X{traceId}` MDC.
- [ ] **Audit log rows** — visible in `AUDIT_LOGS_INFM` after every successful PATCH / PUT, `WSVC_ID='card-transactions'`.
- [ ] **No `System.out.println`** — legacy `_u002_act.jsp:75` debug print is dropped.
- [ ] **No stack traces in `status.message`** — all error envelopes are user-safe.

## 15 · Frontend rules compliance

- [ ] **No inline `text-[Npx]` / `style={{ fontFamily/fontSize/fontWeight/lineHeight }}`** — every text surface uses the typography tokens.
- [ ] **No `response.data.UPPER_SNAKE` reads** in components — all access goes through the camelCase in-memory types via `mappers.ts`.
- [ ] **Dialog variant** — API failures use `type: "error"` (not `"warning"`); validation failures use `type: "error"`; "are you sure?" before destructive uses `type: "confirmation"`.
- [ ] **Modal stacking** — `<ReceiptDetailsDialog>` + `<SelectReportingAccountDialog>` both `stackLevel={1}` (page-level, not Settings).
- [ ] **Path alias** — every import uses `@/...`, no relative `../../../` cross-folder.
- [ ] **No new icon / form / styling library** introduced.
- [ ] **`SCREEN_IDS.CARD_TRANSACTIONS = "card-transactions"`** registered in `lib/viewSettingScreens.ts`.
- [ ] **`useExcelExport` consumed** — no per-page Excel generation.
- [ ] **`lib/print.ts printReport({...})` consumed** — no per-page `window.print()`.
- [ ] **`hooks/useExcelExport.ts` + `hooks/useRefreshKey.ts` consumed** — no copy-pasted re-implementation.

## 16 · Backend rules compliance

- [ ] **DTO Java fields camelCase**; wire is snake_case via global Jackson strategy. **No `@JsonProperty("UPPER_SNAKE")`** anywhere in the new domain package.
- [ ] **`BusinessException("WBExxxx")`** for every business error path; `messages.properties` has the matching keys.
- [ ] **Repository methods constrain on `BIZ_KEY`** in every query touching tenant-scoped tables.
- [ ] **`@Transactional`** on mutating service methods; `readOnly = true` on reads; `REQUIRES_NEW` on audit append.
- [ ] **No `wb_get_next_val` branch added** — Sprint 0 has no write-path that creates rows.
- [ ] **No new `CREATE FUNCTION`** in `db/migration/`.
- [ ] **`ddl-auto: validate`** passes on boot — every entity column matches the migrated schema.
- [ ] **`messages.properties`** carries `WBE_CARD_TRANSACTIONS_NOT_FOUND`, `WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID`, `WBE_CARD_TRANSACTIONS_FIELD_UNKNOWN`, `WBE_CARD_TRANSACTIONS_PERIOD_INVALID`.
- [ ] **Sort whitelist enum** `CardTrnsHstrSortField` exists, every `SORT_YN='Y'` seed entry has a constant.
- [ ] **`ExportRowProvider`** registered as `@Component` with `screenId="card-transactions"`.

## 17 · Cross-tree consistency

Per the playbook step 7 cross-check matrix:

- [ ] **Slug** — `/api/v1/payment/card-transactions` (backend URI) = `app/(dashboard)/payment/card-transactions` (frontend route) = `audit_logs_infm.wsvc_id='card-transactions'` = `sys_scr_option_wecms.web_svc_id='card-transactions'` = `SCREEN_IDS.CARD_TRANSACTIONS = "card-transactions"`.
- [ ] **Wire field names** — `ApiCardTransactionRow` snake_case keys match the backend DTO record field names (after the SNAKE_CASE strategy applies).
- [ ] **In-memory shape** — `CardTransactionRow` (frontend) and the JPA entity (backend) carry semantically equivalent camelCase fields; documented in `mappers.ts`.
- [ ] **Error codes** — every `WBE_CARD_TRANSACTIONS_*` thrown by backend has a matching i18n entry; frontend renders `error.code` + `error.message` + `error.traceId`.
- [ ] **Sortable columns** — `CardTrnsHstrSortField` constants ⊇ frontend sortable header set ⊇ seed `SORT_YN='Y'` set, modulo special FIELD_IDs.
- [ ] **Tenant scope** — backend enforces; frontend does not need to (token-bound).
- [ ] **View Setting screenId** — `sys_scr_option_wecms.web_svc_id` seed value = `SCREEN_IDS.CARD_TRANSACTIONS` literal value = the value the frontend posts in the export request.

## 18 · Intentional deltas (must match the documented decisions, not legacy)

These rows are checked when the refactor **deliberately diverges from legacy** — the parity expectation is "behaviour matches the documented refactor decision", not "behaviour matches legacy verbatim". Each one is referenced in the feature spec / migration / backend docs.

- [ ] **`AUDIT_LOGS_INFM` rows on PATCH / PUT** — legacy writes 0; refactor writes 1 per successful write. Default per OQ-2 (feature spec §11). Confirm PO sign-off recorded in the MR description.
- [ ] **`TOTL_LINK_CNT` / `TOTL_UN_LINK_CNT` dropped** from totals — legacy hardcodes to 0; refactor omits the fields entirely.
- [ ] **`SORT_YN='N'` for `DISP_DOC_ID` / `APPR_DOC_NO`** — legacy seeds `'Y'`; refactor seeds `'N'` because the columns are projection-only (database/card-transactions.md §5.1).
- [ ] **Wire envelope** — `{ status, data }` replaces `{ COMMON_HEAD, REC, TOTL_* }`. Identifier-naming + envelope rule.
- [ ] **Wire field names** — lowercase snake_case replaces UPPER_SNAKE_CASE.
- [ ] **Java identifiers** — `CardTransactionsController` / `card-transactions` package replaces `card_0001_001_r001_act`.
- [ ] **Excel row cap** — 50 000 rows (`WBE2011`) replaces legacy unbounded export.
- [ ] **Trace id on errors** — present on refactor; absent on legacy. Refactor improvement, not a regression.
- [ ] **Single discriminated PATCH endpoint** — replaces legacy `card_0001_001_u001` with `FIELD_ID`/`FIELD_VAL` envelope. Same behaviour, cleaner shape.
- [ ] **Legacy `wb_get_*` DB functions retired** — `wb_get_bank_nm` / `wb_get_prch_stts_nm` / `wb_get_usag_nm_new` / `wb_get_chart_acct_nm` / `wb_get_chart_acct_lv_nm` are replaced by Java services (`BankCdResolver`, `MessageResolver`, `UsageCodeResolver`, `ChartOfAccountsTreeBuilder`). Outcomes match end-to-end.
- [ ] **`km.json` locale** entries — refactor omits Khmer.
- [ ] **Description / purpose inline-edit** — refactor uses inline-editable cells (autosave); legacy uses the same inline-edit pattern but via WeCMS's own cell editors. Behaviour match.
- [ ] **Filter persistence** — legacy persists last filter set via `_v2common.app` cookies; refactor does not (OQ-F1). Document in the MR description; await PO sign-off.

## 19 · Sign-off

- [ ] All §1–§17 boxes ticked OR backed by a documented OQ resolution.
- [ ] All §18 intentional deltas signed off by PO (Mr. Jung).
- [ ] [features/card-transactions.md §11](../../features/payment/card-transactions.md#11--open-questions-for-po-mr-jung) OQ-1 through OQ-10 each have a recorded resolution (and any code/text changes those resolutions imply are in the MR).
- [ ] `docs/refactor-wecms/features/card-transactions.md` Status flipped to `Implemented`.
- [ ] Bug reports filed for any JEX defects discovered (e.g. the `setCardTrnsUsageCd` typo in [CardTrnsHstr.java:184-187](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/CardTrnsHstr.java#L184-L187)) under `docs/bug-reports/` with `System: JEX` per [docs/_shared](../../../../docs/_shared/).

---

## Cross-references

- Feature spec: [docs/refactor-wecms/features/card-transactions.md](../../features/payment/card-transactions.md)
- Migration mapping: [docs/refactor-wecms/migration/card-transactions.md](../../references/migration/card-transactions.md)
- Backend design: [docs/refactor-wecms/backend/card-transactions.md](../../references/business-rules/card-transactions.md)
- Frontend design: [docs/refactor-wecms/frontend/card-transactions.md](../../../../wecms-web/docs/features/payment/card-transactions.md)
- Database schema notes: [docs/refactor-wecms/database/card-transactions.md](../../references/database/card-transactions.md)
- Playbook (process): [docs/refactor-wecms/MIGRATION_PLAYBOOK.md](../../references/migration/migration-playbook.md)
- Workspace rules: [/CLAUDE.md](../../../../CLAUDE.md)
- Spring backend rules: [wecms-api/CLAUDE.md](../../../CLAUDE.md)
- Next.js frontend rules: [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md)
- DB MCP rules: [/.claude/rules/database.md](../../../../.claude/rules/database.md)
