package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMstId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TbPayFileMstRepository
        extends JpaRepository<TbPayFileMst, TbPayFileMstId> {

    // FILE_SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextTbPayFileMstFileSeqNo (V140 #4 — the GLOBAL
    // firm_file_seq_no sequence) so every wb_get_next_val call goes through the
    // one funnel and lines up with the live JEX firm-banking file dispatch.

    /** The bulk pay-file target row (00301 reception flow + 01302 final-status aggregation). */
    Optional<TbPayFileMst> findByTranDtAndBizKeyAndFileSeqNo(String tranDt, String bizKey, String fileSeqNo);
}
