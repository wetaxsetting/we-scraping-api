package com.kosign.wecms.domain.setting.chartofaccounts.dto;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for {@code GET /api/v1/settings/chart-of-accounts}.
 * Mirrors the common-sort contract — frontend ships the View Setting
 * {@code FIELD_ID} (UPPER_SNAKE) as the {@code sort_field} query
 * param; the service translates it via this enum to the JPA entity
 * property name. Unknown / blank tokens fall back to the default
 * ORDER BY at the service level — never echo into a SQL string.
 *
 * <p>Whitelisted columns (planning doc §"Sort whitelist"):
 * <ul>
 *   <li>{@link #CHART_ACCT_CD} → {@code chartAcctCd}</li>
 *   <li>{@link #CHART_ACCT_NM_EN} → {@code chartAcctNmEn}</li>
 *   <li>{@link #STTS} → {@code stts}</li>
 *   <li>{@link #MOD_DTM} → {@code modDtm}</li>
 * </ul>
 *
 * <p>{@code CHART_ACCT_NM_VN} / {@code CHART_ACCT_NM_KO} /
 * {@code CHART_ACCT_TYPE} are intentionally NOT in the whitelist for
 * Phase 1 — adding them is a one-line extension when the frontend
 * wires the column.
 *
 * <p>Default in the service is {@link #CHART_ACCT_CD} ASC, mirroring
 * legacy {@code IDO.CHART_ACCT_INFM_R001} {@code ORDER BY
 * CHART_ACCT_CD ASC}.
 */
public enum ChartOfAccountsSortField implements SortableField {

    CHART_ACCT_CD("chartAcctCd"),
    CHART_ACCT_NM_EN("chartAcctNmEn"),
    STTS("stts"),
    MOD_DTM("modDtm");

    private final String entityProperty;

    ChartOfAccountsSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient parser. Accepts case-insensitive enum names; unknown /
     * blank values return {@link Optional#empty()} so the service falls
     * back to the default ORDER BY.
     */
    public static Optional<ChartOfAccountsSortField> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(valueOf(trimmed.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
