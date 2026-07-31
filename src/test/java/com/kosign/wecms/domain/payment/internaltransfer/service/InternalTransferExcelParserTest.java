package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParsedTransferLine;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import com.kosign.wecms.domain.payment.common.UploadConsistencyValidator;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartAcctInfm;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InternalTransferExcelParser}.
 *
 * <p>Builds in-memory XLSX workbooks via POI's {@code XSSFWorkbook},
 * feeds them through the parser, and asserts on the resulting
 * {@link ParseExcelResponse}: row counts, per-cell error flags +
 * paired cause strings, bank-code/name resolution via
 * {@link TbFirmBankCodeRepository}, header-row validation per locale,
 * and the rejection envelope on malformed input.
 */
class InternalTransferExcelParserTest {

    /** Real Vietnam-region NAPAS codes — mirrors the V61 demo seed. */
    private static final String NAPAS_SHINHAN = "970424";
    private static final String NAPAS_WOORI   = "970457";
    private static final String NM_SHINHAN    = "ShinhanBank";
    private static final String NM_WOORI      = "WooriBank";

    private static final String MASTER_BANK_CD = "11704000";
    private static final String TEST_BIZ_KEY   = "VN_VNM0004_000001";
    private final TbFirmBankCodeRepository repo = mock(TbFirmBankCodeRepository.class);
    private final SpecialCharValidator specialCharValidator = mock(SpecialCharValidator.class);
    private final UploadConsistencyValidator uploadConsistencyValidator = mock(UploadConsistencyValidator.class);
    private final ChartOfAccountRepository chartAcctRepository = mock(ChartOfAccountRepository.class);
    private final AcctMstrRepository acctMstrRepository = mock(AcctMstrRepository.class);
    private final OutBankCdNormalizer outBankCdNormalizer = mock(OutBankCdNormalizer.class);

    /** Build the parser with the standard mock set. Keeps the 6-arg
     *  constructor in one place so adding a collaborator is a one-line
     *  change across every test. */
    private InternalTransferExcelParser newParser() {
        return new InternalTransferExcelParser(
                repo, specialCharValidator, com.kosign.wecms.domain.payment.common.WireWidthGuards.real(),
                uploadConsistencyValidator,
                chartAcctRepository, acctMstrRepository, outBankCdNormalizer);
    }

    @BeforeEach
    void setUpTenant() {
        // Parser's deposit-account validation reads TenantContext + ACCT_MSTR.
        // Provide a tenant + a default account hit so previously-valid rows
        // keep parsing cleanly. Tests that want to exercise the "account not
        // registered" branch can override the stub with their own mock.
        TenantContext.set("testuser", "testuser", TEST_BIZ_KEY, LangCd.EN);
        when(acctMstrRepository.findFirstByBizKeyAndAcctNo(eq(TEST_BIZ_KEY), any()))
                .thenReturn(Optional.of(stubAcct()));
        // Reporting-account validation reads CHART_ACCT_INFM. Echo the
        // queried code back as a registered chart account so previously-
        // valid rows keep parsing cleanly. Tests exercising the "not
        // registered" branch use a blank reporting account instead.
        when(chartAcctRepository.findByBizKeyAndChartAcctCdAndDelYn(eq(TEST_BIZ_KEY), any(), eq("N")))
                .thenAnswer(inv -> Optional.of(stubChart(inv.getArgument(1))));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void parses_valid_row_with_known_napas_bank_code() throws Exception {
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq(NAPAS_SHINHAN)))
                .thenReturn(Optional.of(napas(NAPAS_SHINHAN, NM_SHINHAN)));

        InternalTransferExcelParser parser = newParser();
        // Column order: Bank Code | Deposit No. | Bank | Transfer Amount |
        //               Transfer Currency | Remarks | Reporting Account |
        //               Description | Document No.
        byte[] xlsx = buildWorkbook(new String[][] {
                { NAPAS_SHINHAN, "700071152854", "ShinhanBank", "100000", "VND",
                  "Internal payment 1", "2001001", "VEHICLE FEE", "1C24TPA-25861" },
        });

        ParseExcelResponse out = parser.parse(file(xlsx), MASTER_BANK_CD);

        assertThat(out.totCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(out.sucCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(out.errCnt()).isEqualByComparingTo(BigDecimal.ZERO);
        ParsedTransferLine line = out.rows().get(0);
        // G1: NAPAS TAR_BANK_CD stays the 6-digit registry BANK_CD (== input).
        assertThat(line.tarBankCd()).isEqualTo(NAPAS_SHINHAN);
        // G2: NAPAS rows persist CITAD_BANK_CD = "000" (legacy parity).
        assertThat(line.citadBankCd()).isEqualTo("000");
        // Bank display column resolves to ENG_ABRVAT_NM from
        // TB_FIRM_BANK_CODE — what the legacy `getBankName` returns
        // and what the user expects to see in column 3 of the preview.
        assertThat(line.tarBankNm()).isEqualTo(NM_SHINHAN);
        assertThat(line.antDivCd()).isEqualTo("NAPAS");
        assertThat(line.fincMdaNo()).isEqualTo("700071152854");
        assertThat(line.tranAmt()).isEqualByComparingTo("100000");
        assertThat(line.trnfCcyCd()).isEqualTo("VND");
        assertThat(line.chartAcctCd()).isEqualTo("2001001");
        assertThat(line.note()).isEqualTo("VEHICLE FEE");
        assertThat(line.docNo()).isEqualTo("1C24TPA-25861");
        assertThat(line.errFld()).isNull();
        assertThat(line.errFldCaus()).isNull();
    }

    @Test
    void parses_valid_row_with_known_citad_bank_code() throws Exception {
        // 3-char input routes through the CITAD branch. The registry row's
        // BANK_CD ("01202001") is the resolved 8-digit bank code the legacy
        // getBankCode returns; CITAD_BANK_CD ("202") is the 3-digit input.
        TbFirmBankCode citadRow = new TbFirmBankCode();
        citadRow.setBankCd("01202001");
        citadRow.setAntDivCd("CITAD");
        citadRow.setCitadBankCd("202");
        citadRow.setEngAbrvatNm("BIDV");
        when(repo.findFirstByAntDivCdAndCitadBankCd(eq("CITAD"), eq("202")))
                .thenReturn(Optional.of(citadRow));

        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbook(new String[][] {
                { "202", "700071152854", "BIDV", "50000", "VND", "", "", "", "" },
        });

        ParsedTransferLine line = parser.parse(file(xlsx), MASTER_BANK_CD).rows().get(0);
        assertThat(line.antDivCd()).isEqualTo("CITAD");
        // G1: TAR_BANK_CD = the RESOLVED registry BANK_CD, not the 3-digit input.
        assertThat(line.tarBankCd()).isEqualTo("01202001");
        // G2: CITAD_BANK_CD = the original 3-digit CITAD code.
        assertThat(line.citadBankCd()).isEqualTo("202");
        assertThat(line.tarBankNm()).isEqualTo("BIDV");
        assertThat(line.errFld()).isNull();
    }

    @Test
    void aligns_err_fld_and_err_fld_caus_pipe_separated() throws Exception {
        // Blank required fields → multiple errors on one row. The wire
        // must ship `err_fld` and `err_fld_caus` as pipe-separated
        // arrays of EQUAL length so the frontend can split by `|` and
        // map cell i to cause i.
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq(NAPAS_SHINHAN)))
                .thenReturn(Optional.of(napas(NAPAS_SHINHAN, NM_SHINHAN)));

        InternalTransferExcelParser parser = newParser();
        // Row with: known bank code, blank deposit, blank amount,
        // blank currency. Description col is non-blank so the early-
        // break doesn't trigger.
        byte[] xlsx = buildWorkbook(new String[][] {
                { NAPAS_SHINHAN, "", "", "", "", "", "", "Some note", "" },
        });

        ParsedTransferLine line = parser.parse(file(xlsx), MASTER_BANK_CD).rows().get(0);

        // Both arrays use the same separator and have matching counts.
        assertThat(line.errFld()).isNotNull();
        assertThat(line.errFldCaus()).isNotNull();
        String[] fields = line.errFld().split("\\|", -1);
        String[] causes = line.errFldCaus().split("\\|", -1);
        assertThat(fields).hasSameSizeAs(causes);
        assertThat(fields).contains("FINC_MDA_NO", "TRAN_AMT", "TRNF_CCY_CD");
        // Each FIELD_ID's cause is non-blank so the tooltip has content.
        for (String cause : causes) {
            assertThat(cause).isNotBlank();
        }
    }

    @Test
    void flags_invalid_bank_code_length() throws Exception {
        // 4-char input — neither CITAD (3) nor NAPAS (6). Parser keeps
        // the raw value on TAR_BANK_CD and flags ANT_DIV_CD.
        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbook(new String[][] {
                { "9999", "700071152854", "", "100", "VND", "", "", "", "" },
        });

        ParsedTransferLine line = parser.parse(file(xlsx), MASTER_BANK_CD).rows().get(0);

        assertThat(line.errFld()).contains("ANT_DIV_CD");
        assertThat(line.tarBankCd()).isEqualTo("9999");
    }

    @Test
    void flags_unknown_napas_bank_code() throws Exception {
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq("999999")))
                .thenReturn(Optional.empty());

        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbook(new String[][] {
                { "999999", "700071152854", "", "100", "VND", "", "", "", "" },
        });

        ParsedTransferLine line = parser.parse(file(xlsx), MASTER_BANK_CD).rows().get(0);

        assertThat(line.errFld()).contains("ANT_DIV_CD");
        assertThat(line.antDivCd()).isEqualTo("NAPAS");
        assertThat(line.tarBankCd()).isEqualTo("999999");
    }

    @Test
    void breaks_at_first_all_critical_blank_row() throws Exception {
        // Mirrors legacy `PymnHelper.readExcelFromInternalTransfer:
        // 680-683`: when a row has ALL of Bank Code + Deposit +
        // Amount + Description + Doc No. blank, parsing STOPS — every
        // row after it is ignored. Prevents the "996 errors" symptom
        // from the styled-but-empty trailing rows in the real JEX
        // template.
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq(NAPAS_SHINHAN)))
                .thenReturn(Optional.of(napas(NAPAS_SHINHAN, NM_SHINHAN)));

        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbook(new String[][] {
                { NAPAS_SHINHAN, "1111111111", "", "100", "VND", "", "", "", "" },
                { "", "", "", "", "", "", "", "", "" },
                // Row below would otherwise parse — but the all-blank
                // critical row above stops the loop entirely.
                { NAPAS_SHINHAN, "2222222222", "", "200", "VND", "", "", "", "" },
        });

        ParseExcelResponse out = parser.parse(file(xlsx), MASTER_BANK_CD);
        assertThat(out.totCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(out.sucCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(out.errCnt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejects_workbook_with_wrong_headers() throws Exception {
        // Headers that don't match any of EN / KO / VI → WBE2018.
        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbookWithCustomHeaders(
                new String[] { "Wrong1", "Wrong2", "Wrong3", "Wrong4", "Wrong5",
                               "Wrong6", "Wrong7", "Wrong8", "Wrong9" },
                new String[][] { });

        assertThatThrownBy(() -> parser.parse(file(xlsx), MASTER_BANK_CD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE2018");
    }

    @Test
    void accepts_korean_headers() throws Exception {
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq(NAPAS_WOORI)))
                .thenReturn(Optional.of(napas(NAPAS_WOORI, NM_WOORI)));

        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbookWithCustomHeaders(
                new String[] { "은행코드 *", "입금 계좌번호 *", "은행",
                               "이체 금액 *", "이체통화 *",
                               "적요 (200자)", "보고서 계정",
                               "내용", "문서번호" },
                new String[][] {
                        { NAPAS_WOORI, "700071152854", "", "100", "VND",
                          "", "", "", "" },
                });

        ParseExcelResponse out = parser.parse(file(xlsx), MASTER_BANK_CD);
        assertThat(out.totCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(out.errCnt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void throws_WBE2017_for_non_excel_bytes() {
        InternalTransferExcelParser parser = newParser();
        MockMultipartFile bad = new MockMultipartFile(
                "file_data", "garbage.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not a real xlsx".getBytes());

        assertThatThrownBy(() -> parser.parse(bad, MASTER_BANK_CD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE2017");
    }

    @Test
    void flags_vcb_usd_transfer_to_other_bank() throws Exception {
        // G4: withdrawal bank is VCB (normalize → 970436) + transfer USD +
        // target is NOT VCB (Shinhan 970424) → row flagged, legacy parity
        // (PymnHelper.uploadPaymentExcel:1460-1465).
        when(outBankCdNormalizer.normalize(MASTER_BANK_CD)).thenReturn("970436");
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq(NAPAS_SHINHAN)))
                .thenReturn(Optional.of(napas(NAPAS_SHINHAN, NM_SHINHAN)));

        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbook(new String[][] {
                { NAPAS_SHINHAN, "700071152854", "", "100", "USD", "", "", "", "" },
        });

        ParseExcelResponse out = parser.parse(file(xlsx), MASTER_BANK_CD);
        ParsedTransferLine line = out.rows().get(0);
        assertThat(out.errCnt()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(line.errFld()).contains("TRNF_CCY_CD");
        assertThat(line.errFldCaus()).contains("VCB does not support USD transfer to other banks.");
    }

    @Test
    void allows_vcb_usd_transfer_to_vcb() throws Exception {
        // G4: withdrawal VCB + USD + target IS VCB (970436) → no VCB block.
        when(outBankCdNormalizer.normalize(MASTER_BANK_CD)).thenReturn("970436");
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq("970436")))
                .thenReturn(Optional.of(napas("970436", "Vietcombank")));

        InternalTransferExcelParser parser = newParser();
        byte[] xlsx = buildWorkbook(new String[][] {
                { "970436", "700071152854", "", "100", "USD", "", "", "", "" },
        });

        ParsedTransferLine line = parser.parse(file(xlsx), MASTER_BANK_CD).rows().get(0);
        assertThat(line.errFld()).isNull();
        assertThat(line.tarBankCd()).isEqualTo("970436");
    }

    @Test
    void flags_overlength_remarks_note_and_doc_no() throws Exception {
        // G5: Remarks > 200, Description > 200, Document No. > 50 are flagged
        // at preview (legacy PymnHelper.checkValidStringInMax parity).
        when(repo.findByAntDivCdAndBankCd(eq("NAPAS"), eq(NAPAS_SHINHAN)))
                .thenReturn(Optional.of(napas(NAPAS_SHINHAN, NM_SHINHAN)));

        InternalTransferExcelParser parser = newParser();
        String remarks201 = "R".repeat(201);
        String note201    = "N".repeat(201);
        String docNo51    = "D".repeat(51);
        byte[] xlsx = buildWorkbook(new String[][] {
                { NAPAS_SHINHAN, "700071152854", "", "100", "VND",
                  remarks201, "", note201, docNo51 },
        });

        ParsedTransferLine line = parser.parse(file(xlsx), MASTER_BANK_CD).rows().get(0);
        assertThat(line.errFld()).contains("TRAN_USAG", "NOTE", "DOC_NO");
    }

    /* ----------------------------------------------------------------
     *  Helpers
     * ---------------------------------------------------------------- */

    private static TbFirmBankCode napas(String bankCd, String engAbrvatNm) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setBankCd(bankCd);
        b.setAntDivCd("NAPAS");
        b.setEngAbrvatNm(engAbrvatNm);
        return b;
    }

    private static AcctMstr stubAcct() {
        AcctMstr a = new AcctMstr();
        a.setAcctNm("STUB ACCOUNT");
        a.setCcyCd("VND");
        return a;
    }

    private static ChartAcctInfm stubChart(String chartAcctCd) {
        ChartAcctInfm c = new ChartAcctInfm();
        c.setChartAcctCd(chartAcctCd);
        c.setChartAcctSeqNo("1");
        return c;
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file_data", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    /**
     * Build an XLSX with the canonical EN header row + supplied data
     * rows. The 4 leading rows (title / blank / descriptions /
     * headers) match the JEX template layout exactly so the parser's
     * {@code FIRST_DATA_ROW = 4} contract is exercised.
     */
    private static byte[] buildWorkbook(String[][] rows) throws Exception {
        return buildWorkbookWithCustomHeaders(
                new String[] {
                        "Bank Code *", "Deposit No.*", "Bank",
                        "Transfer Amount *", "Transfer Currency *",
                        "Remarks (200 letters)", "Reporting Account",
                        "Description", "Document No.",
                },
                rows);
    }

    private static byte[] buildWorkbookWithCustomHeaders(
            String[] headers, String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("Internal Transfer");
            sheet.createRow(1);  // blank
            Row descriptions = sheet.createRow(2);
            String[] descs = {
                    "Enter the bank code", "Enter the deposit account",
                    "Bank name", "Enter the amount", "Enter the currency",
                    "Enter the remarks", "Enter reporting account",
                    "Enter the description", "Enter the document number",
            };
            for (int c = 0; c < descs.length; c++) {
                descriptions.createCell(c).setCellValue(descs[c]);
            }
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
