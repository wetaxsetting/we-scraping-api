package com.kosign.wecms.domain.payment.vendorpayment.service;

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
 * Unit tests for {@link VendorPaymentExcelParser} — GAP-3 (VCB-USD
 * cross-bank guard) parity coverage.
 *
 * <p>Mirrors the sibling {@code InternalTransferExcelParserTest} infra:
 * builds an in-memory XLSX with the Vendor template's 10-column layout
 * (Contact Code at index 7) and feeds it through the parser. The
 * contact / deposit / bank-code happy path is fully stubbed so the only
 * variable under test is the VCB-USD-to-other-bank check
 * (legacy {@code PymnHelper.uploadPaymentExcel:1460-1465}).
 */
class VendorPaymentExcelParserTest {

    private static final String TEST_BIZ_KEY = "VN_VNM0004_000001";

    /** Real Vietnam-region NAPAS codes (V61 demo seed). */
    private static final String NAPAS_VCB     = "970436";   // Vietcombank
    private static final String NAPAS_SHINHAN = "970424";   // other bank
    private static final String NAPAS_WOORI   = "970457";   // other bank

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
     *  order in {@link VendorPaymentExcelParser}. */
    private VendorPaymentExcelParser newParser() {
        return new VendorPaymentExcelParser(
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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void flags_vcb_usd_transfer_to_other_bank() throws Exception {
        // Withdrawal = VCB (normalize → 970436), transfer USD, target Shinhan
        // (970424 ≠ VCB) → row flagged on TRNF_CCY_CD (legacy parity).
        stubWithdrawalBank(NAPAS_VCB);
        stubNapasTarget(NAPAS_SHINHAN, "ShinhanBank");

        ParseExcelResponse out = newParser().parse(
                file(buildWorkbook(napasRow(NAPAS_SHINHAN, "USD"))), WITHDRAWAL_BANK_CD);

        ParsedTransferLine line = out.rows().get(0);
        assertThat(out.errCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(line.errFld()).contains("TRNF_CCY_CD");
        assertThat(line.errFldCaus())
                .contains("VCB does not support USD transfer to other banks.");
    }

    @Test
    void allows_vcb_usd_transfer_to_vcb() throws Exception {
        // Withdrawal = VCB, transfer USD, target IS VCB (970436) → no VCB block,
        // row is fully clean.
        stubWithdrawalBank(NAPAS_VCB);
        stubNapasTarget(NAPAS_VCB, "Vietcombank");

        ParsedTransferLine line = newParser().parse(
                file(buildWorkbook(napasRow(NAPAS_VCB, "USD"))), WITHDRAWAL_BANK_CD)
                .rows().get(0);

        assertThat(line.errFld()).isNull();
        assertThat(line.errFldCaus()).isNull();
        assertThat(line.trnfCcyCd()).isEqualTo("USD");
    }

    @Test
    void non_vcb_withdrawal_usd_to_other_bank_is_unaffected() throws Exception {
        // Withdrawal = NON-VCB (normalize → Shinhan 970424). Even a USD transfer
        // to a different bank (Woori 970457) must NOT trigger the VCB guard —
        // the guard is scoped strictly to a VCB withdrawal bank.
        stubWithdrawalBank(NAPAS_SHINHAN);
        stubNapasTarget(NAPAS_WOORI, "WooriBank");

        ParsedTransferLine line = newParser().parse(
                file(buildWorkbook(napasRow(NAPAS_WOORI, "USD"))), WITHDRAWAL_BANK_CD)
                .rows().get(0);

        assertThat(line.errFld()).isNull();
        assertThat(line.errFldCaus()).isNull();
    }

    /* ----------------------------------------------------------------
     *  Stubs
     * ---------------------------------------------------------------- */

    /** Mock the withdrawal bank-code normalization (parser precompute). */
    private void stubWithdrawalBank(String napasCd) {
        when(outBankCdNormalizer.normalize(WITHDRAWAL_BANK_CD)).thenReturn(napasCd);
    }

    /** Stub a NAPAS target bank-code lookup + a matching registered contact
     *  deposit account so the row's only variable is the VCB-USD guard. */
    private void stubNapasTarget(String napasCd, String engNm) {
        when(firmBankCodeRepo.findByAntDivCdAndBankCd("NAPAS", napasCd))
                .thenReturn(Optional.of(napas(napasCd, engNm)));
        when(contactAcctRepo.findByBizKeyAndTrcoKeyAndAcctNo(TEST_BIZ_KEY, TRCO_KEY, DEPOSIT_NO))
                .thenReturn(List.of(napasAcct(napasCd)));
    }

    private static TrcoInfm contact() {
        TrcoInfm c = new TrcoInfm();
        c.setTrcoKey(TRCO_KEY);
        c.setTrcoCmnm("Vendor Co");
        c.setTrcoCmenm("Vendor Co");
        return c;
    }

    private static TbFirmBankCode napas(String bankCd, String engAbrvatNm) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setBankCd(bankCd);
        b.setAntDivCd("NAPAS");
        b.setEngAbrvatNm(engAbrvatNm);
        return b;
    }

    private static TrcoAcctInfo napasAcct(String napasBankCd) {
        TrcoAcctInfo a = new TrcoAcctInfo();
        a.setNapasBankCd(napasBankCd);
        return a;
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file_data", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    /** One Vendor data row: Bank Code | Deposit | Bank | Amount | Currency |
     *  Remarks | Reporting Acct | Contact Code | Description | Document No. */
    private static String[][] napasRow(String targetNapasCd, String ccy) {
        return new String[][] {
                { targetNapasCd, DEPOSIT_NO, "", "100", ccy, "", "",
                  CONTACT_CODE, "", "" },
        };
    }

    private static byte[] buildWorkbook(String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("Vendor Payment");
            sheet.createRow(1);  // blank
            sheet.createRow(2);  // descriptions (unused by the parser)
            // Vendor 10-column EN header (Contact Code at index 7).
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
                Row row = sheet.createRow(r + 4);
                String[] values = rows[r];
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c).setCellValue(values[c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
