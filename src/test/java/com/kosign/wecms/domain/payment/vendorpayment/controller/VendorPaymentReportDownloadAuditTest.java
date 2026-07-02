package com.kosign.wecms.domain.payment.vendorpayment.controller;

import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.domain.payment.match.service.DomesticPaymentLineMatchService;
import com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentDownloadService;
import com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentResultService;
import com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentService;
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

/**
 * Generated-report download ERP audit (controller layer): the Scheduled
 * report endpoint records the SCHEDULED type code (02), the Result report
 * endpoint records the RESULT type code (04). Both download the same legacy
 * 22-column artifact via {@code VendorPaymentDownloadService.generate}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorPaymentReportDownloadAuditTest {

    private static final byte[] BYTES = {1, 2, 3};

    @Mock VendorPaymentService            service;
    @Mock VendorPaymentResultService      resultService;
    @Mock VendorPaymentDownloadService    downloadService;
    @Mock ErpTrnsHistoryWriter            erpHistoryWriter;
    @Mock ExcelExportService              excelExportService;
    @Mock DomesticPaymentLineMatchService lineMatchService;
    @InjectMocks VendorPaymentController controller;

    @Test void scheduled_report_download_writes_W_scheduled_02() {
        when(downloadService.generate("SP1", "EN"))
                .thenReturn(new VendorPaymentDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadReport("SP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("02"), eq(BYTES), eq("f.xlsx"));
    }

    @Test void result_report_download_writes_W_result_04() {
        when(downloadService.generate("SP1", "EN"))
                .thenReturn(new VendorPaymentDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadResultReport("SP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("04"), eq(BYTES), eq("f.xlsx"));
    }

    /** Toolbar Excel = template-based BULK report over all filtered result
     *  batches → ERP_TRNS_HSTR 'W' / 04 (Vendor Payment Result). */
    @Test void toolbar_bulk_export_uses_generateBulk_and_writes_W_result_04() {
        java.util.List<String> ids = java.util.List.of("SP1", "SP2");
        when(resultService.dispatchedIdsForExport("20260601", "20260630", null, null)).thenReturn(ids);
        when(downloadService.generateBulk(ids, "EN"))
                .thenReturn(new VendorPaymentDownloadService.GeneratedReport(BYTES, "bulk.xlsx"));
        controller.downloadResultBulkReport("20260601", "20260630", null, null, "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("04"), eq(BYTES), eq("bulk.xlsx"));
    }
}
