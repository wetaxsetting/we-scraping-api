package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ExptResolFile;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferFileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * M-1 (legacy parity): shared Request-Approval attachment writer.
 *
 * <p>Persists uploaded attachments into {@code EXPT_RESOL_FILE} for every
 * Payment module's Request Approval flow — the legacy
 * {@code processFileAttachments} (REC1) behaviour at
 * {@code comm_0192_001_c005_act.jsp:459-484} (scheduled / overseas) and
 * {@code card_0001_005_c001_act.jsp:399-423} (Corporate Card).
 *
 * <p>Extracted verbatim from the Internal Transfer implementation
 * ({@code InternalTransferService.persistApprovalAttachments}) so all six
 * payment types share one code path. Behaviour is identical:
 * <ul>
 *   <li>files land under
 *       {@code <storage-root>/<biz_key>/approval-attachments/<appr_seq_no>/}
 *       (via {@link InternalTransferFileResolver});</li>
 *   <li>{@code FILE_ID} is seeded once per request from the
 *       {@code wb_get_next_val('expt_resol_file','file_id', biz_key, appr_seq_no)}
 *       funnel, then incremented in Java across the batch;</li>
 *   <li>each file's original name is sanitised; the on-disk name is
 *       {@code <appr_seq_no>_<file_id>_<org_file_nm>};</li>
 *   <li>one {@code EXPT_RESOL_FILE} row per file.</li>
 * </ul>
 *
 * <p>Runs inside the caller's {@code @Transactional} boundary (no annotation
 * here) so a partial failure rolls back the approval atomically. A null /
 * empty attachment list is the no-attachment path — a no-op, preserving the
 * JSON-only (no-file) Request Approval contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalAttachmentWriter {

    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ExptResolFileRepository      exptResolFileRepository;
    private final InternalTransferFileResolver fileResolver;
    private final LegacyFunctionRepository     legacyFunctions;
    private final java.time.Clock              clock;

    /**
     * Stream each attachment to disk and insert one {@code EXPT_RESOL_FILE}
     * row per file. No-op when {@code attachments} is null / empty.
     *
     * @param bizKey     current tenant (from {@code TenantContext}).
     * @param apprSeqNo  the freshly-allocated approval sequence the files
     *                   attach to (one folder per approval).
     * @param attachments uploaded multipart files; may be null / empty.
     */
    public void persist(String bizKey, String apprSeqNo, List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        String nowDtm = ZonedDateTime.ofInstant(clock.instant(), VIETNAM_ZONE).format(DTM14);

        Path apprDir = fileResolver.approvalAttachmentDirectory(bizKey, apprSeqNo);
        try {
            Files.createDirectories(apprDir);
        } catch (IOException ex) {
            log.error("feature=payment request-approval attachment-mkdir-failed bizKey={} apprSeqNo={} err={}",
                    bizKey, apprSeqNo, ex.getMessage());
            throw new BusinessException("WBE2070");
        }

        // FILE_ID via the wb_get_next_val funnel (per (biz_key, appr_seq_no) MAX+1).
        // Seed ONCE per request, then increment in Java across the batch — no
        // per-file DB round-trip. apprSeqNo is freshly allocated, so the seed is 1.
        long fileIdCursor = Long.parseLong(legacyFunctions.nextExptResolFileId(bizKey, apprSeqNo)) - 1;
        for (MultipartFile file : attachments) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fileId    = Long.toString(++fileIdCursor);
            String orgFileNm = sanitiseOriginalFilename(file.getOriginalFilename());
            String saveFileNm = apprSeqNo + "_" + fileId + "_" + orgFileNm;
            Path target = fileResolver.resolveApprovalAttachment(bizKey, apprSeqNo, saveFileNm);
            try {
                file.transferTo(target);
            } catch (IOException ex) {
                log.error("feature=payment request-approval attachment-write-failed bizKey={} apprSeqNo={} fileId={} err={}",
                        bizKey, apprSeqNo, fileId, ex.getMessage());
                throw new BusinessException("WBE2070");
            }

            ExptResolFile row = new ExptResolFile();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setFileId(fileId);
            row.setOrgFileNm(orgFileNm);
            row.setSaveFileNm(saveFileNm);
            row.setFileSize(Long.toString(file.getSize()));
            row.setRegId(userId);
            row.setRegNm(userNm == null ? userId : userNm);
            row.setRegDtm(nowDtm);
            exptResolFileRepository.save(row);
        }
    }

    /** Strip directory components + shell metacharacters from an uploaded
     *  filename (mirrors the legacy {@code FileUtils.safeName} behaviour and
     *  the Internal Transfer implementation). */
    static String sanitiseOriginalFilename(String original) {
        if (original == null || original.isBlank()) return "upload.xlsx";
        String leaf = original;
        int sep = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (sep >= 0) leaf = leaf.substring(sep + 1);
        leaf = leaf.replaceAll("[^A-Za-z0-9._\\- (),]", "_");
        return leaf.isBlank() ? "upload.xlsx" : leaf;
    }
}
