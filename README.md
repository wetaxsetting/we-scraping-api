# WeCMS API

Spring Boot backend for **WeCMS** (WebCash Management System) — the enterprise cash-management
platform by Webcash Global for mid/large companies in Vietnam and Southeast Asia (integrated
bank / tax / ERP / card / e-invoice connections, real-time cash-flow monitoring, role-based
approval workflows, multi-tenant, multi-language).

`wecms-api` is the **backend half of the JEX → Spring Boot + Next.js refactor**. It renders no UI:
the Next.js client [`wecms-web`](#related-repository) consumes its APIs over HTTP. This repository
holds the modern backend code for **both** the main WeCMS API responsibilities **and** the migrated
WeCMS Batch responsibilities — see the section below before anything else.

> **You do not need JEX knowledge to start.** JEX is the legacy *functional* reference — it says what
> a feature must do. This Spring Boot code is the *implementation* source of truth. Full onboarding
> path: [`docs/README.md`](docs/README.md) § **Start Here**.

## WeCMS API and Batch Architecture

### Legacy

Historically WeCMS was **two separately deployed JEX applications**:

```text
Legacy WeCMS                     Legacy WeCMS Batch
- main application / API         - separate backend WAR
- user-facing backend            - NO user-facing frontend
- some in-process scheduled      - scheduled / background processing
  jobs                           - VAN / EFS / payment dispatch processing
```

They ran on their own WAR deployments and containers, shared the same business database, and
integrated over specific verified channels — primarily **shared database queue state**, plus a
direct call from WeCMS into Batch for one online inquiry and inbound gateway callbacks into Batch.
Batch never called WeCMS, and users never reached Batch.

### Current

**`wecms-api` is one Spring Boot codebase.** Verified from source:

- one Git repository, one source tree
- one Gradle project — `settings.gradle` declares no sub-projects
- one `@SpringBootApplication` entry point (`WecmsApiMainApplication`)
- one WAR build artifact
- **no `wecms-api-batch` Gradle subproject, no separate Batch source set, no separate Batch
  application entry point**

`wecms-api-batch` / `wecms-batch` is an **operational runtime role**, not an independent source
project — the name appears nowhere in this repository. Batch responsibilities are ordinary code in
the domain packages, activated by configuration; every batch job ships **disabled by default**.

Which responsibilities a given running instance performs is therefore decided by **its
configuration** at startup. Whether the operations team runs one instance doing both, or dedicated
API and batch instances, is a deployment choice managed outside this repository — see
[Deployment](#deployment).

### Legacy → Current

```text
Legacy
======

wecms-web
    |
    v
Legacy WeCMS ----------------+
                             |
                             v
                         Shared DB
                             ^
                             |
Legacy WeCMS Batch ----------+
  - scheduled jobs
  - VAN/EFS processing
  - backend only

                 Refactoring
                     |
                     v
Current
=======

wecms-web
    |
    v
               wecms-api codebase
                     |
              one Spring Boot WAR
                     |
          +----------+----------+
          |                     |
          v                     v
     API runtime role      Batch runtime role
```

### Runtime components

| Component | Role | Frontend | Notes |
|---|---|---|---|
| `wecms-web` | User-facing frontend | — | Next.js application, separate repository |
| `wecms-api` | Main backend / API runtime | `wecms-web` | Spring Boot |
| `wecms-api-batch` / `wecms-batch` | Batch runtime role | **None** | Uses the same Spring Boot codebase / artifact |

`wecms-api-batch` is **not** a separate Gradle project, module, or source set. Batch exposes no
user-facing screen; its only HTTP surface is the inbound dispatch-callback endpoints that the
bank/VAN gateway calls.

### Legacy responsibility mapping

| Legacy responsibility | Current approach |
|---|---|
| Payment dispatch processing | Spring Boot payment dispatch services (`domain/payment/dispatch/`) |
| VAN / EFS callbacks | REST-based callback handling (`/api/v1/payment/dispatch/callbacks/**`) |
| Scheduled result checking | Spring scheduler with distributed locking |
| Recipient / account-holder lookup | In-process backend service (was a cross-application call) |
| Exchange-rate processing | Spring scheduled services (`domain/banking/service/`) |
| ERP background processing | Migrated scheduled services (`domain/payment/haengsung/`) |
| Tax-authority operation status | Spring scheduled service (`domain/operation/`) |

Three of the legacy every-minute polling batches were re-implemented as **event-driven
after-commit processing** (dispatch is triggered when a payment commits, not by a poll), with a
bounded scheduled recovery sweep kept as a safety net. The exact legacy-program-to-class mapping,
per-job classifications, schedule comparisons, and concurrency findings are in the parity audit:
[`docs/audits/legacy-parity/scheduler-legacy-parity-audit.md`](docs/audits/legacy-parity/scheduler-legacy-parity-audit.md).

### Batch execution safety

Scheduled processing is protected by several mechanisms working together:

- **Activation gating** — every job is off by default. Some are gated at bean level (the bean is not
  created, so the schedule is never registered); others by a cron property that defaults to Spring's
  never-fire marker (`-`). Event-driven dispatch hooks have their own flags, also off by default.
- **ShedLock, backed by the database** — a distributed lock (`shedlock` table, using the database
  clock and a bounded lock duration) so two Spring instances cannot run the same job concurrently.
- **Controlled scheduling pool** — a single scheduler thread, so no two jobs overlap in one JVM.
- **Atomic status claiming** — the money-movement paths atomically claim a row's processing status
  before any file build, upload, or gateway call, so a second driver claims nothing and no-ops.

> **The legacy JEX Batch runtime does not participate in the Spring ShedLock mechanism.** Legacy and
> refactored schedulers must therefore not be allowed to process the same workload concurrently
> unless coexistence has been explicitly designed for that job.
>
> Detailed legacy scheduler parity and known migration/open questions are maintained in the
> [legacy-parity audit](docs/audits/legacy-parity/scheduler-legacy-parity-audit.md) — review it,
> including its concurrency findings, before changing Batch behaviour. Enabling any batch flag beyond
> a laptop additionally requires the
> [activation runbook](docs/deployment/wecms-batch-activation-runbook.md) and the
> [coexistence gate](docs/deployment/wecms-batch-activation-and-external-confirmations.md).

## Tech Stack

| Layer | Choice |
|---|---|
| Runtime | **Java 21** (Gradle toolchain pins `JavaLanguageVersion.of(21)`) |
| Framework | **Spring Boot 3.5.13** (`io.spring.dependency-management` 1.1.7) |
| Build | **Gradle 8.14.4** (wrapper, Groovy DSL) — `war` packaging |
| Web | `spring-boot-starter-web` (external Tomcat; embedded Tomcat is `providedRuntime`) |
| Persistence | `spring-boot-starter-data-jpa` + Hibernate · **PostgreSQL** driver |
| Security | `spring-boot-starter-security` + **jjwt 0.12.6** (HS256 / HS512) |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| Monitoring | `spring-boot-starter-actuator` — `health` + `info` only |
| Excel | **Apache POI 5.2.5** (`poi-ooxml`, SXSSF streaming) — the single XLSX producer |
| PDF | **OpenPDF 1.3.35** — VAT Declaration A4 export |
| SFTP | **JSch** (mwiede fork) 0.2.21 — bulk-file dispatch channel |
| Scheduling lock | **ShedLock 5.16.0** (`spring` + `provider-jdbc-template`) over PostgreSQL |
| Secondary DB | **Oracle `ojdbc11` 23.4.0.24.05** (`runtimeOnly`) — optional Haengsung ERP datasource |
| Vendor lib | `libs/isas1.0.jar` — licensed Coocon SEED-CBC cipher |
| Boilerplate | Lombok · `spring-boot-devtools` |
| Tests | JUnit 5 · `spring-boot-starter-test` · `spring-security-test` · Mockito |

**Deliberately absent — do not assume otherwise:** SpringDoc / Swagger, Testcontainers, Redis,
Flyway (see [Database](#database)), Docker, and **Spring Batch** (batch work is plain `@Scheduled`
methods plus event-driven hooks — no `Job` / `Step` / `JobRepository`).

Coordinates: `com.kosign.wecms` / `wecms-api` / `0.0.1-SNAPSHOT` ·
entry point `com.kosign.wecms.WecmsApiMainApplication`.

## Prerequisites

| Need | Detail |
|---|---|
| **JDK 21** | Eclipse Temurin recommended. Verify with `java -version`. |
| **Gradle** | None to install — use the bundled wrapper (`./gradlew`, `gradlew.bat`). |
| **PostgreSQL access** | The shared dev DB `172.168.5.172:5432/wecmsdevdb` (user `wecmsdev_owner`) — **VPN / office network required**. A developer-local `localhost:5434/wecms_dev` is a documented fallback only. |
| **DB password** | Not in this repository. Obtain it per the workspace `docs/INFRASTRUCTURE_ACCESS.md`. |
| **`.env.local`** | Mandatory — see [Environment Configuration](#environment-configuration). The application cannot start without it. |

The schema is **not** created by the application — see [Database](#database).

## Getting Started

```bash
# 1. Clone
git clone https://git.kosign.dev/wecms/wecms-api.git

# 2. Enter the project
cd wecms-api

# 3. Dependencies — resolved by the wrapper, nothing to install by hand
./gradlew --version

# 4. Configure: create .env.local from the committed template
cp .env.local.example .env.local
#    then set WECMS_DB_PASSWORD in .env.local (in practice the only value you must fill in)

# 5. Run
./gradlew bootRun --args='--spring.profiles.active=local'

# 6. Verify
curl http://localhost:8080/actuator/health     # -> {"status":"UP"}
```

On Windows use `gradlew.bat`, or the `run-local.bat` wrapper (pins `JAVA_HOME` to Temurin 21,
requires `.env.local`, pre-flights DB reachability, then delegates to `bootRun`).

> ⚠️ **`run-local.sh` is stale** and fails on a correctly configured machine (its DB pre-flight
> hard-codes `localhost:5432`, and two of its messages describe yml defaults that no longer exist).
> Use `./gradlew bootRun --args='--spring.profiles.active=local'` instead —
> [`docs/local-setup.md`](docs/local-setup.md) § Step 2.

**A fresh clone also needs one file that has no template:** `src/main/resources/application-local.yml`
is gitignored and *not* generated — obtain it from a team member (see
`docs/INFRASTRUCTURE_ACCESS.md`). The authoritative start-to-finish walkthrough, including the two
misleading first-run failures, is [`docs/local-setup.md`](docs/local-setup.md) — read it before
debugging a startup problem.

## Environment Configuration

### Spring profiles

Four profiles exist on disk; `local` is the default (`spring.profiles.active: local`).

| Profile | File | Notes |
|---|---|---|
| *(base)* | `application.yml` | Always active. Declares JPA, messages, `server.port: 8080`, actuator, JWT / CORS / crypto, gateways, batch blocks. **`uat` and `prod` do not re-declare the JWT / CORS / crypto blocks — they inherit them from here.** |
| `local` | `application-local.yml` | **Gitignored, no template** — obtain from the team. Callback protection ships `DISABLED`. |
| `sit` | `application-sit.yml` | Current deployment target. |
| `uat` | `application-uat.yml` | Conditionally ready. |
| `prod` | `application-prod.yml` | Reserved; blocked on the gates in [`CLAUDE.md`](CLAUDE.md) § "Security & SIT Readiness Status". |

There is **no `application-dev.yml`, no `application-test.yml`, and no `src/test/resources`** — none
is needed, because no test boots a Spring context (see [Testing](#testing)).

### The configuration rule: bare `${VAR}`, no yml defaults

Since the 2026-07-30 bare-placeholder migration, **every placeholder in every yml file is a bare
`${VAR}` with no default.** Values live only in the `.env` file for the active profile:

```text
application.yml        <-  .env
application-local.yml  <-  .env.local
application-sit.yml    <-  .env.sit
application-uat.yml    <-  .env.uat
application-prod.yml   <-  .env.prod
```

The real `.env.*` files are gitignored; the committed `.env.*.example` templates are the
authoritative checklists — **if a variable is not in the example, no yml reads it.**

| Template | Variables | Purpose |
|---|---|---|
| [`.env.local.example`](.env.local.example) | 68 | Developer laptop (the one profile defaulting callback protection to `DISABLED`) |
| [`.env.sit.example`](.env.sit.example) | 72 | SIT |
| [`.env.uat.example`](.env.uat.example) | 72 | UAT |
| [`.env.prod.example`](.env.prod.example) | 68 | PROD |
| [`.env.example`](.env.example) | 59 | Base-profile shape |

**Do not put a literal value in a yml file to "make it work" — put it in the `.env`.**

### Variable groups

API and Batch share one configuration mechanism and one `.env` per profile: there is no separate
batch config file, and no variable selects an "API mode" or "batch mode". Only *which flags are set*
differs. The **Scope** column says which responsibilities need each group.

| Group | Scope | Variables | Notes |
|---|---|---|---|
| **Database** | Both | `WECMS_DB_URL`, `WECMS_DB_USERNAME`, `WECMS_DB_PASSWORD` | Required in every profile. One datasource serves both. |
| **Security** | Both | `WECMS_JWT_SECRET`, `WECMS_CORS_ALLOWED_ORIGINS`, `WECMS_CRYPTO_LEGACY_CERT_SEED` | JWT secret must be ≥ 32 bytes (≥ 64 for HS512) — fails fast otherwise. `WECMS_CRYPTO_LEGACY_CERT_SEED` is a **cross-system constant**, not a tunable: it must equal the legacy pgcrypto seed or certificate data written by one system is unreadable by the other. |
| **File storage roots** (10) | Both | `WECMS_*_STORAGE_ROOT` (overseas / vendor / expense payment, payroll, contacts, preferences, user image, ERP transaction history, card transactions, internal transfer) | Uploads are an API concern; the dispatch file build uses the same roots. |
| **Coocon / NXiSAS** | Both | `WECMS_COOCON_API_KEY`, `WECMS_COOCON_API_URL`, `WECMS_COOCON_SMART_API_KEY`, `WECMS_COOCON_SMART_API_URL`, `WECMS_COOCON_UNIQUE_KEY` | Interactive bank scraping (API) and FX scraping (batch) share one gateway. |
| **OSMC** | Both | `WECMS_OSMC_URL`, `WECMS_OSMC_API_KEY`, `WECMS_OSMC_API_ID`, `WECMS_OSMC_UNIQUE_KEY` | Interactive + scheduled operation-status calls. |
| **Firm-banking dispatch** | **Batch only** | `WECMS_DISPATCH_*` (flags, crons, batch sizes) | All **off** by default. `AUTO_RECEIVE` / `REALTIME_DISPATCH` gate the event-driven hooks; `RESULT_CHECK_*` / `RECOVERY_*` / `STUCK_MONITOR_*` gate the scheduled jobs; `ALERT_ENABLED` gates ops alerts. |
| **Dispatch callbacks** | Batch only (inbound) | `WECMS_CALLBACK_*` (protection mode, forwarded-for, trusted proxies, allowed IPs) | Protects the bank/VAN callback endpoints. `ENFORCE` in sit / uat / prod, `DISABLED` in local. |
| **EFS VAN gateway** · **SFTP** | Batch only | `WECMS_EFS_ENABLED`, `WECMS_EFS_BASE_URL` · `WECMS_SFTP_*` (host, port, credentials, 5 paths) | Both default `false`; mock clients are used when disabled. |
| **FX + operation-status batches** | Batch only | `WECMS_EXCHANGE_RATE_BATCH_ENABLED`, `WECMS_OPERATION_STATUS_BATCH_{ENABLED,CRON}` | Bean-level gates — with the flag off the cron is never registered. The operation-status cron ships never-fire because the legacy firing instant is unresolved. |
| **Haengsung ERP** | Batch only *(Oracle credentials: both)* | `HAENGSUNG_ORACLE_{ENABLED,URL,USERNAME,PASSWORD}`, `HAENGSUNG_{SCHEDULED_IMPORT,EXCHANGE_RATE_SYNC,CARD_ACCOUNT_SYNC}_{ENABLED,CRON}` | ⚠️ **`url` / `username` / `password` are required even when `enabled=false`** — they are bare `${VAR}`, so resolution runs regardless of the flag and startup aborts without them. Dummy values are fine when disabled. The sync jobs also no-op unless `HAENGSUNG_ORACLE_ENABLED=true`. |

Use safe placeholders when documenting or sharing configuration:

```text
WECMS_DB_PASSWORD=<your-password>
WECMS_JWT_SECRET=<at-least-32-bytes-of-random-text>
HAENGSUNG_ORACLE_PASSWORD=<oracle-password>
```

> ⚠️ **A missing variable does not always fail loudly.** `@ConfigurationProperties` binds leniently,
> so an unresolvable placeholder leaves the literal `"${VAR}"` as the value: a `boolean` / `int`
> target fails the bind *and names the variable*, but a **`String`** target produces **no error** and
> surfaces later as something unrelated. **Diff your `.env` against the matching `.env.*.example`
> before debugging anything else** — see [Troubleshooting](#troubleshooting) for the two symptoms
> this actually produces.

### Cron values and secrets

`"-"` is Spring's never-fire marker and the only safe way to disable a scheduled job. An **empty** or
invalid cron throws at scheduler *initialisation*, so the application will not start — never blank a
cron, set it to `"-"`.

Never commit a real secret. `.env`, `.env.*` (except `*.example`) and `application-local.yml` are
gitignored. The `WECMS_JWT_SECRET` in `.env.local.example` is **laptop-only**; each environment sets
its own. Required non-local secrets:
[`docs/security/required-environment-secrets.md`](docs/security/required-environment-secrets.md).

**Deployed environments override the committed `.env`** (see [Deployment](#deployment)). Which batch
flags are actually set on a running SIT / UAT host is host-owned and cannot be read from this
repository — treat any such claim as needing confirmation from Ops.

## Running Locally

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

- Listens on **port 8080** (declared once, in `application.yml`; no profile overrides it).
- The `bootRun` task reads `.env.local` and passes it to the JVM as environment variables — wired
  explicitly in [`build.gradle`](build.gradle), because Gradle reads no `.env` on its own. It fails
  fast if the file is missing. `test` is unaffected.
- Nothing is migrated or seeded at startup (see [Database](#database)).

### Running Web + API together

Start this API on `:8080` as above, then in `wecms-web` set
`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` in its `.env.local` and run `npm run dev`
(serves `:3000`). CORS already permits it — `.env.local.example` ships `http://localhost:3000` in
`WECMS_CORS_ALLOWED_ORIGINS`. The frontend needs a reachable backend to get past `/login`: its auth
gate is server-side and the dashboard rehydrates the session from `GET /api/v1/auth/me`.

### Enabling batch behaviour locally

**There is no second command and no second process to start.** The batch jobs are in the application
you already started; they are inactive because every batch flag defaults to off (see
[Batch execution safety](#batch-execution-safety)). To exercise one, set its flags in `.env.local`
and restart the same command:

```text
# .env.local — example: enable ONLY the firm-banking result-check job
WECMS_DISPATCH_RESULT_CHECK_ENABLED=true
WECMS_DISPATCH_RESULT_CHECK_CRON=0 */1 * * * *     # "-" = never fire (the default)
```

- **Batch is not required for frontend development.** Every screen is served by the REST APIs; with
  all batch flags off the app is a pure API server and `wecms-web` is fully usable.
- Enable one job at a time and check its gates — several need a second flag (Haengsung jobs require
  `HAENGSUNG_ORACLE_ENABLED=true`; the recovery sweep's result-check leg also requires
  `WECMS_DISPATCH_RESULT_CHECK_ENABLED=true`). Details: [Variable groups](#variable-groups).
- ⚠️ **Enabling a dispatch or ERP flag can cause real external calls** (EFS, SFTP, Oracle) and real
  money movement against whatever database the profile points at. Read the
  [activation runbook](docs/deployment/wecms-batch-activation-runbook.md) and the
  [coexistence gate](docs/deployment/wecms-batch-activation-and-external-confirmations.md) first.

## Available Commands

```bash
./gradlew build     # compile + run unit tests + assemble the WAR
./gradlew test      # tests only
./gradlew bootRun   # run the app (pass --args='--spring.profiles.active=<profile>')
./gradlew clean     # remove build/
./gradlew tasks     # full task list
```

Convenience wrappers: `run-local.bat` (Windows, current) and `run-local.sh` (POSIX, **stale** — see
[Getting Started](#getting-started)).

## Project Structure

```text
wecms-api/
├── build.gradle · settings.gradle    # single-project build; bootRun .env.local loader
├── gradlew · gradlew.bat             # Gradle 8.14.4 wrapper
├── run-local.bat · run-local.sh      # local launch wrappers (see note above)
├── libs/isas1.0.jar                  # licensed Coocon SEED-CBC cipher
├── src/main/
│   ├── java/com/kosign/wecms/
│   │   ├── WecmsApiMainApplication.java     # the single entry point
│   │   ├── config/                   # security, CORS, Jackson, JWT, scheduler lock, time
│   │   ├── common/                   # cross-cutting: security · tenant · response · exception
│   │   │                             #   i18n · export (POI) · auditlog · legacy · lock · file
│   │   └── domain/                   # feature modules
│   │       ├── auth/  navigation/    #   login/JWT/users · product-scoped menu tree
│   │       ├── banking/              #   accounts, transactions, transfers, reports, scraping
│   │       │   └── service/          #     ← BATCH: exchange-rate scheduling
│   │       ├── payment/              #   5 payment modules, approval box, cards
│   │       │   ├── dispatch/         #     ← BATCH: firm banking (legacy WeCMS Batch)
│   │       │   └── haengsung/        #     ← BATCH: ERP sync
│   │       ├── operation/scheduler/  #   ← BATCH: operation-status scheduling
│   │       ├── setting/              #   contacts, approval line, chart of accounts, view setting
│   │       ├── einvoice/             #   sales / expense e-invoices
│   │       ├── vatdeclaration/       #   VAT declaration + PDF export
│   │       └── common/               #   shared reference data (countries, …)
│   └── resources/
│       ├── application.yml + application-{local,sit,uat,prod}.yml
│       ├── messages.properties + messages_{en,ko,vi,km}.properties
│       ├── db/migration/             # 165 .sql files — DBA-run, NOT Flyway (see Database)
│       └── templates/                # Excel / report templates
├── src/test/java/                    # JUnit 5 + Mockito suites (batch tests included)
└── docs/                             # backend documentation root — see docs/README.md
```

Rough scale: 48 controllers · 129 services · 123 repositories · 103 entities · 15 classes with
`@Scheduled` methods. Layering is **controller → service → repository → entity + DTO**; no
controller-to-controller calls and no cross-domain repository access.

There is **no `batch/` top-level package and no separate batch source set** — the rows marked
`← BATCH` are the migrated WeCMS Batch responsibilities, sitting beside the API code they relate to.
Full architecture rules: [`CLAUDE.md`](CLAUDE.md) § "Architecture Rules".

## Application Architecture

```text
HTTP request
   │
   ├─ TraceIdFilter              stamps a trace id into MDC + the X-Trace-Id header
   ├─ JwtAuthenticationFilter    parses the Bearer token, checks revocation,
   │                             sets authorities and populates TenantContext
   ▼
Controller  (thin: bind + validate, delegate, wrap in ApiResponse)
   ▼
Service     (@Transactional, business rules, throws BusinessException)
   ▼
Repository  (Spring Data JPA — every tenant-scoped query constrains on BIZ_KEY)
   ▼
PostgreSQL  (legacy JEX schema, mapped column-for-column)
```

Cross-cutting: a single `@RestControllerAdvice` error funnel · localized `WBExxxx` codes · audit-log
writer · one POI-based XLSX producer · ShedLock around scheduled jobs.

**Multi-tenancy.** Tenant (`BIZ_KEY`) is resolved once at login and carried in signed JWT claims;
`TenantContext` exposes it per request. `BIZ_KEY` and product type are **never** read from a request
body, query parameter, or header outside the login endpoint. Deeper design:
[`docs/architecture/`](docs/architecture/README.md).

## API Conventions

Every business endpoint lives under `/api/v1/**`.

**Response envelope** — every JSON endpoint returns
`{ status: { error, code, message }, data }`; errors add `timestamp`, `path`, and `trace_id`.

- **Check `status.error`, not the HTTP code.** A documented business outcome returns **HTTP 200**
  with `status.error = true`. Client mistakes are 4xx; server faults are 5xx. Full mapping:
  [`CLAUDE.md`](CLAUDE.md) § "Exception → response mapping".
- `trace_id` is also returned as the `X-Trace-Id` header and appears in every log line for the
  request, so a user can quote it back to support.
- **Binary download / export endpoints are the one sanctioned exception** — they stream the file with
  an RFC 5987 `Content-Disposition` filename instead of the envelope.

**Wire format** — lowercase `snake_case`, enforced globally by a Jackson `SNAKE_CASE` strategy (Java
DTOs are records with camelCase fields). External Coocon / NXiSAS gateway DTOs keep **vendor casing**
and are exempt. REST URIs use kebab-case slugs, never legacy JEX WSVC ids. Query params are lowercase
snake_case: pagination `page` (zero-based) + `size`, sorting `sort_field` + `sort_direction` resolved
through a per-domain whitelist enum and never concatenated into SQL. Some earlier endpoints carry
grandfathered camelCase params — see [`CLAUDE.md`](CLAUDE.md) § "REST API Design Conventions".

**Authentication** — `Authorization: Bearer <jwt>`. Public paths: `POST /api/v1/auth/login`,
`POST /api/v1/auth/logout`, `GET /actuator/health` (the only unauthenticated actuator path), and
`/api/v1/payment/dispatch/callbacks/**`. Everything else requires a valid JWT. Sessions are
stateless; logout revokes the token's `jti` via an **in-memory** store, so revocations do not survive
a restart and are not shared across instances.

> **Pre-deploy security gates** (do not treat as production-ready): the dispatch callback endpoints
> are unauthenticated and must be restricted by network / mTLS / shared secret before a non-local
> deploy, and per-action authorization is not yet enforced — [`CLAUDE.md`](CLAUDE.md) § "Security &
> SIT Readiness Status" · [`docs/security/README.md`](docs/security/README.md).

**Error codes** — `WBIxxxx` (success) / `WBExxxx` (error), localized from `Accept-Language`
(EN / KO / VI / KM). Catalogue: [`docs/error-codes.md`](docs/error-codes.md). Endpoint contracts:
[`docs/features/`](docs/features/) · [`docs/references/api-spec/`](docs/references/api-spec/) ·
[`docs/architecture/api-catalog/`](docs/architecture/api-catalog/README.md).

## Database

**Engine:** PostgreSQL. **The schema is external and DBA-applied — the application never creates or
migrates it.**

- **Flyway is not a dependency of this project** and is configured nowhere. The 165 `.sql` files
  under `src/main/resources/db/migration/` are **run manually by a DBA**. Migration numbers were
  rebaselined 2026-06-09, so read
  [`flyway-migration-baseline.md`](docs/references/database/flyway-migration-baseline.md) **before
  citing any `V<n>__*.sql` number** — old numbers no longer identify the same file.
- `spring.jpa.hibernate.ddl-auto` is **`validate`** in every profile. An entity that does not match
  the live schema **fails startup** — intentional, and the guard that keeps the refactor
  column-for-column with legacy JEX.
- **No startup bootstrap.** Accounts and tenants are created by manual DBA SQL
  ([`manual-tenant-setup.md`](docs/references/database/manual-tenant-setup.md)); the `shedlock` table
  is likewise created by hand.
- **Table baseline:** [`legacy-table-inventory.md`](docs/references/database/legacy-table-inventory.md)
  (141 tables) is authoritative — verify any new entity or repository against it first. Names stay
  `UPPER_SNAKE`, mirroring legacy JEX so data migrates with little transformation.
- **Key generation** funnels through the legacy DB function `wb_get_next_val` via a single executor.
  Do not call it elsewhere and do not invent a new ID scheme — [`CLAUDE.md`](CLAUDE.md) § "DB
  Function / Legacy Function Policy".

Local connection comes entirely from `.env.local` (`WECMS_DB_URL` / `WECMS_DB_USERNAME` /
`WECMS_DB_PASSWORD`). Never commit credentials.

## Testing

```bash
./gradlew test                                   # whole suite
./gradlew test --tests '*AuthServiceTest'        # one class
./gradlew build                                  # compile + tests + WAR
```

- **Framework:** JUnit 5 via `spring-boot-starter-test`, with `spring-security-test` available and
  Mockito for unit-level isolation.
- **No test boots a Spring application context.** Exactly one class is annotated `@SpringBootTest`
  and it is `@Disabled` at class level, pending shared integration-test infrastructure. That is why
  the suite needs no `application-test.yml`, no `.env`, and no database.
- **No Testcontainers and no in-memory DB harness** — do not write a test that needs a database until
  that infrastructure lands.
- **Batch tests are part of the same suite** — there is no separate batch test task or source set.
  `./gradlew test` covers the dispatch, Haengsung, and per-scheduler tests, plus contract tests that
  assert the cron-and-lock wiring itself, so a job cannot silently lose its lock or become enabled by
  default.
- An authorization coverage guard fails the build on any un-annotated, non-allow-listed endpoint
  handler, so a new controller cannot ship without an authorization decision.
- `wip-quarantine/payment-service-tests/` holds work-in-progress tests deliberately kept out of the
  compiled source set.

**Current state — green.** Verified 2026-08-17: `./gradlew test` → `BUILD SUCCESSFUL`,
**2,733 tests across 325 classes, 0 failures, 0 errors, 20 skipped.**

Test plans and fixture conventions: [`docs/testing/`](docs/testing/README.md).

## Build

```bash
./gradlew clean build
```

**One Gradle build produces the application artifact used by the current backend runtime roles.**
`settings.gradle` declares no sub-projects and there is no second `bootWar` / `bootJar` task, so the
build cannot produce a separate batch artifact — a batch-role instance runs this same artifact with
batch flags enabled.

Output in `build/libs/`:

| Artifact | Use |
|---|---|
| `wecms-api-0.0.1-SNAPSHOT.war` | The deployable — a Spring Boot WAR for an **external** Tomcat |
| `wecms-api-0.0.1-SNAPSHOT-plain.war` | Plain (non-executable) WAR from the `war` plugin |

The `war` plugin plus `providedRuntime 'spring-boot-starter-tomcat'` deliberately **excludes**
embedded Tomcat — the servlet container is supplied by the host.

## Deployment

**Deployment is managed outside this repository.**

**Legacy:** WeCMS and WeCMS Batch were **separate JEX WAR deployments** on separate containers, with
the batch scheduler switched on per container by a configuration flag.

**Current:** **one Spring Boot codebase and artifact supports the backend runtime roles.** Which
responsibilities an instance performs follows from its configuration. The actual host/process
topology — how many instances run, and whether any is dedicated to batch — is managed by deployment
infrastructure and is **not fully defined by this repository**; confirm it with Ops rather than
inferring it here.

> Do not carry the legacy two-server picture into the current architecture. Legacy had two WARs
> because that was how the code was packaged; the refactor has one artifact, and any runtime
> separation today is a deployment choice enabled by configuration.

Repository evidence: [`.gitlab-ci.yml`](.gitlab-ci.yml) is a **trigger only** — it runs on the `sit`
and `uat` branches and hands off to a downstream pipeline in another GitLab project (`$URL` / `$FILE`
CI variables). The build and deploy steps are not here, and no CI job compiles, tests, or lints —
**running `./gradlew build` locally before opening an MR is a manual responsibility.** There is **no
Dockerfile and no docker-compose**. The deployed host runs an external Tomcat; a deploy replaces
`ROOT.war`.

Per environment, what varies is the database target, the CORS origin, gateway URLs and keys, which
batch flags are on, and the cron schedules:

| Env | Profile | CORS origin (committed template) | Callback protection |
|---|---|---|---|
| LOCAL | `local` | `http://localhost:3000` (+ SIT and NXiSAS alias origins) | `DISABLED` |
| SIT | `sit` | `https://wecms.kosign.dev` | `ENFORCE` |
| UAT | `uat` | `https://test2.wecms.vn` | `ENFORCE` |
| PROD | `prod` | placeholder — not committed | `ENFORCE` |

> ⚠️ **On a deployed host the committed `.env` is not the runtime authority.** Configuration is
> exported as OS environment variables by a host-owned `setenv.sh` that is not in this repository,
> and OS environment variables override `.env` files. Never state a deployed environment's flag
> state from this repo — read it from the host. Deploying and changing configuration are separate
> events.

Run-books and checklists: [`docs/deployment/`](docs/deployment/README.md). Current posture: SIT is
the active target, UAT is conditionally ready, PROD is blocked on documented gates.

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| `Could not resolve placeholder 'HAENGSUNG_ORACLE_ENABLED'` | Missing `.env.local` — **not** an Oracle problem. `cp .env.local.example .env.local` and set `WECMS_DB_PASSWORD`. |
| `Failed to configure a DataSource: 'url' attribute is not specified` | Missing `src/main/resources/application-local.yml` (gitignored, no template). Obtain it from a team member. |
| `wecms.jwt.secret must be at least 32 bytes (current length: 19)` | `WECMS_JWT_SECRET` is unset — 19 is the length of the literal `"${WECMS_JWT_SECRET}"`. Set it in the `.env`. |
| `Failed to bind '…' to boolean — Value: "${SOME_VAR}"` | That variable is missing from the `.env`. Diff against the matching `.env.*.example`. |
| A setting appears ignored, no error anywhere | A missing **`String`** variable binds the literal `"${VAR}"` silently. Diff the whole `.env` against the example. |
| Application fails to start on a scheduler error | An empty or invalid cron. Use `"-"` (never-fire) to disable a job; never blank it. |
| DB connection refused / timeout | The shared dev DB `172.168.5.172` needs the office network or VPN. Check `WECMS_DB_URL`. |
| Hibernate `validate` fails with a schema mismatch | Intentional guard: an entity does not match the external schema. Fix the mapping — do **not** switch `ddl-auto` to `update`. |
| `Port 8080 already in use` | Stop the other process, or pass `--server.port=<free-port>` to `bootRun` (and update `NEXT_PUBLIC_API_BASE_URL` on the frontend). |
| `Unsupported class file major version` / toolchain error | Wrong JDK. This project requires **JDK 21**; check `java -version` and `JAVA_HOME`. |
| 401 on every endpoint | Expected without a JWT. Log in via `POST /api/v1/auth/login` and send `Authorization: Bearer <access_token>`. |
| Frontend blocked by CORS | Add the frontend origin to `WECMS_CORS_ALLOWED_ORIGINS` in the active `.env`. |
| A batch job never runs | Expected — batch jobs are **off by default**. Check both gates: the `*_ENABLED` flag and the cron property (defaults to `-`). See [Enabling batch behaviour locally](#enabling-batch-behaviour-locally). |
| A Haengsung sync job is enabled but logs and returns | It also needs `HAENGSUNG_ORACLE_ENABLED=true` — without the Oracle datasource the job no-ops by design. |
| A scheduled job seems to skip a fire across instances | Expected: ShedLock lets exactly one instance run each job. Inspect the `shedlock` table. |
| Two systems appear to process the same firm-banking row | ShedLock does **not** coordinate with the legacy JEX batch. Confirm the legacy batch is disabled for that environment, or that the two point at different databases — see [Batch execution safety](#batch-execution-safety). |

More: [`docs/local-setup.md`](docs/local-setup.md). A broader `TROUBLESHOOTING.md` exists at the
**multi-tree workspace root** (`../docs/TROUBLESHOOTING.md`), outside this repository — it resolves
only when the full workspace is checked out.

## Documentation

**README = developer entry point · `/docs` = detailed architecture, audits, migration history,
parity analysis.**

| Doc | Purpose |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | **Project rules — read first.** Naming, architecture, DB policy, what not to touch. (`CLAUDE.vi.md` is the Vietnamese copy.) |
| [`docs/README.md`](docs/README.md) | Documentation root + **Start Here** reading order |
| [`docs/local-setup.md`](docs/local-setup.md) | Authoritative laptop setup walkthrough |
| [`docs/features/`](docs/features/) | Per-feature specs, grouped by the six menu domains |
| [`docs/architecture/`](docs/architecture/) | Cross-feature architecture + API catalogue |
| [`docs/references/`](docs/references/) | API contracts · business rules · database · JEX→Spring mapping |
| [`docs/references/database/`](docs/references/database/) | Schema, table inventory, migration baseline, manual tenant setup |
| [`docs/testing/`](docs/testing/) | Test plans and fixture conventions |
| [`docs/security/`](docs/security/) | Closure report, risk register, required secrets, rotation handoff |
| [`docs/audits/`](docs/audits/) | Parity and governance audits (point-in-time records) |
| [`docs/planning/open-questions/`](docs/planning/open-questions/) | Live open questions — never delete an item without confirmation |
| [`docs/error-codes.md`](docs/error-codes.md) · [`docs/glossary.md`](docs/glossary.md) · [`docs/permissions.md`](docs/permissions.md) · [`docs/workflow.md`](docs/workflow.md) | Error codes · terms · roles · dev flow |

### Batch and legacy-migration documentation

| Doc | Purpose |
|---|---|
| [`docs/audits/legacy-parity/scheduler-legacy-parity-audit.md`](docs/audits/legacy-parity/scheduler-legacy-parity-audit.md) | **Per-job legacy↔refactor mapping**, classifications, schedule comparison, concurrency findings, migration gaps and open questions |
| [`docs/features/wecms-batch/`](docs/features/wecms-batch/) | Batch feature specs — the migrated WeCMS Batch responsibilities |
| [`docs/audits/batch/`](docs/audits/batch/) | Batch architecture, operating-hours, and boundary audits |
| [`docs/deployment/wecms-batch-activation-runbook.md`](docs/deployment/wecms-batch-activation-runbook.md) | **Read before enabling any batch flag outside your laptop** |
| [`docs/deployment/wecms-batch-activation-and-external-confirmations.md`](docs/deployment/wecms-batch-activation-and-external-confirmations.md) | Per-environment activation status and the legacy-coexistence gate |
| [`docs/deployment/`](docs/deployment/) | All run-books, checklists, and config/variable references |

## Related Repository

```text
Related frontend: wecms-web  —  https://git.kosign.dev/wecms/wecms-web.git
```

The two repositories are refactored **together**: a contract change (entity, DTO, endpoint, field
name, key format) has a matching change on the other side, and neither ships a change the other has
not accounted for. Keep feature slugs identical across both trees, and note the paired change in the
MR description.

## Contributing / Development Guidelines

- **Read [`CLAUDE.md`](CLAUDE.md) first.** It is the authoritative ruleset for this tree — naming,
  layering, the DB and key-generation policies, and the scope guard that keeps frontend-only tasks
  out of this repository.
- **Understand the legacy flow before implementing.** Read the corresponding JEX behaviour and state
  the impact on both API and WEB — plus the data-migration risk — before changing an entity, DTO,
  SQL, table mapping, or any legacy-compatible process.
- **Migration safety wins over cleaner design.** A change that increases structural drift from the
  legacy schema is the wrong change, however tidy it looks.
- **Review the [legacy-parity audit](docs/audits/legacy-parity/scheduler-legacy-parity-audit.md)
  before changing scheduled/batch behaviour.**
- Run `./gradlew build` locally before opening an MR — **CI does not compile or test this project.**
- New error codes go into `messages.properties` before they are thrown.
- Never commit secrets; never change an environment's configuration as a side effect of a code change.
- Branch naming is not formally documented; `main`, `sit`, and `uat` are the long-lived branches
  (`sit` / `uat` trigger the deployment pipelines). Confirm the target branch with the team lead.

Workspace-level workflow: [`docs/workflow.md`](docs/workflow.md).

## License

**Proprietary — internal project.** This repository contains no license file, and no open-source
license is granted or implied. WeCMS is a commercial product of **Webcash Global**; all rights
reserved. Use, copying, and distribution are governed by your agreement with the company — direct
any licensing question to the project owner rather than assuming permission.

---

_CI check: UAT pipeline re-run after the pipeline-script update (2026-09-30)._
