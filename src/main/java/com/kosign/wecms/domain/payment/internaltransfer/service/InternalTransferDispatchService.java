package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.DispatchRequest;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Payment &gt; Internal Transfer — dispatch ("Transfer" / T-permission)
 * service. Mirrors legacy {@code pymn_0009_001_c001} including
 * {@link CutoffValidator} timing checks + the {@code TRNF_YN} stamp.
 *
 * <h2>Phase 1 dispatch (2026-05-28)</h2>
 *
 * <p>The dispatch flow writes the legacy {@code TB_PAY_*} rows via the
 * Internal-Transfer-specific {@link InternalTransferDispatchExecutor}
 * (G18/G19/G20 legacy-parity routing/currency/channel — the shared
 * {@code TransferDispatchExecutor} used by Vendor/Expense/Payroll is left
 * unchanged):
 * <ul>
 *   <li>Loads the master + lines, validates approval state +
 *       cut-off-time.</li>
 *   <li>Delegates to {@link InternalTransferDispatchExecutor#execute} which
 *       applies the legacy routing rule (API path when
 *       {@code (TRNF_CCY_CD=="VND" && !isShinhanUsdToVnd) || isWooriUsdToVnd},
 *       else file path):
 *       <ul>
 *         <li><b>VND → API path</b>: insert one {@code TB_PAY_TARGET}
 *             per detail line; stamp {@code PAY_TRAN_DT} +
 *             {@code PAY_TRAN_SEQ} on each detail.</li>
 *         <li><b>Non-VND → File path</b>: insert one
 *             {@code TB_PAY_FILE_MST} header + one
 *             {@code TB_PAY_FILE_DATA} per detail line; stamp
 *             {@code PAY_TRAN_DT} + {@code PAY_FILE_SEQ_NO} on the
 *             master and {@code PAY_TRAN_DT} +
 *             {@code PAY_TRAN_SEQ} (the file-data seq, as varchar)
 *             on each detail — legacy parity per PymnHelper:2347.</li>
 *       </ul></li>
 *   <li>Stamps {@code TRNF_YN='Y'}, {@code TRNF_DTM}=now on the master,
 *       writes the audit row.</li>
 * </ul>
 *
 * <p>The bank-network integration (NAPAS / CITAD / SWIFT callbacks
 * flipping {@code PROC_STAT} to '0' / '3') is handled by the live JEX
 * dispatch daemon — refactor R&D is local-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferDispatchService {

    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ScdulPymnSumrRepository     sumrRepository;
    private final ScdulPymnSumrDetlRepository detlRepository;
    private final CutoffValidator                 cutoffValidator;
    private final AuditLogService                 auditLogService;
    private final InternalTransferDispatchExecutor dispatchExecutor;
    private final ExptResolMstrRepository         exptResolMstrRepository;
    private final Clock                           clock;

    /**
     * Execute the dispatch flow. Mirrors
     * {@code pymn_0009_001_c001_act.jsp:96-105} +
     * {@code PymnHelper.pymn_0009_001_c001}.
     */
    @Transactional
    public void dispatch(String id, DispatchRequest req) {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException("tenant context not populated");
        }
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        ScdulPymnSumr master = sumrRepository
                .findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                        bizKey, id, ScdulPymnTypeCd.INTERNAL.code())
                .orElseThrow(() -> new BusinessException("WBE2060"));

        // Already dispatched?
        if (TrnfYn.TRANSFERRED.code().equals(master.getTrnfYn())) {
            throw new BusinessException("WBE2064");
        }

        // Approval not present? Mirrors PymnHelper.pymn_0009_001_c001:2497-2504.
        String apprSeqNo = master.getApprSeqNo();
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE2063");
        }
        // G1 (legacy parity — PymnHelper.pymn_0009_001_c001:2507-2516): a batch may be
        // dispatched ONLY when its approval is fully approved, i.e.
        // EXPT_RESOL_MSTR.APPR_TYPE_CD = '01'. Pending ('00'), rejected ('02'),
        // and partially-approved ('03') batches all carry an APPR_SEQ_NO but are NOT
        // approved. The prior refactor behaviour deferred this to the approval-box
        // flow / UI; a hand-crafted API call could bypass it, so the server-side
        // assertion is now enforced here to match legacy.
        String apprTypeCd = exptResolMstrRepository
                .findByBizKeyAndApprSeqNoIn(bizKey, java.util.List.of(apprSeqNo))
                .stream().findFirst()
                .map(ExptResolMstr::getApprTypeCd)
                .orElse(null);
        if (!"01".equals(apprTypeCd)) {
            throw new BusinessException("WBE2063");
        }

        // Cut-off-time validation — read network from the first detail line.
        List<ScdulPymnSumrDetl> lines = detlRepository.findByBizKeyAndScdulPymnNo(
                bizKey, id, ScdulPymnSumrDetlRepository.DETL_NO_NUMERIC_ASC);
        TransferNetwork network = lines.isEmpty()
                ? TransferNetwork.CITAD
                : TransferNetwork.fromAntDivCd(lines.get(0).getAntDivCd());

        // VCB-source Internal Transfer is exempt from the cut-off entirely
        // (works 24/7, any channel, any day) — legacy parity with
        // PymnHelper.validateTransferTiming:3225-3233, which returns early for
        // an INTERNAL-type payment whose source bank is VCB. This service only
        // ever handles ScdulPymnTypeCd.INTERNAL (see the master lookup above),
        // so gating on the VCB source bank alone is the exact legacy behaviour.
        // Vendor/Expense/Payroll/Overseas run through their own services and are
        // unaffected — they keep the normal VCB cut-off (incl. the ≥500M tier).
        if (cutoffValidator.isInternalTransferCutoffExempt(master.getOutBankCd())) {
            log.info("feature=internal-transfer cutoff bypass: VCB-source internal transfer is 24/7 "
                    + "(legacy PymnHelper.java:3225-3233) — bizKey={} scdulPymnNo={}", bizKey, id);
        } else {
            // CITAD-via-API (16:30) vs File-SFTP (15:30) hinges on the same
            // routing rule the dispatch executor applies; VND→VND drives the
            // Shinhan CITAD hard-block; ≥500M drives the VCB CITAD tier.
            boolean apiTransport = dispatchExecutor.usesApiPath(master, lines, req.trnfCcyCd());
            boolean vndToVnd = "VND".equalsIgnoreCase(master.getCcyCd())
                    && "VND".equalsIgnoreCase(master.getWdrlCcyCd());
            boolean hasLargeTxn = lines.stream().anyMatch(l -> l.getTranAmt() != null
                    && l.getTranAmt().compareTo(cutoffValidator.largeTxnThreshold()) >= 0);
            cutoffValidator.validate(new CutoffCheck(
                    master.getOutBankCd(), network, apiTransport, vndToVnd, hasLargeTxn));
        }

        // Insert dispatch rows (TB_PAY_TARGET for the API path, TB_PAY_FILE_MST
        // + TB_PAY_FILE_DATA for the file path). Uses the Internal-Transfer-
        // specific executor (G18/G19/G20 legacy-parity routing/currency/channel)
        // so Vendor/Expense/Payroll — which still use the shared
        // TransferDispatchExecutor — are unaffected.
        dispatchExecutor.execute(master, lines, req.trnfCcyCd());

        // Stamp the master row. Dirty checking flushes the UPDATE
        // together with the executor's inserts inside this @Transactional.
        master.setTrnfYn(TrnfYn.TRANSFERRED.code());
        master.setTrnfDtm(now);
        master.setModId(userId);
        master.setModDtm(now);

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(InternalTransferService.WSVC_ID)
                .refNo(InternalTransferService.REF_NO)
                .refNo1(id)
                .activityKey(InternalTransferService.AUDIT_ACTIVITY_KEY)
                .detailKey("audit.internal_transfer.dispatch.detail")
                .msgArgs(new Object[]{ req.trnfCcyCd() })
                .build());

        log.info("feature=internal-transfer dispatch bizKey={} scdulPymnNo={} trnfCcyCd={} network={} lines={}",
                bizKey, id, req.trnfCcyCd(), network, lines.size());
    }
}
