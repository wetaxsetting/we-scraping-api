package com.kosign.wecms.domain.setting.zalooa.repository;

import com.kosign.wecms.domain.setting.zalooa.entity.ZaloSendHis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Repository for {@link ZaloSendHis}. Every method constrains on
 * {@code BIZ_KEY}, per the tenant-isolation rule in {@code CLAUDE.md}.
 */
public interface ZaloSendHisRepository extends JpaRepository<ZaloSendHis, BigDecimal> {

    /** Send history for the signed-in tenant, newest first. */
    Page<ZaloSendHis> findByBizKeyOrderByRegDtmDesc(String bizKey, Pageable pageable);

    /**
     * Correlate an inbound delivery report back to the send that produced it.
     * Unused today — the webhook does not exist yet — but the index that backs
     * it ({@code zalo_send_his_ix02}) ships with V254 so adding the webhook
     * needs no migration.
     */
    Optional<ZaloSendHis> findByBizKeyAndTrackingId(String bizKey, String trackingId);
}
