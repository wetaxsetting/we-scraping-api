package com.kosign.wecms.domain.banking.dto;

/**
 * One row in the Smart Organizer candidate list — a payment transaction
 * that could match the bank transaction the popup is anchored on.
 *
 * <p>Mirrors the legacy {@code comm_0196_002_r001} record shape, scoped
 * to domestic scheduled payments in the first refactor slice (overseas
 * {@code OVRS_PYMN} is deferred per scope decision 2026-05-16).
 *
 * <p>{@code payTrnsId} is the composite identifier the match-create
 * endpoint uses to identify a candidate: legacy uses {@code
 * SCDUL_PYMN_NO + "_" + SCDUL_PYMN_DETL_NO} for domestic detail-level
 * matches. The frontend ships this verbatim in the match request.
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 */
public record MatchCandidateRow(
        String payTrnsId,
        /** {@code 'S'} = domestic scheduled (the only variant in the first slice). */
        String payTypeCd,
        String scdulPymnNo,
        String scdulPymnDetlNo,
        String scdulPymnTypeCd,
        String trnfDt,
        String trnfDtm,
        String tranAmt,
        String trnfCcyCd,
        String rcptNm,
        String bnfcBankCd,
        String bnfcBankNm,
        String bnfcAcctNo,
        String tranUsag,
        String titl
) { }
