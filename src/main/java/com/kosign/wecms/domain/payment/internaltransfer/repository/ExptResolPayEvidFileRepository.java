package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolPayEvidFile;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolPayEvidFileId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only Spring Data repository for {@link ExptResolPayEvidFile}
 * (legacy {@code EXPT_RESOL_PAY_EVID_FILE}). Every caller constrains on
 * {@code BIZ_KEY} via {@code TenantContext.currentBizKey()}. The refactor
 * never writes this table — rows originate from the live JEX payment flow.
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
}
