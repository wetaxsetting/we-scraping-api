# Own Account Transfer — Migration Guide (JEX → Spring)

How the legacy `trns_0001_*` / `trns_0002_001` / `nxisas_*` surface was translated to Spring, what parity
must be preserved, and what is deferred. The full risk register + field-level mapping is in
[references/migration/banking/own-account-transfer.md](../../../../references/migration/banking/own-account-transfer.md);
legacy migration notes in [docs/jex/features/own-account-transfer/migration-guide.md](../../../../../../docs/jex/features/own-account-transfer/migration-guide.md).

---

## 1. Service mapping

| Legacy WSVC | Spring endpoint | Service method |
|---|---|---|
| `trns_0001_001_r001` (source list) | `GET /source-accounts` | `listSourceAccounts` |
| `trns_0001_005_r001` (my-account recipient) | `GET /target-accounts` | `listTargetAccounts` |
| `trns_0001_001_r003` (NAPAS validate) | `GET /napas-bank-info/{napas_bank_cd}` | `lookupNapasBankInfo` |
| `trns_0001_001_u001` (balance update) | `POST /balance-refresh` | `applyBalanceRefresh` |
| `trns_0002_001_c001` (history insert) | `POST /scraping-result` | `recordScrapingResult` |
| `nxisas_acctList_r003` | `POST /nxisas/cert-acct-list` | `certAcctList` |
| `nxisas_decrypt` / `nxisas_encrypt` | `POST /nxisas/decrypt` / `/encrypt` | `decrypt` / `encrypt` |
| `nxisas_logging` | `POST /nxisas/logging` | `recordLogging` |
| (no legacy twin) | `POST /engine-connection-check` | `recordEngineConnectionCheck` (compliance audit, OQ-4) |

`trns_0001_001_r002` (cross-tenant recipient-bank search over `NAPAS_BANK_REMIT_INFO`) is **not ported** —
out of scope under OQ-A (own→own only). The `RECEIVER_INFO` pre-scrape is deprecated (engine goes straight to
TRANSFER with `transInfoYN:"Y"`).

## 2. Must-preserve parity (verified)

1. **Source-account filter** — VND, `ACTV_YN='Y'`, types `03/05`; HAENGSUNG → VietinBank `11704001` with saving-maturity + `BAL>0` gate.
2. **Permission model** — non-admins limited to `ACCT_AUTH_INFO` `V`/`W` grants; Transfer requires `W`.
3. **Balance-update sequence** — always patch inq status/msg/dtm; `BAL` delta sets `CORC_BF_BAL` + `ACCT_BAL_UPD_DTM`; `REAL_AMT` refreshed when unchanged; `00000000` gate.
4. **History insert** — `NAPAS_REMIT_TRNS_HSTR` with `TRNS_STLM_YN='N'`; ownership/amount/type re-validation server-side.
5. **SEED-CBC decrypt** — engine `Output.Result` decrypted server-side (legacy `isas1.0.jar`); see §4.
6. **Audit logging** — `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS` on every engine run; `AUDIT_LOGS_INFM` for app actions.
7. **Engine architecture** — Spring never on the scrape path; the verbatim Coocon bundle drives the local `.exe`.

## 3. Improvements over legacy

- **Idempotency** — `OAT_IDEMPOTENCY_KEY` + partial unique index (V195) makes `/scraping-result` safe to retry; legacy guarded only on duplicate `TRS_ID` → `WBE0003`.
- **Type-safe DTOs** — Java records + Bean Validation; lowercase snake_case wire (no `COMMON_HEAD`/`REC`).
- **Slug identifiers** — route, package, audit `WSVC_ID`, i18n, View Setting `WEB_SVC_ID` all use `own-account-transfer`; legacy WSVC ids survive only as file:line citations.
- **NAPAS display names** — `NapasBankInfo` maps `SHORT_NAME`/`LONG_NAME_EN`/`LONG_NAME_VN` (B9, 2026-06-04).
- **Hard-coded bank codes → tenant allow-list** computed by product type.

## 4. SEED-CBC decrypt (B1) — special care

The NXiSAS engine returns the business payload (`Output.Result`) **SEED-CBC encrypted** (proven via a live
Shinhan BALANCE capture — opaque base64, no `ENC=` prefix, despite `ErrorCode=00000000`). The refactor reuses
the legacy `isas1.0.jar` server-side in `SeedCbcDecryptService`, which walks the nested engine result array,
strips any `ENC=` prefix, and decrypts via `ISASSeedCBC` keyed by the per-session `Uid`/`Action` (≥30-char
values → keyed cipher; shorter → unkeyed fallback; tolerant — keeps ciphertext on failure).

Gated by `wecms.oat.seed-decrypt.enabled` (default **OFF** = pre-B1 passthrough). Flip per environment after a
per-bank live BALANCE verification on a licensed origin (Shinhan proven; Vietcombank/IBK/VietinBank/Woori share
the cipher path). See [docs/jex/features/own-account-transfer/b1-decrypt-implementation-plan.md](../../../../../../docs/jex/features/own-account-transfer/b1-decrypt-implementation-plan.md).

## 5. PROD active-table scope

In scope (migrated): `ACCT_MSTR`, `ACCT_AUTH_INFO`, `NAPAS_REMIT_TRNS_HSTR`, `NAPAS_BANK_INFO`,
`BANK_CERT_INFO`, `EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS`, `AUDIT_LOGS_INFM`.
**Out of scope:** `NAPAS_BANK_REMIT_INFO` (recipient remittability — backed the cross-tenant search, not migrated).

## 6. Deferred (post-MR, tracked)

- **OQ-FE-1** — composer credential collection (saved-cert lookup + in-app password capture) → Phase-B composer MR.
- **B5** — HAENGSUNG Woori `100…→DDA…` account rewrite (no live HAENGSUNG Woori account yet).
- **B11** — Vietcombank DIGIBIZ engine-class switch.
- **B14** — CAPTCHA bridge (OTP/QnA/Confirm bridges exist).
- **B-LIVE** — live engine verification on a licensed origin (R&D origin unlicensed; no real credentials).

None block release. See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the parity matrix.
