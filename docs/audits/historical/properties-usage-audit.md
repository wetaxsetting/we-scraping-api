# Properties & Configuration Usage Audit — WeCMS Refactor (`wecms-api`)

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](../README.md)._

> **Read-only audit.** No code was modified. Scope: every `.properties` / `.yml` / `.yaml`
> file under [`wecms-api/src/main/resources`](../../../src/main/resources) plus every Java
> usage of `MessageSource` / `getMessage(...)` / `@Value` / `@ConfigurationProperties` /
> `ReloadableResourceBundleMessageSource`.
>
> **Date:** 2026-06-12 · **System:** Refactor (Spring Boot 3.5.13)

---

## 1. Inventory — every config file under `src/main/resources`

`find src/main/resources -type f \( -name "*.properties" -o -name "*.yml" -o -name "*.yaml" \)` → **10 files**, no `.yaml` (long form) and no nested config files (the `db/migration/` subtree holds only SQL (the `db/manual/` and `db/archive/` SQL subtrees were removed 2026-06-23)).

| # | File | Type | Category |
|---|---|---|---|
| 1 | [`application.yml`](../../../src/main/resources/application.yml) | YAML | Environment configuration (base) |
| 2 | [`application-local.yml`](../../../src/main/resources/application-local.yml) | YAML | Environment configuration (profile) |
| 3 | [`application-sit.yml`](../../../src/main/resources/application-sit.yml) | YAML | Environment configuration (profile) |
| 4 | [`application-uat.yml`](../../../src/main/resources/application-uat.yml) | YAML | Environment configuration (profile, reserved) |
| 5 | [`application-prod.yml`](../../../src/main/resources/application-prod.yml) | YAML | Environment configuration (profile, reserved) |
| 6 | [`messages.properties`](../../../src/main/resources/messages.properties) | properties | i18n / validation / error / audit (default bundle) |
| 7 | [`messages_en.properties`](../../../src/main/resources/messages_en.properties) | properties | i18n (English) |
| 8 | [`messages_ko.properties`](../../../src/main/resources/messages_ko.properties) | properties | i18n (Korean) |
| 9 | [`messages_vi.properties`](../../../src/main/resources/messages_vi.properties) | properties | i18n (Vietnamese) |
| 10 | [`messages_km.properties`](../../../src/main/resources/messages_km.properties) | properties | i18n (Khmer) |

---

## 2. Which files Spring Boot actually loads

### `application*.yml` — profile-driven

- **`application.yml`** — always loaded (base). Sets `spring.profiles.active: local` as the default, so on a bare `bootRun` the **`local`** profile layers on top.
- **`application-{local,sit,uat,prod}.yml`** — exactly **one** is layered over the base per run, selected by `spring.profiles.active` (default `local`) or the `SPRING_PROFILES_ACTIVE` env var set on the host. They are not all active at once, but **all are live config** — each is the active file for its environment.
- **`.env`** — optionally imported via `spring.config.import: optional:file:./.env[.properties]` in `application.yml`. Keys there override the YAML `${VAR:default}` placeholders; OS env vars still win. **Used** (loader present), though the file itself is git-ignored and not in the repo.

> **`application-dev.yml` / `application-test.yml`** referenced in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) §"Application Profiles" **do not exist on disk.** `dev` content was folded into `application-sit.yml` (2026-05-15); `application-test.yml` is **not present** in either `src/main/resources` or `src/test/resources`. The CLAUDE.md table is stale on these two rows. *(Reported, not modified.)*

### `messages*.properties` — all loaded as one i18n bundle

- Loaded via `spring.messages.basename: messages` + `encoding: UTF-8` in `application.yml`.
- Spring Boot **auto-configures** the `MessageSource` (a `ResourceBundleMessageSource`) from these properties — there is **no hand-written `MessageSource` bean** and **no `ReloadableResourceBundleMessageSource`** anywhere in the tree (verified by grep across `src/main/java`).
- `messages.properties` is the **default/fallback** bundle; the `_en` / `_ko` / `_vi` / `_km` files are locale overlays resolved per request via `LocaleContextHolder` (set from `Accept-Language` → `LangCd`).
- **All 5 are used.** The locale files are sparse (see key counts below) and intentionally fall back to `messages.properties` for any missing key.

---

## 3. Code usages of the audited symbols

### `MessageSource` / `getMessage(...)`

| Class | Usage |
|---|---|
| [`common/i18n/MessageResolver.java`](../../../src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java) | The **only** consumer. Injects `MessageSource`; calls `messageSource.getMessage(code, args, code, locale)` in two overloads (request-locale + explicit-locale). Fallback = the code itself. Used product-wide via `BusinessException` → `GlobalExceptionHandler` and by `AuditLogService` (one row per locale). |

- `ReloadableResourceBundleMessageSource` — **not used** (no match in the codebase).
- No custom `MessageSource` / `setBasename` / `setDefaultEncoding` bean in `config/`.
- Other `getMessage()` hits across services are `Throwable.getMessage()` / `ConstraintViolation.getMessage()` — unrelated to i18n.

### `@Value`

| Key | Class | In a YAML file? |
|---|---|---|
| `wecms.cors.allowed-origins` | [`config/CorsConfig.java`](../../../src/main/java/com/kosign/wecms/config/CorsConfig.java) | ✅ base + local + sit |
| `wecms.dispatch.auto-receive:false` | [`payment/dispatch/service/DispatchAutoReceiveRegistrar.java`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/DispatchAutoReceiveRegistrar.java) | ✅ local/sit/uat/prod |
| `wecms.crypto.cert-key:<default>` | [`banking/bankcredentials/service/BankCertCryptoService.java`](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java) | ❌ **code-default only** — not declared in any YAML |
| `wecms.internal-transfer.routing.woori-bank-cd:970457` | [`payment/internaltransfer/service/InternalTransferDispatchExecutor.java`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java) | ❌ **code-default only** |
| `wecms.internal-transfer.routing.shinhan-bank-cd:970424` | `InternalTransferDispatchExecutor.java` | ❌ **code-default only** |

> Note: `wecms.crypto.cert-key` ships a **hardcoded base64 default key in source** — flagged as a secret-hygiene concern for any non-local deploy (every other secret reads from an env var). Reported only.

### `@ConfigurationProperties` (typed config — the preferred pattern per CLAUDE.md)

17 binders, each registered via `@EnableConfigurationProperties` on a `@Configuration` class (or `SecurityConfig`):

| Prefix | Record / class | Registered by | Backing YAML |
|---|---|---|---|
| `wecms.jwt` | [`JwtProperties`](../../../src/main/java/com/kosign/wecms/config/JwtProperties.java) | `SecurityConfig` | base/local/sit |
| `wecms.coocon` | [`CooconProperties`](../../../src/main/java/com/kosign/wecms/config/CooconProperties.java) | `CooconConfig` | base/local/sit/uat/prod |
| `wecms.osmc` | [`OsmcProperties`](../../../src/main/java/com/kosign/wecms/domain/operation/client/OsmcProperties.java) | `OsmcConfig` | base/uat/prod |
| `wecms.nxisas` | [`NxisasGatewayProperties`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasGatewayProperties.java) | `NxisasConfig` | ❌ **none** — only tuning knobs (`timeoutSeconds`/`sessionIdleSeconds`), code defaults 60s/300s. Gateway URL+key reuse `wecms.coocon.smart-api-*`. |
| `wecms.oat.seed-decrypt` | [`OatSeedDecryptProperties`](../../../src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/config/OatSeedDecryptProperties.java) | `OwnAccountTransferConfig` | ❌ **none** — `@DefaultValue("false")` feature flag |
| `wecms.dispatch.efs` | [`EfsProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/van/client/EfsProperties.java) | (self) | local/sit/uat/prod |
| `wecms.dispatch.sftp` | [`SftpProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/sftp/SftpProperties.java) | (self) | local/sit/uat/prod |
| `wecms.dispatch.callback` | [`CallbackProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/CallbackProperties.java) | (self) | local/sit/uat/prod |
| `wecms.internal-transfer` | [`InternalTransferStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferStorageProperties.java) | `InternalTransferConfig` | base/local/sit |
| `wecms.internal-transfer.cutoff` | ``CutoffProperties`` *(historical — class no longer present; the cut-off config is now `domain/payment/internaltransfer/entity/CutoffConfig.java`)* | `InternalTransferConfig` | base/local/sit |
| `wecms.overseas-payment` | [`OverseasPaymentStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/overseas/service/OverseasPaymentStorageProperties.java) | `OverseasPaymentConfig` | base/local/sit |
| `wecms.vendor-payment` | [`VendorPaymentStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentStorageProperties.java) | `VendorPaymentConfig` | base/local/sit |
| `wecms.expense-payment` | [`ExpensePaymentStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentStorageProperties.java) | `ExpensePaymentConfig` | base/local/sit |
| `wecms.payroll` | [`PayrollPaymentStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentStorageProperties.java) | `PayrollPaymentConfig` | base/local/sit |
| `wecms.contacts` | [`ContactsFileStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/setting/contacts/service/ContactsFileStorageProperties.java) | `ContactsConfig` | base/local/sit |
| `wecms.user-image` | [`UserImageStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/setting/users/service/UserImageStorageProperties.java) | `UserImageConfig` | base/local/sit |
| `wecms.erp-transaction-history` | [`ErpTrnsHistoryStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryStorageProperties.java) | `ErpTrnsHistoryConfig` | base/local/sit |
| `wecms.preferences` | [`PreferencesLogoStorageProperties`](../../../src/main/java/com/kosign/wecms/domain/setting/preferences/service/PreferencesLogoStorageProperties.java) | `PreferencesConfig` | base/local/sit |

---

## 4. Per-file report

### Environment configuration files

#### `application.yml` (base) — **used**
- **Purpose:** Shared baseline for every profile. JPA (`open-in-view:false`, `ddl-auto:validate`, PostgreSQL dialect, UTC), `spring.messages` basename/encoding, multipart caps (10 MB/50 MB), single-threaded scheduler, `server.port:8080`, error rendering, the full `wecms.*` tree (jwt, cors, tenant, file-storage roots, internal-transfer cutoff bank codes, coocon, osmc), and the `traceId` logging pattern. **Flyway removed 2026-06-10** — no migration runs; schema is treated as external/pre-migrated.
- **Referenced by:** all 18 `@ConfigurationProperties` binders + `CorsConfig`/`DispatchAutoReceiveRegistrar` `@Value`s + Spring Boot's auto-config (`spring.*`, `server.*`, `logging.*`).
- **Notable keys:** `spring.profiles.active=local`, `spring.config.import=optional:file:./.env`, `spring.messages.basename=messages`, `wecms.jwt.secret=${WECMS_JWT_SECRET}` (no default → fail-fast unless a profile/env supplies it), `wecms.coocon.*` (DEV defaults), `wecms.osmc.*`.

#### `application-local.yml` — **used** (default profile, git-ignored)
- **Purpose:** Developer-laptop overlay. Datasource → shared dev DB `172.168.5.172:5432/wecmsdevdb` (user `wecmsdev_owner`, password default baked in), `show-sql:true` + `format_sql:true`, local JWT dev secret default, CORS (localhost:3000 + SIT + Coocon alias), full `wecms.dispatch.*` (EFS/SFTP/callback, all default OFF → mocks active), file-storage roots → `C:/WECMS PROJECT/File Data`, internal-transfer cut-off time tables (Shinhan/Woori NAPAS/CITAD/SWIFT windows), coocon DEV creds, `com.kosign.wecms: DEBUG` logging.
- **Keys:** all `wecms.*` storage roots, `wecms.dispatch.*`, `wecms.internal-transfer.cutoff.*`, `wecms.coocon.*`, datasource + jpa.

#### `application-sit.yml` — **used** (when `SPRING_PROFILES_ACTIVE=sit`, git-ignored)
- **Purpose:** SIT / shared-dev-server overlay (merged from the server config + the old `application-dev.yml`). Same datasource as local, `show-sql:true`, CORS (localhost + `wecms.kosign.dev`), full `wecms.dispatch.*`, file-storage roots (note **non-standard** `c:/WeCMS/File Data` for internal-transfer / overseas / vendor / expense / payroll vs `C:/WECMS PROJECT/File Data` for the rest — flagged inline for normalization), internal-transfer cut-off table, coocon DEV creds.
- **Keys:** mirror local with SIT origins + the split storage roots.

#### `application-uat.yml` — **used when activated** (RESERVED — no UAT deploy in the R&D sprint)
- **Purpose:** UAT overlay created 2026-06-07 so `SPRING_PROFILES_ACTIVE=uat` doesn't fall through to base. Hard rules: `ddl-auto:validate`, SQL logging OFF, **no secrets**, all dispatch switches default OFF. Re-declares `wecms.coocon.*` and `wecms.osmc.*` **without defaults** so a missing env var fails fast (never silently falls back to DEV creds). `logging.root=WARN`.
- **Keys:** `wecms.dispatch.*` (env-var driven), `wecms.coocon.*` / `wecms.osmc.*` (required env vars, no defaults).

#### `application-prod.yml` — **used when activated** (RESERVED — no PROD deploy in the R&D sprint)
- **Purpose:** PROD overlay. Same hard rules as UAT plus `server.error.include-message:never` / `include-binding-errors:never`. Re-declares coocon/osmc without defaults (fail-fast). `logging.root=WARN`.
- **Keys:** identical shape to UAT.

### i18n / message files

All five together form **one** Spring i18n bundle (`basename=messages`). `messages.properties` is the default/fallback; locale files overlay only the keys that have a verified translation.

| File | Keys (`=` lines) | Total lines | Purpose / contents | Used? | Referencing class |
|---|---|---|---|---|---|
| `messages.properties` | ~399 | 780 | **Default bundle** — the full key universe. Holds **error codes** (`WBExxxx`, `WBIxxxx`, `WBE_*`), **validation messages** (`WBE_VALIDATION`, `validation.own_account_transfer.*`, `settings.*.excel.*`), **audit-log activity/detail keys** (`audit.*`), and **enum/label keys** (`payment.*`, `settings.*`, `bank.acct_type.*`). English text. | ✅ | `MessageResolver` (sole reader); written by `AuditLogService`, thrown via `BusinessException` |
| `messages_en.properties` | ~205 | 258 | English overlay (subset of the default — explicit `en` locale). | ✅ | `MessageResolver` |
| `messages_ko.properties` | ~42 | 67 | Korean overlay — verified translations only (audit/enum/key subset); everything else falls back to default. | ✅ | `MessageResolver` |
| `messages_vi.properties` | ~43 | 65 | Vietnamese overlay — verified subset. | ✅ | `MessageResolver` |
| `messages_km.properties` | ~34 | 56 | Khmer overlay — smallest verified subset. | ✅ | `MessageResolver` |

> The locale files being sparse is **by design** (CLAUDE.md §"Domain enums": omit a locale entry rather than invent a translation; the missing-key fallback returns the default-bundle text or the raw key).

---

## 5. Categorization

| Category | Where it lives |
|---|---|
| **Environment configuration** | `application.yml` + `application-{local,sit,uat,prod}.yml`. Bound via the 18 `@ConfigurationProperties` records and 5 `@Value` injections. Covers DB, JWT, CORS, tenant, scheduler, multipart, file-storage roots, banking cut-off, EFS/SFTP/VAN dispatch, Coocon/OSMC/NXISAS gateway, OAT decrypt flag, logging. |
| **i18n messages** | `messages_{en,ko,vi,km}.properties` + non-error keys in `messages.properties`: `audit.*` (activity/detail), `payment.*` / `settings.*` enum-label keys, `bank.acct_type.*`, `payment.upload.field.*`. |
| **Validation messages** | In `messages.properties`: `WBE_VALIDATION`, `WBE_BAD_REQUEST`, `validation.own_account_transfer.*`, `settings.vas_account.excel.*`, `settings.purpose.excel.*`, `payment.upload.error.blocked_chars`. (Bean-validation `@Pattern`/`@Size` messages resolve through these keys via `GlobalExceptionHandler`.) |
| **Error messages** | In `messages.properties`: the entire `WBExxxx` / `WBIxxxx` / `WBE_*` code set (auth `WBE1009`–`WBE1128`, export `WBE201x`, feature families `WBE_CHART_OF_ACCOUNTS_*`, `WBE_APPROVAL_LINE_*`, `WBE_OAT_*`, `WBE_EFS_*`, catch-all `WBE9999`, etc.). |
| **Email / notification templates** | **None.** No template files, no `spring.mail`, no notification module (the refactor-only notification table/package was removed 2026-06-10 — see memory `project_notification_module_removed.md`). |
| **Business configuration** | In the YAML `wecms.*` tree: banking cut-off windows (`wecms.internal-transfer.cutoff.{shinhan,woori}.{napas,citad,swift}`), cut-off bank codes, internal-transfer routing bank codes (`wecms.internal-transfer.routing.*`, code-default only), JWT issuer/expiry, file-storage layout per module, dispatch enable flags + timeouts, OAT seed-decrypt feature flag. |

---

## 6. Findings (no code changed — report only)

1. **Stale CLAUDE.md profile table.** `application-dev.yml` and `application-test.yml` are listed in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) §"Application Profiles" but **neither exists on disk** (`dev` was merged into `sit`; no `test` profile in `src/main` or `src/test`). The base `application.yml` Flyway comments are accurate (Flyway removed 2026-06-10) but the profile table predates the dev→sit merge.
2. **Config keys with no YAML declaration (code-default only):** `wecms.crypto.cert-key`, `wecms.internal-transfer.routing.woori-bank-cd`, `wecms.internal-transfer.routing.shinhan-bank-cd`, `wecms.nxisas.*`, `wecms.oat.seed-decrypt.enabled`. These bind purely from in-source defaults today — surfacing them in YAML (or env) would make them discoverable/overridable.
3. **Hardcoded default secret.** `wecms.crypto.cert-key` carries a real base64 AES key as its `@Value` default in `BankCertCryptoService`. Every other secret (DB password, JWT secret) reads from an env var with at most an insecure local placeholder; this one ships a usable key in source. Flag for non-local deploy.
4. **No unused config file.** All 10 files are loaded under at least one profile/locale. The locale `messages_*` files are intentionally sparse, not unused.
5. **`MessageSource` is Spring Boot auto-configured** (no custom bean, no `ReloadableResourceBundleMessageSource`); `MessageResolver` is the single application-level consumer.
6. **Storage-root path inconsistency** between profiles (`c:/WeCMS/File Data` vs `C:/WECMS PROJECT/File Data` in `application-sit.yml`) is already flagged inline in the YAML as pending normalization.

---

*End of audit — read-only, no files under `src/main/` were modified.*
