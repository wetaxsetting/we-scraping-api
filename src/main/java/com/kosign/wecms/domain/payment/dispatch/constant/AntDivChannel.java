package com.kosign.wecms.domain.payment.dispatch.constant;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical {@code ANT_DIV_CD} (other-bank network / channel) vocabulary shared by
 * the two operating-hours gates.
 *
 * <h2>Why this exists — three columns, three vocabularies</h2>
 * There is no single {@code ANT_DIV_CD} vocabulary, and the widths are <b>not</b>
 * drift. Each column applies its own vocabulary consistently:
 * <ul>
 *   <li>{@code TB_PAY_TARGET.ANT_DIV_CD} — {@code bpchar(5)}, holds
 *       {@code wabooks.enums.AntDivCd.getValue()}. That enum is <b>mixed-width by
 *       design</b>: {@code INTER("INTER")}, {@code NAPAS("NAPAS")},
 *       {@code CITAD("CITAD")} sit in the same enum body as {@code SWIFT("S")},
 *       {@code EBANK("E")}, {@code VCB("V")}. Verified on JEX DEV 2026-07-28:
 *       {@code CITAD} 39 · {@code NAPAS} 30 · {@code INTER} 28 · {@code 'E'} 2 ·
 *       {@code 'S'} 2 — <b>all 101 rows conform to the enum</b>. The column is
 *       5 bytes wide because {@code INTER}/{@code NAPAS}/{@code CITAD} need 5, not
 *       because every value is 5 chars. {@code 'E'} and {@code 'S'} are the correct
 *       values for EBANK and SWIFT, not anomalies.</li>
 *   <li>{@code TB_PAY_FILE_DATA.ANT_DIV_CD} — {@code bpchar(1)}, holds
 *       {@code wabooks.enums.AntDivCdT.getValue()} ({@code E/N/C/S/V}). This is the
 *       1-byte wire slot at offset 62 of the D record; classified verbatim by
 *       {@code IDO.TB_PAY_FILE_DATA_R005.xml}.</li>
 *   <li>{@code TB_BATCH_SCHEDULE_DTL.ANT_DIV_CD} — {@code bpchar(5)}, holds channel
 *       <b>names</b> ({@code EBANK}/{@code NAPAS}/{@code CITAD}/{@code SWIFT}/{@code VCB}).
 *       These are enum <i>names</i>, matching neither enum's {@code getValue()}.</li>
 * </ul>
 *
 * <p><b>Legacy performs the {@code E → EBANK} expansion itself</b> on the bulk path:
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1:301-381} reads 1-char counts out of
 * {@code TB_PAY_FILE_DATA} (via {@code R005}) and then calls
 * {@code operTimeChk(bankCd, "EBANK"/"VCB"/"CITAD"/"NAPAS"/"SWIFT")} with the
 * <b>hard-coded 5-char name</b>. So {@link #fromStorageCode} normalises in the same
 * direction legacy does, at the same point in the same flow.
 *
 * <p>{@link #canonical} normalises in that same direction but on a <b>different
 * path</b> — the realtime gate, where legacy {@code NAPAS_REAL_TIME_BATCH:144}
 * passes the {@code TB_PAY_TARGET} value <b>verbatim</b>. That is a deliberate
 * divergence; see {@code OperatingHoursService.evaluateRealtime} for exactly what it
 * does and does not achieve.
 *
 * <p><b>Not a behaviour change to the windows themselves</b> — it only decides
 * which existing schedule row a given {@code ANT_DIV_CD} refers to.
 *
 * @see com.kosign.wecms.domain.payment.internaltransfer.service.TransferNetwork
 */
public final class AntDivChannel {

    private AntDivChannel() {
    }

    /** 전자금융 / e-banking channel. */
    public static final String EBANK = "EBANK";
    /** Vietcombank channel (a destination network — NOT the withdrawing bank). */
    public static final String VCB = "VCB";
    /** CITAD (SBV inter-bank) channel. */
    public static final String CITAD = "CITAD";
    /** NAPAS channel. */
    public static final String NAPAS = "NAPAS";
    /** SWIFT (overseas) channel. */
    public static final String SWIFT = "SWIFT";
    /**
     * 당행 / own-bank code. Not a schedule channel — the realtime gate
     * short-circuits on it and never performs a DTL lookup
     * (legacy {@code NAPAS_REAL_TIME_BATCH.java:138}).
     */
    public static final String INTER = "INTER";

    // ── 1-char storage codes (TB_PAY_FILE_DATA.ANT_DIV_CD, bpchar(1)) ────────
    // Values mirror the legacy enum {@code wabooks.enums.AntDivCdT}.

    /** {@code 'E'} — EBANK. */
    public static final String CODE_EBANK = "E";
    /** {@code 'V'} — VCB channel. */
    public static final String CODE_VCB = "V";
    /** {@code 'C'} — CITAD. */
    public static final String CODE_CITAD = "C";
    /** {@code 'N'} — NAPAS. */
    public static final String CODE_NAPAS = "N";
    /** {@code 'S'} — SWIFT. */
    public static final String CODE_SWIFT = "S";

    /**
     * 1-char {@code TB_PAY_FILE_DATA.ANT_DIV_CD} storage code → channel name.
     * Verified against {@code IDO.TB_PAY_FILE_DATA_R005.xml} and the legacy
     * per-channel guards at {@code FILE_DEPOSIT_TRANSFER_BATCH1.java:299-397}.
     */
    private static final Map<String, String> BY_STORAGE_CODE = Map.of(
            CODE_EBANK, EBANK,
            CODE_VCB, VCB,
            CODE_CITAD, CITAD,
            CODE_NAPAS, NAPAS,
            CODE_SWIFT, SWIFT);

    /**
     * Channel name → 1-char storage code. The inverse of {@link #BY_STORAGE_CODE}.
     * {@link #INTER} is deliberately absent — it is an own-bank marker, not a
     * file channel, and the legacy {@code AntDivCdT} enum has no {@code INTER}
     * constant either.
     */
    private static final Map<String, String> CODE_BY_NAME = Map.of(
            EBANK, CODE_EBANK,
            VCB, CODE_VCB,
            CITAD, CODE_CITAD,
            NAPAS, CODE_NAPAS,
            SWIFT, CODE_SWIFT);

    /** The 5-char names that are already canonical. */
    private static final Map<String, String> KNOWN_NAMES = Map.of(
            EBANK, EBANK,
            VCB, VCB,
            CITAD, CITAD,
            NAPAS, NAPAS,
            SWIFT, SWIFT,
            INTER, INTER);

    /**
     * Strict mapping for the <b>bulk / file</b> path: a 1-char
     * {@code TB_PAY_FILE_DATA.ANT_DIV_CD} to its schedule channel name.
     *
     * <p>Returns {@code null} for {@code null}, blank, or any unrecognised code —
     * legacy {@code ETC_CNT}, which is deliberately <b>not</b> operating-hours
     * gated ({@code R005} lumps everything outside {@code E/V/C/N/S} into
     * {@code ETC_CNT}, and no {@code operTimeChk} call is made for it).
     */
    public static String fromStorageCode(String antDivCd) {
        if (antDivCd == null) {
            return null;
        }
        String key = antDivCd.trim().toUpperCase(Locale.ROOT);
        return key.isEmpty() ? null : BY_STORAGE_CODE.get(key);
    }

    /**
     * Lenient canonical schedule key for the <b>realtime</b> path, where
     * {@code TB_PAY_TARGET.ANT_DIV_CD} may hold either width.
     *
     * <p>Rules, in order:
     * <ol>
     *   <li>{@code null} / blank → {@code null} (caller decides; the realtime
     *       gate treats it as a non-{@code INTER} value and looks it up, which
     *       misses and yields {@code NO_CRITERIA} — unchanged legacy outcome).</li>
     *   <li>Trailing {@code bpchar} padding is stripped and the value upper-cased.</li>
     *   <li>A 1-char {@code E/V/C/N/S} expands to its channel name.</li>
     *   <li>A known 5-char name passes through unchanged.</li>
     *   <li>Anything else is returned trimmed/upper-cased but otherwise verbatim,
     *       so an unknown code still misses the schedule lookup and still fails
     *       the row — the legacy outcome — rather than being silently coerced to a
     *       channel it does not belong to. HAENGSUNG's ERP import can pass through
     *       raw values such as {@code 'T'} (documented unresolved M-3/M-12 in
     *       {@code ErpPaymentRefreshMasterImporter}); those intentionally keep
     *       failing loudly instead of being guessed at.</li>
     * </ol>
     */
    public static String canonical(String antDivCd) {
        if (antDivCd == null) {
            return null;
        }
        String key = antDivCd.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) {
            return null;
        }
        String known = KNOWN_NAMES.get(key);
        if (known != null) {
            return known;
        }
        String expanded = BY_STORAGE_CODE.get(key);
        return expanded != null ? expanded : key;
    }

    /**
     * Narrow any {@code ANT_DIV_CD} spelling to the 1-char code that fits
     * {@code TB_PAY_FILE_DATA.ANT_DIV_CD} ({@code bpchar(1)}).
     *
     * <p>Direct port of legacy {@code wabooks.enums.AntDivCdT.parse(String)}:
     * it accepts the 1-char code <i>or</i> the channel name, is
     * case-insensitive, and returns <b>{@code null}</b> for anything it does not
     * recognise — including {@code INTER}, which legacy's {@code AntDivCdT} has
     * no constant for. The one addition over legacy is that the input is
     * <b>trimmed</b>: source columns are {@code bpchar}, so a stored {@code 'C'}
     * reads back as {@code "C    "} and an untrimmed name comparison would miss.
     *
     * <p><b>Unknown values are never guessed.</b> Returning {@code null} keeps
     * the row out of the operating-hours channel set, which is the legacy
     * outcome — a payment is not silently routed onto a channel nobody chose.
     * Callers writing to {@code bpchar(1)} MUST route through this method; the
     * 5-char detail value ({@code SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD} is
     * {@code bpchar(5)}) must never be copied across directly.
     *
     * @param antDivCd a 1-char code, a channel name, or {@code null} / blank
     * @return the 1-char storage code, or {@code null} when unrecognised
     */
    public static String toStorageCode(String antDivCd) {
        String canonical = canonical(antDivCd);
        return canonical == null ? null : CODE_BY_NAME.get(canonical);
    }

    /**
     * <b>Legacy-compatible</b> narrowing for {@code bpchar(1)} — the exact semantics of legacy
     * {@code wabooks.enums.AntDivCdT.fromValue(String)}.
     *
     * <p>Accepts <b>only</b> the 1-char storage codes {@code E/V/C/N/S} and returns
     * <b>{@code null}</b> for everything else — including the long names
     * ({@code "CITAD"} → {@code null}), {@code INTER}, and unknown codes.
     *
     * <h2>Why this exists alongside {@link #toStorageCode(String)}</h2>
     * Legacy has <b>two</b> converters and the choice is per payment type, not a style preference:
     * <table border="1">
     *   <caption>legacy converter split</caption>
     *   <tr><th>Legacy method</th><th>{@code "CITAD"} →</th><th>Legacy caller</th><th>Refactor equivalent</th></tr>
     *   <tr><td>{@code AntDivCdT.parse}</td><td>{@code 'C'}</td>
     *       <td>Payroll — {@code pymn_0005_001_c001_act.jsp:226}</td>
     *       <td>{@link #toStorageCode(String)}</td></tr>
     *   <tr><td>{@code AntDivCdT.fromValue}</td><td><b>{@code null}</b></td>
     *       <td>{@code TbPayFileData.copyFrom} ({@code TbPayFileData.java:301}) — i.e. Vendor,
     *           Expense, Internal Transfer</td>
     *       <td><b>this method</b></td></tr>
     * </table>
     *
     * <p>So {@code toStorageCode} must <b>not</b> be substituted here: it would give Vendor / Expense /
     * Internal Transfer a channel legacy never assigned, and the file would then be gated by that
     * channel's operating window instead of being skipped — a money-routing behaviour change. Conversely
     * this method must not be used for Payroll, which legitimately relies on {@code parse}.
     *
     * <p>One deliberate addition over legacy: the input is <b>trimmed</b>. Source columns are
     * {@code bpchar}, so a stored {@code 'C'} reads back as {@code "C    "}; legacy {@code fromValue}
     * does a bare {@code equals} and would reject it. Trailing padding is never semantically meaningful,
     * and PostgreSQL itself clips trailing spaces when assigning to {@code character(n)} — verified:
     * {@code bpchar('C    '::bpchar, 5, false)} → {@code 'C'}, while
     * {@code bpchar('CITAD'::bpchar, 5, false)} raises {@code value too long for type character(1)}.
     *
     * @param antDivCd a 1-char storage code, or any other value
     * @return the 1-char storage code, or {@code null} when it is not one of {@code E/V/C/N/S}
     */
    public static String toStorageCodeStrict(String antDivCd) {
        if (antDivCd == null) {
            return null;
        }
        String key = antDivCd.trim().toUpperCase(Locale.ROOT);
        return BY_STORAGE_CODE.containsKey(key) ? key : null;
    }
}
