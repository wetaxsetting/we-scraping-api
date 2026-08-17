package com.kosign.wecms.domain.setting.zalooa.repository;

import com.kosign.wecms.domain.setting.zalooa.entity.ZaloOaInfm;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloOaInfmId;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ZaloOaInfm}. Every request-scoped method constrains on
 * {@code BIZ_KEY}, per the tenant-isolation rule in {@code CLAUDE.md}.
 */
public interface ZaloOaInfmRepository extends JpaRepository<ZaloOaInfm, ZaloOaInfmId> {

    /** Settings list for the signed-in tenant, newest connection first. */
    Page<ZaloOaInfm> findByBizKeyAndDelYnOrderByRegDtmDesc(String bizKey, String delYn, Pageable pageable);

    /** One connection by id, for the update / disconnect paths. */
    Optional<ZaloOaInfm> findByBizKeyAndOaIdAndDelYn(String bizKey, String oaId, String delYn);

    /**
     * The Official Account outbound sends go through. At most one row can match
     * — {@code zalo_oa_infm_uk01} makes that a database guarantee, so this
     * returns {@link Optional} rather than a list.
     */
    Optional<ZaloOaInfm> findByBizKeyAndUseYnAndDelYn(String bizKey, String useYn, String delYn);

    /**
     * The tenant's other live connections, used to stand them down before
     * activating a new one. Skipping this makes the activating write violate
     * {@code zalo_oa_infm_uk01}.
     */
    List<ZaloOaInfm> findByBizKeyAndUseYnAndDelYnAndOaIdNot(
            String bizKey, String useYn, String delYn, String oaId);

    boolean existsByBizKeyAndOaIdAndDelYn(String bizKey, String oaId, String delYn);

    /**
     * One connection, locked for the duration of the caller's transaction
     * ({@code SELECT … FOR UPDATE}).
     *
     * <p>Used only by {@code ZaloTokenService} around a token refresh, and the
     * lock is load-bearing rather than defensive. Zalo <b>rotates the refresh
     * token on every refresh and invalidates the previous one</b>, so two callers
     * refreshing the same connection concurrently produce two rotations where the
     * second retires the first: whichever write commits last wins, and the other
     * caller is left holding a token Zalo no longer accepts. Serialising on the
     * row makes the second caller wait and then observe the already-refreshed
     * token instead of asking Zalo again.
     *
     * <p>Does not filter on {@code DEL_YN} — a refresh must be able to read a
     * connection that was disconnected mid-flight, and the caller decides what
     * that means.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ZaloOaInfm o WHERE o.bizKey = :bizKey AND o.oaId = :oaId")
    Optional<ZaloOaInfm> lockForTokenRefresh(@Param("bizKey") String bizKey, @Param("oaId") String oaId);

    /**
     * Connections whose credentials have not been touched for {@code staleDays}
     * days, for the scheduled refresh that keeps Zalo's rotating refresh token
     * alive.
     *
     * <p><b>Deliberately not tenant-scoped.</b> This is the one caller that must
     * see every tenant: it runs from a scheduled job with no request context, so
     * there is no {@code TenantContext} to read. Never expose it through a
     * controller.
     *
     * <p>{@code MOD_DTM} holds UTC, because
     * {@link com.kosign.wecms.common.entity.LegacyDtmConverter} normalises to UTC
     * on write. {@code TO_TIMESTAMP} would otherwise read it in the session
     * timezone, so the value is cast to a naive timestamp and re-anchored with
     * {@code AT TIME ZONE 'UTC'} before the comparison.
     */
    @Query(value = """
            SELECT * FROM zalo_oa_infm
            WHERE use_yn = 'Y'
              AND del_yn = 'N'
              AND refresh_token IS NOT NULL
              AND (CURRENT_TIMESTAMP
                   - (TO_TIMESTAMP(mod_dtm, 'YYYYMMDDHH24MISS')::timestamp AT TIME ZONE 'UTC'))
                  > MAKE_INTERVAL(days => :staleDays)
            """, nativeQuery = true)
    List<ZaloOaInfm> findStaleForRefresh(@Param("staleDays") int staleDays);
}
