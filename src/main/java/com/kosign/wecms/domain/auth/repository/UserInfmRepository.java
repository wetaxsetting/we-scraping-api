package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserInfm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInfmRepository extends JpaRepository<UserInfm, String> {

    /**
     * Used by Settings &gt; Users {@code GET /check-id?login_id=…}
     * (and as a race-condition fallback inside the {@code POST /users}
     * create path) to test whether a candidate login id is free.
     *
     * <p>The check is global, not tenant-scoped: {@code USER_INFM.USER_ID}
     * is a global PK across the whole platform, so a free id in one
     * tenant must also be free in every other tenant.
     */
    boolean existsByUserId(String userId);
}
