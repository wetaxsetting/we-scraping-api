package com.kosign.wecms.domain.payment.expensepayment.export;

import com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentResultService;
import com.kosign.wecms.domain.payment.export.PaymentResultExportProvider;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Payment &gt; Expense Payment &gt; Payment Result — Excel export. */
@Component
@RequiredArgsConstructor
public class ExpensePaymentResultExportProvider extends PaymentResultExportProvider {

    private final ExpensePaymentResultService resultService;

    @Override
    public String screenId() {
        return "payment-expense-payment-result";
    }

    @Override
    protected String titleLabel() {
        return "expense payment batch";
    }

    @Override
    protected String erpResultTrnsTypeCd() {
        return "05"; // legacy ErpTypeCd.EXPENSE_PAYMENT_RESULT
    }

    @Override
    protected TransferResultListResponse loadList(
            String fromDate, String toDate, String srchWd, String trnfStts,
            String sortField, String sortDirection,
            int page, int size) {
        return resultService.list(
                fromDate, toDate, srchWd, trnfStts,
                sortField, sortDirection, page, size);
    }
}
