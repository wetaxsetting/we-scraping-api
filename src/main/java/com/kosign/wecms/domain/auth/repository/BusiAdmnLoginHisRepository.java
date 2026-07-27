package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.BusiAdmnLoginHis;
import com.kosign.wecms.domain.auth.entity.BusiAdmnLoginHisId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link BusiAdmnLoginHis}. <b>Write-only</b> — the admin
 * login-history row appended on a successful business-admin login (see
 * {@code AdminLoginHistoryService}). Live WeCMS only stores this audit and does
 * not surface the legacy {@code comm_0213_001} viewer, so no read/finder method
 * exists here by design (confirmed Sonny 2026-07-24).
 */
public interface BusiAdmnLoginHisRepository extends JpaRepository<BusiAdmnLoginHis, BusiAdmnLoginHisId> {
}
