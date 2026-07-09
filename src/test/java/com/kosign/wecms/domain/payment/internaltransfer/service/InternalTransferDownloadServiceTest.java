package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InternalTransferDownloadService} — the legacy
 * 21-column populated download report. Asserts locale template
 * selection (EN/KO/VI), KM/other → EN fallback, the generated filename,
 * the 21-column layout, and one row per transfer detail.
 *
 * <p>Templates are loaded from the real classpath
 * ({@code src/main/resources/templates/internal-transfer/}), so the test
 * also proves the templates are bundled where the service expects them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalTransferDownloadServiceTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final int FIRST_DATA_ROW = 3; // Excel row 4

    @Mock private ScdulPymnSumrRepository     sumrRepository;
    @Mock private ScdulPymnSumrDetlRepository detlRepository;
    @Mock private ExptResolMstrRepository     exptResolMstrRepository;
    @Mock private TransferLineDetailMapper    transferLineDetailMapper;
    @Mock private DispatchResultResolver      dispatchResultResolver;

    @InjectMocks private InternalTransferDownloadService service;

    @TempDir java.nio.file.Path tmp;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "Tester", BIZ_KEY, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        when(transferLineDetailMapper.resolveBankNm(anyString())).thenReturn("ShinhanBank");
        when(dispatchResultResolver.resolve(any(), any())).thenReturn(Map.of());
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(eq(BIZ_KEY), any()))
                .thenReturn(List.of(approval()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ------------------------------------------------------------------ */

    @Test
    void en_template_selected_and_filename_correct() throws Exception {
        stub("SP1", master("Y"), details(1));

        var report = service.generate("SP1", "EN");

        assertThat(report.fileName()).isEqualTo("WeCMS_Internal_Transfer_download_EN.xlsx");
        assertThat(titleCell(report.content())).isEqualTo("INTERNAL TRANSFER");
    }

    @Test
    void ko_template_selected() throws Exception {
        stub("SP1", master("Y"), details(1));

        var report = service.generate("SP1", "KO");

        assertThat(report.fileName()).isEqualTo("WeCMS_Internal_Transfer_download_KO.xlsx");
        assertThat(titleCell(report.content())).contains("내부"); // 내부 전송
    }

    @Test
    void vi_template_selected() throws Exception {
        stub("SP1", master("Y"), details(1));

        var report = service.generate("SP1", "VI");

        assertThat(report.fileName()).isEqualTo("WeCMS_Internal_Transfer_download_VI.xlsx");
        assertThat(titleCell(report.content())).contains("CHUYỂN KHOẢN NỘI BỘ");
    }

    @Test
    void km_falls_back_to_en() throws Exception {
        stub("SP1", master("Y"), details(1));

        var report = service.generate("SP1", "KM");

        assertThat(report.fileName()).isEqualTo("WeCMS_Internal_Transfer_download_EN.xlsx");
        assertThat(titleCell(report.content())).isEqualTo("INTERNAL TRANSFER");
    }

    @Test
    void unknown_lang_falls_back_to_en() throws Exception {
        stub("SP1", master("Y"), details(1));

        var report = service.generate("SP1", "fr");

        assertThat(report.fileName()).isEqualTo("WeCMS_Internal_Transfer_download_EN.xlsx");
    }

    @Test
    void blank_lang_uses_jwt_langcd() throws Exception {
        // TenantContext langCd is EN in setUp → EN template.
        stub("SP1", master("Y"), details(1));

        var report = service.generate("SP1", "  ");

        assertThat(report.fileName()).isEqualTo("WeCMS_Internal_Transfer_download_EN.xlsx");
    }

    @Test
    void workbook_has_twenty_one_columns_and_populates_first_data_row() throws Exception {
        ScdulPymnSumr m = master("Y");
        m.setTitl("Test Batch");
        m.setWdrlActNo("700005285299");
        List<ScdulPymnSumrDetl> rows = details(1);
        rows.get(0).setTarBankCd("970424");
        rows.get(0).setFincMdaNo("700005285280");
        rows.get(0).setRcptNm("Recipient A");
        rows.get(0).setTranAmt(new BigDecimal("100000"));
        rows.get(0).setTrnfCcyCd("VND");
        rows.get(0).setTranUsag("Internal payment 1");
        rows.get(0).setChartAcctCd("ACC-02");
        rows.get(0).setNote("VEHICLE FEE");
        rows.get(0).setDocNo("DOC-1");
        stub("SP1", m, rows);

        try (Workbook wb = open(service.generate("SP1", "EN").content())) {
            Sheet sheet = wb.getSheetAt(0);

            // Header row (Excel row 3 → index 2) carries 21 columns A–U.
            Row header = sheet.getRow(2);
            assertThat(header.getCell(0)).isNotNull();
            assertThat(header.getCell(20)).isNotNull();
            assertThat(header.getCell(21)).isNull(); // no 22nd column

            Row data = sheet.getRow(FIRST_DATA_ROW);
            assertThat(str(data, 3)).isEqualTo("Test Batch");        // D File Name (TITL)
            assertThat(str(data, 4)).isEqualTo("ShinhanBank");       // E Withdrawal Bank (resolved)
            assertThat(str(data, 5)).isEqualTo("700005285299");      // F Account No.
            assertThat(str(data, 6)).isEqualTo("970424");            // G Deposit Bank Code
            assertThat(str(data, 7)).isEqualTo("ShinhanBank");       // H Deposit Bank (resolved)
            assertThat(str(data, 8)).isEqualTo("700005285280");      // I Deposit No.
            assertThat(str(data, 9)).isEqualTo("Recipient A");       // J Recipient Name
            assertThat(data.getCell(10).getNumericCellValue()).isEqualTo(100000.0); // K Transfer Amount
            assertThat(str(data, 11)).isEqualTo("VND");              // L Transfer Currency
            assertThat(str(data, 12)).isEqualTo("Internal payment 1"); // M Remarks
            assertThat(str(data, 13)).isEqualTo("ACC-02");           // N Reporting Account
            assertThat(str(data, 14)).isEqualTo("VEHICLE FEE");      // O Description
            assertThat(str(data, 15)).isEqualTo("DOC-1");            // P Document No.
            assertThat(str(data, 16)).isEqualTo("Lamine Yamal");     // Q Requestor (DRAFT_USER_NM)
            assertThat(str(data, 18)).isEqualTo("Approved");         // S Approval Status (apprTypeCd=01)
            assertThat(str(data, 19)).isEqualTo("VO_260612_001");    // T Approval Ref. (DOC_NO)
            assertThat(str(data, 20)).isEqualTo("My Approval Title");// U Approval Title (ERM.TITL)
        }
    }

    @Test
    void writes_one_row_per_transfer_detail() throws Exception {
        stub("SP1", master("Y"), details(3));

        try (Workbook wb = open(service.generate("SP1", "EN").content())) {
            Sheet sheet = wb.getSheetAt(0);
            // 3 details → data rows at index 3,4,5 all carry the File Name;
            // index 6 has no 4th detail.
            assertThat(str(sheet.getRow(3), 3)).isEqualTo("Batch");
            assertThat(str(sheet.getRow(4), 3)).isEqualTo("Batch");
            assertThat(str(sheet.getRow(5), 3)).isEqualTo("Batch");
            Row fourth = sheet.getRow(6);
            boolean emptyFourth = fourth == null
                    || fourth.getCell(3) == null
                    || str(fourth, 3).isEmpty();
            assertThat(emptyFourth).isTrue();
        }
    }

    @Test
    void generateBulk_appends_all_filtered_batches_into_one_template() throws Exception {
        // Batch 1: SP1 (master() default title "Batch"), 1 detail.
        stub("SP1", master("Y"), details(1));
        // Batch 2: SP2, distinct title, 1 detail.
        ScdulPymnSumr m2 = master("Y");
        m2.setScdulPymnNo("SP2");
        m2.setTitl("Batch Two");
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(eq(BIZ_KEY), eq("SP2"), anyString()))
                .thenReturn(Optional.of(m2));
        when(detlRepository.findByBizKeyAndScdulPymnNo(eq(BIZ_KEY), eq("SP2"), any()))
                .thenReturn(details(1));

        var report = service.generateBulk(List.of("SP1", "SP2"), "EN");

        // Uses the Internal Transfer locale template (NOT the from-scratch
        // 7-column summary): template filename + title cell + 21-col header.
        assertThat(report.fileName()).isEqualTo("WeCMS_Internal_Transfer_download_EN.xlsx");
        try (Workbook wb = open(report.content())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("INTERNAL TRANSFER");
            assertThat(sheet.getRow(2).getCell(20)).isNotNull(); // col U present
            assertThat(sheet.getRow(2).getCell(21)).isNull();    // no 22nd column
            // BOTH batches appended into the single sheet (1 detail each → rows 3, 4).
            assertThat(str(sheet.getRow(3), 3)).isEqualTo("Batch");     // SP1 File Name (TITL)
            assertThat(str(sheet.getRow(4), 3)).isEqualTo("Batch Two"); // SP2 File Name (TITL)
        }
    }

    /**
     * Trailing-bordered-rows regression (2026-07-01 template cleanup): the
     * template ships exactly one styled data row (index 3), so a populated
     * download must end right after the last written detail — no leftover
     * pre-styled bordered blank rows below it.
     */
    @Test
    void populated_download_has_no_bordered_rows_below_last_data_row() throws Exception {
        stub("SP1", master("Y"), details(2)); // 2 details → rows 3, 4

        try (Workbook wb = open(service.generate("SP1", "EN").content())) {
            Sheet sheet = wb.getSheetAt(0);
            // Sheet ends at the last written data row (index 4); nothing below.
            assertThat(sheet.getLastRowNum()).isEqualTo(FIRST_DATA_ROW + 1);
        }
    }

    /**
     * Empty result set (bulk export, no batches): only the single required
     * style-template data row remains (index 3) — the table ends there, with
     * no large bordered blank block.
     */
    @Test
    void empty_bulk_export_leaves_only_the_single_style_row() throws Exception {
        try (Workbook wb = open(service.generateBulk(List.of(), "EN").content())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(FIRST_DATA_ROW);
        }
    }

    @Test
    void missing_batch_throws_wbe2060() {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(eq(BIZ_KEY), eq("NOPE"), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate("NOPE", "EN"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2060");
    }

    @Test
    void not_transferred_leaves_status_and_transfer_dtm_blank() throws Exception {
        // trnfYn='N' → A (Transfer Date Time) + B (Status) blank.
        ScdulPymnSumr m = master("N");
        m.setTrnfDtm("20260612101500");
        stub("SP1", m, details(1));

        try (Workbook wb = open(service.generate("SP1", "EN").content())) {
            Row data = wb.getSheetAt(0).getRow(FIRST_DATA_ROW);
            assertThat(str(data, 0)).isEmpty(); // A Transfer Date Time
            assertThat(str(data, 1)).isEmpty(); // B Status
        }
    }

    /* ------------------------------------------------------------------ */

    private void stub(String id, ScdulPymnSumr master, List<ScdulPymnSumrDetl> details) {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(eq(BIZ_KEY), eq(id), anyString()))
                .thenReturn(Optional.of(master));
        when(detlRepository.findByBizKeyAndScdulPymnNo(eq(BIZ_KEY), eq(id), any()))
                .thenReturn(details);
    }

    private static ScdulPymnSumr master(String trnfYn) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ_KEY);
        s.setScdulPymnNo("SP1");
        s.setScdulPymnTypeCd("03");
        s.setOutBankCd("970424");
        s.setWdrlActNo("700005285299");
        s.setCcyCd("VND");
        s.setTitl("Batch");
        s.setTotalTranAmt(new BigDecimal("100000"));
        s.setTotTrnsCnt(BigDecimal.ONE);
        s.setApprSeqNo("12345");
        s.setTrnfYn(trnfYn);
        s.setRegDtm("20260612090000");
        s.setRegId("tester");
        s.setModDtm("20260612090000");
        return s;
    }

    private static List<ScdulPymnSumrDetl> details(int n) {
        List<ScdulPymnSumrDetl> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
            d.setBizKey(BIZ_KEY);
            d.setScdulPymnNo("SP1");
            d.setScdulPymnDetlNo(String.valueOf(i));
            d.setTranAmt(new BigDecimal("100000"));
            d.setTrnfCcyCd("VND");
            d.setTarBankCd("970424");
            d.setFincMdaNo("70000528528" + i);
            d.setRcptNm("Recipient " + i);
            d.setRegId("tester");
            d.setRegDtm("20260612090000");
            d.setModDtm("20260612090000");
            list.add(d);
        }
        return list;
    }

    private static ExptResolMstr approval() {
        ExptResolMstr a = new ExptResolMstr();
        a.setBizKey(BIZ_KEY);
        a.setApprSeqNo("12345");
        a.setDraftUserNm("Lamine Yamal");
        a.setDocNo("VO_260612_001");
        a.setTitl("My Approval Title");
        a.setApprTypeCd("01"); // Approved
        a.setPayDate("20260612");
        return a;
    }

    private static Workbook open(byte[] bytes) throws Exception {
        return WorkbookFactory.create(new ByteArrayInputStream(bytes));
    }

    private static String titleCell(byte[] bytes) throws Exception {
        try (Workbook wb = open(bytes)) {
            return wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();
        }
    }

    private static String str(Row row, int col) {
        if (row == null) return "";
        var cell = row.getCell(col);
        return cell == null ? "" : cell.getStringCellValue();
    }
}
