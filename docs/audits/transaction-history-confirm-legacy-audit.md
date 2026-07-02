# Transaction History — Confirm Flow Legacy Audit & Root Cause

- **System:** Refactor (`wecms-api` + `wecms-web`)
- **Feature:** Banking → Transaction History (VND + FCY) → **Confirm** an unconfirmed bank transaction
- **Date:** 2026-07-02
- **Status:** Root cause identified · minimal backend fix applied · regression tests added
- **Legacy source of truth:** `fnsh_0002_001` (Transaction History VND), `fnsh_0006_001` (Transaction History FCY)
- **Related prior audit:** [transaction-history-vnd-parity-audit.md](transaction-history-vnd-parity-audit.md) §10 ("Confirm Workflow", Bug L1)

---

## 0. Symptom

`PUT /api/v1/banking/accounts/11704088_700005285280/transactions/1/confirm` with body `{}` returns
**500 `WBE_DB`**. Server log:

```
ERROR: value too long for type character varying(20)
insert into acct_trns_rcv_pay_infm (... cnfm_by_user_id ...) values (...)
DataIntegrityViolationException → org.postgresql.util.PSQLException
```

Tenant in the screenshot: **CÔNG TY TNHH HAENGSUNG ELECTRONICS VIỆT NAM** (`BIZ_KEY = VN_VNM0004_000014`, `PRDT_TYPE = 'H'`). Logged-in admin: `wecmshaengsung@gmail.com`.

> **Why the stack trace points at `detail()` (line 228), not `save()` (line 317):** `rcvPayRepo.save(entry)` only *stages* the INSERT in the Hibernate persistence context. The very next statement, `return detail(...)`, issues a `SELECT` (`findByBizKeyAndAcctSeqNo`), which triggers a Hibernate **auto-flush** — that flush is where the staged INSERT actually executes and PostgreSQL rejects it. The offending write is the confirm INSERT, not the detail read.

---

## 1. Legacy screen / action used for Confirm

| Path | VND | FCY |
|---|---|---|
| Main page | `fnsh_0002_001` (Transaction History VND) | `fnsh_0006_001` (Transaction History FCY) |
| Confirm button | `.confirm_btn` in `fnsh_0002_001_osmc_VN.ts:553` | same handler shape in `fnsh_0006_001` |
| Confirm action | **`fnsh_0002_001_u002_act.jsp`** | **`fnsh_0006_001_u002_act.jsp`** |
| Write IDO | `ACCT_TRNS_RCV_PAY_INFM_C001` (insert) / `_U001` (update) | same |

The clickable "Confirm" cell (`fnsh_0002_001_osmc_VN.ts:970-974`) is shown only when `val.CNFM_BY_USER_ID` is **falsy** (unconfirmed). Clicking it calls the **`_u002`** service.

## 2. What values legacy sends when the user clicks Confirm

The frontend (`fnsh_0002_001_osmc_VN.ts:585-596`) sends **only locator fields** — no confirmer identity:

```ts
let input = {
    BANK_CD: rowData.BANK_CD,
    ACCT_NO: rowData.ACCT_NO,
    TRSC_DT: rowData.TRSC_DT,
    ACCT_SEQ_NO: rowData.ACCT_SEQ_NO,
    RCV_PAY_SEQ_NO: rowData.RCV_PAY_SEQ_NO,   // present → UPDATE branch; blank → INSERT branch
};
```

The confirmer identity is stamped **server-side** in the action JSP — the client never supplies `CNFM_BY_USER_ID` / `CNFM_BY_USER_NM` / `CNFM_DTM`.

## 3. Does legacy insert directly or via DAO?

Via DAO. `fnsh_0002_001_u002_act.jsp` builds an `AcctTrnsRcvPayInfm` entity and calls `AcctTrnsRcvPayInfmDAO.getInstance().update(...)` (when `RCV_PAY_SEQ_NO` present) or `.create(...)` (when absent). Those route through the IDO `ACCT_TRNS_RCV_PAY_INFM_U001` / `_C001`. There is no raw SQL in the action.

## 4. Exact confirmer stamping in legacy — the critical lines

`fnsh_0002_001_u002_act.jsp:88-95` (INSERT branch identical at `:101-108`; FCY `fnsh_0006_001_u002_act.jsp:89-96` / `:102-109` identical):

```java
acctTrnsRcvPayInfm.setBizKey(loginUser.getBizKey());
acctTrnsRcvPayInfm.setCnfmYn(YesOrNo.YES);
acctTrnsRcvPayInfm.setCnfmByUserId(loginUser.getBizKey());   // ← BIZ_KEY, NOT the login user id
acctTrnsRcvPayInfm.setCnfmByUserNm(loginUser.getName());     // ← display name
acctTrnsRcvPayInfm.setRegDtm(DateTimeUtils.utcNow());
acctTrnsRcvPayInfm.setModDtm(DateTimeUtils.utcNow());
acctTrnsRcvPayInfm.setCnfmDtm(DateTimeUtils.utcNow());
acctTrnsRcvPayInfm.setModUser(loginUser);                    // stamps MOD_ID/REG_ID from the WbUser (the real user id)
```

**Column source-of-value map (legacy confirm):**

| Column | Source (legacy) | Example value | Length used |
|---|---|---|---|
| `BIZ_KEY` | `loginUser.getBizKey()` | `VN_VNM0004_000014` | 17 |
| `ACCT_SEQ_NO` | `input.ACCT_SEQ_NO` | `1` | 1 |
| `RCV_PAY_SEQ_NO` | input / next-val | `1` | numeric(5) |
| `BANK_CD` | `input.BANK_CD` | `11704088` | 8 |
| `ACCT_NO` | `input.ACCT_NO` | `700005285280` | 12 |
| `TRSC_DT` | `input.TRSC_DT` | `20260629` | 8 |
| `RCV_PAY_AMT` | `input.RCV_PAY_AMT` | numeric | numeric(20,6) |
| `RCV_PAY_USAG_CD` | input (optional) | null on bare confirm | — |
| `VAS_ACCT_SEQ_NO` / `VAS_ACCT_CD` / `VAS_ACCT_NM` | input (optional) | null on bare confirm | — |
| `RCV_PAY_TRCO_KEY` / `_NM` / `_ENM` | input (optional) | null on bare confirm | — |
| `CNFM_YN` | `YesOrNo.YES` | `Y` | 1 |
| **`CNFM_BY_USER_ID`** | **`loginUser.getBizKey()`** | **`VN_VNM0004_000014`** | **17** ✅ ≤ 20 |
| `CNFM_BY_USER_NM` | `loginUser.getName()` | `Admin` | 5 |
| `CNFM_DTM` | `DateTimeUtils.utcNow()` | `20260702...` | 14 |
| `REG_ID` / `MOD_ID` | `loginUser` (real user id) | `wecmshaengsung@gmail.com` | 24 (into varchar(100)) |
| `REG_DTM` / `MOD_DTM` | `utcNow()` | | 14 |

**Key legacy fact:** legacy deliberately (or by long-standing quirk) writes the **`BIZ_KEY`** into `CNFM_BY_USER_ID`. Because `BIZ_KEY` is itself constrained to ≤ 20 chars, this value *always* fits the `varchar(20)` column. The real (possibly long) login user id goes into `REG_ID`/`MOD_ID`, which are `varchar(100)`.

Live-DB confirmation (JEX DEV `acct_trns_rcv_pay_infm`):

| `cnfm_by_user_id` | len | `cnfm_by_user_nm` | `reg_id` | reg len |
|---|---|---|---|---|
| `VN_VNM0004_000014` | 17 | `Admin` | `wecmshaengsung@gmail.com` | 24 |
| `VN_VNM0004_000001` | 17 | `lamine` | `lamine@gmail.com` | 16 |
| `lamine@gmail.com` | 16 | `lamine` | `lamine@gmail.com` | 16 |

Every legacy `cnfm_by_user_id` value is ≤ 20 (biz_key = 17, or the short `lamine@gmail.com` = 16). **No legacy row stores a value that overflows varchar(20).**

## 5. DB column lengths — `ACCT_TRNS_RCV_PAY_INFM` (verified on both DBs)

Verified identical on the **legacy JEX DEV** DB (`postgres_dev_server_jexframe`) and the **refactor** DB (`postgres_dev_server_refactor`). Columns written by confirm:

| Column | Type | Max len |
|---|---|---|
| `biz_key` | varchar | **20** |
| `acct_seq_no` | varchar | 20 |
| `rcv_pay_seq_no` | numeric(5,0) | — |
| `bank_cd` | varchar | 20 |
| `acct_no` | varchar | 100 |
| `trsc_dt` | varchar | 8 |
| `rcv_pay_amt` | numeric(20,6) | — |
| `cnfm_yn` | char | 1 |
| **`cnfm_by_user_id`** | **varchar** | **20** ← overflow column |
| `cnfm_by_user_nm` | varchar | 50 |
| `cnfm_dtm` | varchar | 14 |
| `reg_id` / `mod_id` | varchar | 100 |
| `reg_dtm` / `mod_dtm` | varchar | 14 |

> Note: the 2026-04-30 db-parity audit recommended widening local `cnfm_by_user_id` to `varchar(100)` ("matches user_id width"). **That was never applied** — both DBs are `varchar(20)`, matching the legacy DDL. Per the workspace migration-safety rule, the column must **stay** `varchar(20)` (legacy DDL is the schema source of truth); the fix belongs in the Java write, not the schema.

## 6. Current refactor mapping — `TransactionService.confirm()`

`TransactionService.confirm()` ([TransactionService.java:268-319](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L268-L319)):

```java
String userId = TenantContext.currentUserId();   // = login user id = "wecmshaengsung@gmail.com" (24 chars)
...
fresh.setBizKey(r.bizKey);
fresh.setAcctSeqNo(trnsSeqNo);
...
fresh.setRegId(userId);          // → REG_ID varchar(100) — fits
...
entry.setCnfmYn("Y");
entry.setCnfmByUserId(userId);   // ← BUG: writes 24-char user id into CNFM_BY_USER_ID varchar(20)
entry.setCnfmByUserNm(userNm);
entry.setCnfmDtm(nowDtm);
entry.setModId(userId);          // → MOD_ID varchar(100) — fits
entry.setModDtm(nowDtm);
```

`resolve(acctSeqNo)` correctly splits `11704088_700005285280` into `bankCd=11704088` + `acctNo=700005285280` (both fit their columns). **The composite id is NOT stored whole anywhere** (see §8).

## 7. Exact overflowing value

| Candidate | Value | Len | Column | Cap | Overflow? |
|---|---|---|---|---|---|
| `biz_key` | `VN_VNM0004_000014` | 17 | varchar(20) | 20 | no |
| `bank_cd` | `11704088` | 8 | varchar(20) | 20 | no |
| `acct_no` | `700005285280` | 12 | varchar(100) | 100 | no |
| `reg_id` / `mod_id` | `wecmshaengsung@gmail.com` | 24 | varchar(100) | 100 | no |
| **`cnfm_by_user_id`** | **`wecmshaengsung@gmail.com`** | **24** | **varchar(20)** | **20** | **YES** |

**Root cause:** `TransactionService.confirm()` writes `TenantContext.currentUserId()` (the login email, 24 chars for the HAENGSUNG admin) into `CNFM_BY_USER_ID`, which is `varchar(20)`. Legacy writes `BIZ_KEY` (≤ 20) there, so legacy never overflows. This is a **deliberate refactor deviation** from legacy — the prior parity audit §10 recorded it as "Legacy bug L1 is FIXED … stores the real user id, not the biz_key." That "fix" is precisely what breaks for email-style user ids longer than 20 characters. It went unnoticed because the earlier local test tenant used the 16-char `lamine@gmail.com`; the 24-char HAENGSUNG admin is the first id to exceed 20.

## 8. Is the composite display id `11704088_700005285280` misused as a DB value?

**No.** `resolve()` splits on the first `_`: `bankCd = "11704088"`, `acctNo = "700005285280"`. Each is stored in its own column (`BANK_CD` varchar(20), `ACCT_NO` varchar(100)) and both fit. The 21-char composite is a URL/path convenience only and is never persisted whole. This hypothesis is **ruled out**.

## 9. VND vs FCY

Both VND (`fnsh_0002_001`) and FCY (`fnsh_0006_001`) confirm actions are byte-for-byte identical in the confirmer stamping (`setCnfmByUserId(loginUser.getBizKey())`). The refactor uses a **single** `TransactionService.confirm()` for both currencies (`resolve()` derives the currency from `ACCT_MSTR`; confirm itself is currency-independent). **No currency-specific confirm logic exists on either side** — the bug and the fix apply equally to VND and FCY.

## 10. Root cause summary

> The refactor writes the login **user id** into `CNFM_BY_USER_ID (varchar(20))`. For the HAENGSUNG tenant the login id is `wecmshaengsung@gmail.com` (24 chars), exceeding 20 → PostgreSQL `value too long for type character varying(20)` → `DataIntegrityViolationException` → `WBE_DB`. Legacy writes `BIZ_KEY` (≤ 20) into that column and therefore never overflows.

## 11. Safe fix plan (minimal, legacy-parity, migration-safe)

**Chosen fix — restore legacy parity in the Java write.** In `TransactionService.confirm()` change the single line:

```java
entry.setCnfmByUserId(userId);     // BEFORE — 24-char email overflows varchar(20)
entry.setCnfmByUserId(r.bizKey);   // AFTER  — matches legacy fnsh_0002_001_u002_act.jsp:90,103 (biz_key, ≤ 20)
```

Rationale (aligned with the workspace north-star + migration-safety rules):

- **Legacy parity restored** — the refactor now stores exactly what live JEX stores in `CNFM_BY_USER_ID`, so JEX → refactor data migration stays byte-compatible for this column (no structural drift). The prior "L1 improvement" is reverted; the confirmer *identity for migration* was never in `CNFM_BY_USER_ID` in legacy anyway.
- **No schema change** — the column stays `varchar(20)` per the legacy DDL (schema source of truth). Widening to `varchar(100)` was explicitly rejected: it increases drift from legacy and needs a PO/DBA decision, and it would not restore parity.
- **User-facing display is unaffected** — the "Confirmed by" column and the detail popup render `CNFM_BY_USER_NM` (`confirmedByNm`), which continues to carry the display name (`Admin`). The frontend uses `CNFM_BY_USER_ID` only as a truthy "is-confirmed" flag (`fnsh_0002_001_osmc_VN.ts:912,957`), and the refactor list/summary derive confirmed-state from `CNFM_YN`, not from this column — so a biz_key value there is harmless.

**Rejected alternatives:**
- *Widen `cnfm_by_user_id` to varchar(100)* — violates the legacy-schema-parity / migration-safety rules; needs PO + DBA; does not restore behavioral parity.
- *Truncate the user id to 20 chars* — silent data corruption; not legacy behavior.

## 12. Blast radius / related findings

### MR split

| MR | Path | Change | Status |
|---|---|---|---|
| **MR-1 (this MR)** | `TransactionService.confirm()` — the **Confirm button** on Transaction History VND/FCY | `entry.setCnfmByUserId(userId)` → `entry.setCnfmByUserId(r.bizKey)` | **Fixed** ([TransactionService.java:319](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L319)) + regression test `TransactionServiceConfirmTest` (VND + FCY) |
| **MR-2 (separate MR)** | `SmartOrganizerService.confirm()` — the **Match to Payment Transaction / Smart Organizer auto-confirm** path | `c.setCnfmByUserId(userId)` → `c.setCnfmByUserId(bizKey)` | **Fixed** ([SmartOrganizerService.java:353](../../src/main/java/com/kosign/wecms/domain/banking/smartorganizer/service/SmartOrganizerService.java#L353)) + regression test `SmartOrganizerServiceTest.confirm_stampsBizKeyIntoCnfmByUserId_notLongUserId` |

- **`SmartOrganizerService` had the identical latent bug** — the "Match to Payment Transaction" / Smart Organizer auto-confirm path also did `c.setCnfmByUserId(userId)` and would overflow the same way for the HAENGSUNG admin the first time a match auto-confirms a row. It is **fixed in a separate MR (MR-2)** with the same legacy-parity rule: `CNFM_BY_USER_ID = bizKey`, `CNFM_BY_USER_NM` unchanged (display name), `REG_ID`/`MOD_ID` unchanged (real user id, `varchar(100)`). No schema change, no UI change. The rest of the Match-to-Payment behavior (usage-group ↔ direction validation, tenant/account scoping, double-confirm guard, key allocation) is **unchanged** — only the confirmer stamp was brought to parity.
- **`undoConfirm()` is unaffected** — it sets `cnfmByUserId = null` (no overflow) and is not on the confirm write path.
- **Reporting-Account / Purpose / VAS / Contact mutations** create rcv/pay rows via `ensureRcvPayRow()`, which leaves `cnfmByUserId` null — no overflow, not on the confirm path, not changed.
- **Prior audit correction** — [transaction-history-vnd-parity-audit.md](transaction-history-vnd-parity-audit.md) §10's claim that "Legacy bug L1 is FIXED (stores the real user id)" is now known to be the outage cause; that section should be re-annotated to reflect that legacy parity (biz_key) is the correct and required behavior for this varchar(20) column.

## 13. Verification

- Regression test `TransactionServiceConfirmTest` — confirms **VND and FCY** both write `CNFM_BY_USER_ID = BIZ_KEY` (≤ 20) and never the long login id, using a 24-char HAENGSUNG-style user id that previously overflowed. Asserts `cnfmByUserNm` still carries the display name.
- No UI change (per task scope).
- `Match to Payment Transaction` flow: **unchanged** (SmartOrganizer left as-is; see §12).

## Open items (for PO / follow-up)

1. ~~**SmartOrganizer parity**~~ — **DONE (MR-2, 2026-07-02).** `SmartOrganizerService.confirm()` now writes `bizKey`; regression test added (see §12).
2. ~~**Re-annotate the prior parity audit §10**~~ — **DONE (2026-07-02).** [transaction-history-vnd-parity-audit.md](transaction-history-vnd-parity-audit.md) §10 now records that biz_key is the required, migration-safe behavior (not a bug).
3. **Confirm the `wecms-web` detail consumer** does not display `confirmed_by_id` as a human identity (spot-check: it uses `confirmed_by_nm`). Low risk — read-only UI verification, no change expected.
