package com.kosign.wecms.domain.payment.controller;

import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.domain.payment.common.WithdrawalAccountListService;
import com.kosign.wecms.domain.payment.service.OverseasPaymentDownloadService;
import com.kosign.wecms.domain.payment.service.OverseasPaymentExcelService;
import com.kosign.wecms.domain.payment.service.OverseasPaymentService;
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

/** Generated-report download ERP audit: Scheduled → 13 (Overseas Payment
 *  Request), Result → 14 (Overseas Request Result). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentReportDownloadAuditTest {

    private static final byte[] BYTES = {1, 2, 3};

    @Mock OverseasPaymentService         overseasPaymentService;
    @Mock OverseasPaymentExcelService    overseasExcelService;
    @Mock WithdrawalAccountListService   withdrawalAccountListService;
    @Mock ExcelExportService             excelExportService;
    @Mock OverseasPaymentDownloadService downloadService;
    @Mock ErpTrnsHistoryWriter           erpHistoryWriter;
    @InjectMocks OverseasPaymentController controller;

    @Test void scheduled_report_download_writes_W_scheduled_13() {
        when(downloadService.generate("OP1", "EN"))
                .thenReturn(new OverseasPaymentDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadReport("OP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("13"), eq(BYTES), eq("f.xlsx"));
    }

    @Test void result_report_download_writes_W_result_14() {
        when(downloadService.generate("OP1", "EN"))
                .thenReturn(new OverseasPaymentDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadResultReport("OP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("14"), eq(BYTES), eq("f.xlsx"));
    }

    /** Toolbar Excel = template-based BULK report over all filtered result
     *  batches → ERP_TRNS_HSTR 'W' / 14 (Overseas Request Result). */
    @Test void toolbar_bulk_export_uses_generateBulk_and_writes_W_result_14() {
        java.util.List<String> ids = java.util.List.of("OP1", "OP2");
        when(overseasPaymentService.resultIdsForExport("20260601", "20260630", null, null)).thenReturn(ids);
        when(downloadService.generateBulk(ids, "EN"))
                .thenReturn(new OverseasPaymentDownloadService.GeneratedReport(BYTES, "bulk.xlsx"));
        controller.downloadResultBulkReport("20260601", "20260630", null, null, "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("14"), eq(BYTES), eq("bulk.xlsx"));
    }
}
