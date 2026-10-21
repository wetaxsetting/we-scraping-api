# Own Account Transfer — account-password source parity audit (engine error `80002420`)

- **Date:** 2026-08-25
- **System:** Refactor (`wecms-api` + `wecms-web`)
- **Screen:** Banking → Own Account Transfer (`own-account-transfer`, route `/banking/transfer-own-accounts`)
- **Legacy source of truth:** JEX WeCMS `trns_0001_001` page + `trns_0001_007` composer popup; WABOOKS `trns_0001_001` (**newer**, carries Redmine **#47851**)
- **Reported by:** Vietnam dev team via Sonny · **PO direction:** must match legacy
- **JEX-side companion audit:** [`WECMS DEV/WECMS_DOC/docs/banking/internal-transfer/transfer-own-accounts/audits/account-and-transfer-password-source-2026-08-25.md`](../../../../WECMS%20DEV/WECMS_DOC/docs/banking/internal-transfer/transfer-own-accounts/audits/account-and-transfer-password-source-2026-08-25.md) — the full three-way legacy comparison, evidence index E-1…E-19, and the JEX-only findings. **Not duplicated here.**

```
Investigation Status:  COMPLETE
Approval Status:       APPROVED  — Sonny, 2026-08-26
Implementation Status: COMPLETE  — see the bug record below
```

> Per [`audits/README.md`](../README.md), this audit stops at the findings — and it **stays here
> unchanged**. The approved fix has its own record, which links back to this document as evidence:
> [`../../bugs/own-account-transfer-account-password-2026-08-26.md`](../../bugs/own-account-transfer-account-password-2026-08-26.md)
> (frontend half: [`wecms-web/docs/bugs/own-account-transfer-account-password-2026-08-26.md`](../../../../wecms-web/docs/bugs/own-account-transfer-account-password-2026-08-26.md)).
>
> **What was approved (2026-08-26) is not §5's Phase 1 + Phase 2 as written below.** The PO chose
> **Model A**: the user types the password on every transfer and the refactor writes it to
> `ACCT_MSTR` on success but **never reads it back**. That drops the Account Management registration
> screen (§5 Phase 1 item 5) and the read path (§5 Phase 2 items 6-8) entirely, and leaves the
> refactor write-only on this column pair — no decrypted bank password ever reaches the browser.
> §5 is preserved as the analysis that led there; the bug record is authoritative for what shipped.

---

## 1. Symptom

On SIT (`wecms.kosign.dev`), an IBK VN → IBK VN own-account transfer
(`076400794420001` → `076400794420002`, 10.000 VND) fails at the engine step:

> **Error** — An account password was not entered. Please check and try again. **(Error Code : 80002420)**

The same transfer works on both legacy systems.

`80002420` is a **Coocon/NXiSAS vendor code** meaning *계좌 비밀번호를 입력하지 않으셨습니다*
("the account password was not entered") —
`SOURCE_CODE_ADMIN/WABOOKS_ADMIN_STATIC/web/js/comm/error_msg.js:103`. The refactor rendered the
bank's own text verbatim through `getScrapingMessage`
([`OwnAccountTransferComposerDialog.tsx:2195-2204`](../../../../wecms-web/components/banking/own-account-transfer/OwnAccountTransferComposerDialog.tsx)),
so **`wecms-api` was never involved** — the bank rejected the job.

It is an **account**-password error, not a transfer-password error: the vendor uses `800024xx` for
계좌 비밀번호 and `800032xx` for 이체 비밀번호, and legacy's own
`errorTrnsPwdCode = ["80003230","80003231","80003233".."80003236"]` does not contain `80002420`.
**IBK needs one password, not two.** (Only KEB Hana sends `transPWD`, and it is outside the
refactor's allow-list.)

---

## 2. Root cause

The refactor sends `acctNumPWD: ""` and there is **no code path by which any other value could
arrive**.

| Step | Code | Effect |
|---|---|---|
| 1 | [`use-own-account-transfer.ts:62-70`](../../../../wecms-web/components/banking/own-account-transfer/hooks/use-own-account-transfer.ts) — `OAT_BANK_PASSWORD_REQUIRED = [Shinhan, Woori, KEBHana]` | IBK `11704003` absent ⇒ `isPasswordRequiredFor` → `false` |
| 2 | [`OwnAccountTransferComposerDialog.tsx:1669-1681`](../../../../wecms-web/components/banking/own-account-transfer/OwnAccountTransferComposerDialog.tsx) — `disabled={!pwdRequired}` | the input is **disabled**; no asterisk |
| 3 | same file `:332` — `useState<string>("")` | `acctPassword` has no other writer |
| 4 | [`bank-profiles.ts:231`](../../../../wecms-web/components/banking/own-account-transfer/bank-profiles.ts) — `acctNumPWD: draft.acctPassword` | `""` reaches the engine |

### 2.1 The mistranslation

`bank-profiles.ts` and the composer both carry this comment:

> *"legacy `trns_0001_007.ts:1124-1149` disables the field for Vietin/Vietcom/IBK (**their engines
> don't read `acctNumPWD`**)"*

**That clause is factually wrong for IBK.** Legacy `trns_0001_007.ts:1943` assigns
`_input_bal.acctNumPWD = acctNumPwd` for IBK unconditionally. Legacy disables the *input box*
because the value comes from **`ACCT_MSTR.ACCT_PWD`** (stored, `encrypt()`/`decrypt()`), surfaced via
`IDO.ACCT_MSTR_R015:115` → the sender-account combo's `dataAcct` attribute →
`#snd_acct_info[data-acct]` → `ACCT_NUM_PWD`. When that stored value is empty, legacy **blocks the
transfer** and opens the `fnif_0002_006` registration popup (`trns_0001_007.ts:675-682`).

The refactor copied *the disabling* and not *the replacement source*.

### 2.2 The storage half was never migrated

| Legacy artefact | Refactor status |
|---|---|
| `ACCT_MSTR.ACCT_PWD` / `TRNS_PWD` read (`decrypt`) | ❌ `ACCT_PWD` appears **once** in all of `src/main` — as a column in `db/migration/V2__acct_mstr.sql:49-50`. No entity, repository, query or DTO reads it. |
| `AcctMstr` entity password fields | ❌ [`AcctMstr.java`](../../../src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java) has **no** password field (`grep -i pwd` → nothing) |
| `SourceAccountRow` carries it | ❌ [`SourceAccountRow.java`](../../../src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/dto/response/SourceAccountRow.java) carries the **bank cert** (`certInfoCon` / `certInfoDetlCon`) only |
| `fnif_0002_006` / `_007` / `_008` / `_009` popups | ❌ absent from both refactor trees (`grep fnif_0002_006` → 0 hits) |
| Account Management password CRUD (`fnif_0002_001:381-403`) | ❌ absent |

**Consequence:** every `acct_mstr` row the refactor creates via `AccountRegistrationService` has
`acct_pwd = NULL` **by construction**. Confirmed by the reporter's read-only query on
`172.168.5.172/wecmsdevdb` (SIT shares the dev DB):

| bank_cd | accts | with_acct_pwd | with_trns_pwd |
|---|---|---|---|
| `11704001` · `11704003` · `11704004` · `11704013` | 3 each | 0 | 0 |
| `11704088` (Shinhan) | 23 | 0 | 0 |
| `11704020` (Woori) | 5 | 0 | 0 |
| `11704007` · `11704011` · `11704080` · `11704100`…`11704108` | 1-2 each | 0 | 0 |
| **17 banks / 54 accounts** | | **0** | **0** |

Shinhan and Woori transfer fine despite the zeros because the refactor takes **their** password from
the typed input — which is exactly why IBK, whose input is disabled, is the one that fails.

### 2.3 This was a known deferral

`docs/jex/features/own-account-transfer/legacy-parity-verification.md:55` (Note ①) and
`wecms-api/docs/features/banking/internal-transfer-vnd/own-account-transfer/legacy-spec.md:316`
already record it: *"credential fields … (`acctNumPWD`, `transPwd`, `acceptName`) ship as null today
— composer collection ships in a separate UX MR before PROD rollout"* (OQ-FE-1).

**What is new in this audit:** the deferral has become a live defect on first real IBK use; the DB
evidence shows the write path is missing too, which **fixes the build order**; and WABOOKS #47851
changes the recommended target design.

---

## 3. 🔴 Do not "just enable the input"

The obvious one-line fix — add `"11704003"` to `OAT_BANK_PASSWORD_REQUIRED` — is **the design WABOOKS
removed from production**. Redmine **#47851** (WABOOKS `trns_0001_001.ts`, mtime **2026-07-30**, the
newest transfer code in either legacy tree):

```ts
//#47851: acctNumPWD must come ONLY from Account Management (registered password) —
//never from the on-screen field (browser autofill was injecting the login password)   // :627-628

//#47851: browsers autofill the saved login password into this field,
//which silently bypasses the WBE1181 "not registered" check — wipe it after render    // :993-994

//#47851: final guard — these banks require the account password;
//never call the scraping module with an empty value                                    // :1635-1636
if ((bank_cd == "11704003" || bank_cd == "11704080" || bank_cd == "11704088")
    && acctNumPwd == "") { _v2common.app.showMessage("WBE1181"); return; }
```

#47851 also moved **Shinhan** out of the typed camp entirely, hid the field for
IBK/KEB Hana/Shinhan (`:989-990`), and added `autocomplete="new-password" readonly onfocus=remove`
plus a 500 ms value wipe on the remaining banks' fields.

**The refactor was ported from the WeCMS `trns_0001_007` generation, which predates all of that.**
Target the #47851 shape, not the shape we copied.

---

## 4. Legacy → refactor gap table

| # | Legacy behaviour (source of truth) | Refactor today | Gap |
|---|---|---|---|
| G-1 | `acctNumPWD` for IBK read from stored `ACCT_MSTR.ACCT_PWD` (`R015:115` → `dataAcct` → `data-acct` → `:1943`) | typed input only, and disabled for IBK | 🔴 sends `""` |
| G-2 | Blocks the transfer + opens the registration popup when not stored (`:675-682`); #47851 adds `WBE1181` guidance first | no gate at all — calls the engine with `""` | 🔴 |
| G-3 | #47851 hard guard immediately before the scraping call for IBK/KEB Hana/Shinhan (`:1635-1641`) | none | 🟠 |
| G-4 | Password registration in Account Management — add/edit × account/transfer (`fnif_0002_001:381-403` → `006`/`008`/`007`/`009`) | screen and popups do not exist | 🔴 |
| G-5 | Write via legacy `encrypt(:ACCT_PWD)` (`IDO.ACCT_MSTR_U001:55-56`); edit verifies the old value in SQL (`fnif_0002_008_u001_act.jsp:74-75`) | no writer | 🔴 |
| G-6 | Registered-yes/no exposed as `ACCT_PWD_YN`/`TRNS_PWD_YN` (`IDO.ACCT_MSTR_R003:147-155`) to drive Add vs Edit | not exposed | 🟠 |
| ~~G-7~~ | ~~`_ML` jobs only *register* the transfer; legacy warns on Transfer History (`trns_0003_001.ts:96,140`)~~ | ~~no such notice anywhere~~ | ~~🟠 separate defect~~ **← WITHDRAWN 2026-08-27. The premise was wrong — WeCMS legacy does not show this notice either. See G-7a.** |
| **G-7a** | **WeCMS legacy shows NO multi-level-approval notice.** The JS is present and byte-identical to WABOOKS (`trns_0003_001.ts:96,140` ↔ WABOOKS `:91,112`), but it writes into `.title_info`, and **that element does not exist in the WeCMS view tree** — `grep -rln "title_info" "WECMS DEV/WECMS/web"` returns **nothing**. WeCMS rebuilt the header (`title-area sp-btw ks-border-none` + bare `<h1>`, `trns_0003_001_view.jsp:114-115`) and dropped WABOOKS' `exclamationWrap` block (`WABOOKS .../trns_0003_001_view.jsp:96-104`). The `.find()` matches nothing ⇒ **silent no-op**. Confirmed on UAT `test.wecms.vn/trns_0003_001.act` (Sonny's screenshots, 2026-08-27): no ⓘ icon, no notice | Also absent | 🟡 **NOT a parity gap and NOT a refactor regression** — the refactor matches WeCMS legacy exactly. Only **WABOOKS** renders it. Adding it to the refactor would be a **new feature**, not a parity fix |
| G-8 | KEB Hana needs `acctNumPWD` **and** `transPWD` (`:1971-1972`) | KEB Hana not in the allow-list | 🟡 latent — design storage for both columns so this is not a rebuild |
| G-10 | #47851 guards **immediately before the scraping call** — `if (bank needs pwd && acctNumPwd == "") { showMessage("WBE1181"); return; }` (`trns_0001_001.ts:1635-1641`). The UI-level check alone was what autofill silently bypassed | The Model A fix gates only the **Next button** (`OwnAccountTransferComposerDialog.tsx:652` → `:1485`); `handleConfirm` does not re-check `validation.ok` before `callNxisas` | 🟡 **partial — opened 2026-08-26.** A disabled button is not a guard. Lower risk in React than in jQuery (controlled state, derived validation), but a one-line `if (!validation.ok) return;` at the top of `handleConfirm` would close it and match legacy |
| G-9 | Bank-filter dropdown is **name-sorted** — `trns_0001_001_v001.ts:663` requests `comm_0140_001_r001` with `sort=BANK_NM:asc`, then appends any allow-listed bank the service did not return (`:677-681`). PROD renders *IBK · Shinhan · Vietcom · VIETIN · Woori* | Rendered in `allowed_bank_cds` **array order** (`OwnAccountTransferPage.tsx:334-339`), i.e. the declaration order of `ALLOWED_BANK_CDS_DEFAULT:86-91` → *Shinhan · IBK · …* | 🟡 cosmetic — same five banks, first two transposed. **Bank SET is confirmed correct** (Sonny, 2026-08-26, verified against PROD); only the sort differs |

Verified clean (checked, no gap): the per-bank job/`Input` matrix for the 5 allow-listed banks; the
IBK forced fee bearer (`bank-profiles.ts` `forcedBearer:"B", useFeeYn:"N"` = legacy
`TRNS_FEE_VAL="2"`, `trns_0001_007.ts:592-597`); the remark `memo`/`memo2` split for IBK.

**Bank-list parity is exact — 5 banks is correct, not a regression** (verified 2026-08-26).
`OwnAccountTransferConstants.ALLOWED_BANK_CDS_DEFAULT:86-91` is a byte-for-byte match for legacy
`trns_0001_001_r001_act.jsp:144-155`, and `ALLOWED_BANK_CDS_HAENGSUNG:95-96` matches the `H` → VIETIN
narrowing. BIDV `11704008` and KEB Hana `11704080` have `case` blocks in `trns_0001_007.ts` inherited
from the WABOOKS clone but are **absent from WeCMS's allow-list in both enforcement points** — the
server action above and the client filter builder `trns_0001_001_v001.ts:641-647` — so they are
unreachable in legacy too. The refactor omitting them is deliberate parity. Full derivation:
[the JEX-side audit §5.4a](../../../../WECMS%20DEV/WECMS_DOC/docs/banking/internal-transfer/transfer-own-accounts/audits/account-and-transfer-password-source-2026-08-25.md).

---

## 5. Recommended implementation — **build order matters**

> ⚠️ **The read path alone makes things worse.** With `acct_pwd` NULL for every account (§2.2),
> shipping G-1 + G-2 without G-4/G-5 replaces `80002420` with a permanent block pointing at a screen
> that does not exist.

**Phase 1 — write path (`wecms-api` + `wecms-web`).**
1. Add the password fields to `AcctMstr` mapped to the **existing** legacy columns `ACCT_PWD` /
   `TRNS_PWD` (`varchar(100)`, already in `V2__acct_mstr.sql:49-50` — **no DDL, no migration**).
2. Write through the legacy pgcrypto `encrypt()` so live JEX can still `decrypt()` it. Do **not**
   use `BankCertCryptoService`/AES-GCM here — that would make the column unreadable by JEX and break
   coexistence. Cipher precedent: memory `project_cert_info_cipher_incompatible.md`.
3. Endpoints for add + edit, each password type; edit verifies the previous value the way
   `fnif_0002_008_u001_act.jsp:74-75` does (`and acct_pwd = encrypt(?)`).
4. Expose `acct_pwd_yn` / `trns_pwd_yn` (G-6) so the UI can render Add vs Edit.
5. Frontend: the Account Management screen's per-account password controls, mirroring
   `fnif_0002_001:381-403`.

**Phase 2 — read path + gate (`wecms-api` + `wecms-web`).**
6. Add the decrypted value to `SourceAccountRow` (same security envelope as the existing
   `certInfoDetlCon` — same tenant scope, same W-grant filter; document it in the record's Javadoc).
7. Composer: for IBK **hide** the field (#47851 shape — not "enable", not "disable"); take the value
   from the row.
8. Add the pre-engine guard (G-3) + the `WBE1181` guidance string. **Reuse the legacy text**, which
   already exists in EN/KO/VI/KM at `WABOOKS_NEW_ETC/xml/code/RSPS_CD/CD_WBE1181.xml` — EN:
   *"Please register your bank password in Account Management first before making a transfer."*
9. Correct the false comments in `bank-profiles.ts` and `OwnAccountTransferComposerDialog.tsx`
   (§2.1) so the next reader is not misled the same way.

~~**Separate, independent:** G-7 (multi-level-approval notice for Vietcom/VIETIN) — its own bug record.~~
**Withdrawn 2026-08-27** — see G-7a. WeCMS legacy does not show the notice either, so there is
nothing to restore and no bug record is owed. If the team still wants users warned that a
Vietcom/VIETIN transfer is only *registered* until approved in internet banking, that is a **new
feature** needing a PO decision — and a conditional notice on the success dialog would beat
reinstating WABOOKS' static page banner on a different screen.

### Paired API ↔ WEB changes (CLAUDE.md §4)

| API change | Required WEB change |
|---|---|
| `SourceAccountRow` + `acct_pwd` (lowercase snake_case on the wire) | `ApiOwnAccountSourceRow` type + `mappers.ts`; composer reads `source.acctPwd` instead of local state |
| new add/edit password endpoints | Account Management password dialogs |
| `acct_pwd_yn` / `trns_pwd_yn` | Add-vs-Edit button state |

No DB schema change. No table added. `ACCT_MSTR` is table **#4** in
[`legacy-table-inventory.md`](../../references/database/legacy-table-inventory.md) — verified present,
so the baseline rule is satisfied.

---

## 6. Impact and risk

| Area | Note |
|---|---|
| Security | The decrypted password crosses the wire and lands in a DOM attribute in legacy too, so this adds no *new* class of exposure — but it does add a second secret to the OAT response. Keep it inside the existing tenant + W-grant scope, and out of logs, HARs and screenshots. |
| Coexistence | 🔴 The cipher **must** be legacy pgcrypto. A JCE/AES-GCM value in `ACCT_PWD` would be undecryptable by live JEX and would silently break IBK/KEB Hana transfers on the legacy side after cutover. |
| Migration | Zero-transformation: the columns already exist and already hold legacy-encrypted data on the JEX DBs. |
| Product types | `H` sees VIETIN only (`trns_0001_001_r001_act.jsp:146-147`), and VIETIN sends no account password — **H is unaffected** by G-1/G-2. |
| Blast radius | Read path touches `/source-accounts` (OAT only). Write path touches `ACCT_MSTR` update — shared, so regression-test account registration and the account list. |

---

## 7. Open items

| # | Item | Owner | Blocks |
|---|---|---|---|
| B-1 | Run `SELECT bank_cd, COUNT(*), COUNT(acct_pwd), COUNT(trns_pwd) FROM acct_mstr GROUP BY bank_cd` on **JEX DEV/UAT/PROD**. §2.2's zeros are the refactor dev DB only; live JEX population decides whether existing tenants keep working immediately after Phase 2 or must re-register. | Sonny / DBA | Phase-2 rollout plan (not Phase 1) |
| B-2 | PO approval to build Phase 1 + Phase 2 as specified (target the #47851 shape). | Mr. Jung | implementation |
| ~~B-3~~ | ~~G-7 multi-level-approval notice — own bug record?~~ **CLOSED 2026-08-27** — no record owed; the premise was wrong (G-7a). Re-opening it would be a new-feature request, not a defect. | — | — |
| B-4 | Confirm the legacy `encrypt()`/`decrypt()` functions are present and visible on the target JEX DBs — cf. memory `project_jex_db_function_schema_shadowing.md` (functions can land in `public` and be shadowed with no error). | Arthur | Phase 1 |

---

## 8. Conclusion

The refactor's Own Account Transfer is **not carrying a transfer-logic bug**. Both legacy systems
transfer IBK correctly; the refactor cannot, because the **password-storage half of Account
Management was never migrated** and the composer was wired to an input box it then disables.

Closing `80002420` therefore means shipping a feature, in two phases, in order — and shipping it to
the **WABOOKS #47851** design rather than the WeCMS `trns_0001_007` design the refactor was ported
from.

**No source, SQL, migration or configuration was modified by this audit.**
