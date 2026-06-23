# Corporate Card — Refactor Gap Analysis (Card Transactions)

Gaps between the refactor Card Transactions sub-module and legacy JEX, using the shared payment gap vocabulary. Source: [payment-gap-analysis.md](../../../audits/payment-gap-analysis.md) §"Card Transactions" + §"Cross-cutting".

## Gap vocabulary

- **MATCH** — refactor reproduces legacy behaviour.
- **PARTIAL** — partially implemented, intentional or pending.
- **APPROVED DEVIATION** — intentional divergence, documented + accepted.
- **MISSING** — not implemented yet.

## Card Transactions gaps

| ID | Item | Status | Disposition | Owner |
|---|---|---|---|---|
| G-CARD-1 | `approvers[]` empty on ledger rows (chain only in Approval Details popup) | **PARTIAL** | intentional (documented) | — |
| G-CARD-2 | Resolved display names not on wire; FE resolves from chart-tree + purpose-options | **APPROVED DEVIATION** | intentional (documented) | — |
| G-CARD-3 | `MEMO_INFM` not in PROD active table list yet supports full memo CRUD (Java MAX+1 key-gen exception) | **APPROVED DEVIATION** | **PO-decision-required** (confirm baseline scope) | PO (Mr. Jung) |
| G-CARD-4 | OQ-3 `PRCH_STTS` value set still only `'1'` + null | — | **PO-decision-required** (carried) | PO |
| G-CARD-5 | No frontend tests (backend has 44 test methods across 5 files) | **MISSING** | test-missing | dev |

## Detail

### G-CARD-1 — `approvers[]` always empty (PARTIAL, intentional)
`CardTransactionsService.toRow` ships `List.of()` for every ledger row. The approver chain is fetched on demand via `GET /by-approval/{apprSeqNo}` and rendered in `<ApprovalDetailsDialog kind="card">`. Not a defect.

### G-CARD-2 — display names client-side (APPROVED DEVIATION)
No `card_trns_usage_nm` / `chart_acct_nm`/`_en`/`_level_nm` on the wire. Frontend resolves from `GET /settings/chart-of-accounts/tree` + `GET /purpose-options` keyed by raw codes. `card_trns_usage_nm` specifically is deferred as a PO decision (needs the legacy `wb_get_usag_nm` resolver / USAG_INFM join) — documented at `CardTransactionRow.java:61-67`.

### G-CARD-3 — `MEMO_INFM` baseline scope (PO-decision-required)
Memo CRUD is shipped, but `MEMO_INFM` is **not** in the PROD active table list. Key-gen uses Java MAX+1 (a documented `wb_get_next_val` funnel exception). Confirm with PO whether the memo write-path is in baseline scope before SIT.

### G-CARD-4 — `PRCH_STTS` value set (PO-decision-required, carried)
DEV data carries only `'1'` + null. Full legal value set + EN/KO/VI labels needed before `payment.card_transactions.status.*` locale entries are complete. Default: ship observed codes; frontend renders the raw code on unknown values.

### G-CARD-5 — no frontend tests (MISSING)
Backend ships 5 test files / 44 methods (`CardTransactionsControllerTest`, `CardTransactionsServiceTest`, `CardTransactionScrapeServiceTest`, `CardTransactionSyncServiceTest`, `CardTrnsHstrSortFieldTest`). No `.test.*` files for the FE — the largest single coverage gap (consistent across all payment modules).

## Cross-cutting (relevant to Card)

- **CC-1 (Notification)** — `ApprovalNotificationService` write-side is **live** (BLTB tables), consumed by Card's Request Approval fan-out. The "NOTIFICATION module removed" memory refers only to a refactor-only table never built; the BLTB write-side is **not** dead legacy. Read-side controller is a stub.
- **CC-3 (Validation)** — `BlockedCharsRule` (`PymnType`/`FieldCd`) applies to payment text fields generally; Card's description/purpose patches use standard bean validation.

## Open PO / dev decisions

| Decision | Owner |
|---|---|
| `MEMO_INFM` baseline-scope confirmation (G-CARD-3) | PO |
| `PRCH_STTS` full value set + labels (G-CARD-4) | PO |
| Establish FE test baseline (G-CARD-5) | dev |
