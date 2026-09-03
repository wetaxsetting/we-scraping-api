# Payment > Card Transactions — Mobile Receipt Channel API

**Module:** Payment (Card Transactions controller — receipts live under this resource today)
**Spring domain package:** `com.kosign.wecms.domain.payment.cardtransactions`
**Refactor screen ID:** `card-transactions` (the receipt endpoints hang off that resource; no new screen slug)
**Legacy counterpart:** the mobile gateway services `WABOOKS_MREC_R006` / `R009` / `C002` / `C003` / `C004` / `D001`
**Feature record:** [../../../features/payment/mobile-receipt-channel/mobile-receipt-channel-2026-09-03.md](../../../features/payment/mobile-receipt-channel/mobile-receipt-channel-2026-09-03.md)
**Field-level legacy spec:** [../../migration/mobile-app/receipt.md](../../migration/mobile-app/receipt.md)
**Status:** Implemented — list · detail · create-with-channel · update. Photo staging (pre-`DOC_ID` upload) is **not** implemented; it needs a table-scope decision (see §5).

JEX and the legacy mobile source are **read-only reference**.

---

## 1 · Design decision — the client speaks REST, not the legacy gateway

Decided 2026-09-03. The rebuilt mobile client calls `/api/v1/**` directly with a normal JWT and the
project's lowercase `snake_case` wire shape. The backend does **not** re-implement the legacy
single-gateway envelope (`API_SVC_ID` + `UPPER_SNAKE` + `RSLT_CD` / `REC`).

Consequences for this contract:

- No adapter or gateway controller exists, and none should be added.
- The mobile client and the web client share the same endpoints. Where the legacy mobile service
  filtered on its own registration channel, the refactor exposes that as an **optional** filter rather
  than baking it in — see §3.
- `REG_CHNL` survives as **provenance**, not as an access rule. It never widens or narrows what a
  caller may do.

## 2 · Endpoints

| Method | Path | Returns | Legacy twin |
|---|---|---|---|
| GET | `/api/v1/payment/card-transactions/receipts` | `ApiResponse<OwnReceiptListResponse>` | `WABOOKS_MREC_R006` |
| GET | `/api/v1/payment/card-transactions/receipts/{doc_id}` | `ApiResponse<ReceiptDetailResponse>` | `WABOOKS_MREC_R009` |
| POST | `/api/v1/payment/card-transactions/receipts` | `ApiResponse<CreateReceiptResponse>` | `WABOOKS_MREC_C003` |
| PUT | `/api/v1/payment/card-transactions/receipts/{doc_id}` | `ApiResponse<Void>` | `WABOOKS_MREC_C004` |
| GET / POST | `/api/v1/payment/card-transactions/receipts/{doc_id}/files` | `ApiResponse<…>` | `WABOOKS_MREC_C002` (post-`DOC_ID` only) |
| DELETE | `/api/v1/payment/card-transactions/receipts/{doc_id}/files/{file_id}` | `ApiResponse<Void>` | `WABOOKS_MREC_D001` (post-`DOC_ID` only) |

Authorization: the whole controller is gated by
`@PreAuthorize("@menuAuthz.canAccess('payment-corporate-card-transactions'))`, so a mobile user needs
that menu grant. Flagged as a follow-up in the feature record — a dedicated mobile grant may be
warranted, but inventing one here would have diverged from the endpoint's existing gate.

## 3 · `GET /receipts` — the caller's own receipts

| Param | Required | Shape | Meaning |
|---|---|---|---|
| `from_date` | yes | `^[0-9]{8}$` | inclusive lower bound on `COALESCE(USE_DT, ISSU_DT)` |
| `to_date` | yes | `^[0-9]{8}$` | inclusive upper bound |
| `search` | no | free text | case-insensitive `LIKE` over contact name (VI + EN), display doc id, description |
| `reg_chnl` | no | `PC` \| `MOB` | provenance filter; **omit for both channels** |
| `page` | no | int, default `0` | zero-based |
| `size` | no | int, default `50` | clamped to 200 |

Response rows (`OwnReceiptRow`, wire `snake_case`): `doc_id` · `disp_doc_id` · `use_dt` · `merch_nm` ·
`merch_tax_id` · `totl_amt` · `ccy_cd` · `purpose` · `purpose_nm` · `description` · `reg_chnl` ·
`appr_seq_no` · `file_count` · `editable`.

**Deliberate deltas from legacy `R006`:**

| Legacy behaviour | Refactor behaviour | Why |
|---|---|---|
| Filters `REG_CHNL='MOB'` unconditionally | Spans both channels unless `reg_chnl` is passed | Under one shared API a receipt keyed on the web would otherwise vanish from the app. `?reg_chnl=MOB` restores exact parity. |
| Excludes nothing on link state | Keeps already-linked receipts, exposing `appr_seq_no` + `editable` | The app must keep showing a receipt after it is attached to an approval, and needs to know when to hide its Edit action. |
| `UNION ALL` with `ADV_PAYM_TRNS_HSTR` (imprest ledger) | Absent | `ADV_PAYM_*` is outside refactor scope. |
| Adds a "photo captured, not yet keyed" group from `MREC_PIC_STORE` | Absent | See §5. |

Shared with the neighbouring `GET /links/receipts`: the same tenant scope, the `DOC_TYPE='E'` +
`HAND_REG_YN='Y'` filter and the `PROV_STTS NOT IN ('EE','VD')` exclusion. Different question, though —
`/links/receipts` answers *"what may I still attach to this card transaction"* (company-wide, unlinked
only); `/receipts` answers *"what have I captured"* (one `USER_ID`, link state included).

## 4 · `PUT /receipts/{doc_id}` — update

Full replace of the user-owned fields; body is `UpdateReceiptRequest`, field-for-field the same as
`CreateReceiptRequest` so a client can reuse its payload builder. `DOC_ID` is the path variable;
`DOC_TYPE`, `HAND_REG_YN` and `REG_CHNL` are immutable after create.

Guard order and outcomes:

| Condition | Result |
|---|---|
| Document missing, or in another tenant | `WBE_NOT_FOUND` |
| Not the caller's own row (`USER_ID` mismatch) | `WBE_RECEIPT_NOT_EDITABLE` |
| Not a hand-registered expense receipt (`DOC_TYPE≠'E'` or `HAND_REG_YN≠'Y'`) | `WBE_RECEIPT_NOT_EDITABLE` |
| `PROV_STTS` is `EE` / `VD` (rejected / voided) | `WBE_RECEIPT_NOT_EDITABLE` |
| Already attached to an expense approval (`APPR_SEQ_NO` set) | `WBE_RECEIPT_NOT_EDITABLE` |

The approval guard is the one worth stating out loud: editing the amount of a receipt that a submitted
approval already totals would desynchronise the approval silently. The list's `editable` flag is
computed from the same predicate, so a well-behaved client never reaches the refusal.

**Parity delta:** legacy `C004` also inserts a `BUSA_DOC_MSTR_HIS` snapshot. This repository maps no
`BUSA_DOC_MSTR_HIS` entity and its create path writes no history either, so update stays consistent
with create rather than introducing a half-covered audit trail. Recorded as a follow-up rather than
silently diverging.

## 5 · Photo staging — specified, not implemented

Legacy `C002` uploads a photo **before** any document exists: rows land in `MREC_PIC_STORE` /
`MREC_PIC_STORE_FILE`, keyed `(BIZ_KEY, REG_DT, SEQ_NO)`, and `BUSA_DOC_MSTR` later points at that row
through `busa_doc_mstr_fk04`. That is what makes "photograph now on the phone, key it later, possibly
from the web" work across devices.

The refactor cannot ship it under current governance:

- `mrec_pic_store` / `mrec_pic_store_file` are **legacy-but-inactive** — present in
  [legacy-table-inventory.md](../../database/legacy-table-inventory.md) under *"legacy but currently out
  of the PROD Active Table List"*, whose rule reads *"confirm before wiring an entity/migration"* and
  *"confirm with the PO"*.
- Only `mrec_pic_store` has DDL on disk (added purely so a manual topological build could satisfy
  `busa_doc_mstr_fk04`; its header states no JPA entity is mapped). `mrec_pic_store_file` has no DDL.

So the decision is a scope call, not a coding one. The options, for whoever takes it:

1. **Activate the legacy pair** — PO widens the active list, `mrec_pic_store_file` gets DDL, both get
   entities, staged photos move onto `BUSA_DOC_FILE` at create time. Highest parity; needs the same
   kind of PO override that `busa_doc_file` (2026-07-04) and `trco_info_file` (2026-05-13) went through.
2. **Client-side staging** — the app holds photos locally until create, then posts them to the existing
   `POST /receipts/{doc_id}/files`. Zero backend work; loses the cross-device capture-now-key-later
   flow.
3. **Refactor-owned staging table** — a new table of our own design. Avoids reviving a legacy shape but
   still a new table, so still a decision, and it duplicates a table the legacy schema already has.

Until then, `POST /receipts` followed by `POST /receipts/{doc_id}/files` is the supported capture path.

## 6 · `X-Client-Channel` header

`POST /receipts` accepts an optional request header:

```
X-Client-Channel: MOB     # mobile client
X-Client-Channel: PC      # web client (also the default when the header is absent)
```

Validated against `^(PC|MOB)$`; the value is written to `BUSA_DOC_MSTR.REG_CHNL`, whose column has been
`varchar(3) NOT NULL DEFAULT 'PC'` since the schema baseline. The service maps anything other than
`MOB` to `PC`, so a malformed or unexpected value can never reach the column.

Why a header rather than a body field: the channel is cross-cutting request provenance, not business
data the user entered. Putting it in the body would invite clients to treat it as an editable
attribute of the receipt — it is not, and update leaves it untouched.

## 7 · Error codes

| Code | Meaning |
|---|---|
| `WBE_NOT_FOUND` | Receipt missing, or belongs to another tenant |
| `WBE_RECEIPT_NOT_EDITABLE` | Not a hand-registered receipt · not the caller's · rejected/voided · already attached to an approval |
| `WBE_RECEIPT_DOCID_UNAVAILABLE` | `SYS_DOC_ID_GEN` could not allocate a `DOC_ID` (create only) |
| `WBE_VALIDATION` | Bean-validation failure on the body, a path variable, or a query param |

`WBE_RECEIPT_NOT_EDITABLE` was added with this feature; the others already existed. All four resolve
from `messages.properties` and travel in the standard `ApiResponse` error envelope.
