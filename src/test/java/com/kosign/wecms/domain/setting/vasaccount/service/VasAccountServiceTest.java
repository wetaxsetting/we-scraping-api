package com.kosign.wecms.domain.setting.vasaccount.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountCreateRequest;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountListResponse;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountRow;
import com.kosign.wecms.domain.setting.vasaccount.dto.VasAccountUpdateRequest;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Settings &gt; VAS Account. Spans list +
 * detail + create + update + softDelete with parity-relevant edge cases:
 * tenant scoping, soft-delete contract, duplicate-code rejection (create
 * + rename), unchanged-code update (skip the duplicate-check),
 * status-filter normalisation (A / I / both / unknown), sort whitelist +
 * fall-through, pagination clamping, audit-log emission with the slug
 * wsvc_id, and i18n key resolution on row mapping.
 */
@ExtendWith(MockitoExtension.class)
class VasAccountServiceTest {

    @Mock BusiVasAcctInfmRepository repo;
    @Mock VasAcctSeqNoGenerator seqGen;
    @Mock AuditLogService auditLogService;

    private VasAccountService service;

    private static final String BIZ  = "VN_VNM0004_000001";
    private static final String USER = "tester@example.com";

    @BeforeEach
    void setUp() {
        service = new VasAccountService(repo, seqGen, auditLogService);
        TenantContext.set(USER, USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  list — filter resolution, pagination, sort
     * ================================================================= */

    @Test
    void list_emptyStatuses_passesNullFilter() {
        // Use the Pageable-aware PageImpl ctor so getSize() reflects the
        // service-built request, not the content length.
        Page<BusiVasAcctInfm> page = new PageImpl<>(
                List.of(seed(132L, "323", "Jinpai", "0", "A")),
                PageRequest.of(0, VasAccountService.DEFAULT_PAGE_SIZE),
                1);
        when(repo.search(eq(BIZ), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        VasAccountListResponse res = service.list(null, null, null, null, 0, 0);

        assertThat(res.list()).hasSize(1);
        assertThat(res.totalCount()).isEqualTo(1L);
        assertThat(res.pageSize()).isEqualTo(VasAccountService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void list_singleStatusA_passesAsFilter() {
        Page<BusiVasAcctInfm> page = new PageImpl<>(List.of());
        when(repo.search(eq(BIZ), eq("A"), isNull(), any(Pageable.class))).thenReturn(page);

        service.list(null, List.of("A"), null, null, 0, 0);

        verify(repo).search(eq(BIZ), eq("A"), isNull(), any(Pageable.class));
    }

    @Test
    void list_bothStatuses_treatedAsNoFilter() {
        Page<BusiVasAcctInfm> page = new PageImpl<>(List.of());
        when(repo.search(eq(BIZ), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        service.list(null, List.of("A", "I"), null, null, 0, 0);

        verify(repo).search(eq(BIZ), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_invalidStatus_throwsValidation() {
        assertThatThrownBy(() -> service.list(null, List.of("Q"), null, null, 0, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
        verify(repo, never()).search(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void list_searchWdEscapesLikeMetacharacters() {
        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        when(repo.search(eq(BIZ), isNull(), patternCaptor.capture(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list("10%_x!", null, null, null, 0, 0);

        // Lowercased + each meta-char prefixed with the '!' escape +
        // wrapped with %…%.
        assertThat(patternCaptor.getValue()).isEqualTo("%10!%!_x!!%");
    }

    @Test
    void list_unknownSortField_fallsBackToDefaultSort() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.search(eq(BIZ), isNull(), isNull(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, "MALICIOUS_SQL", "ASC", 0, 0);

        Sort sort = pageableCaptor.getValue().getSort();
        Sort.Order primary = sort.iterator().next();
        // Default: vasAcctCd ASC + vasAcctSeqNo ASC tiebreaker
        assertThat(primary.getProperty()).isEqualTo("vasAcctCd");
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void list_whitelistedSortField_appliedWithTiebreaker() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.search(eq(BIZ), isNull(), isNull(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, "stts", "DESC", 0, 0);

        List<Sort.Order> orders = pageableCaptor.getValue().getSort().toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("stts");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getProperty()).isEqualTo("vasAcctSeqNo");
    }

    @Test
    void list_pageSizeClampedToMax() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.search(eq(BIZ), isNull(), isNull(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null, 0, 999);

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(VasAccountService.MAX_PAGE_SIZE);
    }

    @Test
    void list_negativePage_clampedToZero() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.search(eq(BIZ), isNull(), isNull(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null, -5, 0);

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void list_mapsRowWithI18nKeys() {
        BusiVasAcctInfm e = seed(132L, "323", "Jinpai", "0", "A");
        when(repo.search(eq(BIZ), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e), PageRequest.of(0, 20), 1));

        VasAccountListResponse res = service.list(null, null, null, null, 0, 0);

        VasAccountRow row = res.list().get(0);
        assertThat(row.vasAcctSeqNo()).isEqualTo("132");
        assertThat(row.vasAcctType()).isEqualTo("0");
        assertThat(row.vasAcctTypeKey()).isEqualTo("settings.vas_account.type.dual");
        assertThat(row.stts()).isEqualTo("A");
        assertThat(row.sttsKey()).isEqualTo("settings.vas_account.status.active");
    }

    /* =================================================================
     *  listAll — load-all (no pagination) for the management panel
     *  (RISK-1 / RISK-2 fix). Reuses the unpaged search path.
     * ================================================================= */

    @Test
    void listAll_returnsFullSet_unpaged() {
        Page<BusiVasAcctInfm> page = new PageImpl<>(List.of(
                seed(1L, "111", "Parent", "0", "A"),
                seed(2L, "1111", "Child", "0", "A")));
        when(repo.search(eq(BIZ), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        VasAccountListResponse res = service.listAll(null, null, null, null);

        assertThat(res.list()).hasSize(2);
        assertThat(res.totalCount()).isEqualTo(2L);
        assertThat(res.pageNumber()).isEqualTo(0);
        assertThat(res.pageSize()).isEqualTo(2);
        assertThat(res.totalPages()).isEqualTo(1);
    }

    @Test
    void listAll_requestsUnpagedPageable() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.search(eq(BIZ), isNull(), isNull(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listAll(null, null, null, null);

        // Single page wide enough to hold the entire tenant set — no
        // MAX_PAGE_SIZE clamp on this path (that is the point of the fix).
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void listAll_emptyTenant_zeroTotalPages() {
        when(repo.search(eq(BIZ), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        VasAccountListResponse res = service.listAll(null, null, null, null);

        assertThat(res.list()).isEmpty();
        assertThat(res.totalCount()).isZero();
        assertThat(res.totalPages()).isZero();
    }

    @Test
    void listAll_honoursStatusAndSort() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repo.search(eq(BIZ), eq("A"), isNull(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listAll(null, List.of("A"), "stts", "DESC");

        verify(repo).search(eq(BIZ), eq("A"), isNull(), any(Pageable.class));
        Sort.Order primary = pageableCaptor.getValue().getSort().iterator().next();
        assertThat(primary.getProperty()).isEqualTo("stts");
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /* =================================================================
     *  detail
     * ================================================================= */

    @Test
    void detail_returnsRow() {
        BusiVasAcctInfm e = seed(132L, "323", "Jinpai", "0", "A");
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 132L, "N")).thenReturn(Optional.of(e));

        VasAccountRow row = service.detail(132L);

        assertThat(row.vasAcctCd()).isEqualTo("323");
    }

    @Test
    void detail_notFound_throwsWBE2040() {
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 999L, "N")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE2040");
    }

    @Test
    void detail_negativeSeqNo_throwsValidation() {
        assertThatThrownBy(() -> service.detail(0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_VALIDATION");
        verify(repo, never()).findByBizKeyAndVasAcctSeqNoAndDelYn(any(), anyLong(), anyString());
    }

    /* =================================================================
     *  create
     * ================================================================= */

    @Test
    void create_happyPath_persistsAndAudits() {
        when(repo.existsByBizKeyAndVasAcctCdAndDelYn(BIZ, "999", "N")).thenReturn(false);
        when(seqGen.next(BIZ)).thenReturn(200L);
        when(repo.save(any(BusiVasAcctInfm.class))).thenAnswer(inv -> inv.getArgument(0));

        VasAccountRow row = service.create(new VasAccountCreateRequest(
                "999", "Test name", null, "0", "A", null));

        ArgumentCaptor<BusiVasAcctInfm> savedCaptor = ArgumentCaptor.forClass(BusiVasAcctInfm.class);
        verify(repo).save(savedCaptor.capture());
        BusiVasAcctInfm saved = savedCaptor.getValue();
        assertThat(saved.getBizKey()).isEqualTo(BIZ);
        assertThat(saved.getVasAcctSeqNo()).isEqualTo(200L);
        assertThat(saved.getDelYn()).isEqualTo("N");
        assertThat(saved.getRegId()).isEqualTo(USER);
        assertThat(saved.getModId()).isEqualTo(USER);
        assertThat(saved.getRegDtm()).hasSize(14);
        assertThat(saved.getModDtm()).isEqualTo(saved.getRegDtm());

        // Audit row carries the slug, not a legacy WSVC id.
        ArgumentCaptor<AuditLogContext> auditCaptor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(auditCaptor.capture());
        AuditLogContext ctx = auditCaptor.getValue();
        assertThat(ctx.wsvcId()).isEqualTo("vas-account");
        assertThat(ctx.refNo()).isEqualTo("BUSI_VAS_ACCT_INFM");
        assertThat(ctx.refNo1()).isEqualTo("200");
        assertThat(ctx.refNo2()).isEqualTo("999");
        assertThat(ctx.detailKey()).isEqualTo("audit.vas_account.create.detail");

        assertThat(row.vasAcctSeqNo()).isEqualTo("200");
    }

    @Test
    void create_blankType_defaultsToDual() {
        // PO call 2026-05-11: UI no longer surfaces Type. Wire ships
        // null; service defaults to "0" (Dual) so the NOT-NULL DB
        // column never sees a null bind.
        when(repo.existsByBizKeyAndVasAcctCdAndDelYn(BIZ, "999", "N")).thenReturn(false);
        when(seqGen.next(BIZ)).thenReturn(201L);
        when(repo.save(any(BusiVasAcctInfm.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new VasAccountCreateRequest(
                "999", "Test name", null, null, "A", null));

        ArgumentCaptor<BusiVasAcctInfm> savedCaptor = ArgumentCaptor.forClass(BusiVasAcctInfm.class);
        verify(repo).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getVasAcctType()).isEqualTo("0");
    }

    @Test
    void create_duplicateCode_throwsWBE0003() {
        when(repo.existsByBizKeyAndVasAcctCdAndDelYn(BIZ, "999", "N")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new VasAccountCreateRequest(
                "999", "Test", null, "0", "A", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE0003");
        verify(repo, never()).save(any());
        verify(auditLogService, never()).append(any());
    }

    /* =================================================================
     *  update
     * ================================================================= */

    @Test
    void update_sameCode_skipsDuplicateCheck() {
        BusiVasAcctInfm existing = seed(132L, "323", "Jinpai", "0", "A");
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 132L, "N")).thenReturn(Optional.of(existing));

        VasAccountRow row = service.update(132L, new VasAccountUpdateRequest(
                "323", "Renamed", null, "1", "I", "notes"));

        verify(repo, never()).existsByBizKeyAndVasAcctCdAndDelYn(anyString(), anyString(), anyString());
        assertThat(existing.getVasAcctNm()).isEqualTo("Renamed");
        assertThat(existing.getVasAcctType()).isEqualTo("1");
        assertThat(existing.getStts()).isEqualTo("I");
        assertThat(existing.getCont()).isEqualTo("notes");
        assertThat(existing.getModId()).isEqualTo(USER);
        assertThat(row.vasAcctType()).isEqualTo("1");

        ArgumentCaptor<AuditLogContext> auditCaptor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().detailKey()).isEqualTo("audit.vas_account.update.detail");
    }

    @Test
    void update_blankTypeAndEnm_preserveExistingValues() {
        // PO call 2026-05-11: UI no longer surfaces Type or English
        // Name. Wire ships both as null; service preserves the existing
        // entity values so a future tool that sets either field can't
        // be silently wiped by an Edit dialog submit.
        BusiVasAcctInfm existing = seed(132L, "323", "Jinpai", "2", "A");
        existing.setVasAcctEnm("Cash");
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 132L, "N")).thenReturn(Optional.of(existing));

        service.update(132L, new VasAccountUpdateRequest(
                "323", "Renamed", null, null, "I", "notes"));

        assertThat(existing.getVasAcctType()).isEqualTo("2");      // preserved
        assertThat(existing.getVasAcctEnm()).isEqualTo("Cash");    // preserved
        assertThat(existing.getVasAcctNm()).isEqualTo("Renamed");  // updated
        assertThat(existing.getStts()).isEqualTo("I");             // updated
    }

    @Test
    void update_codeChangedNotDuplicate_persists() {
        BusiVasAcctInfm existing = seed(132L, "323", "Jinpai", "0", "A");
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 132L, "N")).thenReturn(Optional.of(existing));
        when(repo.existsByBizKeyAndVasAcctCdAndDelYn(BIZ, "999", "N")).thenReturn(false);

        service.update(132L, new VasAccountUpdateRequest(
                "999", "Renamed", null, "0", "A", null));

        assertThat(existing.getVasAcctCd()).isEqualTo("999");
        verify(repo).existsByBizKeyAndVasAcctCdAndDelYn(BIZ, "999", "N");
    }

    @Test
    void update_codeChangedDuplicate_throwsWBE0003() {
        BusiVasAcctInfm existing = seed(132L, "323", "Jinpai", "0", "A");
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 132L, "N")).thenReturn(Optional.of(existing));
        when(repo.existsByBizKeyAndVasAcctCdAndDelYn(BIZ, "999", "N")).thenReturn(true);

        assertThatThrownBy(() -> service.update(132L, new VasAccountUpdateRequest(
                "999", "Renamed", null, "0", "A", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE0003");
        // The change must not have persisted on the entity.
        assertThat(existing.getVasAcctCd()).isEqualTo("323");
        verify(auditLogService, never()).append(any());
    }

    @Test
    void update_notFound_throwsWBE2040() {
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 999L, "N")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, new VasAccountUpdateRequest(
                "999", "X", null, "0", "A", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE2040");
    }

    /* =================================================================
     *  softDelete
     * ================================================================= */

    @Test
    void softDelete_flipsDelYnAndAudits() {
        BusiVasAcctInfm existing = seed(132L, "323", "Jinpai", "0", "A");
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 132L, "N")).thenReturn(Optional.of(existing));

        service.softDelete(132L);

        assertThat(existing.getDelYn()).isEqualTo("Y");
        assertThat(existing.getModId()).isEqualTo(USER);

        ArgumentCaptor<AuditLogContext> auditCaptor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(auditCaptor.capture());
        AuditLogContext ctx = auditCaptor.getValue();
        assertThat(ctx.detailKey()).isEqualTo("audit.vas_account.delete.detail");
        assertThat(ctx.refNo2()).isEqualTo("323");
    }

    @Test
    void softDelete_notFound_throwsWBE2040() {
        when(repo.findByBizKeyAndVasAcctSeqNoAndDelYn(BIZ, 999L, "N")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE2040");
        verify(auditLogService, never()).append(any());
    }

    /* =================================================================
     *  Helpers
     * ================================================================= */

    private static BusiVasAcctInfm seed(long seqNo, String cd, String nm, String type, String stts) {
        BusiVasAcctInfm e = new BusiVasAcctInfm();
        e.setBizKey(BIZ);
        e.setVasAcctSeqNo(seqNo);
        e.setVasAcctCd(cd);
        e.setVasAcctNm(nm);
        e.setVasAcctType(type);
        e.setStts(stts);
        e.setDelYn("N");
        e.setRegId("seed@example.com");
        e.setRegDtm("20260101000000");
        e.setModId("seed@example.com");
        e.setModDtm("20260101000000");
        return e;
    }
}
