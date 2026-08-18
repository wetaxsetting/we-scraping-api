# WeCMS Batch Migration — moved

> **This document was reorganized into a feature-oriented doc set (2026-06-24).** WeCMS Batch is a backend business module (firm-banking dispatch), not merely a migration reference, so its content now lives under:
>
> ## → [wecms-api/docs/features/wecms-batch/](../../features/wecms-batch/README.md)

The former single migration document was split as follows:

| Was (here) | Now |
|---|---|
| Module overview / architecture summary | [features/wecms-batch/README.md](../../features/wecms-batch/README.md) |
| Business purpose, legacy + Spring architecture, all flow diagrams, design decisions, future improvements | [features/wecms-batch/firm-banking-dispatch.md](../../features/wecms-batch/firm-banking-dispatch.md) |
| Schedulers, cron, `afterCommit`, ShedLock | [features/wecms-batch/scheduler-flow.md](../../features/wecms-batch/scheduler-flow.md) |
| The ten dispatch tables | [features/wecms-batch/database-schema.md](../../features/wecms-batch/database-schema.md) |
| Build, config, env vars, activation checklists | [features/wecms-batch/deployment-guide.md](../../features/wecms-batch/deployment-guide.md) |
| (new) operational diagnostics | [features/wecms-batch/troubleshooting.md](../../features/wecms-batch/troubleshooting.md) |
| **Legacy JEX → Spring mapping** (the migration-traceability content) | [features/wecms-batch/legacy-jex-mapping.md](../../features/wecms-batch/legacy-jex-mapping.md) |

Per the WeCMS doc convention, migration-traceability content for this module is consolidated in [legacy-jex-mapping.md](../../features/wecms-batch/legacy-jex-mapping.md). This stub is retained so existing links keep resolving — do not author new content here.

**Related (unchanged):** schema DDL parity [../database/payment/firm-banking-dispatch-schema.md](../database/payment/firm-banking-dispatch-schema.md) · dispatch-path peer [internal-transfer-mapping.md](internal-transfer-mapping.md) · audits [TO-BE process](../../audits/legacy-parity/wecms-batch-tobe-process-audit.md) · [final gap](../../audits/legacy-parity/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md).
