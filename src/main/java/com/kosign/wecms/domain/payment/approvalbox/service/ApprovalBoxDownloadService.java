package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Payment &gt; Approval Box — Excel Download (the legacy template-based
 * generated report). Ports the legacy {@code resl_0001_001} Approval Box
 * Excel, which was composed <em>client-side</em> in
 * {@code resl_0001_001_osmc.ts} ({@code makeGrid_excel} / {@code download_excel}
 * / {@code formatExcel}) with the ERP audit written by {@code downloadProc.jsp}.
 *
 * <p>This is the template-populate architecture decided by the PO in
 * {@code docs/audits/approval-box-excel-download-audit.md}. Like
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService}
 * and {@code ContactsDownloadService} it deliberately does <strong>NOT</strong>
 * reuse the shared {@code common/export/ExcelExportService} pipeline (which
 * builds an empty workbook from scratch and cannot preserve the template's
 * merged title {@code A1:J2}, the summary-row merges {@code A3:E3}/{@code G3:J3},
 * the baked column widths, or the {@code #,##0} amount format). Instead it loads
 * the styled locale template from the backend classpath via
 * {@link ClassPathResource} and writes data into the pre-styled rows so all
 * template formatting is preserved.
 *
 * <p><b>Template is {@code .xls} (HSSF), not {@code .xlsx}.</b> The supplied
 * {@code Approval Box_{EN,KO,VI}.xls} files are OLE2/BIFF8 workbooks;
 * {@link WorkbookFactory#create(InputStream)} returns an {@code HSSFWorkbook}
 * and the streamed MIME is {@code application/vnd.ms-excel}. HSSF's 65 536-row
 * cap is well above the {@link ApprovalBoxService#MAX_EXPORT_ROWS} = 50 000
 * export limit.
 *
 * <p><b>Data source (legacy parity):</b> reuses
 * {@link ApprovalBoxService#listForExport} so the download and the on-screen
 * list draw from one query path — same filters (date range, status tab,
 * keyword), same sort, same tenant + per-user visibility scope, unpaged
 * (legacy ran {@code EXPT_RESOL_MSTR_R003} with {@code PAGE_PER_CNT = 0}).
 *
 * <p><b>Layout (verified against the templates):</b>
 * <ul>
 *   <li>Row 0 (merged {@code A1:J2}) — localized title ("Approval Box"),
 *       inherited from the template (untouched).</li>
 *   <li>Row 2 (merged {@code A3:E3} / {@code G3:J3}) — summary row: left =
 *       {@code Date: FROM ~ TO}, right = {@code N document(s) : Total USD : …
 *       / Total VND : … / Total KRW : …} (legacy {@code left_title} /
 *       {@code right_title} parity).</li>
 *   <li>Row 3 — localized header row, inherited from the template (untouched).</li>
 *   <li>Row 4+ — data, one row per approval, 10 columns A–J. The Approver
 *       column is intentionally excluded (legacy {@code resl_0001_001_osmc.ts}
 *       skips {@code APPRER_NM_2}).</li>
 * </ul>
 *
 * <p>The ERP audit ({@code TRNS_TYPE_CD = "11"} Approval Result, {@code ERP_TYPE
 * = "W"}) is written by the controller via
 * {@code ErpTrnsHistoryWriter.recordExportedBytes("11", …)} — the bytes-based
 * analogue of the generic provider's {@code recordExportedFile("11", …)}.
 *
 * <p>Locale selection mirrors {@link InternalTransferDownloadService} /
 * {@code ContactsDownloadService}: {@code EN} / {@code KO} / {@code VI} each
 * load their own template; {@code KM} and anything else fall back to {@code EN}
 * (refactor standard — legacy defaulted the unknown locale to KO, but the
 * refactor standardises KM → EN across every template-download service).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalBoxDownloadService {

    /** Classpath folder holding the three locale templates. */
    static final String TEMPLATE_DIR = "templates/payment/approval-box/";
    /** Summary row (merged {@code A3:E3} left / {@code G3:J3} right) — POI index 2. */
    static final int SUMMARY_ROW_INDEX = 2;
    /** Left summary cell (top-left of the {@code A3:E3} merge). */
    static final int SUMMARY_LEFT_COL = 0;
    /** Right summary cell (top-left of the {@code G3:J3} merge). */
    static final int SUMMARY_RIGHT_COL = 6;
    /** Header is row 4 (1-based); data rows start at row 5 → index 4. */
    static final int FIRST_DATA_ROW_INDEX = 4;
    /** 10 columns A–J (Approver excluded), identical order across EN/KO/VI. */
    static final int COLUMN_COUNT = 10;
    /** Amount column (G) — the only numeric column; template carries {@code #,##0}. */
    static final int AMOUNT_COL = 6;

    private final ApprovalBoxService approvalBoxService;

    /** Generated report payload — workbook bytes + the download filename. */
    public record GeneratedReport(byte[] content, String fileName) { }

    /**
     * Generate the 10-column Approval Box download report for the current
     * filters. Reuses {@link ApprovalBoxService#listForExport} so the export
     * mirrors the on-screen list exactly.
     *
     * @param fromDate      required {@code YYYYMMDD}
     * @param toDate        required {@code YYYYMMDD}
     * @param srchWd        optional keyword
     * @param apprTypeCd    optional tab ({@code 00}/{@code 01}/{@code 02}/{@code 03}); blank = all
     * @param sortField     optional whitelisted FIELD_ID
     * @param sortDirection optional {@code ASC}/{@code DESC}
     * @param langParam     optional UI-locale hint ({@code EN}/{@code KO}/{@code VI}); blank → JWT langCd → EN; KM/other → EN
     */
    @Transactional(readOnly = true)
    public GeneratedReport generate(
            String fromDate, String toDate, String srchWd, String apprTypeCd,
            String sortField, String sortDirection, String langParam) {

        String lang = resolveLang(langParam);
        List<ApprovalBoxRow> rows = approvalBoxService.listForExport(
                fromDate, toDate, srchWd, apprTypeCd, sortField, sortDirection);

        byte[] bytes = populate(lang, fromDate, toDate, rows);
        String fileName = "Approval Box_" + lang + ".xls";
        log.info("feature=approval-box report-download bizKey={} lang={} rows={}",
                TenantContext.currentBizKey(), lang, rows.size());
        return new GeneratedReport(bytes, fileName);
    }

    private byte[] populate(String lang, String fromDate, String toDate, List<ApprovalBoxRow> rows) {
        String resourcePath = TEMPLATE_DIR + "Approval Box_" + lang + ".xls";
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);

            // Capture the per-column cell style from the template's first data
            // row (index 4). Reusing these shared CellStyle objects on every
            // written cell preserves the template's borders / alignment / fill
            // and the amount column's #,##0 format on all rows, and avoids the
            // HSSF cell-style limit. (Same technique as InternalTransfer /
            // Contacts download services.)
            CellStyle[] colStyle = captureColStyles(sheet);

            // Summary row (idx 2) — legacy left_title / right_title parity.
            LinkedHashMap<String, BigDecimal> totals = perCurrencyTotals(rows);
            setCellText(sheet, SUMMARY_ROW_INDEX, SUMMARY_LEFT_COL,
                    composeSubHeaderLeft(fromDate, toDate));
            setCellText(sheet, SUMMARY_ROW_INDEX, SUMMARY_RIGHT_COL,
                    composeSubHeaderRight(rows.size(), totals));

            // Data rows from idx 4 — one row per approval.
            int rowIndex = FIRST_DATA_ROW_INDEX;
            for (ApprovalBoxRow r : rows) {
                writeRow(sheet, rowIndex++, colStyle, r);
            }

            // Drop any leftover pre-styled empty template rows below the last
            // written data row so the download carries no trailing blank rows
            // (the template ships ~68 pre-styled rows to index 72). Styles were
            // captured above before removal, so this is safe.
            int lastRow = sheet.getLastRowNum();
            for (int r = rowIndex; r <= lastRow; r++) {
                Row leftover = sheet.getRow(r);
                if (leftover != null) sheet.removeRow(leftover);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Template missing from the jar, or write failure — surfaces as
            // WBE9999 via the global handler with the stack logged.
            throw new UncheckedIOException(
                    "Failed to generate Approval Box download report (lang=" + lang + ")", e);
        }
    }

    private static CellStyle[] captureColStyles(Sheet sheet) {
        CellStyle[] colStyle = new CellStyle[COLUMN_COUNT];
        Row templateRow = sheet.getRow(FIRST_DATA_ROW_INDEX);
        if (templateRow != null) {
            for (int c = 0; c < COLUMN_COUNT; c++) {
                Cell cell = templateRow.getCell(c);
                if (cell != null) colStyle[c] = cell.getCellStyle();
            }
        }
        return colStyle;
    }

    /**
     * Write one approval into columns A–J. Mirrors the legacy
     * {@code formatExcel} / the refactor {@code ApprovalBoxExportProvider}
     * mapping 1:1 (P1 — data parity must be preserved across the mechanism
     * swap). The Approver column is intentionally absent.
     */
    private static void writeRow(Sheet sheet, int idx, CellStyle[] colStyle, ApprovalBoxRow r) {
        Row row = rowAt(sheet, idx);
        setText  (row, 0, colStyle, formatDateLegacy(datePart(r.regDtm()))); // Date
        setText  (row, 1, colStyle, nullToEmpty(r.typeLabel()));            // Type
        setText  (row, 2, colStyle, nullToEmpty(r.docNo()));                // Document No.
        setText  (row, 3, colStyle, nullToEmpty(r.title()));               // Title
        setText  (row, 4, colStyle, nullToEmpty(r.fileName()));            // File Name
        setText  (row, 5, colStyle, nullToEmpty(r.currency()));            // Transfer Currency
        setAmount(row, 6, colStyle, parseAmount(r.totPayAmt()));           // Transfer Amount
        setText  (row, 7, colStyle, apprSttsLabel(r.apprStts()));          // Approval Status
        setText  (row, 8, colStyle, formatDateLegacy(r.payDate()));        // Scheduled Date
        setText  (row, 9, colStyle, nullToEmpty(r.draftUserNm()));         // Requestor
    }

    /* ----------------------------------------------------------------- */

    /** Sum the exported rows into the three footer buckets (USD / VND / KRW),
     *  mirroring {@code ApprovalBoxExportProvider}. Other currencies are not
     *  in the 3-bucket summary (legacy parity). */
    private static LinkedHashMap<String, BigDecimal> perCurrencyTotals(List<ApprovalBoxRow> rows) {
        LinkedHashMap<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("USD", BigDecimal.ZERO);
        totals.put("VND", BigDecimal.ZERO);
        totals.put("KRW", BigDecimal.ZERO);
        for (ApprovalBoxRow r : rows) {
            BigDecimal amt = parseAmount(r.totPayAmt());
            String ccy = r.currency() != null ? r.currency().toUpperCase(Locale.ROOT) : "";
            if (amt != null && totals.containsKey(ccy)) {
                totals.put(ccy, totals.get(ccy).add(amt));
            }
        }
        return totals;
    }

    /** Legacy {@code left_title} — {@code Date: FROM ~ TO} (DD-Mon-YYYY).
     *  Empty when both dates are blank so a missing filter doesn't render
     *  {@code Date: null ~ null}. */
    static String composeSubHeaderLeft(String fromDate, String toDate) {
        String from = formatDateLegacy(fromDate);
        String to   = formatDateLegacy(toDate);
        if (from.isEmpty() && to.isEmpty()) return "";
        return "Date: " + from + " ~ " + to;
    }

    /** Legacy {@code right_title} — {@code N document(s) : Total USD : … /
     *  Total VND : … / Total KRW : …}. vi-VN number format (USD 2dp,
     *  VND/KRW 0dp). Identical composition to {@code ApprovalBoxExportProvider}. */
    static String composeSubHeaderRight(int rowCount, LinkedHashMap<String, BigDecimal> totals) {
        StringBuilder sb = new StringBuilder();
        sb.append(rowCount).append(" document(s)");
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            sb.append(" : Total ").append(e.getKey()).append(" : ")
              .append(formatTotalLegacy(e.getValue(), e.getKey()))
              .append(" /");
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
            sb.setLength(sb.length() - 2); // trim trailing " /"
        }
        return sb.toString();
    }

    private static String formatTotalLegacy(BigDecimal value, String currency) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        if ("USD".equalsIgnoreCase(currency)) {
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
        } else {
            nf.setMinimumFractionDigits(0);
            nf.setMaximumFractionDigits(0);
        }
        return nf.format(value != null ? value : BigDecimal.ZERO);
    }

    /** {@code yyyyMMdd} or {@code yyyyMMddHHmmss} → {@code DD-Mon-YYYY}
     *  (legacy {@code formatDateLang} parity, English month). Blank/short → "". */
    static String formatDateLegacy(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() < 8) return "";
        try {
            int year  = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(4, 6));
            int day   = Integer.parseInt(s.substring(6, 8));
            if (month < 1 || month > 12) return s;
            String[] months = {
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
            };
            return String.format("%02d-%s-%04d", day, months[month - 1], year);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    /** {@code yyyyMMddHHmmss} → {@code yyyyMMdd}; input unchanged if already ≤ 8. */
    private static String datePart(String raw) {
        if (raw == null) return null;
        return raw.length() >= 14 ? raw.substring(0, 8) : raw;
    }

    /** APPR_STTS (P/C/R/I) → English label. Matches {@code ApprovalBoxExportProvider}. */
    static String apprSttsLabel(String code) {
        if (code == null) return "";
        return switch (code) {
            case "P" -> "Pending";
            case "C" -> "Approved";
            case "R" -> "Rejected";
            case "I" -> "In Progress";
            default  -> code;
        };
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** EN / KO / VI templates; KM and anything else → EN (refactor standard). */
    private static String resolveLang(String langParam) {
        LangCd lc = (langParam != null && !langParam.isBlank())
                ? LangCd.fromCodeOrDefault(langParam)
                : TenantContext.currentLangCd();
        if (lc == null) return "EN";
        return switch (lc) {
            case KO -> "KO";
            case VI -> "VI";
            default -> "EN"; // EN + KM (no template) + any unmapped
        };
    }

    private static Row rowAt(Sheet sheet, int idx) {
        Row row = sheet.getRow(idx);
        return row != null ? row : sheet.createRow(idx);
    }

    private static void setText(Row row, int col, CellStyle[] colStyle, String value) {
        cellAt(row, col, colStyle).setCellValue(value == null ? "" : value);
    }

    private static void setAmount(Row row, int col, CellStyle[] colStyle, BigDecimal value) {
        Cell cell = cellAt(row, col, colStyle);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(value.doubleValue());
        }
    }

    private static Cell cellAt(Row row, int col, CellStyle[] colStyle) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
            if (colStyle != null && col < colStyle.length && colStyle[col] != null) {
                cell.setCellStyle(colStyle[col]);
            }
        }
        return cell;
    }

    /** Overwrite a summary-row cell's text, preserving its existing template
     *  style. The cell already exists in the template (it carries the merged
     *  label), so this never creates an unstyled cell. */
    private static void setCellText(Sheet sheet, int rowIdx, int col, String value) {
        Row row = rowAt(sheet, rowIdx);
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
