# Own Account Transfer — Legacy Bank Business-Rules Audit

> **System:** JEX (legacy, source of truth) — analysis for the Refactor (Spring Boot + Next.js)
> **Scope:** "Own Account Transfer" feature (`trns_0001_001` main page + `trns_0001_007` transfer popup + NXiSAS local-engine flow)
> **Date:** 2026-06-01
> **Method:** Read-only inspection of legacy JSP / TypeScript / `_act.jsp` / NXiSAS JS. **No code was modified.**
> **Rule:** Every claim is cited to a legacy `file:line`. Where a rule could not be confirmed it is marked **Unknown / not found** — never guessed.

---

## 1. Executive Summary

The legacy "Own Account Transfer" feature is **not a single uniform flow**. The transfer popup (`trns_0001_007.ts`, 2366 lines) contains **large `switch (bank_cd)` blocks** that change authentication, field enablement, fee handling, remark byte-limits, NXiSAS job type, and payload shape **per bank**. Generalising any one bank's behaviour to another will produce incorrect transfers.

**Banks supported on the main page** (`trns_0001_001_v001.ts:642-655`) are exactly five:

| Bank code | Name (from legacy) | Source |
|---|---|---|
| `11704088` | **Shinhan VN** | `trns_0001_001_v001.ts:651`, `trns_0001_007.ts:998` |
| `11704003` | **IBK** (IBK VN / "IBK Bank") | `trns_0001_001_v001.ts:652`, `trns_0001_007.ts:1004,1061` |
| `11704004` | **Vietcom** (Vietcombank) | `trns_0001_001_v001.ts:653`, `trns_0001_007.ts:986` |
| `11704001` | **VIETIN** (VietinBank) | `trns_0001_001_v001.ts:654`, `trns_0001_007.ts:1022` |
| `11704020` | **Woori VN** | `trns_0001_001_v001.ts:655`, `trns_0001_007.ts:1016` |

Two further bank codes are handled by the popup's `switch` blocks but are **NOT** in the main-page allowed list (legacy/dormant branches kept for shared code):

| Bank code | Name | Status |
|---|---|---|
| `11704080` | **KEBHana** | Branch logic present (`trns_0001_007.ts:992,1042`) but not in main allow-list. Uses external password popups `fnif_0002_006` / `fnif_0002_007`. |
| `11704008` | **BIDV** | Branch logic present (`trns_0001_007.ts:1010,1071`) but not in main allow-list. |
| `11704007` | **Unknown** (requires `corporateID`) | Only referenced in cert logic `trns_0001_007.ts:1587,2032`. Name **Unknown / not found**. |

**Most important bank-specific differences (detail in §6–§7):**

1. **Account-password field is editable for only TWO banks** — Shinhan VN (`11704088`) and Woori VN (`11704020`) (`trns_0001_007.ts:1124-1129`). For all others the field is `disabled`.
2. **Account password is "required" (data-next) for** Shinhan, KEBHana, BIDV, Woori; **not required for** Vietcom, IBK, VIETIN (`trns_0001_007.ts:1029-1094`, default `isPwdRequired=true` at line 953).
3. **KEBHana and IBK collect passwords from separate popups** (`fnif_0002_006` account pwd, `fnif_0002_007` transfer pwd) instead of inline fields (`trns_0001_007.ts:614-681`).
4. **Remark max-length differs by bank and by within-bank vs inter-bank** (35 to 210 **bytes**, UTF-8 counted) (`trns_0001_007.ts:1258-1316`).
5. **Transaction-fee radios are disabled / forced for most banks**; only BIDV & Vietcom keep `trns_detl_use_fee_yn = "Y"` (`trns_0001_007.ts:1474-1532`). IBK forces "Sender pays" label but key `N`/val `2` (`trns_0001_007.ts:593-601`).
6. **NXiSAS job type differs per bank** — `TRANSFER` vs `TRANSFER_ML` vs `TRANSFER_INTERNAL(_ML)` (`trns_0001_007.ts:1930-2015`), plus Vietcom switches to non-ML when module `CLASS == "DIGIBIZ"`.
7. **OTP and Q&A are NOT HTML fields** — they are prompted by the NXiSAS local `.exe` during scraping; the popup JSP contains no OTP/QNA elements (`trns_0001_007_view.jsp`, confirmed full read).

---

## 2. Legacy Files Discovered (Part 1)

### Main page (account list)
| Artifact | Path |
|---|---|
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/trns/trns_0001_001_view.jsp` |
| TypeScript (loaded) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_001_v001.ts` |
| Compiled JS (loaded by JSP) | `…/web/js/jex/wabooks/trns/trns_0001_001_v001.js` (JSP `<script>` include — confirmed) |
| Alternate TS (NOT loaded) | `…/ts-src/js/jex/wabooks/trns/trns_0001_001.ts` (older NAPAS variant — present but the JSP loads `_v001`) |

### Transfer popup (the Own-Account-Transfer UI)
| Artifact | Path |
|---|---|
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/trns/trns_0001_007_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_007.ts` |
| Compiled JS | `…/web/js/jex/wabooks/trns/trns_0001_007.js` |

> The popup is opened from the main page by `#btn_transfer_own` → `_v2common.app.openLayerPopup("trns_0001_007.act")` (`trns_0001_001_v001.ts:330-339`). There is **no separate confirmation-popup file** in this flow — confirmation/scraping/save all happen inside `trns_0001_007.ts` `a#next_step` → `fnTransferConfirmation` → `fnTransferConfirmationCallback`.

### Recipient-selection popup ("My Accounts")
| Artifact | Path | Note |
|---|---|---|
| View JSP | `…/view/jex/wabooks/trns/trns_0001_005_view.jsp` | Title `trns_0001_005_ml005` "My Accounts" |
| TypeScript | `…/ts-src/js/jex/wabooks/trns/trns_0001_005.ts` | Lists user's other accounts (recipient picker), **not** a confirmation popup |

### Backend services (action JSP)
| Service ID | Path | Role |
|---|---|---|
| `trns_0001_001_r001` | `…/action/jex/wabooks/trns/trns_0001_001_r001_act.jsp` | Sender account list (main page) |
| `trns_0001_001_r002` | `…/trns/trns_0001_001_r002_act.jsp` | Recipient/bank list |
| `trns_0001_001_r003` | `…/trns/trns_0001_001_r003_act.jsp` | NAPAS bank-code validation |
| `trns_0001_001_u001` | `…/trns/trns_0001_001_u001_act.jsp` | Balance update after scraping |
| `trns_0001_005_r001` | `…/trns/trns_0001_005_r001_act.jsp` | "My accounts" (recipient combo source) |
| `trns_0002_001_c001` | `…/trns/trns_0002_001_c001_act.jsp` | **Final transfer-record save** → `NAPAS_REMIT_TRNS_HSTR` |
| `comm_0140_001_r001` | `…/comm/comm_0140_001_r001_act.jsp` | Bank/account list (filter + sender combo) |
| `comm_0141_001_r001` | `…/api/comm/comm_0141_001_r001_act.jsp` | VND/FCY left-menu account list |
| `comm_0500_001_r001` | `…/comm/comm_0500_001_r001_act.jsp` | Exchange rate (USD↔local) |
| `comm_0192_007_r002` | `…/comm/comm_0192_007_r002_act.jsp` | Notification count (page chrome, not transfer-core) |
| `comm_0108_001_r001` (+`_osmc`) | `…/comm/comm_0108_001_r001_act.jsp` | User screen/display settings (View Setting) |

### NXiSAS-related files
| Artifact | Path | Role |
|---|---|---|
| Local-engine JS API (v2) | `WECMS DEV/WECMS_STATIC/web/nxisas/js/nxisas_api_v2.js` | `_v2nxisas` object: `openNxisas`, `callNxisas`, `callNxisasWithoutOpenProcess`, `getCertInfo`, `getModuleInfo`, `nxisas_decrypt` |
| Local-engine JS (v1/legacy) | `…/web/nxisas/js/nxisas_api.js`, `nxisas.js`, `nxisas_v2.js`, `nxisas_v2_u000.js` | QNA/OTP modal handling |
| `nxisas_acctList_r003` | `…/action/jex/wabooks/nxisas/nxisas_acctList_r003_act.jsp` | Backend: read certificate info (`getCertInfo`) |
| `nxisas_getModuleInfo` | `…/nxisas/nxisas_getModuleInfo_act.jsp` | Backend: module + CLASS lookup (OSMC external API) |
| `nxisas_decrypt` | `…/nxisas/nxisas_decrypt_act.jsp` | Backend: decrypt scraping result (`ISASSeedCBC`) |
| `nxisas_logging` | `…/nxisas/nxisas_logging_act.jsp` | Backend: write audit rows `EXT_TRAN_CALL_HIS`, `NXISAS_MODULE_TRAN_LOG_HIS` |
| `nxisas_getCertFormInfo` | `…/nxisas/nxisas_getCertFormInfo_act.jsp` | Backend: cert form metadata |

### Bank-specific helper popups
| Popup | Path | Purpose |
|---|---|---|
| `fnif_0002_006` | `…/view/jex/wabooks/fnif/fnif_0002_006_view.jsp` | **Account password** entry/save (KEBHana, IBK) |
| `fnif_0002_007` | `…/view/jex/wabooks/fnif/fnif_0002_007_view.jsp` | **Transfer password** entry/save (KEBHana) |

### Shared combo helpers
| Function | Defined in | Service called |
|---|---|---|
| `common.app.cardComboDataSenderAccount` | `WECMS_STATIC/web/js/wabooks/wabooks.comm_osmc.js` (~line 201) | `trns_0001_001_r001` |
| `common.app.cardComboDataRecipientBank` | same file (~line 108) | `trns_0001_005_r001` |
| `common.app.loadCardComboDataSenderAccount` | same file | `trns_0001_001_r001` (re-load variant) |

> The combo definition file path/line numbers are reported by the sub-agent search; treat the `wabooks.comm_osmc.js` line numbers as approximate (the file is large) — **verify before refactor citation**.

---

## 3. Main Page Event Flow (Part 2 — page-level)

File: `trns_0001_001_v001.ts` (loaded by `trns_0001_001_view.jsp`).

| # | Event / function | Trigger | Input | Side-effect / service | Callback | Validation |
|---|---|---|---|---|---|---|
| 1 | `$(function(){…})` → `osmc.ui.initializeUI` → `trns_0001_001.onload()` | DOM ready | — | Registers `table1` reload + excel format | `onload` | — |
| 2 | `onload` (`:294-315`) | after init | — | `tmsComm.createProgressBar()`; if `!trns_0001.loadedNxisas` calls `_v2nxisas.openNxisas(cb)` after 1000 ms; then `initialize()`, `load()`, `event()`, sort wiring | `openNxisas` cb sets `loadedNxisas=true` | — |
| 3 | `initialize` (`:322-324`) → `fnLoadBankName` | onload | — | Builds bank filter `<li>` list; calls `comm_0140_001_r001.jct?sort=BANK_NM:asc` with `CCY_CD=local` | inline | Filters to allowed bank codes only (`:642-655`) |
| 4 | `load` → `fnLoadData` (`:341-473`) | onload / bank filter click / sort | `{BANK_CD}` (selected `li`) | `trns_0001_001_r001.jct` (sync via sort path); renders rows, footer totals (`TOT_CNT/TOT_BAL/TOT_AVI_BAL`) | inline | `jexjs.isJexError` guard |
| 5 | bank filter click (`:326-328`) | click `.list_bank li` | selected id | `fnLoadData({TO_EXCEL:false})` | — | — |
| 6 | Per-row "Transfer" button visibility (`fnLoadDataRow :529-543`) | render | `USE_YN_V/W`, `AUTH_TYPE_V/W`, `IS_ADMIN` | shows/hides `#btn_transfer_own` | — | permission rule (admin OR V+W auth) |
| 7 | **Open transfer popup** (`:330-339`) | click `#btn_transfer_own` | row `BANK_CD`, `ACCT_NO` | `sessionStorage SELECTED_BANK / SELECTED_ACCOUNT`; `_v2common.app.openLayerPopup("trns_0001_007.act")` | — | — |
| 8 | Product-type "H" special (`:390-392,643-647`) | render/load | `_prdtTypes=="H"` | forces `BANK_CD="11704001"`; only VIETIN allowed | — | — |

> **Account-list loading sequence:** `onload` → `initialize`(bank list) → `load`(account list). NXiSAS is *opened* on the main page (`openNxisas`) but the popup re-opens it (the popup's own `onload` NXiSAS init is commented out, see Redmine #42095 note `trns_0001_007.ts:262-277`).

---

## 4. Transfer Popup Event Flow (Part 2 — popup-level)

File: `trns_0001_007.ts`. Popup root selector `div#transfer_own_account` (`:238`).

| # | Event / function | Trigger | Input | Side-effect | Service / process ID | Validation msg |
|---|---|---|---|---|---|---|
| 1 | `$(function(){…})` (`:14-19`) | popup DOM ready | — | `onload()` then `fnLoadBank()` | — | — |
| 2 | `onload` (`:257-311`) | init | — | Builds `multiLanguagesObj` (ML j001–j027), focuses sender remark; **NXiSAS init commented out** (#42095) | — | — |
| 3 | `initialize` (`:336-361`) | load | `trns_0001.rData` (base64) | `cardComboDataSenderAccount(.from_Bank,…snd_acct_callback)`; `cardComboDataRecipientBank(.to_Bank,…recipient_bank_callback)`; `trns_0001_001_callbackLoad`; reads exchange rate | `trns_0001_001_r001`, `trns_0001_005_r001`, `comm_0500_001_r001` | — |
| 4 | `fnLoadBank` (`:2327-2358`) | after onload | `sessionStorage SELECTED_BANK/ACCOUNT` | `loadCardComboDataSenderAccount` then `fnAutoSelectFromBank` (auto-clicks matching sender option) | `trns_0001_001_r001` | — |
| 5 | **Sender account select** `snd_acct_callback` (`:950-1105`) | click `.snd_acct_select` (`:392-412`) | selected account JSON | Resets recipient/amount/pwd/remark fields; sets `isWithinBank`; `switch(bank_cd)` & `switch(dataKey)` set `isPwd`, `isPwdRequired`, trns limits; calls `loadDataReloadAcctINFO` + `loadDataReloadTrnsDetlINFO` | — | enables `#next_step` |
| 6 | `loadDataReloadAcctINFO` (`:1112-1169`) | from #5 | bank + balance | Renders Account Password input (enabled only for Shinhan/Woori, `:1124-1129`); sets available balance + last-updated time; `validateInputMASK(CCY_CD)` | — | adds `required` class if `isPwdRequired` |
| 7 | `loadDataReloadTrnsDetlINFO` (`:1254-1537`) | from #5/#11 | bank, isWithinBank | Sets `totl_remark_length`/`length2` per bank; picks `trns_detl_module`1–4; sets `trns_detl_use_fee_yn`=Y/N | — | — |
| 8 | **Update Balance** (`:374-389`) | click `#update_balance` | sender account | `loadDataReloadBALANCE` (NXiSAS BALANCE job) then `fnTransferBalanceUpDATE` → `trns_0001_001_u001` | NXiSAS `BALANCE`; `trns_0001_001_u001` | `WBE6021` if no sender account |
| 9 | `loadDataReloadBALANCE` (`:1549-1681`) | from #8 | acct, cert | Opens progress bar; builds `_nxisas_dataObj` (loginID/PWD from `getCertInfo`); job `BALANCE`; `nonLogin=false,nonLogout=true` (Woori: both false `:1611`); runs scraping | NXiSAS `BALANCE` | `WBE1135` on error, success `00000000` |
| 10 | `fnTransferBalanceUpDATE` (`:1686-1763`) | balance success | balObj | Saves to `trns_0001_001_u001`; re-loads sender combo; updates UI | `trns_0001_001_u001` | `WBE9999` (8000F110), `WBW1103` (other) |
| 11 | **Recipient bank select** `recipient_bank_callback` (`:1192-1249`) | click `.rcpt_bank_select` (`:414-428`) | recipient bank JSON | `switch(bank_cd)`; calls `loadDataReloadTrnsDetlINFO` | — | enables `#next_step` |
| 12 | **Fee radio change** (`:483-493`) | change `input[name='shihhan-radio[200]']` | which radio | Sender-pays → enable sender remark, disable recipient remark (and vice-versa) | — | — |
| 13 | **Sender remark input** (`:451-465`) | keyup/paste `.remark_trns_detl_sender` | text | maxlength=`totl_remark_length_sender`; live byte count via `validateStringByTES` (UTF-8); only counts if Sender-pays checked | — | byte length |
| 14 | **Recipient remark input** (`:467-481`) | keyup/paste `.remark_trns_detl_recipient` | text | mirror of #13 for Recipient-pays | — | byte length |
| 15 | applicant/beneficiary remark (`:495-507`) | keyup `.remark_for_appl/.remark_for_benf` | text | live byte count (used by module2/3/4) | — | — |
| 16 | recipient acct numeric-only (`:509-511`) | keyup `.recipient_acct_no` | digits | strips non-digits | — | — |
| 17 | password eye toggle (`:513-527`) | click `.btnInputEye` | — | toggles password/text | — | — |
| 18 | **Next** `a#next_step` (`:538-913`) | click | all form fields | Per-bank validation + payload build; `fnReadNapasBankCode`; `fnTransferConfirmation` | `trns_0001_001_r003`; NXiSAS transfer job | per-bank (see §6) |
| 19 | `fnReadNapasBankCode` (`:2255-2294`) | inside Next | recipient NAPAS code | Validates code exists | `trns_0001_001_r003` | `WBE1143` if not found |
| 20 | `fnTransferConfirmation` (`:1889-2203`) | inside Next | full data | Per-bank `switch` sets `job` & `_input_bal`; runs NXiSAS transfer; builds `transferObj1`; on success calls `callback`+`fnLoadBank` | NXiSAS `TRANSFER*`; `getCertInfo`, `getModuleInfo` | — |
| 21 | success callback (`:852-857`) | scraping `TRNS_STTS==00000000` | result | `closeProgressBar`; `fnTransferConfirmationCallback` | — | — |
| 22 | `fnTransferConfirmationCallback` (`:2208-2250`) | from #21 | `trns_0002_001_c001_In` | **Saves transfer record**; on `TRNS_STTS==00000000` shows `WBI1137`, closes popup, reloads main list | `trns_0002_001_c001` | `WBI1137` success |
| 23 | failure callbacks (`:858-907`) | non-zero `TRNS_STTS` | result | Branches: `LOGIN_CAN` (cancel), `errorTrnsPwdCode` (re-enter pwd), `errorOTPCode` (OTP), `8000F110` (`WBE9999`), else `WBE1135` | — | `WBE1135` / `WBE9999` |
| 24 | **Cancel/close** `#btn_close` (`:370-372`) | click | — | `closeLayerPopup` + reload main list `trns_0001_001.fnLoadData` | — | — |
| 25 | reset after transfer | within #22 | — | `closeLayerPopup` + `fnLoadData({TO_EXCEL:false})` reloads account list | `trns_0001_001_r001` | — |

> **There is no distinct "confirmation popup".** "Next" runs the scraping transfer immediately and shows result messages inline. Any "confirmation" the refactor implements as a second popup is a **new** abstraction, not legacy behaviour.

---

## 5. Confirmation Popup Event Flow (Part 2 — confirmation)

**Finding:** No separate confirmation popup exists in the legacy Own-Account-Transfer flow.

- The data object passed from the form to the transfer step is assembled in `a#next_step` as `{REC, REC2, REC3, COMON_HEAD}` (`trns_0001_007.ts:834-839`).
- `REC` = sender account (`Dtrns_0001_001_r001_Rec`), `REC2` = recipient bank/account (`Dtrns_0001_001_r002_Rec`), `REC3` = transaction details (fee + remarks).
- This object is handed directly to `fnTransferConfirmation` (no UI confirmation step). The user's only "confirmation" is the browser/NXiSAS OTP/QNA prompt and the final success dialog `WBI1137`.
- `trns_0001_005` (sometimes mistaken for a confirmation popup) is the **recipient "My Accounts" picker**, not a confirmation (`trns_0001_005.ts`, title "My Accounts").

---

## 6. Bank-Specific Business-Rule Matrix (Part 3)

> All citations are `trns_0001_007.ts` unless noted. "dataKey switch" = `snd_acct_callback :1029-1094`; "field render" = `loadDataReloadAcctINFO :1112-1169`; "remark/fee" = `loadDataReloadTrnsDetlINFO :1264-1532`; "Next" = `a#next_step :538-775`; "job" = `fnTransferConfirmation :1930-2015`.

### A. Basic transfer support (all five supported banks)
- **Supported account types:** the sender list service `trns_0001_001_r001` restricts to `ACCT_TYPE_CD in ('03','05')` (current + one saving type), active (`ACTV_YN='Y'`), local currency (`CCY_CD='VND'`) — per backend agent read of `trns_0001_001_r001_act.jsp`. **Currency: VND only** for the sender list. USD appears only in fee/limit conversion (`fnReadExchangeRATE`, `fnConvertTrnsNOTE`).
- **Same-bank vs cross-bank:** both supported — `isWithinBank` computed by comparing sender `BANK_CD` vs recipient `BANK_CD` (`:982,1207`). Job type changes to `*_INTERNAL*` when sender bank == recipient bank.
- **Product type "H"** (Housing): only VIETIN `11704001` allowed (`:643-647`); Woori account numbers `100…` are rewritten to `DDA…` for scraping and back (`:798-808,1564-1568,2036-2044,2213-2223`).

### B. Authentication requirement matrix

| Bank | code | `isPwd` (dataKey switch) | `isPwdRequired` | Acct-pwd field state (field render) | Transfer pwd | Q&A / OTP | Decided by |
|---|---|---|---|---|---|---|---|
| **Shinhan VN** | 11704088 | `true` (`:1053`) | `true` (default, not overridden) | **Enabled + required** (in `inputPassBank` `:1125`) | none | NXiSAS engine only | inline typed pwd, or saved `data-acct` (`:703-707`) |
| **Vietcom** | 11704004 | false | **`false`** (`:1032`) | Disabled, not required | none | NXiSAS engine only | no pwd sent (`:1976-1984`) |
| **IBK** | 11704003 | `true` (`:1062`) | **`false`** (`:1063`) | Disabled, not required | none | NXiSAS engine only | **password from `fnif_0002_006` popup** (`:676-681`); else `WBE…` |
| **VIETIN** | 11704001 | false | **`false`** (`:1088`) | Disabled, not required | none | NXiSAS engine only | typed pwd or saved (`:725-729`), field disabled so effectively none |
| **Woori VN** | 11704020 | false | `true` (default) | **Enabled + required** (in `inputPassBank` `:1127`) | none | NXiSAS engine only | typed pwd or saved (`:747-751`); `acctNumPWD` sent (`:1995`) |
| KEBHana (dormant) | 11704080 | `true` (`:1043`) | `true` | Disabled inline; **acct pwd via `fnif_0002_006`, transfer pwd via `fnif_0002_007`** (`:634-654`) | **required** (`transPWD` `:1971`) | NXiSAS engine | `data-acct` + `data-trns` (`:1866-1869`) |
| BIDV (dormant) | 11704008 | false | `true` (default) | Disabled, required-class | none | NXiSAS engine | no inline pwd branch |

> **Account password is editable inline for ONLY Shinhan VN and Woori VN** (`inputPassBank = ["11704088","11704020"]`, `:1124-1129`). For Vietcom / VIETIN the field is disabled and **no password is actually transmitted**. For IBK / KEBHana the password is collected through the `fnif_0002_006/007` popups, not the inline field.

### C. Transfer-popup field behaviour by bank

**From Bank / sender combo** — same for all: `cardComboDataSenderAccount` → `trns_0001_001_r001`; options carry `jsondata`, `datakey`(BANK_CD), `dataacct`(ACCT_PWD), `datatrns`(TRNS_PWD), `databankcdno`(`${BANK_CD}_${ACCT_NO}`).

**Available Balance / Update Balance / Last-updated** — same UI for all (`#update_balance`, `.available_balance`, `#acct_lst-update_stts_*`). Behaviour difference: Woori VN runs balance with `nonLogout=false` (full logout) while others keep session alive `nonLogout=true` (`:1606-1614`).

**To Bank** — `cardComboDataRecipientBank` → `trns_0001_005_r001` (user's own other accounts). `datakey` is the **NAPAS_BANK_CD**, not BANK_CD.

**Transfer Amount** — `.trns_amount_input`, masked numeric (`data-mask="true"`); mask is local-currency mask if `CCY_CD` matches local, else USD mask (`validateInputMASK :1174-1187`). No explicit min/max in the popup; per-bank limits are **display-only notes** (`trns_limit` in `snd_acct_callback`, e.g. Vietcom `499.999.999`/txn, `3.000.000.000`/day `:1034-1038`). Available-balance is **not** hard-validated against amount in the Next handler (only field-required checks).

**Transaction Fee radios** (`#shihhan-radio-group-1` Sender pays / `-2` Recipient pays):

| Bank | Fee radios | `trns_detl_use_fee_yn` | Fee key/val sent |
|---|---|---|---|
| Shinhan VN | rendered, but `trns_detl_use_fee_yn="N"` (`:1487,1514`) | N | from radio (Y/1 or N/2) |
| Vietcom | active fee (`module`), `"Y"` (`:1492,1519`) | **Y** | from radio |
| IBK | `module3`, `"N"` (`:1477,1509`) | N | **forced label "Sender pays", key `N`, val `2`** (`:593-596`) |
| VIETIN | `module4`, `"N"` (`:1502,1529`) | N | from radio; but for `TRANSFER_INTERNAL_ML` `feePaymentType="2"` forced and `transType` deleted (`:2105-2110`); for `TRANSFER_ML` `feePaymentType` deleted (`:2112-2114`) |
| Woori VN | `module3`, `"N"` (`:1497,1524`) | N | from radio |
| KEBHana (dormant) | `module2`, `"N"` (`:1482`) | N | n/a |
| BIDV (dormant) | `module` (`:1490,1517`), `"Y"` | **Y** | from radio |

**Remarks** — two distinct UI shapes:
- `module` / `module4`: single sender remark tied to the fee radio (`.remark_trns_detl_sender` REMARK, `data-next`); recipient textarea disabled.
- `module2` / `module3`: independent applicant + beneficiary remarks (`.remark_for_appl`, `.remark_for_benf`), fee radios disabled.

**Remark byte-limits (UTF-8 bytes via `validateStringByTES`):**

| Bank | within-bank sender/recipient | inter-bank sender/recipient | Source |
|---|---|---|---|
| Vietcom | 195 / 195 | 180 / 180 | `:1269-1275` |
| Shinhan VN | 200 / 200 | 200 / 200 | `:1291-1292` |
| IBK | sender 35 / recipient 200 | 35 / 200 | `:1297-1298` |
| Woori VN | sender 100 / recipient 210 | 100 / 210 | `:1308-1309` |
| VIETIN | 210 / 210 | 210 / 210 | `:1313-1314` |
| KEBHana (dormant) | 210 / 210 (within) else 50 | 50 / 50 | `:1281-1285` |
| BIDV (dormant) | 210 / 210 (+ default = COMP_NM) | 210 / 210 | `:1302-1304` |
| default | 210 / 210 | 210 / 210 | `:1260-1261` |

> **Default remark fallback:** for Vietcom (`11704004`) and Shinhan (`11704088`), if the user leaves the remark empty, it is auto-filled with the company name `COMP_NM` (`:568-572`). The JSP placeholder confirms: "If not entered, the company name will be displayed" (`trns_0001_007_view.jsp:194`).

### D. Confirmation popup behaviour
N/A — no confirmation popup (see §5). The "confirmation" data object `{REC,REC2,REC3,COMON_HEAD}` is built at `:834-839` and consumed directly. Password is held in `REC.ACCT_NUM_PWD` / `REC.TRNS_PWD` (in-memory, never displayed). OTP/QNA happen in the NXiSAS engine (not in WeCMS DOM).

### E. Final transfer process (all banks)
- **NXiSAS transfer job** (per-bank `job`) runs first via `trns_0001.runScraping` (`:2127`).
- On scraping success → **save** via `trns_0002_001_c001` (`fnTransferConfirmationCallback :2227`).
- **Request payload (`trns_0002_001_c001_In`, `:114-131` + build `:2132-2188`):** `OUT_BANK_CODE`, `OUT_ACCT_NO`, `TRANS_TYPE`, `IN_NAPAS_BANK_CODE`, `IN_ACCT_NO`, `IN_BANK_CODE`, `CCY_CD`, `TRSC_AMT`, `FEE_DFR_SBJ`, `FEE`, `RMRK`, `RMRK2`, `OWAC_NM`, `TRNS_STTS`, `TRNS_MSG`, `REMIT_DATE`.
- **Table written:** `NAPAS_REMIT_TRNS_HSTR` (entity `NapasRemitTrnsHstr`, `TRNS_STLM_YN='N'`, `BIZ_KEY` set) — backend `trns_0002_001_c001_act.jsp`.
- **Success condition:** scraping `ErrorCode == "00000000"` **and** `result.TRNS_STTS == "00000000"` → `WBI1137`, close popup, reload list.
- **Failure conditions:** see §4 row 23 (LOGIN_CAN, errorTrnsPwdCode, errorOTPCode, 8000F110, generic WBE1135).
- **Logging:** `nxisas_logging` writes `EXT_TRAN_CALL_HIS` + `NXISAS_MODULE_TRAN_LOG_HIS`.

---

## 7. Bank-Specific Authentication Matrix (Part — auth focus)

| Bank | Acct pwd required? | Acct pwd field editable? | Transfer pwd? | Q&A? | OTP? | Security module (NXiSAS)? | Pwd source | Deciding fn |
|---|---|---|---|---|---|---|---|---|
| Shinhan VN (11704088) | Yes | **Yes** | No | Engine-prompted | Engine-prompted (optional) | Yes | inline / saved | `snd_acct_callback`, `next_step :682-707` |
| Vietcom (11704004) | No | No | No | Engine-prompted | Engine-prompted | Yes | none | `snd_acct_callback :1031-1039` |
| IBK (11704003) | No (`isPwdRequired=false`) but `acctNumPWD` sent | No (popup) | No | Engine-prompted | Engine-prompted | Yes | `fnif_0002_006` popup | `next_step :655-681` |
| VIETIN (11704001) | No | No | No | Engine-prompted | Engine-prompted | Yes | none effective | `next_step :708-729` |
| Woori VN (11704020) | Yes | **Yes** | No | Engine-prompted | Engine-prompted | Yes (full logout) | inline / saved | `next_step :730-751`, `job :1986-1999` |
| KEBHana (dormant 11704080) | Yes | No (popups) | **Yes** | Engine | Engine | Yes | `fnif_0002_006`+`fnif_0002_007` | `next_step :614-654` |
| BIDV (dormant 11704008) | required-class | No | No | Engine | Engine | Yes | none | `job :1932-1937` |

> **Q&A and OTP** are entirely NXiSAS-engine concerns. The popup defines OTP/transfer-password error-code arrays (`errorOTPCode` `:53-56`, `errorTrnsPwdCode` `:59-61`) so it can react to engine results, but it renders **no** OTP/QNA HTML. Whether a given bank actually prompts OTP or Q&A is determined by the **bank's NXiSAS module at runtime**, not by WeCMS code → **partially Unknown / not confirmable from this codebase**.

---

## 8. UI Behaviour Matrix (Part 7)

| Bank | Acct-pwd required | Acct-pwd field enabled | Q&A | OTP | Sender-pays usable | Recipient-pays usable | Default fee | Sender remark req. | Sender max (bytes) | Recipient remark req. | Recipient max (bytes) | Balance update | Last-updated shown | Special read-only | Source fn |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **Shinhan VN** | Yes | Yes | engine | engine | yes(but feeYN=N) | yes | Sender | Yes | 200 | No | 200 | Yes | Yes | recipient remark disabled until fee toggle | `:1485-1488,1287-1292` |
| **Vietcom** | No | No | engine | engine | **Yes (feeYN=Y)** | Yes | Sender | Yes | 195/180 | No | 195/180 | Yes | Yes | acct-pwd disabled; empty remark→COMP_NM | `:1490-1493,1264-1276,568-572` |
| **IBK** | No (sent) | No | engine | engine | no (forced) | no | **Sender forced (key N/val 2)** | appl field | 35 | benf field | 200 | Yes | Yes | fee radios disabled; pwd via popup | `:593-601,1294-1298,1475-1478` |
| **VIETIN** | No | No | engine | engine | radio | radio | Sender (ML deletes fee) | appl required | 210 | recipient textarea disabled | 210 | Yes | Yes | module4; fee logic ML-dependent | `:1500-1503,2104-2115` |
| **Woori VN** | Yes | Yes | engine | engine | no (feeYN=N) | no | Sender | appl field | 100 | benf field | 210 | Yes (full logout) | Yes | module3; `100→DDA` for type H | `:1495-1498,1306-1309,1611-1614` |
| KEBHana (dormant) | Yes | No | engine | engine | no | no | Sender | appl | 210/50 | benf | 210/50 | Yes | Yes | acct+transfer pwd popups | `:1480-1483,1277-1285` |
| BIDV (dormant) | req-class | No | engine | engine | **Yes (feeYN=Y)** | Yes | Sender | Yes | 210 | No | 210 | Yes | Yes | empty remark→COMP_NM | `:1490-1493,1300-1304` |

---

## 9. Validation Matrix (Part 6)

| Field | Condition | Bank-specific? | Error code (usage-derived meaning) | Function | File:line | When |
|---|---|---|---|---|---|---|
| From Bank / sender acct | required | common | `WBE6021` (generic "field required", uses field name) | `update_balance` handler / `validateRequire` | `:374-389,919-943` | on Update Balance / on Next |
| Available Balance | not empty on balance-success | common | `WBE6021` "Balance" | `fnTransferBalanceUpDATE` | `:1711-1715` | after scraping |
| Account Password | required only if `isPwdRequired` (Shinhan/Woori/KEBHana/BIDV) | **bank-specific** | `WBE1196` (acct pwd required) / `WBE6021` | `next_step` per-bank `field_nm=="ACCT_NUM_PWD"` | `:617,659,686,713,735,756` | on Next |
| To Bank (recipient) | required (non-VIETIN/Woori branches) | **bank-specific** | `WBE1197` (recipient bank required) | `next_step` `field_nm=="RCPT_BANK_NM"` | `:620,662,689,759` | on Next |
| Recipient NAPAS code | must exist in `NAPAS_BANK_INFO` | common | `WBE1143` (NAPAS code not found) | `fnReadNapasBankCode` → `trns_0001_001_r003` | `:848,2282-2287` | on Next (before transfer) |
| Transfer Amount | masked numeric; formatted for save | common | — (no explicit min/max/balance check in popup) | `formatAmtForsave` | `:547,551` | on input / on Next |
| Transaction Fee | radio required where active (`data-collname=TRNS_FEE_TYPE`) | **bank-specific** | `WBE6021` "TRNS_FEE_TYPE" | `validateRequire` | `:930-933` | on Next |
| Remark (sender) | required (module/module4 banks) | **bank-specific** | `WBE1198` (remark required) / `WBE6021` | `next_step` `field_nm=="REMARK"` | `:624,666,693,715,737,763` | on Next |
| Remark byte length | maxlength per bank (35–210) | **bank-specific** | — (truncation, live count) | `validateStringByTES` | `:451-507,1543` | on input |
| Applicant/Beneficiary remark | module2/3/4 only | **bank-specific** | — | keyup handlers | `:495-507` | on input |
| Transfer Password | KEBHana only | **bank-specific** | `WBE6021` "TRNS_PWD" | `next_step` KEBHana branch | `:641-653` | on Next |
| Q&A answer | n/a (engine) | engine | n/a | NXiSAS modal | nxisas_api.js | during scraping |
| OTP | n/a (engine) | engine | engine error codes `errorOTPCode` | NXiSAS modal | `:53-56` | during scraping |
| Module/cert info | cert seq must resolve | common | balance/transfer error if missing | `getCertInfo`,`getModuleInfo` | `:1576-1582,1917-1923` | on Update Balance / Next |

> **Error-code text:** `WBE1196/1197/1198/1135/1143/6021/WBI1137/WBW1103/WBI6003` were **not found in `WbMessageCode.java`** — they are registered in the dictionary / Jex Studio (the meanings above are **derived from code usage**, not from a confirmed dictionary read). `WBE9999` = "An unexpected error has occurred… contact the Help desk." is confirmed in `WbMessageCode.java:22`. **Mark dictionary text as Unknown / not confirmed** until `dictionary_*.properties` is read.

---

## 10. Process / Service Call Sequence by Bank (Part 4)

> Common prelude for **all** banks (popup load): `trns_0001_001_r001` (sender list) → `trns_0001_005_r001` (recipient list) → `comm_0500_001_r001` (FX rate) → `trns_0001_001_callbackLoad` (saved pwd hydrate). NXiSAS `openNxisas` is initialised on the **main page** (`trns_0001_001_v001.ts:299`).

**Shinhan VN (11704088)**
1. Update Balance → `getCertInfo`(`nxisas_acctList_r003`) → NXiSAS `BALANCE` → `trns_0001_001_u001`
2. Next → `trns_0001_001_r003` (NAPAS validate)
3. `getModuleInfo`(`nxisas_getModuleInfo`) + `getCertInfo`
4. NXiSAS job `TRANSFER` (inter-bank, with `remitBranchCode='79616016'`, Thu Thiem branch, `acceptName`) or `TRANSFER_INTERNAL_ML` (same bank); `acctNumPWD` sent (`:1950-1960`)
5. `nxisas_decrypt` → `nxisas_logging`
6. `trns_0002_001_c001` (save) → `WBI1137`

**Vietcom (11704004)**
1. Update Balance → cert → NXiSAS `BALANCE` → `trns_0001_001_u001`
2. Next → `trns_0001_001_r003`
3. `getModuleInfo` → if `CLASS=="DIGIBIZ"` use non-ML jobs
4. NXiSAS `TRANSFER_ML`/`TRANSFER_INTERNAL_ML`, or `TRANSFER`/`TRANSFER_INTERNAL` when DIGIBIZ (`:1976-1984`); no acct pwd
5. `nxisas_decrypt` → `nxisas_logging`
6. `trns_0002_001_c001` → `WBI1137`

**IBK (11704003)**
1. (acct pwd via `fnif_0002_006` if not saved) 
2. Update Balance → cert → NXiSAS `BALANCE` → `trns_0001_001_u001`
3. Next → `trns_0001_001_r003`
4. NXiSAS `TRANSFER`/`TRANSFER_INTERNAL`; `acctNumPWD`, `memo`=appl, `memo2`=benf (`:1940-1947`)
5. `nxisas_decrypt` → `nxisas_logging`
6. `trns_0002_001_c001` → `WBI1137`

**VIETIN (11704001)**
1. Update Balance → cert → NXiSAS `BALANCE` → `trns_0001_001_u001`
2. Next → `trns_0001_001_r003`
3. NXiSAS `TRANSFER_ML`/`TRANSFER_INTERNAL_ML`; same-acct → transType="2"; ML-specific payload edits (`feePaymentType`/`transType` deletes `:2104-2115`); `memo`=appl
4. `nxisas_decrypt` → `nxisas_logging`
5. `trns_0002_001_c001` → `WBI1137`

**Woori VN (11704020)**
1. Update Balance → cert → NXiSAS `BALANCE` with `nonLogin=false,nonLogout=false` (full session) → `trns_0001_001_u001`
2. Next → `trns_0001_001_r003`
3. NXiSAS `TRANSFER`/`TRANSFER_INTERNAL`; `acctNumPWD`, `memo`/`memo2`; same-acct → transType="2"; if TRANSFER+within sets blank branch + `acceptName` (`:2064-2073`)
4. `resetNxisas()` after unit-callback (`:2197-2200`)
5. `nxisas_decrypt` → `nxisas_logging`
6. `trns_0002_001_c001` → `WBI1137`

**KEBHana (dormant 11704080)** — `fnif_0002_006`(acct pwd) + `fnif_0002_007`(transfer pwd) → NXiSAS `TRANSFER_ML`/`TRANSFER_INTERNAL_ML` with `transPWD`,`acctNumPWD`,`remitPurpose="Fund transfer"` (`:1962-1974`) → save.

**BIDV (dormant 11704008)** — NXiSAS `TRANSFER_ML`/`TRANSFER_INTERNAL_ML` (`:1931-1937`) → save.

---

## 11. NXiSAS Local `.exe` Flow (Part 5)

> Source: `nxisas_api_v2.js` (`_v2nxisas`) + `trns_0001_007.ts` usage + the `nxisas_*_act.jsp` backends.

- **Initialisation** — `openNxisas` (`nxisas_api_v2.js ~:796`) delegates to the opaque binary `CooconiSASNX.init(cb)` then `CooconiSASNX.open(0, cb)`. Windows-only; macOS blocked. The actual host/port/websocket is **inside the `CooconiSASNX` binary — not in JS (Unknown / not found)**. (Per memory `reference_nxisas_local_engine_origin.md`, the engine is licensed **per page origin** via Coocon's wss handshake.)
- **Job dispatch** — `callNxisas` (full login→job→logout) vs `callNxisasWithoutOpenProcess` (reuse open session). Chosen by `trns_0001.runScrapingJob` flag (`trns_0001_007.ts:77-83`). Session reuse driven by `nonLogin`/`nonLogout` flags on `_nxisas_dataObj`.
- **Account list** — `getCertInfo(seq)` → backend `nxisas_acctList_r003` (`SEQ_NO`) returns `NXISAS_GB_SEQ_NO`, `CERT_INFO_CON` (loginID), `CERT_INFO_DETL_CON` (loginPWD), `CERT_INFO_DETL_CON2` (2nd pwd / corporate). **Backend ajax, not local engine.**
- **Module info** — `getModuleInfo(bankCd, seqno)` → backend `nxisas_getModuleInfo`, builds `API_ID = "BNK_BNK001_"+bankCd[+"_"+seqno]` (or `NTS_NTS001_00001` for GDT); returns `{MODULE, CLASS}`. `CLASS` examples: `DIGIBIZ` (Vietcom → switches job to non-ML). **Backend ajax (OSMC external API).**
- **Decrypt** — `nxisas_decrypt` backend uses `ISASSeedCBC.decrypt(enc, OpenUid, OpenAction)`. The Uid/Action come from the local engine. **Must stay backend** (decryption key server-side).
- **Logging** — `nxisas_logging` backend inserts `EXT_TRAN_CALL_HIS` + `NXISAS_MODULE_TRAN_LOG_HIS`; classifies svcId by job (BALANCE multi-job → `REAL_TIME_ONLINE_INQUIRY`, ACCT_LIST → `BANKING_CREDENTIAL`, …). **Must stay backend** (audit).
- **QNA / OTP** — handled by `nxisas_api(.js)` modal flow (`CallBackfunc == "QNA"`/`"OTP"`, 2ndCert retry loop). The transfer popup only reacts to **result codes** (`errorOTPCode :53-56`, `errorTrnsPwdCode :59-61`).
- **Error handling** — `LOGIN_CAN` = user cancelled (`closeProgressBar`, no save). `8000F110` → `WBE9999`. `80010171` = wrong 2ndCert (engine retries). `80002411` → custom message "sender account cannot be transferred" (`getScrapingMessage :225-229`). **Timeout behaviour: Unknown / not found** (inside binary). Module-missing → install modal in `openNxisas`. Unlicensed-origin `80000015` → **not found in this transfer path** (documented in memory only).
- **Which banks use NXiSAS:** **all five** supported banks (every transfer & balance call goes through the engine). There is no non-NXiSAS / pure-API transfer path in this feature.
- **What MUST remain local `.exe` (cannot be a backend API):** `CooconiSASNX.init/open/execute` (the scraping engine itself, bank login, OTP/QNA prompting, on-device cert handling). **Can be backend:** `getCertInfo`, `getModuleInfo`, `nxisas_decrypt`, `nxisas_logging` (already backend).

---

## 12. Legacy Function → Business-Rule Mapping (Part — index)

| Business rule | Function | File:line |
|---|---|---|
| Allowed banks (main list) | `fnLoadBankName` | `trns_0001_001_v001.ts:640-686` |
| Per-row Transfer button permission | `fnLoadDataRow` (ETC) | `trns_0001_001_v001.ts:529-543` |
| Open transfer popup | `event` (`#btn_transfer_own`) | `trns_0001_001_v001.ts:330-339` |
| Sender select → bank flags / pwd / limits | `snd_acct_callback` | `trns_0001_007.ts:950-1105` |
| Acct-pwd field enabled banks | `loadDataReloadAcctINFO` (`inputPassBank`) | `trns_0001_007.ts:1124-1129` |
| Remark byte-limits / fee module per bank | `loadDataReloadTrnsDetlINFO` | `trns_0001_007.ts:1254-1537` |
| Balance scraping job | `loadDataReloadBALANCE` | `trns_0001_007.ts:1549-1681` |
| Balance persist | `fnTransferBalanceUpDATE` | `trns_0001_007.ts:1686-1763` |
| Per-bank Next validation + pwd source | `a#next_step` | `trns_0001_007.ts:538-775` |
| Empty-remark→COMP_NM (Vietcom/Shinhan) | `a#next_step` | `trns_0001_007.ts:568-572` |
| IBK forced fee key/val | `a#next_step` | `trns_0001_007.ts:593-601` |
| Per-bank NXiSAS job + payload | `fnTransferConfirmation` | `trns_0001_007.ts:1889-2203` |
| transType (within/self/Vietcom) | `fnTransferConfirmation` | `trns_0001_007.ts:2052-2080` |
| VIETIN ML payload deletes | `fnTransferConfirmation` | `trns_0001_007.ts:2104-2115` |
| Woori session reset | `resetNxisas` / unit-callback | `trns_0001_007.ts:85-92,2197-2200` |
| Final save | `fnTransferConfirmationCallback` | `trns_0001_007.ts:2208-2250` |
| NAPAS code validation | `fnReadNapasBankCode` | `trns_0001_007.ts:2255-2294` |
| Saved-pwd hydration per bank | `trns_0001_001_callbackLoad` | `trns_0001_007.ts:1855-1883` |
| Product-type H Woori `100↔DDA` | multiple | `trns_0001_007.ts:798-808,1564-1568,2036-2044,2213-2223` |

---

## 13. Gaps to Check in the Refactored Implementation Later (Part 8)

> No refactor code was inspected for this audit. These are the things the refactor **must verify against** the legacy rules above.

**Likely-wrong / high-risk if generalised:**
1. **Account-password field enablement** — must be editable **only** for Shinhan VN and Woori VN; disabled elsewhere. A global "always show password" is wrong (`:1124-1129`).
2. **Password requirement** ≠ field visibility — Shinhan/Woori/KEBHana/BIDV require it; Vietcom/IBK/VIETIN do not. Don't conflate.
3. **IBK & KEBHana password popups** (`fnif_0002_006/007`) — if the refactor inlines these, behaviour diverges.
4. **Remark max-length** — must be a per-bank, per-direction (within/inter) **byte** count (UTF-8), not a single global `maxLength` char count (`:1258-1316,1543`).
5. **Fee radios** — disabled/forced for IBK/Shinhan/VIETIN/Woori; only Vietcom/BIDV keep them active. IBK forces key `N`/val `2` while labelling "Sender pays" (`:593-601`).
6. **NXiSAS job type** per bank and within/inter (`TRANSFER` vs `TRANSFER_ML` vs `*_INTERNAL*`), plus Vietcom's `DIGIBIZ` non-ML switch (`:1930-2015`).
7. **VIETIN ML payload mutations** — deleting `transType` / `feePaymentType` for `TRANSFER_INTERNAL_ML` / `TRANSFER_ML` (`:2104-2115`).
8. **Woori session handling** — balance with full logout, `resetNxisas` after transfer.
9. **Product-type H** — VIETIN-only allow-list and Woori `100↔DDA` rewriting.
10. **Empty-remark → company-name** default for Vietcom & Shinhan.

**Must be configuration-driven (not hard-coded global):** bank allow-list, password-field enable set, password-required set, remark byte-limits (by bank + direction), fee-radio enablement & forced values, NXiSAS job-type mapping, branch constants (Shinhan Thu Thiem `79616016`).

**Process IDs that must map exactly:** `trns_0001_001_r001`, `trns_0001_001_r002`, `trns_0001_001_r003`, `trns_0001_001_u001`, `trns_0001_005_r001`, `trns_0002_001_c001`, `comm_0140_001_r001`, `comm_0141_001_r001`, `comm_0500_001_r001`, plus NXiSAS backends `nxisas_acctList_r003`, `nxisas_getModuleInfo`, `nxisas_decrypt`, `nxisas_logging`.

**NXiSAS functions that must remain local `.exe`:** `CooconiSASNX.init/open/execute` (login, OTP, QNA, on-device cert, scraping). `getCertInfo`/`getModuleInfo`/`nxisas_decrypt`/`nxisas_logging` are backend and may map to Spring endpoints.

**UI fields that must be conditional by bank:** account-password (visible/enabled/required), to-bank requiredness, fee radios, remark module shape (single vs appl/benf), remark limits, transfer-password (KEBHana).

**Validation rules that must be conditional by bank:** which `field_nm` branches fire (`ACCT_NUM_PWD`/`RCPT_BANK_NM`/`REMARK`/`TRNS_FEE_TYPE`/`TRNS_PWD`) — see `:614-775`.

---

## 14. Open Questions / Unknowns

1. **Dictionary text** for `WBE1135/1196/1197/1198/1143/6021`, `WBI1137`, `WBW1103`, `WBI6003` — not in `WbMessageCode.java`; **must read `dictionary_en/ko/km/vi.properties`** to confirm exact wording. Meanings here are usage-derived.
2. **NXiSAS host/port/handshake** — opaque inside the `CooconiSASNX` binary. Per-origin licensing confirmed only via memory note, not code.
3. **Whether each bank prompts OTP and/or Q&A** — decided by the bank's NXiSAS module at runtime, not by WeCMS code. Cannot be confirmed from this repo (engine-side). Needs scraping-team confirmation.
4. **Bank `11704007`** (requires `corporateID`) — name **Unknown / not found**; only in cert logic, not a transfer-supported bank.
5. **KEBHana (11704080) and BIDV (11704008)** — full popup logic exists but they are **not** in the main allow-list. Confirm with PO whether these are intentionally disabled or pending.
6. **Sender-list account-type restriction** (`ACCT_TYPE_CD in ('03','05')`, VND only) — read from `trns_0001_001_r001_act.jsp` via sub-agent; **verify against the WSVC/IDO XML** before relying on it.
7. **Combo helper line numbers** in `wabooks.comm_osmc.js` are approximate (large file) — verify exact definitions before refactor citation.
8. **`transType` assignment quirk** at `:2056` (`data.REC.BANK_CD = '11704008'` — assignment, not comparison `==`). This looks like a **latent bug** in legacy; confirm intended behaviour before replicating.
9. **Available-balance vs amount** — the popup does **not** hard-block amount > balance in the Next handler. Confirm whether NXiSAS/back-end enforces it, or if it is intentionally unguarded.

---

*End of audit. No implementation files were created or modified.*
