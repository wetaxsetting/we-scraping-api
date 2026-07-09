# WECMS Batch — MSG_API / WGVN_MSG_SEND_001 Ops-Alert Port — Implementation Plan

> **Status (2026-07-06):** **CORE + WIRING IMPLEMENTED (flag-OFF, tested green).** Decisions applied — OQ-4: **orchestrator-level** wiring (closest to legacy batch); OQ-5: **legacy Korean text verbatim**; HS: alert **off** like legacy; default `WECMS_DISPATCH_ALERT_ENABLED=false`. Wired into all 5 dispatch orchestrators. Two legacy batch-terminal branches (cutoff / no-criteria) are **not ported** (their operating-hours gate is itself deferred) → left as TODO doc notes, no invented logic. Ships inert — cannot fire live until **OQ-1** (crypto byte-parity) is confirmed and the flag is enabled. See §"Implementation status" below.
> **Author aid:** Claude Code · **Date:** 2026-07-06
> **Related:** [firm-banking-dispatch.md](firm-banking-dispatch.md) · [legacy-jex-mapping.md](legacy-jex-mapping.md) · [scheduler-flow.md](scheduler-flow.md)
> **Prior investigation:** conclusion was **"Missing migration — explicitly deferred"** (documented in `BulkReceptionService` Javadoc, not intentionally removed, not BLTB-replaced).

---

## 1. Summary

Legacy JEX batch (`WECMS_BATCH`) posts **operational Slack alerts** to the VAN/EFS smart-gateway endpoint `WGVN_MSG_SEND_001` whenever a firm-banking batch hits an error, a transaction cutoff, or a notable result. These are **internal ops-observability messages** to the Slack channel `WECMSBATCH` — **not** customer-facing (no SMS / Kakao / Email / in-app notification).

The Spring refactor has **all the transport building blocks already** (`EfsApiClient`, `EfsCryptoService`, `VanApiKeyService`, `EfsEnvelope`) but **no MSG-send endpoint, service, or wiring**. The one code trace is a deferral note in `BulkReceptionService`.

This plan ports the alert as a **new, flag-gated-OFF, fire-and-forget `OpsAlertService`** wired at the Spring equivalents of the 5 legacy callers. It reuses the existing EFS transport; the only new endpoint is the `WGVN_MSG_SEND_001` path. **Configuration alone is insufficient — new Java code is required.**

---

## 1a. Legacy value verification (code-confirmed 2026-07-06)

Re-verified directly against JEX source (not from prior notes). Every `MsgSendUtil.SendMsg(...)` call site was enumerated fresh.

| # | Question | Answer | Code evidence |
|---|---|---|---|
| Q1 | Is `appDiv` hardcoded `"0"` at every call site? | **YES — 10/10 sites pass literal `"0"`** | `FILE_DEPOSIT_TRANSFER_BATCH1.java:262,441,766`; `FILE_DEPOSIT_TRANSFER_BATCH2.java:280`; `NAPAS_REAL_TIME_BATCH.java:223,478,694`; `PROCESSING_RESULT_CHECK_BATCH.java:232,391`; `WB_00000100.java:232` |
| Q2 | Is `"0"` explicitly documented as Slack? | **Partly — only via one inline source comment**, not an enum/constant/mapping | `MSGSendAPIThread.java:60` → `param.put("appDiv", appDiv); //0:Slack`. No code maps `0`→Slack behaviourally; the value is passed verbatim to the gateway. |
| Q3 | Is `grpID` always `"WECMSBATCH"`? | **YES — 10/10 sites pass literal `"WECMSBATCH"`** | same 10 call sites as Q1 |
| Q4 | Is `grpID` configurable anywhere? | **NO — hardcoded** | `grpID` is only a method parameter (`MsgSendUtil.java:16`, `MSGSendAPIThread.java:25,31,61`); never read from any `.properties` / `PropertyUtil.getProperty`. No config key exists. |
| Q5 | Is `appDiv` configurable anywhere? | **NO — hardcoded** | `appDiv` is only a method parameter (`MsgSendUtil.java:16`, `MSGSendAPIThread.java:25,30,60`); never read from config. |
| Q6 | Any other values besides `0` / `WECMSBATCH` anywhere? | **NO** | Grep of `appDiv\|grpID\|WECMSBATCH` across the whole project returns only field decls, constructor, debug logs, and `param.put` — **no other literal values, no config lookup.** |
| Q7 | Does HS omit `MSG_API` while normal includes it? | **YES** | `wecms_batch.properties:42` = `MSG_API=/WGVN_MSG_SEND_001.jct`; `wecms_batch_HS.properties` = **no `MSG_API` line** (grep exit 1). |

**Conclusion of verification →** `appDiv="0"` and `grpID="WECMSBATCH"` are **legacy protocol constants** — hardcoded at every call site, never environment-specific, never configurable. `"0"` = Slack rests solely on the `//0:Slack` source comment. **The refactor must model them as Java constants, NOT as env/YAML configuration.** (This corrects the earlier draft of §7, which had proposed `WECMS_DISPATCH_ALERT_APP_DIV` / `_GROUP_ID` env keys — removed below.)

---

## 1b. Implementation status (2026-07-06)

**Done — core transport, flag-OFF, compiles + unit-tested green:**

| File | Change |
|---|---|
| `van/client/EfsApiClient.java` | Added `EFS_MSG` endpoint-key constant + Javadoc |
| `van/client/EfsProperties.java` | Mapped `EFS_MSG` → `/WGVN_MSG_SEND_001.jct` in `defaultEndpoints()` |
| `van/dto/MsgSendRequest.java` | **New** — vendor-cased business payload (`@JsonProperty` `appDiv`/`grpID`/`content`/`mode`, declaration order = legacy insertion order) |
| `dispatch/alert/OpsAlertAsyncConfig.java` | **New** — `@EnableAsync` + dedicated `opsAlertExecutor` `ThreadPoolTaskExecutor` (core 1 / max 2 / queue 50 / DiscardPolicy) |
| `dispatch/alert/OpsAlertService.java` | **New** — `@Async("opsAlertExecutor")` `notify(vanOrgCd, content)`; `@Value("${wecms.dispatch.alert.enabled:false}")` gate; `APP_DIV="0"` / `GRP_ID="WECMSBATCH"` / `TNO="M9999999"` constants; reuses `EfsCryptoService` + `VanApiKeyService` + `EfsApiClient`; try/catch-swallow |
| `resources/application.yml` | Added `wecms.dispatch.alert.enabled: ${WECMS_DISPATCH_ALERT_ENABLED:false}` (base → inherited by all profiles) |
| `.env.example` + 4 profile examples | Added `WECMS_DISPATCH_ALERT_ENABLED=false` (non-secret) |
| `test/.../alert/OpsAlertServiceTest.java` | **New** — 7 tests: disabled no-op, blank no-op, envelope constants (`TNO=M9999999`/EM/VM/id), vendor-cased JSON, partner-id fallback, gateway-throw swallowed, key-lookup-throw swallowed. `./gradlew test --tests OpsAlertServiceTest` green; full `compileJava`/`compileTestJava` clean. |

**Wiring — DONE (orchestrator-level, verbatim Korean, all-outcome semantics matched to legacy):**

| Legacy caller / site | Spring orchestrator (wired point) | Korean header | Outcomes | Notes |
|---|---|---|---|---|
| WB_00000100 §232 | `DepositorInquiryService.inquire` (success + gateway-error + infra-error) | `예금주조회 API` | all | production seam that `FirmBankingInquiryService.inquiryAccount` delegates to (not the low-level `RecipientLookupService`) |
| PROCESSING_RESULT_CHECK §232 / §391 | `ResultCheckService.reflectOutcome` | `처리결과조회 Batch` / `처리결과조회 Batch 원거래오류` (on fail) | all | per-row result, after firm-tran + pay-target update |
| NAPAS_REAL_TIME §694 | `RealtimeDispatchService.dispatch` (after 202 outcome) | `실시간지급이체 Batch` | all | RC/RM from the resolved firm-tran row |
| NAPAS_REAL_TIME §478 | `RealtimeDispatchService.runFxInquiry` (204 success + fail) | `실시간지급이체 Batch` | all | Woori/non-VND FX pre-step |
| FILE_DEPOSIT_TRANSFER_BATCH1 §249 | `BulkReceptionService.receive` (pre-flight validation FAIL) | `이체파일 Batch1 검증 오류 발생` | error | TRAN_DT/BIZ_KEY/FILE_SEQ_NO/OUT_BANK_CD/BANK_PRTN_ID/REQ_CNT/VRFIMSG |
| FILE_DEPOSIT_TRANSFER_BATCH1 §751 | `BulkReceptionService.receive` (`buildAndDispatchFile` fail / R004 skip) | `이체파일 Batch1 처리중 오류 발생` | error | same field set, minus VRFIMSG |
| FILE_DEPOSIT_TRANSFER_BATCH2 §280 | `BulkExecutionResultService.handle` (malformed result file_nm) | `이체실행지시 Batch2 API수신 오류 발생` | error | FIRM_FILE_NM + reason |

**Skipped (not ported — TODO doc notes only, no invented logic):**
- FILE_DEPOSIT_TRANSFER_BATCH1 §424 (`대량이체 Batch 거래 마감.` / `수행 기준 없음.`) — batch-terminal cutoff/criteria alert; its operating-hours gate is a documented deferral in `BulkReceptionService`. TODO added inline.
- NAPAS_REAL_TIME §223 (`실시간지급이체 Batch 거래 마감.` / `수행 기준 없음.`) — same batch-terminal gate; the per-target after-commit refactor has no batch loop. TODO added inline in `RealtimeDispatchService.dispatch`.

**Deviations (documented):** legacy appended `CommUtil.getServerGB()` (`[DEV][WAS_NO]`) to each message body; the refactor omits it — the environment marker lives in the payload `mode` field (active Spring profile) instead. `appDiv`/`grpID`/`TNO` are the legacy constants (`0`/`WECMSBATCH`/`M9999999`).

All wiring is fire-and-forget (`@Async`, gated OFF); an alert failure never blocks/fails the transfer or batch. Each wired service gained one `private final OpsAlertService` field (all use `@RequiredArgsConstructor`).

---

## 2. Legacy behavior (code evidence)

### 2.1 Endpoint & gateway

| Property | File | Line | Value |
|---|---|---|---|
| `MSG_API` | `WECMS_BATCH DEV/.../WEB-INF/properties/wecms_batch.properties` | 42 | `/WGVN_MSG_SEND_001.jct` |
| Gateway host | same file | 22–24 | `{ENV}_API_URL` → `https://dev-van-gw.webcashvietnam.com` (T/U), `https://van-gw.webcashvietnam.com` (R) |
| **HS variant** | `wecms_batch_HS.properties` | — | **`MSG_API` ABSENT** — HS file has no message-send key (only 301/302/201/202/203) |

Gateway = the **same VAN/EFS smart-gateway** as the transfer endpoints (`{ENV}_API_URL` + path). Not a separate SMS/email provider.

### 2.2 Transport, envelope & crypto — `MSGSendAPIThread.java`

Evidence: `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/thread/MSGSendAPIThread.java`

- **Inner business JSON** (lines 59–63): `{ "appDiv": <appDiv>, "grpID": <grpID>, "content": <content>, "mode": <ENV_MODE> }`
  - `appDiv="0"` → commented **`//0:Slack`** (line 60)
  - `grpID` → **`//CHANNEL 매핑 ID`** (line 61), always `"WECMSBATCH"` at every call site
  - `content` → the human-readable alert text (line 62)
  - `mode` → system env mode (line 63)
- **Crypto** (lines 68–69):
  - `EV = SecurityUtil.EncryptAesBase64(tr_dttm + param.toJSONString(), cryptKey, true)` — AES-256, **plaintext = `RQ_DTIME` concatenated with business JSON**
  - `VV = SecurityUtil.getHmacSha256(param.toJSONString(), cryptKey, true)` — HMAC-SHA256 over **business JSON only**
- **Outer envelope** (lines 76–83): `{ ID=<vanOrgCD>, RQ_DTIME=<tr_dttm>, TNO="M9999999", EV, VV, EM="AES", VM="HmacSHA256" }` — identical shape to the EFS 2xx/3xx envelope; **TNO is the fixed literal `M9999999`**.
- **Transport** (line 91): `ConnectionUtil.connectUrlByPost(url, tparam.toJSONString())`.
- **Response** (lines 112–124): reads `RC`; `!"0000"` → debug-log only. **No retry, no state change, no exception propagation.**

### 2.3 API key source

- `MsgSendUtil.SendMsg(vanOrgCD, cryptKey, ...)` — `cryptKey` is the **per-VAN API key**, resolved by the caller via `CommUtil.getApiKey(vanOrgCD)` (evidence: `FILE_DEPOSIT_TRANSFER_BATCH1.java:227` — `msgApikey = CommUtil.getApiKey(msgApiID)`). Same key used for the transfer envelopes.

### 2.4 Async / fire-and-forget & error handling

- `MsgSendUtil.SendMsg` (lines 30–35) spawns a raw `new Thread(new MSGSendAPIThread(...))` and `.start()` — **fully async, fire-and-forget.**
- Every failure path in `MSGSendAPIThread.run()` is swallowed (`e.printStackTrace()` / `return`; lines 39, 71–72, 92–93, 96–99, 126–128). **A failed alert never affects the batch.**
- `MsgSendUtil.SendMsg` wraps thread creation in try/catch (lines 37–40) — even thread spawn failure is swallowed.

### 2.5 Exact caller list & message content

`grp ID` is `"WECMSBATCH"` and `appDiv` is `"0"` at **all** call sites. Every message appends `CommUtil.getServerGB()` (server tag, e.g. `[DEV][<WAS_NO>]`).

| # | Legacy file | Line(s) | Key/crypt args | Alert message (leading text → appended fields) |
|---|---|---|---|---|
| 1 | `FILE_DEPOSIT_TRANSFER_BATCH1.java` | 249–262 | `msgApiID`, `msgApikey` (`getApiKey`) | `"이체파일 Batch1 검증 오류 발생"` + TRAN_DT, BIZ_KEY, FILE_SEQ_NO, OUT_BANK_CD, BANK_PRTN_ID, FUND_TP, TRAN_TP, REQ_CNT, VRFIMSG |
| 2 | `FILE_DEPOSIT_TRANSFER_BATCH1.java` | 424–441 | `msgApiID`, `msgApikey` | `"대량이체 Batch 거래 마감."` **or** `"대량이체 Batch 수행 기준 없음."` + TRAN_DT, BIZ_KEY, FILE_SEQ_NO, MSG |
| 3 | `FILE_DEPOSIT_TRANSFER_BATCH1.java` | 751–766 | `msgApiID`, `msgApikey` | `"이체파일 Batch1 처리중 오류 발생"` + TRAN_DT, BIZ_KEY, FILE_SEQ_NO, OUT_BANK_CD, BANK_PRTN_ID, FUND_TP, TRAN_TP, REQ_CNT |
| 4 | `FILE_DEPOSIT_TRANSFER_BATCH2.java` | 280 (msg 270–280) | `van_org_cd`, `CRYPT_KEY` | `"이체실행지시 Batch2 API수신 오류 발생"` + TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM, API RM |
| 5 | `NAPAS_REAL_TIME_BATCH.java` | 223 (msg ~207–223) | `ID`, `crypt_key` | `"실시간지급이체 Batch 거래 마감."` **or** `"실시간지급이체 Batch 수행 기준 없음."` + TRAN_SEQ, OUT_BANK_CD, ANT_DIV_CD |
| 6 | `NAPAS_REAL_TIME_BATCH.java` | 478 (msg ~462–478) | `ID`, `crypt_key` | `"실시간지급이체 Batch"` + ID, bank_cd, prtn_id, TNO, [Result] RC, RM (from `rtn_204`) |
| 7 | `NAPAS_REAL_TIME_BATCH.java` | 694 (msg ~678–694) | `ID`, `crypt_key` | `"실시간지급이체 Batch"` + ID, bank_cd, prtn_id, TNO, [Result] RC, RM (from `rtn_202`) |
| 8 | `PROCESSING_RESULT_CHECK_BATCH.java` | 232 (msg ~218–232) | `van_org_cd`, `CRYPT_KEY` | `"처리결과조회 Batch"` + ID, bank_cd, prtn_id, TNO, ORG TNO, [Result] RC, RM |
| 9 | `PROCESSING_RESULT_CHECK_BATCH.java` | 391 (msg ~377–391) | `van_org_cd`, `CRYPT_KEY` | `"처리결과조회 Batch 원거래오류"` + ID, bank_cd, prtn_id, TNO, ORG TNO, [Result] RC/RM, [ORG Result] ORG RC/ORG RM |
| 10 | `WB_00000100.java` | 232 (msg ~216–232) | `WBCOMMON.getString("ID")`, `crypt_key` | `"예금주조회 API"` + ID, TNO, [Result] RC, RM |

> **Note:** the variable name `startSendMSG` is a misnomer — the content is predominantly **error / cutoff / result** notifications, not "batch start" pings.

### 2.6 HS batch difference

`wecms_batch_HS.properties` has **no `MSG_API` key** (confirmed: grep exit 1), while `wecms_batch.properties:42` defines it.

**File-selection mechanism (code-confirmed):** `PropertyUtil.loadInfo()` loads a *single* properties file chosen by the `CONT_PROP_PATH` system property, defaulting to `/properties/wecms_batch.properties` (`PropertyUtil.java:42-46`). The HAENGSUNG deployment points `CONT_PROP_PATH` at `wecms_batch_HS.properties`; the same batch code then runs against the HS file.

**Resulting behaviour on HS (code-confirmed, no explicit guard):** `getProperty("MSG_API")` passes through `StringUtil.null2void(...)` (`PropertyUtil.java:76`) → returns `""`. `MsgSendUtil` builds `url = apiUrl + ""` (just the base URL, no `.jct` path), and `MSGSendAPIThread`'s missing-value guard (`if("".equals(url) || "".equals(cryptKey))`, line 51) does **not** trip because `url` is non-empty. The POST would hit the bare gateway URL and fail, and the failure is swallowed (fire-and-forget). So on HS the alert **silently no-ops** — there is **no explicit "HS disables alerts" branch in code**; it degrades to a failing call via the omitted property.

**Interpretation:** the omission is a real, per-deployment config choice, but the code does not *enforce* HS-off — it relies on the missing endpoint. → **OQ-3** (confirm HS should stay alert-off, i.e. the refactor gates it per product type / config rather than by a broken URL).

---

## 3. Spring current state (code evidence)

### 3.1 Reusable EFS transport (all present)

| Class | Path | Reusable member (evidence) |
|---|---|---|
| `EfsApiClient` | `domain/payment/dispatch/van/client/EfsApiClient.java` | `EfsEnvelope send(String endpointKey, EfsEnvelope request)`; endpoint-key constants `EFS_201…EFS_302` |
| `EfsProperties` | `.../van/client/EfsProperties.java` | `enabled`, `baseUrl`, `partnerId`, `Map<String,String> endpoints` seeded in `defaultEndpoints()` (lines 66–75) — **no MSG entry** |
| `RestClientEfsApiClient` | `.../van/client/RestClientEfsApiClient.java` | form-urlencoded POST of the envelope (lines 51–88); `@ConditionalOnProperty(efs.enabled=true)`; request-mode form fields ID/RQ_DTIME/TNO/EV/VV/EM/VM |
| `MockEfsApiClient` | `.../van/client/MockEfsApiClient.java` | active when `efs.enabled=false` (default) — throws on call |
| `EfsCryptoService` | `.../van/crypto/EfsCryptoService.java` | `encryptEv(businessJson, apiKey, rqDtime)` (line 71), `buildVv(businessJson, apiKey)` (line 104), `verifyVv(...)` (117) |
| `VanApiKeyService` | `.../van/client/VanApiKeyService.java` | `String getApiKey(String vanOrgCd)` (line 39) — reads `TB_VAN_API_INFO` |
| `EfsEnvelope` | `.../van/dto/EfsEnvelope.java` | envelope record; `EM_AES`, `VM_HMAC_SHA256` constants |

> **Crypto-parity must-verify (do NOT assume):** legacy `EV = AES(RQ_DTIME + businessJson)` and `VV = HMAC(businessJson)`. Spring `encryptEv(businessJson, apiKey, rqDtime)` takes `rqDtime` separately and `buildVv(businessJson, apiKey)` takes the business JSON. The **exact concatenation order and JSON serialization** used by `encryptEv` must be byte-compared against `MSGSendAPIThread` during implementation before trusting parity. Flagged as OQ-1.

### 3.2 Async pattern in the dispatch package

- **No `@Async` / `@EnableAsync`** anywhere in `domain/payment/dispatch`.
- The one async-ish precedent is `ApplicationEventPublisher` in `BulkExecutionResultService.java:34,103` (event-driven), plus `HaengsungDispatchResultWriteBackService` uses AFTER_COMMIT event listeners.
- Legacy used a raw fire-and-forget `Thread`. The refactor has **no direct equivalent yet** → **resolved (OQ-2):** introduce `@EnableAsync` + a dedicated `ThreadPoolTaskExecutor` + an `@Async` method (Spring standard async), see §6.2.

### 3.3 Spring equivalents of the legacy callers (wiring targets)

| Legacy caller | Spring equivalent | Path | Concrete method / error path (evidence) |
|---|---|---|---|
| `FILE_DEPOSIT_TRANSFER_BATCH1` (301 reception) | `BulkReceptionService` | `dispatch/service/BulkReceptionService.java` | `receive(...)` (155); validation-fail `log.warn` (198); split-abort `log.warn` (239). **Deferral note lines 104–108 name `MsgSendUtil` explicitly.** |
| `FILE_DEPOSIT_TRANSFER_BATCH2` (302 execution) | `BulkExecutionResultService` | `dispatch/callback/service/BulkExecutionResultService.java` | `handle(...)` (114); VV-fail warn (123); result-file error (138); line-missing warn (218) |
| `NAPAS_REAL_TIME_BATCH` (202 realtime) | `RealtimeDispatchService` (+ `RealtimeTransferService`) | `dispatch/service/RealtimeDispatchService.java` / `RealtimeTransferService.java` | `dispatch(...)` (106); 204-fail warn (162); 202-fail FAIL warn (205). `RealtimeTransferService.transfer(...)` (100/128); 202-fail (184) → `WBE_EFS_GATEWAY_ERROR` |
| `PROCESSING_RESULT_CHECK_BATCH` (203 poll) | `ResultCheckService` (+ `ResultCheckJob`) | `dispatch/service/ResultCheckService.java` / `scheduler/ResultCheckJob.java` | `runOnce()` (152); per-row deferred warn (173); `inquire(...)` (90) 203-fail warn (116). `ResultCheckJob.run()` (41) cron entry |
| `WB_00000100` (201 depositor inquiry) | `FirmBankingInquiryService` (+ `RecipientLookupService`) | `common/firmbanking/FirmBankingInquiryService.java` / `dispatch/recipient/RecipientLookupService.java` | `inquiryAccount(...)` (141), gated by `recipient-inquiry` (`@Value` 107). `RecipientLookupService.lookup(...)` (75); 201-fail warn (103); VV-fail warn (112) |

### 3.4 Existing alerting?

**None.** Exhaustive grep for `slack|WGVN_MSG|msg_send|sendmsg|ops.?alert|webhook|notifier|appDiv` in `src/main/java` + `src/main/resources` returns **only** the `BulkReceptionService` deferral comment. The `domain/payment/notification` module is the **customer-facing in-app BLTB header-bell** — unrelated to ops alerting.

---

## 4. Legacy → Spring mapping table

| Legacy caller | Legacy trigger | Legacy message (leading text) | Spring equivalent location | Implementation action |
|---|---|---|---|---|
| BATCH1 §249–262 | 301 reception pre-flight validation error | `이체파일 Batch1 검증 오류 발생` | `BulkReceptionService.receive` fail path (~198) | Emit alert at validation-fail |
| BATCH1 §424–441 | bulk transfer cutoff / no-criteria | `대량이체 Batch 거래 마감.` / `수행 기준 없음.` | `BulkReceptionService` (cutoff/criteria branch) | Emit alert; **cutoff/criteria branch not yet ported → verify OQ-4** |
| BATCH1 §751–766 | 301 processing runtime error | `이체파일 Batch1 처리중 오류 발생` | `BulkReceptionService.receive` catch/abort (~239) | Emit alert at processing error |
| BATCH2 §280 | 302 execution API-receive error | `이체실행지시 Batch2 API수신 오류 발생` | `BulkExecutionResultService.handle` fail (123/138/218) | Emit alert on 302/callback error |
| NAPAS §223 | 202 cutoff / no-criteria | `실시간지급이체 Batch 거래 마감.` / `수행 기준 없음.` | `RealtimeDispatchService.dispatch` guard (~106–160) | Emit alert; **cutoff/criteria guard not yet ported → verify OQ-4** |
| NAPAS §478 | 204 FX inquiry result | `실시간지급이체 Batch` + 204 RC/RM | `RealtimeDispatchService` 204 branch (~162) | Emit alert on 204 result/fail |
| NAPAS §694 | 202 transfer result | `실시간지급이체 Batch` + 202 RC/RM | `RealtimeDispatchService` 202 branch (~205) / `RealtimeTransferService.transfer` (184) | Emit alert on 202 result/fail |
| RESULT_CHECK §232 | 203 result-poll outcome | `처리결과조회 Batch` + 203 RC/RM | `ResultCheckService.inquire`/`runOnce` (116/173) | Emit alert on 203 result |
| RESULT_CHECK §391 | 203 original-transaction error | `처리결과조회 Batch 원거래오류` | `ResultCheckService` original-txn error branch | Emit alert on original-txn error |
| WB_00000100 §232 | 201 depositor inquiry outcome | `예금주조회 API` + 201 RC/RM | `RecipientLookupService.lookup` (103/112) / `FirmBankingInquiryService.inquiryAccount` (141) | Emit alert on 201 result/fail |

**No caller lacks a Spring equivalent class.** Two *branches* (bulk cutoff / no-criteria at BATCH1 §424 and NAPAS §223) map to Spring logic that **may not be fully ported yet** (the batch operating-hours / criteria gate is itself a documented deferral in `BulkReceptionService:105-106`) → marked **OQ-4**.

---

## 5. Gap analysis

| Aspect | Legacy | Spring today | Gap |
|---|---|---|---|
| MSG endpoint | `/WGVN_MSG_SEND_001.jct` | not in `EfsProperties.endpoints` | **Add `EFS_MSG` key** |
| Send transport | `MSGSendAPIThread` + `ConnectionUtil` | `EfsApiClient.send` exists | Reuse (no new transport) |
| Crypto | `SecurityUtil` AES/HMAC | `EfsCryptoService` exists | Reuse + verify byte parity (OQ-1) |
| API key | `CommUtil.getApiKey` | `VanApiKeyService.getApiKey` | Reuse |
| Alert builder | inline string concat per caller | none | **New `OpsAlertService`** |
| Async | raw `Thread`, fire-and-forget | none in dispatch | **New `@EnableAsync` + `ThreadPoolTaskExecutor` + `@Async`** (OQ-2 resolved) |
| Wiring | 10 call sites in 5 classes | none | **New wiring at 5 Spring services** |
| Config | `MSG_API` + `{ENV}_API_URL` | none for MSG | **1 new flag** `wecms.dispatch.alert.enabled` (URL reuses efs.base-url) |
| Enable flag | always-on | n/a | **New; default OFF** |
| Channel/appDiv | `WECMSBATCH` / `0` | none | **Java constants** in `OpsAlertService` (not config) |

**Classification: Missing migration — explicitly deferred.** Configuration alone is insufficient; new Java is required.

---

## 6. Implementation plan (design — NOT yet coded)

### 6.1 Endpoint

Add an `EFS_MSG` key to `EfsApiClient` constants and to `EfsProperties.defaultEndpoints()`:
`m.put(EfsApiClient.EFS_MSG, "/WGVN_MSG_SEND_001.jct")`.

### 6.2 New `OpsAlertService` (fire-and-forget)

- Location: `domain/payment/dispatch/alert/OpsAlertService.java` (new sub-package `alert`).
- Responsibility: build the business JSON `{appDiv, grpID, content, mode}` → `EfsCryptoService.encryptEv` / `buildVv` → assemble `EfsEnvelope` with `TNO="M9999999"` (legacy literal) → `EfsApiClient.send(EFS_MSG, envelope)`.
- Inputs: `vanOrgCd` (envelope `ID`), `content`. `apiKey = vanApiKeyService.getApiKey(vanOrgCd)`.
- **`appDiv` and `grpID` are legacy protocol constants** (verified §1a — hardcoded `"0"` / `"WECMSBATCH"` at all 10 legacy sites, never configurable). Model them as `private static final` constants on `OpsAlertService` (e.g. `APP_DIV = "0"  // Slack`, `GRP_ID = "WECMSBATCH"`), **not** as env/YAML keys — this preserves legacy parity. `TNO` is likewise the fixed literal `"M9999999"`. `mode` maps to the active Spring profile (env-derived, unlike the two constants).
- **Fire-and-forget (DECIDED — OQ-2 resolved):** Spring standard async — `@EnableAsync` + a dedicated `ThreadPoolTaskExecutor` bean + an `@Async` method on `OpsAlertService`. This replaces the legacy raw `Thread` while preserving fire-and-forget. The `@Async` method body is wrapped in try/catch and logs at `warn`; nothing propagates to the caller, and the dedicated executor keeps alert traffic off the request/dispatch threads.
- **Master gate:** no-op immediately when `wecms.dispatch.alert.enabled=false` (default), read directly via `@Value("${wecms.dispatch.alert.enabled:false}")` on `OpsAlertService` — **no `OpsAlertProperties` class** (only one flag; mirrors the `recipient-inquiry` `@Value` gate in `FirmBankingInquiryService`).
- **Server marker:** the legacy `CommUtil.getServerGB()` per-host tag is **not** replicated as config. If a marker is needed, derive it from the active Spring profile (`Environment.getActiveProfiles()`) — no new `server-tag` key.

### 6.3 Message builders

Port the exact leading strings + appended field lists from §2.5 (keep the Korean leading text verbatim for ops continuity, or confirm EN/localization with ops — OQ-5). Each Spring wiring site composes its `content` and calls `opsAlertService.notify(vanOrgCd, content)`.

### 6.4 Wiring locations (see §3.3 / §4)

`BulkReceptionService`, `BulkExecutionResultService`, `RealtimeDispatchService` (+`RealtimeTransferService`), `ResultCheckService` (+`ResultCheckJob`), `RecipientLookupService` (+`FirmBankingInquiryService`). Insert alert calls at the failure / cutoff / result branches identified, matching the 10 legacy sites.

---

## 7. Config / env keys to add

A single new `wecms.dispatch.alert.enabled` flag — read directly via `@Value` (no `OpsAlertProperties` class), added to the base `application.yml` and profiles as a `${VAR}` placeholder, plus **all 5** `.env*.example` files (and the two real gitignored files by the developer):

| Env key | YAML key | Default (example) | Notes |
|---|---|---|---|
| `WECMS_DISPATCH_ALERT_ENABLED` | `wecms.dispatch.alert.enabled` | `false` | Master gate — OFF by default (like efs/sftp). Read via `@Value("${wecms.dispatch.alert.enabled:false}")`. |

- **No `WECMS_DISPATCH_ALERT_SERVER_TAG`.** The legacy per-host `getServerGB()` marker is not part of legacy parity as config; if needed, derive it from the active Spring profile (`Environment.getActiveProfiles()`).
- **`appDiv` (`"0"`) and `grpID` (`"WECMSBATCH"`) are NOT config keys.** Verified §1a as legacy protocol constants (hardcoded at every call site, never configurable in JEX). They live as Java constants in `OpsAlertService` for legacy parity — the earlier draft's `WECMS_DISPATCH_ALERT_APP_DIV` / `_GROUP_ID` env keys are **removed**. (If a future requirement needs a second channel, promoting `grpID` to config is a deliberate change — see OQ-6, not part of this port.)
- **Endpoint URL & API key are NOT new env keys** — the alert reuses `wecms.dispatch.efs.base-url` (`WECMS_EFS_BASE_URL`) and the per-VAN key from `TB_VAN_API_INFO` via `VanApiKeyService`.
- Net new env key added to all 5 `.env*.example`: **exactly 1** (`WECMS_DISPATCH_ALERT_ENABLED`) — non-secret boolean.
- **No secrets** in the new key. Example files stay blank/safe.

---

## 8. Exact files expected to change during implementation

**New:**
- `src/main/java/com/kosign/wecms/domain/payment/dispatch/alert/OpsAlertService.java` (`@Value` gate; `@Async` notify)
- `src/main/java/com/kosign/wecms/domain/payment/dispatch/alert/OpsAlertAsyncConfig.java` (`@EnableAsync` + dedicated `ThreadPoolTaskExecutor` bean) — **required** (OQ-2 resolved: Spring standard async)
- `src/test/java/.../dispatch/alert/OpsAlertServiceTest.java`
- *(no `OpsAlertProperties` — single flag read via `@Value`)*

**Modified (Java):**
- `.../van/client/EfsApiClient.java` (add `EFS_MSG` constant)
- `.../van/client/EfsProperties.java` (add `EFS_MSG` → `/WGVN_MSG_SEND_001.jct` in `defaultEndpoints()`)
- `.../dispatch/service/BulkReceptionService.java` (wire; update the deferral note at lines 104–108)
- `.../dispatch/callback/service/BulkExecutionResultService.java` (wire)
- `.../dispatch/service/RealtimeDispatchService.java` (+ possibly `RealtimeTransferService.java`) (wire)
- `.../dispatch/service/ResultCheckService.java` (+ possibly `scheduler/ResultCheckJob.java`) (wire)
- `.../dispatch/recipient/RecipientLookupService.java` (+ possibly `common/firmbanking/FirmBankingInquiryService.java`) (wire)

**Modified (config):**
- `src/main/resources/application.yml` (+ `application-local/sit/uat/prod.yml`) — add `wecms.dispatch.alert.*` placeholders
- `.env.example`, `.env.local.example`, `.env.sit.example`, `.env.uat.example`, `.env.prod.example` — add the **1** new key (`WECMS_DISPATCH_ALERT_ENABLED`). `appDiv`/`grpID` are code constants, not env keys (§1a / §7); no server-tag key.

**Docs:**
- This file; plus `firm-banking-dispatch.md` / `legacy-jex-mapping.md` cross-reference updates.

---

## 9. Test plan

1. **Unit — `OpsAlertServiceTest`:**
   - Given `enabled=false` → `notify(...)` is a no-op (no `EfsApiClient` call). *(mirrors the recipient-inquiry gate test)*
   - Given `enabled=true` → envelope built with `TNO="M9999999"`, `ID=vanOrgCd`, `EM=AES`, `VM=HmacSHA256`; `EfsApiClient.send(EFS_MSG, …)` invoked once.
   - Given `EfsApiClient.send` throws → `notify(...)` **does not propagate** (fire-and-forget guarantee).
   - Business JSON contains `appDiv="0"`, `grpID="WECMSBATCH"`, `content`, `mode` (active profile).
2. **Crypto parity (OQ-1):** assert `encryptEv`/`buildVv` output for a fixed `(rqDtime, businessJson, apiKey)` matches a captured legacy `MSGSendAPIThread` sample (or document the accepted divergence if the gateway is version-tolerant).
3. **Wiring tests:** for each of the 5 services, a test that the fail/cutoff/result branch calls `opsAlertService.notify(...)` with the expected leading message — using a mocked `OpsAlertService` so no gateway is hit.
4. **Non-blocking test:** simulate a slow/throwing alert and assert the transfer/dispatch result is unaffected.
5. **Build/regression:** `./gradlew test` green; confirm `MockEfsApiClient` remains active (efs disabled) so no live call in CI.

---

## 10. Rollback plan

- **Primary rollback = the master gate.** `WECMS_DISPATCH_ALERT_ENABLED=false` (the default) makes `OpsAlertService` a no-op — instant disable without redeploy. Since the whole dispatch subsystem is also OFF by default, shipping the code is inert until explicitly enabled.
- **Code rollback:** the change is additive (new service + endpoint entry + guarded call sites). Reverting the wiring commit removes all alert calls; `EfsProperties`/`EfsApiClient` gain one unused endpoint key (harmless).
- **No schema, no data, no contract change** — nothing to migrate back.

---

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Alert failure blocks/fails a transfer | Fire-and-forget; try/catch-swallow; async executor; unit test #4. **Non-negotiable.** |
| Accidentally enabled in prod | Default `enabled=false`; gated like efs/sftp; documented in required-secrets. |
| Customer-notification confusion | This is **ops-only** (Slack `WECMSBATCH`); explicitly **not** the BLTB customer notification module. |
| Secret leakage in templates | New keys are non-secret (bool/channel/appDiv/tag); URL + API key reuse existing (`base-url` + `TB_VAN_API_INFO`). Example files stay blank/safe. |
| VAN API key handling drift | Key stays sourced from `TB_VAN_API_INFO` via `VanApiKeyService.getApiKey` — never a config/env value. |
| Crypto byte-mismatch vs gateway | OQ-1 parity test before enabling. |
| Over-alerting / noise | Match legacy triggers 1:1; do not add new alert points beyond the 10 legacy sites without ops sign-off. |

---

## 12. Open questions for PO / Ops

- **OQ-1 (crypto parity):** Confirm `EfsCryptoService.encryptEv(businessJson, apiKey, rqDtime)` produces byte-identical `EV` to legacy `AES(RQ_DTIME + businessJson)` (concatenation order + JSON field order/serialization). Must verify against a live/captured sample before enabling.
- **OQ-2 (async mechanism): RESOLVED (2026-07-06).** Spring standard async — `@EnableAsync` + a dedicated `ThreadPoolTaskExecutor` + an `@Async` `notify(...)` method on `OpsAlertService`. Replaces the legacy raw `Thread` while preserving fire-and-forget. (Event-listener alternative rejected — the alert is not tied to a transaction commit.)
- **OQ-3 (HS behavior):** Verified — `wecms_batch_HS.properties` omits `MSG_API`, and HS silently no-ops the alert (via the missing endpoint, `PropertyUtil.java:42-46` + `null2void`), with **no explicit HS guard** in code. Confirm the refactor should keep HS alert-off **explicitly** (e.g. product-type gate or leaving `WECMS_DISPATCH_ALERT_ENABLED=false` on HS deployments) rather than replicating the broken-URL degradation.
- **OQ-4 (cutoff/criteria branches):** BATCH1 §424 and NAPAS §223 alerts fire on transaction-cutoff / no-criteria. The Spring operating-hours / criteria gate is itself a documented deferral (`BulkReceptionService:105-106`). Confirm whether these two alert branches port now or with the operating-hours gate.
- **OQ-5 (message language):** Keep the legacy Korean leading strings verbatim, or localize to EN/VI? (Ops-facing; likely keep Korean for continuity, but confirm.)
- **OQ-6 (channel target):** `grpID="WECMSBATCH"` / `appDiv="0"` are **kept as legacy protocol constants** (verified §1a). Confirm with Ops that the VAN gateway still maps `WECMSBATCH` to the intended live Slack channel and that `appDiv=0` still means Slack in the current gateway version. Only if a *second* channel is ever required would `grpID` be promoted to config — out of scope for this parity port.
- **OQ-7 (sequencing):** Port now (inert, flag-off) or bundle with the dispatch go-live / operating-hours-gate phase? Recommend bundling with dispatch enablement so alerts have real events to fire on.

---

## 13. Conclusion

- **Not implemented in the refactor**; **explicitly deferred** (evidence: `BulkReceptionService.java:104-108`).
- **All transport prerequisites already exist** — the port is a new `OpsAlertService` (`@Async` + `@EnableAsync` executor) + one endpoint entry + guarded wiring at 5 services + **1** non-secret config key (`WECMS_DISPATCH_ALERT_ENABLED`).
- **Estimated effort: LOW (~0.5–1 day)**, flag-gated OFF, additive, reversible.
- **CORE + WIRING IMPLEMENTED (2026-07-06):** OQ-2 (Spring `@Async`), OQ-4 (orchestrator-level), OQ-5 (verbatim Korean), HS-off, flag-OFF all applied. Transport core + wiring into all 5 dispatch orchestrators built and unit-tested green (147 dispatch/alert tests pass; §1b). Ships inert (`enabled=false`, `MockEfsApiClient` active) — no live call possible until OQ-1 (crypto byte-parity) is confirmed **and** dispatch/EFS is enabled.
- **Skipped (TODO, not invented):** the 2 batch-terminal cutoff/no-criteria alerts (BATCH1 §424, NAPAS §223) — their operating-hours gate is itself deferred.
