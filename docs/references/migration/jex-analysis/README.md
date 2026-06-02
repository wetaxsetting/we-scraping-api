# migration/jex-analysis/

Reverse-engineering notes on the live JEX system, written as input to a refactor design.

## Purpose

Capture the result of reading JEX source for a given service / feature: entity tree, DAO method inventory, IDO XML query summary, WSVC XML flow, `_act.jsp` orchestration, audit-table writes. This is **prose against the live system**, written once per feature before the refactor design starts.

## What goes here

- Per-feature analysis (`<feature>-jex-analysis.md`)
- `file:line` citations into `WECMS DEV/`
- Open questions to PO arising from JEX behaviour that is not documented elsewhere

## Naming convention

- kebab-case, slug matches the refactor feature
- Suffix `-jex-analysis.md`

## What does NOT go here

- Refactor design — that's in [`../../features/`](../../../features/) and the per-layer folders
- Field-by-field mapping — that's in the per-feature `*-mapping.md` docs in [`../`](../) or [`../../database/table-mapping/`](../../database/table-mapping/)
