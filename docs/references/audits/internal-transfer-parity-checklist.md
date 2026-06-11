# Internal Transfer — Refactor Parity Checklist

| Field | Value |
|---|---|
| Slug | `internal-transfer` |
| Module | `payment` |
| Companion docs | [features/payment/internal-transfer.md](../../features/payment/internal-transfer/README.md) · [features/payment/README.md](../../features/payment/README.md) · [migration/internal-transfer-mapping.md](../migration/internal-transfer-mapping.md) · [backend/internal-transfer.md](../business-rules/internal-transfer.md) · [frontend/internal-transfer.md](../../../../wecms-web/docs/features/payment/internal-transfer/README.md) · [database/payment/internal-transfer-schema.md](../database/payment/internal-transfer-schema.md) |
| Sprint state | **Phase A landed (backend + frontend data-layer + upload-dialog wrapper).** Phase B (page rewiring + dispatch-table writes + ERP_TRNS_HSTR + CHART_ACCT guard) outstanding. Last verified 2026-05-21 against `wecms-api` + `wecms-web` HEAD. |

> Boxes ticked `[x]` reflect what was verified against the implemented code on the last-verified date. Boxes left `[ ]` are Phase B + Prompt-4-style sign-off items that need a follow-up review pass. Where a row reads `(Phase B)`, that work is intentionally deferred — not failed.

---

## 0. Follow-up — CA-G10: chart-account validation gap on reporting-account write

> Filed 2026-06-08 from the Chart of Accounts final parity audit
> ([../../audits/chart-of-accounts-final-parity-audit.md](../../audits/chart-of-accounts-final-parity-audit.md), gap **CA-G10**).
> **This is a Payment / Internal-Transfer defect — it does NOT gate Chart of Accounts SIT.**

| Field | Value |
|---|---|
| **Issue** | `InternalTransferService` persists a reporting account (`chart_acct_cd` + `chart_acct_seq_no`) onto `SCDUL_PYMN_SUMR_DETL` **without validating the chart account exists / is not soft-deleted**. |
| **Legacy evidence** | `pymn_0009_002_u002_act.jsp:92-97` — builds a dynamic on `BIZ_KEY + CHART_ACCT_SEQ_NO + DEL_YN='N'`, reads `CHART_ACCT_INFM_R001`, and throws `WBE0404` when the row is missing. (Legacy checks existence + not-deleted; it does NOT check `STTS='A'`.) |
| **Refactor evidence** | `InternalTransferService.java:572-577` (update path) skips the lookup behind a stale/false comment "CHART_ACCT_INFM entity not yet migrated"; create path `:503-504` sets the code/seq with no lookup. The entity **is** migrated and is validated by the sibling features `TransactionService.java:355-362` and `CardTransactionsService.java:1678-1690` (both check existence + `DEL_YN='N'` + `STTS='A'`). |
| **Impact** | A deleted / non-existent `chart_acct_cd+seq` can be written onto a scheduled-transfer line; downstream chart-name/level joins resolve to blank. Bounded (no financial-amount corruption, no cascade). UI-constrained (the picker offers only valid active accounts) — fully open to a direct API caller or a delete-after-pick race. |
| **Classification** | **MEDIUM** — confirmed parity **regression** (legacy validated; refactor does not), master-data referential integrity, with a false justifying comment. |
| **Required fix** | In `InternalTransferService` create + update reporting-account paths, reject an unknown/soft-deleted chart account before persisting — reuse the sibling guard (`findByBizKeyAndChartAcctCdAndDelYn(..., "N")`, optionally also `"A".equals(stts)` which would exceed legacy). Delete the stale "not yet migrated" comment. Lands with Phase B (the Sprint state line already lists "CHART_ACCT guard" as outstanding). |
| **Do NOT** | fix this inside the Chart of Accounts task/MR — it is a separate feature's defect. |

---

## 1. Per-service parity matrix

One row per JEX service id in scope. Columns: DTO field parity (every WSVC `<in>`/`<out>` field has a Java DTO mirror with the right name + length), Validation parity (every `ValidationHelper.*` call has a `@NotBlank` / `@Size` / `@Pattern` or service-layer cross-field check), Permission parity (R / U / D / T flag honoured), Audit-log parity (`AUDIT_LOGS_INFM` row written with `WSVC_ID='internal-transfer'`), UI parity (the matching Next.js page renders the right columns / fields), i18n parity (EN + KO + VI entries — KH explicitly skipped).

| JEX service id | Refactor endpoint | DTO fields | Validation | Permission | Audit log | UI | i18n (EN/KO/VI) |
|---|---|---|---|---|---|---|---|
| `pymn_0009_001_r001` (list) | `GET /api/v1/payment/internal-transfer` | [x] | [x] | [x] R | [ ] (n/a — read-only) | [ ] Phase B (page rewire) | [x] (shared `payment.appr_stts.*` keys seeded) |
| `pymn_0009_001_c001` (execute / dispatch) | `POST /api/v1/payment/internal-transfer/{id}/dispatch` | [x] | [x] (cut-off, approval-status) | [x] T | [x] `op=dispatch` | [ ] Phase B (page rewire) | [x] (`internal_transfer.error.*` keys + cut-off message keys seeded) |
| `pymn_0009_001_d001` (delete) | `DELETE /api/v1/payment/internal-transfer/{id}` | [x] | [x] (approval-locked guard) | [x] D | [x] `op=delete` (Phase A: no ERP_TRNS_HSTR write — file storage deferred) | [ ] Phase B (page rewire) | [x] (`internal_transfer.error.appr_locked` key seeded) |
| `pymn_0009_002_r001` (parse Excel) | `POST /api/v1/payment/internal-transfer/uploads/parse` | [x] | [x] (file, bank_cd) | [x] R | [ ] (n/a — read-only) | [ ] Phase B (upload dialog wire) | [ ] Phase B (upload dialog) |
| `pymn_0009_002_c001` (save draft) | `POST /api/v1/payment/internal-transfer` | [x] | [x] (title, bank, account, currency, per-line FINC_MDA_NO via `@Valid`/`@NotBlank`/`@Pattern`) | [x] U | [x] `op=create` | [ ] Phase B (upload dialog wire) | [ ] Phase B (upload dialog) |
| `pymn_0009_002_u002` (update reporting account) | `PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account` | [x] | ⚠️ **CA-G10 regression** — CHART_ACCT existence guard MISSING (see Follow-up §0 below). The "deferred until that entity is migrated" comment is stale/false: `CHART_ACCT_INFM` IS migrated. Legacy validates it (`pymn_0009_002_u002_act.jsp:92-97` → `WBE0404`). | [x] U | [x] `op=update` | [ ] Phase B (CHART_ACCT picker wire) | [ ] Phase B |
| `pymn_0009_003_r001` (detail) | `GET /api/v1/payment/internal-transfer/{id}` | [x] | [x] | [x] R | [ ] (n/a — read-only) | [ ] Phase B (page rewire) | [x] (shared keys seeded) |
| `pymn_0009_003_r002` (legacy Excel template) | `POST /api/v1/payment/internal-transfer/{id}/export` (shared XLSX) | [ ] | [ ] (column allow-list) | [ ] R | [ ] (n/a) | [ ] (deferred to follow-up MR — shared XLSX provider) | [ ] |
| `pymn_0010_001_r001` (result list) | `GET /api/v1/payment/internal-transfer/results` | [x] | [x] | [x] R | [ ] (n/a) | [ ] Phase B (page rewire) | [x] (`payment.proc_stat.*` keys seeded) |
| `pymn_0010_002_r001` (result detail) | `GET /api/v1/payment/internal-transfer/results/{id}` | [x] | [x] | [x] R | [ ] (n/a) | [ ] Phase B (page rewire) | [x] (`payment.proc_stat.*` keys seeded) |
| `pymn_0010_002_r002` (legacy Excel template) | `POST /api/v1/payment/internal-transfer/results/{id}/export` (shared XLSX) | [ ] | [ ] | [ ] R | [ ] (n/a) | [ ] (deferred to follow-up MR — shared XLSX provider) | [ ] |

Total: 11 rows × 6 capability columns = 66 parity assertions. Every box must be ticked or marked `n/a` with reason before Prompt 4 sign-off.

## 2. Cross-cutting parity (must all be `Yes`)

| Concern | Status | Evidence (file:line or n/a) |
|---|---|---|
| Slug `internal-transfer` used in every refactor surface (REST URI, Java package, DTOs, audit `WSVC_ID`, log tag, View Setting screenId) | [x] | Verified: `/api/v1/payment/internal-transfer*` route base, `com.kosign.wecms.domain.payment.internaltransfer` package, `WSVC_ID = "internal-transfer"` at [InternalTransferService.java:113](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java#L113), `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_*` at [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts). |
| No legacy WSVC id appears as a Java class name, REST URI segment, package name, i18n key, log tag, or `AUDIT_LOGS_INFM.WSVC_ID` value | [x] | Verified — `pymn_0009`/`pymn_0010` appears only inside JEX file citations (Javadoc + migration prose). |
| Multi-tenant scope — every JPQL / repository call constrains on `BIZ_KEY = TenantContext.currentBizKey()` | [ ] | (Phase B reviewer — sweep `InternalTransferService` + `InternalTransferResultService` + `InternalTransferDispatchService` repository call sites.) |
| Schema parity — every table touched has matching DEV column count, types, lengths, defaults, PK, UK, CHECK, applicable FKs | [x] *(with documented refactor-only deviation)* | V131 / V133 / V136 / V146 / V148 / V149 / V188 / V189 shipped. **Refactor-only deviation:** `SCDUL_PYMN_NO VARCHAR(20)` (DEV is 10) — documented in [database/payment/internal-transfer-schema.md §3.1 + §7](../database/payment/internal-transfer-schema.md). |
| No new `wb_get_next_val` branches added in feature code | [x] | Verified — refactor uses [`InternalTransferKeyGenerator`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java) Java-side; no new `wb_get_next_val` branch in V131 or follow-ups. |
| Java SCDUL_PYMN_NO generator implemented and `INTERNAL_TRANSFER_KEY_SEQ` helper table seeded with 0 rows (lazy populated) | [x] | `InternalTransferKeyGenerator.nextScdulPymnNo` at L77; helper table created in V131. |
| `wecms.internal-transfer.storage-root` configured; saved uploads write under that root only — never legacy paths | [ ] *(Phase B)* | `InternalTransferStorageProperties` exists; the writer is **not** wired in Phase A — `createDraft` skips disk persistence per backend §15.6. |
| Cut-off-time validator runs Vietnam local time (UTC+7) and matches the legacy table for Woori + Shinhan | [x] | `CutoffValidator` + `VIETNAM_ZONE` constant in [InternalTransferKeyGenerator.java:63](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java#L63); 9 unit tests in `CutoffValidatorTest`. |
| Routing decision (TB_PAY_TARGET vs TB_PAY_FILE_MST) mirrors `PymnHelper.pymn_0009_001_c001` flag logic byte-for-byte | [ ] *(Phase B)* | Dispatch service Phase A stamps `TRNF_YN='Y'` + audit only; routing port + `TransferDispatchExecutor` consumption deferred per backend §15.6. |
| Audit row `WSVC_ID` literal is `"internal-transfer"`, never `"pymn_0009_001"` | [x] | `static final String WSVC_ID = "internal-transfer"` at [InternalTransferService.java:113](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java#L113); 5 `.wsvcId(WSVC_ID)` call sites at L426 / L473 / L536 / L609 / L813. |
| Frontend wire types in `lib/types/api.ts` use lowercase `snake_case` (no `Api*` UPPER_SNAKE types added for this feature) | [x] | Verified per frontend §14.3. |
| Mapper translation at the boundary only (no `response.data.scdul_pymn_no` access in components) | [x] | Mappers in [lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts) translate at the API-client boundary. |
| Typography — no `text-[Npx]`, `font-[N]`, `tracking-[…]`, or inline `style={{ fontFamily \| fontSize \| fontWeight \| lineHeight }}` | [x] | Verified 2026-05-22 — 0 matches across `components/payment/internal-transfer/` per frontend §16.2. |
| Every visible string goes through `t(...)` and exists in `locales/en.json` + `ko.json` + `vi.json` | [ ] *(Phase B)* | Upload-dialog strings shipped (`internal_transfer.upload.*` + `.dialog.save_success` + `.error.*`). List/detail page strings (`internal_transfer.title`, `.column.*`, `.action.*`) land when pages rewire. |
| `km.json` has no `internal_transfer.*` keys (KH deferred) | [x] | No `km.json` in `locales/`. |
| Dialog variants — every `useDialog().open` matches the variant table per [wecms-web/CLAUDE.md § "Dialog variant"](../../../../wecms-web/CLAUDE.md#dialog-variant--choose-the-right-type-project-wide-rule-2026-05-12) | [ ] *(Phase B)* | Verified within `<InternalTransferUploadDialog>`; remaining dialog flows land when pages rewire. |
| Pagination state via `usePagination` only (no per-page `useState` for `page`/`pageSize`) | [ ] *(Phase B)* | Will be verified when pages consume `useApiData` + `usePagination`. |
| Sort via shared `useTableSort` + per-domain whitelist enum on backend | [x] backend / [ ] *(Phase B)* frontend | Backend: [`InternalTransferSortField`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferSortField.java) + [`InternalTransferResultSortField`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferResultSortField.java) implement `SortableField`. Frontend wiring lands in Phase B. |
| View Setting `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_*` constants registered + `SCREEN_ID_TO_ROUTE` entries land in same MR | [x] | Verified at [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts) — all 4 constants present. |
| Excel export via shared `useExcelExport` + `<ExportRowProvider>` (no per-page CSV / xlsx-js code) | [x] backend / [ ] *(Phase B)* frontend | [`InternalTransferResultExportProvider`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/export/) exists. Frontend `useExcelExport` wiring lands in Phase B. |
| Amount values formatted via `formatAmount(value, ccyCd, true)` only — no `Intl.NumberFormat`, `toLocaleString()`, or template-literal `${n}₫` | [ ] *(Phase B)* | Will be verified during page rewiring. |
| Internal Transfer Result list page omits totals footer (`paginationOnly`) — only Payment Result variant that does | [ ] *(Phase B)* | Documented as the intended behaviour in [frontend/internal-transfer.md §2.3](../../../../wecms-web/docs/features/payment/internal-transfer/README.md); enforced when `<TransferResultPage>` is rewired. |
| Detail pages route through `<PaymentDetailPage>` / `<ResultDetailView>` and pass `<Pagination hidden>` | [ ] *(Phase B)* | Static-UI composes the shared shells; live-data path lands with Phase B. |
| File card download wrapper `size-5`, icon `size-4 brightness-0 invert` | [ ] *(Phase B)* | Phase B verifies via the live `FileCard` consumer. |
| Calendar icon via `<CalendarIconButton>` only — no `lucide-react`'s `<Calendar>` substitution and no inline `<img src=".../ico-calendar-outline.svg">` | [ ] *(Phase B)* | Phase B verifies. |
| `appr_stts_key` / `proc_stat_key` wire fields populated server-side; frontend renders via `t(row.apprSttsKey)` | [ ] *(Phase B)* | Backend Phase A returns these as `null` (depends on EXPT_RESOL_MSTR join). Populated in Phase B with the approval-box join + dispatch-tables join. |
| Error codes — `WBE2060`–`WBE2068` added to `messages.properties` before being thrown | [x] | Verified per backend §15.3. (Earlier design names `WBE1162` / `WBE1167` / `WBE1168` / `WBE2017` / `WBE2018` are superseded.) |
| GlobalExceptionHandler unchanged — no new handler introduced for this feature | [x] | Verified — no changes to [`GlobalExceptionHandler.java`](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java). |
| ERP_TRNS_HSTR row written on draft delete (legacy parity from `pymn_0009_001_d001_act.jsp:97`) | [ ] *(Phase B)* | Phase A `delete` writes audit row; ERP_TRNS_HSTR write deferred until file-storage path is wired (backend §15.6). |
| `EXPT_RESOL_MSTR.APPR_TYPE_CD == '01'` check before dispatch | [ ] *(Phase B)* | Deferred — currently relies on the Approval Box workflow to populate `APPR_SEQ_NO` only after approval (backend §15.6 item 4). |
| `TRNF_YN` stamped to `'Y'` on dispatch success | [x] | `InternalTransferDispatchService.dispatch:69` stamps `TRNF_YN='Y'` + audit row. |
| Concurrent dispatch on the same batch rejected (transaction isolation or row-level lock) | [ ] *(Phase B)* | Concurrency test deferred to Phase B alongside dispatch-tables wiring. |

## 3. Behaviour delta — recorded intentional differences (each must be approved by PO before sign-off)

From [migration/internal-transfer-mapping.md § 3](../migration/internal-transfer-mapping.md#3-behavior-delta-from-jex):

| Delta | Approved by | Notes |
|---|---|---|
| Slug `internal-transfer` (not `pymn_0009_001`) in `AUDIT_LOGS_INFM.WSVC_ID` | [ ] | workspace CLAUDE.md rule 2026-05-09 |
| Excel-download legacy services `pymn_0009_003_r002` + `pymn_0010_002_r002` unified into shared XLSX `POST .../export` | [ ] | api CLAUDE.md "Excel export" rule |
| Permission `T` becomes a dedicated REST verb `POST .../dispatch` | [ ] | refactor REST naming |
| `pageSize` query param replaces legacy `PAGE_PER_CNT` | [ ] | wecms-web `<Pagination>` rule |
| Sort wire fields `sortField` / `sortDirection` via common-sort whitelist | [ ] | api CLAUDE.md "Common table sort" |
| SCDUL_PYMN_NO format `<YYYYMMDD><seq:6>` from Java generator | [ ] | OQ-3 |
| `appr_stts_key` / `proc_stat_key` siblings on the wire | [ ] | api CLAUDE.md "Domain enum keys" |
| `wecms-web` Internal Transfer Result list omits totals footer (only Payment Result variant that does) | [ ] | OQ-8 |
| Audit-history triggers (`_log_h`) NOT ported; refactor uses `AUDIT_LOGS_INFM` | [ ] | api CLAUDE.md V24 precedent |
| `SCDUL_PYMN_SUMR_HIS` / `SCDUL_PYMN_SUMR_DETL_HIS` column-parity-only, not written by refactor | [ ] | database doc § 6 |
| **Single transfer network per upload file** (no mixed CITAD / NAPAS in one Excel) — `UploadConsistencyValidator.assertSingleNetwork` → `WBE2121` | [x] **PO-confirmed 2026-06-08** | **Confirmed business rule:** one uploaded file must contain only one network type. The refactor's strict mixed-network rejection is **intended and correct — NOT a parity gap.** Resolves the "refactor stricter — verify legacy intent" item flagged in the Upload Internal Transfer audit (the amount / chart-account / network validation triad). Sibling rule `assertSingleCurrency` (`WBE2120`, one currency per file) carries the same confirmation. |

## 4. Open questions resolution (OQ-1 … OQ-8 from feature spec)

| OQ | Status | Resolution |
|---|---|---|
| OQ-1 (CLARIFY-1) — `APPR_STTS` code → label mapping | [ ] | Still open. Phase A returns `appr_stts` / `appr_stts_key` as null; verified mapping lands with the EXPT_RESOL_MSTR join in Phase B. |
| OQ-2 (CLARIFY-2) — Excel upload size cap | [ ] | PO confirmation still pending; placeholder `wecms.internal-transfer.upload.max-bytes = 5 MB` in design (not yet enforced — Excel parse is a Phase A stub). |
| OQ-3 (BLOCKER-A) — SCDUL_PYMN_NO generator format | [x] **RESOLVED 2026-05-14** | Refactor widens column to `VARCHAR(20)` and uses Java-side `YYYYMMDD<seq:6>` generator. See [database/payment/internal-transfer-schema.md §7](../database/payment/internal-transfer-schema.md). |
| OQ-4 (CLARIFY-3) — WSVC scope deviation (no `_c002`; `_r002` = excel template) | [x] | Confirmed by JEX file listing (feature doc §3) + verified again 2026-05-22 — the `_r002` wrappers are `viewUseYn=Y, actUseYn=N` templates, not act endpoints. |
| OQ-5 (CLARIFY-4) — Approval workflow contract source | [x] | Resolved — Internal Transfer's `POST /{id}/request-approval` + `/{id}/approval-action` delegate to shared `ApprovalBoxService` (`WSVC_ID = "approval-box"`). |
| OQ-6 (CLARIFY-5) — Cut-off table source of truth | [x] | YAML properties via `CutoffValidator`. No new `BANK_CUTOFF_INFM` table. |
| OQ-7 (CLARIFY-6) — AntDivCd "INTER" coercion | [ ] *(Phase B)* | Currently un-tested; lands with dispatch-tables wiring. |
| OQ-8 (CLARIFY-7) — Multi-currency totals on Result | [ ] *(Phase B)* | Internal Transfer Result `paginationOnly` exception documented in [frontend/internal-transfer.md §16.1](../../../../wecms-web/docs/features/payment/internal-transfer/README.md); enforced when `<TransferResultPage>` is rewired. |
| BLOCKER-B — `proc_stat` column width on dispatch tables | [ ] | Verify shipped column width in V148 / V189 vs `ProcStat` enum codes on next parity sweep. Phase A dispatch does not write TB_PAY_TARGET rows so runtime impact is zero today. |

## 5. Sign-off

When every box above is ticked or explicitly waived with a reason:

- [ ] Backend MR (`wecms-api`) reviewed and merged on `feature/internal-transfer`.
- [ ] Frontend MR (`wecms-web`) reviewed and merged on `feature/internal-transfer`.
- [ ] Feature spec status flipped to `Implemented` ([features/internal-transfer.md](../business-rules/internal-transfer.md)).
- [ ] PO (Mr. Jung) confirmed every behaviour-delta row in § 3.
- [ ] Local end-to-end demo run by tester (Mr. Youvita): upload → save → request approval → execute → result detail.

Reviewer:  ______________________   Date: ______________________

---

Last verified: 2026-05-22

**2026-06-08 addendum (status-only, no code change):** Upload Internal Transfer validation audit follow-ups recorded — (1) **amount must be > 0** and (2) **Reporting Account must be `DEL_YN='N' AND STTS='A'`** were both implemented in the payment Excel parsers (legacy-parity fixes, verified by `InternalTransferExcelParserTest` + backend build); (3) **mixed CITAD/NAPAS per file** is confirmed intended behaviour and approved (see § 3 single-network row) — not a gap.
