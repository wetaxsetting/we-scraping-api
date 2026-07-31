# Batch / firm-banking dispatch — variable reference

**This is DOCUMENTATION, not configuration. Nothing reads this file.**

It was `batch-dispatch-variable-reference.md` until 2026-07-31. The name made it look like a config
template, which it never was: no `spring.config.import` in any of the five
`application*.yml` files points at it, and it is not referenced by `build.gradle`,
`.gitlab-ci.yml`, `run-local.bat/sh`, or any Dockerfile. Renamed and moved here so it
cannot be mistaken for something the application loads.

## What it is

A **narrative reference for the 37 batch and dispatch variables** — what each one
does, what happens when you flip it, the audit findings behind it, the legacy
coexistence hazards, and the activation gates. It is the "why" behind values that the
real `.env` files can only state.

## What it is NOT

- **Not a source of values.** Every one of its 37 keys already exists in all four real
  `.env` files (verified 2026-07-31 — zero gaps). Where the values differ, the real
  `.env` files are authoritative; the samples here are illustrative and some are
  deliberately `******` or `${PLACEHOLDER}`.
- **Not a file to place on a server.** See "Server placement" below.
- **Not complete.** It covers 37 of the 68–72 variables a profile needs. The complete,
  authoritative checklist for any profile is that profile's `.env.*.example`.

## Where to look instead

| You want | Go to |
|---|---|
| The complete required-variable list for a profile | `.env.local.example` · `.env.sit.example` · `.env.uat.example` · `.env.prod.example` |
| Actual values in use | `.env.local` / `.env.sit` (repo) · the host's file for UAT/PROD |
| Why placeholders are bare and what that costs | [config-bare-placeholder-migration.md](config-bare-placeholder-migration.md) |
| The SIT deploy procedure | [sit-bare-placeholder-deploy-checklist.md](sit-bare-placeholder-deploy-checklist.md) |
| Per-variable binding detail | [wecms-batch-environment-variables.md](wecms-batch-environment-variables.md) |
| Activation gates | [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) §5.0 |

## Server placement

**This file does not belong on the SIT, UAT or PROD server.** It is not loaded by
anything and placing it in `/DATA/src-wecms/wecms-backend/environment/` — alongside
the real `.env.sit` and `application-sit.yml` — would put a documentation file in a
runtime config folder, where the next person may reasonably assume it is live
configuration. Leave it in the repository at this path; Ops reads it from git or a
browser when they need to understand a variable.

## Content, verbatim

Everything below is the original file, unchanged — same keys, same values, same
comments, same section numbering. Its dated correction blocks are part of the record
and are deliberately preserved, including the two that supersede each other. Read the
most recent dated block in any section as current.

```properties
# =============================================================================
# WeCMS Batch — firm-banking dispatch environment template
# =============================================================================
# Generated 2026-07-28 by the batch infrastructure readiness audit.
#   docs/deployment/wecms-batch-infrastructure-readiness-audit.md
#   docs/deployment/wecms-batch-environment-variables.md
#   docs/deployment/wecms-batch-activation-runbook.md
#
# SCOPE — this file covers ONLY the batch / firm-banking dispatch subsystem.
# It is a SUPPLEMENT to the full per-profile templates, not a replacement:
#   .env.local.example  .env.sit.example  .env.uat.example  .env.prod.example
# The full templates also carry DB, JWT, CORS, Coocon, OSMC, storage-root and
# Haengsung variables that this file intentionally does not duplicate.
#
# -----------------------------------------------------------------------------
# HOW TO USE
# -----------------------------------------------------------------------------
# 1. Copy the relevant blocks into the target host's real configuration.
#    On SIT the runtime authority is the server's bin/setenv.sh (OS env), NOT a
#    .env file — see memory reference_sit_server_config_setenv.
# 2. Replace every ${PLACEHOLDER} with a real value obtained from the owning
#    team. Placeholders are NOT valid values.
# 3. NEVER commit a filled-in copy. .gitignore ignores .env.* and re-includes
#    only *.example — keep it that way.
#
# -----------------------------------------------------------------------------
# CRITICAL — READ BEFORE CHANGING ANY FLAG
# -----------------------------------------------------------------------------
# * EVERY variable below is REQUIRED for the application to START on the
#   sit / uat / prod profiles. The profile YAMLs bind ${VAR} with NO in-yaml
#   default, so a missing variable aborts startup with
#   "Could not resolve placeholder". The only exceptions are the three
#   WECMS_DISPATCH_RECOVERY_* keys, which do have in-yaml defaults.
#   An empty value satisfies a String property; it does NOT satisfy a boolean
#   or an int (WECMS_SFTP_PORT= will fail to bind).
#
#   *** CORRECTED 2026-07-30 — THE PARAGRAPH ABOVE IS WRONG, AND WRONG IN THE
#   *** DANGEROUS DIRECTION. Do not rely on it.
#
#   Verified by reading all five profile YAMLs: of every variable in THIS file,
#   exactly FOUR are bare ${VAR} and therefore startup-required on sit/uat/prod —
#     WECMS_DB_URL   WECMS_DB_USERNAME   WECMS_DB_PASSWORD
#     WECMS_CALLBACK_ALLOWED_IPS
#   EVERY OTHER key here carries an in-yaml default: all three dispatch flags,
#   every result-check / stuck-monitor / recovery / alert key, every EFS key,
#   every SFTP key, the exchange-rate and operation-status keys, and the callback
#   protection / trusted-proxies / trust-forwarded-for trio. The three
#   WECMS_DISPATCH_RECOVERY_* keys are NOT the only exceptions — they are three
#   among roughly thirty.
#
#   WHY THE DIRECTION MATTERS: the original text says a missing variable will stop
#   the app. For almost everything here it will NOT. It boots and silently uses the
#   yaml default, so a MISSPELLED VARIABLE NAME is indistinguishable from
#   "deliberately not configured" — and on sit/uat several of those defaults are
#   now `true` (stuck-monitor, alert — FIX H / FIX I), not `false`. Setting a flag
#   here under a typo'd name does not disable it; it leaves the yaml default in
#   charge. Verify with `env | grep` on the host, not by reading this file.
#
#   The empty-value note in the original paragraph IS correct: an empty value
#   satisfies a String but not a boolean or an int.
#
#   *** SUPERSEDED 2026-07-30 (later the same day) — THE CORRECTION BLOCK ABOVE IS
#   *** NOW ITSELF OUT OF DATE, AND THE ORIGINAL PARAGRAPH IS CLOSER TO TRUE AGAIN.
#
#   The bare-placeholder migration removed EVERY in-yaml default from all five
#   config files (169 placeholders). So "exactly FOUR are startup-required and
#   ~30 carry an in-yaml default" no longer holds: there are no in-yaml defaults
#   left. EVERY ${VAR} in a profile yml must now be set in that profile's .env.
#   Required counts: local 68, sit 72, uat 72, prod 68.
#   See docs/deployment/config-bare-placeholder-migration.md.
#
#   READ THE ORIGINAL PARAGRAPH AT THE TOP OF THIS SECTION AS CORRECT AGAIN, with
#   one refinement it never had: a missing variable does not always announce
#   itself. @Value / @Scheduled properties abort naming the variable ("Could not
#   resolve placeholder 'VAR'"), but @ConfigurationProperties binds LENIENTLY —
#   the literal text "${VAR}" becomes the value. A boolean/int target then fails
#   the bind (verified: 'wecms.dispatch.callback.trust-forwarded-for' ->
#   Value: "${WECMS_CALLBACK_TRUST_FORWARDED_FOR}"), but a STRING target produces
#   NO error at all. So most keys here now fail fast; the String ones (DB URL,
#   SFTP paths, EFS base-url) still do not.
#
#   DEPLOY ORDER: the host .env / setenv.sh must be updated BEFORE the new war is
#   deployed. Old .env + new yml = the application does not start. For SIT the
#   exact lines are in docs/deployment/sit-bare-placeholder-deploy-checklist.md.
#
#   The three WECMS_DISPATCH_RECOVERY_* keys are no longer special in any way —
#   they are required exactly like everything else.
#
# * EVERY value below is the SAFE / DISABLED value. Do not flip anything to
#   true without working through the activation runbook §5 gates.
#
# * BLOCKER STATUS:
#     - TB_BATCH_SCHEDULE_INFO / TB_BATCH_SCHEDULE_DTL: *** RESOLVED 2026-07-28 ***
#       Seeded on the DEV/SIT DB (3 INFO + 12 DTL, last_edtr='DBA_MANUAL'),
#       verified row-by-row against the seed spec. Audit C-2 CLOSED.
#       STILL TO DO: run the same seed on the UAT and PROD DBs.
#       RESIDUAL: the realtime rail has DTL rows for 970424/970457 x NAPAS/CITAD
#       only — a target with ANT_DIV_CD SWIFT/EBANK (or 'S'/'E') still fails
#       NO_CRITERIA. That is a PO routing decision, not a window gap.
#     - EFS 201 currently returns RS_CD='1000' for every request (17/17 on the
#       shared dev DB). Awaiting VAN Center.  (audit C-3 — STILL OPEN)
#
# * NO SECRETS IN THIS FILE. Secret values are shown as ****** and must be
#   provisioned out-of-band (host env / secret manager), never committed.
# =============================================================================


# =============================================================================
# 1. DISPATCH ACTIVATION FLAGS  — all OFF
# =============================================================================
# Post-commit bulk 00301 reception hand-off for non-VND (file-path) payments.
# false -> DispatchAutoReceiveRegistrar no-ops; TB_PAY_FILE_MST stays PROC_STAT='9'.
# Activation runbook step 4. Requires the TB_BATCH_SCHEDULE seed (audit C-2).
WECMS_DISPATCH_AUTO_RECEIVE=false

# Post-commit NAPAS real-time transfer (EFS 202, + 204 FX pre-step) for VND.
# false -> RealtimeDispatchRegistrar no-ops; TB_PAY_TARGET stays PROC_STAT='9'.
# HIGHEST RISK LEG — moves money synchronously. Activation runbook step 5.
WECMS_DISPATCH_REALTIME_DISPATCH=false

# EFS 201 recipient / depositor-name inquiry during Excel upload + payment entry.
# READ-ONLY (moves no money) — the safest leg to validate the transport with.
# false -> FirmBankingInquiryService.inquiryAccount is a no-op that writes nothing.
# NOTE: DB evidence shows this was TRUE on a deployed node on 2026-07-22 and
#       2026-07-24 and failed 17/17 with RS_CD='1000'. Reconcile the deployed
#       host env before assuming this is off anywhere.  (audit C-1)
WECMS_DISPATCH_RECIPIENT_INQUIRY=false

# Spring cron for the EFS 203 processing-result scan, zone Asia/Ho_Chi_Minh.
# "-" is Spring's NEVER-FIRE marker. Confirmed never fired (absent from shedlock).
# BLOCKED: fix the UTC/ICT day-boundary bug first — the API rail stamps TRAN_DT /
# REG_TM in UTC while ResultCheckService selects on ICT-today.  (audit H-7)
# MUST stay "-" while legacy PROCESSING_RESULT_CHECK_BATCH (WECMS_BATCH DEV,
# schedule */1 * * * *) is active against the same DB — both drive
# TB_FIRM_TRAN/TB_PAY_TARGET PROC_STAT and ShedLock cannot coordinate with legacy.
WECMS_DISPATCH_RESULT_CHECK_CRON=-

# H-2 (2026-07-29): candidate rows fetched per pass. The sweep previously loaded EVERY
# TRAN_TP='202'/PROC_STAT='6' row — all dates, all tenants — into memory; it is now an ordered
# (tranDt, tranTm, tranSeq ASC), DB-limited query. Oldest-first ordering is what makes the limit
# safe: a young row can never displace an aged one, so a truncated pass still makes forward progress
# and nothing is permanently skipped. Non-positive falls back to 100 with a WARN.
# Setting this enables NOTHING — the cron above is still "-".
# Scope note: the scan is GLOBAL across tenants. That is legacy parity, not a defect —
# IDO.TB_FIRM_TRAN_R001.xml has no BIZ_KEY predicate either (it is a system batch).
WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE=100

# Read-only stuck-pay-file monitor (refactor-only; no legacy twin). Safe to enable
# in any environment — it mutates nothing. Both gates must be opened together.
#
# *** UPDATED 2026-07-30 (FIX H) — the two values at the end of this block are no
# *** longer the yaml defaults everywhere. application-sit.yml and
# *** application-uat.yml now default BOTH gates OPEN (enabled=true,
# *** cron=0 */5 * * * *), because after the batch-1 fail-closed fix this sweep is
# *** the ONLY detection for the two states described below. application-local.yml
# *** and application-prod.yml still default them closed (false + "-") — prod by
# *** omission rather than decision; see the header of application-prod.yml.
# *** So the false/"-" below are SAFE values but they are OVERRIDES on sit/uat, not
# *** agreement with the yaml. sit/uat also gained
# *** WECMS_DISPATCH_STUCK_MONITOR_RETENTION_DAYS (default 7), absent here and in
# *** the local/prod ymls, where the code default of 7 applies.
#
# 2026-07-29: gained a SECOND read-only sweep over TB_FIRM_FILE_MST rows stranded at
# PROC_STEP='1'/PROC_STAT='6' (the bank has the file via 00301, but 00302 was never
# confirmed). It classifies:
#   BULK_EXECUTION_NOT_ISSUED   - no 00302 audit row: the inline issue never ran
#                                 (lost 01301 callback / crash). Safe to re-drive.
#   BULK_EXECUTION_UNCONFIRMED  - a 00302 row exists but the file never advanced.
#                                 RECONCILE WITH THE VAN CENTER FIRST - the bank may
#                                 already have executed the batch.
# These two need OPPOSITE manual actions, which is the whole point of the sweep.
#
# This monitor + manual recovery IS the approved residual-recovery model. There is
# deliberately NO automatic 00302 retry: K9 offered Mr. Kang a "Recovery Scheduler"
# (Option A) and he chose inline execution from the 01301 callback instead. A retry
# sweep briefly existed here on 2026-07-29 and was removed the same day.
#   See docs/audits/final-four-to-one-batch-architecture-correction.md
WECMS_DISPATCH_STUCK_MONITOR_ENABLED=false
WECMS_DISPATCH_STUCK_MONITOR_CRON=-

# Cap on the second (firm-file) sweep, so monitoring cannot become an unbounded scan.
WECMS_DISPATCH_STUCK_MONITOR_FIRM_FILE_BATCH_SIZE=200

# Ops alerting on dispatch failures, posted via the EFS WGVN_MSG_SEND_001 endpoint.
# Requires WECMS_EFS_ENABLED=true to reach anything. Fires on failure only.
WECMS_DISPATCH_ALERT_ENABLED=false


# =============================================================================
# 2. STRANDED-RECOVERY SWEEP  — DOUBLE-GATED OFF
# =============================================================================
# UPDATED 2026-07-30: these three USED to have in-yaml defaults (:false / :- / :30)
# and so were optional for startup. After the bare-placeholder migration they are
# REQUIRED like every other variable — and they are now present in all five
# .env.*.example files, which was the original gap.  (audit H-5, closed)
#
# BOTH gates must be opened together, and only in an environment whose DB is
# verified separate from the legacy JEX batch DB. The sweep is blind: on a shared
# DB the refactor cannot distinguish its own rows from JEX rows (both draw the
# same global firm_seq_no / firm_file_seq_no via wb_get_next_val, and there is no
# source marker). DEV and SIT are verified DB-separated; UAT is NOT confirmed.
WECMS_DISPATCH_RECOVERY_ENABLED=false

# "-" = never fire.
WECMS_DISPATCH_RECOVERY_CRON=-

# Age in minutes before a '9'/'6' row is treated as stranded.
WECMS_DISPATCH_RECOVERY_GRACE_MINUTES=30


# =============================================================================
# 3. INBOUND BANK CALLBACKS  (01301 / 01302)
# =============================================================================
# WARNING — /api/v1/payment/dispatch/callbacks/** is permitAll and ALWAYS mapped
# (SecurityConfig:80). There is no on/off flag. Payload VV HMAC is the only
# application-level gate.
#
# An EMPTY allow-list makes CallbackIpWhitelistFilter a NO-OP: every source IP is
# accepted (FAIL-OPEN). Populate this with the VAN gateway source IPs before any
# non-local deployment.  (audit H-2 / prior gate R-03)
#
# Additional caveat: wecms.dispatch.callback.trust-forwarded-for is HARDCODED
# true in all four profile YAMLs, so a client-supplied X-Forwarded-For header can
# spoof the allow-list unless the reverse proxy overwrites or strips it. Verify
# with activation runbook §3.11 before relying on this list.
#
# *** UPDATED 2026-07-29 (H-1) — the two warnings above are NO LONGER ACCURATE. ***
#
# FAIL-OPEN IS FIXED. An empty allow-list no longer disables the filter; under the new default
# (protection=ENFORCE) it REJECTS every callback with 403. A malformed entry ABORTS STARTUP.
#
# X-FORWARDED-FOR SPOOFING IS FIXED. trust-forwarded-for is no longer hardcoded true in the YAMLs,
# and on its own it is no longer sufficient: the header is honoured ONLY when the immediate peer is
# listed in WECMS_CALLBACK_TRUSTED_PROXIES. Empty trusted-proxies (the default) => the header is
# always ignored, so a forged header cannot influence the decision.
#
# Classification note: this is a DEPLOYMENT-SECURITY control, not a legacy port. Verified from
# source — the legacy callbacks authenticate by HMAC only
# (FILE_DEPOSIT_TRANSFER_API1_act.jsp:102-109); no callback JSP reads an IP; web.xml has no
# security-constraint; and x-coocon-accessIp (ConnectionUtil.java:191) is an OUTBOUND header, not an
# inbound restriction. The HMAC gate is unchanged and remains the primary authentication.
#
# Full model / behaviour matrix / rollback:
#   docs/deployment/callback-security-configuration.md

# ENFORCE (default, and set explicitly in sit/uat/prod) | DISABLED (local only; WARN-logged each
# startup). Never set DISABLED in a deployed environment.
WECMS_CALLBACK_PROTECTION=ENFORCE

# Reverse proxies whose X-Forwarded-For may be believed. Literals and/or CIDR, comma-separated.
# EMPTY IS THE SAFE VALUE — set this only if TLS terminates upstream of the app.
WECMS_CALLBACK_TRUSTED_PROXIES=

# Whether the header may be consulted at all. Necessary but not sufficient (see above).
WECMS_CALLBACK_TRUST_FORWARDED_FOR=true

# Literal IPv4/IPv6 addresses and/or CIDR blocks (e.g. 203.0.113.7,203.0.113.0/24).
# Comma-separated, no spaces. Blank entries ignored; anything else malformed ABORTS STARTUP.
# Bare in every profile now (as is everything else) — an unset variable fails fast rather than booting unprotected.
# STILL NEEDS CONFIRMATION from the VAN Center: until the real gateway IPs are supplied, sit/uat/prod
# will 403 every callback. That is the intended fail-closed behaviour, not a regression.
WECMS_CALLBACK_ALLOWED_IPS=${VAN_GATEWAY_IP_1},${VAN_GATEWAY_IP_2}


# =============================================================================
# 4. EFS / VAN CENTER GATEWAY  (outbound HTTPS)
# =============================================================================
# NOTE: "EFS" here means the Webcash EFS / VAN Center HTTP gateway
# (WGVN_EFS_*.jct endpoints) — NOT a network filesystem.
#
# Master transport switch. false -> MockEfsApiClient, whose send() THROWS (absent is no longer possible: the variable is required),
# so no live request can leave by accident. true -> RestClientEfsApiClient.
WECMS_EFS_ENABLED=false

# Base URL of the gateway. Endpoint paths (/WGVN_EFS_201.jct .. /WGVN_EFS_302.jct
# and /WGVN_MSG_SEND_001.jct) are vendor-fixed in EfsProperties and need no override.
# Known per-environment values:
#   SIT : https://dev-van-gw.webcashvietnam.com   (from .env.sit.example)
#   UAT : NEEDS CONFIRMATION — empty in .env.uat.example
#   PROD: NEEDS CONFIRMATION — empty in .env.prod.example
WECMS_EFS_BASE_URL=${EFS_GATEWAY_BASE_URL}

# WECMS_EFS_PARTNER_ID — RETIRED 2026-07-29 (D-2, legacy-verified). The EFS envelope "ID"
# is ALWAYS TB_FIRM_CONF.VAN_ORG_CD on every leg (201/202/203/204/301/302/MSG); legacy has
# no partner-id property at all, so a configured value could only break parity. The property
# was removed from EfsProperties and from all four profile YAMLs — do not reintroduce it.
# The separate BANK_PRTN_ID is a business-payload field read from TB_FIRM_CONF, not config.

# -----------------------------------------------------------------------------
# The per-VAN AES/HMAC API_KEY is deliberately NOT an environment variable.
# It lives in TB_VAN_API_INFO.API_KEY and is read by VanApiKeyService, which
# THROWS when the row is missing — so crypto can never run on a money-moving
# path with an absent key. Provision it out-of-band into the DB.
#   DEV/SIT: provisioned (2 rows, both keyed).
#   UAT/PROD: not provisioned.
# Do NOT add a WECMS_EFS_API_KEY variable.
# -----------------------------------------------------------------------------

# Timeouts are currently HARDCODED in the four profile YAMLs
# (connect-timeout-ms: 3000, read-timeout-ms: 20000) and are NOT variables.
# To make them per-environment tunable, add the YAML placeholders first.
# (audit M-4)


# =============================================================================
# 5. SFTP / EXTERNAL FILE SERVICE  (outbound)
# =============================================================================
# false -> MockSftpClient, no network I/O. true -> JschSftpClient. (absent is no longer possible: the variable is required.)
WECMS_SFTP_ENABLED=false

# Host / port / user. NOTHING about the SFTP endpoint exists in the repository.
# The committed templates carry port 3300 (non-standard) with an empty host.
# NEEDS CONFIRMATION for every environment.
WECMS_SFTP_HOST=${SFTP_HOST}
WECMS_SFTP_PORT=3300
WECMS_SFTP_USERNAME=${SFTP_USERNAME}

# Filesystem PATH to the private key — the key material itself is never in config.
# The file must be mode 0600 and owned by the application service account.
WECMS_SFTP_KEY_PATH=/opt/wecms/secrets/sftp_id_rsa

# Key passphrase, or password when key auth is not used. Legacy used key-only.
# SECRET — provision out-of-band, never commit.
WECMS_SFTP_PASSWORD=******

# -----------------------------------------------------------------------------
# Paths. WARNING: the two LOCAL paths are EMPTY in .env.sit.example, and the code
# silently falls back to System.getProperty("java.io.tmpdir") — so 1000-byte
# request files and downloaded result files would land in the OS temp directory
# and be lost on restart. Set all four explicitly.  (audit M-2)
#   BulkReceptionService:630        -> local upload fallback
#   BulkExecutionResultService:342  -> local download fallback
# Both directories must exist, be writable by the app user, and be 0750 or
# tighter (they contain bank account data).
# UAT/PROD template values shown; confirm PROD independently — .env.prod.example
# is a UAT clone (audit H-4).
# -----------------------------------------------------------------------------
WECMS_SFTP_LOCAL_UPLOAD=/DATA/src-wecms/wecms-backend/upload-files/send
WECMS_SFTP_REMOTE_UPLOAD=sdata
WECMS_SFTP_LOCAL_DOWNLOAD=/DATA/src-wecms/wecms-backend/upload-files/receive
WECMS_SFTP_REMOTE_DOWNLOAD=rdata
WECMS_SFTP_REMOTE_MOVE=rdata/success

# wecms.dispatch.sftp.connect-timeout-ms is HARDCODED 60000 in the profile YAMLs
# and is not a variable.


# =============================================================================
# 6. OPS ALERT LABELS  — BINDABLE IN sit/uat; STILL NOT IN prod
# =============================================================================
# OpsAlertService reads wecms.dispatch.alert.env-mode (:97) and .was-no (:101)
# via @Value, but NEITHER has a YAML entry in any profile — so these variable
# names do not bind today. Add the two YAML lines first:
#
#   wecms:
#     dispatch:
#       alert:
#         env-mode: ${WECMS_DISPATCH_ALERT_ENV_MODE:}
#         was-no:   ${WECMS_DISPATCH_ALERT_WAS_NO:}
#
# Then the two variables below take effect. Until that one-line change lands,
# these are NEEDS CONFIRMATION.  (audit H-5)
#
# *** UPDATED 2026-07-30 (FIX I / FIX K) — the paragraph above is now PARTLY
# *** STALE. DO NOT ACT ON ITS INSTRUCTION WITHOUT CHECKING THE TARGET PROFILE.
#
# The YAML lines it tells you to add ALREADY EXIST in two of the four profiles:
#   application-sit.yml   env-mode: ${WECMS_DISPATCH_ALERT_ENV_MODE:T}
#                         was-no:   ${WECMS_DISPATCH_ALERT_WAS_NO:}
#                         grp-id:   ${WECMS_DISPATCH_ALERT_GRP_ID:WECMSBATCH}
#   application-uat.yml   same, with env-mode defaulting to U
# So on sit and uat both variables BIND today and are no longer NEEDS
# CONFIRMATION. Adding the block again would duplicate the keys.
#
# application-prod.yml and application-local.yml still have NO alert env-mode /
# was-no / grp-id entry, so on those two profiles the variables genuinely do not
# bind and only the OpsAlertService @Value defaults apply (env-mode falls back to
# the active Spring profile name, so the ServerGB label is never blank but is not
# the legacy R marker either). prod's omission is drift, not a decision — sit and
# uat received FIX H / I / K on 2026-07-30 and prod did not. See the header of
# application-prod.yml.
#
# A third variable now exists alongside these two — WECMS_DISPATCH_ALERT_GRP_ID
# (FIX K), the gateway channel-mapping id, defaulting to the legacy WECMSBATCH.
# DO NOT set it to anything else before the VAN Center confirms that channel is
# registered: an unregistered grpID most likely means the gateway drops the
# message silently, and this path is fire-and-forget, so the only trace would be a
# non-0000 RC in the log.
#
# env-mode: legacy ENV_MODE marker — T=DEV, U=UAT, R=PROD. Drives the alert
# "mode" field and the ServerGB label. Empty -> falls back to the Spring profile.
WECMS_DISPATCH_ALERT_ENV_MODE=T

# was-no: legacy WAS_NO — the server/instance number shown in the ServerGB label.
WECMS_DISPATCH_ALERT_WAS_NO=01


# =============================================================================
# 7. ADJACENT SCHEDULERS  — share the same ShedLock table
# =============================================================================
# Not part of the firm-banking dispatch subsystem, but they share the `shedlock`
# lock table and the single scheduling thread (spring.task.scheduling.pool.size:1),
# so a long-running job here delays the dispatch jobs.
#
# NOTE: live DB evidence (shedlock, 2026-07-28) shows ALL of these firing on a
# deployed node, while .env.uat.example / .env.prod.example say false. The
# committed templates are not a reliable description of any running environment.
# Reconcile with the host env before changing anything.  (audit §4.3)
#
# Exchange-rate collection. WARNING: the three crons are HARDCODED in
# ExchangeRateBatchScheduler (0 30 8 * * ?, 0 30 8-18 * * ?, 0 0,30 9-20 * * ?)
# and are NOT tunable per environment, unlike every other job.  (audit M-3)
#
# COEXISTENCE: enabling this is safe ONLY after legacy EXCH_0001_001 /
# EXCH_0010_001 / EXCH_0020_001 are disabled in the same environment. Legacy was
# verified still writing BANK_EXCH_RATE_INFM in DEV on 2026-07-28. ShedLock does
# NOT coordinate with the legacy JEX runtime. The Woori leg is the most dangerous:
# both sides compute SEQ_NO = MAX+1 cross-tenant and both INSERT only.
# Also note legacy EXCH_0030_001 (multi-bank x multi-SEQ_NO backfill) has NO
# refactor equivalent, so a missed window is not self-healing (OQ-2).
WECMS_EXCHANGE_RATE_BATCH_ENABLED=false

# Operation-status inquiry batch.
WECMS_OPERATION_STATUS_BATCH_ENABLED=false
# CONSOLIDATED 2026-07-28: SEED_CRON + REFRESH_CRON are retired. Seed and refresh
# now run as ONE locked, ordered unit (seed first) under one cron, so they can no
# longer fire at the same instant in an undefined order.
# Never-fire by default: the real legacy instant is UNRESOLVED (OQ-3 — legacy DEV
# fired 15:00:01 UTC = 22:00 ICT = 00:00 Asia/Seoul, not the 00:00 ICT once assumed).
# Set this only after the legacy batch owner or Infra confirms the JEX Batch Admin
# entry AND its timezone.
WECMS_OPERATION_STATUS_BATCH_CRON=-


# =============================================================================
# 8. DATABASE  — required for startup; full values live in the profile templates
# =============================================================================
# Repeated here only because every batch component depends on them.
#   DEV/SIT : jdbc:postgresql://172.168.5.172:5432/wecmsdevdb  (committed)
#   UAT/PROD: NEEDS CONFIRMATION — real URLs are not committed anywhere.
#
# BLOCKING for the recovery sweep: Ops must confirm the refactor UAT/PROD DB is
# NOT the legacy JEX UAT/PROD batch DB. If it is the same instance, the two
# systems share firm-banking rows and the sweep could double-process.
WECMS_DB_URL=jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
WECMS_DB_USERNAME=${DB_USER}
WECMS_DB_PASSWORD=******

# Schema is EXTERNAL and pre-migrated: ddl-auto is `validate` in every profile and
# Flyway is NOT a dependency, so nothing is applied at startup. The 161 V*.sql
# scripts under src/main/resources/db/migration/ are DBA-run references only.
#
# Required DB state before enabling any dispatch leg — verify with the runbook §3.6:
#   [x] shedlock                 present (8 rows on the dev DB)
#   [x] TB_VAN_API_INFO          >= 1 row with a populated API_KEY
#   [x] TB_FIRM_CONF             >= 1 row with ORG_STAT='1' for the tenant
#   [x] TB_BATCH_SCHEDULE_INFO   3 rows on DEV/SIT (2026-07-28) — UAT/PROD pending
#   [x] TB_BATCH_SCHEDULE_DTL    12 rows on DEV/SIT (2026-07-28) — UAT/PROD pending
#
# Both TB_BATCH_SCHEDULE tables are seeded by a MANUAL DBA script under
# "Claude Code (AI)/sql/" per the workspace DDL policy — never via Flyway.
# V95/V96 create the tables but contain zero INSERT statements.
#
# Related property (NOT an environment variable, in-code default only):
#   wecms.dispatch.operating-hours.zone  default Asia/Ho_Chi_Minh
# It is the wall-clock zone the HHmmss schedule windows are evaluated in. Leave it
# at ICT — these are Vietnamese bank cut-offs. Set Asia/Seoul ONLY for bug-for-bug
# parity with a co-hosted legacy JEX batch (the JEX frame DB runs Asia/Seoul, so
# legacy reads a 090000-153000 window as 07:00-13:30 ICT).


# =============================================================================
# 9. NOT IN THIS FILE — see the per-profile templates
# =============================================================================
# WECMS_JWT_SECRET                       (SECRET, fail-fast on missing/short)
# WECMS_CORS_ALLOWED_ORIGINS             (exact origins only, never "*")
# WECMS_CRYPTO_LEGACY_CERT_SEED          (SECRET — note audit H-6: the real value
#                                         is committed in all five .env.*.example
#                                         files and as a code default; stop
#                                         committing it. It cannot be rotated
#                                         freely because of legacy pgcrypto data.)
# WECMS_COOCON_*  (5 vars, 3 SECRET)     (open rotation item before PROD)
# WECMS_OSMC_*    (4 vars, 2 SECRET)
# 10x WECMS_*_STORAGE_ROOT               (confirm PROD paths independently)
# HAENGSUNG_ORACLE_*  + 3 sync job pairs (currently live per shedlock evidence)
#
# The complete startup-required variable list is in
# docs/deployment/wecms-batch-environment-variables.md §12.
```
