package com.kosign.wecms.common.legacy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/**
 * Single funnel for native calls to surviving legacy PL/pgSQL functions during
 * the JEX → Spring cut-over. Every {@code wb_get_next_val(...)} site MUST go
 * through this repository — never via ad-hoc {@code @Query(nativeQuery=true)}
 * scattered across domain repositories. This makes Phase 3 retirement a
 * {@code grep} target.
 *
 * <p>Phase 1 surface: {@link #nextVal(String, String)} and
 * {@link #nextVal(String, String, String, String, String, String, String, String)}
 * which delegate to {@code wb_get_next_val(...)} with up to six trailing
 * parameters (matching the legacy signature).
 *
 * <p>Per Phase 1 scope (seahorse plan §2 + crystalline-honking-sky.md §7.2)
 * only the {@code BIZ_KEY} (busi_infm) and {@code EMPL_NO} (empl_infm) branches
 * are exercised. Other branches return {@code null} from the local-DB
 * V11 reduced function — calling them indicates a Phase 1 scope violation.
 */
@Repository
public class LegacyFunctionRepository {

    @PersistenceContext
    private EntityManager em;

    /** Two-argument shorthand. */
    public String nextVal(String table, String field) {
        return nextVal(table, field, null, null, null, null, null, null);
    }

    /**
     * Calls {@code wb_get_next_val(:t, :f, :p1, :p2, :p3, :p4, :p5, :p6)}.
     * Any parameter not used by the matched branch should be {@code null}.
     */
    public String nextVal(String table, String field,
                          String p1, String p2, String p3,
                          String p4, String p5, String p6) {
        Query q = em.createNativeQuery(
                "SELECT wb_get_next_val(" +
                        "CAST(?1 AS varchar), CAST(?2 AS varchar), " +
                        "CAST(?3 AS varchar), CAST(?4 AS varchar), " +
                        "CAST(?5 AS varchar), CAST(?6 AS varchar), " +
                        "CAST(?7 AS varchar), CAST(?8 AS varchar))");
        q.setParameter(1, table);
        q.setParameter(2, field);
        q.setParameter(3, p1);
        q.setParameter(4, p2);
        q.setParameter(5, p3);
        q.setParameter(6, p4);
        q.setParameter(7, p5);
        q.setParameter(8, p6);
        Object result = q.getSingleResult();
        return result != null ? result.toString() : null;
    }
}
