package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprLine;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprLineId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository over the existing {@code EXPT_RESOL_APPR_LINE} table (Flyway
 * {@code V55}). Written by the Request Approval flow — see
 * {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService#writeApprovalLineSnapshot}.
 */
public interface ExptResolApprLineRepository extends JpaRepository<ExptResolApprLine, ExptResolApprLineId> {
}
