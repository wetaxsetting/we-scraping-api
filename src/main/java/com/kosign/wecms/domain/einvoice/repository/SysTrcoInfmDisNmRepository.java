package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.SysTrcoInfmDisNm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

/**
 * Repository for the global {@code SYS_TRCO_INFM_DIS_NM} reference table.
 *
 * <p>Provided for inventory completeness + verification; the display-name
 * normalisation runtime path is the ported DB function
 * {@code wb_get_normalized_trco_nick_nm} via
 * {@code LegacyFunctionRepository.getNormalizedTrcoNickNm} (legacy parity),
 * NOT a JPA query on this repository.
 */
public interface SysTrcoInfmDisNmRepository extends JpaRepository<SysTrcoInfmDisNm, BigDecimal> {
}
