package com.kosign.wecms.domain.setting.purposesettings.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeReorderItem;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeReorderRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingCreateRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingDetail;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingListResponse;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingRow;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingUpdateRequest;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetInfm;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeInUseRepository;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Settings &gt; Purpose. Pins:
 *
 * <ul>
 *   <li>tenant scoping via {@link TenantContext} on every operation;</li>
 *   <li>OQ-8 — legacy codes reused verbatim: {@code WBW1152} (dup
 *       hand code), {@code WBW1138} (dup name), {@code WBE1199}
 *       (delete blocked — both children AND OQ-5 transaction-reference
 *       cases under one wire code);</li>
 *   <li>OQ-5 — recursive delete pre-check: parent blocked when child
 *       is in use;</li>
 *   <li>OQ-3 — PK generation via {@code wb_get_next_val}; one-shot
 *       retry on PK collision;</li>
 *   <li>OQ-4 — Update path does NOT duplicate-check
 *       {@code hand_use_usag_cd} (legacy parity);</li>
 *   <li>parent / self-parent / demote validation map to
 *       {@code WBE_VALIDATION};</li>
 *   <li>tree assembly nests level-2 rows under their level-1 parent;</li>
 *   <li>audit-log writes go through the shared {@link AuditLogService}
 *       with {@code wsvc_id = "purpose-settings"};</li>
 *   <li>reorder rejects duplicate codes / duplicate {@code disp_ord} /
 *       unknown codes with {@code WBE_VALIDATION};</li>
 *   <li>upload tests live in PurposeExcelServiceTest.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PurposeSettingsServiceTest {

    @Mock PurposeSettingsRepository repo;
    @Mock PurposeInUseRepository    inUseRepo;
    @Mock AuditLogService           auditLogService;
    @Mock LegacyFunctionRepository  legacyFunctions;

    PurposeSettingsService service;

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String USER     = "tester";
    private static final String CODE_A   = "0000000001";
    private static final String CODE_B   = "0000000005";
    private static final String HAND_A   = "P01";
    private static final String NAME_A   = "Deposit";

    @BeforeEach
    void setUp() {
        service = new PurposeSettingsService(repo, inUseRepo, auditLogService, legacyFunctions);
        TenantContext.set(USER, USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @Test
    void list_assemblesLevel2RowsUnderParents() {
        BusiUsagSetInfm parent = level1(CODE_A, "BD01", "Business Dev");
        BusiUsagSetInfm child  = level2(CODE_B, "BD01-01", "Sales", CODE_A);
        when(repo.findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, ""))
                .thenReturn(List.of(parent, child));
        when(inUseRepo.findInUseCodes(eq(BIZ), any())).thenReturn(Set.of(CODE_A));

        PurposeSettingListResponse out = service.list(null);

        assertThat(out.list()).hasSize(1);
        PurposeSettingRow top = out.list().get(0);
        assertThat(top.useUsagCd()).isEqualTo(CODE_A);
        assertThat(top.usedInTransactions()).isTrue();
        assertThat(top.children()).hasSize(1);
        assertThat(top.children().get(0).useUsagCd()).isEqualTo(CODE_B);
        assertThat(top.children().get(0).usedInTransactions()).isFalse();
    }

    @Test
    void list_appliesKeywordTrim() {
        when(repo.findActiveForTenant(eq(BIZ), eq(PurposeSettingsService.RESERVED_SYSTEM_CODES),
                eq("Sale"))).thenReturn(List.of());
        // Empty rows ⇒ resolveInUseCodes short-circuits and never calls
        // inUseRepo, so no need to stub findInUseCodes here.

        service.list("   Sale   ");

        verify(repo).findActiveForTenant(BIZ, PurposeSettingsService.RESERVED_SYSTEM_CODES, "Sale");
    }

    @Test
    void list_missingTenant_throwsWBE9999() {
        TenantContext.clear();
        assertBusinessCode("WBE9999", () -> service.list(null));
        verify(repo, never()).findActiveForTenant(anyString(), any(), anyString());
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Test
    void detail_returnsRow_withUsedInTransactionsFromView() {
        BusiUsagSetInfm entity = level1(CODE_A, HAND_A, NAME_A);
        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(entity));
        when(inUseRepo.findInUseCodes(eq(BIZ), any())).thenReturn(Set.of(CODE_A));

        PurposeSettingDetail detail = service.detail(CODE_A);

        assertThat(detail.useUsagCd()).isEqualTo(CODE_A);
        assertThat(detail.handUseUsagCd()).isEqualTo(HAND_A);
        assertThat(detail.usedInTransactions()).isTrue();
    }

    @Test
    void detail_rowMissing_throwsWBE_VALIDATION() {
        when(repo.findByBizKeyAndUseUsagCd(BIZ, "missing")).thenReturn(Optional.empty());
        assertBusinessCode("WBE_VALIDATION", () -> service.detail("missing"));
    }

    @Test
    void detail_blankPath_throwsWBE_VALIDATION() {
        assertBusinessCode("WBE_VALIDATION", () -> service.detail(null));
        assertBusinessCode("WBE_VALIDATION", () -> service.detail(""));
        assertBusinessCode("WBE_VALIDATION", () -> service.detail("  "));
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Test
    void create_persistsRow_andAppendsAuditWithSlugWsvcId() {
        PurposeSettingCreateRequest req = new PurposeSettingCreateRequest(
                "BD02", "Operating Expenses", "Day to day spend", null, "Y");

        when(repo.findActiveByHandCd(BIZ, "BD02")).thenReturn(Optional.empty());
        when(repo.findActiveByName(BIZ, "Operating Expenses")).thenReturn(Optional.empty());
        when(repo.findMaxDispOrd(BIZ)).thenReturn(Optional.of(192));
        when(legacyFunctions.nextUseUsagCd(BIZ, "")).thenReturn("0000000010");
        when(repo.saveAndFlush(any(BusiUsagSetInfm.class))).thenAnswer(inv -> inv.getArgument(0));
        // Create's post-write echo passes `false` directly for
        // usedInTransactions — no inUseRepo call.

        PurposeSettingDetail out = service.create(req);

        assertThat(out.useUsagCd()).isEqualTo("0000000010");
        assertThat(out.dispOrd()).isEqualTo(193);
        assertThat(out.usedInTransactions()).isFalse();

        ArgumentCaptor<BusiUsagSetInfm> persisted = ArgumentCaptor.forClass(BusiUsagSetInfm.class);
        verify(repo).saveAndFlush(persisted.capture());
        BusiUsagSetInfm e = persisted.getValue();
        assertThat(e.getBizKey()).isEqualTo(BIZ);
        assertThat(e.getHandUseUsagCd()).isEqualTo("BD02");
        assertThat(e.getUseUsagNm()).isEqualTo("Operating Expenses");
        assertThat(e.getUseYn()).isEqualTo("Y");
        assertThat(e.getDispYn()).isEqualTo("Y");
        assertThat(e.getUsagGrpCd()).isNull();
        assertThat(e.getModId()).isEqualTo(USER);
        assertThat(e.getModDtm()).isNotBlank();

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(ctx.capture());
        assertThat(ctx.getValue().wsvcId()).isEqualTo("purpose-settings");
        assertThat(ctx.getValue().refNo()).isEqualTo("BUSI_USAG_SET_INFM");
        assertThat(ctx.getValue().refNo1()).isEqualTo("0000000010");
        assertThat(ctx.getValue().refNo2()).isEqualTo("BD02");
        assertThat(ctx.getValue().detailKey()).isEqualTo("audit.purpose_settings.create.detail");
    }

    @Test
    void create_duplicateHandCd_throwsWBW1152() {
        PurposeSettingCreateRequest req = new PurposeSettingCreateRequest(
                "BD02", "Operating Expenses", null, null, null);
        when(repo.findActiveByHandCd(BIZ, "BD02"))
                .thenReturn(Optional.of(level1("x", "BD02", "Old")));

        assertBusinessCode("WBW1152", () -> service.create(req));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicateName_throwsWBW1138() {
        PurposeSettingCreateRequest req = new PurposeSettingCreateRequest(
                "BD02", "Operating Expenses", null, null, null);
        when(repo.findActiveByHandCd(BIZ, "BD02")).thenReturn(Optional.empty());
        when(repo.findActiveByName(BIZ, "Operating Expenses"))
                .thenReturn(Optional.of(level1("x", "OLD", "Operating Expenses")));

        assertBusinessCode("WBW1138", () -> service.create(req));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void create_parentNotFound_throwsWBE_VALIDATION() {
        PurposeSettingCreateRequest req = new PurposeSettingCreateRequest(
                "BD02", "Operating Expenses", null, "ghost", "Y");
        when(repo.findActiveByHandCd(BIZ, "BD02")).thenReturn(Optional.empty());
        when(repo.findActiveByName(BIZ, "Operating Expenses")).thenReturn(Optional.empty());
        when(repo.findByBizKeyAndUseUsagCd(BIZ, "ghost")).thenReturn(Optional.empty());

        assertBusinessCode("WBE_VALIDATION", () -> service.create(req));
    }

    @Test
    void create_parentIsLevel2_throwsWBE_VALIDATION() {
        PurposeSettingCreateRequest req = new PurposeSettingCreateRequest(
                "BD02", "Operating Expenses", null, CODE_B, "Y");
        when(repo.findActiveByHandCd(BIZ, "BD02")).thenReturn(Optional.empty());
        when(repo.findActiveByName(BIZ, "Operating Expenses")).thenReturn(Optional.empty());
        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_B))
                .thenReturn(Optional.of(level2(CODE_B, "BD01-01", "Sales", CODE_A)));

        assertBusinessCode("WBE_VALIDATION", () -> service.create(req));
    }

    @Test
    void create_retriesOncePastPkCollision() {
        PurposeSettingCreateRequest req = new PurposeSettingCreateRequest(
                "BD02", "Operating Expenses", null, null, "Y");
        when(repo.findActiveByHandCd(BIZ, "BD02")).thenReturn(Optional.empty());
        when(repo.findActiveByName(BIZ, "Operating Expenses")).thenReturn(Optional.empty());
        when(repo.findMaxDispOrd(BIZ)).thenReturn(Optional.of(0));
        when(legacyFunctions.nextUseUsagCd(BIZ, "")).thenReturn("0000000001", "0000000002");
        when(repo.saveAndFlush(any(BusiUsagSetInfm.class)))
                .thenThrow(new DataIntegrityViolationException("pk collision"))
                .thenAnswer(inv -> inv.getArgument(0));

        PurposeSettingDetail out = service.create(req);

        assertThat(out.useUsagCd()).isEqualTo("0000000002");
        verify(repo, times(2)).saveAndFlush(any(BusiUsagSetInfm.class));
        verify(legacyFunctions, times(2)).nextUseUsagCd(BIZ, "");
    }

    @Test
    void create_pkCollisionTwice_throwsWBE9999() {
        PurposeSettingCreateRequest req = new PurposeSettingCreateRequest(
                "BD02", "Operating Expenses", null, null, "Y");
        when(repo.findActiveByHandCd(BIZ, "BD02")).thenReturn(Optional.empty());
        when(repo.findActiveByName(BIZ, "Operating Expenses")).thenReturn(Optional.empty());
        when(repo.findMaxDispOrd(BIZ)).thenReturn(Optional.of(0));
        when(legacyFunctions.nextUseUsagCd(BIZ, "")).thenReturn("0000000001", "0000000002");
        when(repo.saveAndFlush(any(BusiUsagSetInfm.class)))
                .thenThrow(new DataIntegrityViolationException("pk collision"));

        assertBusinessCode("WBE9999", () -> service.create(req));
        verify(repo, times(2)).saveAndFlush(any(BusiUsagSetInfm.class));
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @Test
    void update_persistsChanges_andDoesNotCheckHandCdDup_legacyParity() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        PurposeSettingUpdateRequest req = new PurposeSettingUpdateRequest(
                "P01-RENAMED", "Deposit (renamed)", "New cont", null, "N");

        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.findDuplicateNameExcludingSelf(BIZ, "Deposit (renamed)", CODE_A))
                .thenReturn(Optional.empty());
        when(repo.save(existing)).thenReturn(existing);
        when(inUseRepo.findInUseCodes(eq(BIZ), any())).thenReturn(Set.of()); // used by post-write echo

        PurposeSettingDetail out = service.update(CODE_A, req);

        assertThat(out.handUseUsagCd()).isEqualTo("P01-RENAMED");
        assertThat(out.useUsagNm()).isEqualTo("Deposit (renamed)");
        assertThat(out.dispYn()).isEqualTo("N");

        // OQ-4 — legacy parity: NO findActiveByHandCd call on update.
        verify(repo, never()).findActiveByHandCd(anyString(), anyString());
    }

    @Test
    void update_duplicateName_throwsWBW1138() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        PurposeSettingUpdateRequest req = new PurposeSettingUpdateRequest(
                HAND_A, "Sales", null, null, null);

        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.findDuplicateNameExcludingSelf(BIZ, "Sales", CODE_A))
                .thenReturn(Optional.of(level2("other", "X", "Sales", CODE_A)));

        assertBusinessCode("WBW1138", () -> service.update(CODE_A, req));
        verify(repo, never()).save(any());
    }

    @Test
    void update_demoteWithChildren_throwsWBE_VALIDATION() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        PurposeSettingUpdateRequest req = new PurposeSettingUpdateRequest(
                HAND_A, NAME_A, null, CODE_B, null);

        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.findDuplicateNameExcludingSelf(BIZ, NAME_A, CODE_A)).thenReturn(Optional.empty());
        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_B))
                .thenReturn(Optional.of(level1(CODE_B, "BD02", "Other")));
        when(repo.hasActiveChildren(BIZ, CODE_A)).thenReturn(true);

        assertBusinessCode("WBE_VALIDATION", () -> service.update(CODE_A, req));
        verify(repo, never()).save(any());
    }

    @Test
    void update_selfParent_throwsWBE_VALIDATION() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        PurposeSettingUpdateRequest req = new PurposeSettingUpdateRequest(
                HAND_A, NAME_A, null, CODE_A, null);

        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.findDuplicateNameExcludingSelf(BIZ, NAME_A, CODE_A)).thenReturn(Optional.empty());

        assertBusinessCode("WBE_VALIDATION", () -> service.update(CODE_A, req));
    }

    // ------------------------------------------------------------------
    // Delete — OQ-5 recursive + legacy parity, OQ-8 single WBE1199 code
    // ------------------------------------------------------------------

    @Test
    void delete_blockedByActiveChildren_throwsWBE1199() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.hasActiveChildren(BIZ, CODE_A)).thenReturn(true);

        assertBusinessCode("WBE1199", () -> service.delete(CODE_A));
        verify(inUseRepo, never()).existsAnyByBizKeyAndUseUsagCdIn(anyString(), any());
        verify(repo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    @Test
    void delete_blockedByOwnTransactionReference_throwsWBE1199() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.hasActiveChildren(BIZ, CODE_A)).thenReturn(false);
        when(repo.findActiveChildCodes(BIZ, CODE_A)).thenReturn(List.of());
        when(inUseRepo.existsAnyByBizKeyAndUseUsagCdIn(BIZ, Set.of(CODE_A))).thenReturn(true);

        assertBusinessCode("WBE1199", () -> service.delete(CODE_A));
        verify(repo, never()).save(any());
    }

    @Test
    void delete_blockedByChildTransactionReference_recursive_throwsWBE1199() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.hasActiveChildren(BIZ, CODE_A)).thenReturn(false);
        when(repo.findActiveChildCodes(BIZ, CODE_A)).thenReturn(List.of(CODE_B));
        when(inUseRepo.existsAnyByBizKeyAndUseUsagCdIn(BIZ, Set.of(CODE_A, CODE_B)))
                .thenReturn(true);

        assertBusinessCode("WBE1199", () -> service.delete(CODE_A));
        verify(repo, never()).save(any());
    }

    @Test
    void delete_softDeletesAndAuditsWhenAllowed() {
        BusiUsagSetInfm existing = level1(CODE_A, HAND_A, NAME_A);
        when(repo.findByBizKeyAndUseUsagCd(BIZ, CODE_A)).thenReturn(Optional.of(existing));
        when(repo.hasActiveChildren(BIZ, CODE_A)).thenReturn(false);
        when(repo.findActiveChildCodes(BIZ, CODE_A)).thenReturn(List.of());
        when(inUseRepo.existsAnyByBizKeyAndUseUsagCdIn(BIZ, Set.of(CODE_A))).thenReturn(false);

        service.delete(CODE_A);

        assertThat(existing.getUseYn()).isEqualTo("N");
        assertThat(existing.getModId()).isEqualTo(USER);
        verify(repo).save(existing);

        ArgumentCaptor<AuditLogContext> ctx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(ctx.capture());
        assertThat(ctx.getValue().wsvcId()).isEqualTo("purpose-settings");
        assertThat(ctx.getValue().detailKey()).isEqualTo("audit.purpose_settings.delete.detail");
        assertThat(ctx.getValue().refNo1()).isEqualTo(CODE_A);
    }

    // ------------------------------------------------------------------
    // Reorder
    // ------------------------------------------------------------------

    @Test
    void reorder_duplicateUseUsagCd_throwsWBE_VALIDATION() {
        PurposeReorderRequest req = new PurposeReorderRequest(List.of(
                new PurposeReorderItem(CODE_A, 1),
                new PurposeReorderItem(CODE_A, 2)));

        assertBusinessCode("WBE_VALIDATION", () -> service.reorder(req));
        verify(repo, never()).findAllActiveForReorder(anyString(), any());
    }

    @Test
    void reorder_duplicateDispOrd_throwsWBE_VALIDATION() {
        PurposeReorderRequest req = new PurposeReorderRequest(List.of(
                new PurposeReorderItem(CODE_A, 1),
                new PurposeReorderItem(CODE_B, 1)));

        assertBusinessCode("WBE_VALIDATION", () -> service.reorder(req));
        verify(repo, never()).findAllActiveForReorder(anyString(), any());
    }

    @Test
    void reorder_unknownCode_throwsWBE_VALIDATION() {
        PurposeReorderRequest req = new PurposeReorderRequest(List.of(
                new PurposeReorderItem(CODE_A, 1),
                new PurposeReorderItem("ghost", 2)));

        when(repo.findAllActiveForReorder(eq(BIZ), any()))
                .thenReturn(List.of(level1(CODE_A, HAND_A, NAME_A)));   // only one of the two

        assertBusinessCode("WBE_VALIDATION", () -> service.reorder(req));
    }

    // Note: Excel upload tests live in PurposeExcelServiceTest — the
    // two-step preview/import flow has its own service class.

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static BusiUsagSetInfm level1(String useUsagCd, String handCd, String name) {
        BusiUsagSetInfm e = new BusiUsagSetInfm();
        e.setBizKey(BIZ);
        e.setUseUsagCd(useUsagCd);
        e.setHandUseUsagCd(handCd);
        e.setUseUsagNm(name);
        e.setUseYn("Y");
        e.setDispYn("Y");
        e.setDispOrd(1);
        e.setParentUseUsagCd(null);
        return e;
    }

    private static BusiUsagSetInfm level2(String useUsagCd, String handCd, String name, String parent) {
        BusiUsagSetInfm e = level1(useUsagCd, handCd, name);
        e.setParentUseUsagCd(parent);
        return e;
    }

    private static void assertBusinessCode(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", code);
    }
}
