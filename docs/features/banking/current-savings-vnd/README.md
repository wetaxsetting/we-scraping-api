# Banking → Current & Savings (VND)

WeCMS menu: **Banking → Current & Savings (VND)**. VND-denominated current / savings accounts —
balance inquiry, transaction ledger, and the Smart Organizer view.

## Screens in this group

| Screen | Doc | Status |
|---|---|---|
| Account Inquiry (VND) | [account-inquiry.md](account-inquiry.md) | Implemented |
| Transaction History (VND) | [transaction-history.md](transaction-history.md) | Implemented |
| Smart Organizer (VND) | [smart-organizer.md](smart-organizer.md) | Backend implemented (`SmartOrganizerController` + `OrganizerRuleController` + V172–V174); Next.js route exists |

## Related reference docs

| Layer | Location |
|---|---|
| API contract | [account-inquiry-vnd-api.md](../../../references/api-spec/banking/account-inquiry-vnd-api.md) |
| DB schema | [account-inquiry-vnd-db.md](../../../references/database/banking/account-inquiry-vnd-db.md) |
| JEX → Spring mapping | [references/migration/](../../../references/migration/) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |

The FCY counterpart is [../current-savings-fcy/](../current-savings-fcy/). Foreign-exchange rates used
in the FCY views come from Payment → Add-on → [exchange-rate-inquiry.md](../../payment/exchange-rate-inquiry.md).
