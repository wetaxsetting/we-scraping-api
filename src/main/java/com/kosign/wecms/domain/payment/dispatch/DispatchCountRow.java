package com.kosign.wecms.domain.payment.dispatch;

/**
 * Spring Data projection for {@link DispatchCountRepository#countByBatch}.
 * One row per batch with the dispatch-table success and error counts.
 *
 * <p>Property names match the aliases (`scdul_pymn_no`, `succ_cnt`,
 * `err_cnt`) emitted by the native query — Spring Data binds projection
 * getters to result-set columns case-insensitively, with `_` collapsed
 * to camelCase.
 */
public interface DispatchCountRow {
    String getScdulPymnNo();
    Long   getSuccCnt();
    Long   getErrCnt();
}
