---
slug: transfer-history
jex_wsvc_id: trns_0003_001_r001  # citation only — never used as a refactor runtime identifier
owner: Sonny (Ran Sokny)
status: working   # backend + frontend both landed 2026-05-20
last_updated: 2026-05-20
---

> **2026-05-20 — Backend landed.** Spring Boot implementation delivered per
> resolutions in Prompts 2 & 3 (OQs 1–8 all resolved with spec defaults).
> Code lives under
> [wecms-api/src/main/java/com/kosign/wecms/domain/banking/transferhistory/](../../../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/)
> with the shared `NapasRemitTrnsHstr` entity reused (no duplicate
> entity). Helper index shipped as
> `V196__transfer_history_helper_index.sql`.
> Sprint-1 interim endpoint at `/api/v1/banking/transfers/history`
> retained `@Deprecated` until removed in a follow-up cleanup.

> **2026-05-20 — Frontend landed.** Next.js page at
> `/banking/transfer-history` ships in
> [wecms-web/app/(dashboard)/banking/transfer-history/page.tsx](../../../../../../wecms-web/app/(dashboard)/banking/transfer-history/page.tsx)
> backed by
> [TransferHistoryClient.tsx](../../../../../../wecms-web/components/banking/internal-transfer/TransferHistoryClient.tsx)
> + the existing
> [TransferHistoryPage.tsx](../../../../../../wecms-web/components/banking/internal-transfer/TransferHistoryPage.tsx).
> Wire types in [lib/types/api.ts](../../../../../../wecms-web/lib/types/api.ts)
> flipped from the `ApiTransferRow` / `ApiTransferHistoryResponse`
> UPPER_SNAKE holdover to lowercase snake_case
> (`ApiTransferHistoryRow` / `ApiTransferHistoryResponse`). API client
> at [`lib/api/banking.ts#fetchTransferHistory`](../../../../../../wecms-web/lib/api/banking.ts)
> calls `/api/v1/banking/internal-transfer/transfer-history` with the
> full snake_case query-param set. Mappers
> [`mapTransferHistoryRow` / `mapTransferHistoryTotals`](../../../../../../wecms-web/lib/api/mappers.ts)
> own the wire ↔ in-memory translation. Screen id
> `SCREEN_IDS.TRANSFER_HISTORY = "transfer-history"` registered in
> [lib/viewSettingScreens.ts](../../../../../../wecms-web/lib/viewSettingScreens.ts)
> (popover NOT rendered per § 8.4 — same precedent as approval-line /
> erp-transaction-history). i18n keys
> `transfer_history.{title, filter.*, col.*, status.*, footer.*,
> empty.*, error.*}` added in EN / KO / VI under
> [`locales/`](../../../../../../wecms-web/locales/). Loading skeleton, empty
> state, and a typed error banner are wired in
> [TransferHistoryPage.tsx](../../../../../../wecms-web/components/banking/internal-transfer/TransferHistoryPage.tsx);
> Coocon-timeout / Coocon-failure codes have dedicated localized
> messages even though the current read path does not raise them
> (they're carried for the upstream Transfer Register MR's error
> surface).

# Banking > Internal Transfer > Transfer History — Refactor Feature Spec

**Module:** Banking
**Menu:** Internal Transfer > Transfer History
**Route:** `/banking/internal-transfer/transfer-history` (slug — matches the
existing frontend component `TransferHistoryTable` per
[wecms-web/CLAUDE.md](../../../../../../wecms-web/CLAUDE.md))
**Refactor screen ID:** `transfer-history`
**JEX counterpart:** `trns_0003_001` (cited only as a JEX file:line pointer below
— per workspace [CLAUDE.md § Cross-Tree Rules](../../../../../../CLAUDE.md) the legacy
WSVC id is never re-used as a refactor identifier surface).

JEX is **read-only reference**. This spec captures the refactor design; JEX
behavior is the functional source of truth.

---

## 1. Overview & business purpose

Lists the user's outbound NAPAS-routed bank transfers (inter-bank and
intra-bank) for the current tenant over a date range, with per-row status from
the Coocon iSAS-NX scraping engine. The page shows the live success / failure of
each remittance so the operator can confirm whether the bank actually processed
the transfer — independent of WeCMS's own state — and reconcile follow-up
action (re-submit, escalate, settle).

Concretely, each row was created by the upstream "Transfer Register" flow
(`trns_0002_001_c001`) when the operator submitted a transfer; the row's
`TRNS_STTS` / `TRNS_MSG` / `TRSC_AMT` come from the bank's online-banking
response that was scraped by the Coocon engine on the user's PC at submit time
(see §3). Transfer History is a **read-only** view over those rows.

---

## 2. JEX source-of-truth map

| Asset | Path | Anchor |
|---|---|---|
| View JSP | [WECMS DEV/WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp](../../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp) | page title `trns_0003_001_view.jsp:115`; date inputs `:118-142`; status radios `:144-160`; table headers + `data-sort` keys `:164-181`; pagination shell `:297-326` |
| TypeScript | [WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0003_001.ts](../../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0003_001.ts) | input shape `trns_0003_001.ts:39-45`; default 7-day window `:149`; AJAX call `:175-196`; row render `:276-341`; client-side footer build `:243-258` |
| Action JSP (read) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp) | `validateInput` `:32-50`; `getTotCnt` `:52-58`; `getDynamic` `:60-84` (WHERE assembly: BIZ_KEY + REG_DTM range + TRNS_STTS); `trns_0003_001_r001` `:86-100` (paged read); `normalizeTrnsDtFrmUTC` `:101-105`; main flow `:129-159` |
| WSVC XML (read) | [WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001_r001.xml](../../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001_r001.xml) | INPUT fields `:32-38` (FROM_DT, TO_DT, PAGE_NO, PAGE_PER_CNT, TRNS_STTS); OUTPUT scalars `:39-46` (totals); OUTPUT record sub-fields `:48-64`; `mntLogYn=Y` `:11` (framework-level audit) |
| WSVC XML (view) | [WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001.xml](../../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001.xml) | page-level service registration |
| IDO (rows) | [WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml](../../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml) | SELECT body `:24-75`; bank/user-name joins via `wb_get_bank_nm` `:29`, `WB_GET_NAPAS_BANK_NM` `:30`, `WB_GET_USER_NM` `:31`; 21 OUTPUT items `:81-103`; dual `??` placeholders for WHERE + ORDER BY `:78-79` |
| IDO (count + totals) | [WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml](../../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml) | TOT_CNT + per-CCY sums + per-CCY counts `:24-34` |
| Entity | [WECMS DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstr.java](../../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstr.java) | `@Table("NAPAS_REMIT_TRNS_HSTR")` `:36`; column mappings `:40-108`; TRSC_AMT `@Positive` `:66-68`; settlement `TRNS_STLM_ID` / `TRNS_STLM_YN` `:104-108` |
| Entity PK | [WECMS DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstrPk.java](../../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstrPk.java) | composite PK (BIZ_KEY, SEQ_NO) `:20-23` |
| DAO | [WECMS DEV/WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java](../../../../../../WECMS%20DEV/WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java) | singleton `:26-30`; `findByDynamic` `:53-68`; `create` (used by Register, not by History) `:71-112`; `patchByTrnsStlmId` `:114-130` |
| Upstream Register action | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0002_001_c001_act.jsp](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0002_001_c001_act.jsp) | INSERT path `:101-108` (`copyFrom(input)`; `setTrnsStlmYn(NO)`; DAO `create`) |
| Upstream Register TS | [WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0002_001.ts](../../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0002_001.ts) | Coocon job selection `:250-273`; scrape invocation `_v2nxisas.callNxisas` `:340-410`; result → DTO mapping `:357-389`; success code `"00000000"` check `:365` |
| Live DEV URL | `https://dev.wecms.vn/trns_0003_001.act` | — |

ML codes registered for the legacy view (Web Service View ML group `trns_0003_001`):

| ID | JSP file:line | Semantics |
|---|---|---|
| `trns_0003_001_m001` | `trns_0003_001_view.jsp:115` | Page title "Transfer History" |
| `trns_0003_001_m002` | `trns_0003_001_view.jsp:148` | Status radio: All |
| `trns_0003_001_m003` | `trns_0003_001_view.jsp:153` | Status radio: Success (filter value `00000000`) |
| `trns_0003_001_m004` | `trns_0003_001_view.jsp:158` | Status radio: Failed (filter value `99999999` sentinel; backend treats any non-`00000000` as failed — `trns_0003_001_r001_act.jsp:77-80`) |
| `trns_0003_001_m005..m010` | `trns_0003_001_view.jsp:168-179` | Column headers (Transaction Date, Sender Bank, Recipient Bank, Amount, Remarks, Status / Time) |
| `trns_0003_001_js003..js011` | `trns_0003_001.ts:93-101` | Body labels: Complete / Error / Total / Transaction(s) / No Data Found / Failed / Success |

Translate every ML id to a refactor i18n key (see §9). **Never re-use the
`trns_0003_001_*` ids as identifiers in refactor code** — only as citations.

---

## 3. Coocon scraping engine contract

> **Architectural note.** The Transfer History *read* endpoint does NOT itself
> invoke the Coocon engine. The engine is invoked upstream by the **Transfer
> Register** page (`trns_0002_001`) — its result populates `NAPAS_REMIT_TRNS_HSTR`,
> and History simply lists those rows. The contract is documented here because
> (a) it is the data source for every visible `TRSC_AMT` / `TRNS_STTS` /
> `TRNS_MSG` value, and (b) the refactor's Internal Transfer port (a separate
> feature MR) needs the same contract.

### 3.1 What is "coocon.exe"?

Coocon iSAS-NX (a.k.a. `iBASENX`) is a Coocon-provided Windows `.exe` that the
end user installs on the operator PC. It runs as a local agent that knows how
to drive each Vietnamese bank's online-banking website / e-banking API on
behalf of the logged-in operator (the operator's bank credentials are stored
encrypted in the engine, indexed by `BANK_CERT_SEQ` per
[WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0002_001.ts:281-299](../../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0002_001.ts)).

The browser does **not** call the engine through a Java backend subprocess; it
calls it directly from the page via JavaScript loaded from
[WECMS DEV/WECMS/web/view/jex/wabooks/include/inc_wabooks_nxisas_v2.jsp](../../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/include/inc_wabooks_nxisas_v2.jsp).
The JEX Java side never spawns `ProcessBuilder` for the engine.

### 3.2 Invocation contract (browser ↔ local engine)

| Concern | Value | Source |
|---|---|---|
| Engine binary location | Installed by Coocon's MSI under the operator's PC (path proprietary to the Coocon installer; not configurable by WeCMS) | — |
| Engine version pinned by WeCMS | `2023.4.7.0` | `isasscaping.js:13` |
| Transport — HTTPS poll/UAC | `https://ibasews.coocon.co.kr:14101/` (the hostname resolves to **127.0.0.1** via the engine installer's hosts-file entry so the engine can serve a real CA-signed cert from localhost) | `isasscaping.js:19-22` |
| Transport — WebSocket (streaming) | `wss://ibasews.coocon.co.kr:14102/` | `isasscaping.js:25-28` |
| Licensing | `{ MstCompNo: "5568800909", SubCompNo: "5568800909", ProductNo: "00000014" }` — embedded in every `Open` message | `isasscaping.js:4`, `:428` |
| Run timeout | `5 * 60 * 1000` ms (5 min — UAC permission window) | `isasscaping.js:15` |
| Update timeout | `3 * 60 * 1000` ms (3 min — auto-update of the engine module) | `isasscaping.js:16` |
| Thread length | 1 by default; configurable per call via `ThreadLength` in the `Open` message | `isasscaping.js:38`, `:428` |
| Result shape | `{ ErrorCode, ErrorMessage, Result: { transfer: [ { acctNum, currency, remitAmt, fee, memo, memo2, remitAcctNum, acceptName, transType, remitBankCode } ] } }` | `trns_0002_001.ts:361-377` |
| Success sentinel | `ErrorCode === "00000000"` | `trns_0002_001.ts:365`; matches `IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml` filter `:73-80` in `_act.jsp` |
| Per-bank job code | `TRANSFER` / `TRANSFER_INTERNAL` / `TRANSFER_ML` / `TRANSFER_INTERNAL_ML` selected by `BANK_CD` (BIDV `11704008`, IBK `11704003`, Shinhan `11704088`, KEBHana `11704080`, Vietcom `11704004`; DIGIBIZ class uses non-`_ML` job) | `trns_0002_001.ts:250-273` |
| Concurrency / retry | Engine handles its own retries inside `RunTimeout`; WeCMS does NOT auto-retry — operator must re-submit. Idempotency is not guaranteed at the engine layer; the refactor relies on the operator confirming via Transfer History. | `isasscaping.js:599-635` |
| Credential handling | Bank credentials stored encrypted in the engine, indexed by `BANK_CERT_SEQ` (`certObj.CERT_INFO_CON` etc.); the browser passes credential refs in the `Open` message — **plaintext credentials never traverse the Java backend** | `trns_0002_001.ts:281-299` |

### 3.3 What lands in the DB after a scrape

After the engine returns a success result, the upstream
`trns_0002_001_c001_act.jsp:101-108` writes one row into
`NAPAS_REMIT_TRNS_HSTR`. The row's fields map from the engine result as
follows (per `trns_0002_001.ts:357-389`):

```text
Engine result field           →  DB column (NAPAS_REMIT_TRNS_HSTR)
─────────────────────────────────────────────────────────────────
ErrorCode                     →  TRNS_STTS         ("00000000" on success)
ErrorMessage                  →  TRNS_MSG          (engine error description)
Result.transfer[0].acctNum    →  OUT_ACCT_NO       (sender account)
Result.transfer[0].currency   →  CCY_CD            ("VND" / "USD")
Result.transfer[0].remitAmt   →  TRSC_AMT          (the Transfer Amount visible in History)
Result.transfer[0].fee        →  FEE
Result.transfer[0].memo       →  RMRK
Result.transfer[0].memo2      →  RMRK2
Result.transfer[0].remitAcctNum  →  IN_ACCT_NO
Result.transfer[0].acceptName →  OWAC_NM           (recipient holder name)
```

Plus client-side values not sourced from the engine: `OUT_BANK_CODE`,
`IN_BANK_CODE`, `IN_NAPAS_BANK_CODE`, `TRANS_TYPE`, `FEE_DFR_SBJ`. On engine
failure (non-`00000000`), the row is still inserted with the engine's
`ErrorCode` / `ErrorMessage` and the operator's *intended* amount echoed back
(`trns_0002_001.ts:379-386`).

### 3.4 Refactor stance on Coocon

- **Transfer History (this feature)** does NOT call the Coocon engine. It only
  reads the rows the engine produced upstream.
- **Internal Transfer Register (a separate feature MR — out of scope here)**
  must port the upstream `_v2nxisas.callNxisas` invocation into the Next.js
  client (keeping the engine talk client-side; the Spring backend still never
  spawns a subprocess). That MR owns the engine contract surface.
- The Spring backend never embeds a `coocon.exe` path, `ProcessBuilder`, or any
  subprocess for this feature. Any future migration of the engine away from
  the operator PC would land as a separate architectural change (out of R&D
  scope per project memory `project_refactoring_scope` (Needs Confirmation)).

---

## 4. API surface for the refactor

One read endpoint. No write endpoints in this feature.

### 4.1 Endpoint

```
GET /api/v1/banking/internal-transfer/transfer-history
```

Auth: JWT-protected. Tenant resolved from `TenantContext.currentBizKey()` —
the request body / query string MUST NOT carry `biz_key`.

### 4.2 Query parameters (snake_case, validated)

| Param | Type | Required | Notes |
|---|---|---|---|
| `from_dt` | string YYYYMMDD | yes | `@Pattern("\\d{8}")`; mirrors JEX `FROM_DT` (`WSVC.trns_0003_001_r001.xml:33`) |
| `to_dt` | string YYYYMMDD | yes | `@Pattern("\\d{8}")`; mirrors JEX `TO_DT` (`WSVC.trns_0003_001_r001.xml:34`); must be ≥ `from_dt` (`ValidationHelper.validatePeriod`, action `:39`) |
| `trns_stts` | string | no | one of `""` (all), `"00000000"` (success), `"99999999"` (failed sentinel — backend translates to `<> '00000000'` per legacy semantics, action `:73-81`) |
| `page_no` | int ≥ 1 | no | default 1 |
| `page_per_cnt` | int 10/15/20/50/100 | no | default 15 (per the legacy default-selected `<li data-val="10" class="selected">15</li>` in `trns_0003_001_view.jsp:303`) |
| `sort_field` | enum | no | one of `TRNS_DT`, `OUT_BANK_NM`, `IN_BANK_NM`, `TRSC_AMT`, `RMRK` (the `data-sort` keys in `trns_0003_001_view.jsp:168-179`) |
| `sort_direction` | enum | no | `ASC` / `DESC` — default `DESC` for `TRNS_DT`, `ASC` otherwise |

Validate with a Spring `@Validated` record DTO (Bean Validation); never read
`Map<String, Object>` in the controller (per
[wecms-api/CLAUDE.md § API / DB Rules](../../../../../CLAUDE.md)).

### 4.3 Response DTO (camelCase Java → snake_case wire via global Jackson)

```text
TransferHistoryResponse
├── total_count : long                       (TOT_CNT)
├── total_vnd_amount : decimal               (TOT_VND_AMT)
├── total_usd_amount : decimal               (TOT_USD_AMT)
├── total_vnd_count : long                   (TOT_VND_CNT)
├── total_usd_count : long                   (TOT_USD_CNT)
└── rows : TransferHistoryRow[]
        ├── seq_no            : long
        ├── trns_dt           : string YYYYMMDD     (date portion of TRNS_DT)
        ├── trns_tm           : string HHmm         (time portion — IDO splits REG_DTM)
        ├── out_bank_code     : string
        ├── out_bank_nm       : string               (resolved server-side; see §4.4)
        ├── out_acct_no       : string
        ├── in_bank_code      : string
        ├── in_bank_nm        : string               (resolved server-side; see §4.4)
        ├── in_acct_no        : string
        ├── owan_nm           : string               (recipient holder name from OWAC_NM)
        ├── ccy_cd            : string ("VND"/"USD")
        ├── trsc_amt          : decimal
        ├── fee               : decimal | null
        ├── trans_type        : string | null
        ├── rmrk              : string | null
        ├── trns_stts         : string  ("00000000" or engine error code)
        ├── trns_msg          : string | null
        └── user_nm           : string               (resolved from REG_ID; see §4.4)
```

Stable identifier *values* (`"00000000"`, `"VND"`, `"USD"`) stay UPPERCASE per
[wecms-web/CLAUDE.md § Naming](../../../../../../wecms-web/CLAUDE.md) — only the wire
*keys* are snake_case.

### 4.4 Bank-name + user-name resolution

Legacy delegates to three PL/pgSQL helpers
(`wb_get_bank_nm(out_bank_code, 'S')`, `wb_get_napas_bank_nm(in_bank_code, 'S', 'EN')`,
`wb_get_user_nm(reg_id)` — `IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:29-31`).
The refactor resolves them in Java:

- `out_bank_nm` — look up `BANK_INFM` (already ported, V20). Use the existing
  `BankCdResolver` service.
- `in_bank_nm` — same as Contacts' OQ-7 unverified-coverage situation. Two
  options:
  1. **Reuse the V114 `wb_get_napas_bank_nm_or_citad_bank_nm` funnel** via
     `LegacyFunctionRepository.resolveNapasOrCitadBankName(code, langCd)` —
     already wired for Contacts; lowest-risk parity (see
     [wecms-api/CLAUDE.md § Surviving legacy functions](../../../../../CLAUDE.md)).
  2. Resolve from `BANK_INFM` once the NAPAS code-space is audited end-to-end.
  Decision deferred to Mr. Jung — see §10 OQ-1.
- `user_nm` — `LEFT JOIN UserInfm` keyed on `REG_ID`, same shape as
  Contacts / ERP Transaction History (`erptransactionhistory` precedent).

Resolve in one batched repository call per request (do not N+1 per row).

### 4.5 Error codes (additions to `messages.properties`)

| Code | Meaning |
|---|---|
| `WBE_VALIDATION` (existing) | bean-validation failure on `from_dt` / `to_dt` / `page_*` |
| `WBE_TRANSFER_HISTORY_INVALID_PERIOD` | `to_dt < from_dt` |
| `WBE_TRANSFER_HISTORY_PERIOD_TOO_LONG` | range exceeds the legacy cap, if any (legacy `ValidationHelper.validatePeriod` — confirm cap with PO; current source shows the call but no documented numeric cap in the action JSP — see §10 OQ-2) |
| `WBE_TRANSFER_HISTORY_NOT_FOUND` | `seq_no` not in tenant (only relevant if we add a detail endpoint later — out of scope here) |

No `WBE2014` reuse — Transfer History does not share ERP Transaction History's
"not found" semantics.

---

## 5. DB impact

### 5.1 Tables read

Only one table is read by this feature: **`NAPAS_REMIT_TRNS_HSTR`**.

| Column | Type (DEV) | Notes |
|---|---|---|
| `BIZ_KEY` | VARCHAR(20) NOT NULL | tenant scope |
| `SEQ_NO` | NUMERIC(10,0) NOT NULL | per-tenant sequence (allocated by `SeqHelper.getNextVal` on insert — `NapasRemitTrnsHstrDAO.java:74-79`; **not** in the V27 `wb_get_next_val` branch list, so refactor INSERTs would need a Java generator — but History is read-only so this is **not** in scope here) |
| `OUT_BANK_CODE` | VARCHAR(20) NOT NULL | sender bank |
| `OUT_ACCT_NO` | VARCHAR(100) NOT NULL | sender account |
| `TRANS_TYPE` | CHAR(1) | transfer-type flag |
| `IN_NAPAS_BANK_CODE` | VARCHAR(10) | recipient NAPAS code |
| `IN_ACCT_NO` | VARCHAR(100) NOT NULL | recipient account |
| `IN_BANK_CODE` | VARCHAR(20) | recipient bank code (CITAD-style) |
| `CCY_CD` | CHAR(3) NOT NULL | "VND" / "USD" |
| `TRSC_AMT` | NUMERIC(20,6) NOT NULL | Transfer Amount — engine-scraped (§3.3) |
| `FEE_DFR_SBJ` | CHAR(1) | fee-bearer flag |
| `FEE` | NUMERIC(20,6) | fee amount |
| `RMRK` / `RMRK2` | VARCHAR(500) | remarks |
| `OWAC_NM` | VARCHAR(200) | recipient holder name |
| `REG_DTM` | VARCHAR(14) NOT NULL | UTC `yyyyMMddHHmmss` — split into `trns_dt` + `trns_tm` on the wire |
| `REG_ID` | VARCHAR(100) NOT NULL | operator user id |
| `TRNS_STTS` | VARCHAR(20) | engine `ErrorCode` |
| `TRNS_MSG` | VARCHAR(255) | engine `ErrorMessage` |
| `TRNS_STLM_ID` / `TRNS_STLM_YN` | VARCHAR(100) / VARCHAR(1) | settlement linkage (out of scope for History) |

(21 columns total; verified via DEV `information_schema.columns`, read-only MCP.)

Constraints: PRIMARY KEY `(BIZ_KEY, SEQ_NO)` — only one index on DEV
(`napas_remit_trns_hstr_pk`). No UNIQUE, no CHECK, no FOREIGN KEY on DEV
(`pg_constraint` returned only the PK).

Row count estimate (DEV `pg_class.reltuples`): **~239 rows** (live DEV is light
— production volume will be higher; design for paging from day one).

`TRNS_STTS` distribution on DEV (top sample):

| `TRNS_STTS` | rows |
|---|---|
| `00000000` (success) | 67 |
| `80002421`, `80002411`, `80003271`, … (engine errors) | 25 / 16 / 14 / … |
| `LOGIN_CAN` (operator cancelled the engine prompt) | 6 |

The non-`00000000` codes are the Coocon engine's error catalogue — refactor
does not parse them, only echoes `trns_stts` + `trns_msg` to the wire for the
operator to read.

### 5.2 Tables written

**None.** This feature is read-only.

(Upstream `trns_0002_001_c001_act.jsp:101-108` writes the rows via
`NapasRemitTrnsHstrDAO.create` and `NAPAS_REMIT_TRNS_HSTR_C001` IDO — that
write lives in the Internal Transfer Register port, not here.)

### 5.3 Flyway migration plan

- **Schema** — add `NAPAS_REMIT_TRNS_HSTR` to the local Flyway set with full
  column-for-column parity per the
  [§ Legacy Schema Parity Rule](../../../../../CLAUDE.md). The table is
  already in V13 (Sprint 1 banking schema) and was widened by V18 / V19. **No
  new migration is needed for the read endpoint.** Confirm via:

  ```
  ./gradlew flywayInfo
  ```

  and `\d napas_remit_trns_hstr` on local before opening the MR.

- **Indexes** — DEV has only the PK. The refactor list-query filters by
  `BIZ_KEY + substring(REG_DTM,1,8)` over a date range (action `:68`), sorts
  by `TRNS_DT` desc by default, and counts via `IDO.…_R003_CNT`. Add a helper
  index:

  ```sql
  -- Migration name placeholder (DO NOT CREATE in this MR — see §10 OQ-3):
  --   V{N}__transfer_history_idx.sql
  CREATE INDEX IF NOT EXISTS ix_napas_remit_trns_hstr_biz_regdtm
      ON napas_remit_trns_hstr (biz_key, substr(reg_dtm, 1, 8) DESC, seq_no DESC);
  ```

  Helper indexes beyond DEV are allowed by the parity rule
  ([wecms-api/CLAUDE.md § Legacy Schema Parity Rule](../../../../../CLAUDE.md)).
  Whether to ship the index in the same MR or defer to a perf MR — see §10
  OQ-3.

- **Seed** — re-use any rows already imported by V22 / V29; if DEV's 239 rows
  for tenant `VN_VNM0004_000001` aren't already in local, add a seed migration
  importing the top ~25 most-recent rows (matching the Sprint 1 precedent for
  `acct_trns_hstr` / `busa_doc_rcv_pay_his` per V22).

- **`wb_get_next_val` branch** — Sprint 1 audit (`wecms-api/CLAUDE.md`)
  records `napas_remit_trns_hstr.seq_no` as one of the 11 DEV branches but
  intentionally NOT ported because Sprint 1 was read-only. History stays
  read-only. **No branch added.**

---

## 6. Validation rules

### 6.1 Server

| Field | Rule | Source |
|---|---|---|
| `from_dt` | required, `\d{8}`, valid calendar date | `ValidationHelper.validateRequired` / `validateDate8` (action `:33-37`) |
| `to_dt` | required, `\d{8}`, valid calendar date, ≥ `from_dt` | `ValidationHelper.validatePeriod` (action `:39`) |
| `page_no` | optional, ≥ 1 | `ValidationHelper.validatePageNo` (action `:43`) |
| `page_per_cnt` | optional, one of {10, 15, 20, 50, 100} | `ValidationHelper.validatePagePerCnt` (action `:48`) — confirm exact whitelist with PO; the JSP `<ul>` shows 10/20/50/100 but the selected default is **15** (`trns_0003_001_view.jsp:303-307`); see §10 OQ-2 |
| `trns_stts` | optional, one of `""`, `"00000000"`, `"99999999"` | the radio's `status` attribute carries these literal values (`trns_0003_001_view.jsp:146-159`) |
| `sort_field` | optional, whitelist enum (see §4.2) | refactor addition — JEX takes the `paramSort` string verbatim (`trns_0003_001.ts:184-193`); refactor MUST whitelist (per the §"Common table sort" rule in [wecms-api/CLAUDE.md](../../../../../CLAUDE.md)) |

### 6.2 Client

- Date pickers default to **last 7 days** ending today
  (`trns_0003_001.ts:149` — `comm_0106_001.loadinitializeDate(..., -7, ...)`).
- `to_dt < from_dt` shows `WBE1000` and aborts the call
  (`trns_0003_001.ts:154`); refactor surfaces the same as a system error
  dialog with code `WBE_VALIDATION`.
- Empty data shows the localized "No Data Found" string
  (`trns_0003_001_js009`, `trns_0003_001.ts:99`).

---

## 7. Permission model

| Aspect | Rule |
|---|---|
| Authentication | JWT-protected; `JwtAuthenticationFilter` populates `TenantContext` |
| Authorization | All authenticated users of the tenant can read their own tenant's transfer history. No `@PreAuthorize` admin gate (follows JEX — the WSVC has `NORM_USER_ONLY_YN=Y` per `WSVC.trns_0003_001_r001.xml:26`, meaning **normal users only**, but no ADMIN-only restriction). |
| Tenant scope | `TenantContext.currentBizKey()` is the only allowed `BIZ_KEY` source. The request MUST NOT accept `biz_key` from the wire. |
| Audit | The JEX WSVC enables `mntLogYn=Y` (`WSVC.trns_0003_001_r001.xml:11`) which writes a framework-level call-log row. The refactor's audit-log slug is `transfer-history`. Whether to write an `AUDIT_LOGS_INFM` row per read is a project decision — see §10 OQ-4. Default recommendation: **no audit row** (matches `approval-line` / `erp-transaction-history` precedent — read-only views do not write audit rows; only mutating endpoints do). |

Per workspace [CLAUDE.md § Refactor identifier naming](../../../../../../CLAUDE.md):
audit `WSVC_ID` (if/when written) MUST be the slug `transfer-history`, never
`trns_0003_001`.

---

## 8. Frontend architecture

### 8.1 Route + page

```
app/(dashboard)/banking/internal-transfer/transfer-history/page.tsx
```

(matches the existing component path `components/banking/internal-transfer/`
referenced in [wecms-web/CLAUDE.md § Known Inconsistency — two table patterns](../../../../../../wecms-web/CLAUDE.md)).

### 8.2 Reuse — DO NOT introduce new abstractions

Per the workspace [CLAUDE.md](../../../../../../CLAUDE.md) and existing
[wecms-web/CLAUDE.md](../../../../../../wecms-web/CLAUDE.md), the following pieces already
exist and MUST be reused:

| Need | Existing piece |
|---|---|
| Shell, header, sidebar | `<AppShell>` via `app/(dashboard)/layout.tsx` |
| Page title + action buttons | [`components/shared/PageHeader.tsx`](../../../../../../wecms-web/components/shared/PageHeader.tsx) + [`PageActionButtons.tsx`](../../../../../../wecms-web/components/shared/PageActionButtons.tsx) |
| Search input | [`SearchInput.tsx`](../../../../../../wecms-web/components/shared/SearchInput.tsx) |
| Date range picker | `DateRangePicker.tsx` (currently UI-only — see existing OQ in wecms-web/CLAUDE.md) |
| Pagination | [`Pagination.tsx`](../../../../../../wecms-web/components/shared/Pagination.tsx) |
| Table + colgroup + footer | The bare-`<table>` pattern + already-scaffolded [`TransferHistoryTable.tsx`](../../../../../../wecms-web/components/banking/internal-transfer/) + [`TransferHistoryTableColumns.tsx`](../../../../../../wecms-web/components/banking/internal-transfer/) |
| Currency formatting | `_osmc.ui.formatAmount` equivalent in the existing `lib/format/currency.ts` (or whichever shared helper banking uses today — re-use, do not duplicate) |
| Status badge | `<StatusBadge>` from `components/payment/approval-box/` — same `wb-status-*` token system covers success/failed |
| System dialogs | `useDialog()` — type `"error"` for API/validation failures per the [wecms-web § Dialog variant rule](../../../../../../wecms-web/CLAUDE.md) |
| Wire ↔ in-memory mapping | Extend `lib/api/mappers.ts` (existing file) with `toTransferHistoryRow(...)` |

### 8.3 Wire-type cutover

[lib/types/api.ts](../../../../../../wecms-web/lib/types/api.ts) currently carries
`ApiTransferRow` / `ApiTransferHistoryResponse` as **UPPER_SNAKE holdovers**
flagged in [wecms-web/CLAUDE.md § Holdovers](../../../../../../wecms-web/CLAUDE.md). Per
the workspace full-stack ownership rule, the backend MR that lands this feature
MUST flip those two wire types to lowercase snake_case in the same MR (and
update every consumer). The CTO directive (2026-05-08) makes this non-optional.

### 8.4 View Setting

**Not adopted in this feature.** Same precedent as `approval-line`,
`erp-transaction-history`, `chart-of-accounts` — the legacy JEX panel
(`trns_0003_001_view.jsp`) has no toolbar list-icon button. Reserve the slug
`transfer-history` in the frontend `SCREEN_IDS` registry
([wecms-web/lib/viewSettingScreens.ts](../../../../../../wecms-web/lib/viewSettingScreens.ts))
for log/audit consistency only — no `SYS_SCR_OPTION_WECMS` seed.

Excel export (`POST /api/v1/banking/internal-transfer/transfer-history/export`)
is **out of scope for this feature MR**; if requested later, follow the
`AccountInquiryExportProvider` precedent and add a `transfer-history` seed to
`SYS_SCR_OPTION_WECMS` *only then* (because the shared `ExcelExportService`
gates on the View Setting registration per
[wecms-api/CLAUDE.md V153 / V154 § Excel export](../../../../../CLAUDE.md)).

---

## 9. Non-functional

### 9.1 i18n (one key per ML id — never reuse the ML id)

Refactor i18n keys live under `banking.transfer_history.*` in
[wecms-web/locales/](../../../../../../wecms-web/locales/) (EN/KO/VI; KH ships when the
real translation arrives — `messages_km.properties` follows the
[wecms-api § i18n Rules](../../../../../CLAUDE.md) "add when real
translation arrives" rule).

| Refactor key | EN default | JEX origin |
|---|---|---|
| `banking.transfer_history.title` | "Transfer History" | `trns_0003_001_m001` |
| `banking.transfer_history.filter.status.all` | "All" | `trns_0003_001_m002` |
| `banking.transfer_history.filter.status.success` | "Success" | `trns_0003_001_m003` |
| `banking.transfer_history.filter.status.failed` | "Failed" | `trns_0003_001_m004` |
| `banking.transfer_history.column.trns_dt` | "Transaction Date" | `trns_0003_001_m005` |
| `banking.transfer_history.column.out_bank_nm` | "Sender Bank" | `trns_0003_001_m006` |
| `banking.transfer_history.column.in_bank_nm` | "Recipient Bank" | `trns_0003_001_m007` |
| `banking.transfer_history.column.trsc_amt` | "Amount" | `trns_0003_001_m008` |
| `banking.transfer_history.column.rmrk` | "Remarks" | `trns_0003_001_m009` |
| `banking.transfer_history.column.status_time` | "Status / Time" | `trns_0003_001_m010` |
| `banking.transfer_history.footer.total_ccy` | "Total ({ccy_cd})" | `trns_0003_001_js001` |
| `banking.transfer_history.footer.count` | "{count} Transaction(s)" | `trns_0003_001_js008` |
| `banking.transfer_history.empty` | "No Data Found" | `trns_0003_001_js009` |
| `banking.transfer_history.status.failed` | "Failed" | `trns_0003_001_js010` |
| `banking.transfer_history.status.success` | "Success" | `trns_0003_001_js011` |

### 9.2 Typography + layout

- **Typography:** strictly the workspace [§ Global Typography](../../../../../../CLAUDE.md)
  tokens. Page title uses `text-page-title`; column headers `text-table-header`;
  amount cells `text-table-cell` with `tar` alignment; status badge inherits
  badge tokens. No inline `style={{ fontSize: ... }}` per
  [wecms-web § Naming convention](../../../../../../wecms-web/CLAUDE.md).
- **Layout:** Desktop-first (per memory `feedback_desktop_first_ui`). Mirror the
  Account Inquiry (VND) reference shell — page header, filter row
  (DateRangePicker + status radios), table + footer summary, pagination.
- **Footer summary:** show "Total (VND) — N Transaction(s) — totalVndAmount"
  per the legacy `tfoot` HTML (`trns_0003_001.ts:243-258`). USD totals are also
  returned by the endpoint (`total_usd_amount`, `total_usd_count`) — render a
  second footer row when `total_usd_count > 0` (the legacy code structure
  anticipates a USD row but the current footer template renders only the VND
  row — see §10 OQ-5).

### 9.3 Performance budget

- p95 < 500 ms for the list endpoint at PROD volumes (estimate: <50K rows per
  tenant per quarter; helper index makes the date-range filter cheap).
- No N+1 on bank-name or user-name resolution — batch in one query per request
  (see §4.4).

---

## 10. Open questions — RESOLVED 2026-05-20

All eight OQs resolved by the PO with spec-default selections in Prompt 3
(2026-05-20). Implementation matches these decisions verbatim.

| # | Resolution |
|---|---|
| OQ-1 | **Resolved — V114 funnel.** Use `LegacyFunctionRepository.resolveNapasOrCitadBankName(code, langCd)` for `in_bank_nm`. Implementation: [TransferHistoryQueryService.toRow:212](../../../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java). |
| OQ-2 | **Resolved — no period cap.** Bean Validation enforces `\d{8}` shape + `to_dt ≥ from_dt`; no `WBE_TRANSFER_HISTORY_PERIOD_TOO_LONG` code. |
| OQ-3 | **Resolved — ship the helper index in the same MR.** `V181__transfer_history_helper_index.sql` (Needs Confirmation — source file not found). |
| OQ-4 | **Resolved — no audit write.** Legacy-parity (matches `approval-line` / `erp-transaction-history` precedent). `AuditLogService` is intentionally NOT a dependency. |
| OQ-5 | **Resolved — VND footer only.** Frontend renders VND total per legacy `trns_0003_001.ts:243-258`. USD totals are still carried on the wire (`total_usd_amount` / `total_usd_count`) so a future Figma frame can surface a second footer row without a backend change. |
| OQ-6 | **Resolved — whitelist `{10,15,20,50,100}`, default 15.** Enforced by `TransferHistoryListRequest.PAGE_PER_CNT_PATTERN` + the controller's `@Pattern` guard. |
| OQ-7 | **Resolved — Excel export NOT in scope for this MR.** Slug `transfer-history` reserved in the frontend `SCREEN_IDS` registry; no `ExportRowProvider`, no `SYS_SCR_OPTION_WECMS` seed for export. |
| OQ-8 | **Resolved — omit settlement linkage from the wire.** `TRNS_STLM_ID` / `TRNS_STLM_YN` not on `TransferHistoryRow`. Settlement-view feature can add them when needed. |

---

## 11. Out of scope (this feature MR)

- The Coocon engine invocation itself (Internal Transfer Register port — a
  separate feature MR).
- Settlement-status update (`patchByTrnsStlmId` + `NAPAS_REMIT_TRNS_HSTR_U001`
  IDO) — out of scope; lives on the settlement / reconciliation feature.
- A row-detail dialog / `GET /…/{seq_no}` endpoint — the legacy page has no
  detail view.
- New columns or schema changes to `NAPAS_REMIT_TRNS_HSTR`.
- DEV / UAT / PROD deploy of the refactor (R&D scope — local + GitLab only,
  per memory `project_refactoring_scope` (Needs Confirmation)).
