package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolPayEvidFile;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolPayEvidFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared resolver for Approval Details attachment <em>downloads</em> —
 * routes by the {@code fileType} discriminator carried on
 * {@link ApprovalAttachmentRow} to the correct legacy table:
 * <ul>
 *   <li>{@code EXPT_RESOL_FILE} (requester uploads) — id column {@code FILE_ID}</li>
 *   <li>{@code EXPT_RESOL_PAY_EVID_FILE} (payment evidence) — id column {@code EVID_FILE_ID}</li>
 * </ul>
 *
 * <p>The two tables can hold the same short id (e.g. {@code "1"}) for the
 * same {@code APPR_SEQ_NO}, so the lookup keys on
 * {@code (BIZ_KEY, APPR_SEQ_NO, FILE_ID, fileType)} together — picking the
 * wrong table would otherwise stream the wrong file. Tenant scope is the
 * caller's responsibility: pass {@code bizKey} from
 * {@code TenantContext.currentBizKey()} (never a client value).
 *
 * <p>Read-only. The binary lives under
 * {@code <storage-root>/<biz_key>/approval-attachments/<appr_seq_no>/<save_file_nm>}
 * (resolved by {@link InternalTransferFileResolver}). When the row exists
 * but the on-disk file is absent (e.g. a legacy payment-evidence file that
 * was never copied into the local store), the resolver surfaces
 * {@code WBE2069} — the same "file unavailable" envelope used elsewhere.
 *
 * <p>Used by the cross-type Approval Box download endpoint and (via
 * delegation) by the Internal-Transfer module's own download endpoint, so
 * the table-routing logic lives in exactly one place.
 */
@Service
@RequiredArgsConstructor
public class ApprovalAttachmentDownloadService {

    private final ExptResolFileRepository        exptResolFileRepository;
    private final ExptResolPayEvidFileRepository exptResolPayEvidFileRepository;
    private final InternalTransferFileResolver   fileResolver;

    /** Disk path + display filename for one attachment, ready for the
     *  controller to compose {@code Content-Disposition} + stream. */
    public record DownloadFile(Path path, String orgFileNm, String saveFileNm, long fileSize) { }

    /**
     * Resolve one approval attachment by
     * {@code (bizKey, apprSeqNo, fileId, fileType)}.
     *
     * @param fileType {@link ApprovalAttachmentRow#TYPE_PAY_EVID_FILE} routes
     *                 to {@code EXPT_RESOL_PAY_EVID_FILE}; anything else
     *                 (including {@code null} / blank / unknown) defaults
     *                 safely to {@code EXPT_RESOL_FILE} — legacy behaviour.
     * @throws BusinessException {@code WBE2069} when the row is missing in
     *                 the resolved table (wrong tenant / wrong table / wrong
     *                 id) or the on-disk binary is unreadable.
     */
    @Transactional(readOnly = true)
    public DownloadFile resolve(String bizKey, String apprSeqNo, String fileId, String fileType) {
        String orgFileNm;
        String saveFileNm;
        String storedSize;

        if (ApprovalAttachmentRow.TYPE_PAY_EVID_FILE.equals(fileType)) {
            ExptResolPayEvidFile row = exptResolPayEvidFileRepository
                    .findByBizKeyAndApprSeqNoAndEvidFileId(bizKey, apprSeqNo, fileId)
                    .orElseThrow(() -> new BusinessException("WBE2069"));
            orgFileNm  = row.getOrgFileNm();
            saveFileNm = row.getSaveFileNm();
            storedSize = row.getFileSize();
        } else {
            ExptResolFile row = exptResolFileRepository
                    .findByBizKeyAndApprSeqNoAndFileId(bizKey, apprSeqNo, fileId)
                    .orElseThrow(() -> new BusinessException("WBE2069"));
            orgFileNm  = row.getOrgFileNm();
            saveFileNm = row.getSaveFileNm();
            storedSize = row.getFileSize();
        }

        Path path = fileResolver.resolveApprovalAttachment(bizKey, apprSeqNo, saveFileNm);
        if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("WBE2069");
        }
        long fileSize = storedSize == null
                ? path.toFile().length()
                : Long.parseLong(storedSize);
        return new DownloadFile(path, orgFileNm, saveFileNm, fileSize);
    }
}
