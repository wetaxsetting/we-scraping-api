package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParsedTransferLine;
import com.kosign.wecms.domain.payment.common.LegacyTextNormalizer;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
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
 * Workbook parser for the Internal Transfer Excel template
 * ({@code pymn_0009_002}).
 *
 * <p>Reads an Excel workbook (.xls / .xlsx) handed to
 * {@link InternalTransferService#parseExcel(MultipartFile, String)} and
 * emits one {@link ParsedTransferLine} per data row plus per-cell error
 * flags so the frontend preview can paint the red highlights AND the
 * hover tooltip (paired {@code err_fld} / {@code err_fld_caus} arrays,
 * legacy parity with {@code pymn_0009_002.ts:296-298}).
 *
 * <p>Row layout matches the JEX
 * {@code WeCMS_Internal_Transfer_upload_*.xlsx} template:
 * <ul>
 *   <li>row 0 — "Internal Transfer" merged title</li>
 *   <li>row 1 — blank</li>
 *   <li>row 2 — per-column descriptions</li>
 *   <li>row 3 — column headers (validated per locale — see
 *       {@link #validateHeaderRow})</li>
 *   <li>row 4+ — data (capped at 500 rows + early break on first
 *       all-critical-blank row)</li>
 * </ul>
 *
 * <p>Column order on the data side (matches legacy
 * {@code PymnHelper.readExcelFromInternalTransfer} cell indices):
 * <ol start="0">
 *   <li>Bank Code — 3 chars → CITAD, 6 chars → NAPAS. Resolution
 *       goes through {@link TbFirmBankCodeRepository}: {@code TAR_BANK_CD}
 *       persists the resolved registry {@code BANK_CD} (legacy
 *       {@code getBankCode}; NAPAS = the 6-digit code, CITAD = the full
 *       {@code BANK_CD} the 3-digit {@code citad_bank_cd} maps to), and
 *       {@code CITAD_BANK_CD} carries the 3-digit code for a CITAD row /
 *       {@code "000"} for a NAPAS row. The display name from
 *       {@code ENG_ABRVAT_NM} lands on {@code TAR_BANK_NM} (the
 *       Bank column).</li>
 *   <li>Deposit No. — {@code FINC_MDA_NO}</li>
 *   <li>Bank — display-only (parser ignores input, derives the
 *       value from column 0's bank-code lookup)</li>
 *   <li>Transfer Amount — {@code TRAN_AMT}</li>
 *   <li>Transfer Currency — {@code TRNF_CCY_CD}</li>
 *   <li>Remarks — {@code TRAN_USAG}</li>
 *   <li>Reporting Account — {@code CHART_ACCT_CD}</li>
 *   <li>Description — {@code NOTE}</li>
 *   <li>Document No. — {@code DOC_NO}</li>
 * </ol>
 *
 * <p>Mirrors legacy {@code PymnHelper.importDataFromExcelForInternalTransfer}
 * and {@code validDefualtHeaderInternalTransfer} — see
 * {@code WSVC.pymn_0009_002_r001.xml:rule_Sub_0}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTransferExcelParser {

    private static final String FLD_ANT_DIV_CD    = "ANT_DIV_CD";
    private static final String FLD_FINC_MDA_NO   = "FINC_MDA_NO";
    private static final String FLD_TRAN_AMT      = "TRAN_AMT";
    private static final String FLD_TRNF_CCY_CD   = "TRNF_CCY_CD";
    private static final String FLD_CHART_ACCT_CD = "CHART_ACCT_CD";

    /** Database column length cap on {@code SCDUL_PYMN_SUMR_DETL.CHART_ACCT_CD}. */
    private static final int CHART_ACCT_CD_MAX_LEN = 20;

    /** Header row layout — see class Javadoc. */
    private static final int HEADER_ROW = 3;
    private static final int FIRST_DATA_ROW = 4;
    private static final int MAX_ROWS = 500;

    /** Network-code derivation from the user-typed Bank Code length —
     *  mirrors legacy {@code PymnHelper.readExcelFromInternalTransfer:
     *  706-712} byte-for-byte: 3 chars → CITAD, 6 chars → NAPAS. */
    private static final int CITAD_LENGTH = 3;
    private static final int NAPAS_LENGTH = 6;

    /** Vietcombank NAPAS code — legacy {@code PymnHelper.VCB_BANK_CD} (970436).
     *  VCB does not support USD transfers to other banks (PymnHelper
     *  .uploadPaymentExcel:1460-1465). */
    private static final String VCB_BANK_CD = "970436";

    /** Legacy field-length caps flagged at preview
     *  ({@code PymnHelper.checkValidStringInMax}): Remarks 200, Description
     *  200, Document No. 50. They equal the DB column lengths on
     *  {@code SCDUL_PYMN_SUMR_DETL} ({@code TRAN_USAG}/{@code NOTE} = 200,
     *  {@code DOC_NO} = 50). */
    private static final int REMARKS_MAX_LEN = 200;
    private static final int NOTE_MAX_LEN    = 200;
    private static final int DOC_NO_MAX_LEN  = 50;

    /**
     * Expected column header text at row 3 for each supported locale.
     * Mirrors legacy {@code PymnHelper#HEADER_TITLE_INTER_EN/KO/VI}
     * (line 106-114). Header validation passes when each cell at row
     * 3 begins with one of the per-language expected values
     * (case-sensitive prefix match — legacy parity with
     * {@code validDefualtHeaderInternalTransfer}).
     */
    private static final String[] HEADERS_EN = {
            "Bank Code *", "Deposit No.*", "Bank",
            "Transfer Amount *", "Transfer Currency *",
            "Remarks (200 letters)", "Reporting Account",
            "Description", "Document No.",
    };
    private static final String[] HEADERS_KO = {
            "은행코드 *", "입금 계좌번호 *", "은행",
            "이체 금액 *", "이체통화 *",
            "적요 (200자)", "보고서 계정",
            "내용", "문서번호",
    };
    private static final String[] HEADERS_VI = {
            "Mã ngân hàng *", "Số tài khoản nhận tiền*", "Ngân hàng",
            "Số tiền chuyển khoản *", "Lọai tiền chuyển *",
            "Nội dung giao dịch (200 ký tự)", "Tài khoản báo cáo",
            "Diễn giải", "Số chứng từ",
    };

    private final TbFirmBankCodeRepository firmBankCodeRepo;
    private final SpecialCharValidator     specialCharValidator;
    /** Cross-row consistency guard — single currency per file + single
     *  network per file + VND/USD whitelist. Mirrors legacy
     *  {@code PymnHelper.java:420-436} + {@code :1261-1297}. */
    private final com.kosign.wecms.domain.payment.common.UploadConsistencyValidator
                                           uploadConsistencyValidator;
    /** Resolves {@code CHART_ACCT_INFM} for the Reporting Account
     *  cell (column 6). Mirrors legacy {@code PymnHelper.ckChartAcctCd}
     *  — active-only lookup; rejects codes that don't exist for the
     *  tenant. Same pattern as Vendor / Expense / Payroll parsers. */
    private final ChartOfAccountRepository chartAcctRepo;
    /** Banking-account master — used to resolve {@code RCPT_NM} from
     *  the company's own account directory. Internal Transfer always
     *  targets one of the company's own accounts, so {@code ACCT_NM}
     *  is the recipient's holder name. Mirrors legacy
     *  {@code PymnHelper.validateBankAccountInAcctMstr}. */
    private final AcctMstrRepository       acctMstrRepository;
    /** Folds the withdrawal bank code (NAPAS or internal {@code BANK_CD})
     *  to its canonical NAPAS code so the VCB-USD-to-other-bank guard can
     *  compare it against {@link #VCB_BANK_CD}. Mirrors legacy
     *  {@code PymnHelper.uploadPaymentExcel:1315-1317}. */
    private final OutBankCdNormalizer      outBankCdNormalizer;

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

            // VCB-USD guard precompute (legacy PymnHelper.uploadPaymentExcel:
            // 1460): the withdrawal bank code arrives in internal BANK_CD form;
            // fold it to its canonical NAPAS code once so the per-row USD check
            // can compare against VCB.
            boolean withdrawalIsVcb =
                    VCB_BANK_CD.equals(outBankCdNormalizer.normalize(bankCd));

            // Cap at MAX_ROWS — Excel templates often carry hundreds
            // of styled-but-empty trailing rows. The early-break below
            // already stops at the right place, but the cap is the
            // belt-and-braces defence (legacy parity).
            int lastRow = Math.min(sheet.getLastRowNum(), FIRST_DATA_ROW + MAX_ROWS - 1);
            for (int r = FIRST_DATA_ROW; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Legacy early-break: when the critical cells (Bank
                // Code + Deposit + Amount + Description + Doc No.) are
                // ALL blank, parsing STOPS — see
                // `PymnHelper.readExcelFromInternalTransfer:680-683`.
                String c0 = cellString(row, 0, fmt);
                String c1 = cellString(row, 1, fmt);
                String c3 = cellString(row, 3, fmt);
                String c7 = cellString(row, 7, fmt);
                String c8 = cellString(row, 8, fmt);
                if (isBlank(c0) && isBlank(c1) && isBlank(c3)
                        && isBlank(c7) && isBlank(c8)) {
                    break;
                }

                ParsedRow parsed = parseRow(row, fmt, bankCd, withdrawalIsVcb);
                rows.add(parsed.line);
                if (parsed.hasError) {
                    errCnt++;
                } else {
                    sucCnt++;
                }
            }
        } catch (BusinessException ex) {
            // Header / business validation — propagate verbatim so the
            // controller envelope keeps the original error code.
            throw ex;
        } catch (IOException ex) {
            log.warn("feature=internal-transfer parse-excel io-error: {}", ex.getMessage());
            throw new BusinessException("WBE2017");
        } catch (RuntimeException ex) {
            log.warn("feature=internal-transfer parse-excel format-error: {}", ex.getMessage());
            throw new BusinessException("WBE2017");
        }

        // Cross-row consistency — legacy parity with PymnHelper:
        //   • assertSingleCurrency: WBW1160 → refactor WBE2120 (mixed VND/USD).
        //   • assertCurrencyWhitelist: WBE1162 → refactor folds into WBE2120
        //     so the user sees the bad currency directly.
        //   • assertSingleNetwork: WBE1191 → refactor WBE2121 (mixed CITAD/NAPAS).
        // Blank values are ignored — per-row required-field checks above
        // already flagged them on the offending row.
        List<String> ccyList = rows.stream()
                .map(ParsedTransferLine::trnfCcyCd).toList();
        uploadConsistencyValidator.assertSingleCurrency(ccyList);
        uploadConsistencyValidator.assertCurrencyWhitelist(ccyList);
        uploadConsistencyValidator.assertSingleNetwork(rows.stream()
                .map(ParsedTransferLine::antDivCd).toList());

        int totCnt = rows.size();
        log.info("feature=internal-transfer parse-excel totCnt={} sucCnt={} errCnt={}",
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

    /**
     * Row 3 of the workbook MUST carry the canonical column headers
     * for one of EN / KO / VI. Each cell is matched per-language by
     * prefix (so trailing whitespace from the template's bold-cell
     * formatting doesn't matter). Mirrors legacy
     * {@code validDefualtHeaderInternalTransfer}.
     *
     * @throws BusinessException {@code WBE2018} when a column header
     *         doesn't match any of the three locales.
     */
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
                log.warn("feature=internal-transfer parse-excel header-mismatch col={} got={}",
                        c, trimmed);
                throw new BusinessException("WBE2018");
            }
        }
    }

    /* --------------------------------------------------------------
     *  Per-row parsing
     * -------------------------------------------------------------- */

    private record ParsedRow(ParsedTransferLine line, boolean hasError) {}

    /**
     * Single field-level error entry. Each entry pairs a FIELD_ID
     * (the cell key the frontend uses to red-highlight + show the
     * tooltip) with its cause string. The legacy wire packs the two
     * into pipe-separated `err_fld` / `err_fld_caus` arrays of
     * equal length; we mirror that shape so the frontend's existing
     * splitter (`pymn_0009_002.ts:296-298`) consumes the wire
     * verbatim — see {@link #buildErrPair}.
     */
    private record FieldError(String fieldId, String cause) {}

    private ParsedRow parseRow(Row row, DataFormatter fmt, String bankCd,
                               boolean withdrawalIsVcb) {
        String bankCodeIn  = cellString(row, 0, fmt);
        String fincMdaNo   = cellString(row, 1, fmt);
        String tranAmtIn   = cellString(row, 3, fmt);
        String trnfCcyIn   = cellString(row, 4, fmt);
        // F1 (audit task-44508): remove Vietnamese accents from Remarks BEFORE
        // validation and BEFORE persistence, mirroring legacy
        // `PymnHelper:788-789`. Non-Vietnamese / null values pass through
        // unchanged. Other special characters are preserved (validated, never
        // replaced with spaces).
        String tranUsag    = LegacyTextNormalizer.stripVietnameseTone(cellString(row, 5, fmt));
        String chartAcctCd = cellString(row, 6, fmt);
        String note        = cellString(row, 7, fmt);
        String docNo       = cellString(row, 8, fmt);

        List<FieldError> errors = new ArrayList<>();

        // Special-character validation against the BLOCKED_CHARS_RULE
        // table for the master's OUT_BANK_CD × INTR × RMKS tuple.
        // Mirrors legacy `SpecialCharValidator.validate(...)` at
        // pymn_0009_002_r001_act.jsp. Skips when the Remarks cell is
        // blank — required-field check above is the parser's job.
        if (!isBlank(tranUsag)) {
            specialCharValidator
                    .validate(bankCd, PymnType.INTERNAL_TRANSFER, FieldCd.REMARKS, tranUsag)
                    .ifPresent(msg -> errors.add(new FieldError("TRAN_USAG", msg)));
        }

        // Legacy field-length caps flagged at preview
        // (PymnHelper.checkValidStringInMax): Remarks 200, Description 200,
        // Document No. 50. Mirrors the legacy preview-time validation so the
        // user sees over-length cells before save instead of an opaque
        // constraint failure at insert time.
        if (!isBlank(tranUsag) && tranUsag.trim().length() > REMARKS_MAX_LEN) {
            errors.add(new FieldError("TRAN_USAG",
                    "Remarks must not exceed " + REMARKS_MAX_LEN + " characters"));
        }
        if (!isBlank(note) && note.trim().length() > NOTE_MAX_LEN) {
            errors.add(new FieldError("NOTE",
                    "Description must not exceed " + NOTE_MAX_LEN + " characters"));
        }
        if (!isBlank(docNo) && docNo.trim().length() > DOC_NO_MAX_LEN) {
            errors.add(new FieldError("DOC_NO",
                    "Document No. must not exceed " + DOC_NO_MAX_LEN + " characters"));
        }

        if (isBlank(fincMdaNo)) {
            errors.add(new FieldError(FLD_FINC_MDA_NO, "Deposit No. is required"));
        }

        // Bank Code resolution via TB_FIRM_BANK_CODE — length tells us
        // which lookup branch (3-char CITAD vs 6-char NAPAS).
        String resolvedBankCd = null;
        String resolvedBankNm = null;
        String resolvedAntDiv = null;
        // CITAD_BANK_CD persisted on the detail row — legacy parity
        // (PymnHelper.uploadPaymentExcel:1468): the 3-char CITAD code for a
        // CITAD row, the literal "000" for a NAPAS row, null when the bank
        // code never resolved.
        String resolvedCitadBankCd = null;
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
                // Legacy parity (PymnHelper.getBankCode + uploadPaymentExcel:
                // 1450-1468): TAR_BANK_CD persists the RESOLVED BANK_CD from
                // TB_FIRM_BANK_CODE — for NAPAS the 6-digit code (== input),
                // for CITAD the full BANK_CD the 3-digit citad_bank_cd resolves
                // to (e.g. "202" → "01202001"). CITAD_BANK_CD then carries the
                // 3-digit code for CITAD, "000" for NAPAS.
                resolvedBankCd = hit.get().getBankCd();
                resolvedBankNm = displayName(hit.get());
                resolvedCitadBankCd = "CITAD".equals(resolvedAntDiv) ? trimmed : "000";
            } else if (resolvedAntDiv.isEmpty()) {
                // Length already flagged — fall-through, value passes
                // through verbatim so the cell shows the raw input.
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
                // Legacy parity: `ValidationHelper.validateAmountOnlyPositive`
                // passes only when `amount.signum() == 1` (strictly > 0), so a
                // zero amount is rejected just like a negative one.
                if (parsed.signum() <= 0) {
                    errors.add(new FieldError(FLD_TRAN_AMT,
                            "Transfer Amount must be greater than zero"));
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

        // VCB-USD → other-bank block (legacy PymnHelper.uploadPaymentExcel:
        // 1460-1465). When the WITHDRAWAL bank is Vietcombank and the transfer
        // currency is USD, the target bank must also be Vietcombank — VCB does
        // not support USD transfers to other banks. Compared against the
        // RESOLVED target BANK_CD, exactly as legacy compares its resolved
        // TAR_BANK_CD against VCB_BANK_CD.
        if (withdrawalIsVcb && "USD".equals(trnfCcyCd)
                && !VCB_BANK_CD.equals(resolvedBankCd)) {
            errors.add(new FieldError(FLD_TRNF_CCY_CD,
                    "VCB does not support USD transfer to other banks."));
        }

        // Resolve RCPT_NM + DEPO_CCY_CD from ACCT_MSTR — Internal
        // Transfer always targets one of the company's own accounts, so
        // the recipient's holder name lives on `ACCT_MSTR.ACCT_NM` and
        // the destination currency on `ACCT_MSTR.CCY_CD`. Mirrors legacy
        // `PymnHelper.validateBankAccountInAcctMstr` (lines 613-645)
        // verbatim — the legacy query is `(biz_key, acct_no)` ONLY,
        // with no `bank_cd` predicate, because the Excel cell ships a
        // NAPAS / CITAD code (e.g. `970424`) while `ACCT_MSTR.BANK_CD`
        // stores the bank's internal 8-char code (e.g. `11704088`); the
        // two formats are incompatible so filtering by bank_cd on this
        // path produces false negatives. Behaviour:
        //   - On hit  → set RCPT_NM + DEPO_CCY_CD (legacy lines 636-639).
        //   - On miss → emit "Deposit account not registered" against
        //               FINC_MDA_NO (legacy line 631-632: WBE1194).
        // The lookup runs only when the upstream bank-code + deposit-no
        // checks both passed; otherwise the user already has a clearer
        // upstream error and the ACCT_MSTR query would be noise.
        String rcptNm    = null;
        String depoCcyCd = null;
        if (!isBlank(fincMdaNo) && !isBlank(resolvedBankCd) && errors.stream()
                .noneMatch(e -> FLD_ANT_DIV_CD.equals(e.fieldId()) || FLD_FINC_MDA_NO.equals(e.fieldId()))) {
            String bizKey = TenantContext.currentBizKey();
            Optional<AcctMstr> acctHit = acctMstrRepository
                    .findFirstByBizKeyAndAcctNo(bizKey, fincMdaNo.trim());
            if (acctHit.isPresent()) {
                AcctMstr a = acctHit.get();
                if (!isBlank(a.getAcctNm())) rcptNm = a.getAcctNm();
                if (!isBlank(a.getCcyCd())) depoCcyCd = a.getCcyCd();
            } else {
                errors.add(new FieldError(FLD_FINC_MDA_NO,
                        "Deposit account is not registered in the company's bank accounts: " + fincMdaNo));
            }
        }

        // Internal Transfer recipient name is INTENTIONALLY NOT special-char
        // validated (audit task-44508, P1 decision). Legacy
        // `PymnHelper.readExcelFromInternalTransfer` (lines 648-857) validates
        // REMARKS only; the recipient is the company's OWN account holder name,
        // system-resolved from `ACCT_MSTR.ACCT_NM` above (curated bank data, not
        // free Excel text), so legacy never special-char-validates it here.
        // Validating it (VCB rule 0028 / BIDV rule 0036) would reject
        // legacy-accepted internal transfers. The INTR/RCPT rows in
        // BLOCKED_CHARS_RULE are LEFT IN PLACE for possible future use; they are
        // simply not invoked on this path. If a PO later approves the stricter
        // behaviour, re-add the validate() call here.

        // Note — recipient-name inquiry (ckRecName) is intentionally
        // NOT wired here. Legacy parity:
        // `PymnHelper.readExcelFromInternalTransfer` (lines 648-857)
        // does not call `ckRecName` because Internal Transfer always
        // targets one of the company's OWN accounts (resolved from
        // `ACCT_MSTR.ACCT_NM` above), not a third-party recipient. The
        // bank-side `inquiryAccount` lookup is reserved for the Vendor
        // / Expense parsers where the recipient lives outside the
        // company on a contact's bank account.

        // Reporting Account validation — mirrors Vendor / Expense / Payroll
        // parsers (same legacy helper `PymnHelper.ckChartAcctCd`). When
        // the cell is non-blank we resolve the user-typed code against
        // CHART_ACCT_INFM for the current tenant and stamp BOTH
        // `chart_acct_cd` (normalised) + `chart_acct_seq_no` (resolved)
        // onto the line so the saved detail row carries the seq_no the
        // breadcrumb / picker resolver depends on. Inactive / deleted
        // codes are rejected.
        String bizKey = TenantContext.currentBizKey();
        String resolvedChartAcctSeqNo = null;
        String resolvedChartAcctCd    = null;
        if (!isBlank(chartAcctCd)) {
            String trimmedChart = chartAcctCd.trim();
            if (trimmedChart.length() > CHART_ACCT_CD_MAX_LEN) {
                errors.add(new FieldError(FLD_CHART_ACCT_CD,
                        "Reporting Account exceeds " + CHART_ACCT_CD_MAX_LEN
                                + " characters: " + trimmedChart));
            } else if (bizKey == null) {
                errors.add(new FieldError(FLD_CHART_ACCT_CD,
                        "Tenant context missing — please re-login"));
            } else {
                Optional<ChartAcctInfm> hit =
                        chartAcctRepo.findByBizKeyAndChartAcctCdAndDelYn(bizKey, trimmedChart, "N");
                if (hit.isPresent()) {
                    resolvedChartAcctCd    = hit.get().getChartAcctCd();
                    resolvedChartAcctSeqNo = hit.get().getChartAcctSeqNo();
                } else {
                    // Preserve the typed value so the preview cell shows
                    // the user what they entered. Same pattern as the
                    // bank-code branch above.
                    resolvedChartAcctCd = trimmedChart;
                    errors.add(new FieldError(FLD_CHART_ACCT_CD,
                            "Reporting Account is not registered or inactive: " + trimmedChart));
                }
            }
        }

        // errFld / errCause must be built AFTER the ACCT_MSTR check so
        // any newly-added FINC_MDA_NO error from that step is included
        // in the preview's red-cell pair list.
        String errFld   = buildErrPair(errors, FieldError::fieldId);
        String errCause = buildErrPair(errors, FieldError::cause);

        ParsedTransferLine line = new ParsedTransferLine(
                tranAmt,
                resolvedAntDiv,
                rcptNm,                   // RCPT_NM — resolved from ACCT_MSTR.ACCT_NM (legacy parity)
                resolvedBankCd,
                resolvedBankNm,
                emptyToNull(fincMdaNo),
                emptyToNull(tranUsag),
                errFld,
                errCause,
                null, null,
                emptyToNull(docNo),
                emptyToNull(note),
                resolvedCitadBankCd,         // CITAD_BANK_CD — 3-digit (CITAD) / "000" (NAPAS), legacy parity
                null, null,                  // trcoCmnm, trcoCmenm
                resolvedChartAcctSeqNo,      // CHART_ACCT_SEQ_NO — looked up from CHART_ACCT_INFM
                resolvedChartAcctCd,         // CHART_ACCT_CD — normalised to the registered code
                trnfCcyCd,
                depoCcyCd);                  // DEPO_CCY_CD — resolved from ACCT_MSTR.CCY_CD (legacy parity)
        return new ParsedRow(line, !errors.isEmpty());
    }

    private static String displayName(TbFirmBankCode b) {
        // Legacy `getBankName` returns ENG_ABRVAT_NM (e.g. "ShinhanBank")
        // when present, falling back to the long-form Vietnamese
        // BANK_NM. Last-resort fallback is the bank code itself so the
        // cell never renders blank.
        if (!isBlank(b.getEngAbrvatNm())) return b.getEngAbrvatNm();
        if (!isBlank(b.getBankNm()))      return b.getBankNm();
        return b.getBankCd();
    }

    /**
     * Build the wire `err_fld` / `err_fld_caus` payload. Both arrays
     * use {@code "|"} as the separator (legacy parity with
     * {@code pymn_0009_002.ts:296-298}); the frontend splits on
     * {@code "|"} and pairs the same index across the two arrays so
     * each FIELD_ID surfaces its own cause string on the tooltip.
     */
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
        // mirroring legacy `ExcelUtils.getCell:144` (`replaceAll("\\r|\\n","")`).
        // Tabs are preserved (legacy parity). Trim ends after stripping.
        return LegacyTextNormalizer.stripCrLf(fmt.formatCellValue(cell)).trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
