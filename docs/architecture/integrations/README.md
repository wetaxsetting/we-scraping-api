# architecture/integrations/

Cross-feature **external-integration** architecture: the bank-scraping / firm-banking gateway, the
FX-rate feed, the tax-authority (GDT) credential path, and any other third-party system the refactor
talks to.

## What goes here

- Coocon smart-gateway wiring (bank balance / transaction scraping, FX rates) — DEV/SIT vs UAT/PROD endpoints
- NXiSAS local-engine origin-licensing model (Own Account Transfer)
- Login-credentials verify-before-save orchestration (LOGIN + OTP + ACCT_LIST)
- GDT (tax authority) credential path — manual-only, distinct from bank scraping
- Integration sequence diagrams + failure / retry semantics

## What does NOT go here

- Per-feature service design that *consumes* an integration → [references/business-rules/](../../references/business-rules/)
- Endpoint contracts → [references/api-spec/](../../references/api-spec/)
- Login Credentials feature spec → [features/settings/login-credentials.md](../../features/settings/login-credentials.md)

> R&D refactor is **local-only** — no live bank integration. These docs describe the target
> architecture and the JEX behaviour being mirrored, not a wired production path.
