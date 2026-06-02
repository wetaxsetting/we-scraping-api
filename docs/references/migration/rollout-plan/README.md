# migration/rollout-plan/

Rollout / cutover plan for the JEX → refactor migration.

## Purpose

Capture the **future** cutover plan: what goes live first, in what order, with what fallback, and the parity checks each step depends on. Today this is design intent only.

## Refactor scope

Per memory `project_refactoring_scope`: the refactor is **local-only R&D**. No DEV/UAT/PROD deploy is planned in this phase. Documents here are **draft cutover plans** — they do not affect the live JEX system today.

## What goes here

- Phase-by-phase rollout drafts
- Per-feature go-live readiness checklist
- Data-migration plan (one-shot vs dual-write vs read-through)
- Communication plan (who tells which tenant, when)

## Related

- Risk register: [`../risk-analysis/`](../risk-analysis/)
- Planning roadmap: [`../../planning/roadmap/`](../../../planning/roadmap/)
