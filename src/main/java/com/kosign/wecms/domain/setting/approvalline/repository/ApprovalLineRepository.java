package com.kosign.wecms.domain.setting.approvalline.repository;

import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLine;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link ApprovalLine}. Every method that returns rows is
 * tenant-scoped &mdash; callers supply {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 *
 * <p>The list query uses {@code IgnoreCase} which Spring Data emits as
 * {@code LOWER(...) LIKE LOWER(...)} on Postgres &mdash; same semantic
 * as the legacy {@code ILIKE} predicate at
 * {@code comm_0222_001_r001_act.jsp:43-46}. Empty {@code q} short-
 * circuits to {@link #findByBizKey}.
 */
public interface ApprovalLineRepository
        extends JpaRepository<ApprovalLine, ApprovalLineId> {

    /** No-filter list for the current tenant. */
    List<ApprovalLine> findByBizKey(String bizKey, Sort sort);

    /** Name-substring search (case-insensitive) for the current tenant.
     *  Mirrors legacy {@code APPR_LINE_NM ilike '%' || ? || '%'}. */
    List<ApprovalLine> findByBizKeyAndApprLineNmContainingIgnoreCase(
            String bizKey, String q, Sort sort);
}
