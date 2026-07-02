package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ApprovalBoxDownloadService} — the PO-requested
 * template-based Approval Box Excel Download. Mocks {@link ApprovalBoxService}
 * (the shared list query path) and reads the generated {@code .xls} bytes back
 * with Apache POI to assert template selection, filename, layout, cell mapping,
 * preserved formatting, and the summary row.
 */
class ApprovalBoxDownloadServiceTest {

    private static final int SUMMARY_ROW = 2;
    private static final int HEADER_ROW  = 3;
    private static final int DATA_ROW    = 4;

    private ApprovalBoxService listService;
    private ApprovalBoxDownloadService service;

    @BeforeEach
    void setUp() {
        listService = mock(ApprovalBoxService.class);
        service = new ApprovalBoxDownloadService(listService);
        TenantContext.set("tester", "Tester", "VN_VNM0004_000001",
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Build an ApprovalBoxRow with only the fields the download reads. */
    private static ApprovalBoxRow row(
            String docNo, String title, String fileName, String typeLabel,
            String currency, String amt, String payDate, String draftUserNm,
            String regDtm, String apprStts) {
        return new ApprovalBoxRow(
                "1000", docNo, title, fileName, "content", "35", typeLabel, currency,
                amt, payDate, "u001", draftUserNm, regDtm, apprStts,
                2L, 1L, "N", List.of(),
                null, null, null, null, null);
    }

    private void stubRows(List<ApprovalBoxRow> rows) {
        when(listService.listForExport(any(), any(), any(), any(), any(), any()))
                .thenReturn(rows);
    }

    private static Workbook open(byte[] bytes) throws Exception {
        return WorkbookFactory.create(new ByteArrayInputStream(bytes));
    }

    private static String str(Sheet sheet, int r, int c) {
        Row row = sheet.getRow(r);
        if (row == null) return null;
        Cell cell = row.getCell(c);
        return cell == null ? null : cell.getStringCellValue();
    }

    /* ===================== template selection + filename ===================== */

    @Test
    void en_selectsEnglishTemplate_andFilename() throws Exception {
        stubRows(List.of());
        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");

        assertThat(report.fileName()).isEqualTo("Approval Box_EN.xls");
        try (Workbook wb = open(report.content())) {
            // Title cell (merged A1:J2) — EN template title.
            assertThat(str(wb.getSheetAt(0), 0, 0)).isEqualTo("Approval Box");
            // EN header row col A.
            assertThat(str(wb.getSheetAt(0), HEADER_ROW, 0)).isEqualTo("Date");
        }
    }

    @Test
    void ko_selectsKoreanTemplate_andFilename() throws Exception {
        stubRows(List.of());
        var report = service.generate("20260101", "20260131", null, null, null, null, "KO");

        assertThat(report.fileName()).isEqualTo("Approval Box_KO.xls");
        try (Workbook wb = open(report.content())) {
            // Localized KO title — not the English string.
            assertThat(str(wb.getSheetAt(0), 0, 0)).isNotBlank().isNotEqualTo("Approval Box");
        }
    }

    @Test
    void vi_selectsVietnameseTemplate_andFilename() throws Exception {
        stubRows(List.of());
        var report = service.generate("20260101", "20260131", null, null, null, null, "VI");

        assertThat(report.fileName()).isEqualTo("Approval Box_VI.xls");
        try (Workbook wb = open(report.content())) {
            assertThat(str(wb.getSheetAt(0), 0, 0)).isNotBlank().isNotEqualTo("Approval Box");
            assertThat(str(wb.getSheetAt(0), HEADER_ROW, 0)).isNotEqualTo("Date");
        }
    }

    @Test
    void km_fallsBackToEnglish() throws Exception {
        stubRows(List.of());
        var report = service.generate("20260101", "20260131", null, null, null, null, "KM");

        assertThat(report.fileName()).isEqualTo("Approval Box_EN.xls");
        try (Workbook wb = open(report.content())) {
            assertThat(str(wb.getSheetAt(0), 0, 0)).isEqualTo("Approval Box");
        }
    }

    @Test
    void unknownLang_fallsBackToEnglish() throws Exception {
        stubRows(List.of());
        var report = service.generate("20260101", "20260131", null, null, null, null, "zz");
        assertThat(report.fileName()).isEqualTo("Approval Box_EN.xls");
    }

    @Test
    void blankLang_usesJwtLangCd() throws Exception {
        TenantContext.clear();
        TenantContext.set("tester", "Tester", "VN_VNM0004_000001",
                LangCd.KO, TenantContext.AUTH_GR_USER, "F");
        stubRows(List.of());
        var report = service.generate("20260101", "20260131", null, null, null, null, null);
        assertThat(report.fileName()).isEqualTo("Approval Box_KO.xls");
    }

    /* ===================== row population + mapping ===================== */

    @Test
    void populatesDataRow_withFullColumnMapping() throws Exception {
        stubRows(List.of(row(
                "VO_260115_001", "Vendor batch #1", "vendor_pay.xlsx", "vendor",
                "VND", "5000000", "20260220", "Alice Nguyen",
                "20260115103000", "P")));

        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");
        try (Workbook wb = open(report.content())) {
            Sheet s = wb.getSheetAt(0);
            assertThat(str(s, DATA_ROW, 0)).isEqualTo("15-Jan-2026");   // Date (from regDtm)
            assertThat(str(s, DATA_ROW, 1)).isEqualTo("vendor");        // Type (slug, provider parity)
            assertThat(str(s, DATA_ROW, 2)).isEqualTo("VO_260115_001"); // Document No.
            assertThat(str(s, DATA_ROW, 3)).isEqualTo("Vendor batch #1");// Title
            assertThat(str(s, DATA_ROW, 4)).isEqualTo("vendor_pay.xlsx");// File Name
            assertThat(str(s, DATA_ROW, 5)).isEqualTo("VND");           // Currency

            Cell amt = s.getRow(DATA_ROW).getCell(6);                   // Transfer Amount
            assertThat(amt.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(amt.getNumericCellValue()).isEqualTo(5_000_000d);

            assertThat(str(s, DATA_ROW, 7)).isEqualTo("Pending");       // Approval Status
            assertThat(str(s, DATA_ROW, 8)).isEqualTo("20-Feb-2026");   // Scheduled Date
            assertThat(str(s, DATA_ROW, 9)).isEqualTo("Alice Nguyen");  // Requestor
        }
    }

    @Test
    void writesOneRowPerApproval() throws Exception {
        stubRows(List.of(
                row("A", "t1", "f1", "vendor", "VND", "100", "20260201", "U1", "20260101090000", "C"),
                row("B", "t2", "f2", "expense", "USD", "200", "20260202", "U2", "20260102090000", "R")));

        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");
        try (Workbook wb = open(report.content())) {
            Sheet s = wb.getSheetAt(0);
            assertThat(str(s, DATA_ROW, 2)).isEqualTo("A");
            assertThat(str(s, DATA_ROW, 7)).isEqualTo("Approved");   // C → Approved
            assertThat(str(s, DATA_ROW + 1, 2)).isEqualTo("B");
            assertThat(str(s, DATA_ROW + 1, 7)).isEqualTo("Rejected"); // R → Rejected
        }
    }

    @Test
    void approvalStatusMapping_coversAllCodes() throws Exception {
        stubRows(List.of(
                row("A", "t", "f", "vendor", "VND", "1", "20260201", "U", "20260101090000", "P"),
                row("B", "t", "f", "vendor", "VND", "1", "20260201", "U", "20260101090000", "I"),
                row("C", "t", "f", "vendor", "VND", "1", "20260201", "U", "20260101090000", "C"),
                row("D", "t", "f", "vendor", "VND", "1", "20260201", "U", "20260101090000", "R")));

        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");
        try (Workbook wb = open(report.content())) {
            Sheet s = wb.getSheetAt(0);
            assertThat(str(s, DATA_ROW, 7)).isEqualTo("Pending");
            assertThat(str(s, DATA_ROW + 1, 7)).isEqualTo("In Progress");
            assertThat(str(s, DATA_ROW + 2, 7)).isEqualTo("Approved");
            assertThat(str(s, DATA_ROW + 3, 7)).isEqualTo("Rejected");
        }
    }

    /* ===================== empty dataset ===================== */

    @Test
    void emptyDataset_producesOpenableWorkbook_withNoDataRows() throws Exception {
        stubRows(List.of());
        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");

        assertThat(report.content()).isNotEmpty();
        try (Workbook wb = open(report.content())) {
            Sheet s = wb.getSheetAt(0);
            // Title + header preserved; no data row leaked from the template samples.
            assertThat(str(s, 0, 0)).isEqualTo("Approval Box");
            assertThat(str(s, HEADER_ROW, 0)).isEqualTo("Date");
            assertThat(s.getRow(DATA_ROW)).isNull();
        }
    }

    /* ===================== preserved template structure ===================== */

    @Test
    void mergedRegions_arePreserved() throws Exception {
        stubRows(List.of(
                row("A", "t", "f", "vendor", "VND", "1", "20260201", "U", "20260101090000", "P")));

        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");
        try (Workbook wb = open(report.content())) {
            Sheet s = wb.getSheetAt(0);
            List<String> merges = s.getMergedRegions().stream()
                    .map(CellRangeAddress::formatAsString).toList();
            // Title A1:J2 + summary A3:E3 (left) + G3:J3 (right).
            assertThat(merges).contains("A1:J2", "A3:E3", "G3:J3");
        }
    }

    @Test
    void amountColumn_keepsTemplateNumberFormat() throws Exception {
        stubRows(List.of(
                row("A", "t", "f", "vendor", "VND", "1234567", "20260201", "U", "20260101090000", "P")));

        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");
        try (Workbook wb = open(report.content())) {
            Cell amt = wb.getSheetAt(0).getRow(DATA_ROW).getCell(6);
            String fmt = amt.getCellStyle().getDataFormatString();
            assertThat(fmt).contains("#,##0");
        }
    }

    /* ===================== summary row ===================== */

    @Test
    void summaryRow_leftIsDateRange_rightIsCountAndTotals() throws Exception {
        stubRows(List.of(
                row("A", "t", "f", "vendor", "VND", "1000000", "20260201", "U", "20260101090000", "P"),
                row("B", "t", "f", "vendor", "USD", "250.50", "20260201", "U", "20260102090000", "P")));

        var report = service.generate("20260101", "20260131", null, null, null, null, "EN");
        try (Workbook wb = open(report.content())) {
            Sheet s = wb.getSheetAt(0);
            String left = str(s, SUMMARY_ROW, 0);
            assertThat(left).startsWith("Date:").contains("01-Jan-2026").contains("31-Jan-2026");

            String right = str(s, SUMMARY_ROW, 6);
            assertThat(right).contains("2 document(s)")
                    .contains("Total USD").contains("Total VND").contains("Total KRW");
        }
    }
}
