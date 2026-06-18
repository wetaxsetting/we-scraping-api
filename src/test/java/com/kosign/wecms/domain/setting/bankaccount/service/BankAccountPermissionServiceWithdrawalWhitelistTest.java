package com.kosign.wecms.domain.setting.bankaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionUpdateRequest;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * G2 — withdrawal-permission bank whitelist guard in
 * {@link BankAccountPermissionService#replace}.
 *
 * Withdraw ({@code AUTH_TYPE='W'}) may only be granted on the five firm-banking
 * withdrawal banks (legacy {@code comm_0183_002_u001_act.jsp:182/192} → WBE0452;
 * business decision 2026-06-16 = keep). A grant on any other bank must be
 * rejected with {@code WBE0452}; a whitelisted bank must pass the guard.
 */
@ExtendWith(MockitoExtension.class)
class BankAccountPermissionServiceWithdrawalWhitelistTest {

    @Mock AcctMstrRepository         acctRepo;
    @Mock AcctAuthInfoRepository     authRepo;
    @Mock UserBusiConnInfmRepository userBusiRepo;
    @Mock BusiAdmnInfmRepository     busiAdmnRepo;
    @Mock AuditLogService            auditLogService;

    BankAccountPermissionService service;

    private static final String BIZ         = "VN_VNM0004_000001";
    private static final String USER        = "tester";
    private static final String SHINHAN     = "11704088"; // whitelisted
    private static final String NON_WL_BANK = "11704099"; // not in the 5-bank whitelist
    private static final String ACCT        = "700000000001";

    @BeforeEach
    void setUp() {
        service = new BankAccountPermissionService(
                acctRepo, authRepo, userBusiRepo, busiAdmnRepo, auditLogService);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void replace_withdrawOnNonWhitelistedBank_throwsWBE0452() {
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, NON_WL_BANK, ACCT))
                .thenReturn(Optional.of(acct(NON_WL_BANK)));

        assertThatThrownBy(() -> service.replace(NON_WL_BANK, ACCT,
                new BankAccountPermissionUpdateRequest("WITHDRAWAL", List.of("u1"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE0452");
    }

    @Test
    void replace_withdrawOnWhitelistedBank_passesWhitelistGuard() {
        // Whitelisted bank skips the WBE0452 guard and reaches tenant validation;
        // with an unknown user that surfaces WBE_USER_NOT_IN_TENANT — proving the
        // whitelist guard did NOT fire for a whitelisted bank.
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, SHINHAN, ACCT))
                .thenReturn(Optional.of(acct(SHINHAN)));
        when(userBusiRepo.findAllByUserIdInAndBizKeyAndDelYn(any(), eq(BIZ), eq("N")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.replace(SHINHAN, ACCT,
                new BankAccountPermissionUpdateRequest("WITHDRAWAL", List.of("u1"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_USER_NOT_IN_TENANT");
    }

    private static AcctMstr acct(String bankCd) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd(bankCd);
        a.setAcctNo(ACCT);
        a.setCcyCd("VND");
        return a;
    }
}
