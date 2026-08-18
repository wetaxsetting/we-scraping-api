# Own Account Transfer — Legacy ↔ Refactor Parity Audit

> **⚠️ SUPERSEDED crypto note (2026-06-24).** Statements below that the cert-read path "uses AES-GCM" are
> **stale**. The bank/GDT credential cipher is now legacy-compatible **pgcrypto AES-256-CBC**
> (`BankCertCryptoService`, byte-identical to `public.encrypt()`/`decrypt()`); the earlier AES-GCM cipher
> was retired. See [../../security/remaining-security-risk-register.md](../../security/remaining-security-risk-register.md) (R-1 / F-08).

- **System:** Refactor (wecms-api + wecms-web)
- **Feature slug:** `own-account-transfer` (legacy JEX `trns_0001_001` main page + `trns_0001_007` transfer popup)
- **Date:** 2026-06-01
- **Scope:** Compare the already-built refactor implementation against the legacy JEX behaviour, catalogue divergences, fix the safe ones, and flag the rest with recommendations.
- **Verdict:** The feature is **implemented end-to-end** in both trees. Structurally the NXiSAS **local-`.exe`** requirement is satisfied. There is **one release-blocker** (SEED-CBC decrypt) that needs a PO/architecture decision, plus a set of narrow parity gaps — most of which are intentional rewrite decisions or deliberate Phase-B deferrals. One safe, isolated correctness fix was applied in this pass (remark UTF-8 byte limits).

> ⚠️ This feature is **money-movement code** and is **not exercisable on localhost** (no bank integration in R&D; localhost is not a Coocon-licensed NXiSAS origin → ErrorCode `80000015`). Every fix below was weighed against that untestability. Speculative edits to engine/credential/persist paths were deliberately **not** made; they are documented with precise file:line recommendations instead.

---

## 1. Feature map (already wired)

| Legacy | Refactor — frontend | Refactor — backend |
|---|---|---|
| `trns_0001_001_v001.ts` main page + `_view.jsp` | `OwnAccountTransferPage.tsx`, `SourceAccountsTable.tsx`, `source-accounts-columns.tsx` | `GET /api/v1/banking/own-account-transfer/source-accounts` |
| `trns_0001_007.ts` transfer popup + `_view.jsp` | `OwnAccountTransferComposerDialog.tsx`, `use-own-account-transfer.ts`, `bank-profiles.ts`, `mappers.ts` | — |
| confirm / OTP / QnA engine popups | `TransferConfirmationDialog.tsx`, `OtpAuthDialog.tsx`, `lib/nxisas/{confirm,otp,qna}-bridge.ts` | — |
| `trns_0001_001_r001` (source list) | `useOwnAccountTransfer().reload` | `OwnAccountTransferService.listSourceAccounts` |
| `trns_0001_001_u001` (balance update) | composer `#update_balance` → `postBalanceRefresh` | `POST .../balance-refresh` |
| `trns_0001_001_r003` (NAPAS lookup) | (target rows already carry `napasBankCd`) | `GET .../napas-bank-info/{napas_bank_cd}` |
| `trns_0002_001_c001` (persist transfer) | composer `submitScrapingResult` | `POST .../scraping-result` → `NAPAS_REMIT_TRNS_HSTR` |
| `trns_0001_005_r001` (my accounts) | `useOwnAccountTransfer().loadTargets` | `GET .../target-accounts` |
| `comm_0500_001_r001` (FX) | (VND-only — not consumed) | `GET /api/v1/banking/exchange-rates` |
| `nxisas_acctList_r003` | `_v2nxisas.getCertInfo` (bundle) | `POST .../nxisas/cert-acct-list` (`OatNxisasService.certAcctList`) |
| `nxisas_getModuleInfo` | static `BANK_NXISAS_PROFILES` map in `jexjs-adapter.ts` (`_isOsmc=false`) | — (no endpoint; handled client-side) |
| `nxisas_decrypt` / `nxisas_encrypt` | `jexjs-adapter.ts` pass-through | `POST .../nxisas/decrypt` `/encrypt` (`OatNxisasService.passthrough`) |
| `nxisas_logging` | bundle `logging()` | `POST .../nxisas/logging` (`OatNxisasService.recordLogging`) |
| local `CooconiSASNX.execute` (browser ↔ `NXiSAS.exe`) | `use-nxisas-engine.ts` + `public/nxisas/` vendor bundle | **never on the scraping path** (correct) |

**Local-`.exe` requirement — satisfied (structurally):** the refactor ships the vendor bundle byte-for-byte (`isasscaping.js`, `nxisas_v2.js` identical; `web_socket.js` differs only in line endings), injects scripts in the exact legacy order with the Flash SWF hint set, and drives BALANCE / TRANSFER / TRANSFER_INTERNAL[_ML] through `_v2nxisas.callNxisas → CooconiSASNX.execute` over `wss://…:14102` to the local agent. Spring is never inserted into the scrape path; the four helper endpoints are genuine support calls. Per-bank job selection in `bank-profiles.ts` faithfully reproduces the legacy `fnTransferConfirmation` quirks for the 5 in-scope banks (Shinhan branch `79616016`, Woori `nonLogin=false`, Vietin fee/transType/memo, Vietcom `transType=""`, IBK/Woori memo split).

---

## 2. Findings

Severity: **CRITICAL** (money/auth correctness) · **MAJOR** (visible functional gap) · **MINOR** (cosmetic/ordering). Status: **FIXED** / **DEFER** (intentional or needs decision) / **FALSE-POSITIVE**.

### Corrected false-positives (verified across both trees — NOT bugs)

| # | Finding (as raised) | Verdict | Evidence |
|---|---|---|---|
| F1 | Transfer-button visibility delegated to a server `can_transfer` instead of the client 3-clause rule | **FALSE-POSITIVE** | Frontend `mappers.ts:50` reads `api.can_transfer`; backend `SourceAccountRow.canTransfer` computes `isAdmin || hasW()` server-side from `ACCT_AUTH_INFO` (`OwnAccountTransferService:208-209`). Both sides agree; the gate is correct. The "wire contract diverges from the migration spec" note is a doc-vs-code mismatch, not a functional defect. (Refactor renders the button **disabled** vs legacy **hidden** — cosmetic, MINOR.) |
| F2 | `nxisas_decrypt` is a no-op pass-through (vs legacy SEED-CBC) — server can't decrypt cert | **PARTLY FALSE / see B1** | `decrypt`/`encrypt` pass-through is the documented design; the **engine-output** decrypt gap is the real issue (B1). The cert-read path is separate and uses AES-GCM. |
| F3 | `cert-acct-list` over-decrypts `CERT_INFO_DETL_CON2/CON3` (legacy decrypts only `CON`) | **NOT A CLEAR BUG** | Refactor is a clean rewrite with its own AES-GCM crypto, not a SEED-CBC port. Whether CON2/CON3 are decrypted depends on the refactor's **own** `BankCertCryptoService` write-path (`BankCredentialController`), not on legacy. `decryptOrEcho` already falls back to echo on failure (`OatNxisasService:253-261`). **Action: confirm the credential write-path encrypts CON2/CON3; if it stores them plaintext, drop the decrypt on those two fields.** Not changed blind. |
| F4 | Pre-submit NAPAS-code validation (`r003` / `WBE1143`) missing in composer | **LOW (scope-narrowed)** | Legacy allowed arbitrary recipients (contact/recent/myacct) so it re-validated NAPAS. The refactor's targets come **only** from `/target-accounts` (the user's own registered accounts), and each `OwnAccountTargetRow.napasBankCd` is server-supplied — so it is implicitly valid. A cheap defensive guard is still recommended (R3). |

### Real gaps

| # | Area | Severity | Status | Legacy (file:line) | Refactor (file:line) | Notes / recommendation |
|---|---|---|---|---|---|---|
| B1 | **Engine-output decrypt** — local exe returns `Output.Result = "ENC=…"` (SEED-CBC) by default (`encMode=true`); legacy decrypts server-side via `ISASSeedCBC` | **CRITICAL — release blocker** | DEFER (PO decision) | `nxisas_decrypt_act.jsp:79`; `isasscaping.js:35,429-436` | `OatNxisasService.passthrough:114-117`; `jexjs-adapter.ts:296-302` (sync pass-through) | For any bank that returns encrypted results, BALANCE/TRANSFER output will not parse (`bal=null`, unreadable confirmation). The "VND paths emit plaintext" comment is **unverified** and contradicts the vendor default. Cannot be a network-backed server call without breaking the bundle's synchronous `nxisas_decrypt` contract (modern browsers forbid sync XHR). **Needs an architecture decision:** (a) confirm in-scope VND banks truly emit plaintext (then keep + document the evidence), or (b) port a JS-side SEED-CBC into the bundle adapter keyed by `CooconiSASNX.OpenUid/OpenAction`. Do **not** ship to any non-local environment until resolved. |
| B2 | Per-bank remark **byte** limits + byte-vs-char counting | CRITICAL (the byte-vs-char half) | **FIXED (byte count)** / DEFER (per-bank matrix) | `trns_0001_007.ts:1258-1316,1542-1544` | composer remark fields + `use-own-account-transfer.ts` validate | **Applied this pass:** remark length now measured in **UTF-8 bytes** at 210 (was JS chars) — fixes the Vietnamese-diacritics over-count (see §3). **Still deferred:** the per-bank/within-vs-inter byte matrix (IBK 35, Vietcom 195/180, Shinhan 200, KEBHana 50, …). 210 bytes is a monotonic improvement (≤ every legacy cap for the 4 banks at 210; stricter than the old 210-char cap everywhere). |
| B3 | Fee-payer radio ↔ remark editability coupling | MAJOR | DEFER | `trns_0001_007.ts:451-493,558-566` | composer renders both remark textareas always-editable | Legacy enables exactly one remark per fee selection. Refactor keeps both editable and posts both. Re-wire onChange enable/disable to the `feeBearer` state; low risk but needs the per-bank fee model. |
| B4 | Vietcom/Shinhan empty remark → `COMP_NM` default | MAJOR | DEFER | `trns_0001_007.ts:568-572` | absent in `handleConfirm` / `bank-profiles.ts` | For Vietcom (`11704004`) / Shinhan (`11704088`) an empty memo should default to the company name. Requires `companyName` from `userStorage` (`StoredUser.companyName`). Bank statement memo currently blank. |
| B5 | HAENGSUNG (`PRDT_TYPE='H'`) Woori `100→DDA` account rewrite | CRITICAL (HS+Woori only) | DEFER | `trns_0001_007.ts:798-811,1564-1568,2036-2044,2213-2223` | absent; `prdtType` not surfaced to composer | HS-tenant Woori accounts beginning `100` must be sent to the engine as `DDA` and reversed on persist. Needs `prdtType` plumbed from `StoredUser.productType` into the composer + `bank-profiles.ts` Woori builder + the scraping-result reverse map. Narrow (single tenant + single bank) but money-correctness for that combo. |
| B6 | Error-status specificity (OTP / transfer-pwd / `LOGIN_CAN` / `8000F110` / `80002411`) | MAJOR | DEFER | `trns_0001_007.ts:862-906,137-140` | composer keeps dialog open on failure but no code-specific routing | The retry-on-error **shape** is preserved (composer stays open); the code-specific branches (`errorOTPCode`/`errorTrnsPwdCode` lists, `LOGIN_CAN`, `8000F110→WBE9999`, `80002411` special message) are collapsed into generic dialogs. Improve once B1 is resolved (decrypt gates whether these codes are even reachable). |
| B7 | Engine-result parse reads flat fields, not nested `Output.Result.transfer[0]` | MAJOR | DEFER | `trns_0001_007.ts:853,2152` | `OwnAccountTransferComposerDialog.tsx parseEngineOutcome` | Refactor reads `trnsStts` from a flat priority list; if the agent nests `ErrorCode` under `result[].Output`, a real success could be recorded as failed. Verify against a real engine payload (blocked on B1). |
| B8 | Persist payload provenance: `fee:null`, `target_owac_nm` from picker nickname, `actual_amt` defaults to requested | MAJOR | DEFER | `trns_0001_007.ts:2160,2164` | `OwnAccountTransferComposerDialog.tsx:447,454` | Legacy carries the engine-returned `fee` / `acceptName` / `remitAmt`. Persisted audit row won't carry bank-authoritative values. Map from the (decrypted) engine result — also blocked on B1. |
| B9 | NAPAS display names (`SHORT_NAME` / `LONG_NAME_EN/VN`) ship null on `napas-bank-info` and `target-accounts` | MAJOR | DEFER | `WSVC.trns_0001_001_r003.xml:46-50`, `trns_0001_005_r001.xml:46-53` | `NapasBankInfoRow` ships null names; `NapasBankInfo` entity doesn't map them | Composer can't render the NAPAS bank name. Map the three name columns on the entity (they exist on `NAPAS_BANK_INFO` per the PROD active list) — backend change. |
| B10 | `nxisas_logging` writes only `NXSIAS_MODULE_TRAN_LOG_HIS`, not `EXT_TRAN_CALL_HIS` | MAJOR | DEFER | `nxisas_logging_act.jsp:29-235` | `OatNxisasService.recordLogging:141-225` | Legacy writes both audit tables; refactor drops the external-call history. Both tables are in the PROD active list. Add the second write (new entity/repo) for audit completeness. |
| B11 | Vietcom DIGIBIZ/PERSONAL class switch not handled (`getModuleInfo` static map pins CORPORATE) | MAJOR (Vietcom DIGIBIZ users) | DEFER | `nxisas_v2.js:877-928`; `trns_0001_007.ts` Vietcom branch | `jexjs-adapter.ts BANK_NXISAS_PROFILES` (static, CORPORATE) | A DIGIBIZ Vietcom user gets the wrong Module/Class header and the job fails. Add a class discriminator to the profile lookup. |
| B12 | Bank-filter ordering lost (`BANK_NM:asc`); page-size default 10 not 15 | MINOR | DEFER | `trns_0001_001_v001.ts:643-685`; `_view.jsp:170-177` | `OwnAccountTransferPage.tsx` (server `allowed_bank_cds` order; shared `PAGE_SIZE_OPTIONS=[10,20,50,100]`) | Cosmetic. Page-size is governed by the shared `usePagination` contract — changing it is a cross-cutting decision, leave as-is. |
| B13 | Account number not run through `bank_format`; bank cell adds a `VND • type` chip + building icon | MINOR | DEFER | `trns_0001_001_v001.ts:485-495` | `SourceAccountsTable.tsx:280-307` | Cosmetic; refactor follows the Account-Inquiry cell pattern. Confirm against the OAT Figma frame. |
| B14 | CAPTCHA + save-password 2nd-factor flows have no React bridge (only OTP/QnA/confirm) | MAJOR (CAPTCHA banks) | DEFER | `nxisas_v2.js` 2nd-cert popups | `lib/nxisas/` (no captcha-bridge) | None of the 5 in-scope banks need CAPTCHA today; add a bridge when one does. Engine error/info also degrade to `window.alert` in `jexjs-adapter.ts`. |
| B15 | Localhost origin-licensing (`80000015`) not surfaced in-UI | MAJOR (dev-UX) | DEFER | n/a | `EngineStatusBanner.tsx` / `use-nxisas-engine.ts` status enum | On localhost the engine open fails generically (`not_installed`/`timeout`) with no hint that the real cause is an unlicensed origin and testing must happen on `wecms.kosign.dev`. Add an `unlicensed` status + banner copy. |

**Refactor-only hardening (NOT gaps — keep):** scraping-result idempotency key + replay short-circuit (`V195`), ownership/amount/transfer-type re-validation, audit-everywhere (OQ-4), balance-refresh active/VND/W-grant guards, JWT-only tenant scoping. These are correct improvements over the legacy JSPs.

---

## 3. Fix applied in this pass

**Remark length now measured in UTF-8 bytes (was JS characters).** Legacy `trns_0001_007.ts:1542-1544` (`validateStringByTES`) measures remarks with `TextEncoder().encode(input).length`; the bank IB sites limit by byte. Vietnamese diacritics encode to 2–3 UTF-8 bytes, so a sub-210-character memo could exceed the bank's 210-byte limit and be silently accepted by the UI, then rejected/truncated downstream.

Files:
- **`lib/text.ts`** (new) — `utf8ByteLength(s)`, `clampUtf8Bytes(s, max)` (clamps from the start, never splitting a multi-byte code point).
- **`components/banking/own-account-transfer/OwnAccountTransferComposerDialog.tsx`** — both remark counters (`n/210`) and both `onChange` clamps now use the byte helpers.
- **`components/banking/own-account-transfer/hooks/use-own-account-transfer.ts`** — `validateDraft` remark guard now `utf8ByteLength(remarkForSender) > 210`.

Scope-limited deliberately: this fixes the byte-vs-char correctness bug **universally** (210 bytes ≤ every legacy per-bank cap of 210, and stricter than the old 210-char cap everywhere). The **per-bank / within-vs-inter-bank** byte matrix (B2 remainder) is left as a follow-up because it depends on reactive bank+target state and cannot be exercised locally.

**Validation:** `npx tsc --noEmit` → clean (exit 0); `npx eslint` on the three touched files → clean (exit 0).

---

## 4. Recommendations (priority order)

1. **R1 — Resolve B1 (decrypt) before any non-local use.** PO/architecture decision: prove in-scope VND banks emit plaintext (document the evidence) **or** port a client-side SEED-CBC into the bundle adapter. This gates B6/B7/B8 too.
2. **R2 — B9 (NAPAS names) + B4 (COMP_NM default) + B3 (fee/remark coupling):** small, visible parity wins; B4/B9 unblock the confirmation display.
3. **R3 — B5 (HAENGSUNG Woori DDA):** plumb `prdtType` from `StoredUser.productType` and add the `100↔DDA` rewrite/reverse for the HS+Woori combo.
4. **R4 — B2 remainder (per-bank remark matrix), B10 (EXT_TRAN_CALL_HIS), B11 (Vietcom DIGIBIZ), B14 (CAPTCHA bridge), B15 (localhost licensing banner):** schedule per-bank/per-need.
5. **R5 — Verify F3 (cert CON2/CON3 decrypt) against `BankCredentialController`'s write-path.** If those fields are stored plaintext, drop the decrypt on them.

All money-path items (B1, B5, B6, B7, B8) must be verified against a **licensed origin** with a real local `NXiSAS.exe` — they cannot be validated on localhost.
