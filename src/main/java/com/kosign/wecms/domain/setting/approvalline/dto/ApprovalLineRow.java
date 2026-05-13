package com.kosign.wecms.domain.setting.approvalline.dto;

import java.util.List;

/**
 * One approval-line row in the list / detail response. Wire shape:
 * <pre>{@code
 * {
 *   "appr_line_no": 19,
 *   "appr_line_nm": "A Plus (Exp) +",
 *   "reg_dtm":      "20250727105815",
 *   "approvers":    [ { ... }, { ... } ]
 * }
 * }</pre>
 *
 * <p>{@code reg_dtm} ships verbatim as the legacy {@code VARCHAR(14)}
 * {@code yyyyMMddHHmmss} string &mdash; the frontend formats for
 * display. {@code approvers} is always present, always ordered by
 * {@code apprvr_disp_ord ASC}.
 */
public record ApprovalLineRow(
        Long                            apprLineNo,
        String                          apprLineNm,
        String                          regDtm,
        List<ApprovalLineApproverRow>   approvers
) {}
