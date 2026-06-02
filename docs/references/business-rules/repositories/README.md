# backend/repositories/

Spring Data JPA repository definitions for `wecms-api`.

## Purpose

Document the per-entity / per-aggregate Spring Data interfaces, the query methods they expose, custom JPQL / native queries, and any specifications / dynamic-query helpers.

## What goes here

- Per-repository markdown (`<entity>-repository.md`)
- Custom-query rationale and matching JEX DAO method (so legacy traceability is preserved)
- Native-SQL access patterns (e.g. funnel through `LegacyFunctionRepository` rather than scattering `@Query(nativeQuery = true)`)

## Naming convention

- kebab-case, suffixed `-repository.md`
- Slug matches the entity under [`../entities/`](../entities/)

## Related

- Entities: [`../entities/`](../entities/)
- Services: [`../services/`](../services/)
- Database schema & indexing: [`../../database/`](../../database/)
