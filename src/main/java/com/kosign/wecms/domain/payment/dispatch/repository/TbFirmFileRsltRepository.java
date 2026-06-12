package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileRslt;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileRsltId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link TbFirmFileRslt} (per-line gateway result rows parsed
 * from the bank result file by the 01302 / API2 path,
 * {@code IDO.TB_FIRM_FILE_RSLT_C001}).
 *
 * <p>PK is {@code (TRAN_DT, BIZ_KEY, FIRM_FILE_NM, SEQ)}.
 *
 * <p><b>TODO(K3):</b> result rows are populated by parsing the fixed-length
 * bank result file; the byte layout that feeds these columns is gated on the
 * K3 file-format-version confirmation (see {@code FixedLengthCodec}).
 */
public interface TbFirmFileRsltRepository
        extends JpaRepository<TbFirmFileRslt, TbFirmFileRsltId> {

    /** Result lines for one file (post-execution aggregation). */
    List<TbFirmFileRslt> findByTranDtAndBizKeyAndFirmFileNmOrderBySeqAsc(
            String tranDt, String bizKey, String firmFileNm);
}
