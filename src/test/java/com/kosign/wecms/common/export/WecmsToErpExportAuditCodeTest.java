package com.kosign.wecms.common.export;

import com.kosign.wecms.common.i18n.RelativeTimeFormatter;
import com.kosign.wecms.domain.banking.export.AccountInquiryFcyExportProvider;
import com.kosign.wecms.domain.banking.export.AccountInquiryVndExportProvider;
import com.kosign.wecms.domain.banking.export.TransactionHistoryFcyExportProvider;
import com.kosign.wecms.domain.banking.export.TransactionHistoryVndExportProvider;
import com.kosign.wecms.domain.banking.service.AccountService;
import com.kosign.wecms.domain.banking.service.TransactionService;
import com.kosign.wecms.domain.payment.approvalbox.export.ApprovalBoxExportProvider;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxService;
import com.kosign.wecms.domain.payment.cardtransactions.export.CardTransactionsExportProvider;
import com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionsService;
import com.kosign.wecms.domain.payment.overseas.export.OverseasPaymentResultDetailExportProvider;
import com.kosign.wecms.domain.payment.service.OverseasPaymentService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Legacy-parity guard for the WeCMS&rarr;ERP export audit (the {@code 'W'}
 * {@code ERP_TRNS_HSTR} rows written by {@code downloadProc.jsp} in JEX).
 *
 * <p>Each export provider that mirrors a legacy {@code downloadProc.jsp}
 * branch must (a) opt into the audit via {@link ExportRowProvider#auditsErpExport()}
 * and (b) forward the correct {@code TRNS_TYPE_CD} to
 * {@link ErpTrnsHistoryWriter#recordExportedFile} when
 * {@link ExportRowProvider#recordErpExport} fires. The mapping is pinned
 * against the audit doc
 * {@code docs/audits/erp-transaction-history-legacy-flow-audit.md} §5/§6.
 *
 * <p>The Account-Inquiry / Transaction-History providers field-inject the
 * writer (abstract base, lean subclass constructors — same precedent as
 * {@code PaymentResultExportProvider}), so the mock is set via reflection.
 * Approval Box / Card Transactions constructor-inject it.
 */
class WecmsToErpExportAuditCodeTest {

    private static final Path WORKBOOK = Path.of("export.xlsx");
    private static final String ORG_FILE_NM = "name.xlsx";

    /** Field-injected (@Autowired) providers — set the mock via reflection. */
    private void checkFieldInjected(ExportRowProvider p, ErpTrnsHistoryWriter w, String expectedCode) {
        ReflectionTestUtils.setField(p, "erpHistoryWriter", w);
        assertThat(p.auditsErpExport()).isTrue();
        p.recordErpExport(WORKBOOK, ORG_FILE_NM);
        verify(w).recordExportedFile(expectedCode, WORKBOOK, ORG_FILE_NM);
    }

    /** Constructor-injected providers — the mock is already wired in. */
    private void checkConstructorInjected(ExportRowProvider p, ErpTrnsHistoryWriter w, String expectedCode) {
        assertThat(p.auditsErpExport()).isTrue();
        p.recordErpExport(WORKBOOK, ORG_FILE_NM);
        verify(w).recordExportedFile(expectedCode, WORKBOOK, ORG_FILE_NM);
    }

    @Test
    void account_inquiry_vnd_code_09() {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        checkFieldInjected(
                new AccountInquiryVndExportProvider(mock(AccountService.class), mock(RelativeTimeFormatter.class)),
                w, "09");
    }

    @Test
    void account_inquiry_fcy_code_10() {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        checkFieldInjected(
                new AccountInquiryFcyExportProvider(mock(AccountService.class), mock(RelativeTimeFormatter.class)),
                w, "10");
    }

    @Test
    void transaction_history_vnd_code_01() {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        checkFieldInjected(
                new TransactionHistoryVndExportProvider(mock(TransactionService.class)),
                w, "01");
    }

    @Test
    void transaction_history_fcy_code_08() {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        checkFieldInjected(
                new TransactionHistoryFcyExportProvider(mock(TransactionService.class)),
                w, "08");
    }

    @Test
    void approval_box_code_11() {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        checkConstructorInjected(
                new ApprovalBoxExportProvider(mock(ApprovalBoxService.class), w),
                w, "11");
    }

    @Test
    void card_transactions_code_07() {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        checkConstructorInjected(
                new CardTransactionsExportProvider(mock(CardTransactionsService.class), w),
                w, "07");
    }

    @Test
    void overseas_result_detail_export_code_14() {
        ErpTrnsHistoryWriter w = mock(ErpTrnsHistoryWriter.class);
        checkConstructorInjected(
                new OverseasPaymentResultDetailExportProvider(mock(OverseasPaymentService.class), w),
                w, "14");
    }

    /** The two retired-screenId bugs the split fixed: the providers must
     *  register the V156 seed ids, not the retired single key. */
    @Test
    void transaction_history_screen_ids_match_v156_seed() {
        assertThat(new TransactionHistoryVndExportProvider(mock(TransactionService.class)).screenId())
                .isEqualTo("banking-transaction-history-vnd");
        assertThat(new TransactionHistoryFcyExportProvider(mock(TransactionService.class)).screenId())
                .isEqualTo("banking-transaction-history-fcy");
    }
}
