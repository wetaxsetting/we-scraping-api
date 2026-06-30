# Audits — Index

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

## 1. Governance & Documentation

The cross-cutting documentation-quality program (structure, truth, drift, remediation, governance).

| File | Title | Purpose | Status |
|---|---|---|---|
| [documentation-truth-audit.md](documentation-truth-audit.md) | Documentation Truth Audit | Executive summary of the code-vs-docs audit (6 modules × 5 dimensions); module scorecards + drift counts | historical (2026-06-23 snapshot; reference) |
| [documentation-remediation-plan.md](documentation-remediation-plan.md) | Remediation Plan | P1–P4 fix plan + DC-1/DC-2 deployment-context items; the living tracker of doc fixes | active |
| [documentation-remediation-closure-report.md](documentation-remediation-closure-report.md) | Remediation Closure Report | Project timeline, what was fixed, link-health history, post-closure notes (DB-folder + docs-folder removals) | active |
| [documentation-governance-roadmap.md](documentation-governance-roadmap.md) | Governance Roadmap | Ongoing operating model — ownership, maintenance workflow, audit schedule, drift prevention, legacy-DB governance | active |
| [documentation-folder-rationalization.md](documentation-folder-rationalization.md) | Folder Rationalization | Pre-deletion impact audit of `archive/`/`planning/`/`migration/` | **superseded** (folders later removed by decision) |
| [api-contract-drift.md](api-contract-drift.md) | API Contract Drift | Interface-spec vs controllers/DTOs — undocumented/changed endpoints & fields | active |
| [authorization-doc-drift.md](authorization-doc-drift.md) | Authorization Drift | `@PreAuthorize`/`@menuAuthz` vs documented gates; unauth-endpoint findings | active |
| [database-doc-drift.md](database-doc-drift.md) | Database Mapping Drift | `database-mapping.md` vs entities/SQL/migrations; version-citation & view findings | active |

## 2. Module Audit Triplets

One triplet per module — **`-docs-audit`** (doc-vs-code reconciliation) · **`-gap-analysis`** (parity matrix:
MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) · **`-source-index`** (code ↔ docs ↔ tests map).
All **active**.

| Module | docs-audit | gap-analysis | source-index |
|---|---|---|---|
| **Banking** | [banking-docs-audit.md](banking-docs-audit.md) | [banking-gap-analysis.md](banking-gap-analysis.md) | [banking-source-index.md](banking-source-index.md) |
| **Payment** | [payment-docs-audit.md](payment-docs-audit.md) | [payment-gap-analysis.md](payment-gap-analysis.md) | [payment-source-index.md](payment-source-index.md) |
| **Settings** | [settings-docs-audit.md](settings-docs-audit.md) | [settings-gap-analysis.md](settings-gap-analysis.md) | [settings-source-index.md](settings-source-index.md) |
| **Common** | [common-docs-audit.md](common-docs-audit.md) | [common-gap-analysis.md](common-gap-analysis.md) | [common-source-index.md](common-source-index.md) |
| **Add-on Service** | [addon-service-docs-audit.md](addon-service-docs-audit.md) | [addon-service-gap-analysis.md](addon-service-gap-analysis.md) | [addon-service-source-index.md](addon-service-source-index.md) |
| **Auth** | [auth-docs-audit.md](auth-docs-audit.md) | [auth-gap-analysis.md](auth-gap-analysis.md) | [auth-source-index.md](auth-source-index.md) |
| **CLAUDE.md** *(governance, not a feature)* | [claude-docs-audit.md](claude-docs-audit.md) | [claude-gap-analysis.md](claude-gap-analysis.md) | [claude-source-index.md](claude-source-index.md) |

> The **Auth** triplet was relocated here (2026-06-23) from `features/auth/` so all module triplets live together.
> The **CLAUDE.md** triplet reconciles the `CLAUDE.md` rule files against the code — it is a governance artifact,
> not a WeCMS feature.

## 3. Feature Verification & Parity Audits

Per-feature legacy-vs-refactor parity / verification records — the current parity reference for each feature.
All **active** unless noted.

| File | Feature | Purpose |
|---|---|---|
| [account-inquiry-fcy-parity-audit.md](account-inquiry-fcy-parity-audit.md) | Banking · Account Inquiry (FCY) | Legacy-vs-refactor parity |
| [transaction-history-vnd-parity-audit.md](transaction-history-vnd-parity-audit.md) | Banking · Transaction History (VND) | Parity |
| [transaction-history-fcy-parity-audit.md](transaction-history-fcy-parity-audit.md) | Banking · Transaction History (FCY) | Parity |
| [chart-of-accounts-final-parity-audit.md](chart-of-accounts-final-parity-audit.md) | Settings · Chart of Accounts | Final parity |
| [purpose-refactor-parity-audit.md](purpose-refactor-parity-audit.md) | Settings · Purpose | Parity |
| [login-credentials-legacy-vs-refactor-audit.md](login-credentials-legacy-vs-refactor-audit.md) | Settings · Login Credentials | Bank/GDT cert + cipher parity (§7) |
| [login-legacy-compatibility-2026-06-10.md](login-legacy-compatibility-2026-06-10.md) | Auth · Login | Password (SHA-256) coexistence + account-creation parity |
| [operation-status-inquiry-parity.md](operation-status-inquiry-parity.md) | Add-on · Operation Status | Parity |
| [sales-expense-einvoices-scraping-parity.md](sales-expense-einvoices-scraping-parity.md) | Add-on · e-Invoices | Scraping/coexistence parity |
| [card-transactions-legacy-vs-refactor-audit.md](card-transactions-legacy-vs-refactor-audit.md) | Payment · Corporate Card | Legacy-vs-refactor audit |
| [notification-popup-legacy-vs-refactor-analysis.md](notification-popup-legacy-vs-refactor-analysis.md) | Common · Notifications | Popup behaviour analysis |
| [notification-approval-detail-popup-parity-audit.md](notification-approval-detail-popup-parity-audit.md) | Common · Notifications | Approval-detail popup parity |
| [wecms-batch-full-legacy-vs-refactor-final-gap-audit.md](wecms-batch-full-legacy-vs-refactor-final-gap-audit.md) | Payment · Firm-Banking Batch | Full dispatch gap audit |
| [wecms-batch-tobe-process-audit.md](wecms-batch-tobe-process-audit.md) | Payment · Firm-Banking Batch | To-be process audit |
| [smart-organizer-r11-verification.md](smart-organizer-r11-verification.md) | Banking · Smart Organizer | R11 verification |
| [internal-transfer-excel-upload-audit.md](internal-transfer-excel-upload-audit.md) | Payment · Internal Transfer · Excel Upload | Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [vendor-payment-excel-upload-audit.md](vendor-payment-excel-upload-audit.md) | Payment · Vendor · Excel Upload / Refresh | Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [expense-payment-excel-upload-audit.md](expense-payment-excel-upload-audit.md) | Payment · Expense · Excel Upload / Refresh | Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [payroll-payment-excel-upload-audit.md](payroll-payment-excel-upload-audit.md) | Payment · Payroll · Excel Upload / Refresh | Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |
| [overseas-payment-excel-upload-audit.md](overseas-payment-excel-upload-audit.md) | Payment · Overseas · Excel Upload / Refresh | Excel-upload parity + Recipient Name / Lookup Recipient parity (§ added 2026-06-30) |

## 4. Historical One-Off Audits

Dated, point-in-time records of a completed fix / verification / remediation. **Historical** — findings preserved
unchanged; for a feature's *current* state see its `features/<…>/refactor-gap-analysis.md` or the §2 triplet.

| File | Topic | Status |
|---|---|---|
| [field-casing-parity-audit-2026-06-06.md](field-casing-parity-audit-2026-06-06.md) | UPPER→lowercase snake_case wire migration inventory | historical (complete) |
| [wb-get-next-val-centralization-2026-06-09.md](wb-get-next-val-centralization-2026-06-09.md) | Key-gen funnel centralization record | historical (complete) |
| [scheduled-page-parity-2026-06-08.md](scheduled-page-parity-2026-06-08.md) | Payment scheduled-page parity pass | historical (complete) |
| [special-char-validation-parity-audit-2026-06-10.md](special-char-validation-parity-audit-2026-06-10.md) | Blocked-chars validation parity | historical (complete) |
| [internal-transfer-upload-withdrawal-account-listing-audit-2026-06-10.md](internal-transfer-upload-withdrawal-account-listing-audit-2026-06-10.md) | Internal-transfer withdrawal-account listing audit | historical (complete) |
| [internal-transfer-withdrawal-account-remediation-plan-2026-06-10.md](internal-transfer-withdrawal-account-remediation-plan-2026-06-10.md) | …remediation plan | historical (implemented) |
| [vendor-expense-payroll-withdrawal-account-remediation-plan-2026-06-10.md](vendor-expense-payroll-withdrawal-account-remediation-plan-2026-06-10.md) | Vendor/Expense/Payroll withdrawal-account remediation plan | historical (implemented) |
| [vendor-expense-payroll-withdrawal-account-remediation-implementation-2026-06-12.md](vendor-expense-payroll-withdrawal-account-remediation-implementation-2026-06-12.md) | …implementation record | historical (complete) |
| [transfer-result-f1-f2-f3-sit-readiness-2026-06-16.md](transfer-result-f1-f2-f3-sit-readiness-2026-06-16.md) | Transfer Result F1/F2/F3 SIT-readiness | historical (complete) |
| [th-01-vcb-running-balance-fix-verification.md](th-01-vcb-running-balance-fix-verification.md) | Transaction-History TH-01 (Vietcombank running-balance) fix verification | historical (closed) |
| [transaction-history-fcy-fix-verification.md](transaction-history-fcy-fix-verification.md) | Transaction-History FCY fix verification | historical (closed) |
| [account-inquiry-vnd-ai01-ai06-implementation-verification.md](account-inquiry-vnd-ai01-ai06-implementation-verification.md) | Account Inquiry VND AI-01…AI-06 implementation verification | historical (closed) |
| [properties-usage-audit.md](properties-usage-audit.md) | `application*.yml` / properties usage audit | historical (complete) |
| [legacy-vs-refactor-properties-config-audit.md](legacy-vs-refactor-properties-config-audit.md) | Legacy vs refactor config/properties audit | historical (complete) |

---

*Filenames are intentionally stable and dated — do not rename or delete (audit history). Add new audits under the
matching section above and keep this index in sync (see the [governance roadmap](documentation-governance-roadmap.md)).*
