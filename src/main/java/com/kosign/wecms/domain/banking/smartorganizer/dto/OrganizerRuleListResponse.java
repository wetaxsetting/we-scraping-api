package com.kosign.wecms.domain.banking.smartorganizer.dto;

import java.util.List;

/** Wraps the rule list with a total count for the popup header. */
public record OrganizerRuleListResponse(
        long totalCount,
        List<OrganizerRuleRow> rows
) {
}
