package com.kosign.wecms.domain.setting.firmbankingsettings.repository;

import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
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
public interface TbFirmBankCodeRepository extends JpaRepository<TbFirmBankCode, String> {

    /**
     * Returns {@code true} when a row exists in
     * {@code TB_FIRM_BANK_CODE} for the given ancillary-division code
     * and bank code. The firm-banking-settings Create endpoint passes
     * {@code antDivCd = "NAPAS"} per the legacy filter at
     * {@code comm_0220_002_c001_act.jsp:121}.
     */
    boolean existsByAntDivCdAndBankCd(String antDivCd, String bankCd);
}
