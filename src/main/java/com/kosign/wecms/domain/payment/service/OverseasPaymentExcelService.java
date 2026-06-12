package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.tx.TransactionalRetry;
import org.springframework.dao.DataIntegrityViolationException;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelFieldError;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelPreview;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelPreviewRow;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelUploadRequest;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelUploadResult;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.service.OverseasPaymentFileResolver;
import com.kosign.wecms.domain.payment.overseas.service.OverseasPaymentKeyGenerator;
import com.kosign.wecms.domain.payment.validation.FieldCd;
import com.kosign.wecms.domain.payment.validation.PymnType;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoOvrsInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoOvrsInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses and validates an overseas payment Excel upload file.
 *
 * <p>Mirrors the legacy {@code pymn_0007_001} batch-upload flow.
 * The template has a 3-row preamble (title, spacer, headers) followed
 * by data rows. Column order matches the download template:
 * <ol>
 *   <li>Beneficiary Code</li>
 *   <li>Transfer Currency</li>
 *   <li>Transfer Amount</li>
 *   <li>Fee Payer</li>
 *   <li>Memo</li>
 *   <li>Reporting Account (optional)</li>
 * </ol>
 *
 * <p>Beneficiary detail fields (name, bank, BIC, account, address) are
 * NOT in the Excel file — they are resolved client-side from the
 * contacts endpoint using {@code beneficiary_code}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasPaymentExcelService {

    private static final ZoneId        VIETNAM_ZONE   = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM_FMT    = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OvrsPymnSumrRepository       sumrRepository;
    private final OvrsPymnDtlsRepository       dtlsRepository;
    private final OverseasPaymentKeyGenerator  keyGenerator;
    private final OverseasPaymentFileResolver  fileResolver;
    private final TrcoInfmRepository           contactRepo;
    private final TrcoOvrsInfmRepository       overseasContactRepo;
    private final SpecialCharValidator         specialCharValidator;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;
    private final TransactionalRetry           transactionalRetry;

    /** Header row index (0-based POI index; row 3 in Excel = index 2). */
    private static final int HEADER_ROW_INDEX   = 2;
    /** First data row index (0-based). */
    private static final int FIRST_DATA_ROW_INDEX = 3;
    /** Minimum expected columns in the data area. */
    private static final int MIN_COLUMNS        = 6;
    /** Maximum data rows accepted in a single upload. */
    private static final int MAX_ROWS           = 500;

    /* Column indices (0-based) — matches WeCMS_Overseas_Payment_upload template. */
    private static final int COL_BENEFICIARY_CODE   = 0;
    private static final int COL_CURRENCY           = 1;
    private static final int COL_AMOUNT             = 2;
    private static final int COL_FEE_PAYER          = 3;
    private static final int COL_MEMO               = 4;
    private static final int COL_REPORTING_ACCOUNT  = 5;

    /**
     * Parse and validate an overseas payment Excel file.
     *
     * @param file the uploaded multipart file
     * @return preview with all rows (valid + invalid) and aggregate counts
     * @throws BusinessException {@code WBE2041} when row count exceeds {@link #MAX_ROWS}
     * @throws BusinessException {@code WBE2042} when the header row is missing or malformed
     * @throws BusinessException {@code WBE2043} on a corrupt / unreadable workbook
     */
    public OverseasPaymentExcelPreview preview(MultipartFile file, String bankCd) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION", "file: required");
        }

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet);

            List<OverseasPaymentExcelPreviewRow> rows = new ArrayList<>();
            int completed = 0;
            int errored   = 0;

            // rowIterator() yields only rows that physically exist in the file,
            // so pre-formatted empty rows in the template are never visited.
            java.util.Iterator<Row> rowIter = sheet.rowIterator();
            while (rowIter.hasNext()) {
                Row row = rowIter.next();
                int r = row.getRowNum();
                if (r < FIRST_DATA_ROW_INDEX) continue;   // skip preamble rows
                if (isRowEmpty(row)) continue;
                if (rows.size() >= MAX_ROWS) {
                    throw new BusinessException("WBE2041", String.valueOf(MAX_ROWS));
                }

                String currency          = readCell(row, COL_CURRENCY);
                String amount            = readCell(row, COL_AMOUNT);
                String feePayer          = readCell(row, COL_FEE_PAYER);
                String beneficiaryCode   = readCell(row, COL_BENEFICIARY_CODE);
                String memo              = readCell(row, COL_MEMO);
                String reportingAccount  = readCell(row, COL_REPORTING_ACCOUNT);

                List<OverseasPaymentExcelFieldError> errors = new ArrayList<>();
                validateRequired(currency,        "currency",        errors);
                // Legacy parity: currency must be exactly 3 chars
                // (pymn_0007_002_r001_act.jsp:286-303).
                if (currency != null && !currency.isBlank() && currency.trim().length() != 3) {
                    errors.add(new OverseasPaymentExcelFieldError(
                            "currency", "currency must be 3 characters"));
                }
                validateAmount(amount,                               errors);
                validateRequired(feePayer,         "feePayer",        errors);
                validateRequired(beneficiaryCode,  "beneficiaryCode", errors);
                validateRequired(memo,             "memo",            errors);
                // Legacy parity: memo capped at 200 chars
                // (pymn_0007_002_r001_act.jsp:322 — checkValidStringInMax).
                if (memo != null && memo.length() > 200) {
                    errors.add(new OverseasPaymentExcelFieldError(
                            "memo", "memo must be 200 characters or less"));
                }

                // Resolve beneficiary detail server-side from
                // TRCO_INFM + TRCO_OVRS_INFM. Mirrors legacy
                // pymn_0007_003_r001_act.jsp#getOverseasContactInfo
                // (lines 125-150) + getOverseasContactDetails (152-238).
                ResolvedBeneficiary bnfc = resolveBeneficiary(beneficiaryCode, errors);

                // Special-character checks against BLOCKED_CHARS_RULE.
                // Mirrors legacy pymn_0007_003_r001_act.jsp:
                //   :175 — BENEFICIARY_NAME (cross-filled BNFC_NM)
                //   :197 — BENEFICIARY_ADDRESS (cross-filled BNFC_ADDR)
                //   :372 — MEMO (Excel cell, col 4)
                // Bank-specific — missing rule for the (bankCd,
                // OVERSEAS_PAYMENT, fieldCd) tuple means the call is a
                // no-op, so this is safe before BLOCKED_CHARS_RULE is
                // seeded for Overseas.
                if (bankCd != null && !bankCd.isBlank()) {
                    if (bnfc.bnfcNm() != null) {
                        specialCharValidator
                                .validate(bankCd, PymnType.OVERSEAS_PAYMENT, FieldCd.BENEFICIARY_NAME, bnfc.bnfcNm())
                                .ifPresent(msg -> errors.add(new OverseasPaymentExcelFieldError("bnfcNm", msg)));
                    }
                    if (bnfc.bnfcAddr() != null) {
                        specialCharValidator
                                .validate(bankCd, PymnType.OVERSEAS_PAYMENT, FieldCd.BENEFICIARY_ADDRESS, bnfc.bnfcAddr())
                                .ifPresent(msg -> errors.add(new OverseasPaymentExcelFieldError("bnfcAddr", msg)));
                    }
                    if (memo != null && !memo.isBlank()) {
                        specialCharValidator
                                .validate(bankCd, PymnType.OVERSEAS_PAYMENT, FieldCd.MEMO, memo)
                                .ifPresent(msg -> errors.add(new OverseasPaymentExcelFieldError("memo", msg)));
                    }
                }

                boolean valid = errors.isEmpty();
                if (valid) completed++; else errored++;

                // POI row index is 0-based; add 1 so it matches what the
                // user sees in Excel (header is row 3, first data is row 4
                // from the user's perspective — but we report 1-based from
                // data start to match the legacy preview UI).
                rows.add(new OverseasPaymentExcelPreviewRow(
                        r + 1,
                        currency, amount, feePayer,
                        beneficiaryCode, memo, reportingAccount,
                        bnfc.bnfcNm(), bnfc.bicCode(), bnfc.bnfcAcctNo(),
                        bnfc.bnfcAddr(), bnfc.bnfcBankNm(), bnfc.bnfcBankCd(),
                        valid, errors));
            }

            log.info("overseas-payment excel preview: total={} completed={} errored={}",
                    rows.size(), completed, errored);
            return new OverseasPaymentExcelPreview(rows, rows.size(), completed, errored);

        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("overseas-payment excel preview failed for file={}",
                    file.getOriginalFilename(), ex);
            throw new BusinessException("WBE2043");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header == null || header.getLastCellNum() < MIN_COLUMNS) {
            throw new BusinessException("WBE2042",
                    "Overseas payment template must have at least " + MIN_COLUMNS + " columns");
        }
    }

    private void validateRequired(String value, String fieldId,
                                   List<OverseasPaymentExcelFieldError> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new OverseasPaymentExcelFieldError(fieldId, fieldId + " is required"));
        }
    }

    /**
     * Backend resolver for the overseas beneficiary detail bundle.
     * Three outcomes:
     * <ul>
     *   <li><b>Blank beneficiary code</b> — already flagged by
     *       {@link #validateRequired}; returns empty so the preview
     *       carries nulls without piling on more errors.</li>
     *   <li><b>Code does not resolve</b> to an overseas
     *       ({@code TRCO_TYPE='O'}) contact for the tenant — emits
     *       one error on {@code beneficiaryCode} (legacy {@code WBE1189})
     *       plus per-field errors on every beneficiary field so the
     *       preview highlights every cell (legacy
     *       {@code pymn_0007_003_r001_act.jsp:142-148}).</li>
     *   <li><b>Code resolves</b> — cross-fills BNFC_NM, BIC_CODE,
     *       BNFC_ACCT_NO, BNFC_ADDR, BNFC_BANK_NM, BNFC_BANK_CD from
     *       {@code TRCO_OVRS_INFM}. Per-field error emitted for any
     *       column that's blank in the master row (legacy
     *       {@code WBW1159} at lines 218-232).</li>
     * </ul>
     */
    private ResolvedBeneficiary resolveBeneficiary(
            String beneficiaryCode,
            List<OverseasPaymentExcelFieldError> errors) {
        if (beneficiaryCode == null || beneficiaryCode.isBlank()) {
            return ResolvedBeneficiary.EMPTY;
        }

        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            errors.add(new OverseasPaymentExcelFieldError(
                    "beneficiaryCode", "Tenant context missing — please re-login"));
            return ResolvedBeneficiary.EMPTY;
        }

        Optional<TrcoInfm> contactHit = contactRepo
                .findActiveOverseasByBizKeyAndHandTrcoKey(bizKey, beneficiaryCode.trim());
        if (contactHit.isEmpty()) {
            errors.add(new OverseasPaymentExcelFieldError(
                    "beneficiaryCode",
                    "Beneficiary code is not registered: " + beneficiaryCode));
            errors.add(new OverseasPaymentExcelFieldError("bnfcNm",      "Beneficiary Name is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bicCode",     "BIC Code is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bnfcAcctNo",  "Beneficiary Account No. is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bnfcAddr",    "Beneficiary Address is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bnfcBankNm",  "Beneficiary Bank Name is missing"));
            return ResolvedBeneficiary.EMPTY;
        }

        List<TrcoOvrsInfm> ovrsRows = overseasContactRepo
                .findByBizKeyAndTrcoKey(bizKey, contactHit.get().getTrcoKey());
        if (ovrsRows.isEmpty()) {
            // Contact exists but has no overseas-detail row at all.
            errors.add(new OverseasPaymentExcelFieldError("bnfcNm",      "Beneficiary Name is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bicCode",     "BIC Code is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bnfcAcctNo",  "Beneficiary Account No. is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bnfcAddr",    "Beneficiary Address is missing"));
            errors.add(new OverseasPaymentExcelFieldError("bnfcBankNm",  "Beneficiary Bank Name is missing"));
            return ResolvedBeneficiary.EMPTY;
        }

        TrcoOvrsInfm o = ovrsRows.get(0);
        String bnfcNm     = trimToNull(o.getBeneName());
        String bicCode    = trimToNull(stripWhitespace(o.getBicCode()));
        String bnfcAcctNo = trimToNull(stripWhitespace(o.getBeneAcctNo()));
        String bnfcAddr   = trimToNull(o.getBeneAddr());
        String bnfcBankNm = trimToNull(o.getBeneBankName());
        String bnfcBankCd = trimToNull(stripWhitespace(o.getBeneBankCode()));

        if (bnfcNm     == null) errors.add(new OverseasPaymentExcelFieldError("bnfcNm",      "Beneficiary Name is missing"));
        if (bicCode    == null) errors.add(new OverseasPaymentExcelFieldError("bicCode",     "BIC Code is missing"));
        if (bnfcAcctNo == null) errors.add(new OverseasPaymentExcelFieldError("bnfcAcctNo",  "Beneficiary Account No. is missing"));
        if (bnfcAddr   == null) errors.add(new OverseasPaymentExcelFieldError("bnfcAddr",    "Beneficiary Address is missing"));
        if (bnfcBankNm == null) errors.add(new OverseasPaymentExcelFieldError("bnfcBankNm",  "Beneficiary Bank Name is missing"));

        return new ResolvedBeneficiary(bnfcNm, bicCode, bnfcAcctNo, bnfcAddr, bnfcBankNm, bnfcBankCd);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String stripWhitespace(String s) {
        return s == null ? null : s.replaceAll("\\s+", "");
    }

    /**
     * Six-field beneficiary projection used by {@link #resolveBeneficiary}.
     * Component values are {@code null} when blank in the master row
     * (the corresponding error has already landed in the row's
     * {@code errors} list).
     */
    private record ResolvedBeneficiary(
            String bnfcNm,
            String bicCode,
            String bnfcAcctNo,
            String bnfcAddr,
            String bnfcBankNm,
            String bnfcBankCd) {
        static final ResolvedBeneficiary EMPTY =
                new ResolvedBeneficiary(null, null, null, null, null, null);
    }

    private void validateAmount(String raw, List<OverseasPaymentExcelFieldError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new OverseasPaymentExcelFieldError("amount", "amount is required"));
            return;
        }
        try {
            BigDecimal val = new BigDecimal(raw.replace(",", ""));
            if (val.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new OverseasPaymentExcelFieldError("amount", "amount must be greater than zero"));
            }
        } catch (NumberFormatException ex) {
            errors.add(new OverseasPaymentExcelFieldError("amount", "amount must be a valid number"));
        }
    }

    /** Read a cell value as a trimmed string. Numeric cells are
     *  converted to plain decimal text (no trailing .0 for integers). */
    private String readCell(Row row, int col) {
        if (row == null) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d) && !Double.isInfinite(d))
                        ? String.valueOf((long) d)
                        : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception ex) {
                    yield cell.getStringCellValue().trim();
                }
            }
            default -> "";
        };
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    /**
     * Persist the overseas-payment Excel batch into {@code OVRS_PYMN_SUMR}
     * (one summary row) and {@code OVRS_PYMN_DTLS} (one row per transaction),
     * then return the allocated {@code OVRS_PYMN_NO}.
     *
     * <p>The physical Excel file is saved under the configured
     * {@code wecms.overseas-payment.storage-root} so the Scheduled
     * Payment detail page can stream it back via the download endpoint.
     *
     * @param req  batch metadata + full list of parsed transaction rows
     * @param file the original uploaded Excel file (may be {@code null}
     *             for legacy callers that do not supply the physical file)
     * @return the new batch key wrapped in {@link OverseasPaymentExcelUploadResult}
     */
    public OverseasPaymentExcelUploadResult submit(OverseasPaymentExcelUploadRequest req,
                                                    MultipartFile file) {
        // One-shot retry on the OVRS_PYMN_NO MAX+1 race (wb_get_next_val has no
        // row lock — V140 #63). Each attempt runs in its own transaction via
        // TransactionalRetry.runOnce, so a failed attempt fully rolls back and the
        // retry re-allocates the key in a fresh Hibernate session. The summary row is
        // flushed before the physical file write and the detail INSERTs (see doSubmit),
        // so a failed attempt never writes a file or detail row to duplicate. Same
        // stance as the four SCDUL_PYMN create services.
        try {
            return transactionalRetry.runOnce(() -> doSubmit(req, file));
        } catch (DataIntegrityViolationException firstRace) {
            log.warn("feature=overseas-payment ovrs_pymn_no insert race — retrying once");
            try {
                return transactionalRetry.runOnce(() -> doSubmit(req, file));
            } catch (DataIntegrityViolationException secondRace) {
                log.warn("feature=overseas-payment ovrs_pymn_no insert race retry failed", secondRace);
                throw new BusinessException("WBE_SCHEDULED_PAYMENT_KEY_RACE");
            }
        }
    }

    /**
     * Transactional body for {@link #submit}. Not separately {@code @Transactional} —
     * it executes inside the {@code REQUIRES_NEW} transaction that
     * {@link TransactionalRetry#runOnce} opens, so the one-shot retry can cross a
     * clean transaction boundary on a key-allocation race.
     */
    OverseasPaymentExcelUploadResult doSubmit(OverseasPaymentExcelUploadRequest req,
                                              MultipartFile file) {
        String bizKey    = TenantContext.currentBizKey();
        String userId    = TenantContext.currentUserId();
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String nowDtm    = now.format(DTM_FMT);

        // Legacy parity: no same-day filename guard. Legacy JEX
        // (pymn_0007_002_c001_act.jsp) accepts unlimited uploads of the
        // same Excel filename — the user-supplied TITL is the only
        // human-facing identifier, and OVRS_PYMN_NO is the actual key.
        // An earlier refactor revision added a "rename to re-upload"
        // block; removed for parity with the other 4 payment modules.

        String pymnNo    = keyGenerator.nextOvrsPymnNo(bizKey);

        // Legacy parity (pymn_0007_002_c001_act.jsp:144-145, :161, :197, :207-208):
        // server recomputes the row count + total transfer amount from the
        // detail list — the client values are advisory only.
        int      serverTotTrnsCnt   = req.rows().size();
        BigDecimal serverTotalAmt   = BigDecimal.ZERO;
        for (var src : req.rows()) {
            if (src.trnf_amt() != null) {
                serverTotalAmt = serverTotalAmt.add(src.trnf_amt());
            }
        }

        // ── Summary row ──────────────────────────────────────────────────────
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(bizKey);
        sumr.setOvrsPymnNo(pymnNo);
        // Legacy OVRS_PYMN_TYPE_CD = "01" (pymn_0007_002_c001_act.jsp:139).
        sumr.setOvrsPymnTypeCd("01");
        sumr.setTitl(req.file_nm());
        sumr.setCcyCd(req.ccy_cd());
        sumr.setTotalTranAmt(serverTotalAmt);
        sumr.setTotTrnsCnt(serverTotTrnsCnt);
        sumr.setOutBankCd(req.wdrl_bank_cd());
        // OUT_BANK_NM is not stored on OVRS_PYMN_SUMR in JEX — the withdrawal
        // bank name is derived from OUT_BANK_CD via BANK_INFM in the list/result queries.
        sumr.setWdrlActNo(req.wdrl_acct_no());
        sumr.setOrgFileNm(req.file_nm());
        // Legacy parity (pymn_0007_002_c001_act.jsp:148): stamp upload
        // timestamp into TRNF_DT so the result-list query has a date anchor.
        sumr.setTrnfDt(nowDtm);
        sumr.setTrnfYn("N");
        sumr.setRegDtm(nowDtm);
        sumr.setRegId(userId);

        // Record the uploaded-file metadata on the summary BEFORE the flush; the
        // physical bytes are written only after the key is proven unique (below) so
        // a failed key-race attempt leaves no orphaned file on disk.
        String saveFileNm = null;
        if (file != null && !file.isEmpty()) {
            String orgFileNm = sanitiseOriginalFilename(file.getOriginalFilename());
            saveFileNm = pymnNo + "_" + orgFileNm;
            sumr.setSaveFileNm(saveFileNm);
            // JEX OVRS_PYMN_SUMR.FILE_SIZE is VARCHAR(100) — store the byte count as text.
            sumr.setFileSize(String.valueOf(file.getSize()));
        }

        // Flush the summary INSERT up front so a concurrent MAX+1 race on
        // OVRS_PYMN_NO (wb_get_next_val has no row lock) surfaces as a
        // DataIntegrityViolationException HERE — before the physical file write and
        // the detail INSERTs — so the one-shot retry in submit() re-runs cleanly with
        // no orphaned file or detail rows.
        sumrRepository.saveAndFlush(sumr);

        // Physical file write happens only after the key is proven unique.
        if (saveFileNm != null) {
            persistUploadedFile(bizKey, saveFileNm, file);
        }

        // ── Detail rows ───────────────────────────────────────────────────────
        // OVRS_PYMN_DETL_NO is a per-tenant numeric sequence (legacy parity);
        // allocate a contiguous block above the current tenant max.
        // OVRS_PYMN_DETL_NO base via the single wb_get_next_val funnel (V140 #30 —
        // per-tenant MAX+1 over (mstr UNION detl)). The funnel returns the next
        // value; subtract 1 so the loop's leading ++ yields that value for the
        // first row and a contiguous block thereafter.
        long detlNoCursor = Long.parseLong(legacyFunctions.nextOvrsPymnDetlNo(bizKey)) - 1;
        List<OvrsPymnSumrDetl> dtlsList = new ArrayList<>();
        for (var src : req.rows()) {
            OvrsPymnSumrDetl dtl = new OvrsPymnSumrDetl();
            dtl.setBizKey(bizKey);
            dtl.setOvrsPymnNo(pymnNo);
            dtl.setOvrsPymnDetlNo(Long.toString(++detlNoCursor));
            dtl.setCcyCd(src.ccy_cd());
            dtl.setTrnfAmt(src.trnf_amt());
            dtl.setFeePayerCd(src.fee_payer_cd());
            dtl.setMemo(nullToEmpty(src.memo()));
            dtl.setRprtAcctCd(nullIfBlank(src.rprt_acct_cd()));
            dtl.setBnfcNm(nullToEmpty(src.bnfc_nm()));
            dtl.setBnfcBankNm(nullToEmpty(src.bnfc_bank_nm()));
            dtl.setBicCd(nullToEmpty(src.bic_cd()));
            // JEX OVRS_PYMN_SUMR_DETL.BNFC_BANK_CD is NOT NULL and carries the
            // beneficiary bank SWIFT/BIC (same value the BIC column holds).
            dtl.setBnfcBankCd(nullToEmpty(src.bic_cd()));
            dtl.setBnfcAcctNo(nullToEmpty(src.bnfc_acct_no()));
            dtl.setBnfcAddr(nullToEmpty(src.bnfc_addr()));
            // Contact-less Excel upload: no resolved internal TRCO_KEY.
            // HAND_TRCO_KEY carries the user-facing beneficiary code.
            dtl.setTrcoKey("");
            dtl.setHandTrcoKey(nullToEmpty(src.bnfc_code()));
            dtl.setRegDtm(nowDtm);
            dtl.setRegId(userId);
            dtl.setModDtm(nowDtm);
            dtl.setModId(userId);
            dtlsList.add(dtl);
        }
        dtlsRepository.saveAll(dtlsList);

        log.info("feature=overseas-payment excel-upload saved bizKey={} ovrs_pymn_no={} file={} rows={}",
                bizKey, pymnNo, req.file_nm(), dtlsList.size());
        return new OverseasPaymentExcelUploadResult(pymnNo);
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Coalesce null → "" for JEX NOT-NULL detail columns (the Excel upload
     *  validation allows these optional, but OVRS_PYMN_SUMR_DETL requires them). */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Returns {@code true} when every cell in the row is blank/null. */
    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = COL_CURRENCY; c <= COL_REPORTING_ACCOUNT; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = readCell(row, c);
                if (!val.isBlank()) return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // File storage — write on submit, read on download
    // -------------------------------------------------------------------------

    /**
     * Write the uploaded Excel bytes to
     * {@code <storage-root>/<bizKey>/overseas-payment/<saveFileNm>}.
     * Creates the parent directories if they do not exist.
     */
    private void persistUploadedFile(String bizKey, String saveFileNm, MultipartFile file) {
        try {
            Path dir = fileResolver.tenantDirectory(bizKey);
            Files.createDirectories(dir);
            Path target = fileResolver.resolve(bizKey, saveFileNm);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("feature=overseas-payment file-saved bizKey={} saveFileNm={}", bizKey, saveFileNm);
        } catch (IOException e) {
            log.error("feature=overseas-payment file-write-failed bizKey={} saveFileNm={}",
                    bizKey, saveFileNm, e);
            throw new BusinessException("WBE2070");
        }
    }

    /**
     * Sanitise an original filename from a {@link MultipartFile}: strip
     * path separators that some clients send, and fall back to
     * {@code "upload.xlsx"} when the name is blank.
     */
    private static String sanitiseOriginalFilename(String raw) {
        if (raw == null || raw.isBlank()) return "upload.xlsx";
        String name = raw.replaceAll("[/\\\\]", "_").trim();
        return name.isBlank() ? "upload.xlsx" : name;
    }

    /**
     * Resolve the physical file behind an overseas payment batch upload.
     *
     * @param ovrsPymnNo the batch key
     * @return a {@link DownloadResource} carrying the path, display name,
     *         save name, and file size
     * @throws BusinessException {@code WBE2069} when the batch has no file
     *         (legacy DEV-imported row or pre-storage-rollout upload)
     */
    @Transactional(readOnly = true)
    public DownloadResource resolveDownload(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        OvrsPymnSumr sumr = sumrRepository.findByBizKeyAndOvrsPymnNo(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE2069"));

        String saveFileNm = sumr.getSaveFileNm();
        if (saveFileNm == null || saveFileNm.isBlank()) {
            throw new BusinessException("WBE2069");
        }

        Path path = fileResolver.resolve(bizKey, saveFileNm);
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("WBE2069");
        }

        long fileSize = (sumr.getFileSize() == null || sumr.getFileSize().isBlank())
                ? path.toFile().length()
                : Long.parseLong(sumr.getFileSize().trim());
        String displayName = (sumr.getOrgFileNm() == null || sumr.getOrgFileNm().isBlank())
                ? saveFileNm
                : sumr.getOrgFileNm();
        return new DownloadResource(path, displayName, saveFileNm, fileSize);
    }

    /**
     * Carrier for the resolved file — path + display name + file size.
     */
    public record DownloadResource(
            Path path, String orgFileNm, String saveFileNm, long fileSize) { }
}
