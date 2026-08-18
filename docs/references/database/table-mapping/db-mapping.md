# DB Mapping — Refactor PostgreSQL ↔ JEX schema


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../flyway-migration-baseline.md) §5.

Cross-feature index of tables touched by the refactor, with their JEX counterpart and the Flyway migration that introduced them.

JEX is **read-only reference**. The refactor mirrors JEX schema column-for-column unless an explicit divergence is documented.

## Local DB

- Host: `localhost:5434`
- Database: `wecms_dev`
- User: `postgres`
- Migrations: `wecms-api/src/main/resources/db/migration/V<N>__<topic>.sql`
- Single tenant: `BIZ_KEY = VN_VNM0004_000001`

## Table mapping

| Table | Refactor Entity | Domain | First Migration | JEX Counterpart | Notes |
|---|---|---|---|---|---|
| `BUSI_INFM` | `BusiInfm` | `auth` | V1 | `BUSI_INFM` | PK = `BIZ_KEY`; tenant master |
| `USER_INFM` | `UserInfm` | `auth` | V1 | `USER_INFM` | PK = `USER_ID`; password = legacy SHA-256 (`public.hash()`); verify-only, no upgrade (corrected 2026-06-10) |
| `USER_BUSI_CONN_INFM` | `UserBusiConnInfm` | `auth` | V1 | `USER_BUSI_CONN_INFM` | Composite PK (`USER_ID`, `BIZ_KEY`) |
| `USER_LOGIN_HIS` | `UserLoginHis` | `auth` | V1 (PK rebuilt in V28) | `USER_LOGIN_HIS` | Composite PK (`USER_ID`, `REG_DTM`, `REG_UID`) post-V28 |
| `ACCT_MSTR` | `AcctMstr` | `banking` | V1 | `ACCT_MSTR` | Account master |
| `ACCT_TRNS_HSTR` | `AcctTrnsHstr` | `banking` | V1 (UNIQUE added V28) | `ACCT_TRNS_HSTR` | Transaction history; `acct_trns_hstr_uk01` from V28 |
| `SYS_SCR_OPTION_WECMS` | `SysScrOptionWecms` | `setting` | V30 (cutover V32) | `SYS_SCR_OPTION_WECMS` | Sys defaults for View Setting; `WEB_SVC_ID` semantic kebab-case post-V32 |
| `USER_SCR_OPTION_WECMS` | `UserScrOptionWecms` | `setting` | V30 (cutover V32) | `USER_SCR_OPTION_WECMS` | User overrides for View Setting; `DYM_AREA_ID` NOT NULL post-V32 |

> Add a row here when introducing a new entity. Keep the entity name PascalCase (Java convention) and the table name `UPPER_SNAKE` (JEX convention).

## Migration index (high level)

See per-feature migration intent docs in this folder, and the parity audit at [../audits/migration-audit/db-parity-audit-2026-04-30.md](../../../audits/historical/db-parity-audit-2026-04-30.md). Full migration log is documented inline in [../../../wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Migrations".

| Migration | Scope |
|---|---|
| V1–V10 | Initial Sprint 0 schema (auth + supporting tables) |
| V25–V27 | `wb_get_next_val` corrective rounds |
| V28 | Sprint 1 final parity audit fixes (additive) |
| V29 | Real-DEV seed round 2 (226 rows across 5 tables) |
| V30 | View Setting tables seeded for `fnsh_0001_001` |
| V31 | PROD active table scope cleanup (drops 6 unused tables) |
| V32 | View Setting semantic `WEB_SVC_ID` cutover + `DYM_AREA_ID` NOT NULL |
| V33 | View Setting special FIELD_ID seed (`CHECKBOX`) |
| V34 | View Setting `SIZE` aligned to VARCHAR(10) + width seed |

## Conventions

- **Tenant scope:** every tenant-scoped table includes `BIZ_KEY` — never query without it
- **Audit columns:** every table includes `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM` (handled by `BaseEntity` `@PrePersist`/`@PreUpdate`)
- **Naming:** `@Table(name = "UPPER_SNAKE")`, `@Column(name = "UPPER_SNAKE")` — column-for-column mirror of JEX
- **Migrations:** additive only — never edit applied migrations
- **Composite keys:** use `@IdClass` with a `Serializable` companion record
- **No JPA cascade** on cross-aggregate relations

## Reference

- JEX schema reference: [../../jex/architecture/database-design.md](../../../../../docs/jex/architecture/database-design.md), [../../jex/architecture/ERD.md](../../../../../docs/jex/architecture/ERD.md)
- JEX DEV DB: governed by [.claude/rules/database.md](../../../../../.claude/rules/database.md) — read-only via `postgres` MCP
- Local DB setup: see memory `reference_local_db_setup.md`
