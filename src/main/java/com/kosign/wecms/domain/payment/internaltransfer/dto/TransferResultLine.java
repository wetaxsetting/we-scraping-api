package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;

/**
 * One row in the per-line table inside the Transfer Result detail
 * response.
 *
 * <p>Result fields ({@code procStat}, {@code procStatKey}, {@code rsCd},
 * {@code rsMsg}, {@code procDttm}, {@code rsltDttm}) are joined from
 * the dispatch tables. Phase A returns null for these fields until
 * the dispatch tables (TB_PAY_TARGET / TB_PAY_FILE_DATA / conn tables)
 * are migrated — see {@code wecms-api/src/main/resources/db/migration/V85}.
 *
 * <p><b>Banking-match read fields (Phase 1, additive):</b> {@code linkMatchYn},
 * {@code acctSeqNo}, {@code bankTrnsFullId} expose the per-line reconciliation
 * state from {@code SCDUL_PYMN_SUMR_DETL} ({@code bankTrnsShortId} was already
 * present). They are populated by the Vendor + Expense result services; siblings
 * that have not yet been wired (Internal Transfer, Payroll) call the
 * backward-compatible 22-arg constructor below and emit {@code null} for all
 * three. No write path yet — read-only exposure for the Result-detail cell.
 */
public record TransferResultLine(
        String     scdulPymnDetlNo,
        BigDecimal tranAmt,
        String     antDivCd,
        String     rcptNm,
        String     tarBankCd,
        /** Server-resolved human-readable bank name — same two-step
         *  lookup the Scheduled detail line uses (BANK_INFM → V86
         *  NAPAS / CITAD function → CITAD_BANK_CD canonical fallback).
         *  Null when nothing matches; frontend renders the code alone. */
        String     tarBankNm,
        String     fincMdaNo,
        String     tranUsag,
        String     ccyCd,
        String     trnfCcyCd,
        String     depoCcyCd,
        String     note,
        String     docNo,
        String     chartAcctCd,
        String     chartAcctSeqNo,
        String     bankTrnsShortId,
        // Deferred — populated once dispatch tables land.
        String     procStat,
        String     procStatKey,
        String     procDttm,
        String     rsltDttm,
        String     rsCd,
        String     rsMsg,
        // Banking-match read fields (Phase 1, additive — SCDUL_PYMN_SUMR_DETL).
        String     linkMatchYn,
        String     acctSeqNo,
        String     bankTrnsFullId) {

    /**
     * Backward-compatible constructor matching the pre-banking-match
     * (22-arg) signature. Lets sibling result services that have not been
     * wired for banking match (Internal Transfer, Payroll) construct a line
     * unchanged; the three match fields default to {@code null}.
     */
    public TransferResultLine(
            String     scdulPymnDetlNo,
            BigDecimal tranAmt,
            String     antDivCd,
            String     rcptNm,
            String     tarBankCd,
            String     tarBankNm,
            String     fincMdaNo,
            String     tranUsag,
            String     ccyCd,
            String     trnfCcyCd,
            String     depoCcyCd,
            String     note,
            String     docNo,
            String     chartAcctCd,
            String     chartAcctSeqNo,
            String     bankTrnsShortId,
            String     procStat,
            String     procStatKey,
            String     procDttm,
            String     rsltDttm,
            String     rsCd,
            String     rsMsg) {
        this(scdulPymnDetlNo, tranAmt, antDivCd, rcptNm, tarBankCd, tarBankNm,
                fincMdaNo, tranUsag, ccyCd, trnfCcyCd, depoCcyCd, note, docNo,
                chartAcctCd, chartAcctSeqNo, bankTrnsShortId, procStat, procStatKey,
                procDttm, rsltDttm, rsCd, rsMsg,
                null, null, null);
    }
}
