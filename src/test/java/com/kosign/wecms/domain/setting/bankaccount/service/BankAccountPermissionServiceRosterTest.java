package com.kosign.wecms.domain.setting.bankaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository.TenantUserManagementProjection;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionMember;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountPermissionResponse;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G2 (UI parity) — Settings &gt; Bank Account permission roster.
 *
 * Pins that admins are EXCLUDED from the View / Withdrawal permission popup,
 * matching legacy {@code comm_0183_002_r001} (IDO {@code ACCT_AUTH_INFO_R004:80}
 * filters {@code AND T.ADMIN_YN != 'Y'}). Admins are implicitly authorized and
 * never appear as selectable grantees; only normal users are listed.
 */
@ExtendWith(MockitoExtension.class)
class BankAccountPermissionServiceRosterTest {

    @Mock AcctMstrRepository         acctRepo;
    @Mock AcctAuthInfoRepository     authRepo;
    @Mock UserBusiConnInfmRepository userBusiRepo;
    @Mock BusiAdmnInfmRepository     busiAdmnRepo;
    @Mock AuditLogService            auditLogService;

    BankAccountPermissionService service;

    private static final String BIZ    = "VN_VNM0004_000001";
    private static final String CALLER = "caller";
    private static final String BANK   = "11704088";
    private static final String ACCT   = "700000000001";
    private static final String ADMIN  = "admin@webcash.com";
    private static final String USER   = "lamine@gmail.com";

    @BeforeEach
    void setUp() {
        service = new BankAccountPermissionService(
                acctRepo, authRepo, userBusiRepo, busiAdmnRepo, auditLogService);
        TenantContext.set(CALLER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_excludesAdmins_includesOnlyNormalUsers() {
        TenantUserManagementProjection adminUser = user(ADMIN, "Admin Boss");
        TenantUserManagementProjection normalUser = user(USER, "Lamine Yamal");
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, BANK, ACCT))
                .thenReturn(Optional.of(acct()));
        when(authRepo.findAllByAccount(BIZ, BANK, ACCT, "V")).thenReturn(List.of());
        when(userBusiRepo.findTenantUsersForManagement(BIZ))
                .thenReturn(List.of(adminUser, normalUser));
        when(busiAdmnRepo.findAllByBizKey(BIZ)).thenReturn(List.of(admin(ADMIN)));

        BankAccountPermissionResponse resp = service.list(BANK, ACCT, "VIEW");

        // Only the normal user is listed — the admin is excluded entirely.
        assertThat(resp.members()).hasSize(1);
        BankAccountPermissionMember only = resp.members().get(0);
        assertThat(only.userId()).isEqualTo(USER);
        assertThat(only.userType()).isEqualTo("USER");
        // No member is ever typed ADMIN.
        assertThat(resp.members())
                .noneMatch(m -> "ADMIN".equals(m.userType()));
        // Tenant scoping: the cross-tenant findAll() must never be used.
        verify(busiAdmnRepo).findAllByBizKey(BIZ);
        verify(busiAdmnRepo, never()).findAll();
    }

    @Test
    void list_withdrawalKind_alsoExcludesAdmins() {
        TenantUserManagementProjection adminUser = user(ADMIN, "Admin Boss");
        TenantUserManagementProjection normalUser = user(USER, "Lamine Yamal");
        when(acctRepo.findByBizKeyAndBankCdAndAcctNo(BIZ, BANK, ACCT))
                .thenReturn(Optional.of(acct()));
        when(authRepo.findAllByAccount(BIZ, BANK, ACCT, "W")).thenReturn(List.of());
        when(userBusiRepo.findTenantUsersForManagement(BIZ))
                .thenReturn(List.of(adminUser, normalUser));
        when(busiAdmnRepo.findAllByBizKey(BIZ)).thenReturn(List.of(admin(ADMIN)));

        BankAccountPermissionResponse resp = service.list(BANK, ACCT, "WITHDRAWAL");

        assertThat(resp.members()).hasSize(1);
        assertThat(resp.members().get(0).userId()).isEqualTo(USER);
        assertThat(resp.members()).noneMatch(m -> "ADMIN".equals(m.userType()));
        verify(busiAdmnRepo, never()).findAll();
    }

    /* ------------------------------------------------------------------ */

    private static AcctMstr acct() {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd(BANK);
        a.setAcctNo(ACCT);
        a.setCcyCd("VND");
        return a;
    }

    private static BusiAdmnInfm admin(String userId) {
        BusiAdmnInfm a = new BusiAdmnInfm();
        a.setBizKey(BIZ);
        a.setUserId(userId);
        a.setUserNm("Admin");
        return a;
    }

    private static TenantUserManagementProjection user(String userId, String userNm) {
        TenantUserManagementProjection p = mock(TenantUserManagementProjection.class);
        // Lenient: the admin projection's name getter is never read (admins are
        // skipped before the member is built), so strict stubbing would flag it.
        org.mockito.Mockito.lenient().when(p.getUserId()).thenReturn(userId);
        org.mockito.Mockito.lenient().when(p.getUserNm()).thenReturn(userNm);
        return p;
    }
}
