package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.notification.entity.BltbPostInfm;
import com.kosign.wecms.domain.payment.notification.repository.BltbPostInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the Approval Details "Approval Content" note.
 *
 * <p>Legacy source of truth: {@code BLTB_POST_INFM.POST_CONT} joined by
 * {@code APPR_SEQ_NO} (board {@code BLTB_CD='000000000000000'}). The legacy
 * approval-detail reads {@code IDO.EXPT_RESOL_MSTR_R007 / R008 / R009 / R010}
 * all select {@code BPI.POST_CONT}; {@code comm_0193_001_r001} ships it as
 * {@code POST_CONT} and {@code comm_0195_001.ts:863} renders it into
 * {@code #approval_input}.
 *
 * <p>This is deliberately {@code POST_CONT}, NOT {@code EXPT_RESOL_MSTR.CONT}:
 * the two columns can hold DIFFERENT values on the live JEX DB (verified) and
 * the legacy popup always displays {@code POST_CONT}. Every Approval Details
 * consumer (Internal Transfer / Vendor / Expense / Payroll / Overseas) calls
 * {@link #load(String, String)} so the source can't drift per module — the
 * same shared-component pattern as {@link ApprovalAttachmentLoader}.
 *
 * <p>Read-only. Tenant scoping is the caller's responsibility — pass the
 * {@code bizKey} from {@code TenantContext.currentBizKey()}.
 */
@Component
@RequiredArgsConstructor
public class ApprovalContentLoader {

    /** Approval bulletin board code — legacy {@code GenericBean.BLTB_CD_APPR};
     *  keys the {@code BLTB_POST_INFM} row that holds the Approval Content. */
    private static final String BLTB_CD_APPR = "000000000000000";

    private final BltbPostInfmRepository bltbPostInfmRepository;

    /**
     * The Approval Content note ({@code BLTB_POST_INFM.POST_CONT}) for one
     * approval batch. Returns null when the batch has no approval request yet
     * ({@code apprSeqNo} null/blank) or the note is blank.
     */
    public String load(String bizKey, String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            return null;
        }
        return bltbPostInfmRepository
                .findByBizKeyAndBltbCdAndApprSeqNo(bizKey, BLTB_CD_APPR, apprSeqNo)
                .map(BltbPostInfm::getPostCont)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
    }
}
