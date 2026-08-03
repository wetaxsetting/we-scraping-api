# Bulk File Transaction Boundary — Legacy Parity Follow-Up

**Date:** 2026-07-29 · **System:** Refactor (`wecms-api`)
**Status:** `DECISION REQUIRED (PO)` — legacy order is proven; the refactor's state model cannot yet represent the outcome the change would create.

---

## 1. The residual

The H-4 fix closed the duplicate-egress hole. It did **not** change the surviving divergence:
`BulkReceptionService.buildAndDispatchFile` holds **one** `REQUIRES_NEW` transaction across the MST
insert, the SFTP upload and the EFS 00301 call.

## 2. Legacy order — proven

`WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java`:

| Legacy line | Operation |
|---|---|
| `:541` | `idoCon.beginTransaction()` |
| `:542-558` | `TB_FIRM_FILE_MST_C001` — insert, `FIRM_FILE_NM` `:551`, `PROC_STEP='0'` `:554`, `PROC_STAT='6'` `:555` |
| `:559` | `createFirmData(...)` — the insert **executes** here |
| `~:1098` | `TB_FIRM_FILE_DATA_C001` — detail inserts |
| `:690-694` | file-I/O failure → `UpdatePayFileMst(FAIL)` + `rollback()` + `endTransaction()` |
| **`:696-697`** | **`idoCon.commit(); idoCon.endTransaction();`** |
| **`:700`** | **`sftpFileUpload(...)`** — egress, **outside** any transaction |
| `:706-721` | `TB_FIRM_BAT_API_TRAN_C001` — 00301 audit insert (own transaction) |
| **`:724`** | **`apiCall(...)` → EFS 00301** (`ConnectionUtil.connectUrlByPost` `:1221`) — egress |
| `:726-728` | non-`0000` → `UpdatePayFileMst(FAIL)` — **separate** write |
| `:731-741` | `TB_FIRM_BAT_API_TRAN_U001` — audit result update |
| `:742-745` | catch → `UpdateFirmFileMst(...)` + rethrow — **separate** write |

**Legacy shape:** `Transaction A (insert + commit)` → `no transaction (SFTP, 00301)` →
`separate writes for outcome`. Exactly the target shape in the brief.

## 3. Refactor shape today

```
runInNewTransaction(REQUIRES_NEW):
    firmFileMstRepository.save(mst)      ← INSERT deferred to commit
    firmFileDataRepository.save(fd) × N  ← deferred
    build fixed-length file
    sftpClient.upload(...)               ← egress, INSIDE the transaction
    00301 audit (REQUIRES_NEW writer)
    issueReception(...)                  ← egress, INSIDE the transaction
    failBoth(...) on failure             ← same transaction, so it rolls back with it
commit
```

**Classification:** `legacy parity defect` — the transaction boundary was moved.

## 4. Why it is no longer a *duplicate* risk

The H-4 claim commits before the loop is entered, so only one execution reaches
`buildAndDispatchFile` for a given logical file. The locks the transaction holds during egress are on
**freshly-inserted** `TB_FIRM_FILE_MST`/`DATA` rows that no other transaction contends for; it holds no
lock on the pay-file row (that was released when the claim committed). So the "long external call under
a row lock" concern is materially reduced — this is a *durability and observability* divergence now, not
a concurrency one.

## 5. Why this is not being changed in the same pass

The brief's own condition applies: *"Do not bundle this change if legacy evidence is inconclusive or
the current state model cannot represent uncertainty safely."* The evidence is conclusive; **the state
model is not adequate.**

Splitting the transaction creates a state the schema cannot express:

| Crash point after the split | Truth | Representable today? |
|---|---|---|
| after SFTP, before 00301 | file is on the bank's server, not instructed | **No** — only `'6'` (in-flight), indistinguishable from "never uploaded" |
| after 00301 `RC=0000`, before the outcome write | bank accepted it; we have no record | **No** — `'6'` again |
| after 00301 non-`0000`, before the outcome write | rejected; we have no record | **No** — `'6'` again |

`TB_PAY_FILE_MST.PROC_STAT` has exactly four legacy values — `'0'` succ, `'3'` fail, `'6'` req, `'9'`
wait. There is **no `UNCERTAIN`**, and adding one is forbidden: the refactor may not alter legacy column
semantics or add states without PO approval (root `CLAUDE.md` §1 — *"Do not rename, remove, redesign,
normalize, re-type…"*), and a refactor-only side table is likewise disallowed
(`project_notification_module_removed`, `project_oat_idempotency_dropped`).

Today the single transaction makes those three cases collapse to a clean rollback — the MST insert
disappears and the pay-file stays claimed at `'6'`, which the stuck monitor reports. Splitting without
an uncertainty state would instead leave a **committed** `'6'` MST row whose egress status is unknowable,
and any future re-drive would risk re-sending a file the bank already accepted. That is a worse failure
mode than the divergence it fixes.

Legacy does not solve this either — it just leaves the same ambiguity at `:742-745` and relies on
operators. The difference is that legacy's BATCH2 re-poll gave it a retry path, and the approved 4→1
architecture deliberately removed that (K9: monitoring + manual re-trigger).

## 6. What would need to be true to proceed

1. **PO decision** on representing uncertainty. Options: (a) accept a committed `'6'` + monitor +
   manual-only resolution, documented as the operational contract; (b) approve an additional
   `PROC_STAT` value (schema-semantics change, DBA + migration-safety review); (c) approve a
   refactor-owned state column.
2. `failBoth` moved onto a proxied `REQUIRES_NEW` service — `BulkDispatchClaimTxService.failStrandedPayFile`
   is already the pattern and could absorb it.
3. Detection for "uploaded but not instructed" — currently impossible to distinguish (§5).
4. A recovery rule that **never** blindly re-sends an uncertain file.

## 7. Required tests (for whoever implements it)

```text
fileState_commitsBeforeSftp
sftpFailure_persistsFailureInSeparateTransaction
efs301Failure_persistsFailureOrUncertainState
outcomePersistenceFailure_doesNotReleaseClaim
networkCall_occursWithoutLongDbTransaction
recovery_doesNotBlindlyResendUncertainFile
```

All six need a real transaction manager, so they belong in the integration source set —
see [../testing/payment-dispatch-integration-tests.md](../testing/payment-dispatch-integration-tests.md).

## 8. Interim position

`P2`, accepted, mitigated by the H-4 claim, and gated behind an OFF dispatch flag. It is **not** a
release blocker on its own; it is a correctness-of-durability item that needs the state-model decision
first.

**[CONFIRM: PO]** — §6 item 1.
