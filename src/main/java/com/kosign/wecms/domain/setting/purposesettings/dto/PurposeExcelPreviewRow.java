package com.kosign.wecms.domain.setting.purposesettings.dto;

import java.util.List;

/**
 * One parsed row from the uploaded Purpose Excel file. Mirrors the
 * legacy {@code comm_0187_004_r001} {@code REC} sub-record shape (with
 * the four user-visible columns) plus the per-cell error payload.
 *
 * <p>{@link #valid} is {@code false} when {@link #errors} is non-empty.
 * Both valid AND invalid rows ship in the preview response so the UI
 * can show every input row to the user.
 *
 * <p>{@link #rowIndex} is the 1-based Excel row number (matches what
 * the user sees in Excel — header is row 1, first data row is row 2,
 * etc.) so error reporting reads naturally in the preview UI.
 */
public record PurposeExcelPreviewRow(
        /** 1-based Excel row number (sheet position). */
        int rowIndex,
        /** Raw value from the {@code Purpose Code} column. */
        String handUseUsagCd,
        /** Raw value from the {@code Purpose Name} column. */
        String useUsagNm,
        /** Raw value from the {@code Purpose Description} column. */
        String useUsagCont,
        /** Raw value from the {@code Parent Purpose} column. */
        String parentUseUsagCd,
        /** True when {@link #errors} is empty. */
        boolean valid,
        /** Per-field error messages. Empty for valid rows. */
        List<PurposeExcelFieldError> errors
) { }
