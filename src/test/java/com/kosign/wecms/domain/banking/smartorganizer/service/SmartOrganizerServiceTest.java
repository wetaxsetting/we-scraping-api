package com.kosign.wecms.domain.banking.smartorganizer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.banking.service.AccountAccessGuard;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmRequest;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmResponse;
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

    SmartOrganizerService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";
    private static final String BANK = "11704088";
    private static final String ACCT = "5280123456";

    @BeforeEach
    void setUp() {
        service = new SmartOrganizerService(
                acctTrnsHstrRepo, rcvPayRepo, legacy, accountAccessGuard,
                vasRepo, trcoRepo, purposeRepo, chartRepo, acctMstrRepo, bankCdResolver);
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

    /* ---- eligibility SQL carries the usage-group direction filter ------ */

    /** The generated count/select SQL must include the legacy getDynamic1
     *  group↔direction filter, gated on a non-null USE_USAG_CD (Option B). */
    @Test
    void listEligible_sqlAppliesUsageGroupDirectionFilter() {
        ReflectionTestUtils.setField(service, "em", em());
        // total == 0 returns early, so only the COUNT query is built.

        service.listEligible(BANK, ACCT, "20260101", "20260131",
                null, null, null, 1, 15);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(capturedEm).createNativeQuery(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("busi_usag_set_infm");
        assertThat(sql).contains("u.usag_grp_cd = CASE WHEN h.inout_dv = '1' THEN '4' ELSE '6' END");
        assertThat(sql).contains("r.use_usag_cd IS NULL OR EXISTS");
    }

    // ---- fixtures ------------------------------------------------------

    private EntityManager capturedEm;

    private EntityManager em() {
        capturedEm = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(capturedEm.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(0L);
        return capturedEm;
    }

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
