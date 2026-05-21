package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface NapasRemitTrnsHstrRepository extends JpaRepository<NapasRemitTrnsHstr, NapasRemitTrnsHstrId> {

    /** Transfer history rows for a tenant within an inclusive date range, newest first. */
    List<NapasRemitTrnsHstr> findByBizKeyAndRegDtmBetweenOrderByRegDtmDesc(String bizKey, String fromDtm, String toDtm);

    /** Same range, but filtered to one TRNS_STTS code. */
    List<NapasRemitTrnsHstr> findByBizKeyAndTrnsSttsAndRegDtmBetweenOrderByRegDtmDesc(
            String bizKey, String trnsStts, String fromDtm, String toDtm);

    /** Same range, status NOT one specific value (used to express "non-failed only"). */
    List<NapasRemitTrnsHstr> findByBizKeyAndTrnsSttsNotAndRegDtmBetweenOrderByRegDtmDesc(
            String bizKey, String trnsStts, String fromDtm, String toDtm);

    /** No-date-range variant for when the UI does not pass from/to. */
    List<NapasRemitTrnsHstr> findByBizKeyOrderByRegDtmDesc(String bizKey);

    /** Own Account Transfer — idempotency replay lookup. V179 added
     *  {@code OAT_IDEMPOTENCY_KEY} + a partial unique index on
     *  {@code (BIZ_KEY, OAT_IDEMPOTENCY_KEY) WHERE OAT_IDEMPOTENCY_KEY IS
     *  NOT NULL}. The composer client generates a UUID per attempt; a hit
     *  here means the prior attempt already persisted and the service
     *  returns the cached row with {@code already_recorded = true}. */
    Optional<NapasRemitTrnsHstr> findByBizKeyAndOatIdempotencyKey(
            String bizKey, String oatIdempotencyKey);

    /** Own Account Transfer — allocate the next {@code SEQ_NO} for the
     *  tenant. Legacy schema has no DB sequence; refactor computes
     *  {@code MAX(SEQ_NO) + 1} per-tenant inside the caller's transaction.
     *  The PK {@code (BIZ_KEY, SEQ_NO)} catches concurrent allocators;
     *  the service retries once on
     *  {@link org.springframework.dao.DataIntegrityViolationException}. */
    @Query("""
            SELECT COALESCE(MAX(n.seqNo), 0) + 1
              FROM NapasRemitTrnsHstr n
             WHERE n.bizKey = :bizKey
            """)
    BigDecimal allocateNextSeqNo(@Param("bizKey") String bizKey);
}
