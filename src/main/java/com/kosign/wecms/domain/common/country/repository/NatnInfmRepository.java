package com.kosign.wecms.domain.common.country.repository;

import com.kosign.wecms.domain.common.country.entity.NatnInfm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Country master repository.
 *
 * <p>Read-only reference data; no tenant filter. Sorted by display name
 * — the picker shows an alphabetical list and the helper index added in
 * V200 backs the ORDER BY.
 */
public interface NatnInfmRepository extends JpaRepository<NatnInfm, String> {

    List<NatnInfm> findAllByOrderByNatlNmAsc();
}
