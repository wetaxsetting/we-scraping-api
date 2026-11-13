# wecms-api — Documentation

**Status:** active · **Last reviewed:** 2026-08-17

Backend documentation for the **Spring Boot 3.5.13** side of the WeCMS JEX → Spring/Next.js refactor (`wecms-api`). Local-only R&D (PostgreSQL, GitLab); no DEV/UAT/PROD deploy, no bank integration, no coexistence with the live JEX system.

> **Reorganized to feature-first (2026-06-01).** Documentation is now keyed off the live WeCMS
> **menu structure**: start at [features/](features/) and drill into the menu group → screen you need.
> Supporting detail (API contracts, schema, JEX→Spring mapping, parity audits) lives under
> [references/](references/); cross-feature design under [architecture/](architecture/). The earlier
> workspace-level `docs/refactor-wecms/` tree remains **deprecated read-only legacy**.
>
> **Structure update (2026-06-23, corrected 2026-08-17).** The `archive/` folder and the top-level
> `migration/` stray were **removed from `wecms-api/docs/` by project decision**; migration narratives live
> under [references/migration/](references/migration/). **`planning/` was NOT removed** — the earlier
> revision of this note said it was, which was wrong;
> [planning/open-questions/](planning/open-questions/) is live and carries 7 open-question documents.
> The actual top-level folders are listed in full in the [Layout](#layout) table below.

> **Field-name casing (project-wide standard).** Legacy JEX APIs used UPPER_SNAKE field names. The Spring/Next.js refactor intentionally uses lowercase snake_case JSON field names via global Jackson SNAKE_CASE configuration. This is an approved modernization under CTO directive dated 2026-05-08 and is not a legacy parity gap. Source-verified inventory + status table: [audits/field-casing-parity-audit-2026-06-06.md](./audits/historical/field-casing-parity-audit-2026-06-06.md).

## Start Here

> **You do not need JEX knowledge to begin development.** This is a **Spring Boot 3.5.13** codebase —
> start from the rules, the feature docs, and the code. JEX is **historical / functional reference only**
> (it tells you *what* a feature must do); you consult it only when porting a feature.
>
> **Source-of-truth split:** JEX is the **functional / behavioral** source of truth (*what* the system does);
> the **Spring Boot code is the implementation source of truth** (*how* it is built). Build from the Spring code,
> never from JEX.

**New to this codebase? Read in this order.** All documentation is written in **English**. WeCMS is a refactor of
the legacy **JEX** system, so a few business terms are Korean (결재라인 → *approval line*, 사용용도 → *purpose*);
when a JEX or Korean term is unfamiliar, check the glossary (step 4) — you never have to read JEX itself to get
started. This guide is for new joiners, including developers in **Cambodia** and **Vietnam**.

1. **[../CLAUDE.md](../CLAUDE.md)** — the project rules (how to build, naming, what not to touch). Read first.
2. **[local-setup.md](local-setup.md)** — get the backend running on your laptop. **Do this before anything else** — the app will not start without a `.env.local`, and the error it gives you looks like an Oracle bug.
3. **[README.md](README.md)** — *(this file)* — how the docs are organized and where things live.
4. **[Implementation records (docs-first)](#implementation-records-docs-first)** — ⚠️ **read before your
   first change.** Every developer on the team writes the dated record **before** implementing a
   meaningful behaviour change — this is a project rule, not an AI-only rule, and it applies however
   you write the code. Read the
   **[implementation quality gate](#implementation-quality-gate--verify-before-complete)** in the same
   pass: the task is not finished when the code is written, it is finished when the change has been
   audited, tested, verified, and honestly reported.
5. **[glossary.md](glossary.md)** — business + technical terms (JEX/Korean → English). Skim it before the feature docs.
6. **Feature README** — open the screen you're working on: `features/<module>/<screen>/README.md`
   (e.g. [features/settings/contacts/README.md](features/settings/contacts/README.md)). Each feature folder has the
   same 9-doc set.
7. **`business-flow.md`** — what the feature does end-to-end (user flow + backend orchestration).
8. **`interface-specification.md`** — the API request/response contract (lowercase snake_case wire shape).
9. **`source-code-index.md`** — the map from this feature's docs to the actual backend/frontend code + tests.
10. **Relevant audits** — for parity history, decisions, and known gaps, see the **[audits index](audits/README.md)**
    (governance docs, per-module triplets, per-feature parity audits).

**Also useful:** [workflow.md](workflow.md) (dev flow) · [permissions.md](permissions.md) (roles) ·
[error-codes.md](error-codes.md) (`WBExxxx` codes) · frontend docs at [../../wecms-web/docs/](../../wecms-web/docs/).

## Implementation records (docs-first)

**Status:** active · **Last reviewed:** 2026-08-17

**Adopted 2026-08-17.** Every meaningful behaviour-changing task — bug fix, issue, feature, audit /
parity review / investigation, functional behaviour / business-rule / API / integration change,
legacy-JEX parity resolution, functional refactor, or SIT/UAT/PROD behaviour fix — gets a **dated
Markdown implementation record created BEFORE the detailed investigation begins — and therefore before any application code is modified**, with `**Status:** IN
PROGRESS`. The record is then completed in place after implementation and its status moved to
`COMPLETE` / `CLOSED` / `PARTIAL` / `BLOCKED`.

> **⛔ HARD PRECONDITION — `NO DOC FILE → NO DETAILED INVESTIGATION → NO IMPLEMENTATION`**
> *(strengthened 2026-08-18 after a governance test failed — a "can you check this error?" request
> produced a full UI → API → DB → legacy investigation and no record).*
> **Orientation** — reading enough to classify the task and place its record — is allowed before the
> file exists. **Investigation** — tracing the fault, following the call chain, querying the DB,
> comparing against legacy, forming a root cause — is not. Create the record, **verify it exists**,
> report its path, and only then investigate; write findings into that same record as they are found.
> An unknown root cause is written `Pending investigation` — never a reason to postpone the record. If
> the record cannot be created, **stop** and report `DOCS-FIRST BLOCKED`. Full rule:
> [../CLAUDE.md](../CLAUDE.md) §2a.

> **This applies to the whole team, not just to AI sessions.** Every developer contributing a
> meaningful behaviour-changing change follows docs-first — **manually, with Claude Code, with Cursor,
> with any other AI assistant, with IDE tooling, via scripts, or pair programming.** No one is exempt
> because they did not use an AI tool, because the work is on their own branch, because the change is
> small but behaviour-changing, or because it is backend-only. The requirement belongs to the
> **project**, not to a person or a tool. AI assistants **enforce** this rule; they do not own it.
>
> The **developer who starts the change** is responsible for ensuring the record exists before code
> implementation begins. **One task → one canonical record per affected repository** — if a second
> developer continues the work, they update the *same* record, never add a per-person copy. Team
> detail (parallel work, review expectations, branch/environment independence, the emergency-hotfix
> exception): [../CLAUDE.md](../CLAUDE.md) §12 "Who must follow this".

The full rule — mandatory order, Definition of Done, filename + date standards, both initial
templates, planned-vs-current wording, the parity and cross-repo rules, and the exception list — is
**authoritative in [../CLAUDE.md](../CLAUDE.md) §"Docs-First Implementation Workflow (MANDATORY)"**.
This section is a pointer; if the two disagree, `CLAUDE.md` wins.

- **Filename:** `descriptive-name-YYYY-MM-DD.md`, lowercase kebab-case, ISO date only — e.g.
  `account-filter-parity-fix-2026-08-17.md`. Never a work-code or sequence filename
  (`a1-01-…`, `k9-fix.md`, `p1-update.md`, `task-03.md`, `final-fix.md`).
- **Internal issue / task IDs** (`A1-01`, `K9`, `TH-01`, `P0`, `Task-03`, …) go in the document
  metadata as `**Issue:**`, not in the filename.
- **Where it goes** *(placement policy revised 2026-08-17)* — four record categories, one home each:
  | Record type | Home |
  |---|---|
  | **BUG** — dated bug-fix implementation record | [bugs/](bugs/) |
  | **ISSUE** — dated non-bug issue / behavioural / operational / tracked implementation record | [issues/](issues/) |
  | **FEATURE** — current + permanent feature documentation (**structure unchanged**) | [features/](features/)`<domain>/<feature>/` |
  | **AUDIT** — audit · parity review · verification · investigation · legacy comparison · migration verification · architecture review · historical validation · cross-feature analysis | [audits/](audits/)`<category>/` |

  Dated bug/issue records **no longer** live inside the feature folder; the earlier "no global `bugs/`
  or `issues/` folder" instruction is **withdrawn**. [architecture/](architecture/) ·
  [deployment/](deployment/) · [security/](security/) ·
  [planning/open-questions/](planning/open-questions/) · [references/database/](references/database/) ·
  [references/api-spec/](references/api-spec/) keep their existing purposes as **permanent reference**
  material — a bug/issue that touches one of them still gets its dated record in `bugs/` / `issues/`
  and the permanent doc updated afterwards.
- **BUG vs ISSUE:** BUG = implemented behaviour is incorrect / something broke / behaviour differs from
  an accepted requirement / a defect causes wrong behaviour / parity behaviour is wrong and the task is
  to fix it. ISSUE = needed implementation work that is not clearly a product defect / operational or
  configuration correction / an unresolved implementation concern / a tracked problem that does not fit
  BUG cleanly. **When BUG-vs-ISSUE is unclear for work already known to need implementation, prefer
  ISSUE — but when the nature of the problem itself is unclear, the answer is AUDIT, not ISSUE.**
  Classification is semantic — intent + verified nature, never the words the user used; the definitions
  above are meanings, not keyword triggers. Never file duplicate BUG + ISSUE records for one task.
- **An audit is never converted into an implementation record.** If an audit leads to a fix, the audit
  stays in [audits/](audits/) and a **separate** record is created in [bugs/](bugs/) or
  [issues/](issues/). An audit may *identify* DB work but never creates or executes it.
- **Database impact is declared in the record, before implementation** *(added 2026-08-17)* — a
  `## Database Impact` section set to one of `NONE` · `FLYWAY MIGRATION` · `MANUAL DBA SCRIPT` · `BOTH` ·
  `UNRESOLVED — DBA REVIEW REQUIRED`, never left vague. **New table / new DB function** → a
  `V<n>__<name>.sql` file in `src/main/resources/db/migration/` (schema-of-record — still DBA-applied by
  hand; **Flyway is disabled**, see
  [references/database/flyway-migration-baseline.md](references/database/flyway-migration-baseline.md)).
  **Existing-table change, index, constraint, or any data change** → a reviewed manual script in
  [sql/](sql/)`<YYYY>/descriptive-name-YYYY-MM-DD.sql`. There is **no** rule that every DB change goes
  into the migration folder. The record explains *why* and links the artifact; the `.sql` states *what to
  run*, with pre/post-checks, a named executor, and a per-environment execution table. **Preparing a
  script is not executing it** — execution is a separate operational action, and an AI assistant never
  executes shared-environment SQL. Full rule: [../CLAUDE.md](../CLAUDE.md) §14 · folder rules:
  [sql/README.md](sql/README.md).
- **A large feature is still a feature** — do not open a bug/issue record just because feature work is
  big.
- **Dated record ≠ permanent docs.** [bugs/](bugs/) and [issues/](issues/) are **change history**; they
  do not replace [features/](features/). The dated record explains *why the change happened*; the
  feature docs describe *how the system works now*. Update the permanent docs after implementation when
  the authoritative behaviour changed — and never treat a dated record as the only source of truth for
  current behaviour.
- **An existing authoritative feature doc does not remove the docs-first requirement.**
- **Cross-repo work** (API + WEB) creates the record in **both** repositories before either side's
  code is modified — `wecms-api/docs/bugs/` ↔ `wecms-web/docs/bugs/` for a BUG,
  `wecms-api/docs/issues/` ↔ `wecms-web/docs/issues/` for an ISSUE, same descriptive subject + date
  where appropriate, cross-linked. For a cross-repo **audit**, prefer the repository that owns the
  primary audit scope; if both sides need their own evidence, scope one record per repository and
  cross-link. See the frontend counterpart in `wecms-web/docs/README.md`.

### Implementation quality gate — verify before COMPLETE

**Added 2026-08-18.** Docs-first opens the task; the **quality gate closes it**. After the code is
written, a meaningful BUG / ISSUE / FEATURE / parity fix / behaviour, business-rule, API, UI or
integration change is **not complete until it has been verified**:

```
DOCS-FIRST RECORD → implementation → implementation self-audit → tests
  → test scenarios → regression review → DB verification (if applicable)
  → security / permission verification (if applicable) → documentation update
  → final evidence → COMPLETE
```

**The full rule is authoritative in [../CLAUDE.md](../CLAUDE.md) §15 "Implementation quality gate".**
This is a pointer; if the two disagree, `CLAUDE.md` wins. In short:

- **Self-audit the actual Git diff** (`git status` · `git diff` · `git diff --stat`) — not the original
  ticket text. Unrequested changes in the diff are a finding, not a bonus (§15b, §15s).
- **Decide the appropriate tests and add them where in scope.** A bug fix normally carries a
  **regression test** that fails before the fix and passes after; if that is impractical, the record
  says **why** and supplies manual evidence. *"No test needed"* with no justification is not
  acceptable (§15c, §15d). Do not write filler tests, and do not invent test infrastructure this repo
  does not have — there is **no H2, no embedded DB, no Testcontainers** here (§15c).
- **Run the smallest relevant scope first** (`./gradlew test --tests "…"`), consider a broader
  regression run before completion, and **report exactly what was run** (§15i). Failing tests are
  classified — caused-by-this-change / pre-existing / environment / test-data / unresolved — never
  hidden (§15j).
- **Update `features/**/test-scenarios.md`** when permanent behaviour changed; the dated record keeps
  only this change's evidence (§15f).
- **Verify what applies**: API contract · UI-facing payload · database (§14 gate) · legacy/JEX parity ·
  security & permissions · performance & concurrency · error handling (§15k–§15q).
- **Record the evidence** in the record's **`## Verification`** section, with honest states —
  `PASS` / `FAIL` / `NOT RUN` / `BLOCKED` / `NOT APPLICABLE`. A test plan is never reported as a test
  result (§15g, §15h).
- **Write the durable evidence to a test-result record** *(added 2026-08-18)*. Once verification
  actually runs, the commands, counts, environment, build under test, and pre-existing-vs-new failures
  go into [testing/results/](testing/results/)`descriptive-name-test-result-YYYY-MM-DD.md`; the
  implementation record gains a **`## Test Evidence`** section linking to it, and the result links back.
  `## Verification` stays the summary. **`Implementation Status: COMPLETE` requires test evidence that
  supports it** — if tests fail, the honest pair is `Test Status: FAIL` + `BLOCKED` / `PARTIAL` (§18).
  Rules: [testing/results/README.md](testing/results/README.md).
- **`COMPLETE` only when every required part is done** — otherwise `PARTIAL` / `BLOCKED` /
  `READY FOR DBA` / `READY FOR SIT` / `READY FOR DEPLOYMENT` (§15u).
- **Cross-repo work is not complete because one repository is green** — both sides verify, then the
  integration is verified end-to-end (§15r).
- **Risk-based, not boilerplate.** Every check is *required when applicable*; a CSS fix does not need a
  DB audit, and a payment-dispatch change needs a great deal more (§15x).

**Same team scope as docs-first:** Cambodia · Vietnam · Korea · any future WeCMS contributor, and every
tool — manual, Claude Code, Cursor, another AI assistant, IDE tooling, scripts, pair programming. **No
team runs a weaker Definition of Done** (§15a). Reviewer checklist: §15v. AI assistants enforce the
same rule and may not stop at "the code compiles" (§15w).

### Manual Git & deployment — the workflow ends at READY FOR MANUAL COMMIT

**Added 2026-08-18.** The quality gate finishes the *work*; it does not ship it.

```
QUALITY GATE COMPLETE  →  READY FOR MANUAL COMMIT  →  HUMAN Git / deployment process
```

**The full rule is authoritative in [../CLAUDE.md](../CLAUDE.md) §16 "Manual Git & deployment rule".**
This is a pointer; if the two disagree, `CLAUDE.md` wins. In short:

- **No AI assistant automatically performs** `git commit` · `git push` · `git merge` · `git rebase` onto
  a shared branch · tag creation/push · push to `sit` / `uat` / `prod` · a deployment trigger · a
  promotion between environments · any release command. These are **manual developer / team
  responsibilities** (§16a).
- **Read-only Git inspection is expected** — `git status`, `git diff`, `git log`, `git branch` are part
  of the quality gate (§15b, §15s). The boundary is between *observing* the repository and *changing
  shared history or a running environment*.
- **The AI-assisted workflow ends at `READY FOR MANUAL COMMIT`** — never at `COMMITTED`, `PUSHED`, or
  `DEPLOYED`. After that, the developer reviews `git status` / `git diff`, stages, commits, pushes the
  intended branch, merges per team process, and promotes to SIT / UAT / PROD (§16b).
- **Explicit-request exception only** (§16c): if the user asks in the current conversation for one
  specific operation ("commit this", "push this branch", "merge this branch"), the assistant may assist
  with **that operation only**. It does not extend to the operations that usually follow, nor to the
  rest of the session. **SIT / UAT / PROD deployment and promotion stay manual regardless.**
- **Never infer permission** from *"done"*, *"finish it"*, *"deploy-ready"*, *"complete"*, or *"ready to
  merge"* — none of those means commit, push, or deploy.
- **Never report** `PUSHED` · `MERGED` · `DEPLOYED` · `SIT COMPLETE` · `UAT COMPLETE` · `PROD COMPLETE`
  unless the responsible team actually performed and verified the action (§16d).

**Team-wide** — Cambodia · Vietnam · Korea · future contributors · Claude Code · Agents · Cursor · any
other AI tool. Git and environment promotion remain human-controlled steps (§16e).

### Human approval gate — investigation does not authorize implementation

**Added 2026-08-18.** The full chain, from a request to a hand-over:

```
INVESTIGATE  →  DOCUMENT FINDINGS  →  HUMAN APPROVAL  →  implement  →  quality gate
   →  READY FOR MANUAL COMMIT  →  HUMAN Git / deployment process
```

**The full rule is authoritative in [../CLAUDE.md](../CLAUDE.md) §17 "Human approval gate between
investigation and implementation".** This is a pointer; if the two disagree, `CLAUDE.md` wins.

- **The record is the pre-implementation review contract** (§17a). Before code changes, a reviewer must
  be able to read it and know what is wrong, what the system does now, why, what is recommended, which
  repositories are affected, the DB/API/UI impact, the risks, and the tests required.
- **The record exists first — and the assistant proves it** (§17b) by naming the actual file path,
  classification, and the three statuses. *"Docs-first followed"* is not evidence; the file must exist.
- **Three request classes** (§17c): **A · check / investigate / review / audit / analyze** authorizes
  documentation and read-only inspection and then **stops for human approval** — never source, tests,
  SQL, migrations, or config. **B · "investigate and fix"** already carries approval; do not ask twice.
  **C · "implement / fix this"** is explicitly authorized. Docs-first applies to all three. **Ambiguous
  → treat as A.**
- **The human decides** — `APPROVE IMPLEMENTATION` · `DENY` · `NEEDS MORE INVESTIGATION` (§17e).
  *"OK"* · *"thanks"* · *"looks good"* · *"done"* · *"understood"* · *"check again"* are **not**
  approval.
- **If denied** (§17g): no source, no tests, no DB change, no migration — record the decision, preserve
  the evidence, stop. **The record stays as engineering history; it is never deleted because
  implementation was denied.**
- **AUDIT → BUG/ISSUE → implementation** (§17i): the audit stays in [audits/](audits/); the approved fix
  gets its own record in [bugs/](bugs/) / [issues/](issues/) that **links to the audit as evidence**
  rather than copying it. **`Audit: COMPLETE` never means the defect is fixed** (§17n).
- **Three status dimensions** — Investigation / Approval / Implementation — recorded as metadata and
  mapped onto the existing canonical `Status:` vocabulary; this is **not** a second vocabulary (§17k).
- **Implementation approval ≠ DB execution approval** (§17m). Code may be `APPROVED` while SQL is still
  `READY FOR DBA`.

**Team-wide** — Cambodia · Vietnam · Korea · future contributors · Claude Code · Agents · Cursor · any
other AI tool (§17p). One canonical record per repository; a recorded denial binds everyone until a
later authorized decision changes it.

## Layout

**Every top-level folder that exists under `wecms-api/docs/` today.** If a folder is not in this table, it does
not exist; if it exists, it is in this table. Verified against the tree 2026-08-17.

| Folder / file | Contents |
|---|---|
| [features/](features/) | **The spine.** Per-feature functional specs grouped by the six menu domains (`auth` · `common` · `banking` · `payment` · `addon-service` · `settings`), plus `wecms-batch/` and [`integrations/haengsung/`](features/integrations/haengsung/) (non-menu integration docs). **Current + permanent** behaviour — dated bug/issue records live in `bugs/` / `issues/`, not here. Start here. |
| [bugs/](bugs/) | **Added 2026-08-17.** Dated **bug-fix** implementation records — `descriptive-name-YYYY-MM-DD.md`, created *before* the code change at `Status: IN PROGRESS` and completed in place. Change history, not current-behaviour documentation. Rules: [bugs/README.md](bugs/README.md) |
| [issues/](issues/) | **Added 2026-08-17.** Dated **issue** implementation records (non-bug: behavioural, operational, configuration, tracked implementation work) — same filename + docs-first rules. Rules: [issues/README.md](issues/README.md) |
| [architecture/](architecture/) | Cross-feature system architecture — module boundaries, payment-dispatch call graph, tenant isolation, timezone decisions, plus [architecture/api-catalog/](architecture/api-catalog/) (the numbered endpoint catalogue) |
| [references/](references/) | Supporting reference material — see the four sub-trees below |
| [references/api-spec/](references/api-spec/) | REST endpoint contracts (`*-api.md`) + [api-mapping.md](references/api-spec/api-mapping.md) cross-feature index |
| [references/business-rules/](references/business-rules/) | Service / domain design — entities, repositories, services, security, settings |
| [references/database/](references/database/) | Schema design, ERDs, table-mapping, [legacy-table-inventory.md](references/database/legacy-table-inventory.md) (the authoritative 141-table baseline), [flyway-migration-baseline.md](references/database/flyway-migration-baseline.md) (**read before citing any migration number**), [manual-tenant-setup.md](references/database/manual-tenant-setup.md) |
| [references/migration/](references/migration/) | JEX → Spring service/field mapping, jex-analysis, [migration-playbook.md](references/migration/migration-playbook.md) |
| [references/audits/](references/audits/) | ⚠️ **MIGRATED 2026-08-17** — now a pointer stub only. All audit files moved to [audits/](audits/). |
| [references/jex/](references/jex/) · [references/figma/](references/figma/) | JEX + Figma reference notes |
| [audits/](audits/) | **The single authoritative audit home** (136 files), grouped into [governance/](audits/governance/) · [modules/](audits/modules/) · [legacy-parity/](audits/legacy-parity/) · [batch/](audits/batch/) · [deployment/](audits/deployment/) · [historical/](audits/historical/). Audits · parity reviews · verifications · investigations · legacy comparisons · migration verifications · architecture reviews · historical validation · cross-feature analysis. An audit is **never** converted into an implementation record — a fix it triggers gets its own record in [bugs/](bugs/) or [issues/](issues/). Curated index with a status legend: [audits/README.md](audits/README.md) |
| [deployment/](deployment/) | SIT / UAT run-books, checklists, batch activation, callback security, config references. **Start at [deployment/README.md](deployment/README.md)** (index by purpose). Readiness *audits* live in [audits/deployment/](audits/deployment/). |
| [security/](security/) | Security closure report, remaining-risk register, ops secret-rotation handoff, required environment secrets — index at [security/README.md](security/README.md) |
| [testing/](testing/) | Refactor backend test plans + fixture conventions, plus three live subfolders — [testing/results/](testing/results/) (**added 2026-08-18** — durable per-change **test-result records**, `descriptive-name-test-result-YYYY-MM-DD.md`; rules at [testing/results/README.md](testing/results/README.md)), [testing/sit/](testing/sit/) (carries the field-casing assertion rule) and [testing/uat/](testing/uat/). `results/` is per **implemented change**; `sit/` and `uat/` stay per **environment cycle**. The empty `integration/` · `performance/` · `regression/` scaffolds were **collapsed into [testing/README.md](testing/README.md) on 2026-08-17**; write those plans at the `testing/` root. |
| [planning/](planning/) | **Live.** [planning/open-questions/](planning/open-questions/) — open-question tracking (OQ-DOC / OQ-DB items, project reviews, VAT follow-ups) plus the Haengsung Oracle integration roadmap. Never delete an OQ item without explicit confirmation. |
| [reports/](reports/) | One-off engineering reports (dead-code review, vendor-payment upload implementation report) |
| [sql/](sql/) | **Manual DBA** `.sql` scripts — never run by the application. Kept in-repo (the workspace `Claude Code (AI)/` folder is under no version control) and each is the deliverable of a document that specifies it. **Since 2026-08-17 this is also the home for the docs-first DB-impact artifacts**: existing-table alterations, indexes, constraints, and all data changes, as `sql/<YYYY>/descriptive-name-YYYY-MM-DD.sql`. New tables / new DB functions stay in `src/main/resources/db/migration/`. Index + rules: [sql/README.md](sql/README.md). |
| [README.md](README.md) *(this file)* · [local-setup.md](local-setup.md) | Root index + the **laptop setup walkthrough** (repo-owned, authoritative) |
| [workflow.md](workflow.md) · [glossary.md](glossary.md) · [permissions.md](permissions.md) · [error-codes.md](error-codes.md) | Four **pointer stubs** to the workspace-level authoritative copies (see "Shared docs" below) |

> **One audit folder — the duplication is resolved.** [audits/](audits/) is the **single authoritative
> home**; every file that used to live in `references/audits/` was migrated into it on 2026-08-17, and
> [references/audits/](references/audits/) is now a pointer stub with no audit content. Search
> [audits/](audits/) only, and write new audits there. *(This note previously said "search both" — that
> instruction is obsolete and was corrected on 2026-08-17.)*
>
> **Removed 2026-06-23 (project decision):** `archive/` (superseded feature docs + structure/classification
> reports + settings-menu hubs) and the top-level `migration/` stray. Their removal does **not** indicate
> missing functionality. `planning/` was **not** removed — see the corrected note at the top of this file.

### Settings menu (`settings/`)

Canonical API-side doc per **Settings** menu item (backend / API / DB / business logic). Each file
cross-links to the detailed `business-rules/`, `features/`, `database/`, and `migration/` docs.

| Settings item | Doc |
|---|---|
| Purpose | [settings/purpose.md](features/settings/purpose.md) |
| VAS Account | [settings/vas-account.md](features/settings/vas-account.md) |
| Contacts | [settings/contacts.md](features/settings/contacts.md) |
| Approval Line | [settings/approval-line.md](features/settings/approval-line.md) |
| Chart of Accounts | [settings/chart-of-accounts.md](features/settings/chart-of-accounts.md) |
| Login Credentials | [settings/login-credentials.md](features/settings/login-credentials.md) |
| Bank Account | [settings/bank-account.md](features/settings/bank-account.md) |
| Preferences | [settings/preferences.md](features/settings/preferences.md) |
| Users | [settings/users.md](features/settings/users.md) |
| Transaction History (ERP) | [settings/transaction-history.md](features/settings/transaction-history.md) |
| Firm Banking Settings | [settings/firm-banking-settings.md](features/settings/firm-banking-settings.md) |
| Firm Banking History | [settings/firm-banking-history.md](features/settings/firm-banking-history.md) |

### Add-on Service menu (`features/addon-service/`)

Add-on Service docs are split across **three layers** (the feature spec is the hub; granular contracts
and schema stay in their own sections and are cross-linked, not duplicated):

| Layer | Location | Contents |
|---|---|---|
| **Feature spec** (menu-level) | [`features/addon-service/`](features/addon-service/) | Scope, business rules, validation, error handling, integration flow; links out to the two layers below |
| **API contract** | [`api-spec/addon/`](references/api-spec/addon/) | Endpoint/DTO contracts (`*-api.md`) |
| **DB schema** | [`database/addon/`](references/database/addon/) | Table/column schema (`*-db.md`) |

Each feature spec links out to the frontend UI doc in `wecms-web/docs/features/addon-service/`. *(The former
`planning/addon/*-plan.md` task plans were removed with `planning/` on 2026-06-23 — project decision.)*

| Add-on Service item | Doc |
|---|---|
| Sales e-Invoices | [features/addon-service/sales-e-invoices.md](features/addon-service/sales-e-invoices.md) |
| Expenses e-Invoices | [features/addon-service/expenses-e-invoices.md](features/addon-service/expenses-e-invoices.md) |
| Operation Status Inquiry | [features/addon-service/operation-status-inquiry.md](features/addon-service/operation-status-inquiry.md) |

### Banking menu (`features/banking/`)

Canonical API-side feature hub per **Banking** menu item (3-layer pattern: hub →
[`api-spec/banking/`](references/api-spec/banking/) contracts + [`database/banking/`](references/database/banking/) schema).
Full index in [features/banking/README.md](features/banking/README.md).

| Group | Items |
|---|---|
| Current & Savings (VND) | [account-inquiry-vnd](./features/banking/current-savings-vnd/account-inquiry/README.md) · [transaction-history-vnd](./features/banking/current-savings-vnd/transaction-history/README.md) · [smart-organizer-vnd](./features/banking/smart-organizer/README.md) |
| Current & Savings (FCY) | [account-inquiry-fcy](./features/banking/current-savings-fcy/account-inquiry/README.md) · [transaction-history-fcy](./features/banking/current-savings-fcy/transaction-history/README.md) · [smart-organizer-fcy](./features/banking/smart-organizer/README.md) |
| Internal Transfer | [own-account-transfer](./features/banking/internal-transfer-vnd/own-account-transfer/README.md) · [transfer-history](./features/banking/internal-transfer-vnd/transfer-history/README.md) |
| Reports | [daily-cash](./features/banking/reports/daily-cash/README.md) · [daily-financial](./features/banking/reports/daily-financial/README.md) · [cash-flow](./features/banking/reports/cash-flow/README.md) |

Smart Organizer is backend-implemented (`SmartOrganizerController` + `OrganizerRuleController` + V172–V174) with a Next.js route; its feature docs carry residual `Needs Confirmation` detail to reconcile against the shipped code.

### Payment menu (`features/payment/`)

Family-hub per **Payment** menu group (each hub covers its Scheduled / Result + Details sub-items as sections).
Full index + cross-sibling / shared-infra authority: [features/payment/README.md](features/payment/README.md).

| Group | Hub |
|---|---|
| Approval Management → Approval Box | [features/payment/approval-box/](features/payment/approval-box/README.md) |
| Internal Transfer | [features/payment/internal-transfer/](features/payment/internal-transfer/README.md) ⭐ |
| Vendor Payment | [features/payment/vendor-payment/](features/payment/vendor-payment/README.md) 🟡 stub |
| Expense Payment | [features/payment/expense-payment/](features/payment/expense-payment/README.md) 🟡 stub |
| Payroll | [features/payment/payroll/](features/payment/payroll/README.md) 🟡 stub |
| Overseas Payment | [features/payment/overseas-payment/](features/payment/overseas-payment/README.md) 🆕 |
| Add-on → Exchange Rate Inquiry | [features/payment/exchange-rate-inquiry.md](features/payment/exchange-rate-inquiry.md) |
| Corporate Card → Card Transactions | [features/payment/card-transactions.md](features/payment/card-transactions.md) |

### Shared (cross-system) docs — pointers only

These apply to **both** the JEX and refactor systems and are maintained once at the workspace root:

| Here | Authoritative source |
|---|---|
| [workflow.md](workflow.md) | [../../docs/WORKFLOW.md](../../docs/WORKFLOW.md) |
| [glossary.md](glossary.md) | [../../docs/glossary.md](../../docs/glossary.md) |
| [permissions.md](permissions.md) | [../../docs/_shared/permission-roles.md](../../docs/_shared/permission-roles.md) |
| [error-codes.md](error-codes.md) | [../../docs/_shared/error-code-registry.md](../../docs/_shared/error-code-registry.md) |
| [security/](architecture/security/) | [../../docs/security/](../../docs/security/) + [../../docs/_shared/security-baseline.md](../../docs/_shared/security-baseline.md) |
| bug-reports | [../../docs/bug-reports/](../../docs/bug-reports/) |
| change-log | [../../docs/change-log/](../../docs/change-log/) |

## Related

- Frontend docs: [../../wecms-web/docs/](../../wecms-web/docs/)
- JEX functional spec (read-only reference): [../../docs/jex/](../../docs/jex/)
- Project rules: [../CLAUDE.md](../CLAUDE.md) · workspace rules: [../../CLAUDE.md](../../CLAUDE.md)
