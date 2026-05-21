package com.kosign.wecms.domain.banking.transferhistory.repository;

import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only repository for the Transfer History list endpoint.
 *
 * <p>Distinct from the older {@code com.kosign.wecms.domain.banking.
 * repository.NapasRemitTrnsHstrRepository} which carries hand-rolled
 * derived methods for the Sprint 1 interim endpoint at
 * {@code /api/v1/banking/transfers/history}. This repository surfaces
 * {@link JpaSpecificationExecutor} so the Transfer History service can
 * build {@code WHERE BIZ_KEY = ? AND substr(REG_DTM,1,8) BETWEEN ? AND
 * ? AND TRNS_STTS …} dynamically while keeping the sort + paging
 * contract.
 *
 * <p>The interim repository stays in place until the corresponding
 * frontend cutover MR flips the {@code /transfers/history} consumer to
 * the new endpoint.
 */
public interface TransferHistoryRepository
        extends JpaRepository<NapasRemitTrnsHstr, NapasRemitTrnsHstrId>,
                JpaSpecificationExecutor<NapasRemitTrnsHstr> {

    /**
     * Single-roundtrip totals for the date+status filter — mirrors
     * the legacy {@code IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml:24-34}
     * projection. Returns the same shape so the service can populate
     * the response envelope's footer fields without a second
     * Specification-based aggregation.
     *
     * <p>{@code statusEq} is the literal status code to filter on or
     * {@code null} for no status filter; {@code statusNeq} is the
     * literal code to exclude or {@code null} for no negation. The
     * legacy convention is mutually-exclusive — pass one or the other,
     * never both.
     */
    @Query("""
            SELECT new com.kosign.wecms.domain.banking.transferhistory.repository.TransferHistoryTotals(
                COUNT(t),
                COALESCE(SUM(CASE WHEN t.ccyCd = 'VND' THEN t.trscAmt ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN t.ccyCd = 'USD' THEN t.trscAmt ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN t.ccyCd = 'VND' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN t.ccyCd = 'USD' THEN 1 ELSE 0 END), 0))
            FROM NapasRemitTrnsHstr t
            WHERE t.bizKey = :bizKey
              AND SUBSTRING(t.regDtm, 1, 8) BETWEEN :fromDt AND :toDt
              AND (:statusEq  IS NULL OR t.trnsStts = :statusEq)
              AND (:statusNeq IS NULL OR t.trnsStts IS NULL OR t.trnsStts <> :statusNeq)
            """)
    TransferHistoryTotals aggregateTotals(
            @Param("bizKey")    String bizKey,
            @Param("fromDt")    String fromDt,
            @Param("toDt")      String toDt,
            @Param("statusEq")  String statusEq,
            @Param("statusNeq") String statusNeq);

    @Override
    Page<NapasRemitTrnsHstr> findAll(
            org.springframework.data.jpa.domain.Specification<NapasRemitTrnsHstr> spec,
            Pageable pageable);
}
