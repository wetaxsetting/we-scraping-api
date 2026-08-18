# testing/sit/

System Integration Test scenarios.

## Purpose

SIT covers the cross-component flows: `wecms-web` ↔ `wecms-api` ↔ DB, plus any future external-system handshakes. Distinct from unit tests (per-class) and UAT (business-led).

> **SIT readiness — field-name casing (migration closed 2026-06-07).** Legacy JEX APIs used UPPER_SNAKE field names. The Spring/Next.js refactor uses lowercase snake_case JSON field names via global Jackson SNAKE_CASE configuration (approved modernization, CTO directive 2026-05-08 — not a legacy parity gap). **SIT assertions on every active endpoint's request/response bodies must expect lowercase snake_case keys** (e.g. `acct_no`, `bank_cd`, `ccy_cd`, `rows`, `total_count`). The former UPPER_SNAKE holdovers (Transaction History, Daily Cash / Daily Financial reports, Bank-Matching) were **all migrated to lowercase** on 2026-06-07, and the deprecated UPPER_SNAKE Transfer-History endpoint was deleted — **no UPPER_SNAKE WeCMS contract surface remains.** (The external Coocon/NXiSAS vendor-gateway payloads keep vendor-defined casing — out of scope; they are not the WeCMS REST wire.) Full inventory + migration record: [../../audits/field-casing-parity-audit-2026-06-06.md](../../audits/historical/field-casing-parity-audit-2026-06-06.md).

## What goes here

- Per-feature SIT plan (`<feature>-sit.md`)
- Cross-feature scenarios (auth + approval-line + payment, etc.)
- SIT cycle reports
