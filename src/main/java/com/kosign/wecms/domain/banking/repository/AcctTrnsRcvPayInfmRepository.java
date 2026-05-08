package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Per §13.6 risk R3 + the user's Sprint 1 decision: implement the
 * Transaction History "confirmed" flag with derived methods first.
 * existsByBizKeyAndAcctSeqNoAndCnfmYn(...) returns true when at least
 * one ACCT_TRNS_RCV_PAY_INFM row for the given transaction has
 * {@code CNFM_YN = 'Y'}.
 *
 * Promote to a native JOIN against BUSA_DOC_RCV_PAY_HIS only if the
 * derived shape can no longer express the contract.
 */
public interface AcctTrnsRcvPayInfmRepository extends JpaRepository<AcctTrnsRcvPayInfm, AcctTrnsRcvPayInfmId> {

    boolean existsByBizKeyAndAcctSeqNoAndCnfmYn(String bizKey, String acctSeqNo, String cnfmYn);

    List<AcctTrnsRcvPayInfm> findByBizKeyAndAcctSeqNo(String bizKey, String acctSeqNo);

    List<AcctTrnsRcvPayInfm> findByBizKeyAndAcctSeqNoIn(String bizKey, List<String> acctSeqNos);
}
