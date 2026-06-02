# features/banking/

Feature specs (menu-level hubs) for the **Banking** domain in the Spring Boot + Next.js refactor.
Each hub cross-links to its API contract (`../../api-spec/banking/`), DB schema
(`../../database/banking/`), and the frontend doc (`wecms-web/docs/features/banking/`).

## Banking menu → docs

**Current & Savings (VND)**
| Menu | Feature doc |
|---|---|
| Account Inquiry (VND) | [account-inquiry-vnd.md](current-savings-vnd/account-inquiry.md) |
| Transaction History (VND) | [transaction-history-vnd.md](current-savings-vnd/transaction-history.md) |
| Smart Organizer (VND) ⚠️ discovery | [smart-organizer-vnd.md](current-savings-vnd/smart-organizer.md) |

**Current & Savings (FCY)**
| Menu | Feature doc |
|---|---|
| Account Inquiry (FCY) | [account-inquiry-fcy.md](current-savings-fcy/account-inquiry.md) |
| Transaction History (FCY) | [transaction-history-fcy.md](current-savings-fcy/transaction-history.md) |
| Smart Organizer (FCY) ⚠️ discovery | [smart-organizer-fcy.md](current-savings-fcy/smart-organizer.md) |

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
