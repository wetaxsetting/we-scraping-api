package com.kosign.wecms.domain.setting.firmbankingsettings.repository;

import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCodeId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to {@code TB_FIRM_BANK_CODE} (V59) for the
 * firm-banking-settings Create endpoint's V10 referential check —
 * "{@code out_bank_cd} must exist in {@code TB_FIRM_BANK_CODE} for
 * {@code (ant_div_cd='NAPAS', bank_cd=outBankCd)}", per JEX
 * {@code comm_0220_002_c001_act.jsp:116-121}.
 *
 * <p>Spring Data derives the SQL from {@link #existsByAntDivCdAndBankCd}
 * — the legacy filter shape is captured by the method name. The
 * dictionary is global (not tenant-scoped — no {@code BIZ_KEY}
 * column on the table).
 *
 * <p>Earlier revision used a bare {@code Repository<Object, Object>}
 * marker with a native {@code @Query}; that broke at startup because
 * Spring Data JPA's {@code JpaRepositoryFactoryBean} eagerly resolves
 * the entity metamodel and rejected {@code java.lang.Object} as
 * unmanaged. The fix is a real entity ({@link TbFirmBankCode},
 * minimal column projection) plus this {@link JpaRepository}.
 */
public interface TbFirmBankCodeRepository extends JpaRepository<TbFirmBankCode, TbFirmBankCodeId> {

    /**
     * Returns {@code true} when a row exists in
     * {@code TB_FIRM_BANK_CODE} for the given ancillary-division code
     * and bank code. The firm-banking-settings Create endpoint passes
     * {@code antDivCd = "NAPAS"} per the legacy filter at
     * {@code comm_0220_002_c001_act.jsp:121}.
     */
    boolean existsByAntDivCdAndBankCd(String antDivCd, String bankCd);

    /**
     * Returns the NAPAS / CITAD bank rows the contacts Add/Edit
     * dialog populates its bank-picker autocomplete from. Mirrors
     * the IN-clause used by legacy {@code comm_0109_001_r002}; the
     * dropdown lists both NAPAS and CITAD entries when called without
     * a filter, ordered by {@code ENG_ABRVAT_NM} for a stable label
     * sequence. The caller (contacts service) is read-only.
     */
    List<TbFirmBankCode> findByAntDivCdInOrderByEngAbrvatNmAscBankCdAsc(Collection<String> antDivCds);

    /**
     * Resolve a NAPAS bank code (6-char input) to its registry row.
     * Mirrors legacy {@code PymnHelper#getListTbFirmBankCode} for the
     * NAPAS branch — used by the Internal Transfer Excel parser to
     * validate the user-typed Bank Code and stamp the display name
     * onto {@code TAR_BANK_NM}.
     *
     * <p>The legacy also filters on {@code BANK_STAS='1'} (active
     * banks only); we omit that predicate because the local seed
     * (V61 / V65) inserts these rows with {@code BANK_STAS} NULL.
     * When the column gets populated by a future migration the
     * predicate can be re-added without changing the call site.
     */
    Optional<TbFirmBankCode> findByAntDivCdAndBankCd(String antDivCd, String bankCd);

    /**
     * Resolve a CITAD bank code (3-char input) to its registry row.
     * Mirrors legacy {@code PymnHelper#getListTbFirmBankCode} for the
     * CITAD branch. {@code BANK_BRANCH_DIV='1'} narrows to
     * headquarters rows (legacy parity); see {@link #findByAntDivCdAndBankCd}
     * for the deferred {@code BANK_STAS='1'} caveat.
     */
    Optional<TbFirmBankCode> findFirstByAntDivCdAndCitadBankCd(
            String antDivCd, String citadBankCd);

    /**
     * Reverse name &rarr; code lookup &mdash; returns every NAPAS / CITAD
     * row whose English abbreviation matches {@code engAbrvatNm}.
     * Mirrors legacy {@code comm_0184_007_r001_act.jsp:235-264 getBankName
     * / getBankCode}: the Excel-upload parser passes the bank name
     * cell verbatim ({@code "VietinBank"}, {@code "ABBank"}, &hellip;) and
     * the count of matches drives the routing:
     * <ul>
     *   <li>0 matches &rarr; bank not found, row flagged.</li>
     *   <li>1 match &rarr; check the row's {@code antDivCd} to know
     *       whether to stamp {@code NAPAS_BANK_CD} or
     *       {@code CITAD_BANK_CD}.</li>
     *   <li>2+ matches (legacy assumes name overlap between NAPAS and
     *       CITAD) &rarr; route as NAPAS, take the NAPAS row.</li>
     * </ul>
     */
    List<TbFirmBankCode> findByEngAbrvatNm(String engAbrvatNm);

    /**
     * Bulk variant of {@link #findByEngAbrvatNm} &mdash; one query for the
     * whole batch of distinct bank names parsed from the Excel sheet so
     * the Excel-upload preflight does not fan out into N round trips.
     * Caller groups the returned rows by {@code engAbrvatNm} on the
     * service side.
     */
    List<TbFirmBankCode> findByEngAbrvatNmIn(Collection<String> engAbrvatNms);
}
