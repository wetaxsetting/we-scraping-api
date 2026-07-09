# Firm Banking Settings — Refactor Gap Analysis (Final Parity Review)

Legacy JEX (`comm_0220_001/002/003`, `TB_FIRM_CONF`) vs. the refactor (`firm-banking-settings`). Status
vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **List** | `comm_0220_001_r001` (LEFT JOIN `ACCT_MSTR`) | `GET /firm-banking-settings` via `@Subselect` view | **MATCH** |
| **Detail** | `comm_0220_003_r001` (entity leg) | `GET /{out_bank_cd}/{wdrl_act_no}` | **MATCH** |
| **Audit log** | `comm_0220_003_r001` (audit `REC` leg, same action) | `GET …/audit` (separate REST sub-resource) | **APPROVED DEVIATION** (REST split; same filter + data) |
| **Create** | `comm_0220_002_c001` (validate, `FILE_TYPE` derive, insert, audit) | `POST /firm-banking-settings`, same flow | **MATCH** |
| **Update** | `comm_0220_003_u001` (editable fields, `LAST_EDTR`, audit) | `PUT /{out_bank_cd}/{wdrl_act_no}` | **MATCH** |
| **Delete** | `comm_0220_001_d001` (hard delete, **no** audit) | `DELETE /{out_bank_cd}/{wdrl_act_no}` (hard delete, no audit) | **MATCH** (OQ-2) |
| **`FILE_TYPE` derivation** | inline JSP branch on `OUT_BANK_CD` | server-side `BANK_TO_FILE_TYPE` map | **MATCH** |
| **Bank-name resolution** | join `TB_FIRM_BANK_CODE` (NAPAS) | same join inside `@Subselect` | **MATCH** |
| **List view object** | IDO join (no DB view) | Hibernate `@Subselect` (no `V_FIRM_BANKING_SETTING` created) | **APPROVED DEVIATION** (per `project_v_firm_banking_history_subselect`) |
| **Audit `WSVC_ID`** | `comm_0220_001` | slug `firm-banking-settings` | **APPROVED DEVIATION** (workspace slug rule; OQ-6) |
| **Audit locale rows** | 3 (EN/KO/local) | per-locale via shared `AuditLogService` | **APPROVED DEVIATION** |
| **`wdrl_act_no` exposure** | full on the wire | cleartext on both list + detail (display masking is frontend-only) | **MATCH** (wire parity; OQ-4 masking moved to the FE mapper) |
| **Bank scope** | inline Shinhan/Woori branch | bounded allow-list `{970424, 970436, 970457}` | **APPROVED DEVIATION** (V8, OQ-8a — provisional) |
| **Authorization** | menu visibility only (no explicit check) | explicit `@menuAuthz.canAccess('settings-firm-banking-settings')` | **APPROVED DEVIATION** (F-3, stronger enforcement) |
| **Approval-line integration** | none | none | **DEAD LEGACY** (OQ-7 — neither side has it) |
| **`V9` `NAPAS_BANK_INFO` check** | present (Create) | deferred (table not migrated; V8 stronger) | **PARTIAL** |
| **`V11` `ACCT_MSTR` existence check** | disabled in legacy (#42004) | kept disabled | **DEAD LEGACY** (OQ-3) |
| **`wdrl_act_no` encryption** | plain text | plain text (masking only) | **PARTIAL** (OQ-4 — project-wide encryption deferred) |
| **Automation toggles** (Auto Match / Auto Transfer) | env-set codes `00078`/`00079` | separate Environment Settings service | **APPROVED DEVIATION** (scope boundary — not part of this feature) |

---

## Specially-verified items

### Delete parity (OQ-2)

Delete is preserved **verbatim**: a physical `deleteById` with **no** audit row, mirroring
`comm_0220_001_d001_act.jsp` byte-for-byte. `TB_FIRM_CONF` has no soft-delete column on either side.
The refactor explicitly does **not** write an `AUDIT_LOGS_INFM` row on delete (the shared
`AuditLogService` is not even a dependency of the delete path). **Status: MATCH.**

### Audit-DB-constant retirement (OQ-6) — carried over from the rich flat doc

The morning default reserved `AUDIT_LOGS_INFM.WSVC_ID = 'comm_0220_001'` so the legacy JEX detail-audit
query (`comm_0220_003_r001_act.jsp:74-86`) would still find refactor-written rows. **Overridden the same
afternoon (2026-05-09):** refactor-written audit rows now carry `WSVC_ID = 'firm-banking-settings'`. The
cross-system motivation evaporated because the refactor is local-only R&D and JEX never queries the local
DB. The refactor writes the slug from the start, so no local rows ever carried the legacy constant — the
planned `V67` back-fill was never needed and no such migration ships in the live set. This is the same
retirement recorded in the workspace + `wecms-api/CLAUDE.md` "audit-DB-constant bucket **retired
2026-05-09**" — a future feature with genuinely irreducible cross-system audit needs would require a fresh
PO decision before reviving the legacy-constant bucket. **Status: APPROVED DEVIATION.**

### `@Subselect` replaces `V_FIRM_BANKING_SETTING`

The planned `V_FIRM_BANKING_SETTING` DB view is **not created**. `FirmBankingSettingView` is an
`@Immutable @Subselect` entity that projects `TB_FIRM_CONF ⨝ ACCT_MSTR ⨝ TB_FIRM_BANK_CODE` at query
time and skips schema-validate — the established pattern (memory
`project_v_firm_banking_history_subselect.md`). **Status: APPROVED DEVIATION.**

### Authorization parity

Legacy enforced admin-only access by menu visibility (the Settings → Firm Banking Settings entry is
admin-gated via `USER_MENU_AUTH_INFM`), with no explicit check in the action JSPs. The refactor enforces
the same effective boundary explicitly via `@PreAuthorize("@menuAuthz.canAccess('settings-firm-banking-settings')")`
(F-3, 2026-06-06). **Status: APPROVED DEVIATION** — same boundary, stronger enforcement.

---

## OQ decision log (carried over from the rich flat doc)

| OQ | Topic | Resolution |
|---|---|---|
| **OQ-1** | Error-code allocation | `WBE2020` not-found (Detail/Update/Delete), `WBE2021` duplicate (Create); `WBE2022` / `WBE2023` allocated but **reserved/unused** in v1. Generic `WBE0001`/`WBE0002` for V8/V10. |
| **OQ-2** | Audit on Delete? | **No** — legacy writes no audit on delete; refactor mirrors exactly. |
| **OQ-3** | Re-enable `V11` (`wdrl_act_no` ∈ `ACCT_MSTR`)? | **No** — disabled in legacy (#42004); stays disabled. |
| **OQ-4** | Encrypt `wdrl_act_no` now? | **No** — plain-text in DB; cleartext on both list + detail (PK the FE addresses for writes); display masking is frontend-only; project-wide encryption deferred. |
| **OQ-5** | Enforce `NORM_USER_ONLY_YN` list gate via `@PreAuthorize`? | **Skip in v1** — every authenticated user with the menu grant may call; wire `userType` later in an auth-hardening pass. |
| **OQ-6** | Audit `WSVC_ID` legacy vs slug + REQUIRES_NEW | **Slug `firm-banking-settings`** (override 2026-05-09; slug written from the start, no back-fill migration ships). Audit write is `REQUIRES_NEW` inside the shared writer. |
| **OQ-7** | Approval-line in refactor? | **Parked** — JEX has none; v1 preserves immediate writes. A future approval gate is a new feature. |
| **OQ-8** | `out_bank_cd` values in DEV | **3 verified:** `970424` Shinhan (10 rows), `970436` Vietcombank (1), `970457` Woori (4). |
| **OQ-8a** | Bank codes outside the verified set | **Option (c): bounded set + strict reject** (provisional; Mr. Jung may override). Anything outside `{970424,970436,970457}` → `WBE0001`. `FILE_TYPE` server-derived. Adding a 4th bank = one map + one allow-list entry. |

---

## Migration status

```
Refactor Status: 100% Complete (for the in-scope feature surface)
```

Evidence for closed gaps:

- **All 6 endpoints implemented** (controller verified) with unit + controller tests green — see
  [test-scenarios.md §2](test-scenarios.md).
- **Hard-delete + no-audit-on-delete** verified by parity reading against `comm_0220_001_d001`.
- **Tenant scoping + cleartext-PK-on-the-wire** verified by the service test
  (`list_passesTenantBizKeyToRepo_andShipsCleartextWdrlActNoAsThePkComponent` — the list ships the
  cleartext PK; masking is the frontend mapper's job).
- **Slug `firm-banking-settings` + `settings-firm-banking-settings`** used end-to-end (URI, package,
  audit `WSVC_ID`, menu grant).

Residual (non-blocking, tracked):

1. **OQ-8a Option (c) provisional** — PO (Mr. Jung) may widen the bank set.
2. **`V9` deferred** — re-enable when `NAPAS_BANK_INFO` is migrated locally.
3. **Stale frontend comment** — the frontend API client's audit-fetch Javadoc still says
   `WSVC_ID='comm_0220_001'`; the backend reads/writes with the slug `firm-banking-settings`. One-line
   Javadoc cleanup in the next stabilisation pass. Does not affect behaviour.
4. **`wdrl_act_no` encryption** — deferred to the project-wide encryption migration (OQ-4).

None of the residual items affect the implemented feature's parity or correctness.
