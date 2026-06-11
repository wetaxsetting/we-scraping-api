# Approval Line — Interface Specification

REST contract for `/api/v1/settings/approval-line`. Controller:
[ApprovalLineController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/controller/ApprovalLineController.java).
Wire is lowercase snake_case (global Jackson `SNAKE_CASE`); the standard `{ status, data }` envelope
applies (errors add `timestamp` / `path` / `trace_id`). Frontend consumption:
[wecms-web/docs/features/settings/approval-line/api-consumption.md](../../../../../wecms-web/docs/features/settings/approval-line/api-consumption.md).

---

## Authorization

- Every endpoint is JWT-protected (global rule). Tenant is `TenantContext.currentBizKey()` — **never**
  a request param.
- The controller carries class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")`
  (F-3 menu-auth rollout, 2026-06-06). No per-method override; no admin-role gate.
- `{appr_line_no}` path variable is `@Positive Long` (class is `@Validated`).

---

## Endpoints

| # | Method | URI | Purpose |
|---|---|---|---|
| 1 | `GET` | `/api/v1/settings/approval-line` | List (name search + sort; no pagination) |
| 2 | `GET` | `/api/v1/settings/approval-line/{appr_line_no}` | Read one (master + nested chain) |
| 3 | `POST` | `/api/v1/settings/approval-line` | Create (master + 1–5 approvers, single tx) |
| 4 | `PUT` | `/api/v1/settings/approval-line/{appr_line_no}` | Update (rename + diff-sync, single tx) |
| 5 | `DELETE` | `/api/v1/settings/approval-line/{appr_line_no}` | Hard delete (master + detail, single tx) |

### Sort

Query params `sort_field` + `sort_dir` (`ASC` / `DESC`), resolved through the `ApprovalLineSortField`
whitelist — lenient, falls back to default on unknown/blank (never echoes input into SQL).

| `sort_field` token | Entity property | Notes |
|---|---|---|
| `REG_DTM` | `regDtm` | default `DESC` |
| `APPR_LINE_NM` | `apprLineNm` | optional |
| `MOD_DTM` | `modDtm` | optional |

Default ORDER BY (no/unknown field): `REG_DTM DESC, APPR_LINE_NO DESC`. A chosen field appends
`APPR_LINE_NO DESC` as a deterministic tie-break.

---

## 1. List — `GET /api/v1/settings/approval-line`

**Query:** `q` (optional name filter, case-insensitive `LIKE`), `sort_field`, `sort_dir`.

**Response** `data: ApprovalLineListResponse`:

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "total_count": 2,
    "approval_lines": [
      {
        "appr_line_no": 1001,
        "appr_line_nm": "Standard Payment Approval",
        "reg_dtm": "20260513091200",
        "approvers": [
          {
            "apprvr_disp_ord": 1,
            "apprvr_id": "manager01",
            "apprvr_nm": "Nguyen Van A",
            "apprvr_posi": "Manager",
            "apprvr_dept": "Finance",
            "user_img_url": "/api/v1/users/manager01/image"
          },
          {
            "apprvr_disp_ord": 2,
            "apprvr_id": "director01",
            "apprvr_nm": "Tran Thi B",
            "apprvr_posi": "Director",
            "apprvr_dept": "Finance",
            "user_img_url": null
          }
        ]
      }
    ]
  }
}
```

`total_count` = `approval_lines.size()` (no pagination). `approvers` is always ordered
`apprvr_disp_ord ASC`. `user_img_url` is `null` when the approver has no stored image.

## 2. Detail — `GET …/{appr_line_no}`

`data: ApprovalLineRow` — the single line object (same shape as a list row above). Unknown id (or a
row owned by another tenant) → `WBE_APPROVAL_LINE_NOT_FOUND`.

## 3. Create — `POST …`

**Body** `ApprovalLineCreateRequest`:

```json
{
  "appr_line_nm": "Standard Payment Approval",
  "approvers": [
    { "apprvr_id": "manager01", "apprvr_nm": "Nguyen Van A", "apprvr_posi": "Manager",  "apprvr_dept": "Finance" },
    { "apprvr_id": "director01", "apprvr_nm": "Tran Thi B",  "apprvr_posi": "Director", "apprvr_dept": "Finance" }
  ]
}
```

- `appr_line_nm` — `@NotBlank @Size(max=100)`.
- `approvers` — `@NotNull @Size(min=1, max=5) @Valid`.
- Per approver — `apprvr_id` / `apprvr_nm` `@NotBlank @Size(max=100)`; `apprvr_posi` / `apprvr_dept` optional `@Size(max=100)`.
- **No `apprvr_disp_ord`** in the request — server assigns `1..N` by array order.

**Response** `data: ApprovalLineRow` — the created line (re-read after save).

## 4. Update — `PUT …/{appr_line_no}`

**Body** `ApprovalLineUpdateRequest` — identical shape/validation to Create. `appr_line_no` comes from
the path, not the body. The approver set is **diff-synced** (UPDATE survivors, INSERT new, DELETE
removed); the array order determines `apprvr_disp_ord`. **Response** = the updated `ApprovalLineRow`.

## 5. Delete — `DELETE …/{appr_line_no}`

No body. **Response** `data: null` (`ApiResponse.ok(null)`). Hard delete of master + all detail rows.
Unknown id → `WBE_APPROVAL_LINE_NOT_FOUND`. No in-use guard.

---

## DTO inventory (Java records)

| DTO | Fields (Java → wire) |
|---|---|
| `ApprovalLineListResponse` | `totalCount → total_count`, `approvalLines → approval_lines[]` |
| `ApprovalLineRow` | `apprLineNo`, `apprLineNm`, `regDtm`, `approvers[]` |
| `ApprovalLineApproverRow` | `apprvrDispOrd`, `apprvrId`, `apprvrNm`, `apprvrPosi`, `apprvrDept`, `userImgUrl` |
| `ApprovalLineCreateRequest` | `@NotBlank @Size(max=100) apprLineNm`; `@NotNull @Size(min=1,max=5) @Valid approvers[]` |
| `ApprovalLineUpdateRequest` | identical shape to Create |
| `ApprovalLineApproverInput` | `@NotBlank @Size(max=100) apprvrId`; `@NotBlank @Size(max=100) apprvrNm`; `@Size(max=100) apprvrPosi`; `@Size(max=100) apprvrDept` |

No per-field `@JsonProperty` — the global SNAKE_CASE strategy produces every wire name.

---

## Error-code registry

All business errors return HTTP 200 with `status.error = true` (frontend reads `status.code`).
Registered in `messages.properties` / `messages_en.properties`:

| Code | EN message | Trigger |
|---|---|---|
| `WBE_APPROVAL_LINE_NOT_FOUND` | Approval line not found. | Detail / Update / Delete on an unknown id (or wrong tenant) |
| `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` | Approval line cannot have more than 5 approvers. | `approvers.size() > 5` |
| `WBE_APPROVAL_LINE_DUP_APPROVER` | Approval line cannot have the same approver more than once ({0}). | duplicate `apprvr_id` in the request |
| `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` | Approver does not belong to the current company ({0}). | `apprvr_id` with no active `USER_BUSI_CONN_INFM` link |
| `WBE_APPROVAL_LINE_EMPTY_APPROVERS` | Approval line must have at least one approver. | `approvers[]` empty/missing |
| `WBE_APPROVAL_LINE_NAME_BLANK` | Approval line name is required. | blank `appr_line_nm` |

Bean-validation failures (e.g. `@Size`) that bypass the typed guards fall through to `WBE_VALIDATION`;
missing tenant/user context or an unparseable generated key surfaces `WBE9999`. Standard envelope +
`X-Trace-Id` per the global `GlobalExceptionHandler`.
