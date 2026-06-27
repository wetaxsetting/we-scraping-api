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

## Intentionally OFF on SIT/UAT (leave unset / false — F-09 gating)

`WECMS_EFS_ENABLED`, `WECMS_SFTP_ENABLED`, `WECMS_DISPATCH_AUTO_RECEIVE`, `WECMS_DISPATCH_REALTIME_DISPATCH`, `WECMS_DISPATCH_RECIPIENT_INQUIRY`, `WECMS_DISPATCH_RESULT_CHECK_CRON` (default `-`), `WECMS_CALLBACK_ALLOWED_IPS`. Enabling dispatch requires the F-09 hardening first (see the risk register).

## Quick verify

```bash
echo "$SPRING_PROFILES_ACTIVE"   # expect: sit
env | grep -E 'WECMS_JWT_SECRET|WECMS_DB_PASSWORD|WECMS_COOCON_(API|SMART_API)_KEY|WECMS_CRYPTO_LEGACY_CERT_SEED'
grep -nE "secret|password|api-key|smart-api-key" src/main/resources/application-sit.yml   # only ${VAR} forms
```
