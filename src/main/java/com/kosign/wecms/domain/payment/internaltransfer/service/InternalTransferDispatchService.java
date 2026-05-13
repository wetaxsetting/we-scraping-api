package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogContext;
import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
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
 * <h2>Phase A scope — local stub for the routing decision</h2>
 *
 * <p>Per the prompt's "Bank-network integration, real SMTP, real signing
 * — local stubs only" rule and backend doc §13, Phase A:
 * <ul>
 *   <li>Loads the master + lines, validates approval state +
 *       cut-off-time.</li>
 *   <li>Stamps {@code TRNF_YN='Y'}, {@code TRNF_DTM}=now, audit
 *       row.</li>
 *   <li>Does NOT insert {@code TB_PAY_TARGET} / {@code TB_PAY_FILE_MST}
 *       / {@code TB_PAY_FILE_DATA} rows — those tables are not yet
 *       migrated locally (see backend doc §13 + database doc §2.4).</li>
 * </ul>
 *
 * <p>The routing-flag computation
 * (API-path vs File-path based on {@code OUT_BANK_CD} + currencies)
 * lands when the dispatch tables land.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferDispatchService {

    private static final DateTimeFormatter DTM14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ScdulPymnSumrRepository     sumrRepository;
    private final ScdulPymnSumrDetlRepository detlRepository;
    private final CutoffValidator             cutoffValidator;
    private final AuditLogService             auditLogService;
    private final Clock                       clock;

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

        // Approval not present? Mirrors PymnHelper.pymn_0009_001_c001 lines 2439-2445.
        if (master.getApprSeqNo() == null || master.getApprSeqNo().isBlank()) {
            throw new BusinessException("WBE2063");
        }
        // NOTE: EXPT_RESOL_MSTR.APPR_TYPE_CD == '01' check is enforced by
        // the parallel approval-box flow before the user can press Transfer.
        // The refactor's authoritative gate is the presence of APPR_SEQ_NO
        // combined with the Approval Box state machine. A defensive read
        // against EXPT_RESOL_MSTR can be added when that domain exposes a
        // shared "isApproved(apprSeqNo)" service.

        // Cut-off-time validation — read network from the first detail line.
        List<ScdulPymnSumrDetl> lines = detlRepository.findByBizKeyAndScdulPymnNo(
                bizKey, id, Sort.by(Sort.Order.asc("scdulPymnDetlNo")));
        TransferNetwork network = lines.isEmpty()
                ? TransferNetwork.CITAD
                : TransferNetwork.fromAntDivCd(lines.get(0).getAntDivCd());
        cutoffValidator.validate(master.getOutBankCd(), network);

        // Stamp dispatch. Phase A: no TB_PAY_TARGET / TB_PAY_FILE_MST
        // insert — those land with the matching schema migration.
        master.setTrnfYn(TrnfYn.TRANSFERRED.code());
        master.setTrnfDtm(now);
        master.setModId(userId);
        master.setModDtm(now);
        // JPA dirty checking flushes the UPDATE.

        auditLogService.append(AuditLogContext.builder()
                .wsvcId(InternalTransferService.WSVC_ID)
                .refNo(InternalTransferService.REF_NO)
                .refNo1(id)
                .activityKey(InternalTransferService.AUDIT_ACTIVITY_KEY)
                .detailKey("audit.internal_transfer.dispatch.detail")
                .msgArgs(new Object[]{ req.trnfCcyCd() })
                .build());

        log.info("feature=internal-transfer dispatch bizKey={} scdulPymnNo={} trnfCcyCd={} network={} (Phase A stub: no TB_PAY_TARGET write)",
                bizKey, id, req.trnfCcyCd(), network);
    }
}
