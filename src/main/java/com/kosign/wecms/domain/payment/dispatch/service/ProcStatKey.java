package com.kosign.wecms.domain.payment.dispatch.service;

/**
 * Maps the legacy {@code PROC_STAT} code (single-character / single-digit)
 * to the matching {@code payment.proc_stat.*} i18n key the frontend
 * Result detail page resolves via {@code t(key)}.
 *
 * <p>Codes are from the legacy {@code ProcStat} / {@code ProcStatF}
 * enums (PymnHelper:2280 / etc.):
 * <ul>
 *   <li>{@code '0'} — success</li>
 *   <li>{@code '3'} — failed</li>
 *   <li>{@code '6'} — transfer pending (API path, written by dispatch)</li>
 *   <li>{@code '9'} — waiting (File path, written by dispatch)</li>
 * </ul>
 *
 * <p>Unknown codes fall back to {@code in_progress} — the neutral
 * "neither success nor explicit failure" state that the legacy two-status
 * rule (see {@code pymn_0002_001.ts:443-450}) reads as "still moving
 * through the bank pipeline".
 */
public final class ProcStatKey {

    private ProcStatKey() {}

    public static final String SUCCESS          = "payment.proc_stat.success";
    public static final String FAILED           = "payment.proc_stat.failed";
    public static final String TRANSFER_PENDING = "payment.proc_stat.transfer_pending";
    public static final String PENDING          = "payment.proc_stat.pending";
    public static final String IN_PROGRESS      = "payment.proc_stat.in_progress";

    /**
     * Returns the i18n key for {@code procStat}, or {@code null} when
     * the code is null / blank (the wire carries null and the frontend
     * renders the neutral state).
     */
    public static String forCode(String procStat) {
        if (procStat == null || procStat.isBlank()) return null;
        return switch (procStat.trim()) {
            case "0" -> SUCCESS;
            case "3" -> FAILED;
            case "6" -> TRANSFER_PENDING;
            case "9" -> PENDING;
            default  -> IN_PROGRESS;
        };
    }
}
