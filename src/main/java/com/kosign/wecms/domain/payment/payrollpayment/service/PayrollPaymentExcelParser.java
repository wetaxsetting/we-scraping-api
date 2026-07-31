package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParsedTransferLine;
import com.kosign.wecms.domain.payment.common.LegacyTextNormalizer;
import com.kosign.wecms.domain.payment.common.PaymentWireWidthGuard;
import com.kosign.wecms.domain.payment.common.WireField;
import com.kosign.wecms.domain.payment.validation.FieldCd;
import com.kosign.wecms.domain.payment.validation.PymnType;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Workbook parser for the Payroll Payment Excel template
 * ({@code pymn_0005_002}).
 *
 * <p>Smaller sibling of {@link com.kosign.wecms.domain.payment.expensepayment.service.ExpensePaymentExcelParser}
 * — the Payroll template carries 8 columns instead of 10 (no Contact
 * Code, no Document No., and "Deposit Bank" is a label-only display
 * column in the middle instead of derived from the bank code). Mirrors
 * legacy {@code pymn_0005_002_r001_act.jsp} header / cell layout.
 *
 * <p>Column order on the data side (matches legacy
 * {@code pymn_0005_002_r001_act.jsp} cell indices):
 * <ol start="0">
 *   <li>Bank Code — {@code TAR_BANK_CD} (3 chars → CITAD, 6 chars → NAPAS).
 *       Resolution via {@link TbFirmBankCodeRepository}.</li>
 *   <li>Deposit Bank — {@code TAR_BANK_NM} (free-text display label;
 *       parser keeps the user value verbatim).</li>
 *   <li>Deposit Account No. — {@code FINC_MDA_NO}</li>
 *   <li>Recipient Name — {@code RCPT_NM}</li>
 *   <li>Transfer Amount — {@code TRAN_AMT}</li>
 *   <li>Transfer Currency — {@code TRNF_CCY_CD}</li>
 *   <li>Remarks — {@code TRAN_USAG} (note: legacy stores in
 *       WDRL_ACCT_RMRK but our refactor wire uses TRAN_USAG for parity
 *       with the other payment-module parsers)</li>
 *   <li>Reporting Account — {@code CHART_ACCT_CD}</li>
 * </ol>
 *
 * <p>No Contact Code lookup — Payroll legacy template does NOT carry
 * a HAND_TRCO_KEY column, so this parser short-circuits that step
 * relative to Vendor / Expense.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollPaymentExcelParser {

    private static final String FLD_ANT_DIV_CD    = "ANT_DIV_CD";
    private static final String FLD_FINC_MDA_NO   = "FINC_MDA_NO";
    private static final String FLD_RCPT_NM       = "RCPT_NM";
    private static final String FLD_TRAN_AMT      = "TRAN_AMT";
    private static final String FLD_TRNF_CCY_CD   = "TRNF_CCY_CD";
    private static final String FLD_CHART_ACCT_CD = "CHART_ACCT_CD";

    /** Database column length cap on {@code SCDUL_PYMN_SUMR_DETL.CHART_ACCT_CD}. */
    private static final int CHART_ACCT_CD_MAX_LEN = 20;

    private static final int HEADER_ROW = 3;
    private static final int FIRST_DATA_ROW = 4;
    // G5 (2026-06-29): legacy DOM path caps at 1000 data rows
    // (pymn_0005_002_r001_act.jsp:510-512); the SAX/.xlsx path is uncapped.
    // The prior 500 + Math.min(...) silently DROPPED rows 501+, which
    // under-pays large payroll files. Raise the cap to the legacy DOM value
    // and REJECT (WBE_PAYROLL_TOO_MANY_ROWS) instead of silently truncating
    // — see the parse loop below.
    private static final int MAX_ROWS = 1000;

    private static final int CITAD_LENGTH = 3;
    private static final int NAPAS_LENGTH = 6;

    /** Expected column header text at row 3. Mirrors legacy
     *  {@code pymn_0005_002_r001_act.jsp HEADER_TITLE_*} (lines 60-92). */
    private static final String[] HEADERS_EN = {
            "Bank Code", "Deposit Bank", "Deposit", "Recipient Name",
            "Transfer Amount", "Transfer Currency",
            "Remarks", "Reporting Account",
    };
    private static final String[] HEADERS_KO = {
            "은행코드", "입금은행", "입금 계좌번호", "수신자명",
            "이체 금액", "이체통화",
            "적요", "보고서 계정",
    };
    private static final String[] HEADERS_VI = {
            "Mã ngân hàng", "Ngân hàng nhận tiền", "Số tài khoản nhận tiền",
            "Tên người nhận", "Số tiền chuyển khoản", "Loại tiền chuyển",
            "Nội dung giao dịch", "Tài khoản báo cáo",
    };

    private final TbFirmBankCodeRepository firmBankCodeRepo;
    private final ChartOfAccountRepository chartAcctRepo;
    private final SpecialCharValidator     specialCharValidator;
    /** C-14: fixed-length wire-slot width guard for the bank-bound name & memo fields. */
    private final PaymentWireWidthGuard    wireWidthGuard;
    /** Cross-row consistency guard — single currency per file + single
     *  network per file + VND/USD whitelist. Mirrors legacy
     *  {@code PymnHelper.java:420-436} + {@code :1261-1297}. */
    private final com.kosign.wecms.domain.payment.common.UploadConsistencyValidator
                                           uploadConsistencyValidator;

    @Transactional(readOnly = true)
    public ParseExcelResponse parse(MultipartFile file, String bankCd) {
        List<ParsedTransferLine> rows = new ArrayList<>();
        int sucCnt = 0;
        int errCnt = 0;

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return new ParseExcelResponse(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
            }

            DataFormatter fmt = new DataFormatter();
            validateHeaderRow(sheet, fmt);

            // G5: iterate to the real last row (no Math.min truncation) and
            // stop at the first all-blank data row (legacy break). Counting
            // only the contiguous non-blank data rows means trailing blank /
            // formatted rows never trip the cap with a false positive.
            for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // G6 (legacy parity): SKIP (continue) rows whose critical
                // cells (A/C/D/E/F/G) are all empty — legacy
                // `pymn_0005_002_r001_act.jsp:255-260` continues/returns past a
                // blank row and keeps processing later rows. The prior `break`
                // stopped at the first blank, silently dropping every row after
                // a mid-file blank separator.
                String c0 = cellString(row, 0, fmt);
                String c2 = cellString(row, 2, fmt);
                String c3 = cellString(row, 3, fmt);
                String c4 = cellString(row, 4, fmt);
                String c5 = cellString(row, 5, fmt);
                String c6 = cellString(row, 6, fmt);
                if (isBlank(c0) && isBlank(c2) && isBlank(c3)
                        && isBlank(c4) && isBlank(c5) && isBlank(c6)) {
                    continue;
                }

                // G5: reject oversized files rather than silently dropping
                // rows 501+ (the old Math.min behaviour). Throws on the
                // (MAX_ROWS+1)-th non-blank data row.
                if (rows.size() >= MAX_ROWS) {
                    throw new BusinessException("WBE_PAYROLL_TOO_MANY_ROWS");
                }

                ParsedRow parsed = parseRow(row, fmt, bankCd);
                rows.add(parsed.line);
                if (parsed.hasError) {
                    errCnt++;
                } else {
                    sucCnt++;
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("feature=payroll-payment parse-excel io-error: {}", ex.getMessage());
            throw new BusinessException("WBE2017");
        } catch (RuntimeException ex) {
            log.warn("feature=payroll-payment parse-excel format-error: {}", ex.getMessage());
            throw new BusinessException("WBE2017");
        }

        // Cross-row consistency — legacy parity with PymnHelper
        // (WBW1160 + WBE1162 → WBE2120; WBE1191 → WBE2121).
        List<String> ccyList = rows.stream()
                .map(ParsedTransferLine::trnfCcyCd).toList();
        uploadConsistencyValidator.assertSingleCurrency(ccyList);
        uploadConsistencyValidator.assertCurrencyWhitelist(ccyList);
        uploadConsistencyValidator.assertSingleNetwork(rows.stream()
                .map(ParsedTransferLine::antDivCd).toList());

        int totCnt = rows.size();
        log.info("feature=payroll-payment parse-excel totCnt={} sucCnt={} errCnt={}",
                totCnt, sucCnt, errCnt);
        return new ParseExcelResponse(
                BigDecimal.valueOf(totCnt),
                BigDecimal.valueOf(sucCnt),
                BigDecimal.valueOf(errCnt),
                rows);
    }

    /* --------------------------------------------------------------
     *  Header validation
     * -------------------------------------------------------------- */

    private static void validateHeaderRow(Sheet sheet, DataFormatter fmt) {
        Row header = sheet.getRow(HEADER_ROW);
        if (header == null) {
            throw new BusinessException("WBE2018");
        }
        for (int c = 0; c < HEADERS_EN.length; c++) {
            String cell = cellString(header, c, fmt);
            if (isBlank(cell)) {
                throw new BusinessException("WBE2018");
            }
            String trimmed = cell.trim();
            boolean ok = trimmed.startsWith(HEADERS_EN[c])
                      || trimmed.startsWith(HEADERS_KO[c])
                      || trimmed.startsWith(HEADERS_VI[c]);
            if (!ok) {
                log.warn("feature=payroll-payment parse-excel header-mismatch col={} got={}",
                        c, trimmed);
                throw new BusinessException("WBE2018");
            }
        }
    }

    /* --------------------------------------------------------------
     *  Per-row parsing
     * -------------------------------------------------------------- */

    private record ParsedRow(ParsedTransferLine line, boolean hasError) {}
    private record FieldError(String fieldId, String cause) {}

    private ParsedRow parseRow(Row row, DataFormatter fmt, String bankCd) {
        String bankCodeIn  = cellString(row, 0, fmt);
        String tarBankNm   = cellString(row, 1, fmt);
        String fincMdaNo   = cellString(row, 2, fmt);
        // F1 (audit task-44508): strip Vietnamese accents from Recipient Name
        // and Remarks BEFORE validation and persistence. Mirrors legacy
        // `pymn_0005_002_r001_act.jsp:305-306` (RCPT_NM) + `:344-345` (REMARKS).
        String rcptNm      = LegacyTextNormalizer.stripVietnameseTone(cellString(row, 3, fmt));
        String tranAmtIn   = cellString(row, 4, fmt);
        String trnfCcyIn   = cellString(row, 5, fmt);
        String tranUsag    = LegacyTextNormalizer.stripVietnameseTone(cellString(row, 6, fmt));
        String chartAcctCd = cellString(row, 7, fmt);

        List<FieldError> errors = new ArrayList<>();

        if (isBlank(fincMdaNo)) {
            errors.add(new FieldError(FLD_FINC_MDA_NO, "Deposit Account No. is required"));
        }
        if (isBlank(rcptNm)) {
            errors.add(new FieldError(FLD_RCPT_NM, "Recipient Name is required"));
        }

        // Bank Code resolution via TB_FIRM_BANK_CODE.
        String resolvedBankCd = null;
        String resolvedBankNm = null;
        String resolvedAntDiv = null;
        if (isBlank(bankCodeIn)) {
            errors.add(new FieldError(FLD_ANT_DIV_CD, "Bank Code is required"));
        } else {
            String trimmed = bankCodeIn.trim();
            Optional<TbFirmBankCode> hit;
            if (trimmed.length() == NAPAS_LENGTH) {
                resolvedAntDiv = "NAPAS";
                hit = firmBankCodeRepo.findByAntDivCdAndBankCd("NAPAS", trimmed);
            } else if (trimmed.length() == CITAD_LENGTH) {
                resolvedAntDiv = "CITAD";
                hit = firmBankCodeRepo.findFirstByAntDivCdAndCitadBankCd("CITAD", trimmed);
            } else {
                resolvedAntDiv = "";
                hit = Optional.empty();
                errors.add(new FieldError(FLD_ANT_DIV_CD,
                        "Bank Code must be 3 chars (CITAD) or 6 chars (NAPAS): " + trimmed));
            }

            if (hit.isPresent()) {
                resolvedBankCd = trimmed;
                resolvedBankNm = displayName(hit.get());
            } else if (resolvedAntDiv.isEmpty()) {
                resolvedBankCd = trimmed;
                resolvedBankNm = emptyToNull(tarBankNm);
            } else {
                resolvedBankCd = trimmed;
                resolvedBankNm = emptyToNull(tarBankNm);
                errors.add(new FieldError(FLD_ANT_DIV_CD,
                        "Unknown Bank Code: " + trimmed));
            }
        }

        BigDecimal tranAmt = null;
        if (isBlank(tranAmtIn)) {
            errors.add(new FieldError(FLD_TRAN_AMT, "Transfer Amount is required"));
        } else {
            try {
                BigDecimal parsed = new BigDecimal(tranAmtIn.trim().replace(",", ""));
                if (parsed.signum() < 0) {
                    errors.add(new FieldError(FLD_TRAN_AMT,
                            "Transfer Amount must be non-negative"));
                }
                tranAmt = parsed;
            } catch (NumberFormatException ex) {
                errors.add(new FieldError(FLD_TRAN_AMT,
                        "Transfer Amount is not a valid number: " + tranAmtIn));
            }
        }

        String trnfCcyCd = null;
        if (isBlank(trnfCcyIn)) {
            errors.add(new FieldError(FLD_TRNF_CCY_CD, "Transfer Currency is required"));
        } else {
            trnfCcyCd = trnfCcyIn.trim().toUpperCase();
        }

        // Reporting Account validation — lookup CHART_ACCT_INFM for
        // the current tenant; stamp BOTH chart_acct_cd + seq_no so the
        // saved row carries the seq_no the picker/breadcrumb need.
        String bizKey = TenantContext.currentBizKey();
        String resolvedChartAcctSeqNo = null;
        String resolvedChartAcctCd    = null;
        if (!isBlank(chartAcctCd)) {
            String trimmed = chartAcctCd.trim();
            if (trimmed.length() > CHART_ACCT_CD_MAX_LEN) {
                errors.add(new FieldError(FLD_CHART_ACCT_CD,
                        "Reporting Account exceeds " + CHART_ACCT_CD_MAX_LEN
                                + " characters: " + trimmed));
            } else if (bizKey == null) {
                errors.add(new FieldError(FLD_CHART_ACCT_CD,
                        "Tenant context missing — please re-login"));
            } else {
                Optional<ChartAcctInfm> hit =
                        chartAcctRepo.findByBizKeyAndChartAcctCdAndDelYn(bizKey, trimmed, "N");
                if (hit.isPresent()) {
                    resolvedChartAcctCd    = hit.get().getChartAcctCd();
                    resolvedChartAcctSeqNo = hit.get().getChartAcctSeqNo();
                } else {
                    // Preserve the typed value so the preview cell shows
                    // the user what they entered (mirrors the bank-code
                    // branch above). The pipe-separated err_fld + cause
                    // pair drives the red highlight + tooltip on the wire.
                    resolvedChartAcctCd = trimmed;
                    errors.add(new FieldError(FLD_CHART_ACCT_CD,
                            "Reporting Account is not registered or inactive: " + trimmed));
                }
            }
        }

        // Special-character checks against BLOCKED_CHARS_RULE. Mirrors
        // legacy `pymn_0005_002_r001_act.jsp:311` (RCPT_NM cell col 3)
        // and `:350` (TRAN_USAG cell col 6). Bank-specific — missing
        // rule for the (bankCd, PAYROLL_PAYMENT, fieldCd) tuple means
        // the call is a no-op, so this is safe before BLOCKED_CHARS_RULE
        // is seeded for Payroll.
        if (!isBlank(rcptNm)) {
            specialCharValidator
                    .validate(bankCd, PymnType.PAYROLL_PAYMENT, FieldCd.RECIPIENT_NAME, rcptNm)
                    .ifPresent(msg -> errors.add(new FieldError(FLD_RCPT_NM, msg)));
        }
        if (!isBlank(tranUsag)) {
            specialCharValidator
                    .validate(bankCd, PymnType.PAYROLL_PAYMENT, FieldCd.REMARKS, tranUsag)
                    .ifPresent(msg -> errors.add(new FieldError("TRAN_USAG", msg)));
        }

        // C-14: the value must also FIT its slot in the bank's fixed-length record. RCPT_NM feeds
        // the 150-byte receiverName field and TRAN_USAG feeds both 210-byte memo fields; text that
        // survives tone-stripping (Korean/CJK) is 3 UTF-8 bytes per character and blows the slot
        // long before the varchar(150) column notices. Flagged here so the user can fix the cell —
        // FixedLengthCodec's throw is the mid-dispatch backstop, not a user-facing error.
        wireWidthGuard.check(WireField.RECEIVER_NAME, rcptNm)
                .ifPresent(msg -> errors.add(new FieldError(FLD_RCPT_NM, msg)));
        wireWidthGuard.check(WireField.DEPOSIT_ACCOUNT_MEMO, tranUsag)
                .ifPresent(msg -> errors.add(new FieldError("TRAN_USAG", msg)));

        String errFld   = buildErrPair(errors, FieldError::fieldId);
        String errCause = buildErrPair(errors, FieldError::cause);

        ParsedTransferLine line = new ParsedTransferLine(
                tranAmt,
                resolvedAntDiv,
                emptyToNull(rcptNm),
                resolvedBankCd,
                resolvedBankNm,
                emptyToNull(fincMdaNo),
                emptyToNull(tranUsag),
                errFld,
                errCause,
                null,                          // TRCO_KEY — Payroll has no Contact Code column
                null,                          // HAND_TRCO_KEY — Payroll has no Contact Code column
                null,                          // DOC_NO — Payroll has no Document No. column
                null,                          // NOTE — Payroll has no Description column
                null,                          // CITAD_BANK_CD — bank-code resolver detail (Phase 2)
                null,                          // TRCO_CMNM — n/a
                null,                          // TRCO_CMENM — n/a
                resolvedChartAcctSeqNo,        // CHART_ACCT_SEQ_NO — looked up from CHART_ACCT_INFM
                resolvedChartAcctCd,           // CHART_ACCT_CD — normalized to the registered code
                trnfCcyCd,
                null);
        return new ParsedRow(line, !errors.isEmpty());
    }

    private static String displayName(TbFirmBankCode b) {
        if (!isBlank(b.getEngAbrvatNm())) return b.getEngAbrvatNm();
        if (!isBlank(b.getBankNm()))      return b.getBankNm();
        return b.getBankCd();
    }

    private static String buildErrPair(
            List<FieldError> errors,
            java.util.function.Function<FieldError, String> extractor) {
        if (errors.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(extractor.apply(errors.get(i)));
        }
        return sb.toString();
    }

    /* --------------------------------------------------------------
     *  POI cell helpers
     * -------------------------------------------------------------- */

    private static String cellString(Row row, int col, DataFormatter fmt) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return Long.toString((long) d);
            }
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        // F2 (audit task-44508): strip embedded CR/LF from EVERY text cell,
        // mirroring legacy `ExcelUtils.getCell:144`. Tabs preserved.
        // C-14: NFC first. replaceTone maps precomposed code points only, so a decomposed accent
        // would survive tone-stripping and reach the fixed-length codec at full UTF-8 width.
        // See LegacyTextNormalizer.toNfc.
        return LegacyTextNormalizer.stripCrLf(
                LegacyTextNormalizer.toNfc(fmt.formatCellValue(cell))).trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
