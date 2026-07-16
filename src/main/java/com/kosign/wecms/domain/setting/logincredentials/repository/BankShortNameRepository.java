package com.kosign.wecms.domain.setting.logincredentials.repository;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the SHORT bank name shown in the Login Credentials list's
 * Institution column. Mirrors the legacy two-argument DB function
 * {@code wb_get_bank_nm(bank_cd, 's')} used by the list IDO
 * ({@code IDO.BANK_CERT_INFO_R007.xml:46} → {@code INST}). In legacy the
 * SHORT name lives in {@code BANK_INFM_DETL} under
 * {@code BANK_DETL_INFO_CD = '10'}, date-effective between {@code START_DATE}
 * and {@code END_DATE} (both {@code yyyyMMdd} strings); the FULL name
 * (the {@code 'l'} branch) is {@code BANK_INFM.RPRS_BANK_NM} and is the
 * caller's fallback.
 *
 * <p>Reads {@code BANK_INFM_DETL} directly from Java (not the legacy
 * PL/pgSQL function) — same approach as {@link BankFormInfoRepository}
 * and {@code BankInfmDetlPeriodLimitRepository}, per the refactor
 * "no new legacy DB-function dependencies" policy.
 */
@Repository
public class BankShortNameRepository {

    private static final String SQL = """
            SELECT bank_cd, bank_detl_info_cont AS short_nm
              FROM bank_infm_detl
             WHERE bank_detl_info_cd = '10'
               AND to_char(now(), 'YYYYMMDD') BETWEEN start_date AND end_date
               AND bank_cd IN (:bankCds)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BankShortNameRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code bankCd → short name} for every code that has a
     * date-effective {@code '10'} row. Codes with no short row are
     * absent from the map, so the caller keeps its full-name fallback
     * for them (legacy would render blank; the refactor degrades to the
     * full name rather than an empty cell). Returns an empty map for a
     * null/empty input.
     */
    public Map<String, String> shortNames(Collection<String> bankCds) {
        Map<String, String> out = new HashMap<>();
        if (bankCds == null || bankCds.isEmpty()) return out;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bankCds", bankCds);
        RowCallbackHandler handler = rs ->
                out.put(rs.getString("bank_cd"), rs.getString("short_nm"));
        jdbc.query(SQL, params, handler);
        return out;
    }
}
