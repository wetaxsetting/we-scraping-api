package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for
 * {@code POST /api/v1/settings/contacts/bulk-delete}. Mirrors legacy
 * {@code comm_0184_001_u001} (Move-to-Trash); per OQ-2 the service
 * additionally runs the {@code WBE1190} in-use guard for every key
 * before flipping any row.
 *
 * <p>{@code @Size(max = 100)} caps a single request at 100 contacts.
 * Above that the client should batch.
 */
public record ContactBulkDeleteRequest(
        @NotEmpty
        @Size(max = 100)
        List<String> trcoKeys) { }
