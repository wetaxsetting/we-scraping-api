package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.BusiMatchLog;
import com.kosign.wecms.domain.banking.entity.BusiMatchLogId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusiMatchLogRepository extends JpaRepository<BusiMatchLog, BusiMatchLogId> {

    /** Find a live (LOCKED_YN='Y') match for the given bank transaction. */
    Optional<BusiMatchLog> findFirstByBizKeyAndBankTrnsShortIdAndLockedYn(
            String bizKey, String bankTrnsShortId, String lockedYn);

    /** Find a live (LOCKED_YN='Y') match for the given payment. */
    Optional<BusiMatchLog> findFirstByBizKeyAndPayTrnsIdAndLockedYn(
            String bizKey, String payTrnsId, String lockedYn);

    /** Find a match-log row for a specific (bank txn, payment) pair at a given
     *  lock state — used by the Payroll batch match for the duplicate guard
     *  (LOCKED_YN='Y') and the re-lock upsert lookup (LOCKED_YN='N'). Mirrors
     *  legacy {@code comm_0197_001_c001} {@code getExistingMatchDynamic}. */
    Optional<BusiMatchLog> findFirstByBizKeyAndBankTrnsShortIdAndPayTrnsIdAndLockedYn(
            String bizKey, String bankTrnsShortId, String payTrnsId, String lockedYn);

    /** Used by the MAX+1 sequence generator to find the highest
     *  numeric `match_log_seq` for the tenant. */
    List<BusiMatchLog> findByBizKey(String bizKey);
}
