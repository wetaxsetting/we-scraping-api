package com.kosign.wecms.domain.setting.purposesettings.dto;

/**
 * One row of the Settings &gt; Purpose "Deleted Purpose" tab. Flat
 * (soft-deleted rows are never re-nested). Mirrors legacy
 * {@code comm_0187_003_r001} — the tab shows a Categories column
 * derived from {@code usag_grp_cd} ({@code 4} = Banking Deposit,
 * {@code 6} = Banking Withdrawal; {@code null} = the historic refactor
 * scope) plus Restore.
 *
 * <p>Wire field names auto-derived from camelCase via the global
 * Jackson SNAKE_CASE strategy.
 *
 * @param usagGrpCd raw legacy group code ({@code "4"}/{@code "6"}/{@code null});
 *                  the frontend maps it to the localized Categories label.
 */
public record DeletedPurposeRow(
        String  useUsagCd,
        String  handUseUsagCd,
        String  useUsagNm,
        String  useUsagCont,
        String  usagGrpCd,
        String  modId,
        String  modDtm
) { }
