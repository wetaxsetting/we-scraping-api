package com.kosign.wecms.domain.banking.smartorganizer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleRow;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleSaveRequest;
import com.kosign.wecms.domain.banking.smartorganizer.entity.RmrkOrgInfm;
import com.kosign.wecms.domain.banking.smartorganizer.repository.RmrkOrgInfmRepository;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetInfm;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SO-RULE — Smart Organizer rule {@code RCV_PAY_DV} derivation parity.
 *
 * <p>Legacy {@code fnsh_0200_002_c001_act.jsp:103-110} derives a rule's
 * {@code RCV_PAY_DV} from the chosen usage's {@code USAG_GRP_CD}
 * (group {@code '4'} → {@code RCV}, group {@code '6'} → {@code PAY}) and
 * never trusts the client value for grouped-usage rules. These tests pin
 * the restored behavior in {@link OrganizerRuleService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizerRuleServiceTest {

    @Mock RmrkOrgInfmRepository      repo;
    @Mock LegacyFunctionRepository   legacy;
    @Mock PurposeSettingsRepository  purposeRepo;

    OrganizerRuleService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester";

    @BeforeEach
    void setUp() {
        service = new OrganizerRuleService(repo, legacy, purposeRepo);
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Create — a group-'4' usage derives RCV, overriding the client's "PAY". */
    @Test
    void create_group4Usage_derivesRcv_ignoringClientValue() {
        when(legacy.nextRmrkSeqNo(BIZ)).thenReturn("100");
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "4000000300"))
                .thenReturn(Optional.of(usage("4000000300", "4")));

        OrganizerRuleRow row = service.create(
                groupedReq("salary", "C", "PAY", "4000000300"));

        assertThat(row.rcvPayDv()).isEqualTo("RCV");
    }

    /** Create — a group-'6' usage derives PAY, overriding the client's "RCV". */
    @Test
    void create_group6Usage_derivesPay_ignoringClientValue() {
        when(legacy.nextRmrkSeqNo(BIZ)).thenReturn("101");
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "6000000300"))
                .thenReturn(Optional.of(usage("6000000300", "6")));

        OrganizerRuleRow row = service.create(
                groupedReq("rent", "C", "RCV", "6000000300"));

        assertThat(row.rcvPayDv()).isEqualTo("PAY");
    }

    /** Update — derivation also applies on the update path. */
    @Test
    void update_group6Usage_derivesPay() {
        RmrkOrgInfm existing = new RmrkOrgInfm();
        existing.setBizKey(BIZ);
        existing.setRmrkSeqNo(100L);
        existing.setDispOrd(1);
        when(repo.findByBizKeyAndRmrkSeqNo(BIZ, 100L)).thenReturn(Optional.of(existing));
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "6000000301"))
                .thenReturn(Optional.of(usage("6000000301", "6")));

        OrganizerRuleRow row = service.update(100L,
                groupedReq("utilities", "C", "RCV", "6000000301"));

        assertThat(row.rcvPayDv()).isEqualTo("PAY");
    }

    /** VAS-only rule (no usage) keeps the client-supplied RCV_PAY_DV and
     *  never touches the usage table. */
    @Test
    void create_vasOnlyRule_keepsClientValue() {
        when(legacy.nextRmrkSeqNo(BIZ)).thenReturn("102");

        OrganizerRuleRow row = service.create(new OrganizerRuleSaveRequest(
                "deposit", "C", "RCV", 1, null, null, 55L, null, null));

        assertThat(row.rcvPayDv()).isEqualTo("RCV");
        verifyNoInteractions(purposeRepo);
    }

    /** A grouped-usage rule pointing at a missing usage is rejected. */
    @Test
    void create_usageNotFound_throws() {
        when(legacy.nextRmrkSeqNo(BIZ)).thenReturn("103");
        when(purposeRepo.findByBizKeyAndUseUsagCd(BIZ, "9999999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                groupedReq("x", "C", "RCV", "9999999999")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("WBE_SMART_ORGANIZER_USAGE_NOT_FOUND");
    }

    // ---- fixtures ------------------------------------------------------

    /** Grouped-usage rule request (dispOrd pinned to skip the MAX+1 path). */
    private static OrganizerRuleSaveRequest groupedReq(
            String rmrk, String matchType, String clientRcvPayDv, String useUsagCd) {
        return new OrganizerRuleSaveRequest(
                rmrk, matchType, clientRcvPayDv, 1, null, useUsagCd, null, null, null);
    }

    private static BusiUsagSetInfm usage(String code, String grp) {
        BusiUsagSetInfm u = new BusiUsagSetInfm();
        u.setBizKey(BIZ);
        u.setUseUsagCd(code);
        u.setUsagGrpCd(grp);
        return u;
    }
}
