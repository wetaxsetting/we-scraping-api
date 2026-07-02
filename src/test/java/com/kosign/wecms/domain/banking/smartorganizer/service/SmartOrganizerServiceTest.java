package com.kosign.wecms.domain.banking.smartorganizer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.banking.service.AccountAccessGuard;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmRequest;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmResponse;
import com.kosign.wecms.domain.banking.smartorganizer.repository.SmartOrganizerEligibleRepository;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetInfm;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SO-CONFIRM / SO-ELIG — Smart Organizer usage-group ↔ direction parity.
 *
 * <p>Confirm: legacy {@code fnsh_0200_001_c001_act.jsp:96-116} rejects a
 * confirm whose chosen usage's {@code USAG_GRP_CD} contradicts the bank
 * row direction (deposit '1' needs group '4', withdrawal '2' needs '6').
 * Eligibility: legacy {@code getDynamic1}
 * ({@code fnsh_0200_001_r001_act.jsp:100-112}) applies the same
 * group↔direction filter to the eligible list.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmartOrganizerServiceTest {

    @Mock AcctTrnsHstrRepository       acctTrnsHstrRepo;
    @Mock AcctTrnsRcvPayInfmRepository rcvPayRepo;
    @Mock LegacyFunctionRepository     legacy;
    @Mock AccountAccessGuard           accountAccessGuard;
    @Mock BusiVasAcctInfmRepository    vasRepo;
    @Mock TrcoInfmRepository           trcoRepo;
    @Mock PurposeSettingsRepository    purposeRepo;
    @Mock ChartOfAccountRepository     chartRepo;
    @Mock AcctMstrRepository           acctMstrRepo;
    @Mock BankCdResolver               bankCdResolver;
    @Mock SmartOrganizerEligibleRepository eligibleRepo;

    SmartOrganizerService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String BANK = "11704088";
    private static final String ACCT = "5280123456";

    @BeforeEach
    void setUp() {
        service = new SmartOrganizerService(
                acctTrnsHstrRepo, rcvPayRepo, legacy, accountAccessGuard,
                vasRepo, trcoRepo, purposeRepo, chartRepo, acctMstrRepo, bankCdResolver,
                eligibleRepo);
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ---- confirm-time direction validation (4 required cases) ---------- */

    /** IN (deposit '1') + group-'4' usage = PASS. */
    @Test
    void confirm_depositWithGroup4Usage_passes() {
        stubBank("1");
        when(rcvPayRepo.existsByBizKeyAndAcctSeqNoAndCnfmYn(BIZ, "10", "Y")).thenReturn(false);
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "4000000300"))
                .thenReturn(Optional.of(usage("4000000300", "4")));
        when(legacy.nextVal(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("1");

        ConfirmResponse res = service.confirm(confirmReq("4000000300"));

        assertThat(res.confirmedCount()).isEqualTo(1);
        verify(rcvPayRepo).save(any());
    }

    /** IN (deposit '1') + group-'6' usage = FAIL (rollback, no save). */
    @Test
    void confirm_depositWithGroup6Usage_fails() {
        stubBank("1");
        when(rcvPayRepo.existsByBizKeyAndAcctSeqNoAndCnfmYn(BIZ, "10", "Y")).thenReturn(false);
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "6000000300"))
                .thenReturn(Optional.of(usage("6000000300", "6")));

        assertThatThrownBy(() -> service.confirm(confirmReq("6000000300")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WBE_SMART_ORGANIZER_USAGE_DIRECTION_MISMATCH");
        verify(rcvPayRepo, never()).save(any());
    }

    /** OUT (withdrawal '2') + group-'6' usage = PASS. */
    @Test
    void confirm_withdrawalWithGroup6Usage_passes() {
        stubBank("2");
        when(rcvPayRepo.existsByBizKeyAndAcctSeqNoAndCnfmYn(BIZ, "10", "Y")).thenReturn(false);
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "6000000300"))
                .thenReturn(Optional.of(usage("6000000300", "6")));
        when(legacy.nextVal(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("1");

        ConfirmResponse res = service.confirm(confirmReq("6000000300"));

        assertThat(res.confirmedCount()).isEqualTo(1);
        verify(rcvPayRepo).save(any());
    }

    /** OUT (withdrawal '2') + group-'4' usage = FAIL (rollback, no save). */
    @Test
    void confirm_withdrawalWithGroup4Usage_fails() {
        stubBank("2");
        when(rcvPayRepo.existsByBizKeyAndAcctSeqNoAndCnfmYn(BIZ, "10", "Y")).thenReturn(false);
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "4000000300"))
                .thenReturn(Optional.of(usage("4000000300", "4")));

        assertThatThrownBy(() -> service.confirm(confirmReq("4000000300")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WBE_SMART_ORGANIZER_USAGE_DIRECTION_MISMATCH");
        verify(rcvPayRepo, never()).save(any());
    }

    /* ---- confirmer stamping parity (varchar(20) overflow regression) ---- */

    /**
     * Legacy parity + regression: the Smart Organizer confirm write stamps
     * {@code CNFM_BY_USER_ID} with the {@code BIZ_KEY}, not the login user id
     * (mirrors {@code fnsh_0002_001_u002_act.jsp:90,103}). The column is
     * {@code varchar(20)}; a 24-char email login (the HAENGSUNG admin
     * {@code wecmshaengsung@gmail.com}) previously overflowed it and threw
     * {@code value too long for type character varying(20)} → {@code WBE_DB}.
     * See docs/audits/transaction-history-confirm-legacy-audit.md §12.
     */
    @Test
    void confirm_stampsBizKeyIntoCnfmByUserId_notLongUserId() {
        final String hsBiz  = "VN_VNM0004_000014";           // 17 chars — fits varchar(20)
        final String hsUser = "wecmshaengsung@gmail.com";    // 24 chars — overflowed varchar(20)
        final String hsNm   = "Admin";
        TenantContext.set(hsUser, hsNm, hsBiz, LangCd.EN, TenantContext.AUTH_GR_ADMIN);

        AcctTrnsHstr bank = new AcctTrnsHstr();
        bank.setBizKey(hsBiz);
        bank.setAcctSeqNo("10");
        bank.setBankCd(BANK);
        bank.setAcctNo(ACCT);
        bank.setTrscDt("20260629");
        bank.setTrscAmt(new BigDecimal("100"));
        bank.setInoutDv("1");
        when(acctTrnsHstrRepo.findById(any())).thenReturn(Optional.of(bank));
        when(rcvPayRepo.existsByBizKeyAndAcctSeqNoAndCnfmYn(hsBiz, "10", "Y")).thenReturn(false);
        when(legacy.nextVal(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("1");

        // Bare confirm — no usage/vas/contact classification (null-safe path).
        ConfirmResponse res = service.confirm(new ConfirmRequest(BANK, ACCT, List.of(
                new ConfirmRequest.Row("10", null, null, null, null, null))));

        assertThat(res.confirmedCount()).isEqualTo(1);
        ArgumentCaptor<AcctTrnsRcvPayInfm> captor = ArgumentCaptor.forClass(AcctTrnsRcvPayInfm.class);
        verify(rcvPayRepo).save(captor.capture());
        AcctTrnsRcvPayInfm saved = captor.getValue();

        assertThat(saved.getCnfmYn()).isEqualTo("Y");
        // Legacy parity: biz_key in CNFM_BY_USER_ID — and it fits varchar(20).
        assertThat(saved.getCnfmByUserId()).isEqualTo(hsBiz);
        assertThat(saved.getCnfmByUserId()).isNotEqualTo(hsUser);
        assertThat(saved.getCnfmByUserId().length()).isLessThanOrEqualTo(20);
        // Confirm name still preserved via CNFM_BY_USER_NM.
        assertThat(saved.getCnfmByUserNm()).isEqualTo(hsNm);
        // Real (long) user id preserved in the varchar(100) audit columns.
        assertThat(saved.getRegId()).isEqualTo(hsUser);
        assertThat(saved.getModId()).isEqualTo(hsUser);
    }

    /* ---- eligibility now delegates to the repository ------------------- */

    /** The eligibility read delegates count + page to
     *  {@link SmartOrganizerEligibleRepository}; the SQL-content assertion
     *  (usage-group filter, tenant filter, parameter binding) lives in
     *  {@code SmartOrganizerEligibleRepositoryTest}. When the count is zero
     *  the service returns early and never asks for a page. */
    @Test
    void listEligible_zeroCount_returnsEmpty_noPageQuery() {
        when(eligibleRepo.countEligible(BIZ, BANK, ACCT, "20260101", "20260131", null))
                .thenReturn(0L);

        var resp = service.listEligible(BANK, ACCT, "20260101", "20260131",
                null, null, null, 1, 15);

        assertThat(resp.totalCount()).isEqualTo(0L);
        assertThat(resp.rows()).isEmpty();
        verify(eligibleRepo).countEligible(BIZ, BANK, ACCT, "20260101", "20260131", null);
        verify(eligibleRepo, never()).pageEligible(
                any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    /** When rows exist, the service pages with the whitelist-validated sort
     *  expression (default {@code h.trsc_dt} when no sort field is supplied)
     *  and maps the raw rows. */
    @Test
    void listEligible_withRows_pagesWithDefaultSortExpression() {
        when(eligibleRepo.countEligible(BIZ, BANK, ACCT, "20260101", "20260131", null))
                .thenReturn(1L);
        when(eligibleRepo.pageEligible(
                eq(BIZ), eq(BANK), eq(ACCT), eq("20260101"), eq("20260131"), org.mockito.ArgumentMatchers.isNull(),
                eq("h.trsc_dt"), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        var resp = service.listEligible(BANK, ACCT, "20260101", "20260131",
                null, null, null, 1, 15);

        assertThat(resp.totalCount()).isEqualTo(1L);
        verify(eligibleRepo).pageEligible(
                eq(BIZ), eq(BANK), eq(ACCT), eq("20260101"), eq("20260131"), org.mockito.ArgumentMatchers.isNull(),
                eq("h.trsc_dt"), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ---- fixtures ------------------------------------------------------

    private void stubBank(String inoutDv) {
        AcctTrnsHstr h = new AcctTrnsHstr();
        h.setBizKey(BIZ);
        h.setAcctSeqNo("10");
        h.setBankCd(BANK);
        h.setAcctNo(ACCT);
        h.setTrscDt("20260101");
        h.setTrscAmt(new BigDecimal("100"));
        h.setInoutDv(inoutDv);
        when(acctTrnsHstrRepo.findById(any())).thenReturn(Optional.of(h));
    }

    private static ConfirmRequest confirmReq(String useUsagCd) {
        return new ConfirmRequest(BANK, ACCT, List.of(
                new ConfirmRequest.Row("10", useUsagCd, null, null, null, null)));
    }

    private static BusiUsagSetInfm usage(String code, String grp) {
        BusiUsagSetInfm u = new BusiUsagSetInfm();
        u.setBizKey(BIZ);
        u.setUseUsagCd(code);
        u.setUsagGrpCd(grp);
        return u;
    }
}
