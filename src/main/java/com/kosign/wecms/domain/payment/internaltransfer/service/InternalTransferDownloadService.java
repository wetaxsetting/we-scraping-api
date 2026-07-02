package com.kosign.wecms.domain.payment.internaltransfer.service;

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
 * Payment &gt; Internal Transfer — Excel Download (the legacy 21-column
 * populated report). Ports legacy {@code pymn_0009_003_r002} (Scheduled
 * Transfer detail) and {@code pymn_0010_002_r002} (Transfer Result
 * detail), which share one locale template and one 21-column layout.
 *
 * <p>This is the template-based generated report decided in
 * {@code docs/audits/internal-transfer-excel-download-audit.md} §5/§14/§15.
 * It deliberately does <strong>NOT</strong> reuse the shared
 * {@code common/export/ExcelExportService} pipeline — that writer builds
 * an empty workbook from scratch and cannot preserve the template's
 * merged title ({@code A1:U2}), baked column widths, and per-column
 * number / date formats. Instead this service loads the styled locale
 * template from the backend classpath via {@link ClassPathResource} and
 * writes data into the pre-styled rows so all template formatting is
 * preserved (legacy parity).
 *
 * <p>Locale selection mirrors legacy {@code LocaleHelper.getLanguage}:
 * {@code EN} / {@code KO} / {@code VI} each load their own template;
 * {@code KM} and any other value fall back to {@code EN}.
 *
 * <p>The uploaded Excel file is the import source only and is NOT
 * returned by this download (that legacy-vs-refactor gap is the reason
 * this report exists — see the audit). Tenant scope is enforced on every
 * query via {@link TenantContext#currentBizKey()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferDownloadService {

    /** Classpath folder holding the three locale templates. */
    static final String TEMPLATE_DIR = "templates/internal-transfer/";
    /** Excel header is row 3 (1-based); data rows start at row 4 → index 3. */
    static final int FIRST_DATA_ROW_INDEX = 3;
    /** 21 columns A–U, identical order across EN/KO/VI templates. */
    static final int COLUMN_COUNT = 21;

    private final ScdulPymnSumrRepository     sumrRepository;
    private final ScdulPymnSumrDetlRepository detlRepository;
    private final ExptResolMstrRepository     exptResolMstrRepository;
    /** Shared two-step bank-name resolver (BANK_INFM → NAPAS/CITAD function
     *  → CITAD canonical fallback) — the same chain the Result detail uses. */
    private final TransferLineDetailMapper    transferLineDetailMapper;
    /** Per-line dispatch-result resolver — supplies PROC_STAT / RS_CD for the
     *  Status column. Returns {@link DispatchResult#EMPTY} when the dispatch
     *  tables are unreachable (pre-dispatch / fresh DB). */
    private final DispatchResultResolver      dispatchResultResolver;

    /** Generated report payload — workbook bytes + the download filename. */
    public record GeneratedReport(byte[] content, String fileName) { }

    /**
     * Generate the 21-column Internal Transfer download report for one
     * batch.
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
        String lang = resolveLang(langParam);
        // Single-batch download: throwIfMissing=true so an unknown / cross-tenant
        // id still surfaces WBE2060 exactly as before.
        byte[] bytes = buildWorkbook(lang, bizKey, List.of(scdulPymnNo), true);
        String fileName = "WeCMS_Internal_Transfer_download_" + lang + ".xlsx";
        log.info("feature=internal-transfer report-download bizKey={} scdulPymnNo={} lang={}",
                bizKey, scdulPymnNo, lang);
        return new GeneratedReport(bytes, fileName);
    }

    /**
     * Bulk variant — the Transfer Result toolbar Excel. Generates ONE
     * workbook from the same locale template as the per-row report, with
     * every listed batch's transfer detail lines appended into the single
     * sheet (legacy {@code pymn_0002_002_r003} bulk-report parity — same
     * template, same 21-column layout as the row report, all filtered rows).
     *
     * <p>Unknown / cross-tenant ids are skipped (throwIfMissing=false) rather
     * than aborting the whole export — the id set comes from the result
     * service's filtered query, so a vanished row just drops out.
     *
     * @param scdulPymnNos ordered batch keys (the filtered result list)
     * @param langParam    optional UI-locale hint (EN/KO/VI; KM/other → EN)
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateBulk(List<String> scdulPymnNos, String langParam) {
        String bizKey = requireTenant();
        String lang = resolveLang(langParam);
        List<String> ids = scdulPymnNos == null ? List.of() : scdulPymnNos;
        byte[] bytes = buildWorkbook(lang, bizKey, ids, false);
        String fileName = "WeCMS_Internal_Transfer_download_" + lang + ".xlsx";
        log.info("feature=internal-transfer bulk-report-download bizKey={} batches={} lang={}",
                bizKey, ids.size(), lang);
        return new GeneratedReport(bytes, fileName);
    }

    /**
     * Load the locale template once and append every batch's detail rows
     * into the single sheet, preserving the template's styles. Shared by the
     * single-batch {@link #generate} and bulk {@link #generateBulk} paths.
     */
    private byte[] buildWorkbook(String lang, String bizKey, List<String> ids, boolean throwIfMissing) {
        String resourcePath = TEMPLATE_DIR + "WeCMS_Internal_Transfer_download_" + lang + ".xlsx";
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);
            CellStyle[] colStyle = captureColStyles(sheet);
            // One bank-name cache across ALL batches — the same bank resolves
            // once for the whole workbook (legacy getBankName parity).
            Map<String, String> bankNmCache = new HashMap<>();

            int rowIdx = FIRST_DATA_ROW_INDEX;
            for (String id : ids) {
                rowIdx = appendBatch(sheet, colStyle, rowIdx, bizKey, id, bankNmCache, throwIfMissing);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Template missing from the jar, or write failure — surfaces as
            // WBE9999 via the global handler with the stack logged.
            throw new UncheckedIOException(
                    "Failed to generate Internal Transfer download report (lang=" + lang + ")", e);
        }
    }

    /**
     * Capture the per-column cell style from the template's first data row
     * (index 3). Reusing these shared CellStyle objects for every written
     * cell preserves the template's number / date formats, borders,
     * alignment and fill on all rows, and avoids the 64k-style limit
     * (legacy parity: write into pre-styled rows).
     */
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
     * Resolve one batch and write its transfer detail lines into the sheet
     * starting at {@code startIdx}. Returns the next free row index.
     *
     * @param throwIfMissing when true (single-batch path) a missing /
     *                       cross-tenant batch throws {@code WBE2060}; when
     *                       false (bulk path) it is skipped.
     */
    private int appendBatch(Sheet sheet, CellStyle[] colStyle, int startIdx,
                            String bizKey, String scdulPymnNo,
                            Map<String, String> bankNmCache, boolean throwIfMissing) {
        Optional<ScdulPymnSumr> masterOpt = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, scdulPymnNo, ScdulPymnTypeCd.INTERNAL.code());
        if (masterOpt.isEmpty()) {
            if (throwIfMissing) throw new BusinessException("WBE2060");
            return startIdx;
        }
        ScdulPymnSumr master = masterOpt.get();

        List<ScdulPymnSumrDetl> details = detlRepository.findByBizKeyAndScdulPymnNo(
                bizKey, scdulPymnNo, ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC);

        Optional<ExptResolMstr> approval = loadApproval(bizKey, master.getApprSeqNo());
        Map<String, DispatchResult> resultByDetlNo =
                dispatchResultResolver.resolve(master, details);

        boolean dispatched = "Y".equalsIgnoreCase(trim(master.getTrnfYn()));
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

        for (int i = 0; i < details.size(); i++) {
            ScdulPymnSumrDetl d = details.get(i);
            DispatchResult r = resultByDetlNo.getOrDefault(
                    d.getScdulPymnDetlNo(), DispatchResult.EMPTY);
            Row row = rowAt(sheet, startIdx + i);

            // A–U (0..20) — identical order across EN/KO/VI templates.
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
            setText  (row, 12, colStyle, d.getTranUsag());
            setText  (row, 13, colStyle, d.getChartAcctCd());
            setText  (row, 14, colStyle, d.getNote());
            setText  (row, 15, colStyle, d.getDocNo());
            setText  (row, 16, colStyle, requestor);
            setText  (row, 17, colStyle, formatDate8(scheduledDate));
            setText  (row, 18, colStyle, approvalStatus);
            setText  (row, 19, colStyle, approvalRef);
            setText  (row, 20, colStyle, approvalTitle);
        }
        return startIdx + details.size();
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
     * classification in {@code InternalTransferResultService.countByStatus}
     * / legacy {@code pymn_0010_002.js:238-253}. English-only for now — the
     * localized status/approval-status cell text is a documented follow-up
     * (the report headers ARE localized via the template).
     */
    static String statusLabel(DispatchResult r) {
        String ps = r.procStat() == null ? "" : r.procStat().trim();
        String rs = r.rsCd()     == null ? "" : r.rsCd().trim();
        if (ps.isEmpty() && rs.isEmpty())               return "In Progress";
        if ("0".equals(ps) && "0000".equals(rs))        return "Success";
        if ("9".equals(ps) || "1".equals(ps) || "6".equals(ps)) return "In Progress";
        return "Failed";
    }

    /** Approval Status label (col S) from {@code EXPT_RESOL_MSTR.APPR_TYPE_CD}. */
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
