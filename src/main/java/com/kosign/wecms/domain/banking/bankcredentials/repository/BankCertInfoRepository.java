package com.kosign.wecms.domain.banking.bankcredentials.repository;

import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfo;
import com.kosign.wecms.domain.banking.bankcredentials.entity.BankCertInfoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BankCertInfoRepository extends JpaRepository<BankCertInfo, BankCertInfoId> {

    List<BankCertInfo> findByBizKeyOrderBySeqNoAsc(String bizKey);

    Optional<BankCertInfo> findByBizKeyAndSeqNo(String bizKey, BigDecimal seqNo);

    /** Next SEQ_NO for the tenant. Returns 0 if no rows exist, so the
     *  caller increments to 1. Bound to {@code BIZ_KEY} — the tenant's
     *  own seq space. */
    @Query("SELECT COALESCE(MAX(b.seqNo), 0) FROM BankCertInfo b WHERE b.bizKey = :bizKey")
    BigDecimal findMaxSeqNo(String bizKey);
}
