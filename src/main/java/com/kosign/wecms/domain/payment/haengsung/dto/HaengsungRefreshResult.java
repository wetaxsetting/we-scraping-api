package com.kosign.wecms.domain.payment.haengsung.dto;

/**
 * Result counts for a HAENGSUNG Oracle Refresh import
 * ({@code haengsung-payment-refresh} §9). Serialized lowercase snake_case.
 *
 * @param mastersFound    Oracle {@code TB_TRNS_MAST} rows matched by the read filter.
 * @param imported        masters whose WeCMS draft was inserted/updated successfully.
 * @param skipped         masters skipped (approval-locked, or no importable detail lines).
 * @param failed          masters whose WeCMS write threw (rolled back; loop continued).
 * @param detailsImported total detail lines inserted/updated across imported masters.
 */
public record HaengsungRefreshResult(
        int mastersFound,
        int imported,
        int skipped,
        int failed,
        int detailsImported
) { }
