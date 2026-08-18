# Haengsung Oracle Integration

Customer-specific integration between WeCMS and **HAENGSUNG ELECTRONICS**' Oracle ERP
(`PRDT_TYPE = 'H'`, tenant `VN_VNM0004_000014`).

**Status:** active · **Last reviewed:** 2026-08-17

> **Why this lives outside the six menu domains.** `features/` is organised by the live WeCMS menu
> (`auth` · `common` · `banking` · `payment` · `addon-service` · `settings`). Haengsung is not a menu
> item — it is a per-customer backend integration that spans several modules. It therefore sits under
> `features/integrations/` alongside any future customer-specific integration, rather than being forced
> into one menu domain. Moved here from the `features/` root on 2026-08-17.

## Documents

| Doc | Covers |
|---|---|
| [payment-refresh.md](payment-refresh.md) | Payment refresh flow — the anchor document; the others reference its status model |
| [card-account-sync.md](card-account-sync.md) | Scheduled account-history + card-approval sync |
| [exchange-rate-sync.md](exchange-rate-sync.md) | Exchange-rate sync (`BANK_EXCH_RATE_INFM` → `TB_FCY_EXRATE`) |
| [dispatch-result-writeback.md](dispatch-result-writeback.md) | Dispatch-result write-back (AFTER_COMMIT, no XA, retryable) |
| [approval-status-writeback.md](approval-status-writeback.md) | Approval-status write-back |

## Related — filed by document type, not by feature

| Doc | Where | Why there |
|---|---|---|
| Oracle integration final audit | [`audits/legacy-parity/haengsung-oracle-integration-final-audit.md`](../../../audits/legacy-parity/haengsung-oracle-integration-final-audit.md) | It is an audit record |
| SIT verification | [`audits/legacy-parity/haengsung-oracle-integration-sit-verification.md`](../../../audits/legacy-parity/haengsung-oracle-integration-sit-verification.md) | Audit record |
| SIT deployment checklist | [`deployment/haengsung-sit-deployment-checklist.md`](../../../deployment/haengsung-sit-deployment-checklist.md) | Deployment artifact |
| Integration roadmap | [`planning/haengsung-oracle-integration-roadmap.md`](../../../planning/haengsung-oracle-integration-roadmap.md) | Forward-looking plan |
| Daily Financial (HS) report | [`features/banking/reports/daily-financial-hs/`](../../banking/reports/daily-financial-hs/) | It **is** a menu screen — stays in the banking domain |
