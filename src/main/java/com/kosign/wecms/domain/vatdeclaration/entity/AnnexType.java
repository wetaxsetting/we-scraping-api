package com.kosign.wecms.domain.vatdeclaration.entity;

import java.util.Optional;

/**
 * The five annex types a VAT declaration can carry, matching the PO prototype's
 * create-form checkboxes one-for-one ({@code VAT_Declaration_Report.html},
 * {@code .annexChk[data-code]}). The per-declaration "attached?" flags live as
 * the {@code ANNEX_*_YN} columns on {@code VAT_DECL}; the annex line data itself
 * is live-derived from {@code BUSA_DOC_MSTR}+{@code BUSA_DOC_DETL} (no persisted
 * annex-item table). Used on the wire as the {@code annex_type} filter of
 * {@code GET /{sid}/annex/invoices}.
 *
 * <p>01-6/GTGT ships in two variants that are distinct forms — production
 * facility (CSSX) and post-paid telecom — so they carry distinct codes rather
 * than one merged {@code ALLOC}.
 */
public enum AnnexType {

    /** BKBR 01-1/GTGT — output (sales) invoice listing. */
    BKBR("BKBR", "01-1/GTGT"),
    /** BKMV 01-2/GTGT — input (purchase) invoice listing. */
    BKMV("BKMV", "01-2/GTGT"),
    /** PL 01-6/GTGT (CSSX) — allocation annex, production facility. */
    ALLOC_CSSX("ALLOC_CSSX", "01-6/GTGT (CSSX)"),
    /** PL 01-6/GTGT (VT cước trả sau) — allocation annex, post-paid telecom. */
    ALLOC_TELE("ALLOC_TELE", "01-6/GTGT (VT)"),
    /** 8% VAT-reduction annex (Resolution 204/2025/QH15). */
    REDUCE8("REDUCE8", "204/2025/QH15");

    private final String code;
    private final String formCode;

    AnnexType(String code, String formCode) {
        this.code = code;
        this.formCode = formCode;
    }

    public String code() {
        return code;
    }

    public String formCode() {
        return formCode;
    }

    public static Optional<AnnexType> fromCode(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String v = raw.trim().toUpperCase();
        for (AnnexType t : values()) {
            if (t.code.equals(v)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
