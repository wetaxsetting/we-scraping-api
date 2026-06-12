# Contacts — Test Scenarios (Backend)

SIT/UAT scenarios for Settings > Contacts plus the existing JUnit coverage map. Slug `contacts`.

---

## 1. SIT / UAT scenarios

### Search
| # | Scenario | Expected |
|---|---|---|
| S1 | Search by contact name | Rows where `TRCO_CMNM`/`TRCO_CMENM` contains the keyword (case-insensitive); trashed rows excluded |
| S2 | Search by contact code | Rows where `HAND_TRCO_KEY` matches the keyword |
| S3 | Filter type = Local | Only `TRCO_TYPE='L'` rows |
| S4 | Filter type = Overseas | Only `TRCO_TYPE='O'` rows |
| S5 | No filter / both types | All managed active rows, ordered `HAND_TRCO_KEY ASC` (Local + Overseas interleave) |
| S6 | Sort by code / name / type / tax-id ASC+DESC | Order matches `ContactSortField`; unknown sort field falls back to default order |
| S7 | Page beyond last | Empty `rows`, correct `total_count` / `total_pages` |

### Create
| # | Scenario | Expected |
|---|---|---|
| C1 | Create Local with one account | 200, `trco_key` allocated; PGRO `B`+`S` rows seeded (`USER_ID='ALL'`); `TRCO_INQ_YN='Y'`; `TRSC_CCY_CD='VND'` |
| C2 | Create Overseas with one beneficiary | 200; overseas row persisted; `TRSC_CCY_CD='USD'` |
| C3 | Create Local with **zero** accounts | 200 (legacy parity — Local does not require a bank account) |
| C4 | Duplicate `hand_trco_key` | `WBE_CONTACTS_HAND_KEY_DUP`; `nextTrcoKey` never called |
| C5 | Duplicate non-blank `trco_tax_id` | `WBE_CONTACTS_TAX_ID_DUP` |
| C6 | Address upsert `disp_ord = 3` | `WBE_VALIDATION` (OQ-8) |
| C7 | Missing `hand_trco_key` / `trco_type='X'` | `WBE_VALIDATION` envelope, HTTP 200 |
| C8 | Duplicate account (`bank_nm|acct_no`) / overseas (`bene_acct_no|bic_code`) | `WBE_CONTACTS_ACCT_DUP` / `WBE_CONTACTS_BENE_DUP` |

### Update
| # | Scenario | Expected |
|---|---|---|
| U1 | Update general info (name / tax id) | 200, refreshed row; dup checks skipped when unchanged |
| U2 | Add / edit / remove address (current `disp_ord=1`, old `disp_ord=2`) | Combined upsert/delete pass; old address `due_dt` becomes the valid date |
| U3 | Add / edit / remove bank account (`accounts_to_delete`) | Account upsert/delete applied in one tx |
| U4 | Switch Local → Overseas | Leaving-side rows deleted (frontend-queued), opposite-side rows created |
| U5 | Set `trco_stts='C'` while in use | `WBE1190` — not trashed |
| U6 | Set `trco_stts='C'` while free | Trashed; response is a projected row (not hidden) |

### Delete
| # | Scenario | Expected |
|---|---|---|
| D1 | Single delete, free contact | `TRCO_STTS='C'` + audit |
| D2 | Single delete, in use | `WBE1190`; status unchanged |
| D3 | Bulk delete, all free | All flipped to `'C'` |
| D4 | Bulk delete with one in-use key | `WBE1190`; the whole batch rolls back (no partial flip) |
| D5 | Bulk delete empty list | `WBE_VALIDATION`; service not called |
| D6 | Delete then list | Trashed contact absent from the list (OQ-5) |

### Export (GAP-01)
| # | Scenario | Expected |
|---|---|---|
| E1 | Export Local | XLSX, 9 columns in order (Contact Code · Contact Name · Tax ID · Deposit Bank · Deposit Account No. · Currency · Current Address · Old Address · Valid Date); only `TRCO_TYPE='L'` active rows; honours keyword |
| E2 | Export Overseas | XLSX, 11 columns (Contact Code · Contact Name · Tax ID · Beneficiary Name · Beneficiary Bank Name · BIC · Beneficiary Account No. · Beneficiary Address · Current Address · Old Address · Valid Date); only `TRCO_TYPE='O'` |
| E3 | Export with active keyword | Export rows match the on-screen filtered set |
| E4 | Empty export | Valid XLSX with header + subheader `0 contact(s)`, no data rows |
| E5 | Unknown screenId | `WBE2010` |

### Import
| # | Scenario | Expected |
|---|---|---|
| I1 | Preflight existing code / tax id | `hand_trco_key_exists` / `trco_tax_id_exists` true on the matching rows |
| I2 | Resolve recipients (Local, NAPAS-VND) | `bank_found=true`, NAPAS code + holder name (when firm config active) |
| I3 | Resolve unknown bank name | `bank_found=false` |
| I4 | Import 10 rows, 1 duplicate code | `total=10, imported=9, failed=1`, `errors[0].code=WBE_CONTACTS_HAND_KEY_DUP`; HTTP 200 |
| I5 | Import >500 rows | `WBE_VALIDATION` (`@Size(max=500)`) |

### Validation / error handling
| # | Scenario | Expected |
|---|---|---|
| V1 | Required field missing | `WBE_VALIDATION`, HTTP 200 |
| V2 | Duplicate contact (code / tax id) | `WBE_CONTACTS_HAND_KEY_DUP` / `_TAX_ID_DUP` |
| V3 | Invalid bank info (dup account) | `WBE_CONTACTS_ACCT_DUP` / `_BENE_DUP` |
| V4 | Attachment > 10 MB / bad extension | `WBE_CONTACTS_FILE_TOO_LARGE` / `_TYPE_NOT_ALLOWED` |
| V5 | Detail unknown / wrong tenant / trashed | `WBE_CONTACTS_NOT_FOUND` |

### Authorization
| # | Scenario | Expected |
|---|---|---|
| A1 | No JWT | 401 `WBE1009` |
| A2 | JWT without `settings-core-contacts` grant | 403 `WBE1003` |

---

## 2. Existing JUnit coverage map

### `ContactsControllerTest` (standalone MockMvc, manual SNAKE_CASE converter)
- `list_happyPathSerialisesSnakeCase` — `data.total_count`, `rows[0].trco_key/hand_trco_key/trco_type`, `addresses[0].disp_ord` (OQ-8), `accounts[0].bank_nm/napas_bank_cd` (OQ-7).
- `create_happyPathRoundTripsSnakeCase` — POST with nested account; asserts `data.trco_key`.
- `create_missingRequiredFieldYieldsValidationEnvelope` — omitted `hand_trco_key` → `WBE_VALIDATION`, 200.
- `create_invalidTrcoTypeYieldsValidationEnvelope` — `trco_type='X'` → `WBE_VALIDATION`.
- `delete_happyPath` — verifies `service.softDelete`.
- `delete_inUseContactThrowsWbe1190` — `WBE1190` envelope, 200.
- `bulkDelete_happyPath` — verifies `bulkSoftDelete(List)`.
- `bulkDelete_emptyListRejected` — empty list → `WBE_VALIDATION`, service never called.

### `ContactsServiceTest` (Mockito, real `ContactsValidator` + real `FirmBankingInquiryService`)
- List: paged rows with tenant scope; `HAND_TRCO_KEY ASC` reaches search as `handTrcoKey` asc; unknown sort field falls back to default order.
- getOne: missing row and trashed (`trcoStts='C'`) row both → `WBE_CONTACTS_NOT_FOUND`.
- Create: happy path (allocates key, seeds exactly two PGRO rows `B`/`S` with `USER_ID='ALL'`, asserts `TRCO_INQ_YN='Y'`); duplicate hand_trco_key → `WBE_CONTACTS_HAND_KEY_DUP` (+ `verifyNoInteractions(legacyFn)`); duplicate tax id → `WBE_CONTACTS_TAX_ID_DUP`; **Local with zero accounts succeeds**; address upsert disp_ord out-of-range (3) → `WBE_VALIDATION`; PK collision retries once (`nextTrcoKey` called twice).
- Soft-delete: in-use → `WBE1190` (status not flipped); free row flips to `'C'` with audit; bulk delete with an in-use key throws `WBE1190` before the second flip lands.

## 3. Coverage notes

- Export providers (GAP-01) are covered by the frontend wiring + SIT verification; backend column
  order is pinned by the V247 seed and the provider allow-lists. A dedicated provider unit test is a
  non-blocking follow-up.
- Frontend test scenarios: [wecms-web/docs/features/settings/contacts/test-scenarios.md](../../../../../wecms-web/docs/features/settings/contacts/test-scenarios.md).
