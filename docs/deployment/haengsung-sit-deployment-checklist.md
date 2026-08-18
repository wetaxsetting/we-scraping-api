# HAENGSUNG Oracle Integration — SIT Deployment Checklist

**Audience:** Ops / deployer (SIT Tomcat host).
**Target:** `wecms-api` WAR on external Tomcat, `SPRING_PROFILES_ACTIVE=sit`.
**Datasources:** WeCMS PostgreSQL (Primary, JPA/Flyway-validated) + HAENGSUNG Oracle (Secondary, JdbcTemplate-only, **optional feature**).
**Golden rule:** Oracle is turned off with `HAENGSUNG_ORACLE_ENABLED=false` (then the app boots PostgreSQL-only, no Oracle bean). ⚠️ **Updated 2026-06-29 — config changed to fail-fast:** `application-*.yml` now binds the HAENGSUNG vars as **bare `${…}` placeholders with no YAML defaults**. Consequently **all 10 `HAENGSUNG_*` keys must be DEFINED in the environment (`.env.*` / setenv.sh) even when the feature is OFF** — omitting any one makes the *whole app* fail to start with `Could not resolve placeholder 'HAENGSUNG_…'`. "OFF by default" now means "set `ENABLED=false`", **not** "omit the vars". No credential is ever committed or logged.

Config source on the real SIT box is **`$CATALINA_BASE/bin/setenv.sh` OS environment variables** — NOT `.env.sit` (per memory `reference_sit_server_config_setenv`). `application-sit.yml` carries only bare `${WECMS_*}` / inherited `${HAENGSUNG_*}` placeholders.

---

## 1. Required environment variables (setenv.sh)

### 1a. Already required by the existing SIT app (unchanged)
`WECMS_DB_URL`, `WECMS_DB_USERNAME`, `WECMS_DB_PASSWORD`, `WECMS_JWT_SECRET`, `WECMS_CORS_ALLOWED_ORIGINS`, `WECMS_COOCON_*`, `WECMS_OSMC_*`, `WECMS_*_STORAGE_ROOT`, `WECMS_SHINHAN_BANK_CD`, `WECMS_WOORI_BANK_CD`, `WECMS_EXCHANGE_RATE_BATCH_ENABLED`, dispatch/SFTP/EFS flags. (See `docs/security/required-environment-secrets.md`.)

### 1b. HAENGSUNG Oracle — datasource

> ⚠️ **Every key below must be DEFINED in each environment** — the YAML binds them bare (no defaults). When the feature is OFF the value may be blank (e.g. `HAENGSUNG_ORACLE_URL=`); when `ENABLED=true` the URL/username/password must be non-blank (else `HaengsungOracleProperties` fail-fast). **Omitting a key entirely → startup crash**, even when disabled.

| Variable | Key present | Value required | Example | Notes |
|---|---|---|---|---|
| `HAENGSUNG_ORACLE_ENABLED` | always | always (`true`/`false`) | `false` → `true` | Master switch. `false` = feature inert (beans absent). |
| `HAENGSUNG_ORACLE_URL` | always | when `ENABLED=true` | `jdbc:oracle:thin:@//<host>:1521/<service>` | **Secret-adjacent. Never commit.** |
| `HAENGSUNG_ORACLE_USERNAME` | always | when `ENABLED=true` | `<oracle-user>` | |
| `HAENGSUNG_ORACLE_PASSWORD` | always | when `ENABLED=true` | `<secret>` | **Secret.** Never logged. |

### 1c. HAENGSUNG schedulers — keys mandatory; values default to OFF

| Variable | Default | Notes |
|---|---|---|
| `HAENGSUNG_SCHEDULED_IMPORT_ENABLED` | `false` | Step 3 payment import batch. |
| `HAENGSUNG_SCHEDULED_IMPORT_CRON` | `0 */5 * * * *` | Spring cron, ICT (`Asia/Ho_Chi_Minh`). |
| `HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED` | `false` | Step 6 FX→Oracle sync. |
| `HAENGSUNG_EXCHANGE_RATE_SYNC_CRON` | `0 */5 * * * *` | |
| `HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED` | `false` | Step 7 account-history + card sync. |
| `HAENGSUNG_CARD_ACCOUNT_SYNC_CRON` | `0 */5 * * * *` | |

> Hikari tuning (`maxPoolSize`, `minIdle`, `connectionTimeoutMs`, `keepaliveMs`, `validationTimeoutMs`) is **not** wired to env vars; it uses the in-code defaults (5 / 1 / 30000 / 120000 / 5000 ms). Override only via relaxed-binding env (`HAENGSUNG_ORACLE_MAXPOOLSIZE`, etc.) if a DBA recommends it — not required for SIT.

---

## 2. setenv.sh example (placeholders only — fill real values on host)

```sh
# $CATALINA_BASE/bin/setenv.sh   — chmod 600, owned by the tomcat user.
# REAL credentials live ONLY here on the SIT host. Never commit this file.

export SPRING_PROFILES_ACTIVE=sit

# --- existing SIT vars (DB / JWT / CORS / Coocon / storage) already set; unchanged ---

# --- HAENGSUNG Oracle (Phase: foundation + manual Refresh) ---
export HAENGSUNG_ORACLE_ENABLED=true
export HAENGSUNG_ORACLE_URL='jdbc:oracle:thin:@//ORACLE_HOST:1521/ORACLE_SERVICE'
export HAENGSUNG_ORACLE_USERNAME='WECMS'
export HAENGSUNG_ORACLE_PASSWORD='__SET_ON_HOST__'

# --- HAENGSUNG schedulers: keep OFF for the first deploy; enable one at a time later ---
export HAENGSUNG_SCHEDULED_IMPORT_ENABLED=false
export HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED=false
export HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED=false
# crons inherit the 5-minute default; set only to override.
```

After editing `setenv.sh`: `chmod 600`, verify it is owned by the Tomcat service user, and confirm it is **not** inside the webapp / WAR.

---

## 3. Pre-deploy verification (before bouncing Tomcat)

- [ ] WAR built from the reviewed commit (`./gradlew clean build`; `war` plugin → deployable WAR, embedded Tomcat is `providedRuntime`).
- [ ] `ojdbc11` is on the runtime classpath inside the WAR (`WEB-INF/lib/ojdbc11-*.jar`) — exactly one Oracle driver, no duplicate.
- [ ] `SPRING_PROFILES_ACTIVE=sit` is exported (never let SIT fall through to the `local` default → dev DB).
- [ ] PostgreSQL `WECMS_DB_*` reachable from the SIT host.
- [ ] Target Postgres schema already migrated (Flyway is removed; `ddl-auto=validate` only checks mappings).
- [ ] If any HAENGSUNG scheduler will be enabled now or later: the **`shedlock` table exists** in the WeCMS Postgres DB (shared with the FX batch). If the FX batch already runs on SIT, it exists.
- [ ] Oracle account is **read-mostly on the interface tables** — the integration reads `TB_TRNS_MAST`/`TB_TRNS_DETAIL` and writes status/sync columns on the 5 `TB_*` interface tables only; it never issues DDL and creates no objects.
- [ ] No `.env.sit` with stale values present in Tomcat's working dir (OS env from setenv.sh wins over `.env.sit`, but remove any stale copy to avoid confusion).

---

## 4. Deployment steps

1. Place `setenv.sh` with the env vars above (Oracle ON, schedulers OFF) on the SIT host; `chmod 600`.
2. Deploy the WAR to Tomcat `webapps/` (or the configured context).
3. Start / restart Tomcat (see §5).
4. Watch `catalina.out` for the startup markers in §6.
5. Run the smoke test (§7) and Oracle verification (§8).

## 5. Restart steps

```sh
$CATALINA_HOME/bin/shutdown.sh        # graceful stop
# confirm the JVM is gone:
ps -ef | grep -i tomcat | grep -v grep
$CATALINA_HOME/bin/startup.sh         # reloads setenv.sh → new env vars take effect
tail -f $CATALINA_BASE/logs/catalina.out
```

Env var changes in `setenv.sh` require a **full Tomcat restart** — they are read at JVM launch.

## 6. Startup smoke (log expectations)

- App reaches `Started WecmsApiMainApplication` (no fatal stack trace).
- **Oracle reachable:** `HAENGSUNG Oracle connectivity OK (SELECT 1 FROM dual).`
- **Oracle unreachable:** `HAENGSUNG Oracle is enabled but not reachable at startup. The application will continue; ERP flows retry on demand.` — **this is NOT a startup failure**; the app keeps serving and PostgreSQL is unaffected.
- If `HAENGSUNG_ORACLE_ENABLED=true` but a credential is missing: startup **fails fast** with `HAENGSUNG Oracle integration is enabled ... but a required setting is missing: haengsung.oracle.url (HAENGSUNG_ORACLE_URL) ...` (names the setting, never the value). Fix the env var and restart.
- Confirm **no** Oracle URL / username / password appears anywhere in the logs.

## 7. Functional smoke test (manual Refresh)

1. Log in as a HAENGSUNG user (`PRDT_TYPE='H'`, tenant `VN_VNM0004_000014`).
2. Open a payment scheduled-list screen (Vendor / Expense / Payroll / Overseas) → the **Refresh** button shows (non-H tenants show Excel Upload).
3. Click Refresh for a date range with known Oracle masters.
4. Expect a success dialog (`Imported N batch(es) (M transaction(s))`) and the list reloads with imported drafts.
5. Negative check: with `HAENGSUNG_ORACLE_ENABLED=false`, the same call returns `WBE_HS_REFRESH_DISABLED` (feature cleanly off) — confirms the gate.

## 8. Oracle verification (read-only)

- From the Oracle side, confirm the 5 interface tables are present and the account can read them: `TB_TRNS_MAST`, `TB_TRNS_DETAIL`, `TB_FCY_EXRATE`, `TB_ACCT_HISTORY`, `TB_CARD_APPROVAL`.
- After a Refresh, the imported masters' `ERP_RCV_FLAG='Y'` (and detail `END_GB='2'`) in `TB_TRNS_MAST`/`TB_TRNS_DETAIL`.
- Cross-check the WeCMS Postgres side: new `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` drafts + an `ERP_TRNS_HSTR` audit row.

## 9. Scheduler enable order (one at a time, after manual Refresh is verified)

Enable each independently, restart, watch one full cron cycle, then proceed:

1. `HAENGSUNG_SCHEDULED_IMPORT_ENABLED=true` → confirm `feature=haengsung-scheduled-import start/done` logs; verify imports match a manual Refresh.
2. `HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED=true` → confirm FX rows land in Oracle `TB_FCY_EXRATE` and WeCMS `BANK_EXCH_RATE_INFM.ERP_SND_YN='Y'`.
3. `HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED=true` → confirm `TB_ACCT_HISTORY` / `TB_CARD_APPROVAL` inserts + WeCMS `ERP_SND_YN='Y'`. **Confirm card-sync source-row parity (audit item F-1) before trusting this in volume.**

> Each scheduler also self-no-ops if `HAENGSUNG_ORACLE_ENABLED=false` (logs `skipped — Oracle feature disabled`). The single shared scheduler thread (`spring.task.scheduling.pool.size=1`) is shared with the FX batch — enabling a HS sync while Oracle is unreachable can briefly delay other scheduled jobs (bounded by the 30 s connection timeout); enable only after §6 shows Oracle reachable.

## 10. Rollback procedure

- **Fastest (disable feature, keep build):** set `HAENGSUNG_ORACLE_ENABLED=false` (and any scheduler flag back to `false`) in `setenv.sh` → restart Tomcat. All Oracle beans vanish; app runs PostgreSQL-only. No data change on the WeCMS side.
- **Disable a single scheduler:** set just that `*_ENABLED=false` → restart.
- **Full rollback:** redeploy the previous WAR + revert `setenv.sh`. PostgreSQL schema is untouched by the Oracle integration, so no DB rollback is needed.
- Oracle-side: the integration writes only status/sync flag columns; there is no destructive operation to undo. If a sync wrote rows in error, coordinate with the HAENGSUNG DBA (out of band) — the refactor performs no DDL.

## 11. Production recommendation

- Deploy to SIT first with **Oracle ON, all schedulers OFF**; validate manual Refresh end-to-end.
- Enable schedulers one at a time (§9) and observe at least one full cycle each.
- Before UAT/PROD: rotate any historically-committed keys (Coocon, etc.), keep `HAENGSUNG_ORACLE_*` only in host env (never in a committed file), and confirm Oracle network reachability + the Oracle account's least-privilege grants (read on the source tables, update/insert only on the documented interface columns).
- For multi-instance deploys, ShedLock already serialises the schedulers via the Postgres `shedlock` table — confirm the table exists in the PROD DB before enabling.
- Keep `gdt-verify-enabled=false` and dispatch/SFTP/EFS OFF unless separately provisioned — they are independent of HAENGSUNG Oracle.

---

*Companion docs:* [haengsung-oracle-integration-roadmap.md](../planning/haengsung-oracle-integration-roadmap.md) · [haengsung-oracle-integration-sit-verification.md](../audits/legacy-parity/haengsung-oracle-integration-sit-verification.md) · [haengsung-oracle-integration-final-audit.md](../audits/legacy-parity/haengsung-oracle-integration-final-audit.md)
