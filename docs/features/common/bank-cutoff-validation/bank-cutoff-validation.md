# Common Bank Cut-off Validation (Shinhan CITAD-API 16:30 + VCB)

**Slug:** `bank-cutoff-validation`
**Type:** Common banking rule — shared across all payment product types, not owned by a single payment module.
**System:** Refactor (wecms-api + wecms-web)
**Legacy source of truth:** `WECMS DEV/WECMS/src/wabooks/helper/PymnHelper.java` → `validateTransferTiming` + `getCutoffConfig` (lines 3159-3317)
**Date:** 2026-06-29

---

## Purpose

Bring the refactor's bank cut-off validation to legacy parity and apply the
**Shinhan Bank confirmation (17-Jun-2026)**: the Shinhan **CITAD-via-API** cut-off
moves from **15:30 → 16:30**, while the **CITAD/EBANK/VCB File-SFTP** cut-off stays
**15:30**. Also adds the previously missing **VCB** bank (including the ≥500M VND
CITAD tier) and makes the cut-off gate actually fire (it was inert — see Root Cause).

This is a **common banking rule**, not a Payment-module feature: the same
`CutoffValidator` gates the bank-transfer dispatch ("Transfer") action of every
supported product type.

## Scope

This is a **shared / common banking rule** applied to the bank-dispatch ("Transfer")
action of **all supported product types** — it is **not owned by a single payment
module**. The shared `CutoffValidator` is invoked by:

- **Internal Transfer**
- **Vendor Payment**
- **Expense Payment**
- **Payroll**
- **Overseas Payment**

- **In scope:** the cut-off-time gate run on the bank-dispatch ("Transfer") action of
  the five product types above, all of which route through the shared `CutoffValidator`.
  Backend (`wecms-api`) is authoritative; the frontend pre-flight (`wecms-web`) is a
  coarse UX speed-up only.
- **Out of scope / unchanged:** Internal-transfer (account-to-account) business
  rules, NAPAS all-day windows, Woori windows, approval/reject flow, the dispatch
  executors' routing/write logic, DB schema, `.env` files.

## Legacy rule summary (parity baseline)

Banks identified by `OUT_BANK_CD`: **Shinhan `970424`, Woori `970457`,
VCB `970436`**. Network from the first line's `ANT_DIV_CD`
(`N`/`NAPAS`/`INTER`→NAPAS, `S`/`SWIFT`→SWIFT, else CITAD).

| Network | Shinhan 970424 | Woori 970457 | VCB 970436 |
|---|---|---|---|
| NAPAS (Mon–Sun) | 00:05–23:45 | 00:15–23:55 | 00:05–23:45 |
| CITAD (Mon–Fri) | 09:00–15:30 | 08:30–17:40 | 08:00–15:30 base; **≥500M → 16:30** |
| SWIFT (Mon–Fri) | 09:00–22:00 | 08:30–19:10 | — |

- Weekend + non-NAPAS → block (`WBW1162` legacy / `WBE2068` refactor).
- Before start → `WBW1167` / `WBE2067`.
- Past cut-off → `WBW1168` / `WBE2066`. **Shinhan is exempt (logs, allows) EXCEPT
  CITAD VND→VND, which hard-blocks** (legacy req. 2026-04-14). Woori/VCB always block.
- VCB CITAD ≥500M tier is **per-transaction** (any line ≥ 500,000,000 VND).
- Legacy has **no API-vs-File-SFTP distinction** — one CITAD window per bank.

## Current refactor issue (root cause)

1. **The gate was inert.** `CutoffValidator` resolved banks by
   `WECMS_SHINHAN_BANK_CD` / `WECMS_WOORI_BANK_CD` (`.env` = **88886000 / 11704000**),
   but the real `SCDUL_PYMN_SUMR.OUT_BANK_CD` is **970424** (verified on the
   refactor DB; `TB_FIRM_BANK_CODE` has Shinhan/Woori/VCB as 970424/970457/970436).
   The lookup never matched → every transfer silently bypassed the cut-off.
   (The dispatch *executor* used a separate, correct `routing.shinhan-bank-cd`
   default of 970424 — the two halves of the feature disagreed.)
2. **No VCB** in the refactor config (backend + frontend) — and no ≥500M tier.
3. **No API-vs-File-SFTP CITAD split** — required for the new Shinhan 16:30 rule.
4. **Shinhan blanket non-block** — the refactor logged-and-allowed *all* Shinhan
   past-cut-off cases, so the cut-off popup never fired for Shinhan (diverged from
   legacy's CITAD VND→VND hard-block).

## Final cut-off matrix (implemented)

| Category | Bank | Start | Warning | Cut-off |
|---|---|---|---|---|
| Internal & NAPAS (Mon–Sun) | Shinhan / VCB | 00:05 | 23:00 | 23:45 |
| | Woori | 00:15 | 23:00 | 23:55 |
| **CITAD via API** (Mon–Fri) | **Shinhan** | 09:00 | 15:00 | **16:30** ⬅ was 15:30 |
| CITAD / EBANK / VCB **File SFTP** (Mon–Fri) | Shinhan | 09:00 | 15:00 | 15:30 |
| | Woori | 08:30 | 17:10 | 17:40 |
| | VCB (USD / VND < 500M) | 08:00 | 15:00 | 15:30 |
| | VCB (VND ≥ 500M) | 08:00 | 15:00 | **16:30** |
| SWIFT (Mon–Fri) | Shinhan | 09:00 | 18:30 | 22:00 |
| | Woori | 08:30 | 18:30 | 19:10 |

> The "warning" column is the legacy bank-side warning time; the refactor does not
> implement a soft warning window (legacy `WBC1179`) — it blocks at cut-off only.
> "API vs File SFTP" is decided by the dispatch transport (see below).

## Backend flow

1. Each module's `dispatch(...)` loads master + lines, derives the network, and
   builds a `CutoffCheck(outBankCd, network, apiTransport, vndToVnd, hasTxnAtOrAbove500M)`.
   - `apiTransport` — Internal: `InternalTransferDispatchExecutor.usesApiPath(...)`
     (single source of truth for the routing rule). Vendor/Expense:
     `TRNF_CCY_CD == "VND"` (shared executor rule). Payroll: always `false`
     (file route). Overseas: SWIFT → uses the 2-arg overload (transport N/A).
   - `vndToVnd` = transfer-ccy AND withdrawal-ccy both VND.
   - `hasTxnAtOrAbove500M` = any line `TRAN_AMT ≥ 500,000,000`.
2. `CutoffValidator.validate(CutoffCheck)`:
   - **Bank identity** resolved from `TB_FIRM_BANK_CODE.ENG_ABRVAT_NM`
     (`ShinhanBank` / `WooriBank` / `Vietcombank`, active rows) — **not** a
     hard-coded `OUT_BANK_CD`. Unknown bank → pass (legacy parity).
   - Weekend + non-NAPAS → `WBE2068`.
   - Window selection: NAPAS→napas, SWIFT→swift, CITAD→ `citadApi` when
     `apiTransport` and configured (Shinhan 16:30), else `citadLarge` for VCB
     when `hasTxnAtOrAbove500M` (16:30), else `citad` (File SFTP 15:30).
   - Before start → `WBE2067(network, start)`.
   - Past cut-off → `WBE2066(cutoff, network)`; Shinhan logged-and-allowed unless
     `network==CITAD && vndToVnd` (hard-block).
3. Windows live in **base `application.yml`** (`wecms.internal-transfer.cutoff`),
   inherited by every profile; `local`/`sit` no longer override them, and the
   obsolete `WECMS_*_BANK_CD` keys are no longer consumed.

## Frontend popup / message flow

- Authoritative popup is the **backend** `WBE2066` error rendered by the standard
  error dialog — its `{0}` argument carries the resolved cut-off time, so a blocked
  Shinhan CITAD-API dispatch shows **16:30** dynamically, and a blocked File-SFTP /
  Woori / VCB dispatch shows its own time (15:30 / 17:40 / …).
- `wecms-web/lib/payment/validateBankingTime.ts` is a coarse pre-flight only. Its
  `BANKING_SCHEDULES` was re-keyed to the real firm codes (970424/970457/970436)
  and gained VCB; it does **not** model the API-vs-File transport split (keys CITAD
  off the 15:30 File-SFTP window) — the 16:30 API window + VCB ≥500M tier are
  backend-enforced. It never blocks a transfer the backend would allow.

## Files changed

**Backend (`wecms-api`)**
- `domain/payment/internaltransfer/service/CutoffProperties.java` — typed per-bank
  `BankWindows` (napas/citad/citadApi/swift/citadLarge); removed bank-cd fields.
- `domain/payment/internaltransfer/service/CutoffCheck.java` — **new** context record.
- `domain/payment/internaltransfer/service/CutoffValidator.java` — eng_abrvat_nm
  resolution, VCB, API/File split, ≥500M tier, Shinhan hard-block; 2-arg back-compat overload.
- `domain/payment/internaltransfer/service/InternalTransferDispatchExecutor.java` —
  extracted `usesApiPath` / `computeRoutingFlags` (behavior-preserving).
- `domain/payment/internaltransfer/service/InternalTransferDispatchService.java`,
  `vendorpayment/service/VendorPaymentService.java`,
  `expensepayment/service/ExpensePaymentService.java`,
  `payrollpayment/service/PayrollPaymentService.java` — build + pass `CutoffCheck`.
  `service/OverseasPaymentService.java` — unchanged (SWIFT 2-arg overload).
- `resources/application.yml` — full Shinhan/Woori/VCB window table; removed bank-cd lines.
- `resources/application-local.yml`, `application-sit.yml` — removed duplicate cutoff
  block (inherit base); removed bank-cd lines.
- `test/.../CutoffValidatorTest.java` — new fixture shape + cases (API 16:30, VCB tier,
  Shinhan hard-block, eng_abrvat_nm bank resolution).
- `test/.../ExpensePaymentDispatchApprovalGateTest.java` — verify `CutoffCheck` overload.

**Frontend (`wecms-web`)**
- `lib/payment/validateBankingTime.ts` — real bank codes + VCB + clarifying comments;
  **full legacy confirm/warn dialog parity (2026-06-29 — see section below).**
- `locales/{en,ko,vi}.json` — 3 confirmation message keys (2026-06-29).

**Not changed:** `.env*` files, DB schema, message text (`WBE2066`/`WBE2067`/`WBE2068`),
dispatch executor write logic, Overseas/Woori behavior, approval/reject flow.

## Verification result

- `./gradlew compileJava compileTestJava` — **clean**.
- `./gradlew test --tests *CutoffValidatorTest *ExpensePaymentDispatchApprovalGateTest`
  and the internal/vendor/expense/payroll/overseas dispatch suites — **BUILD SUCCESSFUL**.
- `npx tsc --noEmit` (wecms-web) — **exit 0**.
- Grep confirms: no stale `88886000/11704000` in cut-off config/logic (only doc
  comments + unrelated test fixtures remain); Shinhan `citad-api = 16:30`;
  Shinhan File-SFTP `citad = 15:30`; Woori `17:40` unchanged; VCB `citad-large = 16:30`.

### Open items / limitations

- The FE pre-flight does not reproduce the API-vs-File transport split or the VCB
  ≥500M tier — backend is authoritative (documented in the file).
- UAT/PROD now inherit the cut-off table from base `application.yml` (previously had
  no cutoff block → no enforcement). This is intended; refactor remains local/SIT R&D.

## Legacy popup/dialog parity — frontend (2026-06-29)

The cut-off **time matrix** (above) is unchanged. This section adds **dialog-flow
parity** with the legacy frontend gate. (Note: the FE `validateBankingTime.ts` had
reverted to its pre-matrix-fix state on disk; this change restores the correct bank
codes/VCB **and** adds the dialog tree. The backend was untouched and intact.)

### How legacy FE + BE cooperate

Legacy splits the work in two layers — the **frontend** owns the confirm-vs-block UX
decision; the **backend** is a binary hard-block-or-allow safety net that re-validates
on the dispatch API call.

- **Legacy FE** — `_v2common.app.validateBankingTime()` (`add_osmc.js:8394-8572`):
  decides whether to show a **Confirmation** (Continue/Cancel → proceeds on Continue)
  or a **Warning** (OK → hard block), then on Continue calls `pymn_0009_001_c001`.
- **Legacy BE** — `PymnHelper.validateTransferTiming()` re-runs on `c001_act.jsp:94`
  before commit: throws (`WBW1162/1167/1168`) or allows. Shinhan exempt except CITAD
  VND→VND. **No backend confirmation response** — confirm is FE-only.
- **No auto-transfer on final approval** in legacy *or* refactor — transfer is always a
  separate manual action; the cut-off gate only runs on that dispatch. (Refactor
  `ApprovalBoxService.approve` only publishes an Oracle-sync event on final approval.)

### FE dialog decision tree (now implemented, mirrors legacy)

| Case | Dialog | Outcome |
|---|---|---|
| Weekend, non-NAPAS | **Warning (OK)** — `WBW1162` | block |
| Before start time | **Warning (OK)** — `WBW1166` | block |
| Past cut-off — Woori / VCB / other | **Warning (OK)** — `WBW1165` | block |
| Past cut-off — Shinhan CITAD **VND→VND** | **Warning (OK)** — `WBW1165` | block |
| Past cut-off — Shinhan (all other) | **Confirmation (Continue/Cancel)** — `WBC1182` | Continue → dispatch |
| Warning zone (warning ≤ now < cut-off), all banks | **Confirmation** — `WBC1179` | Continue → dispatch |
| VCB CITAD **MIXED** tier, 15:30–16:30 | **Confirmation** (file split) — `WBC1187` | Continue → dispatch |
| VCB CITAD **MIXED** tier, ≥ 16:30 | **Warning (OK)** — `WBW1165` | block |
| Inside window, before warning zone | — | proceed |

On `Continue`, the page then shows its existing generic
`payment.{module}.transfer_confirm_description` confirmation and dispatches — so a
past-cut-off Shinhan send shows the cut-off confirm **then** the generic send confirm,
both preserved.

### Code mapping (legacy → refactor)

| Concern | Legacy FE | Refactor FE i18n key | Dialog type |
|---|---|---|---|
| Shinhan past-cutoff confirm | `WBC1182` | `payment.transfer.cutoff.confirm_shinhan_past` | confirmation |
| Approaching-cutoff confirm | `WBC1179` | `payment.transfer.cutoff.confirm_warning_zone` | confirmation |
| VCB mixed-tier file-split confirm | `WBC1187` | `payment.transfer.cutoff.confirm_vcb_split` | confirmation |
| Weekend block | `WBW1162` | `payment.transfer.cutoff.weekend` | warning |
| Before-start block | `WBW1166` | `payment.transfer.cutoff.before_start` | warning |
| Past-cutoff block | `WBW1165` | `payment.transfer.cutoff.past_end` | warning |
| **Backend safety-net** | `WBW1162/1167/1168` | **`WBE2068` / `WBE2067` / `WBE2066`** | hard-block (HTTP, no confirm) |

KO/VI for the 3 confirmation keys are copied verbatim from the legacy message XML
(`CD_WBC1179.xml` / `CD_WBC1182.xml` / `CD_WBC1187.xml`); `confirm_vcb_split` ships
the EN text in all three locales (legacy XML carries EN for KO/VI too).

### FE data-availability limits (backend is the safety net)

The refactor list rows carry only `txnCurrency` + `fromBank`. They do **not** carry the
withdrawal currency, the ANT_DIV_CD network, or per-transaction amounts, so without a
list-API change (out of scope — no new backend API) the FE:
- infers network from currency (VND→NAPAS, FCY→SWIFT) — never CITAD;
- cannot distinguish Shinhan CITAD VND→VND (it would show the soft confirm; the
  **backend hard-blocks** it on dispatch);
- cannot detect the VCB MIXED tier (uses the base 15:30 VCB window).

The helper implements the full tree and consumes `transferType` / `wdrlCcyCd` /
`amountTier` when a caller supplies them; those branches activate once the list payload
is enriched. The Shinhan past-cutoff confirm (`WBC1182`) and the warning-zone confirm
(`WBC1179`) fire today for the common NAPAS case.

### Server time

The refactor has **no server/VN-time endpoint**, so VN-local time is read from the
browser clock (legacy fetches it from `comm_1006_001`). **Open item** — no new backend
API created (not approved). The authoritative backend gate uses the server clock, so a
skewed browser only affects this client UX hint.

### Verification (FE dialog parity)

- `npx tsc --noEmit` — **exit 0**.
- `npx eslint lib/payment/validateBankingTime.ts` — **clean**.
- `en/ko/vi.json` parse OK.
- Backend: **unchanged** (no compile/test re-run needed for this FE-only change).
- Behavior (by logic): Shinhan past-cutoff (non-VND→VND) → Confirmation, Continue
  proceeds; Shinhan CITAD VND→VND → Warning block (when context supplied; else
  backend-blocked); Woori/VCB past-cutoff → Warning block; warning-zone → Confirmation;
  VCB mixed-tier → Confirmation; generic transfer confirm still chains after; backend
  dispatch untouched.

## Configuration cleanup — obsolete env vars removed (2026-06-29)

> Obsolete environment variables `WECMS_SHINHAN_BANK_CD` and `WECMS_WOORI_BANK_CD`
> were removed because bank identification is now resolved dynamically from
> `TB_FIRM_BANK_CODE.ENG_ABRVAT_NM`. Runtime behavior is unchanged.

Read-only audit before removal confirmed the variables were **completely unused at
runtime**: no `${WECMS_*_BANK_CD}` interpolation in any `application*.yml`, no `@Value`
binding, and `CutoffProperties` carries no bank-cd field (it was dropped when bank
resolution moved to `eng_abrvat_nm`). The separate **routing** key
`wecms.internal-transfer.routing.shinhan-bank-cd` / `.woori-bank-cd` (used by the
dispatch executors / `BulkTransferValidator`) is **unrelated** — it uses inline
defaults `970424` / `970457` and was **not** wired to these env vars, so it is untouched.

Removed the two lines (and their `# ── Banking Cutoff ──` header block) from all seven
env files: `.env.local`, `.env.example`, `.env.local.example`, `.env.sit`,
`.env.sit.example`, `.env.prod.example`, `.env.uat.example`. No other env vars changed.
`application*.yml` had no active cutoff bank-cd entries to remove (only explanatory
comments remain).

Residual **non-runtime** references intentionally left for a separate doc pass (not
config): comment lines in `application-sit.yml` (the "required env vars" header), the
`haengsung-sit-deployment-checklist.md` env list, and the historical
`docs/references/business-rules/internal-transfer.md` snippet.

## Backend rule clarification — Shinhan 16:30 is currency-based (2026-06-29)

The updated legacy backend (`PymnHelper.validateTransferTiming`, PymnHelper.java:3257-3263)
sets the Shinhan CITAD cut-off to **16:30 for VND→VND** (`CCY_CD==VND && WDRL_CCY_CD==VND`)
and **15:30 otherwise** — a **currency-based** rule, **independent of API vs File SFTP transport**.

The refactor's `CutoffValidator.resolveWindow` was originally keyed on `apiTransport`
(transport-based, per the 17-Jun IMP image). That matched legacy for Internal / Vendor /
Expense (their `apiTransport` is true for VND) but **diverged for Payroll**, which is
hard-coded to the File route (`apiTransport=false`): a Payroll Shinhan CITAD VND→VND
resolved to the 15:30 window and blocked an hour early.

**Fix (Option A, legacy-faithful):** `resolveWindow` now selects the 16:30 (`citadApi`)
window for **Shinhan CITAD when `ctx.vndToVnd()`**, regardless of `apiTransport`. So:

- **Shinhan CITAD VND→VND → 16:30 for every module, including Payroll (File route).**
- Shinhan CITAD non-VND→VND → 15:30 base (and Shinhan is exempt past cut-off anyway → allowed).
- This **supersedes the earlier transport-only interpretation for backend validation.**

`apiTransport` is retained on `CutoffCheck` (minimal change; no FE/`.env`/config change) but
no longer selects the window. VCB (`citadLarge` on ≥500M), Woori, NAPAS, and SWIFT are unchanged.
**Frontend remains UX-only; the backend `CutoffValidator` is authoritative** (re-validates on
every dispatch before the executor; `BusinessException` → `@Transactional` rollback).

## Notes added on 17-Jun-2026

- Based on **Shinhan Bank confirmation**.
- **CITAD via API** cut-off time: **16:30**.
- **CITAD / EBANK / VCB via File SFTP** cut-off time: **15:30**.
