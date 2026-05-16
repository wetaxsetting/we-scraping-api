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

    /** Count credentials at one bank within the tenant. Used by the
     *  Disconnect-credential path to decide whether to cascade-delete
     *  the registered {@code ACCT_MSTR} rows for that bank (only when
     *  this was the last remaining credential — preserves accounts
     *  linked by sibling credentials). */
    long countByBizKeyAndBankCd(String bizKey, String bankCd);

    /** Pick the first registered credential at one bank (legacy parity:
     *  multiple accounts at one bank share one credential row, so
     *  resolving by {@code (BIZ_KEY, BANK_CD)} is enough for the sync
     *  flow). Returns the lowest {@code SEQ_NO} so behaviour is
     *  deterministic when more than one credential exists at the same
     *  bank (rare; happens when a user re-registers after a password
     *  change before deleting the old row). */
    Optional<BankCertInfo> findFirstByBizKeyAndBankCdOrderBySeqNoAsc(String bizKey, String bankCd);
}
