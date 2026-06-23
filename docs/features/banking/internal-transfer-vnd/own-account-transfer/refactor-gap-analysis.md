# Own Account Transfer — Refactor Gap Analysis (Final Parity Review)

Parity of the shipped refactor (backend + frontend) against the legacy JEX feature. Companion audits:
[own-account-transfer-parity-2026-06-01.md](../../../../references/audits/own-account-transfer-parity-2026-06-01.md),
[own-account-transfer-current-implementation-audit.md](../../../../references/audits/own-account-transfer-current-implementation-audit.md),
[own-account-transfer-legacy-bank-business-rules.md](../../../../references/audits/own-account-transfer-legacy-bank-business-rules.md).
Legacy-side gap doc: [docs/jex/features/own-account-transfer/refactor-gap-analysis.md](../../../../../../docs/jex/features/own-account-transfer/refactor-gap-analysis.md) and [final-status.md](../../../../../../docs/jex/features/own-account-transfer/final-status.md).

---

## 1. Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| Search (source-account list) | `trns_0001_001_r001` | `GET /source-accounts` (VND/perms/types, HAENGSUNG gate) | **MATCH** |
| Create Transfer | `trns_0002_001_c001` history insert | `POST /scraping-result` (+ idempotency) | **MATCH** (improved) |
| Edit Transfer | none (transfers immutable) | none | **N/A — by design** |
| Delete Transfer | none (transfers immutable) | none | **N/A — by design** |
| Approval | none in-app (bank 2nd-factor only) | none in-app (bank 2nd-factor only) | **N/A — by design** |
| Transfer Execution | engine `LOGIN→TRANSFER→LOGOUT` (verbatim Coocon) | identical — verbatim Coocon bundle + `/scraping-result` intake | **MATCH** (code-level; ENGINE-LIVE not run on unlicensed origin) |
| Status Inquiry (NAPAS) | `trns_0001_001_r003` | `GET /napas-bank-info/{code}` (+ display names) | **MATCH** |
| Status Inquiry (engine handshake) | implicit `openNxisas` | `POST /engine-connection-check` audit | **MATCH** (added; compliance improvement) |
| Balance Inquiry / Update Balance | `trns_0001_001_u001` | `POST /balance-refresh` (same patch sequence) | **MATCH** (code-level; runtime needs ENGINE-LIVE) |
| Last-Refresh display | status badge + humanized time | identical 4-state rule | **MATCH** |
| SEED-CBC decrypt | `nxisas_decrypt_act.jsp` | `SeedCbcDecryptService` (legacy `isas1.0.jar`), flag-gated | **MATCH** (B1 closed; flag-on pending per-bank ENGINE-LIVE) |
| External-tran-call history | `nxisas_logging` → `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS` | `OatNxisasService.recordLogging` — both tables; BALANCE_INQUIRY visibility restored | **MATCH** (B10 closed) |
| NAPAS display names | `LONG_NAME_*` / `SHORT_NAME` | `NapasBankInfo` maps all three | **MATCH** (B9 closed) |
| Error handling | ML codes (WBE1135 etc.) | `WBE_*` + `{status,trace_id}` envelope | **MATCH** (translated per CTO directive) |
| Permissions | `ACCT_AUTH_INFO` V/W + admin | identical; tenant-scoped | **MATCH** |
| Export | none | none | **N/A** (no legacy export) |
| Recipient: Contacts / Recent / manual-NAPAS search | `trns_0001_002/004` + `r002` | not ported (own→own scope) | **MISSING — out of scope (OQ-A)** |
| HAENGSUNG Woori `100→DDA` rewrite | `trns_0001_007.ts` | not ported | **MISSING — deferred (B5)** |
| Vietcombank DIGIBIZ class switch | `trns_0001_001.ts` | not ported | **MISSING — deferred (B11)** |
| CAPTCHA 2nd-factor bridge | engine CAPTCHA | not bridged (OTP/QnA/Confirm are) | **MISSING — deferred (B14)** |

## 2. Closed tracked gaps

- **B1 — SEED-CBC decrypt** — ✅ CLOSED 2026-06-05. `SeedCbcDecryptService` reuses legacy `isas1.0.jar`; gated by `wecms.oat.seed-decrypt.enabled` (default OFF). Flag-on awaits per-bank ENGINE-LIVE verification.
- **B9 — NAPAS display names** — ✅ CLOSED 2026-06-04. `SHORT_NAME`/`LONG_NAME_EN`/`LONG_NAME_VN` mapped + asserted in controller test.
- **B10 — Firm Banking History visibility** — ✅ CLOSED 2026-06-05. `recordLogging` writes `EXT_TRAN_CALL_HIS` for FBH scrape categories (BALANCE_INQUIRY); "Scraping Balance" row appears after Update Balance.

## 3. Open / deferred (none block release)

| Item | Disposition |
|---|---|
| OQ-A (scope) | Resolved — own→own, VND-only (PO 2026-05-18). |
| OQ-FE-1 | Composer credential collection deferred to Phase-B composer MR. |
| B5 / B11 / B14 | Deferred until the relevant bank/account enters scope. |
| B-LIVE | One live TRANSFER + per-bank BALANCE verification on a licensed origin (deployment activity, not code). |

## 4. Completion statement

For the **in-scope feature — own→own VND transfer (self-transfer), source-account listing, balance refresh,
transfer execution + history, status/NAPAS inquiry, SEED-CBC decrypt, and audit logging** — the refactor is:

```
Refactor Status: 100% Complete (in-scope)
```

**Supporting evidence:**
- Backend: `OwnAccountTransferController` (10 endpoints) + `OwnAccountTransferService` (~970 lines) + `OatNxisasService` + `SeedCbcDecryptService`, with unit/integration tests (`OwnAccountTransferServiceTest`, `OwnAccountTransferControllerTest`, `OatNxisasServiceExtTranLogTest`, `OatNxisasServiceSeedFlagTest`, `SeedCbcDecryptServiceTest`).
- Frontend: Next.js page + verbatim Coocon bundle + composer/engine dialogs + hooks (see [source-code-index.md](source-code-index.md)).
- Migrations: V195 (idempotency), V199 (View Setting seed).
- All three tracked parity gaps (B1/B9/B10) closed; the remaining `MISSING` rows are explicitly out-of-scope (OQ-A) or deferred (B5/B11/B14) with PO acknowledgement.

**Caveat:** runtime transfer/balance flows are verified at code level but **not exercised end-to-end** on a
licensed engine origin (B-LIVE) — a deployment/verification activity, not outstanding development.
