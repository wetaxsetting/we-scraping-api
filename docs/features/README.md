# features/

**Status:** active · **Last reviewed:** 2026-08-17

**Feature-first** documentation for the Spring Boot + Next.js refactor, organized to mirror the live
WeCMS **menu structure**. This is the primary entry point for developers: start from the menu group
your screen belongs to, then drill into the per-screen doc.

## Purpose

One doc per screen describing the refactor scope, JEX traceability, REST endpoints, page route, DTO
shape, and DB tables touched. Each doc is the spine that links the supporting reference material
(API contract, DB schema, JEX→Spring mapping, parity audit) without duplicating it.

## Menu groups

| Group | Folder | README | WeCMS menu |
|---|---|---|---|
| Auth *(system)* | [auth/](auth/) | [auth/README.md](auth/README.md) | Cross-cutting — login / logout / me (not a top-menu group) |
| Common *(system)* | [common/](common/) | [common/README.md](common/README.md) | Cross-cutting — View Setting (Screen Options) and other shared features (not a top-menu group) |
| Banking | [banking/](banking/) | [banking/README.md](banking/README.md) · [flow-process HTML](banking/banking-flow-process.html) | Banking → Current & Savings (VND/FCY), Own Account Transfer, Reports |
| Payment | [payment/](payment/) | [payment/README.md](payment/README.md) | Payment → Approval Box, Internal/Vendor/Expense/Payroll/Overseas, Exchange Rate, Card |
| Add-on Service | [addon-service/](addon-service/) | [addon-service/README.md](addon-service/README.md) | Add-on Service → Sales/Expenses e-Invoices, Operation Status |
| Settings | [settings/](settings/) | [settings/README.md](settings/README.md) | Settings menu (one doc per Settings item) |
| WeCMS Batch *(infrastructure)* | [wecms-batch/](wecms-batch/) | [wecms-batch/README.md](wecms-batch/README.md) · [flow-process HTML](wecms-batch/wecms-batch-flow-process.html) | Firm-banking dispatch engine (no menu — backend batch/dispatch path; payment-domain capability kept at features root by request) |

## How a feature doc maps to the reference layers

```
features/<group>/<screen>.md        ← functional spec (the spine; read this first)
   ├─ references/api-spec/...        ← REST endpoint / DTO contract
   ├─ references/database/...        ← table / column schema
   ├─ references/migration/...       ← field-by-field JEX → Spring mapping
   ├─ references/audits/...          ← parity audit (refactor vs JEX)
   └─ wecms-web/docs/features/...    ← Next.js page / hook / API-client design
```

## What does NOT go here

- REST endpoint contracts → [../references/api-spec/](../references/api-spec/)
- Service / domain design → [../references/business-rules/](../references/business-rules/)
- Field-by-field JEX → Spring mapping → [../references/migration/](../references/migration/)
- Schema / migration intent → [../references/database/](../references/database/)
- Frontend page design → [wecms-web/docs/features/](../../../wecms-web/docs/features/)
- JEX feature specs — read-only reference at [docs/jex/features/](../../../docs/jex/features/)

## Naming traceability

The kebab-case slug **must match** the JEX counterpart: `docs/jex/features/<slug>.md` ↔
`wecms-api/docs/features/<group>/<slug>.md`. Same name on both sides — never rename in the refactor doc.
