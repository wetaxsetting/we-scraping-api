package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTranId;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;

/**
 * C-4 · Everything {@link BulkExecutionResultTxService#applyResult} needs to write one 01302
 * execution result — <b>parsed data and identifiers only, never a live entity</b>.
 *
 * <h2>Why no entities cross this boundary</h2>
 * The orchestrator ({@link BulkExecutionResultService#handle}) now runs without a transaction, so any
 * entity it loaded would arrive here <b>detached</b>. A {@code save(...)} on a detached instance is a
 * merge that writes every mapped column, which would blind-overwrite whatever a concurrent 01302, a
 * live JEX batch or an operator changed in between. Passing the composite keys instead forces the
 * writer to re-read inside its own transaction and act on committed data.
 *
 * @param fileId        {@code TB_FIRM_FILE_MST} PK — re-read inside the writer, never carried as an entity
 * @param auditId       {@code TB_FIRM_BAT_API_TRAN} PK of the 01302 audit row already committed by
 *                      {@link DispatchApiTranAuditWriter} in its own {@code REQUIRES_NEW} transaction
 * @param resultFileNm  the bank's {@code R_…} result-file name from the callback — what
 *                      {@code TB_FIRM_FILE_RSLT.FIRM_FILE_NM} stores (NOT the sent {@code S_…} name
 *                      held on the master row)
 * @param tranDt        the result-filename date, key for the {@code TB_FIRM_FILE_RSLT} rows and for the
 *                      firm/pay-data indexes (legacy {@code API2_act.jsp} uses {@code file_sel_cond[4]}
 *                      in exactly these places)
 * @param parsed        the decoded result file — the whole reason the download and parse could move out
 *                      of the transaction
 * @param dttm14        one {@code yyyyMMddHHmmss} stamp for every {@code PROC_DTTM} / {@code RS_DTTM}
 *                      this apply writes, taken once in the orchestrator so all of them agree
 */
public record BulkExecutionResultCommand(
        TbFirmFileMstId fileId,
        TbFirmBatApiTranId auditId,
        String resultFileNm,
        String tranDt,
        FixedLengthCodec.ParsedFile parsed,
        String dttm14) {
}
