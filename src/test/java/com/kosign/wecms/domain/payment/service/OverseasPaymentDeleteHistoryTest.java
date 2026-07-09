package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.service.OverseasPaymentFileResolver;
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

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G1 fix — Overseas scheduled-payment delete must write an
 * {@code ERP_TRNS_HSTR} deletion-history row ({@code ERP_TYPE='D'},
 * {@code TRNS_TYPE_CD='13'}) and preserve the uploaded file, mirroring
 * legacy {@code pymn_0007_001_d001_act.jsp} and the four domestic
 * scheduled modules. Approval-requested batches stay blocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentDeleteHistoryTest {

    private static final String BIZ_KEY      = "VN_VNM0004_000001";
    private static final String OVRS_PYMN_NO = "OV-DEL-1";
    private static final String ORG_FILE_NM  = "Overseas_Payment_241205.xlsx";
    private static final String SAVE_FILE_NM = "20241205_ab12cd34_Overseas_Payment_241205.xlsx";

    @Mock private OvrsPymnSumrRepository        sumrRepository;
    @Mock private OvrsPymnDtlsRepository        dtlsRepository;
    @Mock private ErpTrnsHistoryWriter          erpHistoryWriter;
    @Mock private OverseasPaymentFileResolver   fileResolver;

    @InjectMocks private OverseasPaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "tester", BIZ_KEY, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private OvrsPymnSumr draftBatch(String apprSeqNo) {
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(BIZ_KEY);
        sumr.setOvrsPymnNo(OVRS_PYMN_NO);
        sumr.setApprSeqNo(apprSeqNo);
        sumr.setTrnfYn("N");
        sumr.setOrgFileNm(ORG_FILE_NM);
        sumr.setSaveFileNm(SAVE_FILE_NM);
        return sumr;
    }

    /** (1) blank APPR_SEQ_NO → summary + detail rows are deleted;
     *  (2)+(3)+(4) an ERP 'D' row is written with TRNS_TYPE_CD='13' and the
     *  original file name preserved. `recordDeletedFile` internally stamps
     *  ERP_TYPE='D' — verifying the call proves the "Deleted" tab bucket. */
    @Test
    void delete_notYetApproved_deletesRows_and_writes_D_row_code13_preservingOrgFileNm() {
        OvrsPymnSumr sumr = draftBatch(null);
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, OVRS_PYMN_NO))
                .thenReturn(Optional.of(sumr));
        when(fileResolver.resolveForRead(BIZ_KEY, SAVE_FILE_NM))
                .thenReturn(Path.of("overseas-payment", SAVE_FILE_NM));

        service.deleteDraftBatch(OVRS_PYMN_NO);

        // (1) summary + detail physically deleted
        verify(dtlsRepository, times(1)).deleteByBizKeyAndOvrsPymnNo(BIZ_KEY, OVRS_PYMN_NO);
        verify(sumrRepository, times(1)).delete(sumr);

        // (2) ERP_TYPE='D' bucket (recordDeletedFile writes 'D') +
        // (3) TRNS_TYPE_CD='13' + (4) ORG_FILE_NM preserved verbatim
        verify(erpHistoryWriter, times(1))
                .recordDeletedFile(eq("13"), any(Path.class), eq(ORG_FILE_NM));
    }

    /** (5) APPR_SEQ_NO set → delete is blocked; no rows deleted, no history
     *  row written. Block code is the legacy WBE1063 (aligned across all 5
     *  payment modules, PO decision 2026-07-08 — was WBE2099 before). */
    @Test
    void delete_afterApprovalRequested_isBlocked_noDelete_noHistory() {
        OvrsPymnSumr sumr = draftBatch("APS-000123");
        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, OVRS_PYMN_NO))
                .thenReturn(Optional.of(sumr));

        assertThatThrownBy(() -> service.deleteDraftBatch(OVRS_PYMN_NO))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE1063");

        verify(dtlsRepository, never()).deleteByBizKeyAndOvrsPymnNo(anyString(), anyString());
        verify(sumrRepository, never()).delete(any());
        verify(erpHistoryWriter, never()).recordDeletedFile(anyString(), any(), anyString());
    }
}
