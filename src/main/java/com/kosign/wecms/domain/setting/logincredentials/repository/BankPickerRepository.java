package com.kosign.wecms.domain.setting.logincredentials.repository;

import com.kosign.wecms.domain.setting.logincredentials.dto.BankPickerRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only repository for the Add-Bank picker. Runs the legacy
 * {@code nxisas_acctList_r002} → {@code BANK_INFM_DETL_R004} query
 * verbatim — joining three views of {@code BANK_INFM_DETL} on
 * {@code (BANK_CD, SEQ_NO)} to project (bank_cd, bank_nm, bank_img).
 *
 * <p>Implemented via {@link NamedParameterJdbcTemplate} rather than a
 * full JPA entity because {@code BANK_INFM_DETL} is a flexible
 * key-value table (one row per (BANK_CD, BANK_DETL_INFO_CD)) and
 * modelling it as a JPA entity would force every consumer through the
 * type system for what is naturally a tabular projection.
 *
 * <p>Tenant-agnostic: bank registry is global. No BIZ_KEY filter.
 */
@Repository
public class BankPickerRepository {

    private static final String SQL = """
            SELECT INFO.BANK_CD AS bank_cd,
                   NAME.BANK_DETL_INFO_CONT AS bank_nm,
                   IMG.BANK_DETL_INFO_CONT AS bank_img
              FROM BANK_INFM_DETL INFO
              JOIN (
                   SELECT BANK_CD, SEQ_NO, BANK_DETL_INFO_CONT
                     FROM BANK_INFM_DETL
                    WHERE BANK_DETL_INFO_CD = '11'
                   ) IMG
                ON INFO.BANK_CD = IMG.BANK_CD AND INFO.SEQ_NO = IMG.SEQ_NO
              JOIN (
                   SELECT BANK_CD, SEQ_NO, BANK_DETL_INFO_CONT
                     FROM BANK_INFM_DETL
                    WHERE BANK_DETL_INFO_CD = '10'
                   ) NAME
                ON INFO.BANK_CD = NAME.BANK_CD AND INFO.SEQ_NO = NAME.SEQ_NO
             WHERE INFO.BANK_DETL_INFO_CD = '9'
               AND INFO.BANK_DETL_INFO_CONT = 'Y'
               AND INFO.BANK_CD != '11740099'
               -- Cast both bindings to text. PostgreSQL can't infer the
               -- type of a JDBC-bound NULL on its own and refuses the
               -- query with "could not determine data type of parameter".
               AND (CAST(:search AS text) IS NULL
                    OR NAME.BANK_DETL_INFO_CONT ILIKE '%' || CAST(:search AS text) || '%')
             ORDER BY NAME.BANK_DETL_INFO_CONT
            """;

    private static final RowMapper<BankPickerRow> ROW_MAPPER = (rs, n) ->
            new BankPickerRow(
                    rs.getString("bank_cd"),
                    rs.getString("bank_nm"),
                    rs.getString("bank_img"));

    private final NamedParameterJdbcTemplate jdbc;

    public BankPickerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Return all scraping-capable banks, optionally filtered by a
     * case-insensitive substring on {@code BANK_NM}. Null / blank
     * {@code search} returns the full list.
     */
    public List<BankPickerRow> list(String search) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search", (search == null || search.isBlank()) ? null : search.trim());
        return jdbc.query(SQL, params, ROW_MAPPER);
    }
}
