# Corporate Card — Test Scenarios (Card Transactions)

## Backend tests (present — 5 files / 44 methods)

| Test file | Covers |
|---|---|
| `CardTransactionsControllerTest` | Endpoint wiring, validation (path patterns, required date range), authz, request/response shapes, error envelopes |
| `CardTransactionsServiceTest` | `list`/totals, `toRow` (`approvers[]` empty), classify (patch/reporting-account), link amount guard (`WBE_LINK_AMOUNT_MISMATCH`), request-approval fan-out (`APPR_DV='33'`), withdraw/change-line, memo author guard, receipt create, purpose options |
| `CardTransactionScrapeServiceTest` | Session/challenge scrape (`/sync-session`): LOGIN → QNA/OTP/CAPTCHA → CARD_USING → persist; `*_REQUIRED` returns |
| `CardTransactionSyncServiceTest` | Deprecated headless `/sync`; abort on challenge-gated banks (`WBE_CARD_SYNC_NEEDS_RE_AUTH`) |
| `CardTrnsHstrSortFieldTest` | Sort whitelist resolution + default ORDER BY |

## Backend scenarios to confirm at SIT

- List default ORDER BY (`TRSC_DT DESC, TRSC_TM DESC, CARD_SEQ_NO DESC`) + each filter (bank, card, link status, keyword, date range).
- Totals (TOTAL / APPROVAL REQUESTED / APPROVAL NOT REQUESTED) parity with `CARD_TRNS_COUNT_R001`.
- Reporting-account PUT validation (`WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID`) + clear (idempotent).
- Card delete in-use guard (`WBE1153`); bulk-create dedupe (`inserted_count`/`skipped_count`).
- Request Approval multipart (with + without attachments); withdraw; change-line.
- Link → unlink round-trip; receipt create → select; period-limits default fallback (90 days).
- `PRCH_STTS` status filter with real `TB_PAY_*` data (G-CARD-4).

## Frontend tests — MISSING (G-CARD-5)

No `.test.*` files exist for any Card Transactions component. Largest single coverage gap (consistent across all payment modules). Recommended baseline (dev):

- `cardTransactionMappers` — wire → in-memory mapping, including `approvers[]` always empty, `using_type`/`auth_code`/`city`/`note` carried, display-name resolution from chart-tree + purpose-options.
- `CardTransactionsPage` — fetch/refresh, filter reset to page 1, inline-edit PATCH, dialog stacking.
- Dialogs — Link, New Receipt, Card Management, Update Cards (session/challenge loop), Approval Ref Detail (withdraw/change-line).
