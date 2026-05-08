package com.kosign.wecms.domain.setting.repository;

import com.kosign.wecms.domain.setting.entity.UserScrOptionWecms;
import com.kosign.wecms.domain.setting.entity.UserScrOptionWecmsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserScrOptionWecmsRepository
        extends JpaRepository<UserScrOptionWecms, UserScrOptionWecmsId> {

    /** User overrides for a screen + area, ordered for the UI. */
    List<UserScrOptionWecms> findByBizKeyAndUserIdAndWebSvcIdAndDymAreaIdOrderByDispOrdAsc(
            String bizKey, String userId, String webSvcId, String dymAreaId);

    /** Wipe every row matching the (tenant, user, screen, area) scope.
     *  Used by Save (delete-and-insert pattern) and Reset (wipe to fall
     *  back to system defaults via merge-on-read). */
    @Modifying
    @Query("delete from UserScrOptionWecms u "
            + "where u.bizKey = :bizKey "
            + "  and u.userId = :userId "
            + "  and u.webSvcId = :webSvcId "
            + "  and u.dymAreaId = :dymAreaId")
    int deleteAllForScope(
            @Param("bizKey") String bizKey,
            @Param("userId") String userId,
            @Param("webSvcId") String webSvcId,
            @Param("dymAreaId") String dymAreaId);
}
