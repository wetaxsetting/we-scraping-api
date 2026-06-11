package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleDtl;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleDtlId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TbBatchScheduleDtl} (per-bank, per-network operating
 * hours). Legacy {@code IDO.TB_BATCH_SCHEDULE_DTL_R001} (BATCH1
 * {@code operTimeChk()}) read the window for a specific
 * {@code (BATCH_ID, OUT_BANK_CD, ANT_DIV_CD)} channel (EBANK / VCB / NAPAS /
 * SWIFT / CITAD).
 *
 * <p>No {@code BIZ_KEY} — same global-namespace design as the parent header.
 */
public interface TbBatchScheduleDtlRepository
        extends JpaRepository<TbBatchScheduleDtl, TbBatchScheduleDtlId> {

    Optional<TbBatchScheduleDtl> findByBatchIdAndOutBankCdAndAntDivCd(
            String batchId, String outBankCd, String antDivCd);

    List<TbBatchScheduleDtl> findByBatchIdAndOutBankCd(String batchId, String outBankCd);
}
