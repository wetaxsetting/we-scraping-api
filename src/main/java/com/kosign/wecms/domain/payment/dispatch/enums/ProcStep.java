package com.kosign.wecms.domain.payment.dispatch.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Processing step — value set of the legacy {@code PROC_STEP} column on
 * {@code TB_FIRM_FILE_MST}. Tracks where a bulk-transfer file sits in the
 * reception → execution lifecycle.
 *
 * <p>Ported verbatim from {@code consts.WCConst.PROC_STP_*}
 * ({@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/consts/WCConst.java}):
 * <pre>
 *   PROC_STP_REQ          = "0"  // 접수요청              (reception requested — BATCH1 sends 00301)
 *   PROC_STP_REQ_RECV     = "1"  // 접수요청 결과 수신     (reception result received — 01301 callback)
 *   PROC_STP_DEPO_REQ     = "2"  // 입금이체 실행요청       (execution requested — 00302)
 *   PROC_STP_DEPO_RES_RECV= "3"  // 입금이체 실행요청 결과수신 (execution result received — 01302 callback)
 * </pre>
 *
 * <p>Legacy state machine (verified against {@code FILE_DEPOSIT_TRANSFER_BATCH1/2}
 * + {@code FILE_DEPOSIT_TRANSFER_API1/2_act.jsp}): the step only ever advances
 * {@code 0 → 1 → 2 → 3}, never backward.
 *
 * <p><b>TODO(K9):</b> in the confirmed TOBE, 00302 (step {@code 1 → 2}) is
 * issued inline from the 01301 callback instead of by the removed BATCH2 sweep.
 * The recovery rule for a file stranded at {@code REQ_RECV} ("1") — when the
 * inline 00302 never fires or fails — is the open K9 decision and is NOT
 * implemented here.
 *
 * <p>Same modernized idiom as {@link com.kosign.wecms.common.enums.InoutDv}.
 * The DB column is {@code CHAR(1)}; never persisted via {@code @Enumerated}.
 */
public enum ProcStep {

    /** {@code 0} — 접수요청 / reception requested (BATCH1 → 00301). */
    REQ("0"),

    /** {@code 1} — 접수요청 결과 수신 / reception result received (01301 callback). */
    REQ_RECV("1"),

    /** {@code 2} — 입금이체 실행요청 / execution requested (00302). */
    DEPO_REQ("2"),

    /** {@code 3} — 입금이체 실행요청 결과수신 / execution result received (01302 callback). */
    DEPO_RES_RECV("3");

    private final String code;

    ProcStep(String code) {
        this.code = code;
    }

    /** Raw 1-char code stored in {@code TB_FIRM_FILE_MST.PROC_STEP}. */
    public String code() {
        return code;
    }

    public static Optional<ProcStep> fromCode(String raw) {
        return Arrays.stream(values()).filter(e -> e.code.equals(raw)).findFirst();
    }
}
