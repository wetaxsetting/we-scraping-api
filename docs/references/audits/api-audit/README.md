# audits/api-audit/

API parity audits comparing the refactor REST surface to JEX behaviour.

## Purpose

For each refactor endpoint, document the parity check against the JEX WSVC / `_act.jsp` source of truth: request shape, response shape, audit-log row, error envelope, status codes.

## What goes here

- Per-endpoint or per-feature audit (`<feature>-api-audit-<yyyy-mm-dd>.md`)
- Findings (F-1, F-2, …) with severity, evidence, and fix-or-accept decision
- A closing checklist mirrored back into the feature spec

## Naming convention

- `<feature>-api-audit-<yyyy-mm-dd>.md`
- Slug matches the feature; date is the audit date.
