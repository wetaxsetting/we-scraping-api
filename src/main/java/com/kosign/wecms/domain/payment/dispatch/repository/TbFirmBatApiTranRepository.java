package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTranId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link TbFirmBatApiTran} (batch-level EFS API audit ledger).
 * Legacy {@code IDO.TB_FIRM_BAT_API_TRAN_C001/U001} inserted one row per API
 * leg (00301/00302/01301/01302) and updated it with the response.
 *
 * <p>PK is the six-column composite
 * {@code (TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)}.
 *
 * <p><b>TODO(K9):</b> this ledger + the {@code PROC_STEP} state guard are the
 * intended idempotency key for the 01301→00302 trigger (a duplicate callback
 * must not issue 00302 twice). The dedupe finder below supports that check, but
 * the idempotent-trigger mechanism itself is part of the open K9 decision and
 * is not wired here.
 */
public interface TbFirmBatApiTranRepository
        extends JpaRepository<TbFirmBatApiTran, TbFirmBatApiTranId> {

    /** Audit rows for one file (any API leg) — supports the K9 dedupe guard. */
    List<TbFirmBatApiTran> findByTranDtAndBizKeyAndFirmFileNm(String tranDt, String bizKey, String firmFileNm);

    /** Whether a given API leg has already been logged for a file — idempotency probe. */
    boolean existsByTranDtAndBizKeyAndFirmFileNmAndApiTranTp(
            String tranDt, String bizKey, String firmFileNm, String apiTranTp);
}
