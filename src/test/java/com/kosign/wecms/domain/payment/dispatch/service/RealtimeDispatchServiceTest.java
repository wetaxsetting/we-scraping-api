package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.file.VietnameseText;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs204Response;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RealtimeDispatchService} — the C2 real-time 202 write seam.
 * Mocks the three repositories + {@link RealtimeTransferService}; asserts the
 * {@code TB_FIRM_TRAN} (C001/U004) + {@code TB_PAY_TARGET} (U001) writes and the two
 * idempotency gates. The 204 FX leg and the 203→pay-target propagation are out of
 * C2 scope and not exercised here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealtimeDispatchServiceTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String TRAN_DT = "20260613";
    private static final String TRAN_SEQ = "0000042";
    /** Independent seq the 204 FX row allocates (legacy {@code ccy_exc_seq}). */
    private static final String CCY_EXC_SEQ = "0000099";
    private static final String WOORI = "970457";

    @Mock TbPayTargetRepository payTargetRepository;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock TbFirmTranRepository firmTranRepository;
    @Mock RealtimeTransferService realtimeTransferService;
    @Mock DispatchKeyGenerator dispatchKeyGenerator;

    private RealtimeDispatchService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeDispatchService(
                payTargetRepository, firmConfRepository, firmTranRepository, realtimeTransferService,
                dispatchKeyGenerator);

        TbFirmConf conf = mock(TbFirmConf.class);
        when(conf.getVanOrgCd()).thenReturn("VAN01");
        when(conf.getBankPrtnId()).thenReturn("PRTN01");
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf));

        when(firmTranRepository.existsByTranDtAndBizKeyAndTranSeq(any(), any(), any())).thenReturn(false);
        when(firmTranRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(firmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(any(), any())).thenReturn(CCY_EXC_SEQ);
    }

    private TbPayTarget payTarget(String procStat) {
        TbPayTarget p = new TbPayTarget();
        p.setTranDt(TRAN_DT);
        p.setBizKey(BIZ_KEY);
        p.setTranSeq(TRAN_SEQ);
        p.setOutBankCd("970436");
        p.setWdrlActNo("700005285299");
        p.setRegTm("090000");
        p.setAntDivCd("INTER");
        p.setTarBankCd("970436");
        p.setFincMdaNo("0681000001");
        p.setFincMdaOwnerNm("Nguyễn Văn A");
        p.setTranAmt(new BigDecimal("10.50"));
        p.setTranUsag("Thanh toán");
        p.setTrscCcyCd("VND");
        p.setWithdCcyCd("VND");
        p.setProcStat(procStat);
        return p;
    }

    private ResultCheckInquiryResult recovery(String orgRespCd) {
        return new ResultCheckInquiryResult(
                TRAN_DT, TRAN_SEQ, "090101", orgRespCd, "msg",
                "700005285299", "VND", "10", "50", "0681000001",
                "Owner From 203", "wmemo", "tmemo");
    }

    private void stubWaiting() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(payTarget("9")));
    }

    @Test
    void directSuccess_writesFirmTranSixThenZero_andPayTargetZero() {
        stubWaiting();
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "1000000", "00", "Owner From 202", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        // Insert leg: firm-tran created at '6', reusing the pay-target TRAN_SEQ.
        ArgumentCaptor<TbFirmTran> insert = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).saveAndFlush(insert.capture());
        assertEquals("202", insert.getValue().getTranTp());
        assertEquals(TRAN_SEQ, insert.getValue().getTranSeq());

        // Result leg: firm-tran '0' + balance; pay-target '0' + owner coalesced from the response.
        ArgumentCaptor<TbFirmTran> upd = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).save(upd.capture());
        assertEquals("0", upd.getValue().getProcStat());
        assertEquals("0000", upd.getValue().getRsCd());
        assertEquals(new BigDecimal("1000000.00"), upd.getValue().getBalAmt());
        assertEquals("0", upd.getValue().getBalAmtSign());

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository).save(pay.capture());
        assertEquals("0", pay.getValue().getProcStat());
        assertEquals("Owner From 202", pay.getValue().getFincMdaOwnerNm());
    }

    @Test
    void commandIsBuiltWithToneNormalizationAndSplitAmount() {
        stubWaiting();
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "0", "00", "Owner", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        ArgumentCaptor<RealtimeTransferCommand> cmd = ArgumentCaptor.forClass(RealtimeTransferCommand.class);
        verify(realtimeTransferService).transfer(cmd.capture());
        assertEquals("VAN01", cmd.getValue().vanOrgCd());
        assertEquals("970436", cmd.getValue().bankCd());
        assertEquals("PRTN01", cmd.getValue().prtnId());
        assertEquals("VND", cmd.getValue().currenCd());
        assertEquals("10", cmd.getValue().amt());
        assertEquals("50", cmd.getValue().dcmAmt());
        assertEquals("A", cmd.getValue().tarFincMdaDiv());
        assertEquals(VietnameseText.replaceTone("Nguyễn Văn A"), cmd.getValue().fincMdaOwnerNm());
        assertEquals(VietnameseText.replaceTone("Thanh toán"), cmd.getValue().tranUse());
    }

    @Test
    void gatewayError_writesFirmTranAndPayTargetFail_withGatewayRc() {
        stubWaiting();
        when(realtimeTransferService.transfer(any()))
                .thenThrow(new BusinessException("WBE_EFS_GATEWAY_ERROR", "G998"));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        ArgumentCaptor<TbFirmTran> upd = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).save(upd.capture());
        assertEquals("3", upd.getValue().getProcStat());
        assertEquals("G998", upd.getValue().getRsCd());

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository).save(pay.capture());
        assertEquals("3", pay.getValue().getProcStat());
    }

    @Test
    void g999RecoveryConfirmedSuccess_marksBothZero() {
        stubWaiting();
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(true, null, null, null, null, null, recovery("0000")));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        ArgumentCaptor<TbFirmTran> upd = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).save(upd.capture());
        assertEquals("0", upd.getValue().getProcStat());
        assertEquals("0000", upd.getValue().getRsCd());
        assertNull(upd.getValue().getBalAmt(), "recovery path carries no balance");

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository).save(pay.capture());
        assertEquals("0", pay.getValue().getProcStat());
        assertEquals("Owner From 203", pay.getValue().getFincMdaOwnerNm());
    }

    @Test
    void g999RecoveryConfirmedFail_marksBothFail() {
        stubWaiting();
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(true, null, null, null, null, null, recovery("0001")));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        ArgumentCaptor<TbFirmTran> upd = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).save(upd.capture());
        assertEquals("3", upd.getValue().getProcStat());

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository).save(pay.capture());
        assertEquals("3", pay.getValue().getProcStat());
    }

    @Test
    void gate1_payTargetNotWaiting_isNoOp() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(payTarget("6")));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        verify(realtimeTransferService, never()).transfer(any());
        verify(firmTranRepository, never()).saveAndFlush(any());
    }

    @Test
    void gate1_payTargetMissing_isNoOp() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.empty());

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        verify(realtimeTransferService, never()).transfer(any());
        verify(firmTranRepository, never()).saveAndFlush(any());
    }

    @Test
    void gate2_firmTranAlreadyExists_isNoOp() {
        stubWaiting();
        when(firmTranRepository.existsByTranDtAndBizKeyAndTranSeq(TRAN_DT, BIZ_KEY, TRAN_SEQ)).thenReturn(true);

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        verify(realtimeTransferService, never()).transfer(any());
        verify(firmTranRepository, never()).saveAndFlush(any());
    }

    @Test
    void firmConfMissing_throwsIllegalState_andDoesNotCallGateway() {
        stubWaiting();
        when(firmConfRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ));
        verify(realtimeTransferService, never()).transfer(any());
    }

    // ── 204 FX persistence (Woori / non-VND pre-step) ───────────────────────────

    /** A Woori + USD pay-target → triggers the WGVN_EFS_204 FX pre-step. */
    private TbPayTarget wooriUsdPayTarget() {
        TbPayTarget p = payTarget("9");
        p.setOutBankCd(WOORI);
        p.setTrscCcyCd("USD");
        p.setWithdCcyCd("USD");
        p.setTranAmt(new BigDecimal("100.00"));
        return p;
    }

    /** 204 response: rate 24500.00, withd 100.00 USD → dep 2,450,000.00 VND. */
    private Efs204Response fx204() {
        return new Efs204Response("24500", "00", "100", "00", "VND", "2450000", "00");
    }

    private TbFirmTran captured204Row(java.util.List<TbFirmTran> rows) {
        return rows.stream().filter(r -> "204".equals(r.getTranTp())).findFirst().orElse(null);
    }

    /** 1. 204 success creates a TRAN_TP='204' audit row and updates the U005 fields. */
    @Test
    void woori204Success_creates204AuditRow_andUpdatesU005Fields() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(wooriUsdPayTarget()));
        when(realtimeTransferService.inquireFxRate(any())).thenReturn(fx204());
        when(realtimeTransferService.transfer(any(), any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "0", "00", "Owner", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        // The 204 row was persisted with its own (firm_seq_no) TRAN_SEQ.
        verify(dispatchKeyGenerator, times(1)).nextTranSeq(BIZ_KEY, TRAN_DT);
        ArgumentCaptor<TbFirmTran> flushed = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository, atLeast(1)).saveAndFlush(flushed.capture());
        TbFirmTran row204 = captured204Row(flushed.getAllValues());
        assertEquals(CCY_EXC_SEQ, row204.getTranSeq());

        // _U005 success: PROC_STAT='0' + RS_CD/MSG + the four FX columns.
        assertEquals("0", row204.getProcStat());
        assertEquals("0000", row204.getRsCd());
        assertEquals("VND", row204.getDepCcyCd());
        assertEquals(new BigDecimal("24500.00"), row204.getAplExcRate());
        assertEquals(new BigDecimal("100.00"), row204.getWithdAmt());
        assertEquals(new BigDecimal("2450000.00"), row204.getDepAmt());

        // The 202 still proceeds afterwards (FX fed into it).
        verify(realtimeTransferService).transfer(any(), any());
    }

    /** 2. 204 failure creates/updates a FAILED 204 audit row and skips the 202. */
    @Test
    void woori204Failure_persistsFailed204Row_andSkips202() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(wooriUsdPayTarget()));
        when(realtimeTransferService.inquireFxRate(any()))
                .thenThrow(new BusinessException("WBE_EFS_GATEWAY_ERROR", "G001"));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        // Exactly one firm-tran flushed — the 204 row — at PROC_STAT='3' with the gateway RC.
        ArgumentCaptor<TbFirmTran> flushed = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository, times(1)).saveAndFlush(flushed.capture());
        TbFirmTran row204 = flushed.getValue();
        assertEquals("204", row204.getTranTp());
        assertEquals("3", row204.getProcStat());
        assertEquals("G001", row204.getRsCd());

        // 202 skipped (neither overload called); pay-target untouched (legacy `continue`).
        verify(realtimeTransferService, never()).transfer(any());
        verify(realtimeTransferService, never()).transfer(any(), any());
        verify(payTargetRepository, never()).save(any());
    }

    /** 3. On 204 success the TB_PAY_TARGET FX columns are updated exactly like legacy (_U002). */
    @Test
    void woori204Success_updatesPayTargetU002FxFields() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(wooriUsdPayTarget()));
        when(realtimeTransferService.inquireFxRate(any())).thenReturn(fx204());
        when(realtimeTransferService.transfer(any(), any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "0", "00", "Owner", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        // U002 sets the four FX columns on the pay-target; U001 (202 result) does not clear them.
        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository, atLeast(1)).save(pay.capture());
        TbPayTarget saved = pay.getValue();
        assertEquals("VND", saved.getDepCcyCd());
        assertEquals(new BigDecimal("24500.00"), saved.getAplExcRate());
        assertEquals(new BigDecimal("100.00"), saved.getWithdAmt());
        assertEquals(new BigDecimal("2450000.00"), saved.getDepAmt());
    }

    /** 4a. VND path → no 204 row, no FX inquiry, no seq allocation. */
    @Test
    void vndPath_doesNotCreate204Row() {
        stubWaiting(); // default pay-target is bank 970436 + VND
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "0", "00", "Owner", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        verify(realtimeTransferService, never()).inquireFxRate(any());
        verify(dispatchKeyGenerator, never()).nextTranSeq(any(), any());
        ArgumentCaptor<TbFirmTran> flushed = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository, atLeast(1)).saveAndFlush(flushed.capture());
        assertFalse(flushed.getAllValues().stream().anyMatch(r -> "204".equals(r.getTranTp())),
                "VND path must not create a 204 row");
    }

    /** 4b. Non-Woori bank + non-VND → still no 204 row (the gate is Woori-specific). */
    @Test
    void nonWooriNonVnd_doesNotCreate204Row() {
        TbPayTarget p = payTarget("9");
        p.setOutBankCd("970436"); // not Woori
        p.setTrscCcyCd("USD");
        p.setWithdCcyCd("USD");
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(p));
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "0", "00", "Owner", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        verify(realtimeTransferService, never()).inquireFxRate(any());
        verify(dispatchKeyGenerator, never()).nextTranSeq(any(), any());
        verify(realtimeTransferService).transfer(any()); // 202 still runs via the single-arg path
    }

    /** 5. Existing 202 behavior (VND) is unchanged — single-arg transfer, one 202 row, no FX leg. */
    @Test
    void existing202Behavior_unchanged_forVnd() {
        stubWaiting();
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "1000000", "00", "Owner From 202", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        // No FX leg touched.
        verify(realtimeTransferService, never()).inquireFxRate(any());
        verify(realtimeTransferService, never()).transfer(any(), any());
        verify(dispatchKeyGenerator, never()).nextTranSeq(any(), any());

        // Exactly one firm-tran flushed (the 202 row), result mapped to '0'.
        ArgumentCaptor<TbFirmTran> flushed = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository, times(1)).saveAndFlush(flushed.capture());
        assertEquals("202", flushed.getValue().getTranTp());

        ArgumentCaptor<TbFirmTran> upd = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).save(upd.capture());
        assertEquals("0", upd.getValue().getProcStat());
    }

    @Test
    void splitAmount_handlesWholeNumber() {
        // 10.00 → amt="10", dcm="0" (no fractional cents)
        TbPayTarget p = payTarget("9");
        p.setTranAmt(new BigDecimal("10.00"));
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(p));
        when(realtimeTransferService.transfer(any()))
                .thenReturn(new RealtimeTransferResult(false, "0", "0", "00", "Owner", "INTER", null));

        service.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        ArgumentCaptor<RealtimeTransferCommand> cmd = ArgumentCaptor.forClass(RealtimeTransferCommand.class);
        verify(realtimeTransferService).transfer(cmd.capture());
        assertEquals("10", cmd.getValue().amt());
        assertEquals("0", cmd.getValue().dcmAmt());
        assertTrue(List.of("VND").contains(cmd.getValue().currenCd()));
    }
}
