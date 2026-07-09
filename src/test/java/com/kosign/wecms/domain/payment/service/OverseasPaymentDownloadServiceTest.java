package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnMstrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Unit tests for {@link OverseasPaymentDownloadService} — the legacy
 * 22-column populated download report (ports {@code pymn_0008_002_r002}).
 * Asserts locale template selection (EN/KO/VI), KM/other → EN fallback, the
 * generated filename, the 22-column layout, the full E-mode column mapping,
 * localized Status / Fee Payer, M-mode generation, and the WBE2045 miss.
 *
 * <p>Templates are loaded from the real classpath
 * ({@code src/main/resources/templates/overseas-payment/}), so the test also
 * proves the templates are bundled where the service expects them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentDownloadServiceTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final int FIRST_DATA_ROW = 4; // Excel row 5
    private static final int HEADER_ROW     = 3; // Excel row 4

    @Mock private OvrsPymnSumrRepository    sumrRepository;
    @Mock private OvrsPymnDtlsRepository    dtlsRepository;
    @Mock private OvrsPymnMstrRepository    ovrsPymnMstrRepository;
    @Mock private ExptResolMstrRepository   exptResolMstrRepository;
    @Mock private DispatchResultResolver    dispatchResultResolver;
    @Mock private TransferLineDetailMapper  transferLineDetailMapper;
    @Mock private OverseasPaymentRepository paymentRepo;

    @InjectMocks private OverseasPaymentDownloadService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "Tester", BIZ_KEY, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        when(transferLineDetailMapper.resolveBankNm(anyString())).thenReturn("ShinhanBank");
        when(dispatchResultResolver.resolveOverseas(any(), any())).thenReturn(Map.of());
        when(exptResolMstrRepository.findById(any())).thenReturn(Optional.of(approval()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ------------------------------------------------------------------ */

    @Test
    void en_template_selected_and_filename_correct() throws Exception {
        stubE("OP1", master("Y"), details(1));

        var report = service.generate("OP1", "EN");

        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Payment_download_EN.xlsx");
        assertThat(titleCell(report.content())).isEqualTo("Overseas Payment");
    }

    @Test
    void ko_template_selected() throws Exception {
        stubE("OP1", master("Y"), details(1));

        var report = service.generate("OP1", "KO");

        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Payment_download_KO.xlsx");
        assertThat(titleCell(report.content())).contains("해외"); // 해외 송금신청
    }

    @Test
    void vi_template_selected() throws Exception {
        stubE("OP1", master("Y"), details(1));

        var report = service.generate("OP1", "VI");

        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Payment_download_VI.xlsx");
        assertThat(titleCell(report.content())).contains("Thanh toán quốc tế");
    }

    @Test
    void km_falls_back_to_en() throws Exception {
        stubE("OP1", master("Y"), details(1));

        var report = service.generate("OP1", "KM");

        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Payment_download_EN.xlsx");
        assertThat(titleCell(report.content())).isEqualTo("Overseas Payment");
    }

    @Test
    void unknown_lang_falls_back_to_en() throws Exception {
        stubE("OP1", master("Y"), details(1));

        var report = service.generate("OP1", "fr");

        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Payment_download_EN.xlsx");
    }

    @Test
    void blank_lang_uses_jwt_langcd() throws Exception {
        stubE("OP1", master("Y"), details(1));

        var report = service.generate("OP1", "  ");

        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Payment_download_EN.xlsx");
    }

    @Test
    void workbook_has_twenty_two_columns_and_populates_first_data_row() throws Exception {
        OvrsPymnSumr m = master("Y");
        m.setTitl("Overseas Batch");
        m.setWdrlActNo("700005285299");
        List<OvrsPymnSumrDetl> rows = details(1);
        OvrsPymnSumrDetl d = rows.get(0);
        d.setPayTranSeq("7");
        d.setBnfcNm("ACME Ltd");
        d.setBnfcBankNm("Bank of Tokyo");
        d.setBnfcBankCd("BOTKJPJT");
        d.setBicCd("BOTKJPJTXXX");
        d.setBnfcAcctNo("123-456-789");
        d.setBnfcAddr("1 Chome, Tokyo");
        d.setCcyCd("USD");
        d.setTrnfAmt(new BigDecimal("1500.50"));
        d.setFeePayerCd("B");
        d.setRprtAcctCd("ACC-02");
        d.setMemo("Invoice 2026-01");
        // dispatched success result for col C
        when(dispatchResultResolver.resolveOverseas(any(), any()))
                .thenReturn(Map.of(d.getOvrsPymnDetlNo(), new DispatchResult("0", null, null, null, "0000", null)));
        stubE("OP1", m, rows);

        try (Workbook wb = open(service.generate("OP1", "EN").content())) {
            Sheet sheet = wb.getSheetAt(0);

            // Header row (Excel row 4 → index 3) carries 22 columns A–V.
            Row header = sheet.getRow(HEADER_ROW);
            assertThat(header.getCell(0)).isNotNull();
            assertThat(header.getCell(21)).isNotNull();
            assertThat(header.getCell(22)).isNull(); // no 23rd column

            Row data = sheet.getRow(FIRST_DATA_ROW);
            assertThat(str(data, 1)).isEqualTo("7");                 // B Transaction Seq
            assertThat(str(data, 2)).isEqualTo("Success");           // C Status (0/0000, EN)
            assertThat(str(data, 4)).isEqualTo("Overseas Batch");    // E File Name (TITL)
            assertThat(str(data, 5)).isEqualTo("ShinhanBank");       // F Withdrawal Bank (resolved)
            assertThat(str(data, 6)).isEqualTo("700005285299");      // G Account No.
            assertThat(str(data, 7)).isEqualTo("ACME Ltd");          // H Beneficiary
            assertThat(str(data, 8)).isEqualTo("Bank of Tokyo");     // I Beneficiary Bank Name
            assertThat(str(data, 9)).isEqualTo("BOTKJPJT");          // J BIC (legacy BNFC_BANK_CD)
            assertThat(str(data, 10)).isEqualTo("123-456-789");      // K Beneficiary Account Number
            assertThat(str(data, 11)).isEqualTo("1 Chome, Tokyo");   // L Beneficiary Address
            assertThat(str(data, 12)).isEqualTo("USD");              // M Transfer Currency
            assertThat(data.getCell(13).getNumericCellValue()).isEqualTo(1500.50); // N Transfer Amount
            assertThat(str(data, 14)).isEqualTo("Beneficiary Charge"); // O Fee Payer (B, EN)
            assertThat(str(data, 15)).isEqualTo("ACC-02");           // P Reporting Account
            assertThat(str(data, 16)).isEqualTo("Invoice 2026-01");  // Q Memo
            assertThat(str(data, 17)).isEqualTo("Lamine Yamal");     // R Requestor (DRAFT_USER_NM)
            assertThat(str(data, 19)).isEqualTo("Approved");         // T Approval Status (apprTypeCd=01)
            assertThat(str(data, 20)).isEqualTo("VO_260612_001");    // U Approval Ref. (DOC_NO)
            assertThat(str(data, 21)).isEqualTo("My Approval Title");// V Approval Title (ERM.TITL)
        }
    }

    @Test
    void status_and_fee_payer_localized_in_korean() throws Exception {
        OvrsPymnSumr m = master("Y");
        List<OvrsPymnSumrDetl> rows = details(1);
        OvrsPymnSumrDetl d = rows.get(0);
        d.setFeePayerCd("B");
        when(dispatchResultResolver.resolveOverseas(any(), any()))
                .thenReturn(Map.of(d.getOvrsPymnDetlNo(), new DispatchResult("0", null, null, null, "0000", null)));
        stubE("OP1", m, rows);

        try (Workbook wb = open(service.generate("OP1", "KO").content())) {
            Row data = wb.getSheetAt(0).getRow(FIRST_DATA_ROW);
            assertThat(str(data, 2)).isEqualTo("성공");        // C Status — Success (KO)
            assertThat(str(data, 14)).isEqualTo("수취인 부담"); // O Fee Payer — Beneficiary Charge (KO)
            assertThat(str(data, 19)).isEqualTo("승인");        // T Approval Status — Approved (KO)
        }
    }

    @Test
    void writes_one_row_per_transfer_detail() throws Exception {
        stubE("OP1", master("Y"), details(3));

        try (Workbook wb = open(service.generate("OP1", "EN").content())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(str(sheet.getRow(4), 4)).isEqualTo("Batch");
            assertThat(str(sheet.getRow(5), 4)).isEqualTo("Batch");
            assertThat(str(sheet.getRow(6), 4)).isEqualTo("Batch");
            Row fourth = sheet.getRow(7);
            boolean emptyFourth = fourth == null
                    || fourth.getCell(4) == null
                    || str(fourth, 4).isEmpty();
            assertThat(emptyFourth).isTrue();
        }
    }

    @Test
    void not_transferred_leaves_status_and_transfer_dtm_blank() throws Exception {
        // trnfYn='N' → A (Transfer Date Time) + C (Status) blank.
        OvrsPymnSumr m = master("N");
        m.setTrnfDt("20260612101500");
        stubE("OP1", m, details(1));

        try (Workbook wb = open(service.generate("OP1", "EN").content())) {
            Row data = wb.getSheetAt(0).getRow(FIRST_DATA_ROW);
            assertThat(str(data, 0)).isEmpty(); // A Transfer Date Time
            assertThat(str(data, 2)).isEmpty(); // C Status
        }
    }

    @Test
    void m_mode_generates_from_ovrs_pymn_mstr() throws Exception {
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(eq(BIZ_KEY), eq("OPM1")))
                .thenReturn(Optional.empty());
        when(ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(eq(BIZ_KEY), eq("OPM1")))
                .thenReturn(Optional.of(mmodeMaster()));
        when(paymentRepo.findMmodeDetailRows(eq(BIZ_KEY), eq("OPM1")))
                .thenReturn(List.<Object[]>of(mmodeRow()));

        var report = service.generate("OPM1", "EN");

        assertThat(report.fileName()).isEqualTo("WeCMS_Overseas_Payment_download_EN.xlsx");
        try (Workbook wb = open(report.content())) {
            Row data = wb.getSheetAt(0).getRow(FIRST_DATA_ROW);
            assertThat(str(data, 7)).isEqualTo("Manual Beneficiary"); // H Beneficiary
            assertThat(str(data, 12)).isEqualTo("USD");               // M Transfer Currency
            assertThat(data.getCell(13).getNumericCellValue()).isEqualTo(250.0); // N Transfer Amount
        }
    }

    @Test
    void missing_batch_throws_wbe2045() {
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(eq(BIZ_KEY), eq("NOPE")))
                .thenReturn(Optional.empty());
        when(ovrsPymnMstrRepository.findByBizKeyAndOvrsPymnNo(eq(BIZ_KEY), eq("NOPE")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate("NOPE", "EN"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2045");
    }

    /* ------------------------------------------------------------------ */

    private void stubE(String id, OvrsPymnSumr master, List<OvrsPymnSumrDetl> details) {
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(eq(BIZ_KEY), eq(id)))
                .thenReturn(Optional.of(master));
        when(dtlsRepository.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(eq(BIZ_KEY), eq(id)))
                .thenReturn(details);
    }

    private static OvrsPymnSumr master(String trnfYn) {
        OvrsPymnSumr s = new OvrsPymnSumr();
        s.setBizKey(BIZ_KEY);
        s.setOvrsPymnNo("OP1");
        s.setOvrsPymnTypeCd("E");
        s.setOutBankCd("970424");
        s.setWdrlActNo("700005285299");
        s.setCcyCd("USD");
        s.setTitl("Batch");
        s.setTotalTranAmt(new BigDecimal("1500.50"));
        s.setTotTrnsCnt(1);
        s.setApprSeqNo("12345");
        s.setTrnfYn(trnfYn);
        s.setRegDtm("20260612090000");
        s.setRegId("tester");
        return s;
    }

    private static List<OvrsPymnSumrDetl> details(int n) {
        List<OvrsPymnSumrDetl> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
            d.setBizKey(BIZ_KEY);
            d.setOvrsPymnNo("OP1");
            d.setOvrsPymnDetlNo(String.valueOf(i));
            d.setTrnfAmt(new BigDecimal("1500.50"));
            d.setCcyCd("USD");
            d.setBnfcNm("Beneficiary " + i);
            d.setBnfcBankNm("Bank " + i);
            d.setBnfcBankCd("BANKCD" + i);
            d.setBicCd("BIC" + i);
            d.setBnfcAcctNo("ACCT" + i);
            d.setBnfcAddr("Addr " + i);
            d.setFeePayerCd("S");
            d.setMemo("Memo " + i);
            d.setRegId("tester");
            d.setRegDtm("20260612090000");
            d.setModId("tester");
            d.setModDtm("20260612090000");
            list.add(d);
        }
        return list;
    }

    private static OvrsPymnMstr mmodeMaster() {
        OvrsPymnMstr m = new OvrsPymnMstr();
        m.setBizKey(BIZ_KEY);
        m.setOvrsPymnNo("OPM1");
        m.setTrnfCcyCd("USD");
        m.setTotalTranAmt(new BigDecimal("250"));
        m.setApprSeqNo("12345");
        m.setTrnfYn("N");
        m.setRegDtm("20260612090000");
        m.setDbtAcctCd("970424");
        m.setDbtAcctNo("debit-acct");
        m.setBnfcNm("Manual Beneficiary");
        m.setBnfcAcctNo("manual-acct");
        return m;
    }

    /** Mirrors OverseasPaymentRepository.findMmodeDetailRows index layout. */
    private static Object[] mmodeRow() {
        Object[] m = new Object[17];
        m[2]  = "USD";                 // TRNF_CCY_CD
        m[3]  = new BigDecimal("250"); // TRNF_AMT
        m[4]  = "O";                   // FEE_PAYER_CD
        m[5]  = "Manual memo";         // MEMO
        m[6]  = "ACC-03";              // CHART_ACCT_CD
        m[7]  = "Manual Beneficiary";  // BNFC_NM
        m[8]  = "Manual Bank";         // BNFC_BANK_NAME
        m[9]  = "MBICXXX";             // BNFC_BANK_BIC
        m[10] = "manual-acct";         // BNFC_ACCT_NO
        m[11] = "Manual Addr";         // BNFC_ADDR
        m[13] = "";                    // PROC_STAT
        m[15] = "";                    // RS_CD
        return m;
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
