package com.kosign.wecms.domain.setting.purposesettings.dto;

import java.util.List;

/**
 * Envelope for the Settings &gt; Purpose "Deleted Purpose" tab list
 * ({@code GET /api/v1/settings/purpose-settings/deleted}). Mirrors the
 * flat {@code { list }} shape of {@link PurposeSettingListResponse}.
 */
public record DeletedPurposeListResponse(
        List<DeletedPurposeRow> list
) { }
