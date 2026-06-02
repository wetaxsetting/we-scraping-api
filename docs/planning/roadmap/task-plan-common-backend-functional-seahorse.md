# Common Backend Strategy — DB Functions & First Company Account Bootstrap

**Scope:** wecms-api/ (Needs Confirmation) (Spring Boot 3.5.13, Java 21, Gradle, Flyway). Local-only R&D — no DEV/UAT/PROD deploy yet, no JEX coexistence.

**Frontend contract check only:** wecms-web/ (Needs Confirmation). No "create company" UI exists today.

---

## Context

Legacy WeCMS has no admin UI to create the very first company/account — bootstrap is **manual SQL** that inserts into `BUSI_INFM` → `EMPL_INFM` → `USER_INFM` → `USER_BUSI_CONN_INFM` → `BUSI_ADMN_INFM`, and leans on three PL/pgSQL helpers: `wb_get_next_val(...)` (ID generation, ~40 branches), `encrypt(...)` (phone, AES-ish black-box), and `hash(...)` (password, SHA-256+Base64, unsalted).

The Spring Boot refactor in `wecms-api` already has:
- JPA entities for `BUSI_INFM`, `USER_INFM`, `USER_BUSI_CONN_INFM`, `USER_LOGIN_HIS` ([entity package](../../../src/main/java/com/kosign/wecms/domain/auth/entity/)).
- Hybrid password encoder ([WecmsPasswordEncoder.java](../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java)) with a legacy SHA-256 reader ([LegacyHashPasswordEncoder.java](../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java)) that auto-upgrades on first login.
- A LOCAL-only `__BCRYPT_PENDING__` sentinel patch ([DataSeeder.java](../../../src/main/java/com/kosign/wecms/domain/auth/bootstrap/DataSeeder.java)) — does **not** create companies.
- Flyway migrations V1–V10 (V2 seeds 1 local company + 2 users; V5 imports a 218-tenant JEX DEV snapshot, idempotent on PK).

What's missing: `EMPL_INFM` and `BUSI_ADMN_INFM` entities/repos; any service that creates a tenant; any wrapper for the surviving DB functions; a non-LOCAL bootstrap mechanism.

This plan defines the safest path from "manual SQL + DB functions" → service-driven Spring bootstrap, in three phases. Phase 1 is the immediate sprint deliverable.

---

## 1. Current Legacy Flow (summary)

- **Bootstrap is JEX-only and 100% manual SQL.** Two IDOs (`WB_INIT_BUSI_DATA`, `WB_INIT_USER_DATA`) wrap the inserts; no `_act.jsp` or admin page creates the first company.
- **`wb_get_next_val(table, field, p1..p6)`** is at [WECMS DEV/WECMS_DOC/database/ddl/functions.sql:5159](../../WECMS DEV/WECMS_DOC/database/ddl/functions.sql>) — a ~40-branch PL/pgSQL switch. Three algorithm classes:
  - `BIZ_KEY` → sequence `sq_biz_key`.
  - `TRCO_KEY` → sequence `sq_trco_key`.
  - `EMPL_NO` → date-prefixed `YYYYMMDD_nnn` via row count per `(BIZ_KEY, date)`.
- **`hash(password)`** = SHA-256 + Base64, 44-char, unsalted. Already mirrored in Java via `LegacyHashPasswordEncoder`; new writes use BCrypt.
- **`encrypt(phone)`** definition is **not present** in the modern DDL dump — treat as legacy black-box. Bootstrap path itself does not touch phone fields, so it is **not blocking**.
- Single-tenant in Spring is pinned via `TenantProperties#bizKey`.

---

## 2. DB Function Migration Strategy

| Function | Legacy purpose | Strategy | Replacement | Why |
|---|---|---|---|---|
| `wb_get_next_val('BUSI_INFM','BIZ_KEY')` | Allocate next company key from `sq_biz_key` | **Phase 1 — wrap** | `LegacyFunctionRepository.nextVal("BUSI_INFM","BIZ_KEY")`, fronted by `BusinessKeyGeneratorService` | Sequence still lives in DB; safest to delegate during cut-over and keeps parity with any remaining legacy writes. |
| `wb_get_next_val('EMPL_INFM','EMPL_NO', biz_key)` | `YYYYMMDD_nnn` per `(BIZ_KEY, date)` via row count | **Phase 1 — wrap (stub) → Phase 2 — reimplement in Java** | `EmployeeNumberGeneratorService.next(bizKey, today)` using row-level locking (`SELECT ... FOR UPDATE` on a counter row, or `INSERT ... ON CONFLICT DO UPDATE RETURNING`) | Naive `count(*) + 1` is race-prone under Spring concurrency. Java implementation is testable and lockable. |
| `wb_get_next_val(...)` other ~40 branches (`TRCO_KEY` etc.) | Sequence + composite key generation | **Phase 1 — wrap; Phase 3 — retire branch-by-branch** | `LegacyFunctionRepository.nextVal(table,field,p1..p6)` initially; per-domain generator services later | Big-bang rewrite is unjustified risk. Single funnel point makes retirement a `grep`. |
| `encrypt(phone)` | AES-ish phone encryption, static key, JEX-resident | **Phase 2 — reimplement** | `EncryptionService.encryptPhone/decryptPhone` using `Cipher` + key from config (Vault in PROD), with `v1:` envelope so we can rotate | Definition unknown; do not call legacy `encrypt()` from Spring. Defer until phone fields are exercised. Tag legacy ciphertexts with `legacy:` and migrate lazily on read. |
| `hash(password)` | SHA-256 + Base64, unsalted | **Phase 0 — already done** | Hybrid via [WecmsPasswordEncoder.java](../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java); new writes BCrypt-only via the new `PasswordHashService` facade | Complete. New write paths must not touch legacy `hash()`. |

---

## 3. First Company Account Setup Strategy

| Env | Mechanism | Why |
|---|---|---|
| **LOCAL** | Keep V2 Flyway seed **plus** a `@Profile("local")` `BootstrapRunner` (`CommandLineRunner`) that idempotently calls `CompanyBootstrapService.bootstrapIfAbsent(...)` | V2 already works; layering a runner gives developers parity with UAT/PROD code paths without breaking the seed. The runner is a no-op when V2 has run (short-circuits on `BIZ_KEY` existence). |
| **UAT** | Admin-only internal API: `POST /internal/bootstrap/company` + Spring Security role + internal-token header + IP allowlist | Mirrors prod ops. Calling a real endpoint exercises the same code path operations will use, and produces an audit trail. |
| **PROD** | Same admin-only internal API; one-shot call by ops; **no** Flyway tenant seed, **no** runner | PROD must never auto-create tenants. Single explicit, idempotent, logged call. Manual SQL is forbidden by policy. |

**Evolution from current V2 seed without breakage:** V2 stays; it is idempotent on the `BIZ_KEY` PK. `CompanyBootstrapService.bootstrapIfAbsent(...)` short-circuits via `findById(bizKey).isPresent()`. The new LOCAL runner wins on a wiped-rows DB; V2 wins on a fresh DB. One service, three triggers.

---

## 4. Table Dependency Analysis

**Corrected insert order** (FK-driven; `BUSI_ADMN_INFM` must come after `USER_INFM`, not before, because it references both `BIZ_KEY` and `USER_ID`):

```
BUSI_INFM ──┐
            ├─> EMPL_INFM (FK BIZ_KEY)
            │      │
            │      └─> USER_INFM (carries EMPL_NO snapshot; no hard FK to EMPL_INFM)
            │             │
            │             ├─> USER_BUSI_CONN_INFM (FK USER_ID + BIZ_KEY)
            │             │
            │             └─> BUSI_ADMN_INFM (FK BIZ_KEY + USER_ID)
            │
            └─> (everything below depends on BUSI_INFM)
```

Final ordered chain: **BUSI_INFM → EMPL_INFM → USER_INFM → USER_BUSI_CONN_INFM → BUSI_ADMN_INFM**.

| Table | PK | Required cols | Generated by | Depends on | Rollback risk if a later step fails |
|---|---|---|---|---|---|
| `BUSI_INFM` | `BIZ_KEY` (VARCHAR 32) | `BUSI_NM`, `NTNL_CD`, `BUSI_STTS`, audit | `wb_get_next_val('BUSI_INFM','BIZ_KEY')` → `sq_biz_key` | — | **Low** — orphan company row, recoverable via DELETE; tx rollback is clean. |
| `EMPL_INFM` | `EMPL_NO` (VARCHAR 30) | `BIZ_KEY` (FK), audit; rest TBD when entity is built | `wb_get_next_val('EMPL_INFM','EMPL_NO', biz_key)` → `YYYYMMDD_nnn` | `BUSI_INFM` | **Medium** — counter advances on row-count algorithm; `@Transactional` rollback is mandatory or numbers skip. |
| `USER_INFM` | `USER_ID` (VARCHAR 50) | `USER_NM`, `USER_PW` (BCrypt only), `ACTV_YN`, `LOCK_YN`, `DEL_YN`, `LANG_CD` | caller-provided ID | `EMPL_INFM` (logically — carries `EMPL_NO` snapshot) | **Medium** — partial user without conn row leaves a ghost login. Same TX is mandatory. |
| `USER_BUSI_CONN_INFM` | composite `(USER_ID, BIZ_KEY)` | both FKs, `EMPL_NO`, audit | — | `USER_INFM` + `BUSI_INFM` | **High** — without this row, the user cannot resolve a tenant; login appears fine but authorisation breaks. |
| `BUSI_ADMN_INFM` | TBD (`BIZ_KEY` + `USER_ID` likely) | `BIZ_KEY`, `USER_ID`, audit | — | `USER_INFM` + `BUSI_INFM` | **High** — first admin missing means no one can manage the new tenant. |

**Rule:** the entire chain runs in **one** `@Transactional(rollbackFor = Exception.class)` boundary inside `CompanyBootstrapService.bootstrap(...)`. No partial commits.

---

## 5. Spring Boot Architecture Proposal

All packages rooted at `com.kosign.wecms`.

### 5.1 `LegacyFunctionRepository`
- **Path:** `com.kosign.wecms.common.legacy.LegacyFunctionRepository`
- **Responsibility:** Thin `@Repository` wrapping native calls to surviving PL/pgSQL functions during cut-over. Single grep-and-kill point.
- **Methods (signatures only):**
  ```java
  String nextVal(String table, String field);
  String nextVal(String table, String field, String p1, String p2, String p3, String p4, String p5, String p6);
  ```
- **Replaces:** every direct `select wb_get_next_val(...)` in JEX IDOs.

### 5.2 `BusinessKeyGeneratorService`
- **Path:** `com.kosign.wecms.domain.auth.bootstrap.BusinessKeyGeneratorService`
- **Responsibility:** Issues the next `BIZ_KEY`. Phase 1 delegates to `LegacyFunctionRepository`. Phase 3 swaps to a Spring-managed sequence reader.
- **Methods:** `String nextBizKey()`
- **Replaces:** `wb_get_next_val('BUSI_INFM','BIZ_KEY')`.

### 5.3 `EmployeeNumberGeneratorService`
- **Path:** `com.kosign.wecms.domain.auth.bootstrap.EmployeeNumberGeneratorService`
- **Responsibility:** Generates `YYYYMMDD_nnn` per `(BIZ_KEY, date)`. Phase 1 delegates to `LegacyFunctionRepository` (stub). Phase 2 native Java with row-level locking.
- **Methods:** `String nextEmplNo(String bizKey, java.time.LocalDate today)`
- **Replaces:** `wb_get_next_val('EMPL_INFM','EMPL_NO', biz_key)`.

### 5.4 `CompanyBootstrapService`
- **Path:** `com.kosign.wecms.domain.auth.bootstrap.CompanyBootstrapService`
- **Responsibility:** Single transactional orchestrator that creates the 5-table chain. Idempotent on `BIZ_KEY`.
- **Methods:**
  ```java
  @Transactional(rollbackFor = Exception.class)
  BootstrapResult bootstrap(CompanyBootstrapCommand cmd);
  boolean isBootstrapped(String bizKey);
  BootstrapResult bootstrapIfAbsent(CompanyBootstrapCommand cmd);
  ```
- **Replaces:** `WB_INIT_BUSI_DATA` IDO + manual SQL.

### 5.5 `UserBootstrapService`
- **Path:** `com.kosign.wecms.domain.auth.bootstrap.UserBootstrapService`
- **Responsibility:** Creates the first admin: calls `PasswordHashService.encodeForNewUser()`, inserts `USER_INFM` + `USER_BUSI_CONN_INFM` + `BUSI_ADMN_INFM`. Called only by `CompanyBootstrapService` (package-private preferred).
- **Methods:** `String createFirstAdmin(String bizKey, String emplNo, FirstAdminCommand cmd)`
- **Replaces:** `WB_INIT_USER_DATA` IDO.

### 5.6 `PasswordHashService`
- **Path:** `com.kosign.wecms.common.security.PasswordHashService`
- **Relationship to existing encoder — wrap, do not replace.** [WecmsPasswordEncoder.java](../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java) stays as the Spring Security `PasswordEncoder` SPI for login (hybrid BCrypt + legacy reader). `PasswordHashService` is a thin business-facing facade that exposes **only** `encodeForNewUser(...)` — BCrypt-only, never legacy — so bootstrap code physically cannot emit a SHA-256 hash. `matches()` continues to flow through the hybrid encoder during login.
- **Methods:** `String encodeForNewUser(String raw)`
- **Replaces:** any new write-path that would otherwise call legacy `public.hash()`.

### 5.7 `EncryptionService`
- **Path:** `com.kosign.wecms.common.security.EncryptionService`
- **Responsibility:** Phone-number encrypt/decrypt. Key from `wecms.security.phone-key` (Vault in PROD). Versioned envelope `v1:<base64>` so rotation does not require re-migration. Legacy ciphertexts read with `legacy:` prefix and migrated lazily.
- **Methods:** `String encryptPhone(String plain)`, `String decryptPhone(String cipher)`
- **Replaces:** legacy `public.encrypt()` black-box. **Phase 2** — not blocking Phase 1.

### 5.8 `CompanyBootstrapController`
- **Path:** `com.kosign.wecms.api.internal.CompanyBootstrapController`
- **Responsibility:** Admin-only HTTP surface for UAT/PROD. Single endpoint `POST /internal/bootstrap/company`. Returns `409` if already bootstrapped, `201` with the new `BIZ_KEY` otherwise.
- **Auth (defence in depth):**
  1. `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`
  2. Mandatory `X-Internal-Token` header validated against `wecms.internal.bootstrap-token` (rotated per env, never in repo).
  3. IP allowlist filter (`InternalEndpointFilter`) — restricted to ops bastion CIDR in PROD.
  4. Audit log entry on every call.
- **Methods:** `ResponseEntity<BootstrapResponse> bootstrap(@Valid @RequestBody BootstrapRequest req)`
- **Phase 2 deliverable** — does not ship in Phase 1.

---

## 6. Transaction & Safety Rules

1. **Single transaction.** The full 5-table chain runs under one `@Transactional` boundary in `CompanyBootstrapService`; no nested commits.
2. **Rollback on any failure.** `rollbackFor = Exception.class` (including checked).
3. **No legacy `hash()` on writes.** New rows always BCrypt via `PasswordHashService.encodeForNewUser()`.
4. **No raw SQL for ID generation.** All `wb_get_next_val` calls funnel through `LegacyFunctionRepository`. Never use `MAX(...) + 1` in any service.
5. **Profile gating.** `BootstrapRunner` is `@Profile("local")`. `CompanyBootstrapController` registered only when `wecms.internal.bootstrap.enabled=true` (default true on UAT/PROD, false on LOCAL).
6. **No PROD auto-bootstrap.** PROD has neither runner nor Flyway tenant seed — only the explicit API call.
7. **Tenant pin coherence.** If `TenantProperties#bizKey` is set, bootstrap must refuse to create a different `BIZ_KEY` than the pinned one (fail-fast).
8. **Idempotency.** Every bootstrap call is idempotent on `BIZ_KEY`; second call returns `409` / `BootstrapResult.alreadyExists`.
9. **Duplicate prevention.** Unique constraints (PK + any `TAX_ID` unique index) enforce no duplicate `BIZ_KEY`, no duplicate `USER_ID`, no duplicate company `TAX_ID` at DB level — service surface translates `DataIntegrityViolationException` to a clean `409`.
10. **Audit-user value.** `REG_USER` / `MOD_USER` for bootstrap rows is the constant `"SYSTEM_BOOTSTRAP"` — bootstrap precedes any real principal. `REG_DTM` / `MOD_DTM` from an injected `Clock` bean for testability.
11. **No secrets in logs.** Never log raw passwords, BCrypt hashes, internal tokens, or phone plaintext. Mask `USER_PW` and `X-Internal-Token` in every logging filter.
12. **Concurrency on `EMPL_NO`.** `EmployeeNumberGeneratorService` must use row-level locking or `INSERT ... ON CONFLICT DO UPDATE RETURNING`; never naive `count(*) + 1` in Java.
13. **No silent legacy-encrypt fallback.** `EncryptionService` refuses to decrypt unprefixed ciphertext in PROD until the legacy key is configured explicitly.

---

## 7. Migration Phases

### Phase 1 — Bootstrap Skeleton (immediate next sprint)
- **Scope:** Stand up the service-driven bootstrap path end-to-end on **LOCAL only**. No UAT/PROD enablement. Match JEX behavior exactly by delegating ID generation to legacy DB functions.
- **Spring artifacts:** `LegacyFunctionRepository`, `BusinessKeyGeneratorService` (delegating), `EmployeeNumberGeneratorService` (delegating stub), `CompanyBootstrapService`, `UserBootstrapService`, `PasswordHashService`, `EmplInfm` + `BusiAdmnInfm` JPA entities + repos, `BootstrapRunner` (`@Profile("local")`).
- **Stays legacy:** `wb_get_next_val` (wrapped, not rewritten); `encrypt()` (untouched — phone fields not on the bootstrap critical path); all 40 non-BIZ_KEY branches.
- **Exit criteria:**
  - Wiping `BUSI_INFM` locally + restart yields a fully populated 5-table tenant via the runner.
  - `CompanyBootstrapServiceIT` integration test passes.
  - Second invocation is a no-op (idempotent).
  - Existing V2 seed still works on a virgin DB.
  - Login via the seeded admin produces a working JWT through `AuthService`.

### Phase 2 — Algorithm Reimplementation
- **Scope:** Rewrite `EmployeeNumberGeneratorService` natively in Java with locking; build `EncryptionService` with `v1:` envelope; expose `CompanyBootstrapController` + auth filter for UAT.
- **Spring artifacts:** native `EmployeeNumberGeneratorService`, `EncryptionService`, `CompanyBootstrapController`, `InternalEndpointFilter`, `BOOTSTRAP_AUDIT` table (Flyway V11+).
- **Stays legacy:** `wb_get_next_val` for non-EMPL_NO branches; legacy phone ciphertext readable via `legacy:` prefix.
- **Exit criteria:** UAT ops can bootstrap a tenant via authenticated API; `EMPL_NO` collisions impossible under 50-thread load test; phone round-trip encrypt/decrypt verified.

### Phase 3 — Legacy Function Retirement
- **Scope:** Per-domain replacement of remaining `wb_get_next_val` branches as features touch them; PROD enablement of bootstrap API.
- **Spring artifacts:** domain-specific generator services (`TrcoKeyGeneratorService` etc.); `@Deprecated` on `LegacyFunctionRepository` methods as branches retire.
- **Stays legacy:** nothing intentional; tracked via a checklist.
- **Exit criteria:** `LegacyFunctionRepository` empty/removed; `wb_get_next_val` SQL function dropped via Flyway in a final cleanup migration; PROD has bootstrapped its first tenant via API.

---

## 8. Phase 1 Deliverables Checklist

New files in wecms-api/ (Needs Confirmation):

- `src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java`
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/BusinessKeyGeneratorService.java`
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/EmployeeNumberGeneratorService.java` *(stub; full impl in Phase 2)*
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/CompanyBootstrapService.java`
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/UserBootstrapService.java`
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/CompanyBootstrapCommand.java` *(record)*
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/FirstAdminCommand.java` *(record)*
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/BootstrapResult.java` *(record)*
- `src/main/java/com/kosign/wecms/domain/auth/bootstrap/BootstrapRunner.java` *(`@Profile("local")`)*
- `src/main/java/com/kosign/wecms/common/security/PasswordHashService.java`
- `src/main/java/com/kosign/wecms/domain/auth/entity/EmplInfm.java`
- `src/main/java/com/kosign/wecms/domain/auth/entity/BusiAdmnInfm.java`
- `src/main/java/com/kosign/wecms/domain/auth/repository/EmplInfmRepository.java`
- `src/main/java/com/kosign/wecms/domain/auth/repository/BusiAdmnInfmRepository.java`
- `src/test/java/com/kosign/wecms/domain/auth/bootstrap/CompanyBootstrapServiceIT.java`
- `src/test/java/com/kosign/wecms/domain/auth/bootstrap/BootstrapIdempotencyIT.java`
- `src/main/resources/db/migration/V11__empl_infm_busi_admn_infm.sql` *(only if V5 import didn't already create the schema; check first)*

Updates to existing files:
- [DataSeeder.java](../../../src/main/java/com/kosign/wecms/domain/auth/bootstrap/DataSeeder.java) — javadoc clarifying it is now sentinel-patch-only and `BootstrapRunner` owns tenant creation.

---

### Pseudo-code — `CompanyBootstrapService.bootstrap(...)`

```java
@Transactional(rollbackFor = Exception.class)
public BootstrapResult bootstrap(CompanyBootstrapCommand cmd) {
    String bizKey = cmd.bizKey() != null
        ? cmd.bizKey()
        : businessKeyGenerator.nextBizKey();

    if (busiInfmRepository.existsById(bizKey)) {
        return BootstrapResult.alreadyExists(bizKey);
    }
    tenantProperties.assertConsistent(bizKey);

    busiInfmRepository.save(toBusiInfm(bizKey, cmd, SYSTEM_BOOTSTRAP, clock));

    String emplNo = employeeNumberGenerator.nextEmplNo(bizKey, LocalDate.now(clock));
    emplInfmRepository.save(toEmplInfm(bizKey, emplNo, cmd.firstAdmin(), SYSTEM_BOOTSTRAP, clock));

    String userId = userBootstrapService.createFirstAdmin(bizKey, emplNo, cmd.firstAdmin());

    return BootstrapResult.created(bizKey, userId, emplNo);
}
```

---

## DO / DON'T

**DO**
- Funnel all `wb_get_next_val` calls through `LegacyFunctionRepository` so retirement is a `grep`.
- Run the 5-table chain in one `@Transactional` boundary with `rollbackFor = Exception.class`.
- Make `CompanyBootstrapService.bootstrap()` idempotent on `BIZ_KEY`.
- Use `"SYSTEM_BOOTSTRAP"` as the audit user for bootstrap rows.
- Inject `Clock` so audit timestamps are testable.
- Mask `USER_PW` and `X-Internal-Token` in every logging filter.

**DON'T**
- Don't call legacy `public.hash()` from any new write — BCrypt only.
- Don't auto-bootstrap on PROD; no runner, no Flyway tenant seed there.
- Don't use naive `count(*) + 1` for `EMPL_NO`; use locking or `UPSERT RETURNING`.
- Don't break the existing V2 seed — evolve around it via idempotent `findById`.
- Don't add a "create company" UI to `wecms-web` in this scope — internal API only.
- Don't attempt bit-compatible legacy phone `encrypt()` without a recovered key + sample.

---

## Verification (end-to-end test plan for Phase 1)

1. **Fresh DB.** Drop `wecms_dev`, recreate. Run `./gradlew bootRun --args='--spring.profiles.active=local'`. Flyway applies V1–V10; V2 seeds the test tenant. `BootstrapRunner` short-circuits (already exists). Assert via SQL count: `BUSI_INFM`=1, `USER_INFM`=2.
2. **Wiped tenant.** `DELETE FROM BUSI_ADMN_INFM; DELETE FROM USER_BUSI_CONN_INFM; DELETE FROM USER_INFM; DELETE FROM EMPL_INFM; DELETE FROM BUSI_INFM;` then restart. `BootstrapRunner` creates a fresh tenant via the service. Assert all 5 tables populated; `BIZ_KEY` matches `TenantProperties#bizKey`.
3. **Idempotency.** Restart again with rows present. `BootstrapRunner` short-circuits without writes. No new rows; logs show `alreadyExists`.
4. **Rollback.** Inject a failure in `UserBootstrapService` (test-only flag). Run `BootstrapRunner`. Assert all 5 tables remain empty (single TX rolled back).
5. **Login.** Use the seeded admin via `POST /api/auth/login` (existing `AuthController`). Verify JWT issued and `USER_LOGIN_HIS` row written.
6. **Concurrency smoke (Phase 2 gate, but exercise now if possible).** Two parallel `bootstrap(...)` calls with the same `BIZ_KEY` → exactly one `created`, one `alreadyExists`; no duplicates.

---

## Final Recommendation

Ship **Phase 1** as a thin, service-driven bootstrap exercised on LOCAL via a `@Profile("local")` `CommandLineRunner`, all roads leading through `CompanyBootstrapService` — the same service that will back the UAT/PROD admin API in Phase 2. **Wrap** `wb_get_next_val` rather than rewriting it; the only algorithm worth reimplementing in Java on day one is `EMPL_NO`, and even that ships as a delegating stub to keep the sprint tight. Keep the existing `WecmsPasswordEncoder` hybrid for login and add a write-only `PasswordHashService` facade so bootstrap code physically cannot emit a SHA-256 hash. **Defer** phone `encrypt()` to Phase 2 — it is not on the bootstrap critical path, and a clean `v1:` envelope beats reverse-engineering a JEX black-box. The single biggest risk is order-of-insert + transactional integrity across the 5-table chain; `BUSI_ADMN_INFM` must come **after** `USER_INFM` (correcting the brief), and the whole chain must succeed-or-rollback as one transaction.
