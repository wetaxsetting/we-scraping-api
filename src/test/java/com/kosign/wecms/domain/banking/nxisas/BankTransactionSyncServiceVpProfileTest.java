package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasBalanceJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasOutput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-level proof of the VP Bank sync seqNo-consistency fix
 * (docs/audits/bank-profile-enterprise-audit.md §13).
 *
 * <p>Drives {@link BankTransactionSyncService#start} for a VP Bank account
 * whose credential carries {@code NXISAS_GB_SEQ_NO = 0} and asserts that
 * the gateway's LOGIN, TRANSACTION, and BALANCE calls all receive the SAME
 * Coocon {@code Class} — {@code CORPORATE}. Before the fix, TRANSACTION and
 * BALANCE resolved {@code PERSONAL} via {@code find(bankCd, null)}.
 *
 * <p>Uses the real {@link NxisasBankConfig} (the unit under analysis) and
 * mocks the gateway + collaborators.
 *
 * <p>Also covers B-1 (ABBANK {@code loginType} parity): an ABBANK
 * credential with a blank loginType must send {@code loginType == ""}
 * on the outbound LOGIN.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
class BankTransactionSyncServiceVpProfileTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String VP_BANK = "11704102";
    private static final String VP_MODULE = "GLOBAL/VNM/BANK_VP";
    private static final String ACCT_NO = "0011001234567";
    private static final String OK = "00000000";

    private BankCredentialService credentialService;
    private NxisasGatewayClient gateway;
    private AcctMstrRepository acctRepo;
    private AcctTrnsHstrRepository trnsRepo;
    private LegacyFunctionRepository legacyFn;
    private NxisasSessionRegistry registry;
    private SyncHistoryWriter syncHistoryWriter;
    private AcctMstrStatusUpdater statusUpdater;

    private BankTransactionSyncService service;

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
                legacyFn, registry, syncHistoryWriter, statusUpdater);

        TenantContext.set(USER_ID, BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void vpSeqZeroUsesCorporateForLoginTransactionAndBalance() {
        // Account + credential: VP Bank, seq 0 (Corporate).
        AcctMstr acct = mock(AcctMstr.class);
        when(acct.getBankCd()).thenReturn(VP_BANK);
        when(acct.getAcctNo()).thenReturn(ACCT_NO);
        when(acct.getCcyCd()).thenReturn("VND");
        when(acct.getHandRegYn()).thenReturn("N");
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(acct));

        when(credentialService.loadForScrapingByBank(VP_BANK)).thenReturn(
                new DecryptedCredential(VP_BANK, "vpuser", "vppass", null, "ID", BigDecimal.ZERO));

        when(gateway.newTrSeq()).thenReturn("20260616000000");

        // LOGIN — clean (no challenge, no bank-adapter error).
        NxisasApiOut<NxisasLoginJobResult> loginOut = mock(NxisasApiOut.class);
        NxisasCommon common = mock(NxisasCommon.class);
        when(common.getResultCd()).thenReturn(OK);
        when(loginOut.getCommon()).thenReturn(common);
        when(loginOut.getSasSessionID()).thenReturn("sas-1");
        NxisasOutput loginBankOut = mock(NxisasOutput.class);
        when(loginBankOut.getErrorCode()).thenReturn(OK);
        NxisasLoginJobResult loginResult = mock(NxisasLoginJobResult.class);
        when(loginResult.getCallBackFunc()).thenReturn(null); // no challenge
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstOutput(loginOut)).thenReturn(loginBankOut);
        when(gateway.firstResult(loginOut)).thenReturn(loginResult);

        // TRANSACTION — clean, zero rows.
        NxisasApiOut<NxisasTransactionJobResult> txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(common);
        NxisasOutput txBankOut = mock(NxisasOutput.class);
        when(txBankOut.getErrorCode()).thenReturn(OK);
        NxisasTransactionJobResult txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.transaction(any(), any(), any(), any(), any())).thenReturn(txOut);
        when(gateway.firstOutput(txOut)).thenReturn(txBankOut);
        when(gateway.firstTransactionResult(txOut)).thenReturn(txResult);

        // BALANCE — clean, no balance rows.
        NxisasApiOut<NxisasBalanceJobResult> balOut = mock(NxisasApiOut.class);
        when(balOut.getCommon()).thenReturn(common);
        NxisasOutput balBankOut = mock(NxisasOutput.class);
        when(balBankOut.getErrorCode()).thenReturn(OK);
        NxisasBalanceJobResult balResult = mock(NxisasBalanceJobResult.class);
        when(balResult.getBalance()).thenReturn(null);
        when(balResult.getAcctList()).thenReturn(null);
        when(gateway.balance(any(), any(), any(), any(), any())).thenReturn(balOut);
        when(gateway.firstOutput(balOut)).thenReturn(balBankOut);
        when(gateway.firstBalanceResult(balOut)).thenReturn(balResult);

        TransactionsSyncRequest req = new TransactionsSyncRequest(
                List.of(new TransactionsSyncRequest.AccountKey(VP_BANK, ACCT_NO)),
                null, null, null);

        TransactionsSyncResponse resp = service.start(req);

        assertThat(resp.status()).isEqualTo(TransactionsSyncResponse.Status.COMPLETED);

        // All three gateway calls must carry Class = CORPORATE for VP seq 0.
        org.mockito.ArgumentCaptor<String> loginCat = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gateway).login(eq(VP_MODULE), loginCat.capture(), anyString(), any());
        assertThat(loginCat.getValue()).isEqualTo("CORPORATE");

        org.mockito.ArgumentCaptor<String> txCat = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gateway).transaction(eq(VP_MODULE), txCat.capture(), anyString(), any(), any());
        assertThat(txCat.getValue()).isEqualTo("CORPORATE");

        org.mockito.ArgumentCaptor<String> balCat = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gateway).balance(eq(VP_MODULE), balCat.capture(), anyString(), any(), any());
        assertThat(balCat.getValue()).isEqualTo("CORPORATE");
    }

    @Test
    void abbankBlankLoginTypeSendsEmptyStringOnLogin() {
        // ABBANK (11704106 → GLOBAL/VNM/BANK_AB, COMMON) credential with a
        // blank loginType. Legacy parity (B-1): the outbound LOGIN must carry
        // loginType == "", not "ID".
        final String AB_BANK = "11704106";
        final String AB_MODULE = "GLOBAL/VNM/BANK_AB";

        AcctMstr acct = mock(AcctMstr.class);
        when(acct.getBankCd()).thenReturn(AB_BANK);
        when(acct.getAcctNo()).thenReturn(ACCT_NO);
        when(acct.getCcyCd()).thenReturn("VND");
        when(acct.getHandRegYn()).thenReturn("N");
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(acct));

        // Credential carries a BLANK loginType (null) — the default kicks in.
        when(credentialService.loadForScrapingByBank(AB_BANK)).thenReturn(
                new DecryptedCredential(AB_BANK, "abuser", "abpass", null, null, BigDecimal.ZERO));

        when(gateway.newTrSeq()).thenReturn("20260617000000");

        NxisasApiOut<NxisasLoginJobResult> loginOut = mock(NxisasApiOut.class);
        NxisasCommon common = mock(NxisasCommon.class);
        when(common.getResultCd()).thenReturn(OK);
        when(loginOut.getCommon()).thenReturn(common);
        when(loginOut.getSasSessionID()).thenReturn("sas-ab");
        NxisasOutput loginBankOut = mock(NxisasOutput.class);
        when(loginBankOut.getErrorCode()).thenReturn(OK);
        NxisasLoginJobResult loginResult = mock(NxisasLoginJobResult.class);
        when(loginResult.getCallBackFunc()).thenReturn(null);
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstOutput(loginOut)).thenReturn(loginBankOut);
        when(gateway.firstResult(loginOut)).thenReturn(loginResult);

        NxisasApiOut<NxisasTransactionJobResult> txOut = mock(NxisasApiOut.class);
        when(txOut.getCommon()).thenReturn(common);
        NxisasOutput txBankOut = mock(NxisasOutput.class);
        when(txBankOut.getErrorCode()).thenReturn(OK);
        NxisasTransactionJobResult txResult = mock(NxisasTransactionJobResult.class);
        when(txResult.getTransaction()).thenReturn(null);
        when(gateway.transaction(any(), any(), any(), any(), any())).thenReturn(txOut);
        when(gateway.firstOutput(txOut)).thenReturn(txBankOut);
        when(gateway.firstTransactionResult(txOut)).thenReturn(txResult);

        NxisasApiOut<NxisasBalanceJobResult> balOut = mock(NxisasApiOut.class);
        when(balOut.getCommon()).thenReturn(common);
        NxisasOutput balBankOut = mock(NxisasOutput.class);
        when(balBankOut.getErrorCode()).thenReturn(OK);
        NxisasBalanceJobResult balResult = mock(NxisasBalanceJobResult.class);
        when(balResult.getBalance()).thenReturn(null);
        when(balResult.getAcctList()).thenReturn(null);
        when(gateway.balance(any(), any(), any(), any(), any())).thenReturn(balOut);
        when(gateway.firstOutput(balOut)).thenReturn(balBankOut);
        when(gateway.firstBalanceResult(balOut)).thenReturn(balResult);

        TransactionsSyncRequest req = new TransactionsSyncRequest(
                List.of(new TransactionsSyncRequest.AccountKey(AB_BANK, ACCT_NO)),
                null, null, null);

        service.start(req);

        org.mockito.ArgumentCaptor<com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput> loginInput =
                org.mockito.ArgumentCaptor.forClass(com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobInput.class);
        verify(gateway).login(eq(AB_MODULE), anyString(), anyString(), loginInput.capture());
        assertThat(loginInput.getValue().getLoginType()).isEqualTo("");
    }
}
