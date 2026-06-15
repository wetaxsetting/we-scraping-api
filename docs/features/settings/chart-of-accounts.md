# Chart of Accounts — Refactor Feature Spec

**Slug:** `chart-of-accounts`
**Module:** Settings (WABOOKS accounting)
**JEX counterpart:** [WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0223_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0223_001_view.jsp) (legacy WSVC `comm_0223_001` / `comm_0223_002` — kept for migration-traceability only; refactor code never echoes these ids — see [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Refactor identifier naming")
**Status:** R&D refactor target — local-only (no DEV/UAT/PROD deploy, no bank integration).

> **⚠️ Superseded for implementation truth (2026-06-08).** This is the **pre-implementation design spec**. The feature is now built and SIT/UAT-verified; the authoritative as-built documentation lives in the feature folder **[chart-of-accounts/](chart-of-accounts/README.md)**. This file is preserved for traceability but several design intentions changed in implementation — notably: hierarchy depth cap is **3** (not 4); Excel **upload is wired live** via `POST /bulk-upload` (JSON, FE-parsed — not the `bulk-import` multipart endpoint described below) and is **not** Phase-2-inert; **parent is required** on create + update; `cal_rule` is **level-3-only**; bulk **Delete + Deactivate** endpoints exist but bulk **Activate** does not; there is **no Excel export**. See [chart-of-accounts/refactor-gap-analysis.md](chart-of-accounts/refactor-gap-analysis.md) and the [final parity audit](../../audits/chart-of-accounts-final-parity-audit.md).

This spec mirrors the live JEX behaviour and is the functional source of truth for the refactor implementation. JEX source (read-only) is the second reference for any behaviour Figma does not capture. There is **no** corresponding `docs/jex/features/chart-of-accounts.md` today — JEX-side documentation never landed for `comm_0223_*` (the feature was added 2025-07/08 with no spec). This file therefore documents both sides; the JEX behaviour columns below are extracted directly from the `_act.jsp` action handlers + IDO XML.

---

## 1. Purpose

Per-tenant chart of accounts master list used by the WABOOKS accounting / financial-report modules to classify every booked transaction (banking, payment, card, e-invoice) under a hierarchical accounting code. Each tenant maintains its own tree; the three roots `ACC-01` (REVENUE), `ACC-02` (EXPENSES), `ACC-03` (INVESTMENT) are system-seeded and cannot be edited / deleted. All other nodes are user-created up to **four hierarchy levels** (PO cap).

Downstream readers in the JEX live system (kept here for context — none of these are in refactor scope yet):

| Consumer | Role |
|---|---|
| `ACCT_TRNS_HSTR` | Booked banking transactions reference `CHART_ACCT_CD` |
| `CARD_TRNS_HSTR` | Card transactions reference `CHART_ACCT_CD` |
| Financial report `fnsh_0200_001_r001` | Pulls `CHART_ACCT_LEVEL_NM`, `CHART_ACCT_NM`, `CHART_ACCT_CD` for the per-tenant Income Statement / Trial Balance view |

The refactor port covers the **CRUD master surface only** — the read-side joins from `ACCT_TRNS_HSTR` / reports land with their owning module ports.

---

## 2. User roles & permissions

| Role | Read | Create | Update | Delete | Notes |
|---|---|---|---|---|---|
| ADMIN (tenant) | ✓ | ✓ | ✓ | ✓ | Full CRUD. Restricted root codes (ACC-01/02/03) remain read-only via `WBE1178` regardless of role. |
| USER (tenant) | ✓ | ✗ | ✗ | ✗ | List-only access; toolbar Create / Edit / Delete CTAs hidden. (Legacy JEX does NOT gate by role today — every authenticated tenant user can edit. Refactor proposal flags this as **OQ-1**: confirm with Mr. Jung whether USER is read-only or full CRUD.) |

Multi-tenant isolation is enforced exclusively via `TenantContext.currentBizKey()` per [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Auth Rules" — no request body or query parameter may override the active tenant.

---

## 3. Screen flow

The page mounts inside the Settings modal shell — there is no top-level `/chart-of-accounts` route per [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §3a. Sidebar entry: **Settings → Chart of Accounts** (same nesting tier as VAS Account / Purpose / Contacts / Approval Line).

```
Settings modal opens (top-nav → Settings)
        │
        ▼
SettingsNav clicks "Chart of Accounts"
        │
        ▼
<ChartOfAccountsPanel>
        │
        ├─ Header (title + subtitle + "+ Add A New Account" CTA + Excel Upload chip)
        ├─ Filter row (SearchInput on Code / EN name)
        ├─ Table (sticky thead, scrollable body)
        │      ├─ Inline create row (tfoot) opens on "+ Add A New Account"
        │      └─ Per-row inline edit (Edit pencil) replaces row with input cells
        └─ (no pagination — load-all matches legacy; tree-render in flat order)
```

**No detail page.** Edits happen inline in the table row (matches legacy JEX `comm_0223_001_view.jsp` — see [the create / update `<tfoot>` block lines 226–322](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0223_001_view.jsp#L226-L322)). Confirmation / error feedback routes through the shared `useDialog()` system dialog.

### 3.1 Excel bulk upload

A secondary "Excel Upload" chip (legacy `comm_0223_002`) opens the shared [`<ExcelUploadDialog>`](../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx) per [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §3a "Excel Upload preview modals" (the canonical example wrapper is already implemented at [wecms-web/components/settings/chart-of-accounts/ExcelUploadDialog.tsx](../../../../wecms-web/components/settings/chart-of-accounts/ExcelUploadDialog.tsx) on the static-UI side). The refactor backend wires the corresponding bulk-import endpoint in Phase 2 of the plan — Phase 1 keeps the dialog inert (Upload button disabled).

---

## 4. Business rules

### 4.1 Identifier rules

| Field | Rule | Source |
|---|---|---|
| `CHART_ACCT_SEQ_NO` | Per-tenant integer surrogate PK, allocated via `wb_get_next_val('chart_acct_infm','chart_acct_seq_no', BIZ_KEY)` (MAX+1). Stored as `VARCHAR(14)` — refactor entity types this as `Long` and casts at IDO call boundary, matching the V91 / `appr_line_no` precedent. | DEV introspection (2026-05-13) |
| `CHART_ACCT_CD` | User-supplied, 1–20 chars (legacy view shows "0/10 Characters" hint — soft cap of 10 in the UI but DB allows 20; refactor honours the DB cap, frontend keeps the 10-char counter chip). Trimmed, NBSP-sanitised. **Per-tenant uniqueness among non-deleted rows** enforced by `uk_chart_acct_cd (BIZ_KEY, CHART_ACCT_CD, DEL_YN)`. | [comm_0223_001_c001_act.jsp:40-56](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_c001_act.jsp#L40-L56) |
| `PARENT_CHART_ACCT_CD` | Nullable. When non-blank, must reference an existing active row in the same tenant. Self-reference is **not** blocked by the legacy code (a row can be its own parent in DEV — see seq 104 `2001001 → 2001001`). The refactor MUST reject self-reference (**proposed change**, OQ-2). | [comm_0223_001_c001_act.jsp:98-115](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_c001_act.jsp#L98-L115) |
| Hierarchy depth | Maximum **4 levels** (PO requirement, baked into the legacy R002 query). Anything deeper is hidden from the parent-account picker but not actively blocked on Create — refactor MUST validate at write time. | [IDO.CHART_ACCT_INFM_R002.xml:62-99](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_R002.xml#L62-L99) |
| `STTS` | `A` Active / `I` Inactive. CHAR(1). Required on create + update. | DEV |
| `CAL_RULE` | Nullable. `P` (+) or `M` (-) — used for sign aggregation in financial reports. Frontend displays `+` / `-` via `data-key`/`data-val` mapping. | [comm_0223_001_view.jsp:271-292](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0223_001_view.jsp#L271-L292) |
| `DEL_YN` | `N` Active / `Y` Soft-deleted. `DEFAULT 'N'`. List queries filter `DEL_YN='N'`. | DEV |

### 4.2 Restricted root codes

| Code | Legacy EN name | Behaviour |
|---|---|---|
| `ACC-01` | REVENUE | Cannot be updated, cannot be deleted. Service throws `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` (refactor) / `WBE1178` (legacy). Created by initial tenant seed with `REG_ID = BIZ_KEY`. |
| `ACC-02` | EXPENSES | Same. |
| `ACC-03` | INVESTMENT | Same. |

These three codes are **always** present in every tenant. The list MUST surface them as level-1 roots even when the tenant has no other accounts. **Pinning is by literal code match** — the refactor MUST mirror this exact set (`Set.of("ACC-01","ACC-02","ACC-03")`) rather than a generic "system-seeded" flag, since the legacy data carries no such flag.

### 4.3 Code uniqueness — soft-delete interplay

The unique key is `(BIZ_KEY, CHART_ACCT_CD, DEL_YN)` — so a code can have at most one active row AND at most one soft-deleted row of the same code. The legacy Create path exploits this:

1. If an active row with the requested code exists → `WBE0003` (duplicate).
2. Else if a deleted row with the same code exists → **revive** that row (clear `DEL_YN`, overwrite fields, bump `MOD_DTM`). No new row is inserted; the same `CHART_ACCT_SEQ_NO` is reused.
3. Else → insert a new row with a freshly allocated `CHART_ACCT_SEQ_NO`.

The legacy Delete path symmetrically defends the UK: before flipping `DEL_YN='Y'`, if a deleted row with the same code already exists in the tenant, the row being deleted gets its code **renamed** to an archived form (`prefix(0..4) + last5(seq_no) + 'D'`) so two deleted rows never collide on `(BIZ_KEY, code, 'Y')`. See [comm_0223_001_d001_act.jsp:120-126](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_d001_act.jsp#L120-L126).

The refactor MUST preserve both behaviours — they're the only mechanism keeping the UK from blowing up over the lifetime of a tenant.

### 4.4 Delete cascade — re-parent children

When a node with children is deleted:

1. Read all rows where `PARENT_CHART_ACCT_CD = <deleting node code>` AND `DEL_YN='N'` AND same tenant.
2. For each child, set its `PARENT_CHART_ACCT_CD` to the **deleting node's own parent** (`existingAccount.getParentChartAcctCd()`) — i.e. promote one level up.
3. If the deleting node was itself a root (parent is NULL), every child becomes a root too.

This is the entire cascade — the legacy code does NOT recursively delete descendants. Per [comm_0223_001_d001_act.jsp:178-211](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_d001_act.jsp#L178-L211).

### 4.5 Update — parent change

When `PARENT_CHART_ACCT_CD` changes on Update:

- New parent must exist (active, same tenant) → else `WBE0001`.
- **The legacy code carries a quirky "parent cleanup" block** ([comm_0223_001_u001_act.jsp:237-256](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_u001_act.jsp#L237-L256)) that nullifies the parent of an unrelated row in certain self-referencing cases. The branch reads ambiguously and the DEV `System.out.println` calls suggest it was instrumented mid-debugging. The refactor MUST NOT port this block verbatim — it produces silent edits to rows the user didn't touch. **OQ-3**: confirm with Mr. Jung whether the cleanup is intentional business logic or leftover debugging.

### 4.6 Validation summary (per action)

| Action | Required fields | Optional | Business validations |
|---|---|---|---|
| Create (`POST`) | `chart_acct_cd`, `chart_acct_nm_en`, `parent_chart_acct_cd`, `stts` | `chart_acct_nm_vn`, `chart_acct_nm_ko`, `chart_acct_type`, `cal_rule` | Code unique among active rows · parent exists if non-blank · depth ≤ 4 |
| Update (`PUT`) | `chart_acct_cd`, `chart_acct_nm_en`, `stts` | `chart_acct_nm_vn`, `chart_acct_nm_ko`, `chart_acct_type`, `parent_chart_acct_cd`, `cal_rule` | Row exists in tenant · NOT a restricted code · code unique on change · parent exists if non-blank · depth ≤ 4 after edit |
| Delete (`DELETE`) | — (id in path) | — | Row exists in tenant · NOT a restricted code · re-parent any children · rename own code if a deleted dup exists |

Legacy "required" markers are loose — Create flags `PARENT_CHART_ACCT_CD` as required but then guards it with `isNotBlank` (root nodes need NULL). The refactor MUST treat parent as optional on Create (NULL = root) and reject the legacy validator quirk; this is a clarification, not a behaviour change.

---

## 5. Edge cases

| Case | JEX behaviour | Refactor behaviour |
|---|---|---|
| Create code matching a soft-deleted code in same tenant | Revives the deleted row | Same — atomic UPDATE inside `@Transactional` create flow |
| Create code matching an active code | `WBE0003` | Same |
| Delete a root with children | Children's parent set to NULL (become roots) | Same |
| Delete a level-2 node with children | Children re-parented to level-2's parent (the level-1 root) | Same |
| Delete a leaf | Plain soft-delete | Same |
| Update changes parent to create a cycle (A → B → A) | NOT blocked in legacy | **MUST block** (refactor change, OQ-2). Walk the parent chain; reject on cycle. |
| Update changes parent so depth exceeds 4 | NOT blocked in legacy | **MUST block** (refactor change, OQ-2). Compute resulting depth before commit. |
| Two active rows in same tenant with same code | Prevented by UK; would surface as `DataIntegrityViolationException` | Same — handler returns `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` |
| Restricted code edit / delete | `WBE1178` | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` (`Settings.chart_of_accounts.restricted_code`) |
| Race: two concurrent Creates with same code | DB UK rejects the second; service throws `WbRollbackException` | Same; service catches `DataIntegrityViolationException`, retries once with a fresh `seq_no` allocation, surfaces `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` on second failure |
| Tenant with zero rows | List returns empty array; legacy renders empty `<tbody>` | List returns empty; frontend renders the 3 restricted roots from a static seed reference (TBD — see OQ-4) OR the backend seeds them on first read |

---

## 6. ML codes / i18n

Legacy ML codes from [comm_0223_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0223_001_view.jsp) — translated to slug-based keys per [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Refactor identifier naming". Update `wecms-web/locales/{en,ko,vi}.json` in the same MR as the panel.

| Legacy ML | Refactor key | EN |
|---|---|---|
| `comm_0223_001_ml001` | `settings.chart_of_accounts.title` | Chart of Accounts |
| `comm_0223_001_ml002` | `settings.chart_of_accounts.subtitle` | Setup Reporting Accounts to record financial data |
| `comm_0223_001_ml003` | `settings.chart_of_accounts.search_placeholder` | Search |
| `comm_0223_001_ml004` | `settings.chart_of_accounts.excel_upload` | Excel Upload |
| `comm_0223_001_ml005..009` | `settings.chart_of_accounts.column.{account_code,name_en,name_vn,name_ko,parent_account}` | Account Code / Account Name (English) / … |
| `comm_0223_001_ml010` | `settings.chart_of_accounts.column.status` | Status |
| `comm_0223_001_ml019` | `settings.chart_of_accounts.add_button` | Add A New Account |
| `comm_0223_001_ml020` | `settings.chart_of_accounts.column.cal_rule` | Cal Rule |
| `comm_0223_001_l001` / `_l002` | `settings.chart_of_accounts.status.active` / `.inactive` | Active / Inactive |
| `comm_0223_001_b001..004` | `settings.chart_of_accounts.button.{cancel,save,cancel_edit,save_edit}` | Cancel / Save |
| `comm_0223_001_p001..005` | `settings.chart_of_accounts.placeholder.{code,name_en,name_vn,name_ko,parent}` | Enter Code* / Enter Name* (English) / … |
| `comm_0223_001_ml011` / `_ml012` | `settings.chart_of_accounts.error.code_in_use` / `.name_in_use` | Account Code is already in use / Account Name is already in use |

Restricted-code labels (REVENUE / EXPENSES / INVESTMENT) ship server-side via a per-tenant seed; per [§13a-bis](../../../../wecms-web/CLAUDE.md) the wire carries an `i18n_key` sibling (e.g. `settings.chart_of_accounts.restricted.acc_01`) so the frontend localises via `t(...)`.

---

## 7. Open questions for Mr. Jung (PO)

| # | Question | Recommended default | Impact if deferred |
|---|---|---|---|
| OQ-1 | Should USER (non-admin) have read-only Chart of Accounts, or full CRUD like JEX today? | Read-only (consistent with VAS Account / Purpose) | If kept full-CRUD, drop the `@PreAuthorize("hasRole('ADMIN')")` on write endpoints |
| OQ-2 | Refactor adds cycle detection + depth-cap validation on Update. Confirm? | Yes — legacy hole is a bug, not a feature | Without this, a user can break the level-4 cap by editing a parent post-create |
| OQ-3 | The legacy `comm_0223_001_u001_act.jsp:237-256` "parent cleanup" block silently nullifies an unrelated row's parent. Is this intentional? | Drop it — looks like leftover instrumentation | If intentional, port verbatim with regression tests covering the trigger path |
| OQ-4 | When a tenant has zero rows, should the backend auto-seed ACC-01/02/03 on first read, or rely on a Flyway seed at tenant-provisioning time? | Flyway seed at provisioning (matches legacy — they exist in DEV for every tenant) | Refactor's local Flyway needs a `INSERT … ON CONFLICT DO NOTHING` for the 3 roots in every seeded tenant migration |
| OQ-5 | Excel bulk upload — Phase 1 or Phase 2? | Phase 2 (Phase 1 keeps the dialog inert) | Phase-1 scope shrinks; the dialog still renders for visual parity |
| OQ-6 | View Setting popover for the Chart of Accounts panel — adopt, or skip like Contacts / Approval Line? | **Skip** — legacy view JSP toolbar has no list-icon button (same precedent as Approval Line) | If adopted, ship a `SYS_SCR_OPTION_WECMS` seed and a `<ViewSettingPopover>` wiring in the panel |
| OQ-7 | Sort field whitelist — default `CHART_ACCT_CD ASC` matches legacy ORDER BY in R001. Confirm? | Yes — sortable: `CHART_ACCT_CD`, `CHART_ACCT_NM_EN`, `STTS`, `MOD_DTM` | If different, update the `ChartOfAccountSortField` enum + seed migration |

---

## 8. Non-goals (this MR)

- Downstream report integration (`fnsh_0200_001`) — ports with the report module.
- `ACCT_TRNS_HSTR` / `CARD_TRNS_HSTR` FK validation — ports with banking / card modules.
- AUDIT_LOGS_INFM write path for chart-of-accounts changes — covered in [docs/refactor-wecms/backend/chart-of-accounts.md §"Audit logging"](../../references/business-rules/chart-of-accounts.md#audit-logging).
- Multi-language name input validation (e.g. KO name must contain Hangul) — out of scope; treat all three name columns as plain VARCHAR(255).
