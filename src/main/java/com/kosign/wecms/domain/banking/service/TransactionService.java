package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.InoutDv;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.TransactionConfirmRequest;
import com.kosign.wecms.domain.banking.dto.TransactionDetailResponse;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import com.kosign.wecms.domain.banking.dto.TransactionSummaryResponse;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Transaction History (VND/FCY) service. Mirrors legacy
 * {@code trns_0001_001} family — list/detail/confirm/undo.
 *
 * Sprint 2 additions (2026-05-14):
 * <ul>
 *   <li>{@code cnfmYn} filter — Confirmed / Unconfirmed / All.</li>
 *   <li>{@code keyword} filter — partial match on RMRK / BANK_REF_NO.</li>
 *   <li>{@code summary} aggregate — in/out totals + confirmed count.</li>
 *   <li>{@code detail} — single transaction with VAS / counterparty / confirmation meta.</li>
 *   <li>{@code confirm} / {@code undoConfirm} — write ACCT_TRNS_RCV_PAY_INFM.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String CODE_BAD_REQUEST = "WBE2002";
    private static final String CODE_NOT_FOUND = "WBE_TRANSACTION_NOT_FOUND";
    private static final String CODE_ALREADY_CONFIRMED = "WBE_TRANSACTION_ALREADY_CONFIRMED";
    private static final String CODE_NOT_CONFIRMED = "WBE_TRANSACTION_NOT_CONFIRMED";

    private static final DateTimeFormatter DTM_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AcctTrnsHstrRepository txnRepo;
    private final AcctTrnsRcvPayInfmRepository rcvPayRepo;

    @Transactional(readOnly = true)
    public TransactionListResponse list(String acctSeqNo, String fromDt, String toDt, String typeFilter) {
        return list(acctSeqNo, fromDt, toDt, typeFilter, null, null);
    }

    /**
     * Extended list — adds {@code cnfmFilter} (Y/N/ALL) and {@code keyword} (RMRK/BANK_REF_NO).
     * Filters apply in-memory after the DB date-range query — list size is bounded by the
     * per-account history depth.
     */
    @Transactional(readOnly = true)
    public TransactionListResponse list(String acctSeqNo, String fromDt, String toDt,
                                        String typeFilter, String cnfmFilter, String keyword) {
        Resolved r = resolve(acctSeqNo);
        List<AcctTrnsHstr> rows = fetchRows(r, fromDt, toDt);

        rows = rows.stream()
                .filter(row -> matchesTypeFilter(row.getInoutDv(), typeFilter))
                .filter(row -> matchesKeyword(row, keyword))
                .toList();

        Set<String> confirmedSeqs = confirmedSeqs(r.bizKey, rows);
        rows = rows.stream()
                .filter(row -> matchesCnfmFilter(confirmedSeqs.contains(row.getAcctSeqNo()), cnfmFilter))
                .toList();

        BigDecimal depositTotal = BigDecimal.ZERO;
        BigDecimal withdrawTotal = BigDecimal.ZERO;
        for (AcctTrnsHstr row : rows) {
            BigDecimal amt = row.getTrscAmt() != null ? row.getTrscAmt() : BigDecimal.ZERO;
            if (InoutDv.IN.code().equals(row.getInoutDv())) {
                depositTotal = depositTotal.add(amt);
            } else if (InoutDv.OUT.code().equals(row.getInoutDv())) {
                withdrawTotal = withdrawTotal.add(amt);
            }
        }

        List<TransactionRow> mapped = rows.stream()
                .map(row -> toRow(row, confirmedSeqs.contains(row.getAcctSeqNo())))
                .toList();

        return new TransactionListResponse(
                mapped,
                mapped.size(),
                AmountFormatter.wire(depositTotal),
                AmountFormatter.wire(withdrawTotal)
        );
    }

    /**
     * Summary aggregates — total deposit / withdrawal, count, confirmed-vs-total.
     * Shares the same filter contract as {@link #list}.
     */
    @Transactional(readOnly = true)
    public TransactionSummaryResponse summary(String acctSeqNo, String fromDt, String toDt,
                                              String typeFilter, String cnfmFilter, String keyword) {
        Resolved r = resolve(acctSeqNo);
        List<AcctTrnsHstr> rows = fetchRows(r, fromDt, toDt).stream()
                .filter(row -> matchesTypeFilter(row.getInoutDv(), typeFilter))
                .filter(row -> matchesKeyword(row, keyword))
                .toList();

        Set<String> confirmedSeqs = confirmedSeqs(r.bizKey, rows);
        rows = rows.stream()
                .filter(row -> matchesCnfmFilter(confirmedSeqs.contains(row.getAcctSeqNo()), cnfmFilter))
                .toList();

        BigDecimal depositTotal = BigDecimal.ZERO;
        BigDecimal withdrawTotal = BigDecimal.ZERO;
        int confirmed = 0;
        for (AcctTrnsHstr row : rows) {
            BigDecimal amt = row.getTrscAmt() != null ? row.getTrscAmt() : BigDecimal.ZERO;
            if (InoutDv.IN.code().equals(row.getInoutDv())) depositTotal = depositTotal.add(amt);
            else if (InoutDv.OUT.code().equals(row.getInoutDv())) withdrawTotal = withdrawTotal.add(amt);
            if (confirmedSeqs.contains(row.getAcctSeqNo())) confirmed++;
        }
        return new TransactionSummaryResponse(
                rows.size(),
                confirmed,
                rows.size() - confirmed,
                AmountFormatter.wire(depositTotal),
                AmountFormatter.wire(withdrawTotal)
        );
    }

    /** Single-transaction detail. {@code trnsSeqNo} is the {@code ACCT_SEQ_NO} surrogate. */
    @Transactional(readOnly = true)
    public TransactionDetailResponse detail(String acctSeqNo, String trnsSeqNo) {
        Resolved r = resolve(acctSeqNo);
        AcctTrnsHstr row = txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        Optional<AcctTrnsRcvPayInfm> rcvOpt = rcvPayRepo.findByBizKeyAndAcctSeqNo(r.bizKey, trnsSeqNo)
                .stream()
                .filter(rp -> "Y".equals(rp.getCnfmYn()))
                .findFirst();

        boolean confirmed = rcvOpt.isPresent();
        boolean isDeposit = InoutDv.IN.code().equals(row.getInoutDv());
        return new TransactionDetailResponse(
                row.getAcctSeqNo(),
                row.getTrscDt(),
                row.getTrscTm(),
                row.getInoutDv(),
                isDeposit ? AmountFormatter.wire(row.getTrscAmt()) : null,
                !isDeposit ? AmountFormatter.wire(row.getTrscAmt()) : null,
                AmountFormatter.wire(row.getBal()),
                row.getRmrk() != null ? row.getRmrk() : "",
                row.getRmrk2() != null ? row.getRmrk2() : "",
                row.getBankRefNo(),
                row.getCcyCd(),
                confirmed ? "Y" : "N",
                rcvOpt.map(AcctTrnsRcvPayInfm::getRcvPayTrcoKey).orElse(null),
                rcvOpt.map(AcctTrnsRcvPayInfm::getRcvPayTrcoNm).orElse(null),
                rcvOpt.map(AcctTrnsRcvPayInfm::getRcvPayUsagCd).orElse(null),
                rcvOpt.map(AcctTrnsRcvPayInfm::getVasAcctCd).orElse(null),
                rcvOpt.map(AcctTrnsRcvPayInfm::getCnfmByUserId).orElse(null),
                rcvOpt.map(AcctTrnsRcvPayInfm::getCnfmByUserNm).orElse(null),
                rcvOpt.map(AcctTrnsRcvPayInfm::getCnfmDtm).orElse(null)
        );
    }

    /**
     * Confirm a transaction — write a new ACCT_TRNS_RCV_PAY_INFM row (or flip the existing
     * row's CNFM_YN to 'Y'). {@code RCV_PAY_SEQ_NO} is allocated as per-(BIZ_KEY,ACCT_SEQ_NO)
     * MAX+1 in Java (no new wb_get_next_val branch — per CLAUDE.md key-generation rule for
     * Sprint 2+ writes).
     */
    @Transactional
    public TransactionDetailResponse confirm(String acctSeqNo, String trnsSeqNo, TransactionConfirmRequest req) {
        Resolved r = resolve(acctSeqNo);

        AcctTrnsHstr row = txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        List<AcctTrnsRcvPayInfm> existing = rcvPayRepo.findByBizKeyAndAcctSeqNo(r.bizKey, trnsSeqNo);
        boolean alreadyConfirmed = existing.stream().anyMatch(rp -> "Y".equals(rp.getCnfmYn()));
        if (alreadyConfirmed) {
            throw new BusinessException(CODE_ALREADY_CONFIRMED);
        }

        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        if (userNm == null) userNm = userId;
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);

        AcctTrnsRcvPayInfm entry = existing.stream().findFirst().orElseGet(() -> {
            AcctTrnsRcvPayInfm fresh = new AcctTrnsRcvPayInfm();
            fresh.setBizKey(r.bizKey);
            fresh.setAcctSeqNo(trnsSeqNo);
            fresh.setRcvPaySeqNo(nextRcvPaySeqNo(r.bizKey, trnsSeqNo));
            fresh.setBankCd(r.bankCd);
            fresh.setAcctNo(r.acctNo);
            fresh.setTrscDt(row.getTrscDt());
            fresh.setRcvPayAmt(row.getTrscAmt());
            fresh.setRegId(userId);
            fresh.setRegDtm(nowDtm);
            return fresh;
        });

        if (req != null) {
            entry.setRcvPayTrcoKey(req.rcvPayTrcoKey());
            entry.setRcvPayTrcoNm(req.rcvPayTrcoNm());
            entry.setRcvPayUsagCd(req.rcvPayUsagCd());
            entry.setVasAcctCd(req.vasAcctCd());
        }
        entry.setCnfmYn("Y");
        entry.setCnfmByUserId(userId);
        entry.setCnfmByUserNm(userNm);
        entry.setCnfmDtm(nowDtm);
        entry.setModId(userId);
        entry.setModDtm(nowDtm);

        rcvPayRepo.save(entry);
        return detail(acctSeqNo, trnsSeqNo);
    }

    /** Undo confirmation — flip CNFM_YN to 'N' on the existing row. */
    @Transactional
    public void undoConfirm(String acctSeqNo, String trnsSeqNo) {
        Resolved r = resolve(acctSeqNo);
        List<AcctTrnsRcvPayInfm> existing = rcvPayRepo.findByBizKeyAndAcctSeqNo(r.bizKey, trnsSeqNo);
        AcctTrnsRcvPayInfm confirmed = existing.stream()
                .filter(rp -> "Y".equals(rp.getCnfmYn()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_CONFIRMED));

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        confirmed.setCnfmYn("N");
        confirmed.setCnfmByUserId(null);
        confirmed.setCnfmByUserNm(null);
        confirmed.setCnfmDtm(null);
        confirmed.setModId(userId);
        confirmed.setModDtm(nowDtm);
        rcvPayRepo.save(confirmed);
    }

    /* ------------------------------------------------------------------ */

    /**
     * Per-(BIZ_KEY,ACCT_SEQ_NO) MAX+1 in Java. No new wb_get_next_val branch — Sprint 2+
     * writes use service-side generators per CLAUDE.md "Key-generation rule for migrated
     * legacy tables".
     */
    private String nextRcvPaySeqNo(String bizKey, String acctSeqNo) {
        List<AcctTrnsRcvPayInfm> existing = rcvPayRepo.findByBizKeyAndAcctSeqNo(bizKey, acctSeqNo);
        long max = 0;
        for (AcctTrnsRcvPayInfm rp : existing) {
            try {
                long v = Long.parseLong(rp.getRcvPaySeqNo());
                if (v > max) max = v;
            } catch (NumberFormatException ignored) {
                // non-numeric legacy seq — skip
            }
        }
        return String.valueOf(max + 1);
    }

    private Resolved resolve(String acctSeqNo) {
        if (acctSeqNo == null || !acctSeqNo.contains("_")) {
            throw new BusinessException(CODE_BAD_REQUEST);
        }
        int sep = acctSeqNo.indexOf('_');
        return new Resolved(TenantContext.currentBizKey(),
                acctSeqNo.substring(0, sep),
                acctSeqNo.substring(sep + 1));
    }

    private List<AcctTrnsHstr> fetchRows(Resolved r, String fromDt, String toDt) {
        return (fromDt == null || fromDt.isBlank() || toDt == null || toDt.isBlank())
                ? txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(r.bizKey, r.bankCd, r.acctNo)
                : txnRepo.findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo, fromDt, toDt);
    }

    private static boolean matchesTypeFilter(String inoutDv, String typeFilter) {
        if (typeFilter == null || typeFilter.isBlank() || "ALL".equalsIgnoreCase(typeFilter)) return true;
        if ("DEPOSIT".equalsIgnoreCase(typeFilter))  return InoutDv.IN.code().equals(inoutDv);
        if ("WITHDRAW".equalsIgnoreCase(typeFilter)) return InoutDv.OUT.code().equals(inoutDv);
        return true;
    }

    private static boolean matchesCnfmFilter(boolean confirmed, String cnfmFilter) {
        if (cnfmFilter == null || cnfmFilter.isBlank() || "ALL".equalsIgnoreCase(cnfmFilter)) return true;
        if ("Y".equalsIgnoreCase(cnfmFilter)) return confirmed;
        if ("N".equalsIgnoreCase(cnfmFilter)) return !confirmed;
        return true;
    }

    private static boolean matchesKeyword(AcctTrnsHstr row, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String needle = keyword.trim().toLowerCase();
        return containsLc(row.getRmrk(), needle)
                || containsLc(row.getRmrk2(), needle)
                || containsLc(row.getBankRefNo(), needle);
    }

    private static boolean containsLc(String haystack, String lcNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lcNeedle);
    }

    private Set<String> confirmedSeqs(String bizKey, List<AcctTrnsHstr> rows) {
        if (rows.isEmpty()) return Set.of();
        List<String> seqs = rows.stream().map(AcctTrnsHstr::getAcctSeqNo).toList();
        Set<String> out = new HashSet<>();
        for (AcctTrnsRcvPayInfm rp : rcvPayRepo.findByBizKeyAndAcctSeqNoIn(bizKey, seqs)) {
            if ("Y".equals(rp.getCnfmYn())) out.add(rp.getAcctSeqNo());
        }
        return out;
    }

    private TransactionRow toRow(AcctTrnsHstr r, boolean confirmed) {
        boolean isDeposit = InoutDv.IN.code().equals(r.getInoutDv());
        BigDecimal amt = r.getTrscAmt();
        return new TransactionRow(
                r.getAcctSeqNo(),
                r.getTrscDt(),
                r.getTrscTm(),
                r.getInoutDv(),
                isDeposit ? AmountFormatter.wire(amt) : null,
                !isDeposit ? AmountFormatter.wire(amt) : null,
                AmountFormatter.wire(r.getBal()),
                r.getRmrk() != null ? r.getRmrk() : "",
                confirmed ? "Y" : "N",
                null,
                null
        );
    }

    private record Resolved(String bizKey, String bankCd, String acctNo) { }
}
