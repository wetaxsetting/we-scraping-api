package com.kosign.wecms.domain.banking.nxisas;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only lookup over the legacy {@code NXISAS_ACCT_TYPE_MAPPING}
 * reference table — maps a bank's raw scraped account-type label to a
 * 2-char {@code ACCT_TYPE_CD}. Mirrors the legacy server-side mapping in
 * {@code fnsh_0101_001_r001_act.jsp:164-176}
 * ({@code NXISAS_ACCT_TYPE_MAPPING} keyed by
 * {@code (BANK_CD, CCY_CD, NXISAS_ACCT_TYPE)} → {@code ACCT_TYPE_CD};
 * blank when no row matches).
 *
 * <p><b>Off-inventory table — graceful by design.</b>
 * {@code NXISAS_ACCT_TYPE_MAPPING} is a legacy reference/config table
 * that is NOT in the refactor table baseline and is seeded by a manual
 * DBA script ({@code Claude Code (AI)/sql/nxisas_acct_type_mapping_*.sql}),
 * not Flyway. To keep {@code ddl-auto: validate} unaffected it is read via
 * a native query with <b>no {@code @Entity}</b>, and the lookup is guarded
 * by {@code to_regclass(...)} so an environment where the table is not yet
 * present simply returns no mappings (the picker leaves Account Type blank)
 * rather than failing the scrape or the application boot.
 *
 * <p><b>Pending PO sign-off:</b> adding this off-inventory table to the
 * refactor baseline (and running the seed script per env) is a documented
 * follow-up for the PO / DBA — until then this resolver degrades to
 * "no mapping".
 */
@Repository
public class NxisasAcctTypeMappingRepository {

    private static final Logger log = LoggerFactory.getLogger(NxisasAcctTypeMappingRepository.class);

    @PersistenceContext
    private EntityManager em;

    /**
     * Load every {@code CCY_CD -> (NXISAS_ACCT_TYPE -> ACCT_TYPE_CD)}
     * mapping for one bank, in a single query, so {@code runAcctList} can
     * resolve each scraped account in memory. Returns an empty map when
     * the reference table is absent in this environment or carries no rows
     * for the bank — never throws.
     *
     * @return nested map: currency → (raw nxisas type → {@code ACCT_TYPE_CD}),
     *         all keys trimmed
     */
    public Map<String, Map<String, String>> loadBankMappings(String bankCd) {
        if (bankCd == null || bankCd.isBlank()) {
            return Map.of();
        }
        try {
            // to_regclass returns NULL (no exception) when the table is
            // absent — so this never poisons an enclosing transaction the
            // way a failing SELECT on a missing table would.
            Object reg = em
                    .createNativeQuery("SELECT to_regclass('public.nxisas_acct_type_mapping')")
                    .getSingleResult();
            if (reg == null) {
                return Map.of();
            }
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT ccy_cd, nxisas_acct_type, acct_type_cd "
                            + "FROM nxisas_acct_type_mapping WHERE bank_cd = ?1")
                    .setParameter(1, bankCd)
                    .getResultList();
            Map<String, Map<String, String>> byCcy = new HashMap<>();
            for (Object[] r : rows) {
                String ccy = r[0] == null ? "" : r[0].toString().trim();
                String raw = r[1] == null ? "" : r[1].toString().trim();
                String code = r[2] == null ? null : r[2].toString().trim();
                if (code != null && !code.isEmpty()) {
                    byCcy.computeIfAbsent(ccy, k -> new HashMap<>()).put(raw, code);
                }
            }
            return byCcy;
        } catch (Exception e) {
            log.debug("NXISAS_ACCT_TYPE_MAPPING lookup skipped for bankCd={}: {}",
                    bankCd, e.getMessage());
            return Map.of();
        }
    }

    /** Resolve one raw scraped type to its {@code ACCT_TYPE_CD} against a
     *  pre-loaded {@link #loadBankMappings(String)} map; {@code null} when
     *  unmapped. */
    public static String resolve(Map<String, Map<String, String>> bankMappings,
                                 String ccyCd, String nxisasAcctType) {
        if (bankMappings == null || bankMappings.isEmpty()
                || ccyCd == null || nxisasAcctType == null) {
            return null;
        }
        Map<String, String> byType = bankMappings.get(ccyCd.trim());
        return byType == null ? null : byType.get(nxisasAcctType.trim());
    }
}
