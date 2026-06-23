# features/banking/

Feature specs (menu-level hubs) for the **Banking** domain in the Spring Boot + Next.js refactor.
The folder tree mirrors the live WeCMS **Banking top menu**: each menu group is a folder, each submenu page is a leaf folder carrying the standard 9-doc set (README · business-flow · database-mapping · functional-specification · interface-specification · migration-guide · refactor-gap-analysis · source-code-index · test-scenarios). Each hub cross-links to its API contract (`../../references/api-spec/banking/`), DB schema (`../../references/database/banking/`), and the frontend doc (`wecms-web/docs/features/banking/`).

> **Cross-cutting:** Banking menu/page authorization + product-type (F/M/H) behavior is documented once in **[menu-authorization-and-product-types.md](menu-authorization-and-product-types.md)** (verified 2026-06-07 against the V243 nav seed). Frontend twin: [wecms-web/.../menu-sidebar-route-guard-behavior.md](../../../../wecms-web/docs/features/banking/menu-sidebar-route-guard-behavior.md).

## Banking top menu → docs

| # | Menu group | Submenu page | Leaf folder |
|---|---|---|---|
| 1 | **Current & Savings (VND)** | Account Inquiry | [current-savings-vnd/account-inquiry/](current-savings-vnd/account-inquiry/README.md) |
|   | | Transaction History | [current-savings-vnd/transaction-history/](current-savings-vnd/transaction-history/README.md) |
| 2 | **Current & Savings (FCY)** | Account Inquiry | [current-savings-fcy/account-inquiry/](current-savings-fcy/account-inquiry/README.md) |
|   | | Transaction History | [current-savings-fcy/transaction-history/](current-savings-fcy/transaction-history/README.md) |
| 3 | **Smart Organizer** | Smart Organizer | [smart-organizer/](smart-organizer/README.md) |
| 4 | **Internal Transfer (VND)** | Own Account Transfer | [internal-transfer-vnd/own-account-transfer/](internal-transfer-vnd/own-account-transfer/README.md) |
|   | | Transfer History | [internal-transfer-vnd/transfer-history/](internal-transfer-vnd/transfer-history/README.md) |
| 5 | **Reports** | Daily Cash | [reports/daily-cash/](reports/daily-cash/README.md) |
|   | | Daily Financial | [reports/daily-financial/](reports/daily-financial/README.md) |
|   | | Cash Flow | [reports/cash-flow/](reports/cash-flow/README.md) |

Group hubs: [current-savings-vnd/](current-savings-vnd/README.md) · [current-savings-fcy/](current-savings-fcy/README.md) · [internal-transfer-vnd/](internal-transfer-vnd/README.md) · [reports/](reports/README.md).

> **Smart Organizer is a single screen serving both VND and FCY** (currency is data-driven, not a separate screen) — it is its own top-menu group, not a Current & Savings child. The older per-currency single-file specs are preserved as [smart-organizer/legacy-spec-vnd.md](smart-organizer/legacy-spec-vnd.md) + [smart-organizer/legacy-spec-fcy.md](smart-organizer/legacy-spec-fcy.md).
>
> **Daily Financial (HAENGSUNG)** ⚠️ — a separate `H`-product report (`rept_0017_001`, route `/banking/reports/daily-financial-hs`) exists in code; its doc scope is pending **OQ-DOC-8** and it is folded into the [reports/daily-financial/](reports/daily-financial/README.md) package for now.

## Not part of the Banking top menu (root-level reference docs)

| Doc | Purpose |
|---|---|
| [bank-credentials.md](bank-credentials.md) | Bank Credentials & live scraping (settings-adjacent; not a Current&Savings/Reports submenu) — high-level summary |
| [banking-docs-audit.md](../../audits/banking-docs-audit.md) | 2026-06-23 documentation audit (code = source of truth): per-module before→after + structure |
| [banking-source-index.md](../../audits/banking-source-index.md) | Code ↔ docs ↔ tests index across all banking features |
| [banking-gap-analysis.md](../../audits/banking-gap-analysis.md) | Consolidated parity/gap matrix |
| [menu-authorization-and-product-types.md](menu-authorization-and-product-types.md) | Menu authz + product-type behavior |

**Related (other modules):** [Payment → Exchange Rate Inquiry](../payment/exchange-rate-inquiry/README.md) (FX rates consumed by the FCY views).
**Data model (ERD):** [Banking ERD](../../references/database/erd/banking-erd.md) · [full-project ERD](../../references/database/erd/full-project-erd.md) · [common ERD](../../references/database/erd/common-erd.md). Coverage: [database-coverage-matrix.md](../../references/database/database-coverage-matrix.md) · ownership: [table-ownership.md](../../references/database/table-ownership.md).

## Document standards
- Each submenu page is a leaf folder with the standard 9 files; legacy single-file specs are preserved in-folder as `legacy-spec.md`.
- Kebab-case slugs match the JEX counterpart under [`docs/jex/`](../../../../docs/jex/). Cite JEX source-of-truth with `WECMS DEV/` `file:line` anchors.
- API endpoint detail → [`../../references/api-spec/banking/`](../../references/api-spec/banking/); page/component design → [`wecms-web/docs/features/banking/`](../../../../wecms-web/docs/features/banking/); schema → [`../../references/database/banking/`](../../references/database/banking/); JEX→Spring mappings → [`../../references/migration/`](../../references/migration/).
