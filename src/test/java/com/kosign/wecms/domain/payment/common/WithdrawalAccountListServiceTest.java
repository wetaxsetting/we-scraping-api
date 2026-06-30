package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
import com.kosign.wecms.domain.setting.bankaccount.entity.AcctAuthInfo;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Legacy-parity coverage for the shared payment Excel-Upload
 * "Withdrawal Bank/Account No." dropdown (legacy {@code comm_1001_001}).
 *
 * <p>This is the single behavior lock for all five consumers (Internal
 * Transfer, Vendor, Expense, Payroll, Overseas) after the Option A
 * consolidation onto {@link WithdrawalAccountListService}. Locks the
 * three remediation fixes:
 * <ul>
 *   <li><b>G1</b> — the query is called with the legacy bank set
 *       {@code ('11704088','11704020','11704004')}.</li>
 *   <li><b>G2</b> — only accounts the user holds an active {@code V}/{@code W}/{@code F}
 *       grant on are returned; other grant types and ungranted accounts are
 *       dropped.</li>
 *   <li><b>No admin bypass</b> — an ADMIN principal with no grants gets an
 *       empty list (legacy {@code comm_1001_001} has no admin branch).</li>
 *   <li><b>G3</b> — the firm-banking fallback flag passed to the query is
 *       {@code false} (Mockito leaves the {@code @Value} field at its
 *       strict-parity default).</li>
 * </ul>
 *
 * <p>Pure Mockito. The query's own bank / firm-banking SQL is exercised
 * at the DB layer; here it is mocked so the test isolates the service-side
 * G2 intersection and the G1/G3 arguments handed to the repository.
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalAccountListServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    private static final String SHINHAN = "11704088";
    private static final String WOORI   = "11704020";
    private static final String VCB     = "11704004";

    @Mock private AcctMstrRepository     acctMstrRepository;
    @Mock private AcctAuthInfoRepository acctAuthInfoRepository;
    @Mock private BankInfmRepository     bankInfmRepository;

    @InjectMocks
    private WithdrawalAccountListService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
        // bank-name lookup is incidental to this test — fall back to BANK_CD.
        lenient().when(bankInfmRepository.findById(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_appliesBankSet_firmFallbackOff_andVwfPermissionIntersection() {
        // Four accounts that already passed the bank + firm-banking gate
        // (the query is mocked). User has: V on A, W on B, F on D (VCB), and a
        // non-V/W/F grant on C.
        AcctMstr a = acct(SHINHAN, "1111", "03", "VND");
        AcctMstr b = acct(WOORI,   "2222", "04", "USD");
        AcctMstr c = acct(WOORI,   "3333", "05", "VND");
        AcctMstr d = acct(VCB,     "4444", "03", "VND");
        when(acctMstrRepository.findFirmBankingWithdrawalAccounts(eq(TENANT), any(), any(), eq(false)))
                .thenReturn(List.of(a, b, c, d));
        when(acctAuthInfoRepository.findByBizKeyAndUserIdAndUseYn(TENANT, USER, "Y"))
                .thenReturn(List.of(
                        grant(SHINHAN, "1111", WithdrawalAccountConstants.AUTH_TYPE_VIEW),
                        grant(WOORI,   "2222", WithdrawalAccountConstants.AUTH_TYPE_WITHDRAW),
                        grant(WOORI,   "3333", "M"),    // non-V/W/F → must be excluded
                        grant(VCB,     "4444", WithdrawalAccountConstants.AUTH_TYPE_FILE)));

        List<WithdrawalAccountRow> rows = service.list(null);

        // G2: the V-, W- and F-granted accounts survive; the 'M' grant is dropped.
        assertThat(rows).extracting(WithdrawalAccountRow::acctNo)
                .containsExactlyInAnyOrder("1111", "2222", "4444");

        // G1 + G3: the query received the legacy 3-bank set and fallback OFF.
        ArgumentCaptor<java.util.Collection<String>> banks = ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<Boolean> fallback = ArgumentCaptor.forClass(Boolean.class);
        org.mockito.Mockito.verify(acctMstrRepository)
                .findFirmBankingWithdrawalAccounts(eq(TENANT), any(), banks.capture(), fallback.capture());
        assertThat(banks.getValue()).containsExactlyInAnyOrder(SHINHAN, WOORI, VCB);
        assertThat(banks.getValue()).isEqualTo(WithdrawalAccountConstants.WITHDRAWAL_BANK_CDS);
        assertThat(fallback.getValue()).isFalse();
    }

    @Test
    void list_adminWithNoGrants_returnsEmpty_noAdminBypass() {
        // ADMIN principal — legacy comm_1001_001 has no admin branch, so the
        // V/W permission filter still applies and an ungranted admin sees nothing.
        TenantContext.set(USER, USER, TENANT, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");
        when(acctMstrRepository.findFirmBankingWithdrawalAccounts(eq(TENANT), any(), any(), eq(false)))
                .thenReturn(List.of(acct(SHINHAN, "1111", "03", "VND")));
        when(acctAuthInfoRepository.findByBizKeyAndUserIdAndUseYn(TENANT, USER, "Y"))
                .thenReturn(List.of());

        List<WithdrawalAccountRow> rows = service.list(null);

        assertThat(rows).isEmpty();
    }

    /* ----------------------------------------------------------------- */

    private static AcctMstr acct(String bankCd, String acctNo, String acctTypeCd, String ccyCd) {
        AcctMstr a = new AcctMstr();
        a.setBankCd(bankCd);
        a.setAcctNo(acctNo);
        a.setAcctTypeCd(acctTypeCd);
        a.setCcyCd(ccyCd);
        a.setAcctNickNm(null);
        return a;
    }

    private static AcctAuthInfo grant(String bankCd, String acctNo, String authType) {
        AcctAuthInfo g = new AcctAuthInfo();
        g.setBankCd(bankCd);
        g.setAcctNo(acctNo);
        g.setAuthType(authType);
        return g;
    }
}
