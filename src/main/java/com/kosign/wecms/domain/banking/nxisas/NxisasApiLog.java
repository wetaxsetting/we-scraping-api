package com.kosign.wecms.domain.banking.nxisas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common structured debug logging for every server-side NXISAS API
 * scraping flow (Bank Transaction Sync VND/FCY, Bank Login Credentials
 * setup, card scraping, FX, and the OTP/QNA/CAPTCHA continuations). The
 * refactor analogue of legacy {@code nxisas_api.js}'s {@code console.log}
 * trace — {@code nxisas_api.js} is reference only and is never loaded or
 * executed.
 *
 * <p>Every line is emitted on the dedicated {@code NXiSAS-API} logger in a
 * grep-friendly shape so one scrape can be followed end-to-end across
 * multiple HTTP requests (start → challenge → submit → final):
 *
 * <pre>
 * [NXiSAS-API][traceId=&lt;sessionId&gt;][flow=SYNC] GROUP_START bankCd=970424 accounts=2
 * </pre>
 *
 * <p><b>traceId</b> is the scrape session id ({@code NxisasScrapeSession
 * .sessionId}) — the one value already shared across the start request,
 * every challenge response, every OTP/QNA/CAPTCHA submit, and the final
 * result (the frontend echoes it back as {@code session_id}). Reusing it
 * means browser, Spring, and gateway logs all correlate on the same id
 * without any new wire field.
 *
 * <p><b>Levels:</b> milestone events are INFO and always safe (fully
 * masked). The verbose masked request/response body dump lives at DEBUG on
 * the gateway logger (see {@link NxisasGatewayClient}). Enable the verbose
 * tier with {@code logging.level.NXiSAS-API=DEBUG} (or the gateway package).
 *
 * <p><b>Never pass a raw secret as a key/value.</b> Account numbers go
 * through {@link NxisasMask#acct}, session tokens through
 * {@link NxisasMask#token}; passwords / OTP / CAPTCHA / QNA answers are
 * never logged at all.
 */
public final class NxisasApiLog {

    private NxisasApiLog() { }

    /** Dedicated logger so ops can grep / level-tune the whole flow. */
    private static final Logger LOG = LoggerFactory.getLogger("NXiSAS-API");

    /** Flow discriminators — match the frontend {@code flowType}. One per
     *  server-side NXISAS scraping screen. */
    public static final String FLOW_BANK_SYNC = "BANK_SYNC";                       // Account Inquiry VND/FCY sync
    public static final String FLOW_BANK_SETUP = "BANK_SETUP";                      // Bank Login Credentials verify
    public static final String FLOW_CARD_TRANSACTIONS = "CARD_TRANSACTIONS";        // Card Transactions list (read-only)
    public static final String FLOW_CARD_UPDATE_TRANSACTIONS = "CARD_UPDATE_TRANSACTIONS"; // Card Transactions "Update" scrape
    public static final String FLOW_CARD_MANAGEMENT_SETTING = "CARD_MANAGEMENT_SETTING";   // Card Management add-card scrape
    public static final String FLOW_GATEWAY = "GATEWAY";                            // shared gateway-level line

    /**
     * Emit one milestone line. {@code kv} is a flat key,value,key,value…
     * varargs list; null trace/flow degrade to {@code "-"}. Values are the
     * caller's responsibility to mask — pass {@link NxisasMask#acct} /
     * {@link NxisasMask#token} outputs, never raw secrets.
     */
    public static void event(String traceId, String flow, String event, Object... kv) {
        if (!LOG.isInfoEnabled()) return;
        LOG.info(line(traceId, flow, event, kv));
    }

    /** DEBUG-tier variant for high-volume / verbose milestones. */
    public static void debug(String traceId, String flow, String event, Object... kv) {
        if (!LOG.isDebugEnabled()) return;
        LOG.debug(line(traceId, flow, event, kv));
    }

    /** True when the verbose ({@code DEBUG}) tier is enabled — lets callers
     *  skip building an expensive masked-JSON body when it would be
     *  discarded. */
    public static boolean isDebug() {
        return LOG.isDebugEnabled();
    }

    private static String line(String traceId, String flow, String event, Object... kv) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("[NXiSAS-API][traceId=").append(traceId == null ? "-" : traceId)
          .append("][flow=").append(flow == null ? "-" : flow).append("] ")
          .append(event);
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                sb.append(' ').append(kv[i]).append('=').append(kv[i + 1]);
            }
        }
        return sb.toString();
    }
}
