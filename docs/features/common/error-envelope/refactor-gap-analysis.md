# Refactor Gap Analysis — Error Envelope

**Part of:** Common → error-envelope
**Last verified:** 2026-06-23

Status of the error-envelope infrastructure against its legacy origin and against the documented rules.
Gap vocabulary: **DEAD LEGACY** · **APPROVED DEVIATION** · **PARTIAL** · **CODE DRIFT** · **DONE**.

## 1. Vocabulary / status table

| Item | Status | Detail |
|---|---|---|
| `COMMON_HEAD` / `REC` envelope | **DEAD LEGACY** | Retired CTO directive 2026-05-08. Replaced by `ApiResponse` + `CommonHead`. Re-introducing is a review failure. No compatibility surface. |
| Lowercase snake_case wire | **APPROVED DEVIATION** | Legacy JEX wire was UPPER_SNAKE; refactor wire is lowercase snake_case via global Jackson `SNAKE_CASE`. Approved 2026-05-08. (DB columns stay UPPER_SNAKE — separate concern.) |
| JEX ML code ids reused verbatim | **DEAD LEGACY** | `P00001`-style ids are not reused; translated to `WBExxxx`/`WBIxxxx`, legacy id kept only in migration prose. |
| Khmer (`KM`) i18n | **PARTIAL** | `LangCd` enum includes `KM` and `Accept-Language: km` resolves to it; `MessageResolver` can fan to KM via the explicit-locale overload. But **no `messages_km.properties`** exists yet (CLAUDE.md §"i18n Rules" lists only EN today, with KO/VI/KM "as real translations arrive"). KM resolves → falls back to the code/EN. Frontend has **no `km.json`** at all (`LocaleProvider` only supports `EN`/`KO`/`VI`). |
| `trace_id` per error | **DONE** | `TraceIdFilter` + `TraceId` + `X-Trace-Id` header + MDC `%X{traceId}`. JEX had no equivalent. |
| Global exception funnel | **DONE** | Single `@RestControllerAdvice` covering 15 handler methods (`GlobalExceptionHandler.java`). |
| Audit columns `varchar(14)` UTC | **APPROVED DEVIATION / migration-safe** | `BaseEntity` keeps Java `OffsetDateTime` ergonomics but persists legacy `varchar(14)` via `LegacyDtmConverter` — deliberately NOT `timestamptz`. |
| `RelativeTimeFormatter` UTC bucketing | **APPROVED DEVIATION** | Diverges from legacy `common.app.humanizeDatetime` (which parsed local-zone and over-stated elapsed time). Data is UTC, so the local parse was a legacy bug; refactor parses UTC (`RelativeTimeFormatter.java:20-26`). |

## 2. Code drift to flag

| Drift | Location | Recommendation |
|---|---|---|
| `AuthenticationException` code | **CODE DRIFT** — `GlobalExceptionHandler.java:167` emits **`WBE1401`**; `wecms-api/CLAUDE.md` §"Exception → response mapping" says `WBE1009` | Code is source of truth → `WBE1401`. The CLAUDE.md row is stale; flag for correction. The code has an explicit comment that `WBE1009` stays reserved for login wrong-credentials, so the divergence is intentional in code, accidental in the doc. |

## 3. Translation coverage (i18n PARTIAL detail)

| Locale | Backend `messages_*.properties` | Frontend `locales/*.json` | Notes |
|---|---|---|---|
| EN | ✅ `messages.properties` + `messages_en.properties` | ✅ `en.json` | Fallback locale. |
| KO | ⏳ "as real translations arrive" | ✅ `ko.json` | Backend bundle not confirmed present this pass. |
| VI | ⏳ "as real translations arrive" | ✅ `vi.json` | Backend bundle not confirmed present this pass. |
| KM | ⚠️ **PARTIAL** — enum + resolution path exist, no bundle | ❌ **absent** — `LocaleProvider` supports only EN/KO/VI | Khmer audit fan-out resolves but renders the code/EN until a `messages_km.properties` lands; frontend has no KM at all. |

## 4. No-table confirmation

There is no envelope/trace-id table and none is needed — these are runtime constructs. The only data-layer
surface is `BaseEntity` audit columns + `TenantContext` (request-scoped, non-persisted). Creating a
refactor-only "envelope" or "trace" table is forbidden (no legacy table backs it) — see CLAUDE.md
§"Refactor DB & Legacy-Compatibility Rules" item 1.

## 5. Open items

- **OQ-EE-1 — CLAUDE.md `WBE1009`→`WBE1401` row fix.** Update the §"Exception → response mapping" table
  to match `GlobalExceptionHandler.java:167`. Doc-only; no code change.
- **OQ-EE-2 — KM translation files.** Ship `messages_km.properties` (backend) + `km.json` +
  `LOCALES` extension (frontend) when verified Khmer copy lands. Until then KM stays PARTIAL.

## Cross-links

- Migration mapping: [migration-guide.md](migration-guide.md)
- Source map: [source-code-index.md](source-code-index.md)
