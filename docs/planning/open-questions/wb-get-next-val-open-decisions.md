# Open Decisions — `wb_get_next_val` Centralization

- **System:** Refactor (wecms-api)
- **Opened:** 2026-06-09
- **Context:** [docs/audits/wb-get-next-val-centralization-2026-06-09.md](../../audits/wb-get-next-val-centralization-2026-06-09.md) — all in-scope business key generation now routes through `LegacyFunctionRepository` → `wb_get_next_val` (V155, 28 branches). The two items below were deliberately **left unchanged** pending an explicit decision.

---

## OD-1 — Contacts `trco_acct_key` is client-supplied, not generated

**Status:** Open · **Action now:** none (recorded only)

**Issue.** `ContactsService` writes `TRCO_ACCT_INFO.trco_acct_key` from the **client request** (`AccountOp.trcoAcctKey()` → `ContactsService:772`) instead of generating it through `wb_get_next_val`. JEX generates this key via `wb_get_next_val('trco_acct_info','trco_acct_key', biz_key, trco_key)` (per (biz_key, trco_key) MAX+1), and the refactor's **e-Invoice path to the same table IS funneled** (V155 branch #28, `EInvoiceTrcoAutoRegistrar.nextAcctKey`). So one writer of `TRCO_ACCT_INFO` is funneled and the other trusts the client — an inconsistency and a potential collision/uniqueness gap on a shared JEX DB.

**Why not changed now.** The contacts Add/Edit flow round-trips existing `trco_acct_key` values for updates; converting blindly could break the edit contract. Needs a **create-vs-update key-contract review**: confirm which requests carry an existing key (update) vs expect server allocation (create), then funnel only the create path via `LegacyFunctionRepository.nextTrcoAcctKey(bizKey, trcoKey)`.

**Decision needed:** Confirm the create-vs-update key contract for contact bank accounts; then either (a) funnel the create path, or (b) document client-supplied keys as an approved exception.

---

## OD-2 — Date-prefixed composite payment keys vs plain JEX `wb_get_next_val`

**Status:** Open · **Action now:** none (recorded only) · **Owner:** PO / architecture

**Issue.** Payment document keys use the refactor's **date-prefixed composite architecture** — `<YYYYMMDD><6-digit per-tenant-day seq>` allocated from dedicated `*_KEY_SEQ` counter tables — rather than the plain integer `wb_get_next_val` keys JEX produces:

- `scdul_pymn_sumr.scdul_pymn_no` — `InternalTransferKeyGenerator` + `INTERNAL_TRANSFER_KEY_SEQ`
- `ovrs_pymn_sumr.ovrs_pymn_no` — `OverseasPaymentKeyGenerator` + `OVERSEAS_PAYMENT_KEY_SEQ`
- `scdul_pymn_sumr_detl.scdul_pymn_detl_no` — per-document `%06d` line ordinal (PK includes the uniquely-allocated parent `scdul_pymn_no`)

JEX's `wb_get_next_val` branches for these columns are plain per-tenant MAX+1 integers.

**Why not changed now.** This is a deliberate, cross-module architecture (mirrored across Internal/Overseas/Vendor/Expense/Payroll). It is **collision-free** on a shared DB — the date-prefixed keys occupy a numeric range disjoint from JEX's plain-int keys, and detail numbering is uniqueness-guaranteed by the uniquely-allocated parent key. Converting would change the wire/key **format**, not just the allocation mechanism.

**Decision needed:** Approve the date-prefixed composite-key architecture as a standing exception, **or** schedule conversion of `scdul_pymn_no` / `ovrs_pymn_no` (and child detail numbering) to the plain JEX `wb_get_next_val` format for full shared-DB key parity.

---

## Guardrail (in force regardless of the above)

Per [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"DB Function / Legacy Function Policy" → "Funnel architecture rule": the only executable `wb_get_next_val` call is `LegacyFunctionRepository`; no new direct callers; remaining Java-side generators are documented exceptions only.
