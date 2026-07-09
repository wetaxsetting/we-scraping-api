> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in: [docs/features/banking/current-savings-fcy/](account-inquiry/README.md)

# Banking → Current & Savings (FCY)

WeCMS menu: **Banking → Current & Savings (FCY)**. Foreign-currency current / savings accounts —
balance inquiry, transaction ledger, and the Smart Organizer view. The refactor widens the legacy
USD-only screen to a multi-currency scope. The legacy single-file specs for this group have been
superseded by the standardized per-module folders below.

## See the authoritative module docs

- [account-inquiry/README.md](account-inquiry/README.md) — Account Inquiry (FCY)
- [transaction-history/README.md](transaction-history/README.md) — Transaction History (FCY)
- [../smart-organizer/README.md](../smart-organizer/README.md) — Smart Organizer (shared VND/FCY)

## Related reference docs

| Layer | Location |
|---|---|
| API contract | [account-inquiry-fcy-api.md](../../../references/api-spec/banking/account-inquiry-fcy-api.md) |
| DB schema | [account-inquiry-fcy-db.md](../../../references/database/banking/account-inquiry-fcy-db.md) |
| JEX → Spring mapping | [references/migration/](../../../references/migration/) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |

The VND counterpart is [../current-savings-vnd/](../current-savings-vnd/README.md).
