package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetl;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetlId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository over the existing {@code EXPT_RESOL_DETL} table (Flyway
 * {@code V57}). Written by the Request Approval flow — see
 * {@link com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService#writeResolutionDetail}.
 */
public interface ExptResolDetlRepository extends JpaRepository<ExptResolDetl, ExptResolDetlId> {

    /**
     * All resolution-detail rows for one approval (read-only). For the
     * scheduled / overseas kinds the Request Approval flow writes a single
     * row carrying {@code DOC_ID} = the source payment-batch business key
     * ({@code SCDUL_PYMN_NO} / {@code OVRS_PYMN_NO}); {@code ApprovalDetailService}
     * reads {@code DOC_ID} to bridge {@code APPR_SEQ_NO} → the batch's line
     * transactions. Tenant scope ({@code BIZ_KEY}) is the caller's
     * responsibility — always pass {@code TenantContext.currentBizKey()}.
     */
    List<ExptResolDetl> findByBizKeyAndApprSeqNo(String bizKey, String apprSeqNo);
}
