package com.kosign.wecms.domain.payment.dispatch.recipient;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Production orchestrator for the depositor-name inquiry (WGVN_EFS_201) — the
 * refactor of legacy batch SVC {@code WB_00000100}'s {@code execute(...)} body.
 * Wraps the pure REST/crypto {@link RecipientLookupService} with the
 * {@code TB_FIRM_TRAN} audit lifecycle (C1) and {@code TRAN_SEQ} allocation.
 *
 * <p>Per-inquiry flow (mirrors {@code WB_00000100.java:80-237}):
 * <ol>
 *   <li>allocate a fresh {@code TRAN_SEQ} from the global {@code firm_seq_no}
 *       sequence — legacy {@code comm.Sequence.getSeq()} parity, funnelled via
 *       {@link LegacyFunctionRepository#nextTbFirmTranSeq()};</li>
 *   <li>insert the {@code TB_FIRM_TRAN} request row at {@code PROC_STAT='6'}
 *       ({@code TB_FIRM_TRAN_C001}, committed in its own unit);</li>
 *   <li>call {@link RecipientLookupService#lookup} (EFS 201 + response VV verify);</li>
 *   <li>apply the outcome to {@code TB_FIRM_TRAN} ({@code TB_FIRM_TRAN_U002}).</li>
 * </ol>
 *
 * <h2>Return contract + failure handling</h2>
 * Returns {@link Optional}{@code <String>} carrying the resolved owner name —
 * empty when the gateway resolves no name (RC=0000 + blank owner → the legacy
 * frontend's {@code WBE1143} branch) or on any gateway / infrastructure error.
 * Every failure is recorded as a committed {@code PROC_STAT='3'} (FAIL) audit row
 * and logged; nothing is rethrown, so callers (Excel-upload parsers, the Lookup
 * Recipient popup) degrade to "skip recipient verification" exactly like the
 * legacy {@code contactInfo == null} branch — never an error to the user.
 *
 * <p><b>{@code ant_div_cd}</b> is resolved by the gateway but is neither persisted
 * (legacy U002 omits it) nor returned (no current caller consumes it) — it is
 * logged only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositorInquiryService {

    private static final DateTimeFormatter DT8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TM6 = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DTTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LegacyFunctionRepository legacyFunctionRepository;
    private final FirmTranInquiryAuditService auditService;
    private final RecipientLookupService recipientLookupService;
    private final OpsAlertService opsAlertService;

    /**
     * Run one depositor inquiry for the given tenant + resolved firm-banking context.
     *
     * @param bizKey current tenant key (audit {@code BIZ_KEY} — not carried on the command)
     * @param cmd    the resolved WGVN_EFS_201 inquiry context
     * @return resolved owner name, or {@link Optional#empty()} when unresolved / on error
     */
    public Optional<String> inquire(String bizKey, RecipientLookupCommand cmd) {
        LocalDateTime now = LocalDateTime.now(ICT);
        String tranDt = now.format(DT8);
        String tranTm = now.format(TM6);
        String rqDttm = now.format(DTTM14);

        // 1+2. Allocate TRAN_SEQ (global firm_seq_no, legacy Sequence.getSeq() parity)
        //      and insert the C001 request row at PROC_STAT='6' in its own committed unit.
        final String tranSeq;
        try {
            tranSeq = legacyFunctionRepository.nextTbFirmTranSeq();
            auditService.recordRequest(tranDt, bizKey, tranSeq, tranTm, rqDttm, cmd);
        } catch (RuntimeException e) {
            log.warn("recipient-inquiry pre-call audit failed bizKey={} tarBankCd={} — skipping inquiry",
                    bizKey, cmd.tarBankCd(), e);
            return Optional.empty();
        }

        // 3+4. EFS 201, then map the outcome onto the audit row (U002).
        try {
            RecipientLookupResult result = recipientLookupService.lookup(cmd);
            String owner = result.ownerName();
            auditService.recordResult(tranDt, bizKey, tranSeq, ProcStat.SUCC.code(),
                    FirmBankingResultCd.SUCC, FirmBankingResultCd.MSG_SUCC, owner);
            // Ops alert (legacy WB_00000100:232 "예금주조회 API" — fires on every outcome).
            opsAlertService.notify(cmd.vanOrgCd(),
                    "예금주조회 API"
                            + "\nID : " + cmd.vanOrgCd()
                            + "\nTNO : " + tranSeq
                            + "\n[Result]"
                            + "\nRC : " + FirmBankingResultCd.SUCC
                            + "\nRM : " + FirmBankingResultCd.MSG_SUCC);
            if (!StringUtils.hasText(owner)) {
                // RC=0000 but empty owner — legacy frontend treats this as WBE1143
                // "Recipient Name not found"; the caller sees empty and skips.
                log.info("recipient-inquiry RC=0000 with blank owner — caller treats as not-found");
                return Optional.empty();
            }
            log.debug("recipient-inquiry ok antDivCd={}", result.antDivCd()); // ant_div_cd: log-only
            return Optional.of(owner.trim());
        } catch (BusinessException e) {
            // Gateway business error — non-0000 RC or VV-verify failure (legacy U002 FAIL branch).
            String rc = e.getArgs() != null && e.getArgs().length > 0 && e.getArgs()[0] != null
                    ? e.getArgs()[0].toString() : FirmBankingResultCd.ETC_ERR;
            auditService.recordResult(tranDt, bizKey, tranSeq, ProcStat.FAIL.code(),
                    rc, e.getCode(), null);
            opsAlertService.notify(cmd.vanOrgCd(),
                    "예금주조회 API"
                            + "\nID : " + cmd.vanOrgCd()
                            + "\nTNO : " + tranSeq
                            + "\n[Result]"
                            + "\nRC : " + rc
                            + "\nRM : " + (e.getCode() == null ? "" : e.getCode()));
            log.warn("recipient-inquiry gateway error rc={} bizKey={} tarBankCd={}",
                    rc, bizKey, cmd.tarBankCd());
            return Optional.empty();
        } catch (RuntimeException e) {
            // Infrastructure error — missing API key (B2, IllegalStateException) or bad
            // input (IllegalArgumentException). Record FAIL and degrade to empty.
            auditService.recordResult(tranDt, bizKey, tranSeq, ProcStat.FAIL.code(),
                    FirmBankingResultCd.ETC_ERR, FirmBankingResultCd.MSG_ETC_ERR, null);
            opsAlertService.notify(cmd.vanOrgCd(),
                    "예금주조회 API"
                            + "\nID : " + cmd.vanOrgCd()
                            + "\nTNO : " + tranSeq
                            + "\n[Result]"
                            + "\nRC : " + FirmBankingResultCd.ETC_ERR
                            + "\nRM : " + FirmBankingResultCd.MSG_ETC_ERR);
            log.warn("recipient-inquiry infrastructure error bizKey={} tarBankCd={} — {}",
                    bizKey, cmd.tarBankCd(), e.toString());
            return Optional.empty();
        }
    }
}
