# migration/

JEX → Spring Boot service-by-service translation plan.

## Purpose

Per-feature migration mapping: which JEX WSVC/IDO maps to which Spring controller/service/repository, field-by-field. The single place that proves traceability between the legacy system and the refactor.

## What goes here

- Per-feature mapping files (one per JEX feature being refactored)
- JEX WSVC ID → Spring controller method
- JEX IDO query → JPA repository method (or `@Query`)
- Field-by-field mapping (`UPPER_SNAKE` JEX field → camelCase Java property → lowercase `snake_case` JSON via the global Jackson `SNAKE_CASE` strategy; `@JsonProperty` only where the wire name diverges)
- Behavior delta (anything intentionally different from JEX, with reason)
- Cutover strategy (when/if applicable — currently R&D only, no live cutover)

## What does NOT go here

- Backend REST design → [../business-rules/](../business-rules/)
- Frontend page design → [wecms-web/docs/](../../../../wecms-web/docs/)
- DB schema design → [../database/](../database/)
- Parity audit reports → [../audits/](../audits/)
- JEX behavior — read-only reference at [../../jex/](../../../../docs/jex/)

## Example file names

- `account-inquiry-vnd.md`
- `login-auth.md`
- `view-setting.md`
- `payment-approval-box.md`
- `service-mapping.md` — cross-feature index of WSVC → REST mappings
- [`wecms-batch-migration.md`](wecms-batch-migration.md) — **moved** → the firm-banking dispatch batch is now a feature doc set at [features/wecms-batch/](../../features/wecms-batch/README.md); legacy mapping lives in [legacy-jex-mapping.md](../../features/wecms-batch/legacy-jex-mapping.md)

## Sub-folders

- [`mobile-app/`](mobile-app/README.md) — **added 2026-09-03.** The legacy **WeCMS mobile** gateway (`WABOOKS_MREC_*`, 76 services) mapped onto `wecms-api`, field by field, from the API workbook handed over with the iOS / Android source. Reference only — no mobile endpoint is implemented in this repository. Index + transport contract + coverage: [mobile-app/README.md](mobile-app/README.md); the field-level spec is split by domain (`auth` · `master-data` · `receipt` · `notification-push` · `bulletin-board`).

## Reference

JEX is **read-only reference** for all mappings. Read `WECMS DEV/.../wsvc/<svc>.xml`, `ido/<ido>.xml`, `_act.jsp`, entity, DAO. Never copy JEX patterns — translate to clean Spring Boot.
