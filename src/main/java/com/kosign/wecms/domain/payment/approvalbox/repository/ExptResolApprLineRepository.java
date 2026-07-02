package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprLine;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprLineId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository over the existing {@code EXPT_RESOL_APPR_LINE} table (Flyway
 * {@code V55}). Written by the Request Approval flow — see
 * {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService#writeApprovalLineSnapshot}.
 */
public interface ExptResolApprLineRepository extends JpaRepository<ExptResolApprLine, ExptResolApprLineId> {

    /**
     * Physically delete the approval-line print-header snapshot rows for one
     * approval reference. The withdraw path calls this so the snapshot does
     * not survive as an orphan after {@code EXPT_RESOL_MSTR} is removed
     * (mirrors the {@code EXPT_RESOL_APPR_LINE} leg of the legacy
     * {@code ExptResolMstrDAO.deleteByPk} cascade). Always pass
     * {@code TenantContext.currentBizKey()}.
     */
    @Modifying
    @Query("""
           DELETE FROM ExptResolApprLine a
           WHERE a.bizKey = :bizKey AND a.apprSeqNo = :apprSeqNo
           """)
    int deleteByBizKeyAndApprSeqNo(@Param("bizKey") String bizKey,
                                   @Param("apprSeqNo") String apprSeqNo);
}
