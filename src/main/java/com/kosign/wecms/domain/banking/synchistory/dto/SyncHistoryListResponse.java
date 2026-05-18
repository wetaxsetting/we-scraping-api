package com.kosign.wecms.domain.banking.synchistory.dto;

import java.util.List;

/** List envelope for the sync-history popup. {@code totalCount} mirrors
 *  the rendered row count; the legacy popup shows no separate pagination
 *  so the two are always equal today. */
public record SyncHistoryListResponse(
        List<SyncHistoryRow> rows,
        int totalCount
) { }
