package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMstId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TbPayFileMstRepository
        extends JpaRepository<TbPayFileMst, TbPayFileMstId> {

    // FILE_SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextTbPayFileMstFileSeqNo (V140 #4 — the GLOBAL
    // firm_file_seq_no sequence) so every wb_get_next_val call goes through the
    // one funnel and lines up with the live JEX firm-banking file dispatch.
}
