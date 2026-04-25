package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBusiConnInfmRepository extends JpaRepository<UserBusiConnInfm, UserBusiConnInfmId> {

    /**
     * Locate the single active link for a user within the configured tenant.
     * Single-tenant R&D — never call with a wildcard BIZ_KEY.
     */
    Optional<UserBusiConnInfm> findByUserIdAndBizKeyAndDelYn(
            String userId, String bizKey, String delYn);
}
