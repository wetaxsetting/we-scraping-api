package com.kosign.wecms.domain.setting.zalooa.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiClient;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloApiException;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloProperties;
import com.kosign.wecms.domain.setting.zalooa.client.ZaloTokenGrant;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloOaInfm;
import com.kosign.wecms.domain.setting.zalooa.repository.ZaloOaInfmRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Owns the lifecycle of a connection's Zalo OAuth tokens: hand out a usable
 * access token on demand, and keep the rotating refresh token alive.
 *
 * <h2>No in-memory token cache — deliberately</h2>
 *
 * <p>The reference implementation caches tokens in a per-process
 * {@code ConcurrentHashMap} ({@code TokenStore}). That is unsafe here and is
 * recorded as finding §10.2 of
 * {@code webill365-vn-api/docs/features/zalo-oa-integration.md}: because Zalo
 * <b>rotates the refresh token on every refresh and invalidates the previous
 * one</b>, a second instance still holding a cached copy will eventually present
 * a dead refresh token and lose the connection — recoverable only by a human
 * redoing the OAuth popup. This repo already carries the multi-instance
 * durability caveat for its in-memory JWT revocation store; adding a second
 * in-memory store with a worse failure mode is not worth the saved query. The
 * token is read from {@code ZALO_OA_INFM} each time.
 *
 * <h2>Two things that must not be conflated</h2>
 *
 * <p><b>Access token</b> — short-lived, refreshed on demand by
 * {@link #accessTokenFor(String, String)} when it is at or near expiry.
 * <br><b>Refresh token</b> — valid roughly three months, kept alive by
 * {@link #refreshStaleConnections()}. A tenant that sends nothing for a quarter
 * would otherwise silently lose its authorisation. The sweep is about the
 * rotation, not about warm access tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloTokenService {

    /** Audit actor for writes made by the scheduled sweep, which has no user. */
    private static final String SYSTEM_USER = "SYSTEM";

    private final ZaloOaInfmRepository oaRepo;
    private final ZaloApiClient zaloApiClient;
    private final ZaloProperties props;

    /**
     * Self-reference so the sweep loop reaches the per-connection
     * {@code @Transactional(REQUIRES_NEW)} method through the Spring proxy — a
     * direct {@code this.refreshOneInOwnTransaction(...)} call would bypass the
     * transaction advice entirely and put every connection in one transaction,
     * defeating the per-tenant isolation the loop exists for. {@code @Lazy} breaks
     * the construction-time self cycle. Mirrors
     * {@code OperationStatusBatchService.self}. Package-private so unit tests can
     * wire it without reflection.
     */
    @Autowired
    @Lazy
    ZaloTokenService self;

    /**
     * A valid access token for one connection, refreshing first if the stored one
     * is at or near expiry.
     *
     * <h3>Why {@code REQUIRES_NEW}</h3>
     *
     * <p>The rotated token pair must commit <b>independently of the caller's
     * transaction</b>. Zalo has already invalidated the old refresh token by the
     * time this method returns, so if the caller's transaction later rolls back —
     * a failed send, a validation error further down — joining it would discard
     * the only copy of the new refresh token that exists anywhere. The stored
     * value would then be one Zalo no longer accepts, and the connection would be
     * dead until someone repeated the OAuth consent flow. Committing separately
     * costs a transaction; getting it wrong costs a manual recovery per tenant.
     *
     * <h3>Why the row is locked</h3>
     *
     * <p>{@code SELECT … FOR UPDATE} serialises concurrent refreshes of the same
     * connection across instances. Without it, two requests arriving as the token
     * expires both refresh; the second rotation invalidates the first, and
     * whichever write lands last wins — leaving at least one caller holding a
     * token Zalo has already retired.
     *
     * @throws BusinessException {@code WBE_ZALO_OA_NOT_FOUND} when the connection
     *         is gone, {@code WBE_ZALO_NOT_AUTHORIZED} when it never completed
     *         OAuth, {@code WBE_ZALO_TOKEN_REFRESH_FAILED} when Zalo refuses.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String accessTokenFor(String bizKey, String oaId) {
        ZaloOaInfm oa = oaRepo.lockForTokenRefresh(bizKey, oaId)
                .orElseThrow(() -> new BusinessException("WBE_ZALO_OA_NOT_FOUND"));

        if (oa.getRefreshToken() == null || oa.getRefreshToken().isBlank()) {
            // A row exists from the moment the tenant submits an OA id; tokens only
            // arrive once they approve in the Zalo popup. This is a normal
            // intermediate state, not corruption — say so precisely.
            throw new BusinessException("WBE_ZALO_NOT_AUTHORIZED");
        }

        if (!needsRefresh(oa)) {
            return oa.getAccessToken();
        }

        log.info("feature=zalo-oa refreshing access token biz_key={} oa_id={} expiry={}",
                bizKey, oaId, oa.getTokenExpiDtm());
        applyGrant(oa, refresh(oa.getRefreshToken(), bizKey, oaId), currentActor());
        oaRepo.save(oa);
        return oa.getAccessToken();
    }

    /**
     * Store a fresh grant on a connection. Used by the OAuth completion path in
     * {@code ZaloOaService} as well as by refresh here, so the "always write both
     * halves plus the derived expiry" rule lives in exactly one place.
     */
    public void applyGrant(ZaloOaInfm oa, ZaloTokenGrant grant, String actor) {
        oa.setAccessToken(grant.accessToken());
        // Never conditional: Zalo has already invalidated whatever was here.
        oa.setRefreshToken(grant.refreshToken());
        oa.setTokenExpiDtm(OffsetDateTime.now().plusSeconds(grant.expiresInSeconds()));
        oa.setModId(actor);
    }

    /**
     * Keep long-idle connections' refresh tokens alive. Driven by
     * {@code ZaloTokenRefreshScheduler}; see {@link ZaloProperties#getRefreshStaleDays()}
     * for why the threshold is what it is.
     *
     * <p>Each connection is refreshed in its own transaction so one tenant's
     * failure — revoked app permission, disabled OA, network fault — cannot roll
     * back or abort the others. Returns a summary rather than throwing, because a
     * partial sweep is a normal outcome worth logging, not a job failure.
     */
    public SweepResult refreshStaleConnections() {
        List<ZaloOaInfm> stale = oaRepo.findStaleForRefresh(props.getRefreshStaleDays());
        int ok = 0;
        int failed = 0;
        for (ZaloOaInfm oa : stale) {
            try {
                // Through the proxy — see the `self` field Javadoc.
                self.refreshOneInOwnTransaction(oa.getBizKey(), oa.getOaId());
                ok++;
            } catch (RuntimeException ex) {
                failed++;
                // Log and continue. Deliberately does NOT deactivate the connection:
                // a transient network fault would then silently switch the tenant's
                // Zalo messaging off, and nothing would switch it back on. The
                // reference implementation also only logs here, and that part of its
                // behaviour is correct.
                log.error("feature=zalo-oa refresh sweep failed biz_key={} oa_id={}",
                        oa.getBizKey(), oa.getOaId(), ex);
            }
        }
        SweepResult result = new SweepResult(stale.size(), ok, failed);
        log.info("feature=zalo-oa refresh sweep done {}", result);
        return result;
    }

    /**
     * One connection's refresh, isolated. Forces a refresh regardless of access-token
     * expiry — the point of the sweep is to exercise the rotation, and an access
     * token that happens to still be valid does not keep the refresh token alive.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshOneInOwnTransaction(String bizKey, String oaId) {
        ZaloOaInfm oa = oaRepo.lockForTokenRefresh(bizKey, oaId)
                .orElseThrow(() -> new BusinessException("WBE_ZALO_OA_NOT_FOUND"));
        if (oa.getRefreshToken() == null || oa.getRefreshToken().isBlank()) {
            throw new BusinessException("WBE_ZALO_NOT_AUTHORIZED");
        }
        applyGrant(oa, refresh(oa.getRefreshToken(), bizKey, oaId), SYSTEM_USER);
        oaRepo.save(oa);
    }

    private ZaloTokenGrant refresh(String refreshToken, String bizKey, String oaId) {
        try {
            return zaloApiClient.refreshAccessToken(refreshToken);
        } catch (ZaloApiException ex) {
            log.error("feature=zalo-oa token refresh rejected biz_key={} oa_id={} zalo_error={}",
                    bizKey, oaId, ex.getZaloErrorCode(), ex);
            throw new BusinessException("WBE_ZALO_TOKEN_REFRESH_FAILED");
        }
    }

    /**
     * True when there is no usable access token, or it falls inside the
     * {@link ZaloProperties#getExpirySkewSeconds()} margin. The margin exists so a
     * token cannot lapse between this check and Zalo receiving the request.
     */
    private boolean needsRefresh(ZaloOaInfm oa) {
        if (oa.getAccessToken() == null || oa.getAccessToken().isBlank()) {
            return true;
        }
        OffsetDateTime expiry = oa.getTokenExpiDtm();
        if (expiry == null) {
            return true;
        }
        return OffsetDateTime.now().isAfter(expiry.minusSeconds(props.getExpirySkewSeconds()));
    }

    /**
     * Audit actor for a refresh triggered by a request. Falls back to
     * {@link #SYSTEM_USER} because this can also run under the sweep, where there
     * is no signed-in user to attribute the write to.
     */
    private String currentActor() {
        String userId = TenantContext.currentUserId();
        return userId == null ? SYSTEM_USER : userId;
    }

    /**
     * Outcome of one sweep pass.
     *
     * @param scanned connections found stale
     * @param refreshed connections whose rotation succeeded
     * @param failed connections whose rotation failed (logged, left untouched)
     */
    public record SweepResult(int scanned, int refreshed, int failed) {
    }
}
