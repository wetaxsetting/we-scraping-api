package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a failed-scrape status onto {@code ACCT_MSTR} in its own
 * {@code REQUIRES_NEW} transaction so the failure survives the outer
 * Bank Transaction Sync transaction's rollback.
 *
 * <p>HR-2 legacy parity: when a TRANSACTION / BALANCE job throws
 * ({@code BankTransactionSyncService.assertOk} → {@code WBE2095}), the
 * {@code @Transactional} on {@code start()} / {@code submit*()} rolls
 * back every write made during the batch — including any status the
 * orchestrator stamped on the managed entity. The legacy
 * {@code fnsh_0001_001_c001_act.jsp}, by contrast, commits the failure
 * status so the account row reads "Failed" on the next list load. This
 * helper restores that behaviour by writing the status in an
 * independent transaction (mirrors the existing
 * {@code SyncHistoryWriter.recordAttempt} REQUIRES_NEW audit write).
 *
 * <p>Login / gateway failures take a non-throwing path
 * ({@code handleLoginContinuation} returns an {@code ERROR} response),
 * so the outer transaction commits and a REQUIRES_NEW write there is
 * simply an independent commit — harmless and consistent.
 */
@Component
@RequiredArgsConstructor
public class AcctMstrStatusUpdater {

    private final AcctMstrRepository acctRepo;

    /** Max length of the legacy {@code *_STTS_MSG} columns. */
    private static final int MSG_MAX = 255;
    /** Max length of the legacy {@code *_STTS} code columns. */
    private static final int CODE_MAX = 20;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(String bizKey, String bankCd, String acctNo,
                            String code, String msg, String userId, String nowDb) {
        acctRepo.markScrapeFailure(
                bizKey, bankCd, acctNo,
                trunc(blankToDash(code), CODE_MAX),
                trunc(msg, MSG_MAX),
                userId, nowDb);
    }

    private static String blankToDash(String code) {
        return (code == null || code.isBlank()) ? "ERROR" : code;
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
