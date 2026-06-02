# database/query-analysis/

Query performance & plan analysis notes for the refactor database.

## Purpose

Capture `EXPLAIN` plans (without `ANALYZE` per [database access rules](../../../../../.claude/rules/database.md)), hot-query patterns, and index-fit verification for each feature's main read / write queries.

## What goes here

- Per-query markdown documenting plan, expected cardinality, and chosen index
- Cross-link to the supporting index in [`../indexing/`](../indexing/)
- Flag any query that requires DEV-DB data inspection (must follow `.claude/rules/database.md` — read-only, LIMIT-bound)

## What does NOT go here

- Production tuning / live-system trace — that belongs in `docs/jex/` (read-only) and never in the refactor docs.
