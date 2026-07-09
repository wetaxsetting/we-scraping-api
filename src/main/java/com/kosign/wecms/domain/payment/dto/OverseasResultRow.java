package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;

/**
 * Single row in the overseas-payment result list.
 * Wire names auto-derived via global Jackson SNAKE_CASE strategy.
 *
 * <p>Mirrors the payment-result view (JEX {@code pymn_0007_003}) —
 * batch records from {@code OVRS_PYMN_SUMR} that have been submitted
 * for processing, joined with {@code EXPT_RESOL_MSTR} for approval info.
 *
 * <p>{@code success_count} / {@code failed_count} / {@code prs_cnt} are the
 * per-batch transaction tallies computed from {@code TB_PAY_FILE_MST.PROC_STAT}
 * + {@code TB_PAY_FILE_DATA.RS_CD} (mirrors legacy
 * {@code IDO.OVRS_PYMN_SUMR_R005}); {@code trnf_stts} is the legacy computed
 * transfer status (F/P/C) driving the Completed / In-Progress display + filter.
 */
public record OverseasResultRow(
        String ovrsPymnNo,
        String ccyCd,
        String fileNm,
        BigDecimal totalTranAmt,
        Integer totTrnsCnt,
        String regDtm,
        String draftUserNm,
        String regId,
        String outBankNm,
        String outBankCd,
        String wdrlActNo,
        /** {@code "Y"} = completed / transferred; {@code "N"} = in progress. */
        String trnfYn,
        String docNo,
        String titl,
        /** Per-batch success-transaction count — {@code PROC_STAT='0' AND RS_CD='0000'}. */
        Integer successCount,
        /** Per-batch failed-transaction count — {@code PROC_STAT='3'} or {@code PROC_STAT='0' AND RS_CD<>'0000'}. */
        Integer failedCount,
        /** Scheduled transfer date — {@code EXPT_RESOL_MSTR.PAY_DATE} (yyyyMMdd). */
        String payDate,
        /** Approval workflow status code — {@code EXPT_RESOL_MSTR.APPR_TYPE_CD}. */
        String apprStts,
        /** Approver display name — complex join deferred; null for now. */
        String apprvrNm,
        /** F4b — approval-document key ({@code OVRS_PYMN_SUMR.APPR_SEQ_NO}). Wire
         *  {@code appr_seq_no}; the frontend maps it to {@code approvalRefId} so the
         *  Approval Ref cell opens the shared Approval Details popup (legacy
         *  {@code comm_0195_001}). Null for drafts with no approval → plain text. */
        String apprSeqNo,
        /** Legacy computed transfer status (mirrors {@code IDO.OVRS_PYMN_SUMR_R005}
         *  {@code TRNF_STTS}): {@code 'F'} all error, {@code 'P'} some pending,
         *  {@code 'C'} otherwise. Drives the Completed / In-Progress display. */
        String trnfStts,
        /** Per-batch in-progress (pending) transaction count =
         *  {@code TOT_TRNS_CNT - (error + success)}. */
        Integer prsCnt
) {}
