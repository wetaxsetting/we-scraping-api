package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasTransactionEntry;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.service.AutoMatchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Short, per-phase transactional persistence for the Bank Transaction Sync
 * async worker (Phase 2). Split out of {@code BankTransactionSyncService} so
 * the (blocking) Coocon gateway calls run <b>outside</b> any DB transaction —
 * the worker calls the gateway with no transaction open, then hands the parsed
 * result to one of these {@code @Transactional} methods, which opens a short
 * transaction, commits, and returns. No transaction is ever held across a
 * gateway round-trip or across a challenge wait.
 *
 * <p>Behaviour is moved <b>verbatim</b> from the former monolithic
 * {@code processOneAccount} — dedup on {@code BANK_TRNS_FULL_ID}, key
 * generation through {@code wb_get_next_val}, the P1 auto-matcher (run in the
 * SAME transaction as the inserts so it sees the just-inserted rows), and the
 * {@code ACCT_MSTR} balance + status-column stamps. The only semantic change
 * versus the pre-Phase-2 monolith: each account commits in its own transaction
 * instead of one batch-wide transaction, so a fail-fast abort mid-batch leaves
 * earlier accounts persisted (partial persistence). This matches the legacy
 * JEX {@code fnsh_0001_001_c001} per-account commit — the batch-wide rollback
 * was a refactor artifact, not legacy parity — and is documented in the audit
 * report.
 */
@Component
@RequiredArgsConstructor
public class BankTransactionSyncPersistence {

    private static final Logger log = LoggerFactory.getLogger(BankTransactionSyncPersistence.class);

    /** Coocon success code stamped on ACCT_MSTR after a clean job. */
    private static final String OK_CODE = NxisasResultCodes.OK;

    private final AcctMstrRepository acctRepo;
    private final AcctTrnsHstrRepository trnsRepo;
    private final LegacyFunctionRepository legacyFn;
    private final AutoMatchService autoMatchService;

    /** Per-account persist counts — every Coocon-returned row in the window
     *  (dedup hits included, matching the popup's "scraped" totals) plus the
     *  P1 auto-match count for this account. */
    public record PersistResult(
            int debitCount,
            int creditCount,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            int autoMatchedCount) { }

    /**
     * Phase 35% — dedup + INSERT the scraped rows into {@code ACCT_TRNS_HSTR},
     * then (in the SAME transaction, so the just-inserted debits are visible)
     * run the P1 auto-matcher. Returns the per-account counts + auto-match
     * count. Verbatim port of the former {@code persistEntries} + the
     * auto-match block from {@code processOneAccount}.
     */
    @Transactional
    public PersistResult persistTransactions(String bizKey, String userId, String bankCd,
                                             String acctNo, String ccyCd,
                                             List<NxisasTransactionEntry> entries, String nowDb) {
        int debit = 0;
        int credit = 0;
        BigDecimal debitSum = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;
        List<AcctTrnsHstr> newDebits = new ArrayList<>();

        for (NxisasTransactionEntry e : entries) {
            BigDecimal amt;
            String inoutDv;
            if (isPositive(e.getDebit())) {
                amt = parseDecimal(e.getDebit());
                inoutDv = "2";
                if (amt.signum() < 0) { amt = amt.negate(); inoutDv = "1"; }
            } else if (isPositive(e.getCredit())) {
                amt = parseDecimal(e.getCredit());
                inoutDv = "1";
                if (amt.signum() < 0) { amt = amt.negate(); inoutDv = "2"; }
            } else {
                continue;
            }

            if ("1".equals(inoutDv)) {
                credit++;
                creditSum = creditSum.add(amt);
            } else {
                debit++;
                debitSum = debitSum.add(amt);
            }

            String fullId = BankTrnsIdGenerator.fullId(
                    bankCd, e.getDate(), e.getTime(),
                    amt, inoutDv, e.getRemarks(), e.getRemarks2());
            if (trnsRepo.findFirstByBizKeyAndBankTrnsFullId(bizKey, fullId).isPresent()) {
                continue;
            }

            AcctTrnsHstr row = new AcctTrnsHstr();
            row.setBizKey(bizKey);
            row.setAcctSeqNo(legacyFn.nextAcctTrnsHstrAcctSeqNo(bizKey));
            row.setBankCd(bankCd);
            row.setAcctNo(acctNo);
            row.setTrscDt(e.getDate());
            row.setTrscTm(e.getTime() == null ? "" : e.getTime());
            row.setCcyCd(ccyCd);
            row.setInoutDv(inoutDv);
            row.setTrscAmt(amt);
            row.setBal(parseDecimal(e.getBalance()));
            row.setRmrk(e.getRemarks());
            row.setRmrk2(e.getRemarks2());
            row.setBankRefNo(e.getBankReference());
            row.setBankTrnsFullId(fullId);
            row.setBankTrnsShortId(BankTrnsIdGenerator.shortId(e.getDate(), fullId));
            row.setLinkMatchYn("N");
            row.setRegId(userId);
            row.setRegDtm(nowDb);
            row.setModId(userId);
            row.setModDtm(nowDb);

            trnsRepo.save(row);
            if ("2".equals(inoutDv)) newDebits.add(row);
        }

        // P1 AUTO-MATCH — same transaction as the inserts so the matcher sees
        // the just-inserted withdrawals (legacy fnsh_0001_001_c001:1853-2079).
        // Gated per-tenant by biz-env 00079 inside the service. A matcher
        // failure never aborts persistence (the rows are already saved).
        int autoMatched = 0;
        try {
            autoMatched = autoMatchService.autoMatchNewDebits(bizKey, userId, acctNo, newDebits);
        } catch (Exception amEx) {
            log.error("BankTransactionSync AUTO-MATCH failed [bank={}, acct={}] — sync continues",
                    bankCd, acctNo, amEx);
        }

        return new PersistResult(debit, credit, debitSum, creditSum, autoMatched);
    }

    /**
     * Phase 100% (persist leg) — stamp {@code ACCT_MSTR} with the balance +
     * login/transaction/balance status columns for a fully-processed account.
     * Loads the account fresh in this transaction (the worker read it detached
     * earlier), mutates, saves. Verbatim port of the {@code refreshBalance}
     * persist branch + the audit/status stamps from {@code processOneAccount}.
     *
     * @param balanceOk whether the BALANCE job returned a whitelisted code
     * @param balCode   bank-adapter balance code (error code when !balanceOk)
     * @param balMsg    balance error message (null on success)
     * @param bal       parsed current balance (null → leave unchanged)
     * @param avail     parsed available balance (null → leave unchanged)
     */
    @Transactional
    public void persistBalanceAndStamp(String bizKey, String userId, String bankCd, String acctNo,
                                       String nowDb, boolean balanceOk, String balCode, String balMsg,
                                       BigDecimal bal, BigDecimal avail) {
        AcctMstr acct = acctRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo)
                .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));

        // Clean LOGIN + TRANSACTION outcome for this account.
        acct.setLstLgnSucDtm(nowDb);
        acct.setAcctBalLstInqDtm(nowDb);
        acct.setLstLgnStts(OK_CODE);
        acct.setLstLgnSttsMsg(null);
        acct.setAcctHisInqStts(OK_CODE);
        acct.setAcctHisInqMsg(null);
        acct.setAcctHisLstInqDt(nowDb);
        acct.setModId(userId);
        acct.setModDtm(nowDb);

        if (balanceOk) {
            acct.setAcctBalInqStts(OK_CODE);
            acct.setAcctBalInqMsg(null);
            if (bal != null) acct.setBal(bal);
            if (avail != null) acct.setRealAmt(avail);
        } else {
            acct.setAcctBalInqStts(balCode == null || balCode.isBlank() ? "ERROR" : balCode);
            acct.setAcctBalInqMsg(balMsg);
        }

        acctRepo.save(acct);
    }

    /** Read-only helper — the per-account TRANSACTION window start for Auto
     *  mode (account's own last-success date). Runs in its own short read so
     *  the worker never holds a transaction while reading it. Mirrors the
     *  former {@code perAccountStartDt}. */
    @Transactional(readOnly = true)
    public AcctMstr loadAccount(String bizKey, String bankCd, String acctNo) {
        return acctRepo.findByBizKeyAndBankCdAndAcctNo(bizKey, bankCd, acctNo)
                .orElseThrow(() -> new BusinessException("WBE_BANK_ACCOUNT_NOT_FOUND"));
    }

    // ── tiny pure helpers (duplicated from the service — no shared state) ──

    private static boolean isPositive(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            BigDecimal v = parseDecimal(raw);
            return v != null && v.signum() != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null) return null;
        String t = raw.trim().replace(",", "");
        if (t.isEmpty()) return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
