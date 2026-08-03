package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs202Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>202-leg pinning test: a transport failure does not resend.</b>
 *
 * <p>Closes {@code docs/testing/integration-test-audit.md} §10.2 / §11 row 5 — the 203 leg already had
 * {@code ResultCheckPollTest.transportFailure_leavesOriginalUntouched_but203RowRecordedFail}; the 202
 * leg had no equivalent. Money moves on the 202, so "no automatic resend" is the property that keeps a
 * lost response from becoming a double payment.
 *
 * <h2>Legacy source of truth — {@code NAPAS_REAL_TIME_BATCH}</h2>
 *
 * {@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/NAPAS_REAL_TIME_BATCH.java}, read
 * end-to-end around the 202 call:
 *
 * <pre>
 *  :567  String respData_202 = ConnectionUtil.connectUrlByPost(url, data_202, ...);
 *  :570  // 무응답 경우 다음으로  ("if no response, move to the next one")
 *        if (respData_202 == null || respData_202.length() == 0
 *            || rtn_202.getString("RC").equals("G999")) {
 *  :573      // 입금이체 대상 상태 처리중 업데이트(TB_PAY_TARGET)
 *            idoInPTU003 = TB_PAY_TARGET_U001;  PROC_STAT = WCConst.PROC_ST_REQ;   // '6'
 *  :588      Thread.sleep(per_request_delay);
 *  :589      continue;                                                            // NEXT target
 *        }
 * </pre>
 *
 * Three properties fall out of that block, and all three are asserted here:
 *
 * <ol>
 *   <li><b>The 202 is sent exactly once.</b> {@code continue} advances the loop to the next
 *       {@code TB_PAY_TARGET} row; there is no retry of the current one, no inner loop, and no
 *       synchronous 203 recovery. (Legacy's own retry surface, BATCH2, exists only for the
 *       <i>bulk</i> 00302 leg — see §10.2 — never for the realtime 202.)</li>
 *   <li><b>The row is parked at {@code PROC_STAT='6'}</b> — not {@code '3'} (which would claim a
 *       failure the bank never reported) and not left at {@code '9'} (which every sweep would treat as
 *       undispatched and re-drive). Legacy also leaves {@code TB_FIRM_TRAN} at the {@code '6'} its
 *       C001 insert wrote: the no-response branch issues <b>no</b> {@code TB_FIRM_TRAN_U004}.</li>
 *   <li><b>Nothing re-drives a {@code '6'}.</b> The only thing that may touch it afterwards is the
 *       scheduled {@code PROCESSING_RESULT_CHECK} 203 <i>inquiry</i> (read-only with respect to the
 *       transfer), which owns the eventual {@code 6 → 0/3}. The refactor's recovery sweep selects
 *       {@code ProcStat.WAIT} ('9') only, so the parked {@code '6'} is structurally out of its reach.</li>
 * </ol>
 *
 * <p>The refactor treats three distinct gateway outcomes as the same "no definitive response" case
 * ({@code RealtimeTransferService:182-214}): a null envelope (transport failure), {@code RC=G999}, and
 * a {@code RC=0000} whose response {@code VV} does not verify (audit H-5 — a tampered success is not a
 * trusted success). All three must obey property 1, so all three are covered.
 *
 * <p><b>Scope note.</b> This class asserts the <i>absence</i> of a resend. The complementary
 * "pending leaves both rows at '6'" state assertion lives in
 * {@code RealtimeDispatchServiceTest.pendingResult_leavesBothRowsAt6_forResultCheck}, and the
 * "no synchronous 203" assertion in
 * {@code RealtimeTransferServiceTest.g999_returns_pending_and_does_no_synchronous_203}; both are
 * re-asserted here only where they are inseparable from the no-resend property.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealtimeTransportFailureNoResendTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN = "ORG000001";
    private static final String TNO = "0000000123";

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String TRAN_DT = "20260613";
    private static final String TRAN_SEQ = "0000042";

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final EfsProperties efsProperties = new EfsProperties();

    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;

    @Mock TbPayTargetRepository payTargetRepository;
    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock TbFirmTranRepository firmTranRepository;
    @Mock RealtimeTransferService realtimeTransferService;
    @Mock DispatchKeyGenerator dispatchKeyGenerator;
    @Mock com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    @Mock OperatingHoursService operatingHoursService;
    @Mock com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard blockedCharGuard;
    @Mock com.kosign.wecms.domain.payment.common.OutBankCdNormalizer outBankCdNormalizer;
    @Mock BulkReceptionService bulkReceptionService;
    @Mock ResultCheckService resultCheckService;

    /** Real service under test for the transport leg (layer 1). */
    private RealtimeTransferService transferService;
    /** Real service under test for the dispatch seam (layer 2) — the transfer leg is mocked there. */
    private RealtimeDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        transferService = new RealtimeTransferService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, mapper);

        dispatchService = new RealtimeDispatchService(
                payTargetRepository, firmConfRepository, firmTranRepository, realtimeTransferService,
                dispatchKeyGenerator, opsAlertService, operatingHoursService,
                blockedCharGuard, outBankCdNormalizer);

        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);

        when(operatingHoursService.evaluateRealtime(any(), any()))
                .thenReturn(OperatingHoursDecision.DISPATCH);
        TbFirmConf conf = mock(TbFirmConf.class);
        when(conf.getVanOrgCd()).thenReturn(VAN);
        when(conf.getBankPrtnId()).thenReturn("PRTN01");
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf));
        when(firmTranRepository.existsByTranDtAndBizKeyAndTranSeq(any(), any(), any())).thenReturn(false);
        when(payTargetRepository.claimForDispatch(any(), any(), any(), any())).thenReturn(1);
        when(firmTranRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(firmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(any(), any())).thenReturn("0000099");
    }

    /* ─────────────────────────── fixtures ─────────────────────────── */

    /** Shinhan + VND → ordinary domestic path, no WGVN_EFS_204 pre-step. */
    private static RealtimeTransferCommand cmd() {
        return new RealtimeTransferCommand(
                VAN, "970424", VAN, "VND", "1000000", "0",
                "A", "12345678901111", "970436", "98765432100000",
                "NGUYEN VAN B", "salary", "NAPAS", null);
    }

    /** {@code RC=G999} with no EV — the gateway's "general error / no definitive response". */
    private EfsEnvelope g999() {
        return new EfsEnvelope(VAN, null, "20240101000000", "TNO-RESP",
                null, null, "AES", "HmacSHA256", "G999", "ERR");
    }

    /** {@code RC=0000} whose EV/VV pair does not verify (audit H-5). */
    private EfsEnvelope successWithBadVv() throws Exception {
        String respJson = mapper.writeValueAsString(
                new Efs202Response("0", "5000000", "0", "NGUYEN VAN B", "NAPAS"));
        String ev = crypto.encryptEv(respJson, TEST_KEY, "20240101000000");
        return new EfsEnvelope(VAN, null, "20240101000000", "TNO-RESP",
                ev, "TAMPERED-VV", "AES", "HmacSHA256", "0000", "OK");
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

    /* ══════════════ layer 1 — the transport leg sends the 202 exactly once ══════════════ */

    /**
     * Legacy {@code :570} treats a null / empty response body as "no response" and {@code continue}s.
     * The refactor's {@code EfsApiClient} surfaces that as a {@code null} envelope; the service must
     * return pending after <b>one</b> send — no retry, no 203, no 204.
     */
    @Test
    @DisplayName("202 transport failure (null envelope): sent exactly once, no resend, no 203")
    void nullEnvelope_sendsThe202Once_andNeverResends() {
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(null);

        RealtimeTransferResult result = transferService.transfer(cmd(), null, TNO);

        assertTrue(result.pending(), "a lost response is pending, never a success or a fail");
        verify(efsApiClient, times(1)).send(eq(EfsApiClient.EFS_202), any());
        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_203), any());
        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_204), any());
        verify(efsApiClient, times(1)).send(anyString(), any());
    }

    /** {@code RC=G999} — legacy's explicit third no-response condition at {@code :570}. */
    @Test
    @DisplayName("202 RC=G999: sent exactly once, no resend, no 203")
    void g999_sendsThe202Once_andNeverResends() {
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(g999());

        RealtimeTransferResult result = transferService.transfer(cmd(), null, TNO);

        assertTrue(result.pending());
        verify(efsApiClient, times(1)).send(eq(EfsApiClient.EFS_202), any());
        verify(efsApiClient, times(1)).send(anyString(), any());
    }

    /**
     * Audit H-5: a {@code RC=0000} whose response {@code VV} fails verification is not a trusted
     * success. It is folded into the same pending branch — and, critically, must not be retried: the
     * transfer may well have happened, so a resend is a double payment.
     */
    @Test
    @DisplayName("202 RC=0000 with an unverifiable VV: sent exactly once, no resend")
    void unverifiableVv_sendsThe202Once_andNeverResends() throws Exception {
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(successWithBadVv());

        RealtimeTransferResult result = transferService.transfer(cmd(), null, TNO);

        assertTrue(result.pending());
        verify(efsApiClient, times(1)).send(eq(EfsApiClient.EFS_202), any());
        verify(efsApiClient, times(1)).send(anyString(), any());
    }

    /**
     * Calling {@code transfer} twice is two <i>caller-driven</i> sends, one each — the service holds no
     * internal retry state that could turn one call into two sends. Pins the shape of the leg: the
     * decision to send again is never taken inside it.
     */
    @Test
    @DisplayName("N calls -> N sends: the transport leg has no internal retry")
    void repeatedCalls_produceOneSendEach_noAmplification() {
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(null);

        transferService.transfer(cmd(), null, TNO);
        transferService.transfer(cmd(), null, TNO);

        verify(efsApiClient, times(2)).send(eq(EfsApiClient.EFS_202), any());
        verify(efsApiClient, times(2)).send(anyString(), any());
    }

    /* ══════════════ layer 2 — the dispatch seam parks at '6' and does not retry ══════════════ */

    /**
     * The seam calls the transfer leg once and, on a pending outcome, writes {@code '6'} to both rows
     * and stops — matching legacy {@code :573-589} ({@code TB_PAY_TARGET_U001 → '6'}, no
     * {@code TB_FIRM_TRAN_U004}, then {@code continue}).
     */
    @Test
    @DisplayName("pending 202 outcome: transfer attempted once, both rows parked at '6'")
    void pendingOutcome_attemptsTheTransferOnce_andParksBothRowsAtSix() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(payTarget(ProcStat.WAIT.code())));
        when(realtimeTransferService.transfer(any(), any()))
                .thenReturn(RealtimeTransferResult.ofPending());

        dispatchService.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        // Exactly one transfer attempt for this target — no retry loop in the seam (legacy `continue`).
        verify(realtimeTransferService, times(1)).transfer(any(), any());

        ArgumentCaptor<TbFirmTran> firm = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(firmTranRepository).save(firm.capture());
        assertEquals(ProcStat.REQ.code(), firm.getValue().getProcStat(),
                "the 202 firm-tran row stays '6' (legacy writes no U004 in the no-response branch)");

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository).save(pay.capture());
        assertEquals(ProcStat.REQ.code(), pay.getValue().getProcStat(),
                "legacy :576 writes PROC_ST_REQ ('6') — never '3', never back to '9'");
    }

    /**
     * The parked {@code '6'} is not re-dispatchable. A second hand-off (a duplicate after-commit hook,
     * a second node, an operator re-trigger) reads the row, sees it is no longer WAIT, and returns
     * before the transfer leg — so the transport failure cannot be turned into a second 202.
     *
     * <p>Legacy has the same structural guard: {@code TB_PAY_TARGET_R001} selects
     * {@code PROC_STAT = '9'} only, so a row parked at {@code '6'} is simply not in the next run's
     * candidate set.
     */
    @Test
    @DisplayName("a target parked at '6' is never re-dispatched (no second 202)")
    void targetParkedAtSix_isNotReDispatched() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(payTarget(ProcStat.REQ.code())));

        dispatchService.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        verify(realtimeTransferService, never()).transfer(any(), any());
        verify(firmTranRepository, never()).saveAndFlush(any());
        // Not even the claim is attempted — the read gate returns first.
        verify(payTargetRepository, never()).claimForDispatch(any(), any(), any(), any());
    }

    /**
     * And when two drivers race with a stale {@code '9'} read, the atomic claim — not the read gate —
     * is what stops the second 202: the loser's conditional UPDATE matches zero rows and it returns
     * before the transfer leg.
     */
    @Test
    @DisplayName("lost atomic claim stops the second 202 even on a stale WAIT read")
    void lostClaim_stopsTheSecond202() {
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(payTarget(ProcStat.WAIT.code())));
        when(payTargetRepository.claimForDispatch(any(), any(), any(), any())).thenReturn(0);

        dispatchService.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ);

        verify(realtimeTransferService, never()).transfer(any(), any());
        verify(firmTranRepository, never()).saveAndFlush(any());
    }

    /* ══════════════ layer 3 — no sweep re-drives a parked '6' ══════════════ */

    /**
     * The recovery sweep's candidate query is pinned to {@code ProcStat.WAIT} ('9')
     * ({@code StrandedRecoveryService:159} / {@code :184}), so a target parked at {@code '6'} by a
     * transport failure is structurally outside its reach — it can only be resolved by the 203
     * result-check, never by a resend of the 202.
     *
     * <p>This is the sweep-level half of the no-resend property, and the one a future change is most
     * likely to break: widening the sweep predicate to {@code '6'} would silently convert every lost
     * 202 response into a second payment.
     */
    @Test
    @DisplayName("recovery sweep selects only PROC_STAT='9' — a parked '6' is never re-driven")
    void recoverySweep_selectsOnlyWaitRows_soParkedSixIsNeverResent() {
        StrandedRecoveryService recovery = new StrandedRecoveryService(
                payTargetRepository, payFileMstRepository, bulkReceptionService,
                resultCheckService, com.kosign.wecms.common.lock.JobLockGuardStubs.alwaysFree(),
                mock(RealtimeDispatchQueue.class));
        ReflectionTestUtils.setField(recovery, "enabled", true);
        ReflectionTestUtils.setField(recovery, "graceMinutes", 30L);

        when(payTargetRepository.findStrandedPayTargets(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());
        when(payFileMstRepository.findStrandedPayFiles(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        recovery.recoverOnce();

        ArgumentCaptor<String> targetStat = ArgumentCaptor.forClass(String.class);
        verify(payTargetRepository).findStrandedPayTargets(
                targetStat.capture(), anyString(), any(Pageable.class));
        assertEquals(ProcStat.WAIT.code(), targetStat.getValue(),
                "widening this to '6' would resend every lost 202 — legacy R001 selects '9' only");

        ArgumentCaptor<String> fileStat = ArgumentCaptor.forClass(String.class);
        verify(payFileMstRepository).findStrandedPayFiles(
                fileStat.capture(), anyString(), any(Pageable.class));
        assertEquals(ProcStat.WAIT.code(), fileStat.getValue(),
                "the bulk rail's sweep is bounded to '9' for the same reason");

        // The sweep found nothing, so nothing was re-driven on either rail.
        verify(bulkReceptionService, never()).receive(anyString(), anyString(), anyString());
    }

    /**
     * A stranded {@code '9'} row IS re-driven — the flip side that makes the assertion above
     * meaningful. Without this, "never re-drives" could pass trivially on a broken sweep.
     */
    @Test
    @DisplayName("a stranded '9' target IS re-driven (proves the sweep is not inert)")
    void strandedWaitTarget_isReDriven() {
        // C-6: the sweep re-drives by handing the row to the shared paced drain, not by calling the
        // gateway itself — so "re-driven" is asserted on the queue.
        RealtimeDispatchQueue queueSpy = mock(RealtimeDispatchQueue.class);
        StrandedRecoveryService recovery = new StrandedRecoveryService(
                payTargetRepository, payFileMstRepository,
                bulkReceptionService, resultCheckService,
                com.kosign.wecms.common.lock.JobLockGuardStubs.alwaysFree(), queueSpy);
        ReflectionTestUtils.setField(recovery, "enabled", true);
        ReflectionTestUtils.setField(recovery, "graceMinutes", 30L);

        when(payTargetRepository.findStrandedPayTargets(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(payTarget(ProcStat.WAIT.code())));
        when(payFileMstRepository.findStrandedPayFiles(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.<TbPayFileMst>of());

        recovery.recoverOnce();

        verify(queueSpy).enqueue(BIZ_KEY, TRAN_DT, TRAN_SEQ);
    }
}
