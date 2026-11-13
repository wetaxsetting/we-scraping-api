# Own Account Transfer — Test Scenarios (SIT/UAT, Refactor Backend)

Backend-focused scenarios. The engine-driven transfer path requires a **licensed origin + real bank
credentials** (R&D origin is unlicensed — those steps are marked **ENGINE-LIVE**). Unit/integration
coverage lives under `src/test/java/.../ownaccounttransfer/`. Legacy functional cases:
[docs/jex/features/own-account-transfer/test-scenarios.md](../../../../../../docs/jex/features/own-account-transfer/test-scenarios.md).

Frontend test plan: [wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/test-scenarios.md](../../../../../../wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/test-scenarios.md).

---

## 1. Source-account list / search

| ID | Scenario | Expected |
|---|---|---|
| OAT-L1 | List as ADMIN | All active VND `03/05` accounts on allowed banks; `can_transfer=true` on all; totals correct. |
| OAT-L2 | List as USER (partial grants) | Only `V`/`W`-granted accounts; `can_transfer=true` only on `W` rows. |
| OAT-L3 | Bank filter `bank_cd=11704088` | Only Shinhan rows; `allowed_bank_cds` unchanged. |
| OAT-L4 | Bank filter unsupported code | Coerced to "all" — full list (legacy parity). |
| OAT-L5 | Sort `sortField=BAL&sortDirection=DESC` | Descending balance; unknown field → default `BANK_CD ASC, ACCT_NO ASC`. |
| OAT-L6 | HAENGSUNG tenant (`PRDT_TYPE='H'`) | Only VietinBank `11704001`; matured/zero-balance savings excluded. |
| OAT-L7 | Empty (no eligible accounts) | `list=[]`, `total_count=0`, totals zero. |

### 1a. Per-row credential projection

The five cert fields (`bank_cert_seq`, `cert_info_con`, `cert_info_detl_con`, `cert_info_detl_con2`,
`nxisas_gb_seq_no`) are resolved **per row** from that row's own `ACCT_MSTR.BANK_CERT_SEQ` — matching
legacy's per-row `_v2nxisas.getCertInfo(BANK_CERT_SEQ)`. A row that resolves no credential still
renders; it is simply un-credentialed, and the frontend gate disables its Transfer button with the
register-credentials tooltip.

| ID | Scenario | Expected |
|---|---|---|
| SA-CERT-1 | Row whose `BANK_CERT_SEQ` resolves a credential **at the same bank** | All five cert fields project; `cert_info_detl_con` is the **strictly** decrypted password (a value that fails legacy-cipher decrypt projects `null`, never a raw blob). |
| SA-CERT-2 | Row whose `BANK_CERT_SEQ` resolves a credential **at a different bank** | Treated as **no cert** — all five fields `null`, `bank_cert_seq` still echoed, row still listed. Server logs `WARN` naming both bank codes. The engine must never receive another bank's login. |
| SA-CERT-3 | Row whose `BANK_CERT_SEQ` points at a **deleted** credential | All cert fields `null`. After the 2026-08-19 delete fix this state is only reachable for rows that were already dangling before the fix shipped — cure is Settings → Login Credentials → the bank → Select Bank Accounts (login-credentials D1c). |
| SA-CERT-4 | Row with `BANK_CERT_SEQ IS NULL` (never linked) | All cert fields `null`; the credential table is not queried at all for the request. |
| SA-CERT-5 | Partially-linked bank (one linked row + one unlinked row, same `BANK_CD`) | Only the linked row projects cert fields. The gate is **per account**, never per bank. |

> **SA-CERT-2 is a deliberate divergence from legacy.** `SEQ_NO` is tenant-global (one sequence shared
> by every bank), and neither legacy's IDO `BANK_CERT_INFO_R006` nor the refactor's `loadBankCerts`
> filters `BANK_CD`. Without the guard a stale pointer could resolve another bank's login ID and
> password, and the engine would attempt a login at bank A with bank B's credentials — a guaranteed
> authentication failure, and each one feeds the bank's 3-strike lockout. Rationale and approval:
> [../../../../bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md](../../../../bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md).

Headless coverage for SA-CERT-1…4 is in `OwnAccountTransferServiceCertBankGuardTest`; SA-CERT-5 is
covered on the frontend side (per-account gate). Evidence:
[../../../../testing/results/bank-credential-delete-dangling-linkage-test-result-2026-08-19.md](../../../../testing/results/bank-credential-delete-dangling-linkage-test-result-2026-08-19.md).

## 2. Transfer creation (`/scraping-result`)

| ID | Scenario | Expected |
|---|---|---|
| OAT-C1 | Valid SAME_BANK success (`trns_stts=00000000`) | Row inserted; `status="success"`, `already_recorded=false`; `SCRAPING_RESULT_OK` audit. |
| OAT-C2 | Valid NAPAS success | NAPAS code resolved to internal `bank_cd`; row inserted with `TRANS_TYPE=2`. |
| OAT-C3 | Invalid amount (`requested_amt ≠ actual_amt`) | Rejected; nothing persisted; `WBE_VALIDATION`. |
| OAT-C4 | Invalid/inactive source account | Rejected (ownership/active check); `SCRAPING_RESULT_REJECT`. |
| OAT-C5 | Non-VND currency | `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY`. |
| OAT-C6 | SAME_BANK missing `target_bank_cd` / NAPAS missing `target_napas_bank_cd` | Transfer-type shape rejection. |
| OAT-C7 | Engine failure (`trns_stts≠00000000`) | Row recorded with `status="failed"`; `SCRAPING_RESULT_REJECT` audit; no balance change. |
| OAT-C8 | USER without `W` grant on source | Rejected (no sender authority). |

## 3. Idempotency / retry

| ID | Scenario | Expected |
|---|---|---|
| OAT-R1 | Resubmit same `idempotency_key` | Cached row returned; `already_recorded=true`; no duplicate insert. |
| OAT-R2 | New key, same details | New row inserted (legitimate repeat transfer). |
| OAT-R3 | Concurrent `SEQ_NO` race | One retry of allocation; both rows persist with distinct `SEQ_NO`. |

## 4. Approval

| ID | Scenario | Expected |
|---|---|---|
| OAT-A1 | Request approval / approve / reject / withdraw | **N/A** — own-account transfers have no in-app approval workflow; the bank's 2nd-factor (OTP/QnA/Confirm) is the only approval. Documented as MISSING-by-design in [refactor-gap-analysis.md](refactor-gap-analysis.md). |

## 5. Balance inquiry / refresh (`/balance-refresh`)

| ID | Scenario | Expected |
|---|---|---|
| OAT-B1 | Success, balance changed | `CORC_BF_BAL=old`, `BAL=new`, `ACCT_BAL_UPD_DTM=now`; status badge **Success** + datetime. |
| OAT-B2 | Success, balance unchanged | `REAL_AMT` refreshed; `BAL` unchanged. |
| OAT-B3 | Failed inquiry (status≠00000000) | Inq status/msg/dtm patched; badge **Failed** + datetime; balance untouched. |
| OAT-B4 | Non-VND | `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY`. |
| OAT-B5 | Last-Refresh display — no history | Blank cell (no status, no datetime). |

## 6. Status inquiry

| ID | Scenario | Expected |
|---|---|---|
| OAT-S1 | NAPAS lookup valid code | `NapasBankInfoRow` with display names + internal `bank_cd`. |
| OAT-S2 | NAPAS lookup unknown code | Not-found handling (empty/error per contract). |
| OAT-S3 | Engine-connection-check (OK/FAIL/TIMEOUT/NOT_INSTALLED) | `204`; audit row written; failures swallowed (best-effort). |

## 7. NXiSAS bundle support

| ID | Scenario | Expected |
|---|---|---|
| OAT-N1 | `/nxisas/logging` with mixed jobs | One `NXSIAS_MODULE_TRAN_LOG_HIS` per job; `EXT_TRAN_CALL_HIS` for BALANCE_INQUIRY/CARD_INQUIRY only; TRANSFER not written there (B10). |
| OAT-N2 | `/nxisas/decrypt` flag OFF | `NXISAS_DATA` returned unchanged. |
| OAT-N3 | `/nxisas/decrypt` flag ON, valid cipher | `Output.Result` decrypted to parsed JSON (SeedCbcDecryptServiceTest). |
| OAT-N4 | `/nxisas/cert-acct-list` by `seq_no` | Cert fields returned (BANK_CERT_INFO). |

## 8. Permissions

| ID | Scenario | Expected |
|---|---|---|
| OAT-P1 | Authorized user (W grant) | Can list + transfer the account. |
| OAT-P2 | Unauthorized user (no grant) | Account not listed; cannot transfer. |
| OAT-P3 | Cross-tenant attempt | Tenant scoping via `TenantContext.currentBizKey()` blocks rows from another `biz_key`. |
| OAT-P4 | No JWT | 401 via `JwtAuthenticationEntryPoint`. |

## 9. ENGINE-LIVE (licensed origin only)

| ID | Scenario | Expected |
|---|---|---|
| OAT-E1 | Full LOGIN→TRANSFER→LOGOUT on a real account | `00000000` → row recorded; balance refresh shows new balance. |
| OAT-E2 | Flag-on BALANCE per bank | Decrypted `balance[]` → `ACCT_MSTR.BAL` updated; verify per in-scope bank. |
| OAT-E3 | 2nd-factor (OTP/QnA/Confirm) | Engine challenge bridged to dialog; flow continues on correct input, aborts on cancel. |
