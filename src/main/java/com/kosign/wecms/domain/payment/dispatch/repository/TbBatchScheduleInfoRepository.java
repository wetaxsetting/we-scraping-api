package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleInfo;
import com.kosign.wecms.domain.payment.dispatch.entity.TbBatchScheduleInfoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link TbBatchScheduleInfo} (per-bank operating-hours header).
 * Legacy {@code IDO.TB_BATCH_SCHEDULE_INFO_R001} read the
 * {@code (BATCH_ID, OUT_BANK_CD)} window and derived an {@code EXE_DIV}
 * (Y open / N not-yet / E ended) by comparing {@code BATCH_START_TM} /
 * {@code BATCH_END_TM} against the current time.
 *
 * <p>The {@code EXE_DIV} derivation is operating-hours logic that belongs in an
 * {@code OperatingHoursValidator} (not a repository); this finder only loads the
 * row. No {@code BIZ_KEY} — schedules are global per {@code (BATCH_ID, OUT_BANK_CD)}.
 */
public interface TbBatchScheduleInfoRepository
        extends JpaRepository<TbBatchScheduleInfo, TbBatchScheduleInfoId> {

    Optional<TbBatchScheduleInfo> findByBatchIdAndOutBankCd(String batchId, String outBankCd);
}
