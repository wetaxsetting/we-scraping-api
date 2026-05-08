package com.kosign.wecms.domain.setting.firmbankinghistory.repository;

import com.kosign.wecms.domain.setting.firmbankinghistory.entity.FirmBankingHistoryView;
import com.kosign.wecms.domain.setting.firmbankinghistory.entity.FirmBankingHistoryViewId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only repository over {@code V_FIRM_BANKING_HISTORY}. One JPQL
 * search method covers every supported filter combination — date range
 * is required, the rest are optional and guarded with {@code IS NULL OR}
 * predicates so the same query plan handles "no filter" / "single
 * filter" / "every filter" without method explosion.
 *
 * <p>Sort and pagination are driven by the {@link Pageable} parameter;
 * the service layer assembles the {@link org.springframework.data.domain.Sort}
 * via the
 * {@link com.kosign.wecms.domain.setting.firmbankinghistory.service.FirmBankingHistorySortField}
 * whitelist, so unknown / blank wire {@code sort_field} values fall
 * through to the default {@code TARN_DTM DESC} order.
 *
 * <p>The keyword pattern is built service-side (lowercased + LIKE
 * meta-chars escaped + wrapped with {@code %…%}) following the same
 * convention as
 * {@link com.kosign.wecms.domain.banking.service.AccountService#buildKeywordPattern},
 * with {@code !} as the escape character so the JPQL pattern stays
 * portable.
 */
public interface FirmBankingHistoryRepository
        extends JpaRepository<FirmBankingHistoryView, FirmBankingHistoryViewId> {

    @Query("""
           SELECT v FROM FirmBankingHistoryView v
           WHERE v.bizKey = :bizKey
             AND SUBSTRING(v.tarnDtm, 1, 8) BETWEEN :fromDate AND :toDate
             AND (:svcType IS NULL OR v.svcType = :svcType)
             AND (:keywordPattern IS NULL OR LOWER(v.seqNo) LIKE :keywordPattern ESCAPE '!')
           """)
    Page<FirmBankingHistoryView> search(
            @Param("bizKey")         String bizKey,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("svcType")        String svcType,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);
}
