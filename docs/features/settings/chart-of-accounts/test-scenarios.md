# Chart of Accounts — Backend Test Scenarios (SIT / UAT)

API-level scenarios for the shipped backend. Frontend/UI scenarios: [../../../../../wecms-web/docs/features/settings/chart-of-accounts/test-scenarios.md](../../../../../wecms-web/docs/features/settings/chart-of-accounts/test-scenarios.md). The audit's SIT checklist (`§5`) is the master list: [final parity audit](../../../audits/legacy-parity/chart-of-accounts-final-parity-audit.md).

All requests require a JWT + the `settings-core-chart-of-accounts` menu grant; all are tenant-scoped via `TenantContext`.

---

## 1. Search / List

| # | Scenario | Expected |
|---|---|---|
| S1 | `GET /` no params | All active rows for tenant, `CHART_ACCT_CD ASC`; `restricted_codes=[ACC-01,ACC-02,ACC-03]` |
| S2 | `GET /?search=acc` | Rows where code OR EN name `LIKE %acc%` (case-insensitive) |
| S3 | `GET /?sort_field=MOD_DTM&sort_dir=DESC` | Sorted by MOD_DTM desc + default tiebreak |
| S4 | `GET /?sort_field=BOGUS` | Falls back to default sort (no error) |
| S5 | Search by status | No dedicated status filter on the API — status visible per row (`stts`); FE filters in-memory |
| S6 | `GET /tree` | Parent-picker nodes, breadcrumb populated, orphans dropped, code ASC |
| S7 | `GET /{seq}` valid / unknown | Row / `WBE_CHART_OF_ACCOUNTS_NOT_FOUND` |
| S8 | Cross-tenant | Tenant A cannot read/modify Tenant B rows; same code allowed across tenants |

## 2. Create

| # | Scenario | Expected |
|---|---|---|
| C1 | Valid create under a parent | 200, row returned, audit row written, appears in list/tree |
| C2 | Missing code / name_en / parent / stts | `WBE_VALIDATION` (or `WBE_..PARENT_REQUIRED` for blank parent) |
| C3 | Duplicate active code | `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` |
| C4 | Code matches a soft-deleted code | **Revives** the deleted row, reuses original seq_no |
| C5 | Non-existent parent | `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND` |
| C6 | Self-parent | `WBE_CHART_OF_ACCOUNTS_SELF_PARENT` |
| C7 | Create that would exceed depth 3 | `WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED` |
| C8 | `cal_rule` on a non-level-3 row | `WBE_CHART_OF_ACCOUNTS_CAL_RULE_LEVEL3_ONLY` |
| C9 | Code length (PO-CODELEN) | Backend accepts ≤20; FE caps 10 — confirm enforced limit |
| C10 | VN/KO blank | Stored `null` (not EN-defaulted — CA-G8) |
| C11 | Concurrent create, same code | Second hits UK → 1-shot retry → `DUPLICATE_CODE` |

## 3. Update

| # | Scenario | Expected |
|---|---|---|
| U1 | Rename EN/VN/KO | 200, audit row written |
| U2 | Edit ACC-01/02/03 | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` |
| U3 | Rename to a restricted code | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` |
| U4 | Change parent → cycle (A→B→A) | `WBE_CHART_OF_ACCOUNTS_CYCLE` |
| U5 | Change parent → exceeds depth 3 (incl. subtree) | `WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED` |
| U6 | Move to non-existent parent | `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND` |
| U7 | Blank parent on update | `WBE_CHART_OF_ACCOUNTS_PARENT_REQUIRED` (no promote-to-root — CA-G6) |
| U8 | Change status A↔I | 200 (this is the single-row (de)activation path) |
| U9 | **OQ-3 regression:** edit a node with children, change its parent | children's parent links **unchanged** (no bulk nulling) — key assertion |
| U10 | Body echoes `chart_acct_seq_no`/`biz_key` | ignored (path/JWT authoritative) |

## 4. Activation / Deactivation

| # | Scenario | Expected |
|---|---|---|
| A1 | `PUT` stts=I then stts=A (single) | toggles; A = re-activation route |
| A2 | `POST /bulk-deactivate` active rows | `STTS='I'`; `{processed=N, failed=0}` |
| A3 | bulk-deactivate already-inactive rows | idempotent, no DB write, counted processed |
| A4 | bulk-deactivate including a restricted root | restricted tallied in `failed` (`WBE_..RESTRICTED_CODE`) |
| A5 | **Bulk activate** | **No endpoint** (CA-G1) — verify single-row path covers it / PO sign-off |

## 5. Delete

| # | Scenario | Expected |
|---|---|---|
| D1 | Soft-delete a leaf | `DEL_YN='Y'`, leaves list |
| D2 | Delete a mid node with children | children re-parented to the grandparent; one `@Modifying` update |
| D3 | Delete then recreate same code | revive path (D1 then C4) |
| D4 | Delete ACC-0x | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` |
| D5 | Delete with a soft-deleted dup code present | own code archive-renamed (`prefix+last5+'D'`) before flip |
| D6 | `POST /bulk-delete` | per-seq tally `{processed, failed}` |
| D7 | (CA-G11) delete a non-restricted root | children → NULL parent (confirm per PO-ROOTDEL) |

## 6. Excel Import (`POST /bulk-upload`)

| # | Scenario | Expected |
|---|---|---|
| E1 | Valid batch | all rows created; `{created=N, failed=0}`; grid refresh |
| E2 | Batch with a duplicate-of-DB code | that row → `failed`; others committed (partial success) |
| E3 | Batch where a row revives a soft-deleted code | revive path; counted created |
| E4 | `> 500` items | `WBE_VALIDATION` (`@Size(max=500)`) |
| E5 | Saved rows status | rows ACTIVE (FE sends `"A"`; CA-G9 — backend does not force it) |
| E6 | FE-side validation (depth>3, in-file dup, missing required, missing parent) | blocked in the dialog before upload — see FE test-scenarios |

## 7. Validation / Error handling

| # | Scenario | Expected |
|---|---|---|
| V1 | Bean-validation failures (code pattern, sizes, stts pattern) | `WBE_VALIDATION` |
| V2 | All COA business codes resolve localized messages | `messages.properties` entries present |
| V3 | Unexpected runtime fault | `WBE9999` + trace_id, logged |
| V4 | No menu grant | menu-access denied (CA-G2 / OQ-1) |

## 8. Downstream compatibility (read-side, separate modules)

| # | Scenario | Expected |
|---|---|---|
| X1 | Banking reporting-account assignment | validates exists + seq + `STTS='A'` (stronger than legacy) ✅ |
| X2 | Card reporting-account assignment | same ✅ |
| X3 | Internal-transfer reporting-account assignment | **gap CA-G10** — verify guard (Payment-module fix) |
| X4 | Cash Flow report hierarchy/name/level | unchanged (Java `ChartOfAccountsHierarchy`) |

---

Map to the audit's master SIT checklist for sign-off tracking: [final parity audit §5](../../../audits/legacy-parity/chart-of-accounts-final-parity-audit.md).
