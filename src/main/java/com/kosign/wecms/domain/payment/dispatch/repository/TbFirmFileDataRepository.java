package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileDataId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link TbFirmFileData} (firm-banking dispatch file detail
 * lines). Mirrors legacy {@code IDO.TB_FIRM_FILE_DATA_*} — BATCH1 inserted one
 * row per beneficiary line ({@code C001}); reads are by file identity.
 *
 * <p>PK is {@code (TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)} — note DEV omits
 * {@code BIZ_KEY} from this PK (see {@link TbFirmFileData}).
 */
public interface TbFirmFileDataRepository
        extends JpaRepository<TbFirmFileData, TbFirmFileDataId> {

    /** Detail lines of one dispatched file, ordered by line sequence. */
    List<TbFirmFileData> findByTranDtAndFileSeqNoAndFirmFileNmOrderBySeqAsc(
            String tranDt, String fileSeqNo, String firmFileNm);
}
