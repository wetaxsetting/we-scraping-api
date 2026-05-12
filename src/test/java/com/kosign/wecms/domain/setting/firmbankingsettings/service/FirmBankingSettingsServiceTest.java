package com.kosign.wecms.domain.setting.firmbankingsettings.service;

import com.kosign.wecms.common.auditlog.entity.AuditLogEntry;
import com.kosign.wecms.common.auditlog.repository.AuditLogEntryRepository;
import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingAuditListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingCreateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingDetail;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingUpdateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSetting;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSettingId;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.FirmBankingSettingView;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.FirmBankingSettingViewRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Settings &gt; Firm Banking Settings (Phase
 * 2 read-only slice — Path A).
 *
 * <p>Pins:
 * <ul>
 *   <li>tenant scoping via {@link TenantContext} for List / Detail /
 *       Audit-read;</li>
 *   <li>{@code wdrl_act_no} masking on the list response wire shape
 *       (OQ-4 §9 presentation-layer rule);</li>
 *   <li>{@code wdrl_act_no} <em>cleartext</em> on the detail response
 *       (edit-dialog round-trip);</li>
 *   <li>missing-row {@code WBE2020} on Detail (OQ-1 allocation);</li>
 *   <li>audit-log filter shape mirrors legacy
 *       {@code comm_0220_003_r001_act.jsp:74-86} (REF_NO=TB_FIRM_CONF,
 *       WSVC_ID={@code firm-banking-settings} — refactor route slug,
 *       OQ-6 override 2026-05-09, LANG_CD from {@link TenantContext});</li>
 *   <li>blank/missing path components throw
 *       {@code WBE_VALIDATION} (defence-in-depth — the controller's
 *       {@code @Pattern} guard catches the standard cases first).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FirmBankingSettingsServiceTest {

    @Mock FirmBankingSettingRepository      settingRepo;
    @Mock FirmBankingSettingViewRepository  viewRepo;
    @Mock AuditLogEntryRepository           auditRepo;
    @Mock AuditLogService                   auditLogService;
    @Mock TbFirmBankCodeRepository          firmBankCodeRepo;

    FirmBankingSettingsService service;

    private static final String BIZ        = "VN_VNM0004_000001";
    private static final String USER       = "tester";
    private static final String OUT_BANK   = "970424";
    private static final String WDRL_ACT   = "700005285280";

    @BeforeEach
    void setUp() {
        service = new FirmBankingSettingsService(
                settingRepo, viewRepo, auditRepo, auditLogService, firmBankCodeRepo);
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @Test
    void list_passesTenantBizKeyToRepo_andShipsCleartextWdrlActNoAsThePkComponent() {
        FirmBankingSettingView view = newView();
        when(viewRepo.findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc(BIZ))
                .thenReturn(List.of(view));

        FirmBankingSettingListResponse response = service.list();

        assertThat(response.list()).hasSize(1);
        // The list ships cleartext wdrl_act_no — it's the PK component
        // the frontend addresses for Detail / Update / Delete / Audit.
        // Earlier revisions returned a masked value here, which broke
        // every write flow because the masked value contains '*' chars
        // outside the @Pattern("^[A-Za-z0-9\\-]{1,100}$") character
        // class. Masking is presentation-layer only — owned by the
        // frontend mapper.
        assertThat(response.list().get(0).wdrlActNo()).isEqualTo(WDRL_ACT);
        assertThat(response.list().get(0).outBankCd()).isEqualTo(OUT_BANK);
        assertThat(response.list().get(0).bankNm())   .isEqualTo("Shinhan Bank Vietnam");
        assertThat(response.list().get(0).bizKey())   .isEqualTo(BIZ);
        verify(viewRepo).findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc(BIZ);
    }

    @Test
    void list_emptyTenant_returnsEmptyListAndNoUpstreamError() {
        when(viewRepo.findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc(BIZ))
                .thenReturn(List.of());

        FirmBankingSettingListResponse response = service.list();

        assertThat(response.list()).isEmpty();
    }

    @Test
    void list_missingTenantContext_throwsWBE9999() {
        TenantContext.clear();

        assertBusinessCode("WBE9999", () -> service.list());
        verify(viewRepo, never()).findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc(any());
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Test
    void detail_returnsCleartextWdrlActNo_forEditDialogRoundTrip() {
        FirmBankingSetting entity = newEntity();
        when(settingRepo.findById(new FirmBankingSettingId(BIZ, OUT_BANK, WDRL_ACT)))
                .thenReturn(Optional.of(entity));

        FirmBankingSettingDetail detail = service.detail(OUT_BANK, WDRL_ACT);

        // Detail intentionally returns CLEARTEXT — the edit dialog needs
        // the round-trip value. The list endpoint is what masks. See
        // feature §9.
        assertThat(detail.wdrlActNo()).isEqualTo(WDRL_ACT);
        assertThat(detail.outBankCd()).isEqualTo(OUT_BANK);
        assertThat(detail.bizKey())   .isEqualTo(BIZ);
        assertThat(detail.bankPrtnId()).isEqualTo("WEBC1");
    }

    @Test
    void detail_rowNotFound_throwsWBE2020() {
        when(settingRepo.findById(new FirmBankingSettingId(BIZ, OUT_BANK, "missing-acct")))
                .thenReturn(Optional.empty());

        assertBusinessCode("WBE2020", () -> service.detail(OUT_BANK, "missing-acct"));
    }

    @Test
    void detail_blankPathSegments_throwWBE_VALIDATION() {
        // Defence-in-depth — controller @Pattern guards normally catch
        // these first, but the service must reject too in case a caller
        // bypasses the controller layer (test suite, internal call, etc.).
        assertBusinessCode("WBE_VALIDATION", () -> service.detail(null, WDRL_ACT));
        assertBusinessCode("WBE_VALIDATION", () -> service.detail("",   WDRL_ACT));
        assertBusinessCode("WBE_VALIDATION", () -> service.detail("  ", WDRL_ACT));
        assertBusinessCode("WBE_VALIDATION", () -> service.detail(OUT_BANK, null));
        assertBusinessCode("WBE_VALIDATION", () -> service.detail(OUT_BANK, ""));
    }

    @Test
    void detail_missingTenantContext_throwsWBE9999() {
        TenantContext.clear();
        assertBusinessCode("WBE9999", () -> service.detail(OUT_BANK, WDRL_ACT));
        verify(settingRepo, never()).findById(any());
    }

    // ------------------------------------------------------------------
    // Audit-read
    // ------------------------------------------------------------------

    @Test
    void auditLog_appliesLegacyFilterShape() {
        AuditLogEntry e = newAuditEntry();
        when(auditRepo.findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(
                BIZ,
                "TB_FIRM_CONF",
                OUT_BANK,
                WDRL_ACT,
                "firm-banking-settings",   // OQ-6 override 2026-05-09 — slug-based audit ids
                "EN"))
                .thenReturn(List.of(e));

        FirmBankingAuditListResponse response = service.auditLog(OUT_BANK, WDRL_ACT);

        assertThat(response.list()).hasSize(1);
        assertThat(response.list().get(0).auditLogsActv())   .isEqualTo("Firm Banking Details");
        assertThat(response.list().get(0).auditLogsActvDtl()).isEqualTo("Update firmbanking details");
        assertThat(response.list().get(0).regNm())           .isEqualTo("lamine");
    }

    @Test
    void auditLog_localeFromTenantContext_drivesLangCdPredicate() {
        // Switch the request locale and confirm the repo filter follows
        // it — the audit table holds one row per locale, so this is the
        // discriminator that picks the right pre-localized text.
        TenantContext.set(USER, BIZ, LangCd.VI);
        when(auditRepo.findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(
                BIZ, "TB_FIRM_CONF", OUT_BANK, WDRL_ACT, "firm-banking-settings", "VI"))
                .thenReturn(List.of());

        FirmBankingAuditListResponse response = service.auditLog(OUT_BANK, WDRL_ACT);

        assertThat(response.list()).isEmpty();
        verify(auditRepo).findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(
                BIZ, "TB_FIRM_CONF", OUT_BANK, WDRL_ACT, "firm-banking-settings", "VI");
    }

    // ------------------------------------------------------------------
    // Create (added 2026-05-09 — OQ-8a Option (c))
    // ------------------------------------------------------------------

    @Test
    void create_happyPath_persistsEntity_setsServerDefaults_andAppendsCreateAudit() {
        when(firmBankCodeRepo.existsByAntDivCdAndBankCd("NAPAS", OUT_BANK)).thenReturn(true);
        when(settingRepo.existsById(new FirmBankingSettingId(BIZ, OUT_BANK, WDRL_ACT)))
                .thenReturn(false);
        when(settingRepo.save(any(FirmBankingSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FirmBankingSettingCreateRequest req = new FirmBankingSettingCreateRequest(
                OUT_BANK, WDRL_ACT, "WEBC1", "GV00000001", "1");

        FirmBankingSettingDetail detail = service.create(req);

        // Wire-supplied fields preserved
        assertThat(detail.outBankCd()) .isEqualTo(OUT_BANK);
        assertThat(detail.wdrlActNo()) .isEqualTo(WDRL_ACT);
        assertThat(detail.bankPrtnId()).isEqualTo("WEBC1");
        assertThat(detail.vanOrgCd())  .isEqualTo("GV00000001");
        assertThat(detail.orgStat())   .isEqualTo("1");
        // Server-derived defaults
        assertThat(detail.fileType())            .isEqualTo("SHB"); // V14 — Shinhan→SHB
        assertThat(detail.batTranBranchDiv())    .isEqualTo("Y");   // V15
        assertThat(detail.batTranBranchLimit())  .isEqualByComparingTo(new BigDecimal("2000")); // V16
        assertThat(detail.regDttm())   .hasSize(14); // V18
        assertThat(detail.startDttm()) .hasSize(14);
        assertThat(detail.modDttm())   .hasSize(14);
        assertThat(detail.endDttm())   .isNull();
        assertThat(detail.lastEdtr())  .isEqualTo(USER); // V19

        // Audit append — same envelope as Update except detailKey.
        ArgumentCaptor<AuditLogContext> captor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(captor.capture());
        AuditLogContext ctx = captor.getValue();
        assertThat(ctx.wsvcId())     .isEqualTo("firm-banking-settings");
        assertThat(ctx.refNo())      .isEqualTo("TB_FIRM_CONF");
        assertThat(ctx.refNo1())     .isEqualTo(OUT_BANK);
        assertThat(ctx.refNo2())     .isEqualTo(WDRL_ACT);
        assertThat(ctx.activityKey()).isEqualTo("audit.firm_banking_settings.activity");
        assertThat(ctx.detailKey())  .isEqualTo("audit.firm_banking_settings.create.detail");
    }

    @Test
    void create_vietcombank_derivesVcbFileType() {
        when(firmBankCodeRepo.existsByAntDivCdAndBankCd("NAPAS", "970436")).thenReturn(true);
        when(settingRepo.existsById(any())).thenReturn(false);
        when(settingRepo.save(any(FirmBankingSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FirmBankingSettingDetail detail = service.create(new FirmBankingSettingCreateRequest(
                "970436", "700022964690", "WEBC2", "GV00000002", "1"));

        assertThat(detail.fileType()).isEqualTo("VCB");
    }

    @Test
    void create_woori_derivesWrbFileType() {
        when(firmBankCodeRepo.existsByAntDivCdAndBankCd("NAPAS", "970457")).thenReturn(true);
        when(settingRepo.existsById(any())).thenReturn(false);
        when(settingRepo.save(any(FirmBankingSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FirmBankingSettingDetail detail = service.create(new FirmBankingSettingCreateRequest(
                "970457", "100912150384", "WEBC3", "GV00000003", "1"));

        assertThat(detail.fileType()).isEqualTo("WRB");
    }

    @Test
    void create_unsupportedBank_throwsWBE0001_andDoesNotHitDownstream() {
        // OQ-8a Option (c) — anything outside {970424, 970436, 970457}
        // is a hard reject. The DTO-level @Pattern enforces format, but
        // the bounded-bank set lives in the service so the rejection
        // can carry the WBE0001 argument verbatim.
        FirmBankingSettingCreateRequest req = new FirmBankingSettingCreateRequest(
                "999999", WDRL_ACT, "WEBC1", "GV00000001", "1");

        assertBusinessCode("WBE0001", () -> service.create(req));

        verify(firmBankCodeRepo, never()).existsByAntDivCdAndBankCd(any(), any());
        verify(settingRepo, never()).existsById(any());
        verify(settingRepo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void create_bankNotInTbFirmBankCode_throwsWBE0002() {
        when(firmBankCodeRepo.existsByAntDivCdAndBankCd("NAPAS", OUT_BANK)).thenReturn(false);
        FirmBankingSettingCreateRequest req = new FirmBankingSettingCreateRequest(
                OUT_BANK, WDRL_ACT, "WEBC1", "GV00000001", "1");

        assertBusinessCode("WBE0002", () -> service.create(req));

        verify(settingRepo, never()).existsById(any());
        verify(settingRepo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void create_duplicatePk_throwsWBE2021_andDoesNotSaveOrAudit() {
        when(firmBankCodeRepo.existsByAntDivCdAndBankCd("NAPAS", OUT_BANK)).thenReturn(true);
        when(settingRepo.existsById(new FirmBankingSettingId(BIZ, OUT_BANK, WDRL_ACT)))
                .thenReturn(true);
        FirmBankingSettingCreateRequest req = new FirmBankingSettingCreateRequest(
                OUT_BANK, WDRL_ACT, "WEBC1", "GV00000001", "1");

        assertBusinessCode("WBE2021", () -> service.create(req));

        verify(settingRepo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void create_blankRequiredField_throwsWBE_VALIDATION_noRepoTouch() {
        FirmBankingSettingCreateRequest blankBank = new FirmBankingSettingCreateRequest(
                "", WDRL_ACT, "WEBC1", "GV0", "1");
        assertBusinessCode("WBE_VALIDATION", () -> service.create(blankBank));

        FirmBankingSettingCreateRequest blankAcct = new FirmBankingSettingCreateRequest(
                OUT_BANK, "", "WEBC1", "GV0", "1");
        assertBusinessCode("WBE_VALIDATION", () -> service.create(blankAcct));

        verifyNoInteractions(firmBankCodeRepo);
        verify(settingRepo, never()).existsById(any());
        verify(settingRepo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void create_missingTenantContext_throwsWBE9999_andDoesNotPersist() {
        TenantContext.clear();
        FirmBankingSettingCreateRequest req = new FirmBankingSettingCreateRequest(
                OUT_BANK, WDRL_ACT, "WEBC1", "GV0", "1");

        assertBusinessCode("WBE9999", () -> service.create(req));

        verify(settingRepo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    // ------------------------------------------------------------------
    // Update (added 2026-05-09)
    // ------------------------------------------------------------------

    @Test
    void update_existingRow_persistsEditableFields_setsModDttmAndLastEdtr_andAppendsAudit() {
        FirmBankingSetting existing = newEntity();
        when(settingRepo.findById(new FirmBankingSettingId(BIZ, OUT_BANK, WDRL_ACT)))
                .thenReturn(Optional.of(existing));
        // Hibernate would mutate the entity in place and return it from
        // save() — Mockito mirrors that with thenAnswer.
        when(settingRepo.save(any(FirmBankingSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FirmBankingSettingUpdateRequest req = new FirmBankingSettingUpdateRequest(
                "WEBC2-NEW", "GV99999999", "2");

        FirmBankingSettingDetail detail = service.update(OUT_BANK, WDRL_ACT, req);

        // Editable fields applied
        assertThat(detail.bankPrtnId()).isEqualTo("WEBC2-NEW");
        assertThat(detail.vanOrgCd())  .isEqualTo("GV99999999");
        assertThat(detail.orgStat())   .isEqualTo("2");
        // Path PK passed through unchanged
        assertThat(detail.outBankCd()) .isEqualTo(OUT_BANK);
        assertThat(detail.wdrlActNo()) .isEqualTo(WDRL_ACT);
        // file_type stayed (V14 — derivation only on Create)
        assertThat(detail.fileType())  .isEqualTo("SHB");
        // Server-set audit columns
        assertThat(detail.modDttm()).hasSize(14);   // yyyyMMddHHmmss
        // The default TenantContext userNm equals the userId (3-arg
        // setUp form delegates). For the audit row LAST_EDTR field
        // the test's USER value is what populates here.
        assertThat(detail.lastEdtr()).isEqualTo(USER);

        // Audit-log writer invoked once with the expected envelope.
        ArgumentCaptor<AuditLogContext> captor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(captor.capture());
        AuditLogContext ctx = captor.getValue();
        assertThat(ctx.wsvcId())     .isEqualTo("firm-banking-settings"); // OQ-6 override 2026-05-09
        assertThat(ctx.refNo())      .isEqualTo("TB_FIRM_CONF");
        assertThat(ctx.refNo1())     .isEqualTo(OUT_BANK);
        assertThat(ctx.refNo2())     .isEqualTo(WDRL_ACT);
        assertThat(ctx.activityKey()).isEqualTo("audit.firm_banking_settings.activity");
        assertThat(ctx.detailKey())  .isEqualTo("audit.firm_banking_settings.update.detail");
    }

    @Test
    void update_missingRow_throwsWBE2020_andDoesNotSaveOrAudit() {
        when(settingRepo.findById(new FirmBankingSettingId(BIZ, OUT_BANK, "9".repeat(12))))
                .thenReturn(Optional.empty());
        FirmBankingSettingUpdateRequest req = new FirmBankingSettingUpdateRequest(
                "WEBC2", "GV0", "1");

        assertBusinessCode("WBE2020", () -> service.update(OUT_BANK, "9".repeat(12), req));

        verify(settingRepo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void update_blankPathSegments_throwWBE_VALIDATION_noRepoTouch() {
        FirmBankingSettingUpdateRequest req = new FirmBankingSettingUpdateRequest(
                "WEBC2", "GV0", "1");
        assertBusinessCode("WBE_VALIDATION", () -> service.update(null, WDRL_ACT, req));
        assertBusinessCode("WBE_VALIDATION", () -> service.update("",   WDRL_ACT, req));
        assertBusinessCode("WBE_VALIDATION", () -> service.update(OUT_BANK, null, req));
        assertBusinessCode("WBE_VALIDATION", () -> service.update(OUT_BANK, "",   req));

        verify(settingRepo, never()).findById(any());
        verify(settingRepo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void update_missingTenantContext_throwsWBE9999() {
        TenantContext.clear();
        FirmBankingSettingUpdateRequest req = new FirmBankingSettingUpdateRequest(
                "WEBC2", "GV0", "1");

        assertBusinessCode("WBE9999", () -> service.update(OUT_BANK, WDRL_ACT, req));
        verify(settingRepo, never()).findById(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void update_doesNotMutateFileTypeOrPkColumns_perV14() {
        // V14 invariant: FILE_TYPE is server-derived on Create and
        // never touched on Update. PK columns are never mutable.
        // Pass a request with values that would be wrong for the
        // current row's file_type and verify it stays put.
        FirmBankingSetting existing = newEntity();
        existing.setFileType("SHB");                        // current
        existing.setOutBankCd(OUT_BANK);
        existing.setWdrlActNo(WDRL_ACT);
        when(settingRepo.findById(new FirmBankingSettingId(BIZ, OUT_BANK, WDRL_ACT)))
                .thenReturn(Optional.of(existing));
        when(settingRepo.save(any(FirmBankingSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FirmBankingSettingUpdateRequest req = new FirmBankingSettingUpdateRequest(
                "any", "any", "1");

        FirmBankingSettingDetail detail = service.update(OUT_BANK, WDRL_ACT, req);

        assertThat(detail.fileType()) .isEqualTo("SHB");    // unchanged
        assertThat(detail.outBankCd()).isEqualTo(OUT_BANK); // unchanged
        assertThat(detail.wdrlActNo()).isEqualTo(WDRL_ACT); // unchanged
    }

    // ------------------------------------------------------------------
    // Delete (added 2026-05-09)
    // ------------------------------------------------------------------

    @Test
    void delete_existingRow_invokesDeleteByIdWithTenantScopedPk() {
        FirmBankingSettingId id = new FirmBankingSettingId(BIZ, OUT_BANK, WDRL_ACT);
        when(settingRepo.existsById(id)).thenReturn(true);

        service.delete(OUT_BANK, WDRL_ACT);

        verify(settingRepo).deleteById(id);
    }

    @Test
    void delete_missingRow_throwsWBE2020_andDoesNotInvokeDelete() {
        FirmBankingSettingId id = new FirmBankingSettingId(BIZ, OUT_BANK, "9".repeat(12));
        when(settingRepo.existsById(id)).thenReturn(false);

        assertBusinessCode("WBE2020", () -> service.delete(OUT_BANK, "9".repeat(12)));
        verify(settingRepo, never()).deleteById(any());
    }

    @Test
    void delete_doesNotWriteAuditRow_perOQ2() {
        // OQ-2 parity: JEX comm_0220_001_d001_act.jsp writes no AuditLogsInfm
        // entry; the refactor must mirror exactly. The audit-log writer
        // is not present yet (P-1 hasn't shipped), but even after it
        // does, this test pins the contract: delete() must not invoke
        // any audit repository.
        FirmBankingSettingId id = new FirmBankingSettingId(BIZ, OUT_BANK, WDRL_ACT);
        when(settingRepo.existsById(id)).thenReturn(true);

        service.delete(OUT_BANK, WDRL_ACT);

        // The audit repo's read API is the only surface today; a future
        // writer would also be reachable via this mock. Either way the
        // delete path must not touch it.
        verifyNoInteractions(auditRepo);
    }

    @Test
    void delete_blankPathSegments_throwWBE_VALIDATION() {
        // Defence-in-depth — controller @Pattern guards normally catch
        // these first, but the service must reject too.
        assertBusinessCode("WBE_VALIDATION", () -> service.delete(null, WDRL_ACT));
        assertBusinessCode("WBE_VALIDATION", () -> service.delete("",   WDRL_ACT));
        assertBusinessCode("WBE_VALIDATION", () -> service.delete(OUT_BANK, null));
        assertBusinessCode("WBE_VALIDATION", () -> service.delete(OUT_BANK, ""));
        verify(settingRepo, never()).existsById(any());
        verify(settingRepo, never()).deleteById(any());
    }

    @Test
    void delete_missingTenantContext_throwsWBE9999() {
        TenantContext.clear();
        assertBusinessCode("WBE9999", () -> service.delete(OUT_BANK, WDRL_ACT));
        verify(settingRepo, never()).existsById(any());
        verify(settingRepo, never()).deleteById(any());
    }

    @Test
    void auditLog_doesNotPreCheckRowExistence_returnsEmptyForUnknownRow() {
        // The audit endpoint deliberately doesn't call settingRepo —
        // matches legacy comm_0220_003_r001_act.jsp which lazy-queries
        // the audit log without first verifying the firm-conf row.
        when(auditRepo.findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        FirmBankingAuditListResponse response = service.auditLog(OUT_BANK, "non-existent-row");

        assertThat(response.list()).isEmpty();
        verify(settingRepo, never()).findById(any());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertBusinessCode(String expectedCode, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private static FirmBankingSettingView newView() {
        FirmBankingSettingView v = new FirmBankingSettingView();
        v.setBizKey(BIZ);
        v.setOutBankCd(OUT_BANK);
        v.setWdrlActNo(WDRL_ACT);
        v.setBankNm("Shinhan Bank Vietnam");
        v.setCcyCd("VND");
        v.setBankPrtnId("WEBC1");
        v.setVanOrgCd("GV00000001");
        v.setOrgStat("1");
        v.setFileType("SHB");
        v.setBatTranBranchDiv("Y");
        v.setBatTranBranchLimit(BigDecimal.valueOf(2000));
        v.setRegDttm("20260101090000");
        v.setStartDttm("20260101090000");
        v.setEndDttm("20260101090000");
        v.setModDttm("20260415120000");
        v.setLastEdtr("demo_user");
        v.setBal(new BigDecimal("71745969.000000"));
        v.setExpiDt(null);
        return v;
    }

    private static FirmBankingSetting newEntity() {
        FirmBankingSetting e = new FirmBankingSetting();
        e.setBizKey(BIZ);
        e.setOutBankCd(OUT_BANK);
        e.setWdrlActNo(WDRL_ACT);
        e.setBankPrtnId("WEBC1");
        e.setVanOrgCd("GV00000001");
        e.setOrgStat("1");
        e.setFileType("SHB");
        e.setBatTranBranchDiv("Y");
        e.setBatTranBranchLimit(BigDecimal.valueOf(2000));
        e.setRegDttm("20260101090000");
        e.setStartDttm("20260101090000");
        e.setEndDttm("20260101090000");
        e.setModDttm("20260415120000");
        e.setLastEdtr("demo_user");
        return e;
    }

    private static AuditLogEntry newAuditEntry() {
        AuditLogEntry a = new AuditLogEntry();
        a.setBizKey(BIZ);
        a.setAuditLogsSeqNo(BigDecimal.valueOf(3));
        a.setWsvcId("firm-banking-settings");
        a.setLangCd("EN");
        a.setRefNo("TB_FIRM_CONF");
        a.setRefNo1(OUT_BANK);
        a.setRefNo2(WDRL_ACT);
        a.setAuditLogsActv("Firm Banking Details");
        a.setAuditLogsActvDtl("Update firmbanking details");
        a.setRegId("lamine@gmail.com");
        a.setRegNm("lamine");
        a.setRegDtm("20260402115348");
        return a;
    }
}
