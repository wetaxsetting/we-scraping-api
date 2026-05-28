package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFileId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link ExptResolFile}. All callers
 * constrain on {@code BIZ_KEY} via {@code TenantContext.currentBizKey()}
 * — every read/write is tenant-scoped.
 */
public interface ExptResolFileRepository
        extends JpaRepository<ExptResolFile, ExptResolFileId> {

    /** All attachments registered against an approval batch, ordered
     *  by upload sequence ({@code FILE_ID} stamped monotonically by
     *  the service). Drives the Approval Details popup's file list. */
    List<ExptResolFile> findByBizKeyAndApprSeqNoOrderByFileIdAsc(
            String bizKey, String apprSeqNo);

    /** Single-attachment lookup for the download endpoint. The PK is
     *  composite so this is a metamodel-derived query. */
    Optional<ExptResolFile> findByBizKeyAndApprSeqNoAndFileId(
            String bizKey, String apprSeqNo, String fileId);
}
