# Settings > Firm Banking Settings — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend (controller + service + 3 repositories + 5 entities incl. a `@Subselect` list view + 7 DTOs, with unit + controller tests) and frontend wired (panel consumes the live list/detail/create/update/delete/audit API; legacy-parity verified for the `comm_0220` family). Create/Update/Delete landed 2026-05-09 under OQ-8a Option (c) — see the OQ log below.
**Module / menu:** Settings → Firm Banking → **Firm Banking Settings** (Settings modal panel — **no standalone route**)
**Refactor slug:** `firm-banking-settings` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire base path:** `/api/v1/settings/firm-banking-settings` (list · detail · audit · create · update · delete)
**Menu permission slug:** `settings-firm-banking-settings`
**Legacy JEX counterpart:** WSVC family `comm_0220_001` (list `_r001` / delete `_d001`), `comm_0220_002` (create `_c001`), `comm_0220_003` (detail+audit `_r001` / update `_u001`). Table `TB_FIRM_CONF`.

> This folder is the **primary documentation home** for Firm Banking Settings on the backend.
> It consolidates and indexes material that previously lived in a single flat file; that file is
> **preserved and cross-linked** (see [§ Related & preserved docs](#related--preserved-docs)), not
> deleted. The JEX-side behaviour is the read-only source of truth for legacy parity.

---

## What this feature is

Firm Banking Settings is the per-tenant master list of **firm-banking channel bindings**. Each row
binds a withdrawal account (`WDRL_ACT_NO`) to an outbound bank channel (`OUT_BANK_CD`, a NAPAS code)
under a bank-issued partner id (`BANK_PRTN_ID`) + VAN organisation code (`VAN_ORG_CD`), and tracks the
binding's operational status (`ORG_STAT`). These bindings authorise WeCMS to dispatch firm-banking
transfer instructions for that account/bank pair. A tenant administrator can:

- **List** every binding for the current `BIZ_KEY`, enriched (via the list view) with the bank name,
  currency, balance, and expiry from the joined `ACCT_MSTR` / `TB_FIRM_BANK_CODE` rows.
- **View detail + audit log** for one binding by its composite PK.
- **Create** a binding — pick the bank (`out_bank_cd`), a withdrawal account, partner id, VAN code, and
  status; `FILE_TYPE` is **server-derived** from the bank code (Shinhan→`SHB`, Vietcombank→`VCB`,
  Woori→`WRB`), never client-supplied.
- **Update** the editable fields (`bank_prtn_id`, `van_org_cd`, `org_stat`) — the PK is immutable.
- **Delete** a binding (**hard delete**, mirroring legacy — no soft-delete column, no audit row).

The frontend panel also hosts two **Automation toggles** (Auto Match `00079` / Auto Transfer `00078`).
Those toggles are **NOT part of this feature** — they target the separate Environment Settings service
(`BUSI_ENV_SET_INFM`). See [refactor-gap-analysis.md](refactor-gap-analysis.md) "Automation toggles".

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 6: GET (list) · GET `/{out_bank_cd}/{wdrl_act_no}` (detail) · GET `/{out_bank_cd}/{wdrl_act_no}/audit` (audit) · POST (create) · PUT `/{out_bank_cd}/{wdrl_act_no}` (update) · DELETE `/{out_bank_cd}/{wdrl_act_no}` |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-firm-banking-settings')")` — class-level on the controller (F-3 menu-auth model). See [interface-specification.md §Authorization](interface-specification.md). |
| Tenant scope | `TenantContext.currentBizKey()` — `bizKey` is the leading component of the composite PK; never from a request param |
| Core table | `TB_FIRM_CONF` (14 columns; composite PK `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)`) |
| List source | `@Subselect` entity `FirmBankingSettingView` over `TB_FIRM_CONF` ⨝ `ACCT_MSTR` ⨝ `TB_FIRM_BANK_CODE` — **replaces** the `V_FIRM_BANKING_SETTING` view object (per memory `project_v_firm_banking_history_subselect`) |
| Supporting table | `TB_FIRM_BANK_CODE` (read-only NAPAS/CITAD bank dictionary) — Create referential check |
| PK on Create | client-supplied composite key (no `wb_get_next_val`); duplicate → `WBE2021` |
| Delete | **hard delete** (`deleteById`); no soft-delete column; no audit row (OQ-2) |
| Audit | Create / Update write `AUDIT_LOGS_INFM` with `WSVC_ID = 'firm-banking-settings'` (slug — OQ-6 override 2026-05-09; legacy was `comm_0220_001`). The refactor writes the slug from the start, so no local rows ever carried the legacy constant (the planned "V67 back-fill" was never needed and no such migration ships). Reads + Delete write **no** audit row. |
| Sensitive field | `wdrl_act_no` stays plain-text in DB (OQ-4); the backend ships it **cleartext on every endpoint** (list + detail) — it is the PK component the frontend addresses for write flows. Masking is **frontend-only** (the `mapFirmBankingSettingRow` mapper masks the displayed string while keeping the cleartext PK) |
| Bank scope | bounded to `{970424 Shinhan, 970436 Vietcombank, 970457 Woori}` (OQ-8a Option c, provisional) — outside set → `WBE0001` |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.firmbankingsettings
├── controller/FirmBankingSettingsController.java   6 endpoints; class-level @menuAuthz + @Validated
├── service/FirmBankingSettingsService.java          list/detail/auditLog/create/update/delete; audit; bank-code guard
├── repository/
│   ├── FirmBankingSettingRepository.java            JpaRepository<TbFirmConf, TbFirmConfId> — CRUD + active-config finders
│   ├── FirmBankingSettingViewRepository.java         JpaRepository<FirmBankingSettingView, TbFirmConfId> — list (ordered)
│   └── TbFirmBankCodeRepository.java                 JpaRepository<TbFirmBankCode, TbFirmBankCodeId> — bank dictionary lookups
├── entity/
│   ├── TbFirmConf.java                               @Table(TB_FIRM_CONF), 14 cols, @IdClass
│   ├── TbFirmConfId.java                             composite PK { bizKey, outBankCd, wdrlActNo }
│   ├── TbFirmBankCode.java                           @Table(TB_FIRM_BANK_CODE), @IdClass
│   ├── TbFirmBankCodeId.java                         composite PK { antDivCd, bankCd }
│   └── FirmBankingSettingView.java                   @Immutable @Subselect (TB_FIRM_CONF ⨝ ACCT_MSTR ⨝ TB_FIRM_BANK_CODE)
└── dto/
    ├── FirmBankingSettingListResponse.java · FirmBankingSettingRow.java (18 fields, cleartext wdrl_act_no — FE masks display)
    ├── FirmBankingSettingDetail.java (14 fields, cleartext wdrl_act_no)
    ├── FirmBankingSettingCreateRequest.java · FirmBankingSettingUpdateRequest.java
    └── FirmBankingAuditEntry.java · FirmBankingAuditListResponse.java
```

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: list → controller → service → view/repo → enriched rows; detail/audit; create/update/delete; audit-write & tenant invariants |
| [functional-specification.md](functional-specification.md) | Operations matrix, field-by-field validation, bank-scope rule, hard-delete behaviour, masking, audit expectations |
| [database-mapping.md](database-mapping.md) | `TB_FIRM_CONF` + `TB_FIRM_BANK_CODE` columns, the `@Subselect` list view, and the full legacy-column → entity-field → DTO-field → UI-field mapping |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint, error-code registry, authorization, audit-write behaviour |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (`_view.jsp` / `_act.jsp` / IDO / WSVC / entity / DAO) became its Spring counterpart, incl. dropped/dead legacy flows |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios + the existing JUnit coverage map |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) + the OQ decision log |

---

## Related & preserved docs

- **Detailed phase-by-phase implementation spec** (operations matrix, field contract, OQ decisions
  OQ-1…OQ-8a + REQUIRES_NEW audit semantics): [../firm-banking-settings.md](../firm-banking-settings.md)
  — the original ~620-line spec, kept as the deep design narrative.
- **Sibling feature (closely related, separate data domain):** [../firm-banking-history/](../firm-banking-history/README.md)
  — `TB_FIRM_TRAN` transaction audit ledger; read-only. Firm Banking Settings is configuration
  (`TB_FIRM_CONF`); Firm Banking History is the operational dispatch log.
- **`@Subselect` view pattern:** memory `project_v_firm_banking_history_subselect.md` — the three
  refactor-only `V_*` views (incl. `V_FIRM_BANKING_SETTING`) replaced by Hibernate `@Subselect` over
  legacy base tables; no view object is created in the DB.
- **Table baseline:** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)
  — `TB_FIRM_CONF`, `TB_FIRM_BANK_CODE`, `ACCT_MSTR`, `AUDIT_LOGS_INFM` are all in the inventory.
- **Frontend feature folder:** [wecms-web/docs/features/settings/firm-banking-settings/](../../../../../wecms-web/docs/features/settings/firm-banking-settings/README.md)

---

## Open items / known stale comments

- **OQ-8a Option (c) is provisional.** Create restricts `out_bank_cd` to the verified 3-bank set;
  Mr. Jung (PO) may later widen it (Option a/b). When a fourth bank goes live the operational change is
  one row in the service's `BANK_TO_FILE_TYPE` map + the allow-list constant — never a re-architecture.
  See [refactor-gap-analysis.md](refactor-gap-analysis.md) OQ-8a.
- **Stale frontend comment.** The frontend API client `lib/api/firmBankingSettings.ts` Javadoc for the
  audit fetch still says `WSVC_ID='comm_0220_001'` — that is **stale**. The backend service writes and
  reads the audit rows with the slug `firm-banking-settings` (OQ-6 override, 2026-05-09). Logged as a
  non-blocking cleanup in [refactor-gap-analysis.md](refactor-gap-analysis.md).
- **`V9` referential check deferred.** `NAPAS_BANK_INFO` is not migrated locally; with the Option (c)
  3-bank reject, `V8` is strictly stronger than `V9`. Re-enable `V9` when `NAPAS_BANK_INFO` lands.
