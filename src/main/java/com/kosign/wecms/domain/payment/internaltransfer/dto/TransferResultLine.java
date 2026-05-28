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
        String     rsMsg) { }
