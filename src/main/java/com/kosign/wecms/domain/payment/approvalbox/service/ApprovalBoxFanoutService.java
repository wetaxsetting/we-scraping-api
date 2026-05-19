package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Shared writer that fans Request-Approval submissions out into the
 * {@code EXPT_RESOL_MSTR} + {@code EXPT_RESOL_APPRVR_INFM} tables the
 * Approval Box list reads from.
 *
 * <p>Without this fanout the Payment Scheduled tables ({@code SCDUL_PYMN_SUMR})
 * carry the stamped {@code APPR_SEQ_NO} but no row appears in the Approval
 * Box query (legacy {@code resl_0001_001_r001_act.jsp:78}) — that query
 * is keyed on {@code EXPT_RESOL_MSTR.APPR_DV IN ('21','31','32','33','34','35')}
 * and joins per-approver rows for the "Approver (X/Y)" chip.
 *
 * <p>Single writer reused by all four Payment Scheduled services
 * (Internal Transfer / Vendor / Expense / Payroll). Each module supplies
 * its own {@link ApprDv} discriminator + the master document fields;
 * everything else (per-approver fan-out, status flags, audit columns)
 * is uniform across modules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalBoxFanoutService {

    /** Initial master state — Pending. Flips via the approve / reject
     *  actions (see {@link ApprovalBoxService#applyApprove}). */
    private static final String APPR_TYPE_CD_PENDING = ApprTypeCd.PENDING;

    /** First approver is PENDING, others WAITING. The approve action
     *  cascades WAITING → PENDING when the prior approver acts. */
    private static final String APPRVR_STTS_PENDING = ApprvrStts.PENDING;
    private static final String APPRVR_STTS_WAITING = ApprvrStts.WAITING;

    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME6 = DateTimeFormatter.ofPattern("HHmmss");

    private final ExptResolMstrRepository       mstrRepository;
    private final ExptResolApprvrInfmRepository approverRepository;
    private final Clock                         clock;

    /**
     * Persist the master + per-approver rows for one Request Approval
     * submission. Idempotent at the (BIZ_KEY, APPR_SEQ_NO) PK level —
     * a re-submit collides and aborts the parent transaction, which is
     * the correct behaviour (the legacy contract is "one APPR_SEQ_NO,
     * one approval workflow").
     *
     * @param apprDv      Module discriminator (Internal Transfer / Vendor / …).
     * @param apprSeqNo   Already-allocated sequence (same value stamped on
     *                    {@code SCDUL_PYMN_SUMR.APPR_SEQ_NO}).
     * @param titl        Approval title (the user's "Post Title" input).
     * @param docNo       Document number — the Payment-module batch id.
     * @param totalVndAmt Total VND amount (mapped onto the legacy
     *                    {@code TOT_PAY_VND_AMT} column).
     * @param payDate     Optional scheduled-payment date ({@code yyyyMMdd});
     *                    null / blank stays null on the row.
     * @param approvers   Ordered approver chain. First entry is the active
     *                    approver (status {@code 'P'}); the rest start as
     *                    {@code 'W'} (waiting).
     */
    @Transactional
    public void fanout(
            ApprDv apprDv,
            String apprSeqNo,
            String titl,
            String docNo,
            BigDecimal totalVndAmt,
            String payDate,
            List<Approver> approvers) {

        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        String nowDtm = now.format(DTM14);
        String today  = now.format(DATE8);
        String time   = now.format(TIME6);

        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        if (userNm == null) userNm = userId;

        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setBizKey(bizKey);
        mstr.setApprSeqNo(apprSeqNo);
        mstr.setApprDv(apprDv.code());
        mstr.setApprSts("0");
        mstr.setDraftDate(today);
        mstr.setDraftTime(time);
        mstr.setDraftUserId(userId);
        mstr.setDraftUserNm(userNm);
        mstr.setDraftUserSiteCd("");
        mstr.setDraftUserSiteNm("");
        mstr.setTitl(titl != null ? titl : "");
        mstr.setDocNo(docNo != null ? docNo : "");
        mstr.setDocNm(apprDv.slug());
        mstr.setTotPayVndAmt(totalVndAmt);
        // Approval Box list projection reads totPayKhrAmt — per the
        // entity Javadoc, the legacy KHR column carries the VND amount
        // for VN tenants. Stamp both so the wire amount displays.
        mstr.setTotPayKhrAmt(totalVndAmt);
        mstr.setPayDate(payDate != null && !payDate.isBlank() ? payDate : null);
        mstr.setDelYn("N");
        mstr.setPayEndYn("N");
        // Legacy print-form flags — all NOT NULL with no DB DEFAULT. The
        // refactor doesn't render the legacy print form, but the columns
        // must be populated so the INSERT doesn't violate the constraint.
        // Values mirror the legacy DEV defaults ('Y'/'N' from V109 schema).
        mstr.setHandRegYn("Y");
        mstr.setApprBoxUseYn("N");
        mstr.setApprDatDispYn("N");
        mstr.setCmpnLogoDispYn("N");
        mstr.setRegDispYn("N");
        mstr.setApprTypeCd(APPR_TYPE_CD_PENDING);
        mstr.setRegId(userId);
        mstr.setRegDtm(nowDtm);
        mstr.setModId(userId);
        mstr.setModDtm(nowDtm);
        mstrRepository.save(mstr);

        int line = 1;
        for (Approver a : approvers) {
            ExptResolApprvrInfm row = new ExptResolApprvrInfm();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setApprvrId(a.apprvrId());
            row.setApprvrNm(a.apprvrNm() != null ? a.apprvrNm() : a.apprvrId());
            row.setApprvrStts(line == 1 ? APPRVR_STTS_PENDING : APPRVR_STTS_WAITING);
            row.setApprvrLineNo(BigDecimal.valueOf(line));
            row.setReadYn("N");
            row.setRegId(userId);
            row.setRegDtm(nowDtm);
            row.setModId(userId);
            row.setModDtm(nowDtm);
            approverRepository.save(row);
            line++;
        }

        log.info("approval-box fanout bizKey={} apprSeqNo={} apprDv={} approvers={}",
                bizKey, apprSeqNo, apprDv.code(), approvers.size());
    }

    /** Approver-row input — name + id pair from the request body. */
    public record Approver(String apprvrId, String apprvrNm) { }
}
