package com.kosign.wecms.domain.payment.approvalbox.dto;

/**
 * Per-tab document counts for the Approval Box tab strip. Mirrors the
 * legacy {@code TOT_PEDN_CNT}/{@code TOT_INP_CNT}/{@code TOT_APPR_CNT}/
 * {@code TOT_RJT_CNT} scalars from {@code WSVC.resl_0001_001_r001.xml}.
 *
 * <p>Counts intentionally ignore the {@code appr_type_cd} request param
 * so every tab chip updates simultaneously for one filter combination —
 * mirrors legacy {@code resl_0001_001_r001_act.jsp:80-86}.
 */
public record ApprovalBoxTabCounts(
        long pending,
        long inProgress,
        long approved,
        long rejected
) {
    public static ApprovalBoxTabCounts empty() {
        return new ApprovalBoxTabCounts(0L, 0L, 0L, 0L);
    }
}
