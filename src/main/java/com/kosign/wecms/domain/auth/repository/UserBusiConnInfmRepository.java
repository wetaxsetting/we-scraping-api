package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserBusiConnInfm;
import com.kosign.wecms.domain.auth.entity.UserBusiConnInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBusiConnInfmRepository extends JpaRepository<UserBusiConnInfm, UserBusiConnInfmId> {

    /**
     * Locate the single active link for a user within a specific tenant.
     * Used by /auth/login (finalize step, when the caller supplied a
     * biz_key) and by /auth/me (when the JWT already carries the
     * resolved biz_key). The BIZ_KEY here is the one resolved from the
     * link table or the JWT — never a wildcard, never a value the
     * frontend can spoof.
     */
    Optional<UserBusiConnInfm> findByUserIdAndBizKeyAndDelYn(
            String userId, String bizKey, String delYn);

    /**
     * Every active company link for a user across the local tenant set.
     * Drives the company-chooser branch of /auth/login: when this returns
     * exactly one row, the login auto-picks that company; when it returns
     * two or more, the response carries {@code companies_available} and
     * the frontend asks the user to choose.
     *
     * <p>The result must NOT be trusted as a tenant filter — the caller
     * still has to ensure the BIZ_KEY ultimately bound to the JWT exists
     * in this list, never echoing the frontend's payload directly into
     * the token.
     */
    List<UserBusiConnInfm> findAllByUserIdAndDelYn(String userId, String delYn);
}
