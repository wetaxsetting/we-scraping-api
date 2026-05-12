package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
     * Every BUSI_ADMN_INFM row for a user. Used when building the
     * login chooser response so the frontend can show "ADMIN" /
     * "USER" next to each company option. Most users will return at
     * most one row.
     */
    List<BusiAdmnInfm> findAllByUserId(String userId);
}
