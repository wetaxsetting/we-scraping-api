package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link OvrsPymnSumr}.
 * Used by the overseas-payment Excel upload submit flow to persist
 * one summary row per uploaded batch.
 */
public interface OvrsPymnSumrRepository extends JpaRepository<OvrsPymnSumr, OvrsPymnSumrId> {

    /**
     * Guards against accidental double-submit: returns true when the same
     * tenant already has a batch with the same original filename and the
     * same REG_DTM date prefix (first 8 chars = yyyyMMdd).
     */
    boolean existsByBizKeyAndOrgFileNmAndRegDtmStartingWith(
            String bizKey, String orgFileNm, String regDtmDatePrefix);

    Optional<OvrsPymnSumr> findByBizKeyAndOvrsPymnNo(String bizKey, String ovrsPymnNo);

    /** Per-tenant MAX(apprSeqNo::bigint) for the request-approval sequence. */
    @Query(value = "SELECT MAX(CAST(appr_seq_no AS BIGINT)) FROM ovrs_pymn_sumr " +
                   "WHERE biz_key = :bizKey AND appr_seq_no IS NOT NULL AND appr_seq_no ~ '^[0-9]+$'",
           nativeQuery = true)
    Long findMaxApprSeqNoByBizKey(@Param("bizKey") String bizKey);

    /** Batch-fetch (apprSeqNo → ccyCd) pairs for the Approval Box list.
     *  Returns only rows whose apprSeqNo is in the given set so the
     *  caller can build a lookup map without an N+1 query. */
    @Query("SELECT ops.apprSeqNo, ops.ccyCd FROM OvrsPymnSumr ops " +
           "WHERE ops.bizKey = :bizKey AND ops.apprSeqNo IN :apprSeqNos")
    List<Object[]> findCcyCdByBizKeyAndApprSeqNos(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);
}
