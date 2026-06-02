# Payment > Approval Box — Refactor Feature Hub

**Module:** Payment → Approval Management · **Screen:** Approval Box (single screen — the cross-feature inbox)
**Backend package:** `com.kosign.wecms.domain.payment.approvalbox`
**Frontend route:** `/payment/approval-box`
**Refactor slug / audit `WSVC_ID` / View Setting `WEB_SVC_ID`:** `approval-box`
**JEX source:** canonical service id `resl_0001_001` (NOT `pymn_0001_001` — see the naming-bug finding below) · OSMC variant `resl_0001_001_view_osmc.jsp`
**Status:** Implemented (read path). Mutation flows (Approval Details popup, approve/reject, bulk approve) deferred to follow-up MRs.

> Family hub. The detailed multi-doc design set lives in this folder. This README is the single entry
> the Payment menu / parent index points at; deep detail lives in the six design docs indexed below.

## Overview

Centralized approval inbox listing every pending Payment write request — internal-transfer, vendor,
expense, payroll, overseas, and corporate-card — with approval status, scheduled date, requestor, and
the approver chain. Supports approve / reject and approver hover cards, and gates dispatch of
firm-banking transfers. It is **not a payment sibling**: it is the cross-feature inbox consumed by
every Payment write flow before dispatch (see [payment hub README §1](../README.md)
and the cross-sibling audit-channel rule in §4.4 there — approval-flow audit rows carry
`WSVC_ID = "approval-box"`, not the sibling slug).

## Scope

- **In scope:** read-only paginated list with 5 tabs (All / Pending / In Progress / Approved /
  Rejected), date-range + search filter, sort, per-tenant visibility (admin sees all; non-admin sees
  rows they own / requested / approve — Redmine #42046), View Setting integration (`screenId =
  approval-box`), Excel export.
- **Deferred to follow-up MRs:** the Approval Details popup (legacy `comm_0195_001`, row-click),
  approve / reject WSVCs (owned by the detail popup), bulk approve / bulk delete, sticky-note memos.

Full scope rationale + the deferred table are in [discovery.md §7](discovery.md). Open questions for
the PO (5-vs-6 tabs, bulk approve, status-derivation source, audit policy, sortable whitelist, export
sub-header) are tracked in [discovery.md §10](discovery.md).

> **JEX naming-bug finding (do not lose).** The screen the PO calls "Approval Box" is implemented
> under JEX module **`resl`** (resolution / 결재), not `pymn` — the canonical service id is
> **`resl_0001_001`**. This contradicts the WSVC id `pymn_0001_001` listed in
> `docs/jex/screen-flows/payments.md` (that row is incorrect; `pymn_0001_001` is in fact Scheduled
> Payment (Vendor)). The full evidence and the suggested JEX bug-report follow-up live in
> [discovery.md §1](discovery.md). The refactor port treats `resl_0001_001` as the canonical JEX source.

## Design docs (index)

| Doc | Purpose |
|---|---|
| [discovery.md](discovery.md) | JEX → refactor discovery: §1 the `resl_0001_001` vs `pymn_0001_001` naming-bug finding, JEX entry points (menu / view JSPs / TS / action JSPs / WSVC / IDO / DAO / helper / entity), tables touched (DEV read-only), auth / role / approval-line deps, multi-tenant + multi-language touch points, scope, wire shapes, required renamings, and §10 open questions. |
| [design-backend.md](design-backend.md) | Spring Boot backend design — REST URI, controller / service / repository / DTO / entity layout, validation, error model, audit slug. |
| [design-frontend.md](design-frontend.md) | Next.js (App Router) frontend design — route, components, state, i18n, View Setting `screenId`, typography. |
| [api-flow.md](api-flow.md) | Mermaid sequence diagrams (list in-scope; view-detail / approve / reject / bulk-approve / download documented for completeness). |
| [database-impact.md](database-impact.md) | Flyway needs (default: none — read path), full `expt_resol_mstr` column list, schema-parity notes. |
| [migration-plan.md](migration-plan.md) | JEX-vs-Spring component mapping, phased order, risk register. |

## Cross-links

- Cross-sibling index: [payment hub README](../README.md) (§2.3 lists the shared `payment/approvalbox/` backend classes)
- Consumed by: [internal-transfer/README.md](../internal-transfer/README.md), [vendor-payment/README.md](../vendor-payment/README.md), [expense-payment/README.md](../expense-payment/README.md), [payroll/README.md](../payroll/README.md), [overseas-payment/README.md](../overseas-payment/README.md)
- Web counterpart: [`wecms-web/docs/features/payment/approval-box.md`](../../../../../wecms-web/docs/features/payment/approval-box.md)
- JEX reference: [screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) · [business-rules/approval.md](../../../../../docs/jex/business-rules/approval.md)
- Migration playbook: [MIGRATION_PLAYBOOK.md](../../../references/migration/MIGRATION_PLAYBOOK.md)
