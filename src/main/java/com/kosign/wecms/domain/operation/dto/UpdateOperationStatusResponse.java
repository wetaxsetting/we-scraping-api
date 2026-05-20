package com.kosign.wecms.domain.operation.dto;

/**
 * Wire response for {@code POST /api/v1/addon/operation-status/update}
 * — the toolbar "Update" button that re-polls every cached row in
 * {@code OPRT_STTS_INFO} from the GDT and upserts fresh values.
 *
 * <p>Carries the counts so the frontend can surface a "Refreshed N of
 * M" toast and trigger a list refetch.
 */
public record UpdateOperationStatusResponse(
        int totalAttempted,
        int updated,
        int failed
) { }
