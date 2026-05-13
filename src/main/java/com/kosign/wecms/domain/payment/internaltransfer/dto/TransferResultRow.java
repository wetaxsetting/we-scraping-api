package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;

/**
 * One row in the Transfer Result list response. Read-only — surfaces
 * batches that have either dispatched ({@code trnfYn='Y'}) or are
 * actively in the queue.
 *
 * <p>{@code totTrnsSuccCnt} / {@code totTrnsErrorCnt} are computed
 * from the dispatch tables (TB_PAY_TARGET / TB_PAY_FILE_DATA) once
 * those land. Phase A returns null for these fields until the dispatch
 * tables are migrated.
 */
public record TransferResultRow(
        String     scdulPymnNo,
        String     scdulPymnTypeCd,
        String     ccyCd,
        String     titl,
        BigDecimal totalTranAmt,
        BigDecimal totTrnsCnt,
        BigDecimal totTrnsSuccCnt,
        BigDecimal totTrnsErrorCnt,
        String     trnfStts,
        String     regDtm,
        String     apprSeqNo,
        String     docNo) { }
