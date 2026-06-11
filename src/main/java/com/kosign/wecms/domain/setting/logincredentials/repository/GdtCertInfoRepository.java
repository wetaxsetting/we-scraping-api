package com.kosign.wecms.domain.setting.logincredentials.repository;

import com.kosign.wecms.domain.setting.logincredentials.entity.GdtCertInfo;
import com.kosign.wecms.domain.setting.logincredentials.entity.GdtCertInfoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GdtCertInfoRepository extends JpaRepository<GdtCertInfo, GdtCertInfoId> {

    List<GdtCertInfo> findByBizKeyOrderBySeqNoAsc(String bizKey);

    Optional<GdtCertInfo> findByBizKeyAndSeqNo(String bizKey, BigDecimal seqNo);

    // SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextGdtCertSeqNo (V140 #26 — per-tenant MAX+1,
    // verbatim from the live JEX function) so every wb_get_next_val call goes
    // through the one funnel.
}
