# Settings > Login Credentials — Refactor Feature Spec

**Module:** Settings
**Menu:** Login Credentials
**Refactor screen ID / slug:** `login-credentials`
**Frontend placement:** Settings modal panel ([components/settings/login-credentials/LoginCredentialsPanel.tsx](../../../../wecms-web/components/settings/login-credentials/LoginCredentialsPanel.tsx)).
**View Setting screenId (per [lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts)):** *not adopted* — the panel has no toolbar list-icon button (same precedent as `erp-transaction-history`, `approval-line`, `chart-of-accounts`). The slug is reserved in the registry for future log/audit consistency.
**JEX counterparts:** WSVCs `comm_0218_003_c001` (Add — create bank credential), `comm_0218_004_u001` (Edit — change password with `decrypt(...)` guard), `comm_0218_nxisas` (`enter_login_popup` → `_v2nxisas.callNxisas` LOGIN + ACCT_LIST orchestration). Plus the GDT sibling (`comm_0218_005_*`) for the tax-authority credentials variant.
**Status:** **Phase 3 — wired full-stack.** Backend CRUD over `BANK_CERT_INFO` is live; Coocon LOGIN + OTP + ACCT_LIST orchestration is wired through `/api/v1/banking/scraping/*`; frontend Settings panel composes the full Figma Add and Registered chains end-to-end. GDT branch + Audit Logs body are static-UI until their respective endpoints land (OQ-1, OQ-2).

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
- **Reveal** a masked password — [PasswordConfirmationDialog](../../../../wecms-web/components/shared/popups/PasswordConfirmationDialog.tsx) (static UI — the live reveal endpoint is deferred per OQ-3; password ciphertext stays server-side).
- **Delete** a credential row — kebab menu → confirmation. **Static UI only today** (OQ-4); `DELETE /api/v1/banking/credentials/{seqNo}` exists on the older CRUD slice but is not yet wired into the Settings panel.
- **Audit Logs** — opens [AuditLogsDialog](../../../../wecms-web/components/shared/popups/AuditLogsDialog.tsx) with the per-row event log. **Body is mock data** (`auditLogsMockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced)) — Phase 2 backend port is OQ-2.

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
| Reveal password | **Out of scope** — `PasswordConfirmationDialog` is wired as static UI; the live reveal endpoint requires a separate audit + policy decision (OQ-3). |
| GDT credentials (tax authority) | **Static UI** — [GdtCredentialsDialog](../../../../wecms-web/components/shared/popups/GdtCredentialsDialog.tsx) mounts but doesn't persist. The legacy `comm_0218_005_*` port lands in the GDT feature slice (OQ-1). |
| Audit Logs body | **Static UI** — mock rows; the live query against `AUDIT_LOGS_INFM WHERE WSVC_ID='login-credentials'` (refactor slug, NOT `comm_0218_*`) lands with the audit-write retrofit (OQ-2). |
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

## 7. Audit trail (planned — OQ-2)

The Settings panel exposes an Audit Logs link that opens [AuditLogsDialog](../../../../wecms-web/components/shared/popups/AuditLogsDialog.tsx). Body is mock today (`auditLogsMockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced)).

When wired, the panel queries:

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

**OQ-1 — GDT credentials wire-up.** The GDT branch ([GdtCredentialsDialog](../../../../wecms-web/components/shared/popups/GdtCredentialsDialog.tsx)) renders correctly but doesn't persist. The legacy `comm_0218_005_*` write path needs porting to a new backend slice (likely `POST /api/v1/login-credentials/gdt`) plus a parallel `GDT_CERT_INFO` table audit. Owner: backend. Status: deferred to GDT feature MR.

**OQ-2 — Audit Logs body.** Mock today. Wire to `AUDIT_LOGS_INFM` filtered by `WSVC_ID = 'login-credentials'`. Pairs with the audit-write retrofit in the service layer. Owner: backend. Status: deferred — bundle with the audit-write rollout across Settings panels.

**OQ-3 — Password reveal endpoint.** `PasswordConfirmationDialog` collects the WeCMS user's login password as a re-auth gate but no decrypt endpoint exists. Proposed: `POST /api/v1/login-credentials/bank/{seqNo}/reveal` body `{ current_password }` returns the decrypted password once on success; rejected after N failures (lock the user). Owner: PO + security review. Status: pending decision — risk of broadening the attack surface vs. UX value.

**OQ-4 — Delete wiring in the panel.** Backend endpoint exists (`DELETE /api/v1/banking/credentials/{seqNo}`); the panel's kebab → Delete fires only the confirmation dialog today. Trivial wire-up; bundled with OQ-2 (audit-write) so the delete event records correctly. Owner: frontend + backend. Status: ready to land.

**OQ-5 — Per-account registration into `ACCT_MSTR`.** Today's persist call lands only the credential row in `BANK_CERT_INFO`. The accounts the user picks in `SelectBankAccountsDialog` are intentionally not forwarded — mapping the scraped 8-field shape into `ACCT_MSTR`'s 30+ columns is a follow-up. Owner: backend. Status: deferred until the account-management feature consolidates the schema.

**OQ-6 — Consolidate `/banking/credentials` and `/login-credentials/bank` surfaces.** Today the list query goes through the generic CRUD path (`GET /api/v1/banking/credentials`) and the verify+persist write goes through `/login-credentials/bank`. A future MR may collapse the two under one consistent prefix (likely `/api/v1/settings/login-credentials/*`). Owner: backend. Status: deferred — works today; consolidation is hygiene, not function.

**OQ-7 — CAPTCHA / Q&A challenge support.** Some banks return `CAPTCHA_REQUIRED` / `QNA_REQUIRED` instead of `OTP_REQUIRED`. Today the panel surfaces a system error and collapses. Add proper dialogs when a real bank in the local test set demands it. Owner: frontend + backend. Status: deferred until a real consumer.

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
| Backend audit-write | ⏳ Deferred | OQ-2 |
| Backend delete wiring (frontend caller) | ⏳ Deferred | OQ-4 |
| Backend GDT slice | ⏳ Deferred | OQ-1 |
| Backend password-reveal endpoint | ⏳ Pending PO | OQ-3 |
| Frontend Settings panel | ✅ Complete | [LoginCredentialsPanel.tsx](../../../../wecms-web/components/settings/login-credentials/LoginCredentialsPanel.tsx) |
| Frontend Add chain (verify → OTP → persist) | ✅ Wired | `handleVerifyLogin` / `handleOtpSubmit` / `handlePersistCredential` |
| Frontend Edit chain (verify + WBE2091 → PUT) | ✅ Wired | `handleEditSave` / `handleEditOtpSubmit` / `persistEditedPassword` |
| Frontend GDT chain | ⏳ Static UI | OQ-1 |
| Frontend Audit Logs body | ⏳ Static UI | OQ-2 |
| Frontend Delete | ⏳ Static UI | OQ-4 |
| Frontend Password Reveal | ⏳ Static UI | OQ-3 |
| Standalone `/banking/credentials` spike route | 🗑 Removed | The earlier `app/(dashboard)/banking/credentials/` route was the verify-before-save spike that predated the Settings panel. Removed — the Settings panel is the canonical UI surface. |
| Feature doc (this file) | ✅ Authored | — |
