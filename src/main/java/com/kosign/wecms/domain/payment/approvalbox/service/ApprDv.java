package com.kosign.wecms.domain.payment.approvalbox.service;

import java.util.Optional;

/**
 * Approval Box document discriminator — mirrors the six legacy
 * {@code EXPT_RESOL_MSTR.APPR_DV} values surfaced by the Approval Box
 * page at {@code resl_0001_001_r001_act.jsp:78}:
 * {@code APPR_DV IN ('21','31','32','33','34','35')}.
 *
 * <p>The refactor returns both the raw {@code appr_dv} digit and a
 * stable {@link #slug()} on the wire (per {@code design-backend.md} §2.1
 * D2): the frontend renders the visible label via
 * {@code payment.approval_box.type.<slug>} i18n keys and ignores the
 * legacy digit. Legacy DEV stores nine additional {@code APPR_DV} codes
 * (00, 10, 20, 30, 40, 50, 60, 70) that belong to other modules — they
 * are intentionally not modelled here.
 *
 * <p>Refactor enum policy ({@code wecms-api-main/CLAUDE.md} §"Domain
 * enums"): non-persisted, no {@code @Enumerated}, {@code code()} carries
 * the verbatim legacy DB string, {@code i18nKey()} stays a stable key
 * with frontend-side resolution.
 */
public enum ApprDv {

    PAYROLL("21",           "payroll",           "payment.approval_box.type.payroll"),
    EXPENSE("31",           "expense",           "payment.approval_box.type.expense"),
    VENDOR("32",            "vendor",            "payment.approval_box.type.vendor"),
    CORPORATE_CARD("33",    "corporate_card",    "payment.approval_box.type.corporate_card"),
    OVERSEAS("34",          "overseas",          "payment.approval_box.type.overseas"),
    INTERNAL_TRANSFER("35", "internal_transfer", "payment.approval_box.type.internal_transfer");

    /** Six-element array used as the {@code APPR_DV IN (...)} filter
     *  predicate on every Approval Box query. */
    public static final String[] APPROVAL_BOX_CODES =
            { "21", "31", "32", "33", "34", "35" };

    private final String code;
    private final String slug;
    private final String i18nKey;

    ApprDv(String code, String slug, String i18nKey) {
        this.code    = code;
        this.slug    = slug;
        this.i18nKey = i18nKey;
    }

    public String code()    { return code;    }
    public String slug()    { return slug;    }
    public String i18nKey() { return i18nKey; }

    /** Returns the slug for a legacy code, or {@code null} if the code is
     *  outside the six Approval Box-supported types. The wire passes
     *  through the raw code untouched; the slug is what the frontend
     *  i18n key resolves against. */
    public static String resolveSlug(String code) {
        return fromCode(code).map(ApprDv::slug).orElse(null);
    }

    public static Optional<ApprDv> fromCode(String code) {
        if (code == null) return Optional.empty();
        for (ApprDv v : values()) {
            if (v.code.equals(code)) return Optional.of(v);
        }
        return Optional.empty();
    }
}
