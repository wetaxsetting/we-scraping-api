# Mobile Receipt Channel

**Date:** 2026-09-03
**Type:** FEATURE
**Status:** READY FOR MANUAL COMMIT
**Repository:** wecms-api
**Issue:** —

## Purpose

The WeCMS mobile apps (iOS `WeCMS_iOS`, Android `WeCMS_AOS`) are being rebuilt. Their receipt-capture
flow is the app's core business function: photograph a receipt, key its fields, attach it to an expense
approval. Today that flow is served by six services on the legacy mobile gateway, none of which exists
in this repository — `wecms-api` has **no mobile-facing endpoint at all** (`API_SVC_ID` appears zero
times in `src/main`).

The full inventory of the 76 legacy mobile services, their field-level specs and their coverage against
this backend is at
[references/migration/mobile-app/](../../../references/migration/mobile-app/README.md); the receipt
group is [receipt.md](../../../references/migration/mobile-app/receipt.md).

## Requirement

Decided with the requester on 2026-09-03: the rebuilt mobile client will **speak the refactor REST API
directly** (`/api/v1/**`, JWT, lowercase `snake_case` via the global Jackson strategy) rather than the
backend re-implementing the legacy single-gateway envelope (`API_SVC_ID` + `UPPER_SNAKE` + `RSLT_CD` /
`REC`). Consequence: no gateway/adapter layer is built; instead the existing REST surface is extended
until it covers the mobile receipt flow.

This record covers the **receipt channel** block only — the six legacy services below:

| Legacy service | Purpose | Coverage today |
|---|---|---|
| `WABOOKS_MREC_R006` | Receipt list | PARTIAL |
| `WABOOKS_MREC_R009` | Receipt detail | PARTIAL |
| `WABOOKS_MREC_C002` | Upload receipt photo | PARTIAL |
| `WABOOKS_MREC_D001` | Delete receipt photo | PARTIAL |
| `WABOOKS_MREC_C003` | Create receipt | PARTIAL |
| `WABOOKS_MREC_C004` | Update receipt | MISSING |

## Current System Behavior

Verified from source on 2026-09-03.

**No mobile-facing endpoint exists.** `API_SVC_ID` appears zero times under `src/main`, and the only
`RSLT_CD` occurrences are the `USER_LOGIN_HIS` audit column — there is no trace of the legacy gateway
envelope. All 422 endpoints are web REST under `/api/v1/**`.

What the receipt surface does have, on `CardTransactionsController`:

| Existing endpoint | Behaviour | Gap for mobile |
|---|---|---|
| `GET /links/receipts` | Company-wide list of hand-registered receipts (`DOC_TYPE='E'` + `HAND_REG_YN='Y'`, `PROV_STTS NOT IN ('EE','VD')`) that are **not yet linked** to a payment, for the card-link dialog | Not the app's question: no `USER_ID` scope, and it hides a receipt as soon as it is attached |
| `GET /receipts/{doc_id}` | 11-field detail, tenant-scoped by `BIZ_KEY` only | No ownership check, no link/editability state |
| `POST /receipts` | Creates one `BUSA_DOC_MSTR` row — `DOC_TYPE='E'`, `HAND_REG_YN='Y'`, `PROV_STTS='OP'`, `DOC_ID` from the `wb_get_next_val` / `SYS_DOC_ID_GEN` funnel | Did not stamp `REG_CHNL`; the column fell back to its DB default `'PC'` |
| `GET / POST /receipts/{doc_id}/files`, `GET / DELETE …/{file_id}` | Attachments on `BUSA_DOC_FILE`, disk-backed, 10 MB cap, extension allow-list | Requires a `DOC_ID` to already exist |
| — | | **No update path at all** — POST creates, nothing edits |

Schema facts that shaped the plan:

- `BUSA_DOC_MSTR.REG_CHNL` already exists (`varchar(3) NOT NULL DEFAULT 'PC'`, `V23__busa_doc_mstr.sql:57`)
  but was **not mapped** on the `BusaDocMstr` entity.
- `mrec_pic_store` has DDL on disk (`V155__mrec_pic_store.sql`) added only so a manual topological
  build could satisfy `busa_doc_mstr_fk04`; its own header records that no JPA entity is mapped.
  `mrec_pic_store_file` has **no** DDL.
- Both tables are listed in
  [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md) under *"legacy
  but currently out of the PROD Active Table List"*, whose rule is *"confirm before wiring an
  entity/migration"* and *"confirm with the PO"*.
- No `BUSA_DOC_MSTR_HIS` entity exists; the create path writes no history row.

## Target Behavior

A mobile client can, against `/api/v1/**` with a normal JWT:

1. List the receipts it captured, over a date range, with search, paging, an optional channel filter,
   and enough state per row to know whether editing is still allowed.
2. Read one receipt's detail.
3. Create a receipt, with the backend recording that the capture came from mobile.
4. Update a receipt it created, for as long as no approval depends on it.
5. Attach and remove photos on a receipt that exists.

Deferred: uploading a photo *before* the receipt exists (§ Known Limitations).

## Scope

- `GET /api/v1/payment/card-transactions/receipts` — the caller's own receipts (legacy `R006`).
- `PUT /api/v1/payment/card-transactions/receipts/{doc_id}` — update (legacy `C004`).
- `REG_CHNL` provenance on create via the `X-Client-Channel` header (legacy `C003`).
- `BusaDocMstr.regChnl` entity mapping, with a Java-side `'PC'` default so existing writers keep
  working.
- The `WBE_RECEIPT_NOT_EDITABLE` error code.

## Out of Scope

- **Photo staging before a `DOC_ID` exists** (legacy `C002` / `D001` pre-create leg) — blocked on a
  table-scope decision, see § Known Limitations. Post-create attachment already works.
- `WABOOKS_MREC_U003` (receipt ↔ e-invoice link) and `R007` (withdrawal matching) — neighbouring
  blocks, tracked separately.
- `WABOOKS_MREC_C005` (submit receipt) — the source workbook marks it deleted / unused.
- The advance-payment (`ADV_PAYM_*`) UNION branch of the legacy list and detail services — that
  ledger is outside refactor scope.
- Bulletin board, push notification and SMS-OTP blocks.
- Any change to the web receipt screens or to `wecms-web`.
- A dedicated mobile menu grant (see § Known Limitations).

## Proposed User / Business Flow

As implemented:

1. **Capture** — the app creates the receipt (`POST /receipts` with `X-Client-Channel: MOB`), then
   uploads its photos to `POST /receipts/{doc_id}/files`.
2. **Review** — `GET /receipts` lists what the user captured; `file_count` shows attachment count and
   `editable` tells the client whether to render its Edit action.
3. **Correct** — `PUT /receipts/{doc_id}` while the receipt is still free of an approval.
4. **Attach to an approval** — existing expense-approval flow; the receipt then reports
   `editable=false` and carries its `appr_seq_no`.

The legacy "photograph now, key later, possibly from another device" flow is **not** reproduced — that
is the staged-photo leg in § Known Limitations.

## Proposed Implementation

Implemented as described in § Implementation below.

## API Impact

Two new endpoints, one new request header, one new error code, no breaking change to any existing
contract. Full contract:
[references/api-spec/payment/mobile-receipt-api.md](../../../references/api-spec/payment/mobile-receipt-api.md).

`ReceiptDetailResponse` was left untouched — the ownership/editability data the app needs rides on the
list rows, so no existing web response shape changed.

## UI Impact

None in this repository. `wecms-web` is untouched; the consumer is the mobile client.

## Database Impact

**NONE.**

- No DDL, no DML, no migration file, no manual `docs/sql/` script.
- `BUSA_DOC_MSTR.REG_CHNL` already exists in the schema of record (`V23__busa_doc_mstr.sql:57`), so
  mapping it on the entity keeps `ddl-auto=validate` satisfied with no schema change.
- `BUSA_DOC_MSTR` is in the PROD Active Table List.
- The new query is read-only and native.
- The staged-photo tables that *would* have required a decision are out of scope, which is precisely
  why this section can read `NONE`.

## Integration Impact

None. No bank, GDT, dispatch or Zalo path is touched.

## Validation / Business Rules

Derived from the legacy process rules quoted verbatim in
[references/migration/mobile-app/receipt.md](../../../references/migration/mobile-app/receipt.md).

**List** — `BIZ_KEY` + `USER_ID` + `DOC_TYPE='E'` + `HAND_REG_YN='Y'` +
`PROV_STTS NOT IN ('EE','VD')` + `COALESCE(USE_DT, ISSU_DT) BETWEEN from AND to`, optional
`REG_CHNL`, optional keyword. Ordered `COALESCE(USE_DT, ISSU_DT) DESC, DOC_ID DESC`.

**Update** — refused unless the row exists in the caller's tenant (`WBE_NOT_FOUND`), and is the
caller's own hand-registered receipt, not rejected/voided, and not yet attached to an expense approval
(`WBE_RECEIPT_NOT_EDITABLE`). The approval guard is the substantive one: editing the amount of a
receipt that a submitted approval already totals would desynchronise the approval silently. `editable`
on the list rows is computed from the same predicate, so a well-behaved client never hits the refusal.

**Channel** — `X-Client-Channel` is `^(PC|MOB)$`-validated and re-mapped server-side; anything other
than `MOB` becomes `PC`. Provenance only — it never affects authorization.

**Immutable after create** — `DOC_TYPE`, `HAND_REG_YN`, `REG_CHNL`, `DOC_ID`.

## Testing Plan

Executed — see § Verification and the linked test-result record.

## Implementation

| File | Change |
|---|---|
| `domain/einvoice/entity/BusaDocMstr.java` | Map `REG_CHNL` + the `REG_CHNL_PC` / `REG_CHNL_MOBILE` constants. Field initialised to `"PC"` in Java on purpose: the column is `NOT NULL` and previously relied on the DB default, so an unset mapped field would send `NULL` and break the two existing insert paths. |
| `domain/einvoice/repository/BusaDocMstrRepository.java` | New native `findOwnReceipts(bizKey, userId, fromDt, toDt, regChnl, keywordPattern, pageable)` + count query. |
| `domain/payment/cardtransactions/service/CardTransactionsService.java` | `listOwnReceipts(...)`; `updateReceipt(docId, req)` (`@Transactional`); `createReceipt(req, regChnl)` overload with the 1-arg version delegating as `PC`; helpers `receiptDate(...)` + `isReceiptEditable(...)`. |
| `domain/payment/cardtransactions/controller/CardTransactionsController.java` | `GET /receipts`; `PUT /receipts/{doc_id}`; `X-Client-Channel` header on `POST /receipts`; the `CLIENT_CHANNEL_HEADER` constant. |
| `dto/OwnReceiptRow.java`, `dto/OwnReceiptListResponse.java`, `dto/UpdateReceiptRequest.java` | New records. |
| `resources/messages.properties` | `WBE_RECEIPT_NOT_EDITABLE` + `audit.card_transactions.update_receipt.detail`. |

Audit logging: update writes one `AUDIT_LOGS_INFM` row with `WSVC_ID = "card-transactions"` (the route
slug, not a legacy WSVC id), matching the create path.

**Parity delta (deliberate):** legacy `C004` also inserts a `BUSA_DOC_MSTR_HIS` snapshot. This
repository maps no such entity and its create path writes no history either, so update stays
consistent with create rather than introducing a half-covered audit trail. Recorded as a follow-up.

## Files Changed

Modified: `BusaDocMstr.java` · `BusaDocMstrRepository.java` · `CardTransactionsService.java` ·
`CardTransactionsController.java` · `messages.properties` · `docs/references/migration/README.md`.

Added: `OwnReceiptRow.java` · `OwnReceiptListResponse.java` · `UpdateReceiptRequest.java` ·
`MobileReceiptChannelTest.java` · `docs/references/api-spec/payment/mobile-receipt-api.md` ·
`docs/references/migration/mobile-app/**` (6 files) · this record · the test-result record.

**Not part of this change** — the working tree already carried two unrelated edits when the work
started, and they are left untouched: a modified
`src/test/java/com/kosign/wecms/domain/vatdeclaration/service/VatDeclarationLifecycleTest.java` and an
untracked `src/test/java/com/kosign/wecms/domain/setting/zalooa/service/ZaloTokenServiceTest.java`.
A commit for this feature should be path-scoped accordingly.

## Test Evidence

Test result:
[Mobile Receipt Channel — Test Result](../../../testing/results/mobile-receipt-channel-test-result-2026-09-03.md)

Final test result: **PASS** · Regression review: **PASS** (2 pre-existing unrelated failures recorded)

## Verification

### Automated Tests

- Test: `MobileReceiptChannelTest` (8 cases) — channel stamping, list scope/filter/flags, update guard set.
- Test: `./gradlew test --tests "com.kosign.wecms.domain.payment.cardtransactions.*"` → **PASS**, 78 tests, 0 failed.
- Test: `./gradlew test` (full suite) → 2811 tests, **2 failed**, 21 skipped. Both failures are
  `SchedulerConfigurationContractTest` config-drift assertions on `application-local.yml`
  (`wecms.dispatch.efs.base-url`, `wecms.dispatch.stuck-monitor.enabled`), pre-existing and unrelated —
  this change touches no `*.yml` or `.env*` file. Not fixed here per §15j.
- Result: **PASS** for this change.

### Test Scenarios

Recorded in the test-result document (6 scenarios; 5 PASS at unit level, 1 NOT APPLICABLE — photo
staging is unimplemented).

### Regression Review

- Areas checked: the two `new BusaDocMstr()` insert sites (`EInvoiceScrapeService:203`,
  `CardTransactionsService:1625`) against the new `NOT NULL` mapped column; the existing
  `/links/receipts` list and `/receipts/{doc_id}` detail paths (untouched); endpoint authorization
  coverage.
- Result: the Java-side `'PC'` default keeps both writers valid; e-invoice scrape tests pass
  unchanged; `EndpointAuthorizationCoverageTest` passes, so both new handlers are correctly gated by
  the class-level `@menuAuthz`.

### Manual Verification

- Environment: LOCAL
- Steps: `./gradlew compileJava compileTestJava` (clean), then the two test runs above.
- Live HTTP smoke: **NOT RUN** — the app was not started (the shared dev DB needs VPN) and nothing is
  deployed.
- Result: **PASS** for what was run; live verification NOT RUN.

## Result

**READY FOR MANUAL COMMIT.**

Four of the six receipt-channel services are now served by the REST API: list (`R006`), detail
(`R009`, ownership/editability now derivable from the list), create with channel provenance (`C003`),
and update (`C004`, which had no endpoint at all before). Post-create photo attach/delete
(`C002`/`D001`) already existed and is unchanged.

Commit, push and any deployment are manual team actions (§16).

## Known Limitations / Follow-up

1. **Photo staging before a `DOC_ID` exists — needs a scope decision, not code.** Legacy `C002`
   writes to `MREC_PIC_STORE` / `MREC_PIC_STORE_FILE`; both are legacy-but-inactive in the table
   inventory (*"confirm with the PO"*), only the parent has DDL, and neither has an entity. Three
   options, with trade-offs, are written up in
   [mobile-receipt-api.md §5](../../../references/api-spec/payment/mobile-receipt-api.md). Until one is
   chosen, the app must create the receipt first and upload photos after — which loses the
   capture-now-key-later-on-another-device flow.
2. **Menu grant.** Both new endpoints inherit
   `@menuAuthz.canAccess('payment-corporate-card-transactions')`, so a mobile user needs the corporate
   card menu grant. Consistent with the endpoint's neighbours and keeps the authorization coverage
   test green, but a dedicated mobile grant may be the better model once the app's permission story is
   decided.
3. **No `BUSA_DOC_MSTR_HIS` snapshot on update** — deliberate, matching the create path. Revisit if
   receipt history becomes a requirement, and then cover create and update together.
4. **`GET /receipts/{doc_id}` still has no ownership check** — unchanged from before this work
   (tenant-scoped only). Editability is enforced on the write path and surfaced on the list, so
   nothing is exploitable, but a future MR should decide whether detail reads should be user-scoped too.
5. **Live verification outstanding** — no HTTP smoke, no device integration; nothing is deployed.
6. **Pre-existing local-profile config drift** (`wecms.dispatch.efs.base-url`,
   `wecms.dispatch.stuck-monitor.enabled` missing from `application-local.yml`) makes
   `./gradlew test` red for the whole repository. Not this feature's to fix; owner of the dispatch
   configuration should pick it up.
