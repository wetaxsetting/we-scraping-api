# Settings > Firm Banking History — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/firm-banking-history/`

## Feature Overview
- **Purpose:** A unified, paginated, **read-only audit history** of every outbound integration call WeCMS made on behalf of the current tenant for firm-banking-related services. Five distinct legacy source legs (Firm Banking `F`, Funds Transfer / Scraping `S`, Vietnam GDT e-Invoice `G`, Vietnam Business Inquiry `B`) are stitched into one row shape with a date-range filter, service-type filter, sequence-number keyword search, sortable columns, server-side pagination, a per-row View Details popover, and an XLSX export.
- **Business role:** A diagnostic / audit tool for tenant users — it never initiates a transaction and never writes to any source table.

## Menu / Route Mapping
- **Menu / panel:** Settings → Firm Banking → Firm Banking History
- **Menu permission ID (slug):** `settings-firm-banking-history` — full gate `@PreAuthorize("hasAnyRole('ADMIN','USER') and @menuAuthz.canAccess('settings-firm-banking-history')")`
- **Wire base path:** `/api/v1/settings/firm-banking-history` (list) + `POST .../export`
- **Frontend mount / route:** Settings modal panel (`FirmBankingHistoryPanel`)
- **Legacy JEX counterpart:** WSVC `comm_0221_001` (view) + `comm_0221_001_r001` (read API), IDO `TB_FIRM_TRAN_R002` — legacy ids cited for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack, legacy parity verified (2026-06-05). `GET /api/v1/settings/firm-banking-history` (list with date-range/service-type/keyword filters, sort, paging) + `POST .../export` (XLSX). Data shape is one `@Immutable` JPA entity backed by a Hibernate `@Subselect` (the five-leg `UNION ALL` runs over the legacy base tables `TB_FIRM_TRAN`, `NAPAS_REMIT_TRNS_HSTR`, `EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS` — there is **no** `V_FIRM_BANKING_HISTORY` DB view). Read-only — no write surface, no audit-log write on read. Frontend panel, filters, table, and detail popover wired to the live endpoint (no mock fallback). Backed by service + controller + export-provider tests.
- 🟡 **Partially implemented:** The backend export endpoint + `FirmBankingHistoryExportProvider` exist and are tested, but the **frontend Excel export action is not wired**. There is **no View Setting seed** for this screen — `ScreenOptionService.load('firm-banking-history', …)` throws `WBE2001`, which the export pipeline catches and falls back to **request columns ∩ provider allow-list**. Frontend default page size is 10 vs backend default 15 (intentional; backend honours the sent `size`).
- ⚪ **Deprecated / Legacy / Not ported:** No write/inquire/print flows (legacy has none for this screen). No read-side maintenance-log write (`mntLogYn=Y`) — legacy logs the inquiry, refactor does not (low impact, read-only screen).

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [firm-banking-history/README.md](firm-banking-history/README.md) |
| Business flow | [firm-banking-history/business-flow.md](firm-banking-history/business-flow.md) |
| Database mapping | [firm-banking-history/database-mapping.md](firm-banking-history/database-mapping.md) |
| Functional specification | [firm-banking-history/functional-specification.md](firm-banking-history/functional-specification.md) |
| Interface specification | [firm-banking-history/interface-specification.md](firm-banking-history/interface-specification.md) |
| Migration guide | [firm-banking-history/migration-guide.md](firm-banking-history/migration-guide.md) |
| Refactor gap analysis | [firm-banking-history/refactor-gap-analysis.md](firm-banking-history/refactor-gap-analysis.md) |
| Source code index | [firm-banking-history/source-code-index.md](firm-banking-history/source-code-index.md) |
| Test scenarios | [firm-banking-history/test-scenarios.md](firm-banking-history/test-scenarios.md) |

## Historical Design Notes (preserved)
Omitted — all unique rationale (View Setting deliberately NOT applicable to this screen, the five-leg union design, the DICTIONARY_VAN / scraping result-code i18n bundle ports, the server-owned service-name resolver, and the resolved Phase-2/3 open questions) is already preserved in the folder docs. The old flat narrative additionally carried stale facts now scrubbed: references to a "view (Flyway V60)" / `V_FIRM_BANKING_HISTORY` DB view (the union is a `@Subselect`, no view object ships), and a V153 View Setting seed (this screen has no View Setting seed — export uses the allow-list ∩ request fallback).
