package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusiAdmnInfmRepository extends JpaRepository<BusiAdmnInfm, BusiAdmnInfmId> {

    /**
     * Authority probe for a single (BIZ_KEY, USER_ID): presence in
     * BUSI_ADMN_INFM means the user is an ADMIN of that company; absence
     * means USER. Used at login finalize to stamp the JWT's authGrCd
     * claim. Legacy USER_BUSI_CONN_INFM carries no AUTH_GR_CD column
     * (confirmed on DEV 2026-05-11), so the admin-master table is the
     * authoritative source.
     */
    boolean existsByBizKeyAndUserId(String bizKey, String userId);

    /**
     * The admin row for a single (BIZ_KEY, USER_ID). Used at login finalize:
     * presence decides authGrCd (ADMIN vs USER) AND supplies {@code USER_NM}
     * for the BUSI_ADMN_LOGIN_HIS audit row — legacy sources REG_NM from
     * {@code busiAdmnInfm.getUserNm()}, not USER_INFM
     * ({@code logn_2001_001_r001_act.jsp:277-278}).
     */
    Optional<BusiAdmnInfm> findByBizKeyAndUserId(String bizKey, String userId);

    /**
     * Every BUSI_ADMN_INFM row for a user. Used when building the
     * login chooser response so the frontend can show "ADMIN" /
     * "USER" next to each company option. Most users will return at
     * most one row.
     */
    List<BusiAdmnInfm> findAllByUserId(String userId);

    /**
     * Count of admin rows for a tenant — used by Settings &gt; Users
     * to enforce the "at least one admin must remain" invariant when
     * an admin row is removed (either by demote on PUT or by delete).
     */
    long countByBizKey(String bizKey);

    /**
     * Every BUSI_ADMN_INFM row for a tenant. Used by Settings &gt; Users
     * ({@code UserManagementService.adminIdSet}) to resolve the set of
     * business-admin user ids for the tenant.
     */
    List<BusiAdmnInfm> findAllByBizKey(String bizKey);
}
