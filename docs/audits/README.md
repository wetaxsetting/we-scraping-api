# Audits — Index

**Status:** active · **Last reviewed:** 2026-08-17

> Human-friendly index for everything under `wecms-api/docs/audits/`. This folder holds **documentation
> governance** records, **per-module audit triplets**, **per-feature parity/verification** audits, and
> **historical one-off** audits. Filenames are stable (never renamed); use this index to find what you need.
>
> **New here?** Read [../README.md](../README.md) → **Start Here** first. The audits below are the *history and
> reconciliation record* — the day-to-day spec for a feature lives in `../features/<module>/<screen>/`.
>
> **Status legend:** **active** = current reference, kept up to date · **historical** = point-in-time record,
> preserved unchanged (findings still valid for their date) · **superseded** = replaced by a later decision/doc.

---

## An audit stops at the findings — a human approves what happens next

**Added 2026-08-18.** An audit or investigation is **not** a licence to change code. Full rule:
[`../../CLAUDE.md`](../../CLAUDE.md) §17 "Human approval gate between investigation and
implementation".

```
AUDIT  ──identifies──▶  BUG / ISSUE  ──human approval──▶  IMPLEMENTATION
```

- **The record exists before the investigation**, here in `audits/<category>/` as
  `descriptive-name-YYYY-MM-DD.md` — not written up afterwards.
- **A request to *check · investigate · review · audit · analyze* authorizes documentation and
  read-only inspection only**: source reading, Git/history, existing tests, permitted read-only DB
  inspection, root-cause analysis, parity comparison, and a **recommended** implementation. It does
  **not** authorize modifying source, writing implementation tests, changing SQL, migrations, or
  configuration — nor committing, pushing, merging, or deploying.
- **Finish at the gate**, with the three dimensions stated explicitly:

  ```
  Investigation Status:  COMPLETE
  Approval Status:       PENDING
  Implementation Status: NOT STARTED
  ```

- **`Audit: COMPLETE` means the investigation is complete — not that the defect is fixed.** That
  combination above is a perfectly valid *final* state for an investigation request.
- **The human chooses** `APPROVE IMPLEMENTATION` · `DENY` · `NEEDS MORE INVESTIGATION`. *"OK"*,
  *"thanks"*, *"looks good"* are **not** approval.
- **When a fix is approved**, the audit **stays here unchanged** and the work gets its own record in
  [`../bugs/`](../bugs/) or [`../issues/`](../issues/), which **links back to this audit as evidence** —
  never a copy of it, never a conversion of it.
- **When implementation is denied**, the audit still stays: record the decision
  (`Approval Status: DENIED`), preserve every piece of evidence, and stop. **A denied audit is not
  deleted** — it is the answer for the next person who hits the same symptom.

This binds every team — **Cambodia · Vietnam · Korea · future contributors** — and every tool
(Claude Code · Agents · Cursor · any other AI assistant).

---

> **Reorganised 2026-08-17.** The formerly flat directory is now grouped into the six folders below,
> and the 15 audit files that lived in `docs/references/audits/` were migrated here. `docs/audits/` is the
> **single authoritative home for audit records** — write new audits here.

---

## Governance & Documentation — [`governance/`](governance/)

The cross-cutting documentation-quality program plus schema/key-generation governance records.

| File | Notes |
|---|---|
| [api-contract-drift.md](governance/api-contract-drift.md) | API Contract Drift · Interface-spec vs controllers/DTOs — undocumented/changed endpoints & fields · active |
| [authorization-doc-drift.md](governance/authorization-doc-drift.md) | Authorization Drift · `@PreAuthorize`/`@menuAuthz` vs documented gates; unauth-endpoint findings · active |
| [database-doc-drift.md](governance/database-doc-drift.md) | Database Mapping Drift · `database-mapping.md` vs entities/SQL/migrations; version-citation & view findings · active |
| [database-phase-a-implementation-plan.md](governance/database-phase-a-implementation-plan.md) | Phase A Implementation Plan · The five refactor-owned "safe improvement" items (A1–A5) split out of the remediation roadmap — Java wiring plus one DEV-only DB object, no legacy-schema/wire/DTO change, each independently shippable · **planning only, no code changed** · active |
| [database-remediation-roadmap.md](governance/database-remediation-roadmap.md) | Database Remediation Roadmap · Phased plan built on the 2026-06-24 database-consistency audit; classifies every finding into refactor-owned / legacy-owned / PO-gated ownership buckets and states what may be actioned freely · **planning only, no code changed** · active |
| [documentation-folder-rationalization.md](governance/documentation-folder-rationalization.md) | Folder Rationalization · Pre-deletion impact audit of `archive/`/`planning/`/`migration/` · **superseded** (folders later removed by decision) |
| [documentation-governance-roadmap.md](governance/documentation-governance-roadmap.md) | Governance Roadmap · Ongoing operating model — ownership, maintenance workflow, audit schedule, drift prevention, legacy-DB governance · active |
| [documentation-remediation-closure-report.md](governance/documentation-remediation-closure-report.md) | Remediation Closure Report · Project timeline, what was fixed, link-health history, post-closure notes (DB-folder + docs-folder removals) · active |
| [documentation-remediation-plan.md](governance/documentation-remediation-plan.md) | Remediation Plan · P1–P4 fix plan + DC-1/DC-2 deployment-context items; the living tracker of doc fixes · active |
| [documentation-truth-audit.md](governance/documentation-truth-audit.md) | Documentation Truth Audit · Executive summary of the code-vs-docs audit (6 modules × 5 dimensions); module scorecards + drift counts · historical (2026-06-23 snapshot; reference) |
| [efs-guide-1.2.6-open-questions-closure.md](governance/efs-guide-1.2.6-open-questions-closure.md) | EFS API Guide v1.2.6 Closure · Point-in-time reading of the vendor spec resolving the open questions it left; body preserved unchanged behind a status banner listing what was implemented in the same-day 2026-07-31 remediation · historical (banner is current) |
| [partner-parity-gap-matrix-audit.md](governance/partner-parity-gap-matrix-audit.md) | Partner Gap Matrix Audit · Verification of the partner-facing `WECMS-CAN-UPDATE-FOR-PARTNER-BI.xlsx` gap matrix (2026-07-24) · ⚠️ all bare `V<n>` citations are **pre-rebaseline** · historical |
| [wb-get-next-val-dev-function-definitive-branch-list-2026-07-03.md](governance/wb-get-next-val-dev-function-definitive-branch-list-2026-07-03.md) | `wb_get_next_val` Definitive Branch List · States exactly which branches should exist in the refactor DEV function — classified Must-exist / Missing / Dormant-parity / Removable, each verified against a real executable caller in `wecms-api/src` · active |
| [wb-get-next-val-legacy-uat-parity-2026-07-03.md](governance/wb-get-next-val-legacy-uat-parity-2026-07-03.md) | `wb_get_next_val` Legacy-UAT Parity · Branch-by-branch comparison of legacy UAT (~130 branches) against refactor DEV (66), plus every Spring call path through the `LegacyFunctionRepository` funnel (39 helpers) · active |
| [wb-get-next-val-parity-closure-2026-06-24.md](governance/wb-get-next-val-parity-closure-2026-06-24.md) | `wb_get_next_val` Parity Closure · Closes the key-generation parity question — every column legacy generates via the function is matched by the refactor; **no branch is missing or needs enhancement, and the DB function must not be modified** · active |
| [web-svc-id-route-migration-audit.md](governance/web-svc-id-route-migration-audit.md) | `WEB_SVC_ID` → Route-Slug Migration · Splits the identifier families (View Setting / audit / memo) so each can be reviewed independently; **DDL + data SQL prepared, execution PO-gated** — deliverable is [`sql/2026/widen-screen-identifier-columns-and-rekey-card-memos-2026-07-02.sql`](../sql/2026/widen-screen-identifier-columns-and-rekey-card-memos-2026-07-02.sql) · active |

---

## Module Audit Triplets — [`modules/`](modules/)

One triplet per module — **`-docs-audit`** (doc-vs-code) · **`-gap-analysis`** (parity matrix) · **`-source-index`** (code ↔ docs ↔ tests).

| File | Notes |
|---|---|
| [addon-service-docs-audit.md](modules/addon-service-docs-audit.md) | Add-on Service · doc-vs-code re-audit (2026-06-23), triggered by implementation changes after the previous documentation pass; verdict — the consolidated folder docs are accurate and current · historical |
| [addon-service-gap-analysis.md](modules/addon-service-gap-analysis.md) | Add-on Service · gap matrix, every row verified against the working tree, grouped by "code exists / doc missing" and open decisions · historical |
| [addon-service-source-index.md](modules/addon-service-source-index.md) | Add-on Service · validated code ↔ docs ↔ tests index (2026-06-23) · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [auth-docs-audit.md](modules/auth-docs-audit.md) | Auth · doc-vs-code audit + standardization (2026-06-23) — replaced two flat summary docs that carried stale claims with the standard per-feature folder set · historical |
| [auth-gap-analysis.md](modules/auth-gap-analysis.md) | Auth · gap matrix in the MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING vocabulary, each row tagged with a gap *kind* and an owner · historical |
| [auth-source-index.md](modules/auth-source-index.md) | Auth · code ↔ docs ↔ tests index across `wecms-api` and `wecms-web` · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [banking-docs-audit.md](modules/banking-docs-audit.md) | Banking · doc-vs-code audit (2026-06-23) — reconciles stale docs, confirms the 9-doc API / 6-doc WEB structure, and produces the cross-tree index + gap matrix · historical |
| [banking-gap-analysis.md](modules/banking-gap-analysis.md) | Banking · gap matrix; rows corrected during the pass are retained as the audit trail alongside their post-fix state · historical |
| [banking-source-index.md](modules/banking-source-index.md) | Banking · cross-tree code ↔ docs ↔ tests index · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [claude-docs-audit.md](modules/claude-docs-audit.md) | CLAUDE.md Reconciliation · reconciles the two `CLAUDE.md` **governance/rule files** against the codebase and the six reconciled feature-doc trees — a governance artifact, **not** a feature specification · historical |
| [claude-gap-analysis.md](modules/claude-gap-analysis.md) | CLAUDE.md Reconciliation · gap matrix over the rule files, every row verified against the working tree · historical |
| [claude-source-index.md](modules/claude-source-index.md) | CLAUDE.md Reconciliation · every suspect rule-file claim mapped to the current implementation; a ✔ marks a reference opened and verified on 2026-06-23 · historical |
| [common-docs-audit.md](modules/common-docs-audit.md) | Common (cross-cutting) · doc-vs-code re-audit of the shared services — View Setting, Excel export, common sort, audit log, error model · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [common-gap-analysis.md](modules/common-gap-analysis.md) | Common (cross-cutting) · gap matrix over the shared services, with the fixed gap vocabulary defined inline · historical |
| [common-source-index.md](modules/common-source-index.md) | Common (cross-cutting) · validated source index — §3 carries the registered `ExportRowProvider` roster, the authoritative list of export-wired screens · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [payment-docs-audit.md](modules/payment-docs-audit.md) | Payment · full doc-vs-code re-audit (2026-06-23) after the code changed following the previous docs pass · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [payment-gap-analysis.md](modules/payment-gap-analysis.md) | Payment · consolidated gap table across every Payment module (Approval Box, the 5 payment types, Card Transactions), each row with class, kind, and owner · historical |
| [payment-source-index.md](modules/payment-source-index.md) | Payment · cross-tree index mapping every Payment module to its backend, frontend, docs, and tests · historical |
| [settings-docs-audit.md](modules/settings-docs-audit.md) | Settings · module-level doc audit (2026-06-23) completing the six-module documentation programme · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [settings-gap-analysis.md](modules/settings-gap-analysis.md) | Settings · cross-module gap roll-up; per-module detail stays in each `features/settings/<module>/refactor-gap-analysis.md` · historical |
| [settings-source-index.md](modules/settings-source-index.md) | Settings · code ↔ docs ↔ tests index across all Settings submenus, with wire base path and primary tables per module · historical |

---

## Feature Verification & Parity Audits — [`legacy-parity/`](legacy-parity/)

Per-feature legacy-vs-refactor parity / verification records — the current parity reference for each feature. Includes the parity checklists migrated from the former `references/audits/`.

| File | Notes |
|---|---|
| [account-filter-parity-audit.md](legacy-parity/account-filter-parity-audit.md) | Banking · Transaction History "Bank Account Filter" (A1-01) · Parity of the account-list query shared with Account Inquiry; approved by the PO 2026-07-24 · active |
| [tenant-scope-correction-implementation-review.md](legacy-parity/tenant-scope-correction-implementation-review.md) | Banking · A1 tenant-scope fix in `BankAccountPermissionService` · Readiness review — verdict **safe to implement immediately**: a two-line swap to an already-existing repository method, no schema, wire, API or DTO change · **planning only** · active |
| [account-inquiry-fcy-parity-audit.md](legacy-parity/account-inquiry-fcy-parity-audit.md) | Banking · Account Inquiry (FCY) · Legacy-vs-refactor parity |
| [bank-transaction-sync-debit-matched-parity-gap.md](legacy-parity/bank-transaction-sync-debit-matched-parity-gap.md) | Banking · Bank Transaction Sync · ✅ **Implemented + strict legacy parity** (2026-07-09) — "Debit Matched {matched}/{debit}" per-currency row. Auto-match now = Internal Transfer / Vendor / Expense **+ Overseas** (R007 Branch 3), **Payroll excluded** (legacy has no payroll auto-match). Per-currency `auto_matched_count` + `auto_match_enabled` + FE row |
| [bank-transaction-sync-fixed-deposit-parity.md](legacy-parity/bank-transaction-sync-fixed-deposit-parity.md) | Banking · Bank Transaction Sync · Fixed-Deposit vs Current/Savings account-type routing in the scrape popup · ✅ **implemented + legacy parity** (2026-07-16); sibling of the debit-matched audit above · active |
| [card-transaction-link-reference-legacy-audit.md](legacy-parity/card-transaction-link-reference-legacy-audit.md) | Payment · Card Transactions "Link Reference" modal (Expenses e-Invoice tab + Receipt tab + New Receipt sub-popup + GDT-login empty state) · **audit only, no code or schema changed** · ⚠️ bare `V<n>` citations are **pre-rebaseline** · active |
| [card-transactions-legacy-vs-refactor-audit.md](legacy-parity/card-transactions-legacy-vs-refactor-audit.md) | Payment · Corporate Card · Legacy-vs-refactor audit |
| [card-transactions-parity-checklist.md](legacy-parity/card-transactions-parity-checklist.md) | Payment · Card Transactions · Pre-merge **sign-off gate** — tick each observable behaviour only after seeing it in both live JEX and the refactor; intentional deltas are listed in their own section · active |
| [card-transactions-status-label-parity-2026-07-04.md](legacy-parity/card-transactions-status-label-parity-2026-07-04.md) | Payment · Card Transactions "Status" column · Labels had drifted from the legacy `wb_get_prch_stts_nm` function (code `2` rendering as "Cancelled"); **resolved PO-directed** by resolving the label server-side through that function and deleting the hand-maintained i18n table so it can never drift again · active |
| [cash-flow-exchange-rate-legacy-audit.md](legacy-parity/cash-flow-exchange-rate-legacy-audit.md) | Banking · Cash Flow · Exchange-rate legacy flow audit + fix plan — refactor shows static 26.000 vs legacy 26.455 (2026-07-02) |
| [chart-of-accounts-final-parity-audit.md](legacy-parity/chart-of-accounts-final-parity-audit.md) | Settings · Chart of Accounts · Final parity |
| [corporate-card-transaction-scrape-legacy-vs-refactor-audit.md](legacy-parity/corporate-card-transaction-scrape-legacy-vs-refactor-audit.md) | Payment · Corporate Card transaction scrape/insert · Root-causes the 4×-duplicated merchant transaction (one row inserted per registered card); already-corrupted rows need a PO-approved re-scrape — a cleanup script is drafted but **not executed** · active |
| [legacy-verification.md](legacy-parity/legacy-verification.md) | Payment · WeCMS Batch (firm-banking dispatch) · **Decision record** (2026-07-29) for the D-1/D-2/D-3 fixes — legacy JEX source read first, refactor second, then fix. No DB reachable that session, so no runtime/DB claim is asserted as live-verified. Companion to [test-coverage-audit.md](./legacy-parity/test-coverage-audit.md). |
| [daily-cash-refactor-parity-verification.md](legacy-parity/daily-cash-refactor-parity-verification.md) | Banking Reports · Daily Cash · Parity verification with a **data-source correction** — the live path calls legacy `rept_0900_001_rec1..4` directly and the earlier Java recompute is bypassed · ⚠️ bare `V<n>` citations are **pre-rebaseline** · active |
| [daily-financial-hs-final-audit.md](legacy-parity/daily-financial-hs-final-audit.md) | Banking · Daily Financial (HS) · **Final closure audit** — scope, fixes, PO-approved divergences (Excel title localization, KRW), validation (2026-07-02) |
| [daily-financial-hs-legacy-audit.md](legacy-parity/daily-financial-hs-legacy-audit.md) | Banking · Daily Financial (HS) · Legacy behavior audit + parity plan (2026-07-01) |
| [daily-financial-hs-parity-verification-report.md](legacy-parity/daily-financial-hs-parity-verification-report.md) | Banking Reports · Daily Financial (HS, `rept_0017_001`) · Category-by-category parity from verified evidence only — source reads of both trees, the green unit-test suite, template SHA-256/CRC checks, and side-by-side screenshots · active |
| [daily-financial-parity-verification-report.md](legacy-parity/daily-financial-parity-verification-report.md) | Banking Reports · Daily Financial (CMC, `rept_0015_001`) · Source-code-evidence parity report; live-DB verification was attempted but both MCP servers returned an empty schema, so DB findings lean on the migration ledger plus service code · active |
| [erp-transaction-history-audit.md](legacy-parity/erp-transaction-history-audit.md) | Settings · ERP Transaction History · Independent pre-sign-off audit of the Spring backend, the Next.js panel, and the six design docs (2026-05-11) · ⚠️ bare `V<n>` citations are **pre-rebaseline** · historical |
| [expense-payment-excel-download-smoke-test.md](legacy-parity/expense-payment-excel-download-smoke-test.md) | Payment · Expense Payment Excel **Download** (22-column template) · Pre-SIT manual smoke-test checklist — implemented; verification pending on live data · active |
| [expense-payment-excel-upload-audit.md](legacy-parity/expense-payment-excel-upload-audit.md) | Payment · Expense · Excel Upload / Refresh · Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [firm-banking-history-gdt-einvoice-parity-2026-07-17.md](legacy-parity/firm-banking-history-gdt-einvoice-parity-2026-07-17.md) | Settings · Firm Banking History → Vietnam GDT (e-Invoice) inquiry rows · Parity audit of the missing GDT scrape log; PO direction was **must match legacy** · active |
| [firm-banking-history-parity-checklist.md](legacy-parity/firm-banking-history-parity-checklist.md) | Settings · Firm Banking History · Phase 2–4 acceptance checklist against JEX `comm_0221_001` · active |
| [firm-banking-settings-parity-checklist.md](legacy-parity/firm-banking-settings-parity-checklist.md) | Settings · Firm Banking Settings · Refactor parity checklist against JEX `comm_0220_*` · ⚠️ dated record — every `V<n>` is **pre-rebaseline** and preserved as written · historical |
| [haengsung-oracle-integration-final-audit.md](legacy-parity/haengsung-oracle-integration-final-audit.md) | Integrations · HAENGSUNG Oracle · Final full audit across all five systems — verdict ✅ code/DB/legacy complete and legacy-faithful for all 11 in-scope flows, no credential leak, no DB write performed. See the §Re-audit section for the 🟡 conditional **release** verdict after the bare-placeholder config change · active |
| [haengsung-oracle-integration-sit-verification.md](legacy-parity/haengsung-oracle-integration-sit-verification.md) | Integrations · HAENGSUNG Oracle · Step 8 SIT end-to-end verification (the gate to UAT) — records what was verified and how, plus what still has to be executed on the live SIT box · **verification only, no code changed** · active |
| [internal-transfer-excel-download-smoke-test.md](legacy-parity/internal-transfer-excel-download-smoke-test.md) | Payment · Internal Transfer Excel **Download** (21-column template) · Pre-SIT manual smoke-test checklist — implemented; verification pending on live data · active |
| [internal-transfer-excel-upload-audit.md](legacy-parity/internal-transfer-excel-upload-audit.md) | Payment · Internal Transfer · Excel Upload · Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [internal-transfer-parity-checklist.md](legacy-parity/internal-transfer-parity-checklist.md) | Payment · Internal Transfer · Refactor parity checklist against the legacy screens · active |
| [legacy-vs-refactor-output-parity-audit.md](legacy-parity/legacy-vs-refactor-output-parity-audit.md) | Payment Dispatch · Final **observable-output** parity, source-proven — proves the firm-banking dispatch artifacts for all 5 payment types match legacy field-by-field for identical input. An output audit, **not** a code-quality review · active |
| [login-credentials-legacy-vs-refactor-audit.md](legacy-parity/login-credentials-legacy-vs-refactor-audit.md) | Settings · Login Credentials · Bank/GDT cert + cipher parity (§7) |
| [login-legacy-compatibility-2026-06-10.md](legacy-parity/login-legacy-compatibility-2026-06-10.md) | Auth · Login · Password (SHA-256) coexistence + account-creation parity |
| [notification-approval-detail-popup-parity-audit.md](legacy-parity/notification-approval-detail-popup-parity-audit.md) | Common · Notifications · Approval-detail popup parity |
| [notification-popup-legacy-vs-refactor-analysis.md](legacy-parity/notification-popup-legacy-vs-refactor-analysis.md) | Common · Notifications · Popup behaviour analysis |
| [operation-status-inquiry-parity.md](legacy-parity/operation-status-inquiry-parity.md) | Add-on · Operation Status · Parity |
| [operation-status-inquiry-status-column-legacy-audit.md](legacy-parity/operation-status-inquiry-status-column-legacy-audit.md) | Add-on · Operation Status Inquiry · Result-table **Status column rendering** parity fix (filtering was already corrected in the dropdown pass) · active |
| [operation-status-inquiry-status-dropdown-legacy-audit.md](legacy-parity/operation-status-inquiry-status-dropdown-legacy-audit.md) | Add-on · Operation Status Inquiry · **Status dropdown** parity audit against legacy `aosv_0001_001`; companion to the status-column audit above · active |
| [overseas-payment-excel-download-smoke-test.md](legacy-parity/overseas-payment-excel-download-smoke-test.md) | Payment · Overseas Payment Excel **Download** (22-column template) · Pre-SIT manual smoke-test checklist — implemented; verification pending on live data · active |
| [overseas-payment-excel-upload-audit.md](legacy-parity/overseas-payment-excel-upload-audit.md) | Payment · Overseas · Excel Upload / Refresh · Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [own-account-transfer-current-implementation-audit.md](legacy-parity/own-account-transfer-current-implementation-audit.md) | Banking · Own Account Transfer · The refactor implementation audited against the legacy business-rules doc · ⚠️ its "AES-GCM decrypt" statements are **superseded** — the live cert cipher is legacy pgcrypto AES-256-CBC · historical |
| [own-account-transfer-legacy-bank-business-rules.md](legacy-parity/own-account-transfer-legacy-bank-business-rules.md) | Banking · Own Account Transfer · The **legacy source-of-truth** rules, extracted read-only from JEX (`trns_0001_001` + `trns_0001_007` + the NXiSAS local-engine flow). Every claim is cited to a legacy `file:line`; unconfirmed rules are marked *Unknown*, never guessed · active |
| [own-account-transfer-parity-2026-06-01.md](legacy-parity/own-account-transfer-parity-2026-06-01.md) | Banking · Own Account Transfer · Legacy ↔ refactor parity audit (2026-06-01) · ⚠️ its "uses AES-GCM" statements are **superseded** — the live cert cipher is legacy pgcrypto AES-256-CBC · historical |
| [payment-approval-detail-delete-legacy-vs-refactor-audit.md](legacy-parity/payment-approval-detail-delete-legacy-vs-refactor-audit.md) | Payment / Card · "Delete Approval Details" · Root-causes the HTTP 500 `WBE_DB` (`expt_resol_detl_fk01` violation) raised from the Card Transactions approval-details popup · **audit only, no code changed, no tests added** · active |
| [payment-excel-download-refactor-audit.md](legacy-parity/payment-excel-download-refactor-audit.md) | Payment · Excel **Download** master parity audit — the template-populated generated report across all 5 payment types and all 4 surfaces; the five per-type smoke-test checklists hang off this one · **documentation only, no source modified** · active |
| [payment-excel-upload-crossmodule-verification.md](legacy-parity/payment-excel-upload-crossmodule-verification.md) | Payment · all 5 modules · Excel Upload → create batch · **Cross-module verification (2026-07-25)** of the five audits above against current code. Corrects partner-matrix row #24 (legacy also parses server-side with POI — same mechanism, not "different"); 5 divergences found + fixed (payroll & overseas `WDRL_CCY_CD`, overseas `TRNF_DT`, workbook mandatory at save, save-side single-network guard). Read with the per-module audits. |
| [payroll-payment-excel-download-smoke-test.md](legacy-parity/payroll-payment-excel-download-smoke-test.md) | Payment · Payroll Excel **Download** (20-column template) · Pre-SIT manual smoke-test checklist — implemented; verification pending on live data · active |
| [payroll-payment-excel-upload-audit.md](legacy-parity/payroll-payment-excel-upload-audit.md) | Payment · Payroll · Excel Upload / Refresh · Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [purpose-refactor-parity-audit.md](legacy-parity/purpose-refactor-parity-audit.md) | Settings · Purpose · Parity |
| [purpose-settings-audit.md](legacy-parity/purpose-settings-audit.md) | Settings · Purpose · End-to-end final audit spanning Phase 1 docs → Phase 2 backend → Phase 3 frontend (including Excel upload), reviewed against the project rules, the JEX source of truth, and the design docs · historical |
| [sales-expense-einvoices-scraping-parity.md](legacy-parity/sales-expense-einvoices-scraping-parity.md) | Add-on · e-Invoices · Scraping/coexistence parity |
| [scheduler-audit.md](legacy-parity/scheduler-audit.md) | Infrastructure · Every `@Scheduled` method in `wecms-api` (12 methods / 13 annotations / 9 classes), cross-checked against the live `shedlock` table. Excludes the legacy JEX BCS batch and the DB-driven `TB_BATCH_SCHEDULE_*` windows · active |
| [scheduler-legacy-parity-audit.md](legacy-parity/scheduler-legacy-parity-audit.md) | Payment · WeCMS Batch · Schedulers · **Legacy-parity** layer for the Spring schedulers vs the legacy WeCMS/JEX batch processes (2026-07-28) — duplicated/missing jobs, timing, ordering, concurrency, shared-table conflicts. Audit only, no code changed. Supersedes [scheduler-audit.md](./legacy-parity/scheduler-audit.md) on inventory (see F-0). Records the K9 conflict that removed `BulkExecutionRetryJob` on 2026-07-29. |
| [select-bank-accounts-account-type-autofill-parity-audit.md](legacy-parity/select-bank-accounts-account-type-autofill-parity-audit.md) | Settings · Login Credentials → "Select bank accounts" registration popup · Account-Type auto-fill parity; the mapping seed needs a baseline agreed with the PO before it ships as a manual DBA script · active |
| [smart-organizer-verification-2026-06-06.md](legacy-parity/smart-organizer-verification-2026-06-06.md) | Banking · Smart Organizer · R11 verification |
| [test-coverage-audit.md](legacy-parity/test-coverage-audit.md) | Refactor · whole suite (dispatch in depth) · **Test-coverage audit** (2026-07-29) of the whole suite with the firm-banking dispatch subsystem audited in depth. Post-fix execution: 269 suites · 2171 tests · 0 failures. Closes coverage gaps **G-2** and **G-8**; defines the D-1/D-2/D-3 defects resolved in the companion record above. |
| [transaction-history-confirm-legacy-audit.md](legacy-parity/transaction-history-confirm-legacy-audit.md) | Banking · Transaction History (VND + FCY) → **Confirm** an unconfirmed bank transaction · Root cause identified, minimal backend fix applied, regression tests added · active |
| [transaction-history-fcy-parity-audit.md](legacy-parity/transaction-history-fcy-parity-audit.md) | Banking · Transaction History (FCY) · Parity |
| [transaction-history-vnd-parity-audit.md](legacy-parity/transaction-history-vnd-parity-audit.md) | Banking · Transaction History (VND) · Parity |
| [van-center-open-questions-verification-2026-08-01.md](legacy-parity/van-center-open-questions-verification-2026-08-01.md) | Payment · WeCMS Batch (firm-banking dispatch) · **Legacy verification** (2026-08-01) of the seven-row "Needs VAN Center" summary against legacy source + the live JEX DEV DB. **Four rows were wrong** — callback-RC semantics (legacy API2 answers non-`0000` on five *non-throwing* early returns), fixed-length charset ("moot by construction" is false — `rcptUseAddr` and the ERP path are unguarded), retry (legacy **did** poll-retry the 00302 leg; our no-retry is the K9 divergence), operating hours (matches legacy, diverges from the vendor). Retitles the section to **two open vendor questions (§10 q1, §10 q6), neither blocking SIT**; records the **01301 vs 01302 unmatched-callback asymmetry** and defers the 01302 `0000`→`G999` flip with a decision sequence. One code change: the 01302 unmatched-callback ops alert (signal only). |
| [vas-account.md](legacy-parity/vas-account.md) | Settings · VAS Account · End-to-end refactor audit · ⚠️ dated record — every `V<n>` is **pre-rebaseline** and preserved as written · historical |
| [vendor-payment-excel-download-smoke-test.md](legacy-parity/vendor-payment-excel-download-smoke-test.md) | Payment · Vendor Payment Excel **Download** (22-column template) · Pre-SIT manual smoke-test checklist — implemented; verification pending on live data · active |
| [vendor-payment-excel-upload-audit.md](legacy-parity/vendor-payment-excel-upload-audit.md) | Payment · Vendor · Excel Upload / Refresh · Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [wecms-batch-business-parity-audit-2026-07-29.md](legacy-parity/wecms-batch-business-parity-audit-2026-07-29.md) | Payment · WeCMS Batch (firm-banking dispatch) · **Business-parity** audit (2026-07-29) of the 4 legacy payment batch programs + the 2 VAN callback endpoints they depend on. Frame is business parity, **not** architectural — the four-to-one consolidation, event-driven trigger model, and direct VAN API calls are Mr. Kang-approved design changes and are not reported as defects. Findings **H-1/H-2 · M-1…M-6 · L-1…L-10** + **R-1**. |
| [wecms-batch-full-legacy-vs-refactor-final-gap-audit.md](legacy-parity/wecms-batch-full-legacy-vs-refactor-final-gap-audit.md) | Payment · Firm-Banking Batch · Full dispatch gap audit |
| [wecms-batch-refactor-parity-audit-2026-07-21.md](legacy-parity/wecms-batch-refactor-parity-audit-2026-07-21.md) | Payment · WeCMS Batch (firm-banking dispatch) · Full legacy-vs-refactor parity audit (BATCH1/2, NAPAS realtime+204, result-check, API1/API2 callbacks, support/crypto/codec, data model). Byte layout + crypto + hand-off + 6 dated updates MATCH; **7 HIGH / 12 MED** pre-activation parity gaps (TNO≠TRAN_SEQ, callback ack envelope, sibling fail-stop, 0001 loop, response VV, decimal amount) + **R-1** reliability enhancement (PROC_STAT='9' stranding — event-driven TO-BE is approved, not a parity gap). All dispatch flags OFF by default — latent, not live. |
| [wecms-batch-tobe-process-audit.md](legacy-parity/wecms-batch-tobe-process-audit.md) | Payment · Firm-Banking Batch · To-be process audit |

---

## Batch Audits — [`batch/`](batch/)

WeCMS Batch parity, boundary-rule and ownership audits.

| File | Notes |
|---|---|
| [batch-schedule-operating-hours-audit.md](batch/batch-schedule-operating-hours-audit.md) | Batch · Operating-hours gate (`TB_BATCH_SCHEDULE_INFO` / `_DTL`) · Audit **plus implementation record** — `OperatingHoursService` is wired into realtime and bulk dispatch, flag-gated OFF, no schema change. ⚠️ **the schedule tables must be seeded before enabling dispatch**: an empty INFO table resolves to `NO_CRITERIA` and blocks every dispatch · active |
| [final-four-to-one-batch-architecture-correction.md](batch/final-four-to-one-batch-architecture-correction.md) | Batch · Four-to-one batch architecture correction and removal, acting on Mr. Kang's already-approved K9 reply (**no new decision was requested or required**); no scheduler enabled, no SQL executed, no live DB touched · active |
| [legacy-batch-boundary-rule-sweep-2026-07-31.md](batch/legacy-batch-boundary-rule-sweep-2026-07-31.md) | Batch · Boundary-rule sweep over all six legacy firm-banking dispatch programs — every conditional, substring, wrap, clamp and silent skip classified as **ported / deliberate divergence / legacy defect (do not port)** · active |
| [legacy-batch2-ownership-verification.md](batch/legacy-batch2-ownership-verification.md) | Batch · Ownership and coexistence check on legacy Batch 2 — does enabling the Spring `00302` retry actually need fresh approval from Mr. Kang? An evidence-first re-read of `WECMS_BATCH DEV/**`; the prior report's condition was deliberately **not** carried forward unexamined · active |
| [operation-status-inquiry-batch-legacy-audit.md](batch/operation-status-inquiry-batch-legacy-audit.md) | Batch · Legacy audit of the two scheduled tasks `OPRT_0001_001` / `OPRT_0010_001` ("Batch Operation Status Infor") · **legacy audit only, no code modified** · active |
| [operation-status-inquiry-batch-refactor-plan.md](batch/operation-status-inquiry-batch-refactor-plan.md) | Batch · Refactor implementation plan for those two Operation-Status batch tasks, following the flag-gated `ExchangeRateBatchScheduler` pattern · **PLAN ONLY — no code written** · active |
| [wecms-batch-independent-audit-2026-07-30.md](batch/wecms-batch-independent-audit-2026-07-30.md) | Batch · Consolidated independent audit of the JEX SimpleBatch → Spring `domain/payment/dispatch` refactor. Baseline state recorded: not deployed, legacy JEX owns PROD, all three schedulers on Spring's never-fire marker, both dispatch flags false, SFTP disabled, mock EFS client, **no live egress has occurred** · active |

---

## Deployment Readiness Audits — [`deployment/`](deployment/)

Readiness and flag-verification audits. Operational run-books live in [`../deployment/`](../deployment/).

| File | Notes |
|---|---|
| [deployment-readiness-2026-07-29.md](deployment/deployment-readiness-2026-07-29.md) | ✅ **CANONICAL** deployment-readiness audit — a full re-audit from source. Supersedes the 07-28 record (preserved separately; the two disagree on some items and each remains valid for its own date) · active |
| [dispatch-repository-query-reverification.md](deployment/dispatch-repository-query-reverification.md) | Payment Dispatch · Every query method on the dispatch rails (13 repositories plus `DispatchCountRepository`) re-read against its legacy IDO XML source of truth, then against its live callers · active |
| [payment-dispatch-flags-deployment-readiness-audit.md](deployment/payment-dispatch-flags-deployment-readiness-audit.md) | Payment Dispatch · Every dispatch feature flag, scheduler flag, cron property, environment variable and activation condition, against legacy WeCMS and legacy WeCMS Batch · **AUDIT ONLY — no code, YAML, cron, DB row or flag modified; no dispatch, EFS or bank call executed** · active |
| [payment-dispatch-operating-hours-flow.md](deployment/payment-dispatch-operating-hours-flow.md) | Payment Dispatch · Static trace of the controller → service → executor → registrar → operating-hours-gate path for all 5 payment types (revision 6, synchronised with `HEAD`; findings F-1…F-5 all closed) · **no code modified, no DB writes** · active |
| [scheduler-final-readiness.md](deployment/scheduler-final-readiness.md) | Scheduler · SIT / UAT final readiness · ⚠️ **superseded in part (2026-07-30)** — every statement about *safe inline defaults* predates the bare-placeholder migration that stripped all 169 inline defaults from the five config files · historical |

---

## Historical One-Off Audits — [`historical/`](historical/)

Point-in-time records preserved unchanged. Findings remain valid **for their date**; numbering and code references may be stale by design.

| File | Notes |
|---|---|
| [account-inquiry-vnd-implementation-verification-2026-06-15.md](historical/account-inquiry-vnd-implementation-verification-2026-06-15.md) | Account Inquiry VND AI-01…AI-06 implementation verification · historical (closed) |
| [approval-box-parity-fixes-2026-06.md](historical/approval-box-parity-fixes-2026-06.md) | Payment · Approval Box · Record of the parity fixes and explicit deferrals that landed **after** the original Phase-1 plan, superseding that plan's "Deferred — Phase 2/3/4" rows · historical |
| [db-parity-audit-2026-04-30.md](historical/db-parity-audit-2026-04-30.md) | Local DB vs JEX DEV · Full column / constraint / index parity audit closing Sprint 1 · ⚠️ dated record — every `V<n>` is **pre-rebaseline** and preserved as written · historical |
| [deployment-readiness-2026-07-28.md](historical/deployment-readiness-2026-07-28.md) | ⚠️ **SUPERSEDED** — batch deployment-readiness audit from the day before the canonical 07-29 re-audit. Findings are preserved unchanged and remain valid **for 2026-07-28**; do not read it as the current position · historical |
| [field-casing-parity-audit-2026-06-06.md](historical/field-casing-parity-audit-2026-06-06.md) | UPPER→lowercase snake_case wire migration inventory · historical (complete) |
| [firm-banking-history-review-2026-05-08.md](historical/firm-banking-history-review-2026-05-08.md) | Settings · Firm Banking History · Independent review of Phases 2–3d (backend, frontend, docs and migrations) by a reviewer who did not author them · historical |
| [firm-banking-settings-review-2026-05-09.md](historical/firm-banking-settings-review-2026-05-09.md) | Settings · Firm Banking Settings · Independent Phase 2/3 review of the read-only Path-A slice (List / Detail / Audit-read) · ⚠️ dated record — `V<n>` numbers are **pre-rebaseline** · historical |
| [internal-transfer-upload-withdrawal-account-listing-audit-2026-06-10.md](historical/internal-transfer-upload-withdrawal-account-listing-audit-2026-06-10.md) | Internal-transfer withdrawal-account listing audit · historical (complete) |
| [internal-transfer-withdrawal-account-remediation-plan-2026-06-10.md](historical/internal-transfer-withdrawal-account-remediation-plan-2026-06-10.md) | …remediation plan · historical (implemented) |
| [legacy-vs-refactor-properties-config-audit.md](historical/legacy-vs-refactor-properties-config-audit.md) | Legacy vs refactor config/properties audit · historical (complete) |
| [nxisas-vcb-spike.md](historical/nxisas-vcb-spike.md) | ⚠️ **SUPERSEDED** — NXiSAS/VCB spike describing an **AES-GCM cert cipher** keyed by a `wecms.crypto.cert-key` config slot. Both were **retired**; the live cipher is legacy pgcrypto AES-256-CBC. Do not treat as current · historical |
| [properties-usage-audit.md](historical/properties-usage-audit.md) | `application*.yml` / properties usage audit · historical (complete) |
| [scheduled-page-parity-2026-06-08.md](historical/scheduled-page-parity-2026-06-08.md) | Payment scheduled-page parity pass · historical (complete) |
| [special-char-validation-parity-audit-2026-06-10.md](historical/special-char-validation-parity-audit-2026-06-10.md) | Blocked-chars validation parity · historical (complete) |
| [vcb-running-balance-fix-verification-2026-06-15.md](historical/vcb-running-balance-fix-verification-2026-06-15.md) | Transaction-History TH-01 (Vietcombank running-balance) fix verification · historical (closed) |
| [transaction-history-fcy-fix-verification.md](historical/transaction-history-fcy-fix-verification.md) | Transaction-History FCY fix verification · historical (closed) |
| [transfer-history-audit-2026-05-20.md](historical/transfer-history-audit-2026-05-20.md) | Banking · Transfer History · Feature parity and compliance audit (backend + frontend), status *final* · historical |
| [transfer-result-sit-readiness-2026-06-16.md](historical/transfer-result-sit-readiness-2026-06-16.md) | Transfer Result F1/F2/F3 SIT-readiness · historical (complete) |
| [vendor-expense-payroll-withdrawal-account-remediation-implementation-2026-06-12.md](historical/vendor-expense-payroll-withdrawal-account-remediation-implementation-2026-06-12.md) | …implementation record · historical (complete) |
| [vendor-expense-payroll-withdrawal-account-remediation-plan-2026-06-10.md](historical/vendor-expense-payroll-withdrawal-account-remediation-plan-2026-06-10.md) | Vendor/Expense/Payroll withdrawal-account remediation plan · historical (implemented) |
| [wb-get-next-val-centralization-2026-06-09.md](historical/wb-get-next-val-centralization-2026-06-09.md) | Key-gen funnel centralization record · historical (complete) |

---

**Total: 136 audit documents** across 6 categories.

> Per-file descriptions marked `—` were not present in the pre-2026-08-17 index (37 files on disk were
> never indexed). They are listed here so the index is complete; descriptions can be filled in over time.
