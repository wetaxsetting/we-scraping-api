package com.kosign.wecms.common.enums;

import java.util.Optional;

/**
 * Firm Banking History service-type discriminator — value set of the
 * {@code SVC_TYPE} column on the unified {@code V_FIRM_BANKING_HISTORY}
 * view (V60). Four legs identified by single-character codes:
 *
 * <ul>
 *   <li>{@code F} — Firm Banking gateway (Korean VAN: TB_FIRM_TRAN)</li>
 *   <li>{@code S} — Scraping (NAPAS + EXT_TRAN: balance / transaction / card)</li>
 *   <li>{@code G} — NXISAS GDT e-Invoice inquiry (sale + expense)</li>
 *   <li>{@code B} — NXISAS Vietnam Business inquiry</li>
 * </ul>
 *
 * <p><b>Modernized refactor enum.</b> No legacy JEX twin — the
 * {@code SVC_TYPE} discriminator is a refactor-only construct (V60
 * unifies five legacy tables into one view). This enum centralises the
 * code → i18n-key + frontend-category mapping that was previously
 * forked between {@code FirmBankingServiceNameResolver} (server-side
 * name resolution) and {@code mappers.ts:coerceFirmBankingServiceType}
 * (client-side semantic translation).
 *
 * <p>The {@link #i18nKey()} resolves to the same
 * {@code settings.firm_banking_history.service_type.*} namespace the
 * frontend's {@code <FirmBankingHistoryTable>} already consumes via
 * {@code t(key)}, so the wire field stays a stable identifier and the
 * locale-specific display text lives in {@code locales/{en,ko,vi}.json}.
 *
 * <p>Per the workspace task rule "Do not use {@code EnumType.ORDINAL}",
 * this enum is <b>not</b> persisted under any {@code @Enumerated}
 * mapping. The view column is a single character, read as
 * {@link String}.
 */
public enum FirmBankingSvcType {

    /** {@code F} — Firm Banking gateway (Korean VAN). Source: TB_FIRM_TRAN. */
    FIRM_BANKING_TRANSACTION(
            "F",
            "settings.firm_banking_history.service_type.firm_banking_transaction"),

    /** {@code S} — Scraping. Source: NAPAS_REMIT_TRNS_HSTR + EXT_TRAN_CALL_HIS. */
    SCRAPING_TRANSFER(
            "S",
            "settings.firm_banking_history.service_type.scraping_transfer"),

    /** {@code G} — Vietnam GDT e-Invoice inquiry. Source: NXISAS_MODULE_TRAN_LOG_HIS. */
    VIETNAM_GDT_INQUIRY(
            "G",
            "settings.firm_banking_history.service_type.vietnam_gdt_inquiry"),

    /** {@code B} — Vietnam Business inquiry. Source: NXISAS_MODULE_TRAN_LOG_HIS. */
    VIETNAM_BUSINESS_INQUIRY(
            "B",
            "settings.firm_banking_history.service_type.vietnam_business_inquiry");

    private final String code;
    private final String i18nKey;

    FirmBankingSvcType(String code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }

    /** Raw 1-char code stored in the view's {@code SVC_TYPE} column. */
    public String code() {
        return code;
    }

    /** Frontend i18n key —
     *  {@code settings.firm_banking_history.service_type.*}. Always
     *  non-null for the four enum constants — every leg has a verified
     *  EN/KO/VI translation in the existing locale files. */
    public String i18nKey() {
        return i18nKey;
    }

    /** Resolve a raw {@code SVC_TYPE} character to its enum constant.
     *  Returns {@link Optional#empty()} for unknown / blank input.
     *  Whitespace is trimmed; lookup is case-sensitive (legacy values
     *  are uppercase letters). */
    public static Optional<FirmBankingSvcType> fromCode(String code) {
        if (code == null) return Optional.empty();
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (FirmBankingSvcType t : values()) {
            if (t.code.equals(trimmed)) return Optional.of(t);
        }
        return Optional.empty();
    }

    /** Resolve a raw {@code SVC_TYPE} character directly to its
     *  frontend i18n key, or {@code null} when the code is unknown
     *  (frontend then falls back to rendering the raw {@code svc_type}
     *  letter — never blank). Convenience for callers that don't need
     *  the enum constant itself. */
    public static String resolveKey(String code) {
        return fromCode(code).map(FirmBankingSvcType::i18nKey).orElse(null);
    }
}
