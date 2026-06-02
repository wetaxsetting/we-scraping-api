# backend/api/

REST API surface for `wecms-api` (Spring Boot 3.5.13).

## Purpose

Controller-level documentation: endpoint URIs, HTTP methods, request / response DTOs, status codes, error model, and per-endpoint authorisation.

## What goes here

- Per-feature or per-controller markdown files (e.g. `firm-banking-settings-api.md`)
- The shared error / pagination / audit envelope contract
- API versioning rules and deprecation notes

## Naming convention

- kebab-case filenames, suffixed with `-api.md`
- Filename slug must match the feature slug used under [`../../features/`](../../features/)
- Wire format is **lowercase snake_case** for JSON keys (per `project_api_naming_lowercase` (Needs Confirmation) memory / CTO directive 2026-05-08)

## Related

- Feature specs: [`../../features/`](../../features/)
- Service layer: `../services/` (Needs Confirmation)
- Security filter chain & auth: [`../security/`](../../architecture/security/)
- External system clients (banks, ERP): `../integrations/` (Needs Confirmation)
