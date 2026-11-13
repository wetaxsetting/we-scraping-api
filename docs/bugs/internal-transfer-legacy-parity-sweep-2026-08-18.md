# Internal Transfer Intake — Remaining Legacy-Parity Differences (Sweep)

**Date:** 2026-08-18
**Type:** PARITY FIX
**Current Work Classification:** BUG
**Status:** IN PROGRESS
**Repository:** wecms-api
**Issue:** —
**Prior record (intake validation relaxations, same feature):** [internal-transfer-intake-legacy-parity-2026-08-18.md](internal-transfer-intake-legacy-parity-2026-08-18.md)
**Paired frontend record:** `wecms-web/docs/bugs/internal-transfer-legacy-parity-sweep-2026-08-18.md`

## Problem

Backend half of the 7-item Internal Transfer legacy-parity sweep. The items with a backend
component:

- **#2 `FEE_DFR_SBJ`** — refactor persists `A`/`B`; legacy persists `1`/`2` (`FeeDfrSbjType`).
- **#3 Balance refresh** — `BalanceRefreshRequest.acctBalInqStts` is `@NotBlank`, so a verbatim
  blank engine code cannot be recorded.
- **#4 Account / currency fields** — whether the intake can accept bank-normalized values after
  execution while still validating the original request before execution.
- **Review — `requested_amt`** — whether legacy retains a requested amount separately.
- **Review — `WBE_OAT_AMOUNT_MISMATCH`** — message key now unreferenced; is it dead?

Items #1 (OTP retry), #5 (ordering), #6 (i18n) are frontend-only unless the trace says otherwise.

## Scope

`ScrapingResultIntakeRequest`, `BalanceRefreshRequest`, `OwnAccountTransferService`,
`OwnAccountTransferConstants`, their tests, and the affected docs. No schema change intended.

## Out of Scope

Everything outside Own Account Transfer and its directly shared logic.

## Expected Behavior

Legacy JEX (`trns_0002_001_c001_act.jsp`, `trns_0001_001_u001`-family, `FeeDfrSbjType`,
`ValidationHelper`, the IDO/WSVC XML) is the source of truth. Traced before any code change.

## Legacy Investigation

The full trace (including the client-side flows) is in the paired frontend record's
`## Legacy Investigation`; it is not duplicated here. Backend-relevant conclusions:

### `FEE_DFR_SBJ` — legacy persists `1` / `2`

| Step | Evidence |
|---|---|
| radios carry `value="1"` (Sender pays, default) / `value="2"` (Recipient pays) | `WECMS/web/view/jex/wabooks/trns/trns_0001_007_view.jsp:177-183` |
| `trns_fee_val` = `"1"` \| `"2"` | `WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_007.ts:596-600` |
| the **same value** goes to the engine (`feePaymentType`, `:2086`) **and** to the column (`FEE_DFR_SBJ`, `:2182`) | ibid. |
| enum declares only those two codes | `WECMS/src/wabooks/enums/FeeDfrSbjType.java:8-9` |
| server rejects anything else with `WBE0001` | `trns_0002_001_c001_act.jsp:49-51` → `ValidationHelper.java:5019-5025` |
| column length 1 | `WECMS_ETC/xml/service/WSVC/WSVC.trns_0002_001_c001.xml:41` |

No legacy reader expects `A`/`B`. Refactor readers: **none** — `grep -rn "feeDfrSbj\|fee_dfr_sbj"`
across both trees finds only the entity field (`NapasRemitTrnsHstr.java:70`) and the frontend wire
type. Nothing displays, filters, sorts, reports on, or exports it, so a value change is
contained.

### Balance refresh — no backend change needed

Legacy's `trns_0001_001_u001_act.jsp:93-99` *would* store a failure status if it were called
(it patches `ACCT_BAL_INQ_STTS`/`_MSG`/`_LST_INQ_DTM` unconditionally, then gates the balance
patch on `00000000` at `:105`), and the Korean spec comment at `:101-103` says exactly that. But
**no caller passes a failure** — `trns_0001_007.ts:1717-1733` and `trns_0001_001.ts:1312-1321`
both `return` first. **The gate is on the client in legacy, so the parity fix belongs on the
client.** `applyBalanceRefresh`'s existing failure branch mirrors the JSP and is retained;
`BalanceRefreshRequest.acctBalInqStts` keeps its `@NotBlank` because the frontend now only ever
sends `"00000000"` and loosening it would make the endpoint *less* strict than legacy's own action
for no behavioural gain.

### Account / currency echo — legacy re-validates nothing post-engine

`trns_0002_001_c001_act.jsp:27-56` (`valiateInput`) checks required-ness, `TransType`, `CcyCd`,
positive amount and `FeeDfrSbjType`. It never reads `ACCT_MSTR`. So legacy can overwrite
`OUT_ACCT_NO` / `IN_ACCT_NO` / `CCY_CD` from `Result.transfer[0]`
(`trns_0001_007.ts:2157-2158, 2163`) with no risk of rejection. This refactor *does* re-validate
ownership, which is why the two roles must live on separate fields.

### `requested_amt` review

**Legacy stores no requested amount separately.** There is exactly one amount on the wire
(`TRSC_AMT`, `WSVC.trns_0002_001_c001.xml:39`) and one column. It holds the bank's
`Result.transfer[0].remitAmt` on success (`:2159`) and the entered amount on failure (`:2171`) —
so legacy *does* trust the returned `remitAmt`, and keeps no reconciliation pair.

Conclusion: keep `requested_amt` on the wire (it is the only drift signal we have) but continue
**not** persisting it — there is no legacy column for it, and adding one would breach the
"no refactor-only columns" rule. The audit value is preserved by the `log.warn` on drift, which
carries `bizKey`, `idem`, `requested` and `actual` and correlates via `traceId`. No information
is lost.

### `WBE_OAT_AMOUNT_MISMATCH` review

**Dead key — confirmed repo-wide.** A whole-repository grep returns only: its own definition in
`messages.properties:730` and `messages_en.properties:231`, its comment line, documentation prose,
and three stale build artifacts (`build/tmp/**/stash-dir/*.class`, a turbopack cache entry). **Zero
source references.** Kept deliberately — an unreferenced message key cannot be resolved, so it is
harmless, and keeping it means a future reinstatement needs no new message. Its comment line now
carries a `RETIRED 2026-08-18, NO LONGER EMITTED` marker with the legacy citation so nobody reads
it as live behaviour.

## Root Cause

`FEE_DFR_SBJ` — the refactor persisted its UI vocabulary (`A`/`B`) into a column whose legacy
vocabulary is `1`/`2`. Account/currency — a single wire field was doing two incompatible jobs
(validated identity *and* recorded result).

## Implementation

1. **`OwnAccountTransferConstants`** — `toLegacyFeeDfrSbj(String)` plus the four named constants.
   Maps `A`→`1`, `B`→`2`; anything else passes through unchanged, so a caller sending the legacy
   code is idempotent and the unused `S` (split — no legacy counterpart) is never silently
   rewritten into a valid-looking code.
2. **`ScrapingResultIntakeRequest`** — three new optional post-transfer fields
   (`engineOutAcctNo`, `engineInAcctNo`, `engineCcyCd`), constrained by **length alone**. The
   Javadoc states the pre-transfer / post-transfer split explicitly.
3. **`OwnAccountTransferService.insertHistoryRow`** — `OUT_ACCT_NO` / `IN_ACCT_NO` / `CCY_CD` now
   take the engine echo when present, else the composed value (`firstNonBlank` helper), and
   `FEE_DFR_SBJ` goes through `toLegacyFeeDfrSbj`. The ownership and CCY validation above it is
   untouched and still runs on the composed keys.
4. **`messages.properties`** — comment-only `RETIRED` marker on the dead key.

## Files Changed

- `src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/OwnAccountTransferConstants.java`
- `src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/dto/request/ScrapingResultIntakeRequest.java`
- `src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferService.java`
- `src/main/resources/messages.properties` (comment only)
- `src/test/java/com/kosign/wecms/domain/banking/ownaccounttransfer/service/OwnAccountTransferServiceTest.java`

## API Impact

Additive and backward-compatible: three new **optional** request fields; no field removed,
renamed, or re-typed; no response change; no new error code. A payload valid before is still
valid. The `fee_dfr_sbj` wire vocabulary is deliberately **unchanged** (`A`/`B`) — the conversion
is internal, so no frontend or contract change was needed for item 2.

## UI Impact

None directly. Paired frontend changes are in the frontend record.

## Database Impact

**No schema change. No migration. No seed. No SQL artifact.** Stored **values** change on
`NAPAS_REMIT_TRNS_HSTR`:

| Column | Before | After |
|---|---|---|
| `FEE_DFR_SBJ` | `A` / `B` | `1` / `2` (legacy `FeeDfrSbjType`) |
| `OUT_ACCT_NO` / `IN_ACCT_NO` / `CCY_CD` | always the composed value | bank-echoed value when the engine reported one, else composed |

Migration safety: **improved** — both columns now carry values that can occur in legacy data.

**Pre-existing drift — detection only, read-only, DO NOT auto-run any fix.** Rows written by the
refactor before this change carry non-legacy values. Detect, then decide with the PO/DBA:

```sql
-- 1. NAPAS_REMIT_TRNS_HSTR rows whose FEE_DFR_SBJ is not the legacy 1/2 vocabulary.
SELECT biz_key, seq_no, reg_dtm, fee_dfr_sbj, trns_stts
FROM   napas_remit_trns_hstr
WHERE  fee_dfr_sbj IS NOT NULL
  AND  fee_dfr_sbj NOT IN ('1','2')
ORDER  BY reg_dtm DESC;

-- 2. Rows carrying the synthesized transfer status that legacy never writes.
SELECT biz_key, seq_no, reg_dtm, out_bank_code, out_acct_no, in_acct_no,
       trsc_amt, trns_stts, trns_msg
FROM   napas_remit_trns_hstr
WHERE  trns_stts = '99999999'
ORDER  BY reg_dtm DESC;

-- 3. ACCT_MSTR rows whose balance-inquiry status is a value legacy never persists
--    (legacy only ever writes '00000000'; '99999999' is refactor-only).
SELECT biz_key, bank_cd, acct_no, acct_bal_inq_stts, acct_bal_inq_msg,
       acct_bal_lst_inq_dtm
FROM   acct_mstr
WHERE  acct_bal_inq_stts IS NOT NULL
  AND  acct_bal_inq_stts <> '00000000'
ORDER  BY acct_bal_lst_inq_dtm DESC;

-- 4. Counts, for sizing the decision.
SELECT 'fee_dfr_sbj_non_legacy' AS finding, COUNT(*) FROM napas_remit_trns_hstr
  WHERE fee_dfr_sbj IS NOT NULL AND fee_dfr_sbj NOT IN ('1','2')
UNION ALL
SELECT 'trns_stts_99999999', COUNT(*) FROM napas_remit_trns_hstr
  WHERE trns_stts = '99999999'
UNION ALL
SELECT 'acct_bal_inq_stts_non_success', COUNT(*) FROM acct_mstr
  WHERE acct_bal_inq_stts IS NOT NULL AND acct_bal_inq_stts <> '00000000';
```

No corrective UPDATE is proposed here. `ACCT_BAL_INQ_STTS` in particular is **self-healing** — the
next successful refresh overwrites it — so a data fix may be unnecessary. The
`FEE_DFR_SBJ` and `TRNS_STTS='99999999'` rows are historical records of real events; whether to
normalize or leave them as-is is a PO/DBA call, and any script would be a separate
`docs/sql/2026/` deliverable under §14.

## Integration Impact

None — Spring is strictly post-scrape here; the engine runs in the browser.

## Verification

See `## Test Evidence`.

### Automated Tests

- Test: `OwnAccountTransferServiceTest` — 32 tests (6 new for this sweep); full suite 2748
- Result: PASS — 0 failures, 0 errors

### Regression Review

- Areas checked: every other intake rejection path; the `f6031d9` NAPAS duplicate-`BANK_CD` fix;
  `TransferHistoryQueryServiceTest` (the `99999999` filter sentinel); `applyBalanceRefresh`.
- Result: PASS — see the test-result record.

### Manual Verification

- Environment: LOCAL
- Steps: not performed — an end-to-end transfer needs a licensed NXiSAS origin + bank credentials.
- Result: NOT RUN — **SIT NOT VERIFIED**

## Result

`FEE_DFR_SBJ` now stores what legacy stores. Bank-echoed account numbers and currency are
recordable without any risk of rejecting an executed transfer, because validation and persistence
now use separate fields. `requested_amt` and `WBE_OAT_AMOUNT_MISMATCH` reviewed — no change
warranted; the latter is confirmed dead and marked as such.

Code + tests + docs complete and verified locally. **READY FOR MANUAL COMMIT** — nothing
committed, pushed, merged, or deployed; no SQL prepared or executed.

## Known Limitations / Follow-up

1. **Pre-existing `FEE_DFR_SBJ = A/B` and `TRNS_STTS = '99999999'` rows are untouched.** Detection
   SQL above; corrective action is a PO/DBA decision and a separate `docs/sql/2026/` deliverable.
2. **`S` (split) has no legacy value.** It rides the DTO's `@Pattern` but the frontend never emits
   it; `toLegacyFeeDfrSbj` passes it through rather than inventing a code. If a split-fee feature
   ever lands, `FeeDfrSbjType` needs a PO decision first.
3. **`applyBalanceRefresh`'s failure branch is now unreachable from this screen** (the frontend
   gates it, as legacy does). Retained deliberately — it mirrors
   `trns_0001_001_u001_act.jsp:93-105`, which accepts a failure status by design.

## Test Evidence

Test result:
[Internal Transfer Intake Legacy-Parity Sweep — Test Result](../testing/results/internal-transfer-legacy-parity-sweep-test-result-2026-08-18.md)

Final test result:    PASS
Regression coverage:  PASS
