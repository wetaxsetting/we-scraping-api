package com.kosign.wecms.domain.setting.erptransactionhistory.repository;

import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryCounts;
import com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryProjection;
import com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstr;
import com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only repository for {@code ERP_TRNS_HSTR}. Two JPQL queries cover
 * the entire Settings &gt; ERP Transaction History list view:
 *
 * <ol>
 *   <li>{@link #search} — page slice with {@code USER_INFM.USER_NM}
 *       resolved via {@code LEFT JOIN}. Replaces the legacy
 *       {@code WB_GET_USER_NM(REG_ID)} function call from
 *       {@code IDO.ERP_TRNS_HSTR_R001.xml}.</li>
 *   <li>{@link #countAllDirections} — single aggregate row carrying the
 *       total + three direction-specific tab counts. Mirrors the legacy
 *       {@code IDO.ERP_TRNS_HSTR_R001_CNT} and the
 *       {@code getDynamicCNT} helper at
 *       {@code WECMS DEV/.../comm_0219_001_r001_act.jsp:77-108} —
 *       both intentionally drop the {@code ERP_TYPE} predicate from
 *       the filter set so all four tab chips update simultaneously
 *       for one filter combination.</li>
 * </ol>
 *
 * <p>Optional filters are passed as {@code null} when the caller wants
 * them skipped — the {@code (:p IS NULL OR ...)} guard pattern keeps
 * the query plan simple and avoids method-explosion across the eight
 * filter permutations.
 *
 * <p>Sort + pagination are driven by the {@link Pageable} parameter on
 * {@link #search}; the service layer assembles the {@link
 * org.springframework.data.domain.Sort} via the
 * {@link com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistorySortField}
 * whitelist so an unknown / typo wire {@code sort_field} value falls
 * through to the default {@code REG_DTM DESC} order.
 *
 * <p>The keyword pattern is built service-side (lowercased + LIKE
 * meta-chars escaped + wrapped with {@code %…%}) using {@code !} as
 * the escape character — same convention as
 * {@code FirmBankingHistoryService.buildKeywordPattern}.
 */
public interface ErpTrnsHstrRepository extends JpaRepository<ErpTrnsHstr, ErpTrnsHstrId> {

    /**
     * Page slice with the user-name LEFT JOIN. The HQL {@code LEFT JOIN
     * UserInfm u ON …} extension lets us join on {@code REG_ID = USER_ID}
     * without declaring a JPA relationship (the two tables share no
     * formal foreign key — legacy schema parity prevents that).
     *
     * <p>Selecting via the {@link ErpTrnsHistoryProjection} record's
     * canonical constructor keeps the wire-facing
     * {@link com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryRow}
     * decoupled from JPA — the service builds the wire row from the
     * projection.
     */
    @Query("""
           SELECT new com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryProjection(
               e.seqNo, e.orgFileNm, e.saveFileNm, e.fileSize, e.erpType, e.trnsTypeCd,
               e.regDtm, e.regId, u.userNm
           )
           FROM ErpTrnsHstr e
           LEFT JOIN UserInfm u ON u.userId = e.regId
           WHERE e.bizKey = :bizKey
             AND SUBSTRING(e.regDtm, 1, 8) BETWEEN :fromDate AND :toDate
             AND (:erpType IS NULL OR e.erpType = :erpType)
             AND (:trnsTypeCd IS NULL OR e.trnsTypeCd = :trnsTypeCd)
             AND (:keywordPattern IS NULL OR LOWER(e.regId) LIKE :keywordPattern ESCAPE '!')
           """)
    Page<ErpTrnsHistoryProjection> search(
            @Param("bizKey")         String bizKey,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("erpType")        String erpType,
            @Param("trnsTypeCd")     String trnsTypeCd,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);

    /**
     * Aggregate count — total + per-direction breakdown for the same
     * filter scope as {@link #search}, intentionally dropping the
     * {@code erpType} predicate so all four tab chips reflect the same
     * filter context. {@code COUNT(*)} on a 3-distinct-value column is
     * cheap; the index {@code IX_ERP_TRNS_HSTR_BIZ_ERP_TYPE} (V71) keeps
     * the {@code CASE WHEN} branches under one B-tree scan.
     *
     * <p>{@code SUM(CASE WHEN …)} over zero matched rows returns
     * {@code NULL} (SQL spec — a SUM over an empty set is undefined),
     * which would unbox into the record's primitive {@code long} fields
     * as an NPE. {@code COALESCE(..., 0L)} preserves the "zero counts"
     * semantic at the SQL level so the record always receives concrete
     * longs. {@code COUNT(e)} is already safe (always returns 0 for
     * empty sets per SQL spec) so it doesn't need the wrapper.
     */
    @Query("""
           SELECT new com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryCounts(
               COUNT(e),
               COALESCE(SUM(CASE WHEN e.erpType = 'W' THEN 1L ELSE 0L END), 0L),
               COALESCE(SUM(CASE WHEN e.erpType = 'E' THEN 1L ELSE 0L END), 0L),
               COALESCE(SUM(CASE WHEN e.erpType = 'D' THEN 1L ELSE 0L END), 0L)
           )
           FROM ErpTrnsHstr e
           WHERE e.bizKey = :bizKey
             AND SUBSTRING(e.regDtm, 1, 8) BETWEEN :fromDate AND :toDate
             AND (:trnsTypeCd IS NULL OR e.trnsTypeCd = :trnsTypeCd)
             AND (:keywordPattern IS NULL OR LOWER(e.regId) LIKE :keywordPattern ESCAPE '!')
           """)
    ErpTrnsHistoryCounts countAllDirections(
            @Param("bizKey")         String bizKey,
            @Param("fromDate")       String fromDate,
            @Param("toDate")         String toDate,
            @Param("trnsTypeCd")     String trnsTypeCd,
            @Param("keywordPattern") String keywordPattern);
}
