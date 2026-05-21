package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.InternalTransferKeySeq;
import com.kosign.wecms.domain.payment.internaltransfer.entity.InternalTransferKeySeqId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Spring Data JPA repository for {@link InternalTransferKeySeq} +
 * a custom native-query method for the atomic "allocate next sequence"
 * operation. The helper table is refactor-only — created in V85 —
 * and is the sole replacement for the legacy
 * {@code wb_get_next_val('scdul_pymn_sumr', 'scdul_pymn_no', BIZ_KEY)}
 * branch (intentionally NOT ported per
 * {@code wecms-api/CLAUDE.md} §"DB Function / Legacy Function Policy").
 *
 * <p>Native SQL is necessary because Spring Data JPA cannot express
 * {@code INSERT … ON CONFLICT … DO UPDATE … RETURNING} as a derived
 * method — and atomicity matters here (two concurrent saves on the
 * same tenant/day must not collide on the same {@code LAST_SEQ}).
 */
public interface InternalTransferKeySeqRepository
        extends JpaRepository<InternalTransferKeySeq, InternalTransferKeySeqId>,
                InternalTransferKeySeqAllocator {
}

/**
 * Custom-fragment interface for the atomic allocator. Spring Data JPA
 * wires this fragment into the {@link InternalTransferKeySeqRepository}
 * bean — the implementation in
 * {@link InternalTransferKeySeqRepositoryImpl} gets the {@code Impl}
 * suffix so Spring's repository factory finds it.
 */
interface InternalTransferKeySeqAllocator {
    /**
     * Atomically advance the per-(tenant, day) counter and return the new
     * {@code LAST_SEQ}. Concurrent calls for the same key are serialised
     * by PostgreSQL's row-level lock that {@code ON CONFLICT … DO UPDATE}
     * acquires.
     *
     * @param bizKey tenant scope (non-null)
     * @param keyDt  per-day scope (non-null) — Vietnam-local date per
     *               {@code InternalTransferKeyGenerator}
     * @return the new value of {@code LAST_SEQ} (1, 2, 3, …)
     */
    int allocateNext(String bizKey, LocalDate keyDt);
}

/**
 * Native-SQL implementation of {@link InternalTransferKeySeqAllocator}.
 * One UPSERT statement — {@code INSERT … ON CONFLICT (BIZ_KEY, KEY_DT)
 * DO UPDATE SET LAST_SEQ = INTERNAL_TRANSFER_KEY_SEQ.LAST_SEQ + 1 …
 * RETURNING LAST_SEQ} — so a fresh tenant-day starts at 1 and existing
 * rows advance atomically.
 */
@Repository
class InternalTransferKeySeqRepositoryImpl implements InternalTransferKeySeqAllocator {

    @PersistenceContext
    private EntityManager em;

    /** UPSERT + RETURNING — single round-trip, atomic per
     *  PostgreSQL's ON CONFLICT semantics. The {@code MOD_DTM} column
     *  is refreshed on every advance so we have a "last-used" audit
     *  trail without paying for a separate UPDATE. */
    private static final String UPSERT_SQL = """
            INSERT INTO INTERNAL_TRANSFER_KEY_SEQ (BIZ_KEY, KEY_DT, LAST_SEQ, REG_DTM, MOD_DTM)
            VALUES (:bizKey, :keyDt, 1, now(), now())
            ON CONFLICT (BIZ_KEY, KEY_DT)
            DO UPDATE SET LAST_SEQ = INTERNAL_TRANSFER_KEY_SEQ.LAST_SEQ + 1,
                          MOD_DTM  = now()
            RETURNING LAST_SEQ
            """;

    @Override
    public int allocateNext(String bizKey, LocalDate keyDt) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        if (keyDt == null) {
            throw new IllegalArgumentException("keyDt is required");
        }
        Number result = (Number) em.createNativeQuery(UPSERT_SQL)
                .setParameter("bizKey", bizKey)
                .setParameter("keyDt",  Date.valueOf(keyDt))
                .getSingleResult();
        return result.intValue();
    }
}
