package com.kosign.wecms.common.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-03 — server-side JWT revocation (logout + privilege change).
 *
 * <p>Two in-memory structures, both keyed so an expired entry can be dropped:
 * <ul>
 *   <li><b>jti denylist</b> ({@code jti → token exp}) — a specific token that was
 *       explicitly logged out. Populated by {@code AuthService.logout(...)}.</li>
 *   <li><b>per-user not-before cutoff</b> ({@code userId → cutoff instant}) — every
 *       token for that user issued <i>before</i> the cutoff is rejected. Populated
 *       when a user is deleted / demoted / has their password reset by an admin, so
 *       a locked/deleted/demoted user cannot keep using a stale privileged token.</li>
 * </ul>
 *
 * <p><b>Scope / known limitations (documented, not a bug):</b> this store is
 * <b>in-process</b>. It is intentionally NOT backed by a database table — the
 * refactor runs against the live JEX schema and the project DB policy forbids new
 * refactor-only tables without PO + JEX-DDL approval (see the removed notification /
 * idempotency tables). Consequences:
 * <ul>
 *   <li>Revocations are <b>lost on restart</b> — after a restart, un-expired tokens
 *       are honoured again until their natural {@code exp} (≤ the 1h access-token
 *       TTL), so the window is bounded, not unbounded.</li>
 *   <li>Revocations are <b>not shared across instances</b> — a multi-instance deploy
 *       needs a shared store. The smallest durable upgrade is proposed in
 *       {@code docs/security/jwt-revocation.md} (shared cache or a PO-approved
 *       legacy-table-backed denylist); do not add a refactor-only table here.</li>
 * </ul>
 * Even with these limits this is a strict improvement over "no revocation at all":
 * logout now actually invalidates a token within a single running instance, and an
 * admin lock/delete/demote cuts the affected user off immediately on that instance.
 */
@Component
public class TokenRevocationService {

    /** jti → token expiry. Entry is meaningless once {@code now > exp}. */
    private final Map<String, Instant> revokedJti = new ConcurrentHashMap<>();

    /** userId → cutoff. A token whose {@code iat} is before this is rejected. */
    private final Map<String, Instant> userNotBefore = new ConcurrentHashMap<>();

    /** Cheap, self-contained eviction: purge expired jti entries every N writes
     *  (no Spring scheduler dependency, keeps the service unit-testable). */
    private final AtomicInteger writeCount = new AtomicInteger();
    private static final int PURGE_EVERY = 256;

    /** Revoke a single token (logout). {@code expiresAt} lets the entry self-evict. */
    public void revokeToken(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) {
            return;
        }
        revokedJti.put(jti, expiresAt);
        maybePurge();
    }

    /**
     * Revoke every token for a user issued before {@code cutoff} (lock / delete /
     * demote / admin password reset). Keeps the latest (largest) cutoff on repeat.
     */
    public void revokeAllForUser(String userId, Instant cutoff) {
        if (userId == null || cutoff == null) {
            return;
        }
        userNotBefore.merge(userId, cutoff, (oldV, newV) -> newV.isAfter(oldV) ? newV : oldV);
        maybePurge();
    }

    /** True when this exact token (jti) was explicitly revoked and has not yet expired. */
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Instant exp = revokedJti.get(jti);
        if (exp == null) {
            return false;
        }
        if (Instant.now().isAfter(exp)) {
            revokedJti.remove(jti);   // lazy eviction
            return false;
        }
        return true;
    }

    /** True when the user has a revocation cutoff after the token's issued-at. */
    public boolean isUserRevokedSince(String userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        Instant cutoff = userNotBefore.get(userId);
        return cutoff != null && issuedAt.isBefore(cutoff);
    }

    private void maybePurge() {
        if (writeCount.incrementAndGet() % PURGE_EVERY == 0) {
            Instant now = Instant.now();
            revokedJti.values().removeIf(now::isAfter);
        }
    }
}
