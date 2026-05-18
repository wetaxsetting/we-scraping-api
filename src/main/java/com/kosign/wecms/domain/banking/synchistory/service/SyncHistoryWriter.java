package com.kosign.wecms.domain.banking.synchistory.service;

import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Writes one {@code EXT_TRAN_CALL_HIS} row per per-account scrape
 * attempt. Mirrors legacy WeCMS, which logs every NXISAS call so the
 * Bank Transaction Sync &gt; History popup can list past attempts.
 *
 * <p>The refactor's {@code BankTransactionSyncService} (the orchestrator
 * that drives the LOGIN → TRANSACTION → BALANCE chain per account)
 * calls {@link #recordAttempt} from both its success and error
 * branches. Each call gets its own row keyed by the legacy
 * {@code (SVC_ID, REF_DATE, SEQ_NO)} PK with {@code SEQ_NO} allocated
 * via {@code MAX+1} per day (legacy {@code wb_get_next_val} parity).
 *
 * <p>Writes run in a {@code REQUIRES_NEW} transaction so a parent
 * rollback (the orchestrator throws on a Coocon-gateway error) never
 * swallows the audit row. Mirrors {@code LoginHistoryService.record}
 * which uses the same propagation for the same reason.
 *
 * <p>One-shot retry on {@code DataIntegrityViolationException} —
 * concurrent syncs racing for the same {@code SEQ_NO} on the same day
 * can collide. The retry re-reads {@code MAX(SEQ_NO)} so the winner
 * still gets a fresh value.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncHistoryWriter {

    /** Account-Inquiry sync's audit-log discriminator. The History popup
     *  filters on this exact value (legacy
     *  {@code fnsh_0001_003_r001.jsp}). */
    public static final String SVC_ID_REAL_TIME_ONLINE_INQUIRY = "REAL_TIME_ONLINE_INQUIRY";

    /** Legacy success code — anything else surfaces as "Failed" in the
     *  History popup. */
    public static final String RSLT_CD_SUCCESS = "00000000";

    private static final DateTimeFormatter DT_8  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DTM_14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ExtTranCallHisRepository repo;

    /**
     * Persist one audit row.
     *
     * @param bizKey   tenant of the calling user
     * @param userId   the {@code USER_INFM.USER_ID} that triggered the scrape
     * @param bankCd   legacy 20-char {@code BANK_CD}
     * @param acctNo   account number being scraped
     * @param fromDt   scrape window start ({@code yyyyMMdd})
     * @param toDt     scrape window end ({@code yyyyMMdd})
     * @param rsltCd   {@code "00000000"} on success, gateway error code on failure
     * @param errCont  free-text error from the gateway; empty string on success
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(
            String bizKey,
            String userId,
            String bankCd,
            String acctNo,
            String fromDt,
            String toDt,
            String rsltCd,
            String errCont) {

        try {
            insert(bizKey, userId, bankCd, acctNo, fromDt, toDt, rsltCd, errCont);
        } catch (DataIntegrityViolationException e) {
            // Concurrent sync allocated the same SEQ_NO. Retry once with
            // a fresh MAX lookup — the second attempt sees the winner's
            // row and picks the next slot.
            log.debug("EXT_TRAN_CALL_HIS SEQ_NO collision, retrying once [bizKey={}, bank={}, acct={}]",
                    bizKey, bankCd, acctNo);
            insert(bizKey, userId, bankCd, acctNo, fromDt, toDt, rsltCd, errCont);
        }
    }

    private void insert(
            String bizKey,
            String userId,
            String bankCd,
            String acctNo,
            String fromDt,
            String toDt,
            String rsltCd,
            String errCont) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String refDate = LocalDate.now(ZoneOffset.UTC).format(DT_8);
        String regDtm = now.format(DTM_14);

        BigDecimal nextSeq = repo
                .findMaxSeqNoForDay(SVC_ID_REAL_TIME_ONLINE_INQUIRY, refDate)
                .add(BigDecimal.ONE);

        ExtTranCallHis row = new ExtTranCallHis();
        row.setSvcId(SVC_ID_REAL_TIME_ONLINE_INQUIRY);
        row.setRefDate(refDate);
        row.setSeqNo(nextSeq);
        row.setWebSvcId(SVC_ID_REAL_TIME_ONLINE_INQUIRY);
        row.setRegDtm(regDtm);
        row.setRsltCd(rsltCd == null || rsltCd.isBlank() ? RSLT_CD_SUCCESS : rsltCd);
        row.setErrCont(errCont == null ? "" : errCont);
        row.setBizKey(bizKey);
        row.setUserId(userId);
        row.setBankCd(bankCd);
        row.setAcctNo(acctNo);
        row.setStartDate(fromDt);
        row.setEndDate(toDt);
        // TAXCODE only populated by tax-related scrapes (e.g. GDT_INQUIRY_*).
        // Bank scrapes always leave it null.

        repo.save(row);
    }
}
