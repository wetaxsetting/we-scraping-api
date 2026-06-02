# Approval Line — Add-flow legacy analysis

| Field | Value |
|---|---|
| Date | 2026-05-13 |
| Slug | `approval-line` |
| Legacy WSVCs analysed | `comm_0222_002` (Add Approval Line popup + `_c001` create action) · `comm_0192_001_r001` (Inquiry Approver — the tenant-user picker source) |
| Companion docs | [features/approval/approval-line.md](../../../features/settings/approval-line.md) · [backend/approval-line.md](../../business-rules/approval-line.md) · [migration/approval-line.md](../approval-line.md) |
| What was already shipped | Master + child CRUD service, REST controller, Flyway V90–V92, frontend panel/dialog/table/popover wiring (uses static `APPROVER_POOL` fallback) |
| What this analysis informs | The single missing piece — the tenant-users endpoint that the Add Approver popover should read from |

This document covers the **Add Approval Line flow specifically** — list / detail / update / delete are documented in the broader migration/approval-line.md (Needs Confirmation). The focus here is the user-picker behaviour the legacy `comm_0222_002` view drives via `comm_0192_001_r001`.

---

## 1. Screen / Web behaviour

### 1.1 Approval Line list screen

Legacy: [`comm_0222_001_view.jsp`](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_001_view.jsp) + [`comm_0222_001.ts:14-104`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_001.ts).

- Table renders `comm_0222_001_r001` rows: Approval Line Name · Approver (name + count chip) · Create Date · row actions Edit / Delete.
- Header search input `#SRCH_WD` + button `#BTN_SRCH_WD`; pressing Enter or clicking the button reloads the list with `SRCH_WD: <keyword>`.
- "+ Add Approval line" button `#ADD_APPROVAL_LINE` opens the Add popup via `_v2common.app.openLayerPopup("comm_0222_002.act")`.
- Row Edit `#EDIT_APPR` opens `comm_0222_003.act?APPR_LINE_NO=<id>` (the Update popup; covered in migration/approval-line.md (Needs Confirmation), out of scope here).
- Row Delete `#DELETE_APPR` shows `_v2common.app.showMessage('WBC1172', ...)` (confirmation), then calls `comm_0222_001_d001` on confirm.
- The "Approver" cell renders the primary approver name with a `+N` chip + chevron when the chain has more than one row; the full chain expands inline via `#user_appr_info` on click (the popover is HTML pre-rendered from `comm_0222_001_r001`'s `REC1` sub-record).

### 1.2 Add Approval Line popup — Figma 9196:11969 / 9196:11547

Legacy: [`comm_0222_002_view.jsp`](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_002_view.jsp) + [`comm_0222_002.ts`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts).

Layout (top → bottom):

1. **Header** — title "Add Approval line" + Esc / X close affordance.
2. **Name input** `#approval_title` — `<input>` styled as a hero (large font), placeholder "Enter approval line name". Required for Save to enable.
3. **"+ Add Approver"** button `.show-appr-btn` — toggles the picker dropdown (`.show-appr-btn` adds `show` class).
4. **Picker dropdown** (anchored to the Add Approver button) — 260 px wide, contains:
   - Search input `#user-search` — 1000 ms debounce → `comm_0222_002.fn_listUser(keyword)` → `comm_0192_001_r001.jct` (see § 3.2).
   - Count row: `{n} of 5 selected` + "Clear all" link.
   - `<ul>#list-user`: one `<li>` per user with `<label>{USER_NM}</label>` + a `.checkBox-normal` checkbox. Each `<li>` carries `data-user-id`, `data-user-nm`, `data-user-dept_nm`, `data-user-posi_nm` so the check event can hydrate `selectedUsers`.
   - Empty state `#list-not-found` when `REC1` is empty.
   - Footer Add button `.total-selete-appr2` — disabled at 0 selected; brand-blue "Add N Approvers" at ≥ 1.
5. **Drag-instruction strip** — neutral banner: "You can adjust the payment order by dragging and dropping with your mouse." (Figma 9196:11969 panel header inside the chain table).
6. **Approver chain table** `.ks-table-sortable`:
   - Columns: drag handle · `#` (order) · Name · Position · Department · Remove button.
   - Drag-and-drop reorder via jQuery UI `sortable()` — `tbody.sortable({...})` ([`comm_0222_002.ts:289-319`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)).
   - Reorder triggers a re-numbering of the `#` column ([`comm_0222_002.ts:236`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)).
   - Remove button `.btn-remove-approver` removes the row, decrements the chain count, renumbers the `#` column, and unchecks the matching `<li>` in the picker so the same user can be re-added.
   - When the chain is empty, the tbody renders 5 ghost rows so the table area keeps its height ([`comm_0222_002.ts:197-205`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)).
7. **Footer** — `{n} of 5` badge (`.num_of_appr`) + "Approvers assigned" label + Cancel + Save button.

### 1.3 Add Approver button behaviour

- `.show-appr-btn` click → adds `show` class to the trigger ([`comm_0222_002.ts:58-60`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)).
- Document-level click outside `.outside-add-appr` → removes `show` (closes the dropdown — [`comm_0222_002.ts:63-66`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)).
- Once the user clicks the picker's Add button, `comm_0222_002.loadData()` runs (rebuilds the chain table from `selectedUsers`) and then hides the picker by removing `show` ([`comm_0222_002.ts:126-133`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)).
- Picker stays visually open across multiple checkbox toggles — the user can pick several rows in one open.

### 1.4 Save / Cancel / Close behaviour

- **Cancel** — `_v2common.app.closeLayerPopup()` (no body button shown in TS — handled by the popup framework's outer chrome).
- **Close (Esc / X)** — same as Cancel.
- **Save** — `.btn_save_appr` click → `comm_0222_002.fn_saveAppr()` ([`comm_0222_002.ts:135-140, 402-436`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)):
  - Reads `#approval_title` and every `tbody.ui-sortable tr` (in DOM order — already reflects drag-and-drop reorder).
  - Builds payload `{ APPR_LINE_NM, REC: [{ APPRVR_DISP_ORD, APPRVR_ID, APPRVR_NM, APPRVR_POSI, APPRVR_DEPT }] }`.
  - POSTs to `comm_0222_002_c001.jct`.
  - On success: `_v2common.app.showMessage("WBI6002", ...)` → `closeLayerPopup()` → `comm_0222_001.fnSelect()` (reload the list behind the popup).

### 1.5 Drag-and-drop approver order

Legacy: [`comm_0222_002.ts:286-330`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts).

- jQuery UI `sortable()` initialised on `.ks-table-sortable tbody`.
- `handle: 'td.ks-icon-move'` — only the leftmost cell starts the drag (the "grip" glyph).
- `helper` clone is built per row so column widths stay sane while dragging.
- `placeholder: 'ui-sortable-placeholder'` — dashed-border row of the same height as the dragged row.
- On `update`, every `<tr>` `<td:eq(1)>` is re-written to `(index + 1)` so the `#` column always shows 1..N in DOM order ([`comm_0222_002.ts:236`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)).
- No server roundtrip during reorder; the final order is read from the DOM at Save time.

### 1.6 Validation rules

Client-side (legacy):

| Rule | Where enforced | Message |
|---|---|---|
| `approval_title` (APPR_LINE_NM) must be non-blank | `checkButtonSave()` ([`comm_0222_002.ts:485-494`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)) — disables Save button | n/a (Save button stays disabled) |
| At least one approver in the chain | `checkButtonSave()` | n/a (Save disabled) |
| Max 5 approvers (`maxAllowed = 5`) | Picker checkbox change handler ([`comm_0222_002.ts:69-81`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)) unchecks the 6th selection | silent |

Server-side ([`comm_0222_002_c001_act.jsp:28-105`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp)):

| Rule | Where enforced | Error code |
|---|---|---|
| `APPR_LINE_NM` required | `validateInput(util, input)` → `ValidationHelper.validateRequired` | `WBE_VALIDATION` (legacy) |
| `REC` record present | `ValidationHelper.validateRecord` | `WBE_VALIDATION` |
| For each row: `APPRVR_DISP_ORD`, `APPRVR_ID`, `APPRVR_NM` required | `validateRecItem` | `WBE_VALIDATION` |
| `REC.size() <= 5` | `comm_0222_002_c001_act.jsp:90-92` | `WBW1164` |
| PK uniqueness `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)` | DB constraint | `WBE_DB` (raw) |

### 1.7 Maximum approver count rule — "0 of 5"

- The "0 of 5 selected" label inside the picker is rendered by [`comm_0222_002.ts:122`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) on Clear-all and updated by [`comm_0222_002.ts:101-103`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) on every checkbox change.
- The "0 of 5" badge inside the chain table footer (`.num_of_appr`) is updated by [`comm_0222_002.ts:158, 397-398`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) on row add / remove.
- ML keys `comm_0222_002_ml001` / `ml002` / `ml003` / `ml004` resolve the localised strings.

### 1.8 Required fields, duplicate handling, alerts

- **Required**: `APPR_LINE_NM` + ≥ 1 approver. No constraint on Position / Department (they're optional snapshots).
- **Duplicate approvers**: legacy enforces uniqueness implicitly through the picker's `selectedUsers` array (the checkbox change handler uses `findIndex(user => user.id === userId)` and skips duplicates — [`comm_0222_002.ts:84-93`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts)). If a duplicate `APPRVR_ID` ever slipped through to the server, the PK constraint `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)` would reject the second INSERT — but that path is not user-reachable today.
- **Confirm / alerts**: only on Save success — `WBI6002` (generic "Saved successfully"). No confirmation before Save.

---

## 2. JavaScript functions

### 2.1 Approval-line list page — `comm_0222_001.ts`

| Function / handler | File:line | Role |
|---|---|---|
| `comm_0222_001.onload()` | [`comm_0222_001.ts:36-44`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_001.ts) | Boot: ML, sort plugin, initial `load()`. |
| `comm_0222_001.event()` | [`comm_0222_001.ts:50-103`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_001.ts) | Wires `#BTN_SRCH_WD`, `#SRCH_WD` keypress, `#ADD_APPROVAL_LINE`, `#EDIT_APPR`, `#user_appr_info`, `#DELETE_APPR`. |
| `comm_0222_001.loadData(_input?)` | [`comm_0222_001.ts:105+`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_001.ts) | Ajax → `comm_0222_001_r001.jct`, renders rows. |
| `comm_0222_001.fnSelect()` | (referenced from popup save) | Re-runs `loadData()` after a child popup committed. |
| `comm_0222_001.fnDetails(jsonData)` | (referenced) | Builds the inline approver-detail HTML when the row chevron is clicked. |
| `comm_0222_001.deleteApprNo(input)` | (referenced) | Ajax → `comm_0222_001_d001.jct`. |

### 2.2 Add Approval Line popup — `comm_0222_002.ts`

| Function / handler | File:line | Role |
|---|---|---|
| `onload()` | [`comm_0222_002.ts:35-38`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Boot: ML map, calls `load()`. |
| `load()` | [`comm_0222_002.ts:40-44`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | `event()` + `loadData()` + `fn_listUser()`. |
| `event()` | [`comm_0222_002.ts:46-256`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Handlers (see below). |
| **Picker** keydown on `#user-search` (1000 ms debounce) | [`comm_0222_002.ts:48-55`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Calls `fn_listUser(keyword)`. |
| **Picker** show / hide via `.show-appr-btn` + outside click | [`comm_0222_002.ts:58-66`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Toggles the dropdown's `show` class. |
| **Picker** checkbox change | [`comm_0222_002.ts:69-112`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Hard cap of 5 (unchecks the 6th); maintains `selectedUsers[]` with `{id, name, dept, posi}`; updates count / Add-button labels. |
| **Picker** Clear all (`.btn-clear-check`) | [`comm_0222_002.ts:117-124`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Resets `selectedUsers = []`, unchecks every `.checkBox-normal`, resets labels. |
| **Picker** Add (`.total-selete-appr2`) | [`comm_0222_002.ts:126-133`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Calls `loadData()` (rebuilds chain table from `selectedUsers`), `checkButtonSave()`, hides picker. |
| **Save** (`.btn_save_appr`) | [`comm_0222_002.ts:135-140, 402-436`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | `fn_saveAppr()` (see § 3.3). |
| **Title input** `#approval_title` | [`comm_0222_002.ts:142-144`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Triggers `checkButtonSave()` on input. |
| **Remove** row (`.btn-remove-approver`) | [`comm_0222_002.ts:146-208`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Removes the row, renumbers `#`, unchecks the matching picker row, updates labels, refills empty state if chain is now empty. |
| **Sortable** init (`.ks-table-sortable tbody.sortable(...)`) | [`comm_0222_002.ts:286-330`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | jQuery UI drag-and-drop reorder with the grip-cell handle. |
| **Tooltip** mouseenter / mouseleave on `.ks-tooltip-trigger` | [`comm_0222_002.ts:240-282`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Builds the info-icon tooltip (not behavioural; visual only). |
| `loadData()` | [`comm_0222_002.ts:340-400`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Renders the chain table from `selectedUsers[]`. |
| `fn_listUser(keyword?)` | [`comm_0222_002.ts:438-483`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | **Ajax → `comm_0192_001_r001.jct`** with `{ USER_NM: keyword }`; renders `<li>` rows; re-checks any already-selected ids. |
| `fn_saveAppr()` | [`comm_0222_002.ts:402-436`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Builds the `{APPR_LINE_NM, REC[]}` payload and POSTs to `comm_0222_002_c001.jct`. |
| `checkButtonSave()` | [`comm_0222_002.ts:485-494`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_002.ts) | Disables / enables Save based on title + chain non-empty. |

### 2.3 Multi-language keys used by the picker (legacy IDs)

| ML id | Default | Used by |
|---|---|---|
| `comm_0222_002_ml001` | `Add #{Amount} Approvers` | Picker Add button — assigned state |
| `comm_0222_002_ml002` | `0 of 5 selected` | Picker count label — empty state |
| `comm_0222_002_ml003` | `#{Amount} of #{Max} selected` | Picker count label — selected |
| `comm_0222_002_ml004` | `Add` | Picker Add button — disabled |

Refactor key map (verified against current `wecms-web/locales/en.json` — every legacy ML has a slug-based counterpart):

| Legacy ML | Refactor key |
|---|---|
| `comm_0222_002_ml001` | `settings.approval_line.add_dialog.add_count` |
| `comm_0222_002_ml002` | `settings.approval_line.add_dialog.selected_count` |
| `comm_0222_002_ml003` | `settings.approval_line.add_dialog.selected_count` |
| `comm_0222_002_ml004` | `settings.approval_line.add_dialog.add_short` |
| (n/a — search input copy) | `settings.approval_line.add_dialog.search_placeholder` |

---

## 3. API behaviour

### 3.1 `comm_0222_001_r001` — List

Covered in migration/approval-line.md § 1 (Needs Confirmation). Refactor: `GET /api/v1/settings/approval-line`.

### 3.2 `comm_0192_001_r001` — Inquiry Approver (tenant-user picker)

The Add Approver dropdown calls this every time the user types (after a 1000 ms debounce) and on dropdown open.

**Endpoint**: `comm_0192_001_r001.jct` (POST gateway).

**Source files**:

- WSVC: [`WSVC.comm_0192_001_r001.xml`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0192_001_r001.xml)
- Action: [`comm_0192_001_r001_act.jsp`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_001_r001_act.jsp)
- IDO: [`IDO.USER_BUSI_CONN_INFM_R004.xml`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.USER_BUSI_CONN_INFM_R004.xml)

**Input** (WSVC `<in>`):

| Field | Length | Notes |
|---|---|---|
| `BLTB_CD` | 15 | **Unused** — `validateRequired` is commented out. Carried only because the legacy WSVC template required a non-empty input. |
| `USER_NM` | 100 | Optional. Triggers `ILIKE '%?%'` against `USER_INFM.USER_NM`. |

**Output** (WSVC `<out>` + IDO):

| Field | Length | Source |
|---|---|---|
| `USER_ID` | 100 | `USER_INFM.USER_ID` |
| `USER_NM` | 100 | `USER_INFM.USER_NM` |
| `CLPH_NO` | 100 | `decrypt(USER_INFM.CLPH_NO)` — only meaningful when legacy `decrypt()` is installed |
| `USER_IMG_URL` | 200 | Computed in the JSP — `szon_0012_003_r002.act?USER_ID={id}&cmd=getthumb` when `USER_IMG` is non-blank; otherwise omitted |
| `USER_IMG` | 500 | `USER_BUSI_CONN_INFM.USER_IMG` — only used to decide whether to emit `USER_IMG_URL` |
| `POSI_NM` | 100 | `USER_INFM.POSI_NM` |
| `DEPT_NM` | 100 | `USER_INFM.DEPT_NM` |

**SQL essence** ([`IDO.USER_BUSI_CONN_INFM_R004.xml:24-62`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.USER_BUSI_CONN_INFM_R004.xml)):

```sql
SELECT USER_ID, USER_NM, CLPH_NO, ADMIN_YN, MOB_YN, USER_IMG, POSI_NM, DEPT_NM
FROM (
    SELECT
        UI.USER_ID, UI.USER_NM, decrypt(UI.CLPH_NO) AS CLPH_NO,
        (CASE WHEN (SELECT COUNT(*) FROM BUSI_ADMN_INFM BAI
                    WHERE BAI.BIZ_KEY = UBCI.BIZ_KEY AND BAI.USER_ID = UBCI.USER_ID) > 0
              THEN 'Y' ELSE 'N' END) ADMIN_YN,
        (CASE WHEN (SELECT COUNT(*) FROM PUSH_DEVICE_MSTR PDM
                    WHERE PDM.BIZ_KEY = UBCI.BIZ_KEY AND PDM.USER_ID = UBCI.USER_ID) > 0
              THEN 'Y' ELSE 'N' END) MOB_YN,
        UBCI.USER_IMG, UI.POSI_NM, UI.DEPT_NM
    FROM USER_INFM UI
    JOIN USER_BUSI_CONN_INFM UBCI ON UI.USER_ID = UBCI.USER_ID
    WHERE 1=1
    ??         -- DYNAMIC_0 (tenant + name filter, see below)
) T
WHERE 1=1
??             -- DYNAMIC_1 (unused in the comm_0192_001_r001 path)
```

**Dynamic WHERE** ([`comm_0192_001_r001_act.jsp:38-53`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_001_r001_act.jsp)):

```
and ubci.biz_key = <loginUser.bizKey>
and ubci.del_yn = 'N'
[and ui.user_nm ilike '%' || <USER_NM> || '%']    -- only when USER_NM is non-blank
```

**Response shape** (envelope omitted):

```json
{
  "REC1": [
    { "USER_ID": "...", "USER_NM": "...", "CLPH_NO": "...",
      "USER_IMG": "...", "USER_IMG_URL": "...", "POSI_NM": "...", "DEPT_NM": "..." }
  ]
}
```

**Error / transaction handling**: read-only — no transaction begin / commit; exceptions bubble up to the gateway and become `WBE9999` envelopes.

### 3.3 `comm_0222_002_c001` — Create Approval Line

Covered in migration/approval-line.md § 1 (Needs Confirmation). Refactor: `POST /api/v1/settings/approval-line`. The legacy and refactor request shapes are documented there.

---

## 4. Database logic

### 4.1 Tables touched by the Add flow

| Table | Role |
|---|---|
| `APPR_LINE_INFM` | Master row written by `comm_0222_002_c001`. Already documented in [features/approval/approval-line.md § 3.1](../../../features/settings/approval-line.md). |
| `APPR_LINE_INFM_DETL` | Approver chain written by `comm_0222_002_c001`. Already documented in [features/approval/approval-line.md § 3.2](../../../features/settings/approval-line.md). |
| `USER_INFM` (read) | Picker source: `USER_ID`, `USER_NM`, `POSI_NM`, `DEPT_NM`, `CLPH_NO`. |
| `USER_BUSI_CONN_INFM` (read) | Tenant-link / `DEL_YN` / `USER_IMG`. Picker JOIN partner. |
| `BUSI_ADMN_INFM` (read) | ADMIN_YN sub-flag — not consumed by the Add Approver picker (it's an inherited column from `USER_BUSI_CONN_INFM_R004`). |
| `PUSH_DEVICE_MSTR` (read) | MOB_YN sub-flag — same. |

### 4.2 Key columns

| Column | Notes |
|---|---|
| `APPR_LINE_INFM.APPR_LINE_NO` | Per-tenant surrogate. Generated by `wb_get_next_val('appr_line_infm','appr_line_no', biz_key)` — refactor port lives in V91. |
| `APPR_LINE_INFM_DETL.APPRVR_DISP_ORD` | 1..5 — server-assigned from request-array position (refactor) / from `(index + 1)` at save time (legacy). DEV column type `NUMERIC(1,0)`; refactor V92 converted to `INTEGER`. |
| `APPR_LINE_INFM_DETL.APPRVR_ID` | FK (logical, not enforced) to `USER_INFM.USER_ID`. PK component. |
| `BIZ_KEY` | Tenant scope on every table involved. Read from `loginUser.getBizKey()` (JEX) / `TenantContext.currentBizKey()` (refactor). |
| `USER_BUSI_CONN_INFM.DEL_YN` | Soft-delete sentinel. `'N'` for active links. The picker filters `del_yn = 'N'`. |

### 4.3 Audit columns

| Column | Source on write |
|---|---|
| `REG_ID` / `REG_DTM` | `loginUser.getId()` + `DateTimeUtils.utcNow()` on INSERT |
| `MOD_ID` / `MOD_DTM` | Same on INSERT (legacy sets both at create time); `mod_dtm` rewritten on subsequent UPDATEs |

Refactor preserves this column-for-column — `ApprovalLineService.utcNow14()` formats `yyyyMMddHHmmss` UTC.

### 4.4 Read-only DEV evidence (verified 2026-05-13)

`USER_INFM` has both `POSI_NM` + `DEPT_NM` (`VARCHAR(100)`) — already mapped on the refactor's `UserInfm` entity ([UserInfm.java:69-74](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java)). No new column work needed.

---

## 5. New Spring implementation proposal

### 5.1 What is already shipped

The whole CRUD path for `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL` is already merged ([backend/approval-line.md](../../business-rules/approval-line.md)) — five endpoints under `/api/v1/settings/approval-line`, V90–V92 migrations, `ApprovalLineConstants.SLUG = "approval-line"`. The Add flow's wire shape, validation rules, and audit policy are all settled and live.

What this analysis adds: a **single missing endpoint** that the legacy "Add Approver" picker depends on — `comm_0192_001_r001`. The refactor frontend currently falls back to a static `APPROVER_POOL`; this section is the proposal for what the picker should call instead.

### 5.2 Tenant-users picker endpoint — design

| Item | Value |
|---|---|
| HTTP | `GET /api/v1/users` |
| Auth | JWT (default) |
| Query | `q?: string` — optional case-insensitive substring match against `USER_NM` |
| Tenant | `TenantContext.currentBizKey()` — never on the wire |
| Response | `ApiResponse<TenantUserListResponse>` with `{ total_count, users[] }` (snake_case wire) |
| Errors | `WBE_VALIDATION` (bad `q`), `WBE9999` (server) |
| Audit | None (read-only) |
| Pagination | None — same precedent as approval-line list (small directories per tenant; revisit if tenant counts ever spike) |

#### Java DTO shape

```java
public record TenantUserRow(
    String userId,
    String userNm,
    String posiNm,
    String deptNm,
    String userImgUrl    // null when USER_BUSI_CONN_INFM.USER_IMG is null
) {}

public record TenantUserListResponse(
    int                  totalCount,
    List<TenantUserRow>  users
) {}
```

Wire JSON:

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "total_count": 6,
    "users": [
      { "user_id": "user1@example.com", "user_nm": "User One",
        "posi_nm": "Manager", "dept_nm": "Finance", "user_img_url": null }
    ]
  }
}
```

#### Controller / service / repo layout

```
domain/auth/
├── controller/UserController.java         — GET /api/v1/users
├── service/UserDirectoryService.java      — tenant-scoped lookup
├── dto/TenantUserRow.java
└── dto/TenantUserListResponse.java
```

Repository: extends `UserBusiConnInfmRepository` with one JPQL `@Query` joining `UserInfm` on `USER_ID` so the position / department columns come from `USER_INFM` while tenant scope + `USER_IMG` come from `USER_BUSI_CONN_INFM`. Returns a projection interface or a JPQL constructor expression — avoid pulling whole entities for what's effectively a read-only view.

Default order: `USER_NM ASC, USER_ID ASC` (the picker is name-sorted; tie-break on id for determinism).

`user_img_url` resolution rule: same as Approval Line list (feature § 4.5 / OQ-1) — return `/api/v1/users/{userId}/image` when `USER_BUSI_CONN_INFM.USER_IMG` is non-null, else `null`. The streaming endpoint itself remains a follow-up MR.

#### Validation

`@RequestParam(name = "q") @Size(max = 100)` — matches the legacy WSVC `USER_NM` length cap. No `@NotBlank`; blank / missing `q` is a valid full-directory query.

#### Transaction boundary

`@Transactional(readOnly = true)` on the service method. Read-only; no audit.

#### Error envelope

Standard `ApiResponse<T>` with `status.error = true` on failure. Codes: `WBE_VALIDATION` for bad input, `WBE9999` for unexpected runtime. No feature-specific WBE codes — the surface is trivial enough to not warrant any.

---

## 6. Implementation checklist (this MR)

1. ✅ Backend: `UserController` + `UserDirectoryService` + DTOs + repo method.
2. ✅ Frontend: `lib/types/api.ts` adds `ApiTenantUserRow` / `ApiTenantUserListResponse`. `lib/api/users.ts` adds `fetchTenantUsers(q?)`.
3. ✅ `ApprovalLineDialog.tsx`: replace the static `APPROVER_POOL` import with a fetched-on-mount tenant directory; pass the result to `<AddApproverPopover>` as `available` (filtered against `approvers` already in the chain — same logic as today).
4. ✅ `approverMockData.ts`: keep `MAX_APPROVERS` and the `ApproverRow` type; demote `APPROVER_POOL` to an offline-dev affordance (so the dialog still renders without a backend on a dev's laptop).
5. ✅ Build / type-check / lint clean.

Out of scope for this MR (documented for follow-ups):

- The `/api/v1/users/{userId}/image` streaming endpoint (OQ-1) — the URL string in `user_img_url` is honored by the frontend's existing `<Avatar>` fallback (null → icon).
- Server-side pagination on `/api/v1/users` — picker stays small enough not to need it.
- The drag-and-drop reorder inside the Approver chain table — the static UI ships the grip glyph today; actual DnD wiring would happen in a follow-up. Server-side ordering is already correct (1..N from array position on every Save).

---

## 7. Verified facts (legacy ↔ refactor crosscheck)

| Legacy fact | Refactor counterpart | Verified |
|---|---|---|
| `comm_0192_001_r001` filters `ubci.biz_key = ? AND ubci.del_yn = 'N'` | `TenantContext.currentBizKey()` + `del_yn = 'N'` in JPQL `@Query` | ✅ |
| Optional `ILIKE '%?%'` on `USER_NM` | Spring Data `containsIgnoreCase` derived keyword (emits `LOWER(...) LIKE LOWER(...)` on Postgres — same semantic) | ✅ |
| Picker payload field set: `USER_ID`, `USER_NM`, `POSI_NM`, `DEPT_NM`, `USER_IMG_URL` | `TenantUserRow.{userId,userNm,posiNm,deptNm,userImgUrl}` | ✅ |
| Max 5 approvers (legacy `maxAllowed = 5`) | `ApprovalLineConstants.MAX_APPROVERS = 5` | ✅ (already shipped) |
| `APPRVR_DISP_ORD` re-numbered from DOM order at save time | Server assigns `1..N` from request-array position | ✅ (already shipped — see backend § 3.5) |
| Save endpoint `comm_0222_002_c001` | `POST /api/v1/settings/approval-line` | ✅ (already shipped) |
| `WBI6002` "Saved successfully" toast | Refactor `t("settings.approval_line.create_success")` via `useDialog()` success | ✅ (already shipped) |
| `CLPH_NO` decryption via legacy `decrypt()` | **Dropped** — refactor picker does not surface `CLPH_NO` (Approval Line UI doesn't display phone numbers); avoids a new legacy DB-function dependency | ✅ deliberate divergence |
| ADMIN_YN + MOB_YN sub-flags from `USER_BUSI_CONN_INFM_R004` | **Dropped** — not consumed by the Approval Line picker | ✅ deliberate divergence |
