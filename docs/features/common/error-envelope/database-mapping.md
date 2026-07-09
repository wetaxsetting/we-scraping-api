# Database Mapping — Error Envelope

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

> **No dedicated table.** The error envelope, trace id, and exception funnel are **runtime constructs** —
> nothing in this module maps to a DB table. What this module *does* own at the data layer is the shared
> **audit-column base class** (`BaseEntity`), its **legacy-dtm converter**, and the request-scoped
> **tenant context** that scopes every tenant query. Those are documented here because they are the
> common data-layer surface every feature inherits.

## 1. Shared audit columns — `BaseEntity`

`BaseEntity` (`common/entity/BaseEntity.java`) is a `@MappedSuperclass` (not a table) whose columns are
inherited by every entity mapped to a legacy JEX table.

| Java field | `@Column` | Type on legacy JEX DDL | Notes |
|---|---|---|---|
| `regId` | `REG_ID`, length 100, `updatable=false` | `VARCHAR(100)` | Creator id; set by the service, not auto |
| `regDtm` | `REG_DTM`, length 14, `updatable=false` | `VARCHAR(14)` | `yyyyMMddHHmmss` UTC; `@PrePersist` stamps it |
| `modId` | `MOD_ID`, length 100 | `VARCHAR(100)` | Last-editor id |
| `modDtm` | `MOD_DTM`, length 14 | `VARCHAR(14)` | `@PrePersist` + `@PreUpdate` stamp it |

- `@PrePersist onCreate()` (`BaseEntity.java:36-41`) sets `regDtm` + `modDtm` to `OffsetDateTime.now()`
  when null.
- `@PreUpdate onUpdate()` (`BaseEntity.java:43-46`) refreshes `modDtm` only.
- Column names mirror legacy JEX **column-for-column** per the Legacy Schema Parity Rule — they are
  never renamed for the refactor.

## 2. Legacy datetime conversion — `LegacyDtmConverter`

`LegacyDtmConverter` (`common/entity/LegacyDtmConverter.java`) is a JPA `@Converter` mapping
`OffsetDateTime ⇄ String`:

- **Write** (`convertToDatabaseColumn`, `LegacyDtmConverter.java:31-34`): convert to UTC, format
  `yyyyMMddHHmmss`. So Hibernate `ddl-auto: validate` sees a `VARCHAR` column matching live JEX.
- **Read** (`convertToEntityAttribute`, `LegacyDtmConverter.java:37-45`): parse the 14-char string as UTC;
  **tolerate malformed/legacy strings** by returning `null` rather than failing the read.

> The Java API stays `OffsetDateTime` for ergonomics; the DB stays legacy `varchar(14)` for migration
> safety. This is the canonical pattern — do not "improve" legacy audit columns to `timestamptz`.
> (Exception precedent: `USER_LOGIN_HIS.REG_DTM` is mapped as a raw `String varchar(14)`, see memory
> `project_user_login_his_reg_dtm_string.md`.)

## 3. Tenant context — `TenantContext` (no table)

`TenantContext` (`common/tenant/TenantContext.java`) is a `ThreadLocal<Principal>` request-scoped holder —
**not persisted**. Populated by `JwtAuthenticationFilter` from the signed JWT claims and cleared at request
end (`TenantContext.java:89-91`).

`Principal` record (`TenantContext.java:163-164`): `(userId, userNm, bizKey, langCd, authGrCd, prdtType)`.

Static accessors used pervasively by service-layer queries and audit writes:

| Accessor | Returns | Used by (examples) |
|---|---|---|
| `currentUserId()` | JWT `sub` | every tenant-scoped write |
| `currentUserNm()` | display name (falls back to `userId`) | audit `REG_NM`, firm-banking `LAST_EDTR` |
| `currentBizKey()` | selected `BIZ_KEY` | **mandatory** `BIZ_KEY` filter on every tenant query |
| `currentLangCd()` | `LangCd` | message resolution, locale fan-out |
| `currentAuthGrCd()` / `currentIsAdmin()` | `"ADMIN"`/`"USER"` | service-layer admin guards |
| `currentPrdtType()` | verbatim `PRDT_TYPE` letter (may be `null`) | per-product gates via `PrdtType.fromCode(...)` |

The `bizKey` here is the value the JWT was issued for — the frontend cannot override it on a normal
request (`TenantContext.java:11-16`).

## 4. Tables this module reads/writes

| Table | Relationship |
|---|---|
| *(none)* | The envelope / exception / trace-id / i18n surface touches **no table directly**. |
| `AUDIT_LOGS_INFM` | **Not owned here** — the shared audit writer (`common/auditlog/`, see [`../audit-log/README.md`](../audit-log/README.md)) writes it, reusing this module's `MessageResolver` locale fan-out + `TenantContext`. Documented there, not here. |
| Every legacy table with audit columns | `BaseEntity` supplies their `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` mapping. |

## Cross-links

- Envelope + status semantics: [functional-specification.md](functional-specification.md)
- Authoritative table baseline (verify any table touched): [`../../../references/database/legacy-table-inventory.md`](../../../references/database/legacy-table-inventory.md)
