# D-1 / D-2 / D-3 — Legacy Verification and Fix Record

> **⚠ ID collision — qualify `D-1` and `D-3` whenever they leave this document.** A second, independent
> `D-n` series is owned by
> [`legacy-batch-boundary-rule-sweep-2026-07-31.md`](legacy-batch-boundary-rule-sweep-2026-07-31.md), and
> **two IDs collide**:
>
> | ID | **This document** | Boundary-sweep series |
> |---|---|---|
> | `D-1` | `ResultCheckService` `TRAN_DT` UTC/ICT reader fix | `S_`/`R_` `FIRM_FILE_NM` prefix asymmetry |
> | `D-3` | Spring Boot Actuator | 99 / 999 file-round wrap |
> | `D-2` | EFS envelope partner ID | *(not used there)* |
>
> Per-document scoping is the house convention (`H-1` already means three different things in three
> documents) and it holds while an ID stays home — **inside this file `D-1` is unambiguous and needs no
> qualifier.** It breaks where two series meet in one document, today
> [`../deployment/wecms-batch-activation-runbook.md`](../deployment/wecms-batch-activation-runbook.md)
> (§5.1 step 6 vs §5.1a-1). **Convention: write `D-1 (d1-d2-d3 record)` / `D-1 (boundary-sweep)` at every
> citation outside the owning document.** Recorded 2026-08-01.

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`) firm-banking dispatch
**Method:** legacy JEX source read **first**, refactor source second, then fix. No DB was reachable this
session (`postgres_dev_server_*` MCP unavailable), so no runtime/DB claim is asserted as live-verified.

**Inputs (all read directly, not via documentation):**
- `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/` — `NAPAS_REAL_TIME_BATCH.java`,
  `PROCESSING_RESULT_CHECK_BATCH.java`, `FILE_DEPOSIT_TRANSFER_BATCH1.java`, `…BATCH2.java`
- `WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R001.xml`
- `WECMS DEV/WECMS/src/wabooks/helper/PymnHelper.java`, `…/commons/DateTimeUtils.java`
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/wecmsbatch/WGVN_EFS_201_act.jsp`,
  `…/api/comm/comm_1003_001_act.jsp`, `…/comm/comm_0184_007_r001_act.jsp`

**Headline:** one of the three audit recommendations was **wrong**, and reading legacy first is what caught
it. D-2's recommendation (reject a blank partner id) would have entrenched a refactor-only invention;
legacy has no partner-id concept at all.

---

## D-1 — `TRAN_DT` timezone behaviour

### Refactor behaviour before the fix

| Field | Written by | Zone |
|---|---|---|
| `TB_PAY_TARGET.TRAN_DT` / `REG_TM` | `ExpenseDispatchExecutor:150-152` (+ internal-transfer / vendor peers) | **UTC** |
| `TB_FIRM_TRAN.TRAN_DT` (202) | `RealtimeDispatchService:234` — reuses the pay-target's value | **UTC** |
| `TB_FIRM_TRAN.TRAN_TM` / `RQ_DTTM` (202) | `RealtimeDispatchService:146-148,240` | **ICT** |
| Result-check date filter | `ResultCheckService:205-211` | **ICT** ← the defect |

ICT is UTC+7, so between 00:00 and 07:00 ICT the ICT date is one day ahead of the UTC date. Rows dispatched
in that window failed `today.equals(t.getTranDt())`, and because the filter was today-only they were never
reconsidered on any later pass — permanently stuck at `PROC_STAT='6'`, with the originating
`TB_PAY_TARGET` row stuck too. 7 hours of every 24.

### Exact legacy behaviour

Legacy maintains **two deliberately separate date spaces**:

**1. `TB_PAY_TARGET` — UTC.** The enqueue stamps it explicitly:

```java
// PymnHelper.pymn_0001_001_c001
tbPayTarget.setTranDt(DateTimeUtils.utcNow().toLocalDate());   // :2197
tbPayTarget.setRegTm(DateTimeUtils.utcNow().toLocalTime());    // :2214
tbPayTarget.setProcDttm(DateTimeUtils.gmtNowVanCenter());      // :2221  ← different zone, on purpose
```

with `DateTimeUtils:84-86` `utcNow() → now(TimeZone.getTimeZone("UTC"))` and `:97-99`
`gmtNowVanCenter() → now(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))`. So legacy is explicit and
intentional: the pay-target key date is UTC, while `PROC_DTTM` is Vietnam time.

⇒ **The refactor's UTC pay-target write is exactly legacy.** Commit `0d24c50`'s "legacy parity" claim is
confirmed correct.

**2. `TB_FIRM_TRAN` — the WAS clock, captured fresh at dispatch.**

```java
// NAPAS_REAL_TIME_BATCH
String tran_dt = DateTime.getInstance().getDate("yyyymmdd");   // :62   (also :295, :489 — refreshed per row)
String tran_tm = DateTime.getInstance().getDate("hh24miss");   // :63
…
idoInFTC001.put("TRAN_DT", tran_dt);                           // :302 / :494  → TB_FIRM_TRAN
```

Legacy does **not** propagate the pay-target's UTC date into `TB_FIRM_TRAN`. It takes **one fresh reading
of one clock** and uses it for both `TRAN_DT` and `TRAN_TM` — and the same pair becomes the envelope
`RQ_DTIME` (`:712-713` `"&RQ_DTIME=" + tran_dt + tran_tm`). When it updates `TB_PAY_TARGET` it switches
back to the pay-target's own date: `idoInPTU00x.put("TRAN_DT", idoOutPTR001.get("TRAN_DT"))`
(`:195, :577, :632, :669`).

**3. The result-check reader — the DB clock, today only.** `IDO.TB_FIRM_TRAN_R001.xml`:

```sql
WHERE TRAN_DT = TO_CHAR(now(),'YYYYMMDD')
  AND PROC_STAT = '6'
  AND TRAN_TM <= TO_CHAR(now() - INTERVAL '2 minute','hh24miss')
  AND TO_CHAR(now() - INTERVAL '2 minute','hh24miss') < '235800'
  AND TRAN_TP = '202'
```

and `PROCESSING_RESULT_CHECK_BATCH:78-79` stamps the 203 audit row from the same WAS clock.

Answers to the required questions:

| Question | Legacy answer | Evidence |
|---|---|---|
| App-server timezone | JVM default (host OS). **No `-Duser.timezone` anywhere** in `WECMS DEV` / `WECMS_BATCH DEV` | grep across `*.sh/*.bat/*.xml/*.properties/*.conf` — no hits |
| DB session timezone | `Asia/Seoul` on the JEX frame DB | memory `project_batch_schedule_tz_antdiv_fixes` (measured 2026-07-28). **Not re-verified this session** |
| `TRAN_DT` generation | `TB_PAY_TARGET`: `DateTimeUtils.utcNow()`. `TB_FIRM_TRAN`: `DateTime.getInstance().getDate("yyyymmdd")` (WAS-local) | `PymnHelper:2197`; `NAPAS_REAL_TIME_BATCH:62,302,494` |
| `REG_TM` generation | `DateTimeUtils.utcNow().toLocalTime()` | `PymnHelper:2214` |
| Request date/time (`RQ_DTIME`) | `tran_dt + tran_tm` from the same single WAS reading | `NAPAS_REAL_TIME_BATCH:712-713`; 203 at `PROCESSING_RESULT_CHECK_BATCH:164-165` |
| Result-check query dates | **DB `now()`**, today only, plus `TRAN_TM <= now-2min` and a `< '235800'` dead-window | `IDO.TB_FIRM_TRAN_R001.xml` |
| App time vs DB `now()` vs bank/Seoul/ICT | **Both** — app time for writes, DB `now()` for the read. Never bank-local. ICT appears only for `PROC_DTTM` (`gmtNowVanCenter`) | as above |
| Today only, or today + previous day? | **Today only** | `IDO.TB_FIRM_TRAN_R001.xml` |
| Any timezone conversion? | Only inside `DateTimeUtils` (`toUtc`/`fromUtc` helpers). None in the batch read/write path | `DateTimeUtils:41-101` |

**The invariant legacy relies on:** `TRAN_DT` and `TRAN_TM` on a `TB_FIRM_TRAN` row come from **one
clock**, and the reader compares against **that same clock**. Legacy is only fully self-consistent when
the WAS zone and the DB zone agree; where they do not, legacy has the same class of latent gap (and its
`< '235800'` guard shows the authors were aware of the wrap without solving it).

### Was it a parity defect?

**Yes — a genuine parity defect, on the reader.** Legacy never reads `TRAN_DT` in a zone other than the
one it wrote. The refactor wrote UTC and read ICT: two clocks, guaranteed to disagree for 7 hours a day.

It is **not** an approved TO-BE difference and **not** a documentation misunderstanding: the stranded
rows are a real, reproducible consequence, now pinned by a test that fails without the fix.

### Final decision — **Option B** (preserve UTC storage, fix result selection)

Rejected **Option A/C** (move `TB_FIRM_TRAN.TRAN_DT` to ICT / the configured business zone) because it
would have broken a legacy behaviour that the refactor currently gets right:

- `DispatchResultTxService.finalizeOriginalAndPayTarget(bizKey, tranDt, tranSeq, …)` (`:58-99`) uses **one**
  `tranDt` to key **both** `TbFirmTran` (PK) and `TbPayTarget`. That is precisely legacy's
  `PROCESSING_RESULT_CHECK_BATCH:301,319`, where a single `org_proc_dt` keys `TB_FIRM_TRAN_U004` **and**
  `TB_PAY_TARGET_U001`. Because the refactor keeps firm-tran's date equal to the pay-target's UTC date,
  that shared-key pattern is *correct here even where legacy's own is fragile*.
- Changing the firm-tran date to ICT would desynchronise the two tables and break the pay-target
  propagation — trading a 7-hour selection gap for a permanent write gap. Strictly worse.

So: **writes unchanged** (all three legacy-justified), **reader aligned to the writer's clock**.

Implemented in `ResultCheckService`:

1. **Date filter evaluated in UTC** — the zone `TRAN_DT` is actually written in. Restores legacy's
   one-clock invariant.
2. **Previous UTC date also accepted** — so the midnight rollover cannot orphan a row.
   *Documented TO-BE divergence:* legacy is today-only. Legacy's answer to the same edge is the
   `< '235800'` guard, which merely stops the batch for the last two minutes of the day and never
   recovers the affected row. Widening by one day cannot double-process (each row is claimed via
   `PROC_STAT`) and is what makes "no permanent stranding" provable.
3. **Aging on a monotonic timestamp** — `isAged` is tiered: an earlier `TRAN_DT` is aged by date alone;
   otherwise the 14-char `RQ_DTTM` is compared (cannot wrap); the legacy `TRAN_TM` `HHmmss` compare
   remains as the fallback for rows written without `RQ_DTTM` (e.g. legacy-JEX-originated rows).
   *Documented TO-BE divergence:* legacy's `< '235800'` dead-window is deliberately not reproduced — with
   an instant-based compare it protects nothing and would itself create a 2-minute blind spot.
4. **Selection extracted as a pure function** `selectAged(candidates, nowIct, nowUtcDate)` so every
   boundary is testable without waiting for one. No behavioural change beyond 1–3.

The `TRAN_DT` zone is deliberately **not** made configurable: it is fixed by the legacy storage contract
above. The configurable zone (`wecms.dispatch.operating-hours.zone`) remains a separate concern.

### Files changed

| File | Change |
|---|---|
| `src/main/java/.../dispatch/service/ResultCheckService.java` | `UTC` constant + Javadoc; `runOnce` delegates to the new `selectAged`; new `selectAged` + `isAged`; D-1 evidence in `runOnce`'s Javadoc |
| `src/test/java/.../dispatch/service/ResultCheckPollTest.java` | fixture date moved from the ICT date to the UTC date, i.e. what production actually writes (with a comment explaining why the old fixture could not catch D-1) |

### The boundary suite caught a flaw in the first cut of the fix

Worth recording, because it is the reason the tiering in `isAged` is ordered the way it is.

The first implementation checked the **date shortcut first** ("an earlier `TRAN_DT` is necessarily older
than the delay window, so it is aged"). `rowInsideTheDelayWindowIsNeverSelected` failed at **07:00 ICT**:
a row dispatched at 06:59 ICT is `2026-07-29T23:59` UTC, so it already carries *yesterday's* `TRAN_DT`
while being **one minute old**. The shortcut declared it aged and the poll would have fired a 203 inquiry
before the 2-minute delay had elapsed — a new, smaller defect introduced by the fix for D-1.

Corrected by making `RQ_DTTM` **tier 1**: every refactor-written row carries it, so the normal path is an
exact full-timestamp compare at every boundary, and the date shortcut is reached only by rows with no
`RQ_DTTM` (legacy-JEX-originated), where its ≤2-minute-early edge around UTC midnight is accepted
deliberately rather than by accident. Documented in `isAged`'s Javadoc.

### Tests added — `ResultCheckSelectionBoundaryTest` (24 tests)

- the D-1 defect itself: dispatched 03:00 ICT, checked 03:03 ICT (fails without the fix)
- ICT clock boundaries: 23:59, 00:00, 00:01, 06:59, 07:00, and 07:01 straddling UTC midnight
- fresh-row rejection re-asserted at each of those five boundaries (the fix must not widen the aging
  window — this is the assertion that caught the tier-order flaw above)
- previous-day unresolved row; month boundary (31 Jul→01 Aug); year boundary (31 Dec→01 Jan); leap day (28→29 Feb 2028)
- **no-permanent-stranding property**: all 1440 dispatch minutes of an ICT day, each checked after its
  window, must be selected
- durability: a row stays selectable hours later, so a missed pass does not lose it
- bounded window: rows older than the previous UTC date are out of scope (owned by `StrandedRecoveryService`)
- tiered aging: tier 1 (earlier date beats a late `TRAN_TM`), tier 3 (legacy `HHmmss` fallback), tier 2
  (`RQ_DTTM` wins over a misleading `TRAN_TM`)
- defensive input: null / blank / bpchar-padded `TRAN_DT`; empty candidate list

Timezone-property override is covered by the existing
`OperatingHoursServiceTest#operatingHoursZone_defaultsToIct_andIsOverridable`.

### Remaining external confirmation

- **Legacy DB session timezone** — recorded as `Asia/Seoul` from a 2026-07-28 measurement; **not
  re-verified this session** (no DB access). It does not change the fix (the fix aligns the refactor's
  reader with the refactor's own writer), but it does determine how much latent stranding legacy itself
  has, which matters when reconciling historical `PROC_STAT='6'` rows during migration.
- **Historical rows** — any row already stranded at `'6'` from before this fix is now selectable only if
  its `TRAN_DT` is today or yesterday (UTC). Older ones need `StrandedRecoveryService` (still
  double-gated off) or a one-off DBA sweep. **PO/DBA decision.**

### Deployment impact

None while `wecms.dispatch.result-check.cron = "-"` (the default in all four profiles). No schema change,
no config change, no contract change. The fix is a precondition for ever setting that cron.

---

## D-2 — EFS envelope partner ID

### Refactor behaviour before the fix

Seven envelope-build sites shared:

```java
String id = StringUtils.hasText(efsProperties.getPartnerId())
        ? efsProperties.getPartnerId() : cmd.vanOrgCd();
```

`RealtimeTransferService:103,145,273` · `ResultCheckService:134` · `BulkReceptionService:704` ·
`BulkReceptionResultService:469` · `RecipientLookupService:91`.

`wecms.dispatch.efs.partner-id` defaulted to `""` and was empty in all four templates, so live traffic
sent `ID = <vanOrgCd>`.

### Exact legacy behaviour

**Legacy has no partner-id property. The envelope `ID` is always `TB_FIRM_CONF.VAN_ORG_CD`, read from the
DB.** Verified on every leg:

| Leg | Legacy site | Code |
|---|---|---|
| 202 / 204 | `NAPAS_REAL_TIME_BATCH:108` | `ID = idoOutFCR001.getString("VAN_ORG_CD")`, used at `:346`/`:559` via `inputData(ID,…)` → `"ID=" + ID` (`:712`) |
| 203 | `PROCESSING_RESULT_CHECK_BATCH:112,164` | `van_org_cd = idoOutFCR001.getString("VAN_ORG_CD")`; `"ID=" + van_org_cd` |
| 301 | `FILE_DEPOSIT_TRANSFER_BATCH1:1208` | `"ID=" + idoOutFCR001.getString("VAN_ORG_CD")` |
| 302 | `FILE_DEPOSIT_TRANSFER_BATCH2:131,189` | `van_org_cd = data.getString("VAN_ORG_CD")`; `"ID=" + van_org_cd` |
| MSG | `NAPAS:223,478,694` · `RESULT_CHECK:232,391` · `BATCH2:280` | `MsgSendUtil.SendMsg(van_org_cd, …)` |
| 201 | `PymnHelper:950` · `comm_1003_001_act.jsp:136` · `comm_0184_007_r001_act.jsp:355` | `inputJSON.put("ID", …getString("VAN_ORG_CD"))` |

`BANK_PRTN_ID` is a **different** field: legacy puts it in the *business payload* as `prtn_id`
(`PROCESSING_RESULT_CHECK_BATCH:144,153`), never in the envelope `ID`. `WGVN_EFS_201_act.jsp:49,52`
confirms the gateway contract treats `ID` and `PRTN_ID` as two separate required fields. The refactor
already models this correctly (`Efs202Request.prtnId`). The API key is looked up by the same value
(`CommUtil.getApiKey(van_org_cd)` — `PROCESSING_RESULT_CHECK_BATCH:114`), matching `VanApiKeyService`.

Answers to the required questions:

| Question | Answer |
|---|---|
| What value did legacy put in `ID`? | `TB_FIRM_CONF.VAN_ORG_CD` |
| Where from? | **DB lookup** (`TB_FIRM_CONF_R001` by `BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO`). Not a property, not hardcoded, not `BANK_PRTN_ID` |
| Could it be blank? | No — legacy has no branch for it; it is a NOT-NULL-in-practice config row read per dispatch |
| Did legacy substitute another field when blank? | **No.** There is no fallback because there is no alternative source |
| Are `GV00000001`-shaped values valid? | Yes — that is what a `VAN_ORG_CD` looks like, and it is what legacy sends |
| Is `CORP000001` a real requirement? | **No — vendor sample only.** It appears in the REST-guide PDF; no legacy code path produces it |
| Do different EFS transactions use different ID sources? | No — identical on all seven legs |
| Same rule for 201/202/203/204/301/302/MSG? | **Yes**, all seven verified above |
| Environment-specific partner IDs expected? | No. `VAN_ORG_CD` is per (tenant, bank, account) **data**, not per environment |
| Can the 17 `RS_CD='1000'` responses be linked to this field? | **No — disproven.** See below |

### Was it a parity defect?

**Yes — but the opposite of what the audit concluded.** The audit recommended failing fast on a blank
partner id. That would have hardened a **refactor-only invention**. The real defect is the *override*: if
anyone had set `WECMS_EFS_PARTNER_ID`, all seven legs would have sent a value legacy never sends. The
so-called "fallback" was the only legacy-correct branch.

**`RS_CD='1000'` link: disproven.** The 17 live 201 calls sent `ID = GV00000001`, i.e. the `VAN_ORG_CD` —
byte-identical to what legacy sends. The envelope `ID` therefore cannot be the cause, and the earlier
"provision the partner id and re-probe" suggestion is withdrawn. RC `1000` remains an unexplained
gateway-side rejection for VAN Center.

### Final decision — remove the property; always use `VAN_ORG_CD`

Per the decision tree, this is the "legacy intentionally uses `vanOrgCd`" branch. Rather than keep a
ternary whose alternate branch can only ever break parity, the property is removed so the mistake is
unavailable. `VAN_ORG_CD` continues to come from `TB_FIRM_CONF` — the same authoritative source as legacy.

### Files changed

| File | Change |
|---|---|
| `.../van/client/EfsProperties.java` | `partnerId` field removed; class Javadoc records the per-leg legacy evidence and a "do not reintroduce" note |
| `.../service/RealtimeTransferService.java` | 3 sites → `String id = cmd.vanOrgCd();` |
| `.../service/ResultCheckService.java` · `.../service/BulkReceptionService.java` · `.../callback/service/BulkReceptionResultService.java` · `.../recipient/RecipientLookupService.java` | 1 site each, same change |
| `application-{local,sit,uat,prod}.yml` | `partner-id:` line removed |
| `.env.example` · `.env.local.example` · `.env.sit.example` · `.env.uat.example` · `.env.prod.example` | `WECMS_EFS_PARTNER_ID` removed |
| `batch-dispatch-variable-reference.md` | replaced with a retirement note carrying the legacy rationale |

**Deliberately not changed:** the now-unused `EfsProperties` injection in the five services. Removing a
constructor parameter would force edits across ~9 unrelated test classes for no behavioural gain;
`EfsProperties` is still a live bean (`enabled`, `base-url`, `endpoints`, timeouts). Recorded as a
known cosmetic leftover rather than expanding the diff.

### Tests added / changed

- `SchedulerConfigurationContractTest#partnerIdPropertyIsRetired` — parameterized over all four profiles;
  fails if the property is reintroduced in configuration.
- `OpsAlertServiceTest` — removed the `getPartnerId()` stub; the two ID assertions now read as legacy
  parity rather than "partner-id not configured".
- Five dispatch test classes: the misleading `// partnerId blank → ID = vanOrgCd` comments now state the
  legacy rule. **The assertions themselves were kept** — `assertThat(sent.id()).isEqualTo("ORG000001")`
  asserts `ID == VAN_ORG_CD`, which is now *proven* legacy-correct, so these are legacy-parity tests, not
  defect-encoding tests. Envelope-ID coverage across legs: 201 (`RecipientLookupServiceTest`),
  202/204 (`RealtimeTransferServiceTest`), 203 (`ResultCheckServiceTest`), 301 (`BulkReceptionServiceTest`),
  302 (`BulkReceptionResultServiceTest`), MSG (`OpsAlertServiceTest`).

### Remaining external confirmation

- **`RS_CD='1000'`** — still unexplained; owner VAN Center. The envelope `ID` is now eliminated as a
  candidate cause, which narrows their investigation.
- **`TB_FIRM_CONF.VAN_ORG_CD` per environment** — UAT/PROD rows must exist and carry the org code the
  gateway expects for that environment. DBA + VAN Center; **not live-verified.**

### Deployment impact

`WECMS_EFS_PARTNER_ID` is now an unknown variable. Setting it has no effect; hosts that export it are
unaffected (Spring ignores unbound env vars). No schema change. Behaviour for every environment where the
property was empty — i.e. all of them — is **byte-identical** to before.

---

## D-3 — Spring Boot Actuator

### Was it required?

**Yes.** Four places already assumed it, and all four were pointing at a 404:

| Reference | Location |
|---|---|
| Security allow-list | `SecurityConfig:75` — `.requestMatchers(…, "/actuator/health").permitAll()` |
| Logging filter skip | `RequestResponseLoggingFilter:69` — `uri.startsWith("/actuator")` |
| Deployment checklist | `docs/deployment/sit-deployment-checklist-wecms-batch.md` — "`/actuator/health` UP" step |
| Application docs | `wecms-api/CLAUDE.md` endpoint table lists `/actuator/health` |

`grep -c actuator build.gradle` → **0** before this change. No CI/CD probe or monitoring config referencing
it exists in-repo (`.gitlab-ci.yml` only forwards `project`/`file`; no Dockerfile or manifest).

### Implementation

**Dependency:** `implementation 'org.springframework.boot:spring-boot-starter-actuator'` (`build.gradle`),
with a comment recording why it exists and that the exposure list is deliberately narrow.

**Configuration** (`application.yml`, base — applies to local / SIT / UAT / PROD alike):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info      # allow-list: env/configprops/beans/heapdump/loggers stay OFF
  endpoint:
    health:
      show-details: never
      show-components: never
      probes:
        enabled: false
  info:
    env:   { enabled: false }     # /actuator/info returns {} — no build/git/env metadata
    java:  { enabled: false }
    os:    { enabled: false }
```

**Per-environment stance — uniform, deliberately.** No profile overrides `management`. The strictest
setting is applied everywhere rather than loosening local/SIT, because (a) the endpoint's only job here is
the checklist's liveness answer, which needs no detail, and (b) per-profile drift in this repo is itself a
recorded finding. If richer detail is ever wanted for local debugging, add it to `application-local.yml`
only, never to UAT/PROD.

### Security behaviour

- `/actuator/health` — **unauthenticated**, via the pre-existing `SecurityConfig:75` allow-list entry.
  Returns exactly `{"status":"UP"}`.
- `/actuator/info` — **authenticated**: it is not in the allow-list, so it falls under
  `.anyRequest().authenticated()` and returns 401 without a JWT. Intentional.
- Everything else under `/actuator/**` — **does not exist** (not exposed), so it 404s regardless of auth.
- `show-details: never` + `show-components: never` means the DataSource / disk-space contributors still
  determine the overall status (a DB outage correctly reports `DOWN`/503, which is what a probe should do)
  while their details — JDBC URL, validation query, disk paths, free bytes — are never serialised.
- No environment variable, credential, DB detail or gateway secret is reachable: `env`, `configprops` and
  `info`'s env contributor are all off.

### Tests added — `ActuatorEndpointSecurityTest` (18 tests)

Configuration-contract style, matching `SchedulerConfigurationContractTest` (no DB, no context, runs on
every commit), parameterized across all four profiles:

- exposure is a subset of `{health, info}` and always contains `health`
- none of `env`, `configprops`, `beans`, `heapdump`, `threaddump`, `loggers`, `mappings`,
  `scheduledtasks`, `shutdown`, `quartz`, `flyway`, `liquibase`, `*` appears in the exposure list
- `health.show-details` and `show-components` are both `never`
- `info`'s `env` / `java` / `os` contributors are all `false`
- `SecurityConfig` contains `"/actuator/health"` and contains **no** `/actuator/**`, `/actuator/*` or
  `"/actuator/info"` matcher — so `health` is the only unauthenticated actuator path
- `management.endpoints.web.base-path` is unset in every profile, since `SecurityConfig`,
  `RequestResponseLoggingFilter:69` and the SIT checklist all hardcode `/actuator`

Not covered: live HTTP behaviour (`/actuator/health` actually returning `UP`). That needs a booted context
with a reachable DB, which is the same gap as the rest of the suite (`G-7` in the coverage audit) —
verify it during the SIT checklist run.

### Remaining external confirmation

- Whether Ops wants Kubernetes-style `liveness`/`readiness` probe groups (`probes.enabled: true`). Left
  off because nothing in-repo deploys to K8s. **Ops decision.**
- The SIT checklist step can now pass; someone with SIT access should confirm it end-to-end.

### Deployment impact

New endpoint on every environment. One new dependency in the WAR. No schema change, no behaviour change to
any business endpoint. `CLAUDE.md`'s endpoint table becomes accurate rather than aspirational.

---

## Summary

| Finding | Legacy-verified conclusion | Classification | Fix |
|---|---|---|---|
| **D-1** | Legacy uses one clock per `TB_FIRM_TRAN` row and reads it with that same clock; UTC pay-target storage is legacy-correct | **Parity defect (reader)** | Reader aligned to UTC + previous-day window + monotonic aging |
| **D-2** | Envelope `ID` is always `TB_FIRM_CONF.VAN_ORG_CD` on all seven legs; no partner-id concept exists in legacy | **Parity defect (the override, not the fallback)** — audit recommendation reversed | Property removed; `VAN_ORG_CD` unconditional |
| **D-3** | n/a — refactor-only operability gap | **Missing dependency** | Actuator added, `health` public + `info` authenticated, nothing else exposed |

## Verification

**Authoritative run — the real working tree, clean, no cached results:**

```
./gradlew clean compileJava cleanTest test jacocoTestReport --no-build-cache
→ BUILD SUCCESSFUL in 4m 1s
→ 269 suites · 2171 tests · 2151 passed · 0 failed · 0 errors · 20 skipped
```

Coverage after the fixes (JaCoCo injected via an external init script — the plugin is still not in
`build.gradle`, see the caveat below):

| Scope | Line | Branch | vs 2026-07-29 baseline |
|---|---|---|---|
| Whole application | **56.0 %** | **41.3 %** | 55.7 % / 41.0 % |
| `**/payment/dispatch/**` | **79.9 %** | **60.6 %** | 78.2 % / 58.5 % |
| `ResultCheckService` (D-1) | **93.8 %** | **75.6 %** | 93.3 % / 71.6 % |

Every suite touched or added passes: `ResultCheckSelectionBoundaryTest` 24, `ActuatorEndpointSecurityTest`
18, `SchedulerConfigurationContractTest` 24, `ResultCheckPollTest` 12, `ResultCheckServiceTest` 4,
`RecipientLookupServiceTest` 5, `BulkReceptionResultServiceTest` 12, `RealtimeTransferServiceTest` 12,
`OpsAlertServiceTest` 7 — 0 failures each.

The 20 skipped are the pre-existing class-level `@Disabled` on `VasAccountIntegrationTest`, unchanged.

**Timeline note (recorded because it affected how verification was done).** For roughly 8 minutes the
working tree did not compile: an untracked, actively-edited package
(`domain/payment/dispatch/monitor/` + `StuckPayFileMonitorJob`) was mid-refactor by another author, and two
of their test classes had fallen behind their production constructors — 4 errors, none in any file touched
here. Rather than edit someone else's in-flight work, verification first ran in an isolated copy with those
two classes excluded (2073 tests, 0 failures). Once their work compiled, the **full suite was re-run on the
real tree** and that is the result above. The interim isolated-copy coverage figures are superseded and
should not be quoted.

**Still outstanding for durable verification:** the `jacoco` plugin is not in `build.gradle`, so coverage is
reproducible only with the external init script and is not a CI gate.
