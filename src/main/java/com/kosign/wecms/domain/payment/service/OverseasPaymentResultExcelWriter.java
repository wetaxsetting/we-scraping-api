package com.kosign.wecms.domain.payment.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the WeCMS "Overseas Payment" XLSX export per the product spec:
 *
 * <ul>
 *   <li>A1:V2 — merged title row ("Overseas Payment", Arial 14pt bold, centred).</li>
 *   <li>A3:T3 — merged summary row (USD / VND / KRW totals, Arial 10pt).</li>
 *   <li>Row 4 — 22-column styled header (Arial 10pt bold, #C0C0C0 fill, border, wrap).</li>
 *   <li>Row 5+ — one data row per {@link ExportRow} (Arial 9pt, border, no wrap).</li>
 * </ul>
 *
 * <p>Uses {@code XSSFWorkbook} (not SXSSF) because merged cells require random
 * row access. The overseas-payment result set is bounded in practice (not
 * 50 k-row territory) so heap usage is acceptable.
 */
public final class OverseasPaymentResultExcelWriter {

    private static final String[] HEADERS = {
        "Transfer Date Time", "Transaction Seq", "Status", "Request Date/Time",
        "File Name", "Withdrawal Bank", "Account No.", "Beneficiary",
        "Beneficiary Bank Name", "Bank Identifier Code (BIC)",
        "Beneficiary Account Number", "Beneficiary Address",
        "Transfer Currency", "Transfer Amount", "Fee Payer", "Reporting Account",
        "Memo", "Requestor", "Scheduled Date", "Approval Status",
        "Approval Ref.", "Approval Title",
    };

    // Per-spec column widths (Excel char units).
    private static final double[] COLUMN_WIDTHS = {
        26,    20,    20,    26,    // A  Transfer Date Time      B  Transaction Seq   C  Status             D  Request Date/Time
        40,    38,    30,    35,    // E  File Name               F  Withdrawal Bank   G  Account No.        H  Beneficiary
        38,    32,    38,    40,    // I  Beneficiary Bank Name   J  BIC               K  Bnfc Account No.   L  Beneficiary Address
        20,    25,    28,    24,    // M  Transfer Currency       N  Transfer Amount   O  Fee Payer          P  Reporting Account
        40,    28,    22,    22,    32,    42,  // Q  Memo        R  Requestor         S  Scheduled Date     T  Approval Status  U  Approval Ref.  V  Approval Title
    };

    private static final DateTimeFormatter IN_DTM  = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter IN_DATE  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter OUT_DTM  = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter OUT_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private OverseasPaymentResultExcelWriter() {}

    // ─── public contract ──────────────────────────────────────────────────────

    public record ExportRow(
        String transferDatetime,
        String transactionSeq,
        String status,
        String requestDatetime,
        String fileName,
        String withdrawalBank,
        String accountNo,
        String beneficiaryName,
        String beneficiaryBankName,
        String bic,
        String beneficiaryAccountNo,
        String beneficiaryAddress,
        String transferCurrency,
        BigDecimal transferAmount,
        String feePayer,
        String reportingAccount,
        String memo,
        String requestor,
        String scheduledDate,
        String approvalStatus,
        String approvalRef,
        String approvalTitle
    ) {}

    public static void write(Map<String, BigDecimal> currencyTotals,
                              List<ExportRow> rows,
                              OutputStream out) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("sheet1");

            CellStyle titleStyle   = makeTitleStyle(wb);
            CellStyle summaryStyle = makeSummaryStyle(wb);
            CellStyle headerStyle  = makeHeaderStyle(wb);
            CellStyle dataStyle    = makeDataStyle(wb);
            CellStyle numStyle     = makeNumberStyle(wb);

            // ── Rows 1–2: merged title A1:V2 ───────────────────────────────
            Row r1 = sheet.createRow(0); r1.setHeightInPoints(15);
            Row r2 = sheet.createRow(1); r2.setHeightInPoints(15);
            Cell titleCell = r1.createCell(0);
            titleCell.setCellValue("Overseas Payment");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 21)); // A1:V2

            // ── Row 3: merged summary A3:T3 ────────────────────────────────
            Row r3 = sheet.createRow(2);
            Cell summaryCell = r3.createCell(0);
            summaryCell.setCellValue(buildSummary(currencyTotals));
            summaryCell.setCellStyle(summaryStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 19)); // A3:T3

            // ── Row 4: header ───────────────────────────────────────────────
            Row headerRow = sheet.createRow(3);
            headerRow.setHeightInPoints(30);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            // ── Rows 5+: data ───────────────────────────────────────────────
            int rowNum = 4;
            for (ExportRow row : rows) {
                Row dr = sheet.createRow(rowNum++);
                dr.setHeightInPoints(16.5f);
                str(dr,  0, row.transferDatetime(),    dataStyle);
                str(dr,  1, row.transactionSeq(),      dataStyle);
                str(dr,  2, row.status(),              dataStyle);
                str(dr,  3, row.requestDatetime(),     dataStyle);
                str(dr,  4, row.fileName(),            dataStyle);
                str(dr,  5, row.withdrawalBank(),      dataStyle);
                txt(dr,  6, row.accountNo(),           dataStyle); // preserve leading zeros
                str(dr,  7, row.beneficiaryName(),     dataStyle);
                str(dr,  8, row.beneficiaryBankName(), dataStyle);
                str(dr,  9, row.bic(),                 dataStyle);
                txt(dr, 10, row.beneficiaryAccountNo(), dataStyle); // preserve leading zeros
                str(dr, 11, row.beneficiaryAddress(),  dataStyle);
                str(dr, 12, row.transferCurrency(),    dataStyle);
                num(dr, 13, row.transferAmount(),      numStyle);  // real number so SUM works
                str(dr, 14, row.feePayer(),            dataStyle);
                str(dr, 15, row.reportingAccount(),    dataStyle);
                str(dr, 16, row.memo(),                dataStyle);
                str(dr, 17, row.requestor(),           dataStyle);
                str(dr, 18, row.scheduledDate(),       dataStyle);
                str(dr, 19, row.approvalStatus(),      dataStyle);
                str(dr, 20, row.approvalRef(),         dataStyle);
                str(dr, 21, row.approvalTitle(),       dataStyle);
            }

            // ── Column widths ───────────────────────────────────────────────
            for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
                if (COLUMN_WIDTHS[i] > 0) {
                    sheet.setColumnWidth(i, (int) (COLUMN_WIDTHS[i] * 256));
                }
            }

            wb.write(out);
        }
    }

    // ─── cell writers ─────────────────────────────────────────────────────────

    private static void str(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private static void txt(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col, CellType.STRING);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private static void num(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col, CellType.NUMERIC);
        c.setCellValue(value != null ? value.doubleValue() : 0.0);
        c.setCellStyle(style);
    }

    // ─── summary builder ──────────────────────────────────────────────────────

    private static String buildSummary(Map<String, BigDecimal> totals) {
        String gap = " ".repeat(21);
        BigDecimal usd = totals.getOrDefault("USD", BigDecimal.ZERO);
        BigDecimal vnd = totals.getOrDefault("VND", BigDecimal.ZERO);
        BigDecimal krw = totals.getOrDefault("KRW", BigDecimal.ZERO);
        return "Total Amount (USD) : $" + String.format(Locale.US, "%,.2f", usd)
             + gap
             + "Total Amount (VND) : "  + String.format(Locale.US, "%,.0f", vnd) + "₫"
             + gap
             + "Total Amount (KRW) : ₩" + String.format(Locale.US, "%,.0f", krw);
    }

    // ─── styles ───────────────────────────────────────────────────────────────

    private static CellStyle makeTitleStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setFontName("Arial");
        f.setFontHeightInPoints((short) 14);
        f.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private static CellStyle makeSummaryStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setFontName("Arial");
        f.setFontHeightInPoints((short) 10);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private static CellStyle makeHeaderStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setFontName("Arial");
        f.setFontHeightInPoints((short) 10);
        f.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); // #C0C0C0
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(s);
        return s;
    }

    private static CellStyle makeDataStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setFontName("Arial");
        f.setFontHeightInPoints((short) 9);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(s);
        return s;
    }

    private static CellStyle makeNumberStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setFontName("Arial");
        f.setFontHeightInPoints((short) 9);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorder(s);
        return s;
    }

    private static void applyThinBorder(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    // ─── static helpers (package-visible for service use) ────────────────────

    static String formatDtm(String dtm) {
        if (dtm == null || dtm.length() < 14) return dtm != null ? dtm : "";
        try {
            return LocalDateTime.parse(dtm.substring(0, 14), IN_DTM).format(OUT_DTM);
        } catch (Exception e) {
            return dtm;
        }
    }

    static String formatDate(String dt) {
        if (dt == null || dt.isBlank()) return "";
        try {
            return LocalDate.parse(dt.substring(0, 8), IN_DATE).format(OUT_DATE);
        } catch (Exception e) {
            return dt;
        }
    }

    static String mapStatus(String trnfYn) {
        return "Y".equals(trnfYn) ? "Completed" : "In Progress";
    }

    static String mapFeePayer(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "S", "SHA" -> "Shared";
            case "B", "BEN" -> "Beneficiary charged";
            case "O", "OUR" -> "Ordering Charge";
            default         -> code;
        };
    }

    static String mapApprovalStatus(String code) {
        if (code == null) return "";
        return switch (code) {
            case "00" -> "Pending";
            case "01" -> "Approved";
            case "02" -> "Rejected";
            case "03" -> "Cancelled";
            default   -> code;
        };
    }

    static String safe(Object val) {
        return val == null ? "" : val.toString();
    }

    static String stripExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
