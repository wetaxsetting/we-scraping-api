# Account Inquiry (FCY) — Legacy ↔ Refactor Parity Audit

- **Feature:** Account Inquiry (FCY) — foreign-currency deposit account list
- **Legacy (JEX):** `fnsh_0005_001` / `fnsh_0005_001_r001` (view: `fnsh_0005_001_view_osmc.jsp`, script: `fnsh_0005_001_api_osmc.ts`)
- **Refactor route:** `/banking/account-inquiry-fcy` → `AccountController` / `AccountService` / `AcctMstrRepository`
- **System tag:** Refactor
- **Date:** 2026-06-06
- **Method:** Source-code verified. Documentation used for navigation only; every conclusion below cites `file:line` evidence read directly from source. Where a fact could not be source-verified it is marked `NOT VERIFIED`.

---

## Executive Summary

**Status: PASS WITH DEVIATIONS** → **read path PASS** after the 2026-06-06 fixes below.

> **Resolution update (2026-06-06).** The two weighted divergences are dispositioned:
> - **FCY-A01 → CLOSED (PO Approved Deviation).** PO confirmed multi-currency FCY (`ccy_cd<>'VND'`) is the intended modernization; legacy USD-only scope is intentionally retired. No code change.
> - **FCY-A02 → CLOSED (Implemented).** "Last Update" now sources `ACCT_HIS_LST_INQ_DT` (online accounts) with the legacy FD-only fallback to `ACCT_BAL_LST_INQ_DTM` — `AccountService.resolveLastUpdate(...)` + `toRow`, `AccountSortField.LAST_UPDATE → acctHisLstInqDt`, export inherits via the DTO, frontend wire/grid unchanged. Unit-tested + compiles green.
>
> FCY-A03 (server-side scraping not validated vs live banks) stays scope-deferred per R&D rules. Final verdict updated below.
>
> **Correction (2026-06-06, post-source-reverification).** The earlier **FCY-G02 "FALSE POSITIVE" finding below is RETRACTED — it was wrong.** A direct re-read of legacy source proved historical/as-of-date balance is **real and functional**: FE always sends `START_DATE` (`fnsh_0005_001_api_osmc.ts:606-615`), `FnshHelper.fnsh_0001_001_r001_readList:246` binds it into the IDO, and `IDO.ACCT_MSTR_R004.xml:90-217` reconstructs as-of-date `BAL`/`REAL_AMT` from `ACCT_TRNS_HSTR` — **including** the Vietcombank `11704004` special case (`:96-98,140-142,182-184`). §3 below inspected only the dynamic WHERE-clause builder and missed that `:START_DATE` is consumed in the SELECT projection. Reclassified as **OQ-FCY-2** and **CLOSED — PO Approved Deviation (Option B, 2026-06-06):** refactor ships snapshot-only balances; full legacy parity (Option A) deferred to a post-SIT MR gated on realistic seeded transaction history (R&D seed ≤30 `ACCT_TRNS_HSTR` rows can't validate it). The refactor date control is retained because it scopes the per-account balance/transaction popup (`AccountInquiryPage.tsx:1301`), not the list balances.

The refactor reimplements the FCY account list as a **prop-variant of the shared VND Account Inquiry screen** (`currency="FCY"`, `multiCurrencyMode`). The core read path — list, per-currency summary cards, grid, sort, pagination, keyword/bank filters, tenant + per-user account-authority scoping, Excel export — is present full-stack and is functionally close to legacy.

Three real divergences carry weight:

1. **FCY currency scope (HIGH).** Legacy FCY is hard-locked to `CCY_CD = 'USD'`; the refactor returns **all non-VND currencies** (`ccy_cd <> 'VND'`, incl. KRW). The two screens return different row sets for any tenant holding non-USD foreign accounts.
2. **"Last Update" source column (MEDIUM).** Legacy binds `ACCT_HIS_LST_INQ_DT` (transaction-history last-inquiry date) for online accounts; the refactor always binds `ACCT_BAL_LST_INQ_DTM` (balance last-inquiry datetime). Different timestamps can render.
3. **Update-Balance scraping topology (MEDIUM, by-design).** Legacy scrapes via the **client-side NXiSAS engine**; the refactor scrapes **server-side** via a real `RestClient` to the Coocon smart-gateway. Same Coocon backend, different topology; not validated against live banks under R&D scope.

~~The headline doc-flagged item **FCY-G02 "Date-aware balance support" is a FALSE POSITIVE** — neither legacy nor refactor queries historical/as-of-date balances; both render the live `ACCT_MSTR` snapshot (evidence in §3).~~ **RETRACTED (2026-06-06) — see the Correction note above.** Legacy *does* perform as-of-date historical-balance inquiry (incl. Vietcombank `11704004`); only the **refactor** renders the live snapshot. This is a real legacy feature the refactor omits, now **CLOSED as OQ-FCY-2 PO Approved Deviation (Option B)** — not a false positive.

---

## Gap Table

| Gap ID | Title | Severity | Status |
|---|---|---|---|
| FCY-A01 | FCY currency scope: legacy `CCY_CD='USD'` vs refactor `ccy_cd<>'VND'` (+ KRW type codes) | HIGH | **CLOSED** — PO Approved Deviation (2026-06-06) |
| FCY-A02 | "Last Update" source column differs (`ACCT_HIS_LST_INQ_DT` vs `ACCT_BAL_LST_INQ_DTM`) | MEDIUM | **CLOSED** — Implemented (2026-06-06) |
| FCY-A03 | Update-Balance scraping topology: client-side NXiSAS engine vs server-side Coocon `RestClient`; not validated vs live banks | MEDIUM | OPEN (scope-deferred) |
| FCY-A04 | PrdtType `H` (Haengsung) maturity/balance filter on summary cards not ported | MEDIUM | OPEN |
| FCY-A05 | ">72h stale = red" Last-Update indicator not reproduced (refactor reds only on inquiry-status error) | MEDIUM | OPEN |
| FCY-A06 | Frontend has no role/permission gating on Update / Excel buttons (legacy hides `.scraping` for non-admin) | MEDIUM | OPEN |
| FCY-A07 | `BUSI_STTS='C'` (closed business) hides account-register button in legacy; no refactor equivalent | LOW | OPEN |
| OQ-FCY-2 (was FCY-G02) | As-of-date historical balance (date filter drives historical balance via `ACCT_MSTR_R004` + `ACCT_TRNS_HSTR`, incl. Vietcombank `11704004`) — **legacy real & functional; refactor snapshot-only** | HIGH (effort) | **CLOSED — PO Approved Deviation (Option B, 2026-06-06)** · prior "FALSE POSITIVE" RETRACTED |
| FCY-A08 | Date-change refetch behavior: legacy refetches (no-op), refactor does not refetch | LOW | OPEN |
| FCY-A09 | VAS account column present in both but never displayed (legacy: not rendered; refactor: hidden `USE_YN='N'`, `—` placeholder) | COSMETIC | CLOSED |
| FCY-A10 | "Register account when list empty" path (legacy `acct_Reg` → `nxisas_acctList.act`) | LOW | OPEN |

---

## 1. Screen-Level Audit

### Header

| Element | Legacy | Refactor | Same? |
|---|---|---|---|
| Page title | `<h1 data-jex-ml="fnsh_0005_001_m001">` — `fnsh_0005_001_view_osmc.jsp:155` | `PageHeader` title `t(titleKey)`, `titleKey="account_inquiry.title_fcy"` — `AccountInquiryFcyClient.tsx:31`; `AccountInquiryPage.tsx:971` | ✔ |
| Dashboard toggle | `.page-dashboard a … dashboard on` (`m058`) — `view:158-160` | Dashboard eye toggle — `AccountInquiryPage.tsx:977-989` | ✔ |
| View Setting | `.view-sort a.viewsetting` — `view:161-163` | `ViewSettingPopover` — `AccountInquiryPage.tsx:1406-1446` | ✔ |
| Excel | `#btnExcel` — `view:164` | `PageActionButtons` excel — `AccountInquiryPage.tsx:991-999` | ✔ |
| Print | `#printf` — `view:165` | `PageActionButtons` print — same | ✔ |
| Update / scrape button | `#acct_Reg` (`m052`, `btn-st primary disable`) — `view:168` | "Update" button — `AccountInquiryPage.tsx:1006-1028` | ◑ behavior differs (§4) |

**Visibility/permission rules (legacy):** `.scraping` shown only for admin — `fnsh_0005_001_api_osmc.ts:68-71`; account-register parent hidden when `BUSI_STTS=='C'` — `ts:78-81`. **Refactor:** no client-side role/visibility gating found — frontend agent confirmed; buttons gate on selection only (`updateEnabled`, `AccountInquiryPage.tsx:746,1017`). → **FCY-A06, FCY-A07.**

### Summary cards

| Aspect | Legacy | Refactor | Same? |
|---|---|---|---|
| Account count | `recTot.TOT_CNT` per `REC_TOT` card — `ts:668-695` | `CurrencyTotal.count` per `totalsByCcy` tile — `AccountListResponse.java:37-54`; `AccountInquiryPage.tsx:1041-1052` | ✔ |
| Balance | `recTot.TOT_BAL` — `ts:683` | `SUM(a.bal)` per ccy — `AcctMstrRepository.java:239` | ✔ |
| Available balance | `recTot.TOT_REAL_AMT` — `ts:679` | `SUM(a.realAmt)` per ccy — `AcctMstrRepository.java:240` | ✔ |
| Currency handling | One card per `REC_TOT.CCY_CD` (USD-only in practice — see FCY-A01) | One tile per `totals_by_ccy.ccy_cd` (multi-currency) | ◑ scope differs (FCY-A01) |
| Totals filter | `tot_dynamic0` applies PrdtType.H maturity/balance exclusion for savings — `FnshHelper.java:505-509` | `aggregateByCcy` has no PrdtType.H clause — `AcctMstrRepository.java:235-272` | ✘ **FCY-A04** |

### Search area

| Field | Legacy | Refactor | Same? |
|---|---|---|---|
| Bank filter | `.list_bank` dropdown → `input.BANK_CD` — `view:181-186`, `ts:581-582,601` → `FnshHelper.java:453-461` | `BankFilterPopover` → `bankCd` param → `AccountFilter.bankCd` → repo `:bankCd` — `AccountController.java:96`; `AcctMstrRepository.java:201-204(predicate)` | ✔ |
| Currency filter | `.list_ccyCd` — **only blank(all) + USD** — `view:190-193` | FCY currency dropdown (`multiCurrencyMode`) — `AccountInquiryPage.tsx:1095-1119` | ◑ FCY-A01 |
| Date filter | `#TO_DT` datepicker, default today — `view:197-203`, `ts:175-183` | `DateField`, default today (`todayIso()`) — `AccountInquiryPage.tsx:532,1125-1131` | ◑ see §3 |
| Keyword | `#SRCH_WD` → `input.SRCH_WD` ILIKE — `view:210`, `ts:564,602`; `FnshHelper.java:468-472` | `SearchInput` → `keyword` → `LIKE` on formatted nickname — `AcctMstrRepository.java:205(predicate, function wb_format_acct_nick_nm)` | ◑ keyword target differs slightly* |

\* Legacy matches `ACCT_NICK_NM ILIKE %kw%` (`FnshHelper.java:470`). Refactor matches `LOWER(wb_format_acct_nick_nm(...)) LIKE` — i.e. the **displayed** nickname (falls back to "BankName ####" when nickname blank). Functional near-parity; refactor is broader (matches the synthesized label too). Noted, not gated.

**Defaults:** both default the date to today. Legacy validates only `HAND_REG_YN`, `PAGE_NO`, `PAGE_PER_CNT` (`fnsh_0005_001_r001_act.jsp:24-36`); refactor clamps page size to `MAX_PAGE_SIZE` (`AccountService.java:158`).

---

## 2. Grid Audit

Both sides drive columns from the View Setting registry (legacy thead/colgroup empty in `view:223-228`, rendered by `ts:917-973`; refactor `COLUMN_REGISTRY` + `SYS_SCR_OPTION_WECMS`). The refactor FCY screenId is `banking-account-inquiry-fcy` (`viewSettingScreens.ts:79`).

| Legacy Column (FIELD_ID) | Refactor Column | Same? | Notes |
|---|---|---|---|
| CHECKBOX (selection) | CHECKBOX (`disp_ord=0`, never hideable) | ✔ | Both anchor a row-select column. |
| BANK_NM (+acct type + acct no) — `ts:925-936` | BANK (bankName + type + accountNo) — `AccountInquiryPage.tsx:169-192` | ✔ | Composite cell on both. |
| ACCT_NICK_NM — `ts:937-941` | ACCOUNT_NICKNAME — `:196-202` | ✔ | |
| BAL — `ts:942-950` (clickable → history popup) | BALANCE — `:206-216` (clickable → `BalancePopover`) | ✔ | Right-aligned amount; click opens per-account history/balance popup on both. |
| REAL_AMT — `ts:951-955` | AVAILABLE_BALANCE — `:221-226` | ✔ | |
| (VAS_ACCT_CD/NM in WSVC but **not rendered** in grid switch `ts:922-972`) | VAS_ACCOUNT (hidden `USE_YN='N'`, renders `—`) — `:232-236` | ✔ | **FCY-A09** — neither displays VAS today. Cosmetic. |
| ACCT_HIS_LST_INQ_DT (Last Update) — `ts:956-969` | LAST_UPDATE — `:240-249` | ✔ | **FCY-A02 CLOSED (2026-06-06)** — refactor now sources `ACCT_HIS_LST_INQ_DT` via `AccountService.resolveLastUpdate` (see below). |

**Label / order / formatting / sortability:** parity. Sort is server-side on both — legacy `SortHelper.getSortDynamic("fnsh_0001_001_r001")` (`FnshHelper.java:380`); refactor `AccountSortField` whitelist + `Sort` (`AccountInquiryPage.tsx:1196-1218`, `AccountService.java:160`). Both default to `PRN_SEQ ASC` (legacy `SortHelper#86`; refactor `DEFAULT_SORT`, `AccountService.java:124-125`). Amount cells right-aligned and formatted via `_osmc.ui.formatAmount` (legacy) / `formatAmount` (refactor `:215,226`).

### Last Update — special focus (FCY-A02)

- **Legacy source column:** for normal online accounts the displayed value is `ACCT_HIS_LST_INQ_DT`; only for FD-only banks (`ACCT_TYPE_CD=="02" && BANK_CD!="11704004"`) it falls back to `ACCT_BAL_LST_INQ_DTM` — `fnsh_0005_001_api_osmc.ts:956-969`.
- **Refactor source column:** ~~always `ACCT_BAL_LST_INQ_DTM`~~ → **FIXED 2026-06-06 (FCY-A02 CLOSED).** Now `AccountService.resolveLastUpdate(a)` returns `ACCT_HIS_LST_INQ_DT` for online accounts and falls back to `ACCT_BAL_LST_INQ_DTM` only for fixed-deposit-only banks (`ACCT_TYPE_CD='02'` excl. `11704004`) — byte-for-byte the legacy conditional. Wire field name `acct_bal_upd_dtm` retained; value source flipped. Sort key `AccountSortField.LAST_UPDATE` now maps `acctHisLstInqDt`.
- **Formatting:** both humanize to relative time — legacy `common.app.humanizeDatetime` (`ts:967`); refactor `humanizeDatetime` (`AccountInquiryPage.tsx:1634`, buckets in `lib/datetime.ts:372-414`). Tooltip carries full datetime on both. **Parity on formatting.**
- **Success/fail logic:** refactor reds the cell only when `row.error` (derived from `acct_bal_inq_stts`) — `AccountInquiryPage.tsx:1639-1646`; `mappers.ts:194`. Legacy additionally renders a **red staleness indicator when the last update is >72h old** regardless of status — `ts:791-802` (FD branch) and `ts:822-834` (normal branch), `lastDate>72 ? c_date_r : c_date_n`. → **FCY-A05** (refactor does not reproduce the 72h staleness red).
- **Empty state:** legacy shows manual-account marker (`ML_NO_UPDATE_INFORMATION2`) / "no update info" (`ts:756,780`); refactor blanks the cell on empty/malformed timestamp — `AccountInquiryPage.tsx:1650-1654`. Near-parity.

---

## 3. Date Filter Audit — ~~FCY-G02 verdict: **FALSE POSITIVE**~~ → **CORRECTED: OQ-FCY-2, legacy real & functional**

> **CORRECTION (2026-06-06).** This section's original conclusion was **wrong**. It inspected only the
> dynamic WHERE-clause builder (`fnsh_0001_001_r001_dynamic0`) and concluded the backend "never reads
> `START_DATE`". In fact `START_DATE` is bound by `FnshHelper.fnsh_0001_001_r001_readList:246`
> (`idoIn.put("START_DATE", input.getString("START_DATE"))`) and consumed in the **SELECT projection** of
> `IDO.ACCT_MSTR_R004.xml:90-217` — a `CASE` that returns the current `BAL`/`REAL_AMT` when the date is
> today/blank, otherwise reconstructs the as-of-date balance from `ACCT_TRNS_HSTR` (Vietcombank `11704004`
> = current minus transactions after the date, `:96-98,140-142,182-184`; other banks = ledger balance
> where `ATH.TRSC_DT <= :START_DATE`). **Legacy historical-balance inquiry is therefore real and
> functional.** Only the **refactor** renders the live snapshot. The original (incorrect) analysis is
> retained below struck-through for traceability.

~~Definitive evidence that **neither system performs date-aware/historical balance inquiry on this screen**:~~

**Legacy**
- The WSVC contract *declares* a date input: `<item ... id="START_DATE" />` — `WSVC.fnsh_0005_001_r001.xml:40`.
- The frontend *sends* it: `input.START_DATE = common.app.formatDateForSave(selectedDate)` — `fnsh_0005_001_api_osmc.ts:606-615`; date-change handler reloads — `ts:189-192`.
- **But the backend never reads it.** `fnsh_0005_001_r001_act.jsp:79` validates only `HAND_REG_YN`/`PAGE_NO`/`PAGE_PER_CNT`, then delegates to `FnshHelper.fnsh_0001_001_r001(..., CcyCd.USD)` (`:84`). The query builder `fnsh_0001_001_r001_dynamic0` (`FnshHelper.java:402-489`) constructs the WHERE clause from `actv_yn`, `biz_key`, `ccy_cd`, `acct_type_cd IN(...)`, `HAND_REG_YN`, `ACCT_CUST_TYPE`, `BANK_CD`, `CCY_CD`, `SRCH_WD` — **there is no `START_DATE` / date predicate, and no join to any history/balance-history table.** Balances come from `ACCT_MSTR.BAL` / `ACCT_MSTR.REAL_AMT` (current snapshot) via `AcctMstrDAO.getTotInfo` (`FnshHelper.java:392`).

**Refactor**
- No date parameter at any layer: `AccountController.listAccounts` declares `currency, bankCd, keyword, sortField, sortDirection, page, size` only (`AccountController.java:92-104`); `AccountFilter` carries `currency, bankCd, keyword` (`AccountFilter.java:23`); `findFiltered` / `aggregateByCcy` have no date predicate (`AcctMstrRepository.java:189-220, 235-272`). The date picker is local state and is **not** in the fetch dependency array — `AccountInquiryPage.tsx:532,1125-1131`; `AccountInquiryClient.tsx:155-164`.

**Answers to the audit's questions:**
- Does legacy query historical balances? **No.** Which SQL? `IDO.ACCT_MSTR_*` via `FnshHelper.fnsh_0001_001_r001_dynamic0` — current snapshot, no date. Which parameter? `START_DATE` is accepted on the wire but **discarded**. Mandatory? No (defaults to today, unused). Does refactor send/use date? **No** at every layer.
- ~~**Conclusion:** FCY-G02 is a **FALSE POSITIVE**. The date control is decorative on both sides…~~
- **CORRECTED Conclusion (2026-06-06):** Legacy **does** query historical balances — SQL is
  `IDO.ACCT_MSTR_R004.xml:90-217` via `FnshHelper.fnsh_0001_001_r001_readList:246` (the `:START_DATE`
  named param is consumed in the SELECT, **not** the WHERE — which is why the WHERE-only inspection
  missed it); the parameter is **used**, not discarded; the Vietcombank `11704004` branch is real. The
  **refactor** sends/uses no date at any layer (`AccountController.java:94-101`; `AccountFilter.java:23`;
  `findFiltered`/`aggregateByCcy` `AcctMstrRepository.java:189-220,235-272`); its date picker is page-local
  (`AccountInquiryPage.tsx:532,1125-1131`) and only seeds the per-account popup
  (`AccountInquiryPage.tsx:1301`). This is a genuine **legacy feature the refactor omits** — reclassified
  **OQ-FCY-2** and **CLOSED — PO Approved Deviation (Option B, 2026-06-06):** snapshot-only accepted,
  full parity (Option A) deferred to a post-SIT MR gated on realistic transaction history.

---

## 4. Update Balance Audit

Flow shape (button → API → scrape → save → refresh) is present on both, but topology differs.

| Stage | Legacy | Refactor |
|---|---|---|
| Trigger | `#acct_Reg` click — `ts:334-383`. If list empty → open `nxisas_acctList.act` (register); else collect checked rows and open `nxisas_tranList.act` layer popup with `dataJson` + `ccyCd` — `ts:379` | "Update" → `TransactionSyncDialog` (`AccountInquiryPage.tsx:755-762,1375-1399`) |
| API | Client-side NXiSAS engine via `common.app.getNxisasMulti` / nxisas layer popups (`ts:491`); engine bridges to Coocon | `POST /api/v1/banking/accounts/transactions-sync` → `AccountService.syncTransactions` → `BankTransactionSyncService.start` → `NxisasGatewayClient` |
| Request payload | NXiSAS multi-input: per-account `BANK_CD, ACCT_NO, CERT_INFO*, JOB='BALANCE,TRANSACTION', START_DATE, END_DATE, CCY_CD` — `ts:468-489` | `{accounts:[{bank_cd,acct_no}], from_dt?, to_dt?, auto}` — `TransactionsSyncRequest.java:30-63`; window defaults now-30d..now, 92-day cap — `BankTransactionSyncService.java:138-163` |
| Scrape | NXiSAS.exe local engine → Coocon wss/gateway | Server-side `restClient.post().body(reqJson).retrieve()` to Coocon smart-gateway — `NxisasGatewayClient.java:282`; JOBs LOGIN/TRANSACTION/BALANCE — `:71-223` |
| Save | NXiSAS callback persists balances/transactions | `processOneAccount` INSERTs `ACCT_TRNS_HSTR` (dedup on `BANK_TRNS_FULL_ID`) + `refreshBalance` writes `ACCT_MSTR.BAL/REAL_AMT` — `BankTransactionSyncService.java` (~`:500-738`) |
| Refresh | Reloads list (`fn_loadData`) | `onSyncComplete()` → parent refetch — `TransactionSyncDialog.tsx:222-247` |
| Success / failure | NXiSAS status codes drive per-row msg/red — `ts:fn_acc_his_msg` | discriminated `status` COMPLETED/ERROR + challenge resume — `TransactionSyncDialog.tsx:221-294` |
| Challenge (OTP/QNA/CAPTCHA) | NXiSAS engine handles in-browser | `OTP_REQUIRED`/`QNA_REQUIRED`/`CAPTCHA_REQUIRED` → resume endpoints — `NxisasGatewayClient.java:77-110`; dialogs `TransactionSyncDialog.tsx:764-799` |
| Timeout | engine-side | `AbortController` per request, cancel aborts in-flight fetch only — `TransactionSyncDialog.tsx:301-332,359-363` |

**Verdict (FCY-A03):** Structural parity, **topology divergence (by design)** — legacy scrapes client-side via the NXiSAS local engine; the refactor scrapes server-side via a real Coocon `RestClient`. Both reach the same Coocon backend. Under the documented R&D scope ("no bank integration") the refactor path is implemented but **not validated against live banks**; the frontend dialog still carries a stale "stub" comment though the backend gateway is real (`NxisasGatewayClient.java:282`). MEDIUM, scope-deferred.

---

## 5. Scraping Architecture Audit

The audit's premise ("legacy uses Coocon API; does NOT use NXiSAS.exe / local engine") is **partially contradicted by source.**

- **Legacy:** the FCY view includes the NXiSAS client bridge — `<%@ include file=".../inc_wabooks_nxisas_v2.jsp" %>` (`fnsh_0005_001_view_osmc.jsp:41`) — and the script calls `common.app.getNxisasMulti(...)` (`ts:491`) and opens `nxisas_tranList.act` / `nxisas_acctList.act` layer popups (`ts:341,379`). i.e. legacy **does** use the **client-side NXiSAS engine**, which in turn talks to the Coocon smart-gateway (per memory `reference_nxisas_local_engine_origin.md` / `reference_nxisas_gateway_urls.md`). It is *both* — NXiSAS local agent **bridging to** Coocon, not Coocon-only.
- **Refactor:** server-side `RestClient` to the Coocon smart-gateway, no local engine — `NxisasGatewayClient` (`:51-282`), config via `CooconProperties.smartApiKey()` (`:315`), audit into `NXSIAS_MODULE_TRAN_LOG_HIS`.

| Check | Legacy | Refactor | Parity |
|---|---|---|---|
| Endpoints | Coocon smart-gateway via NXiSAS engine | Coocon smart-gateway direct (`nxisasRestClient`) | ◑ same backend |
| Payloads | NXiSAS multi-job envelope | `NxisasApiIn` envelope (`Common`+`InputList[thread]`, `Dsnc="S"`) — `:312-356` | ◑ equivalent shape |
| Polling/callbacks | engine callback in browser | session continuation + resume endpoints — `:231-256` | ◑ different mechanism |
| Topology | **client-side engine** | **server-side** | ✘ divergent (FCY-A03) |

---

## 6. API Audit

Legacy `fnsh_0005_001_r001` OUTPUT (`WSVC.fnsh_0005_001_r001.xml:42-87`) vs refactor `GET /api/v1/banking/accounts` → `AccountRow` (`AccountRow.java:22-71`).

| Legacy field (REC) | Refactor field (`AccountRow`) | Same? |
|---|---|---|
| BANK_CD | bankCd | ✔ |
| ACCT_NO | acctNo | ✔ |
| ACCT_NICK_NM | acctNickNm | ✔ |
| BAL | bal | ✔ |
| REAL_AMT | realAmt | ✔ |
| ACCT_TYPE_CD | acctTypeCd (+ acctTypeNm/acctTypeKey) | ✔ (refactor adds i18n key) |
| BANK_NM / BANK_NM2 | bankNm | ◑ refactor drops formal name (`BANK_NM2`) |
| ACCT_BAL_INQ_STTS | acctBalInqStts | ✔ |
| ACCT_BAL_LST_INQ_DTM | acctBalUpdDtm (sourced from `ACCT_BAL_LST_INQ_DTM`) | ◑ renamed (FCY-A02) |
| HAND_REG_YN | handRegYn | ✔ |
| CERT_INFO_DV / CERT_INFO_CON / CERT_INFO_DETL_CON(2) | certConnYn (boolean derived) | ◑ refactor exposes only a connected flag, not raw cert payload (security-positive) |
| ACCT_HIS_LST_INQ_DT, ACCT_HIS_INQ_STTS, ACCT_HIS_INQ_MSG | — | ✘ not on wire (drives legacy Last Update + msg) |
| LST_LGN_STTS, LST_LGN_STTS_MSG, LST_LGN_SUC_DTM, TRN_LST_SUC_DTM | — | ✘ not on wire (legacy uses for staleness/status branching `ts:789-855`) |
| NXISAS_ACCT_TYPE, CORC_BF_BAL, TRSC_DT, BANK_CERT_SEQ | — | ✘ not on wire (legacy scrape/popup plumbing) |
| ACCT_CUST_TYPE | — | ✘ not on wire (KH personal/corporate split, not VN) |
| VAS_ACCT_CD / VAS_ACCT_NM | — (VAS column hidden) | ✔ (both unused) |
| CCY_CD | ccyCd | ✔ |
| TOT_CNT / TOT_BAL / TOT_REAL_AMT / REC_TOT[] | totalCount / balance / availableBal / totalsByCcy[] | ✔ |

**Missing fields of consequence:** `ACCT_HIS_LST_INQ_DT` + the `LST_LGN_*` / `ACCT_HIS_INQ_*` status fields are absent on the refactor wire — they are precisely what the legacy grid uses for the Last-Update source (FCY-A02) and the >72h staleness red (FCY-A05). **Renamed:** `ACCT_BAL_LST_INQ_DTM` → `acct_bal_upd_dtm`. **Transformed:** raw cert fields collapsed into `certConnYn` (improvement). Wire shape is lowercase snake_case per CTO directive; envelope `{status,data}` (not `COMMON_HEAD/REC`).

---

## 7. Database Audit

| Concern | Legacy | Refactor | Parity |
|---|---|---|---|
| Primary table | `ACCT_MSTR` (alias T) — `FnshHelper.java:412-472` | `AcctMstr` `@Table("ACCT_MSTR")` — `AcctMstr.java:31` | ✔ |
| Tenant filter | `T.biz_key = ?` — `:414` | `a.bizKey = :bizKey` — `AcctMstrRepository.java(findFiltered)` | ✔ |
| Active filter | `T.actv_yn='Y'` — `:412` | `a.actvYn='Y'` | ✔ |
| Currency filter | `T.ccy_cd = 'USD'` (CcyCd.USD) — `:417-418` | `a.ccyCd <> 'VND'` (FCY) — `resolveFilter`, `AccountService.java:450-452` | ◑ **FCY-A01 CLOSED** — PO Approved Deviation (multi-currency FCY) |
| Acct-type filter | `IN ('04','06','02')` (CURRENT_USD/SAVINGS_USD/DEPOSIT) — `:426-430` | `IN ('02','04','06','07','08')` (+ KRW) — `FCY_TYPE_CODES`, `AccountService.java:109-114` | ◑ **FCY-A01 CLOSED** — approved |
| Bank filter | `T.BANK_CD = ?` — `:453-461` (duplicated harmless) | `:bankCd` | ✔ |
| Keyword | `T.ACCT_NICK_NM ILIKE %?%` — `:468-472` | `LOWER(wb_format_acct_nick_nm(...)) LIKE` | ◑ §1 note |
| Per-user authority | non-admin → `getPermittedBankAccountDynamic` (ACCT_AUTH_INFO `use_yn='Y'`) — `:483-486` | EXISTS on `AcctAuthInfo` (`use_yn='Y'`, `auth_type='V'`) when non-admin — `AccountService.java:186-188`; repo EXISTS subquery | ✔ |
| Aggregation | `getTotInfo(dynamic0)` + `REC_TOT` via `tot_dynamic0` (PrdtType.H exclusion) — `:387-392,505-509` | `aggregateByCcy` GROUP BY ccy, **no H clause** — `AcctMstrRepository.java:235-272` | ✘ **FCY-A04** |
| Balance source | `ACCT_MSTR.BAL/REAL_AMT` (snapshot) | `AcctMstr.bal/realAmt` (snapshot) | ✔ |
| `BANK_CERT_INFO` | read for cert/connect status | `BankCertInfoRepository.findConnectedBankCds` → `certConnYn` — `AccountService.loadConnectedBanks ~:550` | ✔ |
| `ACCT_TRNS_HSTR` | written by scrape; read by per-account popup (`fnsh_0001_001_r002`) | written by sync; not read on list path | ✔ (list path) |

Filters/joins/sort/aggregation otherwise match. The two HIGH/MEDIUM divergences are FCY-A01 (currency+type scope) and FCY-A04 (H maturity exclusion on totals).

---

## 8. UI Audit (functional only)

- **Controls / disabled state:** Update button disabled until ≥1 row selected on both (legacy `acct_Reg disable` toggled in `ts:517-528`; refactor `updateEnabled` `:746,1017`). Per-row checkbox disabled for manual / disconnected-bank accounts on both (legacy `ts:757-782`; refactor `row.selectable===false` + tooltip `:1244-1253,1279`). **Functional parity.**
- **Loading state:** legacy progress bar (`tmsComm.createProgressBar`); refactor sticky-loading latch keeps prior rows, no skeleton yet (`AccountInquiryPage.tsx:88-93`). Cosmetic.
- **Empty state:** legacy "No Account Found" (`ts:1022-1033`); refactor `account_inquiry.empty_fcy` (`:1325-1333`). Functional parity (FCY has dedicated copy).
- **Error state:** legacy `_v2common.app.showMessage(data)` on JEX error (`ts:639-643`); refactor surfaces via dialog for sync, swallows list error to empty/loading (`useApiData` returns no error). Minor.
- **Cosmetic differences** (spacing, skeleton, tooltip styling) are **not** counted as parity gaps.

---

## 9. Permission Audit

| Permission | Legacy | Refactor | Same? |
|---|---|---|---|
| Login required | `<login>Y</login>` — `WSVC...:28` | JWT-protected (everything outside `/auth/*`) | ✔ |
| Menu / page | WSVC ext `WTA_USER_ALLOW_YN=Y`, `NORM_USER_ONLY_YN=Y` — `:25-26` | `@PreAuthorize("@menuAuthz.canAccessAccountInquiry(#p0)")` → FCY resolves to `banking-fcy-update-inquire` — `AccountController.java:93`; `MenuAuthorizationGuard.java:141-145` | ✔ (backend) |
| Per-account data scope | non-admin restricted via ACCT_AUTH_INFO — `FnshHelper.java:483-486` | EXISTS on ACCT_AUTH_INFO for non-admin — `AccountService.java:186-188` | ✔ |
| Update / scrape | admin-only (`.scraping` shown for admin) — `ts:68-71` | backend `canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')` on `/transactions-sync`; **no frontend role gate** | ◑ **FCY-A06** |
| Export / download | excel button always present | `/accounts/export` gated `canAccessAny(...)`; **no frontend role gate** | ◑ **FCY-A06** |
| Closed business (`BUSI_STTS='C'`) | hides register button — `ts:78-81` | no equivalent | ✘ **FCY-A07** |

Backend authorization is enforced and arguably stronger (per-endpoint `@PreAuthorize`). The gap is **client-side** button gating (legacy hides admin-only scraping; refactor relies on backend 403).

---

## 10. Edge Cases

| Case | Legacy | Refactor | Parity |
|---|---|---|---|
| No account | "No Account Found", register button enabled — `ts:1022-1033` | `empty_fcy` message — `:1325-1333` | ◑ (no "register-when-empty" affordance — FCY-A10) |
| Single / multiple accounts | grid + per-ccy cards | grid + `totalsByCcy` tiles | ✔ |
| Failed scrape | per-row status msg + red via `fn_acc_his_msg` | sync dialog `ERROR` status | ◑ list-row status not re-derived from missing wire fields |
| Scrape timeout | engine-side | `AbortController` (client) — `:301-332` | ◑ |
| Bank disconnected | `cert_info_con==''` → checkbox disabled + tooltip — `ts:774-782` | `row.selectable===false` + tooltip — `:1244-1253` | ✔ |
| Missing balance | `parseFloat(val.BAL)` → 0 display | `AmountFormatter.wire` of null → 0 | ✔ |
| Missing update time | manual/"no info" marker or blank | blank cell — `:1650-1654` | ◑ near-parity |
| Null currency | n/a (USD-locked) | `ccyCd` nullable; summary uses `summaryCurrency` fallback | ◑ refactor handles multi/empty ccy |
| Stale >72h | red staleness indicator — `ts:791-802,822-834` | not reproduced — **FCY-A05** | ✘ |

---

## Final Verdict

**READY FOR SIT** (read path) — the two weighted divergences were dispositioned on 2026-06-06 (fresh source re-audit below), with Update-Balance scraping carried as a known, accepted, scope-deferred item.

Rationale: the read path is at parity; **OQ-FCY-2 (was FCY-G02, date-aware/historical balance) is a real legacy feature the refactor omits — NOT a false positive (that earlier finding is retracted, see §3) — and is CLOSED as a PO Approved Deviation (Option B, 2026-06-06):** snapshot-only balances accepted for SIT, full legacy parity (Option A, incl. Vietcombank `11704004`) deferred to a post-SIT MR gated on realistic transaction history. Both originally-blocking items are also closed:

1. **FCY-A01 (HIGH) → CLOSED, PO Approved Deviation.** Multi-currency FCY (`ccy_cd<>'VND'`) is the intended modernization; legacy USD-only scope is intentionally retired. No code change.
2. **FCY-A02 (MEDIUM) → CLOSED, Implemented.** "Last Update" sources `ACCT_HIS_LST_INQ_DT` (online) with the FD-only `ACCT_BAL_LST_INQ_DTM` fallback — legacy-equivalent across grid, sort, and export.

Carried (do not block SIT): **FCY-A03** (server-side scraping not validated vs live banks — explicit R&D-scope deferral), **FCY-A04** (Haengsung maturity totals filter), **FCY-A05** (>72h stale-red indicator), **FCY-A06/A07** (frontend role / closed-business gating — backend already enforces auth). These are tracked for the next stabilisation pass; none change the read-path verdict.

### Final verification — fresh source re-audit (2026-06-06, post-implementation)

Re-read from source after the change (not from prior findings):

- **Controller** — `AccountController.listAccounts` (`:92-104`): FCY scope via `currency` param → `@menuAuthz.canAccessAccountInquiry` → `banking-fcy-update-inquire`. Unchanged; multi-currency confirmed (FCY-A01).
- **Service** — `AccountService.toRow` (`:579-610`) now calls **`resolveLastUpdate(a)`**; new helper (`resolveLastUpdate`) returns `a.getAcctHisLstInqDt()` for online accounts, `a.getAcctBalLstInqDtm()` for FD-only (`ACCT_TYPE_CD='02'` && `BANK_CD != '11704004'`). `resolveFilter` FCY branch (`:450-452`) = `ccyNot="VND"` + `FCY_TYPE_CODES`.
- **Repository** — `AcctMstrRepository.findFiltered` / `aggregateByCcy`: no date predicate (this is the **refactor's** snapshot-only behaviour — the OQ-FCY-2 Option-B deviation; **not** evidence that legacy lacks date inquiry, which it has — see §3 correction); sort property `acctHisLstInqDt` is a mapped `AcctMstr` field (`ACCT_HIS_LST_INQ_DT`, entity `:158-159`) so `Sort.by("acctHisLstInqDt")` is metamodel-valid.
- **DTO** — `AccountRow.acctBalUpdDtm` (`:35`): wire name retained; value now `ACCT_HIS_LST_INQ_DT`-sourced.
- **Sort enum** — `AccountSortField.LAST_UPDATE` → `acctHisLstInqDt` (`:32`). Case 3 satisfied.
- **Export** — `AccountInquiryExportProvider.toFieldMap` (`:170`): `m.put("LAST_UPDATE", r.acctBalUpdDtm())` inherits the corrected value via the DTO. Case 4 satisfied.
- **Frontend wire/grid** — `ApiAccountRow.acct_bal_upd_dtm` → `mappers.ts:193 lastUpdate` → `LastUpdateCell` (humanize + error). Unchanged by design; the value flips on the backend so the grid renders the legacy-equivalent timestamp.
- **Sync stamp** — `BankTransactionSyncService.processOneAccount:555` `setAcctHisLstInqDt(nowDb)` confirms the Update button still moves the column.
- **Validation cases:** Case 1 (online acct, has `ACCT_HIS_LST_INQ_DT`) → displays it ✔ (unit test `…_forOnlineAccount`); Case 2 (`ACCT_HIS_LST_INQ_DT` NULL) → blank for online / FD-only falls back to balance date ✔ (unit test `…_forFixedDepositOnlyBank`); Case 3 (sort) → `acctHisLstInqDt` ✔; Case 4 (export) → DTO value ✔. `./gradlew compileJava compileTestJava` + `AccountServiceAuthorityTest` green.

---

### Evidence index (primary files read)

- Legacy view: `WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/fnsh_0005_001_view_osmc.jsp`
- Legacy script: `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0005_001_api_osmc.ts`
- Legacy action: `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0005_001_r001_act.jsp`
- Legacy helper: `WECMS DEV/WECMS/src/wabooks/helper/FnshHelper.java` (`fnsh_0001_001_r001*`, lines 363-537)
- Legacy WSVC: `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.fnsh_0005_001_r001.xml`
- Refactor BE: `wecms-api/.../banking/controller/AccountController.java`, `service/AccountService.java`, `repository/AcctMstrRepository.java`, `entity/AcctMstr.java`, `dto/AccountRow.java`+`AccountListResponse.java`+`CurrencyTotal.java`, `nxisas/NxisasGatewayClient.java`+`BankTransactionSyncService.java`
- Refactor FE: `wecms-web/.../banking/account-inquiry-fcy/{page.tsx,AccountInquiryFcyClient.tsx}`, `.../account-inquiry/AccountInquiryClient.tsx`, `components/shared/AccountInquiryPage.tsx`, `lib/api/banking.ts`, `lib/types/api.ts`, `lib/viewSettingScreens.ts`
