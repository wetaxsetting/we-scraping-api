package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserLoginHis;
import com.kosign.wecms.domain.auth.entity.UserLoginHisId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLoginHisRepository extends JpaRepository<UserLoginHis, UserLoginHisId> {
}
