package com.kosign.wecms.domain.setting.approvalline.dto;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for {@code GET /api/v1/settings/approval-line}. The
 * legacy {@code SortHelper.getSortDynamic(util, "comm_0222_001_r001")}
 * surface is a JEX framework attribute &mdash; the refactor surfaces
 * the three fields the frontend cares about today.
 *
 * <p>Constant names use UPPER_SNAKE_CASE semantic keys (matches the
 * workspace common-sort rule). {@link #entityProperty()} resolves to
 * the {@link com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLine}
 * JPA property name &mdash; Spring Data's metamodel validation fails
 * fast on a typo at query time rather than at the database.
 *
 * <p>Default in the service is {@link #REG_DTM} DESC tie-broken on
 * {@code apprLineNo DESC} (deterministic for rows that share a
 * timestamp).
 */
public enum ApprovalLineSortField implements SortableField {

    REG_DTM("regDtm"),
    APPR_LINE_NM("apprLineNm"),
    MOD_DTM("modDtm");

    private final String entityProperty;

    ApprovalLineSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient parse &mdash; accepts case-insensitive enum names;
     * unknown / blank values return {@link Optional#empty()} so the
     * service falls back to the default ORDER BY.
     */
    public static Optional<ApprovalLineSortField> parse(String raw) {
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
