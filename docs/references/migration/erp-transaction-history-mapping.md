# ERP Transaction History — JEX ↔ Spring REST mapping

**Refactor slug:** `erp-transaction-history`
**JEX path:** Settings > ERP Transaction History (`comm_0219_001` family — `r001`, `c001`, `r002`).
**Sibling docs:** [feature spec](../../features/settings/transaction-history.md) · [DB schema](../database/settings/erp-transaction-history-schema.md) · [backend design](../business-rules/settings/erp-transaction-history.md) · [frontend design](../../../../wecms-web/docs/features/settings/transaction-history.md) · [plan](../../planning/settings/erp-transaction-history-plan.md).

This is the JEX → Spring service-by-service mapping. Each row pairs one
legacy artifact (WSVC operation + IDO + JSP) with its Spring REST endpoint,
controller, service, DTO, entity, and field-level wire shape. Legacy WSVC
ids appear here only as JEX `file:line` citations (the refactor's
`AUDIT_LOGS_INFM.WSVC_ID` writes the slug — see [wecms-api/CLAUDE.md §"Refactor identifier naming"](../../../CLAUDE.md)).

---

## 1. WSVC ↔ REST endpoint map

| JEX WSVC | JEX operation | Refactor endpoint | Spring component | Scope |
|---|---|---|---|---|
| `comm_0219_001_r001` ([WSVC](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0219_001_r001.xml) · [act](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_r001_act.jsp)) | Inquiry ERP Transaction Histories — paged list + 4 counts | `GET /api/v1/settings/erp-transaction-history` | `ErpTrnsHistoryController#list` → `ErpTrnsHistoryService#search` | **Phase 2 — in scope** |
| `comm_0219_001_r002` ([WSVC](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0219_001_r002.xml) · [view](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/api/comm/comm_0219_001_r002_view.jsp)) | Download attached file (full / thumbnail) | `GET /api/v1/settings/erp-transaction-history/{seqNo}/download` (full) and `GET .../{seqNo}/thumbnail` (640px) | `ErpTrnsHistoryController#download` / `#thumbnail` → `ErpTrnsHistoryService#streamFile` | **Phase 2 — in scope** |
| `comm_0219_001_c001` ([WSVC](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0219_001_c001.xml) · [act](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_c001_act.jsp)) | Upload an ERP file (generic) | **NOT ported.** Every Payment / Transfer / Account-Inquiry upload feature writes its own `erp_trns_hstr` row from its own service. | — | **Out of scope** — see [feature spec §9 OQ-1](../../features/settings/transaction-history.md) |
| `comm_0219_001` (View) | Page view JSP (no rule/in/out) | Settings modal panel — `components/settings/erp-transaction-history/ErpTransactionHistoryPanel.tsx` (Next.js, no backend). | — | **N/A — frontend only** |

The download endpoint is split into `download` + `thumbnail` to surface the
JEX `?cmd=getthumb` switch as two named REST resources. Frontends pick the
correct one based on whether the row is an image preview (e.g. future Smart
Organizer integration) — current Phase 2 frontend uses `download` only.

---

## 2. IDO ↔ Repository / SQL map

| Legacy IDO | Operation | Refactor implementation |
|---|---|---|
| [`ERP_TRNS_HSTR_R001`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_R001.xml) — default SELECT with `WB_GET_USER_NM(REG_ID)` join | List page slice. | `ErpTrnsHstrRepository#search(...)` — single JPQL `search` over `ErpTrnsHstr` entity, joined to `UserInfm` for the `userNm` projection (LEFT JOIN on `(REG_ID, BIZ_KEY)` so missing/`SYSTEM` users yield `null`). Pagination via Spring Data `Pageable`. |
| [`ERP_TRNS_HSTR_R001_CNT`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_R001_CNT.xml) — aggregate count with case-sum on `ERP_TYPE` | 4 tab counts. | Same `ErpTrnsHstrRepository#countByDirection(...)` JPQL returning a typed record `(totalCnt, wecmsCnt, erpCnt, deletedCnt)`. Mirrors the JSP's `getDynamicCNT` which drops the `ERP_TYPE` predicate from the filter set. Executed inside the same service call as `search()` so list + counts arrive in one round trip (atomic from the tab UI's perspective). |
| [`ERP_TRNS_HSTR_C001`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_C001.xml) — INSERT | Upload write. | **Not refactored** (see WSVC mapping above). Other features write their own rows via `ErpTrnsHstrAuditWriter` (Phase 3+ — out of this feature's scope). |
| [`ERP_TRNS_HSTR_U001`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_U001.xml) — UPDATE | Soft-delete or metadata patch. | **Not refactored.** Soft-delete (`ERP_TYPE='D'`) lands with each Payment feature's "Cancel" flow. |
| [`ERP_TRNS_HSTR_D001`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.ERP_TRNS_HSTR_D001.xml) — DELETE | Hard delete. | **Not refactored** — the JEX schema is append-only in practice; delete IDO exists but is unused in live traffic. |

---

## 3. Request field map (`r001` list)

| Legacy JEX field (`comm_0219_001_r001` IN) | Wire JSON (snake_case) | Java DTO field (camelCase) | Required | Validation |
|---|---|---|---|---|
| `FROM_DT` (8 chars) | `from_dt` | `fromDt: String` | yes | `@Pattern("\\d{8}")` + service-side `LocalDate.parse(YYYYMMDD)` |
| `TO_DT` (8 chars) | `to_dt` | `toDt: String` | yes | `@Pattern("\\d{8}")` + `from ≤ to` guard (`WBE_VALIDATION`) |
| `ERP_TYPE` (`W` / `E` / `D` / `""`) | `erp_type` | `erpType: String` | no | `@Pattern("[WED]?")` (empty allowed) |
| `TRNS_TYPE_CD` (`01..17` or `""`) | `trns_type_cd` | `trnsTypeCd: String` | no | `@Pattern("[0-9]{2}?")` |
| `SRCH_WD` (≤ 100) | `srch_wd` | `srchWd: String` | no | `@Size(max=100)`; LIKE-escape inside the service (mirror `FirmBankingHistoryService.escapeLikePattern`) |
| `PAGE_NO` (numeric string, 1-based) | `page` | `page: Integer` | no | `@Min(1)`; default `1`; converted to 0-based `Pageable` in the service |
| `PAGE_PER_CNT` | `page_size` | `pageSize: Integer` | no | `@Min(1) @Max(100)`; default `15` |
| sort token (legacy `paramSort`) | `sort_field` + `sort_direction` | `sortField: String`, `sortDirection: String` | no | Whitelisted via `ErpTrnsHistorySortField` per [wecms-api/CLAUDE.md §"Common table sort"](../../../CLAUDE.md) |

Tenant: `BIZ_KEY` is read from `TenantContext.currentBizKey()` — never wire input.

Legacy `paramSort` was a free-form string; the refactor splits it into the
two query parameters `sort_field` (uppercase FIELD_ID) and `sort_direction`
(`ASC` / `DESC`) per the shared common-sort contract.

---

## 4. Response field map (`r001` list)

### 4.1 Envelope

| Legacy WSVC `out` | Wire JSON | Java DTO |
|---|---|---|
| `REC` (RECORD) | `data.list[]` | `List<ErpTrnsHistoryRow>` |
| `TOT_CNT` | `data.total_count` | `totalCount: long` |
| `TOT_CNT_WECMS` | `data.total_count_wecms` | `totalCountWecms: long` |
| `TOT_CNT_ERP` | `data.total_count_erp` | `totalCountErp: long` |
| `TOT_CNT_DELETED` | `data.total_count_deleted` | `totalCountDeleted: long` |
| — | `data.total_pages` | `totalPages: int` |
| — | `data.page_number` | `pageNumber: int` (1-based) |
| — | `data.page_size` | `pageSize: int` |

### 4.2 `REC[]` rows

| Legacy `REC.<field>` | Wire JSON | Java DTO | Origin |
|---|---|---|---|
| `SEQ_NO` | `seq_no` | `seqNo: long` | `erp_trns_hstr.seq_no` |
| `ORG_FILE_NM` | `org_file_nm` | `orgFileNm: String` | `erp_trns_hstr.org_file_nm` |
| `SAVE_FILE_NM` | `save_file_nm` | `saveFileNm: String` | `erp_trns_hstr.save_file_nm` |
| `FILE_SIZE` | `file_size` | `fileSize: long` | `erp_trns_hstr.file_size` |
| `ERP_TYPE` | `erp_type` | `erpType: String` | raw `W` / `E` / `D` (frontend resolves the label) |
| — | `erp_type_key` | `erpTypeKey: String` | i18n key `settings.erp_transaction_history.direction.<wecms_to_erp|erp_to_wecms|deleted>` — per [wecms-api/CLAUDE.md §"Domain enums"](../../../CLAUDE.md), backend emits both the raw code and the localisation key |
| `TRNS_TYPE_CD` | `trns_type_cd` | `trnsTypeCd: String` | raw `01..17` |
| — | `trns_type_key` | `trnsTypeKey: String` | i18n key `settings.erp_transaction_history.type.<NN>` |
| `REG_DTM` (`yyyyMMddHHmmss`) | `reg_dtm` | `regDtm: String` | passed through verbatim — frontend splits into date + time line via `lib/datetime.ts` |
| `REG_ID` | `reg_id` | `regId: String` | `erp_trns_hstr.reg_id` |
| `USER_NM` | `user_nm` | `userNm: String` | `USER_INFM.USER_NM WHERE USER_ID = REG_ID AND BIZ_KEY = tenant` (replaces legacy `WB_GET_USER_NM(REG_ID)` — OQ-4 in the feature spec). `null` when `REG_ID = 'SYSTEM'` or the user no longer exists; frontend falls back to `regId`. |
| `FILE_URL` (legacy convenience) | — | — | **Not emitted.** Frontend composes the download URL from `seq_no` directly. |

### 4.3 Empty-result rule

The legacy IDO returns zero `REC` rows + `TOT_CNT=0` / `TOT_CNT_WECMS=0` /
etc. when nothing matches. Spring side does the same: `list = []`,
`total_count = 0`, `total_pages = 0`, `page_number = 1`, `page_size = (input)`.

---

## 5. Field map (`r002` download)

| Legacy WSVC IN | Wire | Java | Validation |
|---|---|---|---|
| `SEQ_NO` (path) | path variable `{seqNo}` | `@PathVariable Long seqNo` | `@Positive` |

Legacy WSVC OUT is empty — the response is an `application/octet-stream`
binary stream with `Content-Disposition: attachment; filename="<save_file_nm>"`.
Refactor mirrors verbatim and adds RFC 5987 `filename*=UTF-8''...` so
non-ASCII filenames round-trip correctly.

Thumbnail variant (`?cmd=getthumb`) → refactor surfaces as a separate
endpoint `GET .../{seqNo}/thumbnail` returning `image/jpeg` at max width
640 px. Same auth + tenant gate as the full download.

Errors (mapped on `ApiResponse` envelope):

- Row not found / not in tenant → 404 + `WBE2014` (new code — "ERP transaction history row not found").
- File missing on storage root → 404 + `WBE2015` (new code — "ERP transaction history file is unavailable").

---

## 6. Sort whitelist (`ErpTrnsHistorySortField`)

Per [wecms-api/CLAUDE.md §"Common table sort"](../../../CLAUDE.md) — one enum, lenient `parse()` falls back to default sort on unknown / blank input.

| Wire FIELD_ID (uppercase) | Entity property | Notes |
|---|---|---|
| `REG_DTM` | `regDtm` | Default sort key (DESC) — OQ-7 resolved 2026-05-11. |
| `ERP_TYPE` | `erpType` | Direction code. |
| `TRNS_TYPE_CD` | `trnsTypeCd` | Transaction-type code (sorts by code, not label — same as JEX). |
| `REG_USER` | `regId` | Sort by the login id (frontend's User column). |
| `FILE` | — | **Not whitelisted.** OQ-5 — the JEX header shows a sort glyph but no IDO column maps to it. Refactor seeds `SORT_YN='N'` for the `FILE` row in `SYS_SCR_OPTION_WECMS`; the popover hides the glyph. |

Default `ORDER BY`: `regDtm DESC, seqNo DESC` (OQ-7 resolved 2026-05-11 — deterministic tiebreaker; both columns are part of the natural ledger ordering).

---

## 7. View Setting registry — NOT ADOPTED

**OQ-FE-2 resolved 2026-05-11.** The legacy JEX panel has no toolbar
list-icon button ([comm_0219_001_view.jsp:91-98](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0219_001_view.jsp))
and the refactor mirrors that contract. View Setting is **not adopted**
for this screen — there is **no** `SCREEN_IDS.ERP_TRANSACTION_HISTORY`
registry entry, **no** `SYS_SCR_OPTION_WECMS` seed, **no** popover.

Column visibility / order / size are hardcoded in the panel's static
`<colgroup>` per [frontend doc §13](../../../../wecms-web/docs/features/settings/transaction-history.md).
Table sort still ships via the common-sort contract — `sortable` is
declared per `<TableHeaderCell>` directly instead of being derived from
`SORT_YN`.

Reference column shape (for the static `<colgroup>` and table header):

| FIELD_ID | DISP_ORD (hardcoded) | Width | Sortable | Notes |
|---|---|---|---|---|
| `REG_DTM` | 1 | 146 px | yes | Date column |
| `ERP_TYPE` | 2 | auto | yes | Type (W / E / D) |
| `TRNS_TYPE_CD` | 3 | 280 px | yes | Transaction Type |
| `FILE` | 4 | 280 px | no | File column (OQ-5 — no IDO column to sort on) |
| `REG_USER` | 5 | 125 px | yes | User column |

If a future product requirement adds the toolbar list-icon button, View
Setting adoption is a single MR: add `SCREEN_IDS.ERP_TRANSACTION_HISTORY`,
seed `SYS_SCR_OPTION_WECMS` with the rows above, mount
`<ViewSettingPopover>`, and flip `<TableHeaderCell sortable>` to read from
`SORT_YN`. No backend code change required.

---

## 8. Locale keys to add

```json
{
  "settings.erp_transaction_history.title": {
    "en": "ERP Transaction History",
    "ko": "ERP 거래 내역",
    "vi": "Lịch sử giao dịch ERP"
  },
  "settings.erp_transaction_history.description": {
    "en": "Browse the ledger of files exchanged between WeCMS and your ERP.",
    "ko": "WeCMS와 ERP 간 교환된 파일 내역을 확인합니다.",
    "vi": "Xem nhật ký các tệp trao đổi giữa WeCMS và ERP."
  },

  "settings.erp_transaction_history.tabs.all": {"en": "All", "ko": "전체", "vi": "Tất cả"},
  "settings.erp_transaction_history.tabs.wecms_to_erp": {"en": "WeCMS → ERP", "ko": "WeCMS → ERP", "vi": "WeCMS → ERP"},
  "settings.erp_transaction_history.tabs.erp_to_wecms": {"en": "ERP → WeCMS", "ko": "ERP → WeCMS", "vi": "ERP → WeCMS"},
  "settings.erp_transaction_history.tabs.deleted": {"en": "Deleted", "ko": "삭제됨", "vi": "Đã xoá"},

  "settings.erp_transaction_history.direction.wecms_to_erp": {"en": "WeCMS → ERP", "ko": "WeCMS → ERP", "vi": "WeCMS → ERP"},
  "settings.erp_transaction_history.direction.erp_to_wecms": {"en": "ERP → WeCMS", "ko": "ERP → WeCMS", "vi": "ERP → WeCMS"},
  "settings.erp_transaction_history.direction.deleted": {"en": "Deleted", "ko": "삭제됨", "vi": "Đã xoá"},

  "settings.erp_transaction_history.type.01": {"en": "Transaction History (VND)", "ko": "거래 내역 (VND)", "vi": "Lịch sử giao dịch (VND)"},
  "settings.erp_transaction_history.type.02": {"en": "Vendor Scheduled Payment", "ko": "Vendor 예약 지급", "vi": "Thanh toán nhà cung cấp theo lịch"},
  /* ...17 entries, full list in the locale files... */

  "settings.erp_transaction_history.col.date": {"en": "Date", "ko": "일시", "vi": "Ngày"},
  "settings.erp_transaction_history.col.type": {"en": "Type", "ko": "유형", "vi": "Loại"},
  "settings.erp_transaction_history.col.transaction_type": {"en": "Transaction Type", "ko": "거래 유형", "vi": "Loại giao dịch"},
  "settings.erp_transaction_history.col.file": {"en": "File", "ko": "파일", "vi": "Tệp"},
  "settings.erp_transaction_history.col.user": {"en": "User", "ko": "사용자", "vi": "Người dùng"},

  "settings.erp_transaction_history.filter.transaction_type_all": {"en": "All Transaction Type", "ko": "전체 거래 유형", "vi": "Tất cả loại giao dịch"},
  "settings.erp_transaction_history.filter.search_placeholder": {"en": "Search", "ko": "검색", "vi": "Tìm kiếm"},

  "settings.erp_transaction_history.empty": {"en": "No Data Found", "ko": "데이터가 없습니다", "vi": "Không có dữ liệu"}
}
```

Verified translations come from `dictionary_{ko,vi}.properties` / `ErpType.getLabel()` / `ErpTypeCd.getLabel()` where available. The KO/VI entries above are placeholders pending verification by Mr. Jung — leave them out of the locale file and rely on the EN fallback if no verified translation exists (per [wecms-api/CLAUDE.md §"Domain enums"](../../../CLAUDE.md)).

---

## 9. Error code map

| Legacy error | New code | Message (EN) |
|---|---|---|
| `WBE0001` (required field missing) | `WBE_VALIDATION` | (default Bean Validation message) |
| `WSE0408` (invalid date format) | `WBE_VALIDATION` | (Bean Validation pattern message) |
| `WSE0402` (invalid period) | `WBE_VALIDATION` | "Date range is invalid: from date must be on or before to date." (custom `messages.properties`) |
| `WSE0409` (multiple/none found in `validateErpTrnsHstr`) | **`WBE2014`** (new) | "ERP transaction history row not found." |
| File missing on NAS | **`WBE2015`** (new) | "ERP transaction history file is unavailable." |
| Generic server error | `WBE9999` | (catch-all) |

Add `WBE2014` + `WBE2015` to `messages.properties` (EN + KO + VI as translations land) before the service throws.

---

## 10. AUDIT_LOGS_INFM WSVC_ID values

Per [wecms-api/CLAUDE.md §"Refactor identifier naming"](../../../CLAUDE.md) — refactor writes the slug, not the legacy WSVC id.

| Endpoint | `WSVC_ID` written |
|---|---|
| `GET /api/v1/settings/erp-transaction-history` | `erp-transaction-history` |
| `GET .../{seqNo}/download` | `erp-transaction-history/download` |
| `GET .../{seqNo}/thumbnail` | `erp-transaction-history/thumbnail` |

JEX writes `comm_0219_001_r001` / `comm_0219_001_r002` / `comm_0219_001_c001`
on the live system — those values appear in the existing PROD audit rows
verbatim. The refactor does not back-fill historical rows (it never writes
to the JEX DB); a future cross-system audit report would have to UNION the
two id formats. No action required for Phase 2.

---

## 11. Out-of-scope mapping (documented for traceability)

| JEX artifact | Why deferred | Sprint that owns it |
|---|---|---|
| `comm_0219_001_c001` upload action | Per OQ-1 — generic; replaced by per-feature audit-writer services. | Each Payment / Transfer / E-Invoice feature in Sprint 2+. |
| `ERP_0100_001..004` batch writers + `ErpDataRefreshHelper` | No scheduled batch infrastructure in refactor R&D yet. | Sprint 3+ when batch scheduling lands. |
| `wb_get_user_nm(REG_ID)` legacy function | Refactor reads `USER_INFM` directly (OQ-4). | This feature (Phase 2). |
| Soft-delete write (`ERP_TYPE='D'`) | Driven from each Payment feature's Cancel flow. | Per-Payment-feature spec. |

---

## 12. Changelog

- 2026-05-11 — v0.1 — initial mapping (Sokny, AI-assisted).
