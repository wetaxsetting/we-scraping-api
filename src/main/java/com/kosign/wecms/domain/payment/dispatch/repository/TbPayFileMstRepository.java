package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMstId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TbPayFileMstRepository
        extends JpaRepository<TbPayFileMst, TbPayFileMstId> {

    // FILE_SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextTbPayFileMstFileSeqNo (V140 #4 — the GLOBAL
    // firm_file_seq_no sequence) so every wb_get_next_val call goes through the
    // one funnel and lines up with the live JEX firm-banking file dispatch.

    /** The bulk pay-file target row (00301 reception flow + 01302 final-status aggregation). */
    Optional<TbPayFileMst> findByTranDtAndBizKeyAndFileSeqNo(String tranDt, String bizKey, String fileSeqNo);

    /**
     * R-1 stranded-record recovery (Item 4): bulk pay-files left at {@code PROC_STAT='9'}
     * (WAIT) whose {@code PROC_DTTM} predates the grace cutoff — i.e. a request-time
     * {@code receive()} hand-off that never advanced them (lost after-commit event, flag flipped
     * on after the row existed, crash in the commit→afterCommit window). {@code PROC_DTTM} is the
     * 14-char {@code yyyyMMddHHmmss} string, so a lexicographic {@code <} is a chronological {@code <}.
     * Null {@code PROC_DTTM} rows are excluded (never eligible) — safe by construction.
     */
    List<TbPayFileMst> findByProcStatAndProcDttmLessThan(String procStat, String procDttmCutoff);
}
