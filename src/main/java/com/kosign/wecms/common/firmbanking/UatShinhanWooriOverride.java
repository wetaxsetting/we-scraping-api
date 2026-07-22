package com.kosign.wecms.common.firmbanking;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * M — legacy {@code PymnHelper.java:917-947} UAT-only Shinhan/Woori depositor-inquiry override.
 *
 * <p>Under the <b>UAT profile only</b>, when the target bank is Shinhan ({@code 970424}) or
 * Woori ({@code 970457}), the outbound {@code BANK_CD} / {@code PRTN_ID} the inquiry request
 * carries are swapped to fixed UAT partner ids keyed on the tenant product type — mirroring the
 * legacy application point exactly ({@code inputJSON.put("BANK_CD", …)} / {@code "PRTN_ID"}):
 *
 * <ul>
 *   <li><b>Shinhan (970424):</b> F → {@code PRTN_ID=WEBC1}; H → {@code PRTN_ID=HASU1}</li>
 *   <li><b>Woori (970457):</b>  F → {@code PRTN_ID=000001008}; H → {@code PRTN_ID=000001020}</li>
 * </ul>
 *
 * <p>The legacy override keeps {@code OUT_BANK_CD} at the bank code (970424/970457) and changes
 * only {@code PRTN_ID} (legacy {@code :926-942} — {@code outBankCd = "970424"/"970457"}). It is a
 * <b>request-only</b> override (never persisted to a table); legacy applies it while building the
 * WGVN inquiry payload, not on any DB row.
 *
 * <p>Non-UAT profiles (DEV / SIT / PROD) and any other bank / product type are pass-through:
 * {@link #resolve} returns the supplied firm-config values unchanged. The profile check lives
 * ONLY here (isolated resolver) so no other service scatters {@code isUat()} branches.
 */
@Component
public class UatShinhanWooriOverride {

    private static final String SHINHAN = "970424";
    private static final String WOORI   = "970457";

    private static final String PRDT_TYPE_WEBCASH   = "F";
    private static final String PRDT_TYPE_HAENGSUNG = "H";

    private final boolean uat;

    public UatShinhanWooriOverride(Environment environment) {
        this.uat = environment.acceptsProfiles(Profiles.of("uat"));
    }

    /** Overridden outbound {@code (bankCd, prtnId)} pair for the inquiry request. */
    public record Override(String bankCd, String prtnId) {}

    /**
     * Resolve the outbound BANK_CD / PRTN_ID for a depositor-name inquiry.
     *
     * @param tarBankCd the target bank the inquiry addresses (NAPAS code) — the legacy key
     * @param prdtType  the tenant product type (verbatim {@code BUSI_INFM.PRDT_TYPE} letter)
     * @param bankCd    firm-config {@code OUT_BANK_CD} (returned unchanged when no override applies)
     * @param prtnId    firm-config {@code BANK_PRTN_ID} (returned unchanged when no override applies)
     * @return the (possibly overridden) pair — original values on non-UAT / unrelated bank / other product type
     */
    public Override resolve(String tarBankCd, String prdtType, String bankCd, String prtnId) {
        if (!uat || tarBankCd == null || prdtType == null) {
            return new Override(bankCd, prtnId);
        }
        if (SHINHAN.equals(tarBankCd)) {
            if (PRDT_TYPE_WEBCASH.equals(prdtType))   return new Override(SHINHAN, "WEBC1");
            if (PRDT_TYPE_HAENGSUNG.equals(prdtType)) return new Override(SHINHAN, "HASU1");
        } else if (WOORI.equals(tarBankCd)) {
            if (PRDT_TYPE_WEBCASH.equals(prdtType))   return new Override(WOORI, "000001008");
            if (PRDT_TYPE_HAENGSUNG.equals(prdtType)) return new Override(WOORI, "000001020");
        }
        return new Override(bankCd, prtnId);
    }

    /** Whether the UAT override is active (the current profile is {@code uat}). */
    public boolean isActive() {
        return uat;
    }
}
