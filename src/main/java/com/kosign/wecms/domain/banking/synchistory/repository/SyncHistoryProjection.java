package com.kosign.wecms.domain.banking.synchistory.repository;

/**
 * Flat projection of one sync-history row joined with its
 * {@code ACCT_MSTR} row. Used by
 * {@link ExtTranCallHisRepository#findSyncHistory} to ship every column
 * the popup needs in a single query — avoids per-row N+1 nickname /
 * type lookups. Field order matches the JPQL {@code new ...} projection
 * constructor.
 */
public record SyncHistoryProjection(
        String refDate,
        java.math.BigDecimal seqNo,
        String bankCd,
        String acctNo,
        String acctNickNm,
        String acctTypeCd,
        String ccyCd,
        String startDate,
        String endDate,
        String rsltCd,
        String errCont,
        String regDtm
) { }
