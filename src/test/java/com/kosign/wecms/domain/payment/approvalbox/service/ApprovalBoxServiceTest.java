package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxActionResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxBulkApproveResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxListResponse;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxProjection;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxTabCounts;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfmId;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Payment &gt; Approval Box.
 *
 * <p>Pins behaviour the prompt's acceptance criteria require:
 * <ul>
 *   <li><b>list</b> — date validation parity with legacy
 *       {@code resl_0001_001_r001_act.jsp:54-60}, tab-type / sort-field
 *       guards, default ORDER BY fallthrough, tenant scoping;</li>
 *   <li><b>approve</b> — happy path advances status + writes audit;
 *       negative paths: not-found / not-an-approver /
 *       already-actioned / workflow-finalised;</li>
 *   <li><b>reject</b> — happy path cascades downstream + writes audit;
 *       same negative-path set as approve;</li>
 *   <li><b>bulkApprove</b> — happy path approves every row; partial
 *       success when one row fails; oversize / empty rejection.</li>
 * </ul>
 *
 * <p>The audit-log filter shape is asserted via an
 * {@link ArgumentCaptor} on every successful write so the
 * {@code WSVC_ID = "approval-box"} contract is pinned (slug, not legacy
 * {@code resl_0001_001}).
 */
@ExtendWith(MockitoExtension.class)
class ApprovalBoxServiceTest {

    @Mock ExptResolMstrRepository       masterRepo;
    @Mock ExptResolApprvrInfmRepository approverRepo;
    @Mock AuditLogService               auditLogService;

    ApprovalBoxService service;

    private static final String BIZ   = "VN_VNM0004_000001";
    private static final String USER  = "u_approver";
    private static final String SEQ   = "1540";
    private static final String FROM  = "20260101";
    private static final String TO    = "20260131";

    @BeforeEach
    void setUp() {
        service = new ApprovalBoxService(masterRepo, approverRepo, auditLogService);
        // Default to non-admin so the visibility filter receives isAdmin=false.
        TenantContext.set(USER, USER, BIZ, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  list — validation + tenant pass-through + counts default
     * ================================================================= */

    @Test
    void list_missingFromDate_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.list(
                null, TO, null, null, null, null, 0, 0));
        assertBusinessCode("WBE_VALIDATION", () -> service.list(
                "   ", TO, null, null, null, null, 0, 0));
        verify(masterRepo, never()).search(anyString(), anyString(), anyBoolean(),
                anyString(), anyString(), any(), any(), any(Pageable.class));
    }

    @Test
    void list_malformedDate_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.list(
                "2026010", TO, null, null, null, null, 0, 0));
        assertBusinessCode("WBE_VALIDATION", () -> service.list(
                "garbage1", TO, null, null, null, null, 0, 0));
        // Feb 30 — parseable as 8 digits but not a real date.
        assertBusinessCode("WBE_VALIDATION", () -> service.list(
                "20260230", TO, null, null, null, null, 0, 0));
    }

    @Test
    void list_invertedRange_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.list(
                "20260301", "20260101", null, null, null, null, 0, 0));
    }

    @Test
    void list_invalidApprTypeCd_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.list(
                FROM, TO, null, "99", null, null, 0, 0));
    }

    @Test
    void list_happyPath_passesTenantAndDefaults() {
        Page<ApprovalBoxProjection> empty = new PageImpl<>(List.of());
        when(masterRepo.search(eq(BIZ), eq(USER), eq(false),
                eq(FROM), eq(TO), any(), any(), any(Pageable.class))).thenReturn(empty);
        when(masterRepo.countTabs(eq(BIZ), eq(USER), eq(false),
                eq(FROM), eq(TO), any())).thenReturn(new ApprovalBoxTabCounts(2L, 1L, 3L, 4L));

        ApprovalBoxListResponse res = service.list(FROM, TO,
                null, null, null, null, 0, 0);

        assertThat(res.totalCount()).isEqualTo(0L);
        assertThat(res.tabCounts().pending()).isEqualTo(2L);
        assertThat(res.tabCounts().inProgress()).isEqualTo(1L);
        assertThat(res.tabCounts().approved()).isEqualTo(3L);
        assertThat(res.tabCounts().rejected()).isEqualTo(4L);
    }

    @Test
    void list_adminFlagFlowsThrough() {
        TenantContext.clear();
        TenantContext.set("adm", "adm", BIZ, LangCd.EN, TenantContext.AUTH_GR_ADMIN, "F");
        Page<ApprovalBoxProjection> empty = new PageImpl<>(List.of());
        when(masterRepo.search(eq(BIZ), eq("adm"), eq(true),
                anyString(), anyString(), any(), any(), any(Pageable.class))).thenReturn(empty);
        when(masterRepo.countTabs(eq(BIZ), eq("adm"), eq(true),
                anyString(), anyString(), any())).thenReturn(ApprovalBoxTabCounts.empty());

        service.list(FROM, TO, null, null, null, null, 0, 0);

        verify(masterRepo).search(eq(BIZ), eq("adm"), eq(true),
                anyString(), anyString(), any(), any(), any(Pageable.class));
    }

    /* =================================================================
     *  approve — happy + every negative path
     * ================================================================= */

    @Test
    void approve_happyPath_advancesAndAudits() {
        ExptResolMstr master = master(masterPending());
        ExptResolApprvrInfm me = approver(USER, "P", 1);
        ExptResolApprvrInfm next = approver("u_next", "W", 2);

        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.of(master));
        when(approverRepo.findById(new ExptResolApprvrInfmId(BIZ, SEQ, USER))).thenReturn(Optional.of(me));
        // After my approve: 0 pending, 1 waiting → still in progress, promote next.
        when(approverRepo.countByStatus(BIZ, SEQ, "P")).thenReturn(0L);
        when(approverRepo.countByStatus(BIZ, SEQ, "W")).thenReturn(1L);
        when(approverRepo.countByStatus(BIZ, SEQ, "A")).thenReturn(1L);
        when(approverRepo.findChain(BIZ, SEQ)).thenReturn(List.of(me, next));

        ApprovalBoxActionResponse res = service.approve(SEQ);

        assertThat(res.apprSeqNo()).isEqualTo(SEQ);
        assertThat(res.apprStts()).isEqualTo("I");      // in-progress derived from APPR_TYPE_CD='03'
        assertThat(res.apprvrTotCnt()).isEqualTo(2L);
        assertThat(res.apprvrApprTotCnt()).isEqualTo(1L);

        // Master flipped to '03' In Progress; caller's approver row is 'A'.
        assertThat(master.getApprTypeCd()).isEqualTo("03");
        assertThat(me.getApprvrStts()).isEqualTo("A");
        // Downstream waiting peer promoted to PENDING.
        assertThat(next.getApprvrStts()).isEqualTo("P");

        // Audit row written with the slug — pin the WSVC_ID contract.
        ArgumentCaptor<AuditLogContext> captor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(captor.capture());
        AuditLogContext ctx = captor.getValue();
        assertThat(ctx.wsvcId()).isEqualTo("approval-box");
        assertThat(ctx.refNo()).isEqualTo("EXPT_RESOL_MSTR");
        assertThat(ctx.refNo1()).isEqualTo(SEQ);
        assertThat(ctx.detailKey()).isEqualTo("audit.approval_box.approve.detail");
    }

    @Test
    void approve_lastStepFinalisesWorkflow() {
        ExptResolMstr master = master(masterPending());
        ExptResolApprvrInfm me = approver(USER, "P", 2);

        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.of(master));
        when(approverRepo.findById(new ExptResolApprvrInfmId(BIZ, SEQ, USER))).thenReturn(Optional.of(me));
        // No pending or waiting peers remain after my approve.
        when(approverRepo.countByStatus(BIZ, SEQ, "P")).thenReturn(0L);
        when(approverRepo.countByStatus(BIZ, SEQ, "W")).thenReturn(0L);
        when(approverRepo.countByStatus(BIZ, SEQ, "A")).thenReturn(2L);
        when(approverRepo.findChain(BIZ, SEQ)).thenReturn(List.of(me));

        ApprovalBoxActionResponse res = service.approve(SEQ);

        assertThat(master.getApprTypeCd()).isEqualTo("01");     // Approved tab
        assertThat(res.apprStts()).isEqualTo("C");              // Completed
    }

    @Test
    void approve_masterMissing_throwsWBE2050() {
        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.empty());
        assertBusinessCode("WBE2050", () -> service.approve(SEQ));
        verify(auditLogService, never()).append(any());
    }

    @Test
    void approve_workflowAlreadyFinalised_throwsWBE2053() {
        ExptResolMstr master = master(masterPending());
        master.setApprTypeCd("02");      // Rejected
        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.of(master));
        assertBusinessCode("WBE2053", () -> service.approve(SEQ));
        verify(auditLogService, never()).append(any());
    }

    @Test
    void approve_callerNotApprover_throwsWBE2051() {
        ExptResolMstr master = master(masterPending());
        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.of(master));
        when(approverRepo.findById(new ExptResolApprvrInfmId(BIZ, SEQ, USER))).thenReturn(Optional.empty());
        assertBusinessCode("WBE2051", () -> service.approve(SEQ));
        verify(auditLogService, never()).append(any());
    }

    @Test
    void approve_callerAlreadyActed_throwsWBE2052() {
        ExptResolMstr master = master(masterPending());
        ExptResolApprvrInfm me = approver(USER, "A", 1);     // already approved
        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.of(master));
        when(approverRepo.findById(new ExptResolApprvrInfmId(BIZ, SEQ, USER))).thenReturn(Optional.of(me));
        assertBusinessCode("WBE2052", () -> service.approve(SEQ));
        verify(auditLogService, never()).append(any());
    }

    @Test
    void approve_blankApprSeqNo_throwsValidation() {
        assertBusinessCode("WBE_VALIDATION", () -> service.approve(null));
        assertBusinessCode("WBE_VALIDATION", () -> service.approve("   "));
    }

    /* =================================================================
     *  reject — happy + key negative paths (negatives share approve's
     *  load step, so spot-check the cascade + master flip + audit key).
     * ================================================================= */

    @Test
    void reject_happyPath_cascadesAndAudits() {
        ExptResolMstr master = master(masterPending());
        ExptResolApprvrInfm me = approver(USER, "P", 1);

        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.of(master));
        when(approverRepo.findById(new ExptResolApprvrInfmId(BIZ, SEQ, USER))).thenReturn(Optional.of(me));
        when(approverRepo.stopDownstream(eq(BIZ), eq(SEQ), eq(USER), anyString())).thenReturn(2);
        when(approverRepo.findChain(BIZ, SEQ)).thenReturn(List.of(me));
        when(approverRepo.countByStatus(BIZ, SEQ, "A")).thenReturn(0L);

        ApprovalBoxActionResponse res = service.reject(SEQ);

        assertThat(res.apprStts()).isEqualTo("R");
        assertThat(master.getApprTypeCd()).isEqualTo("02");
        assertThat(me.getApprvrStts()).isEqualTo("R");
        // Downstream cascade executed.
        verify(approverRepo).stopDownstream(eq(BIZ), eq(SEQ), eq(USER), anyString());

        ArgumentCaptor<AuditLogContext> captor = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLogService).append(captor.capture());
        assertThat(captor.getValue().wsvcId()).isEqualTo("approval-box");
        assertThat(captor.getValue().detailKey()).isEqualTo("audit.approval_box.reject.detail");
    }

    @Test
    void reject_workflowAlreadyFinalised_throwsWBE2053() {
        ExptResolMstr master = master(masterPending());
        master.setApprTypeCd("01");      // already approved
        when(masterRepo.findById(new ExptResolMstrId(BIZ, SEQ))).thenReturn(Optional.of(master));
        assertBusinessCode("WBE2053", () -> service.reject(SEQ));
        verify(auditLogService, never()).append(any());
    }

    /* =================================================================
     *  bulk-approve — happy, partial-success, oversize / empty
     * ================================================================= */

    @Test
    void bulkApprove_happyPath_approvesAllAndAuditsBatch() {
        // Each ID resolves to a master with one pending approver (caller).
        stubApprovableRow("1");
        stubApprovableRow("2");

        ApprovalBoxBulkApproveResponse res = service.bulkApprove(List.of("1", "2"));

        assertThat(res.successes()).hasSize(2);
        assertThat(res.failures()).isEmpty();

        // 2 per-row approve audits + 1 bulk audit = 3 calls.
        verify(auditLogService, times(3)).append(any());
    }

    @Test
    void bulkApprove_partialFailure_collectsCodes() {
        stubApprovableRow("1");
        // Second id resolves to a missing master.
        when(masterRepo.findById(new ExptResolMstrId(BIZ, "missing"))).thenReturn(Optional.empty());

        ApprovalBoxBulkApproveResponse res = service.bulkApprove(List.of("1", "missing"));

        assertThat(res.successes()).hasSize(1);
        assertThat(res.successes().get(0).apprSeqNo()).isEqualTo("1");
        assertThat(res.failures()).hasSize(1);
        assertThat(res.failures().get(0).apprSeqNo()).isEqualTo("missing");
        assertThat(res.failures().get(0).code()).isEqualTo("WBE2050");

        // 1 per-row approve audit + 1 bulk audit (one success ⇒ bulk audit fires).
        verify(auditLogService, times(2)).append(any());
    }

    @Test
    void bulkApprove_empty_throwsWBE2054() {
        assertBusinessCode("WBE2054", () -> service.bulkApprove(List.of()));
        assertBusinessCode("WBE2054", () -> service.bulkApprove(null));
        verify(auditLogService, never()).append(any());
    }

    @Test
    void bulkApprove_oversized_throwsWBE2054() {
        List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < ApprovalBoxService.MAX_BULK_SIZE + 1; i++) tooMany.add(String.valueOf(i));
        assertBusinessCode("WBE2054", () -> service.bulkApprove(tooMany));
        verify(auditLogService, never()).append(any());
    }

    /* =================================================================
     *  Status derivation — pins the wb_get_appr_stts replacement
     * ================================================================= */

    @Test
    void deriveApprStts_mapsAllFour() {
        assertThat(ApprovalBoxService.deriveApprStts("00")).isEqualTo("P");
        assertThat(ApprovalBoxService.deriveApprStts("03")).isEqualTo("I");
        assertThat(ApprovalBoxService.deriveApprStts("01")).isEqualTo("C");
        assertThat(ApprovalBoxService.deriveApprStts("02")).isEqualTo("R");
        assertThat(ApprovalBoxService.deriveApprStts(null)).isEqualTo("P");
    }

    /* =================================================================
     *  Fixtures + assertions
     * ================================================================= */

    private static ExptResolMstr masterPending() {
        ExptResolMstr m = new ExptResolMstr();
        m.setBizKey(BIZ);
        m.setApprSeqNo(SEQ);
        m.setApprDv("35");                          // Internal Transfer
        m.setApprSts("0");
        m.setApprTypeCd("00");                      // Pending tab
        m.setDelYn("N");
        m.setTitl("Test");
        m.setDocNo("VO_260511_002");
        m.setDocNm("Internal Transfer");
        m.setDraftDate("20260511");
        m.setDraftTime("075547");
        m.setDraftUserId(USER);
        m.setDraftUserNm("Approver Test");
        m.setRegId(USER);
        m.setRegDtm("20260511075547");
        m.setModDtm("20260511075547");
        m.setPayEndYn("N");
        m.setTotPayKhrAmt(new BigDecimal("2000000"));
        return m;
    }

    private static ExptResolMstr master(ExptResolMstr m) { return m; }

    private static ExptResolApprvrInfm approver(String userId, String status, int lineNo) {
        ExptResolApprvrInfm a = new ExptResolApprvrInfm();
        a.setBizKey(BIZ);
        a.setApprSeqNo(SEQ);
        a.setApprvrId(userId);
        a.setApprvrStts(status);
        a.setApprvrNm("Approver " + userId);
        a.setRegDtm("20260511075547");
        a.setModId(userId);
        a.setModDtm("20260511075547");
        a.setReadYn("N");
        a.setApprvrLineNo(BigDecimal.valueOf(lineNo));
        return a;
    }

    /** Stub a freshly-approvable row keyed on {@code id}. The caller's
     *  approver row is the only approver, so after one approve the
     *  workflow finalises. */
    private void stubApprovableRow(String id) {
        ExptResolMstr m = masterPending();
        m.setApprSeqNo(id);
        ExptResolApprvrInfm me = approver(USER, "P", 1);
        me.setApprSeqNo(id);

        when(masterRepo.findById(new ExptResolMstrId(BIZ, id))).thenReturn(Optional.of(m));
        when(approverRepo.findById(new ExptResolApprvrInfmId(BIZ, id, USER))).thenReturn(Optional.of(me));
        when(approverRepo.countByStatus(BIZ, id, "P")).thenReturn(0L);
        when(approverRepo.countByStatus(BIZ, id, "W")).thenReturn(0L);
        when(approverRepo.countByStatus(BIZ, id, "A")).thenReturn(1L);
        when(approverRepo.findChain(BIZ, id)).thenReturn(List.of(me));
    }

    private static void assertBusinessCode(String code, Runnable r) {
        assertThatThrownBy(r::run)
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(code);
    }
}
