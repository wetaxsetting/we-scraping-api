package com.kosign.wecms.domain.setting.vasaccount.repository;

import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfmId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@code BUSI_VAS_ACCT_INFM}. One paginated
 * search query covers the list endpoint; derived methods cover detail +
 * duplicate-check + max-seq-allocation.
 *
 * <p>Every method binds {@code biz_key} from {@link
 * com.kosign.wecms.common.tenant.TenantContext#currentBizKey()} at the
 * service layer — never trust a wire-supplied tenant.
 *
 * <p>Soft-delete contract: live rows have {@code DEL_YN='N'}; soft-deleted
 * rows stay in the table with {@code DEL_YN='Y'}. The service writes the
 * flag explicitly and all read paths constrain on it.
 */
public interface BusiVasAcctInfmRepository extends JpaRepository<BusiVasAcctInfm, BusiVasAcctInfmId> {

    /**
     * Live-row detail lookup by {@code (biz_key, vas_acct_seq_no)} with
     * the {@code del_yn='N'} predicate baked in — refuses to return
     * soft-deleted rows so the service can throw {@code WBE2014} for
     * both "not found" and "soft-deleted" without leaking the difference.
     */
    Optional<BusiVasAcctInfm> findByBizKeyAndVasAcctSeqNoAndDelYn(
            String bizKey, Long vasAcctSeqNo, String delYn);

    /**
     * Duplicate-code check used at create + update-with-renamed-code.
     * Scoped to live rows only — a previously deleted code can be
     * re-used (mirrors legacy {@code comm_0217_003_c001_act.jsp:46-58}).
     */
    boolean existsByBizKeyAndVasAcctCdAndDelYn(
            String bizKey, String vasAcctCd, String delYn);

    /**
     * Lookup by user-visible code + soft-delete flag. Used by the
     * Transaction History VAS picker to validate the picker's choice
     * (active + tenant-scoped) before writing
     * {@code ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_CD}.
     */
    Optional<BusiVasAcctInfm> findByBizKeyAndVasAcctCdAndDelYn(
            String bizKey, String vasAcctCd, String delYn);

    /**
     * Paginated, filtered list. Uses the {@code (:p IS NULL OR ...)}
     * guard pattern so optional filters are skipped without method-
     * explosion. {@code keywordPattern} is built service-side (lower-
     * cased + LIKE meta-chars escaped + wrapped with {@code %…%}) with
     * {@code !} as the escape character — same convention as the ERP
     * Transaction History list query.
     *
     * <p>{@code statusFilter} is a single status code or {@code null}
     * for no filter. When the caller wants both Active + Inactive
     * (the legacy "both checkboxes ticked" path), they pass {@code null};
     * the JEX sentinel {@code STTS=N} is NOT preserved.
     */
    @Query("""
           SELECT v FROM BusiVasAcctInfm v
           WHERE v.bizKey = :bizKey
             AND v.delYn = 'N'
             AND (:statusFilter IS NULL OR v.stts = :statusFilter)
             AND (:keywordPattern IS NULL
                  OR LOWER(v.vasAcctCd) LIKE :keywordPattern ESCAPE '!'
                  OR LOWER(v.vasAcctNm) LIKE :keywordPattern ESCAPE '!')
           """)
    Page<BusiVasAcctInfm> search(
            @Param("bizKey")         String bizKey,
            @Param("statusFilter")   String statusFilter,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable);

    /**
     * Per-tenant maximum {@code vas_acct_seq_no}. Used by
     * {@code VasAcctSeqNoGenerator.next(bizKey)} as the base for
     * {@code MAX + 1}. Returns {@code null} when the tenant has no rows
     * — caller defaults to {@code 1}.
     */
    @Query("""
           SELECT MAX(v.vasAcctSeqNo) FROM BusiVasAcctInfm v
           WHERE v.bizKey = :bizKey
           """)
    Long findMaxSeqNoByBizKey(@Param("bizKey") String bizKey);

    /**
     * All live {@code vas_acct_cd} values for the tenant. Consumed by
     * {@code VasAccountExcelService.preview / import_} to detect
     * duplicates without fetching whole rows. Cheap — the
     * {@code (biz_key, vas_acct_cd, del_yn)} index from V77 covers it.
     */
    @Query("""
           SELECT v.vasAcctCd FROM BusiVasAcctInfm v
           WHERE v.bizKey = :bizKey AND v.delYn = 'N'
           """)
    java.util.List<String> findLiveCodesByBizKey(@Param("bizKey") String bizKey);

    /**
     * Soft-delete every live row for the tenant in a single UPDATE.
     * Used by the Replace import path so it doesn't round-trip 500
     * times for a 500-row payload. Returns the rowcount.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("""
           UPDATE BusiVasAcctInfm v
              SET v.delYn = 'Y', v.modId = :modId, v.modDtm = :modDtm
            WHERE v.bizKey = :bizKey AND v.delYn = 'N'
           """)
    int softDeleteAllLiveForTenant(
            @Param("bizKey") String bizKey,
            @Param("modId")  String modId,
            @Param("modDtm") String modDtm);

    /**
     * Active + live VAS accounts for the typeahead picker on Edit Bank
     * Account. Mirrors legacy {@code comm_1002_001} — returns only
     * {@code STTS='A'} + {@code DEL_YN='N'} rows; inactive / deleted
     * entries are never offered as a new mapping target.
     *
     * <p>Optional substring filter ({@code keywordPattern}) matches
     * either {@code vas_acct_cd} or {@code vas_acct_nm} (case
     * insensitive). Same {@code (:p IS NULL OR …) ESCAPE '!'} pattern
     * as {@link #search} for consistency. Ordered by code so the picker
     * renders deterministically.
     */
    @Query("""
           SELECT v FROM BusiVasAcctInfm v
           WHERE v.bizKey = :bizKey
             AND v.delYn  = 'N'
             AND v.stts   = 'A'
             AND (:keywordPattern IS NULL
                  OR LOWER(v.vasAcctCd) LIKE :keywordPattern ESCAPE '!'
                  OR LOWER(v.vasAcctNm) LIKE :keywordPattern ESCAPE '!')
           ORDER BY v.vasAcctCd ASC
           """)
    java.util.List<BusiVasAcctInfm> findLookup(
            @Param("bizKey")         String bizKey,
            @Param("keywordPattern") String keywordPattern);
}
