package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncPersistence.PersistResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobInput;
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
import java.util.concurrent.atomic.AtomicReference;

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
 * Fixed-Deposit ({@code ACCT_TYPE_CD "02"}) scrape routing for
 * {@link BankTransactionSyncService} — legacy parity with
 * {@code nxisas_tranList_osmc.js:300-361}:
 *
 * <ul>
 *   <li><b>VCB (11704004)</b> → {@code TRANSACTION_FIXED_DEPOSIT} +
 *       {@code BALANCE_FIXED_DEPOSIT} (both balance AND transaction).</li>
 *   <li><b>Shinhan/ACB/HD/Vietin/VP</b> → {@code BALANCE_FIXED_DEPOSIT} only
 *       (no transaction job); the account still completes as a success.</li>
 *   <li><b>Any other bank</b> (or non-FD account) → regular {@code BALANCE} +
 *       {@code TRANSACTION}.</li>
 * </ul>
 *
 * Uses an inline executor for deterministic state-machine coverage.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class BankTransactionSyncServiceFixedDepositTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String OK = "00000000";

    private static final String VCB = "11704004";       // FD balance + transaction
    private static final String SHINHAN = "11704088";    // FD balance-only
    private static final String ACB = "11704011";        // regular Current/Savings anchor

    private BankCredentialService credentialService;
    private NxisasGatewayClient gateway;
    private AcctMstrRepository acctRepo;
    private NxisasSessionRegistry registry;
    private BankTransactionSyncPersistence persistence;
    private AcctMstrStatusUpdater statusUpdater;
    private SyncHistoryWriter syncHistoryWriter;
    private BankTransactionSyncService service;

    private final AtomicReference<NxisasScrapeSession> sref = new AtomicReference<>();
    private final java.util.List<AcctMstr> mockedAccts = new java.util.ArrayList<>();
    private NxisasCommon okCommon;

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
        when(acctRepo.findAllById(any())).thenReturn(mockedAccts);
        when(gateway.newTrSeq()).thenReturn("20260716000000");

        org.mockito.Mockito.doAnswer(inv -> {
            sref.set(inv.getArgument(0));
            return null;
        }).when(registry).register(any());

        okCommon = mock(NxisasCommon.class);
        when(okCommon.getResultCd()).thenReturn(OK);
        stubLoginClean();
        when(persistence.persistTransactions(anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString()))
                .thenReturn(new PersistResult(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubLoginClean() {
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
    }

    private void stubAccount(String bankCd, String acctNo, String acctTypeCd) {
        stubAccount(bankCd, acctNo, acctTypeCd, "VND");
    }

    private void stubAccount(String bankCd, String acctNo, String acctTypeCd, String ccyCd) {
        AcctMstr acct = mock(AcctMstr.class);
        when(acct.getBankCd()).thenReturn(bankCd);
        when(acct.getAcctNo()).thenReturn(acctNo);
        when(acct.getCcyCd()).thenReturn(ccyCd);
        when(acct.getHandRegYn()).thenReturn("N");
        when(acct.getAcctTypeCd()).thenReturn(acctTypeCd);
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(eq(BIZ_KEY), eq(bankCd), eq(acctNo)))
                .thenReturn(Optional.of(acct));
        mockedAccts.add(acct);
        when(credentialService.loadForScrapingByBank(bankCd)).thenReturn(
                new DecryptedCredential(bankCd, "u", "p", null, "ID", BigDecimal.ZERO));
    }

    private void stubFdTransactionOk() {
        NxisasApiOut<NxisasTransactionJobResult> txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(okCommon);
        NxisasOutput txBankOut = mock(NxisasOutput.class);
        when(txBankOut.getErrorCode()).thenReturn(OK);
        NxisasTransactionJobResult txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.transactionFixedDeposit(any(), any(), any(), any(), any())).thenReturn(txOut);
        when(gateway.firstOutput(txOut)).thenReturn(txBankOut);
        when(gateway.firstTransactionResult(txOut)).thenReturn(txResult);
    }

    private void stubFdBalanceOk() {
        NxisasApiOut<NxisasBalanceJobResult> balOut = mock(NxisasApiOut.class);
        when(balOut.getCommon()).thenReturn(okCommon);
        NxisasOutput balBankOut = mock(NxisasOutput.class);
        when(balBankOut.getErrorCode()).thenReturn(OK);
        NxisasBalanceJobResult balResult = mock(NxisasBalanceJobResult.class);
        when(balResult.getBalance()).thenReturn(null);
        when(balResult.getAcctList()).thenReturn(null);
        when(gateway.balanceFixedDeposit(any(), any(), any(), any(), any())).thenReturn(balOut);
        when(gateway.firstOutput(balOut)).thenReturn(balBankOut);
        when(gateway.firstBalanceResult(balOut)).thenReturn(balResult);
    }

    private void start(String bankCd, String acctNo) {
        service.start(new TransactionsSyncRequest(
                List.of(new TransactionsSyncRequest.AccountKey(bankCd, acctNo)), null, null, null));
    }

    @Test
    void vcbFixedDeposit_dispatchesFdBalanceAndFdTransaction_notRegularJobs() {
        stubAccount(VCB, "9001", "02");
        stubFdTransactionOk();
        stubFdBalanceOk();

        start(VCB, "9001");

        NxisasScrapeSession s = sref.get();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(s.getSuccessAccounts()).isEqualTo(1);

        // VCB FD → both FD jobs, never the regular Current/Savings jobs.
        verify(gateway, times(1)).transactionFixedDeposit(any(), any(), any(), any(), any());
        verify(gateway, times(1)).balanceFixedDeposit(any(), any(), any(), any(), any());
        verify(gateway, never()).transaction(any(), any(), any(), any(), any());
        verify(gateway, never()).balance(any(), any(), any(), any(), any());
    }

    @Test
    void shinhanFixedDeposit_dispatchesFdBalanceOnly_noTransactionJob_stillSucceeds() {
        stubAccount(SHINHAN, "9002", "02");
        stubFdBalanceOk(); // no FD transaction stub — it must never be called

        start(SHINHAN, "9002");

        NxisasScrapeSession s = sref.get();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(s.getSuccessAccounts()).isEqualTo(1);

        // Balance-only FD bank → FD balance only, no transaction job of any kind.
        verify(gateway, times(1)).balanceFixedDeposit(any(), any(), any(), any(), any());
        verify(gateway, never()).transactionFixedDeposit(any(), any(), any(), any(), any());
        verify(gateway, never()).transaction(any(), any(), any(), any(), any());
        verify(gateway, never()).balance(any(), any(), any(), any(), any());
        // Success audit row still written (legacy: balance-only FD counts as success).
        verify(syncHistoryWriter, times(1)).recordAttempt(anyString(), anyString(), eq(SHINHAN),
                eq("9002"), any(), any(), eq(SyncHistoryWriter.RSLT_CD_SUCCESS), anyString());
    }

    @Test
    void vcbFixedDeposit_fcyUsd_dispatchesFdJobs_carryingUsdCurrency() {
        // FCY parity: the sync popup + endpoint are shared by the VND
        // (fnsh_0001_001) and FCY (fnsh_0005_001) account-inquiry pages. Routing
        // is by ACCT_TYPE_CD + BANK_CD only — currency is agnostic — and the
        // account's real CCY_CD (USD here) must flow into the FD job inputs.
        stubAccount(VCB, "9004", "02", "USD");
        stubFdTransactionOk();
        stubFdBalanceOk();

        ArgumentCaptor<NxisasTransactionJobInput> txIn =
                ArgumentCaptor.forClass(NxisasTransactionJobInput.class);
        ArgumentCaptor<NxisasBalanceJobInput> balIn =
                ArgumentCaptor.forClass(NxisasBalanceJobInput.class);

        start(VCB, "9004");

        assertThat(sref.get().getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        verify(gateway).transactionFixedDeposit(any(), any(), any(), txIn.capture(), any());
        verify(gateway).balanceFixedDeposit(any(), any(), any(), balIn.capture(), any());
        assertThat(txIn.getValue().getCurrency()).isEqualTo("USD");
        assertThat(balIn.getValue().getCurrency()).isEqualTo("USD");
        verify(gateway, never()).transaction(any(), any(), any(), any(), any());
        verify(gateway, never()).balance(any(), any(), any(), any(), any());
    }

    @Test
    void currentSavingsAccount_stillUsesRegularBalanceAndTransaction_noFdJobs() {
        // A non-FD account (ACCT_TYPE_CD "03" = Current) at a bank that also
        // appears in the FD whitelist must NOT be routed to the FD jobs — the
        // account type is the primary gate.
        stubAccount(ACB, "9003", "03");
        NxisasApiOut<NxisasTransactionJobResult> txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(okCommon);
        NxisasOutput txBankOut = mock(NxisasOutput.class);
        when(txBankOut.getErrorCode()).thenReturn(OK);
        NxisasTransactionJobResult txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.transaction(any(), any(), any(), any(), any())).thenReturn(txOut);
        when(gateway.firstOutput(txOut)).thenReturn(txBankOut);
        when(gateway.firstTransactionResult(txOut)).thenReturn(txResult);
        NxisasApiOut<NxisasBalanceJobResult> balOut = mock(NxisasApiOut.class);
        when(balOut.getCommon()).thenReturn(okCommon);
        NxisasOutput balBankOut = mock(NxisasOutput.class);
        when(balBankOut.getErrorCode()).thenReturn(OK);
        NxisasBalanceJobResult balResult = mock(NxisasBalanceJobResult.class);
        when(balResult.getBalance()).thenReturn(null);
        when(balResult.getAcctList()).thenReturn(null);
        when(gateway.balance(any(), any(), any(), any(), any())).thenReturn(balOut);
        when(gateway.firstOutput(balOut)).thenReturn(balBankOut);
        when(gateway.firstBalanceResult(balOut)).thenReturn(balResult);

        start(ACB, "9003");

        assertThat(sref.get().getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        verify(gateway, times(1)).transaction(any(), any(), any(), any(), any());
        verify(gateway, times(1)).balance(any(), any(), any(), any(), any());
        verify(gateway, never()).transactionFixedDeposit(any(), any(), any(), any(), any());
        verify(gateway, never()).balanceFixedDeposit(any(), any(), any(), any(), any());
    }
}
