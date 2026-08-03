package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.common.UploadConsistencyValidator;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParseExcelResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ParsedTransferLine;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PayrollPaymentExcelParser}. Focus: G5 (row-cap fix —
 * reject oversized files instead of silently truncating) plus a CITAD
 * happy-path and the blank-row break, the pieces the upload audit flagged as
 * untested. Builds the payroll 8-column XLSX template in memory.
 */
class PayrollPaymentExcelParserTest {

    private static final String TEST_BIZ_KEY = "VN_VNM0004_000001";
    private static final String WITHDRAWAL_BANK_CD = "11704004";
    private static final String CITAD_SHINHAN     = "616";
    private static final String CITAD_SHINHAN_CD6 = "01616001";

    private final TbFirmBankCodeRepository firmBankCodeRepo = mock(TbFirmBankCodeRepository.class);
    private final ChartOfAccountRepository chartAcctRepo    = mock(ChartOfAccountRepository.class);
    private final SpecialCharValidator     specialCharValidator = mock(SpecialCharValidator.class);
    private final UploadConsistencyValidator uploadConsistencyValidator = mock(UploadConsistencyValidator.class);

    private PayrollPaymentExcelParser newParser() {
        return new PayrollPaymentExcelParser(
                firmBankCodeRepo, chartAcctRepo, specialCharValidator,
                com.kosign.wecms.domain.payment.common.WireWidthGuards.real(), uploadConsistencyValidator);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set("testuser", "testuser", TEST_BIZ_KEY, LangCd.EN);
        // Special-char checks are inert in tests (no BLOCKED_CHARS_RULE seed).
        when(specialCharValidator.validate(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        // CITAD 616 resolves to a registered headquarters row.
        when(firmBankCodeRepo.findFirstByAntDivCdAndCitadBankCd("CITAD", CITAD_SHINHAN))
                .thenReturn(Optional.of(citad(CITAD_SHINHAN_CD6, CITAD_SHINHAN, "ShinhanBank")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- G5 — row cap: reject, never silently truncate -------------------

    @Test
    void rejects_when_data_rows_exceed_max() throws Exception {
        // 1001 non-blank data rows → over the 1000 cap → reject (the old code
        // silently dropped rows 501+ via Math.min).
        assertThatThrownBy(() ->
                newParser().parse(file(buildWorkbook(1001)), WITHDRAWAL_BANK_CD))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("WBE_PAYROLL_TOO_MANY_ROWS");
    }

    @Test
    void accepts_exactly_max_rows() throws Exception {
        ParseExcelResponse out = newParser().parse(file(buildWorkbook(1000)), WITHDRAWAL_BANK_CD);
        assertThat(out.rows()).hasSize(1000);
    }

    // ---- Parser sanity ----------------------------------------------------

    @Test
    void citad_row_parses_typed_code_without_error() throws Exception {
        // Parser keeps the TYPED code (resolution to 6-digit happens at save, G2).
        ParsedTransferLine line = newParser().parse(
                file(buildWorkbook(1)), WITHDRAWAL_BANK_CD).rows().get(0);

        assertThat(line.errFld()).isNull();
        assertThat(line.antDivCd()).isEqualTo("CITAD");
        assertThat(line.tarBankCd()).isEqualTo(CITAD_SHINHAN);
    }

    @Test
    void skips_blank_row_and_keeps_parsing_later_rows() throws Exception {
        // G6 (legacy parity): row 4 = data, row 5 = all-blank, row 6 = data.
        // The blank row is SKIPPED (not a break), so BOTH data rows parse.
        ParseExcelResponse out = newParser().parse(
                file(buildWorkbookWithBlankSecondRow()), WITHDRAWAL_BANK_CD);
        assertThat(out.rows()).hasSize(2);
    }

    /* ----------------------------------------------------------------
     *  Workbook builders + stubs
     * ---------------------------------------------------------------- */

    private static TbFirmBankCode citad(String bankCd6, String citadBankCd, String engAbrvatNm) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setBankCd(bankCd6);
        b.setCitadBankCd(citadBankCd);
        b.setAntDivCd("CITAD");
        b.setEngAbrvatNm(engAbrvatNm);
        return b;
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file_data", "WeCMS_Payroll_Payment_upload_EN.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    /** Payroll 8-column header (matches the live template + parser prefixes). */
    private static void writeHeader(Sheet sheet) {
        sheet.createRow(0).createCell(0).setCellValue("Payroll");
        sheet.createRow(1);  // blank
        sheet.createRow(2);  // hint row (unused by the parser)
        String[] headers = {
                "Bank Code*", "Deposit Bank", "Deposit Account No.*",
                "Recipient Name *", "Transfer Amount *", "Transfer Currency *",
                "Remarks (max 150 letters)", "Reporting Account",
        };
        Row header = sheet.createRow(3);
        for (int c = 0; c < headers.length; c++) {
            header.createCell(c).setCellValue(headers[c]);
        }
    }

    private static void writeDataRow(Sheet sheet, int r) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(CITAD_SHINHAN);     // Bank Code
        row.createCell(1).setCellValue("");                // Deposit Bank (auto)
        row.createCell(2).setCellValue("700071152854");    // Deposit Account No.
        row.createCell(3).setCellValue("NNT dang hoat dong"); // Recipient Name
        row.createCell(4).setCellValue("100");             // Transfer Amount
        row.createCell(5).setCellValue("VND");             // Transfer Currency
        row.createCell(6).setCellValue("");                // Remarks
        row.createCell(7).setCellValue("");                // Reporting Account
    }

    private static byte[] buildWorkbook(int dataRows) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payroll");
            writeHeader(sheet);
            for (int i = 0; i < dataRows; i++) {
                writeDataRow(sheet, 4 + i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] buildWorkbookWithBlankSecondRow() throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payroll");
            writeHeader(sheet);
            writeDataRow(sheet, 4);
            sheet.createRow(5);                 // all-blank → skipped (G6)
            writeDataRow(sheet, 6);             // still parsed after the blank
            wb.write(out);
            return out.toByteArray();
        }
    }
}
