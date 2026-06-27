# SIT Security Deployment Checklist — wecms-api

**System:** Refactor (wecms-api). **Target:** SIT (shared dev DB `172.168.5.172:5432/wecmsdevdb`, VPN-required).
**Use:** run top-to-bottom for every SIT deploy of the security remediation. Tick each box on the deployment ticket.
**Companion docs:** [security-remediation-closure-2026-06-24.md](../security/security-remediation-closure-2026-06-24.md) · [required-environment-secrets.md](../security/required-environment-secrets.md) · [ops-secret-rotation-handoff.md](../security/ops-secret-rotation-handoff.md).

> The smoke-test commands below assume `BASE=https://wecms-api.kosign.dev` (adjust to the SIT host) and a tool like `curl`. They are **read-only probes** — none mutate data except the login-lockout test, which only writes `USER_LOGIN_HIS` FAIL rows against a disposable test account.

---

## A. Pre-deploy checks

- [ ] Build artifact is from a commit that includes F-01–F-07 + F-10 (closure report §3).
- [ ] A green **full** `./gradlew test` CI run is attached to the ticket (boot tests need the VPN DB — run in CI, not by hand on the host).
- [ ] No code/SQL/YAML drift since the closure report (run the §I static sweep below).
- [ ] DB schema on the SIT instance is pre-migrated (Flyway disabled; `ddl-auto: validate` will fail boot on any missing column).
- [ ] The Coocon keys in git history have been rotated **or** a rotation ticket is open and dispatch/scraping stay off (see ops handoff).

## B. Required environment variables

`application-sit.yml` carries **no** secret defaults — every value below must be present on the host (`setenv.sh` / container env). Boot fails fast if a required one is missing.

- [ ] **`SPRING_PROFILES_ACTIVE=sit`** ← **critical.** If unset, the app silently activates `local` → shared DEV DB + the gitignored local dev secret (fail-open, `application.yml:16-27`). Confirm it is exported before launch.
- [ ] `WECMS_JWT_SECRET` — 32+ bytes (64+ → HS512). Generate `openssl rand -hex 64`. Must **not** equal the known dev placeholder (blocklisted; boot fails if it does).
- [ ] `WECMS_DB_PASSWORD` — `wecmsdev_owner` password (rotated value, not the historically-committed one).
- [ ] `WECMS_COOCON_API_KEY`, `WECMS_COOCON_SMART_API_KEY` — rotated Coocon keys (bare `${VAR}` in tracked yml).
- [ ] `WECMS_CRYPTO_LEGACY_CERT_SEED` — must equal the legacy seed (`wabooksvn`) for cert-credential coexistence; do **not** randomize it (F-08).
- [ ] `WECMS_CORS_ALLOWED_ORIGINS` — exact SIT frontend origin(s); never `*`.
- [ ] Dispatch/SFTP/EFS vars left **unset/false** (dispatch stays inert — F-09): `WECMS_EFS_ENABLED`, `WECMS_SFTP_ENABLED`, `WECMS_DISPATCH_*`, `WECMS_DISPATCH_RESULT_CHECK_CRON` (default `-`).

## C. Secret validation

- [ ] Tracked yml contains **no** secret literals: `grep -nE "secret|password|api-key|smart-api-key" src/main/resources/application*.yml` shows only `${VAR...}` placeholders (local yml is gitignored and excluded).
- [ ] `WECMS_JWT_SECRET` on the host is unique to SIT (not reused from local/DEV; not the blocklisted placeholder).
- [ ] Rotated Coocon keys differ from `r2L7U6KnB4T51VokPlkg` / `fxAeocJbuweNTgv5FBns` (the values in git history).

## D. Startup fail-fast checks

- [ ] App boots **only** with `SPRING_PROFILES_ACTIVE=sit`; a no-profile boot on the host is rejected by ops policy.
- [ ] Missing `WECMS_JWT_SECRET` → boot fails with the `JwtTokenProvider` message (verify in a dry run).
- [ ] Insecure placeholder as `WECMS_JWT_SECRET` → boot fails (blocklist).
- [ ] Missing `WECMS_DB_PASSWORD` / Coocon keys → boot fails fast (no silent dev fallback).
- [ ] `GET $BASE/actuator/health` → `200 {"status":"UP"}`.

## E. Functional smoke tests

- [ ] Login OK: `POST $BASE/api/v1/auth/login {user_id,user_pw}` for a valid SIT account → `200`, `status.error=false`, `access_token` present.
- [ ] `GET $BASE/api/v1/auth/me` with the token → `200`, correct `biz_key`/`auth_gr_cd`.
- [ ] A protected endpoint without a token → `401` via `JwtAuthenticationEntryPoint`.

## F. Security smoke tests

- [ ] **SVG rejection (F-02):** upload a `.svg` (or `image/svg+xml`) avatar/logo → rejected (`WBE2118`/`WBE2087`); `.png` of the same size succeeds.
- [ ] **Logout token rejection (F-03):** login → call `me` (200) → `POST /api/v1/auth/logout` with the token → re-call `me` with the **same** token → **401** (revoked `jti`).
- [ ] **Locked-user rejection (F-10 + flag gate):** a `LOCK_YN='Y'` account → login → `WBE1011`.
- [ ] **Auto-lock (F-10):** 5 consecutive wrong-password logins for a disposable test account → 6th attempt (even with the correct password) → `WBE1011`. (Unlock via DBA `LOCK_YN='N'` afterwards.)
- [ ] **Rate limiting (F-10):** rapid-fire `POST /api/v1/auth/login` from one IP beyond the window limit → throttled (`429` / `WBE_RATE_LIMIT`). Repeat for the credential `…/reveal` endpoint.
- [ ] **Reveal audit (F-07):** one successful `…/reveal` → a new `AUDIT_LOGS_INFM` row exists for the actor.

## G. Security headers verification

`curl -sI $BASE/api/v1/auth/login` (or any response) shows:

- [ ] `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'`
- [ ] `X-Frame-Options: DENY`
- [ ] `Referrer-Policy: no-referrer`
- [ ] `Strict-Transport-Security: max-age=31536000; includeSubDomains` (HTTPS only — present when SIT is behind TLS; absent on plain HTTP by design)
- [ ] `X-Content-Type-Options: nosniff` (Spring default, retained)

## H. Logging verification (F-06)

- [ ] SIT logging is `com.kosign.wecms: INFO`, `org.springframework.security: WARN` (not DEBUG).
- [ ] **No sensitive request/response bodies** in the logs — login passwords, `…/reveal` plaintext, and bank/GDT credentials never appear. Tail the log during the §F smoke tests and confirm.
- [ ] **No SQL bind-parameter TRACE** — `org.hibernate.SQL`, `org.springframework.jdbc.core`, `org.hibernate.orm.jdbc.bind` are all at `WARN`+; `show-sql:false`.
- [ ] Every error line carries a `traceId` (MDC) and matches the `X-Trace-Id` response header.

## I. Static doc/config sweep (drift gate — report, do not fix here)

- [ ] `grep -rnE "always writes BCrypt|transparently upgrades|AES-GCM (ciphertext|base64)|advisory until|wired up yet" src/main/java` → 0 matches.
- [ ] No active doc claims SVG allowed / logout no-op / lockout-or-rate-limit missing (see this package's final verification).
- [ ] If any drift is found, log it on the ticket — do not hot-patch during deploy.

## J. Rollback checks

- [ ] Previous artifact + its `setenv.sh` are retained and known-good.
- [ ] Rollback is a redeploy of the previous build with the **same** env vars — no DB rollback needed (no schema change in this remediation).
- [ ] In-memory revocation/rate-limit state is **expected to reset** on restart/rollback (bounded ≤ JWT TTL) — not a data-loss event; note it so support doesn't treat re-honoured tokens as a regression.

## K. Post-deploy verification

- [ ] Re-run §E + §F + §G + §H against the live SIT URL.
- [ ] Confirm `actuator/health` UP and a real login round-trips end-to-end from the SIT frontend.
- [ ] File the deploy outcome (pass/fail per section) on the ticket; link the closure report.
- [ ] Confirm dispatch is still inert (`efs.enabled=false`) — no `WGVN_EFS_*` traffic in logs.
