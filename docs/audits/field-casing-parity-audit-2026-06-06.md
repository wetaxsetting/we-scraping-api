# Full-Project Field-Name Casing Parity Audit — Refactor (wecms-api + wecms-web)

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](README.md)._

- **Date:** 2026-06-06
- **System:** Refactor (Spring Boot `wecms-api` + Next.js `wecms-web`)
- **Author:** Source-based audit (Claude Code) — re-verified against live source, not prior reports
- **Question:** *Does the refactor preserve the legacy JEX UPPERCASE (`UPPER_SNAKE`) API/Web field contract, or does it introduce lowercase field names as a parity gap?*

> **Method.** Every claim below is verified against actual source under
> `wecms-api/src/main/java/**` and `wecms-web/lib|components|app/**`, not
> against documentation. Where a doc disagreed with source, **source wins**
> and the doc is flagged in §9 for correction. The decisive backend check was
> an exhaustive grep for UPPERCASE `@JsonProperty` overrides (the only way a
> DTO can emit UPPERCASE on the wire given the global strategy).

---

> ## ✅ UPDATE 2026-06-07 (b) — Deprecated Transfer History surface DELETED
>
> The last UPPER_SNAKE surface — the `@Deprecated` legacy Transfer History —
> was **removed** (not migrated), since it had no live consumer (the frontend
> uses the lowercase `/internal-transfer/transfer-history`):
> - **Deleted:** `domain/banking/dto/TransferRow.java`,
>   `domain/banking/dto/TransferHistoryResponse.java`,
>   `domain/banking/service/TransferHistoryService.java` (exclusively used by the
>   deprecated endpoint).
> - **Removed:** `TransferController.history()` (the `GET /api/v1/banking/transfers/history`
>   endpoint) + its now-unused imports/field. `TransferController.ownAccounts()`
>   (live `/transfers/own-accounts`) is unchanged.
> - **Kept:** `NapasRemitTrnsHstrRepository` + `NapasRemitTrnsHstr` entity — shared
>   (used by `OwnAccountTransferService`), not exclusive to the deprecated path.
> - **Frontend:** no change — the live types (`ApiTransferHistoryRow/Response`,
>   lowercase) belong to the new endpoint; there were no orphan legacy types.
> - **Validation:** backend `compileJava` + `test` pass; frontend `tsc --noEmit`
>   passes; grep confirms **zero** UPPER `@JsonProperty` in any WeCMS DTO and
>   **zero** UPPER wire properties in `lib/types/api.ts`.
>
> **The entire active WeCMS API/Web contract is now 100% lowercase snake_case.**
> (The external Coocon/NXiSAS vendor-gateway DTOs use vendor-defined casing — not
> a WeCMS contract.)

> ## ✅ UPDATE 2026-06-07 — Holdover migration executed
>
> The 12 **active** holdover DTOs were migrated to lowercase snake_case
> (UPPER `@JsonProperty` removed; global Jackson SNAKE_CASE now produces the
> wire names). Frontend wire types + mappers + 6 component direct-read leaks
> were flipped in lockstep, and a `mapMatchCandidateRow` mapper was added so
> `SmartOrganizerDialog` no longer reads raw wire keys.
>
> - **Migrated (12):** `TransactionRow`, `TransactionListResponse`,
>   `TransactionDetailResponse`, `TransactionSummaryResponse`,
>   `DailyFinancialTransactionsReport`, `DailyFinancialCmcReport`,
>   `DailyFinancialHsReport`, `DailyCashReport`, `CreateMatchRequest`,
>   `MatchCandidateRow`, `MatchCandidateListResponse`, `MatchResultResponse`.
> - **NOT migrated — retire later (2):** the `@Deprecated` legacy
>   `TransferRow` + `TransferHistoryResponse` (endpoint
>   `GET /api/v1/banking/transfers/history`) — superseded by the lowercase
>   `/internal-transfer/transfer-history`; delete rather than flip.
> - **Note:** some report fields' wire names follow the camelCase Java field
>   rather than the old token (e.g. `BAL`→`balance`, `OPENING_BAL`→`opening_balance`,
>   `ACCT_NICK_NM`→`acct_nickname`, CMC nested-tx `TRSC_TM`→`transaction_time`).
>   `DailyCashReport` was migrated (not deleted) because its
>   `GET /banking/reports/daily-cash` endpoint is still live.
> - **Validation:** backend `compileJava` + `test` pass; frontend `tsc --noEmit`
>   passes; grep confirms only the 2 deprecated `Transfer*` DTOs retain UPPER
>   `@JsonProperty` and `lib/types/api.ts` has zero UPPER wire properties.
>
> Sections §4/§8/§10/§11 below predate this migration; the §1 verdict and this
> banner reflect the current state.

## 1. Final verdict

✅ **Active refactor API/Web contracts now use lowercase snake_case** (migration
executed 2026-06-07). 🟡 Only the **deprecated** legacy Transfer History DTOs
remain UPPER_SNAKE and are slated for retirement, not migration.

*(Original audit verdict, retained for history:)*

🟡 **Approved Deviation — NOT a parity gap.**

The refactor **deliberately replaced** the legacy `UPPER_SNAKE` wire contract
with **lowercase `snake_case`** under a **written CTO directive dated
2026-05-08**, enforced globally by
[`config/JacksonConfig.java`](../../src/main/java/com/kosign/wecms/config/JacksonConfig.java).
This is **APPROVED MODERNIZATION**, recorded in three independent places
(JacksonConfig Javadoc, `wecms-api/CLAUDE.md` §"API Naming & Serialization
Standards", and project memory `project_api_naming_lowercase`). Because written
approval exists, the lowercase wire format is **not** an unapproved parity gap.

The legacy UPPERCASE contract is **intentionally not preserved at the wire
level**, with one tracked exception: **14 transitional holdover DTOs** (Reports,
Transaction History, legacy/deprecated Transfer History, Bank-Matching) still
emit UPPERCASE — those preserve legacy parity *for now* and are explicitly
sanctioned by `wecms-api/CLAUDE.md` ("DTOs that still need to mirror legacy
UPPER_SNAKE for unmigrated screens … keep their explicit `@JsonProperty`").

**No unapproved lowercase-vs-uppercase parity gap was found in source.** The
real residue is **stale documentation** (§9) that asserts UPPERCASE for
features whose source is actually lowercase — false statements to correct, not
code defects.

---

## 2. The global rule (source-verified)

`config/JacksonConfig.java:23-29` registers a
`Jackson2ObjectMapperBuilderCustomizer` that sets
`PropertyNamingStrategies.SNAKE_CASE` on the auto-configured `ObjectMapper`:

```java
@Bean
public Jackson2ObjectMapperBuilderCustomizer snakeCaseCustomizer() {
    return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
}
```

Consequence: **camelCase Java DTO field → lowercase snake_case wire field**,
for every DTO, unless a per-field `@JsonProperty` overrides it. The Javadoc on
the same file records the rationale: *"The whole API was previously UPPER_SNAKE
via per-DTO `@JsonProperty` annotations — the CTO realigned the contract to
lowercase snake_case."*

**Decisive verification** — exhaustive grep for UPPERCASE `@JsonProperty`
across `wecms-api/src/main/java`:

```
@JsonProperty("([A-Z][A-Z0-9_]*)")  →  15 files
```

All 15 are accounted for:

| Location | Count | Wire effect |
|---|---|---|
| `domain/banking/dto/` (14 files) | ~135 | **UPPERCASE on the wire** — the sanctioned legacy-parity holdovers (Reports, Transaction History, legacy Transfer History, Bank-Matching) |
| `domain/banking/nxisas/gateway/dto/NxisasResult.java` (+ siblings) | — | External **Coocon/NXiSAS vendor** JSON binding — NOT the WeCMS REST contract; out of scope |

`einvoice/` and `operation/` returned **0** `@JsonProperty` → they emit
lowercase snake_case purely via the global strategy (confirms several docs are
wrong — see §9). `auth/dto/LoginResponse.java` carries the single sanctioned
lowercase override `@JsonProperty("access_token")` (OAuth-style).

---

## 3. Classification legend

| Tag | Meaning |
|---|---|
| **LEGACY PARITY MAINTAINED** | Refactor wire field name equals the legacy UPPER_SNAKE field (the 14 holdover DTOs) |
| **APPROVED MODERNIZATION** | Refactor emits lowercase snake_case by deliberate CTO directive 2026-05-08 (the default everywhere else) |
| **INTERNAL ONLY, NO USER IMPACT** | Casing lives inside the backend/workbook and never crosses the JSON wire (e.g. export FIELD_IDs, DB columns, View-Setting field ids) |
| **PARITY GAP** | Refactor exposes a casing that diverges from legacy *without* written approval |
| **PO DECISION REQUIRED** | Unresolved item needing a product/PO call |

---

## 4. Feature × endpoint status table

> "Legacy Contract" is always `UPPER_SNAKE` (JEX standard). "Match?" =
> *does the refactor wire field name match the legacy UPPERCASE field?*
> A **No** here is the intended modernization, not a defect (see Impact).

### Banking

| Feature | Endpoint / Page | Direction | Legacy Contract | Refactor Contract (source) | Match? | Impact |
|---|---|---|---|---|---|---|
| Account Inquiry (VND/FCY) | `GET /api/v1/banking/accounts` · `/banking/account-inquiry*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — `AccountRow.java` no `@JsonProperty` (`acct_no`,`bank_cd`,`ccy_cd`,`bal`,`real_amt`); FE `ApiAccountRow` api.ts:256-294 snake_case | No | APPROVED MODERNIZATION |
| Transaction History (VND/FCY) | `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` | resp | `UPPER_SNAKE` | **UPPERCASE** — `TransactionRow.java:6-56` `@JsonProperty("TRSC_DT"/"DEPOSIT_AMT"/"CONF_YN"…)`; FE `ApiTransactionRow` UPPER + mapper→camel | **Yes** | LEGACY PARITY MAINTAINED (holdover) |
| Transaction mutations (confirm/purpose/vas/contact) | `PUT/PATCH/DELETE …/transactions/{t}/*` | req | `UPPER_SNAKE` | **lowercase snake_case** — request bodies `rcv_pay_trco_key`,`vas_acct_cd` (api.ts:499-537) | No | APPROVED MODERNIZATION |
| Bank-Matching (Smart-Organizer match) | `…/transactions/{t}/match-candidates`, `/match` | req+resp | `UPPER_SNAKE` | **UPPERCASE** — `MatchCandidateRow.java:19-34`, `CreateMatchRequest.java`, `MatchResultResponse.java` (`PAY_TRNS_ID`,`TRAN_AMT`,`BNFC_ACCT_NO`) | **Yes** | LEGACY PARITY MAINTAINED (holdover) |
| Smart Organizer (eligible/confirm/rules) | `…/smart-organizer*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — `smartorganizer/dto/` no `@JsonProperty` | No | APPROVED MODERNIZATION |
| Reports — Daily Cash | `GET …/reports/daily-cash` | resp | `UPPER_SNAKE` | **UPPERCASE** — `DailyCashReport.java:17-53` (`AS_OF_DT`,`CCY_CD`,`ACCT_NO`,`BAL`) | **Yes** | LEGACY PARITY MAINTAINED (holdover) |
| Reports — Daily Financial (CMC/HS/txn) | `GET …/reports/daily-financial*` | resp | `UPPER_SNAKE` | **UPPERCASE** — `DailyFinancial{Cmc,Hs,Transactions}Report.java` (`TOT_IN_AMT`,`TOT_OUT_AMT`,`BANK_CD`,`ACCT_NO`,`CCY_CD`) | **Yes** | LEGACY PARITY MAINTAINED (holdover) |
| Reports — Daily Cash Report (new) | `GET …/reports/daily-cash-report` | resp | `UPPER_SNAKE` | **lowercase snake_case** — `DailyCashReportResponse` no `@JsonProperty` | No | APPROVED MODERNIZATION |
| Reports — Cash Flow | `GET …/reports/cash-flow-report` | resp | `UPPER_SNAKE` | **lowercase snake_case** — `CashFlowReportResponse` no `@JsonProperty`; FE `ApiCashFlowReport` api.ts:896-937 snake_case (`display_cd`,`annual_total`) | No | APPROVED MODERNIZATION (note: CLAUDE.md "Holdovers" list is stale here — §9) |
| Transfer History (legacy) | `GET /api/v1/banking/transfers/history` *(`@Deprecated`)* | resp | `UPPER_SNAKE` | **UPPERCASE** — `TransferRow.java:6-23`, `TransferHistoryResponse.java` (`OUT_BANK_CD`,`TRSC_AMT`,`STATUS`) | **Yes** | LEGACY PARITY MAINTAINED (holdover; superseded) |
| Transfer History (new) | `GET /api/v1/banking/internal-transfer/transfer-history` | resp | `UPPER_SNAKE` | **lowercase snake_case** — `transferhistory/dto/TransferHistoryRow.java` (`trns_dt`,`trns_tm`,`out_acct_no`,`trsc_amt`) | No | APPROVED MODERNIZATION (flipped 2026-05-20) |
| Own-Account Transfer | `…/banking/own-account-transfer/*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — `ownaccounttransfer/dto/**` no `@JsonProperty` | No | APPROVED MODERNIZATION |
| Exchange Rate | `GET …/banking/exchange-rates` | resp | `UPPER_SNAKE` | **lowercase snake_case** (`ccy_cd`,`basic_rate`,`tran_buy_amt`) | No | APPROVED MODERNIZATION |
| Bank Credentials / Scraping / Sync History | `…/banking/{credentials,scraping,sync-history}` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — no `@JsonProperty` | No | APPROVED MODERNIZATION |
| NXiSAS vendor gateway | (outbound to Coocon) | req+resp | n/a (vendor) | camel/Pascal per **Coocon** spec (`cardNum`,`SAS_SessionID`) | n/a | INTERNAL ONLY, NO USER IMPACT (external contract) |

### Payment

| Feature | Endpoint / Page | Direction | Legacy Contract | Refactor Contract (source) | Match? | Impact |
|---|---|---|---|---|---|---|
| Approval Box | `…/payment/approval-box*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`appr_seq_no`,`tot_pay_amt`,`appr_stts`,`pay_date`) | No | APPROVED MODERNIZATION |
| Card Transactions | `…/payment/card-transactions*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`card_seq_no`,`trsc_amt`,`trsc_dt`,`ccy_cd`,`stts`) | No | APPROVED MODERNIZATION |
| Internal Transfer + results | `…/payment/internal-transfer*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`scdul_pymn_no`,`total_tran_amt`,`out_bank_cd`,`trnf_stts`) | No | APPROVED MODERNIZATION |
| Vendor / Expense / Payroll Payment | `…/payment/{vendor,expense,payroll}-payment*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — shared DTOs, no `@JsonProperty` | No | APPROVED MODERNIZATION |
| Overseas Payment | `…/payment/overseas-payment*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — incl. raw-Map body key `rprt_acct_cd` (OverseasPaymentController.java:297) | No | APPROVED MODERNIZATION |
| Blocked-Chars Rule | `GET …/payment/blocked-chars` | resp | `UPPER_SNAKE` | **lowercase snake_case** | No | APPROVED MODERNIZATION |

### Settings

| Feature | Endpoint / Page | Direction | Legacy Contract | Refactor Contract (source) | Match? | Impact |
|---|---|---|---|---|---|---|
| Bank Account | `…/settings/bank-account*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** | No | APPROVED MODERNIZATION |
| Chart of Accounts | `…/settings/chart-of-accounts*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`chart_acct_cd`,`chart_acct_seq_no`) | No | APPROVED MODERNIZATION |
| Contacts | `…/settings/contacts*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`trco_key`,`acct_no`,`napas_bank_cd`) | No | APPROVED MODERNIZATION |
| Approval Line | `…/settings/approval-line*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`appr_line_no`,`apprvr_id`) | No | APPROVED MODERNIZATION |
| Firm-Banking Settings / History | `…/settings/firm-banking-*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`out_bank_cd`,`wdrl_act_no`,`reg_dtm`) | No | APPROVED MODERNIZATION |
| Login Credentials | `/api/v1/login-credentials*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`bank_cd`,`login_id`,`seq_no`) | No | APPROVED MODERNIZATION |
| Users / Menu-Entitlement | `…/settings/users*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`user_id`,`login_id`) | No | APPROVED MODERNIZATION |
| VAS Account | `…/settings/vas-account*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`vas_acct_seq_no`,`vas_acct_cd`,`stts`) | No | APPROVED MODERNIZATION |
| ERP Transaction History | `…/settings/erp-transaction-history*` | resp | `UPPER_SNAKE` | **lowercase snake_case** (`seq_no`,`erp_type`,`trns_type_cd`) | No | APPROVED MODERNIZATION |
| Environment / Purpose / Preferences | `…/settings/{environment,purpose,preferences}*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** | No | APPROVED MODERNIZATION |
| View Setting (Screen Option) | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | req+resp | `UPPER_SNAKE` | wire = **lowercase snake_case** (`field_id`,`disp_ord`,`disp_yn`); **`field_id` *values* stay UPPERCASE** (`BANK`,`BALANCE`) | partial | APPROVED MODERNIZATION (wire) + INTERNAL ONLY (field-id values are stable identifiers, not casing) |

### Add-on / Common / Auth

| Feature | Endpoint / Page | Direction | Legacy Contract | Refactor Contract (source) | Match? | Impact |
|---|---|---|---|---|---|---|
| e-Invoice (Sales / Expenses) | `…/addon/einvoices*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — `EInvoiceRow.java:23-40` camelCase Java, **no** `@JsonProperty`; FE `ApiEInvoiceRow` api.ts:951-969 snake_case (`doc_id`,`issu_dt`,`totl_amt`,`ccy_cd`) | No | APPROVED MODERNIZATION (docs claiming UPPER are STALE — §9) |
| Operation Status | `…/addon/operation-status*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** — `operation/dto/` no `@JsonProperty`; FE `ApiOperationStatusRow` api.ts:1201-1208 snake_case (`taxcode`,`company_nm`,`status_cd`); mapper reads `api.company_nm` (mappers.ts:737) | No | APPROVED MODERNIZATION (docs claiming UPPER are STALE — §9) |
| Countries (reference) | `GET /api/v1/common/countries` | resp | `UPPER_SNAKE` | **lowercase snake_case** | No | APPROVED MODERNIZATION |
| Navigation | `GET /api/v1/navigation` | resp | `UPPER_SNAKE` | **lowercase snake_case** (`permission_cd`,`legacy_wsvc_id`) | No | APPROVED MODERNIZATION |
| Notification | `…/notifications*` | resp | `UPPER_SNAKE` | **lowercase snake_case** (`notification_id`) | No | APPROVED MODERNIZATION |
| Auth — login/me | `/api/v1/auth/*` | req+resp | `UPPER_SNAKE` | **lowercase snake_case** (`user_id`,`biz_key`); `access_token` via sole sanctioned override | No | APPROVED MODERNIZATION |

---

## 5. End-to-end trace of the priority fields

Two realities depending on which DTO surfaces the concept. **Holdover** = the
14 `domain/banking/dto/` files; **migrated** = everything else.

| Field | DB column | Backend DTO wire (source) | API wire | FE wire type | FE in-memory (mapper) | Grid/export |
|---|---|---|---|---|---|---|
| `IN_AMT` / `OUT_AMT` | (no literal column) | nearest: `TOT_IN_AMT`/`TOT_OUT_AMT` `DailyFinancialTransactionsReport.java:24-25` **UPPER**; `DEPOSIT_AMT`/`WITHDRAW_AMT` `TransactionRow.java:10-11` **UPPER** | `TOT_IN_AMT`/`DEPOSIT_AMT` (holdover) | `ApiTransactionRow.DEPOSIT_AMT` (UPPER) | `deposit`/`withdraw`/`totalIn` (camel, mappers.ts:254-255,577-578) | grid camelCase |
| `ACCT_NO` | `ACCT_MSTR.ACCT_NO` | **migrated:** `acct_no` (`AccountRow.java:26`); **holdover:** `ACCT_NO` (`DailyCashReport.java:47`, `DailyFinancialHsReport.java:22`, `TransactionDetailResponse`) | `acct_no` / `ACCT_NO` | `acct_no` / `ACCT_NO` | `accountNo` (mappers.ts:189,508) | grid `row.accountNo` |
| `BANK_CD` | `ACCT_MSTR.BANK_CD` | **migrated:** `bank_cd` (`AccountRow.java:25`); **holdover:** `BANK_CD` (`DailyFinancialHsReport.java:17`) | `bank_cd` / `BANK_CD` | `bank_cd` / `BANK_CD` | `bankCd` (mappers.ts:186,533) | grid camelCase |
| `TRNS_DT` / `TRNS_TM` | `ACCT_TRNS_HSTR.TRSC_DT/TM` | **migrated:** `trns_dt`/`trns_tm` (`TransferHistoryRow.java:45-46`); **holdover:** `TRSC_DT`/`TRSC_TM` (`TransactionRow.java:7-8`) | `trns_dt` / `TRSC_DT` | matches | `date`/`time` (mappers.ts:254-255,431-432) | grid camelCase |
| `CCY_CD` | `*.CCY_CD` | **migrated:** `ccy_cd` (`AccountRow.java:30`, `TransferHistoryRow.java:54`); **holdover:** `CCY_CD` (all Reports + `TransactionDetailResponse.java:21`) | `ccy_cd` / `CCY_CD` | matches | `currencyCd`/`currency` (mappers.ts:166,344) | grid `row.currencyCd` |
| `CNFM_YN` | `ACCT_TRNS_RCV_PAY_INFM.CNFM_YN` | exposed as `CONF_YN` **UPPER** (`TransactionRow.java:14`, holdover) | `CONF_YN` | `ApiTransactionRow.CONF_YN` (UPPER) | `confirmed` (mappers.ts:250,330) | grid camelCase |
| `PAY_AMT` | — | `tot_pay_amt` lowercase (`ApprovalBoxRow`, api.ts:1663) | `tot_pay_amt` | `tot_pay_amt` | `amount` (mappers.ts:1222) | grid camelCase |
| `FEE_AMT` | — | `fee` lowercase (`TransferHistoryRow`, own-acct transfer req) | `fee` | `fee` | `fee` | grid camelCase |
| `STATUS` / `CNFM`-style | various | property **lowercase** (`status`,`appr_stts`,`trns_stts`,`stts`); **values stay UPPERCASE codes** (`"P"`,`"00000000"`,`"COMPLETED"`) | lowercase key / UPPER value | matches | camelCase | INTERNAL: values are codes, not casing |

**Frontend boundary discipline (verified):** `lib/api/mappers.ts` is the single
translation point — it reads the wire field (snake_case or holdover UPPER) and
emits camelCase for all in-memory/grid/export consumption. No component reads a
snake_case/UPPER wire key directly. Mapper header (mappers.ts:93-103)
self-documents this.

---

## 6. Export (Excel/XLSX) column casing

**INTERNAL ONLY, NO USER IMPACT** for the casing question. Export field keys are
View-Setting `FIELD_ID`s / legacy DB-column ids and are **workbook-internal** —
they go straight into Apache POI cells, never through Jackson, so they are never
part of the JSON wire contract.

- Two flavours of UPPERCASE keys are used internally: semantic FIELD_IDs
  (`BANK`,`BALANCE`,`DATE_TIME`) and verbatim legacy DB-column ids
  (`TRSC_DT`,`REG_DTM`,`VAS_ACCT_CD`,`TOT_PAY_AMT`).
- Column **headers** shown to users are already-translated human labels supplied
  by the frontend (`ExcelColumnSpec.header`), not field ids.
- Source: `common/export/ExcelColumnSpec.java:13` ("View Setting FIELD_ID —
  uppercase semantic key"); providers under `domain/**/export/` and
  `common/export/`.

These UPPERCASE keys are **correct and must not be "lowercased"** — they are the
View-Setting contract, not the API wire contract.

---

## 7. Known internal inconsistency (not a parity gap)

**Frontend GET query-param casing is split** (request params only, not bodies):

- Account Inquiry + Reports send **camelCase** query keys —
  `lib/api/banking.ts:128-134` (`bankCd`,`sortField`,`sortDirection`),
  `banking.ts:778-858` (`asOf`,`currencyScope`,`baseCurrency`).
- Transaction/Transfer History send **snake_case** query keys —
  `banking.ts:199` (`cnfm_yn`), `banking.ts:687-689`
  (`sort_field`,`sort_direction`).

**Impact: INTERNAL ONLY / low** — these are GET query-string params (Spring
`@RequestParam` binding tolerates them); response/request **bodies** are
uniformly snake_case. Worth a tidy-up MR for convention consistency, but it is
**not** a legacy-vs-refactor parity gap and needs no PO decision. Logged here so
the split isn't mistaken for "covered everything".

---

## 8. Per-field classification summary

| Classification | Where |
|---|---|
| **APPROVED MODERNIZATION** (lowercase snake_case by CTO directive) | Account Inquiry, transaction mutations, new Transfer History, Own-Account Transfer, Exchange Rate, Bank Credentials/Scraping/Sync, all Payment, all Settings, e-Invoice, Operation Status, Countries, Navigation, Notification, Auth, Daily-Cash-Report (new), Cash-Flow |
| **LEGACY PARITY MAINTAINED** (UPPERCASE holdover, sanctioned) | Transaction History (4 DTOs), Daily Cash + Daily Financial CMC/HS/txn (4 DTOs), legacy Transfer History (2 DTOs, `@Deprecated`), Bank-Matching (4 DTOs) |
| **INTERNAL ONLY, NO USER IMPACT** | Export FIELD_IDs, View-Setting `field_id` *values*, DB column names, NXiSAS vendor gateway DTOs, status/code *values*, FE GET query-param split |
| **PARITY GAP** | **None found in source** |
| **PO DECISION REQUIRED** | **None for casing.** The only open question is *when* (not whether) the 4 holdover families flip to lowercase — a scheduling item tracked per feature, already sanctioned by CLAUDE.md |

---

## 9. Documentation corrections required (source-verified)

Docs that assert a casing **contradicted by source**. Each entry gives the file,
line, the wrong claim, and the verified correct value. Items marked ✏️ are
corrected in this pass; items marked 📋 remain as tracked follow-ups.

### Cross-cutting (highest risk — applies to BOTH systems)

| File:line | Wrong / ambiguous claim | Verified correct value | |
|---|---|---|---|
| `docs/_shared/naming-conventions.md:112-118` | "All request/response field names: `UPPER_SNAKE_CASE`" — no JEX/refactor split | JEX = `UPPER_SNAKE`; **Refactor wire = lowercase `snake_case`** (CTO 2026-05-08) | ✏️ |
| `docs/jex/api-spec/common.md:59-64` | "All field IDs: UPPER_SNAKE … frontend TS must use same field names as backend" | True for **JEX only**; refactor FE uses camelCase in-memory + snake_case wire | 📋 (JEX-scoped; add a refactor pointer) |
| `Claude Code (AI)/references/03_code_pattern_examples.md:40-41,108,503` | "UPPER_SNAKE_CASE for all API field names" | JEX-pattern reference; refactor wire is lowercase snake_case | 📋 |

### Refactor backend (`wecms-api/docs/`)

| File:line | Wrong claim | Verified correct value | |
|---|---|---|---|
| `references/api-spec/banking/account-inquiry-fcy-api.md:101` | "JSON keys are UPPER_SNAKE_CASE via `@JsonProperty` — match JEX 1:1" | `AccountRow.java` has **no** `@JsonProperty` → **lowercase snake_case** (`acct_no`,`bank_cd`,`ccy_cd`,`bal`,`real_amt`); identical to VND | ✏️ |
| `features/addon-service/sales-e-invoices.md` (~90,323,338) · `expenses-e-invoices.md` (~116,243,255) | wire should be / is UPPER_SNAKE; "preserve verbatim" / flip-to-UPPER checklist | `EInvoiceRow.java:23-40` no `@JsonProperty` → **lowercase snake_case**; FE `ApiEInvoiceRow` snake_case | ✏️ (banner) |
| `planning/addon/sales-e-invoices-plan.md:47,130` | "Wire shape **is** UPPER_SNAKE via `@JsonProperty` (verified)" | Source has no `@JsonProperty` on `EInvoiceRow` → lowercase snake_case | 📋 |
| `references/api-spec/addon/sales-e-invoices-api.md:144-145,369` | "`EInvoiceListResponse`/`EInvoiceRow` all `@JsonProperty("UPPER_SNAKE")`" | No such annotations in source → lowercase snake_case | 📋 |
| `features/addon-service/operation-status-inquiry.md:268` · `references/api-spec/addon/operation-status-inquiry-api.md:133-145` · `planning/addon/operation-status-inquiry-plan.md:96,216` | response keeps/should keep UPPER_SNAKE | `operation/dto/` no `@JsonProperty` → lowercase snake_case (`taxcode`,`company_nm`,`status_cd`) | ✏️ (banner on feature doc) |
| `references/migration/service-mapping.md:45,134,336` · `references/api-spec/api-mapping.md:34` | show `@JsonProperty (UPPER_SNAKE)` as the wire layer (pre-CTO model) | Wire = lowercase snake_case; per-field UPPER only on the 14 holdover DTOs | 📋 |

### Refactor frontend (`wecms-web/docs/`)

| File:line | Wrong claim | Verified correct value | |
|---|---|---|---|
| `features/addon-service/sales-e-invoices.md:77,240` · `expenses-e-invoices.md:206` | "Wire JSON is UPPER_SNAKE … the project's standard pattern; do not alter" | Source wire is lowercase snake_case; mapper reads `api.doc_id` etc. | 📋 |
| `features/addon-service/operation-status-inquiry.md:58,258` | mapper consumes UPPER_SNAKE wire (`TAXCODE`,`COMPANY_NM`) | mapper reads `api.taxcode`/`api.company_nm` (mappers.ts:736-740) — lowercase | 📋 |
| `features/banking/current-savings-vnd/account-inquiry.md:64` · `current-savings-fcy/account-inquiry.md:71` | "UPPER_SNAKE → camelCase translation" in mappers | Account Inquiry wire is lowercase snake_case; mapper reads `api.bank_cd` etc. | 📋 |
| `features/banking/reports/cash-flow/api-consumption.md:109-110` | flags `wecms-web/CLAUDE.md` Holdovers list as stale for `ApiCashFlowReport` | Correct — `ApiCashFlowReport` is actually snake_case; CLAUDE.md holdover entry is stale | 📋 (governance file — flag, do not silently edit) |

> The two root rule files `wecms-api/CLAUDE.md` and `wecms-web/CLAUDE.md` are the
> consistent source of truth (lowercase snake_case + an explicit holdover list)
> and should be treated as the yardstick — the only stale entry is the
> `ApiCashFlowReport` line in the `wecms-web/CLAUDE.md` "Holdovers" list, which
> should be removed by whoever owns that file (it is a project-instruction file,
> not flagged for silent edit here).

---

## 10. Remaining gaps & PO decisions

- **Parity gaps:** ❌ none in source.
- **PO decisions required for casing:** none. The lowercase wire is CTO-approved;
  the UPPERCASE holdovers are CLAUDE.md-sanctioned.
- **Tracked scheduling item (not a gap):** flip the 4 holdover families
  (Transaction History, Daily Cash, Daily Financial CMC/HS, Bank-Matching;
  legacy Transfer History is `@Deprecated`) to lowercase snake_case as each
  feature reaches full migration — and flip the matching `wecms-web` wire type +
  mapper in the same MR (full-stack ownership rule).
- **Convention tidy-up (low):** unify the FE GET query-param casing (§7).

---

## 11. Final status

| Dimension | Result |
|---|---|
| Legacy UPPERCASE contract preserved verbatim? | **No — deliberately** (CTO directive 2026-05-08) |
| Lowercase introduced without approval? | **No** — written approval exists |
| Holdover UPPERCASE DTOs still emitting legacy contract? | **Yes — 14 DTOs, sanctioned & tracked** |
| Unapproved parity gap in source? | **None** |
| Documentation accurate? | **No — stale UPPER_SNAKE claims** (see §9); corrected/flagged in this pass |

**Verdict: 🟡 Approved Deviation / Modernization — no parity gap remains.**
Source is internally consistent; the only action items are documentation
corrections (§9) and an optional query-param tidy-up (§7).
