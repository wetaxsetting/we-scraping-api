package com.kosign.wecms.domain.payment.notification.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.entity.BusiAdmnInfm;
import com.kosign.wecms.domain.auth.repository.BusiAdmnInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.notification.entity.BltbComntInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbNotiContInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbNotiInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbPostInfm;
import com.kosign.wecms.domain.payment.notification.entity.BltbUserNewPostInfm;
import com.kosign.wecms.domain.payment.notification.repository.BltbComntInfmRepository;
import com.kosign.wecms.domain.payment.notification.repository.BltbNotiContInfmRepository;
import com.kosign.wecms.domain.payment.notification.repository.BltbNotiInfmRepository;
import com.kosign.wecms.domain.payment.notification.repository.BltbPostInfmRepository;
import com.kosign.wecms.domain.payment.notification.repository.BltbUserNewPostInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared request-approval bulletin / notification writer — one service
 * used by all six Payment modules (Internal Transfer, Vendor, Expense,
 * Payroll, Overseas, Card).
 *
 * <p>Reproduces the legacy request-approval notification fan-out exactly
 * (NOT the approve / reject / comment paths):
 * <ul>
 *   <li>{@code createBulletinPost} → one {@code BLTB_POST_INFM}
 *       ({@code comm_0192_001_c005_act.jsp:544} / {@code card_0001_005_c001_act.jsp:378}).</li>
 *   <li>{@code createUserNewPostNotifications} → one
 *       {@code BLTB_USER_NEW_POST_INFM} per approver
 *       ({@code comm_0192_001_c005_act.jsp:565}).</li>
 *   <li>{@code BltbHelper.createNotificationToApprvr → createPublicNotification}
 *       → one {@code BLTB_NOTI_INFM} + three {@code BLTB_NOTI_CONT_INFM}
 *       (EN / KO / local) per approver
 *       ({@code BltbHelper.java:178} / {@code :256}).</li>
 * </ul>
 *
 * <p><b>PUSH_NOTICE_INFM / MSG_SND_HIS are intentionally NOT written.</b>
 * On the legacy request-approval path {@code pushToMobileNoti}
 * ({@code BltbHelper.java:359}) returns early because the {@code option}
 * map carries no {@code dvCd} key — those rows are only inserted on the
 * approve / reject / comment flows, which are out of scope here.
 *
 * <p>Recipients are the approver rows already written into
 * {@code EXPT_RESOL_APPRVR_INFM} for this {@code APPR_SEQ_NO} (legacy
 * resolves the same set via {@code EXPT_RESOL_APPRVR_INFM_R004}/{@code R006};
 * the refactor's chain has one row per approver with
 * {@code APPRVR_LINE_NO > 0}, so both legacy queries return that set).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalNotificationService {

    /** Approval bulletin board code — legacy {@code GenericBean.BLTB_CD_APPR}. */
    static final String BLTB_CD_APPR = "000000000000000";
    /** {@code PostTypeCd.APPROVAL}. */
    static final String POST_TYPE_APPROVAL = "02";
    /** {@code PostStts.PRIVATE}. */
    static final String POST_STTS_PRIVATE = "PV";
    /** Notification target screen — legacy {@code comm_0193_001.act}
     *  (the approval-detail view). Stored verbatim so the notification
     *  navigates to the same place the live system does. */
    static final String NOTI_WSVC = "comm_0193_001.act";
    /** Notification title — legacy {@code WbMessageCode.WBI1035}. The
     *  legacy dictionaries register no per-language override, so every
     *  language row stores this same literal (differing only by
     *  {@code LANG_CD}). */
    static final String NOTI_TITLE = "Added a new approval.";
    /** Notification content languages — legacy EN / KO / local (VN→VI). */
    static final List<String> NOTI_LANGS = List.of("EN", "KO", "VI");

    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME6 = DateTimeFormatter.ofPattern("HHmmss");

    private final ExptResolApprvrInfmRepository approverRepository;
    private final BltbPostInfmRepository        postRepository;
    private final BltbUserNewPostInfmRepository userNewPostRepository;
    private final BltbNotiInfmRepository        notiRepository;
    private final BltbNotiContInfmRepository    notiContRepository;
    private final BltbComntInfmRepository       comntRepository;
    private final BusiAdmnInfmRepository        busiAdmnRepository;
    private final ExptResolFileRepository       exptResolFileRepository;
    private final LegacyFunctionRepository      legacyFunctions;
    private final Clock                         clock;

    /** Approve/reject comment + notification title constants (legacy
     *  {@code ComntTaskCd} + {@code WbMessageCode}). */
    static final String COMNT_DV_REMARK   = "R";          // ComntDvCd.REMARK
    static final String COMNT_TASK_REJECT   = "R002";     // ComntTaskCd.REJECT
    static final String COMNT_TASK_COMPLETE = "R003";     // ComntTaskCd.COMPLETE
    static final String COMNT_TASK_APPROVED = "R004";     // ComntTaskCd.APPROVED
    static final String NOTI_TITLE_APPROVED = "Approved an approval.";  // WBI1043
    static final String NOTI_TITLE_REJECTED = "Rejected an approval.";  // WBI1044

    /**
     * Write the request-approval bulletin post + per-approver new-post
     * trackers + per-approver notifications (+ per-language content).
     *
     * <p>Runs in the caller's transaction (each module's
     * {@code requestApproval} owns the {@code @Transactional} boundary),
     * AFTER the {@code EXPT_RESOL_APPRVR_INFM} rows are written so the
     * recipient set is resolvable.
     *
     * @param apprSeqNo     the approval sequence (links every row back to
     *                      {@code EXPT_RESOL_MSTR}).
     * @param postTitle     user's approval title ({@code POST_TITLE}).
     * @param postCont      user's approval content note ({@code POST_CONT}); nullable.
     * @param postApprDv    {@code BLTB_POST_INFM.POST_APPR_DV} — mirrors APPR_DV
     *                      ({@code 21/31/32/34/35}) for the five scheduled/overseas
     *                      types, {@code "10"} for Card (legacy frontend value).
     * @param apprTypeLabel human approval-type label embedded in the
     *                      notification URL / JSON (legacy {@code apprType}):
     *                      e.g. {@code "Internal Transfer"}, {@code "Corporate Card Payment"}.
     */
    @Transactional
    public void notifyRequestApproval(String apprSeqNo, String postTitle, String postCont,
                                      String postApprDv, String apprTypeLabel) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        if (userNm == null) userNm = userId;

        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        String nowDtm = now.format(DTM14);
        String today  = now.format(DATE8);
        String time   = now.format(TIME6);

        // 1. BLTB_POST_INFM — one bulletin post for this approval.
        String postSeq = legacyFunctions.nextVal(
                "bltb_post_infm", "post_seq_no", bizKey, BLTB_CD_APPR, null, null, null, null);
        BigDecimal postSeqNo = new BigDecimal(postSeq);

        BltbPostInfm post = new BltbPostInfm();
        post.setBizKey(bizKey);
        post.setBltbCd(BLTB_CD_APPR);
        post.setPostSeqNo(postSeqNo);
        post.setPostTitle(postTitle != null ? postTitle : "");
        post.setPostTypeCd(POST_TYPE_APPROVAL);
        post.setPostApprDv(postApprDv);
        post.setPostStts(POST_STTS_PRIVATE);
        post.setApprSeqNo(apprSeqNo);
        post.setPostCont(postCont != null && !postCont.isBlank() ? postCont : null);
        post.setRegId(userId);
        post.setRegDtm(nowDtm);
        post.setModId(userId);
        post.setModDtm(nowDtm);
        postRepository.save(post);

        // Recipients = the approver rows just written for this approval.
        List<ExptResolApprvrInfm> recipients = approverRepository.findChain(bizKey, apprSeqNo);

        // 2. BLTB_USER_NEW_POST_INFM — one unread-marker per approver.
        for (ExptResolApprvrInfm r : recipients) {
            BltbUserNewPostInfm unp = new BltbUserNewPostInfm();
            unp.setBizKey(bizKey);
            unp.setBltbCd(BLTB_CD_APPR);
            unp.setUserId(r.getApprvrId());
            unp.setPostSeqNo(postSeqNo);
            unp.setReadYn("N");
            unp.setRegId(userId);
            unp.setRegDtm(nowDtm);
            unp.setModId(userId);
            unp.setModDtm(nowDtm);
            userNewPostRepository.save(unp);
        }

        // 3. BLTB_NOTI_INFM (+ 3 BLTB_NOTI_CONT_INFM) — one per approver.
        for (ExptResolApprvrInfm r : recipients) {
            String notiSeq = legacyFunctions.nextVal(
                    "bltb_noti_infm", "noti_seq_no", bizKey, BLTB_CD_APPR, null, null, null, null);
            BigDecimal notiSeqNo = new BigDecimal(notiSeq);

            String rcvNm = r.getApprvrNm() != null ? r.getApprvrNm() : r.getApprvrId();
            String notiUrl = NOTI_WSVC + "?POST_SEQ_NO=" + postSeqNo.toPlainString()
                    + "&APPR_DV=" + apprTypeLabel;
            String notiJson = buildNotiJson(postSeqNo, apprTypeLabel, apprSeqNo, r.getApprvrId());

            BltbNotiInfm noti = new BltbNotiInfm();
            noti.setBizKey(bizKey);
            noti.setBltbCd(BLTB_CD_APPR);
            noti.setNotiSeqNo(notiSeqNo);
            noti.setBltbName("");
            noti.setSndId(userId);
            noti.setSndName(userNm);
            noti.setRcvId(r.getApprvrId());
            noti.setRcvNm(rcvNm);
            noti.setNotiDt(today);
            noti.setNotiTm(time);
            noti.setNotiUrl(notiUrl);
            noti.setActvYn("N");
            noti.setNotiWsvc(NOTI_WSVC);
            noti.setNotiJson(notiJson);
            noti.setPostSeqNo(postSeqNo);
            noti.setDelYn("N");
            noti.setRegId(userId);
            noti.setRegDtm(nowDtm);
            // MOD_ID / MOD_DTM are NOT NULL on DEV; the row is born
            // unmodified so stamp them with the reg values.
            noti.setModId(userId);
            noti.setModDtm(nowDtm);
            notiRepository.save(noti);

            for (String lang : NOTI_LANGS) {
                BltbNotiContInfm cont = new BltbNotiContInfm();
                cont.setBizKey(bizKey);
                cont.setBltbCd(BLTB_CD_APPR);
                cont.setNotiSeqNo(notiSeqNo);
                cont.setLangCd(lang);
                cont.setTitl(NOTI_TITLE);
                cont.setCont("");
                cont.setRegId(userId);
                cont.setRegDtm(nowDtm);
                notiContRepository.save(cont);
            }
        }

        log.info("approval-notification bizKey={} apprSeqNo={} postSeqNo={} recipients={}",
                bizKey, apprSeqNo, postSeqNo.toPlainString(), recipients.size());
    }

    /**
     * Build the {@code NOTI_JSON} navigation payload, mirroring legacy
     * {@code createPublicNotification} ({@code BltbHelper.java:284-295}):
     * {@code POST_SEQ_NO} (numeric), {@code BLTB_CD}, {@code APPR_DV}
     * (the type label), {@code APPR_SEQ_NO}, {@code APPRVR_ID}. The
     * values are controlled codes / ids and the label is a fixed
     * constant, so no characters require JSON escaping.
     */
    private String buildNotiJson(BigDecimal postSeqNo, String apprTypeLabel,
                                 String apprSeqNo, String apprvrId) {
        return "{"
                + "\"POST_SEQ_NO\":" + postSeqNo.toBigInteger()
                + ",\"BLTB_CD\":\"" + BLTB_CD_APPR + "\""
                + ",\"APPR_DV\":\"" + apprTypeLabel + "\""
                + ",\"APPR_SEQ_NO\":\"" + apprSeqNo + "\""
                + ",\"APPRVR_ID\":\"" + apprvrId + "\""
                + "}";
    }

    /* ----------------------------------------------------------------
     *  Approve / Reject — comment + notification (lifecycle parity).
     *  Legacy comm_0192_010_u001 / u002 →
     *  BltbHelper.createNotificationToApprvrAfterRequest (IDO R008).
     * ---------------------------------------------------------------- */

    /**
     * Write the approve/reject system remark ({@code BLTB_COMNT_INFM}) and
     * the per-recipient in-app notification ({@code BLTB_NOTI_INFM} + 3×
     * {@code BLTB_NOTI_CONT_INFM}).
     *
     * <p>Recipients mirror legacy IDO {@code EXPT_RESOL_APPRVR_INFM_R008}:
     * admins ({@code BUSI_ADMN_INFM}) ∪ approvers with
     * {@code APPRVR_STTS ∈ (P,A,R)}, distinct by id, EXCLUDING the actor.
     * The pure drafter (R008 line 0) is excluded by the {@code
     * APPRVR_LINE_NO <> 0} filter, so {@code REG_ID} is not added on its
     * own (the helper is named "owner+admin" but the SQL drops line 0).
     *
     * <p><b>No</b> {@code PUSH_NOTICE_INFM} / {@code MSG_SND_HIS} — legacy
     * {@code pushToMobileNoti} is {@code dvCd}-gated off on this path.
     *
     * <p>Runs in the caller's approve/reject transaction, AFTER the
     * status mutation. If the approval has no bulletin post (submitted
     * before request-notification parity landed) it skips gracefully.
     *
     * @param apprSeqNo   the approval sequence.
     * @param apprDvCode  master {@code APPR_DV} ({@code 21/31/32/33/34/35}).
     * @param comntTaskCd {@code R003} Complete / {@code R004} Approved / {@code R002} Reject.
     */
    @Transactional
    public void notifyApprovalAction(String apprSeqNo, String apprDvCode, String comntTaskCd) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String userNm = TenantContext.currentUserNm();
        if (userNm == null) userNm = userId;

        BltbPostInfm post = postRepository
                .findByBizKeyAndBltbCdAndApprSeqNo(bizKey, BLTB_CD_APPR, apprSeqNo)
                .orElse(null);
        if (post == null) {
            log.warn("approval-action notification skipped — no BLTB_POST_INFM bizKey={} apprSeqNo={}",
                    bizKey, apprSeqNo);
            return;
        }
        BigDecimal postSeqNo = post.getPostSeqNo();

        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        String nowDtm = now.format(DTM14);
        String today  = now.format(DATE8);
        String time   = now.format(TIME6);
        String apprTypeLabel = apprTypeLabel(apprDvCode);
        boolean rejected = COMNT_TASK_REJECT.equals(comntTaskCd);

        // 1. BLTB_COMNT_INFM — system remark recording the action.
        String comntSeq = legacyFunctions.nextVal(
                "bltb_comnt_infm", "comnt_seq_no", bizKey, BLTB_CD_APPR, null, null, null, null);
        BltbComntInfm comnt = new BltbComntInfm();
        comnt.setBizKey(bizKey);
        comnt.setBltbCd(BLTB_CD_APPR);
        comnt.setPostSeqNo(postSeqNo);
        comnt.setComntSeqNo(new BigDecimal(comntSeq));
        comnt.setComntDvCd(COMNT_DV_REMARK);
        comnt.setComntTaskCd(comntTaskCd);
        comnt.setComntCont(null);
        comnt.setRegId(userId);
        comnt.setRegDtm(nowDtm);
        comnt.setModId(userId);
        comnt.setModDtm(nowDtm);
        comntRepository.save(comnt);

        // 2. Recipients (R008): acted approvers (line order) then admins,
        //    distinct by id, excluding the actor.
        Map<String, String> recipients = new LinkedHashMap<>();
        for (ExptResolApprvrInfm a : approverRepository.findChain(bizKey, apprSeqNo)) {
            String s = a.getApprvrStts();
            if (("P".equals(s) || "A".equals(s) || "R".equals(s)) && !userId.equals(a.getApprvrId())) {
                recipients.putIfAbsent(a.getApprvrId(),
                        a.getApprvrNm() != null ? a.getApprvrNm() : a.getApprvrId());
            }
        }
        for (BusiAdmnInfm adm : busiAdmnRepository.findAllByBizKey(bizKey)) {
            if (!userId.equals(adm.getUserId())) {
                recipients.putIfAbsent(adm.getUserId(),
                        adm.getUserNm() != null ? adm.getUserNm() : adm.getUserId());
            }
        }

        String title = rejected ? NOTI_TITLE_REJECTED : NOTI_TITLE_APPROVED;
        String notiUrl = NOTI_WSVC + "?POST_SEQ_NO=" + postSeqNo.toPlainString()
                + "&APPR_DV=" + apprTypeLabel;
        // Action NOTI_JSON carries 3 keys (legacy createApprNotificationAfterRequest),
        // unlike the 5-key request-path JSON.
        String notiJson = "{"
                + "\"POST_SEQ_NO\":" + postSeqNo.toBigInteger()
                + ",\"BLTB_CD\":\"" + BLTB_CD_APPR + "\""
                + ",\"APPR_DV\":\"" + apprTypeLabel + "\""
                + "}";

        for (Map.Entry<String, String> r : recipients.entrySet()) {
            String notiSeq = legacyFunctions.nextVal(
                    "bltb_noti_infm", "noti_seq_no", bizKey, BLTB_CD_APPR, null, null, null, null);
            BltbNotiInfm noti = new BltbNotiInfm();
            noti.setBizKey(bizKey);
            noti.setBltbCd(BLTB_CD_APPR);
            noti.setNotiSeqNo(new BigDecimal(notiSeq));
            noti.setBltbName("");
            noti.setSndId(userId);
            noti.setSndName(userNm);
            noti.setRcvId(r.getKey());
            noti.setRcvNm(r.getValue());
            noti.setNotiDt(today);
            noti.setNotiTm(time);
            noti.setNotiUrl(notiUrl);
            noti.setActvYn("N");
            noti.setNotiWsvc(NOTI_WSVC);
            noti.setNotiJson(notiJson);
            noti.setPostSeqNo(postSeqNo);
            noti.setDelYn("N");
            noti.setRegId(userId);
            noti.setRegDtm(nowDtm);
            noti.setModId(userId);
            noti.setModDtm(nowDtm);
            notiRepository.save(noti);

            for (String lang : NOTI_LANGS) {
                BltbNotiContInfm cont = new BltbNotiContInfm();
                cont.setBizKey(bizKey);
                cont.setBltbCd(BLTB_CD_APPR);
                cont.setNotiSeqNo(noti.getNotiSeqNo());
                cont.setLangCd(lang);
                cont.setTitl(title);
                cont.setCont("");
                cont.setRegId(userId);
                cont.setRegDtm(nowDtm);
                notiContRepository.save(cont);
            }
        }
        log.info("approval-action notification bizKey={} apprSeqNo={} taskCd={} recipients={}",
                bizKey, apprSeqNo, comntTaskCd, recipients.size());
    }

    /* ----------------------------------------------------------------
     *  Withdraw cleanup (lifecycle parity). Legacy comm_0192_004_d001.
     * ---------------------------------------------------------------- */

    /**
     * Delete the approval's attachments + bulletin artifacts on withdraw.
     * {@code EXPT_RESOL_FILE} is deleted first (FK to {@code EXPT_RESOL_MSTR});
     * the bulletin post + its {@code BLTB_USER_NEW_POST_INFM} + {@code BLTB_COMNT_INFM}
     * rows are hard-deleted; {@code BLTB_NOTI_INFM} is SOFT-deleted
     * ({@code DEL_YN='Y'}); {@code BLTB_NOTI_CONT_INFM} is left in place —
     * exactly mirroring legacy {@code comm_0192_004_d001}.
     *
     * <p>Runs in the caller's withdraw transaction, BEFORE the
     * {@code EXPT_RESOL_MSTR} / {@code EXPT_RESOL_APPRVR_INFM} deletes so
     * the {@code EXPT_RESOL_FILE} FK is honoured.
     */
    @Transactional
    public void cleanupApprovalArtifacts(String apprSeqNo) {
        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        String nowDtm = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).format(DTM14);

        exptResolFileRepository.deleteByBizKeyAndApprSeqNo(bizKey, apprSeqNo);

        BltbPostInfm post = postRepository
                .findByBizKeyAndBltbCdAndApprSeqNo(bizKey, BLTB_CD_APPR, apprSeqNo)
                .orElse(null);
        if (post == null) {
            return;
        }
        BigDecimal postSeqNo = post.getPostSeqNo();
        userNewPostRepository.deleteByPost(bizKey, BLTB_CD_APPR, postSeqNo);
        comntRepository.deleteByPost(bizKey, BLTB_CD_APPR, postSeqNo);
        notiRepository.softDeleteByPost(bizKey, BLTB_CD_APPR, postSeqNo, userId, nowDtm);
        postRepository.delete(post);
        log.info("approval-artifact cleanup bizKey={} apprSeqNo={} postSeqNo={}",
                bizKey, apprSeqNo, postSeqNo.toPlainString());
    }

    /** Map master {@code APPR_DV} code → the legacy human approval-type
     *  label used in the notification URL / JSON (matches the request path). */
    private static String apprTypeLabel(String apprDvCode) {
        if (apprDvCode == null) return "";
        return switch (apprDvCode) {
            case "21" -> "Payroll Payment";
            case "31" -> "Expense Payment";
            case "32" -> "Vendor Payment";
            case "33" -> "Corporate Card Payment";
            case "34" -> "Oversea Payment";
            case "35" -> "Internal Transfer";
            default   -> "";
        };
    }
}
