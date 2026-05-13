package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for Payment &gt; Approval Box. Constant names match the
 * View Setting {@code FIELD_ID} values seeded in
 * {@code V84__approval_box_schema.sql} and the legacy
 * {@code data-sort} attributes on the table header at
 * {@code WECMS DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view.jsp:189-198}.
 * Each constant maps to a JPA property on
 * {@link com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr}.
 *
 * <p>Two View Setting columns are intentionally not sortable server-side:
 * {@code CHECKBOX} and {@code ETC} (the row-selection and action-icon
 * columns) — special FIELD_IDs that are never sortable per the
 * {@code wecms-api-main/CLAUDE.md §"Common table sort"} contract.
 *
 * <p>Two View Setting columns are sortable on the wire but resolve to
 * an expression rather than a single entity property:
 * <ul>
 *   <li>{@code APPRER_NM} — joins {@code EXPT_RESOL_APPRVR_INFM}; deferred
 *       until the detail-popup MR ports the chain join.</li>
 *   <li>{@code CURRENCY} — currently always {@code "VND"} in the refactor
 *       (legacy currency-by-column model retired, see
 *       {@code design-backend.md} §13 D1). Hidden by default (USE_YN='N')
 *       and skipped server-side.</li>
 * </ul>
 * Both fall through to the default ORDER BY when sent.
 *
 * <p>{@code TOT_PAY_AMT} maps to {@code totPayKhrAmt} — the legacy column
 * name is misleading (it has stored VND for VN tenants since 2020) but
 * the schema-parity rule means the entity field name preserves the DB
 * column name. The wire / sort id uses the semantic {@code TOT_PAY_AMT}.
 */
public enum ApprovalBoxSortField implements SortableField {

    REG_DTM("regDtm"),
    DOC_NM("docNm"),
    DOC_NO("docNo"),
    TITL("titl"),
    TOT_PAY_AMT("totPayKhrAmt"),
    PAY_END_YN("payEndYn"),
    PAY_DATE("payDate"),
    DRAFT_USER_NM("draftUserNm"),
    APPR_TYPE_CD("apprTypeCd");

    private final String entityProperty;

    ApprovalBoxSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient lookup. Accepts the FIELD_ID in any case; blank input or
     * any non-whitelisted value returns {@code null} so the service
     * falls back to the default {@code REG_DTM DESC} order.
     */
    public static ApprovalBoxSortField parse(String fieldId) {
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
