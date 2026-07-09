# Operation Status Inquiry — Status Dropdown Legacy-Parity Audit

- **Feature:** Add-on Service → Operation Status → Operation Status Inquiry
- **Legacy page:** `aosv_0001_001` (`https://www.wecms.vn/aosv_0001_001.act`)
- **Refactor route:** `/addon/operation-status`
- **System tags:** JEX (legacy) ↔ Refactor (wecms-api + wecms-web)
- **Date:** 2026-06-30
- **Scope:** "Operation Status" filter dropdown only — label list, order, status codes/values, default option, and the filter-query mapping.
- **Type:** Documentation audit only. No code modified.

---

## Result: **FAIL**

The refactor dropdown ships **4 options**; the legacy dropdown ships **11**. The refactor is missing **8 legacy statuses**, introduces a **non-legacy "Suspended" label**, and uses **short Vietnamese fragments** as filter values that **collapse several distinct legacy statuses into one option** instead of filtering each one precisely. The label-spelling difference ("Temporally closed" vs "Temporarily closed") is real but secondary — the corrected spelling lands on the refactor side.

---

## 1. Legacy source files (`aosv_0001_001`)

| Artifact | Path |
|---|---|
| View JSP (dropdown markup) | [WECMS DEV/WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp](../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp) |
| TypeScript (status→display map) | [WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts) |
| Read action (filter WHERE clause) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp) |
| Update action (GDT pull → store) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_u001_act.jsp` |
| View service XML | `WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001.xml` |
| Read service XML | `WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml` |
| Read IDO XML | `WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml` |
| Entity | `WECMS DEV/WECMS/src/wabooks/entity/OprtSttsInfo.java` |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/OprtSttsInfoDAO.java` |

### 1.1 Is the dropdown hardcoded or data-driven?

**Hardcoded in the JSP `<ul>` markup.** It is NOT loaded from a common code group, a code table, or an API. The visible labels resolve via Jex Studio **ML codes** (`data-jex-ml="aosv_0001_001_mNNN"`); the *filter value* sent to the backend is the hardcoded Vietnamese phrase in each `<li data-filter="…">`.

### 1.2 The exact legacy mapping

Dropdown markup — [aosv_0001_001_view.jsp:109-125](../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp#L109-L125):

| Order | Display label (ML) | ML code | `data-key` | `data-filter` (value sent as `COMPANYSTATUS`) |
|---|---|---|---|---|
| 1 | All Operation Status | `aosv_0001_001_m009` | `""` (default, `class="selected"`) | *(none — no filter)* |
| 2 | Active | `aosv_0001_001_m010` | `A` | `NNT đang hoạt động` |
| 3 | Address mismatch | `aosv_0001_001_m014` | `N` | `NNT không hoạt động tại địa chỉ đã đăng ký` |
| 4 | Bankruptcy in progress | `aosv_0001_001_m012` | `N` | `NNT chờ làm thủ tục phá sản` |
| 5 | Closed | `aosv_0001_001_m018` | `N` | `NNT ngừng hoạt động và đã hoàn thành thủ tục chấm dứt hiệu lực MST` |
| 6 | On closing | `aosv_0001_001_m015` | `N` | `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục đóng MST` |
| 7 | Pending Address Verification | `aosv_0001_001_m019` | `N` | `NNT chờ xác minh tình trạng hoạt động tại địa chỉ đã đăng ký` |
| 8 | Pending Personal ID Update | `aosv_0001_001_m020` | `N` | `Mã số thuế chờ cập nhật thông tin số định danh cá nhân` |
| 9 | Tax Code Issued | `aosv_0001_001_m021` | `N` | `NNT đã được cấp MST` |
| 10 | Tax office changed | `aosv_0001_001_m013` | `N` | `NNT đã chuyển cơ quan thuế quản lý` |
| 11 | Temporally closed *(legacy spelling, sic)* | `aosv_0001_001_m017` | `N` | `NNT tạm nghỉ kinh doanh có thời hạn` |

Notes:
- Two `<li>` are **commented out** in the JSP and are not active options: `aosv_0001_001_v003` (`m011`, line 114) and `aosv_0001_001_v008` (`m016` "Closed", line 118).
- **`data-key` (`A`/`N`) is NOT the filter value.** It is the Active/Inactive indicator used for row display; the actual server filter is the `data-filter` Vietnamese phrase.
- The default "All Operation Status" has `data-key=""` and **no** `data-filter` → no filter, all rows returned.

### 1.3 Legacy filter mechanism

[aosv_0001_001_r001_act.jsp:47-52](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp#L47-L52):

```java
if (StringUtils.isNotBlank(input.getString("COMPANYSTATUS"))) {
    dQuery.append("\n and companystatus ilike '%'||?||'%' ");
    dParam.add(input.getString("COMPANYSTATUS"));
}
```

The selected option's `data-filter` Vietnamese phrase is sent as `COMPANYSTATUS` and applied as a case-insensitive partial-match (`ILIKE '%…%'`) against `OPRT_STTS_INFO.COMPANYSTATUS` — a **free-text** Vietnamese status string supplied by the GDT (Tổng cục Thuế / Vietnamese Tax Authority). Because each legacy option uses the **full distinctive phrase**, the partial match resolves to exactly one tax-status meaning per option (e.g. `…và đã hoàn thành…` = Closed vs `…nhưng chưa hoàn thành…` = On closing).

### 1.4 Display-side status map (table cell rendering, not the dropdown)

[aosv_0001_001.ts:58-72](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts#L58-L72) maps the raw GDT Vietnamese text → a display message + Active/Inactive flag for the result table. This is a separate concern from the filter dropdown but confirms the same code universe (and the legacy English typo `"Temporaily closed"` at `aosv_0001_001_s008`).

---

## 2. Refactor source files (`/addon/operation-status`)

### 2.1 Frontend (`wecms-web`)

| Artifact | Path |
|---|---|
| Page component + dropdown options | [wecms-web/components/addon/operation-status/OperationStatusPage.tsx](../../../wecms-web/components/addon/operation-status/OperationStatusPage.tsx) |
| i18n EN labels | [wecms-web/locales/en.json](../../../wecms-web/locales/en.json) |

Dropdown options — [OperationStatusPage.tsx:179-184](../../../wecms-web/components/addon/operation-status/OperationStatusPage.tsx#L179-L184):

```ts
const STATUS_FILTER_OPTIONS: StatusFilterOption[] = [
    { wireValue: null,             labelKey: "operation.filter.all_operation_status" },
    { wireValue: "đang hoạt động", labelKey: "operation.filter.active" },
    { wireValue: "ngừng",          labelKey: "operation.filter.suspended" },
    { wireValue: "tạm",            labelKey: "operation.filter.tam_suspended" },
];
```

EN labels — [en.json:1674-1677](../../../wecms-web/locales/en.json#L1674-L1677):

```json
"operation.filter.all_operation_status": "All Operation Status",
"operation.filter.active": "Active",
"operation.filter.suspended": "Suspended",
"operation.filter.tam_suspended": "Temporarily closed",
```

The selected option's `wireValue` is sent as the `companyStatus` query param; `null` = no filter.

### 2.2 Backend (`wecms-api`)

| Artifact | Path |
|---|---|
| Controller | `wecms-api/src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java` |
| Filter record | `wecms-api/src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusFilter.java` |
| Service (status-code resolver) | [wecms-api/.../operation/service/OperationStatusService.java](../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) |
| Repository (filter query) | `wecms-api/.../operation/repository/OprtSttsInfoRepository.java` |
| Entity | `wecms-api/.../operation/entity/OprtSttsInfo.java` |

Filter query — `OprtSttsInfoRepository.findFiltered`:

```sql
WHERE o.bizKey = :bizKey
  AND (:companyStatusPattern IS NULL
       OR LOWER(COALESCE(o.companystatus, '')) LIKE :companyStatusPattern ESCAPE '!')
  ...
```

→ Same mechanism as legacy: the wire value is lower-cased, wrapped in `%…%`, and partial-matched against `OPRT_STTS_INFO.COMPANYSTATUS` (`@Column(name = "COMPANYSTATUS")`).

Display-side status code resolver — [OperationStatusService.java:267-275](../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java#L267-L275):

```java
static String resolveStatusCd(String status) {
    if (status == null) return "not_registered";
    String s = status.toLowerCase(Locale.ROOT);
    if (s.contains("đang hoạt động")) return "active_certificate_issued";
    if (s.contains("ngừng hoạt động")) return "suspended";
    if (s.contains("tạm ngừng"))       return "suspended";
    if (s.contains("tạm nghỉ"))        return "suspended";
    return "not_registered";
}
```

The refactor collapses every status into a **3-state model** (`active_certificate_issued` / `suspended` / `not_registered`) — itself a divergence from the legacy 11-value model, but out of scope here except as it explains the merged dropdown.

---

## 3. Comparison table

Aligned to legacy display order. Legacy "code" = `data-filter` value actually sent as `COMPANYSTATUS`. Refactor "code" = `wireValue` sent as `companyStatus`.

| Order | Legacy Label | Legacy Code (`data-filter`) | Refactor Label | Refactor Code (`wireValue`) | Result |
|-------|--------------|-----------------------------|----------------|------------------------------|--------|
| 1 | All Operation Status | `""` (no filter) | All Operation Status | `null` (no filter) | ✅ Match |
| 2 | Active | `NNT đang hoạt động` | Active | `đang hoạt động` | ⚠️ Match (refactor uses substring of legacy phrase — still resolves to Active) |
| 3 | Address mismatch | `NNT không hoạt động tại địa chỉ đã đăng ký` | — | — | ❌ Missing |
| 4 | Bankruptcy in progress | `NNT chờ làm thủ tục phá sản` | — | — | ❌ Missing |
| 5 | Closed | `NNT ngừng hoạt động và đã hoàn thành thủ tục chấm dứt hiệu lực MST` | *(folded into "Suspended")* | `ngừng` (matches) | ❌ Mismatch — merged |
| 6 | On closing | `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục đóng MST` | *(folded into "Suspended")* | `ngừng` (matches) | ❌ Mismatch — merged |
| 7 | Pending Address Verification | `NNT chờ xác minh tình trạng hoạt động tại địa chỉ đã đăng ký` | — | — | ❌ Missing |
| 8 | Pending Personal ID Update | `Mã số thuế chờ cập nhật thông tin số định danh cá nhân` | — | — | ❌ Missing |
| 9 | Tax Code Issued | `NNT đã được cấp MST` | — | — | ❌ Missing |
| 10 | Tax office changed | `NNT đã chuyển cơ quan thuế quản lý` | — | — | ❌ Missing |
| 11 | Temporally closed *(sic)* | `NNT tạm nghỉ kinh doanh có thời hạn` | Temporarily closed | `tạm` (matches) | ⚠️ Mismatch — label spelling corrected; broad `tạm` fragment |
| — | *(none)* | — | Suspended | `ngừng` | ❌ Non-legacy label (no legacy counterpart) |

---

## 4. Identified mismatches

### 4.1 Missing legacy statuses (8)
Not present in the refactor at all: **Address mismatch, Bankruptcy in progress, Closed, On closing, Pending Address Verification, Pending Personal ID Update, Tax Code Issued, Tax office changed.**

### 4.2 Non-legacy "Suspended" label
"Suspended" (`operation.filter.suspended` → `ngừng`) **does not exist in the legacy dropdown**. It is a refactor-invented catch-all. Its fragment `ngừng` partial-matches BOTH legacy **Closed** (`…ngừng hoạt động và đã hoàn thành…`) and **On closing** (`…ngừng hoạt động nhưng chưa hoàn thành…`), silently merging two distinct legacy statuses into one user-facing option.

### 4.3 Renamed / mis-granular filter values
- **`tạm`** ("Temporarily closed") is a broad fragment of the legacy full phrase `NNT tạm nghỉ kinh doanh có thời hạn`. It also matches any `tạm ngừng` value, so it is not a faithful 1:1 to the single legacy "Temporally closed" status.
- **`đang hoạt động`** ("Active") is a substring of the legacy `NNT đang hoạt động`. Functionally equivalent here (it still matches Active), but note the legacy used the full phrase and the refactor's `resolveStatusCd` also maps several `đang hoạt động (…)` variants to active.

### 4.4 Label spelling — "Temporally closed" vs "Temporarily closed"
The **legacy label is the typo'd "Temporally closed"** (ML `aosv_0001_001_m017`; the TS fallback at `aosv_0001_001_s008` even reads `"Temporaily closed"`). The **refactor uses the grammatically correct "Temporarily closed."** For strict label parity the refactor diverges; however the refactor spelling is the *correct* English. This is a product decision (keep the legacy typo for byte-parity, or keep the correction) — flag to the PO; do not silently choose.

### 4.5 Wrong order / count
Refactor presents 4 options; legacy presents 11. Even the 3 statuses the refactor does cover are presented in a reduced/merged set, not the legacy order 1–11.

---

## 5. Recommendation — implementation plan to match legacy

**Goal:** reproduce the legacy 11-option dropdown exactly (labels, order, and the full-phrase filter values), preserving the existing `COMPANYSTATUS ILIKE '%…%'` mechanism (which both sides already share).

### 5.1 Frontend (`wecms-web`) — primary change
Replace `STATUS_FILTER_OPTIONS` in [OperationStatusPage.tsx:179-184](../../../wecms-web/components/addon/operation-status/OperationStatusPage.tsx#L179-L184) with the legacy 11-option set, **using the full legacy `data-filter` phrase as the `wireValue`** so each option filters to one status:

| Order | `labelKey` (new EN value) | `wireValue` (full legacy phrase) |
|---|---|---|
| 1 | `operation.filter.all_operation_status` → "All Operation Status" | `null` |
| 2 | `operation.filter.active` → "Active" | `NNT đang hoạt động` |
| 3 | `operation.filter.address_mismatch` → "Address mismatch" | `NNT không hoạt động tại địa chỉ đã đăng ký` |
| 4 | `operation.filter.bankruptcy_in_progress` → "Bankruptcy in progress" | `NNT chờ làm thủ tục phá sản` |
| 5 | `operation.filter.closed` → "Closed" | `NNT ngừng hoạt động và đã hoàn thành thủ tục chấm dứt hiệu lực MST` |
| 6 | `operation.filter.on_closing` → "On closing" | `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục đóng MST` |
| 7 | `operation.filter.pending_address_verification` → "Pending Address Verification" | `NNT chờ xác minh tình trạng hoạt động tại địa chỉ đã đăng ký` |
| 8 | `operation.filter.pending_personal_id_update` → "Pending Personal ID Update" | `Mã số thuế chờ cập nhật thông tin số định danh cá nhân` |
| 9 | `operation.filter.tax_code_issued` → "Tax Code Issued" | `NNT đã được cấp MST` |
| 10 | `operation.filter.tax_office_changed` → "Tax office changed" | `NNT đã chuyển cơ quan thuế quản lý` |
| 11 | `operation.filter.temporarily_closed` → "Temporarily closed" *(see §4.4 — confirm spelling with PO)* | `NNT tạm nghỉ kinh doanh có thời hạn` |

- **Remove** the invented `operation.filter.suspended` / "Suspended" option (see §5.4).
- Add the new EN keys to `en.json`, plus matching `ko.json` / `vi.json` entries (do not invent translations — use verified legacy ML values; for VI, the underlying `data-filter` IS the Vietnamese text, so VI labels can mirror legacy ML).
- Keep the long Vietnamese strings as `wireValue` constants; they are filter values, not display text, so they are exempt from the "no hardcoded text" UI rule the same way the existing `đang hoạt động` literal already is.

### 5.2 Backend (`wecms-api`) — no contract change required for the filter
The `companyStatus` param + `LIKE '%…%'` query already accept any string, so the 11 full phrases work without a controller/DTO/repository change. **However:** the result-row `resolveStatusCd` ([OperationStatusService.java:267-275](../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java#L267-L275)) still only emits a 3-state code. If the result-table column must display the **legacy 11-state label** (not just the dropdown), that resolver needs to be extended to the full legacy status map (see [aosv_0001_001.ts:58-72](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts#L58-L72)). That is a **separate decision** — the dropdown-parity fix in §5.1 does not require it, but the result rendering will otherwise stay coarser than legacy. Confirm scope with the PO before changing backend code (per the backend Scope Guard).

### 5.3 Coordination
This is a frontend-only change to reach dropdown parity (§5.1). It does **not** require a paired backend change unless §5.2 (full status-code resolution) is also in scope. Keep the feature slug `addon-operation-status` identical across trees.

### 5.4 On "Suspended" — remove or keep?
**Recommendation: REMOVE.** The legacy source has no "Suspended" label, and its `ngừng` fragment ambiguously merges legacy **Closed** + **On closing**. There is no legacy status whose canonical text is "Suspended" — so keeping it cannot be justified from the legacy UI/spec.

The only condition under which a "Suspended"-style consolidated option should be **kept** is if **backend data proves** the `OPRT_STTS_INFO.COMPANYSTATUS` column actually stores `ngừng`-family values that do NOT match any of the 11 full legacy phrases (i.e. a real GDT status the legacy dropdown never enumerated). To verify, query the live data (read-only) for the tenant before deciding:

```sql
-- run read-only against the JEX DEV schema (postgres_dev_server_jexframe MCP), LIMIT enforced
SELECT DISTINCT companystatus
FROM oprt_stts_info
WHERE companystatus ILIKE '%ngừng%'
LIMIT 50;
```

If every distinct value matches one of legacy options 5/6/11, **remove "Suspended"** and use the precise legacy options. If a residual `ngừng` value exists that legacy never enumerated, raise it with the PO (Mr. Jung) — it would be a new status beyond the legacy spec, not a parity item.

---

## 6. Summary

| Aspect | Legacy | Refactor | Verdict |
|---|---|---|---|
| Option count | 11 | 4 | ❌ |
| Source | Hardcoded JSP + ML labels | Hardcoded TS + i18n | (both hardcoded) |
| Filter mechanism | `COMPANYSTATUS ILIKE '%…%'` | `companystatus LIKE '%…%'` | ✅ same |
| Filter value granularity | Full distinctive Vietnamese phrase per status | Short shared fragments (merge statuses) | ❌ |
| Default option | `""` → no filter | `null` → no filter | ✅ |
| "Suspended" | absent | present (invented) | ❌ remove |
| "Temporally closed" spelling | typo "Temporally"/"Temporaily" | corrected "Temporarily" | ⚠️ PO decision |
| Missing statuses | — | 8 | ❌ |

**Overall: FAIL — frontend dropdown must be expanded to the legacy 11-option set with full-phrase filter values (§5.1); "Suspended" removed unless live data proves otherwise (§5.4).**
