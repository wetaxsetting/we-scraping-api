package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.ScdulPymnTypeCd;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Payment &gt; Payroll Payment — Excel Download (the legacy 20-column
 * populated report). Ports legacy {@code pymn_0006_002_r002} (the dedicated
 * "Payroll Payment > Download Excel" handler) + the bulk variant
 * {@code pymn_0006_002_r003}, driven from all four Payroll Payment screens
 * via the {@code FILE_URL} → {@code pymn_0006_002_r002.act} wiring
 * (PymnHelper, SVC_ID = {@code pymn_0005_001} / {@code pymn_0005_003} /
 * {@code pymn_0006_001} / {@code pymn_0006_002}). The type code carried in
 * the legacy {@code FILE_URL} is {@link ScdulPymnTypeCd#PAYROLL} ({@code "00"}).
 *
 * <p>This is the template-based generated report decided in
 * {@code docs/audits/payroll-payment-excel-download-audit.md}. It follows the
 * same architecture as
 * {@link com.kosign.wecms.domain.payment.vendorpayment.service.VendorPaymentDownloadService}
 * and
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService},
 * but the Payroll report is a <strong>distinct 20-column layout</strong>
 * (legacy {@code pymn_0006_002_r002_view.jsp}'s {@code for (i < 20)} loop) —
 * columns A–N are header-identical to Internal Transfer, then Payroll:
 * <ul>
 *   <li><strong>has NO Contact Name</strong> column (Vendor's col J);</li>
 *   <li><strong>drops</strong> Internal Transfer's Description and per-row
 *       Document No. columns;</li>
 *   <li><strong>adds</strong> a payroll-specific <strong>Payroll Type</strong>
 *       column at <strong>O</strong> sourced from
 *       {@code EXPT_RESOL_MSTR.APPR_FUND_TP} ({@code "0"}=Salary /
 *       {@code "1"}=Bonus / else blank — the same code the Payroll Result list
 *       already surfaces);</li>
 *   <li>sources <strong>Remarks</strong> (col M) from
 *       {@code SCDUL_PYMN_SUMR_DETL.WDRL_ACCT_RMRK} (NOT {@code TRAN_USAG} as
 *       Vendor / Internal Transfer do), and <strong>Reporting Account</strong>
 *       (col N) from {@code CHART_ACCT_CD}.</li>
 * </ul>
 * The locale template is {@code WeCMS_Payroll_download_{EN,KO,VI}.xlsx} (note:
 * no "Payment" in the filename) and the workbook title cell reads
 * {@code Payroll Payments} / {@code 급여 지급} / {@code Thanh toán lương}
 * (baked into the template, not written here).
 *
 * <p>It deliberately does <strong>NOT</strong> reuse the shared
 * {@code common/export/ExcelExportService} pipeline — that writer builds an
 * empty workbook from scratch and cannot preserve the template's merged
 * title ({@code A1:T2}), baked column widths, and per-column cell styles.
 * Instead this service loads the styled locale template from the backend
 * classpath via {@link ClassPathResource} and writes data into the
 * pre-styled rows so all template formatting is preserved (legacy parity).
 *
 * <p>Locale selection mirrors legacy {@code LocaleHelper.getLanguage}:
 * {@code EN} / {@code KO} / {@code VI} each load their own template;
 * {@code KM} and any other value fall back to {@code EN}.
 *
 * <p>The uploaded Excel file is the import source only and is NOT returned
 * by this download (it stays served by {@code GET /{id}/file}). Tenant scope
 * is enforced on every query via {@link TenantContext#currentBizKey()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollPaymentDownloadService {

    /** Classpath folder holding the three locale templates. */
    static final String TEMPLATE_DIR = "templates/payroll-payment/";
    /** Excel header is row 3 (1-based); data rows start at row 4 → index 3. */
    static final int FIRST_DATA_ROW_INDEX = 3;
    /** 20 columns A–T, identical order across EN/KO/VI templates. */
    static final int COLUMN_COUNT = 20;

    /** {@code APPR_FUND_TP} = Salary (Payroll Type col O). */
    private static final String FUND_TP_SALARY = "0";
    /** {@code APPR_FUND_TP} = Bonus (Payroll Type col O). */
    private static final String FUND_TP_BONUS  = "1";

    private final ScdulPymnSumrRepository     sumrRepository;
    private final ScdulPymnSumrDetlRepository detlRepository;
    private final ExptResolMstrRepository     exptResolMstrRepository;
    /** Shared two-step bank-name resolver (BANK_INFM → NAPAS/CITAD function
     *  → CITAD canonical fallback) — the same chain Internal Transfer uses. */
    private final TransferLineDetailMapper    transferLineDetailMapper;
    /** Per-line dispatch-result resolver — supplies PROC_STAT / RS_CD for the
     *  Status column. Returns {@link DispatchResult#EMPTY} when the dispatch
     *  tables are unreachable (pre-dispatch / fresh DB). */
    private final DispatchResultResolver      dispatchResultResolver;

    /** Generated report payload — workbook bytes + the download filename. */
    public record GeneratedReport(byte[] content, String fileName) { }

    /**
     * Generate the 20-column Payroll Payment download report for one batch.
     *
     * @param scdulPymnNo the batch key ({@code SCDUL_PYMN_NO})
     * @param langParam   optional UI-locale hint (e.g. {@code "KO"}); when
     *                    blank the JWT {@code langCd} is used, then EN
     * @throws BusinessException {@code WBE2060} when the batch is missing
     *         or not in the caller's tenant
     */
    @Transactional(readOnly = true)
    public GeneratedReport generate(String scdulPymnNo, String langParam) {
        String bizKey = requireTenant();

        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, scdulPymnNo, ScdulPymnTypeCd.PAYROLL.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));

        List<ScdulPymnSumrDetl> details = detlRepository.findByBizKeyAndScdulPymnNo(
                bizKey, scdulPymnNo, ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC);

        Optional<ExptResolMstr> approval = loadApproval(bizKey, master.getApprSeqNo());
        Map<String, DispatchResult> resultByDetlNo =
                dispatchResultResolver.resolve(master, details);

        String lang = resolveLang(langParam);
        boolean dispatched = "Y".equalsIgnoreCase(trim(master.getTrnfYn()));

        // Per-call bank-name cache so a batch with N lines to the same bank
        // resolves the name once, not N times (legacy getBankName parity).
        Map<String, String> bankNmCache = new HashMap<>();
        String outBankNm = resolveBankNm(master.getOutBankCd(), bankNmCache);
        String requestor = approval.map(ExptResolMstr::getDraftUserNm)
                .filter(s -> s != null && !s.isBlank())
                .orElse(master.getRegId());
        String scheduledDate = approval.map(ExptResolMstr::getPayDate).orElse(null);
        String payrollType = approval.map(a -> payrollTypeLabel(a.getApprFundTp())).orElse("");
        String approvalStatus = approval.map(a -> approvalStatusLabel(a.getApprTypeCd())).orElse("");
        String approvalRef = approval.map(ExptResolMstr::getDocNo).orElse(null);
        String approvalTitle = approval.map(ExptResolMstr::getTitl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(master.getTitl());

        byte[] bytes = populate(lang, master, details, resultByDetlNo, dispatched,
                outBankNm, requestor, scheduledDate, payrollType, approvalStatus,
                approvalRef, approvalTitle, bankNmCache);

        String fileName = "WeCMS_Payroll_download_" + lang + ".xlsx";
        log.info("feature=payroll-payment report-download bizKey={} scdulPymnNo={} lang={} lines={}",
                bizKey, scdulPymnNo, lang, details.size());
        return new GeneratedReport(bytes, fileName);
    }

    private byte[] populate(
            String lang, ScdulPymnSumr master, List<ScdulPymnSumrDetl> details,
            Map<String, DispatchResult> resultByDetlNo, boolean dispatched,
            String outBankNm, String requestor, String scheduledDate,
            String payrollType, String approvalStatus, String approvalRef,
            String approvalTitle, Map<String, String> bankNmCache) {

        String resourcePath = TEMPLATE_DIR + "WeCMS_Payroll_download_" + lang + ".xlsx";
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);

            // Capture the per-column cell style from the template's first
            // data row (index 3). Reusing these shared CellStyle objects for
            // every written cell preserves the template's number / date
            // formats, borders, alignment and fill on all rows, and avoids
            // the 64k-style limit (legacy parity: write into pre-styled rows).
            CellStyle[] colStyle = new CellStyle[COLUMN_COUNT];
            Row templateRow = sheet.getRow(FIRST_DATA_ROW_INDEX);
            if (templateRow != null) {
                for (int c = 0; c < COLUMN_COUNT; c++) {
                    Cell cell = templateRow.getCell(c);
                    if (cell != null) colStyle[c] = cell.getCellStyle();
                }
            }

            for (int i = 0; i < details.size(); i++) {
                ScdulPymnSumrDetl d = details.get(i);
                DispatchResult r = resultByDetlNo.getOrDefault(
                        d.getScdulPymnDetlNo(), DispatchResult.EMPTY);
                Row row = rowAt(sheet, FIRST_DATA_ROW_INDEX + i);

                // A–T (0..19) — identical order across EN/KO/VI templates.
                // Payroll = Internal Transfer's A–N, then drops Description +
                // per-row Document No. and adds Payroll Type at O; NO Contact
                // Name. Remarks (M) = WDRL_ACCT_RMRK (payroll-specific source).
                setText  (row,  0, colStyle, dispatched ? formatDtm14(master.getTrnfDtm()) : "");
                setText  (row,  1, colStyle, dispatched ? statusLabel(r) : "");
                setText  (row,  2, colStyle, formatDtm14(master.getRegDtm()));
                setText  (row,  3, colStyle, master.getTitl());
                setText  (row,  4, colStyle, outBankNm);
                setText  (row,  5, colStyle, master.getWdrlActNo());
                setText  (row,  6, colStyle, d.getTarBankCd());
                setText  (row,  7, colStyle, resolveBankNm(d.getTarBankCd(), bankNmCache));
                setText  (row,  8, colStyle, d.getFincMdaNo());
                setText  (row,  9, colStyle, d.getRcptNm());
                setAmount(row, 10, colStyle, d.getTranAmt());
                setText  (row, 11, colStyle, d.getTrnfCcyCd());
                setText  (row, 12, colStyle, d.getWdrlActRmrk());            // M — Remarks (WDRL_ACCT_RMRK)
                setText  (row, 13, colStyle, d.getChartAcctCd());           // N — Reporting Account
                setText  (row, 14, colStyle, payrollType);                  // O — Payroll Type (APPR_FUND_TP)
                setText  (row, 15, colStyle, requestor);
                setText  (row, 16, colStyle, formatDate8(scheduledDate));
                setText  (row, 17, colStyle, approvalStatus);
                setText  (row, 18, colStyle, approvalRef);
                setText  (row, 19, colStyle, approvalTitle);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Template missing from the jar, or write failure — surfaces as
            // WBE9999 via the global handler with the stack logged.
            throw new UncheckedIOException(
                    "Failed to generate Payroll Payment download report (lang=" + lang + ")", e);
        }
    }

    /* ----------------------------------------------------------------- */

    private Optional<ExptResolMstr> loadApproval(String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) return Optional.empty();
        return exptResolMstrRepository
                .findByBizKeyAndApprSeqNoIn(bizKey, List.of(apprSeqNo))
                .stream()
                .findFirst();
    }

    private String resolveBankNm(String code, Map<String, String> cache) {
        String key = code == null ? "" : code.trim();
        if (key.isEmpty()) return null;
        // computeIfAbsent can't store null, so use containsKey semantics.
        if (cache.containsKey(key)) return cache.get(key);
        String nm = transferLineDetailMapper.resolveBankNm(key);
        cache.put(key, nm);
        return nm;
    }

    /**
     * Resolve the template locale. Priority: explicit {@code langParam}
     * (the UI locale the frontend passes) → JWT {@code langCd} → EN.
     * Only EN / KO / VI have a template; KM and anything else fall back to
     * EN (legacy {@code LocaleHelper} parity — KM has no template file).
     */
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
        Cell cell = cellAt(row, col, colStyle);
        cell.setCellValue(value == null ? "" : value);
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
            // Apply the captured template column style only to cells the
            // template didn't pre-create, so pre-styled cells keep their own.
            if (colStyle != null && colStyle[col] != null) {
                cell.setCellStyle(colStyle[col]);
            }
        }
        return cell;
    }

    /** {@code yyyyMMddHHmmss} → {@code dd-MM-yyyy HH:mm:ss}; blank/short → "". */
    static String formatDtm14(String s) {
        String v = trim(s);
        if (v.length() < 14) return "";
        return v.substring(6, 8) + "-" + v.substring(4, 6) + "-" + v.substring(0, 4)
                + " " + v.substring(8, 10) + ":" + v.substring(10, 12) + ":" + v.substring(12, 14);
    }

    /** {@code yyyyMMdd} → {@code dd-MM-yyyy}; blank/short → "". */
    static String formatDate8(String s) {
        String v = trim(s);
        if (v.length() < 8) return "";
        return v.substring(6, 8) + "-" + v.substring(4, 6) + "-" + v.substring(0, 4);
    }

    /**
     * Per-line Status label (col B). Mirrors the chip-consistent
     * classification used by the Vendor / Expense / Internal Transfer
     * download reports / legacy {@code pymn_0006_002_r002_view.jsp}.
     * English-only for now — the localized status/approval-status/payroll-type
     * cell text is a documented follow-up (the report headers ARE localized
     * via the template).
     */
    static String statusLabel(DispatchResult r) {
        String ps = r.procStat() == null ? "" : r.procStat().trim();
        String rs = r.rsCd()     == null ? "" : r.rsCd().trim();
        if (ps.isEmpty() && rs.isEmpty())               return "In Progress";
        if ("0".equals(ps) && "0000".equals(rs))        return "Success";
        if ("9".equals(ps) || "1".equals(ps) || "6".equals(ps)) return "In Progress";
        return "Failed";
    }

    /** Approval Status label (col R) from {@code EXPT_RESOL_MSTR.APPR_TYPE_CD}. */
    static String approvalStatusLabel(String apprTypeCd) {
        String v = trim(apprTypeCd);
        return switch (v) {
            case "00" -> "Pending";
            case "01" -> "Approved";
            case "02" -> "Rejected";
            case "03" -> "In Progress";
            default   -> "";
        };
    }

    /**
     * Payroll Type label (col O) from {@code EXPT_RESOL_MSTR.APPR_FUND_TP}.
     * Legacy {@code FundTp}: {@code "0"}=Salary, {@code "1"}=Bonus; any other
     * value ({@code "9"}/null/blank) renders blank — matching the Payroll
     * Result list's frontend behaviour (the refactor ships the raw code and
     * the list shows blank for 9/null). English-only, consistent with the
     * Status / Approval-Status cell-text divergence above.
     */
    static String payrollTypeLabel(String apprFundTp) {
        String v = trim(apprFundTp);
        if (FUND_TP_SALARY.equals(v)) return "Salary";
        if (FUND_TP_BONUS.equals(v))  return "Bonus";
        return "";
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException("tenant context not populated");
        }
        return bizKey;
    }
}
