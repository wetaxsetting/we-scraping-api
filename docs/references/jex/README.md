# references/jex/

JEX reference notes used during refactor design — **read-only mirror, not source of truth**.

## Purpose

Quick-reference notes pulled from the JEX source while designing the refactor: framework conventions, ML-code patterns, service-id ↔ feature mapping, common DAO shapes, audit-table contracts.

## What goes here

- Per-topic JEX cheatsheets (`<topic>.md`)
- Service-id → feature-slug lookup tables
- ML-code conventions (legacy `comm_0220_001_*` and how it maps to the new slug)

## Authority

JEX is **read-only reference**. The authoritative JEX docs live under [`../../../jex/`](../../../../docs/jex/). Notes here are convenience extracts for refactor designers — when in doubt, read the live JEX source under `WECMS DEV/` and the per-feature spec under `docs/jex/features/`.

## What does NOT go here

- New behaviour proposals (those belong in [`../../features/`](../../features/) or [`../../planning/`](../../planning/))
- JEX bug history (that lives under [`../../../jex/features/`](../../../../docs/jex/features/))
