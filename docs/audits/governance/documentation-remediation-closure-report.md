# Documentation Remediation — Closure Report

> **Status: CLOSED (documentation workstream).** This report closes the multi-phase WeCMS refactor
> documentation governance + truth + remediation effort. As of 2026-06-23 the doc-to-doc link graph is
> **100% healthy** and every in-scope documentation defect is resolved or reclassified. Remaining open
> items are **non-documentation** (application code, security wiring, DBA provisioning) and are listed in §6.
>
> Companion artifacts: [documentation-governance-roadmap.md](documentation-governance-roadmap.md) ·
> [documentation-remediation-plan.md](documentation-remediation-plan.md) · [documentation-truth-audit.md](documentation-truth-audit.md) ·
> [authorization-doc-drift.md](authorization-doc-drift.md) · [database-doc-drift.md](database-doc-drift.md) · [api-contract-drift.md](api-contract-drift.md).

---

## 1. Project timeline

| Phase | Focus | Outcome |
|---|---|---|
| **Phase 1** | Documentation structure audit (`wecms-api/docs` + `wecms-web/docs`) | Found banking link drift, misplaced audit triplets, missing Settings triplet, ~173 broken doc-to-doc links. Verdict: *partially correct*. |
| **Phase 2** | Structure governance fix | Repaired banking link drift (77 rewrites/28 files); moved banking+payment audit triplets into `audits/` with redirect stubs; created the Settings triplet; fixed README refs. Doc-to-doc health → **99.58%**. |
| **Phase 3** | Code-vs-documentation truth audit (6 modules × 5 dimensions, fan-out investigators) | Produced [documentation-truth-audit.md](documentation-truth-audit.md) + the three drift reports. Found feature docs largely code-faithful; concentrated drift in Banking Daily Financial CMC, common migration-version citations, stale code Javadocs, 1 missing audit file. |
| **Phase 3A** | Remediation **plan** | Authored [documentation-remediation-plan.md](documentation-remediation-plan.md) (P1–P4 + quick wins + intentionally-unchanged + suspected). |
| **Phase 3B** | Execute P1 | Corrected Banking Daily Financial CMC docs (11 files) + the common migration-version citations (V242/V243→V137-139, V155→V140; 15 files incl. CLAUDE.md ledger). 0 links broken. |
| **Phase 3C** | Verification + deployment-context alignment | Verified P1-1; **reclassified P1-2** to *APPROVED DEVIATION / DEPLOYMENT CONTEXT NOTE* (DC-1) under the legacy-DB-reuse model; added the navigation-seed DBA item (DC-2). |
| **P2-1** | Missing audit file | Decided **Option B** (the 2026-06-10 doc audits a *different* feature) → created [login-credentials-legacy-vs-refactor-audit.md](../legacy-parity/login-credentials-legacy-vs-refactor-audit.md); 3 dangling refs resolved. |
| **Phase 4** | Governance roadmap | Authored [documentation-governance-roadmap.md](documentation-governance-roadmap.md) (ownership · workflow · audit schedule · source-code-index · gap lifecycle · drift prevention · legacy-DB governance). |
| **Phase 4A** | Final documentation cleanup | The six residual cleanup items below → **doc-to-doc link health 100.00%**. |

## 2. Audits completed

- **Structure audit** (Phase 1) — both docs trees vs the documented structure rules.
- **Coverage audit** (Phase 1–2) — every feature leaf carries the full 9-doc (API) / 6-doc (WEB) set; **zero missing/empty leaf docs**.
- **Truth audit** (Phase 3) — 6 modules (auth · banking · payment · settings · common · addon-service) × 5 dimensions (accuracy · coverage · authorization · database mapping · API contract), code-as-source-of-truth, every finding file-referenced + CONFIRMED/SUSPECTED tagged. Reports: [documentation-truth-audit.md](documentation-truth-audit.md) + [authorization-doc-drift.md](authorization-doc-drift.md) + [database-doc-drift.md](database-doc-drift.md) + [api-contract-drift.md](api-contract-drift.md).
- **Module audit-first triplets** — all five business modules now carry `<module>-docs-audit` / `-gap-analysis` / `-source-index` under `audits/` (Settings triplet created in Phase 2).
- **Feature audit** — `login-credentials-legacy-vs-refactor-audit.md` authored (P2-1).

## 3. Governance improvements

- **Single source of truth = implementation code** (legacy *database* excepted — its truth is the live legacy/PROD schema/functions/seed).
- **Audit-first structure standardised** — module triplets co-located in `audits/`; redirect stubs preserve inbound links.
- **Deployment-context model codified** — legacy-DB reuse; Flyway is *not* the source of truth for legacy objects; a missing/renumbered migration file is **not** automatically a defect (DC-1). Migration-renumber awareness documented; the pre-renumber archive (`db/archive/`) and the manual-seed folder (`db/manual/`) were subsequently **removed 2026-06-23 (approved decision)** — only `db/migration/` remains.
- **Governance roadmap published** — ownership matrix, same-MR maintenance workflow, three-tier audit schedule, source-code-index strategy, gap-analysis lifecycle vocabulary (MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING), drift-prevention checklist + a CI-automation backlog (link gate, stale-citation linter, coverage check, authz↔doc cross-check).
- **Audit history preserved throughout** — superseded content is dated + bannered, never deleted (Strategy-A/B, migration renumber, Daily Cash recompute).

## 4. Link health improvements

| Milestone | Doc-to-doc link health | Doc-to-doc broken |
|---|---|---|
| Phase 1 (baseline) | ~96.8% | ~168 |
| Phase 2 (structure fix) | 99.58% | 22 |
| Phase 3B/3C | ~99.6% | 21 |
| After P2-1 | 99.6% | 18 |
| **Phase 4A (final)** | **100.00%** | **0** |

Validated with two independent checkers (a strict Markdown-link parser requiring full bracket-then-paren syntax, plus a looser target-only parser) — both report **0 broken doc-to-doc links** over **5,538** internal doc links. **0 broken links were introduced** in any remediation phase.

## 5. Remediation completed

**Phase 4A — the six residual cleanup items (20 files changed):**

| # | Item | Action | Files |
|---|---|---|---|
| 1 | Daily Financial citation depth | `../../../../src` → `../../../../../src` (off-by-one to reach `wecms-api/src`) | `daily-financial/parity-verification-report.md` (×12), `runtime-data-source-check.md` (×3) |
| 2 | WEB README depth/path | added the missing `../` to the cross-tree + page-mapping links | `wecms-web/.../internal-transfer-vnd/README.md`, `wecms-web/.../reports/daily-cash/README.md` |
| 3 | Archive malformed link | reworded the `resultArray, idx` copy-paste artifact (2 archived files) so it is no longer parsed as a link | `archive/features/sales-and-expense-einvoices.md`, `archive/reports/link-stabilization-report-2026-06-01.md` |
| 4 | Daily Cash residual stale wording | added a dated correction banner (current source = `rept_0900_001_rec1..rec4` via `DailyCashReportFunctionRepository`, per `DailyCashReportService.java:24-75`; recompute path superseded) | 10 `daily-cash/*.md` docs |
| 5 | Contacts wording | **verified already-correct** — contacts docs use `WBE_CONTACTS_NOT_FOUND` throughout; no `WBE2040` remains (the AC-1 reference was the `lib/api/contacts.ts` Javadoc = code, out of scope) | — (no change) |
| 6 | HS mock-fallback wording | corrected HS "mock fallback" claims → "**no** mock fallback (empty state on no data)", per `DailyFinancialHsClient.tsx:20` ("No mock fallback"); CMC mock-fallback statements left intact | `wecms-web/.../daily-financial/{README.md, component-index.md, screen-flow.md, test-scenarios.md}` |

**Cumulative across all phases:** banking link drift (Phase 2), audit relocation + Settings triplet (Phase 2), Banking Daily Financial CMC truth (Phase 3B), common migration versions (Phase 3B), P1-2 reclassification (Phase 3C), missing login-credentials audit (P2-1), and the Phase-4A cleanup — **all documentation defects resolved or reclassified.**

## 6. Remaining non-documentation items

These are **out of the documentation workstream** (application code, security wiring, DBA provisioning). They are tracked in [documentation-remediation-plan.md](documentation-remediation-plan.md); none is a documentation defect.

| Ref | Item | Type | Owner |
|---|---|---|---|
| P2-2 / AZ-6 | Unauthenticated `/payment/dispatch/callbacks/*` — wire `CallbackIpWhitelistFilter` | **Code (security)** | backend + PO |
| P2-3 | Stale crypto/security **Javadoc** (SecurityConfig "BCrypt", JwtAuthenticationFilter "advisory", GdtCertInfo/BankCertInfo "AES-GCM") | **Code (Javadoc)** | dev |
| P3-5 | `wecms-api/CLAUDE.md` endpoint counts (chart-of-accounts 9≠6, contacts 17≠6); contacts "global audit aspect" that doesn't exist | **CLAUDE.md / code** | lead + dev |
| P3-6 / P4-4 | Stale `@Subselect`-as-"view" + authz-"deferred" + key-gen Javadocs (`PurposeInUseRepository`, `FirmBankingSettingViewRepository`, `CardTransactionsController`, `PurposeSettingsController`, `VasAccountController`, `BusiVasAcctInfm`, `BusiUsagSetInfm`) | **Code (Javadoc)** | dev |
| P4-2 / P4-3 | `wecms-web/CLAUDE.md` route-guard description; `wecms-api/CLAUDE.md` `WBE1009`→`WBE1401` narration | **CLAUDE.md** | lead |
| DC-2 | Navigation seed (and other refactor-only objects) has no in-repo source after the `db/archive/` removal — **expected, not a defect**; clean-environment provisioning is out of repo scope (DBA-approved procedures) | **DBA / deployment** | DBA + PO |
| (linkcheck CODE/OTHER) | 112 doc→**code** citations (`src/main`/`components`/`app` `path:line`) with depth/anchor drift in non-Daily-Financial features, + 43 parser false-positives on `app/(dashboard)` paths | **Doc→code citations** (optional future pass; not doc-graph defects) | dev (optional) |

> **Explicitly NOT touched in Phase 4A** (per instruction): application code, Javadocs, `CLAUDE.md`, the callback security implementation, and the navigation-seed mechanism.

## 7. Maintenance-mode recommendations

The documentation is now in **maintenance mode**. To keep it there, adopt the roadmap's operating model:

1. **Same-MR rule** — every code change updates its feature's docs in the same MR (full-stack ownership). No "doc follow-up later".
2. **Run the §6 maintenance checklist** from [documentation-governance-roadmap.md](documentation-governance-roadmap.md) on every doc/code MR.
3. **Automate the floor (highest-value backlog):** a **CI link-validation gate** (fail on a new broken doc-to-doc link — the Node walker used here is ready to promote), a **stale-citation linter** (flag cited `V<n>__*.sql` / `path:line` that no longer exist on disk), and a **coverage check** (9/6-doc set present, none empty).
4. **Per-sprint module audit hygiene** — refresh the touched module's audit triplet + re-run its gap-analysis vs code; regenerate `source-code-index` rather than hand-patching.
5. **Trigger-based audits** on the historically high-drift events: data-source pivots, authz changes, DTO casing flips, migration renumbers.
6. **Quarterly / pre-release cross-tree truth audit** + full link validation + `CLAUDE.md` reconciliation.
7. **Close the residual code-tree items (§6)** in a single dev MR (stale Javadocs + CLAUDE.md counts) and route P2-2 (callback security) + DC-2 (nav-seed provisioning) to their owners — these retire the last truth-liabilities the docs already describe correctly.
8. **Keep classifying DB items by the legacy-reuse model** (DC-class), never a Flyway-first model.

## Closure summary

The WeCMS refactor documentation went from *partially correct with ~168 broken doc-to-doc links and multiple
truth defects* to **structurally consistent, code-faithful, 100% doc-to-doc link-healthy, and governed by a
published operating model.** Coverage is complete (full doc sets, zero empty), the audit-first structure is
uniform across all five modules, DB/version items are classified by the project's real legacy-reuse deployment
architecture, and audit history is preserved throughout. All in-scope documentation remediation is **complete**;
the only open items are application-code, security-wiring, and DBA-provisioning tasks that the documentation now
describes accurately. The documentation workstream is **closed and in maintenance mode.**

---

## Post-closure note — DB folder removal reconciliation (2026-06-23)

After closure, an approved project decision **removed `db/manual/` and `db/archive/`** (the latter being the
pre-renumber migration history, formerly `migration_archive_20260609/`) from `src/main/resources/db/`. Only
**`db/migration/`** remains. This reflects the deployment model: UAT/PROD **reuse the existing legacy database
schema, functions, and data**; the project does **not** use manual-seed SQL or archived-migration SQL.

A maintenance-mode documentation reconciliation pass updated all references to the removed folders/files
(`db/manual`, `db/archive`, `migration_archive_20260609`, `manual_seed_*.sql`, archived `V*.sql`) across 12 docs —
replacing each with an explanatory removal note while **preserving the historical record** (e.g. the navigation
seed shipped as the now-removed `V243`; the `V242/V243/V155` numbers were the pre-renumber scheme). Governance
docs (this report, the remediation plan DC-1/DC-2 + deployment premise, and the governance roadmap §7) now state
the removal explicitly. The open **DC-2** item is reframed accordingly: the navigation seed (and the ~75 refactor-only
objects the archive once held) have **no in-repo source**, which is **expected** — the repository is not a complete
archive of legacy DB objects. *Clean-environment database provisioning is outside the scope of this repository and must follow DBA-approved procedures.* Doc-to-doc link health
remained **100%** after the pass. *(The physical folder deletion itself is applied outside this docs pass.)*

---

## Post-closure note — docs-folder removal reconciliation (2026-06-23)

A second approved project decision removed four **documentation** folders (distinct from the `db/` SQL folders
above): `wecms-api/docs/{archive, migration, planning}` and `wecms-web/docs/archive`. The
[documentation-folder-rationalization.md](documentation-folder-rationalization.md) impact audit had recommended
keeping `planning/` (57 inbound) and retaining `archive/` history; the project chose to remove them anyway — that
decision stands and the rationalization report is marked **superseded**.

A reconciliation pass then fixed every reference: **92 broken doc-to-doc links** (planning 75 / archive 31 /
migration 17, overlapping) — **79 inline references across 56 files** were de-linked and annotated *"removed —
historical `<folder>/` folder deleted by project decision"* (preserving the historical record), and the
navigation READMEs (both roots, `features/settings/README`, both `references/README`) were hand-edited to drop the
deleted-folder rows and state the new structure. **Remaining docs structure** — API: `architecture/ · audits/ ·
features/ · references/ · testing/`; WEB: `architecture/ · bug-reports/ · change-log/ · features/ · references/ ·
testing/`. Migration narratives now live only under `references/migration/`. Doc-to-doc link health was restored
to **100%**. Removal of these folders does **not** indicate missing functionality — it reflects that the repo is
no longer kept as a complete historical archive.
