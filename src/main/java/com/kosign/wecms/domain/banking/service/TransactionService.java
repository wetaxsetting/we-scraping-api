package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.InoutDv;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.TransactionConfirmRequest;
import com.kosign.wecms.domain.banking.dto.TransactionContactRequest;
import com.kosign.wecms.domain.banking.dto.TransactionDetailResponse;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionPurposeRequest;
import com.kosign.wecms.domain.banking.dto.TransactionReportingAccountRequest;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import com.kosign.wecms.domain.banking.dto.TransactionSummaryResponse;
import com.kosign.wecms.domain.banking.dto.TransactionVasAccountRequest;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.AcctTrnsRcvPayInfmRepository;
import com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount;
import com.kosign.wecms.domain.setting.chartofaccounts.repository.ChartOfAccountRepository;
import com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm;
import com.kosign.wecms.domain.setting.contacts.repository.TrcoInfmRepository;
import com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSet;
import com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository;
import com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm;
import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final String CODE_CHART_ACCT_NOT_FOUND =
            "WBE_TRANSACTION_CHART_ACCOUNT_NOT_FOUND";
    private static final String CODE_CHART_ACCT_CODE_MISMATCH =
            "WBE_TRANSACTION_CHART_ACCOUNT_CODE_MISMATCH";
    private static final String CODE_PURPOSE_NOT_FOUND =
            "WBE_TRANSACTION_PURPOSE_NOT_FOUND";
    private static final String CODE_VAS_NOT_FOUND =
            "WBE_TRANSACTION_VAS_ACCOUNT_NOT_FOUND";
    private static final String CODE_CONTACT_NOT_FOUND =
            "WBE_TRANSACTION_CONTACT_NOT_FOUND";

    private static final DateTimeFormatter DTM_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AcctTrnsHstrRepository txnRepo;
    private final AcctTrnsRcvPayInfmRepository rcvPayRepo;
    private final ChartOfAccountRepository chartAcctRepo;
    private final PurposeSettingsRepository purposeRepo;
    private final BusiVasAcctInfmRepository vasAcctRepo;
    private final TrcoInfmRepository contactRepo;

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

        RcvPayClassification classification = rcvPayClassificationBySeq(r.bizKey, rows);

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
                .map(row -> toRow(row,
                        confirmedSeqs.contains(row.getAcctSeqNo()),
                        classification.purposeByAcctSeqNo().get(row.getAcctSeqNo()),
                        classification.vasAcctCdByAcctSeqNo().get(row.getAcctSeqNo()),
                        classification.trcoKeyByAcctSeqNo().get(row.getAcctSeqNo()),
                        classification.trcoNmByAcctSeqNo().get(row.getAcctSeqNo())))
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

    /**
     * Assign / change the Reporting Account (chart of accounts) on one
     * bank transaction. Writes {@code CHART_ACCT_CD} +
     * {@code CHART_ACCT_SEQ_NO} directly on {@code ACCT_TRNS_HSTR} —
     * mirrors legacy {@code fnsh_0200_001_view.jsp} line 327 where the
     * picker cell reads those two columns off the transaction row.
     *
     * <p>Validates that the {@code (chart_acct_seq_no, chart_acct_cd)}
     * pair refers to an active (non-deleted) row in
     * {@code CHART_ACCT_INFM} for the current tenant. Mismatched code +
     * seq combinations are rejected so a stale frontend cannot drift
     * the two halves apart.
     *
     * <p>No audit-log row + no {@code MOD_DTM} bump on
     * {@code ACCT_TRNS_RCV_PAY_INFM} — Reporting Account is an
     * orthogonal classification, not part of the matching panel.
     */
    @Transactional
    public TransactionDetailResponse updateReportingAccount(
            String acctSeqNo, String trnsSeqNo,
            TransactionReportingAccountRequest request) {
        Resolved r = resolve(acctSeqNo);

        AcctTrnsHstr row = txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        // Validate the picked chart-of-accounts row exists, is active,
        // and the (seq_no, code) pair matches a single row. The
        // UK(BIZ_KEY, CHART_ACCT_CD, DEL_YN='N') makes the lookup O(1).
        ChartOfAccount chart = chartAcctRepo
                .findByBizKeyAndChartAcctCdAndDelYn(r.bizKey, request.chartAcctCd(), "N")
                .orElseThrow(() -> new BusinessException(CODE_CHART_ACCT_NOT_FOUND));
        if (!chart.getChartAcctSeqNo().equals(request.chartAcctSeqNo())) {
            throw new BusinessException(CODE_CHART_ACCT_CODE_MISMATCH);
        }
        if (!"A".equals(chart.getStts())) {
            throw new BusinessException(CODE_CHART_ACCT_NOT_FOUND);
        }

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        row.setChartAcctCd(request.chartAcctCd());
        row.setChartAcctSeqNo(request.chartAcctSeqNo());
        row.setModId(userId);
        row.setModDtm(nowDtm);
        txnRepo.save(row);

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Clear the Reporting Account on one bank transaction — sets both
     * {@code CHART_ACCT_CD} and {@code CHART_ACCT_SEQ_NO} to null on
     * {@code ACCT_TRNS_HSTR}. Idempotent: clearing an already-empty
     * row is a no-op (no error, refreshed detail returned).
     */
    @Transactional
    public TransactionDetailResponse clearReportingAccount(String acctSeqNo, String trnsSeqNo) {
        Resolved r = resolve(acctSeqNo);

        AcctTrnsHstr row = txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        if (row.getChartAcctCd() != null || row.getChartAcctSeqNo() != null) {
            String userId = TenantContext.currentUserId();
            String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
            row.setChartAcctCd(null);
            row.setChartAcctSeqNo(null);
            row.setModId(userId);
            row.setModDtm(nowDtm);
            txnRepo.save(row);
        }

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Assign / change the Purpose ({@code USE_USAG_CD}) on one bank
     * transaction. Writes {@code RCV_PAY_USAG_CD} on the matching
     * {@code ACCT_TRNS_RCV_PAY_INFM} row — creating one when the
     * transaction has no receipt/payment record yet. Mirrors the
     * Reporting Account precedent + legacy {@code fnsh_0200_001_c001}
     * write path which persists USE_USAG_CD alongside the matching
     * cluster.
     *
     * <p>Validates the picker's choice exists in {@code BUSI_USAG_SET_INFM}
     * for the current tenant and is active ({@code USE_YN='Y'}) before
     * the write commits.
     */
    @Transactional
    public TransactionDetailResponse updatePurpose(
            String acctSeqNo, String trnsSeqNo, TransactionPurposeRequest request) {
        Resolved r = resolve(acctSeqNo);

        AcctTrnsHstr row = txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        BusiUsagSet purpose = purposeRepo
                .findByBizKeyAndUseUsagCd(r.bizKey, request.useUsagCd())
                .orElseThrow(() -> new BusinessException(CODE_PURPOSE_NOT_FOUND));
        if (!"Y".equals(purpose.getUseYn())) {
            throw new BusinessException(CODE_PURPOSE_NOT_FOUND);
        }

        AcctTrnsRcvPayInfm entry = ensureRcvPayRow(r, trnsSeqNo, row);

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        entry.setRcvPayUsagCd(request.useUsagCd());
        entry.setModId(userId);
        entry.setModDtm(nowDtm);
        rcvPayRepo.save(entry);

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Clear the Purpose on one bank transaction — sets
     * {@code RCV_PAY_USAG_CD} to null on every receipt/payment row tied
     * to the transaction. Idempotent: clearing an already-empty record
     * is a no-op.
     */
    @Transactional
    public TransactionDetailResponse clearPurpose(String acctSeqNo, String trnsSeqNo) {
        Resolved r = resolve(acctSeqNo);

        // Ensure the transaction belongs to the tenant before mutating.
        txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        for (AcctTrnsRcvPayInfm rp : rcvPayRepo.findByBizKeyAndAcctSeqNo(r.bizKey, trnsSeqNo)) {
            if (rp.getRcvPayUsagCd() != null) {
                rp.setRcvPayUsagCd(null);
                rp.setModId(userId);
                rp.setModDtm(nowDtm);
                rcvPayRepo.save(rp);
            }
        }

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Assign / change the VAS Account ({@code VAS_ACCT_CD}) on one bank
     * transaction. Writes {@code VAS_ACCT_CD} on the matching
     * {@code ACCT_TRNS_RCV_PAY_INFM} row — creating one when the
     * transaction has no receipt/payment record yet. Validates the
     * picker's choice exists in {@code BUSI_VAS_ACCT_INFM} for the
     * current tenant and is active ({@code STTS='A'}, {@code DEL_YN='N'}).
     */
    @Transactional
    public TransactionDetailResponse updateVasAccount(
            String acctSeqNo, String trnsSeqNo, TransactionVasAccountRequest request) {
        Resolved r = resolve(acctSeqNo);

        AcctTrnsHstr row = txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        BusiVasAcctInfm vas = vasAcctRepo
                .findByBizKeyAndVasAcctCdAndDelYn(r.bizKey, request.vasAcctCd(), "N")
                .orElseThrow(() -> new BusinessException(CODE_VAS_NOT_FOUND));
        if (!"A".equals(vas.getStts())) {
            throw new BusinessException(CODE_VAS_NOT_FOUND);
        }

        AcctTrnsRcvPayInfm entry = ensureRcvPayRow(r, trnsSeqNo, row);

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        entry.setVasAcctCd(request.vasAcctCd());
        entry.setModId(userId);
        entry.setModDtm(nowDtm);
        rcvPayRepo.save(entry);

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Clear the VAS Account on one bank transaction — sets
     * {@code VAS_ACCT_CD} to null on every receipt/payment row tied to
     * the transaction. Idempotent.
     */
    @Transactional
    public TransactionDetailResponse clearVasAccount(String acctSeqNo, String trnsSeqNo) {
        Resolved r = resolve(acctSeqNo);

        txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        for (AcctTrnsRcvPayInfm rp : rcvPayRepo.findByBizKeyAndAcctSeqNo(r.bizKey, trnsSeqNo)) {
            if (rp.getVasAcctCd() != null) {
                rp.setVasAcctCd(null);
                rp.setModId(userId);
                rp.setModDtm(nowDtm);
                rcvPayRepo.save(rp);
            }
        }

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Assign / change the Contact ({@code RCV_PAY_TRCO_KEY} +
     * {@code RCV_PAY_TRCO_NM}) on one bank transaction. Mirrors legacy
     * {@code fnsh_0200_001_c001} which stamps both the key and the
     * name at pick time so the audit row preserves "what the contact
     * was called when the user matched it" even if the master is
     * renamed later. Validates the contact exists, is non-trashed
     * ({@code TRCO_STTS != 'C'}), and belongs to the current tenant.
     */
    @Transactional
    public TransactionDetailResponse updateContact(
            String acctSeqNo, String trnsSeqNo, TransactionContactRequest request) {
        Resolved r = resolve(acctSeqNo);

        AcctTrnsHstr row = txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        TrcoInfm contact = contactRepo
                .findByTrcoKeyAndBizKey(request.trcoKey(), r.bizKey)
                .orElseThrow(() -> new BusinessException(CODE_CONTACT_NOT_FOUND));
        if ("C".equals(contact.getTrcoStts())) {
            throw new BusinessException(CODE_CONTACT_NOT_FOUND);
        }

        AcctTrnsRcvPayInfm entry = ensureRcvPayRow(r, trnsSeqNo, row);

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        entry.setRcvPayTrcoKey(contact.getTrcoKey());
        entry.setRcvPayTrcoNm(contact.getTrcoCmnm());
        entry.setModId(userId);
        entry.setModDtm(nowDtm);
        rcvPayRepo.save(entry);

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Clear the Contact on one bank transaction — sets the snapshot
     * pair ({@code RCV_PAY_TRCO_KEY} + {@code RCV_PAY_TRCO_NM}) to
     * null on every receipt/payment row tied to the transaction.
     * Idempotent.
     */
    @Transactional
    public TransactionDetailResponse clearContact(String acctSeqNo, String trnsSeqNo) {
        Resolved r = resolve(acctSeqNo);

        txnRepo.findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
                        r.bizKey, r.bankCd, r.acctNo)
                .stream()
                .filter(h -> trnsSeqNo.equals(h.getAcctSeqNo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND));

        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        for (AcctTrnsRcvPayInfm rp : rcvPayRepo.findByBizKeyAndAcctSeqNo(r.bizKey, trnsSeqNo)) {
            if (rp.getRcvPayTrcoKey() != null || rp.getRcvPayTrcoNm() != null) {
                rp.setRcvPayTrcoKey(null);
                rp.setRcvPayTrcoNm(null);
                rp.setModId(userId);
                rp.setModDtm(nowDtm);
                rcvPayRepo.save(rp);
            }
        }

        return detail(acctSeqNo, trnsSeqNo);
    }

    /**
     * Returns the (single) {@code ACCT_TRNS_RCV_PAY_INFM} row for the
     * transaction, or creates a fresh row when the matching cluster
     * has never been touched. Used by Purpose / Reporting-Account-style
     * mutations that need an underlying record to attach to without
     * the user having clicked Confirm first.
     */
    private AcctTrnsRcvPayInfm ensureRcvPayRow(
            Resolved r, String trnsSeqNo, AcctTrnsHstr hstr) {
        List<AcctTrnsRcvPayInfm> existing =
                rcvPayRepo.findByBizKeyAndAcctSeqNo(r.bizKey, trnsSeqNo);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_FMT);
        AcctTrnsRcvPayInfm fresh = new AcctTrnsRcvPayInfm();
        fresh.setBizKey(r.bizKey);
        fresh.setAcctSeqNo(trnsSeqNo);
        fresh.setRcvPaySeqNo(nextRcvPaySeqNo(r.bizKey, trnsSeqNo));
        fresh.setBankCd(r.bankCd);
        fresh.setAcctNo(r.acctNo);
        fresh.setTrscDt(hstr.getTrscDt());
        fresh.setRcvPayAmt(hstr.getTrscAmt());
        fresh.setCnfmYn("N");
        fresh.setRegId(userId);
        fresh.setRegDtm(nowDtm);
        return fresh;
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

    /**
     * Map each {@code ACCT_SEQ_NO} → the picked Purpose
     * ({@code RCV_PAY_USAG_CD}) and VAS Account ({@code VAS_ACCT_CD})
     * from {@code ACCT_TRNS_RCV_PAY_INFM}. The Purpose and VAS columns
     * on the list page read these without a per-row detail fetch.
     * Multiple RCV_PAY rows per transaction collapse to the most
     * recently modified entry so the pickers reflect the canonical pick.
     */
    private RcvPayClassification rcvPayClassificationBySeq(
            String bizKey, List<AcctTrnsHstr> rows) {
        if (rows.isEmpty()) {
            return new RcvPayClassification(Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<String> seqs = rows.stream().map(AcctTrnsHstr::getAcctSeqNo).toList();
        Map<String, String> purposes = new HashMap<>();
        Map<String, String> vas = new HashMap<>();
        Map<String, String> trcoKeys = new HashMap<>();
        Map<String, String> trcoNms = new HashMap<>();
        Map<String, String> modDtmByAcct = new HashMap<>();
        for (AcctTrnsRcvPayInfm rp : rcvPayRepo.findByBizKeyAndAcctSeqNoIn(bizKey, seqs)) {
            String key = rp.getAcctSeqNo();
            String cur = rp.getModDtm() != null ? rp.getModDtm() : rp.getRegDtm();
            String prev = modDtmByAcct.get(key);
            if (prev != null && (cur == null || cur.compareTo(prev) < 0)) continue;
            modDtmByAcct.put(key, cur);
            String purpose = rp.getRcvPayUsagCd();
            if (purpose != null && !purpose.isBlank()) purposes.put(key, purpose);
            else purposes.remove(key);
            String vasCd = rp.getVasAcctCd();
            if (vasCd != null && !vasCd.isBlank()) vas.put(key, vasCd);
            else vas.remove(key);
            String trcoKey = rp.getRcvPayTrcoKey();
            if (trcoKey != null && !trcoKey.isBlank()) trcoKeys.put(key, trcoKey);
            else trcoKeys.remove(key);
            String trcoNm = rp.getRcvPayTrcoNm();
            if (trcoNm != null && !trcoNm.isBlank()) trcoNms.put(key, trcoNm);
            else trcoNms.remove(key);
        }
        return new RcvPayClassification(purposes, vas, trcoKeys, trcoNms);
    }

    private record RcvPayClassification(
            Map<String, String> purposeByAcctSeqNo,
            Map<String, String> vasAcctCdByAcctSeqNo,
            Map<String, String> trcoKeyByAcctSeqNo,
            Map<String, String> trcoNmByAcctSeqNo) { }

    private TransactionRow toRow(AcctTrnsHstr r, boolean confirmed,
                                  String useUsagCd, String vasAcctCd,
                                  String rcvPayTrcoKey, String rcvPayTrcoNm) {
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
                null,
                "Y".equals(r.getLinkMatchYn()) ? "Y" : "N",
                r.getChartAcctCd(),
                r.getChartAcctSeqNo(),
                useUsagCd,
                vasAcctCd,
                rcvPayTrcoKey,
                rcvPayTrcoNm
        );
    }

    private record Resolved(String bizKey, String bankCd, String acctNo) { }
}
