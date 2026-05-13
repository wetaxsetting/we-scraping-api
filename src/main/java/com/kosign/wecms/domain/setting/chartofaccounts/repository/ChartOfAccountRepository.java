package com.kosign.wecms.domain.setting.chartofaccounts.repository;

import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccountId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ChartOfAccount}. Every method that returns rows is
 * tenant-scoped — callers supply {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 *
 * <p>Search predicates use {@code ContainingIgnoreCase} which Spring Data
 * emits as {@code LOWER(...) LIKE LOWER(...)} on Postgres — semantically
 * equivalent to the legacy ILIKE at
 * {@code comm_0223_001_r001_act.jsp:37-46}.
 *
 * <p>Soft-delete is enforced via the {@code DEL_YN} parameter on every
 * finder — pass {@code "N"} for active rows, {@code "Y"} for the
 * deleted-archive lookup used by the Create-revive path.
 */
public interface ChartOfAccountRepository
        extends JpaRepository<ChartOfAccount, ChartOfAccountId> {

    /** All rows for the current tenant matching the soft-delete flag.
     *  Used by the no-filter list query. */
    List<ChartOfAccount> findByBizKeyAndDelYn(String bizKey, String delYn, Sort sort);

    /** Combined code/name search for the current tenant. Mirrors the
     *  legacy ILIKE clause on {@code CHART_ACCT_CD} OR
     *  {@code CHART_ACCT_NM_EN}. The query name + parameter style
     *  reads slightly awkwardly because Spring Data's "Or" expansion
     *  applies the search arg to a single field — using
     *  {@link Query} keeps the SQL legible. */
    @Query("""
            SELECT c FROM ChartOfAccount c
             WHERE c.bizKey = :bizKey
               AND c.delYn  = :delYn
               AND (LOWER(c.chartAcctCd)     LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.chartAcctNmEn)   LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    List<ChartOfAccount> searchByBizKey(@Param("bizKey") String bizKey,
                                        @Param("delYn") String delYn,
                                        @Param("search") String search,
                                        Sort sort);

    /** Lookup by code + soft-delete flag in the current tenant. Used by:
     *  Create dup-check (delYn='N'), Create revive lookup (delYn='Y'),
     *  Update dup-on-rename, Delete archive-collision check. The UK
     *  {@code (BIZ_KEY, CHART_ACCT_CD, DEL_YN)} indexes this exactly. */
    Optional<ChartOfAccount> findByBizKeyAndChartAcctCdAndDelYn(
            String bizKey, String chartAcctCd, String delYn);

    /** Same as above but with an explicit exclude on a known seq_no —
     *  used by the Update dup-on-rename check (so the row being edited
     *  doesn't trip the check against itself) and by the Delete
     *  archive-collision check (so the row being deleted is not the
     *  collision target). */
    Optional<ChartOfAccount> findByBizKeyAndChartAcctCdAndDelYnAndChartAcctSeqNoNot(
            String bizKey, String chartAcctCd, String delYn, String chartAcctSeqNo);

    /** Active rows whose {@code PARENT_CHART_ACCT_CD} matches the
     *  supplied code in the current tenant. Powers the Delete
     *  re-parent step (children become the deleting node's parent)
     *  and the {@code has_children} projection in the list / tree
     *  responses. */
    List<ChartOfAccount> findByBizKeyAndParentChartAcctCdAndDelYn(
            String bizKey, String parentChartAcctCd, String delYn);

    /** Bulk re-parent every active child of {@code oldParentCd} to
     *  {@code newParentCd} (which may be {@code null} when the
     *  deleting node was itself a root). Single statement keeps the
     *  Delete transaction lean even for a node with many children. */
    @Modifying
    @Query("""
            UPDATE ChartOfAccount c
               SET c.parentChartAcctCd = :newParentCd,
                   c.modId  = :modId,
                   c.modDtm = :modDtm
             WHERE c.bizKey = :bizKey
               AND c.parentChartAcctCd = :oldParentCd
               AND c.delYn = 'N'
           """)
    int reparentActiveChildren(@Param("bizKey") String bizKey,
                               @Param("oldParentCd") String oldParentCd,
                               @Param("newParentCd") String newParentCd,
                               @Param("modId") String modId,
                               @Param("modDtm") String modDtm);
}
