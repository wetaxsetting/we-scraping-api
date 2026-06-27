# Troubleshooting

> Part of the [WeCMS Batch](README.md) feature set. Common operational issues, each with **symptoms → root cause → verification → resolution**. Most "nothing happens" symptoms trace back to the intentional inert-by-default posture (see [deployment-guide.md](deployment-guide.md)).
>
> **Status:** source-verified 2026-06-24.

> **First check, always:** the six flags. With `efs.enabled`, `sftp.enabled`, `auto-receive`, `realtime-dispatch`, `recipient-inquiry` OFF and `result-check.cron="-"`, the engine does nothing **by design** — that is not a bug. Confirm the intended flags are on for the environment before treating silence as a fault.

---

## 1. EFS disabled

**Symptoms.** Outbound transfers never reach the bank; logs show `UnsupportedOperationException` from `MockEfsApiClient`, or real-time targets sit at `TB_PAY_TARGET.PROC_STAT='9'` forever.

**Root cause.** `wecms.dispatch.efs.enabled=false` (default) → `MockEfsApiClient` is the active bean and throws on any call; and/or `realtime-dispatch`/`auto-receive` are OFF so no dispatch is even attempted.

**Verification.** Check the resolved property (`/actuator/env` or startup log) for `wecms.dispatch.efs.enabled`. Confirm which client bean loaded — `RestClientEfsApiClient` (real) only registers when `enabled=true`.

**Resolution.** Set `WECMS_EFS_ENABLED=true` **and** `WECMS_EFS_BASE_URL`/`WECMS_EFS_PARTNER_ID` (B1), then enable the relevant trigger flag. Never enable a trigger while EFS is still mocked — the mock throws.

## 2. SFTP upload failures

**Symptoms.** File dispatch fails at the upload step; `TB_FIRM_FILE_MST` stays at `PROC_STEP=0`; `SftpTransferException` (real client) or `UnsupportedOperationException` from `MockSftpClient` (disabled).

**Root cause.** Either `sftp.enabled=false` (mock throws) or a real connection fault — wrong host/port (legacy `3300`), bad/missing private key at `private-key-path`, or an unwritable remote `sdata` path. JSch uses `StrictHostKeyChecking=no` (legacy parity), so host-key mismatch is not the cause.

**Verification.** Confirm `wecms.dispatch.sftp.enabled=true` and that the key file exists at `WECMS_SFTP_KEY_PATH`. Manually `sftp` to the host:port with the same key/user. Check the staging dir (`local-upload-path`) actually received the generated file.

**Resolution.** Provision B4 (host/key/paths), ensure the key file is present and readable, confirm the remote upload dir is writable. The `move` step is best-effort (housekeeping) and is swallowed by design — an upload failure, not a move failure, is what blocks dispatch.

## 3. Callback not received

**Symptoms.** Files reach `PROC_STEP=1` (or stay at `0`) and never advance; no inbound POST appears in the access log for `/api/v1/payment/dispatch/callbacks/*`.

**Root cause.** The gateway cannot reach the callback endpoints — firewall closed for inbound 01301/01302 (B7), wrong callback URL registered with the VAN, or the request is rejected by `CallbackIpWhitelistFilter` (source IP not in `callback.allowed-ips`), or VV verification failed (`WBE_EFS_VERIFY_FAILED`).

**Verification.** Confirm the gateway is configured to POST to `…/callbacks/bulk-reception-result` and `…/bulk-execution-result`. Check `wecms.dispatch.callback.allowed-ips` includes the gateway IP (empty = no IP restriction). Grep logs for `WBE_EFS_VERIFY_FAILED` (crypto) vs a 403 from the IP filter. Confirm `trust-forwarded-for` matches your proxy setup.

**Resolution.** Open the firewall inbound (B7), register the correct callback URLs with the VAN, add the gateway IPs to `allowed-ips`, and confirm the VAN key (`TB_VAN_API_INFO`) used to sign matches the one the gateway uses. The 302 execution request is issued **inline** from the 01301 callback — if 01301 never arrives, nothing advances to step 2.

## 4. Result check not running

**Symptoms.** No-response 202 transfers stay at `TB_FIRM_TRAN.PROC_STAT='6'` indefinitely; `ResultCheckJob.run fired` never appears in logs.

**Root cause.** `wecms.dispatch.result-check.cron="-"` (default) → the job never fires. Or it fires but selects nothing because of the day-boundary edge case (rows from a prior day are never re-checked; the first 2 minutes after midnight the `HHmmss` filter degenerates).

**Verification.** Check the resolved `wecms.dispatch.result-check.cron`. Enable `DEBUG` on `ResultCheckJob` and confirm "fired" appears on schedule. Confirm there are actually rows matching `TRAN_TP='202' AND PROC_STAT='6'` older than 2 minutes for today (ICT).

**Resolution.** Set a real cron (e.g. every minute) **only** in the environment that owns the real-time traffic. Before PROD, apply the day-boundary fix (timestamp/`RQ_DTTM` range instead of same-day + string time) per [firm-banking-dispatch.md §6](firm-banking-dispatch.md#6-future-improvements).

## 5. ShedLock issues

**Symptoms.** The result-check job runs on multiple instances at once (duplicate 203 inquiries), or never runs after a node crash, or startup fails referencing the lock table.

**Root cause.** Missing/inaccessible `shedlock` table, clock skew between nodes (mitigated by `.usingDbTime()`), or a stuck lock from a crashed run (released after `lockAtMostFor=PT2M`).

**Verification.** Confirm the `shedlock` table exists and the app `DataSource` can read/write it. Inspect the row for `ResultCheckJob.run` — `lock_until` in the future on a dead node means waiting for the `lockAtMostFor` ceiling. Check all nodes share the same DB.

**Resolution.** Ensure the `shedlock` table is present (it is a refactor-owned infra table). The provider uses DB time, so node clock skew is not the lock authority. A stuck lock self-releases at `lockAtMostFor` (2 min); do not delete the row mid-run.

## 6. TB_FIRM_CONF missing

**Symptoms.** File or real-time dispatch aborts immediately after the `afterCommit` hook; logs show the per-tenant config gate failing; nothing is sent to the gateway.

**Root cause.** No `TB_FIRM_CONF` row for the tenant (provisioning item I5). `BulkReceptionService.receive` and `RealtimeDispatchService` both gate on the firm config (VAN_ORG_CD / OUT_BANK_CD / BANK_PRTN_ID / WDRL_ACT_NO / branch-split).

**Verification.** Query `TB_FIRM_CONF` for the tenant's `BIZ_KEY`. Confirm `VAN_ORG_CD` matches a seeded `TB_VAN_API_INFO` row.

**Resolution.** Seed the per-tenant `TB_FIRM_CONF` row by hand (Flyway off in SIT/UAT/PROD). `TB_FIRM_CONF` is owned by the settings module; coordinate the seed with the firm-banking-settings feature. See [database-schema.md §10](database-schema.md#10-tb_firm_conf).

## 7. Gateway timeout

**Symptoms.** EFS calls fail with read/connect timeouts; real-time transfers end at `PROC_STAT='3'` FAIL or stay at `6` (no definitive response); `WBE_EFS_GATEWAY_ERROR` in logs.

**Root cause.** Gateway slow/unreachable beyond `connect-timeout-ms=3000` / `read-timeout-ms=20000`, or a `G999` general error from the gateway.

**Verification.** Check latency to `efs.base-url`. Distinguish a no-response (`G999` → triggers EFS 203 recovery on the 202 path; row left at `6` for the result-check sweep) from a hard business error (other RC → FAIL). Inspect `TB_FIRM_BAT_API_TRAN` for the leg's recorded RC.

**Resolution.** Restore gateway connectivity. A no-response 202 is the result-check sweep's job to reconcile (EFS 203) — confirm that job is enabled (issue 4). Adjust the timeouts only with gateway-SLA agreement; the defaults match the legacy expectation.

## 8. Firewall issues

**Symptoms.** Outbound EFS calls fail to connect, or inbound callbacks never arrive (overlaps issue 3), despite correct flags and config.

**Root cause.** Network firewall blocks outbound to the EFS base-url / SFTP host:port (3300), or blocks inbound to the callback endpoints (B7).

**Verification.** From the app host, test outbound TCP to the EFS host:443 and the SFTP host:3300. From the gateway side, confirm inbound to the app's callback path is permitted. Correlate with `CallbackIpWhitelistFilter` 403s vs connection refused.

**Resolution.** Open outbound to EFS + SFTP and inbound to the callback endpoints (B7) for the gateway IPs only. Keep the callback endpoints restricted by network/IP — they are unauthenticated and rely on VV crypto + the IP allow-list (pre-deploy gate R-03).

---

### Escalation

If an issue is not infrastructure/config but appears to be missing or wrong logic, check it against the gap audit before raising — most "missing" behaviour is a documented deferral, not a defect: [../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md](../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md). PO (Mr. Jung) owns scope decisions; Mr. Kang owns TO-BE architecture.
