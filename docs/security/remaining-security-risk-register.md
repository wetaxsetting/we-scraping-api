# Remaining Security Risk Register — wecms-api

**System:** Refactor (wecms-api). **Date:** 2026-06-24.
**Scope:** the open items left after the F-01–F-07/F-10 remediation closed (see [closure report](security-remediation-closure-2026-06-24.md)). Each item is sourced from the implementation; none is a regression.

Severity legend: 🟥 blocks PROD · 🟧 conditional / decision needed · 🟩 accepted (no action unless coexistence ends).

---

## R-1 · F-08 — Legacy AES-256-CBC cert cipher (zero-IV, fixed key) 🟩

- **What:** `BankCertCryptoService` is AES-256-CBC, all-zero IV, PKCS#5, key = `SHA-256(seed 'wabooksvn')`. Deterministic, no AEAD/MAC. Byte-identical to legacy pgcrypto `public.encrypt()`/`decrypt()`; golden-vector tested (`BankCertCryptoServiceTest`, 5/5).
- **Risk:** equal plaintexts → equal ciphertext; no tamper detection. Bounded by the coexistence requirement — it matches the production system already in use; the refactor introduces no new risk.
- **Owner:** PO + DBA. **Env scope:** all. **UAT impact:** none (works, tested). **PROD impact:** none independently (matches live JEX).
- **Required decision:** keep as-is while the JEX DB is shared; AES-GCM only via a coordinated re-encrypt-everything migration that cuts JEX off these columns first.
- **Timeline:** no action until a JEX coexistence-end cutover is scheduled. Optional now: source the seed from a secret store (already env-wired as `WECMS_CRYPTO_LEGACY_CERT_SEED`).

## R-2 · F-09 — Callback replay / empty IP allow-list / no RQ_DTIME freshness 🟧→🟥 (before dispatch)

- **What:** EFS callbacks (`/api/v1/payment/dispatch/callbacks/**`) are `permitAll`; auth is the per-VAN VV-HMAC, which **is** verified before processing (`BulkExecutionResultService.java:119`, `BulkReceptionResultService.java:129`). Gaps: no RQ_DTIME freshness check (prefix stripped + discarded, `EfsCryptoService.java:97`), no nonce/replay cache, and the IP allow-list defaults empty → no-op (`CallbackProperties.java:43-44`). `trust-forwarded-for:true`.
- **Risk:** with dispatch enabled, a captured valid VV envelope could be replayed (re-execution bounded — not prevented — by the `PROC_STEP`/`PROC_STAT` state machine + same-day `TRAN_DT` lookup). Inert today: dispatch is **OFF in every profile** (`efs.enabled:false`, `sftp.enabled:false`, `result-check.cron:'-'`).
- **Owner:** PO + Coocon + Backend. **Env scope:** only envs with dispatch ON. **UAT impact:** none while dispatch stays off. **PROD impact:** 🟥 **blocks before any money-movement** (pre-deploy gate R-03).
- **Required decision/action before enabling dispatch:** populate `WECMS_CALLBACK_ALLOWED_IPS` (fail-closed); set `trust-forwarded-for` for the real proxy; add an RQ_DTIME freshness window + a TNO replay guard (backed by existing `TB_FIRM_BAT_API_TRAN` — **no new table**); keep VV mandatory.
- **Timeline:** before the first PROD dispatch enablement; not required for SIT/UAT functional testing.

## R-3 · F-11 — Legacy unsalted SHA-256 password hashing 🟩

- **What:** `WecmsPasswordEncoder` writes the legacy `public.hash()` value (unsalted `Base64(SHA-256(pw))`) on every path; BCrypt is never written; no upgrade on login. Pinned by `LegacyHashPasswordEncoderTest` (6/6).
- **Risk:** weak against offline/rainbow attack if the DB leaks. Cannot be changed unilaterally — JEX authenticates the same `USER_INFM` row. No regression vs the production system.
- **Owner:** PO + DBA. **Env scope:** all. **UAT impact:** none. **PROD impact:** none independently (matches live JEX posture).
- **Required decision:** keep while JEX shares `USER_INFM`; a salted-hash migration (Argon2/bcrypt) needs a dual-read + JEX cutover plan.
- **Timeline:** coexistence-end only. Mitigate meanwhile with DB-at-rest encryption + access controls (Ops/DBA).

## R-4 · In-memory revocation + rate-limit durability 🟧

- **What:** `TokenRevocationService` (jti denylist + per-user cutoff) and `RateLimiter` (login + reveal) are in-process `ConcurrentHashMap`s. Lost on restart; not shared across instances. (Scheduled `ResultCheckJob` already uses ShedLock — multi-instance is contemplated.)
- **Risk:** after a restart, un-expired revoked tokens are honoured again until natural `exp` (≤ 1h TTL — bounded). On multiple nodes, a logout/lock on node A doesn't propagate to node B, and rate-limit counters dilute N×. The durable `USER_INFM.LOCK_YN` lock still blocks a locked user on the next request regardless.
- **Owner:** Architecture / PO. **Env scope:** matters only if PROD scales horizontally. **UAT impact:** none (single-instance). **PROD impact:** 🟧 acceptable single-instance; a shared store is required before scaling out.
- **Required decision:** pin PROD to single-instance (document it) **or** adopt a shared store (Redis / PO-approved table) before multi-node.
- **Timeline:** decide at the PROD-topology sign-off; not a SIT/UAT blocker.

## R-5 · Secret rotation status (F-05 follow-through) 🟥 (PROD)

- **What:** tracked YAML defaults removed; the historically-committed Coocon `api-key`/`smart-api-key` and the (blocklisted) JWT dev secret remain in git history.
- **Risk:** valid vendor credentials exposed in the repo until rotated.
- **Owner:** Infra / Ops (+ Coocon vendor). **Env scope:** wherever the leaked keys are still valid. **UAT impact:** none if env vars set + dispatch off (DEV-scoped keys). **PROD impact:** 🟥 **blocks PROD until rotation is complete.**
- **Required action:** rotate the Coocon keys with the vendor; provision rotated values via env/secret store. See [ops-secret-rotation-handoff.md](ops-secret-rotation-handoff.md).
- **Timeline:** before PROD; open the rotation ticket now.

---

## Summary

| Item | Severity | UAT | PROD | Owner |
|---|---|---|---|---|
| R-1 F-08 AES-CBC | 🟩 accepted | OK | OK (matches legacy) | PO/DBA |
| R-2 F-09 callback | 🟧→🟥 | OK (dispatch off) | **blocks before dispatch** | PO/Coocon/Backend |
| R-3 F-11 SHA-256 | 🟩 accepted | OK | OK (matches legacy) | PO/DBA |
| R-4 in-mem durability | 🟧 | OK | conditional (topology) | Architecture/PO |
| R-5 secret rotation | 🟥 (PROD) | OK (keys DEV-scoped) | **blocks PROD** | Ops |
