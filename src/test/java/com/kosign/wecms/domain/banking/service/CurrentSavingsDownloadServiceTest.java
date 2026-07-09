package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.i18n.RelativeTimeFormatter;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.AccountRow;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CurrentSavingsDownloadService}. Drives the real
 * classpath {@code .xls} templates
 * ({@code templates/current-savings-vnd/**} + {@code templates/current-savings-fcy/**})
 * through Apache POI and asserts the produced workbook — locale template
 * selection (EN/KO/VI), KM→EN fallback, filename, the column layouts
 * (Account Inquiry VND 9 / FCY 6; Transaction History 10 shared), populated +
 * empty datasets, preserved merged cells, preserved template styles, numeric
 * amount cells, and the FCY currency-scope pass-through.
 */
class CurrentSavingsDownloadServiceTest {

    private AccountService accountService;
    private TransactionService transactionService;
    private RelativeTimeFormatter relativeTimeFormatter;
    private CurrentSavingsDownloadService downloadService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        transactionService = mock(TransactionService.class);
        relativeTimeFormatter = mock(RelativeTimeFormatter.class);
        // The Last-Update humanization is exercised elsewhere; stub a fixed
        // value so the mapping assertions stay deterministic.
        when(relativeTimeFormatter.humanize(anyString())).thenReturn("1 week ago");
        when(relativeTimeFormatter.humanize(isNull())).thenReturn("");
        downloadService = new CurrentSavingsDownloadService(
                accountService, transactionService, relativeTimeFormatter);
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ----------------------------------------------------------------- */

    private void stubAccounts(List<AccountRow> rows) {
        AccountListResponse resp = new AccountListResponse(
                rows, rows.size(), "0", "0", List.of(), 0, rows.size(), 1, List.of());
        when(accountService.list(any(AccountFilter.class), isNull(), isNull(), any()))
                .thenReturn(resp);
    }

    private void stubTransactions(List<TransactionRow> rows) {
        TransactionListResponse resp = new TransactionListResponse(rows, rows.size(), "0", "0");
        when(transactionService.list(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(resp);
        when(transactionService.list(isNull(), any(), any(), any(), any(), any()))
                .thenReturn(resp);
    }

    private static AccountRow account(String bankCd, String bankNm, String acctNo, String typeNm,
                                      String nick, String bal, String realAmt,
                                      String inqStts, String updDtm) {
        return new AccountRow(bankCd + "_" + acctNo, bankCd, bankNm, acctNo, "02", typeNm,
                "bank.acct_type.02", "VND", nick, bal, realAmt, inqStts, updDtm, "N", "Y");
    }

    private static AccountRow fcyAccount(String bankCd, String bankNm, String acctNo, String ccy,
                                         String typeNm, String nick, String bal, String realAmt,
                                         String updDtm) {
        return new AccountRow(bankCd + "_" + acctNo, bankCd, bankNm, acctNo, "02", typeNm,
                "bank.acct_type.02", ccy, nick, bal, realAmt, "Y", updDtm, "N", "Y");
    }

    private static TransactionRow txn(String dt, String tm, String deposit, String withdraw,
                                      String bal, String rmrk, String chartAcct, String purpose,
                                      String vas, String contact, String confirmedBy) {
        return new TransactionRow(
                "b_a", dt, tm, "1", deposit, withdraw, bal, rmrk, "Y", confirmedBy, dt + tm,
                "N", chartAcct, "s1", purpose, vas, "tk", contact, "SHORT1", null, null, null,
                null, 0);
    }

    private static Sheet readSheet(CurrentSavingsDownloadService.GeneratedReport report) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(report.content()))) {
            return wb.getSheetAt(0);
        }
    }

    private static String cell(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return null;
        Cell c = row.getCell(colIdx);
        return c == null ? null : c.toString();
    }

    /* =================================================================
     *  Account Inquiry (VND)
     * ================================================================= */

    @Test
    void accountInquiry_en_fullColumnMapping() throws Exception {
        stubAccounts(List.of(account("970436", "VCB - Vietcombank", "700005285280",
                "Current", "CÔNG TY WEBCASH", "1659685693", "1657827269", "Y", "20260624000000")));

        var report = downloadService.generateAccountInquiry("EN");
        assertThat(report.fileName()).isEqualTo("Account Inquiry (VND).xls");

        Sheet sheet = readSheet(report);
        // Template title + header preserved.
        assertThat(cell(sheet, 0, 0)).isEqualTo("Account Inquiry (VND)");
        assertThat(cell(sheet, 3, 0)).isEqualTo("Bank");
        assertThat(cell(sheet, 3, 8)).isEqualTo("Results");
        // Data row starts at index 4.
        assertThat(cell(sheet, 4, 0)).isEqualTo("VCB - Vietcombank");
        assertThat(cell(sheet, 4, 1)).isEqualTo("700005285280");
        assertThat(cell(sheet, 4, 2)).isEqualTo("Current");
        assertThat(cell(sheet, 4, 3)).isEqualTo("CÔNG TY WEBCASH");
        assertThat(cell(sheet, 4, 6)).isEqualTo("");            // VAS Account Name blank
        assertThat(cell(sheet, 4, 7)).isEqualTo("1 week ago");  // Last Update humanized
        assertThat(cell(sheet, 4, 8)).isEqualTo("Y");           // Results
    }

    @Test
    void accountInquiry_balanceCellsAreNumeric() throws Exception {
        stubAccounts(List.of(account("970436", "VCB", "111", "Current", "Nick",
                "1659685693", "1657827269", "Y", "20260624000000")));
        Sheet sheet = readSheet(downloadService.generateAccountInquiry("EN"));
        Cell balance = sheet.getRow(4).getCell(4);
        Cell available = sheet.getRow(4).getCell(5);
        assertThat(balance.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(balance.getNumericCellValue()).isEqualTo(1659685693d);
        assertThat(available.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(available.getNumericCellValue()).isEqualTo(1657827269d);
    }

    @Test
    void accountInquiry_bankNameFallsBackToCodeWhenBlank() throws Exception {
        stubAccounts(List.of(account("88886000", null, "111", "Current", "Nick",
                "0", "0", "N", null)));
        Sheet sheet = readSheet(downloadService.generateAccountInquiry("EN"));
        assertThat(cell(sheet, 4, 0)).isEqualTo("88886000");
        // Null updDtm → humanize("") → "" (stubbed).
        assertThat(cell(sheet, 4, 7)).isEqualTo("");
    }

    @Test
    void accountInquiry_ko_selectsKoreanTemplate() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry("KO");
        assertThat(report.fileName()).isEqualTo("계좌조회 (VND).xls");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("계좌조회(VND)");
    }

    @Test
    void accountInquiry_vi_selectsVietnameseTemplate() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry("VI");
        assertThat(report.fileName()).isEqualTo("Tra cứu tài khoản (VND).xls");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Tra cứu tài khoản (VND)");
    }

    @Test
    void accountInquiry_km_fallsBackToEn() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry("KM");
        assertThat(report.fileName()).isEqualTo("Account Inquiry (VND).xls");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Account Inquiry (VND)");
    }

    @Test
    void accountInquiry_blankLang_usesJwtLangCd() throws Exception {
        TenantContext.clear();
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.VI);
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry(null);
        assertThat(report.fileName()).isEqualTo("Tra cứu tài khoản (VND).xls");
    }

    @Test
    void accountInquiry_emptyDataset_producesReadableWorkbookWithNoDataRows() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry("EN");
        Sheet sheet = readSheet(report); // opens without error
        assertThat(cell(sheet, 0, 0)).isEqualTo("Account Inquiry (VND)");
        // No data written at the first data row (index 4).
        assertThat(sheet.getRow(4) == null || cell(sheet, 4, 0) == null || cell(sheet, 4, 0).isEmpty())
                .isTrue();
    }

    @Test
    void accountInquiry_preservesMergedTitleAndDataCellStyle() throws Exception {
        stubAccounts(List.of(account("970436", "VCB", "111", "Current", "Nick",
                "100", "50", "Y", "20260624000000")));
        Sheet sheet = readSheet(downloadService.generateAccountInquiry("EN"));
        // Merged title region A1:I2 preserved.
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A1:I2")));
        // Written data cell carries a (template) cell style.
        assertThat(sheet.getRow(4).getCell(0).getCellStyle()).isNotNull();
        // Template column widths preserved (non-default).
        assertThat(sheet.getColumnWidth(0)).isGreaterThan(0);
    }

    @Test
    void accountInquiry_vnd_usesVndCurrencyFilter() {
        ArgumentCaptor<AccountFilter> captor = ArgumentCaptor.forClass(AccountFilter.class);
        stubAccounts(List.of());
        downloadService.generateAccountInquiry("EN");
        verify(accountService).list(captor.capture(), isNull(), isNull(), any());
        assertThat(captor.getValue().currency()).isEqualTo("VND");
    }

    /* =================================================================
     *  Account Inquiry (FCY)
     * ================================================================= */

    @Test
    void accountInquiryFcy_en_fullColumnMapping() throws Exception {
        stubAccounts(List.of(fcyAccount("11704020", "WOORI BANK", "2308", "USD",
                "Current", "HAENGSUNG USD", "12931", "12000", "20260624000000")));

        var report = downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, "FCY", "EN");
        assertThat(report.fileName()).isEqualTo("Account Inquiry (FCY).xls");

        Sheet sheet = readSheet(report);
        // Template title + header preserved (6-column FCY layout A–F).
        assertThat(cell(sheet, 0, 0)).isEqualTo("Account Inquiry (FCY)");
        assertThat(cell(sheet, 3, 0)).isEqualTo("Bank");
        assertThat(cell(sheet, 3, 5)).isEqualTo("Last Update");
        // Data row starts at index 4. Bank cell is the composite.
        assertThat(cell(sheet, 4, 0)).isEqualTo("WOORI BANK  ·  USD Current / 2308");
        assertThat(cell(sheet, 4, 1)).isEqualTo("HAENGSUNG USD");
        assertThat(cell(sheet, 4, 4)).isEqualTo("");            // VAS Account Name blank
        assertThat(cell(sheet, 4, 5)).isEqualTo("1 week ago");  // Last Update humanized
        // Balance / Available Balance numeric.
        Cell balance = sheet.getRow(4).getCell(2);
        Cell available = sheet.getRow(4).getCell(3);
        assertThat(balance.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(balance.getNumericCellValue()).isEqualTo(12931d);
        assertThat(available.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(available.getNumericCellValue()).isEqualTo(12000d);
    }

    @Test
    void accountInquiryFcy_ko_selectsKoreanTemplate() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, null, "KO");
        assertThat(report.fileName()).isEqualTo("계좌조회 (FCY).xls");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("계좌조회(FCY)");
        assertThat(cell(readSheet(report), 3, 0)).isEqualTo("은행명");
    }

    @Test
    void accountInquiryFcy_vi_selectsVietnameseTemplate() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, null, "VI");
        assertThat(report.fileName()).isEqualTo("Tra cứu tài khoản (FCY).xls");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Tra cứu tài khoản (FCY)");
    }

    @Test
    void accountInquiryFcy_km_fallsBackToEn() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, null, "KM");
        assertThat(report.fileName()).isEqualTo("Account Inquiry (FCY).xls");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Account Inquiry (FCY)");
    }

    @Test
    void accountInquiryFcy_emptyDataset_producesReadableWorkbook() throws Exception {
        stubAccounts(List.of());
        var report = downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, "FCY", "EN");
        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 0, 0)).isEqualTo("Account Inquiry (FCY)");
        assertThat(sheet.getRow(4) == null || cell(sheet, 4, 0) == null || cell(sheet, 4, 0).isEmpty())
                .isTrue();
    }

    @Test
    void accountInquiryFcy_preservesMergedTitleAndStyleAndWidths() throws Exception {
        stubAccounts(List.of(fcyAccount("11704020", "WOORI", "2308", "USD",
                "Current", "Nick", "100", "50", "20260624000000")));
        Sheet sheet = readSheet(downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, "FCY", "EN"));
        // Merged title region A1:F2 preserved (FCY 6-column layout).
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A1:F2")));
        assertThat(sheet.getRow(4).getCell(0).getCellStyle()).isNotNull();
        assertThat(sheet.getColumnWidth(0)).isGreaterThan(0);
    }

    /**
     * Border-fidelity regression (the 2026-07-01 report): the section-label
     * row immediately above the header (Excel row 3 → index 2, merged
     * {@code A3:F3}) must carry <b>no</b> borders, while the table header
     * (Excel row 4 → index 3) must keep its borders — exactly as the supplied
     * template ships. Asserted across EN/KO/VI, populated and empty, so any
     * future change that recreates / restyles those rows fails the build.
     */
    @Test
    void accountInquiryFcy_sectionLabelRowHasNoBorders_headerRowKeepsBorders_allLocales() throws Exception {
        for (String lang : List.of("EN", "KO", "VI")) {
            // Populated (2 rows) and empty both exercise the write path.
            for (List<AccountRow> data : List.of(
                    List.of(fcyAccount("11704020", "WOORI", "2308", "USD",
                            "Current", "Nick", "100", "50", "20260624000000")),
                    List.<AccountRow>of())) {
                stubAccounts(data);
                Sheet sheet = readSheet(
                        downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, "FCY", lang));

                // Section-label row (index 2) — every cell has NO border.
                Row label = sheet.getRow(2);
                assertThat(label).as("label row present lang=%s", lang).isNotNull();
                for (int c = 0; c <= 5; c++) {
                    CellStyle s = label.getCell(c).getCellStyle();
                    assertThat(s.getBorderTop())
                            .as("label top border c%d lang=%s", c, lang).isEqualTo(BorderStyle.NONE);
                    assertThat(s.getBorderBottom())
                            .as("label bottom border c%d lang=%s", c, lang).isEqualTo(BorderStyle.NONE);
                    assertThat(s.getBorderLeft()).isEqualTo(BorderStyle.NONE);
                    assertThat(s.getBorderRight()).isEqualTo(BorderStyle.NONE);
                }

                // Header row (index 3) — borders preserved.
                Row header = sheet.getRow(3);
                for (int c = 0; c <= 5; c++) {
                    assertThat(header.getCell(c).getCellStyle().getBorderBottom())
                            .as("header border c%d lang=%s", c, lang).isEqualTo(BorderStyle.THIN);
                }

                // Section-label merge (A3:F3) preserved.
                assertThat(sheet.getMergedRegions())
                        .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A3:F3")));
            }
        }
    }

    @Test
    void accountInquiryFcy_defaultCurrencyIsFcy() {
        ArgumentCaptor<AccountFilter> captor = ArgumentCaptor.forClass(AccountFilter.class);
        stubAccounts(List.of());
        downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, null, "EN");
        verify(accountService).list(captor.capture(), isNull(), isNull(), any());
        assertThat(captor.getValue().currency()).isEqualTo("FCY");
    }

    @Test
    void accountInquiryFcy_honoursConcreteCurrencyNarrow() {
        ArgumentCaptor<AccountFilter> captor = ArgumentCaptor.forClass(AccountFilter.class);
        stubAccounts(List.of());
        downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, "USD", "EN");
        verify(accountService).list(captor.capture(), isNull(), isNull(), any());
        assertThat(captor.getValue().currency()).isEqualTo("USD");
    }

    /* =================================================================
     *  Transaction History (VND)
     * ================================================================= */

    @Test
    void transactionHistory_en_fullColumnMapping() throws Exception {
        stubTransactions(List.of(txn("20260624", "090200", "4000000", "0", "1659685693",
                "SALARY", "ACC-01", "PURP1", "VAS1", "CÔNG TY ISC", "admin")));

        var report = downloadService.generateTransactionHistory(
                "11704088_700005285280", null, null, null, null, null, "EN");
        assertThat(report.fileName()).isEqualTo("Transaction History (VND).xls");

        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 3, 0)).isEqualTo("Date/Time");
        assertThat(cell(sheet, 3, 9)).isEqualTo("Confirmed by");
        // Data row at index 4.
        assertThat(cell(sheet, 4, 0)).isEqualTo("24-06-2026 09:02:00");
        assertThat(cell(sheet, 4, 4)).isEqualTo("SALARY");
        assertThat(cell(sheet, 4, 5)).isEqualTo("ACC-01");
        assertThat(cell(sheet, 4, 6)).isEqualTo("PURP1");
        assertThat(cell(sheet, 4, 7)).isEqualTo("VAS1");
        assertThat(cell(sheet, 4, 8)).isEqualTo("CÔNG TY ISC");
        assertThat(cell(sheet, 4, 9)).isEqualTo("admin");
        // Deposit numeric.
        Cell deposit = sheet.getRow(4).getCell(1);
        assertThat(deposit.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(deposit.getNumericCellValue()).isEqualTo(4000000d);
    }

    @Test
    void transactionHistory_ko_selectsKoreanTemplate() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory("b_a", null, null, null, null, null, "KO");
        assertThat(report.fileName()).isEqualTo("거래내역조회 (VND).xls");
        assertThat(cell(readSheet(report), 3, 0)).isEqualTo("거래일시");
    }

    @Test
    void transactionHistory_vi_selectsVietnameseTemplate() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory("b_a", null, null, null, null, null, "VI");
        assertThat(report.fileName()).isEqualTo("Tra cứu giao dịch (VND).xls");
        assertThat(cell(readSheet(report), 3, 0)).isEqualTo("Ngày/Giờ");
    }

    @Test
    void transactionHistory_km_fallsBackToEn() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory("b_a", null, null, null, null, null, "KM");
        assertThat(report.fileName()).isEqualTo("Transaction History (VND).xls");
        assertThat(cell(readSheet(report), 3, 0)).isEqualTo("Date/Time");
    }

    @Test
    void transactionHistory_emptyDataset_producesReadableWorkbook() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory("b_a", null, null, null, null, null, "EN");
        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 3, 0)).isEqualTo("Date/Time");
        assertThat(sheet.getRow(4) == null || cell(sheet, 4, 0) == null || cell(sheet, 4, 0).isEmpty())
                .isTrue();
    }

    @Test
    void transactionHistory_preservesMergedTitle() throws Exception {
        stubTransactions(List.of(txn("20260624", "090200", "1", "0", "1", "r", "c", "p", "v", "ct", "by")));
        Sheet sheet = readSheet(downloadService.generateTransactionHistory(
                "b_a", null, null, null, null, null, "EN"));
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A1:J2")));
        assertThat(sheet.getRow(4).getCell(0).getCellStyle()).isNotNull();
    }

    @Test
    void transactionHistory_dateOnlyWhenTimeBlank() throws Exception {
        stubTransactions(List.of(txn("20260624", "", "1", "0", "1", "r", "c", "p", "v", "ct", "by")));
        Sheet sheet = readSheet(downloadService.generateTransactionHistory(
                "b_a", null, null, null, null, null, "EN"));
        assertThat(cell(sheet, 4, 0)).isEqualTo("24-06-2026");
    }

    /* =================================================================
     *  Transaction History (FCY)
     * ================================================================= */

    @Test
    void transactionHistoryFcy_en_fullColumnMapping() throws Exception {
        stubTransactions(List.of(txn("20260624", "090200", "4000", "0", "12931",
                "USD SALARY", "ACC-01", "PURP1", "VAS1", "CÔNG TY ISC", "admin")));

        var report = downloadService.generateTransactionHistory(
                CurrentSavingsScope.FCY, "11704020_2308", null, null, null, null, null, "EN");
        assertThat(report.fileName()).isEqualTo("Transaction History (FCY).xls");

        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 3, 0)).isEqualTo("Date/Time");
        assertThat(cell(sheet, 3, 9)).isEqualTo("Confirmed by");
        assertThat(cell(sheet, 4, 0)).isEqualTo("24-06-2026 09:02:00");
        assertThat(cell(sheet, 4, 4)).isEqualTo("USD SALARY");
        assertThat(cell(sheet, 4, 8)).isEqualTo("CÔNG TY ISC");
        assertThat(cell(sheet, 4, 9)).isEqualTo("admin");
        Cell deposit = sheet.getRow(4).getCell(1);
        assertThat(deposit.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(deposit.getNumericCellValue()).isEqualTo(4000d);
    }

    @Test
    void transactionHistoryFcy_ko_selectsKoreanTemplate() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory(
                CurrentSavingsScope.FCY, "b_a", null, null, null, null, null, "KO");
        assertThat(report.fileName()).isEqualTo("거래내역조회 (FCY).xls");
        assertThat(cell(readSheet(report), 3, 0)).isEqualTo("거래일시");
    }

    @Test
    void transactionHistoryFcy_vi_selectsVietnameseTemplate() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory(
                CurrentSavingsScope.FCY, "b_a", null, null, null, null, null, "VI");
        assertThat(report.fileName()).isEqualTo("Tra cứu giao dịch (FCY).xls");
        assertThat(cell(readSheet(report), 3, 0)).isEqualTo("Ngày/Giờ");
    }

    @Test
    void transactionHistoryFcy_km_fallsBackToEn() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory(
                CurrentSavingsScope.FCY, "b_a", null, null, null, null, null, "KM");
        assertThat(report.fileName()).isEqualTo("Transaction History (FCY).xls");
        assertThat(cell(readSheet(report), 3, 0)).isEqualTo("Date/Time");
    }

    @Test
    void transactionHistoryFcy_preservesMergedTitle() throws Exception {
        stubTransactions(List.of(txn("20260624", "090200", "1", "0", "1", "r", "c", "p", "v", "ct", "by")));
        Sheet sheet = readSheet(downloadService.generateTransactionHistory(
                CurrentSavingsScope.FCY, "b_a", null, null, null, null, null, "EN"));
        assertThat(sheet.getMergedRegions())
                .anyMatch(r -> r.equals(CellRangeAddress.valueOf("A1:J2")));
    }

    @Test
    void transactionHistoryFcy_emptyDataset_producesReadableWorkbook() throws Exception {
        stubTransactions(List.of());
        var report = downloadService.generateTransactionHistory(
                CurrentSavingsScope.FCY, "b_a", null, null, null, null, null, "EN");
        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 3, 0)).isEqualTo("Date/Time");
        assertThat(sheet.getRow(4) == null || cell(sheet, 4, 0) == null || cell(sheet, 4, 0).isEmpty())
                .isTrue();
    }

    /* =================================================================
     *  Trailing bordered rows (2026-07-01 template cleanup regression)
     * ================================================================= */

    /**
     * The FCY Account Inquiry template previously shipped 10 pre-styled
     * bordered rows (indices 4–13), which leaked into the download as empty
     * bordered rows below the data (the reported symptom). After the cleanup
     * the template holds a single style row (index 4), so a populated download
     * ends right after the last account and an empty one ends at the style row.
     */
    @Test
    void accountInquiryFcy_noBorderedRowsBelowData_populatedAndEmpty() throws Exception {
        // Populated: 2 accounts → data rows 4, 5; sheet ends at 5.
        stubAccounts(List.of(
                fcyAccount("11704020", "WOORI", "2308", "USD", "Current", "N1", "100", "50", "20260624000000"),
                fcyAccount("11704021", "SHINHAN", "2309", "USD", "Current", "N2", "200", "60", "20260624000000")));
        Sheet populated = readSheet(
                downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, "FCY", "EN"));
        assertThat(populated.getLastRowNum()).isEqualTo(5);

        // Empty: only the single required style row (index 4) remains.
        stubAccounts(List.of());
        Sheet empty = readSheet(
                downloadService.generateAccountInquiry(CurrentSavingsScope.FCY, "FCY", "EN"));
        assertThat(empty.getLastRowNum()).isEqualTo(4);
    }

    @Test
    void accountInquiryVnd_noBorderedRowsBelowData_emptyEndsAtStyleRow() throws Exception {
        stubAccounts(List.of());
        Sheet empty = readSheet(downloadService.generateAccountInquiry("EN"));
        // VND Account Inquiry template previously shipped 32 bordered blank rows.
        assertThat(empty.getLastRowNum()).isEqualTo(4);
    }
}
