package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprLine;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetl;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprLineRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolDetlRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 1 legacy-parity coverage for {@link ApprovalBoxFanoutService}:
 * <ul>
 *   <li>F-1 — {@code writeResolutionDetail} inserts one {@code EXPT_RESOL_DETL}
 *       row (DOC_ID / AMT / CCY_CD) with a funnel-allocated APPR_DETL_SEQ_NO.</li>
 *   <li>F-2 — {@code writeApprovalLineSnapshot} inserts the env-gated
 *       {@code EXPT_RESOL_APPR_LINE} print-header snapshot; no-op when the
 *       tenant has {@code APPR_BOX_USE_YN != 'Y'}.</li>
 *   <li>F-4 — {@code fanout} stamps {@code APPR_STS = "2"} (In Progress).</li>
 * </ul>
 * Pure Mockito; the clock is a fixed UTC clock so the timestamps are
 * deterministic.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalBoxFanoutServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private ExptResolMstrRepository        mstrRepository;
    @Mock private ExptResolApprvrInfmRepository  approverRepository;
    @Mock private ApprovalLineApproverRepository apprLineApproverRepository;
    @Mock private ExptResolDetlRepository        detlRepository;
    @Mock private ExptResolApprLineRepository    apprLineRepository;
    @Mock private BusiEnvSetInfmRepository       busiEnvSetInfmRepository;
    @Mock private LegacyFunctionRepository       legacyFunctions;
    @Mock private com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService
                                                 approvalNotificationService;

    private ApprovalBoxFanoutService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC);
        service = new ApprovalBoxFanoutService(
                mstrRepository, approverRepository, apprLineApproverRepository,
                detlRepository, apprLineRepository, busiEnvSetInfmRepository,
                clock, legacyFunctions, approvalNotificationService,
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
        TenantContext.set(USER, TENANT, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ---- F-1: EXPT_RESOL_DETL --------------------------------------- */

    @Test
    void writeResolutionDetail_inserts_single_row_with_funnel_seq() {
        when(legacyFunctions.nextVal("expt_resol_detl", "appr_detl_seq_no",
                TENANT, null, null, null, null, null)).thenReturn("7");

        service.writeResolutionDetail("100", "SC1", new BigDecimal("12345.00"), "VND");

        ArgumentCaptor<ExptResolDetl> captor = ArgumentCaptor.forClass(ExptResolDetl.class);
        verify(detlRepository).save(captor.capture());
        ExptResolDetl d = captor.getValue();
        assertThat(d.getBizKey()).isEqualTo(TENANT);
        assertThat(d.getApprSeqNo()).isEqualTo("100");
        assertThat(d.getApprDetlSeqNo()).isEqualByComparingTo(new BigDecimal("7"));
        assertThat(d.getDocId()).isEqualTo("SC1");
        assertThat(d.getAmt()).isEqualByComparingTo(new BigDecimal("12345.00"));
        assertThat(d.getCcyCd()).isEqualTo("VND");
        assertThat(d.getRegId()).isEqualTo(USER);
        assertThat(d.getRegDtm()).isEqualTo("20260612030000");
    }

    @Test
    void writeResolutionDetail_defaults_blank_currency_to_vnd_and_null_amount_to_zero() {
        when(legacyFunctions.nextVal(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("1");

        service.writeResolutionDetail("100", "SC1", null, "  ");

        ArgumentCaptor<ExptResolDetl> captor = ArgumentCaptor.forClass(ExptResolDetl.class);
        verify(detlRepository).save(captor.capture());
        assertThat(captor.getValue().getCcyCd()).isEqualTo("VND");
        assertThat(captor.getValue().getAmt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /* ---- F-2: EXPT_RESOL_APPR_LINE ---------------------------------- */

    @Test
    void writeApprovalLineSnapshot_inserts_env_gated_rows() {
        env("00014", "Y");      // APPR_BOX_USE_YN
        env("00015", "2");      // APPR_BOX_DISP_CNT
        env("00029", "Manager");
        env("00034", "Alice");
        env("00030", "Director");
        env("00035", "Bob");

        service.writeApprovalLineSnapshot("100");

        ArgumentCaptor<ExptResolApprLine> captor = ArgumentCaptor.forClass(ExptResolApprLine.class);
        verify(apprLineRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<ExptResolApprLine> rows = captor.getAllValues();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getApprLineNo()).isEqualTo("1");
        assertThat(rows.get(0).getJbclNm()).isEqualTo("Manager");
        assertThat(rows.get(0).getEmplNm()).isEqualTo("Alice");
        assertThat(rows.get(1).getApprLineNo()).isEqualTo("2");
        assertThat(rows.get(1).getJbclNm()).isEqualTo("Director");
        assertThat(rows.get(1).getEmplNm()).isEqualTo("Bob");
        assertThat(rows.get(0).getRegId()).isEqualTo(USER);
        assertThat(rows.get(0).getRegDtm()).isEqualTo("20260612030000");
    }

    @Test
    void writeApprovalLineSnapshot_noop_when_appr_box_disabled() {
        env("00014", "N");      // print form off → legacy creates no rows

        service.writeApprovalLineSnapshot("100");

        verify(apprLineRepository, never()).save(any());
    }

    @Test
    void writeApprovalLineSnapshot_noop_when_use_flag_unseeded() {
        // No env rows stubbed at all → findById returns empty → no-op
        service.writeApprovalLineSnapshot("100");

        verify(apprLineRepository, never()).save(any());
    }

    /* ---- F-4: APPR_STS = "2" on fanout ------------------------------ */

    @Test
    void fanout_stamps_appr_sts_in_progress() {
        when(legacyFunctions.nextDocNo(TENANT)).thenReturn("VO_260612_001");

        service.fanout(
                ApprDv.CORPORATE_CARD, "100", "Title", "VO_260612_001",
                new BigDecimal("500.00"), "VND", null, null,
                List.of(new ApprovalBoxFanoutService.Approver("u1", "User One")),
                4321L);

        ArgumentCaptor<ExptResolMstr> captor = ArgumentCaptor.forClass(ExptResolMstr.class);
        verify(mstrRepository).save(captor.capture());
        assertThat(captor.getValue().getApprSts()).isEqualTo("2");
    }

    /* ---- fixtures --------------------------------------------------- */

    private void env(String key, String value) {
        BusiEnvSetInfm row = new BusiEnvSetInfm();
        row.setBizKey(TENANT);
        row.setEnvSetInf(key);
        row.setEnvSetCont(value);
        when(busiEnvSetInfmRepository.findById(new BusiEnvSetInfmId(TENANT, key)))
                .thenReturn(Optional.of(row));
    }
}
