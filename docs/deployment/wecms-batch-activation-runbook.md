# WeCMS Batch — Activation Runbook

**Date:** 2026-07-28 · **System:** Refactor (`wecms-api`) firm-banking dispatch
**Applies to:** DEV · SIT · UAT · PROD
**Companions:** [readiness audit](wecms-batch-infrastructure-readiness-audit.md) ·
[environment variables](wecms-batch-environment-variables.md) ·
[infra checklist](wecms-batch-infra-team-checklist.md)

> **Current status: activation is BLOCKED.** Work through §1 and §2; do not begin §5 until every gate in §5.0
> is green. Replace every `<PLACEHOLDER>` before running a command. Nothing below performs a destructive
> operation.

> **ADDENDUM 2026-07-29 — there is NO `00302` retry job. There are still only three dispatch jobs.**
>
> A `BulkExecutionRetryJob` briefly existed on this branch (added and **removed the same day**). It was
> K9 **Option A** — *"Recovery Scheduler (reconciliation) — periodically re-process aged `STEP=1` rows"*
> — which was formally offered to Mr. Kang and **not selected**; he directed inline `00302` execution
> from the `01301` callback. Automatic retry was therefore never a parity requirement, and the job, its
> service classes, its ShedLock identity, its 5 YAML keys × 4 profiles and its 5 environment variables
> have all been **deleted**.
>
> * **Nothing to enable, nothing to keep disabled.** If you find
>   `WECMS_DISPATCH_BULK_EXECUTION_RETRY_*` set on a host, it is a leftover — remove it; it binds to
>   nothing.
> * **The approved residual-recovery model is monitoring + manual recovery.**
>   `StuckPayFileMonitorJob` detects files stranded at `PROC_STEP='1'/PROC_STAT='6'` read-only and
>   classifies `BULK_EXECUTION_NOT_ISSUED` (no `00302` audit row — safe to re-drive) vs
>   `BULK_EXECUTION_UNCONFIRMED` (a row exists — **reconcile with the VAN Center first**). Those two need
>   opposite actions. Manual procedure: [../features/wecms-batch/bulk-execution-retry.md §11](../features/wecms-batch/bulk-execution-retry.md).
> * New env var: `WECMS_DISPATCH_STUCK_MONITOR_FIRM_FILE_BATCH_SIZE` (default 200) — see
>   [environment variables §2a](wecms-batch-environment-variables.md).
> * Full record: [final-four-to-one-batch-architecture-correction.md](../audits/final-four-to-one-batch-architecture-correction.md).

> **ADDENDUM 2 (2026-07-29) — two changes that are ACTIVE WITHOUT A FLAG.**
> Unlike everything else in this runbook, these two are not gated and take effect on deploy.
>
> **1. Callback IP filtering is now FAIL-CLOSED (H-1).** `wecms.dispatch.callback.protection`
> defaults to `ENFORCE` and is set explicitly in `sit`/`uat`/`prod`. An unset, empty or blank
> `WECMS_CALLBACK_ALLOWED_IPS` now **rejects every callback with 403** instead of silently accepting
> from any source; a **malformed** entry (bad IP, `/33`, hostname) **aborts startup**.
> `X-Forwarded-For` is honoured only from an IP listed in `WECMS_CALLBACK_TRUSTED_PROXIES`.
> **Before deploying to any environment that receives callbacks, supply the real gateway IPs**
> (**[CONFIRM: VAN Center]** — still open). Model, matrix and rollback:
> [callback-security-configuration.md](callback-security-configuration.md); checklist step: §Phase 2f
> of [sit-uat-scheduler-deployment-checklist.md](sit-uat-scheduler-deployment-checklist.md).
>
> **2. The result-check scan is bounded (H-2).** `ResultCheckService` no longer loads every
> `202`/`PROC_STAT='6'` row; it uses an ordered, limited query with
> `WECMS_DISPATCH_RESULT_CHECK_BATCH_SIZE` (default 100, safe inline default, non-positive falls back
> rather than breaking the scheduler). **The job's cron is still `-`**, so this changes nothing until
> the result-check is separately activated — it only removes a scalability hazard from that future
> activation. Scope stays GLOBAL across tenants, matching legacy by design.

---

## 0. Step 0 — Establish the actual current state (mandatory, do this first)

The repository cannot tell you what any environment is currently running. DB evidence shows a deployed node
had `WECMS_DISPATCH_RECIPIENT_INQUIRY=true` and `WECMS_EFS_ENABLED=true` on 2026-07-22 and 2026-07-24, while
every committed `.env.*` says `false`.

On **each** app host (DEV, SIT, UAT):

```bash
# Redact before sharing: strip anything that looks like a credential.
env | grep -E '^(WECMS|HAENGSUNG)_' \
  | sed -E 's/(SECRET|PASSWORD|API_KEY|UNIQUE_KEY|SEED)=.*/\1=******/' \
  | sort
```

Record the output in the readiness audit §4. Then confirm what is actually running:

```bash
# Which EFS client bean is active? (mock => EFS off)
grep -c 'MockEfsApiClient' <APP_LOG_PATH>
# Which schedulers have ever acquired the lock?
psql -h <DB_HOST> -U <DB_USER> -d <DB_NAME> \
  -c "SELECT name, locked_at, locked_by FROM shedlock ORDER BY locked_at DESC;"
```

**Do not proceed until the flag state of the target environment is written down.**

---

## 1. Pre-deployment checks

| # | Check | Command / evidence | Pass criterion |
|---|---|---|---|
| 1.1 | Flag state captured | §0 | Recorded in the audit §4 |
| 1.2 | Build is green | `./gradlew clean build` | Compile + tests pass |
| 1.3 | No tab characters in the profile YAMLs | `grep -Pn '\t' src/main/resources/application-*.yml` | No output (a tab has broken a SIT deploy before) |
| 1.4 | No secret committed | `git ls-files \| grep -E '^\.env'` | Only `*.example` files listed |
| 1.5 | Target profile exists | `ls src/main/resources/application-<ENV>.yml` | File present — **DEV currently fails this** (audit C-4) |
| 1.6 | Full startup variable set present on the host | env-vars doc §12 vs §0 output | Every listed variable set |
| 1.7 | Dispatch flags are all OFF in the intended baseline | §0 output | `false` / `false` / `false` / `-` / `-` |
| 1.8 | `TB_BATCH_SCHEDULE_INFO` / `_DTL` seeded | §3.6 | `count > 0` — **DEV/SIT: 3 + 12 rows, verified** (audit §4.5). **UAT/PROD: not yet seeded** |
| 1.9 | Actuator present | `grep actuator build.gradle` | Dependency present — **currently absent** (audit C-5) |
| 1.10 | Rollback baseline captured | snapshot `ROOT.war` + host env | Both stored |

---

## 2. Deployment steps

1. **Snapshot for rollback** — copy the current `ROOT.war` aside and save the redacted `env` output from §0.
2. **Confirm the baseline is safe** — the host env must have all dispatch flags OFF (§1.7). Do not change any
   flag during a deploy; a deploy and an activation are separate events.
3. **Stop Tomcat.**
4. **Replace `ROOT.war`.** Leave the host configuration (`setenv.sh` / OS env) untouched.
5. **Start Tomcat.**
6. **Verify startup** — see §3.1–§3.3 before anything else.

---

## 3. Connectivity and verification tests

Replace `<...>` placeholders. All commands are read-only or create a single throwaway file that is removed.

### 3.1 Application startup

```bash
# No placeholder-resolution failure (the most common startup abort here)
grep -i "Could not resolve placeholder" <APP_LOG_PATH>        # expect: no output

# Hibernate schema validation passed
grep -iE "SchemaManagementException|missing column|wrong column type" <APP_LOG_PATH>   # expect: no output

# Which gateway clients are active? (baseline = both mocks)
grep -iE "MockEfsApiClient|RestClientEfsApiClient|MockSftpClient|JschSftpClient" <APP_LOG_PATH>
```

### 3.2 Scheduler state

```bash
# Baseline: neither dispatch job should ever appear in shedlock.
psql -h <DB_HOST> -p 5432 -U <DB_USER> -d <DB_NAME> -c \
  "SELECT name, locked_at, locked_by FROM shedlock
    WHERE name IN ('ResultCheckJob.run','StrandedRecoveryJob.run');"
# Expect: 0 rows while the crons are '-'.
```

### 3.3 Health endpoint

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://<API_HOST>/actuator/health
# Expect 200 with {"status":"UP"}.
# TODAY THIS RETURNS 404 — spring-boot-starter-actuator is not a dependency (audit C-5).
```

### 3.4 DNS resolution

```bash
nslookup <EFS_GATEWAY_HOST>
nslookup <SFTP_HOST>
nslookup <DB_HOST>
nslookup <ORACLE_HOST>
nslookup sgw.webcashvietnam.com          # UAT/PROD Coocon smart gateway
nslookup dev-sgw.webcashvietnam.com      # DEV/SIT Coocon smart gateway
```

### 3.5 TCP port reachability (outbound)

```bash
nc -vz -w 5 <EFS_GATEWAY_HOST> 443
nc -vz -w 5 <SFTP_HOST> <SFTP_PORT>      # templates carry 3300 — CONFIRM the real port
nc -vz -w 5 <DB_HOST> 5432
nc -vz -w 5 <ORACLE_HOST> 1521
# telnet <HOST> <PORT>                   # fallback if nc is unavailable
```

### 3.6 Database connection and readiness

```bash
# Connectivity + identity
psql -h <DB_HOST> -p 5432 -U <DB_USER> -d <DB_NAME> \
     -c "SELECT current_database(), current_user, version();"

# Batch readiness — the decisive query. All of these must be satisfied.
psql -h <DB_HOST> -p 5432 -U <DB_USER> -d <DB_NAME> <<'SQL'
SELECT 'tb_batch_schedule_info' AS object, count(*) AS rows,
       'MUST BE > 0 — empty means every dispatch FAILS' AS requirement
  FROM tb_batch_schedule_info
UNION ALL
SELECT 'tb_batch_schedule_dtl', count(*), 'required for the NAPAS real-time leg'
  FROM tb_batch_schedule_dtl
UNION ALL
SELECT 'tb_van_api_info (keyed)', count(*) FILTER (WHERE api_key IS NOT NULL AND api_key <> ''),
       'MUST BE > 0 — VanApiKeyService throws otherwise'
  FROM tb_van_api_info
UNION ALL
SELECT 'tb_firm_conf (ORG_STAT=1)', count(*) FILTER (WHERE org_stat = '1'),
       'MUST BE > 0 for the tenant'
  FROM tb_firm_conf
UNION ALL
SELECT 'shedlock', count(*), 'table must exist' FROM shedlock;
SQL
```

```bash
# The NAPAS_REAL_TIME_BATCH window — BOTH the real-time and BATCH1 paths read this INFO key.
psql -h <DB_HOST> -U <DB_USER> -d <DB_NAME> -c \
  "SELECT batch_id, out_bank_cd, batch_start_tm, batch_end_tm, ant_where_use_yn
     FROM tb_batch_schedule_info WHERE batch_id = 'NAPAS_REAL_TIME_BATCH';"
# Expect >= 1 row. HHmmss strings; the gate compares them lexicographically against ICT now().
```

### 3.7 HTTPS health check and certificate verification (EFS gateway)

```bash
# Reachability + HTTP status (does NOT send a transaction)
curl -sS -o /dev/null -w 'http=%{http_code} tls=%{ssl_verify_result} time=%{time_total}s\n' \
     --max-time 20 https://<EFS_GATEWAY_HOST>/

# Certificate chain, subject, issuer, validity — verify_return_error fails on an untrusted chain.
openssl s_client -connect <EFS_GATEWAY_HOST>:443 -servername <EFS_GATEWAY_HOST> \
        -verify_return_error </dev/null 2>&1 | head -30

# Expiry only
echo | openssl s_client -connect <EFS_GATEWAY_HOST>:443 -servername <EFS_GATEWAY_HOST> 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates

# If the chain is a private CA, confirm the JVM trusts it:
keytool -list -cacerts -storepass changeit | grep -i '<CA_NAME_FRAGMENT>'
```

### 3.8 SFTP test

```bash
# Port first
nc -vz -w 5 <SFTP_HOST> <SFTP_PORT>

# Key permissions must be 0600 (-rw-------)
ls -l <WECMS_SFTP_KEY_PATH>

# Interactive login + directory listing (read-only; 'bye' exits immediately)
sftp -P <SFTP_PORT> -i <WECMS_SFTP_KEY_PATH> <SFTP_USERNAME>@<SFTP_HOST> <<'EOF'
pwd
ls sdata
ls rdata
ls rdata/success
bye
EOF
```

### 3.9 Local file directory permission test (run as the application service account)

```bash
for d in "<WECMS_SFTP_LOCAL_UPLOAD>" "<WECMS_SFTP_LOCAL_DOWNLOAD>" \
         "<WECMS_INTERNAL_TRANSFER_STORAGE_ROOT>"; do
  echo "--- $d"
  ls -ld "$d"                                  # ownership + mode
  touch "$d/.wecms_write_probe" \
    && echo "WRITE OK" && rm -f "$d/.wecms_write_probe" \
    || echo "WRITE FAILED"
done

df -h "<WECMS_SFTP_LOCAL_UPLOAD>"              # disk headroom
```

> If `WECMS_SFTP_LOCAL_UPLOAD` / `_LOCAL_DOWNLOAD` are unset, the code falls back to
> `System.getProperty("java.io.tmpdir")`. Check there too — files must not be landing in the OS temp dir.

### 3.10 EFS mount test (only if the file service is a mounted network filesystem)

```bash
mount | grep -i -E 'nfs|efs|cifs'
findmnt -T "<WECMS_SFTP_LOCAL_UPLOAD>"
stat -f "<WECMS_SFTP_LOCAL_UPLOAD>"            # filesystem type + free blocks
touch "<WECMS_SFTP_LOCAL_UPLOAD>/.mount_probe" && rm -f "<WECMS_SFTP_LOCAL_UPLOAD>/.mount_probe"
```

> **Terminology note:** in this project "EFS" means the **Webcash EFS / VAN Center HTTP gateway**
> (`WGVN_EFS_*.jct` endpoints), **not** a network filesystem. §3.10 applies only if the staging directories
> happen to sit on a network mount.

### 3.11 Callback endpoint test (inbound)

```bash
# From OUTSIDE the app host — ideally from the VAN gateway's network.
# Reachability + that the path is mapped (a bad payload is expected to be rejected).
curl -sS -o /dev/null -w 'http=%{http_code}\n' --max-time 15 \
     -X POST 'https://<API_HOST>/api/v1/payment/dispatch/callbacks/<CALLBACK_PATH>' \
     -H 'Content-Type: application/json' -d '{}'
# Mapped => 200 with an EFS ack envelope, or a 4xx. A 404 means the path is not reachable.

# Verify the IP allow-list actually fails closed once WECMS_CALLBACK_ALLOWED_IPS is set:
# run the same call from a NON-allow-listed host -> expect HTTP 403.

# Verify the proxy does NOT honour a client-supplied X-Forwarded-For (audit H-2):
curl -sS -o /dev/null -w 'http=%{http_code}\n' \
     -H 'X-Forwarded-For: <AN_ALLOW_LISTED_IP>' \
     -X POST 'https://<API_HOST>/api/v1/payment/dispatch/callbacks/<CALLBACK_PATH>' \
     -H 'Content-Type: application/json' -d '{}'
# From a non-allow-listed host this MUST still be 403. A 200/4xx means XFF is spoofable — STOP.
```

### 3.12 Log hygiene check

```bash
grep -icE 'api_key|apikey|"user_pw"|"password"|BEGIN (RSA )?PRIVATE KEY' <APP_LOG_PATH>   # expect 0
grep -inE '"acct_no"\s*:\s*"[0-9]{6,}"' <APP_LOG_PATH> | head                             # unmasked accounts
grep -c 'com.kosign.wecms.*DEBUG' <APP_LOG_PATH>   # SIT is DEBUG: full bodies are logged (audit H-3)
```

---

## 4. Controlled validation (baseline — all dispatch flags OFF)

Run this after every deploy, before any activation.

- [ ] Application boots; §3.1 clean.
- [ ] `MockEfsApiClient` and `MockSftpClient` are the active beans.
- [ ] Login works; one Banking read and one Payment read succeed.
- [ ] Save a payment in each module (Vendor / Expense / Internal Transfer / Payroll / Overseas). Confirm
      `TB_PAY_TARGET` / `TB_PAY_FILE_MST` rows land at `PROC_STAT='9'` and **no** `TB_FIRM_TRAN` row is
      created.
- [ ] Neither dispatch scheduler appears in `shedlock` (§3.2).
- [ ] §3.12 log hygiene clean.
- [ ] Record the row counts of `TB_FIRM_TRAN`, `TB_PAY_TARGET`, `TB_PAY_FILE_MST` as the pre-activation
      baseline.

---

## 5. Activation sequence

### 5.0 Gates (all must be green before step 1)

| Gate | Requirement | Current |
|---|---|---|
| A | Callback path restricted by IP / mTLS / network; XFF spoofing test passes (§3.11) | **BLOCKED** — audit H-2 |
| B | `TB_BATCH_SCHEDULE_INFO` (+ `_DTL`) seeded and verified (§3.6) — **read §5.0-B below before seeding UAT/PROD** | **GREEN for DEV/SIT** (3 + 12 rows, audit §4.5). **BLOCKED for UAT/PROD** — not yet seeded. Residual: SWIFT/EBANK have no realtime DTL row (PO routing decision); OQ-4 CITAD 15:30 vs 16:30 open |
| C | EFS `base-url` + `partner-id` set; `TB_VAN_API_INFO.API_KEY` present for the environment | **BLOCKED** — partner id empty everywhere |
| D | EFS 201 `RS_CD='1000'` explained and resolved by VAN Center | **BLOCKED** — audit C-3 |
| E | Deployed flag state known and reconciled (§0) | **BLOCKED** — audit C-1 |
| F | Actuator present so health/metrics exist (§3.3) | **BLOCKED** — audit C-5 |
| G | For UAT: refactor DB confirmed **not** the JEX UAT batch DB | **BLOCKED** — audit dep. 26 |
| H | Both dispatch performance indexes applied and `indisvalid` (§5.0-H) | **OPEN** — scripts written 2026-07-30, **not applied in any environment** |

#### Gate B — seeding these two tables changes the LIVE JEX batch's operating hours

> **This is not stated anywhere else in this runbook, and it is the single most consequential property
> of gate B on UAT/PROD. Read it before running the seed.**

`TB_BATCH_SCHEDULE_INFO` and `TB_BATCH_SCHEDULE_DTL` have **no `BIZ_KEY` column**. Their primary keys are
`(BATCH_ID, OUT_BANK_CD)` and `(BATCH_ID, OUT_BANK_CD, ANT_DIV_CD)` — the windows are **global**, shared
by every tenant. On UAT/PROD the refactor points at the **live JEX DB**
(memory `project_uat_prod_live_jex_db_target`), and the legacy `NAPAS_REAL_TIME_BATCH` /
`FILE_DEPOSIT_TRANSFER_BATCH1` read the **same rows**. So:

- **Inserting a row here changes when the live JEX batch dispatches.** It is not a refactor-local
  configuration change. There is no tenant or system scoping available to limit it.
- **Widening a window lets legacy dispatch outside its current hours; narrowing one stops legacy
  dispatching inside them.** Either is a production behaviour change to a money-moving path owned by a
  system this project does not deploy.
- `ANT_WHERE_USE_YN` is equally shared: flipping it to `'Y'` for a bank makes legacy consult the
  per-channel DTL rows it previously ignored.

**Therefore, on UAT/PROD:**

1. Run [`tb_batch_schedule_seed_refactor.sql`](../../../Claude%20Code%20%28AI%29/sql/tb_batch_schedule_seed_refactor.sql)
   **§0.1 first** and read the output. If live-JEX rows already exist, they are the authority —
   `ON CONFLICT DO NOTHING` preserves them and that is the correct outcome. **Do not** "correct" a
   legacy window to match the refactor's expectation.
2. Never use the script's §6 `DO UPDATE` re-align variant on a shared DB without an explicit PO
   (Mr. Jung) decision recorded against the specific rows. It rewrites windows legacy is using.
3. Capture the before-image (§7.1 `\copy … CSV HEADER`) **before** any write, so a legacy window can be
   restored exactly.
4. If a required row is genuinely absent, the decision to add it is a **PO + bank** decision about
   legacy's operating hours, not a refactor deployment step. The VCB (`970436`) case is the worked
   precedent: legacy has no `INFO` row, so VCB dispatch fails `NO_CRITERIA` — and that row was
   deliberately withdrawn rather than added, because adding it would have given legacy an operating
   window it does not currently have. See
   [support-matrix.md](../references/support-matrix.md) §11a R-1/R-2.

**What happens if the tables stay unseeded:** `OperatingHoursService` returns `NO_CRITERIA`, and both
`BulkReceptionService` and `RealtimeDispatchService` **FAIL the row** — 100% of refactor transfers fail
before any gateway call, with `RS_MSG = "No data to perform bulk transfer batch. Please check
TB_BATCH_SCHEDULE_INFO."` on the bulk path and no message on the realtime path. Legacy is unaffected
(it has its own rows). So an unseeded UAT/PROD is a refactor-only outage, whereas a mis-seeded one is a
**legacy** incident. The asymmetry is why this gate is worth stopping on.

#### Gate H — the two dispatch indexes are UAT/PROD preconditions, not optimisations

Two 2026-07-30 fixes removed a `TRAN_DT` predicate from a query that had been relying on it for index
access. Both queries are correct without an index and **both currently run as sequential scans**:

| Script | Query | Runs on | Frequency |
|---|---|---|---|
| [`tb_firm_bat_api_tran_idempotency_index_20260730.sql`](../../../Claude%20Code%20%28AI%29/sql/tb_firm_bat_api_tran_idempotency_index_20260730.sql) | `existsByBizKeyAndFirmFileNmAndApiTranTp` — the `00302` idempotency guard (FIX D) | the **01301 callback thread**, synchronously, **before the 00302 gateway call** | once per bulk file |
| [`tb_firm_file_mst_stranded_sweep_index_20260730.sql`](../../../Claude%20Code%20%28AI%29/sql/tb_firm_file_mst_stranded_sweep_index_20260730.sql) | `findStrandedBulkExecutionFiles` — the stranded-file sweep (FIX H) | the stuck-monitor scheduler thread | every 5 min while `stuck-monitor.enabled=true` |

Neither table has an index that serves its query: `TB_FIRM_BAT_API_TRAN` has only its PK
(leading `TRAN_DT`) and `TB_FIRM_FILE_MST` only its PK (leading `TRAN_DT`) — verified read-only
2026-07-30 on both reachable DBs.

**Why this is a gate and not a nice-to-have.** The seq scans were accepted on *measured* size — JEX DEV
`TB_FIRM_BAT_API_TRAN` 1,304 rows / 360 kB, `TB_FIRM_FILE_MST` 122 rows / 88 kB. **That measurement does
not transfer to UAT/PROD**, because there the refactor runs against the **live JEX DB**
(memory `project_uat_prod_live_jex_db_target`), whose copies of these tables carry the live system's full
history and are not observable from this workspace. `TB_FIRM_BAT_API_TRAN` in particular accumulates one
row per API leg ever made and is never pruned, so it is the table most likely to be large and the one
whose scan sits on a money path inside a gateway request that the VAN Center times out.

So: **apply both before enabling any dispatch leg in UAT or PROD** (i.e. before step 4 of §5.1), and
record the row count and `EXPLAIN` output at the time. Both scripts are additive
`CREATE INDEX CONCURRENTLY IF NOT EXISTS`, take no exclusive lock, are behaviour-neutral for legacy, and
are independent of each other and of every other step — but they are DDL on a **shared** table and need
the same DBA sign-off as gate B's seed. Verify `indisvalid = true` afterwards (§2.1 of each script); a
`CONCURRENTLY` build that fails part-way leaves an invalid index that serves nothing.

**On DEV/SIT they remain optional.** At 122 / 1,304 rows the planner will choose a seq scan even with
the index present, and be right to — a post-apply `EXPLAIN` showing one there is not a failed apply.

### 5.1 Proposed order

Enable **one** component per window. Restart is required after each change (no `@RefreshScope`; see §7.2).
After each step run the step's verification, then §6 monitoring for at least one full business cycle before
moving on.

| Step | Component | Variables to change | Why this position |
|---|---|---|---|
| **1** | **Callback APIs** (network restriction) | `WECMS_CALLBACK_ALLOWED_IPS=<VAN_IP_1>,<VAN_IP_2>` | The endpoints are **already live and unauthenticated** — this is the only step that *reduces* exposure. It must come first because every later step causes the gateway to call back to us, and we must not open a money-moving inbound path we have not restricted. Verify with §3.11 (allow-listed host → 200/4xx; other host → 403; spoofed XFF → 403). |
| **2** | **EFS transport + recipient inquiry** | `WECMS_EFS_ENABLED=true`, `WECMS_DISPATCH_RECIPIENT_INQUIRY=true` | EFS 201 is **read-only** — it inquires a recipient name and moves no money. It exercises the whole risky substrate (TLS, partner id, VAN `API_KEY`, `EV`/`VV` crypto, envelope shape) at zero financial risk. Success criterion: a `TB_FIRM_TRAN` `TRAN_TP='201'` row with `RS_CD='0000'`. This is exactly the leg that is currently failing with `1000`, so it is also the cheapest place to fix the integration. |
| **3** | **EFS/file dispatch — SFTP transport only** | `WECMS_SFTP_ENABLED=true` (leave `auto-receive=false`) | Turns on the file channel without triggering any dispatch. Verify with §3.8 plus one manual round trip of a throwaway file. Separating transport from dispatch means a path/permission error surfaces before real payment files exist. |
| **4** | **EFS/file dispatch — bulk 00301** | `WECMS_DISPATCH_AUTO_RECEIVE=true` | Bulk file dispatch is **asynchronous and reversible before the bank acts**: the file is built, uploaded and audited, and the result arrives via the 01302 callback (already restricted in step 1). It is preferred ahead of real-time because a mistake leaves a file and an audit trail rather than an immediately executed transfer. Requires gate B (green on DEV/SIT since 2026-07-28) and, **on UAT/PROD, gate H** — this is the first step whose callback path runs the un-indexed `00302` idempotency probe on a money path (§5.0-H). On the bulk path a *missing* DTL row passes, so this leg is the more forgiving of the two. |
| **5** | **NAPAS real-time dispatch** | `WECMS_DISPATCH_REALTIME_DISPATCH=true` | The **highest-risk** step: EFS 202 moves money synchronously, with no file to inspect and no cancellation window. It goes after the bulk path because by this point TLS, keys, crypto, envelopes, operating-hours gating and the callback path have all been proven end to end. Verify `PROC_STAT 9→6→0` and that `TNO == TRAN_SEQ`. **Coverage caveat:** the seeded realtime DTL rows cover `970424`/`970457` × `NAPAS`/`CITAD` only. `INTER` short-circuits to DISPATCH (all 4 currently-pending rows are `INTER`), but a target with `ANT_DIV_CD` of `SWIFT`/`EBANK` (or `'S'`/`'E'`) still fails `NO_CRITERIA` — a PO routing decision, not a window gap. |
| **6** | **Result-check scheduler** | `WECMS_DISPATCH_RESULT_CHECK_ENABLED=true` **and** `WECMS_DISPATCH_RESULT_CHECK_CRON=<cron>` — **both gates, like every other sweep** | Deliberately **after** dispatch: with nothing dispatched it has nothing to reconcile, and enabling it earlier would only scan an empty set while adding a scheduled write path. **No longer blocked — audit H-7 was resolved by D-1** (`ResultCheckService`, 2026-07-29): the scan's date bound now evaluates `TRAN_DT` in **UTC**, the same zone the writer stamps it in (`DateTimeUtils.utcNow()` / `PymnHelper:2197`), so the 00:00–07:00 ICT window that stranded rows at `PROC_STAT='6'` is closed. D-1 also widened the bound to accept the previous UTC date so a row dispatched just before midnight cannot be orphaned by the rollover. **This does not mean timezone handling is closed.** The same method still evaluates two clocks — the date bound in UTC and the age bound in ICT — because `RQ_DTTM`/`TRAN_TM` are stamped in ICT (`RealtimeDispatchService:146-148`) while `TRAN_DT` is stamped in UTC. Both readers match their own writer, and C-10 (2026-07-30 independent audit) verified that and made both derive from a single instant; the remaining open item is the **writer-side** inconsistency that the 203 audit row's own `TRAN_DT` is stamped in ICT (`ResultCheckService.reflectOutcome`) while every 202 row's is UTC — see §6 before interpreting `TRAN_DT` in any reconciliation query. Start with a conservative cron (e.g. every 5 minutes) rather than every minute. |
| **7** | **Stranded recovery scheduler** | `WECMS_DISPATCH_RECOVERY_ENABLED=true` **and** `WECMS_DISPATCH_RECOVERY_CRON=<cron>` | **Last, and only in a DB-separated environment.** It is a blind sweep over `'9'`/`'6'` rows and on a shared DB it cannot distinguish refactor rows from legacy-JEX rows (both draw the same global `firm_seq_no`/`firm_file_seq_no`). It is also the only component that is *useless* until the earlier legs have produced real stranded rows, so there is nothing to gain from enabling it early. DEV/SIT are verified DB-separated; UAT requires gate G. Both gates must be opened together.<br><br>**Depends on step 6.** This sweep covers four strand classes, and one of them — aged firm transactions at `TRAN_TP='202'`/`PROC_STAT='6'` awaiting 203 confirmation — is delegated to the result-check body, which has its own master gate. With `WECMS_DISPATCH_RESULT_CHECK_ENABLED=false` the other three classes still recover and that one is **skipped**, so the pass is partial. It is not silent (the sweep logs a WARN naming the flag — grep `aged firm-transaction .* recovery SKIPPED`), but if you enable step 7 without step 6 you are running a recovery that cannot recover `'6'` rows. Enable step 6 first, or accept the gap knowingly. |
| **8** | **Ops alerting** | `WECMS_DISPATCH_ALERT_ENABLED=true` (+ the two alert YAML lines) | Requires a working EFS transport (it posts through `WGVN_MSG_SEND_001`). Enabling it before EFS works produces nothing; enabling it before step 4/5 would only generate noise from the very failures you are deliberately provoking during earlier verification. Verify: a forced failure alerts; a success does **not**. |

### 5.2 Why this order is safe — the principle

Each step either **reduces** attack surface or adds the **least destructive** capability that still proves the
next step's prerequisites:

- Inbound before outbound (step 1) — never leave an unrestricted money-moving callback open while causing the
  gateway to call it.
- Read-only before write (step 2) — validate transport, identity and crypto with an inquiry that cannot move
  money.
- Transport before dispatch (steps 3 → 4) — separate "can we reach the file service" from "do we send payment
  files".
- Asynchronous before synchronous (step 4 → 5) — a bulk file can be inspected and stopped; a real-time
  transfer cannot.
- Reconciliation after production (step 6) — a result-checker with nothing to check adds risk and proves
  nothing.
- Blind sweeps last (step 7) — recovery is the only component that can act on rows it did not create.
- Observability last only because it depends on the transport (step 8) — until then, rely on the DB queries in
  §6 and the `shedlock` heartbeat.

---

## 6. Monitoring during and after activation

Until actuator exists (audit C-5), these DB queries plus log greps are the monitoring surface.

```sql
-- Dispatch outcomes today (0 = success, 3 = fail, 6 = in-flight, 9 = queued)
SELECT tran_dt, tran_tp, proc_stat, rs_cd, count(*)
  FROM tb_firm_tran
 WHERE tran_dt >= to_char(now() - interval '1 day', 'YYYYMMDD')
 GROUP BY 1,2,3,4 ORDER BY 1 DESC, 2, 3;

-- Queued too long (pending)
SELECT biz_key, tran_dt, tran_seq, proc_stat, proc_dttm
  FROM tb_pay_target WHERE proc_stat = '9' ORDER BY proc_dttm LIMIT 50;

-- Stranded / in-flight beyond the grace window
SELECT biz_key, tran_dt, tran_seq, proc_stat, proc_dttm
  FROM tb_pay_target WHERE proc_stat = '6' ORDER BY proc_dttm LIMIT 50;
SELECT biz_key, tran_dt, file_seq_no, proc_stat, proc_dttm
  FROM tb_pay_file_mst WHERE proc_stat IN ('6','9') ORDER BY proc_dttm LIMIT 50;

-- Scheduler heartbeat
SELECT name, locked_at, lock_until, locked_by FROM shedlock ORDER BY locked_at DESC;

-- Gateway result-code distribution (watch for 1000 / G999)
SELECT rs_cd, count(*) FROM tb_firm_tran GROUP BY 1 ORDER BY 2 DESC;
```

```bash
# Failure signals
grep -icE 'auto-receive \(00301\) failed|realtime-dispatch \(202\) failed' <APP_LOG_PATH>
grep -icE 'callback VV verification failed'                                 <APP_LOG_PATH>
grep -icE 'WBE_EFS_GATEWAY_ERROR|MockEfsApiClient'                          <APP_LOG_PATH>
grep -icE 'Callback rejected — source IP'                                   <APP_LOG_PATH>
grep -icE 'WBE_DB|DataAccessException'                                      <APP_LOG_PATH>
df -h <WECMS_SFTP_LOCAL_UPLOAD>
```

**Stop-and-roll-back triggers:** any unexpected `PROC_STAT='3'`, any `RS_CD` other than `0000` on a leg you
just enabled, any `callback VV verification failed`, any `Callback rejected` from an expected source, a
pending count that does not drain within 15 minutes, or any stranded `'6'` row.

---

## 7. Rollback procedure

### 7.1 Immediate stop (no redeploy required)

Set on the host, then restart:

```bash
WECMS_DISPATCH_REALTIME_DISPATCH=false     # stop real-time (202)
WECMS_DISPATCH_AUTO_RECEIVE=false          # stop bulk (00301)
WECMS_DISPATCH_RECIPIENT_INQUIRY=false     # stop 201 inquiry
WECMS_DISPATCH_RESULT_CHECK_CRON=-         # stop the result-check scheduler
WECMS_DISPATCH_RESULT_CHECK_ENABLED=false  # second result-check gate (also stops the recovery delegation)
WECMS_DISPATCH_RECOVERY_CRON=-             # stop the recovery scheduler
WECMS_DISPATCH_RECOVERY_ENABLED=false      # second recovery gate
WECMS_DISPATCH_ALERT_ENABLED=false         # optional: silence alerting
# Hard stop of all outbound EFS traffic in one variable:
WECMS_EFS_ENABLED=false                    # MockEfsApiClient throws => nothing leaves
WECMS_SFTP_ENABLED=false                   # MockSftpClient => no file I/O
```

**No code change and no redeploy is needed** — every leg is flag-gated.

### 7.2 Is a restart required?

**Yes.** All gates are read via `@Value` / `@ConfigurationProperties` with **no** `@RefreshScope`, and there
is no Spring Cloud Config or actuator `/refresh` endpoint (actuator is not even a dependency). A flag change
takes effect only on restart. Confirm the restart duration with Ops and record it.

**Cron-only exception:** none — `@Scheduled(cron = "${...}")` is resolved once at bean creation.

### 7.3 Preserving in-progress transactions

Rollback is safe for in-flight work by construction:

- Dispatch hooks run in `afterCommit`, so a committed payment is **never** rolled back by stopping dispatch.
- Rows simply stay at their current `PROC_STAT` (`'9'` queued, `'6'` claimed/in-flight). Nothing is lost.
- `TB_FIRM_TRAN` / `TB_FIRM_BAT_API_TRAN` audit rows are written `REQUIRES_NEW`, so the audit trail survives
  regardless.
- The `'6'` claim is atomic (`TbPayTargetRepository.claimForDispatch`), so a restart mid-flight cannot cause a
  double claim.

**Preventing new dispatches** is exactly §7.1 — the registrars no-op and the pay rows stay at `'9'`.

### 7.4 Identifying transactions that need manual review

Read-only queries. **No destructive `UPDATE` is sanctioned in this runbook** — any status correction must be a
separate, PO-approved, DBA-executed change.

```sql
-- A. Claimed but never finalised (the classic strand)
SELECT biz_key, tran_dt, tran_seq, proc_stat, proc_dttm
  FROM tb_pay_target WHERE proc_stat = '6' ORDER BY proc_dttm;

-- B. Bulk files claimed/queued but not completed
SELECT biz_key, tran_dt, file_seq_no, proc_stat, proc_dttm
  FROM tb_pay_file_mst WHERE proc_stat IN ('6','9') ORDER BY proc_dttm;

-- C. Requested at the gateway with no terminal result
SELECT tran_dt, tran_seq, tran_tp, proc_stat, rs_cd, rs_msg, rq_dttm
  FROM tb_firm_tran WHERE proc_stat NOT IN ('0','3') ORDER BY rq_dttm;

-- D. Failed with a gateway code (currently: every 201 with rs_cd='1000')
SELECT tran_dt, tran_seq, tran_tp, rs_cd, rs_msg, rq_dttm
  FROM tb_firm_tran WHERE proc_stat = '3' ORDER BY rq_dttm DESC;

-- E. A file was sent but no execution result came back
SELECT m.biz_key, m.tran_dt, m.firm_file_seq_no
  FROM tb_firm_file_mst m
  LEFT JOIN tb_firm_file_rslt r
         ON r.biz_key = m.biz_key AND r.tran_dt = m.tran_dt
 WHERE r.biz_key IS NULL;
```

Rows from **A**, **B**, **C** and **E** are the manual-review set: for each, confirm with the bank whether the
transfer actually executed **before** any status is corrected.

### 7.5 Reverting to the legacy JEX batch

- Legacy JEX batch is configured **running** in DEV, UAT and PROD (`JEX_BATCH=true` in
  `WECMS_BATCH/web/WEB-INF/jex.prop`) and **absent in SIT** (refactor-only environment).
- **DEV / SIT:** nothing to revert — the refactor DB (`wecmsdevdb` @ `172.168.5.172`) is a **separate
  instance** from the JEX DEV frame DB (`wecmsvndevdb` @ `10.254.242.254`). The legacy batch never touched
  the refactor rows.
- **UAT / PROD:** the refactor is not the system of record. Disabling the refactor flags (§7.1) is sufficient;
  the legacy batch continues on its own every-minute cadence.
- **Do not run both systems against the same firm-banking rows.** Refactor rows are indistinguishable from
  JEX rows (shared global sequences via `wb_get_next_val`, no source marker). If the DB target is shared, Ops
  and the PO must decide which system owns dispatch for that window — that decision is a prerequisite, not a
  rollback step.

---

## 8. Sign-off

One block per environment, per activation step. Do not proceed to the next step without a completed block.

| Field | Value |
|---|---|
| Environment | DEV / SIT / UAT / PROD |
| Activation step (§5.1) | 1 · 2 · 3 · 4 · 5 · 6 · 7 · 8 |
| Component enabled | |
| Variables changed (names only, no values) | |
| Deployed flag state captured (§0) | ☐ |
| Gates §5.0 A–G verified green | ☐ |
| Pre-deployment checks §1 passed | ☐ |
| Connectivity tests §3 passed (list which) | |
| Baseline validation §4 passed | ☐ |
| Step verification passed | ☐ |
| Monitoring §6 observed for one full business cycle | ☐ |
| Rollback rehearsed / confirmed available | ☐ |
| Issues found | |
| **Ops** — name / date / signature | |
| **DBA** — name / date / signature | |
| **Dev (Arthur)** — name / date / signature | |
| **VAN Center** — confirmation reference | |
| **PO (Mr. Jung)** — approval / date | |
| **Team lead (Sonny)** — approval / date | |

**Production activation additionally requires:** written PO sign-off on the coexistence decision (audit
dep. 26), completed secret rotation (Coocon keys), the callback restriction verified from outside the network
(§3.11), a tested DB restore, and monitoring + alerting confirmed live.
