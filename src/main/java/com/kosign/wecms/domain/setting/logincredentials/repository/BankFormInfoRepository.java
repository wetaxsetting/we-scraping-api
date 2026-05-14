package com.kosign.wecms.domain.setting.logincredentials.repository;

import com.kosign.wecms.domain.setting.logincredentials.dto.BankFormInfoSubType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads the per-bank form info from {@code BANK_INFM_DETL}, grouping
 * by {@code SEQ_NO} to produce one row per sub-type. Mirrors the
 * legacy {@code nxisas_getCertFormInfo} response (R006 + R005 nested
 * IDOs) but collapses the two-stage query into a single SQL using
 * conditional aggregation on {@code BANK_DETL_INFO_CD}.
 *
 * <p>The legacy query did a separate fetch per {@code SEQ_NO} to read
 * detail rows; PostgreSQL's {@code FILTER (WHERE ...)} aggregate lets
 * us project codes 12 / 13 / 16 / 19 / 22 / 25 into named columns in
 * one shot — same end result, one round-trip.
 *
 * <p>Returns empty list for banks with no configured detail rows.
 */
@Repository
public class BankFormInfoRepository {

    private static final String SQL = """
            SELECT bank_cd,
                   seq_no::int                                                                                AS seq_no,
                   MAX(bank_detl_info_cont) FILTER (WHERE bank_detl_info_cd = '25')                            AS seq_nm,
                   MAX(bank_detl_info_cont) FILTER (WHERE bank_detl_info_cd = '12')                            AS logo_url,
                   MAX(bank_detl_info_cont) FILTER (WHERE bank_detl_info_cd = '13')                            AS id_label,
                   MAX(bank_detl_info_cont) FILTER (WHERE bank_detl_info_cd = '16')                            AS password_label,
                   MAX(bank_detl_info_cont) FILTER (WHERE bank_detl_info_cd = '19')                            AS confirm_label,
                   MAX(bank_detl_info_cont) FILTER (WHERE bank_detl_info_cd = '22')                            AS extra_label
              FROM bank_infm_detl
             WHERE bank_cd = :bankCd
               AND bank_detl_info_cd IN ('12','13','16','19','22','25')
             GROUP BY bank_cd, seq_no
             ORDER BY seq_no
            """;

    private static final RowMapper<BankFormInfoSubType> ROW_MAPPER = (rs, n) ->
            new BankFormInfoSubType(
                    rs.getString("bank_cd"),
                    rs.getInt("seq_no"),
                    rs.getString("seq_nm"),
                    rs.getString("logo_url"),
                    rs.getString("id_label"),
                    rs.getString("password_label"),
                    rs.getString("confirm_label"),
                    rs.getString("extra_label"));

    private final NamedParameterJdbcTemplate jdbc;

    public BankFormInfoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BankFormInfoSubType> formInfo(String bankCd) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bankCd", bankCd);
        return jdbc.query(SQL, params, ROW_MAPPER);
    }
}
