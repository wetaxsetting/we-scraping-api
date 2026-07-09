package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.dto.EditApprovalRequest;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Shared "Edit approval" command — the refactor of legacy
 * {@code comm_0192_013_u002} ({@code comm_0195_001.ts editApproval()}). One
 * transactional call mirrors the legacy save: patch the title + content on
 * {@code EXPT_RESOL_MSTR}, delete removed attachments (REC4) and add new ones
 * (REC3) on {@code EXPT_RESOL_FILE} / {@code EXPT_RESOL_PAY_EVID_FILE}, and —
 * when a new approval line is picked — rotate the approver chain (REC1/REC2)
 * via {@link ApprovalBoxFanoutService#changeApprovalLine}.
 *
 * <p>Consumed by BOTH the Approval Box and Corporate Card edit endpoints. The
 * entire edit surface lives on the shared approval tables (all keyed by
 * {@code APPR_SEQ_NO}), so there is no card-specific branch — legacy parity:
 * {@code u002} has none. Each caller passes its own {@code auditWsvcId} slug so
 * the audit row carries the right feature discriminator
 * ({@code approval-box} vs {@code card-transactions}).
 *
 * <p>Guards (legacy {@code comm_0195_001.ts:909-926} parity):
 * <ul>
 *   <li>master must exist for the tenant ({@code WBE2050});</li>
 *   <li>status must be Pending ({@code 00}) or In Progress ({@code 03}) —
 *       finalised documents are not editable;</li>
 *   <li>the caller must be the requestor ({@code REG_ID} / {@code DRAFT_USER_ID})
 *       or an admin ({@code WBE1003} otherwise).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEditService {

    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Legacy {@code APPR_TYPE_CD} values that permit editing. */
    private static final String APPR_TYPE_PENDING     = "00";
    private static final String APPR_TYPE_IN_PROGRESS = "03";

    private static final String AUDIT_ACTIVITY_KEY = "audit.approval_box.activity";
    private static final String AUDIT_EDIT_DETAIL  = "audit.approval_box.edit.detail";
    private static final String REF_NO_EXPT_RESOL_MSTR = "EXPT_RESOL_MSTR";

    private final ExptResolMstrRepository  masterRepository;
    private final ApprovalBoxFanoutService fanoutService;
    private final ApprovalAttachmentWriter approvalAttachmentWriter;
    private final AuditLogService          auditLogService;

    /**
     * Apply an Approval Details edit.
     *
     * @param apprSeqNo    the approval to edit ({@code EXPT_RESOL_MSTR} PK).
     * @param req          title / content / picked line / deleted attachments
     *                     (the multipart {@code request} JSON part).
     * @param attachments  newly-uploaded files (multipart {@code attachments}
     *                     parts); may be null / empty.
     * @param auditWsvcId  caller's audit slug — {@code "approval-box"} or
     *                     {@code "card-transactions"}.
     */
    @Transactional
    public void edit(String apprSeqNo,
                     EditApprovalRequest req,
                     List<MultipartFile> attachments,
                     String auditWsvcId) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "appr_seq_no: is required");
        }
        String seq = apprSeqNo.trim();

        String  bizKey  = TenantContext.currentBizKey();
        String  userId  = TenantContext.currentUserId();
        boolean isAdmin = TenantContext.currentIsAdmin();
        if (bizKey == null || userId == null) {
            throw new BusinessException("WBE9999");
        }

        ExptResolMstr master = masterRepository
                .findById(new ExptResolMstrId(bizKey, seq))
                .orElseThrow(() -> {
                    log.warn("approval-edit target missing bizKey={} apprSeqNo={}", bizKey, seq);
                    return new BusinessException("WBE2050");
                });

        // Status guard — only Pending / In Progress documents are editable
        // (mirrors ApprovalBoxFanoutService.changeApprovalLine + the
        // frontend Pending-only kebab gate).
        String typeCd = master.getApprTypeCd();
        if (!APPR_TYPE_PENDING.equals(typeCd) && !APPR_TYPE_IN_PROGRESS.equals(typeCd)) {
            throw new BusinessException("WBE_VALIDATION",
                    "Only Pending or In Progress approvals can be edited");
        }

        // Requestor / admin guard — legacy comm_0195_001.ts:913-926
        // (REG_ID == _user_id, or _isAdmin). DRAFT_USER_ID is accepted too so
        // the backend never rejects a requestor the frontend kebab allowed
        // (the wire row keys the requestor on draft_user_id).
        boolean isRequestor = userId.equals(master.getRegId())
                || userId.equals(master.getDraftUserId());
        if (!isAdmin && !isRequestor) {
            log.warn("approval-edit not requestor/admin bizKey={} apprSeqNo={} userId={}",
                    bizKey, seq, userId);
            throw new BusinessException("WBE1003");
        }

        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);

        // Title + content (legacy POST_TITLE / POST_CONT → TITL / CONT). Null
        // leaves the column untouched; the frontend always sends both.
        if (req != null) {
            if (req.title() != null) {
                master.setTitl(req.title());
            }
            if (req.cont() != null) {
                master.setCont(req.cont());
            }
        }
        master.setModId(userId);
        master.setModDtm(now);
        masterRepository.save(master);

        // Attachments — delete removed (REC4) then add new (REC3).
        if (req != null && req.deletedAttachments() != null && !req.deletedAttachments().isEmpty()) {
            List<ApprovalAttachmentWriter.FileRef> refs = req.deletedAttachments().stream()
                    .map(d -> new ApprovalAttachmentWriter.FileRef(d.fileId(), d.fileType()))
                    .toList();
            approvalAttachmentWriter.deleteByRefs(bizKey, seq, refs);
        }
        approvalAttachmentWriter.persist(bizKey, seq, attachments);

        // Approver line (REC1/REC2) — only when the user picked a new line.
        // changeApprovalLine reloads the same managed master, rebuilds the
        // chain, and resets APPR_TYPE_CD to Pending. The title/content set
        // above persist on the same instance.
        if (req != null && req.apprLineNo() != null && req.apprLineNo() > 0) {
            fanoutService.changeApprovalLine(seq, req.apprLineNo());
        }

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(auditWsvcId)
                .refNo(REF_NO_EXPT_RESOL_MSTR)
                .refNo1(seq)
                .activityKey(AUDIT_ACTIVITY_KEY)
                .detailKey(AUDIT_EDIT_DETAIL)
                .build());

        log.info("approval-edit bizKey={} apprSeqNo={} userId={} apprLineChanged={} newFiles={} deletedFiles={} wsvc={}",
                bizKey, seq, userId,
                req != null && req.apprLineNo() != null && req.apprLineNo() > 0,
                attachments == null ? 0 : attachments.size(),
                req == null || req.deletedAttachments() == null ? 0 : req.deletedAttachments().size(),
                auditWsvcId);
    }
}
