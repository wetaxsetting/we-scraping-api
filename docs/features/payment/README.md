# features/payment/

Feature hubs (menu-level) for the **Payment** domain in the Spring Boot + Next.js refactor.
Cross-sibling index: [_payment-module-overview.md](_payment-module-overview.md). Each hub documents its
menu's sub-items (Scheduled / Scheduled Details / Result / Result Details) as **sections within the
family hub** (per the approved 2026-06-01 decision — no per-sub-item split).

## Payment menu → docs

| Group | Menu | Feature hub |
|---|---|---|
| Approval Management | Approval Box | [approval-box/](approval-box/) (6-doc set) |
| Internal Transfer | Scheduled Transfer · Result (+ Details) | [internal-transfer.md](internal-transfer/README.md) ⭐ baseline |
| Vendor Payment | Scheduled · Result (+ Details) | [vendor-payment.md](vendor-payment/README.md) |
| Expense Payment | Scheduled · Result (+ Details) | [expense-payment.md](expense-payment/README.md) |
| Payroll | Scheduled · Result (+ Details) | [payroll.md](payroll/README.md) |
| Overseas Payment | Scheduled · Result (+ Details) | [overseas-payment.md](overseas-payment/README.md) |
| Add-on | Exchange Rate Inquiry | [exchange-rate-inquiry.md](exchange-rate-inquiry.md) (moved from banking 2026-06-01) |
| Corporate Card | Card Transactions | [card-transactions.md](card-transactions.md) |

## Companion layers (cross-linked, not duplicated)
- **Business rules:** [`../../business-rules/internal-transfer.md`](../../references/business-rules/internal-transfer.md), [`../../business-rules/card-transactions.md`](../../references/business-rules/card-transactions.md)
- **Database schema:** [`../../database/payment/`](../../references/database/payment/) (`internal-transfer-schema`, `firm-banking-dispatch-*`), [`../../database/card-transactions.md`](../../references/database/card-transactions.md)
- **Data model (ERD):** [Payment ERD](../../references/database/erd/payment-erd.md) ([Mermaid](../../references/database/erd/diagrams/payment-erd.mmd)); Card Transactions is in the [Add-on Service ERD](../../references/database/erd/addon-service-erd.md). Cross-module map: [full-project ERD](../../references/database/erd/full-project-erd.md). Coverage: [database-coverage-matrix.md](../../references/database/database-coverage-matrix.md) · ownership: [table-ownership.md](../../references/database/table-ownership.md).
- **Migration:** [`../../migration/internal-transfer-mapping.md`](../../references/migration/internal-transfer-mapping.md), [`../../migration/card-transactions.md`](../../references/migration/card-transactions.md)
- **Audits:** [`../../audits/internal-transfer-parity-checklist.md`](../../references/audits/internal-transfer-parity-checklist.md), [`../../audits/card-transactions-parity-checklist.md`](../../references/audits/card-transactions-parity-checklist.md)
- **Frontend:** [`wecms-web/docs/features/payment/`](../../../../wecms-web/docs/features/payment/)
- **JEX reference:** [`../../../../docs/jex/screen-flows/payments.md`](../../../../docs/jex/screen-flows/payments.md), [`../../../../docs/jex/business-rules/payment.md`](../../../../docs/jex/business-rules/payment.md)

## Standards
- One hub per payment family; sub-items are sections, not separate files. kebab-case slug matching the JEX counterpart.
- Shared infra (SCDUL tables, dispatch, approval) is indexed in [_payment-module-overview.md](_payment-module-overview.md) — mirror Internal Transfer's shape when expanding a sibling.
- Overseas Payment uses a **separate** table family (`OVRS_PYMN_*`), not the shared SCDUL flow.

## Open items
- Vendor / Expense / Payroll hubs are **stubs** (implemented in code; full-spec backfill pending).
- Overseas Payment hub is hub-level (created 2026-06-01); deepen to internal-transfer parity in a follow-up.
- No `api-spec/payment/` layer yet (contracts folded into hubs) — split if full 5-layer parity is wanted.
