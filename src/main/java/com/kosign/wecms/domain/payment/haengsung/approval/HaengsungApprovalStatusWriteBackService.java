package com.kosign.wecms.domain.payment.haengsung.approval;

import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatusChangedEvent;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.haengsung.oracle.HaengsungErpOracleRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnDtlsRepository;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * HAENGSUNG Oracle approval-status write-back ({@code haengsung-approval-status-writeback},
 * Step 4 — refactor of legacy {@code ErpStatusUpdateHelper}). When a WeCMS payment
 * batch's approval lifecycle is finalized, this writes the corresponding status
 * back to the Oracle ERP ({@code TB_TRNS_MAST.LAST_STATUS}, plus reject-time
 * {@code TB_TRNS_DETAIL} updates).
 *
 * <p><b>Single trigger.</b> This {@link #onApprovalStatusChanged} listener is the
 * ONE place that triggers Oracle approval synchronization. The approval workflow
 * is reused unchanged — its services merely publish a generic
 * {@link PaymentApprovalStatusChangedEvent}; no approval logic is duplicated.
 *
 * <p><b>Transaction model (no XA).</b> The listener runs {@code AFTER_COMMIT} of
 * the WeCMS approval transaction, so the approval is already durably committed
 * before Oracle is touched. Any Oracle failure is caught and logged — the
 * approval is NEVER rolled back, and the batch's Oracle status simply stays stale
 * until a later retry (the next status change re-writes it; a retry scheduler is
 * deliberately out of scope for Step 4).
 *
 * <p><b>Gating &amp; filtering.</b> The bean exists only when
 * {@code haengsung.oracle.enabled=true} — otherwise the event has no subscriber
 * (Oracle disabled → skipped). It additionally no-ops for non-HAENGSUNG tenants
 * and for batches not imported from ERP (blank {@code ERP_FILE_*}).
 *
 * <p>Mapping (WeCMS → Oracle {@code LAST_STATUS}): PENDING→10, APPROVED→20,
 * REJECTED→32, PROCESSING→51. The Oracle lookup is by the ERP reference imported
 * in Steps 2–3 ({@code ERP_FILE_DATE} + {@code ERP_FILE_NUM}; detail
 * {@code ERP_FILE_SEQ} on reject) — never by amount or account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "haengsung.oracle", name = "enabled", havingValue = "true")
public class HaengsungApprovalStatusWriteBackService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DTM_FMT  = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HaengsungErpOracleRepository oracle;
    private final ScdulPymnSumrRepository scdulSumrRepo;
    private final ScdulPymnSumrDetlRepository scdulDetlRepo;
    private final OvrsPymnSumrRepository ovrsSumrRepo;
    private final OvrsPymnDtlsRepository ovrsDetlRepo;
    private final BusiInfmRepository busiInfmRepository;

    /**
     * The single Oracle-sync trigger. Runs after the approval transaction commits
     * (no XA); swallows any Oracle failure so the committed approval is never
     * rolled back and a later status change / retry can re-sync.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApprovalStatusChanged(PaymentApprovalStatusChangedEvent event) {
        try {
            writeBack(event.bizKey(), event.apprSeqNo(), event.status());
        } catch (RuntimeException ex) {
            // Approval already committed (AFTER_COMMIT). Log + leave Oracle stale
            // for retry — never propagate (would not roll back, but keeps logs clean).
            log.warn("feature=haengsung-approval-writeback Oracle sync failed (approval kept) "
                    + "bizKey={} apprSeqNo={} status={}", event.bizKey(), event.apprSeqNo(), event.status(), ex);
        }
    }

    /**
     * Resolve the ERP batch keys from the WeCMS batch and write the mapped status
     * to Oracle. Package-visible for direct unit testing. May throw on Oracle
     * failure (the listener catches it).
     */
    public void writeBack(String bizKey, String apprSeqNo, PaymentApprovalStatus status) {
        Optional<ResolvedBatch> resolved = resolveBatch(bizKey, apprSeqNo);
        if (resolved.isEmpty()) {
            log.debug("feature=haengsung-approval-writeback skip — no batch for apprSeqNo={}", apprSeqNo);
            return;
        }
        ResolvedBatch batch = resolved.get();
        if (isBlank(batch.erpFileDate()) || isBlank(batch.erpFileNum())) {
            log.debug("feature=haengsung-approval-writeback skip — non-ERP batch apprSeqNo={}", apprSeqNo);
            return;
        }
        if (!isHaengsung(bizKey)) {
            log.debug("feature=haengsung-approval-writeback skip — non-HAENGSUNG tenant bizKey={}", bizKey);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String nowVn = now.format(DTM_FMT);
        String today = now.format(DATE_FMT);

        if (status == PaymentApprovalStatus.REJECTED) {
            oracle.updateMasterRejected(batch.erpFileDate(), batch.erpFileNum(), nowVn,
                    batch.totCnt(), batch.totAmt());
            for (BigDecimal seq : loadDetailErpSeqs(bizKey, batch)) {
                oracle.markDetailRejected(batch.erpFileDate(), batch.erpFileNum(), seq, nowVn, today);
            }
        } else {
            oracle.updateMasterApprovalStatus(batch.erpFileDate(), batch.erpFileNum(),
                    lastStatusCode(status), nowVn);
        }
        log.info("feature=haengsung-approval-writeback synced bizKey={} apprSeqNo={} status={} "
                        + "erpFileDate={} erpFileNum={}",
                bizKey, apprSeqNo, status, batch.erpFileDate(), batch.erpFileNum());
    }

    /** WeCMS status → Oracle {@code LAST_STATUS}. */
    static String lastStatusCode(PaymentApprovalStatus status) {
        return switch (status) {
            case PENDING    -> "10";
            case APPROVED   -> "20";
            case REJECTED   -> "32";
            case PROCESSING -> "51";
        };
    }

    /** Try SCDUL (Vendor/Expense/Payroll/Internal) then OVRS (Overseas). */
    private Optional<ResolvedBatch> resolveBatch(String bizKey, String apprSeqNo) {
        Optional<ScdulPymnSumr> scdul = scdulSumrRepo.findFirstByBizKeyAndApprSeqNo(bizKey, apprSeqNo);
        if (scdul.isPresent()) {
            ScdulPymnSumr s = scdul.get();
            return Optional.of(new ResolvedBatch(s.getErpFileDate(), s.getErpFileNum(),
                    s.getTotTrnsCnt(), s.getTotalTranAmt(), false, s.getScdulPymnNo()));
        }
        Optional<OvrsPymnSumr> ovrs = ovrsSumrRepo.findByBizKeyAndApprSeqNo(bizKey, apprSeqNo);
        if (ovrs.isPresent()) {
            OvrsPymnSumr o = ovrs.get();
            BigDecimal cnt = o.getTotTrnsCnt() == null ? null : BigDecimal.valueOf(o.getTotTrnsCnt());
            return Optional.of(new ResolvedBatch(o.getErpFileDate(), o.getErpFileNum(),
                    cnt, o.getTotalTranAmt(), true, o.getOvrsPymnNo()));
        }
        return Optional.empty();
    }

    /** Per-line ERP_FILE_SEQ values for the batch (used only on reject). */
    private List<BigDecimal> loadDetailErpSeqs(String bizKey, ResolvedBatch batch) {
        if (batch.overseas()) {
            return ovrsDetlRepo.findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(bizKey, batch.pymnNo())
                    .stream().map(OvrsPymnSumrDetl::getErpFileSeq).filter(java.util.Objects::nonNull).toList();
        }
        return scdulDetlRepo.findByBizKeyAndScdulPymnNo(bizKey, batch.pymnNo(), Sort.unsorted())
                .stream().map(ScdulPymnSumrDetl::getErpFileSeq).filter(java.util.Objects::nonNull).toList();
    }

    private boolean isHaengsung(String bizKey) {
        return busiInfmRepository.findById(bizKey)
                .filter(b -> "H".equals(b.getPrdtType()) && !"Y".equals(b.getDelYn()))
                .isPresent();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Resolved ERP keys + close-out totals for one batch. */
    private record ResolvedBatch(String erpFileDate, String erpFileNum,
                                 BigDecimal totCnt, BigDecimal totAmt,
                                 boolean overseas, String pymnNo) { }
}
