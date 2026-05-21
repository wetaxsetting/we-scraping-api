package com.kosign.wecms.domain.banking.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-bank period-limit lookup over {@code BANK_INFM_DETL}. Mirrors
 * the legacy IDO {@code BANK_INFM_DETL_R007} as consumed by
 * {@code nxisas_cardTranList_r001_act.jsp:113-115}:
 *
 * <pre>{@code
 *   SELECT ...
 *     FROM BANK_INFM_DETL
 *    WHERE BANK_DETL_INFO_CD IN ('26', '27')      -- '26' = count, '27' = unit
 *      AND (
 *            (BANK_CD = ? AND SEQ_NO IN (
 *               SELECT NXISAS_GB_SEQ_NO FROM BANK_CERT_INFO
 *                WHERE BIZ_KEY = ? AND SEQ_NO = ? AND BANK_CD = ?))
 *           OR ...
 *          )
 * }</pre>
 *
 * <p>The refactor's {@code BANK_INFM_DETL} table uses a single
 * {@code bank_detl_info_cont} column instead of legacy
 * {@code CONT1}/{@code CONT2}, so the query pivots via two filtered
 * aggregates per row (same pattern used by
 * {@code BankFormInfoRepository} for codes {@code 12-25}). Each
 * resulting row carries {@code cont1} (from code {@code '26'}) and
 * {@code cont2} (from code {@code '27'}) so the wire shape matches
 * legacy {@code REC_OUT} exactly.
 *
 * <p>When the bank has no {@code '26'} / {@code '27'} rows yet
 * (refactor's seed deliberately did not include them &mdash; awaiting
 * DEV snapshot), the query returns NULL for the missing field; the
 * service then falls back to the legacy default
 * ({@code count=90, unit=days}).
 */
@Repository
public class BankInfmDetlPeriodLimitRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public BankInfmDetlPeriodLimitRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Resolve the period limit for one (bizKey, bankCd, bankCertSeq)
     * triple. Returns {@code null} when the bank carries no period
     * configuration in {@code BANK_INFM_DETL} for the cert's sub-type.
     *
     * <p>Joins through {@code BANK_CERT_INFO.NXISAS_GB_SEQ_NO} so each
     * cert can have its own per-sub-type limit (e.g. VCB Corporate
     * vs. VCB DigiBiz). Mirrors the legacy IDO predicate at
     * {@code nxisas_cardTranList_r001_act.jsp:50-54}.
     */
    public PeriodRow findByBankAndCert(
            @NonNull String bizKey,
            @NonNull String bankCd,
            @NonNull BigDecimal bankCertSeq) {

        final String sql = """
            SELECT
                MAX(d.bank_detl_info_cont) FILTER (WHERE d.bank_detl_info_cd = '26') AS cont1,
                MAX(d.bank_detl_info_cont) FILTER (WHERE d.bank_detl_info_cd = '27') AS cont2
              FROM bank_infm_detl d
             WHERE d.bank_cd = :bankCd
               AND d.bank_detl_info_cd IN ('26', '27')
               AND d.seq_no IN (
                   SELECT c.nxisas_gb_seq_no
                     FROM bank_cert_info c
                    WHERE c.biz_key = :bizKey
                      AND c.seq_no  = :bankCertSeq
                      AND c.bank_cd = :bankCd
               )
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("bankCd",      bankCd);
        params.put("bizKey",      bizKey);
        params.put("bankCertSeq", bankCertSeq);

        List<PeriodRow> rows = jdbc.query(sql, params, (rs, idx) ->
                new PeriodRow(rs.getString("cont1"), rs.getString("cont2")));
        if (rows.isEmpty()) return null;
        PeriodRow row = rows.get(0);
        // Both columns NULL means no period config at all for this
        // (bank, cert) — return null so the caller can apply the
        // legacy default.
        if (row.cont1() == null && row.cont2() == null) return null;
        return row;
    }

    /** Raw period info pulled from {@code BANK_INFM_DETL}. */
    public record PeriodRow(String cont1, String cont2) { }
}
