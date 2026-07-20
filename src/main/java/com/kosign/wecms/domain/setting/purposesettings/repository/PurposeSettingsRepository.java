package com.kosign.wecms.domain.setting.purposesettings.repository;

import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetInfm;
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
 * <p><b>Group scope (2026-07-20).</b> The refactor Purpose screen now
 * mirrors legacy {@code comm_0187_001}'s three tabs, keyed by
 * {@code USAG_GRP_CD}:
 * <ul>
 *   <li>Banking Deposit  → {@code usag_grp_cd = '4'}</li>
 *   <li>Banking Withdrawal → {@code usag_grp_cd = '6'}</li>
 *   <li>the historic refactor "level-2 hierarchy" scope → {@code usag_grp_cd IS NULL}</li>
 * </ul>
 * Every active-scope query below accepts a nullable {@code group}
 * parameter: {@code null} keeps the legacy {@code IS NULL} behaviour
 * (backward compatible with every existing caller); a non-null value
 * pins {@code usag_grp_cd = :group}. The predicate is expressed as
 * {@code ((:group IS NULL AND p.usagGrpCd IS NULL) OR p.usagGrpCd = :group)}
 * so one method covers both cases. Mirrors legacy
 * {@code comm_0187_001_r001_act.jsp:47-55}.
 *
 * <p>Tenant scoping: {@code biz_key} is part of every query
 * predicate, sourced from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}
 * by the service.
 */
public interface PurposeSettingsRepository
        extends JpaRepository<BusiUsagSetInfm, BusiUsagSetId> {

    /** Full active list for the tenant within the requested group,
     *  ordered for the UI table. Excludes the 9 reserved system codes
     *  from {@code comm_0187_001_r001_act.jsp:58-67}; the service supplies
     *  them via {@code RESERVED_SYSTEM_CODES}. Optional keyword
     *  matches against {@code use_usag_nm} (case-insensitive,
     *  substring) — merges legacy {@code _r002} into the list path.
     *
     *  <p><b>Keyword binding:</b> caller passes <b>empty string</b>
     *  (NOT null) when no keyword filter is active — see class Javadoc
     *  of the previous revision (PostgreSQL {@code lower(bytea)} pitfall).
     *
     *  <p><b>Group binding:</b> {@code null} → legacy {@code usag_grp_cd
     *  IS NULL}; {@code '4'}/{@code '6'} → that deposit / withdrawal
     *  bucket. */
    @Query("""
        SELECT p FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND ((:group IS NULL AND p.usagGrpCd IS NULL) OR p.usagGrpCd = :group)
          AND p.useYn = 'Y'
          AND p.useUsagCd NOT IN :reservedCodes
          AND (:keyword = ''
               OR LOWER(p.useUsagNm) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.dispOrd ASC NULLS LAST, p.useUsagCd ASC
        """)
    List<BusiUsagSetInfm> findActiveForTenant(
            @Param("bizKey")        String       bizKey,
            @Param("group")         String       group,
            @Param("reservedCodes") Set<String>  reservedCodes,
            @Param("keyword")       String       keyword);

    /** Deleted / unused rows for the Deleted Purpose tab
     *  ({@code use_yn = 'N'}). Mirrors legacy {@code comm_0187_003_r001}
     *  (fetchDataUnused). Restricted to the deposit/withdrawal buckets
     *  ({@code usag_grp_cd IN ('4','6')}) plus the historic NULL scope so
     *  refactor-created rows that were soft-deleted also surface. The
     *  Categories column on the tab derives from {@code usag_grp_cd}.
     *  Reserved system codes are excluded, same as the active list. */
    @Query("""
        SELECT p FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND p.useYn = 'N'
          AND (p.usagGrpCd IS NULL OR p.usagGrpCd IN ('4','6'))
          AND p.useUsagCd NOT IN :reservedCodes
        ORDER BY p.usagGrpCd ASC NULLS FIRST, p.dispOrd ASC NULLS LAST, p.useUsagCd ASC
        """)
    List<BusiUsagSetInfm> findDeletedForTenant(
            @Param("bizKey")        String       bizKey,
            @Param("reservedCodes") Set<String>  reservedCodes);

    /** Lookup by composite PK. The {@code (biz_key, use_usag_cd)}
     *  shape guarantees tenant safety — a cross-tenant lookup returns
     *  empty. Used by Detail / Update / Delete / Restore. */
    Optional<BusiUsagSetInfm> findByBizKeyAndUseUsagCd(String bizKey, String useUsagCd);

    /** Duplicate-{@code hand_use_usag_cd} guard for Create — scoped to
     *  the target group. Mirrors legacy {@code findActivePurposeByHandCd}
     *  at {@code comm_0187_001_c001_act.jsp:36-53}. */
    @Query("""
        SELECT p FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND ((:group IS NULL AND p.usagGrpCd IS NULL) OR p.usagGrpCd = :group)
          AND p.useYn = 'Y'
          AND p.handUseUsagCd = :handCd
        """)
    Optional<BusiUsagSetInfm> findActiveByHandCd(
            @Param("bizKey") String bizKey,
            @Param("group")  String group,
            @Param("handCd") String handCd);

    /** Duplicate-{@code use_usag_nm} guard for Create — scoped to the
     *  target group. Mirrors legacy {@code findActivePurposeByName} at
     *  {@code comm_0187_001_c001_act.jsp:55-72}. */
    @Query("""
        SELECT p FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND ((:group IS NULL AND p.usagGrpCd IS NULL) OR p.usagGrpCd = :group)
          AND p.useYn = 'Y'
          AND p.useUsagNm = :name
        """)
    Optional<BusiUsagSetInfm> findActiveByName(
            @Param("bizKey") String bizKey,
            @Param("group")  String group,
            @Param("name")   String name);

    /** Duplicate-name guard for Update — excludes the row being
     *  updated, scoped to the row's own group. Mirrors legacy
     *  {@code findDuplicateNameForUpdate} at
     *  {@code comm_0187_001_u002_act.jsp:54-72}. */
    @Query("""
        SELECT p FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND ((:group IS NULL AND p.usagGrpCd IS NULL) OR p.usagGrpCd = :group)
          AND p.useYn = 'Y'
          AND p.useUsagNm = :name
          AND p.useUsagCd <> :selfUseUsagCd
        """)
    Optional<BusiUsagSetInfm> findDuplicateNameExcludingSelf(
            @Param("bizKey")        String bizKey,
            @Param("group")         String group,
            @Param("name")          String name,
            @Param("selfUseUsagCd") String selfUseUsagCd);

    /** Legacy delete pre-check: "row has active children". Mirrors
     *  {@code hasActiveChildren} at
     *  {@code comm_0187_001_u002_act.jsp:75-91}. */
    @Query("""
        SELECT (count(p) > 0) FROM BusiUsagSetInfm p
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
        SELECT p.useUsagCd FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND p.parentUseUsagCd = :parentUseUsagCd
          AND p.useYn = 'Y'
        """)
    List<String> findActiveChildCodes(
            @Param("bizKey")          String bizKey,
            @Param("parentUseUsagCd") String parentUseUsagCd);

    /** {@code MAX(disp_ord)} within the active scope for the target
     *  group. Service adds {@code +1} for the new row's disp_ord. */
    @Query("""
        SELECT MAX(p.dispOrd) FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND ((:group IS NULL AND p.usagGrpCd IS NULL) OR p.usagGrpCd = :group)
          AND p.useYn = 'Y'
        """)
    Optional<Integer> findMaxDispOrd(
            @Param("bizKey") String bizKey,
            @Param("group")  String group);

    /** Bulk-load by PK set — used by Reorder to fetch every row the
     *  payload touches in one query before applying disp_ord updates.
     *  Group-agnostic (matches by PK) so a single reorder call can
     *  reorder within whichever tab the rows belong to. */
    @Query("""
        SELECT p FROM BusiUsagSetInfm p
        WHERE p.bizKey = :bizKey
          AND p.useYn = 'Y'
          AND p.useUsagCd IN :useUsagCds
        """)
    List<BusiUsagSetInfm> findAllActiveForReorder(
            @Param("bizKey")     String       bizKey,
            @Param("useUsagCds") Set<String>  useUsagCds);
}
