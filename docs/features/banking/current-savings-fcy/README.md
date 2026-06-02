# Banking → Current & Savings (FCY)

WeCMS menu: **Banking → Current & Savings (FCY)**. Foreign-currency current / savings accounts —
balance inquiry, transaction ledger, and the Smart Organizer view. The refactor widens the legacy
USD-only screen to a multi-currency scope (`currency=FCY` or a concrete `currency=<CCY>`).

## Screens in this group

| Screen | Doc | Status |
|---|---|---|
| Account Inquiry (FCY) | [account-inquiry.md](account-inquiry.md) | Implemented |
| Transaction History (FCY) | [transaction-history.md](transaction-history.md) | Implemented |
| Smart Organizer (FCY) | [smart-organizer.md](smart-organizer.md) | ⚠️ discovery scaffold (no refactor implementation yet) |

## Related reference docs

| Layer | Location |
|---|---|
| API contract | [account-inquiry-fcy-api.md](../../../references/api-spec/banking/account-inquiry-fcy-api.md) |
| DB schema | [account-inquiry-fcy-db.md](../../../references/database/banking/account-inquiry-fcy-db.md) |
| JEX → Spring mapping | [references/migration/](../../../references/migration/) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |

The VND counterpart is [../current-savings-vnd/](../current-savings-vnd/). FX rates used here come from
Payment → Add-on → [exchange-rate-inquiry.md](../../payment/exchange-rate-inquiry.md).
