# Firm Banking History — Functional Specification (Backend)

What the screen does and the rules the backend enforces. Parent: [README.md](README.md).
Legacy functional spec (read-only): [docs/jex/.../functional-specification.md](../../../../../docs/jex/features/firm-banking-history/functional-specification.md).
UI rendering detail lives in the frontend [ui-specification.md](../../../../../wecms-web/docs/features/settings/firm-banking-history/ui-specification.md).

---

## 1. Search conditions

| Condition | Wire param | Required | Backend rule |
|---|---|---|---|
| Date range — from | `from_date` | ✅ | `YYYYMMDD`, length 8, parseable; filters `SUBSTRING(tarn_dtm,1,8) >= from_date` |
| Date range — to | `to_date` | ✅ | `YYYYMMDD`, length 8, parseable; `from ≤ to`; filters `… <= to_date` (inclusive BETWEEN) |
| Service type | `svc_type` | — | One of `F` / `S` / `G` / `B`; blank → no filter; unknown → `WBE_VALIDATION` (case-insensitive input is upper-cased) |
| Keyword | `keyword` | — | Case-insensitive partial match on `seq_no` (`LOWER(seq_no) LIKE %kw% ESCAPE '!'`); LIKE meta-chars escaped; max length 100 (legacy `WSVC` declares `SRCH_WD` length 100) |
| Sort field | `sort_field` | — | Whitelisted FIELD_ID; unknown → default sort (no error) |
| Sort direction | `sort_direction` | — | `ASC` / `DESC`; unknown → default sort |
| Page | `page` | — | Zero-based; `< 0` clamped to `0` |
| Page size | `size` | — | Default `15`; clamped to max `100` |

There are **no** account, bank-code, currency, or status filters on this screen — the legacy screen
has none either. Service type is the only categorical filter; date range is the only mandatory one.

### Filter mapping (frontend → wire)

The four service-type options are server-keyed; the frontend sends the raw discriminator letter:

| UI option (i18n key suffix) | Wire `svc_type` |
|---|---|
| `firm_banking_transaction` | `F` |
| `scraping_transfer` | `S` |
| `vietnam_gdt_inquiry` | `G` |
| `vietnam_business_inquiry` | `B` |
| `all` | (param omitted) |

For product type `H`, the frontend hides `G` and `B` options (legacy `comm_0221_001_view.jsp` `prdtTypes === "H"` gate).

---

## 2. Grid columns

The list table renders six data columns plus a View Details action column. Sortable columns map
1:1 to the [`FirmBankingHistorySortField`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistorySortField.java) whitelist.

| # | Column | FIELD_ID | Sortable | Source field | Rendering |
|---|---|---|---|---|---|
| 1 | Date / Time | `TARN_DTM` | ✅ | `tarn_dtm` | Split into date chip + time; rendered in Asia/Ho_Chi_Minh TZ |
| 2 | Service Type | `SVC_TYPE` | ✅ | `svc_type` (+ `svc_type_key`) | `t(svc_type_key)`; falls back to raw letter |
| 3 | Service Name | `SVC_NM` | ✅ | `svc_nm` (+ `svc_name_key`) | `t(svc_name_key)`; falls back to raw `svc_nm` |
| 4 | Seq. No. | `SEQ_NO` | ✅ | `seq_no` | verbatim (keyword-search target) |
| 5 | Deposit Bank / Inst. Code | `DEP_BANK_CD` | ✅ | `dep_bank_cd` | verbatim (e.g. "Vietcombank - 970436") |
| 6 | Results | `RSLT_CD` | ✅ | `rslt_cd` | status pill (see § 4) |
| 7 | (action) | — | — | — | "View Details" link → popover |

Columns that ride in the row but are **not** sortable and **not** shown as their own grid column:
`rslt_msg`, `job`, `req_data`, `van_org_cd` — these feed the detail popover.

> The Excel export uses a parallel FIELD_ID set seeded for View Setting (V165): `REG_DTM`,
> `SVC_TYPE`, `SVC_NM`, `SEQ_NO`, `DEP_BANK_CD`, `RS_CD`, `RS_MSG`. `REG_DTM`/`RS_CD`/`RS_MSG` are the
> seed's names for the grid's `TARN_DTM`/`RSLT_CD`/`RSLT_MSG`. See [interface-specification.md § Export](interface-specification.md#4-export-contract).

---

## 3. Sort, pagination & defaults

- **Default sort:** `TARN_DTM DESC, SEQ_NO DESC` (most recent first; `SEQ_NO` is a deterministic tiebreaker so paging is stable).
- **Sort whitelist:** `TARN_DTM`, `SVC_TYPE`, `SVC_NM`, `SEQ_NO`, `DEP_BANK_CD`, `RSLT_CD`. The wire FIELD_ID is matched against the enum via a lenient `parse(...)` (case-insensitive; unknown → `null` → default sort). The raw wire string never reaches SQL.
- **Pagination:** server-side. The wire `page` is zero-based (Spring convention); the UI displays one-based. Backend default page size is `15`; the frontend sends `10` by default (its canonical page size). `size` is clamped to `100`.
- **Response paging metadata:** `total_count` (unpaged filtered total), `page_number`, `page_size`, `total_pages`.

---

## 4. Transaction status mapping

`RSLT_CD` is a per-leg result code; success codes differ by leg. The frontend derives a 3-state UI
result and colour from the raw `rslt_cd`, and resolves a localized **message** for the detail
popover separately.

### Grid status pill (derived from `rslt_cd`)

| UI result | Source `rslt_cd` | Colour token |
|---|---|---|
| `success` | `00000000` or `0000` | green (`text-wb-status-approved-text`) |
| `failed` | everything else (incl. null/blank) | red (`text-wb-status-rejected-text`) |
| `in_progress` | (never produced from the wire today; retained in the union for styling) | amber (`text-wb-status-progress-text`) |

> **Parity note (GAP-2, fixed 2026-06-05):** a blank/null `rslt_cd` now maps to `failed` (was
> previously ambiguous). The 3-state union keeps `in_progress` for forward-compatibility even though
> the current view never emits a pending code.

### Detail-popover result *message* resolution

The message shown in the popover is leg-specific (these are **localization** concerns, not error
handling — the HTTP call is always 200 regardless of `rslt_cd`):

- **F leg:** `resolveVanMessage(rslt_cd, rslt_msg, locale, failedLabel)` — looks up the 4-char VAN code in the ported `DICTIONARY_VAN` table (69 entries). Fallback chain: dictionary hit → `rslt_msg` → localized "Failed" → "".
- **S / G / B legs:** `resolveScrapingResultMessage(rslt_cd, rslt_msg, t, failedLabel)` — looks up the 8-char scraping code in the 18-code registry. Fallback chain: i18n message → `rslt_msg` → raw `rslt_cd` → localized "Failed" → "".

Full detail of both bundles (verified EN strings, KO/VI status, reuse contract) is in the
[detailed implementation spec](../firm-banking-history.md) (§ DICTIONARY_VAN port, § Result-code i18n bundle).

---

## 5. Service-name & service-type resolution (server-owned)

The backend owns the `(svc_type, svc_nm) → display` mapping and emits two i18n keys on the wire so
the frontend holds **zero** hardcoded service-code → label maps.

- **`svc_type_key`** — from [`FirmBankingSvcType`](../../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java):
  `F → …service_type.firm_banking_transaction`, `S → …scraping_transfer`, `G → …vietnam_gdt_inquiry`, `B → …vietnam_business_inquiry`. Null only for an unknown letter.
- **`svc_name_key`** — from [`FirmBankingServiceNameResolver`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingServiceNameResolver.java):
  - F leg → `TranTp.fromCode(svc_nm)` → `fb_history.service.{201|202|203}` (code `204` has no verified EN label → `null`).
  - S leg → switch on the V60 literals → `fb_history.service.{funds_transfer_scraping|scraping_balance|scraping_transaction|scraping_card_transaction}`.
  - G leg → `fb_history.service.{sale_einvoice_inquiry|expense_einvoice_inquiry}`.
  - B leg → `fb_history.service.vietnam_business_inquiry`.
  - Unknown pair → `null` (frontend falls back to raw `svc_nm`).

See [database-mapping.md](database-mapping.md) for the full code-set table and
[migration-guide.md](migration-guide.md) for how this replaced the legacy frontend `SERVICE_NAME_MAP`.

---

## 6. Loading / empty / error states

| State | Backend behaviour | Frontend behaviour |
|---|---|---|
| Initial load | Panel fetches with the default last-30-days range | spinner / skeleton until first response |
| Search loading | new request per filter/sort/page change | latched stable data prevents grid flicker between refetches |
| Empty result | returns `list: []`, `total_count: 0` | renders the localized empty-state row |
| Validation error | `WBE_VALIDATION` (HTTP 200, business envelope) | suppressed from the global error dialog — the panel guarantees its own input shape |
| Other error | `WBE_DB` (500) / `WBE9999` (500) with `trace_id` | global system error dialog renders code + `trace_id` |

The screen is **read-only**: no create/update/delete, no per-leg business gate, no write to any
source table.
