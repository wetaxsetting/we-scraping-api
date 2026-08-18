# Deployment — Index

Entry point for every deployment artifact in `wecms-api`. **This is an index, not a specification** —
each row links to the document that owns the detail.

**Status:** active · **Last reviewed:** 2026-08-17

> **Deployment posture.** The refactor targets **SIT** today; UAT is *conditionally ready* and PROD is
> blocked on the gates listed in [`../../CLAUDE.md`](../../CLAUDE.md) §"Security & SIT Readiness Status".
> Flyway is **disabled** — schema is external and DBA-applied (see
> [`../references/database/flyway-migration-baseline.md`](../references/database/flyway-migration-baseline.md)).

## Document types

| Type | Meaning |
|---|---|
| **Runbook** | Step-by-step operational procedure — follow it during a deploy |
| **Checklist** | Pre-/post-deploy verification list |
| **Reference** | Configuration / variable lookup, not a procedure |
| **Handoff** | Written for another team (infra, ops, DBA) |
| **Historical** | Point-in-time record; not the current position |

## Environment / configuration

| Doc | Type | Notes |
|---|---|---|
| [wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) | Reference | Batch env-var reference |
| [batch-dispatch-variable-reference.md](batch-dispatch-variable-reference.md) | Reference | Dispatch flag/variable reference |
| [config-bare-placeholder-migration.md](config-bare-placeholder-migration.md) | Runbook | Bare-placeholder config migration |
| [`application-sit.yml.server-ready`](application-sit.yml.server-ready) | Reference — **deployment artifact** | The server-deployment variant of `src/main/resources/application-sit.yml`: identical shape, but its `spring.config.import` points at the **host-absolute** `.env.sit` path (`/DATA/src-wecms/wecms-backend/environment/`) instead of `./`. Contains **no secrets** — every value is a bare `${VAR}` placeholder. ⚠️ **Drifted** — see the note below. |

> **⚠️ `application-sit.yml.server-ready` has drifted from the live config (found 2026-08-17).**
> It was last written 2026-07-31; `src/main/resources/application-sit.yml` was updated 2026-08-03.
> Two lines differ — the intended host-absolute `spring.config.import` path (correct, that is the
> file's whole purpose) **and** `read-timeout-ms`, which is `20000` here vs `60000` in the live file
> (unintended drift). **Do not deploy this file as-is without re-syncing it against
> `src/main/resources/application-sit.yml`.** Re-syncing is a backend/deployment change and is out of
> scope for a documentation pass — raised for developer action.
>
> **Why it stays in `docs/deployment/`:** the canonical config location
> (`src/main/resources/application-sit.yml`) is already occupied by the authoritative file, and a
> non-loadable `.server-ready` sibling there would be worse. No clearly-correct alternative repository
> location exists, so it stays beside the deployment docs that reference it.

> SIT configuration is loaded from **host environment / `bin/setenv.sh`**, not from a committed `.env.sit`.

## SIT

| Doc | Type |
|---|---|
| [sit-security-deployment-checklist.md](sit-security-deployment-checklist.md) | Checklist |
| [sit-bare-placeholder-deploy-checklist.md](sit-bare-placeholder-deploy-checklist.md) | Checklist |
| [sit-deployment-checklist-wecms-batch.md](sit-deployment-checklist-wecms-batch.md) | Checklist |
| [expense-payment-dispatch-sit-uat-checklist.md](expense-payment-dispatch-sit-uat-checklist.md) | Checklist |
| [haengsung-sit-deployment-checklist.md](haengsung-sit-deployment-checklist.md) | Checklist |
| [vat-declaration-sit-2026-08-13.md](vat-declaration-sit-2026-08-13.md) | Historical — dated SIT record |

## UAT

| Doc | Type |
|---|---|
| [sit-uat-scheduler-deployment-checklist.md](sit-uat-scheduler-deployment-checklist.md) | Checklist — SIT **and** UAT scheduler |
| [expense-payment-dispatch-sit-uat-checklist.md](expense-payment-dispatch-sit-uat-checklist.md) | Checklist — SIT **and** UAT dispatch |

## PROD

No PROD runbook exists yet. PROD is gated on secret rotation, callback hardening (R-03), action-level
authorization (R-02), and the multi-instance durability decision — see
[`../security/README.md`](../security/README.md) and `../../CLAUDE.md` §"Security & SIT Readiness Status".

## Payment dispatch

| Doc | Type |
|---|---|
| [payment-dispatch-final-readiness.md](payment-dispatch-final-readiness.md) | Readiness |
| [payment-dispatch-merge-sit-handoff.md](payment-dispatch-merge-sit-handoff.md) | Handoff |
| [callback-security-configuration.md](callback-security-configuration.md) | Reference — dispatch callback protection (**R-03 gate**) |
| [bulk-file-reconciliation-procedure.md](bulk-file-reconciliation-procedure.md) | Runbook |
| [bulk-file-backlog-reconciliation-worksheet.md](bulk-file-backlog-reconciliation-worksheet.md) | Worksheet |

## Batch

| Doc | Type |
|---|---|
| [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) | **Runbook** — primary batch activation procedure |
| [wecms-batch-activation-and-external-confirmations.md](wecms-batch-activation-and-external-confirmations.md) | Runbook — external confirmation steps |
| [wecms-batch-infra-team-checklist.md](wecms-batch-infra-team-checklist.md) | Handoff — infra team |
| [wecms-batch-infrastructure-readiness-audit.md](wecms-batch-infrastructure-readiness-audit.md) | Readiness |
| [wecms-batch-schedule-seed-review.md](wecms-batch-schedule-seed-review.md) | Reference — schedule seed review |

## Readiness audits — live in `audits/`, not here

Deployment **audits** are audit records and live under [`../audits/deployment/`](../audits/deployment/):

| Doc | Status |
|---|---|
| [../audits/deployment/deployment-readiness-2026-07-29.md](../audits/deployment/deployment-readiness-2026-07-29.md) | ✅ **CANONICAL** readiness audit |
| [../audits/historical/deployment-readiness-2026-07-28.md](../audits/historical/deployment-readiness-2026-07-28.md) | Superseded by the above — preserved |
| [../audits/deployment/payment-dispatch-flags-deployment-readiness-audit.md](../audits/deployment/payment-dispatch-flags-deployment-readiness-audit.md) | Flag-verification audit |
| [../audits/deployment/scheduler-final-readiness.md](../audits/deployment/scheduler-final-readiness.md) | Scheduler readiness |
| [../audits/batch/](../audits/batch/) | Batch parity + boundary audits |

## Troubleshooting

No repo-local troubleshooting guide exists yet — see the workspace `docs/TROUBLESHOOTING.md`.
*(A repo-local operations/troubleshooting guide is a recommended P2 addition.)*

## Related

- [`../security/README.md`](../security/README.md) — security closure, required secrets, rotation handoff
- [`../audits/README.md`](../audits/README.md) — full audit index
- [`../references/database/flyway-migration-baseline.md`](../references/database/flyway-migration-baseline.md) — why Flyway is disabled and how DDL is applied
