package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncPersistence.PersistResult;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Legacy login-once-per-bank parity for Bank Transaction Sync, adapted to the
 * Phase 2 async model (the scrape runs on the inline executor during
 * {@code start()} / resume). Asserts login-group reuse, per-account
 * TRANSACTION/BALANCE, the OTP pause→resume re-entrant flow, and that scraped
 * entries reach the persistence bean.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class BankTransactionSyncServiceLoginGroupingTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String OK = "00000000";

    private static final String SHINHAN = "11704088";
    private static final String SHINHAN_MODULE = "GLOBAL/GBL/BANK_Shinhan";
    private static final String WOORI = "11704020";
    private static final String WOORI_MODULE = "GLOBAL/GBL/BANK_Woori";

    private BankCredentialService credentialService;
    private NxisasGatewayClient gateway;
    private AcctMstrRepository acctRepo;
    private NxisasSessionRegistry registry;
    private SyncHistoryWriter syncHistoryWriter;
    private AcctMstrStatusUpdater statusUpdater;
    private BankTransactionSyncPersistence persistence;

    private BankTransactionSyncService service;

    private java.util.List<AcctMstr> mockedAccts;
    private NxisasCommon okCommon;
    private NxisasApiOut<NxisasLoginJobResult> loginOut;
    private NxisasLoginJobResult loginResult;
    private NxisasApiOut<NxisasTransactionJobResult> txOut;
    private NxisasTransactionJobResult txResult;
    private NxisasApiOut<NxisasBalanceJobResult> balOut;

    @BeforeEach
    void setUp() {
        credentialService = mock(BankCredentialService.class);
        gateway = mock(NxisasGatewayClient.class);
        acctRepo = mock(AcctMstrRepository.class);
        registry = mock(NxisasSessionRegistry.class);
        syncHistoryWriter = mock(SyncHistoryWriter.class);
        statusUpdater = mock(AcctMstrStatusUpdater.class);
        persistence = mock(BankTransactionSyncPersistence.class);
        when(persistence.persistTransactions(anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString()))
                .thenReturn(new PersistResult(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0));

        service = new BankTransactionSyncService(
                credentialService, gateway, new NxisasBankConfig(), acctRepo,
                syncHistoryWriter, statusUpdater,
                mock(com.kosign.wecms.domain.banking.service.AutoMatchService.class),
                registry, persistence, Runnable::run);

        TenantContext.set(USER_ID, BIZ_KEY, LangCd.EN);
        mockedAccts = new java.util.ArrayList<>();
        when(acctRepo.findAllById(any())).thenReturn(mockedAccts);
        when(gateway.newTrSeq()).thenReturn("20260714000000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubCleanFlow() {
        okCommon = mock(NxisasCommon.class);
        when(okCommon.getResultCd()).thenReturn(OK);

        loginOut = mock(NxisasApiOut.class);
        when(loginOut.getCommon()).thenReturn(okCommon);
        when(loginOut.getSasSessionID()).thenReturn("sas-live");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        NxisasOutput loginBankOut = mock(NxisasOutput.class);
        when(loginBankOut.getErrorCode()).thenReturn(OK);
        loginResult = mock(NxisasLoginJobResult.class);
        when(loginResult.getCallBackFunc()).thenReturn(null);
        when(gateway.firstOutput(loginOut)).thenReturn(loginBankOut);
        when(gateway.firstResult(loginOut)).thenReturn(loginResult);

        txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(okCommon);
        when(gateway.transaction(any(), any(), any(), any(), any())).thenReturn(txOut);
        NxisasOutput txBankOut = mock(NxisasOutput.class);
        when(txBankOut.getErrorCode()).thenReturn(OK);
        txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.firstOutput(txOut)).thenReturn(txBankOut);
        when(gateway.firstTransactionResult(txOut)).thenReturn(txResult);

        balOut = mock(NxisasApiOut.class);
        when(balOut.getCommon()).thenReturn(okCommon);
        when(gateway.balance(any(), any(), any(), any(), any())).thenReturn(balOut);
        NxisasOutput balBankOut = mock(NxisasOutput.class);
        when(balBankOut.getErrorCode()).thenReturn(OK);
        NxisasBalanceJobResult balResult = mock(NxisasBalanceJobResult.class);
        when(balResult.getBalance()).thenReturn(null);
        when(balResult.getAcctList()).thenReturn(null);
        when(gateway.firstOutput(balOut)).thenReturn(balBankOut);
        when(gateway.firstBalanceResult(balOut)).thenReturn(balResult);
    }

    private void stubAccount(String bankCd, String acctNo) {
        AcctMstr acct = mock(AcctMstr.class);
        when(acct.getBankCd()).thenReturn(bankCd);
        when(acct.getAcctNo()).thenReturn(acctNo);
        when(acct.getCcyCd()).thenReturn("VND");
        when(acct.getHandRegYn()).thenReturn("N");
        // Unbound account: Mockito would otherwise default a Long getter to 0L, which
        // reads as a real BANK_CERT_SEQ and diverts to the by-seq credential lookup.
        when(acct.getBankCertSeq()).thenReturn(null);
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(eq(BIZ_KEY), eq(bankCd), eq(acctNo)))
                .thenReturn(Optional.of(acct));
        mockedAccts.add(acct);
    }

    private void stubCredential(String bankCd) {
        when(credentialService.loadForScrapingByBank(bankCd)).thenReturn(
                new DecryptedCredential(bankCd, bankCd + "-user", "pass", null, "ID", BigDecimal.ZERO));
    }

    /** An account bound to a specific {@code BANK_CERT_SEQ}. */
    private void stubBoundAccount(String bankCd, String acctNo, long certSeq) {
        AcctMstr acct = mock(AcctMstr.class);
        when(acct.getBankCd()).thenReturn(bankCd);
        when(acct.getAcctNo()).thenReturn(acctNo);
        when(acct.getCcyCd()).thenReturn("VND");
        when(acct.getHandRegYn()).thenReturn("N");
        when(acct.getBankCertSeq()).thenReturn(certSeq);
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(eq(BIZ_KEY), eq(bankCd), eq(acctNo)))
                .thenReturn(Optional.of(acct));
        mockedAccts.add(acct);
    }

    /** One registered credential row, addressed by its own {@code SEQ_NO}. */
    private void stubCredentialBySeq(long certSeq, String bankCd, long nxisasGbSeqNo, String loginId) {
        when(credentialService.loadForScraping(BigDecimal.valueOf(certSeq))).thenReturn(
                new DecryptedCredential(bankCd, loginId, loginId + "-pw", null, "ID",
                        BigDecimal.valueOf(nxisasGbSeqNo)));
    }

    private List<NxisasLoginJobInput> capturedLogins() {
        ArgumentCaptor<NxisasLoginJobInput> cap = ArgumentCaptor.forClass(NxisasLoginJobInput.class);
        verify(gateway, atLeastOnce()).login(any(), any(), any(), cap.capture());
        return cap.getAllValues();
    }

    private static TransactionsSyncRequest.AccountKey key(String bankCd, String acctNo) {
        return new TransactionsSyncRequest.AccountKey(bankCd, acctNo);
    }

    /** Start (inline worker runs immediately) and return the captured session. */
    private NxisasScrapeSession startAndCapture(TransactionsSyncRequest.AccountKey... keys) {
        service.start(new TransactionsSyncRequest(List.of(keys), null, null, null));
        ArgumentCaptor<NxisasScrapeSession> cap = ArgumentCaptor.forClass(NxisasScrapeSession.class);
        verify(registry).register(cap.capture());
        return cap.getValue();
    }

    @Test
    void singleAccount_completesWithOneLogin() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");
        stubCredential(SHINHAN);

        NxisasScrapeSession s = startAndCapture(key(SHINHAN, "1111"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(s.getProgress()).isEqualTo(100);
        assertThat(s.getCompletedAccounts()).isEqualTo(1);
        verify(gateway, times(1)).login(eq(SHINHAN_MODULE), any(), any(), any());
        verify(gateway, times(1)).transaction(any(), any(), any(), any(), any());
    }

    @Test
    void twoAccountsSameBank_loginCalledOnce() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");
        stubAccount(SHINHAN, "2222");
        stubCredential(SHINHAN);

        NxisasScrapeSession s = startAndCapture(key(SHINHAN, "1111"), key(SHINHAN, "2222"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        verify(gateway, times(1)).login(any(), any(), any(), any());
        verify(gateway, times(2)).transaction(any(), any(), any(), any(), any());
        verify(gateway, times(2)).balance(any(), any(), any(), any(), any());
    }

    @Test
    void twoBanksTwoAccountsEach_oneLoginPerBankGroup() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");
        stubAccount(SHINHAN, "2222");
        stubAccount(WOORI, "3333");
        stubAccount(WOORI, "4444");
        stubCredential(SHINHAN);
        stubCredential(WOORI);

        NxisasScrapeSession s = startAndCapture(
                key(SHINHAN, "1111"), key(WOORI, "3333"), key(SHINHAN, "2222"), key(WOORI, "4444"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        verify(gateway, times(2)).login(any(), any(), any(), any());
        verify(gateway, times(1)).login(eq(SHINHAN_MODULE), any(), any(), any());
        verify(gateway, times(1)).login(eq(WOORI_MODULE), any(), any(), any());
        verify(gateway, times(4)).transaction(any(), any(), any(), any(), any());
    }

    @Test
    void otpOnGroup_pauses_thenResumeDrainsRestOfGroupWithoutRelogin() {
        okCommon = mock(NxisasCommon.class);
        when(okCommon.getResultCd()).thenReturn(OK);
        stubAccount(SHINHAN, "1111");
        stubAccount(SHINHAN, "2222");
        stubCredential(SHINHAN);

        loginOut = mock(NxisasApiOut.class);
        when(loginOut.getCommon()).thenReturn(okCommon);
        when(loginOut.getSasSessionID()).thenReturn("sas-live");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);

        NxisasOutput okBankOut = mock(NxisasOutput.class);
        when(okBankOut.getErrorCode()).thenReturn(OK);
        when(gateway.firstOutput(any())).thenReturn(okBankOut);

        NxisasLoginJobResult otpResult = mock(NxisasLoginJobResult.class);
        when(otpResult.getCallBackFunc()).thenReturn("OTP");
        when(otpResult.challengePrompt()).thenReturn("Enter OTP");
        NxisasLoginJobResult cleanResult = mock(NxisasLoginJobResult.class);
        when(cleanResult.getCallBackFunc()).thenReturn(null);
        when(gateway.firstResult(any())).thenReturn(otpResult, cleanResult);

        NxisasApiOut<NxisasOTPJobResult> otpResumeOut = mock(NxisasApiOut.class);
        when(otpResumeOut.getCommon()).thenReturn(okCommon);
        when(otpResumeOut.getSasSessionID()).thenReturn("sas-live");
        when(otpResumeOut.getResultList()).thenReturn(null);
        when(gateway.submitOtp(any(), any(), any(), any(), any())).thenReturn(otpResumeOut);

        txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(okCommon);
        when(gateway.transaction(any(), any(), any(), any(), any())).thenReturn(txOut);
        txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.firstTransactionResult(any())).thenReturn(txResult);

        balOut = mock(NxisasApiOut.class);
        when(balOut.getCommon()).thenReturn(okCommon);
        when(gateway.balance(any(), any(), any(), any(), any())).thenReturn(balOut);
        NxisasBalanceJobResult balResult = mock(NxisasBalanceJobResult.class);
        when(balResult.getBalance()).thenReturn(null);
        when(balResult.getAcctList()).thenReturn(null);
        when(gateway.firstBalanceResult(any())).thenReturn(balResult);

        NxisasScrapeSession s = startAndCapture(key(SHINHAN, "1111"), key(SHINHAN, "2222"));
        // Worker paused at the OTP challenge — no account scraped, no thread parked.
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
        assertThat(s.getChallengeKind()).isEqualTo(
                com.kosign.wecms.domain.banking.nxisas.session.ScrapeChallengeKind.OTP);
        assertThat(s.isWorkerRunning()).isFalse();
        verify(gateway, never()).transaction(any(), any(), any(), any(), any());

        when(registry.find(eq(s.getSessionId()), eq(BIZ_KEY), eq(USER_ID))).thenReturn(Optional.of(s));

        service.submitOtp(s.getSessionId(), new SubmitChallengeRequest("123456"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        verify(gateway, times(1)).login(any(), any(), any(), any());
        verify(gateway, times(1)).submitOtp(any(), any(), any(), any(), any());
        verify(gateway, times(2)).transaction(any(), any(), any(), any(), any());
    }

    @Test
    void scrapedEntriesReachThePersistenceBean() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");
        stubCredential(SHINHAN);

        NxisasTransactionEntry entry = mock(NxisasTransactionEntry.class);
        when(txResult.getTransaction()).thenReturn(List.of(entry));

        startAndCapture(key(SHINHAN, "1111"));

        ArgumentCaptor<List<NxisasTransactionEntry>> cap = ArgumentCaptor.forClass(List.class);
        verify(persistence, times(1)).persistTransactions(eq(BIZ_KEY), eq(USER_ID), eq(SHINHAN),
                eq("1111"), eq("VND"), cap.capture(), anyString());
        assertThat(cap.getValue()).containsExactly(entry);
    }

    @Test
    void bankAdapterEdgeCodesAreNonFatal() {
        for (String edgeCode : List.of("42120011", "42120111")) {
            setUp();
            stubCleanFlow();
            NxisasOutput edgeBankOut = mock(NxisasOutput.class);
            when(edgeBankOut.getErrorCode()).thenReturn(edgeCode);
            when(gateway.firstOutput(loginOut)).thenReturn(edgeBankOut);
            stubAccount(SHINHAN, "1111");
            stubCredential(SHINHAN);

            NxisasScrapeSession s = startAndCapture(key(SHINHAN, "1111"));

            assertThat(s.getStatus())
                    .as("edge code %s should be non-fatal", edgeCode)
                    .isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        }
    }

    // ── Two credentials at ONE bank ──────────────────────────────────────────
    // Regression for the Vietcom iB@nking / DigiBiz case: a tenant can register
    // more than one login at the same BANK_CD — a different sub-type
    // (NXISAS_GB_SEQ_NO 0 = "VCB iB@nking", 1 = "VCB DigiBiz") and/or a
    // different bank-side user level (preparer vs approver), which legacy keeps
    // as separate BANK_CERT_INFO rows because the find-or-create key includes
    // CERT_INFO_CON (fnsh_0101_001_c001_act.jsp:168). Legacy groups the
    // real-time inquiry by BANK_CERT_SEQ — one engine login per credential
    // (nxisas_tranList_osmc.js:265-279). Keying by BANK_CD alone scraped every
    // account with the lowest SEQ_NO's ID and password.

    private static final String VIETCOM = "11704004";
    private static final String VIETCOM_MODULE = "GLOBAL/VNM/BANK_Vietcom";

    @Test
    void twoCredentialsSameBank_oneLoginPerCredential_eachWithItsOwnIdAndPassword() {
        stubCleanFlow();
        // SEQ_NO 1 = VCB DigiBiz (NXISAS_GB_SEQ_NO 1), SEQ_NO 2 = VCB iB@nking (0).
        stubCredentialBySeq(1L, VIETCOM, 1L, "vcb-digibiz-login");
        stubCredentialBySeq(2L, VIETCOM, 0L, "vcb-ibanking-login");
        stubBoundAccount(VIETCOM, "0441000688210", 2L);   // iB@nking
        stubBoundAccount(VIETCOM, "1063895376",    1L);   // DigiBiz

        NxisasScrapeSession s = startAndCapture(
                key(VIETCOM, "0441000688210"), key(VIETCOM, "1063895376"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        // Two credentials at one bank => two logins, not one.
        verify(gateway, times(2)).login(eq(VIETCOM_MODULE), any(), any(), any());
        assertThat(capturedLogins())
                .extracting(NxisasLoginJobInput::getLoginId)
                .containsExactlyInAnyOrder("vcb-digibiz-login", "vcb-ibanking-login");
        assertThat(capturedLogins())
                .extracting(NxisasLoginJobInput::getLoginPwd)
                .containsExactlyInAnyOrder("vcb-digibiz-login-pw", "vcb-ibanking-login-pw");
        // Each credential resolves its own module CLASS from NXISAS_GB_SEQ_NO.
        ArgumentCaptor<String> cls = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).login(any(), cls.capture(), any(), any());
        assertThat(cls.getAllValues()).containsExactlyInAnyOrder("DIGIBIZ", "CORPORATE");
        // The ambiguous bank-level fallback must never be reached.
        verify(credentialService, never()).loadForScrapingByBank(anyString());
    }

    @Test
    void boundAccount_usesItsOwnCredential_notTheLowestSeqAtTheBank() {
        stubCleanFlow();
        stubCredentialBySeq(1L, VIETCOM, 1L, "vcb-digibiz-login");   // lowest SEQ_NO — the old wrong pick
        stubCredentialBySeq(2L, VIETCOM, 0L, "vcb-ibanking-login");
        stubBoundAccount(VIETCOM, "0441000688210", 2L);

        NxisasScrapeSession s = startAndCapture(key(VIETCOM, "0441000688210"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(capturedLogins()).singleElement()
                .extracting(NxisasLoginJobInput::getLoginId)
                .isEqualTo("vcb-ibanking-login");
        verify(credentialService, never()).loadForScraping(BigDecimal.valueOf(1L));
        verify(credentialService, never()).loadForScrapingByBank(anyString());
    }

    @Test
    void twoAccountsOnTheSameCredential_stillLoginOnce() {
        stubCleanFlow();
        stubCredentialBySeq(2L, VIETCOM, 0L, "vcb-ibanking-login");
        stubBoundAccount(VIETCOM, "0441000688210", 2L);
        stubBoundAccount(VIETCOM, "1051996380",    2L);

        NxisasScrapeSession s = startAndCapture(
                key(VIETCOM, "0441000688210"), key(VIETCOM, "1051996380"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        verify(gateway, times(1)).login(any(), any(), any(), any());
        verify(gateway, times(2)).transaction(any(), any(), any(), any(), any());
    }

    @Test
    void unboundAccount_fallsBackToTheBankLevelLookup() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");   // no BANK_CERT_SEQ
        stubCredential(SHINHAN);

        NxisasScrapeSession s = startAndCapture(key(SHINHAN, "1111"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        verify(credentialService, times(1)).loadForScrapingByBank(SHINHAN);
    }

    @Test
    void crossBankBinding_isRefused_ratherThanScrapedWithTheWrongBanksLogin() {
        stubCleanFlow();
        // SEQ_NO 9 belongs to Woori, but the Shinhan account points at it.
        stubCredentialBySeq(9L, WOORI, 0L, "woori-user");
        stubBoundAccount(SHINHAN, "1111", 9L);

        NxisasScrapeSession s = startAndCapture(key(SHINHAN, "1111"));

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.FAILED);
        assertThat(s.getErrorCode()).isEqualTo("WBE2050");
        verify(gateway, never()).login(any(), any(), any(), any());
    }
}
