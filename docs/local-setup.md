# Local Setup — `wecms-api`

Getting the Spring Boot backend running on a developer laptop. This is the **whole path**, in order,
with the two failure modes that actually stop people.

**Status:** active · **Last reviewed:** 2026-08-17

> **Why this document exists.** The get-running steps were previously spread across
> [`../CLAUDE.md`](../CLAUDE.md) §"Build & Run", the workspace
> [`docs/INFRASTRUCTURE_ACCESS.md`](../../docs/INFRASTRUCTURE_ACCESS.md), a `build.gradle` comment, and
> [`deployment/config-bare-placeholder-migration.md`](deployment/config-bare-placeholder-migration.md).
> None of them is a start-to-finish walkthrough, and the most common first-run failure looks like an
> Oracle bug rather than a missing file. Everything here is verified against the working tree.

## Prerequisites

| Need | Value | Verify |
|---|---|---|
| **JDK 21** | Eclipse Temurin — the Gradle toolchain pins `JavaLanguageVersion.of(21)` ([`build.gradle:12-16`](../build.gradle)) | `java -version` |
| **PostgreSQL access** | The shared dev DB `172.168.5.172:5432/wecmsdevdb`, user `wecmsdev_owner` — **VPN required**. A developer-local `localhost:5434/wecms_dev` is a documented fallback only | `psql` or any client |
| **The DB password** | Not in the repo. Obtain it per [`docs/INFRASTRUCTURE_ACCESS.md`](../../docs/INFRASTRUCTURE_ACCESS.md) | — |

## Step 1 — create `.env.local` (this is the step people skip)

```bash
cd wecms-api
cp .env.local.example .env.local     # .env.local is gitignored
# then set WECMS_DB_PASSWORD in .env.local
```

`.env.local.example` carries **68 variables**, each already holding the value the profile needs —
`WECMS_DB_URL`, `WECMS_DB_USERNAME`, a laptop-only `WECMS_JWT_SECRET`, `HAENGSUNG_ORACLE_ENABLED=false`,
and the rest. In practice **`WECMS_DB_PASSWORD` is the only one you must fill in.**

> **Why the file is mandatory.** Since the 2026-07-30 bare-placeholder migration
> ([`deployment/config-bare-placeholder-migration.md`](deployment/config-bare-placeholder-migration.md)),
> every placeholder in `application.yml` / `application-local.yml` is a bare `${VAR}` with **no yml
> default**. Gradle reads no `.env` file on its own and the project has no dotenv dependency, so
> `build.gradle` explicitly loads `.env.local` and feeds it to `bootRun`
> ([`build.gradle:100-134`](../build.gradle)).

### ⚠️ Failure mode 1 — the misleading Oracle error

```
Could not resolve placeholder 'HAENGSUNG_ORACLE_ENABLED'
```

**This is a missing `.env.local`, not an Oracle problem.** It surfaces on that particular variable only
because `HaengsungOracleDataSourceConfig`'s `@ConditionalOnProperty` is the first placeholder Spring
touches during component scan. `build.gradle` now fails fast with a clear message instead, but the raw
error still appears if you launch outside `bootRun`. **Fix: create `.env.local`.**

### ⚠️ Failure mode 2 — a String placeholder that fails silently

`@ConfigurationProperties` binds **leniently**: a missing variable leaves the literal text `"${VAR}"` as
the value. A `boolean`/`int` target then fails the bind and names the variable — but a **`String`**
target produces no error and surfaces later as something unrelated. The known example: a missing
`WECMS_JWT_SECRET` reported *"must be at least 32 bytes (current length: 19)"* — 19 being the length of
the literal string `"${WECMS_JWT_SECRET}"`. **So diff your `.env.local` against `.env.local.example`
before debugging anything else.**

## Step 2 — run

```bash
./gradlew bootRun --args='--spring.profiles.active=local'   # loads .env.local via build.gradle
./gradlew build                                             # compile + unit tests
./gradlew test                                              # tests only
```

Convenience wrappers `run-local.bat` / `run-local.sh` pin `JAVA_HOME` to Temurin 21 and pre-flight a few
checks before delegating to `bootRun`. **They are not equivalent — prefer `gradlew bootRun` directly, or
`run-local.bat` on Windows.**

> ✅ **`run-local.bat` is current.** It requires `.env.local` and fails fast with the right message, and
> it derives the DB reachability check from `WECMS_DB_URL` in your `.env.local`.
>
> ⚠️ **`run-local.sh` is stale in two ways — one of them will stop you.**
>
> 1. **It fails on a correctly-configured machine.** Its DB pre-flight hard-codes `localhost:5432` and
>    `exit 1`s when nothing answers there. That address is **neither** the canonical target
>    (`172.168.5.172:5432`) **nor** the documented fallback (`localhost:5434`), so following this guide
>    exactly and then running `./run-local.sh` yields
>    `[FAIL] Local PostgreSQL at localhost:5432 is not reachable` even though the app would have
>    started. Use `./gradlew bootRun --args='--spring.profiles.active=local'` instead.
> 2. **Two of its messages are false.** It says *"application-local.yml has safe local defaults"* and
>    prints *"using default from application-local.yml (password: 1234)"*. Neither has been true since
>    the 2026-07-30 bare-placeholder migration removed every yml default. It also has no `.env.local`
>    guard of its own — you get the `build.gradle` failure instead, which is clear but comes later.
>
> Fixing the script is a build-tooling change, out of scope for a documentation pass — reported for
> developer action.

Default profile is `local` and the server listens on **port 8080**
(`application.yml`). The Next.js frontend expects it there; `WECMS_CORS_ALLOWED_ORIGINS` already
includes `http://localhost:3000`.

## Step 3 — schema and data

**Do not expect the application to create or migrate anything.**

- **Flyway is disabled** and `spring.jpa.hibernate.ddl-auto` is `validate` in every profile. The schema
  is external and DBA-applied — see
  [`references/database/flyway-migration-baseline.md`](references/database/flyway-migration-baseline.md)
  **before citing any `V<n>__*.sql` number**; the migration set was rebaselined 2026-06-09 and old
  numbers do not identify the same file today.
- **No startup bootstrap.** The `DataSeeder` / `BootstrapRunner` path was removed 2026-06-10. Accounts
  are created by manual DBA SQL — see
  [`references/database/manual-tenant-setup.md`](references/database/manual-tenant-setup.md).
- Because `ddl-auto` is `validate`, **an entity that does not match the live schema fails startup**.
  That is intentional: it is the guard that keeps the refactor column-for-column with legacy JEX.

## Step 4 — confirm it works

| Check | Expected |
|---|---|
| `GET http://localhost:8080/actuator/health` | `{"status":"UP"}` — public, no JWT |
| `POST http://localhost:8080/api/v1/auth/login` | `{ status, data }` envelope with an `access_token` |
| Startup log | No `Could not resolve placeholder` lines; Hibernate `validate` passes |

Every endpoint outside `/api/v1/auth/login`, `/api/v1/auth/logout`, and `/actuator/health` requires a
JWT and returns 401 without one.

## Secrets — the rules

- **Never commit a real secret.** `.env.local` and `src/main/resources/application-local.yml` are
  gitignored; the `.env.*.example` files hold shapes and laptop-only values.
- The `WECMS_JWT_SECRET` in `.env.local.example` is explicitly **laptop-only**. SIT / UAT / PROD each
  set their own unique value and the app fails fast when it is missing.
- Required non-local secrets are inventoried in
  [`security/required-environment-secrets.md`](security/required-environment-secrets.md).

## Where to go next

| Goal | Doc |
|---|---|
| The project rules (naming, what not to touch, DB policy) | [`../CLAUDE.md`](../CLAUDE.md) — read first |
| How the docs are organised | [`README.md`](README.md) → **Start Here** |
| Work on a feature | [`features/`](features/) → your module → screen |
| Understand a config variable | [`deployment/wecms-batch-environment-variables.md`](deployment/wecms-batch-environment-variables.md) · [`deployment/batch-dispatch-variable-reference.md`](deployment/batch-dispatch-variable-reference.md) |
| Deploy anywhere but a laptop | [`deployment/README.md`](deployment/README.md) |
| VPN / DB credentials / server access | [`docs/INFRASTRUCTURE_ACCESS.md`](../../docs/INFRASTRUCTURE_ACCESS.md) (workspace) |
