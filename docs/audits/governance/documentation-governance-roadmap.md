# Documentation Governance Roadmap

> **Purpose.** A forward-looking operating model for keeping the WeCMS refactor documentation
> (`wecms-api/docs` + `wecms-web/docs`) accurate, owned, and drift-free. Distilled from Phases 1–3C
> (structure governance → coverage audit → truth audit → remediation → deployment-context alignment).
> **This is a roadmap, not a fix.** It defines *how* docs are owned and maintained going forward; it does
> not change feature content.
>
> **Founding principle (non-negotiable): implementation code is the single source of truth.** Where docs and
> code disagree, code wins and the doc is corrected — never the reverse. The one exception is the *legacy
> database*, whose source of truth is the **existing legacy/PROD schema, functions, and seed** (see §7).
>
> Companion audit artifacts (the evidentiary basis for this roadmap):
> [documentation-truth-audit.md](documentation-truth-audit.md) · [documentation-remediation-plan.md](documentation-remediation-plan.md) ·
> [authorization-doc-drift.md](authorization-doc-drift.md) · [database-doc-drift.md](database-doc-drift.md) · [api-contract-drift.md](api-contract-drift.md).

---

## 1. Documentation ownership

Docs are owned **by the same person who owns the code they describe** — there is no separate "docs team".
A documentation change ships in the **same MR** as the code change that motivates it (full-stack ownership
rule, already in both `CLAUDE.md` files). The matrix below assigns *accountability*, not gatekeeping.

### Ownership matrix

| Documentation surface | Path | Primary owner | Reviewer / approver |
|---|---|---|---|
| Backend feature docs (9-doc set) | `wecms-api/docs/features/**` | Backend dev (Arthur) | Lead (Sonny) |
| Frontend feature docs (6-doc set) | `wecms-web/docs/features/**` | Frontend dev (Arthur) | Lead (Sonny) |
| API contracts / interface-specs | `wecms-api/docs/features/*/interface-specification.md` + `references/api-spec/` | Backend dev | Lead |
| Database mapping / ERD / inventory | `wecms-api/docs/features/*/database-mapping.md` + `references/database/` | Backend dev | Lead + DBA |
| Audits (module triplets + drift reports) | `wecms-api/docs/audits/**` | Whoever runs the audit | Lead |
| Architecture / cross-cutting | `*/docs/architecture/**` | Lead | PO (Mr. Jung) for scope |
| Migration / traceability | `wecms-api/docs/migration/` + `references/migration/` | Backend dev | Lead |
| Legacy DB governance (inventory, reuse policy) | `references/database/legacy-table-inventory.md` + this roadmap §7 | Lead + DBA | **PO (Mr. Jung)** |
| Deployment-context classifications (DC-*) | `documentation-remediation-plan.md` | Lead | PO + DBA |
| Root `CLAUDE.md` (both trees) | repo roots | Lead | PO |
| Test plans / scenarios | `*/docs/testing/` + `*/docs/features/*/test-scenarios.md` | AI-assisted tester (Mr. Youvita) | Lead |

**Rules.**
- **No orphan docs.** Every doc maps to an owner via the matrix. A doc with no clear owner is a governance defect — assign it before merge.
- **The PO owns *scope* decisions, not prose.** Anything that changes what is in/out of refactor scope (table baseline, dropped features, legacy-vs-refactor deviations) needs PO sign-off; wording does not.
- **The DBA co-owns legacy-DB governance** (§7) — schema/function/seed provisioning is a deployment concern, not a Flyway-file concern.

---

## 2. Maintenance workflow

The lifecycle of a documentation change, keyed to the code change that drives it:

```
code change (feature / fix / refactor)
        │
        ▼
same MR updates the matching doc(s)         ← full-stack ownership rule
        │   • backend DTO/endpoint flip → interface-specification.md + frontend api-consumption.md
        │   • entity/@Column change      → database-mapping.md
        │   • authz annotation change    → functional-specification.md (+ authorization notes)
        │   • new feature screen         → full 9-doc (API) / 6-doc (WEB) set
        ▼
reviewer checks the §6 maintenance checklist
        │
        ▼
link validation (scripted) must pass — 0 new broken intra-doc links
        │
        ▼
merge
```

**Non-negotiables.**
- **Reuse before create.** Walk existing docs before adding a new one (mirrors the code reuse rule). New module-level docs only when a feature genuinely lacks a home.
- **Preserve audit history.** Superseded content is *reframed as historical* (dated "superseded" banner), never silently deleted — this is the pattern used for Banking Daily Financial Strategy-A/B and the migration renumber notes.
- **Slugs, not legacy WSVC ids.** New identifier surfaces use kebab-case slugs; legacy WSVC ids survive only in JEX file:line citations and migration-traceability prose (workspace rule).
- **Feature slugs match across both trees** (`wecms-api` ↔ `wecms-web`) for traceability.

---

## 3. Audit schedule

Three cadences, smallest-to-largest:

| Cadence | Trigger | Scope | Owner | Output |
|---|---|---|---|---|
| **Per-MR (continuous)** | every code MR | the touched feature's docs + link validation | MR author | updated docs in the MR |
| **Per-feature (on completion)** | a feature reaches "implemented" | that feature's `refactor-gap-analysis.md` re-run vs code; coverage check (9/6 docs present) | feature dev | refreshed gap-analysis |
| **Per-module (each sprint / stabilisation pass)** | sprint boundary | the module's audit triplet (`<module>-docs-audit` / `-gap-analysis` / `-source-index`) refreshed vs code | module owner | updated triplet under `audits/` |
| **Cross-tree (quarterly or pre-release)** | release prep / quarter | full truth audit (all 6 modules × 5 dimensions) + full link validation + drift-report refresh | Lead | refreshed `documentation-truth-audit.md` + drift reports |

**Audit-first pattern (standard).** Each of the five business modules (auth · banking · payment · settings ·
common · addon-service) carries a module-level audit triplet under `audits/`:
`<module>-docs-audit.md` (doc-vs-code reconciliation) · `<module>-gap-analysis.md` (parity matrix roll-up) ·
`<module>-source-index.md` (code ↔ docs ↔ tests map). Keep all triplets co-located in `audits/` (not in
`features/`) — convergence completed in Phase 2/3A.

**Trigger-based (not only calendar):** a doc audit is *also* mandatory whenever a data-source pivot, an authz
change, a DTO casing flip, or a migration renumber lands — those are the historically highest-drift events
(see the truth audit's "data-source pivots" and "migration renumber" findings).

---

## 4. `source-code-index` strategy

The `source-code-index.md` per feature (and the module-level `<module>-source-index.md`) is the **code ↔ docs
↔ tests traceability map**. It is the highest-value anti-drift artifact because it pins each doc claim to a
concrete code location.

**Rules.**
- **Cite by path, and by `path:line` for behaviour claims** (e.g. `ReportService.java:147-225`). Line ranges
  drift; treat a stale line number as a *low-severity* find, a stale *path/symbol* as a real defect.
- **The index is regenerated, not hand-patched, at each per-module audit.** Walk the actual
  `domain/**/controller|service|repository`, `entity`, `dto`, and `src/test` trees; do not trust the prior index.
- **Mark unverified claims explicitly.** When a file body was not read in a pass, tag the row `⚠️ UNVERIFIED
  this pass` rather than asserting it (this honesty is already practised — keep it).
- **Migration citations point at the *live* file on disk** (e.g. `V140__wb_get_next_val.sql`), with archived
  numbers noted as archived (see §7 / DC-1). Never cite a renumbered-away version as live.
- **One index per feature folder + one roll-up per module.** The roll-up summarises; it does not duplicate.

---

## 5. Gap-analysis lifecycle

Every `refactor-gap-analysis.md` uses one fixed status vocabulary; a finding moves through these states over its life:

| Status | Meaning | Exit condition |
|---|---|---|
| **MATCH** | Refactor reproduces legacy behaviour | terminal (re-verify on related code change) |
| **APPROVED DEVIATION** | Intentional divergence, PO/architecture-approved (e.g. server-side scraping, stronger authz, slug audit ids) | terminal; needs a recorded decision |
| **DEAD LEGACY** | Legacy behaviour deliberately not ported (column/feature retired) | terminal; preserved for schema parity |
| **PARTIAL** | Implemented but incomplete (flag-gated, deferred sub-case) | → MATCH when completed, or stays PARTIAL with owner |
| **MISSING** | Required behaviour absent | → implemented (MATCH) or → PO-deferred |

**Lifecycle rules.**
- **A gap is closed in the doc only when the code closes it** — verified against the actual implementation, not the plan.
- **Gaps carry an owner + a `GAP-n` id** so they're traceable across the feature doc, the module roll-up, and the remediation plan.
- **Resolved gaps are marked `✅ RESOLVED <date>`, not deleted** (e.g. S-1 login-credentials audit, Phase 3C/P2-1) — preserves the lifecycle record.
- **Suspected ≠ confirmed.** Findings are tagged `[CONFIRMED]` (verified vs code) or `[SUSPECTED]` (needs a read) — never blur the two. `[SUSPECTED]` items must be verified before any doc edit acts on them.
- **Deployment-context items are classified separately** as `APPROVED DEVIATION / DEPLOYMENT CONTEXT NOTE` (DC-*) when the "gap" is documentation-clarity only and not a runtime/deployment risk (see §7).

---

## 6. Drift prevention — maintenance checklist

Reviewers run this checklist on every MR that touches code or docs. (Copy into the MR description.)

```
[ ] Code = source of truth: every changed doc claim is backed by a code path (path or path:line).
[ ] Same-MR rule: the doc(s) for the changed feature were updated in THIS MR (no "doc follow-up later").
[ ] Cross-tree pairing: a wire/contract change updated BOTH wecms-api interface-spec AND wecms-web api-consumption.
[ ] Coverage: a new feature ships the full 9-doc (API) / 6-doc (WEB) set; none empty (<~5 lines).
[ ] No stale version numbers: migration/version citations match the LIVE file on disk (archived numbers marked archived).
[ ] No retired-path claims: superseded designs are reframed as historical, not presented as current.
[ ] Authz accuracy: documented @PreAuthorize/@menuAuthz/role gates match the live annotations.
[ ] Slugs not WSVC ids in new identifier surfaces; legacy ids only in citations/traceability prose.
[ ] Gap-analysis updated: any new gap has a GAP-n id + owner + status from the §5 vocabulary.
[ ] source-code-index updated for new/moved/renamed code artifacts.
[ ] Links validated: 0 new broken intra-doc links (run the link-validation script).
[ ] Audit-history preserved: nothing deleted that records a past decision; superseded content dated + banner'd.
[ ] Legacy-DB governance (§7): no doc instructs creating legacy DDL or porting all functions into Flyway.
```

**Recommended automation (drift-prevention backlog, not yet built):**
- **CI link-validation gate** — fail the pipeline on a new broken intra-doc link (the Phase-1→3 audits used a Node walker over both `docs/` trees; promote it into CI).
- **Stale-citation linter** — flag doc-cited `V<n>__*.sql` filenames that don't exist on disk, and `path:line` citations whose file is gone.
- **Coverage check** — assert every `features/**/<screen>/` folder has the full 9/6-doc set and no empty files.
- **`@PreAuthorize` ↔ doc cross-check** — list controller gates vs. the slugs documented in interface-specs.

---

## 7. Legacy database governance

This is the one area where **Flyway is *not* the source of truth.** The refactor **reuses the existing legacy
database**; the authoritative state is the live **legacy / PROD schema, functions, and seed data**.

**Established model (from the deployment-context premise + DC-1/DC-2 in the remediation plan):**
- UAT and PROD **already contain** the legacy schema, legacy functions, and legacy seed. Refactor work
  **primarily ports application code**.
- Existing table **DDL is generally not recreated** through Flyway; existing **DB functions are reused**
  (called by name through `LegacyFunctionRepository` — e.g. `wb_get_next_val`, `rept_0015_001_r001_rec1`,
  `get_cashflow_report`), not re-expressed in Java.
- **`db/manual/` and `db/archive/` (the archived migration history) were removed 2026-06-23 (approved decision); only `db/migration/` remains.** A missing file under `db/migration` is **NOT** automatically a
  defect, and the project does not use manual-seed or archived-migration SQL. Do not assume a missing/renumbered Flyway file must be created.
- **Migration renumber awareness:** the 2026-06-09 consolidation renumbered the live set and archived the old
  files (that archive, `db/archive/`, was **removed 2026-06-23**). Docs must cite the **live** number (e.g. `V140__wb_get_next_val.sql`,
  nav `V137`–`V139`) and note archived numbers (`V155`, `V242/V243`) as archived — never as live (DC-1).

**Governance rules.**
- **Table baseline = the inventory.** [`references/database/legacy-table-inventory.md`](../../references/database/legacy-table-inventory.md)
  is the single source-of-truth table set; no entity/repository/migration for a table absent from it without PO sign-off.
- **Clean-environment provisioning is out of repository scope** (item **DC-2**). *Clean-environment database provisioning is outside the scope of this repository and must follow DBA-approved procedures.* The repository is **not** a
  complete historical archive of legacy DB objects (e.g. the navigation seed that shipped as the now-removed `V243`);
  a missing in-repo seed/DDL file is **not** a defect. **Do not** "fix" this by creating Flyway migrations, DDL, or
  replacement seed SQL.
- **Manual-DDL policy stays.** When DDL is genuinely needed it ships as a DBA-run manual script with documented
  run-order — never an auto-applied Flyway migration (existing project rule).
- **Drift classification for DB docs:** a doc-vs-disk version/seed mismatch is a **documentation-clarity** issue
  (DC-class), **not** a runtime risk — classify it accordingly, don't escalate it to "critical defect".

---

## 8. Roadmap summary — sequencing

| Horizon | Focus | Concrete steps |
|---|---|---|
| **Now (continuous)** | Lock in the per-MR discipline | Adopt §6 checklist in every MR; same-MR doc rule enforced at review. |
| **Each sprint** | Module audit-first hygiene | Refresh the touched module's audit triplet; re-run its gap-analysis vs code; keep source-code-index current. |
| **Near-term backlog** | Automate drift prevention | Build the CI link-validation gate + stale-citation linter + coverage check (§6 automation list). |
| **Pre-release / quarterly** | Cross-tree truth audit | Full 6-module × 5-dimension audit + full link validation; refresh drift reports; reconcile CLAUDE.md. |
| **Owner-gated, ongoing** | Legacy-DB governance | Keep the inventory authoritative; clean-env provisioning is out of repo scope (DBA-approved procedures, DC-2); preserve the legacy-reuse classification model (DC-1). |

**Definition of "healthy docs" (target state):**
1. Every feature has its full doc set; none empty. *(Coverage — achieved in Phase 2.)*
2. Every doc claim traces to a live code path; 0 confirmed code-vs-doc contradictions. *(Truth — Phase 3 baseline; maintain.)*
3. Intra-doc link health ≥ 99% with a CI gate preventing regressions. *(Phase 2 reached ~99.6%; add the gate.)*
4. Every gap has an id, owner, and §5 status; resolved gaps marked, not deleted.
5. DB-related items classified by the legacy-reuse model, never a Flyway-first model. *(Phase 3C.)*

This roadmap is the standing reference; the remediation plan tracks the open P2/P3/P4 + DC items that predate it.
