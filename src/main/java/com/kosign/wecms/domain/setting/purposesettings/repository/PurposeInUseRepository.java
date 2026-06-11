package com.kosign.wecms.domain.setting.purposesettings.repository;

import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetId;
import com.kosign.wecms.domain.setting.purposesettings.entity.VPurposeInUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

/**
 * Read-only repository over the {@code v_purpose_in_use} view (V81).
 * Drives:
 * <ol>
 *   <li>The {@code used_in_transactions} wire field on every list
 *       response — service joins the view in memory after the list
 *       fetch (PK match against {@link VPurposeInUse}).</li>
 *   <li>The recursive delete pre-check (OQ-5) — service collects the
 *       deleted row's {@code use_usag_cd} plus every active child's
 *       {@code use_usag_cd}, then calls
 *       {@link #existsAnyByBizKeyAndUseUsagCdIn} to check whether ANY
 *       of those codes is referenced by an active row in the view.</li>
 * </ol>
 *
 * <p>The view is read-only; no save path here.
 */
public interface PurposeInUseRepository
        extends JpaRepository<VPurposeInUse, BusiUsagSetId> {

    /** Exists-check across a set of codes — one query covers the
     *  parent-and-every-active-child recursive scope. */
    @Query("""
        SELECT (count(v) > 0) FROM VPurposeInUse v
        WHERE v.bizKey = :bizKey
          AND v.useUsagCd IN :useUsagCds
        """)
    boolean existsAnyByBizKeyAndUseUsagCdIn(
            @Param("bizKey")     String      bizKey,
            @Param("useUsagCds") Set<String> useUsagCds);

    /** Bulk lookup — used by list to populate {@code used_in_transactions}
     *  in one query for all rows on the page. */
    @Query("""
        SELECT v.useUsagCd FROM VPurposeInUse v
        WHERE v.bizKey = :bizKey
          AND v.useUsagCd IN :useUsagCds
        """)
    Set<String> findInUseCodes(
            @Param("bizKey")     String      bizKey,
            @Param("useUsagCds") Set<String> useUsagCds);
}
