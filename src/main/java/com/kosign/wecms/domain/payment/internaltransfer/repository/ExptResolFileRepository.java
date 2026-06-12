package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFileId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Withdraw cleanup — delete every approval attachment row (legacy
     *  {@code comm_0192_004_d001} deletes {@code EXPT_RESOL_FILE} before
     *  the master row, honouring the {@code expt_resol_file_fk01} FK). */
    @Modifying
    @Query("""
           DELETE FROM ExptResolFile f
           WHERE f.bizKey = :bizKey AND f.apprSeqNo = :apprSeqNo
           """)
    int deleteByBizKeyAndApprSeqNo(@Param("bizKey") String bizKey,
                                   @Param("apprSeqNo") String apprSeqNo);
}
