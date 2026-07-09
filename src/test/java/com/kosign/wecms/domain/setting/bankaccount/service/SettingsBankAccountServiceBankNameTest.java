package com.kosign.wecms.domain.setting.bankaccount.service;

import com.kosign.wecms.common.auditlog.entity.AuditLogsInfm;
import com.kosign.wecms.common.auditlog.repository.AuditLogEntryRepository;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountAuditResponse;
import com.kosign.wecms.domain.setting.bankaccount.dto.BankAccountListResponse;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G1 — Settings &gt; Bank Account list bank-name display.
 *
 * Pins that the displayed bank name is resolved through
 * {@link BankCdResolver#resolveBankNm(String)} (the legacy SHORT name
 * {@code wb_get_bank_nm(_,'s')}, e.g. {@code "Shinhan VN"}) — NOT the formal
 * {@code BANK_INFM.RPRS_BANK_NM} ({@code "Shinhan(Vietnam)"}). This is the
 * 2026-06-16 G1 fix; the service no longer queries {@code BankInfmRepository}.
 */
@ExtendWith(MockitoExtension.class)
class SettingsBankAccountServiceBankNameTest {

    @Mock AcctMstrRepository        acctRepo;
    @Mock BankCdResolver            bankCdResolver;
    @Mock BusiVasAcctInfmRepository vasAcctRepo;
    @Mock AcctAuthInfoRepository    acctAuthRepo;
    @Mock AuditLogService           auditLogService;
    @Mock AuditLogEntryRepository   auditLogReadRepo;
    @Mock com.kosign.wecms.domain.auth.repository.UserInfmRepository userInfmRepo;

    SettingsBankAccountService service;

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String USER     = "tester";
    private static final String SHINHAN  = "11704088";
    private static final String ACCT     = "700005285280";

    @BeforeEach
    void setUp() {
        service = new SettingsBankAccountService(
                acctRepo, bankCdResolver, vasAcctRepo, acctAuthRepo,
                auditLogService, auditLogReadRepo, userInfmRepo);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_resolvesBankNameViaResolver_shortName() {
        when(acctRepo.findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class)))
                .thenReturn(List.of(newAcct(SHINHAN, ACCT, "VND")));
        // Resolver returns the legacy SHORT name.
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan VN");
        // No active grantees → batched permission lookups return empty (default).

        BankAccountListResponse response = service.list();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).bankName()).isEqualTo("Shinhan VN");
        // Resolution went through the shared resolver, not RPRS_BANK_NM.
        verify(bankCdResolver).resolveBankNm(SHINHAN);
    }

    /* G11 — list shows ACTIVE accounts only (legacy ACTV_YN='Y' parity). */
    @Test
    void list_queriesActiveOnly_neverTheUnfilteredFinder() {
        when(acctRepo.findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class)))
                .thenReturn(List.of(newAcct(SHINHAN, ACCT, "VND")));
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan VN");

        BankAccountListResponse response = service.list();

        assertThat(response.items()).hasSize(1);
        // Active-only finder is used; the unfiltered finder is never called.
        verify(acctRepo).findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class));
        verify(acctRepo, never()).findByBizKey(any(), any(Sort.class));
    }

    /* Permission summary label — placeholder when no (non-admin) grantee.
     * The admin-exclusion itself lives in the repo JPQL (NOT EXISTS
     * BUSI_ADMN_INFM); these pin the service's label composition over the
     * admin-excluded counts the query returns. */
    @Test
    void list_permissionLabel_zeroGrantees_nullLabel_rendersPlaceholderOnFe() {
        when(acctRepo.findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class)))
                .thenReturn(List.of(newAcct(SHINHAN, ACCT, "VND")));
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan VN");
        // Admin-only account → batched count query excludes the admin → no group.
        when(acctAuthRepo.countActiveByTenant(BIZ)).thenReturn(List.of());
        when(acctAuthRepo.findActiveGranteesByTenant(BIZ)).thenReturn(List.of());

        BankAccountListResponse response = service.list();

        assertThat(response.items().get(0).viewPermissionLabel()).isNull();
        assertThat(response.items().get(0).withdrawalPermissionLabel()).isNull();
    }

    @Test
    void list_permissionLabel_singleGrantee_showsName() {
        when(acctRepo.findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class)))
                .thenReturn(List.of(newAcct(SHINHAN, ACCT, "VND")));
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan VN");
        when(acctAuthRepo.countActiveByTenant(BIZ)).thenReturn(List.of(pc(SHINHAN, ACCT, "V", 1L)));
        when(acctAuthRepo.findActiveGranteesByTenant(BIZ))
                .thenReturn(List.of(pn(SHINHAN, ACCT, "V", "Pedri Gonzalez")));

        BankAccountListResponse response = service.list();

        assertThat(response.items().get(0).viewPermissionLabel()).isEqualTo("Pedri Gonzalez");
        // Batched path used; the per-row finders are never called.
        verify(acctAuthRepo).countActiveByTenant(BIZ);
        verify(acctAuthRepo, never()).countActiveByAccount(any(), any(), any(), any());
        verify(acctAuthRepo, never()).findFirstActiveUserName(any(), any(), any(), any());
    }

    @Test
    void list_permissionLabel_multipleGrantees_showsNamePlusCount() {
        when(acctRepo.findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class)))
                .thenReturn(List.of(newAcct(SHINHAN, ACCT, "VND")));
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan VN");
        when(acctAuthRepo.countActiveByTenant(BIZ)).thenReturn(List.of(pc(SHINHAN, ACCT, "V", 3L)));
        when(acctAuthRepo.findActiveGranteesByTenant(BIZ))
                .thenReturn(List.of(pn(SHINHAN, ACCT, "V", "Pedri Gonzalez")));

        BankAccountListResponse response = service.list();

        // count (3) drives "+ N more"; head is the first grantee name.
        assertThat(response.items().get(0).viewPermissionLabel())
                .isEqualTo("Pedri Gonzalez + 2 more");
    }

    /* C1 parity — first-name selection is the EARLIEST grantee. The query
     * returns grantees ordered earliest-REG_DTM first per group; the service
     * keeps the first via putIfAbsent. Two grantees for the same (acct, V)
     * → the head is the first list entry, not the second. */
    @Test
    void list_permissionLabel_firstNameIsEarliestGrantee() {
        when(acctRepo.findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class)))
                .thenReturn(List.of(newAcct(SHINHAN, ACCT, "VND")));
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan VN");
        when(acctAuthRepo.countActiveByTenant(BIZ)).thenReturn(List.of(pc(SHINHAN, ACCT, "V", 2L)));
        when(acctAuthRepo.findActiveGranteesByTenant(BIZ)).thenReturn(List.of(
                pn(SHINHAN, ACCT, "V", "Earliest Grantee"),   // earliest REG_DTM (query order)
                pn(SHINHAN, ACCT, "V", "Later Grantee")));

        BankAccountListResponse response = service.list();

        assertThat(response.items().get(0).viewPermissionLabel())
                .isEqualTo("Earliest Grantee + 1 more");
    }

    /* C1 parity — count > 0 but no grantee has a USER_INFM name match
     * (query returns no name row for the group) → label is null, matching
     * the legacy {@code firstName.isEmpty() → null} branch. */
    @Test
    void list_permissionLabel_countButNoName_nullLabel() {
        when(acctRepo.findByBizKeyAndActvYn(eq(BIZ), eq("Y"), any(Sort.class)))
                .thenReturn(List.of(newAcct(SHINHAN, ACCT, "VND")));
        when(bankCdResolver.resolveBankNm(SHINHAN)).thenReturn("Shinhan VN");
        when(acctAuthRepo.countActiveByTenant(BIZ)).thenReturn(List.of(pc(SHINHAN, ACCT, "V", 2L)));
        when(acctAuthRepo.findActiveGranteesByTenant(BIZ)).thenReturn(List.of());

        BankAccountListResponse response = service.list();

        assertThat(response.items().get(0).viewPermissionLabel()).isNull();
    }

    private static AcctAuthInfoRepository.PermissionCount pc(
            String bankCd, String acctNo, String authType, long cnt) {
        return new AcctAuthInfoRepository.PermissionCount() {
            public String getBankCd()   { return bankCd; }
            public String getAcctNo()   { return acctNo; }
            public String getAuthType() { return authType; }
            public long   getCnt()      { return cnt; }
        };
    }

    private static AcctAuthInfoRepository.PermissionFirstName pn(
            String bankCd, String acctNo, String authType, String userNm) {
        return new AcctAuthInfoRepository.PermissionFirstName() {
            public String getBankCd()   { return bankCd; }
            public String getAcctNo()   { return acctNo; }
            public String getAuthType() { return authType; }
            public String getUserNm()   { return userNm; }
        };
    }

    /* G4 — audit logs: maps wire rows, tenant + REF_NO/WSVC_ID sets, latest-first. */
    @Test
    void auditLogs_mapsRows_scopedToTenantAndAccount() {
        when(auditLogReadRepo
                .findByBizKeyAndRefNoInAndRefNo1AndRefNo2AndWsvcIdInAndLangCdOrderByRegDtmDesc(
                        eq(BIZ), any(), eq(SHINHAN), eq(ACCT), any(), eq("EN")))
                .thenReturn(List.of(auditRow("Bank Account", "Edit bank account (Shinhan VN 700005285280)")));

        BankAccountAuditResponse resp = service.auditLogs(SHINHAN, ACCT);

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).auditLogsActv()).isEqualTo("Bank Account");
        assertThat(resp.items().get(0).regNm()).isEqualTo("Kim Thu");
        assertThat(resp.items().get(0).regDtm()).isEqualTo("20260211013357");
        verify(auditLogReadRepo)
                .findByBizKeyAndRefNoInAndRefNo1AndRefNo2AndWsvcIdInAndLangCdOrderByRegDtmDesc(
                        eq(BIZ), any(), eq(SHINHAN), eq(ACCT), any(), eq("EN"));
    }

    /* G4 — empty history returns an empty list, never an error. */
    @Test
    void auditLogs_emptyHistory_returnsEmptyList() {
        when(auditLogReadRepo
                .findByBizKeyAndRefNoInAndRefNo1AndRefNo2AndWsvcIdInAndLangCdOrderByRegDtmDesc(
                        eq(BIZ), any(), eq(SHINHAN), eq(ACCT), any(), eq("EN")))
                .thenReturn(List.of());

        BankAccountAuditResponse resp = service.auditLogs(SHINHAN, ACCT);

        assertThat(resp.items()).isEmpty();
    }

    private static AuditLogsInfm auditRow(String activity, String detail) {
        AuditLogsInfm e = new AuditLogsInfm();
        e.setBizKey(BIZ);
        e.setAuditLogsSeqNo(new java.math.BigDecimal("1"));
        e.setWsvcId("bank-account");
        e.setLangCd("EN");
        e.setRefNo("ACCT_MSTR");
        e.setRefNo1(SHINHAN);
        e.setRefNo2(ACCT);
        e.setAuditLogsActv(activity);
        e.setAuditLogsActvDtl(detail);
        e.setRegId("kimthu");
        e.setRegNm("Kim Thu");
        e.setRegDtm("20260211013357");
        return e;
    }

    private static AcctMstr newAcct(String bankCd, String acctNo, String ccyCd) {
        AcctMstr a = new AcctMstr();
        a.setBizKey(BIZ);
        a.setBankCd(bankCd);
        a.setAcctNo(acctNo);
        a.setCcyCd(ccyCd);
        a.setAcctTypeCd("03");
        return a;
    }
}
