package com.kosign.wecms.domain.banking.nxisas.session;

import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory holder for in-flight {@link NxisasScrapeSession}s. Single
 * Spring Boot instance scope — fine for the spike, not for horizontal
 * scaling.
 *
 * <p>Eviction: a scheduled task purges sessions idle longer than
 * {@code wecms.nxisas.session-idle-seconds} (default 5 min). Lookups
 * also validate tenant + user against the bound principal so a
 * session id leaked to a different user is rejected.
 */
@Component
@RequiredArgsConstructor
public class NxisasSessionRegistry {

    private final ConcurrentHashMap<UUID, NxisasScrapeSession> sessions = new ConcurrentHashMap<>();
    private final NxisasGatewayProperties props;

    public void register(NxisasScrapeSession session) {
        sessions.put(session.getSessionId(), session);
    }

    public Optional<NxisasScrapeSession> find(UUID sessionId, String bizKey, String userId) {
        if (sessionId == null) return Optional.empty();
        NxisasScrapeSession s = sessions.get(sessionId);
        if (s == null) return Optional.empty();
        if (!s.getBizKey().equals(bizKey) || !s.getUserId().equals(userId)) {
            // Wrong tenant or wrong user — treat as not found to avoid
            // leaking session existence across principals.
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public void discard(UUID sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Retention-aware eviction (Phase 2).
     * <ul>
     *   <li><b>Terminal</b> sessions (COMPLETED / FAILED / CANCELLED) are kept
     *       for a retention window measured from {@code terminalAt} (fallback
     *       {@code lastTouched} for non-SYNC flows that don't stamp it) so the
     *       frontend's final poll can still read the terminal snapshot, then
     *       evicted. Removing them prevents any later resume.</li>
     *   <li><b>Non-terminal</b> sessions (RUNNING / AWAITING_CHALLENGE) are
     *       evicted only when idle past the same window AND no worker is
     *       actively running — an abandoned OTP wait eventually expires, but an
     *       in-flight scrape is never removed out from under its worker.</li>
     * </ul>
     * Eviction does not itself LOGOUT the bank session (the registry has no
     * gateway handle) — the gateway session times out on its own; a user-driven
     * cancel is the path that sends LOGOUT.
     */
    @Scheduled(fixedDelay = 60_000L)
    void evictIdle() {
        Duration ttl = Duration.ofSeconds(props.sessionIdleSeconds());
        Instant now = Instant.now();
        sessions.values().removeIf(s -> {
            if (s.isTerminal()) {
                Instant since = s.getTerminalAt() != null ? s.getTerminalAt() : s.getLastTouched();
                return since.isBefore(now.minus(ttl));
            }
            // Non-terminal — never evict an actively-running segment.
            if (s.isWorkerRunning()) return false;
            return s.getLastTouched().isBefore(now.minus(ttl));
        });
    }
}
