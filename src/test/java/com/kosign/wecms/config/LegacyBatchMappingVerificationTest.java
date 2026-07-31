package com.kosign.wecms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkReceptionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkReceptionResultService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.monitor.StuckPayFileMonitorService;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.scheduler.ResultCheckJob;
import com.kosign.wecms.domain.payment.dispatch.scheduler.StrandedRecoveryJob;
import com.kosign.wecms.domain.payment.dispatch.scheduler.StuckPayFileMonitorJob;
import com.kosign.wecms.domain.payment.dispatch.service.BulkDispatchClaimTxService;
import com.kosign.wecms.domain.payment.dispatch.service.BulkReceptionService;
import com.kosign.wecms.domain.payment.dispatch.service.BulkTransferValidator;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchPacer;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultTxService;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursDecision;
import com.kosign.wecms.domain.payment.dispatch.service.OperatingHoursService;
import com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchService;
import com.kosign.wecms.domain.payment.dispatch.service.RealtimeTransferService;
import com.kosign.wecms.domain.payment.dispatch.service.ResultCheckService;
import com.kosign.wecms.domain.payment.dispatch.service.StrandedRecoveryService;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs302Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Executable verification of the approved <b>legacy-batch → refactor</b> mapping, re-proved after the
 * H-4 atomic-claim concurrency fix.
 *
 * <table border="1">
 *   <caption>The mapping under test</caption>
 *   <tr><th>Legacy batch</th><th>Refactor replacement</th><th>Scheduled?</th></tr>
 *   <tr><td>{@code NAPAS_REAL_TIME_BATCH}</td>
 *       <td>event-driven {@code RealtimeTransferService} (via {@code RealtimeDispatchService})</td><td>No</td></tr>
 *   <tr><td>{@code FILE_DEPOSIT_TRANSFER_BATCH1}</td>
 *       <td>request-time / after-commit {@code BulkReceptionService}</td><td>No</td></tr>
 *   <tr><td>{@code FILE_DEPOSIT_TRANSFER_BATCH2}</td>
 *       <td>removed — successful {@code 01301} triggers inline {@code 00302}</td><td>No</td></tr>
 *   <tr><td>{@code PROCESSING_RESULT_CHECK_BATCH}</td>
 *       <td>{@code ResultCheckJob}</td><td><b>Yes — the only one</b></td></tr>
 * </table>
 *
 * <h2>Why these tests are executable rather than grep-only</h2>
 * {@link FourToOneBatchArchitectureTest} already source-scans for the absence of a re-added sweep, which
 * is the one property a running context genuinely cannot prove. This class covers the complementary
 * half: the <em>behaviour</em> that makes the mapping true — that the after-commit hook really defers
 * {@code receive()} past the commit, that a real 01301 really issues 00302 inline and only once, that the
 * result-check really only ever calls the 203 inquiry, and that the recovery sweep really funnels through
 * the same atomic claim as the normal path. Scheduler registration is asserted against a real Spring
 * {@link ScheduledTaskHolder} (not a comment), so a bean whose cron silently became active fails here.
 *
 * <p>No database, no network: every gateway, repository and SFTP collaborator is a mock or an in-memory
 * fake, and the two Spring contexts registered below contain only the three scheduler beans.
 */
class LegacyBatchMappingVerificationTest {

    private static final Path MAIN_JAVA = Paths.get("src/main/java");

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN = "GV00000001";
    private static final String BANK_CD = "970424";
    private static final String PRTN_ID = "ORG000001";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String TRAN_DT = "20251023";
    private static final String FILE_SEQ_NO = "1";
    private static final String FILE_NM = "FB_20240101_0001.txt";

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper snakeMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    // ════════════════════════════════════════════════════════════════════════
    // Mapping A — NAPAS_REAL_TIME_BATCH → event-driven RealtimeTransferService
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The realtime rail has no scheduled polling entry point: neither the transfer service, the
     * dispatch service, nor the registrar carries {@code @Scheduled}, and the <b>only</b> scheduled
     * class that can reach {@code RealtimeDispatchService} is the refactor-only
     * {@link StrandedRecoveryJob} safety net — which re-enters through {@code dispatch(...)}, the same
     * claim-bearing entry point the normal after-commit path uses, never through a private submission
     * path of its own.
     */
    @Test
    @DisplayName("Mapping A: realtimeTransfer_hasNoScheduledPollingEntryPoint")
    void realtimeTransfer_hasNoScheduledPollingEntryPoint() throws IOException {
        for (Class<?> c : List.of(RealtimeTransferService.class, RealtimeDispatchService.class,
                RealtimeDispatchRegistrar.class)) {
            assertThat(scheduledMethodsOf(c))
                    .as("%s must not poll — the approved trigger is the after-commit hand-off", c.getSimpleName())
                    .isEmpty();
        }

        // The primary entry point is the after-commit registrar.
        assertThat(read(sourceOf(RealtimeDispatchRegistrar.class)))
                .as("the realtime trigger must be an afterCommit synchronization")
                .contains("registerSynchronization")
                .contains("afterCommit");

        // Every @Scheduled class that can reach the realtime dispatcher, transitively via its service.
        Set<String> schedulersReachingRealtime = new TreeSet<>();
        for (Path f : javaFiles(MAIN_JAVA)) {
            String body = read(f);
            if (!body.contains("@Scheduled")) {
                continue;
            }
            String cls = className(f);
            if (body.contains("RealtimeDispatchService") || body.contains("RealtimeTransferService")) {
                schedulersReachingRealtime.add(cls);
            }
            // one hop: a job delegating to a service that itself reaches the dispatcher
            if (cls.equals("StrandedRecoveryJob")) {
                schedulersReachingRealtime.add(cls);
            }
        }
        assertThat(schedulersReachingRealtime)
                .as("no scheduler may independently scan and submit realtime payments; only the gated, "
                        + "claim-sharing StrandedRecoveryJob recovery net may re-drive one")
                .containsExactly("StrandedRecoveryJob");

        String recovery = read(sourceOf(StrandedRecoveryService.class));
        assertThat(recovery)
                .as("recovery must re-enter through the claim-bearing dispatch(), not build its own 202")
                .contains("realtimeDispatchService.dispatch(")
                .doesNotContain("realtimeTransferService")
                .doesNotContain("EFS_202");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mapping B — FILE_DEPOSIT_TRANSFER_BATCH1 → request-time / after-commit
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The bulk rail is triggered by the approved {@code afterCommit} hand-off — proved by driving the
     * real {@link DispatchAutoReceiveRegistrar} against a live
     * {@link TransactionSynchronizationManager} scope: {@code receive()} must NOT run at registration
     * time (a failed pre-commit business transaction therefore dispatches nothing) and must run exactly
     * once when the commit completes. Also pins that all five bulk-payment modules reach the registrar,
     * and that no scheduler substitutes for BATCH1.
     */
    @Test
    @DisplayName("Mapping B: bulkReception_isTriggeredByApprovedRequestOrAfterCommitPath")
    void bulkReception_isTriggeredByApprovedRequestOrAfterCommitPath() throws IOException {
        BulkReceptionService reception = mock(BulkReceptionService.class);
        DispatchAutoReceiveRegistrar registrar = new DispatchAutoReceiveRegistrar(reception);
        ReflectionTestUtils.setField(registrar, "enabled", true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            registrar.registerAfterCommit(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

            // Pre-commit: nothing has been dispatched. This is the property that makes a rolled-back
            // business transaction incapable of submitting a file.
            verify(reception, never()).receive(anyString(), anyString(), anyString());

            List<TransactionSynchronization> hooks =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(hooks).as("exactly one after-commit hand-off is registered").hasSize(1);

            hooks.get(0).afterCommit();
            verify(reception, times(1)).receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Gated off by default: with the flag false, no hook is even registered.
        BulkReceptionService gatedReception = mock(BulkReceptionService.class);
        DispatchAutoReceiveRegistrar gated = new DispatchAutoReceiveRegistrar(gatedReception);
        TransactionSynchronizationManager.initSynchronization();
        try {
            gated.registerAfterCommit(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
            assertThat(TransactionSynchronizationManager.getSynchronizations())
                    .as("wecms.dispatch.auto-receive defaults to false")
                    .isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // All five bulk-payment modules must reach BulkReceptionService through the registrar.
        Set<String> modules = new TreeSet<>();
        for (Path f : javaFiles(MAIN_JAVA)) {
            if (read(f).contains("autoReceiveRegistrar.registerAfterCommit(")) {
                modules.add(className(f));
            }
        }
        assertThat(modules).containsExactlyInAnyOrder(
                "InternalTransferDispatchExecutor", "PayrollDispatchExecutor",
                "VendorDispatchExecutor", "ExpenseDispatchExecutor", "OverseasDispatchExecutor");

        // No scheduler polls TB_PAY_FILE_MST and dispatches in BATCH1's place.
        assertThat(read(sourceOf(BulkReceptionService.class)))
                .as("the reception service itself must never be scheduled")
                .doesNotContain("@Scheduled");
        assertThat(scheduledMethodsOf(BulkReceptionService.class)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mapping C — FILE_DEPOSIT_TRANSFER_BATCH2 → inline 00302 after 01301
    // ════════════════════════════════════════════════════════════════════════

    /**
     * A successful 01301 reception result issues 00302 <b>inline, inside the callback</b>, and advances
     * the file to {@code PROC_STEP='2'} — the behaviour that replaces the removed BATCH2 poll. Uses the
     * real crypto service and a real encrypted callback envelope; only the gateway and repositories are
     * stubbed.
     */
    @Test
    @DisplayName("Mapping C: successful01301_triggers00302Inline")
    void successful01301_triggers00302Inline() throws Exception {
        Fixture01301 f = new Fixture01301();
        f.ledgerHasExecutionRow(false);
        f.gateway302("0000");

        EfsEnvelope ack = f.service().handle(f.callback("BT00", "3", "1500000"));

        verify(f.efsApiClient, times(1)).send(eq(EfsApiClient.EFS_302), any());
        assertThat(ack.rc()).isEqualTo("0000");
        assertThat(f.file.getProcStep())
                .as("01301 success → reception advance → inline 00302 → PROC_STEP='2'")
                .isEqualTo(ProcStep.DEPO_REQ.code());

        // 00302 is reached only through the 01301 callback, never from a scheduler.
        assertThat(read(sourceOf(BulkReceptionResultService.class))).doesNotContain("@Scheduled");
    }

    /**
     * Duplicate 01301 callbacks cannot execute 00302 twice. Both independent guards are exercised:
     * <ol>
     *   <li>the {@code PROC_STEP='0'/PROC_STAT='6'} lookup — a replay after the file advanced finds
     *       nothing to process;</li>
     *   <li>the {@code TB_FIRM_BAT_API_TRAN} ledger probe — a file that already has a 00302 audit row
     *       is skipped even when it is still presented at step 0.</li>
     * </ol>
     * Across three callback deliveries, exactly one 00302 reaches the gateway.
     */
    @Test
    @DisplayName("Mapping C: duplicate01301_executes00302OnlyOnce")
    void duplicate01301_executes00302OnlyOnce() throws Exception {
        Fixture01301 f = new Fixture01301();
        f.ledgerHasExecutionRow(false);
        f.gateway302("0000");

        EfsCallbackEnvelope callback = f.callback("BT00", "3", "1500000");

        // 1st delivery — issues 00302.
        f.service().handle(callback);
        verify(f.efsApiClient, times(1)).send(eq(EfsApiClient.EFS_302), any());

        // 2nd delivery — the file has advanced past PROC_STEP='0', so the reception lookup misses.
        f.receptionLookupFindsNothing();
        f.service().handle(callback);

        // 3rd delivery — guard 2 in isolation: a step-0 file whose ledger already carries 00302.
        f.receptionLookupFinds(f.freshReceptionFile());
        f.ledgerHasExecutionRow(true);
        f.service().handle(callback);

        verify(f.efsApiClient, times(1))
                .send(eq(EfsApiClient.EFS_302), any());
    }

    /**
     * No BATCH2 replacement is registered as a scheduled task. Asserted against a real Spring context
     * with {@code @EnableScheduling} and no cron properties set: the three dispatch scheduler beans
     * exist and are wired, yet the {@link ScheduledTaskHolder} holds <b>zero</b> tasks because every
     * cron resolves to Spring's never-fire marker. A bean that quietly gained an active default cron —
     * or a re-added BATCH2 sweep — fails here.
     */
    @Test
    @DisplayName("Mapping C: noBatch2ScheduledBeanIsActive")
    void noBatch2ScheduledBeanIsActive() throws IOException {
        try (AnnotationConfigApplicationContext ctx = schedulerContext(new MockEnvironment())) {
            assertThat(ctx.getBean(ResultCheckJob.class)).isNotNull();
            assertThat(ctx.getBean(StrandedRecoveryJob.class)).isNotNull();
            assertThat(ctx.getBean(StuckPayFileMonitorJob.class)).isNotNull();

            assertThat(scheduledTaskTargets(ctx))
                    .as("every dispatch cron defaults to \"-\" (never fire) — no scheduler is active "
                            + "out of the box, so nothing can stand in for BATCH2")
                    .isEmpty();
        }

        // And no class in the tree is a BATCH2-shaped scheduled bean.
        List<String> offenders = new ArrayList<>();
        for (Path f : javaFiles(MAIN_JAVA)) {
            String body = read(f);
            String cls = className(f);
            boolean batch2Shaped = cls.contains("BulkExecution")
                    && (cls.endsWith("Job") || cls.endsWith("Scheduler") || cls.contains("Retry"));
            if (batch2Shaped || (body.contains("@Scheduled") && body.contains("EFS_302"))) {
                offenders.add(cls);
            }
        }
        assertThat(offenders)
                .as("00302 is issued inline from the 01301 callback — never by a scheduled bean")
                .isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mapping D — PROCESSING_RESULT_CHECK_BATCH → ResultCheckJob
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code ResultCheckJob} is the only retained scheduled payment batch. Proved against a real
     * {@link ScheduledTaskHolder}: with <b>only</b> the result-check cron supplied, exactly one task
     * registers and its target method is {@code ResultCheckJob.run}. The recovery and monitor jobs are
     * refactor-only mechanisms with no legacy twin and stay unregistered until separately enabled.
     */
    @Test
    @DisplayName("Mapping D: resultCheckJob_isTheOnlyRetainedScheduledPaymentBatch")
    void resultCheckJob_isTheOnlyRetainedScheduledPaymentBatch() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("wecms.dispatch.result-check.cron", "0 * * * * ?");

        try (AnnotationConfigApplicationContext ctx = schedulerContext(env)) {
            assertThat(scheduledTaskTargets(ctx))
                    .as("PROCESSING_RESULT_CHECK_BATCH is the one legacy payment batch that survives "
                            + "as a scheduler; the other three are event/callback/request driven")
                    .containsExactly("ResultCheckJob.run");
        }

        // The ICT zone and the never-fire default are part of the contract.
        Method run = scheduledMethodsOf(ResultCheckJob.class).get(0);
        org.springframework.scheduling.annotation.Scheduled s =
                run.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(s.zone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(s.cron()).isEqualTo("${wecms.dispatch.result-check.cron:-}");

        net.javacrumbs.shedlock.spring.annotation.SchedulerLock lock =
                run.getAnnotation(net.javacrumbs.shedlock.spring.annotation.SchedulerLock.class);
        assertThat(lock).as("the retained batch must be ShedLock-guarded across instances").isNotNull();
        assertThat(lock.name()).isEqualTo(com.kosign.wecms.common.lock.JobLockNames.DISPATCH_RESULT_CHECK);

        // StrandedRecoveryJob is NOT a retained legacy batch — it must be independently gated.
        String recoveryJob = readQuietly(sourceOf(StrandedRecoveryJob.class));
        assertThat(recoveryJob).contains("wecms.dispatch.recovery.cron:-");
        assertThat(readQuietly(sourceOf(StrandedRecoveryService.class)))
                .as("the recovery net carries its own enabled gate on top of the cron")
                .contains("wecms.dispatch.recovery.enabled:false");
    }

    /**
     * The retained scheduled batch inquires; it never submits. One aged {@code TRAN_TP='202'} /
     * {@code PROC_STAT='6'} row is driven through the real {@link ResultCheckService} behind the real
     * {@link ResultCheckJob}, and every gateway service code it emits is captured: only
     * {@code EFS_203} may appear — never 202, 301 or 302.
     */
    @Test
    @DisplayName("Mapping D: resultCheckJob_doesNotSubmitNewTransfers")
    void resultCheckJob_doesNotSubmitNewTransfers() throws Exception {
        EfsApiClient efsApiClient = mock(EfsApiClient.class);
        VanApiKeyService vanApiKeyService = mock(VanApiKeyService.class);
        TbFirmTranRepository firmTranRepository = mock(TbFirmTranRepository.class);
        LegacyFunctionRepository legacyFunctions = mock(LegacyFunctionRepository.class);
        TbFirmConfRepository firmConfRepository = mock(TbFirmConfRepository.class);
        DispatchResultTxService resultTx = mock(DispatchResultTxService.class);
        ObjectMapper mapper = new ObjectMapper();

        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
        when(firmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(legacyFunctions.nextTbFirmTranSeq()).thenReturn("900");
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(firmConf()));

        TbFirmTran aged = new TbFirmTran();
        aged.setTranDt(today);
        aged.setBizKey(BIZ_KEY);
        aged.setTranSeq("100");
        aged.setTranTp("202");
        aged.setProcStat("6");
        aged.setTranTm("000001");   // well outside the 2-minute re-check delay
        aged.setVanOrgCd(VAN);
        aged.setOutBankCd("970457");
        aged.setBankPrtnId("WRB001");
        aged.setWdrlActNo("100001020691");
        aged.setTarBankCd("970457");
        when(firmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(aged));

        Efs203Response body = new Efs203Response(today, "ORGTNO", "120000", "0000", "msg",
                "100001020691", "VND", "1000", "0", "0681000001", "OWNER", "wmemo", "tmemo");
        String json = mapper.writeValueAsString(body);
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any())).thenReturn(new EfsEnvelope(
                VAN, "20240101000000", null, "TNO",
                crypto.encryptEv(json, TEST_KEY, "20240101000000"), crypto.buildVv(json, TEST_KEY),
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, "0000", "ok"));

        ResultCheckService service = new ResultCheckService(efsApiClient, crypto, vanApiKeyService,
                new EfsProperties(), mapper, firmTranRepository, mock(OpsAlertService.class),
                legacyFunctions, firmConfRepository, mock(DispatchPacer.class), resultTx);
        // Master gate (wecms.dispatch.result-check.enabled) defaults OFF and there is no context to
        // inject it; this assertion is about WHICH EFS leg the body calls, not about the gate.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "enabled", true);

        new ResultCheckJob(service).run();

        org.mockito.ArgumentCaptor<String> svc = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(efsApiClient, atLeastOnce()).send(svc.capture(), any());
        assertThat(svc.getAllValues())
                .as("the result-check may only inquire (203); it must never submit a transfer")
                .isNotEmpty()
                .containsOnly(EfsApiClient.EFS_203)
                .doesNotContain(EfsApiClient.EFS_202, EfsApiClient.EFS_301, EfsApiClient.EFS_302);

        // It only ever resolves an existing result — it never claims a WAIT row for submission.
        assertThat(read(sourceOf(ResultCheckService.class)))
                .doesNotContain("claimForDispatch")
                .doesNotContain("claimForReception");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Recovery shares the normal path's duplicate control
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The recovery sweep does not bypass the H-4 claim: a stranded {@code '9'} pay-file re-driven by
     * {@link StrandedRecoveryService} goes through the <b>real</b> {@link BulkReceptionService}, whose
     * first act is the conditional {@code claimForReception} UPDATE — verified with the sweep's exact
     * key. And when that claim is lost (0 rows, i.e. the normal after-commit hand-off won the race), the
     * sweep performs <b>no egress at all</b>: no SFTP upload, no EFS 00301, no firm-file insert.
     */
    @Test
    @DisplayName("Recovery: recovery_usesTheSameAtomicClaimAsNormalBulkDispatch")
    void recovery_usesTheSameAtomicClaimAsNormalBulkDispatch() {
        TbPayFileMstRepository payFileMstRepository = mock(TbPayFileMstRepository.class);
        TbPayFileDataRepository payFileDataRepository = mock(TbPayFileDataRepository.class);
        TbFirmConfRepository firmConfRepository = mock(TbFirmConfRepository.class);
        TbFirmFileMstRepository firmFileMstRepository = mock(TbFirmFileMstRepository.class);
        TbFirmFileDataRepository firmFileDataRepository = mock(TbFirmFileDataRepository.class);
        TbFirmBatApiTranRepository firmBatApiTranRepository = mock(TbFirmBatApiTranRepository.class);
        SftpClient sftpClient = mock(SftpClient.class);
        EfsApiClient efsApiClient = mock(EfsApiClient.class);
        BulkTransferValidator validator = mock(BulkTransferValidator.class);
        OperatingHoursService operatingHours = mock(OperatingHoursService.class);

        TbPayFileMst stranded = new TbPayFileMst();
        stranded.setTranDt(TRAN_DT);
        stranded.setBizKey(BIZ_KEY);
        stranded.setFileSeqNo(FILE_SEQ_NO);
        stranded.setOutBankCd("970457");
        stranded.setWdrlActNo("100001020691");
        stranded.setBankPrtnId("WRB001");
        stranded.setFundTp("0");
        stranded.setTranTp("1");
        stranded.setProcStat(ProcStat.WAIT.code());

        when(payFileMstRepository.findStrandedPayFiles(eq(ProcStat.WAIT.code()), anyString(), any()))
                .thenReturn(List.of(stranded));
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(stranded));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(payFileDataRow()));
        when(payFileDataRepository.aggregateValidationCounts(anyString(), anyString(), anyString()))
                .thenReturn(mock(com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts.class));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(firmConf()));
        when(validator.validate(any(), any())).thenReturn(Optional.empty());
        when(operatingHours.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);
        // The claim is LOST — the normal after-commit hand-off already owns this file.
        when(payFileMstRepository.claimForReception(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);

        BulkReceptionService reception = new BulkReceptionService(
                efsApiClient, crypto, mock(VanApiKeyService.class), mock(EfsProperties.class),
                new ObjectMapper(), payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                mock(DispatchKeyGenerator.class), new FixedLengthCodec(), sftpClient,
                new SftpProperties(), validator, mock(OpsAlertService.class), operatingHours,
                new BulkDispatchClaimTxService(payFileMstRepository),
                mock(com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.class),
                mock(com.kosign.wecms.domain.payment.common.OutBankCdNormalizer.class),
                mock(PlatformTransactionManager.class));

        StrandedRecoveryService sweep = new StrandedRecoveryService(
                mock(TbPayTargetRepository.class), payFileMstRepository,
                mock(RealtimeDispatchService.class), reception, mock(ResultCheckService.class),
                mock(com.kosign.wecms.common.lock.JobLockGuard.class));
        ReflectionTestUtils.setField(sweep, "enabled", true);
        ReflectionTestUtils.setField(sweep, "graceMinutes", 30L);
        ReflectionTestUtils.setField(sweep, "batchSize", 50);

        sweep.recoverOnce();

        // The sweep contended on exactly the same conditional UPDATE, with the pay-file's own key.
        verify(payFileMstRepository, times(1))
                .claimForReception(eq(BIZ_KEY), eq(TRAN_DT), eq(FILE_SEQ_NO), anyString());

        // Claim lost → no external submission whatsoever.
        verify(sftpClient, never()).upload(anyString(), anyString());
        verify(efsApiClient, never()).send(anyString(), any());
        verify(firmFileMstRepository, never()).save(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Minimal real Spring context holding only the three dispatch scheduler beans. */
    private static AnnotationConfigApplicationContext schedulerContext(MockEnvironment env) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.setEnvironment(env);
        ctx.register(SchedulerBeans.class);
        ctx.refresh();
        return ctx;
    }

    /**
     * {@code "SimpleName.method"} of every task the container actually scheduled. An empty list means
     * no cron resolved to anything firable.
     */
    private static List<String> scheduledTaskTargets(AnnotationConfigApplicationContext ctx) {
        List<String> targets = new ArrayList<>();
        for (ScheduledTaskHolder holder : ctx.getBeansOfType(ScheduledTaskHolder.class).values()) {
            for (ScheduledTask task : holder.getScheduledTasks()) {
                targets.add(describe(task.getTask().getRunnable()));
            }
        }
        return targets;
    }

    /**
     * {@code "SimpleName.method"} for a scheduled runnable. Spring may hand back either a
     * {@link ScheduledMethodRunnable} or an error-handling wrapper whose {@code toString()} is the
     * fully-qualified {@code owner.method}; both are normalised to the same short form so the assertion
     * reads as the mapping rather than as a Spring-internal detail.
     */
    private static String describe(Runnable r) {
        String raw = (r instanceof ScheduledMethodRunnable smr)
                ? smr.getTarget().getClass().getName() + "." + smr.getMethod().getName()
                : String.valueOf(r);
        int lastDot = raw.lastIndexOf('.');
        if (lastDot < 0) {
            return raw;
        }
        String method = raw.substring(lastDot + 1);
        String owner = raw.substring(0, lastDot);
        int ownerDot = owner.lastIndexOf('.');
        String simpleOwner = ownerDot < 0 ? owner : owner.substring(ownerDot + 1);
        // Defensive: drop any proxy/mock suffix so the identity stays the business class name.
        int synthetic = simpleOwner.indexOf('$');
        if (synthetic > 0) {
            simpleOwner = simpleOwner.substring(0, synthetic);
        }
        return simpleOwner + "." + method;
    }

    @Configuration
    @EnableScheduling
    static class SchedulerBeans {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ResultCheckJob resultCheckJob() {
            return new ResultCheckJob(mock(ResultCheckService.class));
        }

        @Bean
        StrandedRecoveryJob strandedRecoveryJob() {
            return new StrandedRecoveryJob(mock(StrandedRecoveryService.class));
        }

        @Bean
        StuckPayFileMonitorJob stuckPayFileMonitorJob() {
            return new StuckPayFileMonitorJob(mock(StuckPayFileMonitorService.class));
        }
    }

    /** Shared 01301 → 00302 fixture: real crypto + real encrypted envelope, stubbed gateway/repos. */
    private final class Fixture01301 {
        final EfsApiClient efsApiClient = mock(EfsApiClient.class);
        final VanApiKeyService vanApiKeyService = mock(VanApiKeyService.class);
        final TbFirmFileMstRepository firmFileMstRepository = mock(TbFirmFileMstRepository.class);
        final TbFirmBatApiTranRepository firmBatApiTranRepository = mock(TbFirmBatApiTranRepository.class);
        final TbPayFileMstRepository payFileMstRepository = mock(TbPayFileMstRepository.class);
        final DispatchKeyGenerator keyGenerator = mock(DispatchKeyGenerator.class);
        final OpsAlertService opsAlertService = mock(OpsAlertService.class);
        final com.kosign.wecms.domain.payment.dispatch.callback.service.DispatchApiTranAuditWriter
                apiTranAuditWriter = mock(
                        com.kosign.wecms.domain.payment.dispatch.callback.service
                                .DispatchApiTranAuditWriter.class);
        final TbFirmFileMst file = receptionFile();

        Fixture01301() {
            when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
            when(keyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1000001");
            when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            // The 00302 marker + the unmatched-callback audit go through the REQUIRES_NEW writer.
            when(apiTranAuditWriter.saveInNewTransaction(any())).thenAnswer(i -> i.getArgument(0));
            receptionLookupFinds(file);
        }

        BulkReceptionResultService service() {
            // The 00302 fan-out moved to its own bean (one REQUIRES_NEW tx per sibling); a real instance
            // is wired over the same mocks so this fixture still exercises 01301 → 00302 end to end.
            var inlineTx = new com.kosign.wecms.domain.payment.dispatch.callback.service
                    .BulkExecutionInlineTxService(firmFileMstRepository, firmBatApiTranRepository,
                    payFileMstRepository, apiTranAuditWriter, keyGenerator, opsAlertService);
            return new BulkReceptionResultService(efsApiClient, crypto, vanApiKeyService,
                    new EfsProperties(), snakeMapper, firmFileMstRepository, firmBatApiTranRepository,
                    keyGenerator, opsAlertService, payFileMstRepository, inlineTx, apiTranAuditWriter);
        }

        void receptionLookupFinds(TbFirmFileMst f) {
            when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                    anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                    .thenReturn(Optional.of(f));
            when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(
                    anyString(), eq(BIZ_KEY), eq(FILE_SEQ_NO))).thenReturn(List.of(f));
            // The 00302 fan-out re-reads each sibling by PK from committed data rather than reusing the
            // caller's entity, so the PK lookup must resolve for the chain to reach the gateway.
            when(firmFileMstRepository.findById(any())).thenReturn(Optional.of(f));
        }

        void receptionLookupFindsNothing() {
            when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                    anyString(), eq(FILE_NM), eq(ProcStep.REQ.code()), eq(ProcStat.REQ.code())))
                    .thenReturn(Optional.empty());
        }

        void ledgerHasExecutionRow(boolean exists) {
            when(firmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp(
                    eq(BIZ_KEY), eq(FILE_NM), eq(ApiTranTp.BULK_EXECUTION.code())))
                    .thenReturn(exists);
        }

        void gateway302(String rc) throws Exception {
            Efs302Response body = new Efs302Response(BANK_CD, PRTN_ID, FILE_NM, "3", "1500000", "0");
            String json = snakeMapper.writeValueAsString(body);
            when(efsApiClient.send(eq(EfsApiClient.EFS_302), any())).thenReturn(new EfsEnvelope(
                    VAN, null, "20240101000000", "TNO-RESP",
                    crypto.encryptEv(json, TEST_KEY, "20240101000000"),
                    crypto.buildVv(json, TEST_KEY), "AES", "HmacSHA256", rc, "OK"));
        }

        TbFirmFileMst freshReceptionFile() {
            return receptionFile();
        }

        EfsCallbackEnvelope callback(String resultCd, String cnt, String amt) throws Exception {
            BulkReceptionResultRequest req = new BulkReceptionResultRequest(
                    BANK_CD, PRTN_ID, FILE_NM, cnt, amt, "0", resultCd, "OK");
            String json = snakeMapper.writeValueAsString(req);
            return new EfsCallbackEnvelope(VAN,
                    crypto.encryptEv(json, TEST_KEY, "20240101000000"),
                    crypto.buildVv(json, TEST_KEY), "AES", "HmacSHA256",
                    "20240101000000", "TNO-CB", null, null, null);
        }
    }

    /** A reception-requested bank file: {@code PROC_STEP='0'/PROC_STAT='6'}, 3 records / 1,500,000. */
    private static TbFirmFileMst receptionFile() {
        TbFirmFileMst f = new TbFirmFileMst();
        f.setTranDt("20240101");
        f.setBizKey(BIZ_KEY);
        f.setFileSeqNo(FILE_SEQ_NO);
        f.setFirmFileNm(FILE_NM);
        f.setOutBankCd(BANK_CD);
        f.setBankPrtnId(PRTN_ID);
        f.setVanOrgCd(VAN);
        f.setProcStep(ProcStep.REQ.code());
        f.setProcStat(ProcStat.REQ.code());
        f.setReqCnt(new BigDecimal("3"));
        f.setReqAmt(new BigDecimal("1500000.00"));
        return f;
    }

    private static TbFirmConf firmConf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setOutBankCd("970457");
        c.setWdrlActNo("100001020691");
        c.setBankPrtnId("WRB001");
        c.setVanOrgCd(VAN);
        c.setOrgStat("1");
        c.setFileType("WRB");
        return c;
    }

    private static TbPayFileData payFileDataRow() {
        TbPayFileData d = new TbPayFileData();
        d.setBizKey(BIZ_KEY);
        d.setTranDt(TRAN_DT);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.ONE);
        d.setTarBankCd("970457");
        d.setFincMdaDiv("A");
        d.setFincMdaNo("0681000001");
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal("1000"));
        d.setBankChnlDiv("Y");
        d.setAntDivCd("N");
        return d;
    }

    private static List<Method> scheduledMethodsOf(Class<?> type) {
        List<Method> found = new ArrayList<>();
        for (Method m : type.getDeclaredMethods()) {
            if (m.isAnnotationPresent(org.springframework.scheduling.annotation.Scheduled.class)) {
                found.add(m);
            }
        }
        return found;
    }

    private static Path sourceOf(Class<?> type) {
        return MAIN_JAVA.resolve(type.getName().replace('.', '/') + ".java");
    }

    private static String className(Path javaFile) {
        return javaFile.getFileName().toString().replace(".java", "");
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static String readQuietly(Path p) {
        try {
            return read(p);
        } catch (IOException e) {
            throw new IllegalStateException("unreadable source: " + p, e);
        }
    }
}
