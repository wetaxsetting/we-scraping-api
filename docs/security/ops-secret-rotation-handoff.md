# Ops Secret Rotation Handoff — wecms-api

**Audience:** Infra / Ops. **System:** Refactor (wecms-api). **Date:** 2026-06-24.
**Why:** F-05 removed committed secret *defaults* from tracked config, but the previously-committed values **remain in git history** and must be rotated by Ops. Code can fail-fast on a missing secret; it cannot un-leak a value already pushed to GitLab.

Companion: [required-environment-secrets.md](required-environment-secrets.md) · [SIT checklist §B/§C](../deployment/sit-security-deployment-checklist.md).

---

## 1. Secrets historically exposed (in git history)

| Secret | Exposed value (in history) | Where it was committed | Action |
|---|---|---|---|
| JWT dev signing secret | `local-rnd-dev-only-insecure-jwt-signing-secret-override-in-prod-abcdef123456` | base `application.yml` (commit `a55344a`) | **Blocklisted in code** — can never sign a token again. Dev-only; rotate for hygiene, not urgency. |
| Coocon `api-key` | `r2L7U6KnB4T51VokPlkg` | tracked yml across ≥6 commits (from `dee1274`) | **Rotate with the vendor.** |
| Coocon `smart-api-key` | `fxAeocJbuweNTgv5FBns` | tracked yml across ≥7 commits | **Rotate with the vendor.** |

> The shared-DB password (`wecmsdev_owner`) default lives only in the **gitignored** `application-local.yml`, so it is **not** in git history via tracked files. Treat it as sensitive regardless and use a rotated value on SIT/UAT/PROD.

## 2. Active tracked defaults removed (F-05 — already done in code)

These are now bare `${VAR}` placeholders in tracked config (fail-fast when unset):

- `wecms.jwt.secret = ${WECMS_JWT_SECRET}` — `application.yml:101`, `application-sit.yml:68`
- `spring.datasource.password = ${WECMS_DB_PASSWORD}` — `application-sit.yml:43`
- `wecms.coocon.api-key = ${WECMS_COOCON_API_KEY}` — `application.yml:274`, `application-sit.yml:281`
- `wecms.coocon.smart-api-key = ${WECMS_COOCON_SMART_API_KEY}` — `application.yml:276`, `application-sit.yml:283`
- `wecms.osmc.api-key = ${WECMS_OSMC_API_KEY}` — `application.yml:300`

## 3. Secrets Ops MUST rotate

| Priority | Secret | Owner | Notes |
|---|---|---|---|
| **High** | Coocon `api-key` + `smart-api-key` | Infra + Coocon vendor | Valid financial-integration credentials sitting in git history. Rotate before PROD; before SIT if SIT reaches a real Coocon gateway. |
| **Medium** | `WECMS_DB_PASSWORD` (`wecmsdev_owner`) | DBA | Rotate the shared-DB credential; update every consumer (`setenv.sh`, `.env`, MCP config, `run-local`). |
| **Low** | JWT dev placeholder | — | Already blocklisted in code; rotate the dev value for hygiene. SIT/UAT/PROD already use unique per-env secrets. |

## 4. What the code now enforces

- **Fail-fast on missing/short JWT secret** and **blocklist of the known dev placeholder** — `JwtTokenProvider.java:38-50`.
- **No secret defaults** in SIT/UAT/PROD profiles — boot fails if a required `${VAR}` is unresolved.
- **Cert seed pinned to the legacy value** via `WECMS_CRYPTO_LEGACY_CERT_SEED` (default `wabooksvn`) — must stay equal to legacy for coexistence; this is a *compatibility constant*, not a rotatable secret.

## 5. What the code CANNOT enforce (Ops responsibility)

- Removing leaked values from **git history** (requires history rewrite / BFG, or — preferred — vendor-side rotation that invalidates the leaked keys).
- Setting `SPRING_PROFILES_ACTIVE` on the host (without it the app fails open to `local`).
- Provisioning rotated values into the host env / secret store.
- Confirming the rotated Coocon keys actually authenticate against the gateway.

## 6. SIT / UAT / PROD responsibilities

| Env | Profile | DB | Secrets | Dispatch |
|---|---|---|---|---|
| **Local** | `local` (gitignored yml) | shared dev DB | R&D defaults OK (gitignored) | off |
| **SIT** | `sit` | shared dev DB (real creds) | all `${VAR}` from host; rotated Coocon keys before any real gateway use | off |
| **UAT** | `uat` | per env | all `${VAR}` from host; rotated keys | off |
| **PROD** | `prod` | PROD | all `${VAR}`; **rotation MUST be complete**; pinned origins | off until F-09 hardened |

## 7. Verification checklist / commands

- [ ] `echo $SPRING_PROFILES_ACTIVE` → `sit` (or the target env) on the host.
- [ ] Tracked yml has no literals: `grep -nE "secret|password|api-key|smart-api-key" src/main/resources/application*.yml` → only `${VAR}` forms.
- [ ] Host env present: `env | grep -E "WECMS_JWT_SECRET|WECMS_DB_PASSWORD|WECMS_COOCON_API_KEY|WECMS_COOCON_SMART_API_KEY|WECMS_CRYPTO_LEGACY_CERT_SEED"` → all set.
- [ ] Rotated Coocon keys ≠ the history values (`r2L7U6KnB4T51VokPlkg` / `fxAeocJbuweNTgv5FBns`).
- [ ] `WECMS_JWT_SECRET` length ≥ 32 and ≠ the blocklisted placeholder.
- [ ] Dry-run boot with a missing secret confirms fail-fast (then restore).
- [ ] Vendor confirms the old Coocon keys are revoked.
