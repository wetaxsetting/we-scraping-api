package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetl;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Physically delete every resolution-detail row for one approval
     * reference. Required by the withdraw path so the {@code EXPT_RESOL_MSTR}
     * delete does not violate {@code expt_resol_detl_fk01} (the FK has no
     * {@code ON DELETE CASCADE} — legacy parity; the cascade is done in
     * application code, mirroring {@code ExptResolMstrDAO.deleteByPk}).
     * Always pass {@code TenantContext.currentBizKey()}.
     */
    @Modifying
    @Query("""
           DELETE FROM ExptResolDetl d
           WHERE d.bizKey = :bizKey AND d.apprSeqNo = :apprSeqNo
           """)
    int deleteByBizKeyAndApprSeqNo(@Param("bizKey") String bizKey,
                                   @Param("apprSeqNo") String apprSeqNo);
}
