package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.domain.banking.service.TransactionService;
import org.springframework.stereotype.Component;

/**
 * Banking &gt; Transaction History (FCY) — Excel export.
 *
 * <p>Binds {@code screenId = "banking-transaction-history-fcy"} (V156 seed)
 * and writes the WeCMS&rarr;ERP audit row with {@code TRNS_TYPE_CD = "08"}
 * (legacy {@code downloadProc.jsp:1379}, {@code fnsh_0006_001} branch).
 */
@Component
public class TransactionHistoryFcyExportProvider extends TransactionHistoryExportProvider {

    public TransactionHistoryFcyExportProvider(TransactionService transactionService) {
        super(transactionService);
    }

    @Override
    public String screenId() {
        return "banking-transaction-history-fcy";
    }

    @Override
    protected String erpExportTrnsTypeCd() {
        return "08";
    }
}
