package com.kosign.wecms.domain.payment.payrollpayment.controller;

import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollBankingMatchService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentDownloadService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentResultService;
import com.kosign.wecms.domain.payment.payrollpayment.service.PayrollPaymentService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Generated-report download ERP audit: Scheduled → 12, Result → 06. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollPaymentReportDownloadAuditTest {

    private static final byte[] BYTES = {1, 2, 3};

    @Mock PayrollPaymentService         service;
    @Mock PayrollPaymentResultService   resultService;
    @Mock PayrollPaymentDownloadService downloadService;
    @Mock ErpTrnsHistoryWriter          erpHistoryWriter;
    @Mock PayrollBankingMatchService    matchService;
    @Mock ExcelExportService            excelExportService;
    @InjectMocks PayrollPaymentController controller;

    @Test void scheduled_report_download_writes_W_scheduled_12() {
        when(downloadService.generate("SP1", "EN"))
                .thenReturn(new PayrollPaymentDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadReport("SP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("12"), eq(BYTES), eq("f.xlsx"));
    }

    @Test void result_report_download_writes_W_result_06() {
        when(downloadService.generate("SP1", "EN"))
                .thenReturn(new PayrollPaymentDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadResultReport("SP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("06"), eq(BYTES), eq("f.xlsx"));
    }

    /** Toolbar Excel = template-based BULK report over all filtered result
     *  batches → ERP_TRNS_HSTR 'W' / 06 (Payroll Payment Result). */
    @Test void toolbar_bulk_export_uses_generateBulk_and_writes_W_result_06() {
        java.util.List<String> ids = java.util.List.of("SP1", "SP2");
        when(resultService.dispatchedIdsForExport("20260601", "20260630", null, null)).thenReturn(ids);
        when(downloadService.generateBulk(ids, "EN"))
                .thenReturn(new PayrollPaymentDownloadService.GeneratedReport(BYTES, "bulk.xlsx"));
        controller.downloadResultBulkReport("20260601", "20260630", null, null, "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("06"), eq(BYTES), eq("bulk.xlsx"));
    }
}
