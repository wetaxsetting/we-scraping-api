package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.firmbanking.FirmBankingInquiryService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import com.kosign.wecms.domain.payment.common.UploadConsistencyValidator;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParsedTransferLine;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoAcctInfo;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoAcctInfoRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpensePaymentExcelParser} — bank-code resolution
 * parity (audit G2 + G3) and the VCB-USD cross-bank guard (audit G6).
 *
 * <p>Mirrors the sibling {@code VendorPaymentExcelParserTest} infra: builds
 * an in-memory XLSX with the Expense template's 10-column layout (Contact
 * Code at index 7) and feeds it through the parser. The contact / deposit
 * happy path is stubbed so each test isolates the behaviour under test.
 */
class ExpensePaymentExcelParserTest {

    private static final String TEST_BIZ_KEY = "VN_VNM0004_000001";

    /** Real Vietnam-region NAPAS codes (V61 demo seed). */
    private static final String NAPAS_VCB     = "970436";   // Vietcombank
    private static final String NAPAS_SHINHAN = "970424";   // other bank

    /** A Shinhan CITAD code → its 6-digit BANK_CD (legacy getBankCode). */
    private static final String CITAD_SHINHAN     = "616";
    private static final String CITAD_SHINHAN_CD6 = "01616001";

    /** Internal BANK_CD handed to the parser as the withdrawal bank. The
     *  normalizer is mocked, so this value only needs to be stable. */
    private static final String WITHDRAWAL_BANK_CD = "11704004";

    private static final String CONTACT_CODE = "C001";
    private static final String TRCO_KEY     = "TK001";
    private static final String DEPOSIT_NO   = "700071152854";

    private final TbFirmBankCodeRepository firmBankCodeRepo = mock(TbFirmBankCodeRepository.class);
    private final TrcoInfmRepository       contactRepo      = mock(TrcoInfmRepository.class);
    private final TrcoAcctInfoRepository   contactAcctRepo  = mock(TrcoAcctInfoRepository.class);
    private final ChartOfAccountRepository chartAcctRepo    = mock(ChartOfAccountRepository.class);
    private final SpecialCharValidator     specialCharValidator = mock(SpecialCharValidator.class);
    private final FirmBankingInquiryService firmBankingInquiryService = mock(FirmBankingInquiryService.class);
    private final UploadConsistencyValidator uploadConsistencyValidator = mock(UploadConsistencyValidator.class);
    private final OutBankCdNormalizer      outBankCdNormalizer = mock(OutBankCdNormalizer.class);

    /** Constructor arg order matches the @RequiredArgsConstructor field
     *  order in {@link ExpensePaymentExcelParser}. */
    private ExpensePaymentExcelParser newParser() {
        return new ExpensePaymentExcelParser(
                firmBankCodeRepo, contactRepo, contactAcctRepo, chartAcctRepo,
                specialCharValidator, com.kosign.wecms.domain.payment.common.WireWidthGuards.real(),
                firmBankingInquiryService,
                uploadConsistencyValidator, outBankCdNormalizer);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set("testuser", "testuser", TEST_BIZ_KEY, LangCd.EN);
        // Contact Code (col 7) resolves to a registered contact.
        when(contactRepo.findActiveByBizKeyAndHandTrcoKey(TEST_BIZ_KEY, CONTACT_CODE))
                .thenReturn(Optional.of(contact()));
        // Withdrawal bank is non-VCB by default (the VCB guard is opt-in
        // per test). Folds the internal BANK_CD to a non-VCB NAPAS code.
        when(outBankCdNormalizer.normalize(WITHDRAWAL_BANK_CD)).thenReturn(NAPAS_SHINHAN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- G2 / G3 — bank-code resolution ----------------------------------

    @Test
    void napas_row_keeps_6digit_tar_bank_cd_and_citad_000() throws Exception {
        // NAPAS target Shinhan (970424): TAR_BANK_CD passes through as the
        // 6-digit NAPAS code; CITAD_BANK_CD sentinel "000".
        when(firmBankCodeRepo.findByAntDivCdAndBankCd("NAPAS", NAPAS_SHINHAN))
                .thenReturn(Optional.of(napas(NAPAS_SHINHAN, "ShinhanBank")));
        when(contactAcctRepo.findByBizKeyAndTrcoKeyAndAcctNo(TEST_BIZ_KEY, TRCO_KEY, DEPOSIT_NO))
                .thenReturn(List.of(napasAcct(NAPAS_SHINHAN)));

        ParsedTransferLine line = newParser().parse(
                file(buildWorkbook(row(NAPAS_SHINHAN, "VND"))), WITHDRAWAL_BANK_CD)
                .rows().get(0);

        assertThat(line.errFld()).isNull();
        assertThat(line.tarBankCd()).isEqualTo(NAPAS_SHINHAN);
        assertThat(line.citadBankCd()).isEqualTo("000");
    }

    @Test
    void citad_row_resolves_tar_bank_cd_to_6digit_and_keeps_3digit_citad() throws Exception {
        // CITAD target Shinhan (616): TAR_BANK_CD resolves to the 6-digit
        // BANK_CD (01616001); CITAD_BANK_CD keeps the typed 3-digit code.
        when(firmBankCodeRepo.findFirstByAntDivCdAndCitadBankCd("CITAD", CITAD_SHINHAN))
                .thenReturn(Optional.of(citad(CITAD_SHINHAN_CD6, CITAD_SHINHAN, "ShinhanBank")));
        when(contactAcctRepo.findByBizKeyAndTrcoKeyAndAcctNo(TEST_BIZ_KEY, TRCO_KEY, DEPOSIT_NO))
                .thenReturn(List.of(citadAcct(CITAD_SHINHAN)));

        ParsedTransferLine line = newParser().parse(
                file(buildWorkbook(row(CITAD_SHINHAN, "VND"))), WITHDRAWAL_BANK_CD)
                .rows().get(0);

        assertThat(line.errFld()).isNull();
        assertThat(line.tarBankCd()).isEqualTo(CITAD_SHINHAN_CD6);
        assertThat(line.citadBankCd()).isEqualTo(CITAD_SHINHAN);
    }

    // ---- G6 — VCB-USD cross-bank guard -----------------------------------

    @Test
    void flags_vcb_usd_transfer_to_other_bank() throws Exception {
        // Withdrawal = VCB (normalize → 970436), transfer USD, target Shinhan
        // (970424 ≠ VCB) → row flagged on TRNF_CCY_CD (legacy parity).
        when(outBankCdNormalizer.normalize(WITHDRAWAL_BANK_CD)).thenReturn(NAPAS_VCB);
        when(firmBankCodeRepo.findByAntDivCdAndBankCd("NAPAS", NAPAS_SHINHAN))
                .thenReturn(Optional.of(napas(NAPAS_SHINHAN, "ShinhanBank")));
        when(contactAcctRepo.findByBizKeyAndTrcoKeyAndAcctNo(TEST_BIZ_KEY, TRCO_KEY, DEPOSIT_NO))
                .thenReturn(List.of(napasAcct(NAPAS_SHINHAN)));

        ParseExcelResponse out = newParser().parse(
                file(buildWorkbook(row(NAPAS_SHINHAN, "USD"))), WITHDRAWAL_BANK_CD);

        ParsedTransferLine line = out.rows().get(0);
        assertThat(out.errCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(line.errFld()).contains("TRNF_CCY_CD");
        assertThat(line.errFldCaus())
                .contains("VCB does not support USD transfer to other banks.");
    }

    @Test
    void allows_vcb_usd_transfer_to_vcb() throws Exception {
        // Withdrawal = VCB, transfer USD, target IS VCB (970436) → no block.
        when(outBankCdNormalizer.normalize(WITHDRAWAL_BANK_CD)).thenReturn(NAPAS_VCB);
        when(firmBankCodeRepo.findByAntDivCdAndBankCd("NAPAS", NAPAS_VCB))
                .thenReturn(Optional.of(napas(NAPAS_VCB, "Vietcombank")));
        when(contactAcctRepo.findByBizKeyAndTrcoKeyAndAcctNo(TEST_BIZ_KEY, TRCO_KEY, DEPOSIT_NO))
                .thenReturn(List.of(napasAcct(NAPAS_VCB)));

        ParsedTransferLine line = newParser().parse(
                file(buildWorkbook(row(NAPAS_VCB, "USD"))), WITHDRAWAL_BANK_CD)
                .rows().get(0);

        assertThat(line.errFld()).isNull();
        assertThat(line.trnfCcyCd()).isEqualTo("USD");
    }

    /* ----------------------------------------------------------------
     *  Stubs
     * ---------------------------------------------------------------- */

    private static TrcoInfm contact() {
        TrcoInfm c = new TrcoInfm();
        c.setTrcoKey(TRCO_KEY);
        c.setTrcoCmnm("Expense Co");
        c.setTrcoCmenm("Expense Co");
        return c;
    }

    private static TbFirmBankCode napas(String bankCd, String engAbrvatNm) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setBankCd(bankCd);
        b.setAntDivCd("NAPAS");
        b.setEngAbrvatNm(engAbrvatNm);
        return b;
    }

    private static TbFirmBankCode citad(String bankCd6, String citadBankCd, String engAbrvatNm) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setBankCd(bankCd6);
        b.setCitadBankCd(citadBankCd);
        b.setAntDivCd("CITAD");
        b.setEngAbrvatNm(engAbrvatNm);
        return b;
    }

    private static TrcoAcctInfo napasAcct(String napasBankCd) {
        TrcoAcctInfo a = new TrcoAcctInfo();
        a.setNapasBankCd(napasBankCd);
        return a;
    }

    private static TrcoAcctInfo citadAcct(String citadBankCd) {
        TrcoAcctInfo a = new TrcoAcctInfo();
        a.setCitadBankCd(citadBankCd);
        return a;
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file_data", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    /** One Expense data row: Bank Code | Deposit | Bank | Amount | Currency |
     *  Remarks | Reporting Acct | Contact Code | Description | Document No. */
    private static String[][] row(String targetBankCode, String ccy) {
        return new String[][] {
                { targetBankCode, DEPOSIT_NO, "", "100", ccy, "", "",
                  CONTACT_CODE, "", "" },
        };
    }

    private static byte[] buildWorkbook(String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("Expense Payment");
            sheet.createRow(1);  // blank
            sheet.createRow(2);  // descriptions (unused by the parser)
            // Expense 10-column EN header (Contact Code at index 7).
            String[] headers = {
                    "Bank Code *", "Deposit No.*", "Bank",
                    "Transfer Amount *", "Transfer Currency *",
                    "Remarks (200 letters)", "Reporting Account",
                    "Contact Code *", "Description", "Document No.",
            };
            Row header = sheet.createRow(3);
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row dataRow = sheet.createRow(r + 4);
                String[] values = rows[r];
                for (int c = 0; c < values.length; c++) {
                    dataRow.createCell(c).setCellValue(values[c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
