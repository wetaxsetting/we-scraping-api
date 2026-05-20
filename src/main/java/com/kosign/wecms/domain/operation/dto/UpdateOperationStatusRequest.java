package com.kosign.wecms.domain.operation.dto;

import java.util.List;

/**
 * Wire request for {@code POST /api/v1/addon/operation-status/update}
 * (toolbar "Update" button).
 *
 * <p>When {@code taxCodes} is {@code null} or empty the service updates
 * every cached row for the tenant (the original behaviour). When it
 * carries 1..N tax codes, only those rows are re-polled — used by the
 * frontend when the user has selected specific rows via the checkbox
 * column.
 */
public record UpdateOperationStatusRequest(
        List<String> taxCodes
) { }
