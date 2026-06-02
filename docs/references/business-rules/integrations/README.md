# backend/integrations/

External-system integration design for `wecms-api`.

## Purpose

Document the outbound clients (banks, ERP, e-invoice providers, tax portal, NXIS-AS firm-banking middleware) that `wecms-api` talks to. Includes contract notes, retry / timeout policy, secret handling, and the shared HTTP client abstraction.

## What goes here

- Per-integration markdown (`<provider>-integration.md` — e.g. `nxisas-vcb-spike.md` lives here once productionised)
- Common HTTP-client / retry / circuit-breaker patterns
- Secrets and credential rotation procedure references (no secrets in this folder)

## Refactor scope

Per memory `project_refactoring_scope`: bank integration **is out of scope** for the local R&D refactor — these docs capture **design intent only**. They become live work once the refactor leaves R&D.

## Related

- Services that call integrations: [`../services/`](../services/)
- JEX-side integration architecture: [`../../../jex/architecture/integration-architecture.md`](../../../../../docs/jex/architecture/) (read-only reference)
