package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetl;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetlId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository over the existing {@code EXPT_RESOL_DETL} table (Flyway
 * {@code V57}). Written by the Request Approval flow — see
 * {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService#writeResolutionDetail}.
 */
public interface ExptResolDetlRepository extends JpaRepository<ExptResolDetl, ExptResolDetlId> {
}
