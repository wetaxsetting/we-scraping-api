package com.kosign.wecms.domain.setting.vasaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelImportRequest;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelImportResult;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountExcelPreview;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * Excel service coverage — preview parse + validation (length, format,
 * in-file dup, against-DB dup), Append vs Replace import semantics,
 * audit slug. Template generation is NOT in scope — the refactor
 * serves the template as a static file the legacy project already
 * ships (see frontend {@code ExcelUploadDialog.tsx}).
 */
@ExtendWith(MockitoExtension.class)
class VasAccountExcelServiceTest {

    @Mock BusiVasAcctInfmRepository repo;
    @Mock VasAcctSeqNoGenerator seqGen;
    @Mock AuditLogService auditLogService;
    @Mock MessageResolver messages;

    private VasAccountExcelService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester@example.com";

    @BeforeEach
    void setUp() {
        service = new VasAccountExcelService(repo, seqGen, auditLogService, messages);
        TenantContext.set(USER, USER, BIZ, LangCd.EN);
        // Lenient — most tests don't exercise every message key, and
        // Mockito otherwise complains about unused stubs.
        lenient().when(messages.resolve(anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  Preview
     * ================================================================= */

    @Test
    void preview_validRows_allCompleted() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"111",      "Cash"},
                {"112",      "Bank deposit"},
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.totalCount()).isEqualTo(2);
        assertThat(p.completedCount()).isEqualTo(2);
        assertThat(p.errorCount()).isEqualTo(0);
        assertThat(p.rows()).allMatch(r -> r.valid() && r.errors().isEmpty());
    }

    @Test
    void preview_missingCode_flagsRowAsError() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"",         "Missing code"},
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.completedCount()).isEqualTo(0);
        assertThat(p.errorCount()).isEqualTo(1);
        assertThat(p.rows().get(0).errors())
                .extracting(e -> e.field())
                .containsExactly("vas_acct_cd");
    }

    @Test
    void preview_codeTooShort_flagsRowAsError() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"ab",       "Short code"},
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.errorCount()).isEqualTo(1);
        assertThat(p.rows().get(0).errors()).hasSize(1);
    }

    @Test
    void preview_codeBadFormat_flagsRowAsError() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"a b!",     "Has space + bang"},
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.errorCount()).isEqualTo(1);
    }

    @Test
    void preview_duplicateWithinFile_secondRowFlagged() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"111",      "First"},
                {"111",      "Second"},
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.completedCount()).isEqualTo(1);
        assertThat(p.errorCount()).isEqualTo(1);
        // First row is the canonical one; the second is the dup.
        assertThat(p.rows().get(0).valid()).isTrue();
        assertThat(p.rows().get(1).valid()).isFalse();
    }

    @Test
    void preview_duplicateAgainstDb_flagsRowAsError() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of("111"));
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"111",      "Conflict with DB"},
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.errorCount()).isEqualTo(1);
        // The code tag MUST be `dup_db` so the frontend can drop it
        // client-side when the user picks Reset/Replace (the legacy
        // option-toggle behaviour).
        assertThat(p.rows().get(0).errors())
                .extracting(e -> e.code())
                .containsExactly("dup_db");
    }

    @Test
    void preview_errorsCarryCategoryCodes() throws Exception {
        // One pass exercising every category code so the contract
        // stays stable across edits — frontend client-side filtering
        // depends on these exact strings.
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of("999"));
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"",         "no code"},        // required
                {"ab",       "short"},          // length
                {"a b!",     "format"},         // format
                {"111",      "first"},          // valid (no error)
                {"111",      "second"},         // dup_file
                {"999",      "db dup"},         // dup_db
                {"222",      ""},               // name required
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.totalCount()).isEqualTo(7);
        // 6 rows fail; 1 ("111", "first") passes.
        assertThat(p.completedCount()).isEqualTo(1);
        assertThat(p.errorCount()).isEqualTo(6);
        assertThat(p.rows())
                .extracting(r -> r.errors().isEmpty() ? "" : r.errors().get(0).code())
                .containsExactly(
                        "required",  // row 0 — empty code
                        "length",    // row 1 — too short
                        "format",    // row 2 — bad chars
                        "",          // row 3 — valid
                        "dup_file",  // row 4 — duplicate within file
                        "dup_db",    // row 5 — duplicate against DB
                        "required"); // row 6 — empty name
    }

    @Test
    void preview_emptyName_flagsRowAsError() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        MultipartFile file = buildXlsx(new String[][]{
                {"VAS Code", "VAS Account Name"},
                {"111",      ""},
        });

        VasAccountExcelPreview p = service.preview(file);

        assertThat(p.errorCount()).isEqualTo(1);
        assertThat(p.rows().get(0).errors())
                .extracting(e -> e.field())
                .containsExactly("vas_acct_nm");
    }

    @Test
    void preview_badHeader_throwsWBE2042() throws Exception {
        MultipartFile file = buildXlsx(new String[][]{
                {"Wrong",    "Header"},
                {"111",      "Cash"},
        });
        assertThatThrownBy(() -> service.preview(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE2042");
    }

    @Test
    void preview_emptyFile_throwsValidation() {
        MultipartFile empty = new MockMultipartFile(
                "file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);
        assertThatThrownBy(() -> service.preview(empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
    }

    @Test
    void preview_corruptBytes_throwsWBE2043() {
        MultipartFile garbage = new MockMultipartFile(
                "file", "junk.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not an xlsx".getBytes());
        assertThatThrownBy(() -> service.preview(garbage))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE2043");
    }

    @Test
    void preview_numericCellCoercion_readsAsString() throws Exception {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        try (Workbook wb = new XSSFWorkbook()) {
            // Legacy template shape: title (row 0) + spacer (row 1) +
            // header (row 2) + data (row 3+). Account Code in column B
            // (index 1), Account Name in column C (index 2).
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("LIST OF CHART OF ACCOUNTS");
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("No.");
            header.createCell(1).setCellValue("Account Code");
            header.createCell(2).setCellValue("Account Name");
            Row dataRow = sheet.createRow(3);
            // Numeric cell — POI would normally render as "111.0";
            // the service coerces back to "111".
            dataRow.createCell(1).setCellValue(111);
            dataRow.createCell(2).setCellValue("Numeric code");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            MultipartFile file = new MockMultipartFile("file", "ok.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());

            VasAccountExcelPreview p = service.preview(file);
            assertThat(p.rows().get(0).vasAcctCd()).isEqualTo("111");
            assertThat(p.rows().get(0).valid()).isTrue();
        }
    }

    /* =================================================================
     *  Import — Append
     * ================================================================= */

    @Test
    void import_appendHappy_insertsAndAuditsAppend() {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());
        when(seqGen.next(BIZ)).thenReturn(300L, 301L);
        when(repo.save(any(BusiVasAcctInfm.class))).thenAnswer(inv -> inv.getArgument(0));

        VasAccountExcelImportResult res = service.import_(new VasAccountExcelImportRequest(
                "A",
                List.of(
                        new VasAccountExcelImportRequest.Row("111", "Cash"),
                        new VasAccountExcelImportRequest.Row("112", "Bank"))));

        assertThat(res.insertedCount()).isEqualTo(2);
        assertThat(res.replacedCount()).isEqualTo(0);
        verify(repo, never()).softDeleteAllLiveForTenant(anyString(), anyString(), anyString());

        ArgumentCaptor<BusiVasAcctInfm> saved = ArgumentCaptor.forClass(BusiVasAcctInfm.class);
        verify(repo, org.mockito.Mockito.times(2)).save(saved.capture());
        // All inserts default Type to "0" (Dual) — UI no longer surfaces Type.
        assertThat(saved.getAllValues())
                .allMatch(e -> "0".equals(e.getVasAcctType()) && "A".equals(e.getStts()));

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(ctx.capture());
        assertThat(ctx.getValue().wsvcId()).isEqualTo("vas-account");
        assertThat(ctx.getValue().detailKey()).isEqualTo("audit.vas_account.excel_append.detail");
        assertThat(ctx.getValue().refNo2()).isEqualTo("APPEND");
    }

    @Test
    void import_appendDuplicateAgainstDb_failsAtomically() {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of("111"));

        assertThatThrownBy(() -> service.import_(new VasAccountExcelImportRequest(
                "A",
                List.of(new VasAccountExcelImportRequest.Row("111", "Cash")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
        verify(repo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void import_appendDuplicateWithinPayload_failsAtomically() {
        when(repo.findLiveCodesByBizKey(BIZ)).thenReturn(List.of());

        assertThatThrownBy(() -> service.import_(new VasAccountExcelImportRequest(
                "A",
                List.of(
                        new VasAccountExcelImportRequest.Row("111", "First"),
                        new VasAccountExcelImportRequest.Row("111", "Second")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
        verify(repo, never()).save(any());
    }

    /* =================================================================
     *  Import — Replace
     * ================================================================= */

    @Test
    void import_replaceHappy_wipesThenInsertsAndAuditsReplace() {
        when(repo.softDeleteAllLiveForTenant(eq(BIZ), eq(USER), anyString())).thenReturn(3);
        when(seqGen.next(BIZ)).thenReturn(500L);
        when(repo.save(any(BusiVasAcctInfm.class))).thenAnswer(inv -> inv.getArgument(0));

        VasAccountExcelImportResult res = service.import_(new VasAccountExcelImportRequest(
                "R",
                List.of(new VasAccountExcelImportRequest.Row("999", "Fresh"))));

        assertThat(res.insertedCount()).isEqualTo(1);
        assertThat(res.replacedCount()).isEqualTo(3);
        verify(repo).softDeleteAllLiveForTenant(eq(BIZ), eq(USER), anyString());

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(ctx.capture());
        assertThat(ctx.getValue().detailKey()).isEqualTo("audit.vas_account.excel_replace.detail");
        assertThat(ctx.getValue().refNo2()).isEqualTo("REPLACE");
    }

    @Test
    void import_replaceSkipsDbDupCheck() {
        // Even when the DB has a "111" row, Replace mode wipes first
        // so the in-payload "111" is fine. The repository's
        // findLiveCodesByBizKey isn't consulted in Replace mode.
        when(repo.softDeleteAllLiveForTenant(eq(BIZ), eq(USER), anyString())).thenReturn(1);
        when(seqGen.next(BIZ)).thenReturn(500L);
        when(repo.save(any(BusiVasAcctInfm.class))).thenAnswer(inv -> inv.getArgument(0));

        VasAccountExcelImportResult res = service.import_(new VasAccountExcelImportRequest(
                "R",
                List.of(new VasAccountExcelImportRequest.Row("111", "Cash"))));

        assertThat(res.insertedCount()).isEqualTo(1);
        verify(repo, never()).findLiveCodesByBizKey(anyString());
    }

    @Test
    void import_emptyRows_throwsValidation() {
        assertThatThrownBy(() -> service.import_(new VasAccountExcelImportRequest(
                "A", List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
    }

    /* =================================================================
     *  Helpers
     * ================================================================= */

    /**
     * Build an XLSX that matches the legacy 3-row-preamble template
     * shape ({@code comm_0217_002_r001_act.jsp:106}):
     * <ul>
     *   <li>Row 0 — title banner (single cell in A0).</li>
     *   <li>Row 1 — blank spacer.</li>
     *   <li>Row 2 — column headers shifted to columns B + C (the legacy
     *       template uses 7 columns; column A is the row number).</li>
     *   <li>Row 3+ — data, same shift.</li>
     * </ul>
     *
     * <p>The {@code cells[][]} input is the logical "header + rows"
     * shape callers want to test (cells[0] = 2-element header pair,
     * cells[1..] = 2-element data pairs). The helper rewrites that
     * into the legacy layout so the service's parser exercises the
     * real production path.
     */
    private static MultipartFile buildXlsx(String[][] cells) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            // Row 0 — title banner.
            sheet.createRow(0).createCell(0).setCellValue("LIST OF CHART OF ACCOUNTS");
            // Row 1 — blank spacer (POI creates it implicitly when row 2 is created).

            // Row 2 — column headers in columns B + C (indices 1 + 2).
            if (cells.length > 0) {
                Row header = sheet.createRow(2);
                header.createCell(0).setCellValue("No.");
                if (cells[0].length > 0) header.createCell(1).setCellValue(cells[0][0]);
                if (cells[0].length > 1) header.createCell(2).setCellValue(cells[0][1]);
            }
            // Row 3+ — data in columns B + C.
            for (int r = 1; r < cells.length; r++) {
                Row data = sheet.createRow(2 + r);
                if (cells[r].length > 0) data.createCell(1).setCellValue(cells[r][0]);
                if (cells[r].length > 1) data.createCell(2).setCellValue(cells[r][1]);
            }
            wb.write(out);
            return new MockMultipartFile(
                    "file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @SuppressWarnings("unused")
    private static void anyLongHelper() {
        // Suppresses an unused-import warning on anyLong() when Mockito's
        // generic inference resolves the call elsewhere.
        anyLong();
    }
}
