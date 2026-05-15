package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParsedTransferLine;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
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
 * Workbook parser for the Vendor Payment Excel template
 * ({@code pymn_0001_002}).
 *
 * <p>Sibling of {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferExcelParser}
 * — same row layout, same bank-code resolution, same per-cell error
 * shape; the only difference is one extra column (Contact Code at
 * index 7) that Vendor Payment requires for downstream Contacts
 * master validation (legacy
 * {@code PymnHelper.uploadPaymentExcel:1386-1421}). Internal Transfer
 * has 9 columns; Vendor has 10.
 *
 * <p>Row layout matches the JEX
 * {@code WeCMS_Vendor_Payment_upload_*.xlsx} template:
 * <ul>
 *   <li>row 0 — "Vendor Payment" merged title</li>
 *   <li>row 1 — blank</li>
 *   <li>row 2 — per-column descriptions</li>
 *   <li>row 3 — column headers (validated per locale — see
 *       {@link #validateHeaderRow})</li>
 *   <li>row 4+ — data (capped at 500 rows + early break on first
 *       all-critical-blank row)</li>
 * </ul>
 *
 * <p>Column order on the data side (matches legacy
 * {@code PymnHelper.readExcel} cell indices):
 * <ol start="0">
 *   <li>Bank Code — {@code ANT_DIV_CD} value (3 chars → CITAD, 6
 *       chars → NAPAS). Resolution via {@link TbFirmBankCodeRepository}.</li>
 *   <li>Deposit No. — {@code FINC_MDA_NO}</li>
 *   <li>Bank — display-only (parser derives the value from column 0)</li>
 *   <li>Transfer Amount — {@code TRAN_AMT}</li>
 *   <li>Transfer Currency — {@code TRNF_CCY_CD}</li>
 *   <li>Remarks — {@code TRAN_USAG}</li>
 *   <li>Reporting Account — {@code CHART_ACCT_CD}</li>
 *   <li><b>Contact Code — {@code HAND_TRCO_KEY}</b> (Vendor-only)</li>
 *   <li>Description — {@code NOTE}</li>
 *   <li>Document No. — {@code DOC_NO}</li>
 * </ol>
 *
 * <p>Phase 1 scope: header layout validation + per-cell parsing
 * succeed. The legacy contact-master validation (lookup
 * {@code TRCO_INFM} by {@code HAND_TRCO_KEY}, then cross-check
 * recipient name / company name) lands when Contacts becomes a hard
 * dependency on the refactor side — until then {@code HAND_TRCO_KEY}
 * is carried verbatim through the wire to the save step.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VendorPaymentExcelParser {

    private static final String FLD_ANT_DIV_CD    = "ANT_DIV_CD";
    private static final String FLD_FINC_MDA_NO   = "FINC_MDA_NO";
    private static final String FLD_TRAN_AMT      = "TRAN_AMT";
    private static final String FLD_TRNF_CCY_CD   = "TRNF_CCY_CD";
    private static final String FLD_HAND_TRCO_KEY = "HAND_TRCO_KEY";

    private static final int HEADER_ROW = 3;
    private static final int FIRST_DATA_ROW = 4;
    private static final int MAX_ROWS = 500;

    private static final int CITAD_LENGTH = 3;
    private static final int NAPAS_LENGTH = 6;

    /** Expected column header text at row 3. Mirrors legacy
     *  {@code PymnHelper#HEADER_TITLE_EN/KO/VI} (lines 94-103). 10
     *  columns — the Contact-Code header at index 7 is the divergence
     *  from Internal Transfer. */
    private static final String[] HEADERS_EN = {
            "Bank Code *", "Deposit No.*", "Bank",
            "Transfer Amount *", "Transfer Currency *",
            "Remarks (200 letters)", "Reporting Account",
            "Contact Code *",
            "Description", "Document No.",
    };
    private static final String[] HEADERS_KO = {
            "은행코드 *", "입금 계좌번호 *", "은행",
            "이체 금액 *", "이체통화 *",
            "적요 (200자)", "보고서 계정",
            "거래처 코드 *",
            "내용", "문서번호",
    };
    private static final String[] HEADERS_VI = {
            "Mã ngân hàng *", "Số tài khoản nhận tiền*", "Ngân hàng",
            "Số tiền chuyển khoản *", "Lọai tiền chuyển *",
            "Nội dung giao dịch (200 ký tự)", "Tài khoản báo cáo",
            "Mã đối tác *",
            "Diễn giải", "Số chứng từ",
    };

    private final TbFirmBankCodeRepository firmBankCodeRepo;
    private final TrcoInfmRepository       contactRepo;

    @Transactional(readOnly = true)
    public ParseExcelResponse parse(MultipartFile file) {
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

            int lastRow = Math.min(sheet.getLastRowNum(), FIRST_DATA_ROW + MAX_ROWS - 1);
            for (int r = FIRST_DATA_ROW; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Legacy early-break — see PymnHelper.readExcel:338-341.
                // Vendor's break-set is the same 6 critical cells:
                // Bank Code (0), Deposit No. (1), Amount (3),
                // Contact Code (7), Description (8), Doc No. (9).
                String c0 = cellString(row, 0, fmt);
                String c1 = cellString(row, 1, fmt);
                String c3 = cellString(row, 3, fmt);
                String c7 = cellString(row, 7, fmt);
                String c8 = cellString(row, 8, fmt);
                String c9 = cellString(row, 9, fmt);
                if (isBlank(c0) && isBlank(c1) && isBlank(c3)
                        && isBlank(c7) && isBlank(c8) && isBlank(c9)) {
                    break;
                }

                ParsedRow parsed = parseRow(row, fmt);
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
            log.warn("feature=vendor-payment parse-excel io-error: {}", ex.getMessage());
            throw new BusinessException("WBE2017");
        } catch (RuntimeException ex) {
            log.warn("feature=vendor-payment parse-excel format-error: {}", ex.getMessage());
            throw new BusinessException("WBE2017");
        }

        int totCnt = rows.size();
        log.info("feature=vendor-payment parse-excel totCnt={} sucCnt={} errCnt={}",
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
                log.warn("feature=vendor-payment parse-excel header-mismatch col={} got={}",
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

    private ParsedRow parseRow(Row row, DataFormatter fmt) {
        String bankCodeIn  = cellString(row, 0, fmt);
        String fincMdaNo   = cellString(row, 1, fmt);
        String tranAmtIn   = cellString(row, 3, fmt);
        String trnfCcyIn   = cellString(row, 4, fmt);
        String tranUsag    = cellString(row, 5, fmt);
        String chartAcctCd = cellString(row, 6, fmt);
        String handTrcoKey = cellString(row, 7, fmt);   // Vendor-only column
        String note        = cellString(row, 8, fmt);
        String docNo       = cellString(row, 9, fmt);

        List<FieldError> errors = new ArrayList<>();

        if (isBlank(fincMdaNo)) {
            errors.add(new FieldError(FLD_FINC_MDA_NO, "Deposit No. is required"));
        }

        // Contact Code — required on the Vendor template (header `*`).
        // Mirrors legacy `PymnHelper.ckContactCode` (lines 1169-1227):
        //
        //   1. Required-and-not-blank.
        //   2. Length <= 20 chars.
        //   3. Lookup in TRCO_INFM by (BIZ_KEY, HAND_TRCO_KEY) where
        //      TRCO_STTS is NULL or != 'C' (excludes trashed contacts).
        //   4. On hit: cross-fill TRCO_KEY / TRCO_CMNM / TRCO_CMENM on
        //      the line — those land on the wire so the save endpoint
        //      can persist the resolved recipient identity without
        //      re-querying.
        //   5. On miss: error "HAND_TRCO_KEY" with the localised
        //      "Contact Code not registered" message.
        //
        // The tenant scope comes from TenantContext.currentBizKey() —
        // never trust a wire-supplied biz_key here.
        String resolvedTrcoKey    = null;
        String resolvedTrcoCmnm   = null;
        String resolvedTrcoCmenm  = null;
        if (isBlank(handTrcoKey)) {
            errors.add(new FieldError(FLD_HAND_TRCO_KEY, "Contact Code is required"));
        } else if (handTrcoKey.length() > 20) {
            errors.add(new FieldError(FLD_HAND_TRCO_KEY,
                    "Contact Code exceeds 20 characters: " + handTrcoKey));
        } else {
            String bizKey = TenantContext.currentBizKey();
            if (bizKey == null) {
                // Should never happen — the controller is authenticated.
                // Defensive fallback so the parser never NPEs.
                errors.add(new FieldError(FLD_HAND_TRCO_KEY,
                        "Tenant context missing — please re-login"));
            } else {
                java.util.Optional<TrcoInfm> hit =
                        contactRepo.findActiveByBizKeyAndHandTrcoKey(bizKey, handTrcoKey.trim());
                if (hit.isPresent()) {
                    TrcoInfm c = hit.get();
                    resolvedTrcoKey   = c.getTrcoKey();
                    resolvedTrcoCmnm  = c.getTrcoCmnm();
                    resolvedTrcoCmenm = c.getTrcoCmenm();
                } else {
                    errors.add(new FieldError(FLD_HAND_TRCO_KEY,
                            "Contact Code is not registered: " + handTrcoKey));
                }
            }
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
                resolvedBankNm = "";
            } else {
                resolvedBankCd = trimmed;
                resolvedBankNm = "";
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

        String errFld   = buildErrPair(errors, FieldError::fieldId);
        String errCause = buildErrPair(errors, FieldError::cause);

        ParsedTransferLine line = new ParsedTransferLine(
                tranAmt,
                resolvedAntDiv,
                null,                          // RCPT_NM — derived from contact bank-account lookup; out of Phase 1
                resolvedBankCd,
                resolvedBankNm,
                emptyToNull(fincMdaNo),
                emptyToNull(tranUsag),
                errFld,
                errCause,
                resolvedTrcoKey,               // TRCO_KEY — cross-filled from Contacts master
                emptyToNull(handTrcoKey),
                emptyToNull(docNo),
                emptyToNull(note),
                null,                          // CITAD_BANK_CD — bank-code resolver detail (Phase 2)
                resolvedTrcoCmnm,              // TRCO_CMNM — cross-filled from Contacts master
                resolvedTrcoCmenm,             // TRCO_CMENM — cross-filled from Contacts master
                null,
                emptyToNull(chartAcctCd),
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
        return fmt.formatCellValue(cell).trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
