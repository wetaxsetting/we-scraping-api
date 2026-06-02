# planning/open-questions/

Open Questions (OQ) awaiting PO / stakeholder decision.

## Purpose

Single place to track unresolved questions that block planning or implementation. Per feature, OQs live inline in the feature spec; this folder aggregates **cross-feature** OQs and **escalations** that have not been resolved within their owning feature for >1 sprint.

## What goes here

- `open-questions-tracker.md` — single rolling index of unresolved OQs across all features
- Per-topic OQ write-ups for questions too large to fit in a feature spec (rare)
- Resolved OQ archive (move into the feature file once PO signs off)

## Open items

- [`banking-reports-remaining-scope.md`](banking-reports-remaining-scope.md)
- [`documentation-follow-up.md`](documentation-follow-up.md) — post-migration doc decisions: ~~OQ-DOC-1 login-auth doc~~ ✅ resolved 2026-06-01 (`features/auth/`), ~~OQ-DOC-2 view-setting doc~~ ✅ resolved 2026-06-01 (`features/common/`), OQ-DOC-3 historical source refs, OQ-DOC-4 Smart Organizer scope, OQ-DOC-5 Transaction History API/DB contract, OQ-DOC-6 Banking report JEX mapping; **round-2 audit (2026-06-01):** OQ-DOC-7 bank-credentials↔login-credentials boundary, OQ-DOC-8 Daily Financial HS scope, OQ-DOC-9 Users doc accuracy, OQ-DOC-10 exchange-rate management scope, OQ-DOC-11 web change-password doc, OQ-DOC-12 approval-workflow/attachments cross-cutting docs, OQ-DOC-13 notifications/dashboard (not implemented). Raised 2026-06-01.

## Convention

- Each OQ has: id, feature, raised-by, raised-on, owner, blocking-task, status (open / proposed / resolved + date), and the decision text once resolved.
- Per memory `project_chart_of_accounts_oq_pending`: OQ-1 (USER role) and OQ-3 (legacy parent-cleanup branch) are currently open, blocking BE-8 at sprint day 4.

## Related

- Feature-local OQs stay in [`../../features/`](../../features/) inside each feature file.
- Roadmap: [`../roadmap/`](../roadmap/)
