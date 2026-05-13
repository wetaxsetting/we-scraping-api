package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * Wire envelope for {@code GET /api/v1/settings/contacts}. Mirrors the
 * pagination shape used by every other refactor list endpoint:
 * zero-based {@code page} / {@code size} + total counts.
 */
public record ContactListResponse(
        List<ContactRow> rows,
        long totalCount,
        int page,
        int size,
        int totalPages) { }
