---
name: wecms-api-docs
description: Maintain WECMS refactor backend (Spring Boot) documentation under wecms-api/docs/. Use when creating, editing, restructuring, or cross-linking API/backend docs — feature specs, API contracts, database/ERD references, migration notes, or open questions. Enforces feature-first structure, reuse-before-create, open-question preservation, and link validation.
---

# wecms-api-docs

Maintains the **refactor backend** documentation tree at `wecms-api/docs/`. This is the Spring Boot 3.5.13 R&D target. JEX trees (`WECMS DEV/`, `SOURCE_CODE_ADMIN/`, …) are read-only functional reference — never write there.

## When this skill applies

Trigger when the task involves any of:
- **Writing the dated implementation record that docs-first requires — before the detailed investigation, and therefore before any application code change** (see the GATE and "Docs-first implementation records" below) — bug fix, issue, feature, **audit / investigation**, functional-behaviour / business-rule / API change, legacy-JEX parity resolution, functional refactor, SIT/UAT/PROD behaviour fix, or an integration change with observable effect.
- **Any request to check · investigate · review · audit · analyze a defect or behaviour** — the record comes first, then the investigation (`wecms-api/CLAUDE.md` §2a).
- Creating or editing a backend feature spec (`wecms-api/docs/features/...`).
- Writing or updating a REST API contract / endpoint doc.
- Adding or revising database references, table mappings, or ERDs.
- Recording, updating, or (with confirmation) resolving an open question.
- Reorganizing or cross-linking anything under `wecms-api/docs/`.

Do **not** apply this skill to frontend docs (`wecms-web/docs/` → use `wecms-web-docs`), to JEX docs (`docs/jex/`), or to application source code.

## ⛔ GATE — check this before any code-tracing workflow

**Added 2026-08-18 after a governance test failed.** A request to *check a Transfer-button error* was
answered with a full UI → API → DB → legacy investigation and **no record was ever created**.

**Before running any source-search, code-tracing, Git-history, or DB-inspection workflow for a
meaningful AUDIT / INVESTIGATION / BUG / ISSUE / FEATURE / PARITY REVIEW / BEHAVIOUR CHANGE, answer
one question:**

> **Has the required governed record been created?**

```
NO   → create it FIRST, verify the file exists, report its path — then investigate
YES  → proceed, and write findings into that record as they are found
FAIL → STOP. Report "DOCS-FIRST BLOCKED — documentation record could not be created."
       Do not continue the investigation.
```

```
NO DOC FILE  →  NO DETAILED INVESTIGATION  →  NO IMPLEMENTATION
```

**Orientation is allowed before the record; investigation is not.** Reading enough to know *which
module this is and which folder the record belongs in* is orientation. Tracing the fault, following the
call chain, querying the DB, comparing against legacy, or forming a root cause is investigation — and it
happens inside a record. Full rule: `wecms-api/CLAUDE.md` §2a.

**Never defer the record** with *"I'll investigate first and document once I understand it."* The record
starts at `Investigation Status: IN PROGRESS` with `## Root Cause — Pending investigation`; that is what
the status is for.

## Docs-first implementation records (mandatory)

**Adopted 2026-08-17.** For every meaningful behaviour-changing task, the **dated Markdown
implementation record is created BEFORE the detailed investigation begins — and therefore before any application code is modified**, with
`**Status:** IN PROGRESS`, and is completed in place afterwards. Documentation is part of the
implementation workflow, not an afterthought — never ask *"do you want me to document this?"* for a
clearly meaningful behaviour change.

> **This is a TEAM rule that this skill enforces — it is not an AI-only obligation.** Every developer
> contributing a meaningful behaviour-changing change to WeCMS follows docs-first, however the work is
> done (manually · Claude Code · Cursor · another AI assistant · IDE tooling · scripts · pair
> programming). When operating this skill, **check whether a record already exists for the task and
> update it** rather than adding a second one — **one task → one canonical record per repository**, and
> the document belongs to the task, not to a developer or a session. Team detail (responsibility,
> parallel work, review expectations, branch/environment independence, emergency-hotfix exception):
> `wecms-api/CLAUDE.md` §12 "Who must follow this".

**The authoritative rule is `wecms-api/CLAUDE.md` §"Docs-First Implementation Workflow
(MANDATORY)"** — mandatory order, Definition of Done, filename + date standards, both initial
templates, finalization rule, planned-vs-current wording, parity + cross-repo rules, exception list.
Read it before writing the record; if this skill and that section disagree, **CLAUDE.md wins**.

Quick reference:

- **Filename:** `descriptive-name-YYYY-MM-DD.md` — lowercase kebab-case, ISO date only (e.g. `account-filter-parity-fix-2026-08-17.md`). Never `a1-01-…`, `k9-fix.md`, `p1-update.md`, `task-03.md`, `final-fix.md`.
- **Internal issue / task IDs** (`A1-01`, `K9`, `TH-01`, `P0`, `Task-03`, …) go in the document metadata as `**Issue:**`, not in the filename.
- **Placement** *(policy revised 2026-08-17 — the earlier "no global `bugs/` or `issues/` folder" instruction is withdrawn)*:
  | Record type | Home |
  |---|---|
  | **BUG** | `docs/bugs/` |
  | **ISSUE** (non-bug: behavioural · operational · configuration · tracked implementation work) | `docs/issues/` |
  | **FEATURE** (current + permanent docs — structure unchanged) | `docs/features/<domain>/<feature>/` |
  | **AUDIT** (audit · parity review · verification · investigation · legacy comparison · migration verification · architecture review · historical validation · cross-feature analysis) | `docs/audits/<category>/` |

  Dated bug/issue records never go inside `docs/features/`. `docs/architecture/`, `docs/deployment/`, `docs/security/`, `docs/planning/open-questions/`, `docs/references/database/`, `docs/references/api-spec/` remain **permanent reference** homes — a bug/issue touching them still records in `docs/bugs/` / `docs/issues/` and updates the permanent doc afterwards.
- **BUG vs ISSUE:** BUG = implemented behaviour is incorrect · something that worked is broken · behaviour differs from an accepted requirement · a defect causes wrong behaviour · parity behaviour is wrong and the task is to fix it. ISSUE = needed work that is not clearly a product defect · operational/configuration correction · an unresolved implementation concern · a tracked problem that does not fit BUG cleanly. **When unclear, prefer ISSUE.** Never create duplicate BUG + ISSUE records for one task.
- **An audit is never converted into an implementation record.** If an audit leads to a fix, the audit stays in `docs/audits/` and a **separate** record is created in `docs/bugs/` / `docs/issues/`.
- **A large feature is still a feature** — do not open a bug/issue record just because feature work is big.
- **Finalize the same file** — never create a second "final" record. Move `Status:` off `IN PROGRESS` to `COMPLETE` / `CLOSED` / `PARTIAL` / `BLOCKED`, and update the permanent feature docs when the authoritative behaviour changed.
- **An existing authoritative feature doc does not remove this requirement.**
- **Declare `## Database Impact` in the record** *(added 2026-08-17)* — `NONE` · `FLYWAY MIGRATION` · `MANUAL DBA SCRIPT` · `BOTH` · `UNRESOLVED — DBA REVIEW REQUIRED`, decided at `IN PROGRESS`, never vague. **New table / new DB function** → `src/main/resources/db/migration/V<n>__<name>.sql` (schema-of-record; DBA-applied by hand — **Flyway is disabled**). **Existing-table change, index, constraint, or ANY data change** → a reviewed manual script at `docs/sql/<YYYY>/descriptive-name-YYYY-MM-DD.sql`. There is **no** rule that every DB change belongs in the migration folder, and executable SQL is **never** pasted into the Markdown. Full rule: `wecms-api/CLAUDE.md` §14 · folder rules: `docs/sql/README.md`.
- **Writing SQL ≠ running SQL.** This skill may author or edit a `.sql` artifact as a document deliverable, but **never executes** DDL/DML and never reports a script as applied. Execution is a separate operational action (DBA team / authorized developer / deployment team, per environment), tracked in the record's `## Database Execution Status` table. Never mark a task `COMPLETE` while a required DB execution is pending — and flag destructive changes as `DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED`.

> **This skill stays documentation-only.** Docs-first describes the *surrounding implementation
> workflow*: the record is written first (this skill's job), then the application code is changed
> **outside** this skill under the repo's own Scope Guard and approval rules. Hard rule 1 below is
> unchanged.

## Implementation quality gate — the record is finalized from evidence (mandatory)

**Added 2026-08-18.** Docs-first opens the task; the **quality gate closes it**. A meaningful task is
**not complete when the code is written** — it is complete when the change has been self-audited,
tested, verified, and honestly reported. **Authoritative rule: `wecms-api/CLAUDE.md` §15**
("Implementation quality gate"); if this skill and that section disagree, **CLAUDE.md wins**.

> **Team rule, not an AI rule.** It binds **Cambodia · Vietnam · Korea · any future WeCMS
> contributor**, and every tool — manual, Claude Code, Cursor, another AI assistant, IDE tooling,
> scripts, pair programming. **No team may run a weaker Definition of Done** (§15a).

What this means when operating this skill:

- **Do not finalize a record from the plan.** The `Status`, the `## Verification` section, and every
  "the system now does X" statement are written from what the verification pass actually produced —
  the Git diff (§15b, §15s), the tests that were run (§15i), and the checks that were performed.
- **Every BUG / ISSUE record ends with `## Verification`** (§15g) — `### Automated Tests` ·
  `### Test Scenarios` · `### Regression Review` · `### Manual Verification`, applicable subsections
  only. This supersedes the older single `## Test Results` heading; records that already use
  `## Test Results` keep it.
- **Honest states only:** `PASS` · `FAIL` · `NOT RUN` · `BLOCKED` · `NOT APPLICABLE`. **Never write
  `PASS` without evidence, and never turn a planned test case into a test result** (§15h) — this is the
  same discipline as the planned-vs-current wording rule.
- **A bug record documents its regression test** (`## Regression Test` — test file, test case, behaviour
  protected) or explains why an automated one is impractical and gives manual evidence. *"No test
  needed"* with no justification is not acceptable (§15d).
- **Test scenarios split by permanence:** permanent behaviour → the feature's
  `docs/features/<domain>/<feature>/test-scenarios.md`; this change's evidence → the dated record. **Do
  not duplicate a long scenario suite in both** (§15f).
- **Record the verification that applies** — API contract · UI-facing payload · database (§14 gate) ·
  legacy/JEX parity (what was actually compared, never just "matched legacy") · security & permissions ·
  performance & concurrency · error handling (§15k–§15q). Risk-based, not boilerplate: a checklist of
  "N/A" lines is noise (§15x).
- **Final status honesty:** `COMPLETE` only when every required part is genuinely done; otherwise
  `PARTIAL` · `BLOCKED` · `READY FOR DBA` · `READY FOR SIT` · `READY FOR DEPLOYMENT` (§15u).
- **Cross-repo:** one repository being green does not complete the task — both sides verify and the
  integration is verified end-to-end (§15r).
- Reviewer checklist: **§15v**. AI-specific enforcement (never stop at "the code compiles"): **§15w**.

> **This skill still does not run tests or modify source.** It records what the surrounding
> implementation workflow produced, and it **refuses to write an outcome that was not observed** — an
> unrun test is documented as `NOT RUN`, never as `PASS`.

## Human approval gate — investigation does not authorize implementation (mandatory)

**Added 2026-08-18. Authoritative rule: `wecms-api/CLAUDE.md` §17.**

```
INVESTIGATE → DOCUMENT FINDINGS → HUMAN APPROVAL → implement → quality gate
  → READY FOR MANUAL COMMIT → HUMAN Git / deployment process
```

- **Classify the request before doing anything.** *Check · investigate · review · audit · analyze* is
  **class A**: documentation and read-only inspection only, ending at **STOP for human approval**.
  *"Investigate and fix"* (B) and *"implement / fix this"* (C) already carry approval. **Ambiguous →
  class A.** Docs-first applies to all three.
- **Class A never authorizes**: modifying source · writing implementation tests · modifying SQL,
  migrations, or configuration · committing · pushing · merging · deploying.
- **Prove docs-first with the file path**, not the phrase. Report the record, classification, and the
  three statuses (§17b).
- **This skill's own placement rules already fit**: an investigation goes to `docs/audits/<category>/`;
  the **approved** fix gets its own record in `docs/bugs/` / `docs/issues/` that **links to the audit as
  evidence** — never a copy, never a conversion (§17i). **`Audit: COMPLETE` never means the defect is
  fixed** (§17n).
- **Write the three dimensions into the record metadata** — `Investigation Status` · `Approval Status` ·
  `Implementation Status` — and map them onto the existing canonical `Status:` field per §17k. This is
  **not** a second status vocabulary.
- **Never record an approval that a person did not give.** *"OK"* · *"thanks"* · *"looks good"* ·
  *"done"* · *"understood"* · *"check again"* are not approval (§17e). `Approval Status: NOT REQUIRED`
  is the honest value for class-B/C work.
- **A denied record is preserved, never deleted** — write `Approval Status: DENIED`, keep every piece of
  evidence, stop (§17g).
- **Implementation approval ≠ DB execution approval** (§17m) **≠ permission to push** (§16).

> **For an investigation-only task, this skill's deliverable is the record and the recommendation —
> and then it stops.** Do not ask *"shall I continue?"* as a nudge, and do not start implementing while
> waiting for the answer.

## Test-result records — durable execution evidence (mandatory)

**Added 2026-08-18. Authoritative rule: `wecms-api/CLAUDE.md` §18.**

- **When verification actually runs on a meaningful change, write a test-result record** at
  `docs/testing/results/descriptive-name-test-result-YYYY-MM-DD.md` — subject first, ISO date, never
  `test.md` / `result.md` / `p0-test.md` / `dev-a-test.md`.
- **Never in `docs/bugs/`, `docs/issues/`, or `docs/audits/`**, and never a second competing location:
  `docs/testing/sit/` and `docs/testing/uat/` stay per **environment cycle**, `results/` is per
  **implemented change**.
- **Link both directions** — the implementation record gains `## Test Evidence` pointing at the result;
  the result's `**Related Record:**` points back. The record's `## Verification` (§15g) becomes the
  **summary**; commands, counts, environment, build under test, and pre-existing-vs-new failures live in
  the result record.
- **Three documents, three jobs:** `test-scenarios.md` = the **specification** · a test-result record =
  the **evidence** · the bug/issue/feature record = **why the change happened**. Never let one stand in
  for another.
- **Never pre-write a `PASS` record before the tests run**, and never write `PASS` for a check that was
  not executed — `NOT RUN` / `BLOCKED` / `NOT APPLICABLE` **with the reason**. `Commit: NOT COMMITTED` is
  a valid, honest entry (§16).
- **Bug records answer the regression question explicitly** — *did a regression test cover the original
  defect? YES/NO* + test file, test case, result, or the reason why not (§18f).
- **Status follows evidence** (§18h): `Implementation Status: COMPLETE` requires test evidence that
  supports it; failing tests mean `Test Status: FAIL` + `BLOCKED` / `PARTIAL`. Passing tests are not a
  deployment.
- **Audit-only work needs no test-result record** (§18j) — diagnostic tests run as investigation
  evidence belong inside the audit.
- **Not retroactive** (§18m): do **not** back-fill result files for historical work. Report an in-flight
  gap as a **TEST RESULT MIGRATION CANDIDATE** instead.

Folder contract: `docs/testing/results/README.md`.

## Manual Git & deployment — stop at READY FOR MANUAL COMMIT (mandatory)

**Added 2026-08-18.** The documented work ends at the handover point, not at a push.

```
QUALITY GATE COMPLETE  →  READY FOR MANUAL COMMIT  →  HUMAN Git / deployment process
```

**Authoritative rule: `wecms-api/CLAUDE.md` §16 "Manual Git & deployment rule".**

- **Never** `git commit` · `git push` · `git merge` · `git rebase` onto a shared branch · create or push
  a tag · push to `sit` / `uat` / `prod` · trigger a deployment · promote between environments · run a
  release command **on your own initiative**. These are **manual developer / team responsibilities** —
  for every tool and every team (Cambodia · Vietnam · Korea · future contributors).
- **Read-only Git is fine and expected** — `git status`, `git diff`, `git log`, `git branch` back the
  self-audit (§15b, §15s).
- **Only an explicit request in the current conversation** ("commit this", "push this branch", "merge
  this branch") authorizes **that one operation** — it never extends to the operation that usually
  follows, nor to the rest of the session, and **SIT / UAT / PROD deployment stays manual regardless**.
  **Do not infer permission** from *"done"*, *"finish it"*, *"deploy-ready"*, *"complete"*, or *"ready
  to merge"*.
- **Record the honest terminal state.** `READY FOR MANUAL COMMIT` is the normal end state of AI-assisted
  work; `READY FOR DBA` · `READY FOR SIT` · `READY FOR UAT` · `READY FOR DEPLOYMENT` · `PARTIAL` ·
  `BLOCKED` cover the rest. **Never write `PUSHED` · `MERGED` · `DEPLOYED` · `SIT COMPLETE` ·
  `UAT COMPLETE` · `PROD COMPLETE` into a record unless the responsible team actually performed and
  verified the action** — the same honesty rule as unrun tests.

## Hard rules

1. **Never modify application source code.** Documentation only.
2. **Never move or reorganize existing docs** unless the user explicitly asks. Default to in-place edits.
3. **Write only under `wecms-api/docs/`** (plus the workspace cross-cutting folders the root CLAUDE.md allows: `docs/_shared/`, `docs/security/`, `docs/testing/`, `docs/bug-reports/`). Never write into `wecms-web/docs/` or `docs/jex/`.
4. **Audit before creating.** Never create a new doc without first checking whether one already covers the topic. **Exception — the dated implementation record required by docs-first is always a new file**, even when an authoritative feature doc already covers the topic; audit first to place it correctly and cross-link it, not to skip creating it.
5. **Reuse, cross-link, and preserve.** Extend existing docs; link related docs; never delete content without confirmation. Same exception as rule 4: a dated implementation record is never folded into an existing doc to avoid a new file.
6. **Identifier naming = kebab-case slugs**, not legacy JEX WSVC ids (`firm-banking-settings`, never `comm_0220_001`). Legacy ids appear only as JEX file:line citations or migration-traceability prose.
7. **Feature names match across systems** for traceability — a backend feature slug must match its JEX and frontend counterparts (e.g. `view-setting`).

## Folder structure

> **Corrected 2026-08-17** to match the real tree. This map previously showed `api-spec/` and
> `business-rules/` at top level and an `archive/` folder — none of those exist. It also omitted
> `deployment/`, `security/`, `reports/`, and `sql/`.
>
> **Updated 2026-08-17 (P2).** `vat-declaration/` was removed — its binaries moved into
> `features/addon-service/vat-declaration/source-requirements/attachments/`. The empty
> `testing/{integration,performance,regression}/` scaffolds were collapsed into `testing/README.md`.

```
wecms-api/docs/
├── README.md              # canonical folder index — keep in sync with this map
├── local-setup.md         # laptop setup walkthrough (repo-owned, authoritative)
├── workflow.md  glossary.md  permissions.md  error-codes.md   # pointer stubs → workspace root
│
├── features/              # THE SPINE — feature-first specs; default home for new feature docs
│   ├── auth/  common/  banking/  payment/  addon-service/  settings/   # six menu domains
│   ├── wecms-batch/
│   └── integrations/haengsung/   # non-menu integration docs (Oracle sync / write-back)
│
├── architecture/          # cross-feature architecture
│   └── api-catalog/       # endpoint catalogue (auth … erd)
│
├── references/            # supporting reference material (NOT feature docs)
│   ├── api-spec/          # REST endpoint contracts (*-api.md) + api-mapping.md
│   ├── business-rules/    # entities, repositories, services, security, settings
│   ├── database/          # schema design, table-mapping, coverage matrix
│   │   ├── erd/           # ERDs (per-domain + full-project, .md + .mmd)
│   │   ├── legacy-table-inventory.md      # AUTHORITATIVE 141-table baseline
│   │   └── flyway-migration-baseline.md   # READ BEFORE CITING ANY V<n>__*.sql
│   ├── migration/         # JEX → Spring service-by-service mapping + migration-playbook.md
│   ├── audits/            # ⚠️ MIGRATED 2026-08-17 — pointer stub only; all audits live in ../audits/
│   └── jex/  figma/       # JEX + Figma reference notes
│
├── bugs/                  # ADDED 2026-08-17 — dated BUG implementation records (docs-first)
├── issues/                # ADDED 2026-08-17 — dated ISSUE implementation records (non-bug)
├── audits/                # THE audit home — curated index in audits/README.md
│   ├── governance/  modules/  legacy-parity/  batch/  deployment/  historical/
├── deployment/            # SIT/UAT run-books + checklists — index at deployment/README.md
├── security/              # closure report, risk register, required secrets, rotation handoff
├── testing/               # test plans + fixture conventions
│   └── sit/  uat/         # integration/performance/regression collapsed into testing/README.md
├── planning/
│   └── open-questions/    # LIVE — OQ-DOC / OQ-DB tracking. Never delete an OQ without confirmation.
├── reports/               # one-off engineering reports
└── sql/                   # manual DBA .sql scripts, never app-run — see sql/README.md
    └── <YYYY>/            #   docs-first DB-impact artifacts (existing-table + data changes)
```

**Placement decision guide:**

| If the doc is… | It goes in |
|---|---|
| a **dated BUG implementation record** | `bugs/` — never `features/` |
| a **dated ISSUE implementation record** (non-bug) | `issues/` — never `features/` |
| how a screen / service behaves | `features/{domain}/{feature}/` |
| a cross-feature architectural decision | `architecture/` |
| an endpoint / DTO contract | `references/api-spec/` |
| a table / schema / ERD | `references/database/` |
| a JEX → Spring mapping narrative | `references/migration/` |
| a **dated, point-in-time** parity or governance finding | `audits/<category>/` (never `features/`, never `references/audits/`) |
| a deployment checklist or run-book | `deployment/` (never `features/`) |
| an unresolved decision needing a human | `planning/open-questions/` |

**Do not create a new top-level folder under `docs/`.** If one is genuinely needed, update this map,
[`wecms-api/docs/README.md`](../../../docs/README.md) §Layout, and `wecms-api/CLAUDE.md`
§"Documentation Structure Rules" in the same MR — those three must always agree. *(`bugs/` and
`issues/` were added this way on 2026-08-17 and are now part of the approved set.)*

**Never cite a Flyway migration number from memory or from an older doc.** The set was rebaselined
2026-06-09 and numbers were reassigned; verify against `src/main/resources/db/migration/` and read
`docs/references/database/flyway-migration-baseline.md` first.

See `references/api-doc-structure.md` for full placement rules and `references/api-doc-rules.md` for content conventions.

## Status metadata — narrow by design (convention set 2026-08-17)

A small set of documents carries a two-line header immediately under the title:

```
**Status:** active | historical | superseded · **Last reviewed:** YYYY-MM-DD
```

| Value | Meaning |
|---|---|
| **active** | Current reference, kept up to date. A reader may act on it today. |
| **historical** | A point-in-time record, preserved unchanged. Its findings remain valid **for its own date** and must not be rewritten to look current. |
| **superseded** | Replaced by a named later document, which the header must link. |

**Apply it only to these four kinds of document:**

1. **Index / navigation documents** — `docs/README.md`, and each top-level folder's `README.md`.
2. **Governance documents** whose currency determines whether someone may act (`audits/governance/*`,
   the remediation plan / roadmap / closure report).
3. **Baseline references** that other documents cite as authority — `flyway-migration-baseline.md`,
   `legacy-table-inventory.md`.
4. **Deployment run-books and checklists**, where acting on a stale procedure has a real cost.

**Do not apply it** to per-feature specs, per-audit records, source indexes, or gap analyses. Those
already carry their own dated headers (`**Audit date:**`, `**Date:**`), and the
[`audits/README.md`](../../../docs/audits/README.md) index already publishes a per-file status in its
`Notes` column — a second, hand-maintained timestamp on ~400 files would rot faster than it informs.
**A `Last reviewed` date that nobody actually refreshes is worse than no date at all.**

**When you touch a document that carries the header, update `Last reviewed` in the same edit.** If you
did not genuinely re-verify the content, leave the date alone — it records a review, not a keystroke.

## Feature domains (feature-first)

API documentation **must follow feature-first structure**, organized under the six domains:
`auth`, `common`, `banking`, `payment`, `addon-service`, `settings`.

Every feature doc lives under `wecms-api/docs/features/{domain}/`. A complex feature gets its own subfolder; a simple one gets a single kebab-case `.md`.

## Required workflow

Follow these steps in order for any doc task:

0. **If this is a behaviour-changing implementation task, write the dated record first.** Inspect the source, inspect the docs, inspect tests/Git history where needed, place the record per the placement rules, set `**Status:** IN PROGRESS`, and document the requirement · verified current behaviour · target behaviour · scope · out of scope · initial investigation · proposed plan · testing plan — **before** any application code is modified. Full order + templates: `wecms-api/CLAUDE.md` §"Docs-First Implementation Workflow (MANDATORY)".
0b. **First decide whether implementation is authorized at all.** A request to *check · investigate ·
    review · audit · analyze* is investigation-only: create the record, investigate, document the
    findings and the **recommended** implementation, set `Approval Status: PENDING`, and **stop for a
    human decision** — do not modify source, tests, SQL, migrations, or config (`wecms-api/CLAUDE.md`
    §17).
0c. **The record comes before the investigation, not after it.** Create it, **verify the file exists**,
    and report its path *before* the first code-tracing step (the GATE above · `wecms-api/CLAUDE.md`
    §2a). Then write every finding into that same record as it is discovered — the chat summarizes the
    file, it never replaces it. If the record cannot be created, **stop** and report
    `DOCS-FIRST BLOCKED`.
1. **Audit existing docs first.** Glob/grep `wecms-api/docs/` (especially `features/{domain}/`, `references/database/`, and `README.md` index files) for the topic. List what already exists before writing anything.
2. **Reuse / move / cross-link before creating.** Prefer extending an existing doc. If a new doc is truly needed, place it per the structure rules and link it from the relevant `README.md` index and from sibling docs. *(Does not apply to a dated implementation record — that is always its own new file; see Hard rules 4–5.)*
3. **Preserve open questions.** OQ items live in `wecms-api/docs/planning/open-questions/`. Never delete or close an **OQ-DOC** (documentation) or **OQ-DB** (database) item without explicit user confirmation. You may add new OQ items and update their status to "answered — pending confirmation," but the human closes them.
4. **Validate links.** After editing, check every relative link in touched files resolves to a real path (and that anchors exist where feasible). Report any broken links. Expected result: **0 broken documentation links.** *(There is no committed link-checker script in `wecms-api`; the frontend's `node scripts/check-doc-links.mjs` covers `wecms-web` only.)*
4b. **For an implementation task, finalize the record from the quality gate's evidence.** The
    `## Verification` section, the `## Regression Test` block on a bug record, the updated
    `test-scenarios.md`, and the final `Status:` all come from what was actually audited, run, and
    observed — never from the plan. Unrun checks are written as `NOT RUN` / `BLOCKED`. Full rule:
    `wecms-api/CLAUDE.md` §15.
5. **Provide a final report, and hand over — do not ship.** Summarize files created/edited, cross-links added, OQ items touched, and link-validation results. For an implementation task, also report the dated record's path, its final `Status:`, and the verification evidence it now carries (what was run, and what was `NOT RUN` / `BLOCKED` and why). **Close at `READY FOR MANUAL COMMIT`** — never commit, push, merge, tag, or deploy as part of finishing the task (`wecms-api/CLAUDE.md` §16).

## References

- `references/api-doc-rules.md` — content conventions, naming, cross-linking, OQ handling.
- `references/api-doc-structure.md` — directory map and placement decision guide.
