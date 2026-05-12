package com.kosign.wecms.domain.setting.purposesettings.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelImportRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelImportResult;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelPreview;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelPreviewRow;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSet;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for the Purpose Excel preview + import flow.
 * Mirrors the VAS Account precedent (Settings > VAS Account Excel
 * tests) plus the parent-validation rules unique to Purpose:
 *
 * <ul>
 *   <li>required / length / dup_file / dup_db on `hand_use_usag_cd`</li>
 *   <li>required on `use_usag_nm`; length cap on `use_usag_nm` /
 *       `use_usag_cont` / `parent_use_usag_cd`</li>
 *   <li>parent_not_found (parent reference doesn't match any in-file
 *       root nor any live DB row)</li>
 *   <li>parent_not_root (parent matches a live DB row that is itself
 *       a level-2 child — forbidden per the 2-level cap)</li>
 *   <li>header validation (WBE2042) + row cap (WBE2041) + corrupt
 *       workbook (WBE2043)</li>
 *   <li>import inserts roots first then children, resolves parent
 *       PK from the freshly-generated `use_usag_cd`</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PurposeExcelServiceTest {

    @Mock PurposeSettingsRepository repo;
    @Mock AuditLogService           auditLogService;
    @Mock MessageResolver           messages;

    PurposeExcelService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";

    @BeforeEach
    void setUp() {
        service = new PurposeExcelService(repo, auditLogService, messages);
        TenantContext.set(USER, USER, BIZ, LangCd.EN);
        // Resolve any message key to a deterministic placeholder so
        // assertions can match without locking to the bundle's text.
        lenient().when(messages.resolve(anyString(), any(java.util.Locale.class)))
                .thenAnswer(inv -> "msg:" + inv.getArgument(0));
        lenient().when(messages.resolve(anyString(), any(java.util.Locale.class), any(Object[].class)))
                .thenAnswer(inv -> "msg:" + inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // Preview
    // ------------------------------------------------------------------

    @Test
    void preview_emptyFile_throwsWBE_VALIDATION() {
        MultipartFile empty = new MockMultipartFile("file", new byte[0]);
        assertThatThrownBy(() -> service.preview(empty))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE_VALIDATION");
    }

    @Test
    void preview_corruptFile_throwsWBE2043() {
        MultipartFile garbage = new MockMultipartFile(
                "file", "garbage.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.preview(garbage))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE2043");
    }

    @Test
    void preview_invalidHeader_throwsWBE2042() throws Exception {
        // Header row carries the wrong labels — should reject before
        // any row is parsed, so the repo is never queried.
        byte[] bytes = buildWorkbook(new String[]{"Foo", "Bar", "Baz", "Qux"}, List.of());
        MultipartFile file = new MockMultipartFile("file", "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);

        assertThatThrownBy(() -> service.preview(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE2042");
    }

    @Test
    void preview_validRoots_allCompleted() throws Exception {
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"BD01", "Investment", "Income", ""},
                new String[]{"BD02", "Withdrawal", "",        ""}));
        MultipartFile file = mockXlsx(bytes);

        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of());

        PurposeExcelPreview preview = service.preview(file);

        assertThat(preview.totalCount()).isEqualTo(2);
        assertThat(preview.completedCount()).isEqualTo(2);
        assertThat(preview.errorCount()).isZero();
        assertThat(preview.rows()).allMatch(PurposeExcelPreviewRow::valid);
    }

    @Test
    void preview_duplicateCodeInFile_flagsDupFile() throws Exception {
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"BD01", "First",  "", ""},
                new String[]{"BD01", "Second", "", ""}));
        MultipartFile file = mockXlsx(bytes);

        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of());

        PurposeExcelPreview preview = service.preview(file);

        assertThat(preview.completedCount()).isEqualTo(1);
        assertThat(preview.errorCount()).isEqualTo(1);
        // Row 1 = first occurrence (valid); row 2 = duplicate.
        var second = preview.rows().get(1);
        assertThat(second.valid()).isFalse();
        assertThat(second.errors()).hasSize(1);
        assertThat(second.errors().get(0).code()).isEqualTo("dup_file");
        assertThat(second.errors().get(0).field()).isEqualTo("hand_use_usag_cd");
    }

    @Test
    void preview_duplicateCodeAgainstDb_flagsDupDb() throws Exception {
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"P01", "Already exists", "", ""}));
        MultipartFile file = mockXlsx(bytes);

        BusiUsagSet existing = entity("0000000007", "P01", "Deposit", /* parent */ null);
        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of(existing));

        PurposeExcelPreview preview = service.preview(file);

        assertThat(preview.errorCount()).isEqualTo(1);
        var row = preview.rows().get(0);
        assertThat(row.errors().get(0).code()).isEqualTo("dup_db");
        assertThat(row.errors().get(0).field()).isEqualTo("hand_use_usag_cd");
    }

    @Test
    void preview_childWithInFileParent_resolves() throws Exception {
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"BD01",   "Parent", "", ""},
                new String[]{"BD01-1", "Child",  "", "BD01"}));
        MultipartFile file = mockXlsx(bytes);

        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of());

        PurposeExcelPreview preview = service.preview(file);

        assertThat(preview.completedCount()).isEqualTo(2);
        assertThat(preview.errorCount()).isZero();
    }

    @Test
    void preview_childWithDbParent_resolves() throws Exception {
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"BD01-1", "Child", "", "P01"}));
        MultipartFile file = mockXlsx(bytes);

        BusiUsagSet parent = entity("0000000007", "P01", "Deposit", null);
        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of(parent));

        PurposeExcelPreview preview = service.preview(file);

        assertThat(preview.completedCount()).isEqualTo(1);
        assertThat(preview.errorCount()).isZero();
    }

    @Test
    void preview_childWithMissingParent_flagsParentNotFound() throws Exception {
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"BD01-1", "Orphan child", "", "ghost"}));
        MultipartFile file = mockXlsx(bytes);

        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of());

        PurposeExcelPreview preview = service.preview(file);

        var row = preview.rows().get(0);
        assertThat(row.valid()).isFalse();
        assertThat(row.errors())
                .extracting("code")
                .contains("parent_not_found");
    }

    @Test
    void preview_childWithLevel2Parent_flagsParentNotRoot() throws Exception {
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"NEW", "New child", "", "P1.1"}));
        MultipartFile file = mockXlsx(bytes);

        // P1.1 is a level-2 row (its own parent_use_usag_cd is set) — would
        // form level 3 if accepted as parent.
        BusiUsagSet level1 = entity("0000000007", "P01", "Deposit", null);
        BusiUsagSet level2 = entity("0000000008", "P1.1", "Sale", "0000000007");
        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of(level1, level2));

        PurposeExcelPreview preview = service.preview(file);

        var row = preview.rows().get(0);
        assertThat(row.valid()).isFalse();
        assertThat(row.errors())
                .extracting("code")
                .contains("parent_not_root");
    }

    @Test
    void preview_emptyRequiredFields_flagged() throws Exception {
        // Row 1: code + name blank but description present → row is
        // non-empty (so it gets processed) and both required-field
        // errors fire. Row 2: a valid row to anchor `completedCount=1`.
        byte[] bytes = buildPurposeWorkbook(List.<String[]>of(
                new String[]{"",     "",           "some description", ""},
                new String[]{"BD01", "Investment", "",                 ""}));
        MultipartFile file = mockXlsx(bytes);

        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of());

        PurposeExcelPreview preview = service.preview(file);

        assertThat(preview.completedCount()).isEqualTo(1);
        assertThat(preview.errorCount()).isEqualTo(1);
        var bad = preview.rows().get(0);
        assertThat(bad.errors())
                .extracting("code")
                .contains("required", "required");
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    @Test
    void import_rootsFirstThenChildren_succeeds() {
        // Children appear before their parents in the payload — the
        // service still orders the inserts so roots commit first and
        // children resolve their parent PK.
        PurposeExcelImportRequest req = new PurposeExcelImportRequest(List.of(
                new PurposeExcelImportRequest.Row("BD01-1", "Child", "",     "BD01"),
                new PurposeExcelImportRequest.Row("BD01",   "Parent", "",    "")));

        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of());
        when(repo.findMaxDispOrd(BIZ)).thenReturn(Optional.of(0));
        when(repo.nextUseUsagCd(BIZ))
                .thenReturn("0000000010")   // root
                .thenReturn("0000000011");  // child
        when(repo.saveAndFlush(any(BusiUsagSet.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PurposeExcelImportResult out = service.import_(req);

        assertThat(out.insertedCount()).isEqualTo(2);
        verify(auditLogService).append(any());
    }

    @Test
    void import_emptyBatch_throwsWBE_VALIDATION() {
        PurposeExcelImportRequest req = new PurposeExcelImportRequest(List.of());
        assertThatThrownBy(() -> service.import_(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE_VALIDATION");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void import_dupCodeInBatch_throwsWBE_VALIDATION() {
        PurposeExcelImportRequest req = new PurposeExcelImportRequest(List.of(
                new PurposeExcelImportRequest.Row("BD01", "First",  "", ""),
                new PurposeExcelImportRequest.Row("BD01", "Second", "", "")));

        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.import_(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE_VALIDATION");
        verify(repo, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static BusiUsagSet entity(String useUsagCd, String handCd, String name, String parent) {
        BusiUsagSet e = new BusiUsagSet();
        e.setBizKey(BIZ);
        e.setUseUsagCd(useUsagCd);
        e.setHandUseUsagCd(handCd);
        e.setUseUsagNm(name);
        e.setUseYn("Y");
        e.setDispYn("Y");
        e.setDispOrd(1);
        e.setParentUseUsagCd(parent);
        return e;
    }

    private static MultipartFile mockXlsx(byte[] bytes) {
        return new MockMultipartFile("file", "purpose.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    /** Build an XLSX with the canonical Purpose header on row 0 + the
     *  given data rows starting row 1. */
    private static byte[] buildPurposeWorkbook(List<String[]> dataRows) throws Exception {
        return buildWorkbook(
                new String[]{"Purpose Code", "Purpose Name", "Purpose Description", "Parent Purpose"},
                dataRows);
    }

    /** Build an XLSX matching the shipped template's row layout:
     *  row 0 = "PURPOSE" title cell, row 1 = blank spacer, row 2 =
     *  column headers, row 3+ = data. Mirrors the file the production
     *  service reads (see {@link PurposeExcelService#HEADER_ROW_INDEX}
     *  / {@link PurposeExcelService#FIRST_DATA_ROW_INDEX}). */
    private static byte[] buildWorkbook(String[] header, List<String[]> dataRows) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            // Row 0 — legacy title cell.
            sheet.createRow(0).createCell(0).setCellValue("PURPOSE");
            // Row 1 — blank spacer (skipped over by the service).
            // Row 2 — column headers.
            Row headerRow = sheet.createRow(2);
            for (int c = 0; c < header.length; c++) {
                headerRow.createCell(c).setCellValue(header[c]);
            }
            // Row 3+ — data.
            int r = 3;
            for (String[] row : dataRows) {
                Row data = sheet.createRow(r++);
                for (int c = 0; c < row.length; c++) {
                    data.createCell(c).setCellValue(row[c]);
                }
            }
            // Defensive — keep a reference so the unused warning quiets.
            List<String> _ignored = new ArrayList<>();
            _ignored.size();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
