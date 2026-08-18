# Internal Transfer Excel Upload — Withdrawal Account Listing Audit

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](../README.md)._

**System:** Refactor (wecms-api / wecms-web) audited against JEX legacy (source of truth)
**Date:** 2026-06-10
**Author:** Withdrawal-account listing parity audit (Claude)
**Status:** Audit complete — 2 undocumented migration gaps (P1) + 1 documented deviation + 2 minor (P2). **Audit only — no code/tests/schema changed.**
**Target screen:** Payment > Internal Transfer > Scheduled Transfer → Excel Upload popup → Step 3 "Withdrawal Bank / Account No." dropdown.

---

## 1. Summary

**Main question — does the refactor list the withdrawal accounts exactly like legacy? → No.**

Both sides start from `ACCT_MSTR`, scope by tenant, require `ACTV_YN='Y'`, restrict to account types `03–08`, auto-select the first row, and submit `BANK_CD + ACCT_NO` (mapped to a NAPAS `OUT_BANK_CD` + `WDRL_ACT_NO` + `WDRL_CCY_CD`). The label format string is identical.

But the refactor query **drops two legacy filters** that materially change *which accounts appear*:

| ID | Gap | Severity | Documented? |
|---|---|---|---|
| **G1** | Legacy restricts to `BANK_CD IN ('11704088','11704020')` (Shinhan VN + Woori VN); refactor has **no bank-code restriction** | **P1** | No |
| **G2** | Legacy requires per-user account permission (`ACCT_AUTH_INFO`, `AUTH_TYPE IN ('V','W')`, `USE_YN='Y'`); refactor applies **no per-user permission filter** | **P1** | No |
| **G3** | Legacy mandates firm-banking enrolment (`TB_FIRM_CONF`, `ORG_STAT='1'`); refactor adds an **OR NOT EXISTS fallback** that lists all active accounts when the tenant has no firm-banking rows | P2 | **Yes** — repository Javadoc |
| **G4** | Bank-name label sourced from `wb_get_bank_nm(BANK_CD,'s')` (legacy) vs `BANK_INFM.RPRS_BANK_NM` (refactor) — text may differ | P2 | No |
| **G5** | Currency filter: legacy never filters (commented out); refactor query supports an optional `ccy_cd` but the dialog never sends it — net "all currencies" on both, mechanism differs | P2 | No |

**Net effect:** the refactor can list **more** withdrawal accounts than legacy (other banks, and accounts the signed-in user has no withdrawal permission on). Legacy is the baseline; G1 and G2 are migration gaps.

## 2. Scope

Withdrawal-account dropdown population only — UI → API → service → query → response mapping → upload submit payload. Out of scope: the Excel row parsing (covered by `special-char-validation-parity-audit-2026-06-10.md`), approval flow, and transfer execution.

## 3. Legacy Flow

```
pymn_0009_002_view.jsp:110-123   "3 · Withdrawal Bank/Account No." dropdown (#WTH_BANK_NO)
  → pymn_0009_002.ts: functionReadWithdrawalBankNo() (≈529-584)
      ajax service id = comm_1001_001  (shared "Withdraw Bank/Account (FirmBanking) TextBox")
  → comm_1001_001_act.jsp
      getDynamic(loginUser, input)  (lines 23-65)  → IDO ACCT_MSTR_R011
  → IDO.ACCT_MSTR_R011.xml          SELECT … FROM ACCT_MSTR AC WHERE 1=1 ??  (no ORDER BY)
  → REC[] → TS builds <li> options, auto-selects first
  → on Save: fnSaveVendorPayment() reads #WTH_BANK_NO data-bankcd/-bankno/-ccycd
      → pymn_0009_002_c001 → PymnHelper.uploadPaymentExcel:1314-1319
          NapasBankInfo lookup → OUT_BANK_CD; WDRL_ACT_NO; WDRL_CCY_CD → SCDUL_PYMN_SUMR
```

**Filter logic — `comm_1001_001_act.jsp:23-65` (verbatim):**
- `and biz_key = ?` (`loginUser.getBizKey()`) — line 28-29
- `and bank_cd in ('11704088', '11704020')` — **line 31** (Shinhan VN + Woori VN, hardcoded)
- `and acct_type_cd in ('03','04','05','06','07','08')` — line 32
- `and ACTV_YN = 'Y'` — line 42
- `and EXISTS (ACCT_AUTH_INFO AAI … AAI.USER_ID IS NOT NULL AND AAI.USE_YN='Y' AND AAI.AUTH_TYPE IN ('V','W'))` — **lines 43-52** (per-user account permission)
- `and EXISTS (TB_FIRM_CONF TFC … TFC.OUT_BANK_CD = napas(AC.BANK_CD) AND TFC.WDRL_ACT_NO = AC.ACCT_NO AND TFC.ORG_STAT='1')` — **lines 54-61** (firm-banking enrolment, mandatory)
- Currency filter **commented out** (lines 34-39) — never applied.
- **No `ORDER BY`** in `IDO.ACCT_MSTR_R011.xml` → implicit PK order (`BIZ_KEY, BANK_CD, ACCT_NO`).

**Label (pymn_0009_002.ts):** `` `${BANK_NM} ${ACCT_NO} (${ACCT_TYPE} ${CCY_CD})` `` where `BANK_NM = wb_get_bank_nm(BANK_CD,'s')` (short name) and `ACCT_TYPE` is a JS map of `ACCT_TYPE_CD` → Current/Savings.

**Submit value:** `BANK_CD` + `ACCT_NO` + `CCY_CD` (the account's own currency from `data-ccycd`). Backend maps `BANK_CD → NAPAS_BANK_CD` and stores `SCDUL_PYMN_SUMR.OUT_BANK_CD` / `WDRL_ACT_NO` / `WDRL_CCY_CD`.

**Default:** first option auto-selected (`$first.trigger("click")`). **Sort:** none in SQL; relies on PK order.

## 4. Refactor Flow

```
InternalTransferUploadDialog.tsx
  useEffect (lines 118-134)  fetchWithdrawalAccounts()   ← NO ccy arg, fired once on mount
  → lib/api/internalTransfer.ts:372-380
      GET /api/v1/payment/internal-transfer/withdrawal-accounts[?ccy_cd=]
  → InternalTransferController.listWithdrawalAccounts (149-154)
  → InternalTransferService.listWithdrawalAccounts (1319-1339)
      acctMstrRepository.findWithdrawalAccounts(bizKey, ccyCd)
      toWithdrawalAccountRow(): bank_nm = BankInfm.RPRS_BANK_NM (fallback BANK_CD)
  → AcctMstrRepository.findWithdrawalAccounts JPQL (360-387)
  → WithdrawalAccountRow{bank_cd,bank_nm,acct_no,acct_type_cd,ccy_cd,acct_nick_nm}
  → dialog builds options, auto-selects list[0]
  → on Save (lines 260-267): ApiCreateScheduledTransferRequest{ bank_cd, acct_no, ccy_cd: batchCcyCd, … }
  → InternalTransferService.create: master.setOutBankCd(outBankCdNormalizer.normalize(req.bankCd()))
                                    master.setWdrlActNo(req.acctNo()); master.setWdrlCcyCd(req.ccyCd())   (481-487)
```

**Filter logic — `AcctMstrRepository.findWithdrawalAccounts` JPQL (360-387, verbatim):**
- `a.bizKey = :bizKey`
- `a.actvYn = 'Y'`
- `a.acctTypeCd IN ('03','04','05','06','07','08')`
- `(:ccyCd IS NULL OR a.ccyCd = :ccyCd)` — optional currency
- `EXISTS (FirmBankingSetting tfc, NapasBankInfo nbi … tfc.wdrlActNo=a.acctNo AND tfc.outBankCd=nbi.napasBankCd AND nbi.bankCd=a.bankCd AND tfc.orgStat='1')` **OR `NOT EXISTS (FirmBankingSetting tfc2 … tfc2.orgStat='1')`** — firm-banking match **or fallback-to-all**
- `ORDER BY a.bankCd, a.acctNo`
- **No `BANK_CD IN (...)` restriction.** **No `ACCT_AUTH_INFO` join.**

The fallback is documented in the repository Javadoc (lines 350-358): the `NOT EXISTS` branch exists so the un-provisioned shared R&D DB (`172.168.5.172/wecmsdevdb`) still surfaces accounts; it claims production parity because a real tenant has firm-banking enrolled.

**Label (InternalTransferUploadDialog.tsx:61-69):** `` `${a.bank_nm} ${a.acct_no} (${acctTypeLabel(a.acct_type_cd)} ${a.ccy_cd})` `` — same format string; `bank_nm` from `BANK_INFM.RPRS_BANK_NM`.

**Submit value:** `bank_cd` + `acct_no` + `ccy_cd` (= `batchCcyCd`, the batch/line currency — *not* necessarily the account's own `ccy_cd`). Backend normalizes `bank_cd → OUT_BANK_CD` and stores `WDRL_ACT_NO` / `WDRL_CCY_CD`.

**Default:** `setSelectedKey(accountKey(list[0]))` — first row auto-selected (lines 118-134). **Sort:** `ORDER BY bankCd, acctNo`. **Currency re-fetch:** the dialog calls `fetchWithdrawalAccounts()` once with no argument; there is no re-fetch on currency change, so the `ccy_cd` query param is currently never sent from this screen.

## 5. Legacy vs Refactor Comparison

| Area | Legacy Behavior | Refactor Behavior | Same/Different | Risk | Evidence |
|---|---|---|---|---|---|
| Source table | `ACCT_MSTR` | `ACCT_MSTR` (`AcctMstr`) | Same | — | `IDO.ACCT_MSTR_R011.xml` · `AcctMstrRepository:360` |
| Company / biz key | `biz_key = loginUser.bizKey` | `a.bizKey = TenantContext` | Same | — | `comm_1001_001_act.jsp:28` · `InternalTransferService:1320` |
| **Bank restriction** | `BANK_CD IN ('11704088','11704020')` | **none** | **Different** | **P1** | `comm_1001_001_act.jsp:31` · JPQL has no bank predicate (`AcctMstrRepository:360-387`) |
| Account type | `IN ('03'..'08')` | `IN ('03'..'08')` | Same | — | `:32` · `AcctMstrRepository:364` |
| Account status | `ACTV_YN='Y'` | `actvYn='Y'` | Same | — | `:42` · `:363` |
| **Account permission** | `EXISTS ACCT_AUTH_INFO … AUTH_TYPE IN ('V','W') AND USE_YN='Y'` | **none** | **Different** | **P1** | `:43-52` · JPQL has no `ACCT_AUTH_INFO` join |
| Firm-banking eligibility | `EXISTS TB_FIRM_CONF … ORG_STAT='1'` (mandatory) | `EXISTS(...) OR NOT EXISTS(any active firm conf)` (fallback-to-all) | Different (documented) | P2 | `:54-61` · `AcctMstrRepository:366-382` + Javadoc `:350-358` |
| Currency filter | none (commented out) | optional `:ccyCd`; dialog never sends it | Same net result | P2 | `:34-39` · `AcctMstrRepository:365` · dialog `:120` |
| Label format | `{BANK_NM} {ACCT_NO} ({ACCT_TYPE} {CCY_CD})` | `{bank_nm} {acct_no} ({acctTypeLabel} {ccy_cd})` | Same string | — | `pymn_0009_002.ts` · `InternalTransferUploadDialog.tsx:61-69` |
| Bank-name source | `wb_get_bank_nm(BANK_CD,'s')` | `BANK_INFM.RPRS_BANK_NM` (fallback `BANK_CD`) | Different source | P2 | `IDO.ACCT_MSTR_R011.xml` · `InternalTransferService:1328-1339` |
| Submit value | `BANK_CD`+`ACCT_NO`+`CCY_CD` → `OUT_BANK_CD`(napas)+`WDRL_ACT_NO`+`WDRL_CCY_CD` | `bank_cd`+`acct_no`+`ccy_cd` → `OUT_BANK_CD`(normalizer)+`WDRL_ACT_NO`+`WDRL_CCY_CD` | Effectively same | low | `PymnHelper:1314-1319` · `InternalTransferService:481-487` |
| Submitted currency value | account's own `CCY_CD` (`data-ccycd`) | `batchCcyCd` (selected account / first line `trnf_ccy_cd`) | Possibly different | P2 / Needs Confirmation | `pymn_0009_002.ts` · `InternalTransferUploadDialog.tsx:260-267` |
| Default selection | first option auto-clicked | `list[0]` auto-selected | Same | — | `pymn_0009_002.ts:580-581` · dialog `:124` |
| Sort / order | none (PK order `BIZ_KEY,BANK_CD,ACCT_NO`) | `ORDER BY bankCd, acctNo` | Effectively same | low | `IDO.ACCT_MSTR_R011.xml` · `AcctMstrRepository:383` |

## 6. Filtering Rule Matrix

| Filter dimension | Legacy | Refactor | Match |
|---|---|---|---|
| Company / business key | `BIZ_KEY = bizKey` | `bizKey = TenantContext` | ✅ |
| Bank | `IN ('11704088','11704020')` | (no restriction) | ❌ **G1** |
| Account status | `ACTV_YN='Y'` | `actvYn='Y'` | ✅ |
| Account type | `IN ('03'..'08')` | `IN ('03'..'08')` | ✅ |
| Currency | not filtered (commented) | optional `ccy_cd` (never sent by dialog) → not filtered | ✅ (net) |
| Internal-transfer eligibility (firm-banking) | `EXISTS TB_FIRM_CONF ORG_STAT='1'` (required) | `EXISTS(...) OR NOT EXISTS(...)` (fallback) | ⚠️ **G3** (documented) |
| User / account permission | `EXISTS ACCT_AUTH_INFO V/W, USE_YN='Y'` | (none) | ❌ **G2** |
| Display / use flags | `ACTV_YN='Y'` (no DEL/USE/DISP) | `actvYn='Y'` | ✅ |

## 7. Display Label / Submitted Value Comparison

- **Label format:** identical (`Bank Account (Type CCY)`). **Risk:** bank-name text may differ — legacy short name via `wb_get_bank_nm(...,'s')` vs refactor `RPRS_BANK_NM` (G4). Neither uses `acct_nick_nm` in the label (refactor maps it on the DTO but the dialog ignores it — parity).
- **Submitted value:** functionally equivalent — both resolve the bank code to a NAPAS `OUT_BANK_CD` and persist `WDRL_ACT_NO`. **Watch:** the submitted currency is the *account's own* `CCY_CD` in legacy vs the *batch* currency (`batchCcyCd`) in the refactor — these usually coincide for a single-currency batch but can diverge; **Needs Confirmation** whether the refactor `create` should store the account currency instead.

## 8. Default Selection / Sorting Comparison

- **Default:** both auto-select the first row — same.
- **Sorting:** legacy has no `ORDER BY` and relies on the `ACCT_MSTR` PK order (`BIZ_KEY, BANK_CD, ACCT_NO`); the refactor sorts explicitly by `bankCd, acctNo`. Within one tenant these produce the same order, so the *first* (default-selected) account is the same in practice — low risk. The refactor is deterministic where legacy is implicit; not a behavioral regression.

## 9. Migration Gaps

### G1 — Bank-code restriction missing — **P1 (undocumented migration gap)**
Legacy lists **only** Shinhan VN (`11704088`) and Woori VN (`11704020`) accounts (`comm_1001_001_act.jsp:31`). The refactor query has no bank predicate (`AcctMstrRepository:360-387`); it relies on the firm-banking `EXISTS` to implicitly scope banks. On a tenant with firm-banking config for other banks — or on the fallback path (G3) — the refactor lists accounts from banks legacy never shows. **Legacy is the baseline; the refactor should restrict the bank set to match** (or derive the restriction from firm-banking config so it can't drift). Confirm the intended bank set with the lead before implementing.

### G2 — Per-user account permission filter missing — **P1 (undocumented migration gap)**
Legacy requires the signed-in user to hold a `View`/`Withdrawal` permission on the account (`ACCT_AUTH_INFO`, `AUTH_TYPE IN ('V','W')`, `USE_YN='Y'` — `comm_1001_001_act.jsp:43-52`). The refactor `findWithdrawalAccounts` query performs **no** `ACCT_AUTH_INFO` join, so it lists every active firm-configured account regardless of the user's per-account permission. (The sibling own-account-transfer path applies the permission filter in Java — see the repo comment at `AcctMstrRepository:393-401` — but the withdrawal-accounts path does not.) This is a security-relevant divergence: a user could select a withdrawal account they are not authorized to draw from. **The refactor should reinstate the V/W permission filter to match legacy.**

### G3 — Firm-banking fallback-to-all — **P2 (documented deviation)**
Legacy mandates an active `TB_FIRM_CONF` row (`ORG_STAT='1'`). The refactor adds `OR NOT EXISTS (any active FirmBankingSetting for the tenant)`, listing all active accounts when the tenant has no firm-banking rows. This is **documented** in the repository Javadoc (`AcctMstrRepository:350-358`) as an R&D-DB accommodation that "resumes strict parity once firm-banking is provisioned." On a fully-provisioned tenant the `EXISTS` branch behaves like legacy's firm-conf requirement; the fallback branch is the only divergence. Flag for confirmation that production tenants are always provisioned before this screen — otherwise the fallback is a real listing divergence and should be gated out for non-R&D environments.

### G4 — Bank-name label source — **P2**
Legacy uses `wb_get_bank_nm(BANK_CD,'s')` (short name); refactor uses `BANK_INFM.RPRS_BANK_NM` (fallback to raw `BANK_CD`). The label text can differ for the same account. Confirm the two name sources resolve to identical display strings for Shinhan/Woori, or align the source.

### G5 — Currency filter mechanism — **P2**
Legacy never filters by currency (commented out); the refactor query supports an optional `ccy_cd` but the dialog never sends it (single mount-time `fetchWithdrawalAccounts()` with no argument, no re-fetch on currency change). Net behavior matches today (all currencies listed). Low risk, but the divergent mechanism is worth noting: if a future change starts sending `ccy_cd`, the refactor would filter where legacy does not.

## 10. Needs Confirmation

- **Submitted currency value (G5/submit):** does the refactor `create` correctly persist `WDRL_CCY_CD`? It stores `req.ccyCd()` = `batchCcyCd` (batch/line currency), whereas legacy stores the *selected account's own* `CCY_CD`. Confirm whether these can diverge for a valid upload and which value the bank file requires.
- **G3 production posture:** confirm every production/UAT tenant has active `TB_FIRM_CONF` rows before reaching this screen, so the refactor's `NOT EXISTS` fallback never fires outside R&D.
- **G4 name equivalence:** confirm `BANK_INFM.RPRS_BANK_NM` == `wb_get_bank_nm(BANK_CD,'s')` for the in-scope banks, or treat as a label divergence.
- **Runtime data:** this audit reads code + queries; it does not assert the runtime row counts returned on the shared DEV DB. A live comparison (same tenant, same user) of legacy `comm_1001_001` output vs refactor `/withdrawal-accounts` would quantify the actual extra rows G1/G2/G3 introduce.

## 11. Final Recommendation

Legacy is the source of truth. The refactor's withdrawal-account list is **not** at parity:

- **Fix G1 + G2 (P1)** — reinstate the bank-code restriction and the per-user `ACCT_AUTH_INFO` (`V`/`W`, `USE_YN='Y'`) permission filter so the refactor lists exactly the accounts legacy does. G2 is security-relevant (authorization on the withdrawal source).
- **Gate G3 (P2)** — keep the documented R&D fallback only for un-provisioned local tenants; ensure it cannot fire in UAT/PROD, or remove it once firm-banking seeding is available.
- **Confirm G4/G5 (P2)** and the submitted-currency question before relying on label/submit parity.

Answers to the required questions:

- **Same withdrawal accounts as legacy?** No — refactor can list more (other banks via G1; unauthorized accounts via G2; all accounts via the G3 fallback).
- **Same exclusions as legacy?** No — refactor excludes fewer accounts (no bank restriction, no permission filter, optional firm-banking).
- **Selected account value the same?** Effectively yes — `bank_cd`+`acct_no` → NAPAS `OUT_BANK_CD` + `WDRL_ACT_NO`; **but** the submitted `WDRL_CCY_CD` source differs (batch vs account currency — Needs Confirmation).
- **Label text the same?** Same format; bank-name source differs (G4) so text may differ.
- **Default selection the same?** Yes — first row auto-selected on both.
- **Account permission filtering the same?** No — legacy filters by `ACCT_AUTH_INFO` V/W; refactor does not (G2).
- **Currency filtering the same?** Net yes (both list all currencies); mechanism differs (G5).
- **Bank/account status filtering the same?** `ACTV_YN='Y'` is identical; the bank-code restriction is **not** (G1).

This document is documentation only — no application source, tests, or schema were changed.

## 12. Source citations

Legacy (source of truth):
- `WECMS DEV/WECMS/web/view/jex/wabooks/pymn/pymn_0009_002_view.jsp:110-123`
- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/pymn/pymn_0009_002.ts` — `functionReadWithdrawalBankNo()` (≈529-584), save `fnSaveVendorPayment()` (≈454-472), auto-select (≈580-581)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_1001_001_act.jsp:23-65` (filter logic), `:68` (IDO call)
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_R011.xml` (SQL, no ORDER BY)
- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_1001_001.xml` (input `CCY_CD`, output `REC`)
- `WECMS DEV/WECMS/src/wabooks/helper/PymnHelper.java:1314-1319` (submit → SCDUL_PYMN_SUMR)

Refactor:
- `wecms-web/components/payment/internal-transfer/InternalTransferUploadDialog.tsx:61-69` (label), `:118-134` (fetch + default), `:260-267` (submit payload)
- `wecms-web/lib/api/internalTransfer.ts:372-380` (`fetchWithdrawalAccounts`)
- `wecms-web/lib/types/api.ts:708-715` (`ApiWithdrawalAccountRow`)
- `wecms-api/.../payment/internaltransfer/controller/InternalTransferController.java:149-154`
- `wecms-api/.../payment/internaltransfer/service/InternalTransferService.java:1319-1339` (list + DTO map), `:481-487` (submit → OUT_BANK_CD/WDRL_ACT_NO/WDRL_CCY_CD)
- `wecms-api/.../banking/repository/AcctMstrRepository.java:350-387` (`findWithdrawalAccounts` JPQL + fallback Javadoc)
- `wecms-api/.../payment/internaltransfer/dto/WithdrawalAccountRow.java`
