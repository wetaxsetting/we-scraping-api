package com.kosign.wecms.domain.payment.approvalbox.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprLine;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolDetl;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprLineRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolDetlRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.setting.approvalline.entity.ApprLineInfmDetl;
import com.kosign.wecms.domain.setting.approvalline.repository.ApprovalLineApproverRepository;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfm;
import com.kosign.wecms.domain.setting.environmentsettings.entity.BusiEnvSetInfmId;
import com.kosign.wecms.domain.setting.environmentsettings.repository.BusiEnvSetInfmRepository;
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
    private final ApprovalLineApproverRepository apprLineApproverRepository;
    private final ExptResolDetlRepository       detlRepository;
    private final ExptResolApprLineRepository   apprLineRepository;
    private final BusiEnvSetInfmRepository      busiEnvSetInfmRepository;
    private final Clock                         clock;
    private final LegacyFunctionRepository      legacyFunctions;
    /** Withdraw-time bulletin/file cleanup (legacy comm_0192_004_d001 parity). */
    private final com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService
                                                approvalNotificationService;
    /** Emits the PENDING lifecycle signal consumed by HAENGSUNG Oracle write-back. */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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
     * @param apprLineNo  The picked {@code APPR_LINE_INFM.APPR_LINE_NO}
     *                    stamped onto every {@code EXPT_RESOL_APPRVR_INFM.APPR_LINE_NO}
     *                    row (legacy parity — Card previously left this
     *                    null; the other five Payment modules set it
     *                    inline). Null leaves the column unset.
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
            List<Approver> approvers,
            Long apprLineNo) {

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
        // F-4 (legacy parity): legacy comm_0192_001_c005 sets ApprSts.STS_2
        // ("2" = In Progress) at request time, not "0" (temp save).
        mstr.setApprSts("2");
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
        // L-1 (legacy parity): DOC_NM = legacy apprDv.getLabel(), not the slug.
        // Matches the scheduled-module inline writers + legacy
        // card_0001_005_c001_act.jsp:237 (exptResolMstr.setDocNm(apprDv.getLabel())).
        mstr.setDocNm(apprDv.docNm());
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
        // M-2 (legacy parity): print-form display flags from BUSI_ENV_SET_INFM
        // (00014/16/17/18) via the shared yn() normaliser, not hardcoded —
        // matches the scheduled-module inline writers + legacy
        // comm_0192_001_c005_act.jsp:316-320 / card_0001_005_c001_act.jsp:256-260.
        // HAND_REG_YN stays 'Y' (legacy setHandRegYn(YesOrNo.YES), unconditional).
        // An unseeded tenant resolves every flag to 'N' (legacy YesOrNo.fromValue("")).
        mstr.setHandRegYn("Y");
        mstr.setApprBoxUseYn(ynEnv(bizKey, "00014"));
        mstr.setApprDatDispYn(ynEnv(bizKey, "00016"));
        mstr.setCmpnLogoDispYn(ynEnv(bizKey, "00017"));
        mstr.setRegDispYn(ynEnv(bizKey, "00018"));
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
            // APPR_LINE_NO — the picked approval-line id (legacy parity:
            // EXPT_RESOL_APPRVR_INFM.APPR_LINE_NO). Card previously left
            // this null; now set it like the other five modules.
            if (apprLineNo != null) {
                row.setApprLineNo(BigDecimal.valueOf(apprLineNo));
            }
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

        // Lifecycle signal — request-approval submitted (PENDING). The HAENGSUNG
        // Oracle write-back is the only subscriber and filters to HAENGSUNG/ERP
        // batches; emitting it for every module/tenant is safe.
        eventPublisher.publishEvent(new com.kosign.wecms.domain.payment.approvalbox.event
                .PaymentApprovalStatusChangedEvent(bizKey, apprSeqNo,
                com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus.PENDING));
    }

    /**
     * Allocate the next {@code DOC_NO} for the tenant via the single
     * {@code wb_get_next_val} funnel ({@link LegacyFunctionRepository#nextDocNo(String)} →
     * V140 branch&nbsp;#39, env-set-driven).
     *
     * <p><strong>Funnel migration (Phase 1, 2026-06-10):</strong> this previously used a
     * hardcoded {@code VO_yyMMdd_NNN} counter that ignored the tenant ENV settings. Routing
     * through the funnel intentionally fixes that gap — Card Transactions now honours the
     * same env-set prefix / date-format / seq-length (BUSI/SYS_ENV_SET_INFM 00070..00073) as
     * every other Payment module and the live JEX/UAT/PROD DB. Identical output for default
     * {@code VO}/{@code YYMMDD}/3 tenants; env-set-override tenants now get their configured
     * format instead of the hardcoded one.
     *
     * <p>The {@code now} parameter is retained for call-site compatibility but is no longer
     * used — the DB branch derives the date from {@code timezone('utc', now())}.
     */
    public String nextDocNo(String bizKey, OffsetDateTime now) {
        return legacyFunctions.nextDocNo(bizKey);
    }

    /**
     * F-1 — insert the single {@code EXPT_RESOL_DETL} row the legacy
     * Request Approval flow creates (legacy
     * {@code comm_0192_001_c005_act.jsp:340 createExptResolDetl}).
     *
     * <p>For the scheduled payment types {@code DOC_ID} = the payment
     * batch business key, {@code AMT} = the batch total, {@code CCY_CD}
     * = the batch currency (legacy {@code TOT_PAY_KHR_AMT} /
     * {@code input.CCY_CD}). {@code APPR_DETL_SEQ_NO} is allocated via
     * the existing {@code wb_get_next_val('expt_resol_detl',
     * 'appr_detl_seq_no', biz_key)} funnel branch — no new branch, no
     * schema change.
     *
     * <p>Runs in the caller's transaction (no {@code @Transactional}
     * here — the per-module {@code requestApproval} owns the boundary).
     * The funnel native query auto-flushes the pending
     * {@code EXPT_RESOL_MSTR} insert first, so the detail row's
     * {@code expt_resol_detl_fk01} FK to the master is satisfied.
     *
     * @param apprSeqNo the master sequence (same value on the summary).
     * @param docId     the payment batch id (legacy {@code SCDUL_PYMN_NO}
     *                  / {@code OVRS_PYMN_NO}).
     * @param amt       the batch total amount.
     * @param ccyCd     the batch currency — must be one of
     *                  {@code VND}/{@code USD}/{@code KRW} (legacy
     *                  {@code expt_resol_detl_chk01}); blank defaults to
     *                  {@code VND}.
     */
    public void writeResolutionDetail(String apprSeqNo, String docId, BigDecimal amt, String ccyCd) {
        writeResolutionDetail(apprSeqNo, docId, amt, ccyCd, null, null, null, null);
    }

    /**
     * Card overload — one {@code EXPT_RESOL_DETL} row per card transaction
     * (legacy {@code card_0001_005_c001_act.jsp:280 createExptResolDetl},
     * which loops REC3). Adds the per-card columns the scheduled / overseas
     * paths leave null: {@code BUSA_USAG_CD}, {@code MEMO}, {@code DOC_TYPE}
     * ({@code 'B'} for card), {@code TRSC_DT}.
     *
     * @param busaUsagCd purpose code (legacy {@code REC3.BUSA_USAG_CD}); nullable.
     * @param memo       per-card memo (legacy {@code REC3.MEMO}); nullable.
     * @param docType    {@code 'B'}/{@code 'E'}/{@code ''} (CHECK-constrained); nullable.
     * @param trscDt     {@code yyyyMMdd}; nullable (legacy card REC3 sends blank).
     */
    public void writeResolutionDetail(String apprSeqNo, String docId, BigDecimal amt, String ccyCd,
                                      String busaUsagCd, String memo, String docType, String trscDt) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        String seq = legacyFunctions.nextVal(
                "expt_resol_detl", "appr_detl_seq_no", bizKey, null, null, null, null, null);

        ExptResolDetl d = new ExptResolDetl();
        d.setBizKey(bizKey);
        d.setApprSeqNo(apprSeqNo);
        d.setApprDetlSeqNo(new BigDecimal(seq));
        d.setCcyCd(ccyCd != null && !ccyCd.isBlank() ? ccyCd.toUpperCase() : "VND");
        d.setAmt(amt != null ? amt : BigDecimal.ZERO);
        d.setDocId(docId);
        d.setBusaUsagCd(busaUsagCd);
        d.setMemo(memo);
        // DOC_TYPE CHECK allows only 'B'/'E'/'' — blank-out anything else.
        d.setDocType(docType != null && (docType.equals("B") || docType.equals("E")) ? docType : null);
        d.setTrscDt(trscDt != null && !trscDt.isBlank() ? trscDt : null);
        d.setRegId(userId != null ? userId : "");
        d.setRegDtm(nowDtm);
        detlRepository.save(d);
    }

    /**
     * F-2 — insert the {@code EXPT_RESOL_APPR_LINE} print-header snapshot
     * the legacy Request Approval flow creates (legacy
     * {@code comm_0192_001_c005_act.jsp:489 createApprovalLines}).
     *
     * <p>This is the standing approval-line display snapshot (job-class +
     * employee names), <b>distinct</b> from the live routing chain in
     * {@code EXPT_RESOL_APPRVR_INFM}. Legacy gates the write on env-set
     * {@code 00014} ({@code APPR_BOX_USE_YN='Y'}); the row count comes
     * from env-set {@code 00015} ({@code APPR_BOX_DISP_CNT}); names from
     * env-set {@code 00029+i} ({@code JBCL_NM}) / {@code 00034+i}
     * ({@code EMPL_NM}). When the tenant has the print form disabled
     * (no {@code 00014='Y'}) this is a no-op — exactly legacy behaviour.
     *
     * <p>{@code APPR_LINE_NO} is the legacy {@code varchar(1)} loop
     * counter {@code "1".."N"}; capped at 9 to respect the column width.
     * Runs in the caller's transaction.
     */
    public void writeApprovalLineSnapshot(String apprSeqNo) {
        String bizKey = TenantContext.currentBizKey();
        if (!"Y".equalsIgnoreCase(readEnv(bizKey, "00014"))) {
            return; // legacy: APPR_BOX_USE_YN != 'Y' → no snapshot rows
        }
        int cnt = parseCount(readEnv(bizKey, "00015"));
        if (cnt <= 0) {
            return;
        }
        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        int max = Math.min(cnt, 9); // APPR_LINE_NO is varchar(1)
        for (int i = 0; i < max; i++) {
            ExptResolApprLine row = new ExptResolApprLine();
            row.setBizKey(bizKey);
            row.setApprSeqNo(apprSeqNo);
            row.setApprLineNo(String.valueOf(i + 1));
            row.setJbclNm(readEnv(bizKey, String.format("%05d", 29 + i)));   // 00029..
            row.setEmplNm(readEnv(bizKey, String.format("%05d", 34 + i)));   // 00034..
            row.setRegId(userId);
            row.setRegDtm(nowDtm);
            apprLineRepository.save(row);
        }
    }

    /** Read one tenant environment-setting value (legacy
     *  {@code SzonHelper.getEnvSetCont}); null when the key is unseeded. */
    private String readEnv(String bizKey, String key) {
        return busiEnvSetInfmRepository.findById(new BusiEnvSetInfmId(bizKey, key))
                .map(BusiEnvSetInfm::getEnvSetCont)
                .orElse(null);
    }

    /** M-2: legacy {@code YesOrNo.fromValue} normalisation of an env flag —
     *  {@code "Y"} when the env row holds {@code "Y"}, else {@code "N"}
     *  (including an unseeded / absent row). */
    private String ynEnv(String bizKey, String key) {
        String raw = readEnv(bizKey, key);
        return raw != null && "Y".equalsIgnoreCase(raw.trim()) ? "Y" : "N";
    }

    private static int parseCount(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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

        // Bulletin / attachment cleanup (legacy comm_0192_004_d001) — runs
        // BEFORE the master delete so EXPT_RESOL_FILE's FK to EXPT_RESOL_MSTR
        // is honoured.
        approvalNotificationService.cleanupApprovalArtifacts(apprSeqNo.trim());

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

        List<ApprLineInfmDetl> chain = apprLineApproverRepository
                .findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo);
        if (chain.isEmpty()) {
            throw new BusinessException("WBE_VALIDATION",
                    "appr_line_no: no approvers found for the picked line");
        }

        approverRepository.deleteByBizKeyAndApprSeqNo(bizKey, apprSeqNo.trim());
        approverRepository.flush();

        int line = 1;
        for (ApprLineInfmDetl a : chain) {
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
