package com.kosign.wecms.domain.payment.validation.repository;

import com.kosign.wecms.domain.payment.validation.entity.BlockedCharsRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlockedCharsRuleRepository
        extends JpaRepository<BlockedCharsRule, String> {

    /**
     * Every {@code STATUS = 'Y'} rule whose {@code (EFF_FR_DT,
     * EFF_TO_DT)} window contains {@code asOfDate} ({@code YYYYMMDD}).
     * The validator filters by {@code (bankCd, pymnType, fieldCd)} in
     * memory because we cache the full active set with a TTL and
     * service ~hundreds of validate calls per Excel parse from one
     * snapshot.
     */
    @Query("""
            SELECT r FROM BlockedCharsRule r
             WHERE r.status = 'Y'
               AND (r.effFrDt IS NULL OR r.effFrDt <= :asOfDate)
               AND (r.effToDt IS NULL OR r.effToDt >= :asOfDate)
            """)
    List<BlockedCharsRule> findActiveRules(@Param("asOfDate") String asOfDate);
}
