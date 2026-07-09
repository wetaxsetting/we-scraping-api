package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.BankExchRateInfm;
import com.kosign.wecms.domain.banking.entity.BankExchRateInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BankExchRateInfmRepository extends JpaRepository<BankExchRateInfm, BankExchRateInfmId> {

    /**
     * One row per currency pair for the tenant's latest batch round.
     *
     * <p>Mirrors legacy JEX {@code exch_0001_001_r001_act.jsp}: the rate read is
     * strictly tenant-scoped ({@code biz_key = current tenant}) and base-currency
     * scoped ({@code ccy_cd = 'VND'}, with the foreign currency in
     * {@code ref_ccy_cd}). {@code BANK_EXCH_RATE_INFM} is physically partitioned
     * per tenant — {@code BIZ_KEY} is part of the PK — and the batch fans the same
     * Coocon rates out to every active company; each company reads only its own
     * copy. Rate <em>values</em> are logically global, but storage and the read
     * contract are per-tenant, so a tenant with no rows must see nothing (never
     * another company's rows).</p>
     *
     * <p>DISTINCT ON (ccy_cd, ref_ccy_cd) with {@code CAST(seq_no AS integer) DESC}
     * picks the highest batch round (latest update) per currency. No cross-tenant
     * {@code biz_key} tiebreak is needed now that the query is scoped to one tenant.</p>
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (ccy_cd, ref_ccy_cd) *
            FROM BANK_EXCH_RATE_INFM
            WHERE biz_key = :bizKey AND bank_cd = :bankCd AND ref_date = :refDate AND ccy_cd = 'VND'
            ORDER BY ccy_cd ASC, ref_ccy_cd ASC, CAST(seq_no AS integer) DESC
            """)
    List<BankExchRateInfm> findLatestPerCurrencyByBizKeyAndBankCdAndRefDate(
            @Param("bizKey") String bizKey,
            @Param("bankCd") String bankCd,
            @Param("refDate") String refDate);

    /**
     * One row per currency pair for a specific batch round — tenant-scoped and
     * base-currency scoped (see
     * {@link #findLatestPerCurrencyByBizKeyAndBankCdAndRefDate}). Used when the
     * frontend selects a specific notification round from the dropdown.
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (ccy_cd, ref_ccy_cd) *
            FROM BANK_EXCH_RATE_INFM
            WHERE biz_key = :bizKey AND bank_cd = :bankCd AND ref_date = :refDate AND ccy_cd = 'VND' AND seq_no = :seqNo
            ORDER BY ccy_cd ASC, ref_ccy_cd ASC
            """)
    List<BankExchRateInfm> findLatestPerCurrencyByBizKeyAndBankCdAndRefDateAndSeqNo(
            @Param("bizKey") String bizKey,
            @Param("bankCd") String bankCd,
            @Param("refDate") String refDate,
            @Param("seqNo") String seqNo);

    /**
     * Finds an exact logical row for the batch upsert/skip decision — mirrors the
     * legacy existence key verbatim:
     * {@code biz_key + bank_cd + ref_date + ref_ccy_cd + ccy_cd + seq_no}
     * (see {@code EXCH_0001/0010/0030_001#execute}, e.g. {@code EXCH_0010_001.java:202}).
     *
     * <p>{@code ref_ccy_cd} (the foreign currency — USD/KRW/JPY/…) is the
     * discriminator: for Shinhan/Woori VN data {@code ccy_cd} is always
     * {@code 'VND'}, so a key without {@code ref_ccy_cd} collapses all 12/5
     * currencies of a round onto one logical row. This is the correct key.
     */
    Optional<BankExchRateInfm> findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo(
            String bizKey, String bankCd, String refDate, String refCcyCd, String ccyCd, String seqNo);

    /**
     * Returns the most recent REF_DATE for the tenant + bank. Used as a fallback
     * when today's date has no rows yet (refactor addition — legacy has no such
     * fallback). Tenant-scoped to match the rate read.
     */
    @Query("SELECT MAX(e.refDate) FROM BankExchRateInfm e WHERE e.bizKey = :bizKey AND e.bankCd = :bankCd")
    Optional<String> findLatestRefDateByBizKeyAndBankCd(@Param("bizKey") String bizKey,
                                                        @Param("bankCd") String bankCd);

    /**
     * Distinct bank codes that have exchange rate data for the tenant.
     * Used by the {@code /exchange-rates/banks} endpoint to populate the bank
     * selector. Tenant-scoped so no cross-company bank set leaks; the frontend
     * falls back to its static Shinhan/Woori list when the tenant has no rows.
     */
    @Query("SELECT DISTINCT e.bankCd FROM BankExchRateInfm e WHERE e.bizKey = :bizKey ORDER BY e.bankCd ASC")
    List<String> findDistinctBankCdByBizKey(@Param("bizKey") String bizKey);

    /**
     * Distinct display-currency codes for the tenant — used by the
     * {@code /exchange-rates/currencies} endpoint.
     *
     * <p>The display currency is the foreign side of each pair: Shinhan stores
     * the foreign currency in {@code ccy_cd}; Woori stores it in {@code ref_ccy_cd}
     * (with {@code ccy_cd} fixed to "VND"). The CASE expression normalises both
     * orientations to the foreign currency, matching the frontend's
     * {@code getDisplayCcy()} helper. Tenant-scoped for isolation parity.</p>
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT CASE WHEN ccy_cd = 'VND' THEN ref_ccy_cd ELSE ccy_cd END AS display_ccy
            FROM BANK_EXCH_RATE_INFM
            WHERE biz_key = :bizKey
            ORDER BY display_ccy ASC
            """)
    List<String> findDistinctDisplayCurrencyByBizKey(@Param("bizKey") String bizKey);

    /**
     * Distinct batch rounds (SEQ_NO) for the tenant's data on a given bank + date.
     * Tenant-scoped and base-currency scoped to match the rate read. Groups by
     * SEQ_NO (batch round "1"=morning, "2"=afternoon) — NOT by EXCH_SEQ_NO
     * (auto-increment PK) — so the dropdown shows one entry per round, not per row.
     */
    @Query(nativeQuery = true, value = """
            SELECT seq_no, MAX(last_update_time) AS last_update_time
            FROM BANK_EXCH_RATE_INFM
            WHERE biz_key = :bizKey AND bank_cd = :bankCd AND ref_date = :refDate AND ccy_cd = 'VND'
            GROUP BY seq_no
            ORDER BY CAST(seq_no AS integer) DESC
            """)
    List<Object[]> findDistinctRoundsByBizKeyAndBankCdAndRefDate(
            @Param("bizKey") String bizKey,
            @Param("bankCd") String bankCd,
            @Param("refDate") String refDate);

    // EXCH_SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextBankExchRateExchSeqNo (V140 #32 — per
    // (biz_key, bank_cd, ref_date, ccy_cd) MAX+1, verbatim from the live JEX
    // function, which scopes more granularly than the old per-biz_key form) so
    // every wb_get_next_val call goes through the one funnel.

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

    /**
     * HAENGSUNG Oracle exchange-rate sync (Step 6 — legacy {@code ERP_0100_001}
     * via {@code BANK_EXCH_RATE_INFM_R003}). Reads ALL of a tenant's rate rows for
     * one reference date — every currency/bank/round, regardless of
     * {@code ERP_SND_YN} (legacy filter {@code coalesce(erp_snd_yn,'N') in ('Y','N')}
     * = all rows). {@code ERP_SND_YN} then drives the per-row insert-vs-update
     * decision in the service. Tenant-scoped by {@code BIZ_KEY}.
     */
    List<BankExchRateInfm> findByBizKeyAndRefDate(String bizKey, String refDate);

    /**
     * HAENGSUNG Oracle exchange-rate sync (Step 6) — mark a single rate row as sent
     * to the ERP ({@code ERP_SND_YN='Y'}, legacy {@code BANK_EXCH_RATE_INFM_U003}).
     * Keyed on the row's full natural PK
     * ({@code BIZ_KEY, BANK_CD, REF_DATE, CCY_CD, EXCH_SEQ_NO}). Called ONLY after
     * the Oracle write for the row succeeds; an Oracle failure leaves the flag
     * unchanged so the row is retried later.
     *
     * <p>Note: {@code BANK_CD} here is the WeCMS-stored ORIGINAL code (the row's
     * real PK). The legacy U003 reverse-mapped the CITAD code back to the original
     * for this WHERE; the refactor reads the entity directly, so its
     * {@code BANK_CD} is already the original — no reverse map needed.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE BankExchRateInfm e
               SET e.erpSndYn = 'Y',
                   e.modId    = :modId,
                   e.modDtm   = :modDtm
             WHERE e.bizKey     = :bizKey
               AND e.bankCd     = :bankCd
               AND e.refDate    = :refDate
               AND e.ccyCd      = :ccyCd
               AND e.exchSeqNo  = :exchSeqNo
            """)
    int markErpSent(@Param("bizKey")    String bizKey,
                    @Param("bankCd")    String bankCd,
                    @Param("refDate")   String refDate,
                    @Param("ccyCd")     String ccyCd,
                    @Param("exchSeqNo") String exchSeqNo,
                    @Param("modId")     String modId,
                    @Param("modDtm")    String modDtm);
}
