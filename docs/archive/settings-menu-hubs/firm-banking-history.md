# Firm Banking History

> Canonical Settings-menu entry for **Settings → Firm Banking → Firm Banking History**. Detailed
> design lives in the linked feature spec below.

## Overview
Firm Banking History is a read-only, tenant-scoped log of firm-banking gateway transactions
(transfers, scraping module runs) with date/service/result filtering, keyword search, sorting, and
pagination. Slug: `firm-banking-history`. **Backend + frontend wired 2026-05-08**. Mirrors live JEX
`comm_0221_001` (View) + `comm_0221_001_r001` (Read API).

- Feature spec (full implementation references): [../features/settings/firm-banking-history.md](../../features/settings/firm-banking-history.md)
- DB schema: [../database/settings/firm-banking-history-schema.md](../../references/database/settings/firm-banking-history-schema.md)
- JEX → Spring mapping: [../migration/firm-banking-history-mapping.md](../../references/migration/firm-banking-history-mapping.md)
- Parity audit: [../audits/firm-banking-history-parity-checklist.md](../../references/audits/firm-banking-history-parity-checklist.md) ·
  [../audits/firm-banking-history-review-2026-05-08.md](../../references/audits/firm-banking-history-review-2026-05-08.md)

## Related APIs
- `GET /api/v1/settings/firm-banking-history` (JWT, snake_case `@RequestParam` names). A single JPQL
  `search()` covers every filter combination. Response: `{ list, total_count, total_pages,
  page_number, page_size }`.

## Database Tables
- `TB_FIRM_TRAN` — firm-banking transactions (source, Flyway V59)
- `NXSIAS_MODULE_TRAN_LOG_HIS` — scraping module transaction log (source, V59)
- `TB_FIRM_BANK_CODE` — bank-code lookup (V59)
- `V_FIRM_BANKING_HISTORY` — `@Immutable` read view (V60) joining the above
- DB functions `kr_to_vn_time`, `wb_get_napas_bank_nm` (1-arg) (V60)

## Business Rules
- Read-only; tenant-scoped.
- Sort whitelist (`FirmBankingHistorySortField`): `TARN_DTM`, `SVC_TYPE`, `SVC_NM`, `SEQ_NO`,
  `DEP_BANK_CD`, `RSLT_CD`.
- Date validation, `svc_type` guard, keyword escaping, pagination clamp, tenant scoping.

## Validation
- Required date params validated; missing → `WBE_VALIDATION` envelope.
- `svc_type` constrained to known values.

## Error Cases
- Missing/invalid required params → `WBE_VALIDATION`.
- Standard envelope; `WBE9999`.

## Notes
Frontend ships as a Settings modal panel (Figma 9198:11718), not a standalone route. Web/UI:
[../../../wecms-web/docs/settings/firm-banking-history.md](../../../../wecms-web/docs/features/settings/firm-banking-history.md).
