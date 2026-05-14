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

    @Scheduled(fixedDelay = 60_000L)
    void evictIdle() {
        Duration ttl = Duration.ofSeconds(props.sessionIdleSeconds());
        Instant cutoff = Instant.now().minus(ttl);
        sessions.values().removeIf(s -> s.getLastTouched().isBefore(cutoff));
    }
}
