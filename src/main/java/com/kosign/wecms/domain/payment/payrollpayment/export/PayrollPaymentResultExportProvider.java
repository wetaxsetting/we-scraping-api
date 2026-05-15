package com.kosign.wecms.domain.payment.payrollpayment.export;

import com.kosign.wecms.domain.payment.export.PaymentResultExportProvider;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Payment &gt; Payroll Payment &gt; Payment Result — Excel export. */
@Component
@RequiredArgsConstructor
public class PayrollPaymentResultExportProvider extends PaymentResultExportProvider {

    private final PayrollPaymentResultService resultService;

    @Override
    public String screenId() {
        return "payment-payroll-payment-result";
    }

    @Override
    protected String titleLabel() {
        return "payroll payment batch";
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
