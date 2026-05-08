package com.kosign.wecms.domain.setting.firmbankinghistory.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for Settings &gt; Firm Banking History. Constant names
 * mirror the View Setting {@code FIELD_ID} values that will be seeded
 * for screen {@code firm-banking-history}; the mapped property is the
 * matching JPA field on
 * {@link com.kosign.wecms.domain.setting.firmbankinghistory.entity.FirmBankingHistoryView}.
 *
 * <p>Sort surface mirrors the legacy {@code comm_0221_001_view.jsp}
 * sortable column set ({@code data-sort} attributes lines 213–235).
 * Columns intentionally NOT sortable:
 * <ul>
 *   <li>{@code RSLT_MSG}, {@code JOB}, {@code REQ_DATA},
 *       {@code VAN_ORG_CD} — diagnostic / non-display columns.</li>
 *   <li>{@code ETC} — special "View Details" action column
 *       (sys-pinned, never sortable per
 *       {@link com.kosign.wecms.domain.setting.service.ScreenOptionService#SPECIAL_FIELD_IDS}).</li>
 * </ul>
 *
 * <p>Unknown / blank wire {@code sort_field} values resolve to
 * {@code null} so the service falls back to the legacy default
 * {@code TARN_DTM DESC} order rather than rejecting the request.
 */
public enum FirmBankingHistorySortField implements SortableField {

    TARN_DTM("tarnDtm"),
    SVC_TYPE("svcType"),
    SVC_NM("svcNm"),
    SEQ_NO("seqNo"),
    DEP_BANK_CD("depBankCd"),
    RSLT_CD("rsltCd");

    private final String entityProperty;

    FirmBankingHistorySortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient lookup. Accepts the FIELD_ID in any case
     * ({@code TARN_DTM} / {@code tarn_dtm} / {@code Tarn_Dtm}); blank
     * input returns {@code null} so the service falls back to the
     * default ORDER BY.
     */
    public static FirmBankingHistorySortField parse(String fieldId) {
        if (fieldId == null) return null;
        String trimmed = fieldId.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
