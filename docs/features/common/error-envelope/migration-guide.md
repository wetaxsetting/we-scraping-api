# Migration Guide — JEX Envelope → Refactor Error Envelope

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

How the legacy JEX response envelope and ML codes map onto the refactor's `ApiResponse` + `WBExxxx`
system. This is a clean rewrite informed by JEX behavior, **not** a port — no `getInput()/getOutput()`
envelope mimicry, no `JexData`, no `_act.jsp` logic inside Java.

## 1. Envelope — `COMMON_HEAD` / `REC` → `ApiResponse`

| Legacy JEX | Refactor | Notes |
|---|---|---|
| `COMMON_HEAD` block (`ERROR`, `CODE`, `MESSAGE`, UPPER_SNAKE) | `CommonHead` (`error`, `code`, `message`, lowercase) | `CommonHead.java` |
| `REC` / `REC1..N` payload arrays | `data: T` (typed record / list) | one `data` slot, typed DTO |
| no per-response trace id | `timestamp` + `path` + `trace_id` on errors | JEX-style id, see [functional-specification.md](functional-specification.md) §4 |
| UPPER_SNAKE wire keys | lowercase snake_case | global Jackson `SNAKE_CASE` strategy |

**`COMMON_HEAD` / `REC` are RETIRED (DEAD LEGACY).** Re-introducing either — or any parallel
`ApiResult<T>` shape — is forbidden and caught at review (`wecms-api/CLAUDE.md` §"Forbidden"). The only
envelope is `{ status, data, timestamp?, path?, trace_id? }`.

When porting a JEX module:

1. Replace every `COMMON_HEAD`/`REC`-style structure with `ApiResponse.ok(data)` /
   `ApiResponse.error(code, message, path, traceId)`.
2. Strip every `@JsonProperty("UPPER_SNAKE")` — the Java field name + global SNAKE_CASE strategy is
   enough. Re-add `@JsonProperty` only when the wire name truly diverges (e.g. `access_token`).
3. Replace `Map<String,Object>` controller returns with a typed Java record.
4. Replace `Map.of("CODE", …, "MESSAGE", …)` returns with `BusinessException` + the standard envelope.

## 2. JEX ML codes → `WBExxxx` / `WBIxxxx`

- **Do NOT reuse JEX ML code ids verbatim.** A legacy `P00001` becomes a new `WBExxxx`, added to
  `messages.properties`, with the legacy code referenced only in the migration-traceability prose
  (`wecms-api/CLAUDE.md` §"Refactoring rules" item 4).
- Resolution moves from JEX's `SzonHelper`/ML lookup to `MessageResolver.resolve(code, args…)` over
  Spring `MessageSource`.
- Locale moves from JEX session/cookie handling to `Accept-Language` → `LangCd` enum → JWT `langCd`
  claim → `TenantContext.currentLangCd()`.

Mapping examples (login flow — verified against `AuthService` rules in CLAUDE.md):

| Legacy behaviour | Refactor code |
|---|---|
| bad credentials / user-not-in-tenant | `WBE1009` |
| account not activated | `WBE1010` |
| account locked | `WBE1011` |
| account deleted | `WBE1128` |
| session expired (re-auth) | `WBE1401` (global handler) |

## 3. Trace id — new construct

JEX had no per-response correlation id surfaced to the client. The refactor adds the JEX-correlation-id-**shaped**
`trace_id` (`yyyyMMddHHmmss + "PT" + 14 digits + "DEVL"`) so the format is familiar to ops greppers, but it
is generated fresh per request by `TraceIdFilter` — there is no legacy field it maps **from**.

## 4. Audit columns — preserved, not redesigned

`REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` keep their legacy column names and `varchar(14)`
`yyyyMMddHHmmss` UTC shape via `BaseEntity` + `LegacyDtmConverter` (see [database-mapping.md](database-mapping.md)).
This is deliberate migration safety — the audit columns are **not** "improved" to `timestamptz`.

## 5. What is NOT migrated

- No `COMMON_HEAD` / `REC` compatibility surface (DEAD LEGACY).
- No JEX `GenericEnum` / `getLabel()` / `@JsonValue` enum machinery — refactor enums carry `code()` +
  `i18nKey()` only; labels live in frontend locale files.
- No localized strings baked into enums or DTOs — `*_key` i18n keys ship on the wire; the frontend
  resolves them.

## Cross-links

- Vocab + deviation/gap status: [refactor-gap-analysis.md](refactor-gap-analysis.md)
- Interface contract: [interface-specification.md](interface-specification.md)
- JEX common API spec (read-only reference): [`../../../../../docs/jex/api-spec/`](../../../../../docs/jex/api-spec/)
