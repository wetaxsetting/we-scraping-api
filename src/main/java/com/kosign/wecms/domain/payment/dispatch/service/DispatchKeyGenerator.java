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

    /**
     * Allocate the next {@code TRAN_SEQ} for the API path
     * ({@link com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget}).
     * Returned as a zero-padded VARCHAR — column is {@code VARCHAR(10)}
     * on DEV so we widen up to 10 digits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextTranSeq(String bizKey, String tranDt) {
        long next = tbPayTargetRepository.maxTranSeq(bizKey, tranDt) + 1L;
        return String.valueOf(next);
    }

    /**
     * Allocate the next {@code FILE_SEQ_NO} for the File path header
     * ({@link com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst}).
     * Column is {@code VARCHAR(7)} on DEV — narrower than TRAN_SEQ.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextFileSeqNo(String bizKey, String tranDt) {
        long next = tbPayFileMstRepository.maxFileSeqNo(bizKey, tranDt) + 1L;
        return String.valueOf(next);
    }
}
