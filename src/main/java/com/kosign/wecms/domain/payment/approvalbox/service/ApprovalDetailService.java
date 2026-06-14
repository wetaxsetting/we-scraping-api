package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalBoxRow;
import com.kosign.wecms.domain.payment.approvalbox.dto.ApprovalTransactionRow;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetl;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolDetlRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ApprovalAttachmentRow;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferLineDetail;
import com.kosign.wecms.domain.payment.internaltransfer.service.ApprovalAttachmentLoader;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService;
import com.kosign.wecms.domain.payment.notification.entity.BltbPostInfmId;
import com.kosign.wecms.domain.payment.notification.repository.BltbPostInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Read-only support for the Approval Details popup — specifically the
 * transaction lines + attachments needed when the popup is opened from a
 * header notification (legacy {@code comm_0193_001_r001} surfaced these
 * inline). Backs {@code GET /api/v1/payment/approval-box/{apprSeqNo}/transactions}
 * and {@code …/attachments}.
 *
 * <p><b>Transaction resolution by {@code APPR_SEQ_NO}.</b> A notification
 * carries only the {@code APPR_SEQ_NO}; the per-kind line tables are keyed by
 * the source batch id. The bridge is {@code EXPT_RESOL_DETL.DOC_ID}, which the
 * Request Approval flow stamps with the batch business key for the scheduled /
 * overseas kinds:
 * <ol>
 *   <li>load {@link ExptResolMstr} → {@code APPR_DV} (the payment kind);</li>
 *   <li>load the {@link ExptResolDetl} row → {@code DOC_ID} = the batch key;</li>
 *   <li>read the batch's line transactions and normalize them to
 *       {@link ApprovalTransactionRow}.</li>
 * </ol>
 *
 * <p><b>Kind coverage.</b> The four scheduled kinds — Internal Transfer
 * ({@code 35}), Vendor ({@code 32}), Expense ({@code 31}), Payroll
 * ({@code 21}) — share {@code SCDUL_PYMN_SUMR_DETL}, so one read via
 * {@link InternalTransferService#transactionLinesByBatch(String)} (which
 * already resolves bank names + chart-of-account breadcrumbs) serves all
 * four. <b>Card</b> ({@code 33}) is intentionally returned empty here — the
 * frontend loads card lines through the existing
 * {@code fetchCardTransactionsByApproval} path (card carries extra card-only
 * fields). <b>Overseas</b> ({@code 34}) line loading is deferred (returns
 * empty) — documented gap; the header / approvers / content / attachments
 * still render for overseas.
 *
 * <p>Everything is tenant-scoped via {@link TenantContext#currentBizKey()};
 * a missing / wrong-tenant approval yields an empty list (never an error) so
 * the popup degrades to its empty state rather than crashing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalDetailService {

    /** {@code APPR_DV} values whose lines live in {@code SCDUL_PYMN_SUMR_DETL}. */
    private static final Set<String> SCHEDULED_APPR_DV = Set.of("21", "31", "32", "35");

    /** Approval bulletin board code — legacy {@code GenericBean.BLTB_CD_APPR};
     *  the {@code BLTB_CD} segment of every approval notification's
     *  {@code BLTB_POST_INFM} / {@code BLTB_NOTI_INFM} key. Default when a
     *  caller resolves by {@code POST_SEQ_NO} without supplying it. */
    private static final String BLTB_CD_APPR = "000000000000000";

    private final ExptResolMstrRepository exptResolMstrRepository;
    private final ExptResolDetlRepository exptResolDetlRepository;
    private final InternalTransferService internalTransferService;
    private final ApprovalAttachmentLoader approvalAttachmentLoader;
    private final ApprovalBoxService approvalBoxService;
    private final BltbPostInfmRepository bltbPostInfmRepository;

    /**
     * Common Approval Details master row, resolvable by either identity:
     * <ul>
     *   <li><b>{@code apprSeqNo}</b> — direct (Approval Box / card ref).</li>
     *   <li><b>{@code postSeqNo} (+ {@code bltbCd})</b> — the legacy-safe
     *       notification identity. Legacy {@code comm_0195_001} loads by
     *       {@code POST_SEQ_NO}; the refactor resolves {@code POST_SEQ_NO} →
     *       {@code APPR_SEQ_NO} via {@code BLTB_POST_INFM} (the approval
     *       bulletin post carries both), then reads the master. This is the
     *       robust path — approval-action notifications carry only
     *       {@code POST_SEQ_NO}, never {@code APPR_SEQ_NO}.</li>
     * </ul>
     * {@code apprSeqNo} wins when both are supplied. Returns {@code null}
     * when neither resolves to an in-tenant approval (the controller maps
     * that to {@code WBE_APPROVAL_DETAIL_NOT_FOUND}).
     *
     * @param apprSeqNo direct approval sequence (optional)
     * @param postSeqNo bulletin post sequence from the notification (optional)
     * @param bltbCd    bulletin board code; defaults to the approval board
     *                  ({@link #BLTB_CD_APPR}) when blank
     */
    @Transactional(readOnly = true)
    public ApprovalBoxRow master(String apprSeqNo, String postSeqNo, String bltbCd) {
        String resolved = resolveApprSeqNo(apprSeqNo, postSeqNo, bltbCd);
        if (resolved == null) {
            return null;
        }
        return approvalBoxService.findOne(resolved);
    }

    /** Resolve the effective {@code APPR_SEQ_NO}: prefer the explicit value,
     *  else look it up from {@code BLTB_POST_INFM} by {@code POST_SEQ_NO}
     *  (+ {@code BLTB_CD}). Returns {@code null} when nothing resolves. */
    private String resolveApprSeqNo(String apprSeqNo, String postSeqNo, String bltbCd) {
        if (apprSeqNo != null && !apprSeqNo.isBlank()) {
            return apprSeqNo.trim();
        }
        if (postSeqNo == null || postSeqNo.isBlank()) {
            return null;
        }
        BigDecimal postSeq;
        try {
            postSeq = new BigDecimal(postSeqNo.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        String bizKey = TenantContext.currentBizKey();
        String board = (bltbCd != null && !bltbCd.isBlank()) ? bltbCd.trim() : BLTB_CD_APPR;
        return bltbPostInfmRepository
                .findById(new BltbPostInfmId(bizKey, board, postSeq))
                .map(p -> p.getApprSeqNo())
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
    }

    /**
     * Normalized per-line transactions for one approval. Empty when the
     * approval is missing/other-tenant, when it is a kind whose lines are
     * loaded elsewhere (card) or deferred (overseas), or when the batch has
     * no lines.
     */
    @Transactional(readOnly = true)
    public List<ApprovalTransactionRow> transactions(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return List.of();
        }
        String bizKey = TenantContext.currentBizKey();

        ExptResolMstr mstr = exptResolMstrRepository
                .findByBizKeyAndApprSeqNoIn(bizKey, List.of(apprSeqNo))
                .stream()
                .findFirst()
                .orElse(null);
        if (mstr == null) {
            return List.of();
        }

        String apprDv = mstr.getApprDv();
        if (!SCHEDULED_APPR_DV.contains(apprDv)) {
            // Card (33) → loaded on the frontend via fetchCardTransactionsByApproval.
            // Overseas (34) → deferred (OVRS_PYMN_SUMR_DETL line read not yet ported).
            return List.of();
        }

        // Bridge APPR_SEQ_NO → batch key (DOC_ID) → scheduled line read.
        String batchKey = exptResolDetlRepository
                .findByBizKeyAndApprSeqNo(bizKey, apprSeqNo)
                .stream()
                .map(ExptResolDetl::getDocId)
                .filter(d -> d != null && !d.isBlank())
                .findFirst()
                .orElse(null);
        if (batchKey == null) {
            return List.of();
        }

        List<TransferLineDetail> lines = internalTransferService.transactionLinesByBatch(batchKey);
        return lines.stream().map(ApprovalDetailService::toRow).toList();
    }

    /**
     * Approval-request attachments registered against {@code APPR_SEQ_NO}
     * (UNION of {@code EXPT_RESOL_FILE} + {@code EXPT_RESOL_PAY_EVID_FILE}),
     * reusing the shared {@link ApprovalAttachmentLoader}. Empty when the
     * approval has none. Download streams via the existing
     * {@code GET …/{apprSeqNo}/attachments/{fileId}} endpoint.
     */
    @Transactional(readOnly = true)
    public List<ApprovalAttachmentRow> attachments(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return List.of();
        }
        return approvalAttachmentLoader.load(TenantContext.currentBizKey(), apprSeqNo);
    }

    /** {@link TransferLineDetail} → wire {@link ApprovalTransactionRow}.
     *  Mirrors the scheduled-payment columns the shared
     *  {@code ApprovalDetailsDialog} renders (deposit bank / amount /
     *  recipient / remarks / description / document no / reporting account).
     *  Amount stays raw — the frontend formats it. */
    private static ApprovalTransactionRow toRow(TransferLineDetail d) {
        return new ApprovalTransactionRow(
                d.scdulPymnDetlNo(),
                d.tarBankCd(),
                d.tarBankNm(),
                d.ccyCd(),
                d.fincMdaNo(),
                d.tranAmt(),
                d.rcptNm(),
                d.note(),
                null,            // contact name — not carried on the scheduled line
                d.tranUsag(),
                d.docNo(),
                d.chartAcctCd(),
                d.chartAcctBreadcrumb());
    }
}
