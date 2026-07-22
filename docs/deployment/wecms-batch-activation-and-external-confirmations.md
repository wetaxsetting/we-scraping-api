# WeCMS Batch — Activation Status, Runtime Config Audit & External Confirmations

**Date:** 2026-07-21 · **System:** Refactor (`wecms-api`) firm-banking dispatch
**Purpose:** deliverables 3, 5, 6, 7, 8 of the "implement all remaining WeCMS Batch items" task —
the runtime configuration audit for DEV/SIT/UAT, the concrete external-confirmation tasks (with owner
+ exact question), and the per-component activation status. Companion to the parity audit
[wecms-batch-refactor-parity-audit-2026-07-21.md](../audits/wecms-batch-refactor-parity-audit-2026-07-21.md).

> **No secrets in this document.** Where a runtime value is a credential/key/host, only its
> *presence/absence* and *source location* are recorded, never the value.

---

## 1. How dispatch is configured (source vs runtime)

Every dispatch flag in **all** profiles (`application-{local,sit,uat,prod}.yml`) binds to an
**environment variable with no in-yaml default**, e.g.:

```yaml
wecms:
  dispatch:
    auto-receive:      ${WECMS_DISPATCH_AUTO_RECEIVE}
    realtime-dispatch: ${WECMS_DISPATCH_REALTIME_DISPATCH}
    recipient-inquiry: ${WECMS_DISPATCH_RECIPIENT_INQUIRY}
    result-check: { cron: ${WECMS_DISPATCH_RESULT_CHECK_CRON} }
    alert:    { enabled: ${WECMS_DISPATCH_ALERT_ENABLED} }
    efs:      { enabled: ${WECMS_EFS_ENABLED}, base-url: ${WECMS_EFS_BASE_URL}, partner-id: ${WECMS_EFS_PARTNER_ID} }
    sftp:     { enabled: ${WECMS_SFTP_ENABLED}, host: ${WECMS_SFTP_HOST}, ... }
    callback: { allowed-ips: ${WECMS_CALLBACK_ALLOWED_IPS} }
    recovery: { enabled: ${WECMS_DISPATCH_RECOVERY_ENABLED:false}, cron: ${WECMS_DISPATCH_RECOVERY_CRON:-}, grace-minutes: ${...:30} }  # NEW (Item 4)
```

**Three distinct layers — do not conflate them:**
- **(a) Source-code default** — the `@Value`/`EfsProperties` fallback used only when the env var is
  unset: `false` / `"-"` / mock bean (`MockEfsApiClient` / `MockSftpClient`). The new recovery keys are
  the only ones with an in-yaml default (`:false` / `:-` / `:30`).
- **(b) Committed runtime config** — the checked-in `.env.*` files (`.env.local`, `.env.sit` are
  actual; `.env.{uat,prod}.example` are placeholders). These are the *intended* per-env values.
- **(c) Deployed runtime authority** — what the running JVM actually reads. Per memory
  `reference_sit_server_config_setenv`, the **SIT server loads `bin/setenv.sh` (OS env), not
  `.env.sit`** — so `.env.sit` is layer (b), and the server's `setenv.sh` (layer c) is authoritative
  and **is not in this repository**. The only layer-(c) signal available from here is *DB behavior*
  (§2.1) — which is corroborating, not the flag value itself.

## 2. Runtime configuration audit — DEV / SIT / UAT / PROD

Values below are **layer (b) — committed `.env.*`** unless noted. Layer (c) (deployed `setenv.sh`)
must be read on the host with `env | grep WECMS_` (owner: Ops/Arthur) to confirm as fact.

| Component | Env var | (a) Code default | (b) DEV `.env.local` | (b) SIT `.env.sit` | (b) UAT / PROD | Layer-(c) status |
|---|---|---|---|---|---|---|
| EFS gateway `efs.enabled` | `WECMS_EFS_ENABLED` | `false`→`MockEfsApiClient` | **`false`** | **`false`** | `.example`=`false` (real not committed) | server `setenv.sh` — Ops confirm |
| EFS base-url / partner-id | `WECMS_EFS_BASE_URL`/`_PARTNER_ID` | empty | empty | empty | placeholder | Not in repo (task B1) |
| SFTP `sftp.enabled` | `WECMS_SFTP_ENABLED` | `false`→`MockSftpClient` | **`false`** | **`false`** | `.example`=`false` | server `setenv.sh` — Ops confirm |
| `auto-receive` (bulk 00301) | `WECMS_DISPATCH_AUTO_RECEIVE` | `false` | **`false`** | **`false`** | `.example`=`false` | server `setenv.sh` — Ops confirm |
| `realtime-dispatch` (202) | `WECMS_DISPATCH_REALTIME_DISPATCH` | `false` | **`false`** | **`false`** | `.example`=`false` | server `setenv.sh` — Ops confirm |
| `recipient-inquiry` (201) | `WECMS_DISPATCH_RECIPIENT_INQUIRY` | `false` | **`false`** | **`false`** | `.example`=`false` | server `setenv.sh` — Ops confirm |
| `result-check.cron` (203 scan) | `WECMS_DISPATCH_RESULT_CHECK_CRON` | `"-"` never | **`"-"`** | **`"-"`** | `.example`=`"-"` | server `setenv.sh` — Ops confirm |
| `alert.enabled` (Slack ops) | `WECMS_DISPATCH_ALERT_ENABLED` | `false` | **`false`** | **`false`** | `.example`=`false` | server `setenv.sh` — Ops confirm |
| callback allowed-ips | `WECMS_CALLBACK_ALLOWED_IPS` | empty | empty | empty | placeholder | Endpoints always mapped, **unauthenticated** (R-03); gated only by network/IP allowlist. No on/off flag. |
| **recovery (NEW, Item 4)** | `WECMS_DISPATCH_RECOVERY_ENABLED`/`_CRON` | `false`/`"-"` | not set → OFF | not set → OFF | not set → OFF | Double-gated OFF everywhere; keep OFF until §4. |
| Real vs mock gateway | (derived) | **mock** | **mock** (efs+sftp false) | **mock** (efs+sftp false) | mock in templates | `RestClient*`/`Jsch*` active only when the enable flag is `true`. |

**Committed DB targets (layer b):** `.env.local` = `.env.sit` = `jdbc:postgresql://172.168.5.172:5432/wecmsdevdb`
(the refactor/shared-dev DB). `.env.{uat,prod}.example` carry `<uat-db-host>` / `<prod-db-host>` placeholders
— the **real UAT/PROD DB URLs are not committed**.

### 2.1 Actual deployed *behavior* evidence (shared dev/SIT DB `172.168.5.172/wecmsdevdb`)

This is the one hard, repo-independent signal available. Queried read-only 2026-07-21:

| Table | State | Reading |
|---|---|---|
| `TB_VAN_API_INFO` | 2 rows, both with a non-empty `API_KEY` | **B2 provisioned** (keys present; values not read). |
| `TB_FIRM_CONF` | 3 rows, all `ORG_STAT='1'` | **I5 provisioned** (connecting-org available). |
| `TB_PAY_TARGET` | 4 rows, **all `PROC_STAT='9'`** (tenant `VN_VNM0004_000001`, 2026-06-12…26) | Real-time dispatch **never advanced** these rows. |
| `TB_PAY_FILE_MST` | 2 rows, **all `PROC_STAT='9'`** | Bulk reception **never advanced** these rows. |
| `TB_FIRM_TRAN` / `TB_FIRM_BAT_API_TRAN` / `TB_FIRM_FILE_DATA` / `TB_FIRM_FILE_RSLT` | **all empty** | **No 201/202/203/204/301/302 leg has ever executed** against this DB. |

**Interpretation (evidence-bound):** on the shared dev/SIT DB, dispatch has **not processed** any
row despite B2/I5 provisioning — consistent with the dispatch flags being OFF there, or the
after-commit hooks never having fired for these rows (an R-1 strand). This is *behavioral* evidence
from the DB, **not** a claim about the SIT/UAT `setenv.sh` flag values (which remain unread). If SIT
had run real end-to-end dispatch against this DB, `TB_FIRM_TRAN` would be non-empty.

> **Action for the team (owner: Arthur / Ops):** capture `env | grep WECMS_` on the running SIT and
> UAT app hosts and paste the (secret-redacted) flag values into this table's SIT/UAT columns. That is
> the only way to state SIT/UAT runtime flags as fact rather than "server env".

### 2.2 Legacy JEX batch running status + DB sharing (per environment)

Sources: legacy `WECMS_BATCH/web/WEB-INF/jex.prop` (per-container `JEX_BATCH` flag) + the scheduler
inventory `WECMS_BATCH DEV/analysis/migration/developer-migration/06-scheduler-inventory.md` + live DB
identity of both MCP servers + committed `.env.*` DB URLs. The legacy batch cron is every-minute for
all four batches; whether it *runs* on a node is the container's `JEX_BATCH` flag.

| Env | Legacy JEX batch running? | Evidence | Refactor (Spring) DB | Legacy JEX DB | Shared DB? |
|---|---|---|---|---|---|
| **DEV** | **YES** | `WECMS_BATCH_DEV` `JEX_BATCH=true`; JEX DEV DB (`wecmsvndevdb`) has **10,151** `tb_firm_tran` rows (latest `tran_dt` 2026-06-29), 136 `tb_pay_file_mst` (latest 2026-07-17) → active | `wecmsdevdb` @ `172.168.5.172:5432` (`.env.local`) | `wecmsvndevdb` @ `10.254.242.254:54311` (live MCP + `database.md`) | **NO** — different host + database |
| **SIT** | **NO legacy batch** | SIT is a **refactor-only** environment (`wecms.kosign.dev`, memory `reference_sit_endpoints`); there is **no `WECMS_BATCH_SIT` container** in `jex.prop` (only DEV/UAT/REAL_01/REAL_02/REAL_HS) | `wecmsdevdb` @ `172.168.5.172:5432` (`.env.sit`) | — (no legacy batch in SIT) | **N/A** — no legacy batch to collide with |
| **UAT** | **YES (config)** | `WECMS_BATCH_UAT` `JEX_BATCH=true` (`jex.prop` + scheduler inventory §3) — config evidence; live UAT not queryable from here | **Not committed** (`.env.uat.example` placeholder) | JEX UAT DB (live UAT, not queryable) | **UNVERIFIABLE from repo** — see below |
| **PROD** | **YES (config)** | `WECMS_BATCH_REAL_01` `JEX_BATCH=true` + `REAL_HS=true`; `REAL_02=false` (single active batch node + separate Haengsung node) — config evidence; PROD DB forbidden to this review | Refactor **not deployed to PROD** (workspace rule: local/SIT/UAT R&D only) | JEX PROD DB (forbidden) | **N/A / UNVERIFIABLE** |

**DEV/SIT — coexistence is SAFE by DB separation (verified).** The Spring refactor's DEV/SIT DB
(`wecmsdevdb` @ `172.168.5.172`) is a **different instance** from the JEX DEV frame DB
(`wecmsvndevdb` @ `10.254.242.254`) — confirmed by both live MCP identities, `database.md`, and the
committed `.env.*` URLs. The legacy JEX firm-banking rows live in `wecmsvndevdb`; the refactor's live
in `wecmsdevdb`. They do **not** share firm-banking rows, so a refactor recovery sweep in DEV/SIT
cannot double-process a JEX-originated row. *(Note: the 2026-06-09 memory `project_uat_prod_live_jex_db_target`
said the refactor "runs against the live JEX DB"; the live connection evidence shows a **separate
JEX-schema instance** in DEV/SIT — that memory reads as schema-source-of-truth parity, not same-instance.)*

**UAT/PROD — coexistence is UNVERIFIED (Ops gate).** The real UAT/PROD `WECMS_DB_URL` is not
committed. If the refactor UAT/PROD is pointed at the **same** DB the legacy JEX UAT/PROD batch uses,
the two systems would share firm-banking rows and a recovery sweep could double-process — because
refactor rows are **not distinguishable** from JEX rows (shared global `firm_seq_no`/`firm_file_seq_no`
sequences via `wb_get_next_val`; no source column / app id / cutover marker). **Ops must compare the
deployed UAT/PROD `WECMS_DB_URL` against the JEX UAT/PROD batch DB before enabling any dispatch leg or
the recovery scheduler there.**

## 3. Per-component activation status

| Component | Code status | Activation status |
|---|---|---|
| Fixed-length file codec, crypto, sequence funnel | Implemented + verified | Always active (pure functions). |
| Result-check 203 + atomic finalize (Item 1) | Implemented + verified | Runs only when `result-check.cron` set (default off). |
| Real-time 202 + 204 FX + G2 atomic claim | Implemented + verified | Runs only when `realtime-dispatch=true` (default off). |
| Bulk 00301 + per-file tx (Item 2) | Implemented + verified | Runs only when `auto-receive=true` (default off). |
| 01301 / 01302 callbacks + REQUIRES_NEW audit (G1) | Implemented + verified | Endpoints always mapped; **unauthenticated** (R-03 — restrict by network before non-local). |
| Ops alerts (H2) | Implemented + verified | Runs only when `alert.enabled=true` (default off). |
| Overseas town-name origination + 35-char guard (Item 3) | Implemented + verified | Active on overseas payment save/dispatch. |
| **R-1 stranded-recovery (Item 4)** | **Implemented + DISABLED** | **Blocked by legacy coexistence (§4). Cron + master flag both default OFF.** |

## 4. Legacy-coexistence gate for the R-1 recovery scheduler (per environment)

Refined from §2.2's verified findings — the gate is now **environment-specific**, not blanket:

| Env | Coexistence status for enabling recovery/dispatch |
|---|---|
| **DEV** | **Safe re: legacy** — the refactor DB (`wecmsdevdb`) is a separate instance from the JEX DEV DB (`wecmsvndevdb`); the legacy batch never touches `wecmsdevdb`. A recovery sweep here can only see refactor-originated rows. (Enabling is still an *operational* choice — it would fire real EFS/SFTP if those flags are also on.) |
| **SIT** | **Safe re: legacy** — no legacy JEX batch runs in SIT (refactor-only env). |
| **UAT** | **BLOCKED until Ops confirms the DB target.** If the refactor UAT `WECMS_DB_URL` = the JEX UAT batch DB, the two share firm-banking rows and recovery could double-process (rows are indistinguishable — no source marker). Confirm the two DB URLs differ, **or** apply a distinguisher/cutover before enabling. |
| **PROD** | **N/A** — refactor not deployed to PROD; the legacy batch owns PROD (`REAL_01` + `REAL_HS`). No refactor recovery in PROD until a separate deploy + coexistence decision. |

**Open decision (owner: PO / Mr. Kang), only material for UAT (and any future PROD deploy):** if the
refactor and legacy JEX will ever share a firm-banking DB, provide a **source/origin marker, a cutover
date, or an explicit "legacy batch off in this env" statement** — because both systems draw the same
global `firm_seq_no`/`firm_file_seq_no` sequences via `wb_get_next_val`, so refactor rows are **not
distinguishable** from JEX rows today.

**Rule (enforced in code):** the recovery scheduler is double-gated OFF
(`wecms.dispatch.recovery.enabled=false` + cron `"-"`); `StrandedRecoveryService.recoverOnce()` no-ops
while disabled. The per-row atomic `'9'→'6'` claim guards concurrency **within** the refactor only, not
across the two systems on a shared DB.

## 5. External confirmation tasks (concrete)

### OQ-1 — `data_dcm_amt` decimal-amount form  ·  **CLOSED — CONFIRMED (2026-07-21)**
- **Decision:** use exactly the latest legacy `BigDecPaser` form; keep the current `AmountSplitter` legacy-parity implementation. Examples: `1000`→`"000"`, `1000.00`→`"00"`, `1000.05`→`"05"`, `1000.50`→`"50"`.
- **Code state:** `AmountSplitter` reproduces `BigDecPaser` (H-6). `AmountSplitterTest` covers all four confirmed formats (`15.00`→`00`, `15.05`→`05`, `1234.50`→`50`, whole→`000`) + the BigDecimal scale cases.
- **No longer an external blocker.** During SIT, capture **one** real outbound request as verification evidence only — not a pending design decision.

### OQ-2 — VCB transfer method + VND/USD file fields  ·  **CLOSED — CONFIRMED + IMPLEMENTED (2026-07-21)**
- **Business decision (Mr. Jung 2026-07-16):** VCB (970436) cannot support the real-time Transfer API without a certificate, so **ALL VCB transfers — both VND and USD — use File-based dispatch** (same pattern as Shinhan USD file-only). Not an open routing question. Backup kept in git branch history + legacy SVN r36803.
- **Routing (CONFIRMED + IMPLEMENTED):** `isVcbFileTransfer` pulls every VCB case off the API path in Vendor / Expense / Internal Transfer; VCB never uses the real-time API. Same-bank and other-bank VND both handled; the VCB→other-bank reject is preserved **USD-only** (legacy parity), so VCB VND→other is allowed. Shinhan / Woori routing unchanged.
- **`BANK_CHNL_DIV` / `ANT_DIV_CD` field values — CONFIRMED from legacy source** (`PymnHelper` latest, Mr. Jung 2026-07-16 switch), so **not invented**:
  - `VCB VND → VCB` (same-bank): `BANK_CHNL_DIV='T'` (PymnHelper:2572); `ANT_DIV_CD` = the uploaded value (`copyFrom(rows)`, :2840 — no VND override).
  - `VCB VND → Other`: `BANK_CHNL_DIV='O'`, `ANT_DIV_CD` = **NAPAS/CITAD from upload** (PymnHelper:2573).
  - `VCB USD → VCB` (same-bank only): `BANK_CHNL_DIV='T'`, `ANT_DIV_CD=null` (PymnHelper:2574/:2784).
- **Refactor change made:** Vendor + Expense now seed `ANT_DIV_CD` from the uploaded line value before the USD override (VCB VND carried `null` before; now carries the uploaded NAPAS/CITAD). Internal Transfer got a targeted VCB-VND branch (same rule; VCB USD stays null). Tests added in all three executor test classes.
- **Remaining (narrow) SIT verification only:** capture one real VCB VND file record at SIT and confirm the bank accepts `BANK_CHNL_DIV`/`ANT_DIV_CD` as emitted. This is acceptance evidence, **not** a design/routing blocker.

### B1/B2/B4/B7 — provisioning  ·  owner: **Ops / Arthur**
Verify + record (redacted) in §2 for SIT and UAT: **B1** EFS base-url + partner-id; **B2** `TB_VAN_API_INFO.API_KEY` (present on dev DB — confirm per env); **B4** SFTP host/port/user/key + remote paths; **B7** callback URL reachability + `WECMS_CALLBACK_ALLOWED_IPS` + firewall. Document presence only, never secret values.

### B5 — 1000-byte file byte-parity  ·  owner: **Arthur (dev) + VAN Center (spec)**
- Generate the **same** transfer input through legacy and the refactor; capture both 1000-byte files.
- Diff byte-for-byte; record a checksum (e.g. SHA-256) of each and any field-offset differences.
- Focus areas (highest pre-SIT risk): the EV/VV URL-encode pass count and the `BT00` reception-code set.

## 6. Final activation statement

- **Implemented & verified (code + tests green, 1862 backend tests):** result-check atomicity (Item 1), BATCH1 per-file tx boundary (Item 2), overseas town-name origination + 35-char guard (Item 3), plus all prior HIGH/MEDIUM parity fixes.
- **Implemented but DISABLED pending configuration:** R-1 recovery (Item 4) — needs the §4 coexistence decision + env flags; all dispatch legs — need their `WECMS_*` env flags flipped on per env.
- **CLOSED this pass:** OQ-1 (`data_dcm_amt` — keep legacy `AmountSplitter`; SIT capture is evidence only) and OQ-2 (VCB file-based VND+USD CONFIRMED + IMPLEMENTED; field values proven from legacy). Neither blocks SIT activation.
- **Requires external confirmation (remaining):** B1/B2/B4/B7 (per-env provisioning readout), B5 (byte-parity capture), a one-request SIT capture each for OQ-1 (`data_dcm_amt`) and OQ-2 (VCB VND file record) as acceptance evidence, **and the deployed UAT/PROD `WECMS_DB_URL` + `env | grep WECMS_` flag readout** (Ops — the only way to turn the §2 layer-(b) values into confirmed layer-(c) fact).
- **Legacy coexistence (per-env, refined):** DEV + SIT are **DB-separated from the legacy JEX batch** (verified — different instances / no SIT legacy node), so recovery there is coexistence-safe. **UAT is blocked** until Ops confirms the refactor UAT DB is not the JEX UAT batch DB (rows are indistinguishable across systems). PROD is N/A (refactor not deployed).
- **Legacy JEX batch is confirmed running (config + DB evidence) in DEV, UAT, PROD; not in SIT** (refactor-only) — see §2.2. Its every-minute polling cadence is unchanged on the JEX side; the refactor's TO-BE event-driven model is independent and, in DEV/SIT, operates on a separate DB.
