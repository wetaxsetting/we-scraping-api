package com.kosign.wecms.domain.payment.vendorpayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParsedTransferLine;
import com.kosign.wecms.domain.payment.common.LegacyTextNormalizer;
import com.kosign.wecms.domain.payment.validation.FieldCd;
import com.kosign.wecms.domain.payment.validation.PymnType;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAcctInfoRepository;
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
 * <p>Per-row validation chain (mirrors legacy
 * {@code PymnHelper.readExcel:490-566}):
 * <ol>
 *   <li>Resolve {@code HAND_TRCO_KEY} (col 7) against
 *       {@code TRCO_INFM} via
 *       {@link TrcoInfmRepository#findActiveByBizKeyAndHandTrcoKey} &mdash;
 *       legacy {@code ckContactCode}.</li>
 *   <li>Resolve {@code FINC_MDA_NO} (col 1) against
 *       {@code TRCO_ACCT_INFO} via
 *       {@link TrcoAcctInfoRepository#findByBizKeyAndTrcoKeyAndAcctNo}
 *       for the resolved contact &mdash; legacy {@code ckDepositNo}.
 *       The matched row's bank code (NAPAS_BANK_CD or CITAD_BANK_CD,
 *       chosen by {@code ANT_DIV_CD}) must equal the Excel
 *       {@code TAR_BANK_CD}; otherwise the line errors on
 *       {@code FINC_MDA_NO} (deposit not registered) or
 *       {@code ANT_DIV_CD} (bank-code mismatch).</li>
 *   <li>For CITAD only, cross-fill {@code RCPT_NM} from
 *       {@code TRCO_ACCT_INFO.FINC_MDA_OWNER_NM} &mdash; legacy
 *       {@code getfincMdaOwnerNmFromAcctInfo}. NAPAS recipient-name
 *       lookup (legacy {@code ckRecName} via external bank service)
 *       is still deferred.</li>
 * </ol>
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
    private static final String FLD_CHART_ACCT_CD = "CHART_ACCT_CD";

    /** Database column length cap on {@code SCDUL_PYMN_SUMR_DETL.CHART_ACCT_CD}. */
    private static final int CHART_ACCT_CD_MAX_LEN = 20;

    private static final int HEADER_ROW = 3;
    private static final int FIRST_DATA_ROW = 4;
    private static final int MAX_ROWS = 500;

    private static final int CITAD_LENGTH = 3;
    private static final int NAPAS_LENGTH = 6;

    /** Vietcombank NAPAS code — legacy {@code PymnHelper.VCB_BANK_CD} (970436).
     *  VCB does not support USD transfers to other banks
     *  ({@code PymnHelper.uploadPaymentExcel:1460-1465}). Mirrors the sibling
     *  {@code InternalTransferExcelParser#VCB_BANK_CD}. */
    private static final String VCB_BANK_CD = "970436";

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
    private final TrcoAcctInfoRepository   contactAcctRepo;
    private final ChartOfAccountRepository chartAcctRepo;
    private final SpecialCharValidator     specialCharValidator;
    /** Cross-bank recipient-name verification — legacy
     *  {@code PymnHelper.ckRecName}. Calls the firm-banking inquiry API
     *  on NAPAS rows and compares the bank-returned
     *  {@code FINC_MDA_OWNER_NM} against the contact's stored owner
     *  name. Returns silently when the inquiry service has no firm-
     *  banking config OR the socket call is not yet implemented. */
    private final com.kosign.wecms.common.firmbanking.FirmBankingInquiryService
                                           firmBankingInquiryService;
    /** Cross-row consistency guard — single currency per file + single
     *  network per file + VND/USD whitelist. Mirrors legacy
     *  {@code PymnHelper.java:420-436} + {@code :1261-1297}. */
    private final com.kosign.wecms.domain.payment.common.UploadConsistencyValidator
                                           uploadConsistencyValidator;
    /** Folds the withdrawal bank code (NAPAS or internal {@code BANK_CD})
     *  to its canonical NAPAS code so the VCB-USD-to-other-bank guard can
     *  compare it against {@link #VCB_BANK_CD}. Mirrors legacy
     *  {@code PymnHelper.uploadPaymentExcel:1315-1317} + the sibling
     *  Internal Transfer parser. */
    private final com.kosign.wecms.domain.payment.common.OutBankCdNormalizer
                                           outBankCdNormalizer;

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
            // can compare against VCB. Mirrors the sibling Internal Transfer parser.
            boolean withdrawalIsVcb =
                    VCB_BANK_CD.equals(outBankCdNormalizer.normalize(bankCd));

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

                ParsedRow parsed = parseRow(row, fmt, bankCd, withdrawalIsVcb);
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

        // Cross-row consistency — legacy parity with PymnHelper
        // (WBW1160 + WBE1162 → WBE2120; WBE1191 → WBE2121).
        List<String> ccyList = rows.stream()
                .map(ParsedTransferLine::trnfCcyCd).toList();
        uploadConsistencyValidator.assertSingleCurrency(ccyList);
        uploadConsistencyValidator.assertCurrencyWhitelist(ccyList);
        uploadConsistencyValidator.assertSingleNetwork(rows.stream()
                .map(ParsedTransferLine::antDivCd).toList());

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

    private ParsedRow parseRow(Row row, DataFormatter fmt, String bankCd,
                               boolean withdrawalIsVcb) {
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
        String bizKey = TenantContext.currentBizKey();
        String resolvedTrcoKey    = null;
        String resolvedTrcoCmnm   = null;
        String resolvedTrcoCmenm  = null;
        if (isBlank(handTrcoKey)) {
            errors.add(new FieldError(FLD_HAND_TRCO_KEY, "Contact Code is required"));
        } else if (handTrcoKey.length() > 20) {
            errors.add(new FieldError(FLD_HAND_TRCO_KEY,
                    "Contact Code exceeds 20 characters: " + handTrcoKey));
        } else if (bizKey == null) {
            // Should never happen — the controller is authenticated.
            // Defensive fallback so the parser never NPEs.
            errors.add(new FieldError(FLD_HAND_TRCO_KEY,
                    "Tenant context missing — please re-login"));
        } else {
            Optional<TrcoInfm> hit =
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

        // Bank Code resolution via TB_FIRM_BANK_CODE.
        String resolvedBankCd = null;
        String resolvedBankNm = null;
        String resolvedAntDiv = "";              // empty when bank cell is blank or wrong-length
        TbFirmBankCode excelBankRow = null;       // kept for bank-identity matching below
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
                excelBankRow   = hit.get();
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

        // Deposit account validation — mirrors legacy `PymnHelper.ckDepositNo`
        // (lines 1138-1166) + the CITAD branch of `readExcel:519-565`:
        //
        //   - Find every TRCO_ACCT_INFO row for (BIZ_KEY, TRCO_KEY, ACCT_NO).
        //   - Match the row whose stored bank resolves to the same bank
        //     identity (TB_FIRM_BANK_CODE.eng_abrvat_nm) as the Excel
        //     bank code. A single bank (e.g. "ShinhanBank") has a NAPAS
        //     row (`BANK_CD=970424`) and one or more CITAD rows
        //     (`BANK_CD=01616001` etc., `CITAD_BANK_CD=616`); the
        //     contact's TRCO_ACCT_INFO row may carry whichever variant
        //     the registration picker happened to surface. Direct
        //     same-variant equality is the fast path; bank-identity
        //     resolution is the cross-variant fallback so a NAPAS-typed
        //     Excel cell can still validate against a CITAD-saved
        //     contact account (and vice versa) when both point to the
        //     same bank.
        //   - On miss: "Deposit account not registered" / "Bank Code
        //     mismatch" against the appropriate FIELD_ID.
        //   - For CITAD only, cross-fill RCPT_NM from FINC_MDA_OWNER_NM.
        //
        // Only runs when the contact + bank-code + deposit-no upstream
        // checks all passed — otherwise the user already has a clearer
        // upstream error and the lookup would be noise.
        String resolvedRcptNm = null;
        // Captured contact-side owner name for the ckRecName check below.
        // Vendor uploads carry no user-typed RCPT_NM column, so the
        // comparison source for the bank inquiry is the contact's stored
        // FINC_MDA_OWNER_NM. Populated for BOTH NAPAS and CITAD when a
        // matching contact-account row is found.
        String contactOwnerNmForCheck = null;
        if (resolvedTrcoKey != null
                && resolvedBankCd != null
                && !resolvedAntDiv.isEmpty()
                && !isBlank(fincMdaNo)) {
            List<TrcoAcctInfo> accts = contactAcctRepo.findByBizKeyAndTrcoKeyAndAcctNo(
                    bizKey, resolvedTrcoKey, fincMdaNo.trim());
            if (accts.isEmpty()) {
                errors.add(new FieldError(FLD_FINC_MDA_NO,
                        "Deposit account is not registered for this contact: " + fincMdaNo));
            } else {
                String excelIdent = bankIdentity(excelBankRow);
                TrcoAcctInfo match = null;
                for (TrcoAcctInfo a : accts) {
                    // Fast path: same-variant code equality.
                    String acctBankCd = "NAPAS".equals(resolvedAntDiv)
                            ? a.getNapasBankCd()
                            : a.getCitadBankCd();
                    if (resolvedBankCd.equals(acctBankCd)) {
                        match = a;
                        break;
                    }
                    // Cross-variant: resolve the account's stored bank
                    // (whichever of napas / citad columns is populated)
                    // back to a TB_FIRM_BANK_CODE row and compare its
                    // engAbrvatNm to the Excel side.
                    if (excelIdent != null) {
                        String acctIdent = resolveAcctBankIdentity(a);
                        if (excelIdent.equals(acctIdent)) {
                            match = a;
                            break;
                        }
                    }
                }
                if (match == null) {
                    errors.add(new FieldError(FLD_ANT_DIV_CD,
                            "Bank Code does not match the contact's registered account: " + resolvedBankCd));
                } else {
                    if ("CITAD".equals(resolvedAntDiv)
                            && !isBlank(match.getFincMdaOwnerNm())) {
                        resolvedRcptNm = match.getFincMdaOwnerNm();
                    }
                    // Capture the contact's stored owner name for the
                    // ckRecName check below — used on NAPAS rows where
                    // the bank inquiry returns the real account-holder
                    // name and we compare it against what the user has
                    // saved against that contact.
                    if (!isBlank(match.getFincMdaOwnerNm())) {
                        contactOwnerNmForCheck = match.getFincMdaOwnerNm().trim();
                    }
                }
            }
        }

        // Cross-bank recipient-name verification — legacy
        // `PymnHelper.ckRecName` (line 860). NAPAS rows call the
        // firm-banking inquiry API and compare the bank-returned
        // FINC_MDA_OWNER_NM against the contact's stored owner name.
        // CITAD is skipped (the legacy `showLookup` gate rejects it).
        // The inquiry returns Optional.empty() until the WB_00000100
        // socket call is wired, so this block is a no-op today —
        // matching legacy's "no firm-banking config" silent-skip
        // (PymnHelper.ckRecName line 1037 "contactInfo == null").
        if ("NAPAS".equals(resolvedAntDiv)
                && !isBlank(resolvedBankCd)
                && !isBlank(fincMdaNo)
                && contactOwnerNmForCheck != null) {
            // Capture into a final local for the lambda closure.
            final String contactOwnerNm = contactOwnerNmForCheck;
            firmBankingInquiryService
                    .inquiryAccount(resolvedBankCd, fincMdaNo)
                    .ifPresent(bankOwnerNm -> {
                        String fromBank = bankOwnerNm.trim();
                        if (!fromBank.isEmpty()
                                && !fromBank.equalsIgnoreCase(contactOwnerNm)) {
                            errors.add(new FieldError("RCPT_NM",
                                    "Recipient name does not match the bank's record: "
                                            + fromBank));
                        }
                    });
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

        // VCB-USD → other-bank block (legacy PymnHelper.uploadPaymentExcel:
        // 1460-1465; GAP-3). When the WITHDRAWAL bank is Vietcombank and the
        // transfer currency is USD, the target bank must also be Vietcombank —
        // VCB does not support USD transfers to other banks. Compared against
        // the resolved target bank code (resolvedBankCd), exactly as the
        // sibling Internal Transfer parser and as legacy compares its resolved
        // TAR_BANK_CD against VCB_BANK_CD. Now reachable because GAP-6 re-added
        // VCB (11704004) to the withdrawal-account picker. The dispatch-time
        // re-check (VendorDispatchExecutor → WBE2088) remains the authoritative
        // guard; this surfaces the error in the upload preview beforehand.
        if (withdrawalIsVcb && "USD".equals(trnfCcyCd)
                && !VCB_BANK_CD.equals(resolvedBankCd)) {
            errors.add(new FieldError(FLD_TRNF_CCY_CD,
                    "VCB does not support USD transfer to other banks."));
        }

        // Reporting Account validation — mirrors the inline picker on
        // the Scheduled detail page (`PATCH .../reporting-account`).
        // Excel cells carry the user-typed CHART_ACCT_CD only; we look
        // up the matching row in CHART_ACCT_INFM for the current tenant
        // and stamp BOTH `chart_acct_cd` + `chart_acct_seq_no` onto the
        // line so the saved detail row carries the seq_no the picker /
        // breadcrumb resolver depends on. Mirrors legacy
        // `PymnHelper.ckChartAcctCd` (active-only lookup; rejects
        // codes that don't exist for the tenant).
        String resolvedChartAcctSeqNo = null;
        String resolvedChartAcctCd    = null;
        if (!isBlank(chartAcctCd)) {
            String trimmed = chartAcctCd.trim();
            if (trimmed.length() > CHART_ACCT_CD_MAX_LEN) {
                errors.add(new FieldError(FLD_CHART_ACCT_CD,
                        "Reporting Account exceeds " + CHART_ACCT_CD_MAX_LEN
                                + " characters: " + trimmed));
            } else if (bizKey == null) {
                // Defensive — same guard as the contact-code branch.
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

        // F1 (audit task-44508): strip Vietnamese accents from Remarks (Excel)
        // and the CITAD cross-filled recipient name BEFORE validation and
        // persistence. Mirrors legacy `PymnHelper:445-446` (REMARKS) +
        // `:526-527` (CITAD recipient). Non-Vietnamese / null pass through.
        tranUsag       = LegacyTextNormalizer.stripVietnameseTone(tranUsag);
        resolvedRcptNm = LegacyTextNormalizer.stripVietnameseTone(resolvedRcptNm);

        // Special-character checks against BLOCKED_CHARS_RULE. Mirrors
        // legacy `PymnHelper.readExcel:451` (REMARKS / TRAN_USAG cell)
        // and `:540` (CITAD-cross-filled RCPT_NM). Bank-specific —
        // missing rule for the (bankCd, VENDOR_PAYMENT, fieldCd) tuple
        // means the call is a no-op, so this is safe before
        // BLOCKED_CHARS_RULE is seeded for Vendor.
        if (!isBlank(tranUsag)) {
            specialCharValidator
                    .validate(bankCd, PymnType.VENDOR_PAYMENT, FieldCd.REMARKS, tranUsag)
                    .ifPresent(msg -> errors.add(new FieldError("TRAN_USAG", msg)));
        }
        if (!isBlank(resolvedRcptNm)) {
            specialCharValidator
                    .validate(bankCd, PymnType.VENDOR_PAYMENT, FieldCd.RECIPIENT_NAME, resolvedRcptNm)
                    .ifPresent(msg -> errors.add(new FieldError("RCPT_NM", msg)));
        }

        String errFld   = buildErrPair(errors, FieldError::fieldId);
        String errCause = buildErrPair(errors, FieldError::cause);

        ParsedTransferLine line = new ParsedTransferLine(
                tranAmt,
                resolvedAntDiv,
                resolvedRcptNm,                // RCPT_NM — CITAD cross-fill from TRCO_ACCT_INFO.FINC_MDA_OWNER_NM
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

    /**
     * Stable cross-variant bank identifier — {@code ENG_ABRVAT_NM}
     * ("ShinhanBank") is shared between a bank's NAPAS row and every
     * CITAD branch row, so it pivots a code-mismatched contact account
     * onto the same bank as the Excel cell.
     */
    private static String bankIdentity(TbFirmBankCode b) {
        if (b == null) return null;
        String engNm = b.getEngAbrvatNm();
        if (engNm != null && !engNm.isBlank()) return engNm.trim();
        String bankNm = b.getBankNm();
        if (bankNm != null && !bankNm.isBlank()) return bankNm.trim();
        return null;
    }

    /**
     * Resolve a contact account's stored bank to its
     * {@link #bankIdentity(TbFirmBankCode)} so cross-variant matching
     * (NAPAS-typed Excel cell against a CITAD-saved contact, or vice
     * versa) works against {@code TB_FIRM_BANK_CODE}. CITAD lookup
     * tries the branch-{@code BANK_CD} lookup first ({@code 01616001}
     * style) and falls back to the canonical {@code CITAD_BANK_CD}
     * column ({@code 616} style) so both legacy save patterns resolve.
     */
    private String resolveAcctBankIdentity(TrcoAcctInfo a) {
        String napasCd = a.getNapasBankCd();
        if (napasCd != null && !napasCd.isBlank()) {
            Optional<TbFirmBankCode> hit =
                    firmBankCodeRepo.findByAntDivCdAndBankCd("NAPAS", napasCd.trim());
            if (hit.isPresent()) return bankIdentity(hit.get());
        }
        String citadCd = a.getCitadBankCd();
        if (citadCd != null && !citadCd.isBlank()) {
            String trimmed = citadCd.trim();
            Optional<TbFirmBankCode> hit =
                    firmBankCodeRepo.findByAntDivCdAndBankCd("CITAD", trimmed);
            if (hit.isPresent()) return bankIdentity(hit.get());
            hit = firmBankCodeRepo.findFirstByAntDivCdAndCitadBankCd("CITAD", trimmed);
            if (hit.isPresent()) return bankIdentity(hit.get());
        }
        return null;
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
        return LegacyTextNormalizer.stripCrLf(fmt.formatCellValue(cell)).trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
