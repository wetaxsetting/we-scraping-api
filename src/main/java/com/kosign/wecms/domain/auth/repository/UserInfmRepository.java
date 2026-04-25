package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.UserInfm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInfmRepository extends JpaRepository<UserInfm, String> {
}
