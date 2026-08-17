package com.kosign.wecms.domain.setting.zalooa.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloSendOutcome;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendHistoryListResponse;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendHistoryRow;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloSendHis;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloSendHisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Writes and reads {@code ZALO_SEND_HIS}.
 *
 * <p><b>Separate bean, and {@code REQUIRES_NEW} on the write.</b> A send log is
 * worth least in exactly the case it is hardest to record: the attempt that
 * failed. If the write joined the caller's transaction, a rollback anywhere
 * downstream would erase the only evidence the attempt happened — and a
 * <em>billed</em> message may already have been delivered. Committing in its own
 * transaction means the row survives regardless.
 *
 * <p>It is a separate bean rather than a method on {@code ZaloMessageService}
 * because Spring's transaction advice lives on the proxy: a self-call would run
 * with the caller's propagation and silently lose the isolation. Mirrors
 * {@code LoginHistoryService}, which exists for the same reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloSendHistoryService {

    private final ZaloSendHisRepository sendHisRepo;

    /**
     * Record one attempt.
     *
     * <p>Called on every path — success, Zalo rejection, and local fault — so the
     * parameters are all nullable except the tenant: a send that failed before the
     * OA or template could be resolved still gets a row, with the columns it never
     * reached left empty.
     *
     * @param reqDtm  when the request left; passed in rather than taken as
     *                {@code now()} so the recorded time is the actual call, not the
     *                (possibly much later) moment the log was written
     * @param outcome Zalo's answer, or a locally-built one for a fault
     * @return the persisted row, so the caller can return its {@code SEND_SEQ}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ZaloSendHis record(String bizKey,
                              String oaId,
                              String tmplId,
                              String tmplType,
                              String langCd,
                              String rcvrNo,
                              String trackingId,
                              String reqData,
                              OffsetDateTime reqDtm,
                              ZaloSendOutcome outcome) {
        ZaloSendHis row = new ZaloSendHis();
        row.setBizKey(bizKey);
        row.setOaId(oaId);
        row.setTmplId(tmplId);
        row.setTmplType(tmplType);
        row.setLangCd(langCd);
        row.setRcvrNo(rcvrNo);
        row.setTrackingId(trackingId);
        row.setReqData(reqData);
        row.setReqDtm(reqDtm);
        row.setResData(outcome.rawResponse());
        row.setResDtm(OffsetDateTime.now());
        row.setRsltCd(String.valueOf(outcome.errorCode()));
        row.setRsltMsg(truncate(outcome.message()));

        String actor = TenantContext.currentUserId();
        row.setRegId(actor == null ? "SYSTEM" : actor);
        row.setModId(row.getRegId());
        return sendHisRepo.save(row);
    }

    /** Send history for the signed-in tenant, newest first. */
    @Transactional(readOnly = true)
    public ZaloSendHistoryListResponse list(Pageable pageable) {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE1009");
        }
        Page<ZaloSendHis> page = sendHisRepo.findByBizKeyOrderByRegDtmDesc(bizKey, pageable);
        return new ZaloSendHistoryListResponse(
                page.getContent().stream().map(ZaloSendHistoryService::toRow).toList(),
                page.getTotalElements());
    }

    private static ZaloSendHistoryRow toRow(ZaloSendHis his) {
        return new ZaloSendHistoryRow(
                his.getSendSeq(),
                his.getRcvrNo(),
                his.getTmplId(),
                his.getTmplType(),
                his.getLangCd(),
                String.valueOf(ZaloSendOutcome.SUCCESS_CODE).equals(his.getRsltCd()),
                his.getRsltCd(),
                his.getRsltMsg(),
                his.getTrackingId(),
                ZaloOaService.dtm14(his.getReqDtm()));
    }

    /**
     * {@code RSLT_MSG} is {@code varchar(500)}. A vendor message longer than that
     * would fail the insert — and losing the whole row over a verbose error string
     * is the opposite of what a log is for.
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
