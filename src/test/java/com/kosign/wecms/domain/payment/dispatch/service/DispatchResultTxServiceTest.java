package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTranId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DispatchResultTxService} — the atomic original-firm-tran (U004) +
 * pay-target (U001) finalize (Item 1 / audit M-7).
 *
 * <p>Rollback itself is a Spring {@code @Transactional(REQUIRES_NEW)} guarantee that a
 * Mockito unit test cannot exercise against a real transaction manager (the project ships no
 * Testcontainers). These tests therefore assert the observable pre-conditions of rollback:
 * on a firm-tran save failure the pay-target update is never attempted, and on a pay-target
 * save failure the exception propagates — so the surrounding {@code REQUIRES_NEW} boundary
 * rolls the firm-tran update back with it. The "one failed row does not block later rows"
 * scenario is covered at the scan level in {@code ResultCheckPollTest.oneFailedRow_...}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchResultTxServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String DT = "20260721";
    private static final String SEQ = "500";

    @Mock TbFirmTranRepository firmTranRepository;
    @Mock TbPayTargetRepository payTargetRepository;

    private DispatchResultTxService service;

    @BeforeEach
    void setUp() {
        service = new DispatchResultTxService(firmTranRepository, payTargetRepository);
        when(firmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private TbFirmTran firmRow() {
        TbFirmTran t = new TbFirmTran();
        t.setTranDt(DT);
        t.setBizKey(BIZ);
        t.setTranSeq(SEQ);
        t.setTranTp("202");
        t.setProcStat("6");
        return t;
    }

    private TbPayTarget payRow() {
        TbPayTarget p = new TbPayTarget();
        p.setTranDt(DT);
        p.setBizKey(BIZ);
        p.setTranSeq(SEQ);
        p.setProcStat("6");
        return p;
    }

    /** 1. Both updates succeed → both rows written with the confirmed status + owner. */
    @Test
    void bothUpdatesSucceed_writesFirmTranAndPayTarget() {
        when(firmTranRepository.findById(new TbFirmTranId(DT, BIZ, SEQ))).thenReturn(Optional.of(firmRow()));
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ, DT, SEQ)).thenReturn(Optional.of(payRow()));

        service.finalizeOriginalAndPayTarget(BIZ, DT, SEQ, "0", "0000", "OK", "20260721120000", "OWNER");

        ArgumentCaptor<TbFirmTran> firm = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).save(firm.capture());
        assertThat(firm.getValue().getProcStat()).isEqualTo("0");
        assertThat(firm.getValue().getRsCd()).isEqualTo("0000");     // D-3: RS_CD set on success too
        assertThat(firm.getValue().getFincMdaOwnerNm()).isEqualTo("OWNER");

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository).save(pay.capture());
        assertThat(pay.getValue().getProcStat()).isEqualTo("0");
        assertThat(pay.getValue().getFincMdaOwnerNm()).isEqualTo("OWNER");
    }

    /** 2. Firm-tran update fails → pay-target update is NEVER attempted; exception propagates
     *     (the REQUIRES_NEW boundary then rolls back — both rows stay unchanged). */
    @Test
    void firmTranUpdateFails_payTargetNotTouched_andPropagates() {
        when(firmTranRepository.findById(new TbFirmTranId(DT, BIZ, SEQ))).thenReturn(Optional.of(firmRow()));
        when(firmTranRepository.save(any())).thenThrow(new RuntimeException("firm-tran DB error"));

        assertThatThrownBy(() ->
                service.finalizeOriginalAndPayTarget(BIZ, DT, SEQ, "0", "0000", "OK", "20260721120000", "OWNER"))
                .isInstanceOf(RuntimeException.class);

        verify(payTargetRepository, never()).save(any());
    }

    /** 3. Pay-target update fails → exception propagates so the whole REQUIRES_NEW tx rolls back
     *     (firm-tran update is undone with it — never firm-tran '0' while pay-target stays '6'). */
    @Test
    void payTargetUpdateFails_propagatesForRollback() {
        when(firmTranRepository.findById(new TbFirmTranId(DT, BIZ, SEQ))).thenReturn(Optional.of(firmRow()));
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ, DT, SEQ)).thenReturn(Optional.of(payRow()));
        when(payTargetRepository.save(any())).thenThrow(new RuntimeException("pay-target DB error"));

        assertThatThrownBy(() ->
                service.finalizeOriginalAndPayTarget(BIZ, DT, SEQ, "3", "9999", "err", "20260721120000", null))
                .isInstanceOf(RuntimeException.class);

        // The firm-tran save was attempted (and, under a real REQUIRES_NEW tx, rolls back with the
        // failed pay-target save — asserted via propagation here).
        verify(firmTranRepository).save(any());
    }

    /** 4. Missing original firm-tran → no-op, pay-target left untouched (anomalous disappearance). */
    @Test
    void missingOriginalFirmTran_isNoOp() {
        when(firmTranRepository.findById(new TbFirmTranId(DT, BIZ, SEQ))).thenReturn(Optional.empty());

        service.finalizeOriginalAndPayTarget(BIZ, DT, SEQ, "0", "0000", "OK", "20260721120000", "OWNER");

        verify(firmTranRepository, never()).save(any());
        verify(payTargetRepository, never()).save(any());
    }

    /** 5. Missing pay-target → firm-tran still finalized (legacy U001 on a missing row = 0 rows, no error). */
    @Test
    void missingPayTarget_finalizesFirmTranOnly() {
        when(firmTranRepository.findById(new TbFirmTranId(DT, BIZ, SEQ))).thenReturn(Optional.of(firmRow()));
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ, DT, SEQ)).thenReturn(Optional.empty());

        service.finalizeOriginalAndPayTarget(BIZ, DT, SEQ, "0", "0000", "OK", "20260721120000", "OWNER");

        verify(firmTranRepository).save(any());
        verify(payTargetRepository, never()).save(any());
    }
}
