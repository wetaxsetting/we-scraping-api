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
        /**
         * <b>Request</b> datetime — the master's {@code TRNF_DTM} (SCDUL family) /
         * {@code TRNF_DT} (Overseas), i.e. when the batch was dispatched. Same value on
         * every line of a batch; present even for a line with no dispatch row. Legacy
         * {@code SCDUL_PYMN_SUMR_R006.xml:89} / {@code OVRS_PYMN_SUMR_R007.xml:75}.
         */
        String procDttm,
        /**
         * <b>Result</b> datetime — the dispatch row's {@code PROC_DTTM}
         * ({@code TB_PAY_FILE_MST} on the File/Overseas route, {@code TB_PAY_TARGET} on the
         * API route), i.e. when the bank reported back. A <i>different</i> column from
         * {@link #procDttm}; null until the bank-side result lands. Legacy
         * {@code R006.xml:96-100} / {@code R007.xml:77}.
         */
        String rsltDttm,
        /** Bank response code (e.g. '0000' on success, error-specific otherwise). */
        String rsCd,
        /**
         * Human-readable bank response message. Set on the SCDUL <b>API</b> path
         * ({@code TB_FIRM_TRAN.RS_MSG}) and on the <b>Overseas</b> path
         * ({@code TB_PAY_FILE_MST.RS_MSG}); always null on the SCDUL File path, where legacy
         * {@code R006} selects {@code NULL} outright.
         */
        String rsMsg) {

    /** Empty result used when no dispatch row exists yet for the line. */
    public static final DispatchResult EMPTY = new DispatchResult(
            null, null, null, null, null, null);
}
