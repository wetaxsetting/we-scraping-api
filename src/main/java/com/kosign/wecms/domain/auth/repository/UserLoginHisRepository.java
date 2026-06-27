package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.entity.UserLoginHisId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserLoginHisRepository extends JpaRepository<UserLoginHis, UserLoginHisId> {

    /**
     * F-10 — the latest 5 attempts for a user, newest first. REG_DTM is a fixed-width
     * {@code yyyyMMddHHmmss} string so lexical DESC == chronological DESC; REG_UID is
     * the same-second tiebreaker. Used by {@code LoginLockoutService} to detect 5
     * consecutive failures.
     */
    List<UserLoginHis> findTop5ByUserIdOrderByRegDtmDescRegUidDesc(String userId);
}
