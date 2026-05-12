package com.kosign.wecms.domain.setting.purposesettings.dto;

/**
 * Response payload for {@code POST /api/v1/settings/purpose-settings/excel/import}.
 * Mirrors the VAS Account shape minus the {@code replacedCount} field —
 * Purpose's import path is pure-append (no Replace mode).
 */
public record PurposeExcelImportResult(
        int insertedCount
) { }
