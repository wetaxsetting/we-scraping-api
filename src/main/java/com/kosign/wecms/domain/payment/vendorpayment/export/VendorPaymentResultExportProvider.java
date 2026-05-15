package com.kosign.wecms.domain.payment.vendorpayment.export;

import com.kosign.wecms.domain.payment.export.PaymentResultExportProvider;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Payment &gt; Vendor Payment &gt; Payment Result — Excel export. */
@Component
@RequiredArgsConstructor
public class VendorPaymentResultExportProvider extends PaymentResultExportProvider {

    private final VendorPaymentResultService resultService;

    @Override
    public String screenId() {
        return "payment-vendor-payment-result";
    }

    @Override
    protected String titleLabel() {
        return "vendor payment batch";
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
