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

> **CORRECTION 2026-08-01 — several items were framed as "obtain this" when the value already exists.**
> Legacy WeCMS Batch runs in PROD today, so its configuration lives in `wecms_batch.properties` and in the
> shared JEX database. Verification against the legacy sources (see
> [readiness audit §6.0–§6.3](wecms-batch-infrastructure-readiness-audit.md)) changed four items from
> *provision* to *read the value legacy already uses*: **B2** (VAN API key — inherited from the DB target,
> not a vendor request), **Gate B** (`TB_BATCH_SCHEDULE_*` — satisfied by construction on UAT/PROD),
> **EFS base-url** (in this repo for UAT and PROD), and **Gate H** (exceeds legacy rather than matching it).
> Three new items were added — **H-11** (PROD SFTP host differs), **H-12** (confirm the service account),
> **M-7** (`WECMS_SFTP_USERNAME` empty) — plus decision **D-1** (staging paths).
> Items below marked `[x] IN REPO` / `[x] INHERITED` were previously `[?]` or `[ ]` in error.
> **C-1 is unaffected and remains the top blocker** — a recoverable value says nothing about what a
> running host is configured with.

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
- [x] ~~**DBA — Run the same seed on the UAT and PROD DBs** once those targets are named (§2).~~
      **Corrected 2026-08-01 — not a task.** UAT/PROD inherit the rows from the live JEX databases (§2,
      Gate B). Replaced by: **one `select count(*)` per environment to confirm**, and a **PO + DBA
      decision on the VCB `970436` INFO row**, which is the only row legacy does not already have.
- [!] **VAN — Explain EFS 201 `RS_CD='1000'`** and state what must change on our side.
      *(Audit C-3)*

---

## 1. Network

- [x] **OPS — EFS / VAN Center gateway host + port — IN REPO for all three environments.**
      ~~UAT and PROD are empty; NEEDS CONFIRMATION.~~ **Corrected 2026-08-01:** legacy `{ENV}_API_URL` in
      `wecms_batch.properties` carries **DEV `https://dev-van-gw.webcashvietnam.com`, UAT
      `https://dev-van-gw.webcashvietnam.com`, PROD `https://van-gw.webcashvietnam.com`**. Direction:
      **outbound**, 443/HTTPS. Action is to copy the values in, not to request them.
- [?] **OPS + VAN — Why does legacy UAT point at the *dev* gateway?** `U_API_URL` is `dev-van-gw`, not a
      UAT-specific host. The value is verbatim legacy and is not in doubt — but confirm it is still
      intended before reusing it. This is a question about the value, not a missing value.
- [x] **Evidence that the gateway is reachable from at least one host** — 17 real 201 responses were parsed
      (audit §4.1). Reachability is proven; acceptance is not.
- [?] **OPS + VAN — Outbound source-IP allow-listing at the gateway.** Our egress IP(s) per environment must
      be registered with the VAN Center. A source-IP rejection is a plausible cause of `RS_CD='1000'`.
      **NEEDS CONFIRMATION.**
- [ ] **OPS — Firewall egress rule** for the app host → EFS gateway, 443/TCP, per environment.
- [x] **OPS — SFTP / external file service host + port — IN REPO for all three environments.**
      ~~Not in the repository at all. NEEDS CONFIRMATION.~~ **Corrected 2026-08-01:** legacy
      `{ENV}_SFTP_IP` / `_SFTP_PORT` carry the values, and `.env.local` / `.env.sit` / `.env.sit.example`
      already hold them. Direction: **outbound**, SSH.
      > ⚠ **H-11 — the PROD host is a different machine.** DEV `42.96.55.163` · UAT `42.96.55.163` ·
      > **PROD `42.96.55.18`**. Port is `3300` everywhere and every remote path is identical across all
      > three, so the **host is the one value that silently breaks if you copy the DEV/UAT block forward.**
      > Read it off `R_SFTP_IP`, do not assume.
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
- [x] **DBA — UAT and PROD inherit the rows; do NOT repeat the seed there.**
      ~~The seed is applied to the DEV/SIT DB only. Each environment needs its own rows. **BLOCKING for
      UAT/PROD dispatch.**~~ **Corrected 2026-08-01:** legacy reads the **same two tables**
      (`FILE_DEPOSIT_TRANSFER_BATCH1.java:271-273` INFO, `:800-803` DTL) and dispatches daily, and UAT/PROD
      point the refactor at the **live JEX databases** (memory `project_uat_prod_live_jex_db_target`), so
      the rows are already there. JEX DEV confirms the shape: **4 INFO + 12 DTL**, the 12 DTL rows
      identical to what was seeded on 2026-07-28 — the seed was transcribed *from* them. The seed script
      was only ever scoped to the refactor's **own separate DB** (`172.168.5.172/wecmsdevdb`), which SIT
      has and UAT/PROD do not. **Not blocking.**
- [ ] **DBA — Confirm with one `SELECT count(*)` per environment.** UAT and PROD are **not reachable from
      this workspace**, so the line above is INFERRED, not observed. One query each closes it.
- [!] **PO + DBA — The VCB `970436` INFO row is the genuine exception.** Legacy has **no** `970436` INFO
      row at all, so inheriting legacy's rows means VCB bulk dispatch keeps failing exactly as it does in
      legacy. The refactor's seeded `970436 / ant_where_use_yn='N'` row is a **refactor addition** (OQ-2).
      Adding it on UAT/PROD is a **write to a live shared table the JEX batch reads** and needs the same
      PO + DBA sign-off as narrowing the operating windows. **This — not the seed — is the Gate B work.**
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
- [ ] **OPS — Create the SFTP staging directories** and verify they are writable by the app user.
      **The path differs by environment on purpose — see D-1 below. Use the right one.**

  > **D-1 — DELIBERATE DECISION, recorded 2026-08-01. Do not "fix" this back to a single path.**
  >
  > `/WAS_DATA/file/wecmsbatch/{send,receive}` are **legacy's own directories** (`{T,U,R}_SEND_PATH` and
  > `{T,U,R}_LOCAL_DOWN_PATH`, identical across all three legacy environments). Pointing at them
  > **matches legacy exactly — and that is precisely the risk.**
  >
  > | Env | Legacy batch on the host? | Staging paths to use | Why |
  > |---|---|---|---|
  > | **SIT** | **No** — refactor-only (`JEX_BATCH` absent) | **legacy's** `/WAS_DATA/file/wecmsbatch/{send,receive}` | parity benefit, zero coexistence risk |
  > | **UAT** | **Yes** (`JEX_BATCH=true`) | **diverged** `/DATA/src-wecms/wecms-backend/upload-files/{send,receive}` | separate dirs are the only thing preventing a silent overwrite |
  > | **PROD** | **Yes** (`JEX_BATCH=true`) | **diverged** — same as UAT | idem |
  >
  > **Why sharing a directory is unsafe:** both systems build files named by the same `firm_file_nm`
  > generator, drawing from the same global `firm_file_seq_no`, into the same directory. A collision does
  > **not** error — it **overwrites**, and the loser's transfer either never uploads or uploads the wrong
  > bytes. **Neither system checks.** No log line, no failed row, no alert on either side.
  >
  > The UAT/PROD template divergence **is the coexistence answer, not drift.** Anyone tidying the
  > templates for consistency would reintroduce the overwrite risk on the two environments where money
  > moves, and would get no error telling them so. Unify back to legacy's paths **only** if legacy is
  > decommissioned on that host. Full rationale:
  > [readiness audit §6.3](wecms-batch-infrastructure-readiness-audit.md).
- [!] **OPS — Set `WECMS_SFTP_LOCAL_UPLOAD` and `WECMS_SFTP_LOCAL_DOWNLOAD`.** Both are **empty** in
      `.env.sit.example`; the code silently falls back to `System.getProperty("java.io.tmpdir")`, so 1000-byte
      request files and downloaded result files would land in the OS temp directory and be lost on restart.
      **BLOCKING for the bulk leg.** *(Audit M-2)*
- [ ] **OPS — Directory ownership + permissions** — owned by the app service account; `0750` or tighter on
      the staging dirs. Not world-readable (they contain bank account data).
- [ ] **OPS — SFTP private key file** placed at `WECMS_SFTP_KEY_PATH`, mode `0600`, owned by the app user.
      Verify with `ls -l` → `-rw-------`. **This is the only genuinely out-of-band item in B4.**
- [!] **OPS — Confirm the OS service account `wecms-api` runs as, BEFORE trusting `WECMS_SFTP_KEY_PATH`
      (H-12).** Legacy's `{ENV}_SFTP_CERT` property is **dead config**:
      [`SftpUtil.CertInit:57-59`](../../../WECMS_BATCH%20DEV/source/WECMS_BATCH/WECMS_BATCH/src/util/SftpUtil.java)
      accepts a `certPath` argument and **ignores it**, hardcoding
      `System.getProperty("user.home") + "/.ssh/id_rsa"`. The property values agree with the process
      user's home *by coincidence of the account* — `was` on DEV/UAT (`/home/was/…`), `wildfly` on PROD
      (`/opt/wildfly/…`) — not because anything reads them. So
      **`WECMS_SFTP_KEY_PATH=/home/was/.ssh/id_rsa` is correct only if `wecms-api` runs as `was`.** The
      refactor's `JschSftpClient` genuinely reads the property, so a mismatch fails at connect time rather
      than silently falling back to the account's home. Record the account per environment.
- [?] **OPS — Shared storage requirement.** If more than one instance runs, the staging dirs must either be
      shared or the bulk leg must be pinned to one node. **NEEDS CONFIRMATION** (depends on §1 instance
      count).
- [ ] **OPS — Disk headroom** on the staging + log volumes, monitored at 80 %.
- [ ] **OPS — Log rotation** configured. Nothing in the repository configures it. *(Audit M-5)*

## 4. EFS / SFTP integration

- [x] **EFS `base-url` for UAT and PROD — IN REPO. Not blocking, and not a VAN request.**
      ~~**VAN —** Empty in both templates. **BLOCKING.**~~ **Corrected 2026-08-01:** `U_API_URL` =
      `https://dev-van-gw.webcashvietnam.com`, `R_API_URL` = `https://van-gw.webcashvietnam.com`, both in
      `wecms_batch.properties`. Copy them into `WECMS_EFS_BASE_URL`. The only thing worth asking VAN is
      **why legacy UAT points at the dev gateway** (see §1) — a question about the value, not a request
      for it.
- [x] **EFS `partner-id` — RETIRED 2026-07-29 (D-2). Not blocking, not a VAN request, not a config value.**
      ~~**[!] VAN — EFS `partner-id` (envelope `ID`).** Empty in **every** template, all environments.
      **BLOCKING.**~~ **Corrected 2026-08-01 — this entry was stale on both counts.** Verified in code:
      - **Nothing reads it**, in source *or* build output. `EfsProperties` has **no `partnerId` field**
        (see the explicit `// NOTE: there is deliberately NO partner-id property here.` at
        `EfsProperties.java:67`, plus the class Javadoc at `:23` and `:39`). `getPartnerId()` **does not
        exist anywhere in `src/`.** The key is absent from every YAML in `src/main/resources/`,
        `bin/main/` and `build/resources/main/` — so setting `WECMS_EFS_PARTNER_ID` on a host would bind
        to nothing.
      - **A contract test enforces its absence.**
        `SchedulerConfigurationContractTest.partnerIdPropertyIsRetired` asserts
        `rawYaml(profile).doesNotContainKey("wecms.dispatch.efs.partner-id")` and **passes for all four
        profiles** (`build/reports/tests/test/classes/…SchedulerConfigurationContractTest.html:152-170`).
        Re-introducing the key breaks the build.
      - **The envelope `ID` is unconditionally `TB_FIRM_CONF.VAN_ORG_CD`.** All six construction sites
        assign `String id = cmd.vanOrgCd();` with no branch (`RecipientLookupService:93`,
        `RealtimeTransferService:105/148/277`, `ResultCheckService:234`, `BulkReceptionService:1120`,
        `BulkReceptionResultService:887`, `OpsAlertService:186`); `RestClientEfsApiClient:181` merely
        serialises it. Matches legacy on every leg (`NAPAS_REAL_TIME_BATCH:108`,
        `PROCESSING_RESULT_CHECK_BATCH:164`, `FILE_DEPOSIT_TRANSFER_BATCH1:1208`, …), which has no
        partner-id property at all.
      - **The vendor's `prtn_id` is a different field** — a **per-account business-payload** value inside
        `EV` (guide §5.9–5.13; §5.11 p.30-31: `prtn_id String(20) 은행에서 발급 받은 Partner ID`,
        "Partner ID **issued by the bank**"), sourced from **`TB_FIRM_CONF.BANK_PRTN_ID`** outbound and
        from the callback body inbound. The envelope `ID` is §4 transport. Legacy keeps the two distinct
        in the same method from the same row: `PROCESSING_RESULT_CHECK_BATCH:112` (`VAN_ORG_CD` → `"ID="`)
        vs `:153` (`BANK_PRTN_ID` → `param.put("prtn_id", …)`).

      > **This item does not need splitting — it needs deleting.** The envelope `ID` and the body
      > `prtn_id` genuinely *are* two different fields with two different sources, which is normally the
      > case for splitting a checklist row. But **neither of them is fed by the retired property**, so
      > there is nothing left for either half to track. What replaces it is the row below — a **DB**
      > check, not a VAN request.

      > **Why the retirement was right (the strongest evidence in the record).**
      > `TB_FIRM_CONF`'s PK is **`(biz_key, out_bank_cd, wdrl_act_no)`**, so `BANK_PRTN_ID` — and
      > `VAN_ORG_CD` with it — is **per withdrawal account**, not per environment or even per bank. JEX
      > DEV demonstrates it: tenant `VN_VNM0004_000001` on bank `970424` carries `bank_prtn_id='WEBC1'`
      > on account `700005285299` and `bank_prtn_id='000001106'` on account `703009080996`, with
      > different `van_org_cd`s too. **A single static configuration property could only ever have been
      > correct for one account** and wrong for every other one. Retiring it removed a latent
      > wrong-identity bug, not just dead config. Do not reintroduce it.

- [!] **DBA + OPS — Verify `TB_FIRM_CONF.VAN_ORG_CD` and `BANK_PRTN_ID` per withdrawal account, in each
      environment.** This is what replaces the retired partner-id item. **Per account, not per
      environment** — the PK is `(biz_key, out_bank_cd, wdrl_act_no)` and JEX DEV shows **two different
      `BANK_PRTN_ID`s under one tenant and one bank** (see the box above). These two columns feed the
      **envelope `ID`** and the **body `prtn_id`** on every EFS call, so a wrong row sends a wrong
      partner identity for that account only — invisible in any per-environment check.

      ```sql
      SELECT biz_key, out_bank_cd, wdrl_act_no, van_org_cd, bank_prtn_id, org_stat
        FROM tb_firm_conf ORDER BY biz_key, out_bank_cd, wdrl_act_no;
      ```

      **What "correct" means:** `van_org_cd` matches a row in `TB_VAN_API_INFO` (otherwise
      `VanApiKeyService` throws and the leg fails closed), and `bank_prtn_id` is **a value the bank
      actually issued**, not a placeholder.

      > ⚠ **Look at this before UAT.** On JEX DEV, **three rows for tenant `VN_VNM0004_000001` carry
      > `van_org_cd='34534534'` and `bank_prtn_id='434534'`** (accounts `700005285280`, `700022964690`,
      > `700035842686`, all `org_stat='1'`). Those two columns feed the envelope `ID` and the body
      > `prtn_id`, so **a row like that sends a nonsense partner identity on every EFS call for that
      > account.** `34534534` is also not a `TB_VAN_API_INFO` key (that table holds `GV00000001`,
      > `GV00000006`, `GV00000007`), so those accounts would additionally fail API-key resolution.
      > Looks like leftover test data — confirm with the DBA and check whether the same pattern exists
      > on UAT/PROD.
- [x] **OPS — Per-VAN `API_KEY` provisioned** into `TB_VAN_API_INFO` on the DEV/SIT DB (2 rows). Never in
      YAML or an env var by design; `VanApiKeyService` throws when absent, so it fails closed.
- [x] **`TB_VAN_API_INFO.API_KEY` for UAT and PROD — INHERITED. Not a vendor request, no provisioning.**
      ~~**OPS —** Provision out-of-band, once those DBs are named.~~ **Corrected 2026-08-01:** legacy
      `CommUtil.getApiKey` resolves IDO `TB_VAN_API_INFO_R001`, whose SQL is
      `SELECT API_KEY FROM TB_VAN_API_INFO WHERE VAN_ORG_CD = :VAN_ORG_CD` — **the same table, column and
      query** the refactor's `VanApiKeyService` documents itself as refactoring. `TB_VAN_API_INFO` is a
      shared legacy JEX table (PROD Active Table List), **not** a refactor-only table. Since UAT → JEX UAT
      DB and PROD → JEX PROD DB with zero data migration, **pointing `WECMS_DB_URL` at the live JEX
      database *is* the provisioning step.** If legacy PROD dispatches payments, the real PROD key is
      already in the JEX PROD database. JEX DEV confirms the pattern: 3 rows, every `API_KEY` populated.
- [ ] **DBA — Confirm with one `SELECT` per environment** that a row exists for that environment's
      `TB_FIRM_CONF.VAN_ORG_CD`. That is the whole of the remaining B2 work. (UAT/PROD not reachable from
      this workspace — the line above is INFERRED.)
- [x] **SFTP host, port and remote paths — IN REPO.**
      ~~**VAN —** host, port, username, and key or password. Nothing in the repository. **NEEDS
      CONFIRMATION.**~~ **Corrected 2026-08-01:** eight of the nine B4 values are recoverable — host, port,
      `sdata`, `rdata`, `rdata/success`, both local staging paths, and the key path (see H-12) — all from
      `wecms_batch.properties`, and already present in `.env.local` / `.env.sit` / `.env.sit.example`.
      **Read the values legacy already uses; do not request them.** Remember **H-11** (PROD host differs).
- [!] **OPS — The SFTP private key file is the only genuinely out-of-band item in B4.** The file at
      `~/.ssh/id_rsa` exists on the host and in no repository. Everything else is recoverable.
- [x] **`WECMS_SFTP_PASSWORD` is not an item.** Legacy calls `jsch.addIdentity(pkey)` with **no
      passphrase** ([`SftpUtil.java:71`](../../../WECMS_BATCH%20DEV/source/WECMS_BATCH/WECMS_BATCH/src/util/SftpUtil.java)),
      so the key is unencrypted and **empty is the correct value** — not a gap, not a credential to
      obtain. Remove it from any "credentials to collect" list.
- [!] **OPS + DEV — Set `WECMS_SFTP_USERNAME` = `GV00000001` (M-7).** It is **empty** in `.env.local`,
      `.env.sit` and `.env.sit.example`, and `JschSftpClient:113` throws
      `"SFTP host/username not configured (B4)"` on an empty username — **the bulk leg fails at first
      use.** This is a **divergence, not a missing credential**: legacy derives the SSH user **per
      transfer** from the row's `vanOrgCd`
      ([`FILE_DEPOSIT_TRANSFER_BATCH1:700`](../../../WECMS_BATCH%20DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java)),
      while we use one static configured value. Equivalent while a single VAN org is in play
      (`TB_FIRM_CONF.VAN_ORG_CD` = `GV00000001` on every row) — **revisit if a second `VAN_ORG_CD` is ever
      onboarded.**
- [x] **Remote directory names confirmed from legacy** — `sdata` (`{ENV}_SFTP_PATH`), `rdata`
      (`{ENV}_REMOTE_DOWN_PATH`), `rdata/success` (`{ENV}_REMOTE_MOVE_PATH`), **identical in all three
      legacy environments**. A VAN confirmation is optional corroboration, not a prerequisite.
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
- [x] **Gate B — `TB_BATCH_SCHEDULE_INFO` / `_DTL` non-empty** (§2). **DEV/SIT: DONE** (3 + 12 rows,
      audit §4.5). **UAT/PROD: SATISFIED BY CONSTRUCTION** — ~~still pending, seed required~~ **corrected
      2026-08-01**: legacy reads the same two tables and dispatches daily, and UAT/PROD point at the live
      JEX databases, so the rows are inherited. **Remaining: one `select count(*)` per environment to
      confirm** (INFERRED — those DBs are not reachable from this workspace), **plus** the VCB `970436`
      INFO row, which does not exist in legacy and needs PO + DBA sign-off as a write to a live shared
      table. The seed script's *"STILL TO RUN on SIT, UAT, PROD"* line was scoped to the refactor's own
      separate DB — which only SIT has.
- [!] **Gate C — EFS `base-url` + VAN `API_KEY` available** for the target environment (§4).
      **Reframed 2026-08-01 — neither is a vendor request:** `base-url` is **in this repo**
      (`U_API_URL` / `R_API_URL`), and `API_KEY` is **inherited from the JEX DB target**. Gate C reduces
      to *copy the URL in* + *one `SELECT` to confirm the key row*. (The `partner-id` element of this gate
      is unchanged by the verification and is tracked separately in §4.)
- [!] **Gate H — the five dispatch indexes applied** (UAT/PROD only, before any leg is enabled).
      **Not a parity gap — this exceeds legacy deliberately.** Verified read-only on JEX DEV: **every one
      of the eleven dispatch tables carries only its primary key** — no secondary index, no unique
      constraint, no foreign key, no check constraint. That is *not* a precedent for us, because **legacy
      never runs our query**: the two legacy callback handlers only `INSERT` (×4) and `UPDATE` (×2) on
      `TB_FIRM_BAT_API_TRAN` and there is **no R-series IDO** for it. Our
      `existsByBizKeyAndFirmFileNmAndApiTranTp` idempotency probe is **refactor-only code**, runs on the
      01301 callback thread **synchronously inside a VAN gateway timeout**, hits an **append-only table
      that is never pruned**, and **cannot use the PK** (leading column `tran_dt`). Justified by the new
      access pattern, not by matching legacy. DDL on shared legacy tables → same DBA sign-off as Gate B.
      Verify `indisvalid = true` after each `CONCURRENTLY` build. Detail:
      [activation runbook §Gate H](wecms-batch-activation-runbook.md).
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
