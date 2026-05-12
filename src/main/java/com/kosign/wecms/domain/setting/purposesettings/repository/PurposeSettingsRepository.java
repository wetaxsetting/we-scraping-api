package com.kosign.wecms.domain.setting.purposesettings.repository;

import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSet;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Owning-table repository for {@code BUSI_USAG_SET_INFM}. Query methods
 * are kept tight to what the {@code PurposeSettingsService} actually
 * calls — no speculative finders, no generic search helpers.
 *
 * <p>Refactor scope: only rows with {@code usag_grp_cd IS NULL AND
 * use_yn = 'Y'}. Every query below pins both predicates so the legacy
 * group-bucket rows can never leak into the refactor's read paths.
 *
 * <p>Tenant scoping: {@code biz_key} is part of every query
 * predicate, sourced from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}
 * by the service.
 */
public interface PurposeSettingsRepository
        extends JpaRepository<BusiUsagSet, BusiUsagSetId> {

    /** Full active refactor-scope list for the tenant, ordered for
     *  the UI table. Excludes the 9 reserved system codes from
     *  {@code comm_0187_001_r001_act.jsp:58-67}; the service supplies
     *  them via {@code RESERVED_SYSTEM_CODES}. Optional keyword
     *  matches against {@code use_usag_nm} (case-insensitive,
     *  substring) — merges legacy {@code _r002} into the list path.
     *
     *  <p><b>Keyword binding:</b> caller passes <b>empty string</b>
     *  (NOT null) when no keyword filter is active. Reason: PostgreSQL
     *  infers {@code bytea} for an untyped null bound inside
     *  {@code LOWER(CONCAT('%', :keyword, '%'))}, which then explodes
     *  with {@code function lower(bytea) does not exist}. Binding a
     *  typed empty string keeps the parameter inference firmly on
     *  {@code varchar} and the {@code :keyword = ''} short-circuit
     *  skips the LIKE arm entirely. Service-layer {@code list(...)}
     *  enforces the empty-string contract before calling this method. */
    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
          AND p.useUsagCd NOT IN :reservedCodes
          AND (:keyword = ''
               OR LOWER(p.useUsagNm) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.dispOrd ASC NULLS LAST, p.useUsagCd ASC
        """)
    List<BusiUsagSet> findActiveForTenant(
            @Param("bizKey")        String       bizKey,
            @Param("reservedCodes") Set<String>  reservedCodes,
            @Param("keyword")       String       keyword);

    /** Lookup by composite PK. The {@code (biz_key, use_usag_cd)}
     *  shape guarantees tenant safety — a cross-tenant lookup returns
     *  empty. Used by Detail / Update / Delete. */
    Optional<BusiUsagSet> findByBizKeyAndUseUsagCd(String bizKey, String useUsagCd);

    /** Duplicate-{@code hand_use_usag_cd} guard for Create. Mirrors
     *  legacy {@code findActivePurposeByHandCd} at
     *  {@code comm_0187_001_c001_act.jsp:36-53}. Legacy parity:
     *  Update path does NOT call this guard (per OQ-4). */
    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
          AND p.handUseUsagCd = :handCd
        """)
    Optional<BusiUsagSet> findActiveByHandCd(
            @Param("bizKey") String bizKey,
            @Param("handCd") String handCd);

    /** Duplicate-{@code use_usag_nm} guard for Create. Mirrors legacy
     *  {@code findActivePurposeByName} at
     *  {@code comm_0187_001_c001_act.jsp:55-72}. */
    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
          AND p.useUsagNm = :name
        """)
    Optional<BusiUsagSet> findActiveByName(
            @Param("bizKey") String bizKey,
            @Param("name")   String name);

    /** Duplicate-name guard for Update — excludes the row being
     *  updated. Mirrors legacy {@code findDuplicateNameForUpdate} at
     *  {@code comm_0187_001_u002_act.jsp:54-72}. */
    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
          AND p.useUsagNm = :name
          AND p.useUsagCd <> :selfUseUsagCd
        """)
    Optional<BusiUsagSet> findDuplicateNameExcludingSelf(
            @Param("bizKey")        String bizKey,
            @Param("name")          String name,
            @Param("selfUseUsagCd") String selfUseUsagCd);

    /** Legacy delete pre-check: "row has active children". Mirrors
     *  {@code hasActiveChildren} at
     *  {@code comm_0187_001_u002_act.jsp:75-91}. */
    @Query("""
        SELECT (count(p) > 0) FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.parentUseUsagCd = :parentUseUsagCd
          AND p.useYn = 'Y'
        """)
    boolean hasActiveChildren(
            @Param("bizKey")          String bizKey,
            @Param("parentUseUsagCd") String parentUseUsagCd);

    /** Returns every active child's {@code use_usag_cd} — fed into the
     *  recursive transaction-reference check on Delete (OQ-5). */
    @Query("""
        SELECT p.useUsagCd FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.parentUseUsagCd = :parentUseUsagCd
          AND p.useYn = 'Y'
        """)
    List<String> findActiveChildCodes(
            @Param("bizKey")          String bizKey,
            @Param("parentUseUsagCd") String parentUseUsagCd);

    /** {@code MAX(disp_ord)} within the active refactor scope. Service
     *  adds {@code +1} for the new row's disp_ord. OQ-3 (p2) — refactor
     *  computes disp_ord in-service since the legacy DB function's
     *  matching branch only fires when {@code usag_grp_cd} is non-empty. */
    @Query("""
        SELECT MAX(p.dispOrd) FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
        """)
    Optional<Integer> findMaxDispOrd(@Param("bizKey") String bizKey);

    /** Bulk-load by PK set — used by Reorder to fetch every row the
     *  payload touches in one query before applying disp_ord updates. */
    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.useYn = 'Y'
          AND p.usagGrpCd IS NULL
          AND p.useUsagCd IN :useUsagCds
        """)
    List<BusiUsagSet> findAllActiveForReorder(
            @Param("bizKey")     String       bizKey,
            @Param("useUsagCds") Set<String>  useUsagCds);

    /**
     * PK generation — delegates to the legacy {@code wb_get_next_val}
     * DB function (V82). Returns a 10-digit zero-padded string in the
     * {@code 0000000001..0999999999} range, scoped to
     * {@code (biz_key, usag_grp_cd IS NULL)}. OQ-3 decided 2026-05-12.
     *
     * <p>Concurrency: the function uses bare {@code MAX+1} (no advisory
     * lock). Service wraps this call + the immediate {@code INSERT} in
     * one {@code @Transactional} block; the PK constraint catches a
     * concurrent-create collision and the service retries once.
     */
    @Query(value = "SELECT wb_get_next_val("
                 + "'busi_usag_set_infm', 'use_usag_cd', :bizKey, '')",
           nativeQuery = true)
    String nextUseUsagCd(@Param("bizKey") String bizKey);
}
