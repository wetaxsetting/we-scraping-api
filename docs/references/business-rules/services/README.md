# backend/services/

Service-layer (business-logic) design for `wecms-api`.

## Purpose

Document the `@Service` classes that sit between controllers and repositories: orchestration, transaction boundaries, domain rules, validation, audit-log writes.

## What goes here

- Per-feature service design (`<feature>-service.md`)
- Cross-cutting service patterns (audit logging, soft-delete, in-use guards, sequence generators)

## Naming convention

- kebab-case, suffixed with `-service.md` where appropriate
- Filename slug must match the feature slug under [`../../features/`](../../../features/)

## What does NOT go here

- HTTP/REST shape → [`../../api-spec/`](../../api-spec/)
- Persistence shape → [`../repositories/`](../repositories/) and [`../entities/`](../entities/)
- Bank / ERP / e-invoice external calls → [`../integrations/`](../integrations/)
