package com.kosign.wecms.domain.payment.internaltransfer.export;

import com.kosign.wecms.domain.payment.export.PaymentResultExportProvider;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Payment &gt; Internal Transfer &gt; Transfer Result — Excel export.
 *
 * <p>Binds {@code screenId = "payment-internal-transfer-result"} and
 * delegates the row fetch to
 * {@link InternalTransferResultService#list} so the export and the
 * on-screen list draw from one query path.
 *
 * <p>Wired by adding {@code POST /api/v1/payment/internal-transfer/results/export}
 * to {@code TransferResultController} — see CLAUDE.md §13b.
 */
@Component
@RequiredArgsConstructor
public class InternalTransferResultExportProvider extends PaymentResultExportProvider {

    private final InternalTransferResultService resultService;

    @Override
    public String screenId() {
        return "payment-internal-transfer-result";
    }

    @Override
    protected String titleLabel() {
        return "transfer batch";
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
