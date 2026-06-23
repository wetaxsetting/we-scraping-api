# Banking → Internal Transfer (VND) — Group Hub (Backend)

WeCMS menu group: **Banking → Internal Transfer (VND)**. Own-account fund movement between the tenant's enrolled VND accounts, plus the executed-transfer ledger.

## Submenu pages (leaf folders, 9-doc set each)

| Submenu | Leaf folder | Status |
|---|---|---|
| Own Account Transfer | [own-account-transfer/](own-account-transfer/README.md) | ✅ implemented (Path A endpoints live; composer credential-collection deferred — OQ-FE-1) |
| Transfer History | [transfer-history/](transfer-history/README.md) | ✅ implemented (backend + frontend landed 2026-05-20) |

> The older single-file specs are preserved inside each leaf as `legacy-spec.md` ([own-account-transfer/legacy-spec.md](own-account-transfer/legacy-spec.md) · [transfer-history/legacy-spec.md](transfer-history/legacy-spec.md)).
> JEX counterparts: Own Account Transfer = `trns_0001_001`; Transfer History = `trns_0003_001` (NAPAS transfer history).

## Related
- Frontend: [wecms-web/.../internal-transfer-vnd/](../../../../../wecms-web/docs/features/banking/internal-transfer-vnd/README.md)
- Banking hub: [../README.md](../README.md)
- Note: the **Payment → Internal Transfer** module (Scheduled Transfer / Transfer Result) is a different feature family under the Payment menu — see [../../payment/internal-transfer/](../../payment/internal-transfer/README.md).
