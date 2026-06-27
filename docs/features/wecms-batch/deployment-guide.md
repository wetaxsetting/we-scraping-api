# Deployment & Activation Guide

> Part of the [WeCMS Batch](README.md) feature set. How to build, configure, and switch on the dispatch engine — and the per-environment activation checklists. The engine is **inert by default**; activation is deliberate and staged.
>
> **Status:** source-verified 2026-06-24. R&D today; no DEV/UAT/PROD deploy yet.

---

## 1. Build process

The dispatch engine ships **inside the single `wecms-api` deployable** — there is no separate batch jar.

| Item | Value |
|---|---|
| Framework | Spring Boot **3.5.13**, Java 21 (Temurin) |
| Group / artifact / version | `com.kosign.wecms` / `wecms-api` / `0.0.1-SNAPSHOT` |
| Packaging | **WAR** (the `war` plugin is active → external servlet container, not an embedded fat-jar) |
| Dispatch deps | SFTP `com.github.mwiede:jsch:0.2.21`; ShedLock `net.javacrumbs.shedlock:shedlock-spring:5.16.0` + `shedlock-provider-jdbc-template:5.16.0`; POI `org.apache.poi:poi-ooxml:5.2.5`; jjwt `0.12.6` |

## 2. Gradle commands

```bash
cd wecms-api
./gradlew build                                              # compile + unit tests
./gradlew test                                               # tests only
./gradlew bootRun --args='--spring.profiles.active=local'    # run against the shared dev DB
# Windows / POSIX wrappers (set env + bootRun):
./run-local.bat        # ./run-local.sh
```

> There is **no** `java -jar wecms-batch.jar`. Build the WAR and deploy it to the servlet container, or run via `bootRun` locally.

## 3. Environment variables

Everything in the `wecms.dispatch.*` block binds from env vars (`application-{profile}.yml`). Nothing sensitive is committed — the SFTP private key is referenced by path and supplied out-of-band.

| Env var | Binds to | Notes |
|---|---|---|
| `WECMS_DISPATCH_AUTO_RECEIVE` | `auto-receive` | File-path 00301 hand-off. Default OFF. |
| `WECMS_DISPATCH_REALTIME_DISPATCH` | `realtime-dispatch` | Real-time 202 dispatch. Default OFF. |
| `WECMS_DISPATCH_RECIPIENT_INQUIRY` | `recipient-inquiry` | EFS 201 depositor inquiry. Default OFF. |
| `WECMS_DISPATCH_RESULT_CHECK_CRON` | `result-check.cron` | Default `"-"` (never fires). |
| `WECMS_CALLBACK_ALLOWED_IPS` | `callback.allowed-ips` | Comma-separated gateway IPs; empty → rely on VV crypto. |
| `WECMS_EFS_ENABLED` | `efs.enabled` | Default OFF → `MockEfsApiClient` active. |
| `WECMS_EFS_BASE_URL` | `efs.base-url` | EFS gateway base URL (B1). |
| `WECMS_EFS_PARTNER_ID` | `efs.partner-id` | Envelope `ID` (e.g. `CORP000001`). |
| `WECMS_SFTP_ENABLED` | `sftp.enabled` | Default OFF → `MockSftpClient` active. |
| `WECMS_SFTP_HOST` / `_PORT` / `_USERNAME` | `sftp.host`/`.port`/`.username` | Legacy port `3300`. |
| `WECMS_SFTP_KEY_PATH` / `_PASSWORD` | `sftp.private-key-path`/`.password` | Key material never committed. |
| `WECMS_SFTP_LOCAL_UPLOAD` / `_REMOTE_UPLOAD` | upload staging / remote (`sdata`) | |
| `WECMS_SFTP_LOCAL_DOWNLOAD` / `_REMOTE_DOWNLOAD` / `_REMOTE_MOVE` | download / remote (`rdata`) / move (`rdata/success`) | |

## 4. EFS gateway configuration

```yaml
wecms:
  dispatch:
    efs:
      enabled:          ${WECMS_EFS_ENABLED}      # false → MockEfsApiClient (throws on call)
      base-url:         ${WECMS_EFS_BASE_URL}      # B1
      partner-id:       ${WECMS_EFS_PARTNER_ID}    # envelope ID
      connect-timeout-ms: 3000
      read-timeout-ms:    20000
```

- Endpoint map (`EfsProperties.defaultEndpoints()`): `EFS_201..EFS_302` → `/WGVN_EFS_{201,202,203,204,301,302}.jct`.
- Real client `RestClientEfsApiClient` is registered only when `efs.enabled=true`; otherwise `MockEfsApiClient` is active and **throws `UnsupportedOperationException`** on any outbound call (never fakes success).
- Crypto (`EfsCryptoService`): AES-256/CBC/PKCS5 EV + HMAC-SHA256 VV; inbound callbacks are VV-verified before decrypt.
- VAN key: resolved per `VAN_ORG_CD` from `TB_VAN_API_INFO` (`VanApiKeyService`) — **throws until seeded (B2)**.

## 5. SFTP configuration

```yaml
wecms:
  dispatch:
    sftp:
      enabled:          ${WECMS_SFTP_ENABLED}     # false → MockSftpClient (throws)
      host:             ${WECMS_SFTP_HOST}         # B4
      port:             ${WECMS_SFTP_PORT}         # legacy 3300
      username:         ${WECMS_SFTP_USERNAME}
      private-key-path: ${WECMS_SFTP_KEY_PATH}     # key file path; key never in config
      password:         ${WECMS_SFTP_PASSWORD}
      connect-timeout-ms: 60000                    # legacy SftpUtil 60s
      local-upload-path:    ${WECMS_SFTP_LOCAL_UPLOAD}
      remote-upload-path:   ${WECMS_SFTP_REMOTE_UPLOAD}    # sdata
      local-download-path:  ${WECMS_SFTP_LOCAL_DOWNLOAD}
      remote-download-path: ${WECMS_SFTP_REMOTE_DOWNLOAD}  # rdata
      remote-move-path:     ${WECMS_SFTP_REMOTE_MOVE}      # rdata/success
```

`JschSftpClient` (JSch) is active only when `sftp.enabled=true`; otherwise `MockSftpClient` throws on `upload`/`download`/`move`.

## 6. Feature flags

Six flags, all default OFF (verified across `application-local/sit/uat/prod.yml`):

| Flag | Controls | Default |
|---|---|---|
| `wecms.dispatch.efs.enabled` | EFS gateway I/O (real vs mock client) | OFF |
| `wecms.dispatch.sftp.enabled` | SFTP I/O (real vs mock client) | OFF |
| `wecms.dispatch.auto-receive` | File-path `afterCommit` trigger | OFF |
| `wecms.dispatch.realtime-dispatch` | Real-time `afterCommit` trigger | OFF |
| `wecms.dispatch.recipient-inquiry` | EFS 201 depositor inquiry | OFF |
| `wecms.dispatch.result-check.cron` | Result-check scheduler | `"-"` (never fires) |

**Activation order (always):** enable EFS + SFTP I/O first (with the mocks proven to throw), then the triggers, then the cron — one leg at a time with a round-trip after each.

---

## 7. SIT activation procedure

SIT DB is Flyway-off (`ddl-auto: validate`) — apply all DDL/seed by hand (DBA).

**Pre-flight checklist**

```
- [ ] B1  EFS base-url + partner-id set; efs.enabled=true
- [ ] B2  TB_VAN_API_INFO seeded with the per-VAN API key
- [ ] B4  SFTP host / private-key / paths set; sftp.enabled=true
- [ ] B7  callback IP allow-list configured + firewall opened for inbound 01301/01302
- [ ] I5  TB_FIRM_CONF rows seeded per tenant (VAN_ORG_CD/OUT_BANK_CD/BANK_PRTN_ID/WDRL_ACT_NO/branch-split)
- [ ] B5  byte-parity verified: fixed-length file + EV/VV encode + full BT00 reception-code set vs captured gateway sample
```

**Activation sequence**

```
1. Flip efs.enabled=true and sftp.enabled=true; confirm the mock beans are NOT loaded.
2. Run V1–V6 round-trips with the I/O legs only:
   V1 EFS 201 inquiry · V2 EFS 202 (+204) · V3 EFS 203 · V4 301→01301 · V5 302→01302 · V6 full file round-trip.
3. Flip the triggers: recipient-inquiry, then realtime-dispatch, then auto-receive — re-run the matching round-trip after each.
4. Set result-check.cron to a real expression last; verify the 203 sweep reconciles a forced no-response 202.
```

## 8. UAT activation procedure

**Additional code requirement before UAT on Shinhan / Woori:** port the UAT Shinhan (`970424`) / Woori (`970457`) bank-code override (legacy `PymnHelper.java:916-946`) — currently deferred. Without it, those two banks dispatch with the wrong partner ids.

```
- [ ] All SIT pre-flight items (B1/B2/B4/B7/I5/B5) repeated for the UAT gateway + UAT TB_VAN_API_INFO
- [ ] UAT Shinhan/Woori bank-code override ported and tested
- [ ] PO decision recorded on the deferred pre-flight gates (operating-hours, channel-mix, Slack alerting)
- [ ] Coexistence confirmed: legacy JEX batch farm not contending for the same PROC_STAT='9' rows during cutover
- [ ] V1–V6 round-trips re-run against the UAT gateway
```

## 9. PROD activation procedure

```
- [ ] All UAT items green, including the Shinhan/Woori override
- [ ] Result-check day-boundary fix applied before enabling result-check.cron in PROD
- [ ] PO ruling on operating-hours gate + channel-mix validation (SIT-only vs PROD-required) implemented if required
- [ ] B7 firewall + IP allow-list locked to the real gateway IPs (callback controllers are otherwise unauthenticated — pre-deploy gate R-03)
- [ ] Monitoring in place (log-based or ported MsgSendUtil) per the PO decision on Slack alerting
- [ ] Cutover window agreed; enable flags one leg at a time, lowest-volume tenant first
```

> **Blocking gates (do not skip):** R-03 (dispatch-callback network protection) and the Shinhan/Woori override block any non-local deploy. Neither blocks local R&D. See [../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md §7](../../audits/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md) for the authoritative blocker list.

For diagnosing failures during activation, see [troubleshooting.md](troubleshooting.md).
