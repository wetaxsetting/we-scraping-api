# CLAUDE.md — wecms-api-main (Spring Boot Refactor Target)

Spring Boot backend for the WeCMS JEX → Spring refactor. **Status: R&D proof-of-concept** (8-day sprint, 2026-04-23 → 2026-05-06). Local-only, GitLab-only. No DEV/UAT/PROD deploy, no bank integration, no coexistence with the live JEX system.

Workspace-level layout, memory, and cross-tree rules are in the root [CLAUDE.md](../CLAUDE.md). JEX functional spec is in [WECMS DEV/CLAUDE.md](../WECMS%20DEV/CLAUDE.md). Full roadmap: [docs/features/wecms-full-refactoring-roadmap.md](../docs/features/wecms-full-refactoring-roadmap.md) and [docs/features/wecms-menu-refactoring-timeline.md](../docs/features/wecms-menu-refactoring-timeline.md).

## Architecture — Domain-Driven (Lightweight)

Per roadmap Slide 05: **single-module Gradle project, domain-driven package structure**. This is *package-by-domain* / lightweight DDD — **NOT** full tactical DDD (no aggregates, value objects, or domain events unless a specific feature demands them).

```
com.kosign.wecms
├── WecmsApiMainApplication          — @SpringBootApplication entry point
├── config/                           — Spring config (security, JWT, JPA, i18n, CORS, OpenAPI)
├── common/                           — cross-cutting building blocks
│   ├── entity/                       — BaseEntity (REG_ID, REG_DTM, MOD_ID, MOD_DTM)
│   ├── tenant/                       — TenantContext (ThreadLocal BIZ_KEY) + @BizKeyFilter
│   ├── enums/                        — LangCd, NtnlCd, YesOrNo, BusiStts + JPA AttributeConverters
│   ├── exception/                    — GlobalExceptionHandler (WBE code → HTTP status)
│   ├── i18n/                         — MessageSource for EN / KO / KM / VI
│   └── logging/                      — Logback MDC helpers (AppLogManager equivalent)
└── domain/                           — one package per business module (feature-driven)
    ├── auth/                         — logn module (login, JWT, password mgmt)
    │   ├── entity/                   — JPA entities (UserInfm, BusiInfm, ...)
    │   ├── repository/               — Spring Data JPA repos
    │   ├── service/                  — business logic (ported from JEX helpers / BCS)
    │   ├── controller/               — REST endpoints
    │   └── dto/                      — request / response DTOs
    ├── payment/                      — pymn module
    ├── member/                       — mbmr module
    └── ...                           — one package per JEX module being ported
```

**Rules:**
- New feature code goes under `domain/{module}/` — one package per JEX module code (`logn`, `pymn`, `mbmr`, `comm`, `admn`, ... — full list in [WECMS DEV/CLAUDE.md](../WECMS%20DEV/CLAUDE.md)).
- `common/` is for shared infrastructure only. If something is used by only one domain, keep it in that domain's package — don't pre-promote to `common/`.
- Entities are data carriers + JPA annotations. Business logic lives in `service/`, not in entities. Rich-entity DDD is out of scope for R&D.
- Within a domain, standard Spring layering applies: **controller → service → repository → entity + dto**.
- Cross-domain calls: service-to-service via injection. Never controller-to-controller, never cross-domain repository access.

## Stack

- **Spring Boot:** 3.5.13
- **Java:** 21 (Eclipse Temurin LTS, Gradle toolchain)
- **Build:** Gradle (Groovy DSL) — `./gradlew build`, `./gradlew bootRun`, `./gradlew test`
- **Database:** PostgreSQL (driver via `org.postgresql:postgresql`)
- **Security:** Spring Security 6 + **jjwt 0.12.x** (JWT access + refresh)
- **ORM:** Spring Data JPA + Hibernate
- **Migrations:** **Flyway** (`src/main/resources/db/migration/`)
- **API docs:** **SpringDoc OpenAPI**
- **Validation:** Jakarta Validation + Hibernate Validator
- **Lombok:** compile-only + annotation processor — trivial `@Getter`/`@Setter` only (same rule as JEX)
- **Tests:** JUnit 5 + **Testcontainers** (PostgreSQL) for integration

**Entry point:** `com.kosign.wecms.WecmsApiMainApplication`
**Group / artifact / version:** `com.kosign.wecms` / `wecms-api-main` / `0.0.1-SNAPSHOT`
**Version control:** Git — `kosign-wecms/wecms-api-main` on GitLab

Note: Flyway, jjwt, SpringDoc, Testcontainers, and the validation starter are **not yet in `build.gradle`**. Current dependencies are Web, Data JPA, Security, Lombok, DevTools, PostgreSQL, plus the default test stack. Add each dependency in the sprint where the feature first needs it.

## Application Profiles

`application-{profile}.yml` under `src/main/resources/`:

| Profile | When |
|---|---|
| `local` | Developer laptop — `localhost:5434/wecms_dev` |
| `dev` | Reserved — not used in the 8-day R&D sprint |
| `uat` | Reserved — not used in the 8-day R&D sprint |
| `prod` | Reserved — not used in the 8-day R&D sprint |

Default profile during the R&D sprint is `local`. `application.properties` currently holds only `spring.application.name=wecms-api-main`; feature config goes into profile-scoped `application-{profile}.yml`.

## Local PostgreSQL

Per [docs/features/wecms-menu-refactoring-timeline.md](../docs/features/wecms-menu-refactoring-timeline.md) and memory `reference_local_db_setup.md`:

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5434` (not 5432 — avoids conflict with other local Postgres instances) |
| Database | `wecms_dev` |
| User | `postgres` |
| JDBC URL | `jdbc:postgresql://localhost:5434/wecms_dev` |
| Driver | `org.postgresql.Driver` |

**One-time bootstrap** (each developer runs once on their local machine):

```sql
CREATE DATABASE wecms_dev OWNER postgres;
ALTER USER postgres WITH PASSWORD '<from team secure channel>';
```

**Password policy** — the `postgres` password **must NEVER** be hardcoded in any committed file (CLAUDE.md included). It lives in:
- Developer shell env var `WECMS_DB_PASSWORD`, or
- IDE run-configuration env, or
- `application-local.yml` which is `.gitignore`d.

**`application-local.yml` template (do not commit with a real password):**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/wecms_dev
    username: postgres
    password: ${WECMS_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
```

**Never** point this project at `dev.wecms.vn`, `test.wecms.vn`, or `www.wecms.vn` — use the JEX SVN / Jex Studio flow for those environments.

## Reading JEX as the Functional Spec

For each service being translated, read these JEX artifacts (read-only) to extract **field mapping and business rules** — *not* layout:

| JEX artifact | Path pattern | What to extract |
|---|---|---|
| Entity | `WECMS DEV/WECMS/src/wabooks/entity/*.java` | `@Table` name, column names and types |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/*DAO.java` | Query methods, dynamic clauses used |
| Default IDO | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.{TABLE}_R001.xml` | Full table column list (`out` entries) |
| Custom IDO | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.{TABLE}_R002.xml`+ | Complex SQL (JOIN, aggregation) |
| API contract | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.{service_id}.xml` | Input / output field IDs, RECORD sub-fields |
| Business logic | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/{module}/{id}_act.jsp` | Validation, branches, workflow |
| Frontend behavior | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/{module}/{id}.ts` | Client-side error handling, state |

Full JEX conventions — module codes, service naming, ML codes, Jex Studio rules — are in [WECMS DEV/CLAUDE.md](../WECMS%20DEV/CLAUDE.md).

## Refactor Write Boundary

- Write only inside `wecms-api-main/`.
- JEX trees (`WECMS DEV/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/`, `DEV ADMIN/`) are **read-only** reference.
- UI code lives in `wecms-web-main/` — don't cross the boundary unless the task is explicitly full-stack.

## Full-Stack Ownership

Per roadmap Slide 3b: **each developer owns both frontend AND backend for their assigned menus.** If Sonny owns Login, she writes the Spring Boot `domain/auth/` code *and* the Next.js login page. Only foundation work (`config/`, `common/`, shared UI primitives) is task-split. AI-generated matching DTOs/types land on both sides in the same commit.

## Build & Run

```bash
./gradlew build                               # compile + test
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test                                # unit + integration tests
```

## Database Access for Claude (AI)

Root rule [.claude/rules/database.md](../.claude/rules/database.md) still applies: the `postgres` MCP connects to the **JEX DEV database** only — read-only, no DDL/DML, no PROD. For the local refactor DB, use `psql` / DBeaver outside Claude Code. Schema changes for this project go through **Flyway migrations** committed to `src/main/resources/db/migration/`.
