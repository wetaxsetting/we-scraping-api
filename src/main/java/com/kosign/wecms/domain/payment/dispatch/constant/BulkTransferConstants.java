package com.kosign.wecms.domain.payment.dispatch.constant;

/**
 * Bulk-transfer domain constants ported verbatim from the legacy batch
 * constants class {@code consts.WCConst}
 * ({@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/consts/WCConst.java}).
 *
 * <p>Only the values that are stable business facts (bank codes, per-bank
 * processing delays, financial-media division, currency, organisation status,
 * legacy {@code BATCH_ID}s) are carried here. Endpoint paths, URLs and API keys
 * are environment/secret config and live in {@code EfsProperties} /
 * {@code SftpProperties} — never as constants.
 */
public final class BulkTransferConstants {

    private BulkTransferConstants() {
    }

    // ── 은행코드 / bank codes ────────────────────────────────────────────────
    /** 신한은행 / Shinhan Bank EFS org code. */
    public static final String BANK_CD_SHINHAN = "970424";
    /** 우리은행 / Woori Bank EFS org code. */
    public static final String BANK_CD_WOORI = "970457";

    // ── 은행 별 처리 딜레이(ms) / per-bank pacing delay ──────────────────────
    // NOTE: these mirror legacy WCConst verbatim and have no caller. The LIVE pacing values are
    // DispatchPacer's, which since 2026-08-05 carry the same three numbers (the 2026-08-01 divergence
    // raising the default to 3000 was withdrawn — handover 05 §5.3 / 06 §6.3 document 100 ms as the
    // 기본 딜레이). Still read DispatchPacer, not this class, for the rate the application sends at.
    public static final int PER_DELAY_DEFAULT = 100;
    public static final int PER_DELAY_SHINHAN = 100;
    public static final int PER_DELAY_WOORI = 3000;

    // ── 파일회차 상한 / file-round (FILE_SEQ) ceilings ───────────────────────
    // Legacy IDO.TB_FIRM_FILE_MST_R002.xml:34-49 wraps the next round back to 1:
    //   WHEN OUT_BANK_CD = '970457' THEN (MAX+1 > 99  ? 1 : MAX+1)   -- 우리은행: 1~99
    //   ELSE                             (MAX+1 > 999 ? 1 : MAX+1)   -- 다른 은행: 1~999
    // FILE_SEQ is a 3-byte fixed-length header field and a name segment, so exceeding the
    // ceiling is bank-visible, and exceeding 999 additionally breaks the 1000-byte record.
    /** 우리은행 / Woori — rounds run 1..99. */
    public static final int FILE_ROUND_MAX_WOORI = 99;
    /** 그 외 은행 / every other bank — rounds run 1..999. */
    public static final int FILE_ROUND_MAX_DEFAULT = 999;

    // ── 대상 구분 / financial-media (target) division ────────────────────────
    /** {@code A} — 계좌 / account. */
    public static final String TAR_FINC_MDA_DIV_ACCT = "A";
    /** {@code C} — 카드 / card. */
    public static final String TAR_FINC_MDA_DIV_CARD = "C";

    // ── 통화코드 / currency ──────────────────────────────────────────────────
    public static final String CURRENCY_CD_VND = "VND";

    // ── 기관상태 / organisation status (TB_FIRM_CONF.ORG_STAT) ───────────────
    /** {@code 0} — 대기 / waiting. */
    public static final String ORG_ST_WAIT = "0";
    /** {@code 1} — 거래가능 / active (only state in which a file may be sent). */
    public static final String ORG_ST_OK = "1";
    /** {@code 2} — 일시정지 / suspended. */
    public static final String ORG_ST_STOP = "2";
    /** {@code 3} — 해지 / terminated. */
    public static final String ORG_ST_OUT = "3";

    // ── 레거시 BATCH_ID / legacy operating-hours schedule keys ────────────────
    // Used as the lookup key into TB_BATCH_SCHEDULE_INFO / _DTL (EXE_DIV window).
    public static final String BATCH_ID_NAPAS_REAL_TIME = "NAPAS_REAL_TIME_BATCH";
    public static final String BATCH_ID_FILE_DEPOSIT_TRANSFER_1 = "FILE_DEPOSIT_TRANSFER_BATCH1";
}
