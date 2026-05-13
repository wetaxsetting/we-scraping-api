package com.kosign.wecms.domain.setting.approvalline.repository;

import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineApprover;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineApproverId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Repository for {@link ApprovalLineApprover} (legacy
 * {@code APPR_LINE_INFM_DETL}). Tenant-scoped on every method.
 *
 * <p>List page batching: callers load all detail rows for a set of
 * parent {@code APPR_LINE_NO}s in one IN-query, then group in-memory.
 * Per-line detail loads use the single-line variant.
 */
public interface ApprovalLineApproverRepository
        extends JpaRepository<ApprovalLineApprover, ApprovalLineApproverId> {

    /** Detail rows for one approval line, in display order. */
    List<ApprovalLineApprover> findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(
            String bizKey, Long apprLineNo);

    /** Batch fetch for the list endpoint &mdash; one IN-query covers
     *  every parent on the page. Ordered so the in-memory group-by
     *  produces correctly-ordered chains without a sort pass. */
    List<ApprovalLineApprover> findAllByBizKeyAndApprLineNoInOrderByApprLineNoAscApprvrDispOrdAsc(
            String bizKey, Collection<Long> apprLineNos);

    /**
     * Delete every detail row for one approval line in a single DELETE
     * statement. Used by the parent {@code delete(...)} flow (mirrors
     * legacy {@code ApprLineInfmDetlDAO.deleteByFk}). Per-row deletes
     * during diff-sync go through the standard {@code deleteById(...)}
     * on the inherited {@link JpaRepository}.
     */
    @Modifying
    @Query("delete from ApprovalLineApprover a "
            + "where a.bizKey = :bizKey and a.apprLineNo = :apprLineNo")
    void deleteAllByBizKeyAndApprLineNo(
            @Param("bizKey") String bizKey,
            @Param("apprLineNo") Long apprLineNo);
}
