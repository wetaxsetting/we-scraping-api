# Own Account Transfer — Functional Specification (Refactor Backend)

Backend functional behaviour of the shipped feature: search/filters, grid columns, transfer actions,
statuses, validation, failure handling. Field-by-field business rules live in the canonical spec
[own-account-transfer.md](legacy-spec.md); legacy functional source-of-truth is
[docs/jex/features/own-account-transfer/functional-specification.md](../../../../../../docs/jex/features/own-account-transfer/functional-specification.md).

---

## 1. Main page (source-account picker)

The page is a paginated list of the tenant's own VND current/saving accounts, plus a per-row **Transfer**
action and a balance-refresh path.

### 1.1 Search conditions / filters

| Filter | Wire param | Behaviour |
|---|---|---|
| Bank | `bank_cd` (query, optional) | Single-select; unsupported codes coerced to "all" (legacy parity). Server returns `allowed_bank_cds[]` to populate the dropdown. |
| Sort | `sortField` + `sortDirection` (camelCase, optional) | Resolved through the `OwnAccountTransferSortField` whitelist; unknown values fall back to default `BANK_CD ASC, ACCT_NO ASC`. Never concatenated into SQL. |

There is **no date filter, no status filter, no currency filter, and no keyword search** on the source-account
picker — currency is fixed VND and the list is the tenant's own accounts. (Date/status/currency filters
listed in the generic audit template do not apply to this screen; they belong to the
[transfer-history.md](../transfer-history/legacy-spec.md) companion screen.)

### 1.2 Grid columns (View Setting seed V199)

| FIELD_ID | Header | Width | Sort | Notes |
|---|---|---|---|---|
| `BANK_CD` | Bank | 260px | Y | Locked (`FIX_YN='Y'`). Bank name + currency + account no. |
| `ACCT_NICK_NM` | Account | 260px | Y | Account nickname (blank when null). |
| `BAL` | Balance | 160px | Y | Right-aligned VND amount. |
| `AVI_BAL` | Available | 160px | Y | `REAL_AMT`; defaults to balance when null. |
| `ACCT_BAL_LST_STS` | Last Refresh | 180px | N | Status badge + datetime (see §3). |
| `ETC` | Action | 140px | N | Per-row **Transfer** button (special column). |

Column visibility/order is driven by the global View Setting feature (`web_svc_id = 'own-account-transfer'`,
seeded in `V199`). Footer shows total account count, Σ balance, Σ available.

## 2. Transfer actions

| Action | Endpoint | Notes |
|---|---|---|
| **List / Search** | `GET /source-accounts` | Page load + filter/sort. |
| **Update Balance** | `POST /balance-refresh` | Patches `ACCT_MSTR` balance + inquiry status after an engine BALANCE scrape. |
| **Target lookup** (composer) | `GET /target-accounts` | Own-account universe minus the source row. |
| **NAPAS validate** (composer) | `GET /napas-bank-info/{napas_bank_cd}` | Cross-bank recipient validation + display names. |
| **Transfer (execute)** | `POST /scraping-result` | Records the engine TRANSFER outcome (idempotent). |
| **Engine handshake audit** | `POST /engine-connection-check` | 204; compliance audit of engine readiness (OQ-4, PO 2026-05-18). |
| **NXiSAS bundle support** | `POST /nxisas/{cert-acct-list,decrypt,encrypt,logging}` | Cert lookup, SEED-CBC round-trip, audit logging. |

**Create / Edit / Delete in the CRUD sense do not exist** for own-account transfers. A transfer is created
exactly once as an immutable `NAPAS_REMIT_TRNS_HSTR` row via `/scraping-result`; there is no update or delete
endpoint (a recorded transfer is a historical fact). The generic "Create/Edit/Delete Transfer" review areas
therefore map to **N/A by design** — see [refactor-gap-analysis.md](refactor-gap-analysis.md).

**Export** — the source-account picker has no XLSX export endpoint in this feature. (Export is wired for
Account Inquiry and Firm Banking History, not OAT.)

## 3. Status & "Last Refresh" display rules

The `ACCT_BAL_LST_STS` cell composes a badge + timestamp from `ACCT_MSTR.ACCT_BAL_INQ_STTS` /
`ACCT_BAL_INQ_MSG` / `ACCT_BAL_LST_INQ_DTM`:

| Data state | Display |
|---|---|
| status `"00000000"` + datetime | green **Success** + formatted datetime |
| non-success status + datetime | red **Failed** + formatted datetime |
| non-success status, no datetime | red **Failed** (no time) |
| no status + no datetime | blank (no inquiry history) |

Success is determined by `isEngineStatusSuccess(stts)` (`stts === "00000000"`) — the same rule the frontend mapper applies.

## 4. Transfer status values

| Status | `NAPAS_REMIT_TRNS_HSTR.TRNS_STTS` | Wire (`ScrapingResultResponse.status`) |
|---|---|---|
| Success | `"00000000"` | `"success"` |
| Failed | any other engine code | `"failed"` |

There are no separate Pending / Approved / Rejected / Processing persisted states for own-account transfers —
the engine returns a terminal code and the refactor records that single outcome (see [business-flow.md §6](business-flow.md)).

## 5. Validation rules

Enforced in `recordScrapingResult` (`OwnAccountTransferService.java:568`) + Bean Validation on the request DTOs:

1. **Idempotency key** — `@Pattern("[A-Za-z0-9-]{32,64}")`; replay returns the cached row.
2. **Currency** — must be `VND`; otherwise `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY`.
3. **Transfer type** — `SAME_BANK|NAPAS`; `SAME_BANK` requires `target_bank_cd`, `NAPAS` requires `target_napas_bank_cd`.
4. **Amount** — integer strings; `requested_amt == actual_amt` exactly (VND).
5. **Source ownership** — active VND row in caller's `ACCT_MSTR`; user holds a `W` grant (or ADMIN).
6. **Target** — active VND own-account; NAPAS code resolved to an internal `bank_cd`.
7. **Balance refresh** — VND-only, account active, user `W`-grant.

## 6. Failure handling & retry

- **Engine failure** (non-`00000000`): the row is still recorded (audit `SCRAPING_RESULT_REJECT`) with `status="failed"` so the failure is traceable; no balance/settlement change.
- **Validation failure**: `BusinessException` → standard `{ status, timestamp, path, trace_id }` envelope; nothing persisted.
- **Network retry**: the client resubmits with the same `idempotency_key`; the server replays the prior row (`already_recorded=true`) — no duplicate insert (partial unique index `uq_napas_remit_trns_hstr_oat_idemp`, V195).
- **PK race** on `SEQ_NO`: `recordScrapingResult` retries allocation once before surfacing the error.
- There is **no automatic re-execution** of a failed transfer — the user re-initiates from the UI (a fresh engine run, a new idempotency key).
