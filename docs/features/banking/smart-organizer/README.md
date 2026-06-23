# Banking > Smart Organizer — Refactor Backend Feature Hub

**Module:** Banking → Current & Savings (VND **and** FCY — one screen, both currencies)
**Menu:** Smart Organizer (reached from the Transaction History toolbar; no standalone menu seed)
**Refactor feature slug (matches JEX + frontend):** `smart-organizer`
**View Setting `WEB_SVC_ID`:** `smart-organizer` (V174 seed)
**Menu permission:** `banking-vnd-inquire-transactions` **OR** `banking-fcy-inquire-transactions` (`@menuAuthz.canAccessAny(...)`)
**REST base:** `/api/v1/banking/smart-organizer` (eligibility list + confirm) and `/api/v1/banking/smart-organizer/rules` (Organizer Settings rule CRUD)
**JEX counterpart:** `fnsh_0200_001` (eligibility list + confirm) + `fnsh_0200_002` (Organizer Settings popup) — [docs/jex/features/smart-organizer/](../../../../../docs/jex/features/smart-organizer/README.md)
**Frontend counterpart:** [wecms-web/docs/features/banking/smart-organizer/](../../../../../wecms-web/docs/features/banking/smart-organizer/README.md)

> **Status — Refactor complete (full-stack).** Eligibility listing (account-scoped, date-range,
> keyword, whitelisted sort, pagination), the rule-driven match engine, Receive/Pay classification,
> VAS-account / reporting-account / purpose / contact mapping with denormalized persistence, and the
> bulk-confirm workflow are migrated and wired end-to-end. Both VND and FCY accounts run through the
> **same** controller / service / query — currency is data-driven (`ACCT_TRNS_HSTR.CCY_CD`), never a
> code fork. Organizer Settings rule CRUD + drag-reorder is implemented in the sibling
> `OrganizerRuleController`. Legacy parity verified against `fnsh_0200_001` / `fnsh_0200_002`; the six
> SIT blockers (R01 matching, R03 confirm validation + PAY rules, R05 persistence, R06 FCY, R07 display
> names, R11 account scope) are **all CLOSED**. SIT/UAT verification completed.
> See [refactor-gap-analysis.md](refactor-gap-analysis.md), the JEX parity audit
> [docs/jex/audits/smart-organizer-parity-audit.md](../../../../../docs/jex/audits/smart-organizer-parity-audit.md),
> and the R11 verification [../../../audits/smart-organizer-r11-verification.md](../../../audits/smart-organizer-r11-verification.md).
>
> This folder is the **primary backend documentation home** for the refactored feature. The older
> single-file specs under
> [current-savings-vnd/smart-organizer.md](legacy-spec-vnd.md) and
> [current-savings-fcy/smart-organizer.md](legacy-spec-fcy.md) predate this
> folder, still carry `Needs Confirmation` markers, and are **preserved + cross-linked but superseded**
> by this folder.

JEX is **read-only functional reference**. This folder documents the refactored Spring Boot
implementation; the JEX behaviour at `fnsh_0200_001` / `fnsh_0200_002` is the functional source of truth.

---

## What this feature does

Smart Organizer is a **rule-driven transaction classifier**. For a single selected bank account it
lists the transactions that (a) have **not yet been confirmed** and (b) **match a remark rule**
(`RMRK_ORG_INFM`), pre-fills each row with the matched rule's classification proposal (Receive/Pay
direction, Reporting Account, Purpose, VAS Account, Contact), and lets the user review and
**bulk-confirm** the rows they accept. A confirmed row is written to `ACCT_TRNS_RCV_PAY_INFM` and then
disappears from the eligibility list (it is "organized").

The matching engine is **SQL, not Java**: a `LATERAL` sub-join inside the eligibility query picks the
first rule (by `DISP_ORD`) whose remark pattern matches the transaction. This mirrors the legacy
correlated sub-query in `IDO.ACCT_TRNS_HSTR_R028.xml`.

Two cooperating REST surfaces:

- **Eligibility + confirm** — `SmartOrganizerController` (`/eligible`, `/confirm`). Lists candidate
  rows and persists confirmations.
- **Organizer Settings (rule CRUD)** — `OrganizerRuleController` (`/rules`, `/rules/{id}`,
  `/rules/reorder`). Maintains the remark rules that drive matching, including drag-reorder of
  `DISP_ORD` (lowest order wins on a tie).

**VND and FCY are not a code fork.** There is no `if (FCY)` branch. The eligibility query projects
`CCY_CD` + `BAL` from `ACCT_TRNS_HSTR`; the account's own currency drives amount formatting on the
client. The only currency-specific surfaces are the two menu permissions the endpoint accepts. See
[business-flow.md §6](business-flow.md#6-vnd-and-fcy-one-path) and the FCY remediation note (R06) in
[refactor-gap-analysis.md](refactor-gap-analysis.md).

**Account scope is mandatory (R11).** Every call requires `bank_cd` + `acct_no`; a missing account is
a client error, never a silent tenant-wide fallback. Non-admin callers must hold a `V` (View) grant in
`ACCT_AUTH_INFO` for that account (`AccountAccessGuard.assertCanView`).

---

## Document map (this folder)

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Scope, eligibility rules, filters, sort whitelist, grid columns, classification fields, confirm semantics, permissions — the *what*. |
| [business-flow.md](business-flow.md) | The full lifecycle: list → match (R01) → classify (R03) → VAS map (R05) → contact map → confirm → persist; display-name resolution (R07); VND/FCY one-path; rule CRUD + reorder. |
| [database-mapping.md](database-mapping.md) | Tables + legacy-column → entity-field → DTO-field → UI-field mapping for the rule table, transaction source, and the confirmation write target; denormalization columns. |
| [interface-specification.md](interface-specification.md) | Every REST endpoint, request/response shapes, DTO field lists, wire-casing note, error codes. |
| [source-code-index.md](source-code-index.md) | Legacy JEX (`fnsh_0200_*`) ↔ refactor backend ↔ refactor frontend file index. |
| [migration-guide.md](migration-guide.md) | JEX WSVC / IDO / `_act.jsp` → Spring controller/service/repository mapping; the SQL-engine port; sequence allocation. |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (search, matching, classification, confirmation, VND/FCY, security, error handling). |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / PARTIAL / MISSING) with the special verification areas R01/R03/R05/R06/R07/R11 and evidence per closed gap. |

## Companion references (preserved — cross-linked, not duplicated)

| Doc | Location |
|---|---|
| **Legacy ↔ refactor parity audit (2026-06-06)** | [docs/jex/audits/smart-organizer-parity-audit.md](../../../../../docs/jex/audits/smart-organizer-parity-audit.md) |
| **Remediation plan (six blockers, 2026-06-06)** | [docs/jex/audits/smart-organizer-remediation-plan.md](../../../../../docs/jex/audits/smart-organizer-remediation-plan.md) |
| **R11 account-scope verification (2026-06-06)** | [../../../audits/smart-organizer-r11-verification.md](../../../audits/smart-organizer-r11-verification.md) |
| JEX functional spec (source of truth) | [docs/jex/features/smart-organizer/](../../../../../docs/jex/features/smart-organizer/README.md) |
| Older single-file backend spec (VND) | [current-savings-vnd/smart-organizer.md](legacy-spec-vnd.md) |
| Older single-file backend spec (FCY) | [current-savings-fcy/smart-organizer.md](legacy-spec-fcy.md) |
| Transaction History (the screen this launches from) | [transaction-history-vnd/README.md](../current-savings-vnd/transaction-history/README.md) · [transaction-history-fcy/README.md](../current-savings-fcy/transaction-history/README.md) |
| Banking ERD | [references/database/erd/banking-erd.md](../../../references/database/erd/banking-erd.md) |
| JEX → Spring service mapping | [references/migration/service-mapping.md](../../../references/migration/service-mapping.md) |

---

## Key backend artifacts

| Layer | Class | Path |
|---|---|---|
| Controller (eligible + confirm) | `SmartOrganizerController` | `src/main/java/com/kosign/wecms/domain/banking/smartorganizer/controller/SmartOrganizerController.java` |
| Controller (rule CRUD + reorder) | `OrganizerRuleController` | `…/smartorganizer/controller/OrganizerRuleController.java` |
| Service (eligible + confirm) | `SmartOrganizerService` | `…/smartorganizer/service/SmartOrganizerService.java` |
| Service (rule CRUD) | `OrganizerRuleService` | `…/smartorganizer/service/OrganizerRuleService.java` |
| Per-account authz guard (R11) | `AccountAccessGuard` | `…/banking/service/AccountAccessGuard.java` |
| Bank/acct-type name resolver (R07) | `BankCdResolver` | `…/banking/service/BankCdResolver.java` |
| Entity (rule) | `RmrkOrgInfm` (`@IdClass RmrkOrgInfmId`) | `…/smartorganizer/entity/RmrkOrgInfm.java` |
| Entity (transaction source) | `AcctTrnsHstr` (`@IdClass AcctTrnsHstrId`) | `…/banking/entity/AcctTrnsHstr.java` |
| Entity (confirmation target) | `AcctTrnsRcvPayInfm` (`@IdClass AcctTrnsRcvPayInfmId`) | `…/banking/entity/AcctTrnsRcvPayInfm.java` |
| Repo (rule CRUD + reorder) | `RmrkOrgInfmRepository` | `…/smartorganizer/repository/RmrkOrgInfmRepository.java` |
| Repo (confirmation lookups) | `AcctTrnsRcvPayInfmRepository` | `…/banking/repository/AcctTrnsRcvPayInfmRepository.java` |
| Repo (transaction load) | `AcctTrnsHstrRepository` | `…/banking/repository/AcctTrnsHstrRepository.java` |
| Sequence allocator (legacy bridge) | `LegacyFunctionRepository` (`wb_get_next_val`) | `…/common/legacy/LegacyFunctionRepository.java` |
| Sort whitelist | `SmartOrganizerSortField` (enum) | `…/smartorganizer/dto/SmartOrganizerSortField.java` |
| Flyway | V172 (RMRK_ORG_INFM schema) · V173 (`wb_get_next_val` rule branch) · V174 (View Setting seed) | `src/main/resources/db/migration/` |

DTOs (`…/smartorganizer/dto/`): `EligibleTransactionListResponse`, `EligibleTransactionRow`,
`ConfirmRequest` (+ nested `Row`), `ConfirmResponse`, `OrganizerRuleListResponse`, `OrganizerRuleRow`,
`OrganizerRuleSaveRequest`, `OrganizerRuleReorderRequest` (+ nested `Entry`).

---

## Migration status

```
Refactor Status: 100% Complete  (full-stack — eligibility list / rule-match engine (R01) /
                                 Receive-Pay classification (R03) / reporting-account · purpose ·
                                 VAS · contact mapping with denormalized persistence (R05) /
                                 display-name resolution (R07) / VND + FCY one path (R06) /
                                 account-scoped authorization (R11) / Organizer Settings rule CRUD +
                                 reorder / bulk confirm)
```

All six SIT blockers are **CLOSED** and source-verified; golden matching-parity test was executed on
the live PostgreSQL engine (within-tenant 5/5 identical to legacy, cross-tenant leak correctly
suppressed). Residual non-blocking items (R03 direction is user-selected rather than derived because
`BUSI_USAG_SET_INFM.USAG_GRP_CD` is unpopulated in the refactor; R07 chart breadcrumb is leaf-name
only; reorder endpoint exists but the drag-save UI is not yet wired) are tracked in
[refactor-gap-analysis.md](refactor-gap-analysis.md) §"Open items".
