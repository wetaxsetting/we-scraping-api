package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a {@code TB_FIRM_BAT_API_TRAN} callback-audit row in its <b>own</b>
 * {@code REQUIRES_NEW} transaction (G1 / audit M-7) so it survives a rollback of the
 * enclosing callback handler.
 *
 * <p>Legacy {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} commits the 01302 audit row
 * <i>before</i> the result-file download/parse transaction, so a malformed/missing
 * result file (which rolls the parse work back) still leaves the audit trail intact.
 * The refactor's {@link BulkExecutionResultService#handle} runs the parse in one
 * transaction; routing the audit insert through this {@code REQUIRES_NEW} writer
 * restores the legacy "audit outlives the parse failure" guarantee without splitting
 * the rest of the handler into multiple transactions.
 *
 * <p>The subsequent success update to the same row (RS_CD/RS_MSG/RS_DTTM) stays in the
 * caller's transaction — it is a merge of the now-detached entity and only runs on the
 * happy path, exactly like legacy (the result columns are set after the parse succeeds).
 */
@Component
@RequiredArgsConstructor
public class DispatchApiTranAuditWriter {

    private final TbFirmBatApiTranRepository firmBatApiTranRepository;

    /**
     * Persist {@code row} in a fresh transaction that commits immediately, independent of
     * the caller's transaction outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TbFirmBatApiTran saveInNewTransaction(TbFirmBatApiTran row) {
        return firmBatApiTranRepository.save(row);
    }
}
