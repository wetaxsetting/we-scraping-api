# Banking > Own Account Transfer — JEX → Spring/Next Migration Mapping

**Refactor slug:** `own-account-transfer`
**Refactor route:** `/banking/own-account-transfer`
**Status:** Phase 1 — design only. No code in this phase.
**Feature spec:** [../../features/banking/own-account-transfer.md](../../../features/banking/own-account-transfer/own-account-transfer.md)
**Backend design:** [../../backend/banking/own-account-transfer-api.md](../../api-spec/banking/own-account-transfer-api.md)
**Frontend design:** [../../frontend/banking/own-account-transfer-page.md](../../../../../wecms-web/docs/features/banking/own-account-transfer/own-account-transfer.md)
**DB design:** [../../database/banking/own-account-transfer-db.md](../../database/banking/own-account-transfer-db.md)

> JEX trees are read-only reference. This file traces every JEX artefact (citation only) to its refactor counterpart. Legacy WSVC ids (`trns_0001_001*`) appear **only** as citations — every refactor identifier surface uses the slug `own-account-transfer`.

---

## 1. Service mapping table

| JEX WSVC ID (citation) | JEX `_act.jsp` (citation) | Spring endpoint (refactor) | Method | Repository | Tables touched | Status |
|---|---|---|---|---|---|---|
| `trns_0001_001_r001` | [`trns_0001_001_r001_act.jsp`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0001_001_r001_act.jsp) | `GET /api/v1/banking/own-account-transfer/source-accounts` | `OwnAccountTransferService#listSourceAccounts(query)` | `AcctMstrRepository` (reused) + `AcctAuthInfoRepository` (new) | `ACCT_MSTR`, `ACCT_AUTH_INFO` (read) | **Planned** |
| `trns_0001_001_r002` | [`trns_0001_001_r002_act.jsp`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0001_001_r002_act.jsp) | `GET /api/v1/banking/own-account-transfer/recipient-banks` (composer-scope; consumed by sibling `own-account-transfer-composer` feature) | `OwnAccountTransferService#listRecipientBanks(bankCd, schWord, fromTrcoYn)` | `NapasBankInfoRepository` (reused, extended) + `NapasBankRemitInfoRepository` (new) | `NAPAS_BANK_INFO`, `NAPAS_BANK_REMIT_INFO`, `ACCT_MSTR` (read) | **Sibling feature** — listed here because the JSP `trns_0001_001` is the WSVC owner; actual UI consumer is the composer popup |
| `trns_0001_001_r003` | [`trns_0001_001_r003_act.jsp`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0001_001_r003_act.jsp) | `GET /api/v1/banking/own-account-transfer/napas-bank-info` (composer-scope) | `OwnAccountTransferService#getNapasBankInfo(napasBankCd)` | `NapasBankInfoRepository` (reused) | `NAPAS_BANK_INFO` (read) | **Sibling feature** |
| `trns_0001_001_u001` | [`trns_0001_001_u001_act.jsp`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0001_001_u001_act.jsp) | `POST /api/v1/banking/own-account-transfer/balance-refresh` | `OwnAccountTransferService#applyBalanceRefresh(request)` | `AcctMstrRepository` (reused) + `AuditLogRepository` | `ACCT_MSTR` (write), `AUDIT_LOGS_INFM` (write) | **Planned** |
| _(no JEX counterpart — refactor-only)_ | _(no JEX file — engine handshake was a pure browser concern in legacy with no server log)_ | `POST /api/v1/banking/own-account-transfer/engine-connection-check` | `OwnAccountTransferService#recordEngineConnectionCheck(req)` | `AuditLogRepository` | `AUDIT_LOGS_INFM` (write) | **Planned — added per OQ-4 (PO directive 2026-05-18)** — audit-only sink: records the result + engine vendor + version (e.g. `COCON` / `NXSAS 2025.03.11.0`) every time the browser bundle probes the local NXiSAS engine at page mount. Compliance trail for "who opened the page on which engine, when". |

**Add a row** for each new endpoint to a future [./service-mapping.md](../service-mapping.md) when this design is approved — keeping that file current is the cross-feature index used by the migration playbook.

The associated frontend pages:

| JEX view JSP (citation) | Next.js route (refactor) | Component(s) | Status |
|---|---|---|---|
| [`trns_0001_001_view.jsp`](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/trns/trns_0001_001_view.jsp) + [`trns_0001_001_v001.ts`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_001_v001.ts) | `/banking/own-account-transfer/page.tsx` | `<OwnAccountTransferClient>` + `<OwnAccountTransferPage>` + `<OwnAccountSourceTable>` + `<BankPopover>` (shared) + `<OwnAccountSourceTableFooter>` + `<Pagination>` (shared) | **Planned** |
| [`trns_0001_007_view.jsp`](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/trns/trns_0001_007_view.jsp) | `/banking/own-account-transfer/composer` (modal route, sibling feature) | `<OwnAccountTransferComposer>` | **Sibling feature** |

## 2. WSVC `<in>` → Spring `@RequestParam` / `@RequestBody` mapping

### 2.1 List endpoint (`r001`)

Source: `WSVC.trns_0001_001_r001.xml:32-34`.

| JEX WSVC field | JEX length | Java field (camelCase, in `OwnAccountSourceQuery` record) | Wire param (snake) | Validation | Source |
|---|---|---|---|---|---|
| `BANK_CD` | 20 | `bankCd` | `bank_cd` | optional; `@Pattern("\\d{8}")` | `_r001_act.jsp:159` |
| — (legacy `paramSort` URL suffix appended client-side) | — | `sortField` | `sort_field` | whitelist | `_r001_act.jsp:170` (`SortHelper.getSortDynamic`) |
| — | — | `sortDirection` | `sort_direction` | `ASC`/`DESC` | same |

**Refactor convention shifts (intentional, not parity gaps):**
- Free-form `paramSort` URL suffix → explicit `sort_field` + `sort_direction` params with a server-side whitelist (refactor convention introduced by Account Inquiry, see [docs/refactor-wecms/migration/api-mapping/firm-banking-history-mapping.md §2](../firm-banking-history-mapping.md)).
- No pagination params on the wire — legacy returns all rows for the bank filter and paginates client-side; refactor keeps that behaviour (OQ-2 in feature spec).

### 2.2 Recipient bank lookup (`r002`) — composer-scope, listed for completeness

Source: `WSVC.trns_0001_001_r002.xml:32-36`.

| JEX | length | Java | Wire | Validation |
|---|---|---|---|---|
| `BANK_CD` | 20 | `bankCd` | `bank_cd` | `@NotBlank` when `from_trco_yn != 'Y'` (legacy `validateInput`) |
| `SCH_WORD` | 255 | `keyword` | `keyword` | optional `@Size(max=255)` |
| `FROM_TRCO_YN` | 1 | `fromTrcoYn` | `from_trco_yn` | optional `@Pattern("[YN]")` |

### 2.3 NAPAS bank info (`r003`) — composer-scope

Source: `WSVC.trns_0001_001_r003.xml:32-34`.

| JEX | length | Java | Wire | Validation |
|---|---|---|---|---|
| `NAPAS_BANK_CD` | 10 | `napasBankCd` | `napas_bank_cd` | `@NotBlank @Pattern("\\d{6,10}")` |

### 2.4 Balance refresh (`u001`)

Source: `WSVC.trns_0001_001_u001.xml:32-40`.

| JEX WSVC field | length | Java (in `BalanceRefreshRequest` record) | Wire | Validation | Source |
|---|---|---|---|---|---|
| `BANK_CD` | 20 | `bankCd` | `bank_cd` | `@NotBlank @Pattern("\\d{8}")` | `_u001_act.jsp:29` |
| `ACCT_NO` | 100 | `acctNo` | `acct_no` | `@NotBlank @Size(max=100)` | `_u001_act.jsp:30` |
| `BAL` | 20 | `bal` | `bal` | optional `@DecimalMin("0")` | `_u001_act.jsp:32-34` |
| `REAL_AMT` | 20 | `realAmt` | `real_amt` | optional `@DecimalMin("0")` | `_u001_act.jsp:36-38` |
| `ACCT_BAL_INQ_STTS` | 20 | `acctBalInqStts` | `acct_bal_inq_stts` | `@NotBlank @Size(max=20)` — `"00000000"` = engine success | `_u001_act.jsp:40` |
| `ACCT_BAL_INQ_MSG` | 255 | `acctBalInqMsg` | `acct_bal_inq_msg` | optional `@Size(max=255)` | `_u001_act.jsp:96` |
| `CCY_CD` | 3 | `ccyCd` | `ccy_cd` | optional `@Pattern("[A-Z]{3}")` — defaults to `VND` for this feature | `_u001_act.jsp:143` |

## 3. WSVC `<out>` → Spring DTO mapping

### 3.1 List endpoint (`r001`)

Source: `WSVC.trns_0001_001_r001.xml:35-64`.

Top-level response — `OwnAccountSourceListResponse`:

| JEX field | Java | Wire | Source |
|---|---|---|---|
| `TOT_CNT` | `totalCount` | `total_count` | from `ACCT_MSTR_R002_CNT` |
| `TOT_BAL` | `totBal` | `tot_bal` | `coalesce(sum(BAL), 0)` |
| `TOT_AVI_BAL` | `totAviBal` | `tot_avi_bal` | `coalesce(sum(BAL), 0)` — legacy aliases both to `BAL`; refactor keeps parity but documents the divergence (no `REAL_AMT`-based sum exists in legacy) |
| `REC` (RECORD) | `list` | `list` | renamed from legacy `REC` per CTO 2026-05-08 |

Per-row — `OwnAccountSourceRow`:

```
JEX WSVC field (UPPER_SNAKE)  →  Java property (camelCase)  →  JSON wire name (snake_case)
BANK_CD                       →  bankCd                     →  bank_cd
BANK_NM                       →  bankNm                     →  bank_nm
BANK_NM2                      →  bankNm2                    →  bank_nm2
ACCT_NO                       →  acctNo                     →  acct_no
ACCT_NICK_NM                  →  acctNickNm                 →  acct_nick_nm
BAL                           →  bal                        →  bal
REAL_AMT                      →  realAmt                    →  real_amt
CCY_CD                        →  ccyCd                      →  ccy_cd
ACCT_TYPE_CD                  →  acctTypeCd                 →  acct_type_cd
ACCT_BAL_INQ_STTS             →  acctBalInqStts             →  acct_bal_inq_stts
ACCT_BAL_INQ_MSG              →  acctBalInqMsg              →  acct_bal_inq_msg
ACCT_BAL_UPD_DTM              →  acctBalUpdDtm              →  acct_bal_upd_dtm
ACCT_BAL_LST_INQ_DTM          →  acctBalLstInqDtm           →  acct_bal_lst_inq_dtm
BANK_CERT_SEQ                 →  bankCertSeq                →  bank_cert_seq
USE_YN_V                      →  useYnV                     →  use_yn_v
USE_YN_W                      →  useYnW                     →  use_yn_w
AUTH_TYPE_V                   →  authTypeV                  →  auth_type_v
AUTH_TYPE_W                   →  authTypeW                  →  auth_type_w
```

**Fields legacy carries but refactor drops** (out-of-scope for the source-account list): `TRNS_PWD` (decrypted password — never on the wire), `ACCT_PWD`, `NXISAS_ACCT_TYPE`, `LST_LGN_STTS*`, `TRN_LST_SUC_DTM`, `VAS_ACCT_SEQ_NO`, `CONT_RT`, `PYAT_DT`, `PYAT_AMT`, `LOAN_NM`, `AGMT_AMT`, `HAND_REG_YN`, `ACCT_HIS_*`, `REG_*`, `MOD_*`, `PRN_SEQ`, `ACCT_CUST_TYPE`, `REMIT_OUT_ACCT_USE_YN`. The transfer composer can declare its own DTO with a wider projection when it needs them.

### 3.2 Balance refresh (`u001`)

Source: `WSVC.trns_0001_001_u001.xml:41-47`.

Response — `BalanceRefreshResponse`:

| JEX field | Java | Wire | Notes |
|---|---|---|---|
| `BANK_CD` | `bankCd` | `bank_cd` | echo |
| `ACCT_NO` | `acctNo` | `acct_no` | echo |
| `BAL` | `bal` | `bal` | from the patched row (when `acct_bal_inq_stts='00000000'`) or the pre-existing BAL (otherwise) — mirrors `_u001_act.jsp:136-140` |
| `CCY_CD` | `ccyCd` | `ccy_cd` | echo |
| `ACCT_BAL_LST_INQ_DTM` | `acctBalLstInqDtm` | `acct_bal_lst_inq_dtm` | UTC yyyymmddhhmiss — refactor uses `Instant.now()` once and stamps both columns from it (`_u001_act.jsp:91`) |

### 3.3 Frontend in-memory shapes

Per the [three naming layers rule](../../../../../wecms-web/CLAUDE.md#api-naming--serialization-standards):

- **Wire types** in `lib/types/api.ts`:
  - `ApiOwnAccountSourceRow` — snake_case property names matching the wire
  - `ApiOwnAccountSourceListResponse`
  - `ApiBalanceRefreshRequest`, `ApiBalanceRefreshResponse`
- **In-memory types** in `components/banking/own-account-transfer/types.ts` (camelCase semantic names):
  - `OwnAccountRow { bankCd; bankNm; acctNo; acctNickNm; balance; available; currency; acctType; lastRefreshStatus; lastRefreshAt; lastBalanceUpdatedAt; bankCertSeq; canTransfer (computed) }`
  - `OwnAccountTotals { total: number; balance: number; available: number }`
- **Mappers** in `lib/api/mappers.ts`:
  - `mapOwnAccountRow(api: ApiOwnAccountSourceRow): OwnAccountRow`
  - `mapOwnAccountTotals(api: ApiOwnAccountSourceListResponse): OwnAccountTotals`

The `canTransfer` flag is computed in the mapper from `(is_admin || (use_yn_w='Y' && auth_type_w='W'))` — the same condition legacy applies in `trns_0001_001_v001.ts:530-537`, but lifted out of the row render so the table component never sees the raw `use_yn_*` columns.

## 4. DAO / IDO → Spring Data repository mapping

| JEX DAO / IDO (citation) | Refactor repository | Method | Returns | Notes |
|---|---|---|---|---|
| `wabooks.dao.AcctMstrDAO` + `IDO.ACCT_MSTR_R015.xml` | `AcctMstrRepository` (existing) — add `findSourceAccountsForOwnTransfer(...)` | `findSourceAccountsForOwnTransfer(bizKey, userId, bankCds, isAdmin, prdtType, sort)` | `List<OwnAccountSourceRow>` projection | Native query or JPA criteria — the legacy IDO joins `ACCT_MSTR` to a `MAX(CASE … END)` pivot over `ACCT_AUTH_INFO`; port the same pivot as a Postgres subquery. **No** `wb_get_bank_nm()` / `wb_format_acct_nick_nm()` / `decrypt()` calls in the new query — those move into the Java service layer (`BankCdResolver`, `AcctNickNmFormatter`); password decryption stays out of the list path entirely. |
| `IDO.ACCT_MSTR_R002_CNT.xml` | `AcctMstrRepository#aggregateForOwnTransfer(...)` | `aggregateForOwnTransfer(bizKey, userId, bankCds, isAdmin, prdtType)` | record `AcctMstrTotals(totalCount, totBal, totAviBal)` | Same WHERE clause as the list query. Refactor: compute both queries in one tx; or fold totals into a `WITH` of the list query (one round-trip — preferred). |
| `wabooks.dao.AcctMstrDAO#patch(...)` + `IDO.ACCT_MSTR_U000.xml` (partial patch) | `AcctMstrRepository#applyBalanceRefresh(...)` | `applyBalanceRefresh(bizKey, bankCd, acctNo, fields)` | refreshed `AcctMstr` | `@Modifying @Query` UPDATE that always patches `ACCT_BAL_INQ_STTS / _MSG / _LST_INQ_DTM` + `MOD_USER / MOD_DTM`; conditionally patches `BAL / REAL_AMT / CORC_BF_BAL / ACCT_BAL_UPD_DTM` when status is `00000000`. Wrapped in a single `@Transactional` boundary on the service. |
| `wabooks.helper.ValidationHelper#validateAcctMstr(...)` | `AcctMstrRepository#findByIdScoped(bizKey, bankCd, acctNo)` + service guard | — | `Optional<AcctMstr>` | Missing row → `BusinessException("WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND")`. |
| `wabooks.dao.NapasBankInfoDAO#getRows(...)` + `IDO.NAPAS_BANK_INFO_R001.xml` | `NapasBankInfoRepository` (existing) — extend with `findByNapasBankCd(...)` if not already present | `findByNapasBankCd(napasBankCd)` | `Optional<NapasBankInfo>` | Used by `r003`. **Composer-scope**; listed here only because the JEX WSVC belongs to `trns_0001_001`. |
| `IDO.NAPAS_BANK_REMIT_INFO_R002.xml` | `NapasBankRemitInfoRepository` (new) — composer-scope | `searchRecipientBanks(bankCd, keyword, bizKey)` | `List<RecipientBankRow>` projection | Three-DYNAMIC UNION query; port verbatim as a JPQL native query. **Composer-scope.** |
| `IDO.NAPAS_BANK_REMIT_INFO_R003.xml` | `NapasBankRemitInfoRepository#listFromContacts(bizKey)` | — | `List<RecipientBankRow>` | The `FROM_TRCO_YN='Y'` branch — picks contacts as recipients. **Composer-scope.** |
| `wabooks.helper.SortHelper#getSortDynamic("trns_0001_001_r001", ...)` | `OwnAccountSourceSortField` enum (new) + Spring `Sort` | — | — | Whitelist enum: `BANK`, `ACCT_NICK_NM`, `BAL`, `AVI_BAL`, `ACCT_BAL_LST_STS`. Maps to `(BANK_CD, ACCT_NO)` / `ACCT_NICK_NM` / `BAL` / `REAL_AMT` / `ACCT_BAL_LST_INQ_DTM`. |
| `wabooks.session.SessionManager#getLoginUser` | `TenantContext.current()` | — | `Principal` | Standard refactor pattern — JWT-derived. |
| `wabooks.commons.DateTimeUtils.utcNow()` / `normalizeResult` | `Instant.now()` + service-level formatter | — | — | The legacy `normalizeResult` rewrites `ACCT_BAL_LST_INQ_DTM` to the user's TZ; refactor ships UTC yyyymmddhhmiss verbatim and lets the frontend handle locale formatting (consistent with Account Inquiry refactor). |

## 5. Reusable components — DO NOT duplicate

The refactor already ships these — own-account-transfer **must consume them**, not rebuild:

### 5.1 Backend (`wecms-api`)

| Component | Path | What it does |
|---|---|---|
| `AcctMstrRepository` + `AcctMstr` entity | [domain/banking/entity/AcctMstr.java](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java) | Already maps every column we need (`BAL`, `REAL_AMT`, `ACCT_BAL_INQ_STTS`, `ACCT_BAL_LST_INQ_DTM`, `ACTV_YN`, `EXPI_DT`, etc.). Extend it with `CORC_BF_BAL` + `BANK_CERT_SEQ` columns if not already present (verify before adding — V13 schema has the core columns). |
| `NapasBankInfoRepository` + `NapasBankInfo` entity | [domain/banking/entity/NapasBankInfo.java](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/NapasBankInfo.java) | Bridges NAPAS code ↔ internal `BANK_CD`. Composer-scope but mentioned for completeness. |
| `BankCdResolver` (existing, in `domain/banking/`) | grep before reusing — used by Account Inquiry, Firm Banking, Contacts | Resolves `BANK_CD` → short name / long name via `TB_FIRM_BANK_CODE` and (for NAPAS-only sources) `NAPAS_BANK_INFO`. **Use this** instead of porting legacy `wb_get_bank_nm()`. |
| `TenantContext` | [common/tenant/TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | JWT-resolved tenant + user + product type. Replaces `SessionManager.getLoginUser`. |
| `PrdtType` enum | [common/enums/PrdtType.java](../../../../src/main/java/com/kosign/wecms/common/enums/PrdtType.java) | `F` / `M` / `H`. Use `PrdtType.fromCode(currentPrdtType)` for the HAENGSUNG gate — never compare to literal `"H"`. |
| Audit logger (existing, used by firm-banking-settings / contacts / chart-of-accounts) | `domain/audit/AuditLogger` or service-local writer (grep for `AUDIT_LOGS_INFM` writes) | Use this for the `own-account-transfer` audit row with the slug as `WSVC_ID`. |
| `BusinessException` + `GlobalExceptionHandler` | [common/exception/](../../../../src/main/java/com/kosign/wecms/common/exception/) | Throw `BusinessException("WBE_OWN_ACCOUNT_TRANSFER_*")` — never raw `String`. New codes registered in `messages.properties` first. |
| `ApiResponse<T>` envelope + global `SNAKE_CASE` Jackson strategy | [common/response/ApiResponse.java](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) + [config/JacksonConfig.java](../../../../src/main/java/com/kosign/wecms/config/JacksonConfig.java) | Wire-level — no per-DTO `@JsonProperty` needed. |

**Explicitly NOT used in own-account-transfer's wire path** (separate concern):
- `domain/banking/nxisas/NxisasGatewayClient` — Webcash NXISAS smart-gateway (HTTP API to a different system). Own-account-transfer talks to **local NXiSAS.exe** via the browser bundle, not to a Spring gateway client.
- `domain/banking/nxisas/BankScrapingService` — same: serves Account Inquiry refresh / Firm Banking. Do not call from this feature.

### 5.2 Frontend (`wecms-web`)

| Component | Path | What it does |
|---|---|---|
| `<AppShell>` + `<Header>` + `<Sidebar>` | [components/layout/](../../../../../wecms-web/components/layout/) | Surrounding chrome — page is dropped inside the existing `(dashboard)` route group. |
| `<PageHeader>` + `<PageActionButtons>` | [components/shared/](../../../../../wecms-web/components/shared/) | Title + toolbar (view-setting icon, export-excel button). |
| `<Pagination>` | [components/shared/Pagination.tsx](../../../../../wecms-web/components/shared/Pagination.tsx) | Page-size dropdown + nav arrows. |
| `<BankPopover>` / bank-filter dropdown | search `components/banking/` and `components/shared/` for the existing one used by Account Inquiry | Single-select bank chooser with "All Banks" option. Reuse, do not rebuild. |
| `<ViewSettingPopover>` + `useViewSetting()` hook | `components/shared/view-setting/` (or wherever the global registry lives) | Triggered from the toolbar icon; consumes the `SCREEN_IDS.OWN_ACCOUNT_TRANSFER` constant (new entry — see migration §6). |
| `useTableSort()` | shared hook | Maps click-on-header → `(sortField, sortDirection)` parameters. |
| `useApiData()` | [lib/api/useApiData.ts](../../../../../wecms-web/lib/api/useApiData.ts) | List-fetch wrapper used by every banking page. |
| `formatAmount()` / currency helpers | [lib/currency.ts](../../../../../wecms-web/lib/currency.ts) | Formats `BAL` / `REAL_AMT` / totals with locale + ccy. |
| `humanizeDatetime()` | [lib/datetime.ts](../../../../../wecms-web/lib/datetime.ts) | Renders `acct_bal_lst_inq_dtm` as relative time + raw timestamp. |
| Axios JWT client | [lib/axios.ts](../../../../../wecms-web/lib/axios.ts) | Auto-attaches Bearer token. |
| Shared NXiSAS bundle loader (new — see §7) | `lib/nxisas/` (proposed new module) | Vendored `<Script>` declarations for the immutable bundle. |

## 6. Existing scaffolding under the wrong slug — action item

The refactor already contains:

- `wecms-web/app/(dashboard)/banking/transfer-own-accounts/page.tsx`
- `wecms-web/components/banking/internal-transfer/TransferOwnAccountsClient.tsx` + siblings
- `wecms-api` `TransferController` with `GET /api/v1/banking/transfers/own-accounts` (STUB)

These were scaffolded under `transfer-own-accounts` before the workspace slug rule was tightened. The CLAUDE.md slug rule (kebab-case, matching the JEX slug **conceptually**, not the WSVC id literally) plus the user's briefing in this MR both pin the slug as **`own-account-transfer`**.

**Proposed cleanup (in scope for the Phase 2 backend MR that ships this feature):**

1. Rename `app/(dashboard)/banking/transfer-own-accounts/` → `app/(dashboard)/banking/own-account-transfer/`.
2. Rename `components/banking/internal-transfer/` → `components/banking/own-account-transfer/` (the existing `internal-transfer` folder name was holdover from when the team treated this as a "transfer between own accounts = internal transfer" — but legacy JEX has a separate `pymn` Internal Transfer (one-time, bulk, batch) module. Renaming clarifies the boundary.).
3. Rename / merge `TransferController` so the stub `GET /api/v1/banking/transfers/own-accounts` retires in favour of `GET /api/v1/banking/own-account-transfer/source-accounts`. The existing controller's other endpoints (if any) stay where they are.
4. Update `lib/api/banking.ts#fetchOwnAccounts` URL + return type.
5. Update wire types in `lib/types/api.ts` + mappers in `lib/api/mappers.ts`.

**Do NOT do this cleanup in Phase 1 (design only).** Flag it in the Phase 2 backend MR description.

## 7. nxisas bundle inventory — classified

All paths below are sources under [`WECMS DEV/WECMS_STATIC/web/nxisas/`](../../../../../WECMS%20DEV/WECMS_STATIC/web/nxisas/). The refactor target path is `wecms-web/public/nxisas/` — vendored **verbatim** (copy bytes; do not rewrite, lint, format, transpile, or bundle through webpack).

### 7.1 Engine binary

| Source path | Classification | Refactor target | Notes |
|---|---|---|---|
| `nxisas/file/NXiSAS.exe` | **ACTIVE** | `wecms-web/public/nxisas/file/NXiSAS.exe` | The current vendor-signed engine. The Install CTA points here. |
| `nxisas/file/old/NXiSAS_2020.3.16.0.exe` | **LEGACY** | not copied | Archive; do not link from refactor UI. |
| `nxisas/file/old/NXiSAS_2024.12.23.0.exe` | **LEGACY** | not copied | Archive. |

### 7.2 Browser-side JS bundle (load-order critical)

| Source path | Classification | Refactor target | Load via |
|---|---|---|---|
| `nxisas/js/platform.js` | **ACTIVE** | `public/nxisas/js/platform.js` | Next.js `<Script src="/nxisas/js/platform.js" strategy="beforeInteractive">` — must run before any UA detection |
| `nxisas/js/swfobject.js` | **ACTIVE** (Flash detector, retained for parity) | `public/nxisas/js/swfobject.js` | `<Script strategy="afterInteractive">` |
| `nxisas/js/web_socket.js` | **ACTIVE** | `public/nxisas/js/web_socket.js` | `<Script strategy="afterInteractive">`. Requires `window.WEB_SOCKET_SWF_LOCATION` set **before** this script executes — see frontend doc §5.2 |
| `nxisas/js/isasscaping.js` | **ACTIVE** | `public/nxisas/js/isasscaping.js` | `<Script strategy="afterInteractive">` |
| `nxisas/js/nxisas_api_v2.js` | **ACTIVE** (loaded by some sibling pages — keep for completeness; OAT page itself does NOT include this script — see inc_wabooks_nxisas_v2.jsp:41-47) | `public/nxisas/js/nxisas_api_v2.js` | Not loaded by Own Account Transfer's page; reserved for the sibling refactor pages that consume the API-only bundle |
| `nxisas/js/nxisas_v2.js` | **ACTIVE** — the Own Account Transfer page's primary engine wrapper | `public/nxisas/js/nxisas_v2.js` | `<Script strategy="afterInteractive">`; depends on jQuery 3.3.1, platform, swfobject, web_socket, isasscaping all already loaded |
| `nxisas/js/jquery-3.3.1.min.js` | **ACTIVE** (bundle requires) | `public/nxisas/js/jquery-3.3.1.min.js` | `<Script strategy="beforeInteractive">` — set `window.jQuery` before any of the engine scripts run. **The Next.js app must NOT depend on this jQuery; it exists only for the bundle.** |
| `nxisas/js/json2.js` | **ACTIVE** (legacy polyfill the bundle still references) | `public/nxisas/js/json2.js` | `<Script strategy="beforeInteractive">` |
| `nxisas/js/jquery-1.7.1.min.js` | **LEGACY** | not copied | Pre-jQuery-3 fallback; the engine is bound to 3.3.1 now |
| `nxisas/js/jquery-1.9.1.min.js` | **LEGACY** | not copied | Same |
| `nxisas/js/json3.min.js` | **LEGACY** | not copied | Superseded by json2.js (yes, that ordering is intentional — vendor pinned to json2) |
| `nxisas/js/nxisas.js` | **LEGACY** | not copied | Pre-v2 engine wrapper; retired by `nxisas_v2.js` |
| `nxisas/js/nxisas_api.js` | **LEGACY** for OAT (still ACTIVE for some sibling pages — see inc_wabooks_nxisas_v2.jsp:46) | `public/nxisas/js/nxisas_api.js` (vendored, but NOT loaded by OAT page) | Not used by Own Account Transfer; kept under `public/nxisas/` for the sibling pages that need the API-only path |
| `nxisas/js/nxisas_v2_u000.js` | **LEGACY** | not copied | Migration-helper variant; not referenced by any active page |
| `nxisas/js/isasscaping.js20200506` | **LEGACY** (filename literally includes a date — archive copy) | not copied | — |
| `nxisas/js/isasscaping_u000.js` | **LEGACY** | not copied | — |

### 7.3 Flash transport

| Source path | Classification | Refactor target | Notes |
|---|---|---|---|
| `nxisas/websocket.swf` | **ACTIVE** (parity-required fallback) | `wecms-web/public/nxisas/websocket.swf` | Referenced by `web_socket.js` via `WEB_SOCKET_SWF_LOCATION`. No modern browser will load it, but the bundle expects the path to resolve. **Keep.** |

### 7.4 Install / health-check + assets

| Source path | Classification | Refactor target | Notes |
|---|---|---|---|
| `nxisas/InstallCheck.html` | **ACTIVE** | `wecms-web/public/nxisas/InstallCheck.html` | "Engine not installed" CTA links here; the page itself is a static HTML — no Next.js wrapper. |
| `nxisas/css/process_manager.css` | **ASSET** (ACTIVE) | `wecms-web/public/nxisas/css/process_manager.css` | Used by the engine's render-into-page modals (OTP / captcha / password prompt). Loaded by `nxisas_v2.js` runtime. |
| `nxisas/css/은행_거래내역조회.html` | **LEGACY** (archived sample HTML — Korean filename, not loaded at runtime) | not copied | Reference sample; ignore. |
| `nxisas/img/**` (all subfolders: `bg/`, `btn/`, `bul/`, `ico/`, `loading.gif`, `img_cert.gif`, `bg_close.gif`, `bul1.gif`) | **ASSET** | `wecms-web/public/nxisas/img/**` | Engine modal artwork. Copy whole tree verbatim. |

### 7.5 Loading rule (refactor)

The bundle is loaded **only on routes that need it** — `wecms-web/app/(dashboard)/banking/own-account-transfer/layout.tsx` (and a small set of sibling routes such as `einv_0001_000/001` once those refactor) declares the `<Script>` tags. The shared dashboard layout must NOT pull the bundle globally — every other page would carry a useless ~500 KB of vendor JS.

## 8. Risk register

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-1 | NXiSAS engine signature breaks if any byte of the bundle is touched (lint / format / transpile / re-bundle) | High if a CI tool slips through | Critical — engine refuses to talk to a tampered bundle | Place `public/nxisas/**` behind a CI guard that blocks any diff on those files; document in CLAUDE.md the "vendored verbatim" rule with a hash manifest if needed |
| R-2 | Next.js `<Script strategy="beforeInteractive">` does not guarantee jQuery + json2 are loaded before `nxisas_v2.js`. Bundle relies on classic `<script>` synchronous ordering | Medium | High — runtime "jQuery is not defined" error breaks the Transfer button | Use Next.js' `next/script` with explicit `onLoad` chaining, OR fall back to a single inline `<Script src="/nxisas/bootloader.js" strategy="beforeInteractive">` that synchronously injects all bundle scripts via `document.write` (legacy pattern). Pick during Phase 2; spike both. |
| R-3 | The engine renders modal prompts (OTP / captcha / password) by manipulating the DOM directly via the bundle's jQuery — those nodes collide with React's reconciler | Medium | High — React may un-render the engine modal | Render the engine modal targets into a **React portal sibling** that React leaves untouched (`useRef` + `createPortal` to a `<div id="nxisas-mount">` outside the page tree). Document in frontend §6. |
| R-4 | The legacy page uses `sessionStorage` keys `SELECTED_BANK` / `SELECTED_ACCOUNT` to hand off row → composer. Refactor's modal stack passes props instead — risk of stale sessionStorage if the composer reads from it accidentally | Low | Medium | Composer must explicitly receive `(bankCd, acctNo, bankCertSeq)` via props; do not touch sessionStorage. Add a test that the route mount doesn't read those keys. |
| R-5 | `ACCT_AUTH_INFO` is not yet declared in the refactor Flyway tree (V13 declares `ACCT_MSTR` and friends but not the auth pivot table). Without it, the USER-mode filter silently returns every row | Certain (V13 audit confirms absence) | High — USER-mode is wide open without it | Add a new Flyway migration declaring `ACCT_AUTH_INFO` column-for-column with DEV. See database doc §3. |
| R-6 | `ACCT_MSTR` in V13 omits `CORC_BF_BAL`, `BANK_CERT_SEQ`, `HAND_REG_YN`, `PRN_SEQ` — the balance-refresh code legacy patches and the engine login reads | High (must verify against V13 + any later ACCT_MSTR migration) | Medium — patch will fail at JPA validate-time | Database doc §4 audits the gap and proposes a follow-up migration that adds the missing columns. Verify against the existing schema before writing the migration. |
| R-7 | HAENGSUNG `PRDT_TYPE='H'` saving-account gating (`EXPI_DT >= today AND BAL > 0`) silently changes the row count between feature releases as accounts mature | Low | Low | Document the gate in the feature spec (§3) and add an integration test against a HAENGSUNG seed tenant that includes (a) a current account, (b) a saving account expiring today, (c) an expired saving account. |
| R-8 | The legacy `wb_get_translate_text(...)` Postgres function used by `NAPAS_BANK_REMIT_INFO_R002` may not exist in the refactor DB | High (composer-scope, not OAT page itself) | Medium | Composer feature MR adds the function or replaces the call with a Java translator. Out of scope for this feature. |
| R-9 | **Audit-everywhere policy diverges from refactor precedent.** Per OQ-4 (PO 2026-05-18) every endpoint writes to `AUDIT_LOGS_INFM` — including `GET /source-accounts`, which is called on every page mount + every bank-popover change + every sort click. Audit-table growth on this slug will dwarf other features' rows. | High once the page sees real traffic | Medium — table bloat, query slowdown on the audit-log read endpoints | Index `AUDIT_LOGS_INFM` on `(WSVC_ID, REG_DTM)` if not already present; document the per-feature exception in CLAUDE.md once the change is merged so future audit-table sizing assumes this row volume; consider per-feature retention policy if the row count crosses 1M locally. |
| R-10 | `engine_version` / `engine_vendor` strings come from the client and could be spoofed by a tampered bundle | Low — vendor signs the engine binary, but the JS could be rewritten | Low | Strings are **audit data only**, never a security boundary. The audit row's value is its existence + timing + reported version, not as a trust signal. Document that a mismatch between the audited version and the deployed binary should trigger a security review, not an automatic action. |

## 9. Phase ordering inside the migration playbook

Per [docs/refactor-wecms/MIGRATION_PLAYBOOK.md](../MIGRATION_PLAYBOOK.md) — the playbook's step 4–5 (backend slice) + step 6 (frontend slice) for **own-account-transfer** decompose as:

1. **Phase 1 — design (this MR).** Five docs land + summary table to chat. No code.
2. **Phase 1.5 — Flyway.** New migrations: `ACCT_AUTH_INFO` table, `ACCT_MSTR` column adds (CORC_BF_BAL, BANK_CERT_SEQ, HAND_REG_YN, PRN_SEQ if missing), `SYS_SCR_OPTION_WECMS` seed for `web_svc_id='own-account-transfer'`. See database doc §3–§5.
3. **Phase 2 — backend.** Entities → repositories → services → controllers → DTOs → bean validation → audit logging. New error codes registered. See backend doc.
4. **Phase 3 — frontend.** Wire types + mappers → hooks → table + popover + composer-launch-button → nxisas bundle loader. See frontend doc.
5. **Phase 4 — composer.** Sibling feature MR ports the `trns_0001_007` layer popup. Out of scope for this feature.
6. **Phase 5 — parity audit.** Side-by-side row count + balance-refresh outcome comparison against a JEX DEV snapshot, recorded in docs/refactor-wecms/audits/own-account-transfer-parity.md (Needs Confirmation) (new).
