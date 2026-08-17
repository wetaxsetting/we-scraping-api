package com.kosign.wecms.domain.setting.zalooa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiClient;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiException;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloSendCommand;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloSendOutcome;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendRequest;
import com.kosign.wecms.domain.setting.zalooa.dto.ZaloSendResponse;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloOaInfm;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloSendHis;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloTmplInfm;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloOaInfmRepository;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloTmplInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sends one Zalo template message for the signed-in tenant and records the
 * attempt.
 *
 * <h2>Send sequence</h2>
 *
 * <ol>
 *   <li>resolve the tenant's active Official Account</li>
 *   <li>resolve the approved template for {@code (type, language)}</li>
 *   <li>obtain a valid access token (refreshing if needed)</li>
 *   <li>call Zalo</li>
 *   <li>write a {@code ZALO_SEND_HIS} row — <b>on every path</b></li>
 * </ol>
 *
 * <h2>Not transactional, on purpose</h2>
 *
 * <p>There is no database state to make atomic with the vendor call, and wrapping
 * one would be actively harmful: the token refresh in step 3 and the log write in
 * step 5 each need to commit independently of everything else (see
 * {@code ZaloTokenService.accessTokenFor} and {@code ZaloSendHistoryService}). An
 * outer transaction here would either swallow their isolation or hold a DB
 * transaction open across a network call to Zalo.
 *
 * <h2>Failures are recorded, not swallowed</h2>
 *
 * <p>A Zalo rejection and a transport fault both produce a history row and a
 * {@code success=false} response. What must never happen is an exception path that
 * skips the row: the message may have been delivered and billed, and support would
 * have no trace of it. The one exception is a failure <em>before</em> the call —
 * no OA, no template, no token — which throws, because nothing was attempted and
 * there is nothing to reconcile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloMessageService {

    private static final String Y = "Y";
    private static final String N = "N";

    /** {@code RSLT_CD} marker for a fault where Zalo never answered at all. */
    private static final int LOCAL_FAULT_CODE = -1;

    private final ZaloOaInfmRepository oaRepo;
    private final ZaloTmplInfmRepository tmplRepo;
    private final ZaloApiClient zaloApiClient;
    private final ZaloTokenService tokenService;
    private final ZaloSendHistoryService historyService;
    private final ObjectMapper objectMapper;

    /**
     * Send to an explicit recipient.
     *
     * <p>The recipient is a parameter because <b>WeCMS has not yet decided who
     * receives Zalo messages</b> — see {@link ZaloSendRequest} and open decision 1
     * in {@code docs/references/database/settings/zalo-oa-schema.md}. When that
     * lands, event-driven callers resolve a recipient and call this method; the
     * signature does not need to change.
     *
     * @throws BusinessException {@code WBE_ZALO_NO_ACTIVE_OA},
     *         {@code WBE_ZALO_TEMPLATE_NOT_FOUND}, {@code WBE_ZALO_DISABLED}, or
     *         whatever {@code ZaloTokenService} raises — all pre-call failures
     */
    public ZaloSendResponse send(ZaloSendRequest req) {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE1009");
        }

        ZaloOaInfm oa = oaRepo.findByBizKeyAndUseYnAndDelYn(bizKey, Y, N)
                .orElseThrow(() -> new BusinessException("WBE_ZALO_NO_ACTIVE_OA"));

        ZaloTmplInfm tmpl = tmplRepo
                .findByBizKeyAndOaIdAndTmplTypeAndLangCdAndUseYn(
                        bizKey, oa.getOaId(), req.tmplType(), req.langCd(), Y)
                .orElseThrow(() -> new BusinessException("WBE_ZALO_TEMPLATE_NOT_FOUND"));

        // Obtained before the row is built so a token failure throws without
        // leaving a half-attempt logged. Commits in its own transaction.
        String accessToken = tokenService.accessTokenFor(bizKey, oa.getOaId());

        String trackingId = UUID.randomUUID().toString();
        ZaloSendCommand command = new ZaloSendCommand(
                req.phone(), tmpl.getTmplId(), Map.copyOf(req.templateData()), trackingId);
        String reqData = serialize(command);
        OffsetDateTime reqDtm = OffsetDateTime.now();

        ZaloSendOutcome outcome;
        try {
            outcome = zaloApiClient.sendTemplateMessage(accessToken, command);
        } catch (UnsupportedOperationException ex) {
            // MockZaloApiClient — nothing left the process, so nothing to reconcile.
            throw new BusinessException("WBE_ZALO_DISABLED");
        } catch (ZaloApiException ex) {
            // Zalo may or may not have received this. Record it as an attempt with a
            // local marker rather than losing it: an unrecorded send that actually
            // went through is a message the tenant paid for and cannot account for.
            log.error("feature=zalo-oa send fault biz_key={} oa_id={} tmpl_id={}",
                    bizKey, oa.getOaId(), tmpl.getTmplId(), ex);
            outcome = new ZaloSendOutcome(LOCAL_FAULT_CODE, ex.getMessage(), null);
        }

        ZaloSendHis logged = historyService.record(
                bizKey, oa.getOaId(), tmpl.getTmplId(), tmpl.getTmplType(), tmpl.getLangCd(),
                req.phone(), trackingId, reqData, reqDtm, outcome);

        return new ZaloSendResponse(
                logged.getSendSeq(),
                outcome.success(),
                outcome.errorCode() == ZaloSendOutcome.SUCCESS_CODE
                        ? String.valueOf(ZaloSendOutcome.SUCCESS_CODE)
                        : String.valueOf(outcome.errorCode()),
                outcome.message(),
                trackingId);
    }

    /**
     * Serialise the request for {@code REQ_DATA}. A serialisation failure must not
     * abort the send — the payload is diagnostic, the message is the product — so it
     * degrades to a marker string.
     */
    private String serialize(ZaloSendCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception ex) {
            log.warn("feature=zalo-oa could not serialise request payload for logging", ex);
            return "{\"error\":\"payload not serialisable\"}";
        }
    }

    /**
     * Read-only visibility used by tests and future callers that need to know
     * whether a tenant can send at all before composing a message.
     */
    @Transactional(readOnly = true)
    public boolean canSend(String bizKey) {
        return oaRepo.findByBizKeyAndUseYnAndDelYn(bizKey, Y, N)
                .filter(oa -> oa.getRefreshToken() != null && !oa.getRefreshToken().isBlank())
                .isPresent();
    }
}
