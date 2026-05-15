package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.overseas.entity.OverseasPaymentKeySeq;
import com.kosign.wecms.domain.payment.overseas.entity.OverseasPaymentKeySeqId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Spring Data JPA repository for {@link OverseasPaymentKeySeq} +
 * custom atomic allocator fragment. Mirrors
 * {@code InternalTransferKeySeqRepository} exactly — same UPSERT pattern,
 * different table ({@code OVERSEAS_PAYMENT_KEY_SEQ} created in V135).
 */
public interface OverseasPaymentKeySeqRepository
        extends JpaRepository<OverseasPaymentKeySeq, OverseasPaymentKeySeqId>,
                OverseasPaymentKeySeqAllocator {
}

interface OverseasPaymentKeySeqAllocator {
    /**
     * Atomically advance the per-(tenant, day) counter and return the
     * new {@code LAST_SEQ}. Concurrent calls are serialised by
     * PostgreSQL's row-level lock that {@code ON CONFLICT … DO UPDATE}
     * acquires.
     */
    int allocateNext(String bizKey, LocalDate keyDt);
}

@Repository
class OverseasPaymentKeySeqRepositoryImpl implements OverseasPaymentKeySeqAllocator {

    @PersistenceContext
    private EntityManager em;

    private static final String UPSERT_SQL = """
            INSERT INTO OVERSEAS_PAYMENT_KEY_SEQ (BIZ_KEY, KEY_DT, LAST_SEQ, REG_DTM, MOD_DTM)
            VALUES (:bizKey, :keyDt, 1, now(), now())
            ON CONFLICT (BIZ_KEY, KEY_DT)
            DO UPDATE SET LAST_SEQ = OVERSEAS_PAYMENT_KEY_SEQ.LAST_SEQ + 1,
                          MOD_DTM  = now()
            RETURNING LAST_SEQ
            """;

    @Override
    public int allocateNext(String bizKey, LocalDate keyDt) {
        if (bizKey == null || bizKey.isBlank()) throw new IllegalArgumentException("bizKey is required");
        if (keyDt == null) throw new IllegalArgumentException("keyDt is required");
        Number result = (Number) em.createNativeQuery(UPSERT_SQL)
                .setParameter("bizKey", bizKey)
                .setParameter("keyDt",  Date.valueOf(keyDt))
                .getSingleResult();
        return result.intValue();
    }
}
