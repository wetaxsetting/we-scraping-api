package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * X-1 — the dispatch-time blocked-character guard must fail <b>loudly</b>, not silently.
 *
 * <h2>The defect</h2>
 * {@code PaymentBlockedCharGuard.assertCleanForDispatch} (audit task-44508, a WeCMS-side P0 gate with
 * no legacy counterpart) throws {@code BusinessException("WBE_PAYMENT_BLOCKED_CHARS")}. Neither rail
 * caught it:
 * <ul>
 *   <li><b>Realtime</b> — it propagated out of the {@code REQUIRES_NEW} unit, rolled the target back,
 *       and was swallowed by {@code RealtimeDispatchRegistrar}'s catch-and-log. The pay-target stayed
 *       at {@code '9'}.</li>
 *   <li><b>Bulk</b> — it escaped {@code buildAndDispatchFile} (whose {@code try} wrapped only the
 *       encode/write step), rolling back that split file. The claim had already committed separately,
 *       so the pay-file stayed at {@code '6'}.</li>
 * </ul>
 * Either way the row was <b>silently not dispatched</b>: no {@code '3'}, no {@code RS_MSG}, no ops
 * alert, no user-visible failure — the same stranding class as R-1/H-3.
 *
 * <h2>What is asserted</h2>
 * The gate itself is <b>not</b> under test and is deliberately kept (task-44508). What is asserted is
 * that its rejection is now recorded the way every other business rejection on each rail is recorded:
 * a terminal {@code PROC_STAT='3'} plus an ops alert carrying the field and the offending character(s).
 *
 * @see BulkReceptionStrandedFailureTest the sibling "never leave a claimed pay-file at '6'" invariant
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchBlockedCharFailureTest {

    private static final String TRAN_DT = "20251023";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String TRAN_SEQ = "1";
    private static final String FILE_SEQ_NO = "1";
    private static final String OUT_BANK = "970457";
    private static final String WDRL = "100001020691";
    private static final String PRTN = "WRB001";
    private static final String VAN = "GV00000001";

    /**
     * The real validator's message shape: field label + the blocked character(s). It is passed as
     * {@code args[0]} of the {@code BusinessException}, which is what both rails must surface.
     */
    private static final String BLOCKED_MSG = "Recipient Name contains blocked characters: @ $";

    private static BusinessException blocked() {
        return new BusinessException("WBE_PAYMENT_BLOCKED_CHARS", BLOCKED_MSG);
    }

    @Mock PaymentBlockedCharGuard blockedCharGuard;
    @Mock OutBankCdNormalizer outBankCdNormalizer;
    @Mock OpsAlertService opsAlertService;
    @Mock OperatingHoursService operatingHoursService;
    @Mock DispatchKeyGenerator dispatchKeyGenerator;
    @Mock TbFirmConfRepository firmConfRepository;

    // realtime rail
    @Mock TbPayTargetRepository payTargetRepository;
    @Mock TbFirmTranRepository firmTranRepository;
    @Mock RealtimeTransferService realtimeTransferService;

    // bulk rail
    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;
    @Mock EfsProperties efsProperties;
    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmFileDataRepository firmFileDataRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock SftpClient sftpClient;
    @Mock BulkTransferValidator bulkTransferValidator;
    @Mock org.springframework.transaction.PlatformTransactionManager perFileTxManager;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final FixedLengthCodec codec = new FixedLengthCodec();
    private final SftpProperties sftpProperties = new SftpProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RealtimeDispatchService realtimeService;
    private BulkReceptionService bulkService;

    @BeforeEach
    void setUp() {
        realtimeService = new RealtimeDispatchService(
                payTargetRepository, firmConfRepository, firmTranRepository, realtimeTransferService,
                dispatchKeyGenerator, opsAlertService, operatingHoursService,
                blockedCharGuard, outBankCdNormalizer);

        bulkService = new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, objectMapper,
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, codec, sftpClient, sftpProperties, bulkTransferValidator,
                opsAlertService, operatingHoursService,
                new BulkDispatchClaimTxService(payFileMstRepository),
                blockedCharGuard, outBankCdNormalizer, perFileTxManager);

        when(operatingHoursService.evaluateRealtime(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);
        when(bulkTransferValidator.validate(any(), any())).thenReturn(Optional.empty());
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(payTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payTargetRepository.claimForDispatch(any(), any(), any(), any())).thenReturn(1);
        when(firmTranRepository.existsByTranDtAndBizKeyAndTranSeq(any(), any(), any())).thenReturn(false);
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.claimForReception(any(), any(), any(), any())).thenReturn(1);
        when(payFileMstRepository.failClaimedPayFile(any(), any(), any(), any(), any())).thenReturn(1);
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        sftpProperties.setLocalUploadPath(System.getProperty("java.io.tmpdir"));

        // The guard rejects — the scenario under test on both rails.
        doThrow(blocked()).when(blockedCharGuard).assertCleanForDispatch(any(), any());
    }

    private TbFirmConf conf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setOutBankCd(OUT_BANK);
        c.setWdrlActNo(WDRL);
        c.setBankPrtnId(PRTN);
        c.setVanOrgCd(VAN);
        c.setFileType("WRB");
        c.setOrgStat("1");
        c.setBatTranBranchDiv("N");
        c.setBatTranBranchLimit(BigDecimal.valueOf(100));
        c.setRegDttm("20251023000000");
        return c;
    }

    /* ───────────────────────── realtime rail ───────────────────────── */

    @Test
    @DisplayName("Realtime: a blocked character fails the pay-target to '3' and alerts — never silent")
    void realtime_blockedChar_failsPayTargetAndAlerts() {
        TbPayTarget p = new TbPayTarget();
        p.setTranDt(TRAN_DT);
        p.setBizKey(BIZ_KEY);
        p.setTranSeq(TRAN_SEQ);
        p.setOutBankCd(OUT_BANK);
        p.setWdrlActNo(WDRL);
        p.setAntDivCd("INTER");
        p.setTarBankCd(OUT_BANK);
        p.setFincMdaNo("0681000001");
        p.setFincMdaOwnerNm("NGUYEN@VAN$A");
        p.setTranAmt(new BigDecimal("1000"));
        p.setTrscCcyCd("VND");
        p.setWithdCcyCd("VND");
        p.setProcStat(ProcStat.WAIT.code());
        when(payTargetRepository.findByBizKeyAndTranDtAndTranSeq(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .thenReturn(Optional.of(p));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf()));

        // Must NOT propagate — the caller only logs, which is how the row went silent.
        assertThatCode(() -> realtimeService.dispatch(BIZ_KEY, TRAN_DT, TRAN_SEQ))
                .doesNotThrowAnyException();

        // Terminal FAIL recorded, with PROC_DTTM stamped like every other failure path.
        ArgumentCaptor<TbPayTarget> cap = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(payTargetRepository, atLeastOnce()).save(cap.capture());
        assertThat(cap.getValue().getProcStat()).isEqualTo(ProcStat.FAIL.code());
        assertThat(cap.getValue().getProcDttm()).isNotBlank();

        // Ops alert names the field + character(s).
        verify(opsAlertService).notify(eqVan(), contains(BLOCKED_MSG));

        // And nothing was sent to the bank.
        verify(realtimeTransferService, never()).transfer(any(), anyString());
        verify(realtimeTransferService, never()).transfer(any(), any(), anyString());
    }

    /* ───────────────────────── bulk rail ───────────────────────── */

    @Test
    @DisplayName("Bulk: a blocked character fails the pay-file to '3' with RS_MSG and alerts — nothing sent")
    void bulk_blockedChar_failsPayFileWithRsMsgAndAlerts() {
        TbPayFileMst pay = new TbPayFileMst();
        pay.setTranDt(TRAN_DT);
        pay.setBizKey(BIZ_KEY);
        pay.setFileSeqNo(FILE_SEQ_NO);
        pay.setOutBankCd(OUT_BANK);
        pay.setWdrlActNo(WDRL);
        pay.setBankPrtnId(PRTN);
        pay.setFundTp("0");
        pay.setTranTp("1");
        pay.setProcStat(ProcStat.WAIT.code());
        pay.setReqCnt(BigDecimal.ONE);
        pay.setReqAmt(new BigDecimal("1000"));

        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.ONE);
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("0681000001");
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal("1000"));
        d.setBankChnlDiv("T");
        d.setAntDivCd("C");
        d.setFincMdaOwnerNm("NGUYEN@VAN$A");
        List<TbPayFileData> details = new ArrayList<>(List.of(d));

        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(pay));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf()));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(details);
        when(payFileDataRepository.aggregateValidationCounts(anyString(), anyString(), anyString()))
                .thenReturn(org.mockito.Mockito.mock(BulkValidationCounts.class));

        assertThatCode(() -> bulkService.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .doesNotThrowAnyException();

        // The pay-file reaches a terminal '3' — never left at the claimed '6'.
        ArgumentCaptor<TbPayFileMst> cap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository, atLeastOnce()).save(cap.capture());
        List<String> statuses = cap.getAllValues().stream().map(TbPayFileMst::getProcStat).toList();
        assertThat(statuses).contains(ProcStat.FAIL.code());
        assertThat(statuses).doesNotContain(ProcStat.REQ.code());

        // RS_MSG names the field + character(s) (TB_PAY_FILE_MST has the column; TB_PAY_TARGET does not).
        assertThat(cap.getAllValues().stream().map(TbPayFileMst::getRsMsg).filter(java.util.Objects::nonNull).toList())
                .anySatisfy(msg -> assertThat(msg).contains(BLOCKED_MSG));

        verify(opsAlertService).notify(eqVan(), contains(BLOCKED_MSG));

        // Pre-egress: nothing uploaded, no 00301 issued.
        verify(sftpClient, never()).upload(anyString(), anyString());
    }

    private static String eqVan() {
        return org.mockito.ArgumentMatchers.eq(VAN);
    }
}
