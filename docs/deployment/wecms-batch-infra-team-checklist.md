# WeCMS Batch — Infrastructure Team Checklist

**Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) firm-banking dispatch
**Companion:** [wecms-batch-infrastructure-readiness-audit.md](wecms-batch-infrastructure-readiness-audit.md) ·
[wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) ·
[wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md)

**Legend**

```
[ ] Pending             — work not started / not evidenced
[x] Confirmed           — evidenced in this audit
[!] Blocked             — cannot proceed until a named prerequisite closes
[?] Needs confirmation  — value or state unknown to this audit; must be supplied
```

**Owners:** `OPS` = infrastructure/Ops · `DBA` = database · `DEV` = Arthur/dev · `VAN` = VAN Center / bank
integration · `PO` = Mr. Jung.

Every item carrying a hostname, port, IP, credential or partner id that the repository does not contain is
marked `[?]` and labelled **NEEDS CONFIRMATION** — nothing has been invented.

---

## 0. Do this first (gates everything below)

- [!] **OPS — Capture the deployed flag state on every host.** Run `env | grep -E '^(WECMS|HAENGSUNG)_'`
      on DEV, SIT and UAT app hosts, redact secrets, and paste into the audit §4. **This is blocking:** DB
      evidence proves `WECMS_DISPATCH_RECIPIENT_INQUIRY` and `WECMS_EFS_ENABLED` were `true` on a deployed
      node on 2026-07-22 and 2026-07-24, while every committed `.env.*` says `false`. Until this is done, no
      environment's configuration is known. *(Audit C-1)*
- [!] **OPS + PO — Decide whether the live EFS 201 recipient-inquiry stays on.** It has produced 17
      consecutive failures (`RS_CD='1000'`) against the shared DB. If unintended, set
      `WECMS_DISPATCH_RECIPIENT_INQUIRY=false` on that host.
- [x] **DBA + PO — Seed `TB_BATCH_SCHEDULE_INFO` / `_DTL` — DONE on the DEV/SIT DB (2026-07-28).**
      3 INFO + 12 DTL rows, `last_edtr='DBA_MANUAL'`, verified row-by-row against the seed spec (exact
      match). Two code fixes landed with it (explicit `wecms.dispatch.operating-hours.zone` property;
      `AntDivChannel` canonicalisation). *(Audit C-2 CLOSED — see audit §4.5)*
- [?] **PO — Confirm `ant_where_use_yn='N'` for VCB `970436`** (seed OQ-2). Legacy JEX DEV has **no**
      `970436` INFO row at all, so this row is a refactor judgement call, not a legacy value.
- [?] **PO + DEV — OQ-4: which layer owns the Shinhan CITAD 16:30 cut-off?** The realtime CITAD row is
      seeded `153000` (15:30), while memory `project_payment_cutoff_validation_16h30` records the move to
      16:30 currently enforced at request time by `CutoffValidator` (`CITAD_API`). If the dispatch gate should
      own it, this row needs `163000`.
- [?] **PO — SWIFT / EBANK on the NAPAS realtime rail.** No DTL row exists for either, so such targets still
      fail with `NO_CRITERIA`. This is a routing decision, not a window decision.
- [ ] **DBA — Run the same seed on the UAT and PROD DBs** once those targets are named (§2).
- [!] **VAN — Explain EFS 201 `RS_CD='1000'`** and state what must change on our side.
      *(Audit C-3)*

---

## 1. Network

- [?] **OPS — EFS / VAN Center gateway host + port.** SIT template carries
      `https://dev-van-gw.webcashvietnam.com`; UAT and PROD are **empty**. **NEEDS CONFIRMATION** for
      UAT/PROD. Direction: **outbound**, 443/HTTPS.
- [x] **Evidence that the gateway is reachable from at least one host** — 17 real 201 responses were parsed
      (audit §4.1). Reachability is proven; acceptance is not.
- [?] **OPS + VAN — Outbound source-IP allow-listing at the gateway.** Our egress IP(s) per environment must
      be registered with the VAN Center. A source-IP rejection is a plausible cause of `RS_CD='1000'`.
      **NEEDS CONFIRMATION.**
- [ ] **OPS — Firewall egress rule** for the app host → EFS gateway, 443/TCP, per environment.
- [?] **OPS — SFTP / external file service host + port.** Not in the repository at all. Templates carry
      `WECMS_SFTP_PORT=3300` (non-standard) with an empty host. **NEEDS CONFIRMATION.** Direction:
      **outbound**, SSH.
- [ ] **OPS — Firewall egress rule** for the app host → SFTP host, port per above.
- [?] **VAN — Gateway source IP(s) for the inbound callback allow-list.** Needed to populate
      `WECMS_CALLBACK_ALLOWED_IPS`. **NEEDS CONFIRMATION.**
- [ ] **OPS — Firewall ingress rule** allowing the VAN gateway → our API host, 443/TCP, restricted to the
      confirmed source IPs (not `0.0.0.0/0`).
- [ ] **OPS — DNS resolution verified** from each app host for: the EFS gateway, the SFTP host, the Coocon
      smart gateway, the OSMC gateway, the refactor DB host, and the Haengsung Oracle host.
- [?] **OPS — Load balancer / instance count per environment.** `shedlock` shows a single holder
      (`GroupwareDev`) today, so the observed deployment is single-instance. **NEEDS CONFIRMATION** for
      SIT/UAT/PROD. *(Audit H-8, dep. 19)*
- [!] **OPS — Reverse proxy must overwrite or strip inbound `X-Forwarded-For`.**
      `wecms.dispatch.callback.trust-forwarded-for` is **hardcoded `true`** in all four profile YAMLs, so a
      client-supplied XFF header would defeat the IP allow-list. Blocked on confirming the proxy behaviour.
      *(Audit H-2)*

## 2. Database

- [x] **DBA — Refactor DB reachable** — `172.168.5.172:5432/wecmsdevdb` queried read-only during this audit.
- [x] **DBA — Schema present; `ddl-auto: validate` passes.** 161 `V*.sql` DDL scripts; Flyway is **not** a
      dependency, so nothing is applied at startup — the schema is external and pre-migrated.
- [x] **DBA — `shedlock` table exists and is healthy** — 8 rows, `usingDbTime()` provider.
- [x] **DBA — `TB_VAN_API_INFO` provisioned** — 2 rows, both `API_KEY` populated (values not read).
- [x] **DBA — `TB_FIRM_CONF` provisioned** — 3 rows, all `ORG_STAT='1'`, `VAN_ORG_CD='GV00000001'`.
- [ ] **DBA + DEV — Investigate the duplicate `TB_FIRM_CONF` row.** Two rows share
      `(VN_VNM0004_000001, GV00000001)` with `ORG_STAT='1'`. `hasActiveFirmConfig` uses `exists` so it
      tolerates this, but single-row finders could be non-deterministic. *(Audit H-10)*
- [x] **DBA + PO — `TB_BATCH_SCHEDULE_INFO` seeded** — 3 rows, all `BATCH_ID='NAPAS_REAL_TIME_BATCH'`
      (`970424` `Y`, `970457` `Y`, `970436` `N`), all `000000`–`235959`. Both the real-time and BATCH1 paths
      look up that same INFO key, so BATCH1 needs no INFO rows of its own.
- [x] **DBA + PO — `TB_BATCH_SCHEDULE_DTL` seeded** — 12 rows (8 bulk + 4 realtime). The two paths differ on
      a missing DTL row: NAPAS real-time → `NO_CRITERIA` (**fails** the row); BATCH1 → passes (`'Y'`).
      Realtime coverage is `970424`/`970457` × `NAPAS`/`CITAD` only — SWIFT/EBANK intentionally absent.
- [ ] **DBA — Repeat the seed on UAT and PROD.** The seed is applied to the DEV/SIT DB only. Each environment
      needs its own rows, delivered as a manual DBA script under `Claude Code (AI)/sql/` per the workspace DDL
      policy (never Flyway). **BLOCKING for UAT/PROD dispatch.**
- [?] **OPS + DBA — Real UAT and PROD `WECMS_DB_URL`.** Not committed. **NEEDS CONFIRMATION.**
- [!] **OPS + DBA + PO — Compare the refactor UAT/PROD DB URL against the legacy JEX batch DB URL.** If they
      are the same instance, the refactor and legacy JEX share firm-banking rows and the recovery sweep could
      double-process, because refactor rows are **not distinguishable** (both draw the same global
      `firm_seq_no` / `firm_file_seq_no`). DEV and SIT are already verified DB-separated. **BLOCKING for
      recovery in UAT.** *(Audit dep. 26)*
- [ ] **DBA — Grants for the app user** on all `TB_FIRM_*`, `TB_PAY_*`, `TB_VAN_API_INFO`,
      `TB_BATCH_SCHEDULE_*`, `MSG_SND_HIS` and `shedlock` (SELECT/INSERT/UPDATE; ShedLock also needs UPDATE
      + INSERT).
- [ ] **DBA — Execute permission** on `wb_get_next_val` and read access to the `firm_seq_no` /
      `firm_file_seq_no` sequences (single-executor funnel: `LegacyFunctionRepository`).
- [ ] **DBA — Connection-pool sizing reviewed** for the added batch load (Hikari defaults are in use; no
      explicit pool config in any profile YAML).
- [ ] **DBA — Backup / PITR confirmed** for the refactor DB, with a tested restore, before any leg is
      enabled. *(Audit dep. 24)*
- [ ] **DBA — Oracle DataSource validation query / connection test** configured for the Haengsung secondary
      DS, to avoid `ORA-17008` on stale connections (`spring.datasource.hikari` binding at
      `HaengsungOracleDataSourceConfig:65`).

## 3. File system

- [?] **OPS — Confirm the 10 storage roots per environment.** UAT/PROD templates use
      `/DATA/src-wecms/wecms-backend/upload-files`. **PROD is an unverified UAT clone** — audit H-4.
      **NEEDS CONFIRMATION for PROD.**
- [ ] **OPS — Create the SFTP staging directories** and verify they are writable by the app user:
      `.../upload-files/send` (request files) and `.../upload-files/receive` (result files).
- [!] **OPS — Set `WECMS_SFTP_LOCAL_UPLOAD` and `WECMS_SFTP_LOCAL_DOWNLOAD`.** Both are **empty** in
      `.env.sit.example`; the code silently falls back to `System.getProperty("java.io.tmpdir")`, so 1000-byte
      request files and downloaded result files would land in the OS temp directory and be lost on restart.
      **BLOCKING for the bulk leg.** *(Audit M-2)*
- [ ] **OPS — Directory ownership + permissions** — owned by the app service account; `0750` or tighter on
      the staging dirs. Not world-readable (they contain bank account data).
- [ ] **OPS — SFTP private key file** placed at `WECMS_SFTP_KEY_PATH`, mode `0600`, owned by the app user.
      Verify with `ls -l` → `-rw-------`.
- [?] **OPS — Shared storage requirement.** If more than one instance runs, the staging dirs must either be
      shared or the bulk leg must be pinned to one node. **NEEDS CONFIRMATION** (depends on §1 instance
      count).
- [ ] **OPS — Disk headroom** on the staging + log volumes, monitored at 80 %.
- [ ] **OPS — Log rotation** configured. Nothing in the repository configures it. *(Audit M-5)*

## 4. EFS / SFTP integration

- [!] **VAN — EFS `base-url` for UAT and PROD.** Empty in both templates. **BLOCKING.**
- [!] **VAN — EFS `partner-id` (envelope `ID`).** Empty in **every** template, all environments. **BLOCKING.**
- [x] **OPS — Per-VAN `API_KEY` provisioned** into `TB_VAN_API_INFO` on the DEV/SIT DB (2 rows). Never in
      YAML or an env var by design; `VanApiKeyService` throws when absent, so it fails closed.
- [ ] **OPS — Provision `TB_VAN_API_INFO.API_KEY` for UAT and PROD** out-of-band, once those DBs are named.
- [?] **VAN — SFTP host, port, username, and key or password.** Nothing in the repository.
      **NEEDS CONFIRMATION.**
- [ ] **VAN — Confirm the remote directory names** (`sdata` / `rdata` / `rdata/success`) per environment.
- [ ] **DEV + VAN — B5 byte-parity capture.** Generate the same transfer through legacy and the refactor;
      diff the two 1000-byte files and record a SHA-256 of each. Focus on the EV/VV URL-encode pass count
      and the `BT00` reception-code set.
- [ ] **VAN — Confirm the 01301 / 01302 result-code set** used in production responses.
- [ ] **VAN — Acceptance capture for `data_dcm_amt`** (OQ-1 is CLOSED as a decision; this is evidence only).
- [ ] **VAN — Acceptance capture for one VCB VND file record** (`BANK_CHNL_DIV` / `ANT_DIV_CD`) — OQ-2 is
      CLOSED as a decision; this is evidence only.

## 5. SSL / TLS

- [?] **OPS — Verify the EFS gateway certificate chain** from each app host. `EfsClientConfig` uses
      `SimpleClientHttpRequestFactory` with **default JVM trust** and there is **no truststore configuration
      anywhere** in the repository. If the gateway presents a private-CA certificate, the CA must be imported
      into the JVM truststore. **NEEDS CONFIRMATION.** *(Audit H-9)*
- [x] **DEV — No TLS verification bypass exists in the codebase** — verified: no `X509TrustManager`, no
      hostname-verifier override, no `setSSLSocketFactory`, no trust-all anywhere in `src/main/java`.
- [ ] **OPS — Import the gateway CA** into the JVM truststore if required (record the keystore path and that
      it is **not** the default password).
- [ ] **OPS — Inbound server certificate** valid for the API host that receives the callbacks; note the
      expiry date.
- [ ] **OPS — Certificate-expiry monitoring** for both the inbound server cert and the outbound gateway
      certs. Nothing in the repository does this. *(Audit M-5)*
- [ ] **OPS — Confirm no TLS termination downgrade** — the callback path must be HTTPS end-to-end or
      HTTPS-to-proxy on a trusted internal hop.

## 6. Secrets

- [x] **DEV — No secrets are committed.** All four profile YAMLs use `${VAR}` placeholders only;
      `git ls-files` shows only the five `.env.*.example` templates; `.gitignore:41-49` ignores `.env.*` and
      re-includes `!.env.*.example`.
- [x] **DEV — `.env.sit` and `.env.local` exist on disk but are untracked** — confirmed.
- [ ] **OPS — Confirm `.env.*` files are excluded from the deployed artifact** as well as from git (SIT bundles
      config inside `ROOT.war` per `project_sit_deploy_war_config_hotfix`).
- [!] **SECURITY + PO — Remove the real cert seed from the committed templates.**
      `WECMS_CRYPTO_LEGACY_CERT_SEED=wabooksvn` appears in all five `.env.*.example` files **and** as a code
      default at `BankCertCryptoService:69`. It cannot be freely rotated (legacy pgcrypto data compatibility),
      so the action is to stop committing it, not to change it. *(Audit H-6)*
- [!] **OPS — Rotate the historically-committed Coocon keys** before any PROD activation (carried from the
      prior security review).
- [ ] **OPS — Provision `WECMS_JWT_SECRET`** ≥ 64 bytes per environment (fail-fast on missing/short — good).
- [ ] **OPS — Confirm secrets are delivered via host OS env / a secret manager**, not via a file in the
      repository tree. SIT reads `bin/setenv.sh`, which is **not** in this repository.
- [ ] **OPS — Document the rotation procedure** for: DB password, JWT secret, Coocon/OSMC keys, VAN
      `API_KEY`, SFTP key. State which require a restart.
- [ ] **OPS — Confirm no secret is passed on a command line** (visible in `ps`).

## 7. Callbacks

- [x] **DEV — Callback endpoints are implemented** and return the EFS ack envelope
      (`BulkReceptionResultController`, `BulkExecutionResultController`).
- [x] **DEV — Payload authentication exists** — `VV` HMAC-SHA256 verified before any `EV` decrypt, on both
      callbacks (`EfsCryptoService.verifyVv`).
- [!] **OPS — The callback endpoints are UNAUTHENTICATED and the IP filter FAILS OPEN.**
      `SecurityConfig:80` marks `/api/v1/payment/dispatch/callbacks/**` `permitAll`, and
      `CallbackIpWhitelistFilter` passes everything through when `allowedIps` is empty — which it is in all
      four templates. Combined with the hardcoded `trust-forwarded-for: true`, the allow-list is spoofable.
      **Must be restricted by IP / mTLS / network before any non-local deployment.** *(Audit H-2, prior gate
      R-03)*
- [!] **VAN — Supply the gateway source IPs** so `WECMS_CALLBACK_ALLOWED_IPS` can be populated. **BLOCKING
      for the item above.**
- [ ] **OPS — Verify the callback URL is reachable from the gateway side** (inbound test, §9.5).
- [ ] **OPS — Confirm the callback path is not cached or rewritten** by the proxy / LB.
- [ ] **OPS — Rate-limit or connection-cap the callback path** at the proxy as defence in depth.
- [?] **OPS + VAN — Confirm the public callback URL** to register with the gateway per environment.
      **NEEDS CONFIRMATION.**

## 8. Monitoring

- [!] **DEV — Add `spring-boot-starter-actuator`.** `SecurityConfig:75` permits `/actuator/health` and
      `RequestResponseLoggingFilter:69` skips `/actuator`, but the starter is **absent from
      `build.gradle`** — so **no health endpoint and no metrics exist today**. The SIT checklist step
      "`/actuator/health` UP" cannot pass, and no LB health probe can work. **BLOCKING for monitoring.**
      *(Audit C-5)*
- [x] **DEV — Correlation ids implemented** — `TraceIdFilter` → MDC `traceId`, echoed as `X-Trace-Id`, in the
      log pattern (`application.yml:148`).
- [x] **DEV — Transaction ids implemented** — `TRAN_SEQ` / `TNO` on every `TB_FIRM_TRAN` row.
- [x] **DEV — Batch execution audit implemented** — `TB_FIRM_TRAN`, `TB_FIRM_BAT_API_TRAN` (written
      `REQUIRES_NEW` so the audit survives a rollback), `TB_FIRM_FILE_RSLT`.
- [x] **DEV — Alert hook implemented but OFF** — `OpsAlertService` posts through EFS
      `WGVN_MSG_SEND_001`; needs `WECMS_DISPATCH_ALERT_ENABLED=true` **and** EFS enabled.
- [ ] **DEV — Add the two alert YAML lines** (`wecms.dispatch.alert.env-mode`, `.was-no`) so the values can be
      supplied per environment; they are read by code but absent from every YAML. *(Audit H-5)*
- [ ] **OPS — Structured (JSON) logging** — not configured; plain pattern layout only.
- [ ] **OPS — Alerts wired for:** dispatch failure count, pending `PROC_STAT='9'` older than 15 min, stranded
      `'6'` rows, result-check non-`0000`, callback `VV` verification failures, gateway RC ≠ `0000`
      (**currently `1000`**), file-creation failures, DB connection failures, disk > 80 %, certificate expiry
      < 30 days, and stale `shedlock.locked_at` per job.
- [ ] **OPS — Scheduler heartbeat dashboard** from `shedlock.locked_at` (usable today without metrics).
- [!] **DEV — SIT logs full request/response bodies.** `application-sit.yml:158` sets
      `com.kosign.wecms: DEBUG`; `RequestResponseLoggingFilter` logs bodies (8 000 chars) and redacts by
      **field name only** — account numbers, `EV`/`VV` and EFS envelope fields are **not** redacted. Set SIT to
      `INFO` or extend the redaction pattern. UAT/PROD are already `INFO`. *(Audit H-3)*

## 9. Deployment

- [x] **DEV — Artifact shape known** — a single `ROOT.war` on an external Tomcat
      (`providedRuntime` starter-tomcat; embedded Tomcat is excluded).
- [?] **OPS — The deployment pipeline is not auditable from this repository.** `.gitlab-ci.yml` only triggers
      an external pipeline (`project: '$URL'`, `file: '$FILE'`) on the `sit` and `uat` branches. There is no
      Dockerfile, no Kubernetes manifest and no `setenv.sh` in the repo. **NEEDS CONFIRMATION** of what the
      external pipeline actually does. *(Audit M-1)*
- [!] **DEV — Create `application-dev.yml`,** or formally record that DEV runs the `local` profile. DEV is a
      named target environment with no Spring profile; `application.yml:11` defaults to `local` and
      `application-local.yml` is gitignored, so a fresh clone or a DEV host has no bindable profile.
      *(Audit C-4)*
- [ ] **DEV — CI guard for tab characters** in the profile YAMLs (a tab has broken a SIT deploy before —
      `project_sit_deploy_war_config_hotfix`).
- [ ] **OPS — Set the full startup-required variable set** on every host — see
      [wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) §12. A missing variable
      **aborts startup** (there are no in-YAML defaults except the three `recovery.*` keys).
- [ ] **DEV — Recommended hardening: add in-YAML defaults** (`${VAR:false}` / `${VAR:-}`) so a missing
      variable degrades to OFF rather than aborting startup.
- [ ] **OPS — Confirm the Java runtime is JDK 21** (Temurin) on every host.
- [ ] **OPS — Snapshot the current `ROOT.war` and the host env** before each deploy (rollback baseline).
- [?] **OPS — Confirm the instance count and whether a rolling deploy is used.** **NEEDS CONFIRMATION.**

## 10. Activation

Gate order is mandatory — see the [runbook](wecms-batch-activation-runbook.md) §5 for the rationale.

- [!] **Gate A — Callbacks network-restricted** (§7) before anything else is enabled.
- [x] **Gate B — `TB_BATCH_SCHEDULE_INFO` / `_DTL` seeded and verified non-empty** (§2) — **DONE for
      DEV/SIT** (3 + 12 rows, audit §4.5). Still `[ ]` **pending for UAT and PROD.**
- [!] **Gate C — EFS `base-url` + `partner-id` + VAN `API_KEY` provisioned** for the target environment (§4).
- [!] **Gate D — EFS 201 `RS_CD='1000'` resolved** (§0) — the transport is proven reachable but rejecting.
- [ ] **Step 1 — Enable `WECMS_EFS_ENABLED=true` with `WECMS_DISPATCH_RECIPIENT_INQUIRY=true`** only
      (read-only inquiry, no money moves). Confirm `RS_CD='0000'`.
- [!] **Step 2 — Enable `WECMS_SFTP_ENABLED=true`** — blocked on the SFTP host/key/paths (§3, §4).
- [ ] **Step 3 — Enable `WECMS_DISPATCH_AUTO_RECEIVE=true`** (bulk file dispatch).
- [ ] **Step 4 — Enable `WECMS_DISPATCH_REALTIME_DISPATCH=true`** (NAPAS real-time).
- [!] **Step 5 — Set `WECMS_DISPATCH_RESULT_CHECK_CRON`** — blocked on the UTC/ICT day-boundary fix
      (audit H-7). Do not set the cron before that lands.
- [!] **Step 6 — Set `WECMS_DISPATCH_RECOVERY_ENABLED=true` + `_CRON`** — blocked on the UAT/PROD
      coexistence decision (§2). DEV and SIT are DB-separated and therefore safe; UAT is not confirmed.
- [ ] **Step 7 — Enable `WECMS_DISPATCH_ALERT_ENABLED=true`** once EFS is confirmed working, plus the two
      alert YAML lines.
- [ ] **PO — Written sign-off** recorded per environment before the next step in the sequence.

## 11. Rollback

- [x] **DEV — Every leg is flag-gated, so a rollback is a variable change** — no code change is needed to
      stop dispatch.
- [ ] **OPS — Confirm whether a restart is required to apply a flag change.** All gates are read via
      `@Value` / `@ConfigurationProperties` **without** `@RefreshScope` and there is no Spring Cloud Config,
      so **a restart is required**. Confirm and document the restart procedure and its duration.
- [x] **DEV — In-flight transactions are preserved on rollback.** Dispatch hooks run `afterCommit`, so a
      committed payment is never rolled back by a dispatch failure; rows simply remain at their current
      `PROC_STAT`.
- [ ] **OPS — Document the "stop new dispatches" one-liner** — set `WECMS_DISPATCH_REALTIME_DISPATCH=false`,
      `WECMS_DISPATCH_AUTO_RECEIVE=false`, `WECMS_DISPATCH_RESULT_CHECK_CRON=-`,
      `WECMS_DISPATCH_RECOVERY_CRON=-`, restart.
- [ ] **DBA — Prepare the read-only "needs manual review" query set** (runbook §7.4). Read-only only — no
      destructive `UPDATE` is sanctioned.
- [ ] **OPS + PO — Confirm the legacy JEX batch fallback per environment.** Legacy is configured running in
      DEV, UAT and PROD (`JEX_BATCH=true`) and absent in SIT (refactor-only). Record who flips it and
      whether the two systems can run simultaneously against the same DB.
- [ ] **OPS — Rehearse the rollback once in SIT** before the first UAT activation.
