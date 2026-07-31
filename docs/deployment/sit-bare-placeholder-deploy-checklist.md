# SIT deploy checklist — bare-placeholder config migration

**Date:** 2026-07-30
**Applies to:** the SIT host only.
**Background:** [config-bare-placeholder-migration.md](config-bare-placeholder-migration.md)

---

## ⛔ 0. STEP ZERO — DIFF BEFORE YOU COPY. NEVER OVERWRITE BLIND.

The deploy copies repo files into the server's config folder, which means **the repo
`.env` is about to become the runtime configuration.** Before copying any `.env` to
any server, diff it against the file already there and review **every** difference:

```bash
# On the SIT host — repo copy vs the file currently in use.
diff -u /DATA/src-wecms/wecms-backend/environment/.env.sit  /path/to/repo/wecms-api/.env.sit

# Side-by-side on values only, ignoring comments and ordering:
diff <(grep -vE '^\s*#' /DATA/src-wecms/wecms-backend/environment/.env.sit | grep '=' | sort) \
     <(grep -vE '^\s*#' /path/to/repo/wecms-api/.env.sit                   | grep '=' | sort)

# And the OS-env layer, which overrides both (see §5):
env | grep -E '^(WECMS|HAENGSUNG)_' | sort
```

**Where the host differs from the repo, assume the host is right until proven
otherwise.** The repo file is a developer's working copy that happens to be committed;
the host file is what a running system was configured with. Two concrete traps in the
current repo `.env.sit`:

| Repo `.env.sit` value | Why it is a risk if copied over the host's |
|---|---|
| `WECMS_DB_URL=jdbc:postgresql://172.168.5.172:5432/wecmsdevdb` | This is the **shared DEV database**. If the SIT host points somewhere else, copying this **silently repoints SIT at DEV** — the app starts cleanly and writes to the wrong database. Nothing in the logs flags it. |
| `WECMS_SFTP_LOCAL_UPLOAD=/WAS_DATA/file/wecmsbatch/send`<br>`WECMS_SFTP_LOCAL_DOWNLOAD=/WAS_DATA/file/wecmsbatch/receive` | These are the **legacy JEX batch's** staging directories, not the refactor's own tree (`/DATA/src-wecms/wecms-backend/upload-files/…`, which UAT and PROD use). If the host is on the refactor tree, copying this moves SIT's payment files into a directory the legacy batch also reads. See §7. |

Also expect `WECMS_JWT_SECRET` to differ — the repo carries a labelled dummy
(`sit-local-run-dummy-jwt-secret-…`) that must never reach a server; and the Coocon /
OSMC / DB / Oracle credentials in the repo file are DEV values.

**Rule: copy the new *variables*, keep the host's *values*, except where you have
decided otherwise and written down why.** The 13 lines in §2 are additions; almost
nothing else in the file should change.

---

## ⛔ 1. ORDERING — READ THIS FIRST, IT IS THE WHOLE POINT

> ## Update the server's `.env.sit` FIRST. Deploy the war SECOND.
>
> **Reversed, the application will not start.**

The new `application-sit.yml` has no default values left in it. Thirteen variables
that used to be optional are now mandatory. A war built from it, running against the
current server `.env.sit`, fails during startup — it does not start degraded, it does
not start at all.

Deploying first and fixing after is not a recovery path: the app is down for the
whole interval.

---

## 2. The exact 13 lines to add to the server's `.env.sit`

Location on the host:

```
/DATA/src-wecms/wecms-backend/environment/.env.sit
```

**This is a different file from the `.env.sit` in the git repository.** Editing the
repo copy changes nothing on the server. Append the block below verbatim. Every value
is the default that was removed from `application-sit.yml`, so behaviour is unchanged.

```properties
# ── Added 2026-07-30: bare-placeholder migration ──────────────────────────
# The yml no longer carries defaults for these. All 13 are now REQUIRED.
# Values below are exactly what the yml used to default to — no behaviour change.

# Result-check: master gate + per-pass row cap.
# RESULT_CHECK_BATCH_SIZE is coupled to ResultCheckJob's PT60M lockAtMostFor —
# recompute the ceiling before raising it (ResultCheckLockCeilingTest guards it).
WECMS_DISPATCH_RESULT_CHECK_ENABLED=false
WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE=100

# R-1 stranded-record recovery sweep — MUTATING. Keep both gates closed.
WECMS_DISPATCH_RECOVERY_ENABLED=false
WECMS_DISPATCH_RECOVERY_CRON=-
WECMS_DISPATCH_RECOVERY_GRACE_MINUTES=30

# Read-only stuck-pay-file monitor: firm-file sweep cap + look-back window.
WECMS_DISPATCH_STUCK_MONITOR_FIRM_FILE_BATCH_SIZE=200
WECMS_DISPATCH_STUCK_MONITOR_RETENTION_DAYS=7

# Ops alert labels. env-mode T = DEV marker (SIT's yml value); WAS_NO may be empty.
# GRP_ID: do NOT change from WECMSBATCH before the VAN Center confirms a second
# channel is registered — an unregistered grpID most likely drops silently.
WECMS_DISPATCH_ALERT_ENV_MODE=T
WECMS_DISPATCH_ALERT_WAS_NO=
WECMS_DISPATCH_ALERT_GRP_ID=WECMSBATCH

# Callback access control. ENFORCE = fail closed (empty allow-list rejects with 403).
# X-Forwarded-For is believed only from a peer listed in TRUSTED_PROXIES; empty means
# the header is ignored entirely, which is the safe setting.
WECMS_CALLBACK_PROTECTION=ENFORCE
WECMS_CALLBACK_TRUSTED_PROXIES=
WECMS_CALLBACK_TRUST_FORWARDED_FOR=true
```

Notes on two of them:

- `WECMS_DISPATCH_ALERT_WAS_NO=` — **keep the line, leave the value empty.** An empty
  value is valid for a String; deleting the line is what breaks it.
- `WECMS_CALLBACK_TRUSTED_PROXIES=` — same. Empty is the intended, safest value.

`WECMS_DISPATCH_STUCK_MONITOR_ENABLED` and `_CRON` are **already present** on the
host if it was built from the repo template — do not add them again. But their
*values* need a decision before this deploy:

> ### ⚠️ Separate decision — the read-only monitor is probably OFF on SIT, and FIX I never took effect
>
> FIX I / FIX H (2026-07-30) enabled the read-only stuck-pay-file monitor for SIT and
> UAT **as a yml default** (`${…:true}` / `${…:0 */5 * * * *}`). An `.env` value has
> always beaten a yml default, and the repo's `.env.sit` carries
> `WECMS_DISPATCH_STUCK_MONITOR_ENABLED=false` + `_CRON=-`, added **2026-07-28 —
> two days before FIX I** — and never updated. So on any host built from that file,
> **FIX I has had no effect: the monitor has been off the whole time.**
>
> The bare-placeholder migration removes the yml default entirely, so the host value
> is now the *only* source. If SIT's `.env.sit` / `setenv.sh` still says `false`,
> the monitor stays off after this deploy.
>
> **That matters because it is the only detection for the two states that resolve
> solely by operator action** — `BULK_EXECUTION_UNCONFIRMED` (a 00302 was issued and
> never confirmed; the bank may already have executed the batch) and
> `BULK_EXECUTION_NOT_ISSUED` (the afterCommit residual). Neither retries. With the
> monitor off, both are invisible until someone queries `TB_FIRM_FILE_MST` by hand.
>
> **To turn it on, both gates move together** (enabled + a real cron; `true` with
> `-` is a silent no-op):
>
> ```properties
> WECMS_DISPATCH_STUCK_MONITOR_ENABLED=true
> WECMS_DISPATCH_STUCK_MONITOR_CRON=0 */5 * * * *
> ```
>
> **Second gap, same cause:** `WECMS_DISPATCH_ALERT_ENABLED=false` on SIT, while
> FIX I set that yml default to `true` as well. With the monitor on but alerting
> off, detection reaches a **log file only** (`grep "feature=stuck-pay-file-monitor"`)
> — nobody is paged. Turning alerting on additionally needs
> `WECMS_EFS_ENABLED=true` + a `TB_VAN_API_INFO.API_KEY` row, or every alert is
> swallowed as a WARN.
>
> This is a deliberate operational decision, not a mechanical part of the migration —
> which is why it is called out here rather than folded into the §2 block.

---

## 3. Verification command — run this ON THE HOST

Lists every variable the new yml needs that the host cannot supply. **Empty output
means complete.**

```bash
# On the SIT host. Adjust paths if the layout differs.
ENVFILE=/DATA/src-wecms/wecms-backend/environment/.env.sit
YMLDIR=/DATA/src-wecms/wecms-backend/environment

comm -23 \
  <(grep -hvE '^[[:space:]]*#' "$YMLDIR/application.yml" "$YMLDIR/application-sit.yml" \
      | grep -oE '\$\{[A-Z][A-Z0-9_]*\}' | tr -d '${}' | sort -u) \
  <( { grep -vE '^[[:space:]]*#' "$ENVFILE" | grep '=' | sed 's/=.*//' | tr -d ' ';
       env | grep -oE '^(WECMS|HAENGSUNG)_[A-Z0-9_]+'; } | sort -u )
```

The second list deliberately unions **both** sources — see §5. Expected total on the
yml side: **72 variables**.

Also confirm no value is itself an unexpanded placeholder (a sign of a half-edited
file):

```bash
grep -nE '^[A-Z_]+=.*\$\{' "$ENVFILE"    # expect no output
```

---

## 4. The failure signature, so you recognise it

If the war is deployed against an un-updated `.env.sit`, the log will **not** say
"Could not resolve placeholder". For the first variable it hits, it looks like this
(verified locally on the `local` profile against an un-updated `.env.local`):

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Failed to bind properties under 'wecms.dispatch.callback.trust-forwarded-for' to boolean:

    Property: wecms.dispatch.callback.trust-forwarded-for
    Value: "${WECMS_CALLBACK_TRUST_FORWARDED_FOR}"
    Origin: class path resource [application-sit.yml] - <line>:<col>
    Reason: failed to convert java.lang.String to boolean
            (caused by java.lang.IllegalArgumentException:
             Invalid boolean value '${WECMS_CALLBACK_TRUST_FORWARDED_FOR}')
```

**The tell is the `Value:` line carrying the literal `${VAR}` text.** That means
Spring could not resolve the variable and bound the placeholder string itself. The
variable named inside `${...}` is the one missing from your `.env`.

Two variants to expect:

| Property type | Symptom |
|---|---|
| boolean / int | bind error as above, naming the property and showing the literal |
| String | **no error at startup.** The literal is accepted silently and breaks later in a way that never mentions the variable — e.g. a missing `WECMS_JWT_SECRET` reported `wecms.jwt.secret must be at least 32 bytes for HS256 (current length: 19)`, 19 being the length of the text `${WECMS_JWT_SECRET}` |

Only `@Value` / `@Scheduled` properties produce the classic
`Could not resolve placeholder 'VAR'`. **Do not treat a successful startup as proof
that the `.env` is complete** — run the §3 command.

---

## 5. Both sources satisfy a variable — check both before concluding it is missing

The application reads configuration from two places, and **OS environment variables
override the `.env` file**:

1. `/DATA/src-wecms/wecms-backend/environment/.env.sit` — loaded by
   `spring.config.import` (prefixed `optional:`, so a missing file does not itself
   fail).
2. **OS environment variables exported by
   `/DATA/src-wecms/apache-tomcat-11.0.22/bin/setenv.sh`** before Tomcat starts.
   This file is host-owned and is **not in the repository** — nobody working from git
   can see it.

Consequences:

- A variable absent from `.env.sit` may still be satisfied by `setenv.sh`, and vice
  versa. The §3 command unions both, which is why it is the authoritative check.
- Where both define a variable, **`setenv.sh` wins.** If a value is not what you
  expect, check `setenv.sh` before editing `.env.sit`.
- This has already caused a wrong conclusion once: DB evidence showed
  `recipient-inquiry` and `efs.enabled` live on a deployed SIT node while the
  committed templates said `false`. Never state SIT's flag state from the repo.
- If you add the 13 lines to `setenv.sh` instead of `.env.sit`, that is equally
  valid — just be consistent and record which you chose.

---

## 6. Rollback

**Keep the previous war and the previous `.env.sit`. Restore them together.**

1. Before changing anything: copy both aside.
   ```bash
   cp /DATA/src-wecms/wecms-backend/environment/.env.sit \
      /DATA/src-wecms/wecms-backend/environment/.env.sit.pre-bare-migration
   cp <tomcat>/webapps/ROOT.war /DATA/backup/ROOT.war.pre-bare-migration
   ```
2. Also capture the effective environment, redacted:
   ```bash
   env | grep -E '^(WECMS|HAENGSUNG)_' | sed 's/=.*/=<redacted>/' > /DATA/backup/env.sit.before.txt
   ```
3. To roll back: stop Tomcat, restore **both** files, start Tomcat.

**Do not roll back only one.** The old war works with either `.env.sit` (its yml
carries the defaults, so the extra variables are harmless). The **new war works only
with the new `.env.sit`.** Mixing new war + old `.env` is the failure case in §4.

**Do not "fix" a failed startup by adding defaults back into `application-sit.yml`.**
That reintroduces the hidden-value drift this migration removed, in one profile only,
and leaves SIT inconsistent with the other three.

---

## 7. Pre-deploy state of SIT (verified 2026-07-30)

| Check | State |
|---|---|
| `TB_BATCH_SCHEDULE_INFO` / `_DTL` on the SIT DB (`172.168.5.172/wecmsdevdb`) | **Seeded — 3 INFO + 12 DTL**, `last_edtr='DBA_MANUAL'`, `reg_dttm=20260728180209`. Activation-runbook gate B is **GREEN for DEV/SIT**. Not a blocker. |
| Bulk rail DTL coverage | 8 rows under `FILE_DEPOSIT_TRANSFER_BATCH1` — `970424`×{CITAD, EBANK, NAPAS, SWIFT}, `970457`×{CITAD, NAPAS, SWIFT, VCB}. |
| Realtime rail DTL coverage | 4 rows under `NAPAS_REAL_TIME_BATCH` — `970424`×{CITAD, NAPAS}, `970457`×{CITAD, NAPAS}. **SWIFT / EBANK / VCB have no realtime DTL row**, so such a target still fails `NO_CRITERIA`. Recorded residual, PO routing decision — not a config gap. |
| INFO lookup key | Both rails read the INFO row under `BATCH_ID='NAPAS_REAL_TIME_BATCH'` (`OperatingHoursService.evaluateBulk` line 235), matching legacy. The 3 seeded INFO rows therefore serve the bulk path too. |
| `WECMS_SFTP_LOCAL_UPLOAD` / `_LOCAL_DOWNLOAD` | Set to real absolute paths in the repo template (`/WAS_DATA/file/wecmsbatch/send` and `/receive`) — **but see the warning below.** |
| Dispatch legs | All `false`. `WECMS_SFTP_ENABLED=false`, `WECMS_EFS_ENABLED=false` → mock clients. |

### ⚠️ SFTP staging paths — confirm before enabling SFTP

The two local staging paths are set, which is what matters: if either were **empty**,
the code silently falls back to the OS temp directory and **nothing logs it**.

```java
// BulkReceptionService.writeLocalFile (:851-853)
String dir = StringUtils.hasText(sftpProperties.getLocalUploadPath())
        ? sftpProperties.getLocalUploadPath()
        : System.getProperty("java.io.tmpdir");

// BulkExecutionResultService.downloadResultFile (:341-342)
String dir = StringUtils.hasText(sftpProperties.getLocalDownloadPath())
        ? sftpProperties.getLocalDownloadPath() : System.getProperty("java.io.tmpdir");
```

There is **no WARN, no INFO, no log line at all** on the fallback path —
`writeLocalFile` even creates the directory silently. The consequence would be
1000-byte payment request files containing bank account numbers, and downloaded bank
result files, written to OS temp and lost on restart, with nothing in the logs
pointing at the cause. The migration improves this only indirectly: the variables are
now mandatory, so they cannot be *absent* — but an **empty value still passes**, and
still triggers the fallback.

**The fallback triggers on an EMPTY value, not only a missing one.** The bare-placeholder
migration stops the variable being *absent* — `StringUtils.hasText("")` is still
`false`, so a blank line (`WECMS_SFTP_LOCAL_UPLOAD=`) silently routes to
`java.io.tmpdir` exactly as a missing variable used to. Bare placeholders buy nothing
here. The line must be present **and** carry a real path.

> ### ❓ Ops decision needed — which staging directory is SIT's?
>
> `.env.sit` points SFTP at the **legacy JEX batch's** directories:
>
> ```
> WECMS_SFTP_LOCAL_UPLOAD=/WAS_DATA/file/wecmsbatch/send
> WECMS_SFTP_LOCAL_DOWNLOAD=/WAS_DATA/file/wecmsbatch/receive
> ```
>
> while `.env.uat.example` and `.env.prod.example` use the refactor's own tree:
>
> ```
> /DATA/src-wecms/wecms-backend/upload-files/send
> /DATA/src-wecms/wecms-backend/upload-files/receive
> ```
>
> **Please confirm which is intended for SIT.** The repo template's own comment says
> *"Samples below mirror the historical SIT layout; confirm the real path with ops."*
> — so this has never been verified.
>
> Why it matters: if the refactor and the legacy JEX batch share a staging directory,
> that is a plausible route to **one system picking up the other's files**. The
> legacy batch polls its own send/receive directories; a refactor-written 1000-byte
> request file landing in `/WAS_DATA/file/wecmsbatch/send` is a file legacy may treat
> as its own to transmit — the file-level analogue of the `PROC_STAT='9'` hand-off,
> where "our flags are off" does not mean "nothing is sent". This is a question, not
> a finding: nobody in the repo can see the SIT filesystem or the legacy batch's
> current configuration.

**Two more things for Ops to confirm before `WECMS_SFTP_ENABLED=true`:**

1. `/WAS_DATA/file/wecmsbatch/send` and `/WAS_DATA/file/wecmsbatch/receive` exist on
   the SIT host, are writable by the Tomcat service account, and are `0750` or
   tighter (they hold bank account data).
2. **These are the legacy JEX batch's directories** (`/WAS_DATA/...`), while the UAT
   and PROD templates use `/DATA/src-wecms/wecms-backend/upload-files/{send,receive}`.
   The repo template's own comment says *"Samples below mirror the historical SIT
   layout; confirm the real path with ops."* If the refactor and the legacy batch
   share a staging directory, confirm that is intended — a shared directory is a
   plausible route to one system picking up the other's files.

---

## 7b. UAT and PROD have NO `.env` in this repository

Only `.env.uat.example` and `.env.prod.example` exist. **They are templates carrying
placeholder credentials — nobody should copy one up as the real file.** There is no
committed `.env.uat` or `.env.prod` and there never should be (`.gitignore:42`).

Copying an example up verbatim produces one of two outcomes, both bad: the app starts
with a `<uat-db-host>` string as its JDBC URL and fails at connection time, or — for a
String property bound through `@ConfigurationProperties` — it accepts the placeholder
silently and misbehaves later (see §4).

### Must be replaced with real values on the host

Everything in this list is either empty or a `<placeholder>` in the template. **20
variables per profile**, identical sets for UAT and PROD except the CORS origin:

| Group | Variables |
|---|---|
| Database | `WECMS_DB_URL` · `WECMS_DB_USERNAME` · `WECMS_DB_PASSWORD` |
| Auth | `WECMS_JWT_SECRET` — generate per environment, `openssl rand -hex 64`; never reuse across environments |
| CORS | **PROD only:** `WECMS_CORS_ALLOWED_ORIGINS` (`https://<prod-frontend-origin>`). UAT already carries the real `https://test2.wecms.vn` |
| Coocon | `WECMS_COOCON_API_KEY` · `WECMS_COOCON_SMART_API_KEY` · `WECMS_COOCON_UNIQUE_KEY` |
| OSMC | `WECMS_OSMC_API_KEY` · `WECMS_OSMC_UNIQUE_KEY` |
| EFS / VAN | `WECMS_EFS_BASE_URL` — **NEEDS CONFIRMATION for both UAT and PROD**; no value is recorded anywhere in this repo |
| Callback | `WECMS_CALLBACK_ALLOWED_IPS` — the VAN gateway source IPs. Empty under `protection=ENFORCE` means **every callback is rejected with 403** (fail-closed, intended until the VAN Center supplies them) |
| SFTP | `WECMS_SFTP_HOST` · `WECMS_SFTP_USERNAME` · `WECMS_SFTP_KEY_PATH` · `WECMS_SFTP_PASSWORD` |
| HAENGSUNG Oracle | `HAENGSUNG_ORACLE_URL` · `HAENGSUNG_ORACLE_USERNAME` · `HAENGSUNG_ORACLE_PASSWORD` — **required even with `HAENGSUNG_ORACLE_ENABLED=false`**; the yml lines are bare, so resolution happens regardless of the flag |
| Alert label | `WECMS_DISPATCH_ALERT_WAS_NO` (UAT) — the server/instance number. **Empty is a valid value**; keep the line, the label just omits the number |

`WECMS_CALLBACK_TRUSTED_PROXIES` is also empty in both templates — **that is the
correct value.** Empty means `X-Forwarded-For` is ignored entirely, so a forged header
cannot influence the allow-list. Set it only if TLS terminates upstream of the app.

### Can be taken from the example as-is

Everything else: the two Coocon/OSMC **URLs** (already the real
`sgw.webcashvietnam.com` production endpoints in both templates), all 10
`WECMS_*_STORAGE_ROOT` paths (`/DATA/src-wecms/wecms-backend/upload-files`), the SFTP
`REMOTE_*` directory names and `PORT=3300`, `WECMS_CRYPTO_LEGACY_CERT_SEED=wabooksvn`
(a cross-system constant that must match legacy, not a per-env secret), every feature
flag, and every cron. Those values were carried over from the yml defaults the
migration removed, so copying them reproduces prior behaviour exactly.

**Neither file has been verified against a real host.** Nobody working from this
repository can see `.env.uat` or `.env.prod`, and live evidence has already
contradicted the committed templates once (shedlock rows for jobs the templates said
were `false`). Treat §3's verification command as mandatory on those hosts too.

---

## 7c. What goes in the server config folder — and what must NOT

`/DATA/src-wecms/wecms-backend/environment/` is a **runtime config folder**. Only
files the application actually loads belong in it:

| File | Goes on the server? | Why |
|---|---|---|
| `.env.sit` | **YES** | Loaded by `spring.config.import` in `application-sit.yml` (and/or exported by `setenv.sh`) |
| `application-sit.yml` | **YES** | The profile the app runs |
| `application.yml` | **YES** | Base profile, always active underneath |
| `.env.sit.example` | **No** | A committed template/checklist. Useful in git or a browser; harmless but pointless on the host |
| **`docs/deployment/batch-dispatch-variable-reference.md`** | **NO — DO NOT COPY IT** | Documentation. **Nothing loads it.** See below |

### The batch/dispatch variable reference is documentation, not config

It was called `.env.batch.example` until 2026-07-31, and the name was the problem: it
looked like a config template sitting next to four real ones. Verified 2026-07-31 —
**nothing reads it**: no `spring.config.import` in any of the five `application*.yml`
files names it, and it is absent from `build.gradle`, `.gitlab-ci.yml`,
`run-local.bat/sh` and every Dockerfile. Every reference to it in the repo is a
documentation cross-link.

It has been renamed and moved to
[`batch-dispatch-variable-reference.md`](batch-dispatch-variable-reference.md).

**It does not belong in `/DATA/src-wecms/wecms-backend/environment/.**
Putting a documentation file in the runtime config folder invites the next person to
assume it is live configuration — the exact confusion the rename exists to end. And
**leaving it out is not a missed step**: nothing on the host needs it.

**Where Ops reads it instead:** in the repository at
`wecms-api/docs/deployment/batch-dispatch-variable-reference.md`, from git or a
browser, whenever they need the *why* behind a variable — what a flag does when
flipped, the audit finding behind it, the legacy-coexistence hazard, the activation
gate. Its 37 keys are all present in the real `.env` files already (verified, zero
gaps), so it is never a source of values — only of explanation.

**If you find a `.env.batch.example` on any server, it was copied there by mistake.**
Nothing reads it; delete it once you have confirmed the host's real `.env.sit` is
complete per §3.

---

## 8. Step order, condensed

0. **Diff the repo `.env.sit` against the host's, and review every difference (§0).**
   Copy the new variables; keep the host's values for DB URL, credentials and SFTP
   paths unless you have decided otherwise and recorded why.
1. Back up `.env.sit` + current `ROOT.war` (§6).
2. Capture `env | grep -E '^(WECMS|HAENGSUNG)_'`, redacted.
3. Append the 13 lines from §2 to the server `.env.sit` (or `setenv.sh` — §5).
4. Run the §3 verification command. **Empty output required.** Do not proceed otherwise.
5. Stop Tomcat, replace `ROOT.war`, start Tomcat.
6. Confirm startup; expect no `APPLICATION FAILED TO START` and no `Value: "${...}"`.
7. Spot-check that flags read as intended — `env | grep` plus the startup log's
   callback-protection line (ENFORCE should **not** emit the DISABLED WARN).
8. Leave every dispatch leg `false`. This deploy changes configuration mechanics only;
   it is not an activation. Activation gates live in
   [wecms-batch-activation-runbook.md](wecms-batch-activation-runbook.md) §5.0.
