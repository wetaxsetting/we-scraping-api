package com.kosign.wecms.domain.payment.approvalbox.dto;

import java.math.BigDecimal;

/**
 * JPQL constructor-projection row for the Approval Box list query.
 * Internal to the repository layer — never escapes onto the wire; the
 * service maps it to {@link ApprovalBoxRow} after appending the
 * approver-chain.
 *
 * <p>Mirrors a slimmed-down version of legacy
 * {@code IDO.EXPT_RESOL_MSTR_R003.xml} output: the sibling-table joins
 * (BLTB_POST_INFM / OVRS_PYMN_MSTR / SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR /
 * CARD_TRNS_HSTR) and the legacy {@code wb_get_*} helpers are dropped
 * for this MR — they ship with their owner-module ports (see
 * {@code database-impact.md} §2.1).
 */
public record ApprovalBoxProjection(
        String     apprSeqNo,
        String     docNo,
        String     titl,
        String     apprDv,
        String     docNm,
        BigDecimal totPayKhrAmt,
        BigDecimal totPayUsdAmt,
        BigDecimal totPayVndAmt,
        String     payDate,
        String     draftUserId,
        String     draftUserNm,
        String     regDtm,
        String     apprTypeCd,
        String     payEndYn,
        Long       apprvrTotCnt,
        Long       apprvrApprTotCnt
) { }
