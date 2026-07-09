package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentResultExportAuditTest {
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    @Mock private OverseasPaymentRepository paymentRepo;
    @Mock private ErpTrnsHistoryWriter erpHistoryWriter;
    @InjectMocks private OverseasPaymentService service;
    @BeforeEach void setUp() {
        TenantContext.set("tester", "tester", BIZ_KEY, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }
    @AfterEach void tearDown() { TenantContext.clear(); }
    @Test void export_writes_one_W_row_code_14_and_streams() throws Exception {
        when(paymentRepo.findResultForExport(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.exportResult("20260101", "20260131", null, null, resp);
        verify(erpHistoryWriter, times(1)).recordExportedFile(eq("14"), any(Path.class), eq("OverseasPaymentResult.xlsx"));
        assertThat(resp.getContentAsByteArray().length).isGreaterThan(0);
    }

    /** F3 — an M-mode export batch emits its line from OVRS_PYMN_MSTR
     *  (via findMmodeDetailRows / R010 M branch), not from OVRS_PYMN_SUMR_DETL. */
    @Test void export_mmode_batch_emits_line_from_mstr() throws Exception {
        Object[] mBatch = new Object[24];
        mBatch[0]  = "OV-M-1";            // ovrs_pymn_no
        mBatch[1]  = "USD";              // ccy_cd
        mBatch[2]  = "John Doe";         // file_nm (BNFC_NM)
        mBatch[5]  = "20250813102604";   // reg_dtm (=TRNF_DT)
        mBatch[11] = "Y";                // trnf_yn
        mBatch[23] = "M";                // ovrs_pymn_type
        when(paymentRepo.findResultForExport(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(java.util.Collections.singletonList(mBatch));

        Object[] mLine = new Object[17];
        mLine[2]  = "USD";   mLine[3] = new java.math.BigDecimal("1500");
        mLine[7]  = "John Doe"; mLine[13] = "0"; mLine[15] = "0000";
        when(paymentRepo.findMmodeDetailRows("VN_VNM0004_000001", "OV-M-1"))
                .thenReturn(java.util.Collections.singletonList(mLine));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.exportResult("20250101", "20251231", null, null, resp);

        verify(paymentRepo, times(1)).findMmodeDetailRows("VN_VNM0004_000001", "OV-M-1");
        verify(erpHistoryWriter, times(1)).recordExportedFile(eq("14"), any(Path.class), eq("OverseasPaymentResult.xlsx"));
        assertThat(resp.getContentAsByteArray().length).isGreaterThan(0);
    }
}
