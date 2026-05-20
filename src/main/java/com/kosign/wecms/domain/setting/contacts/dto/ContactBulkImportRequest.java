package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/settings/contacts/excel/import}.
 * Carries the full list of parsed rows from the Excel template —
 * each entry is the same shape as the single-create endpoint so no
 * new field mapping is required on the backend.
 */
public record ContactBulkImportRequest(
        @NotEmpty
        @Size(max = 500, message = "Cannot import more than 500 contacts at once.")
        @Valid
        List<ContactCreateRequest> contacts) { }
