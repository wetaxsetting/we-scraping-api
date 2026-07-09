package com.kosign.wecms.domain.payment.haengsung.approval;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatusChangedEvent;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.haengsung.oracle.HaengsungErpOracleRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaengsungApprovalStatusWriteBackServiceTest {

    private static final String BIZ = "VN_VNM0004_000014";
    private static final String APPR = "555";
    private static final String FDATE = "20260628";
    private static final String FNUM = "00000123";

    @Mock HaengsungErpOracleRepository oracle;
    @Mock ScdulPymnSumrRepository scdulSumrRepo;
    @Mock ScdulPymnSumrDetlRepository scdulDetlRepo;
    @Mock OvrsPymnSumrRepository ovrsSumrRepo;
    @Mock OvrsPymnDtlsRepository ovrsDetlRepo;
    @Mock BusiInfmRepository busiInfmRepository;

    @InjectMocks HaengsungApprovalStatusWriteBackService service;

    /* ---- mapping helper ---- */
    @Test void statusCodes_mapExactly() {
        org.assertj.core.api.Assertions.assertThat(
                HaengsungApprovalStatusWriteBackService.lastStatusCode(PaymentApprovalStatus.PENDING)).isEqualTo("10");
        org.assertj.core.api.Assertions.assertThat(
                HaengsungApprovalStatusWriteBackService.lastStatusCode(PaymentApprovalStatus.APPROVED)).isEqualTo("20");
        org.assertj.core.api.Assertions.assertThat(
                HaengsungApprovalStatusWriteBackService.lastStatusCode(PaymentApprovalStatus.REJECTED)).isEqualTo("32");
        org.assertj.core.api.Assertions.assertThat(
                HaengsungApprovalStatusWriteBackService.lastStatusCode(PaymentApprovalStatus.PROCESSING)).isEqualTo("51");
    }

    private ScdulPymnSumr scdulBatch() {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ); s.setApprSeqNo(APPR); s.setScdulPymnNo("100");
        s.setErpFileDate(FDATE); s.setErpFileNum(FNUM);
        s.setTotTrnsCnt(new BigDecimal("2")); s.setTotalTranAmt(new BigDecimal("3000"));
        return s;
    }

    private void haengsungTenant() {
        BusiInfm b = new BusiInfm();
        b.setBizKey(BIZ); b.setPrdtType("H"); b.setDelYn("N");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(b));
    }

    private void scdulPresent() {
        when(scdulSumrRepo.findFirstByBizKeyAndApprSeqNo(BIZ, APPR)).thenReturn(Optional.of(scdulBatch()));
    }

    /* ---- mappings via the ERP keys (never amount/account) ---- */

    @Test void pending_mapsTo10_byErpKeys() {
        scdulPresent(); haengsungTenant();
        service.writeBack(BIZ, APPR, PaymentApprovalStatus.PENDING);
        verify(oracle).updateMasterApprovalStatus(eq(FDATE), eq(FNUM), eq("10"), anyString());
        verify(oracle, never()).updateMasterRejected(any(), any(), any(), any(), any());
    }

    @Test void approved_mapsTo20() {
        scdulPresent(); haengsungTenant();
        service.writeBack(BIZ, APPR, PaymentApprovalStatus.APPROVED);
        verify(oracle).updateMasterApprovalStatus(eq(FDATE), eq(FNUM), eq("20"), anyString());
    }

    @Test void processing_mapsTo51() {
        scdulPresent(); haengsungTenant();
        service.writeBack(BIZ, APPR, PaymentApprovalStatus.PROCESSING);
        verify(oracle).updateMasterApprovalStatus(eq(FDATE), eq(FNUM), eq("51"), anyString());
    }

    @Test void rejected_updatesMasterCloseAndDetailProcStsEndGb() {
        scdulPresent(); haengsungTenant();
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setErpFileSeq(new BigDecimal("1"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("100"), any()))
                .thenReturn(List.of(d));

        service.writeBack(BIZ, APPR, PaymentApprovalStatus.REJECTED);

        // master close-out (LAST_STATUS=32 via updateMasterRejected) with batch totals
        verify(oracle).updateMasterRejected(eq(FDATE), eq(FNUM), anyString(),
                eq(new BigDecimal("2")), eq(new BigDecimal("3000")));
        // detail PROC_STS='XXXX' / END_GB='3' via markDetailRejected
        verify(oracle).markDetailRejected(eq(FDATE), eq(FNUM), eq(new BigDecimal("1")),
                anyString(), anyString());
        // never the plain non-reject status update
        verify(oracle, never()).updateMasterApprovalStatus(any(), any(), any(), any());
    }

    @Test void overseasBatch_resolvedAndSynced() {
        when(scdulSumrRepo.findFirstByBizKeyAndApprSeqNo(BIZ, APPR)).thenReturn(Optional.empty());
        OvrsPymnSumr o = new OvrsPymnSumr();
        o.setBizKey(BIZ); o.setApprSeqNo(APPR); o.setOvrsPymnNo("OP1");
        o.setErpFileDate(FDATE); o.setErpFileNum(FNUM);
        o.setTotTrnsCnt(1); o.setTotalTranAmt(new BigDecimal("500"));
        when(ovrsSumrRepo.findByBizKeyAndApprSeqNo(BIZ, APPR)).thenReturn(Optional.of(o));
        haengsungTenant();

        service.writeBack(BIZ, APPR, PaymentApprovalStatus.APPROVED);
        verify(oracle).updateMasterApprovalStatus(eq(FDATE), eq(FNUM), eq("20"), anyString());
    }

    /* ---- skip conditions ---- */

    @Test void nonHaengsungTenant_skipped() {
        scdulPresent();
        BusiInfm f = new BusiInfm();
        f.setBizKey(BIZ); f.setPrdtType("F"); f.setDelYn("N");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(f));

        service.writeBack(BIZ, APPR, PaymentApprovalStatus.APPROVED);
        verifyNoInteractions(oracle);
    }

    @Test void nonErpBatch_blankErpKeys_skipped() {
        ScdulPymnSumr s = scdulBatch();
        s.setErpFileNum("  "); s.setErpFileDate(null);
        when(scdulSumrRepo.findFirstByBizKeyAndApprSeqNo(BIZ, APPR)).thenReturn(Optional.of(s));

        service.writeBack(BIZ, APPR, PaymentApprovalStatus.APPROVED);
        verifyNoInteractions(oracle);
    }

    @Test void batchNotFound_skipped() {
        when(scdulSumrRepo.findFirstByBizKeyAndApprSeqNo(BIZ, APPR)).thenReturn(Optional.empty());
        when(ovrsSumrRepo.findByBizKeyAndApprSeqNo(BIZ, APPR)).thenReturn(Optional.empty());

        service.writeBack(BIZ, APPR, PaymentApprovalStatus.APPROVED);
        verifyNoInteractions(oracle);
    }

    /* ---- Oracle failure must not propagate (approval stays committed) ---- */

    @Test void oracleFailure_doesNotPropagate_fromListener() {
        scdulPresent(); haengsungTenant();
        doThrow(new RuntimeException("ORA-12541 listener down"))
                .when(oracle).updateMasterApprovalStatus(any(), any(), any(), any());

        assertThatNoException().isThrownBy(() ->
                service.onApprovalStatusChanged(
                        new PaymentApprovalStatusChangedEvent(BIZ, APPR, PaymentApprovalStatus.APPROVED)));
    }
}
