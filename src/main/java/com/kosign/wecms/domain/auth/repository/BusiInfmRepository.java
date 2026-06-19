package com.kosign.wecms.domain.auth.repository;

import com.kosign.wecms.domain.auth.entity.BusiInfm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BusiInfmRepository extends JpaRepository<BusiInfm, String> {

    /**
     * Active tenants for batch fan-out, matching the legacy JEX
     * {@code BUSI_INFM_R001} filter used by every exchange-rate BCS job
     * ({@code EXCH_0001/0010/0020/0030}):
     * {@code del_yn = 'N' AND busi_stts = 'N' (BusiStts.NORMAL) AND prdt_type IN ('F','M','H')}.
     *
     * <p>Replaces the previous {@code findAll().filter(delYn != 'Y')} fan-out,
     * which over-included suspended/closed companies (busi_stts W/S/C),
     * {@code del_yn = NULL} rows, and non-F/M/H product types — none of which
     * the legacy batch collects rates for.
     */
    List<BusiInfm> findByDelYnAndBusiSttsAndPrdtTypeIn(
            String delYn, String busiStts, Collection<String> prdtTypes);
}
