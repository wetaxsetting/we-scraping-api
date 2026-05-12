package com.kosign.wecms.domain.setting.purposesettings.dto;

/**
 * Flat detail record — same field shape as {@link PurposeSettingRow}
 * but without {@code children} (detail is per-PK, not tree-shaped).
 * Returned by GET-by-PK and by every mutating endpoint (Create /
 * Update) as the post-write echo.
 */
public record PurposeSettingDetail(
        String  useUsagCd,
        String  handUseUsagCd,
        String  useUsagNm,
        String  useUsagCont,
        String  parentUseUsagCd,
        String  dispYn,
        Integer dispOrd,
        boolean usedInTransactions,
        String  modId,
        String  modDtm
) { }
