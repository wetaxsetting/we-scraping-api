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

    /**
     * Active rules filtered by {@code (bankCd, pymnType)} — powers the
     * {@code GET /api/v1/payment/blocked-chars} endpoint that drives the
     * header-tooltip on the Excel upload preview + Scheduled / Result
     * detail tables. Returns at most 5 rows, one per {@code FieldCd}
     * ({@code RMKS / RCPT / MEMO / BENA / BEAD}).
     */
    @Query("""
            SELECT r FROM BlockedCharsRule r
             WHERE r.status = 'Y'
               AND r.bankCd   = :bankCd
               AND r.pymnType = :pymnType
               AND (r.effFrDt IS NULL OR r.effFrDt <= :asOfDate)
               AND (r.effToDt IS NULL OR r.effToDt >= :asOfDate)
            """)
    List<BlockedCharsRule> findActiveByBankAndPymnType(
            @Param("bankCd")   String bankCd,
            @Param("pymnType") String pymnType,
            @Param("asOfDate") String asOfDate);
}
