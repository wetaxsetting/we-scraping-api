package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository.TenantUserManagementProjection;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest;
import com.kosign.wecms.domain.banking.dto.RegisterAccountsRequest.AccountEntry;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G7 + G2 (fan-out) — the live "+ Add" registration path
 * ({@code POST /api/v1/banking/accounts/register}).
 *
 * <ul>
 *   <li><b>G7</b> — new {@code ACCT_MSTR} rows receive a {@code PRN_SEQ}
 *       allocated through the funnel
 *       ({@link LegacyFunctionRepository#nextAcctMstrPrnSeq(String)} =
 *       {@code wb_get_next_val('acct_mstr','prn_seq',bizKey)}), seeded once and
 *       incremented across the batch.</li>
 *   <li><b>G2 fan-out</b> — withdrawal-capable banks fan out BOTH View + Withdraw
 *       {@code ACCT_AUTH_INFO} rows; non-whitelisted banks fan out View only.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AccountRegistrationServicePrnSeqAndWhitelistTest {

    @Mock AcctMstrRepository         acctMstrRepo;
    @Mock BankCredentialService      credentialService;
    @Mock AcctAuthInfoRepository     acctAuthRepo;
    @Mock UserBusiConnInfmRepository userBusiRepo;
    @Mock LegacyFunctionRepository   legacyFunctions;

    @Captor ArgumentCaptor<List<AcctMstr>>     acctCaptor;
    @Captor ArgumentCaptor<List<AcctAuthInfo>> authCaptor;

    AccountRegistrationService service;

    private static final String BIZ          = "VN_VNM0004_000001";
    private static final String USER         = "tester";
    private static final String SHINHAN      = "11704088"; // whitelisted
    private static final String NON_WL_BANK  = "11704099"; // not in the 5-bank whitelist
    private static final BigDecimal CRED_SEQ = new BigDecimal("42");

    @BeforeEach
    void setUp() {
        service = new AccountRegistrationService(
                acctMstrRepo, credentialService, acctAuthRepo, userBusiRepo, legacyFunctions,
                // C-14 / FIX B: the REAL guard, so the non-blocking ACCT_NM warning path runs here
                // rather than being silently skipped by a no-op mock.
                new com.kosign.wecms.domain.payment.overseas.service.OverseasSenderNameGuard(
                        acctMstrRepo, com.kosign.wecms.domain.payment.common.WireWidthGuards.real()));
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void register_allocatesPrnSeqFromFunnel_seedOnceIncrementAcrossBatch() {
        stubCredential(SHINHAN);
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(any(), any(), any()))
                .thenReturn(Optional.empty());
        // Funnel returns MAX(prn_seq)+1 = 5 for the tenant.
        when(legacyFunctions.nextAcctMstrPrnSeq(BIZ)).thenReturn("5");
        when(userBusiRepo.findTenantUsersForManagement(BIZ)).thenReturn(List.of());

        service.register(new RegisterAccountsRequest(CRED_SEQ, List.of(
                entry("700000000001"),
                entry("700000000002"),
                entry("700000000003"))));

        verify(legacyFunctions).nextAcctMstrPrnSeq(BIZ);
        verify(acctMstrRepo).saveAll(acctCaptor.capture());
        List<AcctMstr> saved = acctCaptor.getValue();
        // Seeded once at 5, then 6, 7 across the batch — never null.
        assertThat(saved).extracting(AcctMstr::getPrnSeq).containsExactly(5, 6, 7);
    }

    @Test
    void register_whitelistedBank_fansOutViewAndWithdraw() {
        stubCredential(SHINHAN);
        stubSingleNewAccount();
        TenantUserManagementProjection u = user(USER);
        when(legacyFunctions.nextAcctMstrPrnSeq(BIZ)).thenReturn("1");
        when(userBusiRepo.findTenantUsersForManagement(BIZ)).thenReturn(List.of(u));

        service.register(new RegisterAccountsRequest(CRED_SEQ, List.of(entry("700000000001"))));

        verify(acctAuthRepo).saveAll(authCaptor.capture());
        assertThat(authCaptor.getValue())
                .extracting(AcctAuthInfo::getAuthType)
                .containsExactlyInAnyOrder("V", "W");
    }

    @Test
    void register_nonWhitelistedBank_fansOutViewOnly() {
        stubCredential(NON_WL_BANK);
        stubSingleNewAccount();
        TenantUserManagementProjection u = user(USER);
        when(legacyFunctions.nextAcctMstrPrnSeq(BIZ)).thenReturn("1");
        when(userBusiRepo.findTenantUsersForManagement(BIZ)).thenReturn(List.of(u));

        service.register(new RegisterAccountsRequest(CRED_SEQ, List.of(entry("700000000001"))));

        verify(acctAuthRepo).saveAll(authCaptor.capture());
        // No 'W' row — withdraw is not available on a non-whitelisted bank.
        assertThat(authCaptor.getValue())
                .extracting(AcctAuthInfo::getAuthType)
                .containsExactly("V");
    }

    /* ------------------------------------------------------------------ */

    private void stubCredential(String bankCd) {
        when(credentialService.loadForScraping(CRED_SEQ))
                .thenReturn(new DecryptedCredential(bankCd, "u", "p", "c", "ID", null));
    }

    private void stubSingleNewAccount() {
        when(acctMstrRepo.findByBizKeyAndBankCdAndAcctNo(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    private static AccountEntry entry(String acctNo) {
        return new AccountEntry(acctNo, "VND", "03", "nick", "desc", "Holder", BigDecimal.ZERO,
                null, null, null);
    }

    private static TenantUserManagementProjection user(String userId) {
        TenantUserManagementProjection p = mock(TenantUserManagementProjection.class);
        when(p.getUserId()).thenReturn(userId);
        return p;
    }
}
