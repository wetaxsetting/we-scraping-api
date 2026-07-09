package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolPayEvidFile;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolPayEvidFileId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link ExptResolPayEvidFile}
 * (legacy {@code EXPT_RESOL_PAY_EVID_FILE}). Reads originate from the live
 * JEX payment flow; the only write the refactor performs is the withdraw-time
 * physical delete (see {@link #deleteByBizKeyAndApprSeqNo}). Every caller
 * constrains on {@code BIZ_KEY} via {@code TenantContext.currentBizKey()}.
 */
public interface ExptResolPayEvidFileRepository
        extends JpaRepository<ExptResolPayEvidFile, ExptResolPayEvidFileId> {

    /** All payment-evidence attachments for an approval batch, ordered by
     *  the legacy {@code EVID_FILE_ID}. Combined with
     *  {@code EXPT_RESOL_FILE} by {@code ApprovalAttachmentLoader} to mirror
     *  the legacy {@code IDO.EXPT_RESOL_FILE_R002} UNION. */
    List<ExptResolPayEvidFile> findByBizKeyAndApprSeqNoOrderByEvidFileIdAsc(
            String bizKey, String apprSeqNo);

    /** Single-attachment lookup for the fileType-routed download path. */
    Optional<ExptResolPayEvidFile> findByBizKeyAndApprSeqNoAndEvidFileId(
            String bizKey, String apprSeqNo, String evidFileId);

    /**
     * Physically delete every payment-evidence attachment row for one
     * approval reference. Required by the withdraw path so the
     * {@code EXPT_RESOL_MSTR} delete does not violate
     * {@code expt_resol_pay_evid_file_fk01} (the FK has no
     * {@code ON DELETE CASCADE} — legacy parity; the cascade is done in
     * application code, mirroring {@code ExptResolMstrDAO.deleteByPk}).
     * Always pass {@code TenantContext.currentBizKey()}.
     */
    @Modifying
    @Query("""
           DELETE FROM ExptResolPayEvidFile f
           WHERE f.bizKey = :bizKey AND f.apprSeqNo = :apprSeqNo
           """)
    int deleteByBizKeyAndApprSeqNo(@Param("bizKey") String bizKey,
                                   @Param("apprSeqNo") String apprSeqNo);
}
