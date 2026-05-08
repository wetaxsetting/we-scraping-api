package com.kosign.wecms.domain.setting.firmbankinghistory.service;

import com.kosign.wecms.common.enums.TranTp;

/**
 * Resolves a row's {@code (svc_type, svc_nm)} pair to a stable
 * frontend i18n key under the {@code fb_history.service.*} namespace.
 *
 * <p><b>Why server-side, not frontend.</b> The legacy
 * {@code SERVICE_NAME_MAP} at
 * {@code WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts:40-52}
 * mixed numeric F-leg codes with English S/G/B-leg labels into one
 * untyped JS object. The refactor moves that mapping authoritative
 * source to Spring so:
 * <ul>
 *   <li>The F-leg's value set comes from {@link TranTp} (mirrors
 *       legacy {@code wabooks.enums.TranTp}).</li>
 *   <li>The S/G/B-leg's value set comes from the {@code V_FIRM_BANKING_HISTORY}
 *       view's CASE-WHEN literals (V60), so the resolver and the
 *       view share one source of truth.</li>
 *   <li>Adding a new code is a single enum entry plus a locale JSON
 *       entry — no per-screen JS map to keep in sync.</li>
 * </ul>
 *
 * <p><b>Wire shape.</b> Callers (only
 * {@code FirmBankingHistoryService#toRow} today) feed the row's
 * {@code (svcType, svcNm)} pair in and emit the i18n key on the
 * wire as {@code svc_name_key}. The frontend renders it via
 * {@code t(key)} with a fallback to the raw {@code svc_nm} so
 * unknown codes still display.
 *
 * <p><b>Stateless utility — no DI bean.</b> Method-level
 * {@code static} so the service can call it from inside the
 * mapping function without holding a Spring reference. Tested via
 * unit tests; no integration setup needed.
 */
public final class FirmBankingServiceNameResolver {

    /** Returned when no key can be resolved. The frontend renders
     *  the raw {@code svc_nm} in that case (gateway emits it
     *  verbatim). Never blank. */
    public static final String NO_KEY = null;

    private FirmBankingServiceNameResolver() {}

    /**
     * Resolve the i18n key for a single row. Branches on
     * {@code svcType}:
     *
     * <ul>
     *   <li>{@code F} — {@code svcNm} is the raw {@code TRAN_TP} value
     *       ({@code "201"} / {@code "202"} / {@code "203"} /
     *       {@code "204"}). Resolved through {@link TranTp#fromCode(String)}.</li>
     *   <li>{@code S} — {@code svcNm} is one of the four English
     *       literals the V60 view emits for the scraping legs:
     *       {@code "Funds Transfer (Scraping)"}, {@code "Scraping Balance"},
     *       {@code "Scraping Transaction"}, {@code "Scraping Card Transaction"}.</li>
     *   <li>{@code G} — {@code svcNm} is one of the two GDT legs:
     *       {@code "Sale E-Invoice Inquiry"}, {@code "Expense E-Invoice Inquiry"}.</li>
     *   <li>{@code B} — {@code svcNm} is the literal
     *       {@code "Vietnam Business Inquiry"}.</li>
     * </ul>
     *
     * <p>Returns {@code null} for any {@code (svcType, svcNm)} pair
     * the resolver doesn't recognise — the frontend treats {@code null}
     * as "no localised name; render the raw value" so unknown
     * gateway responses never display blank.
     */
    public static String resolve(String svcType, String svcNm) {
        if (svcType == null || svcNm == null) return NO_KEY;
        String type = svcType.trim();
        String name = svcNm.trim();
        if (type.isEmpty() || name.isEmpty()) return NO_KEY;

        return switch (type) {
            case "F" -> resolveF(name);
            case "S" -> resolveS(name);
            case "G" -> resolveG(name);
            case "B" -> resolveB(name);
            default  -> NO_KEY;
        };
    }

    private static String resolveF(String svcNm) {
        // F-leg is the only place Spring owns a typed enum — TranTp
        // mirrors legacy wabooks.enums.TranTp. Codes the legacy enum
        // doesn't define resolve to NO_KEY → frontend renders the
        // raw "201"/"202"/etc. unchanged.
        return TranTp.fromCode(svcNm).map(TranTp::i18nKey).orElse(NO_KEY);
    }

    private static String resolveS(String svcNm) {
        // S-leg labels are emitted by the V60 view's CASE WHEN
        // literals — keep this switch in lock-step with V60.
        return switch (svcNm) {
            case "Funds Transfer (Scraping)"   -> "fb_history.service.funds_transfer_scraping";
            case "Scraping Balance"            -> "fb_history.service.scraping_balance";
            case "Scraping Transaction"        -> "fb_history.service.scraping_transaction";
            case "Scraping Card Transaction"   -> "fb_history.service.scraping_card_transaction";
            default                            -> NO_KEY;
        };
    }

    private static String resolveG(String svcNm) {
        return switch (svcNm) {
            case "Sale E-Invoice Inquiry"      -> "fb_history.service.sale_einvoice_inquiry";
            case "Expense E-Invoice Inquiry"   -> "fb_history.service.expense_einvoice_inquiry";
            default                            -> NO_KEY;
        };
    }

    private static String resolveB(String svcNm) {
        return switch (svcNm) {
            case "Vietnam Business Inquiry"    -> "fb_history.service.vietnam_business_inquiry";
            default                            -> NO_KEY;
        };
    }
}
