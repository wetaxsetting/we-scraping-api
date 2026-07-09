package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnMstrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Payment &gt; Overseas Payment — Excel Download (the legacy 22-column
 * populated report). Ports legacy {@code pymn_0008_002_r002} — the single
 * shared download/report handler that all four Overseas screens
 * (Scheduled list/detail {@code pymn_0007_*}, Result list/detail
 * {@code pymn_0008_*}) route their file-card / row-glyph download to.
 *
 * <p>This is the template-based generated report decided in
 * {@code docs/audits/overseas-payment-excel-download-audit.md} §5/§14/§15,
 * following the pattern already shipped for Internal Transfer / Vendor /
 * Expense / Payroll ({@link InternalTransferDownloadService}). It loads the
 * styled locale template from the backend classpath via
 * {@link ClassPathResource} and writes data into the pre-styled rows so all
 * template formatting is preserved (merged title {@code A1:V2}, the
 * {@code A3:T3} total cell, baked column widths, per-column styles). It does
 * <strong>NOT</strong> reuse {@code common/export/ExcelExportService} (which
 * builds an empty workbook from scratch and cannot preserve the template) and
 * is distinct from {@link OverseasPaymentResultExcelWriter} (the from-scratch
 * list-export writer).
 *
 * <p>Overseas-specific differences from the Internal Transfer twin (documented
 * in the audit, not assumed identical):
 * <ul>
 *   <li>22 columns A–V (not 21) — adds Transaction Seq, BIC, Beneficiary
 *       Address, Fee Payer.</li>
 *   <li>Currency-aware amount number format: VND {@code #,##0}, others
 *       {@code #,##0.00} (legacy {@code getSubCellStyle}).</li>
 *   <li>Fully localized derived strings — Status, Fee Payer, Approval Status
 *       in EN/KO/VI (legacy {@code getLocalizedText}; KO/VI strings copied
 *       verbatim from the JEX source — no invented translations).</li>
 *   <li>The {@code A3:T3} total cell is overwritten with the single-currency
 *       total {@code "Total Amount (<CCY>) : <amount>"} (legacy overwrites the
 *       3-currency template placeholder).</li>
 *   <li>UNION of file-batch type {@code E} ({@code OVRS_PYMN_SUMR(_DETL)}) and
 *       manual type {@code M} ({@code OVRS_PYMN_MSTR}).</li>
 * </ul>
 *
 * <p>Locale selection mirrors legacy {@code LocaleHelper.getLanguage}:
 * {@code EN} / {@code KO} / {@code VI} each load their own template;
 * {@code KM} and any other value fall back to {@code EN}.
 *
 * <p>The uploaded Excel file is the import source only and is NOT returned by
 * this download (that gap is the reason this report exists — see the audit;
 * the uploaded file stays on {@code GET /scheduled/{id}/file}). Tenant scope is
 * enforced on every query via {@link TenantContext#currentBizKey()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasPaymentDownloadService {

    /** Classpath folder holding the three locale templates. */
    static final String TEMPLATE_DIR = "templates/overseas-payment/";
    /** Excel title is A1:V2; total label is A3:T3 (row index 2); header is
     *  Excel row 4 (index 3); data rows start at Excel row 5 → index 4. */
    static final int TOTAL_ROW_INDEX      = 2;
    static final int FIRST_DATA_ROW_INDEX = 4;
    /** 22 columns A–V, identical order across EN/KO/VI templates. */
    static final int COLUMN_COUNT = 22;
    /** Amount column index (N) — currency-aware number format applied. */
    static final int AMOUNT_COL_INDEX = 13;

    private final OvrsPymnSumrRepository    sumrRepository;
    private final OvrsPymnDtlsRepository    dtlsRepository;
    private final OvrsPymnMstrRepository    ovrsPymnMstrRepository;
    private final ExptResolMstrRepository   exptResolMstrRepository;
    /** Per-line dispatch-result resolver — supplies PROC_STAT / RS_CD for the
     *  Status column (E-mode). Returns {@link DispatchResult#EMPTY} when the
     *  dispatch tables are unreachable (pre-dispatch / fresh DB). */
    private final DispatchResultResolver    dispatchResultResolver;
    /** Shared two-step bank-name resolver (BANK_INFM → NAPAS/CITAD function →
     *  CITAD canonical fallback) for the Withdrawal Bank column — mirrors the
     *  legacy {@code wb_get_bank_name_any_code(OUT_BANK_CD)}. */
    private final TransferLineDetailMapper  transferLineDetailMapper;
    /** M-mode (OVRS_PYMN_MSTR) detail-row reader — reused from the list-export
     *  path so the manual-payment branch stays consistent with the tested query. */
    private final OverseasPaymentRepository paymentRepo;

    /** Generated report payload — workbook bytes + the download filename. */
    public record GeneratedReport(byte[] content, String fileName) { }

    /** One populated data row (raw values; localization happens in populate). */
    private record Line(
            String transactionSeq, String procStat, String rsCd,
            String bnfcNm, String bnfcBankNm, String bic, String bnfcAcctNo,
            String bnfcAddr, String ccyCd, BigDecimal amt, String feePayerCd,
            String reportingAcct, String memo) { }

    /** Per-batch header fields repeated on every detail row of the batch. */
    private record BlockHeader(
            boolean dispatched, String trnfDtm, String regDtm, String fileNm,
            String outBankNm, String wdrlActNo, String requestor, String scheduledDate,
            String approvalStatus, String approvalRef, String approvalTitle) { }

    /** One batch's header + detail lines + its single-currency total. */
    private record BatchData(BlockHeader hdr, List<Line> lines, String totalCcy, BigDecimal totalAmt) { }

    /**
     * Generate the 22-column Overseas Payment download report for one batch.
     *
     * @param ovrsPymnNo the batch key ({@code OVRS_PYMN_NO})
     * @param langParam  optional UI-locale hint (e.g. {@code "KO"}); when blank
     *                   the JWT {@code langCd} is used, then EN
     * @throws BusinessException {@code WBE2045} when the batch is missing or not
     *         in the caller's tenant (matches the overseas {@code getDetail} miss)
     */
    @Transactional(readOnly = true)
    public GeneratedReport generate(String ovrsPymnNo, String langParam) {
        String bizKey = requireTenant();
        String lang   = resolveLang(langParam);

        BatchData bd = loadBatch(bizKey, lang, ovrsPymnNo, true);
        // Single-batch total cell = single-currency (legacy createRow(2) parity).
        String totalLabel = "Total Amount (" + trim(bd.totalCcy()) + ") : "
                + formatTotal(bd.totalAmt(), bd.totalCcy());
        byte[] bytes = render(lang, List.of(bd), totalLabel);

        String fileName = "WeCMS_Overseas_Payment_download_" + lang + ".xlsx";
        log.info("feature=overseas-payment report-download bizKey={} ovrsPymnNo={} lang={}",
                bizKey, ovrsPymnNo, lang);
        return new GeneratedReport(bytes, fileName);
    }

    /**
     * Bulk variant — the Payment Result toolbar Excel. ONE workbook from the
     * same locale template as the per-row report, with every listed batch's
     * detail lines appended into the single sheet (legacy bulk-report parity —
     * same 22-column layout, E+M union, all filtered rows). The A3:T3 total cell
     * shows the per-currency grand total across the whole export (the template's
     * own multi-currency placeholder shape). Unknown / cross-tenant ids are
     * skipped rather than aborting the export.
     *
     * @param ovrsPymnNos ordered batch keys (the filtered result list)
     * @param langParam   optional UI-locale hint (EN/KO/VI; KM/other → EN)
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateBulk(List<String> ovrsPymnNos, String langParam) {
        String bizKey = requireTenant();
        String lang   = resolveLang(langParam);
        List<String> ids = ovrsPymnNos == null ? List.of() : ovrsPymnNos;

        List<BatchData> blocks = new ArrayList<>(ids.size());
        for (String id : ids) {
            BatchData bd = loadBatch(bizKey, lang, id, false);
            if (bd != null) blocks.add(bd);
        }

        byte[] bytes = render(lang, blocks, buildMultiCurrencyTotalLabel(blocks));
        String fileName = "WeCMS_Overseas_Payment_download_" + lang + ".xlsx";
        log.info("feature=overseas-payment bulk-report-download bizKey={} batches={} lang={}",
                bizKey, blocks.size(), lang);
        return new GeneratedReport(bytes, fileName);
    }

    /**
     * Resolve one batch into a {@link BatchData} (E-mode {@code OVRS_PYMN_SUMR}
     * first, else M-mode {@code OVRS_PYMN_MSTR}). When {@code throwIfMissing} is
     * true (single-batch path) a missing batch throws {@code WBE2045}; when false
     * (bulk path) it returns {@code null} so the caller skips it.
     */
    private BatchData loadBatch(String bizKey, String lang, String ovrsPymnNo, boolean throwIfMissing) {
        Optional<OvrsPymnSumr> sumrOpt = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo);
        if (sumrOpt.isPresent()) {
            return loadEMode(bizKey, lang, sumrOpt.get());
        }
        Optional<OvrsPymnMstr> mstrOpt = ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo);
        if (mstrOpt.isPresent()) {
            return loadMMode(bizKey, lang, mstrOpt.get());
        }
        if (throwIfMissing) throw new BusinessException("WBE2045");
        return null;
    }

    /* ----------------------------------------------------------------
     *  E-mode (Excel batch) — OVRS_PYMN_SUMR + OVRS_PYMN_SUMR_DETL
     * ---------------------------------------------------------------- */

    private BatchData loadEMode(String bizKey, String lang, OvrsPymnSumr master) {
        List<OvrsPymnSumrDetl> details = dtlsRepository
                .findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, master.getOvrsPymnNo());

        Optional<ExptResolMstr> approval = loadApproval(bizKey, master.getApprSeqNo());
        Map<String, DispatchResult> resultByDetlNo =
                dispatchResultResolver.resolveOverseas(master, details);

        boolean dispatched = "Y".equalsIgnoreCase(trim(master.getTrnfYn()));

        List<Line> lines = new ArrayList<>(details.size());
        for (OvrsPymnSumrDetl d : details) {
            DispatchResult r = resultByDetlNo.getOrDefault(d.getOvrsPymnDetlNo(), DispatchResult.EMPTY);
            lines.add(new Line(
                    d.getPayTranSeq(),                 // B — Transaction Seq (PAY_TRAN_SEQ → PAY_FILE_SEQ_NO)
                    r.procStat(), r.rsCd(),            // C — Status (derived per-line)
                    d.getBnfcNm(),                     // H — Beneficiary
                    d.getBnfcBankNm(),                 // I — Beneficiary Bank Name
                    d.getBnfcBankCd(),                 // J — Bank Identifier Code (legacy puts BNFC_BANK_CD here)
                    d.getBnfcAcctNo(),                 // K — Beneficiary Account Number
                    d.getBnfcAddr(),                   // L — Beneficiary Address
                    d.getCcyCd(),                      // M — Transfer Currency
                    d.getTrnfAmt(),                    // N — Transfer Amount
                    d.getFeePayerCd(),                 // O — Fee Payer
                    d.getRprtAcctCd(),                 // P — Reporting Account (CHART_ACCT_CD)
                    d.getMemo()));                     // Q — Memo
        }

        BlockHeader hdr = buildHeader(lang, dispatched, master.getTrnfDt(), master.getRegDtm(),
                master.getTitl(), resolveBankNm(master.getOutBankCd()), master.getWdrlActNo(), approval);
        return new BatchData(hdr, lines, master.getCcyCd(), master.getTotalTranAmt());
    }

    /* ----------------------------------------------------------------
     *  M-mode (manual / individual) — OVRS_PYMN_MSTR
     * ---------------------------------------------------------------- */

    private BatchData loadMMode(String bizKey, String lang, OvrsPymnMstr master) {
        Optional<ExptResolMstr> approval = loadApproval(bizKey, master.getApprSeqNo());
        boolean dispatched = "Y".equalsIgnoreCase(trim(master.getTrnfYn()));

        // M-mode detail rows come from the shared (tested) list-export query so
        // the manual branch stays consistent with OverseasPaymentResultExcelWriter.
        // Index layout mirrors OverseasPaymentService.mapMmodeDetailRow.
        List<Line> lines = new ArrayList<>();
        for (Object[] m : paymentRepo.findMmodeDetailRows(bizKey, master.getOvrsPymnNo())) {
            lines.add(new Line(
                    null,                              // B — M-mode has no PAY_FILE_SEQ_NO
                    toStr(m[13]), toStr(m[15]),        // C — Status (PROC_STAT / RS_CD)
                    toStr(m[7]),                       // H — Beneficiary
                    toStr(m[8]),                       // I — Beneficiary Bank Name
                    toStr(m[9]),                       // J — BIC (M branch carries BNFC_BANK_BIC here)
                    toStr(m[10]),                      // K — Beneficiary Account Number
                    toStr(m[11]),                      // L — Beneficiary Address
                    toStr(m[2]),                       // M — Transfer Currency
                    toBigDecimal(m[3]),                // N — Transfer Amount
                    toStr(m[4]),                       // O — Fee Payer
                    toStr(m[6]),                       // P — Reporting Account (CHART_ACCT_CD)
                    toStr(m[5])));                     // Q — Memo
        }

        // Withdrawal account for M is the debit-account fields (R006 M branch).
        // OVRS_PYMN_MSTR.TRNF_DT is not mapped on the entity → col A (Transfer
        // Date Time) is left blank for M-mode (it is blank pre-dispatch anyway).
        BlockHeader hdr = buildHeader(lang, dispatched, null, master.getRegDtm(),
                // FILE_NM for M = "<BNFC_NM> <BNFC_ACCT_NO>" (legacy R009 M branch).
                joinNonBlank(master.getBnfcNm(), master.getBnfcAcctNo()),
                resolveBankNm(master.getDbtAcctCd()), master.getDbtAcctNo(), approval);
        return new BatchData(hdr, lines, master.getTrnfCcyCd(), master.getTotalTranAmt());
    }

    private BlockHeader buildHeader(
            String lang, boolean dispatched, String trnfDtm, String regDtm, String fileNm,
            String outBankNm, String wdrlActNo, Optional<ExptResolMstr> approval) {
        String requestor = approval.map(ExptResolMstr::getDraftUserNm)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        String scheduledDate  = approval.map(ExptResolMstr::getPayDate).orElse(null);
        String approvalStatus = getLocalizedText(
                approvalStatusLabel(approval.map(ExptResolMstr::getApprTypeCd).orElse(null)), lang);
        String approvalRef    = approval.map(ExptResolMstr::getDocNo).orElse(null);
        String approvalTitle  = approval.map(ExptResolMstr::getTitl).orElse(null);
        return new BlockHeader(dispatched, trnfDtm, regDtm, fileNm, outBankNm, wdrlActNo,
                requestor, scheduledDate, approvalStatus, approvalRef, approvalTitle);
    }

    /* ----------------------------------------------------------------
     *  Template render (shared by single + bulk)
     * ---------------------------------------------------------------- */

    /**
     * Load the locale template, write the {@code A3:T3} total label, then append
     * every block's detail lines from the first data row, preserving template
     * styles. Shared by {@link #generate} (one block, single-currency total) and
     * {@link #generateBulk} (N blocks, per-currency grand total).
     */
    private byte[] render(String lang, List<BatchData> blocks, String totalLabel) {
        String resourcePath = TEMPLATE_DIR + "WeCMS_Overseas_Payment_download_" + lang + ".xlsx";
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);

            // A3:T3 — overwrite the 3-currency template placeholder with the
            // computed total (legacy createRow(2) + setCellValue), reusing the
            // template cell so its style survives.
            Row totalRow = rowAt(sheet, TOTAL_ROW_INDEX);
            Cell totalCell = totalRow.getCell(0);
            if (totalCell == null) totalCell = totalRow.createCell(0);
            totalCell.setCellValue(totalLabel);

            // Capture the per-column template style from the first pre-styled
            // data row (index 4) so written cells keep the template's borders /
            // alignment / number+date formats (legacy parity).
            CellStyle[] colStyle = new CellStyle[COLUMN_COUNT];
            Row templateRow = sheet.getRow(FIRST_DATA_ROW_INDEX);
            if (templateRow != null) {
                for (int c = 0; c < COLUMN_COUNT; c++) {
                    Cell cell = templateRow.getCell(c);
                    if (cell != null) colStyle[c] = cell.getCellStyle();
                }
            }

            // Currency-aware amount styles, cloned from the template amount-column
            // style so font/border/alignment are preserved, with only the data
            // format overridden (legacy getSubCellStyle: #,##0 VND / #,##0.00 else).
            DataFormat fmt = wb.createDataFormat();
            CellStyle amtVnd = wb.createCellStyle();
            CellStyle amtOther = wb.createCellStyle();
            if (colStyle[AMOUNT_COL_INDEX] != null) {
                amtVnd.cloneStyleFrom(colStyle[AMOUNT_COL_INDEX]);
                amtOther.cloneStyleFrom(colStyle[AMOUNT_COL_INDEX]);
            }
            amtVnd.setDataFormat(fmt.getFormat("#,##0;-#,##0"));
            amtOther.setDataFormat(fmt.getFormat("#,##0.00;-#,##0.00"));

            int rowIdx = FIRST_DATA_ROW_INDEX;
            for (BatchData bd : blocks) {
                rowIdx = writeBlockLines(sheet, colStyle, amtVnd, amtOther, lang, rowIdx, bd.hdr(), bd.lines());
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to generate Overseas Payment download report (lang=" + lang + ")", e);
        }
    }

    /** Write one batch's detail lines into the sheet from {@code startIdx},
     *  repeating the batch header fields on every row. Returns the next index. */
    private int writeBlockLines(Sheet sheet, CellStyle[] colStyle, CellStyle amtVnd,
                                CellStyle amtOther, String lang, int startIdx,
                                BlockHeader hdr, List<Line> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Line ln = lines.get(i);
            Row row = rowAt(sheet, startIdx + i);

            // A–V (0..21) — identical order across EN/KO/VI templates.
            setText  (row,  0, colStyle, hdr.dispatched()
                    ? OverseasPaymentResultExcelWriter.formatDtm(hdr.trnfDtm()) : "");
            setText  (row,  1, colStyle, ln.transactionSeq());
            setText  (row,  2, colStyle, getLocalizedText(
                    deriveStatusEn(hdr.dispatched(), ln.procStat(), ln.rsCd()), lang));
            setText  (row,  3, colStyle, OverseasPaymentResultExcelWriter.formatDtm(hdr.regDtm()));
            setText  (row,  4, colStyle, hdr.fileNm());
            setText  (row,  5, colStyle, hdr.outBankNm());
            setText  (row,  6, colStyle, hdr.wdrlActNo());
            setText  (row,  7, colStyle, ln.bnfcNm());
            setText  (row,  8, colStyle, ln.bnfcBankNm());
            setText  (row,  9, colStyle, ln.bic());
            setText  (row, 10, colStyle, ln.bnfcAcctNo());
            setText  (row, 11, colStyle, ln.bnfcAddr());
            setText  (row, 12, colStyle, ln.ccyCd());
            setAmount(row, 13, ln.amt(), isVnd(ln.ccyCd()) ? amtVnd : amtOther);
            setText  (row, 14, colStyle, getLocalizedText(feePayerLabel(ln.feePayerCd()), lang));
            setText  (row, 15, colStyle, ln.reportingAcct());
            setText  (row, 16, colStyle, ln.memo());
            setText  (row, 17, colStyle, hdr.requestor());
            setText  (row, 18, colStyle, OverseasPaymentResultExcelWriter.formatDate(hdr.scheduledDate()));
            setText  (row, 19, colStyle, hdr.approvalStatus());
            setText  (row, 20, colStyle, hdr.approvalRef());
            setText  (row, 21, colStyle, hdr.approvalTitle());
        }
        return startIdx + lines.size();
    }

    /**
     * Per-currency grand-total label for the bulk export — sums every block's
     * single-currency total grouped by currency, in the template's
     * {@code "Total Amount (CCY) : amt …"} shape. Insertion order preserved.
     */
    private static String buildMultiCurrencyTotalLabel(List<BatchData> blocks) {
        java.util.Map<String, BigDecimal> byCcy = new java.util.LinkedHashMap<>();
        for (BatchData bd : blocks) {
            String ccy = trim(bd.totalCcy());
            BigDecimal amt = bd.totalAmt() != null ? bd.totalAmt() : BigDecimal.ZERO;
            byCcy.merge(ccy, amt, BigDecimal::add);
        }
        if (byCcy.isEmpty()) return "Total Amount";
        StringBuilder sb = new StringBuilder("Total Amount");
        for (Map.Entry<String, BigDecimal> e : byCcy.entrySet()) {
            sb.append(" (").append(e.getKey()).append(") : ").append(formatTotal(e.getValue(), e.getKey()));
        }
        return sb.toString();
    }

    /* ----------------------------------------------------------------
     *  Helpers
     * ---------------------------------------------------------------- */

    private Optional<ExptResolMstr> loadApproval(String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) return Optional.empty();
        return exptResolMstrRepository.findById(new ExptResolMstrId(bizKey, apprSeqNo));
    }

    private String resolveBankNm(String code) {
        String key = code == null ? "" : code.trim();
        if (key.isEmpty()) return null;
        return transferLineDetailMapper.resolveBankNm(key);
    }

    /**
     * Resolve the template locale. Priority: explicit {@code langParam} (the UI
     * locale the frontend passes) → JWT {@code langCd} → EN. Only EN / KO / VI
     * have a template; KM and anything else fall back to EN (legacy
     * {@code LocaleHelper} parity — KM has no template file).
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

    /**
     * Per-line Status EN label (col C). Mirrors legacy
     * {@code pymn_0008_002_r002_view.jsp:283-301} — blank when the batch is not
     * dispatched, otherwise derived from {@code PROC_STAT} + {@code RS_CD}.
     */
    static String deriveStatusEn(boolean dispatched, String procStat, String rsCd) {
        if (!dispatched) return "";
        String ps = trim(procStat);
        String rs = trim(rsCd);
        if ("0".equals(ps) && "0000".equals(rs))               return "Success";
        if ("3".equals(ps))                                    return "Failed";
        if (("9".equals(ps) || "1".equals(ps) || "6".equals(ps)) && rs.isEmpty()) return "In Progress";
        if (!rs.isEmpty() && !"0000".equals(rs))               return "Failed";
        return "";
    }

    /** Fee Payer EN label (col O) — legacy {@code pymn_0008_002_r002_view.jsp:304-314}. */
    static String feePayerLabel(String code) {
        return switch (trim(code)) {
            case "B" -> "Beneficiary Charge";
            case "O" -> "Ordering Charge";
            case "S" -> "Shared";
            default  -> "";
        };
    }

    /**
     * Approval Status EN label (col T) from {@code EXPT_RESOL_MSTR.APPR_TYPE_CD}.
     * {@code 03 → "In Progress"} (legacy {@code APPR_STTS='I'} parity), NOT
     * "Cancelled". Matches {@link InternalTransferDownloadService#approvalStatusLabel}.
     */
    static String approvalStatusLabel(String apprTypeCd) {
        return switch (trim(apprTypeCd)) {
            case "00" -> "Pending";
            case "01" -> "Approved";
            case "02" -> "Rejected";
            case "03" -> "In Progress";
            default   -> "";
        };
    }

    /**
     * Localized label map — copied verbatim from legacy
     * {@code pymn_0008_002_r002_view.jsp getLocalizedText} (KO/VI strings are
     * the verified JEX-source translations; EN is the default / fallback). Do
     * not invent translations — any value not in the legacy map falls back to EN.
     */
    static String getLocalizedText(String text, String lang) {
        if (text == null || text.isEmpty()) return "";
        if ("KO".equals(lang)) {
            return switch (text) {
                case "Success"            -> "성공";
                case "Failed"             -> "실패";
                case "In Progress"        -> "진행중";
                case "Pending"            -> "대기중";
                case "Approved"           -> "승인";
                case "Rejected"           -> "반려";
                case "Beneficiary Charge" -> "수취인 부담";
                case "Ordering Charge"    -> "송금인 부담";
                case "Shared"             -> "분할 부담";
                default                   -> text;
            };
        } else if ("VI".equals(lang)) {
            return switch (text) {
                case "Success"            -> "Đã thành công";
                case "Failed"             -> "Đã thất bại";
                case "In Progress"        -> "Đang xử lý";
                case "Pending"            -> "Chờ phê duyệt";
                case "Approved"           -> "Đã phê duyệt";
                case "Rejected"           -> "Đã từ chối";
                case "Beneficiary Charge" -> "Người thụ hưởng chịu phí";
                case "Ordering Charge"    -> "Người gửi chịu phí";
                case "Shared"             -> "Chia sẻ";
                default                   -> text;
            };
        }
        return text; // EN (and KM → EN fallback)
    }

    /** Single-currency total label value — VND has no decimals, others 2 dp. */
    private static String formatTotal(BigDecimal amt, String ccy) {
        BigDecimal v = amt != null ? amt : BigDecimal.ZERO;
        DecimalFormat df = isVnd(ccy) ? new DecimalFormat("#,##0") : new DecimalFormat("#,##0.00");
        return df.format(v);
    }

    private static boolean isVnd(String ccy) {
        return "VND".equalsIgnoreCase(trim(ccy)) || trim(ccy).isEmpty();
    }

    private static Row rowAt(Sheet sheet, int idx) {
        Row row = sheet.getRow(idx);
        return row != null ? row : sheet.createRow(idx);
    }

    private static void setText(Row row, int col, CellStyle[] colStyle, String value) {
        Cell cell = cellAt(row, col, colStyle);
        cell.setCellValue(value == null ? "" : value);
    }

    private static void setAmount(Row row, int col, BigDecimal value, CellStyle amountStyle) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellStyle(amountStyle);
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
            if (colStyle != null && colStyle[col] != null) {
                cell.setCellStyle(colStyle[col]);
            }
        }
        return cell;
    }

    private static String joinNonBlank(String a, String b) {
        String x = trim(a);
        String y = trim(b);
        if (x.isEmpty()) return y;
        if (y.isEmpty()) return x;
        return x + " " + y;
    }

    private static String toStr(Object o) {
        return o == null ? "" : o.toString();
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
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
