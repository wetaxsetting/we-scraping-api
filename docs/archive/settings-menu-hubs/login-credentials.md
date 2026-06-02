# Login Credentials

> Canonical Settings-menu entry for **Settings → Login Credentials**. Detailed design lives in the
> linked feature spec below.

## Overview
Login Credentials is the per-tenant master of **bank login credentials** the firm-banking gateway
uses to scrape balances/transactions on the user's behalf, plus the GDT tax-authority credential
variant. A credential is a `(BANK_CD, login_id, password)` triple; the password ciphertext never
leaves the server. Slug: `login-credentials`. This is the first Settings item that drives a live
external system (Coocon NXISAS). Mirrors live JEX `comm_0218_003/_004/_nxisas` (+ GDT `comm_0218_005_*`).

- Feature spec (full backend + frontend detail): [../features/settings/login-credentials.md](../../features/settings/login-credentials.md)
- Bank-scraping gateway: see [../business-rules/integrations/](../../references/business-rules/integrations/)

## Related APIs
- `POST /api/v1/login-credentials/bank` — persist a verified bank credential
- `PUT /api/v1/login-credentials/bank/{seqNo}` — change password (with `old_password` guard)
- `POST /api/v1/banking/scraping/verify` — Coocon `LOGIN` + `ACCT_LIST` verify-before-save
- `POST /api/v1/banking/scraping/{sessionId}/otp` — OTP challenge step
- `DELETE /api/v1/banking/credentials/{seqNo}` — delete (CRUD slice; Settings-panel wiring is OQ-4)

## Database Tables
- `BANK_CERT_INFO` — credential rows; password stored as ciphertext (server-side only).

## Business Rules
- Verify-before-save: a credential is persisted only after Coocon confirms `LOGIN`/`ACCT_LIST`
  (and any `OTP_REQUIRED` → `COMPLETED`) succeed.
- Edit re-runs Coocon `verify` with the new password; update requires the `old_password` guard
  (mirrors legacy `decrypt(cert_info_detl_con) = ?`).
- Password reveal endpoint deferred (OQ-3); ciphertext stays server-side.
- **GDT credentials are manual-only** (no .exe / NXiSAS local engine); do not conflate with the
  server-side bank scraping API.
- Local NXiSAS.exe (Own Account Transfer only) is origin-licensed via Coocon's wss handshake —
  unlicensed origin returns ErrorCode `80000015`.

## Validation
- Bank + login id + password required at each dialog step; backend re-validates against the gateway.

## Error Cases
- Gateway `OTP_REQUIRED` (state, not error) → OTP step.
- Invalid credentials / gateway failure → surfaced from `/scraping/verify`.
- Unlicensed origin → Coocon `80000015`.
- Standard envelope; `WBE_VALIDATION` / `WBE9999`.

## Notes
GDT branch + per-row Audit Logs body remain backend-pending (OQ-1, OQ-2). Web/UI:
[../../../wecms-web/docs/settings/login-credentials.md](../../../../wecms-web/docs/features/settings/login-credentials.md).
