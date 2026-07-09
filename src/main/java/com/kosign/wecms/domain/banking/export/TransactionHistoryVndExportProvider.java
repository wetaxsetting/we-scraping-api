package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.domain.banking.service.TransactionService;
import org.springframework.stereotype.Component;

/**
 * Banking &gt; Transaction History (VND) — Excel export.
 *
 * <p>Binds {@code screenId = "banking-transaction-history-vnd"} (V156 seed;
 * the retired single {@code banking-transaction-history} key is gone) and
 * writes the WeCMS&rarr;ERP audit row with {@code TRNS_TYPE_CD = "01"}
 * (legacy {@code downloadProc.jsp:1375}, {@code fnsh_0002_001} branch).
 */
@Component
public class TransactionHistoryVndExportProvider extends TransactionHistoryExportProvider {

    public TransactionHistoryVndExportProvider(TransactionService transactionService) {
        super(transactionService);
    }

    @Override
    public String screenId() {
        return "banking-transaction-history-vnd";
    }

    @Override
    protected String erpExportTrnsTypeCd() {
        return "01";
    }
}
