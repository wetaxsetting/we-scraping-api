package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOTPJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Legacy login-once-per-bank parity for Bank Transaction Sync
 * ({@code nxisas_api.js} {@code _nxisas_auth[bankCd]} cache → refactor
 * {@code NxisasScrapeSession.activeLoginGroupKey}).
 *
 * <p>Drives {@link BankTransactionSyncService} with the real
 * {@link NxisasBankConfig} and mocked collaborators. Banks under test are
 * Shinhan ({@code 11704088} → {@code GLOBAL/GBL/BANK_Shinhan}) and Woori
 * ({@code 11704020} → {@code GLOBAL/GBL/BANK_Woori}).
 *
 * <p>Asserts:
 * <ul>
 *   <li>one account → one LOGIN (baseline still works);</li>
 *   <li>two accounts, same bank → exactly one LOGIN (session reuse);</li>
 *   <li>two banks × two accounts → exactly one LOGIN per bank group;</li>
 *   <li>OTP on a group → challenge once, resume drains the rest of the
 *       group with no second LOGIN;</li>
 *   <li>transaction-row persistence still runs under the grouped flow;</li>
 *   <li>bank-adapter codes {@code 42120011} / {@code 42120111} are
 *       non-fatal in the sync path.</li>
 * </ul>
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
    private AcctTrnsHstrRepository trnsRepo;
    private LegacyFunctionRepository legacyFn;
    private NxisasSessionRegistry registry;
    private SyncHistoryWriter syncHistoryWriter;
    private AcctMstrStatusUpdater statusUpdater;

    private BankTransactionSyncService service;

    // Cumulative set of stubbed accounts — returned by the batched
    // acctRepo.findAllById(...) used by the sync pre-filter loop.
    private java.util.List<AcctMstr> mockedAccts;

    // Shared OK gateway envelope.
    private NxisasCommon okCommon;
    // Per-job out objects (shared across banks — login() returns the same
    // clean LOGIN response regardless of module; the test counts invocations
    // by module argument, not by distinct return values).
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
        trnsRepo = mock(AcctTrnsHstrRepository.class);
        legacyFn = mock(LegacyFunctionRepository.class);
        registry = mock(NxisasSessionRegistry.class);
        syncHistoryWriter = mock(SyncHistoryWriter.class);
        statusUpdater = mock(AcctMstrStatusUpdater.class);

        service = new BankTransactionSyncService(
                credentialService, gateway, new NxisasBankConfig(), acctRepo, trnsRepo,
                legacyFn, registry, syncHistoryWriter, statusUpdater,
                mock(com.kosign.wecms.domain.banking.service.AutoMatchService.class));

        TenantContext.set(USER_ID, BIZ_KEY, LangCd.EN);

        mockedAccts = new java.util.ArrayList<>();
        when(acctRepo.findAllById(any())).thenReturn(mockedAccts);

        when(gateway.newTrSeq()).thenReturn("20260617000000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────
    // Mock scaffolding
    // ─────────────────────────────────────────────────────────────────

    /** Stub a clean LOGIN → TRANSACTION(0 rows) → BALANCE(no rows) flow that
     *  works for any bank/account. {@code loginResult.getCallBackFunc()} is
     *  null so no challenge fires. */
    private void stubCleanFlow() {
        okCommon = mock(NxisasCommon.class);
        when(okCommon.getResultCd()).thenReturn(OK);

        // LOGIN — same clean response for every module.
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

        // TRANSACTION — clean, zero rows.
        txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(okCommon);
        when(gateway.transaction(any(), any(), any(), any(), any())).thenReturn(txOut);
        NxisasOutput txBankOut = mock(NxisasOutput.class);
        when(txBankOut.getErrorCode()).thenReturn(OK);
        txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.firstOutput(txOut)).thenReturn(txBankOut);
        when(gateway.firstTransactionResult(txOut)).thenReturn(txResult);

        // BALANCE — clean, no rows.
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
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(eq(BIZ_KEY), eq(bankCd), eq(acctNo)))
                .thenReturn(Optional.of(acct));
        mockedAccts.add(acct);
    }

    private void stubCredential(String bankCd) {
        when(credentialService.loadForScrapingByBank(bankCd)).thenReturn(
                new DecryptedCredential(bankCd, bankCd + "-user", "pass", null, "ID", BigDecimal.ZERO));
    }

    private static TransactionsSyncRequest.AccountKey key(String bankCd, String acctNo) {
        return new TransactionsSyncRequest.AccountKey(bankCd, acctNo);
    }

    private TransactionsSyncResponse start(TransactionsSyncRequest.AccountKey... keys) {
        return service.start(new TransactionsSyncRequest(List.of(keys), null, null, null));
    }

    // ─────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    void singleAccount_completesWithOneLogin() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");
        stubCredential(SHINHAN);

        TransactionsSyncResponse resp = start(key(SHINHAN, "1111"));

        assertThat(resp.status()).isEqualTo(TransactionsSyncResponse.Status.COMPLETED);
        verify(gateway, times(1)).login(eq(SHINHAN_MODULE), any(), any(), any());
        verify(gateway, times(1)).transaction(any(), any(), any(), any(), any());
    }

    @Test
    void twoAccountsSameBank_loginCalledOnce() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");
        stubAccount(SHINHAN, "2222");
        stubCredential(SHINHAN);

        TransactionsSyncResponse resp = start(key(SHINHAN, "1111"), key(SHINHAN, "2222"));

        assertThat(resp.status()).isEqualTo(TransactionsSyncResponse.Status.COMPLETED);
        // Legacy parity: one LOGIN for both Shinhan accounts.
        verify(gateway, times(1)).login(any(), any(), any(), any());
        // …but both accounts were scraped (TRANSACTION ran per account).
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

        // Interleave the selection order to prove grouping reorders the queue.
        TransactionsSyncResponse resp = start(
                key(SHINHAN, "1111"), key(WOORI, "3333"),
                key(SHINHAN, "2222"), key(WOORI, "4444"));

        assertThat(resp.status()).isEqualTo(TransactionsSyncResponse.Status.COMPLETED);
        // 2 logins total — one per bank group, not 4.
        verify(gateway, times(2)).login(any(), any(), any(), any());
        verify(gateway, times(1)).login(eq(SHINHAN_MODULE), any(), any(), any());
        verify(gateway, times(1)).login(eq(WOORI_MODULE), any(), any(), any());
        // All four accounts scraped.
        verify(gateway, times(4)).transaction(any(), any(), any(), any(), any());
    }

    @Test
    void otpOnGroup_challengeOnce_resumeDrainsRestOfGroupWithoutRelogin() {
        // Shinhan LOGIN raises an OTP challenge; after the OTP is submitted,
        // BOTH Shinhan accounts must be scraped under the same session — one
        // LOGIN, one OTP, two accounts.
        okCommon = mock(NxisasCommon.class);
        when(okCommon.getResultCd()).thenReturn(OK);

        stubAccount(SHINHAN, "1111");
        stubAccount(SHINHAN, "2222");
        stubCredential(SHINHAN);

        // LOGIN returns a clean envelope; the challenge is signalled via the
        // result's CallBackFunc. firstResult(any) is sequenced: 1st call (the
        // LOGIN) → OTP; 2nd call (the resume's wrapped result) → clean.
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

        // OTP resume response (shares the LOGIN result shape).
        NxisasApiOut<NxisasOTPJobResult> otpResumeOut = mock(NxisasApiOut.class);
        when(otpResumeOut.getCommon()).thenReturn(okCommon);
        when(otpResumeOut.getSasSessionID()).thenReturn("sas-live");
        when(otpResumeOut.getResultList()).thenReturn(null);
        when(gateway.submitOtp(any(), any(), any(), any(), any())).thenReturn(otpResumeOut);

        // TRANSACTION + BALANCE — clean for both accounts.
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

        // Capture the registered session so the OTP resume can find it.
        TransactionsSyncResponse first = start(key(SHINHAN, "1111"), key(SHINHAN, "2222"));
        assertThat(first.status()).isEqualTo(TransactionsSyncResponse.Status.OTP_REQUIRED);
        // No account scraped before the OTP is answered.
        verify(gateway, never()).transaction(any(), any(), any(), any(), any());

        ArgumentCaptor<NxisasScrapeSession> sessCap = ArgumentCaptor.forClass(NxisasScrapeSession.class);
        verify(registry).register(sessCap.capture());
        NxisasScrapeSession session = sessCap.getValue();
        when(registry.find(eq(session.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(session));

        TransactionsSyncResponse resumed =
                service.submitOtp(session.getSessionId(), new SubmitChallengeRequest("123456"));

        assertThat(resumed.status()).isEqualTo(TransactionsSyncResponse.Status.COMPLETED);
        // Exactly one LOGIN and one OTP for the whole group.
        verify(gateway, times(1)).login(any(), any(), any(), any());
        verify(gateway, times(1)).submitOtp(any(), any(), any(), any(), any());
        // Both Shinhan accounts drained after the single OTP.
        verify(gateway, times(2)).transaction(any(), any(), any(), any(), any());
    }

    @Test
    void persistence_insertsScrapedRowUnderGroupedFlow() {
        stubCleanFlow();
        stubAccount(SHINHAN, "1111");
        stubCredential(SHINHAN);

        // One credit transaction row returned by TRANSACTION.
        NxisasTransactionEntry entry = mock(NxisasTransactionEntry.class);
        when(entry.getCredit()).thenReturn("1000");
        when(entry.getDebit()).thenReturn(null);
        when(entry.getDate()).thenReturn("20260601");
        when(entry.getTime()).thenReturn("101500");
        when(entry.getBalance()).thenReturn("5000");
        when(entry.getRemarks()).thenReturn("salary");
        when(entry.getRemarks2()).thenReturn(null);
        when(entry.getBankReference()).thenReturn("REF1");
        when(txResult.getTransaction()).thenReturn(List.of(entry));

        when(trnsRepo.findFirstByBizKeyAndBankTrnsFullId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(legacyFn.nextAcctTrnsHstrAcctSeqNo(BIZ_KEY)).thenReturn("42");

        TransactionsSyncResponse resp = start(key(SHINHAN, "1111"));

        assertThat(resp.status()).isEqualTo(TransactionsSyncResponse.Status.COMPLETED);
        // The scraped row was persisted.
        ArgumentCaptor<AcctTrnsHstr> rowCap = ArgumentCaptor.forClass(AcctTrnsHstr.class);
        verify(trnsRepo, times(1)).save(rowCap.capture());
        AcctTrnsHstr saved = rowCap.getValue();
        assertThat(saved.getBankCd()).isEqualTo(SHINHAN);
        assertThat(saved.getAcctNo()).isEqualTo("1111");
        assertThat(saved.getInoutDv()).isEqualTo("1"); // credit / IN
        assertThat(saved.getTrscAmt()).isEqualByComparingTo("1000");
    }

    @Test
    void bankAdapterEdgeCodesAreNonFatal() {
        // A LOGIN whose bank-adapter Output.ErrorCode is 42120011 (redmine
        // #36034) must NOT terminate the sync as ERROR — it is whitelisted.
        for (String edgeCode : List.of("42120011", "42120111")) {
            setUp(); // fresh mocks per code
            stubCleanFlow();
            // Override the LOGIN bank-adapter code to the edge value.
            NxisasOutput edgeBankOut = mock(NxisasOutput.class);
            when(edgeBankOut.getErrorCode()).thenReturn(edgeCode);
            when(gateway.firstOutput(loginOut)).thenReturn(edgeBankOut);
            stubAccount(SHINHAN, "1111");
            stubCredential(SHINHAN);

            TransactionsSyncResponse resp = start(key(SHINHAN, "1111"));

            assertThat(resp.status())
                    .as("edge code %s should be non-fatal", edgeCode)
                    .isEqualTo(TransactionsSyncResponse.Status.COMPLETED);
        }
    }
}
