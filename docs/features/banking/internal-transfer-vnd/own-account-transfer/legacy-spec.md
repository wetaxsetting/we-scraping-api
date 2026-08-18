# Banking > Own Account Transfer — Feature Spec (Refactor)

**Refactor slug:** `own-account-transfer`
**Refactor route:** `/banking/own-account-transfer`
**JEX counterpart:** `trns_0001_001` (cited only as functional reference — see [docs/jex/](../../../../../../docs/jex))
**Status:** Implemented (backend + frontend landed; Path A endpoints live). Composer credential-collection deferred (OQ-FE-1).
**Linked design docs:**
- Migration mapping: [../../migration/banking/own-account-transfer.md](../../../../references/migration/banking/own-account-transfer.md)
- Backend design:   [../../backend/banking/own-account-transfer-api.md](../../../../references/api-spec/banking/own-account-transfer-api.md)
- Frontend design:  [../../frontend/banking/own-account-transfer-page.md](../../../../../../wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/README.md)
- Database design:  [../../database/banking/own-account-transfer-db.md](../../../../references/database/banking/own-account-transfer-db.md)

> JEX trees (`WECMS DEV/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/`, `DEV ADMIN/`) are **read-only reference**. This spec describes the **refactor** behaviour; identical wording in `docs/jex/` describes the legacy live system. Same slug, two docs, never mixed.

---

## 1. Business purpose

Own Account Transfer lets a tenant move money between **two accounts they themselves own at the supported Vietnamese banks** — Shinhan VN, IBK VN, Vietcombank, VietinBank, Woori VN (and, for HAENGSUNG tenants, VietinBank only). The page itself is the **source-account picker + live-balance refresher**; the actual transfer composer is a layer popup (legacy `trns_0001_007`, future refactor slug `own-account-transfer-composer`) opened by clicking **Transfer** on a row.

The transfer is executed by the **NXiSAS Coocon engine** — a vendor-signed Windows process on the user's machine that scrapes the bank's IB site directly. Spring is not in the transfer's wire path; the page's only server role is **listing source accounts** and **persisting refreshed balances** the engine reports back.

## 2. Actors & roles

| Role | Source | What they can do here |
|---|---|---|
| Tenant ADMIN | `BUSI_ADMN_INFM` row exists | See every active VND saving / current account in the tenant. Always sees the **Transfer** button. |
| Tenant USER | no `BUSI_ADMN_INFM` row | Sees only accounts where `ACCT_AUTH_INFO` grants them `(USE_YN_V='Y' & AUTH_TYPE_V='V')` **or** `(USE_YN_W='Y' & AUTH_TYPE_W='W')`. **Transfer** button appears only on rows where they have a `W` grant (sender authority). |
| Unauthenticated | — | Not reachable — page is behind the `(dashboard)` route guard / JWT. |

Authority resolution mirrors the legacy condition at `trns_0001_001_r001_act.jsp:86-88` and `trns_0001_001_v001.ts:530-537`.

## 3. Preconditions

1. JWT valid; tenant resolved via `TenantContext.currentBizKey()`.
2. **Scope is strictly own → own within the same `biz_key`** (OQ-A, PO 2026-05-18). Both the source **and** the target account must be active VND rows in the caller's `ACCT_MSTR`. External NAPAS recipients (transfers to another company's account) are **out of scope** for this feature. Example: with `biz_key = 'VN_VNM0004_000001'` (CÔNG TY TNHH WEBCASH VIỆT NAM), transfers are allowed only between accounts belonging to that same company. Two flavours of own → own are supported: `SAME_BANK` (source and target at the same `BANK_CD`) and `NAPAS` (source and target at different banks within the same tenant, routed via NAPAS rails). FCY is deferred — re-open as a new feature when needed.
3. The tenant has at least one active VND **current (`03`)** or **saving (`05`)** account on a supported bank.
4. **HAENGSUNG tenants** (`PRDT_TYPE = 'H'`): only `BANK_CD = 11704001` (VietinBank) is listed, and saving accounts (`05/06/08`) are additionally filtered to `EXPI_DT >= today OR EXPI_DT IS NULL` AND `BAL > 0` — per legacy [`trns_0001_001_r001_act.jsp:74-77`](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0001_001_r001_act.jsp) (Redmine #42633).
5. The NXiSAS engine must be installed on the user's machine for the Transfer flow. The page itself loads even if the engine is missing — the install-check happens in `onload()` and the engine-missing UX is delegated to the shared `_v2nxisas.openNxisas` handler (see §7).

## 4. Screen flow (1:1 with JEX)

```
[App shell] → /banking/own-account-transfer
        │
        ▼
  ┌─────────────────────────────────────────────────────────┐
  │ Page title:   "Own Account Transfer"  (data-jex-ml      │
  │                                        trns_0001_001_m001)│
  │ Toolbar:      view-setting icon                         │
  │                                                         │
  │ Filter bar:   [All Banks ▼]  (bank popover, single-select)│
  │                                                         │
  │ Table  (TRANSFER_OWN_ACCOUNTS, dymAreaId=table1):       │
  │  ┌────┬───────────────┬───────────┬──────┬────────┬───────┬──────┐
  │  │BANK_CD│ACCT_NICK_NM │ BAL       │AVI_BAL│ACCT_BAL_LST_STS│ETC  │
  │  │       │             │           │       │ + last refresh │     │
  │  ├────┴───────────────┴───────────┴──────┴────────────────┴──────┤
  │  │ Shinhan VN  …  ACCT 1234   123,456,789 VND   …   [Transfer]   │
  │  └────────────────────────────────────────────────────────────────┘
  │ Footer:  Total #{CNT} Accounts  |  Σ BAL  |  Σ AVI_BAL           │
  │ Pagination: 15 / 20 / 50 / 100                                  │
  └─────────────────────────────────────────────────────────────────┘
        │
        ├── click [Transfer] on row ──▶ open <OwnAccountTransferComposer> layer popup
        │                                  (port of trns_0001_007 — out of THIS feature's scope;
        │                                   tracked as a sibling refactor task)
        │
        └── view-setting icon ──▶ shared <ViewSettingPopover screenId="own-account-transfer">
                                  (global registry, no per-page server code)
```

Click + onload behaviour (1:1 with `trns_0001_001_v001.ts:294-340`):

1. **On mount** — render shell + table skeleton, call `_v2nxisas.openNxisas(...)` to probe the local engine. Engine state is held in `trns_0001.loadedNxisas` (mirrored as a React ref in the refactor); if not loaded, the legacy code retries once on the next interaction. No blocking modal — the page is browsable without the engine until **Transfer** is pressed.
2. **Initial fetch** — call `GET /api/v1/banking/own-account-transfer/source-accounts?bank_cd=` (no bank filter), populate table + footer + bank popover.
3. **Bank-popover click** — re-fetch with `bank_cd=<selected>` and re-render. **HAENGSUNG forces `bank_cd=11704001`** before any user input (legacy `trns_0001_001_v001.ts:390-392`).
4. **Sort header click** — append the sort segment to the request via the shared `useTableSort()` hook (refactor convention; replaces legacy `paramSort` URL suffix).
5. **Transfer button click** — open the layer popup, passing the row's `bank_cd` + `acct_no` via the in-app modal stack (legacy uses `sessionStorage` keys `SELECTED_BANK` / `SELECTED_ACCOUNT`; refactor uses the props the layer popup component declares — no `sessionStorage`).
6. **Composer → engine completes** — composer posts the engine result back to `POST /api/v1/banking/own-account-transfer/balance-refresh` which writes the refreshed balance into `ACCT_MSTR`. The list page subscribes to the composer's `onSuccess` and refetches its rows.

## 5. Field-by-field spec — list endpoint

Wire fields are **lowercase snake_case** per the [CTO directive 2026-05-08](../../../../../CLAUDE.md#api-naming--serialization-standards). Java DTO fields stay camelCase; the global Jackson `SNAKE_CASE` strategy handles the conversion.

Source: `WECMS_ETC/xml/service/WSVC/WSVC.trns_0001_001_r001.xml` + `_v001.ts:474-547`.

### 5.1 Request — `OwnAccountSourceQuery`

| Wire (snake) | Java | Type | Validation | Notes |
|---|---|---|---|---|
| `bank_cd` | `bankCd` | `String` | optional, `@Pattern("\\d{8}")` | Empty/unsupported → backend treats as "all supported banks". Mirrors `trns_0001_001_r001_act.jsp:159-162`. |
| `sort_field` | `sortField` | `String` | whitelist | One of `bank`, `acct_nick_nm`, `bal`, `avi_bal`, `acct_bal_lst_sts`. Defaults to legacy ordering (`bank_cd`, `acct_no`). |
| `sort_direction` | `sortDirection` | `String` | `ASC`/`DESC` | Defaults to `ASC`. |

No pagination — the legacy list is "all" per filter (the `PAGINATION` dropdown on the legacy page is **client-side** only; same behaviour preserved in the refactor for parity).

### 5.2 Response — `OwnAccountSourceListResponse`

| Wire (snake) | Java | Type | Source | Notes |
|---|---|---|---|---|
| `total_count` | `totalCount` | `Long` | `ACCT_MSTR_R002_CNT.TOT_CNT` | Used in footer ("Total #{CNT} Accounts"). |
| `tot_bal` | `totBal` | `BigDecimal` | `ACCT_MSTR_R002_CNT.TOT_BAL` | Footer Σ. Currency = local (`VND`). |
| `tot_avi_bal` | `totAviBal` | `BigDecimal` | `ACCT_MSTR_R002_CNT.TOT_AVI_BAL` | Footer Σ. |
| `list` | `list` | `OwnAccountSourceRow[]` | `ACCT_MSTR_R015` ordered by sort | Renamed from legacy `REC` (refactor convention). |

### 5.3 `OwnAccountSourceRow` (per legacy `WSVC.trns_0001_001_r001.xml:42-63`)

| Wire | Java | Type | Source / formula |
|---|---|---|---|
| `bank_cd` | `bankCd` | `String(20)` | `ACCT_MSTR.BANK_CD` |
| `bank_nm` | `bankNm` | `String(2000)` | `wb_get_bank_nm(BANK_CD,'s')` — port to a Spring `BankCdResolver` (see backend §4). |
| `bank_nm2` | `bankNm2` | `String(2000)` | `<bank_nm>` + ' ' + `acct_no` + ' (' + `ccy_cd` + ')` — convenience for the bank popover. |
| `acct_no` | `acctNo` | `String(100)` | `ACCT_MSTR.ACCT_NO` |
| `acct_nick_nm` | `acctNickNm` | `String(50)` | `wb_format_acct_nick_nm(...)` — display name; falls back to `BANK_NM | ACCT_NO`. |
| `bal` | `bal` | `BigDecimal` | `ACCT_MSTR.BAL` |
| `real_amt` | `realAmt` | `BigDecimal` | `ACCT_MSTR.REAL_AMT` — "Available balance". Frontend renders as `avi_bal` column. |
| `ccy_cd` | `ccyCd` | `String(3)` | Always `VND` for this feature. |
| `acct_type_cd` | `acctTypeCd` | `String(2)` | `03` current, `05` saving. |
| `acct_bal_inq_stts` | `acctBalInqStts` | `String(20)` | Last balance-refresh result code — `00000000` = success. |
| `acct_bal_inq_msg` | `acctBalInqMsg` | `String(255)` | Last balance-refresh error message. |
| `acct_bal_upd_dtm` | `acctBalUpdDtm` | `String(14)` | Last time `BAL` changed (utc yyyymmddhhmiss). |
| `acct_bal_lst_inq_dtm` | `acctBalLstInqDtm` | `String(14)` | Last time refresh ran (success or fail). |
| `bank_cert_seq` | `bankCertSeq` | `String(10)` | `ACCT_MSTR.BANK_CERT_SEQ` — required by engine login. |
| `use_yn_v` | `useYnV` | `String(1)` | From the `ACCT_AUTH_INFO` pivot in `ACCT_MSTR_R015`. |
| `use_yn_w` | `useYnW` | `String(1)` | Same pivot. |
| `auth_type_v` | `authTypeV` | `String(1)` | `'V'` when granted, null otherwise. |
| `auth_type_w` | `authTypeW` | `String(1)` | `'W'` when granted, null otherwise. |

**Not exposed on the wire** (legacy carried them in `ACCT_MSTR_R015`, refactor strips them as out-of-scope):
`TRNS_PWD`, `ACCT_PWD`, `NXISAS_ACCT_TYPE`, `LST_LGN_STTS*`, `TRN_LST_SUC_DTM`, `VAS_ACCT_SEQ_NO`, `CONT_RT`, `PYAT_*`, `LOAN_NM`, `AGMT_AMT`, `HAND_REG_YN`, `ACCT_HIS_*`. The transfer composer pulls them in its own DTO when it needs them — not this page.

## 6. Validation rules (copied from JEX — not invented)

- **List endpoint** — only `bank_cd` is optional input; no other validation in legacy [`trns_0001_001_r001_act.jsp`](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0001_001_r001_act.jsp).
- **Balance-refresh endpoint** (legacy `trns_0001_001_u001`) — per [`trns_0001_001_u001_act.jsp:28-41`](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0001_001_u001_act.jsp):
  - `bank_cd` **required**
  - `acct_no` **required**
  - `acct_bal_inq_stts` **required**
  - `bal`, when present, must be `>= 0` (`ValidationHelper.validateAmountPositiveOrZero`)
  - `real_amt`, when present, must be `>= 0`
  - The composite `(biz_key, bank_cd, acct_no)` must resolve to a real `ACCT_MSTR` row (legacy `ValidationHelper.validateAcctMstr`).
- **Recipient-bank lookup** (`trns_0001_001_r002`) — `bank_cd` required when `from_trco_yn != 'Y'`; otherwise no input (port verbatim to the composer module's spec, not this feature).
- **NAPAS bank info** (`trns_0001_001_r003`) — `napas_bank_cd` required (also composer-scope).

The refactor **MUST NOT** invent additional rules (no min-amount, no day-limit, no whitelist beyond what JEX already enforces). Bean-validation translation goes into the Spring records in the backend doc §3.

## 7. Engine call chain (Coocon NXiSAS, browser-side)

The browser bundle owns the entire transfer wire path; Spring is **not** invoked between "click Transfer" and "engine reports back". The bundle is loaded once per page from `wecms-web/public/nxisas/` (vendored verbatim).

```
┌──────────────────────────────────────────────────────────────────┐
│ Next.js page  /banking/own-account-transfer                      │
│   (React, no jQuery in app code; bundle's jQuery lives on window) │
└───────────────┬──────────────────────────────────────────────────┘
                │ user clicks [Transfer] on a row
                ▼
┌──────────────────────────────────────────────────────────────────┐
│ <OwnAccountTransferComposer> layer popup                         │
│   composes the transfer JSON (sender BANK_CD/ACCT_NO,            │
│   recipient NAPAS_BANK_CD/ACCT_NO/AMT/REMARK/...)                │
└───────────────┬──────────────────────────────────────────────────┘
                │ trns_0001.runScraping(dataArray, "TRANSFER", cb)
                ▼
┌──────────────────────────────────────────────────────────────────┐
│ nxisas_v2.js   (loaded for this page only; load-order critical)  │
│   _v2nxisas.callNxisas / callNxisasWithoutOpenProcess            │
└───────────────┬──────────────────────────────────────────────────┘
                │ uses CooconiSASNX.open / .execute
                ▼
┌──────────────────────────────────────────────────────────────────┐
│ isasscaping.js  (vendor JS that talks the engine wire protocol)  │
└───────────────┬──────────────────────────────────────────────────┘
                │ chooses transport
                ▼
┌───────────────────────┐         ┌─────────────────────────────────┐
│ web_socket.js         │   or    │ swfobject.js  + websocket.swf   │
│ (preferred, modern)   │         │ (Flash fallback, legacy only)   │
└───────────┬───────────┘         └────────────────┬────────────────┘
            │ localhost WebSocket :tcp port owned by NXiSAS         │
            └────────────────┬──────────────────────────────────────┘
                             ▼
                ┌─────────────────────────────────┐
                │  NXiSAS.exe  (Windows process,  │
                │   vendor-signed, talks to the   │
                │   bank's IB site directly)      │
                └─────────────────────────────────┘
                             │
                             ▼  bank dialog (OTP / password / Q&A)
                ┌─────────────────────────────────┐
                │  Bank IB site  (Shinhan / IBK / │
                │  Vietcombank / Vietin / Woori)  │
                └─────────────────────────────────┘
```

**Spring's role is post-scrape, not in-scrape.** After the engine reports completion (success or fail), the composer calls the **balance-refresh** endpoint described in §5.4 of the backend doc, which:

- updates `ACCT_MSTR.ACCT_BAL_INQ_STTS` / `ACCT_BAL_INQ_MSG` / `ACCT_BAL_LST_INQ_DTM` regardless of outcome (mirrors legacy `_u001_act.jsp:88-100`);
- on `ACCT_BAL_INQ_STTS = '00000000'`, also patches `BAL` / `REAL_AMT` / `CORC_BF_BAL` / `ACCT_BAL_UPD_DTM` (mirrors `_u001_act.jsp:107-130`);
- emits an audit-log row with `WSVC_ID = "own-account-transfer"` (slug, **not** `trns_0001_001` — per workspace [CLAUDE.md 2026-05-09 directive](../../../../../../CLAUDE.md#cross-tree-rules)).

The refactor does **NOT** replicate the engine flow on the server. There is no `nxisas_*` Java client invoked from this feature, no `NxisasGatewayClient` call, no `BankScrapingService.start(...)`. Those exist in `wecms-api` for **other** scraping flows (Account Inquiry refresh, Firm Banking) that use the Webcash NXISAS smart-gateway HTTP API — a different system from the local `NXiSAS.exe` bundle Own Account Transfer requires.

## 8. Permission / role matrix

| UI element | ADMIN | USER with `W` grant | USER with `V` grant only | USER with no grant |
|---|---|---|---|---|
| Row visible in table | yes | yes | yes | row not returned |
| **Transfer** button on row | shown | shown | hidden | row not returned |
| View-setting icon | shown | shown | shown | n/a |
| Sort headers | enabled | enabled | enabled | n/a |
| Bank popover | enabled | enabled | enabled | n/a |

Resolution lives **server-side** (the row's `use_yn_*` / `auth_type_*` fields drive client-side render, but the row itself is only emitted when the legacy WHERE-clause condition holds). The client must NOT trust the absence of the **Transfer** button as a security boundary — the balance-refresh endpoint independently re-validates ownership via `ACCT_AUTH_INFO`.

## 9. i18n key list

Prefix `own_account_transfer.*` (snake_case, dot-separated, per `wecms-web/locales/{en,ko,vi}.json` convention). Legacy JEX ML codes are listed in the migration doc — the refactor invents fresh keys (CTO 2026-05-08 retires JEX ML ids on the refactor side).

| Refactor key | English | Legacy JEX ML | Used in |
|---|---|---|---|
| `own_account_transfer.title` | Own Account Transfer | `trns_0001_001_m001` | page header |
| `own_account_transfer.filter.all_banks` | All Banks | `trns_0001_001_js002` / `_js003` | bank popover default |
| `own_account_transfer.column.bank` | Bank | (View Setting `FIELD_ID=BANK`) | table header |
| `own_account_transfer.column.acct_nick_nm` | Account | (View Setting) | table header |
| `own_account_transfer.column.bal` | Balance | (View Setting `FIELD_ID=BAL`) | table header |
| `own_account_transfer.column.avi_bal` | Available Balance | (View Setting `FIELD_ID=AVI_BAL`) | table header |
| `own_account_transfer.column.acct_bal_lst_sts` | Last Refresh | (View Setting) | table header |
| `own_account_transfer.column.etc` | Action | (View Setting `FIELD_ID=ETC`) | table header |
| `own_account_transfer.row.transfer` | Transfer | `trns_0001_001_js099` | row action button |
| `own_account_transfer.status.success` | Success | `trns_0001_001_js005555` | row status pill |
| `own_account_transfer.status.failed` | Failed | `trns_0001_001_js006666` | row status pill |
| `own_account_transfer.footer.total_accounts` | Total {count} Accounts | `trns_0001_001_js001` | table footer |
| `own_account_transfer.empty.no_data` | No data found | (hard-coded in legacy) | empty state |
| `own_account_transfer.engine.sender_not_supported` | The sender account cannot be transferred. Please use another sender account. | `trns_0001_001_js80002411` | engine error code `80002411` |
| `own_account_transfer.engine.install_required` | The NXiSAS scraping engine is not installed. Click here to install. | `_non_installed_message` | engine missing CTA |

Bank labels, currency labels, and generic action icons reuse the existing global keys (`bank.shinhan_vn`, `bank.vietin_vn`, `currency.vnd`, `action.export_excel`, etc.) — **do not duplicate** them under `own_account_transfer.*`.

## 10. Sequence diagram — list → transfer → refresh

```
User    Next.js page         Spring API                  Browser nxisas bundle      NXiSAS.exe        Bank IB site
 │           │                    │                                │                       │                 │
 │ open      │                    │                                │                       │                 │
 │──────────▶│                    │                                │                       │                 │
 │           │ mount, openNxisas()                                  │                       │                 │
 │           │────────────────────────────────────────────────────▶│ probe localhost socket│                 │
 │           │◀───────────────────────────────────────────────────│ loaded=true/false       │                 │
 │           │ GET /source-accounts│                                │                       │                 │
 │           │───────────────────▶│ select ACCT_MSTR + auth pivot │                       │                 │
 │           │◀───────────────────│ rows + totals                   │                       │                 │
 │ click row │                    │                                │                       │                 │
 │──────────▶│ open composer popup                                  │                       │                 │
 │           │ user fills recipient + amount + remark…              │                       │                 │
 │           │ click "Transfer"                                     │                       │                 │
 │           │────────────────────────────────────────────────────▶│ runScraping("TRANSFER")│                 │
 │           │                    │                                │ open thread / login   │                 │
 │           │                    │                                │──────────────────────▶│ open()           │
 │           │                    │                                │                       │ login to bank   │
 │           │                    │                                │                       │────────────────▶│
 │           │                    │                                │                       │ OTP/QnA/Captcha │
 │           │                    │                                │ render 2nd-cert modal │◀────────────────│
 │           │ user supplies OTP  │                                │                       │                 │
 │           │────────────────────────────────────────────────────▶│ submit OTP            │                 │
 │           │                    │                                │──────────────────────▶│ post transfer   │
 │           │                    │                                │                       │────────────────▶│
 │           │                    │                                │                       │◀────────────────│ TRNS_OK / ERR
 │           │                    │                                │◀──────────────────────│ result          │
 │           │◀───────────────────────────────────────────────────│ callback(result)        │                 │
 │           │ POST /balance-refresh (bank_cd, acct_no,            │                       │                 │
 │           │      bal, real_amt, acct_bal_inq_stts, msg)         │                       │                 │
 │           │───────────────────▶│ patch ACCT_MSTR, audit-log    │                       │                 │
 │           │◀───────────────────│ refreshed row                  │                       │                 │
 │           │ refetch source-accounts → re-render                 │                       │                 │
 │           │ close composer                                       │                       │                 │
```

The diagram intentionally omits the multi-thread / multi-stage queue inside `nxisas_v2.js` (login / 2nd-cert / transfer stages, parallel threading, retry logic) — those are vendor internals and stay encapsulated.

## 10a. Audit logging — every related action

Per OQ-4 (resolved 2026-05-18 by PO Mr. Jung), this feature **diverges from** the refactor's "only-on-writes" audit precedent. Every endpoint and every engine handshake writes an `AUDIT_LOGS_INFM` row with `WSVC_ID = "own-account-transfer"`. The divergence is per-feature, driven by the legacy `mntLogYn=Y` flag and compliance expectations around bank scraping.

| When | Audit action (refactor convention) | Payload in `REQ_DATA` (JSON, snake_case) |
|---|---|---|
| `GET /source-accounts` returns | `LIST_SOURCE_ACCOUNTS` | `{ bank_cd?, sort_field?, sort_direction?, result_count, prdt_type }` |
| `GET /recipient-banks` returns (composer-scope) | `LIST_RECIPIENT_BANKS` | `{ bank_cd, keyword?, from_trco_yn?, result_count }` |
| `GET /napas-bank-info` returns (composer-scope) | `LOOKUP_NAPAS_BANK_INFO` | `{ napas_bank_cd, result_count }` |
| `POST /balance-refresh` succeeds **or** fails | `BALANCE_REFRESH` | `{ bank_cd, acct_no, ccy_cd, prev_bal, new_bal, prev_real_amt, new_real_amt, acct_bal_inq_stts, acct_bal_inq_msg, prdt_type }` |
| `POST /engine-connection-check` (new endpoint) | `ENGINE_CONNECTION_CHECK` | `{ result, engine_vendor, engine_version, user_agent, os_platform, scrape_session_id?, error_code?, error_message? }` |

**Engine connection-check action.** Triggered at page mount when the browser bundle's `_v2nxisas.openNxisas(...)` returns. The frontend POSTs the outcome to `POST /api/v1/banking/own-account-transfer/engine-connection-check` with the engine's vendor + version string (e.g. `engine_vendor="COCON"`, `engine_version="NXSAS 2025.03.11.0"`). Spring writes one audit row per check and returns `204 No Content` (no domain effect — the row is the only artifact). This gives compliance a record of: who opened the page, when, on which OS, with which engine version, and whether the local engine answered.

The full audit-row schema (which audit columns map to which feature concepts) lives in [backend doc §8](../../../../references/api-spec/banking/own-account-transfer-api.md#8-audit-logging) and the table-level constraints + reuse rule live in [database doc §2](../../../../references/database/banking/own-account-transfer-db.md#2-tables-written).

## 11. Out of scope (handled by sibling refactor tasks)

- **`own-account-transfer-composer`** — the `trns_0001_007` layer popup. Owns the recipient picker (`r002` lookup), NAPAS-info lookup (`r003`), transfer-password input, fee selection, remark, and engine job composition. Will get its own feature spec in the same parent folder.
- **`internal-transfer`** — the existing scaffolded `/banking/transfer-own-accounts` route. The current refactor scaffolding under that slug is mock-data only; this MR proposes **renaming** the route + folder to `/banking/own-account-transfer` to match the WSVC slug rule. See migration doc §6.
- **Webcash NXISAS smart-gateway** — `domain/banking/nxisas/BankScrapingService` + `NxisasGatewayClient` serve other features (Account Inquiry refresh, Firm Banking History scraping). They are **not** in this feature's wire path and stay untouched here.

## 12. Open questions

| ID | Topic | Owner | Default if unanswered |
|---|---|---|---|
| ~~OQ-1~~ | ~~Should the refactor merge the legacy `_v001.ts` + `_001.ts` duplicates?~~ **Resolved 2026-05-18 (Sonny):** `_v001.ts` is the active file referenced by the JSP (`<script src="trns_0001_001_v001.js">`) and is the source of truth for the refactor. Ignore `_001.ts` unless a specific dependency is discovered later. | Sonny | — |
| ~~OQ-2~~ | ~~Pagination — keep client-side, or move to a real `page` + `size` server contract?~~ **Resolved 2026-05-18 (PO):** Keep **client-side pagination**. Server returns all rows for the selected bank filter; frontend slices locally. Page-size options follow the **project standard `PAGE_SIZE_OPTIONS = [10, 20, 50, 100]`** from [`Pagination.tsx`](../../../../../../wecms-web/components/shared/Pagination.tsx) — superseded by OQ-FE-3 (PO 2026-05-18); the earlier legacy-parity 15/20/50/100 reading is retired in favour of the cross-page standard. Confirms backend doc §3.1 (no pagination params on the wire) and frontend doc §2. | PO (Mr. Jung) | — |
| ~~OQ-3~~ | ~~`bank_cd` filter — parity-coerce, or 400-reject?~~ **Resolved 2026-05-18 (Sonny):** Keep legacy behaviour — **coerce unknown/unsupported `bank_cd` values to "all"** instead of rejecting. Mirrors `_r001_act.jsp:160-162` exactly and avoids any client-visible behaviour change. Confirms backend doc §4 step 3 ("Coerce q.bankCd: if !allowed → null"). | Sonny | — |
| ~~OQ-4~~ | ~~Audit-log row for the **list** endpoint?~~ **Resolved 2026-05-18 (PO):** **Audit every related action**, not only writes. List/read endpoints, balance-refresh, and the NXiSAS engine connection-check **all** write `AUDIT_LOGS_INFM` rows with `WSVC_ID="own-account-transfer"`. This feature **diverges from** the refactor's "only-on-writes" precedent (chart-of-accounts / contacts / firm-banking-settings) — the divergence is per-feature, driven by the legacy `mntLogYn=Y` flag and compliance expectations around bank scraping. New audit action: **engine connection-check**, recording engine vendor + version (e.g. `NXSAS 2025.03.11.0 / COCON`). See feature §11, backend doc §8, migration §1, frontend doc §5.3, database doc §2. | PO (Mr. Jung) | — |
| ~~OQ-A~~ | ~~Amount tolerance for non-VND.~~ **Resolved 2026-05-18 (PO):** OAT scope is **VND-only AND same-`biz_key` only**. Both the source and the target account must be active VND rows in the caller's own `ACCT_MSTR` (see §3 precondition 2). External NAPAS recipients are out of scope. Two flavours remain: `SAME_BANK` (own → own at same bank) and `NAPAS` (own → own at different banks, routed via NAPAS). FCY is deferred — re-open as a new feature if/when needed; per-currency tolerance ships with that future work. | PO (Mr. Jung) | — |
| ~~OQ-B~~ | ~~Engine vendor/version trust — server-side allow-list?~~ **Resolved 2026-05-18 (PO):** `engine_vendor` and `engine_version` are **audit / reference data only**. Stored verbatim in the audit row for tracking + debugging; never used as a security signal, never gated against an allow-list. If version restriction is needed later it ships as a separate security requirement. Matches what the backend already does. | PO (Mr. Jung) | — |
| ~~OQ-C~~ | ~~`target-accounts` shape — limit to the 5-bank allow-list, or return every `NAPAS_BANK_INFO` row?~~ **Resolved 2026-05-18 (PO):** Limit to the 5 supported banks (IBK VN, Shinhan VN, Vietcombank, VietinBank, Woori VN — i.e. `ALLOWED_BANK_CDS_DEFAULT`) **and** to the tenant's own VND accounts. The endpoint must never dump the global `NAPAS_BANK_INFO` dictionary. After OQ-A, this is already enforced as a side effect — `listTargetAccounts` returns only own VND `ACCT_MSTR` rows filtered through `allowedBankCds`. NAPAS dictionary is consulted only for per-row routing enrichment (`napas_bank_cd`), never to expose unrelated rows. | PO (Mr. Jung) | — |
| ~~OQ-D~~ | ~~`transfer-history` filter scope — all `NAPAS_REMIT_TRNS_HSTR` rows or only OAT-written rows?~~ **Resolved 2026-05-18 (PO):** Scope to records **this feature wrote** — predicate `OAT_IDEMPOTENCY_KEY IS NOT NULL`. Excludes internal-transfer dispatch, legacy imports, and any future NAPAS-remit writer that doesn't tag its rows with an OAT idempotency key. Repository methods renamed to `findByBizKeyAndOatIdempotencyKeyIsNotNull*OrderByRegDtmDesc(...)`. | PO (Mr. Jung) | — |
| ~~OQ-FE-1~~ | ~~Engine `Input` bag — generic shape or per-bank tuning?~~ **Resolved 2026-05-18 (PO):** Generic structure is fine for the page-level envelope, but the per-bank quirks must match the legacy [`trns_0001_007.ts:1916-2115`](../../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_007.ts) verbatim. Refactor frontend ships [`bank-profiles.ts`](../../../../../../wecms-web/components/banking/own-account-transfer/bank-profiles.ts) — a declarative table of the 5 supported banks' job names + Input fields (`acctNumPWD`, `remitBranchCode`, `remitBranchName`, `remitPurpose`, `memo/memo2`, …) plus the VIETIN post-tweak (`delete _input_bal.transType` / `_input_bal.feePaymentType`) and Woori VN's `nonLogin=false` override. Phase-2 gap: credential fields the legacy popup collects via secondary modals (`acctNumPWD`, `transPwd`, `acceptName`) ship as null today — composer collection ships in a separate UX MR before PROD rollout. The structure is engine-compatible; only value collection is deferred. | PO (Mr. Jung) | — |
| ~~OQ-FE-2~~ | ~~Engine result parser shape — single canonical field or priority-list fallback?~~ **Resolved 2026-05-18 (PO):** Keep the **priority-list fallback** in [`parseEngineOutcome`](../../../../../../wecms-web/components/banking/own-account-transfer/OwnAccountTransferComposerDialog.tsx). The current priority order — `bankTxId` → `transactionId` → `tranNo` → `TraceNo` for the bank-side tx id, `amount` → `balance` for the settled value — mirrors the legacy [`trns_0001_007.ts:1634-1655`](../../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_007.ts) walk. Validation against real engine responses (especially **VietinBank**) is **deferred** to Phase-2 alongside the credential-collection composer work. If a new bank returns the tx id under a fifth field name the parser will surface as a parse miss — the scraping-result POST never fires and the user sees the generic engine-failed dialog, which is a safe degradation. | PO (Mr. Jung) | — |
| ~~OQ-FE-3~~ | ~~Page size — legacy 15/20/50/100 or project standard?~~ **Resolved 2026-05-18 (PO):** Follow the **project standard** `PAGE_SIZE_OPTIONS = [10, 20, 50, 100]` from [`Pagination.tsx`](../../../../../../wecms-web/components/shared/Pagination.tsx). No per-page hardcoded list. Already in effect — the OAT page consumes the shared `usePagination` hook which defaults to `PAGE_SIZE_OPTIONS[0] = 10`; no code change required. This also retires the legacy-parity reading from OQ-2. | PO (Mr. Jung) | — |
| ~~OQ-FE-4~~ | ~~Legacy `transfer-own-accounts` route + components — delete in this MR?~~ **Resolved 2026-05-18 (PO):** **Leave in place.** The legacy route at [`app/(dashboard)/banking/transfer-own-accounts/`](<../../../../../../wecms-web/app/(dashboard)/banking/transfer-own-accounts/>) and the `components/banking/internal-transfer/TransferOwnAccounts*` + `TransferDialog.tsx` + `mockData.ts` files stay as-is for this MR. The sidebar now points at `/banking/own-account-transfer` so the old route is unlinked from the UI; the dead-code cleanup ships in a **separate follow-up MR** after the new feature is stabilized and verified end-to-end (including the Phase-2 engine round-trip from OQ-FE-1 / OQ-FE-2). | PO (Mr. Jung) | — |
| ~~OQ-FE-5~~ | ~~Translations — add KO + VI now, or rely on EN fallback?~~ **Resolved 2026-05-18 (PO):** **Add all three locale files now** since the project officially supports EN / KO / VI. All 45 new `own_account_transfer.*` keys + `common.try_again` + `sidebar.banking.ownAccountTransfer` are now translated in [`locales/en.json`](../../../../../../wecms-web/locales/en.json), [`locales/ko.json`](../../../../../../wecms-web/locales/ko.json), and [`locales/vi.json`](../../../../../../wecms-web/locales/vi.json). KO follows the legacy `transfer_own.*` vocabulary verbatim (`내 계좌 이체` / `사용 가능 잔액` / `다시 시도` / etc.); VI follows the legacy `transfer_own.*` vocabulary verbatim (`Chuyển khoản nội bộ` / `Số dư khả dụng` / `Thử lại` / etc.). KM is intentionally not added — `wecms-web` does not yet ship Khmer (see [wecms-web/CLAUDE.md §i18n Rules](../../../../../../wecms-web/CLAUDE.md#i18n-rules)). | PO (Mr. Jung) | — |
| ~~OQ-FE-6~~ | ~~Pre-existing ESLint errors (26 errors in unrelated files) — fix in this MR or punt?~~ **Resolved 2026-05-18 (PO):** **Leave in place.** The 26 errors are scoped to unrelated files (`LocaleProvider.tsx:77` `react-hooks/set-state-in-effect`, `selectBankBanks.ts` unused-vars, `PasswordConfirmationDialog.tsx` `<img>` warnings, `IntegratedDateRangeFilter.tsx` set-state-in-effect, `public/assets/settings/_write_icons.js` `no-require-imports`, etc.) and predate this MR. They are NOT introduced by the Own Account Transfer feature; fixing them would expand scope into unrelated files. A **separate follow-up MR / issue** can address the lint cleanup if/when prioritised — this MR ships green for OAT files and red-only-on-pre-existing-noise. | PO (Mr. Jung) | — |
