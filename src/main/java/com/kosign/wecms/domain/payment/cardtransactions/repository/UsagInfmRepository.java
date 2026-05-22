package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.UsagInfm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Purpose dictionary lookups for the Purpose picker. JOINs to
 * {@code USAG_INFM_LANG_DETL} so the wire ships the localized name
 * matching the caller's {@code Accept-Language}.
 */
public interface UsagInfmRepository extends JpaRepository<UsagInfm, String> {

    /**
     * List active purposes for one {@code USAG_GRP_CD}, joined to the
     * language-detail table so the wire ships the localized name in
     * one round-trip. Returns code + display name pairs.
     *
     * @param grpCd  e.g. {@code "6"} for the New Receipt popup
     * @param lang   two-letter code ({@code EN} / {@code KO} / {@code KM}
     *               / {@code VI}); falls back to {@code EN} when no
     *               row matches the active locale
     */
    @Query(value = """
            SELECT u.USE_USAG_CD                AS use_usag_cd,
                   COALESCE(d.USAG_NM, e.USAG_NM, u.USE_USAG_CD) AS usag_nm
              FROM USAG_INFM u
              LEFT JOIN USAG_INFM_LANG_DETL d
                ON d.USE_USAG_CD = u.USE_USAG_CD AND d.LANG_CODE = :lang
              LEFT JOIN USAG_INFM_LANG_DETL e
                ON e.USE_USAG_CD = u.USE_USAG_CD AND e.LANG_CODE = 'EN'
             WHERE COALESCE(u.DEL_YN, 'N') = 'N'
               AND u.USAG_GRP_CD = :grpCd
             ORDER BY u.USE_USAG_CD
            """, nativeQuery = true)
    List<PurposeProjection> findActiveByGrpCd(
            @Param("grpCd") String grpCd,
            @Param("lang")  String lang);

    interface PurposeProjection {
        String getUseUsagCd();
        String getUsagNm();
    }
}
