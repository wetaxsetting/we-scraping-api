# features/banking/

Feature specs (menu-level hubs) for the **Banking** domain in the Spring Boot + Next.js refactor.
Each hub cross-links to its API contract (`../../api-spec/banking/`), DB schema
(`../../database/banking/`), and the frontend doc (`wecms-web/docs/features/banking/`).

> **Cross-cutting:** Banking menu/page authorization + product-type (F/M/H/W) behavior is documented
> once in **[menu-authorization-and-product-types.md](menu-authorization-and-product-types.md)**
> (verified 2026-06-07 against the V243 nav seed). Frontend twin:
> [wecms-web/docs/features/banking/menu-sidebar-route-guard-behavior.md](../../../../wecms-web/docs/features/banking/menu-sidebar-route-guard-behavior.md).

## Banking menu → docs

**Current & Savings (VND)**
| Menu | Feature doc |
|---|---|
| Account Inquiry (VND) ✅ refactor complete | **[account-inquiry-vnd/](account-inquiry-vnd/README.md)** — primary home (9-doc folder). Preserved single-file spec: [current-savings-vnd/account-inquiry.md](current-savings-vnd/account-inquiry.md). |
| Transaction History (VND) ✅ refactor complete | **[transaction-history-vnd/](transaction-history-vnd/README.md)** — primary home (9-doc folder). Preserved single-file spec: [current-savings-vnd/transaction-history.md](current-savings-vnd/transaction-history.md). |
| Smart Organizer ✅ refactor complete | **[smart-organizer/](smart-organizer/README.md)** — primary home (9-doc folder; one screen serves **VND + FCY**). Superseded single-file specs: [current-savings-vnd/smart-organizer.md](current-savings-vnd/smart-organizer.md), [current-savings-fcy/smart-organizer.md](current-savings-fcy/smart-organizer.md). Parity audit: [../../../../docs/jex/audits/smart-organizer-parity-audit.md](../../../../docs/jex/audits/smart-organizer-parity-audit.md); R11 verification: [../../audits/smart-organizer-r11-verification.md](../../audits/smart-organizer-r11-verification.md). |

**Current & Savings (FCY)**
| Menu | Feature doc |
|---|---|
| Account Inquiry (FCY) ✅ refactor complete | **[account-inquiry-fcy/](account-inquiry-fcy/README.md)** — primary home (9-doc folder). Preserved single-file spec: [current-savings-fcy/account-inquiry.md](current-savings-fcy/account-inquiry.md). Parity audit: [../../audits/account-inquiry-fcy-parity-audit.md](../../audits/account-inquiry-fcy-parity-audit.md). Legacy investigation (`fnsh_0005_001`): [../../../../docs/features/account-inquiry-fcy/](../../../../docs/features/account-inquiry-fcy/README.md). |
| Transaction History (FCY) ✅ refactor complete | **[transaction-history-fcy/](transaction-history-fcy/README.md)** — primary home (9-doc folder). Preserved single-file spec: [current-savings-fcy/transaction-history.md](current-savings-fcy/transaction-history.md). Parity audit: [../../audits/transaction-history-fcy-parity-audit.md](../../audits/transaction-history-fcy-parity-audit.md). Legacy spec (`fnsh_0006_001`): [../../../../docs/jex/features/transaction-history-fcy/](../../../../docs/jex/features/transaction-history-fcy/README.md). |

> **Smart Organizer is a single screen serving both VND and FCY** — see the **[smart-organizer/](smart-organizer/README.md)** hub listed under Current & Savings (VND) above; there is no separate FCY Smart Organizer screen.

**Internal Transfer**
| Menu | Feature doc |
|---|---|
| Own Account Transfer | [own-account-transfer.md](own-account-transfer/own-account-transfer.md) |
| Transfer History | [transfer-history.md](own-account-transfer/transfer-history.md) |

**Reports**
| Menu | Feature doc |
|---|---|
| Daily Cash | [daily-cash.md](reports/daily-cash.md) |
| Daily Financial | [daily-financial.md](reports/daily-financial.md) |
| Daily Financial (HAENGSUNG) ⚠️ stub | _backend stub + FE page — scope pending **OQ-DOC-8**_ |
| Cash Flow | [cash-flow.md](reports/cash-flow.md) |

**Bank Credentials**
| Menu | Feature doc |
|---|---|
| Bank Credentials & Scraping | [bank-credentials.md](bank-credentials.md) |

**Related:** [exchange-rate-inquiry.md](../payment/exchange-rate-inquiry.md) — moved to Payment → Add-on (FX rates; cross-links back to [account-inquiry-fcy.md](current-savings-fcy/account-inquiry.md)).

**Data model (ERD):** [Banking ERD](../../references/database/erd/banking-erd.md) ([Mermaid](../../references/database/erd/diagrams/banking-erd.mmd)) · cross-module map: [full-project ERD](../../references/database/erd/full-project-erd.md) · shared hub: [common ERD](../../references/database/erd/common-erd.md). Coverage: [database-coverage-matrix.md](../../references/database/database-coverage-matrix.md) · ownership: [table-ownership.md](../../references/database/table-ownership.md).

## Document standards
- One markdown file per refactor feature; kebab-case slug matching the JEX counterpart under [`docs/jex/`](../../../../docs/jex/).
- Cite JEX source-of-truth with `WECMS DEV/` `file:line` anchors.
- Cross-link companion docs: API contract → [`../../api-spec/banking/`](../../references/api-spec/banking/), schema → [`../../database/banking/`](../../references/database/banking/), JEX→Spring mapping → [`../../migration/`](../../references/migration/), plans → [`../../planning/`](../../planning/), frontend → [`wecms-web/docs/features/banking/`](../../../../wecms-web/docs/features/banking/).

## What does NOT go here
- API endpoint detail → [`../../api-spec/banking/`](../../references/api-spec/banking/)
- Page / component design → [`../../../../wecms-web/docs/features/banking/`](../../../../wecms-web/docs/features/banking/)
- Schema definitions → [`../../database/banking/`](../../references/database/banking/)
- JEX → Spring field mappings → [`../../migration/`](../../references/migration/)
