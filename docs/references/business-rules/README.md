# backend/

Spring Boot service design for `wecms-api`.

## Purpose

Per-feature backend design: REST endpoints, DTOs, services, repositories, validation, error model, transaction boundaries.

## What goes here

- REST endpoint specifications (path, method, request DTO, response DTO, error codes)
- Service-layer design (business logic, `@Transactional` rules, JEX behavior cited)
- Repository design (JPA derived methods or `@Query`)
- Validation rules (`@NotBlank`, `@Pattern`, custom validators)
- Per-domain notes: `auth/`, `banking/`, `payment/`, `setting/`, etc.

## What does NOT go here

- Frontend Next.js page design → [../frontend/](../../../../wecms-web/docs/)
- JEX → Spring service mapping → [../migration/](../migration/)
- DB schema / Flyway migrations → [../database/](../database/)
- System-wide architecture → [../architecture/](../../architecture/)
- JEX behavior — read-only reference at [../../jex/](../../../../docs/jex/), never duplicated here

## Example file names

- `account-inquiry-vnd.md`
- `login-auth.md`
- `screen-options-view-setting.md`
- `excel-export-shared.md`
- `api-mapping.md` — cross-feature index of endpoints

## Reference

JEX is **read-only reference**. Read `WECMS DEV/.../wsvc/<svc>.xml`, `ido/<ido>.xml`, `_act.jsp` for functional spec. Translate to Spring Boot — never copy `JexData` / `IDODynamic` / `_act.jsp` patterns into Java code.
