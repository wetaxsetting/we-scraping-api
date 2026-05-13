package com.kosign.wecms.domain.setting.chartofaccounts.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountCreateRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountListResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountUpdateRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccountId;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer coverage for Settings &gt; Chart of Accounts. Uses
 * pure Mockito (no Spring context) so the tests stay fast and the
 * scope is just the service's collaborator orchestration.
 *
 * <p>Mirrors {@code ContactsServiceTest} and {@code ApprovalLineService}'s
 * style: every test sets up {@link TenantContext}, exercises one
 * method, and asserts both the repository / audit interactions and
 * the response shape.
 */
@ExtendWith(MockitoExtension.class)
class ChartOfAccountsServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private ChartOfAccountRepository repo;
    @Mock private LegacyFunctionRepository legacyFnRepo;
    @Mock private AuditLogService          auditLog;

    @InjectMocks
    private ChartOfAccountsService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* =================================================================
     *  list
     * ================================================================= */

    @Test
    void list_returnsProjectedRowsWithRestrictedFlag() {
        ChartOfAccount root  = entity("70", "ACC-01",  "REVENUE",      null,    "A");
        ChartOfAccount child = entity("99", "1007009", "Collection 4", "ACC-01","A");
        when(repo.findByBizKeyAndDelYn(eq(TENANT), eq("N"), any(Sort.class)))
                .thenReturn(List.of(root, child));

        ChartOfAccountListResponse res = service.list(null, null, null);

        assertThat(res.totalCount()).isEqualTo(2);
        assertThat(res.items()).hasSize(2);
        assertThat(res.items().get(0).restricted()).isTrue();      // ACC-01
        assertThat(res.items().get(0).chartAcctLevel()).isEqualTo(1);
        assertThat(res.items().get(0).hasChildren()).isTrue();
        assertThat(res.items().get(1).restricted()).isFalse();     // 1007009
        assertThat(res.items().get(1).chartAcctLevel()).isEqualTo(2);
        assertThat(res.items().get(1).hasChildren()).isFalse();
        assertThat(res.restrictedCodes()).containsExactlyInAnyOrder("ACC-01", "ACC-02", "ACC-03");
    }

    @Test
    void list_withSearchUsesSearchRepoMethod() {
        when(repo.searchByBizKey(eq(TENANT), eq("N"), eq("Collection"), any(Sort.class)))
                .thenReturn(List.of());

        service.list("  Collection  ", null, null);

        verify(repo).searchByBizKey(eq(TENANT), eq("N"), eq("Collection"), any(Sort.class));
        verify(repo, never()).findByBizKeyAndDelYn(any(), any(), any());
    }

    /* =================================================================
     *  detail
     * ================================================================= */

    @Test
    void detail_notFoundThrowsBusinessException() {
        when(repo.findById(new ChartOfAccountId(TENANT, "99"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail("99"))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_NOT_FOUND");
    }

    /* =================================================================
     *  create
     * ================================================================= */

    @Test
    void create_happyPathAllocatesSeqAndWritesAudit() {
        // Tenant slice for validator: just the parent.
        ChartOfAccount parent = entity("70", "ACC-01", "REVENUE", null, "A");
        when(repo.findByBizKeyAndDelYn(eq(TENANT), eq("N"), any(Sort.class)))
                .thenReturn(List.of(parent));
        when(repo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "1007009", "N"))
                .thenReturn(Optional.empty());
        when(repo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "1007009", "Y"))
                .thenReturn(Optional.empty());
        when(legacyFnRepo.nextChartAcctSeqNo(TENANT)).thenReturn("113");
        when(repo.save(any(ChartOfAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChartOfAccountCreateRequest req = new ChartOfAccountCreateRequest(
                "1007009", "Collection 4", "회수4", "회수4",
                null, "ACC-01", null, "A");

        service.create(req);

        ArgumentCaptor<ChartOfAccount> saved = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(repo, times(1)).save(saved.capture());
        ChartOfAccount inserted = saved.getValue();
        assertThat(inserted.getBizKey()).isEqualTo(TENANT);
        assertThat(inserted.getChartAcctSeqNo()).isEqualTo("113");
        assertThat(inserted.getChartAcctCd()).isEqualTo("1007009");
        assertThat(inserted.getDelYn()).isEqualTo("N");
        assertThat(inserted.getStts()).isEqualTo("A");
        assertThat(inserted.getRegId()).isEqualTo(USER);

        ArgumentCaptor<AuditLogContext> auditCtx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLog).append(auditCtx.capture());
        assertThat(auditCtx.getValue().wsvcId()).isEqualTo("chart-of-accounts");
        assertThat(auditCtx.getValue().detailKey()).isEqualTo("audit.chart_of_accounts.create.detail");
    }

    @Test
    void create_duplicateActiveCodeRejected() {
        ChartOfAccount parent = entity("70", "ACC-01", "REVENUE", null, "A");
        ChartOfAccount dupActive = entity("99", "1007009", "Collection 4", "ACC-01", "A");
        when(repo.findByBizKeyAndDelYn(eq(TENANT), eq("N"), any(Sort.class)))
                .thenReturn(List.of(parent, dupActive));
        when(repo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "1007009", "N"))
                .thenReturn(Optional.of(dupActive));

        ChartOfAccountCreateRequest req = new ChartOfAccountCreateRequest(
                "1007009", "Collection 4", null, null, null, "ACC-01", null, "A");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE");
        verify(repo, never()).save(any());
        verify(auditLog, never()).append(any());
    }

    @Test
    void create_revivesSoftDeletedRow() {
        ChartOfAccount parent = entity("70", "ACC-01", "REVENUE", null, "A");
        ChartOfAccount deleted = entity("88", "1007009", "Collection 4 (old)", "ACC-01", "I");
        deleted.setDelYn("Y");
        when(repo.findByBizKeyAndDelYn(eq(TENANT), eq("N"), any(Sort.class)))
                .thenReturn(List.of(parent));
        when(repo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "1007009", "N"))
                .thenReturn(Optional.empty());
        when(repo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "1007009", "Y"))
                .thenReturn(Optional.of(deleted));
        when(repo.save(any(ChartOfAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ChartOfAccountCreateRequest req = new ChartOfAccountCreateRequest(
                "1007009", "Collection 4 (revived)", null, null, null, "ACC-01", null, "A");

        service.create(req);

        // legacy seq-no allocator never called — we revived the existing row.
        verify(legacyFnRepo, never()).nextChartAcctSeqNo(any());
        ArgumentCaptor<ChartOfAccount> saved = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(repo).save(saved.capture());
        ChartOfAccount revived = saved.getValue();
        assertThat(revived.getChartAcctSeqNo()).isEqualTo("88"); // preserved
        assertThat(revived.getDelYn()).isEqualTo("N");           // un-deleted
        assertThat(revived.getChartAcctNmEn()).isEqualTo("Collection 4 (revived)");
    }

    @Test
    void create_pkRaceRetriesOnceThenFailsOnDuplicate() {
        ChartOfAccount parent = entity("70", "ACC-01", "REVENUE", null, "A");
        when(repo.findByBizKeyAndDelYn(eq(TENANT), eq("N"), any(Sort.class)))
                .thenReturn(List.of(parent));
        when(repo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "1007009", "N"))
                .thenReturn(Optional.empty());
        when(repo.findByBizKeyAndChartAcctCdAndDelYn(TENANT, "1007009", "Y"))
                .thenReturn(Optional.empty());
        when(legacyFnRepo.nextChartAcctSeqNo(TENANT))
                .thenReturn("113")
                .thenReturn("114");
        when(repo.save(any(ChartOfAccount.class)))
                .thenThrow(new DataIntegrityViolationException("race"))
                .thenThrow(new DataIntegrityViolationException("still racing"));

        ChartOfAccountCreateRequest req = new ChartOfAccountCreateRequest(
                "1007009", "Collection 4", null, null, null, "ACC-01", null, "A");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE");
        verify(repo, times(2)).save(any());
    }

    /* =================================================================
     *  update
     * ================================================================= */

    @Test
    void update_restrictedRowRejected() {
        ChartOfAccount root = entity("70", "ACC-01", "REVENUE", null, "A");
        when(repo.findById(new ChartOfAccountId(TENANT, "70"))).thenReturn(Optional.of(root));

        ChartOfAccountUpdateRequest req = new ChartOfAccountUpdateRequest(
                "ACC-01-RENAMED", "Revenue Renamed", null, null, null, null, null, "A");

        assertThatThrownBy(() -> service.update("70", req))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE");
        verify(repo, never()).save(any());
        verify(auditLog, never()).append(any());
    }

    @Test
    void update_happyPathSavesAndAudits() {
        ChartOfAccount existing = entity("99", "1007009", "Collection 4", "ACC-01", "A");
        ChartOfAccount root = entity("70", "ACC-01", "REVENUE", null, "A");
        when(repo.findById(new ChartOfAccountId(TENANT, "99"))).thenReturn(Optional.of(existing));
        // Request keeps the same code → the dup-on-rename check is
        // skipped, so no stub for findByBizKeyAndChartAcctCdAndDelYnAndChartAcctSeqNoNot.
        when(repo.findByBizKeyAndDelYn(eq(TENANT), eq("N"), any(Sort.class)))
                .thenReturn(List.of(root, existing));
        when(repo.save(any(ChartOfAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ChartOfAccountUpdateRequest req = new ChartOfAccountUpdateRequest(
                "1007009", "Collection 4 (updated)", null, null, null, "ACC-01", null, "A");

        service.update("99", req);

        ArgumentCaptor<ChartOfAccount> saved = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getChartAcctNmEn()).isEqualTo("Collection 4 (updated)");
        assertThat(saved.getValue().getModId()).isEqualTo(USER);

        ArgumentCaptor<AuditLogContext> auditCtx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLog).append(auditCtx.capture());
        assertThat(auditCtx.getValue().detailKey()).isEqualTo("audit.chart_of_accounts.update.detail");
    }

    /* =================================================================
     *  delete
     * ================================================================= */

    @Test
    void delete_restrictedRowRejected() {
        ChartOfAccount root = entity("70", "ACC-01", "REVENUE", null, "A");
        when(repo.findById(new ChartOfAccountId(TENANT, "70"))).thenReturn(Optional.of(root));

        assertThatThrownBy(() -> service.delete("70"))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE");
        verify(repo, never()).reparentActiveChildren(any(), any(), any(), any(), any());
        verify(auditLog, never()).append(any());
    }

    @Test
    void delete_reparentsChildrenAndFlipsDelYn() {
        ChartOfAccount existing = entity("99", "1007009", "Collection 4", "ACC-01", "A");
        when(repo.findById(new ChartOfAccountId(TENANT, "99"))).thenReturn(Optional.of(existing));
        when(repo.reparentActiveChildren(eq(TENANT), eq("1007009"), eq("ACC-01"), eq(USER), any()))
                .thenReturn(2);
        when(repo.findByBizKeyAndChartAcctCdAndDelYnAndChartAcctSeqNoNot(
                TENANT, "1007009", "Y", "99")).thenReturn(Optional.empty());
        when(repo.save(any(ChartOfAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete("99");

        verify(repo).reparentActiveChildren(eq(TENANT), eq("1007009"), eq("ACC-01"), eq(USER), any());

        ArgumentCaptor<ChartOfAccount> saved = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getDelYn()).isEqualTo("Y");
        assertThat(saved.getValue().getChartAcctCd()).isEqualTo("1007009"); // no archive rename

        ArgumentCaptor<AuditLogContext> auditCtx = ArgumentCaptor.forClass(AuditLogContext.class);
        verify(auditLog).append(auditCtx.capture());
        assertThat(auditCtx.getValue().detailKey()).isEqualTo("audit.chart_of_accounts.delete.detail");
    }

    @Test
    void delete_archiveRenamesWhenSoftDeletedDupExists() {
        ChartOfAccount existing = entity("12345", "DUP-CODE", "Some dup", null, "A");
        ChartOfAccount otherDeleted = entity("999", "DUP-CODE", "Older dup", null, "I");
        otherDeleted.setDelYn("Y");
        when(repo.findById(new ChartOfAccountId(TENANT, "12345"))).thenReturn(Optional.of(existing));
        when(repo.reparentActiveChildren(any(), any(), any(), any(), any())).thenReturn(0);
        when(repo.findByBizKeyAndChartAcctCdAndDelYnAndChartAcctSeqNoNot(
                TENANT, "DUP-CODE", "Y", "12345")).thenReturn(Optional.of(otherDeleted));
        when(repo.save(any(ChartOfAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete("12345");

        ArgumentCaptor<ChartOfAccount> saved = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(repo).save(saved.capture());
        // prefix(0..4) of "DUP-CODE" = "DUP-", last5 of "12345" = "12345"
        // + 'D' suffix → "DUP-12345D".
        assertThat(saved.getValue().getChartAcctCd()).isEqualTo("DUP-12345D");
        assertThat(saved.getValue().getDelYn()).isEqualTo("Y");
    }

    /* ---------------------------------------------------------------
     *  Helpers
     * --------------------------------------------------------------- */

    private static ChartOfAccount entity(String seqNo, String code, String nameEn,
                                          String parentCd, String stts) {
        ChartOfAccount e = new ChartOfAccount();
        e.setBizKey(TENANT);
        e.setChartAcctSeqNo(seqNo);
        e.setChartAcctCd(code);
        e.setChartAcctNmEn(nameEn);
        e.setParentChartAcctCd(parentCd);
        e.setStts(stts);
        e.setDelYn("N");
        e.setRegId(USER);
        e.setRegDtm("20260513000000");
        e.setModId(USER);
        e.setModDtm("20260513000000");
        return e;
    }
}
