# Operation Status Inquiry — Status COLUMN Rendering Legacy-Parity Fix

- **Feature:** Add-on Service → Operation Status → Operation Status Inquiry
- **Legacy page:** `aosv_0001_001` · **Refactor route:** `/addon/operation-status`
- **Date:** 2026-06-30 · **Scope:** result-table **Operation Status column** rendering only (filtering already fixed in the dropdown-parity pass — unchanged here).
- **Companion audit:** [operation-status-inquiry-status-dropdown-legacy-audit.md](operation-status-inquiry-status-dropdown-legacy-audit.md)

## Problem

The result-table column rendered the refactor's invented **3-state model** (`active_certificate_issued` / `suspended` / `not_registered`) with an always-green check icon. A row whose GDT `COMPANYSTATUS` was `NNT tạm nghỉ kinh doanh có thời hạn` showed **🟢 Suspended** instead of the legacy **🔴 Temporarily closed**.

## Legacy rendering model (source of truth)

Legacy maps the **exact** Vietnamese `COMPANYSTATUS` → `{ msg, status }` via the `aosv_0001_001.companyStatus` object ([aosv_0001_001.ts:58-72](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts#L58-L72)); rendering at [aosv_0001_001.ts:242-285](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts#L242-L285):

| `status` value | text colour | icon |
|---|---|---|
| `active` | green (`cGreen`) | **none** |
| `Inactive` | red (`cRed`) | red error icon (`icon_error.svg`) |
| `""` (unknown — exact key not in map) | default | none; shows raw `COMPANYSTATUS` verbatim |

13 exact statuses are mapped (3 active + 10 inactive); anything else falls through to the raw-string branch.

## Fix — exact-match resolver + legacy presentation

The mapping moved to an **exact-match** resolver (mirrors the legacy object-key lookup, not a substring scan). Backend ships a stable code; the frontend localises the label and colours by an active/inactive classification (green/no-icon vs red/error-icon), with unknown rendering the raw GDT string.

### Mapping — before vs after

| GDT `COMPANYSTATUS` (exact) | Legacy `status` | Legacy label | **Before** (code → render) | **After** (code → render) |
|---|---|---|---|---|
| NNT đang hoạt động | active | Active | `active_certificate_issued` → 🟢 "Active - Certificate issued" | `active` → 🟢 **Active** (no icon) |
| NNT đang hoạt động (đã được cấp GCN ĐKT) | active | Certificate issued | `active_certificate_issued` → 🟢 | `active_certificate_issued` → 🟢 **Active - Certificate issued** (no icon) |
| NNT đang hoạt động (được cấp thông báo MST) | active | Tax registration noticed | `active_certificate_issued` → 🟢 | `active_tax_notice_issued` → 🟢 **Active - Tax registration notice issued** (no icon) |
| NNT không hoạt động tại địa chỉ đã đăng ký | Inactive | Address mismatch | `not_registered` → ⚪ "Not registered" | `address_mismatch` → 🔴 **Address mismatch** |
| NNT chờ làm thủ tục phá sản | Inactive | Bankruptcy in progress | `not_registered` → ⚪ | `bankruptcy_in_progress` → 🔴 **Bankruptcy in progress** |
| NNT ngừng hoạt động và đã đóng MST | Inactive | Closed | `suspended` → 🟢 "Suspended" | `closed` → 🔴 **Closed** |
| NNT ngừng hoạt động và đã hoàn thành thủ tục chấm dứt hiệu lực MST | Inactive | (Closed variant) | `suspended` → 🟢 "Suspended" | `mst_terminated` → 🔴 **Closed - Tax code terminated** |
| NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục đóng MST | Inactive | On closing | `suspended` → 🟢 "Suspended" | `on_closing` → 🔴 **On closing** |
| NNT chờ xác minh tình trạng hoạt động tại địa chỉ đã đăng ký | Inactive | Pending Address Verification | `not_registered` → ⚪ | `pending_address_verification` → 🔴 **Pending Address Verification** |
| Mã số thuế chờ cập nhật thông tin số định danh cá nhân | Inactive | Pending Personal ID Update | `not_registered` → ⚪ | `pending_personal_id_update` → 🔴 **Pending Personal ID Update** |
| NNT đã được cấp MST | Inactive | Tax Code Issued | `not_registered` → ⚪ | `tax_code_issued` → 🔴 **Tax Code Issued** |
| NNT đã chuyển cơ quan thuế quản lý | Inactive | Tax office changed | `not_registered` → ⚪ | `tax_office_changed` → 🔴 **Tax office changed** |
| **NNT tạm nghỉ kinh doanh có thời hạn** | Inactive | Temporarily closed | **`suspended` → 🟢 "Suspended"** | **`temporarily_closed` → 🔴 "Temporarily closed"** ✅ |
| (any other string) | "" | — | merged into one of the 3 buckets | `unknown` → raw `COMPANYSTATUS` verbatim, no icon |

🟢 = green text · 🔴 = red text + error icon · ⚪ = muted, no icon. Legacy "Temporarily closed" uses the typo "Temporally"/"Temporaily"; the refactor keeps the corrected spelling per the dropdown audit decision.

## Changed files

**Backend (`wecms-api`)**
- `domain/operation/service/OperationStatusService.java` — `resolveStatusCd` rewritten: exact-match `LEGACY_STATUS_CODES` map (13 statuses), unknown → `"unknown"`. 3-state model removed.
- `src/test/java/.../OperationStatusServiceTest.java` — assertion updated (`suspended` → `mst_terminated`).

**Frontend (`wecms-web`)**
- `components/addon/operation-status/types.ts` — `OperationStatusCode` → 14-code union; `OPERATION_STATUS_ACTIVE_CODES` set; `OperationCompanyRow.companyStatusRaw` added.
- `lib/api/mappers.ts` — `coerceOpStatus` validates against the code set, defaults to `"unknown"` (no longer "active"); row carries `companyStatusRaw`.
- `components/addon/operation-status/OperationStatusPage.tsx` — `OperationStatusCell` renders legacy presentation (active green/no-icon, inactive red + `CircleX`, unknown muted raw); export formatter handles `unknown`.
- `components/addon/operation-status/InquireOperationStatusDialog.tsx` — tone/banner/icon/label maps updated to the new code set (Inquire popup stays consistent with the column).
- `locales/{en,ko,vi}.json` — `operation.status.*` keys: EN ships all 13; KO/VI keep `active`/`active_certificate_issued`/`temporarily_closed` (verified), other 10 fall back to EN (verified-ML follow-up); removed `suspended`/`not_registered`.

## Verification

- Backend: `./gradlew compileJava compileTestJava test --tests OperationStatusServiceTest --rerun-tasks` → **BUILD SUCCESSFUL** (4 tasks executed).
- Frontend: `tsc --noEmit` → exit 0; `eslint` (changed files) → 0 errors; locale JSON parse OK.

## Follow-up

KO/VI labels for the 10 inactive statuses fall back to English. Obtain the verified KO/VI from Jex Studio ML registrations (`aosv_0001_001_s001`–`s014`) and add them.
