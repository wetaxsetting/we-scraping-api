package com.kosign.wecms.domain.setting.users.repository;

import com.kosign.wecms.domain.setting.users.entity.UserMenuAuthInfm;
import com.kosign.wecms.domain.setting.users.entity.UserMenuAuthInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@link UserMenuAuthInfm}. Read pulls every active
 * grant for a (tenant, user) pair; write goes through the
 * delete-then-insert pattern from {@code MenuEntitlementService.save(...)}
 * so the response reflects the exact frontend-supplied set without
 * a per-row diff.
 */
public interface UserMenuAuthInfmRepository
        extends JpaRepository<UserMenuAuthInfm, UserMenuAuthInfmId> {

    /** Every row for the (tenant, user) pair, regardless of
     *  {@code USE_YN}. The service filters to {@code 'Y'} on the read
     *  path. */
    List<UserMenuAuthInfm> findAllByBizKeyAndUserId(String bizKey, String userId);

    /** Delete every row for the (tenant, user) pair — first half of
     *  the delete-then-insert save flow. */
    @Modifying
    @Query("DELETE FROM UserMenuAuthInfm m "
            + "WHERE m.bizKey = :bizKey AND m.userId = :userId")
    int deleteAllByBizKeyAndUserId(
            @Param("bizKey") String bizKey,
            @Param("userId") String userId);
}
