package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAccountRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAddressRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactOverseasRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ContactsDownloadService}. Drives the real
 * classpath templates ({@code templates/contacts/*.xlsx}) through Apache
 * POI and asserts the produced workbook — locale template selection,
 * KM→EN fallback, the two column layouts (Local 9 / Overseas 11), the
 * per-account / per-beneficiary row granularity (legacy LEFT JOIN
 * parity), the OQ-8 {@code DISP_ORD} address discriminator, the
 * {@code dd-MM-yyyy} Valid Date, and the filename rule.
 */
class ContactsDownloadServiceTest {

    private ContactsService contactsService;
    private ContactsDownloadService downloadService;

    @BeforeEach
    void setUp() {
        contactsService = mock(ContactsService.class);
        downloadService = new ContactsDownloadService(contactsService);
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ----------------------------------------------------------------- */

    private static ContactRow localContact(String code, String name, String taxId,
                                            List<ContactAddressRow> addrs,
                                            List<ContactAccountRow> accts) {
        return new ContactRow("k_" + code, code, name, name + " EN", taxId, "L", null,
                "20260513000000", addrs, accts, List.of());
    }

    private static ContactRow overseasContact(String code, String name, String taxId,
                                               List<ContactAddressRow> addrs,
                                               List<ContactOverseasRow> bene) {
        return new ContactRow("k_" + code, code, name, name + " EN", taxId, "O", null,
                "20260513000000", addrs, List.of(), bene);
    }

    private static ContactAddressRow addr(int dispOrd, String text, String issuDt, String dueDt) {
        return new ContactAddressRow("a" + dispOrd, text, dispOrd, issuDt, dueDt);
    }

    private static Sheet readSheet(ContactsDownloadService.GeneratedReport report) throws Exception {
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
     *  Local
     * ================================================================= */

    @Test
    void local_en_oneAccount_fullColumnMapping() throws Exception {
        ContactRow c = localContact("C00001", "CÔNG TY WEBCASH", "1201496252",
                List.of(addr(1, "Current Addr", "20220101", "20220831"),
                        addr(2, "Old Addr", "20210101", "20211231")),
                List.of(new ContactAccountRow("acc1", "123456789012", 1,
                        "VCB - Vietcombank", "970436", null, "VND", "Owner", null)));
        when(contactsService.listForExport(isNull(), eq("L"))).thenReturn(List.of(c));

        var report = downloadService.generate("L", "EN");
        assertThat(report.fileName()).isEqualTo("WeCMS_Local_Contacts_download_EN.xlsx");

        Sheet sheet = readSheet(report);
        // Template title + header preserved.
        assertThat(cell(sheet, 0, 0)).isEqualTo("Local Contacts");
        assertThat(cell(sheet, 2, 0)).isEqualTo(" Contact Code");
        // Data row 4 (index 3).
        assertThat(cell(sheet, 3, 0)).isEqualTo("C00001");
        assertThat(cell(sheet, 3, 1)).isEqualTo("CÔNG TY WEBCASH");
        assertThat(cell(sheet, 3, 2)).isEqualTo("1201496252");
        assertThat(cell(sheet, 3, 3)).isEqualTo("VCB - Vietcombank");
        assertThat(cell(sheet, 3, 4)).isEqualTo("123456789012");
        assertThat(cell(sheet, 3, 5)).isEqualTo("VND");
        assertThat(cell(sheet, 3, 6)).isEqualTo("Current Addr");
        assertThat(cell(sheet, 3, 7)).isEqualTo("Old Addr");
        // Valid Date = DUE_DT (preferred) of the DISP_ORD=1 address, dd-MM-yyyy.
        assertThat(cell(sheet, 3, 8)).isEqualTo("31-08-2022");
    }

    @Test
    void local_noAccount_stillEmitsOneRowWithBlankBankColumns() throws Exception {
        ContactRow c = localContact("C00002", "No Bank Co", "999",
                List.of(addr(1, "Addr1", "20220101", null)), List.of());
        when(contactsService.listForExport(isNull(), eq("L"))).thenReturn(List.of(c));

        Sheet sheet = readSheet(downloadService.generate("L", "EN"));
        assertThat(cell(sheet, 3, 0)).isEqualTo("C00002");
        assertThat(cell(sheet, 3, 3)).isEqualTo("");   // Deposit Bank blank
        assertThat(cell(sheet, 3, 4)).isEqualTo("");   // Account No blank
        assertThat(cell(sheet, 3, 5)).isEqualTo("");   // Currency blank
        // No DUE_DT → fall back to ISSU_DT.
        assertThat(cell(sheet, 3, 8)).isEqualTo("01-01-2022");
        // Only one data row exists.
        assertThat(sheet.getRow(4) == null || cell(sheet, 4, 0) == null || cell(sheet, 4, 0).isEmpty())
                .isTrue();
    }

    @Test
    void local_twoAccounts_emitTwoRowsSameContact() throws Exception {
        ContactRow c = localContact("C00003", "Two Acc Co", "111",
                List.of(addr(1, "Addr", "20220101", "20220101")),
                List.of(new ContactAccountRow("a1", "111", 1, "Bank A", "1", null, "VND", "o", null),
                        new ContactAccountRow("a2", "222", 2, "Bank B", "2", null, "USD", "o", null)));
        when(contactsService.listForExport(isNull(), eq("L"))).thenReturn(List.of(c));

        Sheet sheet = readSheet(downloadService.generate("L", "EN"));
        assertThat(cell(sheet, 3, 3)).isEqualTo("Bank A");
        assertThat(cell(sheet, 3, 4)).isEqualTo("111");
        assertThat(cell(sheet, 4, 3)).isEqualTo("Bank B");
        assertThat(cell(sheet, 4, 4)).isEqualTo("222");
        // Contact-level columns repeat on both rows.
        assertThat(cell(sheet, 3, 0)).isEqualTo("C00003");
        assertThat(cell(sheet, 4, 0)).isEqualTo("C00003");
    }

    /* =================================================================
     *  Overseas
     * ================================================================= */

    @Test
    void overseas_en_oneBeneficiary_fullColumnMapping() throws Exception {
        ContactRow c = overseasContact("C00010", "Overseas Co", "0201496352-001",
                List.of(addr(1, "Cur OVRS", "20220101", "20220831"),
                        addr(2, "Old OVRS", null, null)),
                List.of(new ContactOverseasRow("o1", "NGUYEN VAN NAPAS", "180012992264",
                        "SHBKKRSEXXX", "SHINHAN", "BENE_CD", "Korea")));
        when(contactsService.listForExport(isNull(), eq("O"))).thenReturn(List.of(c));

        var report = downloadService.generate("O", "EN");
        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Contacts_download_EN.xlsx");

        Sheet sheet = readSheet(report);
        assertThat(cell(sheet, 0, 0)).isEqualTo("Overseas Contacts");
        assertThat(cell(sheet, 3, 0)).isEqualTo("C00010");
        assertThat(cell(sheet, 3, 1)).isEqualTo("Overseas Co");
        assertThat(cell(sheet, 3, 2)).isEqualTo("0201496352-001");
        assertThat(cell(sheet, 3, 3)).isEqualTo("NGUYEN VAN NAPAS");      // Beneficiary Name
        assertThat(cell(sheet, 3, 4)).isEqualTo("SHINHAN");              // Beneficiary Bank Name
        assertThat(cell(sheet, 3, 5)).isEqualTo("SHBKKRSEXXX");          // BIC
        assertThat(cell(sheet, 3, 6)).isEqualTo("180012992264");        // Beneficiary Account No
        assertThat(cell(sheet, 3, 7)).isEqualTo("Korea");               // Beneficiary Address
        assertThat(cell(sheet, 3, 8)).isEqualTo("Cur OVRS");            // Current Address
        assertThat(cell(sheet, 3, 9)).isEqualTo("Old OVRS");            // Old Address
        assertThat(cell(sheet, 3, 10)).isEqualTo("31-08-2022");         // Valid Date
    }

    @Test
    void overseas_contactWithNoBeneficiary_emitsNoRow() throws Exception {
        ContactRow c = overseasContact("C00011", "Empty OVRS", "1",
                List.of(addr(1, "Addr", "20220101", "20220101")), List.of());
        when(contactsService.listForExport(isNull(), eq("O"))).thenReturn(List.of(c));

        Sheet sheet = readSheet(downloadService.generate("O", "EN"));
        // No data row written below the header (row index 3 empty).
        assertThat(sheet.getRow(3) == null || cell(sheet, 3, 0) == null || cell(sheet, 3, 0).isEmpty())
                .isTrue();
    }

    /* =================================================================
     *  Locale resolution
     * ================================================================= */

    @Test
    void locale_ko_selectsKoreanTemplate() throws Exception {
        when(contactsService.listForExport(isNull(), eq("L"))).thenReturn(List.of());
        var report = downloadService.generate("L", "KO");
        assertThat(report.fileName()).isEqualTo("WeCMS_Local_Contacts_download_KO.xlsx");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("국내 거래처 관리");
    }

    @Test
    void locale_km_fallsBackToEn() throws Exception {
        when(contactsService.listForExport(isNull(), eq("O"))).thenReturn(List.of());
        var report = downloadService.generate("O", "KM");
        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Contacts_download_EN.xlsx");
        assertThat(cell(readSheet(report), 0, 0)).isEqualTo("Overseas Contacts");
    }

    @Test
    void locale_blankParam_usesJwtLangCd() throws Exception {
        TenantContext.clear();
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.VI);
        when(contactsService.listForExport(isNull(), eq("L"))).thenReturn(List.of());
        var report = downloadService.generate("L", null);
        assertThat(report.fileName()).isEqualTo("WeCMS_Local_Contacts_download_VI.xlsx");
    }
}
