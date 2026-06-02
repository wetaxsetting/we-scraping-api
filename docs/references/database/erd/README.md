# database/erd/

Entity-Relationship Diagrams for the wecms-api refactor database.

**Generated:** 2026-06-01 · audited from Flyway migrations `V1`–`V237` (`src/main/resources/db/migration`), JPA entities under `domain/**/entity`, repositories, and the schema docs under [`../`](../). Re-run the audit when new feature migrations land.

## Purpose

Diagrammatic + textual ERDs covering the live refactor schema (**80 tables**). Captures table groupings (Banking, Payment, Add-on Service, Settings, Shared/Common), foreign-key topology (declared vs. logical), tenant-isolation columns (`BIZ_KEY`), key columns, code/enum columns, audit columns, and soft-delete/use flags.

## Contents

| Doc | Mermaid source | Covers |
|---|---|---|
| [full-project-erd.md](full-project-erd.md) | [diagrams/full-project-erd.mmd](diagrams/full-project-erd.mmd) | Cross-module map, Shared/Common hub, declared-FK inventory, Needs-Confirmation + gaps |
| [banking-erd.md](banking-erd.md) | [diagrams/banking-erd.mmd](diagrams/banking-erd.mmd) | `domain/banking` — accounts, transactions, FX, smart organizer, matching |
| [payment-erd.md](payment-erd.md) | [diagrams/payment-erd.mmd](diagrams/payment-erd.mmd) | `domain/payment` — scheduled/overseas payment, approval box, dispatch, validation |
| [addon-service-erd.md](addon-service-erd.md) | [diagrams/addon-service-erd.mmd](diagrams/addon-service-erd.mmd) | `domain/einvoice`, `domain/operation`, card transactions |
| [settings-erd.md](settings-erd.md) | [diagrams/settings-erd.mmd](diagrams/settings-erd.mmd) | `domain/setting` — bank-account, COA, contacts, approval line, firm-banking, ERP, VAS, purpose, env, credentials |

## Conventions

- **Tenancy:** `BUSI_INFM` is the hub; nearly every table carries `BIZ_KEY`.
- **FK vs Logical:** the legacy JEX schema declares few foreign keys. A relationship is **FK declared** only when a `FOREIGN KEY` exists in SQL; otherwise it is **Logical — no FK found** (enforced in the service layer). The full declared-FK list is in [full-project-erd.md](full-project-erd.md).
- **Mermaid `.mmd` files** hold attribute-level entity blocks (key columns only: PK, FK, code/enum, audit, use-flags) plus the relationship edges. The `.md` files add purpose, key columns, and related-doc links.
- The DB schema is `UPPER_SNAKE` (legacy parity); JSON wire is lowercase snake_case.
- **Removed (V31):** `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `BAT_CALL_HIS`, `ACCT_TRNS_RCV_PAY_EVID_FILE` — dropped as not-in-PROD-active-list; excluded from all ERDs.

## Related

- Flyway migrations: [`../flyway/`](../flyway/)
- Per-feature schema design: [`../`](../) (one file per feature at the database root + `banking/`, `payment/`, `settings/`, `addon/`)
- Indexing notes: [`../indexing/`](../indexing/) · Table mapping: [`../table-mapping/`](../table-mapping/)
- JEX ERD reference: [`../../../jex/architecture/`](../../../../../docs/jex/architecture/)
