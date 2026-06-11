package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TbFirmFileMst} (firm-banking dispatch file header).
 *
 * <p>Finder methods mirror the legacy IDO reads in the bulk flow
 * ({@code WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_FIRM_FILE_MST_R*.xml}):
 * <ul>
 *   <li>{@code R001} — BATCH2 picked files at {@code (TRAN_DT, PROC_STEP, PROC_STAT)};
 *       in the TOBE this selection moves into the 01301 callback / recovery path.</li>
 *   <li>{@code R004} — the 01301 callback looked a file up by
 *       {@code (TRAN_DT, FIRM_FILE_NM, PROC_STEP, PROC_STAT)}.</li>
 * </ul>
 *
 * <p><b>TODO(K9):</b> the legacy {@code R001} selection of files stranded at
 * {@code PROC_STEP='1' / PROC_STAT='6'} was BATCH2's self-healing sweep. Whether
 * the refactor exposes a recovery query here (reconciliation sweep vs outbox vs
 * manual) is the open K9 decision — finders are declared but no scheduled
 * recovery is wired.
 */
public interface TbFirmFileMstRepository
        extends JpaRepository<TbFirmFileMst, TbFirmFileMstId> {

    /** Legacy {@code TB_FIRM_FILE_MST_R001} (BATCH2 reception-confirmed pick). */
    List<TbFirmFileMst> findByTranDtAndProcStepAndProcStat(String tranDt, String procStep, String procStat);

    /** Legacy {@code TB_FIRM_FILE_MST_R004} (01301 callback file lookup). */
    Optional<TbFirmFileMst> findByTranDtAndFirmFileNmAndProcStepAndProcStat(
            String tranDt, String firmFileNm, String procStep, String procStat);

    /** All files for a tenant/date — generic detail/list support. */
    List<TbFirmFileMst> findByTranDtAndBizKey(String tranDt, String bizKey);
}
