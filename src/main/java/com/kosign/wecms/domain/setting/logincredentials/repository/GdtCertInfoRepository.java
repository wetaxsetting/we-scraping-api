package com.kosign.wecms.domain.setting.logincredentials.repository;

import com.kosign.wecms.domain.setting.logincredentials.entity.GdtCertInfo;
import com.kosign.wecms.domain.setting.logincredentials.entity.GdtCertInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GdtCertInfoRepository extends JpaRepository<GdtCertInfo, GdtCertInfoId> {

    List<GdtCertInfo> findByBizKeyOrderBySeqNoAsc(String bizKey);

    Optional<GdtCertInfo> findByBizKeyAndSeqNo(String bizKey, BigDecimal seqNo);

    /** Per-tenant MAX+1 generator. Returns 0 if the tenant has no
     *  GDT rows so the caller increments to 1. Mirrors the
     *  {@code BankCertInfoRepository#findMaxSeqNo} pattern; no
     *  legacy {@code wb_get_next_val} branch (V118 deliberately
     *  did NOT add one — Java-side per-tenant generator, with a
     *  one-shot retry on {@code DataIntegrityViolationException}
     *  guarded at the service layer). */
    @Query("SELECT COALESCE(MAX(g.seqNo), 0) FROM GdtCertInfo g WHERE g.bizKey = :bizKey")
    BigDecimal findMaxSeqNo(String bizKey);
}
