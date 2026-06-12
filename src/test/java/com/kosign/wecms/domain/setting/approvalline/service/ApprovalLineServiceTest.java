package com.kosign.wecms.domain.setting.approvalline.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.repository.UserBusiConnInfmRepository;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineApproverInput;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineCreateRequest;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineRow;
import com.kosign.wecms.domain.setting.approvalline.dto.ApprovalLineUpdateRequest;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLine;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprLineInfmDetl;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprLineInfmDetlId;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineId;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer parity coverage for {@link ApprovalLineService} —
 * legacy {@code comm_0222_002_c001} (create), {@code comm_0222_003_u001}
 * (diff-sync update), and {@code comm_0222_001_d001} (delete). Pure
 * Mockito (matches {@code OperationStatusServiceTest} style): collaborators
 * are mocked, {@link TenantContext} is seeded per test, and assertions
 * target the SAVE / DELETE orchestration that carries the legacy parity
 * rules (5-approver cap, duplicate guard, tenant-link guard, 1..N
 * re-sequencing, insert / update / delete reconciliation, hard delete).
 *
 * <p>Closes audit gap AL-GAP-02 (docs/jex/features/approval-line/refactor-audit.md).
 */
@ExtendWith(MockitoExtension.class)
class ApprovalLineServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private ApprovalLineRepository         lineRepo;
    @Mock private ApprovalLineApproverRepository approverRepo;
    @Mock private UserBusiConnInfmRepository     userBusiRepo;
    @Mock private LegacyFunctionRepository       legacyFnRepo;

    @InjectMocks
    private ApprovalLineService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ---------------------------------------------------------------
     *  Create
     * --------------------------------------------------------------- */

    @Test
    void create_insertsApprovers_assignsSequentialDispOrd_stampsTenantAndAudit() {
        linkActive("u1", "u2");
        when(legacyFnRepo.nextApprLineNo(TENANT)).thenReturn("10");
        // detail() read-back after the writes complete.
        when(lineRepo.findById(new ApprovalLineId(TENANT, 10L)))
                .thenReturn(Optional.of(line(10L, "Line A")));
        when(approverRepo.findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(TENANT, 10L))
                .thenReturn(List.of(detail(10L, 1, "u1", "U1"), detail(10L, 2, "u2", "U2")));

        ApprovalLineRow result = service.create(new ApprovalLineCreateRequest(
                "Line A", List.of(input("u1", "U1"), input("u2", "U2"))));

        // Master: tenant + name + server-allocated surrogate.
        ArgumentCaptor<ApprovalLine> master = ArgumentCaptor.forClass(ApprovalLine.class);
        verify(lineRepo).save(master.capture());
        assertThat(master.getValue().getBizKey()).isEqualTo(TENANT);
        assertThat(master.getValue().getApprLineNm()).isEqualTo("Line A");
        assertThat(master.getValue().getApprLineNo()).isEqualTo(10L);
        assertThat(master.getValue().getRegId()).isEqualTo(USER);
        assertThat(master.getValue().getModId()).isEqualTo(USER);

        // Details: disp_ord assigned 1..N from array position; tenant +
        // parent + audit stamped from the JWT-bound user, never the wire.
        ArgumentCaptor<ApprLineInfmDetl> rows = ArgumentCaptor.forClass(ApprLineInfmDetl.class);
        verify(approverRepo, times(2)).save(rows.capture());
        assertThat(rows.getAllValues())
                .extracting(ApprLineInfmDetl::getApprvrId).containsExactly("u1", "u2");
        assertThat(rows.getAllValues())
                .extracting(ApprLineInfmDetl::getApprvrDispOrd).containsExactly(1, 2);
        assertThat(rows.getAllValues()).allSatisfy(r -> {
            assertThat(r.getBizKey()).isEqualTo(TENANT);
            assertThat(r.getApprLineNo()).isEqualTo(10L);
            assertThat(r.getRegId()).isEqualTo(USER);
            assertThat(r.getRegDtm()).isNotBlank();
        });

        assertThat(result.apprLineNo()).isEqualTo(10L);
        assertThat(result.approvers()).hasSize(2);
    }

    @Test
    void create_optionalPosiDept_persistEmptyStringNeverNull() {
        linkActive("u1");
        when(legacyFnRepo.nextApprLineNo(TENANT)).thenReturn("11");
        when(lineRepo.findById(new ApprovalLineId(TENANT, 11L)))
                .thenReturn(Optional.of(line(11L, "L")));
        when(approverRepo.findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(TENANT, 11L))
                .thenReturn(List.of(detail(11L, 1, "u1", "U1")));

        // posi / dept omitted (null) on the wire.
        service.create(new ApprovalLineCreateRequest("L", List.of(input("u1", "U1"))));

        ArgumentCaptor<ApprLineInfmDetl> rows = ArgumentCaptor.forClass(ApprLineInfmDetl.class);
        verify(approverRepo).save(rows.capture());
        // Legacy parity (comm_0222_003_u001_act.jsp:144-148): empty string, not null.
        assertThat(rows.getValue().getApprvrPosi()).isEqualTo("");
        assertThat(rows.getValue().getApprvrDept()).isEqualTo("");
    }

    @Test
    void create_moreThanFiveApprovers_throwsTooMany_doesNotSave() {
        ApprovalLineCreateRequest req = new ApprovalLineCreateRequest("L", List.of(
                input("u1", "U1"), input("u2", "U2"), input("u3", "U3"),
                input("u4", "U4"), input("u5", "U5"), input("u6", "U6")));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_TOO_MANY_APPROVERS"));

        verify(lineRepo, never()).save(any());
        verify(approverRepo, never()).save(any());
    }

    @Test
    void create_emptyApprovers_throwsEmpty() {
        assertThatThrownBy(() -> service.create(new ApprovalLineCreateRequest("L", List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_EMPTY_APPROVERS"));
        verify(lineRepo, never()).save(any());
    }

    @Test
    void create_duplicateApprover_throwsDuplicate() {
        ApprovalLineCreateRequest req = new ApprovalLineCreateRequest("L",
                List.of(input("u1", "U1"), input("u1", "U1 again")));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_DUP_APPROVER"));
        verify(lineRepo, never()).save(any());
    }

    @Test
    void create_blankName_throwsNameBlank() {
        ApprovalLineCreateRequest req = new ApprovalLineCreateRequest("   ",
                List.of(input("u1", "U1")));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_NAME_BLANK"));
        verify(lineRepo, never()).save(any());
    }

    @Test
    void create_approverNotLinkedToTenant_throwsNotInTenant() {
        // u1 is an active tenant link; u2 is not.
        linkActive("u1");

        ApprovalLineCreateRequest req = new ApprovalLineCreateRequest("L",
                List.of(input("u1", "U1"), input("u2", "U2")));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT"));
        verify(lineRepo, never()).save(any());
    }

    /* ---------------------------------------------------------------
     *  Update — diff-sync (the highest-risk path)
     * --------------------------------------------------------------- */

    @Test
    void update_diffSync_insertsNew_updatesExisting_deletesRemoved_resequences() {
        // Existing chain: A(ord 1), B(ord 2). Incoming: C then A — so C is
        // net-new, B is net-removed, and A moves to position 2.
        linkActive("a", "c");
        ApprovalLine master = line(5L, "Old Name");
        when(lineRepo.findById(new ApprovalLineId(TENANT, 5L))).thenReturn(Optional.of(master));
        when(approverRepo.findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(TENANT, 5L))
                .thenReturn(List.of(detail(5L, 1, "a", "A"), detail(5L, 2, "b", "B")));

        service.update(5L, new ApprovalLineUpdateRequest(
                "New Name", List.of(input("c", "C"), input("a", "A"))));

        // Master renamed in place + MOD audit refreshed (managed entity —
        // no explicit save in the service; JPA dirty-checks on flush).
        assertThat(master.getApprLineNm()).isEqualTo("New Name");
        assertThat(master.getModId()).isEqualTo(USER);

        // Two saves: C inserted at disp_ord 1, A updated to disp_ord 2.
        ArgumentCaptor<ApprLineInfmDetl> saved = ArgumentCaptor.forClass(ApprLineInfmDetl.class);
        verify(approverRepo, times(2)).save(saved.capture());
        ApprLineInfmDetl c = byId(saved.getAllValues(), "c");
        ApprLineInfmDetl a = byId(saved.getAllValues(), "a");
        assertThat(c.getApprvrDispOrd()).isEqualTo(1);
        assertThat(c.getApprvrNm()).isEqualTo("C");
        assertThat(c.getRegId()).isEqualTo(USER);   // new row → REG stamped
        assertThat(a.getApprvrDispOrd()).isEqualTo(2);
        assertThat(a.getModId()).isEqualTo(USER);

        // B removed by PK.
        verify(approverRepo).deleteById(new ApprLineInfmDetlId(TENANT, 5L, "b"));
    }

    @Test
    void update_missingMaster_throwsNotFound() {
        linkActive("a");
        when(lineRepo.findById(new ApprovalLineId(TENANT, 99L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L,
                new ApprovalLineUpdateRequest("L", List.of(input("a", "A")))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_NOT_FOUND"));
        verify(approverRepo, never()).save(any());
        verify(approverRepo, never()).deleteById(any());
    }

    @Test
    void update_moreThanFiveApprovers_throwsTooMany_beforeAnyWrite() {
        ApprovalLineUpdateRequest req = new ApprovalLineUpdateRequest("L", List.of(
                input("u1", "U1"), input("u2", "U2"), input("u3", "U3"),
                input("u4", "U4"), input("u5", "U5"), input("u6", "U6")));

        assertThatThrownBy(() -> service.update(5L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_TOO_MANY_APPROVERS"));
        verify(approverRepo, never()).save(any());
        verify(approverRepo, never()).deleteById(any());
    }

    /* ---------------------------------------------------------------
     *  Delete — hard delete (master + all detail rows)
     * --------------------------------------------------------------- */

    @Test
    void delete_removesAllDetailRowsThenMaster() {
        when(lineRepo.existsById(new ApprovalLineId(TENANT, 5L))).thenReturn(true);

        service.delete(5L);

        verify(approverRepo).deleteAllByBizKeyAndApprLineNo(TENANT, 5L);
        verify(lineRepo).deleteById(new ApprovalLineId(TENANT, 5L));
    }

    @Test
    void delete_missingMaster_throwsNotFound_doesNotDelete() {
        when(lineRepo.existsById(new ApprovalLineId(TENANT, 99L))).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo("WBE_APPROVAL_LINE_NOT_FOUND"));
        verify(approverRepo, never()).deleteAllByBizKeyAndApprLineNo(any(), any());
        verify(lineRepo, never()).deleteById(any());
    }

    /* ---------------------------------------------------------------
     *  Fixtures
     * --------------------------------------------------------------- */

    private static ApprovalLineApproverInput input(String id, String nm) {
        return new ApprovalLineApproverInput(id, nm, null, null);
    }

    private static ApprovalLine line(long no, String nm) {
        ApprovalLine l = new ApprovalLine();
        l.setBizKey(TENANT);
        l.setApprLineNo(no);
        l.setApprLineNm(nm);
        l.setRegId(USER);
        l.setRegDtm("20260101000000");
        l.setModId(USER);
        l.setModDtm("20260101000000");
        return l;
    }

    private static ApprLineInfmDetl detail(long no, int ord, String id, String nm) {
        ApprLineInfmDetl a = new ApprLineInfmDetl();
        a.setBizKey(TENANT);
        a.setApprLineNo(no);
        a.setApprvrDispOrd(ord);
        a.setApprvrId(id);
        a.setApprvrNm(nm);
        a.setApprvrPosi("");
        a.setApprvrDept("");
        return a;
    }

    private static ApprLineInfmDetl byId(List<ApprLineInfmDetl> rows, String id) {
        return rows.stream().filter(r -> id.equals(r.getApprvrId())).findFirst().orElseThrow();
    }

    /**
     * Stub the tenant-link lookup so every supplied id resolves to an
     * active {@code USER_BUSI_CONN_INFM} link with no stored image. The
     * same method backs both {@code verifyTenantLinks} and
     * {@code resolveImageUrls}; {@code lenient()} avoids strict-stubbing
     * failures on the error-path tests that never reach the look-up.
     */
    private void linkActive(String... ids) {
        List<UserBusiConnInfm> links = new ArrayList<>();
        for (String id : ids) {
            UserBusiConnInfm link = mock(UserBusiConnInfm.class);
            lenient().when(link.getUserId()).thenReturn(id);
            lenient().when(link.getUserImg()).thenReturn(null);
            links.add(link);
        }
        lenient().when(userBusiRepo.findAllByUserIdInAndBizKeyAndDelYn(any(), eq(TENANT), eq("N")))
                .thenReturn(links);
    }
}
