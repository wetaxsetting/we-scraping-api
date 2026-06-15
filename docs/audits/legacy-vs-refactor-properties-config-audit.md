# Legacy JEX vs Spring Refactor — Properties / Message / Config Structure Audit

> **Read-only comparison audit.** No code modified, no migration created, no refactor performed.
> Goal: determine whether the Spring refactor's properties / message / config usage follows the
> legacy JEX standard, or diverges into a non-standard structure — and whether any divergence is risky.
>
> **Date:** 2026-06-12 · **Legacy tree:** `WECMS DEV/` (JEX framework) · **Refactor tree:** `wecms-api/`
> **Companion:** [properties-usage-audit.md](properties-usage-audit.md) (refactor-side inventory)

---

## 1. Summary

The two systems use **fundamentally different localization and configuration architectures**, and the
divergence is **mostly deliberate and documented** (CTO directive 2026-05-08 retiring legacy JEX naming;
the Spring-idiomatic config model). The refactor does **not** follow the legacy file/key structure — by design.

- **Legacy JEX** resolves messages/error codes from a **Java enum (`WbMessageCode`) + JEX `CodeManager` DB lookup** (group `RSPS_CD`), localizes labels via the **Jex Studio ML registry (DB)** + **`*_LANG_DETL` DB tables** + **frontend `jexML` plugin**, and holds environment config in **per-country / per-environment XML files** (`jex.wabooks_new[.real|.test].xml` + `.user.xml` secrets). Almost nothing lives in flat `.properties` files — the `dictionary*.properties` files are a tiny, partial fallback artifact.
- **Spring refactor** consolidates messages, error codes, validation text, **and** some code-to-label mappings into **`messages.properties` + 4 locale overlays** (Spring `MessageSource`), and holds environment config in **`application.yml` + 4 profile files** with **typed `@ConfigurationProperties`** + env-var secrets.

**Net assessment:** The refactor is **Spring-standard, not legacy-standard.** That is acceptable and largely
an improvement (env-var secrets, typed config, single i18n bundle). The genuine risks are narrow and listed
in §5–§7: (a) DB-driven business config (banking cut-off times) moved into static YAML, (b) a hardcoded crypto
key default in source, (c) backend `messages.properties` carrying code-to-label mappings that the refactor's
own policy says belong on the frontend, and (d) error-code naming that only partially preserves legacy codes.

**Overall risk: 🟡 Medium** (no High items; several Medium items that affect parity/operability, not correctness).

---

## 2. Legacy JEX config / message structure

### 2.1 Messages & error codes — **Java enum + DB lookup, not properties**

| Concern | Mechanism | Location |
|---|---|---|
| Code registry | `WbMessageCode` Java enum — `(cd, default-EN-msg)` tuples | [`WECMS/src/wabooks/enums/WbMessageCode.java`](../../../WECMS%20DEV/WECMS/src/wabooks/enums/WbMessageCode.java) |
| Code families | `WSE*` (system), `WBE*` (business error), `WBI*` (info), `WBW*` (warning), `WBC*` (confirm), `SES*` (session) — **numeric** suffixes (`WBE1009`, `WBW0001`) | same |
| Text resolution | `MessageHelper.getMessage(lang, code)` → `CodeManager.getCode("RSPS_CD", code, jexLang)` **DB lookup**, falling back to the enum's inline English | [`WECMS/src/wabooks/helper/MessageHelper.java`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/MessageHelper.java) ~L46–111 |
| Validation messages | **Same** system — `throw new WbRollbackException(util, WbMessageCode.WBW0001, label)`; no separate validation registry | `ValidationHelper.java`, `*_act.jsp` |
| Per-locale | `JexLANG` (EN/KO/KM/VI) threaded from request → `LocaleHelper` → exception ctor → `CodeManager` | [`WECMS/src/wabooks/helper/LocaleHelper.java`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/LocaleHelper.java) |

> Message **text** is editable in the Jex Studio code registry (DB group `RSPS_CD`) **without a redeploy** —
> the enum only supplies an English fallback.

### 2.2 Labels (screen / field / menu / code-table) — **DB + Jex Studio + frontend JS**

| Label type | Mechanism | Location |
|---|---|---|
| Screen / field labels | Jex Studio **ML registry** (DB), rendered via `jexML` plugin: JSP `data-jex-ml="svc_id_m001"` + JS `ML.get("svc_id_j001","default")` | [`WECMS_STATIC/web/js/comm/jex.wabooks.custom_v2.js`](../../../WECMS%20DEV/WECMS_STATIC/web/js/comm/jex.wabooks.custom_v2.js) L9–23; per-page `*_api_osmc.js` |
| Code-table values | Per-language DB tables `USAG_INFM_LANG_DETL`, `BUSI_USAG_SET_INFM_LANG_DETL`, `NATN_INFM`, `CCY_INFM` (keyed by `LANG_CODE`) | `WECMS/src/wabooks/entity/*LangDetl.java` |
| Enum labels | Java enum `getLabel()` — **mono-language, KO-centric**, fallback only | `WECMS/src/wabooks/enums/AcctTypeCd.java` L38–51, `ApprDv.java` |
| Menu labels | DB `MENU_AUTH_ADMN_SYS.MNU_NM` — **single language**, not per-locale | `WECMS_DOC/database/ddl/menu_auth_admn_sys.sql`; entity `MenuAuthAdmnSys.java` |
| `dictionary*.properties` | 5 tiny files (68–87 lines), payroll/label fragments, mixed-language values; used only as a `ResourceBundle` **fallback** for generic dictionary keys (`DOC_TYPE`, `ACCT_NO`, `TAXCODE`) — **partial/legacy artifact** | `WECMS/src/dictionary*.properties` |

### 2.3 Environment / business config — **per-country, per-environment XML**

| File pattern | Role | Secrets? |
|---|---|---|
| `WEB-INF/jex/config/{,VI/,KM/}jex.wabooks_new.xml` | Framework routing (filters/actions/views/services) | No |
| `…/jex.wabooks_new.user.xml` (+ `.real.user.xml`, `.test.user.xml`) | **DEV/UAT/PROD env config + secrets**: Coocon API keys, AWS SES SMTP creds, Infobip SMS creds (incl. **plaintext password** in KM), session timeout, captcha/GA keys, JNDI datasource name | **Yes (plaintext)** |
| `WEB-INF/jex*.prop` (`jex.prop`, `jex_VI.prop`, `jex.real_VI.prop`, `jex_test_VI.prop`) | Environment switch via `TSTCL_DV=DEVL\|TEST\|REAL` + caching/debug flags + which config file to load | No |
| `WECMS_ETC/.jexSettings/jex.deploy.{dev,test,real}.prop` | Per-env deploy host metadata (build-time) | No (embedded IPs) |
| `WECMS_CONFIG/pkgInfo/*/.prop`, `WECMS_JEXSETTING/jex.studio.domain.xml` | Jex Studio build/package metadata — **not runtime config** | No |
| DB tables `SYS_ENV_SET_INFM`, `BUSI_ENV_SET_INFM`, `TB_FIRM_CONF` | **Runtime business config** — banking cut-offs, feature toggles, VAN/firm codes, bank partition ids | n/a (DB) |

- **Env switching:** environment = filename variant (`.user` = DEV, `.test.user` = UAT, `.real.user` = PROD) selected by the active `.prop` (`TSTCL_DV`). **Country** = folder (`VI/` Vietnam, `KM/` Cambodia).
- **DB connection:** JNDI (`java:/wecmsvnrealdb` etc.), credentials managed by JBoss — never in app XML.
- **Email/notification:** SMTP relay (AWS SES) configured in `.user.xml`; message bodies are DB/`BLTB_*`-driven. **No dedicated template files.**

---

## 3. Spring refactor config / message structure

(Full detail in [properties-usage-audit.md](properties-usage-audit.md). Condensed here for comparison.)

| Concern | Mechanism | Location |
|---|---|---|
| Messages / error codes / validation / some labels | Single **`messages.properties`** (≈399 keys, EN base) + sparse `messages_{en,ko,vi,km}.properties` overlays | `src/main/resources/messages*.properties` |
| i18n engine | Spring Boot **auto-configured `MessageSource`** via `spring.messages.basename=messages`; **no** `ReloadableResourceBundleMessageSource`, no custom bean | `application.yml` |
| Resolver | `MessageResolver` wraps `MessageSource.getMessage(code, args, code, locale)`; fallback = the code itself | `common/i18n/MessageResolver.java` |
| Error-code families | **Mixed**: legacy-numeric reused verbatim (`WBE1009`, `WBE1190`, `WBW1138`, `WBW1152`, `WBE1199`, `WBE1153`), refactor-numeric (`WBE20xx`), and **new snake-case** (`WBE_VALIDATION`, `WBE_CHART_OF_ACCOUNTS_NOT_FOUND`, `WBE_OAT_*`) | `messages.properties` |
| Environment config | `application.yml` + `application-{local,sit,uat,prod}.yml` (Spring profiles) | `src/main/resources/` |
| Typed config | **18 `@ConfigurationProperties`** records (`wecms.*`) + **5 `@Value`** injections | `config/` + `domain/**/` |
| Secrets | **Env vars** (`WECMS_DB_PASSWORD`, `WECMS_JWT_SECRET`, `WECMS_COOCON_*`, …) with insecure local defaults; `uat`/`prod` re-declare without defaults → fail-fast | profile YAML |
| Labels (intended) | Per refactor policy: **frontend** `wecms-web/locales/*.json` via `t(key)`; enums carry `i18nKey()`, not labels | `wecms-web/` (out of this tree) |

---

## 4. Difference table (legacy vs refactor)

| Dimension | Legacy JEX | Spring Refactor | Follows legacy? |
|---|---|---|---|
| **Message text store** | DB (`CodeManager` `RSPS_CD`) + enum fallback | Flat `messages.properties` + locale files | ❌ Different (Spring-standard) |
| **Editable without redeploy** | ✅ Yes (DB registry) | ❌ No (bundled in JAR) | ❌ Capability lost |
| **Error-code definition** | `WbMessageCode` Java enum | `messages.properties` keys | ❌ Different |
| **Error-code naming** | Strict `WBE####` numeric | Mixed: `WBE####` reused + `WBE20xx` + `WBE_SNAKE_CASE` | ⚠️ Partial |
| **Validation messages** | Same code system (`WbMessageCode`) | Same bundle (`WBE_VALIDATION`, `validation.*`) | ✅ Conceptually aligned |
| **Screen/field labels** | Jex Studio ML (DB) + `jexML` JS | Frontend `locales/*.json` (`t(key)`) | ❌ Different layer (moved to FE) |
| **Code-table labels** | DB `*_LANG_DETL` tables | Enum `i18nKey()` + FE `t(key)`; **some in backend `messages.properties`** | ❌ Different + ⚠️ inconsistent |
| **Menu labels** | DB `MENU_AUTH_ADMN_SYS.MNU_NM` (1 lang) | New `MENU_ITEM_TRANSLATION` table (per-lang) + FE registries | ❌ Different (improved) |
| **`dictionary*.properties`** | 5 partial fallback files | **None** | ➖ N/A (legacy artifact) |
| **Env config format** | Per-country/per-env XML + `.prop` | `application.yml` + profiles | ❌ Different (Spring-standard) |
| **Env tiers** | DEVL / TEST / REAL × {VI, KM} | local / sit / uat / prod × {VN} | ⚠️ Remapped; `sit` new; no KH |
| **Secrets** | Plaintext in `.user.xml` (incl. passwords/AWS keys) | Env vars + fail-fast (uat/prod); local defaults | ✅ Improved (mostly) |
| **DB connection** | JNDI (JBoss-managed) | Direct JDBC URL in YAML | ❌ Different (Spring-standard) |
| **Business config (cut-offs, toggles)** | DB (`SYS_ENV_SET_INFM`, `TB_FIRM_CONF`) | **Static YAML** (`wecms.internal-transfer.cutoff.*`) | ❌ Different (DB → file) |
| **Email/SMTP** | `.user.xml` (AWS SES) | **Not configured** (no `spring.mail`, notifications removed) | ➖ Not ported |

---

## 5. Non-standard refactor findings

1. **Code-to-label business mappings live in backend `messages.properties`** — contrary to the refactor's own policy.
   `messages.properties` carries enum/code-to-label entries: `payment.appr_stts.*`, `payment.proc_stat.*`,
   `settings.erp_transaction_history.type.01..17`, `settings.vas_account.type.*` / `.status.*`,
   `payment.approval_box.type.*`. The refactor's stated rule (CLAUDE.md §"Domain enums") is that labels live in
   `wecms-web/locales/*.json` and enums carry only `i18nKey()`. In legacy these were DB `*_LANG_DETL` rows. So
   the refactor has a **third, unintended home** for code labels (backend bundle) that matches neither legacy
   (DB) nor its own policy (frontend). Some of this is intentional (server-resolved Excel-cell tooltips), but the
   enum/status label set is a genuine inconsistency. **Risk: 🟡 Medium** (maintainability / duplicate source of truth).

2. **Error-code naming only partially preserves the legacy scheme.** Legacy is strictly `WBE####`. The refactor
   reuses some numeric codes verbatim (good for traceability: `WBE1009/1190/1199/1153`, `WBW1138/1152`) but also
   introduces `WBE_SNAKE_CASE` families with no legacy counterpart. This is **documented and deliberate** (CTO
   directive retiring legacy naming) but means a legacy `WbMessageCode`↔refactor mapping is non-mechanical.
   **Risk: 🟢 Low** (documented; traceability handled in migration docs).

3. **Messages are no longer runtime-editable.** Legacy ops could change any message's text/translation in the
   Jex Studio DB registry without a deploy. The refactor bundles all text in the JAR (`messages*.properties`).
   **Risk: 🟢 Low** (acceptable for R&D; flag before any prod operability expectation).

4. **Hardcoded secret default in source.** `wecms.crypto.cert-key` ships a real base64 AES key as its `@Value`
   default in `BankCertCryptoService`. Legacy held cert/crypto material in `.user.xml` (externalized, if plaintext).
   Every other refactor secret reads from an env var. **Risk: 🟡 Medium** (security; only acceptable for local R&D).

5. **Config keys bound from code-defaults only (invisible in YAML).** `wecms.nxisas.*`, `wecms.oat.seed-decrypt.enabled`,
   `wecms.internal-transfer.routing.{woori,shinhan}-bank-cd`, `wecms.crypto.cert-key` are not declared in any
   profile file — they bind from in-source defaults. Legacy exposed integration knobs in `.user.xml`.
   **Risk: 🟢 Low** (discoverability only).

---

## 6. Missing legacy-compatible files / keys

**Files present in legacy with no refactor equivalent:**

| Legacy artifact | Refactor status |
|---|---|
| `jex.wabooks_new[.real/.test].user.xml` (env + secrets) | Replaced by Spring profiles + env vars (intentional) |
| Per-country folders `VI/`, `KM/` | **None** — refactor is VN-only by scope; **no Cambodia/KH config path** |
| Jex Studio ML registry (DB) for screen/field labels | Moved to frontend `wecms-web/locales/*.json` |
| DB `*_LANG_DETL` code-table label tables (consumption) | Replaced by enum `i18nKey()` + frontend `t(key)` |
| `WbMessageCode.java` enum (hundreds of codes) | Only the **subset for implemented modules** translated into `messages.properties` |
| `dictionary*.properties` (5 files) | **None** (was a partial fallback artifact — safe to omit) |
| SMTP/email config (`.user.xml` AWS SES) | **Not ported** — no `spring.mail`, notification module removed |

**Message/code keys in legacy but not in refactor:** the entire `WSE*` system-error family, the bulk of
`WBW*` warnings, all `WBC*` confirmations, and most `WBI*` info messages have **no refactor counterpart** — expected,
since the refactor implements a subset of modules. This is **scope-driven, not a defect**, but means the refactor's
`messages.properties` is **not** a superset of the legacy code universe; porting a new module must re-translate its
legacy `WbMessageCode` entries.

**`km` (Khmer) coverage gap:** legacy supports EN/KO/KM/VI uniformly via the DB registry. The refactor ships a
`messages_km.properties` but it is the **smallest** overlay (≈34 keys) and the refactor's enum policy explicitly
omits `km` for several namespaces (`bank.acct_type.*`, etc.). Parity with legacy KH coverage is **incomplete** —
acceptable for VN-focused R&D, flag if Cambodia is ever in scope.

---

## 7. Risk level

| Finding | Risk |
|---|---|
| Spring-idiomatic structure instead of legacy XML/DB (overall divergence) | 🟢 Low — deliberate, documented, generally an improvement |
| Code-to-label mappings in backend `messages.properties` vs FE policy / legacy DB | 🟡 Medium |
| **Banking cut-off times & business toggles moved from DB to static YAML** | 🟡 Medium — see note |
| Hardcoded `wecms.crypto.cert-key` default in source | 🟡 Medium — security |
| Messages not runtime-editable (lost DB registry capability) | 🟢 Low |
| Error-code naming partial-preservation (`WBE_SNAKE` vs `WBE####`) | 🟢 Low — documented |
| No Cambodia/KH config path; thin `km` coverage | 🟢 Low — out of current scope |
| Email/SMTP not ported | 🟢 Low — out of scope (notifications removed) |

> **Note on the cut-off Medium:** legacy stores banking cut-off windows in `SYS_ENV_SET_INFM` /
> `PymnHelper.getCutoffConfig` (DB-tunable per tenant, no redeploy). The refactor hardcodes them in
> `application-local.yml` / `application-sit.yml` (`wecms.internal-transfer.cutoff.{shinhan,woori}.{napas,citad,swift}`).
> This is a **structural drift from legacy** (file vs DB) and a parity concern: a cut-off change that legacy ops
> handled as data now requires a config edit + restart, and the values are not per-tenant. Flag against the
> migration-safety principle in the root CLAUDE.md.

**Overall: 🟡 Medium.** No correctness-breaking High items. The refactor consciously does **not** follow the
legacy file/key structure, which is sanctioned by documented directives. The Medium items are operability /
parity / security hygiene concerns, not blockers.

---

## 8. Recommended cleanup plan (analysis only — nothing executed)

> Read-only recommendations. None of these are applied here; each needs PO sign-off before action.

1. **Decide a single home for code-to-label text.** Either (a) move the enum/status label entries
   (`payment.appr_stts.*`, `settings.*.type.*`, `settings.vas_account.type/status.*`) out of backend
   `messages.properties` into `wecms-web/locales/*.json` per the refactor's own policy, or (b) formally amend the
   policy to acknowledge "server-resolved label keys" as a sanctioned category. Document which keys are
   intentionally server-resolved (Excel tooltips) vs accidental.
2. **Externalize the crypto key.** Change `wecms.crypto.cert-key` to an env var with **no usable default**
   (fail-fast like JWT/coocon in uat/prod); keep only an obviously-fake local placeholder. *(Code change — defer to a backend MR.)*
3. **Reconsider cut-off config location.** If banking cut-offs must stay tunable per legacy behavior, source them
   from a DB table (`SYS_ENV_SET_INFM` equivalent already in the inventory) rather than static YAML, preserving
   the legacy DB-driven model and migration safety. Escalate to PO (matches the root CLAUDE.md migration-safety rule).
4. **Surface the code-default-only keys in YAML.** Add `wecms.nxisas.*`, `wecms.oat.seed-decrypt.enabled`,
   `wecms.internal-transfer.routing.*` to `application.yml` (with their current defaults + comments) for discoverability.
5. **Maintain a legacy↔refactor code map.** Since naming diverges, keep a `WbMessageCode` → `messages.properties`
   mapping per module in `docs/migration/` so a future ported module can re-translate its legacy codes mechanically.
6. **Correct the stale CLAUDE.md profile table** (`application-dev.yml` / `application-test.yml` do not exist) and
   record the legacy tier mapping (DEVL→local/sit, TEST→uat, REAL→prod) for operator clarity.
7. **Track Cambodia/KH + full `km` coverage** as an explicit out-of-scope item so the EN/KO/KM/VI parity gap with
   legacy is a known decision, not an oversight.

---

*End of audit — read-only. No legacy or refactor files were modified; no migrations created.*
