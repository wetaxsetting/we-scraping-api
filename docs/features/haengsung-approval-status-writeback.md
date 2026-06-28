# HAENGSUNG Approval-Status Write-back — Backend Feature Spec (Follow-on / Phase 3)

**Slug:** `haengsung-approval-status-writeback` · **Domain:** Payment · **Status:** Deferred follow-on (Phase 3 of the HAENGSUNG ERP integration) · **Product type:** `H` only · **Last updated:** 2026-06-28

> **Docs only — no Java written.** This is a **follow-on** to [haengsung-payment-refresh.md](haengsung-payment-refresh.md) (Phase 1 = import only). This flow is **not part of Phase 1** and is **not a Phase 1 blocker**. Legacy source of truth: `WECMS DEV/WECMS/src/wabooks/helper/ErpStatusUpdateHelper.java`.

## 1. Purpose

When a HAENGSUNG payment batch (imported via Refresh) moves through the **WeCMS approval lifecycle**, this flow writes the corresponding **status back to the Haengsung Oracle ERP** so the ERP can see the batch is pending / approved / rejected / in processing. It is the second of the three HAENGSUNG↔Oracle write-back surfaces ([refresh spec §20](haengsung-payment-refresh.md#20-which-batch-imports-which-writes-status-back)).

Direction: **WeCMS → Oracle**. It does **not** import data and does **not** create WeCMS rows.

## 2. Trigger point

Invoked from the **approval action flow** — when a user (or the approval engine) sets a payment batch to pending / approve / reject / processing. In legacy this is called from the approval `_act.jsp` path via `ErpStatusUpdateHelper.updateErpStatus(util, idoCon, loginUser, scdulPymnNoOrOvrsPymnNo, wecmsStatus, pymnType)` where `pymnType ∈ {SCDUL, OVRS}`. It is **not a batch** and **not the import** — it fires synchronously on the approval state change.

## 3. WeCMS tables read (keys only)

It reads only to resolve the Oracle `ERP_FILE_*` keys for the batch; it writes nothing to WeCMS:

- Scheduled: `SCDUL_PYMN_SUMR` (legacy IDO `SCDUL_PYMN_SUMR_R017`) + `SCDUL_PYMN_SUMR_DETL` (`SCDUL_PYMN_SUMR_DETL_R005`).
- Overseas: `OVRS_PYMN_SUMR` (`OVRS_PYMN_SUMR_R011`) + `OVRS_PYMN_SUMR_DETL` (`OVRS_PYMN_SUMR_DETL_R005`).

The read returns `ERP_FILE_DATE`, `ERP_FILE_NUM` (master) and `ERP_FILE_SEQ` (detail). If those are blank (batch not from ERP), the helper returns without writing — non-HAENGSUNG / non-ERP batches are skipped.

## 4. Oracle tables written

- `TB_TRNS_MAST` (legacy IDO `TB_TRNS_MAST_U000`) — keyed by `FILE_DATE` + `FILE_NUM`.
- `TB_TRNS_DETAIL` (`TB_TRNS_DETAIL_U000`) — keyed by `FILE_DATE` + `FILE_NUM` + `FILE_SEQ`, **only on rejection**.

## 5. Status mapping (WeCMS → ERP `LAST_STATUS`)

| WeCMS status | ERP `LAST_STATUS` |
|---|---|
| `PENDING` | `10` |
| `APPROVED` | `20` |
| `REJECTED` | `32` |
| `PROCESSING` | `51` |
| (any other) | no-op (null map → return) |

## 6. Columns updated

**`TB_TRNS_MAST`** (always): `LAST_STATUS` = mapped code. **On `REJECTED` (`32`) only**, additionally: `IHB_UDT_USR='WEBCASH'`, `IHB_UDT_DT=now(VN)`, `END_GB='1'`, `END_DATETIME=now(VN)`, `BUL_CNT=TOT_TRNS_CNT`, `BUL_AMT=TOTAL_TRAN_AMT` (the rejected batch is closed as "unable").

**`TB_TRNS_DETAIL`** (only when `REJECTED`): `PROC_STS='XXXX'`, `END_GB='3'` (송금의뢰취소 / request-cancelled), `IHB_UDT_DT=now(VN)`, `TR_DATE=today`.

> Note the lifecycle boundary: this flow writes `LAST_STATUS` (10/20/32/51) and reject-time `PROC_STS='XXXX'` — columns the **import never touches**. Confirmed against the status-flow workbook ([refresh spec §15](haengsung-payment-refresh.md#15-status-flow-confirmed-by-the-status-workbook)).

## 7. Why this is NOT Phase 1 Refresh import

- The import creates a **draft** with `LAST_STATUS` left at `'00'`; approval is a **separate, later** lifecycle stage that doesn't exist at import time.
- It writes **different columns** (`LAST_STATUS`/`PROC_STS`), not the receive-flag columns the import owns.
- The import does not call it and does not depend on it (re-Refresh stays idempotent via DETAIL `end_gb='0'` + upsert dedup — [refresh spec §25.3](haengsung-payment-refresh.md#253-what-breaks-if-phase-1-ships-without-them)).

**What breaks if omitted (Phase 1):** Oracle `LAST_STATUS` stays `'00'` after a WeCMS approve/reject/processing — the ERP can't see approval progress, and a rejected batch isn't marked `PROC_STS='XXXX'`/`END_GB='3'` in Oracle. WeCMS approval itself works; only Oracle visibility is stale. Acceptable for Phase 1 R&D.

## 8. When to implement

**Phase 3 — only after PO approval**, and ideally once the refactor's HAENGSUNG payment **approval flow** is wired end-to-end (so there is an approval state change to hook). Implement as a clean Spring service triggered from the approval action, reusing the same Phase 1 Oracle datasource + write path; do not re-port `_act.jsp`/`JexData` patterns.

## 9. Cross-references
- Parent / Phase 1: [haengsung-payment-refresh.md](haengsung-payment-refresh.md) (§9A phase plan, §20–§23 write-back surfaces).
- Sibling follow-ons: [haengsung-dispatch-result-writeback.md](haengsung-dispatch-result-writeback.md).
- Legacy source: `WECMS DEV/WECMS/src/wabooks/helper/ErpStatusUpdateHelper.java`.
