package com.kosign.wecms.domain.payment.vendorpayment.service;

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
 * Payment &gt; Vendor Payment — Excel Download (the legacy 22-column
 * populated report). Ports legacy {@code pymn_0002_002_r002} (the generic
 * "Vendor and Expense Payment > Download Excel" handler), which is driven
 * from all four Vendor Payment screens via the {@code FILE_URL} →
 * {@code pymn_0002_002_r002.act} wiring (PymnHelper). The legacy handler is
 * shared with Expense Payment and discriminated by
 * {@code SCDUL_PYMN_TYPE_CD} — {@code "01"} selects the Vendor template,
 * {@code "02"} the Expense template; this service only handles Vendor.
 *
 * <p>This is the template-based generated report decided in
 * {@code docs/audits/vendor-payment-excel-download-audit.md}. It mirrors
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService}
 * one-for-one, with two deliberate differences: (1) <strong>22 columns</strong>
 * A–V — Internal Transfer's 21 plus a Vendor-specific <strong>Contact Name</strong>
 * ({@code TRCO_CMENM}) inserted at column J (index 9), shifting K–V by one;
 * (2) the locale template is {@code WeCMS_Vendor_Payment_download_{EN,KO,VI}.xlsx}
 * and the type code is {@link ScdulPymnTypeCd#VENDOR} ({@code "01"}).
 *
 * <p>It deliberately does <strong>NOT</strong> reuse the shared
 * {@code common/export/ExcelExportService} pipeline — that writer builds an
 * empty workbook from scratch and cannot preserve the template's merged
 * title ({@code A1:V2}), baked column widths, and per-column number / date
 * formats. Instead this service loads the styled locale template from the
 * backend classpath via {@link ClassPathResource} and writes data into the
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
public class VendorPaymentDownloadService {

    /** Classpath folder holding the three locale templates. */
    static final String TEMPLATE_DIR = "templates/vendor-payment/";
    /** Excel header is row 3 (1-based); data rows start at row 4 → index 3. */
    static final int FIRST_DATA_ROW_INDEX = 3;
    /** 22 columns A–V, identical order across EN/KO/VI templates. */
    static final int COLUMN_COUNT = 22;

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
     * Generate the 22-column Vendor Payment download report for one batch.
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
                        bizKey, scdulPymnNo, ScdulPymnTypeCd.VENDOR.code())
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
        String approvalStatus = approval.map(a -> approvalStatusLabel(a.getApprTypeCd())).orElse("");
        String approvalRef = approval.map(ExptResolMstr::getDocNo).orElse(null);
        String approvalTitle = approval.map(ExptResolMstr::getTitl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(master.getTitl());

        byte[] bytes = populate(lang, master, details, resultByDetlNo, dispatched,
                outBankNm, requestor, scheduledDate, approvalStatus, approvalRef,
                approvalTitle, bankNmCache);

        String fileName = "WeCMS_Vendor_Payment_download_" + lang + ".xlsx";
        log.info("feature=vendor-payment report-download bizKey={} scdulPymnNo={} lang={} lines={}",
                bizKey, scdulPymnNo, lang, details.size());
        return new GeneratedReport(bytes, fileName);
    }

    private byte[] populate(
            String lang, ScdulPymnSumr master, List<ScdulPymnSumrDetl> details,
            Map<String, DispatchResult> resultByDetlNo, boolean dispatched,
            String outBankNm, String requestor, String scheduledDate,
            String approvalStatus, String approvalRef, String approvalTitle,
            Map<String, String> bankNmCache) {

        String resourcePath = TEMPLATE_DIR + "WeCMS_Vendor_Payment_download_" + lang + ".xlsx";
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

                // A–V (0..21) — identical order across EN/KO/VI templates.
                // Vendor = Internal Transfer's 21 columns + Contact Name
                // (TRCO_CMENM) inserted at index 9 (J), shifting K–V by one.
                setText  (row,  0, colStyle, dispatched ? formatDtm14(master.getTrnfDtm()) : "");
                setText  (row,  1, colStyle, dispatched ? statusLabel(r) : "");
                setText  (row,  2, colStyle, formatDtm14(master.getRegDtm()));
                setText  (row,  3, colStyle, master.getTitl());
                setText  (row,  4, colStyle, outBankNm);
                setText  (row,  5, colStyle, master.getWdrlActNo());
                setText  (row,  6, colStyle, d.getTarBankCd());
                setText  (row,  7, colStyle, resolveBankNm(d.getTarBankCd(), bankNmCache));
                setText  (row,  8, colStyle, d.getFincMdaNo());
                setText  (row,  9, colStyle, d.getTrcoCmenm());            // J — Contact Name (Vendor-specific)
                setText  (row, 10, colStyle, d.getRcptNm());
                setAmount(row, 11, colStyle, d.getTranAmt());
                setText  (row, 12, colStyle, d.getTrnfCcyCd());
                setText  (row, 13, colStyle, d.getTranUsag());
                setText  (row, 14, colStyle, d.getChartAcctCd());
                setText  (row, 15, colStyle, d.getNote());
                setText  (row, 16, colStyle, d.getDocNo());
                setText  (row, 17, colStyle, requestor);
                setText  (row, 18, colStyle, formatDate8(scheduledDate));
                setText  (row, 19, colStyle, approvalStatus);
                setText  (row, 20, colStyle, approvalRef);
                setText  (row, 21, colStyle, approvalTitle);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Template missing from the jar, or write failure — surfaces as
            // WBE9999 via the global handler with the stack logged.
            throw new UncheckedIOException(
                    "Failed to generate Vendor Payment download report (lang=" + lang + ")", e);
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
     * classification used by Internal Transfer's download report
     * / legacy {@code pymn_0002_002_r002_view.jsp}. English-only for now —
     * the localized status/approval-status cell text is a documented
     * follow-up (the report headers ARE localized via the template).
     */
    static String statusLabel(DispatchResult r) {
        String ps = r.procStat() == null ? "" : r.procStat().trim();
        String rs = r.rsCd()     == null ? "" : r.rsCd().trim();
        if (ps.isEmpty() && rs.isEmpty())               return "In Progress";
        if ("0".equals(ps) && "0000".equals(rs))        return "Success";
        if ("9".equals(ps) || "1".equals(ps) || "6".equals(ps)) return "In Progress";
        return "Failed";
    }

    /** Approval Status label (col T) from {@code EXPT_RESOL_MSTR.APPR_TYPE_CD}. */
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
