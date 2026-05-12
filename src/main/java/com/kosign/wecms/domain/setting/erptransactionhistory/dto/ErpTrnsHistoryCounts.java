package com.kosign.wecms.domain.setting.erptransactionhistory.dto;

/**
 * Aggregate count projection — total + per-direction counts for a single
 * filter scope. Mirrors the legacy {@code IDO.ERP_TRNS_HSTR_R001_CNT}
 * output ({@code TOT_CNT} / {@code TOT_CNT_WECMS} / {@code TOT_CNT_ERP} /
 * {@code TOT_CNT_DELETED}).
 *
 * <p>Internal to the repository / service layer — never escapes onto the
 * wire; the service folds the four numbers into
 * {@link ErpTrnsHistoryListResponse}.
 *
 * <p>The aggregate query intentionally drops the {@code ERP_TYPE}
 * predicate from the filter set so all four counts reflect the same
 * date / type / search context — mirrors the legacy
 * {@code getDynamicCNT} helper at {@code comm_0219_001_r001_act.jsp:77-108}.
 */
public record ErpTrnsHistoryCounts(
        long totalCount,
        long totalCountWecms,
        long totalCountErp,
        long totalCountDeleted
) {

    /** Zero counts — used when the empty branch wants to skip the
     *  aggregate query (defensive — the JPQL `COUNT` query never returns
     *  null, but downstream code may want a non-null fallback). */
    public static ErpTrnsHistoryCounts empty() {
        return new ErpTrnsHistoryCounts(0L, 0L, 0L, 0L);
    }
}
