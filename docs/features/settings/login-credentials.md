# Settings > Login Credentials — Refactor Feature Spec

**Module:** Settings
**Menu:** Login Credentials
**Refactor screen ID / slug:** `login-credentials`
**Frontend placement:** Settings modal panel ([components/settings/login-credentials/LoginCredentialsPanel.tsx](../../../../wecms-web/components/settings/login-credentials/LoginCredentialsPanel.tsx)).
**View Setting screenId (per [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts)):** *not adopted* — the panel has no toolbar list-icon button (same precedent as `erp-transaction-history`, `approval-line`, `chart-of-accounts`). The slug is reserved in the registry for future log/audit consistency.
**JEX counterparts:** WSVCs `comm_0218_003_c001` (Add — create bank credential), `comm_0218_004_u001` (Edit — change password with `decrypt(...)` guard), `comm_0218_nxisas` (`enter_login_popup` → `_v2nxisas.callNxisas` LOGIN + ACCT_LIST orchestration). Plus the GDT sibling (`comm_0218_005_*`) for the tax-authority credentials variant.
**Status:** **Phase 3 — wired full-stack.** Backend CRUD over `BANK_CERT_INFO` is live; Coocon LOGIN + OTP + ACCT_LIST orchestration is wired through `/api/v1/banking/scraping/*`; frontend Settings panel composes the full Figma Add and Registered chains end-to-end. GDT create/update (OQ-1), Audit Logs (OQ-2), and password Reveal (OQ-3) are **wired end-to-end** (verified 2026-06-02 — see §10). The only residual gap is Delete-wiring in the panel (OQ-4 — backend endpoint exists, panel kebab fires the confirmation dialog only).

> Legacy WSVC ids appear in this document only as JEX file:line citations and as migration-traceability prose. Every new identifier surface (REST URI, Java package, log tags, audit `WSVC_ID`) carries the kebab-case slug `login-credentials`, never `comm_0218_003`. Cross-tree rule: workspace [CLAUDE.md §Cross-Tree Rules](../../../../CLAUDE.md); backend identifier rules: [wecms-api/CLAUDE.md §Refactor identifier naming](../../../CLAUDE.md); frontend rules: [wecms-web/CLAUDE.md §Routing + §3a Settings Panel](../../../../wecms-web/CLAUDE.md).

---

## 1. Overview

The Login Credentials screen is the per-tenant master list of **bank login credentials** the WeCMS firm-banking gateway uses to scrape balances and transactions on the user's behalf. A credential is a `(BANK_CD, login_id, password)` triple persisted to `BANK_CERT_INFO`; the password ciphertext never leaves the server. The screen lets a tenant administrator:

- **List** all active credentials grouped by institution (bank rows + the GDT tax-authority row).
- **Add** a new bank credential through the Figma chain:
  1. Pick the institution (Bank vs. GDT) — [SelectLoginInstitutionDialog](../../../../wecms-web/components/shared/popups/SelectLoginInstitutionDialog.tsx).
  2. Pick the bank — [SelectBankDialog](../../../../wecms-web/components/shared/popups/SelectBankDialog.tsx).
  3. Enter login ID + password — [LoginIdPasswordDialog](../../../../wecms-web/components/shared/popups/LoginIdPasswordDialog.tsx).
  4. Backend calls Coocon `LOGIN` + `ACCT_LIST` via `POST /api/v1/banking/scraping/verify` to verify-before-save.
  5. If Coocon returns `OTP_REQUIRED` → `OtpChallengeDialog.tsx` (Needs Confirmation — source file not found) → `POST /api/v1/banking/scraping/{sessionId}/otp`.
  6. On `COMPLETED` → pick accounts in [SelectBankAccountsDialog](../../../../wecms-web/components/shared/popups/SelectBankAccountsDialog.tsx) → `POST /api/v1/login-credentials/bank` persists the credential row.
- **Edit** the password on a registered row — [RegisteredLoginCredentialsDialog](../../../../wecms-web/components/shared/popups/RegisteredLoginCredentialsDialog.tsx) → [EditLoginIdPasswordDialog](../../../../wecms-web/components/shared/popups/EditLoginIdPasswordDialog.tsx). Backend re-runs Coocon `verify` with the new password, then `PUT /api/v1/login-credentials/bank/{seqNo}` with the `old_password` guard (mirrors legacy `decrypt(cert_info_detl_con) = ?` at `comm_0218_004_u001_act.jsp:55-57`).
- **Reveal** a masked password — [PasswordConfirmationDialog](../../../../wecms-web/components/shared/popups/PasswordConfirmationDialog.tsx) calls `revealLoginCredential` against `POST /api/v1/login-credentials/{type}/{seqNo}/reveal` on a successful re-auth (wired — RESOLVED OQ-3, 2026-06-02; password ciphertext stays server-side until the re-auth passes).
- **Delete** a credential row — kebab menu → confirmation. **Static UI only today** (OQ-4); `DELETE /api/v1/banking/credentials/{seqNo}` exists on the older CRUD slice but is not yet wired into the Settings panel.
- **Audit Logs** — opens [AuditLogsDialog](../../../../wecms-web/components/shared/popups/AuditLogsDialog.tsx) with the per-row event log, fetched live via `fetchLoginCredentialAuditLogs` against `GET /api/v1/login-credentials/{type}/{seqNo}/audit-logs` (wired — RESOLVED OQ-2, 2026-06-02; no mock fallback).

The panel is the **first Settings submenu that actually drives a live external system** (Coocon NXISAS). Every other Settings panel reads/writes its own DB rows; this one stands up a multi-step state machine that crosses the gateway boundary, handles 2-factor challenges, and persists only after the gateway confirms the credentials work.

JEX file citations (source of truth for behavior — read-only):

| Artifact | Path |
|---|---|
| Add credential — typescript orchestration | [comm_0218_nxisas.ts](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0218_nxisas.ts) |
| Add credential — create action | [comm_0218_003_c001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0218_003_c001_act.jsp) |
| Change password — update action | [comm_0218_004_u001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0218_004_u001_act.jsp) |
| Edit popup — typescript | [comm_0218_003.ts](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0218_003.ts) |
| GDT credentials sibling | `comm_0218_005_*_act.jsp` |
| Entity | [BankCertInfo.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/BankCertInfo.java) |
| DAO | [BankCertInfoDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/BankCertInfoDAO.java) |
| IDO list | `WECMS_ETC/xml/service/IDO/IDO.BANK_CERT_INFO_R001.xml` |
| Coocon gateway client | (Java side — `NxisasClient` + `NxisasModuleTranLogHis`) |

---

## 2. Refactor scope

| Concern | Decision |
|---|---|
| List credentials (`BANK_CERT_INFO_R001`) | **In scope** — `GET /api/v1/banking/credentials` returns one row per credential (no per-row password ciphertext). |
| Add credential — verify with Coocon (`comm_0218_nxisas` → LOGIN + ACCT_LIST) | **In scope** — `POST /api/v1/banking/scraping/verify` runs the verify-before-save dance inline; the typed credentials are NOT persisted unless verify returns `COMPLETED`. |
| Add credential — OTP follow-up | **In scope** — `POST /api/v1/banking/scraping/{sessionId}/otp` resumes the same scrape session. |
| Add credential — persist (`comm_0218_003_c001`) | **In scope** — `POST /api/v1/login-credentials/bank` lands the verified credential into `BANK_CERT_INFO` after the user picks accounts. Per-account persistence into `ACCT_MSTR` is **out of scope** for this slice — see OQ-5. |
| Edit credential — change password (`comm_0218_004_u001`) | **In scope** — `PUT /api/v1/login-credentials/bank/{seqNo}` enforces the `old_password` guard (`WBE2091`) AND re-verifies the new password against Coocon before committing. |
| Delete credential | **Backend ready, frontend deferred** — `DELETE /api/v1/banking/credentials/{seqNo}` exists; the Settings panel's kebab → Delete currently fires only the confirmation dialog (OQ-4). |
| Reveal password | **In scope — wired (RESOLVED OQ-3, 2026-06-02)** — `POST /api/v1/login-credentials/{type}/{seqNo}/reveal` decrypts-and-shows on a successful re-auth; `PasswordConfirmationDialog` calls `revealLoginCredential`. |
| GDT credentials (tax authority) | **In scope — wired (RESOLVED OQ-1, 2026-06-02)** — `POST` / `PUT /api/v1/login-credentials/gdt` persist the GDT branch; [GdtCredentialsDialog](../../../../wecms-web/components/shared/popups/GdtCredentialsDialog.tsx) upserts via `createGdtLoginCredential` / `updateGdtLoginCredential`. |
| Audit Logs body | **In scope — wired (RESOLVED OQ-2, 2026-06-02)** — `GET /api/v1/login-credentials/{type}/{seqNo}/audit-logs` backs the dialog; the frontend fetches via `fetchLoginCredentialAuditLogs` (no mock fallback). Query targets `AUDIT_LOGS_INFM WHERE WSVC_ID='login-credentials'` (refactor slug, NOT `comm_0218_*`). |
| CAPTCHA / Q&A challenge branches | **Out of scope** — Coocon may return `CAPTCHA_REQUIRED` / `QNA_REQUIRED` for some banks; the panel surfaces a system error dialog ("unsupported challenge") and collapses the flow. Add when a real bank in the local test set demands it. |
| Bulk import from Excel | **Not planned** — credentials are entered through the Coocon-verified flow; bulk upload would bypass the verify-before-save guarantee. |

---

## 3. UI behavior

**Panel composition** ([LoginCredentialsPanel.tsx](../../../../wecms-web/components/settings/login-credentials/LoginCredentialsPanel.tsx)) follows the §3a Settings shell contract:

```
[ Header — title + description (left), Add primary button (right) ]
[ Scroll region — bare <table> + sticky <thead> + custom-scrollbar ]
```

The header right-side action is the §3b §B canonical brand-pill `bg-brand text-white shadow-wb-brand-btn h-8 px-3 text-wb-14-tight font-medium`. No additional toolbar icons (no print, no export, no list view).

**Two independent popup chains** stacked on top of the Settings shell at `stackLevel >= 2`:

1. **Add chain** — opened from the panel-header Add button. State variant `AddChild` in `LoginCredentialsPanel.tsx`:
   - `institution` → `bank | gdt`
   - `bank` branch: `bank` → `login-id-pw` → (`otp` if Coocon challenges) → `select-bank-accounts` → success dialog → collapse.
   - `gdt` branch: `gdt` → success dialog → collapse (static UI).
2. **Registered chain** — opened by clicking a credential row's institution cell or its kebab → Edit. State variant `RegisteredChild`:
   - `null` (Registered dialog alone, listing per-account credential entries)
   - `login-id-pw` | `select-bank` | `select-bank-accounts` (add a sibling login under the same institution)
   - `edit-login-id-pw` → (`edit-otp` if Coocon challenges) → PUT → success dialog → close to Registered.
   - `confirmation` (password reveal — static UI).

Both chains are **mutually exclusive at the child level** — opening one child closes any other in the same chain via a single `setState` swap (atomic React commit; no flicker). Cancel on any step ends the entire chain back to the panel; the Settings shell itself stays mounted regardless.

**Modal stacking** strictly follows §"Modal stacking convention":

| Layer | Where |
|---|---|
| L1 (`stackLevel={1}`) | Settings shell ([`<SettingsModal>`](../../../../wecms-web/components/settings/SettingsModal.tsx)) |
| L2 | Registered Login Credentials, Audit Logs, GDT Credentials, Select Login Institution |
| L3 | Login ID/PW, Select Bank, Select Bank Accounts, Password Confirmation, Edit Login ID/PW |
| L4 | OTP Challenge (sits on top of Login ID/PW or Edit Login ID/PW) |
| System feedback (z-100) | `useDialog()` success / error — appears above any chain depth |

Each level's overlay opacity compounds gently (`bg-black/45` at L1 + `bg-black/20` per stacked level); ESC dismisses **only the top-most** layer per the "Modal stack — interaction & close behaviour" rule. The Settings shell itself rejects outside-click + ESC dismissal while any child is open.

---

## 4. Wire contract

### 4.1 List

```http
GET /api/v1/banking/credentials
→ 200 {
    "status": { "error": false, "code": "WBI0000", "message": "Success" },
    "data":   { "items": [ApiBankCredentialRow, ...] }
}
```

```ts
type ApiBankCredentialRow = {
    seq_no: string;
    bank_cd: string | null;
    username: string | null;
    acct_list_inq_stts: string | null;
    acct_list_inq_msg: string | null;
    lst_acct_list_inq_dt: string | null;
    reg_dtm: string | null;
    mod_dtm: string | null;
};
```

The password ciphertext is **never returned**. `acct_list_inq_stts` / `_msg` / `_dt` carry the last scrape outcome so the panel can render the per-row status badge.

### 4.2 Verify-before-save (Add flow)

```http
POST /api/v1/banking/scraping/verify
Content-Type: application/json

{
    "bank_cd":          "VCB",
    "login_id":         "alice",
    "password":         "plaintext-typed-by-user",
    "nxisas_gb_seq_no": 0,                      // optional — sub-type discriminator (0 = Corporate)
    "extra":            null,                   // optional Corporate ID / extra-field input
    "login_type":       null                    // optional Login Type selector ("OTP" / "EMAIL" / "SMS")
}
→ 200 ApiResponse<ApiScrapeSessionResponse>
```

```ts
type ApiScrapeSessionResponse = {
    session_id:        string | null;           // present when status = OTP_REQUIRED
    status:            "OTP_REQUIRED" | "CAPTCHA_REQUIRED" | "QNA_REQUIRED" | "COMPLETED" | "ERROR";
    challenge_prompt:  string | null;           // gateway-supplied prompt text for OTP / CAPTCHA / QNA
    accounts:          ApiScrapedAccount[] | null;  // present when status = COMPLETED
    error_code:        string | null;
    error_message:     string | null;
};
```

**No row is persisted to `BANK_CERT_INFO` by this call.** The plaintext credentials live only inside the in-flight scrape session and the frontend's transient `loginDraft` state — never localStorage, never sessionStorage.

### 4.3 OTP submit

```http
POST /api/v1/banking/scraping/{sessionId}/otp
Content-Type: application/json

{ "value": "123456" }
→ 200 ApiResponse<ApiScrapeSessionResponse>     // same shape — typically COMPLETED or ERROR
```

### 4.4 Persist verified credential

```http
POST /api/v1/login-credentials/bank
Content-Type: application/json

{
    "bank_cd":          "VCB",
    "login_id":         "alice",
    "password":         "plaintext-typed-by-user",   // server encrypts before INSERT
    "nxisas_gb_seq_no": 0,
    "extra":            null,
    "login_type":       null
}
→ 200 ApiResponse<{ seq_no: number }>
```

Allocates `SEQ_NO` per-tenant (`MAX+1` for `BANK_CERT_INFO` rows where `BIZ_KEY = TenantContext.currentBizKey()`); encrypts the password via [`BankCertCryptoService`](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java) before writing `CERT_INFO_DETL_CON`. The selected-account rows from `SelectBankAccountsDialog` are **not** forwarded today — per-account registration into `ACCT_MSTR` is deferred (OQ-5).

### 4.5 Update password

```http
PUT /api/v1/login-credentials/bank/{seqNo}
Content-Type: application/json

{
    "password":     "new-plaintext",
    "old_password": "current-plaintext"               // backend rejects with WBE2091 if mismatch
}
→ 200 ApiResponse<void>
```

Service runs in this order:

1. Load the row by `(BIZ_KEY, SEQ_NO)`. Missing → `WBE_NOT_FOUND`.
2. Decrypt `CERT_INFO_DETL_CON` and compare to `old_password`. Mismatch → `WBE2091`.
3. Encrypt the new password and `UPDATE BANK_CERT_INFO SET CERT_INFO_DETL_CON = ?, MOD_DTM = NOW(), MOD_ID = TenantContext.currentUserId() WHERE BIZ_KEY = ? AND SEQ_NO = ?`.

The frontend always **also re-verifies the new password against Coocon** before firing the PUT — this catches "the user typed a new password that the bank also doesn't accept" early. The PUT runs only when Coocon returns `COMPLETED` (or `COMPLETED` after the user satisfies the OTP). Legacy fidelity note: Coocon checks the new password's validity at the bank; the backend's `WBE2091` checks the user typed the right *current* password — both guards must pass.

### 4.6 Delete (backend ready, frontend deferred per OQ-4)

```http
DELETE /api/v1/banking/credentials/{seqNo}
→ 200 ApiResponse<void>
```

Hard-delete from `BANK_CERT_INFO`. The Settings panel's kebab → Delete fires only the confirmation dialog today; the actual DELETE call is not wired.

---

## 5. Backend slice

Backend module layout — two adjacent packages share the underlying `BANK_CERT_INFO` table but expose different REST surfaces:

| Package | Endpoints | Role |
|---|---|---|
| `domain/banking/bankcredentials/` | `GET / POST / PUT / DELETE /api/v1/banking/credentials` | Generic CRUD over `BANK_CERT_INFO`. Predates the Settings panel; powers the older spike. |
| `domain/banking/scraping/` | `POST /api/v1/banking/scraping/verify`, `POST /api/v1/banking/scraping/{sessionId}/otp`, `POST /api/v1/banking/scraping/account-list` | Coocon NXISAS orchestration. Verify-before-save + OTP follow-up + scrape-from-saved-row. |
| `domain/login-credentials/` *(planned consolidation)* | `POST / PUT /api/v1/login-credentials/bank/...` (live), `POST /api/v1/login-credentials/gdt/...` (planned) | Settings-panel-facing surface that wraps the verify+persist policy into one endpoint per institution kind. The bank slice is live today; GDT lands with OQ-1. |

> The dual surface (`/banking/credentials` vs. `/login-credentials/bank`) is a transitional artifact. The Settings panel uses `/login-credentials/bank` for create/update because that surface wraps the verify+persist policy into one endpoint; the generic CRUD path under `/banking/credentials` stays available for the list query (no overlap) and the deferred delete wire-up. A future MR may collapse the two — see OQ-6.

**Key classes:**

- [BankCertInfo](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/entity/BankCertInfo.java) — entity, composite PK `(BIZ_KEY, SEQ_NO)`.
- [BankCertInfoRepository](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/repository/BankCertInfoRepository.java) — `findByBizKey`, `findMaxSeqNo`.
- [BankCertCryptoService](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java) — encrypt/decrypt for `CERT_INFO_DETL_CON`.
- [BankCredentialService](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCredentialService.java) — list / create / update / delete / `loadForScraping`.
- [BankCredentialController](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/controller/BankCredentialController.java) — REST CRUD.

**Schema:** `BANK_CERT_INFO` is part of the PROD Active Table List ([wecms-api/CLAUDE.md §"PROD DB Table Scope for Refactoring"](../../../CLAUDE.md)). Column-for-column DEV parity is preserved per the Legacy Schema Parity Rule. The password column is `CERT_INFO_DETL_CON` (legacy varchar holding the encrypted blob); plaintext never lands in the row.

**Tenant scope:** every query / write is constrained on `BIZ_KEY = TenantContext.currentBizKey()`. The route does not carry `bizKey`; the JWT does.

**Crypto policy:** AES-GCM via `BankCertCryptoService`. Key material is sourced from the same KMS path the legacy NXISAS client uses (env-backed locally). The plaintext password is loaded ONLY by the scraping flow (`loadForScraping`) and decrypted in memory just long enough to call Coocon; the controller layer never sees plaintext on the read path.

---

## 6. Error codes

| Code | Where | Meaning |
|---|---|---|
| `WBI0000` | Every success | OK |
| `WBE_VALIDATION` | `@Valid` failure on request body / path | Missing `bank_cd` / `login_id` / `password`, malformed `seqNo`, blank `value` on OTP submit |
| `WBE_BANK_NOT_SUPPORTED` | Frontend (Add flow) | Selected bank has no NAPAS code mapped — surfaced as a system error before the verify call leaves the browser |
| `WBE_SCRAPE` | Coocon gateway returned `ERROR` | Body carries the gateway's `error_code` + `error_message` |
| `WBE_OTP_NO_SESSION` | Frontend | Gateway said `OTP_REQUIRED` but supplied no `session_id` — unresumable state |
| `WBE_UNSUPPORTED_CHALLENGE` | Frontend | Gateway returned `CAPTCHA_REQUIRED` / `QNA_REQUIRED` — not wired in this slice |
| `WBE2091` | `PUT /api/v1/login-credentials/bank/{seqNo}` | `old_password` doesn't match `decrypt(CERT_INFO_DETL_CON)` (legacy `comm_0218_004_u001_act.jsp:55-57` parity) |
| `WBE_NOT_FOUND` | `PUT` / `DELETE` against unknown `seqNo` | Row missing OR belongs to a different tenant (single envelope merges both — never leak tenant existence) |
| `WBE_TRANSPORT` | Frontend axios wrapper | Pure network failure / response wasn't a valid `ApiResponse` envelope |
| `WBE9999` | Catch-all | Unexpected server fault — logged with traceId |

Error envelopes always include `path` + `trace_id` per [wecms-api/CLAUDE.md §API/DB Rules → Response envelope](../../../CLAUDE.md). The frontend surfaces every error through `useDialog().open({ type: "error", errorCode, description, … })` — never inline banner text.

---

## 7. Audit trail (wired — RESOLVED OQ-2, 2026-06-02)

The Settings panel exposes an Audit Logs link that opens [AuditLogsDialog](../../../../wecms-web/components/shared/popups/AuditLogsDialog.tsx), fetched live via `fetchLoginCredentialAuditLogs` against `GET /api/v1/login-credentials/{type}/{seqNo}/audit-logs` (no mock fallback).

The panel queries:

```sql
SELECT REG_DTM, REG_ID, AUDIT_ACTV_CD, AUDIT_DETL
FROM AUDIT_LOGS_INFM
WHERE BIZ_KEY = :bizKey
  AND WSVC_ID = 'login-credentials'                  -- refactor slug, NOT 'comm_0218_003'
ORDER BY REG_DTM DESC
LIMIT 100;
```

**Audit-write side** (to be added in the same MR that wires the read):

| Event | `AUDIT_ACTV_CD` | Trigger |
|---|---|---|
| Credential created | `INSERT` | `BankCredentialService.create` on successful persist |
| Password changed | `UPDATE` | `BankCredentialService.update` on successful PUT |
| Credential deleted | `DELETE` | `BankCredentialService.delete` on successful DELETE |
| Verify attempted | `VERIFY` | `ScrapingService.verify` regardless of outcome (logs `bank_cd` + outcome, never the password) |

The `AUDIT_LOGS_INFM.WSVC_ID` column stores the refactor slug `login-credentials` per the [wecms-api/CLAUDE.md §Refactor identifier naming](../../../CLAUDE.md) policy. Earlier revisions debated keeping the legacy `comm_0218_*` ids for cross-system audit-query compatibility — that bucket was retired 2026-05-09 (root [CLAUDE.md §Cross-Tree Rules](../../../../CLAUDE.md)), so new writes use the slug.

---

## 8. Security

- **Password ciphertext never leaves the server.** List responses ship `username` + status fields only. Reveal flow (OQ-3) will need an explicit per-row decrypt endpoint guarded by a fresh re-auth (PasswordConfirmationDialog already collects the user's WeCMS login password as the gate).
- **Plaintext password in transit** is HTTPS-only (every WeCMS API call is). The frontend's transient `loginDraft` / `editDraft` state lives in React state, never `localStorage` / `sessionStorage`.
- **Verify-before-save** is the design guarantee: a credential row only lands in `BANK_CERT_INFO` after Coocon confirms the credentials work. This prevents the panel from filling the table with stale / wrong-bank / typoed entries that would later fail every scrape.
- **OTP session id** is opaque and short-lived (scoped to the verify call's gateway session). The frontend treats it as an opaque token — never inspects, parses, or stores it long-term.
- **Tenant isolation** is enforced by `TenantContext.currentBizKey()` on every query. The route does not accept a `bizKey` parameter; a request body cannot override the JWT-bound tenant.
- **Crypto key rotation** — when the Coocon-side AES key rotates, `BankCertCryptoService` accepts the legacy key for decrypt while writing under the new key (transparent upgrade pattern, mirrors the auth password `WecmsPasswordEncoder` policy). Not yet exercised; document the rotation event in this file when it happens.

---

## 9. Tests

Backend ([wecms-api/src/test/java](../../../src/test/java) — to be added):

- `BankCredentialServiceTest`
  - `create_persistsEncryptedPassword`
  - `create_allocatesSeqNoPerTenant`
  - `update_rejectsBadOldPassword_WBE2091`
  - `update_encryptsNewPassword`
  - `delete_hardDeletes`
  - `loadForScraping_returnsDecryptedPlaintext`
- `BankCertCryptoServiceTest`
  - `roundTrip_encryptThenDecrypt_returnsOriginal`
  - `decrypt_legacyKeyMaterial_stillWorks` (when rotation lands)
- `ScrapingServiceTest`
  - `verify_passes_returnsCompleted`
  - `verify_otpRequired_returnsSessionId`
  - `verify_gatewayError_returnsErrorEnvelope`
  - `submitOtp_completed_returnsAccounts`

Frontend ([wecms-web/__tests__](../../../../wecms-web) — to be added):

- `LoginCredentialsPanel.test.tsx`
  - Add chain — institution → bank → login-id-pw → verify (mocked) → select-bank-accounts → save → success
  - Add chain — verify returns OTP_REQUIRED → otp dialog → submit → completed
  - Edit chain — open Registered → pencil → edit-login-id-pw → verify + PUT → success
  - Edit chain — old_password mismatch → WBE2091 error dialog → drop back to row
  - Cancel on any step collapses the whole chain back to the panel

Until tests land, the integration check is **manual against the local Coocon test sandbox** with `BANK_CD = VCB`, login `cms_test`, password (sealed in `Claude Code (AI)/notes/coocon-test-credentials.md`).

---

## 10. Open Questions

> OQ format: question — owner — status — proposed resolution.

**OQ-1 — GDT credentials wire-up. RESOLVED (verified 2026-06-02): GDT create/update wired end-to-end.** Backend exposes `POST` / `PUT /api/v1/login-credentials/gdt`; the frontend [LoginCredentialsPanel](../../../../wecms-web/components/settings/login-credentials/LoginCredentialsPanel.tsx) upserts the GDT branch via `createGdtLoginCredential` / `updateGdtLoginCredential` against those endpoints (no longer non-persisting). The wire base path is `/api/v1/login-credentials` (not `/settings/login-credentials`). Owner: backend.

**OQ-2 — Audit Logs body. RESOLVED (verified 2026-06-02): audit logs wired end-to-end (not mock).** Backend exposes `GET /api/v1/login-credentials/{type}/{seqNo}/audit-logs`; the frontend fetches it via `fetchLoginCredentialAuditLogs` — no static seed / mock fallback remains. Owner: backend.

**OQ-3 — Password reveal endpoint. RESOLVED (verified 2026-06-02): reveal wired end-to-end.** Backend exposes `POST /api/v1/login-credentials/{type}/{seqNo}/reveal`; the frontend's `PasswordConfirmationDialog` calls `revealLoginCredential` to decrypt-and-show on a successful re-auth. Owner: PO + security review (the decided behaviour shipped).

**OQ-4 — Delete wiring in the panel.** Backend endpoint exists (`DELETE /api/v1/banking/credentials/{seqNo}`); the panel's kebab → Delete fires only the confirmation dialog today. Trivial wire-up; bundled with OQ-2 (audit-write) so the delete event records correctly. Owner: frontend + backend. Status: ready to land.

**OQ-5 — Per-account registration into `ACCT_MSTR`. RESOLVED (2026-06-17).** The accounts the user picks in `SelectBankAccountsDialog` ARE now forwarded and persisted. `POST /api/v1/banking/accounts/register` → [`AccountRegistrationService`](../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountRegistrationService.java) upserts each picked account into `ACCT_MSTR` (INSERT new / UPDATE existing, legacy `fnsh_0101_001_c001` parity) and fans out `ACCT_AUTH_INFO` on insert. The frontend `persistPickedAccounts` (in `LoginCredentialsPanel.tsx`) maps the picked rows to the register payload. See § "Select Bank Accounts registration — final state" below. Owner: backend. Status: **DONE**.

**OQ-6 — Consolidate `/banking/credentials` and `/login-credentials/bank` surfaces.** Today the list query goes through the generic CRUD path (`GET /api/v1/banking/credentials`) and the verify+persist write goes through `/login-credentials/bank`. A future MR may collapse the two under one consistent prefix (likely `/api/v1/settings/login-credentials/*`). Owner: backend. Status: deferred — works today; consolidation is hygiene, not function.

**OQ-7 — CAPTCHA / Q&A challenge support.** Some banks return `CAPTCHA_REQUIRED` / `QNA_REQUIRED` instead of `OTP_REQUIRED`. Today the panel surfaces a system error and collapses. Add proper dialogs when a real bank in the local test set demands it. Owner: frontend + backend. Status: deferred until a real consumer.

**OQ-8 — Maturity Date persistence. COMPLETE (2026-06-17).** `maturityDate` is read end-to-end **and persisted** to `ACCT_MSTR.EXPI_DT`. The frontend sends `maturity_date` in the Save payload; `AccountEntry.maturityDate` → `AccountRegistrationService.normalizeExpiDt(...)` normalizes `yyyy-MM-dd` / `yyyyMMdd` → `yyyyMMdd` (stores on insert + update); an invalid/unknown format is skipped (never saved silently). Owner: backend. Status: **DONE**.

**OQ-9 — VAS Code / VAS Account Name persistence. COMPLETE (2026-06-17).** VAS columns render for non-`H` product types and persist to `ACCT_MSTR.VAS_ACCT_CD` / `VAS_ACCT_NM` (`AccountEntry.vasAcctCd` / `vasAcctNm`, set on insert + update). Hidden + omitted for product type `H` (see OQ-11). `VAS_ACCT_SEQ_NO` is left null — the popup collects free-text code/name, not a `BUSI_VAS_ACCT_INFM` seq (legacy only set the seq on the patch branch when present); see Operational Follow-up #2. Owner: backend. Status: **DONE** (seq confirmation pending — operational).

**OQ-10 — Full atomic credential+accounts save. COMPLETE (2026-06-17).** Implemented the legacy `fnsh_0101_001_c001` "all-on-Save" guarantee. The frontend verifies the login **inline** (`POST /banking/scraping/verify` — **no `BANK_CERT_INFO` created during the scrape**) and holds the verified credential in transient state; on Save it calls `POST /api/v1/login-credentials/bank/register` → `LoginCredentialsService.createBankWithAccounts(...)`, which inserts `BANK_CERT_INFO` + registers `ACCT_MSTR` + fans out `ACCT_AUTH_INFO` (+ audit) in **one `@Transactional`**. Empty accounts are rejected before the credential is written; a registration failure rolls the whole tx back. Observable rule holds: **no save ⇒ no `BANK_CERT_INFO`**; successful save ⇒ all three exist. Abandon-cleanup remains as a guarded safety net. Owner: backend + frontend. Status: **DONE**.

**OQ-11 — Product type `H` VAS hiding. COMPLETE (2026-06-17).** The popup hides VAS Code / VAS Account Name and omits them from the Save payload when the session's product type is `H` (`showVas = !isProductType(userStorage.get(), "H")`), mirroring legacy `comm_0218_nxisas_view.jsp` `if (!"H".equals(prdtTypes))`. UX-only gate; the backend enforces product type from the signed JWT. Owner: frontend. Status: **DONE**.

---

## 10a. Select Bank Accounts registration — final state (Phase 1 + Phase 2, 2026-06-17)

`POST /api/v1/banking/accounts/register` → [`AccountRegistrationService.register`](../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountRegistrationService.java) persists the picked accounts (frontend popup: `SelectBankAccountsDialog`). Final state of the remediation:

| Item | Status | Backend detail |
|---|---|---|
| Account Type auto-mapping | ✅ **COMPLETE** (OQ — see flow audit) | `NxisasAcctTypeMappingRepository` resolves raw `(bankCd, ccy, NXISAS_ACCT_TYPE)` → `ACCT_TYPE_CD` in `runAcctList`; wire `acct_type_cd`; popup shows only Current/Savings/Fixed Deposit, auto-selects mapped code, blank+manual when unmapped, never raw. `acct_type_cd` whitelisted (`02`–`08`, default `03`). |
| Per-account `ACCT_MSTR` upsert | ✅ Done (OQ-5) | INSERT new / UPDATE existing; `BANK_CD` resolved server-side off the credential; `PRN_SEQ` via `wb_get_next_val`; `ACCT_AUTH_INFO` fan-out on insert. |
| **Maturity Date** | ✅ **COMPLETE** (OQ-8) | Gateway `NxisasAcctListEntry.maturityDate` → `ScrapedAccount.maturityDate` → wire `maturity_date` → **persisted** to `ACCT_MSTR.EXPI_DT` via `normalizeExpiDt` (yyyyMMdd; invalid skipped). Displayed read-only in the popup. |
| **VAS Code / VAS Account Name** | ✅ **COMPLETE** (OQ-9) | Render for non-`H`; persist to `ACCT_MSTR.VAS_ACCT_CD` / `VAS_ACCT_NM` (insert + update). `VAS_ACCT_SEQ_NO` null — see Operational Follow-up #2. |
| **Product type `H`** | ✅ **COMPLETE** (OQ-11) | VAS hidden + omitted from payload when product type = `H` (`showVas` gate). |
| **Balance** | ✅ Saved, not displayed | `AccountEntry.bal` persisted to `ACCT_MSTR.BAL` (INSERT + UPDATE). `REAL_AMT` set to `0` (not persisted — matches legacy, which never writes `REAL_AMT`). No Balance column in the UI (legacy parity). |
| **Atomic save** | ✅ **COMPLETE** (OQ-10) | Inline verify (no `BANK_CERT_INFO` during scrape) + `POST /bank/register` → `createBankWithAccounts` (one `@Transactional`: credential + `ACCT_MSTR` + `ACCT_AUTH_INFO` + audit). No save ⇒ no credential. Abandon-cleanup kept as safety net. |

**Readiness:** **SIT Ready — YES**, conditional on the operational follow-ups below (DBA seed + live-gateway verification). **UAT Ready — NO** until the SIT live-gateway pass and the `VAS_ACCT_SEQ_NO` confirmation.

### Operational Follow-ups

All five feature items above are COMPLETE in code. The remaining items are operational, not development:

1. **DBA — run the `NXISAS_ACCT_TYPE_MAPPING` seed** per env: [Claude Code (AI)/sql/nxisas_acct_type_mapping_20260617.sql](../../../../Claude%20Code%20(AI)/sql/nxisas_acct_type_mapping_20260617.sql) (DDL + 18 rows, idempotent). Off-inventory table → also needs PO (Mr. Jung) baseline sign-off + `legacy-table-inventory.md` update. Auto-mapping degrades to manual selection until seeded (safe).
2. **PO/DBA — `VAS_ACCT_SEQ_NO` confirmation.** VAS Code/Name persist; the `BUSI_VAS_ACCT_INFM` seq FK is left null (the popup has no seq source). Confirm whether the seq must be resolved/persisted.
3. **SIT — live-gateway verification** on a licensed Windows origin: real `maturityDate` value/format, VAS round-trip, and the atomic Save against the live Coocon gateway (cannot run headless).

Frontend counterpart: [wecms-web/docs/features/settings/login-credentials.md](../../../../wecms-web/docs/features/settings/login-credentials.md) (§ Select Bank Accounts popup — remediation status). Source audits: [docs/audits/login-credentials-legacy-vs-refactor-audit.md](../../../../docs/audits/login-credentials-legacy-vs-refactor-audit.md).

---

## 11. JEX → Refactor field mapping

| JEX (BANK_CERT_INFO) | Refactor wire (`ApiBankCredentialRow`) | Notes |
|---|---|---|
| `BIZ_KEY` | *(not on wire)* | Derived from `TenantContext.currentBizKey()` |
| `SEQ_NO` | `seq_no` | Tenant-scoped MAX+1 on create |
| `BANK_CD` | `bank_cd` | NAPAS code |
| `CERT_INFO_DV` | *(not on wire today)* | Discriminator for sub-types (Corporate / Personal / OTP / EMAIL / SMS); request body carries `nxisas_gb_seq_no` / `login_type` |
| `CERT_INFO_CON` | `username` | Login ID (plaintext) |
| `CERT_INFO_DETL_CON` | *(never on wire)* | Encrypted password ciphertext |
| `ACCT_LIST_INQ_STTS` | `acct_list_inq_stts` | Last scrape outcome code |
| `ACCT_LIST_INQ_MSG` | `acct_list_inq_msg` | Last scrape outcome message |
| `LST_ACCT_LIST_INQ_DT` | `lst_acct_list_inq_dt` | Last scrape timestamp |
| `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` | `reg_dtm` / `mod_dtm` (id fields suppressed) | Audit columns from `BaseEntity` |

---

## 12. Status snapshot

| Layer | Status | Reference |
|---|---|---|
| Backend CRUD | ✅ Complete | [BankCredentialController](../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/controller/BankCredentialController.java) |
| Backend Coocon orchestration | ✅ Complete | `domain/banking/scraping/` |
| Backend audit-logs (read) | ✅ Wired | OQ-2 (RESOLVED 2026-06-02) — `GET .../audit-logs` |
| Backend delete wiring (frontend caller) | ⏳ Deferred | OQ-4 |
| Backend GDT slice | ✅ Wired | OQ-1 (RESOLVED 2026-06-02) — `POST` / `PUT /api/v1/login-credentials/gdt` |
| Backend password-reveal endpoint | ✅ Wired | OQ-3 (RESOLVED 2026-06-02) — `POST .../reveal` |
| Frontend Settings panel | ✅ Complete | [LoginCredentialsPanel.tsx](../../../../wecms-web/components/settings/login-credentials/LoginCredentialsPanel.tsx) |
| Frontend Add chain (verify → OTP → persist) | ✅ Wired | `handleVerifyLogin` / `handleOtpSubmit` / `handlePersistCredential` |
| Frontend Edit chain (verify + WBE2091 → PUT) | ✅ Wired | `handleEditSave` / `handleEditOtpSubmit` / `persistEditedPassword` |
| Frontend GDT chain | ✅ Wired | OQ-1 (RESOLVED 2026-06-02) — `createGdtLoginCredential` / `updateGdtLoginCredential` |
| Frontend Audit Logs body | ✅ Wired | OQ-2 (RESOLVED 2026-06-02) — `fetchLoginCredentialAuditLogs`, no mock |
| Frontend Delete | ⏳ Static UI | OQ-4 |
| Frontend Password Reveal | ✅ Wired | OQ-3 (RESOLVED 2026-06-02) — `revealLoginCredential` |
| Standalone `/banking/credentials` spike route | 🗑 Removed | The earlier `app/(dashboard)/banking/credentials/` route was the verify-before-save spike that predated the Settings panel. Removed — the Settings panel is the canonical UI surface. |
| Select Bank Accounts → `ACCT_MSTR` register | ✅ Wired (2026-06-17) | OQ-5 RESOLVED — `POST /api/v1/banking/accounts/register` + `AccountRegistrationService` (see §10a) |
| Select Bank Accounts popup layout / Account Type / orphan cleanup | ✅ Fixed (2026-06-17) | Fixed height + body-only scroll; functional Account Type + Save validation; atomic save (no orphan). See frontend doc §"Select Bank Accounts popup". |
| Account Type auto-mapping | ✅ **COMPLETE** | `NXISAS_ACCT_TYPE_MAPPING` raw→`ACCT_TYPE_CD` server-side; 3 business labels; blank+manual when unmapped. DBA seed = operational follow-up #1. |
| Maturity Date | ✅ **COMPLETE** (OQ-8) | Gateway field wired + rendered read-only + persisted to `ACCT_MSTR.EXPI_DT` (`normalizeExpiDt`). |
| Balance | ✅ Saved, not displayed | `ACCT_MSTR.BAL` persisted; `REAL_AMT` not persisted (legacy parity); no UI column. |
| VAS Code / Account Name | ✅ **COMPLETE** (OQ-9) | Persist to `VAS_ACCT_CD`/`VAS_ACCT_NM` (non-`H`); hidden for `H` (OQ-11). `VAS_ACCT_SEQ_NO` = operational follow-up #2. |
| Product type `H` VAS hide | ✅ **COMPLETE** (OQ-11) | `showVas` gate, legacy parity. |
| Full atomic save | ✅ **COMPLETE** (OQ-10) | Inline verify + `POST /bank/register` → `createBankWithAccounts` (one tx). No save ⇒ no credential. |
| Feature doc (this file) | ✅ Authored | — |
