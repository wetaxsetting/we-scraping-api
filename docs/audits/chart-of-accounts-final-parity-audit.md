# Setting › Chart of Accounts — Final Refactor Parity Audit (vs. Legacy JEX)

> **System:** Refactor (`wecms-api` + `wecms-web`) audited against JEX `comm_0223_001` / `comm_0223_002` family.
> **Audited:** 2026-06-07 · **source-based** (read actual code on both sides, not docs).
> **Slug:** `chart-of-accounts` · **Legacy:** `comm_0223_001` (+ `_r001/_r002/_r003/_c001/_u001/_u002/_d001/_d002`), `comm_0223_002` (+ `_r001/_r002/_c001`) · **Table:** `CHART_ACCT_INFM`.

**Output-path note (per task):** the task suggested `docs/features/chart-of-accounts/refactor-gap-analysis.md`. Per `wecms-api/CLAUDE.md` → Documentation Structure Rules, *parity audits (refactor vs JEX)* live under `wecms-api/docs/audits/`. This audit spans both refactor trees but is filed here (the designated audits home) and cross-references `wecms-web`. The JEX legacy dossier stays read-only reference.

Legacy reference dossier: [docs/jex/features/chart-of-accounts/](../../../docs/jex/features/chart-of-accounts/) (README, business-flow, functional-spec, database-mapping, interface-spec, source-code-index, migration-guide, test-scenarios, refactor-gap-analysis).
Related OQ tracking: memory `project-chart-of-accounts-oq-pending`; [planning/open-questions/README.md](../planning/open-questions/README.md).

---

## 1. Final Verdict

## 🟢 Code-complete for SIT · 🟡 Final business closure pending PO ratification only

> **Closeout status (2026-06-08).**
> - **Chart of Accounts implementation is code-complete for SIT.**
> - **No CoA code changes are required before SIT.** (CA-G2 was a doc-only fix, now applied; authorization was always live at runtime.)
> - **Remaining items are PO ratification only** — see the PO Ratification Checklist in the Final Closure Verification section.
> - **CA-G1 (bulk Activate) is a PO-approved-deviation candidate, not a blocker** — row-level re-activation already exists via the inline edit status dropdown.
> - **OQ-3 (the dangerous legacy bulk parent-cleanup / orphaning branch) is intentionally NOT ported** — dropped by design, pending PO ratification.
> - **CA-G10 is tracked as a separate Payment / Internal-Transfer follow-up** (see [internal-transfer-parity-checklist.md](../references/audits/internal-transfer-parity-checklist.md)) — it does not gate Chart of Accounts.

The refactor is **safe** and **largely faithful**. The single highest legacy risk — the OQ-3 bulk parent-cleanup bug (`comm_0223_001_u001_act.jsp:238-256`) — is **correctly dropped**, replaced with proper per-row update plus cycle/depth/self-parent guards. Schema, composite PK, soft-delete uniqueness, restricted-root protection, sequence generation, audit columns, tenant scoping, and the downstream reporting-account write paths all hold parity (several are stronger than legacy). No data-corrupting defect was found, and `wb_get_chart_acct_*` name/level helpers are intentionally re-implemented in Java (not gaps).

It is **CONDITIONAL** rather than GO because:

- **One reachable functional gap** — bulk **Activate** is missing entirely from the UI and API (CA-G1). Legacy offers Activate + Deactivate + Delete; the refactor ships only Deactivate + Delete. Testers will hit this.
- **Several master-data behavior changes need PO ratification before SIT sign-off** — Chart of Accounts is master data, and per the team rule risky/behavioral changes must be PO-confirmed: the CAL_RULE rule changed from "INVESTMENT branch, required" to "level-3 only, optional" (CA-G4); account-code max length changed 10 → 20 (CA-G5); parent became **required on update** (CA-G6); account-code is **no longer editable** on update (CA-G7).
- **One documentation/authorization contradiction** — the controller carries `@PreAuthorize(@menuAuthz.canAccess('settings-core-chart-of-accounts'))` while its own Javadoc (and the service Javadoc) claim "no `@PreAuthorize`" (CA-G2). Must be reconciled and OQ-1 confirmed.

None of these is a stop-ship correctness defect; all are either a small build task or a PO confirmation. Once CA-G1 is implemented (or formally deferred) and the PO-decision items (§4) are confirmed, this upgrades to **GO**.

---

## 2. Summary Table (by audit area)

| # | Area | Legacy behavior | New behavior | Status | Evidence (new) |
|---|---|---|---|---|---|
| 1 | List / Read | `del_yn='N'`, BIZ_KEY scoped, ILIKE code/EN, ORDER BY code | `list()` filters `DEL_YN='N'`, `requireBizKey()`, LOWER LIKE code/EN, default sort code ASC | ✅ Parity | `ChartOfAccountsService.java:132-149`; `ChartOfAccountRepository.java:41-51` |
| 2 | Hierarchy / tree | picker/R002 cap 4 levels; grid uncapped | Cap **3** (`MAX_HIERARCHY_DEPTH=3`); DFS pre-order; orphan/self-ref → null level dropped | 🟡 Tighter (intentional) | `ChartOfAccountsConstants.java:41`; `ChartOfAccountsTreeBuilder.java:261` |
| 3 | Create | required CD/EN/PARENT/STTS; dup-live-code; parent exists; revive soft-deleted; VN/KO←EN | required + parent-required + dup (`WBE_..DUPLICATE_CODE`) + parent-exists + revive-soft-deleted | 🟡 Parity except **VN/KO not defaulted to EN** | `ChartOfAccountsService.java:232-275`; `ChartOfAccountsValidator.java:99-129` |
| 4 | Update | required SEQ/CD/EN/STTS; parent optional; restricted guard; dup-on-rename; **OQ-3 bulk parent-null** | per-row update + restricted + dup-on-rename + cycle + depth + self-parent guards; **no bulk parent-null** | ✅ OQ-3 dropped; 🟡 parent now required + code locked | `ChartOfAccountsService.java:322-388`; `ChartOfAccountsValidator.java:41-210` |
| 5 | Delete | soft `DEL_YN='Y'`; child→grandparent (root children dangling); archive-rename; restricted guard; no txn guard | soft-delete; child re-parent (root children → **NULL/promoted**); archive-rename; restricted guard; no txn guard | 🟡 Parity except **root-child → null vs dangling** | `ChartOfAccountsService.java:411-469`; `ChartOfAccountRepository.java:80-94` |
| 6 | Bulk status | `u002` patch STTS only; restricted guard; never DEL_YN | `bulkDeactivate` STTS='I' only, restricted guard, idempotent. **No bulk Activate** | 🔴 Activate missing | `ChartOfAccountsService.java:486-567`; `ChartOfAccountsPanel.tsx:577-599` |
| 7 | Excel template | per-lang EN/VI/KO `.xlsx`; 5 cols | static `public/templates/chart-of-accounts/WeCMS_Chart_of_Accounts_upload_{EN,KO,VI}.xlsx`; 5 cols; EN fallback | ✅ Parity | `ExcelUploadDialog.tsx:33-37,104-146` |
| 8 | Excel preview/validate | server parse; type/header/required/len/dup; max 500; per-cell errors; button disabled on error | **FE parses** (type/header/required/len/dup/depth≤3, in-file dup flags both rows); button hard-disabled on error | 🟡 Parity (validation moved FE; backend takes JSON) | `useExcelUpload.ts:155-362`; `ExcelUploadDialog.tsx:181,231`; `ChartOfAccountBulkUploadRequest.java:14-17` |
| 9 | Excel save | save error-free rows; STTS=ACTIVE; revive deleted; partial | per-row `create()` (revive + dup); partial-success tally; **STTS not forced ACTIVE in backend** (FE sends "A") | 🟡 Parity (backend should force ACTIVE) | `ChartOfAccountsService.java:580-595`; `ExcelUploadDialog.tsx:153-163` |
| 10 | Authorization | none (any tenant user writes — OQ-1 open) | `@PreAuthorize(@menuAuthz.canAccess('settings-core-chart-of-accounts'))` (menu gate) — **contradicts own Javadoc** | 🔴 Code↔doc contradiction | `ChartOfAccountsController.java:70` vs `:56-59`, `ChartOfAccountsService.java:79-81` |
| 11 | DB schema / PK / unique | PK `(biz_key,seq)`, UK `(biz_key,cd,del_yn)`, 15 cols, CHAR(1) flags | V94 replicates exactly + 2 helpful indexes | ✅ Parity | `V94__chart_of_accounts_schema.sql:54-93` |
| 12 | Sequence | `SeqHelper` MAX+1 per tenant | `wb_get_next_val` branch (verbatim from DEV) via `LegacyFunctionRepository` | ✅ Parity | `V95__wb_get_next_val_chart_acct_infm.sql:86-92`; `LegacyFunctionRepository.java:137-139` |
| 13 | Restricted roots | ACC-01/02/03 = REVENUE/EXPENSES/INVESTMENT, immutable (`WBE1178`) | hardcoded set, seeded per tenant (V96), guarded (`WBE_..RESTRICTED_CODE`) | ✅ Parity | `ChartOfAccountsConstants.java:49`; `V96__chart_of_accounts_seed_restricted_roots.sql:83-89` |
| 14 | Name/level helpers | `wb_get_chart_acct_nm` / `_lv_nm` DB functions | re-implemented in Java (`ChartOfAccountsTreeBuilder`, `ChartOfAccountsHierarchy`); DB fns not created/called | ✅ Parity (intentional) | `ChartOfAccountsTreeBuilder.java`; `banking/.../cashflow/ChartOfAccountsHierarchy.java:16` |
| 15 | Audit / tenant | reg_id/mod_id, reg_dtm/mod_dtm varchar(14) UTC; BIZ_KEY everywhere; audit-log write | `utcNow14()` stamps; bizKey from `TenantContext`; `appendAudit` with `wsvc_id="chart-of-accounts"` (slug) | ✅ Parity (+ audit-log) | `ChartOfAccountsService.java:104-105,312,680-722` |
| 16 | Downstream writes (txn/card) | write `chart_acct_cd`+`seq` (no active check) | banking + card **validate exists + seq + STTS='A'** before write | ✅ Stronger | `TransactionService.java:355-362`; `CardTransactionsService.java:1678-1690` |
| 17 | Downstream — internal transfer | write `chart_acct_cd`+`seq` | **no existence/active guard; stale "entity not migrated" comment** | 🟡 Divergent | `InternalTransferService.java:503-504,572-577` |
| 18 | Reporting-account picker | `del_yn='N'` only (NOT active-only) | `del_yn='N'` only (list/tree) — matches legacy | ✅ Parity (prompt premise corrected) | `ChartOfAccountsService.java:140,159-160` |

Legend: ✅ parity / stronger · 🟡 divergence needing note or PO confirm · 🔴 functional gap / contradiction.

---

## 3. Gap List

| Gap ID | Sev | Description | Legacy Evidence | New Evidence | Recommendation |
|---|---|---|---|---|---|
| **CA-G1** | **HIGH** | **Bulk Activate (re-activate, STTS='A') missing entirely** — no UI button, no API client fn, no endpoint. User cannot bulk re-activate. | Legacy toolbar Activate confirm `WBC1178`; `comm_0223_001_u002` accepts STTS='A' or 'I' | `ChartOfAccountsPanel.tsx:577-599` (only Delete+Deactivate); `lib/api/chartOfAccounts.ts` (no activate fn); `ChartOfAccountsService.java:486-567` (only deactivate) | Add `bulkActivate`/Activate button + `POST .../bulk-activate` (STTS='A', restricted guard, idempotent), **or** PO-approve "individual edit→status is the sole re-activation route" and document it. |
| **CA-G2** | **HIGH** | **Authz code↔doc contradiction.** Controller has `@PreAuthorize(@menuAuthz.canAccess('settings-core-chart-of-accounts'))`; controller + service Javadoc claim there is no `@PreAuthorize`. Diverges from legacy (open to any tenant user). | Legacy `_act.jsp` have no role gate (OQ-1) | `ChartOfAccountsController.java:70` vs `:56-59`; `ChartOfAccountsService.java:79-81` | Reconcile: keep the menu gate (consistent with sibling settings controllers) and **fix the stale Javadoc**, or remove it for legacy parity. Tie to OQ-1 decision. |
| **CA-G3** | MED | **Mixed-selection status-button hiding not implemented.** Legacy hides both status buttons on a mixed active+inactive selection; new always shows Delete+Deactivate when any row selected. Not corrupting (deactivate is idempotent) but diverges + no Activate counterpart. | Legacy `updateBulkActionButtons` (`comm_0223_001.ts:1068-1129`) | `ChartOfAccountsPanel.tsx:578-599` (selection keyed by seqNo only; status never inspected) | Implement status-aware toolbar (show Activate for all-inactive, Deactivate for all-active, hide on mixed) — depends on CA-G1. |
| **CA-G4** | MED | **CAL_RULE rule changed** from "shown/required under the INVESTMENT (ACC-03) branch" to "enabled on level-3 rows only, never required." Over-shows (non-INVESTMENT level-3) + under-enforces (never required). V191 seed has CAL_RULE on level-2 rows → not re-saveable via Update without clearing. | Legacy `comm_0223_001.ts:287-288,452-453,493-503` (INVESTMENT branch, required) | `ChartOfAccountsValidator.java:149-156`; `ChartOfAccountsService.java:238-239,366-367`; `ChartOfAccountsInlineRow.tsx:97-110`; `ChartOfAccountsTable.tsx:298-306` | **PO decision (§4).** Comments cite a PO directive 2026-05-25; confirm it supersedes the INVESTMENT-branch rule, then update the legacy dossier + reconcile V191 seed. |
| **CA-G5** | MED | **Account code max length 20 (new) vs 10 (legacy).** Applies to create, update, and bulk upload. | Legacy live truncation to 10 (`comm_0223_001.ts:122-132`) | `ChartOfAccountCreateRequest.java:35`; `ChartOfAccountUpdateRequest.java:19`; `ChartOfAccount.java:78`; `V94:57` | **PO decision (§4).** If 10 is still the rule, tighten `@Size(max=10)` + FE `maxLength` (FE already caps 10 at `ChartOfAccountsInlineRow.tsx:18`, so backend is the looser side). |
| **CA-G6** | MED | **Parent now required on update** (legacy update allowed empty parent). Can't promote a node to root via edit. | Legacy `comm_0223_001_u001_act.jsp` (parent not in required set) | `ChartOfAccountsService.java:345`; `ChartOfAccountsValidator.java:125-129` | **PO decision (§4).** Likely intentional (roots reserved for ACC-0x); confirm + document. |
| **CA-G7** | MED | **Account code not editable on update** (input disabled in UI). Legacy allowed code edit (backend still supports rename + dup-on-rename). | Legacy editable code field | `ChartOfAccountsInlineRow.tsx:229` (disabled, with comment) | **PO decision (§4).** Confirm code is immutable post-create; if so, document; else enable the field. |
| **CA-G8** | MED | **VN/KO names not defaulted to EN** when blank (stored null). Legacy defaults VN=KO=EN. | Legacy `comm_0223_001.ts:514-520` | `ChartOfAccountsService.java:645-646` (`blankToNull`); `mappers.ts:148-149` | Implement EN fallback in `applyRequest` (or confirm null-is-intended with PO). Low display impact, easy fix. |
| **CA-G9** | MED | **Excel save backend does not force STTS=ACTIVE** (trusts request `stts`). FE hardcodes `stts:"A"`, so reachable only via a non-UI caller. | Legacy `comm_0223_002_c001_act.jsp:137-151` forces ACTIVE | `ChartOfAccountsService.java:650`; FE `ExcelUploadDialog.tsx:159` | Force `STTS='A'` in the bulk-upload path server-side (defense-in-depth). |
| **CA-G10** | MED | **Internal-transfer reporting-account write skips existence/active guard**, with a stale/false "CHART_ACCT_INFM not yet migrated" comment (it IS migrated; banking + card both validate). Allows persisting arbitrary/inactive code+seq onto `SCDUL_PYMN_SUMR_DETL`. | Sibling features validate; legacy writes raw | `InternalTransferService.java:503-504,572-577` | Add the same `findByBizKeyAndChartAcctCdAndDelYn(...,'N')` + seq-match + `"A".equals(stts)` guard; delete the stale comment. (Downstream module, not the COA feature itself.) |
| **CA-G11** | LOW | **Delete of a (non-restricted) root re-parents children to NULL** (promotes to roots) instead of leaving them dangling. Low reachability (3 roots are restricted/undeletable; creating other roots is now blocked) but logic differs + collides with "level 1 reserved." | Legacy leaves root children dangling (`comm_0223_001_d001_act.jsp:182-211`) | `ChartOfAccountsService.java:431-436`; `ChartOfAccountRepository.java:80-94` | Confirm desired behavior; add a test for the root-delete branch (none today). |
| **CA-G12** | LOW | **`CHART_ACCT_SEQ_NO` width mismatch**: master VARCHAR(14) vs downstream columns VARCHAR(10) (`V185`). Values are small today; no truncation risk yet. | — | `V94:56` vs `V185__ovrs_pymn_mstr_chart_acct_columns.sql` | Align downstream to VARCHAR(14) for cleanliness. |
| **CA-G13** | LOW | **Hierarchy indentation 16px (new) vs 20px (legacy).** Cosmetic. | `comm_0223_001.ts` render (level*20) | `ChartOfAccountsTable.tsx:206-211` (level*16) | Cosmetic — align if pixel-parity desired. |
| **CA-G14** | LOW | **Stale "level 1..4" Javadoc** across TreeBuilder/Row/TreeNode/Controller while real cap is 3. Docs only. | — | `ChartOfAccountsTreeBuilder.java:29-32`; `ChartOfAccountTreeNode.java:16-17`; etc. vs `Constants.java:41` | Update comments to "level 1..3." |
| **CA-G15** | LOW | **Dead client code** `fetchChartOfAccountsTree` / `fetchChartOfAccount` defined but never called (panel derives parents from flat list). | — | `lib/api/chartOfAccounts.ts:91-106` | Remove or wire up; low priority. |
| **CA-G16** | LOW | **No explicit in-file duplicate scan in backend bulk upload** — relies on sequential DB dup detection (2nd reported as generic failure, not "in-file duplicate"). FE does flag in-file dups. | Legacy flags in-file dup in `comm_0223_002_r001` | `ChartOfAccountsService.java:580-595` | Acceptable (FE covers it); optionally add backend in-batch scan for clearer errors. |

> **Confirmed NON-gaps (verified at source, no action):**
> (a) OQ-3 bulk parent-cleanup is correctly **dropped** (no `SET parent=NULL WHERE biz_key=? AND parent_chart_acct_cd=?` anywhere in update). (b) `wb_get_chart_acct_nm`/`_lv_nm` intentionally Java-reimplemented; `wb_get_chart_acct_full_nm` does **not exist** anywhere (prompt artifact). (c) Picker filters `del_yn='N'` only — this **matches** legacy R001/R002/R003 (which do not filter `stts='A'`); the prompt's "active-only" premise was inaccurate. (d) Hard-delete IDO `D001` correctly not ported. (e) Schema/PK/unique/seq/restricted-root all parity.

---

## 4. Open PO Decisions

| Ref | Question | Status in code | Recommendation |
|---|---|---|---|
| **OQ-3** (parent-cleanup) | Was the legacy `u001:238-256` bulk parent-null intended, or a bug to drop? | **Code already DROPPED it** (per-row update only). Memory `project-chart-of-accounts-oq-pending` still lists it "pending Mr. Jung." | **Ratify the drop with Mr. Jung**, then close OQ-3 in the ledger. No code change needed; this is a traceability close-out. The refactor's behavior is the recommended one. |
| **OQ-1** (authorization) | USER role: read-only or full CRUD on Chart of Accounts? | Controller has a **menu-access** gate (`@menuAuthz.canAccess`), not an ADMIN-write gate; Javadoc claims none (CA-G2). | Confirm intended model (menu-gate is consistent with sibling settings controllers). Then fix Javadoc and document. |
| **PO-CALRULE** (CA-G4) | Is CAL_RULE "level-3 only, optional" the current rule (superseding "INVESTMENT branch, required")? | Implemented as level-3-only, optional (cites PO directive 2026-05-25). | Confirm; if yes, update legacy dossier §Cal-Rule + reconcile V191 seed (level-2 CAL_RULE rows). |
| **PO-CODELEN** (CA-G5) | Account code max length — 10 (legacy) or 20 (new)? | Backend `@Size(max=20)`; FE caps 10. | Confirm; align both sides. |
| **PO-PARENT-REQ** (CA-G6) | Is parent mandatory on **update** (no promotion to root)? | Required on update. | Confirm + document (likely intended). |
| **PO-CODELOCK** (CA-G7) | Is account code immutable after create? | UI disables code on edit (backend supports rename). | Confirm immutability intent. |
| **PO-ROOTDEL** (CA-G11) | On deleting a root, should children become roots (new) or stay dangling (legacy)? | Children → NULL parent. | Confirm; add test. |

---

## 5. SIT Test Checklist

**Create**
- [ ] Create under a parent (happy path) — appears in tree, audit row written
- [ ] Missing code / EN / parent / status each blocked
- [ ] Duplicate live code rejected (`WBE_..DUPLICATE_CODE`)
- [ ] Recreate a previously soft-deleted code → revives the deleted row (same seq)
- [ ] Code length: confirm enforced limit (10 vs 20 — per PO-CODELEN)
- [ ] VN/KO blank → confirm EN-fallback vs null (per CA-G8)
- [ ] CAL_RULE behavior per confirmed rule (PO-CALRULE)
- [ ] Self-parent rejected (server)
- [ ] Depth > 3 rejected on create

**Update**
- [ ] Rename EN/VN/KO; audit row written
- [ ] Rename code to a duplicate → rejected; to a free value → confirm whether editable at all (CA-G7)
- [ ] Edit ACC-01/02/03 → blocked (restricted)
- [ ] Self-parent / cycle rejected
- [ ] Move to non-existent parent → rejected
- [ ] **OQ-3 regression:** edit a node with children, change its parent → children's parent links **unchanged** (no bulk nulling) ✅ key assertion
- [ ] Parent-required-on-update behavior (PO-PARENT-REQ)

**Delete**
- [ ] Soft-delete a leaf → leaves grid, `DEL_YN='Y'`
- [ ] Delete a mid node with children & parent → children re-parented to grandparent
- [ ] Delete then recreate same code → revive path
- [ ] Delete ACC-0x → blocked
- [ ] Bulk delete → partial-success tally correct
- [ ] (CA-G11) Root delete child-handling per PO-ROOTDEL

**Activate / Deactivate** *(blocked on CA-G1)*
- [ ] Bulk Deactivate active rows → `STTS='I'`, idempotent
- [ ] **Bulk Activate inactive rows** → currently MISSING (CA-G1) — verify the resolution
- [ ] Mixed-selection toolbar behavior (CA-G3)

**Excel template**
- [ ] Download EN/VI/KO templates (correct file each; EN fallback)
- [ ] 5 columns (code, name EN/VI/KO, parent)

**Excel preview**
- [ ] Reject non-xlsx/xls; reject wrong template shape
- [ ] Required/length/duplicate (DB + in-file) flagged per cell with tooltip
- [ ] Depth > 3 flagged
- [ ] Completed/error counts correct; Upload button disabled while any error
- [ ] > 500 rows handling

**Excel upload save**
- [ ] Only error-free rows persisted; partial-success counts shown
- [ ] Saved rows are ACTIVE (verify backend forces it — CA-G9)
- [ ] Import a soft-deleted code → revive
- [ ] Grid refreshes after save

**Downstream compatibility**
- [ ] Banking reporting-account assignment validates exists + active (stronger than legacy) ✅
- [ ] Card reporting-account assignment validates exists + active ✅
- [ ] **Internal-transfer** reporting-account assignment — verify guard (CA-G10)
- [ ] Cash Flow report hierarchy/name/level unchanged (Java `ChartOfAccountsHierarchy`)
- [ ] Smart Organizer rule storing a chart code (no active check — by design, confirm)

**Authorization / tenant**
- [ ] Menu gate behavior for non-entitled user (CA-G2 / OQ-1)
- [ ] Cross-tenant isolation (BIZ_KEY) — Tenant A cannot see/modify Tenant B
- [ ] Same code allowed across two tenants

---

## 6. Final Developer Action List

### Must fix before SIT
- **CA-G1** — Implement bulk **Activate** (button + endpoint), **or** obtain PO sign-off to ship without it and document the sole re-activation route.
- **CA-G2 / OQ-1** — Reconcile the `@PreAuthorize` menu gate with the contradicting Javadoc; confirm the authorization model with the PO.
- **Confirm the §4 PO decisions** (PO-CALRULE, PO-CODELEN, PO-PARENT-REQ, PO-CODELOCK) — Chart of Accounts is master data; these behavioral changes must be PO-ratified, and CA-G4/G5 need code alignment if the legacy rule stands.

### Can defer (fix during SIT or as follow-up)
- **CA-G3** — status-aware bulk toolbar (depends on CA-G1).
- **CA-G8** — VN/KO EN-fallback (or confirm null intended).
- **CA-G9** — force `STTS='A'` server-side on bulk upload.
- **CA-G10** — internal-transfer reporting-account existence/active guard + remove stale comment (downstream module).
- **CA-G11** — root-delete child handling decision + test.
- **CA-G12** — align `CHART_ACCT_SEQ_NO` downstream width to 14.

### No action needed (verified correct / intentional)
- OQ-3 bulk parent-cleanup correctly dropped — **ratify & close ledger** (no code change).
- `wb_get_chart_acct_nm`/`_lv_nm` Java reimplementation; `wb_get_chart_acct_full_nm` non-existent (prompt artifact).
- Picker `del_yn='N'`-only filter matches legacy.
- Hard-delete not ported.
- Schema / PK / unique / sequence / restricted-root / audit / tenant scoping — parity.
- Downstream banking + card active-checks (stronger than legacy).
- **CA-G13/G14/G15/G16** — cosmetic / docs / dead-code / nice-to-have; low priority.

---

# Final Closure Verification

> Added 2026-06-07. Targeted re-verification of the open items only (CA-G1, CA-G2, CA-G10,
> and the four MED PO decisions). Source re-read on both sides; no full re-audit, no code change.

## Item-by-item

### CA-G1 — Bulk Activate missing

1. **Did legacy provide bulk Activate?** Yes. The toolbar `.update-stts[data-stts="A"]` (Activate) button exists and `updateBulkActionButtons` **shows it when the selection is all-inactive** (`comm_0223_001.ts:1102-1105`). It posts `{REC, STTS:'A'}` to `comm_0223_001_u002`.
2. **Reachable from the UI?** Yes in legacy — but only surfaces when inactive rows are selected.
3. **Used in real workflows?** Marginally. Accounts default to ACTIVE; Activate only matters after a Deactivate. New accounts and Excel imports land ACTIVE.
4. **Business-critical?** **No.** Re-activation is still possible in the refactor **one row at a time** via the inline edit status dropdown (`ChartOfAccountsInlineRow.tsx:341-352`, status editable; `PUT .../{seqNo}` flips `STTS`). Only the *bulk* convenience is absent.
5. **Acceptable as PO-approved deviation?** Yes — single-row re-activation covers the functional need.
- **Legacy evidence:** `comm_0223_001.ts:1098-1105`; view buttons `comm_0223_001_view.jsp:152-159`.
- **Refactor evidence:** `ChartOfAccountsPanel.tsx:577-599` (only Delete + Deactivate); `lib/api/chartOfAccounts.ts` (no activate fn); single-row status path `ChartOfAccountsInlineRow.tsx:341-352`.
- **Recommendation: PO APPROVAL SUFFICIENT** (or a low-priority follow-up to add `bulk-activate` for symmetry). Not a SIT blocker.

### CA-G2 — Authorization contradiction

1. **Actual runtime behavior?** The class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-chart-of-accounts')")` (`ChartOfAccountsController.java:70`) **is active at runtime** — Spring Security evaluates the annotation regardless of the Javadoc. Every endpoint is gated by the menu-access check (consistent with the F-3 menuAuthz rollout; `EndpointAuthorizationCoverageTest` would fail the build otherwise).
2. **Authorization functioning correctly?** Yes — the menu gate is enforced.
3. **Merely a documentation inconsistency?** Yes. The controller Javadoc (`:56-59`) and service Javadoc (`ChartOfAccountsService.java:79-81`) say "no `@PreAuthorize`," which is **stale/false** relative to the live annotation.
4. **Blocks SIT?** **No.** Runtime security is correct; only the comment is wrong.
- **Legacy evidence:** `_act.jsp` have no role gate (OQ-1 open).
- **Refactor evidence:** `ChartOfAccountsController.java:70` (live annotation) vs `:56-59` + `ChartOfAccountsService.java:79-81` (stale doc).
- **Recommendation:** Doc-only fix (correct the two Javadocs). Confirm OQ-1 keeps the menu gate (recommended). **No business-logic code change. Not a SIT blocker.**

### CA-G10 — InternalTransferService validation gap *(most important — re-examined)*

Re-read of the **legacy** action changes the severity picture:

1. **Does legacy validate Chart Account existence?** **YES.** `pymn_0009_002_u002_act.jsp:92-97` builds a dynamic on `BIZ_KEY + CHART_ACCT_SEQ_NO + DEL_YN='N'`, reads `CHART_ACCT_INFM_R001`, and throws `WBE0404` when the row is missing.
2. **Does legacy validate active status (`STTS='A'`)?** **No** — it checks `DEL_YN='N'` only (existence + not-soft-deleted), not active status.
3. **Does the refactor bypass validation?** **YES** — `InternalTransferService.updateReportingAccount` (`:572-577`) skips the existence check entirely; the create path (`:503-504`) likewise sets the code/seq with no lookup. The justifying comment ("CHART_ACCT_INFM entity not yet migrated") is **false** — the entity is migrated and used by `TransactionService` + `CardTransactionsService`.
4. **Can inactive accounts be persisted?** Yes (legacy too — legacy doesn't check `STTS`).
5. **Can deleted accounts be persisted?** **Yes in the refactor — a regression.** Legacy rejects a `DEL_YN='Y'`/missing row with `WBE0404`; the refactor accepts any code+seq.
6. **Can invalid Chart Account codes be persisted?** **Yes in the refactor — a regression.** Legacy rejects unknown seq with `WBE0404`; the refactor accepts anything that passes bean-shape validation.
7. **Real data-integrity risk?** Yes, bounded. It writes an unresolvable classification onto `SCDUL_PYMN_SUMR_DETL` (downstream chart-name/level joins resolve to blank). Not financial-amount corruption, no cascade. Via the UI the picker only offers valid active accounts, so it's reachable mainly via a direct API call or a delete-after-pick race; via a non-UI client it's fully open.
- **Classification: MEDIUM.** Confirmed parity **regression** (legacy validated existence; refactor does not) on master-data referential integrity, with a false justifying comment — but bounded blast radius and UI-constrained in practice.
- **Legacy evidence:** `pymn_0009_002_u002_act.jsp:31-56,92-97` (`WBE0404` existence guard).
- **Refactor evidence:** `InternalTransferService.java:503-504` (create), `:572-577` (update, stale comment); sibling guards present at `TransactionService.java:355-362` and `CardTransactionsService.java:1678-1690`.
- **Recommendation: code change required — but in the Payment / Internal-Transfer module, NOT in Chart of Accounts.** Add the existence (`DEL_YN='N'`) guard the sibling features already use (optionally `STTS='A'` too, which would exceed legacy), and delete the false comment. **File as a payment-module defect. Does not block Chart of Accounts SIT.**

### MED-level PO decisions

| # | Change | Legacy parity broken? | Safer than legacy? | PO approval sufficient? | Code change required? |
|---|---|---|---|---|---|
| 1 | **Code length 10 → 20** | Not via the UI — legacy capped at 10 in the **UI** (DB col is `VARCHAR(20)` on both sides); refactor FE also caps at 10 (`ChartOfAccountsInlineRow.tsx:18`). Only the backend `@Size(max=20)` is looser, reachable by non-UI clients. | Neutral (more permissive) | **Yes** | Optional — tighten backend `@Size(max=10)` for defense; trivial, not required for SIT. |
| 2 | **Parent required on update** | Yes (legacy update allowed empty parent → promote to root) | Yes — keeps roots reserved to ACC-01/02/03; prevents accidental new roots | **Yes** (deliberate design) | No |
| 3 | **Account code not editable on update** | Yes (legacy allowed code edit) | Yes — immutable business key protects downstream `code+seq` references | **Yes** | No (backend still supports rename; re-enable the input if PO wants it) |
| 4 | **Calc Rule: INVESTMENT-branch → level-3-only, optional** | Yes — but per a cited **PO directive 2026-05-25** (`ChartOfAccountsValidator.java:136-142`) | Arguable — blocks sign badges on grouping headers; over-shows on non-INVESTMENT level-3 | **Yes** (ratify the directive; reconcile V97/V191 grandfathered level-2 CAL_RULE rows) | No |

All four are PO confirmations; none requires a Chart-of-Accounts code change before SIT (item 1's backend tightening is optional hardening).

## Closure status table

| Gap | Status | Recommendation |
|---|---|---|
| CA-G1 (bulk Activate) | Not a blocker — single-row re-activation works | PO APPROVAL SUFFICIENT (optional bulk-activate follow-up) |
| CA-G2 (authz contradiction) | ✅ RESOLVED 2026-06-08 — both stale Javadocs corrected (`ChartOfAccountsController.java` + `ChartOfAccountsService.java`); authorization was always live at runtime | Done (doc-only). Confirm OQ-1 keeps the menu gate. |
| CA-G10 (internal-transfer validation) | Confirmed MEDIUM regression — **outside CoA scope** | Fix in Payment/Internal-Transfer module (add existence guard, drop false comment). Does not block CoA SIT. |
| MED PO decisions (code length / parent-required / code-immutable / calc-rule) | All PO confirmations | PO APPROVAL SUFFICIENT; no CoA code change required |
| OQ-3 (parent-cleanup) | Already DROPPED in code | Ratify drop with PO; close ledger entry. No code change. |
| OQ-1 (authorization model) | Menu gate live | Confirm intended; fix Javadoc (CA-G2) |

## PO Ratification Checklist

The only items standing between CONDITIONAL GO and full GO. Each is a PO (Mr. Jung) sign-off,
not a code change:

- [ ] **Accept missing bulk Activate as non-blocking** — row-level re-activation already exists via the inline edit status dropdown (CA-G1).
- [ ] **Ratify parent-required-on-update** behavior (CA-G6 / PO-PARENT-REQ).
- [ ] **Ratify account-code-immutable** behavior on update (CA-G7 / PO-CODELOCK).
- [ ] **Ratify level-3-only Calc Rule** behavior, superseding the legacy INVESTMENT-branch rule (CA-G4 / PO-CALRULE 2026-05-25); reconcile the V97/V191 grandfathered level-2 CAL_RULE seed rows.
- [ ] **Ratify dropping the legacy OQ-3 bulk parent-orphaning** branch (`comm_0223_001_u001_act.jsp:238-256`) — intentionally not ported.
- [ ] **Confirm backend code-length looseness is acceptable** (backend `@Size(max=20)` vs UI cap 10) — UI and DB (`VARCHAR(20)`) remain compatible; optional backend tightening to `@Size(max=10)` if the PO prefers strict parity (CA-G5 / PO-CODELEN).

> Optional, non-blocking hardening (may be done any time, not gating): VN/KO EN-fallback (CA-G8),
> force `STTS='A'` on bulk-upload server-side (CA-G9).

## Final Verdict

## 🟢 Code-complete for SIT · 🟡 CONDITIONAL GO — only PO decisions remain

**No Chart-of-Accounts code change is required before SIT.** Every open CoA item resolves to either a PO confirmation (CA-G1 acceptance; the four MED behavior changes; OQ-1/OQ-3 ratification) or a documentation correction (CA-G2 Javadoc — authorization already works at runtime). Because Chart of Accounts is master data, these behavioral confirmations must be signed off by the PO (Mr. Jung) before final closure — which is exactly what keeps this at CONDITIONAL GO rather than a clean GO.

**Explicit code-change statement:**
- **Chart of Accounts feature:** no code change required for SIT. (Optional hardening only: backend `@Size(max=10)` per PO-CODELEN; VN/KO EN-fallback CA-G8; force `STTS='A'` on bulk-upload CA-G9 — all deferrable.)
- **One code change is required elsewhere:** CA-G10 in the **Payment / Internal-Transfer** module (re-instate the chart-account existence guard legacy enforced). This is a separate feature's defect surfaced by this audit — file it against Internal Transfer; it does **not** gate Chart of Accounts.

**Upgrades to 🟢 GO when:** the PO signs off the §4 decisions (CALRULE / CODELEN / PARENT-REQ / CODELOCK), accepts or schedules CA-G1, and ratifies OQ-1 + OQ-3 — at which point the CA-G2 Javadoc fix (doc-only) is the sole remaining cleanup.

---

### Cross-references
- Legacy dossier: [docs/jex/features/chart-of-accounts/](../../../docs/jex/features/chart-of-accounts/)
- Backend feature spec: [../features/settings/chart-of-accounts.md](../features/settings/chart-of-accounts.md)
- DB / business-rule refs: [../references/database/chart-of-accounts.md](../references/database/chart-of-accounts.md), [../references/business-rules/chart-of-accounts.md](../references/business-rules/chart-of-accounts.md)
- OQ tracking: [../planning/open-questions/README.md](../planning/open-questions/README.md); memory `project-chart-of-accounts-oq-pending`
