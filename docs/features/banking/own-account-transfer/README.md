# Banking → Own Account Transfer

WeCMS menu: **Banking → Internal Transfer (Own Account Transfer)**. Move funds between the tenant's
own registered accounts, plus the history of executed own-account transfers. (Distinct from
Payment → Internal Transfer, which dispatches to third-party beneficiaries.)

## Screens in this group

| Screen | Doc | Status |
|---|---|---|
| Own Account Transfer | [own-account-transfer.md](own-account-transfer.md) | Implemented |
| Transfer History | [transfer-history.md](transfer-history.md) | Implemented |

## Related reference docs

| Layer | Location |
|---|---|
| API contract | [own-account-transfer-api.md](../../../references/api-spec/banking/own-account-transfer-api.md) |
| DB schema | [own-account-transfer-db.md](../../../references/database/banking/own-account-transfer-db.md) |
| JEX → Spring mapping | [own-account-transfer.md](../../../references/migration/banking/own-account-transfer.md) · [transfer-history.md](../../../references/migration/transfer-history.md) |
| Parity audits | [own-account-transfer-parity-2026-06-01.md](../../../references/audits/own-account-transfer-parity-2026-06-01.md) · [own-account-transfer-current-implementation-audit.md](../../../references/audits/own-account-transfer-current-implementation-audit.md) · [own-account-transfer-legacy-bank-business-rules.md](../../../references/audits/own-account-transfer-legacy-bank-business-rules.md) · [transfer-history-audit-2026-05-20.md](../../../references/audits/transfer-history-audit-2026-05-20.md) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |
