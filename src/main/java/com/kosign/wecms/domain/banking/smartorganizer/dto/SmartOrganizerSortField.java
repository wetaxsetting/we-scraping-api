package com.kosign.wecms.domain.banking.smartorganizer.dto;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for the Smart Organizer eligibility list. View
 * Setting {@code FIELD_ID}s on the V162 seed that map cleanly to a
 * native query ORDER BY column.
 *
 * <p>{@code TRSC_DT} → composite of trsc_dt + trsc_tm so the eligible
 *  list orders most-recent-first when the user picks "Date" sort.
 *  {@code IN_AMT} / {@code OUT_AMT} are derived in SQL via a CASE on
 *  {@code INOUT_DV} so the frontend doesn't have to split direction
 *  in two columns server-side.
 *
 * <p>Unknown / blank values resolve to {@code null} via the lenient
 * {@link #parse(String)} so the service falls back to the default
 * ORDER BY (latest first) without throwing.
 */
public enum SmartOrganizerSortField implements SortableField {

    TRSC_DT("h.trsc_dt, h.trsc_tm"),
    ACCT_NO("h.acct_no"),
    IN_AMT("CASE WHEN h.inout_dv = '1' THEN h.trsc_amt ELSE 0 END"),
    OUT_AMT("CASE WHEN h.inout_dv = '2' THEN h.trsc_amt ELSE 0 END"),
    RMRK("h.rmrk"),
    CHART_ACCT_CD("r.chart_acct_cd"),
    USE_USAG_CD("r.use_usag_cd");

    private final String sqlExpression;

    SmartOrganizerSortField(String sqlExpression) {
        this.sqlExpression = sqlExpression;
    }

    /** SQL expression to drop into ORDER BY — see service for the
     *  native query that consumes it. */
    public String sqlExpression() {
        return sqlExpression;
    }

    /** {@link SortableField} contract — the native SQL native query
     *  doesn't use Spring Data {@code Sort}, so this returns the
     *  same SQL expression. The interface keeps the whitelist
     *  shape uniform across every list endpoint. */
    @Override
    public String entityProperty() {
        return sqlExpression;
    }

    public static Optional<SmartOrganizerSortField> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
