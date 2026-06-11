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
- Wire format is **lowercase snake_case** for JSON keys (per memory `project_api_naming_lowercase` / CTO directive 2026-05-08 — source-verified, see the casing note below)

> **Field-name casing (project-wide standard).** Legacy JEX APIs used UPPER_SNAKE field names. The Spring/Next.js refactor intentionally uses lowercase snake_case JSON field names via global Jackson SNAKE_CASE configuration. This is an approved modernization under CTO directive dated 2026-05-08 and is not a legacy parity gap. Full source-verified inventory (incl. the 14 sanctioned UPPER_SNAKE holdover DTOs): [../../audits/field-casing-parity-audit-2026-06-06.md](../../audits/field-casing-parity-audit-2026-06-06.md).

## Related

- Feature specs: [`../../features/`](../../features/)
- Service layer: `../services/` (Needs Confirmation)
- Security filter chain & auth: [`../security/`](../../architecture/security/)
- External system clients (banks, ERP): `../integrations/` (Needs Confirmation)
