# wecms-api-docs — Documentation Rules

Content conventions for maintaining `wecms-api/docs/`. Read alongside `api-doc-structure.md`.

## Core principles

- **Doc-only.** Never touch application source. Never deploy. This tree is local R&D.
- **In-place by default.** Do not move or rename existing files unless explicitly asked. Reorganization is a separate, confirmed task.
- **Reuse over create.** A new file is the last resort. First try: extend an existing doc, add a section, or cross-link. **One carve-out:** the dated implementation record that docs-first requires (see below) is **always a new file** — never folded into an existing doc, and never skipped because an authoritative feature doc already exists.
- **Preserve content.** Never delete prose, tables, decisions, or open questions without confirmation. Mark superseded content as such rather than removing it. A dated implementation record is never deleted when work stalls — it moves to `Status: BLOCKED` or `Status: PARTIAL`.

## Docs-first implementation records

**Adopted 2026-08-17.** A meaningful behaviour-changing task (bug fix · issue · feature · audit /
parity review / investigation · functional behaviour / business-rule / API / integration change ·
legacy-JEX parity resolution · functional refactor · SIT/UAT/PROD behaviour fix) gets its dated
Markdown record **created before the detailed investigation begins — and therefore before any application code is modified**, at `Status: IN PROGRESS`, and
completed in place afterwards.

> **HARD PRECONDITION — `NO DOC FILE → NO DETAILED INVESTIGATION → NO IMPLEMENTATION`.**
> Added 2026-08-18 after a governance test failed: a *"can you check this error?"* request produced a
> full technical investigation and no record. **Orientation** — reading enough to classify the task and
> place its record — is allowed before the file exists. **Investigation** — tracing the fault, following
> the call chain, querying the DB, comparing against legacy, forming a root cause — is not. Create the
> record, **verify it exists**, report its path, and only then investigate; if it cannot be created,
> stop and report `DOCS-FIRST BLOCKED`. Never defer it with *"I'll document once I understand the
> issue"* — an unknown root cause is written as `Pending investigation`. Full rule:
> `wecms-api/CLAUDE.md` §2a.

**This is a project-wide TEAM rule, binding every developer and every tool** — manual work, Claude
Code, Cursor, other AI assistants, IDE tooling, scripts, pair programming — on every branch and
environment including PROD/UAT hotfixes. **One task → one canonical record per repository**, owned by
the task rather than by a developer: check for an existing record before adding another, update it when
the scope matches, and merge documentation conflicts like source code rather than dropping someone
else's updates.

**Authoritative rule + both templates: `wecms-api/CLAUDE.md` §"Docs-First Implementation Workflow
(MANDATORY)".** Content conventions specific to writing the record:

- **Wording before implementation** — use `PLANNED` · `PROPOSED` · `EXPECTED` · `TO BE IMPLEMENTED` ·
  `PENDING VERIFICATION`. Do **not** write "the system now does X" until it is verified. After
  implementation, rewrite to CURRENT facts.
- **Never present** planned behaviour as implemented · legacy JEX behaviour as current refactor
  behaviour · a test plan as a successful test result · an assumed root cause as a verified root cause.
- **Finalize the same file** — never a second "final" record. Keep planning history when it explains a
  design decision, but label **Initial plan** vs **Actual implementation** distinctly.
- **Parity records** must distinguish legacy JEX behaviour · refactor behaviour before the fix · target
  parity behaviour · actual behaviour after implementation, and must record *what was actually
  compared* (JEX `file:line`, IDO/WSVC query, columns, rows) — never just "matched legacy".
- **Cross-repo (API + WEB)** work gets a record in **both** repositories before either side's code is
  modified; keep backend internals out of the frontend record and frontend component detail out of the
  backend record, and cross-link the pair. **DB artifacts belong to `wecms-api` only** — a `wecms-web`
  record states the dependency and cross-links this side; no `.sql` ever lives in `wecms-web`.
- **`## Database Impact` is mandatory** — `NONE` · `FLYWAY MIGRATION` · `MANUAL DBA SCRIPT` · `BOTH` ·
  `UNRESOLVED — DBA REVIEW REQUIRED`, decided at `IN PROGRESS`. New table / new DB function → a
  `V<n>__<name>.sql` in `src/main/resources/db/migration/` (schema-of-record; **Flyway is disabled**, so
  it is DBA-applied by hand). Existing-table change, index, constraint, or **any** data change → a
  reviewed manual script at `docs/sql/<YYYY>/descriptive-name-YYYY-MM-DD.sql`. **Never** paste
  executable SQL into the record, and never route an `ALTER`/`UPDATE` into the migration folder. Record =
  *why*; `.sql` = *what to run*, with pre/post-checks that actually prove the change, a named executor,
  and a per-environment execution table. **Preparing a script is not executing it.** Full rule:
  `wecms-api/CLAUDE.md` §14 · folder rules: `wecms-api/docs/sql/README.md`.

## Implementation quality gate — writing the verification evidence

**Added 2026-08-18.** The record is opened by docs-first and **closed by the quality gate**: a
meaningful task is not complete when the code is written, only when it has been self-audited, tested,
verified, and honestly reported. **Authoritative rule: `wecms-api/CLAUDE.md` §15.** It binds every team
(**Cambodia · Vietnam · Korea · any future contributor**) and every tool. Content conventions for
writing that evidence:

- **`## Verification` is the closing section of every BUG / ISSUE record** — `### Automated Tests` ·
  `### Test Scenarios` · `### Regression Review` · `### Manual Verification`, applicable subsections
  only. It supersedes the older single `## Test Results` heading; records already using
  `## Test Results` keep theirs.
- **Result vocabulary is fixed:** `PASS` · `FAIL` · `NOT RUN` · `BLOCKED` · `NOT APPLICABLE`. Write the
  reason next to the non-PASS states (`NOT RUN — environment unavailable`,
  `BLOCKED — DBA script not applied`). **Never write `PASS` without evidence**, and never restate a
  planned test case as a result — the same rule as *"never present a test plan as a successful test
  result"* above.
- **State what was actually run**, not "tests pass": the command
  (`./gradlew test --tests "com.kosign.wecms.domain.<module>.*"`) and the observed counts.
- **A bug record carries a `## Regression Test` block** — test file · test case · behaviour protected —
  or an explicit justification for why an automated regression test is impractical, plus manual
  evidence. *"No test needed"* alone is not acceptable.
- **Do not document test infrastructure this repository does not have** — no H2, no embedded database,
  no Testcontainers. Database-shaped guarantees are documented as **query-contract tests** or as manual
  verification with evidence.
- **Failing tests are documented, not omitted**, and classified: caused by this change (the task is
  **NOT COMPLETE**) · pre-existing (with the evidence that it predates the work) · environment ·
  test data · unresolved.
- **Permanent vs dated:** scenarios describing how the feature behaves **now** go to
  `docs/features/<domain>/<feature>/test-scenarios.md`; the dated record keeps only this change's
  verification evidence. **Never duplicate a long scenario suite in both.**
- **Record only the checks that apply** — API contract · UI-facing payload · database (§14) · legacy
  parity (naming what was compared) · security & permissions · performance & concurrency · error
  handling. A page of "N/A" lines is boilerplate, not verification.
- **Final `Status:` must be honest** — `COMPLETE` only when every required part is done; otherwise
  `READY FOR MANUAL COMMIT` · `PARTIAL` · `BLOCKED` · `READY FOR DBA` · `READY FOR SIT` ·
  `READY FOR DEPLOYMENT`.
- **Cross-repo work is not complete because one side is green** — each record states its own side's
  verification and cross-links the other.

## Human approval gate — writing an investigation record

**Added 2026-08-18.** Authoritative rule: `wecms-api/CLAUDE.md` §17. Content conventions that follow
from it:

- **An investigation record ends at the gate, not at a fix.** Close it with the three dimensions:

  ```
  Investigation Status:  COMPLETE
  Approval Status:       PENDING
  Implementation Status: NOT STARTED
  ```

  That is a **valid final state** for a *"can you check this?"* request — `Audit: COMPLETE` never means
  the defect is fixed.
- **`## Recommended Implementation` is a recommendation, not a licence.** Write the proposed fix in full
  — file, approach, risk — and leave the code untouched.
- **The three dimensions are metadata, not a second vocabulary.** The record keeps its canonical
  `**Status:**` field; map the dimensions onto it per §17k. `Approval Status: NOT REQUIRED` is the
  honest value when the user's own request carried the decision.
- **Never write an approval a person did not give.** *"OK"* · *"thanks"* · *"looks good"* · *"done"* are
  not approval. This is the same honesty rule as never writing `PASS` for an unrun test.
- **A denied record keeps everything** — set `Approval Status: DENIED`, record *who decided and why*,
  preserve the full investigation, and never delete the file.
- **Investigation sections are risk-based like everything else**: use the headings that apply
  (`## Observed Behavior`, `## Root Cause`, `## Evidence`, `## Missing Regression Coverage`,
  `## Recommended Implementation`, `## Risks`, `## Affected Repositories`, …) and omit the rest. An
  empty heading is noise, not thoroughness.
- **An approved fix gets its own record** in `docs/bugs/` / `docs/issues/` that **links to the audit** —
  do not duplicate the audit's body into it.

## Test-result records — writing the execution evidence

**Added 2026-08-18.** Authoritative rule: `wecms-api/CLAUDE.md` §18. Content conventions:

- **Location and filename:** `docs/testing/results/descriptive-name-test-result-YYYY-MM-DD.md` —
  subject first, lowercase kebab-case, ISO date; internal IDs in `**Issue:**`, never the filename.
  Never under `docs/bugs/`, `docs/issues/`, or `docs/audits/`.
- **The record is written from an execution, not from a plan.** Never author a `PASS` result before the
  run; never write `PASS` for a check that was not executed. Non-PASS states carry their reason
  (`NOT RUN — browser environment unavailable`, `BLOCKED — DBA script not applied`).
- **Record what was actually run** — the command (`./gradlew test --tests "…"`), the observed counts,
  the environment, and the build under test. `Commit: NOT COMMITTED` is valid and expected, since the
  workflow ends at `READY FOR MANUAL COMMIT`.
- **Separate `## Existing Failures` from `## New Failures`.** Pre-existing failures are declared with
  the evidence that they predate the work; a new failure means the task is not complete.
- **`## Not Tested` is a required honesty section** when relevant checks were skipped — say which, and
  why.
- **Bug records answer the regression question in words**: *did a regression test cover the original
  defect? YES/NO*, with test file, test case, and result — or the reason it is impractical.
- **Link both ways**: the implementation record's `## Test Evidence` → the result; the result's
  `**Related Record:**` → the implementation record. Do not copy the result document into the record,
  and do not copy the record's narrative into the result.
- **Do not confuse the three documents:** `test-scenarios.md` specifies, the result record evidences,
  the bug/issue/feature record explains. Do not restate a permanent scenario suite in a result record —
  list only the scenarios actually executed.
- **Cross-repo:** each repository writes its own result record for its own side; cross-link for
  integration behaviour rather than duplicating evidence.
- **Never back-fill history** — this applies to new and in-flight work only.

## Manual Git & deployment — the record ends at the handover point

**Added 2026-08-18.** Authoritative rule: `wecms-api/CLAUDE.md` §16. Documentation conventions that
follow from it:

```
QUALITY GATE COMPLETE  →  READY FOR MANUAL COMMIT  →  HUMAN Git / deployment process
```

- **`READY FOR MANUAL COMMIT` is the normal terminal `Status:` for AI-assisted work** — the change is
  complete and verified, and is waiting for a human to review, stage, commit, and push.
- **Never write `PUSHED` · `MERGED` · `DEPLOYED` · `SIT COMPLETE` · `UAT COMPLETE` · `PROD COMPLETE`**
  into a record unless the responsible team actually performed and verified that action. An action
  nobody performed is not a status — the same rule as "never write `PASS` without evidence".
- **Committing, pushing, merging, rebasing onto a shared branch, tagging, deploying, and promoting
  between environments are manual developer / team actions**, never something a documentation task or an
  AI assistant does on its own initiative. Read-only `git status` / `git diff` / `git log` inspection is
  expected and is not covered by that prohibition.
- **Do not infer permission** from *"done"*, *"finish it"*, *"deploy-ready"*, *"complete"*, or *"ready to
  merge"*; only an explicit request for one named operation, in the current conversation, authorizes it —
  and **SIT / UAT / PROD deployment stays manual regardless**.
- This binds every team — **Cambodia · Vietnam · Korea · future contributors** — and every tool.

## Naming

- **Dated implementation / change records** use `descriptive-name-YYYY-MM-DD.md` — lowercase
  kebab-case, ISO date only, subject-descriptive (e.g. `account-filter-parity-fix-2026-08-17.md`,
  `payment-dispatch-timeout-fix-2026-08-17.md`). **Never** a work-code, sequence, or vague filename:
  `a1-01-account-filter-fix.md`, `d1-d2-d3-check.md`, `k9-fix.md`, `p0-fix.md`, `p1-update.md`,
  `th-01-verification.md`, `task-03.md`, `issue-04.md`, `final-fix.md`, `new-feature.md`. **A developer
  name is equally forbidden** (`dev-a-fix.md`, `sokny-fix.md`, `mr-kang-update.md`) — one task, one
  canonical record, named after the subject rather than the person or session. Internal
  identifiers (`A1-01`, `K9`, `TH-01`, `P0`, `Task-03`, …) belong in the document metadata as
  `**Issue:**`, not the filename — keep one in a filename only when the exact identifier is an
  externally fixed contract, vendor specification, or machine-consumed name.
- Dates are ISO `YYYY-MM-DD` everywhere. Never `20260817`, `17-08-2026`, or `08-17-2026`. Never a fake
  date.
- All new identifier surfaces use **kebab-case slugs**: feature folders, doc filenames, REST URIs, i18n key prefixes, log tags. Example: `firm-banking-settings.md`, not `comm_0220_001.md`.
- Legacy JEX WSVC / menu ids are allowed **only** as:
  1. JEX `file:line` citations that point at the source-of-truth implementation, and
  2. migration-traceability prose (e.g. "migrated from `pymn_0010_002`").
- **Feature slugs must match across systems** (JEX `docs/jex/features/`, backend `wecms-api/docs/features/`, frontend `wecms-web/docs/features/`) for traceability.

## Feature-first structure

- Backend docs are organized by **feature domain**: `auth`, `common`, `banking`, `payment`, `addon-service`, `settings`.
- A feature doc belongs under `wecms-api/docs/features/{domain}/`.
- Each feature doc should cover: purpose, JEX source-of-truth pointers (file:line), entities/repositories/services involved, REST endpoints (link to `api-spec`/`references/api-spec`), database tables (link to `references/database`), business rules, validation, and open questions.
- Keep API contract detail (request/response shapes, DTO fields, error codes) in the api-spec docs and link to it from the feature doc — do not duplicate full payload tables in two places.

## Database & ERD references

- Schema, table-mapping, indexing, and coverage live under `wecms-api/docs/references/database/`.
- ERDs live under `wecms-api/docs/references/database/erd/` (per-domain `{domain}-erd.md` plus `full-project-erd.md`).
- When a feature touches new tables, cross-link the feature doc → the relevant database reference and ERD. Do not redraw schema inside the feature doc.

## Open questions (OQ)

- OQ items live under `wecms-api/docs/planning/open-questions/` (e.g. `documentation-follow-up.md`, `database-follow-up.md`).
- **OQ-DOC** = documentation open questions. **OQ-DB** = database open questions.
- You may **add** OQ items and **update** their status to "answered — pending confirmation."
- **Never close or delete an OQ-DOC or OQ-DB item without explicit user confirmation.** The human resolves them. If your edit answers an OQ, note the answer next to the item and flag it for confirmation in your final report.

## Cross-linking

- Use relative markdown links between docs.
- When you add a doc, link it from the nearest `README.md` index and from any sibling/parent doc that should reference it.
- When you edit a doc that other docs reference, check those back-references still make sense.

## Link validation

**This is the established `wecms-api` documentation-link validation workflow.** There is no committed
link-checker script in this repository (the frontend's `node scripts/check-doc-links.mjs` covers
`wecms-web` only), so it is a deliberate manual pass. After every edit:
- Verify each relative link in touched files points to an existing file.
- Where practical, confirm heading anchors exist for `#anchor` links.
- Verify moved / renamed doc references still resolve, and cross-repo links where relevant.
- Report broken or suspicious links; fix the ones you introduced. **Expected result: 0 broken links.**

For an implementation task this pass runs **before** the work is declared complete, alongside the rest
of the docs-first Definition of Done (`wecms-api/CLAUDE.md` §3) and the quality gate
(`wecms-api/CLAUDE.md` §15t).

## Final report (always)

Close every task with:
- Files **created** (full paths).
- Files **edited** (full paths + one-line summary of the change).
- Cross-links added or updated.
- OQ items touched (and any flagged for confirmation).
- Link-validation result (clean / list of issues).
- For an implementation task: the dated record's path, its final `Status:`, and the verification
  evidence recorded — what was run, and what is `NOT RUN` / `BLOCKED` and why.
- **The handover, not a shipment:** close at `READY FOR MANUAL COMMIT`. Never commit, push, merge, tag,
  or deploy as part of finishing the task.
