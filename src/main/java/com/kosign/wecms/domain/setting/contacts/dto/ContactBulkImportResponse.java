package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * Response envelope for {@code POST /api/v1/settings/contacts/excel/import}.
 *
 * @param total    total number of rows submitted
 * @param imported number of rows successfully created
 * @param failed   number of rows that failed
 * @param errors   per-row error details (empty when {@code failed == 0})
 */
public record ContactBulkImportResponse(
        int total,
        int imported,
        int failed,
        List<ContactImportError> errors) { }
