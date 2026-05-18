package com.kosign.wecms.domain.banking.smartorganizer.repository;

import com.kosign.wecms.domain.banking.smartorganizer.entity.RmrkOrgInfm;
import com.kosign.wecms.domain.banking.smartorganizer.entity.RmrkOrgInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Smart Organizer rule repository. Tenant scoping is always part of
 * the query — never call vanilla {@link JpaRepository#findAll()} or
 * {@link JpaRepository#deleteAll()} on this entity.
 */
public interface RmrkOrgInfmRepository extends JpaRepository<RmrkOrgInfm, RmrkOrgInfmId> {

    /** Organizer Settings list — ordered by DISP_ORD ASC then RMRK_SEQ_NO ASC for stable tiebreak. */
    List<RmrkOrgInfm> findByBizKeyOrderByDispOrdAscRmrkSeqNoAsc(String bizKey);

    /** Lookup by tenant-scoped composite id (saver / updater path). */
    Optional<RmrkOrgInfm> findByBizKeyAndRmrkSeqNo(String bizKey, Long rmrkSeqNo);

    /**
     * Reorder helper — patches DISP_ORD for one rule. Used by the
     * Organizer Settings save flow when the user drags rules into a
     * new order. Tenant scoped.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RmrkOrgInfm r SET r.dispOrd = :dispOrd " +
            "WHERE r.bizKey = :bizKey AND r.rmrkSeqNo = :rmrkSeqNo")
    int updateDispOrd(@Param("bizKey") String bizKey,
                      @Param("rmrkSeqNo") Long rmrkSeqNo,
                      @Param("dispOrd") Integer dispOrd);

    /** Tenant-scoped delete — used by the Organizer Settings delete row flow. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RmrkOrgInfm r WHERE r.bizKey = :bizKey AND r.rmrkSeqNo = :rmrkSeqNo")
    int deleteByBizKeyAndRmrkSeqNo(@Param("bizKey") String bizKey,
                                   @Param("rmrkSeqNo") Long rmrkSeqNo);
}
