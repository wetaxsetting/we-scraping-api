# Required Environment Secrets — wecms-api

**System:** Refactor (wecms-api). **Date:** 2026-06-24.
**Purpose:** the authoritative list of environment variables a non-local profile (`sit` / `uat` / `prod`) must have set. Tracked YAML carries **no** secret defaults (F-05), so a missing required var fails the app fast at boot.

> **Drift note (2026-06-24):** several `application*.yml` comments reference `docs/security/required-environment-secrets.md`. That file did not exist; this document fills the referenced gap. No code/YAML was changed to add it.

Companion: [ops-secret-rotation-handoff.md](ops-secret-rotation-handoff.md) · [SIT checklist §B](../deployment/sit-security-deployment-checklist.md).

---

## Required (boot fails fast if unset on a non-local profile)

| Variable | Used by | Constraint |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | profile selection | Must be `sit`/`uat`/`prod`. Unset → silently runs `local` (fail-open, `application.yml:16-27`). |
| `WECMS_JWT_SECRET` | `wecms.jwt.secret` → `JwtTokenProvider` | ≥ 32 bytes (≥ 64 → HS512). Must not equal the blocklisted dev placeholder. `openssl rand -hex 64`. |
| `WECMS_DB_PASSWORD` | `spring.datasource.password` | Rotated `wecmsdev_owner` password (not the historical value). |
| `WECMS_COOCON_API_KEY` | `wecms.coocon.api-key` | Rotated Coocon key. Required for scraping/FX (kept off where not used). |
| `WECMS_COOCON_SMART_API_KEY` | `wecms.coocon.smart-api-key` | Rotated Coocon smart-gateway key. |

## Compatibility constant (set, but NOT a rotatable secret)

| Variable | Used by | Constraint |
|---|---|---|
| `WECMS_CRYPTO_LEGACY_CERT_SEED` | `wecms.crypto.legacy-cert-seed` → `BankCertCryptoService` | **Must equal the legacy seed `wabooksvn`** so cert credentials read/write byte-identically to the live JEX `public.encrypt()`/`decrypt()`. Do NOT randomize (F-08). Default is `wabooksvn`; pin it explicitly in env for clarity. |

## Recommended (non-secret, env-specific)

| Variable | Used by | Notes |
|---|---|---|
| `WECMS_CORS_ALLOWED_ORIGINS` | `wecms.cors.allowed-origins` | Exact frontend origin(s); never `*`. Defaults to localhost — wrong for a deployed env. |
| `WECMS_*_STORAGE_ROOT` (per feature) | file storage roots | Point at the real host storage path. |

## Dispatch / EFS / SFTP / alert — BOOT-REQUIRED (must be DEFINED, even when OFF)

> **Changed 2026-07-06 (Option-1 "full move"):** the tracked `application*.yml` no longer carries inline defaults for any dispatch variable — every dispatch placeholder is now a **bare `${VAR}`**. Consequently these variables are **boot-required on every non-local profile**: an *unset* variable → unresolved placeholder → **fail-fast at startup** (the `EfsProperties`/`SftpProperties`/`CallbackProperties` `@ConfigurationProperties` beans and the `@Value` dispatch flags all bind at boot). The feature stays **OFF** — but "off" now means **explicitly defined with the OFF value**, not "left unset".

**UAT / PROD have no `.env.uat` / `.env.prod` file** — they resolve only from `setenv.sh` / OS env. Therefore **`setenv.sh` MUST `export` all 20 variables below before deployment**, or the app will not boot. (local / sit resolve them from the gitignored `.env.local` / `.env.sit`, which already carry all 20.)

**An empty value still counts as defined** — `export WECMS_EFS_BASE_URL=` resolves to `""` and is fine; *omitting* the line is what fails. Set every variable, even the blank ones.

| Variable | OFF value to set | Bound by |
|---|---|---|
| `WECMS_DISPATCH_AUTO_RECEIVE` | `false` | `@Value` (`DispatchAutoReceiveRegistrar`) |
| `WECMS_DISPATCH_REALTIME_DISPATCH` | `false` | `@Value` (`RealtimeDispatchRegistrar`) |
| `WECMS_DISPATCH_RECIPIENT_INQUIRY` | `false` | `@Value` (`FirmBankingInquiryService`) |
| `WECMS_DISPATCH_RESULT_CHECK_CRON` | `-` | `@Scheduled` cron (`ResultCheckJob`). **Must be `-` when disabled** (never-fire); an empty/invalid cron throws at scheduler init. |
| `WECMS_DISPATCH_ALERT_ENABLED` | `false` | `@Value` (`OpsAlertService`) |
| `WECMS_CALLBACK_ALLOWED_IPS` | *(empty)* | `CallbackProperties` |
| `WECMS_EFS_ENABLED` | `false` | `EfsProperties` |
| `WECMS_EFS_BASE_URL` | *(empty)* | `EfsProperties` |
| `WECMS_EFS_PARTNER_ID` | *(empty)* | `EfsProperties` |
| `WECMS_SFTP_ENABLED` | `false` | `SftpProperties` |
| `WECMS_SFTP_HOST` | *(empty)* | `SftpProperties` |
| `WECMS_SFTP_PORT` | `3300` | `SftpProperties`. Use the host/example value `3300` (the legacy VAN SFTP port), **not** the retired YAML fallback `22`. |
| `WECMS_SFTP_USERNAME` | *(empty)* | `SftpProperties` |
| `WECMS_SFTP_KEY_PATH` | *(empty)* | `SftpProperties` |
| `WECMS_SFTP_PASSWORD` | *(empty)* | `SftpProperties` |
| `WECMS_SFTP_LOCAL_UPLOAD` | `/DATA/src-wecms/wecms-backend/upload-files/send` | `SftpProperties`. Local staging dir for the 00301 file before upload. Placed inside the upload root as a sibling of the `<BIZ_KEY>/` folders. |
| `WECMS_SFTP_REMOTE_UPLOAD` | `sdata` | `SftpProperties` (legacy remote upload dir) |
| `WECMS_SFTP_LOCAL_DOWNLOAD` | `/DATA/src-wecms/wecms-backend/upload-files/receive` | `SftpProperties`. Local staging dir for the 01302 result after download. Sibling of `send` under the upload root. |
| `WECMS_SFTP_REMOTE_DOWNLOAD` | `rdata` | `SftpProperties` (legacy remote download dir) |
| `WECMS_SFTP_REMOTE_MOVE` | `rdata/success` | `SftpProperties` (legacy processed dir) |

Enabling dispatch (flipping the `*_ENABLED` / `AUTO_RECEIVE` / `REALTIME_DISPATCH` / a real `RESULT_CHECK_CRON`) still requires the **F-09 callback hardening** first (see the risk register) — the OFF values above are the safe deploy baseline.

> **OPERATIONAL NOTE — SFTP staging vs tenant folders.** `WECMS_SFTP_LOCAL_UPLOAD` /
> `WECMS_SFTP_LOCAL_DOWNLOAD` resolve to `send` / `receive` folders **inside** the web-upload
> root (`WECMS_*_STORAGE_ROOT = /DATA/src-wecms/wecms-backend/upload-files`). They hold
> transient batch files and are **NOT BIZ_KEY folders**. Any backup / migration / tenant-scan
> tooling that iterates `upload-files/*` must **exclude `send` and `receive`** and must never
> treat them as tenant directories. The application never enumerates the upload root (verified:
> no `Files.list`/`Files.walk`/`DirectoryStream`/`listFiles` over the storage tree), so this is a
> forward-safety constraint for ops scripts, not a current code dependency.

## Quick verify

```bash
echo "$SPRING_PROFILES_ACTIVE"   # expect: sit
env | grep -E 'WECMS_JWT_SECRET|WECMS_DB_PASSWORD|WECMS_COOCON_(API|SMART_API)_KEY|WECMS_CRYPTO_LEGACY_CERT_SEED'
grep -nE "secret|password|api-key|smart-api-key" src/main/resources/application-sit.yml   # only ${VAR} forms
# Dispatch is now bare-placeholder (Option-1). On UAT/PROD confirm all 20 are DEFINED (even if empty):
for v in WECMS_DISPATCH_AUTO_RECEIVE WECMS_DISPATCH_REALTIME_DISPATCH WECMS_DISPATCH_RECIPIENT_INQUIRY \
         WECMS_DISPATCH_RESULT_CHECK_CRON WECMS_DISPATCH_ALERT_ENABLED WECMS_CALLBACK_ALLOWED_IPS \
         WECMS_EFS_ENABLED WECMS_EFS_BASE_URL WECMS_EFS_PARTNER_ID WECMS_SFTP_ENABLED WECMS_SFTP_HOST \
         WECMS_SFTP_PORT WECMS_SFTP_USERNAME WECMS_SFTP_KEY_PATH WECMS_SFTP_PASSWORD WECMS_SFTP_LOCAL_UPLOAD \
         WECMS_SFTP_REMOTE_UPLOAD WECMS_SFTP_LOCAL_DOWNLOAD WECMS_SFTP_REMOTE_DOWNLOAD WECMS_SFTP_REMOTE_MOVE; do
  [ -z "${!v+x}" ] && echo "MISSING (boot will fail): $v"; done
```
