package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BankExchRateInfmRepository extends JpaRepository<BankExchRateInfm, BankExchRateInfmId> {

    /**
     * One row per currency pair — latest batch round — across ALL tenants.
     *
     * <p>Exchange rates are global bank data identical for every tenant.
     * The {@code biz_key} filter has been removed so any authenticated user
     * can see rates regardless of which tenant they belong to.</p>
     *
     * <p>DISTINCT ON (ccy_cd, ref_ccy_cd) is required because both banks store
     * rates with {@code ccy_cd = 'VND'} and the foreign currency in
     * {@code ref_ccy_cd}. ORDER BY uses {@code CAST(seq_no AS integer) DESC} so
     * the highest batch round (latest update) is picked, then {@code biz_key ASC}
     * for fully deterministic tie-breaking when multiple tenants hold the same round.</p>
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (ccy_cd, ref_ccy_cd) *
            FROM BANK_EXCH_RATE_INFM
            WHERE bank_cd = :bankCd AND ref_date = :refDate
            ORDER BY ccy_cd ASC, ref_ccy_cd ASC, CAST(seq_no AS integer) DESC, biz_key ASC
            """)
    List<BankExchRateInfm> findLatestPerCurrencyByBankCdAndRefDate(
            @Param("bankCd") String bankCd,
            @Param("refDate") String refDate);

    /**
     * One row per currency pair for a specific batch round, across ALL tenants.
     * Used when the frontend selects a specific notification round from the dropdown.
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (ccy_cd, ref_ccy_cd) *
            FROM BANK_EXCH_RATE_INFM
            WHERE bank_cd = :bankCd AND ref_date = :refDate AND seq_no = :seqNo
            ORDER BY ccy_cd ASC, ref_ccy_cd ASC, biz_key ASC
            """)
    List<BankExchRateInfm> findLatestPerCurrencyByBankCdAndRefDateAndSeqNo(
            @Param("bankCd") String bankCd,
            @Param("refDate") String refDate,
            @Param("seqNo") String seqNo);

    /**
     * Finds an exact match for updating the exchange rate data from the batch.
     */
    Optional<BankExchRateInfm> findByBizKeyAndBankCdAndRefDateAndCcyCdAndSeqNo(
            String bizKey, String bankCd, String refDate, String ccyCd, String seqNo);

    /**
     * Returns the most recent REF_DATE across ALL tenants for the given bank.
     * Used as a fallback when today's date has no rows yet (e.g. batch hasn't run).
     * Exchange rates are global bank data — no biz_key filter needed.
     */
    @Query("SELECT MAX(e.refDate) FROM BankExchRateInfm e WHERE e.bankCd = :bankCd")
    Optional<String> findLatestRefDateByBankCd(@Param("bankCd") String bankCd);

    /**
     * Distinct bank codes that have exchange rate data — across ALL tenants.
     * Used by the {@code /exchange-rates/banks} endpoint to populate the bank selector.
     */
    @Query("SELECT DISTINCT e.bankCd FROM BankExchRateInfm e ORDER BY e.bankCd ASC")
    List<String> findDistinctBankCd();

    /**
     * Distinct display-currency codes across ALL tenants — used by the
     * {@code /exchange-rates/currencies} endpoint.
     *
     * <p>The display currency is the foreign side of each pair: Shinhan stores
     * the foreign currency in {@code ccy_cd}; Woori stores it in {@code ref_ccy_cd}
     * (with {@code ccy_cd} fixed to "VND"). The CASE expression normalises both
     * orientations to the foreign currency, matching the frontend's
     * {@code getDisplayCcy()} helper.</p>
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT CASE WHEN ccy_cd = 'VND' THEN ref_ccy_cd ELSE ccy_cd END AS display_ccy
            FROM BANK_EXCH_RATE_INFM
            ORDER BY display_ccy ASC
            """)
    List<String> findDistinctDisplayCurrency();

    /**
     * Distinct batch rounds (SEQ_NO) for a given bank and date, across ALL tenants.
     * Groups by SEQ_NO (batch round "1"=morning, "2"=afternoon) — NOT by EXCH_SEQ_NO
     * (auto-increment PK) — so the dropdown shows 2 entries max, not one per row.
     */
    @Query(nativeQuery = true, value = """
            SELECT seq_no, MAX(last_update_time) AS last_update_time
            FROM BANK_EXCH_RATE_INFM
            WHERE bank_cd = :bankCd AND ref_date = :refDate
            GROUP BY seq_no
            ORDER BY CAST(seq_no AS integer) DESC
            """)
    List<Object[]> findDistinctRoundsByBankCdAndRefDate(
            @Param("bankCd") String bankCd,
            @Param("refDate") String refDate);

    /**
     * Returns the next EXCH_SEQ_NO for a tenant — MAX + 1 scoped to BIZ_KEY.
     * Used by the exchange rate batch instead of wb_get_next_val so the seq
     * is computed in Java without requiring a PL/pgSQL function branch.
     */
    @Query(nativeQuery = true,
           value = "SELECT CAST(COALESCE(MAX(exch_seq_no::numeric), 0) + 1 AS varchar) " +
                   "FROM bank_exch_rate_infm WHERE biz_key = :bizKey")
    String findNextExchSeqNo(@Param("bizKey") String bizKey);

    /**
     * Returns the highest existing batch-round {@code SEQ_NO} (as the
     * largest integer-cast value) for the given bank + reference date
     * + counter currency, across ALL tenants. Used by the Woori batch
     * (legacy {@code EXCH_0020_001}) to compute the next round number:
     * {@code MAX(seq_no) + 1}, defaulting to 1 when no row exists.
     *
     * <p>Mirrors legacy SQL at
     * {@code bcs.jex.wabooks.exch.EXCH_0020_001#execute}:
     *
     * <pre>{@code
     *   SELECT seq_no FROM bank_exch_rate_infm
     *    WHERE bank_cd = ? AND ref_date = ? AND ccy_cd = ?
     *    ORDER BY CAST(seq_no AS integer) DESC LIMIT 1
     * }</pre>
     *
     * <p>No {@code biz_key} filter — legacy treats Woori rounds as
     * global per (bank, date, currency) so every tenant shares the same
     * seq_no value within one batch run.
     */
    @Query(nativeQuery = true, value = """
            SELECT seq_no FROM bank_exch_rate_infm
             WHERE bank_cd = :bankCd AND ref_date = :refDate AND ccy_cd = :ccyCd
             ORDER BY CAST(seq_no AS integer) DESC
             LIMIT 1
            """)
    Optional<String> findHighestSeqNo(@Param("bankCd") String bankCd,
                                      @Param("refDate") String refDate,
                                      @Param("ccyCd") String ccyCd);
}
