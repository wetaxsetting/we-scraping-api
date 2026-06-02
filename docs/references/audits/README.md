# audits/

Parity and compliance audits for the refactor.

## Purpose

Snapshot reports comparing the refactor system against the JEX live system or against previously declared specs. Audits are dated, frozen-in-time evidence — not living documentation.

## What goes here

- DB parity audits (refactor PostgreSQL vs JEX DEV PostgreSQL): tables, columns, types, defaults, PK/UK/CHECK/FK, indexes, triggers, row counts
- API contract audits (Spring REST endpoints vs JEX WSVC contracts)
- Security / dependency audits
- End-of-sprint audit reports

## What does NOT go here

- Living migration plans — those belong in [../migration/](../migration/)
- Schema design or Flyway migration intent → [../database/](../database/)
- Bug reports — those go in [../../bug-reports/](../../../../docs/bug-reports/) with `System: Refactor`

## Example file names

- `db-parity-audit-YYYY-MM-DD.md`
- `api-contract-audit-YYYY-MM-DD.md`
- `sprint-1-final-parity-YYYY-MM-DD.md`
- `account-inquiry-vnd-parity.md`

## Reference

JEX DEV (`https://dev.wecms.vn/` + DEV DB) is the **read-only reference** for parity comparisons. Never modify JEX during an audit — only record observations.

## Audit cadence rule

Run a full local-vs-DEV parity audit at the **end of every sprint** before declaring the schema "done". See [../../../wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Audit rule".
