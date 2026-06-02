# migration/risk-analysis/

Risk register for the JEX → refactor migration.

## Purpose

Per-feature and cross-cutting risk write-ups: behavioural drift, data-loss risk, audit-trail continuity, performance regressions, and external-dependency surprises (bank APIs, ERP connectors, e-invoice providers).

## What goes here

- Per-feature risk file (`<feature>-risks.md`)
- Cross-cutting risk topics (audit-id continuity, secrets migration, sequence-generator parity)
- Each risk: severity, likelihood, mitigation, owner, status

## Refactor scope

Per memory `project_refactoring_scope`: the refactor is **local-only R&D** — no DEV/UAT/PROD deploy, no bank integration coexistence. Risks here are about **design correctness**, not deployment cutover; cutover risks become live work once the refactor leaves R&D.

## Related

- Audits: [`../../audits/`](../../audits/)
- Rollout plan: [`../rollout-plan/`](../rollout-plan/)
