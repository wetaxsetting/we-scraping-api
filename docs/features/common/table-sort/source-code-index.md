# Table Sort — Source Code Index

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

All paths relative to repo root. Backend = `wecms-api/`, frontend = `wecms-web/`.

## Backend — common primitives (`common/web/`)

| File | Role |
|---|---|
| `wecms-api/src/main/java/com/kosign/wecms/common/web/SortDirection.java` | enum `{ASC, DESC}` + lenient `parse(String)` (null on blank/unknown) |
| `wecms-api/src/main/java/com/kosign/wecms/common/web/SortableField.java` | interface — `String entityProperty()` (every whitelist enum implements it) |

## Backend — reference service + controller (Account Inquiry)

| File | Lines (verified) | Role |
|---|---|---|
| `wecms-api/src/main/java/com/kosign/wecms/domain/banking/service/AccountSortField.java` | 22-62 | whitelist enum; `parse(...)` → null on miss |
| `wecms-api/src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java` | 131-132 (`DEFAULT_SORT`), 581-589 (`buildSort`), 594-599 (`applyDefaultSortIfNeeded`) | parse → `Sort.by(primary).and(DEFAULT_SORT)`; export-path default weave |
| `wecms-api/src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` | 92-106 | `@GetMapping("/accounts")` with `sortField` + `sortDirection` `@RequestParam(required=false)` |
| `wecms-api/src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java` | — | `findByBizKeyAndActvYn(..., Sort)` + filtered variants accept a `Sort` |

## Backend — per-domain whitelist enums (28 confirmed, 2026-06-23)

All implement `SortableField`. Verified-and-read in this pass are marked ✓.

| Enum | Path |
|---|---|
| `AccountSortField` ✓ | `domain/banking/service/AccountSortField.java` |
| `ContactSortField` ✓ | `domain/setting/contacts/service/ContactSortField.java` |
| `ChartOfAccountsSortField` ✓ | `domain/setting/chartofaccounts/dto/ChartOfAccountsSortField.java` |
| `EInvoiceSortField` ✓ | `domain/einvoice/service/EInvoiceSortField.java` |
| `OperationStatusSortField` ✓ | `domain/operation/service/OperationStatusSortField.java` |
| `EInvoiceDetailHistorySortField` | `domain/einvoice/service/EInvoiceDetailHistorySortField.java` |
| `ApprovalLineSortField` | `domain/setting/approvalline/dto/ApprovalLineSortField.java` |
| `FirmBankingHistorySortField` | `domain/setting/firmbankinghistory/service/FirmBankingHistorySortField.java` |
| `ErpTrnsHistorySortField` | `domain/setting/erptransactionhistory/service/ErpTrnsHistorySortField.java` |
| `BankAccountSortField` | `domain/setting/bankaccount/service/BankAccountSortField.java` |
| `VasAccountSortField` | `domain/setting/vasaccount/service/VasAccountSortField.java` |
| `ExchangeRateSortField` | `domain/banking/service/ExchangeRateSortField.java` |
| `SmartOrganizerSortField` | `domain/banking/smartorganizer/dto/SmartOrganizerSortField.java` |
| `OwnAccountTransferSortField` | `domain/banking/ownaccounttransfer/service/OwnAccountTransferSortField.java` |
| `TransferHistorySortField` | `domain/banking/transferhistory/dto/TransferHistorySortField.java` |
| `ApprovalBoxSortField` | `domain/payment/approvalbox/service/ApprovalBoxSortField.java` |
| `CardMstrSortField` | `domain/payment/cardtransactions/service/CardMstrSortField.java` |
| `CardTrnsHstrSortField` | `domain/payment/cardtransactions/service/CardTrnsHstrSortField.java` |
| `InternalTransferSortField` | `domain/payment/internaltransfer/service/InternalTransferSortField.java` |
| `InternalTransferResultSortField` | `domain/payment/internaltransfer/service/InternalTransferResultSortField.java` |
| `VendorPaymentSortField` | `domain/payment/vendorpayment/service/VendorPaymentSortField.java` |
| `VendorPaymentResultSortField` | `domain/payment/vendorpayment/service/VendorPaymentResultSortField.java` |
| `ExpensePaymentSortField` | `domain/payment/expensepayment/service/ExpensePaymentSortField.java` |
| `ExpensePaymentResultSortField` | `domain/payment/expensepayment/service/ExpensePaymentResultSortField.java` |
| `PayrollPaymentSortField` | `domain/payment/payrollpayment/service/PayrollPaymentSortField.java` |
| `PayrollPaymentResultSortField` | `domain/payment/payrollpayment/service/PayrollPaymentResultSortField.java` |
| `OverseasScheduledSortField` | `domain/payment/service/OverseasScheduledSortField.java` |
| `OverseasResultSortField` | `domain/payment/service/OverseasResultSortField.java` |

## Backend — tests

| File | Covers |
|---|---|
| `wecms-api/src/test/java/com/kosign/wecms/common/web/RequestResponseLoggingFilterTest.java` | `common/web/` filter (adjacent to the sort primitives) |
| `wecms-api/src/test/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentSortFieldTest.java` | per-domain enum parse/whitelist |
| `wecms-api/src/test/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferSortFieldTest.java` | per-domain enum parse/whitelist |
| `wecms-api/src/test/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTrnsHstrSortFieldTest.java` | per-domain enum parse/whitelist |
| `wecms-api/src/test/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountSortFieldTest.java` | per-domain enum parse/whitelist |
| `…/contacts/service/ContactsServiceTest.java`, `…/firmbankinghistory/service/FirmBankingHistoryServiceTest.java`, `…/erptransactionhistory/service/ErpTrnsHistoryServiceTest.java`, `…/einvoice/service/EInvoiceServiceTest.java` | service-level sort behaviour |

## Frontend (consumers — see the WEB twin docs)

| File | Role |
|---|---|
| `wecms-web/hooks/useTableSort.ts` | 3-state cycle `default → ASC → DESC → default`; emits `sortParams` (null in default) |
| `wecms-web/components/shared/TableHeaderCell.tsx` | sort glyph (`ico-sort.svg` / lucide `ChevronUp` / `ChevronDown`), `aria-sort`, keyboard Enter/Space |
| `wecms-web/lib/api/banking.ts` | `ListSortParams` shape spread into the list fetch |
| `wecms-web/app/(dashboard)/banking/account-inquiry/AccountInquiryClient.tsx` | calls `useTableSort()`, forwards state to the page |

## Rules

- `wecms-api/CLAUDE.md` §"Common table sort — `common/web/`"
- `wecms-web/CLAUDE.md` §13a "Table sorting"
