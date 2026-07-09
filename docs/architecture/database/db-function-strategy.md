# DB Function Strategy — Hybrid

**Status:** Adopted 2026-05-03.
**Scope:** Project-wide for the JEX → Spring Boot refactor.

## Decision

The refactor uses a **hybrid approach** for legacy JEX PL/pgSQL functions:

- **Migrate to refactor PostgreSQL** — for read-only legacy helpers that are referenced by JEX IDOs we are porting and where Java translation carries non-trivial parity risk (regex, locale, edge cases).
- **Port to Java** — for new logic that has no JEX precedent, simple lookup-style translators, and anything that needs Spring beans / configuration / tenant context.
- **Always port to Java** — for write-mutating procedures (`wb_init_*`, `wb_insert_*`, etc.). DB-side write logic in the refactor is forbidden.

## Why hybrid

The DEV DB has 59 `wb_*` functions. Translating every one into Java would be slow and risky — especially the regex/format helpers where PostgreSQL's `~` operator and `REGEXP_REPLACE` semantics differ from Java's `Pattern`. Migrating verbatim from `pg_proc` eliminates that translation risk for parity-critical reads.

At the same time, the refactor is the place where business logic should live in Spring services — not in opaque DB procedures. So writes and new behavior stay in Java.

## Decision rule

| Condition | Migrate function | Port to Java |
|---|---|---|
| Read-only, called by a JEX IDO we're porting, complex logic | ✅ | |
| Read-only, called by a JEX IDO, simple lookup (would be a 3-line enum/record in Java) | | ✅ |
| Performs writes (`INSERT`/`UPDATE`/`DELETE`) | | ✅ (always) |
| Depends on tenant context, security context, HTTP request | | ✅ |
| New business logic (no JEX precedent) | | ✅ |

## How to migrate a function

1. **Pull the body verbatim** from JEX DEV `pg_proc` via the `postgres_dev_server_jexframe` MCP. Include the `pg_get_functiondef(oid)` output unmodified.
2. **Migrate dependencies first** — any tables the function reads (e.g. `BANK_INFM_DETL` for `wb_get_bank_nm`) and any other functions it calls. Seed data from DEV.
3. **Single Flyway migration** combining table(s) + seed + function(s). Make it idempotent (`CREATE TABLE IF NOT EXISTS`, `INSERT ... ON CONFLICT DO NOTHING`, `CREATE OR REPLACE FUNCTION`).
4. **Migration header** must cite the source (DEV `pg_proc`) and the date pulled — the function may evolve on DEV; the migration is a snapshot.
5. **Wire from JPA** via Hibernate's `@Formula` on the entity if the value is row-scoped, OR via JPQL `function('name', args...)` in queries that don't load the entity. Native queries are NOT required.

## How to call from Spring Boot

### Preferred: `@Formula` on the entity

```java
@Formula("wb_format_acct_nick_nm(acct_nick_nm, bank_cd, acct_no, hand_reg_yn)")
private String formattedAcctNickNm;
```

- Hibernate splices the SQL into every `SELECT` that loads the entity.
- Aggregation queries that don't load the entity (count, distinct, group-by projections) skip the function call automatically.
- Read-only at the JPA layer; the underlying column is mutated normally and the formula re-evaluates on next load.

### Alternative: JPQL `function()`

```java
@Query("SELECT function('wb_format_acct_nick_nm', a.acctNickNm, a.bankCd, a.acctNo, a.handRegYn) FROM AcctMstr a WHERE ...")
```

Use when you don't want the formula on every entity load (e.g. it's only needed for a specific view), or when projecting into a DTO that doesn't include the underlying entity.

## Migration cadence

- **One function per migration is OK** but cluster related functions when they share dependencies (`wb_format_acct_nick_nm` + `wb_get_bank_nm` + `BANK_INFM_DETL` shipped together in V44 because the formatter calls the lookup).
- **Migrate when you need it.** Don't pre-emptively migrate functions for screens not yet in scope.
- **Document the function in the feature spec** that triggered the migration. The full per-function inventory & migration-priority classification lives in [../../references/database/db-function-classification.md](../../references/database/db-function-classification.md) — add new call-site references there.

## Risks managed by this strategy

| Risk | Mitigation |
|---|---|
| Translation error porting regex / CASE branches into Java | Eliminated — function body copies verbatim |
| Refactor falls behind DEV when a function changes | Migration header cites pulled-on date; quarterly parity audit verifies |
| New screens accumulate unmigrated function dependencies | Per-feature checklist: list `wb_*` functions referenced and triage at design time |
| `@Formula` adds overhead on hot paths | Tolerable for typical row counts (<1000); switch to projection if a hot path is hit |
| Test environments without PostgreSQL break | Spring integration tests use Testcontainers PostgreSQL; H2 unit tests don't load this entity |

## Currently migrated functions

| Function | Migration | First feature that needed it |
|---|---|---|
| `wb_get_next_val` | V25 / V26 / V27 series | Sprint 0 (sequence numbering) |
| `wb_format_acct_nick_nm` | V44 | Banking > Account Inquiry (VND) |
| `wb_get_bank_nm(varchar, varchar)` | V44 | Banking > Account Inquiry (VND), called transitively from `wb_format_acct_nick_nm` |

Other dependencies migrated alongside:

| Table | Migration | Notes |
|---|---|---|
| `BANK_INFM` | V20 | Full bank names |
| `BANK_INFM_DETL` | V44 | Short bank names (`BANK_DETL_INFO_CD = '10'` only — extend if other codes are needed) |

## What stays in Java

- All `domain/*/service/` business logic.
- `BankCdResolver` (full-name lookup with cache; calls `BankInfmRepository`).
- `AmountFormatter`.
- All sort whitelists, validation, security filters.
- Excel export shaping (`ExcelExportService`, `*ExportProvider`).

## Cross-references

- [V44 migration](../../../src/main/resources/db/migration/V44__bank_infm_detl_and_nickname_fns.sql)
- [Account Inquiry (VND) feature spec](../../features/banking/current-savings-vnd/account-inquiry/README.md) — first consumer
- [Account Inquiry (VND) DB doc](../../references/database/banking/account-inquiry-vnd-db.md) — V44 migration entry
