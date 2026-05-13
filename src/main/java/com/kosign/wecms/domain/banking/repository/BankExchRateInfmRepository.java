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
     * One row per counter-currency (latest EXCH_SEQ_NO) for a tenant on a given
     * reference date, ordered by CCY_CD ascending for a stable display order.
     *
     * <p>Uses PostgreSQL {@code DISTINCT ON} to mirror the JEX IDO R002 COALESCE
     * logic that prefers the afternoon batch run (SEQ_NO='2') over the morning run
     * (SEQ_NO='1') when both exist for the same date.</p>
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (ccy_cd) *
            FROM BANK_EXCH_RATE_INFM
            WHERE biz_key = :bizKey AND bank_cd = :bankCd AND ref_date = :refDate
            ORDER BY ccy_cd ASC, exch_seq_no DESC
            """)
    List<BankExchRateInfm> findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(
            @Param("bizKey") String bizKey,
            @Param("bankCd") String bankCd,
            @Param("refDate") String refDate);

    /**
     * All currencies for a specific batch SEQ_NO, ordered by CCY_CD.
     * Used when the caller explicitly requests a particular sequence number
     * (e.g. SEQ_NO='1' for the morning run, '2' for the afternoon run).
     */
    List<BankExchRateInfm> findByBizKeyAndBankCdAndRefDateAndSeqNoOrderByCcyCdAsc(
            String bizKey, String bankCd, String refDate, String seqNo);

    /**
     * Narrows to a single counter-currency — used when the frontend
     * requests one specific currency row.
     */
    List<BankExchRateInfm> findByBizKeyAndBankCdAndRefDateAndCcyCdOrderBySeqNoAsc(
            String bizKey, String bankCd, String refDate, String ccyCd);

    /**
     * Finds an exact match for updating the exchange rate data from the batch.
     */
    Optional<BankExchRateInfm> findByBizKeyAndBankCdAndRefDateAndCcyCdAndSeqNo(
            String bizKey, String bankCd, String refDate, String ccyCd, String seqNo);

    /**
     * Returns the most recent REF_DATE that has data for the given tenant
     * and bank — used as a fallback when today's date has no rows yet
     * (e.g. batch hasn't run, or dev seed uses a historical date).
     */
    @Query("SELECT MAX(e.refDate) FROM BankExchRateInfm e WHERE e.bizKey = :bizKey AND e.bankCd = :bankCd")
    Optional<String> findLatestRefDate(@Param("bizKey") String bizKey, @Param("bankCd") String bankCd);

    /**
     * Distinct bank codes that have data for the given tenant — used by the
     * {@code /exchange-rates/banks} endpoint to populate the bank selector.
     */
    @Query("SELECT DISTINCT e.bankCd FROM BankExchRateInfm e WHERE e.bizKey = :bizKey ORDER BY e.bankCd ASC")
    List<String> findDistinctBankCdByBizKey(@Param("bizKey") String bizKey);

    /**
     * Returns the next EXCH_SEQ_NO for a tenant — MAX + 1 scoped to BIZ_KEY.
     * Used by the exchange rate batch instead of wb_get_next_val so the seq
     * is computed in Java without requiring a PL/pgSQL function branch.
     */
    @Query(nativeQuery = true,
           value = "SELECT CAST(COALESCE(MAX(exch_seq_no::numeric), 0) + 1 AS varchar) " +
                   "FROM bank_exch_rate_infm WHERE biz_key = :bizKey")
    String findNextExchSeqNo(@Param("bizKey") String bizKey);
}
