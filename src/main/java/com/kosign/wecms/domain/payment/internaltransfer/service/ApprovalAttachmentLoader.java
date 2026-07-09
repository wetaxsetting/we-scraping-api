package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolPayEvidFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the Approval Details attachment list.
 *
 * <p>Mirrors the legacy attachment query {@code IDO.EXPT_RESOL_FILE_R002},
 * which returns the <strong>UNION</strong> of:
 * <ul>
 *   <li>{@code EXPT_RESOL_FILE} — requester uploads, and</li>
 *   <li>{@code EXPT_RESOL_PAY_EVID_FILE} — payment evidence files.</li>
 * </ul>
 *
 * <p>Closes GAP-ATT-1: the per-type detail services previously read only
 * {@code EXPT_RESOL_FILE}, dropping payment-evidence files for every payment
 * type. Every Approval Details consumer (Internal Transfer / Vendor / Expense
 * / Payroll / Overseas) now calls {@link #load(String, String)} so the
 * behaviour can't drift per module. Each row carries a {@code fileType}
 * discriminator ({@link ApprovalAttachmentRow#TYPE_EXPT_RESOL_FILE} /
 * {@link ApprovalAttachmentRow#TYPE_PAY_EVID_FILE}) so a download can resolve
 * the correct legacy table — the two tables use different id columns
 * ({@code FILE_ID} vs {@code EVID_FILE_ID}).
 *
 * <p>Read-only. Tenant scoping is the caller's responsibility — pass the
 * {@code bizKey} from {@code TenantContext.currentBizKey()}.
 */
@Component
@RequiredArgsConstructor
public class ApprovalAttachmentLoader {

    private final ExptResolFileRepository        exptResolFileRepository;
    private final ExptResolPayEvidFileRepository exptResolPayEvidFileRepository;

    /**
     * Combined attachment list for one approval batch, ordered
     * {@code EXPT_RESOL_FILE} rows first (by {@code FILE_ID}) then
     * {@code EXPT_RESOL_PAY_EVID_FILE} rows (by {@code EVID_FILE_ID}).
     * Returns an empty list when {@code apprSeqNo} is null/blank (the batch
     * has not been submitted for approval yet).
     */
    public List<ApprovalAttachmentRow> load(String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return List.of();
        }

        List<ApprovalAttachmentRow> combined = new ArrayList<>();

        exptResolFileRepository
                .findByBizKeyAndApprSeqNoOrderByFileIdAsc(bizKey, apprSeqNo)
                .forEach(f -> combined.add(new ApprovalAttachmentRow(
                        f.getFileId(),
                        f.getOrgFileNm(),
                        f.getFileSize(),
                        f.getRegNm(),
                        f.getRegDtm(),
                        ApprovalAttachmentRow.TYPE_EXPT_RESOL_FILE)));

        exptResolPayEvidFileRepository
                .findByBizKeyAndApprSeqNoOrderByEvidFileIdAsc(bizKey, apprSeqNo)
                .forEach(f -> combined.add(new ApprovalAttachmentRow(
                        f.getEvidFileId(),
                        f.getOrgFileNm(),
                        f.getFileSize(),
                        f.getRegNm(),
                        f.getRegDtm(),
                        ApprovalAttachmentRow.TYPE_PAY_EVID_FILE)));

        return combined;
    }
}
