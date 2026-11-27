# WeCMS Mobile (`WABOOKS_MREC`) — Legacy Gateway → Spring Migration Mapping

| Field | Value |
|---|---|
| Scope | The 76 mobile services the WeCMS iOS / Android apps call, mapped onto `wecms-api` |
| Source of truth | `WeCMS_Mobile_INOUT정의서(영수증관리API)_v2024.1.46.xlsx` — the API document handed over with the mobile source (`WeCMS_Handover_Answer & Attachments/API Document/`) |
| Cross-checked against | `WeCMS_iOS.zip` · `WeCMS_AOS.zip` (which service ids the apps actually call) and `src/main/**/*Controller.java` (422 endpoints / 49 controllers) |
| Status | **Reference only.** No mobile endpoint is implemented in this repository yet; this document is the contract inventory a mobile-facing API would have to satisfy. |
| Field-level spec | [auth.md](auth.md) · [master-data.md](master-data.md) · [receipt.md](receipt.md) · [notification-push.md](notification-push.md) · [bulletin-board.md](bulletin-board.md) |

Legacy service ids (`WABOOKS_MREC_*`) appear here as **historical pointers** only, per the workspace identifier-naming rule. Field names are quoted in the legacy `UPPER_SNAKE` form used by the mobile gateway; any refactor endpoint that serves them returns lowercase `snake_case` JSON through the global Jackson strategy (see [docs/README.md](../../../README.md) §field-name casing).

---

## 1 · Transport contract (legacy mobile gateway)

The apps do **not** call a REST path per operation. Every request is a `POST` to a single gateway URL; the operation is selected by the `API_SVC_ID` field in the body.

| Element | Value |
|---|---|
| URL | `c_base_url` — resolved at runtime from the MG config service (`c_base_url_wecms` / `_uat`), never hard-coded in the app |
| Method | `POST` for every service |
| Content-Type | `application/json` |
| Headers | `Accept-Language`, `X-App-Version`, `Authorization: {TOKEN_TYPE} {ACCESS_TOKEN}` (from `WABOOKS_MREC_R002`) |
| Session | Cookie jar + bearer token. **No refresh token** — when the session expires the app silently re-logs in with stored credentials (handover answer item 1.9) |

Request envelope:

```json
{
  "API_CRTS_KEY": "<static client key>",
  "API_SVC_ID":   "WABOOKS_MREC_R002",
  "USER_ID":      "<logged-in user>",
  "TIME_ZONE":    "Asia/Ho_Chi_Minh",
  "NTNL_CD":      "VN",
  "REQ_DATA":     { }
}
```

Response envelope: `RSLT_CD` + `RSLT_MSG` plus the flat output fields of the service. Collections come back under `REC` (and `REC2` when a service returns two lists, e.g. pinned vs normal posts). That is the legacy shape — a refactor-side implementation would use the standard `ApiResponse<T>` envelope instead, which is a deliberate divergence, not a parity gap.

| Platform | Where the envelope is defined |
|---|---|
| iOS | `WABOOKS_iOS_V2/Helper/Networking/Constant.swift` (enum `APIKey`) · `DataAccess.swift` `getURLRequest` / `encodeQueryString` |
| Android | `app/src/main/java/com/wabooks/mobile/utils/Const.kt` (`object API_SVC_ID`) · `dto/base/BaseRequest.kt` |

## 2 · Coverage against the current backend

| | Count |
|---|---|
| Mobile services in the workbook | **76** (75 rows in the `목록` index + `D080`, which has a sheet but is missing from the index) |
| Input / output fields specified | **366 / 734** (1100 total) |
| Services with a filled-in response-code table | **0 / 76** — every sheet carries the `Response Code Define` heading and nothing under it (matches handover answer item 1.7: the error-code table was never delivered) |
| Services the shipped apps actually call | **75 / 76** — only `R011` is documented but not referenced in either app |

| Coverage | Services | Meaning |
|---|---|---|
| `AVAILABLE` | **5** | An equivalent endpoint exists; only envelope / field mapping is needed |
| `PARTIAL` | **32** | An endpoint exists but the business rule, filter or field set diverges |
| `MISSING` | **38** | Nothing in `wecms-api` serves this |
| `NOT USED` | **1** | The workbook marks the service dropped |

| Group | Services | `AVAILABLE` | `PARTIAL` | `MISSING` | `NOT USED` |
|---|---|---|---|---|---|
| [Auth / account](auth.md) | 10 | 0 | 4 | 6 | 0 |
| [Master data / settings](master-data.md) | 14 | 3 | 7 | 4 | 0 |
| [Receipt - the app's core flow](receipt.md) | 10 | 0 | 8 | 1 | 1 |
| [Notification / push](notification-push.md) | 5 | 2 | 1 | 2 | 0 |
| [Board / post / approval post](bulletin-board.md) | 37 | 0 | 12 | 25 | 0 |

The receipt and master-data groups largely have a foothold already, because the refactor writes the same `BUSA_DOC_MSTR` / `USAG_INFM` / `BUSI_ENV_SET_INFM` tables. The bulletin-board group (37 services, roughly half the inventory) has no counterpart at all apart from the approval branch, which overlaps `payment/approval-box`. Push registration and the SMS-OTP account-recovery flow are absent as well.

## 3 · Service index

| # | Legacy service | Purpose | In/Out | Refactor endpoint | Coverage |
|---|---|---|---|---|---|
| 1 | [`WABOOKS_MREC_R001`](auth.md#wabooks_mrec_r001) | Company lookup for a user (user id + password) | 2/7 | `POST /api/v1/auth/login` | `PARTIAL` |
| 2 | [`WABOOKS_MREC_R002`](auth.md#wabooks_mrec_r002) | Log in with the selected company - returns token + profile | 3/17 | `POST /api/v1/auth/login` | `PARTIAL` |
| 3 | [`WABOOKS_MREC_R003`](auth.md#wabooks_mrec_r003) | Log out | 1/0 | `POST /api/v1/auth/logout` | `PARTIAL` |
| 4 | [`WABOOKS_MREC_MBMR_0002_R001`](auth.md#wabooks_mrec_mbmr_0002_r001) | Find ID - request an SMS OTP | 2/1 | — | `MISSING` |
| 5 | [`WABOOKS_MREC_MBMR_0002_R002`](auth.md#wabooks_mrec_mbmr_0002_r002) | Find ID - verify the OTP | 3/1 | — | `MISSING` |
| 6 | [`WABOOKS_MREC_MBMR_0002_R003`](auth.md#wabooks_mrec_mbmr_0002_r003) | Find ID - return the user id | 2/5 | — | `MISSING` |
| 7 | [`WABOOKS_MREC_MBMR_0003_R001`](auth.md#wabooks_mrec_mbmr_0003_r001) | Reset password - request an SMS OTP | 2/1 | — | `MISSING` |
| 8 | [`WABOOKS_MREC_MBMR_0003_R002`](auth.md#wabooks_mrec_mbmr_0003_r002) | Reset password - verify the OTP | 3/2 | — | `MISSING` |
| 9 | [`WABOOKS_MREC_MBMR_0003_R003`](auth.md#wabooks_mrec_mbmr_0003_r003) | Reset password - pre-change lookup | 4/1 | — | `MISSING` |
| 10 | [`WABOOKS_MREC_MBMR_0003_U001`](auth.md#wabooks_mrec_mbmr_0003_u001) | Reset password - apply the new password | 4/0 | `POST /api/v1/auth/change-password` | `PARTIAL` |
| 11 | [`WABOOKS_MREC_R004`](master-data.md#wabooks_mrec_r004) | Read the receipt screen's input-field configuration | 0/8 | `GET /api/v1/screen-options/{webSvcId}/{dymAreaId}` | `PARTIAL` |
| 12 | [`WABOOKS_MREC_C001`](master-data.md#wabooks_mrec_c001) | Save the receipt screen's input-field configuration | 8/0 | `PUT /api/v1/screen-options/{webSvcId}/{dymAreaId}` | `PARTIAL` |
| 13 | [`WABOOKS_MREC_R005`](master-data.md#wabooks_mrec_r005) | Purpose (expense-usage) list | 2/11 | `GET /api/v1/settings/purpose-settings` | `PARTIAL` |
| 14 | [`WABOOKS_MREC_R008`](master-data.md#wabooks_mrec_r008) | Bank list with logo | 4/4 | `GET /api/v1/settings/contacts/banks` | `PARTIAL` |
| 15 | [`WABOOKS_MREC_R011`](master-data.md#wabooks_mrec_r011) | Country list | 1/3 | `GET /api/v1/common/countries` | `PARTIAL` |
| 16 | [`WABOOKS_MREC_R020`](master-data.md#wabooks_mrec_r020) | Dial-code list | 1/4 | `GET /api/v1/common/countries` | `AVAILABLE` |
| 17 | [`WABOOKS_MREC_R012`](master-data.md#wabooks_mrec_r012) | Read a business environment setting | 1/1 | `GET /api/v1/settings/environment-settings/{env_set_inf}` | `AVAILABLE` |
| 18 | [`WABOOKS_MREC_R030`](master-data.md#wabooks_mrec_r030) | Contact list | 2/43 | `GET /api/v1/settings/contacts` | `PARTIAL` |
| 19 | [`WABOOKS_MREC_C080`](master-data.md#wabooks_mrec_c080) | Add a contact to favourites | 1/0 | — | `MISSING` |
| 20 | [`WABOOKS_MREC_D080`](master-data.md#wabooks_mrec_d080) | Remove a contact from favourites | 1/0 | — | `MISSING` |
| 21 | [`WABOOKS_MREC_R040`](master-data.md#wabooks_mrec_r040) | Exchange rates | 3/5 | `GET /api/v1/banking/exchange-rates` | `PARTIAL` |
| 22 | [`WABOOKS_MREC_R050`](master-data.md#wabooks_mrec_r050) | Generate the next Expense document number | 0/1 | `GET /api/v1/payment/card-transactions/next-doc-no` | `AVAILABLE` |
| 23 | [`WABOOKS_MREC_R070`](master-data.md#wabooks_mrec_r070) | Per-user payment methods (advance / imprest ledger) | 0/4 | — | `MISSING` |
| 24 | [`WABOOKS_MREC_R901`](master-data.md#wabooks_mrec_r901) | Withholding-tax configuration | 1/4 | — | `MISSING` |
| 25 | [`WABOOKS_MREC_R006`](receipt.md#wabooks_mrec_r006) | Receipt list | 3/59 | `GET /api/v1/payment/card-transactions/links/receipts` | `PARTIAL` |
| 26 | [`WABOOKS_MREC_R009`](receipt.md#wabooks_mrec_r009) | Receipt detail | 1/53 | `GET /api/v1/payment/card-transactions/receipts/{doc_id}` | `PARTIAL` |
| 27 | [`WABOOKS_MREC_C002`](receipt.md#wabooks_mrec_c002) | Upload a receipt photo (base64) | 3/4 | `POST /api/v1/payment/card-transactions/receipts/{doc_id}/files` | `PARTIAL` |
| 28 | [`WABOOKS_MREC_D001`](receipt.md#wabooks_mrec_d001) | Delete a receipt photo | 2/0 | `DELETE /api/v1/payment/card-transactions/receipts/{doc_id}/files/{file_id}` | `PARTIAL` |
| 29 | [`WABOOKS_MREC_C003`](receipt.md#wabooks_mrec_c003) | Create a receipt | 39/3 | `POST /api/v1/payment/card-transactions/receipts` | `PARTIAL` |
| 30 | [`WABOOKS_MREC_C004`](receipt.md#wabooks_mrec_c004) | Update a receipt | 39/0 | — | `MISSING` |
| 31 | [`WABOOKS_MREC_C005`](receipt.md#wabooks_mrec_c005) | Submit a receipt | 1/0 | — | `NOT USED` |
| 32 | [`WABOOKS_MREC_R007`](receipt.md#wabooks_mrec_r007) | Withdrawal history for expense matching | 4/10 | `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` | `PARTIAL` |
| 33 | [`WABOOKS_MREC_R060`](receipt.md#wabooks_mrec_r060) | E-invoice list for attaching to a receipt | 4/8 | `GET /api/v1/payment/card-transactions/links/e-invoices` | `PARTIAL` |
| 34 | [`WABOOKS_MREC_U003`](receipt.md#wabooks_mrec_u003) | Link an e-invoice to a receipt | 2/0 | `POST /api/v1/payment/card-transactions/links` | `PARTIAL` |
| 35 | [`WABOOKS_MREC_R010`](notification-push.md#wabooks_mrec_r010) | Notification list | 3/20 | `GET /api/v1/notifications` | `PARTIAL` |
| 36 | [`WABOOKS_MREC_U001`](notification-push.md#wabooks_mrec_u001) | Mark one notification read | 2/1 | `PUT /api/v1/notifications/{notificationId}/read` | `AVAILABLE` |
| 37 | [`WABOOKS_MREC_BLTB_0007_U001`](notification-push.md#wabooks_mrec_bltb_0007_u001) | Mark all (or one) notifications read | 2/0 | `PUT /api/v1/notifications/read-all` | `AVAILABLE` |
| 38 | [`WABOOKS_MREC_C006`](notification-push.md#wabooks_mrec_c006) | Register a device for push | 9/0 | — | `MISSING` |
| 39 | [`WABOOKS_MREC_U002`](notification-push.md#wabooks_mrec_u002) | Update the app language (push payload language) | 2/0 | — | `MISSING` |
| 40 | [`WABOOKS_MREC_BLTB_0001_R001`](bulletin-board.md#wabooks_mrec_bltb_0001_r001) | Board list with favourites | 0/17 | — | `MISSING` |
| 41 | [`WABOOKS_MREC_BLTB_0001_U001`](bulletin-board.md#wabooks_mrec_bltb_0001_u001) | Toggle a favourite board | 1/0 | — | `MISSING` |
| 42 | [`WABOOKS_MREC_BLTB_0001_C001`](bulletin-board.md#wabooks_mrec_bltb_0001_c001) | Create a board | 2/1 | — | `MISSING` |
| 43 | [`WABOOKS_MREC_BLTB_0002_R001`](bulletin-board.md#wabooks_mrec_bltb_0002_r001) | Company-wide post + approval list | 6/59 | `GET /api/v1/payment/approval-box` | `PARTIAL` |
| 44 | [`WABOOKS_MREC_BLTB_0002_R004`](bulletin-board.md#wabooks_mrec_bltb_0002_r004) | Per-user post + approval list | 6/59 | `GET /api/v1/payment/approval-box` | `PARTIAL` |
| 45 | [`WABOOKS_MREC_BLTB_0002_R005`](bulletin-board.md#wabooks_mrec_bltb_0002_r005) | Approval list | 6/61 | `GET /api/v1/payment/approval-box` | `PARTIAL` |
| 46 | [`WABOOKS_MREC_BLTB_0002_D001`](bulletin-board.md#wabooks_mrec_bltb_0002_d001) | Delete a post | 2/0 | — | `MISSING` |
| 47 | [`WABOOKS_MREC_BLTB_0002_D003`](bulletin-board.md#wabooks_mrec_bltb_0002_d003) | Delete a draft post | 2/0 | — | `MISSING` |
| 48 | [`WABOOKS_MREC_BLTB_0002_R002`](bulletin-board.md#wabooks_mrec_bltb_0002_r002) | Board list for re-posting | 2/4 | — | `MISSING` |
| 49 | [`WABOOKS_MREC_BLTB_0002_R003`](bulletin-board.md#wabooks_mrec_bltb_0002_r003) | Comment list | 2/20 | — | `MISSING` |
| 50 | [`WABOOKS_MREC_BLTB_0002_C001`](bulletin-board.md#wabooks_mrec_bltb_0002_c001) | Add a comment (mentions + files) | 8/0 | — | `MISSING` |
| 51 | [`WABOOKS_MREC_BLTB_0002_D002`](bulletin-board.md#wabooks_mrec_bltb_0002_d002) | Delete a comment | 3/0 | — | `MISSING` |
| 52 | [`WABOOKS_MREC_BLTB_0002_U001`](bulletin-board.md#wabooks_mrec_bltb_0002_u001) | Edit a comment | 9/0 | — | `MISSING` |
| 53 | [`WABOOKS_MREC_BLTB_0003_R001`](bulletin-board.md#wabooks_mrec_bltb_0003_r001) | Board detail with its posts | 7/57 | — | `MISSING` |
| 54 | [`WABOOKS_MREC_BLTB_0003_D001`](bulletin-board.md#wabooks_mrec_bltb_0003_d001) | Delete a board | 1/0 | — | `MISSING` |
| 55 | [`WABOOKS_MREC_BLTB_0003_U001`](bulletin-board.md#wabooks_mrec_bltb_0003_u001) | Edit a board | 3/0 | — | `MISSING` |
| 56 | [`WABOOKS_MREC_BLTB_0004_C001`](bulletin-board.md#wabooks_mrec_bltb_0004_c001) | Create an article / save an article draft | 22/0 | — | `MISSING` |
| 57 | [`WABOOKS_MREC_BLTB_0004_C002`](bulletin-board.md#wabooks_mrec_bltb_0004_c002) | Create an approval post (expense approval) | 34/0 | `POST /api/v1/payment/expense-payment` | `PARTIAL` |
| 58 | [`WABOOKS_MREC_BLTB_0004_R001`](bulletin-board.md#wabooks_mrec_bltb_0004_r001) | Mentionable member list | 2/5 | `GET /api/v1/users` | `PARTIAL` |
| 59 | [`WABOOKS_MREC_BLTB_0004_R002`](bulletin-board.md#wabooks_mrec_bltb_0004_r002) | Approver list | 2/6 | `GET /api/v1/settings/approval-line` | `PARTIAL` |
| 60 | [`WABOOKS_MREC_BLTB_0004_R003`](bulletin-board.md#wabooks_mrec_bltb_0004_r003) | Attachable expense documents (bill / e-invoice / receipt) | 6/18 | `GET /api/v1/payment/card-transactions/links/receipts` | `PARTIAL` |
| 61 | [`WABOOKS_MREC_BLTB_0004_U001`](bulletin-board.md#wabooks_mrec_bltb_0004_u001) | Edit an article | 13/0 | — | `MISSING` |
| 62 | [`WABOOKS_MREC_BLTB_0004_U002`](bulletin-board.md#wabooks_mrec_bltb_0004_u002) | Edit a pending approval | 15/0 | `PUT /api/v1/payment/approval-box/{apprSeqNo}/edit` | `PARTIAL` |
| 63 | [`WABOOKS_MREC_BLTB_0005_R001`](bulletin-board.md#wabooks_mrec_bltb_0005_r001) | Article detail | 2/22 | — | `MISSING` |
| 64 | [`WABOOKS_MREC_BLTB_0005_D001`](bulletin-board.md#wabooks_mrec_bltb_0005_d001) | Delete a post file | 7/0 | — | `MISSING` |
| 65 | [`WABOOKS_MREC_BLTB_0005_D002`](bulletin-board.md#wabooks_mrec_bltb_0005_d002) | Delete a comment file | 4/0 | — | `MISSING` |
| 66 | [`WABOOKS_MREC_BLTB_0005_R002`](bulletin-board.md#wabooks_mrec_bltb_0005_r002) | Approval detail | 2/56 | `GET /api/v1/payment/approval-box/detail` | `PARTIAL` |
| 67 | [`WABOOKS_MREC_BLTB_0005_U001`](bulletin-board.md#wabooks_mrec_bltb_0005_u001) | Approve or reject | 7/0 | `POST /api/v1/payment/approval-box/{apprSeqNo}/approve` | `PARTIAL` |
| 68 | [`WABOOKS_MREC_BLTB_0005_R003`](bulletin-board.md#wabooks_mrec_bltb_0005_r003) | Read an article / approval draft | 3/41 | — | `MISSING` |
| 69 | [`WABOOKS_MREC_BLTB_0006_R001`](bulletin-board.md#wabooks_mrec_bltb_0006_r001) | Board member list | 2/11 | `GET /api/v1/users` | `PARTIAL` |
| 70 | [`WABOOKS_MREC_BLTB_0006_R002`](bulletin-board.md#wabooks_mrec_bltb_0006_r002) | Invitable (non-member) user list | 2/5 | `GET /api/v1/users` | `PARTIAL` |
| 71 | [`WABOOKS_MREC_BLTB_0006_C001`](bulletin-board.md#wabooks_mrec_bltb_0006_c001) | Invite a member to a board | 3/0 | — | `MISSING` |
| 72 | [`WABOOKS_MREC_BLTB_0006_U001`](bulletin-board.md#wabooks_mrec_bltb_0006_u001) | Promote to / demote from board admin | 3/0 | — | `MISSING` |
| 73 | [`WABOOKS_MREC_BLTB_0006_D001`](bulletin-board.md#wabooks_mrec_bltb_0006_d001) | Remove a member from a board | 2/0 | — | `MISSING` |
| 74 | [`WABOOKS_MREC_BLTB_0006_D002`](bulletin-board.md#wabooks_mrec_bltb_0006_d002) | Leave a board | 2/0 | — | `MISSING` |
| 75 | [`WABOOKS_MREC_BLTB_0006_R003`](bulletin-board.md#wabooks_mrec_bltb_0006_r003) | Non-admin member list | 2/11 | — | `MISSING` |
| 76 | [`WABOOKS_MREC_BLTB_0006_U002`](bulletin-board.md#wabooks_mrec_bltb_0006_u002) | Assign a new admin and leave | 4/0 | — | `MISSING` |

## 4 · Work blocks implied by the gaps

| # | Block | Services | Notes |
|---|---|---|---|
| 1 | **Bulletin-board module** — boards, posts, articles, drafts, comments, mentions, members, favourites, pins | the 25 `MISSING` services in [bulletin-board.md](bulletin-board.md), plus the article/comment half of its 12 `PARTIAL` ones | Largest block. `BLTB_POST_INFM`, `BLTB_COMNT_INFM`, `USER_BLTB_CONN_INFM`, `BLTB_NOTI_CONT_INFM`, `BLTB_DRAFT_POST_MSTR`, `BLTB_USER_NEW_POST_INFM` are all unrefactored |
| 2 | **Push notification stack** | `C006`, `U002`, and the board branch of `R010` | `PUSH_DEVICE_MSTR` / `PUSH_NOTICE_INFM`. Handover answer item 2.7 recommends building a fresh pipeline rather than reusing the KOSIGN Push service |
| 3 | **SMS-OTP account recovery** (find id, reset password) | the 7 `MBMR_0002_*` / `MBMR_0003_*` services | Needs an SMS provider plus endpoints that work without a session |
| 4 | **Two-step mobile login** | `R001`, `R002`, `R003` | Step 1 returns the company list with `LOCK_YN` / `PW_ERR_CNT`; logout is still a stub |
| 5 | **Mobile receipt channel** | `R006`, `R009`, `C002`, `C003`, `C004`, `D001` | `REG_CHNL='MOB'`, photo staging in `MREC_PIC_STORE` before a `DOC_ID` exists, and a receipt-update endpoint (none exists today) |
| 6 | **Master-data field gaps** | `R008` (bank + logo), `R020` (dial code), `R901` (withholding), `C080` / `D080` (contact favourites), `R070` (imprest) | Mostly additive fields on endpoints that already exist; `R070` depends on whether the imprest ledger comes into scope |
| 7 | **Transaction matching for Expense** | `R007` | Search withdrawals by amount + currency + date range across accounts |

## 5 · Source-workbook data quality

Recorded so the next reader does not re-discover them:

- The `목록` index skips row 34, so `WABOOKS_MREC_D080` (remove contact favourite) is absent from the index although it has a sheet and both apps call it.
- The `WABOOKS_MREC_BLTB_0005_U001` sheet is missing its `프로그램ID` cell; the id is taken from the sheet name.
- No sheet has any row under `Response Code Define`.
- Several sheets place long process text in the field-name columns, and one sheet (`BLTB_0004_R002`) has the required mark and the length swapped between columns. Both were normalised when this doc set was generated: a field row is only counted when its name column holds a single `[A-Za-z0-9_]` token, everything else is preserved under "Process rules".
- `WABOOKS_MREC_C005` (submit receipt) is annotated "(04/14) 삭제. 사용하지 않음" — dropped.
- iOS also calls `comm_0102_001` (project / site lookup, used for `PRJT_NO` on receipt creation), which has **no sheet in the workbook** — an open spec gap to raise with the handover team.

