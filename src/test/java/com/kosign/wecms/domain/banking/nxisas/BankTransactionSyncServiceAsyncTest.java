package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.SyncStatusResponse;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncPersistence.PersistResult;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.nxisas.session.ScrapeChallengeKind;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 2 async behaviour for {@link BankTransactionSyncService}: immediate
 * RUNNING start, real per-account progress milestones fired at the correct
 * stages, live processed/success/failed counters, fail-fast FAILED terminal,
 * the duplicate-resume worker guard, resume rejection on a terminal session,
 * and tenant-scoped status lookup. Uses an inline executor for deterministic
 * state-machine coverage (the real bank engine is unavailable in R&D).
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class BankTransactionSyncServiceAsyncTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String SHINHAN = "11704088";
    private static final String OK = "00000000";

    private BankCredentialService credentialService;
    private NxisasGatewayClient gateway;
    private AcctMstrRepository acctRepo;
    private NxisasSessionRegistry registry;
    private BankTransactionSyncPersistence persistence;
    private AcctMstrStatusUpdater statusUpdater;
    private SyncHistoryWriter syncHistoryWriter;
    private BankTransactionSyncService service;

    private final AtomicReference<NxisasScrapeSession> sref = new AtomicReference<>();
    private final java.util.List<Integer> progressAtTx = new java.util.ArrayList<>();
    private final java.util.List<Integer> progressAtPersist = new java.util.ArrayList<>();
    private final java.util.List<Integer> progressAtBalance = new java.util.ArrayList<>();
    private java.util.List<AcctMstr> mockedAccts;
    private NxisasCommon okCommon;
    private NxisasApiOut<NxisasTransactionJobResult> txOut;
    private NxisasTransactionJobResult txResult;
    private NxisasOutput txBankOut;

    @BeforeEach
    void setUp() {
        credentialService = mock(BankCredentialService.class);
        gateway = mock(NxisasGatewayClient.class);
        acctRepo = mock(AcctMstrRepository.class);
        registry = mock(NxisasSessionRegistry.class);
        syncHistoryWriter = mock(SyncHistoryWriter.class);
        statusUpdater = mock(AcctMstrStatusUpdater.class);
        persistence = mock(BankTransactionSyncPersistence.class);

        service = new BankTransactionSyncService(
                credentialService, gateway, new NxisasBankConfig(), acctRepo,
                syncHistoryWriter, statusUpdater,
                mock(com.kosign.wecms.domain.banking.service.AutoMatchService.class),
                registry, persistence, Runnable::run);

        TenantContext.set(USER_ID, BIZ_KEY, LangCd.EN);
        mockedAccts = new java.util.ArrayList<>();
        when(acctRepo.findAllById(any())).thenReturn(mockedAccts);
        when(gateway.newTrSeq()).thenReturn("20260714000000");

        // Capture the registered session so the progress hooks can read it.
        org.mockito.Mockito.doAnswer(inv -> {
            sref.set(inv.getArgument(0));
            return null;
        }).when(registry).register(any());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubAccount(String bankCd, String acctNo) {
        AcctMstr acct = mock(AcctMstr.class);
        when(acct.getBankCd()).thenReturn(bankCd);
        when(acct.getAcctNo()).thenReturn(acctNo);
        when(acct.getCcyCd()).thenReturn("VND");
        when(acct.getHandRegYn()).thenReturn("N");
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(eq(BIZ_KEY), eq(bankCd), eq(acctNo)))
                .thenReturn(Optional.of(acct));
        mockedAccts.add(acct);
        when(credentialService.loadForScrapingByBank(bankCd)).thenReturn(
                new DecryptedCredential(bankCd, "u", "p", null, "ID", BigDecimal.ZERO));
    }

    /** Clean LOGIN → TRANSACTION → BALANCE, with progress captured at each
     *  gateway/persistence stage via Answers. */
    private void stubCleanFlowWithHooks(boolean txFails) {
        okCommon = mock(NxisasCommon.class);
        when(okCommon.getResultCd()).thenReturn(OK);

        NxisasApiOut<NxisasLoginJobResult> loginOut = mock(NxisasApiOut.class);
        when(loginOut.getCommon()).thenReturn(okCommon);
        when(loginOut.getSasSessionID()).thenReturn("sas-live");
        NxisasOutput loginBankOut = mock(NxisasOutput.class);
        when(loginBankOut.getErrorCode()).thenReturn(OK);
        NxisasLoginJobResult loginResult = mock(NxisasLoginJobResult.class);
        when(loginResult.getCallBackFunc()).thenReturn(null);
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstOutput(loginOut)).thenReturn(loginBankOut);
        when(gateway.firstResult(loginOut)).thenReturn(loginResult);

        txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(okCommon);
        txBankOut = mock(NxisasOutput.class);
        when(txBankOut.getErrorCode()).thenReturn(txFails ? "42999999" : OK);
        txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.transaction(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            progressAtTx.add(sref.get().getProgress());
            return txOut;
        });
        when(gateway.firstOutput(txOut)).thenReturn(txBankOut);
        when(gateway.firstTransactionResult(txOut)).thenReturn(txResult);

        when(persistence.persistTransactions(anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString())).thenAnswer(inv -> {
            progressAtPersist.add(sref.get().getProgress());
            return new PersistResult(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        });

        NxisasApiOut<NxisasBalanceJobResult> balOut = mock(NxisasApiOut.class);
        when(balOut.getCommon()).thenReturn(okCommon);
        NxisasOutput balBankOut = mock(NxisasOutput.class);
        when(balBankOut.getErrorCode()).thenReturn(OK);
        NxisasBalanceJobResult balResult = mock(NxisasBalanceJobResult.class);
        when(balResult.getBalance()).thenReturn(null);
        when(balResult.getAcctList()).thenReturn(null);
        when(gateway.balance(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            progressAtBalance.add(sref.get().getProgress());
            return balOut;
        });
        when(gateway.firstOutput(balOut)).thenReturn(balBankOut);
        when(gateway.firstBalanceResult(balOut)).thenReturn(balResult);
    }

    private SyncStatusResponse start(String... acctNos) {
        List<TransactionsSyncRequest.AccountKey> keys = new java.util.ArrayList<>();
        for (String a : acctNos) keys.add(new TransactionsSyncRequest.AccountKey(SHINHAN, a));
        return service.start(new TransactionsSyncRequest(keys, null, null, null));
    }

    @Test
    void start_returnsRunningSnapshotImmediately_totalsSet() {
        stubCleanFlowWithHooks(false);
        stubAccount(SHINHAN, "1111");

        SyncStatusResponse resp = start("1111");

        // The returned snapshot is the pre-scheduling RUNNING/0 state.
        assertThat(resp.status()).isEqualTo("RUNNING");
        assertThat(resp.progress()).isEqualTo(0);
        assertThat(resp.totalAccounts()).isEqualTo(1);
        assertThat(resp.completedAccounts()).isEqualTo(0);
        assertThat(resp.sessionId()).isNotNull();
        // Scheduled + ran exactly once (one LOGIN).
        verify(gateway, times(1)).login(any(), any(), any(), any());
    }

    @Test
    void singleAccountProgress_reachesMilestonesAtRealStages() {
        stubCleanFlowWithHooks(false);
        stubAccount(SHINHAN, "1111");

        start("1111");

        // Progress captured at the moment each real stage ran.
        assertThat(progressAtTx).containsExactly(0);        // 0% — before TRANSACTION scrape
        assertThat(progressAtPersist).containsExactly(25);  // 25% — after scrape, at persist
        assertThat(progressAtBalance).containsExactly(50);  // 50% — balance processing started
        assertThat(sref.get().getProgress()).isEqualTo(100);
        assertThat(sref.get().getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(sref.get().getCompletedAccounts()).isEqualTo(1);
        assertThat(sref.get().getSuccessAccounts()).isEqualTo(1);
    }

    @Test
    void failFast_failedAccountCountsSeparately_statusFailed_progress100() {
        stubCleanFlowWithHooks(true); // TRANSACTION returns a bank error
        stubAccount(SHINHAN, "1111");

        start("1111");

        NxisasScrapeSession s = sref.get();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.FAILED);
        assertThat(s.getFailedAccounts()).isEqualTo(1);
        assertThat(s.getSuccessAccounts()).isZero();
        assertThat(s.getCompletedAccounts()).isEqualTo(1);
        assertThat(s.getErrorCode()).isEqualTo("WBE2095");
        // Fail-fast: no BALANCE for the failing account.
        verify(gateway, never()).balance(any(), any(), any(), any(), any());
    }

    @Test
    void cancelAfterBalance_beforeFinalize_resultsInCancelled_notCompleted() {
        stubCleanFlowWithHooks(false);
        stubAccount(SHINHAN, "1111");
        // Flip the cooperative cancel flag from inside persistBalanceAndStamp —
        // i.e. AFTER the last per-account cancel checkpoint, so only
        // finalizeAsync's guard can catch it. Proves the worker does NOT finalize
        // COMPLETED once a cancel has arrived, even in the finalize window (D4).
        org.mockito.Mockito.doAnswer(inv -> {
            sref.get().requestCancel();
            return null;
        }).when(persistence).persistBalanceAndStamp(anyString(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), any(), any(), any());

        start("1111");

        NxisasScrapeSession s = sref.get();
        // finalize honoured the cancel — CANCELLED, never overwritten to COMPLETED.
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.CANCELLED);
    }

    @Test
    void challengeCancel_failsCurrentAccountLoginCan_continuesToCompleted_notCancelled() {
        // Session parked at a QNA/OTP/CAPTCHA challenge with a single queued
        // account. Cancelling the challenge is ACCOUNT-LEVEL (legacy per-bank
        // run_afterLoginFail): the account fails LOGIN_CAN, the batch continues,
        // and — the queue now empty — finalizes COMPLETED (never CANCELLED).
        NxisasScrapeSession s = new NxisasScrapeSession(
                "tr", BIZ_KEY, USER_ID, BigDecimal.ZERO, "GLOBAL/GBL/BANK_Shinhan", null);
        s.setMode(NxisasScrapeSession.Mode.SYNC);
        s.setTotalAccounts(1);
        Deque<TransactionsSyncRequest.AccountKey> q = new ArrayDeque<>();
        q.add(new TransactionsSyncRequest.AccountKey(SHINHAN, "1111"));
        s.setAccountsQueue(q);
        s.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
        s.setChallengeKind(ScrapeChallengeKind.OTP);
        s.setSasSessionID("sas-live"); // a login attempt left a continuation
        stubAccount(SHINHAN, "1111");  // acctRepo → acct with ccy VND
        when(registry.find(eq(s.getSessionId()), eq(BIZ_KEY), eq(USER_ID))).thenReturn(Optional.of(s));

        SyncStatusResponse resp = service.cancelChallenge(s.getSessionId());

        // Inline executor ran the continuation synchronously → COMPLETED, not CANCELLED.
        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(s.getFailedAccounts()).isEqualTo(1);
        assertThat(s.getSuccessAccounts()).isZero();
        assertThat(s.getCompletedAccounts()).isEqualTo(1);
        // Account marked LOGIN_CAN + audited; NOT a whole-session cancel (no LOGOUT).
        verify(statusUpdater).markFailure(eq(BIZ_KEY), eq(SHINHAN), eq("1111"),
                eq("LOGIN_CAN"), anyString(), eq(USER_ID), anyString());
        verify(syncHistoryWriter).recordAttempt(eq(BIZ_KEY), eq(USER_ID), eq(SHINHAN), eq("1111"),
                any(), any(), eq("LOGIN_CAN"), anyString());
        verify(gateway, never()).logout(any(), any(), any(), any(), any());
        // Per-currency Failed count surfaced on the COMPLETED summary.
        assertThat(resp.currencySummaries())
                .anySatisfy(cs -> {
                    assertThat(cs.ccyCd()).isEqualTo("VND");
                    assertThat(cs.failedCount()).isEqualTo(1);
                    assertThat(cs.successCount()).isZero();
                });
        // Per-account failed detail surfaced for the popup's LOGIN_CAN tooltip.
        assertThat(resp.failedAccountDetails())
                .anySatisfy(fa -> {
                    assertThat(fa.bankCd()).isEqualTo(SHINHAN);
                    assertThat(fa.acctNo()).isEqualTo("1111");
                    assertThat(fa.errorCode()).isEqualTo("LOGIN_CAN");
                });
    }

    @Test
    void duplicateResume_whileWorkerRunning_doesNotScheduleSecondWorker() {
        NxisasScrapeSession s = new NxisasScrapeSession(
                "tr", BIZ_KEY, USER_ID, BigDecimal.ZERO, "GLOBAL/GBL/BANK_Shinhan", null);
        s.setMode(NxisasScrapeSession.Mode.SYNC);
        s.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
        s.setChallengeKind(ScrapeChallengeKind.OTP);
        s.tryStartWorker(); // simulate a worker already processing this session
        when(registry.find(eq(s.getSessionId()), eq(BIZ_KEY), eq(USER_ID))).thenReturn(Optional.of(s));

        SyncStatusResponse resp = service.submitOtp(s.getSessionId(), new SubmitChallengeRequest("123456"));

        // Idempotent no-op — no second gateway resume scheduled.
        assertThat(resp.sessionId()).isEqualTo(s.getSessionId());
        verify(gateway, never()).submitOtp(any(), any(), any(), any(), any());
    }

    @Test
    void completedSession_cannotResume() {
        NxisasScrapeSession s = new NxisasScrapeSession(
                "tr", BIZ_KEY, USER_ID, BigDecimal.ZERO, "GLOBAL/GBL/BANK_Shinhan", null);
        s.setMode(NxisasScrapeSession.Mode.SYNC);
        s.markTerminal(NxisasScrapeSession.Status.COMPLETED);
        when(registry.find(eq(s.getSessionId()), eq(BIZ_KEY), eq(USER_ID))).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submitOtp(s.getSessionId(), new SubmitChallengeRequest("1")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE2060");
    }

    @Test
    void status_unknownOrCrossTenant_rejectedWith2060() {
        UUID unknown = UUID.randomUUID();
        when(registry.find(eq(unknown), eq(BIZ_KEY), eq(USER_ID))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.status(unknown))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE2060");
    }

    @Test
    void status_returnsImmutableSnapshot_forRunningSession() {
        NxisasScrapeSession s = new NxisasScrapeSession(
                "tr", BIZ_KEY, USER_ID, BigDecimal.ZERO, "GLOBAL/GBL/BANK_Shinhan", null);
        s.setMode(NxisasScrapeSession.Mode.SYNC);
        s.setTotalAccounts(2);
        s.setStatus(NxisasScrapeSession.Status.RUNNING);
        s.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, 0.25);
        when(registry.find(eq(s.getSessionId()), eq(BIZ_KEY), eq(USER_ID))).thenReturn(Optional.of(s));

        SyncStatusResponse resp = service.status(s.getSessionId());
        assertThat(resp.status()).isEqualTo("RUNNING");
        assertThat(resp.totalAccounts()).isEqualTo(2);
        assertThat(resp.currentPhase()).isEqualTo("PERSISTING_TRANSACTIONS");
        assertThat(resp.currencySummaries()).isNotNull(); // immutable snapshot, never the live map
        assertThat(resp.challenge()).isNull();
        assertThat(resp.errorCode()).isNull();
    }
}
