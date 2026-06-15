package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OvrsPymnDtlsRepository extends JpaRepository<OvrsPymnSumrDetl, OvrsPymnSumrDetlId> {

    java.util.List<OvrsPymnSumrDetl> findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(
            String bizKey, String ovrsPymnNo);

    java.util.Optional<OvrsPymnSumrDetl> findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(
            String bizKey, String ovrsPymnNo, String ovrsPymnDetlNo);

    @Modifying
    @Query("DELETE FROM OvrsPymnSumrDetl d WHERE d.bizKey = :bizKey AND d.ovrsPymnNo = :ovrsPymnNo")
    int deleteByBizKeyAndOvrsPymnNo(@Param("bizKey") String bizKey, @Param("ovrsPymnNo") String ovrsPymnNo);

    // OVRS_PYMN_DETL_NO allocation now routes through
    // LegacyFunctionRepository.nextOvrsPymnDetlNo (V140 #30 — per-tenant MAX+1
    // over (ovrs_pymn_mstr UNION ovrs_pymn_sumr_detl), verbatim from the live
    // JEX function) so every wb_get_next_val call goes through the one funnel.
    // (The live JEX branch DOES exist for this column — the earlier "returns
    // null for this column" note was incorrect.)
}
