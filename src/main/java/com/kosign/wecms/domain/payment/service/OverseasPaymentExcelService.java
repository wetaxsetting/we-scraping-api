package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelFieldError;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelPreview;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelPreviewRow;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelUploadRequest;
import com.kosign.wecms.domain.payment.dto.OverseasPaymentExcelUploadResult;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.service.OverseasPaymentKeyGenerator;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
    private final OverseasPaymentKeyGenerator  keyGenerator;

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
    public OverseasPaymentExcelPreview preview(MultipartFile file) {
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
                validateAmount(amount,                               errors);
                validateRequired(feePayer,         "feePayer",        errors);
                validateRequired(beneficiaryCode,  "beneficiaryCode", errors);
                validateRequired(memo,             "memo",            errors);

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
     * Persist the overseas-payment Excel batch summary into
     * {@code OVRS_PYMN_SUMR} and return the allocated {@code OVRS_PYMN_NO}.
     *
     * <p>The frontend sends only metadata (account selection + totals derived
     * from the already-previewed rows); the actual file bytes are NOT stored
     * server-side in this R&amp;D implementation — only the summary record is
     * written so the scheduled-payment list can show the batch.
     *
     * @param req metadata gathered from the three Excel-upload dialog steps
     * @return the new batch key wrapped in {@link OverseasPaymentExcelUploadResult}
     */
    @Transactional
    public OverseasPaymentExcelUploadResult submit(OverseasPaymentExcelUploadRequest req) {
        String bizKey    = TenantContext.currentBizKey();
        String userId    = TenantContext.currentUserId();
        String pymnNo    = keyGenerator.nextOvrsPymnNo(bizKey);
        String nowDtm    = ZonedDateTime.now(VIETNAM_ZONE).format(DTM_FMT);

        OvrsPymnSumr row = new OvrsPymnSumr();
        row.setBizKey(bizKey);
        row.setOvrsPymnNo(pymnNo);
        row.setOvrsPymnTypeCd("EX");                       // Excel-upload batch
        row.setTitl(req.file_nm());
        row.setCcyCd(req.ccy_cd());
        row.setTotalTranAmt(req.total_tran_amt());
        row.setTotTrnsCnt(req.tot_trns_cnt());
        row.setOutBankCd(req.wdrl_bank_cd());
        row.setOutBankNm(req.wdrl_bank_nm());
        row.setWdrlActNo(req.wdrl_acct_no());
        row.setOrgFileNm(req.file_nm());
        row.setTrnfYn("N");
        row.setRegDtm(nowDtm);
        row.setRegId(userId);

        sumrRepository.save(row);
        log.info("feature=overseas-payment excel-upload saved bizKey={} ovrs_pymn_no={} file={}",
                bizKey, pymnNo, req.file_nm());
        return new OverseasPaymentExcelUploadResult(pymnNo);
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
}
