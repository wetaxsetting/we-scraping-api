package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.domain.weinvoice.dto.InvoiceRow;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ItemRow;
import com.kosign.wecms.domain.weinvoice.repository.PackRepository.CqtRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** UI Spec v1.1 §3.11 (Excel 3 sheets) and §3.12 (GDT original list). */
class InvoiceWorkbookTest {

    @Test
    void excel_hasSummaryAndOneDetailSheetPerDirection() throws Exception {
        List<InvoiceRow> rows = List.of(invoice("1", "purchase", "C26TAA"), invoice("2", "sales", "C26TBB"));
        Map<Long, List<ItemRow>> items = Map.of(1L, List.of(
                item(1, null, "Hàng A", "100000", "10"),
                item(2, 3, "Chiết khấu", "10000", "10")));
        try (XSSFWorkbook wb = read(InvoiceWorkbook.invoiceSheets(rows, items))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(wb.getSheetName(0)).isEqualTo("Tổng hợp");
            assertThat(wb.getSheetName(1)).isEqualTo("Chi tiết bán ra");
            assertThat(wb.getSheetName(2)).isEqualTo("Chi tiết mua vào");

            org.apache.poi.xssf.usermodel.XSSFSheet sum = wb.getSheetAt(0);
            assertThat(sum.getRow(0).getCell(0).getStringCellValue()).startsWith("⚠️ Ghi chú");
            assertThat(sum.getMergedRegion(0).getLastColumn()).isEqualTo(19);
            assertThat(sum.getRow(1).getCell(0).getStringCellValue()).isEqualTo("STT");
            Cell date = sum.getRow(2).getCell(1);
            assertThat(DateUtil.isCellDateFormatted(date)).isTrue();
            assertThat(date.getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(sum.getRow(2).getCell(12).getCellStyle().getDataFormatString()).isEqualTo("#,##0");
            assertThat(sum.getRow(2).getCell(16).getStringCellValue()).isEqualTo("(không mã)");
            assertThat(sum.getCTWorksheet().isSetAutoFilter()).isTrue();

            Sheet sales = wb.getSheetAt(1);
            assertThat(sales.getRow(1).getCell(5).getStringCellValue()).isEqualTo("MST người mua");
            assertThat(sales.getRow(2).getCell(8).getStringCellValue()).isEqualTo(InvoiceWorkbook.NO_LINES);

            Sheet purchase = wb.getSheetAt(2);
            assertThat(purchase.getRow(2).getCell(8).getStringCellValue()).isEqualTo("Hàng A");
            assertThat(purchase.getRow(2).getCell(13).getStringCellValue()).isEqualTo("10%");
            // discount line is negative
            assertThat(purchase.getRow(3).getCell(12).getNumericCellValue()).isEqualTo(-10000d);
        }
    }

    @Test
    void rateLabel_keepsSpecialRates() {
        assertThat(InvoiceWorkbook.rateLabel("8")).isEqualTo("8%");
        assertThat(InvoiceWorkbook.rateLabel("10.00%")).isEqualTo("10%");
        assertThat(InvoiceWorkbook.rateLabel("KCT")).isEqualTo("KCT");
        assertThat(InvoiceWorkbook.rateLabel("kkknt")).isEqualTo("KKKNT");
    }

    @Test
    void cqtList_monthWorkbookHasOneSheetPerListTypeInGdtLayout() throws Exception {
        String raw = "{\"nbmst\":\"0300000001\",\"nbten\":\"A\",\"nmmst\":\"0101234567\",\"nmten\":\"B\","
                + "\"tgtcthue\":1000.0,\"tgtthue\":80.0,\"tgtttbso\":1080.0,\"dvtte\":\"VND\",\"tgia\":1.0,"
                + "\"_tthai_text\":\"Hóa đơn mới\",\"ttxly\":5}";
        String mtt = "{\"nbmst\":\"0300000002\",\"nbdchi\":\"HN\",\"tthai\":1,\"ttxly\":8}";
        List<CqtRow> rows = List.of(
                new CqtRow(1, "mua", "mua", "2026-10-05", 1, "C26TAA", 7, "0300000001", "A", "0101234567", "B",
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(11), null, raw),
                new CqtRow(2, "mua", null, "2026-10-06", 1, "C26MAA", 8, "0300000002", "C", "0101234567", "B",
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(11), 1, mtt),
                new CqtRow(3, "mua", "mua", "2026-11-01", 1, "C26TAA", 9, "0300000001", "A", "0101234567", "B",
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(11), 1, null));
        var months = CqtListWorkbook.byMonth(rows);
        assertThat(months).containsOnlyKeys("2026-10", "2026-11");
        assertThat(months.get("2026-10")).containsOnlyKeys("mua", "mua-mtt");
        assertThat(CqtListWorkbook.monthFileName("2026-10", true)).isEqualTo("bang-ke-goc-CQT_2026-10__THIEU.xlsx");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CqtListWorkbook.writeMonth(out, "2026-10", months.get("2026-10"), LocalDate.of(2026, 10, 3), null);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet normal = wb.getSheet("Mua vào - có mã");
            assertThat(normal.getRow(2).getCell(0).getStringCellValue()).isEqualTo("DANH SÁCH HÓA ĐƠN");
            assertThat(normal.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Từ ngày 03/10/2026 đến ngày 31/10/2026");
            assertThat(normal.getRow(5).getPhysicalNumberOfCells()).isEqualTo(19);
            assertThat(normal.getRow(6).getCell(3).getStringCellValue()).isEqualTo("7");
            assertThat(normal.getRow(6).getCell(4).getStringCellValue()).isEqualTo("05/10/2026");
            assertThat(normal.getRow(6).getCell(10).getNumericCellValue()).isEqualTo(1000d);
            assertThat(normal.getRow(6).getCell(16).getStringCellValue()).isEqualTo("1.0");
            assertThat(normal.getRow(6).getCell(17).getStringCellValue()).isEqualTo("Hóa đơn mới");
            assertThat(normal.getRow(6).getCell(18).getStringCellValue()).isEqualTo("Đã cấp mã hóa đơn");

            Sheet cash = wb.getSheet("Mua vào - máy tính tiền");
            assertThat(cash.getRow(2).getCell(0).getStringCellValue()).isEqualTo("DANH SÁCH HÓA ĐƠN MÁY TÍNH TIỀN");
            assertThat(cash.getRow(5).getPhysicalNumberOfCells()).isEqualTo(17);
            assertThat(cash.getRow(5).getCell(7).getStringCellValue()).isEqualTo("Địa chỉ người bán");
            assertThat(cash.getRow(6).getCell(16).getStringCellValue())
                    .isEqualTo("Cục Thuế đã nhận hóa đơn có mã khởi tạo từ máy tính tiền");
        }
    }

    @Test
    void cqtReadme_namesIncompleteMonths() {
        assertThat(InvoiceExportService.cqtReadme(5, List.of("2026-10"))).contains("__THIEU").contains("2026-10");
        assertThat(InvoiceExportService.cqtReadme(5, List.of())).doesNotContain("__THIEU");
    }

    private static XSSFWorkbook read(List<InvoiceWorkbook.SheetData> sheets) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InvoiceWorkbook.write(out, sheets);
        return new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
    }

    private static InvoiceRow invoice(String id, String direction, String symbol) {
        return new InvoiceRow(id, direction, "2026-10-05", "1", symbol, "123", "0300000001", "Người bán", "0101234567",
                "Người mua", "0300000001", "Người bán", "VND", BigDecimal.ONE, new BigDecimal("100000"),
                new BigDecimal("10000"), new BigDecimal("110000"), new BigDecimal("110000"), "original", "normal", null,
                "MISA", "0101243150", true, true, false, false, "ABC", null, null, List.of(), "gdt", null);
    }

    private static ItemRow item(int no, Integer kind, String name, String amount, String rate) {
        return new ItemRow(no, kind, null, name, "cái", BigDecimal.ONE, new BigDecimal(amount), null, null,
                new BigDecimal(amount), rate, null, null);
    }
}
