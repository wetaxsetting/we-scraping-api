package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-tenant per-date sequence allocator for the dispatch tables.
 *
 * <p>Mirrors the entity auto-gen pattern in legacy {@code PymnHelper.java}:
 * each insert into {@code TB_PAY_TARGET} / {@code TB_PAY_FILE_MST} pulls
 * {@code MAX+1} scoped by {@code (BIZ_KEY, TRAN_DT)}. {@code TB_PAY_FILE_DATA}
 * sequences within the parent MST row.
 *
 * <p>Concurrency note: two parallel dispatches against the same tenant +
 * date could pick the same sequence. The composite PK then trips a
 * {@code DataIntegrityViolationException} on insert, which the calling
 * service catches and surfaces as a retryable error. Sprint 2+ could
 * tighten this with a row-level lock on a counter table; today's single-
 * tenant R&D traffic doesn't hit the race in practice.
 */
@Service
@RequiredArgsConstructor
public class DispatchKeyGenerator {

    private final TbPayTargetRepository  tbPayTargetRepository;
    private final TbPayFileMstRepository tbPayFileMstRepository;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    /**
     * Allocate the next {@code TRAN_SEQ} for the API path
     * ({@link com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget}).
     * Returned as a zero-padded VARCHAR — column is {@code VARCHAR(10)}
     * on DEV so we widen up to 10 digits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextTranSeq(String bizKey, String tranDt) {
        // TRAN_SEQ via the single wb_get_next_val funnel (V140 #2 — JEX backs
        // this with the GLOBAL firm_seq_no sequence, not a per-tenant/date MAX+1,
        // so the value can never collide with the live JEX / WABOOKS firm-banking
        // dispatch that draws from the same sequence.
        return legacyFunctions.nextTbPayTargetTranSeq();
    }

    /**
     * Allocate the next {@code FILE_SEQ_NO} for the File path header
     * ({@link com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst}).
     * Column is {@code VARCHAR(7)} on DEV — narrower than TRAN_SEQ.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextFileSeqNo(String bizKey, String tranDt) {
        // FILE_SEQ_NO via the single wb_get_next_val funnel (V140 #4 — JEX backs
        // this with the GLOBAL firm_file_seq_no sequence, not a per-tenant/date
        // MAX+1, matching the live JEX firm-banking file dispatch.
        return legacyFunctions.nextTbPayFileMstFileSeqNo();
    }
}
