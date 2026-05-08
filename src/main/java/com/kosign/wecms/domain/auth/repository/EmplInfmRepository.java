package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.EmplInfm;
import com.kosign.wecms.domain.auth.entity.EmplInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmplInfmRepository extends JpaRepository<EmplInfm, EmplInfmId> {
}
