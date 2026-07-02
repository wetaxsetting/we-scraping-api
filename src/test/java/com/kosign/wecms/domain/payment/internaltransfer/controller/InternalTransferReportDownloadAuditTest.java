package com.kosign.wecms.domain.payment.internaltransfer.controller;

import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDispatchService;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferResultService;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService;
import com.kosign.wecms.domain.payment.match.service.DomesticPaymentLineMatchService;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Generated-report download ERP audit for the two Internal Transfer
 * controllers: the Scheduled controller records 16 (Internal Scheduled
 * Transfer), the Result controller records 17 (Internal Transfer Result).
 * Both download the same artifact via {@code InternalTransferDownloadService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalTransferReportDownloadAuditTest {

    private static final byte[] BYTES = {1, 2, 3};

    @Mock InternalTransferService         service;
    @Mock InternalTransferDispatchService dispatchService;
    @Mock DomesticPaymentLineMatchService lineMatchService;
    @Mock InternalTransferDownloadService downloadService;
    @Mock ErpTrnsHistoryWriter            erpHistoryWriter;
    @Mock InternalTransferResultService   resultService;
    @Mock ExcelExportService              excelExportService;

    @Test void scheduled_report_download_writes_W_scheduled_16() {
        InternalTransferController controller = new InternalTransferController(
                service, dispatchService, lineMatchService, downloadService, erpHistoryWriter);
        when(downloadService.generate("SP1", "EN"))
                .thenReturn(new InternalTransferDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadReport("SP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("16"), eq(BYTES), eq("f.xlsx"));
    }

    @Test void result_report_download_writes_W_result_17() {
        TransferResultController controller = new TransferResultController(
                resultService, excelExportService, downloadService, erpHistoryWriter);
        when(downloadService.generate("SP1", "EN"))
                .thenReturn(new InternalTransferDownloadService.GeneratedReport(BYTES, "f.xlsx"));
        controller.downloadReport("SP1", "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("17"), eq(BYTES), eq("f.xlsx"));
    }

    /** Toolbar Excel = template-based BULK report over all filtered result
     *  batches → ERP_TRNS_HSTR 'W' / 17 (Internal Transfer Result). */
    @Test void toolbar_bulk_export_uses_generateBulk_and_writes_W_result_17() {
        TransferResultController controller = new TransferResultController(
                resultService, excelExportService, downloadService, erpHistoryWriter);
        java.util.List<String> ids = java.util.List.of("SP1", "SP2");
        when(resultService.dispatchedIdsForExport("20260601", "20260630", null, null)).thenReturn(ids);
        when(downloadService.generateBulk(ids, "EN"))
                .thenReturn(new InternalTransferDownloadService.GeneratedReport(BYTES, "bulk.xlsx"));
        controller.downloadBulkReport("20260601", "20260630", null, null, "EN");
        verify(erpHistoryWriter).recordExportedBytes(eq("17"), eq(BYTES), eq("bulk.xlsx"));
    }
}
