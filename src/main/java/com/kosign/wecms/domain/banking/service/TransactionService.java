package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.InoutDv;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transaction History (VND/FCY) service. Mirrors legacy
 * {@code fnsh_0002_001_r001} / {@code fnsh_0006_001_r001}.
 *
 * The static UI's "confirmed" badge is derived from
 * {@link AcctTrnsRcvPayInfmRepository#existsByBizKeyAndAcctSeqNoAndConfYn}
 * — derived method first, per §13.6 risk R3 + the user's Sprint 1
 * decision. Promote to @Query if the derived shape can no longer
 * express the contract.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String CODE_BAD_REQUEST = "WBE2002";

    private final AcctTrnsHstrRepository txnRepo;
    private final AcctTrnsRcvPayInfmRepository rcvPayRepo;

    @Transactional(readOnly = true)
    public TransactionListResponse list(String acctSeqNo, String fromDt, String toDt, String typeFilter) {
        final String bizKey = TenantContext.currentBizKey();
        if (acctSeqNo == null || !acctSeqNo.contains("_")) {
            // Synthetic ID is BANK_CD + "_" + ACCT_NO; reject malformed input
            // before hitting the DB. See AccountService.toRow().
            throw new BusinessException(CODE_BAD_REQUEST);
        }
        int sep = acctSeqNo.indexOf('_');
        String bankCd = acctSeqNo.substring(0, sep);
        String acctNo = acctSeqNo.substring(sep + 1);

        List<AcctTrnsHstr> rows = (fromDt == null || fromDt.isBlank() || toDt == null || toDt.isBlank())
                ? txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(bizKey, bankCd, acctNo)
                : txnRepo.findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(bizKey, bankCd, acctNo, fromDt, toDt);

        // INOUT_DV: '1' = deposit (in), '2' = withdraw (out) — verified
        // against wecmsvndev seed on 2026-04-29. Earlier code used 'I'/'O'
        // which never matched the live encoding (silent zero totals).
        // Now routed through InoutDv enum (common/enums/InoutDv).
        rows = rows.stream().filter(r -> matchesTypeFilter(r.getInoutDv(), typeFilter)).toList();

        // Bulk-resolve the "confirmed" flag in one query so we don't N+1
        // existsBy across the response.
        Set<String> confirmedSeqs = confirmedSeqs(bizKey, rows);

        BigDecimal depositTotal = BigDecimal.ZERO;
        BigDecimal withdrawTotal = BigDecimal.ZERO;
        for (AcctTrnsHstr r : rows) {
            BigDecimal amt = r.getTrscAmt() != null ? r.getTrscAmt() : BigDecimal.ZERO;
            if (InoutDv.IN.code().equals(r.getInoutDv())) {
                depositTotal = depositTotal.add(amt);
            } else if (InoutDv.OUT.code().equals(r.getInoutDv())) {
                withdrawTotal = withdrawTotal.add(amt);
            }
        }

        List<TransactionRow> mapped = rows.stream()
                .map(r -> toRow(r, confirmedSeqs.contains(r.getAcctSeqNo())))
                .toList();

        return new TransactionListResponse(
                mapped,
                mapped.size(),
                AmountFormatter.wire(depositTotal),
                AmountFormatter.wire(withdrawTotal)
        );
    }

    private static boolean matchesTypeFilter(String inoutDv, String typeFilter) {
        if (typeFilter == null || typeFilter.isBlank() || "ALL".equalsIgnoreCase(typeFilter)) return true;
        if ("DEPOSIT".equalsIgnoreCase(typeFilter))  return InoutDv.IN.code().equals(inoutDv);
        if ("WITHDRAW".equalsIgnoreCase(typeFilter)) return InoutDv.OUT.code().equals(inoutDv);
        return true;
    }

    private Set<String> confirmedSeqs(String bizKey, List<AcctTrnsHstr> rows) {
        if (rows.isEmpty()) return Set.of();
        List<String> seqs = rows.stream().map(AcctTrnsHstr::getAcctSeqNo).toList();
        return rcvPayRepo.findByBizKeyAndAcctSeqNoIn(bizKey, seqs).stream()
                .filter(rp -> "Y".equals(rp.getCnfmYn()))
                .map(rp -> rp.getAcctSeqNo())
                .collect(Collectors.toCollection(HashSet::new));
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
                null,  // CONFIRMED_BY — populated only when bulk-pulled rcv-pay rows include the confirmer; left null in Sprint 1 to keep payload size minimal.
                null   // CONFIRMED_DTM — same.
        );
    }
}
