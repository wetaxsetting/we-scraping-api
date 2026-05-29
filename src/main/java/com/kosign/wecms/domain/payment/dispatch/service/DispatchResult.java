package com.kosign.wecms.domain.payment.dispatch.service;

/**
 * Per-line dispatch-result projection returned by
 * {@link DispatchResultResolver}. Mirrors the columns the legacy
 * {@code SCDUL_PYMN_SUMR_R006} (and {@code OVRS_PYMN_SUMR_R006/R007})
 * IDO queries surface via the LEFT JOIN against
 * {@code TB_PAY_TARGET} / {@code TB_PAY_FILE_MST} /
 * {@code TB_PAY_FILE_DATA} / {@code TB_FIRM_TRAN}.
 *
 * <p>All fields are nullable so a line that hasn't been dispatched
 * yet (or whose batch never reached the bank-side daemon) round-trips
 * as null on the wire. The frontend's Result detail page already
 * handles this gracefully — empty Date/Time + neutral Result chip.
 */
public record DispatchResult(
        /** Legacy proc-stat code: '0'=success, '3'=failed, '6'/'9'=pending. */
        String procStat,
        /** i18n key resolved from {@link #procStat} via
         *  {@link ProcStatKey#forCode}. Frontend renders via {@code t(key)}. */
        String procStatKey,
        /** Bank-side processing datetime (yyyyMMddHHmmss). */
        String procDttm,
        /** Bank-side result datetime — typically same as {@link #procDttm}. */
        String rsltDttm,
        /** Bank response code (e.g. '0000' on success, error-specific otherwise). */
        String rsCd,
        /** Human-readable bank response message — only set on the API path. */
        String rsMsg) {

    /** Empty result used when no dispatch row exists yet for the line. */
    public static final DispatchResult EMPTY = new DispatchResult(
            null, null, null, null, null, null);
}
