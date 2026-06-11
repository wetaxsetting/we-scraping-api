package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.notification.service.NotificationService;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprovalLineApprover;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    /** {@code yyMMdd} segment of the legacy {@code VO_YYMMDD_NNN} doc-no
     *  format — Vietnam local timezone so the daily counter resets at
     *  the WeCMS business-day boundary, not UTC midnight. */
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    /** WeCMS operates in VN. */
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ExptResolMstrRepository       mstrRepository;
    private final ExptResolApprvrInfmRepository approverRepository;
    private final ApprovalLineApproverRepository apprLineApproverRepository;
    private final Clock                         clock;
    private final NotificationService           notificationService;

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
     * @param totalAmt    Total amount of the batch, in the currency
     *                    given by {@code ccyCd}. The fanout writes it
     *                    into the legacy column matching the currency
     *                    so {@link ApprovalBoxService} reads it back.
     * @param ccyCd       Currency code of {@code totalAmt} (e.g.
     *                    {@code "VND"}, {@code "USD"}, {@code "KRW"}).
     *                    {@code "USD"} goes to {@code TOT_PAY_USD_AMT};
     *                    every other currency goes to
     *                    {@code TOT_PAY_KHR_AMT} (VN-tenant legacy
     *                    alias that doubles for VND + KRW).
     * @param payDate     Optional scheduled-payment date ({@code yyyyMMdd});
     *                    null / blank stays null on the row.
     * @param cont        Optional free-form approval content / requestor
     *                    note (the textarea in the Request Approval
     *                    dialog). Renders in the read-only Approval
     *                    Details popup's "Approval Content" section.
     *                    Null / blank stays null on the row.
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
            BigDecimal totalAmt,
            String ccyCd,
            String payDate,
            String cont,
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
        // Override caller-supplied docNo with the canonical
        // {@code VO_YYMMDD_NNN} format. Every Request Approval row in
        // every Payment module (Internal Transfer / Vendor / Expense /
        // Payroll / Corporate Card / Overseas) shares the same
        // per-tenant per-day sequence so the Approval Box's Document
        // No. column reads uniformly across all submissions.
        mstr.setDocNo(nextDocNo(bizKey, now));
        mstr.setDocNm(apprDv.slug());
        // Surface the user's "Approval Content" textarea on the
        // read-only Approval Details popup. Blank input stays null
        // on the row so the popup hides the section.
        mstr.setCont(cont != null && !cont.isBlank() ? cont.trim() : null);
        // Stamp the matching legacy amount column so the Approval Box
        // list query — which picks USD from TOT_PAY_USD_AMT and
        // everything else from TOT_PAY_KHR_AMT — surfaces the value.
        // Also stamp TOT_PAY_VND_AMT for VND batches to keep the
        // legacy semantic field populated alongside the KHR alias.
        if ("USD".equalsIgnoreCase(ccyCd)) {
            mstr.setTotPayUsdAmt(totalAmt);
        } else {
            mstr.setTotPayKhrAmt(totalAmt);
            if ("VND".equalsIgnoreCase(ccyCd) || ccyCd == null || ccyCd.isBlank()) {
                mstr.setTotPayVndAmt(totalAmt);
            }
        }
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

        // Phase-1 notification producer: fan an approval-request notification
        // out to the approver chain. Card Transactions submits route through this
        // shared writer. Best-effort + isolated (REQUIRES_NEW) so a notification
        // failure never rolls back this approval submission.
        notificationService.notifyApprovalRequested(
                apprDv.slug(), apprSeqNo, mstr.getDocNo(), titl,
                approvers.stream()
                        .map(a -> new NotificationService.Recipient(a.apprvrId(), a.apprvrNm()))
                        .toList());
    }

    /**
     * Allocate the next {@code VO_YYMMDD_NNN} doc-no for the given
     * tenant. Per-tenant per-day counter starting at {@code 001}. Used
     * by every Payment module's Request Approval path so the Approval
     * Box "Document No." column reads uniformly across modules.
     *
     * <p>Date segment uses Vietnam-local time so the counter resets at
     * the WeCMS business-day boundary. Pads to 3 digits.
     */
    public String nextDocNo(String bizKey, OffsetDateTime now) {
        String yymmdd = now.atZoneSameInstant(VIETNAM_ZONE).toLocalDate().format(YYMMDD);
        String prefix = "VO_" + yymmdd + "_";
        Integer max = mstrRepository.findMaxDocNoSeqByBizKeyAndPrefix(bizKey, prefix);
        int next = (max == null ? 0 : max) + 1;
        if (next > 999) {
            // 3-digit cap is fine in practice — DEV's busiest day in 6
            // years tops 5 submissions; growing the format requires a
            // coordinated frontend + downstream-system change.
            throw new IllegalStateException(
                    "doc-no daily counter exhausted for " + bizKey + " on " + yymmdd);
        }
        return prefix + String.format("%03d", next);
    }

    /** Approver-row input — name + id pair from the request body. */
    public record Approver(String apprvrId, String apprvrNm) { }

    /* ----------------------------------------------------------------
     *  Shared withdraw / change-approval-line — used by every Payment
     *  module's Approval Details kebab menu (Card Transactions,
     *  Internal Transfer, Vendor / Expense / Payroll / Overseas as they
     *  port). Both touch only EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM,
     *  so the logic is module-agnostic. Each caller wraps these in its
     *  own service-level transaction so the per-module back-stamp
     *  cleanup (e.g. clearing SCDUL_PYMN_SUMR.APPR_SEQ_NO for Internal
     *  Transfer) lands in the same atomic unit.
     * ---------------------------------------------------------------- */

    /**
     * Withdraw an in-flight Approval Box request. Refuses unless the
     * master is in Pending ({@code "00"}) or In Progress ({@code "03"})
     * — closed approvals (Approved / Rejected) stay immutable.
     *
     * <p>Returns the loaded master so the caller can read whatever
     * audit fields it needs (e.g. the back-stamped {@code DOC_NO}) AFTER
     * the master + approver rows have been cleared from the DB. The
     * tx is the caller's — this method assumes it is invoked from
     * inside a {@code @Transactional} per-module service method.
     *
     * <p>Per-module back-stamp cleanup (Card unstamps
     * {@code CARD_TRNS_HSTR.APPR_SEQ_NO}; Internal Transfer clears
     * {@code SCDUL_PYMN_SUMR.APPR_SEQ_NO} + {@code ERP_FILE_NUM}) is the
     * caller's responsibility — this method only touches the approval
     * tables. Audit-log writes are also the caller's responsibility so
     * each module stamps its own {@code WSVC_ID} slug.
     *
     * @param apprSeqNo The approval sequence number to withdraw.
     * @return the loaded {@link ExptResolMstr} BEFORE deletion, for the
     *         caller's read needs. Never {@code null}.
     * @throws BusinessException {@code WBE_NOT_FOUND} when the row is
     *         missing in the current tenant; {@code WBE_VALIDATION}
     *         when the row is already in a closed state or
     *         {@code apprSeqNo} is blank.
     */
    public ExptResolMstr withdrawApproval(String apprSeqNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "appr_seq_no: required");
        }
        String bizKey = TenantContext.currentBizKey();

        ExptResolMstrId id = new ExptResolMstrId(bizKey, apprSeqNo.trim());
        ExptResolMstr mstr = mstrRepository.findById(id)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "Approval reference not found"));

        String typeCd = mstr.getApprTypeCd();
        if (!"00".equals(typeCd) && !"03".equals(typeCd)) {
            throw new BusinessException("WBE_VALIDATION",
                    "Only Pending or In Progress approvals can be withdrawn");
        }

        approverRepository.deleteByBizKeyAndApprSeqNo(bizKey, apprSeqNo.trim());
        mstrRepository.delete(mstr);
        return mstr;
    }

    /**
     * Replace the per-approver chain on an in-flight Approval Box
     * request with the chain attached to {@code apprLineNo}. Resets the
     * master back to Pending so the first approver of the new chain
     * becomes the active one. Same Pending / In Progress guard as
     * {@link #withdrawApproval}.
     *
     * <p>Mirrors the legacy {@code comm_0195_001.ts:278} editApproval
     * flow. Audit + per-module back-stamp updates are the caller's
     * responsibility.
     *
     * @return the loaded {@link ExptResolMstr} AFTER the rewrite, for
     *         the caller's read needs.
     */
    @SuppressWarnings("UnusedReturnValue")
    public ExptResolMstr changeApprovalLine(String apprSeqNo, Long apprLineNo) {
        if (apprSeqNo == null || apprSeqNo.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "appr_seq_no: required");
        }
        if (apprLineNo == null || apprLineNo <= 0) {
            throw new BusinessException("WBE_VALIDATION", "appr_line_no: required");
        }
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String now    = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        ExptResolMstrId id = new ExptResolMstrId(bizKey, apprSeqNo.trim());
        ExptResolMstr mstr = mstrRepository.findById(id)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "Approval reference not found"));

        String typeCd = mstr.getApprTypeCd();
        if (!"00".equals(typeCd) && !"03".equals(typeCd)) {
            throw new BusinessException("WBE_VALIDATION",
                    "Only Pending or In Progress approvals can be edited");
        }

        List<ApprovalLineApprover> chain = apprLineApproverRepository
                .findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo);
        if (chain.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION",
                    "appr_line_no: no approvers found for the picked line");
        }

        approverRepository.deleteByBizKeyAndApprSeqNo(bizKey, apprSeqNo.trim());
        approverRepository.flush();

        int line = 1;
        for (ApprovalLineApprover a : chain) {
            ExptResolApprvrInfm row = new ExptResolApprvrInfm();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo.trim());
            row.setApprvrId(a.getApprvrId());
            row.setApprvrNm(a.getApprvrNm() != null ? a.getApprvrNm() : a.getApprvrId());
            row.setApprvrStts(line == 1 ? APPRVR_STTS_PENDING : APPRVR_STTS_WAITING);
            row.setApprvrLineNo(BigDecimal.valueOf(line));
            row.setReadYn("N");
            row.setRegId(userId);
            row.setRegDtm(now);
            row.setModId(userId);
            row.setModDtm(now);
            approverRepository.save(row);
            line++;
        }

        mstr.setApprTypeCd(APPR_TYPE_CD_PENDING);
        mstr.setModId(userId);
        mstr.setModDtm(now);
        mstrRepository.save(mstr);
        return mstr;
    }
}
