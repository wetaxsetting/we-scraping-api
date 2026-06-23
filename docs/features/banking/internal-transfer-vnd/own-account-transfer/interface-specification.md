# Own Account Transfer — Interface Specification (Refactor Backend)

The 10 REST endpoints exposed by `OwnAccountTransferController`
(`com.kosign.wecms.domain.banking.ownaccounttransfer.controller`). Full request/response field tables and
validation live in the API contract [own-account-transfer-api.md](../../../../references/api-spec/banking/own-account-transfer-api.md);
legacy WSVC/IDO contracts are in [docs/jex/features/own-account-transfer/interface-specification.md](../../../../../../docs/jex/features/own-account-transfer/interface-specification.md).

**Base path:** `/api/v1/banking/own-account-transfer` · **Auth:** JWT (all endpoints; tenant via `TenantContext`) ·
**Wire:** lowercase snake_case, `{ status, data }` envelope.

---

## 1. Endpoint catalogue

| # | Category | Method | URI (relative to base) | Request DTO | Response DTO | Service method | Legacy |
|---|---|---|---|---|---|---|---|
| 1 | Search / List | GET | `/source-accounts` | query: `bank_cd?`, `sortField?`, `sortDirection?` | `SourceAccountListResponse` | `listSourceAccounts` | `trns_0001_001_r001` |
| 2 | Search / List | GET | `/target-accounts` | query: `source_bank_cd?`, `source_acct_no?` | `TargetAccountListResponse` | `listTargetAccounts` | `trns_0001_005_r001` |
| 3 | Status Inquiry | GET | `/napas-bank-info/{napas_bank_cd}` | path: `napas_bank_cd` | `NapasBankInfoRow` | `lookupNapasBankInfo` | `trns_0001_001_r003` |
| 4 | Update Balance | POST | `/balance-refresh` | `BalanceRefreshRequest` | `BalanceRefreshResponse` | `applyBalanceRefresh` | `trns_0001_001_u001` |
| 5 | Transfer Execution | POST | `/scraping-result` | `ScrapingResultIntakeRequest` | `ScrapingResultResponse` | `recordScrapingResult` | `trns_0002_001_c001` |
| 6 | Status Inquiry (audit) | POST | `/engine-connection-check` | `EngineConnectionCheckRequest` | `204 No Content` | `recordEngineConnectionCheck` | (refactor addition, OQ-4) |
| 7 | Bundle support | POST | `/nxisas/cert-acct-list` | `NxisasCertAcctListRequest` | `NxisasCertAcctListResponse` | `certAcctList` | `nxisas_acctList_r003` |
| 8 | Bundle support | POST | `/nxisas/decrypt` | `NxisasDecryptRequest` | `NxisasDecryptResponse` | `decrypt` | `nxisas_decrypt_act.jsp` |
| 9 | Bundle support | POST | `/nxisas/encrypt` | `NxisasDecryptRequest` | `NxisasDecryptResponse` | `encrypt` | `nxisas_encrypt_act.jsp` |
| 10 | Bundle support | POST | `/nxisas/logging` | `NxisasLoggingRequest` | `204 No Content` | `recordLogging` | `nxisas_logging_act.jsp` |

> **Categorisation note** (vs the generic audit template): there are **no** separate Create / Update / Delete /
> Approval endpoints. A transfer is created exactly once (endpoint 5) and is immutable thereafter — no update,
> no delete. There is no in-app approval endpoint (the bank's 2nd-factor is the only approval). "Status Inquiry"
> maps to endpoints 3 + 6; "Update Balance" / "Balance Inquiry" both map to endpoint 4. No Export endpoint.

## 2. Request DTOs (`…/dto/request/`)

| DTO | Key fields (camelCase → wire snake_case) | Validation highlights |
|---|---|---|
| `BalanceRefreshRequest` | `bankCd`, `acctNo`, `bal?`, `realAmt?`, `acctBalInqStts`, `acctBalInqMsg?`, `ccyCd?` | `bank_cd` `\d{8}`; amounts `≥0`; `ccy_cd` `[A-Z]{3}` → must be VND |
| `ScrapingResultIntakeRequest` | `idempotencyKey`, `transferType`, `sourceBankCd`, `sourceAcctNo`, `targetBankCd?`, `targetNapasBankCd?`, `targetAcctNo`, `ccyCd`, `requestedAmt`, `actualAmt`, `bankTxId?`, `trnsStts`, `trnsMsg?`, `fee?`, `feeDfrSbj?`, `rmrk?`, `rmrk2?`, `engineVendor?`, `engineVersion?` | `idempotency_key` `[A-Za-z0-9-]{32,64}`; `transfer_type` `SAME_BANK|NAPAS`; amounts `\d{1,15}`; `ccy_cd` VND-only; `fee_dfr_sbj` `A|B|S` |
| `EngineConnectionCheckRequest` | `result`, `engineVendor`, `engineVersion`, `userAgent?`, `osPlatform?`, `scrapeSessionId?`, `errorCode?`, `errorMessage?` | `result` `OK|FAIL|TIMEOUT|NOT_INSTALLED` |
| `NxisasCertAcctListRequest` | `seqNo` | numeric string |
| `NxisasDecryptRequest` | `NXISAS_DATA`, `NXISAS_UID`, `NXISAS_ACTION` | (no `@Valid`; bundle-shaped passthrough) |
| `NxisasLoggingRequest` | `JSON_STR` | serialized engine thread array |

## 3. Response DTOs (`…/dto/response/`)

| DTO | Fields (wire snake_case) |
|---|---|
| `SourceAccountListResponse` | `list[]` (`SourceAccountRow`), `total_count`, `tot_bal`, `tot_avi_bal`, `allowed_bank_cds[]` |
| `SourceAccountRow` | `bank_cd`, `bank_nm`, `acct_no`, `acct_nick_nm`, `acct_type_cd`, `bal`, `real_amt`, `ccy_cd`, `acct_bal_inq_stts`, `acct_bal_inq_msg`, `acct_bal_upd_dtm`, `acct_bal_lst_inq_dtm`, `bank_cert_seq`, `cert_info_con`, `cert_info_detl_con`, `nxisas_gb_seq_no`, `can_transfer` |
| `TargetAccountListResponse` / `TargetAccountRow` | `list[]`, `total_count` / `kind`(`OWN_ACCOUNT`), `bank_cd`, `bank_nm`, `acct_no`, `acct_nick_nm`, `acct_type_cd`, `ccy_cd`, `napas_bank_cd` |
| `NapasBankInfoRow` | `napas_bank_cd`, `short_name`, `long_name_en`, `long_name_vn`, `bank_cd` |
| `BalanceRefreshResponse` | `bank_cd`, `acct_no`, `bal`, `ccy_cd`, `acct_bal_lst_inq_dtm` |
| `ScrapingResultResponse` | `seq_no`, `idempotency_key`, `bank_tx_id`, `recorded_amt`, `trns_stts`, `status`(`success`/`failed`), `already_recorded` |
| `NxisasCertAcctListResponse` | `cert_info_con`, `cert_info_detl_con`, `cert_info_detl_con2`, `cert_info_detl_con3`, `nxisas_gb_seq_no` |
| `NxisasDecryptResponse` | `NXISAS_DATA` |

## 4. Feature flag

`/nxisas/decrypt` + `/nxisas/encrypt` are gated by `wecms.oat.seed-decrypt.enabled`
(`OatSeedDecryptProperties`, default `false`). When disabled they return `NXISAS_DATA` unchanged (pre-B1
passthrough); when enabled they run the Coocon ISASSeedCBC cipher via `SeedCbcDecryptService`. See
[migration-guide.md](migration-guide.md) and [refactor-gap-analysis.md](refactor-gap-analysis.md) (B1).

## 5. Error codes

| Code | When |
|---|---|
| `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY` | non-VND currency on balance-refresh or scraping-result |
| `WBE_VALIDATION` | Bean Validation failure on any request DTO |
| `WBE9999` | unexpected server fault |

All errors return the standard `{ status, timestamp, path, trace_id }` envelope.
