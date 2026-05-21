package com.kosign.wecms.domain.setting.purposesettings.dto;

import java.util.List;

/**
 * One row of the Settings &gt; Purpose list. Sent nested — level-1 rows
 * carry their level-2 {@code children}; level-2 rows have {@code null}
 * children. The frontend's {@code PurposeNode} type expects exactly
 * this shape (see {@code wecms-web/components/settings/purpose/types.ts}).
 *
 * <p>Wire field names auto-derived from camelCase via the global
 * Jackson SNAKE_CASE strategy ({@code JacksonConfig}); no per-field
 * {@code @JsonProperty}.
 *
 * <p>{@code usedInTransactions} is server-computed from
 * {@code v_purpose_in_use}; the frontend uses it to gate the row
 * trash button. The boolean is recursive — {@code true} when the row
 * itself OR any of its active children is referenced (OQ-5 decided
 * 2026-05-12).
 */
public record PurposeSettingRow(
        String     useUsagCd,
        String     handUseUsagCd,
        String     useUsagNm,
        String     useUsagCont,
        String     parentUseUsagCd,
        String     dispYn,
        Integer    dispOrd,
        boolean    usedInTransactions,
        String     modId,
        String     modDtm,
        List<PurposeSettingRow> children
) { }
