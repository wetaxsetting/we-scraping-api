package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.CutoffConfig;
import com.kosign.wecms.domain.payment.internaltransfer.entity.CutoffConfigId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read access to {@code TB_CUTOFF_CONFIG}. Phase 2 uses only {@code findAll()}
 * (the table is tiny — ~10 rows — and {@link CutoffConfigProvider} snapshots
 * the whole set into memory on refresh).
 */
public interface CutoffConfigRepository extends JpaRepository<CutoffConfig, CutoffConfigId> {
}
