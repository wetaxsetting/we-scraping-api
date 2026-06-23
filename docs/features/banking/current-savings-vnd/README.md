> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in: [docs/features/banking/current-savings-vnd/](account-inquiry/README.md)

# Banking → Current & Savings (VND)

WeCMS menu: **Banking → Current & Savings (VND)**. VND-denominated current / savings accounts —
balance inquiry, transaction ledger, and the Smart Organizer view. The legacy single-file specs for
this group have been superseded by the standardized per-module folders below.

## See the authoritative module docs

- [account-inquiry/README.md](account-inquiry/README.md) — Account Inquiry (VND)
- [transaction-history/README.md](transaction-history/README.md) — Transaction History (VND)
- [../smart-organizer/README.md](../smart-organizer/README.md) — Smart Organizer (shared VND/FCY)

## Related reference docs

| Layer | Location |
|---|---|
| API contract | [account-inquiry-vnd-api.md](../../../references/api-spec/banking/account-inquiry-vnd-api.md) |
| DB schema | [account-inquiry-vnd-db.md](../../../references/database/banking/account-inquiry-vnd-db.md) |
| JEX → Spring mapping | [references/migration/](../../../references/migration/) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |

The FCY counterpart is [../current-savings-fcy/](../current-savings-fcy/README.md).
