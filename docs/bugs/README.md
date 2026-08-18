# Bugs — dated implementation records

**Status:** active · **Last reviewed:** 2026-08-17

## Purpose

Dated **implementation records for bug fixes** in `wecms-api`. Each record is created **before** the
code change (docs-first), carried at `Status: IN PROGRESS` while the work is in flight, and completed
in place afterwards. This folder is **change history** — it explains *why* something changed. It does
not describe how the system currently works.

## What belongs here

A dated record for work where **existing implemented behaviour is wrong**:

- implemented behaviour is incorrect
- something that previously worked is broken
- behaviour differs from an accepted requirement
- a defect causes wrong user or system behaviour
- parity behaviour is incorrect and the task is to fix it

## What does NOT belong here

| Not this | Goes to |
|---|---|
| Non-bug issue work — behavioural, operational, configuration, or tracked implementation concerns that are not clearly a product defect | [../issues/](../issues/) |
| Current + permanent feature documentation | [../features/](../features/)`<domain>/<feature>/` |
| Audits, parity reviews, verifications, investigations, legacy comparisons, migration verifications, architecture reviews, historical validation, cross-feature analysis | [../audits/](../audits/)`<category>/` |
| Permanent architecture / run-book / security / schema / contract reference | [../architecture/](../architecture/) · [../deployment/](../deployment/) · [../security/](../security/) · [../references/](../references/) |
| Cross-system **bug reports** (intake tracker, tagged `System: JEX` / `System: Refactor`) | the shared workspace `docs/bug-reports/` — a *report*, not an implementation record |

**When classification is unclear between BUG and ISSUE, prefer ISSUE.** Never create duplicate BUG +
ISSUE records for the same implementation task. Never open a bug record just because feature work is
large — that stays on the feature documentation model.

## Filename format

```
descriptive-name-YYYY-MM-DD.md
```

Lowercase kebab-case, ISO date only, describing the actual subject — e.g.
`citad-recipient-name-resolution-fix-2026-08-17.md`,
`payment-dispatch-timeout-fix-2026-08-17.md`.

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
bug/issue template are authoritative in
[../../CLAUDE.md](../../CLAUDE.md) §"Docs-First Implementation Workflow (MANDATORY)":

```
create record (Status: IN PROGRESS) → implement → complete the SAME record
  → update permanent feature docs if behaviour changed → validate links
  → Status: COMPLETE | CLOSED | PARTIAL | BLOCKED
```

Never create a second "final" file — finalize the original. If work stalls, do not delete the record;
set `Status: BLOCKED` or `Status: PARTIAL` and say what remains.

## Human approval before implementation

**Added 2026-08-18.** A bug record is opened by docs-first — but **opening it does not authorize the
fix.** Full rule: [`../../CLAUDE.md`](../../CLAUDE.md) §17.

- **"Can you check this?" is an investigation, not a work order.** Classify the request first: *check ·
  investigate · review · audit · analyze* → document the findings and **stop for human approval**;
  *"investigate and fix"* or *"please fix this"* → implementation is already authorized. **When it is
  ambiguous, treat it as investigation-only.**
- **An investigation-only bug report is normally filed as an AUDIT** in [`../audits/`](../audits/) until
  a human approves the fix; the bug record here is then created for the approved implementation and
  **links to the audit as evidence** (§17i).
- **Record the decision, not an assumption.** `APPROVE IMPLEMENTATION` · `DENY` ·
  `NEEDS MORE INVESTIGATION` come from a person. *"OK"* / *"thanks"* / *"looks good"* / *"done"* are
  **not** approval.
- **Three status dimensions** sit in the record metadata alongside the canonical `Status:` field:

  ```
  Investigation Status:  IN PROGRESS | COMPLETE | BLOCKED
  Approval Status:       PENDING | APPROVED | DENIED | NEEDS MORE INVESTIGATION | NOT REQUIRED
  Implementation Status: NOT STARTED | IN PROGRESS | COMPLETE | BLOCKED | WON'T FIX
  ```

  `Approval Status: NOT REQUIRED` is the honest value for a bug the user explicitly asked you to fix.
- **If implementation is denied**, keep the record: no source change, no tests, no DB work — write the
  decision down, preserve the evidence, stop. **Never delete a record because the fix was denied.**
- **Approving the fix is not approving the SQL** (§17m) and not approving a push (§16) — those stay
  separate human decisions.

## Verification — a fix is not done when the code is written

**Added 2026-08-18.** Every bug record passes the **implementation quality gate** before its status
leaves `IN PROGRESS`. Authoritative rule: [`../../CLAUDE.md`](../../CLAUDE.md) §15.

```
create record → implement → self-audit the diff → tests → test scenarios
  → regression review → DB / API / security / parity checks as applicable
  → update docs → record the evidence → COMPLETE
```

**Regression-test rule for bug fixes.** A bug fix normally carries a regression test whenever the
behaviour can be tested automatically. Answer the four questions explicitly: can the bug be reproduced
in a test · does it fail before the fix · does it pass after · does it protect against recurrence?
Record it:

```markdown
## Regression Test

Test file:          src/test/java/com/kosign/wecms/domain/<...>/<Name>Test.java
Test case:          <method / @DisplayName>
Behavior protected: <the exact behaviour that must never regress>
```

If an automated regression test is genuinely impractical — it needs a live bank gateway, a real
database, a browser, or an environment this repo cannot reach — **say why** and give manual /
test-scenario evidence instead. **"No test needed" with no justification is not acceptable.** Do not
invent test infrastructure this repository lacks (no H2, no embedded DB, no Testcontainers); use the
established query-contract idiom or manual evidence.

**Every record ends with a `## Verification` section** — Automated Tests · Test Scenarios · Regression
Review · Manual Verification, applicable subsections only, with honest states: `PASS` · `FAIL` ·
`NOT RUN` · `BLOCKED` · `NOT APPLICABLE`. **Never mark `PASS` without evidence, and never convert a
planned test case into a test result.** Failing tests are classified (caused by this change ·
pre-existing · environment · test data · unresolved), never hidden — a failure caused by this change
means the bug fix is **NOT COMPLETE**.

**When the fix changes permanent behaviour**, update the affected feature's
`../features/<domain>/<feature>/test-scenarios.md` — permanent scenarios live there, and this record
keeps only the evidence for this change. Do not duplicate a long scenario suite in both places.

**Once verification actually runs, the durable evidence gets its own record** *(added 2026-08-18)* at
[`../testing/results/`](../testing/results/)`descriptive-name-test-result-YYYY-MM-DD.md`, and this bug
record gains:

```markdown
## Test Evidence

Test result:
[<Change Name> Test Result](../testing/results/<name>-test-result-YYYY-MM-DD.md)

Final test result:    PASS
Regression coverage:  PASS
```

**Both links must resolve** — the result record's `**Related Record:**` points back here. `##
Verification` above stays the **summary**; the commands, counts, environment, build under test, and
pre-existing-vs-new failures live in the result record. The test-result record answers the
regression question explicitly (**YES / NO** + detail). **`Implementation Status: COMPLETE` requires
test evidence that supports it** — if tests fail, the honest pair is `Test Status: FAIL` +
`BLOCKED` / `PARTIAL`. Full rule: [`../../CLAUDE.md`](../../CLAUDE.md) §18 · folder rules:
[`../testing/results/README.md`](../testing/results/README.md).

This applies to **every team — Cambodia, Vietnam, Korea, and any future contributor** — and to every
tool used to write the fix.

## Database impact

Every record declares **`## Database Impact`** while still at `Status: IN PROGRESS` — one of `NONE` ·
`FLYWAY MIGRATION` · `MANUAL DBA SCRIPT` · `BOTH` · `UNRESOLVED — DBA REVIEW REQUIRED`. Never vague.

| Change | Artifact |
|---|---|
| **New table** · **new DB function** | `src/main/resources/db/migration/V<n>__<name>.sql` (schema-of-record; DBA-applied by hand — **Flyway is disabled**) |
| **ALTER an existing table** · index · constraint · existing-function replacement · **any data change** (insert / update / delete, seed, reference data, backfill, corrective SQL) | a reviewed manual script in [`../sql/`](../sql/)`<YYYY>/descriptive-name-YYYY-MM-DD.sql` |

**No SQL inside this Markdown.** The bug record explains *why*; the `.sql` states *what the DBA
executes*. Link both directions.

When the artifact is a manual script, the record also carries a **`## Database Execution Status`** table
(LOCAL / SIT / UAT / PROD × required · executor · status · date · verification) — and no environment is
marked `COMPLETE` without evidence. **Preparing a script is not executing it**; execution is a separate
operational action by the DBA team, an authorized developer, or the deployment/operator team depending on
environment. An AI assistant never executes shared-environment SQL.

If a required DB execution is still pending, the bug is **not** `COMPLETE` — use `PARTIAL` / `BLOCKED` /
`READY FOR DBA`. Destructive or data-risk changes carry
**`DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED`** in both the record and the script.

Full rule: [`../../CLAUDE.md`](../../CLAUDE.md) §14 · folder rules: [`../sql/README.md`](../sql/README.md).

## Relationship to `features/`

`bugs/` and [../features/](../features/) answer different questions and both stay:

| | This folder | [../features/](../features/) |
|---|---|---|
| Answers | *why* it changed — problem, root cause, implementation, testing, history | *how the feature works now* |
| Lifecycle | frozen once complete | kept current |

After a fix, update the affected feature's permanent docs if current behaviour changed.

## Relationship to `audits/`

An audit is **never** converted into an implementation record. If an audit in
[../audits/](../audits/) leads to a code fix, the audit stays where it is and a **separate** record is
created here (or in [../issues/](../issues/)).

## Cross-repository

A bug affecting both sides gets a record in **both** repositories before either side's code changes —
this folder and `wecms-web/docs/bugs/` — using the same descriptive subject and date where
appropriate, cross-linked. This side covers backend behaviour, business rules, API, database /
integration, and backend tests.

## Existing historical records

This folder was added 2026-08-17 for **future** work. Older bug documentation elsewhere in the tree is
**not** migrated automatically.
