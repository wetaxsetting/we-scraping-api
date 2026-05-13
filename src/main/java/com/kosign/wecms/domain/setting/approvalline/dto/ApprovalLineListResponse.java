package com.kosign.wecms.domain.setting.approvalline.dto;

import java.util.List;

/**
 * Envelope for the Approval Line list endpoint. No server-side
 * pagination (feature § 4.3 / OQ-3 resolved 2026-05-13): the list
 * returns the entire tenant set; {@code total_count} equals
 * {@code approval_lines.size()}.
 */
public record ApprovalLineListResponse(
        int                      totalCount,
        List<ApprovalLineRow>    approvalLines
) {}
