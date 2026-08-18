# Issues — dated implementation records (non-bug)

**Status:** active · **Last reviewed:** 2026-08-17

## Purpose

Dated **implementation records for issue work that is not a bug** in `wecms-api`. Each record is
created **before** the code change (docs-first), carried at `Status: IN PROGRESS` while the work is in
flight, and completed in place afterwards. This folder is **change history** — it explains *why*
something changed. It does not describe how the system currently works, and it is not a place for
specifications.

## What belongs here

- the problem requires implementation work but is not clearly a product defect
- operational or configuration behaviour needs correcting
- an unresolved implementation concern is being addressed
- a tracked behaviour/problem that does not fit BUG cleanly

**When classification is unclear between BUG and ISSUE, prefer ISSUE.**

## What does NOT belong here

| Not this | Goes to |
|---|---|
| A defect — implemented behaviour is wrong, something broke, behaviour differs from an accepted requirement, parity behaviour is wrong and the task is to fix it | [../bugs/](../bugs/) |
| **Feature specifications** and current + permanent feature documentation | [../features/](../features/)`<domain>/<feature>/` |
| Audits, parity reviews, verifications, investigations, legacy comparisons, migration verifications, architecture reviews, historical validation, cross-feature analysis | [../audits/](../audits/)`<category>/` |
| Unresolved architecture / business **questions** awaiting a human decision (OQ-DOC / OQ-DB) | [../planning/open-questions/](../planning/open-questions/) |
| Permanent architecture / run-book / security / schema / contract reference | [../architecture/](../architecture/) · [../deployment/](../deployment/) · [../security/](../security/) · [../references/](../references/) |

Never create duplicate BUG + ISSUE records for the same implementation task. Never open an issue record
just because feature work is large — that stays on the feature documentation model.

## Filename format

```
descriptive-name-YYYY-MM-DD.md
```

Lowercase kebab-case, ISO date only, describing the actual subject — e.g.
`uat-payment-result-handling-2026-08-17.md`,
`environment-configuration-alignment-2026-08-17.md`.

Never a work-code or sequence filename (`a1-01-…`, `k9-fix.md`, `p1-update.md`, `task-03.md`,
`final-fix.md`). Internal identifiers (`A1-01`, `K9`, `TH-01`, `P0`, `Task-03`, …) go inside the
document metadata as `**Issue:**`.

## Docs-first requirement

**This applies to every developer on the team** — manual work, Claude Code, Cursor, another AI
assistant, IDE tooling, scripts, pair programming — on every branch and environment, **PROD/UAT hotfixes
included**. The developer who starts the change owns getting the record in place. **One task → one
canonical record per repository:** if a colleague already opened a record for this task, update theirs
rather than adding a per-person copy, and merge documentation conflicts like source code.

The record is created **before** application code is modified, and the task is not complete until the
record is finished. Order, Definition of Done, the team rule (§12 "Who must follow this"), and the
templates are authoritative in
[../../CLAUDE.md](../../CLAUDE.md) §"Docs-First Implementation Workflow (MANDATORY)":

```
create record (Status: IN PROGRESS) → implement → complete the SAME record
  → update permanent feature / reference docs where necessary → validate links
  → Status: COMPLETE | CLOSED | PARTIAL | BLOCKED
```

Never create a second "final" file — finalize the original. If work stalls, do not delete the record;
set `Status: BLOCKED` or `Status: PARTIAL` and say what remains.

## Human approval before implementation

**Added 2026-08-18.** An issue record is opened by docs-first — but **opening it does not authorize the
change.** Full rule: [`../../CLAUDE.md`](../../CLAUDE.md) §17.

- **Classify the request first.** *Check · investigate · review · audit · analyze* → document the
  findings and **stop for human approval**; *"investigate and fix"* or *"please implement this"* →
  already authorized. **When it is ambiguous, treat it as investigation-only.**
- **An investigation with no approval yet is normally an AUDIT** in [`../audits/`](../audits/); the issue
  record here is created for the approved work and **links to the audit as evidence** (§17i).
- **Record the decision, not an assumption.** `APPROVE IMPLEMENTATION` · `DENY` ·
  `NEEDS MORE INVESTIGATION` come from a person. *"OK"* / *"thanks"* / *"looks good"* / *"done"* are
  **not** approval.
- **Three status dimensions** sit in the record metadata alongside the canonical `Status:` field:

  ```
  Investigation Status:  IN PROGRESS | COMPLETE | BLOCKED
  Approval Status:       PENDING | APPROVED | DENIED | NEEDS MORE INVESTIGATION | NOT REQUIRED
  Implementation Status: NOT STARTED | IN PROGRESS | COMPLETE | BLOCKED | WON'T FIX
  ```

  `Approval Status: NOT REQUIRED` is the honest value for work the user explicitly asked you to do.
- **If the change is denied**, keep the record: no source change, no tests, no DB work — write the
  decision down, preserve the evidence, stop. **Never delete a record because the work was denied.**
  Operational and configuration issues are denied more often than defects; the recorded *"we decided to
  keep the current behaviour, and why"* is exactly what the next person needs.
- **Approving the change is not approving the SQL** (§17m) and not approving a push (§16) — those stay
  separate human decisions.

## Verification — the work is not done when the code is written

**Added 2026-08-18.** Every issue record passes the **implementation quality gate** before its status
leaves `IN PROGRESS`. Authoritative rule: [`../../CLAUDE.md`](../../CLAUDE.md) §15.

```
create record → implement → self-audit the diff → tests → test scenarios
  → regression review → DB / API / security / parity checks as applicable
  → update docs → record the evidence → COMPLETE
```

- **Self-audit the actual Git diff**, not the ticket text — scope creep, unrelated changes, debug
  logging, hard-coded values, broken tenant / permission scope, secrets (§15b, §15s).
- **Decide the appropriate automated tests and add them where in scope** (§15c). Issue work that
  corrects an observable behaviour follows the same regression-test thinking as a bug fix (§15d); issue
  work that is purely operational or configuration may legitimately be `NOT APPLICABLE` — say so and
  say why, rather than leaving it blank.
- **Run the smallest relevant test scope first**, consider a broader regression run before completion,
  and **report exactly what was run** (§15i). Classify failures rather than hiding them (§15j).
- **Update the affected feature's `../features/<domain>/<feature>/test-scenarios.md`** when permanent
  behaviour changed; this record keeps only the evidence for this change (§15f).

**Every record ends with a `## Verification` section** — Automated Tests · Test Scenarios · Regression
Review · Manual Verification, applicable subsections only, with honest states: `PASS` · `FAIL` ·
`NOT RUN` · `BLOCKED` · `NOT APPLICABLE`. **Never mark `PASS` without evidence, and never convert a
planned test case into a test result.** Configuration and environment work is especially prone to
`BLOCKED` and `NOT RUN` — record those states honestly instead of implying verification happened.

**Once verification actually runs, the durable evidence gets its own record** *(added 2026-08-18)* at
[`../testing/results/`](../testing/results/)`descriptive-name-test-result-YYYY-MM-DD.md`, and this issue
record gains:

```markdown
## Test Evidence

Test result:
[<Change Name> Test Result](../testing/results/<name>-test-result-YYYY-MM-DD.md)

Final test result:    PASS
Regression coverage:  PASS | NOT APPLICABLE — <reason>
```

**Both links must resolve** — the result record's `**Related Record:**` points back here. `##
Verification` above stays the **summary**; the commands, counts, environment, build under test, and
pre-existing-vs-new failures live in the result record. Configuration and operational issue work is
often verified manually or in one environment only — say exactly that (`NOT RUN` / `BLOCKED` with a
reason) rather than leaving it implied. **`Implementation Status: COMPLETE` requires test evidence that
supports it.** Full rule: [`../../CLAUDE.md`](../../CLAUDE.md) §18 · folder rules:
[`../testing/results/README.md`](../testing/results/README.md).

This applies to **every team — Cambodia, Vietnam, Korea, and any future contributor** — and to every
tool used to do the work.

## Database impact

Every record declares **`## Database Impact`** while still at `Status: IN PROGRESS` — one of `NONE` ·
`FLYWAY MIGRATION` · `MANUAL DBA SCRIPT` · `BOTH` · `UNRESOLVED — DBA REVIEW REQUIRED`. Never vague.
Configuration- and reference-data corrections — a common shape for issue work — are **`MANUAL DBA
SCRIPT`**, not migration-folder files.

| Change | Artifact |
|---|---|
| **New table** · **new DB function** | `src/main/resources/db/migration/V<n>__<name>.sql` (schema-of-record; DBA-applied by hand — **Flyway is disabled**) |
| **ALTER an existing table** · index · constraint · existing-function replacement · **any data change** (insert / update / delete, seed, reference/config data, backfill, corrective SQL) | a reviewed manual script in [`../sql/`](../sql/)`<YYYY>/descriptive-name-YYYY-MM-DD.sql` |

**No SQL inside this Markdown.** The issue record explains *why*; the `.sql` states *what the DBA
executes*. Link both directions.

When the artifact is a manual script, the record also carries a **`## Database Execution Status`** table
(LOCAL / SIT / UAT / PROD × required · executor · status · date · verification) — and no environment is
marked `COMPLETE` without evidence. **Preparing a script is not executing it**; execution is a separate
operational action by the DBA team, an authorized developer, or the deployment/operator team depending on
environment. An AI assistant never executes shared-environment SQL.

If a required DB execution is still pending, the issue is **not** `COMPLETE` — use `PARTIAL` / `BLOCKED` /
`READY FOR DBA`. Destructive or data-risk changes carry
**`DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED`** in both the record and the script.

Full rule: [`../../CLAUDE.md`](../../CLAUDE.md) §14 · folder rules: [`../sql/README.md`](../sql/README.md).

## Relationship to `features/`

`issues/` and [../features/](../features/) answer different questions and both stay:

| | This folder | [../features/](../features/) |
|---|---|---|
| Answers | *why* it changed — issue, current state, target state, implementation, testing, history | *how the feature works now* |
| Lifecycle | frozen once complete | kept current |

After the work lands, update the affected permanent feature / reference docs if current behaviour
changed.

## Relationship to `audits/`

An audit is **never** converted into an implementation record. If an audit in
[../audits/](../audits/) leads to a code change, the audit stays where it is and a **separate** record
is created here (or in [../bugs/](../bugs/)).

## Cross-repository

An issue affecting both sides gets a record in **both** repositories before either side's code
changes — this folder and `wecms-web/docs/issues/` — using the same descriptive subject and date where
appropriate, cross-linked. This side covers backend behaviour, business rules, API, database /
integration, and backend tests.

## Existing historical records

This folder was added 2026-08-17 for **future** work. Older issue documentation elsewhere in the tree
is **not** migrated automatically.
