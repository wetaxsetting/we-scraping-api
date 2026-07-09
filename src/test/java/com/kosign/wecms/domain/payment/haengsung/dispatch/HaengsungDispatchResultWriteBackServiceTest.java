package com.kosign.wecms.domain.payment.haengsung.dispatch;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.dispatch.callback.event.PaymentDispatchResultFinalizedEvent;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResult;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.haengsung.oracle.HaengsungErpOracleRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaengsungDispatchResultWriteBackServiceTest {

    private static final String BIZ = "VN_VNM0004_000014";
    private static final String TRAN_DT = "20260628";
    private static final String FILE_SEQ_NO = "0000001";
    private static final String FDATE = "20260628";
    private static final String FNUM = "00000123";

    @Mock HaengsungErpOracleRepository oracle;
    @Mock DispatchResultResolver dispatchResultResolver;
    @Mock ScdulPymnSumrRepository scdulSumrRepo;
    @Mock ScdulPymnSumrDetlRepository scdulDetlRepo;
    @Mock OvrsPymnSumrRepository ovrsSumrRepo;
    @Mock OvrsPymnDtlsRepository ovrsDetlRepo;
    @Mock BusiInfmRepository busiInfmRepository;

    @InjectMocks HaengsungDispatchResultWriteBackService service;

    /* ============================ pure mapping rules ============================ */

    @Test void countClassify_successOnlyWhenProcStat0AndRsCd0000() {
        assertThat(HaengsungDispatchResultWriteBackService.countClassify("0", "0000"))
                .isEqualTo(HaengsungDispatchResultWriteBackService.Cls.SUCCESS);
        assertThat(HaengsungDispatchResultWriteBackService.countClassify("3", "0999"))
                .isEqualTo(HaengsungDispatchResultWriteBackService.Cls.ERROR);
        assertThat(HaengsungDispatchResultWriteBackService.countClassify("0", "9999"))
                .isEqualTo(HaengsungDispatchResultWriteBackService.Cls.ERROR);
        // partial-file master PROC_STAT='1' counts as neither (legacy R015 parity)
        assertThat(HaengsungDispatchResultWriteBackService.countClassify("1", "0000"))
                .isEqualTo(HaengsungDispatchResultWriteBackService.Cls.NEITHER);
    }

    @Test void detailResult_successMapsEndGb8AndProcStsRsCd() {
        HaengsungDispatchResultWriteBackService.DetailResult r =
                HaengsungDispatchResultWriteBackService.detailResult("0", "0000");
        assertThat(r.endGb()).isEqualTo("8");
        assertThat(r.procSts()).isEqualTo("0000");
    }

    @Test void detailResult_failedRejectedMapsEndGb5AndProcSts0999() {
        HaengsungDispatchResultWriteBackService.DetailResult r =
                HaengsungDispatchResultWriteBackService.detailResult("3", "anything");
        assertThat(r.endGb()).isEqualTo("5");
        assertThat(r.procSts()).isEqualTo("0999"); // fallback override
    }

    @Test void detailResult_failedWithRsCdMapsEndGb5AndKeepsRsCd() {
        HaengsungDispatchResultWriteBackService.DetailResult r =
                HaengsungDispatchResultWriteBackService.detailResult("0", "9999");
        assertThat(r.endGb()).isEqualTo("5");
        assertThat(r.procSts()).isEqualTo("9999");
    }

    /* ============================ scheduled (Vendor/Expense/Payroll) ============================ */

    @Test void scheduled_allSuccess_masterClosedAndDetailsEndGb8() {
        haengsungTenant();
        ScdulPymnSumr s = scdulBatch();
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(s));
        when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());

        ScdulPymnSumrDetl d1 = scdulDetl("D1", "1", new BigDecimal("1000"));
        ScdulPymnSumrDetl d2 = scdulDetl("D2", "2", new BigDecimal("2000"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("100"), any()))
                .thenReturn(List.of(d1, d2));
        when(dispatchResultResolver.resolve(eq(s), anyList())).thenReturn(Map.of(
                "D1", success(),
                "D2", success()));

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        // master close-out: NOR_CNT=2 / NOR_AMT=3000 / ERR=0; LAST_STATUS=52 + END_GB=1 baked in the repo SQL
        verify(oracle).updateMasterDispatchResult(eq(FDATE), eq(FNUM), anyString(), eq("20260628090000"),
                eq(new BigDecimal("2")), eq(new BigDecimal("3000")),
                eq(BigDecimal.ZERO), eq(BigDecimal.ZERO));
        // both details END_GB='8', PROC_STS='0000', TR_DATE=PAY_TRAN_DT
        verify(oracle).updateDetailDispatchResult(eq(FDATE), eq(FNUM), eq(new BigDecimal("1")),
                eq("8"), eq("0000"), any(), anyString(), eq(TRAN_DT));
        verify(oracle).updateDetailDispatchResult(eq(FDATE), eq(FNUM), eq(new BigDecimal("2")),
                eq("8"), eq("0000"), any(), anyString(), eq(TRAN_DT));
        // ERP_PROC_CMPL_YN flipped only after the Oracle writes
        verify(scdulSumrRepo).markErpProcComplete(eq(BIZ), eq("100"), anyString(), anyString());
        // never the receive / approval write-backs
        verify(oracle, never()).markMasterReceived(any(), any(), any());
        verify(oracle, never()).updateMasterApprovalStatus(any(), any(), any(), any());
        verify(oracle, never()).markDetailRejected(any(), any(), any(), any(), any());
    }

    @Test void scheduled_mixed_countsAndAmountsSplitAndFailureEndGb5() {
        haengsungTenant();
        ScdulPymnSumr s = scdulBatch();
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(s));
        lenient().when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());

        ScdulPymnSumrDetl ok = scdulDetl("D1", "1", new BigDecimal("1000"));
        ScdulPymnSumrDetl bad = scdulDetl("D2", "2", new BigDecimal("2000"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("100"), any()))
                .thenReturn(List.of(ok, bad));
        when(dispatchResultResolver.resolve(eq(s), anyList())).thenReturn(Map.of(
                "D1", success(),
                "D2", new DispatchResult("3", null, "20260628090000", "20260628090000", "9999", "rejected")));

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        // NOR_CNT=1 / NOR_AMT=1000 ; ERR_CNT=1 / ERR_AMT=2000
        verify(oracle).updateMasterDispatchResult(eq(FDATE), eq(FNUM), anyString(), anyString(),
                eq(new BigDecimal("1")), eq(new BigDecimal("1000")),
                eq(new BigDecimal("1")), eq(new BigDecimal("2000")));
        // failed (PROC_STAT='3') → END_GB='5', PROC_STS='0999' (override)
        verify(oracle).updateDetailDispatchResult(eq(FDATE), eq(FNUM), eq(new BigDecimal("2")),
                eq("5"), eq("0999"), any(), anyString(), eq(TRAN_DT));
        verify(scdulSumrRepo).markErpProcComplete(eq(BIZ), eq("100"), anyString(), anyString());
    }

    @Test void scheduled_payrollTypeSupported_sameCodePath() {
        haengsungTenant();
        ScdulPymnSumr s = scdulBatch();
        s.setScdulPymnTypeCd("00"); // Payroll discriminator — same write-back path
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(s));
        lenient().when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());
        ScdulPymnSumrDetl d = scdulDetl("D1", "1", new BigDecimal("500"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("100"), any())).thenReturn(List.of(d));
        when(dispatchResultResolver.resolve(eq(s), anyList())).thenReturn(Map.of("D1", success()));

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        verify(oracle).updateMasterDispatchResult(eq(FDATE), eq(FNUM), anyString(), anyString(),
                eq(new BigDecimal("1")), eq(new BigDecimal("500")), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO));
        verify(scdulSumrRepo).markErpProcComplete(eq(BIZ), eq("100"), anyString(), anyString());
    }

    /* ================================ overseas ================================ */

    @Test void overseas_supported_viaResolveOverseas() {
        haengsungTenant();
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());
        OvrsPymnSumr o = ovrsBatch();
        when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(o));
        OvrsPymnSumrDetl d = ovrsDetl("OD1", "5", new BigDecimal("750"));
        when(ovrsDetlRepo.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(BIZ, "OP1"))
                .thenReturn(List.of(d));
        when(dispatchResultResolver.resolveOverseas(eq(o), anyList())).thenReturn(Map.of("OD1", success()));

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        verify(oracle).updateMasterDispatchResult(eq(FDATE), eq(FNUM), anyString(), anyString(),
                eq(new BigDecimal("1")), eq(new BigDecimal("750")), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO));
        verify(oracle).updateDetailDispatchResult(eq(FDATE), eq(FNUM), eq(new BigDecimal("5")),
                eq("8"), eq("0000"), any(), anyString(), eq(TRAN_DT));
        verify(ovrsSumrRepo).markErpProcComplete(eq(BIZ), eq("OP1"), anyString(), anyString());
    }

    /* ============================ guards / skips / retry ============================ */

    @Test void incompleteDetails_skippedWholeBatch_noOracleNoFlag() {
        haengsungTenant();
        ScdulPymnSumr s = scdulBatch();
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(s));
        lenient().when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());
        ScdulPymnSumrDetl d1 = scdulDetl("D1", "1", new BigDecimal("1000"));
        ScdulPymnSumrDetl d2 = scdulDetl("D2", "2", new BigDecimal("2000"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("100"), any()))
                .thenReturn(List.of(d1, d2));
        // D2 has no RS_CD yet → not all complete
        when(dispatchResultResolver.resolve(eq(s), anyList())).thenReturn(Map.of(
                "D1", success(),
                "D2", new DispatchResult("0", null, "20260628090000", "20260628090000", null, null)));

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        verifyNoInteractions(oracle);
        verify(scdulSumrRepo, never()).markErpProcComplete(any(), any(), any(), any());
    }

    @Test void nonErpBatch_blankErpKeys_skipped() {
        haengsungTenant();
        ScdulPymnSumr s = scdulBatch();
        s.setErpFileNum("  ");
        s.setErpFileDate(null);
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(s));
        lenient().when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        verifyNoInteractions(oracle);
        verify(scdulSumrRepo, never()).markErpProcComplete(any(), any(), any(), any());
    }

    @Test void notDispatched_or_alreadyPushed_skipped() {
        haengsungTenant();
        ScdulPymnSumr notDispatched = scdulBatch();
        notDispatched.setTrnfYn("N");
        ScdulPymnSumr alreadyPushed = scdulBatch();
        alreadyPushed.setScdulPymnNo("101");
        alreadyPushed.setErpProcCmplYn("Y");
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(notDispatched, alreadyPushed));
        lenient().when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        verifyNoInteractions(oracle);
    }

    @Test void nonHaengsungTenant_skipped() {
        BusiInfm f = new BusiInfm();
        f.setBizKey(BIZ);
        f.setPrdtType("F");
        f.setDelYn("N");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(f));

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        verifyNoInteractions(oracle);
        verifyNoInteractions(scdulSumrRepo);
        verifyNoInteractions(ovrsSumrRepo);
    }

    /* ============================ no-XA: Oracle failure isolation ============================ */

    @Test void oracleFailure_doesNotFlipFlag_andDoesNotPropagateFromListener() {
        haengsungTenant();
        ScdulPymnSumr s = scdulBatch();
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(s));
        lenient().when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());
        ScdulPymnSumrDetl d = scdulDetl("D1", "1", new BigDecimal("1000"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("100"), any())).thenReturn(List.of(d));
        when(dispatchResultResolver.resolve(eq(s), anyList())).thenReturn(Map.of("D1", success()));
        doThrow(new RuntimeException("ORA-12541 listener down"))
                .when(oracle).updateMasterDispatchResult(any(), any(), any(), any(), any(), any(), any(), any());

        // listener entry point must never propagate (dispatch result already committed)
        assertThatNoException().isThrownBy(() -> service.onDispatchResultFinalized(
                new PaymentDispatchResultFinalizedEvent(BIZ, TRAN_DT, FILE_SEQ_NO)));

        // flag NOT flipped → batch retried later
        verify(scdulSumrRepo, never()).markErpProcComplete(any(), any(), any(), any());
    }

    /* ============================ ERP-key-only lookup (never amount/account) ============================ */

    @Test void usesImportedErpReferenceKeysOnly() {
        haengsungTenant();
        ScdulPymnSumr s = scdulBatch();
        when(scdulSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(s));
        lenient().when(ovrsSumrRepo.findByBizKeyAndPayTranDtAndPayFileSeqNo(BIZ, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of());
        ScdulPymnSumrDetl d = scdulDetl("D1", "7", new BigDecimal("1000"));
        when(scdulDetlRepo.findByBizKeyAndScdulPymnNo(eq(BIZ), eq("100"), any())).thenReturn(List.of(d));
        when(dispatchResultResolver.resolve(eq(s), anyList())).thenReturn(Map.of("D1", success()));

        service.writeBackForFile(BIZ, TRAN_DT, FILE_SEQ_NO);

        ArgumentCaptor<BigDecimal> seq = ArgumentCaptor.forClass(BigDecimal.class);
        verify(oracle).updateDetailDispatchResult(eq(FDATE), eq(FNUM), seq.capture(),
                anyString(), any(), any(), anyString(), eq(TRAN_DT));
        // detail keyed by the imported ERP_FILE_SEQ (7), not an amount/account
        assertThat(seq.getValue()).isEqualByComparingTo(new BigDecimal("7"));
    }

    /* ================================== fixtures ================================== */

    private void haengsungTenant() {
        BusiInfm b = new BusiInfm();
        b.setBizKey(BIZ);
        b.setPrdtType("H");
        b.setDelYn("N");
        when(busiInfmRepository.findById(BIZ)).thenReturn(Optional.of(b));
    }

    private ScdulPymnSumr scdulBatch() {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ);
        s.setScdulPymnNo("100");
        s.setScdulPymnTypeCd("01");
        s.setTrnfYn("Y");
        s.setErpProcCmplYn("N");
        s.setErpFileDate(FDATE);
        s.setErpFileNum(FNUM);
        s.setPayTranDt(TRAN_DT);
        s.setPayFileSeqNo(FILE_SEQ_NO);
        return s;
    }

    private OvrsPymnSumr ovrsBatch() {
        OvrsPymnSumr o = new OvrsPymnSumr();
        o.setBizKey(BIZ);
        o.setOvrsPymnNo("OP1");
        o.setTrnfYn("Y");
        o.setErpProcCmplYn("N");
        o.setErpFileDate(FDATE);
        o.setErpFileNum(FNUM);
        o.setPayTranDt(TRAN_DT);
        o.setPayFileSeqNo(FILE_SEQ_NO);
        return o;
    }

    private ScdulPymnSumrDetl scdulDetl(String detlNo, String erpSeq, BigDecimal amt) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo("100");
        d.setScdulPymnDetlNo(detlNo);
        d.setErpFileDate(FDATE);
        d.setErpFileNum(FNUM);
        d.setErpFileSeq(new BigDecimal(erpSeq));
        d.setTranAmt(amt);
        return d;
    }

    private OvrsPymnSumrDetl ovrsDetl(String detlNo, String erpSeq, BigDecimal amt) {
        OvrsPymnSumrDetl d = new OvrsPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setOvrsPymnNo("OP1");
        d.setOvrsPymnDetlNo(detlNo);
        d.setErpFileDate(FDATE);
        d.setErpFileNum(FNUM);
        d.setErpFileSeq(new BigDecimal(erpSeq));
        d.setTrnfAmt(amt);
        return d;
    }

    /** Resolved result for a successful line (PROC_STAT='0', RS_CD='0000'). */
    private static DispatchResult success() {
        return new DispatchResult("0", null, "20260628090000", "20260628090000", "0000", null);
    }
}
