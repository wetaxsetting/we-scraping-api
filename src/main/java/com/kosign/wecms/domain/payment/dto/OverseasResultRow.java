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
 * <p>Note: {@code success_count} / {@code failed_count} are not yet tracked
 * at the summary level — returned as 0 until individual transaction-result
 * tables are ported in a future sprint.
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
        Integer successCount,
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
        String apprSeqNo
) {}
