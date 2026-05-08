package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcctTrnsHstrRepository extends JpaRepository<AcctTrnsHstr, AcctTrnsHstrId> {

    /** Most-recent-first transaction history for one account (date desc, then time desc). */
    List<AcctTrnsHstr> findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(
            String bizKey, String bankCd, String acctNo, String fromDt, String toDt);

    /** Variant without a date range — used when the UI does not supply from/to. */
    List<AcctTrnsHstr> findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
            String bizKey, String bankCd, String acctNo);
}
