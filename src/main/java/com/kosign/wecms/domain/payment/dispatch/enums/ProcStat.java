package com.kosign.wecms.domain.payment.dispatch.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Processing status — value set of the legacy {@code PROC_STAT} column on
 * {@code TB_PAY_FILE_MST}, {@code TB_FIRM_FILE_MST}, {@code TB_PAY_TARGET}
 * and {@code TB_FIRM_TRAN}.
 *
 * <p>Ported verbatim from the legacy batch constants
 * {@code consts.WCConst.PROC_ST_*}
 * ({@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/consts/WCConst.java}):
 * <pre>
 *   PROC_ST_SUCC = "0"  // 정상   (success)
 *   PROC_ST_FAIL = "3"  // 실패   (failure)
 *   PROC_ST_REQ  = "6"  // 요청(처리중) (requested / in progress)
 *   PROC_ST_WAIT = "9"  // 대기   (waiting)
 * </pre>
 *
 * <p>Follows the modernized refactor enum idiom of
 * {@link com.kosign.wecms.common.enums.InoutDv}: {@code (code)} only, no
 * JEX {@code GenericEnum}, no {@code @JsonValue}/{@code @JsonCreator}, and
 * <b>never</b> persisted under {@code @Enumerated} — the DB column stays
 * {@code CHAR(1)} holding the legacy code. {@link #fromCode(String)} is
 * lenient (returns {@link Optional#empty()} for unknown values, never throws).
 *
 * <p>Verified against the live {@code wecmsdevdb.tb_firm_file_mst.proc_stat}
 * ({@code CHAR(1)}) / {@code tb_firm_tran.proc_stat} ({@code VARCHAR(1)}) on
 * 2026-06-08.
 */
public enum ProcStat {

    /** {@code 0} — 정상 / success. */
    SUCC("0"),

    /** {@code 3} — 실패 / failure. */
    FAIL("3"),

    /** {@code 6} — 요청(처리중) / requested, in progress. */
    REQ("6"),

    /** {@code 9} — 대기 / waiting (the initial state BATCH1 picks up). */
    WAIT("9");

    private final String code;

    ProcStat(String code) {
        this.code = code;
    }

    /** Raw 1-char code stored in the {@code PROC_STAT} column. */
    public String code() {
        return code;
    }

    public static Optional<ProcStat> fromCode(String raw) {
        return Arrays.stream(values()).filter(e -> e.code.equals(raw)).findFirst();
    }
}
